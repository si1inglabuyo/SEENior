from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core.config import settings
from app.core.security import (
    _DUMMY_PASSWORD_HASH,
    create_access_token,
    hash_password,
    verify_password,
)
from app.db.models import Contact, DeviceToken, UnlinkActor, User, UserRole
from app.db.session import get_db
from app.schemas.auth import (
    AccountDeletionRequest,
    GoogleSignInRequest,
    PasswordChangeRequest,
    RegisterRequest,
    Token,
    UserOut,
    UserUpdate,
)

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login", response_model=Token)
async def login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: AsyncSession = Depends(get_db),
) -> Token:
    # OAuth2PasswordRequestForm's field is always named "username" by spec, but family
    # accounts log in by email (matching the Log In screen) while barangay responders
    # still log in by their pre-assigned username (CLAUDE.md §2) - so this checks both.
    result = await db.execute(
        select(User).where((User.email == form_data.username) | (User.username == form_data.username))
    )
    user = result.scalar_one_or_none()
    has_password = user is not None and user.password_hash is not None
    # Always run verify_password, even when there's no matching user or no password
    # set, so response timing doesn't leak account state (bcrypt cost stays constant).
    password_hash = user.password_hash if has_password else _DUMMY_PASSWORD_HASH
    password_ok = verify_password(form_data.password, password_hash)
    if user is None or not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    if not has_password:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="This account uses Google Sign-In. Please continue with Google.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    if not password_ok:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    token = create_access_token(subject=user.username, role=user.role.value)
    return Token(access_token=token)


@router.post("/register", response_model=Token, status_code=status.HTTP_201_CREATED)
async def register(payload: RegisterRequest, db: AsyncSession = Depends(get_db)) -> Token:
    """Family app's Sign Up screen. Creates the account up front, separate from and
    before pairing with any senior (pairing happens later via an invite code)."""
    existing = await db.execute(select(User).where(User.email == payload.email))
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="An account with this email already exists")

    user = User(
        username=payload.email,
        email=payload.email,
        password_hash=hash_password(payload.password),
        role=UserRole.FAMILY_CONTACT,
        full_name=payload.full_name,
        phone=payload.phone,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)

    token = create_access_token(subject=user.username, role=user.role.value)
    return Token(access_token=token)


@router.post("/google", response_model=Token)
async def google_sign_in(payload: GoogleSignInRequest, db: AsyncSession = Depends(get_db)) -> Token:
    if not settings.google_client_id:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Google Sign-In is not configured on the server",
        )
    try:
        idinfo = google_id_token.verify_oauth2_token(
            payload.id_token, google_requests.Request(), settings.google_client_id
        )
    except ValueError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Google token")

    google_sub = idinfo["sub"]
    email = idinfo.get("email")
    name = idinfo.get("name", "")

    result = await db.execute(select(User).where(User.google_sub == google_sub))
    user = result.scalar_one_or_none()

    if user is None and email:
        # Same email signed up manually before - link this Google account to it
        # instead of creating a duplicate.
        existing_result = await db.execute(select(User).where(User.email == email))
        existing = existing_result.scalar_one_or_none()
        if existing is not None:
            existing.google_sub = google_sub
            user = existing

    if user is None:
        user = User(
            username=email or f"google_{google_sub}",
            email=email,
            google_sub=google_sub,
            password_hash=None,
            role=UserRole.FAMILY_CONTACT,
            full_name=name,
            phone=None,
        )
        db.add(user)

    await db.commit()
    await db.refresh(user)

    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Account is disabled")

    token = create_access_token(subject=user.username, role=user.role.value)
    return Token(access_token=token)


@router.get("/me", response_model=UserOut)
async def read_current_user(current_user: User = Depends(get_current_user)) -> User:
    return current_user


@router.patch("/me", response_model=UserOut)
async def update_current_user(
    payload: UserUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> User:
    current_user.full_name = payload.full_name
    current_user.phone = payload.phone
    await db.commit()
    await db.refresh(current_user)
    return current_user


@router.post("/change-password", status_code=status.HTTP_204_NO_CONTENT)
async def change_password(
    payload: PasswordChangeRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> None:
    if current_user.password_hash is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="This account uses Google Sign-In and has no password to change",
        )
    if not verify_password(payload.current_password, current_user.password_hash):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Current password is incorrect")
    current_user.password_hash = hash_password(payload.new_password)
    await db.commit()


@router.post("/me/delete", status_code=status.HTTP_204_NO_CONTENT)
async def delete_current_account(
    payload: AccountDeletionRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> None:
    """Soft-deletes the caller's own account.

    The row is kept (deleted_at / deletion_reason / deletion_note) for audit, but:

      * is_active goes False, so login() and get_current_user() already reject it;
      * every pairing this user had is soft-unlinked (unlinked_by=family), so the
        seniors stop seeing them and the escalation sweep stops routing to them --
        Contact.is_active() / has_family_tier() both filter on unlinked_at IS NULL;
      * their device tokens are dropped, so this handset stops receiving pushes
        that name a senior (CLAUDE.md §11);
      * username / email / google_sub are tombstoned with a "+del<id>.<ts>" tag
        (front-truncated to the column limit), freeing those unique slots for a
        fresh sign-up later with no schema change.

    Idempotent: a repeat call on an already-deleted account is a no-op, the same
    way a repeated unlink is.
    """
    if current_user.deleted_at is not None:
        return

    now = datetime.now(timezone.utc).replace(tzinfo=None)
    # The id makes the tag unique on its own, so truncating the front of a long value
    # (username is only String(64), and for a family account it IS the email) still leaves
    # a value that cannot collide with a fresh sign-up. Keeping the tail keeps the tag intact.
    tag = f"+del{current_user.id}.{int(now.timestamp())}"

    current_user.deleted_at = now
    current_user.deletion_reason = payload.reason
    current_user.deletion_note = payload.note
    current_user.is_active = False
    current_user.username = f"{current_user.username}{tag}"[-64:]
    if current_user.email:
        current_user.email = f"{current_user.email}{tag}"[-255:]
    if current_user.google_sub:
        current_user.google_sub = f"{current_user.google_sub}{tag}"[-255:]

    links = await db.execute(
        select(Contact).where(Contact.user_id == current_user.id, Contact.is_active())
    )
    for contact in links.scalars().all():
        contact.unlinked_at = now
        contact.unlinked_by = UnlinkActor.FAMILY

    # Deleted outright, not lazy-loaded off current_user (that relationship is unloaded
    # on an async session and would raise). This handset must stop receiving pushes
    # that name a senior the moment the account is gone.
    await db.execute(delete(DeviceToken).where(DeviceToken.user_id == current_user.id))

    await db.commit()

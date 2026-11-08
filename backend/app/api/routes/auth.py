from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.security import OAuth2PasswordRequestForm
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core import push, ratelimit, sms, twofactor
from app.core.config import settings
from app.core.security import (
    _DUMMY_PASSWORD_HASH,
    create_access_token,
    hash_password,
    verify_password,
)
from app.db.models import Contact, DeviceToken, LoginChallenge, UnlinkActor, User, UserRole
from app.db.session import get_db
from app.schemas.auth import (
    AccountDeletionRequest,
    FirebaseSignInRequest,
    GoogleSignInRequest,
    LanguagePreferenceUpdate,
    PasswordChangeRequest,
    PasswordSetRequest,
    RegisterRequest,
    Token,
    TwoFactorChallenge,
    TwoFactorResendRequest,
    TwoFactorVerifyRequest,
    UserOut,
    UserUpdate,
)

router = APIRouter(prefix="/auth", tags=["auth"])


def _now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _invalid_code() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="That code is not valid. Please sign in again.",
    )


async def _send_code(db: AsyncSession, user: User, challenge: LoginChallenge) -> None:
    """Generates a fresh code for `challenge`, stores its hash and texts it to the account.

    Fails closed: if the text cannot be sent the challenge is left unusable and the caller
    gets a 503, because a responder who never receives a code must not be let in without one.
    The send is counted against the per-account SMS cap first, so retries can't run up credits.
    """
    await ratelimit.check("2fa-sms-user", str(user.id), limit=6, window_seconds=3600)
    code = twofactor.new_code()
    challenge.code_hash = twofactor.hash_code(challenge.id, code)
    result = await sms.send_sms([user.two_factor_phone], twofactor.sms_message(code))
    if result.sent < 1:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Could not send the verification code. Please try again in a moment.",
        )
    now = _now()
    challenge.expires_at = now + timedelta(seconds=twofactor.CODE_TTL_SECONDS)
    challenge.last_sent_at = now


def _challenge_out(challenge: LoginChallenge, user: User) -> TwoFactorChallenge:
    return TwoFactorChallenge(
        challenge_id=challenge.id,
        phone_hint=twofactor.mask_phone(user.two_factor_phone or ""),
        expires_in=twofactor.CODE_TTL_SECONDS,
        resend_after=twofactor.RESEND_COOLDOWN_SECONDS,
    )


@router.post("/login", response_model=Token | TwoFactorChallenge)
async def login(
    request: Request,
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: AsyncSession = Depends(get_db),
) -> Token | TwoFactorChallenge:
    """Password sign-in for both roles.

    An account with two-factor on gets a `TwoFactorChallenge` instead of a token; the token
    is issued by `/auth/login/verify` once the texted code is entered.

    Throttled per IP and per submitted identifier, so neither a single host nor a spread
    of hosts can work through passwords against one account.
    """
    submitted = form_data.username.strip().lower()
    await ratelimit.check("login-ip", ratelimit.client_ip(request), limit=10, window_seconds=300)
    await ratelimit.check("login-id", submitted, limit=5, window_seconds=300)

    # The form field is always named "username"; family accounts log in by email and
    # barangay responders by username, so check both.
    result = await db.execute(
        select(User).where((User.email == form_data.username) | (User.username == form_data.username))
    )
    user = result.scalar_one_or_none()
    has_password = user is not None and user.password_hash is not None
    # Always run verify_password so response timing doesn't reveal account state.
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
    if settings.two_factor_enforced and user.two_factor_enabled:
        if not user.two_factor_phone:
            # Never fall back to password-only: that would let anyone who has the password
            # skip the second factor by an account having no number.
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Two-factor sign-in is on but this account has no code phone number. "
                "Please contact the OSCA officer.",
            )
        # One pending sign-in per account: starting again discards the old code.
        await db.execute(delete(LoginChallenge).where(LoginChallenge.user_id == user.id))
        challenge = LoginChallenge(user_id=user.id, code_hash="", expires_at=_now())
        db.add(challenge)
        await db.flush()
        try:
            await _send_code(db, user, challenge)
        except HTTPException:
            await db.rollback()
            raise
        await db.commit()
        return _challenge_out(challenge, user)
    token = create_access_token(subject=str(user.id), role=user.role.value)
    return Token(access_token=token)


async def _load_challenge(db: AsyncSession, challenge_id: str) -> tuple[LoginChallenge, User]:
    """The live challenge and its account, or the same 401 for every way it can be wrong."""
    challenge = await db.get(LoginChallenge, challenge_id)
    if challenge is None or challenge.consumed:
        raise _invalid_code()
    user = await db.get(User, challenge.user_id)
    if user is None or not user.is_active:
        raise _invalid_code()
    return challenge, user


@router.post("/login/verify", response_model=Token)
async def verify_login_code(
    payload: TwoFactorVerifyRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> Token:
    """Second step of a two-factor sign-in: trade the texted code for a token."""
    await ratelimit.check("2fa-verify-ip", ratelimit.client_ip(request), limit=20, window_seconds=300)
    challenge, user = await _load_challenge(db, payload.challenge_id)

    if challenge.attempts >= twofactor.MAX_WRONG_GUESSES or challenge.expires_at < _now():
        raise _invalid_code()

    # Counted and saved before the comparison, so parallel guesses can't each get a free try.
    challenge.attempts += 1
    await db.commit()

    if not twofactor.code_matches(challenge.id, payload.code, challenge.code_hash):
        raise _invalid_code()

    challenge.consumed = True
    await db.commit()
    return Token(access_token=create_access_token(subject=str(user.id), role=user.role.value))


@router.post("/login/resend", response_model=TwoFactorChallenge)
async def resend_login_code(
    payload: TwoFactorResendRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> TwoFactorChallenge:
    """Texts a new code for a pending sign-in, at most twice and not more than once in 30 s.

    Wrong guesses already made still count against the challenge, so resending doesn't reset
    them.
    """
    await ratelimit.check("2fa-resend-ip", ratelimit.client_ip(request), limit=10, window_seconds=300)
    challenge, user = await _load_challenge(db, payload.challenge_id)
    now = _now()

    if (now - challenge.created_at).total_seconds() > twofactor.CHALLENGE_MAX_AGE_SECONDS:
        raise _invalid_code()
    if challenge.sends >= twofactor.MAX_SENDS:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="No more codes can be sent for this sign-in. Please sign in again.",
        )
    wait = twofactor.RESEND_COOLDOWN_SECONDS - (now - challenge.last_sent_at).total_seconds()
    if wait > 0:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Please wait a moment before asking for another code.",
            headers={"Retry-After": str(int(wait) + 1)},
        )

    await _send_code(db, user, challenge)
    challenge.sends += 1
    await db.commit()
    return _challenge_out(challenge, user)


@router.post(
    "/register",
    response_model=Token,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(ratelimit.per_ip("register", 10, 3600))],
)
async def register(payload: RegisterRequest, db: AsyncSession = Depends(get_db)) -> Token:
    """Family app's Sign Up. Creates the account before pairing with any senior."""
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

    token = create_access_token(subject=str(user.id), role=user.role.value)
    return Token(access_token=token)


@router.post(
    "/google",
    response_model=Token,
    dependencies=[Depends(ratelimit.per_ip("google-sign-in", 20, 300))],
)
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
        # Same email signed up manually before: link this Google account to it.
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

    token = create_access_token(subject=str(user.id), role=user.role.value)
    return Token(access_token=token)


@router.post(
    "/firebase",
    response_model=Token,
    dependencies=[Depends(ratelimit.per_ip("firebase-sign-in", 20, 300))],
)
async def firebase_sign_in(payload: FirebaseSignInRequest, db: AsyncSession = Depends(get_db)) -> Token:
    # Uses the same Firebase Admin connection as push.py (same service account).
    if not push.is_configured():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Firebase Auth is not configured on the server",
        )

    from firebase_admin import auth as firebase_auth

    try:
        decoded = firebase_auth.verify_id_token(payload.id_token)
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Firebase token")

    firebase_uid = decoded["uid"]
    email = decoded.get("email")
    name = decoded.get("name", "")

    result = await db.execute(select(User).where(User.firebase_uid == firebase_uid))
    user = result.scalar_one_or_none()

    if user is None and email:
        existing_result = await db.execute(select(User).where(User.email == email))
        existing = existing_result.scalar_one_or_none()
        if existing is not None:
            if payload.is_sign_up:
                # A fresh Sign Up must create a new identity. Attaching to an existing
                # account would hand it over and the profile PATCH would overwrite its
                # name and phone, so refuse.
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="An account with this email already exists. Please log in instead.",
                )
            # Same email already exists: link this Firebase identity to it. Safe here because
            # this path is only reached by a returning sign-in.
            existing.firebase_uid = firebase_uid
            user = existing

    if user is None:
        user = User(
            username=email or f"firebase_{firebase_uid}",
            email=email,
            firebase_uid=firebase_uid,
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

    token = create_access_token(subject=str(user.id), role=user.role.value)
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


@router.patch("/me/language", response_model=UserOut)
async def update_language(
    payload: LanguagePreferenceUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> User:
    """Family app's Profile -> Language toggle. Saves immediately; there is no Save button."""
    current_user.language_preference = payload.language
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


@router.post("/set-password", status_code=status.HTTP_204_NO_CONTENT)
async def set_password(
    payload: PasswordSetRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> None:
    """Adds a password to a Google-only account (Edit Profile -> Set a password).

    The JWT is the authorization, so no current password is needed. The account keeps
    Google Sign-In and also accepts email + password. Accounts that already have a
    password use /change-password.
    """
    if current_user.password_hash is not None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="This account already has a password. Use change password instead.",
        )
    current_user.password_hash = hash_password(payload.new_password)
    await db.commit()


@router.post("/me/delete", status_code=status.HTTP_204_NO_CONTENT)
async def delete_current_account(
    payload: AccountDeletionRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> None:
    """Soft-deletes the caller's own account.

    The row is kept for audit with deleted_at / deletion_reason / deletion_note. The
    account is deactivated, every pairing is soft-unlinked, device tokens are dropped, and
    username / email / google_sub are tombstoned so they can be reused. Idempotent.
    """
    if current_user.deleted_at is not None:
        return

    now = datetime.now(timezone.utc).replace(tzinfo=None)
    # The id keeps the tag unique, so truncating the front of a long value can't collide.
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

    # Deleted directly, since current_user's relationships aren't loaded on an async session.
    await db.execute(delete(DeviceToken).where(DeviceToken.user_id == current_user.id))

    await db.commit()

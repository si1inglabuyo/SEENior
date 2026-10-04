import enum
import uuid
from datetime import datetime

from sqlalchemy import Enum, ForeignKey, JSON, String
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.sql import func

from app.db.session import Base


def _enum_values(enum_cls: type[enum.Enum]) -> list[str]:
    """Enum() stores member names by default; the Postgres enums use the lowercase values,
    so every Enum column must be told to use .value."""
    return [member.value for member in enum_cls]


class UserRole(str, enum.Enum):
    FAMILY_CONTACT = "family_contact"
    BARANGAY_RESPONDER = "barangay_responder"


class ContactType(str, enum.Enum):
    FAMILY = "family"
    BARANGAY_RESPONDER = "barangay_responder"


class UnlinkActor(str, enum.Enum):
    """Which side ended a pairing — recorded so an unlink is attributable after the fact."""

    SENIOR = "senior"
    FAMILY = "family"


class SeniorStatus(str, enum.Enum):
    """Whether a barangay still lists this senior on its active roster.

    Not the same as `deleted_at`, which is the senior's own decision and wipes the phone.
    `status` is the responder's bookkeeping and does not touch the phone. Marking a senior
    INACTIVE must not stop their alerts reaching the barangay, since that is the last tier.
    """

    ACTIVE = "active"
    INACTIVE = "inactive"


class RiskLevel(str, enum.Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class TriggerType(str, enum.Enum):
    INACTIVITY = "inactivity"
    MOVEMENT = "movement"
    SCREEN_IDLE = "screen_idle"
    CHARGING = "charging"
    SOS = "sos"
    ML_FLAG = "ml_flag"
    FALL_PATTERN = "fall_pattern"


class AlertStatus(str, enum.Enum):
    PENDING = "pending"
    ACKNOWLEDGED = "acknowledged"
    ESCALATED = "escalated"
    RESOLVED = "resolved"
    FALSE_POSITIVE = "false_positive"


class User(Base):
    """Cloud login for a family contact or barangay responder — seniors never log in here."""

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    # Null for Google-only accounts, which have no password.
    password_hash: Mapped[str | None] = mapped_column(String(255), nullable=True)
    role: Mapped[UserRole] = mapped_column(
        Enum(UserRole, name="user_role", values_callable=_enum_values)
    )
    # Display name shown on the senior's Contacts screen. Null for barangay accounts.
    full_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    phone: Mapped[str | None] = mapped_column(String(20), nullable=True)
    # Family login identity. Barangay responders log in by username, so this stays null for them.
    email: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    # Google's stable account ID, set only for Google-linked accounts.
    google_sub: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    # Firebase Auth's stable account ID (family accounts only).
    firebase_uid: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    # Scopes a barangay_responder's dashboard queries; unused for family contacts.
    barangay: Mapped[str | None] = mapped_column(String(128), nullable=True)
    # "en" / "fil", same codes as the senior app's language preference. Drives the family
    # app's language; barangay accounts never read it.
    language_preference: Mapped[str] = mapped_column(String(8), server_default="en")
    is_active: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    # Soft deletion (migration 0010): a null deleted_at means a live account. On delete the
    # account is deactivated, pairings are unlinked and username/email/google_sub are
    # tombstoned so they can be reused. deletion_reason is a stable code, not the label.
    deleted_at: Mapped[datetime | None] = mapped_column(nullable=True)
    deletion_reason: Mapped[str | None] = mapped_column(String(64), nullable=True)
    deletion_note: Mapped[str | None] = mapped_column(String(500), nullable=True)

    contacts: Mapped[list["Contact"]] = relationship(back_populates="user")
    # delete-orphan so a deactivated account's tokens stop receiving pushes.
    device_tokens: Mapped[list["DeviceToken"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )

    @property
    def has_password(self) -> bool:
        """True for Google-only accounts. The family app uses it to offer "Set a password"
        instead of "Change password"."""
        return self.password_hash is not None


class DeviceToken(Base):
    """One FCM registration token (one app install on one device) for a user.

    Its own table because a user can have several devices, and FCM expires tokens
    individually. `token` is globally unique so a device handed to someone else moves to
    the new account. See register_device in api/routes/devices.py.
    """

    __tablename__ = "device_tokens"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    token: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    platform: Mapped[str] = mapped_column(String(16), server_default="android")
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    # Refreshed whenever the app re-registers, so stale tokens can be pruned.
    last_seen_at: Mapped[datetime] = mapped_column(server_default=func.now())

    user: Mapped["User"] = relationship(back_populates="device_tokens")


class Senior(Base):
    """Cloud record for a senior: identity only. The Routine Fingerprint stays on the phone."""

    __tablename__ = "seniors"

    id: Mapped[int] = mapped_column(primary_key=True)
    sync_id: Mapped[uuid.UUID] = mapped_column(
        default=uuid.uuid4, unique=True, index=True
    )
    first_name: Mapped[str] = mapped_column(String(64))
    last_name: Mapped[str] = mapped_column(String(64))
    age: Mapped[int] = mapped_column(server_default="0")
    gender: Mapped[str] = mapped_column(String(16), server_default="unknown")
    barangay: Mapped[str] = mapped_column(String(128))
    address: Mapped[str] = mapped_column(String(255))
    mobile_number: Mapped[str] = mapped_column(String(20))
    invite_code: Mapped[str | None ] = mapped_column(String(6), nullable=True)
    invite_code_expires_at: Mapped[datetime | None] = mapped_column(nullable=True)
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    # Device health from POST /seniors/{sync_id}/heartbeat, overwritten each time. Kept as
    # single values, not a history, because a charge history would reveal routine. Null
    # means never heard from, which is different from 0%.
    last_seen_at: Mapped[datetime | None] = mapped_column(nullable=True)
    battery_percent: Mapped[int | None] = mapped_column(nullable=True)
    is_charging: Mapped[bool | None] = mapped_column(nullable=True)

    # The phone's FCM token, and when the server last used it to wake the phone. Stored on
    # `seniors` because a senior has no users row (see migration 0008). Refreshed on every
    # heartbeat. Null means this phone can't be woken.
    push_token: Mapped[str | None] = mapped_column(String(255), nullable=True)
    last_nudge_at: Mapped[datetime | None] = mapped_column(nullable=True)

    # SHA-256 of the key this senior's phone sends in X-Device-Key (migration 0014; see
    # app/core/device_key.py). Null until the phone has claimed a key.
    device_key_hash: Mapped[str | None] = mapped_column(String(64), nullable=True)

    # Soft deletion (migration 0010): a null deleted_at means a live record. Barangay
    # dashboard queries must exclude rows where deleted_at is set.
    deleted_at: Mapped[datetime | None] = mapped_column(nullable=True)
    deletion_reason: Mapped[str | None] = mapped_column(String(64), nullable=True)
    deletion_note: Mapped[str | None] = mapped_column(String(500), nullable=True)

    # Barangay roster state (migration 0012); see SeniorStatus. It controls whether a
    # responder counts this senior on their roster, never whether an alert reaches them.
    # Defaults to "active" so no row is left undefined.
    status: Mapped[SeniorStatus] = mapped_column(
        Enum(SeniorStatus, name="senior_status", values_callable=_enum_values),
        server_default=SeniorStatus.ACTIVE.value,
        nullable=False,
    )
    # Who last changed the status and when. Null for rows that were never changed.
    status_changed_at: Mapped[datetime | None] = mapped_column(nullable=True)
    status_changed_by: Mapped[int | None] = mapped_column(
        ForeignKey("users.id"), nullable=True
    )

    contacts: Mapped[list["Contact"]] = relationship(back_populates="senior")
    alerts: Mapped[list["Alert"]] = relationship(back_populates="senior")


class Contact(Base):
    """Links a Users account (family or barangay responder) to a senior.

    Unlinking is soft: the row keeps `unlinked_at` / `unlinked_by` for the audit trail, so
    user-facing queries must filter on `unlinked_at IS NULL` (see `active_contacts()` in
    api/routes/contacts.py). Uniqueness is a partial index over active rows only
    (`uq_contact_pair_active`, migration 0005), so a pair can link, unlink and link again.
    """

    __tablename__ = "contacts"

    id: Mapped[int] = mapped_column(primary_key=True)
    senior_id: Mapped[int] = mapped_column(ForeignKey("seniors.id"))
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    contact_type: Mapped[ContactType] = mapped_column(
        Enum(ContactType, name="contact_type", values_callable=_enum_values)
    )
    # Per-pairing: how this family member relates to the senior ("daughter", "son", ...).
    relationship_label: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    # NULL means the pairing is live. Both set together, never one without the other.
    unlinked_at: Mapped[datetime | None] = mapped_column(nullable=True)
    unlinked_by: Mapped[UnlinkActor | None] = mapped_column(
        Enum(UnlinkActor, name="unlink_actor", values_callable=_enum_values), nullable=True
    )

    senior: Mapped["Senior"] = relationship(back_populates="contacts")
    user: Mapped["User"] = relationship(back_populates="contacts")

    @staticmethod
    def is_active():
        """WHERE clause for "this pairing is still live". Every query that decides what a
        user may see or do must include it, or a removed contact keeps access."""
        return Contact.unlinked_at.is_(None)


class Alert(Base):
    """Alert metadata only — never raw sensor readings; `sync_id` avoids cross-device ID collisions."""

    __tablename__ = "alerts"

    id: Mapped[int] = mapped_column(primary_key=True)
    sync_id: Mapped[uuid.UUID] = mapped_column(
        default=uuid.uuid4, unique=True, index=True
    )
    senior_id: Mapped[int] = mapped_column(ForeignKey("seniors.id"))
    risk_level: Mapped[RiskLevel] = mapped_column(
        Enum(RiskLevel, name="risk_level", values_callable=_enum_values)
    )
    trigger_type: Mapped[TriggerType] = mapped_column(
        Enum(TriggerType, name="trigger_type", values_callable=_enum_values)
    )
    status: Mapped[AlertStatus] = mapped_column(
        Enum(AlertStatus, name="alert_status", values_callable=_enum_values),
        default=AlertStatus.PENDING,
    )
    # Anonymous cluster ID captured only at alert-trigger time — never raw coordinates.
    location_cluster_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    escalation_steps: Mapped[list | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    # When the phone's detector actually fired (sent by the client, null on older rows).
    # Escalation deadlines still use created_at, since a client clock can't be trusted. This
    # is only for showing the real time and for the delivery-time metric.
    triggered_at: Mapped[datetime | None] = mapped_column(nullable=True)
    resolved_at: Mapped[datetime | None] = mapped_column(nullable=True)

    senior: Mapped["Senior"] = relationship(back_populates="alerts")

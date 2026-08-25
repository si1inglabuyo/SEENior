import enum
import uuid
from datetime import datetime

from sqlalchemy import Enum, ForeignKey, JSON, String
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.sql import func

from app.db.session import Base


def _enum_values(enum_cls: type[enum.Enum]) -> list[str]:
    """SQLAlchemy's Enum() persists the member NAME by default; the migration's
    Postgres enum types were created with the lowercase VALUES, so every Enum()
    column below must be told to use .value instead."""
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
    # Nullable: a Google-only account (no password ever set) has no hash. login()
    # gives those users a "use Google Sign-In" message instead of a generic 401.
    password_hash: Mapped[str | None] = mapped_column(String(255), nullable=True)
    role: Mapped[UserRole] = mapped_column(
        Enum(UserRole, name="user_role", values_callable=_enum_values)
    )
    # Family member's display identity, shown on the senior's Contacts screen.
    # Nullable because barangay-responder accounts are seeded without them.
    full_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    phone: Mapped[str | None] = mapped_column(String(20), nullable=True)
    # Family login identity (barangay responders log in by username instead, per
    # CLAUDE.md §2 - pre-assigned credentials, so this stays nullable for them).
    email: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    # Google's stable per-account subject ID - set only for Google-linked accounts,
    # used to recognize a returning Google sign-in independent of email changes.
    google_sub: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    # Scopes a barangay_responder's dashboard queries; unused for family contacts.
    barangay: Mapped[str | None] = mapped_column(String(128), nullable=True)
    is_active: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    contacts: Mapped[list["Contact"]] = relationship(back_populates="user")
    # delete-orphan: a deactivated account's tokens must not outlive it and keep
    # receiving pushes for seniors it is no longer linked to.
    device_tokens: Mapped[list["DeviceToken"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )


class DeviceToken(Base):
    """One FCM registration token — i.e. one installed app on one device — for a user.

    A separate table rather than a `users.fcm_token` column, for two reasons that both
    bite in production:

    * One family member may run the app on a phone AND a tablet. A single column silently
      overwrites one with the other, so whichever device registered last is the only one
      that ever rings.
    * Tokens expire on their own — a reinstall, a "clear data", or a rotation by Google.
      FCM reports those per-token as UNREGISTERED, and the correct response is to delete
      that one row, not to blank a user's only column.

    `token` is globally unique, not unique per user: if a device is handed to someone else
    who signs in, the SAME token must move to the new account or the previous owner keeps
    receiving a stranger's alerts. See register_device in api/routes/devices.py.
    """

    __tablename__ = "device_tokens"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    token: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    platform: Mapped[str] = mapped_column(String(16), server_default="android")
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    # Refreshed every time the app re-registers, so a token that has gone quiet for months
    # can be pruned without waiting for FCM to declare it dead.
    last_seen_at: Mapped[datetime] = mapped_column(server_default=func.now())

    user: Mapped["User"] = relationship(back_populates="device_tokens")


class Senior(Base):
    """Privacy-stripped cloud record: identifying info only — the Routine Fingerprint stays on-device."""

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

    # Device health, written by POST /seniors/{sync_id}/heartbeat and overwritten every
    # time. Deliberately three scalars rather than a history: the current charge says
    # whether the phone can keep monitoring, while a series of charge readings is
    # behavioural -- when someone plugs in is roughly when they go to bed -- and that is
    # the routine CLAUDE.md 11 keeps on the device. NULL means never heard from, which is
    # a different answer from 0% and draining.
    last_seen_at: Mapped[datetime | None] = mapped_column(nullable=True)
    battery_percent: Mapped[int | None] = mapped_column(nullable=True)
    is_charging: Mapped[bool | None] = mapped_column(nullable=True)

    contacts: Mapped[list["Contact"]] = relationship(back_populates="senior")
    alerts: Mapped[list["Alert"]] = relationship(back_populates="senior")


class Contact(Base):
    """Links a Users account (family or barangay responder) to a senior.

    Unlinking is SOFT: the row survives with `unlinked_at`/`unlinked_by` set, so a
    pairing that ended is still auditable (who dropped whom, and when) instead of
    vanishing. Everything user-facing must therefore filter on `unlinked_at IS NULL`
    — see `active_contacts()` in api/routes/contacts.py.

    Uniqueness is enforced by a PARTIAL index (`uq_contact_pair_active`, defined in
    migration 0005) covering only active rows, not by a plain UniqueConstraint: the
    same senior and family member may legitimately link, unlink, and link again, and
    each of those pairings is its own historical row.
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
        user may SEE or DO must include it — a soft-unlinked row is invisible and carries
        no access rights, so omitting it silently re-grants a removed contact access to
        the senior's alerts (CLAUDE.md §11)."""
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
    resolved_at: Mapped[datetime | None] = mapped_column(nullable=True)

    senior: Mapped["Senior"] = relationship(back_populates="alerts")

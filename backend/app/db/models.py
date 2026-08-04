import enum
import uuid
from datetime import datetime

from sqlalchemy import Enum, ForeignKey, JSON, String, UniqueConstraint
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
    contacts: Mapped[list["Contact"]] = relationship(back_populates="senior")
    alerts: Mapped[list["Alert"]] = relationship(back_populates="senior")


class Contact(Base):
    """Links a Users account (family or barangay responder) to a senior."""

    __tablename__ = "contacts"
    __table_args__ = (UniqueConstraint("senior_id", "user_id", name="uq_contact_pair"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    senior_id: Mapped[int] = mapped_column(ForeignKey("seniors.id"))
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    contact_type: Mapped[ContactType] = mapped_column(
        Enum(ContactType, name="contact_type", values_callable=_enum_values)
    )
    # Per-pairing: how this family member relates to the senior ("daughter", "son", ...).
    relationship_label: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    senior: Mapped["Senior"] = relationship(back_populates="contacts")
    user: Mapped["User"] = relationship(back_populates="contacts")


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

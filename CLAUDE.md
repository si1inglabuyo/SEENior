# SEENior

**A Mobile-Based Passive Monitoring System for Seniors Using Routine Fingerprinting and Progressive Alert Escalation**

Capstone Project — Department of Information Technology, Polytechnic University of the Philippines (PUP)

---

## 1. What This System Does

SEENior passively monitors elderly individuals (seniors) who live alone or unaccompanied, using only their existing Android smartphone — no wearables required. The app silently learns each senior's daily behavioral routine ("Routine Fingerprint") over a 14-day baseline period, then continuously watches for deviations from that personal baseline. When a meaningful deviation is detected, the system runs through a progressive escalation chain: it first asks the senior directly if they are safe, then notifies a registered family contact if there's no response, and finally alerts an on-duty barangay (local community) responder if the family contact also doesn't respond.

The goal is to close the gap where elderly people living alone go unnoticed for days when something goes wrong — a documented problem in the Philippines — without requiring the senior to wear a device, perform daily check-ins, or learn anything new.

### Core Design Principles

- **Fully passive** — the senior does nothing after initial setup. No daily check-ins, no app interactions required.
- **Personalized, not population-based** — every senior is compared only against their own historical behavior, never against a generic "normal."
- **Privacy-first** — raw behavioral data never leaves the senior's phone. Only alert metadata syncs to the cloud.
- **Offline-first** — full detection works with zero internet connectivity. SMS fallback covers notification when data is unavailable.
- **Human-in-the-loop** — the senior always gets the first chance to self-cancel a false alarm before anyone else is notified.
- **Community-integrated** — the barangay responder is a first-class escalation tier, not an afterthought. This is the project's key local innovation.

---

## 2. The Three User Roles

| Role | Platform | What They Do |
|---|---|---|
| **Senior** | Android mobile app | Onboards once, then the app runs invisibly in the background. Receives wellness check-in prompts and can press a one-swipe SOS button. No login — account is auto-created locally. |
| **Family Contact** | Android mobile app | Pairs with a senior via a time-limited invite code. Receives push/SMS alerts, can acknowledge, escalate, contact the senior, or mark false positives. Can monitor up to 3 seniors. |
| **Barangay Responder** | Web dashboard (desktop browser) | Logs in with a pre-assigned username (credentials are hardcoded/issued by the OSCA officer, not self-registered). Views escalated high-risk alerts, incident logs, and senior records within their assigned barangay. |

Each senior can register up to **5 family contacts**.

---

## 3. System Architecture

```
┌─────────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│   SENIOR'S PHONE     │     │  FAMILY CONTACT      │     │  BARANGAY RESPONDER  │
│   (Android, Kotlin)  │     │  PHONE (Android)     │     │  (Web, React)        │
│                      │     │                      │     │                      │
│  - Sensor collection │     │  - Push notifications│     │  - Incident dashboard│
│  - Local SQLite DB   │     │  - Alert dashboard    │     │  - Log history       │
│  - Detection engine  │     │  - Acknowledge/escalate│    │  - Senior management │
│    (on-device)       │     │                      │     │                      │
└──────────┬───────────┘     └──────────┬───────────┘     └──────────┬───────────┘
           │                            │                            │
           │  alert metadata sync       │  push (FCM) / SMS fallback │
           └────────────┬───────────────┴────────────┬───────────────┘
                         │                            │
                ┌────────▼────────────────────────────▼────────┐
                │         CLOUD BACKEND (Render/Railway)        │
                │                                               │
                │  FastAPI (Python) — REST API + auth           │
                │  PostgreSQL — Users, Seniors, Contacts, Alerts│
                │  Firebase Cloud Messaging — push delivery     │
                │  Semaphore PH — SMS fallback delivery         │
                └───────────────────────────────────────────────┘
```

### Why two databases (SQLite + PostgreSQL)?

These are **not duplicates** — they serve different purposes:

- **Local SQLite (on senior's phone)** = detection engine + privacy boundary. All raw sensor data lives and dies here. Runs fully offline. Never uploads raw behavioral data to the cloud.
- **Server PostgreSQL (cloud)** = coordination layer for multi-user access. Only stores alert *metadata* (risk level, timestamp, status) — never raw sensor readings — so family contacts and barangay responders can view status remotely.

Only 3 tables exist in both (`Seniors`, `Contacts`, `Alerts`), and even those have different field sets in each database (the cloud versions are privacy-stripped subsets).

---

## 4. Sensors Used

SEENior uses sensors built into every standard Android smartphone — no extra hardware:

| Sensor | What It Captures | Frequency |
|---|---|---|
| **Accelerometer** | Movement intensity (`movement_score`, 0.0–1.0) and inactivity duration. Also performs real-time **fall detection** via a 3-phase signature (free fall → impact spike → post-fall stillness). | Every 5 minutes (movement); real-time (fall detection) |
| **Gyroscope** | Confirms body rotation during a fall event — used alongside the accelerometer to improve fall detection accuracy. | Real-time during suspected fall events |
| **Step Counter** | Daily cumulative step count — a direct, low-power activity measure that complements the accelerometer. | Daily |
| **Screen State** | Screen idle duration and unlock count — reflects conscious device engagement. | Every 5 minutes |
| **Battery / Charging Status** | Whether the device is charging — gives context (e.g., resting-while-charging is less concerning than stationary-with-draining-battery). | Every 5 minutes |
| **GPS / Location** | Anonymous location cluster ID — **never raw coordinates, never continuous**. Captured only at the exact moment an alert fires. | Alert-trigger only |

**Deliberately excluded:** camera, microphone, continuous GPS, Bluetooth/Wi-Fi scanning — all rejected on privacy or battery grounds.

---

## 5. Detection Pipeline (Three Layers)

Detection runs entirely **on-device** inside the senior's app — no cloud round-trip needed for a detection decision.

### Layer 0 — Fall Detection (real-time, independent of baseline)
Watches raw accelerometer + gyroscope streams continuously for the fall signature. Operates from Day 1 — does not wait for the 14-day baseline. Immediately classified High-risk if detected, bypassing the normal wellness-prompt-first flow in favor of a compressed response window.

### Layer 1 — Median-MAD (every 5 minutes)
For each sensor signal (`inactivity_duration`, `movement_score`, `screen_idle`), compute a **Modified Z-Score** against the senior's personal 14-day rolling baseline, segmented into 4 time blocks (morning / afternoon / evening / night):

```
deviation_score = |current_value − median_value| / mad_value
```

- `|z|` between 2.5 and 3.5 → moderate anomaly → triggers wellness prompt
- `|z|` ≥ 3.5 → extreme anomaly → triggers wellness prompt (same first step — see escalation flow below)

Median + MAD (not mean + standard deviation) is used because it's robust to outlier days (e.g., one bad week doesn't permanently skew the baseline).

### Layer 2 — Isolation Forest (once daily)
Runs on the previous day's aggregated data (`Daily_Aggregates` table) across **all signals simultaneously**. Catches the case where no single signal crosses its individual threshold, but the *combined* pattern for the day is statistically unusual for that senior. Unsupervised — requires no labeled anomaly data (which can't ethically be collected for real emergencies).

### Layer 3 — Fuzzy Logic (per detected anomaly)
Takes the Layer 1 and/or Layer 2 output plus contextual factors (time of day, onboarding profile) and assigns a final **risk level: Low / Medium / High**. Low-risk anomalies are logged only — no notification. Medium triggers the wellness prompt. High triggers immediate/faster escalation.

### Why three layers and not one
Each catches what the others miss: Median-MAD catches sudden single-signal spikes in real time; Isolation Forest catches gradual multi-signal drift that no individual threshold would flag; Fuzzy Logic turns raw anomaly signals into a graduated, proportionate response instead of a binary alarm.

---

## 6. The 14-Day Baseline & Cold-Start Solution

A senior's Routine Fingerprint requires 14 days of real sensor data to be statistically reliable (Median/MAD need enough samples per time block to be stable; 14 days = 2 full weekly cycles).

**Problem:** the senior is unprotected for those first 14 days if the system has no data yet.

**Solution — Onboarding Questionnaire (warm-start / seed baseline):**
During first-time setup, the senior answers a few simple questions:

- Wake time
- Sleep time
- Whether they nap, and if so, nap time + duration
- Self-reported activity level (low / moderate / high)

These answers are converted into **conservative, wide-margin seed values** that pre-populate the Baseline table before any real sensor data exists — e.g., wake time defines when "morning" monitoring starts; nap time + duration creates a suppression window so a normal nap never triggers a false alarm. Real sensor data progressively replaces these seed values starting Day 1, fully replacing them by Day 14 (`seed_baseline_generated` flag tracks this transition).

**The SOS button works from Day 1 regardless of baseline status** — passive detection ramps up conservatively over 14 days, but conscious emergency response is never gated behind the learning period.

---

## 7. Escalation Flow

```
Anomaly detected (any risk level ≥ Medium, or fall detected, or SOS pressed)
            │
            ▼
   Wellness prompt → senior's screen
   "Are you safe and well?" (formal phrasing — NOT "Are you okay?", flagged as slang by panel)
   Senior has a response window to self-cancel
            │
   ┌────────┴────────┐
   │                 │
Responds "safe"   No response / "I need help"
   │                 │
Alert cancelled       ▼
                Family contact notified
                (push notification + SMS fallback via Semaphore PH)
                Family has a response window to acknowledge
                   │
          ┌────────┴────────┐
          │                 │
   Acknowledges        No acknowledgment
          │                 │
   Escalation halted        ▼
                    Barangay responder notified (web dashboard + SMS)
                    Final tier — always available even if family is unreachable
```

**SOS button** (one-swipe, senior-initiated) bypasses the normal wait times — it has only a short cancellation window (to catch accidental presses) and then notifies all parties simultaneously.

**Important interface requirement (from panel feedback):** the senior-facing wellness prompt must show *why* it's asking — a plain-language reason derived from what triggered the alert (e.g., "We noticed you haven't moved in a while during your usual active hours") — not just a bare "are you okay" with no context. This is built from the `trigger_type` and `time_block` fields already in the Alerts table; no extra database fields needed.

**Language preference:** the senior app supports an English/Filipino toggle, set during onboarding, stored in `Senior_Onboarding`. All senior-facing prompts and buttons must exist in both languages.

---

## 8. Database Schema (Reference)

### Local SQLite (on senior's phone) — 10 tables
`Seniors`, `Contacts`, `Sensor_Data`, `Daily_Aggregates`, `Baseline`, `Alerts`, `Pending_Alerts`, `False_Positives`, `ML_Model_Metadata`, `Senior_Onboarding`

### Server PostgreSQL (cloud) — 4 tables
`Users`, `Seniors`, `Contacts`, `Alerts`

### Tables that exist in both (with different field sets)
`Seniors`, `Contacts`, `Alerts` — local versions have full detection-relevant fields; cloud versions are privacy-stripped metadata-only subsets.

### Cloud PostgreSQL field lists (as of migration 0004)

- **`users`** — `id`, `username`, `password_hash` (nullable — null for Google-only accounts), `role` (`family_contact` / `barangay_responder`), `full_name`, `phone`, `email` (nullable/unique — family sign-in identity; barangay responders log in by `username` instead and leave this null), `google_sub` (nullable/unique — Google's stable per-account ID), `barangay` (scopes a barangay responder's dashboard queries; unused for family contacts), `is_active`, `created_at`.
- **`seniors`** — `id`, `sync_id` (anonymous UUID used for all cloud-facing references, per §11), `first_name`, `last_name`, `age`, `gender`, `barangay`, `address` (joined PSGC-validated string), `mobile_number`, `invite_code` (nullable, 6-digit, time-limited), `invite_code_expires_at`, `created_at`.
- **`contacts`** — `id`, `senior_id`, `user_id`, `contact_type` (`family` / `barangay_responder`), `relationship_label` (nullable — e.g. "daughter", "son"; named `_label` to avoid colliding with SQLAlchemy's `relationship()`), `created_at`. Unique on `(senior_id, user_id)` — one contact row per senior/family pairing.
- **`alerts`** — `id`, `sync_id`, `senior_id`, `risk_level` (`low`/`medium`/`high`), `trigger_type` (`inactivity`/`movement`/`screen_idle`/`charging`/`sos`/`ml_flag`/`fall_pattern`), `status` (`pending`/`acknowledged`/`escalated`/`resolved`/`false_positive`), `location_cluster_id` (nullable, anonymous cluster — never raw coordinates), `escalation_steps` (JSON), `created_at`, `resolved_at`.

None of the fields above are raw sensor/behavioral data — they're identity, pairing, and alert-metadata fields added as pairing/auth features were built (multi-senior linking, Google Sign-In, PH address validation). The privacy boundary from §11 (no raw sensor data leaves the senior's device) remains intact.

### Key fields worth knowing
- `Alerts.trigger_type` — what caused the alert: `inactivity`, `movement`, `screen_idle`, `charging`, `sos`, `ml_flag`, or `fall_pattern`
- `Alerts.risk_level` — `low` / `medium` / `high`, assigned by Fuzzy Logic
- `Alerts.deviation_score` — the Median-MAD z-score (NOT used for Isolation Forest output — IF uses `trigger_type = 'ml_flag'` instead, since its score mechanism is path-length-based, not z-score-based)
- `Alerts.escalation_steps` — JSON array logging the full escalation timeline for audit purposes
- `Baseline.median_value` / `Baseline.mad_value` — per feature, per time_block; this is the Routine Fingerprint
- `Senior_Onboarding.seed_baseline_generated` — flag tracking warm-start → real-baseline transition
- `Contacts.contact_type` — `family` or `barangay_responder`

---

## 9. Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Senior + Family mobile apps | **Kotlin / Java**, Android SDK, min API 26 (Android 8.0) | API 26 chosen specifically for Foreground Service support (continuous background sensor access without being killed by the OS) |
| Local on-device DB | **SQLite** (consider Room as the ORM layer over it) | Offline-first, no server dependency for detection |
| ML on-device or hybrid | **Scikit-learn** Isolation Forest, exported as `.pkl`, trained server-side and shipped to device, or run via lightweight inference | |
| Backend API | **Python + FastAPI** | REST endpoints, auth, alert routing |
| Cloud database | **PostgreSQL** | Multi-user concurrent access for dashboards |
| Push notifications | **Firebase Cloud Messaging (FCM)** | Free tier sufficient for pilot scale |
| SMS fallback | **Semaphore PH** | Philippine SMS gateway, ~₱0.50/SMS |
| Web dashboard (barangay) | **React** (Vite) | Desktop browser, not mobile |
| Hosting | **Render** or **Railway** | Free tier for pilot |
| Testing | **JUnit + Mockito + Espresso** (Android), **Pytest** (backend) | Mockito is used to *simulate* sensor data for accuracy testing — there is no waiting for real emergencies |
| Dev DB tools | **DB Browser for SQLite**, **pgAdmin 4** | |

---

## 10. Accuracy & Performance Targets (Pilot Validation Goals)

| Metric | Target |
|---|---|
| Anomaly detection accuracy | ≥ 85% |
| False-positive rate | ≤ 15% |
| Battery consumption increase | ≤ 10% (stretch goal documented elsewhere as < 5%/day) |
| Alert delivery time | ≤ 30 seconds |

Detection accuracy is validated via **simulated sensor data injection** in test cases, not by waiting for real senior emergencies — that would be unethical and impractical. Known sensor values are injected (e.g., simulate prolonged inactivity, simulate a fall) and the system's output is checked against the expected ground truth.

---

## 11. Privacy & Security Requirements (Non-Negotiable)

- Raw sensor data **never** leaves the senior's device. Period.
- Cloud-synced alert data uses an anonymous `sync_id` (UUID) — not the local sequential `alert_id` — to avoid both ID collisions across devices and leaking volume information.
- GPS location is captured **only at alert-trigger time**, never continuously, stored as an anonymous cluster ID, never raw coordinates.
- `Sensor_Data` raw records are purged nightly after being rolled into `Daily_Aggregates` — both for storage efficiency and data minimization.
- Role-based access control: family contacts only see their linked senior(s); barangay responders only see seniors within their assigned barangay.
- Legal basis for sharing senior name/location with barangay during an active alert: **RA 10173 (Philippine Data Privacy Act of 2012), Section 12(c) — vital interests provision**. This is the standard justification to cite — don't treat sharing this data during an emergency as a privacy violation; it's an explicitly permitted exception.
- Invite/pairing codes (used to link a family contact to a senior) expire after 5 minutes and have a 5-minute regeneration cooldown — standard short-lived-OTP security practice.

---

## 12. Known Limitations (Documented, Not Bugs)

- Android-only. iOS's background execution restrictions are incompatible with continuous passive sensor monitoring at the depth this system requires.
- If the senior leaves their phone at home, the system cannot distinguish that from genuine inactivity — this is a fundamental limitation of phone-based (not wearable-based) passive monitoring.
- Not a medical diagnostic tool. It detects *behavioral deviation*, not specific medical conditions. It signals "something may be wrong," for human follow-up — it does not diagnose stroke, cardiac arrest, etc.
- Designed for cognitively intact seniors who can respond to a wellness prompt. Seniors with moderate-to-severe dementia are outside the primary target population for the self-cancellation mechanism (though the SOS button and passive detection still function).
- SMS fallback depends on Semaphore PH's gateway availability and cellular signal — not a 100% guarantee in zero-signal areas.

---

## 13. Suggested Build Order

1. **Backend skeleton first** — FastAPI app + PostgreSQL schema (Users, Seniors, Contacts, Alerts) + basic auth
2. **Android project skeleton** — target API 26+, set up Room/SQLite schema matching the 10 local tables
3. **Onboarding flow** — senior questionnaire → seed baseline generation logic
4. **Sensor collection service** — Foreground Service, 5-minute polling, write to `Sensor_Data`
5. **Nightly aggregation job** — roll `Sensor_Data` → `Daily_Aggregates`, purge raw records
6. **Median-MAD detection engine** — compute rolling baseline, compute z-scores in real time
7. **Isolation Forest training pipeline** — Python/Scikit-learn, export model for device or server-side inference
8. **Fuzzy Logic risk classifier** — combine Layer 1 + Layer 2 outputs + time context → risk_level
9. **Wellness prompt + escalation chain** — including the context-message-before-prompt requirement and English/Filipino toggle
10. **Fall detection (Layer 0)** — accelerometer + gyroscope real-time signature detection
11. **FCM push integration** + **Semaphore PH SMS fallback**
12. **Family contact mobile app** — alert dashboard, acknowledge/escalate/mark-false-positive actions
13. **Barangay web dashboard (React)** — incident log, analytics (alerts-this-week chart, outcome donut chart), senior management
14. **Testing layer** — Mockito-based sensor simulation test cases for detection accuracy validation

---

## 14. Things AI Coding Agents Should NOT Do

- Do not suggest replacing Median-MAD with standard mean/stddev z-scores — Median-MAD's outlier-resistance is a deliberate, cited design choice.
- Do not suggest sending raw sensor data to the cloud "for better detection" — this directly violates a core privacy requirement of the system.
- Do not implement the wellness prompt with the literal text "Are you okay?" — this was specifically flagged during panel defense as inappropriate informal slang for elderly Filipino users. Use "Are you safe and well?" (English) or "Ligtas po ba kayo? Maayos po ba kayo?" (Filipino).
- Do not assume the barangay responder logs in with an email — they use a **pre-assigned username**, since credentials are issued/hardcoded by the OSCA officer, not self-registered.
- Do not collapse the three detection layers into one combined score — they are deliberately separate (z-score, path-length anomaly score, and fuzzy risk level are three different outputs serving three different purposes).
- Do not design the system to wait for real emergencies to validate detection accuracy — use simulated/injected sensor test data instead.
- Do not add continuous GPS tracking "for convenience" — location is alert-trigger-only by design.

---

*This README is meant to be the single source of context for AI coding agents (Claude Code, Codex, etc.) working on this project. Update it as the system design evolves so agents always have current ground truth.*

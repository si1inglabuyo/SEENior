# Handoff — `seniors.status` now exists, so Deactivate can stop being browser-only

**To:** the `dashboard` lane
**From:** `main` (Android + backend)
**Date:** 2026-09-18

---

## What you were blocked on

`seniorActions.js` has said this since 2026-09-09 (`6012547`):

> *"When `seniors.status` and the endpoints exist this is a PATCH; today it persists to this
> browser — see SeniorRoster."*

The column and the endpoint now exist. `db/models.py` and `migrations/` are `main`-owned
(CLAUDE.md §15), so there was nothing you could do from your side — this was owed to you,
not by you.

## What landed

**Migration `0012_add_senior_status.py`**, three columns on `seniors`:

| column | type | notes |
|---|---|---|
| `status` | enum `senior_status` (`active` / `inactive`) | `NOT NULL`, server default `active`, indexed |
| `status_changed_at` | timestamp, nullable | null = never flipped |
| `status_changed_by` | int FK → `users.id`, nullable | `ON DELETE SET NULL` |

Every existing senior was backfilled to `active` by the server default, so nothing
disappears from your roster when this deploys.

**Endpoint:**

```
PATCH /seniors/{sync_id}/status
Authorization: Bearer <barangay_responder JWT>
Body: {"status": "inactive"}   // or "active"
```

Returns `{sync_id, status, status_changed_at, status_changed_by}` so you can render the new
state without refetching the record.

- Gated by `require_role(BARANGAY_RESPONDER)` and scoped to the responder's own barangay,
  same as every route in your `barangay.py`.
- Out-of-barangay and soft-deleted seniors both return **404**, not 403 — which seniors
  exist elsewhere is not a responder's to learn.
- **Idempotent.** Setting the status it already has returns the row untouched rather than
  rewriting the audit fields to claim a change nobody made.

It lives in `routes/seniors.py`, not `barangay.py`, purely so this handoff doesn't collide
with your file. Move it later if you'd rather it sat with the other responder routes — just
do it on your branch, not mine.

`SeniorOut` also gained `status`, defaulted to `active`, so a roster list can render its
badges from the call you already make.

---

## ⚠️ Two things to change on your side

### 1. Your confirm copy is now wrong, and it was wrong before

`DEACTIVATE_ACTION.dialogMessage` currently says:

> *"**Monitoring stops** and this senior drops off the active roster."*

**Monitoring does not stop, and deliberately cannot be made to.** Two reasons:

- The server cannot reach into a handset and switch its foreground service off. The phone
  keeps sampling, keeps detecting, and keeps raising alerts regardless of what any column
  here says.
- More importantly, an inactive senior's alerts **still escalate to the barangay**, and
  that is a deliberate safety decision rather than an oversight. The barangay is the *last*
  tier in the chain. With the family contacts possibly unlinked too, letting a roster flag
  drop tier 3 would produce an alert with nowhere left to go and nobody told it went
  nowhere — for the person least able to notice. Roster hygiene is worth having; it is not
  worth a silent hole in the escalation chain.

There is a test pinning this (`tests/test_senior_status.py::test_escalation_never_consults_roster_status`),
because the way it breaks is somebody adding a filter in good faith while tidying a query.

Suggested replacement copy:

> *"This senior is removed from your active roster. Their record and alert history are kept,
> and if their phone raises an alert it will still reach you."*

### 2. Your roster queries still need two filters

`barangay.py` currently filters on neither. Both are one clause each:

```python
# barangay.py:256 (roster), :123 (alert feed), :305 (senior detail)
.where(
    Senior.barangay == barangay,
    Senior.deleted_at.is_(None),        # senior deleted their own account (§11a)
    Senior.status == SeniorStatus.ACTIVE,   # roster view only — see below
)
```

- **`deleted_at`** — this one is required by CLAUDE.md §11a and is a correctness fix, not a
  preference. Right now a senior who deleted their account still shows on your roster as
  though they were being watched, when their phone has been wiped and nothing is watching
  them at all. **Note you'll need to merge `main` first** — your branch is ~50 commits
  behind and `Senior.deleted_at` doesn't exist in your copy of `models.py` yet.
- **`status`** — filter this on the *roster* view, but not on the incident feed. An alert
  from an inactive senior must still appear, since it still reaches you; showing it with an
  "inactive record" badge is better than hiding it.

---

## Before any of this works

`main` has to be pushed and Render redeployed, and migration 0012 applied to Supabase. Until
then `PATCH /seniors/{sync_id}/status` returns 404 in production. Ask before wiring the UI to
it, so you don't debug a 404 that is just a pending deploy.

Also worth knowing: `main` changed the JWT subject from username to user id in `698a11b`, so
**every dashboard session will be signed out once** when that deploys. That's expected, not a
bug in your sign-in.

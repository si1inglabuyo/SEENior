# Barangay Dashboard — scope rules for this directory

**Read this before editing anything.** SEENior is built by two people in parallel. This file
defines the boundary of the dashboard lane. The root `../CLAUDE.md` still applies for what the
system *is* — this file governs what may be *changed*.

---

## 1. What you may edit

| Allowed | Path |
|---|---|
| The dashboard, all of it | `barangay-dashboard/**` |
| The dashboard's API route | `backend/app/api/routes/barangay.py` |
| The dashboard's response shapes | `backend/app/schemas/barangay.py` |

Those two backend files are in this lane on purpose: adding a field to an incident card should
not require waiting for anybody.

## 2. What you must NOT edit

Everything else. In particular:

- `android-app/**` — the senior and family apps.
- `backend/` beyond the two files above — **especially** `api/escalation.py`, `core/push.py`,
  `db/models.py`, `core/config.py`, and anything under `migrations/`.
- The root `CLAUDE.md`.

**Migrations are the hard one.** If a change here needs a new database column, do **not** write a
migration and do not add a field to `db/models.py`. Say so and stop — the schema is owned by the
other lane, and two people writing Alembic revisions against one `alembic_version` row produces a
conflict that has to be untangled by hand on the live database.

If a task seems to require a file outside the allowed list: **say so and stop.** Do not edit it,
do not work around it, and do not copy the logic into this directory to avoid touching it.
"I need a field the API doesn't return yet" is a normal thing to report, not a problem to solve
by widening your own scope.

## 3. Branch and PR workflow

- Work on branch **`dashboard`**, never on `main`.
- Rebase on `main` before opening a PR; `main` moves under you often.
- PRs go **into `main`**, and the repo owner merges them.
- Never push to `main` directly, and never force-push a shared branch.

## 4. You are clicking against PRODUCTION

`src/api.js:4` defaults to `https://seenior.onrender.com`, so with no `.env` file this dashboard
talks to the **live** backend and the **live** database.

That is deliberate — it means no local Postgres, no Python, no Android Studio to run the UI. But
it has a consequence worth stating plainly: **Acknowledge and Resolve from your machine mutate
real alert rows.** Those rows are what the other lane is testing escalation against, and a
resolved alert stops the escalation chain for real.

So: read freely, and think before you click an action button. If you need to exercise the write
paths repeatedly, ask for a test senior to be set up rather than using whichever incident happens
to be on screen.

## 5. Running it

```bash
cd barangay-dashboard
npm install
npm run dev
```

**Node 22** or newer (Vite 8 needs ≥20.19). Nothing else — no backend, no database.

Log in with the barangay responder credentials the repo owner gives you. Responders sign in with
a **pre-assigned username, never an email** (root `CLAUDE.md` §14) — the account is issued by the
OSCA officer, not self-registered, and a login form asking for an email is a bug.

## 6. Things worth knowing before you change the UI

- A responder only ever sees seniors **in their own barangay**. That scoping is enforced
  server-side; do not add a client-side filter that pretends to do it, and do not build a screen
  that assumes a responder can see everyone.
- **"Lives alone" is not a risk level.** Its badge is amber, deliberately not red — it is context
  telling the responder nobody else was notified, not a fourth severity. Do not restyle it to
  match the risk colours.
- Sharing a senior's name and location with the barangay during an active alert is lawful under
  **RA 10173 §12(c)** (vital interests). It is not a privacy leak to be designed around.
- Alert *metadata* is all the cloud has. There are no sensor readings, no coordinates, and no
  behavioural history to display — only an anonymous `location_cluster_id`. If a design needs raw
  data to be useful, the design is wrong, not the API (root `CLAUDE.md` §11).
- `labels.js` holds the human wording, including `STEP_LABEL` for escalation timeline steps. A new
  backend step type appearing as a raw string in the UI means it needs a label here.

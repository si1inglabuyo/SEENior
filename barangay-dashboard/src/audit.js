// Access audit log — RA 10173 (Data Privacy Act of 2012) §23(a).
//
// §23(a) requires that government agency personnel handling personal (and especially
// sensitive personal) information have their access to it both *controlled* and *recorded*.
// The dashboard already does the "controlled" half — a responder only ever sees seniors in
// their own barangay, enforced server-side. This module adds the "recorded" half for the
// three disclosures that go BEYOND the always-on default profile view:
//
//   - 'senior_phone_reveal'  — un-masking a senior's phone number
//   - 'active_alert_view'    — opening the active-alert view (full home address +
//                              emergency-contact names/relationships/phones)
//   - 'full_history_view'    — expanding a senior's full alert history
//
// It also closes the "no audit logging" gap called out in the thesis's environmental
// analysis.
//
// WHERE THE LOG LIVES: this build appends to localStorage on the responder's own machine.
// A centrally-held, tamper-evident audit trail needs a server table (`barangay_audit_log`)
// and a `POST /barangay/audit` route — both are schema/route changes owned by the `main`
// lane (barangay-dashboard/CLAUDE.md §2), so they are deliberately NOT part of this change.
// Once that endpoint exists, add the one fetch marked below; this file then keeps working
// as the offline fallback and nothing else changes.

import { currentUserId } from './api'

const KEY = 'seenior.accessAudit'
const MAX_ENTRIES = 500

export function recordAccess(action, target = {}) {
  const entry = {
    at: new Date().toISOString(),
    user_id: currentUserId(),
    action,
    target, // { sync_id, ... } — the record that was opened
  }

  // --- once `POST /barangay/audit` exists (owned by the `main` lane), send the
  //     authoritative copy to the server here; keep the localStorage write below as the
  //     offline fallback:
  //     api('/barangay/audit', { method: 'POST', body: JSON.stringify(entry) }).catch(() => {})

  try {
    const log = JSON.parse(localStorage.getItem(KEY) || '[]')
    log.push(entry)
    if (log.length > MAX_ENTRIES) log.splice(0, log.length - MAX_ENTRIES)
    localStorage.setItem(KEY, JSON.stringify(log))
  } catch {
    /* private mode / quota exceeded — the console line below is then the only trace */
  }
  console.info('[access-audit]', entry.action, entry.target, 'by', entry.user_id)
  return entry
}

export function readAccessLog() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '[]')
  } catch {
    return []
  }
}

// Human wording for the Settings "Access log" panel -- the "reviewable" half of §23(a):
// recording access is only half the requirement, someone has to be able to read it back.
export const ACCESS_ACTION_LABEL = {
  senior_phone_reveal: 'Revealed a senior’s full phone number',
  active_alert_view: 'Opened the active-alert view (full address + family contacts)',
  full_history_view: 'Expanded a senior’s full alert history',
  history_full_view: 'Opened the full alert history (beyond the default 30 days)',
}

// Access audit log (RA 10173 section 23(a)): records disclosures beyond the default view.
//
//   - 'senior_phone_reveal'  - un-masking a senior's phone number
//   - 'active_alert_view'    - opening the active-alert view (full address and contacts)
//   - 'full_history_view'    - expanding a senior's full alert history
//
// The log is kept in localStorage on this machine. A central log would need a server table
// and a POST /barangay/audit route, which belong to the main lane. Once that exists, send
// each entry there at the marked spot and keep this as the offline fallback.

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

    // Once POST /barangay/audit exists (main lane), send the entry to the server here and
    // keep the localStorage write below as the offline fallback.

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

// Wording for the Settings "Access log" panel.
export const ACCESS_ACTION_LABEL = {
  senior_phone_reveal: 'Revealed a senior’s full phone number',
  active_alert_view: 'Opened the active-alert view (full address + family contacts)',
  full_history_view: 'Expanded a senior’s full alert history',
  history_full_view: 'Opened the full alert history (beyond the default 30 days)',
}

// The API speaks in short codes. A barangay responder is a community worker, not an
// engineer, so every code a person can see is translated once, here.

export const TRIGGER_LABEL = {
  inactivity: 'No movement for an unusual stretch',
  movement: 'Movement unlike their usual pattern',
  screen_idle: 'Phone untouched far longer than usual',
  charging: 'Charging pattern unlike their usual',
  sos: 'SOS pressed by the senior',
  ml_flag: "Whole day's pattern unusual for them",
  fall_pattern: 'Possible fall detected',
}

// Short forms of the same trigger codes, for places too tight for the full sentence above
// -- the dashboard's "Alerts by Type" chart labels. An unknown code falls back to itself.
export const TRIGGER_SHORT = {
  inactivity: 'No movement',
  movement: 'Unusual movement',
  screen_idle: 'Phone idle',
  charging: 'Charging pattern',
  sos: 'SOS',
  ml_flag: 'Daily pattern',
  fall_pattern: 'Possible fall',
}

export const STATUS_LABEL = {
  pending: 'Waiting for an answer',
  acknowledged: 'Family is handling it',
  escalated: 'Needs a welfare check',
  resolved: 'Closed',
  false_positive: 'False Positive',
}

// Every step name written by any tier: the phone (AlertEscalator), the family app, and
// the server-side clock. An unknown code falls back to itself rather than vanishing --
// an audit timeline that quietly drops entries is worse than an ugly one.
export const STEP_LABEL = {
  escalated_family: 'Phone notified the family contact',
  escalated_family_server: 'No answer — server notified the family contact',
  delivered_family: 'Alert reached the cloud',
  acknowledged_family: 'Family acknowledged',
  escalated_barangay: 'Family requested a barangay welfare check',
  no_family_contact: 'No family contact linked — skipped straight to barangay',
  self_cancelled: 'Senior answered: safe',
  self_cancelled_senior: 'Senior answered: safe',
  cancel_synced: "Senior's all-clear reached the cloud",
  resolved_family: 'Closed by family',
  acknowledged_barangay: 'Responder is attending',
  resolved_barangay: 'Closed by responder',
  false_positive_barangay: 'Marked a false positive by responder',
}

// `escalated_barangay_auto` is one step code covering three different reasons the server's
// sweep (backend/app/api/escalation.py, sweep_overdue_alerts/barangay_deadline) can have for
// jumping straight to the barangay: an SOS press, a senior with no family contact at all, or
// an ordinary no-response timeout. Those reasons are written into `entry.reason` verbatim, so
// matching on that text -- rather than collapsing all three into one "no answer from family"
// label -- is what actually tells a responder why they're looking at this incident.
const ESCALATED_BARANGAY_AUTO_LABEL = [
  ['SOS pressed', 'SOS pressed — escalated to barangay'],
  ['No family contact', 'No family contact — escalated to barangay'],
]

function escalatedBarangayAutoLabel(reason) {
  const hit = ESCALATED_BARANGAY_AUTO_LABEL.find(([needle]) => reason && reason.includes(needle))
  return hit ? hit[1] : 'No answer from family — escalated to barangay'
}

// Takes a full escalation_steps entry (not just the step code) because a couple of step
// types need more than their name to say what happened -- see escalatedBarangayAutoLabel
// above. An unknown step code falls back to itself rather than vanishing.
export function stepLabel(entry) {
  const step = (entry && entry.step) ?? entry
  if (step === 'escalated_barangay_auto') return escalatedBarangayAutoLabel(entry && entry.reason)
  return STEP_LABEL[step] || step
}
export const triggerLabel = (trigger) => TRIGGER_LABEL[trigger] || trigger
export const triggerShort = (trigger) => TRIGGER_SHORT[trigger] || trigger
export const statusLabel = (status) => STATUS_LABEL[status] || status

// The three "alert types" the Alert History filter offers. Only the first two map to a
// trigger_type; "Dispatch by Family" is not a trigger at all -- it is *how the incident
// reached the barangay*. The family app writes a real `escalated_barangay` step when a
// relative asks for a welfare check (as opposed to `escalated_barangay_auto`, which the
// server writes when nobody answered). So this is derived from data the API already
// returns -- trigger_type plus the escalation timeline -- not a stored enum value, and not
// a UI-only invention. Adding it to the TriggerType enum would be wrong twice over: it is
// not a trigger, and the enum is a Postgres type this lane cannot migrate.
export const CATEGORY_LABEL = {
  anomaly: 'Anomaly',
  sos: 'SOS',
  dispatch_family: 'Dispatch by Family',
}

export function alertCategory(alert) {
  if (alert.trigger_type === 'sos') return 'sos'
  const steps = alert.escalation_steps || []
  if (steps.some((entry) => entry && entry.step === 'escalated_barangay')) return 'dispatch_family'
  return 'anomaly'
}

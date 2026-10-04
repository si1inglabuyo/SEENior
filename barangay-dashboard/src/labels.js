// Translates the API's short codes into wording a responder can read.

export const TRIGGER_LABEL = {
  inactivity: 'No movement for an unusual stretch',
  movement: 'Movement unlike their usual pattern',
  screen_idle: 'Phone untouched far longer than usual',
  charging: 'Charging pattern unlike their usual',
  sos: 'SOS pressed by the senior',
  ml_flag: "Whole day's pattern unusual for them",
  fall_pattern: 'Possible fall detected',
}

// Short forms of the trigger codes for tight spaces (chart labels). Unknown codes fall back to themselves.
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
  attending: 'A responder is attending',
  resolved: 'Closed',
  false_positive: 'False Positive',
}

// Every step name written by any tier (phone, family app, server clock). Unknown codes fall
// back to themselves so the timeline never drops entries.
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

// `escalated_barangay_auto` covers three reasons the server jumps to the barangay: an SOS,
// a senior with no family contact, or no response. The reason is written into `entry.reason`,
// so matching on that text tells the responder which one it was.
const ESCALATED_BARANGAY_AUTO_LABEL = [
  ['SOS pressed', 'SOS pressed — escalated to barangay'],
  ['No family contact', 'No family contact — escalated to barangay'],
]

function escalatedBarangayAutoLabel(reason) {
  const hit = ESCALATED_BARANGAY_AUTO_LABEL.find(([needle]) => reason && reason.includes(needle))
  return hit ? hit[1] : 'No answer from family — escalated to barangay'
}

// Takes the whole escalation_steps entry because some steps need more than their code to
// describe (see above). Unknown codes fall back to themselves.
export function stepLabel(entry) {
  const step = (entry && entry.step) ?? entry
  if (step === 'escalated_barangay_auto') return escalatedBarangayAutoLabel(entry && entry.reason)
  return STEP_LABEL[step] || step
}
export const triggerLabel = (trigger) => TRIGGER_LABEL[trigger] || trigger
export const triggerShort = (trigger) => TRIGGER_SHORT[trigger] || trigger
export const statusLabel = (status) => STATUS_LABEL[status] || status

// The four alert types in the Alert History filter. "Dispatch by Family" is not a trigger;
// it is how the incident reached the barangay (a real `escalated_barangay` step, as opposed
// to `escalated_barangay_auto`), so it is derived from trigger_type plus the timeline.
// "Potential Fall" is separate from "Anomaly" because a fall is not a deviation from the
// senior's routine; Layer 0 fires from Day 1.
export const CATEGORY_LABEL = {
  anomaly: 'Anomaly',
  potential_fall: 'Potential Fall',
  sos: 'SOS',
  dispatch_family: 'Dispatch by Family',
}

// Mirrored by _alert_category() in backend/app/api/routes/barangay.py -- keep them in step.
export function alertCategory(alert) {
  if (alert.trigger_type === 'sos') return 'sos'
  if (alert.trigger_type === 'fall_pattern') return 'potential_fall'
  const steps = alert.escalation_steps || []
  if (steps.some((entry) => entry && entry.step === 'escalated_barangay')) return 'dispatch_family'
  return 'anomaly'
}

// "Attending" is not a stored status. Acknowledging leaves `status` as `escalated` and adds
// an `acknowledged_barangay` step, so an escalated alert with that step is open but claimed.
export function isAttending(alert) {
  return (
    alert.status === 'escalated' &&
    (alert.escalation_steps || []).some((entry) => entry && entry.step === 'acknowledged_barangay')
  )
}

// The status to show: the stored one, except a claimed incident reads "attending".
export const displayStatus = (alert) => (isAttending(alert) ? 'attending' : alert.status)

// Translates the API's short codes into wording a responder can read.
import { dispatchStepLabel } from './dispatch.js'

export const TRIGGER_LABEL = {
  inactivity: 'No Movement for an Unusual Stretch',
  movement: 'Movement Unlike Their Usual Pattern',
  screen_idle: 'Phone Untouched Far Longer Than Usual',
  charging: 'Charging Pattern Unlike Their Usual',
  sos: 'SOS Pressed by the Senior',
  ml_flag: "Whole Day's Pattern Unusual for Them",
  fall_pattern: 'Possible Fall Detected',
}

// Short forms of the trigger codes for tight spaces (chart labels). Unknown codes fall back to themselves.
export const TRIGGER_SHORT = {
  inactivity: 'No Movement',
  movement: 'Unusual Movement',
  screen_idle: 'Phone Idle',
  charging: 'Charging Pattern',
  sos: 'SOS',
  ml_flag: 'Daily Pattern',
  fall_pattern: 'Possible Fall',
}

// A pook is the area of the barangay a senior lives in. The sector (north / south of the
// Sunken Garden) is shown as a tooltip, since the name alone is what a responder looks for.
export const SECTOR_LABEL = { north: 'North sector', south: 'South sector' }

export function pookTitle(sector) {
  return SECTOR_LABEL[sector] || 'Pook'
}

export const STATUS_LABEL = {
  pending: 'Waiting for an Answer',
  acknowledged: 'Family Is Handling It',
  escalated: 'Needs a Welfare Check',
  attending: 'A Responder Is Attending',
  resolved: 'Closed',
  false_positive: 'False Alert',
}

// Every step name written by any tier (phone, family app, server clock). Unknown codes fall
// back to themselves so the timeline never drops entries.
export const STEP_LABEL = {
  escalated_family: 'Phone Notified the Family Contact',
  escalated_family_server: 'No Answer — Server Notified the Family Contact',
  delivered_family: 'Alert Reached the Cloud',
  push_received_family: 'Family Contact Received the Alert by Push Notification',
  sms_sent_family: 'Family Contact Received the Alert by SMS',
  acknowledged_family: 'Family Acknowledged',
  escalated_barangay: 'Family Requested a Barangay Welfare Check',
  self_cancelled: 'Senior Answered: Safe',
  self_cancelled_senior: 'Senior Answered: Safe',
  auto_cleared_phone_use: 'Closed: Senior Used Their Phone',
  cancel_synced: "Senior's All-Clear Reached the Cloud",
  resolved_family: 'Closed by Family',
  acknowledged_barangay: 'Responder Is Attending',
  resolved_barangay: 'Closed by Responder',
  false_positive_barangay: 'Marked a False Alert by Responder',
}

// `escalated_barangay_auto` is one step code covering three different reasons the server's
// sweep (backend/app/api/escalation.py, sweep_overdue_alerts/barangay_deadline) can have for
// jumping straight to the barangay: an SOS press, a senior with no family contact at all, or
// an ordinary no-response timeout. Those reasons are written into `entry.reason` verbatim.
//
// The no-family case must never read as "no answer from family -- escalated": nobody was
// asked, so nobody failed to answer, and nothing was escalated *past* a tier. The alert
// went to the barangay first because it is the only tier this senior has. The same goes
// for `no_family_contact`, which on its own says nothing about what actually happened --
// so it carries the alert's trigger ("Possible Fall Detected", "No Movement ...") as well.

const FAMILY_NOTIFIED = ['escalated_family', 'escalated_family_server']

// Did this alert go out with no family tier? Read from the timeline first -- the server's
// `no_family_contact` step, or its reason text -- because that records how the alert was
// actually routed. `senior_has_family_contact` is the senior's *current* state (they may
// have paired someone since), so it is only a fallback for older rows written before the
// sweep recorded the reason, and only when no family notification appears on the timeline.
function wentWithoutFamily(entry, alert) {
  if (entry && entry.reason && entry.reason.includes('No family contact')) return true
  const steps = (alert && alert.escalation_steps) || []
  if (steps.some((e) => e && e.step === 'no_family_contact')) return true
  if (steps.some((e) => e && FAMILY_NOTIFIED.includes(e.step))) return false
  return Boolean(alert) && alert.senior_has_family_contact === false
}

const hasStep = (alert, step) =>
  ((alert && alert.escalation_steps) || []).some((e) => e && e.step === step)

function escalatedBarangayAutoLabel(entry, alert) {
  const reason = entry && entry.reason
  const sos = (reason && reason.includes('SOS pressed')) || (alert && alert.trigger_type === 'sos')
  const alone = wentWithoutFamily(entry, alert)
  if (sos && alone) return 'SOS Pressed — Sent Straight to Barangay (No Family Contact Linked)'
  if (sos) {
    // Name the channel the family actually got, read off the timeline; say nothing about it until known.
    const push = hasStep(alert, 'push_received_family')
    const text = hasStep(alert, 'sms_sent_family')
    const via = push && text ? ' (push and SMS)' : push ? ' (push)' : text ? ' (SMS)' : ''
    return `SOS Pressed — Family${via} and Barangay Notified at the Same Time`
  }
  if (alone) {
    // When the `no_family_contact` step is on the timeline it has already said what the
    // alert was; repeating it on the very next line is noise.
    if (hasStep(alert, 'no_family_contact')) return 'Sent Straight to Barangay — No Family Contact to Notify'
    const what = alert ? triggerLabel(alert.trigger_type) : 'Alert'
    return `${what} — Sent Straight to Barangay (No Family Contact Linked)`
  }
  return 'No Answer from Family — Escalated to Barangay'
}

function noFamilyContactLabel(alert) {
  const what = alert ? triggerLabel(alert.trigger_type) : 'Alert raised'
  return `${what} — Senior Lives Alone, No Family Contact to Notify`
}

// Takes a full escalation_steps entry (not just the step code), plus the alert it belongs
// to, because a couple of step types need more than their name to say what happened -- see
// escalatedBarangayAutoLabel above. An unknown step code falls back to itself rather than
// vanishing.
export function stepLabel(entry, alert) {
  const step = (entry && entry.step) ?? entry
  if (step === 'escalated_barangay_auto') return escalatedBarangayAutoLabel(entry, alert)
  if (step === 'no_family_contact') return noFamilyContactLabel(alert)
  return STEP_LABEL[step] || dispatchStepLabel(entry) || step
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

// The badge class for each category (index.css .type-badge-*). Anomaly uses the base style.
export const CATEGORY_CLASS = {
  anomaly: '',
  potential_fall: 'type-badge-fall',
  sos: 'type-badge-sos',
  dispatch_family: 'type-badge-dispatch',
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

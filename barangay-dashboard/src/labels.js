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

export const STATUS_LABEL = {
  pending: 'Waiting for an answer',
  acknowledged: 'Family is handling it',
  escalated: 'Needs a welfare check',
  resolved: 'Closed',
  false_positive: 'False alarm',
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
  escalated_barangay_auto: 'No answer from family — escalated to barangay',
  self_cancelled: 'Senior answered: safe',
  self_cancelled_senior: 'Senior answered: safe',
  cancel_synced: "Senior's all-clear reached the cloud",
  resolved_family: 'Closed by family',
  acknowledged_barangay: 'Responder is attending',
  resolved_barangay: 'Closed by responder',
  false_positive_barangay: 'Marked a false alarm by responder',
}

export const stepLabel = (step) => STEP_LABEL[step] || step
export const triggerLabel = (trigger) => TRIGGER_LABEL[trigger] || trigger
export const statusLabel = (status) => STATUS_LABEL[status] || status

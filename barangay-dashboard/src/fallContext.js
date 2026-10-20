// Turns a fall alert's `fall_context` and `fall_tolerance` strings (key=number;key=number) into
// the few lines a responder or panelist reads: what the phone was doing just before the drop, the
// time of day, and whether the detector was running at a raised setting.
//
// `fall_context` is a record, not a rule: the detector never reads it. The "reading" line is an
// interpretation for a human, worded as "consistent with", never as a diagnosis.
import { parseNote } from './fallEvidence.js'

const fixed = (n) => (Number.isFinite(n) ? n.toFixed(1) : '—')

// Returns null when the alert carries no context (older rows, other triggers, unreadable note).
export function fallContext(contextRaw, toleranceRaw) {
  const c = parseNote(contextRaw)
  if (!c || !Number.isFinite(c.night)) return null
  const t = parseNote(toleranceRaw)

  const motionKnown = Number.isFinite(c.active)
  const moving = motionKnown && c.active === 1
  const asleep = c.night === 1

  const rows = [
    {
      key: 'motion',
      label: 'Phone before the drop',
      value: motionKnown
        ? `${moving ? 'Moving, like walking' : 'At rest'} (${fixed(c.pre_dev)} m/s² off 1 g)`
        : 'Too few readings to say',
    },
    { key: 'time', label: 'Time of day', value: asleep ? 'Sleep hours' : 'Awake hours' },
  ]

  const raised = !!t && Number.isFinite(t.impact_min) && Number.isFinite(t.base_impact) && t.impact_min > t.base_impact
  rows.push({
    key: 'setting',
    label: 'Detector setting',
    value: raised
      ? `Raised after ${t.false ?? '?'} cancelled fall(s): impact ≥ ${fixed(t.impact_min)} m/s² (normally ${fixed(t.base_impact)})`
      : 'Normal',
  })

  let reading = null
  if (motionKnown) {
    reading = moving
      ? 'Moving before the drop: consistent with a fall while walking.'
      : asleep
        ? 'At rest during sleep hours: could be a phone dropped by someone resting.'
        : 'At rest before the drop: could be a dropped phone or a fall from standing still.'
  }

  return { rows, reading }
}

// Turns a fall alert's `fall_context` and `fall_tolerance` strings (key=number;key=number) into a
// few plain sentences for a responder: what the phone was doing just before the drop, whether it
// happened in the senior's sleep hours, and whether the fall detector had been made less
// sensitive. No raw figures are shown.
//
// A hint for a human, not a finding: the detector never reads `fall_context`, and the wording says
// "may" and "could". The family app has a Kotlin twin (ui/family/FallContextSummary.kt and
// FamilyStrings.fallContextLines); the two must be changed together.
import { parseNote } from './fallEvidence.js'

// Returns null when the alert carries no context (older rows, other triggers, unreadable note).
export function fallContext(contextRaw, toleranceRaw) {
  const c = parseNote(contextRaw)
  if (!c || !Number.isFinite(c.night)) return null
  const t = parseNote(toleranceRaw)

  const lines = []
  if (Number.isFinite(c.active)) {
    lines.push(
      c.active === 1
        ? 'The phone was being carried or used just before the drop, so the senior may have fallen while moving.'
        : 'The phone was lying still just before the drop. It could simply have been dropped, or the senior may have fallen from standing still.'
    )
  }
  lines.push(
    c.night === 1
      ? "This happened during the senior's usual sleep hours."
      : "This happened outside the senior's usual sleep hours."
  )
  const lessSensitive = !!t && Number.isFinite(t.impact_min) && Number.isFinite(t.base_impact) && t.impact_min > t.base_impact
  if (lessSensitive) {
    lines.push('Fall detection is set less sensitive for this senior because earlier fall alerts were false alarms.')
  }
  return { lines }
}

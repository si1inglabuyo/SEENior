// How a senior's routine was learned (GET /seniors/{sync_id}/routine/history, migration 0024).
//
// One copy of the Routine Fingerprint per day, the last 30 days. Each row is the same median and
// MAD the fingerprint holds, for one feature in one time block on one day; there are still no
// sensor readings here. `is_estimate` is true for days the phone worked out afterwards from its
// stored daily summaries with today's rules, and false for a copy it saved that day. Say that in
// plain words (never "reconstructed" / "recorded").
//
// Mirrors android-app/.../ui/family/FamilyRoutineHistory.kt: same rules, same sentences. Change
// both together. Pure, so it is easy to test.

import { BLOCKS, duration } from './routine.js'

const STILLNESS = 'inactivity_duration'

// One line per time block, drawn from the usual-stillness figure. Null until there are at least
// two days to draw.
export function summarizeHistory(rows) {
  const stillness = rows.filter((r) => r.feature_name === STILLNESS)
  if (new Set(stillness.map((r) => r.day)).size < 2) return null

  const blocks = BLOCKS.map((block) => {
    const points = stillness
      .filter((r) => r.time_block === block)
      .sort((a, b) => (a.day < b.day ? -1 : a.day > b.day ? 1 : 0))
      .map((r) => ({ day: r.day, seconds: r.median_value, isEstimate: r.is_estimate }))
    return points.length < 2 ? null : { block, points, start: points[0], latest: points[points.length - 1] }
  }).filter(Boolean)
  if (blocks.length === 0) return null

  const saved = stillness.filter((r) => !r.is_estimate).map((r) => r.day).sort()
  return {
    blocks,
    hasEstimate: stillness.some((r) => r.is_estimate),
    firstSavedDay: saved.length ? saved[0] : null,
  }
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

// "2026-11-14" -> "Nov 14". Read off the string, not a Date, so the time zone cannot move it.
export function shortDay(iso) {
  const [, month, day] = iso.split('-').map(Number)
  return `${MONTHS[month - 1]} ${day}`
}

export const HISTORY_TITLE = 'How this routine was learned'
export const HISTORY_HEADING = 'Usual longest stillness'
export const ESTIMATE_NOTE =
  "Earlier days are an estimate worked out from the senior's saved daily activity."
export const savedNote = (iso) => `From ${shortDay(iso)}, the app saved this each night.`

export const startedAt = (block) =>
  `Started at a guess of ${duration(block.start.seconds)}, now ${duration(block.latest.seconds)}`

// Points for an SVG polyline in a width x height box, each value scaled between the block's own
// lowest and highest day. A flat line sits in the middle rather than on the floor.
export function chartPoints(points, width, height, pad = 6) {
  const values = points.map((p) => p.seconds)
  const low = Math.min(...values)
  const range = Math.max(...values) - low
  return points.map((p, i) => ({
    x: points.length === 1 ? 0 : (i * width) / (points.length - 1),
    y: range === 0 ? height / 2 : height - pad - ((p.seconds - low) / range) * (height - 2 * pad),
    isEstimate: p.isEstimate,
  }))
}

// Plain-language wording for a senior's Routine Fingerprint (GET /seniors/{sync_id}/routine).
//
// The phone sends, per feature and time block, a median and a MAD over the last 14 days. That
// is a statistical summary, not sensor data: there are no readings and no per-day history here.
// Durations arrive in seconds. Pure, so it is easy to test.

export const BLOCKS = ['morning', 'afternoon', 'evening', 'night']

export const BLOCK_LABEL = {
  morning: 'Morning',
  afternoon: 'Afternoon',
  evening: 'Evening',
  night: 'Night',
}

// Row order in the table. A feature a newer phone adds is simply not shown until labelled here.
export const FEATURES = [
  { key: 'inactivity_duration', label: 'Usually still for', kind: 'duration' },
  { key: 'movement_score', label: 'Movement level', kind: 'score' },
  { key: 'step_count', label: 'Steps', kind: 'count' },
  { key: 'screen_unlock_count', label: 'Phone unlocks', kind: 'count' },
  { key: 'screen_idle_duration', label: 'Phone untouched for', kind: 'duration' },
]

// "45 sec", "25 min", "1 hr 5 min".
export function duration(seconds) {
  const total = Math.max(0, Math.round(seconds))
  if (total < 60) return `${total} sec`
  if (total < 3600) return `${Math.round(total / 60)} min`
  const minutes = Math.round(total / 60)
  const rem = minutes % 60
  return rem === 0 ? `${Math.floor(minutes / 60)} hr` : `${Math.floor(minutes / 60)} hr ${rem} min`
}

function format(kind, value) {
  if (kind === 'duration') return duration(value)
  if (kind === 'score') return value.toFixed(2)
  return String(Math.round(value))
}

// One table cell: the usual value, how far an ordinary day swings, and whether it is still the
// setup answer. Null when the phone has not sent that cell.
export function cell(rows, featureKey, block) {
  const feature = FEATURES.find((f) => f.key === featureKey)
  const row = rows.find((r) => r.feature_name === featureKey && r.time_block === block)
  if (!feature || !row) return null
  return {
    value: format(feature.kind, row.median_value),
    swing: `± ${format(feature.kind, row.mad_value)}`,
    isSeed: row.is_seed,
  }
}

export function seedCount(rows) {
  return rows.filter((r) => r.is_seed).length
}

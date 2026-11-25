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

// ---- the plain-language reading ----
//
// Mirrors android-app/.../ui/family/RoutineSummary.kt: same rules, same cut-offs. Change both
// together. Activity is ranked within the senior (each part of the day against their busiest),
// never against a population.

const ACTIVE_RATIO = 0.66
const LIGHT_RATIO = 0.33
const SOMETIMES_FROM = 2
const OFTEN_FROM = 8

export const LEVEL_LABEL = { busiest: 'Busiest', active: 'Active', light: 'Light', quiet: 'Quiet' }
export const PHONE_LABEL = {
  rarely: 'Phone: rarely used',
  sometimes: 'Phone: used sometimes',
  often: 'Phone: used often',
}

const WHEN = {
  morning: 'in the morning',
  afternoon: 'in the afternoon',
  evening: 'in the evening',
  night: 'at night',
}

const median = (rows, feature, block) =>
  rows.find((r) => r.feature_name === feature && r.time_block === block)?.median_value ?? null

export function summarize(rows) {
  const blocks = BLOCKS.filter((b) => rows.some((r) => r.time_block === b))

  // Each metric is scaled to its own busiest block, then averaged, so steps (hundreds) and
  // movement (hundredths) count equally.
  const metrics = ['movement_score', 'step_count'].map((f) =>
    Object.fromEntries(blocks.map((b) => [b, median(rows, f, b)]))
  )
  const raw = Object.fromEntries(
    blocks.map((b) => {
      const ratios = metrics
        .map((m) => {
          const max = Math.max(0, ...Object.values(m).filter((v) => v !== null))
          return max > 0 && m[b] !== null ? m[b] / max : null
        })
        .filter((v) => v !== null)
      return [b, ratios.length ? ratios.reduce((a, c) => a + c, 0) / ratios.length : 0]
    })
  )
  const peak = Math.max(0, ...Object.values(raw))
  const hasActivity = peak > 0
  const busiest = hasActivity ? blocks.reduce((a, b) => (raw[b] > raw[a] ? b : a)) : null

  const out = blocks.map((b) => {
    const activity = hasActivity ? raw[b] / peak : 0
    const level =
      b === busiest ? 'busiest' : activity >= ACTIVE_RATIO ? 'active' : activity >= LIGHT_RATIO ? 'light' : 'quiet'
    const unlocks = median(rows, 'screen_unlock_count', b)
    return {
      block: b,
      level,
      activity,
      stillSeconds: median(rows, 'inactivity_duration', b),
      phone: unlocks === null ? null : unlocks < SOMETIMES_FROM ? 'rarely' : unlocks < OFTEN_FROM ? 'sometimes' : 'often',
    }
  })

  const quietestBlock =
    hasActivity && out.length > 1 ? out.reduce((a, b) => (b.activity < a.activity ? b : a)).block : null
  const seeds = seedCount(rows)
  return {
    blocks: out,
    busiest,
    quietest: quietestBlock !== busiest ? quietestBlock : null,
    days: Math.min(14, Math.max(0, ...rows.map((r) => r.sample_count))),
    seedCount: seeds,
    total: rows.length,
    fullyLearned: rows.length > 0 && seeds === 0,
  }
}

// A fact, not a judgement: null when there is nothing meaningful to say yet.
export function headline(s) {
  if (!s.busiest) return null
  return s.quietest
    ? `Busiest ${WHEN[s.busiest]}; quietest ${WHEN[s.quietest]}.`
    : `Busiest ${WHEN[s.busiest]}.`
}

export function progress(s) {
  if (s.fullyLearned) return 'Learned from the last 14 days.'
  if (s.days === 0) return 'Just started: based on the setup answers for now.'
  return `Still learning: day ${s.days} of 14.`
}

export const stillLabel = (seconds) => `Still for up to ${duration(seconds)}`

// ---- "Daily routine" card: the same reading the senior's own My routine screen gives ----
//
// Mirrors android-app/.../ui/routine/SeniorRoutineText.kt (block hours, "Usually up by about"),
// from the fingerprint plus `timing` (GET /seniors/{sync_id}/routine). Only what the cloud holds is
// said: it carries the usual first-active time (morning) and last-active time (evening), not an
// afternoon one, so the afternoon gets no "usually active until" line rather than an invented one.

export function clock(minute) {
  const m = ((Math.round(minute) % 1440) + 1440) % 1440
  const h24 = Math.floor(m / 60)
  const h12 = h24 % 12 === 0 ? 12 : h24 % 12
  return `${h12}:${String(m % 60).padStart(2, '0')} ${h24 < 12 ? 'AM' : 'PM'}`
}

const START_KEY = {
  morning: 'morning_start',
  afternoon: 'afternoon_start',
  evening: 'evening_start',
  night: 'night_start',
}

// "6:30 AM - 11:30 AM", or null when the phone has not sent block times.
export function blockHours(timing, block) {
  if (!timing) return null
  const i = BLOCKS.indexOf(block)
  const start = timing[START_KEY[block]]
  const end = timing[START_KEY[BLOCKS[(i + 1) % BLOCKS.length]]]
  if (start == null || end == null) return null
  return `${clock(start)} - ${clock(end)}`
}

export function usualHeadline(block, learned, timing) {
  if (block === 'night') return 'Time for sleep'
  if (!learned) return 'Still learning this part of the day.'
  if (block === 'morning' && timing?.wake_minute != null) return `Usually up by about ${clock(timing.wake_minute)}`
  if (block === 'evening' && timing?.evening_last_minute != null) {
    return `Usually last active about ${clock(timing.evening_last_minute)}`
  }
  return null
}

const times = (n) => (n === 1 ? '1 time' : `${n} times`)

// One entry per part of the day, in order, for the card.
export function partsOfDay(rows, timing) {
  return BLOCKS.filter((b) => rows.some((r) => r.time_block === b)).map((block) => {
    const inBlock = rows.filter((r) => r.time_block === block)
    const learned = inBlock.length > 0 && inBlock.every((r) => !r.is_seed)
    const value = (key) => inBlock.find((r) => r.feature_name === key)?.median_value ?? null
    const still = value('inactivity_duration')
    const steps = value('step_count')
    const unlocks = value('screen_unlock_count')
    const idle = value('screen_idle_duration')
    const lines = []
    if (still !== null) lines.push({ label: 'Still for up to', value: duration(still) })
    if (steps !== null) lines.push({ label: 'Steps', value: Math.round(steps).toLocaleString('en-US') })
    if (unlocks !== null) lines.push({ label: 'Phone unlocks', value: times(Math.round(unlocks)) })
    if (idle !== null) lines.push({ label: 'Longest time phone untouched', value: duration(idle) })
    return {
      block,
      hours: blockHours(timing, block),
      learned,
      headline: usualHeadline(block, learned, timing),
      lines,
    }
  })
}

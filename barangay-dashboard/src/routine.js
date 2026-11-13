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

// ---- how the system worked this out ----
//
// Mirrors android-app/.../ui/family/RoutineProof.kt. Built only from the figures the phone
// already syncs (a median, a MAD and a day count per feature per time block); the per-day
// readings stay on the phone, so this explains how a line like "Still for up to 34 min" was
// reached without showing the days themselves. Change both together.

const GATE = 2.5 // Layer 1's trigger gate
const SCALE = 0.6745 // MedianMad.MODIFIED_Z_SCALE
const STILL_MAD_FLOOR = 300 // seconds; the detector never divides by less
export const MADS_TO_GATE = GATE / SCALE

// Stillness at which the score first reaches the gate: median + (2.5 / 0.6745) * MAD. The time of
// day, phone use and any false-alarm tolerance apply after it, so this is the earliest the system
// would ask, not a promise it will.
export function alertAfterSeconds(median, mad) {
  return median + MADS_TO_GATE * Math.max(mad, STILL_MAD_FLOOR)
}

const rowOf = (rows, feature, block) =>
  rows.find((r) => r.feature_name === feature && r.time_block === block) ?? null

export function proof(rows) {
  const summary = summarize(rows)
  return summary.blocks.map((b) => {
    const still = rowOf(rows, 'inactivity_duration', b.block)
    const inBlock = rows.filter((r) => r.time_block === b.block)
    return {
      block: b.block,
      level: b.level,
      activityPercent: Math.round(b.activity * 100),
      movement: rowOf(rows, 'movement_score', b.block)?.median_value ?? null,
      steps: rowOf(rows, 'step_count', b.block)?.median_value ?? null,
      stillMedian: still?.median_value ?? null,
      stillMad: still?.mad_value ?? null,
      alertAfter: still ? alertAfterSeconds(still.median_value, still.mad_value) : null,
      unlocks: rowOf(rows, 'screen_unlock_count', b.block)?.median_value ?? null,
      phone: b.phone,
      days: Math.max(0, ...inBlock.map((r) => r.sample_count)),
      hasSeed: inBlock.some((r) => r.is_seed),
    }
  })
}

const PHONE_BAND = { rarely: 'rarely (under 2)', sometimes: 'sometimes (2 to 7)', often: 'often (8 or more)' }

export function proofLines(p) {
  const lines = []
  if (p.movement !== null || p.steps !== null) {
    const parts = [
      p.movement !== null ? `movement ${p.movement.toFixed(2)}` : null,
      p.steps !== null ? `steps ${Math.round(p.steps)}` : null,
    ].filter(Boolean)
    lines.push(`Activity: ${parts.join(' and ')}. That is ${p.activityPercent}% of their busiest part of the day.`)
  }
  if (p.stillMedian !== null && p.stillMad !== null) {
    lines.push(
      `Stillness: each day, the longest stretch they sat still in this part of the day is recorded. ` +
        `The middle value over ${p.days} days is ${duration(p.stillMedian)}, and an ordinary day varies by ± ${duration(p.stillMad)}.`
    )
    const usedMad = Math.max(p.stillMad, STILL_MAD_FLOOR)
    lines.push(
      `The system would first ask if they are safe once stillness passes about ${duration(p.alertAfter)}: ` +
        `${duration(p.stillMedian)} + ${MADS_TO_GATE.toFixed(2)} × ${duration(usedMad)} = ${duration(p.alertAfter)}. ` +
        `(2.5 ÷ 0.6745 = ${MADS_TO_GATE.toFixed(2)}.) Time of day and phone use are checked before it asks.`
    )
  }
  if (p.unlocks !== null && p.phone) {
    lines.push(`Phone: typically ${Math.round(p.unlocks)} unlocks in this part of the day, so ${PHONE_BAND[p.phone]}.`)
  }
  if (p.hasSeed) lines.push('Some figures here are still from the setup answers, not yet from real readings.')
  return lines
}

export const PROOF_FOOTER =
  "Each figure is the median of the days the senior's phone recorded, and ± is its median absolute " +
  'deviation (MAD). Only these summaries are sent; the daily readings stay on the phone.'

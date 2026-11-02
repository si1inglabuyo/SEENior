// Numbers for the printable reports (Reports tab). Pure functions over what the dashboard
// already fetches (GET /barangay/alerts, /barangay/seniors, /barangay/seniors/{sync_id},
// /seniors/{sync_id}/routine), so no new endpoint is needed. Alert metadata and the
// fingerprint only: no sensor readings exist here (root CLAUDE.md section 11).
//
// Only alerts that escalated past the senior reach the cloud, so every count below understates
// what the phones detected. The printed footer says so.

const PH_OFFSET_MS = 8 * 3600 * 1000
const DAY_MS = 24 * 3600 * 1000

// Timestamps arrive as naive UTC; append Z so JS does not read them as local time.
const toMs = (value) => {
  if (!value) return null
  const ms = new Date(/(Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`).getTime()
  return Number.isNaN(ms) ? null : ms
}

// Manila calendar date ("YYYY-MM-DD") of an instant.
const manilaDate = (ms) => new Date(ms + PH_OFFSET_MS).toISOString().slice(0, 10)

// Midnight (Manila) of a "YYYY-MM-DD" date, as UTC milliseconds.
const manilaMidnight = (iso) => new Date(`${iso}T00:00:00Z`).getTime() - PH_OFFSET_MS

export const addDays = (iso, n) => new Date(new Date(`${iso}T00:00:00Z`).getTime() + n * DAY_MS).toISOString().slice(0, 10)

// The Monday on or before a date.
export function mondayOf(iso) {
  const dow = new Date(`${iso}T00:00:00Z`).getUTCDay() // 0 = Sunday
  return addDays(iso, -((dow + 6) % 7))
}

// Monday of the last fully finished week, as of a moment.
export function lastCompleteWeek(nowMs = Date.now()) {
  return addDays(mondayOf(manilaDate(nowMs)), -7)
}

const inRange = (ms, fromIso, toIsoInclusive) =>
  ms !== null && ms >= manilaMidnight(fromIso) && ms < manilaMidnight(addDays(toIsoInclusive, 1))

const stepAt = (alert, ...names) => {
  const hit = (alert.escalation_steps || []).find((e) => e && names.includes(e.step))
  return hit ? toMs(hit.at) : null
}

// Seconds between two instants, or null if either is missing or the order is wrong.
const gap = (from, to) => (from !== null && to !== null && to >= from ? Math.round((to - from) / 1000) : null)

// The outcome a report groups an alert under.
export function outcomeOf(alert) {
  if (alert.status === 'resolved') return 'resolved'
  if (alert.status === 'false_positive') return 'false_alarm'
  if (alert.status === 'acknowledged') return 'acknowledged'
  const attending = (alert.escalation_steps || []).some((e) => e && e.step === 'acknowledged_barangay')
  return attending ? 'attending' : 'open'
}

export const OUTCOME_LABEL = {
  resolved: 'Resolved',
  false_alarm: 'False alarm',
  acknowledged: 'Acknowledged by family',
  attending: 'Responder attending',
  open: 'Still open',
}

// What the alert reached, read off the timeline (not stored anywhere).
export function reachedOf(alert) {
  const steps = (alert.escalation_steps || []).map((e) => e && e.step)
  if (steps.some((s) => s === 'escalated_barangay' || s === 'escalated_barangay_auto')) return 'Barangay'
  if (steps.some((s) => s === 'escalated_family' || s === 'escalated_family_server')) return 'Family'
  return 'Cloud only'
}

const avg = (xs) => (xs.length ? Math.round(xs.reduce((a, b) => a + b, 0) / xs.length) : null)
const max = (xs) => (xs.length ? Math.max(...xs) : null)

const tally = (items, key) => {
  const out = {}
  for (const item of items) {
    const k = key(item)
    out[k] = (out[k] || 0) + 1
  }
  return out
}

function responseTimes(alerts) {
  const family = []
  const barangay = []
  const ack = []
  const resolved = []
  for (const a of alerts) {
    const created = toMs(a.created_at)
    const famAt = stepAt(a, 'escalated_family', 'escalated_family_server')
    const brgyAt = stepAt(a, 'escalated_barangay', 'escalated_barangay_auto')
    const ackAt = stepAt(a, 'acknowledged_barangay')
    const gFam = gap(created, famAt)
    const gBrgy = gap(created, brgyAt)
    const gAck = gap(brgyAt, ackAt)
    const gRes = a.status === 'resolved' ? gap(created, toMs(a.resolved_at)) : null
    if (gFam !== null) family.push(gFam)
    if (gBrgy !== null) barangay.push(gBrgy)
    if (gAck !== null) ack.push(gAck)
    if (gRes !== null) resolved.push(gRes)
  }
  const row = (xs) => ({ avg: avg(xs), max: max(xs), n: xs.length })
  return { family: row(family), barangay: row(barangay), ack: row(ack), resolved: row(resolved) }
}

function summary(alerts) {
  const outcomes = tally(alerts, outcomeOf)
  const total = alerts.length
  return {
    total,
    outcomes,
    falseShare: total ? Math.round(((outcomes.false_alarm || 0) / total) * 100) : null,
    risk: tally(alerts, (a) => a.risk_level),
    triggers: tally(alerts, (a) => a.trigger_type),
  }
}

// ---- weekly ----

export function buildWeekly({ alerts, seniors, weekStart, nowMs = Date.now() }) {
  const weekEnd = addDays(weekStart, 6)
  const prevStart = addDays(weekStart, -7)
  const prevEnd = addDays(weekStart, -1)
  const live = alerts.filter((a) => a.status !== 'pending')
  const week = live.filter((a) => inRange(toMs(a.created_at), weekStart, weekEnd))
  const prev = live.filter((a) => inRange(toMs(a.created_at), prevStart, prevEnd))

  const perDay = Array.from({ length: 7 }, (_, i) => {
    const day = addDays(weekStart, i)
    return { day, count: week.filter((a) => manilaDate(toMs(a.created_at)) === day).length }
  })

  const now = summary(week)
  const before = summary(prev)
  const perPook = Object.entries(tally(week, (a) => a.senior_pook || 'Not set')).sort((a, b) => b[1] - a[1])

  const active = seniors.filter((s) => s.status !== 'inactive')
  const stale = nowMs - 24 * 3600 * 1000
  const roster = {
    registered: seniors.length,
    inactive: seniors.length - active.length,
    notSeen24h: active.filter((s) => {
      const seen = toMs(s.last_seen_at)
      return seen === null || seen < stale
    }).length,
  }

  return {
    weekStart,
    weekEnd,
    ...now,
    perDay,
    perPook,
    response: responseTimes(week),
    roster,
    previous: {
      from: prevStart,
      to: prevEnd,
      total: before.total,
      falseShare: before.falseShare,
      resolvedAvg: responseTimes(prev).resolved.avg,
    },
  }
}

// ---- one senior ----

// Plain observations from fixed rules. Behavioural, never medical: the system signals that
// something may be wrong, and a person follows up (root CLAUDE.md section 12).
export function observations(alerts, { periodDays }) {
  const out = []
  const falseAlarms = alerts.filter((a) => outcomeOf(a) === 'false_alarm').length
  if (alerts.length >= 2 && falseAlarms >= 2) {
    out.push(`${falseAlarms} of ${alerts.length} alerts were closed as false alarms. The system already relaxes its trigger for a time of day with repeated false alarms.`)
  }
  const falls = alerts.filter((a) => a.trigger_type === 'fall_pattern')
  if (falls.length) {
    out.push(`${falls.length} possible fall${falls.length > 1 ? 's' : ''} in this period. A home safety check (bathroom, lighting, floor) may be worth considering.`)
  }
  const sos = alerts.filter((a) => a.trigger_type === 'sos').length
  if (sos) out.push(`The senior pressed SOS ${sos} time${sos > 1 ? 's' : ''}.`)

  const stillness = alerts
    .filter((a) => a.trigger_type === 'inactivity')
    .map((a) => toMs(a.created_at))
    .filter((ms) => ms !== null)
    .sort((a, b) => a - b)
  if (stillness.some((ms, i) => i > 0 && ms - stillness[i - 1] <= 7 * DAY_MS)) {
    out.push('Two or more "no movement" alerts within a week. A welfare visit is suggested.')
  }
  if (!out.length && periodDays) out.push('Nothing in this period stands out from the senior’s usual routine.')
  return out
}

export function buildSenior({ detail, fromIso, toIso }) {
  const all = detail.alerts.filter((a) => a.status !== 'pending')
  const inPeriod = all
    .filter((a) => inRange(toMs(a.created_at), fromIso, toIso))
    .sort((a, b) => toMs(a.created_at) - toMs(b.created_at))
  const periodDays = Math.round((manilaMidnight(addDays(toIso, 1)) - manilaMidnight(fromIso)) / DAY_MS)
  const s = summary(inPeriod)
  return {
    fromIso,
    toIso,
    periodDays,
    alerts: inPeriod,
    ...s,
    falls: s.triggers.fall_pattern || 0,
    sos: s.triggers.sos || 0,
    observations: observations(inPeriod, { periodDays }),
    response: responseTimes(inPeriod),
  }
}

// "2 min 05 s", "1 h 46 min"; an em dash when there is nothing to average.
export function spanLabel(seconds) {
  if (seconds === null || seconds === undefined) return '—'
  if (seconds < 60) return `${seconds} s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ${String(seconds % 60).padStart(2, '0')} s`
  const m = Math.round(seconds / 60)
  return `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`
}

// ---- one incident ----

// A short reference a responder can quote on the phone: the first eight characters of the alert's
// anonymous sync id. Not secret and not sequential, so it says nothing about how many alerts exist.
export const incidentReference = (syncId) => String(syncId || '').replace(/-/g, '').slice(0, 8).toUpperCase()

// Everything one alert's report needs, from the alert row alone (GET /barangay/alerts). The
// timeline is the escalation_steps the phone, the family app and the server clock appended; each
// row also gets the seconds since the alert was detected so a reader can see how quickly each
// tier acted. Alert metadata only: no sensor readings exist in this row.
export function buildIncident(alert) {
  const created = toMs(alert.created_at)
  const closed = ['resolved', 'false_positive'].includes(alert.status) ? toMs(alert.resolved_at) : null
  const famAt = stepAt(alert, 'escalated_family', 'escalated_family_server')
  const brgyAt = stepAt(alert, 'escalated_barangay', 'escalated_barangay_auto')
  const ackAt = stepAt(alert, 'acknowledged_barangay')

  const steps = (Array.isArray(alert.escalation_steps) ? alert.escalation_steps : [])
    .filter(Boolean)
    .map((entry) => ({ entry, sinceSeconds: gap(created, toMs(entry.at)) }))
    .sort((a, b) => (toMs(a.entry.at) ?? Infinity) - (toMs(b.entry.at) ?? Infinity))

  return {
    reference: incidentReference(alert.sync_id),
    outcome: outcomeOf(alert),
    reached: reachedOf(alert),
    closedAt: closed === null ? null : alert.resolved_at,
    openSeconds: gap(created, closed),
    steps,
    times: {
      family: gap(created, famAt),
      barangay: gap(created, brgyAt),
      attending: gap(brgyAt, ackAt),
      closed: gap(created, closed),
    },
  }
}

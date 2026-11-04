// How the barangay is responding to its open alerts: each alert's priority tier, who has been
// sent, and when the hall is on duty. Nothing here is stored as its own state: a response is
// read off the alert's `escalation_steps`, so the timeline stays the single record of what was
// done and when. Kept free of React and the API so it is easy to test
// (run with: node --test src/dispatch.test.js).
//
// Step codes this file reads (the server adds `at` and `by` to each):
//   call_result    { result: 'cleared' | 'needs_team' | 'no_answer' }
//   team_sent      { team }
//   help_requested { service }
//   help_cancelled { service }

// The starting lists, until an admin edits them under Responders (see useResponders).
export const DEFAULT_TEAMS = ['Tanod team 1', 'Tanod team 2', 'BHERT team']
export const DEFAULT_SERVICES = ['Ambulance', 'Health station nurse', 'PRC volunteers', 'BFP volunteers']

// Mirrors the limit on DispatchAction in backend/app/schemas/barangay.py.
export const MAX_RESPONDER_NAME = 64

// Why a name cannot be used, or '' if it can. `taken` is every other name on both lists, because
// the timeline tells responders apart by name alone.
export function responderProblem(name, taken) {
  const clean = name.trim()
  if (!clean) return 'Enter a name.'
  if (clean.length > MAX_RESPONDER_NAME) return `Keep the name to ${MAX_RESPONDER_NAME} characters.`
  const same = taken.some((other) => other.toLowerCase() === clean.toLowerCase())
  return same ? `${clean} is already on the list.` : ''
}

// The priority tiers, most urgent first. This is the barangay's order of attention, separate
// from the senior's risk level. `callFirst` tiers start with a phone call, because one call can
// clear an alert without sending anyone.
export const TIERS = [
  { tier: 1, label: 'SOS', action: 'Send a team now', callFirst: false },
  { tier: 2, label: 'Potential fall', action: 'Send a team now', callFirst: false },
  { tier: 3, label: 'Family requested a welfare check', action: 'Send a team', callFirst: false },
  { tier: 4, label: 'Anomaly, senior lives alone', action: 'Call first, then send a team', callFirst: true },
  { tier: 5, label: 'Other anomaly', action: 'Call first, then send a team', callFirst: true },
]

export function tierOf(alert) {
  if (alert.trigger_type === 'sos') return 1
  if (alert.trigger_type === 'fall_pattern') return 2
  if ((alert.escalation_steps || []).some((e) => e && e.step === 'escalated_barangay')) return 3
  return alert.senior_has_family_contact === false ? 4 : 5
}

// The API sends naive UTC with no zone marker, which JS would read as local time.
function toMs(value) {
  if (!value) return NaN
  return Date.parse(/(Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)
}

function summary({ teams, services, call, tier }) {
  const sent = [...teams, ...services]
  if (sent.length) return { text: `${sent.join(', ')} sent`, tone: 'green' }
  if (call === 'cleared') return { text: 'Senior is fine · ready to close', tone: 'green' }
  if (call === 'no_answer') return { text: 'No answer · needs a team', tone: 'red' }
  if (call === 'needs_team') return { text: 'Needs a team', tone: 'red' }
  if (TIERS[tier - 1].callFirst) return { text: 'Call first', tone: 'amber' }
  return { text: 'Nobody sent yet', tone: 'red' }
}

export function dispatchOf(alert) {
  const steps = (alert.escalation_steps || []).filter(Boolean)
  const teams = steps.filter((e) => e.step === 'team_sent').map((e) => e.team)
  const services = new Set()
  let call = null
  for (const entry of steps) {
    if (entry.step === 'help_requested') services.add(entry.service)
    if (entry.step === 'help_cancelled') services.delete(entry.service)
    if (entry.step === 'call_result') call = entry.result
  }
  const parts = { teams, services: [...services], call, tier: tierOf(alert) }
  return { ...parts, ...summary(parts) }
}

// Alerts still needing the operator come first, by tier. Within a tier the oldest goes first,
// so alerts that waited overnight lead when the hall opens, without ever passing a higher
// tier; a senior living alone goes ahead on a tie. Covered alerts drop below, in the same order.
export function byPriority(alerts) {
  const key = (alert) => {
    const { tier, tone } = dispatchOf(alert)
    return [tone === 'green' ? 1 : 0, tier, toMs(alert.created_at), alert.senior_has_family_contact === false ? 0 : 1]
  }
  return [...alerts].sort((a, b) => {
    const [x, y] = [key(a), key(b)]
    const i = x.findIndex((value, n) => value !== y[n])
    return i === -1 ? 0 : x[i] - y[i]
  })
}

const TONE_RANK = { red: 0, amber: 1, green: 2 }

// Map of pook name to { count, tone } for the pook grid. Alerts with no pook collect under null.
export function pookSummary(alerts) {
  const byPook = new Map()
  for (const alert of alerts) {
    const key = alert.senior_pook ?? null
    const { tone } = dispatchOf(alert)
    const seen = byPook.get(key)
    byPook.set(key, {
      count: (seen?.count ?? 0) + 1,
      tone: seen && TONE_RANK[seen.tone] < TONE_RANK[tone] ? seen.tone : tone,
    })
  }
  return byPook
}

// Which teams and services are out, and where: { name: pookName }. Open alerts only, so a
// resource is free again the moment its alert closes.
export function resourcesOut(alerts) {
  const out = {}
  for (const alert of alerts) {
    const { teams, services } = dispatchOf(alert)
    for (const name of [...teams, ...services]) out[name] = alert.senior_pook || 'No pook set'
  }
  return out
}

export function freeTeams(alerts, teams) {
  const out = resourcesOut(alerts)
  return teams.filter((team) => !(team in out))
}

// Hall hours, in Manila time whatever the viewer's clock says.
const MANILA_OFFSET_MS = 8 * 3600000
export const HALL_OPEN_HOUR = 8
export const HALL_CLOSE_HOUR = 17

// 'YYYY-MM-DD' in Manila, the form the "Hall closed today" days are kept in.
export const manilaDay = (ms) => new Date(ms + MANILA_OFFSET_MS).toISOString().slice(0, 10)

export function hallOpenAt(ms, closedDays = []) {
  const manila = new Date(ms + MANILA_OFFSET_MS)
  const day = manila.getUTCDay()
  const hour = manila.getUTCHours()
  if (day === 0 || day === 6 || closedDays.includes(manilaDay(ms))) return false
  return hour >= HALL_OPEN_HOUR && hour < HALL_CLOSE_HOUR
}

export function receivedAfterHours(alert, closedDays = []) {
  const ms = toMs(alert.created_at)
  return !Number.isNaN(ms) && !hallOpenAt(ms, closedDays)
}

const CALL_LABEL = {
  cleared: 'Call: the senior is fine',
  needs_team: 'Call: a team is needed',
  no_answer: 'Call: no answer',
}

// Timeline wording for the steps above; null for any other step.
export function dispatchStepLabel(entry) {
  switch (entry.step) {
    case 'call_result':
      return CALL_LABEL[entry.result]
    case 'team_sent':
      return `${entry.team} sent from the hall`
    case 'help_requested':
      return `${entry.service} requested`
    case 'help_cancelled':
      return `${entry.service} request cancelled`
    default:
      return null
  }
}

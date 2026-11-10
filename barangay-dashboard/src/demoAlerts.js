// TEMPORARY, for the defense demo only: remove this file, its hook in api.js and
// DemoAlertsCard (bottom of the Alerts tab) after the demo.
//
// Made-up alerts that exist only in this browser tab, to show several alerts arriving at once.
// api() answers every call about them from here, so nothing about a demo alert ever reaches the
// server or the live database. They are kept in sessionStorage, so they survive a reload of the
// tab and disappear when it is closed.

const KEY = 'seenior.demoAlerts'
const PREFIX = 'demo-'

export const isDemoId = (id) => typeof id === 'string' && id.startsWith(PREFIX)

const NAMES = [
  'Adelaida R.', 'Benigno L.', 'Consuelo A.', 'Dionisio F.', 'Estrella M.', 'Florencio T.',
  'Gregoria S.', 'Herminio C.', 'Imelda V.', 'Jacinto P.', 'Leonora B.', 'Mariano D.',
  'Natividad G.', 'Octavio Q.', 'Purificacion N.', 'Rufino E.',
]
const ANOMALY_TRIGGERS = ['inactivity', 'movement', 'screen_idle']
const pick = (list) => list[Math.floor(Math.random() * list.length)]

function read() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY) || '[]')
  } catch {
    return memory
  }
}
let memory = []
function write(list) {
  memory = list
  try {
    sessionStorage.setItem(KEY, JSON.stringify(list))
  } catch {
    /* blocked storage: the in-memory copy above is used instead */
  }
}

export const demoAlerts = () => read().filter((a) => a.status === 'escalated')

// kind: 'sos' | 'fall' | 'welfare' | 'anomaly'. A welfare check always has family, because only a
// family contact can ask for one. `pooks` is the barangay's pook list, for a realistic area.
export function addDemoAlert(kind, alone, pooks = []) {
  const list = read()
  const n = list.length + 1
  const trigger = { sos: 'sos', fall: 'fall_pattern', welfare: 'inactivity' }[kind] ?? pick(ANOMALY_TRIGGERS)
  const familyAsked = kind === 'welfare'
  const livesAlone = alone && !familyAsked
  const pook = pooks.length ? pick(pooks) : null
  const at = new Date().toISOString()
  const reason = trigger === 'sos' ? 'SOS pressed' : livesAlone ? 'No family contact' : 'No response'
  let steps = [{ step: 'escalated_barangay_auto', reason, at }]
  if (livesAlone) steps = [{ step: 'no_family_contact', at }, ...steps]
  if (familyAsked) steps = [{ step: 'escalated_barangay', at }]
  const id = `${PREFIX}${Date.now()}-${n}`
  list.push({
    sync_id: id,
    senior_sync_id: id,
    senior_name: `${NAMES[(n - 1) % NAMES.length]} (demo)`,
    senior_age: 65 + Math.floor(Math.random() * 25),
    senior_gender: null,
    senior_address: `[Demo address]${pook ? `, Pook ${pook.name}` : ''}`,
    senior_mobile: '0900 000 0000',
    senior_pook: pook?.name ?? null,
    senior_sector: pook?.sector ?? null,
    senior_has_family_contact: !livesAlone,
    trigger_type: trigger,
    risk_level: trigger === 'sos' || trigger === 'fall_pattern' ? 'high' : 'medium',
    status: 'escalated',
    created_at: at,
    location_cluster_id: null,
    escalation_steps: steps,
  })
  write(list)
}

export function clearDemoAlerts() {
  write([])
}

const STEP_FOR_ACTION = {
  send_team: ({ team }) => ({ step: 'team_sent', team }),
  responder_unavailable: ({ team }) => ({ step: 'responder_unavailable', team }),
  request_help: ({ service }) => ({ step: 'help_requested', service }),
  cancel_help: ({ service }) => ({ step: 'help_cancelled', service }),
  call_result: ({ result }) => ({ step: 'call_result', result }),
}
const CLOSE = {
  resolve: ['resolved', 'resolved_barangay'],
  'false-positive': ['false_positive', 'false_positive_barangay'],
}

// The answer to an API call about a demo alert or demo senior, or undefined when the call is
// not about one (and should go to the server as usual).
export function demoResponse(path, options = {}) {
  const parts = path.split('?')[0].split('/')
  // /barangay/alerts/{id}/{action}, /barangay/seniors/{id}, /seniors/{id}/routine
  const id = parts.find(isDemoId)
  if (!id) return undefined
  if (parts[1] === 'seniors') throw new Error('No routine for a demo senior.')
  if (parts[2] === 'seniors') return { contacts: [] }

  const list = read()
  const alert = list.find((a) => a.sync_id === id)
  if (!alert) throw new Error('This demo alert was cleared.')
  const body = options.body ? JSON.parse(options.body) : {}
  const stamp = (step) => alert.escalation_steps.push({ ...step, at: new Date().toISOString(), by: 'Demo' })
  const action = parts[4]

  if (action === 'dispatch') {
    const make = STEP_FOR_ACTION[body.action]
    if (!make) throw new Error('Unknown action.')
    stamp(make(body))
    const claimed = alert.escalation_steps.some((e) => e.step === 'acknowledged_barangay')
    if (body.action !== 'cancel_help' && !claimed) stamp({ step: 'acknowledged_barangay' })
  } else if (action === 'acknowledge') {
    stamp({ step: 'acknowledged_barangay', notes: body.notes })
  } else if (CLOSE[action]) {
    alert.status = CLOSE[action][0]
    stamp({ step: CLOSE[action][1], notes: body.notes })
  } else {
    throw new Error('Not available for a demo alert.')
  }
  write(list)
  return alert
}

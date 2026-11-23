// A stand-in for the SEENior API, for pook-preview.html only. It replaces window.fetch, so the
// real dashboard components run unchanged while every SEENior API call stays in this browser tab:
// nothing here can reach the live database. Reloading the page starts over.

const MANILA_MS = 8 * 3600000

// The most recent weekday at 10:10 AM Manila, so the sample always plays during hall hours.
function sampleNow() {
  const day = new Date(Date.now() + MANILA_MS)
  while ([0, 6].includes(day.getUTCDay())) day.setUTCDate(day.getUTCDate() - 1)
  day.setUTCHours(10, 10, 0, 0)
  return day.getTime() - MANILA_MS
}

// Shifts this tab's clock to that moment, so the hall status and timestamps read as office hours.
function shiftClock(target) {
  const RealDate = Date
  const offset = target - RealDate.now()
  window.Date = class extends RealDate {
    constructor(...args) {
      if (args.length) super(...args)
      else super(RealDate.now() + offset)
    }
    static now() {
      return RealDate.now() + offset
    }
  }
}

const POOK_NAMES = {
  north: ['Arboretum', 'Dagohoy', 'Ricarte', 'Palaris', 'Sumakwel (Area 1/3)', 'Sumakwel (Area 2)'],
  south: [
    'Aguinaldo', 'Amorsolo', 'Daang Tubo', 'Carlos P. Garcia', 'Hernandez - Timog',
    'Hernandez - Hilaga', 'Libis', 'Libis (Annex)', 'Sikatuna Bliss', 'Area 17',
  ],
}

// The same made-up names the "Fill in demo names" button uses, with numbers that are not real.
const DEMO_NAMES = [
  'Maria Santos', 'Jose Reyes', 'Ana Cruz', 'Ramon Bautista',
  'Liza Garcia', 'Eduardo Mendoza', 'Teresita Aquino', 'Roberto Villanueva',
  'Carmela Ramos', 'Antonio Castillo', 'Rosario Flores', 'Danilo Navarro',
  'Gloria Torres', 'Ernesto Domingo', 'Marites Salazar', 'Fernando Dizon',
]

function samplePooks() {
  let id = 0
  return Object.entries(POOK_NAMES).flatMap(([sector, names]) =>
    names.map((name) => {
      id += 1
      return {
        id,
        name,
        sector,
        responder_name: DEMO_NAMES[id - 1],
        responder_phone: `0900000${String(id).padStart(4, '0')}`,
      }
    })
  )
}

const POOKS = samplePooks()
const SECTOR_OF = Object.fromEntries(POOKS.map((p) => [p.name, p.sector]))

// The pook responder's name, for the walkthrough's steps.
export const responderOf = (pook) => POOKS.find((p) => p.name === pook).responder_name

// Ten made-up seniors within nine minutes, four of them in Pook Libis so one pook runs out of its
// own responder: [id, name, age, trigger, lives alone, family asked for a check, minutes ago, pook].
const SAMPLE_ALERTS = [
  ['s1', 'Remedios C.', 78, 'sos', false, false, 9, 'Libis'],
  ['s2', 'Ernesto V.', 83, 'sos', true, false, 7, 'Libis'],
  ['f1', 'Teodoro M.', 81, 'fall_pattern', true, false, 8, 'Amorsolo'],
  ['f2', 'Corazon D.', 76, 'fall_pattern', false, false, 6, 'Dagohoy'],
  ['f3', 'Rogelio B.', 72, 'fall_pattern', false, false, 4, 'Libis'],
  ['r1', 'Milagros T.', 79, 'inactivity', false, true, 5, 'Palaris'],
  ['a1', 'Luz P.', 74, 'inactivity', true, false, 8, 'Ricarte'],
  ['a2', 'Felipe R.', 80, 'screen_idle', true, false, 3, 'Aguinaldo'],
  ['o1', 'Nenita S.', 71, 'movement', false, false, 7, 'Area 17'],
  ['o2', 'Pedro G.', 75, 'inactivity', false, false, 2, 'Libis'],
]

function sampleAlerts() {
  return SAMPLE_ALERTS.map((row, index) => makeAlert(row, index))
}

// One alert row as GET /barangay/alerts returns it.
function makeAlert([id, name, age, trigger, alone, familyAsked, minutes, pook], index) {
  const at = new Date(Date.now() - minutes * 60000).toISOString()
  const reason = trigger === 'sos' ? 'SOS pressed' : alone ? 'No family contact' : 'No response'
  let steps = [{ step: 'escalated_barangay_auto', reason, at }]
  if (alone) steps = [{ step: 'no_family_contact', at }, ...steps]
  if (familyAsked) steps = [{ step: 'escalated_barangay', at }]
  return {
    sync_id: id,
    senior_sync_id: `senior-${id}`,
    senior_name: name,
    senior_age: age,
    senior_gender: null,
    senior_address: `[Sample address], Pook ${pook}`,
    senior_mobile: `0900 000 ${String(100 + index).padStart(4, '0')}`,
    senior_pook: pook,
    senior_sector: SECTOR_OF[pook],
    senior_has_family_contact: !alone,
    trigger_type: trigger,
    risk_level: trigger === 'sos' || trigger === 'fall_pattern' ? 'high' : 'medium',
    status: 'escalated',
    created_at: at,
    location_cluster_id: null,
    escalation_steps: steps,
  }
}

// The alerts the mock server holds, shared with the demo buttons below.
const db = { alerts: [], pooks: [] }

// Made-up seniors for the demo buttons. Names and numbers are not real people.
const DEMO_SENIORS = [
  'Adelaida R.', 'Benigno L.', 'Consuelo A.', 'Dionisio F.', 'Estrella M.', 'Florencio T.',
  'Gregoria S.', 'Herminio C.', 'Imelda V.', 'Jacinto P.', 'Leonora B.', 'Mariano D.',
  'Natividad G.', 'Octavio Q.', 'Purificacion N.', 'Rufino E.',
]
const ANOMALY_TRIGGERS = ['inactivity', 'movement', 'screen_idle']
const pick = (list) => list[Math.floor(Math.random() * list.length)]
let demoCount = 0

// Adds one alert that lands now. kind: 'sos' | 'fall' | 'welfare' | 'anomaly'. A welfare check
// always has family, because only a family contact can ask for one.
export function addDemoAlert(kind, alone) {
  demoCount += 1
  const trigger = { sos: 'sos', fall: 'fall_pattern', welfare: 'inactivity' }[kind] ?? pick(ANOMALY_TRIGGERS)
  const familyAsked = kind === 'welfare'
  const name = DEMO_SENIORS[(demoCount - 1) % DEMO_SENIORS.length]
  const pook = pick(POOKS).name
  const age = 65 + Math.floor(Math.random() * 25)
  const row = [`demo${demoCount}`, name, age, trigger, familyAsked ? false : alone, familyAsked, 0, pook]
  db.alerts.push(makeAlert(row, 50 + demoCount))
}

// Removes every alert, so the demo can start from an empty queue.
export function clearAlerts() {
  db.alerts.length = 0
}

// The hotline list the apps and the outside-help section show (backend/app/core/after_hours.py).
const HOTLINES = [
  { name: 'National Emergency Hotline', numbers: [{ display: '911', dial: '911' }] },
  { name: 'Q.C. Emergency Hotline', numbers: [{ display: '122', dial: '122' }] },
  { name: 'Police Station 9', numbers: [{ display: '8-434-3687', dial: '0284343687' }] },
  { name: 'QC Bureau of Fire Protection', numbers: [{ display: '(02) 833-02344', dial: '0283302344' }] },
  { name: 'QCDRRMO', numbers: [{ display: '0999-228-7362', dial: '09992287362' }] },
]

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const STEP_FOR_ACTION = {
  send_team: ({ team }) => ({ step: 'team_sent', team }),
  responder_unavailable: ({ team }) => ({ step: 'responder_unavailable', team }),
  request_help: ({ service }) => ({ step: 'help_requested', service }),
  cancel_help: ({ service }) => ({ step: 'help_cancelled', service }),
  call_result: ({ result }) => ({ step: 'call_result', result }),
}

function resourcesOn(alert) {
  const out = new Set()
  for (const entry of alert.escalation_steps) {
    if (entry.step === 'team_sent') out.add(entry.team)
    if (entry.step === 'help_requested') out.add(entry.service)
    if (entry.step === 'help_cancelled') out.delete(entry.service)
  }
  return out
}

// A browser that blocks site data throws on any localStorage read, and api() reads the token.
function ensureStorage() {
  try {
    window.localStorage.getItem('seenior.preview')
  } catch {
    const blank = { getItem: () => null, setItem: () => {}, removeItem: () => {} }
    Object.defineProperty(window, 'localStorage', { value: blank })
  }
}

// Every path the dashboard calls on the SEENior API.
const API_PATH = /^\/(barangay|auth|config|seniors|alerts)(\/|$)/

export function installMockServer() {
  ensureStorage()
  shiftClock(sampleNow())
  const realFetch = window.fetch.bind(window)
  db.alerts = sampleAlerts()
  db.pooks = POOKS.map((p) => ({ ...p }))

  function addStep(alert, step) {
    alert.escalation_steps.push({ ...step, at: new Date().toISOString(), by: 'Sample Operator' })
  }

  // Mirrors dispatch_incident() in backend/app/api/routes/barangay.py.
  function dispatch(alert, body) {
    const sending = body.action === 'send_team' ? body.team : body.action === 'request_help' ? body.service : null
    const open = db.alerts.filter((a) => a.status === 'escalated')
    if (sending && open.some((a) => resourcesOn(a).has(sending))) {
      return json({ detail: `${sending} is already out on an alert.` }, 409)
    }
    addStep(alert, STEP_FOR_ACTION[body.action](body))
    const claimed = alert.escalation_steps.some((e) => e.step === 'acknowledged_barangay')
    if (body.action !== 'cancel_help' && !claimed) addStep(alert, { step: 'acknowledged_barangay' })
    return json(alert)
  }

  function handle(method, path, body) {
    if (method === 'GET' && path === '/auth/me') return json({ barangay: 'U.P. Campus' })
    if (method === 'GET' && path === '/barangay/pooks') return json(db.pooks)
    if (method === 'GET' && path === '/config/after-hours') return json({ hotlines: HOTLINES, closed_days: [] })
    if (path.startsWith('/config/hall-closed-days')) return json({ closed_days: [] })
    if (method === 'GET' && path === '/barangay/alerts') {
      return json(db.alerts.filter((a) => a.status === 'escalated'))
    }
    const senior = path.match(/^\/barangay\/seniors\/senior-([^/]+)$/)
    if (method === 'GET' && senior) {
      const alert = db.alerts.find((a) => a.sync_id === senior[1])
      const contacts = alert?.senior_has_family_contact
        ? [{ name: `${alert.senior_name.split(' ')[0]}'s daughter`, relationship_label: 'daughter', phone: '0900 000 0200' }]
        : []
      return json({ contacts })
    }
    const pook = path.match(/^\/barangay\/pooks\/(\d+)\/responder$/)
    if (method === 'PUT' && pook) {
      const row = db.pooks.find((p) => p.id === Number(pook[1]))
      if (!row) return json({ detail: 'Pook not found' }, 404)
      row.responder_name = body?.responder_name || null
      row.responder_phone = body?.responder_phone || null
      return json(row)
    }

    const route = path.match(/^\/barangay\/alerts\/([^/]+)\/([a-z-]+)$/)
    const alert = route && db.alerts.find((a) => a.sync_id === route[1])
    if (!alert) return json({ detail: 'Not found' }, 404)
    if (method === 'POST' && route[2] === 'dispatch') return dispatch(alert, body)
    if (method === 'PATCH' && route[2] === 'acknowledge') {
      addStep(alert, { step: 'acknowledged_barangay', notes: body?.notes })
      return json(alert)
    }
    if (method === 'PATCH' && route[2] === 'resolve') {
      alert.status = 'resolved'
      addStep(alert, { step: 'resolved_barangay', notes: body?.notes })
      return json(alert)
    }
    if (method === 'PATCH' && route[2] === 'false-positive') {
      alert.status = 'false_positive'
      addStep(alert, { step: 'false_positive_barangay', notes: body?.notes })
      return json(alert)
    }
    return json({ detail: 'Not found' }, 404)
  }

  window.fetch = async (input, options = {}) => {
    const url = new URL(typeof input === 'string' ? input : input.url, window.location.href)
    // Map tiles and place-name lookups are not the SEENior API, so they go out as normal.
    if (!API_PATH.test(url.pathname)) return realFetch(input, options)
    const body = options.body ? JSON.parse(options.body) : null
    return handle(options.method ?? 'GET', url.pathname, body)
  }
}

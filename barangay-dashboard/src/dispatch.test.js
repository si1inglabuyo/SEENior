// Run with: node --test src/dispatch.test.js
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  byPriority,
  dispatchOf,
  dispatchStepLabel,
  freeTeams,
  hallOpenAt,
  manilaDay,
  pookSummary,
  receivedAfterHours,
  waitingLabel,
  isLongWait,
  resourcesOut,
  responderProblem,
  tierOf,
} from './dispatch.js'

const alert = (steps = [], extra = {}) => ({
  trigger_type: 'inactivity',
  senior_pook: 'Libis',
  senior_has_family_contact: true,
  created_at: '2026-11-04T02:00:00',
  escalation_steps: steps,
  ...extra,
})
const team = (name = 'Tanod team 1') => ({ step: 'team_sent', team: name })
const call = (result) => ({ step: 'call_result', result })
const ids = (list) => list.map((a) => a.id)

test('tiers follow the agreed order', () => {
  assert.equal(tierOf(alert([], { trigger_type: 'sos' })), 1)
  assert.equal(tierOf(alert([], { trigger_type: 'fall_pattern' })), 2)
  assert.equal(tierOf(alert([{ step: 'escalated_barangay' }])), 3)
  assert.equal(tierOf(alert([], { senior_has_family_contact: false })), 4)
  assert.equal(tierOf(alert()), 4)
})

test('SOS and falls keep their tier even for a senior living alone', () => {
  assert.equal(tierOf(alert([], { trigger_type: 'sos', senior_has_family_contact: false })), 1)
  assert.equal(tierOf(alert([], { trigger_type: 'fall_pattern', senior_has_family_contact: false })), 2)
})

test('a high tier with nobody sent is red, an anomaly starts amber with a call', () => {
  const sos = dispatchOf(alert([], { trigger_type: 'sos' }))
  assert.deepEqual([sos.text, sos.tone], ['Nobody sent yet', 'red'])
  const anomaly = dispatchOf(alert())
  assert.deepEqual([anomaly.text, anomaly.tone], ['Call first', 'amber'])
})

test('call results move an anomaly on', () => {
  assert.equal(dispatchOf(alert([call('cleared')])).tone, 'green')
  assert.equal(dispatchOf(alert([call('needs_team')])).tone, 'red')
  assert.equal(dispatchOf(alert([call('no_answer')])).text, 'No answer · needs a team')
})

test('the latest call result wins', () => {
  assert.equal(dispatchOf(alert([call('no_answer'), call('cleared')])).call, 'cleared')
})

test('anyone sent covers the alert, team or service', () => {
  assert.equal(dispatchOf(alert([team()])).tone, 'green')
  const helped = dispatchOf(alert([{ step: 'help_requested', service: 'PRC volunteers' }]))
  assert.deepEqual([helped.text, helped.tone], ['PRC volunteers on the way', 'green'])
})

test('a cancelled service is no longer out', () => {
  const a = alert([
    { step: 'help_requested', service: 'Ambulance' },
    { step: 'help_cancelled', service: 'Ambulance' },
  ])
  assert.deepEqual(dispatchOf(a).services, [])
  assert.deepEqual(resourcesOut([a]), {})
})

test('resources show where they went, and busy teams are not offered', () => {
  const a = alert([team()], { senior_pook: 'Dagohoy' })
  assert.deepEqual(resourcesOut([a]), { 'Tanod team 1': 'Dagohoy' })
  assert.deepEqual(freeTeams([a], ['Tanod team 1', 'Tanod team 2', 'Rescue van']), ['Tanod team 2', 'Rescue van'])
})

test('priority: tier first, then oldest, with a head start for living alone', () => {
  const list = [
    alert([], { id: 'anomaly' }),
    alert([], { id: 'sos-new', trigger_type: 'sos', created_at: '2026-11-04T02:05:00' }),
    alert([], { id: 'sos-old', trigger_type: 'sos', created_at: '2026-11-04T02:01:00' }),
    alert([], { id: 'fall-family', trigger_type: 'fall_pattern' }),
    alert([], { id: 'fall-alone', trigger_type: 'fall_pattern', senior_has_family_contact: false }),
  ]
  assert.deepEqual(ids(byPriority(list)), ['sos-old', 'sos-new', 'fall-alone', 'fall-family', 'anomaly'])
})

test('living alone goes ahead of an alert with family only within 5 minutes', () => {
  const family = alert([], { id: 'family', created_at: '2026-11-04T02:00:00' })
  const aloneSoon = alert([], { id: 'alone-4min', created_at: '2026-11-04T02:04:00', senior_has_family_contact: false })
  const aloneLate = alert([], { id: 'alone-6min', created_at: '2026-11-04T02:06:00', senior_has_family_contact: false })
  assert.deepEqual(ids(byPriority([family, aloneSoon])), ['alone-4min', 'family'])
  assert.deepEqual(ids(byPriority([family, aloneLate])), ['family', 'alone-6min'])
  assert.deepEqual(ids(byPriority([aloneLate, family, aloneSoon])), ['alone-4min', 'family', 'alone-6min'])
})

test('living alone never passes a higher tier', () => {
  const sos = alert([], { id: 'sos-family', trigger_type: 'sos', created_at: '2026-11-04T02:04:00' })
  const anomaly = alert([], { id: 'anomaly-alone', created_at: '2026-11-04T02:00:00', senior_has_family_contact: false })
  assert.deepEqual(ids(byPriority([anomaly, sos])), ['sos-family', 'anomaly-alone'])
})

test('a covered alert drops below anything still needing the operator', () => {
  const covered = alert([team()], { id: 'sos', trigger_type: 'sos' })
  const waiting = alert([], { id: 'anomaly' })
  assert.deepEqual(ids(byPriority([covered, waiting])), ['anomaly', 'sos'])
})

test('an overnight alert leads its tier but never passes a higher one', () => {
  const overnight = alert([], { id: 'overnight', created_at: '2026-11-03T14:00:00' })
  const morning = alert([], { id: 'morning' })
  const sos = alert([], { id: 'sos', trigger_type: 'sos' })
  assert.deepEqual(ids(byPriority([morning, overnight, sos])), ['sos', 'overnight', 'morning'])
})

test('a pook takes the colour of its worst alert, and pookless alerts are counted', () => {
  const summary = pookSummary([alert([team()]), alert([], { trigger_type: 'sos' }), alert([], { senior_pook: null })])
  assert.deepEqual(summary.get('Libis'), { count: 2, tone: 'red' })
  assert.deepEqual(summary.get(null), { count: 1, tone: 'amber' })
})

test('a responder name must be filled in, short enough and not already used', () => {
  assert.equal(responderProblem('  Rescue van ', ['Ambulance']), '')
  assert.equal(responderProblem('   ', []), 'Enter a name.')
  assert.equal(responderProblem('x'.repeat(65), []), 'Keep the name to 64 characters.')
  assert.equal(responderProblem(' ambulance', ['Ambulance']), 'ambulance is already on the list.')
})

// 2026-11-04 is a Wednesday. Manila is UTC+8.
const utc = (text) => Date.parse(`${text}Z`)

test('hall hours are Monday to Friday, 8 to 5, Manila time', () => {
  assert.equal(hallOpenAt(utc('2026-11-04T00:00:00')), true) // Wed 8:00 AM
  assert.equal(hallOpenAt(utc('2026-11-03T23:59:00')), false) // Wed 7:59 AM
  assert.equal(hallOpenAt(utc('2026-11-04T08:59:00')), true) // Wed 4:59 PM
  assert.equal(hallOpenAt(utc('2026-11-04T09:00:00')), false) // Wed 5:00 PM
  assert.equal(hallOpenAt(utc('2026-11-07T02:00:00')), false) // Sat
  assert.equal(hallOpenAt(utc('2026-11-08T02:00:00')), false) // Sun
})

test('the Manila date rolls over at Manila midnight', () => {
  assert.equal(manilaDay(utc('2026-11-03T15:59:00')), '2026-11-03')
  assert.equal(manilaDay(utc('2026-11-03T16:00:00')), '2026-11-04')
})

test('a day marked closed counts as after hours, and only that day', () => {
  const open = alert([], { created_at: '2026-11-04T02:00:00' })
  assert.equal(receivedAfterHours(open), false)
  assert.equal(receivedAfterHours(open, ['2026-11-04']), true)
  assert.equal(receivedAfterHours(open, ['2026-11-03']), false)
})

test('timeline wording', () => {
  assert.equal(dispatchStepLabel(call('no_answer')), 'Call: no answer')
  assert.equal(dispatchStepLabel(team()), 'Tanod team 1 reached, on the way')
  assert.equal(
    dispatchStepLabel({ step: 'responder_unavailable', team: 'Ana Reyes' }),
    'Ana Reyes not available'
  )
  assert.equal(dispatchStepLabel({ step: 'escalated_family' }), null)
})

test('an unavailable responder is red and asks for outside help, until someone is sent', () => {
  const none = { step: 'responder_unavailable', team: 'Ana Reyes' }
  const a = dispatchOf(alert([none]))
  assert.deepEqual([a.text, a.tone], ['Ana Reyes not available · request outside help', 'red'])
  assert.equal(dispatchOf(alert([none, { step: 'help_requested', service: 'Ambulance' }])).tone, 'green')
})

test('waitingLabel shows how long an alert has been open', () => {
  const now = Date.parse('2026-11-10T03:00:00Z')
  const at = (iso) => ({ created_at: iso })
  assert.equal(waitingLabel(at('2026-11-10T02:59:30'), now), 'Waiting under 1 min')
  assert.equal(waitingLabel(at('2026-11-10T02:56:00'), now), 'Waiting 4 min')
  assert.equal(waitingLabel(at('2026-11-10T01:00:00'), now), 'Waiting 2 hr')
  assert.equal(waitingLabel(at('2026-11-10T00:55:00'), now), 'Waiting 2 hr 5 min')
  assert.equal(waitingLabel(at('2026-11-08T03:00:00'), now), 'Waiting 2 days')
  assert.equal(waitingLabel(at(null), now), '')
})

test('isLongWait flags a wait past the limit for its tier', () => {
  const now = Date.parse('2026-11-10T03:00:00Z')
  const at = (iso) => ({ created_at: iso })
  assert.equal(isLongWait(at('2026-11-10T02:56:00'), 1, now), false)
  assert.equal(isLongWait(at('2026-11-10T02:55:00'), 1, now), true)
  assert.equal(isLongWait(at('2026-11-10T02:50:00'), 4, now), false)
  assert.equal(isLongWait(at('2026-11-10T02:45:00'), 4, now), true)
  assert.equal(isLongWait(at(null), 1, now), false)
})

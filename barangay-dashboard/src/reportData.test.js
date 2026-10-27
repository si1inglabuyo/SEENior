// Run with: node --test src/reportData.test.js
import test from 'node:test'
import assert from 'node:assert/strict'
import { addDays, buildSenior, buildWeekly, lastCompleteWeek, mondayOf, outcomeOf, spanLabel } from './reportData.js'

// 2026-10-20 is a Tuesday; its Monday is 2026-10-19.
const alert = (created_at, extra = {}) => ({
  status: 'resolved',
  trigger_type: 'inactivity',
  risk_level: 'medium',
  created_at,
  resolved_at: null,
  escalation_steps: [],
  senior_pook: 'Ricarte',
  ...extra,
})

test('mondayOf and lastCompleteWeek', () => {
  assert.equal(mondayOf('2026-10-25'), '2026-10-19') // Sunday belongs to the week before
  assert.equal(mondayOf('2026-10-19'), '2026-10-19')
  assert.equal(addDays('2026-10-19', 6), '2026-10-25')
  // Wednesday 2026-10-28 12:00 Manila -> last finished week starts 2026-10-19
  assert.equal(lastCompleteWeek(Date.parse('2026-10-28T04:00:00Z')), '2026-10-19')
})

test('week boundary is Manila midnight, not UTC', () => {
  // 2026-10-19 00:30 Manila = 2026-10-18 16:30 UTC -> inside the week starting 10-19
  // 2026-10-26 00:30 Manila = 2026-10-25 16:30 UTC -> next week
  const alerts = [alert('2026-10-18T16:30:00'), alert('2026-10-25T16:30:00')]
  const r = buildWeekly({ alerts, seniors: [], weekStart: '2026-10-19' })
  assert.equal(r.total, 1)
  assert.equal(r.perDay[0].count, 1)
})

test('pending alerts are never counted', () => {
  const r = buildWeekly({
    alerts: [alert('2026-10-20T01:00:00', { status: 'pending' })],
    seniors: [],
    weekStart: '2026-10-19',
  })
  assert.equal(r.total, 0)
})

test('empty week gives zero counts and no averages', () => {
  const r = buildWeekly({ alerts: [], seniors: [], weekStart: '2026-10-19' })
  assert.equal(r.total, 0)
  assert.equal(r.falseShare, null)
  assert.equal(r.response.resolved.avg, null)
  assert.equal(r.perDay.length, 7)
})

test('response times come off the timeline', () => {
  const a = alert('2026-10-20T00:00:00', {
    resolved_at: '2026-10-20T00:30:00',
    escalation_steps: [
      { step: 'escalated_family_server', at: '2026-10-20T00:02:00' },
      { step: 'escalated_barangay_auto', at: '2026-10-20T00:12:00' },
      { step: 'acknowledged_barangay', at: '2026-10-20T00:17:00' },
    ],
  })
  const r = buildWeekly({ alerts: [a], seniors: [], weekStart: '2026-10-19' })
  assert.equal(r.response.family.avg, 120)
  assert.equal(r.response.barangay.avg, 720)
  assert.equal(r.response.ack.avg, 300)
  assert.equal(r.response.resolved.avg, 1800)
})

test('outcomes and previous-week comparison', () => {
  const alerts = [
    alert('2026-10-20T01:00:00'),
    alert('2026-10-21T01:00:00', { status: 'false_positive' }),
    alert('2026-10-14T01:00:00', { status: 'false_positive' }),
  ]
  const r = buildWeekly({ alerts, seniors: [], weekStart: '2026-10-19' })
  assert.equal(r.outcomes.resolved, 1)
  assert.equal(r.falseShare, 50)
  assert.equal(r.previous.total, 1)
  assert.equal(r.previous.falseShare, 100)
})

test('roster snapshot', () => {
  const now = Date.parse('2026-10-26T10:00:00Z')
  const seniors = [
    { status: 'active', last_seen_at: '2026-10-26T09:00:00' },
    { status: 'active', last_seen_at: '2026-10-24T09:00:00' },
    { status: 'inactive', last_seen_at: '2026-10-01T09:00:00' },
  ]
  const r = buildWeekly({ alerts: [], seniors, weekStart: '2026-10-19', nowMs: now })
  assert.deepEqual(r.roster, { registered: 3, inactive: 1, notSeen24h: 1 })
})

test('outcomeOf separates attending from open', () => {
  assert.equal(outcomeOf({ status: 'escalated', escalation_steps: [] }), 'open')
  assert.equal(outcomeOf({ status: 'escalated', escalation_steps: [{ step: 'acknowledged_barangay' }] }), 'attending')
})

test('senior observations follow fixed rules', () => {
  const detail = {
    alerts: [
      alert('2026-10-20T01:00:00'),
      alert('2026-10-23T01:00:00'),
      alert('2026-10-24T01:00:00', { trigger_type: 'fall_pattern', risk_level: 'high' }),
    ],
  }
  const r = buildSenior({ detail, fromIso: '2026-10-01', toIso: '2026-10-31' })
  assert.equal(r.total, 3)
  assert.equal(r.falls, 1)
  assert.ok(r.observations.some((o) => o.includes('possible fall')))
  assert.ok(r.observations.some((o) => o.includes('welfare visit')))
})

test('a quiet period says so, and a period outside the window is empty', () => {
  const r = buildSenior({ detail: { alerts: [alert('2026-09-01T01:00:00')] }, fromIso: '2026-10-01', toIso: '2026-10-31' })
  assert.equal(r.total, 0)
  assert.equal(r.observations.length, 1)
})

test('spanLabel', () => {
  assert.equal(spanLabel(null), '—')
  assert.equal(spanLabel(125), '2 min 05 s')
  assert.equal(spanLabel(6360), '1 h 46 min')
})

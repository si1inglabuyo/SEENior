// Run with: node --test src/routineHistory.test.js
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  summarizeHistory, shortDay, savedNote, startedAt, chartPoints, ESTIMATE_NOTE,
} from './routineHistory.js'

const row = (day, median_value, is_estimate, time_block = 'morning', feature_name = 'inactivity_duration') => ({
  day, feature_name, time_block, median_value, mad_value: 60, sample_count: 14, is_seed: false, is_estimate,
})

test('one day is not a trend', () => {
  assert.equal(summarizeHistory([row('2026-11-10', 1800, true)]), null)
})

test('only the stillness figure is drawn, in block order', () => {
  const s = summarizeHistory([
    row('2026-11-10', 1800, true, 'evening'), row('2026-11-11', 2400, true, 'evening'),
    row('2026-11-10', 1200, true, 'morning'), row('2026-11-11', 1500, true, 'morning'),
    row('2026-11-10', 0.3, true, 'morning', 'movement_score'),
  ])
  assert.deepEqual(s.blocks.map((b) => b.block), ['morning', 'evening'])
  assert.equal(s.blocks[0].points.length, 2)
})

test('start and latest are the earliest and the newest day, whatever order rows arrive in', () => {
  const s = summarizeHistory([row('2026-11-12', 3000, false), row('2026-11-10', 1800, true), row('2026-11-11', 2400, true)])
  const b = s.blocks[0]
  assert.equal(b.start.seconds, 1800)
  assert.equal(b.latest.seconds, 3000)
  assert.equal(startedAt(b), 'Started at a guess of 30 min, now 50 min')
})

test('notes depend on which kinds of day exist', () => {
  const onlyEstimates = summarizeHistory([row('2026-11-10', 1, true), row('2026-11-11', 2, true)])
  assert.equal(onlyEstimates.hasEstimate, true)
  assert.equal(onlyEstimates.firstSavedDay, null)

  const mixed = summarizeHistory([row('2026-11-10', 1, true), row('2026-11-11', 2, false), row('2026-11-12', 3, false)])
  assert.equal(mixed.firstSavedDay, '2026-11-11')

  const onlySaved = summarizeHistory([row('2026-11-11', 2, false), row('2026-11-12', 3, false)])
  assert.equal(onlySaved.hasEstimate, false)
})

test('the wording is the agreed plain sentences', () => {
  assert.equal(ESTIMATE_NOTE, "Earlier days are an estimate worked out from the senior's saved daily activity.")
  assert.equal(savedNote('2026-11-14'), 'From Nov 14, the app saved this each night.')
  assert.equal(shortDay('2026-01-05'), 'Jan 5')
})

test('chart points span the box and a flat line sits in the middle', () => {
  const rising = chartPoints(
    [{ seconds: 0, isEstimate: true }, { seconds: 50, isEstimate: true }, { seconds: 100, isEstimate: false }], 200, 56
  )
  assert.equal(rising[0].x, 0)
  assert.equal(rising[2].x, 200)
  assert.ok(rising[2].y < rising[0].y, 'a higher value is drawn higher up')
  assert.equal(rising[2].isEstimate, false)

  const flat = chartPoints([{ seconds: 5, isEstimate: false }, { seconds: 5, isEstimate: false }], 100, 56)
  assert.equal(flat[0].y, 28)
})

// Run with: node --test src/routine.test.js
import test from 'node:test'
import assert from 'node:assert/strict'
import { summarize, headline, progress, BLOCKS } from './routine.js'

const row = (feature_name, time_block, median_value, sample_count = 14, is_seed = false) => ({
  feature_name, time_block, median_value, mad_value: 1, sample_count, is_seed,
})

function fingerprint({ movement, steps = [100, 800, 400, 10], unlocks = [5, 9, 3, 0], days = 14, seed = false }) {
  return BLOCKS.flatMap((b, i) => [
    row('movement_score', b, movement[i], days, seed),
    row('step_count', b, steps[i], days, seed),
    row('screen_unlock_count', b, unlocks[i], days, seed),
    row('inactivity_duration', b, 600, days, seed),
  ])
}

const typical = fingerprint({ movement: [0.04, 0.1, 0.06, 0.01] })

test('ranks each part of the day against the same senior', () => {
  const s = summarize(typical)
  assert.equal(s.busiest, 'afternoon')
  assert.equal(s.quietest, 'night')
  assert.equal(s.blocks.find((b) => b.block === 'night').level, 'quiet')
})

test('headline states a fact, and is null with no activity', () => {
  assert.equal(headline(summarize(typical)), 'Busiest in the afternoon; quietest at night.')
  const flat = summarize(fingerprint({ movement: [0, 0, 0, 0], steps: [0, 0, 0, 0] }))
  assert.equal(headline(flat), null)
})

test('phone use cut-offs match the app', () => {
  const by = Object.fromEntries(summarize(typical).blocks.map((b) => [b.block, b.phone]))
  assert.deepEqual(by, { morning: 'sometimes', afternoon: 'often', evening: 'sometimes', night: 'rarely' })
})

test('progress counts days, not values', () => {
  assert.equal(progress(summarize(fingerprint({ movement: [0.04, 0.1, 0.06, 0.01], days: 5, seed: true }))), 'Still learning: day 5 of 14.')
  assert.equal(progress(summarize(fingerprint({ movement: [0.04, 0.1, 0.06, 0.01], days: 0, seed: true }))), 'Just started: based on the setup answers for now.')
  assert.equal(progress(summarize(typical)), 'Learned from the last 14 days.')
})

// ---- how the system worked this out ----
import { alertAfterSeconds, proof, proofLines } from './routine.js'

test('the alert point is the median plus 2.5 / 0.6745 MADs', () => {
  assert.ok(Math.abs(alertAfterSeconds(2040, 720) - 4708.6) < 0.1)
})

test('a tiny MAD is floored the way the detector floors it', () => {
  assert.ok(Math.abs(alertAfterSeconds(2040, 60) - (2040 + (2.5 / 0.6745) * 300)) < 0.1)
})

test('the working names the numbers the card shows', () => {
  const rows = [
    { feature_name: 'inactivity_duration', time_block: 'morning', median_value: 2040, mad_value: 720, sample_count: 14, is_seed: false },
    { feature_name: 'movement_score', time_block: 'morning', median_value: 0.07, mad_value: 0.01, sample_count: 14, is_seed: false },
    { feature_name: 'step_count', time_block: 'morning', median_value: 900, mad_value: 100, sample_count: 14, is_seed: false },
    { feature_name: 'screen_unlock_count', time_block: 'morning', median_value: 4, mad_value: 1, sample_count: 14, is_seed: false },
  ]
  const text = proofLines(proof(rows)[0]).join(' ')
  assert.ok(text.includes('34 min'))
  assert.ok(text.includes('14 days'))
  assert.ok(text.includes('1 hr 18 min'))
  assert.ok(text.includes('0.6745'))
  assert.ok(text.includes('sometimes'))
})

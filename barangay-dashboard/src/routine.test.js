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

import { clock, blockHours, partsOfDay } from './routine.js'

const timing = { morning_start: 390, afternoon_start: 690, evening_start: 990, night_start: 1290, wake_minute: 405, evening_last_minute: 1130, afternoon_last_minute: 940 }

test('clock and block hours read like the senior app', () => {
  assert.equal(clock(0), '12:00 AM')
  assert.equal(clock(405), '6:45 AM')
  assert.equal(blockHours(timing, 'morning'), '6:30 AM - 11:30 AM')
  assert.equal(blockHours(timing, 'night'), '9:30 PM - 6:30 AM')
  assert.equal(blockHours(null, 'morning'), null)
})

test('parts of the day say only what the cloud holds', () => {
  const parts = partsOfDay(fingerprint({ movement: [0.04, 0.1, 0.06, 0.01] }), timing)
  const by = Object.fromEntries(parts.map((p) => [p.block, p]))
  assert.equal(by.morning.headline, 'Usually up by about 6:45 AM')
  assert.equal(by.afternoon.headline, 'Usually active until about 3:40 PM')
  assert.equal(by.evening.headline, 'Usually last active about 6:50 PM')
  assert.equal(by.night.headline, 'Time for sleep')
  assert.ok(by.morning.learned)
  assert.equal(by.morning.lines[0].label, 'Still for up to')
  assert.equal(by.morning.lines[0].value, '10 min')
})

test('afternoon has no line until the phone sends one', () => {
  const parts = partsOfDay(fingerprint({ movement: [0.04, 0.1, 0.06, 0.01] }), { ...timing, afternoon_last_minute: null })
  assert.equal(parts.find((p) => p.block === 'afternoon').headline, null)
})

test('a part still on setup answers is marked and has no usual line', () => {
  const rows = fingerprint({ movement: [0.04, 0.1, 0.06, 0.01], days: 3, seed: true })
  const morning = partsOfDay(rows, timing).find((p) => p.block === 'morning')
  assert.equal(morning.learned, false)
  assert.equal(morning.headline, 'Still learning this part of the day.')
})

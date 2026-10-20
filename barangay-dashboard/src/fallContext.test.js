import test from 'node:test'
import assert from 'node:assert/strict'
import { fallContext } from './fallContext.js'

test('a fall while moving reads as consistent with walking', () => {
  const out = fallContext('pre_dev=2.8;active=1;night=0', null)
  assert.match(out.rows[0].value, /Moving/)
  assert.equal(out.rows[1].value, 'Awake hours')
  assert.equal(out.rows[2].value, 'Normal')
  assert.match(out.reading, /walking/)
})

test('a drop from rest at night reads as a possible dropped phone', () => {
  const out = fallContext('pre_dev=0.1;active=0;night=1', null)
  assert.match(out.rows[0].value, /At rest/)
  assert.equal(out.rows[1].value, 'Sleep hours')
  assert.match(out.reading, /dropped/)
})

test('unknown motion gives no reading', () => {
  const out = fallContext('night=1', null)
  assert.match(out.rows[0].value, /Too few/)
  assert.equal(out.reading, null)
})

test('a raised detector is shown with the thresholds', () => {
  const out = fallContext(
    'pre_dev=0.1;active=0;night=1',
    'impact_min=27.5;base_impact=25.0;still_ms=8800;base_still=8000;false=2;real=0'
  )
  assert.match(out.rows[2].value, /Raised after 2/)
  assert.match(out.rows[2].value, /27\.5/)
})

test('no note, or an unreadable one, gives nothing', () => {
  assert.equal(fallContext(null, null), null)
  assert.equal(fallContext('garbage', null), null)
})

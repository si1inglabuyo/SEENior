import test from 'node:test'
import assert from 'node:assert/strict'
import { fallContext } from './fallContext.js'

test('a fall while moving says the senior may have fallen while moving', () => {
  const out = fallContext('pre_dev=4.7;active=1;night=0', null)
  assert.match(out.lines[0], /carried or used/)
  assert.match(out.lines[0], /may have fallen while moving/)
  assert.match(out.lines[1], /outside the senior's usual sleep hours/)
  assert.equal(out.lines.length, 2)
})

test('a drop from rest during sleep hours says it could simply have been dropped', () => {
  const out = fallContext('pre_dev=0.0;active=0;night=1', null)
  assert.match(out.lines[0], /lying still/)
  assert.match(out.lines[0], /could simply have been dropped/)
  assert.match(out.lines[1], /during the senior's usual sleep hours/)
})

test('unknown motion leaves out the motion sentence', () => {
  const out = fallContext('night=1', null)
  assert.equal(out.lines.length, 1)
  assert.match(out.lines[0], /sleep hours/)
})

test('a less sensitive detector is explained without thresholds', () => {
  const out = fallContext(
    'pre_dev=0.1;active=0;night=1',
    'impact_min=27.5;base_impact=25.0;still_ms=8800;base_still=8000;false=2;real=0'
  )
  assert.match(out.lines.at(-1), /less sensitive/)
})

test('no figure from the note ever appears in the sentences', () => {
  const out = fallContext(
    'pre_dev=4.7;active=1;night=0',
    'impact_min=27.5;base_impact=25.0;still_ms=8800;base_still=8000;false=2;real=0'
  )
  const text = out.lines.join(' ')
  assert.doesNotMatch(text, /\d/)
})

test('no note, or an unreadable one, gives nothing', () => {
  assert.equal(fallContext(null, null), null)
  assert.equal(fallContext('garbage', null), null)
})

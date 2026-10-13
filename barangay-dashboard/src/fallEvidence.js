// Turns a fall alert's `fall_evidence` and `fall_tolerance` strings (key=number;key=number)
// into the phase checklist a responder or panelist reads. Pure, so it is easy to test.
//
// The figures are what each detector gate measured on the fall that confirmed, set against the
// threshold that gate had to clear. The impact and stillness thresholds can be raised by fall
// tolerance, so they come from the alert's own `fall_tolerance` note when it has one; the free
// fall window and rotation threshold never move.

const DEFAULT_IMPACT_MIN = 25.0
const DEFAULT_STILL_MS = 8000
const FREE_FALL_MIN_MS = 100
const FREE_FALL_MAX_MS = 2000
const ROTATION_MIN = 2.0

export function parseNote(raw) {
  if (!raw) return null
  const out = {}
  for (const part of raw.split(';')) {
    const i = part.indexOf('=')
    if (i > 0) out[part.slice(0, i)] = Number(part.slice(i + 1))
  }
  return out
}

const fixed = (n) => (Number.isFinite(n) ? n.toFixed(1) : '—')

// Returns null when the alert carries no evidence (older rows, other triggers).
export function fallPhases(evidenceRaw, toleranceRaw) {
  const e = parseNote(evidenceRaw)
  if (!e || !Number.isFinite(e.ff) || !Number.isFinite(e.imp) || !Number.isFinite(e.still)) return null
  const t = parseNote(toleranceRaw) || {}
  const impactMin = Number.isFinite(t.impact_min) ? t.impact_min : DEFAULT_IMPACT_MIN
  const stillMs = Number.isFinite(t.still_ms) ? t.still_ms : DEFAULT_STILL_MS

  const phases = [
    {
      key: 'free_fall',
      label: 'Free fall',
      measured: `${e.ff} ms`,
      needed: `${FREE_FALL_MIN_MS}–${FREE_FALL_MAX_MS} ms`,
      met: e.ff >= FREE_FALL_MIN_MS && e.ff <= FREE_FALL_MAX_MS,
    },
    {
      key: 'impact',
      label: 'Impact',
      measured: `${fixed(e.imp)} m/s²`,
      needed: `≥ ${fixed(impactMin)} m/s²`,
      met: e.imp >= impactMin,
    },
    {
      key: 'stillness',
      label: 'Stillness',
      measured: `${e.still} ms`,
      needed: `≥ ${stillMs} ms`,
      met: e.still >= stillMs,
    },
  ]
  // Absent when the phone has no gyroscope, in which case the rotation gate was off.
  if (Number.isFinite(e.rot)) {
    phases.push({
      key: 'rotation',
      label: 'Body rotation',
      measured: `${fixed(e.rot)} rad/s`,
      needed: `≥ ${fixed(ROTATION_MIN)} rad/s`,
      met: e.rot >= ROTATION_MIN,
    })
  }
  return phases
}

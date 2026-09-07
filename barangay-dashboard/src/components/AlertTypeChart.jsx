import { IconChart } from '../icons'
import { triggerShort, TRIGGER_SHORT } from '../labels'
import SectionCard from './SectionCard'

// Same shape as "Alerts Outcome": a donut plus a legend, showing this week's alerts
// (stats.alert_types, keyed by trigger_type) split by what triggered them. Where the
// outcome donut has two slices, this one has up to seven -- one per trigger type that
// actually occurred this week.
//
// Slices are drawn in TRIGGER_ORDER so the ring and legend don't reshuffle between polls;
// any code the API sends that isn't in the map is appended in a neutral grey so nothing is
// silently dropped.
const TRIGGER_ORDER = Object.keys(TRIGGER_SHORT)

const TRIGGER_COLOR = {
  sos: '#a33329',
  fall_pattern: '#c4453c',
  inactivity: '#e08a3c',
  ml_flag: '#b58b2a',
  charging: '#5aa666',
  movement: '#4f6b8a',
  screen_idle: '#7d6ca3',
}
const UNKNOWN_COLOR = '#9aa4b2'

export default function AlertTypeChart({ types }) {
  const counts = types || {}
  const extra = Object.keys(counts).filter((k) => !TRIGGER_ORDER.includes(k))
  const slices = [...TRIGGER_ORDER, ...extra]
    .map((key) => ({
      key,
      label: triggerShort(key),
      count: counts[key] || 0,
      color: TRIGGER_COLOR[key] || UNKNOWN_COLOR,
    }))
    .filter((slice) => slice.count > 0)

  const total = slices.reduce((sum, slice) => sum + slice.count, 0)

  return (
    <SectionCard icon={<IconChart />} title="Alerts by Type">
      {total === 0 ? (
        <p className="muted">No alerts recorded this week.</p>
      ) : (
        <div className="donut-row">
          <Ring slices={slices} total={total} />
          <ul className="donut-legend">
            {slices.map((slice) => (
              <li key={slice.key}>
                <span className="dot" style={{ background: slice.color }} />
                {Math.round((slice.count / total) * 100)}% {slice.label} ({slice.count})
              </li>
            ))}
          </ul>
        </div>
      )}
    </SectionCard>
  )
}

function Ring({ slices, total }) {
  // One circle per slice, exactly as OutcomeDonut does it: each takes its share of the
  // circumference via stroke-dasharray, and strokeDashoffset rotates it past every slice
  // already drawn.
  const radius = 60
  const circumference = 2 * Math.PI * radius

  // Arc length per slice, plus the summed length of every slice before it as the rotation
  // offset. Prefix-summed with slice() so nothing is mutated mid-render (slices is <= 7).
  const lengths = slices.map((slice) => (slice.count / total) * circumference)
  const offsets = lengths.map((_, i) => lengths.slice(0, i).reduce((a, b) => a + b, 0))

  return (
    <svg viewBox="0 0 160 160" className="donut" role="img" aria-label="Alerts by type">
      {slices.map((slice, i) => (
        <circle
          key={slice.key}
          cx="80"
          cy="80"
          r={radius}
          fill="none"
          stroke={slice.color}
          strokeWidth="22"
          strokeDasharray={`${lengths[i]} ${circumference - lengths[i]}`}
          strokeDashoffset={-offsets[i]}
          transform="rotate(-90 80 80)"
        />
      ))}
    </svg>
  )
}

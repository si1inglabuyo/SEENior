import { IconChart } from '../icons'
import { CATEGORY_LABEL } from '../labels'
import SectionCard from './SectionCard'

// Same shape as "Alerts Outcome" -- a donut plus a legend -- showing this week's alerts
// grouped into the three responder-facing categories (stats.alert_categories). Every slice
// and legend row is a button: clicking one opens Alert History filtered to that category,
// across the whole active / attending / closed split (scope=all).
const CATEGORY_ORDER = ['anomaly', 'sos', 'dispatch_family']
const CATEGORY_COLOR = {
  anomaly: '#e08a3c',
  sos: '#a33329',
  dispatch_family: '#4f6b8a',
}

export default function AlertTypeChart({ categories, onSelect }) {
  const counts = categories || {}
  const slices = CATEGORY_ORDER.map((key) => ({
    key,
    label: CATEGORY_LABEL[key],
    count: counts[key] || 0,
    color: CATEGORY_COLOR[key],
  })).filter((slice) => slice.count > 0)

  const total = slices.reduce((sum, slice) => sum + slice.count, 0)

  return (
    <SectionCard icon={<IconChart />} title="Alerts by Type">
      {total === 0 ? (
        <p className="muted">No alerts recorded this week.</p>
      ) : (
        <div className="donut-row">
          <Ring slices={slices} total={total} onSelect={onSelect} />
          <ul className="donut-legend">
            {slices.map((slice) => (
              <li key={slice.key}>
                <button
                  type="button"
                  className="donut-legend-btn"
                  onClick={() => onSelect(slice.key, slice.label)}
                >
                  <span className="dot" style={{ background: slice.color }} />
                  {Math.round((slice.count / total) * 100)}% {slice.label} ({slice.count})
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </SectionCard>
  )
}

function Ring({ slices, total, onSelect }) {
  const radius = 60
  const circumference = 2 * Math.PI * radius
  const lengths = slices.map((slice) => (slice.count / total) * circumference)
  const offsets = lengths.map((_, i) => lengths.slice(0, i).reduce((a, b) => a + b, 0))

  return (
    <svg viewBox="0 0 160 160" className="donut" role="img" aria-label="Alerts by type">
      {slices.map((slice, i) => (
        <circle
          key={slice.key}
          className="donut-slice"
          cx="80"
          cy="80"
          r={radius}
          fill="none"
          stroke={slice.color}
          strokeWidth="22"
          strokeDasharray={`${lengths[i]} ${circumference - lengths[i]}`}
          strokeDashoffset={-offsets[i]}
          transform="rotate(-90 80 80)"
          onClick={() => onSelect(slice.key, slice.label)}
        >
          <title>{`${slice.label}: ${slice.count}`}</title>
        </circle>
      ))}
    </svg>
  )
}

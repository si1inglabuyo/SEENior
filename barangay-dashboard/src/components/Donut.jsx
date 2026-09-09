// The dashboard's donut chart, shared by "Alerts Outcome" and "Alerts by Type" so the two
// always look and behave the same. `segments` is an ordered list of
//   { key, label, value, color, onClick? }
// Zero-value segments are dropped from the ring but kept (greyed) in the legend, so the
// full set of categories is always listed. A segment with `onClick` becomes a button in
// the legend and a clickable arc in the ring.
const RADIUS = 60
const CIRC = 2 * Math.PI * RADIUS

export default function Donut({ segments, ariaLabel, emptyText = 'Nothing to show.' }) {
  const shown = segments.filter((s) => s.value > 0)
  const total = shown.reduce((sum, s) => sum + s.value, 0)

  if (total === 0) return <p className="muted">{emptyText}</p>

  const lengths = shown.map((s) => (s.value / total) * CIRC)
  const offsets = lengths.map((_, i) => lengths.slice(0, i).reduce((a, b) => a + b, 0))

  return (
    <div className="donut-row">
      <svg viewBox="0 0 160 160" className="donut" role="img" aria-label={ariaLabel}>
        {shown.map((s, i) => (
          <circle
            key={s.key}
            className={s.onClick ? 'donut-slice donut-slice-link' : 'donut-slice'}
            cx="80"
            cy="80"
            r={RADIUS}
            fill="none"
            stroke={s.color}
            strokeWidth="22"
            strokeDasharray={`${lengths[i]} ${CIRC - lengths[i]}`}
            strokeDashoffset={-offsets[i]}
            transform="rotate(-90 80 80)"
            onClick={s.onClick}
          >
            <title>{`${s.label}: ${s.value}`}</title>
          </circle>
        ))}
      </svg>
      <ul className="donut-legend">
        {segments.map((s) => {
          const pct = Math.round(((s.value || 0) / total) * 100)
          const body = (
            <>
              <span className="dot" style={{ background: s.color }} />
              {pct}% {s.label} ({s.value || 0})
            </>
          )
          return (
            <li key={s.key}>
              {s.onClick ? (
                <button type="button" className="donut-legend-btn" onClick={s.onClick}>
                  {body}
                </button>
              ) : (
                <span className="donut-legend-static">{body}</span>
              )}
            </li>
          )
        })}
      </ul>
    </div>
  )
}

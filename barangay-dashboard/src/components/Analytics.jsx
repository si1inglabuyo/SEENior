import { useEffect, useState } from 'react'
import { api } from '../api'
import { statusLabel } from '../labels'

// Plain SVG and CSS, no chart library. One less dependency to install, explain, or have
// break at a defense.
const OUTCOME_COLORS = {
  resolved: '#2f9e6f',
  false_positive: '#b58b2a',
  escalated: '#c4453c',
  acknowledged: '#3d6fb4',
}

export default function Analytics({ onSessionLost }) {
  const [stats, setStats] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api('/barangay/stats')
      .then(setStats)
      .catch((err) => {
        setError(err.message)
        if (err.message.includes('expired')) onSessionLost()
      })
  }, [onSessionLost])

  if (error) return <p className="error">{error}</p>
  if (!stats) return <p className="muted">Loading…</p>

  const peak = Math.max(1, ...stats.alerts_this_week.map((d) => d.count))
  const outcomes = Object.entries(stats.outcomes)
  const total = outcomes.reduce((sum, [, count]) => sum + count, 0)

  return (
    <section className="analytics">
      <div className="tiles">
        <div className="tile">
          <span className="tile-value">{stats.seniors_monitored}</span>
          <span className="tile-label">Seniors monitored</span>
        </div>
        <div className="tile">
          <span className="tile-value">{stats.open_incidents}</span>
          <span className="tile-label">Open incidents</span>
        </div>
        <div className="tile">
          <span className="tile-value">{total}</span>
          <span className="tile-label">Alerts this week</span>
        </div>
      </div>

      <div className="panel">
        <h3>Alerts this week</h3>
        <div className="bars">
          {stats.alerts_this_week.map((day) => (
            <div className="bar-col" key={day.day}>
              <div className="bar" style={{ height: `${(day.count / peak) * 100}%` }} />
              <span className="bar-count">{day.count}</span>
              <span className="bar-label">
                {new Date(`${day.day}T00:00:00`).toLocaleDateString(undefined, {
                  weekday: 'short',
                })}
              </span>
            </div>
          ))}
        </div>
      </div>

      <div className="panel">
        <h3>Outcomes</h3>
        {total === 0 ? (
          <p className="muted">No alerts recorded this week.</p>
        ) : (
          <div className="donut-row">
            <Donut outcomes={outcomes} total={total} />
            <ul className="legend">
              {outcomes.map(([status, count]) => (
                <li key={status}>
                  <span className="dot" style={{ background: OUTCOME_COLORS[status] || '#888' }} />
                  {statusLabel(status)} — {count}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </section>
  )
}

function Donut({ outcomes, total }) {
  // A ring drawn as one circle per slice using stroke-dasharray. The circumference is
  // 2πr; each slice takes its share of it, and strokeDashoffset rotates it to start where
  // the previous slice ended.
  const radius = 60
  const circumference = 2 * Math.PI * radius

  // Each slice's start point is the sum of every slice before it. Worked out up front
  // instead of accumulated inside the JSX: a variable mutated part-way through rendering
  // is exactly what React's compiler cannot reason about. There are at most four
  // outcomes, so the repeated sum costs nothing.
  const lengths = outcomes.map(([, count]) => (count / total) * circumference)
  const offsets = lengths.map((_, index) =>
    lengths.slice(0, index).reduce((sum, value) => sum + value, 0)
  )

  return (
    <svg viewBox="0 0 160 160" className="donut" role="img" aria-label="Alert outcomes">
      {outcomes.map(([status], index) => (
        <circle
          key={status}
          cx="80" cy="80" r={radius}
          fill="none"
          stroke={OUTCOME_COLORS[status] || '#888'}
          strokeWidth="22"
          strokeDasharray={`${lengths[index]} ${circumference - lengths[index]}`}
          strokeDashoffset={-offsets[index]}
          transform="rotate(-90 80 80)"
        />
      ))}
      <text x="80" y="86" textAnchor="middle" className="donut-total">{total}</text>
    </svg>
  )
}

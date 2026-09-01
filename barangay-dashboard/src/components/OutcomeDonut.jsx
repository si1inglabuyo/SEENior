import { IconChart } from '../icons'
import SectionCard from './SectionCard'

// The API's `outcomes` dict is keyed by alert status. The dashboard donut only draws two
// slices -- an alert is either done with (resolved / marked a false alarm) or still live
// (escalated / being attended).
function split(outcomes) {
  const resolved = (outcomes.resolved || 0) + (outcomes.false_positive || 0)
  const active = (outcomes.escalated || 0) + (outcomes.acknowledged || 0)
  return { resolved, active, total: resolved + active }
}

const RESOLVED_COLOR = '#5aa666'
const ACTIVE_COLOR = '#c4453c'

export default function OutcomeDonut({ outcomes }) {
  const { resolved, active, total } = split(outcomes || {})

  return (
    <SectionCard icon={<IconChart />} title="Alerts Outcome">
      {total === 0 ? (
        <p className="muted">No alerts recorded this week.</p>
      ) : (
        <div className="donut-row">
          <Ring resolved={resolved} active={active} total={total} />
          <ul className="donut-legend">
            <li>
              <span className="dot" style={{ background: RESOLVED_COLOR }} />
              {Math.round((resolved / total) * 100)}% Resolved
            </li>
            <li>
              <span className="dot" style={{ background: ACTIVE_COLOR }} />
              {Math.round((active / total) * 100)}% Active
            </li>
          </ul>
        </div>
      )}
    </SectionCard>
  )
}

function Ring({ resolved, active, total }) {
  // One circle per slice, drawn with stroke-dasharray: each slice takes its share of the
  // circumference and strokeDashoffset rotates it past the slice before it. Lifted from
  // the old Analytics Donut.
  const radius = 60
  const circumference = 2 * Math.PI * radius
  const resolvedLen = (resolved / total) * circumference
  const activeLen = (active / total) * circumference

  return (
    <svg viewBox="0 0 160 160" className="donut" role="img" aria-label="Alert outcomes">
      <circle
        cx="80"
        cy="80"
        r={radius}
        fill="none"
        stroke={RESOLVED_COLOR}
        strokeWidth="22"
        strokeDasharray={`${resolvedLen} ${circumference - resolvedLen}`}
        transform="rotate(-90 80 80)"
      />
      <circle
        cx="80"
        cy="80"
        r={radius}
        fill="none"
        stroke={ACTIVE_COLOR}
        strokeWidth="22"
        strokeDasharray={`${activeLen} ${circumference - activeLen}`}
        strokeDashoffset={-resolvedLen}
        transform="rotate(-90 80 80)"
      />
    </svg>
  )
}

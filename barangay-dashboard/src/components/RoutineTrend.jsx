import { BLOCK_LABEL } from '../routine'
import {
  ESTIMATE_NOTE, HISTORY_HEADING, HISTORY_TITLE, chartPoints, savedNote, startedAt, summarizeHistory,
} from '../routineHistory'

const W = 300
const H = 56

// How this senior's routine was learned: one small line per part of the day. Days the phone saved
// are solid, days it worked out afterwards are dotted, and two plain sentences say which is which.
// Renders nothing until there are two days to draw, so an older backend or a new senior just sees
// the card as it was.
export default function RoutineTrend({ rows }) {
  const trend = summarizeHistory(rows)
  if (!trend) return null

  return (
    <section className="routine-trend" aria-label={HISTORY_TITLE}>
      <h4 className="routine-trend-title">{HISTORY_TITLE}</h4>
      <p className="muted routine-note">{HISTORY_HEADING}</p>

      <ul className="routine-blocks">
        {trend.blocks.map((b) => {
          const pts = chartPoints(b.points, W, H)
          return (
            <li key={b.block} className="routine-block">
              <strong>{BLOCK_LABEL[b.block]}</strong>
              <svg className="routine-trend-chart" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" aria-hidden="true">
                {pts.slice(1).map((p, i) => (
                  <line
                    key={i}
                    x1={pts[i].x} y1={pts[i].y} x2={p.x} y2={p.y}
                    strokeDasharray={p.isEstimate ? '5 4' : undefined}
                    vectorEffect="non-scaling-stroke"
                  />
                ))}
              </svg>
              <p className="muted routine-line">{startedAt(b)}</p>
            </li>
          )
        })}
      </ul>

      {trend.hasEstimate && <p className="muted routine-note">{ESTIMATE_NOTE}</p>}
      {trend.firstSavedDay && <p className="muted routine-note">{savedNote(trend.firstSavedDay)}</p>}
    </section>
  )
}

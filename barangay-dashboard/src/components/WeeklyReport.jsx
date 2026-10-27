import { triggerShort } from '../labels'
import { spanLabel } from '../reportData'

const OUTCOME_ROWS = [
  ['resolved', 'Resolved'],
  ['false_alarm', 'False alarm'],
  ['acknowledged', 'Acknowledged (family)'],
  ['attending', 'Responder attending'],
  ['open', 'Still open'],
]

const day = (iso, opts) => new Date(`${iso}T00:00:00Z`).toLocaleDateString(undefined, { timeZone: 'UTC', ...opts })
const short = (iso) => day(iso, { day: 'numeric', month: 'short' })
const long = (iso) => day(iso, { day: 'numeric', month: 'short', year: 'numeric' })
const pct = (n, total) => (total ? `${Math.round((n / total) * 100)}%` : '—')

function DayChart({ perDay }) {
  const peak = Math.max(1, ...perDay.map((d) => d.count))
  return (
    <svg className="rpt-chart" viewBox="0 0 640 150" role="img" aria-label="Alerts per day">
      <line x1="40" y1="120" x2="620" y2="120" stroke="#9aa6b8" />
      {perDay.map((d, i) => {
        const x = 60 + i * 80
        const h = Math.round((d.count / peak) * 80)
        return (
          <g key={d.day}>
            {d.count > 0 && <rect x={x} y={120 - h} width="50" height={h} fill="#1f6f5c" />}
            <text x={x + 25} y={114 - h} textAnchor="middle" fontSize="12">{d.count}</text>
            <text x={x + 25} y="138" textAnchor="middle" fontSize="12">
              {day(d.day, { weekday: 'short' })}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

// The weekly summary, laid out for A4. Figures come from reportData.buildWeekly.
export default function WeeklyReport({ data, generatedAt, generatedBy }) {
  const r = data
  const triggers = Object.entries(r.triggers).sort((a, b) => b[1] - a[1])
  const topTrigger = Math.max(1, ...triggers.map(([, n]) => n))
  const resolved = r.outcomes.resolved || 0
  const falseAlarms = r.outcomes.false_alarm || 0

  return (
    <article className="rpt">
      <header className="rpt-head">
        <div>
          <h1>SEENior Weekly Summary</h1>
          <small>Office of the Senior Citizens Affairs (OSCA)</small>
        </div>
        <div className="rpt-meta">
          <b>Week of {short(r.weekStart)} – {long(r.weekEnd)}</b>
          <small>Generated {generatedAt}{generatedBy ? ` · by responder #${generatedBy}` : ''}</small>
        </div>
      </header>

      <h2>1. At a glance</h2>
      <div className="rpt-kpis">
        <div><b>{r.total}</b><span>Escalated alerts</span></div>
        <div><b>{resolved}</b><span>Resolved</span></div>
        <div><b>{falseAlarms}</b><span>False alarms{r.falseShare !== null ? ` (${r.falseShare}%)` : ''}</span></div>
        <div><b>{spanLabel(r.response.barangay.avg)}</b><span>Avg alert → barangay</span></div>
        <div><b>{r.roster.registered}</b><span>Registered seniors</span></div>
      </div>

      <h2>2. Alerts per day</h2>
      <DayChart perDay={r.perDay} />

      <div className="rpt-cols">
        <section>
          <h2>3. By trigger</h2>
          {triggers.length === 0 ? (
            <p className="muted">No alerts this week.</p>
          ) : (
            <table className="rpt-table">
              <thead><tr><th>Trigger</th><th className="n">Alerts</th><th /></tr></thead>
              <tbody>
                {triggers.map(([t, n]) => (
                  <tr key={t}>
                    <td>{triggerShort(t)}</td>
                    <td className="n">{n}</td>
                    <td><span className="rpt-bar" style={{ width: `${(n / topTrigger) * 80}px` }} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
        <section>
          <h2>4. By outcome</h2>
          <table className="rpt-table">
            <thead><tr><th>Outcome</th><th className="n">Alerts</th><th className="n">%</th></tr></thead>
            <tbody>
              {OUTCOME_ROWS.map(([key, label]) => (
                <tr key={key}>
                  <td>{label}</td>
                  <td className="n">{r.outcomes[key] || 0}</td>
                  <td className="n">{pct(r.outcomes[key] || 0, r.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <h2>Risk level</h2>
          <table className="rpt-table">
            <tbody>
              <tr><td>High</td><td className="n">{r.risk.high || 0}</td></tr>
              <tr><td>Medium</td><td className="n">{r.risk.medium || 0}</td></tr>
              {r.risk.low ? <tr><td>Low</td><td className="n">{r.risk.low}</td></tr> : null}
            </tbody>
          </table>
        </section>
      </div>

      <h2>5. Response times</h2>
      <table className="rpt-table">
        <thead><tr><th>Step</th><th className="n">Average</th><th className="n">Slowest</th><th className="n">Alerts</th></tr></thead>
        <tbody>
          {[
            ['Alert → family notified', r.response.family],
            ['Alert → barangay notified', r.response.barangay],
            ['Barangay notified → acknowledged', r.response.ack],
            ['Alert → resolved', r.response.resolved],
          ].map(([label, row]) => (
            <tr key={label}>
              <td>{label}</td>
              <td className="n">{spanLabel(row.avg)}</td>
              <td className="n">{spanLabel(row.max)}</td>
              <td className="n">{row.n}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="rpt-cols">
        <section>
          <h2>6. Alerts per pook</h2>
          {r.perPook.length === 0 ? (
            <p className="muted">No alerts this week.</p>
          ) : (
            <table className="rpt-table">
              <thead><tr><th>Pook</th><th className="n">Alerts</th></tr></thead>
              <tbody>
                {r.perPook.map(([name, n]) => (
                  <tr key={name}><td>{name}</td><td className="n">{n}</td></tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
        <section>
          <h2>7. Roster snapshot</h2>
          <table className="rpt-table">
            <tbody>
              <tr><td>Registered seniors</td><td className="n">{r.roster.registered}</td></tr>
              <tr><td>Phone not seen in 24 h</td><td className="n">{r.roster.notSeen24h}</td></tr>
              <tr><td>Inactive on roster</td><td className="n">{r.roster.inactive}</td></tr>
            </tbody>
          </table>
        </section>
      </div>

      <h2>8. Compared with the week before ({short(r.previous.from)} – {short(r.previous.to)})</h2>
      <table className="rpt-table">
        <thead><tr><th>Measure</th><th className="n">Before</th><th className="n">This week</th></tr></thead>
        <tbody>
          <tr><td>Escalated alerts</td><td className="n">{r.previous.total}</td><td className="n">{r.total}</td></tr>
          <tr>
            <td>False-alarm share</td>
            <td className="n">{r.previous.falseShare === null ? '—' : `${r.previous.falseShare}%`}</td>
            <td className="n">{r.falseShare === null ? '—' : `${r.falseShare}%`}</td>
          </tr>
          <tr>
            <td>Average alert → resolved</td>
            <td className="n">{spanLabel(r.previous.resolvedAvg)}</td>
            <td className="n">{spanLabel(r.response.resolved.avg)}</td>
          </tr>
        </tbody>
      </table>

      <div className="rpt-sig"><div>Prepared by (Responder)</div><div>Noted by (OSCA Officer)</div></div>
      <p className="rpt-conf">
        <b>CONFIDENTIAL.</b> For authorized barangay use only (RA 10173, Sec. 12(c)). Counts include
        only alerts that escalated past the senior; alerts the senior cancelled on their own phone are
        not recorded in the cloud, so totals understate all detections. SEENior detects behavioral
        deviation and is not a medical diagnostic tool.
      </p>
    </article>
  )
}

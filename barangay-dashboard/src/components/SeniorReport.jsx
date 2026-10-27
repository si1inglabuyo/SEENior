import { BLOCKS, BLOCK_LABEL, FEATURES, cell, headline, summarize } from '../routine'
import { maskPhone, dateTimeLabel } from '../format'
import { triggerShort } from '../labels'
import { outcomeOf, spanLabel } from '../reportData'

const OUTCOME_LABEL = {
  resolved: 'Resolved',
  false_alarm: 'False alarm',
  acknowledged: 'Acknowledged by family',
  attending: 'Responder attending',
  open: 'Still open',
}

// What the alert reached, read off the timeline (not stored anywhere).
function reached(alert) {
  const steps = (alert.escalation_steps || []).map((e) => e && e.step)
  if (steps.some((s) => s === 'escalated_barangay' || s === 'escalated_barangay_auto')) return 'Barangay'
  if (steps.some((s) => s === 'escalated_family' || s === 'escalated_family_server')) return 'Family'
  return 'Cloud only'
}

const dateLabel = (iso) =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString(undefined, { timeZone: 'UTC', day: 'numeric', month: 'short', year: 'numeric' })

// One senior's summary for a chosen period. Deliberately shows no street address or full phone
// number: those unlock only during an open alert (see SeniorDetail), and a filed report is not
// an open alert. The pook and a masked number are enough to follow up.
export default function SeniorReport({ data, detail, routine, generatedAt, generatedBy }) {
  const r = data
  const rows = routine?.rows ?? []
  const lead = rows.length ? headline(summarize(rows)) : null
  const family = detail.contacts || []
  const resolvedAvg = r.response.resolved.avg

  return (
    <article className="rpt">
      <header className="rpt-head">
        <div>
          <h1>SEENior Senior Summary</h1>
          <small>Office of the Senior Citizens Affairs (OSCA)</small>
        </div>
        <div className="rpt-meta">
          <b>{dateLabel(r.fromIso)} – {dateLabel(r.toIso)}</b>
          <small>Generated {generatedAt}{generatedBy ? ` · by responder #${generatedBy}` : ''}</small>
        </div>
      </header>

      <h2>1. Senior</h2>
      <table className="rpt-table rpt-kv">
        <tbody>
          <tr><td>Name</td><td>{detail.first_name} {detail.last_name}</td></tr>
          <tr><td>Age / sex</td><td>{detail.age}{detail.gender ? ` / ${detail.gender}` : ''}</td></tr>
          <tr><td>Pook</td><td>{detail.pook || 'Not set'}</td></tr>
          <tr><td>Mobile</td><td>{maskPhone(detail.mobile_number)}</td></tr>
          <tr>
            <td>Family contacts</td>
            <td>
              {family.length === 0
                ? 'None — lives alone'
                : family.map((c) => `${c.name}${c.relationship_label ? ` (${c.relationship_label})` : ''}`).join(' · ')}
            </td>
          </tr>
          <tr><td>Phone last seen</td><td>{detail.last_seen_at ? dateTimeLabel(detail.last_seen_at) : '—'}</td></tr>
        </tbody>
      </table>

      <h2>2. Period at a glance</h2>
      <div className="rpt-kpis">
        <div><b>{r.total}</b><span>Alerts that left the phone</span></div>
        <div><b>{r.outcomes.resolved || 0}</b><span>Resolved</span></div>
        <div><b>{r.outcomes.false_alarm || 0}</b><span>False alarms</span></div>
        <div><b>{r.falls}</b><span>Possible falls</span></div>
        <div><b>{r.sos}</b><span>SOS presses</span></div>
      </div>
      {resolvedAvg !== null && <p className="muted rpt-note">Average time from alert to closed: {spanLabel(resolvedAvg)}.</p>}

      <h2>3. Alert history</h2>
      {r.alerts.length === 0 ? (
        <p className="muted">No alerts in this period.</p>
      ) : (
        <table className="rpt-table">
          <thead><tr><th>Date</th><th>Trigger</th><th>Risk</th><th>Reached</th><th>Outcome</th></tr></thead>
          <tbody>
            {r.alerts.map((a) => (
              <tr key={a.sync_id}>
                <td>{dateTimeLabel(a.created_at)}</td>
                <td>{triggerShort(a.trigger_type)}</td>
                <td>{a.risk_level[0].toUpperCase() + a.risk_level.slice(1)}</td>
                <td>{reached(a)}</td>
                <td>{OUTCOME_LABEL[outcomeOf(a)]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <p className="muted rpt-note">Alerts the senior cancelled on their own phone are not recorded in the cloud and are not listed.</p>

      <h2>4. Usual routine (last 14 days)</h2>
      {rows.length === 0 ? (
        <p className="muted">The phone has not sent a routine yet.</p>
      ) : (
        <>
          {lead && <p>{lead}</p>}
          <table className="rpt-table">
            <thead>
              <tr><th />{BLOCKS.map((b) => <th key={b} className="n">{BLOCK_LABEL[b]}</th>)}</tr>
            </thead>
            <tbody>
              {FEATURES.map((f) => (
                <tr key={f.key}>
                  <td>{f.label}</td>
                  {BLOCKS.map((b) => {
                    const c = cell(rows, f.key, b)
                    return <td key={b} className="n">{c ? `${c.value}${c.isSeed ? '*' : ''}` : '—'}</td>
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          <p className="muted rpt-note">
            Typical values, not readings. * still an estimate from the setup questions, not yet learned.
          </p>
        </>
      )}

      <h2>5. Observations for follow-up</h2>
      <ul className="rpt-list">
        {r.observations.map((o) => <li key={o}>{o}</li>)}
      </ul>
      <p className="muted rpt-note">
        Behavioral observations, not a medical assessment. SEENior detects deviation from the senior’s
        own routine and does not diagnose any condition.
      </p>

      <h2>6. Follow-up notes</h2>
      <div className="rpt-box" />

      <div className="rpt-sig"><div>Prepared by (Responder)</div><div>Noted by (OSCA Officer)</div></div>
      <p className="rpt-conf">
        <b>CONFIDENTIAL.</b> Contains personal information about one senior. For authorized barangay
        use only (RA 10173, Sec. 12(c)). Generating this report is written to the access log.
      </p>
    </article>
  )
}

import { CATEGORY_LABEL, alertCategory, pookTitle, stepLabel, triggerLabel } from '../labels'
import { dateTimeLabel, maskPhone } from '../format'
import { fallContext } from '../fallContext'
import { OUTCOME_LABEL, spanLabel } from '../reportData'

const cap = (text) => (text ? text[0].toUpperCase() + text.slice(1) : '—')
const since = (seconds) => (seconds === null ? '—' : seconds === 0 ? 'at detection' : `+ ${spanLabel(seconds)}`)
const span = (seconds) => (seconds === null ? '—' : spanLabel(seconds))

// One alert, start to finish: what happened, who the senior is, what each tier did and when, and
// how it ended. Deliberately shows no street address, coordinates or full phone number: those
// unlock only while an alert is open (see AlertDetailsModal), and a filed report is a record,
// not a dispatch sheet. Whether a GPS fix was captured is stated; the fix itself is not printed.
export default function IncidentReport({ alert, data, generatedAt }) {
  const fall = alert.trigger_type === 'fall_pattern' ? fallContext(alert.fall_context, alert.fall_tolerance) : null

  return (
    <article className="rpt">
      <header className="rpt-head">
        <div>
          <h1>SEENior Incident Report</h1>
          <small>Office of the Senior Citizens Affairs (OSCA)</small>
        </div>
        <div className="rpt-meta">
          <b>Incident {data.reference}</b>
          <small>Generated {generatedAt}</small>
        </div>
      </header>

      <h2>1. Incident</h2>
      <table className="rpt-table rpt-kv">
        <tbody>
          <tr><td>What the system detected</td><td>{triggerLabel(alert.trigger_type)}</td></tr>
          <tr><td>Type</td><td>{CATEGORY_LABEL[alertCategory(alert)]}</td></tr>
          <tr><td>Risk level</td><td>{cap(alert.risk_level)}</td></tr>
          <tr><td>Detected</td><td>{dateTimeLabel(alert.created_at)}</td></tr>
          <tr><td>Reached</td><td>{data.reached}</td></tr>
          <tr><td>Outcome</td><td>{OUTCOME_LABEL[data.outcome]}</td></tr>
          <tr><td>Closed</td><td>{data.closedAt ? dateTimeLabel(data.closedAt) : 'Not closed yet'}</td></tr>
          <tr><td>Time from detection to closed</td><td>{span(data.openSeconds)}</td></tr>
        </tbody>
      </table>

      <h2>2. Senior</h2>
      <table className="rpt-table rpt-kv">
        <tbody>
          <tr><td>Name</td><td>{alert.senior_name}</td></tr>
          <tr><td>Age / sex</td><td>{alert.senior_age}{alert.senior_gender ? ` / ${alert.senior_gender}` : ''}</td></tr>
          <tr>
            <td>Pook</td>
            <td>
              {alert.senior_pook
                ? `${alert.senior_pook}${alert.senior_sector ? ` (${pookTitle(alert.senior_sector)})` : ''}`
                : 'Not set'}
            </td>
          </tr>
          <tr><td>Mobile</td><td>{maskPhone(alert.senior_mobile)}</td></tr>
          <tr>
            <td>Family contact</td>
            <td>{alert.senior_has_family_contact ? 'Registered' : 'None — lives alone, so no family was notified'}</td>
          </tr>
          <tr>
            <td>Location</td>
            <td>{alert.location_cluster_id ? 'A GPS fix was captured when the alert fired.' : 'No GPS fix was captured.'}</td>
          </tr>
        </tbody>
      </table>

      <h2>3. What happened, in order</h2>
      <table className="rpt-table">
        <thead><tr><th>Time</th><th>Event</th><th>By</th><th className="n">Since detection</th></tr></thead>
        <tbody>
          <tr>
            <td>{dateTimeLabel(alert.created_at)}</td>
            <td>Alert detected on the senior’s phone</td>
            <td>System</td>
            <td className="n">{since(0)}</td>
          </tr>
          {data.steps.map(({ entry, sinceSeconds }, i) => (
            <tr key={i}>
              <td>{dateTimeLabel(entry.at)}</td>
              <td>
                {stepLabel(entry, alert)}
                {entry.notes && <div className="rpt-note-quote">“{entry.notes}”</div>}
              </td>
              <td>{entry.by || '—'}</td>
              <td className="n">{since(sinceSeconds)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="muted rpt-note">
        An alert the senior answers “safe” on their own phone before it escalates is not sent to the cloud, so a
        report can only be made for alerts that left the phone.
      </p>

      <h2>4. Response times</h2>
      <table className="rpt-table rpt-kv">
        <tbody>
          <tr><td>Detection → family notified</td><td>{span(data.times.family)}</td></tr>
          <tr><td>Detection → barangay notified</td><td>{span(data.times.barangay)}</td></tr>
          <tr><td>Barangay notified → responder attending</td><td>{span(data.times.attending)}</td></tr>
          <tr><td>Detection → closed</td><td>{span(data.times.closed)}</td></tr>
        </tbody>
      </table>

      {fall && (
        <>
          <h2>5. Just before the fall</h2>
          <ul className="rpt-list">
            {fall.lines.map((line) => <li key={line}>{line}</li>)}
          </ul>
          <p className="muted rpt-note">A hint only. It was not confirmed with the senior or family.</p>
        </>
      )}

      <h2>{fall ? '6' : '5'}. Action taken</h2>
      <div className="rpt-box" />

      <h2>{fall ? '7' : '6'}. Follow-up needed</h2>
      <div className="rpt-box" />
      <p className="muted rpt-note">
        Behavioral signal, not a medical assessment. SEENior detects a deviation and asks people to check;
        it does not diagnose any condition.
      </p>

      <div className="rpt-sig"><div>Prepared by (Responder)</div><div>Noted by (OSCA Officer)</div></div>
      <p className="rpt-conf">
        <b>CONFIDENTIAL.</b> Contains personal information about one senior. For authorized barangay use only
        (RA 10173, Sec. 12(c)). Generating this report is written to the access log.
      </p>
    </article>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { api, formatTime, timeAgo } from '../api'
import { stepLabel, statusLabel, triggerLabel } from '../labels'

// Short enough that a responder watching the screen sees a new incident appear on its
// own, long enough not to hammer a free-tier service. It also keeps the Render instance
// awake, which is what keeps the server-side escalation clock running.
const POLL_MS = 10000

export default function IncidentQueue({ onSessionLost }) {
  const [scope, setScope] = useState('active')
  // null means "not fetched yet", which is what drives the loading state -- a separate
  // `loading` flag would have to be set from inside the effect, and setting state
  // synchronously in an effect is what causes cascading renders.
  const [alerts, setAlerts] = useState(null)
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState('')

  const loading = alerts === null
  const rows = alerts ?? []

  const load = useCallback(async () => {
    try {
      const data = await api(`/barangay/alerts?scope=${scope}`)
      setAlerts(data)
      setError('')
      // Keep the open incident in step with the poll, so a status change made in another
      // browser (or by the escalation clock) shows up without closing the panel.
      setSelected((current) =>
        current ? data.find((a) => a.sync_id === current.sync_id) || current : null
      )
    } catch (err) {
      setError(err.message)
      // Settle the loading state even on failure, or a first fetch that fails would
      // leave "Loading…" on screen forever with the error hidden underneath it.
      setAlerts((current) => current ?? [])
      if (err.message.includes('expired')) onSessionLost()
    }
  }, [scope, onSessionLost])

  // Polling the API is a subscription to an external system, which is what effects are
  // for -- but the lint rule cannot tell that apart from a render-loop bug, because
  // `load` eventually calls setState. Every setState it makes lands after an await,
  // never synchronously during the effect, so there is no cascading render here.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load()
    const timer = setInterval(load, POLL_MS)
    return () => clearInterval(timer)
  }, [load])

  function changeScope(next) {
    setScope(next)
    setAlerts(null)
    setSelected(null)
  }

  async function act(alert, action, notes) {
    try {
      await api(`/barangay/alerts/${alert.sync_id}/${action}`, {
        method: 'PATCH',
        body: JSON.stringify({ notes: notes || null }),
      })
      setSelected(null)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="queue-layout">
      <section className="queue">
        <div className="queue-head">
          <h2>{scope === 'active' ? 'Open incidents' : 'Incident log'}</h2>
          <div className="scope-toggle">
            <button
              className={scope === 'active' ? 'active' : ''}
              onClick={() => changeScope('active')}
            >
              Open
            </button>
            <button
              className={scope === 'history' ? 'active' : ''}
              onClick={() => changeScope('history')}
            >
              Log
            </button>
          </div>
        </div>

        {error && <p className="error">{error}</p>}
        {loading && <p className="muted">Loading…</p>}

        {!loading && rows.length === 0 && (
          <div className="empty">
            <p className="empty-title">
              {scope === 'active' ? 'No open incidents' : 'Nothing logged yet'}
            </p>
            <p className="muted">
              {scope === 'active'
                ? 'Seniors in your barangay are being monitored. An incident will appear here the moment one is escalated to you.'
                : 'Incidents appear here once they have been handled.'}
            </p>
          </div>
        )}

        <ul className="cards">
          {rows.map((alert) => (
            <li
              key={alert.sync_id}
              className={
                'card' +
                (alert.risk_level === 'high' ? ' high' : '') +
                (selected?.sync_id === alert.sync_id ? ' selected' : '')
              }
              onClick={() => setSelected(alert)}
            >
              <div className="card-top">
                <span className={`risk risk-${alert.risk_level}`}>{alert.risk_level}</span>
                <span className="muted">{timeAgo(alert.created_at)}</span>
              </div>
              <p className="card-name">{alert.senior_name}, {alert.senior_age}</p>
              <p className="card-reason">{triggerLabel(alert.trigger_type)}</p>
              <p className="card-address">{alert.senior_address}</p>
              <span className={`status status-${alert.status}`}>
                {statusLabel(alert.status)}
              </span>
            </li>
          ))}
        </ul>
      </section>

      {selected && (
        <IncidentDetail alert={selected} onClose={() => setSelected(null)} onAct={act} />
      )}
    </div>
  )
}

function IncidentDetail({ alert, onClose, onAct }) {
  const [notes, setNotes] = useState('')
  const open = alert.status === 'escalated'

  return (
    <aside className="detail">
      <div className="detail-head">
        <h3>{alert.senior_name}</h3>
        <button className="close" onClick={onClose}>✕</button>
      </div>

      <span className={`risk risk-${alert.risk_level}`}>{alert.risk_level} risk</span>
      <p className="detail-reason">{triggerLabel(alert.trigger_type)}</p>

      <dl className="detail-facts">
        <dt>Age</dt><dd>{alert.senior_age}</dd>
        <dt>Address</dt><dd>{alert.senior_address}</dd>
        <dt>Mobile</dt>
        <dd><a href={`tel:${alert.senior_mobile}`}>{alert.senior_mobile}</a></dd>
        <dt>Raised</dt><dd>{formatTime(alert.created_at)}</dd>
        <dt>Status</dt><dd>{statusLabel(alert.status)}</dd>
      </dl>

      <h4>What happened, in order</h4>
      <ol className="timeline">
        {(alert.escalation_steps || []).map((step, index) => (
          <li key={index}>
            <span className="timeline-when">{formatTime(step.at)}</span>
            <span className="timeline-what">{stepLabel(step.step)}</span>
            {step.by && <span className="timeline-by">by {step.by}</span>}
            {step.notes && <span className="timeline-notes">“{step.notes}”</span>}
          </li>
        ))}
        {(!alert.escalation_steps || alert.escalation_steps.length === 0) && (
          <li className="muted">No steps recorded.</li>
        )}
      </ol>

      {open && (
        <div className="actions">
          <label htmlFor="notes">Notes (optional)</label>
          <textarea
            id="notes"
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="e.g. Tanod dispatched, arriving in 10 minutes"
          />
          <button className="btn primary" onClick={() => onAct(alert, 'acknowledge', notes)}>
            We are attending
          </button>
          <button className="btn ok" onClick={() => onAct(alert, 'resolve', notes)}>
            Senior is safe — close
          </button>
          {/* Separate from "close" on purpose: §10 targets a false-positive rate of 15%
              or less, and that can only be measured if wrong alerts are marked wrong. */}
          <button className="btn muted-btn" onClick={() => onAct(alert, 'false-positive', notes)}>
            False alarm
          </button>
        </div>
      )}
    </aside>
  )
}

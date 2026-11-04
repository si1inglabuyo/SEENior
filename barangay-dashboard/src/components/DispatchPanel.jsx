import { useState } from 'react'
import { recordAccess } from '../audit'
import { maskPhone } from '../format'
import { TIERS, dispatchOf, freeTeams, resourcesOut } from '../dispatch'

const CALL_BUTTONS = [
  { result: 'cleared', label: 'Senior is fine' },
  { result: 'needs_team', label: 'Needs a team' },
  { result: 'no_answer', label: 'No answer' },
]

// The number stays masked until asked for, and asking is written to the access log.
function SeniorPhone({ alert }) {
  const [shown, setShown] = useState(false)
  if (!alert.senior_mobile) return <span className="muted">No number on file</span>
  if (shown) return <a href={`tel:${alert.senior_mobile.replace(/\s/g, '')}`}>{alert.senior_mobile}</a>
  return (
    <>
      {maskPhone(alert.senior_mobile)}{' '}
      <button
        type="button"
        className="dispatch-reveal"
        onClick={() => {
          setShown(true)
          recordAccess('senior_phone_reveal', { sync_id: alert.senior_sync_id })
        }}
      >
        Show number
      </button>
    </>
  )
}

function CallFirst({ alert, call, busy, onSend }) {
  return (
    <div className="dispatch-action">
      <p className="dispatch-action-title">Call the senior first</p>
      <p className="dispatch-hint">
        Mobile: <SeniorPhone alert={alert} />
      </p>
      <div className="dispatch-btns">
        {CALL_BUTTONS.map(({ result, label }) => (
          <button
            key={result}
            type="button"
            className={call === result ? 'btn-outline is-on' : 'btn-outline'}
            aria-pressed={call === result}
            disabled={busy}
            onClick={() => onSend(alert, { action: 'call_result', result })}
          >
            {label}
          </button>
        ))}
      </div>
      {call === 'cleared' && (
        <p className="dispatch-hint muted">Close it as Resolved or False Alert below.</p>
      )}
    </div>
  )
}

// The response panel in an alert's Details: the tier, the call for anomaly alerts, a hall team,
// and outside help. Only open alerts show it. Every button adds one step to the timeline.
export default function DispatchPanel({ alert, activeAlerts, teamList, serviceList, busy, error, onSend }) {
  const dispatch = dispatchOf(alert)
  const { teams, services, call, tier, text, tone } = dispatch
  const { label, action, callFirst } = TIERS[tier - 1]
  const free = freeTeams(activeAlerts, teamList)
  const out = resourcesOut(activeAlerts)

  return (
    <div className="dispatch-panel">
      <div className="dispatch-head">
        <span className={`tier-badge tier-badge-${tier}`}>Tier {tier}</span>
        <div>
          <p className="dispatch-action-title">{label}</p>
          <p className="dispatch-hint muted">{action}</p>
        </div>
      </div>
      <p className={`dispatch-state tone-${tone}`}>{text}</p>

      {callFirst && teams.length === 0 && (
        <CallFirst alert={alert} call={call} busy={busy} onSend={onSend} />
      )}

      <div className="dispatch-action">
        <p className="dispatch-action-title">Send a hall team</p>
        {free.length > 0 ? (
          <div className="dispatch-btns">
            {free.map((team) => (
              <button
                key={team}
                type="button"
                className="btn-outline"
                disabled={busy}
                onClick={() => onSend(alert, { action: 'send_team', team })}
              >
                Send {team}
              </button>
            ))}
          </div>
        ) : (
          <p className="dispatch-hint tone-red">
            {teamList.length === 0
              ? 'No hall teams are set up. Add them under Manage responders.'
              : 'Every hall team is out. Request outside help below, or wait for a team to come back.'}
          </p>
        )}
      </div>

      <div className="dispatch-action">
        <p className="dispatch-action-title">Request more help</p>
        <div className="dispatch-chips">
          {[...new Set([...serviceList, ...services])].map((service) => {
            const mine = services.includes(service)
            const elsewhere = !mine && service in out
            return (
              <button
                key={service}
                type="button"
                className={mine ? 'dispatch-chip is-on' : 'dispatch-chip'}
                aria-pressed={mine}
                disabled={busy || elsewhere}
                onClick={() =>
                  onSend(alert, { action: mine ? 'cancel_help' : 'request_help', service })
                }
              >
                <span>{service}</span>
                <small>
                  {mine ? 'Requested · click to cancel' : elsewhere ? `Out · ${out[service]}` : 'Available'}
                </small>
              </button>
            )
          })}
        </div>
      </div>

      {error && <p className="error">{error}</p>}
    </div>
  )
}

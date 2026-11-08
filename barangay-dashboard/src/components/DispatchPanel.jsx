import { useState } from 'react'
import { recordAccess } from '../audit'
import { maskPhone } from '../format'
import { TIERS, dispatchOf, freeTeams, resourcesOut } from '../dispatch'
import { isAttending } from '../labels'

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

// A phone number shown large so it can be read across the desk, with a Copy button for dialling
// it on the staff's own phone and a tel: link for computers that can place calls.
function CopyNumber({ display, dial, big }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(display)
      setCopied(true)
      setTimeout(() => setCopied(false), 1800)
    } catch {
      /* clipboard blocked -- the number is still on screen to read */
    }
  }

  return (
    <span className="copy-number">
      <strong className={big ? 'copy-number-big' : 'copy-number-small'}>{display}</strong>
      <button type="button" className="btn-outline copy-number-btn" onClick={copy}>
        {copied ? 'Copied' : 'Copy'}
      </button>
      <a className="copy-number-call" href={`tel:${dial.replace(/[^\d+]/g, '')}`}>
        Call
      </a>
    </span>
  )
}

// The pook's designated responder: the first person the desk staff phone for this area.
function PookResponderCard({ alert, pook, reached, outElsewhere, unavailable, busy, onSend }) {
  const name = pook.responder_name
  return (
    <div className="dispatch-action">
      <p className="dispatch-action-title">Phone the {pook.name} responder</p>
      <p className="pook-responder-line">{name}</p>
      <CopyNumber display={pook.responder_phone} dial={pook.responder_phone} big />
      {reached ? (
        <p className="dispatch-hint tone-green">{name} is on the way.</p>
      ) : outElsewhere ? (
        <p className="dispatch-hint tone-red">{name} is already out on another alert.</p>
      ) : (
        <div className="dispatch-btns">
          <button
            type="button"
            className="btn-outline"
            disabled={busy}
            onClick={() => onSend(alert, { action: 'send_team', team: name })}
          >
            Reached, on the way
          </button>
          <button
            type="button"
            className={unavailable ? 'btn-outline is-on' : 'btn-outline'}
            aria-pressed={unavailable}
            disabled={busy || unavailable}
            onClick={() => onSend(alert, { action: 'responder_unavailable', team: name })}
          >
            Not available
          </button>
        </div>
      )}
    </div>
  )
}

// The old hall-team buttons, for a pook with no responder set (or an alert with no pook).
function HallTeams({ alert, free, teamList, busy, onSend }) {
  return (
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
  )
}

// Outside help, each with its hotline. One tap records "called, on the way".
function OutsideHelp({ alert, services, mine, out, hotlines, busy, onSend }) {
  const numbersOf = (service) =>
    hotlines.find((h) => h.name.toLowerCase() === service.toLowerCase())?.numbers ?? []
  return (
    <div className="dispatch-action">
      <p className="dispatch-action-title">Outside help</p>
      <ul className="outside-help">
        {services.map((service) => {
          const isMine = mine.includes(service)
          const elsewhere = !isMine && service in out
          return (
            <li key={service} className="outside-help-row">
              <div className="outside-help-who">
                <span className="outside-help-name">{service}</span>
                {numbersOf(service).map((n) => (
                  <CopyNumber key={n.dial} display={n.display} dial={n.dial} />
                ))}
              </div>
              <button
                type="button"
                className={isMine ? 'btn-outline is-on' : 'btn-outline'}
                aria-pressed={isMine}
                disabled={busy || elsewhere}
                onClick={() =>
                  onSend(alert, { action: isMine ? 'cancel_help' : 'request_help', service })
                }
              >
                {isMine ? 'On the way · cancel' : elsewhere ? `Out · ${out[service]}` : 'Called, on the way'}
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

// The response panel in an alert's Details: the tier, the call for anomaly alerts, the pook's
// responder, and outside help. Only open alerts show it. Every button adds one step to the timeline.
export default function DispatchPanel({
  alert,
  activeAlerts,
  pooks,
  hotlines = [],
  teamList,
  serviceList,
  busy,
  error,
  onSend,
}) {
  const [showHelp, setShowHelp] = useState(false)
  const dispatch = dispatchOf(alert)
  const { teams, services, call, tier, text, tone, unavailable } = dispatch
  const { label, action, callFirst } = TIERS[tier - 1]
  const out = resourcesOut(activeAlerts)

  const pook = (pooks ?? []).find((p) => p.name === alert.senior_pook)
  const hasResponder = Boolean(pook?.responder_name)
  const name = pook?.responder_name
  const reached = hasResponder && teams.includes(name)
  const outElsewhere = hasResponder && !reached && name in out
  const notReachable = hasResponder && !reached && (unavailable.includes(name) || outElsewhere)

  // Outside help opens by itself once the responder cannot come, when there is no responder to
  // phone, or when something is already requested. Otherwise it is one click away.
  const helpOpen = showHelp || notReachable || !hasResponder || services.length > 0
  const helpNames = [...new Set([...hotlines.map((h) => h.name), ...serviceList, ...services])]

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
      {!isAttending(alert) && (
        <p className="dispatch-hint muted">Any step below marks you as attending. No need to Acknowledge first.</p>
      )}

      {callFirst && teams.length === 0 && (
        <CallFirst alert={alert} call={call} busy={busy} onSend={onSend} />
      )}

      {hasResponder ? (
        <PookResponderCard
          alert={alert}
          pook={pook}
          reached={reached}
          outElsewhere={outElsewhere}
          unavailable={unavailable.includes(name)}
          busy={busy}
          onSend={onSend}
        />
      ) : (
        <>
          <p className="dispatch-hint muted">
            {pook
              ? `No responder is set for ${pook.name}. Set one under Manage responders.`
              : 'This senior has no pook on file, so there is no responder to phone.'}
          </p>
          <HallTeams
            alert={alert}
            free={freeTeams(activeAlerts, teamList)}
            teamList={teamList}
            busy={busy}
            onSend={onSend}
          />
        </>
      )}

      {helpOpen ? (
        <OutsideHelp
          alert={alert}
          services={helpNames}
          mine={services}
          out={out}
          hotlines={hotlines}
          busy={busy}
          onSend={onSend}
        />
      ) : (
        <button type="button" className="link-btn" onClick={() => setShowHelp(true)}>
          Need outside help? Show hotlines
        </button>
      )}

      {error && <p className="error">{error}</p>}
    </div>
  )
}

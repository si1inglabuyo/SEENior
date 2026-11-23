import { useState } from 'react'
import { TIERS, dispatchOf, freeTeams, resourcesOut } from '../dispatch'

// Shown for every alert type. The only thing to record is a call that went unanswered; if the senior is safe, the alert is closed once, in
// "Close the alert" below.
function UnreachableButton({ alert, call, busy, onSend }) {
  const unanswered = call === 'no_answer'
  return (
    <button
      type="button"
      className={unanswered ? 'btn-outline is-on' : 'btn-outline'}
      aria-pressed={unanswered}
      disabled={busy || unanswered}
      onClick={() => onSend(alert, { action: 'call_result', result: 'no_answer' })}
    >
      Did Not Answer / Unreachable
    </button>
  )
}

// A phone number shown large so it can be read across the desk. Calls are placed by hand, so
// there is no Call link and no Copy button.
function CopyNumber({ display, big }) {
  return <strong className={big ? 'copy-number-big' : 'copy-number-small'}>{display}</strong>
}

// A PH mobile number in the 4-3-4 groups people read aloud ("0900 000 0007"), so it is easy to
// dial by hand. Anything else (landlines, +63, already spaced) is shown as typed.
function groupPhone(value) {
  const digits = (value ?? '').trim()
  return /^09\d{9}$/.test(digits) ? `${digits.slice(0, 4)} ${digits.slice(4, 7)} ${digits.slice(7)}` : value
}

// The pook's designated responder: the first person the desk staff phone for this area.
function PookResponderCard({ alert, pook, reached, outElsewhere, unavailable, busy, onSend }) {
  const name = pook.responder_name
  return (
    <div className="dispatch-action">
      <p className="dispatch-action-title">Contact a responder near Pook {pook.name}</p>
      {/* Same row format as the Contact numbers list: who on the left, number on the right. */}
      <div className="pook-responder-card">
        <div className="pook-responder-card-row">
          <span className="contact-numbers-who">{name}</span>
          <strong className="contact-numbers-phone">{groupPhone(pook.responder_phone)}</strong>
        </div>
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
              Reached, on the Way
            </button>
            <button
              type="button"
              className={unavailable ? 'btn-outline is-on' : 'btn-outline'}
              aria-pressed={unavailable}
              disabled={busy || unavailable}
              onClick={() => onSend(alert, { action: 'responder_unavailable', team: name })}
            >
              Not Available
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

// The old hall-team buttons, for a pook with no responder set (or an alert with no pook).
function HallTeams({ alert, free, teamList, busy, onSend }) {
  return (
    <div className="dispatch-action">
      <p className="dispatch-action-title">Send a Hall Team</p>
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
      <p className="dispatch-action-title">Outside Help</p>
      <ul className="outside-help">
        {services.map((service) => {
          const isMine = mine.includes(service)
          const elsewhere = !isMine && service in out
          return (
            <li key={service} className="outside-help-row">
              <div className="outside-help-who">
                <span className="outside-help-name">{service}</span>
                <span className="outside-help-numbers">
                  {numbersOf(service).map((n) => (
                    <CopyNumber key={n.dial} display={n.display} />
                  ))}
                </span>
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
                {isMine ? 'On the Way · Cancel' : elsewhere ? `Out · ${out[service]}` : 'Called, on the Way'}
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
  // An unanswered call keeps the amber "Call first" badge and adds the red one beside it.
  const noAnswer = call === 'no_answer' && teams.length === 0 && services.length === 0
  const keepCallFirst = noAnswer && TIERS[tier - 1].callFirst
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
      {/* The tier badge itself sits in the details header, next to the status pill. */}
      <div className="dispatch-state-row">
        {keepCallFirst ? (
          <>
            <p className="dispatch-state tone-amber">Call First</p>
            <p className="dispatch-state tone-red">{text}</p>
          </>
        ) : (
          <p className={`dispatch-state tone-${tone}`}>{text}</p>
        )}
        {teams.length === 0 && call !== 'no_answer' && (
          <UnreachableButton alert={alert} call={call} busy={busy} onSend={onSend} />
        )}
      </div>
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
              ? `No responder is set for ${pook.name}. Set one in the Responders panel.`
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

      {/* The pook's responder is out on another alert or cannot come: a hall team can still go,
          so a second alert from the same pook is not left with outside hotlines only. */}
      {notReachable && (
        <HallTeams
          alert={alert}
          free={freeTeams(activeAlerts, teamList)}
          teamList={teamList}
          busy={busy}
          onSend={onSend}
        />
      )}

      {teams.length === 0 && (
        <p className="dispatch-hint muted">
          If the senior is safe, mark the alert as Resolved below.
        </p>
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

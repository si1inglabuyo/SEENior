import { alertCategory, CATEGORY_CLASS, CATEGORY_LABEL, displayStatus, pookTitle } from '../labels'
import { initials, dateTimeLabel } from '../format'
import { isActiveAlert } from '../historyFilters'
import { isLongWait, waitingLabel } from '../dispatch'
import StatusPill from './StatusPill'

// One incident row, shared by the Alerts queue and Alert History's chronological view --
// identical markup on both screens (the only difference is which alerts each hands in). The
// row itself carries no controls: it shows who the alert is about, when it fired, and the
// type/status badges, and the whole row is a button that opens the Details modal.
// Resolve / False Alert live inside that modal.

// `dispatch` is the optional dispatchOf() result: the tier badge and how the response is going.
// `afterHours` adds the label for an alert that arrived while the hall was closed.
export default function AlertRow({ alert, onShowDetails, dispatch, afterHours = false }) {
  const category = alertCategory(alert)

  return (
    <li
      className={`queue-row queue-row-clickable${dispatch && dispatch.tier <= 2 && dispatch.tone !== 'green' ? ' queue-row-urgent' : ''}`}
      role="button"
      tabIndex={0}
      onClick={() => onShowDetails(alert)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onShowDetails(alert)
        }
      }}
    >
      <span className="avatar">{initials(alert.senior_name)}</span>
      <div className="queue-row-main">
        <p className="alert-row-name">{alert.senior_name}</p>
        <p className="queue-row-meta">
          <span className="queue-row-time">{dateTimeLabel(alert.created_at)}</span>
          {/* Only while someone still has to act: shows the oldest-first order inside a tier. */}
          {dispatch && dispatch.tone !== 'green' && (
            <span
              className={isLongWait(alert, dispatch.tier) ? 'queue-row-waiting is-long' : 'queue-row-waiting'}
            >
              {' · '}
              {waitingLabel(alert)}
            </span>
          )}
        </p>
        {/* Only while the incident is open: the responder needs the number to call at once. */}
        {isActiveAlert(alert) && alert.senior_mobile && (
          <p className="queue-row-phone">
            <a
              href={`tel:${alert.senior_mobile.replace(/\s/g, '')}`}
              onClick={(e) => e.stopPropagation()}
              onKeyDown={(e) => e.stopPropagation()}
            >
              {alert.senior_mobile}
            </a>
          </p>
        )}
        {dispatch && (
          <p className={`queue-row-dispatch tone-${dispatch.tone}`}>{dispatch.text}</p>
        )}
      </div>

      <div className="queue-row-side">
        <div className="queue-row-badges">
          {dispatch && (
            <span className={`tier-badge tier-badge-${dispatch.tier}`}>Tier {dispatch.tier}</span>
          )}
          {afterHours && <span className="badge-after-hours">Received after hours</span>}
          {/* Not a risk level -- the design spec says this stays amber, never the
              risk-level red, so it can't be mistaken for a fourth severity tier. Kept on
              the row because "nobody else was notified" is context the responder needs
              before they even open the incident. */}
          {!alert.senior_has_family_contact && (
            <span className="badge-alone" title="This senior has no family contact linked">
              Lives alone
            </span>
          )}
          {/* Which area of the barangay the senior lives in -- context for whoever is
              assigned to that pook. Not a risk level, so it is neutral, never red or amber. */}
          {alert.senior_pook && (
            <span className="badge-pook" title={pookTitle(alert.senior_sector)}>
              {alert.senior_pook}
            </span>
          )}
          <span className={`type-badge ${CATEGORY_CLASS[category]}`.trim()}>
            {CATEGORY_LABEL[category]}
          </span>
          <StatusPill status={displayStatus(alert)} />
        </div>
      </div>
    </li>
  )
}

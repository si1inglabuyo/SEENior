import { alertCategory, CATEGORY_CLASS, CATEGORY_LABEL, displayStatus, pookTitle } from '../labels'
import { initials, dateTimeLabel } from '../format'
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
      className="queue-row queue-row-clickable"
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
        </p>
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

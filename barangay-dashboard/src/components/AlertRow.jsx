import { alertCategory, CATEGORY_LABEL } from '../labels'
import { initials, dateTimeLabel } from '../format'
import StatusPill from './StatusPill'

// One incident row, shared by the Alerts queue and Alert History's chronological view --
// identical markup on both screens (the only difference is which alerts each hands in). The
// row itself carries no controls: it shows who the alert is about, when it fired, and the
// type/status badges, and the whole row is a button that opens the Details modal.
// Acknowledge / Resolve / False Positive live inside that modal.
const CATEGORY_CLASS = {
  sos: 'type-badge-sos',
  dispatch_family: 'type-badge-dispatch',
  anomaly: '',
}

export default function AlertRow({ alert, onShowDetails }) {
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
      </div>

      <div className="queue-row-side">
        <div className="queue-row-badges">
          {/* Not a risk level -- CLAUDE.md is explicit this stays amber, never the
              risk-level red, so it can't be mistaken for a fourth severity tier. Kept on
              the row because "nobody else was notified" is context the responder needs
              before they even open the incident. */}
          {!alert.senior_has_family_contact && (
            <span className="badge-alone" title="This senior has no family contact linked">
              Lives alone
            </span>
          )}
          <span className={`type-badge ${CATEGORY_CLASS[category]}`.trim()}>
            {CATEGORY_LABEL[category]}
          </span>
          <StatusPill status={alert.status} />
        </div>
      </div>
    </li>
  )
}

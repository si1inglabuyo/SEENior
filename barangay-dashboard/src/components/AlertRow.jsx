import { triggerLabel, alertCategory, CATEGORY_LABEL } from '../labels'
import { initials, clockTime } from '../format'
import { canActOn } from '../alertActions'
import StatusPill from './StatusPill'

// One incident row, shared by the Alerts queue and Alert History. Identical markup and
// identical action gating on both screens -- `canActOn` mirrors what the backend enforces,
// so a disabled button never fires a confirm dialog just to have the API reject it. The
// only thing that differs between the two screens is which alerts they hand in.
const CATEGORY_CLASS = {
  sos: 'type-badge-sos',
  dispatch_family: 'type-badge-dispatch',
  anomaly: '',
}

export default function AlertRow({ alert, onAskAction, onShowDetails }) {
  const category = alertCategory(alert)

  // Click anywhere on the row opens Details -- except on the action buttons, which run
  // their own handlers. `closest('button')` is what tells the two apart.
  function onRowClick(e) {
    if (!e.target.closest('button')) onShowDetails(alert)
  }

  return (
    <li className="queue-row queue-row-clickable" onClick={onRowClick}>
      <span className="avatar">{initials(alert.senior_name)}</span>
      <div className="queue-row-main">
        <p className="alert-row-name">
          {alert.senior_name}, {alert.senior_age}
        </p>
        <p className="alert-row-reason">{triggerLabel(alert.trigger_type)}</p>
        <p className="alert-row-reason muted">Last Detected: {clockTime(alert.created_at)}</p>
      </div>

      <div className="queue-row-side">
        <div className="queue-row-badges">
          {/* Not a risk level -- CLAUDE.md is explicit this stays amber, never the
              risk-level red, so it can't be mistaken for a fourth severity tier. */}
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

        <div className="queue-row-actions">
          <button
            type="button"
            className="btn-outline"
            disabled={!canActOn('acknowledge', alert.status)}
            onClick={() => onAskAction(alert, 'acknowledge')}
          >
            Acknowledge
          </button>
          <button type="button" className="btn-outline" onClick={() => onShowDetails(alert)}>
            Details
          </button>
          <button
            type="button"
            className="btn-outline"
            disabled={!canActOn('resolve', alert.status)}
            onClick={() => onAskAction(alert, 'resolve')}
          >
            Resolved
          </button>
          <button
            type="button"
            className="btn-outline"
            disabled={!canActOn('falsePositive', alert.status)}
            onClick={() => onAskAction(alert, 'falsePositive')}
          >
            False Positive
          </button>
        </div>
      </div>
    </li>
  )
}

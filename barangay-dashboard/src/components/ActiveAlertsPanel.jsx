import { displayStatus, triggerLabel } from '../labels'
import { initials, clockTime } from '../format'
import { IconWarning, IconEye } from '../icons'
import SectionCard from './SectionCard'
import StatusPill from './StatusPill'

// Preview of open incidents, newest first. Shows the 5 most recent; "View All" opens the
// Alerts tab. The header shows the total count.
const MAX_ROWS = 5

export default function ActiveAlertsPanel({ alerts, onViewAll, onShowDetails }) {
  const shown = alerts.slice(0, MAX_ROWS)
  return (
    <SectionCard
      icon={<IconWarning />}
      title={alerts.length ? `Active Alerts (${alerts.length})` : 'Active Alerts'}
      action={
        <button type="button" className="link-btn" onClick={onViewAll}>
          View All <IconEye />
        </button>
      }
      className="alerts-today"
    >
      {alerts.length === 0 ? (
        <p className="muted alerts-empty">No active alerts.</p>
      ) : (
        <ul className="alert-rows">
          {shown.map((alert) => (
            <li
              className="alert-row alert-row-clickable"
              key={alert.sync_id}
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
              <div className="alert-row-main">
                <p className="alert-row-name">
                  {alert.senior_name}, {alert.senior_age}
                  {/* Not a risk level -- amber, never red (dashboard spec §6). It tells
                      the responder nobody else was notified for this senior. Same badge the
                      Alerts-tab rows carry. */}
                  {!alert.senior_has_family_contact && (
                    <span
                      className="badge-alone"
                      title="This senior has no family contact linked"
                    >
                      Lives alone
                    </span>
                  )}
                </p>
                <p className="alert-row-reason">{triggerLabel(alert.trigger_type)}</p>
              </div>
              <div className="alert-row-end">
                <span className="alert-row-time">{clockTime(alert.created_at)}</span>
                <StatusPill status={displayStatus(alert)} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  )
}

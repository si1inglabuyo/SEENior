import { triggerLabel } from '../labels'
import { initials, clockTime } from '../format'
import { IconWarning, IconEye } from '../icons'
import SectionCard from './SectionCard'
import StatusPill from './StatusPill'

// The feed arrives newest-first (Alert.created_at DESC). Only the 5 most recent are shown
// here -- the "View All" button in the card header goes to the full Alerts tab.
const MAX_ROWS = 5

export default function AlertsTodayPanel({ alerts, onViewAll, onShowDetails }) {
  const shown = alerts.slice(0, MAX_ROWS)
  return (
    <SectionCard
      icon={<IconWarning />}
      title="Alerts Today"
      action={
        <button type="button" className="link-btn" onClick={onViewAll}>
          View All <IconEye />
        </button>
      }
      className="alerts-today"
    >
      {alerts.length === 0 ? (
        <p className="muted alerts-empty">No alerts today.</p>
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
                </p>
                <p className="alert-row-reason">{triggerLabel(alert.trigger_type)}</p>
              </div>
              <div className="alert-row-end">
                <span className="alert-row-time">{clockTime(alert.created_at)}</span>
                <StatusPill status={alert.status} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  )
}

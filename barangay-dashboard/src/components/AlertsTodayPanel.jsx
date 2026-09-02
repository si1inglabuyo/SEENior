import { triggerLabel } from '../labels'
import { initials, clockTime } from '../format'
import { IconWarning, IconEye } from '../icons'
import SectionCard from './SectionCard'
import StatusPill from './StatusPill'

export default function AlertsTodayPanel({ alerts, onViewAll }) {
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
          {alerts.map((alert) => (
            <li className="alert-row" key={alert.sync_id}>
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

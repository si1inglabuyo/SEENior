import { byPriority, dispatchOf, receivedAfterHours } from '../dispatch'
import { IconWarning, IconEye } from '../icons'
import SectionCard from './SectionCard'
import AlertRow from './AlertRow'

// Preview of open incidents on the Dashboard tab. Uses the same row as the Alerts tab, in the
// same priority order, and shows the first 5; "View All" opens the Alerts tab. The header
// shows the total count.
const MAX_ROWS = 5

export default function ActiveAlertsPanel({ alerts, closedDays = [], onViewAll, onShowDetails }) {
  const shown = byPriority(alerts).slice(0, MAX_ROWS)
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
        <ul className="queue-rows history-rows">
          {shown.map((alert) => (
            <AlertRow
              key={alert.sync_id}
              alert={alert}
              onShowDetails={onShowDetails}
              dispatch={dispatchOf(alert)}
              afterHours={receivedAfterHours(alert, closedDays)}
            />
          ))}
        </ul>
      )}
    </SectionCard>
  )
}

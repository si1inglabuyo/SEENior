import { IconChart } from '../icons'
import SectionCard from './SectionCard'
import Donut from './Donut'

// This week's alerts by outcome: still open at the barangay tier, closed as resolved, or
// closed as a false positive. Each slice drills in -- "Active" to the Alerts tab (open work
// isn't in the log), "Resolved" / "False Positive" to Alert History filtered to that status.
const ACTIVE_COLOR = '#c4453c'
const RESOLVED_COLOR = '#5aa666'
const FALSE_COLOR = '#9aa4b2'

export default function OutcomeDonut({ outcomes, onNavigate }) {
  const o = outcomes || {}
  const segments = [
    {
      key: 'active',
      label: 'Active',
      value: (o.escalated || 0) + (o.acknowledged || 0),
      color: ACTIVE_COLOR,
      onClick: () => onNavigate('alerts'),
    },
    {
      key: 'resolved',
      label: 'Resolved',
      value: o.resolved || 0,
      color: RESOLVED_COLOR,
      onClick: () => onNavigate('history', { status: 'resolved', label: 'Resolved alerts' }),
    },
    {
      key: 'false_positive',
      label: 'False Positive',
      value: o.false_positive || 0,
      color: FALSE_COLOR,
      onClick: () =>
        onNavigate('history', { status: 'false_positive', label: 'False Positive alerts' }),
    },
  ]

  return (
    <SectionCard icon={<IconChart />} title="Alerts Outcome">
      <Donut
        segments={segments}
        ariaLabel="Alert outcomes"
        emptyText="No alerts recorded this week."
      />
    </SectionCard>
  )
}

import { IconChart } from '../icons'
import SectionCard from './SectionCard'
import Donut from './Donut'

// This week's alerts by outcome: Active (open, unclaimed), Attending, Resolved and False
// Positive. "Active" and "Attending" open the Alerts tab; the others open Alert History
// filtered by status. `attending` is derived by the stats endpoint (an escalated alert with
// an acknowledged_barangay step), not a stored status; older APIs count it as `escalated`.
const ACTIVE_COLOR = '#c4453c'
const ATTENDING_COLOR = '#e0a03c'
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
      key: 'attending',
      label: 'Attending',
      value: o.attending || 0,
      color: ATTENDING_COLOR,
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

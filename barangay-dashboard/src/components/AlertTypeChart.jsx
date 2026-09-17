import { IconChart } from '../icons'
import { CATEGORY_LABEL } from '../labels'
import SectionCard from './SectionCard'
import Donut from './Donut'

// This week's alerts grouped into the three responder-facing categories. Every slice
// drills into Alert History filtered to that category.
const CATEGORY_ORDER = ['anomaly', 'sos', 'dispatch_family']
const CATEGORY_COLOR = {
  anomaly: '#e08a3c',
  sos: '#a33329',
  dispatch_family: '#4f6b8a',
}

export default function AlertTypeChart({ categories, onSelect }) {
  const c = categories || {}
  const segments = CATEGORY_ORDER.map((key) => ({
    key,
    label: CATEGORY_LABEL[key],
    value: c[key] || 0,
    color: CATEGORY_COLOR[key],
    onClick: () => onSelect(key, CATEGORY_LABEL[key]),
  }))

  return (
    <SectionCard icon={<IconChart />} title="Alerts by Type">
      <Donut
        segments={segments}
        ariaLabel="Alerts by type"
        emptyText="No alerts recorded this week."
      />
    </SectionCard>
  )
}

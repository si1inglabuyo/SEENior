import { IconChart } from '../icons'
import { CATEGORY_LABEL } from '../labels'
import SectionCard from './SectionCard'
import Donut from './Donut'

// This week's alerts by category. Each slice opens Alert History for that category.
const CATEGORY_ORDER = ['anomaly', 'potential_fall', 'sos', 'dispatch_family']
const CATEGORY_COLOR = {
  anomaly: '#e08a3c',
  potential_fall: '#7b52a6',
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

import { statusLabel } from '../labels'

// Short pill wording for alert rows. Callers pass displayStatus(alert) (labels.js), so a
// responder-claimed incident arrives as `attending`; `acknowledged` means the family did.
const PILL = {
  escalated: { text: 'Active', cls: 'pill-active' },
  attending: { text: 'Attending', cls: 'pill-attending' },
  acknowledged: { text: 'Family Handling', cls: 'pill-neutral' },
  resolved: { text: 'Resolved', cls: 'pill-resolved' },
  false_positive: { text: 'False Positive', cls: 'pill-false' },
}

export default function StatusPill({ status }) {
  const pill = PILL[status] || { text: statusLabel(status), cls: 'pill-neutral' }
  return <span className={`pill ${pill.cls}`}>{pill.text}</span>
}

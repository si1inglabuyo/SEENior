import { statusLabel } from '../labels'

// Short pill wording for the dashboard's alert rows -- tighter than labels.js's sentence
// phrasings, which are written for the incident cards where there is room.
const PILL = {
  escalated: { text: 'Active', cls: 'pill-active' },
  acknowledged: { text: 'Attending', cls: 'pill-attending' },
  resolved: { text: 'Resolved', cls: 'pill-resolved' },
  false_positive: { text: 'False alarm', cls: 'pill-false' },
}

export default function StatusPill({ status }) {
  const pill = PILL[status] || { text: statusLabel(status), cls: 'pill-neutral' }
  return <span className={`pill ${pill.cls}`}>{pill.text}</span>
}

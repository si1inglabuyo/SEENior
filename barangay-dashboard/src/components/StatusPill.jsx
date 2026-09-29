import { statusLabel } from '../labels'

// Short pill wording for the dashboard's alert rows -- tighter than labels.js's sentence
// phrasings, which are written for the incident cards where there is room.
//
// Callers pass displayStatus(alert) (labels.js), not alert.status, so a responder-claimed
// incident arrives here as `attending`. `acknowledged` is the *family* having picked it up,
// which is why it no longer shares the "Attending" wording with the responder case.
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

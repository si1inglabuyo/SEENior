import { isAttending } from './labels'

// The three row actions in one table, so ConfirmDialog and the toast stay single components.
export const ALERT_ACTIONS = {
  acknowledge: {
    rowLabel: 'Acknowledge',
    dialogTitle: 'Confirm Acknowledgement',
    dialogMessage: 'Mark this emergency as acknowledged?',
    confirmLabel: 'Acknowledge',
    tone: 'ok',
    successMessage: 'Successfully Marked as Acknowledged',
    // Path segment for PATCH /barangay/alerts/{sync_id}/{endpoint}.
    endpoint: 'acknowledge',
  },
  resolve: {
    rowLabel: 'Resolved',
    dialogTitle: 'Resolved',
    dialogMessage: 'Mark this emergency as resolved?',
    confirmLabel: 'Resolve',
    tone: 'ok',
    successMessage: 'Successfully Marked as Resolved',
    endpoint: 'resolve',
  },
  falsePositive: {
    rowLabel: 'False Positive',
    dialogTitle: 'False Positive Alert',
    dialogMessage: 'Mark this emergency as false positive alert?',
    confirmLabel: 'False Positive',
    tone: 'danger',
    successMessage: 'Successfully Marked as False Positive',
    endpoint: 'false-positive',
  },
}

// Mirrors the backend rules so a disabled button never opens a dialog the API would reject:
// acknowledge only while the incident is open and unclaimed, and no action once it is closed.
export function canActOn(actionKey, alert) {
  const { status } = alert
  if (status === 'resolved' || status === 'false_positive') return false
  if (actionKey === 'acknowledge') return status === 'escalated' && !isAttending(alert)
  return true
}

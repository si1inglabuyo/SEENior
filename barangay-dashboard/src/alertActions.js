// The closing actions in one table, so ConfirmDialog and the toast stay single components.
// There is no Acknowledge button: any dispatch step marks the alert attended (the backend
// endpoint still exists).
export const ALERT_ACTIONS = {
  resolve: {
    rowLabel: 'Resolved',
    dialogTitle: 'Resolved',
    dialogMessage: 'Mark this emergency as resolved?',
    confirmLabel: 'Resolve',
    tone: 'ok',
    successMessage: 'Successfully Marked as Resolved',
    // Path segment for PATCH /barangay/alerts/{sync_id}/{endpoint}.
    endpoint: 'resolve',
  },
  falsePositive: {
    rowLabel: 'False Alert',
    dialogTitle: 'False Alert',
    dialogMessage: 'Mark this emergency as a false alert?',
    confirmLabel: 'False Alert',
    tone: 'danger',
    successMessage: 'Successfully Marked as False Alert',
    endpoint: 'false-positive',
  },
}

// Mirrors the backend rules so a disabled button never opens a dialog the API would reject:
// no action once the incident is closed.
export function canActOn(actionKey, alert) {
  const { status } = alert
  return status !== 'resolved' && status !== 'false_positive'
}

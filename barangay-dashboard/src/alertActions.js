// Everything that varies between the three row actions, in one table, so ConfirmDialog and
// the success toast stay single components instead of three near-identical copies. Wording
// matches design/alert/alert-*-confirm.png and alert-*-success.png exactly.
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

// Mirrors what the backend itself enforces (backend/app/api/routes/barangay.py) so a
// disabled button never triggers a confirm dialog just to have the API reject it:
// acknowledge only makes sense while an incident is open at the barangay tier, and neither
// closing action is available once one of them has already closed it.
export function canActOn(actionKey, status) {
  if (status === 'resolved' || status === 'false_positive') return false
  if (actionKey === 'acknowledge') return status === 'escalated'
  return true
}

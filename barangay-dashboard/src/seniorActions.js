// Actions for the shared ConfirmDialog and Toast on the senior record, same shape as
// alertActions.js. Deactivation is a reversible roster flip: the record stays, but the
// phone keeps monitoring and the senior's alerts still reach the barangay
// (docs/handoff-senior-status.md). Saved via PATCH /seniors/{sync_id}/status.
export const DEACTIVATE_ACTION = {
  dialogTitle: 'Deactivate Account',
  dialogMessage:
    'This senior is removed from your active roster. Their record and alert history are ' +
    'kept, and if their phone raises an alert it will still reach you.',
  confirmLabel: 'Deactivate',
  tone: 'danger',
  successMessage: 'Account Deactivated',
}

export const REACTIVATE_ACTION = {
  dialogTitle: 'Reactivate Account',
  dialogMessage: 'This senior returns to your active roster.',
  confirmLabel: 'Reactivate',
  tone: 'ok',
  successMessage: 'Account Reactivated',
}

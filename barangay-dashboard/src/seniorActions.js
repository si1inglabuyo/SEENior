// Action objects for the shared ConfirmDialog + Toast on the senior record. Same shape as
// alertActions.js (dialogTitle / dialogMessage / confirmLabel / tone / successMessage).
//
// Deactivation is a reversible roster flip, not a deletion and not a switch-off: the senior
// drops off the active roster, the record and history stay, and the account can be switched
// back on. It does NOT stop the phone monitoring or stop that senior's alerts reaching the
// barangay -- the barangay is the last tier of the escalation chain and a roster flag must never
// be able to remove it (docs/handoff-senior-status.md). Saved via PATCH /seniors/{sync_id}/status.
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

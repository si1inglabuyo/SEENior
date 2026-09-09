// Action objects for the shared ConfirmDialog + Toast on the senior record. Same shape as
// alertActions.js (dialogTitle / dialogMessage / confirmLabel / tone / successMessage).
//
// Deactivation is a reversible status flip, not a deletion: a deactivated senior stops
// being monitored and drops off the active roster, but the record and its history stay and
// the account can be switched back on. (When `seniors.status` and the endpoints exist this
// is a PATCH; today it persists to this browser -- see SeniorRoster.)
export const DEACTIVATE_ACTION = {
  dialogTitle: 'Deactivate Account',
  dialogMessage:
    'Monitoring stops and this senior drops off the active roster. The record and alert ' +
    'history are kept, and the account can be reactivated later.',
  confirmLabel: 'Deactivate',
  tone: 'danger',
  successMessage: 'Account Deactivated',
}

export const REACTIVATE_ACTION = {
  dialogTitle: 'Reactivate Account',
  dialogMessage: 'Monitoring resumes and this senior returns to the active roster.',
  confirmLabel: 'Reactivate',
  tone: 'ok',
  successMessage: 'Account Reactivated',
}

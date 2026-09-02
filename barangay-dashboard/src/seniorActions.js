// The one action object the shared ConfirmDialog + Toast need for "Deactivate Account".
// Same shape as the entries in alertActions.js (dialogTitle / dialogMessage / confirmLabel
// / tone / successMessage), so it feeds the exact same components with no new variants.
// Wording matches design/seniors/details-deactivate-confirm.png and -success.png.
export const DEACTIVATE_ACTION = {
  dialogTitle: 'Deactivate Account',
  dialogMessage: 'This will permanently delete the account and all data. This cannot be undone.',
  confirmLabel: 'Deactivate',
  tone: 'danger',
  successMessage: 'Successfully Deactivated the Account',
}

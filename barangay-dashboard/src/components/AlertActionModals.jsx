import { ALERT_ACTIONS } from '../alertActions'
import ConfirmDialog from './ConfirmDialog'
import Toast from './Toast'
import AlertDetailsModal from './AlertDetailsModal'

// Renders the shared confirm dialog, toast and details modal for the current action state.
// Used by both the Alerts page and Alert History.
export default function AlertActionModals({ actions }) {
  const {
    pending,
    busy,
    dialogError,
    toast,
    detailsAlert,
    askAction,
    confirmAction,
    cancelDialog,
    closeToast,
    closeDetails,
  } = actions

    // Details renders first so the confirm dialog and toast (same z-index) stack above it.
  return (
    <>
      {detailsAlert && (
        <AlertDetailsModal
          alert={detailsAlert}
          onClose={closeDetails}
          onAct={askAction}
          actionBusy={busy}
        />
      )}
      {pending && (
        <ConfirmDialog
          action={ALERT_ACTIONS[pending.actionKey]}
          error={dialogError}
          busy={busy}
          onCancel={cancelDialog}
          onConfirm={confirmAction}
        />
      )}
      {toast && <Toast message={toast.message} onClose={closeToast} />}
    </>
  )
}

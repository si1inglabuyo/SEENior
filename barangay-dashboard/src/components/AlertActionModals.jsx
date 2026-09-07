import { ALERT_ACTIONS } from '../alertActions'
import ConfirmDialog from './ConfirmDialog'
import Toast from './Toast'
import AlertDetailsModal from './AlertDetailsModal'

// Renders whichever of the three shared overlays the current action state calls for. The
// Alerts page and Alert History each drop this in once and feed it the object from
// useAlertActions. There is exactly one ConfirmDialog, one Toast and one AlertDetailsModal
// in the app; both screens go through these.
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

  // Details is rendered first so the confirm dialog and toast, which share its z-index,
  // stack on top of it -- the action buttons live inside Details, so its dialog opens over
  // it rather than behind it.
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

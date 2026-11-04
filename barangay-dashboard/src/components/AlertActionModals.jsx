import { ALERT_ACTIONS } from '../alertActions'
import ConfirmDialog from './ConfirmDialog'
import Toast from './Toast'
import AlertDetailsModal from './AlertDetailsModal'

// Renders the shared confirm dialog, toast and details modal for the current action state.
// Used by both the Alerts page and Alert History. `dispatch` is for the Alerts page only: it
// adds the response panel, with `liveAlerts` keeping the open Details on the latest poll.
export default function AlertActionModals({ actions, dispatch, liveAlerts }) {
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

  const shownAlert =
    detailsAlert && (liveAlerts?.find((a) => a.sync_id === detailsAlert.sync_id) ?? detailsAlert)

    // Details renders first so the confirm dialog and toast (same z-index) stack above it.
  return (
    <>
      {shownAlert && (
        <AlertDetailsModal
          alert={shownAlert}
          onClose={closeDetails}
          onAct={askAction}
          actionBusy={busy}
          dispatch={dispatch}
          activeAlerts={liveAlerts}
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

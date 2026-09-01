import { useState } from 'react'
import { api } from '../api'
import { ALERT_ACTIONS } from '../alertActions'

// The whole row-action flow -- pick an action, confirm it, PATCH it, toast, reload -- in
// one place so the Alerts queue and the Alert History page run identical logic through the
// same shared dialog/toast/details components (see AlertActionModals).
//
// `onReload` is whatever refetch the calling screen already does on a poll. The PATCH is a
// real write to /barangay/alerts, so once it lands, every other screen that reads the same
// rows -- the Dashboard's Active Alerts / Resolved Today counts, the Alerts Outcome donut,
// the Alerts Today panel -- picks the change up on its own next poll. Nothing here is
// local-only optimism; the row the caller sees after `onReload()` is the server's.
export function useAlertActions({ onReload, onSessionLost }) {
  const [pending, setPending] = useState(null) // { alert, actionKey }
  const [busy, setBusy] = useState(false)
  const [dialogError, setDialogError] = useState('')
  const [toast, setToast] = useState(null) // { message }
  const [detailsAlert, setDetailsAlert] = useState(null)

  function askAction(alert, actionKey) {
    setDialogError('')
    setPending({ alert, actionKey })
  }

  async function confirmAction() {
    if (!pending) return
    const { alert, actionKey } = pending
    const action = ALERT_ACTIONS[actionKey]
    setBusy(true)
    try {
      await api(`/barangay/alerts/${alert.sync_id}/${action.endpoint}`, {
        method: 'PATCH',
        body: JSON.stringify({ notes: null }),
      })
      setBusy(false)
      setPending(null)
      setToast({ message: action.successMessage })
      await onReload() // refetch -- show the server's row, not a guessed one
    } catch (err) {
      setBusy(false)
      setDialogError(err.message)
      if (err.message.includes('expired')) onSessionLost()
    }
  }

  return {
    pending,
    busy,
    dialogError,
    toast,
    detailsAlert,
    askAction,
    showDetails: setDetailsAlert,
    confirmAction,
    cancelDialog: () => setPending(null),
    closeToast: () => setToast(null),
    closeDetails: () => setDetailsAlert(null),
  }
}

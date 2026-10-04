import { useState } from 'react'
import { api } from '../api'
import { ALERT_ACTIONS } from '../alertActions'

// The row-action flow (pick, confirm, PATCH, toast, reload) in one place, shared by the
// Alerts queue and Alert History. `onReload` is the caller's refetch. The PATCH writes to
// /barangay/alerts, so other screens pick it up on their next poll.
export function useAlertActions({ onReload, onSessionLost }) {
  const [pending, setPending] = useState(null) // { alert, actionKey }
  const [busy, setBusy] = useState(false)
  const [dialogError, setDialogError] = useState('')
  const [toast, setToast] = useState(null) // { message }
  const [detailsAlert, setDetailsAlert] = useState(null)

    // Optional remarks from the Details modal. Blank collapses to null.
  function askAction(alert, actionKey, notes = null) {
    setDialogError('')
    const trimmed = typeof notes === 'string' ? notes.trim() : ''
    setPending({ alert, actionKey, notes: trimmed || null })
  }

  async function confirmAction() {
    if (!pending) return
    const { alert, actionKey, notes } = pending
    const action = ALERT_ACTIONS[actionKey]
    setBusy(true)
    try {
      await api(`/barangay/alerts/${alert.sync_id}/${action.endpoint}`, {
        method: 'PATCH',
        body: JSON.stringify({ notes }),
      })
      setBusy(false)
      setPending(null)
      setDetailsAlert(null) // the action was taken from inside Details -- close it too
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

import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'

// The pook list and the dispatch actions. `pooks` stays null until the API answers, and also
// if it has no pooks route yet; screens show no pook UI in that case, so the dashboard works
// exactly as it did until the backend side is deployed.
export function useDispatch({ onReload, onSessionLost }) {
  const [pooks, setPooks] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const loadPooks = useCallback(async () => {
    try {
      setPooks(await api('/barangay/pooks'))
    } catch (err) {
      setPooks(null)
      if (err.message.includes('expired')) onSessionLost()
    }
  }, [onSessionLost])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadPooks()
  }, [loadPooks])

  // Each action becomes one step on the alert's timeline. Reloads either way, so a refused
  // action still shows the server's current state.
  async function send(alert, action) {
    setBusy(true)
    setError('')
    try {
      await api(`/barangay/alerts/${alert.sync_id}/dispatch`, {
        method: 'POST',
        body: JSON.stringify(action),
      })
    } catch (err) {
      setError(err.message)
      if (err.message.includes('expired')) onSessionLost()
    }
    await onReload()
    setBusy(false)
  }

  return { pooks, busy, error, send, clearError: () => setError('') }
}

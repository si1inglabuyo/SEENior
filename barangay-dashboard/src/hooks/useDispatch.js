import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'

// The pook list and the dispatch actions. `pooks` stays null until the API answers, and also
// if it has no pooks route yet; screens show no pook UI in that case, so the dashboard works
// exactly as it did until the backend side is deployed.
export function useDispatch({ onReload, onSessionLost }) {
  const [pooks, setPooks] = useState(null)
  // The emergency hotlines the apps show after hours, reused here as the outside-help list.
  const [hotlines, setHotlines] = useState([])
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

  useEffect(() => {
    api('/config/after-hours')
      .then((config) => setHotlines(Array.isArray(config?.hotlines) ? config.hotlines : []))
      .catch(() => setHotlines([]))
  }, [])

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

  // Set or clear one pook's designated responder. Returns why the server refused it, or ''
  // when it was saved, and swaps the saved pook into the list so every screen shows it.
  async function savePookResponder(pookId, name, phone) {
    try {
      const saved = await api(`/barangay/pooks/${pookId}/responder`, {
        method: 'PUT',
        body: JSON.stringify({ responder_name: name, responder_phone: phone }),
      })
      setPooks((current) => current && current.map((p) => (p.id === saved.id ? saved : p)))
      return ''
    } catch (err) {
      if (err.message.includes('expired')) onSessionLost()
      return err.message
    }
  }

  return { pooks, hotlines, busy, error, send, savePookResponder, clearError: () => setError('') }
}

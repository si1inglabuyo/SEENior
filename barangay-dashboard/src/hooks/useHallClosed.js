import { useCallback, useEffect, useState } from 'react'
import { api, POLL_MS } from '../api'
import { manilaDay } from '../dispatch'

// The "Hall closed today" switch, for holidays and other closures on a weekday. The days live on
// the server (GET/PUT/DELETE /config/hall-closed-days), so every operator and both mobile apps
// see the same answer. A marked day lapses by itself: the server only returns today onward.
export function useHallClosed() {
  const [closedDays, setClosedDays] = useState([])

  const load = useCallback(async () => {
    try {
      const data = await api('/config/hall-closed-days')
      setClosedDays(data.closed_days)
    } catch {
      /* keep the last list; the next poll tries again */
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load()
    const timer = setInterval(load, POLL_MS)
    return () => clearInterval(timer)
  }, [load])

  async function setClosedToday(closed) {
    const today = manilaDay(Date.now())
    // Show the change at once; the server's answer replaces it, or the old list returns on failure.
    const before = closedDays
    setClosedDays(closed ? [...before.filter((d) => d !== today), today] : before.filter((d) => d !== today))
    try {
      const data = await api(`/config/hall-closed-days/${today}`, { method: closed ? 'PUT' : 'DELETE' })
      setClosedDays(data.closed_days)
    } catch {
      setClosedDays(before)
    }
  }

  return { closedDays, setClosedToday }
}

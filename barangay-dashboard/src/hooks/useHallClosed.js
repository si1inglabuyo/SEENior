import { useState } from 'react'
import { manilaDay } from '../dispatch'

const KEY = 'seenior.hallClosedDays'

function loadDays() {
  try {
    const days = JSON.parse(localStorage.getItem(KEY) || '[]')
    return Array.isArray(days) ? days : []
  } catch {
    return []
  }
}

// The "Hall closed today" switch, for holidays and other closures on a weekday. Each marked day
// lapses by itself, so nobody has to turn it off. Kept in this browser until the cloud has
// somewhere to hold it, which needs a table from the main lane.
export function useHallClosed() {
  const [closedDays, setClosedDays] = useState(loadDays)

  function setClosedToday(closed) {
    const today = manilaDay(Date.now())
    const next = closedDays.filter((day) => day !== today)
    if (closed) next.push(today)
    setClosedDays(next)
    try {
      localStorage.setItem(KEY, JSON.stringify(next))
    } catch {
      /* ignore -- the in-memory value still applies for this session */
    }
  }

  return { closedDays, setClosedToday }
}

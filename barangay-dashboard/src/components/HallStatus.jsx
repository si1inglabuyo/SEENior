import { useEffect, useState } from 'react'
import { HALL_CLOSE_HOUR, HALL_OPEN_HOUR, hallOpenAt, manilaDay } from '../dispatch'

const hour12 = (hour) => `${hour % 12 || 12}:00 ${hour < 12 ? 'AM' : 'PM'}`

// Whether the hall is on duty, and the switch for a holiday or other closure on a weekday.
export default function HallStatus({ closedDays, onSetClosedToday }) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30000)
    return () => clearInterval(timer)
  }, [])

  const open = hallOpenAt(now, closedDays)
  const closedToday = closedDays.includes(manilaDay(now))
  return (
    <div className="hall-status">
      <p className={open ? 'hall-status-text tone-green' : 'hall-status-text tone-amber'}>
        {open
          ? `Hall open until ${hour12(HALL_CLOSE_HOUR)}`
          : `Hall closed. Hours: Monday to Friday, ${hour12(HALL_OPEN_HOUR)} to ${hour12(HALL_CLOSE_HOUR)}`}
      </p>
      <label className="hall-status-switch">
        <input
          type="checkbox"
          checked={closedToday}
          onChange={(e) => onSetClosedToday(e.target.checked)}
        />
        Hall closed today
      </label>
    </div>
  )
}

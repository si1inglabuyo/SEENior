import { useState } from 'react'
import { IconCalendar } from '../icons'
import { manilaDay } from '../dispatch'
import SectionCard from './SectionCard'

// Plain flex bars, no chart library. Each column opens Alert History for that day. It uses
// div + role="button" because a <button> is unreliable as a flex container for the bar.
export default function WeeklyBarChart({ days, onSelectDay }) {
  const peak = Math.max(1, ...days.map((d) => d.count))
  // Read once when the chart mounts; the dashboard remounts it on each visit.
  const [today] = useState(() => manilaDay(Date.now()))

  return (
    <SectionCard icon={<IconCalendar />} title="Alerts This Week">
      <div className="bars">
        {days.map((day) => (
          <div
            className={day.day === today ? 'bar-col is-today' : 'bar-col'}
            key={day.day}
            role="button"
            tabIndex={0}
            onClick={() => onSelectDay(day.day)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                onSelectDay(day.day)
              }
            }}
            title={`${day.count} alert${day.count === 1 ? '' : 's'} — view`}
          >
            <div className="bar-track">
              <div
                className="bar"
                style={{ height: `${(day.count / peak) * 100}%` }}
              />
            </div>
            <span className="bar-label">
              {new Date(`${day.day}T00:00:00`)
                .toLocaleDateString(undefined, { weekday: 'short' })
                .toUpperCase()}
            </span>
          </div>
        ))}
      </div>
    </SectionCard>
  )
}

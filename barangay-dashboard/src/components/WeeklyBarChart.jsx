import { IconCalendar } from '../icons'
import SectionCard from './SectionCard'

// Plain flex bars, no chart library -- same approach the old Analytics panel used, kept
// for the same reason: one less dependency to explain or have break at a defense. Each
// column is clickable (opens Alert History filtered to that day, all statuses). It uses
// the div + role="button" pattern the alert/senior rows use rather than a real <button>:
// a <button> is an unreliable flex container for the percentage-height bar inside it.
export default function WeeklyBarChart({ days, onSelectDay }) {
  const peak = Math.max(1, ...days.map((d) => d.count))

  return (
    <SectionCard icon={<IconCalendar />} title="Alerts This Week">
      <div className="bars">
        {days.map((day) => (
          <div
            className="bar-col"
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

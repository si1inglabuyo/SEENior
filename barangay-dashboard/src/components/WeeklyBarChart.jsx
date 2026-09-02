import { IconCalendar } from '../icons'
import SectionCard from './SectionCard'

// Plain flex bars, no chart library -- same approach the old Analytics panel used, kept
// for the same reason: one less dependency to explain or have break at a defense.
export default function WeeklyBarChart({ days }) {
  const peak = Math.max(1, ...days.map((d) => d.count))

  return (
    <SectionCard icon={<IconCalendar />} title="Alerts This Week">
      <div className="bars">
        {days.map((day) => (
          <div className="bar-col" key={day.day}>
            <div className="bar-track">
              <div
                className="bar"
                style={{ height: `${(day.count / peak) * 100}%` }}
                title={`${day.count} alert${day.count === 1 ? '' : 's'}`}
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

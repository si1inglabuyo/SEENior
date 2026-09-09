import { parseServerTime, timeAgo } from '../api'

// Health of the senior's phone *as a monitoring device* -- whether the thing that is
// supposed to be watching them is still checking in. This is device telemetry, not
// behaviour and not location (BarangaySeniorOut: "Device health, not behaviour"), and a
// phone that has gone dark is itself a monitoring failure the barangay needs to see.
//
// Thresholds: the sensor service checks in every ~5 minutes, so a gap of hours is already
// abnormal and a gap of a day means the senior is effectively unmonitored.
const SIX_HOURS = 6 * 60 * 60 * 1000
const ONE_DAY = 24 * 60 * 60 * 1000

function deviceHealth(senior) {
  // Old backend that doesn't send any device field yet -- say nothing rather than claim the
  // phone is dead.
  const hasTelemetry =
    senior &&
    ('last_seen_at' in senior || 'battery_percent' in senior || 'is_charging' in senior)
  if (!hasTelemetry) return null

  const seen = parseServerTime(senior?.last_seen_at)
  const battery = typeof senior?.battery_percent === 'number' ? senior.battery_percent : null
  const charging = senior?.is_charging === true

  if (!seen) return { tone: 'stale', label: 'Phone never checked in' }

  const gap = Date.now() - seen.getTime()
  if (gap >= ONE_DAY) return { tone: 'stale', label: `Phone offline · ${timeAgo(senior.last_seen_at)}` }
  if (gap >= SIX_HOURS) return { tone: 'warn', label: `No check-in for ${timeAgo(senior.last_seen_at)}` }
  if (battery !== null && battery <= 15 && !charging) {
    return { tone: 'warn', label: `Low battery · ${battery}%` }
  }

  let label = 'Phone active'
  if (battery !== null) label += charging ? ` · charging ${battery}%` : ` · ${battery}%`
  return { tone: 'ok', label }
}

export default function DeviceBadge({ senior, className = '' }) {
  const health = deviceHealth(senior)
  if (!health) return null
  return (
    <span
      className={`device-badge device-badge-${health.tone} ${className}`.trim()}
      title="Monitoring device status"
    >
      {health.label}
    </span>
  )
}

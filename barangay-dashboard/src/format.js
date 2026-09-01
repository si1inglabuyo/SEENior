// Small display helpers shared by every screen that renders an alert row (the Dashboard's
// Alerts Today panel and the Alerts page). One place for "how do we shorten a name to an
// avatar" and "how do we print a clock time" so the two screens can't drift apart.
import { parseServerTime } from './api'

export function initials(name) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

export function clockTime(value) {
  const date = parseServerTime(value)
  return date
    ? date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
    : '—'
}

// Full date + time for the alert Details modal and the per-senior alert history, e.g.
// "June 4, 2026 · 4:40 PM". A bare clock time ("4:40 PM") is ambiguous the moment an
// incident is more than a day old.
export function dateTimeLabel(value) {
  const date = parseServerTime(value)
  if (!date) return '—'
  const day = date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })
  const time = date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
  return `${day} · ${time}`
}

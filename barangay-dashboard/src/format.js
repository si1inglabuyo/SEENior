// Small display helpers shared by the screens that render alert rows (avatar initials,
// clock times).
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

// Masks the middle digits of a phone number for the default view, e.g. "0994 555 1234" ->
// "0994XXXX234". The full number needs an explicit "Reveal", which is audited (src/audit.js).
export function maskPhone(value) {
  if (!value) return '—'
  const raw = String(value).trim()
  const digits = raw.replace(/\D/g, '')
  if (digits.length < 8) return raw // too short to mask without hiding all of it
  return `${digits.slice(0, 4)}${'X'.repeat(digits.length - 7)}${digits.slice(-3)}`
}

// Full date and time for the Details modal and per-senior history, e.g. "June 4, 2026 · 4:40 PM".
export function dateTimeLabel(value) {
  const date = parseServerTime(value)
  if (!date) return '—'
  const day = date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })
  const time = date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
  return `${day} · ${time}`
}

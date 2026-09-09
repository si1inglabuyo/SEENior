// Pure filter logic for the Alert History page. Kept dependency-free (a Date goes into
// withinRange, not a raw server string) so the combining rules can be reasoned about and
// tested on their own, away from React and the API client.

export const TYPE_OPTIONS = ['anomaly', 'sos', 'dispatch_family']
export const DATE_LABELS = {
  today: 'Today',
  yesterday: 'Yesterday',
  week: 'This week',
  month: 'This month',
}

// The Dashboard's stat cards navigate here carrying a { when, trigger_type, label } object
// (App.jsx's `navigate`). Translate it into this page's own filter state so the list opens
// already narrowed to what the responder clicked:
//   Resolved Today -> Date Range = Today
//   SOS Triggered  -> Alert Type = SOS + Date Range = Today
// (The "Active Alerts" card goes to the Alerts tab instead -- Alert History is closed
// incidents only.)
export function initialFilters(navFilter) {
  const f = { alertType: 'all', dateRange: null }
  if (!navFilter) return f
  if (navFilter.when === 'today') f.dateRange = { kind: 'today' }
  if (navFilter.trigger_type === 'sos') f.alertType = 'sos'
  return f
}

// An alert still open at the barangay tier vs. one that's been closed. Alert History (and
// the `history` API scope) shows only the closed ones; the Details modal uses this to
// decide whether to show operational fields like Last Known Location.
export function isActiveAlert(alert) {
  return alert.status === 'escalated' || alert.status === 'acknowledged'
}

// `date` is the alert's created_at already parsed to a real instant. Bounds are reckoned
// in the viewer's local calendar, matching how the rest of the dashboard treats "today".
// `end` is exclusive; a custom range includes the whole of its end date.
export function withinRange(date, range) {
  if (!range) return true
  if (!date) return false
  const now = new Date()
  const midnightToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const DAY = 86400000
  let start
  let end
  if (range.kind === 'today') {
    start = midnightToday
    end = new Date(midnightToday.getTime() + DAY)
  } else if (range.kind === 'yesterday') {
    start = new Date(midnightToday.getTime() - DAY)
    end = midnightToday
  } else if (range.kind === 'week') {
    start = new Date(midnightToday.getTime() - 6 * DAY)
    end = new Date(midnightToday.getTime() + DAY)
  } else if (range.kind === 'month') {
    start = new Date(now.getFullYear(), now.getMonth(), 1)
    end = new Date(midnightToday.getTime() + DAY)
  } else if (range.kind === 'custom') {
    if (!range.start || !range.end) return true
    start = new Date(`${range.start}T00:00:00`)
    end = new Date(`${range.end}T23:59:59.999`)
  } else {
    return true
  }
  return date >= start && date < end
}

export function matchesSearch(alert, query) {
  const needle = query.trim().toLowerCase()
  if (!needle) return true
  return (
    alert.senior_name.toLowerCase().includes(needle) ||
    String(alert.senior_age).includes(needle)
  )
}

export function dateRangeLabel(range) {
  if (!range) return 'Date Range'
  if (range.kind === 'custom') return `${range.start} → ${range.end}`
  return DATE_LABELS[range.kind] || 'Date Range'
}

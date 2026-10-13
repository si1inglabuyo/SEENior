// Pure filter logic for the Seniors list, kept free of React so it is easy to test.

export const SENIOR_STATUS_OPTIONS = ['active', 'deactivated']

export const SENIOR_STATUS_LABEL = {
  active: 'Active',
  deactivated: 'Deactivated',
}

// `isDeactivated` is the per-senior flag (see the deactivate store in SeniorRoster).
export function matchesSeniorStatus(isDeactivated, filter) {
  if (filter === 'active') return !isDeactivated
  if (filter === 'deactivated') return isDeactivated
  return true // 'all'
}

export function matchesSeniorSearch(senior, query) {
  const needle = query.trim().toLowerCase()
  if (!needle) return true
  const name = `${senior.first_name} ${senior.last_name}`.toLowerCase()
  return (
    name.includes(needle) ||
    String(senior.age).includes(needle) ||
    (senior.pook || '').toLowerCase().includes(needle)
  )
}

// Pook filter values: 'all', a pook name, or NO_POOK for seniors who never chose one.
export const NO_POOK = '__none__'

export function matchesSeniorPook(senior, filter) {
  if (filter === 'all') return true
  if (filter === NO_POOK) return !senior.pook
  return senior.pook === filter
}

// The pook names present on this roster, in the order they sort, for the filter menu.
export function pookOptionsFor(seniors) {
  return [...new Set(seniors.map((s) => s.pook).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b)
  )
}

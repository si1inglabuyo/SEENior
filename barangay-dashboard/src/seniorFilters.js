// Pure filter logic for the Seniors list. Kept dependency-free and separate from React so
// the status + search combining can be reasoned about and tested on its own.

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
  return name.includes(needle) || String(senior.age).includes(needle)
}

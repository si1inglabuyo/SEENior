// Everything that talks to the SEENior API, so no screen deals with tokens or URLs.

const BASE = import.meta.env.VITE_API_BASE ?? 'https://seenior.onrender.com'
const TOKEN_KEY = 'seenior.responder.token'

// Shared poll interval, so screens notice changes made elsewhere. Also keeps the Render
// instance awake, which keeps the escalation clock running.
export const POLL_MS = 10000

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

// The responder's user id from the JWT `sub` claim. Used only for the access audit log,
// never for access decisions. Returns null on a bad token instead of throwing.
export function currentUserId() {
  const token = getToken()
  if (!token) return null
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(part)).sub ?? null
  } catch {
    return null
  }
}

export async function login(username, password) {
    // /auth/login takes form encoding, not JSON. The field is "username"; responders type
    // their pre-assigned username.
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password }),
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.detail || 'Could not sign in')
  localStorage.setItem(TOKEN_KEY, data.access_token)
  return data.access_token
}

export async function api(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      ...(options.headers || {}),
    },
  })
  if (res.status === 401) {
    clearToken()
    throw new Error('Your session has expired. Please sign in again.')
  }
  const data = res.status === 204 ? null : await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(errorMessage(data, res.status))
  return data
}

// FastAPI sends 422 `detail` as an array of objects; flatten it into readable text.
function errorMessage(data, status) {
  const detail = data && data.detail
  if (typeof detail === 'string') return detail
  if (Array.isArray(detail)) {
    const msg = detail.map((d) => d && d.msg).filter(Boolean).join('; ')
    if (msg) return msg
  }
  return `Request failed (${status})`
}

export function parseServerTime(value) {
    // The API sends naive UTC with no zone marker, which JS would read as local time.
    // Appending Z fixes that.
  if (!value) return null
  return new Date(/(Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)
}

export function timeAgo(value) {
  const date = parseServerTime(value)
  if (!date) return 'never'
  const seconds = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000))
  if (seconds < 60) return `${seconds}s ago`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} hr ago`
  return `${Math.floor(seconds / 86400)} d ago`
}

export function formatTime(value) {
  const date = parseServerTime(value)
  return date ? date.toLocaleString() : '—'
}

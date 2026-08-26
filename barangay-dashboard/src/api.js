// Everything that talks to the SEENior API lives here, so no screen has to know about
// tokens or URLs.

const BASE = import.meta.env.VITE_API_BASE ?? 'https://seenior.onrender.com'
const TOKEN_KEY = 'seenior.responder.token'

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

export async function login(username, password) {
  // /auth/login speaks OAuth2's form encoding, not JSON. The field is named "username"
  // by that spec; family accounts put an email in it, but a barangay responder types
  // their pre-assigned username (CLAUDE.md §2 and §14).
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
  if (!res.ok) throw new Error((data && data.detail) || `Request failed (${res.status})`)
  return data
}

export function parseServerTime(value) {
  // The API returns naive UTC with no zone marker ("2026-08-26T14:03:21.994").
  // JavaScript reads a bare timestamp like that as LOCAL time, which in Manila would make
  // every incident look eight hours old the instant it is raised. Appending Z tells it
  // what the server actually meant. Same trap parseServerTime solves in the Android app.
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

// Turns a decoded GPS fix into a short, human-readable place name (e.g. "Central Village,
// Pasig") via Nominatim's reverse endpoint -- OpenStreetMap data, the same source the alert
// map tiles already come from, so this doesn't hand the fix to a new third party (root
// CLAUDE.md §11 covers the fix itself; the map embed already discloses it to OSM's tile
// servers). Best-effort only: on any failure we return null and the caller falls back to
// showing raw coordinates, which is still a usable location for a responder.
const cache = new Map()

function shortLabel(address) {
  if (!address) return null
  const area = address.neighbourhood || address.suburb || address.village || address.town
  const city =
    address.city || address.municipality || address.town || address.city_district || address.county
  const parts = [area, city].filter(Boolean)
  return parts.length ? parts.join(', ') : null
}

export async function reverseGeocode(lat, lon) {
  const key = `${lat.toFixed(4)},${lon.toFixed(4)}`
  if (cache.has(key)) return cache.get(key)

  const promise = fetch(
    `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lon}&zoom=16&addressdetails=1&accept-language=en`,
    { headers: { Accept: 'application/json' } }
  )
    .then((res) => (res.ok ? res.json() : null))
    .then((data) => shortLabel(data && data.address) || (data && data.name) || null)
    .catch(() => null)

  cache.set(key, promise)
  return promise
}

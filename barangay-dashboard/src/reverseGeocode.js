// Turns a decoded GPS fix into a place name (e.g. "Zuzuarregui St, Central Village, Pasig")
// using Nominatim reverse geocoding. Best effort: on any failure it returns null and the
// caller shows the registered address.
//
// What leaves the browser: coordinates are rounded to 4 decimals (~11 m) before the request,
// since a street name doesn't need more. Nominatim allows one request a second; this runs
// once per opened alert and successful answers are cached for the session.
const cache = new Map()

function placeLabel(address) {
  if (!address) return null
  const street = [address.house_number, address.road].filter(Boolean).join(' ')
  const area = address.neighbourhood || address.quarter || address.suburb || address.village
  const city =
    address.city || address.municipality || address.town || address.city_district || address.county
  const parts = [street, area, city].filter(Boolean)
  return parts.length ? [...new Set(parts)].join(', ') : null
}

export async function reverseGeocode(lat, lon) {
  const latR = lat.toFixed(4)
  const lonR = lon.toFixed(4)
  const key = `${latR},${lonR}`
  if (cache.has(key)) return cache.get(key)

  const promise = fetch(
    `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latR}&lon=${lonR}&zoom=18&addressdetails=1&accept-language=en`,
    { headers: { Accept: 'application/json' } }
  )
    .then((res) => (res.ok ? res.json() : null))
    .then((data) => placeLabel(data && data.address) || (data && data.name) || null)
    .catch(() => null)

  cache.set(key, promise)
    // Don't cache a failure, or a dropped connection would blank this place for the session.
  promise.then((label) => {
    if (label === null) cache.delete(key)
  })
  return promise
}

// Turns a decoded GPS fix into a human-readable place name (e.g. "Zuzuarregui St, Central
// Village, Pasig") via Nominatim's reverse endpoint -- OpenStreetMap data, the same source the
// alert map tiles already come from. Best-effort only: on any failure we return null and the
// caller shows the registered address on its own, which is still usable for a responder.
//
// Two things worth knowing about what leaves the browser (root CLAUDE.md §11):
//  * The coordinates are rounded to 4 decimal places (~11 m) BEFORE the request. The stored fix
//    is a ~5 m cell; a street name does not need it, and a third-party geocoder has no reason to
//    receive more precision than it can use. The pin on the map still shows the exact cell.
//  * Nominatim's usage policy is one request a second at most and no bulk use. This is called
//    once per alert a responder opens, and a successful answer is cached for the session.
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
  // A failure must not be remembered: a dropped connection would otherwise blank this place
  // for the rest of the session even after the network came back.
  promise.then((label) => {
    if (label === null) cache.delete(key)
  })
  return promise
}

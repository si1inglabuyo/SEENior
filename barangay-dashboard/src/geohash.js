// Standard geohash decode (base32, no a/i/l/o). Length-agnostic: a precision-9 cell is
// ~5 m, a precision-7 cell ~150 m -- older alerts carry the wider ones (root CLAUDE.md §11).
// Returns the cell centre and its half-width in degrees, or null for anything that isn't a
// valid geohash. The Android app's Geohash.kt is the reference implementation.
const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz'

export function decodeGeohash(hash) {
  if (typeof hash !== 'string' || hash.length === 0) return null

  const latRange = [-90, 90]
  const lonRange = [-180, 180]
  let evenBit = true // geohash starts on longitude

  for (const rawChar of hash.toLowerCase()) {
    const idx = BASE32.indexOf(rawChar)
    if (idx === -1) return null
    for (let bit = 4; bit >= 0; bit -= 1) {
      const bitValue = (idx >> bit) & 1
      const range = evenBit ? lonRange : latRange
      const mid = (range[0] + range[1]) / 2
      if (bitValue === 1) range[0] = mid
      else range[1] = mid
      evenBit = !evenBit
    }
  }

  return {
    lat: (latRange[0] + latRange[1]) / 2,
    lon: (lonRange[0] + lonRange[1]) / 2,
    latErr: (latRange[1] - latRange[0]) / 2,
    lonErr: (lonRange[1] - lonRange[0]) / 2,
  }
}

// Rough metres across the cell at this latitude, for choosing "pin" vs "approximate area"
// wording. 1 deg latitude ~= 111 km.
export function cellSizeMeters({ lat, latErr, lonErr }) {
  const latM = latErr * 2 * 111_320
  const lonM = lonErr * 2 * 111_320 * Math.cos((lat * Math.PI) / 180)
  return Math.max(latM, lonM)
}

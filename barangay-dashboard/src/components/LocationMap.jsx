import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

// Vite fingerprints leaflet's own default-icon URLs into paths the bundle doesn't serve;
// pointing them at the bundled copies is the standard fix.
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({ iconRetinaUrl: markerIcon2x, iconUrl: markerIcon, shadowUrl: markerShadow })

// The web equivalent of the family app's AlertLocationMap.kt (osmdroid): same OpenStreetMap
// tiles, same pin-vs-square rule read off the cell's own precision rather than assumed, same
// thresholds. A live Leaflet map, not a static openstreetmap.org iframe embed -- pannable and
// zoomable like the app's own map, and not dependent on that site's embed page staying up.
const PIN_THRESHOLD_METRES = 30
const POSITION_ZOOM = 18
const AREA_ZOOM = 17

export default function LocationMap({ cell, metres }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)

  useEffect(() => {
    if (!containerRef.current || !cell) return

    const map = L.map(containerRef.current, {
      zoomControl: false,
      // OSM's tile usage policy requires attribution to stay on the map -- dropping the
      // "Leaflet" branding prefix is fine (that part isn't the tile provider's requirement),
      // but the OpenStreetMap credit itself has to stay, just styled small (see index.css).
      attributionControl: { prefix: false },
    })
    mapRef.current = map

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map)

    const precise = metres <= PIN_THRESHOLD_METRES
    const centre = [cell.lat, cell.lon]

    if (precise) {
      L.marker(centre).addTo(map)
    } else {
      const bounds = [
        [cell.lat - cell.latErr, cell.lon - cell.lonErr],
        [cell.lat + cell.latErr, cell.lon + cell.lonErr],
      ]
      L.rectangle(bounds, {
        color: 'rgb(217, 83, 79)',
        weight: 3,
        fillColor: 'rgb(217, 83, 79)',
        fillOpacity: 0.22,
      }).addTo(map)
    }

    map.setView(centre, precise ? POSITION_ZOOM : AREA_ZOOM)

    return () => {
      map.remove()
      mapRef.current = null
    }
    // Deliberately depend on the cell's fields, not the cell object -- decodeGeohash() returns
    // a fresh object every render, so depending on `cell` itself would tear down and rebuild
    // the map every render instead of only when the actual value changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cell?.lat, cell?.lon, cell?.latErr, cell?.lonErr, metres])

  return <div ref={containerRef} className="location-map" />
}

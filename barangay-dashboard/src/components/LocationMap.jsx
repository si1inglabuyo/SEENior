import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

// Vite renames Leaflet's default icon URLs to paths it doesn't serve; point them at the
// bundled copies.
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({ iconRetinaUrl: markerIcon2x, iconUrl: markerIcon, shadowUrl: markerShadow })

// Web version of the family app's AlertLocationMap: OpenStreetMap tiles, and a pin or a
// square depending on the cell's precision. A live Leaflet map, not an iframe embed.
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
        // OSM requires attribution to stay. Only the Leaflet prefix is dropped.
      attributionControl: { prefix: false },
    })
    mapRef.current = map

    L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}', {
      maxZoom: 19,
      attribution: 'Tiles &copy; Esri &mdash; Sources: Esri, HERE, Garmin, OpenStreetMap contributors',
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
      // Depend on the cell's fields, not the object, since decodeGeohash() returns a new
      // object every render and would rebuild the map each time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cell?.lat, cell?.lon, cell?.latErr, cell?.lonErr, metres])

  return <div ref={containerRef} className="location-map" />
}

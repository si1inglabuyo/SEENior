import { useEffect, useState } from 'react'
import { triggerLabel, stepLabel } from '../labels'
import { reverseGeocode } from '../reverseGeocode'
import { initials, dateTimeLabel } from '../format'
import { canActOn } from '../alertActions'
import { isActiveAlert } from '../historyFilters'
import { decodeGeohash, cellSizeMeters } from '../geohash'
import Modal from './Modal'
import LocationMap from './LocationMap'

// "Last Known Location" -- the senior's actual position at the moment the alert fired,
// captured once (never continuously) and stored as a precision geohash. Since 2026-08-31
// this is a precise fix, held lawfully under RA 10173 §12(c) during an active emergency,
// NOT an anonymised cluster (root CLAUDE.md §11 -- do not call it anonymous). Rendered on a
// live OpenStreetMap tile via LocationMap -- the same osmdroid-equivalent pin-vs-square map
// the family app uses, not a static openstreetmap.org iframe embed -- with the registered
// street address kept alongside because the responder still needs a name to read out. Older
// alerts carry a ~150 m cell; we say "approximate area" for those.
// The place name under the pin, looked up once per fix. The answer is stored with the key it
// was fetched for and only used while that key is still current, so opening a different alert
// never briefly shows the previous alert's street.
function usePlaceName(cell) {
  const lat = cell ? cell.lat : null
  const lon = cell ? cell.lon : null
  const key = lat === null ? null : `${lat},${lon}`
  const [found, setFound] = useState({ key: null, name: null })

  useEffect(() => {
    if (key === null) return undefined
    let cancelled = false
    reverseGeocode(lat, lon).then((name) => {
      if (!cancelled) setFound({ key, name })
    })
    return () => {
      cancelled = true
    }
  }, [key, lat, lon])

  return found.key === key ? found.name : null
}

// Google Maps directions to the senior, the web counterpart of the family app's "Navigate to her
// location" button. Only the destination is given, so Maps starts from wherever the responder
// is. The captured fix wins; with none, the registered address is what there is to go on, which
// is also what the family app falls back to. Opens in the responder's own browser -- nothing
// here is sent by this app to anyone.
function navigateUrl(cell, address) {
  const base = 'https://www.google.com/maps/dir/?api=1&destination='
  if (cell) return `${base}${cell.lat.toFixed(6)},${cell.lon.toFixed(6)}`
  if (address) return `${base}${encodeURIComponent(address)}`
  return null
}

function NavigateButton({ href }) {
  if (!href) return null
  return (
    <a className="btn-primary location-navigate" href={href} target="_blank" rel="noopener noreferrer">
      Navigate there
    </a>
  )
}

function LocationPreview({ address, clusterId }) {
  const cell = decodeGeohash(clusterId)
  const place = usePlaceName(cell)
  const navigateHref = navigateUrl(cell, address)

  if (!cell) {
    // No usable fix on this alert -- show the address the barangay already holds, nothing more.
    return (
      <div className="location-preview">
        <div className="location-text">
          <p className="location-address">{address || 'Address not on file'}</p>
          <p className="location-note muted">
            No GPS fix was captured for this alert. Shown is the senior’s registered address.
          </p>
          <NavigateButton href={navigateHref} />
        </div>
      </div>
    )
  }

  const metres = cellSizeMeters(cell)

  return (
    <div className="location-preview">
      <LocationMap cell={cell} metres={metres} />
      <div className="location-text">
        {/* The place under the pin, not the home on file: a senior is often not at home when an
            alert fires, and printing their registered address under a pin that is somewhere
            else made the text contradict the map. */}
        {place && (
          <p className="location-address">
            {metres > 50 ? 'Around ' : 'Near '}
            {place}
          </p>
        )}
        <p className="location-home">
          <span className="muted">Registered address:</span> {address || 'Address not on file'}
        </p>
        <p className="location-note muted">
          The pin is where the senior was when the alert fired. The registered address is their
          home on file and may be somewhere else.
        </p>
        <NavigateButton href={navigateHref} />
      </div>
    </div>
  )
}

// The full escalation history for this incident: every tier the alert passed through, who
// acted, and any note they left. Built from `escalation_steps`, the JSON audit trail the
// phone, the family app and the server-side clock all append to (CLAUDE.md §8). `STEP_LABEL`
// (labels.js) turns each code into a sentence; an unknown code prints as itself rather than
// vanishing. This is where a responder sees *why* an alert reached them -- and, once closed,
// how it was resolved and by whom.
function EscalationTimeline({ steps }) {
  const entries = Array.isArray(steps) ? steps : []
  if (entries.length === 0) return null
  return (
    <>
      <h3 className="details-location-title">Escalation timeline</h3>
      <ol className="timeline">
        {entries.map((entry, i) => (
          <li key={i} className="timeline-item">
            <span className="timeline-dot" aria-hidden="true" />
            <div className="timeline-body">
              <p className="timeline-step">{stepLabel(entry)}</p>
              <p className="timeline-meta">
                {dateTimeLabel(entry.at)}
                {entry.by ? ` · ${entry.by}` : ''}
              </p>
              {entry.notes && <p className="timeline-note">“{entry.notes}”</p>}
            </div>
          </li>
        ))}
      </ol>
    </>
  )
}

// The responder acts on an incident from here, not from the row: the three buttons sit
// below the location map so the description, time and place are all in view first. The
// remarks box is optional for every action -- blank sends exactly the request the row
// buttons used to (see useAlertActions); a note is saved alongside the status change.
function IncidentActions({ alert, onAct, busy }) {
  const [remarks, setRemarks] = useState('')

  const BUTTONS = [
    { key: 'acknowledge', label: 'Acknowledge', cls: '' },
    { key: 'resolve', label: 'Resolved', cls: 'details-action-ok' },
    { key: 'falsePositive', label: 'False Positive', cls: 'details-action-danger' },
  ]

  return (
    <div className="details-actions">
      <div className="details-action-btns">
        {BUTTONS.map(({ key, label, cls }) => (
          <button
            key={key}
            type="button"
            className={`btn-outline ${cls}`.trim()}
            disabled={busy || !canActOn(key, alert)}
            onClick={() => onAct(alert, key, remarks)}
          >
            {label}
          </button>
        ))}
      </div>
      <label className="details-remarks">
        <span>Remarks (optional)</span>
        <textarea
          rows={3}
          value={remarks}
          onChange={(e) => setRemarks(e.target.value)}
          placeholder="Add a note for the incident log — e.g. why this is a false positive, or who attended. Leave blank to skip."
        />
      </label>
    </div>
  )
}

export default function AlertDetailsModal({ alert, onClose, onAct, actionBusy }) {
  return (
    <Modal onClose={onClose} labelledBy="alert-details-title" className="details-modal">
      <button type="button" className="modal-close" onClick={onClose} aria-label="Close">
        ✕
      </button>

      {/* Senior identity + what/when stay pinned; everything below scrolls under them. */}
      <div className="details-sticky">
        <div className="details-head">
          <span className="avatar avatar-lg">{initials(alert.senior_name)}</span>
          <div>
            <h2 id="alert-details-title" className="details-name">
              {alert.senior_name}
            </h2>
            {/* Age and gender are only relevant while a responder is actively deciding how
                to reach this senior -- hidden once the incident is closed, same as the
                location panel below. Gender shows only when the senior gave one at
                onboarding (the API sends null otherwise). */}
            {isActiveAlert(alert) && (
              <p className="details-age">
                Age: {alert.senior_age}
                {alert.senior_gender ? ` · ${alert.senior_gender}` : ''}
              </p>
            )}
          </div>
        </div>

        <div className="details-cards">
          <div className="details-card">
            <h3>Description</h3>
            <p>{triggerLabel(alert.trigger_type)}</p>
          </div>
          <div className="details-card">
            <h3>Last Detected</h3>
            <p>{dateTimeLabel(alert.created_at)}</p>
          </div>
        </div>
      </div>

      <div className="details-body">
        {/* Last Known Location is an operational aid for a responder who still has to reach
            the senior -- it only makes sense while the incident is open. Once it's resolved
            or marked a false positive (all of Alert History, and the closed rows on the
            Alerts tab) there is nobody to dispatch, so the panel is hidden. */}
        {isActiveAlert(alert) && (
          <>
            <h3 className="details-location-title">Last Known Location</h3>
            <LocationPreview
              address={alert.senior_address}
              clusterId={alert.location_cluster_id}
            />
          </>
        )}

        <EscalationTimeline steps={alert.escalation_steps} />

        {onAct && <IncidentActions alert={alert} onAct={onAct} busy={actionBusy} />}
      </div>
    </Modal>
  )
}

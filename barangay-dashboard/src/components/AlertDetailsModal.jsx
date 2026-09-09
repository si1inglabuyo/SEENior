import { useState } from 'react'
import { triggerLabel, stepLabel } from '../labels'
import { initials, dateTimeLabel } from '../format'
import { canActOn } from '../alertActions'
import { isActiveAlert } from '../historyFilters'
import { decodeGeohash, cellSizeMeters } from '../geohash'
import Modal from './Modal'

// "Last Known Location" -- the senior's actual position at the moment the alert fired,
// captured once (never continuously) and stored as a precision geohash. Since 2026-08-31
// this is a precise fix, held lawfully under RA 10173 §12(c) during an active emergency,
// NOT an anonymised cluster (root CLAUDE.md §11 -- do not call it anonymous). We decode the
// geohash to coordinates and show the real spot on an OpenStreetMap tile, with the
// registered street address kept alongside because the responder still needs a name to
// read out. Older alerts carry a ~150 m cell; we say "approximate area" for those.
function LocationPreview({ address, clusterId }) {
  const cell = decodeGeohash(clusterId)

  if (!cell) {
    // No usable fix on this alert -- show the address the barangay already holds, nothing more.
    return (
      <div className="location-preview">
        <div className="location-text">
          <p className="location-address">{address || 'Address not on file'}</p>
          <p className="location-note muted">
            No GPS fix was captured for this alert. Shown is the senior’s registered address.
          </p>
        </div>
      </div>
    )
  }

  const { lat, lon } = cell
  const metres = cellSizeMeters(cell)
  const precise = metres <= 30
  // A small window around the point for the embed; a touch wider for the coarse old cells.
  const pad = precise ? 0.0016 : Math.max(cell.lonErr, cell.latErr) * 1.6
  const bbox = [lon - pad, lat - pad, lon + pad, lat + pad].map((n) => n.toFixed(6)).join(',')
  const embedSrc = `https://www.openstreetmap.org/export/embed.html?bbox=${encodeURIComponent(
    bbox
  )}&layer=mapnik&marker=${lat.toFixed(6)},${lon.toFixed(6)}`
  const mapLink = `https://www.openstreetmap.org/?mlat=${lat.toFixed(6)}&mlon=${lon.toFixed(
    6
  )}#map=${precise ? 18 : 16}/${lat.toFixed(5)}/${lon.toFixed(5)}`

  return (
    <div className="location-preview">
      <iframe
        className="location-map"
        title="Alert location"
        loading="lazy"
        src={embedSrc}
      />
      <div className="location-text">
        <p className="location-address">{address || 'Address not on file'}</p>
        <p className="location-coords">
          {precise ? 'Fix' : 'Approximate area'}: {lat.toFixed(5)}, {lon.toFixed(5)}
          {' · '}
          <a href={mapLink} target="_blank" rel="noreferrer">
            Open in OpenStreetMap
          </a>
        </p>
        <p className="location-note muted">
          The senior’s position when the alert fired, captured once. Shared with the barangay
          for this emergency under RA 10173 §12(c){precise ? '' : ` (~${Math.round(metres)} m cell)`}.
        </p>
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
              <p className="timeline-step">{stepLabel(entry.step)}</p>
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
    { key: 'resolve', label: 'Resolved', cls: '' },
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
            disabled={busy || !canActOn(key, alert.status)}
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

      <div className="details-head">
        <span className="avatar avatar-lg">{initials(alert.senior_name)}</span>
        <div>
          <h2 id="alert-details-title" className="details-name">
            {alert.senior_name}
          </h2>
          {/* Age and gender are only relevant while a responder is actively deciding how to
              reach this senior -- hidden once the incident is closed, same as the location
              panel below. Gender shows only when the senior gave one at onboarding (the API
              sends null otherwise). */}
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

      {/* Last Known Location is an operational aid for a responder who still has to reach
          the senior -- it only makes sense while the incident is open. Once it's resolved or
          marked a false positive (all of Alert History, and the closed rows on the Alerts
          tab) there is nobody to dispatch, so the panel is hidden. */}
      {isActiveAlert(alert) && (
        <>
          <h3 className="details-location-title">Last Known Location</h3>
          <LocationPreview address={alert.senior_address} clusterId={alert.location_cluster_id} />
        </>
      )}

      <EscalationTimeline steps={alert.escalation_steps} />

      {onAct && <IncidentActions alert={alert} onAct={onAct} busy={actionBusy} />}
    </Modal>
  )
}

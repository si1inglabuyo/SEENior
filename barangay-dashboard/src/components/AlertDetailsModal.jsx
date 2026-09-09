import { useState } from 'react'
import { triggerLabel } from '../labels'
import { initials, dateTimeLabel } from '../format'
import { canActOn } from '../alertActions'
import { isActiveAlert } from '../historyFilters'
import Modal from './Modal'

// "Last Known Location" deliberately does not plot a pin on a real map. CLAUDE.md §11 is
// explicit that GPS is captured only as an anonymous `location_cluster_id`, never raw
// coordinates -- so there is nothing to geocode, and faking a marker from the street
// address would show a location the system never actually recorded. This renders the one
// thing the API does have (the registered address, already shared with the barangay under
// RA 10173 §12(c)) over a static illustration instead of a live tile map.
function LocationPreview({ address, clusterId }) {
  return (
    <div className="location-preview">
      <svg viewBox="0 0 400 160" className="location-art" aria-hidden="true">
        <rect width="400" height="160" fill="#eef2f0" />
        <path d="M0 40 H400 M0 90 H400 M60 0 V160 M230 0 V160 M320 0 V160" stroke="#d7ded9" strokeWidth="3" />
        {/* Map pin: rounded head at the top, tip pointing down at the marked spot. */}
        <path
          d="M200 36c-15 0-27 12-27 27 0 20 27 51 27 51s27-31 27-51c0-15-12-27-27-27z"
          fill="#c4453c"
        />
        <circle cx="200" cy="63" r="10" fill="#eef2f0" />
      </svg>
      <div className="location-text">
        <p className="location-address">{address}</p>
        {clusterId && <p className="location-cluster">Location cluster: {clusterId}</p>}
        <p className="location-note muted">
          Live GPS isn't stored -- SEENior only ever shares the senior's registered address
          and an anonymous location cluster, captured at the moment the alert fired.
        </p>
      </div>
    </div>
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

      {onAct && <IncidentActions alert={alert} onAct={onAct} busy={actionBusy} />}
    </Modal>
  )
}

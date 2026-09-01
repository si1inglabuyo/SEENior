import { triggerLabel } from '../labels'
import { initials, dateTimeLabel } from '../format'
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
        <circle cx="200" cy="78" r="9" fill="#c4453c" />
        <path d="M200 78 c0 14 -16 22 -16 34 a16 16 0 0 0 32 0c0 -12 -16 -20 -16 -34z" fill="#c4453c" />
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

export default function AlertDetailsModal({ alert, onClose }) {
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
          <p className="details-age">Age: {alert.senior_age}</p>
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

      <h3 className="details-location-title">Last Known Location</h3>
      <LocationPreview address={alert.senior_address} clusterId={alert.location_cluster_id} />
    </Modal>
  )
}

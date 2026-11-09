import { useEffect, useState } from 'react'
import {
  alertCategory,
  CATEGORY_CLASS,
  CATEGORY_LABEL,
  displayStatus,
  isAttending,
  stepLabel,
  triggerLabel,
  pookTitle,
} from '../labels'
import { api } from '../api'
import { reverseGeocode } from '../reverseGeocode'
import { initials, dateTimeLabel } from '../format'
import { canActOn } from '../alertActions'
import { isActiveAlert } from '../historyFilters'
import { decodeGeohash, cellSizeMeters } from '../geohash'
import { fallContext } from '../fallContext'
import Modal from './Modal'
import LocationMap from './LocationMap'
import StatusPill from './StatusPill'
import DispatchPanel from './DispatchPanel'
import { TIERS, tierOf } from '../dispatch'

// "Last Known Location": the senior's position when the alert fired, stored as a geohash.
// It is a precise fix held under RA 10173 section 12(c), not anonymised. Shown on a live
// OpenStreetMap map (LocationMap) with the registered address beside it. Older alerts have
// a ~150 m cell, which is labelled "approximate area".
//
// Place name under the pin, looked up once per fix. The result is stored with its key and
// only used while that key is current, so switching alerts never shows the old street.
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

// Google Maps directions to the senior. Only the destination is given. Uses the captured
// fix, or the registered address if there is none.
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
function EscalationTimeline({ alert }) {
  const steps = alert.escalation_steps
  const all = Array.isArray(steps) ? steps : []
  // An SOS goes to family and barangay together, and its one escalation line already names the
  // family channel (labels.js), so the separate family-notified rows would only repeat it.
  const sosMerged = alert.trigger_type === 'sos' && all.some((e) => e && e.step === 'escalated_barangay_auto')
  const MERGED = ['escalated_family', 'escalated_family_server', 'push_received_family', 'sms_sent_family']
  const entries = sosMerged ? all.filter((e) => !(e && MERGED.includes(e.step))) : all
  if (entries.length === 0) return null
  return (
    <>
      <h3 className="details-location-title">Escalation timeline</h3>
      <ol className="timeline">
        {entries.map((entry, i) => (
          <li key={i} className="timeline-item">
            <span className="timeline-dot" aria-hidden="true" />
            <div className="timeline-body">
              <p className="timeline-step">{stepLabel(entry, alert)}</p>
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

// For a fall alert: a few plain sentences on what the phone was doing just before the drop, whether
// it fell in the senior's sleep hours, and whether the detector had been made less sensitive. No raw
// figures. Read from `fall_context` / `fall_tolerance`; nothing for older alerts or other triggers.
// A hint for the responder, not something the detector acts on.
function FallContext({ alert }) {
  if (alert.trigger_type !== 'fall_pattern') return null
  const ctx = fallContext(alert.fall_context, alert.fall_tolerance)
  if (!ctx) return null
  return (
    <>
      <h3 className="details-location-title">Just before the fall</h3>
      <ul className="fall-context">
        {ctx.lines.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <p className="fall-context-reading muted">This is only a hint. Confirm with the senior or family.</p>
    </>
  )
}

// Who to phone: the senior, and each family contact on their record. The family list comes from
// the senior's record (GET /barangay/seniors/{sync_id}), fetched when an open alert is shown.
function useFamilyContacts(seniorSyncId) {
  const [found, setFound] = useState({ id: null, contacts: null, failed: false })

  useEffect(() => {
    let cancelled = false
    api(`/barangay/seniors/${seniorSyncId}`)
      .then((data) => {
        if (!cancelled) setFound({ id: seniorSyncId, contacts: data.contacts ?? [], failed: false })
      })
      .catch(() => {
        if (!cancelled) setFound({ id: seniorSyncId, contacts: null, failed: true })
      })
    return () => {
      cancelled = true
    }
  }, [seniorSyncId])

  return found.id === seniorSyncId ? found : { id: seniorSyncId, contacts: null, failed: false }
}

function PhoneLink({ number }) {
  if (!number) return <span className="muted">No number on file</span>
  return <a href={`tel:${number.replace(/\s/g, '')}`}>{number}</a>
}

function ContactNumbers({ alert }) {
  const { contacts, failed } = useFamilyContacts(alert.senior_sync_id)
  return (
    <div className="contact-numbers">
      <h3 className="details-location-title">Contact numbers</h3>
      <ul className="contact-numbers-list">
        <li>
          <span className="contact-numbers-who">
            {alert.senior_name} <span className="muted">· senior</span>
          </span>
          <strong className="contact-numbers-phone">
            <PhoneLink number={alert.senior_mobile} />
          </strong>
        </li>
        {contacts === null ? (
          <li className="muted">{failed ? 'Family contacts could not be loaded.' : 'Loading family contacts…'}</li>
        ) : contacts.length === 0 ? (
          <li className="muted">No family contact is linked to this senior.</li>
        ) : (
          contacts.map((c, i) => (
            <li key={`${c.name}-${i}`}>
              <span className="contact-numbers-who">
                {c.name}
                {c.relationship_label ? <span className="muted"> · {c.relationship_label}</span> : null}
              </span>
              <strong className="contact-numbers-phone">
                <PhoneLink number={c.phone} />
              </strong>
            </li>
          ))
        )}
      </ul>
    </div>
  )
}

// One tap fills the remark below, which can still be edited. Saved with the status change.
const REMARK_CHIPS = [
  'Called, senior fine',
  'Responder attended, senior safe',
  'Family already with senior',
  'Could not reach, family handling it',
]

// The action buttons sit below the map. The remarks box is optional; a note is
// saved with the status change.
function IncidentActions({ alert, onAct, busy }) {
  const [remarks, setRemarks] = useState('')

  const BUTTONS = [
    { key: 'resolve', label: 'Resolved', cls: 'details-action-ok' },
    { key: 'falsePositive', label: 'False Alert', cls: 'details-action-danger' },
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
      <div className="remark-chips" role="group" aria-label="Quick remarks">
        {REMARK_CHIPS.map((chip) => (
          <button
            key={chip}
            type="button"
            className={remarks === chip ? 'remark-chip is-on' : 'remark-chip'}
            aria-pressed={remarks === chip}
            onClick={() => setRemarks(remarks === chip ? '' : chip)}
          >
            {chip}
          </button>
        ))}
      </div>
      <label className="details-remarks">
        <span>Remarks (optional)</span>
        <textarea
          rows={3}
          value={remarks}
          onChange={(e) => setRemarks(e.target.value)}
          placeholder="Add a note for the incident log — e.g. why this is a false alert, or who attended. Leave blank to skip."
        />
      </label>
      {isAttending(alert) && (
        <p className="details-attending-note muted">
          A responder is already attending. Close it as Resolved or False Alert once it is
          settled.
        </p>
      )}
    </div>
  )
}

// "Tier 1"; hovering shows the tier's name and its first action.
function TierBadge({ alert }) {
  const tier = tierOf(alert)
  const { label, action } = TIERS[tier - 1]
  return (
    <span className={`tier-badge tier-badge-${tier}`} title={`${label}: ${action}`}>
      Tier {tier}
    </span>
  )
}

export default function AlertDetailsModal({ alert, onClose, onAct, actionBusy, dispatch, activeAlerts }) {
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
            {alert.senior_pook && (
              <p className="details-age">
                Pook: {alert.senior_pook}
                {alert.senior_sector ? ` · ${pookTitle(alert.senior_sector)}` : ''}
              </p>
            )}
            {isActiveAlert(alert) && (
              <p className="details-age">
                Age: {alert.senior_age}
                {alert.senior_gender ? ` · ${alert.senior_gender}` : ''}
              </p>
            )}
            <div className="details-badges">
              <span className={`type-badge ${CATEGORY_CLASS[alertCategory(alert)]}`.trim()}>
                {CATEGORY_LABEL[alertCategory(alert)]}
              </span>
              <StatusPill status={displayStatus(alert)} />
              {/* The barangay's priority tier, beside the status, while the alert is open. */}
              {dispatch?.pooks && isActiveAlert(alert) && <TierBadge alert={alert} />}
            </div>
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
        {isActiveAlert(alert) && <ContactNumbers alert={alert} />}

        {dispatch?.pooks && isActiveAlert(alert) && (
          <DispatchPanel
            alert={alert}
            activeAlerts={activeAlerts}
            pooks={dispatch.pooks}
            hotlines={dispatch.hotlines}
            teamList={dispatch.teamList}
            serviceList={dispatch.serviceList}
            busy={dispatch.busy}
            error={dispatch.error}
            onSend={dispatch.send}
            onResolve={onAct ? (a, note) => onAct(a, 'resolve', note) : undefined}
          />
        )}

        {/* Last Known Location is an operational aid for a responder who still has to reach
            the senior -- it only makes sense while the incident is open. Once it's resolved
            or marked a false alert (all of Alert History, and the closed rows on the
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

        <FallContext alert={alert} />

        <EscalationTimeline alert={alert} />

        {onAct && <IncidentActions alert={alert} onAct={onAct} busy={actionBusy} />}
      </div>
    </Modal>
  )
}

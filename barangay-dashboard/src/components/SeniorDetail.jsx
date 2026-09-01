import { useEffect, useState } from 'react'
import { api } from '../api'
import { initials, dateTimeLabel } from '../format'
import { triggerLabel, alertCategory, CATEGORY_LABEL } from '../labels'
import { DEACTIVATE_ACTION } from '../seniorActions'
import {
  IconArrowLeft,
  IconSeniors,
  IconContacts,
  IconHistory,
  IconPhone,
  IconMail,
} from '../icons'
import SectionCard from './SectionCard'
import ConfirmDialog from './ConfirmDialog'
import Toast from './Toast'
import AlertDetailsModal from './AlertDetailsModal'

const CATEGORY_CLASS = { sos: 'type-badge-sos', dispatch_family: 'type-badge-dispatch', anomaly: '' }

// One senior's record. Prefers GET /barangay/seniors/{sync_id} (profile + family contacts
// + that senior's alert history in one call); if that endpoint isn't deployed yet it
// degrades to the list row it was handed plus the shared alerts endpoint, so the page is
// still useful against an older backend -- just without the contacts card.
export default function SeniorDetail({
  syncId,
  fallbackSenior,
  isDeactivated,
  onDeactivate,
  onBack,
  onSessionLost,
}) {
  const [detail, setDetail] = useState(null)
  const [fallbackAlerts, setFallbackAlerts] = useState(null)
  const [phase, setPhase] = useState('loading') // 'loading' | 'ok' | 'fallback' | 'error'
  const [error, setError] = useState('')

  const [confirming, setConfirming] = useState(false)
  const [toastOpen, setToastOpen] = useState(false)
  const [detailsAlert, setDetailsAlert] = useState(null)

  useEffect(() => {
    let live = true
    api(`/barangay/seniors/${syncId}`)
      .then((data) => {
        if (!live) return
        setDetail(data)
        setPhase('ok')
      })
      .catch((err) => {
        if (!live) return
        if (err.message.includes('expired')) {
          onSessionLost()
          return
        }
        if (!fallbackSenior) {
          setError(err.message)
          setPhase('error')
          return
        }
        setPhase('fallback')
        api('/barangay/alerts?scope=history')
          .then((list) => {
            if (live) setFallbackAlerts(list.filter((a) => a.senior_sync_id === syncId))
          })
          .catch(() => {
            if (live) setFallbackAlerts([])
          })
      })
    return () => {
      live = false
    }
  }, [syncId, fallbackSenior, onSessionLost])

  function confirmDeactivate() {
    // The write that persists this is a backend endpoint that doesn't exist yet (needs a
    // `seniors.status` column -- see SeniorRoster). Until then onDeactivate() updates the
    // client store so the List and this page reflect it immediately, as the mockup shows.
    onDeactivate()
    setConfirming(false)
    setToastOpen(true)
  }

  const profile =
    detail ||
    (fallbackSenior && {
      first_name: fallbackSenior.first_name,
      last_name: fallbackSenior.last_name,
      age: fallbackSenior.age,
      gender: fallbackSenior.gender,
      address: fallbackSenior.address,
      mobile_number: fallbackSenior.mobile_number,
      living_arrangement: null,
      contacts: [],
      alerts: fallbackAlerts || [],
    })

  const name = profile ? `${profile.first_name} ${profile.last_name}` : ''
  const contacts = profile?.contacts ?? []
  const alerts = profile?.alerts ?? []

  return (
    <div className="senior-detail">
      <button type="button" className="back-btn" onClick={onBack} aria-label="Back to Senior List">
        <IconArrowLeft />
      </button>

      {phase === 'loading' && !profile && <p className="muted">Loading&hellip;</p>}
      {phase === 'error' && <p className="error">{error}</p>}

      {profile && (
        <div className="senior-detail-grid">
          <SectionCard icon={<IconSeniors />} title="Senior Profile" className="profile-card">
            <div className="profile-hero">
              <span className="avatar avatar-xl">{initials(name)}</span>
              <h2 className="profile-name">{name}</h2>
              <p className="profile-sub">
                Age {profile.age} &middot; {profile.address}
              </p>
              <span className={`status-badge ${isDeactivated ? 'status-off' : 'status-on'}`}>
                <span className="status-dot" />
                {isDeactivated ? 'Deactivated' : 'Active'}
              </span>
            </div>

            <div className="info-block">
              <h4>Personal Information</h4>
              <dl className="info-list">
                <div>
                  <dt>Gender</dt>
                  <dd>{profile.gender || '—'}</dd>
                </div>
                <div>
                  <dt>Age</dt>
                  <dd>{profile.age} years old</dd>
                </div>
                <div>
                  <dt>Phone Number</dt>
                  <dd>{profile.mobile_number || '—'}</dd>
                </div>
                <div>
                  <dt>Address</dt>
                  <dd>{profile.address || '—'}</dd>
                </div>
                <div>
                  <dt>Living Arrangement</dt>
                  <dd>{profile.living_arrangement || '—'}</dd>
                </div>
              </dl>
            </div>

            <button
              type="button"
              className="deactivate-btn"
              disabled={isDeactivated}
              onClick={() => setConfirming(true)}
            >
              <IconSeniors /> {isDeactivated ? 'Account Deactivated' : 'Deactivate Account'}
            </button>
          </SectionCard>

          <div className="senior-detail-side">
            <SectionCard icon={<IconContacts />} title="Emergency Contacts" className="contacts-card">
              {phase === 'fallback' ? (
                <p className="muted alerts-empty">
                  Contacts need a backend update that isn&apos;t live yet.
                </p>
              ) : contacts.length === 0 ? (
                <p className="muted alerts-empty">No family contacts linked.</p>
              ) : (
                <ul className="contact-list">
                  {contacts.map((contact, i) => (
                    <li key={i} className="contact-card">
                      <div>
                        <p className="contact-name">{contact.name}</p>
                        {contact.relationship_label && (
                          <span className="contact-rel">{contact.relationship_label}</span>
                        )}
                      </div>
                      <div className="contact-reach">
                        <span>
                          <IconPhone /> {contact.phone || '—'}
                        </span>
                        <span>
                          <IconMail /> {contact.email || '—'}
                        </span>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </SectionCard>

            <SectionCard icon={<IconHistory />} title="Alert History" className="senior-alert-history">
              {alerts.length === 0 ? (
                <p className="muted alerts-empty">No alerts on record for this senior.</p>
              ) : (
                <ul className="mini-alert-list">
                  {alerts.map((alert) => {
                    const category = alertCategory(alert)
                    return (
                      <li
                        key={alert.sync_id}
                        className="mini-alert"
                        onClick={() => setDetailsAlert(alert)}
                      >
                        <span className={`mini-alert-dot mini-alert-dot-${category}`} />
                        <div className="mini-alert-main">
                          <p className="mini-alert-title">{triggerLabel(alert.trigger_type)}</p>
                          <p className="mini-alert-when muted">{dateTimeLabel(alert.created_at)}</p>
                        </div>
                        <span className={`type-badge ${CATEGORY_CLASS[category]}`.trim()}>
                          {CATEGORY_LABEL[category]}
                        </span>
                      </li>
                    )
                  })}
                </ul>
              )}
            </SectionCard>
          </div>
        </div>
      )}

      {confirming && (
        <ConfirmDialog
          action={DEACTIVATE_ACTION}
          busy={false}
          onCancel={() => setConfirming(false)}
          onConfirm={confirmDeactivate}
        />
      )}
      {toastOpen && (
        <Toast message={DEACTIVATE_ACTION.successMessage} onClose={() => setToastOpen(false)} />
      )}
      {detailsAlert && (
        <AlertDetailsModal alert={detailsAlert} onClose={() => setDetailsAlert(null)} />
      )}
    </div>
  )
}

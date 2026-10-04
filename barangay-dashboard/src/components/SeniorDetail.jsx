import { useEffect, useMemo, useRef, useState } from 'react'
import { api, parseServerTime } from '../api'
import { initials, dateTimeLabel, maskPhone } from '../format'
import { triggerLabel, alertCategory, CATEGORY_LABEL } from '../labels'
import { DEACTIVATE_ACTION, REACTIVATE_ACTION } from '../seniorActions'
import { recordAccess } from '../audit'
import {
  IconArrowLeft,
  IconSeniors,
  IconContacts,
  IconHistory,
  IconPhone,
  IconEye,
  IconLock,
} from '../icons'
import SectionCard from './SectionCard'
import ConfirmDialog from './ConfirmDialog'
import Toast from './Toast'
import AlertDetailsModal from './AlertDetailsModal'
import DeviceBadge from './DeviceBadge'

// RA 10173 (Data Privacy Act) - why some fields on this screen are gated.
//
// By default a responder sees only what a documented task needs: name, status, a maskable
// phone number and a count of open incidents (section 11(d)). Gender, living arrangement,
// full address and the family contact's email are not shown by default.
//
// Full address and the family contact's name/relationship/phone unlock only while the
// senior has an open escalated alert (vital interests, section 13(c)), and lock again when
// it closes. The alert-history card defaults to a short window (section 11(e)). Every
// disclosure beyond the default view is written to the access audit log (src/audit.js),
// section 23(a). The email stays withheld since no responder task needs it.

const CATEGORY_CLASS = {
  sos: 'type-badge-sos',
  dispatch_family: 'type-badge-dispatch',
  potential_fall: 'type-badge-fall',
  anomaly: '',
}

// Retention for the per-senior alert history (RA 10173 section 11(e)): the default card
// shows at most DEFAULT_HISTORY_MAX entries from the last DEFAULT_HISTORY_WINDOW_DAYS. The
// full list needs an audited "View full history" click. Purging alerts older than ~90 days
// server-side is a main-lane job.
const DEFAULT_HISTORY_MAX = 3
const DEFAULT_HISTORY_WINDOW_DAYS = 7

// One senior's record. Uses GET /barangay/seniors/{sync_id}; if that isn't deployed it falls
// back to the list row plus the shared alerts endpoint, without the contacts card.
export default function SeniorDetail({
  syncId,
  fallbackSenior,
  isDeactivated,
  onDeactivate,
  onReactivate,
  onBack,
  onSessionLost,
}) {
  const [detail, setDetail] = useState(null)
  const [fallbackAlerts, setFallbackAlerts] = useState(null)
  const [phase, setPhase] = useState('loading') // 'loading' | 'ok' | 'fallback' | 'error'
  const [error, setError] = useState('')

  const [confirming, setConfirming] = useState(false)
  const [toastMsg, setToastMsg] = useState(null)
  const [detailsAlert, setDetailsAlert] = useState(null)

  const accountAction = isDeactivated ? REACTIVATE_ACTION : DEACTIVATE_ACTION

    // Disclosures beyond the default view, each turned on by a click that writes an audit entry.
  const [phoneRevealed, setPhoneRevealed] = useState(false)
  const [fullHistory, setFullHistory] = useState(false)
  const activeViewLogged = useRef(false)
    // "Now" fixed at mount so the 7-day window is pure during render.
  const [now] = useState(() => Date.now())

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

  async function confirmAccountToggle() {
      // Saved on the server. If that fails nothing changed, and the responder is told.
    const message = accountAction.successMessage
    setConfirming(false)
    try {
      if (isDeactivated) await onReactivate()
      else await onDeactivate()
      setToastMsg(message)
    } catch (err) {
      if (err.message.includes('expired')) onSessionLost()
      else setToastMsg(`Could not save: ${err.message}`)
    }
  }

  const profile = useMemo(
    () =>
      detail ||
      (fallbackSenior && {
        first_name: fallbackSenior.first_name,
        last_name: fallbackSenior.last_name,
        age: fallbackSenior.age,
        gender: fallbackSenior.gender,
        address: fallbackSenior.address,
        mobile_number: fallbackSenior.mobile_number,
        living_arrangement: null,
        last_seen_at: fallbackSenior.last_seen_at,
        battery_percent: fallbackSenior.battery_percent,
        is_charging: fallbackSenior.is_charging,
        contacts: [],
        alerts: fallbackAlerts || [],
      }),
    [detail, fallbackSenior, fallbackAlerts]
  )

  const name = profile ? `${profile.first_name} ${profile.last_name}` : ''
  const contacts = profile?.contacts ?? []
  const alerts = profile?.alerts ?? []

  // An open alert at the barangay tier is an active emergency — the §13(c) trigger.
  const openAlerts = alerts.filter((a) => a.status === 'escalated')
  const activeAlert = openAlerts.length > 0

    // Opening the active-alert view discloses the address and contacts, so it is audited (once per mount).
  useEffect(() => {
    if (!profile || activeViewLogged.current || !activeAlert) return
    activeViewLogged.current = true
    recordAccess('active_alert_view', { sync_id: syncId, open_alerts: openAlerts.length })
  }, [profile, activeAlert, syncId, openAlerts.length])

  function revealPhone() {
    setPhoneRevealed(true)
    recordAccess('senior_phone_reveal', { sync_id: syncId })
  }

  function showFullHistory() {
    setFullHistory(true)
    recordAccess('full_history_view', { sync_id: syncId, total_entries: alerts.length })
  }

  // Newest-first from the API (and the fallback filters a list that is already sorted).
  const windowMs = DEFAULT_HISTORY_WINDOW_DAYS * 24 * 60 * 60 * 1000
  const recentAlerts = alerts
    .filter((a) => {
      const t = parseServerTime(a.created_at)
      return t && now - t.getTime() <= windowMs
    })
    .slice(0, DEFAULT_HISTORY_MAX)
  const shownAlerts = fullHistory ? alerts : recentAlerts
  const moreCount = alerts.length - shownAlerts.length

  const phoneKnown = profile?.mobile_number

  return (
    <div className="senior-detail">
      <button type="button" className="back-btn" onClick={onBack} aria-label="Back to Senior List">
        <IconArrowLeft />
      </button>

      {phase === 'loading' && !profile && <p className="muted">Loading&hellip;</p>}
      {phase === 'error' && <p className="error">{error}</p>}

      {profile && (
        <>
          {activeAlert && (
            <div className="gate-banner" role="status">
              <IconEye />
              <span>
                <strong>Active alert.</strong> Full home address and family-contact
                details are unlocked for the duration of this emergency. This access is being logged.
              </span>
            </div>
          )}

          <div className="senior-detail-grid">
            <SectionCard icon={<IconSeniors />} title="Senior Profile" className="profile-card">
              <div className="profile-hero">
                <span className="avatar avatar-xl">{initials(name)}</span>
                <h2 className="profile-name">{name}</h2>
                <span className={`status-badge ${isDeactivated ? 'status-off' : 'status-on'}`}>
                  <span className="status-dot" />
                  {isDeactivated ? 'Inactive' : 'Active'}
                </span>
              </div>

              <div className="info-block">
                <h4>Contact</h4>
                <dl className="info-list">
                  <div>
                    <dt>Phone Number</dt>
                    <dd>
                      {!phoneKnown ? (
                        '—'
                      ) : phoneRevealed ? (
                        <a href={`tel:${profile.mobile_number}`}>{profile.mobile_number}</a>
                      ) : (
                        <span className="reveal-field">
                          {maskPhone(profile.mobile_number)}
                          <button type="button" className="reveal-btn" onClick={revealPhone}>
                            <IconEye /> View
                          </button>
                        </span>
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt>Open Alerts</dt>
                    <dd>{openAlerts.length === 0 ? 'None' : openAlerts.length}</dd>
                  </div>
                  <div>
                    <dt>Monitoring device</dt>
                    <dd><DeviceBadge senior={profile} /></dd>
                  </div>

                  {activeAlert ? (
                    <>
                      <div>
                        <dt>Age</dt>
                        <dd>{profile.age} years old</dd>
                      </div>
                      <div>
                        <dt>Home Address</dt>
                        <dd>{profile.address || '—'}</dd>
                      </div>
                    </>
                  ) : (
                    <div>
                      <dt>Home Address</dt>
                      <dd className="locked-dd">
                        <IconLock /> Unlocks during an active alert
                      </dd>
                    </div>
                  )}
                </dl>
              </div>

              <button
                type="button"
                className={isDeactivated ? 'deactivate-btn reactivate-btn' : 'deactivate-btn'}
                onClick={() => setConfirming(true)}
              >
                <IconSeniors /> {isDeactivated ? 'Reactivate Account' : 'Deactivate Account'}
              </button>
            </SectionCard>

            <div className="senior-detail-side">
              <SectionCard
                icon={<IconContacts />}
                title="Family Contacts"
                className="contacts-card"
              >
                {phase === 'fallback' ? (
                  <p className="muted alerts-empty">
                    Contacts need a backend update that isn&apos;t live yet.
                  </p>
                ) : contacts.length === 0 ? (
                  <p className="muted alerts-empty">No family contacts linked.</p>
                ) : activeAlert ? (
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
                        </div>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="muted alerts-empty locked-note">
                    <IconLock />{' '}
                    {contacts.length} family contact{contacts.length === 1 ? '' : 's'} on record.
                    Names and numbers unlock during an active alert.
                  </p>
                )}
              </SectionCard>

              <SectionCard
                icon={<IconHistory />}
                title="Alert History"
                className="senior-alert-history"
              >
                {alerts.length === 0 ? (
                  <p className="muted alerts-empty">No alerts on record for this senior.</p>
                ) : (
                  <>
                    {shownAlerts.length === 0 ? (
                      <p className="muted alerts-empty">
                        No alerts in the last {DEFAULT_HISTORY_WINDOW_DAYS} days.
                      </p>
                    ) : (
                      <ul className="mini-alert-list">
                        {shownAlerts.map((alert) => {
                          const category = alertCategory(alert)
                            // The Details modal shows the home address and location, which is
                            // only allowed while the emergency is live (section 13(c)), so rows
                            // open only in the active-alert view.
                          const openable = activeAlert
                          return (
                            <li
                              key={alert.sync_id}
                              className={`mini-alert${openable ? '' : ' mini-alert-static'}`}
                              onClick={openable ? () => setDetailsAlert(alert) : undefined}
                            >
                              <span className={`mini-alert-dot mini-alert-dot-${category}`} />
                              <div className="mini-alert-main">
                                <p className="mini-alert-title">
                                  {triggerLabel(alert.trigger_type)}
                                </p>
                                <p className="mini-alert-when muted">
                                  {dateTimeLabel(alert.created_at)}
                                </p>
                              </div>
                              <span className={`type-badge ${CATEGORY_CLASS[category]}`.trim()}>
                                {CATEGORY_LABEL[category]}
                              </span>
                            </li>
                          )
                        })}
                      </ul>
                    )}
                    {!fullHistory && moreCount > 0 && (
                      <button type="button" className="history-more-btn" onClick={showFullHistory}>
                        <IconHistory /> View full history ({moreCount} older)
                      </button>
                    )}
                    {fullHistory && (
                      <p className="muted history-full-note">
                        Showing full history &middot; this view was logged.
                      </p>
                    )}
                  </>
                )}
              </SectionCard>
            </div>
          </div>
        </>
      )}

      {confirming && (
        <ConfirmDialog
          action={accountAction}
          busy={false}
          onCancel={() => setConfirming(false)}
          onConfirm={confirmAccountToggle}
        />
      )}
      {toastMsg && <Toast message={toastMsg} onClose={() => setToastMsg(null)} />}
      {detailsAlert && (
        <AlertDetailsModal alert={detailsAlert} onClose={() => setDetailsAlert(null)} />
      )}
    </div>
  )
}

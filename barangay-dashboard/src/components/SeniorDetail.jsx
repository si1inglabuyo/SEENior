import { useEffect, useMemo, useRef, useState } from 'react'
import { api, parseServerTime } from '../api'
import { initials, dateTimeLabel, maskPhone } from '../format'
import { triggerLabel, alertCategory, CATEGORY_LABEL } from '../labels'
import { DEACTIVATE_ACTION } from '../seniorActions'
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

// ---------------------------------------------------------------------------------------
// RA 10173 (Data Privacy Act of 2012) — why fields on this screen are gated
//
// This screen is the barangay responder's view of one senior's record. The responder is a
// government agency worker (§23), so what they can see by default is deliberately narrow:
//
//   §11(d) proportionality — a persistent view shows only what a documented responder
//   function needs: name, status, a maskable phone number to call, and a count of open
//   incidents. Gender, living arrangement, full home address and the family contact's
//   email are NOT tied to any standing responder task, so they are not shown by default.
//
//   §13 sensitive personal information — kept off the default view for the same reason.
//
//   §13(c) vital-interests exception — full home address, and the emergency contact's
//   name / relationship / phone, unlock ONLY while this senior has an open (escalated)
//   alert, i.e. an active emergency. This is an exception, not a standing default: when
//   the alert closes, the extra fields lock again.
//
//   §11(e) retention — the alert-history card defaults to a short window (see
//   HISTORY_* below); the full list is one explicit, logged click away.
//
//   §23(a) access control + recording — every disclosure beyond the default view
//   (phone reveal, opening the active-alert view, expanding full history) is written to
//   the access audit log (src/audit.js).
//
// The email address is still withheld even in the gated view — no responder task
// documented so far needs it. Add it here only alongside that documented need.
// ---------------------------------------------------------------------------------------

const CATEGORY_CLASS = { sos: 'type-badge-sos', dispatch_family: 'type-badge-dispatch', anomaly: '' }

// Retention / data-minimisation for the per-senior alert history (RA 10173 §11(e) — keep
// data "only for as long as necessary"):
//   - the default card shows at most DEFAULT_HISTORY_MAX entries AND nothing older than
//     DEFAULT_HISTORY_WINDOW_DAYS; whichever is the shorter list wins.
//   - the full list is behind an explicit, audit-logged "View full history" click.
//   - resolved / false-positive alerts older than ~90 days should be archived out of the
//     GET /barangay/seniors/{sync_id} response server-side. That purge job belongs to the
//     `main` lane (it owns the alerts table and the sync pipeline, and this lane may not
//     add columns or migrations — barangay-dashboard/CLAUDE.md §2); noted here so the
//     policy lives next to the UI that assumes it.
const DEFAULT_HISTORY_MAX = 3
const DEFAULT_HISTORY_WINDOW_DAYS = 7

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

  // Disclosures beyond the default view. Each flips on via an explicit click that also
  // writes an access-audit entry (RA 10173 §23(a)).
  const [phoneRevealed, setPhoneRevealed] = useState(false)
  const [fullHistory, setFullHistory] = useState(false)
  const activeViewLogged = useRef(false)
  // A single "now" fixed at mount, so the 7-day history window is a pure computation
  // during render rather than a fresh Date.now() on every re-render.
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

  function confirmDeactivate() {
    // The write that persists this is a backend endpoint that doesn't exist yet (needs a
    // `seniors.status` column -- see SeniorRoster). Until then onDeactivate() updates the
    // client store so the List and this page reflect it immediately, as the mockup shows.
    onDeactivate()
    setConfirming(false)
    setToastOpen(true)
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

  // Opening the active-alert view discloses the full address + contact details, so it is
  // itself an audited access (once per mount).
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
                <strong>Active alert.</strong> Full home address and emergency-contact
                details are unlocked for the duration of this emergency under RA 10173
                §13(c) (vital interests). This access is being logged.
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
                            <IconEye /> Reveal &amp; call
                          </button>
                        </span>
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt>Open Alerts</dt>
                    <dd>{openAlerts.length === 0 ? 'None' : openAlerts.length}</dd>
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
                className="deactivate-btn"
                disabled={isDeactivated}
                onClick={() => setConfirming(true)}
              >
                <IconSeniors /> {isDeactivated ? 'Account Deactivated' : 'Deactivate Account'}
              </button>
            </SectionCard>

            <div className="senior-detail-side">
              <SectionCard
                icon={<IconContacts />}
                title="Emergency Contacts"
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
                    Names and numbers unlock during an active alert (RA 10173 §13(c)).
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
                          // The Details modal discloses the home address + last-known
                          // location for that alert. That is only permitted while the
                          // emergency is live (RA 10173 §13(c)), so the rows are openable
                          // only in the active-alert view; otherwise the row shows just
                          // the trigger and time, which is summary-level detail.
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

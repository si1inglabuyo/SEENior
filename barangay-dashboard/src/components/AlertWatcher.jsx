import { useEffect, useRef, useState } from 'react'
import { api, POLL_MS } from '../api'
import { isAttending, triggerLabel } from '../labels'

// Always mounted while signed in. Polls the active queue and, when a new incident appears,
// alerts the responder with a chime, a browser notification (if permitted) and a count in
// the tab title. The first poll after a page load adopts existing alerts silently.

function ensureNotificationPermission() {
  if (typeof Notification === 'undefined') return
  if (Notification.permission === 'default') {
    Notification.requestPermission().catch(() => {})
  }
}

// One AudioContext for the page's life. A fresh one made inside a poll starts suspended
// under the browser's autoplay policy and plays nothing; this one is resumed by the
// responder's first click or key press (signing in counts) and by every alert after it.
let sharedCtx = null
function audioContext() {
  const Ctx = window.AudioContext || window.webkitAudioContext
  if (!Ctx) return null
  if (!sharedCtx) sharedCtx = new Ctx()
  if (sharedCtx.state === 'suspended') sharedCtx.resume().catch(() => {})
  return sharedCtx
}

// Three rising beeps, loud enough to be heard across an office. Wrapped in try/catch because
// audio may still be blocked; the notification and title badge fire regardless.
function playChime() {
  try {
    const ctx = audioContext()
    if (!ctx) return
    const start = ctx.currentTime
    for (let round = 0; round < 3; round++) {
      ;[880, 1174].forEach((freq, i) => {
        const t = start + round * 0.7 + i * 0.25
        const osc = ctx.createOscillator()
        const gain = ctx.createGain()
        osc.type = 'square'
        osc.frequency.value = freq
        gain.gain.setValueAtTime(0.0001, t)
        gain.gain.exponentialRampToValueAtTime(0.5, t + 0.02)
        gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.22)
        osc.connect(gain).connect(ctx.destination)
        osc.start(t)
        osc.stop(t + 0.24)
      })
    }
  } catch {
    /* audio unavailable -- the other two channels still fire */
  }
}

// An SOS, a fall or a High-risk incident keeps sounding, and keeps a red banner on every page,
// until somebody takes a step on it (any dispatch step marks it attended). Clicking elsewhere
// does not silence it; the banner's Silence button mutes it for a minute.
const REPEAT_MS = 4000
const SILENCE_MS = 60_000
// Older than this and it is stale test data or yesterday's problem, not something to ring for.
const WAITING_MAX_AGE_MS = 24 * 60 * 60 * 1000
const isUrgent = (a) => a.trigger_type === 'sos' || a.trigger_type === 'fall_pattern' || a.risk_level === 'high'
const urgentLabel = (a) => (a.trigger_type === 'sos' ? 'SOS' : a.trigger_type === 'fall_pattern' ? 'Fall' : 'High risk')
const ageMs = (a) => Date.now() - Date.parse(/(Z|[+-]\d{2}:\d{2})$/.test(a.created_at) ? a.created_at : `${a.created_at}Z`)

// Urgent alerts that reached the barangay and nobody has started on.
const waitingOn = (list) =>
  list.filter((a) => isUrgent(a) && a.status === 'escalated' && !isAttending(a) && ageMs(a) < WAITING_MAX_AGE_MS)

export default function AlertWatcher({ onSessionLost, onGoToAlerts }) {
  const knownIds = useRef(null) // Set<sync_id> from the previous poll; null until first load
  const unseenCount = useRef(0) // new alerts not yet looked at, for the tab-title badge
  const baseTitle = useRef(typeof document !== 'undefined' ? document.title : 'SEENior')
  const goToAlerts = useRef(onGoToAlerts)
  useEffect(() => {
    goToAlerts.current = onGoToAlerts
  }, [onGoToAlerts])
  const repeatTimer = useRef(null)
  const silenceTimer = useRef(null)
  const [waiting, setWaiting] = useState([])
  const [silenced, setSilenced] = useState(false)

  function silence() {
    setSilenced(true)
    clearTimeout(silenceTimer.current)
    silenceTimer.current = setTimeout(() => setSilenced(false), SILENCE_MS)
  }

  function stopRepeating() {
    if (repeatTimer.current) {
      clearInterval(repeatTimer.current)
      repeatTimer.current = null
    }
  }

  // Unlocks audio on the first gesture. It does not stop the alarm: only acting on the alert
  // (or the banner's Silence button) does.
  useEffect(() => {
    function onGesture() {
      audioContext()
    }
    window.addEventListener('pointerdown', onGesture)
    window.addEventListener('keydown', onGesture)
    return () => {
      window.removeEventListener('pointerdown', onGesture)
      window.removeEventListener('keydown', onGesture)
      stopRepeating()
      clearTimeout(silenceTimer.current)
    }
  }, [])

  // Sounds while an urgent alert is waiting and the banner has not been silenced.
  useEffect(() => {
    if (waiting.length === 0 || silenced) {
      stopRepeating()
      return
    }
    if (!repeatTimer.current) repeatTimer.current = setInterval(playChime, REPEAT_MS)
  }, [waiting, silenced])

  useEffect(() => {
    ensureNotificationPermission()
    let cancelled = false

    async function poll() {
      let list
      try {
        list = await api('/barangay/alerts?scope=active')
      } catch (err) {
        if (err.message.includes('expired')) onSessionLost()
        return
      }
      if (cancelled || !Array.isArray(list)) return

      setWaiting(waitingOn(list))
      const ids = new Set(list.map((a) => a.sync_id))
      if (knownIds.current === null) {
        knownIds.current = ids // first load after a page open -- adopt silently
        return
      }

      const fresh = list.filter((a) => !knownIds.current.has(a.sync_id))
      knownIds.current = ids
      if (fresh.length === 0) return

      playChime()
      unseenCount.current += fresh.length
      document.title = `(${unseenCount.current}) ${baseTitle.current}`

      if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        const note =
          fresh.length === 1
            ? new Notification('New alert — welfare check needed', {
                body: `${fresh[0].senior_name} · ${triggerLabel(fresh[0].trigger_type)}`,
                tag: 'seenior-alert',
                renotify: true,
              })
            : new Notification(`${fresh.length} new alerts`, {
                body: 'Open the Alerts tab for details.',
                tag: 'seenior-alert',
                renotify: true,
              })
        note.onclick = () => {
          window.focus()
          goToAlerts.current?.()
          note.close()
        }
      }
    }

    poll()
    const timer = setInterval(poll, POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [onSessionLost])

  // Coming back to the tab means the responder has seen the queue -- drop the badge.
  useEffect(() => {
    function clearBadge() {
      if (document.visibilityState === 'visible') {
        unseenCount.current = 0
        document.title = baseTitle.current
      }
    }
    document.addEventListener('visibilitychange', clearBadge)
    window.addEventListener('focus', clearBadge)
    return () => {
      document.removeEventListener('visibilitychange', clearBadge)
      window.removeEventListener('focus', clearBadge)
    }
  }, [])

  if (waiting.length === 0) return null
  const first = waiting[0]
  return (
    <div className="urgent-banner" role="alert">
      <div className="urgent-banner-text">
        <strong>
          {urgentLabel(first)}: {first.senior_name}
          {first.senior_pook ? ` · Pook ${first.senior_pook}` : ''}
        </strong>
        <span>
          {' · '}Not attended yet
          {waiting.length > 1 ? ` · ${waiting.length - 1} more waiting` : ''}
        </span>
      </div>
      <div className="urgent-banner-btns">
        <button type="button" className="urgent-banner-btn" onClick={() => goToAlerts.current?.()}>
          Open alerts
        </button>
        <button type="button" className="urgent-banner-btn" onClick={silence} disabled={silenced}>
          {silenced ? 'Silenced for 1 min' : 'Silence 1 min'}
        </button>
      </div>
    </div>
  )
}

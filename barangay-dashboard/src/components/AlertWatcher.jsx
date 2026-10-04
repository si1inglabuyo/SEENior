import { useEffect, useRef } from 'react'
import { api, POLL_MS } from '../api'
import { triggerLabel } from '../labels'

// Always mounted while signed in. Polls the active queue and, when a new incident appears,
// alerts the responder with a chime, a browser notification (if permitted) and a count in
// the tab title. The first poll after a page load adopts existing alerts silently.

function ensureNotificationPermission() {
  if (typeof Notification === 'undefined') return
  if (Notification.permission === 'default') {
    Notification.requestPermission().catch(() => {})
  }
}

// Two rising tones. Wrapped in try/catch because autoplay policy may block audio until the
// page has had a user gesture; the notification and title badge still fire.
function playChime() {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const start = ctx.currentTime
    ;[880, 1174].forEach((freq, i) => {
      const t = start + i * 0.18
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      gain.gain.setValueAtTime(0.0001, t)
      gain.gain.exponentialRampToValueAtTime(0.2, t + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.16)
      osc.connect(gain).connect(ctx.destination)
      osc.start(t)
      osc.stop(t + 0.18)
    })
    setTimeout(() => ctx.close().catch(() => {}), 900)
  } catch {
    /* audio unavailable -- the other two channels still fire */
  }
}

export default function AlertWatcher({ onSessionLost, onGoToAlerts }) {
  const knownIds = useRef(null) // Set<sync_id> from the previous poll; null until first load
  const unseenCount = useRef(0) // new alerts not yet looked at, for the tab-title badge
  const baseTitle = useRef(typeof document !== 'undefined' ? document.title : 'SEENior')
  const goToAlerts = useRef(onGoToAlerts)
  useEffect(() => {
    goToAlerts.current = onGoToAlerts
  }, [onGoToAlerts])

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

  return null
}

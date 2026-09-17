import { useEffect, useRef } from 'react'

// A barangay-hall PC is often shared and rarely locked. Leaving a responder session (and
// the senior PII it can reach) open indefinitely is the access-control gap RA 10173 §23(b)
// points at, so the dashboard signs itself out after a stretch of no interaction. The
// responder signs back in with the OSCA-issued credentials; nothing is lost.
const IDLE_MS = 20 * 60 * 1000 // 20 minutes
const CHECK_MS = 30 * 1000

export function useIdleLogout(onIdle, enabled) {
  const onIdleRef = useRef(onIdle)
  useEffect(() => {
    onIdleRef.current = onIdle
  }, [onIdle])

  useEffect(() => {
    if (!enabled) return undefined

    let lastActivity = Date.now()
    const bump = () => {
      lastActivity = Date.now()
    }
    const events = ['mousemove', 'mousedown', 'keydown', 'wheel', 'touchstart']
    events.forEach((name) => window.addEventListener(name, bump, { passive: true }))

    const timer = setInterval(() => {
      if (Date.now() - lastActivity >= IDLE_MS) onIdleRef.current()
    }, CHECK_MS)

    return () => {
      events.forEach((name) => window.removeEventListener(name, bump))
      clearInterval(timer)
    }
  }, [enabled])
}

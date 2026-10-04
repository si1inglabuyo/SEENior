import { useEffect, useRef } from 'react'

// Barangay-hall PCs are often shared, so the dashboard signs out after a stretch of no
// interaction (RA 10173 section 23(b)).
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

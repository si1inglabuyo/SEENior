import { useEffect, useState } from 'react'
import Sidebar from './Sidebar'

// The header subtitle in the design is a live clock ("Wednesday, June 4, 2026 · 9:14 AM").
// One interval, updated once a minute -- seconds would just be visual noise.
function useNow() {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30000)
    return () => clearInterval(timer)
  }, [])
  return now
}

export default function AppShell({ title, view, onNavigate, onSignOut, children }) {
  const now = useNow()
  const when = `${now.toLocaleDateString(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })} · ${now.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}`

  return (
    <div className="shell">
      <Sidebar view={view} onNavigate={onNavigate} onSignOut={onSignOut} />
      <div className="main">
        <header className="page-header">
          <h1 className="page-title">{title}</h1>
          <p className="page-when">{when}</p>
        </header>
        <div className="page-body">{children}</div>
        <footer className="page-footer">©2026 SEENior. All Rights Reserved</footer>
      </div>
    </div>
  )
}

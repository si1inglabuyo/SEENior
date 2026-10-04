import { useCallback, useState } from 'react'
import { clearToken, getToken } from './api'
import { useIdleLogout } from './hooks/useIdleLogout'
import Login from './components/Login'
import AppShell from './components/AppShell'
import AlertWatcher from './components/AlertWatcher'
import Dashboard from './components/Dashboard'
import IncidentQueue from './components/IncidentQueue'
import AlertHistory from './components/AlertHistory'
import SeniorRoster from './components/SeniorRoster'
import Settings from './components/Settings'

// No router library: the view is one piece of state. Five keys, matching Sidebar's NAV.
const TITLES = {
  dashboard: 'Dashboard',
  alerts: 'Alerts',
  history: 'Alert History',
  seniors: 'Seniors',
  settings: 'Settings',
}

export default function App() {
  const [token, setToken] = useState(getToken())
  const [view, setView] = useState('dashboard')
    // The stat card that was clicked, e.g. { when: 'today', label: 'Resolved Today' }.
    // Kept in memory (no router); AlertHistory reads it on mount to pre-apply the filter.
  const [navFilter, setNavFilter] = useState(null)

  const signOut = useCallback(() => {
    clearToken()
    setToken(null)
  }, [])

    // Sign out after a stretch of no interaction, since this may be a shared PC.
  useIdleLogout(signOut, !!token)

  if (!token) return <Login onSignedIn={() => setToken(getToken())} />

    // Sidebar clicks navigate with no filter; stat cards pass one. One function keeps
    // `view` and `navFilter` in step.
  function navigate(nextView, filter = null) {
    setNavFilter(filter)
    setView(nextView)
  }

  return (
    <AppShell title={TITLES[view]} view={view} onNavigate={navigate} onSignOut={signOut}>
      <AlertWatcher onSessionLost={signOut} onGoToAlerts={() => navigate('alerts')} />
      {view === 'dashboard' && <Dashboard onSessionLost={signOut} onNavigate={navigate} />}
      {view === 'alerts' && <IncidentQueue onSessionLost={signOut} />}
      {view === 'history' && (
        <AlertHistory
            // Remount when the drill-down changes so the filters re-seed.
          key={navFilter ? JSON.stringify(navFilter) : 'history'}
          onSessionLost={signOut}
          navFilter={navFilter}
          onClearFilter={() => setNavFilter(null)}
        />
      )}
      {view === 'seniors' && <SeniorRoster onSessionLost={signOut} />}
      {view === 'settings' && <Settings onSessionLost={signOut} />}
    </AppShell>
  )
}

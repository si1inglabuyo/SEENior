import { useState } from 'react'
import { clearToken, getToken } from './api'
import Login from './components/Login'
import AppShell from './components/AppShell'
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
  // What a dashboard stat card was clicked for -- e.g. { status: 'resolved', when: 'today',
  // label: 'Resolved Today' }. With no router this rides along as in-memory navigation
  // state (the job a query param would do); AlertHistory reads it on mount and pre-applies
  // the matching filter.
  const [navFilter, setNavFilter] = useState(null)

  if (!token) return <Login onSignedIn={() => setToken(getToken())} />

  const signOut = () => {
    clearToken()
    setToken(null)
  }

  // Sidebar clicks navigate with no filter (an explicit "show me everything"); the
  // dashboard's stat cards pass one. Both go through this single function so `view` and
  // `navFilter` can never fall out of step with each other.
  function navigate(nextView, filter = null) {
    setNavFilter(filter)
    setView(nextView)
  }

  return (
    <AppShell title={TITLES[view]} view={view} onNavigate={navigate} onSignOut={signOut}>
      {view === 'dashboard' && <Dashboard onSessionLost={signOut} onNavigate={navigate} />}
      {view === 'alerts' && <IncidentQueue onSessionLost={signOut} />}
      {view === 'history' && (
        <AlertHistory
          // Remount when the drill-down intent changes so the filters re-seed from the new
          // navFilter -- clicking a different stat card, or the sidebar item (navFilter null).
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

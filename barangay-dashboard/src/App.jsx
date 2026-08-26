import { useState } from 'react'
import { clearToken, getToken } from './api'
import Login from './components/Login'
import IncidentQueue from './components/IncidentQueue'
import SeniorRoster from './components/SeniorRoster'
import Analytics from './components/Analytics'

const TABS = [
  { id: 'incidents', label: 'Incidents' },
  { id: 'seniors', label: 'Senior Records' },
  { id: 'analytics', label: 'Analytics' },
]

// No router library: three tabs held in one piece of state is less code and one less
// dependency than react-router would be.
export default function App() {
  const [token, setToken] = useState(getToken())
  const [tab, setTab] = useState('incidents')

  if (!token) return <Login onSignedIn={() => setToken(getToken())} />

  const signOut = () => {
    clearToken()
    setToken(null)
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">SEENior</span>
          <span className="brand-sub">Barangay Responder Dashboard</span>
        </div>
        <nav className="tabs">
          {TABS.map((item) => (
            <button
              key={item.id}
              className={item.id === tab ? 'tab active' : 'tab'}
              onClick={() => setTab(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <button className="signout" onClick={signOut}>Sign out</button>
      </header>

      <main className="content">
        {tab === 'incidents' && <IncidentQueue onSessionLost={signOut} />}
        {tab === 'seniors' && <SeniorRoster onSessionLost={signOut} />}
        {tab === 'analytics' && <Analytics onSessionLost={signOut} />}
      </main>
    </div>
  )
}

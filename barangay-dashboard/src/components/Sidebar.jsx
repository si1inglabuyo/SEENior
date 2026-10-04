import {
  IconDashboard,
  IconAlerts,
  IconHistory,
  IconSeniors,
  IconSettings,
  IconLogout,
} from '../icons'
import logoUrl from '../assets/logo.png'

// The nav, shared by every screen. `id` matches the view keys in App.jsx.
const NAV = [
  { id: 'dashboard', label: 'Dashboard', Icon: IconDashboard },
  { id: 'alerts', label: 'Alerts', Icon: IconAlerts },
  { id: 'history', label: 'Alert History', Icon: IconHistory },
  { id: 'seniors', label: 'Seniors', Icon: IconSeniors },
  { id: 'settings', label: 'Settings', Icon: IconSettings },
]

function LogoMark() {
  return <img src={logoUrl} className="logo-mark" alt="SEENior" />
}

export default function Sidebar({ view, onNavigate, onSignOut }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <LogoMark />
        <div>
          <p className="sidebar-word">SEENior</p>
          <p className="sidebar-tag">Senior Monitoring System</p>
        </div>
      </div>

      <nav className="sidebar-nav">
        {NAV.map(({ id, label, Icon }) => (
          <button
            key={id}
            type="button"
            className={id === view ? 'nav-item active' : 'nav-item'}
            aria-current={id === view ? 'page' : undefined}
            onClick={() => onNavigate(id)}
          >
            <Icon className="nav-icon" />
            <span>{label}</span>
          </button>
        ))}
      </nav>

      <button type="button" className="nav-item nav-logout" onClick={onSignOut}>
        <IconLogout className="nav-icon" />
        <span>Logout</span>
      </button>
    </aside>
  )
}

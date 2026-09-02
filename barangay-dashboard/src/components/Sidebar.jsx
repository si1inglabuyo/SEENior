import {
  IconDashboard,
  IconAlerts,
  IconHistory,
  IconSeniors,
  IconSettings,
  IconLogout,
} from '../icons'

// The nav is the same on every screen, so it lives in one place. `id` matches the view
// keys App.jsx switches on.
const NAV = [
  { id: 'dashboard', label: 'Dashboard', Icon: IconDashboard },
  { id: 'alerts', label: 'Alerts', Icon: IconAlerts },
  { id: 'history', label: 'Alert History', Icon: IconHistory },
  { id: 'seniors', label: 'Seniors', Icon: IconSeniors },
  { id: 'settings', label: 'Settings', Icon: IconSettings },
]

function LogoMark() {
  // A plain stand-in mark: an orange house inside a two-tone care arc. Swap for the real
  // artwork when it lands as an asset.
  return (
    <svg viewBox="0 0 40 40" className="logo-mark" aria-hidden="true">
      <path
        d="M6 20a14 14 0 0 1 14-14"
        fill="none"
        stroke="#3f9c6d"
        strokeWidth="4"
        strokeLinecap="round"
      />
      <path
        d="M34 20a14 14 0 0 1-14 14"
        fill="none"
        stroke="#e08a3c"
        strokeWidth="4"
        strokeLinecap="round"
      />
      <path
        d="M20 11l8 7v10a1 1 0 0 1-1 1h-4v-6h-6v6h-4a1 1 0 0 1-1-1V18z"
        fill="#e08a3c"
      />
    </svg>
  )
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

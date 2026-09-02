// One figure on the dashboard's top row. `tone` colours the sub-line: 'up' green, 'down'
// red, 'neutral' muted. When `onClick` is passed the whole card becomes a button that
// drills into the alert list behind the number (see Dashboard.jsx's onNavigate calls).
export default function StatCard({ label, value, icon, sub, tone = 'neutral', onClick }) {
  const body = (
    <>
      <div className="stat-head">
        <span className="stat-label">{label}</span>
        <span className="stat-icon">{icon}</span>
      </div>
      <span className="stat-value">{value}</span>
      {sub != null && <span className={`stat-sub stat-sub-${tone}`}>{sub}</span>}
    </>
  )

  if (!onClick) return <div className="stat-card">{body}</div>

  return (
    <button type="button" className="stat-card stat-card-link" onClick={onClick}>
      {body}
    </button>
  )
}

// One figure on the top row. `tone` colours the sub-line (up green, down red, neutral
// muted). With `onClick` the card opens the alert list behind the number.
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

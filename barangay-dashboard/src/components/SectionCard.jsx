// The orange-header card used on the dashboard, defined once so they stay identical.
export default function SectionCard({ icon, title, action, children, className = '' }) {
  return (
    <section className={`section-card ${className}`.trim()}>
      <div className="section-bar">
        <span className="section-bar-title">
          {icon}
          {title}
        </span>
        {action && <span className="section-bar-action">{action}</span>}
      </div>
      <div className="section-body">{children}</div>
    </section>
  )
}

import { TIERS, dispatchOf } from '../dispatch'

// The top of the Dashboard tab: how the open alerts split across the priority tiers. Orange
// while any still needs someone.
export default function TodayBanner({ alerts }) {
  // Nothing to report with no open alerts, so the banner is not shown at all.
  if (alerts.length === 0) return null

  const states = alerts.map(dispatchOf)
  const waiting = states.filter((s) => s.tone !== 'green').length
  const byTier = TIERS.map(({ tier, label }) => ({
    tier,
    label,
    count: states.filter((s) => s.tier === tier).length,
  }))

  return (
    <section className={`today-banner${waiting ? ' has-waiting' : ''}`}>
      <div className="today-banner-text">
        <ul className="today-banner-tiers">
          {byTier.map(({ tier, label, count }) => (
            <li key={tier} className={count ? '' : 'is-zero'} title={label}>
              <span className={`tier-badge tier-badge-${tier}`}>Tier {tier}</span>
              <span>
                {label} <b>{count}</b>
              </span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

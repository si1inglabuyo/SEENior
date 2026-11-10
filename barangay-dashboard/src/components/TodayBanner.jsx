import { TIERS, dispatchOf } from '../dispatch'

// The top of the Dashboard tab: how many alerts are open, how many still need someone, and how
// they split across the priority tiers.
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

  let headline
  if (waiting === 0) headline = `All ${alerts.length} open ${alerts.length === 1 ? 'alert is' : 'alerts are'} covered.`
  else headline = `${waiting} of ${alerts.length} open ${alerts.length === 1 ? 'alert needs' : 'alerts need'} a responder.`

  return (
    <section className={`today-banner${waiting ? ' has-waiting' : ''}`}>
      <div className="today-banner-text">
        <p className="today-banner-headline">{headline}</p>
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

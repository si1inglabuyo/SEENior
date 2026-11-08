import { TIERS, dispatchOf } from '../dispatch'
import { IconChart } from '../icons'
import SectionCard from './SectionCard'

const GROUPS = [
  { title: 'Send a team', tiers: TIERS.filter((t) => !t.callFirst) },
  { title: 'Call first', tiers: TIERS.filter((t) => t.callFirst) },
]

// Same colours as the pook grid: red needs a team, amber needs a call, green is covered.
function toneOf(states) {
  for (const tone of ['red', 'amber', 'green']) {
    if (states.some((s) => s.tone === tone)) return tone
  }
  return null
}

function statusText(states) {
  const waiting = states.filter((s) => s.tone !== 'green').length
  if (states.length === 0) return 'None open'
  return waiting ? `${waiting} waiting` : 'All covered'
}

function TierTile({ tier, label, states, selected, onSelect }) {
  const tone = toneOf(states)
  return (
    <button
      type="button"
      className={`tier-tile ${tone ? `tier-tile-${tone}` : ''} ${selected ? 'is-selected' : ''}`}
      aria-pressed={selected}
      onClick={() => onSelect(selected ? undefined : tier)}
    >
      <span className="tier-tile-top">
        <span className={`tier-badge tier-badge-${tier}`}>Tier {tier}</span>
        {tone && <span className="pook-count">{states.length}</span>}
      </span>
      <span className="tier-tile-label">{label}</span>
      <span className="tier-tile-status">{statusText(states)}</span>
    </button>
  )
}

// The order the barangay works through alerts, grouped by what the operator does first. Each
// tile shows how that tier is going, and clicking one narrows the list to it.
export default function TierGuide({ alerts, selected, onSelect }) {
  const states = alerts.map(dispatchOf)
  return (
    <SectionCard
      icon={<IconChart />}
      title="Priority Tiers"
      action={<span className="pook-grid-hint">Most urgent first. Click a tier to see only its alerts</span>}
    >
      <div className="tier-guide">
        {GROUPS.map((group) => (
          <section className="tier-group" key={group.title}>
            <div className="tier-group-tiles">
              {group.tiers.map(({ tier, label }) => (
                <TierTile
                  key={tier}
                  tier={tier}
                  label={label}
                  states={states.filter((s) => s.tier === tier)}
                  selected={selected === tier}
                  onSelect={onSelect}
                />
              ))}
            </div>
          </section>
        ))}
      </div>
    </SectionCard>
  )
}

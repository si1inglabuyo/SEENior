import { SECTOR_LABEL } from '../labels'
import { pookSummary } from '../dispatch'
import { IconSeniors } from '../icons'
import SectionCard from './SectionCard'

const TONE_TEXT = {
  red: 'someone still needs a team',
  amber: 'a call is still to be made',
  green: 'all covered',
}

function PookTile({ name, label = name, stat, selected, onSelect }) {
  const count = stat?.count ?? 0
  const alerts = `${count} open ${count === 1 ? 'alert' : 'alerts'}`
  return (
    <button
      type="button"
      className={`pook-tile ${stat ? `pook-tile-${stat.tone}` : ''} ${selected ? 'is-selected' : ''}`}
      aria-pressed={selected}
      aria-label={stat ? `${label}: ${alerts}, ${TONE_TEXT[stat.tone]}` : `${label}: no open alerts`}
      onClick={() => onSelect(selected ? undefined : name)}
    >
      <span>{label}</span>
      {stat && <span className="pook-count">{count}</span>}
    </button>
  )
}

// All of the barangay's pooks at a glance, coloured by how the response to their open alerts
// is going. `selected` is a pook name, null for "No pook set", or undefined for all of them.
export default function PookGrid({ pooks, alerts, selected, onSelect }) {
  const stats = pookSummary(alerts)
  const sectors = Object.keys(SECTOR_LABEL)
    .map((sector) => ({ sector, pooks: pooks.filter((p) => p.sector === sector) }))
    .filter((group) => group.pooks.length > 0)

  return (
    <SectionCard
      icon={<IconSeniors />}
      title="Pooks with Active Alerts"
      action={<span className="pook-grid-hint">Click a pook to see only its alerts</span>}
    >
      {sectors.map((group) => (
        <div className="pook-sector" key={group.sector}>
          <h4>{SECTOR_LABEL[group.sector]}</h4>
          <div className="pook-grid">
            {group.pooks.map((pook) => (
              <PookTile
                key={pook.id}
                name={pook.name}
                stat={stats.get(pook.name)}
                selected={selected === pook.name}
                onSelect={onSelect}
              />
            ))}
          </div>
        </div>
      ))}
      {stats.has(null) && (
        <div className="pook-sector">
          <h4>Not in a pook</h4>
          <div className="pook-grid">
            <PookTile
              name={null}
              label="No pook set"
              stat={stats.get(null)}
              selected={selected === null}
              onSelect={onSelect}
            />
          </div>
        </div>
      )}
      <ul className="pook-legend">
        <li className="pook-legend-red">Needs a team</li>
        <li className="pook-legend-amber">Call first</li>
        <li className="pook-legend-green">Covered</li>
      </ul>
    </SectionCard>
  )
}

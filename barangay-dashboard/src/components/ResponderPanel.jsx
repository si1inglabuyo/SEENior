import { useState } from 'react'
import { resourcesOut } from '../dispatch'
import { IconBuilding } from '../icons'
import ResponderManager from './ResponderManager'
import SectionCard from './SectionCard'

// Who the barangay still has free. Worked out from the open alerts, so a team is available
// again as soon as its alert closes. Anyone out stays listed even if this browser's list does
// not have them, because another computer may have sent them.
export default function ResponderPanel({ alerts, responders, pooks, onSavePook }) {
  const [managing, setManaging] = useState(false)
  const out = resourcesOut(alerts)
  const names = [...new Set([...responders.teams, ...responders.services, ...Object.keys(out)])]
  const free = names.filter((name) => !(name in out)).length
  return (
    <SectionCard
      icon={<IconBuilding />}
      title="Responders"
      action={<span className="pook-grid-hint">{free} of {names.length} free</span>}
      className="responder-panel"
    >
      {names.length === 0 && <p className="muted responder-none">No responders yet.</p>}
      <ul className="responder-list">
        {names.map((name) => (
          <li key={name}>
            <span>{name}</span>
            {name in out ? (
              <span className="responder-out">Out · {out[name]}</span>
            ) : (
              <span className="responder-free">Available</span>
            )}
          </li>
        ))}
      </ul>
      <button type="button" className="btn-outline responder-manage" onClick={() => setManaging(true)}>
        Manage responders
      </button>
      {managing && (
        <ResponderManager
          responders={responders}
          out={out}
          pooks={pooks}
          onSavePook={onSavePook}
          onClose={() => setManaging(false)}
        />
      )}
    </SectionCard>
  )
}

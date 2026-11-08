import { useState } from 'react'
import { resourcesOut } from '../dispatch'
import { IconBuilding } from '../icons'
import { PookRow } from './PookResponders'
import ResponderManager from './ResponderManager'
import SectionCard from './SectionCard'

// Each pook with its designated responder and number, editable by whoever monitors the dashboard.
// A responder who is out on an open alert shows as out, worked out from the open alerts so they
// are free again as soon as that alert closes.
export default function ResponderPanel({ alerts, responders, pooks, onSavePook }) {
  const [managing, setManaging] = useState(false)
  const out = resourcesOut(alerts)
  return (
    <SectionCard
      icon={<IconBuilding />}
      title="Responders"
      action={<span className="pook-grid-hint">One per pook. Edit to change</span>}
      className="responder-panel"
    >
      {!pooks || pooks.length === 0 ? (
        <p className="muted responder-none">No pooks loaded yet.</p>
      ) : (
        <ul className="responder-list responder-pooks">
          {pooks.map((pook) => (
            <PookRow
              key={pook.id}
              pook={pook}
              onSave={onSavePook}
              outAt={pook.responder_name ? out[pook.responder_name] : undefined}
            />
          ))}
        </ul>
      )}
      <button type="button" className="btn-outline responder-manage" onClick={() => setManaging(true)}>
        Manage hall teams
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

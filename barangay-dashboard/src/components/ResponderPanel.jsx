import { useState } from 'react'
import { resourcesOut } from '../dispatch'
import { IconBuilding } from '../icons'
import Modal from './Modal'
import PookResponders from './PookResponders'
import ResponderManager, { ResponderGroup } from './ResponderManager'
import SectionCard from './SectionCard'

// A short card on the side; the full pook-by-pook list opens in a popup so it does not stretch
// the page. Whoever monitors the dashboard edits each pook's responder and number there.
export default function ResponderPanel({ alerts, responders, pooks, onSavePook }) {
  const [listOpen, setListOpen] = useState(false)
  const [managing, setManaging] = useState(false)
  const out = resourcesOut(alerts)
  const list = pooks ?? []
  const set = list.filter((p) => p.responder_name).length
  const outNow = list.filter((p) => p.responder_name && p.responder_name in out).length
  return (
    <SectionCard icon={<IconBuilding />} title="Pook Responders" className="responder-panel">
      {list.length === 0 ? (
        <p className="muted responder-none">No pooks loaded yet.</p>
      ) : (
        <p className="responder-summary">
          <strong>{set}</strong> of {list.length} pooks have a responder
          {outNow > 0 && <span className="responder-out"> · {outNow} out now</span>}
        </p>
      )}
      <button
        type="button"
        className="btn-outline responder-manage"
        disabled={list.length === 0}
        onClick={() => setListOpen(true)}
      >
        View and edit responders
      </button>
      <button type="button" className="link-btn" onClick={() => setManaging(true)}>
        Manage hall teams
      </button>

      {listOpen && (
        <Modal onClose={() => setListOpen(false)} labelledBy="pook-responders-title" className="responder-manager">
          <button type="button" className="modal-close" onClick={() => setListOpen(false)} aria-label="Close">
            &times;
          </button>
          <h3 id="pook-responders-title">Pook Responders</h3>
          <PookResponders pooks={list} onSave={onSavePook} out={out} />
          {/* The hall's own people, sent when a pook's responder is out or cannot come. */}
          <ResponderGroup
            title="Tanod and volunteers"
            addLabel="Add a tanod or volunteer"
            names={responders.teams}
            out={out}
            onAdd={(name) => responders.add('teams', name)}
            onRename={(oldName, name) => responders.rename('teams', oldName, name)}
            onRemove={(name) => responders.remove('teams', name)}
          />
          <p className="muted responder-note">
            Tanod and volunteers are saved in this browser only, so other computers keep their own list.
          </p>
        </Modal>
      )}
      {managing && (
        <ResponderManager
          responders={responders}
          out={out}
          pooks={null}
          onSavePook={null}
          onClose={() => setManaging(false)}
        />
      )}
    </SectionCard>
  )
}

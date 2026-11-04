import { useState } from 'react'
import { MAX_RESPONDER_NAME } from '../dispatch'
import Modal from './Modal'

function ResponderRow({ name, outAt, onRename, onRemove }) {
  const [draft, setDraft] = useState(null)
  const [problem, setProblem] = useState('')

  function save(event) {
    event.preventDefault()
    const refused = onRename(draft)
    setProblem(refused)
    if (!refused) setDraft(null)
  }

  if (draft !== null) {
    return (
      <li>
        <form className="responder-edit" onSubmit={save}>
          <input
            aria-label={`New name for ${name}`}
            value={draft}
            maxLength={MAX_RESPONDER_NAME}
            autoFocus
            onChange={(e) => setDraft(e.target.value)}
          />
          <button type="submit" className="btn-outline">Save</button>
          <button type="button" className="btn-outline" onClick={() => { setDraft(null); setProblem('') }}>
            Cancel
          </button>
        </form>
        {problem && <p className="error responder-problem">{problem}</p>}
      </li>
    )
  }

  return (
    <li>
      <div className="responder-edit">
        <span className="responder-name">{name}</span>
        {outAt ? (
          <span className="responder-out">Out · {outAt}</span>
        ) : (
          <>
            <button type="button" className="btn-outline" onClick={() => setDraft(name)}>Rename</button>
            <button type="button" className="btn-outline" onClick={onRemove}>Delete</button>
          </>
        )}
      </div>
    </li>
  )
}

function ResponderGroup({ title, addLabel, names, out, onAdd, onRename, onRemove }) {
  const [draft, setDraft] = useState('')
  const [problem, setProblem] = useState('')

  function add(event) {
    event.preventDefault()
    const refused = onAdd(draft)
    setProblem(refused)
    if (!refused) setDraft('')
  }

  return (
    <section className="responder-group">
      <h4>{title}</h4>
      {names.length > 0 ? (
        <ul className="responder-list">
          {names.map((name) => (
            <ResponderRow
              key={name}
              name={name}
              outAt={out[name]}
              onRename={(next) => onRename(name, next)}
              onRemove={() => onRemove(name)}
            />
          ))}
        </ul>
      ) : (
        <p className="muted responder-none">None yet.</p>
      )}
      <form className="responder-edit" onSubmit={add}>
        <input
          aria-label={addLabel}
          placeholder={addLabel}
          value={draft}
          maxLength={MAX_RESPONDER_NAME}
          onChange={(e) => setDraft(e.target.value)}
        />
        <button type="submit" className="btn-outline">Add</button>
      </form>
      {problem && <p className="error responder-problem">{problem}</p>}
    </section>
  )
}

// Add, rename or delete who the barangay can send. Anyone out on an alert stays locked until
// that alert closes, because the timeline and the Responders panel find them by name.
export default function ResponderManager({ responders, out, onClose }) {
  const { teams, services, add, rename, remove } = responders
  return (
    <Modal onClose={onClose} labelledBy="responder-manager-title" className="responder-manager">
      <button type="button" className="modal-close" onClick={onClose} aria-label="Close">
        &times;
      </button>
      <h3 id="responder-manager-title">Manage responders</h3>
      <ResponderGroup
        title="Hall teams"
        addLabel="Add a hall team"
        names={teams}
        out={out}
        onAdd={(name) => add('teams', name)}
        onRename={(oldName, name) => rename('teams', oldName, name)}
        onRemove={(name) => remove('teams', name)}
      />
      <ResponderGroup
        title="Outside help"
        addLabel="Add outside help"
        names={services}
        out={out}
        onAdd={(name) => add('services', name)}
        onRename={(oldName, name) => rename('services', oldName, name)}
        onRemove={(name) => remove('services', name)}
      />
      <p className="muted responder-note">
        Changes are saved in this browser only, so other computers keep their own list.
      </p>
    </Modal>
  )
}

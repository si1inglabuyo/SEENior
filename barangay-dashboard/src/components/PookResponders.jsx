import { useState } from 'react'
import { MAX_RESPONDER_NAME } from '../dispatch'

// One pook's designated responder: who the hall staff phone for an alert from that area.
export function PookRow({ pook, onSave, outAt }) {
  const [draft, setDraft] = useState(null)
  const [problem, setProblem] = useState('')
  const [busy, setBusy] = useState(false)

  async function save(event) {
    event.preventDefault()
    setBusy(true)
    const refused = await onSave(pook.id, draft.name, draft.phone)
    setBusy(false)
    setProblem(refused)
    // A refusal keeps the form open so the typing is not lost.
    if (!refused) setDraft(null)
  }

  return (
    <li>
      {draft === null ? (
        <div className="pook-responder-row">
          <div className="pook-responder-who">
            <span className="responder-name">{pook.name}</span>
            {pook.responder_name ? (
              <span className="pook-responder-detail">
                {pook.responder_name} · <strong>{pook.responder_phone}</strong>
                {outAt && <span className="responder-out"> Out · {outAt}</span>}
              </span>
            ) : (
              <span className="pook-responder-detail muted">No responder set</span>
            )}
          </div>
          <button
            type="button"
            className="btn-outline"
            onClick={() => {
              setProblem('')
              setDraft({ name: pook.responder_name ?? '', phone: pook.responder_phone ?? '' })
            }}
          >
            Edit
          </button>
        </div>
      ) : (
        <form className="pook-responder-form" onSubmit={save}>
          <span className="responder-name">{pook.name}</span>
          <input
            aria-label={`Responder name for ${pook.name}`}
            placeholder="Responder name"
            value={draft.name}
            maxLength={MAX_RESPONDER_NAME}
            autoFocus
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
          <input
            aria-label={`Responder phone number for ${pook.name}`}
            placeholder="Phone number"
            inputMode="tel"
            value={draft.phone}
            maxLength={32}
            onChange={(e) => setDraft({ ...draft, phone: e.target.value })}
          />
          <div className="responder-edit">
            <button type="submit" className="btn-outline" disabled={busy}>Save</button>
            <button type="button" className="btn-outline" disabled={busy} onClick={() => setDraft(null)}>
              Cancel
            </button>
            <button
              type="button"
              className="btn-outline"
              disabled={busy}
              onClick={() => setDraft({ name: '', phone: '' })}
            >
              Clear
            </button>
          </div>
          {problem && <p className="error responder-problem">{problem}</p>}
        </form>
      )}
    </li>
  )
}

// The pook-by-pook list inside Manage responders. Leaving both fields blank and saving clears
// the pook's responder.
export default function PookResponders({ pooks, onSave }) {
  return (
    <section className="responder-group">
      <h4>Pook responders</h4>
      <p className="muted responder-note">
        The person to phone first for an alert from each pook. Saved for every computer.
      </p>
      <ul className="responder-list">
        {pooks.map((pook) => (
          <PookRow key={pook.id} pook={pook} onSave={onSave} />
        ))}
      </ul>
    </section>
  )
}

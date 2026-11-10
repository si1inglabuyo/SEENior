import { useState } from 'react'
import { MAX_RESPONDER_NAME } from '../dispatch'
import { SECTOR_LABEL } from '../labels'

// The names migration 0022 seeded ("Responder, Aguinaldo") until the barangay gives real ones.
const isPlaceholder = (name) => /^Responder, /.test(name ?? '')

// Demo names for the consultation, put in by the button below in place of the placeholders.
// Fictional, and the 0900 000 xxxx numbers they keep are not real subscriber numbers.
const DEMO_NAMES = [
  'Maria Santos', 'Jose Reyes', 'Ana Cruz', 'Ramon Bautista',
  'Liza Garcia', 'Eduardo Mendoza', 'Teresita Aquino', 'Roberto Villanueva',
  'Carmela Ramos', 'Antonio Castillo', 'Rosario Flores', 'Danilo Navarro',
  'Gloria Torres', 'Ernesto Domingo', 'Marites Salazar', 'Fernando Dizon',
]

// "09000000007" -> "0900 000 0007"; anything else as typed.
const groupPhone = (value) =>
  /^09\d{9}$/.test(value ?? '') ? `${value.slice(0, 4)} ${value.slice(4, 7)} ${value.slice(7)}` : value

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

  if (draft === null) {
    return (
      <li className="pr-row">
        <span className="pr-pook">{pook.name}</span>
        {pook.responder_name ? (
          <span className="pr-who">
            {pook.responder_name}
            {outAt && <span className="responder-out"> · Out</span>}
          </span>
        ) : (
          <span className="pr-who muted">No responder set</span>
        )}
        <strong className="pr-phone">{groupPhone(pook.responder_phone) || ''}</strong>
        <button
          type="button"
          className="pr-edit"
          onClick={() => {
            setProblem('')
            setDraft({ name: pook.responder_name ?? '', phone: pook.responder_phone ?? '' })
          }}
        >
          Edit
        </button>
      </li>
    )
  }

  return (
    <li className="pr-row pr-row-editing">
      <form className="pr-form" onSubmit={save}>
        <span className="pr-pook">{pook.name}</span>
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
        <div className="pr-form-btns">
          <button type="submit" className="btn-outline" disabled={busy}>Save</button>
          <button type="button" className="btn-outline" disabled={busy} onClick={() => setDraft(null)}>
            Cancel
          </button>
          <button
            type="button"
            className="link-btn-plain pr-clear"
            disabled={busy}
            onClick={() => setDraft({ name: '', phone: '' })}
          >
            Clear
          </button>
        </div>
      </form>
      {problem && <p className="error responder-problem">{problem}</p>}
    </li>
  )
}

// The pook-by-pook list inside Manage responders, grouped by sector. Leaving both fields blank
// and saving clears the pook's responder.
export default function PookResponders({ pooks, onSave, out = {} }) {
  const [filling, setFilling] = useState(false)
  const [fillProblem, setFillProblem] = useState('')
  const placeholders = pooks.filter((p) => isPlaceholder(p.responder_name))

  async function fillDemoNames() {
    setFilling(true)
    setFillProblem('')
    for (const [i, pook] of placeholders.entries()) {
      const refused = await onSave(pook.id, DEMO_NAMES[i % DEMO_NAMES.length], pook.responder_phone ?? '')
      if (refused) {
        setFillProblem(`${pook.name}: ${refused}`)
        break
      }
    }
    setFilling(false)
  }

  const sectors = Object.keys(SECTOR_LABEL)
    .map((sector) => ({ sector, pooks: pooks.filter((p) => p.sector === sector) }))
    .filter((group) => group.pooks.length > 0)
  const other = pooks.filter((p) => !SECTOR_LABEL[p.sector])
  if (other.length) sectors.push({ sector: 'other', pooks: other })

  return (
    <section className="responder-group">
      <p className="muted responder-note">
        The person to phone first for an alert from each pook. Saved for every computer.
      </p>

      {placeholders.length > 0 && (
        <div className="pr-placeholder">
          <span>
            {placeholders.length} {placeholders.length === 1 ? 'pook still has' : 'pooks still have'} a
            placeholder name.
          </span>
          <button type="button" className="btn-outline" disabled={filling} onClick={fillDemoNames}>
            {filling ? 'Filling…' : 'Fill in demo names'}
          </button>
          {fillProblem && <p className="error responder-problem">{fillProblem}</p>}
        </div>
      )}

      {sectors.map((group) => (
        <div className="pr-sector" key={group.sector}>
          <h4>{SECTOR_LABEL[group.sector] ?? 'Other'}</h4>
          <ul className="pr-list">
            {group.pooks.map((pook) => (
              <PookRow
                key={pook.id}
                pook={pook}
                onSave={onSave}
                outAt={pook.responder_name ? out[pook.responder_name] : undefined}
              />
            ))}
          </ul>
        </div>
      ))}
    </section>
  )
}

import { useEffect, useState } from 'react'
import { api } from '../api'
import { IconBuilding } from '../icons'
import SectionCard from './SectionCard'
import Toast from './Toast'

// Barangay Information. The one value the cloud actually holds is the responder's own
// `barangay` (GET /auth/me), used to prefill Barangay Name. City/Municipality and Region
// have no cloud home yet, and PATCH /auth/me (out of this lane) doesn't accept `barangay`
// anyway -- so Confirm persists to this browser. Swap loadSaved / the save in onConfirm
// for an endpoint once one exists; the form doesn't change.
const KEY = 'seenior.barangayInfo'
const DEFAULTS = {
  name: 'Barangay',
  city: 'Quezon City',
  region: 'National Capital Region (NCR)',
}

function loadSaved() {
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) || '{}') }
  } catch {
    return { ...DEFAULTS }
  }
}

export default function Settings({ onSessionLost }) {
  const [saved, setSaved] = useState(loadSaved)
  const [form, setForm] = useState(loadSaved)
  const [toastOpen, setToastOpen] = useState(false)

  useEffect(() => {
    let live = true
    api('/auth/me')
      .then((me) => {
        if (!live || !me.barangay) return
        // Only let the account's barangay win if this browser has never saved its own.
        if (localStorage.getItem(KEY)) return
        const seeded = { ...DEFAULTS, name: me.barangay }
        setSaved(seeded)
        setForm(seeded)
      })
      .catch((err) => {
        if (err.message.includes('expired')) onSessionLost()
      })
    return () => {
      live = false
    }
  }, [onSessionLost])

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  function onConfirm() {
    try {
      localStorage.setItem(KEY, JSON.stringify(form))
    } catch {
      /* ignore -- the in-memory value still updates for this session */
    }
    setSaved(form)
    setToastOpen(true)
  }

  return (
    <div className="settings-page">
      <SectionCard icon={<IconBuilding />} title="Barangay Information">
        <div className="settings-form">
          <label className="field">
            <span>Barangay Name</span>
            <input type="text" value={form.name} onChange={set('name')} />
          </label>
          <label className="field">
            <span>City/Municipality</span>
            <input type="text" value={form.city} onChange={set('city')} />
          </label>
          <label className="field">
            <span>Region</span>
            <input type="text" value={form.region} onChange={set('region')} />
          </label>
          <div className="settings-actions">
            <button type="button" className="btn-outline" onClick={() => setForm(saved)}>
              Cancel
            </button>
            <button type="button" className="btn-primary" onClick={onConfirm}>
              Confirm
            </button>
          </div>
        </div>
      </SectionCard>

      {toastOpen && (
        <Toast message="Confirmation Successful" onClose={() => setToastOpen(false)} />
      )}
    </div>
  )
}

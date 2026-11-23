import { useState } from 'react'
import AppShell from '../components/AppShell'
import IncidentQueue from '../components/IncidentQueue'
import { SCENARIO, playStep } from './scenario'

const noop = () => {}

// The real Alerts screen on sample data, with a walkthrough bar on top.
export default function Preview() {
  const [step, setStep] = useState(0)
  const [busy, setBusy] = useState(false)
  const last = step === SCENARIO.length - 1

  async function next() {
    setBusy(true)
    await playStep(SCENARIO[step + 1])
    setStep(step + 1)
    setBusy(false)
  }

  return (
    <>
      <div className="preview-bar">
        <span className="preview-bar-step">
          Step {step + 1} of {SCENARIO.length}
        </span>
        <p>{SCENARIO[step].say}</p>
        <button type="button" className="btn-primary" disabled={busy || last} onClick={next}>
          Next step
        </button>
        <button type="button" className="link-btn" onClick={() => window.location.reload()}>
          Start over
        </button>
      </div>
      <AppShell title="Alerts" view="alerts" onNavigate={noop} onSignOut={noop}>
        {/* A new key per step re-reads the sample data at once, without waiting for the poll. */}
        <IncidentQueue key={step} onSessionLost={noop} />
      </AppShell>
    </>
  )
}

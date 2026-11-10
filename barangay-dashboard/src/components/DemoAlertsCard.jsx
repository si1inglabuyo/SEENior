import { useState } from 'react'
import { addDemoAlert, clearDemoAlerts, demoAlerts } from '../demoAlerts'
import { IconWarning } from '../icons'
import SectionCard from './SectionCard'

// TEMPORARY, for the defense demo only: remove with src/demoAlerts.js after the demo.
// Adds made-up alerts to this browser tab only, to show several alerts arriving at once.

const KINDS = [
  ['sos', 'SOS'],
  ['fall', 'Potential fall'],
  ['welfare', 'Welfare check'],
  ['anomaly', 'Anomaly'],
]

export default function DemoAlertsCard({ pooks, onChange }) {
  const [alone, setAlone] = useState(false)
  const count = demoAlerts().length

  function add(kind) {
    addDemoAlert(kind, alone, pooks ?? [])
    onChange()
  }

  return (
    <SectionCard icon={<IconWarning />} title="Demo alerts" className="demo-alerts-card">
      <p className="muted demo-alerts-note">
        For the demo only. These alerts stay in this browser tab and never reach the server, the
        family or the senior. {count > 0 && `${count} open now.`}
      </p>
      <div className="demo-alerts-controls">
        <div className="demo-toggle" role="group" aria-label="Senior lives">
          <button type="button" aria-pressed={!alone} onClick={() => setAlone(false)}>
            With family
          </button>
          <button type="button" aria-pressed={alone} onClick={() => setAlone(true)}>
            Lives alone
          </button>
        </div>
        {KINDS.map(([kind, label]) => {
          const blocked = kind === 'welfare' && alone
          return (
            <button
              key={kind}
              type="button"
              className="btn-primary"
              disabled={blocked}
              title={blocked ? 'Only a family contact can request a welfare check' : undefined}
              onClick={() => add(kind)}
            >
              + {label}
            </button>
          )
        })}
        <button
          type="button"
          className="link-btn"
          disabled={count === 0}
          onClick={() => {
            clearDemoAlerts()
            onChange()
          }}
        >
          Clear demo alerts
        </button>
      </div>
    </SectionCard>
  )
}

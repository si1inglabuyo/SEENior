import { useState } from 'react'
import { api } from '../api'
import { recordAccess } from '../audit'
import { BLOCK_LABEL, partsOfDay, progress, summarize } from '../routine'
import { IconChart, IconEye } from '../icons'
import SectionCard from './SectionCard'

// A senior's routine, laid out like the "My routine" screen on their own phone: how far the
// phone has learned, then the four parts of the day with what is usual in each. The phone
// computes it from 14 days of its own readings and uploads only these medians; the readings
// never leave the phone. Behavioural, so it opens on a click that is written to the access log
// (RA 10173 section 23(a)), like the other disclosures beyond the default view.
export default function RoutineCard({ syncId, onSessionLost }) {
  const [phase, setPhase] = useState('closed') // 'closed' | 'loading' | 'ok' | 'error'
  const [routine, setRoutine] = useState(null)
  const [error, setError] = useState('')

  async function open() {
    setPhase('loading')
    recordAccess('routine_view', { sync_id: syncId })
    try {
      setRoutine(await api(`/seniors/${syncId}/routine`))
      setPhase('ok')
    } catch (err) {
      if (err.message.includes('expired')) {
        onSessionLost()
        return
      }
      setError(err.message)
      setPhase('error')
    }
  }

  const rows = routine?.rows ?? []
  const summary = summarize(rows)
  const parts = partsOfDay(rows, routine?.timing)
  const days = summary.fullyLearned ? 14 : summary.days

  return (
    <SectionCard icon={<IconChart />} title="Daily Routine" className="routine-card">
      {phase === 'closed' && (
        <>
          <p className="muted routine-note">
            What is usual for this senior in each part of the day, learned from the last 14 days
            on their phone. Opening it is recorded in the access log.
          </p>
          <button type="button" className="history-more-btn" onClick={open}>
            <IconEye /> View routine
          </button>
        </>
      )}

      {phase === 'loading' && <p className="muted">Loading&hellip;</p>}
      {phase === 'error' && <p className="error">{error}</p>}

      {phase === 'ok' && rows.length === 0 && (
        <p className="muted alerts-empty">
          Nothing shared yet. It appears once the senior&apos;s phone has connected.
        </p>
      )}

      {phase === 'ok' && rows.length > 0 && (
        <div className="rt">
          <div className="rt-learn">
            <strong>{progress(summary)}</strong>
            <div className="rt-track" aria-hidden="true">
              <i style={{ width: `${Math.round((days / 14) * 100)}%` }} />
            </div>
            <p className="muted rt-note">
              Alerts are judged against this senior&apos;s own routine, not against other seniors.
            </p>
          </div>

          <div className="rt-parts">
            {parts.map((p) => (
              <section key={p.block} className="rt-part">
                <div className="rt-head">
                  <strong>{BLOCK_LABEL[p.block]}</strong>
                  {p.hours && <span className="muted rt-hours">{p.hours}</span>}
                  <span className={`rt-tag${p.learned ? '' : ' rt-tag-seed'}`}>
                    {p.learned ? '✓ Learned' : '○ Setup answer'}
                  </span>
                </div>
                {p.headline && <p className="rt-say">{p.headline}</p>}
                <dl className="rt-lines">
                  {p.lines.map((l) => (
                    <div key={l.label} className="rt-line">
                      <dt>{l.label}</dt>
                      <dd>{l.value}</dd>
                    </div>
                  ))}
                </dl>
              </section>
            ))}
          </div>
        </div>
      )}
    </SectionCard>
  )
}

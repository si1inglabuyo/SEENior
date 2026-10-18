import { useState } from 'react'
import { api, formatTime } from '../api'
import { recordAccess } from '../audit'
import {
  BLOCKS, BLOCK_LABEL, FEATURES, LEVEL_LABEL, PHONE_LABEL,
  cell, headline, progress, stillLabel, summarize,
} from '../routine'
import { IconChart, IconEye } from '../icons'
import SectionCard from './SectionCard'

// A senior's Routine Fingerprint: what is usual for them in each part of the day. The phone
// computes it from 14 days of its own readings and uploads only these medians; the readings
// never leave the phone. Behavioural, so it opens on a click that is written to the access log
// (RA 10173 section 23(a)), like the other disclosures beyond the default view.
export default function RoutineCard({ syncId, onSessionLost }) {
  const [phase, setPhase] = useState('closed') // 'closed' | 'loading' | 'ok' | 'error'
  const [routine, setRoutine] = useState(null)
  const [error, setError] = useState('')
  const [showNumbers, setShowNumbers] = useState(false)

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
  const lead = headline(summary)

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
        <>
          {lead && <p className="routine-headline">{lead}</p>}
          <p className="muted routine-note">
            {progress(summary)} Last updated {formatTime(routine.updated_at)}.
          </p>

          <ul className="routine-blocks">
            {summary.blocks.map((b) => (
              <li key={b.block} className="routine-block">
                <div className="routine-block-head">
                  <strong>{BLOCK_LABEL[b.block]}</strong>
                  <span className={`routine-level routine-level-${b.level}`}>{LEVEL_LABEL[b.level]}</span>
                </div>
                <div className="routine-bar" aria-hidden="true">
                  <div className="routine-bar-fill" style={{ width: `${Math.max(3, Math.round(b.activity * 100))}%` }} />
                </div>
                {b.stillSeconds !== null && <p className="muted routine-line">{stillLabel(b.stillSeconds)}</p>}
                {b.phone && <p className="muted routine-line">{PHONE_LABEL[b.phone]}</p>}
              </li>
            ))}
          </ul>

          <button type="button" className="history-more-btn" onClick={() => setShowNumbers((v) => !v)}>
            {showNumbers ? 'Hide numbers' : 'Show numbers'}
          </button>

          {showNumbers && (
            <>
              <div className="routine-scroll">
                <table className="routine-table">
                  <thead>
                    <tr>
                      <th />
                      {BLOCKS.map((b) => (
                        <th key={b}>{BLOCK_LABEL[b]}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {FEATURES.map((f) => (
                      <tr key={f.key}>
                        <th scope="row">{f.label}</th>
                        {BLOCKS.map((b) => {
                          const c = cell(rows, f.key, b)
                          return (
                            <td key={b}>
                              {c ? (
                                <>
                                  <strong>
                                    {c.value}
                                    {c.isSeed ? '*' : ''}
                                  </strong>
                                  <span className="muted routine-swing">{c.swing}</span>
                                </>
                              ) : (
                                '—'
                              )}
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="muted routine-note">
                * still from the setup answers. &ldquo;±&rdquo; is how much an ordinary day normally varies.
              </p>
            </>
          )}
        </>
      )}
    </SectionCard>
  )
}

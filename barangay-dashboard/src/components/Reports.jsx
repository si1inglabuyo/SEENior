import { useEffect, useState } from 'react'
import { api, currentUserId } from '../api'
import { recordAccess } from '../audit'
import { addDays, buildSenior, buildWeekly, lastCompleteWeek, mondayOf } from '../reportData'
import SectionCard from './SectionCard'
import WeeklyReport from './WeeklyReport'
import SeniorReport from './SeniorReport'
import { IconChart } from '../icons'

const todayIso = () => new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10)

// Printable reports for the barangay. Built in the browser from the same endpoints the other
// screens use; "Print / Save as PDF" hands the page to the browser's print dialog.
export default function Reports({ onSessionLost }) {
  const [kind, setKind] = useState('weekly') // 'weekly' | 'senior'
  const [weekStart, setWeekStart] = useState(() => lastCompleteWeek())
  const [seniors, setSeniors] = useState([])
  const [syncId, setSyncId] = useState('')
  const [toIso, setToIso] = useState(todayIso)
  const [fromIso, setFromIso] = useState(() => addDays(todayIso(), -29))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [report, setReport] = useState(null)

  useEffect(() => {
    api('/barangay/seniors')
      .then(setSeniors)
      .catch((err) => {
        if (err.message.includes('expired')) onSessionLost()
        else setError(err.message)
      })
  }, [onSessionLost])

  async function generate() {
    setBusy(true)
    setError('')
    setReport(null)
    const generatedAt = new Date().toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
    const generatedBy = currentUserId()
    try {
      if (kind === 'weekly') {
        const monday = mondayOf(weekStart)
        const [alerts, roster] = await Promise.all([
          api(`/barangay/alerts?scope=all&date_from=${addDays(monday, -7)}&date_to=${addDays(monday, 6)}`),
          api('/barangay/seniors'),
        ])
        recordAccess('report_generated', { kind: 'weekly', week_start: monday })
        setReport({ kind, generatedAt, generatedBy, data: buildWeekly({ alerts, seniors: roster, weekStart: monday }) })
      } else {
        if (!syncId) throw new Error('Choose a senior first.')
        if (fromIso > toIso) throw new Error('The start date is after the end date.')
        recordAccess('report_generated', { kind: 'senior', sync_id: syncId, from: fromIso, to: toIso })
        const detail = await api(`/barangay/seniors/${syncId}`)
        // The fingerprint is behavioural, so reading it is logged like the "View routine" click.
        recordAccess('routine_view', { sync_id: syncId })
        const routine = await api(`/seniors/${syncId}/routine`).catch(() => null)
        setReport({ kind, generatedAt, generatedBy, detail, routine, data: buildSenior({ detail, fromIso, toIso }) })
      }
    } catch (err) {
      if (err.message.includes('expired')) {
        onSessionLost()
        return
      }
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <SectionCard icon={<IconChart />} title="Generate a report" className="rpt-controls">
        <div className="rpt-form">
          <label>
            Report
            <select value={kind} onChange={(e) => { setKind(e.target.value); setReport(null) }}>
              <option value="weekly">Weekly summary (whole barangay)</option>
              <option value="senior">Senior summary (one person)</option>
            </select>
          </label>
          {kind === 'weekly' ? (
            <label>
              Week starting (Monday)
              <input type="date" value={weekStart} onChange={(e) => e.target.value && setWeekStart(mondayOf(e.target.value))} />
            </label>
          ) : (
            <>
              <label>
                Senior
                <select value={syncId} onChange={(e) => setSyncId(e.target.value)}>
                  <option value="">Choose…</option>
                  {seniors.map((s) => (
                    <option key={s.sync_id} value={s.sync_id}>{s.last_name}, {s.first_name}</option>
                  ))}
                </select>
              </label>
              <label>
                From
                <input type="date" value={fromIso} max={toIso} onChange={(e) => setFromIso(e.target.value)} />
              </label>
              <label>
                To
                <input type="date" value={toIso} onChange={(e) => setToIso(e.target.value)} />
              </label>
            </>
          )}
          <button type="button" className="btn-primary" onClick={generate} disabled={busy}>
            {busy ? 'Generating…' : 'Generate'}
          </button>
          {report && (
            <button type="button" className="btn-outline" onClick={() => window.print()}>
              Print / Save as PDF
            </button>
          )}
        </div>
        {error && <p className="error">{error}</p>}
        <p className="muted rpt-hint">
          Reports are built from alerts that reached the cloud. Choosing “Save as PDF” in the print
          window keeps a copy; turn off “Headers and footers” for a clean page.
        </p>
      </SectionCard>

      {report?.kind === 'weekly' && (
        <WeeklyReport data={report.data} generatedAt={report.generatedAt} generatedBy={report.generatedBy} />
      )}
      {report?.kind === 'senior' && (
        <SeniorReport
          data={report.data}
          detail={report.detail}
          routine={report.routine}
          generatedAt={report.generatedAt}
          generatedBy={report.generatedBy}
        />
      )}
    </>
  )
}

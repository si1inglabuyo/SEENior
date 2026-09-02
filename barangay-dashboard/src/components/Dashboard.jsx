import { useCallback, useEffect, useState } from 'react'
import { api, POLL_MS } from '../api'
import { clockTime } from '../format'
import { IconPeople, IconWarning, IconCheck, IconSos } from '../icons'
import StatCard from './StatCard'
import AlertsTodayPanel from './AlertsTodayPanel'
import WeeklyBarChart from './WeeklyBarChart'
import OutcomeDonut from './OutcomeDonut'

// Every new stats field is read defensively (`?? 0` / `?? null`). The dashboard has to
// render cleanly against the production API before it ships the extra fields -- the
// sub-lines just fall back to "—" until then.
function resolutionRate(outcomes) {
  const resolved = (outcomes.resolved || 0) + (outcomes.false_positive || 0)
  const active = (outcomes.escalated || 0) + (outcomes.acknowledged || 0)
  const total = resolved + active
  return total === 0 ? null : Math.round((resolved / total) * 100)
}

function yesterdayDelta(stats) {
  if (stats.alerts_today_total == null || stats.alerts_yesterday_total == null) return null
  const diff = stats.alerts_today_total - stats.alerts_yesterday_total
  const tone = diff > 0 ? 'down' : diff < 0 ? 'up' : 'neutral'
  const arrow = diff > 0 ? '↑' : diff < 0 ? '↓' : ''
  return { text: `${arrow} ${Math.abs(diff)} from yesterday`.trim(), tone }
}

export default function Dashboard({ onSessionLost, onNavigate }) {
  const [stats, setStats] = useState(null)
  const [today, setToday] = useState(null)
  const [error, setError] = useState('')

  // Stats drives the whole page and must succeed. The "today" feed is a nice-to-have: an
  // older backend that doesn't know `scope=today` yet should still leave a working
  // dashboard, just with an empty Alerts Today panel.
  const load = useCallback(
    (live) => {
      api('/barangay/stats')
        .then((s) => {
          if (live()) setStats(s)
        })
        .catch((err) => {
          if (!live()) return
          setError(err.message)
          if (err.message.includes('expired')) onSessionLost()
        })
      api('/barangay/alerts?scope=today')
        .then((t) => {
          if (live()) setToday(t)
        })
        .catch(() => {
          if (live()) setToday([])
        })
    },
    [onSessionLost]
  )

  // Polled, not just fetched once: an alert acted on from the Alerts page (Acknowledge,
  // Resolve, False Positive) is a real write to the same alerts table this page reads --
  // polling is what makes the stat cards, the Alerts Today panel and the outcome donut
  // catch that change without requiring the responder to leave and re-enter this view.
  useEffect(() => {
    let cancelled = false
    const live = () => !cancelled
    load(live)
    const timer = setInterval(() => load(live), POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [load])

  // A poll that fails transiently (a Render cold-start, a dropped connection) must not
  // blank a dashboard that already has good data on screen -- only the first load blocks
  // on success, same as IncidentQueue's own load/poll split.
  if (error && !stats) return <p className="error">{error}</p>
  if (!stats || !today) return <p className="muted">Loading…</p>

  const rate = resolutionRate(stats.outcomes || {})
  const delta = yesterdayDelta(stats)
  const monthAdded = stats.seniors_added_this_month
  const monthSub =
    monthAdded == null
      ? '—'
      : monthAdded === 0
        ? 'No new seniors this month'
        : `↑ ${monthAdded} this month`

  return (
    <div className="dashboard">
      <div className="stat-row">
        <StatCard
          label="Total Seniors"
          value={stats.seniors_monitored}
          icon={<IconPeople />}
          tone={monthAdded ? 'up' : 'neutral'}
          sub={monthSub}
          onClick={() => onNavigate('seniors')}
        />
        <StatCard
          label="Active Alerts"
          value={stats.open_incidents}
          icon={<IconWarning />}
          tone={delta ? delta.tone : 'neutral'}
          sub={delta ? delta.text : '—'}
          onClick={() => onNavigate('history', { status: 'escalated', label: 'Active' })}
        />
        <StatCard
          label="Resolved Today"
          value={stats.resolved_today ?? '—'}
          icon={<IconCheck />}
          tone="up"
          sub={rate != null ? `↑ ${rate}% resolution rate` : '—'}
          onClick={() =>
            onNavigate('history', { status: 'resolved', when: 'today', label: 'Resolved Today' })
          }
        />
        <StatCard
          label="SOS Triggered"
          value={stats.sos_today ?? '—'}
          icon={<IconSos />}
          tone="down"
          sub={stats.sos_last_at ? `⏱ ${clockTime(stats.sos_last_at)}` : 'None today'}
          onClick={() =>
            onNavigate('history', { trigger_type: 'sos', when: 'today', label: 'SOS Triggered' })
          }
        />
      </div>

      <AlertsTodayPanel alerts={today} onViewAll={() => onNavigate('alerts')} />

      <div className="dash-charts">
        <WeeklyBarChart days={stats.alerts_this_week} />
        <OutcomeDonut outcomes={stats.outcomes} />
      </div>
    </div>
  )
}

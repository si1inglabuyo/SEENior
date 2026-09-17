import { useCallback, useEffect, useState } from 'react'
import { api, POLL_MS } from '../api'
import { clockTime } from '../format'
import { IconPeople, IconCheck, IconSos } from '../icons'
import { useAlertActions } from '../hooks/useAlertActions'
import StatCard from './StatCard'
import ActiveAlertsPanel from './ActiveAlertsPanel'
import WeeklyBarChart from './WeeklyBarChart'
import OutcomeDonut from './OutcomeDonut'
import AlertTypeChart from './AlertTypeChart'
import AlertActionModals from './AlertActionModals'

// "2026-09-08" -> "Mon, Sep 8", for the Alerts-This-Week bar drill-down label.
function dayLabel(iso) {
  return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  })
}

// Every new stats field is read defensively (`?? 0` / `?? null`). The dashboard has to
// render cleanly against the production API before it ships the extra fields -- the
// sub-lines just fall back to "—" until then.
function resolutionRate(outcomes) {
  const resolved = (outcomes.resolved || 0) + (outcomes.false_positive || 0)
  const active = (outcomes.escalated || 0) + (outcomes.acknowledged || 0)
  const total = resolved + active
  return total === 0 ? null : Math.round((resolved / total) * 100)
}

// The dashboard's "Alerts by Type" donut wants the three responder-facing categories
// (anomaly / sos / dispatch_family). The deployed /barangay/stats still returns
// `alert_types`, keyed by raw trigger_type, so fold those into the categories that can be
// derived from a trigger alone: sos -> sos, everything else -> anomaly. (dispatch_family
// needs the escalation timeline, which stats doesn't carry, so it only appears once the
// API returns `alert_categories` directly.)
function categoriesFromTypes(types) {
  if (!types) return null
  const out = {}
  for (const [trigger, n] of Object.entries(types)) {
    const key = trigger === 'sos' ? 'sos' : 'anomaly'
    out[key] = (out[key] || 0) + n
  }
  return out
}

export default function Dashboard({ onSessionLost, onNavigate }) {
  const [stats, setStats] = useState(null)
  const [activeAlerts, setActiveAlerts] = useState(null)
  const [error, setError] = useState('')

  // Stats drives the whole page and must succeed. The active-alerts feed is a nice-to-have
  // -- a transient failure just leaves that one panel empty.
  const load = useCallback(
    (live = () => true) => {
      api('/barangay/stats')
        .then((s) => {
          if (live()) setStats(s)
        })
        .catch((err) => {
          if (!live()) return
          setError(err.message)
          if (err.message.includes('expired')) onSessionLost()
        })
      api('/barangay/alerts?scope=active')
        .then((t) => {
          if (live()) setActiveAlerts(t)
        })
        .catch(() => {
          if (live()) setActiveAlerts([])
        })
    },
    [onSessionLost]
  )

  // Clicking a row in the Active Alerts panel opens the shared Details modal (with the same
  // Acknowledge / Resolve / False Positive actions the Alerts tab uses). A successful action
  // reloads the whole dashboard so the stat cards and charts catch it immediately.
  const actions = useAlertActions({ onReload: () => load(), onSessionLost })

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
  if (!stats || !activeAlerts) return <p className="muted">Loading…</p>

  const rate = resolutionRate(stats.outcomes || {})
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
          label="Resolved Today"
          value={stats.resolved_today ?? '—'}
          icon={<IconCheck />}
          tone="up"
          sub={rate != null ? `↑ ${rate}% resolution rate` : '—'}
          onClick={() =>
            onNavigate('history', { when: 'today', label: 'Resolved Today' })
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

      <ActiveAlertsPanel
        alerts={activeAlerts}
        onViewAll={() => onNavigate('alerts')}
        onShowDetails={actions.showDetails}
      />

      <WeeklyBarChart
        days={stats.alerts_this_week}
        onSelectDay={(day) => onNavigate('history', { date: day, label: dayLabel(day) })}
      />

      <div className="dash-charts">
        <OutcomeDonut outcomes={stats.outcomes} onNavigate={onNavigate} />
        <AlertTypeChart
          categories={stats.alert_categories || categoriesFromTypes(stats.alert_types)}
          onSelect={(key, label) =>
            onNavigate('history', { category: key, label: `${label} alerts` })
          }
        />
      </div>

      <AlertActionModals actions={actions} />
    </div>
  )
}

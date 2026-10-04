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

// New stats fields are read defensively (`?? 0` / `?? null`), so the dashboard still
// renders against an API that doesn't send them yet.
function resolutionRate(outcomes) {
  const resolved = (outcomes.resolved || 0) + (outcomes.false_positive || 0)
  const active =
    (outcomes.escalated || 0) + (outcomes.attending || 0) + (outcomes.acknowledged || 0)
  const total = resolved + active
  return total === 0 ? null : Math.round((resolved / total) * 100)
}

// Older /barangay/stats responses only have `alert_types` keyed by trigger_type. Fold those
// into the four categories that can be derived from a trigger: sos, fall_pattern ->
// potential_fall, everything else -> anomaly. dispatch_family needs the timeline, so it
// only appears when the API returns `alert_categories`.
function categoriesFromTypes(types) {
  if (!types) return null
  const out = {}
  for (const [trigger, n] of Object.entries(types)) {
    const key = trigger === 'sos' ? 'sos' : trigger === 'fall_pattern' ? 'potential_fall' : 'anomaly'
    out[key] = (out[key] || 0) + n
  }
  return out
}

export default function Dashboard({ onSessionLost, onNavigate }) {
  const [stats, setStats] = useState(null)
  const [activeAlerts, setActiveAlerts] = useState(null)
  const [error, setError] = useState('')

    // Stats must succeed. The active-alerts feed is optional; a failure just empties that panel.
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

    // Clicking a row opens the shared Details modal. A successful action reloads the dashboard.
  const actions = useAlertActions({ onReload: () => load(), onSessionLost })

    // Polled so actions taken on other screens show up in the stat cards and charts.
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

    // A failed poll must not blank data already on screen; only the first load blocks.
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

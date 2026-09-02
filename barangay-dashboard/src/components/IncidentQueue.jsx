import { useCallback, useEffect, useState } from 'react'
import { api, POLL_MS } from '../api'
import { CATEGORY_LABEL, alertCategory } from '../labels'
import { STATUS_PILLS, TYPE_OPTIONS, matchesStatus } from '../historyFilters'
import { useAlertActions } from '../hooks/useAlertActions'
import { IconWarning } from '../icons'
import SectionCard from './SectionCard'
import FilterMenu from './FilterMenu'
import AlertRow from './AlertRow'
import AlertActionModals from './AlertActionModals'

// The Alerts tab -- today's alerts (design/alert/alert-default.png: the "Alerts Today"
// card). The all-time incident log is its own tab now (Alert History), so this view is
// scoped to the day and defaults to the open ones. The All / Active / Resolved pills and
// the Alert Type dropdown filter the same rows client-side, sharing the exact primitives
// Alert History uses so the two screens can't drift.
export default function IncidentQueue({ onSessionLost }) {
  const [alerts, setAlerts] = useState(null)
  const [error, setError] = useState('')
  const [statusPill, setStatusPill] = useState('active')
  const [alertType, setAlertType] = useState('all')

  const load = useCallback(async () => {
    try {
      // `today` is the design's dataset. An older backend that predates that scope 422s
      // here -- fall back to the open queue so the tab still works rather than showing an
      // error over a filter bar that then can't do anything.
      let data
      try {
        data = await api('/barangay/alerts?scope=today')
      } catch {
        data = await api('/barangay/alerts?scope=active')
      }
      setAlerts(data)
      setError('')
    } catch (err) {
      setError(err.message)
      setAlerts((current) => current ?? [])
      if (err.message.includes('expired')) onSessionLost()
    }
  }, [onSessionLost])

  // Polling: an action taken here or on Alert History or the Dashboard is a real write to
  // the same table; the poll is what converges every open screen without a manual refresh.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load()
    const timer = setInterval(load, POLL_MS)
    return () => clearInterval(timer)
  }, [load])

  const actions = useAlertActions({ onReload: load, onSessionLost })

  const loading = alerts === null
  const rows = alerts ?? []
  const visibleRows = rows.filter(
    (a) =>
      matchesStatus(a, statusPill) && (alertType === 'all' || alertCategory(a) === alertType)
  )

  return (
    <div className="queue-page">
      <div className="history-filters">
        <div className="pill-group">
          {STATUS_PILLS.map(([key, label]) => (
            <button
              key={key}
              type="button"
              className={statusPill === key ? 'active' : ''}
              onClick={() => setStatusPill(key)}
            >
              {label}
            </button>
          ))}
        </div>

        <FilterMenu
          label={alertType === 'all' ? 'Alert Type' : CATEGORY_LABEL[alertType]}
          active={alertType !== 'all'}
        >
          {(close) => (
            <ul className="menu-list">
              {TYPE_OPTIONS.map((type) => (
                <li key={type}>
                  <button
                    type="button"
                    className={alertType === type ? 'active' : ''}
                    onClick={() => {
                      setAlertType(type)
                      close()
                    }}
                  >
                    {CATEGORY_LABEL[type]}
                  </button>
                </li>
              ))}
              {alertType !== 'all' && (
                <li>
                  <button
                    type="button"
                    className="menu-clear"
                    onClick={() => {
                      setAlertType('all')
                      close()
                    }}
                  >
                    Clear
                  </button>
                </li>
              )}
            </ul>
          )}
        </FilterMenu>
      </div>

      {error && <p className="error">{error}</p>}

      <SectionCard icon={<IconWarning />} title="Alerts Today">
        {loading ? (
          <p className="muted alerts-empty">Loading&hellip;</p>
        ) : visibleRows.length === 0 ? (
          <p className="muted alerts-empty">
            {rows.length === 0
              ? 'No alerts today. An incident will appear here the moment one is escalated to you.'
              : 'No alerts today match this filter.'}
          </p>
        ) : (
          <ul className="queue-rows history-rows">
            {visibleRows.map((alert) => (
              <AlertRow
                key={alert.sync_id}
                alert={alert}
                onAskAction={actions.askAction}
                onShowDetails={actions.showDetails}
              />
            ))}
          </ul>
        )}
      </SectionCard>

      <AlertActionModals actions={actions} />
    </div>
  )
}

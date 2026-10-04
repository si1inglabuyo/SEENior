import { useCallback, useEffect, useState } from 'react'
import { api, POLL_MS } from '../api'
import { CATEGORY_LABEL, alertCategory } from '../labels'
import { TYPE_OPTIONS, matchesSearch } from '../historyFilters'
import { useAlertActions } from '../hooks/useAlertActions'
import { IconWarning, IconSearch } from '../icons'
import SectionCard from './SectionCard'
import FilterMenu from './FilterMenu'
import AlertRow from './AlertRow'
import AlertActionModals from './AlertActionModals'

// The Alerts tab: every incident open at the barangay tier. Closed ones move to Alert
// History. The type dropdown and search filter rows on the client, using the same
// primitives as Alert History.
export default function IncidentQueue({ onSessionLost }) {
  const [alerts, setAlerts] = useState(null)
  const [error, setError] = useState('')
  const [alertType, setAlertType] = useState('all')
  const [search, setSearch] = useState('')

  const load = useCallback(async () => {
    try {
      const data = await api('/barangay/alerts?scope=active')
      setAlerts(data)
      setError('')
    } catch (err) {
      setError(err.message)
      setAlerts((current) => current ?? [])
      if (err.message.includes('expired')) onSessionLost()
    }
  }, [onSessionLost])

    // Poll so actions taken on other screens show up here.
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
      (alertType === 'all' || alertCategory(a) === alertType) && matchesSearch(a, search)
  )

  return (
    <div className="queue-page">
      <div className="history-filters">
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

        <div className="history-search">
          <IconSearch />
          <input
            type="search"
            placeholder="Search by name, age…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {error && <p className="error">{error}</p>}

      <SectionCard icon={<IconWarning />} title="Active Alerts">
        {loading ? (
          <p className="muted alerts-empty">Loading&hellip;</p>
        ) : visibleRows.length === 0 ? (
          <p className="muted alerts-empty">
            {rows.length === 0
              ? 'No active alerts. An incident will appear here the moment one is escalated to you.'
              : 'No active alerts match these filters.'}
          </p>
        ) : (
          <ul className="queue-rows history-rows">
            {visibleRows.map((alert) => (
              <AlertRow
                key={alert.sync_id}
                alert={alert}
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

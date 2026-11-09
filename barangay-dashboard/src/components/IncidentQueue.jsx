import { useCallback, useEffect, useState } from 'react'
import { api, POLL_MS } from '../api'
import { CATEGORY_LABEL, alertCategory } from '../labels'
import { TYPE_OPTIONS, matchesSearch } from '../historyFilters'
import { byPriority, dispatchOf, receivedAfterHours } from '../dispatch'
import { useAlertActions } from '../hooks/useAlertActions'
import { useDispatch } from '../hooks/useDispatch'
import { useHallClosed } from '../hooks/useHallClosed'
import { useResponders } from '../hooks/useResponders'
import { IconWarning, IconSearch } from '../icons'
import SectionCard from './SectionCard'
import FilterMenu from './FilterMenu'
import AlertRow from './AlertRow'
import AlertActionModals from './AlertActionModals'
import PookGrid from './PookGrid'
import ResponderPanel from './ResponderPanel'
import TierGuide from './TierGuide'

// The Alerts tab: every incident open at the barangay tier. Closed ones move to Alert
// History. The type dropdown and search filter rows on the client, using the same
// primitives as Alert History.
export default function IncidentQueue({ onSessionLost }) {
  const [alerts, setAlerts] = useState(null)
  const [error, setError] = useState('')
  const [alertType, setAlertType] = useState('all')
  const [search, setSearch] = useState('')
    // A pook name, null for alerts with no pook, or undefined for every pook.
  const [pook, setPook] = useState(undefined)
  const [tier, setTier] = useState(undefined)

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
  const dispatch = useDispatch({ onReload: load, onSessionLost })
  const { pooks } = dispatch
  const { closedDays } = useHallClosed()
  const responders = useResponders()

  const loading = alerts === null
  const rows = alerts ?? []
  const filteredRows = rows.filter(
    (a) =>
      (alertType === 'all' || alertCategory(a) === alertType) &&
      matchesSearch(a, search) &&
      (pook === undefined || (a.senior_pook ?? null) === pook) &&
      (tier === undefined || dispatchOf(a).tier === tier)
  )
    // Once dispatch is live, the list follows the priority tiers, not arrival order.
  const visibleRows = pooks ? byPriority(filteredRows) : filteredRows

  const queue = (
    <SectionCard
      icon={<IconWarning />}
      title={rows.length ? `Active Alerts (${rows.length})` : 'Active Alerts'}
    >
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
              onShowDetails={(a) => {
                dispatch.clearError()
                actions.showDetails(a)
              }}
              dispatch={pooks ? dispatchOf(alert) : undefined}
              afterHours={Boolean(pooks) && receivedAfterHours(alert, closedDays)}
            />
          ))}
        </ul>
      )}
    </SectionCard>
  )

  return (
    <div className="queue-page">
      {pooks && <PookGrid pooks={pooks} alerts={rows} selected={pook} onSelect={setPook} />}
      {pooks && <TierGuide alerts={rows} selected={tier} onSelect={setTier} />}

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

      {pook !== undefined && (
        <p className="pook-filter-note">
          Showing alerts in <strong>{pook ?? 'No pook set'}</strong> only.{' '}
          <button type="button" className="link-btn" onClick={() => setPook(undefined)}>
            Show all pooks
          </button>
        </p>
      )}

      {tier !== undefined && (
        <p className="pook-filter-note">
          Showing <strong>Tier {tier}</strong> alerts only.{' '}
          <button type="button" className="link-btn" onClick={() => setTier(undefined)}>
            Show all tiers
          </button>
        </p>
      )}

      {pooks ? (
        <div className="queue-columns">
          {queue}
          <ResponderPanel
            alerts={rows}
            responders={responders}
            pooks={pooks}
            onSavePook={dispatch.savePookResponder}
          />
        </div>
      ) : (
        queue
      )}

      <AlertActionModals
        actions={actions}
        dispatch={{ ...dispatch, teamList: responders.teams, serviceList: responders.services }}
        liveAlerts={rows}
      />
    </div>
  )
}

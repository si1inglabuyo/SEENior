import { useCallback, useEffect, useState } from 'react'
import { api, parseServerTime, POLL_MS } from '../api'
import { CATEGORY_LABEL, alertCategory } from '../labels'
import {
  TYPE_OPTIONS,
  DATE_LABELS,
  initialFilters,
  withinRange,
  matchesSearch,
  dateRangeLabel,
  rangeBounds,
} from '../historyFilters'
import { useAlertActions } from '../hooks/useAlertActions'
import { IconHistory, IconSearch } from '../icons'
import SectionCard from './SectionCard'
import FilterMenu from './FilterMenu'
import AlertRow from './AlertRow'
import AlertActionModals from './AlertActionModals'

function DateRangePanel({ value, onPick, onClear }) {
  const [start, setStart] = useState(value && value.kind === 'custom' ? value.start : '')
  const [end, setEnd] = useState(value && value.kind === 'custom' ? value.end : '')

  return (
    <div className="date-panel">
      <ul className="menu-list">
        {Object.entries(DATE_LABELS).map(([kind, label]) => (
          <li key={kind}>
            <button
              type="button"
              className={value && value.kind === kind ? 'active' : ''}
              onClick={() => onPick({ kind })}
            >
              {label}
            </button>
          </li>
        ))}
      </ul>
      <div className="date-custom">
        <label>
          Start Date
          <input
            type="date"
            value={start}
            max={end || undefined}
            onChange={(e) => setStart(e.target.value)}
          />
        </label>
        <label>
          End Date
          <input
            type="date"
            value={end}
            min={start || undefined}
            onChange={(e) => setEnd(e.target.value)}
          />
        </label>
        <button
          type="button"
          className="btn-outline"
          disabled={!start || !end}
          onClick={() => onPick({ kind: 'custom', start, end })}
        >
          Apply range
        </button>
      </div>
      {value && (
        <button type="button" className="menu-clear" onClick={onClear}>
          Clear date filter
        </button>
      )}
    </div>
  )
}

export default function AlertHistory({ onSessionLost, navFilter, onClearFilter }) {
  const [alerts, setAlerts] = useState(null)
  const [error, setError] = useState('')
  const [filters, setFilters] = useState(() => initialFilters(navFilter))
  const [search, setSearch] = useState('')
  // The search box updates on every keystroke for the instant client-side filter, but the
  // server query only follows once typing pauses.
  const [debouncedSearch, setDebouncedSearch] = useState('')

  const { alertType, dateRange } = filters
  const setAlertType = (v) => setFilters((f) => ({ ...f, alertType: v }))
  const setDateRange = (v) => setFilters((f) => ({ ...f, dateRange: v }))

  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search.trim()), 350)
    return () => clearTimeout(t)
  }, [search])

  // Normally the log is closed incidents only. A category drill-down from the dashboard's
  // "Alerts by Type" donut wants that whole category across every status, so it widens the
  // fetch to scope=all. The page remounts when navFilter changes (App.jsx key), so reading
  // it here is enough.
  const scope = navFilter && navFilter.category ? 'all' : 'history'
  // Name and date filters go to the server so the log's own controls aren't limited to the
  // most recent page (the row cap). Bounds is null when no date filter is set.
  const bounds = rangeBounds(dateRange)
  const dateFrom = bounds ? bounds.from : ''
  const dateTo = bounds ? bounds.to : ''

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams({ scope })
      if (debouncedSearch) params.set('q', debouncedSearch)
      if (dateFrom) params.set('date_from', dateFrom)
      if (dateTo) params.set('date_to', dateTo)
      const data = await api(`/barangay/alerts?${params.toString()}`)
      setAlerts(data)
      setError('')
    } catch (err) {
      setError(err.message)
      setAlerts((current) => current ?? [])
      if (err.message.includes('expired')) onSessionLost()
    }
  }, [onSessionLost, scope, debouncedSearch, dateFrom, dateTo])

  // Same poll as the Alerts page and the Dashboard: an action taken here (or on either of
  // those screens) is a real write to the shared alerts table, and the poll is what makes
  // every open screen converge without a manual refresh.
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
      (alertType === 'all' || alertCategory(a) === alertType) &&
      withinRange(parseServerTime(a.created_at), dateRange) &&
      matchesSearch(a, search)
  )

  const filtersActive =
    alertType !== 'all' || dateRange != null || search.trim() !== ''

  function clearAll() {
    setFilters({ alertType: 'all', dateRange: null })
    setSearch('')
    if (onClearFilter) onClearFilter()
  }

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

        <FilterMenu label={dateRangeLabel(dateRange)} active={dateRange != null}>
          {(close) => (
            <DateRangePanel
              value={dateRange}
              onPick={(range) => {
                setDateRange(range)
                close()
              }}
              onClear={() => {
                setDateRange(null)
                close()
              }}
            />
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

      {filtersActive && (
        <div className="filter-banner">
          <span>
            {navFilter && navFilter.label ? (
              <>
                Showing: <strong>{navFilter.label}</strong> &middot;{' '}
              </>
            ) : null}
            {visibleRows.length} of {rows.length}
          </span>
          <button type="button" className="link-btn-plain" onClick={clearAll}>
            Clear all filters
          </button>
        </div>
      )}

      {error && <p className="error">{error}</p>}

      <SectionCard
        icon={<IconHistory />}
        title={scope === 'all' ? `All ${navFilter.label || 'alerts'}` : 'History'}
      >
        {loading ? (
          <p className="muted alerts-empty">Loading&hellip;</p>
        ) : visibleRows.length === 0 ? (
          <p className="muted alerts-empty">
            {rows.length === 0 ? 'Nothing logged yet.' : 'No alerts match these filters.'}
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

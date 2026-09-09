import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, parseServerTime, POLL_MS } from '../api'
import { CATEGORY_LABEL, alertCategory, triggerLabel } from '../labels'
import { initials, dateTimeLabel } from '../format'
import { recordAccess } from '../audit'
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
import { IconHistory, IconSearch, IconChevron } from '../icons'
import SectionCard from './SectionCard'
import FilterMenu from './FilterMenu'
import AlertRow from './AlertRow'
import StatusPill from './StatusPill'
import AlertActionModals from './AlertActionModals'

// RA 10173 §11(e) (retain personal data only as long as necessary): the log opens on the
// last 30 days, not an unbounded scroll. "View full history" lifts that floor and is an
// audited action (recordAccess). The server enforces the same 30-day floor (barangay.py,
// `full` param). A real archive of resolved / false-positive rows older than the window is
// a main-lane job -- this window is the stand-in until then.
const DEFAULT_WINDOW_DAYS = 30

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

const ts = (v) => {
  const d = parseServerTime(v)
  return d ? d.getTime() : 0
}

// One past alert inside an expanded senior group. Same click-to-open-Details behaviour as
// AlertRow, but the senior's name is the group header so the line leads with the trigger.
function GroupedAlertLine({ alert, onShowDetails }) {
  const category = alertCategory(alert)
  return (
    <li
      className="grouped-alert"
      role="button"
      tabIndex={0}
      onClick={() => onShowDetails(alert)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onShowDetails(alert)
        }
      }}
    >
      <span className={`mini-alert-dot mini-alert-dot-${category}`} />
      <div className="grouped-alert-main">
        <p className="grouped-alert-title">{triggerLabel(alert.trigger_type)}</p>
        <p className="grouped-alert-meta muted">{dateTimeLabel(alert.created_at)}</p>
      </div>
      <StatusPill status={alert.status} />
    </li>
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
  // History review is about spotting patterns per senior, so the default is grouped; the
  // Alerts tab is the opposite (flat, for triage). "flat" here is a recent-activity scan.
  const [grouping, setGrouping] = useState('senior') // 'senior' | 'flat'
  const [expanded, setExpanded] = useState(() => new Set())
  // false = last 30 days only (the §11(e) default); true = the whole log, and logged.
  const [fullHistory, setFullHistory] = useState(false)

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
  // The 30-day floor only applies to the plain history view with no explicit date range.
  const windowed = scope === 'history' && !fullHistory && !dateFrom

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams({ scope })
      if (debouncedSearch) params.set('q', debouncedSearch)
      if (dateFrom) params.set('date_from', dateFrom)
      if (dateTo) params.set('date_to', dateTo)
      if (fullHistory) params.set('full', '1')
      const data = await api(`/barangay/alerts?${params.toString()}`)
      setAlerts(data)
      setError('')
    } catch (err) {
      setError(err.message)
      setAlerts((current) => current ?? [])
      if (err.message.includes('expired')) onSessionLost()
    }
  }, [onSessionLost, scope, debouncedSearch, dateFrom, dateTo, fullHistory])

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
  const rows = useMemo(() => alerts ?? [], [alerts])
  const visibleRows = useMemo(
    () =>
      rows
        .filter(
          (a) =>
            (alertType === 'all' || alertCategory(a) === alertType) &&
            withinRange(parseServerTime(a.created_at), dateRange) &&
            matchesSearch(a, search)
        )
        .sort((a, b) => ts(b.created_at) - ts(a.created_at)),
    [rows, alertType, dateRange, search]
  )

  // Grouped by senior, each group sorted newest-first and the groups themselves ordered by
  // their most recent alert.
  const groups = useMemo(() => {
    const map = new Map()
    for (const a of visibleRows) {
      let g = map.get(a.senior_sync_id)
      if (!g) {
        g = { key: a.senior_sync_id, name: a.senior_name, age: a.senior_age, alerts: [] }
        map.set(a.senior_sync_id, g)
      }
      g.alerts.push(a)
    }
    const list = [...map.values()]
    list.forEach((g) => {
      g.latest = g.alerts[0] ? g.alerts[0].created_at : null
    })
    list.sort((a, b) => ts(b.latest) - ts(a.latest))
    return list
  }, [visibleRows])

  const filtersActive =
    alertType !== 'all' || dateRange != null || search.trim() !== '' || fullHistory

  function clearAll() {
    setFilters({ alertType: 'all', dateRange: null })
    setSearch('')
    setFullHistory(false)
    if (onClearFilter) onClearFilter()
  }

  function openFullHistory() {
    setFullHistory(true)
    recordAccess('history_full_view', { scope: 'barangay', shown: visibleRows.length })
  }

  function toggleGroup(key) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  return (
    <div className="queue-page">
      <div className="history-filters">
        <div className="pill-group">
          <button
            type="button"
            className={grouping === 'senior' ? 'active' : ''}
            onClick={() => setGrouping('senior')}
          >
            By senior
          </button>
          <button
            type="button"
            className={grouping === 'flat' ? 'active' : ''}
            onClick={() => setGrouping('flat')}
          >
            Chronological
          </button>
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
        {windowed && (
          <p className="history-window-note muted">
            Showing the last {DEFAULT_WINDOW_DAYS} days.
          </p>
        )}

        {loading ? (
          <p className="muted alerts-empty">Loading&hellip;</p>
        ) : visibleRows.length === 0 ? (
          <p className="muted alerts-empty">
            {rows.length === 0 ? 'Nothing logged yet.' : 'No alerts match these filters.'}
          </p>
        ) : grouping === 'flat' ? (
          <ul className="queue-rows history-rows">
            {visibleRows.map((alert) => (
              <AlertRow key={alert.sync_id} alert={alert} onShowDetails={actions.showDetails} />
            ))}
          </ul>
        ) : (
          <ul className="history-groups">
            {groups.map((g) => {
              const open = expanded.has(g.key)
              return (
                <li key={g.key} className="history-group">
                  <button
                    type="button"
                    className="history-group-head"
                    aria-expanded={open}
                    onClick={() => toggleGroup(g.key)}
                  >
                    <span className="avatar">{initials(g.name)}</span>
                    <span className="history-group-name">{g.name}</span>
                    <span className="history-group-count">
                      {g.alerts.length} alert{g.alerts.length === 1 ? '' : 's'}
                    </span>
                    <span className="history-group-latest muted">
                      latest {dateTimeLabel(g.latest)}
                    </span>
                    <IconChevron className={`history-group-chev${open ? ' open' : ''}`} />
                  </button>
                  {open && (
                    <ul className="history-group-alerts">
                      {g.alerts.map((alert) => (
                        <GroupedAlertLine
                          key={alert.sync_id}
                          alert={alert}
                          onShowDetails={actions.showDetails}
                        />
                      ))}
                    </ul>
                  )}
                </li>
              )
            })}
          </ul>
        )}

        {windowed && !loading && (
          <button type="button" className="history-more-btn" onClick={openFullHistory}>
            <IconHistory /> View full history
          </button>
        )}
        {fullHistory && (
          <p className="muted history-full-note">Showing the full log &middot; this view was logged.</p>
        )}
      </SectionCard>

      <AlertActionModals actions={actions} />
    </div>
  )
}

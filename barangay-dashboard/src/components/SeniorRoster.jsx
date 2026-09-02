import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../api'
import { initials } from '../format'
import {
  SENIOR_STATUS_OPTIONS,
  SENIOR_STATUS_LABEL,
  matchesSeniorStatus,
  matchesSeniorSearch,
} from '../seniorFilters'
import { IconSeniors, IconSearch } from '../icons'
import SectionCard from './SectionCard'
import FilterMenu from './FilterMenu'
import SeniorDetail from './SeniorDetail'

// Client-only deactivation store. The cloud `seniors` table has no active/deactivated
// column, and adding one is a schema migration owned by the Android/backend lane
// (barangay-dashboard/CLAUDE.md §2) -- so this keeps the flow real and persistent on this
// machine. When `seniors.status` and a deactivate endpoint exist, replace loadDeactivated
// + the localStorage write in setDeactivatedFor with the API calls; nothing else changes.
const DEACT_KEY = 'seenior.deactivatedSeniors'
function loadDeactivated() {
  try {
    return new Set(JSON.parse(localStorage.getItem(DEACT_KEY) || '[]'))
  } catch {
    return new Set()
  }
}

export default function SeniorRoster({ onSessionLost }) {
  const [seniors, setSeniors] = useState(null)
  const [error, setError] = useState('')
  const [statusFilter, setStatusFilter] = useState('all') // 'all' | 'active' | 'deactivated'
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(null) // sync_id of the senior being viewed
  const [deactivated, setDeactivated] = useState(() => loadDeactivated())

  function setDeactivatedFor(syncId, value) {
    setDeactivated((prev) => {
      const next = new Set(prev)
      if (value) next.add(syncId)
      else next.delete(syncId)
      try {
        localStorage.setItem(DEACT_KEY, JSON.stringify([...next]))
      } catch {
        /* private mode / storage full -- the in-memory Set still drives this session */
      }
      return next
    })
  }

  const load = useCallback(async () => {
    try {
      const data = await api('/barangay/seniors')
      setSeniors(data)
      setError('')
    } catch (err) {
      setError(err.message)
      setSeniors((current) => current ?? [])
      if (err.message.includes('expired')) onSessionLost()
    }
  }, [onSessionLost])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load()
  }, [load])

  const rows = useMemo(() => seniors ?? [], [seniors])
  const loading = seniors === null

  const visible = useMemo(
    () =>
      rows.filter(
        (senior) =>
          matchesSeniorStatus(deactivated.has(senior.sync_id), statusFilter) &&
          matchesSeniorSearch(senior, search)
      ),
    [rows, deactivated, statusFilter, search]
  )

  if (selected) {
    return (
      <SeniorDetail
        syncId={selected}
        fallbackSenior={rows.find((s) => s.sync_id === selected) || null}
        isDeactivated={deactivated.has(selected)}
        onDeactivate={() => setDeactivatedFor(selected, true)}
        onBack={() => {
          setSelected(null)
          load()
        }}
        onSessionLost={onSessionLost}
      />
    )
  }

  return (
    <div className="queue-page">
      <div className="history-filters">
        <div className="pill-group">
          <button
            type="button"
            className={statusFilter === 'all' ? 'active' : ''}
            onClick={() => setStatusFilter('all')}
          >
            All
          </button>
        </div>

        <FilterMenu
          label={statusFilter === 'all' ? 'Status' : SENIOR_STATUS_LABEL[statusFilter]}
          active={statusFilter !== 'all'}
        >
          {(close) => (
            <ul className="menu-list">
              {SENIOR_STATUS_OPTIONS.map((opt) => (
                <li key={opt}>
                  <button
                    type="button"
                    className={statusFilter === opt ? 'active' : ''}
                    onClick={() => {
                      setStatusFilter(opt)
                      close()
                    }}
                  >
                    {SENIOR_STATUS_LABEL[opt]}
                  </button>
                </li>
              ))}
              {statusFilter !== 'all' && (
                <li>
                  <button
                    type="button"
                    className="menu-clear"
                    onClick={() => {
                      setStatusFilter('all')
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

      <SectionCard icon={<IconSeniors />} title="Senior List">
        {loading ? (
          <p className="muted alerts-empty">Loading&hellip;</p>
        ) : visible.length === 0 ? (
          <p className="muted alerts-empty">
            {rows.length === 0
              ? 'No seniors registered in your barangay yet.'
              : 'No seniors match these filters.'}
          </p>
        ) : (
          <ul className="senior-rows">
            {visible.map((senior) => {
              const off = deactivated.has(senior.sync_id)
              return (
                <li key={senior.sync_id} className="senior-row">
                  <span className="avatar">
                    {initials(`${senior.first_name} ${senior.last_name}`)}
                  </span>
                  <p className="senior-row-name">
                    {senior.first_name} {senior.last_name}
                  </p>
                  <span className={`status-badge ${off ? 'status-off' : 'status-on'}`}>
                    <span className="status-dot" />
                    {off ? 'Deactivated' : 'Active'}
                  </span>
                  <button
                    type="button"
                    className="btn-outline senior-row-btn"
                    onClick={() => setSelected(senior.sync_id)}
                  >
                    Details
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </SectionCard>
    </div>
  )
}

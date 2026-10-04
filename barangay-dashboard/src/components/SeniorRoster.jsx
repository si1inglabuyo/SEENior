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
import DeviceBadge from './DeviceBadge'

export default function SeniorRoster({ onSessionLost }) {
  const [seniors, setSeniors] = useState(null)
  const [error, setError] = useState('')
  const [statusFilter, setStatusFilter] = useState('all') // 'all' | 'active' | 'deactivated'
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(null) // sync_id of the senior being viewed

    // Saved on the server (PATCH /seniors/{sync_id}/status) so it is the same on every
    // machine. Rejects on failure; the list only changes once the server agrees.
  async function setDeactivatedFor(syncId, value) {
    const updated = await api(`/seniors/${syncId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status: value ? 'inactive' : 'active' }),
    })
    setSeniors((current) =>
      (current ?? []).map((s) => (s.sync_id === syncId ? { ...s, status: updated.status } : s))
    )
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
          matchesSeniorStatus(senior.status === 'inactive', statusFilter) &&
          matchesSeniorSearch(senior, search)
      ),
    [rows, statusFilter, search]
  )

  if (selected) {
    return (
      <SeniorDetail
        syncId={selected}
        fallbackSenior={rows.find((s) => s.sync_id === selected) || null}
        isDeactivated={rows.find((s) => s.sync_id === selected)?.status === 'inactive'}
        onDeactivate={() => setDeactivatedFor(selected, true)}
        onReactivate={() => setDeactivatedFor(selected, false)}
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
              const off = senior.status === 'inactive'
              return (
                <li
                  key={senior.sync_id}
                  className="senior-row senior-row-clickable"
                  role="button"
                  tabIndex={0}
                  onClick={() => setSelected(senior.sync_id)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      setSelected(senior.sync_id)
                    }
                  }}
                >
                  <span className="avatar">
                    {initials(`${senior.first_name} ${senior.last_name}`)}
                  </span>
                  <div className="senior-row-main">
                    <p className="senior-row-name">
                      {senior.first_name} {senior.last_name}
                    </p>
                    <DeviceBadge senior={senior} className="senior-row-device" />
                  </div>
                  <span className={`status-badge ${off ? 'status-off' : 'status-on'}`}>
                    <span className="status-dot" />
                    {off ? 'Deactivated' : 'Active'}
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </SectionCard>
    </div>
  )
}

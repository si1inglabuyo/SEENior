import { useEffect, useState } from 'react'
import { api, timeAgo } from '../api'

export default function SeniorRoster({ onSessionLost }) {
  const [seniors, setSeniors] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api('/barangay/seniors')
      .then(setSeniors)
      .catch((err) => {
        setError(err.message)
        if (err.message.includes('expired')) onSessionLost()
      })
      .finally(() => setLoading(false))
  }, [onSessionLost])

  if (loading) return <p className="muted">Loading…</p>
  if (error) return <p className="error">{error}</p>

  return (
    <section>
      <h2>Seniors in your barangay</h2>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th><th>Age</th><th>Address</th><th>Mobile</th>
              <th>Phone last heard from</th><th>Battery</th><th>Open</th>
            </tr>
          </thead>
          <tbody>
            {seniors.map((senior) => (
              <tr key={senior.sync_id}>
                <td>{senior.last_name}, {senior.first_name}</td>
                <td>{senior.age}</td>
                <td>{senior.address}</td>
                <td><a href={`tel:${senior.mobile_number}`}>{senior.mobile_number}</a></td>
                {/* "Never" and "2 min ago" are completely different situations: the first
                    means this phone has never checked in at all. */}
                <td className={senior.last_seen_at ? '' : 'warn'}>
                  {senior.last_seen_at ? timeAgo(senior.last_seen_at) : 'Never checked in'}
                </td>
                <td>
                  {senior.battery_percent == null
                    ? '—'
                    : `${senior.battery_percent}%${senior.is_charging ? ' ⚡' : ''}`}
                </td>
                <td>{senior.open_incidents || '—'}</td>
              </tr>
            ))}
            {seniors.length === 0 && (
              <tr>
                <td colSpan={7} className="muted">
                  No seniors registered in your barangay yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

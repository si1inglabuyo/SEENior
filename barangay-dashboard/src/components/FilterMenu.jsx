import { useEffect, useRef, useState } from 'react'
import { IconChevron } from '../icons'

// A label button that opens a popover panel -- used for both the Alert Type and Date Range
// dropdowns on Alert History. `children` is a render function given a `close` callback, so
// a menu option or an "Apply range" button can dismiss the panel after it acts. Closes on
// an outside click or Escape.
export default function FilterMenu({ label, active, children }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return
    function onDown(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false)
    }
    function onKey(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="filter-menu" ref={ref}>
      <button
        type="button"
        className={`filter-menu-btn${active ? ' has-value' : ''}`}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <span>{label}</span>
        <IconChevron />
      </button>
      {open && <div className="filter-menu-panel">{children(() => setOpen(false))}</div>}
    </div>
  )
}

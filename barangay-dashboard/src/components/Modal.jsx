import { useEffect } from 'react'

// The one overlay every dialog on this dashboard is built from -- the confirm dialog, the
// success toast, and the alert details panel are all just different content inside this
// same shell. Centred card, backdrop click and Escape both close it.
export default function Modal({ onClose, children, className = '', labelledBy }) {
  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className={`modal-card ${className}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        onClick={(event) => event.stopPropagation()}
      >
        {children}
      </div>
    </div>
  )
}

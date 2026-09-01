import { useEffect } from 'react'
import { IconCheck } from '../icons'
import Modal from './Modal'

// The one success toast for all three row actions. `message` is the only thing that
// changes between them (see ALERT_ACTIONS[...].successMessage in src/alertActions.js).
// Auto-dismisses, but the X and a backdrop click both close it early.
export default function Toast({ message, onClose, duration = 2500 }) {
  useEffect(() => {
    const timer = setTimeout(onClose, duration)
    return () => clearTimeout(timer)
  }, [onClose, duration])

  return (
    <Modal onClose={onClose} className="success-toast">
      <button type="button" className="modal-close" onClick={onClose} aria-label="Close">
        ✕
      </button>
      <span className="success-icon">
        <IconCheck />
      </span>
      <p className="success-message">{message}</p>
    </Modal>
  )
}

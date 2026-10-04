import { useEffect } from 'react'
import { IconCheck } from '../icons'
import Modal from './Modal'

// The success toast for all three row actions; only `message` differs. Auto-dismisses, or
// closes early on the X or a backdrop click.
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

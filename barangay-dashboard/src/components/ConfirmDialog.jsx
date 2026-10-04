import Modal from './Modal'

// The confirm dialog for all three row actions. The title, message and colour come from
// `action` (see src/alertActions.js).
export default function ConfirmDialog({ action, error, busy, onCancel, onConfirm }) {
  return (
    <Modal onClose={busy ? () => {} : onCancel} labelledBy="confirm-dialog-title" className="confirm-dialog">
      <h2 id="confirm-dialog-title" className="confirm-title">
        {action.dialogTitle}
      </h2>
      <p className="confirm-message">{action.dialogMessage}</p>
      {error && <p className="error confirm-error">{error}</p>}
      <div className="confirm-actions">
        <button type="button" className="confirm-btn confirm-cancel" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button
          type="button"
          className={`confirm-btn confirm-ok confirm-ok-${action.tone}`}
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? 'Please wait…' : action.confirmLabel}
        </button>
      </div>
    </Modal>
  )
}

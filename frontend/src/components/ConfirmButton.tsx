interface ConfirmButtonProps {
  disabled: boolean;
  submitting: boolean;
  onConfirm: () => void;
}

export function ConfirmButton({ disabled, submitting, onConfirm }: ConfirmButtonProps) {
  return (
    <button type="button" className="confirm-button" disabled={disabled || submitting} onClick={onConfirm}>
      {submitting ? 'Submitting…' : 'Confirm split'}
    </button>
  );
}

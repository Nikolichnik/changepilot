import type { ChangeStatus } from '../types/change';
import { STATUS_LABELS } from './changePresentation';

export function StatusBadge({ status }: { status: ChangeStatus }) {
  return <span className={`badge badge--status badge--${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>;
}

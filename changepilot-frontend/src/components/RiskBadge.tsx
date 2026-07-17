import type { RiskLevel } from '../types/change';
import { RISK_LABELS } from './changePresentation';

export function RiskBadge({ risk }: { risk: RiskLevel }) {
  return <span className={`badge badge--risk badge--risk-${risk.toLowerCase()}`}>{RISK_LABELS[risk]}</span>;
}

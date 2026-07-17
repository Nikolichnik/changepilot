import type { ChangeStatus, RiskLevel } from '../types/change';

export const STATUS_LABELS: Record<ChangeStatus, string> = {
  DRAFT: 'Draft',
  READY: 'Ready',
  IN_PROGRESS: 'In Progress',
  VERIFIED: 'Verified',
  DONE: 'Done'
};

export const RISK_LABELS: Record<RiskLevel, string> = {
  LOW: 'Low risk',
  MEDIUM: 'Medium risk',
  HIGH: 'High risk'
};

export const CHANGE_STATUSES = ['DRAFT', 'READY', 'IN_PROGRESS', 'VERIFIED', 'DONE'] as const;
export type ChangeStatus = (typeof CHANGE_STATUSES)[number];

export const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH'] as const;
export type RiskLevel = (typeof RISK_LEVELS)[number];

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface AcceptanceCriterion {
  id: string;
  text: string;
  completed: boolean;
}

export interface EngineeringChangeSummary {
  id: string;
  title: string;
  risk: RiskLevel;
  status: ChangeStatus;
  completedCriteriaCount: number;
  totalCriteriaCount: number;
  updatedAt: string;
}

export interface EngineeringChangeDetail extends EngineeringChangeSummary {
  description: string;
  affectedComponents: string[];
  criteria: AcceptanceCriterion[];
  availableTransitions: ChangeStatus[];
  deletable: boolean;
  createdAt: string;
}

export interface EngineeringChangeCriterionInput {
  id?: string;
  text: string;
}

export interface EngineeringChangeUpsertInput {
  title: string;
  description: string;
  risk: RiskLevel;
  affectedComponents: string[];
  criteria: EngineeringChangeCriterionInput[];
}

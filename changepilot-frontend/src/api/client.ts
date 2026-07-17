import type {
  ApiFieldError,
  ChangeStatus,
  EngineeringChangeDetail,
  EngineeringChangeSummary,
  EngineeringChangeUpsertInput
} from '../types/change';

const DEFAULT_API_BASE_URL = 'http://localhost:8080';
const ENGINEERING_CHANGES_PATH = '/api/engineering-changes';

interface ApiErrorPayload {
  status?: number;
  code?: string;
  message?: string;
  fieldErrors?: ApiFieldError[];
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ApiFieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: ApiFieldError[] = []) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

function buildBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL?.trim() || DEFAULT_API_BASE_URL;
  return configured.replace(/\/$/, '');
}

function buildUrl(path = '', search?: URLSearchParams): string {
  const encodedPath = path
    .split('/')
    .filter(Boolean)
    .map((segment) => encodeURIComponent(segment))
    .join('/');
  const url = new URL(`${buildBaseUrl()}${ENGINEERING_CHANGES_PATH}${encodedPath ? `/${encodedPath}` : ''}`);
  if (search) {
    url.search = search.toString();
  }
  return url.toString();
}

function buildSegmentUrl(segments: string[], search?: URLSearchParams): string {
  const encodedPath = segments.map((segment) => encodeURIComponent(segment)).join('/');
  const url = new URL(`${buildBaseUrl()}${ENGINEERING_CHANGES_PATH}${encodedPath ? `/${encodedPath}` : ''}`);
  if (search) {
    url.search = search.toString();
  }
  return url.toString();
}

async function parseError(response: Response): Promise<ApiClientError> {
  let payload: ApiErrorPayload | null = null;
  try {
    payload = (await response.json()) as ApiErrorPayload;
  } catch {
    payload = null;
  }

  return new ApiClientError(
    response.status,
    payload?.code || `HTTP_${response.status}`,
    payload?.message || response.statusText || 'Request failed',
    Array.isArray(payload?.fieldErrors) ? payload.fieldErrors : []
  );
}

async function requestSegments<T>(segments: string[], init?: RequestInit): Promise<T> {
  return requestAbsolute<T>(buildSegmentUrl(segments), init);
}

async function requestAbsolute<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {})
      },
      ...init
    });
  } catch {
    throw new ApiClientError(0, 'NETWORK_ERROR', 'Unable to reach ChangePilot API');
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

function serializeUpsert(input: EngineeringChangeUpsertInput): string {
  return JSON.stringify({
    title: input.title,
    description: input.description,
    risk: input.risk,
    affectedComponents: input.affectedComponents,
    criteria: input.criteria.map((criterion) =>
      criterion.id ? { id: criterion.id, text: criterion.text } : { text: criterion.text }
    )
  });
}

export const apiClient = {
  async listChanges(status?: ChangeStatus): Promise<EngineeringChangeSummary[]> {
    const search = new URLSearchParams();
    if (status) {
      search.set('status', status);
    }
    return requestAbsolute<EngineeringChangeSummary[]>(buildUrl('', search), {
      method: 'GET',
      headers: { Accept: 'application/json' }
    });
  },

  getChange(id: string): Promise<EngineeringChangeDetail> {
    return requestSegments<EngineeringChangeDetail>([id], { method: 'GET' });
  },

  createChange(input: EngineeringChangeUpsertInput): Promise<EngineeringChangeDetail> {
    return requestAbsolute<EngineeringChangeDetail>(buildUrl(), {
      method: 'POST',
      body: serializeUpsert(input)
    });
  },

  updateChange(id: string, input: EngineeringChangeUpsertInput): Promise<EngineeringChangeDetail> {
    return requestSegments<EngineeringChangeDetail>([id], {
      method: 'PUT',
      body: serializeUpsert(input)
    });
  },

  updateCriterionCompletion(id: string, criterionId: string, completed: boolean): Promise<EngineeringChangeDetail> {
    return requestSegments<EngineeringChangeDetail>([id, 'criteria', criterionId], {
      method: 'PATCH',
      body: JSON.stringify({ completed })
    });
  },

  updateStatus(id: string, targetStatus: ChangeStatus): Promise<EngineeringChangeDetail> {
    return requestSegments<EngineeringChangeDetail>([id, 'status'], {
      method: 'PATCH',
      body: JSON.stringify({ targetStatus })
    });
  },

  deleteChange(id: string): Promise<void> {
    return requestSegments<void>([id], { method: 'DELETE' });
  }
};

export { buildUrl };

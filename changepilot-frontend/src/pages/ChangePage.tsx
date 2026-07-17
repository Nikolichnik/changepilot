import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiClientError, apiClient } from '../api/client';
import { ChangeForm, type ChangeFormValues } from '../components/ChangeForm';
import { STATUS_LABELS } from '../components/changePresentation';
import { PageShell } from '../components/PageShell';
import { RiskBadge } from '../components/RiskBadge';
import { StatusBadge } from '../components/StatusBadge';
import type { ApiFieldError, ChangeStatus, EngineeringChangeDetail, EngineeringChangeUpsertInput } from '../types/change';
import { areDetailActionsDisabled } from './changePageState';

const CREATE_INITIAL_VALUES: ChangeFormValues = {
  title: '',
  description: '',
  risk: 'MEDIUM',
  affectedComponents: [],
  criteria: [{ text: '' }]
};

function formatTimestamp(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

function toFormValues(detail: EngineeringChangeDetail): ChangeFormValues {
  return {
    title: detail.title,
    description: detail.description,
    risk: detail.risk,
    affectedComponents: detail.affectedComponents,
    criteria: detail.criteria.map((criterion) => ({ id: criterion.id, text: criterion.text }))
  };
}

function actionErrorMessage(caught: unknown, fallback: string): { message: string; fieldErrors: ApiFieldError[] } {
  if (caught instanceof ApiClientError) {
    return { message: caught.message, fieldErrors: caught.fieldErrors };
  }
  return { message: fallback, fieldErrors: [] };
}

export function ChangePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isCreateRoute = !id;
  const [detail, setDetail] = useState<EngineeringChangeDetail | null>(null);
  const [loading, setLoading] = useState(!isCreateRoute);
  const [pageError, setPageError] = useState<string | null>(null);
  const [editMode, setEditMode] = useState(isCreateRoute);
  const [formBusy, setFormBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [pendingStatus, setPendingStatus] = useState<ChangeStatus | null>(null);
  const [pendingCriterionId, setPendingCriterionId] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const mutationLock = useRef(false);

  useEffect(() => {
    if (isCreateRoute || !id) {
      return;
    }
    let active = true;
    setLoading(true);
    setPageError(null);
    apiClient
      .getChange(id)
      .then((response) => {
        if (active) {
          setDetail(response);
        }
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }
        const message = caught instanceof ApiClientError ? caught.message : 'Unable to load this engineering change.';
        setPageError(message);
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [id, isCreateRoute]);

  const canEditMetadata = isCreateRoute || (detail?.status !== 'DONE');
  const criteriaEditable = isCreateRoute || (detail?.status !== 'VERIFIED' && detail?.status !== 'DONE');
  const canToggleCriteria = detail?.status !== 'VERIFIED' && detail?.status !== 'DONE';
  const mutationBusy = formBusy || Boolean(pendingStatus) || Boolean(pendingCriterionId) || deleteBusy;
  const detailActionsDisabled = areDetailActionsDisabled(editMode, mutationBusy);

  const formValues = useMemo(() => {
    if (detail) {
      return toFormValues(detail);
    }
    return CREATE_INITIAL_VALUES;
  }, [detail]);

  async function handleSubmit(values: EngineeringChangeUpsertInput) {
    if (mutationLock.current) {
      return;
    }
    mutationLock.current = true;
    setFormBusy(true);
    setFormError(null);
    setFieldErrors([]);
    try {
      if (isCreateRoute) {
        const created = await apiClient.createChange(values);
        await navigate(`/changes/${created.id}`);
        return;
      }
      if (!id) {
        return;
      }
      const updated = await apiClient.updateChange(id, values);
      setDetail(updated);
      setEditMode(false);
    } catch (caught) {
      const actionError = actionErrorMessage(caught, 'Unable to save this engineering change.');
      setFormError(actionError.message);
      setFieldErrors(actionError.fieldErrors);
    } finally {
      setFormBusy(false);
      mutationLock.current = false;
    }
  }

  async function handleStatusTransition(targetStatus: ChangeStatus) {
    if (!id || editMode || mutationLock.current) {
      return;
    }
    mutationLock.current = true;
    setPendingStatus(targetStatus);
    setPageError(null);
    try {
      const updated = await apiClient.updateStatus(id, targetStatus);
      setDetail(updated);
      setEditMode(false);
    } catch (caught) {
      setPageError(actionErrorMessage(caught, 'Unable to update status.').message);
    } finally {
      setPendingStatus(null);
      mutationLock.current = false;
    }
  }

  async function handleCriterionToggle(criterionId: string, completed: boolean) {
    if (!id || editMode || mutationLock.current) {
      return;
    }
    mutationLock.current = true;
    setPendingCriterionId(criterionId);
    setPageError(null);
    try {
      const updated = await apiClient.updateCriterionCompletion(id, criterionId, completed);
      setDetail(updated);
    } catch (caught) {
      setPageError(actionErrorMessage(caught, 'Unable to update criterion completion.').message);
    } finally {
      setPendingCriterionId(null);
      mutationLock.current = false;
    }
  }

  async function handleDelete() {
    if (!id || !detail?.deletable || editMode || mutationLock.current) {
      return;
    }
    if (!window.confirm(`Delete "${detail.title}"? This cannot be undone.`)) {
      return;
    }
    mutationLock.current = true;
    setDeleteBusy(true);
    setPageError(null);
    try {
      await apiClient.deleteChange(id);
      await navigate('/');
    } catch (caught) {
      setPageError(actionErrorMessage(caught, 'Unable to delete this engineering change.').message);
    } finally {
      setDeleteBusy(false);
      mutationLock.current = false;
    }
  }

  if (isCreateRoute) {
    return (
      <PageShell
        eyebrow="New request"
        title="Open a new engineering change"
        subtitle="Capture the scope, risk, and acceptance gate before the lifecycle rail begins."
        actions={
          <Link className="button button--ghost" to="/">
            Back to list
          </Link>
        }
      >
        <ChangeForm
          mode="create"
          initialValues={formValues}
          busy={formBusy}
          criteriaEditable
          fieldErrors={fieldErrors}
          formError={formError}
          onSubmit={handleSubmit}
        />
      </PageShell>
    );
  }

  if (loading) {
    return (
      <PageShell title="Loading change" subtitle="Pulling detail from the backend control track.">
        <section className="card state">Loading engineering change…</section>
      </PageShell>
    );
  }

  if (pageError && !detail) {
    return (
      <PageShell title="Change unavailable" subtitle="The requested record could not be loaded.">
        <section className="card state state--error" role="alert" aria-live="assertive">
          {pageError}
        </section>
      </PageShell>
    );
  }

  if (!detail) {
    return null;
  }

  return (
    <PageShell
      eyebrow="Change detail"
      title={detail.title}
      subtitle="Inspect lifecycle state, acceptance gate progress, and backend-authoritative actions."
      actions={
        <div className="button-row">
          <Link className="button button--ghost" to="/">
            Back to list
          </Link>
          {canEditMetadata && !editMode ? (
            <button className="button button--primary" type="button" onClick={() => setEditMode(true)} disabled={mutationBusy}>
              Edit metadata
            </button>
          ) : null}
        </div>
      }
    >
      {pageError ? (
        <section className="notice notice--error" role="alert" aria-live="assertive">
          {pageError}
        </section>
      ) : null}

      <section className="detail-grid detail-grid--top">
        <article className="card detail-panel">
          <div className="detail-header">
            <StatusBadge status={detail.status} />
            <RiskBadge risk={detail.risk} />
          </div>
          <p className="detail-description">{detail.description}</p>
          <dl className="detail-meta">
            <div>
              <dt>Created</dt>
              <dd>{formatTimestamp(detail.createdAt)}</dd>
            </div>
            <div>
              <dt>Updated</dt>
              <dd>{formatTimestamp(detail.updatedAt)}</dd>
            </div>
            <div>
              <dt>Completion</dt>
              <dd>
                {detail.completedCriteriaCount}/{detail.totalCriteriaCount} criteria complete
              </dd>
            </div>
          </dl>
        </article>

        <article className="card lifecycle-panel">
          <div className="section-heading">
            <div>
              <h2>Lifecycle rail</h2>
              <p>Status transitions come directly from the backend response.</p>
            </div>
          </div>
          <div className="lifecycle-panel__status">
            <span className="transition-current">Current: {STATUS_LABELS[detail.status]}</span>
            {detail.availableTransitions.length === 0 ? <p className="muted">No transitions available.</p> : null}
          </div>
          <div className="transition-list lifecycle-panel__actions">
            {detail.availableTransitions.map((status) => (
              <button
                className="button button--ghost"
                key={status}
                type="button"
                onClick={() => handleStatusTransition(status)}
                disabled={detailActionsDisabled}
              >
                {pendingStatus === status ? 'Updating…' : `Move to ${STATUS_LABELS[status]}`}
              </button>
            ))}
          </div>
          {detail.deletable ? (
            <div className="lifecycle-panel__danger">
              <button className="button button--danger" type="button" onClick={handleDelete} disabled={detailActionsDisabled}>
                {deleteBusy ? 'Deleting…' : 'Delete draft'}
              </button>
            </div>
          ) : null}
        </article>
      </section>

      {editMode ? (
        <section className="detail-edit-section">
          <ChangeForm
            mode="edit"
            initialValues={formValues}
            busy={formBusy}
            criteriaEditable={Boolean(criteriaEditable)}
            fieldErrors={fieldErrors}
            formError={formError}
            onSubmit={handleSubmit}
            onCancel={() => {
              setEditMode(false);
              setFormError(null);
              setFieldErrors([]);
            }}
          />
        </section>
      ) : null}

      <section className="detail-grid detail-grid--stacked">
        <article className="card detail-panel">
          <div className="section-heading">
            <div>
              <h2>Affected components</h2>
              <p>Operational footprint for this change.</p>
            </div>
          </div>
          {detail.affectedComponents.length === 0 ? (
            <p className="muted">No components listed.</p>
          ) : (
            <ul className="chip-list">
              {detail.affectedComponents.map((component) => (
                <li key={component}>{component}</li>
              ))}
            </ul>
          )}
        </article>

        <article className="card detail-panel">
          <div className="section-heading">
            <div>
              <h2>Acceptance gate</h2>
              <p>
                {canToggleCriteria
                  ? 'Toggle completion directly here. Text and membership are handled in edit mode.'
                  : 'Criteria completion is locked by the current backend status.'}
              </p>
            </div>
          </div>
          <ul className="criteria-list">
            {detail.criteria.map((criterion) => (
              <li className="criterion-row" key={criterion.id}>
                <label>
                  <input
                    type="checkbox"
                    checked={criterion.completed}
                    disabled={!canToggleCriteria || detailActionsDisabled}
                    onChange={(event) => handleCriterionToggle(criterion.id, event.target.checked)}
                  />
                  <span>{criterion.text}</span>
                </label>
                <strong>{criterion.completed ? 'Complete' : 'Open'}</strong>
              </li>
            ))}
          </ul>
        </article>
      </section>
    </PageShell>
  );
}

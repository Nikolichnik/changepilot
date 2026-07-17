import { useEffect, useMemo, useState } from 'react';
import type { ApiFieldError, EngineeringChangeCriterionInput, EngineeringChangeUpsertInput, RiskLevel } from '../types/change';
import { RISK_LEVELS } from '../types/change';

export interface ChangeFormValues {
  title: string;
  description: string;
  risk: RiskLevel;
  affectedComponents: string[];
  criteria: EngineeringChangeCriterionInput[];
}

interface ChangeFormProps {
  mode: 'create' | 'edit';
  initialValues: ChangeFormValues;
  busy: boolean;
  criteriaEditable: boolean;
  fieldErrors: ApiFieldError[];
  formError?: string | null;
  onSubmit: (values: EngineeringChangeUpsertInput) => Promise<void> | void;
  onCancel?: () => void;
}

interface ClientErrors {
  title?: string;
  description?: string;
  risk?: string;
  criteria?: string;
}

function toFieldMessages(fieldErrors: ApiFieldError[], prefix: string): string[] {
  return fieldErrors.filter((error) => error.field === prefix || error.field.startsWith(`${prefix}[`) || error.field.startsWith(`${prefix}.`)).map((error) => error.message);
}

export function ChangeForm({ mode, initialValues, busy, criteriaEditable, fieldErrors, formError, onSubmit, onCancel }: ChangeFormProps) {
  const [values, setValues] = useState<ChangeFormValues>(initialValues);
  const [clientErrors, setClientErrors] = useState<ClientErrors>({});

  useEffect(() => {
    setValues(initialValues);
    setClientErrors({});
  }, [initialValues]);

  const serverFieldMessages = useMemo(
    () => ({
      title: toFieldMessages(fieldErrors, 'title'),
      description: toFieldMessages(fieldErrors, 'description'),
      risk: toFieldMessages(fieldErrors, 'risk'),
      affectedComponents: toFieldMessages(fieldErrors, 'affectedComponents'),
      criteria: toFieldMessages(fieldErrors, 'criteria')
    }),
    [fieldErrors]
  );

  function updateValue<K extends keyof ChangeFormValues>(key: K, next: ChangeFormValues[K]) {
    setValues((current) => ({ ...current, [key]: next }));
  }

  function updateArrayItem(key: 'affectedComponents' | 'criteria', index: number, next: string) {
    setValues((current) => {
      if (key === 'affectedComponents') {
        const copy = [...current.affectedComponents];
        copy[index] = next;
        return { ...current, affectedComponents: copy };
      }
      const copy = current.criteria.map((criterion, criterionIndex) => (criterionIndex === index ? { ...criterion, text: next } : criterion));
      return { ...current, criteria: copy };
    });
  }

  function addComponent() {
    updateValue('affectedComponents', [...values.affectedComponents, '']);
  }

  function removeComponent(index: number) {
    updateValue(
      'affectedComponents',
      values.affectedComponents.filter((_, componentIndex) => componentIndex !== index)
    );
  }

  function addCriterion() {
    updateValue('criteria', [...values.criteria, { text: '' }]);
  }

  function removeCriterion(index: number) {
    updateValue(
      'criteria',
      values.criteria.filter((_, criterionIndex) => criterionIndex !== index)
    );
  }

  function validate(): EngineeringChangeUpsertInput | null {
    const normalizedTitle = values.title.trim();
    const normalizedDescription = values.description.trim();
    const normalizedComponents = values.affectedComponents.map((item) => item.trim()).filter(Boolean);
    const normalizedCriteria = values.criteria.map((criterion) => ({ ...criterion, text: criterion.text.trim() }));

    const nextErrors: ClientErrors = {};
    if (!normalizedTitle) {
      nextErrors.title = 'Title is required.';
    }
    if (!normalizedDescription) {
      nextErrors.description = 'Description is required.';
    }
    if (!values.risk) {
      nextErrors.risk = 'Risk is required.';
    }
    if (normalizedCriteria.length === 0 || normalizedCriteria.some((criterion) => !criterion.text)) {
      nextErrors.criteria = 'Provide at least one non-empty acceptance criterion.';
    }

    setClientErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return null;
    }

    return {
      title: normalizedTitle,
      description: normalizedDescription,
      risk: values.risk,
      affectedComponents: normalizedComponents,
      criteria: normalizedCriteria.map((criterion) => (criterion.id ? { id: criterion.id, text: criterion.text } : { text: criterion.text }))
    };
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const payload = validate();
    if (!payload) {
      return;
    }
    await onSubmit(payload);
  }

  return (
    <form className="card form-panel" onSubmit={handleSubmit}>
      <div className="section-heading section-heading--form">
        <div>
          <h2>{mode === 'create' ? 'New change flight plan' : 'Edit change metadata'}</h2>
          <p>Titles, narrative, risk, components, and draft criteria live here.</p>
        </div>
      </div>

      {formError ? (
        <div className="notice notice--error" role="alert" aria-live="assertive">
          {formError}
        </div>
      ) : null}

      <div className="form-grid">
        <label>
          <span>Title</span>
          <input
            name="title"
            value={values.title}
            onChange={(event) => updateValue('title', event.target.value)}
            disabled={busy}
            required
          />
          {clientErrors.title ? <small className="field-error">{clientErrors.title}</small> : null}
          {serverFieldMessages.title.map((message) => (
            <small className="field-error" key={message}>
              {message}
            </small>
          ))}
        </label>

        <label>
          <span>Risk</span>
          <select value={values.risk} onChange={(event) => updateValue('risk', event.target.value as RiskLevel)} disabled={busy}>
            {RISK_LEVELS.map((risk) => (
              <option key={risk} value={risk}>
                {risk}
              </option>
            ))}
          </select>
          {clientErrors.risk ? <small className="field-error">{clientErrors.risk}</small> : null}
          {serverFieldMessages.risk.map((message) => (
            <small className="field-error" key={message}>
              {message}
            </small>
          ))}
        </label>

        <label className="form-grid__full">
          <span>Description</span>
          <textarea
            name="description"
            value={values.description}
            onChange={(event) => updateValue('description', event.target.value)}
            disabled={busy}
            rows={5}
            required
          />
          {clientErrors.description ? <small className="field-error">{clientErrors.description}</small> : null}
          {serverFieldMessages.description.map((message) => (
            <small className="field-error" key={message}>
              {message}
            </small>
          ))}
        </label>
      </div>

      <section className="form-section">
        <div className="section-heading">
          <div>
            <h3>Affected components</h3>
            <p>Optional, trimmed on submit, duplicates rejected by the backend.</p>
          </div>
          <button className="button button--ghost button--compact" type="button" onClick={addComponent} disabled={busy}>
            Add component
          </button>
        </div>
        <div className="stack-list">
          {values.affectedComponents.length === 0 ? <p className="muted">No components listed yet.</p> : null}
          {values.affectedComponents.map((component, index) => (
            <div className="repeated-row" key={`component-${index}`}>
              <label className="repeated-row__field">
                <span className="sr-only">Affected component {index + 1}</span>
                <input
                  value={component}
                  onChange={(event) => updateArrayItem('affectedComponents', index, event.target.value)}
                  disabled={busy}
                />
              </label>
              <div className="repeated-row__action">
                <button className="button button--ghost button--compact" type="button" onClick={() => removeComponent(index)} disabled={busy}>
                  Remove
                </button>
              </div>
            </div>
          ))}
          {serverFieldMessages.affectedComponents.map((message) => (
            <small className="field-error" key={message}>
              {message}
            </small>
          ))}
        </div>
      </section>

      <section className="form-section">
        <div className="section-heading">
          <div>
            <h3>Acceptance criteria</h3>
            <p>{criteriaEditable ? 'Edit scope criteria here. Completion is managed separately from the detail view.' : 'Criteria are locked by the current backend status.'}</p>
          </div>
          {criteriaEditable ? (
            <button className="button button--ghost button--compact" type="button" onClick={addCriterion} disabled={busy}>
              Add criterion
            </button>
          ) : null}
        </div>
        <div className="stack-list">
          {values.criteria.map((criterion, index) => (
            <div className="repeated-row repeated-row--top" key={criterion.id ?? `new-${index}`}>
              <label className="repeated-row__field">
                <span className="sr-only">Acceptance criterion {index + 1}</span>
                <textarea
                  rows={2}
                  value={criterion.text}
                  onChange={(event) => updateArrayItem('criteria', index, event.target.value)}
                  disabled={busy || !criteriaEditable}
                  readOnly={!criteriaEditable}
                  required
                />
              </label>
              {criteriaEditable ? (
                <div className="repeated-row__action">
                  <button className="button button--ghost button--compact" type="button" onClick={() => removeCriterion(index)} disabled={busy}>
                    Remove
                  </button>
                </div>
              ) : null}
            </div>
          ))}
          {clientErrors.criteria ? <small className="field-error">{clientErrors.criteria}</small> : null}
          {serverFieldMessages.criteria.map((message) => (
            <small className="field-error" key={message}>
              {message}
            </small>
          ))}
        </div>
      </section>

      <div className="button-row form-actions">
        <button className="button button--primary" type="submit" disabled={busy}>
          {busy ? 'Saving…' : mode === 'create' ? 'Create change' : 'Save update'}
        </button>
        {onCancel ? (
          <button className="button button--ghost" type="button" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
        ) : null}
      </div>
    </form>
  );
}

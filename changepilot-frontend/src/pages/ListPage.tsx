import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiClientError, apiClient } from '../api/client';
import { STATUS_LABELS } from '../components/changePresentation';
import { PageShell } from '../components/PageShell';
import { RiskBadge } from '../components/RiskBadge';
import { StatusBadge } from '../components/StatusBadge';
import { CHANGE_STATUSES, type ChangeStatus, type EngineeringChangeSummary } from '../types/change';

function formatTimestamp(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

export function ListPage() {
  const [statusFilter, setStatusFilter] = useState<ChangeStatus | 'ALL'>('ALL');
  const [changes, setChanges] = useState<EngineeringChangeSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    apiClient
      .listChanges(statusFilter === 'ALL' ? undefined : statusFilter)
      .then((response) => {
        if (active) {
          setChanges(response);
        }
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }
        const message = caught instanceof ApiClientError ? caught.message : 'Unable to load engineering changes.';
        setError(message);
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [statusFilter]);

  return (
    <PageShell
      eyebrow="Control tower"
      title="Lifecycle rail for engineering changes"
      subtitle="Track each change request from draft through verification with concise progress, risk, and status signals."
      heroClassName="hero--list"
      actions={
        <Link className="button button--primary" to="/changes/new">
          Open new change
        </Link>
      }
    >
      <section className="card toolbar toolbar--filters" aria-label="List controls">
        <div className="toolbar__intro">
          <h2>Filter changes</h2>
          <p>Use filters below to narrow the engineering change list.</p>
        </div>
        <div className="toolbar__fields">
          <label className="toolbar__field">
            <span>Status</span>
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as ChangeStatus | 'ALL')}>
              <option value="ALL">All statuses</option>
              {CHANGE_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {STATUS_LABELS[status]}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      {loading ? <section className="card state">Loading engineering changes…</section> : null}
      {!loading && error ? (
        <section className="card state state--error" role="alert" aria-live="assertive">
          {error}
        </section>
      ) : null}
      {!loading && !error && changes.length === 0 ? <section className="card state">No changes found for this filter.</section> : null}

      {!loading && !error && changes.length > 0 ? (
        <section className="change-grid change-grid--list">
          {changes.map((change) => (
            <article className="card change-card" key={change.id}>
              <div className="change-card__rail" aria-hidden="true" />
              <div className="change-card__body">
                <div className="change-card__meta">
                  <StatusBadge status={change.status} />
                  <RiskBadge risk={change.risk} />
                </div>
                <h2>
                  <Link to={`/changes/${change.id}`}>{change.title}</Link>
                </h2>
                <div className="progress-row" aria-label={`Completed ${change.completedCriteriaCount} of ${change.totalCriteriaCount} criteria`}>
                  <strong>
                    {change.completedCriteriaCount}/{change.totalCriteriaCount}
                  </strong>
                  <span>criteria complete</span>
                </div>
                <footer className="change-card__footer">
                  <span>Updated {formatTimestamp(change.updatedAt)}</span>
                  <Link className="text-link" to={`/changes/${change.id}`}>
                    Review change
                  </Link>
                </footer>
              </div>
            </article>
          ))}
        </section>
      ) : null}
    </PageShell>
  );
}

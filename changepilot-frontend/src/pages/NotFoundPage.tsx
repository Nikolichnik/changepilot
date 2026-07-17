import { Link } from 'react-router-dom';
import { PageShell } from '../components/PageShell';

export function NotFoundPage() {
  return (
    <PageShell title="Route not found" subtitle="This control-track waypoint does not exist.">
      <section className="card state">
        <p>Check the URL or head back to the engineering change list.</p>
        <Link className="button button--primary" to="/">
          Return home
        </Link>
      </section>
    </PageShell>
  );
}

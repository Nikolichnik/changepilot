import { Link } from 'react-router-dom';
import type { PropsWithChildren, ReactNode } from 'react';

interface PageShellProps extends PropsWithChildren {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  heroClassName?: string;
}

export function PageShell({ eyebrow, title, subtitle, actions, children, heroClassName }: PageShellProps) {
  const heroClasses = ['hero', 'card', heroClassName].filter(Boolean).join(' ');

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/">
          <span className="brand__mark" aria-hidden="true">
            CP
          </span>
          <span>
            <strong>ChangePilot</strong>
            <small>Engineering change control panel</small>
          </span>
        </Link>
      </header>
      <main className="page">
        <section className={heroClasses}>
          <div className="hero__content">
            {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
            <h1>{title}</h1>
            {subtitle ? <p className="hero__subtitle">{subtitle}</p> : null}
          </div>
          {actions ? <div className="hero__actions">{actions}</div> : null}
        </section>
        {children}
      </main>
    </div>
  );
}

import type { ReactNode } from 'react';
import { FocusedHeading } from '../auth/AuthComponents';

/** 'neutre' is the plain banner of the reference: an outlined note with no accent, for a statement that is not a warning. */
export type NoticeKind = 'attention' | 'critique' | 'info' | 'neutre';

const icons: Record<NoticeKind, string> = { attention: 'alerte', critique: 'alerte', info: 'info', neutre: 'info' };

export function Notice({ kind, title, children, actions, alert = false, icon }: {
  kind: NoticeKind; title: string; children?: ReactNode; actions?: ReactNode; alert?: boolean; icon?: string;
}) {
  return (
    <div className={kind === 'neutre' ? 'etaye-bandeau' : `etaye-bandeau etaye-bandeau--${kind}`} role={alert ? 'alert' : 'status'}>
      <span aria-hidden="true" className={`etaye-icone etaye-icone--18 etaye-icone--${icon ?? icons[kind]}`} />
      <div>
        <div className="etaye-bandeau__titre">{title}</div>
        {children && <div>{children}</div>}
      </div>
      {actions && <div className="etaye-bandeau__actions">{actions}</div>}
    </div>
  );
}

export function EntryHeader({ title, meta, crumb, actions, focus = true, onCrumb }: {
  title: string; meta: ReactNode; crumb?: ReactNode; actions?: ReactNode; focus?: boolean; onCrumb?: () => void;
}) {
  return (
    <div className="etaye-entete">
      <div>
        {crumb && (
          <div className="etaye-entete__fil">
            <a href="#patients" onClick={(event) => { event.preventDefault(); onCrumb?.(); }}>Patients</a> › {crumb}
          </div>
        )}
        {focus ? <FocusedHeading>{title}</FocusedHeading> : <h1>{title}</h1>}
        <div className="etaye-entete__meta">{meta}</div>
      </div>
      {actions && <div className="etaye-entete__actions">{actions}</div>}
    </div>
  );
}

/** Last name in capitals then first name, the way the approved references list a person. */
export function patientName(person: { lastName: string; firstName: string }): string {
  return `${person.lastName.toLocaleUpperCase('fr-FR')} ${person.firstName}`;
}

export function clockTime(at: number): string {
  return new Date(at).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
}

export function Skeleton({ label }: { label: string }) {
  return (
    <div aria-busy="true">
      <p className="etaye-3" role="status" style={{ fontSize: 13, margin: '0 0 var(--espace-2)' }}>{label}</p>
      <div className="etaye-squelette" aria-hidden="true"><span /><span /><span /><span /><span /></div>
    </div>
  );
}

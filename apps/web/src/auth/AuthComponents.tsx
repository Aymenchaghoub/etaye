import { type ComponentPropsWithoutRef, type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import logoLight from '../../../../design/offline-reference/site/logos/etaye-logo.svg?url';
import logoDark from '../../../../design/offline-reference/site/logos/etaye-logo-sombre.svg?url';
import patientLight from '../../../../design/offline-reference/site/logos/etaye-logo-espace-patient.svg?url';
import patientDark from '../../../../design/offline-reference/site/logos/etaye-logo-espace-patient-sombre.svg?url';

export function AuthShell({ children, patient = false }: { children: ReactNode; patient?: boolean }) {
  const name = patient ? 'Étaye — Espace patient' : 'Étaye';
  return (
    <div className={`etaye-root etaye-auth${patient ? ' etaye-patient patient-corps' : ''}`}>
      <header>
        <img src={patient ? patientLight : logoLight} className="etaye-logo-c" alt={name} />
        <img src={patient ? patientDark : logoDark} className="etaye-logo-s" alt={name} />
      </header>
      <main className="etaye-auth__corps" id="contenu">{children}</main>
      <footer className="etaye-auth__pied">
        <span className="etaye-demo">Démonstration — personnes et données fictives</span>
        <span className="etaye-3 etaye-auth__usage">Projet de démonstration — aucun usage clinique</span>
      </footer>
    </div>
  );
}

export function FocusedHeading({ children }: { children: ReactNode }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
  }, []);
  return <h1 ref={heading} tabIndex={-1}>{children}</h1>;
}

export function AuthHeading({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div>
      <h1>{title}</h1>
      {children && <p className="etaye-2 etaye-auth__introduction">{children}</p>}
    </div>
  );
}

export function Banner({ title, children, information = false }: {
  title: string; children?: ReactNode; information?: boolean;
}) {
  const appearance = information ? 'info' : 'attention';
  return (
    <div className={`etaye-bandeau etaye-bandeau--${appearance}`} role={information ? 'status' : 'alert'}>
      <span className={`etaye-auth-icone etaye-auth-icone--${appearance}`} aria-hidden="true" />
      <div>
        <div className="etaye-bandeau__titre">{title}</div>
        {children}
      </div>
    </div>
  );
}

export type FieldError = { id: string; summary: string; detail: string };

export function ErrorSummary({ errors, amber = false }: { errors: FieldError[]; amber?: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.focus();
  }, [errors]);
  return (
    <div ref={ref} tabIndex={-1} role="alert" className={`etaye-resume-erreurs${amber ? ' etaye-resume-erreurs--attention' : ''}`}>
      <h2>{errors.length === 1 ? '1 champ à corriger' : `${errors.length} champs à corriger`}</h2>
      <ul>{errors.map((item) => (
        <li key={item.id}>
          <a href={`#${item.id}`} onClick={(event) => {
            event.preventDefault();
            document.getElementById(item.id)?.focus();
          }}>{item.summary}</a>
        </li>
      ))}</ul>
    </div>
  );
}

type FieldProps = { label: string; id: string; error?: FieldError; hint?: string }
  & Omit<ComponentPropsWithoutRef<'input'>, 'id'>;

export function Field({ id, label, error, hint, ...input }: FieldProps) {
  return (
    <div className={`etaye-champ${error ? ' etaye-champ--erreur' : ''}`}>
      <label className="etaye-champ__libelle" htmlFor={id}>
        {label} <span aria-hidden="true">*</span>
      </label>
      <input {...input} id={id} required className="etaye-champ__saisie"
        aria-invalid={error ? true : undefined} aria-describedby={error || hint ? `${id}-hint` : undefined} />
      {(error || hint) && <span className="etaye-champ__aide" id={`${id}-hint`}>{error?.detail ?? hint}</span>}
    </div>
  );
}

export function emailError(input: HTMLInputElement): FieldError | undefined {
  if (input.validity.valueMissing) return {
    id: input.id, summary: 'Adresse e-mail : obligatoire', detail: 'Erreur : indiquez votre adresse e-mail.',
  };
  if (!input.validity.valid) return {
    id: input.id, summary: 'Adresse e-mail : format incorrect', detail: 'Erreur : vérifiez le format de votre adresse e-mail.',
  };
  return undefined;
}

export function useRetryDelay() {
  const [wait, setWait] = useState(0);
  useEffect(() => {
    if (!wait) return;
    const timer = window.setTimeout(() => setWait(0), wait * 1000);
    return () => window.clearTimeout(timer);
  }, [wait]);
  const block = useCallback((seconds: number) => setWait(seconds || 60), []);
  return { waiting: wait > 0, block };
}

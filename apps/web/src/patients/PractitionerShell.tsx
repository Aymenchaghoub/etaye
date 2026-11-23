import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import '../../../../design/received/2026-11-03/icons.css';
import type { AccessContext, Session } from '../api/generated/types.gen';
import { contextPath, practiceLabel, roleLabel } from '../auth/access-contexts';
import { AuthLogo } from '../auth/AuthComponents';
import { ACCOUNT_PATH } from '../auth/session-work';
import { setLeaveGuard } from './leave-guard';
import { patientsPath } from './routes';
import '../auth/practice-context.css';
import './patients.css';

export type DirtyWork = { label: string; crumb: string; onDiscard: () => void };

type ShellApi = {
  /** Navigation that goes through the unsaved-changes guard (I-20) when a form holds unsaved input. */
  navigate: (path: string) => void;
  setDirty: (work: DirtyWork | null) => void;
  practiceName: string;
};

const ShellContext = createContext<ShellApi | undefined>(undefined);

export function useShell(): ShellApi {
  const api = useContext(ShellContext);
  if (!api) throw new Error('useShell must be used inside PractitionerShell');
  return api;
}

export function PractitionerShell({ session, context, contexts, select, signOut, children }: {
  session: Session;
  context: AccessContext;
  contexts: AccessContext[];
  select: (path: string) => void;
  signOut: () => void;
  children: ReactNode;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [pending, setPending] = useState<(() => void) | null>(null);
  const dirty = useRef<DirtyWork | null>(null);
  const menuButton = useRef<HTMLButtonElement>(null);
  const nav = useRef<HTMLElement>(null);
  const stay = useRef<HTMLButtonElement>(null);
  const base = contextPath(context);
  const practice = practiceLabel(context);
  const role = roleLabel(context, contexts);

  const request = useCallback((action: () => void) => {
    if (dirty.current) setPending(() => action);
    else action();
  }, []);
  const navigate = useCallback((path: string) => request(() => select(path)), [request, select]);
  const setDirty = useCallback((work: DirtyWork | null) => { dirty.current = work; }, []);
  const api = useMemo<ShellApi>(() => ({ navigate, setDirty, practiceName: practice }), [navigate, setDirty, practice]);

  const closeMenu = useCallback((returnFocus: boolean) => {
    setMenuOpen(false);
    if (returnFocus) menuButton.current?.focus();
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    nav.current?.querySelector('a')?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeMenu(true);
    };
    const wide = window.matchMedia('(min-width: 761px)');
    const onWide = (event: MediaQueryListEvent) => { if (event.matches) closeMenu(false); };
    document.addEventListener('keydown', onKey);
    wide.addEventListener('change', onWide);
    return () => {
      document.removeEventListener('keydown', onKey);
      wide.removeEventListener('change', onWide);
    };
  }, [closeMenu, menuOpen]);

  useEffect(() => {
    // Leaving the page entirely (reload, close) also warns while a form holds unsaved input.
    const warn = (event: BeforeUnloadEvent) => {
      if (dirty.current) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, []);

  useEffect(() => {
    if (pending) stay.current?.focus();
  }, [pending]);

  useEffect(() => {
    // Back and Forward with unsaved input ask first, like every other way of leaving the form (BR-015).
    setLeaveGuard((target) => {
      if (!dirty.current) return false;
      setPending(() => () => select(target));
      return true;
    });
    return () => setLeaveGuard(undefined);
  }, [select]);

  const go = (path: string) => (event: { preventDefault: () => void }) => {
    event.preventDefault();
    setMenuOpen(false);
    navigate(path);
  };
  const label = <>
    <span className="etaye-icone etaye-icone--18 etaye-icone--cabinet" aria-hidden="true" />
    <span>
      <strong className="etaye-contexte__cabinet">{practice}</strong>{' '}
      <span className="etaye-contexte__role">{`${role} · ${session.account.displayName}`}</span>
    </span>
  </>;
  const patients = patientsPath(base);

  return (
    <ShellContext.Provider value={api}>
      <div className="etaye-root etaye-app" data-testid="practitioner-shell">
        <header className="etaye-top">
          <button ref={menuButton} type="button" className="etaye-fermer etaye-top__menu" data-menu="true"
            aria-expanded={menuOpen} aria-controls="navigation-principale"
            aria-label={menuOpen ? 'Fermer le menu' : 'Ouvrir le menu'} onClick={() => setMenuOpen((open) => !open)}>
            <span aria-hidden="true" className="etaye-icone etaye-icone--18 etaye-icone--menu" />
          </button>
          <a className="etaye-top__logo" href={patients} onClick={go(patients)}><AuthLogo /></a>
          {contexts.length > 1 ? (
            <button className="etaye-contexte" type="button" inert={menuOpen} onClick={() => navigate(ACCOUNT_PATH)}
              aria-label={`${practice} ${role} · ${session.account.displayName} — Changer de contexte`}>
              {label}
              <span className="etaye-icone etaye-icone--deplier" aria-hidden="true" />
            </button>
          ) : <div className="etaye-contexte" inert={menuOpen}>{label}</div>}
          <div className="etaye-top__droite">
            <span className="etaye-demo">Démonstration — personnes et données fictives</span>
            <span className="etaye-compte__nom">{session.account.displayName}</span>
            <button className="etaye-bouton etaye-bouton--discret" type="button"
              onClick={() => request(signOut)}>Se déconnecter</button>
          </div>
        </header>
        <nav ref={nav} id="navigation-principale" aria-label="Navigation principale" className="etaye-nav"
          data-open={menuOpen ? 'true' : undefined}>
          <ul className="etaye-nav__liste">
            <li>
              <a aria-current="page" className="etaye-nav__rangee" href={patients} onClick={go(patients)}>
                <span aria-hidden="true" className="etaye-icone etaye-icone--18 etaye-icone--patients" />Patients
              </a>
            </li>
          </ul>
        </nav>
        {/* While the menu covers the page, nothing behind it can take focus (focus not obscured). */}
        <main className="etaye-main" id="contenu" inert={menuOpen}>
          <div className="etaye-page">
            {pending && dirty.current && (
              <div onKeyDown={(event) => { if (event.key === 'Escape') setPending(null); }}>
                <div className="etaye-entete">
                  <div>
                    <div className="etaye-entete__fil">
                      <a href={patients} onClick={(event) => { event.preventDefault(); setPending(null); }}>Patients</a> › {dirty.current.crumb}
                    </div>
                    <h1>Modifications non enregistrées</h1>
                    <div className="etaye-entete__meta">{dirty.current.label} · {practice}</div>
                  </div>
                </div>
                <div className="etaye-panneau" style={{ maxWidth: 720, marginTop: 'var(--espace-6)' }}>
                  <div className="etaye-panneau__corps">
                    <p>Vous avez saisi des informations qui ne sont pas enregistrées. Si vous quittez ce formulaire, elles seront perdues.</p>
                    <div className="etaye-ligne">
                      <button ref={stay} type="button" className="etaye-bouton etaye-bouton--principal"
                        onClick={() => setPending(null)}>Rester sur le formulaire</button>
                      <button type="button" className="etaye-bouton etaye-bouton--secondaire" onClick={() => {
                        const action = pending;
                        const work = dirty.current;
                        dirty.current = null;
                        work?.onDiscard();
                        setPending(null);
                        action();
                      }}>Abandonner les modifications</button>
                    </div>
                  </div>
                </div>
              </div>
            )}
            {/* The form stays mounted while the guard is shown, so staying never loses the entered values. */}
            <div hidden={pending !== null} style={{ display: pending ? 'none' : 'contents' }}>{children}</div>
          </div>
        </main>
      </div>
    </ShellContext.Provider>
  );
}

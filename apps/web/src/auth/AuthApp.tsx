import { useCallback, useEffect, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { Session } from '../api/generated/types.gen';
import { AuthHeading, AuthShell, Banner, FocusedHeading } from './AuthComponents';
import { Invitation } from './Invitation';
import { RecoveryRequest, ResetPassword } from './Recovery';
import { SignIn } from './SignIn';
import { ACCOUNT_PATH, allowsAccountPath, checkPausedWork, isAccountPath, pauseWork } from './session-work';
import './auth.css';

type Notice = 'expired' | 'grant-changed' | 'account-changed';
type Resume = ReturnType<typeof pauseWork<undefined>>;

export function usesAuth(path: string): boolean {
  return isAccountPath(path) || [
    '/connexion', '/connexion/patient', '/recuperation', '/recuperation/nouveau', '/invitation',
  ].includes(path);
}

export function AuthApp() {
  const [path, setPath] = useState(window.location.pathname);
  const [notice, setNotice] = useState<Notice>();
  const [returning, setReturning] = useState(false);
  const [initial, setInitial] = useState<Session>();
  const [retry, setRetry] = useState(0);
  const paused = useRef<Resume>(undefined);
  const requestedPath = new URLSearchParams(window.location.search).get('retour');
  const patient = path === '/connexion/patient' || path.startsWith('/compte/patient/');

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const selected = new URLSearchParams(window.location.search).get('theme');
    const updateTheme = () => {
      document.documentElement.dataset.theme = selected === 'sombre' || (selected !== 'clair' && media.matches)
        ? 'sombre' : 'clair';
    };
    updateTheme();
    media.addEventListener('change', updateTheme);
    return () => media.removeEventListener('change', updateTheme);
  }, []);

  useEffect(() => {
    const onPopState = () => {
      paused.current = undefined;
      setNotice(undefined);
      setReturning(false);
      setInitial(undefined);
      setPath(window.location.pathname);
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  function navigate(target: string) {
    window.history.replaceState(null, '', target);
    setPath(target);
  }

  const halt = useCallback((reason: Notice, target: string, previous?: Session) => {
    paused.current = reason === 'expired' && previous ? pauseWork(previous, target, undefined) : undefined;
    setInitial(undefined);
    setNotice(reason);
  }, []);

  function leave() {
    paused.current = undefined;
    setNotice(undefined);
    setReturning(false);
    setInitial(undefined);
    navigate('/connexion');
  }

  function signedIn(session: Session) {
    const safe = session.returnTo && allowsAccountPath(session, session.returnTo) ? session.returnTo : ACCOUNT_PATH;
    const work = paused.current;
    paused.current = undefined;
    setReturning(false);
    setInitial(session);

    // The previous route is restored only after both the identity and current grant match.
    if (work) {
      const result = checkPausedWork(work, session, safe);
      if (result.status !== 'restored') {
        setNotice(result.status);
        navigate(ACCOUNT_PATH);
        return;
      }
    }
    setNotice(undefined);
    navigate(safe);
  }

  if (notice) return (
    <SessionNotice reason={notice} standalone={path === ACCOUNT_PATH} reconnect={() => {
      setNotice(undefined);
      setReturning(true);
    }} leave={leave} continueToAccount={() => {
      setNotice(undefined);
      setRetry((attempt) => attempt + 1);
      navigate(ACCOUNT_PATH);
    }} />
  );

  if (returning || path === '/connexion' || path === '/connexion/patient') {
    return <SignIn key={path} patient={patient} returning={returning}
      returnTo={isAccountPath(path) ? path : requestedPath && isAccountPath(requestedPath) ? requestedPath : undefined}
      onSignedIn={signedIn} />;
  }
  if (path === '/invitation') return <Invitation onAccepted={(session) => {
    const portal = session.activatedPortalLinkId;
    const target = portal ? `/compte/patient/${portal}` : ACCOUNT_PATH;
    setInitial(session);
    navigate(allowsAccountPath(session, target) ? target : ACCOUNT_PATH);
  }} />;
  if (path === '/recuperation/nouveau') return <ResetPassword />;
  if (path === '/recuperation') {
    return <RecoveryRequest patient={new URLSearchParams(window.location.search).get('espace') === 'patient'} />;
  }
  if (!isAccountPath(path)) return null;

  return <AccountGate key={`${path}-${retry}`} target={path} initial={initial} onHalt={halt} leave={leave}
    askToSignIn={() => setReturning(true)} />;
}

function SessionNotice({ reason, standalone, reconnect, leave, continueToAccount }: {
  reason: Notice; standalone: boolean; reconnect: () => void; leave: () => void; continueToAccount: () => void;
}) {
  const descriptions = {
    expired: {
      title: 'Votre session a expiré',
      detail: 'Reconnectez-vous pour revenir à la page demandée. Aucune opération ne sera rejouée automatiquement.',
    },
    'grant-changed': { title: 'Accès modifié', detail: 'Votre accès à cet espace a changé.' },
    'account-changed': {
      title: 'Autre compte connecté',
      detail: "Un autre compte s'est connecté sur cet appareil. Les informations de la session précédente ont été effacées.",
    },
  };
  const notice = descriptions[reason];
  const actions = reason === 'expired' ? <>
    <button type="button" className="etaye-bouton etaye-bouton--principal" onClick={reconnect}>Se reconnecter</button>
    <button type="button" className="etaye-bouton etaye-bouton--discret" onClick={leave}>Quitter la session</button>
  </> : <button type="button" className="etaye-bouton etaye-bouton--principal" onClick={continueToAccount}>
    Continuer
  </button>;

  if (standalone && reason === 'expired') return (
    <AuthShell>
      <div>
        <FocusedHeading>{notice.title}</FocusedHeading>
        <p className="etaye-2 etaye-auth__introduction">
          Pour protéger les dossiers, la session se ferme après une période d'inactivité.
        </p>
      </div>
      <div className="etaye-auth__carte">
        <p>{notice.detail}</p>
        <div className="etaye-auth__actions">{actions}</div>
      </div>
    </AuthShell>
  );

  return (
    <main className="etaye-root etaye-session-scene">
      <section className="etaye-session-panel">
        <div className="etaye-session-panel__heading">
          <FocusedHeading>{notice.title}</FocusedHeading>
        </div>
        <p className="etaye-session-panel__body">{notice.detail}</p>
        <div className="etaye-session-panel__actions">{actions}</div>
      </section>
    </main>
  );
}

function AccountGate({ target, initial, onHalt, leave, askToSignIn }: {
  target: string;
  initial?: Session;
  onHalt: (reason: Notice, target: string, previous?: Session) => void;
  leave: () => void;
  askToSignIn: () => void;
}) {
  const [session, setSession] = useState<Session>();
  const [unavailable, setUnavailable] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [retry, setRetry] = useState(0);
  // Keep authorization checks independent of a re-render of the login callback.
  const signIn = useRef(askToSignIn);
  signIn.current = askToSignIn;
  const verifiedAccount = useRef<Session | undefined>(initial);

  useEffect(() => {
    if (leaving) return;
    let current = verifiedAccount.current ?? initial;
    let pending: AbortController | undefined;
    let deadline: number | undefined;

    function halt(reason: Notice) {
      pending?.abort();
      window.clearTimeout(deadline);
      verifiedAccount.current = undefined;
      onHalt(reason, target, current);
    }

    async function check(hideWhileChecking = false) {
      if (hideWhileChecking) setSession(undefined);
      pending?.abort();
      const request = new AbortController();
      pending = request;
      const result = await accountApi.readSession(request.signal);
      if (request.signal.aborted) return;
      if (!result.ok) {
        setSession(undefined);
        if (result.status === 401) {
          if (current) halt('expired');
          else signIn.current();
        } else if (result.status === 403) {
          halt('grant-changed');
        } else {
          setUnavailable(true);
        }
        return;
      }
      const latest = result.data;
      if (current && current.account.id !== latest.account.id) {
        halt('account-changed');
        return;
      }
      if (!allowsAccountPath(latest, target)) {
        halt('grant-changed');
        return;
      }
      const remaining = Date.parse(latest.expiresAt) - Date.now();
      if (!Number.isFinite(remaining) || remaining <= 0) {
        halt('expired');
        return;
      }
      current = latest;
      verifiedAccount.current = latest;
      setUnavailable(false);
      setSession(latest);
      window.clearTimeout(deadline);
      deadline = window.setTimeout(() => halt('expired'), remaining);
    }
    void check(true);
    const poll = window.setInterval(() => {
      if (document.visibilityState === 'visible') void check();
    }, 20_000);
    const checkOnReturn = () => {
      if (document.visibilityState === 'visible') void check(true);
    };
    document.addEventListener('visibilitychange', checkOnReturn);
    window.addEventListener('pageshow', checkOnReturn);
    return () => {
      pending?.abort();
      window.clearTimeout(deadline);
      window.clearInterval(poll);
      document.removeEventListener('visibilitychange', checkOnReturn);
      window.removeEventListener('pageshow', checkOnReturn);
    };
  }, [initial, leaving, onHalt, retry, target]);

  async function signOut() {
    verifiedAccount.current = undefined;
    setSession(undefined);
    setUnavailable(false);
    setLeaving(true);
    const result = await accountApi.signOut();
    if (result.ok || result.status === 401) leave();
    else setUnavailable(true);
  }

  if (!session) return (
    <AuthShell>
      <AuthHeading title={leaving ? 'Se déconnecter' : 'Votre session'} />
      {unavailable ? <>
        <Banner title="Le service est temporairement indisponible" />
        <button type="button" className="etaye-bouton etaye-bouton--secondaire"
          onClick={() => leaving ? void signOut() : setRetry((attempt) => attempt + 1)}>Réessayer</button>
      </> : <p role="status">{leaving ? 'Déconnexion en cours…' : 'Vérification de votre session…'}</p>}
    </AuthShell>
  );

  return (
    <AuthShell patient={target.startsWith('/compte/patient/')}>
      <AuthHeading title="Vous êtes connecté" />
      <div className="etaye-auth__carte" data-testid="protected-account">
        <p>{session.account.displayName}</p>
        <p className="etaye-2">{session.account.email}</p>
        {!session.membershipIds.length && !session.portalLinkIds.length && <p>Aucun espace autorisé.</p>}
        <button type="button" onClick={signOut} className="etaye-bouton etaye-bouton--secondaire">Se déconnecter</button>
      </div>
    </AuthShell>
  );
}

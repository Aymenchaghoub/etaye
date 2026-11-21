import { useCallback, useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { accountApi } from '../api/browser';
import type { AccessContext, Session } from '../api/generated/types.gen';
import { AuthShell, Banner, FocusedHeading } from './AuthComponents';
import { contextPath, currentContexts } from './access-contexts';
import { PracticeChooser, PracticeChooserHeading, PracticeDestination } from './PracticeContext';
import { Invitation } from './Invitation';
import { RecoveryRequest, ResetPassword } from './Recovery';
import { SignIn } from './SignIn';
import { ACCOUNT_PATH, allowsAccountPath, checkPausedWork, isAccountPath, pauseWork } from './session-work';
import { applyDemoTheme, clearDemoEntry, demoEntry, type FixtureMarker, fixtureChanged,
  fixtureMarker, rememberDemo, SHOWCASE } from '../demo/demo-entry';
import './auth.css';

type Notice = 'expired' | 'grant-changed' | 'account-changed' | 'demo-reset';
type Resume = ReturnType<typeof pauseWork<undefined>>;

function isAccountArea(path: string) {
  return path === ACCOUNT_PATH || path.startsWith(`${ACCOUNT_PATH}/`);
}

function readPath() {
  const { pathname } = window.location;
  return pathname === `${ACCOUNT_PATH}/` ? ACCOUNT_PATH : pathname;
}

export function usesAuth(path: string): boolean {
  return isAccountArea(path) || [
    '/connexion', '/connexion/patient', '/recuperation', '/recuperation/nouveau', '/invitation',
  ].includes(path);
}

export function AuthApp() {
  const [path, setPath] = useState(readPath);
  const [notice, setNotice] = useState<Notice>();
  const [resetVersion, setResetVersion] = useState<string>();
  const [returning, setReturning] = useState(false);
  const [initial, setInitial] = useState<Session>();
  const [retry, setRetry] = useState(0);
  const paused = useRef<Resume>(undefined);
  const requestedPath = new URLSearchParams(window.location.search).get('retour');
  const patient = path === '/connexion/patient' || path.startsWith('/compte/patient/');

  useEffect(applyDemoTheme, []);

  useEffect(() => {
    const onPopState = () => {
      paused.current = undefined;
      setNotice(undefined);
      setReturning(false);
      setInitial(undefined);
      setRetry((attempt) => attempt + 1);
      setPath(readPath());
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  function navigate(target: string) {
    window.history.replaceState(null, '', target);
    setPath(target);
  }

  const navigateContext = useCallback((target: string, session: Session, replace = false) => {
    if (target === window.location.pathname) return;
    setInitial(session);
    if (replace) window.history.replaceState(null, '', target);
    else window.history.pushState(null, '', target);
    setPath(target);
  }, []);

  const halt = useCallback((reason: Notice, target: string, previous?: Session) => {
    paused.current = reason === 'expired' && previous ? pauseWork(previous, target, undefined) : undefined;
    setInitial(undefined);
    setNotice(reason);
  }, []);

  const onDemoReset = useCallback((marker: FixtureMarker) => {
    clearDemoEntry();
    paused.current = undefined;
    setInitial(undefined);
    setResetVersion(marker.version);
    setNotice('demo-reset');
  }, []);

  function leave() {
    paused.current = undefined;
    setNotice(undefined);
    setReturning(false);
    setInitial(undefined);
    if (demoEntry()) {
      clearDemoEntry();
      window.location.assign(SHOWCASE);
      return;
    }
    navigate('/connexion');
  }

  function signedIn(session: Session) {
    const demo = demoEntry();
    if (demo?.email && demo.email !== session.account.email) rememberDemo(demo);
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

  if (notice === 'demo-reset') return (
    <AuthShell>
      <FocusedHeading>Votre session a expiré</FocusedHeading>
      <Banner title="Démonstration réinitialisée" information>
        <p>Le jeu {resetVersion} a été rechargé. Les sessions ouvertes ont été fermées.</p>
      </Banner>
      <div className="etaye-auth__actions">
        <a className="etaye-bouton etaye-bouton--principal" href={SHOWCASE}>Revenir à la démonstration</a>
      </div>
    </AuthShell>
  );

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
    return <SignIn key={path} patient={patient} returning={returning} demo={demoEntry()}
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
  if (!isAccountArea(path)) return null;

  return <AccountGate key={`${path}-${retry}`} target={path} initial={initial} onHalt={halt} onDemoReset={onDemoReset} leave={leave}
    enter={navigateContext} askToSignIn={() => setReturning(true)} />;
}

function SessionNotice({ reason, standalone, reconnect, leave, continueToAccount }: {
  reason: Exclude<Notice, 'demo-reset'>; standalone: boolean; reconnect: () => void; leave: () => void; continueToAccount: () => void;
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

function AccountGate({ target, initial, onHalt, onDemoReset, leave, enter, askToSignIn }: {
  target: string;
  initial?: Session;
  onHalt: (reason: Notice, target: string, previous?: Session) => void;
  onDemoReset: (marker: FixtureMarker) => void;
  leave: () => void;
  enter: (path: string, session: Session, replace?: boolean) => void;
  askToSignIn: () => void;
}) {
  const [view, setView] = useState<{ session: Session; contexts: AccessContext[] }>();
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
      if (hideWhileChecking) {
        setView(undefined);
        setUnavailable(false);
      }
      pending?.abort();
      const request = new AbortController();
      pending = request;
      const result = await accountApi.readSession(request.signal);
      if (request.signal.aborted) return;
      const entry = demoEntry();
      if (entry || import.meta.env.VITE_ETAYE_LOCAL_DEMO === 'true') {
        const meta = await accountApi.readMeta(request.signal);
        if (request.signal.aborted) return;
        const marker = meta.ok ? fixtureMarker(meta.data) : undefined;
        if (!marker) {
          setView(undefined);
          setUnavailable(true);
          return;
        }
        if (!entry && result.ok) rememberDemo(marker);
        if (entry && fixtureChanged(entry, marker)) {
          verifiedAccount.current = undefined;
          window.clearTimeout(deadline);
          setView(undefined);
          onDemoReset(marker);
          return;
        }
      }
      if (!result.ok) {
        setView(undefined);
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
      const contexts = latest?.account && currentContexts(latest);
      if (!contexts) {
        setView(undefined);
        setUnavailable(true);
        return;
      }
      if (current && current.account.id !== latest.account.id) {
        halt('account-changed');
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
      if (target === ACCOUNT_PATH && contexts.length === 1 && latest.contexts?.length === 1) {
        // A direct entry still gates the exact grant through a fresh status read.
        enter(contextPath(contexts[0]), latest, true);
        return;
      }
      setView({ session: latest, contexts });
      window.clearTimeout(deadline);
      deadline = window.setTimeout(() => halt('expired'), remaining);
    }
    void check(true);
    const poll = window.setInterval(() => {
      if (document.visibilityState === 'visible') void check();
    }, 20_000);
    const clearBeforeHide = () => {
      pending?.abort();
      // Do not let the browser restore a protected view from its history snapshot.
      flushSync(() => setView(undefined));
    };
    const checkOnReturn = () => {
      if (document.visibilityState === 'visible') void check(true);
    };
    const visibilityChanged = () => {
      if (document.visibilityState === 'hidden') clearBeforeHide();
      else checkOnReturn();
    };
    document.addEventListener('visibilitychange', visibilityChanged);
    window.addEventListener('pageshow', checkOnReturn);
    window.addEventListener('pagehide', clearBeforeHide);
    return () => {
      pending?.abort();
      window.clearTimeout(deadline);
      window.clearInterval(poll);
      document.removeEventListener('visibilitychange', visibilityChanged);
      window.removeEventListener('pageshow', checkOnReturn);
      window.removeEventListener('pagehide', clearBeforeHide);
    };
  }, [enter, initial, leaving, onDemoReset, onHalt, retry, target]);

  async function signOut() {
    verifiedAccount.current = undefined;
    setView(undefined);
    setUnavailable(false);
    setLeaving(true);
    const result = await accountApi.signOut();
    if (result.ok || result.status === 401) leave();
    else setUnavailable(true);
  }

  const retryRead = () => setRetry((attempt) => attempt + 1);
  if (!view) return (
    <AuthShell wide>
      {leaving ? <FocusedHeading>Se déconnecter</FocusedHeading> : <PracticeChooserHeading />}
      {unavailable ? <>
        <Banner title="Impossible de charger vos accès">
          <p>Le service est temporairement indisponible.</p>
        </Banner>
        <button type="button" className="etaye-bouton etaye-bouton--secondaire"
          onClick={() => leaving ? void signOut() : retryRead()}>Réessayer</button>
      </> : <p role="status">
        {leaving ? 'Déconnexion en cours…' : target === ACCOUNT_PATH ? 'Chargement de vos accès…' : 'Vérification de l’accès…'}
      </p>}
    </AuthShell>
  );

  const { session, contexts } = view;
  const select = (path: string) => enter(path, session);
  const selected = contexts.find((context) => contextPath(context) === target);
  if (selected) return (
    <PracticeDestination context={selected} contexts={contexts} session={session} select={select} signOut={signOut} />
  );
  return <PracticeChooser session={session} contexts={contexts} restricted={target !== ACCOUNT_PATH}
    select={select} retry={retryRead} signOut={signOut} />;
}

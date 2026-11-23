import { type ReactNode, useEffect, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { AccessContext, Session } from '../api/generated/types.gen';
import { AuthLogo, AuthShell, FocusedHeading } from '../auth/AuthComponents';
import { contextPath, practiceLabel } from '../auth/access-contexts';
import { ContextFrame } from '../auth/PracticeContext';
import { SHOWCASE } from '../demo/demo-entry';
import {
  type Audience, classify, frenchDate, ISOLATION_DATASET, journeyContext, matrixCase, OUTSIDE_PATH, type Outcome,
  PRACTICE_A, RECORD_A, RECORD_B, type Step,
} from './isolation-journey';
import './isolation.css';

type Shown = Outcome['kind'] | 'loading';

function BackToDemo({ discreet = false }: { discreet?: boolean }) {
  return <a className={`etaye-bouton etaye-bouton--${discreet ? 'discret' : 'secondaire'}`} href={SHOWCASE}>
    Revenir à la démonstration
  </a>;
}

function Actions({ children }: { children: ReactNode }) {
  return <div className="etaye-isolation__actions">{children}</div>;
}

/** The S-36 loading state, also used while the session is checked, in the same shell as the result. */
export function IsolationLoading() {
  return (
    <div className="etaye-etat-page" data-isolation-state="loading">
      <FocusedHeading>Vérification de l’accès</FocusedHeading>
      <div className="etaye-bandeau etaye-bandeau--neutre" role="status">
        <span className="etaye-icone etaye-icone--18 etaye-icone--horloge" aria-hidden="true" />
        <p>Vérification de l’accès en cours…</p>
      </div>
      <Actions><BackToDemo /></Actions>
    </div>
  );
}

export function IsolationFailed({ retry, repeated }: { retry: () => void; repeated: boolean }) {
  return (
    <div className="etaye-etat-page" data-isolation-state="failed">
      <FocusedHeading>{repeated
        ? 'Impossible de vérifier votre accès à cette étape de la démonstration pour le moment'
        : 'Vérification momentanément indisponible'}</FocusedHeading>
      <div className="etaye-bandeau etaye-bandeau--attention" role="alert">
        <span className="etaye-icone etaye-icone--18 etaye-icone--alerte" aria-hidden="true" />
        <p>{repeated
          ? 'La vérification n’a pas abouti. Vous pouvez réessayer maintenant, ou revenir à la démonstration et sélectionner un scénario correspondant au compte avec lequel vous souhaitez vous reconnecter.'
          : 'La vérification de l’accès a échoué. Réessayez ou revenez à la démonstration.'}</p>
      </div>
      <Actions>
        <button type="button" className="etaye-bouton etaye-bouton--principal" onClick={retry}>
          {repeated ? 'Réessayer la vérification de l’accès' : 'Réessayer'}
        </button>
        <BackToDemo discreet />
      </Actions>
    </div>
  );
}

/** Shown while the session and its current grants are being verified: no practice or account is named yet. */
export function IsolationPending({ failed, retry }: { failed: boolean; retry: () => void }) {
  return (
    <div className="etaye-root etaye-context-destination">
      <header className="etaye-context-top">
        <AuthLogo />
        <span className="etaye-demo">Démonstration — personnes et données fictives</span>
      </header>
      <main className="etaye-auth__corps etaye-auth__corps--large" id="contenu">
        {failed ? <IsolationFailed retry={retry} repeated={false} /> : <IsolationLoading />}
      </main>
    </div>
  );
}

/** I-22 recheck after a reconnection, in the neutral shell of the sign-in it follows. */
export function IsolationRechecking() {
  return (
    <AuthShell>
      <FocusedHeading>Vérification après reconnexion</FocusedHeading>
      <p className="etaye-isolation__notice" role="status">Vérification de votre accès au parcours…</p>
      <Actions><BackToDemo /></Actions>
    </AuthShell>
  );
}

/** I-22 unavailable return: the account, the grant or the fixture is no longer the one the step needs. */
export function IsolationChanged() {
  return (
    <AuthShell>
      <FocusedHeading>Cette étape n’est plus disponible</FocusedHeading>
      <div className="etaye-auth__carte">
        <p>Revenez à la démonstration pour reprendre avec un compte et un espace autorisés.</p>
        <div className="etaye-auth__actions">
          <a className="etaye-bouton etaye-bouton--principal" href={SHOWCASE}>Revenir à la démonstration</a>
        </div>
      </div>
    </AuthShell>
  );
}

/** I-22 expiry with the read-specific wording: no notes, edits or publication are mentioned. */
export function IsolationExpired({ reconnect, leave }: { reconnect: () => void; leave: () => void }) {
  return (
    <AuthShell>
      <FocusedHeading>Votre session a expiré</FocusedHeading>
      <div className="etaye-auth__carte">
        <p>Reconnectez-vous pour vérifier votre accès à cette étape.</p>
        <div className="etaye-auth__actions">
          <button type="button" className="etaye-bouton etaye-bouton--principal" onClick={reconnect}>Se reconnecter</button>
          <button type="button" className="etaye-bouton etaye-bouton--discret" onClick={leave}>Quitter la session</button>
        </div>
      </div>
    </AuthShell>
  );
}

function roleClause(context: AccessContext) {
  return context.displayRole === 'ADMINISTRATION' ? 'Votre rôle d’administration' : 'Votre rôle';
}

/**
 * The signed-in journey step: it performs the real record read for the current account and shows what the server
 * answered. The browser never decides access; it classifies the answer and clears every previous result first.
 */
export function IsolationJourney({ step, session, contexts, fixtureVersion, enter, expire }: {
  step?: Step;
  session: Session;
  contexts: AccessContext[];
  fixtureVersion?: string;
  enter: (path: string, session: Session) => void;
  expire: () => void;
}) {
  const scope = journeyContext(contexts);
  const audience: Audience | undefined = scope?.audience;
  const grant = scope?.context.grantId;
  const ready = scope !== undefined && fixtureVersion === ISOLATION_DATASET;
  const [outcome, setOutcome] = useState<Outcome>();
  const [attempt, setAttempt] = useState(0);
  const onExpired = useRef(expire);
  onExpired.current = expire;

  useEffect(() => {
    setOutcome(undefined);
    if (!ready || !audience || !step) return;
    const request = new AbortController();
    void (async () => {
      const result = await accountApi.readRecord(PRACTICE_A, step === 'lecture' ? RECORD_A : RECORD_B, request.signal);
      if (request.signal.aborted) return;
      const next = classify(step, audience, result.ok ? { ok: true, data: result.data } : { ok: false, status: result.status });
      if (next.kind === 'expired') {
        onExpired.current();
        return;
      }
      setOutcome(next);
    })();
    return () => request.abort();
  }, [step, ready, audience, grant, attempt]);

  if (!scope || !ready) return <IsolationChanged />;
  const { context } = scope;
  const patient = audience === 'patient';
  const practice = practiceLabel(context);
  // An old or unknown link makes no request: it shows the same generic refusal as a hidden object.
  const shown: Shown = !step ? (patient ? 'patient' : 'hidden') : outcome?.kind ?? 'loading';
  const requested = step ? '404' : undefined;
  const label = step && audience && outcome ? matrixCase(step, audience, outcome.kind) : undefined;
  const backToSpace = (
    <button type="button" className={`etaye-bouton etaye-bouton--principal${patient ? ' etaye-bouton--patient' : ''}`}
      onClick={() => enter(contextPath(context), session)}>Revenir à mon espace</button>
  );

  let state: ReactNode;
  if (shown === 'loading') state = <IsolationLoading />;
  else if (shown === 'failed') state = <IsolationFailed repeated={attempt > 0} retry={() => setAttempt((count) => count + 1)} />;
  else if (shown === 'allowed' && outcome?.kind === 'allowed') {
    state = (
      <div className="etaye-etat-page" data-isolation-state="allowed" data-isolation-case={label} data-http-status="200">
        <p className="etaye-isolation__etape">Prouvez l’isolation · Lecture autorisée</p>
        <FocusedHeading>Dossier accessible</FocusedHeading>
        <section className="etaye-panneau" aria-labelledby="isolation-record">
          <div className="etaye-panneau__tete"><h2 id="isolation-record">{outcome.name}</h2></div>
          <div className="etaye-panneau__corps">
            <dl className="etaye-isolation__faits">
              <div><dt>Cabinet</dt><dd>{practice}</dd></div>
              <div><dt>Date de naissance</dt>
                <dd><time dateTime={outcome.birthDate}>{frenchDate(outcome.birthDate)}</time></dd></div>
            </dl>
          </div>
        </section>
        <Actions>
          <button type="button" className="etaye-bouton etaye-bouton--principal"
            onClick={() => enter(OUTSIDE_PATH, session)}>Voir le refus hors du cabinet</button>
          <BackToDemo discreet />
        </Actions>
      </div>
    );
  } else if (shown === 'restricted') {
    state = (
      <div className="etaye-etat-page" data-isolation-state="restricted" data-isolation-case={label} data-http-status="403">
        <span className="etaye-etat-page__code">Accès non autorisé · 403</span>
        <FocusedHeading>Cet espace est réservé aux soins</FocusedHeading>
        <p>{roleClause(context)} au {practice} ne donne pas accès aux dossiers patients.</p>
        <Actions>{backToSpace}</Actions>
      </div>
    );
  } else if (shown === 'patient') {
    state = (
      <div className="etaye-etat-page" data-isolation-state="patient" data-isolation-case={label} data-http-status={requested}>
        <span className="etaye-etat-page__code">Élément introuvable</span>
        <FocusedHeading>Cette page n’est pas disponible</FocusedHeading>
        <p>Elle n’existe pas ou ne vous a pas été partagée.</p>
        <Actions>{backToSpace}</Actions>
      </div>
    );
  } else {
    // A hidden object and an old or unknown link share one generic refusal that discloses nothing about the target.
    state = (
      <div className="etaye-etat-page" data-isolation-state="hidden" data-isolation-case={label}
        data-http-status={requested}>
        <span className="etaye-etat-page__code">Élément introuvable · 404</span>
        <FocusedHeading>Élément introuvable</FocusedHeading>
        <p>Vérifiez le lien ou revenez à votre espace.</p>
        <Actions>{backToSpace}</Actions>
      </div>
    );
  }

  return (
    <div className={patient ? 'etaye-isolation--patient' : undefined}>
      <ContextFrame context={context} contexts={contexts} select={(path) => enter(path, session)}
        actor={session.account.displayName} testId="isolation-journey">
        <div key={shown}>{state}</div>
      </ContextFrame>
    </div>
  );
}

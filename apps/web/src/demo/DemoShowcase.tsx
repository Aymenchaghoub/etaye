import { useEffect, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { DemoScenario } from '../api/generated/types.gen';
import { AuthLogo, AuthShell, Banner, FocusedHeading, useRetryDelay } from '../auth/AuthComponents';
import { applyDemoTheme, type FixtureMarker, fixtureChanged, fixtureMarker, OPERATOR_EMAIL, pendingReset, rememberDemo, storeReset } from './demo-entry';
import { demoSign } from './demo-sign';
import { guideScenarios, IsolationGuide, IsolationPanel } from '../isolation/IsolationGuide';
import { ISOLATION_DATASET } from '../isolation/isolation-journey';
import './demo.css';

type Failure = 'unavailable' | 'busy' | 'limited' | 'restricted';
type Scenario = DemoScenario & Required<Pick<DemoScenario,
  'actorName' | 'roleLabel' | 'practiceName' | 'journeySummary' | 'limitsNotice' | 'signInIdentifier'>>;
type Snapshot = { scenarios: Scenario[]; marker: FixtureMarker; operator: 'present' | 'absent' | 'unavailable' };

function isScenario(value: DemoScenario): value is Scenario {
  return value?.synthetic === true && [value.actorName, value.roleLabel, value.practiceName, value.journeySummary,
    value.limitsNotice, value.signInIdentifier].every((field) => typeof field === 'string' && field.length > 0);
}

function failureFor(status: number, code?: string): Failure {
  if (code === 'DEMO_RESET_IN_PROGRESS') return 'busy';
  if (status === 429) return 'limited';
  if (status === 401 || status === 403) return 'restricted';
  return 'unavailable';
}

function DemoProblem({ failure }: { failure: Failure }) {
  const messages = {
    unavailable: ['Démonstration indisponible', 'Le service est temporairement indisponible. Réessayez dans quelques instants.'],
    busy: ['Réinitialisation en cours', 'Le jeu fictif est en cours de chargement. Réessayez dans quelques instants.'],
    limited: ['Demande temporairement limitée', 'Attendez quelques instants avant de réessayer.'],
    restricted: ['Accès réservé', 'Connectez-vous avec le compte opérateur pour réinitialiser la démonstration.'],
  };
  const [title, detail] = messages[failure];
  return <Banner title={title}><p>{detail}</p></Banner>;
}

export function DemoShowcase() {
  const [snapshot, setSnapshot] = useState<Snapshot>();
  const [scenario, setScenario] = useState<Scenario>();
  const [loading, setLoading] = useState(true);
  const [trace, setTrace] = useState(false);
  const [failure, setFailure] = useState<Failure>();
  const [resetFailure, setResetFailure] = useState<Failure>();
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [guide, setGuide] = useState(false);
  const [resetVersion, setResetVersion] = useState<FixtureMarker>();
  const unconfirmedReset = useRef(pendingReset());
  const loadRequest = useRef<AbortController | undefined>(undefined);
  const mutation = useRef<AbortController | undefined>(undefined);
  const { block, waiting } = useRetryDelay();
  const previousAction = useRef<string | undefined>(undefined);
  const detailOpen = Boolean(scenario || confirming || guide);

  useEffect(() => {
    if (detailOpen || !previousAction.current) return;
    const action = document.getElementById(previousAction.current);
    const target = !action || (action instanceof HTMLButtonElement && action.disabled)
      ? document.getElementById('contenu') : action;
    target?.focus({ preventScroll: true });
    previousAction.current = undefined;
  }, [detailOpen]);

  useEffect(() => {
    if (!detailOpen) return;
    const escape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || pending) return;
      setScenario(undefined);
      setConfirming(false);
      setGuide(false);
    };
    window.addEventListener('keydown', escape);
    return () => window.removeEventListener('keydown', escape);
  }, [detailOpen, pending]);

  useEffect(applyDemoTheme, []);

  useEffect(() => {
    if (!loading) return;
    const timer = window.setTimeout(() => setTrace(true), 400);
    return () => window.clearTimeout(timer);
  }, [loading]);

  async function refresh(accepted = false) {
    loadRequest.current?.abort();
    const request = new AbortController();
    loadRequest.current = request;
    setLoading(true);
    setTrace(false);
    setFailure(undefined);
    const [meta, catalogue, session] = await Promise.all([
      accountApi.readMeta(request.signal), accountApi.listDemoScenarios(request.signal),
      accountApi.readSession(request.signal),
    ]);
    if (request.signal.aborted) return;
    setLoading(false);
    const marker = meta.ok ? fixtureMarker(meta.data) : undefined;
    if (!meta.ok || !catalogue.ok) {
      const problem = !catalogue.ok ? catalogue : !meta.ok ? meta : undefined;
      setFailure(failureFor(problem?.status ?? 0, problem?.code));
      if (problem?.status === 429) block(problem.retryAfter);
      return;
    }
    const scenarios = catalogue.data?.items;
    if (!marker || !Array.isArray(scenarios) || !scenarios.every(isScenario)
      || scenarios.some((item) => item.datasetVersion !== marker.version)) {
      setFailure('unavailable');
      return;
    }
    const operator = session.ok
      ? session.data?.account?.email === OPERATOR_EMAIL ? 'present' : 'absent'
      : session.status === 401 ? 'absent' : 'unavailable';
    setSnapshot({ marker, scenarios, operator });
    const attempt = unconfirmedReset.current;
    if (attempt && fixtureChanged(attempt.marker, marker)) {
      unconfirmedReset.current = undefined;
      storeReset();
      setResetVersion(marker);
    } else if (accepted) {
      setFailure('unavailable');
    }
  }

  useEffect(() => {
    void refresh();
    return () => {
      loadRequest.current?.abort();
      mutation.current?.abort();
    };
  }, [block]);

  async function reset() {
    if (!snapshot || pending || waiting) return;
    const request = new AbortController();
    mutation.current = request;
    setPending(true);
    setResetFailure(undefined);
    let attempt = unconfirmedReset.current;
    if (!attempt) {
      const baseline = await accountApi.readMeta(request.signal);
      if (request.signal.aborted) return;
      const marker = baseline.ok ? fixtureMarker(baseline.data) : undefined;
      if (!marker) {
        setPending(false);
        setResetFailure(baseline.ok ? 'unavailable' : failureFor(baseline.status, baseline.code));
        if (!baseline.ok && baseline.status === 429) block(baseline.retryAfter);
        return;
      }
      attempt = { key: crypto.randomUUID(), marker };
      unconfirmedReset.current = attempt;
      storeReset(attempt);
    }
    const result = await accountApi.resetDemo(attempt.key, request.signal);
    if (request.signal.aborted) return;
    const accepted = result.ok && result.data?.accepted === true;
    let fixtureRestored = false;
    if (!result.ok && (result.status === 0 || result.status >= 500)) {
      const meta = await accountApi.readMeta(request.signal);
      if (request.signal.aborted) return;
      const marker = meta.ok ? fixtureMarker(meta.data) : undefined;
      fixtureRestored = Boolean(marker && fixtureChanged(attempt.marker, marker));
    }
    setPending(false);
    if (accepted || fixtureRestored) {
      previousAction.current = 'contenu';
      setConfirming(false);
      void refresh(accepted);
      return;
    }
    if (!result.ok && (result.status === 401 || result.status === 403)) {
      setSnapshot({ ...snapshot, operator: 'absent' });
    }
    if (!result.ok && result.status === 429) block(result.retryAfter);
    setResetFailure(result.ok ? 'unavailable' : failureFor(result.status, result.code));
  }

  const isolation = snapshot && !failure && snapshot.marker.version === ISOLATION_DATASET
    ? guideScenarios(snapshot.scenarios) : undefined;

  if (guide && snapshot && isolation) return (
    <IsolationGuide scenarios={isolation} marker={snapshot.marker} back={() => setGuide(false)} />
  );

  if (scenario && snapshot) return (
    <AuthShell wide>
      <div>
        <p className="etaye-2">Démonstration avec données fictives</p>
        <FocusedHeading>Scénario {scenario.roleLabel.toLocaleLowerCase('fr')}</FocusedHeading>
      </div>
      <div className="etaye-auth__carte">
        <dl className="etaye-demo-facts">
          <dt>Vous jouez</dt><dd><strong>{scenario.actorName}</strong> · {scenario.roleLabel}</dd>
          <dt>Cabinet</dt><dd>{scenario.practiceName}</dd>
          <dt>Données</dt><dd>Jeu {scenario.datasetVersion} · <time dateTime="2026-11-02T16:00:00+01:00">2 novembre 2026 à 16 h</time></dd>
          <dt>Limites</dt><dd>{scenario.limitsNotice} Ne saisissez aucune donnée réelle.</dd>
          <dt>Identifiant fictif</dt><dd>{scenario.signInIdentifier}</dd>
        </dl>
        <p>{scenario.journeySummary}</p>
        <p>Le mot de passe figure dans le <a href="/demo-guide.txt" target="_blank" rel="noreferrer">README de la démonstration</a>.</p>
        <div className="etaye-demo-actions">
          <a className="etaye-bouton etaye-bouton--principal"
            href={scenario.id === 'patiente' ? '/connexion/patient' : '/connexion'}
            onClick={() => rememberDemo(snapshot.marker, scenario)}>Ouvrir la démonstration</a>
          <button className="etaye-bouton etaye-bouton--discret" type="button" onClick={() => setScenario(undefined)}>Retour</button>
        </div>
      </div>
    </AuthShell>
  );

  if (confirming && snapshot) return (
    <AuthShell wide>
      <FocusedHeading>Réinitialiser la démonstration</FocusedHeading>
      <div className="etaye-auth__carte">
        <p>Cette action efface les modifications, recharge le jeu {snapshot.marker.version}
          et ferme les sessions ouvertes.</p>
        <p className="etaye-3">Réservé à l’opérateur de la démonstration.</p>
        {resetFailure && <DemoProblem failure={resetFailure} />}
        <p className="etaye-demo-mutation-status" role="status">{pending ? 'Réinitialisation en cours…' : ''}</p>
        <div className="etaye-demo-actions">
          <button className="etaye-bouton etaye-bouton--secondaire" type="button" disabled={pending}
            onClick={() => setConfirming(false)}>Retour</button>
          <button className="etaye-bouton etaye-bouton--destructif" type="button"
            disabled={pending || waiting || snapshot.operator !== 'present'} onClick={() => void reset()}>Confirmer la réinitialisation</button>
        </div>
      </div>
    </AuthShell>
  );

  return <div className="etaye-root etaye-demo-page">
    <main className="etaye-showcase" id="contenu" tabIndex={-1}>
      <header className="etaye-showcase__header">
        <AuthLogo />
        <span className="etaye-demo">Démonstration — personnes et données fictives</span>
      </header>
      {failure && <div className="etaye-showcase__pile">
        <DemoProblem failure={failure} />
        <button type="button" className="etaye-bouton etaye-bouton--principal etaye-showcase__retry" disabled={waiting || loading}
          onClick={() => {
            document.getElementById('contenu')?.focus({ preventScroll: true });
            void refresh();
          }}>Réessayer</button>
      </div>}
      <div className="etaye-showcase__intro">
        <div className="etaye-showcase__pile">
          <h1>Préparer une consultation avec des informations datées et vérifiables</h1>
          <p className="etaye-2 etaye-showcase__lede">
            Étaye aide un cabinet à préparer ses consultations, à tenir le dossier et à rédiger des comptes rendus.
            Les brouillons générés citent leurs sources et restent des brouillons tant qu’un praticien ne les a pas relus et validés.
          </p>
          <p className="etaye-3">Ce parcours présente les accès aux espaces fictifs. Aucun usage clinique.</p>
        </div>
        <div className="etaye-showcase__document">
          <p>Depuis la consultation du 20 octobre, Claire Martin a signalé un souvenir de réaction à la pénicilline
            <sup className="etaye-demo-citation" aria-label="Source 1">1</sup>.
          </p>
          <figure className="etaye-showcase__source">
            <figcaption><span className="etaye-demo-citation" aria-hidden="true">1</span> Préparation de visite ·{' '}
              <time dateTime="2026-11-02T09:10:00+01:00">02/11/2026 09:10</time>
            </figcaption>
            <blockquote><mark>Je me souviens d’une réaction à la pénicilline dans ma jeunesse. Je ne sais plus laquelle.</mark></blockquote>
            <p className="etaye-demo-declared">Déclaré par la patiente · non confirmé</p>
          </figure>
        </div>
      </div>
      <div className="etaye-showcase__pile" role="status">
        {loading && <div className="etaye-showcase__loading">
          {trace && <div className="etaye-demo-sign" data-state="loading" dangerouslySetInnerHTML={{ __html: demoSign }} />}
          <p>Préparation du scénario…</p>
        </div>}
      </div>
      {resetVersion && <Banner title="Démonstration réinitialisée" information>
        <p>Le jeu {resetVersion.version} a été rechargé. Les sessions ouvertes ont été fermées.</p>
      </Banner>}
      <section aria-labelledby="scenarios" className="etaye-showcase__pile">
        <h2 id="scenarios">Choisir un scénario de démonstration</h2>
        {snapshot && !failure && <ul className="etaye-showcase__list">
          {snapshot.scenarios.map((item) => <li key={item.id}>
            <span className={`etaye-demo-icon etaye-demo-icon--${item.id}`} aria-hidden="true" />
            <div>
              <strong>{item.roleLabel} · {item.actorName}</strong>
              <p className="etaye-2">{item.practiceName} · {item.journeySummary}</p>
            </div>
            <button type="button" className="etaye-bouton etaye-bouton--secondaire"
              id={`scenario-${item.id}`} aria-label={`Choisir · ${item.actorName}`} disabled={!item.available || loading}
              onClick={(event) => { previousAction.current = event.currentTarget.id; setScenario(item); }}>Choisir</button>
          </li>)}
        </ul>}
        {isolation && <IsolationPanel disabled={loading} open={(button) => {
          previousAction.current = button.id;
          setGuide(true);
        }} />}
        {snapshot?.scenarios.length === 0 && !failure && !loading && <p>Aucun scénario disponible.</p>}
        <p className="etaye-3">Personnes et cabinets entièrement fictifs{snapshot ? ` (jeu ${snapshot.marker.version})` : ''}.
          La démonstration peut être réinitialisée à tout moment.</p>
      </section>
      {snapshot?.operator === 'present' && !failure && <div className="etaye-showcase__operator">
        <button type="button" className="etaye-bouton etaye-bouton--secondaire" disabled={loading} id="demo-reset"
          onClick={(event) => {
            previousAction.current = event.currentTarget.id;
            setResetFailure(undefined);
            setConfirming(true);
          }}>Réinitialiser la démonstration</button>
      </div>}
      {snapshot?.operator === 'unavailable' && !failure && <div className="etaye-showcase__pile">
        <Banner title="Session opérateur indisponible">
          <p>La vérification de la session a échoué.</p>
        </Banner>
        <button type="button" className="etaye-bouton etaye-bouton--secondaire etaye-showcase__retry" disabled={loading}
          onClick={() => {
            document.getElementById('contenu')?.focus({ preventScroll: true });
            void refresh();
          }}>Vérifier la session</button>
      </div>}
      {snapshot?.operator === 'absent' && !failure && <a className="etaye-bouton etaye-bouton--discret etaye-showcase__operator"
        href="/connexion" onClick={() => rememberDemo(snapshot.marker)}>Connexion opérateur</a>}
    </main>
  </div>;
}

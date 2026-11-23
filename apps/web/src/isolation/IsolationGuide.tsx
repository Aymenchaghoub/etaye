import type { DemoScenario } from '../api/generated/types.gen';
import { AuthLogo, FocusedHeading } from '../auth/AuthComponents';
import { type FixtureMarker, rememberDemo } from '../demo/demo-entry';
import { journeySignInPath } from './isolation-journey';
import './isolation.css';

const NORA = 'praticienne';
const MILA = 'administration';
const CLAIRE = 'patiente';

/** The guided entry is offered only when the catalogue holds the three fictional accounts it walks through. */
export function guideScenarios(scenarios: DemoScenario[]) {
  const nora = scenarios.find(({ id }) => id === NORA);
  const mila = scenarios.find(({ id }) => id === MILA);
  const claire = scenarios.find(({ id }) => id === CLAIRE);
  return nora && mila && claire ? { nora, mila, claire } : undefined;
}

export function IsolationPanel({ open, disabled }: { open: (button: HTMLButtonElement) => void; disabled: boolean }) {
  return (
    <section className="etaye-isolation-panel" aria-labelledby="isolation-panel-title">
      <h2 id="isolation-panel-title">Prouvez l’isolation</h2>
      <p>Voyez un dossier s’ouvrir pour une praticienne autorisée, puis suivez trois cas où l’accès est refusé.</p>
      <div className="etaye-isolation__actions">
        <button type="button" id="isolation-open" className="etaye-bouton etaye-bouton--secondaire" disabled={disabled}
          onClick={(event) => open(event.currentTarget)}>Ouvrir le parcours</button>
      </div>
    </section>
  );
}

export function IsolationGuide({ scenarios, marker, back }: {
  scenarios: NonNullable<ReturnType<typeof guideScenarios>>;
  marker: FixtureMarker;
  back: () => void;
}) {
  const { nora, mila, claire } = scenarios;
  return (
    <div className="etaye-root etaye-demo-page">
      <main className="etaye-showcase" id="contenu">
        <header className="etaye-showcase__header">
          <AuthLogo />
          <span className="etaye-demo">Démonstration — personnes et données fictives</span>
        </header>
        <section className="etaye-etat-page etaye-isolation-guide">
          <FocusedHeading>Prouvez l’isolation</FocusedHeading>
          <p>Le parcours commence dans le Cabinet des Tilleuls avec le compte fictif de Nora Lefebvre.</p>
          <ol>
            <li>Lire un dossier autorisé avec Nora.</li>
            <li>Vérifier le refus d’un dossier hors de son périmètre.</li>
            <li>Se reconnecter avec{' '}
              <a href={journeySignInPath(false)} onClick={() => rememberDemo(marker, mila)}>Mila</a>, puis avec{' '}
              <a href={journeySignInPath(true)} onClick={() => rememberDemo(marker, claire)}>Claire</a>,
              pour voir les refus dans leurs espaces respectifs.</li>
          </ol>
          <div className="etaye-isolation__actions">
            <a className="etaye-bouton etaye-bouton--principal" href={journeySignInPath(false)}
              onClick={() => rememberDemo(marker, nora)}>Commencer avec Nora</a>
            <button type="button" className="etaye-bouton etaye-bouton--discret" onClick={back}>
              Revenir à la démonstration
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}

import type { AccessContext, Session } from '../api/generated/types.gen';
import '../../../../design/received/2026-11-03/icons.css';
import { AuthLogo, AuthShell, Banner, FocusedHeading } from './AuthComponents';
import { contextPath, practiceLabel, roleLabel } from './access-contexts';
import { ACCOUNT_PATH } from './session-work';
import './practice-context.css';

export function PracticeChooserHeading({ restricted = false }: { restricted?: boolean }) {
  return (
    <div>
      <FocusedHeading>{restricted ? 'Accès modifié' : 'Choisir un contexte de travail'}</FocusedHeading>
      <p className="etaye-2 etaye-auth__introduction">
        Chaque cabinet a ses propres dossiers. Les recherches et brouillons ouverts restent dans leur contexte.
      </p>
    </div>
  );
}

export function PracticeChooser({ session, contexts, restricted, select, retry, signOut }: {
  session: Session;
  contexts: AccessContext[];
  restricted: boolean;
  select: (path: string) => void;
  retry: () => void;
  signOut: () => void;
}) {
  return (
    <AuthShell wide>
      <PracticeChooserHeading restricted={restricted} />
      {restricted && <Banner title="Ce contexte n’est plus disponible">
        <p>Votre accès a changé. Choisissez un contexte encore disponible.</p>
      </Banner>}
      {contexts.length ? (
        <ul className="etaye-pliste">
          {contexts.map((entry) => {
            const path = contextPath(entry);
            const practice = practiceLabel(entry);
            const role = roleLabel(entry, contexts);
            return (
              <li key={path}>
                <span className="etaye-icone etaye-icone--24 etaye-icone--cabinet" aria-hidden="true" />
                <div className="etaye-pliste__txt">
                  <strong>{practice}</strong>
                  <small>{role}</small>
                </div>
                <button className="etaye-bouton etaye-bouton--secondaire" type="button"
                  aria-label={`Ouvrir · ${role} · ${practice}`} onClick={() => select(path)}>Ouvrir</button>
              </li>
            );
          })}
        </ul>
      ) : <>
        <p role="status">Aucun contexte actif. Si vous attendiez un accès, contactez le cabinet.</p>
        <button type="button" className="etaye-bouton etaye-bouton--secondaire" onClick={retry}>Vérifier mes accès</button>
      </>}
      <span className="etaye-2">{session.account.displayName}</span>
      <button type="button" className="etaye-bouton etaye-bouton--discret" onClick={signOut}>Se déconnecter</button>
    </AuthShell>
  );
}

export function PracticeDestination({ session, context, contexts, select, signOut }: {
  session: Session;
  context: AccessContext;
  contexts: AccessContext[];
  select: (path: string) => void;
  signOut: () => void;
}) {
  const patient = context.kind === 'PORTAL';
  const practice = practiceLabel(context);
  const role = roleLabel(context, contexts);
  const label = <>
    <span className="etaye-icone etaye-icone--18 etaye-icone--cabinet" aria-hidden="true" />
    <span>
      <strong className="etaye-contexte__cabinet">{practice}</strong>{' '}
      <span className="etaye-contexte__role">{role}</span>
    </span>
  </>;

  return (
    <div className="etaye-root etaye-context-destination">
      <header className="etaye-context-top">
        <AuthLogo patient={patient} />
        {contexts.length > 1 ? (
          <button className="etaye-contexte" type="button" onClick={() => select(ACCOUNT_PATH)}
            aria-label={`${practice} ${role} — Changer de contexte`}>
            {label}
            <span className="etaye-icone etaye-icone--deplier" aria-hidden="true" />
          </button>
        ) : <div className="etaye-contexte">{label}</div>}
        <span className="etaye-demo">Démonstration — personnes et données fictives</span>
      </header>
      <main className="etaye-auth__corps etaye-auth__corps--large" data-testid="protected-account" id="contenu">
        <FocusedHeading>{patient ? 'Votre espace patient' : 'Votre espace de travail'}</FocusedHeading>
        <p>{session.account.displayName}</p>
        <button type="button" className="etaye-bouton etaye-bouton--discret" onClick={signOut}>Se déconnecter</button>
      </main>
    </div>
  );
}

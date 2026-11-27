import type { AccessContext, Session } from '../api/generated/types.gen';
import { contextPath } from '../auth/access-contexts';
import { activateScope, scopeKey } from './memory';
import { PatientForm } from './PatientForm';
import { PatientsPage } from './PatientsPage';
import { ChronologyPage } from '../record/ChronologyPage';
import { HistoryPage } from '../record/HistoryPage';
import { MeasurementsPage } from '../record/MeasurementsPage';
import { LaboratoryPage } from '../record/LaboratoryPage';
import { RecordPage } from '../record/RecordPage';
import { hasUsableZone, resolveZone } from '../record/zone';
import { PractitionerShell } from './PractitionerShell';
import type { PatientRoute } from './routes';

const recordPages = { record: ChronologyPage, history: HistoryPage, measurements: MeasurementsPage, laboratory: LaboratoryPage };

/**
 * The patient directory of one practice grant (S-07, S-08). The server decides every request: this component only
 * renders what the practitioner shell, the list and the identity form were given by the already verified session.
 */
export function PatientsArea({ session, context, contexts, route, select, signOut, expire }: {
  session: Session;
  context: AccessContext;
  contexts: AccessContext[];
  route: PatientRoute;
  select: (path: string) => void;
  signOut: () => void;
  expire: () => void;
}) {
  const scope = scopeKey(session.account.id, context.grantId);
  // Links are built from the verified session context, never from the text of the address bar.
  const base = contextPath(context);
  // Memory held for another account or practice grant is dropped before anything reads it.
  activateScope(scope);
  return (
    <PractitionerShell session={session} context={context} contexts={contexts} select={select} signOut={signOut}>
      {route.view === 'list' && (
        <PatientsPage scope={scope} practiceId={context.practiceId} base={base} expire={expire} />
      )}
      {route.view === 'new' && (
        <PatientForm mode="new" scope={scope} practiceId={context.practiceId} base={base} expire={expire} />
      )}
      {(route.view === 'record' || route.view === 'history' || route.view === 'measurements' || route.view === 'laboratory') && (
        <RecordPage tab={route.view} scope={scope} practiceId={context.practiceId} base={base} recordId={route.recordId}
          zone={resolveZone(context.timezone)} zoneKnown={hasUsableZone(context.timezone)} expire={expire}>
          {/* Keyed by practice and record: the state of one record's screen never survives a move to another record. */}
          {(tab) => {
            const Page = recordPages[tab];
            return <Page key={`${context.practiceId}:${route.recordId}`} />;
          }}
        </RecordPage>
      )}
      {route.view === 'edit' && (
        <PatientForm mode="edit" scope={scope} practiceId={context.practiceId} base={base}
          recordId={route.recordId} expire={expire} />
      )}
    </PractitionerShell>
  );
}

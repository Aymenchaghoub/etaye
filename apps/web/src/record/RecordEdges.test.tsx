// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AccessContext, HistorySummary, Record as PatientRecord, Session } from '../api/generated/types.gen';
import { clearPatientMemory } from '../patients/memory';
import { PatientsPage } from '../patients/PatientsPage';
import { PractitionerShell } from '../patients/PractitionerShell';
import { ChronologyPage } from './ChronologyPage';
import { HistoryPage } from './HistoryPage';
import { MeasurementsPage } from './MeasurementsPage';
import { RecordPage } from './RecordPage';
import { PatientsArea } from '../patients/PatientsArea';

const practiceId = '00000000-0000-4000-8000-000000000010';
const recordId = '00000000-0000-4000-8000-000000000021';
const patientId = '00000000-0000-4000-8000-000000000022';
const base = `/compte/pratiques/${practiceId}`;

const context: AccessContext = {
  kind: 'MEMBERSHIP',
  grantId: practiceId,
  practiceId,
  practiceName: 'Cabinet des Tilleuls',
  displayRole: 'CLINICAL',
  timezone: 'Europe/Paris',
};

const session: Session = {
  account: {
    id: 'acc-1',
    displayName: 'Dr Nora Lefebvre',
    email: 'nora@example.test',
    state: 'ACTIVE',
    locale: 'fr-FR',
    version: 0,
    createdAt: '2026-11-26T10:00:00Z',
    updatedAt: '2026-11-26T10:00:00Z',
  },
  membershipIds: [practiceId],
  portalLinkIds: [],
  contexts: [context],
  expiresAt: '2026-11-26T12:00:00Z',
  returnTo: '/compte',
};

const recordPayload: PatientRecord = {
  id: recordId,
  practiceId,
  patientId,
  recordRevision: 1,
  patient: {
    id: patientId,
    practiceId,
    recordId,
    firstName: 'Claire',
    lastName: 'Martin',
    dateOfBirth: '1978-04-18',
    contactEmail: null,
    phone: null,
    version: 1,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  },
  version: 1,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
};

const conflictingHistory: HistorySummary = {
  asOf: null,
  sections: [
    {
      category: 'ALLERGY',
      state: 'CONFLICTING',
      truncated: true,
      statements: [
        {
          id: 'st-1',
          practiceId,
          version: 1,
          createdAt: '2026-11-01T09:00:00Z',
          updatedAt: '2026-11-01T09:00:00Z',
          category: 'ALLERGY',
          assertionKind: 'PRESENT',
          text: 'Allergie pénicilline',
          eventDate: { precision: 'DATE', date: '2010-05-01' },
          recordedAt: '2026-11-01T09:00:00Z',
          authorLabel: 'Dr Nora',
          sourceType: 'PATIENT_REPORTED',
          sourceRevisionId: 'src-1',
          sourceKind: 'CONSULTATION_NOTE',
          sourceReference: 'Note 1',
        },
        {
          id: 'st-2',
          practiceId,
          version: 1,
          createdAt: '2026-11-02T09:00:00Z',
          updatedAt: '2026-11-02T09:00:00Z',
          category: 'ALLERGY',
          assertionKind: 'EXPLICITLY_ABSENT',
          text: 'Aucune allergie connue',
          eventDate: { precision: 'DATE', date: '2026-11-02' },
          recordedAt: '2026-11-02T09:00:00Z',
          authorLabel: 'Dr Claire',
          sourceType: 'PRACTITIONER_OBSERVED',
          sourceRevisionId: 'src-2',
          sourceKind: 'UPLOADED_DOCUMENT',
          sourceReference: 'Courrier 2',
        },
      ],
    },
    { category: 'HISTORY', state: 'NOT_RECORDED', statements: [], truncated: false },
    { category: 'REPORTED_TREATMENT', state: 'NOT_ASKED', statements: [], truncated: false },
  ],
};

beforeEach(() => {
  clearPatientMemory();
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: false,
    media: '',
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  clearPatientMemory();
});

describe('PatientsPage and Record edge states', () => {
  it('renders PatientsPage 503 hard failure, retry to empty directory, 429 limit and 403 refusal', async () => {
    const user = userEvent.setup();
    let mode: '503' | 'empty' | '429' | '403' = '503';
    vi.stubGlobal('fetch', vi.fn(() => {
      if (mode === '503') return Promise.resolve(Response.json({ code: 'DOWN' }, { status: 503 }));
      if (mode === '429') return Promise.resolve(Response.json({ code: 'LIMIT' }, { status: 429, headers: { 'Retry-After': '12' } }));
      if (mode === '403') return Promise.resolve(Response.json({ code: 'FORBIDDEN' }, { status: 403 }));
      return Promise.resolve(Response.json({ items: [], nextCursor: null }));
    }));

    render(
      <PractitionerShell session={session} context={context} contexts={[context]} select={vi.fn()} signOut={vi.fn()}>
        <PatientsPage scope="s-1" practiceId={practiceId} base={base} expire={vi.fn()} />
      </PractitionerShell>,
    );
    expect((await screen.findByRole('alert')).textContent).toContain('La liste n’a pas pu être chargée');

    mode = '429';
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect((await screen.findByRole('alert')).textContent).toContain('12 secondes');

    mode = 'empty';
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('Aucun patient dans ce cabinet')).toBeTruthy();

    mode = '403';
    await user.type(screen.getByLabelText('Nom, prénom ou date de naissance'), 'Martin');
    await user.click(screen.getByRole('button', { name: 'Rechercher' }));
    expect(await screen.findByText('Accès aux patients non autorisé')).toBeTruthy();
  });

  it('renders RecordPage 404/403 states, fallback Paris zone notice, History conflict review and Chronology/Measurements empty & error states', async () => {
    const user = userEvent.setup();
    let tabFail = true;
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const url = new URL(request.url);
      if (url.pathname.endsWith(`/records/${recordId}`)) return Promise.resolve(Response.json(recordPayload));
      if (url.pathname.endsWith('/history')) return Promise.resolve(Response.json(conflictingHistory));
      if (url.pathname.endsWith('/timeline')) {
        return Promise.resolve(tabFail
          ? Response.json({ code: 'DOWN' }, { status: 503 })
          : Response.json({ items: [], nextCursor: null }));
      }
      if (url.pathname.endsWith('/measurements')) {
        return Promise.resolve(tabFail
          ? Response.json({ code: 'LIMIT' }, { status: 429, headers: { 'Retry-After': '10' } })
          : Response.json({ items: [], nextCursor: null }));
      }
      if (url.pathname.includes('/sources/')) return Promise.resolve(Response.json({ code: 'NOT_FOUND' }, { status: 404 }));
      throw new Error(`Unexpected ${url.pathname}`);
    }));

    const { rerender } = render(
      <PractitionerShell session={session} context={context} contexts={[context]} select={vi.fn()} signOut={vi.fn()}>
        <RecordPage tab="history" scope="s-2" practiceId={practiceId} base={base} recordId={recordId}
          zone="Europe/Paris" zoneKnown={false} expire={vi.fn()}>
          {() => <HistoryPage />}
        </RecordPage>
      </PractitionerShell>,
    );

    expect(await screen.findByText('Fuseau du cabinet non disponible')).toBeTruthy();
    await user.click(await screen.findByRole('button', { name: /Examiner les sources/ }));
    expect(await screen.findByText(/Examen des sources en ligne/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Retour aux antécédents et allergies' }));
    expect(await screen.findByRole('button', { name: /Examiner les sources/ })).toBeTruthy();

    // Switch to Chronology failed -> retry -> empty, plus invalid AsOf date check.
    rerender(
      <PractitionerShell session={session} context={context} contexts={[context]} select={vi.fn()} signOut={vi.fn()}>
        <RecordPage tab="record" scope="s-2" practiceId={practiceId} base={base} recordId={recordId}
          zone="Europe/Paris" zoneKnown expire={vi.fn()}>
          {() => <ChronologyPage />}
        </RecordPage>
      </PractitionerShell>,
    );
    expect((await screen.findByRole('alert')).textContent).toContain('chronologie');
    tabFail = false;
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('Aucun événement au dossier')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Voir le dossier à une date passée' }));
    await user.click(screen.getByRole('button', { name: 'Afficher cette date' }));
    expect(screen.getByRole('alert').textContent).toContain('Date');

    // Switch to Measurements 429 -> retry -> empty.
    tabFail = true;
    rerender(
      <PractitionerShell session={session} context={context} contexts={[context]} select={vi.fn()} signOut={vi.fn()}>
        <RecordPage tab="measurements" scope="s-2" practiceId={practiceId} base={base} recordId={recordId}
          zone="Europe/Paris" zoneKnown expire={vi.fn()}>
          {() => <MeasurementsPage />}
        </RecordPage>
      </PractitionerShell>,
    );
    expect((await screen.findByRole('alert')).textContent).toContain('Trop de requêtes');
    tabFail = false;
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('Aucune mesure au dossier')).toBeTruthy();
  });

  it('validates Chronology date range order and renders RecordPage 403 and 404 refusals', async () => {
    const user = userEvent.setup();
    let mode: 'ok' | '403' | '404' = 'ok';
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const url = new URL(request.url);
      if (mode === '403') return Promise.resolve(Response.json({ code: 'FORBIDDEN' }, { status: 403 }));
      if (mode === '404') return Promise.resolve(Response.json({ code: 'NOT_FOUND' }, { status: 404 }));
      if (url.pathname.endsWith(`/records/${recordId}`)) return Promise.resolve(Response.json(recordPayload));
      if (url.pathname.endsWith('/history')) return Promise.resolve(Response.json(conflictingHistory));
      if (url.pathname.endsWith('/timeline')) {
        return Promise.resolve(Response.json({
          items: [{
            id: 'tl-1',
            kind: 'CONSULTATION_NOTE',
            label: 'Consultation de suivi',
            eventDate: { precision: 'DATE', date: '2026-11-01' },
            recordedAt: '2026-11-01T10:00:00Z',
            corrected: false,
            revision: 1,
          }],
          nextCursor: null,
        }));
      }
      throw new Error(`Unexpected ${url.pathname}`);
    }));

    render(
      <PractitionerShell session={session} context={context} contexts={[context]} select={vi.fn()} signOut={vi.fn()}>
        <RecordPage tab="record" scope="s-3" practiceId={practiceId} base={base} recordId={recordId}
          zone="Europe/Paris" zoneKnown expire={vi.fn()}>
          {() => <ChronologyPage />}
        </RecordPage>
      </PractitionerShell>,
    );
    expect((await screen.findAllByText('Consultation de suivi')).length).toBeGreaterThan(0);

    const filterForm = within(screen.getByRole('search', { name: 'Filtrer la chronologie' }));
    await user.type(filterForm.getAllByLabelText('Jour')[0], '15');
    await user.type(filterForm.getAllByLabelText('Mois')[0], '10');
    await user.type(filterForm.getAllByLabelText('Année')[0], '2026');
    await user.type(filterForm.getAllByLabelText('Jour')[1], '01');
    await user.type(filterForm.getAllByLabelText('Mois')[1], '10');
    await user.type(filterForm.getAllByLabelText('Année')[1], '2026');
    await user.click(screen.getByRole('button', { name: 'Appliquer les filtres' }));
    expect((await screen.findByRole('alert')).textContent).toContain('après la date « Au »');

    mode = '403';
    cleanup();
    render(
      <PractitionerShell session={session} context={context} contexts={[context]} select={vi.fn()} signOut={vi.fn()}>
        <RecordPage tab="record" scope="s-4" practiceId={practiceId} base={base} recordId={recordId}
          zone="Europe/Paris" zoneKnown expire={vi.fn()}>
          {() => <ChronologyPage />}
        </RecordPage>
      </PractitionerShell>,
    );
    expect(await screen.findByRole('heading', { name: 'Cet espace est réservé aux soins' })).toBeTruthy();

    mode = '404';
    cleanup();
    render(
      <PractitionerShell session={session} context={context} contexts={[context]} select={vi.fn()} signOut={vi.fn()}>
        <RecordPage tab="record" scope="s-5" practiceId={practiceId} base={base} recordId={recordId}
          zone="Europe/Paris" zoneKnown expire={vi.fn()}>
          {() => <ChronologyPage />}
        </RecordPage>
      </PractitionerShell>,
    );
    expect(await screen.findByRole('heading', { name: 'Élément introuvable' })).toBeTruthy();
  });

  it('exercises MeasurementsPage filtering, pagination, lineage retry, and PatientsArea view routing', async () => {
    const user = userEvent.setup();
    let lineageCalls = 0;
    let firstMeasurePage = true;
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const url = new URL(request.url);
      if (url.pathname.endsWith('/patients')) {
        return Promise.resolve(Response.json({ items: [], nextCursor: null }));
      }
      if (url.pathname.endsWith(`/records/${recordId}`)) return Promise.resolve(Response.json(recordPayload));
      if (url.pathname.endsWith('/history')) return Promise.resolve(Response.json(conflictingHistory));
      if (url.pathname.endsWith('/timeline')) return Promise.resolve(Response.json({ items: [], nextCursor: null }));
      if (url.pathname.endsWith('/revisions')) {
        lineageCalls += 1;
        if (lineageCalls === 1) return Promise.resolve(Response.json({ code: 'UNAVAILABLE' }, { status: 503 }));
        return Promise.resolve(Response.json({
          items: [
            {
              id: 'm-1', version: 1, revision: 1, createdAt: '2026-10-01T08:00:00Z', updatedAt: '2026-10-01T08:00:00Z',
              practiceId, concept: 'WEIGHT', value: '68.0', unit: 'kg', observedAt: '2026-10-01T08:00:00Z',
              sourceType: 'PRACTITIONER_OBSERVED', sourceRevisionId: 'src-1', recordedAt: '2026-10-01T08:00:00Z', authorLabel: 'Dr Nora', sourceReference: 'S-1',
            },
            {
              id: 'm-2', version: 2, revision: 2, createdAt: '2026-10-02T08:00:00Z', updatedAt: '2026-10-02T08:00:00Z',
              practiceId, concept: 'WEIGHT', value: '69.2', unit: 'kg', observedAt: '2026-10-02T08:00:00Z',
              sourceType: 'PRACTITIONER_OBSERVED', sourceRevisionId: 'src-2', recordedAt: '2026-10-02T08:00:00Z', authorLabel: 'Dr Nora', sourceReference: 'S-2',
              correctionReason: 'Balance étalonnée',
            },
          ],
        }));
      }
      if (url.pathname.endsWith('/measurements')) {
        if (url.searchParams.get('from')) {
          return Promise.resolve(Response.json({ items: [], nextCursor: null, truncated: false }));
        }
        if (url.searchParams.get('cursor') === 'c2') {
          return Promise.resolve(Response.json({
            items: [{
              id: 'm-3', version: 1, revision: 1, createdAt: '2026-09-15T08:00:00Z', updatedAt: '2026-09-15T08:00:00Z',
              practiceId, concept: 'WEIGHT', value: '67.5', unit: 'kg', observedAt: '2026-09-15T08:00:00Z',
              sourceType: 'PRACTITIONER_OBSERVED', sourceRevisionId: 'src-3', recordedAt: '2026-09-15T08:00:00Z', authorLabel: 'Dr Nora', sourceReference: 'S-3',
            }],
            nextCursor: null,
            truncated: false,
          }));
        }
        firstMeasurePage = false;
        return Promise.resolve(Response.json({
          items: [
            {
              id: 'm-2', version: 2, revision: 2, createdAt: '2026-10-02T08:00:00Z', updatedAt: '2026-10-02T08:00:00Z',
              practiceId, concept: 'WEIGHT', value: '69.2', unit: 'kg', observedAt: '2026-10-02T08:00:00Z',
              sourceType: 'PRACTITIONER_OBSERVED', sourceRevisionId: 'src-2', recordedAt: '2026-10-02T08:00:00Z', authorLabel: 'Dr Nora', sourceReference: 'S-2',
              correctionReason: 'Balance étalonnée',
            },
            {
              id: 'm-1b', version: 1, revision: 1, createdAt: '2026-09-25T08:00:00Z', updatedAt: '2026-09-25T08:00:00Z',
              practiceId, concept: 'WEIGHT', value: '68.5', unit: 'kg', observedAt: '2026-09-25T08:00:00Z',
              sourceType: 'PRACTITIONER_OBSERVED', sourceRevisionId: 'src-1b', recordedAt: '2026-09-25T08:00:00Z', authorLabel: 'Dr Nora', sourceReference: 'S-1b',
            },
          ],
          nextCursor: firstMeasurePage ? null : 'c2',
          truncated: true,
        }));
      }
      throw new Error(`Unexpected ${url.pathname}`);
    }));

    const select = vi.fn();
    const expire = vi.fn();
    const { rerender } = render(
      <PatientsArea session={session} context={context} contexts={[context]} route={{ base, view: 'measurements', recordId }}
        select={select} signOut={vi.fn()} expire={expire} />,
    );
    expect(await screen.findByText(/Courbe limitée aux mesures chargées/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Afficher plus de mesures' }));
    expect((await screen.findAllByText('67,5')).length).toBeGreaterThanOrEqual(1);

    await user.click(screen.getByRole('button', { name: /Voir l’historique/ }));
    expect(await screen.findByText(/L’historique n’a pas pu être chargé/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText(/version précédente 68,0 kg conservée/)).toBeTruthy();

    const search = within(screen.getByRole('search', { name: 'Filtrer les mesures' }));
    await user.selectOptions(search.getByLabelText('Période'), '3m');
    await user.selectOptions(search.getByLabelText('Mesure'), 'c:WEIGHT');
    await user.click(search.getByRole('button', { name: 'Appliquer les filtres' }));
    expect(await screen.findByText('Aucune mesure ne correspond à ces filtres')).toBeTruthy();
    await user.click(search.getByRole('button', { name: 'Réinitialiser les filtres' }));
    expect(await screen.findByText(/Courbe limitée aux mesures chargées/)).toBeTruthy();

    for (const route of [
      { base, view: 'list' as const },
      { base, view: 'new' as const },
      { base, view: 'edit' as const, recordId },
      { base, view: 'history' as const, recordId },
      { base, view: 'record' as const, recordId },
    ]) {
      rerender(
        <PatientsArea session={session} context={context} contexts={[context]} route={route}
          select={select} signOut={vi.fn()} expire={expire} />,
      );
    }
    expect(await screen.findByText('Aucun événement au dossier')).toBeTruthy();
  });
});

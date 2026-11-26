// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AccessContext, HistorySummary, Record as PatientRecord, Session, Source } from '../api/generated/types.gen';
import { PractitionerShell } from '../patients/PractitionerShell';
import { RecordPage } from './RecordPage';
import { useSource } from './SourceDisclosure';

const practiceId = '00000000-0000-4000-8000-000000000010';
const recordId = '00000000-0000-4000-8000-000000000021';
const sourceId = '00000000-0000-4000-8000-000000000051';
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
    id: '00000000-0000-4000-8000-000000000099',
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
  patientId: '00000000-0000-4000-8000-000000000022',
  version: 1,
  recordRevision: 1,
  createdAt: '2026-10-01T08:00:00Z',
  updatedAt: '2026-10-01T08:00:00Z',
  patient: {
    id: '00000000-0000-4000-8000-000000000022',
    practiceId,
    recordId,
    firstName: 'Claire',
    lastName: 'Martin',
    dateOfBirth: '1988-04-12',
    phone: null,
    contactEmail: null,
    version: 1,
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-01T08:00:00Z',
  },
};

const historyPayload: HistorySummary = {
  asOf: null,
  sections: [
    { category: 'ALLERGY', state: 'NOT_RECORDED', statements: [], truncated: false },
    { category: 'HISTORY', state: 'NOT_RECORDED', statements: [], truncated: false },
    { category: 'REPORTED_TREATMENT', state: 'NOT_RECORDED', statements: [], truncated: false },
  ],
};

const sourcePayload: Source = {
  id: sourceId,
  practiceId,
  recordId,
  reference: 'SRC-UNIT-001',
  revision: 1,
  kind: 'CONSULTATION_NOTE',
  sourceType: 'PRACTITIONER_OBSERVED',
  authorLabel: 'Dr Nora Lefebvre',
  eventDate: { precision: 'DATE', date: '2026-10-10' },
  recordedAt: '2026-10-10T09:05:00Z',
  excerpt: 'Extrait synthétique de consultation.',
  locator: 'NOTE-1',
};

function Harness({ expire }: { expire: () => void }) {
  const source = useSource({ id: sourceId, reference: 'SRC-UNIT-001', number: 1 }, practiceId, undefined, expire);
  return <div>{source.marker}{source.panel}</div>;
}

beforeEach(() => {
  sessionStorage.clear();
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe('SourceDisclosure', () => {
  it('loads source on disclosure, retries after outage, and handles 403 refusal', async () => {
    const user = userEvent.setup();
    let sourceCalls = 0;
    const expire = vi.fn();
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : (input as Request).url;
      const pathname = new URL(url, 'http://localhost').pathname;
      if (pathname.endsWith(`/records/${recordId}`)) return Response.json(recordPayload);
      if (pathname.endsWith('/history')) return Response.json(historyPayload);
      if (pathname.includes(`/sources/${sourceId}`)) {
        sourceCalls += 1;
        if (sourceCalls === 1) return Response.json({ code: 'UNAVAILABLE' }, { status: 503 });
        if (sourceCalls === 2) return Response.json(sourcePayload);
        return Response.json({ code: 'FORBIDDEN' }, { status: 403 });
      }
      throw new Error(`Unexpected ${pathname}`);
    }));

    render(
      <PractitionerShell session={session} context={context} contexts={[context]} select={vi.fn()} signOut={vi.fn()}>
        <RecordPage tab="record" scope="unit-record" practiceId={practiceId} base={base}
          recordId={recordId} zone="Europe/Paris" expire={expire}>
          {() => <Harness expire={expire} />}
        </RecordPage>
      </PractitionerShell>,
    );

    const toggle = await screen.findByRole('button', { name: /Afficher la source/ });
    await user.click(toggle);
    expect(await screen.findByText(/La source n’a pas pu être chargée/)).toBeDefined();

    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText(/Extrait synthétique de consultation/)).toBeDefined();

    await user.click(screen.getByRole('button', { name: /Masquer la source/ }));
    await user.click(screen.getByRole('button', { name: /Afficher la source/ }));
    await waitFor(() => expect(screen.getByText(/Accès non autorisé/)).toBeDefined());
  });
});

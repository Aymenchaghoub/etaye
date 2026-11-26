// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AccessContext, Patient, Session } from '../api/generated/types.gen';
import { clearPatientMemory } from './memory';
import { PatientForm } from './PatientForm';
import { PractitionerShell } from './PractitionerShell';

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

const existingPatient: Patient = {
  id: patientId,
  practiceId,
  recordId,
  firstName: 'Claire',
  lastName: 'Martin',
  dateOfBirth: '1988-04-12',
  phone: '0600000001',
  contactEmail: 'claire.martin@example.test',
  version: 2,
  createdAt: '2026-10-01T08:00:00Z',
  updatedAt: '2026-10-01T08:00:00Z',
};

beforeEach(() => {
  clearPatientMemory();
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
  clearPatientMemory();
  sessionStorage.clear();
});

function renderForm(props: { mode: 'new' | 'edit'; recordId?: string; expire?: () => void; select?: (p: string) => void }) {
  const select = props.select ?? vi.fn();
  const expire = props.expire ?? vi.fn();
  return {
    select,
    expire,
    ...render(
      <PractitionerShell session={session} context={context} contexts={[context]} select={select} signOut={vi.fn()}>
        <PatientForm mode={props.mode} scope="unit-scope" practiceId={practiceId} base={base}
          recordId={props.recordId} expire={expire} />
      </PractitionerShell>,
    ),
  };
}

describe('PatientForm', () => {
  it('validates empty fields, invalid birth date, bad contact email shape, and phone length client-side', async () => {
    const user = userEvent.setup();
    const fetch = vi.fn(() => Response.json({ headerName: 'X-CSRF-TOKEN', token: 'tok' }));
    vi.stubGlobal('fetch', fetch);

    renderForm({ mode: 'new' });
    await user.click(screen.getByRole('button', { name: 'Enregistrer l’identité' }));
    expect(screen.getByRole('alert').textContent).toContain('Nom de naissance : obligatoire');
    expect(screen.getByRole('alert').textContent).toContain('Prénom : obligatoire');

    await user.type(screen.getByLabelText(/Nom de naissance/), 'Martin');
    await user.type(screen.getByLabelText(/Prénom/), 'Claire');
    await user.type(screen.getByLabelText('Jour'), '12');
    await user.type(screen.getByLabelText('Mois'), '04');
    await user.type(screen.getByLabelText('Année'), '1988');
    await user.type(screen.getByLabelText(/^Adresse e-mail/), 'pas-un-email');
    fireEvent.change(screen.getByLabelText(/^Téléphone/), { target: { value: '1'.repeat(41) } });
    await user.click(screen.getByRole('button', { name: 'Enregistrer l’identité' }));

    const alert = screen.getByRole('alert').textContent ?? '';
    expect(alert).toContain('Adresse e-mail : format incorrect');
    expect(alert).toContain('Téléphone : trop long');
    expect(fetch).not.toHaveBeenCalled();
  });

  it('handles duplicate candidates, confirms creation, and verifies after network loss', async () => {
    const user = userEvent.setup();
    let createCalls = 0;
    const created: Patient = { ...existingPatient, id: '00000000-0000-4000-8000-000000000033' };
    const fetch = vi.fn(async (input: RequestInfo | URL) => {
      const req = input as Request;
      const pathname = new URL(req.url).pathname;
      if (pathname === '/api/v1/csrf') {
        return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'tok' });
      }
      if (pathname.endsWith('/patients') && req.method === 'POST') {
        createCalls += 1;
        if (createCalls === 1) {
          return Response.json({
            code: 'DUPLICATE_PATIENT',
            candidates: [{ id: patientId, recordId, firstName: 'Claire', lastName: 'Martin', dateOfBirth: '1988-04-12' }],
          }, { status: 409 });
        }
        if (createCalls === 2) {
          throw new Error('network drop');
        }
        return Response.json(created, { status: 201 });
      }
      throw new Error(`Unexpected ${req.method} ${pathname}`);
    });
    vi.stubGlobal('fetch', fetch);

    const { select } = renderForm({ mode: 'new' });
    await user.type(screen.getByLabelText(/Nom de naissance/), 'Martin');
    await user.type(screen.getByLabelText(/Prénom/), 'Claire');
    await user.type(screen.getByLabelText('Jour'), '12');
    await user.type(screen.getByLabelText('Mois'), '04');
    await user.type(screen.getByLabelText('Année'), '1988');
    await user.type(screen.getByLabelText(/^Adresse e-mail/), 'claire@example.test');
    await user.click(screen.getByRole('button', { name: 'Enregistrer l’identité' }));

    expect(await screen.findByText('Un dossier semblable existe dans ce cabinet', {}, { timeout: 3000 })).toBeDefined();
    await user.click(screen.getByRole('button', { name: 'Créer quand même' }));
    await waitFor(() => expect(select).toHaveBeenCalledWith(`${base}/patients`), { timeout: 3000 });
    expect(createCalls).toBe(3);
  });

  it('handles edit loading failure, retry, 412 conflict reload, and session expiry', async () => {
    const user = userEvent.setup();
    let reads = 0;
    const serverPatient: Patient = { ...existingPatient, phone: '0699999999', version: 3 };
    const fetch = vi.fn(async (input: RequestInfo | URL) => {
      const req = input as Request;
      const pathname = new URL(req.url).pathname;
      if (pathname === '/api/v1/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'tok' });
      if (pathname.endsWith(`/records/${recordId}`) && req.method === 'GET') {
        reads += 1;
        if (reads === 1) return Response.json({ code: 'UNAVAILABLE' }, { status: 503 });
        if (reads === 2) return Response.json({ patient: existingPatient });
        return Response.json({ patient: serverPatient });
      }
      if (pathname.endsWith(`/patients/${patientId}`) && req.method === 'PATCH') {
        return Response.json({ code: 'CONFLICT' }, { status: 412 });
      }
      throw new Error(`Unexpected ${req.method} ${pathname}`);
    });
    vi.stubGlobal('fetch', fetch);

    renderForm({ mode: 'edit', recordId });
    await user.click(await screen.findByRole('button', { name: 'Réessayer' }));
    const tel = await screen.findByLabelText<HTMLInputElement>(/^Téléphone/);
    expect(tel.value).toBe('0600000001');

    await user.clear(tel);
    await user.type(tel, '0611111111');
    await user.click(screen.getByRole('button', { name: 'Enregistrer l’identité' }));

    expect(await screen.findByText('Ce dossier a été modifié entre-temps')).toBeDefined();
    await user.click(screen.getByRole('button', { name: 'Recharger la version enregistrée' }));
    expect(screen.getByLabelText<HTMLInputElement>(/^Téléphone/).value).toBe('0699999999');
  });

  it('renders restricted and not-found states on edit load', async () => {
    let status = 403;
    vi.stubGlobal('fetch', vi.fn(async () => Response.json({ code: 'ERR' }, { status })));

    const first = renderForm({ mode: 'edit', recordId });
    expect(await screen.findByText('Modification non autorisée')).toBeDefined();
    first.unmount();

    status = 404;
    renderForm({ mode: 'edit', recordId });
    expect(await screen.findByRole('heading', { name: 'Élément introuvable' })).toBeDefined();
  });
});

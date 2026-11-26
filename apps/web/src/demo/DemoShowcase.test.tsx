// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ISOLATION_DATASET } from '../isolation/isolation-journey';
import { OPERATOR_EMAIL } from './demo-entry';
import { DemoShowcase } from './DemoShowcase';

const baseMeta = {
  service: 'etaye-api',
  version: '0.1.0-test',
  status: 'up',
  demoMode: true,
  demoDatasetVersion: ISOLATION_DATASET,
  demoResetGeneration: 1,
};

const scenarios = [
  {
    id: 'praticienne',
    synthetic: true,
    datasetVersion: ISOLATION_DATASET,
    available: true,
    actorName: 'Dr Nora Lefebvre',
    roleLabel: 'Praticienne',
    practiceName: 'Cabinet des Tilleuls',
    journeySummary: 'Ouvre les dossiers autorisés du cabinet.',
    limitsNotice: 'Personne fictive uniquement.',
    signInIdentifier: 'nora@example.test',
  },
  {
    id: 'administration',
    synthetic: true,
    datasetVersion: ISOLATION_DATASET,
    available: true,
    actorName: 'Mila Laurent',
    roleLabel: 'Administration',
    practiceName: 'Cabinet des Tilleuls',
    journeySummary: 'Administre le cabinet sans accès clinique.',
    limitsNotice: 'Personne fictive uniquement.',
    signInIdentifier: 'mila@example.test',
  },
  {
    id: 'patiente',
    synthetic: true,
    datasetVersion: ISOLATION_DATASET,
    available: true,
    actorName: 'Claire Martin',
    roleLabel: 'Patiente',
    practiceName: 'Cabinet des Tilleuls',
    journeySummary: 'Consulte son espace patient uniquement.',
    limitsNotice: 'Personne fictive uniquement.',
    signInIdentifier: 'claire@example.test',
  },
];

beforeEach(() => {
  sessionStorage.clear();
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
  sessionStorage.clear();
});

function stubDemoFetch(handler: (path: string, method: string) => Response) {
  const fetch = vi.fn((request: Request) => {
    const path = new URL(request.url).pathname;
    if (path === '/api/v1/csrf' && request.method === 'GET') {
      return Promise.resolve(Response.json({ headerName: 'X-CSRF-TOKEN', token: 'demo-csrf' }));
    }
    return Promise.resolve(handler(path, request.method));
  });
  vi.stubGlobal('fetch', fetch);
  return fetch;
}

describe('DemoShowcase and IsolationGuide', () => {
  it('shows unavailable/retry and empty states, then opens scenario detail and IsolationGuide', async () => {
    const user = userEvent.setup();
    let failFirst = true;
    stubDemoFetch((path) => {
      if (path === '/api/v1/meta') return Response.json(baseMeta);
      if (path === '/api/v1/demo/scenarios') {
        if (failFirst) {
          return Response.json({ code: 'DEMO_RESET_IN_PROGRESS' }, { status: 503 });
        }
        return Response.json({ items: scenarios });
      }
      if (path === '/api/v1/session') return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 });
      throw new Error(`Unexpected ${path}`);
    });

    render(<DemoShowcase />);
    expect((await screen.findByRole('alert')).textContent).toContain('Réinitialisation en cours');
    failFirst = false;
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));

    expect(await screen.findByRole('link', { name: 'Connexion opérateur' })).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' }));
    expect(await screen.findByRole('heading', { name: 'Scénario praticienne' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ouvrir la démonstration' }).getAttribute('href')).toBe('/connexion');

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(await screen.findByRole('button', { name: 'Ouvrir le parcours' })).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Ouvrir le parcours' }));
    expect(await screen.findByRole('heading', { name: 'Prouvez l’isolation' })).toBeTruthy();
    await user.click(screen.getByRole('link', { name: 'Mila' }));
    expect(sessionStorage.getItem('etaye-demo-entry')).toContain('mila@example.test');
    await user.click(screen.getByRole('button', { name: 'Revenir à la démonstration' }));
    expect(await screen.findByRole('button', { name: 'Ouvrir le parcours' })).toBeTruthy();
  });

  it('handles operator session check, reset failures, and confirmed reset refresh', async () => {
    const user = userEvent.setup();
    let generation = 1;
    let sessionStatus = 503;
    let resetCalls = 0;

    stubDemoFetch((path, method) => {
      if (path === '/api/v1/meta') return Response.json({ ...baseMeta, demoResetGeneration: generation });
      if (path === '/api/v1/demo/scenarios') return Response.json({ items: scenarios });
      if (path === '/api/v1/session') {
        if (sessionStatus === 503) return Response.json({ code: 'DOWN' }, { status: 503 });
        return Response.json({
          account: { id: 'op-1', displayName: 'Opérateur', email: OPERATOR_EMAIL, state: 'ACTIVE', locale: 'fr-FR', version: 0, createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z' },
          membershipIds: [],
          portalLinkIds: [],
          contexts: [],
          expiresAt: '2026-11-26T18:00:00Z',
          returnTo: '/compte',
        });
      }
      if (path === '/api/v1/demo/reset' && method === 'POST') {
        resetCalls += 1;
        if (resetCalls === 1) return Response.json({ code: 'DEMO_RESET_IN_PROGRESS' }, { status: 503 });
        generation = 2;
        return Response.json({ accepted: true, targetGeneration: 2 }, { status: 202 });
      }
      throw new Error(`Unexpected ${method} ${path}`);
    });

    render(<DemoShowcase />);
    expect((await screen.findByRole('alert')).textContent).toContain('Session opérateur indisponible');
    sessionStatus = 200;
    await user.click(screen.getByRole('button', { name: 'Vérifier la session' }));

    await user.click(await screen.findByRole('button', { name: 'Réinitialiser la démonstration' }));
    await user.click(screen.getByRole('button', { name: 'Confirmer la réinitialisation' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Réinitialisation en cours');

    await user.click(screen.getByRole('button', { name: 'Confirmer la réinitialisation' }));
    expect(await screen.findByText('Démonstration réinitialisée')).toBeTruthy();
  });

  it('shows empty state and rejects mismatched dataset versions cleanly', async () => {
    let items: typeof scenarios = [];
    stubDemoFetch((path) => {
      if (path === '/api/v1/meta') return Response.json({ ...baseMeta, demoDatasetVersion: 'other-version' });
      if (path === '/api/v1/demo/scenarios') return Response.json({ items });
      if (path === '/api/v1/session') return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 });
      throw new Error(`Unexpected ${path}`);
    });

    const { rerender } = render(<DemoShowcase />);
    expect(await screen.findByText('Aucun scénario disponible.')).toBeTruthy();

    items = scenarios;
    rerender(<DemoShowcase />);
    // Reload triggers mismatched version comparison.
    await userEvent.setup().click(screen.getByRole('link', { name: 'Connexion opérateur' }));
    cleanup();
    render(<DemoShowcase />);
    expect((await screen.findByRole('alert')).textContent).toContain('Démonstration indisponible');
  });
});

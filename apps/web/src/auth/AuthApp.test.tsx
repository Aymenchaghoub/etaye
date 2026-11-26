// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AccessContext, Session } from '../api/generated/types.gen';
import { AuthApp, usesAuth } from './AuthApp';

const practiceId = '00000000-0000-4000-8000-000000000010';
const secondPracticeId = '00000000-0000-4000-8000-000000000020';

function makeSession(
  accountId = 'acc-1',
  contexts: AccessContext[] = [
    { kind: 'MEMBERSHIP', grantId: practiceId, practiceId, practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL', timezone: 'Europe/Paris' },
    { kind: 'MEMBERSHIP', grantId: secondPracticeId, practiceId: secondPracticeId, practiceName: 'Cabinet du Canal', displayRole: 'CLINICAL', timezone: 'Europe/Paris' },
  ],
): Session {
  return {
    account: {
      id: accountId,
      displayName: 'Dr Nora Lefebvre',
      email: 'nora@example.test',
      state: 'ACTIVE',
      locale: 'fr-FR',
      version: 0,
      createdAt: '2026-11-26T10:00:00Z',
      updatedAt: '2026-11-26T10:00:00Z',
    },
    membershipIds: contexts.map((c) => c.practiceId),
    portalLinkIds: [],
    contexts,
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    returnTo: '/compte',
  };
}

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
  window.history.replaceState(null, '', '/');
  sessionStorage.clear();
});

describe('AuthApp routes and session gate', () => {
  it('recognizes auth routes and renders recovery routes', () => {
    expect(usesAuth('/connexion')).toBe(true);
    expect(usesAuth('/compte/pratiques/1')).toBe(true);
    expect(usesAuth('/demonstration')).toBe(false);

    window.history.replaceState(null, '', '/recuperation?espace=patient');
    render(<AuthApp />);
    expect(screen.getByRole('heading', { name: 'Récupérer votre accès' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Revenir à la connexion' }).getAttribute('href')).toBe('/connexion/patient');
  });

  it('shows unavailable retry, then falls back to SignIn when unauthenticated', async () => {
    const user = userEvent.setup();
    let calls = 0;
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === '/api/v1/session') {
        calls += 1;
        return Promise.resolve(calls === 1
          ? Response.json({ code: 'DOWN' }, { status: 503 })
          : Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 }));
      }
      throw new Error(`Unexpected ${path}`);
    }));

    window.history.replaceState(null, '', `/compte/pratiques/${practiceId}/patients`);
    render(<AuthApp />);
    expect((await screen.findByRole('alert')).textContent).toContain('Impossible de charger vos accès');
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByRole('heading', { name: 'Se connecter' })).toBeTruthy();
  });

  it('handles grant-changed on visibility recheck and signOut failures with retry', async () => {
    const user = userEvent.setup();
    const s1 = makeSession('acc-1');
    let sessionResp: Response = Response.json(s1);
    let signOutFail = true;

    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === '/api/v1/csrf') return Promise.resolve(Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf-1' }));
      if (path === '/api/v1/session' && request.method === 'DELETE') {
        if (signOutFail) return Promise.resolve(Response.json({ code: 'DOWN' }, { status: 503 }));
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (path === '/api/v1/session') return Promise.resolve(sessionResp);
      throw new Error(`Unexpected ${request.method} ${path}`);
    }));

    window.history.replaceState(null, '', '/compte');
    render(<AuthApp />);
    expect(await screen.findByRole('button', { name: 'Se déconnecter' })).toBeTruthy();

    sessionResp = Response.json({ code: 'FORBIDDEN' }, { status: 403 });
    window.dispatchEvent(new Event('pageshow'));
    expect(await screen.findByRole('heading', { name: 'Accès modifié' })).toBeTruthy();

    sessionResp = Response.json(s1);
    await user.click(screen.getByRole('button', { name: 'Continuer' }));
    expect(await screen.findByRole('button', { name: 'Se déconnecter' })).toBeTruthy();

    // Account switch triggers account-changed notice.
    sessionResp = Response.json(makeSession('acc-2'));
    window.dispatchEvent(new Event('pageshow'));
    expect(await screen.findByRole('heading', { name: 'Autre compte connecté' })).toBeTruthy();

    // Sign out failure with retry -> leaves to /connexion.
    sessionResp = Response.json(s1);
    await user.click(screen.getByRole('button', { name: 'Continuer' }));
    await user.click(await screen.findByRole('button', { name: 'Se déconnecter' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Impossible de charger vos accès');
    signOutFail = false;
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByRole('heading', { name: 'Se connecter' })).toBeTruthy();
  });

  it('shows standalone expired notice on /compte when active session returns 401 and returns to SignIn on reconnect or leave', async () => {
    const user = userEvent.setup();
    const expiredSession = makeSession('acc-1');
    let phase: 'active' | 'expired' = 'active';
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === '/api/v1/session' && request.method === 'GET') {
        return Promise.resolve(phase === 'expired'
          ? Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
          : Response.json(expiredSession));
      }
      throw new Error(`Unexpected ${request.method} ${path}`);
    }));

    window.history.replaceState(null, '', '/compte');
    render(<AuthApp />);
    expect(await screen.findByRole('button', { name: 'Se déconnecter' })).toBeTruthy();

    phase = 'expired';
    window.dispatchEvent(new Event('pageshow'));
    expect(await screen.findByRole('heading', { name: 'Votre session a expiré' })).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Quitter la session' }));
    expect(await screen.findByRole('heading', { name: 'Se connecter' })).toBeTruthy();
  });

  it('redirects directly from /compte when a session holds a single context', async () => {
    const singleContext = makeSession('acc-1', [
      { kind: 'MEMBERSHIP', grantId: practiceId, practiceId, practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL', timezone: 'Europe/Paris' },
    ]);
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === '/api/v1/session') return Promise.resolve(Response.json(singleContext));
      if (path.includes('/patients')) return Promise.resolve(Response.json({ items: [], nextCursor: null }));
      throw new Error(`Unexpected ${request.method} ${path}`);
    }));

    window.history.replaceState(null, '', '/compte');
    render(<AuthApp />);
    expect(await screen.findByText('Cabinet des Tilleuls')).toBeTruthy();
    expect(window.location.pathname).toBe(`/compte/pratiques/${practiceId}`);
  });
});

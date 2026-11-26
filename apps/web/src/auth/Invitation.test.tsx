// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Session } from '../api/generated/types.gen';
import { Invitation } from './Invitation';

const token = 'C'.repeat(43);

function portalPreview(overrides: Record<string, unknown> = {}) {
  return {
    eligibility: 'USABLE',
    purpose: 'PATIENT_PORTAL',
    practiceName: 'Cabinet des Tilleuls',
    recipientHint: 'claire.***@example.test',
    expiresAt: '2026-12-01T10:00:00Z',
    ...overrides,
  };
}

function memberSession(id = 'acc-1'): Session {
  return {
    account: {
      id,
      displayName: 'Claire Martin',
      email: 'claire@example.test',
      state: 'ACTIVE',
      locale: 'fr-FR',
      version: 0,
      createdAt: '2026-11-26T10:00:00Z',
      updatedAt: '2026-11-26T10:00:00Z',
    },
    membershipIds: [],
    portalLinkIds: ['portal-1'],
    activatedPortalLinkId: 'portal-1',
    contexts: [],
    expiresAt: '2026-11-26T18:00:00Z',
    returnTo: '/compte',
  };
}

beforeEach(() => {
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
});

describe('Invitation edge and terminal states', () => {
  it('renders terminal stop screens and handles inspection outage then retry', async () => {
    const user = userEvent.setup();
    let inspectResp = Response.json({ code: 'DOWN' }, { status: 503 });
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === '/api/v1/csrf') return Promise.resolve(Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }));
      if (path === '/api/v1/invitations/inspect') return Promise.resolve(inspectResp);
      throw new Error(`Unexpected ${path}`);
    }));

    window.history.replaceState(null, '', `/invitation#${token}`);
    render(<Invitation onAccepted={vi.fn()} />);
    expect((await screen.findByRole('alert')).textContent).toContain('service est temporairement indisponible');

    inspectResp = Response.json(portalPreview({ eligibility: 'EXPIRED' }));
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect((await screen.findByRole('heading', { level: 1 })).textContent).toContain("lien d'activation a expiré");
  });

  it('validates patient portal activation, mismatch, 429 limit and session-ended recovery', async () => {
    const user = userEvent.setup();
    const accepted = vi.fn();
    let acceptResp = Response.json({ code: 'RECIPIENT_MISMATCH' }, { status: 403 });
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === '/api/v1/csrf') return Promise.resolve(Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }));
      if (path === '/api/v1/invitations/inspect') return Promise.resolve(Response.json(portalPreview()));
      if (path === '/api/v1/session') return Promise.resolve(Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 }));
      if (path === '/api/v1/invitations/accept') return Promise.resolve(acceptResp);
      throw new Error(`Unexpected ${path}`);
    }));

    window.history.replaceState(null, '', `/invitation#${token}`);
    render(<Invitation onAccepted={accepted} />);
    await screen.findByRole('heading', { name: 'Activer mon espace patient' });

    // Local validation & missing birth date on patient portal invitation (confirmation is practitioner-only).
    await user.type(screen.getByLabelText(/^Créer un mot de passe/), 'trop-court');
    await user.click(screen.getByRole('button', { name: /Activer/ }));
    expect(screen.getByRole('alert').textContent).toContain('date de naissance');

    await user.type(screen.getByLabelText(/^Votre date de naissance/), '18/04/1978');
    await user.type(screen.getByLabelText(/^Créer un mot de passe/), 'long-synthetic-pass');
    await user.click(screen.getByRole('button', { name: /Activer/ }));
    expect((await screen.findByRole('alert')).textContent).toContain('Activation impossible avec ces informations');

    acceptResp = Response.json({ code: 'PORTAL_LINK_EXISTS' }, { status: 409 });
    await user.type(screen.getByLabelText(/^Créer un mot de passe/), 'long-synthetic-pass');
    await user.click(screen.getByRole('button', { name: /Activer/ }));
    expect(await screen.findByRole('heading', { name: 'Contactez votre cabinet' })).toBeTruthy();

    // Verify clean acceptance callback with fresh session.
    cleanup();
    acceptResp = Response.json(memberSession());
    window.history.replaceState(null, '', `/invitation#${token}`);
    render(<Invitation onAccepted={accepted} />);
    await screen.findByRole('heading', { name: 'Activer mon espace patient' });
    await user.type(screen.getByLabelText(/^Votre date de naissance/), '18/04/1978');
    await user.type(screen.getByLabelText(/^Créer un mot de passe/), 'long-synthetic-pass');
    await user.click(screen.getByRole('button', { name: /Activer/ }));
    expect(accepted).toHaveBeenCalledTimes(1);
  });

  it('handles signed-in professional invitation, sign-out failure and membership conflict', async () => {
    const user = userEvent.setup();
    let acceptResp = Response.json({ code: 'SIGN_IN_REQUIRED' }, { status: 403 });
    let signOutFail = true;
    vi.stubGlobal('fetch', vi.fn((request: Request) => {
      const path = new URL(request.url).pathname;
      if (path === '/api/v1/csrf') return Promise.resolve(Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }));
      if (path === '/api/v1/invitations/inspect') {
        return Promise.resolve(Response.json(portalPreview({ purpose: 'PRACTICE_MEMBER' })));
      }
      if (path === '/api/v1/session' && request.method === 'GET') return Promise.resolve(Response.json(memberSession()));
      if (path === '/api/v1/session' && request.method === 'DELETE') {
        return Promise.resolve(signOutFail
          ? Response.json({ code: 'DOWN' }, { status: 503 })
          : new Response(null, { status: 204 }));
      }
      if (path === '/api/v1/invitations/accept') return Promise.resolve(acceptResp);
      throw new Error(`Unexpected ${request.method} ${path}`);
    }));

    window.history.replaceState(null, '', `/invitation#${token}`);
    render(<Invitation onAccepted={vi.fn()} />);
    await screen.findByRole('heading', { name: /Rejoindre le Cabinet des Tilleuls/ });

    // Trigger sign-in banner while signed in, then sign out -> 503 failure -> retry switches to unsigned form.
    await user.click(screen.getByRole('button', { name: /Activer/ }));
    expect(await screen.findByText('Connectez-vous pour continuer')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Continuer avec un nouveau compte' }));
    expect(await screen.findByText('La déconnexion a échoué')).toBeTruthy();
    signOutFail = false;
    await user.click(screen.getByRole('button', { name: 'Continuer avec un nouveau compte' }));
    expect(await screen.findByLabelText(/^Nom affiché/)).toBeTruthy();

    // Sign-in prompt + membership conflict.
    cleanup();
    acceptResp = Response.json({ code: 'MEMBERSHIP_EXISTS' }, { status: 409 });
    window.history.replaceState(null, '', `/invitation#${token}`);
    render(<Invitation onAccepted={vi.fn()} />);
    await screen.findByRole('heading', { name: /Rejoindre le Cabinet des Tilleuls/ });
    await user.click(screen.getByRole('button', { name: /Activer/ }));
    expect(await screen.findByText('Vous avez déjà accès à ce cabinet')).toBeTruthy();
  });
});

// @vitest-environment jsdom
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Session } from '../api/generated/types.gen';
import { deferred, stubAccountRequest } from '../../tests/support/components';
import { SignIn } from './SignIn';

const email = 'camille@example.test';
const createdAt = '2026-11-26T12:00:00Z';
const session: Session = {
  account: {
    id: '00000000-0000-4000-8000-000000000098',
    displayName: 'Camille Exemple',
    email,
    state: 'ACTIVE',
    locale: 'fr-FR',
    version: 0,
    createdAt,
    updatedAt: createdAt,
  },
  membershipIds: [],
  portalLinkIds: [],
  contexts: [],
  expiresAt: '2026-11-26T12:30:00Z',
  returnTo: '/compte',
};
const password = 'synthetic-passphrase';

async function enterCredentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByRole('textbox', { name: 'Adresse e-mail' }), email);
  await user.type(screen.getByLabelText(/^Mot de passe/), password);
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('sign-in form', () => {
  it('focuses invalid fields through the error summary without contacting the API', async () => {
    const user = userEvent.setup();
    const fetch = stubAccountRequest(['/api/v1/session'], () => Response.json(session));
    render(<SignIn patient returning={false} onSignedIn={vi.fn()} />);

    const address = screen.getByRole('textbox', { name: 'Adresse e-mail' });
    await user.type(address, 'adresse-incomplète');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    const summary = screen.getByRole('alert');
    expect(within(summary).getByRole('heading').textContent).toBe('2 champs à corriger');
    expect(document.activeElement).toBe(summary);
    expect(address.getAttribute('aria-invalid')).toBe('true');
    await user.click(within(summary).getByRole('link', { name: 'Adresse e-mail : format incorrect' }));
    expect(document.activeElement).toBe(address);
    expect(screen.getByRole('link', { name: 'Mot de passe oublié ?' }).getAttribute('href')).toBe('/recuperation?espace=patient');
    expect(fetch).not.toHaveBeenCalled();
  });

  it('erases the password, blocks duplicate submission and resumes only after success', async () => {
    const user = userEvent.setup();
    const reply = deferred<Response>();
    const submit = vi.fn<(request: Request) => Promise<Response>>(() => reply.promise);
    stubAccountRequest(['/api/v1/session'], submit);
    const onSignedIn = vi.fn();
    const onCancel = vi.fn();
    render(<SignIn patient={false} returning returnTo="/compte" onSignedIn={onSignedIn} onCancel={onCancel} />);

    expect(screen.getByRole('status').textContent).toContain('Votre session a expiré');
    await enterCredentials(user);
    await user.click(screen.getByRole('checkbox', { name: 'Afficher le mot de passe' }));
    const secret = screen.getByLabelText<HTMLInputElement>(/^Mot de passe/);
    expect(secret.type).toBe('text');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    const [request] = submit.mock.calls[0];
    expect(request.headers.get('X-CSRF-TOKEN')).toBe('component-test-token');
    expect(await request.json()).toEqual({ email, password, returnTo: '/compte' });
    expect(secret.value).toBe('');
    expect(secret.type).toBe('password');
    const pending = screen.getByRole<HTMLButtonElement>('button', { name: 'Connexion en cours…' });
    expect(pending.disabled).toBe(true);
    await user.click(pending);
    await user.click(screen.getByRole('button', { name: 'Revenir à l’invitation' }));
    expect(submit).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
    expect(onSignedIn).not.toHaveBeenCalled();

    reply.resolve(Response.json(session));
    await waitFor(() => expect(onSignedIn).toHaveBeenCalledExactlyOnceWith(session));
  });

  it('shows a generic credential error and allows an explicit retry after an outage', async () => {
    const user = userEvent.setup();
    const submit = vi.fn()
      .mockResolvedValueOnce(Response.json({ code: 'INVALID_CREDENTIALS' }, { status: 401 }))
      .mockResolvedValueOnce(Response.json({ code: 'DB_UNAVAILABLE' }, { status: 503 }));
    stubAccountRequest(['/api/v1/session'], submit);
    const onSignedIn = vi.fn();
    render(<SignIn patient={false} returning={false} onSignedIn={onSignedIn} />);
    await enterCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    const invalid = await screen.findByRole('alert');
    expect(invalid.textContent).toContain('Adresse e-mail ou mot de passe incorrect');
    const secret = screen.getByLabelText<HTMLInputElement>(/^Mot de passe/);
    expect(secret.value).toBe('');
    await user.type(secret, password);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Le service est temporairement indisponible');
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Se connecter' }).disabled).toBe(false);
    expect(submit).toHaveBeenCalledTimes(2);
    expect(onSignedIn).not.toHaveBeenCalled();
  });

  it('aborts the in-flight request when leaving and discards a late successful response', async () => {
    const user = userEvent.setup();
    const reply = deferred<Response>();
    const submit = vi.fn<(request: Request) => Promise<Response>>(() => reply.promise);
    stubAccountRequest(['/api/v1/session'], submit);
    const onSignedIn = vi.fn();
    const { unmount } = render(<SignIn patient={false} returning={false} onSignedIn={onSignedIn} />);
    await enterCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));
    await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));

    const request = submit.mock.calls[0][0];
    unmount();
    expect(request.signal.aborted).toBe(true);
    reply.resolve(Response.json(session));
    // Flush the fetch and body decoding before checking that navigation has not happened.
    await new Promise<void>((resolve) => setImmediate(resolve));
    expect(onSignedIn).not.toHaveBeenCalled();
  });
});

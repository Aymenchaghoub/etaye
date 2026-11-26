// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import { stubAccountRequest } from '../../tests/support/components';
import { RecoveryRequest, ResetPassword } from './Recovery';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState(null, '', '/');
});

it('retains the address and receipt after an outage, then confirms receipt without confirming the account', async () => {
  const user = userEvent.setup();
  const submit = vi.fn<(request: Request) => Response>()
    .mockReturnValueOnce(Response.json({ code: 'DB_UNAVAILABLE' }, { status: 503 }))
    .mockReturnValueOnce(new Response(null, { status: 204 }));
  stubAccountRequest(['/api/v1/access-recovery'], submit);
  render(<RecoveryRequest patient />);

  const address = screen.getByRole<HTMLInputElement>('textbox', { name: 'Adresse e-mail' });
  await user.type(address, 'fiction@example.test');
  await user.click(screen.getByRole('button', { name: 'Recevoir un lien' }));
  expect((await screen.findByRole('alert')).textContent).toContain('Votre adresse est conservée');
  expect(address.value).toBe('fiction@example.test');
  await user.click(screen.getByRole('button', { name: 'Réessayer' }));

  const confirmation = await screen.findByRole('heading', { name: 'Demande de récupération reçue' });
  expect(document.activeElement).toBe(confirmation);
  expect(screen.getByText(/Si un compte correspond/).textContent).toContain('fiction@example.test');
  expect(screen.getByRole('link', { name: 'Revenir à la connexion' }).getAttribute('href')).toBe('/connexion/patient');
  expect(submit).toHaveBeenCalledTimes(2);
  const [first, second] = submit.mock.calls.map(([request]) => request);
  const receipt = first.headers.get('Idempotency-Key');
  expect(receipt).toMatch(/^[0-9a-f-]{36}$/);
  expect(second.headers.get('Idempotency-Key')).toBe(receipt);
  expect(await first.json()).toEqual({ email: 'fiction@example.test' });
  expect(await second.json()).toEqual({ email: 'fiction@example.test' });
});

it('keeps a mistyped confirmation local and discards the recovery fragment after completion', async () => {
  const user = userEvent.setup();
  const submit = vi.fn<(request: Request) => Response>(() => new Response(null, { status: 204 }));
  stubAccountRequest(['/api/v1/access-recovery/complete'], submit);
  const token = 'A'.repeat(43);
  window.history.replaceState(null, '', `/recuperation/terminer#${token}`);
  render(<ResetPassword />);

  const first = screen.getByLabelText<HTMLInputElement>(/^Nouveau mot de passe/);
  const confirmation = screen.getByLabelText<HTMLInputElement>(/^Confirmer le nouveau mot de passe/);
  const save = screen.getByRole('button', { name: 'Enregistrer le mot de passe' });
  await user.type(first, 'synthetic-passphrase');
  await user.type(confirmation, 'different-passphrase');
  await user.click(save);
  expect(screen.getByRole('alert').textContent).toContain('Confirmer le nouveau mot de passe');
  expect(first.value).toBe('synthetic-passphrase');
  expect(confirmation.value).toBe('');
  expect(submit).not.toHaveBeenCalled();

  await user.type(confirmation, 'synthetic-passphrase');
  await user.click(save);
  const completed = await screen.findByRole('heading', { name: 'Mot de passe modifié' });
  expect(document.activeElement).toBe(completed);
  expect(window.location.hash).toBe('');
  expect(submit).toHaveBeenCalledTimes(1);
  const [request] = submit.mock.calls[0];
  expect(new URL(request.url).pathname).toBe('/api/v1/access-recovery/complete');
  expect(await request.json()).toEqual({ token, newPassword: 'synthetic-passphrase' });
});

it('offers a new recovery request for an unusable link without sending a reset', () => {
  const submit = vi.fn(() => new Response(null, { status: 204 }));
  stubAccountRequest(['/api/v1/access-recovery/complete'], submit);
  window.history.replaceState(null, '', '/recuperation/terminer#invalide');
  render(<ResetPassword />);

  expect(document.activeElement).toBe(screen.getByRole('heading', { name: "Ce lien n'est plus valable" }));
  expect(screen.getByRole('link', { name: 'Faire une nouvelle demande' }).getAttribute('href')).toBe('/recuperation');
  expect(screen.queryByRole('button', { name: 'Enregistrer le mot de passe' })).toBeNull();
  expect(submit).not.toHaveBeenCalled();
});

it('validates email locally and enforces server rate limits on recovery requests', async () => {
  const user = userEvent.setup();
  const submit = vi.fn<() => Response>(() => Response.json(
    { code: 'RATE_LIMITED' },
    { status: 429, headers: { 'Retry-After': '60' } },
  ));
  stubAccountRequest(['/api/v1/access-recovery'], submit);
  render(<RecoveryRequest patient={false} />);

  await user.type(screen.getByRole('textbox', { name: 'Adresse e-mail' }), 'pas-une-adresse');
  await user.click(screen.getByRole('button', { name: 'Recevoir un lien' }));
  expect(submit).not.toHaveBeenCalled();
  expect(screen.getByRole('alert').textContent).toContain('Adresse e-mail');

  const input = screen.getByRole<HTMLInputElement>('textbox', { name: 'Adresse e-mail' });
  await user.clear(input);
  await user.type(input, 'nora@example.test');
  await user.click(screen.getByRole('button', { name: 'Recevoir un lien' }));
  expect((await screen.findByRole('alert')).textContent).toContain('Trop de demandes');
  expect(screen.getByRole('button', { name: 'Recevoir un lien' }).hasAttribute('disabled')).toBe(true);
});

it('rejects too-short, too-long, server-rejected and outage password reset responses safely', async () => {
  const user = userEvent.setup();
  const submit = vi.fn<() => Response>()
    .mockReturnValueOnce(Response.json({ code: 'PASSWORD_TOO_SHORT' }, { status: 400 }))
    .mockReturnValueOnce(Response.json({ code: 'RATE_LIMITED' }, { status: 429, headers: { 'Retry-After': '30' } }))
    .mockReturnValueOnce(Response.json({ code: 'DB_UNAVAILABLE' }, { status: 503 }))
    .mockReturnValueOnce(Response.json({ code: 'INVALID_RECOVERY_LINK' }, { status: 400 }));
  stubAccountRequest(['/api/v1/access-recovery/complete'], submit);
  const token = 'B'.repeat(43);
  window.history.replaceState(null, '', `/recuperation/terminer#${token}`);
  render(<ResetPassword />);

  const first = screen.getByLabelText<HTMLInputElement>(/^Nouveau mot de passe/);
  const confirmation = screen.getByLabelText<HTMLInputElement>(/^Confirmer le nouveau mot de passe/);
  const save = screen.getByRole('button', { name: 'Enregistrer le mot de passe' });

  await user.type(first, 'trop-court');
  await user.type(confirmation, 'trop-court');
  await user.click(save);
  expect(screen.getByRole('alert').textContent).toContain('12 caractères');
  expect(first.value).toBe('');

  await user.type(first, 'a'.repeat(1025));
  await user.click(save);
  expect(screen.getByRole('alert').textContent).toContain('1 024 caractères');

  await user.type(first, 'synthetic-passphrase');
  await user.type(confirmation, 'synthetic-passphrase');
  await user.click(save);
  expect((await screen.findByRole('alert')).textContent).toContain('12 caractères');

  await user.type(first, 'synthetic-passphrase');
  await user.type(confirmation, 'synthetic-passphrase');
  await user.click(save);
  expect((await screen.findByRole('alert')).textContent).toContain('Trop de demandes');

  // Re-render fresh instance for 503 and INVALID_RECOVERY_LINK without timer lockout.
  cleanup();
  window.history.replaceState(null, '', `/recuperation/terminer#${token}`);
  render(<ResetPassword />);
  await user.type(screen.getByLabelText(/^Nouveau mot de passe/), 'synthetic-passphrase');
  await user.type(screen.getByLabelText(/^Confirmer le nouveau mot de passe/), 'synthetic-passphrase');
  await user.click(screen.getByRole('button', { name: 'Enregistrer le mot de passe' }));
  expect((await screen.findByRole('alert')).textContent).toContain("Le changement n'a pas pu être confirmé");

  await user.type(screen.getByLabelText(/^Nouveau mot de passe/), 'synthetic-passphrase');
  await user.type(screen.getByLabelText(/^Confirmer le nouveau mot de passe/), 'synthetic-passphrase');
  await user.click(screen.getByRole('button', { name: 'Enregistrer le mot de passe' }));
  expect(await screen.findByRole('heading', { name: "Ce lien n'est plus valable" })).toBeTruthy();
  expect(window.location.hash).toBe('');
});

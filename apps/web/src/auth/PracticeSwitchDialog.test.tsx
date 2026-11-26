// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { deferred } from '../../tests/support/components';
import { PracticeSwitchDialog } from './PracticeSwitchDialog';

function openDialog(saveAndVerify: ComponentProps<typeof PracticeSwitchDialog>['saveAndVerify']) {
  const callbacks = {
    onStay: vi.fn(), onDiscard: vi.fn(), onChooseAnother: vi.fn(), onEntered: vi.fn(), onSourceUnavailable: vi.fn(),
  };
  const view = render(<PracticeSwitchDialog destination="Cabinet Exemple" {...callbacks} saveAndVerify={saveAndVerify} />);
  return { ...view, ...callbacks };
}

afterEach(cleanup);

it('focuses the named dialog and dismisses with Escape without discarding work', async () => {
  const user = userEvent.setup();
  const save = vi.fn<Parameters<typeof openDialog>[0]>();
  const { onStay, onDiscard } = openDialog(save);

  const dialog = screen.getByRole('dialog', { name: 'Modifications non enregistrées' });
  expect(document.activeElement).toBe(within(dialog).getByRole('heading'));
  expect(within(dialog).getByText('Cabinet Exemple')).toBeDefined();
  await user.keyboard('{Escape}');
  expect(onStay).toHaveBeenCalledTimes(1);
  expect(onDiscard).not.toHaveBeenCalled();
  expect(save).not.toHaveBeenCalled();
});

it('blocks dismissal while saving and offers another context when the destination is gone', async () => {
  const user = userEvent.setup();
  const outcome = deferred<'target-unavailable'>();
  const save = vi.fn(() => outcome.promise);
  const { onStay, onDiscard, onChooseAnother, onEntered } = openDialog(save);

  await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }));
  expect(screen.getByRole('status').textContent).toBe('Enregistrement en cours…');
  expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Fermer' }).disabled).toBe(true);
  await user.keyboard('{Escape}');
  await user.click(screen.getByRole('button', { name: 'Quitter sans enregistrer' }));
  expect(onDiscard).not.toHaveBeenCalled();
  expect(onStay).not.toHaveBeenCalled();

  outcome.resolve('target-unavailable');
  const alternate = await screen.findByRole('button', { name: 'Choisir un autre contexte' });
  expect(screen.getByRole('status').textContent).toContain('Modifications enregistrées');
  expect(screen.queryByRole('button', { name: 'Enregistrer et continuer' })).toBeNull();
  await user.click(alternate);
  expect(onChooseAnother).toHaveBeenCalledTimes(1);
  expect(onEntered).not.toHaveBeenCalled();
  expect(save).toHaveBeenCalledTimes(1);
});

it('stays on the current context after an uncertain save and prevents a second attempt', async () => {
  const user = userEvent.setup();
  const save = vi.fn<Parameters<typeof openDialog>[0]>().mockRejectedValue(new Error('Connection lost'));
  const { onStay, onEntered } = openDialog(save);
  await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }));

  expect((await screen.findByRole('alert')).textContent).toContain('Enregistrement non confirmé');
  const submit = screen.getByRole<HTMLButtonElement>('button', { name: 'Enregistrer et continuer' });
  expect(submit.disabled).toBe(true);
  await user.click(submit);
  await user.click(screen.getByRole('button', { name: 'Rester sur cette page' }));
  expect(save).toHaveBeenCalledTimes(1);
  expect(onStay).toHaveBeenCalledTimes(1);
  expect(onEntered).not.toHaveBeenCalled();
});

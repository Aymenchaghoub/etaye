// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AccessContext, Session } from '../api/generated/types.gen';
import { PractitionerShell, useShell } from './PractitionerShell';

const practiceId = '00000000-0000-4000-8000-000000000010';
const secondPracticeId = '00000000-0000-4000-8000-000000000020';

const clinicalContext: AccessContext = {
  kind: 'MEMBERSHIP',
  grantId: practiceId,
  practiceId,
  practiceName: 'Cabinet des Tilleuls',
  displayRole: 'CLINICAL',
  timezone: 'Europe/Paris',
};

const secondContext: AccessContext = {
  ...clinicalContext,
  grantId: secondPracticeId,
  practiceId: secondPracticeId,
  practiceName: 'Cabinet du Canal',
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
  contexts: [clinicalContext],
  expiresAt: '2026-11-26T12:00:00Z',
  returnTo: '/compte',
};

let mediaListeners: Array<(event: MediaQueryListEvent) => void> = [];

beforeEach(() => {
  mediaListeners = [];
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: false,
    media: '(min-width: 761px)',
    addEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) => { mediaListeners.push(listener); },
    removeEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) => {
      mediaListeners = mediaListeners.filter((item) => item !== listener);
    },
  })));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function ProbeChild({ onDiscard, onApi }: { onDiscard: () => void; onApi?: (api: ReturnType<typeof useShell>) => void }) {
  const shell = useShell();
  onApi?.(shell);
  return (
    <div>
      <button type="button" onClick={() => shell.setDirty({ label: 'Création test', crumb: 'Nouveau dossier', onDiscard })}>
        Mark dirty
      </button>
      <button type="button" onClick={() => shell.completeSave('/fallback')}>
        Complete save
      </button>
      <button type="button" onClick={() => shell.navigate('/target')}>
        Go target
      </button>
    </div>
  );
}

describe('PractitionerShell', () => {
  it('throws when useShell is called outside PractitionerShell', () => {
    expect(() => render(<ProbeChild onDiscard={vi.fn()} />)).toThrow('useShell must be used inside PractitionerShell');
  });

  it('guards unsaved work, dismisses the confirmation on Escape, and discards on confirm', async () => {
    const user = userEvent.setup();
    const select = vi.fn();
    const signOut = vi.fn();
    const onDiscard = vi.fn();

    render(
      <PractitionerShell session={session} context={clinicalContext} contexts={[clinicalContext, secondContext]}
        select={select} signOut={signOut}>
        <ProbeChild onDiscard={onDiscard} />
      </PractitionerShell>,
    );

    await user.click(screen.getByRole('button', { name: 'Mark dirty' }));
    await user.click(screen.getByRole('button', { name: 'Go target' }));
    expect(screen.getByRole('heading', { name: 'Modifications non enregistrées' })).toBeDefined();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Rester sur le formulaire' }));

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('heading', { name: 'Modifications non enregistrées' })).toBeNull();
    expect(onDiscard).not.toHaveBeenCalled();
    expect(select).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Se déconnecter' }));
    expect(screen.getByRole('heading', { name: 'Modifications non enregistrées' })).toBeDefined();
    await user.click(screen.getByRole('button', { name: 'Abandonner les modifications' }));
    expect(onDiscard).toHaveBeenCalledTimes(1);
    expect(signOut).toHaveBeenCalledTimes(1);
  });

  it('completes deferred navigation after finishWork and completes fallback when none is pending', async () => {
    const user = userEvent.setup();
    const select = vi.fn();
    let api!: ReturnType<typeof useShell>;
    render(
      <PractitionerShell session={session} context={clinicalContext} contexts={[clinicalContext]}
        select={select} signOut={vi.fn()}>
        <ProbeChild onDiscard={vi.fn()} onApi={(value) => { api = value; }} />
      </PractitionerShell>,
    );

    await user.click(screen.getByRole('button', { name: 'Mark dirty' }));
    await user.click(screen.getByRole('button', { name: 'Go target' }));
    fireEvent.click(screen.getByRole('button', { name: 'Rester sur le formulaire' }));
    // Re-open guard, simulate save completion clearing dirty and unblocking pending navigation
    await user.click(screen.getByRole('button', { name: 'Go target' }));
    act(() => { api.finishWork(); });
    expect(api.completeSave('/fallback')).toBe(false);
    expect(select).toHaveBeenLastCalledWith('/target');

    await user.click(screen.getByRole('button', { name: 'Complete save' }));
    expect(select).toHaveBeenLastCalledWith('/fallback');
  });

  it('handles mobile menu Escape, viewport resize, and beforeunload while dirty', async () => {
    const user = userEvent.setup();
    const select = vi.fn();
    render(
      <PractitionerShell session={session} context={clinicalContext} contexts={[clinicalContext, secondContext]}
        select={select} signOut={vi.fn()}>
        <ProbeChild onDiscard={vi.fn()} />
      </PractitionerShell>,
    );

    const toggle = screen.getByRole('button', { name: 'Ouvrir le menu' });
    await user.click(toggle);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    await user.keyboard('{Escape}');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(toggle);

    await user.click(toggle);
    for (const listener of mediaListeners) {
      listener({ matches: true } as MediaQueryListEvent);
    }
    expect(await screen.findByRole('button', { name: 'Ouvrir le menu' })).toBeDefined();

    await user.click(screen.getByRole('button', { name: 'Mark dirty' }));
    const unload = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(unload);
    expect(unload.defaultPrevented).toBe(true);
  });
});

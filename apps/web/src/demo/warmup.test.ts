// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
  vi.resetModules();
  document.body.innerHTML = '';
  document.documentElement.removeAttribute('data-theme');
  window.history.replaceState(null, '', '/');
});

describe('demo warmup entry', () => {
  it('renders readiness failure with retry and succeeds after API recovery', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    document.body.innerHTML = `
      <div id="signe"></div>
      <h1>Préparation</h1>
      <p id="etat"></p>
      <button id="reessayer" hidden></button>
    `;
    let attempt = 0;
    vi.stubGlobal('fetch', vi.fn(async () => {
      attempt += 1;
      if (attempt < 22) {
        return Response.json({ status: 'DOWN' }, { status: 503 });
      }
      return Response.json({ status: 'UP', database: 'UP' }, { status: 200 });
    }));

    const replace = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...window.location, search: '?theme=sombre', replace },
    });

    await import('./warmup');
    await vi.advanceTimersByTimeAsync(31_500);

    const retry = document.querySelector<HTMLButtonElement>('#reessayer')!;
    expect(retry.hidden).toBe(false);
    expect(document.querySelector('#etat')!.textContent).toContain('temporairement indisponible');

    retry.focus();
    retry.click();
    await vi.advanceTimersByTimeAsync(2_000);
    expect(replace).toHaveBeenCalledWith('/demonstration?theme=sombre');
  });
});

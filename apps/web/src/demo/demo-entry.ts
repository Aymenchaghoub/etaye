import type { ApiMeta, DemoScenario } from '../api/generated/types.gen';

export const SHOWCASE = '/demonstration';
export const LOCAL_DEMO = import.meta.env.VITE_ETAYE_LOCAL_DEMO === 'true';
export const OPERATOR_EMAIL = 'operateur@example.test';
const ENTRY_KEY = 'etaye-demo-entry';
const RESET_KEY = 'etaye-demo-pending-reset';

export type FixtureMarker = { version: string; generation: number };
export type ResetAttempt = { key: string; marker: FixtureMarker };
type DemoEntry = FixtureMarker & { actor?: string; email?: string };

export function fixtureMarker(meta: ApiMeta | undefined): FixtureMarker | undefined {
  const version = meta?.demoDatasetVersion;
  const generation = meta?.demoResetGeneration;
  if (typeof version === 'string' && typeof generation === 'number' && Number.isSafeInteger(generation) && generation > 0) {
    return { version, generation };
  }
}

export function demoEntry(): DemoEntry | undefined {
  try {
    const stored = sessionStorage.getItem(ENTRY_KEY);
    if (!stored) return;
    const value = JSON.parse(stored) as DemoEntry;
    if (typeof value?.version === 'string' && Number.isSafeInteger(value.generation)) return value;
  } catch {
    // A browser can disable session storage. No session or privilege depends on this marker.
  }
}

export function rememberDemo(marker: FixtureMarker, scenario?: DemoScenario) {
  const selection: DemoEntry = { ...marker, actor: scenario?.actorName, email: scenario?.signInIdentifier };
  try {
    sessionStorage.setItem(ENTRY_KEY, JSON.stringify(selection));
  } catch {
    // Sign-in remains available when browser storage is unavailable.
  }
}

export function clearDemoEntry() {
  try {
    sessionStorage.removeItem(ENTRY_KEY);
  } catch {
    // Storage can be unavailable throughout the tab's lifetime.
  }
}

export function fixtureChanged(previous: FixtureMarker, current: FixtureMarker) {
  return previous.generation !== current.generation || previous.version !== current.version;
}

export function applyDemoTheme() {
  const media = window.matchMedia('(prefers-color-scheme: dark)');
  const selected = new URLSearchParams(window.location.search).get('theme');
  const updateTheme = () => {
    document.documentElement.dataset.theme = selected === 'sombre' || (selected !== 'clair' && media.matches)
      ? 'sombre' : 'clair';
  };
  updateTheme();
  media.addEventListener('change', updateTheme);
  return () => media.removeEventListener('change', updateTheme);
}

export function pendingReset(): ResetAttempt | undefined {
  try {
    const stored = sessionStorage.getItem(RESET_KEY);
    if (!stored) return;
    const value = JSON.parse(stored) as ResetAttempt;
    if (typeof value?.key === 'string' && typeof value.marker?.version === 'string'
      && Number.isSafeInteger(value.marker.generation)) return value;
  } catch {
    // The reset remains reconcilable in memory while this screen stays active.
  }
}

export function storeReset(attempt?: ResetAttempt) {
  try {
    if (attempt) sessionStorage.setItem(RESET_KEY, JSON.stringify(attempt));
    else sessionStorage.removeItem(RESET_KEY);
  } catch {
    // Storage failures do not affect the API or the live fixture metadata.
  }
}

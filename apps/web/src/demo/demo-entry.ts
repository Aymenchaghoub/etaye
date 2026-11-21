import type { ApiMeta, DemoScenario } from '../api/generated/types.gen';

export const SHOWCASE = '/demonstration';
export const OPERATOR_EMAIL = 'operateur@example.test';
const ENTRY_KEY = 'etaye-demo-entry';

export type FixtureMarker = { version: string; generation: number };
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

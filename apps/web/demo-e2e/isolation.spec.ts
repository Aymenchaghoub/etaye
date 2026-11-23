import { readFileSync } from 'node:fs';
import { expect, test, type Page, type Response } from '@playwright/test';
import { PRACTICE_A, RECORD_A, RECORD_B } from '../src/isolation/isolation-journey';

const password = 'Etaye-Jeu-Fictif-91!';
const manifest = JSON.parse(readFileSync(
  new URL('../../api/src/test/resources/isolation/matrix-cases.json', import.meta.url), 'utf8',
)) as { cases: { id: string; expected: string; label: string }[] };
// md5('etaye-design-v1/PRA-B'): the second fictional practice, used only by the positive control below.
const PRACTICE_B = '65696517-3eff-da90-ae10-0bd4d7616a64';
const generic = { type: 'about:blank', title: 'Not Found', status: 404, code: 'NOT_FOUND', detail: 'Resource not found' };

const recordUrl = (record: string) => `/api/v1/practices/${PRACTICE_A}/records/${record}`;

async function signIn(page: Page) {
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

/** Runs the action and returns the actual HTTP response of the record read it triggers. */
async function readAfter(page: Page, record: string, action: () => Promise<void>): Promise<Response> {
  const pending = page.waitForResponse((response) =>
    response.url().includes(recordUrl(record)) && response.request().method() === 'GET');
  await action();
  return pending;
}

async function withoutCorrelation(response: { json(): Promise<any> }) {
  const { correlationId, ...problem } = await response.json();
  expect(correlationId).toEqual(expect.any(String));
  return problem;
}

async function startJourney(page: Page) {
  await page.goto('/demonstration');
  await page.getByRole('button', { name: 'Ouvrir le parcours' }).click();
  await expect(page.getByRole('heading', { name: 'Prouvez l’isolation', level: 1 })).toBeFocused();
  await page.getByRole('link', { name: 'Commencer avec Nora' }).click();
}

test('replays the cross-practice, administrator and patient refusals on the real stack', async ({ page, request }) => {
  const replay: { case: string; expected: string; observed: number }[] = [];
  const record = async (response: Response, expectedCase: string) => {
    await expect(page.locator('[data-isolation-case]')).toHaveAttribute('data-isolation-case', expectedCase);
    replay.push({ case: expectedCase, expected: manifest.cases.find(({ id }) => id === expectedCase)!.expected, observed: response.status() });
  };

  // Positive control: the record Nora is refused exists, and the practitioner of its own practice reads it.
  const csrf = await (await request.get('/api/v1/csrf')).json();
  const signedIn = await request.post('/api/v1/session', {
    headers: { 'X-CSRF-TOKEN': csrf.token }, data: { email: 'elodie.morel@example.test', password },
  });
  expect(signedIn.status()).toBe(200);
  const control = await request.get(`/api/v1/practices/${PRACTICE_B}/records/${RECORD_B}`);
  expect(control.status()).toBe(200);
  expect((await control.json()).patient.lastName).toBe('Petit');

  // Nora reads the authorized record, then the record outside her practice.
  await startJourney(page);
  await expect(page.getByLabel('Adresse e-mail')).toHaveValue('nora.lefebvre@example.test');
  const allowed = await readAfter(page, RECORD_A, () => signIn(page));
  expect(allowed.status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Dossier accessible' })).toBeFocused();
  await expect(page.getByTestId('isolation-journey')).toContainText('Claire Martin');
  await expect(page.getByTestId('isolation-journey').locator('time')).toHaveText('18 avril 1978');
  await expect(page.locator('header')).toContainText('Cabinet des Tilleuls');
  await record(allowed, 'REC-A-ALLOWED');
  await page.screenshot({ path: test.info().outputPath('isolation-1-nora-allowed.png') });

  const outside = await readAfter(page, RECORD_B, () => page.getByRole('button', { name: 'Voir le refus hors du cabinet' }).click());
  expect(outside.status()).toBe(404);
  await expect(page.getByRole('heading', { name: 'Élément introuvable' })).toBeFocused();
  await record(outside, 'REC-HIDDEN-FOREIGN-ID');
  await expect(page.getByTestId('isolation-journey')).not.toContainText(/Hugo|Petit|Canal/);
  expect(await withoutCorrelation(outside)).toEqual(generic);
  // An absent record of the same practice answers byte for byte the same problem.
  const missing = await page.request.get(recordUrl('00000000-0000-4000-8000-00000000dead'));
  expect(missing.status()).toBe(404);
  expect(await withoutCorrelation(missing)).toEqual(generic);
  await page.screenshot({ path: test.info().outputPath('isolation-2-nora-hidden.png') });

  // Mila, an administrator without a clinical capability, is refused in the practice she can see.
  await page.getByRole('button', { name: 'Revenir à mon espace' }).click();
  await page.getByRole('button', { name: 'Se déconnecter' }).click();
  await expect(page).toHaveURL(/\/demonstration$/);
  await page.getByRole('button', { name: 'Ouvrir le parcours' }).click();
  await page.getByRole('link', { name: 'Mila' }).click();
  await expect(page.getByLabel('Adresse e-mail')).toHaveValue('mila.dubois@example.test');
  const refused = await readAfter(page, RECORD_A, () => signIn(page));
  expect(refused.status()).toBe(403);
  await expect(page.getByRole('heading', { name: 'Cet espace est réservé aux soins' })).toBeFocused();
  await expect(page.getByTestId('isolation-journey')).toContainText('Votre rôle d’administration au Cabinet des Tilleuls');
  await expect(page.getByTestId('isolation-journey')).not.toContainText('Claire');
  await record(refused, 'ADM-NO-CLINICAL');
  await page.screenshot({ path: test.info().outputPath('isolation-3-mila-restricted.png') });

  // Claire, linked to the record as a patient, sees the generic unavailable page in her own layout.
  await page.getByRole('button', { name: 'Revenir à mon espace' }).click();
  await page.getByRole('button', { name: 'Se déconnecter' }).click();
  await page.getByRole('button', { name: 'Ouvrir le parcours' }).click();
  await page.getByRole('link', { name: 'Claire' }).click();
  await expect(page).toHaveURL(/\/connexion\/patient\?retour=/);
  await expect(page.getByLabel('Adresse e-mail')).toHaveValue('claire.martin@example.test');
  const patient = await readAfter(page, RECORD_A, () => signIn(page));
  expect(patient.status()).toBe(404);
  await expect(page.getByRole('heading', { name: 'Cette page n’est pas disponible' })).toBeFocused();
  await record(patient, 'PAT-OWN-RECORD-STAFF-ROUTE');
  expect(await withoutCorrelation(patient)).toEqual(generic);
  await expect(page.getByTestId('isolation-journey')).not.toContainText(/403|Claire Martin|1978/);
  await page.screenshot({ path: test.info().outputPath('isolation-4-claire-patient.png') });
  await page.getByRole('button', { name: 'Revenir à mon espace' }).click();
  await expect(page.getByRole('heading', { name: 'Votre espace patient' })).toBeVisible();

  // Each replayed refusal is a measured case of the CI matrix with the same expected status.
  for (const step of replay) expect(String(step.observed), step.case).toBe(step.expected.split(' ')[0]);
  await test.info().attach('isolation-replay.json', { body: JSON.stringify(replay, null, 2), contentType: 'application/json' });
});

test('an expired session returns to the same step after reconnection and never shows the previous result', async ({ page }) => {
  await startJourney(page);
  await readAfter(page, RECORD_A, () => signIn(page));
  await expect(page.getByRole('heading', { name: 'Dossier accessible' })).toBeFocused();

  await page.context().clearCookies();
  await page.getByRole('button', { name: 'Voir le refus hors du cabinet' }).click();
  await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeFocused();
  await expect(page.getByRole('main')).toContainText('Reconnectez-vous pour vérifier votre accès à cette étape.');
  await expect(page.getByText('Claire Martin')).toHaveCount(0);
  await page.screenshot({ path: test.info().outputPath('isolation-5-expired.png') });

  await page.getByRole('button', { name: 'Se reconnecter' }).click();
  // The 9.1 entry marker keeps no identifier once the first sign-in succeeded, so the account is typed again.
  await page.getByLabel('Adresse e-mail').fill('nora.lefebvre@example.test');
  const rechecked = await readAfter(page, RECORD_B, () => signIn(page));
  expect(rechecked.status()).toBe(404);
  await expect(page.getByRole('heading', { name: 'Élément introuvable' })).toBeFocused();
  await expect(page).toHaveURL(/\/compte\/isolation\/hors-cabinet$/);
});

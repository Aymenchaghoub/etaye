import { execFileSync } from 'node:child_process';
import { pbkdf2Sync, randomBytes, randomUUID } from 'node:crypto';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { expect, test, type Page } from '@playwright/test';

const container = process.env.ETAYE_E2E_DB_CONTAINER;
const captures = process.env.ETAYE_E2E_CONTEXT_CAPTURES;
const password = 'Une phrase de test synthétique';
const nameA = 'Cabinet des Tilleuls';
const nameB = 'Cabinet du Canal';

function sql(input: string) {
  if (!container || !/^[a-z0-9_.-]+$/i.test(container)) throw new Error('Set the disposable database container first');
  execFileSync('docker', ['exec', '-i', container, 'psql', '-U', 'etaye_owner', '-d', 'etaye',
    '-q', '-v', 'ON_ERROR_STOP=1'], { input, stdio: ['pipe', 'ignore', 'pipe'], timeout: 20_000 });
}

function seed() {
  const actor = randomUUID();
  const email = `contexts-${actor}@example.test`;
  const [practiceA, practiceB, membershipA, membershipB, patientA, patientB, recordA, recordB, portal] =
    Array.from({ length: 9 }, () => randomUUID());
  const salt = randomBytes(16);
  const digest = Buffer.concat([salt, pbkdf2Sync(password, salt, 600_000, 32, 'sha256')]).toString('hex');
  sql(`
    BEGIN;
    INSERT INTO account (id, normalized_email, display_name, password_hash)
      VALUES ('${actor}', '${email}', 'Sami Rahmani', '{pbkdf2-sha256-600000}${digest}');
    INSERT INTO practice (id, display_name) VALUES ('${practiceA}', '${nameA}'), ('${practiceB}', '${nameB}');
    INSERT INTO membership (id, account_id, practice_id, capabilities) VALUES
      ('${membershipA}', '${actor}', '${practiceA}', ARRAY['CLINICAL_READ']),
      ('${membershipB}', '${actor}', '${practiceB}', ARRAY['TEAM_MANAGE']);
    SELECT set_config('etaye.practice_id', '${practiceA}', true);
    INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth)
      VALUES ('${patientA}', '${practiceA}', 'Claire', 'Fictive', '1990-01-01');
    INSERT INTO patient_record (id, practice_id, patient_id)
      VALUES ('${recordA}', '${practiceA}', '${patientA}');
    SELECT set_config('etaye.practice_id', '${practiceB}', true);
    INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth)
      VALUES ('${patientB}', '${practiceB}', 'Louis', 'Fictif', '1991-01-01');
    INSERT INTO patient_record (id, practice_id, patient_id)
      VALUES ('${recordB}', '${practiceB}', '${patientB}');
    INSERT INTO portal_link (id, account_id, practice_id, record_id, verified_by, state)
      VALUES ('${portal}', '${actor}', '${practiceB}', '${recordB}', '${actor}', 'ACTIVE');
    COMMIT;
  `);
  return { email, practiceA, practiceB, membershipA, membershipB, recordA, recordB, portal };
}

async function compare(page: Page, reference: Page, state: string) {
  if (!captures) return;
  const path = pathToFileURL(resolve('../../docs/design/reviews/2026-11-21-story-1-3-variants/index.html'));
  for (const [width, height] of [[1440, 1024], [1024, 768], [768, 1024], [390, 844], [320, 844]]) {
    for (const theme of ['clair', 'sombre'] as const) {
      await page.setViewportSize({ width, height });
      await page.emulateMedia({ colorScheme: theme === 'sombre' ? 'dark' : 'light' });
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
      await reference.setViewportSize({ width, height });
      path.search = `?etat=${state}&theme=${theme}`;
      await reference.goto(path.href);
      await reference.evaluate(() => document.fonts.ready.then(() => null));
      await page.evaluate(() => document.fonts.ready.then(() => null));
      const prefix = `${state}-${width}-${theme}`;
      await page.screenshot({ path: join(captures, `${prefix}-app.png`) });
      await reference.screenshot({ path: join(captures, `${prefix}-ref.png`) });
    }
  }
}

async function readRecord(page: Page, practice: string, record: string) {
  return page.evaluate(async (path) => {
    const response = await fetch(path, { cache: 'no-store' });
    return { status: response.status, body: await response.json() };
  }, `/api/v1/practices/${practice}/records/${record}`);
}

test('real practice selection rechecks a revoked destination and preserves another tab', async ({ page, context }) => {
  const fixture = seed();
  const reference = captures ? await context.newPage() : undefined;
  const staffPath = `/compte/pratiques/${fixture.membershipA}`;
  const adminPath = `/compte/pratiques/${fixture.membershipB}`;
  const portalPath = `/compte/patient/${fixture.portal}`;

  await page.goto('/connexion');
  await page.locator('#l-mail').fill(fixture.email);
  await page.locator('#l-pw').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Choisir un contexte de travail' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Ouvrir · Praticien/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Ouvrir · Administration/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Ouvrir · Espace patient/ })).toBeVisible();
  if (reference) await compare(page, reference, 'choix');

  await page.getByRole('button', { name: /Ouvrir · Praticien/ }).click();
  await expect(page).toHaveURL(new RegExp(staffPath + '$'));
  await expect(page.locator('header')).toContainText(nameA);
  if (reference) await compare(page, reference, 'praticien');
  const positive = await readRecord(page, fixture.practiceA, fixture.recordA);
  expect(positive.status).toBe(200);
  const hidden = await readRecord(page, fixture.practiceA, fixture.recordB);
  expect(hidden.status).toBe(404);
  expect(JSON.stringify(hidden.body)).not.toContain('Louis');

  const otherTab = await context.newPage();
  await otherTab.goto(staffPath);
  await expect(otherTab.locator('header')).toContainText(nameA);
  await page.bringToFront();
  await page.getByRole('button', { name: /Changer de contexte/ }).click();
  await page.getByRole('button', { name: /Ouvrir · Administration/ }).click();
  await expect(page).toHaveURL(new RegExp(adminPath + '$'));
  await expect(page.locator('header')).toContainText('Administration');
  await expect(page.getByText(nameA)).toHaveCount(0);
  if (reference) await compare(page, reference, 'administration');
  expect((await readRecord(page, fixture.practiceB, fixture.recordB)).status).toBe(403);
  await otherTab.bringToFront();
  await expect(otherTab).toHaveURL(new RegExp(staffPath + '$'));
  await expect(otherTab.locator('header')).toContainText(nameA);

  await page.getByRole('button', { name: /Changer de contexte/ }).click();
  if (captures) await page.setViewportSize({ width: 390, height: 844 });
  let continueRead: (() => void) | undefined;
  const networkDelay = new Promise<void>((resolve) => { continueRead = resolve; });
  await page.route('**/api/v1/session', async (route) => {
    await networkDelay;
    await route.continue();
  }, { times: 1 });
  try {
    await page.getByRole('button', { name: /Ouvrir · Espace patient/ }).click();
    await expect(page.getByRole('status')).toContainText('Vérification de l’accès');
    await expect(page.getByTestId('protected-account')).toHaveCount(0);
    if (captures) await page.screenshot({ path: join(captures, 'verification-390-app.png') });
  } finally {
    continueRead?.();
  }
  await expect(page).toHaveURL(new RegExp(portalPath + '$'));
  await expect(page.getByRole('heading', { name: 'Votre espace patient' })).toBeFocused();
  if (reference) await compare(page, reference, 'patient');

  sql(`UPDATE practice SET state = 'SUSPENDED' WHERE id = '${fixture.practiceB}'`);
  await page.reload();
  await expect(page.getByRole('alert')).toContainText('Ce contexte n’est plus disponible');
  await expect(page.getByText(nameB)).toHaveCount(0);
  await expect(page.getByTestId('protected-account')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Ouvrir · Praticien/ })).toBeVisible();
  if (reference) await compare(page, reference, 'retire');
  await page.getByRole('button', { name: /Ouvrir · Praticien/ }).click();
  await expect(page.locator('header')).toContainText(nameA);
  if (!container) throw new Error('The disposable database is required for the outage check.');
  execFileSync('docker', ['pause', '--', container], { stdio: 'ignore', timeout: 20_000 });
  try {
    await page.reload();
    await expect(page.getByRole('alert')).toContainText('Le service est temporairement indisponible', { timeout: 15_000 });
    await expect(page.getByText(nameA)).toHaveCount(0);
    if (reference) await compare(page, reference, 'echec');
  } finally {
    execFileSync('docker', ['unpause', '--', container], { stdio: 'ignore', timeout: 20_000 });
  }
  await expect(async () => {
    const retry = page.getByRole('button', { name: 'Réessayer' });
    if (await retry.isVisible()) await retry.click();
    await expect(page.locator('header')).toContainText(nameA, { timeout: 8_000 });
  }).toPass({ timeout: 60_000 });
  await reference?.close();
});

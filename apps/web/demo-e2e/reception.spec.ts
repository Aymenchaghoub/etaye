import { randomUUID } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';
import type { Csrf, SchedulingPatient, SchedulingPatientPage } from '../src/api/generated/types.gen';
import { fixtureId, heading, practicePath, signIn } from './support';

test.use({ actionTimeout: 10_000 });

const patients = `/api/v1/practices/${fixtureId('PRA-A')}/patients`;
const saveIdentity = (page: Page) => page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
const firstNameField = (page: Page) => page.getByRole('textbox', { name: 'Prénom', exact: true });
const registration = (page: Page) => page.waitForResponse((response) => response.url().endsWith(patients)
  && response.request().method() === 'POST');

test('search, register, check a duplicate and correct an identity after a concurrent save', async ({ page, context }) => {
  await page.goto('/connexion');
  await signIn(page, 'nora.lefebvre@example.test');
  await page.getByRole('link', { name: 'Patients', exact: true }).click();
  await page.getByLabel('Nom, prénom ou date de naissance').fill('Mar Claire');
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
  await expect(page.getByRole('link', { name: 'MARTIN Claire', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'BERNARD Louis', exact: true })).toHaveCount(0);

  async function enterNewIdentity(firstName: string) {
    await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
    await page.getByLabel('Nom de naissance').fill('Dupont');
    await firstNameField(page).fill(firstName);
    await page.getByLabel('Jour', { exact: true }).fill('02');
    await page.getByLabel('Mois', { exact: true }).fill('06');
    await page.getByLabel('Année', { exact: true }).fill('1985');
  }

  await enterNewIdentity('Jeanne');
  const created = registration(page);
  await saveIdentity(page);
  expect((await created).status()).toBe(201);
  await expect(page.locator('.etaye-bandeau__titre', { hasText: 'Dossier créé' })).toBeVisible();
  await enterNewIdentity('Juliette');
  const duplicate = registration(page);
  await saveIdentity(page);
  expect((await duplicate).status()).toBe(409);
  await expect(page.getByRole('alert').filter({ hasText: 'Un dossier semblable existe dans ce cabinet' }))
    .toContainText('DUPONT Jeanne');
  const lookup = await page.request.get(patients, { params: { q: 'Dupont' } });
  expect(lookup.status()).toBe(200);
  expect(await lookup.json()).toMatchObject({ items: [{ firstName: 'Jeanne', lastName: 'Dupont' }] });
  const confirmed = registration(page);
  await page.getByRole('button', { name: 'Créer quand même' }).click();
  expect((await confirmed).status()).toBe(201);
  await expect(page.locator('.etaye-bandeau__titre', { hasText: 'Dossier créé' })).toBeVisible();
  const distinct = await page.request.get(patients, { params: { q: 'Dupont' } });
  expect(distinct.status()).toBe(200);
  const people = (await distinct.json() as { items: Array<{ firstName: string; recordId: string }> }).items;
  expect(people.map(({ firstName }) => firstName)).toEqual(['Jeanne', 'Juliette']);
  expect(new Set(people.map(({ recordId }) => recordId)).size).toBe(2);

  await page.goto(`${practicePath('MEM-NORA-A')}/patients`);
  await page.getByRole('link', { name: 'Modifier l’identité de DUPONT Jeanne' }).click();
  await expect(heading(page, 'Modifier l’identité')).toBeFocused();
  const editingUrl = page.url();
  const other = await context.newPage();
  try {
    await other.goto(editingUrl);
    await expect(firstNameField(other)).toHaveValue('Jeanne');
    await firstNameField(other).fill('Jeanne Anne');
    const first = other.waitForResponse((response) => response.request().method() === 'PATCH');
    await saveIdentity(other);
    expect((await first).status()).toBe(200);

    await page.bringToFront();
    await firstNameField(page).fill('Jeanne Louise');
    const stale = page.waitForResponse((response) => response.request().method() === 'PATCH');
    await saveIdentity(page);
    const refused = await stale;
    expect(refused.status()).toBe(412);
    expect(refused.request().headers()['if-match']).toBe('"0"');
    await expect(page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' })).toBeVisible();
    await expect(firstNameField(page)).toHaveValue('Jeanne Louise');
    await expect(page.getByText('Jeanne Anne', { exact: true })).toBeVisible();

    const revised = page.waitForResponse((response) => response.request().method() === 'PATCH');
    await saveIdentity(page);
    const saved = await revised;
    expect(saved.status()).toBe(200);
    expect(saved.request().headers()['if-match']).toBe('"1"');
    await expect(page.getByText('L’identité de DUPONT Jeanne Louise a été enregistrée.')).toBeVisible();
  } finally {
    await other.close();
  }
});

test('the scheduling contract gives Mila administrative identity data and no clinical record', async ({ page }) => {
  await page.goto('/connexion');
  await signIn(page, 'mila.dubois@example.test');
  const scheduling = `/api/v1/practices/${fixtureId('PRA-A')}/scheduling/patients`;
  const search = await page.request.get(scheduling, { params: { q: 'Martin' } });
  expect(search.status()).toBe(200);
  const projection = await search.json() as SchedulingPatientPage;
  expect(projection.items).toHaveLength(1);
  expect(projection.items[0]).toEqual({
    id: expect.any(String), practiceId: fixtureId('PRA-A'), recordId: fixtureId('R-A-001'),
    firstName: 'Claire', lastName: 'Martin', dateOfBirth: '1978-04-18', contactEmail: null, phone: null,
  });
  const csrf = await page.request.get('/api/v1/csrf');
  expect(csrf.status()).toBe(200);
  const { token } = await csrf.json() as Csrf;
  const registration = await page.request.post(scheduling, {
    headers: { 'X-CSRF-TOKEN': token, 'Idempotency-Key': randomUUID() },
    data: { lastName: 'Fabre', firstName: 'Camille', dateOfBirth: '1988-06-13' },
  });
  expect(registration.status()).toBe(201);
  const created = await registration.json() as SchedulingPatient;
  expect(created).toMatchObject({ lastName: 'Fabre', firstName: 'Camille' });
  const record = await page.request.get(`/api/v1/practices/${fixtureId('PRA-A')}/records/${created.recordId}`);
  expect(record.status()).toBe(403);
});

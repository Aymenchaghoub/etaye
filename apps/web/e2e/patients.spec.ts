import { execFileSync } from 'node:child_process';
import { pbkdf2Sync, randomBytes, randomUUID } from 'node:crypto';
import { expect, test, type Page } from '../tests/support/browser-coverage';

/**
 * Real Spring, PostgreSQL and React stack for story 3.1. Run with ETAYE_E2E_DB_CONTAINER set to a disposable database
 * container (see playwright.real.config.ts). Every case seeds its own practices and accounts with synthetic data.
 */
const container = process.env.ETAYE_E2E_DB_CONTAINER;
const database = process.env.ETAYE_E2E_DB_NAME ?? 'etaye';
const password = 'Une phrase de test synthétique';
const nameA = 'Cabinet des Tilleuls';
const nameB = 'Cabinet du Canal';

function sql(input: string) {
  if (!container || !/^[a-z0-9_.-]+$/i.test(container)) throw new Error('Set the disposable database container first');
  execFileSync('docker', ['exec', '-i', container, 'psql', '-U', 'etaye_owner', '-d', database,
    '-q', '-v', 'ON_ERROR_STOP=1'], { input, stdio: ['pipe', 'ignore', 'pipe'], timeout: 20_000 });
}

function digest() {
  const salt = randomBytes(16);
  return Buffer.concat([salt, pbkdf2Sync(password, salt, 600_000, 32, 'sha256')]).toString('hex');
}

/** One clinician in both practices, one read-only member of the first, and one patient per name below. */
function seed() {
  const [writer, reader] = [randomUUID(), randomUUID()];
  const [practiceA, practiceB, writerA, writerB, readerA] = Array.from({ length: 5 }, () => randomUUID());
  const tag = randomUUID().slice(0, 8);
  const hash = `{pbkdf2-sha256-600000}${digest()}`;
  const writerEmail = `patients-w-${tag}@example.test`;
  const readerEmail = `patients-r-${tag}@example.test`;
  const patient = (practice: string, first: string, last: string, born: string) => {
    const [id, record] = [randomUUID(), randomUUID()];
    return `INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth)
      VALUES ('${id}', '${practice}', '${first}', '${last}', '${born}');
    INSERT INTO patient_record (id, practice_id, patient_id) VALUES ('${record}', '${practice}', '${id}');`;
  };
  sql(`
    BEGIN;
    INSERT INTO account (id, normalized_email, display_name, password_hash) VALUES
      ('${writer}', '${writerEmail}', 'Nora Lefebvre', '${hash}'),
      ('${reader}', '${readerEmail}', 'Lecteur Fictif', '${hash}');
    INSERT INTO practice (id, display_name) VALUES ('${practiceA}', '${nameA}'), ('${practiceB}', '${nameB}');
    INSERT INTO membership (id, account_id, practice_id, capabilities) VALUES
      ('${writerA}', '${writer}', '${practiceA}', ARRAY['CLINICAL_READ','CLINICAL_WRITE']),
      ('${writerB}', '${writer}', '${practiceB}', ARRAY['CLINICAL_READ','CLINICAL_WRITE']),
      ('${readerA}', '${reader}', '${practiceA}', ARRAY['CLINICAL_READ']);
    SELECT set_config('etaye.practice_id', '${practiceA}', true);
    ${patient(practiceA, 'Claire', 'Martin', '1978-04-18')}
    ${patient(practiceA, 'Louis', 'Bernard', '1991-07-09')}
    SELECT set_config('etaye.practice_id', '${practiceB}', true);
    ${patient(practiceB, 'Hugo', 'Petit', '1986-02-25')}
    COMMIT;
  `);
  return { writerEmail, readerEmail, practiceA, practiceB, writerA, writerB, readerA };
}

async function signIn(page: Page, email: string) {
  await page.goto('/connexion');
  await page.locator('#l-mail').fill(email);
  await page.locator('#l-pw').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
}

const heading = (page: Page, name: string) => page.getByRole('heading', { name, exact: true, level: 1 });
/** Reads the listed names once the first row has rendered: the directory loads after navigation. */
const names = async (page: Page) => {
  await expect(page.locator('.etaye-lignes__titre').first()).toBeVisible();
  return page.locator('.etaye-lignes__titre').allTextContents();
};

async function api(page: Page, method: string, path: string, init: { body?: unknown; ifMatch?: string } = {}) {
  return page.evaluate(async ([verb, target, payload, match]) => {
    const headers: Record<string, string> = {};
    if (verb !== 'GET') {
      const csrf = await (await fetch('/api/v1/csrf', { cache: 'no-store' })).json();
      headers[csrf.headerName] = csrf.token;
      headers['Content-Type'] = 'application/json';
      if (match) headers['If-Match'] = match;
    }
    const response = await fetch(target, { method: verb, headers, body: payload ? payload : undefined, cache: 'no-store' });
    return { status: response.status, body: await response.json().catch(() => null) };
  }, [method, `/api/v1${path}`, init.body ? JSON.stringify(init.body) : '', init.ifMatch ?? ''] as const);
}

async function fillIdentity(page: Page, values: { nom: string; prenom: string; j: string; m: string; a: string }) {
  await page.getByLabel('Nom de naissance').fill(values.nom);
  await page.getByLabel('Prénom').fill(values.prenom);
  await page.getByLabel('Jour').fill(values.j);
  await page.getByLabel('Mois').fill(values.m);
  await page.getByLabel('Année').fill(values.a);
}

test('registers a patient, warns about a likely duplicate and creates a distinct person only after confirmation', async ({ page }) => {
  const fixture = seed();
  await signIn(page, fixture.writerEmail);
  await page.getByRole('button', { name: new RegExp(`Ouvrir · Praticien · ${nameA}`) }).click();
  await page.getByRole('link', { name: 'Patients' }).click();
  await expect(heading(page, 'Patients')).toBeFocused();
  expect(await names(page)).toEqual(['BERNARD Louis', 'MARTIN Claire']);

  // Another practice's patient is never found, and an empty search is not an empty practice.
  await page.getByLabel('Nom, prénom ou date de naissance').fill('Petit');
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await expect(page.getByText(`Aucun patient trouvé pour « Petit » au ${nameA}`)).toBeVisible();
  await page.getByRole('button', { name: 'Effacer la recherche' }).first().click();
  await expect.poll(() => names(page)).toHaveLength(2);

  await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
  await fillIdentity(page, { nom: 'Dupont', prenom: 'Jeanne', j: '02', m: '06', a: '1985' });
  await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
  await expect(page.locator('.etaye-bandeau__titre', { hasText: 'Dossier créé' })).toBeVisible();
  await expect(page.locator('.etaye-ligne--nouvelle')).toContainText('DUPONT Jeanne');

  await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
  await fillIdentity(page, { nom: 'martin', prenom: 'CLAIRE', j: '18', m: '04', a: '1978' });
  await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
  const warning = page.getByRole('alert').filter({ hasText: 'Un dossier semblable existe dans ce cabinet' });
  await expect(warning).toContainText('MARTIN Claire');
  await expect(warning).toContainText('naissance 18/04/1978');
  const before = await api(page, 'GET', `/practices/${fixture.practiceA}/patients?q=Martin`);
  expect(before.body.items).toHaveLength(1);

  await page.getByRole('button', { name: 'Créer quand même' }).click();
  await expect(page.locator('.etaye-bandeau__titre', { hasText: 'Dossier créé' })).toBeVisible();
  const after = await api(page, 'GET', `/practices/${fixture.practiceA}/patients?q=Martin`);
  expect(after.body.items).toHaveLength(2);
  expect(new Set(after.body.items.map((p: { recordId: string }) => p.recordId)).size).toBe(2);
  expect(after.body.nextCursor).toBeNull();
});

test('scopes the directory to the selected practice and hides the other one from a member of a single practice', async ({ page }) => {
  const fixture = seed();
  await signIn(page, fixture.writerEmail);
  await page.getByRole('button', { name: new RegExp(`Ouvrir · Praticien · ${nameB}`) }).click();
  await page.getByRole('link', { name: 'Patients' }).click();
  expect(await names(page)).toEqual(['PETIT Hugo']);
  await page.getByLabel('Nom, prénom ou date de naissance').fill('Martin');
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await expect(page.getByText(`Aucun patient trouvé pour « Martin » au ${nameB}`)).toBeVisible();

  // The query stays in memory: coming back from another practice shows an empty field.
  await page.getByRole('button', { name: /Changer de contexte/ }).click();
  await page.getByRole('button', { name: new RegExp(`Ouvrir · Praticien · ${nameA}`) }).click();
  await page.getByRole('link', { name: 'Patients' }).click();
  await expect(page.getByLabel('Nom, prénom ou date de naissance')).toHaveValue('');
  expect(await names(page)).toEqual(['BERNARD Louis', 'MARTIN Claire']);

  await page.context().clearCookies();
  await signIn(page, fixture.readerEmail);
  await expect(page).toHaveURL(new RegExp(`/compte/pratiques/${fixture.readerA}$`));
  const foreign = await api(page, 'GET', `/practices/${fixture.practiceB}/patients`);
  expect(foreign.status).toBe(404);
  expect(JSON.stringify(foreign.body)).not.toContain('Hugo');
});

test('a read-only member can search but is refused creation with the restricted state', async ({ page }) => {
  const fixture = seed();
  await signIn(page, fixture.readerEmail);
  // The demonstration package closes a hard navigation as an expired session, so follow the in-app link.
  await page.getByRole('link', { name: 'Patients' }).click();
  expect(await names(page)).toEqual(['BERNARD Louis', 'MARTIN Claire']);
  await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
  await fillIdentity(page, { nom: 'Refusé', prenom: 'Test', j: '01', m: '01', a: '1980' });
  await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
  await expect(page.getByText('Création non autorisée')).toBeVisible();
  await expect(page.getByLabel('Nom de naissance')).toHaveCount(0);
  const list = await api(page, 'GET', `/practices/${fixture.practiceA}/patients?q=Refus`);
  expect(list.body.items).toHaveLength(0);
});

test('a correction on a stale version keeps the input, compares both versions and saves on the version seen', async ({ page }) => {
  const fixture = seed();
  await signIn(page, fixture.writerEmail);
  await page.getByRole('button', { name: new RegExp(`Ouvrir · Praticien · ${nameA}`) }).click();
  await page.getByRole('link', { name: 'Patients' }).click();
  await page.getByRole('link', { name: /Modifier l’identité de MARTIN Claire/ }).click();
  await expect(heading(page, 'Modifier l’identité')).toBeFocused();
  await expect(page.locator('.etaye-entete__meta')).toContainText('MARTIN Claire · version 0 ouverte');
  const found = await api(page, 'GET', `/practices/${fixture.practiceA}/patients?q=Claire`);
  const target = found.body.items[0] as { id: string };

  // Another colleague saves first, through the same API.
  const other = await api(page, 'PATCH', `/practices/${fixture.practiceA}/patients/${target.id}`, {
    ifMatch: '"0"', body: { firstName: 'Claire', lastName: 'Martin', dateOfBirth: '1978-04-18', phone: '0100000000' },
  });
  expect(other.status).toBe(200);

  await page.getByLabel('Prénom').fill('Claire-Anne');
  await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
  const conflict = page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' });
  await expect(conflict).toContainText('La version 1 a été enregistrée pendant votre saisie.');
  await expect(page.getByLabel('Prénom')).toHaveValue('Claire-Anne');
  await expect(page.getByText(/Enregistré · version 1 · \d{2}:\d{2}/)).toBeVisible();

  await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
  await expect(page.getByText('L’identité de MARTIN Claire-Anne a été enregistrée.')).toBeVisible();
  const saved = await api(page, 'GET', `/practices/${fixture.practiceA}/patients?q=Claire-Anne`);
  expect(saved.body.items[0]).toMatchObject({ firstName: 'Claire-Anne', version: 2, id: target.id });
});

test('warns before leaving an unsaved form and keeps nothing after discarding it', async ({ page }) => {
  const fixture = seed();
  await signIn(page, fixture.writerEmail);
  await page.getByRole('button', { name: new RegExp(`Ouvrir · Praticien · ${nameA}`) }).click();
  await page.goto(`/compte/pratiques/${fixture.writerA}/patients/nouveau`);
  await page.getByLabel('Nom de naissance').fill('Brouillon');
  await page.getByRole('button', { name: 'Annuler' }).click();
  await expect(page.getByRole('button', { name: 'Rester sur le formulaire' })).toBeFocused();
  await page.getByRole('button', { name: 'Rester sur le formulaire' }).click();
  await expect(page.getByLabel('Nom de naissance')).toHaveValue('Brouillon');
  await page.getByRole('button', { name: 'Annuler' }).click();
  await page.getByRole('button', { name: 'Abandonner les modifications' }).click();
  await expect(heading(page, 'Patients')).toBeVisible();
  await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
  await expect(page.getByLabel('Nom de naissance')).toHaveValue('');
});

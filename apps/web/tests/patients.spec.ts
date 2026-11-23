import { AxeBuilder } from '@axe-core/playwright';
import { expect, test, type Page, type Route } from '@playwright/test';
import type { AccessContext, Patient, Problem, SchedulingPatient, Session } from '../src/api/generated/types.gen';

const ids = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const tilleuls: AccessContext = {
  kind: 'MEMBERSHIP', grantId: ids(1), practiceId: ids(10), practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL',
};
const canal: AccessContext = {
  kind: 'MEMBERSHIP', grantId: ids(2), practiceId: ids(20), practiceName: 'Cabinet du Canal', displayRole: 'CLINICAL',
};
const admin: AccessContext = { ...canal, grantId: ids(4), practiceName: 'Cabinet des Tilleuls', displayRole: 'ADMINISTRATION' };
const base = `/compte/pratiques/${tilleuls.grantId}`;
const LIST = `${base}/patients`;
const NEW = `${base}/patients/nouveau`;
const when = '2026-11-23T09:00:00Z';

function person(n: number, lastName: string, firstName: string, dateOfBirth: string, patch: Partial<Patient> = {}): Patient {
  return {
    id: ids(1000 + n), recordId: ids(2000 + n), practiceId: tilleuls.practiceId, version: 0, createdAt: when, updatedAt: when,
    lastName, firstName, dateOfBirth, contactEmail: null, phone: null, ...patch,
  };
}
const louis = person(2, 'Bernard', 'Louis', '1991-07-09');
const marie = person(3, 'Collet', 'Marie', '1990-03-12');
const claire = person(1, 'Martin', 'Claire', '1978-04-18', { version: 7 });
const roster = [louis, marie, claire];

function snapshot(contexts: AccessContext[]): Session {
  return {
    account: {
      id: ids(100), displayName: 'Nora Lefebvre', email: 'nora.lefebvre@example.test', state: 'ACTIVE', locale: 'fr-FR',
      version: 0, createdAt: when, updatedAt: when,
    },
    membershipIds: contexts.map(({ grantId }) => grantId),
    portalLinkIds: [],
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    contexts,
  };
}
const problem = (status: number, code: string, extra: Partial<Problem> = {}): Problem => ({
  type: 'about:blank', title: code, status, code, correlationId: 'test', ...extra,
});

type Reply = { status?: number; json?: unknown; headers?: Record<string, string>; delay?: number; fail?: boolean };
type Calls = { searches: URL[]; creates: { body: Record<string, unknown>; key: string }[]; edits: { body: Record<string, unknown>; match: string }[]; reads: number };
type Handlers = {
  contexts?: AccessContext[];
  roster?: Patient[];
  search?: (url: URL, count: number) => Reply | undefined;
  create?: (body: Record<string, unknown>, key: string, count: number) => Reply | undefined;
  edit?: (body: Record<string, unknown>, match: string, count: number) => Reply | undefined;
  record?: (id: string, count: number) => Reply | undefined;
};

const squash = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

async function reply(route: Route, answer: Reply) {
  if (answer.delay) await new Promise((resolve) => setTimeout(resolve, answer.delay));
  if (answer.fail) return route.abort('failed');
  return route.fulfill({ status: answer.status ?? 200, json: answer.json, headers: answer.headers });
}

async function stubApi(page: Page, handlers: Handlers = {}): Promise<Calls> {
  const calls: Calls = { searches: [], creates: [], edits: [], reads: 0 };
  const contexts = handlers.contexts ?? [tilleuls];
  const people = [...(handlers.roster ?? roster)];
  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } }));
  await page.route('**/api/v1/session', (route) => route.fulfill({ json: snapshot(contexts) }));
  await page.route((url) => url.pathname.startsWith('/api/v1/practices/'), async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1/practices/', '');
    if (request.method() === 'GET' && /^[^/]+\/patients$/.test(path)) {
      calls.searches.push(url);
      const custom = handlers.search?.(url, calls.searches.length);
      if (custom) return reply(route, custom);
      const q = squash(url.searchParams.get('q') ?? '');
      const hits = people.filter((p) => !q || squash(`${p.lastName} ${p.firstName}`).includes(q));
      const offset = Number(Buffer.from(url.searchParams.get('cursor') ?? '', 'base64').toString() || 0);
      const limit = Number(url.searchParams.get('limit') ?? 25);
      const page_ = hits.slice(offset, offset + limit);
      const more = offset + limit < hits.length;
      return reply(route, { json: { items: page_, nextCursor: more ? Buffer.from(String(offset + limit)).toString('base64') : null } });
    }
    if (request.method() === 'POST' && /^[^/]+\/patients$/.test(path)) {
      const body = request.postDataJSON() as Record<string, unknown>;
      calls.creates.push({ body, key: request.headers()['idempotency-key'] ?? '' });
      const custom = handlers.create?.(body, request.headers()['idempotency-key'] ?? '', calls.creates.length);
      if (custom) return reply(route, custom);
      const created = person(50 + calls.creates.length, String(body.lastName), String(body.firstName), String(body.dateOfBirth));
      people.push(created);
      return reply(route, { status: 201, json: created, headers: { ETag: '"0"' } });
    }
    if (request.method() === 'PATCH') {
      const body = request.postDataJSON() as Record<string, unknown>;
      const match = request.headers()['if-match'] ?? '';
      calls.edits.push({ body, match });
      const custom = handlers.edit?.(body, match, calls.edits.length);
      if (custom) return reply(route, custom);
      const target = people.find((p) => path.endsWith(p.id));
      if (!target) return reply(route, { status: 404, json: problem(404, 'NOT_FOUND') });
      if (match !== `"${target.version}"`) return reply(route, { status: 412, json: problem(412, 'STALE_VERSION') });
      Object.assign(target, body, { version: target.version + 1 });
      return reply(route, { json: target, headers: { ETag: `"${target.version}"` } });
    }
    if (request.method() === 'GET' && /^[^/]+\/records\/[^/]+$/.test(path)) {
      calls.reads += 1;
      const id = path.split('/')[2];
      const custom = handlers.record?.(id, calls.reads);
      if (custom) return reply(route, custom);
      const target = people.find((p) => p.recordId === id);
      if (!target) return reply(route, { status: 404, json: problem(404, 'NOT_FOUND') });
      return reply(route, {
        json: { id: target.recordId, version: 1, createdAt: when, updatedAt: when, practiceId: target.practiceId,
          patientId: target.id, recordRevision: 1, patient: target },
        headers: { ETag: '"1"' },
      });
    }
    return route.fulfill({ status: 404, json: problem(404, 'NOT_FOUND') });
  });
  return calls;
}

async function accessible(page: Page) {
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.passes.length).toBeGreaterThan(0);
  expect(result.violations.map(({ id, nodes }) => [id, nodes.map(({ target }) => target)])).toEqual([]);
}
async function noOverflow(page: Page) {
  const width = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  expect(width[0]).toBeLessThanOrEqual(width[1]);
}
async function usesBackground(page: Page, selector: string, token: string) {
  return page.evaluate(([target, name]) => {
    const probe = document.createElement('div');
    probe.style.background = `var(${name})`;
    document.body.append(probe);
    const expected = getComputedStyle(probe).backgroundColor;
    probe.remove();
    const element = document.querySelector(target);
    return element ? getComputedStyle(element).backgroundColor === expected : false;
  }, [selector, token]);
}
async function noRingOnHeading(page: Page) {
  const style = await page.evaluate(() => getComputedStyle(document.querySelector('h1')!).outlineStyle);
  expect(style).toBe('none');
}
const rowNames = (page: Page) => page.locator('.etaye-lignes__titre').allTextContents();
const heading = (page: Page, name: string) => page.getByRole('heading', { name, exact: true, level: 1 });

async function fillIdentity(page: Page, values: { nom?: string; prenom?: string; j?: string; m?: string; a?: string; tel?: string; mail?: string }) {
  if (values.nom !== undefined) await page.getByLabel('Nom de naissance').fill(values.nom);
  if (values.prenom !== undefined) await page.getByLabel('Prénom').fill(values.prenom);
  if (values.j !== undefined) await page.getByLabel('Jour').fill(values.j);
  if (values.m !== undefined) await page.getByLabel('Mois').fill(values.m);
  if (values.a !== undefined) await page.getByLabel('Année').fill(values.a);
  if (values.tel !== undefined) await page.getByLabel('Téléphone').fill(values.tel);
  if (values.mail !== undefined) await page.getByLabel('Adresse e-mail').fill(values.mail);
}

const candidate = (p: Patient, extra: Partial<SchedulingPatient> = {}): SchedulingPatient => ({
  id: p.id, practiceId: p.practiceId, recordId: p.recordId, firstName: p.firstName, lastName: p.lastName,
  dateOfBirth: p.dateOfBirth, contactEmail: p.contactEmail, phone: p.phone, ...extra,
});

test.describe('S-07 directory', () => {
  test('lists the practice, tells an empty practice from a search without result and stays accessible', async ({ page }) => {
    await stubApi(page);
    await page.goto(LIST);
    await expect(heading(page, 'Patients')).toBeFocused();
    await noRingOnHeading(page);
    expect(await rowNames(page)).toEqual(['BERNARD Louis', 'COLLET Marie', 'MARTIN Claire']);
    await expect(page.getByRole('status').filter({ hasText: '3 patients affichés' })).toBeVisible();
    await expect(page.locator('.etaye-lignes__detail').first()).toContainText('09/07/1991');
    await expect(page.locator('.etaye-lignes__detail').first()).toContainText(/\d+ ans/);
    await expect(page.getByText('R-A-001')).toHaveCount(0);
    await expect(page.getByLabel('Espace patient')).toHaveCount(0);
    await accessible(page);

    await page.getByLabel('Nom, prénom ou date de naissance').fill('Petit');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect(page.getByText('Aucun patient trouvé pour « Petit » au Cabinet des Tilleuls')).toBeVisible();
    await expect(page.getByText('Aucun patient dans ce cabinet')).toHaveCount(0);
    await accessible(page);
    await page.getByRole('button', { name: 'Effacer la recherche' }).first().click();
    await expect.poll(() => rowNames(page)).toHaveLength(3);
  });

  test('an empty practice is not a failed search', async ({ page }) => {
    await stubApi(page, { roster: [] });
    await page.goto(LIST);
    await expect(page.getByText('Aucun patient dans ce cabinet')).toBeVisible();
    await expect(page.getByText('Aucun patient trouvé')).toHaveCount(0);
    await accessible(page);
  });

  test('the query is submitted only on request and never reaches the URL or browser storage', async ({ page }) => {
    const calls = await stubApi(page);
    await page.goto(LIST);
    await expect.poll(() => rowNames(page)).toHaveLength(3);
    const before = calls.searches.length;
    await page.getByLabel('Nom, prénom ou date de naissance').fill('Mar');
    expect(calls.searches.length).toBe(before);
    await page.getByLabel('Nom, prénom ou date de naissance').press('Enter');
    await expect.poll(() => calls.searches.length).toBe(before + 1);
    expect(calls.searches.at(-1)?.searchParams.get('q')).toBe('Mar');
    expect(page.url()).not.toContain('Mar');
    expect(page.url()).not.toContain('q=');
    const stored = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }));
    expect(stored).not.toContain('Mar');
    expect(await rowNames(page)).toEqual(['COLLET Marie', 'MARTIN Claire']);
  });

  test('keeps the query in memory when going to the form and back, and clears it when the practice changes', async ({ page }) => {
    await stubApi(page, { contexts: [tilleuls, canal] });
    await page.goto(LIST);
    await page.getByLabel('Nom, prénom ou date de naissance').fill('Claire');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect.poll(() => rowNames(page)).toEqual(['MARTIN Claire']);
    await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
    await expect(heading(page, 'Nouveau dossier patient')).toBeFocused();
    await page.getByRole('button', { name: 'Annuler' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    await expect(page.getByLabel('Nom, prénom ou date de naissance')).toHaveValue('Claire');

    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await page.getByRole('button', { name: /Ouvrir · Praticien · Cabinet du Canal/ }).click();
    await page.getByRole('link', { name: 'Patients' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    await expect(page.locator('header')).toContainText('Cabinet du Canal');
    await expect(page.getByLabel('Nom, prénom ou date de naissance')).toHaveValue('');
  });

  test('"Afficher plus" follows the cursor and a failed next page keeps the loaded rows', async ({ page }) => {
    const many = Array.from({ length: 30 }, (_, n) => person(100 + n, `Nom${String(n).padStart(2, '0')}`, 'Test', '1980-01-01'));
    let failNext = true;
    const calls = await stubApi(page, {
      roster: many,
      search: (url) => (url.searchParams.get('cursor') && failNext ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined),
    });
    await page.goto(LIST);
    await expect(page.getByRole('status').filter({ hasText: '25 patients affichés · d’autres patients existent' })).toBeVisible();
    await page.getByRole('button', { name: 'Afficher plus' }).click();
    await expect(page.getByText('La suite n’a pas pu être chargée')).toBeVisible();
    expect(await rowNames(page)).toHaveLength(25);
    await accessible(page);
    failNext = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect.poll(() => rowNames(page)).toHaveLength(30);
    expect(calls.searches.at(-1)?.searchParams.get('cursor')).toBeTruthy();
    await expect(page.getByRole('button', { name: 'Afficher plus' })).toHaveCount(0);
    await expect(page.getByRole('status').filter({ hasText: '30 patients affichés' })).toBeVisible();
  });

  test('a failed refresh keeps the earlier results and says how old they are', async ({ page }) => {
    let fail = false;
    await stubApi(page, { search: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined) });
    await page.goto(LIST);
    await expect.poll(() => rowNames(page)).toHaveLength(3);
    fail = true;
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect(page.getByText('La liste n’a pas pu être chargée entièrement')).toBeVisible();
    await expect(page.getByText(/Les résultats affichés datent de \d{2}:\d{2}/)).toBeVisible();
    expect(await rowNames(page)).toHaveLength(3);
    await accessible(page);
    fail = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByText('La liste n’a pas pu être chargée entièrement')).toHaveCount(0);
  });

  test('says honestly when nothing could be loaded, when access is refused, hidden or throttled', async ({ page }) => {
    let answer: Reply = { status: 503, json: problem(503, 'UNAVAILABLE') };
    await stubApi(page, { search: () => answer });
    await page.goto(LIST);
    await expect(page.getByText('La liste n’a pas pu être chargée', { exact: true })).toBeVisible();
    await expect(page.getByText('Aucun patient dans ce cabinet')).toHaveCount(0);
    await accessible(page);

    answer = { status: 429, json: problem(429, 'RATE_LIMITED'), headers: { 'Retry-After': '12' } };
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByText('Trop de recherches')).toBeVisible();
    await expect(page.getByText(/Patientez 12 secondes/)).toBeVisible();
    await expect(page.getByText('Aucun patient dans ce cabinet')).toHaveCount(0);

    answer = { status: 403, json: problem(403, 'FORBIDDEN') };
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByText('Accès aux patients non autorisé')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Créer un dossier patient' })).toHaveCount(0);
    await accessible(page);

    answer = { status: 404, json: problem(404, 'NOT_FOUND') };
    await page.goto(LIST);
    await expect(page.getByText('Élément introuvable').first()).toBeVisible();
  });

  test('a long name wraps and the birth date stays whole at 320 px', async ({ page }) => {
    const long = person(9, 'Dupont-Lefèvre de la Rochefoucauld-Montmorency', 'Marie-Charlotte Anne-Sophie', '1980-01-01');
    await page.setViewportSize({ width: 320, height: 844 });
    await stubApi(page, { roster: [long, claire] });
    await page.goto(LIST);
    await expect(page.locator('.etaye-lignes__detail').first()).toContainText('01/01/1980');
    await noOverflow(page);
    await accessible(page);
  });
});

test.describe('S-08 identity form', () => {
  test('explains each invalid field, keeps the input and creates the patient, returning to the list with the created row', async ({ page }) => {
    const calls = await stubApi(page);
    await page.goto(NEW);
    await expect(heading(page, 'Nouveau dossier patient')).toBeFocused();
    await expect(page.getByLabel('Nom d’usage')).toHaveCount(0);
    await expect(page.getByLabel(/Origine/)).toHaveCount(0);
    await accessible(page);

    await fillIdentity(page, { nom: '', prenom: 'Claire', j: '31', m: '02', a: '1978' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    const summary = page.getByRole('alert').filter({ hasText: '2 champs à corriger' });
    await expect(summary).toBeFocused();
    await expect(summary).toContainText('Nom de naissance : obligatoire');
    await expect(page.getByText('Erreur : le 31 février n’existe pas. Format JJ/MM/AAAA.')).toBeVisible();
    await expect(page.getByLabel('Prénom')).toHaveValue('Claire');
    expect(calls.creates).toHaveLength(0);
    await accessible(page);

    await fillIdentity(page, { nom: 'Dupont', prenom: 'Jeanne', j: '02', m: '06', a: '1985', mail: ' jeanne@example.test ' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(heading(page, 'Patients')).toBeFocused();
    await noRingOnHeading(page);
    await expect(page.getByText('Dossier créé')).toBeVisible();
    await expect(page.getByText('DUPONT Jeanne existe dans ce cabinet uniquement. Aucun espace patient n’a été créé.')).toBeVisible();
    await expect(page.locator('.etaye-ligne--nouvelle')).toContainText('DUPONT Jeanne');
    expect(calls.creates).toHaveLength(1);
    expect(calls.creates[0].body).toEqual({ firstName: 'Jeanne', lastName: 'Dupont', dateOfBirth: '1985-06-02', contactEmail: 'jeanne@example.test', phone: null });
    expect(calls.creates[0].key.length).toBeGreaterThanOrEqual(16);
    await accessible(page);
  });

  test('refuses an impossible, missing or future birth date before any request', async ({ page }) => {
    const calls = await stubApi(page);
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Test', prenom: 'Test' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByText('Erreur : indiquez la date de naissance.')).toBeVisible();
    await fillIdentity(page, { j: '29', m: '02', a: '1979' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByText('Erreur : le 29 février 1979 n’existe pas.')).toBeVisible();
    await fillIdentity(page, { j: '01', m: '01', a: '2999' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByText('Erreur : la date de naissance ne peut pas être dans le futur.')).toBeVisible();
    expect(calls.creates).toHaveLength(0);
  });

  test('a likely duplicate shows typed candidates; creating anyway names them and uses a new key', async ({ page }) => {
    const second = person(7, 'Martin', 'Claire', '1985-11-03', { phone: '0100000000' });
    const calls = await stubApi(page, {
      create: (body, _key, count) => {
        const confirmed = (body.confirmedDistinctRecordIds as string[] | undefined) ?? [];
        if (count === 1) return { status: 409, json: problem(409, 'DUPLICATE_PATIENT', { candidates: [candidate(claire)] }) };
        if (count === 2 && !confirmed.includes(second.recordId)) {
          return { status: 409, json: problem(409, 'DUPLICATE_PATIENT', { candidates: [candidate(claire), candidate(second)] }) };
        }
        return undefined;
      },
    });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Martin', prenom: 'Claire', j: '18', m: '04', a: '1978' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    const alert = page.getByRole('alert').filter({ hasText: 'Un dossier semblable existe dans ce cabinet' });
    await expect(alert).toBeVisible();
    await expect(alert).toContainText('MARTIN Claire');
    await expect(alert).toContainText('naissance 18/04/1978 · coordonnées : non renseignées');
    await expect(alert).toContainText('« Créer quand même » confirme qu’il s’agit d’une autre personne que MARTIN Claire.');
    await expect(page.getByRole('link', { name: 'Ouvrir le dossier existant' })).toHaveAttribute('href', `${base}/patients/${claire.recordId}/modifier`);
    await accessible(page);

    await page.getByRole('button', { name: 'Créer quand même' }).click();
    const changed = page.getByRole('alert').filter({ hasText: 'La liste des dossiers semblables a changé' });
    await expect(changed).toBeVisible();
    await expect(changed.locator('li')).toHaveCount(2);
    await expect(changed.getByRole('link', { name: /Ouvrir ce dossier/ })).toHaveCount(2);
    await accessible(page);

    await page.getByRole('button', { name: 'Créer quand même' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    expect(calls.creates).toHaveLength(3);
    expect(calls.creates[0].body.confirmedDistinctRecordIds).toBeUndefined();
    expect(calls.creates[1].body.confirmedDistinctRecordIds).toEqual([claire.recordId]);
    expect(calls.creates[2].body.confirmedDistinctRecordIds).toEqual([claire.recordId, second.recordId]);
    expect(new Set(calls.creates.map((c) => c.key)).size).toBe(3);
  });

  test('a failed save retries with the same key and a changed body gets a new one', async ({ page }) => {
    const calls = await stubApi(page, {
      create: (_body, _key, count) => (count <= 2 ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined),
    });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Roux', prenom: 'Paul', j: '05', m: '05', a: '1970' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByText('L’enregistrement a échoué')).toBeVisible();
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('Roux');
    await accessible(page);
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByText('L’enregistrement a échoué')).toBeVisible();
    await fillIdentity(page, { prenom: 'Pierre' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    expect(calls.creates.map((c) => c.key)[0]).toBe(calls.creates[1].key);
    expect(calls.creates[2].key).not.toBe(calls.creates[1].key);
  });

  test('disables the form while saving, keeps the button width and shows no success before the server answers', async ({ page }) => {
    await stubApi(page, { create: () => ({ status: 201, delay: 700, json: person(60, 'Lent', 'Test', '1980-01-01'), headers: { ETag: '"0"' } }) });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Lent', prenom: 'Test', j: '01', m: '01', a: '1980' });
    const button = page.getByRole('button', { name: 'Enregistrer l’identité' });
    const rest = (await button.boundingBox())!.width;
    await button.click();
    const busy = page.getByRole('button', { name: 'Enregistrement…' });
    await expect(busy).toBeDisabled();
    await expect(busy).toHaveAttribute('aria-busy', 'true');
    expect((await busy.boundingBox())!.width).toBeCloseTo(rest, 0);
    await expect(page.getByLabel('Nom de naissance')).toHaveAttribute('readonly', '');
    await expect(page.getByRole('status').filter({ hasText: 'Enregistrement en cours…' })).toBeVisible();
    await expect(page.getByText('Dossier créé')).toHaveCount(0);
    await accessible(page);
    await expect(page.getByText('Dossier créé')).toBeVisible();
  });

  test('a lost response is checked with the same key and never creates a second attempt', async ({ page }) => {
    const calls = await stubApi(page, { create: (_body, _key, count) => (count === 1 ? { fail: true } : undefined) });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Perdu', prenom: 'Test', j: '01', m: '01', a: '1980' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    expect(calls.creates).toHaveLength(2);
    expect(calls.creates[0].key).toBe(calls.creates[1].key);
  });

  test('two lost responses end as an unconfirmed save with the input kept', async ({ page }) => {
    await stubApi(page, { create: () => ({ fail: true }) });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Perdu', prenom: 'Test', j: '01', m: '01', a: '1980' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByText('Enregistrement non confirmé')).toBeVisible();
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('Perdu');
    await accessible(page);
  });

  test('maps a server refusal to the restricted state and field errors to the summary', async ({ page }) => {
    let answer: Reply = { status: 422, json: problem(422, 'VALIDATION_FAILED', { fieldErrors: [{ field: 'lastName', code: 'NotBlank', message: 'x' }] }) };
    await stubApi(page, { create: () => answer });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Test', prenom: 'Test', j: '01', m: '01', a: '1980' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByRole('alert').filter({ hasText: '1 champ à corriger' })).toContainText('Nom de naissance : à vérifier');
    answer = { status: 403, json: problem(403, 'FORBIDDEN') };
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByText('Création non autorisée')).toBeVisible();
    await expect(page.getByLabel('Nom de naissance')).toHaveCount(0);
    await accessible(page);
    await page.getByRole('link', { name: 'Retour aux patients' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
  });

  test('warns before leaving unsaved input, keeps the values on "Rester" and drops them on "Abandonner"', async ({ page }) => {
    await stubApi(page, { contexts: [tilleuls, canal] });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Brouillon', prenom: 'Test' });
    await page.getByRole('button', { name: 'Annuler' }).click();
    const stay = page.getByRole('button', { name: 'Rester sur le formulaire' });
    await expect(page.getByRole('heading', { name: 'Modifications non enregistrées', level: 1 })).toBeVisible();
    await expect(stay).toBeFocused();
    await accessible(page);
    await page.keyboard.press('Escape');
    await expect(heading(page, 'Nouveau dossier patient')).toBeVisible();
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('Brouillon');

    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await expect(page.getByRole('button', { name: 'Rester sur le formulaire' })).toBeFocused();
    await page.getByRole('button', { name: 'Rester sur le formulaire' }).click();
    await page.getByRole('link', { name: 'Patients' }).first().click();
    await expect(page.getByRole('button', { name: 'Abandonner les modifications' })).toBeVisible();
    await page.getByRole('button', { name: 'Abandonner les modifications' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('');
  });

  test('a form without changes leaves without a warning', async ({ page }) => {
    await stubApi(page);
    await page.goto(NEW);
    await page.getByRole('button', { name: 'Annuler' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
  });

  test('a restricted practitioner finds no creation form', async ({ page }) => {
    await stubApi(page, { create: () => ({ status: 403, json: problem(403, 'FORBIDDEN') }) });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Test', prenom: 'Test', j: '01', m: '01', a: '1980' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Création non autorisée' })).toContainText('Demandez à un membre habilité du cabinet.');
  });
});

test.describe('S-08 correction', () => {
  const editPath = `${base}/patients/${claire.recordId}/modifier`;

  test('opens the identity with an identity-only banner and saves with the quoted current version', async ({ page }) => {
    const calls = await stubApi(page, { roster: roster.map((p) => ({ ...p })) });
    await page.goto(editPath);
    await expect(heading(page, 'Modifier l’identité')).toBeFocused();
    await expect(page.locator('.etaye-entete__meta')).toContainText('MARTIN Claire · version 7 ouverte');
    await expect(page.locator('.etaye-banniere')).toContainText('Naissance 18/04/1978');
    await expect(page.locator('.etaye-banniere')).not.toContainText(/allerg/i);
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('Martin');
    await expect(page.getByText('Non renseignée au dossier')).toBeVisible();
    await accessible(page);

    await fillIdentity(page, { prenom: 'Claire-Anne' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    await expect(page.getByText('L’identité de MARTIN Claire-Anne a été enregistrée.')).toBeVisible();
    expect(calls.edits).toHaveLength(1);
    expect(calls.edits[0].match).toBe('"7"');
    expect(calls.edits[0].body.firstName).toBe('Claire-Anne');
  });

  test('a stale version keeps the entered values, compares both versions and saves on the version seen', async ({ page }) => {
    const live = roster.map((p) => ({ ...p }));
    const calls = await stubApi(page, { roster: live });
    await page.goto(editPath);
    await expect(heading(page, 'Modifier l’identité')).toBeFocused();
    const target = live.find((p) => p.id === claire.id)!;
    Object.assign(target, { phone: '0100000000', version: 8, updatedAt: '2026-11-23T14:02:00Z' });
    await fillIdentity(page, { prenom: 'Claire-Anne', tel: '0600000000' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    const conflict = page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' });
    await expect(conflict).toContainText('La version 8 a été enregistrée pendant votre saisie.');
    await expect(page.getByText(/Enregistré · version 8 · \d{2}:\d{2}/)).toBeVisible();
    await expect(page.getByLabel('Prénom')).toHaveValue('Claire-Anne');
    await expect(page.getByLabel('Téléphone')).toHaveValue('0600000000');
    await expect(page.locator('.etaye-comparaison')).toContainText('Téléphone');
    await accessible(page);
    expect(calls.edits[0].match).toBe('"7"');

    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    expect(calls.edits[1].match).toBe('"8"');
    await expect(page.getByRole('status').filter({ hasText: /patients? affichés?/ })).toBeVisible();
  });

  test('"Recharger la version enregistrée" replaces the local input with the saved identity', async ({ page }) => {
    const live = roster.map((p) => ({ ...p }));
    await stubApi(page, { roster: live });
    await page.goto(editPath);
    await expect(heading(page, 'Modifier l’identité')).toBeFocused();
    Object.assign(live.find((p) => p.id === claire.id)!, { firstName: 'Clara', version: 8 });
    await fillIdentity(page, { tel: '0600000000' });
    await page.getByRole('button', { name: 'Enregistrer l’identité' }).click();
    await page.getByRole('button', { name: 'Recharger la version enregistrée' }).click();
    await expect(page.getByLabel('Prénom')).toHaveValue('Clara');
    await expect(page.getByLabel('Téléphone')).toHaveValue('');
    await expect(page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' })).toHaveCount(0);
  });

  test('says a dossier is hidden, refused or unavailable without exposing it', async ({ page }) => {
    let answer: Reply = { status: 404, json: problem(404, 'NOT_FOUND') };
    await stubApi(page, { record: () => answer });
    await page.goto(editPath);
    await expect(page.getByText('Élément introuvable').first()).toBeVisible();
    await expect(page.getByLabel('Nom de naissance')).toHaveCount(0);
    answer = { status: 403, json: problem(403, 'FORBIDDEN') };
    await page.goto(editPath);
    await expect(page.getByText('Modification non autorisée')).toBeVisible();
    answer = { status: 503, json: problem(503, 'UNAVAILABLE') };
    await page.goto(editPath);
    await expect(page.getByText('Le dossier n’a pas pu être chargé')).toBeVisible();
    await accessible(page);
  });
});

test.describe('practitioner shell', () => {
  test('the menu below 760 px announces its state, moves focus, closes with Escape and on choice', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await stubApi(page);
    await page.goto(LIST);
    const menu = page.getByRole('button', { name: 'Ouvrir le menu' });
    await expect(menu).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toBeHidden();
    await menu.click();
    await expect(page.getByRole('button', { name: 'Fermer le menu' })).toHaveAttribute('aria-expanded', 'true');
    const link = page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Patients' });
    await expect(link).toBeFocused();
    await accessible(page);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', { name: 'Ouvrir le menu' })).toBeFocused();
    await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toBeHidden();

    await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
    await link.click();
    await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toBeHidden();
    await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
    await page.setViewportSize({ width: 1024, height: 768 });
    await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toBeVisible();
    await expect(page.getByRole('button', { name: /le menu/ })).toBeHidden();
  });

  test('offers only the Patients destination, the account and the sign-out', async ({ page }) => {
    await stubApi(page);
    await page.goto(LIST);
    const nav = page.getByRole('navigation', { name: 'Navigation principale' });
    await expect(nav.getByRole('link')).toHaveCount(1);
    await expect(nav.getByRole('link', { name: 'Patients' })).toHaveAttribute('aria-current', 'page');
    await expect(page.locator('header')).toContainText('Nora Lefebvre');
    await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible();
  });

  test('the practice home links to the directory for a clinical grant only', async ({ page }) => {
    await stubApi(page, { contexts: [tilleuls] });
    await page.goto(base);
    await page.getByRole('link', { name: 'Patients' }).click();
    await expect(page).toHaveURL(new RegExp(`${LIST}$`));
    await stubApi(page, { contexts: [admin] });
    await page.goto(`/compte/pratiques/${admin.grantId}`);
    await expect(page.getByRole('heading', { name: 'Votre espace de travail' })).toBeFocused();
    await expect(page.getByRole('link', { name: 'Patients' })).toHaveCount(0);
  });
});

test.describe('states in both themes and four widths', () => {
  const widths = [[1440, 1024], [1024, 768], [390, 844], [320, 844]] as const;
  const screens: [string, string, (page: Page) => Promise<void>][] = [
    ['list', LIST, async (page) => { await expect(page.locator('.etaye-lignes__titre').first()).toBeVisible(); }],
    ['form', NEW, async (page) => { await expect(page.getByLabel('Nom de naissance')).toBeVisible(); }],
  ];
  for (const [name, path, ready] of screens) {
    test(`${name} keeps the shell backgrounds without overflow or violation`, async ({ page }) => {
      await stubApi(page);
      for (const scheme of ['light', 'dark'] as const) {
        await page.emulateMedia({ colorScheme: scheme });
        for (const [width, height] of widths) {
          await page.setViewportSize({ width, height });
          await page.goto(path);
          await ready(page);
          expect(await usesBackground(page, '.etaye-app', '--fond')).toBe(true);
          expect(await usesBackground(page, '.etaye-top', '--fond')).toBe(true);
          expect(await usesBackground(page, '.etaye-main', '--surface')).toBe(true);
          await noRingOnHeading(page);
          await noOverflow(page);
          if (width === 390 || width === 1440) await accessible(page);
        }
      }
    });
  }

  test('keyboard focus shows a 2 px ring on controls and Tab order reaches the form in reading order', async ({ page }) => {
    await stubApi(page);
    await page.goto(NEW);
    await expect(heading(page, 'Nouveau dossier patient')).toBeFocused();
    const order: string[] = [];
    for (let i = 0; i < 9; i += 1) {
      await page.keyboard.press('Tab');
      const info = await page.evaluate(() => {
        const element = document.activeElement as HTMLElement;
        const style = getComputedStyle(element);
        return { label: element.getAttribute('aria-label') || element.id || element.textContent?.trim() || element.tagName,
          style: style.outlineStyle, width: style.outlineWidth, visible: element.matches(':focus-visible') };
      });
      expect(info).toMatchObject({ style: 'solid', width: '2px', visible: true });
      order.push(info.label);
    }
    expect(order.indexOf('i-nom')).toBeLessThan(order.indexOf('i-pre'));
    expect(order.indexOf('i-pre')).toBeLessThan(order.indexOf('dn-j'));
    expect(order.indexOf('dn-j')).toBeLessThan(order.indexOf('dn-m'));
    expect(order.indexOf('dn-a2')).toBeLessThan(order.indexOf('i-tel'));
  });
});

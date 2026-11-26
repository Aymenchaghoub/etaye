import { AxeBuilder } from '@axe-core/playwright';
import { expect, test, type Page, type Route } from './support/browser-coverage';
import type { AccessContext, Session } from '../src/api/generated/types.gen';
import { OUTSIDE_PATH, PRACTICE_A, READ_PATH, RECORD_A, RECORD_B } from '../src/isolation/isolation-journey';

const version = 'etaye-design-v1';
const meta = {
  apiVersion: 'v1', minimumClientVersion: '0.0.1', latestClientVersion: '0.0.1',
  demoDatasetVersion: version, demoResetGeneration: 1,
};
const ids = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const problem = (status: number, code: string) => ({
  type: 'about:blank', title: code, status, code, detail: 'fictional', correlationId: 'c-1',
});
const noraContext: AccessContext = {
  kind: 'MEMBERSHIP', grantId: ids(1), practiceId: PRACTICE_A, practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL',
};
const milaContext: AccessContext = { ...noraContext, grantId: ids(2), displayRole: 'ADMINISTRATION' };
const claireContext: AccessContext = { ...noraContext, kind: 'PORTAL', grantId: ids(3), displayRole: 'PATIENT' };
const elsewhere: AccessContext = {
  ...noraContext, grantId: ids(4), practiceId: ids(40), practiceName: 'Cabinet du Canal',
};
const record = {
  id: RECORD_A, version: 0, createdAt: '2026-11-02T15:00:00Z', updatedAt: '2026-11-02T15:00:00Z', practiceId: PRACTICE_A,
  patientId: ids(50), recordRevision: 8,
  patient: {
    id: ids(50), version: 0, createdAt: '2026-11-02T15:00:00Z', updatedAt: '2026-11-02T15:00:00Z',
    practiceId: PRACTICE_A, firstName: 'Claire', lastName: 'Martin', dateOfBirth: '1978-04-18',
    contactEmail: null, phone: null, recordId: RECORD_A,
  },
};

function snapshot(contexts: AccessContext[], name = 'Nora Lefebvre', account = ids(100)): Session {
  const time = new Date().toISOString();
  return {
    account: {
      id: account, displayName: name, email: 'actor@example.test', state: 'ACTIVE', locale: 'fr-FR',
      version: 0, createdAt: time, updatedAt: time,
    },
    membershipIds: contexts.filter(({ kind }) => kind === 'MEMBERSHIP').map(({ grantId }) => grantId),
    portalLinkIds: contexts.filter(({ kind }) => kind === 'PORTAL').map(({ grantId }) => grantId),
    expiresAt: new Date(Date.now() + 90_000).toISOString(),
    contexts,
  };
}

type Reply = (route: Route, url: URL) => Promise<void> | void;
type Stub = { reads: string[]; setSession: (next: Session | undefined) => void; setRead: (next: Reply) => void };

/** Stubs the API of one fictional actor and records every record read the page performs. */
async function stub(page: Page, contexts: AccessContext[], read: Reply, name?: string): Promise<Stub> {
  let session: Session | undefined = snapshot(contexts, name);
  let answer = read;
  const reads: string[] = [];
  await page.addInitScript((entry) => sessionStorage.setItem('etaye-demo-entry', JSON.stringify(entry)),
    { version, generation: 1, actor: 'Dr Nora Lefebvre', email: 'nora.lefebvre@example.test' });
  await page.route('**/api/v1/meta', (route) => route.fulfill({ json: meta }));
  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { token: 'fictional-csrf' } }));
  await page.route('**/api/v1/session', (route) => {
    if (route.request().method() === 'POST') return route.fulfill({ json: session });
    return session ? route.fulfill({ json: session }) : route.fulfill({ status: 401, json: problem(401, 'UNAUTHORIZED') });
  });
  await page.route('**/api/v1/practices/**', async (route) => {
    const url = new URL(route.request().url());
    reads.push(url.pathname);
    await answer(route, url);
  });
  return { reads, setSession: (next) => { session = next; }, setRead: (next) => { answer = next; } };
}

const allowed: Reply = (route) => route.fulfill({ json: record, headers: { ETag: '"0"' } });
const hidden: Reply = (route) => route.fulfill({ status: 404, json: problem(404, 'NOT_FOUND') });
const restricted: Reply = (route) => route.fulfill({ status: 403, json: problem(403, 'FORBIDDEN') });

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

/** Buttons and links carry no outline until the keyboard reaches them; then the token-based ring appears. */
async function ringOnlyForKeyboard(page: Page) {
  const outlined = () => page.evaluate(() => [...document.querySelectorAll('a, button')]
    .filter((element) => getComputedStyle(element).outlineStyle !== 'none').map((element) => element.textContent?.trim()));
  expect(await outlined()).toEqual([]);
  await page.keyboard.press('Tab');
  const focused = await page.evaluate(() => {
    const element = document.activeElement as HTMLElement;
    const style = getComputedStyle(element);
    return { match: element.matches(':focus-visible'), style: style.outlineStyle, width: style.outlineWidth };
  });
  expect(focused).toEqual({ match: true, style: 'solid', width: '2px' });
}

const readUrl = (record: string) => `/api/v1/practices/${PRACTICE_A}/records/${record}`;

test.describe('the guided entry in the demonstration', () => {
  const items = [
    ['praticienne', 'Praticienne', 'Dr Nora Lefebvre', 'nora.lefebvre@example.test'],
    ['patiente', 'Patiente', 'Claire Martin', 'claire.martin@example.test'],
    ['administration', 'Administration', 'Mila Dubois', 'mila.dubois@example.test'],
  ].map(([id, roleLabel, actorName, signInIdentifier]) => ({
    id, name: roleLabel, roleLabel, actorName, signInIdentifier, practiceName: 'Cabinet des Tilleuls',
    journeySummary: 'Ouvrir son espace fictif.', limitsNotice: 'Données fictives uniquement.',
    datasetVersion: version, synthetic: true, available: true,
  }));

  async function showcase(page: Page, datasetVersion = version) {
    const current = items.map((item) => ({ ...item, datasetVersion }));
    await page.route('**/api/v1/meta', (route) => route.fulfill({ json: { ...meta, demoDatasetVersion: datasetVersion } }));
    await page.route('**/api/v1/demo/scenarios', (route) => route.fulfill({ json: { items: current, nextCursor: null } }));
    await page.route('**/api/v1/session', (route) => route.fulfill({ status: 401, json: problem(401, 'UNAUTHORIZED') }));
  }

  test('offers the panel after the scenario list and walks through the guide by keyboard', async ({ page }) => {
    await showcase(page);
    await page.setViewportSize({ width: 320, height: 844 });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/demonstration');
    const open = page.getByRole('button', { name: 'Ouvrir le parcours' });
    await expect(open).toBeVisible();
    const order = await page.evaluate(() => {
      const list = document.querySelector('.etaye-showcase__list')!;
      const panel = document.querySelector('.etaye-isolation-panel')!;
      return Boolean(list.compareDocumentPosition(panel) & Node.DOCUMENT_POSITION_FOLLOWING);
    });
    expect(order).toBe(true);
    await noOverflow(page);
    await accessible(page);

    await open.focus();
    await page.keyboard.press('Enter');
    const heading = page.getByRole('heading', { name: 'Prouvez l’isolation', level: 1 });
    await expect(heading).toBeFocused();
    await expect(page.getByRole('listitem')).toHaveCount(3);
    await expect(page.getByRole('link', { name: 'Commencer avec Nora' })).toHaveAttribute('href', `/connexion?retour=${encodeURIComponent(READ_PATH)}`);
    await expect(page.getByRole('link', { name: 'Mila' })).toHaveAttribute('href', `/connexion?retour=${encodeURIComponent(READ_PATH)}`);
    await expect(page.getByRole('link', { name: 'Claire' })).toHaveAttribute('href', `/connexion/patient?retour=${encodeURIComponent(READ_PATH)}`);
    expect(await usesBackground(page, '.etaye-demo-page', '--fond')).toBe(true);
    await noOverflow(page);
    await accessible(page);

    await page.keyboard.press('Escape');
    await expect(heading).toHaveCount(0);
    await expect(open).toBeFocused();
    await open.click();
    await page.getByRole('button', { name: 'Revenir à la démonstration' }).click();
    await expect(open).toBeFocused();
    // Returned to by a mouse click, the action is focused without the keyboard ring.
    expect(await open.evaluate((element) => getComputedStyle(element).outlineStyle)).toBe('none');
  });

  test('keeps the entry hidden for another fixture version and the existing cards untouched', async ({ page }) => {
    await showcase(page, 'autre-jeu');
    await page.goto('/demonstration');
    await expect(page.getByRole('button', { name: /^Choisir · / })).toHaveCount(3);
    await expect(page.getByRole('button', { name: 'Ouvrir le parcours' })).toHaveCount(0);
  });

  test('starts the journey with the existing sign-in and returns to the step after authentication', async ({ page }) => {
    await showcase(page);
    await page.goto('/demonstration');
    await page.getByRole('button', { name: 'Ouvrir le parcours' }).click();
    await page.getByRole('link', { name: 'Commencer avec Nora' }).click();
    await expect(page).toHaveURL(/\/connexion\?retour=/);
    await expect(page.getByLabel('Adresse e-mail')).toHaveValue('nora.lefebvre@example.test');
    await expect(page.locator('input[name="password"]')).toHaveValue('');

    const actor = await stub(page, [noraContext], allowed);
    await page.locator('input[name="password"]').fill('Etaye-Jeu-Fictif-91!');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page).toHaveURL(new RegExp(`${READ_PATH}$`));
    await expect(page.getByRole('heading', { name: 'Dossier accessible' })).toBeFocused();
    expect(actor.reads).toContain(readUrl(RECORD_A));
  });
});

test.describe('the replayed read', () => {
  test('shows the record of the authorized practitioner, then the same generic refusal outside the practice', async ({ page }) => {
    const actor = await stub(page, [noraContext], (route, url) => url.pathname.endsWith(RECORD_A) ? allowed(route, url) : hidden(route, url));
    await page.goto(READ_PATH);
    const heading = page.getByRole('heading', { name: 'Dossier accessible' });
    await expect(heading).toBeFocused();
    const main = page.getByTestId('isolation-journey');
    await expect(main).toContainText('Claire Martin');
    await expect(main.locator('time')).toHaveText('18 avril 1978');
    await expect(main).toContainText('Cabinet des Tilleuls');
    await expect(page.locator('header')).toContainText('Praticien · Nora Lefebvre');
    await expect(main.locator('[data-isolation-case]')).toHaveAttribute('data-isolation-case', 'REC-A-ALLOWED');
    await expect(main.locator('[data-http-status]')).toHaveAttribute('data-http-status', '200');
    // Nothing identifies the record or its practice in the rendered page or its address.
    expect(page.url()).not.toContain(RECORD_A);
    expect(await page.content()).not.toContain(RECORD_A);
    await accessible(page);

    await page.getByRole('button', { name: 'Voir le refus hors du cabinet' }).click();
    await expect(page).toHaveURL(new RegExp(`${OUTSIDE_PATH}$`));
    await expect(page.getByRole('heading', { name: 'Élément introuvable' })).toBeFocused();
    await expect(page.getByTestId('isolation-journey')).not.toContainText('Claire');
    await expect(main.locator('[data-isolation-case]')).toHaveAttribute('data-isolation-case', 'REC-HIDDEN-FOREIGN-ID');
    // StrictMode can repeat a read in development; only the two addressed targets may ever be requested.
    expect([...new Set(actor.reads)]).toEqual([readUrl(RECORD_A), readUrl(RECORD_B)]);
    const body = await page.getByTestId('isolation-journey').innerText();
    expect(body).not.toMatch(/Hugo|Petit|Canal|1986/);
    expect(await page.content()).not.toContain(RECORD_B);
    expect(page.url()).not.toContain(RECORD_B);
    await accessible(page);

    await page.getByRole('button', { name: 'Revenir à mon espace' }).click();
    await expect(page).toHaveURL(new RegExp(`/compte/pratiques/${noraContext.grantId}$`));
    await expect(page.getByRole('heading', { name: 'Votre espace de travail' })).toBeVisible();
  });

  test('refuses the administrator in the visible practice without a clinical page', async ({ page }) => {
    await stub(page, [milaContext], restricted, 'Mila Dubois');
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Cet espace est réservé aux soins' })).toBeFocused();
    const main = page.getByTestId('isolation-journey');
    await expect(main).toContainText('Accès non autorisé · 403');
    await expect(main).toContainText('Votre rôle d’administration au Cabinet des Tilleuls ne donne pas accès aux dossiers patients.');
    await expect(main).not.toContainText('Claire');
    await expect(main.locator('[data-isolation-case]')).toHaveAttribute('data-isolation-case', 'ADM-NO-CLINICAL');
    await expect(page.locator('header')).toContainText('Administration · Mila Dubois');
    await accessible(page);
  });

  test('shows the patient layout and one generic refusal for the clinical item', async ({ page }) => {
    await stub(page, [claireContext], hidden, 'Claire Martin');
    await page.goto(READ_PATH);
    const heading = page.getByRole('heading', { name: 'Cette page n’est pas disponible' });
    await expect(heading).toBeFocused();
    const main = page.getByTestId('isolation-journey');
    await expect(main).toContainText('Elle n’existe pas ou ne vous a pas été partagée.');
    await expect(main).not.toContainText('403');
    await expect(main.locator('[data-isolation-case]')).toHaveAttribute('data-isolation-case', 'PAT-OWN-RECORD-STAFF-ROUTE');
    await expect(page.getByRole('img', { name: 'Étaye — Espace patient' }).first()).toBeAttached();
    expect(await heading.evaluate((node) => getComputedStyle(node).fontSize)).toBe('28px');
    const button = page.getByRole('button', { name: 'Revenir à mon espace' });
    expect(await button.evaluate((node) => node.getBoundingClientRect().height)).toBeGreaterThanOrEqual(48);
    await accessible(page);
    await button.click();
    await expect(page).toHaveURL(new RegExp(`/compte/patient/${claireContext.grantId}$`));
  });

  test('treats an old link of the namespace as the same generic refusal and sends no request', async ({ page }) => {
    const actor = await stub(page, [noraContext], allowed);
    await page.goto('/compte/isolation/ancien-lien');
    await expect(page.getByRole('heading', { name: 'Élément introuvable' })).toBeFocused();
    await expect(page.getByTestId('isolation-journey')).toContainText('Vérifiez le lien ou revenez à votre espace.');
    expect(actor.reads).toEqual([]);
    await accessible(page);
  });

  test('hides the previous result while the access is being verified and recovers from a failure', async ({ page }) => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    let healthy = false;
    await stub(page, [noraContext], async (route, url) => {
      await gate;
      if (!healthy) return route.fulfill({ status: 503, json: problem(503, 'SERVICE_UNAVAILABLE') });
      return allowed(route, url);
    });
    await page.goto(READ_PATH);
    const status = page.getByRole('status');
    await expect(status).toContainText('Vérification de l’accès en cours…');
    await expect(page.getByRole('heading', { name: 'Vérification de l’accès' })).toBeFocused();
    await expect(page.getByTestId('isolation-journey')).not.toContainText('Claire Martin');
    release();

    await expect(page.getByRole('heading', { name: 'Vérification momentanément indisponible' })).toBeFocused();
    await expect(page.getByRole('alert')).toContainText('La vérification de l’accès a échoué.');
    await accessible(page);
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByRole('heading', { name: /Impossible de vérifier votre accès à cette étape/ })).toBeFocused();
    await noOverflow(page);
    await accessible(page);
    healthy = true;
    await page.getByRole('button', { name: 'Réessayer la vérification de l’accès' }).click();
    await expect(page.getByRole('heading', { name: 'Dossier accessible' })).toBeFocused();
  });

  test('never renders an unexpected successful answer', async ({ page }) => {
    await stub(page, [noraContext], allowed);
    await page.goto(OUTSIDE_PATH);
    await expect(page.getByRole('heading', { name: 'Vérification momentanément indisponible' })).toBeVisible();
    await expect(page.getByTestId('isolation-journey')).not.toContainText('Claire');
    expect(await page.content()).not.toContain('1978');
  });

  test('keeps long French practice and account names readable at 320 CSS pixels', async ({ page }) => {
    const long: AccessContext = {
      ...noraContext,
      practiceName: 'Cabinet des Tilleuls — Maison de santé pluriprofessionnelle de Saint-Martin-des-Rives et du Val-de-Loire',
    };
    await stub(page, [long], allowed, 'Nora Lefebvre-Castelnau de la Fontaine-Saint-Georges');
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Dossier accessible' })).toBeFocused();
    await expect(page.getByTestId('isolation-journey')).toContainText('Maison de santé pluriprofessionnelle');
    await noOverflow(page);
    for (const name of ['Voir le refus hors du cabinet', 'Revenir à la démonstration']) {
      const box = await page.getByRole(name.startsWith('Voir') ? 'button' : 'link', { name }).boundingBox();
      expect(box && box.x >= 0 && box.x + box.width <= 320).toBe(true);
    }
  });
});

test.describe('states in both themes and six widths', () => {
  // 720×512 and 512×384 are the CSS viewports of a 200 % browser zoom on 1440×1024 and 1024×768.
  const widths = [[1440, 1024], [1024, 768], [720, 512], [512, 384], [390, 844], [320, 568]] as const;
  const cases: [string, AccessContext[], Reply, string][] = [
    ['allowed', [noraContext], allowed, 'Dossier accessible'],
    ['restricted', [milaContext], restricted, 'Cet espace est réservé aux soins'],
    ['hidden', [noraContext], hidden, 'Élément introuvable'],
    ['patient', [claireContext], hidden, 'Cette page n’est pas disponible'],
  ];

  for (const [name, contexts, reply, heading] of cases) {
    test(`${name} keeps the context shell and the page background without overflow or violation`, async ({ page }) => {
      await stub(page, contexts, reply);
      for (const scheme of ['light', 'dark'] as const) {
        await page.emulateMedia({ colorScheme: scheme });
        for (const [width, height] of widths) {
          await page.setViewportSize({ width, height });
          await page.goto(name === 'hidden' ? OUTSIDE_PATH : READ_PATH);
          await expect(page.getByRole('heading', { name: heading, exact: true })).toBeFocused();
          expect(await usesBackground(page, '.etaye-context-destination', '--fond')).toBe(true);
          await expect(page.locator('header .etaye-contexte')).toBeVisible();
          if (width === 1024) await ringOnlyForKeyboard(page);
          await noOverflow(page);
          if (width === 390 || width === 1440) await accessible(page);
        }
      }
    });
  }
});

test.describe('session return for the journey', () => {
  test('an expired read offers reconnection, rechecks the same step and restores it for the same account', async ({ page }) => {
    let expired = true;
    const actor = await stub(page, [noraContext], (route, url) => expired
      ? route.fulfill({ status: 401, json: problem(401, 'UNAUTHORIZED') }) : allowed(route, url));
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeFocused();
    const main = page.getByRole('main');
    await expect(main).toContainText('Reconnectez-vous pour vérifier votre accès à cette étape.');
    await expect(main).not.toContainText(/note|publication|brouillon|Claire/i);
    await expect(page.getByRole('button', { name: 'Quitter la session' })).toBeVisible();
    await accessible(page);

    expired = false;
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    await page.unroute('**/api/v1/session');
    await page.route('**/api/v1/session', async (route) => {
      if (route.request().method() === 'POST') return route.fulfill({ json: snapshot([noraContext]) });
      await gate;
      return route.fulfill({ json: snapshot([noraContext]) });
    });
    await page.getByRole('button', { name: 'Se reconnecter' }).click();
    await page.locator('input[name="password"]').fill('Etaye-Jeu-Fictif-91!');
    await page.getByLabel('Adresse e-mail').fill('nora.lefebvre@example.test');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page.getByRole('heading', { name: 'Vérification après reconnexion' })).toBeFocused();
    await expect(page.getByRole('status')).toContainText('Vérification de votre accès au parcours…');
    await expect(page.getByText('Claire Martin')).toHaveCount(0);
    await accessible(page);
    release();
    await expect(page.getByRole('heading', { name: 'Dossier accessible' })).toBeFocused();
    await expect(page).toHaveURL(new RegExp(`${READ_PATH}$`));
    expect(actor.reads.length).toBeGreaterThanOrEqual(2);
  });

  test('another account after reconnection never inherits the step', async ({ page }) => {
    await stub(page, [noraContext], (route) => route.fulfill({ status: 401, json: problem(401, 'UNAUTHORIZED') }));
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeVisible();
    await page.unroute('**/api/v1/session');
    await page.route('**/api/v1/session', (route) => {
      const other = snapshot([milaContext], 'Mila Dubois', ids(101));
      return route.request().method() === 'POST' ? route.fulfill({ json: other }) : route.fulfill({ status: 401, json: problem(401, 'UNAUTHORIZED') });
    });
    await page.getByRole('button', { name: 'Se reconnecter' }).click();
    await page.getByLabel('Adresse e-mail').fill('mila.dubois@example.test');
    await page.locator('input[name="password"]').fill('Etaye-Jeu-Fictif-91!');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page.getByRole('heading', { name: 'Cette étape n’est plus disponible' })).toBeFocused();
    await expect(page.getByRole('main')).toContainText('Revenez à la démonstration pour reprendre avec un compte et un espace autorisés.');
    await expect(page.getByText('Mila')).toHaveCount(0);
    await accessible(page);
  });

  test('an account without a grant in the fictional practice or another fixture gets the unavailable return', async ({ page }) => {
    await stub(page, [elsewhere], allowed);
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Cette étape n’est plus disponible' })).toBeFocused();
    await expect(page.getByRole('link', { name: 'Revenir à la démonstration' })).toHaveAttribute('href', '/demonstration');
    await expect(page.getByText('Cabinet du Canal')).toHaveCount(0);

    const other = await page.context().newPage();
    await other.route('**/api/v1/meta', (route) => route.fulfill({ json: { ...meta, demoDatasetVersion: 'autre-jeu' } }));
    await other.route('**/api/v1/session', (route) => route.fulfill({ json: snapshot([noraContext]) }));
    await other.addInitScript((entry) => sessionStorage.setItem('etaye-demo-entry', JSON.stringify(entry)),
      { version: 'autre-jeu', generation: 1 });
    await other.goto(READ_PATH);
    await expect(other.getByRole('heading', { name: 'Cette étape n’est plus disponible' })).toBeVisible();
  });

  test('a capability withdrawn while the page is open replaces the record at the next periodic check', async ({ page }) => {
    let withdrawn = false;
    await stub(page, [noraContext], (route, url) => withdrawn ? restricted(route, url) : allowed(route, url));
    await page.clock.install();
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Dossier accessible' })).toBeVisible();
    withdrawn = true;
    await page.clock.fastForward(21_000);
    await expect(page.getByRole('heading', { name: 'Cet espace est réservé aux soins' })).toBeVisible();
    await expect(page.getByText('Claire Martin')).toHaveCount(0);
  });

  test('an answer is never shown under another account when the cookie changed during the read', async ({ page }) => {
    await stub(page, [noraContext], allowed);
    let reads = 0;
    await page.unroute('**/api/v1/session');
    await page.route('**/api/v1/session', (route) => {
      // The first status read belongs to the gate; the confirmation after the record read finds another account.
      reads++;
      return route.fulfill({ json: reads === 1 ? snapshot([noraContext]) : snapshot([milaContext], 'Mila Dubois', ids(101)) });
    });
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Cette étape n’est plus disponible' })).toBeFocused();
    await expect(page.getByText('Claire Martin')).toHaveCount(0);
    await expect(page.getByText('Nora Lefebvre')).toHaveCount(0);
  });

  test('a patient who reconnects returns to the patient sign-in', async ({ page }) => {
    await stub(page, [claireContext], (route) => route.fulfill({ status: 401, json: problem(401, 'UNAUTHORIZED') }), 'Claire Martin');
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeFocused();
    await page.getByRole('button', { name: 'Se reconnecter' }).click();
    await expect(page.getByRole('heading', { name: 'Se connecter à votre espace patient' })).toBeVisible();
  });

  test('a revoked grant while the step is open removes the result at the next check', async ({ page }) => {
    const actor = await stub(page, [noraContext], allowed);
    await page.goto(READ_PATH);
    await expect(page.getByRole('heading', { name: 'Dossier accessible' })).toBeVisible();
    actor.setSession(snapshot([elsewhere]));
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    await page.evaluate(() => window.dispatchEvent(new Event('pageshow')));
    await expect(page.getByRole('heading', { name: 'Cette étape n’est plus disponible' })).toBeVisible();
    await expect(page.getByText('Claire Martin')).toHaveCount(0);
  });
});

import { AxeBuilder } from '@axe-core/playwright';
import { expect, test, type Page } from './support/browser-coverage';
import type { InvitationPreview, Session } from '../src/api/generated/types.gen';

const fragment = 'T'.repeat(43);
const password = 'Une phrase fictive';
const practice = 'Cabinet des Tilleuls';
const portalLink = '00000000-0000-4000-8000-000000000002';
const otherLink = '00000000-0000-4000-8000-000000000001';
const url = `/invitation#${fragment}`;

function preview(purpose: InvitationPreview['purpose'], eligibility: InvitationPreview['eligibility'] = 'USABLE') {
  return {
    purpose, eligibility, practiceName: practice, recipientHint: 'a•••@example.test',
    expiresAt: '2026-11-05T11:00:00Z',
    acceptedAt: eligibility === 'ALREADY_USED' ? '2026-11-03T11:00:00Z' : undefined,
  } satisfies InvitationPreview;
}

function session(): Session {
  return {
    account: {
      id: '00000000-0000-4000-8000-000000000003', displayName: 'Marie Collet',
      email: 'marie.collet@example.test', createdAt: '2026-11-03T11:00:00Z', updatedAt: '2026-11-03T11:00:00Z',
      version: 0, state: 'ACTIVE', locale: 'fr-FR',
    },
    membershipIds: [], portalLinkIds: [otherLink, portalLink],
    contexts: [otherLink, portalLink].map((grantId) => ({
      kind: 'PORTAL', displayRole: 'PATIENT', grantId, practiceId: portalLink, practiceName: practice,
    })),
    expiresAt: new Date(Date.now() + 120_000).toISOString(),
  };
}

async function stubInvitation(page: Page, view?: InvitationPreview) {
  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } }));
  await page.route('**/api/v1/invitations/inspect', (route) => {
    const request = route.request();
    expect(request.method()).toBe('POST');
    expect(request.url()).not.toContain(fragment);
    expect(request.headers()['x-csrf-token']).toBe('test-csrf');
    expect(request.postDataJSON()).toEqual({ token: fragment });
    return view ? route.fulfill({ json: view }) : route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
  });
  await page.route('**/api/v1/session', (route) => route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } }));
}

async function axe(page: Page) {
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.passes.length).toBeGreaterThan(0);
  expect(result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(({ target }) => target) }))).toEqual([]);
}

async function accessibility(page: Page) {
  const heading = page.getByRole('heading', { level: 1 });
  await expect(heading).toBeFocused();
  await expect(heading).toHaveCSS('outline-style', 'none');
  await axe(page);
}

test('S-03 team preview keeps the token off URLs and focuses the password confirmation error', async ({ page }) => {
  await stubInvitation(page, preview('TEAM_MEMBERSHIP'));
  await page.goto(url);
  await expect(page.getByRole('heading', { name: /Rejoindre.*Cabinet des Tilleuls/ })).toBeVisible();
  await expect(page).toHaveURL(/\/invitation$/);
  await expect(page.getByText('a•••@example.test')).toBeVisible();
  await accessibility(page);
  await page.keyboard.press('Tab');
  const displayName = page.getByLabel('Nom affiché');
  await expect(displayName).toBeFocused();
  await expect(displayName).toHaveCSS('outline-style', 'solid');
  await page.keyboard.type('Alex Rivière');
  await page.keyboard.press('Tab');
  await expect(page.locator('#a-pw')).toBeFocused();
  await page.keyboard.type(password);
  await page.keyboard.press('Tab');
  await page.keyboard.type('Une autre phrase fictive');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('alert')).toBeFocused();
  await expect(page.getByRole('alert')).toContainText('1 champ à corriger');
  await expect(page.locator('#a-pw')).toHaveValue('');
  await expect(page.locator('#a-pw-confirm')).toHaveValue('');
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations).toEqual([]);
  await page.getByRole('link', { name: /Confirmation : les deux mots de passe/ }).press('Enter');
  await expect(page.getByLabel('Confirmer le mot de passe')).toBeFocused();

  await page.unroute('**/api/v1/invitations/inspect');
  await page.route('**/api/v1/invitations/inspect', (route) =>
    route.fulfill({ json: preview('TEAM_MEMBERSHIP', 'REVOKED') }));
  await page.evaluate((token) => { window.location.hash = token; }, fragment);
  await expect(page.getByRole('heading', { name: "Cette invitation n'est plus valable" })).toBeFocused();
  await expect(page).toHaveURL(/\/invitation$/);
});

const terminalStates = [
  { name: 'team expired', purpose: 'TEAM_MEMBERSHIP', state: 'EXPIRED', heading: 'Cette invitation a expiré' },
  { name: 'team revoked', purpose: 'TEAM_MEMBERSHIP', state: 'REVOKED', heading: "Cette invitation n'est plus valable" },
  { name: 'team already used', purpose: 'TEAM_MEMBERSHIP', state: 'ALREADY_USED', heading: "Cette invitation vient d'être utilisée" },
  { name: 'practice suspended', purpose: 'TEAM_MEMBERSHIP', state: 'UNAVAILABLE', heading: 'Cette invitation est indisponible' },
  { name: 'unknown', heading: "Lien d'invitation invalide" },
  { name: 'portal expired', purpose: 'PATIENT_PORTAL', state: 'EXPIRED', heading: "Ce lien d'activation a expiré" },
  { name: 'portal already active', purpose: 'PATIENT_PORTAL', state: 'ALREADY_USED', heading: 'Votre espace patient est déjà activé' },
] as const;

for (const state of terminalStates) {
  test(`terminal invitation renders its recovery action and passes axe (${state.name})`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 844 });
    await page.emulateMedia({ colorScheme: 'dark' });
    await stubInvitation(page, 'purpose' in state ? preview(state.purpose, state.state) : undefined);
    await page.goto(url);
    await expect(page.getByRole('heading', { name: state.heading })).toBeVisible();
    await expect(page.getByRole('textbox')).toHaveCount(0);
    await expect(page.getByText('a•••@example.test')).toHaveCount(0);
    await accessibility(page);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(320);
    if (state.name === 'portal expired' || state.name === 'practice suspended') {
      await page.keyboard.press('Tab');
      await page.keyboard.press('Enter');
      await expect(page.getByText(/Utilisez les coordonnées de votre cabinet/)).toBeVisible();
    } else {
      await expect(page.getByRole('link', { name: /connexion|connecter/ })).toHaveAttribute('href', /^\/connexion/);
    }
  });
}

test('I-03 verifies birth date, preserves a generic refusal and opens the exact activated portal', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stubInvitation(page, preview('PATIENT_PORTAL'));
  let accepted = false;
  let requests = 0;
  const keys: string[] = [];
  await page.unroute('**/api/v1/session');
  await page.route('**/api/v1/session', (route) => accepted
    ? route.fulfill({ json: session() })
    : route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } }));
  await page.route('**/api/v1/invitations/accept', (route) => {
    requests += 1;
    keys.push(route.request().headers()['idempotency-key']);
    expect(route.request().url()).not.toContain(fragment);
    expect(route.request().headers()['x-csrf-token']).toBe('test-csrf');
    expect(route.request().postDataJSON()).toEqual({
      token: fragment, password, recipientVerification: requests === 1 ? '1991-03-12' : '1990-03-12',
    });
    if (requests === 1) return route.fulfill({ status: 422, json: { code: 'RECIPIENT_MISMATCH' } });
    if (requests === 2) return route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } });
    accepted = true;
    return route.fulfill({ json: { ...session(), activatedPortalLinkId: portalLink } });
  });
  await page.goto(url);
  await expect(page.getByRole('heading', { name: 'Activer mon espace patient' })).toBeVisible();
  await accessibility(page);
  await page.keyboard.press('Tab');
  await expect(page.getByLabel('Votre date de naissance')).toBeFocused();
  await page.getByLabel('Votre date de naissance').fill('12/03/1991');
  await page.getByLabel('Créer un mot de passe').fill(password);
  await page.getByRole('button', { name: 'Activer mon espace' }).click();
  await expect(page.getByRole('alert')).toContainText('Activation impossible avec ces informations');
  await expect(page.getByLabel('Votre date de naissance')).toHaveValue('12/03/1991');
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations).toEqual([]);
  await page.getByLabel('Votre date de naissance').fill('12/03/1990');
  await page.getByLabel('Créer un mot de passe').fill(password);
  await page.getByRole('button', { name: 'Activer mon espace' }).click();
  await expect(page.getByRole('alert')).toContainText("L'activation n'a pas pu être confirmée");
  await page.getByRole('button', { name: 'Activer mon espace' }).click();
  await expect(page).toHaveURL(new RegExp(`/compte/patient/${portalLink}$`));
  await expect(page.getByTestId('protected-account')).toBeVisible();
  expect(keys[0]).not.toBe(keys[1]);
  expect(keys[1]).toBe(keys[2]);
});

test('S-03 holds the form through throttling and displays a concurrent-use refusal', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 844 });
  await page.emulateMedia({ colorScheme: 'dark' });
  const longName = 'Cabinet interdisciplinaire de la maison de santé des Tilleuls et des Hautes Rives';
  await stubInvitation(page, { ...preview('TEAM_MEMBERSHIP'), practiceName: longName });
  await page.clock.install();
  let attempts = 0;
  await page.route('**/api/v1/invitations/accept', (route) => ++attempts === 1
    ? route.fulfill({ status: 429, headers: { 'Retry-After': '60' }, json: { code: 'RATE_LIMITED' } })
    : route.fulfill({ status: 422, json: { code: 'INVITATION_ALREADY_USED' } }));
  await page.goto(url);
  await page.getByLabel('Nom affiché').fill('Alex Rivière');
  await page.locator('#a-pw').fill(password);
  await page.getByLabel('Confirmer le mot de passe').fill(password);
  const submit = page.getByRole('button', { name: 'Activer mon compte' });
  await page.keyboard.press('Tab');
  await expect(submit).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('alert')).toContainText('Trop de demandes');
  await expect(submit).toBeDisabled();
  await expect(submit).toBeFocused();
  await expect(submit).toHaveCSS('outline-style', 'solid');
  await page.keyboard.press('Enter');
  await axe(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(320);
  expect(attempts).toBe(1);
  await page.clock.fastForward(60_000);
  await expect(submit).toBeEnabled();
  await expect(submit).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: "Cette invitation vient d'être utilisée" })).toBeFocused();
});

test('a missing fragment stops locally and a URL query is not used as a credential', async ({ page }) => {
  let inspected = false;
  await page.route('**/api/v1/invitations/inspect', (route) => {
    inspected = true;
    return route.abort();
  });
  await page.goto(`/invitation?token=${fragment}`);
  await expect(page.getByRole('heading', { name: "Lien d'invitation invalide" })).toBeFocused();
  expect(inspected).toBe(false);
});

test('an existing recipient can sign in without reopening the original invitation', async ({ page }) => {
  await stubInvitation(page, preview('TEAM_MEMBERSHIP'));
  let signedIn = false;
  let hasGrant = false;
  const membershipId = '00000000-0000-4000-8000-000000000004';
  await page.unroute('**/api/v1/session');
  await page.route('**/api/v1/session', (route) => {
    if (route.request().method() === 'POST') signedIn = true;
    return signedIn ? route.fulfill({ json: {
      ...session(), membershipIds: hasGrant ? [membershipId] : [], portalLinkIds: [],
      contexts: hasGrant ? [{ kind: 'MEMBERSHIP', displayRole: 'CLINICAL', grantId: membershipId,
        practiceId: membershipId, practiceName: practice }] : [],
    } })
      : route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } });
  });
  await page.route('**/api/v1/invitations/accept', (route) => {
    if (!signedIn) return route.fulfill({ status: 422, json: { code: 'SIGN_IN_REQUIRED' } });
    expect(route.request().postDataJSON()).toEqual({ token: fragment });
    hasGrant = true;
    return route.fulfill({ json: { ...session(), membershipIds: [membershipId], portalLinkIds: [] } });
  });
  await page.goto(url);
  await page.getByRole('button', { name: 'J’ai déjà un compte' }).click();
  await page.getByRole('button', { name: 'Revenir à l’invitation' }).click();
  await expect(page.getByRole('heading', { name: /Rejoindre/ })).toBeFocused();
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  await expect(page.getByRole('alert')).toContainText('2 champs à corriger');
  await page.getByRole('button', { name: 'J’ai déjà un compte' }).click();
  await page.locator('#l-mail').fill('marie.collet@example.test');
  await page.locator('#l-pw').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('heading', { name: /Rejoindre/ })).toBeFocused();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.locator('#a-pw')).toHaveCount(0);
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  await expect(page).toHaveURL(new RegExp(`/compte/pratiques/${membershipId}$`));
  await expect(page.getByTestId('protected-account')).toBeVisible();
});

test('a lost session restores the new-account fields without losing the patient verification', async ({ page }) => {
  await stubInvitation(page, preview('PATIENT_PORTAL'));
  await page.unroute('**/api/v1/session');
  await page.route('**/api/v1/session', (route) => route.fulfill({ json: session() }));
  await page.route('**/api/v1/invitations/accept', (route) => {
    expect(route.request().postDataJSON()).toEqual({ token: fragment, recipientVerification: '1990-03-12' });
    return route.fulfill({ status: 422, json: { code: 'PASSWORD_TOO_SHORT' } });
  });
  await page.goto(url);
  await page.getByLabel('Votre date de naissance').fill('12/03/1990');
  await expect(page.getByLabel('Créer un mot de passe')).toHaveCount(0);
  await page.getByRole('button', { name: 'Activer mon espace' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Votre session est terminée' })).toBeVisible();
  await expect(page.getByLabel('Votre date de naissance')).toHaveValue('12/03/1990');
  await expect(page.getByLabel('Créer un mot de passe')).toBeVisible();
  await axe(page);
});

test('switching to a new account preserves input and keyboard focus through a sign-out retry', async ({ page }) => {
  await stubInvitation(page, preview('PATIENT_PORTAL'));
  await page.unroute('**/api/v1/session');
  let signOuts = 0;
  let releaseSignOut = () => {};
  const firstSignOut = new Promise<void>((resolve) => { releaseSignOut = resolve; });
  await page.route('**/api/v1/session', async (route) => {
    if (route.request().method() !== 'DELETE') return route.fulfill({ json: session() });
    if (++signOuts === 1) {
      await firstSignOut;
      return route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } });
    }
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/v1/invitations/accept', (route) =>
    route.fulfill({ status: 422, json: { code: 'SIGN_IN_REQUIRED' } }));
  try {
    await page.goto(url);
    const birthDate = page.getByLabel('Votre date de naissance');
    await birthDate.fill('12/03/1990');
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Activer mon espace' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('alert')).toContainText('Connectez-vous pour continuer');
    await page.keyboard.press('Shift+Tab');
    await page.keyboard.press('Shift+Tab');
    const newAccount = page.getByRole('button', { name: 'Continuer avec un nouveau compte' });
    await expect(newAccount).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('status')).toHaveText('Déconnexion en cours…');
    await expect(newAccount).toBeDisabled();
    await expect(newAccount).toBeFocused();
    await page.keyboard.press('Enter');
    await axe(page);
    expect(signOuts).toBe(1);
    releaseSignOut();
    await expect(page.getByRole('alert')).toContainText('La déconnexion a échoué');
    await expect(newAccount).toBeFocused();
    await page.keyboard.press('Enter');
    const passwordField = page.getByLabel('Créer un mot de passe');
    await expect(passwordField).toBeFocused();
    await expect(passwordField).toBeEmpty();
    await expect(birthDate).toHaveValue('12/03/1990');
  } finally {
    releaseSignOut();
  }
});

test('preview reload and pending activation maintain a recovery path for a conflicting link', async ({ page }) => {
  await stubInvitation(page, preview('PATIENT_PORTAL'));
  let releaseInspection = () => {};
  let releaseActivation = () => {};
  const inspection = new Promise<void>((resolve) => { releaseInspection = resolve; });
  const activation = new Promise<void>((resolve) => { releaseActivation = resolve; });
  let inspections = 0;
  let sessionReads = 0;
  let attempts = 0;
  await page.unroute('**/api/v1/invitations/inspect');
  await page.route('**/api/v1/invitations/inspect', async (route) => {
    if (++inspections === 1) {
      await inspection;
      return route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } });
    }
    return route.fulfill({ json: preview('PATIENT_PORTAL') });
  });
  await page.unroute('**/api/v1/session');
  await page.route('**/api/v1/session', (route) => ++sessionReads === 1
    ? route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } })
    : route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } }));
  await page.route('**/api/v1/invitations/accept', async (route) => {
    attempts += 1;
    await activation;
    return route.fulfill({ status: 409, json: { code: 'PORTAL_LINK_EXISTS' } });
  });
  try {
    await page.goto(url);
    await expect(page.getByRole('status')).toContainText("Vérification de l'invitation");
    await axe(page);
    releaseInspection();
    await expect(page.getByRole('alert')).toContainText("La vérification de l'invitation a échoué");
    await axe(page);
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Réessayer' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect.poll(() => inspections).toBe(2);
    await expect(page.getByRole('alert')).toContainText("La vérification de l'invitation a échoué");
    await expect(page.getByLabel('Créer un mot de passe')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Réessayer' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Activer mon espace patient' })).toBeFocused();
    await page.getByLabel('Votre date de naissance').fill('12/03/1990');
    await page.getByLabel('Créer un mot de passe').fill(password);
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Activer mon espace' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('status')).toHaveText('Activation en cours…');
    await expect(page.getByRole('button', { name: 'Activation en cours…' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Activation en cours…' })).toBeFocused();
    await expect(page.getByLabel('Votre date de naissance')).toHaveAttribute('readonly', '');
    await page.keyboard.press('Enter');
    await axe(page);
    expect(attempts).toBe(1);
    releaseActivation();
    await expect(page.getByRole('heading', { name: 'Contactez votre cabinet' })).toBeFocused();
    await expect(page.getByRole('link', { name: 'Me connecter' })).toHaveCount(0);
    await page.keyboard.press('Tab');
    await page.keyboard.press('Enter');
    await expect(page.getByText(/Utilisez les coordonnées de votre cabinet/)).toBeVisible();
    await axe(page);
  } finally {
    releaseInspection();
    releaseActivation();
  }
});

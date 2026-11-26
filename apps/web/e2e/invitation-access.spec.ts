import { execFileSync } from 'node:child_process';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { join } from 'node:path';
import { expect, test, type Page } from '../tests/support/browser-coverage';

test.use({ trace: 'off', screenshot: 'off', video: 'off' });

const container = process.env.ETAYE_E2E_INVITE_DB_CONTAINER;
const captures = process.env.ETAYE_E2E_INVITE_CAPTURES;
const referenceOrigin = process.env.ETAYE_E2E_REFERENCE_ORIGIN;
const practiceName = 'Cabinet des Tilleuls';
const password = 'Une phrase de démonstration';

test.skip(!container, 'Set ETAYE_E2E_INVITE_DB_CONTAINER to the disposable PostgreSQL container.');

type Purpose = 'TEAM_MEMBERSHIP' | 'PATIENT_PORTAL';
type State = 'PENDING' | 'EXPIRED' | 'REVOKED';

function seed(purpose: Purpose, state: State = 'PENDING') {
  if (!container) throw new Error('An isolated invitation database is required');
  const token = randomBytes(32).toString('base64url');
  const digest = createHash('sha256').update(token).digest('hex');
  const ids = { practice: randomUUID(), issuer: randomUUID(), patient: randomUUID(), record: randomUUID() };
  const email = `invite-${randomUUID()}@example.test`;
  const portal = purpose === 'PATIENT_PORTAL';

  execFileSync('docker', ['exec', '-i', container, 'psql', '-U', 'etaye_owner', '-d', 'etaye', '-q', '-v', 'ON_ERROR_STOP=1'], {
    input: `
      BEGIN;
      SELECT set_config('etaye.practice_id', '${ids.practice}', true);
      INSERT INTO practice (id, display_name) VALUES ('${ids.practice}', '${practiceName}');
      INSERT INTO account (id, normalized_email, display_name, password_hash)
      VALUES ('${ids.issuer}', 'issuer-${ids.issuer}@example.test', 'Nora Lefebvre', 'fixture-no-password');
      ${portal ? `INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth)
        VALUES ('${ids.patient}', '${ids.practice}', 'Marie', 'Collet', '1990-03-12');
        INSERT INTO patient_record (id, practice_id, patient_id)
        VALUES ('${ids.record}', '${ids.practice}', '${ids.patient}');` : ''}
      INSERT INTO invitation (practice_id, purpose, intended_recipient, token_digest, issuer_id, state, expires_at, record_id, capabilities)
      VALUES ('${ids.practice}', '${purpose}', '${email}', '${digest}', '${ids.issuer}', '${state}',
        now() + interval '${state === 'EXPIRED' ? '-1 hour' : '72 hours'}',
        ${portal ? `'${ids.record}', '{}'` : "NULL, '{CLINICAL_READ,SCHEDULE_MANAGE}'"});
      COMMIT;
    `,
    stdio: ['pipe', 'ignore', 'pipe'],
  });
  return { token, email };
}

async function compare(page: Page, reference: Page, state: string) {
  if (!captures || !referenceOrigin) return;
  for (const [width, height] of [[1440, 1024], [1024, 768], [768, 1024], [390, 844], [320, 844]]) {
    for (const theme of ['clair', 'sombre'] as const) {
      await page.setViewportSize({ width, height });
      await page.emulateMedia({ colorScheme: theme === 'sombre' ? 'dark' : 'light' });
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
      await reference.setViewportSize({ width, height });
      await reference.goto(`${referenceOrigin}/ecrans/${state}.html?theme=${theme}`);
      await reference.evaluate(() => document.fonts.ready);
      await page.evaluate(() => document.fonts.ready);
      const name = `${state.replace('/', '-')}-${width}-${theme}`;
      await page.screenshot({ path: join(captures, `${name}-app.png`), fullPage: true });
      await reference.screenshot({ path: join(captures, `${name}-ref.png`), fullPage: true });
    }
  }
}

test('real invited team member activates the grant and can sign back in', async ({ page, context }) => {
  const fixture = seed('TEAM_MEMBERSHIP');
  const reference = await context.newPage();
  await page.goto(`/invitation#${fixture.token}`);
  await expect(page.getByRole('heading', { name: /Rejoindre.*Cabinet des Tilleuls/ })).toBeVisible();
  await expect(page).toHaveURL(/\/invitation$/);
  await compare(page, reference, 'S-03/equipe');
  await page.locator('#a-nom').fill('Alex Rivière');
  await page.locator('#a-pw').fill(password);
  await page.locator('#a-pw-confirm').fill('Une autre phrase fictive');
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  await expect(page.getByRole('alert')).toBeFocused();
  await compare(page, reference, 'S-03/erreur');
  await page.locator('#a-pw').fill(password);
  await page.locator('#a-pw-confirm').fill(password);
  const accepted = page.waitForResponse((response) => response.url().endsWith('/invitations/accept'));
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  expect((await (await accepted).json()).membershipIds).toHaveLength(1);
  await expect(page).toHaveURL(/\/compte\/pratiques\/[0-9a-f-]+$/);
  await expect(page.getByTestId('protected-account')).toContainText('Alex Rivière');

  await page.getByRole('button', { name: 'Se déconnecter' }).click();
  await page.locator('#l-mail').fill(fixture.email);
  await page.locator('#l-pw').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByTestId('protected-account')).toContainText('Alex Rivière');

  await page.goto(`/invitation#${fixture.token}`);
  await expect(page.getByRole('heading', { name: "Cette invitation vient d'être utilisée" })).toBeVisible();
  await compare(page, reference, 'S-03/deja-utilisee');
});

test('real patient sees the generic refusal and activates exactly the invited portal', async ({ page, context }) => {
  const fixture = seed('PATIENT_PORTAL');
  const reference = await context.newPage();
  await page.goto(`/invitation#${fixture.token}`);
  await expect(page.getByRole('heading', { name: 'Activer mon espace patient' })).toBeVisible();
  await compare(page, reference, 'I-03/ouvert');
  await page.getByLabel('Votre date de naissance').fill('12/03/1991');
  await page.getByLabel('Créer un mot de passe').fill(password);
  await page.getByRole('button', { name: 'Activer mon espace' }).click();
  await expect(page.getByRole('alert')).toContainText('Activation impossible avec ces informations');
  await compare(page, reference, 'I-03/erreur');

  await page.getByLabel('Votre date de naissance').fill('12/03/1990');
  await page.getByLabel('Créer un mot de passe').fill(password);
  const accepted = page.waitForResponse((response) => response.url().endsWith('/invitations/accept'));
  await page.getByRole('button', { name: 'Activer mon espace' }).click();
  const session = await (await accepted).json();
  expect(session.membershipIds).toEqual([]);
  expect(session.portalLinkIds).toHaveLength(1);
  expect(session.activatedPortalLinkId).toBe(session.portalLinkIds[0]);
  await expect(page).toHaveURL(new RegExp(`/compte/patient/${session.activatedPortalLinkId}$`));
  await expect(page.getByTestId('protected-account')).toContainText('Marie Collet');

  await page.goto(`/invitation#${fixture.token}`);
  await expect(page.getByRole('heading', { name: 'Votre espace patient est déjà activé' })).toBeVisible();
  await compare(page, reference, 'I-03/deja-active');
});

test('real invitation restores account creation when another tab signs out', async ({ page, context }) => {
  test.setTimeout(30_000);
  const team = seed('TEAM_MEMBERSHIP');
  const patient = seed('PATIENT_PORTAL');
  await page.goto(`/invitation#${team.token}`);
  await page.getByLabel('Nom affiché').fill('Alex Rivière');
  await page.locator('#a-pw').fill(password);
  await page.getByLabel('Confirmer le mot de passe').fill(password);
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  await expect(page.getByTestId('protected-account')).toContainText('Alex Rivière');

  const otherTab = await context.newPage();
  await otherTab.goto(page.url());
  await page.goto(`/invitation#${patient.token}`);
  const birthDate = page.getByLabel('Votre date de naissance');
  const newPassword = page.getByLabel('Créer un mot de passe');
  await birthDate.fill('12/03/1990');
  await expect(newPassword).toHaveCount(0);
  await otherTab.getByRole('button', { name: 'Se déconnecter' }).click();
  await expect(otherTab.getByRole('heading', { name: 'Se connecter' })).toBeVisible();

  const activate = page.getByRole('button', { name: 'Activer mon espace' });
  await activate.click();
  await expect(page.getByRole('status').filter({ hasText: 'Votre session est terminée' })).toBeVisible();
  await expect(birthDate).toHaveValue('12/03/1990');
  await expect(newPassword).toBeEmpty();
  await newPassword.fill(password);
  await activate.click();
  await expect(page).toHaveURL(/\/compte\/patient\/[0-9a-f-]+$/);
  await expect(page.getByTestId('protected-account')).toContainText('Marie Collet');
});

test('real expired, revoked and unknown links recover without displaying input fields', async ({ page, context }) => {
  const reference = await context.newPage();
  const states = [
    [seed('TEAM_MEMBERSHIP', 'EXPIRED').token, 'Cette invitation a expiré', 'S-03/expiree'],
    [seed('TEAM_MEMBERSHIP', 'REVOKED').token, "Cette invitation n'est plus valable", 'S-03/revoquee'],
    [randomBytes(32).toString('base64url'), "Lien d'invitation invalide", 'S-03/invalide'],
    [seed('PATIENT_PORTAL', 'EXPIRED').token, "Ce lien d'activation a expiré", 'I-03/expiree'],
  ];
  for (const [token, heading, state] of states) {
    await page.goto(`/invitation#${token}`);
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    await expect(page.getByRole('textbox')).toHaveCount(0);
    await compare(page, reference, state);
  }
});

import { expect, test, type Page } from './support/browser-coverage';
import {
  accessible, base, canal, candidate, claire, fillIdentity, heading, ids, LIST, NEW, person, problem, roster, rowNames, snapshot,
  stubApi, tilleuls,
} from './support/patients';

/**
 * Cases raised by the end-of-story review (W1 to W9): each one reproduces the failure that was reported and asserts the
 * behavior that replaced it. The stubbed API stands for the server; the real stack covers the contract itself.
 */

/** Simulates the tab being hidden and shown again: the account gate removes the view and rechecks the session. */
async function hideAndReturn(page: Page) {
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));
  });
}
const save = (page: Page) => page.getByRole('button', { name: 'Enregistrer l’identité' });
const editPath = `${base}/patients/${claire.recordId}/modifier`;
const many = () => Array.from({ length: 30 }, (_, n) => person(100 + n, `Nom${String(n).padStart(2, '0')}`, 'Test', '1980-01-01'));

test.describe('correction drafts and outcomes', () => {
  test('W1 a restored correction draft is compared with a newer saved version and never re-based silently', async ({ page }) => {
    const live = roster.map((p) => ({ ...p }));
    const calls = await stubApi(page, { roster: live });
    await page.goto(editPath);
    await expect(heading(page, 'Modifier l’identité')).toBeFocused();
    await fillIdentity(page, { prenom: 'Claire-Anne' });
    // A colleague saves version 8 while the form is hidden.
    Object.assign(live.find((p) => p.id === claire.id)!, { phone: '0100000000', version: 8, updatedAt: '2026-11-23T14:02:00Z' });
    await hideAndReturn(page);
    const conflict = page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' });
    await expect(conflict).toContainText('La version 8 a été enregistrée pendant votre saisie.');
    await expect(page.getByLabel('Prénom')).toHaveValue('Claire-Anne');
    await expect(page.getByLabel('Téléphone')).toHaveValue('');
    await expect(page.locator('.etaye-comparaison')).toContainText('Téléphone');
    expect(calls.edits).toHaveLength(0);
    await accessible(page);
    await save(page).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    expect(calls.edits.map((edit) => edit.match)).toEqual(['"8"']);
  });

  test('W1 a restored draft that is still based on the saved version opens without a conflict', async ({ page }) => {
    await stubApi(page, { roster: roster.map((p) => ({ ...p })) });
    await page.goto(editPath);
    await fillIdentity(page, { prenom: 'Claire-Anne' });
    await hideAndReturn(page);
    await expect(page.getByLabel('Prénom')).toHaveValue('Claire-Anne');
    await expect(page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' })).toHaveCount(0);
  });

  test('W2 a lost correction response is a success only when the saved identity is what was sent', async ({ page }) => {
    const live = roster.map((p) => ({ ...p }));
    const target = live.find((p) => p.id === claire.id)!;
    let stranger = true;
    const calls = await stubApi(page, {
      roster: live,
      edit: (body) => {
        if (stranger) Object.assign(target, { phone: '0100000000', version: target.version + 1 });
        else Object.assign(target, body, { version: target.version + 1 });
        return { fail: true };
      },
    });
    await page.goto(editPath);
    await fillIdentity(page, { prenom: 'Claire-Anne' });
    await save(page).click();
    // Version 8 exists but carries someone else's change: it is compared, not shown as our success.
    await expect(page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' })).toBeVisible();
    await expect(page.getByText('L’identité de MARTIN Claire-Anne a été enregistrée.')).toHaveCount(0);
    await expect(heading(page, 'Modifier l’identité')).toBeVisible();

    stranger = false;
    await save(page).click();
    await expect(page.getByText('L’identité de MARTIN Claire-Anne a été enregistrée.')).toBeVisible();
    expect(calls.edits.map((edit) => edit.match)).toEqual(['"7"', '"8"']);
  });

  test('W2 a lost correction that was not applied can simply be retried', async ({ page }) => {
    const calls = await stubApi(page, {
      roster: roster.map((p) => ({ ...p })),
      edit: (_body, _match, count) => (count === 1 ? { fail: true } : undefined),
    });
    await page.goto(editPath);
    await fillIdentity(page, { prenom: 'Claire-Anne' });
    await save(page).click();
    await expect(page.getByText('L’enregistrement a échoué')).toBeVisible();
    await save(page).click();
    await expect(page.getByText('L’identité de MARTIN Claire-Anne a été enregistrée.')).toBeVisible();
    expect(calls.edits.map((edit) => edit.match)).toEqual(['"7"', '"7"']);
  });
});

test.describe('registration attempts', () => {
  test('W3 an unresolved confirmed registration keeps its confirmation and its key across a remount', async ({ page }) => {
    const calls = await stubApi(page, {
      create: (_body, _key, count) => (count === 1
        ? { status: 409, json: problem(409, 'DUPLICATE_PATIENT', { candidates: [candidate(claire)] }) }
        : count <= 3 ? { fail: true } : undefined),
    });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Martin', prenom: 'Claire', j: '18', m: '04', a: '1978' });
    await save(page).click();
    await page.getByRole('button', { name: 'Créer quand même' }).click();
    await expect(page.getByText('Enregistrement non confirmé')).toBeVisible();
    await hideAndReturn(page);
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('Martin');
    await save(page).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    expect(calls.creates).toHaveLength(4);
    const confirmed = [claire.recordId];
    expect(calls.creates.slice(1).map((c) => c.body.confirmedDistinctRecordIds)).toEqual([confirmed, confirmed, confirmed]);
    expect(new Set(calls.creates.slice(1).map((c) => c.key)).size).toBe(1);
    expect(calls.creates[0].key).not.toBe(calls.creates[1].key);
  });

  test('W3 a retry after a failed confirmed attempt repeats the confirmation instead of dropping it', async ({ page }) => {
    const calls = await stubApi(page, {
      create: (_body, _key, count) => (count === 1
        ? { status: 409, json: problem(409, 'DUPLICATE_PATIENT', { candidates: [candidate(claire)] }) }
        : count === 2 ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined),
    });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Martin', prenom: 'Claire', j: '18', m: '04', a: '1978' });
    await save(page).click();
    await page.getByRole('button', { name: 'Créer quand même' }).click();
    await expect(page.getByText('L’enregistrement a échoué')).toBeVisible();
    await save(page).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    expect(calls.creates[2].body.confirmedDistinctRecordIds).toEqual([claire.recordId]);
    expect(calls.creates[2].key).toBe(calls.creates[1].key);
  });
});

test.describe('directory rows', () => {
  test('W4 no cached row is shown before the search answers, and a refusal clears the rows', async ({ page }) => {
    await stubApi(page, {
      search: (_url, count) => (count >= 2 ? { status: 403, delay: 700, json: problem(403, 'FORBIDDEN') } : undefined),
    });
    await page.goto(LIST);
    await expect.poll(() => rowNames(page)).toHaveLength(3);
    await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
    await page.getByRole('button', { name: 'Annuler' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    await expect(page.getByText('Chargement des patients…')).toBeVisible();
    await expect(page.locator('.etaye-lignes__titre')).toHaveCount(0);
    await expect(page.getByText('Accès aux patients non autorisé')).toBeVisible();
    await expect(page.locator('.etaye-lignes__titre')).toHaveCount(0);
  });

  test('W5 "Afficher plus" waits for a pending refresh and a repeated row is shown once with a refresh notice', async ({ page }) => {
    const roster30 = many();
    let slow = false;
    const calls = await stubApi(page, {
      roster: roster30,
      search: (url) => {
        if (url.searchParams.get('cursor')) return { json: { items: roster30.slice(20, 30), nextCursor: null } };
        return slow ? { delay: 900, json: { items: roster30.slice(0, 25), nextCursor: 'bmV4dA==' } } : undefined;
      },
    });
    await page.goto(LIST);
    await expect.poll(() => rowNames(page)).toHaveLength(25);
    slow = true;
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect(page.getByText('Actualisation en cours…')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Afficher plus' })).toBeDisabled();
    await expect(page.getByText('Actualisation en cours…')).toHaveCount(0);
    expect(calls.searches.filter((url) => !url.searchParams.get('cursor'))).toHaveLength(2);

    slow = false;
    await page.getByRole('button', { name: 'Afficher plus' }).click();
    await expect.poll(() => rowNames(page)).toHaveLength(30);
    expect(new Set(await rowNames(page)).size).toBe(30);
    await expect(page.getByText('Résultats à actualiser')).toBeVisible();
    await expect(page.getByText(/Un dossier a été modifié depuis votre recherche \(\d{2}:\d{2}\)\./)).toBeVisible();
    await accessible(page);
    await page.getByRole('button', { name: 'Actualiser' }).click();
    await expect(page.getByText('Résultats à actualiser')).toHaveCount(0);
  });

  test('W8 a created patient that sorts after the loaded page is pinned first and shown once when its page loads', async ({ page }) => {
    await stubApi(page, { roster: many() });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Zèbre', prenom: 'Zoé', j: '03', m: '03', a: '1990' });
    await save(page).click();
    await expect(page.getByText('Dossier créé').first()).toBeVisible();
    const first = page.locator('.etaye-lignes > li').first();
    await expect(first).toContainText('ZÈBRE Zoé');
    await expect(first).toContainText('Dossier créé');
    await expect(first).toHaveClass(/etaye-ligne--nouvelle/);
    await expect(page.getByRole('status').filter({ hasText: '26 patients affichés · d’autres patients existent' })).toBeVisible();
    await accessible(page);
    await page.getByRole('button', { name: 'Afficher plus' }).click();
    await expect.poll(() => rowNames(page)).toHaveLength(31);
    expect((await rowNames(page)).filter((name) => name === 'ZÈBRE Zoé')).toHaveLength(1);
    await expect(page.locator('.etaye-ligne--nouvelle')).toHaveCount(1);
  });

  test('W9 "Réessayer" repeats the query that failed, not an earlier successful one', async ({ page }) => {
    let fail = false;
    const calls = await stubApi(page, {
      search: (url) => (fail && url.searchParams.get('q') === 'Dupont' ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined),
    });
    await page.goto(LIST);
    await expect.poll(() => rowNames(page)).toHaveLength(3);
    fail = true;
    await page.getByLabel('Nom, prénom ou date de naissance').fill('Dupont');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect(page.getByText('La liste n’a pas pu être chargée entièrement')).toBeVisible();
    fail = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect.poll(() => calls.searches.at(-1)?.searchParams.get('q')).toBe('Dupont');
    await expect(page.getByText('La liste n’a pas pu être chargée entièrement')).toHaveCount(0);
    await expect(page.getByText('Aucun patient trouvé pour « Dupont » au Cabinet des Tilleuls')).toBeVisible();
  });
});

test.describe('shell and leaving', () => {
  test('W7 nothing behind the open mobile menu can take focus', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await stubApi(page, { contexts: [tilleuls, canal] });
    await page.goto(LIST);
    await expect.poll(() => rowNames(page)).toHaveLength(3);
    await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
    await expect(page.locator('main')).toHaveAttribute('inert', '');
    await expect(page.locator('.etaye-contexte')).toHaveAttribute('inert', '');
    const behind: (string | null)[] = [];
    for (let i = 0; i < 8; i += 1) {
      await page.keyboard.press('Tab');
      behind.push(await page.evaluate(() => {
        const element = document.activeElement as HTMLElement;
        const covered = element.closest('main, .etaye-contexte');
        return covered ? element.textContent?.trim() ?? covered.tagName : null;
      }));
    }
    expect(behind).toEqual(Array(8).fill(null));
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', { name: 'Ouvrir le menu' })).toBeFocused();
    await expect(page.locator('main')).not.toHaveAttribute('inert', '');
    await expect(page.locator('.etaye-contexte')).not.toHaveAttribute('inert', '');
  });

  test('W6 Back and Forward ask before leaving unsaved input, and a chosen discard follows the browser', async ({ page }) => {
    await stubApi(page);
    await page.goto(LIST);
    await expect.poll(() => rowNames(page)).toHaveLength(3);
    await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
    await fillIdentity(page, { nom: 'Brouillon', prenom: 'Test' });
    await page.goBack();
    await expect(page.getByRole('heading', { name: 'Modifications non enregistrées', level: 1 })).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`${NEW}$`));
    await expect(page.getByRole('button', { name: 'Rester sur le formulaire' })).toBeFocused();
    await page.getByRole('button', { name: 'Rester sur le formulaire' }).click();
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('Brouillon');

    await page.goBack();
    await page.getByRole('button', { name: 'Abandonner les modifications' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`${LIST}$`));
    await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('');
  });

  test('W6 a form without changes follows Back without a question', async ({ page }) => {
    await stubApi(page);
    await page.goto(LIST);
    await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
    await page.goBack();
    await expect(heading(page, 'Patients')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Modifications non enregistrées' })).toHaveCount(0);
  });

  test('W6 the draft survives an expired session and the reconnection of the same account', async ({ page }) => {
    await stubApi(page, {
      session: (count) => ({ ...snapshot([tilleuls]), expiresAt: new Date(Date.now() + (count === 1 ? 5_000 : 3_600_000)).toISOString() }),
    });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Brouillon', prenom: 'Longue', j: '04', m: '05', a: '1960' });
    await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeFocused({ timeout: 15_000 });
    await expect(page.getByText('Brouillon')).toHaveCount(0);
    await page.getByRole('button', { name: 'Se reconnecter' }).click();
    await page.locator('input[name="password"]').fill('Etaye-Jeu-Fictif-91!');
    await page.getByLabel('Adresse e-mail').fill('nora.lefebvre@example.test');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(heading(page, 'Nouveau dossier patient')).toBeVisible();
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('Brouillon');
    await expect(page.getByLabel('Prénom')).toHaveValue('Longue');
    await expect(page.getByLabel('Année')).toHaveValue('1960');
  });

  test('W6 switching to another practice home drops the previous search', async ({ page }) => {
    await stubApi(page, { contexts: [tilleuls, canal] });
    await page.goto(LIST);
    await page.getByLabel('Nom, prénom ou date de naissance').fill('Claire');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect.poll(() => rowNames(page)).toEqual(['MARTIN Claire']);
    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await page.getByRole('button', { name: /Ouvrir · Praticien · Cabinet du Canal/ }).click();
    await expect(page.getByRole('heading', { name: 'Votre espace de travail' })).toBeFocused();
    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await page.getByRole('button', { name: /Ouvrir · Praticien · Cabinet des Tilleuls/ }).click();
    await page.getByRole('link', { name: 'Patients' }).click();
    await expect(page.getByLabel('Nom, prénom ou date de naissance')).toHaveValue('');
  });
});

test.describe('pull request review findings', () => {
  test('R1 the paused patient page is restored for the same account although the server only returns a grant home', async ({ page }) => {
    const calls = await stubApi(page, {
      session: (count) => ({ ...snapshot([tilleuls]), expiresAt: new Date(Date.now() + (count === 1 ? 5_000 : 3_600_000)).toISOString() }),
    });
    const returned: (string | undefined)[] = [];
    page.on('response', async (response) => {
      if (response.url().endsWith('/api/v1/session') && response.request().method() === 'POST') {
        returned.push((await response.json()).returnTo);
      }
    });
    await page.goto(editPath);
    await expect(heading(page, 'Modifier l’identité')).toBeFocused();
    await fillIdentity(page, { prenom: 'Claire-Anne' });
    await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeFocused({ timeout: 15_000 });
    await page.getByRole('button', { name: 'Se reconnecter' }).click();
    await page.locator('input[name="password"]').fill('Etaye-Jeu-Fictif-91!');
    await page.getByLabel('Adresse e-mail').fill('nora.lefebvre@example.test');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(heading(page, 'Modifier l’identité')).toBeVisible();
    await expect(page.getByLabel('Prénom')).toHaveValue('Claire-Anne');
    expect(returned).toEqual(['/compte']);
    expect(calls.edits).toHaveLength(0);
  });

  test('R1 another account after reconnection never inherits the draft', async ({ page }) => {
    await stubApi(page, {
      session: (count) => {
        const long = snapshot([tilleuls]);
        return count === 1
          ? { ...long, expiresAt: new Date(Date.now() + 5_000).toISOString() }
          : { ...long, account: { ...long.account, id: ids(200), displayName: 'Autre Compte' } };
      },
    });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Brouillon', prenom: 'Longue' });
    await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeFocused({ timeout: 15_000 });
    await page.getByRole('button', { name: 'Se reconnecter' }).click();
    await page.locator('input[name="password"]').fill('Etaye-Jeu-Fictif-91!');
    await page.getByLabel('Adresse e-mail').fill('autre@example.test');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page.getByRole('heading', { name: 'Autre compte connecté' })).toBeFocused();
    await page.getByRole('button', { name: 'Continuer' }).click();
    await page.getByRole('link', { name: 'Patients' }).click();
    await page.getByRole('link', { name: 'Créer un dossier patient' }).click();
    await expect(page.getByLabel('Nom de naissance')).toHaveValue('');
    await expect(page.getByLabel('Prénom')).toHaveValue('');
  });

  test('R2 a saved correction leaves no draft: reopening shows no false conflict and the guard stays off, even when the server normalized a value', async ({ page }) => {
    const live = roster.map((p) => ({ ...p }));
    const target = live.find((p) => p.id === claire.id)!;
    await stubApi(page, {
      roster: live,
      edit: (body) => {
        // The server normalizes the phone number it stores.
        Object.assign(target, body, { phone: String(body.phone ?? '').replace(/\s/g, '') || null, version: target.version + 1 });
        return { json: target, headers: { ETag: `"${target.version}"` } };
      },
    });
    await page.goto(editPath);
    await fillIdentity(page, { prenom: 'Claire-Anne', tel: '06 00 00 00 00' });
    await save(page).click();
    await expect(page.getByText('L’identité de MARTIN Claire-Anne a été enregistrée.')).toBeVisible();
    await page.getByRole('link', { name: /Modifier l’identité de MARTIN Claire-Anne/ }).click();
    await expect(heading(page, 'Modifier l’identité')).toBeFocused();
    await expect(page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' })).toHaveCount(0);
    await expect(page.getByLabel('Téléphone')).toHaveValue('0600000000');
    await page.getByRole('button', { name: 'Annuler' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Modifications non enregistrées' })).toHaveCount(0);
  });

  test('R2 reopening after another editor saved a newer version shows that version, with no conflict and no guard', async ({ page }) => {
    const live = roster.map((p) => ({ ...p }));
    await stubApi(page, { roster: live });
    await page.goto(editPath);
    await fillIdentity(page, { prenom: 'Claire-Anne' });
    await save(page).click();
    await expect(page.getByText('L’identité de MARTIN Claire-Anne a été enregistrée.')).toBeVisible();
    Object.assign(live.find((p) => p.id === claire.id)!, { phone: '0100000000', version: 9 });
    await page.getByRole('link', { name: /Modifier l’identité de MARTIN Claire-Anne/ }).click();
    await expect(heading(page, 'Modifier l’identité')).toBeFocused();
    await expect(page.getByRole('alert').filter({ hasText: 'Ce dossier a été modifié entre-temps' })).toHaveCount(0);
    await expect(page.getByLabel('Téléphone')).toHaveValue('0100000000');
    await page.getByRole('button', { name: 'Annuler' }).click();
    await expect(heading(page, 'Patients')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Modifications non enregistrées' })).toHaveCount(0);
  });

  test('R2 a failed save keeps the draft and the guard', async ({ page }) => {
    await stubApi(page, { roster: roster.map((p) => ({ ...p })), edit: () => ({ status: 503, json: problem(503, 'UNAVAILABLE') }) });
    await page.goto(editPath);
    await fillIdentity(page, { prenom: 'Claire-Anne' });
    await save(page).click();
    await expect(page.getByText('L’enregistrement a échoué')).toBeVisible();
    await page.getByRole('button', { name: 'Annuler' }).click();
    await expect(page.getByRole('heading', { name: 'Modifications non enregistrées', level: 1 })).toBeVisible();
  });

  test('R3 the created row is pinned only in the unfiltered directory', async ({ page }) => {
    await stubApi(page, { roster: many() });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Zèbre', prenom: 'Zoé', j: '03', m: '03', a: '1990' });
    await save(page).click();
    await expect(page.locator('.etaye-lignes > li').first()).toContainText('ZÈBRE Zoé');
    await page.getByLabel('Nom, prénom ou date de naissance').fill('Martin');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect(page.getByText('Aucun patient trouvé pour « Martin » au Cabinet des Tilleuls')).toBeVisible();
    await expect(page.locator('.etaye-lignes > li')).toHaveCount(0);
    await expect(page.locator('.etaye-lignes__titre')).toHaveCount(0);
    // The save confirmation itself stays: the person did create the patient.
    await expect(page.locator('.etaye-bandeau__titre', { hasText: 'Dossier créé' })).toBeVisible();
    await page.getByRole('button', { name: 'Effacer la recherche' }).first().click();
    await expect(page.locator('.etaye-lignes > li').first()).toContainText('ZÈBRE Zoé');
  });

  test('R4 a navigation requested during a save is kept: the confirmation shows, then the requested page opens', async ({ page }) => {
    await stubApi(page, {
      contexts: [tilleuls, canal],
      create: () => ({ status: 201, delay: 900, json: person(60, 'Lent', 'Test', '1980-01-01'), headers: { ETag: '"0"' } }),
    });
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Lent', prenom: 'Test', j: '01', m: '01', a: '1980' });
    await save(page).click();
    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await expect(page.getByRole('heading', { name: 'Modifications non enregistrées', level: 1 })).toBeVisible();
    // The save commits: the question no longer applies and the form shows the confirmation instead of a blank page.
    await expect(page.getByRole('heading', { name: 'Modifications non enregistrées' })).toHaveCount(0);
    await expect(page.getByRole('status').filter({ hasText: 'Identité enregistrée.' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Enregistré' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Choisir un contexte de travail' })).toBeVisible();
    await page.getByRole('button', { name: /Ouvrir · Praticien · Cabinet des Tilleuls/ }).click();
    await page.getByRole('link', { name: 'Patients' }).click();
    await expect.poll(() => rowNames(page)).toHaveLength(3);
    await expect(page.locator('.etaye-bandeau__titre', { hasText: 'Dossier créé' })).toHaveCount(0);
  });

  test('R4 without a requested navigation the save still returns to the list with its confirmation', async ({ page }) => {
    await stubApi(page);
    await page.goto(NEW);
    await fillIdentity(page, { nom: 'Dupont', prenom: 'Jeanne', j: '02', m: '06', a: '1985' });
    await save(page).click();
    await expect(page.locator('.etaye-bandeau__titre', { hasText: 'Dossier créé' })).toBeVisible();
  });
});

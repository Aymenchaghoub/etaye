import { expect, test, type Page } from '@playwright/test';
import { accessible, base, canal, claire, heading, ids, louis, noOverflow, noRingOnHeading, problem, tilleuls, usesBackground } from './support/patients';
import { claireRows, claireStatements, type Row, stubRecord, timelineItem } from './support/record';

const RECORD = `${base}/patients/${claire.recordId}`;
const HISTORY = `${RECORD}/antecedents`;
const LOUIS = `${base}/patients/${louis.recordId}`;
const items = (page: Page) => page.locator('.etaye-chrono__item');
const refs = async (page: Page) => (await page.locator('.etaye-provenance .etaye-num:last-of-type').allTextContents());
const banner = (page: Page) => page.locator('header.etaye-banniere');
const sections = (page: Page) => page.getByRole('region', { name: 'Rubriques du dossier' });

test.describe('S-09 chronology (AT-010-A, AT-010-B)', () => {
  test('lists dated evidence with both dates, author, origin and a source marker, and the derived allergy state', async ({ page }) => {
    await stubRecord(page);
    await page.goto(RECORD);
    await expect(heading(page, 'Chronologie')).toBeFocused();
    await noRingOnHeading(page);
    await expect(items(page)).toHaveCount(8);
    await expect.poll(() => refs(page)).toEqual(['SRC-A-005', 'SRC-A-006', 'SRC-A-007-v2', 'SRC-A-004', 'SRC-A-003', 'SRC-A-008', 'SRC-A-001', 'SRC-A-002']);
    const first = items(page).first();
    await expect(first.locator('.etaye-chrono__date')).toContainText('02/11/2026');
    await expect(first.locator('.etaye-chrono__date')).toContainText('09:10');
    await expect(first).toContainText('survenu le 02/11/2026 09:10');
    await expect(first).toContainText('enregistré le 02/11/2026 09:10');
    await expect(first).toContainText('Claire Martin (patiente) · Déclaration de la patiente');
    await expect(first).toContainText('Déclaré par la patiente · non confirmé');
    // The weight is the corrected current revision, with the original retained and the reason shown.
    const weight = items(page).nth(2);
    await expect(weight).toContainText('Poids 68,4 kg');
    await expect(weight).toContainText('Corrigé · version 2');
    await expect(weight).toContainText('version précédente conservée · motif : Correction d’une erreur de saisie, 86,4 devient 68,4 kg.');
    // The recording date differs from the occurrence date for the laboratory value.
    await expect(items(page).last()).toContainText('survenu le 14/09/2026 08:00');
    await expect(items(page).last()).toContainText('enregistré le 15/09/2026 10:00');
    await expect(banner(page)).toContainText('MARTIN Claire');
    await expect(banner(page)).toContainText('Déclarations discordantes à préciser · ne pas conclure à l’absence');
    await expect(sections(page)).toContainText('Antécédents : non renseigné');
    await expect(sections(page)).toContainText('Traitements déclarés : inconnu · à préciser');
    await expect(page.getByText('R-A-001')).toHaveCount(0);
    await accessible(page);
  });

  test('a thin record says so and never reads as normal', async ({ page }) => {
    await stubRecord(page);
    await page.goto(LOUIS);
    await expect(page.getByText('Historique peu renseigné')).toBeVisible();
    await expect(page.getByText('Cela ne signifie pas qu’il n’y a rien à signaler.')).toBeVisible();
    await expect(page.getByText('Aucun événement au dossier')).toBeVisible();
    await expect(page.getByRole('search', { name: 'Filtrer la chronologie' })).toHaveCount(0);
    await expect(banner(page)).toContainText('Non renseigné');
    await expect(sections(page)).toContainText('Allergies : non renseigné');
    await expect(sections(page)).toContainText('Aucune source au dossier · ne signifie pas absence d’allergie');
    await expect(sections(page)).toContainText('Antécédents : inconnu · à préciser');
    await expect(sections(page)).toContainText('Traitements déclarés : non renseigné');
    for (const forbidden of [/aucune allergie/i, /aucun traitement/i, /\bnormal\b/i]) {
      await expect(page.locator('main')).not.toContainText(forbidden);
    }
    await accessible(page);
  });

  test('while loading it shows skeletons and never an unknown label (C10)', async ({ page }) => {
    await stubRecord(page, {
      history: () => ({ json: { asOf: null, sections: [] }, delay: 900 }),
      timeline: () => ({ json: { items: [], nextCursor: null }, delay: 900 }),
    });
    await page.goto(RECORD);
    await expect(page.getByText('Chargement de la chronologie…')).toBeVisible();
    await expect(page.getByText('Chargement des rubriques…')).toBeVisible();
    await expect(banner(page)).toContainText('Chargement des allergies…');
    for (const label of ['Non renseigné', 'Inconnu', 'Historique peu renseigné', 'Aucun événement au dossier']) {
      await expect(page.locator('main')).not.toContainText(label);
    }
    await expect(page.locator('[aria-busy="true"]').first()).toBeVisible();
    await accessible(page);
    await expect(page.getByText('Chargement de la chronologie…')).toHaveCount(0);
  });

  test('a failing timeline keeps the sections and says nothing was deleted; retry reloads only the timeline (C9)', async ({ page }) => {
    let fail = true;
    const { evidence } = await stubRecord(page, { timeline: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined) });
    await page.goto(RECORD);
    await expect(page.getByText('La chronologie n’a pas pu être chargée')).toBeVisible();
    await expect(page.getByText('Chronologie indisponible')).toBeVisible();
    await expect(page.getByText('Ceci n’est pas un dossier vide')).toBeVisible();
    await expect(sections(page)).toContainText('Allergies : déclarations discordantes');
    await expect(banner(page)).toContainText('Déclarations discordantes');
    await expect(page.getByText('Aucun événement au dossier')).toHaveCount(0);
    await accessible(page);
    fail = false;
    const histories = evidence.histories.length;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(items(page)).toHaveCount(8);
    expect(evidence.histories.length).toBe(histories);
  });

  test('failing sections never conclude an absence while the timeline stays readable', async ({ page }) => {
    let fail = true;
    await stubRecord(page, { history: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined) });
    await page.goto(RECORD);
    await expect(page.getByText('Les rubriques n’ont pas pu être chargées')).toBeVisible();
    await expect(page.getByText('Ne pas conclure à l’absence d’allergie, d’antécédent ou de traitement')).toBeVisible();
    await expect(banner(page)).toContainText('Allergies non chargées');
    await expect(banner(page)).toContainText('Ne pas conclure à l’absence');
    await expect(sections(page)).toContainText('États non chargés.');
    await expect(items(page)).toHaveCount(8);
    await accessible(page);
    fail = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(sections(page)).toContainText('Allergies : déclarations discordantes');
    await expect(page.getByText('Les rubriques n’ont pas pu être chargées')).toHaveCount(0);
  });

  test('says honestly when the record fails, is throttled, is refused or is hidden', async ({ page }) => {
    let answer: Parameters<typeof stubRecord>[2] = { record: () => ({ status: 503, json: problem(503, 'UNAVAILABLE') }) };
    await stubRecord(page, {}, { record: (...args) => answer?.record?.(...args) });
    await page.goto(RECORD);
    await expect(page.getByText('Le dossier n’a pas pu être chargé')).toBeVisible();
    await expect(page.getByText('Aucune information du dossier n’est affichée et rien n’a été supprimé.')).toBeVisible();
    await expect(banner(page)).toHaveCount(0);
    await accessible(page);
    answer = { record: () => ({ status: 429, json: problem(429, 'RATE_LIMITED'), headers: { 'Retry-After': '9' } }) };
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByText('Trop de requêtes')).toBeVisible();
    await expect(page.getByText(/Patientez 9 secondes/)).toBeVisible();
    answer = { record: () => ({ status: 403, json: problem(403, 'FORBIDDEN') }) };
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByText('Accès non autorisé · 403')).toBeVisible();
    await expect(heading(page, 'Cet espace est réservé aux soins')).toBeFocused();
    await expect(page.getByText('MARTIN Claire')).toHaveCount(0);
    await accessible(page);
    answer = { record: () => ({ status: 404, json: problem(404, 'NOT_FOUND') }) };
    await page.goto(RECORD);
    await expect(page.getByText('Élément introuvable · 404')).toBeVisible();
    await expect(page.getByText('MARTIN Claire')).toHaveCount(0);
    await accessible(page);
  });

  test('a refusal of a later panel removes every clinical row at once', async ({ page }) => {
    await stubRecord(page, { timeline: (_url, count) => (count > 1 ? { status: 403, json: problem(403, 'FORBIDDEN') } : undefined) });
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(8);
    await page.getByLabel('Type d’événement').selectOption('mesures');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(page.getByText('Accès non autorisé · 403')).toBeVisible();
    await expect(items(page)).toHaveCount(0);
    await expect(page.getByText('MARTIN Claire')).toHaveCount(0);
  });

  test('type and date filters keep a reset; the unknown-date group stays under a date filter; an empty filter is not an empty record', async ({ page }) => {
    const rows: Row[] = [...claireRows, {
      id: ids(5900), lineage: ids(6900), revision: 1, reference: 'SRC-GABARIT', kind: 'UPLOADED_DOCUMENT', label: 'Courrier scanné sans date lisible',
      sourceType: 'DOCUMENT_REPORTED', recordedAt: '2026-10-25T13:30:00Z', author: 'Dr Sami Rahmani',
    }, {
      id: ids(5901), lineage: ids(6901), revision: 1, reference: 'SRC-JOUR', kind: 'CONSULTATION_NOTE', label: 'Note datée au jour',
      excerpt: 'Note dont seul le jour est connu.', sourceType: 'PRACTITIONER_OBSERVED', day: '2026-10-15', recordedAt: '2026-10-16T08:00:00Z', author: 'Dr Nora Lefebvre',
    }];
    const { evidence } = await stubRecord(page, { rows });
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(10);
    await expect(page.getByRole('heading', { name: 'Date inconnue', level: 3 })).toBeVisible();
    await expect(items(page).last().locator('.etaye-chrono__date')).toHaveText('Date inconnue');
    await expect(items(page).last()).toContainText('Date de survenue inconnue');
    await expect(items(page).last()).not.toContainText('survenu le');
    await expect(items(page).last()).toContainText('enregistré le 25/10/2026 14:30');
    await expect(page.getByRole('button', { name: 'Réinitialiser les filtres' })).toHaveCount(0);

    await page.getByLabel('Type d’événement').selectOption('mesures');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(items(page)).toHaveCount(3);
    expect(evidence.timelines.at(-1)!.searchParams.getAll('kind')).toEqual(['PRACTITIONER_MEASUREMENT']);
    await expect(page.getByRole('button', { name: 'Réinitialiser les filtres' })).toBeVisible();

    await page.getByLabel('Type d’événement').selectOption('');
    await page.locator('#f-du-j').fill('01');
    await page.locator('#f-du-m').fill('10');
    await page.locator('#f-du-a').fill('2026');
    await page.locator('#f-au-j').fill('31');
    await page.locator('#f-au-m').fill('10');
    await page.locator('#f-au-a').fill('2026');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(page.getByText('Les événements sans date de survenue restent affichés')).toBeVisible();
    await expect(page.getByText('Un filtre de dates ne masque jamais un fait')).toBeVisible();
    const last = evidence.timelines.at(-1)!.searchParams;
    expect(last.get('from')).toBe('2026-09-30T22:00:00.000Z');
    expect(last.get('to')).toBe('2026-10-31T23:00:00.000Z');
    await expect.poll(() => refs(page)).toEqual(['SRC-A-006', 'SRC-A-007-v2', 'SRC-A-004', 'SRC-JOUR', 'SRC-A-003', 'SRC-GABARIT']);
    // A note known only by its day keeps its date in the list, and its source says so.
    await expect(items(page).filter({ hasText: 'SRC-JOUR' })).toContainText('survenu le 15/10/2026');
    await expect(items(page).filter({ hasText: 'SRC-JOUR' })).not.toContainText('15/10/2026 00:00');
    await accessible(page);

    // The date filter keeps the undated group, but a type filter still applies to it: nothing matches here, and the
    // screen says the record is not empty.
    await page.getByLabel('Type d’événement').selectOption('patient');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(page.getByText('Aucun événement ne correspond à ces filtres')).toBeVisible();
    await expect(page.getByText('Historique peu renseigné')).toHaveCount(0);
    await page.getByRole('button', { name: 'Réinitialiser les filtres' }).last().click();
    await expect(items(page)).toHaveCount(10);
    await expect(page.getByLabel('Type d’événement')).toHaveValue('');
  });

  test('a filter without result says the record is not empty and offers the reset', async ({ page }) => {
    await stubRecord(page);
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(8);
    await page.locator('#f-du-j').fill('01');
    await page.locator('#f-du-m').fill('01');
    await page.locator('#f-du-a').fill('2030');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(page.getByText('Aucun événement ne correspond à ces filtres')).toBeVisible();
    await expect(page.getByText('Cela ne signifie pas que le dossier est vide')).toBeVisible();
    await expect(page.getByText('Historique peu renseigné')).toHaveCount(0);
    await expect(page.getByText('0 événement affiché')).toBeVisible();
    await accessible(page);
    await page.getByRole('button', { name: 'Réinitialiser les filtres' }).last().click();
    await expect(items(page)).toHaveCount(8);
  });

  test('an incorrect filter date is refused with an error summary before any request', async ({ page }) => {
    const { evidence } = await stubRecord(page);
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(8);
    const before = evidence.timelines.length;
    await page.locator('#f-du-j').fill('31');
    await page.locator('#f-du-m').fill('02');
    await page.locator('#f-du-a').fill('2026');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(page.getByRole('alert').filter({ hasText: '1 champ à corriger' })).toBeFocused();
    await expect(page.getByText('Du : date incorrecte')).toBeVisible();
    expect(evidence.timelines.length).toBe(before);
    await accessible(page);
  });

  test('pages of 25 follow the cursor, a failed next page keeps the loaded items and no total is shown', async ({ page }) => {
    const rows: Row[] = Array.from({ length: 30 }, (_, n) => ({
      id: ids(8000 + n), lineage: ids(9000 + n), revision: 1, reference: `SRC-L-${String(n).padStart(2, '0')}`, kind: 'CONSULTATION_NOTE',
      label: `Note ${n}`, excerpt: `Texte ${n}`, sourceType: 'PRACTITIONER_OBSERVED', at: `2026-09-${String(n % 28 + 1).padStart(2, '0')}T08:00:00Z`,
      recordedAt: '2026-10-02T08:00:00Z', author: 'Dr Nora Lefebvre',
    }));
    let failNext = true;
    const { evidence } = await stubRecord(page, {
      rows,
      timeline: (url) => (url.searchParams.get('cursor') && failNext ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined),
    });
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(25);
    await expect(page.getByRole('status').filter({ hasText: '25 événements affichés · d’autres événements existent' })).toBeVisible();
    expect(evidence.timelines[0].searchParams.get('limit')).toBe('25');
    await page.getByRole('button', { name: 'Afficher plus' }).click();
    await expect(page.getByText('La suite n’a pas pu être chargée')).toBeVisible();
    await expect(items(page)).toHaveCount(25);
    await accessible(page);
    failNext = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(items(page)).toHaveCount(30);
    await expect(page.getByRole('status').filter({ hasText: '30 événements affichés' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Afficher plus' })).toHaveCount(0);
  });
});

test.describe('review findings of story 3.2', () => {
  test('a late page of an earlier view never replaces the view that was chosen after it', async ({ page }) => {
    const rows: Row[] = Array.from({ length: 30 }, (_, n) => ({
      id: ids(8000 + n), lineage: ids(9000 + n), revision: 1, reference: `SRC-L-${String(n).padStart(2, '0')}`, kind: 'CONSULTATION_NOTE',
      label: `Note ${n}`, excerpt: `Texte ${n}`, sourceType: 'PRACTITIONER_OBSERVED', at: `2026-09-${String(n % 28 + 1).padStart(2, '0')}T08:00:00Z`,
      recordedAt: '2026-10-02T08:00:00Z', author: 'Dr Nora Lefebvre',
    }));
    await stubRecord(page, { rows, timeline: (url) => (url.searchParams.get('cursor') ? { json: { items: [timelineItem(rows[29])], nextCursor: null }, delay: 1200 } : undefined) });
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(25);
    const isNextPage = (request: { url: () => string }) => request.url().includes('cursor=');
    const stale = Promise.race([page.waitForEvent('requestfinished', isNextPage), page.waitForEvent('requestfailed', isNextPage)]);
    await page.getByRole('button', { name: 'Afficher plus' }).click();
    // While the next page loads, the practitioner chooses an earlier instant: nothing was recorded yet.
    await page.locator('button[aria-controls="asof-region"]').click();
    for (const [id, value] of [['as-j', '01'], ['as-m', '10'], ['as-a', '2026'], ['as-h', '10'], ['as-mi', '00']]) await page.locator(`#${id}`).fill(value);
    await page.getByRole('button', { name: 'Afficher cette date' }).click();
    await expect(page.getByText('Rien n’était enregistré à cette date')).toBeVisible();
    // The delayed page of the earlier view was cancelled or has settled: the chosen view must be unchanged either way.
    await stale;
    await expect(items(page)).toHaveCount(0);
    await expect(page.getByText('Rien n’était enregistré à cette date')).toBeVisible();
    await expect(page.getByText('SRC-L-29')).toHaveCount(0);
  });

  test('a throttled history says so and can be retried, on the chronology too', async ({ page }) => {
    let throttled = true;
    await stubRecord(page, { history: () => (throttled ? { status: 429, json: problem(429, 'RATE_LIMITED'), headers: { 'Retry-After': '7' } } : undefined) });
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(8);
    await expect(page.getByRole('alert').filter({ hasText: 'Trop de requêtes' })).toContainText('Patientez 7 secondes avant de recharger les rubriques.');
    await accessible(page);
    throttled = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(sections(page)).toContainText('Allergies : déclarations discordantes');
  });

  test('a source refused after the page loaded removes the protected content at once', async ({ page }) => {
    await stubRecord(page, { source: () => ({ status: 403, json: problem(403, 'FORBIDDEN') }) });
    await page.goto(HISTORY);
    await expect(page.getByRole('region', { name: 'Allergies' })).toContainText('« Aucune allergie connue');
    await page.getByRole('region', { name: 'Allergies' }).locator('.etaye-controle li').first().getByRole('button', { name: /Afficher la source/ }).click();
    await expect(page.getByText('Accès non autorisé · 403')).toBeVisible();
    await expect(page.getByText('Aucune allergie connue')).toHaveCount(0);
    await expect(page.getByText('MARTIN Claire')).toHaveCount(0);
  });

  test('Enter on the marker of a source that is already open moves focus to its button', async ({ page }) => {
    await stubRecord(page);
    await page.goto(RECORD);
    const item = items(page).first();
    const marker = item.getByRole('link', { name: 'Source 1 : SRC-A-005' });
    await marker.focus();
    await page.keyboard.press('Enter');
    await expect(item.locator('.etaye-source > button')).toBeFocused();
    await marker.focus();
    await expect(marker).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(item.locator('.etaye-source > button')).toBeFocused();
    await expect(item.locator('.etaye-source > button')).toHaveAttribute('aria-expanded', 'true');
  });

  test('the conflict review closes when another instant leaves no conflict', async ({ page }) => {
    await stubRecord(page);
    await page.goto(HISTORY);
    await page.getByRole('button', { name: /Examiner les sources/ }).click();
    await expect(page.getByRole('region', { name: 'Informations à vérifier' })).toBeVisible();
    await page.locator('button[aria-controls="asof-region"]').click();
    for (const [id, value] of [['as-j', '01'], ['as-m', '11'], ['as-a', '2026'], ['as-h', '10'], ['as-mi', '00']]) await page.locator(`#${id}`).fill(value);
    await page.getByRole('button', { name: 'Afficher cette date' }).click();
    await expect(page.getByRole('region', { name: 'Informations à vérifier' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Allergies' })).toContainText('Absence déclarée le 14/09/2026');
    await expect(page.getByRole('region', { name: 'Allergies' })).not.toContainText('Déclarations discordantes');
  });

  test('an hour that repeats when the clocks go back is refused, not guessed', async ({ page }) => {
    const { evidence } = await stubRecord(page);
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(8);
    await page.locator('button[aria-controls="asof-region"]').click();
    const before = evidence.timelines.length;
    for (const [id, value] of [['as-j', '25'], ['as-m', '10'], ['as-a', '2026'], ['as-h', '02'], ['as-mi', '30']]) await page.locator(`#${id}`).fill(value);
    await page.getByRole('button', { name: 'Afficher cette date' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Date et heure : cette heure existe deux fois' })).toBeFocused();
    expect(evidence.timelines.length).toBe(before);
  });

  test('long untrusted statements on the history tab wrap and clip nothing at 390 and 320 px', async ({ page }) => {
    const longWord = 'A'.repeat(70);
    const long = `<img src=x onerror="window.__s10 = 1"> ${('Déclaration longue ' + longWord + ' ').repeat(60)}`.slice(0, 5000);
    const statements = { [claire.recordId]: claireStatements.map((s) => (s.id === ids(7002) ? { ...s, text: long } : s)) };
    await stubRecord(page, { statements });
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      await page.goto(HISTORY);
      await expect(page.getByRole('region', { name: 'Allergies' })).toContainText('Déclaration longue AAAAAAAAAA');
      await noOverflow(page);
      const clipped = await page.evaluate(() => [...document.querySelectorAll('.etaye-controle li, .etaye-declaration, .etaye-3, .etaye-etat')]
        .filter((element) => element.scrollWidth > element.clientWidth + 1).map((element) => element.textContent?.slice(0, 40)));
      expect(clipped).toEqual([]);
      expect(await page.evaluate(() => (window as unknown as { __s10?: number }).__s10)).toBeUndefined();
      await expect(page.locator('main img[src="x"]')).toHaveCount(0);
      if (width !== 1440) await accessible(page);
    }
  });
});

test.describe('the past-instant view (UD-32)', () => {
  const choose = async (page: Page, parts: { j: string; m: string; a: string; h: string; mi: string }) => {
    await page.locator('#as-j').fill(parts.j);
    await page.locator('#as-m').fill(parts.m);
    await page.locator('#as-a').fill(parts.a);
    await page.locator('#as-h').fill(parts.h);
    await page.locator('#as-mi').fill(parts.mi);
    await page.getByRole('button', { name: 'Afficher cette date' }).click();
  };

  test('shows the record as known at an instant without later revisions, read-only, with the current identity noted', async ({ page }) => {
    const { evidence } = await stubRecord(page);
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(8);
    const toggle = page.locator('button[aria-controls="asof-region"]');
    await expect(toggle).toHaveText('Voir le dossier à une date passée');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByText('Heure de Paris. Lecture seule')).toBeVisible();
    await accessible(page);
    await choose(page, { j: '20', m: '10', a: '2026', h: '10', mi: '14' });
    const status = page.locator('.etaye-bandeau--info').filter({ hasText: 'Vue historique — lecture seule' });
    await expect(status).toBeFocused();
    await expect(status).toContainText('Dossier tel qu’il était le 20/10/2026 à 10:14 (heure de Paris).');
    await expect(status).toContainText('L’identité affichée est l’identité actuelle.');
    await expect(page.locator('.etaye-entete__meta')).toContainText('vue historique au 20/10/2026 à 10:14');
    expect(evidence.timelines.at(-1)!.searchParams.get('asOf')).toBe('2026-10-20T08:14:00.000Z');
    expect(evidence.histories.at(-1)!.searchParams.get('asOf')).toBe('2026-10-20T08:14:00.000Z');
    // At 10:14 the weight is version 1: no correction and no trace of the later value.
    // The consultation note of 10:30 was recorded at 10:35: it does not exist yet at 10:14.
    await expect(items(page)).toHaveCount(6);
    await expect(page.locator('main')).not.toContainText('SRC-A-006');
    await expect(items(page).filter({ hasText: 'Poids 86,4 kg' })).toHaveCount(1);
    await expect(page.locator('main')).not.toContainText('68,4');
    await expect(page.locator('main')).not.toContainText('Corrigé');
    await expect(page.locator('main')).not.toContainText('SRC-A-005');
    await expect(sections(page)).toContainText('Allergies : absence déclarée');
    await expect(banner(page)).toContainText('Absence déclarée le 14/09/2026');
    await accessible(page);
    await page.getByRole('button', { name: 'Revenir à la situation actuelle' }).click();
    await expect(items(page)).toHaveCount(8);
    await expect(status).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Voir le dossier à une date passée' })).toBeFocused();
    expect(evidence.timelines.at(-1)!.searchParams.has('asOf')).toBe(false);
  });

  test('a future or impossible instant is refused before any request, with an error summary', async ({ page }) => {
    const { evidence } = await stubRecord(page);
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(8);
    await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
    const requests = evidence.timelines.length;
    await choose(page, { j: '01', m: '01', a: '2099', h: '10', mi: '00' });
    const summary = page.getByRole('alert').filter({ hasText: '1 champ à corriger' });
    await expect(summary).toBeFocused();
    await expect(summary).toContainText('Date et heure : une date future ne peut pas être affichée');
    await expect(page.getByText('Erreur : une date future ne peut pas être affichée. Choisissez un instant passé.')).toBeVisible();
    await accessible(page);
    await choose(page, { j: '31', m: '02', a: '2026', h: '10', mi: '00' });
    await expect(summary).toContainText('Date et heure : cet instant n’existe pas');
    await choose(page, { j: '', m: '', a: '', h: '', mi: '' });
    await expect(summary).toContainText('Date et heure : obligatoires');
    expect(evidence.timelines.length).toBe(requests);
  });

  test('before the first record nothing is known, and nothing reads as normal', async ({ page }) => {
    await stubRecord(page);
    await page.goto(RECORD);
    await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
    await choose(page, { j: '01', m: '09', a: '2026', h: '10', mi: '00' });
    await expect(page.getByText('Rien n’était enregistré à cette date')).toBeVisible();
    await expect(page.getByText('Cela ne signifie pas que le dossier était normal')).toBeVisible();
    await expect(page.getByText('Aucun événement enregistré au 01/09/2026 à 10:00')).toBeVisible();
    await expect(sections(page)).toContainText('Aucune source au dossier à cette date · ne signifie pas absence d’allergie');
    await expect(banner(page)).toContainText('Non renseigné');
    await expect(page.locator('main')).not.toContainText(/aucune allergie\b/i);
    await accessible(page);
  });

  test('the choice follows the record to the history tab and is cleared when the practice changes', async ({ page }) => {
    const { evidence } = await stubRecord(page, {}, { contexts: [tilleuls, canal] });
    await page.goto(RECORD);
    await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
    await choose(page, { j: '01', m: '11', a: '2026', h: '10', mi: '00' });
    await expect(items(page)).toHaveCount(7);
    await page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: 'Antécédents et allergies' }).click();
    await expect(heading(page, 'Antécédents et allergies')).toBeFocused();
    await expect(page.getByText('Vue historique — lecture seule')).toBeVisible();
    await expect(page.locator('#h-ALLERGY')).toBeVisible();
    expect(evidence.histories.at(-1)!.searchParams.get('asOf')).toBe('2026-11-01T09:00:00.000Z');
    await expect(page.getByText('Absence déclarée le 14/09/2026').first()).toBeVisible();
    await expect(page.getByText('Déclarations discordantes')).toHaveCount(0);
    const stored = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }));
    expect(stored).not.toContain('2026');
    expect(page.url()).not.toContain('asOf');
    expect(page.url()).not.toContain('2026-11');

    // Another practice and back, through the application only (no reload): the choice must be gone.
    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await page.getByRole('button', { name: /Ouvrir · Praticien · Cabinet du Canal/ }).click();
    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await page.getByRole('button', { name: /Ouvrir · Praticien · Cabinet des Tilleuls/ }).click();
    await page.getByRole('link', { name: 'Patients', exact: true }).first().click();
    await page.getByRole('link', { name: 'MARTIN Claire', exact: true }).click();
    await expect(items(page)).toHaveCount(8);
    await expect(page.getByText('Vue historique — lecture seule')).toHaveCount(0);
  });
});

test.describe('markers and sources (E1, D-45)', () => {
  test('a disclosure is a button with aria-expanded, operated by Enter and Space, and keeps focus', async ({ page }) => {
    await stubRecord(page);
    await page.goto(RECORD);
    const button = items(page).first().locator('.etaye-source > button');
    await expect(button).toHaveText(/Afficher la source/);
    await expect(button).toHaveAttribute('aria-expanded', 'false');
    await button.focus();
    await page.keyboard.press('Enter');
    await expect(button).toHaveAttribute('aria-expanded', 'true');
    await expect(button).toBeFocused();
    const region = items(page).first().getByRole('region', { name: 'Source SRC-A-005' });
    await expect(region).toContainText('SRC-A-005 · version 1 · PREP-A-001:allergies');
    await expect(region).toContainText('Préparation de visite');
    await expect(region).toContainText('Survenu le 02/11/2026 09:10 · enregistré le 02/11/2026 09:10 · Claire Martin (patiente)');
    await expect(region).toContainText('Déclaré par la patiente · non confirmé');
    await expect(region).toContainText('Je me souviens d’une réaction à la pénicilline');
    await accessible(page);
    await page.keyboard.press('Space');
    await expect(button).toHaveAttribute('aria-expanded', 'false');
    await expect(button).toBeFocused();
    await expect(region).toHaveCount(0);
  });

  test('the marker opens its source and moves focus to the button; "Revenir à l’élément" returns focus to the marker', async ({ page }) => {
    await stubRecord(page);
    await page.goto(RECORD);
    const item = items(page).nth(2);
    const marker = item.getByRole('link', { name: 'Source 3 : SRC-A-007-v2' });
    await marker.focus();
    await page.keyboard.press('Enter');
    await expect(item.getByRole('button', { name: /Masquer la source/ })).toBeFocused();
    const region = item.getByRole('region', { name: 'Source SRC-A-007-v2' });
    await expect(region).toContainText('Remplace SRC-A-007-v1 · motif : Correction d’une erreur de saisie, 86,4 devient 68,4 kg.');
    await expect(region).toContainText('Mesure au cabinet');
    await region.getByRole('link', { name: 'Revenir à l’élément' }).click();
    await expect(marker).toBeFocused();
  });

  test('a source that fails says the statement stays visible; retry works; loading is announced', async ({ page }) => {
    let fail = true;
    await stubRecord(page, { source: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE'), delay: 400 } : undefined) });
    await page.goto(RECORD);
    const item = items(page).first();
    await item.getByRole('button', { name: /Afficher la source/ }).click();
    const region = item.getByRole('region', { name: 'Source SRC-A-005' });
    await expect(region).toHaveAttribute('aria-busy', 'true');
    await expect(region).toContainText('Chargement de la source…');
    await expect(region.getByRole('alert')).toContainText('La source n’a pas pu être chargée');
    await expect(region).toContainText('La déclaration affichée au-dessus reste visible ; seul l’extrait manque. Rien n’a été supprimé.');
    await expect(item).toContainText('Je me souviens d’une réaction à la pénicilline');
    await accessible(page);
    fail = false;
    await region.getByRole('button', { name: 'Réessayer' }).click();
    await expect(region).toContainText('SRC-A-005 · version 1');
  });

  test('long untrusted text wraps, is never markup and never clips a date, an author or a source at 320 px', async ({ page }) => {
    const hostile = '<script>window.__x = 1</script><img src=x onerror="window.__y = 1"> ';
    const longWord = 'A'.repeat(70);
    const long = `${hostile}${('Texte long ' + longWord + ' ').repeat(80)}`.slice(0, 5000);
    const label = `Libellé ${longWord} ${'très long '.repeat(40)}`.slice(0, 500);
    const rows: Row[] = [{ ...claireRows[4], excerpt: long, label }, ...claireRows.filter((_, n) => n !== 4)];
    await stubRecord(page, { rows });
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      await page.goto(RECORD);
      await expect(items(page)).toHaveCount(8);
      await expect(page.locator('main').getByText('Libellé AAAAAAAA').first()).toBeVisible();
      await noOverflow(page);
      expect(await page.evaluate(() => [(window as unknown as { __x?: number }).__x, (window as unknown as { __y?: number }).__y])).toEqual([undefined, undefined]);
      // No essential text is clipped: every dated cell and provenance line fits its box.
      const clipped = await page.evaluate(() => [...document.querySelectorAll('.etaye-chrono__date, .etaye-provenance, .etaye-chrono__titre')]
        .filter((element) => element.scrollWidth > element.clientWidth + 1).map((element) => element.textContent?.slice(0, 40)));
      expect(clipped).toEqual([]);
      const dates = await page.locator('.etaye-chrono__date').first().evaluate((element) => ({ scroll: element.scrollWidth, client: element.clientWidth }));
      expect(dates.scroll).toBeLessThanOrEqual(dates.client);
      await expect(page.locator('script', { hasText: 'window.__x' })).toHaveCount(0);
      await expect(page.locator('main img[src="x"]')).toHaveCount(0);
      if (width !== 1440) await accessible(page);
    }
  });
});

test.describe('S-10 history and allergies, I-27 read subset (AT-010-A, AT-010-B)', () => {
  test('shows the conflicting reports with both statements, dates, authors and origins, and never resolves them', async ({ page }) => {
    await stubRecord(page);
    await page.goto(HISTORY);
    await expect(heading(page, 'Antécédents et allergies')).toBeFocused();
    await noRingOnHeading(page);
    const allergies = page.getByRole('region', { name: 'Allergies' });
    await expect(allergies).toContainText('Déclarations discordantes');
    await expect(allergies).toContainText('Rubrique à préciser : deux déclarations datées se contredisent · ne pas conclure à l’absence d’allergie.');
    await expect(allergies).toContainText('Informations à vérifier');
    await expect(allergies).toContainText('Aucune n’est supprimée ; rien n’est conclu automatiquement.');
    const rows = allergies.locator('.etaye-controle li');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('14/09/2026 · Note de consultation');
    await expect(rows.nth(0)).toContainText('« Aucune allergie connue, selon la déclaration recueillie ce jour. »');
    await expect(rows.nth(0)).toContainText('Absence déclarée le 14/09/2026');
    await expect(rows.nth(0)).toContainText('Dr Nora Lefebvre · Déclaration de la patiente · SRC-A-001');
    await expect(rows.nth(1)).toContainText('02/11/2026 · Préparation de visite');
    await expect(rows.nth(1)).toContainText('Déclaré par la patiente · non confirmé');
    await expect(rows.nth(1)).toContainText('Claire Martin (patiente) · Déclaration de la patiente · SRC-A-005');
    await expect(page.getByRole('region', { name: 'Antécédents' })).toContainText('Non renseigné');
    await expect(page.getByRole('region', { name: 'Antécédents' })).toContainText('Aucune source au dossier · ne signifie pas absence d’antécédent.');
    await expect(page.getByRole('region', { name: 'Traitements déclarés' })).toContainText('Inconnu · à préciser');
    await expect(page.getByRole('region', { name: 'Traitements déclarés' })).toContainText('Marqué inconnu au dossier. Ne pas écrire « aucun traitement ».');
    await expect(page.getByRole('button', { name: /Rédiger|Ajouter|Corriger|Modifier|Enregistrer/ })).toHaveCount(0);
    await accessible(page);
  });

  test('a thin record shows unknown or not recorded for every section', async ({ page }) => {
    await stubRecord(page);
    await page.goto(`${LOUIS}/antecedents`);
    await expect(page.getByRole('region', { name: 'Allergies' })).toContainText('Non renseigné');
    await expect(page.getByRole('region', { name: 'Antécédents' })).toContainText('Inconnu · à préciser');
    await expect(page.getByRole('region', { name: 'Traitements déclarés' })).toContainText('Non renseigné');
    await expect(page.locator('.etaye-controle')).toHaveCount(0);
    await expect(page.locator('main')).not.toContainText(/aucune allergie\b/i);
    await accessible(page);
  });

  test('loading and failed sections: skeleton without any state label, then an honest failure with retry', async ({ page }) => {
    let fail = true;
    await stubRecord(page, { history: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE'), delay: 600 } : undefined) });
    await page.goto(HISTORY);
    await expect(page.getByText('Chargement des rubriques…')).toBeVisible();
    for (const label of ['Non renseigné', 'Inconnu', 'Non demandé']) await expect(page.locator('main')).not.toContainText(label);
    await accessible(page);
    await expect(page.getByRole('alert').filter({ hasText: 'Les rubriques n’ont pas pu être chargées' })).toBeVisible();
    await expect(page.getByText('Ne pas conclure à l’absence d’allergie, d’antécédent ou de traitement')).toBeVisible();
    await accessible(page);
    fail = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByRole('region', { name: 'Allergies' })).toContainText('Déclarations discordantes');
  });

  test('an explicit negative alone reads as dated and never as "Aucune allergie"', async ({ page }) => {
    const { evidence } = await stubRecord(page);
    await page.goto(HISTORY);
    await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
    await page.locator('#as-j').fill('01');
    await page.locator('#as-m').fill('11');
    await page.locator('#as-a').fill('2026');
    await page.locator('#as-h').fill('10');
    await page.locator('#as-mi').fill('00');
    await page.getByRole('button', { name: 'Afficher cette date' }).click();
    const allergies = page.getByRole('region', { name: 'Allergies' });
    await expect(allergies).toContainText('Absence déclarée le 14/09/2026');
    expect(evidence.histories.at(-1)!.searchParams.get('asOf')).toBe('2026-11-01T09:00:00.000Z');
  });

  test('"Examiner les sources" opens the read-only review of the conflict, with both sources, and returns focus', async ({ page }) => {
    await stubRecord(page);
    await page.goto(HISTORY);
    const examine = page.getByRole('button', { name: /Examiner les sources/ });
    await examine.click();
    const review = page.getByRole('region', { name: 'Informations à vérifier' });
    await expect(page.getByRole('heading', { name: 'Informations à vérifier' })).toBeFocused();
    await expect(review).toContainText('Allergies · lecture seule');
    await expect(review).toContainText('Plus ancienne');
    await expect(review).toContainText('Plus récente');
    await expect(review).toContainText('14/09/2026 09:30');
    await expect(review).toContainText('02/11/2026 09:10');
    await expect(review).toContainText('La déclaration négative du 14/09/2026 ne contredit pas une réaction rapportée ensuite : elle date d’avant.');
    await expect(review).toContainText('Rien n’est fusionné ni résolu automatiquement. La rubrique reste « à préciser ».');
    await expect(review.locator('.etaye-bandeau')).toHaveCount(0);
    await expect(review.getByRole('button', { name: /Conserver|Résoudre|Valider|Fusionner/ })).toHaveCount(0);
    await review.getByRole('button', { name: /Afficher la source/ }).first().click();
    await review.getByRole('button', { name: /Afficher la source/ }).last().click();
    await expect(review.getByRole('region', { name: 'Source SRC-A-001' })).toContainText('Aucune allergie connue, selon la déclaration recueillie ce jour.');
    await expect(review.getByRole('region', { name: 'Source SRC-A-005' })).toContainText('Claire Martin (patiente)');
    await accessible(page);
    await review.getByRole('button', { name: 'Retour aux antécédents et allergies' }).click();
    await expect(page.getByRole('button', { name: /Examiner les sources/ })).toBeFocused();
  });

  test('a statement source that fails or loads says what remains visible', async ({ page }) => {
    let fail = true;
    await stubRecord(page, { source: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined) });
    await page.goto(HISTORY);
    const first = page.getByRole('region', { name: 'Allergies' }).locator('.etaye-controle li').first();
    await first.getByRole('button', { name: /Afficher la source/ }).click();
    await expect(first.getByRole('alert')).toContainText('La source n’a pas pu être chargée');
    await expect(first).toContainText('« Aucune allergie connue, selon la déclaration recueillie ce jour. »');
    fail = false;
    await first.getByRole('button', { name: 'Réessayer' }).click();
    await expect(first.getByRole('region', { name: 'Source SRC-A-001' })).toContainText('SRC-A-001 · version 1 · section:allergies');
  });
});

test.describe('shell, entries and context hygiene', () => {
  test('the directory name opens the chronology and the duplicate panel leads to the same screen', async ({ page }) => {
    await stubRecord(page);
    await page.goto(`${base}/patients`);
    await page.getByRole('link', { name: 'MARTIN Claire', exact: true }).click();
    await expect(heading(page, 'Chronologie')).toBeFocused();
    await expect(page).toHaveURL(RECORD);
    await page.getByRole('link', { name: 'Patients', exact: true }).first().click();
    await expect(heading(page, 'Patients')).toBeVisible();
  });

  test('nothing clinical reaches the address bar or browser storage', async ({ page }) => {
    await stubRecord(page);
    await page.goto(RECORD);
    await expect(items(page)).toHaveCount(8);
    await items(page).first().getByRole('button', { name: /Afficher la source/ }).click();
    const stored = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }));
    for (const secret of ['pénicilline', 'Claire', 'SRC-A-005', 'MARTIN']) {
      expect(stored).not.toContain(secret);
      expect(page.url()).not.toContain(secret);
    }
  });

  test('the record screens keep the shell and background in both themes at four widths without overflow or violation', async ({ page }) => {
    await stubRecord(page);
    for (const scheme of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: scheme });
      for (const [width, height] of [[1440, 1024], [1024, 768], [390, 844], [320, 844]] as const) {
        await page.setViewportSize({ width, height });
        for (const path of [RECORD, HISTORY, LOUIS]) {
          await page.goto(path);
          await expect(page.locator('.etaye-banniere')).toBeVisible();
          await expect(page.locator('h1')).toBeVisible();
          expect(await usesBackground(page, '.etaye-app', '--fond')).toBe(true);
          expect(await usesBackground(page, '.etaye-top', '--fond')).toBe(true);
          expect(await usesBackground(page, '.etaye-main', '--surface')).toBe(true);
          await noRingOnHeading(page);
          await noOverflow(page);
          if (width === 390 || width === 1440) await accessible(page);
        }
      }
    }
  });

  test('keyboard focus shows a 2 px ring on the controls of the chronology', async ({ page }) => {
    await stubRecord(page);
    await page.setViewportSize({ width: 1440, height: 1024 });
    await page.goto(RECORD);
    await expect(heading(page, 'Chronologie')).toBeFocused();
    // The tab order is read on the loaded page: the heading is focused while the record still loads.
    await expect(items(page)).toHaveCount(8);
    await expect(heading(page, 'Chronologie')).toBeFocused();
    const stops: string[] = [];
    for (let tab = 0; tab < 14; tab += 1) {
      await page.keyboard.press('Tab');
      const ring = await page.evaluate(() => {
        const element = document.activeElement as HTMLElement;
        const style = getComputedStyle(element);
        return { style: style.outlineStyle, width: style.outlineWidth, element: `${element.tagName}.${element.className}#${element.id}` };
      });
      stops.push(`${ring.element} ${ring.style} ${ring.width}`);
    }
    expect(stops.filter((stop) => !/ solid 2px$/.test(stop)), stops.join('\n')).toEqual([]);
  });

  test('the timeline item of the stub is the contract shape (guards the stub itself)', () => {
    const item = timelineItem(claireRows[6]);
    expect(item).toMatchObject({ kind: 'PRACTITIONER_MEASUREMENT', corrected: false, revision: 1 });
    expect(claireStatements.filter((statement) => statement.category === 'ALLERGY')).toHaveLength(2);
  });
});

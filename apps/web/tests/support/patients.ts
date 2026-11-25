import { AxeBuilder } from '@axe-core/playwright';
import { expect, type Page, type Route } from '@playwright/test';
import type { AccessContext, Patient, Problem, SchedulingPatient, Session } from '../../src/api/generated/types.gen';

export const ids = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
export const tilleuls: AccessContext = {
  kind: 'MEMBERSHIP', grantId: ids(1), practiceId: ids(10), practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL', timezone: 'Europe/Paris',
};
export const canal: AccessContext = {
  kind: 'MEMBERSHIP', grantId: ids(2), practiceId: ids(20), practiceName: 'Cabinet du Canal', displayRole: 'CLINICAL', timezone: 'Europe/Paris',
};
export const admin: AccessContext = { ...canal, grantId: ids(4), practiceName: 'Cabinet des Tilleuls', displayRole: 'ADMINISTRATION' };
export const base = `/compte/pratiques/${tilleuls.grantId}`;
export const LIST = `${base}/patients`;
export const NEW = `${base}/patients/nouveau`;
export const when = '2026-11-23T09:00:00Z';

export function person(n: number, lastName: string, firstName: string, dateOfBirth: string, patch: Partial<Patient> = {}): Patient {
  return {
    id: ids(1000 + n), recordId: ids(2000 + n), practiceId: tilleuls.practiceId, version: 0, createdAt: when, updatedAt: when,
    lastName, firstName, dateOfBirth, contactEmail: null, phone: null, ...patch,
  };
}
export const louis = person(2, 'Bernard', 'Louis', '1991-07-09');
export const marie = person(3, 'Collet', 'Marie', '1990-03-12');
export const claire = person(1, 'Martin', 'Claire', '1978-04-18', { version: 7 });
export const roster = [louis, marie, claire];

export function snapshot(contexts: AccessContext[]): Session {
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
export const problem = (status: number, code: string, extra: Partial<Problem> = {}): Problem => ({
  type: 'about:blank', title: code, status, code, correlationId: 'test', ...extra,
});

export type Reply = { status?: number; json?: unknown; headers?: Record<string, string>; delay?: number; fail?: boolean };
export type Calls = { searches: URL[]; creates: { body: Record<string, unknown>; key: string }[]; edits: { body: Record<string, unknown>; match: string }[]; reads: number };
export type Handlers = {
  contexts?: AccessContext[];
  /** Answers the nth session read or sign-in; the default is a long session. */
  session?: (count: number) => Session;
  roster?: Patient[];
  search?: (url: URL, count: number) => Reply | undefined;
  create?: (body: Record<string, unknown>, key: string, count: number) => Reply | undefined;
  edit?: (body: Record<string, unknown>, match: string, count: number) => Reply | undefined;
  record?: (id: string, count: number) => Reply | undefined;
};

export const squash = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

export async function reply(route: Route, answer: Reply) {
  if (answer.delay) await new Promise((resolve) => setTimeout(resolve, answer.delay));
  if (answer.fail) return route.abort('failed');
  return route.fulfill({ status: answer.status ?? 200, json: answer.json, headers: answer.headers });
}

export async function stubApi(page: Page, handlers: Handlers = {}): Promise<Calls> {
  const calls: Calls = { searches: [], creates: [], edits: [], reads: 0 };
  const contexts = handlers.contexts ?? [tilleuls];
  const people = [...(handlers.roster ?? roster)];
  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } }));
  let sessionReads = 0;
  await page.route('**/api/v1/session', (route) => {
    sessionReads += 1;
    const request = route.request();
    const session = handlers.session?.(sessionReads) ?? snapshot(contexts);
    const candidate = request.method() === 'POST' ? (request.postDataJSON() as { returnTo?: string } | null)?.returnTo : undefined;
    // The real server (AccountAccessController.safeReturn) returns only the exact path of an active grant and
    // answers /compte for anything else, including every sub-route such as the patient directory.
    const exact = candidate !== undefined && (session.membershipIds.some((id) => `/compte/pratiques/${id}` === candidate)
      || session.portalLinkIds.some((id) => `/compte/patient/${id}` === candidate));
    return route.fulfill({ json: request.method() === 'POST' ? { ...session, returnTo: exact ? candidate : '/compte' } : session });
  });
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

export async function accessible(page: Page) {
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.passes.length).toBeGreaterThan(0);
  expect(result.violations.map(({ id, nodes }) => [id, nodes.map(({ target }) => target)])).toEqual([]);
}
export async function noOverflow(page: Page) {
  const width = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  expect(width[0]).toBeLessThanOrEqual(width[1]);
}
export async function usesBackground(page: Page, selector: string, token: string) {
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
export async function noRingOnHeading(page: Page) {
  const style = await page.evaluate(() => getComputedStyle(document.querySelector('h1')!).outlineStyle);
  expect(style).toBe('none');
}
export const rowNames = (page: Page) => page.locator('.etaye-lignes__titre').allTextContents();
export const heading = (page: Page, name: string) => page.getByRole('heading', { name, exact: true, level: 1 });

export async function fillIdentity(page: Page, values: { nom?: string; prenom?: string; j?: string; m?: string; a?: string; tel?: string; mail?: string }) {
  if (values.nom !== undefined) await page.getByLabel('Nom de naissance').fill(values.nom);
  if (values.prenom !== undefined) await page.getByLabel('Prénom').fill(values.prenom);
  if (values.j !== undefined) await page.getByLabel('Jour').fill(values.j);
  if (values.m !== undefined) await page.getByLabel('Mois').fill(values.m);
  if (values.a !== undefined) await page.getByLabel('Année').fill(values.a);
  if (values.tel !== undefined) await page.getByLabel('Téléphone').fill(values.tel);
  if (values.mail !== undefined) await page.getByLabel('Adresse e-mail').fill(values.mail);
}

export const candidate = (p: Patient, extra: Partial<SchedulingPatient> = {}): SchedulingPatient => ({
  id: p.id, practiceId: p.practiceId, recordId: p.recordId, firstName: p.firstName, lastName: p.lastName,
  dateOfBirth: p.dateOfBirth, contactEmail: p.contactEmail, phone: p.phone, ...extra,
});

import type { Page } from '@playwright/test';
import type { Assertion, HistorySection, HistorySummary, Source, SourceKind, SourceType, TimelineItem } from '../../src/api/generated/types.gen';
import { ids, claire, louis, problem, reply, type Reply, stubApi, tilleuls, type Handlers } from './patients';

/** One immutable revision of the stub evidence, shaped like the seeded rows of the synthetic fixture (Claire Martin). */
export type Row = {
  id: string; lineage: string; revision: number; reference: string; kind: SourceKind; label: string; excerpt?: string;
  sourceType: SourceType; at?: string; day?: string; recordedAt: string; author: string; locator?: string;
  supersedes?: string; reason?: string;
};
export type Statement = {
  id: string; category: Assertion['category']; kind: Assertion['assertionKind']; text?: string; at?: string; recordedAt: string; source?: string;
};

const row = (n: number, patch: Partial<Row> & Pick<Row, 'reference' | 'kind' | 'label' | 'sourceType' | 'recordedAt' | 'author'>): Row => ({
  id: ids(5000 + n), lineage: ids(6000 + n), revision: 1, ...patch,
});
const nora = 'Dr Nora Lefebvre';
export const claireRows: Row[] = [
  row(1, { reference: 'SRC-A-001', kind: 'CONSULTATION_NOTE', label: 'Allergies déclarées lors de la consultation', excerpt: 'Aucune allergie connue, selon la déclaration recueillie ce jour.', sourceType: 'PATIENT_REPORTED', at: '2026-09-14T07:30:00Z', recordedAt: '2026-09-14T07:45:00Z', author: nora, locator: 'section:allergies' }),
  row(2, { reference: 'SRC-A-002', kind: 'MANUAL_LAB_PANEL', label: 'Créatinine 96 µmol/L', excerpt: 'Créatinine 96 µmol/L, intervalle fourni 60–110 µmol/L.', sourceType: 'DOCUMENT_REPORTED', at: '2026-09-14T06:00:00Z', recordedAt: '2026-09-15T08:00:00Z', author: nora, locator: 'LAB-A-001' }),
  row(3, { reference: 'SRC-A-003', kind: 'UPLOADED_DOCUMENT', label: 'Compte rendu de laboratoire fictif', excerpt: 'Exemple de compte rendu de laboratoire fictif : créatinine 104 µmol/L.', sourceType: 'DOCUMENT_REPORTED', at: '2026-10-12T06:00:00Z', recordedAt: '2026-10-13T08:00:00Z', author: 'Dr Sami Rahmani', locator: 'DOC-A-001:page-1' }),
  row(4, { reference: 'SRC-A-004', kind: 'PRACTITIONER_MEASUREMENT', label: 'Pression artérielle 134/82 mmHg', excerpt: 'Pression artérielle mesurée au cabinet : 134/82 mmHg.', sourceType: 'PRACTITIONER_OBSERVED', at: '2026-10-20T08:10:00Z', recordedAt: '2026-10-20T08:12:00Z', author: nora, locator: 'MEAS-A-002' }),
  row(5, { reference: 'SRC-A-005', kind: 'PATIENT_PREPARATION', label: 'Reçue de Claire Martin', excerpt: 'Je me souviens d’une réaction à la pénicilline dans ma jeunesse. Je ne sais plus laquelle.', sourceType: 'PATIENT_REPORTED', at: '2026-11-02T08:10:00Z', recordedAt: '2026-11-02T08:10:00Z', author: 'Claire Martin (patiente)', locator: 'PREP-A-001:allergies' }),
  row(6, { reference: 'SRC-A-006', kind: 'CONSULTATION_NOTE', label: 'Consultation de suivi · Dr Nora Lefebvre', excerpt: 'Consultation de suivi : résultats disponibles relus avec la patiente. Traitements actuels non renseignés dans ce jeu de données.', sourceType: 'PRACTITIONER_OBSERVED', at: '2026-10-20T08:30:00Z', recordedAt: '2026-10-20T08:35:00Z', author: nora, locator: 'CONS-A-001:report' }),
  row(7, { reference: 'SRC-A-007-v1', kind: 'PRACTITIONER_MEASUREMENT', label: 'Poids 86,4 kg', excerpt: 'Poids saisi : 86,4 kg.', sourceType: 'PRACTITIONER_OBSERVED', at: '2026-10-20T08:12:00Z', recordedAt: '2026-10-20T08:13:00Z', author: nora, locator: 'MEAS-A-003:version-1' }),
  row(7, { id: ids(5107), reference: 'SRC-A-007-v2', kind: 'PRACTITIONER_MEASUREMENT', label: 'Poids 68,4 kg', excerpt: 'Poids corrigé : 68,4 kg.', sourceType: 'PRACTITIONER_OBSERVED', at: '2026-10-20T08:12:00Z', recordedAt: '2026-10-20T08:15:00Z', author: nora, locator: 'MEAS-A-003:version-2', revision: 2, supersedes: ids(5007), reason: 'Correction d’une erreur de saisie, 86,4 devient 68,4 kg.' }),
  row(8, { reference: 'SRC-A-008', kind: 'PRACTITIONER_MEASUREMENT', label: 'Pression artérielle 128/80 mmHg', excerpt: 'Pression artérielle : 128/80 mmHg.', sourceType: 'PRACTITIONER_OBSERVED', at: '2026-09-14T07:35:00Z', recordedAt: '2026-09-14T07:36:00Z', author: nora, locator: 'MEAS-A-001' }),
];
export const claireStatements: Statement[] = [
  { id: ids(7001), category: 'ALLERGY', kind: 'EXPLICITLY_ABSENT', text: 'Aucune allergie connue, selon la déclaration recueillie ce jour.', at: '2026-09-14T07:30:00Z', recordedAt: '2026-09-14T07:45:00Z', source: ids(5001) },
  { id: ids(7002), category: 'ALLERGY', kind: 'PRESENT', text: 'Je me souviens d’une réaction à la pénicilline dans ma jeunesse. Je ne sais plus laquelle.', at: '2026-11-02T08:10:00Z', recordedAt: '2026-11-02T08:10:00Z', source: ids(5005) },
  { id: ids(7003), category: 'REPORTED_TREATMENT', kind: 'UNKNOWN', recordedAt: '2026-11-02T15:00:00Z' },
];
export const louisStatements: Statement[] = [
  { id: ids(7004), category: 'HISTORY', kind: 'UNKNOWN', recordedAt: '2026-11-02T15:00:00Z' },
];

const asDate = (iso: string) => ({ precision: 'INSTANT' as const, instant: iso });
function current<T extends { lineage?: string; recordedAt: string; revision?: number }>(rows: T[], asOf: string | null): T[] {
  const visible = rows.filter((candidate) => !asOf || candidate.recordedAt <= asOf);
  const latest = new Map<string, T>();
  for (const candidate of visible) {
    const key = candidate.lineage ?? '';
    const known = latest.get(key);
    if (!known || (candidate.revision ?? 1) > (known.revision ?? 1)) latest.set(key, candidate);
  }
  return [...latest.values()];
}

export function timelineItem(source: Row): TimelineItem {
  return {
    id: source.id, eventDate: source.at ? asDate(source.at) : source.day ? { precision: 'DATE', date: source.day, timezone: 'Europe/Paris' } : { precision: 'UNKNOWN' },
    recordedAt: source.recordedAt, kind: source.kind, sourceRevisionId: source.id, label: source.label, corrected: source.revision > 1,
    reference: source.reference, sourceType: source.sourceType, authorLabel: source.author, revision: source.revision,
    ...(source.reason ? { correctionReason: source.reason } : {}), ...(source.excerpt ? { excerpt: source.excerpt } : {}),
  };
}
export function sourceBody(source: Row, rows: Row[], recordId: string): Source {
  const previous = rows.find((candidate) => candidate.id === source.supersedes);
  return {
    id: source.id, practiceId: tilleuls.practiceId, recordId, sourceType: source.sourceType,
    eventDate: source.at ? asDate(source.at) : { precision: 'UNKNOWN' }, recordedAt: source.recordedAt, authorLabel: source.author,
    revision: source.revision, reference: source.reference, kind: source.kind, label: source.label,
    ...(source.excerpt ? { excerpt: source.excerpt } : {}), ...(source.locator ? { locator: source.locator } : {}),
    ...(previous ? { supersedesId: previous.id, supersedesReference: previous.reference } : {}),
    ...(source.reason ? { correctionReason: source.reason } : {}),
  };
}

const order = (a: TimelineItem, b: TimelineItem) => {
  const unknownA = a.eventDate.precision === 'UNKNOWN';
  const unknownB = b.eventDate.precision === 'UNKNOWN';
  if (unknownA !== unknownB) return unknownA ? 1 : -1;
  const at = (item: TimelineItem) => item.eventDate.instant ?? '';
  return at(b).localeCompare(at(a)) || b.recordedAt.localeCompare(a.recordedAt) || b.id.localeCompare(a.id);
};

/** The derived state of a section, written again from BR-008 so the stub never depends on the screen's own code. */
export function derive(statements: Statement[]): HistorySection['state'] {
  if (statements.length === 0) return 'NOT_RECORDED';
  const present = statements.some((s) => s.kind === 'PRESENT');
  const absent = statements.some((s) => s.kind === 'EXPLICITLY_ABSENT');
  if (present && absent) return 'CONFLICTING';
  if (present) return 'PRESENT';
  if (absent) return 'EXPLICITLY_ABSENT';
  const latest = [...statements].sort((a, b) => b.recordedAt.localeCompare(a.recordedAt))[0];
  return latest.kind === 'NOT_ASKED' ? 'NOT_ASKED' : 'UNKNOWN';
}

export function assertionBody(statement: Statement, rows: Row[]): Assertion {
  const cited = rows.find((candidate) => candidate.id === statement.source);
  return {
    id: statement.id, version: 1, createdAt: statement.recordedAt, updatedAt: statement.recordedAt, practiceId: tilleuls.practiceId,
    category: statement.category, assertionKind: statement.kind, eventDate: statement.at ? asDate(statement.at) : { precision: 'UNKNOWN' },
    recordedAt: statement.recordedAt, revision: 1,
    ...(statement.text ? { text: statement.text } : {}),
    ...(cited ? { sourceRevisionId: cited.id, sourceReference: cited.reference, sourceKind: cited.kind, sourceType: cited.sourceType, authorLabel: cited.author } : {}),
  };
}

export function historyBody(statements: Statement[], rows: Row[], asOf: string | null): HistorySummary {
  const visible = statements.filter((statement) => !asOf || statement.recordedAt <= asOf);
  const sections = (['ALLERGY', 'HISTORY', 'REPORTED_TREATMENT'] as const).map((category): HistorySection => {
    const own = visible.filter((statement) => statement.category === category)
      .sort((a, b) => (a.at ?? '9').localeCompare(b.at ?? '9'));
    return { category, state: derive(own), statements: own.map((statement) => assertionBody(statement, rows)), truncated: false };
  });
  return { asOf, sections: sections as HistorySummary['sections'] };
}

export type RecordStub = {
  rows?: Row[];
  statements?: Record<string, Statement[]>;
  rowsByRecord?: Record<string, Row[]>;
  /** Replaces the answer to a timeline request; `undefined` keeps the stub's own answer. */
  timeline?: (url: URL, count: number) => Reply | undefined;
  history?: (url: URL, count: number) => Reply | undefined;
  source?: (id: string, url: URL, count: number) => Reply | undefined;
};
export type RecordCalls = { timelines: URL[]; histories: URL[]; sources: URL[]; rawPaths: string[] };

/** The record evidence of the synthetic dataset behind the real operations' shape, for the stubbed smoke tests. */
export async function stubRecord(page: Page, options: RecordStub = {}, handlers: Handlers = {}) {
  const calls = await stubApi(page, handlers);
  const evidence: RecordCalls = { timelines: [], histories: [], sources: [], rawPaths: [] };
  const byRecord: Record<string, Row[]> = options.rowsByRecord ?? { [claire.recordId]: options.rows ?? claireRows, [louis.recordId]: [] };
  const statementsByRecord = options.statements ?? { [claire.recordId]: claireStatements, [louis.recordId]: louisStatements };
  await page.route((url) => /\/api\/v1\/practices\/[^/]+\/(records\/[^/]+\/(timeline|history|sources|assertions)|sources\/[^/]+)$/.test(url.pathname), async (route) => {
    const url = new URL(route.request().url());
    evidence.rawPaths.push(url.pathname + url.search);
    const parts = url.pathname.replace('/api/v1/practices/', '').split('/');
    const asOf = url.searchParams.get('asOf');
    if (asOf && Date.parse(asOf) > Date.now()) return reply(route, { status: 400, json: problem(400, 'INVALID_PARAMETER') });
    if (parts[1] === 'sources') {
      evidence.sources.push(url);
      const custom = options.source?.(parts[2], url, evidence.sources.length);
      if (custom) return reply(route, custom);
      const all = Object.entries(byRecord).flatMap(([recordId, rows]) => rows.map((candidate) => ({ recordId, candidate })));
      const found = all.find(({ candidate }) => candidate.id === parts[2]);
      if (!found || (asOf && found.candidate.recordedAt > asOf)) return reply(route, { status: 404, json: problem(404, 'NOT_FOUND') });
      return reply(route, { json: sourceBody(found.candidate, byRecord[found.recordId], found.recordId) });
    }
    const recordId = parts[2];
    const rows = byRecord[recordId];
    if (!rows) return reply(route, { status: 404, json: problem(404, 'NOT_FOUND') });
    if (parts[3] === 'timeline') {
      evidence.timelines.push(url);
      const custom = options.timeline?.(url, evidence.timelines.length);
      if (custom) return reply(route, custom);
      const kinds = url.searchParams.getAll('kind');
      const from = url.searchParams.get('from');
      const to = url.searchParams.get('to');
      const filtered = current(rows, asOf).map(timelineItem)
        .filter((item) => !kinds.length || kinds.includes(item.kind))
        .filter((item) => item.eventDate.precision === 'UNKNOWN' || ((!from || (item.eventDate.instant ?? '') >= from) && (!to || (item.eventDate.instant ?? '') < to)))
        .sort(order);
      const limit = Number(url.searchParams.get('limit') ?? 25);
      const offset = Number(Buffer.from(url.searchParams.get('cursor') ?? '', 'base64').toString() || 0);
      const more = offset + limit < filtered.length;
      return reply(route, { json: { items: filtered.slice(offset, offset + limit), nextCursor: more ? Buffer.from(String(offset + limit)).toString('base64') : null } });
    }
    if (parts[3] === 'history') {
      evidence.histories.push(url);
      const custom = options.history?.(url, evidence.histories.length);
      if (custom) return reply(route, custom);
      return reply(route, { json: historyBody(statementsByRecord[recordId] ?? [], rows, asOf) });
    }
    return reply(route, { json: { items: [], nextCursor: null } });
  });
  return { calls, evidence };
}

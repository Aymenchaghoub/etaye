import type { Page } from '@playwright/test';
import type { Measurement, MeasurementPage, MeasurementRevisionList } from '../../src/api/generated/types.gen';
import { claire, ids, louis, problem, reply, type Reply, tilleuls, type Handlers } from './patients';
import { type RecordStub, stubRecord } from './record';

/** One immutable revision of a stub measurement, shaped like the seeded rows of the synthetic fixture (Claire Martin). */
export type Meas = {
  id: string; lineage: string; revision: number; concept: string; value: string; unit: string; at: string; recordedAt: string;
  author: string; sourceId: string; reference: string; sourceType?: Measurement['sourceType']; group?: string; reason?: string; supersedes?: string;
};

const nora = 'Dr Nora Lefebvre';
const meas = (n: number, patch: Partial<Meas> & Pick<Meas, 'concept' | 'value' | 'unit' | 'at' | 'recordedAt'>): Meas => ({
  id: ids(9000 + n), lineage: ids(9600 + n), revision: 1, author: nora, sourceId: ids(5000 + n), reference: `SRC-${n}`, ...patch,
});

export const SYSTOLIC = 'SYSTOLIC_BLOOD_PRESSURE';
export const DIASTOLIC = 'DIASTOLIC_BLOOD_PRESSURE';

/** The measurements of Claire in the design fixture: two blood-pressure readings and the weight with its correction. */
export const claireMeasurements: Meas[] = [
  meas(101, { concept: SYSTOLIC, value: '128', unit: 'mmHg', at: '2026-09-14T07:35:00Z', recordedAt: '2026-09-14T07:36:00Z', sourceId: ids(5008), reference: 'SRC-A-008', group: ids(9501), lineage: ids(9701) }),
  meas(102, { concept: DIASTOLIC, value: '80', unit: 'mmHg', at: '2026-09-14T07:35:00Z', recordedAt: '2026-09-14T07:36:00Z', sourceId: ids(5008), reference: 'SRC-A-008', group: ids(9501), lineage: ids(9702) }),
  meas(103, { concept: SYSTOLIC, value: '134', unit: 'mmHg', at: '2026-10-20T08:10:00Z', recordedAt: '2026-10-20T08:12:00Z', sourceId: ids(5004), reference: 'SRC-A-004', group: ids(9502), lineage: ids(9703) }),
  meas(104, { concept: DIASTOLIC, value: '82', unit: 'mmHg', at: '2026-10-20T08:10:00Z', recordedAt: '2026-10-20T08:12:00Z', sourceId: ids(5004), reference: 'SRC-A-004', group: ids(9502), lineage: ids(9704) }),
  meas(105, { concept: 'WEIGHT', value: '86.4', unit: 'kg', at: '2026-10-20T08:12:00Z', recordedAt: '2026-10-20T08:13:00Z', sourceId: ids(5007), reference: 'SRC-A-007-v1', lineage: ids(9705) }),
  meas(105, { id: ids(9205), concept: 'WEIGHT', value: '68.4', unit: 'kg', at: '2026-10-20T08:12:00Z', recordedAt: '2026-10-20T08:15:00Z', sourceId: ids(5107), reference: 'SRC-A-007-v2', lineage: ids(9705), revision: 2, supersedes: ids(9105), reason: 'Correction d’une erreur de saisie, 86,4 devient 68,4 kg.' }),
];

export function measurementBody(row: Meas): Measurement {
  return {
    id: row.id, version: row.revision, createdAt: row.recordedAt, updatedAt: row.recordedAt, practiceId: tilleuls.practiceId,
    concept: row.concept, value: row.value, unit: row.unit, observedAt: row.at, sourceType: row.sourceType ?? 'PRACTITIONER_OBSERVED',
    sourceRevisionId: row.sourceId, recordedAt: row.recordedAt, authorLabel: row.author, sourceReference: row.reference, revision: row.revision,
    ...(row.group ? { groupId: row.group } : {}), ...(row.reason ? { correctionReason: row.reason } : {}),
    ...(row.supersedes ? { supersedesId: row.supersedes } : {}),
  };
}

/** The current revision of every lineage recorded at or before an instant: the rule of the UD-32 view, written again for the stub. */
function currentRevisions(rows: Meas[], asOf: string | null): Meas[] {
  const visible = rows.filter((candidate) => !asOf || Date.parse(candidate.recordedAt) <= Date.parse(asOf));
  const latest = new Map<string, Meas>();
  for (const candidate of visible) {
    const known = latest.get(candidate.lineage);
    if (!known || candidate.revision > known.revision) latest.set(candidate.lineage, candidate);
  }
  return [...latest.values()];
}

export type MeasurementStub = {
  measurements?: Record<string, Meas[]>;
  /** Replaces the answer to a measurements request; `undefined` keeps the stub's own answer. */
  list?: (url: URL, count: number) => Reply | undefined;
  /** Replaces the answer to a lineage request. */
  revisions?: (id: string, url: URL, count: number) => Reply | undefined;
};
export type MeasurementCalls = { lists: URL[]; revisions: URL[] };

/** The measurements of the synthetic dataset behind the real operations' shape, on top of the record evidence stub. */
export async function stubMeasurements(page: Page, options: MeasurementStub = {}, record: RecordStub = {}, handlers: Handlers = {}) {
  const base = await stubRecord(page, record, handlers);
  const byRecord = options.measurements ?? { [claire.recordId]: claireMeasurements, [louis.recordId]: [] };
  const calls: MeasurementCalls = { lists: [], revisions: [] };
  await page.route((url) => /\/api\/v1\/practices\/[^/]+\/(records\/[^/]+\/measurements|measurements\/[^/]+\/revisions)$/.test(url.pathname), async (route) => {
    const url = new URL(route.request().url());
    const parts = url.pathname.replace('/api/v1/practices/', '').split('/');
    const asOf = url.searchParams.get('asOf');
    if (asOf && Date.parse(asOf) > Date.now()) return reply(route, { status: 400, json: problem(400, 'INVALID_PARAMETER') });
    if (parts[1] === 'measurements') {
      calls.revisions.push(url);
      const custom = options.revisions?.(parts[2], url, calls.revisions.length);
      if (custom) return reply(route, custom);
      const all = Object.values(byRecord).flat();
      const found = all.find((candidate) => candidate.id === parts[2]);
      if (!found || (asOf && Date.parse(found.recordedAt) > Date.parse(asOf))) return reply(route, { status: 404, json: problem(404, 'NOT_FOUND') });
      const lineage = all.filter((candidate) => candidate.lineage === found.lineage && (!asOf || Date.parse(candidate.recordedAt) <= Date.parse(asOf)))
        .sort((a, b) => a.revision - b.revision).map(measurementBody);
      return reply(route, { json: { items: lineage } satisfies MeasurementRevisionList });
    }
    calls.lists.push(url);
    const custom = options.list?.(url, calls.lists.length);
    if (custom) return reply(route, custom);
    const rows = byRecord[parts[2]];
    if (!rows) return reply(route, { status: 404, json: problem(404, 'NOT_FOUND') });
    const concepts = url.searchParams.getAll('concept');
    const from = url.searchParams.get('from');
    const filtered = currentRevisions(rows, asOf)
      .filter((row) => !concepts.length || concepts.includes(row.concept))
      .filter((row) => !from || Date.parse(row.at) >= Date.parse(from))
      .sort((a, b) => Date.parse(b.at) - Date.parse(a.at) || b.recordedAt.localeCompare(a.recordedAt) || b.id.localeCompare(a.id));
    const limit = Number(url.searchParams.get('limit') ?? 25);
    const offset = Number(Buffer.from(url.searchParams.get('cursor') ?? '', 'base64').toString() || 0);
    const more = offset + limit < filtered.length;
    const body: MeasurementPage = { items: filtered.slice(offset, offset + limit).map(measurementBody), nextCursor: more ? Buffer.from(String(offset + limit)).toString('base64') : null };
    return reply(route, { json: body });
  });
  return { ...base, measurementCalls: calls };
}

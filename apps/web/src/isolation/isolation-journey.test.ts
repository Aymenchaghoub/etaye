import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import type { AccessContext, Record as PatientRecord } from '../api/generated/types.gen';
import {
  classify, frenchDate, isIsolationArea, isIsolationPath, journeyContext, matrixCase, OUTSIDE_PATH, PRACTICE_A,
  READ_PATH, RECORD_A, stepOf,
} from './isolation-journey';

const context = (kind: AccessContext['kind'], practiceId: string, grantId = '11111111-1111-4111-8111-111111111111'): AccessContext => ({
  kind, grantId, practiceId, practiceName: 'Cabinet des Tilleuls', displayRole: kind === 'PORTAL' ? 'PATIENT' : 'CLINICAL',
});

const record = (overrides: Partial<PatientRecord> = {}): PatientRecord => ({
  id: RECORD_A, version: 0, createdAt: '2026-11-02T15:00:00Z', updatedAt: '2026-11-02T15:00:00Z', practiceId: PRACTICE_A,
  patientId: '44444444-4444-4444-8444-444444444444', recordRevision: 8,
  patient: {
    id: '44444444-4444-4444-8444-444444444444', version: 0, createdAt: '2026-11-02T15:00:00Z',
    updatedAt: '2026-11-02T15:00:00Z', practiceId: PRACTICE_A, firstName: 'Claire', lastName: 'Martin',
    dateOfBirth: '1978-04-18', contactEmail: null, phone: null, recordId: RECORD_A,
  },
  ...overrides,
});

describe('journey paths', () => {
  it('accepts only the two known steps as a sign-in return and treats the rest of the namespace as old links', () => {
    expect([READ_PATH, OUTSIDE_PATH].every(isIsolationPath)).toBe(true);
    for (const path of ['/compte', '/compte/isolation', '/compte/isolation/lecture/', '//evil.example/compte/isolation/lecture', null]) {
      expect(isIsolationPath(path)).toBe(false);
    }
    expect(isIsolationArea('/compte/isolation/ancien-lien')).toBe(true);
    expect(isIsolationArea('/compte/pratiques/x')).toBe(false);
    expect(stepOf(READ_PATH)).toBe('lecture');
    expect(stepOf(OUTSIDE_PATH)).toBe('hors-cabinet');
    expect(stepOf('/compte/isolation/ancien-lien')).toBeUndefined();
  });
});

describe('journey context', () => {
  it('reads through the own grant in the fictional practice, preferring a staff membership', () => {
    const other = '99999999-9999-4999-8999-999999999999';
    expect(journeyContext([context('MEMBERSHIP', other), context('MEMBERSHIP', PRACTICE_A)])?.audience).toBe('staff');
    expect(journeyContext([context('PORTAL', PRACTICE_A), context('MEMBERSHIP', PRACTICE_A)])?.audience).toBe('staff');
    expect(journeyContext([context('PORTAL', PRACTICE_A)])?.audience).toBe('patient');
    expect(journeyContext([context('MEMBERSHIP', other)])).toBeUndefined();
    expect(journeyContext([])).toBeUndefined();
  });
});

describe('classification of the actual answer', () => {
  it('shows the record only for the authorized step and the expected fictional record', () => {
    expect(classify('lecture', 'staff', { ok: true, data: record() }))
      .toEqual({ kind: 'allowed', name: 'Claire Martin', birthDate: '1978-04-18' });
    const unexpected = [
      classify('hors-cabinet', 'staff', { ok: true, data: record() }),
      classify('lecture', 'patient', { ok: true, data: record() }),
      classify('lecture', 'staff', { ok: true, data: record({ id: '44444444-4444-4444-8444-444444444444' }) }),
      classify('lecture', 'staff', { ok: true, data: record({ practiceId: '99999999-9999-4999-8999-999999999999' }) }),
      classify('lecture', 'staff', { ok: true, data: undefined }),
      classify('lecture', 'staff', { ok: true, data: record({ patient: { ...record().patient, dateOfBirth: 'hier' } }) }),
    ];
    for (const outcome of unexpected) expect(outcome).toEqual({ kind: 'failed' });
  });

  it('maps each refusal to its state and never to a disclosure', () => {
    expect(classify('lecture', 'staff', { ok: false, status: 403 })).toEqual({ kind: 'restricted' });
    expect(classify('hors-cabinet', 'staff', { ok: false, status: 404 })).toEqual({ kind: 'hidden' });
    expect(classify('lecture', 'patient', { ok: false, status: 404 })).toEqual({ kind: 'patient' });
    expect(classify('lecture', 'staff', { ok: false, status: 401 })).toEqual({ kind: 'expired' });
    for (const status of [0, 400, 429, 500, 503]) {
      expect(classify('lecture', 'staff', { ok: false, status })).toEqual({ kind: 'failed' });
    }
    // A patient is never told about a permission: 403 on the staff route is not an expected answer for them.
    expect(classify('lecture', 'patient', { ok: false, status: 403 })).toEqual({ kind: 'failed' });
  });
});

describe('ties to the CI matrix report', () => {
  const manifest = JSON.parse(readFileSync(
    new URL('../../../api/src/test/resources/isolation/matrix-cases.json', import.meta.url), 'utf8',
  )) as { cases: { id: string; expected: string }[] };

  it('uses only labels of mandatory measured cases with the same expected status', () => {
    const labels = [
      ['lecture', 'staff', 'allowed', '200'],
      ['lecture', 'staff', 'restricted', '403'],
      ['lecture', 'patient', 'patient', '404'],
      ['hors-cabinet', 'staff', 'hidden', '404'],
    ] as const;
    for (const [step, audience, outcome, status] of labels) {
      const id = matrixCase(step, audience, outcome);
      const measured = manifest.cases.find((entry) => entry.id === id);
      expect(measured, `${step}/${audience}/${outcome}`).toBeDefined();
      expect(measured?.expected.startsWith(status)).toBe(true);
    }
    expect(matrixCase('lecture', 'staff', 'failed')).toBeUndefined();
  });
});

describe('dates', () => {
  it('writes the birth date in French without a time zone shift', () => {
    expect(frenchDate('1978-04-18')).toBe('18 avril 1978');
    expect(frenchDate('1990-01-01')).toBe('1 janvier 1990');
  });
});

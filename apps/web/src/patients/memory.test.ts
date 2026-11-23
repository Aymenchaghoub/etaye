import { beforeEach, describe, expect, it } from 'vitest';
import type { Patient } from '../api/generated/types.gen';
import {
  activateScope, clearDraft, clearPatientMemory, type DraftRecord, readDraft, readSearch, rememberConfirmation, retainScope,
  scopeKey, takeConfirmation, writeDraft, writeSearch,
} from './memory';

const account = '33333333-3333-4333-8333-333333333333';
const otherAccount = '44444444-4444-4444-8444-444444444444';
const grantA = '11111111-1111-4111-8111-111111111111';
const grantB = '22222222-2222-4222-8222-222222222222';
const a = scopeKey(account, grantA);
const b = scopeKey(account, grantB);
const draft: DraftRecord = {
  values: { lastName: 'Dupont', firstName: 'Jeanne', day: '02', month: '06', year: '1985', phone: '', email: '' },
  baseVersion: 7,
  confirmed: ['r-1'],
  attempt: { body: '{"a":1}', key: 'key-0000000000000001' },
};
const saved = { id: 'p', recordId: 'r' } as Patient;

beforeEach(() => clearPatientMemory());

describe('patient memory', () => {
  it('scopes the query to one account and one practice grant and keeps no result row', () => {
    expect(scopeKey(account, grantA)).not.toBe(scopeKey(otherAccount, grantA));
    activateScope(a);
    writeSearch(a, { q: 'Martin' });
    expect(readSearch(a)).toEqual({ q: 'Martin' });
    expect(readSearch(b)).toBeUndefined();
    expect(Object.keys(readSearch(a) ?? {})).toEqual(['q']);
  });

  it('drops everything held for the previous scope when another practice is entered', () => {
    activateScope(a);
    writeSearch(a, { q: 'Martin' });
    writeDraft(a, 'new', draft);
    rememberConfirmation(a, { kind: 'created', patient: saved });
    activateScope(b);
    expect(readSearch(a)).toBeUndefined();
    expect(readDraft(a, 'new')).toBeUndefined();
    expect(takeConfirmation(a)).toBeUndefined();
  });

  it('keeps the memory when the same scope is entered again, for example after a failed recheck', () => {
    activateScope(a);
    writeSearch(a, { q: 'Martin' });
    activateScope(a);
    expect(readSearch(a)?.q).toBe('Martin');
  });

  it('drops the previous search when a destination of another practice, or the chooser, is shown', () => {
    activateScope(a);
    writeSearch(a, { q: 'Martin' });
    writeDraft(a, 'new', draft);
    retainScope(a);
    expect(readSearch(a)?.q).toBe('Martin');
    retainScope(b);
    expect(readSearch(a)).toBeUndefined();
    expect(readDraft(a, 'new')).toBeUndefined();
    activateScope(a);
    writeSearch(a, { q: 'Claire' });
    retainScope(undefined);
    expect(readSearch(a)).toBeUndefined();
  });

  it('clears queries, drafts and confirmations on sign-out and reset', () => {
    activateScope(a);
    writeSearch(a, { q: 'Martin' });
    writeDraft(a, 'new', draft);
    rememberConfirmation(a, { kind: 'updated', patient: saved });
    clearPatientMemory();
    expect(readSearch(a)).toBeUndefined();
    expect(readDraft(a, 'new')).toBeUndefined();
    expect(takeConfirmation(a)).toBeUndefined();
  });

  it('keeps the base version, the confirmed candidates and the attempt with each draft', () => {
    writeDraft(a, 'new', draft);
    writeDraft(a, 'record-1', { ...draft, values: { ...draft.values, lastName: 'Martin' }, baseVersion: 3 });
    expect(readDraft(a, 'new')).toEqual(draft);
    expect(readDraft(a, 'record-1')).toMatchObject({ baseVersion: 3, confirmed: ['r-1'], attempt: { key: 'key-0000000000000001' } });
    clearDraft(a, 'new');
    expect(readDraft(a, 'new')).toBeUndefined();
    expect(readDraft(a, 'record-1')?.values.lastName).toBe('Martin');
  });

  it('consumes a confirmation once and keeps the saved patient for the pinned row', () => {
    rememberConfirmation(a, { kind: 'created', patient: saved });
    expect(takeConfirmation(a)).toEqual({ kind: 'created', patient: saved });
    expect(takeConfirmation(a)).toBeUndefined();
  });
});

import { beforeEach, describe, expect, it } from 'vitest';
import type { Patient } from '../api/generated/types.gen';
import {
  activateScope, clearDraft, clearPatientMemory, type Draft, markStale, readDraft, readSearch, rememberConfirmation, scopeKey,
  takeConfirmation, writeDraft, writeSearch,
} from './memory';

const account = '33333333-3333-4333-8333-333333333333';
const otherAccount = '44444444-4444-4444-8444-444444444444';
const grantA = '11111111-1111-4111-8111-111111111111';
const grantB = '22222222-2222-4222-8222-222222222222';
const a = scopeKey(account, grantA);
const b = scopeKey(account, grantB);
const draft: Draft = { lastName: 'Dupont', firstName: 'Jeanne', day: '02', month: '06', year: '1985', phone: '', email: '' };
const found = { id: 'p', recordId: 'r' } as Patient;

beforeEach(() => clearPatientMemory());

describe('patient memory', () => {
  it('scopes a search to one account and one practice grant', () => {
    expect(scopeKey(account, grantA)).not.toBe(scopeKey(otherAccount, grantA));
    activateScope(a);
    writeSearch(a, { q: 'Martin', items: [found], nextCursor: null, loadedAt: 1 });
    expect(readSearch(a)?.q).toBe('Martin');
    expect(readSearch(b)).toBeUndefined();
  });

  it('drops everything held for the previous scope when another practice is entered', () => {
    activateScope(a);
    writeSearch(a, { q: 'Martin', items: [found], nextCursor: null, loadedAt: 1 });
    writeDraft(a, 'new', draft);
    rememberConfirmation(a, { kind: 'created', recordId: 'r', name: 'DUPONT Jeanne' });
    activateScope(b);
    expect(readSearch(a)).toBeUndefined();
    expect(readDraft(a, 'new')).toBeUndefined();
    expect(takeConfirmation(a)).toBeUndefined();
  });

  it('keeps the memory when the same scope is entered again, for example after a failed recheck', () => {
    activateScope(a);
    writeSearch(a, { q: 'Martin', items: [], nextCursor: null, loadedAt: 1 });
    activateScope(a);
    expect(readSearch(a)?.q).toBe('Martin');
  });

  it('clears searches, drafts and confirmations on sign-out, expiry and reset', () => {
    activateScope(a);
    writeSearch(a, { q: 'Martin', items: [], nextCursor: null, loadedAt: 1 });
    writeDraft(a, 'new', draft);
    rememberConfirmation(a, { kind: 'updated', recordId: 'r', name: 'MARTIN Claire' });
    clearPatientMemory();
    expect(readSearch(a)).toBeUndefined();
    expect(readDraft(a, 'new')).toBeUndefined();
    expect(takeConfirmation(a)).toBeUndefined();
  });

  it('keeps one draft per form and discards it on request', () => {
    writeDraft(a, 'new', draft);
    writeDraft(a, 'record-1', { ...draft, lastName: 'Martin' });
    expect(readDraft(a, 'new')?.lastName).toBe('Dupont');
    expect(readDraft(a, 'record-1')?.lastName).toBe('Martin');
    clearDraft(a, 'new');
    expect(readDraft(a, 'new')).toBeUndefined();
    expect(readDraft(a, 'record-1')?.lastName).toBe('Martin');
  });

  it('consumes a confirmation once', () => {
    rememberConfirmation(a, { kind: 'created', recordId: 'r', name: 'DUPONT Jeanne' });
    expect(takeConfirmation(a)).toEqual({ kind: 'created', recordId: 'r', name: 'DUPONT Jeanne' });
    expect(takeConfirmation(a)).toBeUndefined();
  });

  it('marks the remembered list stale without losing its results', () => {
    writeSearch(a, { q: '', items: [found], nextCursor: null, loadedAt: 1 });
    markStale(a, 99);
    expect(readSearch(a)).toMatchObject({ staleAt: 99, items: [found] });
    markStale(b, 99);
    expect(readSearch(b)).toBeUndefined();
  });
});

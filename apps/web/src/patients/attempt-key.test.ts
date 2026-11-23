import { describe, expect, it } from 'vitest';
import { AttemptKey, newIdempotencyKey } from './attempt-key';

function counter() {
  let n = 0;
  return () => `key-${String(++n).padStart(16, '0')}`;
}

describe('idempotency key per attempt', () => {
  it('generates keys long enough for the contract', () => {
    const key = newIdempotencyKey();
    expect(key.length).toBeGreaterThanOrEqual(16);
    expect(key.length).toBeLessThanOrEqual(128);
    expect(newIdempotencyKey()).not.toBe(key);
  });

  it('keeps the key while the body is the same, whatever the property order', () => {
    const keys = new AttemptKey(counter());
    const first = keys.keyFor({ firstName: 'Jeanne', lastName: 'Dupont', phone: null });
    expect(keys.keyFor({ phone: null, lastName: 'Dupont', firstName: 'Jeanne' })).toBe(first);
    expect(keys.keyFor({ firstName: 'Jeanne', lastName: 'Dupont', phone: null, ignored: undefined })).toBe(first);
  });

  it('rotates the key as soon as the body changes, including the confirmation', () => {
    const keys = new AttemptKey(counter());
    const body = { firstName: 'Claire', lastName: 'Martin' };
    const first = keys.keyFor(body);
    const confirmed = keys.keyFor({ ...body, confirmedDistinctRecordIds: ['b', 'a'] });
    expect(confirmed).not.toBe(first);
    // The named record IDs are a set: their order does not make a new attempt.
    expect(keys.keyFor({ ...body, confirmedDistinctRecordIds: ['a', 'b'] })).toBe(confirmed);
    expect(keys.keyFor({ ...body, confirmedDistinctRecordIds: ['a', 'b', 'c'] })).not.toBe(confirmed);
  });

  it('starts over after a reset', () => {
    const keys = new AttemptKey(counter());
    const first = keys.keyFor({ a: 1 });
    keys.reset();
    expect(keys.keyFor({ a: 1 })).not.toBe(first);
  });
});

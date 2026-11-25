import { describe, expect, it } from 'vitest';
import { dayStart, instantParts, nextDayStart, parseAsOf, parseFilterDay, parisInstant, parisInstants } from './asof';

const now = new Date('2026-11-24T12:00:00Z');
const parts = (day: string, month: string, year: string, hour: string, minute: string) => ({ day, month, year, hour, minute });

describe('Paris wall clock to instant', () => {
  it('applies the summer and the winter offset', () => {
    expect(parisInstant(2026, 10, 20, 10, 14)?.toISOString()).toBe('2026-10-20T08:14:00.000Z');
    expect(parisInstant(2026, 11, 2, 9, 10)?.toISOString()).toBe('2026-11-02T08:10:00.000Z');
    expect(parisInstant(2026, 1, 15, 0, 0)?.toISOString()).toBe('2026-01-14T23:00:00.000Z');
  });

  it('refuses the hour that clocks skip and the hour that repeats, instead of choosing one reading', () => {
    expect(parisInstant(2026, 3, 29, 2, 30)).toBeUndefined();
    expect(parisInstant(2026, 3, 29, 3, 0)?.toISOString()).toBe('2026-03-29T01:00:00.000Z');
    expect(parisInstants(2026, 10, 25, 2, 30).map((at) => at.toISOString())).toEqual(['2026-10-25T00:30:00.000Z', '2026-10-25T01:30:00.000Z']);
    expect(parisInstant(2026, 10, 25, 2, 30)).toBeUndefined();
    expect(parisInstant(2026, 10, 25, 3, 0)?.toISOString()).toBe('2026-10-25T02:00:00.000Z');
    expect(parseAsOf(parts('25', '10', '2026', '02', '30'), now)).toMatchObject({ ok: false, summary: 'Date et heure : cette heure existe deux fois' });
  });

  it('round-trips an instant to the fields of the control', () => {
    expect(instantParts(new Date('2026-10-20T08:14:00Z'))).toEqual(parts('20', '10', '2026', '10', '14'));
  });
});

describe('the past-instant control', () => {
  it('accepts a past instant', () => {
    const result = parseAsOf(parts('20', '10', '2026', '10', '14'), now);
    expect(result.ok && result.value.toISOString()).toBe('2026-10-20T08:14:00.000Z');
  });

  it('refuses a future instant before any request', () => {
    const result = parseAsOf(parts('25', '11', '2026', '09', '00'), now);
    expect(result).toMatchObject({ ok: false, summary: 'Date et heure : une date future ne peut pas être affichée' });
  });

  it('refuses empty, partial, malformed and impossible input with a summary', () => {
    expect(parseAsOf(parts('', '', '', '', ''), now)).toMatchObject({ ok: false, summary: 'Date et heure : obligatoires' });
    expect(parseAsOf(parts('20', '10', '2026', '', ''), now)).toMatchObject({ ok: false, summary: 'Date et heure : format incorrect' });
    expect(parseAsOf(parts('31', '02', '2026', '10', '00'), now)).toMatchObject({ ok: false, summary: 'Date et heure : cet instant n’existe pas' });
    expect(parseAsOf(parts('20', '10', '2026', '24', '00'), now)).toMatchObject({ ok: false });
    expect(parseAsOf(parts('29', '03', '2026', '02', '30'), now)).toMatchObject({ ok: false, summary: 'Date et heure : cette heure n’existe pas' });
  });
});

describe('the date filter', () => {
  it('allows an empty day and refuses a partial one', () => {
    expect(parseFilterDay('Du', { day: '', month: '', year: '' })).toEqual({ ok: true, value: undefined });
    expect(parseFilterDay('Du', { day: '01', month: '', year: '2026' })).toMatchObject({ ok: false, summary: 'Du : date incorrecte' });
    expect(parseFilterDay('Au', { day: '30', month: '02', year: '2026' })).toMatchObject({ ok: false });
  });

  it('keeps a date bound on a day whose midnight repeats (clocks went back at 00:00 on 26/09/1976)', () => {
    expect(dayStart({ year: 1976, month: 9, day: 26 })).toBeDefined();
    expect(nextDayStart({ year: 1976, month: 9, day: 25 })).toBeDefined();
    expect(nextDayStart({ year: 1976, month: 9, day: 26 })).toBeDefined();
  });

  it('bounds a day by its Paris midnights so the last day is included in full', () => {
    const day = { year: 2026, month: 10, day: 20 };
    expect(dayStart(day)?.toISOString()).toBe('2026-10-19T22:00:00.000Z');
    expect(nextDayStart(day)?.toISOString()).toBe('2026-10-20T22:00:00.000Z');
    expect(nextDayStart({ year: 2026, month: 10, day: 31 })?.toISOString()).toBe('2026-10-31T23:00:00.000Z');
  });
});

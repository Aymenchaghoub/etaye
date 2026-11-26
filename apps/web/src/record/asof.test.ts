import { describe, expect, it } from 'vitest';
import {
  dayStart, EMPTY_DAY, EMPTY_INSTANT, instantParts, monthsBefore, nextDayStart, parseAsOf, parseFilterDay,
  parisInstant, parisInstants, wallClock, zoneInstant, zoneInstants,
} from './asof';

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

  it('kills boundary, detail-wording, non-Paris zone and DST edge survivors', () => {
    expect(EMPTY_INSTANT).toEqual({ day: '', month: '', year: '', hour: '', minute: '' });
    expect(EMPTY_DAY).toEqual({ day: '', month: '', year: '' });

    // Santiago skips midnight on 2019-09-08: day starts at 01:00 CLT (04:00Z).
    expect(zoneInstants('America/Santiago', 2019, 9, 8, 0, 30)).toEqual([]);
    expect(zoneInstant('America/Santiago', 2019, 9, 8, 0, 30)).toBeUndefined();
    expect(dayStart({ year: 2019, month: 9, day: 8 }, 'America/Santiago')?.toISOString()).toBe('2019-09-08T04:00:00.000Z');
    // Leap-day monthsBefore shifted when wall clock hits skipped DST hour.
    expect(monthsBefore('America/Santiago', new Date('2019-12-08T03:00:00Z'), 3).toISOString()).toBe('2019-09-08T04:00:00.000Z');

    // wallClock and exact boundaries (min/max month, day, hour, minute, year, equality to now).
    expect(wallClock('Europe/Paris', new Date('2026-10-20T08:14:00Z'))).toEqual({ year: 2026, month: 10, day: 20, hour: 10, minute: 14 });
    expect(parseAsOf(parts('01', '01', '1900', '00', '00'), now).ok).toBe(true);
    expect(parseAsOf(parts('31', '12', '2025', '23', '59'), now).ok).toBe(true);
    expect(parseAsOf(parts('29', '02', '2024', '12', '00'), now).ok).toBe(true);
    expect(parseAsOf(parts('24', '11', '2026', '13', '00'), now).ok).toBe(true); // Exact equality to now (UTC 12:00)
    expect(parseAsOf(parts('24', '11', '2026', '13', '01'), now)).toEqual({
      ok: false,
      summary: 'Date et heure : une date future ne peut pas être affichée',
      detail: 'Erreur : une date future ne peut pas être affichée. Choisissez un instant passé.',
    });

    // Exact detail wording in Paris vs another zone and numeric bounds.
    expect(parseAsOf(parts(' ', ' ', ' ', ' ', ' '), now)).toEqual({
      ok: false,
      summary: 'Date et heure : obligatoires',
      detail: 'Erreur : indiquez la date et l’heure. Format JJ/MM/AAAA et HH:MM, heure de Paris.',
    });
    expect(parseAsOf(parts('20', '10', '2026', 'aa', '00'), now, 'America/Martinique')).toEqual({
      ok: false,
      summary: 'Date et heure : format incorrect',
      detail: 'Erreur : saisissez la date et l’heure en chiffres. Format JJ/MM/AAAA et HH:MM, heure du cabinet.',
    });
    for (const bad of [
      parts('0', '1', '2026', '10', '00'),
      parts('1', '0', '2026', '10', '00'),
      parts('1', '13', '2026', '10', '00'),
      parts('1', '1', '1899', '10', '00'),
      parts('1', '1', '2026', '24', '00'),
      parts('1', '1', '2026', '10', '60'),
      parts('29', '02', '2025', '10', '00'),
    ]) {
      expect(parseAsOf(bad, now)).toEqual({
        ok: false,
        summary: 'Date et heure : cet instant n’existe pas',
        detail: 'Erreur : cette date ou cette heure n’existe pas. Format JJ/MM/AAAA et HH:MM, heure de Paris.',
      });
    }
    expect(parseAsOf(parts('29', '03', '2026', '02', '30'), now, 'Europe/Paris')).toMatchObject({
      ok: false, detail: expect.stringContaining('à Paris (changement d’heure)'),
    });
    expect(parseAsOf(parts('25', '10', '2026', '02', '30'), now, 'Europe/Paris')).toMatchObject({
      ok: false, detail: expect.stringContaining('à Paris (changement d’heure)'),
    });

    expect(parseFilterDay('Au', { day: ' ', month: '', year: '' })).toEqual({ ok: true, value: undefined });
    for (const badDay of [
      { day: '1', month: '0', year: '2026' },
      { day: '1', month: '13', year: '2026' },
      { day: '0', month: '1', year: '2026' },
      { day: '1', month: '1', year: '1899' },
      { day: '29', month: '02', year: '2026' },
    ]) {
      expect(parseFilterDay('Au', badDay)).toEqual({
        ok: false,
        summary: 'Au : date incorrecte',
        detail: 'Erreur : saisissez une date existante. Format JJ/MM/AAAA.',
      });
    }
    expect(parseFilterDay('Du', { day: ' 1 ', month: ' 12 ', year: '2026' })).toEqual({ ok: true, value: { year: 2026, month: 12, day: 1 } });
  });
});

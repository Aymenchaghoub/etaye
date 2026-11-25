import { describe, expect, it } from 'vitest';
import { dayStart, instantParts, nextDayStart, parseAsOf, zoneInstant, zoneInstants } from './asof';
import { clock, eventLabel, formatInstant, sectionWords } from './evidence';
import { DEFAULT_ZONE, hasUsableZone, resolveZone, zoneWords } from './zone';

const now = new Date('2026-11-25T12:00:00Z');
const parts = (day: string, month: string, year: string, hour: string, minute: string) => ({ day, month, year, hour, minute });

describe('the practice zone of the session context (D-64)', () => {
  it('uses the zone the context carries and falls back to Paris for a missing, empty or unknown one', () => {
    expect(resolveZone('America/Martinique')).toBe('America/Martinique');
    expect(resolveZone(' Pacific/Noumea ')).toBe('Pacific/Noumea');
    for (const value of [undefined, null, '', '  ', 'Not/AZone', 42, {}]) expect(resolveZone(value)).toBe(DEFAULT_ZONE);
  });

  it('says whether the context carried a usable zone, so a fallback to Paris is never silent', () => {
    expect(hasUsableZone('America/Martinique')).toBe(true);
    expect(hasUsableZone(' Europe/Paris ')).toBe(true);
    for (const value of [undefined, null, '', '  ', 'Not/AZone', 42, {}]) expect(hasUsableZone(value)).toBe(false);
  });

  it('names Paris as before and any other zone "heure du cabinet", never a guessed name', () => {
    expect(zoneWords('Europe/Paris')).toEqual({ clock: 'heure de Paris', place: 'à Paris' });
    expect(zoneWords('America/Martinique').clock).toBe('heure du cabinet');
    expect(zoneWords('Pacific/Noumea').place).toBe('dans le fuseau du cabinet');
  });
});

describe('an instant typed by the user is read in the practice zone', () => {
  it('reads 20/10/2026 10:14 as 14:14 UTC in Martinique (UTC-4, no daylight saving) and as 23:14 UTC the day before in Nouméa (UTC+11)', () => {
    const martinique = parseAsOf(parts('20', '10', '2026', '10', '14'), now, 'America/Martinique');
    expect(martinique.ok && martinique.value.toISOString()).toBe('2026-10-20T14:14:00.000Z');
    const noumea = parseAsOf(parts('20', '10', '2026', '10', '14'), now, 'Pacific/Noumea');
    expect(noumea.ok && noumea.value.toISOString()).toBe('2026-10-19T23:14:00.000Z');
    const paris = parseAsOf(parts('20', '10', '2026', '10', '14'), now, 'Europe/Paris');
    expect(paris.ok && paris.value.toISOString()).toBe('2026-10-20T08:14:00.000Z');
  });

  it('shows the same instants back in that zone, with a 24-hour clock', () => {
    expect(clock('2026-10-20T14:14:00Z', 'America/Martinique')).toEqual({ date: '20/10/2026', time: '10:14' });
    expect(formatInstant('2026-10-19T23:14:00Z', 'Pacific/Noumea')).toBe('20/10/2026 10:14');
    expect(eventLabel({ precision: 'INSTANT', instant: '2026-10-20T14:12:00Z' }, 'America/Martinique')).toBe('20/10/2026 10:12');
    expect(instantParts(new Date('2026-10-19T23:14:00Z'), 'Pacific/Noumea')).toEqual(parts('20', '10', '2026', '10', '14'));
  });

  it('does not read an instant of a section in Paris time when the zone is another', () => {
    const statement = { id: 'a', version: 1, createdAt: '', updatedAt: '', practiceId: 'p', category: 'ALLERGY' as const, assertionKind: 'EXPLICITLY_ABSENT' as const,
      eventDate: { precision: 'INSTANT' as const, instant: '2026-09-14T02:30:00Z' }, recordedAt: '2026-09-14T02:45:00Z' };
    expect(sectionWords('ALLERGY', { state: 'EXPLICITLY_ABSENT', statements: [statement] }, false, 'Europe/Paris').chip).toBe('Absence déclarée le 14/09/2026');
    expect(sectionWords('ALLERGY', { state: 'EXPLICITLY_ABSENT', statements: [statement] }, false, 'America/Martinique').chip).toBe('Absence déclarée le 13/09/2026');
  });

  it('refuses an hour that repeats or is skipped in that zone and says so by the zone, not by Paris', () => {
    // Martinique has no change of clocks: the same wall time is never ambiguous.
    expect(zoneInstants('America/Martinique', 2026, 10, 25, 2, 30)).toHaveLength(1);
    expect(zoneInstant('Pacific/Noumea', 2026, 3, 29, 2, 30)?.toISOString()).toBe('2026-03-28T15:30:00.000Z');
    // New Caledonia has no daylight saving either, so a skipped hour is tested in a zone that has one.
    const gap = parseAsOf(parts('08', '03', '2026', '02', '30'), now, 'America/New_York');
    expect(gap).toMatchObject({ ok: false, summary: 'Date et heure : cette heure n’existe pas' });
    expect(!gap.ok && gap.detail).toContain('dans le fuseau du cabinet');
    const twice = parseAsOf(parts('01', '11', '2026', '01', '30'), now, 'America/New_York');
    expect(twice).toMatchObject({ ok: false, summary: 'Date et heure : cette heure existe deux fois' });
    const paris = parseAsOf(parts('25', '10', '2026', '02', '30'), now, 'Europe/Paris');
    expect(!paris.ok && paris.detail).toContain('à Paris');
  });

  it('refuses a future instant whatever the zone, using the zone to read it', () => {
    // 25/11/2026 22:00 in Nouméa is 11:00 UTC: before now (12:00 UTC); 23:30 is 12:30 UTC: in the future.
    expect(parseAsOf(parts('25', '11', '2026', '22', '00'), now, 'Pacific/Noumea').ok).toBe(true);
    expect(parseAsOf(parts('25', '11', '2026', '23', '30'), now, 'Pacific/Noumea')).toMatchObject({ ok: false, summary: 'Date et heure : une date future ne peut pas être affichée' });
  });
});

describe('a day filter in a zone whose clocks go back at midnight (review of story 3.3, E6)', () => {
  it('ends the Saturday of the change at the only midnight after the repeated hour, so no item of that day is left out', () => {
    // Santiago: on Saturday 4 April 2026 the clocks go from 24:00 back to 23:00, so 23:00-24:00 happens twice.
    const day = { year: 2026, month: 4, day: 4 };
    const start = dayStart(day, 'America/Santiago');
    const end = nextDayStart(day, 'America/Santiago');
    expect(start && end).toBeTruthy();
    // 23:30 on the first pass (UTC-3) and on the second (UTC-4) both lie inside the day, before the exclusive bound.
    const firstPass = Date.parse('2026-04-05T02:30:00Z');
    const secondPass = Date.parse('2026-04-05T03:30:00Z');
    for (const at of [firstPass, secondPass]) {
      expect(at).toBeGreaterThanOrEqual(start!.getTime());
      expect(at).toBeLessThan(end!.getTime());
    }
    // The bound is the Sunday midnight, which exists once: 00:00 at UTC-4 is 04:00Z.
    expect(end!.toISOString()).toBe('2026-04-05T04:00:00.000Z');
  });

  it('starts a day whose midnight the clocks skip where the skipped hour ends, never without a bound', () => {
    // Santiago, 8 September 2019: the clocks went from 00:00 (UTC-4) to 01:00 (UTC-3), so there is no 00:00 that day.
    expect(zoneInstants('America/Santiago', 2019, 9, 8, 0, 0)).toEqual([]);
    const day = { year: 2019, month: 9, day: 8 };
    expect(dayStart(day, 'America/Santiago')?.toISOString()).toBe('2019-09-08T04:00:00.000Z');
    // The previous day ends there, and the next day starts at its ordinary midnight (UTC-3).
    expect(nextDayStart({ year: 2019, month: 9, day: 7 }, 'America/Santiago')?.toISOString()).toBe('2019-09-08T04:00:00.000Z');
    expect(nextDayStart(day, 'America/Santiago')?.toISOString()).toBe('2019-09-09T03:00:00.000Z');
  });
});

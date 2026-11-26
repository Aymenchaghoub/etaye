import { describe, expect, it } from 'vitest';
import { ageLabel, ageOn, daysInMonth, EMPTY_DATE, formatDate, isoToParts, parseBirthDate } from './date';

const today = new Date(2026, 10, 23); // 23 November 2026, local time

describe('birth date segments', () => {
  it('builds an ISO date from valid segments', () => {
    expect(parseBirthDate({ day: '18', month: '04', year: '1978' }, today)).toEqual({ ok: true, iso: '1978-04-18' });
    expect(parseBirthDate({ day: '2', month: '6', year: '1985' }, today)).toEqual({ ok: true, iso: '1985-06-02' });
    expect(parseBirthDate({ day: ' 29 ', month: '02', year: '2000' }, today)).toEqual({ ok: true, iso: '2000-02-29' });
  });

  it('asks for the date when every segment is empty', () => {
    const result = parseBirthDate({ day: '', month: '', year: '' }, today);
    expect(result).toMatchObject({ ok: false, problem: { code: 'missing', summary: 'Date de naissance : obligatoire' } });
  });

  it('names the impossible day of a month, with the year only for a leap day', () => {
    expect(parseBirthDate({ day: '31', month: '02', year: '1978' }, today)).toMatchObject({
      ok: false, problem: { code: 'impossible', detail: 'Erreur : le 31 février n’existe pas. Format JJ/MM/AAAA.' },
    });
    expect(parseBirthDate({ day: '29', month: '02', year: '1979' }, today)).toMatchObject({
      ok: false, problem: { detail: 'Erreur : le 29 février 1979 n’existe pas. Format JJ/MM/AAAA.' },
    });
    expect(parseBirthDate({ day: '31', month: '04', year: '1990' }, today)).toMatchObject({
      ok: false, problem: { detail: 'Erreur : le 31 avril n’existe pas. Format JJ/MM/AAAA.' },
    });
  });

  it('rejects a month out of range, non-numeric input, a short year and a year before 1900', () => {
    expect(parseBirthDate({ day: '10', month: '13', year: '1990' }, today)).toMatchObject({ ok: false, problem: { code: 'impossible' } });
    expect(parseBirthDate({ day: '0', month: '5', year: '1990' }, today)).toMatchObject({ ok: false, problem: { code: 'impossible' } });
    expect(parseBirthDate({ day: 'aa', month: '05', year: '1990' }, today)).toMatchObject({ ok: false, problem: { code: 'invalid' } });
    expect(parseBirthDate({ day: '10', month: '05', year: '90' }, today)).toMatchObject({ ok: false, problem: { code: 'invalid' } });
    expect(parseBirthDate({ day: '10', month: '05', year: '1899' }, today)).toMatchObject({ ok: false, problem: { code: 'year' } });
  });

  it('refuses a future date but accepts today', () => {
    expect(parseBirthDate({ day: '24', month: '11', year: '2026' }, today)).toMatchObject({ ok: false, problem: { code: 'future' } });
    expect(parseBirthDate({ day: '23', month: '11', year: '2026' }, today)).toEqual({ ok: true, iso: '2026-11-23' });
  });

  it('knows the length of each month', () => {
    expect(daysInMonth(2024, 2)).toBe(29);
    expect(daysInMonth(2026, 2)).toBe(28);
    expect(daysInMonth(1900, 2)).toBe(28);
    expect(daysInMonth(2026, 11)).toBe(30);
  });
});

describe('display of a stored date', () => {
  it('splits and formats an ISO date and tolerates anything else', () => {
    expect(isoToParts('1978-04-18')).toEqual({ day: '18', month: '04', year: '1978' });
    expect(isoToParts('not a date')).toEqual({ day: '', month: '', year: '' });
    expect(formatDate('1978-04-18')).toBe('18/04/1978');
    expect(formatDate('x')).toBe('x');
  });

  it('counts completed years at render time', () => {
    expect(ageOn('1978-04-18', today)).toBe(48);
    expect(ageOn('1991-07-09', today)).toBe(35);
    // The birthday is tomorrow, so the person is still one year younger.
    expect(ageOn('1990-11-24', today)).toBe(35);
    expect(ageOn('1990-11-23', today)).toBe(36);
    expect(ageOn('2026-12-01', today)).toBeUndefined();
    expect(ageOn('bad', today)).toBeUndefined();
  });

  it('labels the age with the right plural', () => {
    expect(ageLabel('2026-01-01', today)).toBe('0 an');
    expect(ageLabel('2025-11-23', today)).toBe('1 an');
    expect(ageLabel('1978-04-18', today)).toBe('48 ans');
    expect(ageLabel('bad', today)).toBe('');
  });

  it('asserts exact boundary and problem messages across all fields', () => {
    expect(EMPTY_DATE).toEqual({ day: '', month: '', year: '' });
    expect(parseBirthDate({ day: ' ', month: ' ', year: ' ' }, today)).toEqual({
      ok: false,
      problem: { code: 'missing', summary: 'Date de naissance : obligatoire', detail: 'Erreur : indiquez la date de naissance. Format JJ/MM/AAAA.' },
    });
    expect(parseBirthDate({ day: '1', month: '12', year: '1900' }, today)).toEqual({ ok: true, iso: '1900-12-01' });
    expect(parseBirthDate({ day: '31', month: '01', year: '1900' }, today)).toEqual({ ok: true, iso: '1900-01-31' });
    expect(parseBirthDate({ day: '10', month: '0', year: '2000' }, today)).toEqual({
      ok: false,
      problem: { code: 'impossible', summary: 'Date de naissance : cette date n’existe pas', detail: 'Erreur : cette date n’existe pas. Format JJ/MM/AAAA.' },
    });
    expect(parseBirthDate({ day: '10', month: '05', year: '1899' }, today)).toEqual({
      ok: false,
      problem: { code: 'year', summary: 'Date de naissance : année à vérifier', detail: 'Erreur : vérifiez l’année de naissance. Format JJ/MM/AAAA.' },
    });
    expect(parseBirthDate({ day: '10', month: 'aa', year: '2000' }, today)).toEqual({
      ok: false,
      problem: { code: 'invalid', summary: 'Date de naissance : format incorrect', detail: 'Erreur : saisissez le jour, le mois et l’année en chiffres. Format JJ/MM/AAAA.' },
    });
    expect(parseBirthDate({ day: '24', month: '11', year: '2026' }, today)).toEqual({
      ok: false,
      problem: { code: 'future', summary: 'Date de naissance : dans le futur', detail: 'Erreur : la date de naissance ne peut pas être dans le futur.' },
    });
    expect(ageOn('2025-11-23', today)).toBe(1);
    expect(ageOn('2026-11-23', today)).toBe(0);
    expect(ageOn('2025-10-30', today)).toBe(1);
    expect(ageLabel('2024-11-23', today)).toBe('2 ans');
  });
});

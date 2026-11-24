import { PRACTICE_ZONE } from './evidence';

export type InstantParts = { day: string; month: string; year: string; hour: string; minute: string };
export type DayParts = { day: string; month: string; year: string };
export const EMPTY_INSTANT: InstantParts = { day: '', month: '', year: '', hour: '', minute: '' };
export const EMPTY_DAY: DayParts = { day: '', month: '', year: '' };

export type ParseResult<T> = { ok: true; value: T } | { ok: false; summary: string; detail: string };

const offsetFormat = new Intl.DateTimeFormat('en-US', {
  timeZone: PRACTICE_ZONE, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  second: '2-digit', hourCycle: 'h23',
});

/** Offset of the practice zone from UTC at an instant, in milliseconds (positive east of Greenwich). */
function zoneOffset(at: number): number {
  const parts = Object.fromEntries(offsetFormat.formatToParts(new Date(at)).map((part) => [part.type, part.value]));
  const local = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day), Number(parts.hour),
    Number(parts.minute), Number(parts.second));
  return local - Math.floor(at / 1000) * 1000;
}

/**
 * The instant at which the Paris wall clock reads the given date and time, or undefined when that time does not exist
 * (the hour skipped when clocks go forward). The hour repeated when clocks go back reads as its first occurrence.
 */
export function parisInstant(year: number, month: number, day: number, hour: number, minute: number): Date | undefined {
  const wall = Date.UTC(year, month - 1, day, hour, minute);
  // The offsets in force a day before and a day after bracket any change of clocks, so both readings of a repeated hour are tried.
  const candidates = new Set([wall - zoneOffset(wall - 86_400_000), wall - zoneOffset(wall + 86_400_000)]);
  const valid = [...candidates].filter((at) => at + zoneOffset(at) === wall).sort((a, b) => a - b);
  return valid.length ? new Date(valid[0]) : undefined;
}

const daysIn = (year: number, month: number) => new Date(Date.UTC(year, month, 0)).getUTCDate();
const FORMAT = 'Format JJ/MM/AAAA et HH:MM, heure de Paris.';

function number(value: string, digits: RegExp): number | undefined {
  return digits.test(value.trim()) ? Number(value.trim()) : undefined;
}

/** The past instant chosen on the date and time fields. A future instant is refused here and again by the server. */
export function parseAsOf(parts: InstantParts, now: Date = new Date()): ParseResult<Date> {
  const empty = Object.values(parts).every((value) => value.trim() === '');
  if (empty) {
    return { ok: false, summary: 'Date et heure : obligatoires', detail: `Erreur : indiquez la date et l’heure. ${FORMAT}` };
  }
  const day = number(parts.day, /^\d{1,2}$/);
  const month = number(parts.month, /^\d{1,2}$/);
  const year = number(parts.year, /^\d{4}$/);
  const hour = number(parts.hour, /^\d{1,2}$/);
  const minute = number(parts.minute, /^\d{1,2}$/);
  if (day === undefined || month === undefined || year === undefined || hour === undefined || minute === undefined) {
    return { ok: false, summary: 'Date et heure : format incorrect', detail: `Erreur : saisissez la date et l’heure en chiffres. ${FORMAT}` };
  }
  if (month < 1 || month > 12 || day < 1 || day > daysIn(year, month) || hour > 23 || minute > 59 || year < 1900) {
    return { ok: false, summary: 'Date et heure : cet instant n’existe pas', detail: `Erreur : cette date ou cette heure n’existe pas. ${FORMAT}` };
  }
  const instant = parisInstant(year, month, day, hour, minute);
  if (!instant) {
    return { ok: false, summary: 'Date et heure : cette heure n’existe pas', detail: 'Erreur : cette heure n’existe pas à Paris (changement d’heure). Choisissez une autre heure.' };
  }
  if (instant.getTime() > now.getTime()) {
    return { ok: false, summary: 'Date et heure : une date future ne peut pas être affichée', detail: 'Erreur : une date future ne peut pas être affichée. Choisissez un instant passé.' };
  }
  return { ok: true, value: instant };
}

/** The fields of an instant, to refill the control with the view on screen. */
export function instantParts(instant: Date): InstantParts {
  const [date, time] = new Intl.DateTimeFormat('fr-FR', {
    timeZone: PRACTICE_ZONE, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(instant).split(' ');
  const [day, month, year] = date.split('/');
  const [hour, minute] = time.split(':');
  return { day, month, year, hour, minute };
}

/** A day of the date filter: empty is allowed, a partial or impossible day is refused. */
export function parseFilterDay(label: string, parts: DayParts): ParseResult<{ year: number; month: number; day: number } | undefined> {
  if (Object.values(parts).every((value) => value.trim() === '')) return { ok: true, value: undefined };
  const day = number(parts.day, /^\d{1,2}$/);
  const month = number(parts.month, /^\d{1,2}$/);
  const year = number(parts.year, /^\d{4}$/);
  if (day === undefined || month === undefined || year === undefined || month < 1 || month > 12 || day < 1 || day > daysIn(year, month) || year < 1900) {
    return { ok: false, summary: `${label} : date incorrecte`, detail: `Erreur : saisissez une date existante. Format JJ/MM/AAAA.` };
  }
  return { ok: true, value: { year, month, day } };
}

/** Start of a Paris day, as the inclusive `from` of the timeline. */
export function dayStart(day: { year: number; month: number; day: number }): Date | undefined {
  return parisInstant(day.year, day.month, day.day, 0, 0);
}

/** Start of the day after, as the exclusive `to`: the last day chosen is included in full. */
export function nextDayStart(day: { year: number; month: number; day: number }): Date | undefined {
  const next = new Date(Date.UTC(day.year, day.month - 1, day.day + 1));
  return parisInstant(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate(), 0, 0);
}

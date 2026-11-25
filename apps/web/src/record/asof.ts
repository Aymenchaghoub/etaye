import { DEFAULT_ZONE, offsetFormat, displayFormat, zoneWords } from './zone';

export type InstantParts = { day: string; month: string; year: string; hour: string; minute: string };
export type DayParts = { day: string; month: string; year: string };
export const EMPTY_INSTANT: InstantParts = { day: '', month: '', year: '', hour: '', minute: '' };
export const EMPTY_DAY: DayParts = { day: '', month: '', year: '' };

export type ParseResult<T> = { ok: true; value: T } | { ok: false; summary: string; detail: string };

/** Offset of a zone from UTC at an instant, in milliseconds (positive east of Greenwich). */
function zoneOffset(zone: string, at: number): number {
  const parts = Object.fromEntries(offsetFormat(zone).formatToParts(new Date(at)).map((part) => [part.type, part.value]));
  const local = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day), Number(parts.hour),
    Number(parts.minute), Number(parts.second));
  return local - Math.floor(at / 1000) * 1000;
}

/**
 * Every instant at which the wall clock of a zone reads the given date and time: none for the hour that clocks skip when
 * they go forward, two for the hour that repeats when they go back.
 */
export function zoneInstants(zone: string, year: number, month: number, day: number, hour: number, minute: number): Date[] {
  const wall = Date.UTC(year, month - 1, day, hour, minute);
  // The offsets in force a day before and a day after bracket any change of clocks, so both readings are tried.
  const candidates = new Set([wall - zoneOffset(zone, wall - 86_400_000), wall - zoneOffset(zone, wall + 86_400_000)]);
  return [...candidates].filter((at) => at + zoneOffset(zone, at) === wall).sort((a, b) => a - b).map((at) => new Date(at));
}

/** The instant of a wall-clock reading in a zone, or undefined when it does not exist or is ambiguous (see zoneInstants). */
export function zoneInstant(zone: string, year: number, month: number, day: number, hour: number, minute: number): Date | undefined {
  const all = zoneInstants(zone, year, month, day, hour, minute);
  return all.length === 1 ? all[0] : undefined;
}

export const parisInstants = (year: number, month: number, day: number, hour: number, minute: number) =>
  zoneInstants(DEFAULT_ZONE, year, month, day, hour, minute);
export const parisInstant = (year: number, month: number, day: number, hour: number, minute: number) =>
  zoneInstant(DEFAULT_ZONE, year, month, day, hour, minute);

/** The wall clock of an instant in a zone. */
export function wallClock(zone: string, instant: Date): { year: number; month: number; day: number; hour: number; minute: number } {
  const parts = Object.fromEntries(displayFormat(zone).formatToParts(instant).map((part) => [part.type, part.value]));
  return { year: Number(parts.year), month: Number(parts.month), day: Number(parts.day), hour: Number(parts.hour), minute: Number(parts.minute) };
}

const daysIn = (year: number, month: number) => new Date(Date.UTC(year, month, 0)).getUTCDate();
const format = (zone: string) => `Format JJ/MM/AAAA et HH:MM, ${zoneWords(zone).clock}.`;

function number(value: string, digits: RegExp): number | undefined {
  return digits.test(value.trim()) ? Number(value.trim()) : undefined;
}

/** The past instant chosen on the date and time fields. A future instant is refused here and again by the server. */
export function parseAsOf(parts: InstantParts, now: Date = new Date(), zone: string = DEFAULT_ZONE): ParseResult<Date> {
  const empty = Object.values(parts).every((value) => value.trim() === '');
  if (empty) {
    return { ok: false, summary: 'Date et heure : obligatoires', detail: `Erreur : indiquez la date et l’heure. ${format(zone)}` };
  }
  const day = number(parts.day, /^\d{1,2}$/);
  const month = number(parts.month, /^\d{1,2}$/);
  const year = number(parts.year, /^\d{4}$/);
  const hour = number(parts.hour, /^\d{1,2}$/);
  const minute = number(parts.minute, /^\d{1,2}$/);
  if (day === undefined || month === undefined || year === undefined || hour === undefined || minute === undefined) {
    return { ok: false, summary: 'Date et heure : format incorrect', detail: `Erreur : saisissez la date et l’heure en chiffres. ${format(zone)}` };
  }
  if (month < 1 || month > 12 || day < 1 || day > daysIn(year, month) || hour > 23 || minute > 59 || year < 1900) {
    return { ok: false, summary: 'Date et heure : cet instant n’existe pas', detail: `Erreur : cette date ou cette heure n’existe pas. ${format(zone)}` };
  }
  const found = zoneInstants(zone, year, month, day, hour, minute);
  const place = zoneWords(zone).place;
  if (found.length === 0) {
    return { ok: false, summary: 'Date et heure : cette heure n’existe pas', detail: `Erreur : cette heure n’existe pas ${place} (changement d’heure). Choisissez une autre heure.` };
  }
  if (found.length > 1) {
    // Choosing one of the two readings silently could hide evidence recorded between them.
    return { ok: false, summary: 'Date et heure : cette heure existe deux fois', detail: `Erreur : cette heure existe deux fois ${place} (changement d’heure). Choisissez une heure avant ou après cet intervalle.` };
  }
  const instant = found[0];
  if (instant.getTime() > now.getTime()) {
    return { ok: false, summary: 'Date et heure : une date future ne peut pas être affichée', detail: 'Erreur : une date future ne peut pas être affichée. Choisissez un instant passé.' };
  }
  return { ok: true, value: instant };
}

/** The fields of an instant, to refill the control with the view on screen. */
export function instantParts(instant: Date, zone: string = DEFAULT_ZONE): InstantParts {
  const [date, time] = displayFormat(zone).format(instant).split(' ');
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

/**
 * The first instant of a calendar day of a zone: when midnight repeats, the earlier reading; when the clocks skip midnight
 * (Santiago on 8 September 2019, from 00:00 to 01:00) the day starts where the skipped hour ends, so a day filter never
 * loses its bound.
 */
function firstInstantOfDay(zone: string, year: number, month: number, day: number): Date {
  const [first] = zoneInstants(zone, year, month, day, 0, 0);
  if (first) return first;
  const wall = Date.UTC(year, month - 1, day, 0, 0);
  // Read with the offset of the day before and of the day after, the later instant is the one where the clocks jumped.
  return new Date(Math.max(wall - zoneOffset(zone, wall - 86_400_000), wall - zoneOffset(zone, wall + 86_400_000)));
}

/** Start of a day of the practice zone, as the inclusive `from` of the timeline. */
export function dayStart(day: { year: number; month: number; day: number }, zone: string = DEFAULT_ZONE): Date | undefined {
  return firstInstantOfDay(zone, day.year, day.month, day.day);
}

/** Start of the day after, as the exclusive `to`: the last day chosen is included in full. */
export function nextDayStart(day: { year: number; month: number; day: number }, zone: string = DEFAULT_ZONE): Date | undefined {
  const next = new Date(Date.UTC(day.year, day.month - 1, day.day + 1));
  return firstInstantOfDay(zone, next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate());
}

/**
 * The instant `months` calendar months before another, on the wall clock of a zone (the day is kept, or the last day of a
 * shorter month). The lower bound of "3 derniers mois": when the wall time does not exist (clocks went forward) the next
 * hour is used, and when it exists twice the earlier reading, so the window is never narrower than asked.
 */
export function monthsBefore(zone: string, instant: Date, months: number): Date {
  const wall = wallClock(zone, instant);
  const index = wall.year * 12 + (wall.month - 1) - months;
  const year = Math.floor(index / 12);
  const month = (index % 12 + 12) % 12 + 1;
  const day = Math.min(wall.day, daysIn(year, month));
  const exact = zoneInstants(zone, year, month, day, wall.hour, wall.minute);
  if (exact.length > 0) return exact[0];
  const shifted = new Date(Date.UTC(year, month - 1, day, wall.hour + 1, wall.minute));
  return zoneInstants(zone, shifted.getUTCFullYear(), shifted.getUTCMonth() + 1, shifted.getUTCDate(), shifted.getUTCHours(), shifted.getUTCMinutes())[0]
    ?? new Date(shifted.getTime());
}

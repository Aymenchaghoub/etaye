/** The zone a practice context without a `timezone` falls back to (an API that does not send it yet): Paris, the zone of the dataset. */
export const DEFAULT_ZONE = 'Europe/Paris';

/**
 * The IANA zone the record screens show and read every instant in (D-64, UD-71 D1): the one the session context carries.
 * A missing, empty or unknown value falls back to the default instead of breaking the screen.
 */
export function resolveZone(value: unknown): string {
  return hasUsableZone(value) ? (value as string).trim() : DEFAULT_ZONE;
}

/** Whether the context carries a zone this browser can read instants in; when not, the screens say they fell back to Paris. */
export function hasUsableZone(value: unknown): boolean {
  if (typeof value !== 'string' || value.trim() === '') return false;
  try {
    new Intl.DateTimeFormat('fr-FR', { timeZone: value.trim() });
    return true;
  } catch {
    return false;
  }
}

const displays = new Map<string, Intl.DateTimeFormat>();
const offsets = new Map<string, Intl.DateTimeFormat>();

/** The 24-hour date and time formatter of a zone (cached: a formatter is costly to build). */
export function displayFormat(zone: string): Intl.DateTimeFormat {
  let format = displays.get(zone);
  if (!format) {
    format = new Intl.DateTimeFormat('fr-FR', {
      timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    });
    displays.set(zone, format);
  }
  return format;
}

/** The formatter that reads a zone's wall clock to the second, to find its offset from UTC. */
export function offsetFormat(zone: string): Intl.DateTimeFormat {
  let format = offsets.get(zone);
  if (!format) {
    format = new Intl.DateTimeFormat('en-US', {
      timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
      second: '2-digit', hourCycle: 'h23',
    });
    offsets.set(zone, format);
  }
  return format;
}

/**
 * How the zone is named to the practitioner. Paris keeps its approved wording; any other zone is "heure du cabinet",
 * because a French name derived from the zone identifier could be wrong and must never be guessed.
 */
export function zoneWords(zone: string): { clock: string; place: string } {
  return zone === DEFAULT_ZONE
    ? { clock: 'heure de Paris', place: 'à Paris' }
    : { clock: 'heure du cabinet', place: 'dans le fuseau du cabinet' };
}

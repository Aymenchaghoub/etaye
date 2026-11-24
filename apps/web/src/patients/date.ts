export type DateParts = { day: string; month: string; year: string };
export type DateProblem = { code: 'missing' | 'invalid' | 'impossible' | 'future' | 'year'; detail: string; summary: string };
export type DateResult = { ok: true; iso: string } | { ok: false; problem: DateProblem };

const MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre',
  'novembre', 'décembre'];
const FORMAT = 'Format JJ/MM/AAAA.';
const MIN_YEAR = 1900;

export const EMPTY_DATE: DateParts = { day: '', month: '', year: '' };

export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function problem(code: DateProblem['code'], summary: string, detail: string): DateResult {
  return { ok: false, problem: { code, summary, detail } };
}

/** Friendly client-side check of the three segments; the server stays authoritative. */
export function parseBirthDate(parts: DateParts, today: Date = new Date()): DateResult {
  const day = parts.day.trim();
  const month = parts.month.trim();
  const year = parts.year.trim();
  if (!day && !month && !year) {
    return problem('missing', 'Date de naissance : obligatoire', `Erreur : indiquez la date de naissance. ${FORMAT}`);
  }
  if (!/^\d{1,2}$/.test(day) || !/^\d{1,2}$/.test(month) || !/^\d{4}$/.test(year)) {
    return problem('invalid', 'Date de naissance : format incorrect',
      `Erreur : saisissez le jour, le mois et l’année en chiffres. ${FORMAT}`);
  }
  const d = Number(day);
  const m = Number(month);
  const y = Number(year);
  if (m < 1 || m > 12 || d < 1) {
    return problem('impossible', 'Date de naissance : cette date n’existe pas', `Erreur : cette date n’existe pas. ${FORMAT}`);
  }
  if (d > daysInMonth(y, m)) {
    const named = m === 2 && d === 29 ? `le ${d} ${MONTHS[m - 1]} ${y}` : `le ${d} ${MONTHS[m - 1]}`;
    return problem('impossible', 'Date de naissance : cette date n’existe pas', `Erreur : ${named} n’existe pas. ${FORMAT}`);
  }
  if (y < MIN_YEAR) {
    return problem('year', 'Date de naissance : année à vérifier', `Erreur : vérifiez l’année de naissance. ${FORMAT}`);
  }
  const iso = `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  if (iso > todayIso) {
    return problem('future', 'Date de naissance : dans le futur', 'Erreur : la date de naissance ne peut pas être dans le futur.');
  }
  return { ok: true, iso };
}

export function isoToParts(iso: string): DateParts {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return match ? { day: match[3], month: match[2], year: match[1] } : { ...EMPTY_DATE };
}

export function formatDate(iso: string): string {
  const { day, month, year } = isoToParts(iso);
  return day ? `${day}/${month}/${year}` : iso;
}

/** Completed years on `today`, computed from the stored birth date at render time. */
export function ageOn(iso: string, today: Date = new Date()): number | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!match) return undefined;
  const [y, m, d] = [Number(match[1]), Number(match[2]), Number(match[3])];
  let age = today.getFullYear() - y;
  const beforeBirthday = today.getMonth() + 1 < m || (today.getMonth() + 1 === m && today.getDate() < d);
  if (beforeBirthday) age -= 1;
  return age >= 0 ? age : undefined;
}

export function ageLabel(iso: string, today: Date = new Date()): string {
  const age = ageOn(iso, today);
  if (age === undefined) return '';
  return age <= 1 ? `${age} an` : `${age} ans`;
}

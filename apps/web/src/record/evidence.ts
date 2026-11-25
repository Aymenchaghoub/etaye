import type {
  Assertion, AssertionCategory, EventDate, HistorySection, SectionState, SourceKind, SourceType,
} from '../api/generated/types.gen';

import { DEFAULT_ZONE, displayFormat } from './zone';

type Clock = { date: string; time: string };

/**
 * The date and the 24-hour time of an instant in the practice zone (D-64): the zone is the one of the session context,
 * passed by the screen. The default is only the fallback of a context that carries none.
 */
export function clock(instant: string | Date, zone: string = DEFAULT_ZONE): Clock {
  const parts = Object.fromEntries(displayFormat(zone).formatToParts(new Date(instant)).map((part) => [part.type, part.value]));
  return { date: `${parts.day}/${parts.month}/${parts.year}`, time: `${parts.hour}:${parts.minute}` };
}

export const formatInstant = (instant: string | Date, zone: string = DEFAULT_ZONE) => {
  const { date, time } = clock(instant, zone);
  return `${date} ${time}`;
};

/** "20/10/2026 10:12" for an instant, "30/09/2026" for a date-only event, nothing for an unknown date. */
export function eventLabel(event: EventDate, zone: string = DEFAULT_ZONE): string | undefined {
  if (event.precision === 'INSTANT' && event.instant) return formatInstant(event.instant, zone);
  if (event.precision === 'DATE' && event.date) {
    const [year, month, day] = event.date.split('-');
    return `${day}/${month}/${year}`;
  }
  return undefined;
}

/** The calendar day of an event, used where only the day matters ("Absence déclarée le …"). */
export function eventDay(event: EventDate, zone: string = DEFAULT_ZONE): string | undefined {
  const label = eventLabel(event, zone);
  return label?.slice(0, 10);
}

export const originLabel: Record<SourceType, string> = {
  PATIENT_REPORTED: 'Déclaration de la patiente',
  PRACTITIONER_OBSERVED: 'Observation du praticien',
  DOCUMENT_REPORTED: 'Rapporté par un document',
};

/** The status chip of a source: the origin in words, and "non confirmé" for a patient report nobody assessed. */
export const originState: Record<SourceType, { label: string; tone: SectionTone }> = {
  PATIENT_REPORTED: { label: 'Déclaré par la patiente · non confirmé', tone: 'declare' },
  PRACTITIONER_OBSERVED: { label: 'Observation du praticien', tone: 'observe' },
  DOCUMENT_REPORTED: { label: 'Rapporté par un document', tone: 'plain' },
};

/** The kind written above a timeline item. A kind this client does not know yet gets a neutral label. */
export function kindLabel(kind: SourceKind | string): string {
  switch (kind) {
    case 'CONSULTATION_NOTE': return 'Note de consultation';
    case 'PRACTITIONER_MEASUREMENT': return 'Mesure';
    case 'MANUAL_LAB_PANEL': return 'Biologie';
    case 'UPLOADED_DOCUMENT': return 'Document';
    case 'PATIENT_PREPARATION': return 'Préparation de visite';
    default: return 'Autre élément du dossier';
  }
}

export function sourceHeading(kind: SourceKind | string): string {
  switch (kind) {
    case 'PRACTITIONER_MEASUREMENT': return 'Mesure au cabinet';
    case 'MANUAL_LAB_PANEL': return 'Bilan saisi manuellement';
    default: return kindLabel(kind);
  }
}

/** The groups of the type filter: one choice can cover several kinds. */
export const KIND_FILTERS: { value: string; label: string; kinds: SourceKind[] }[] = [
  { value: '', label: 'Tous les types', kinds: [] },
  { value: 'notes', label: 'Notes de consultation', kinds: ['CONSULTATION_NOTE'] },
  { value: 'mesures', label: 'Mesures', kinds: ['PRACTITIONER_MEASUREMENT'] },
  { value: 'biologie', label: 'Biologie et documents', kinds: ['MANUAL_LAB_PANEL', 'UPLOADED_DOCUMENT'] },
  { value: 'patient', label: 'Déclarations de la patiente', kinds: ['PATIENT_PREPARATION'] },
];

export const SECTION_ORDER: AssertionCategory[] = ['ALLERGY', 'HISTORY', 'REPORTED_TREATMENT'];

const titles: Record<AssertionCategory, { title: string; thing: string; article: string }> = {
  ALLERGY: { title: 'Allergies', thing: 'allergie', article: 'd’allergie' },
  HISTORY: { title: 'Antécédents', thing: 'antécédent', article: 'd’antécédent' },
  REPORTED_TREATMENT: { title: 'Traitements déclarés', thing: 'traitement', article: 'de traitement' },
};
export const sectionTitle = (category: AssertionCategory) => titles[category].title;
export const sectionThing = (category: AssertionCategory) => titles[category].thing;

/** The marker of a state chip; 'plain' writes the origin in words with no marker, as for a document. */
export type SectionTone = 'manquant' | 'negatif' | 'declare' | 'discordant' | 'observe' | 'plain';

/**
 * One wording per state, in one place (UD-67 C1, BR-008): the chip of the section, its sentence and the shorter line of
 * the dossier panel. An absence is never written as "none": every state names itself and none concludes an absence.
 */
export function sectionWords(category: AssertionCategory, section: Pick<HistorySection, 'state' | 'statements'>, past = false, zone: string = DEFAULT_ZONE): {
  chip: string; tone: SectionTone; sentence: string; panel: string; headline: string; lead: string;
} {
  const { article, thing } = titles[category];
  const none = `Aucune source au dossier${past ? ' à cette date' : ''} · ne signifie pas absence ${article}`;
  switch (section.state) {
    case 'NOT_RECORDED':
      return { chip: 'Non renseigné', tone: 'manquant', sentence: `${none}.`, panel: none, headline: 'Non renseigné', lead: 'non renseigné' };
    case 'UNKNOWN':
      return {
        chip: 'Inconnu · à préciser', tone: 'manquant',
        sentence: category === 'REPORTED_TREATMENT'
          ? 'Marqué inconnu au dossier. Ne pas écrire « aucun traitement ».'
          : 'Marqué inconnu au dossier · à préciser.',
        panel: `Marqué inconnu au dossier · ne pas conclure à l’absence ${article}`,
        headline: 'Inconnu · à préciser', lead: 'inconnu · à préciser',
      };
    case 'NOT_ASKED':
      return {
        chip: 'Non demandé', tone: 'manquant', sentence: 'Cette rubrique n’a pas été demandée.',
        panel: `Non demandé au dossier · ne pas conclure à l’absence ${article}`, headline: 'Non demandé', lead: 'non demandé',
      };
    case 'EXPLICITLY_ABSENT': {
      const day = absentDay(section.statements, zone);
      const label = absentLabel(day);
      return {
        chip: label, tone: 'negatif',
        sentence: 'Déclaration datée, avec son auteur et son origine · une information plus récente peut la remplacer.',
        panel: label, headline: label, lead: 'absence déclarée',
      };
    }
    case 'PRESENT':
      return {
        chip: 'Déclaré', tone: 'declare', sentence: 'Chaque déclaration est datée, avec l’origine en toutes lettres.',
        panel: presentSummary(section.statements, zone), headline: presentSummary(section.statements, zone), lead: 'signalé',
      };
    case 'CONFLICTING':
      return {
        chip: 'Déclarations discordantes', tone: 'discordant',
        sentence: `Rubrique à préciser : deux déclarations datées se contredisent · ne pas conclure à l’absence ${article}.`,
        panel: conflictSummary(section.statements, zone),
        headline: 'Déclarations discordantes à préciser · ne pas conclure à l’absence', lead: 'déclarations discordantes',
      };
    default:
      return { chip: 'État inconnu', tone: 'manquant', sentence: `Ne pas conclure à l’absence ${article} (${thing}).`, panel: '', headline: 'Non renseigné', lead: 'état inconnu' };
  }
}

/** An absence is dated by its occurrence; with no known date it says so and never borrows the recording date. */
function absentLabel(day: string | undefined): string {
  return day ? `Absence déclarée le ${day}` : 'Absence déclarée · date inconnue';
}

function absentDay(statements: Assertion[], zone: string): string | undefined {
  const absent = statements.filter((statement) => statement.assertionKind === 'EXPLICITLY_ABSENT');
  const latest = absent.at(-1);
  return latest ? eventDay(latest.eventDate, zone) : undefined;
}

/** The two sides of a conflict in one line, oldest first, each with its date; nothing is merged or resolved. */
function conflictSummary(statements: Assertion[], zone: string): string {
  return statements.filter((statement) => statement.assertionKind === 'PRESENT' || statement.assertionKind === 'EXPLICITLY_ABSENT')
    .map((statement) => {
      const day = eventDay(statement.eventDate, zone);
      if (statement.assertionKind === 'EXPLICITLY_ABSENT') return absentLabel(day);
      const unconfirmed = statement.sourceType === 'PATIENT_REPORTED' ? ' · non confirmée' : '';
      return `Déclaration présente${day ? ` signalée le ${day}` : ''}${unconfirmed}`;
    }).join(', ');
}

/** What a present section says in one line: the statements, each with its origin and its date. */
function presentSummary(statements: Assertion[], zone: string): string {
  const present = statements.filter((statement) => statement.assertionKind === 'PRESENT');
  if (present.length === 0) return 'Déclaré';
  return present.map((statement) => {
    const origin = statement.sourceType === 'PATIENT_REPORTED' ? 'déclaré par la patiente, non confirmé'
      : statement.sourceType === 'DOCUMENT_REPORTED' ? 'rapporté par un document'
        : statement.sourceType === 'PRACTITIONER_OBSERVED' ? 'observation du praticien' : 'origine non précisée';
    const day = eventDay(statement.eventDate, zone);
    return `${statement.text ?? 'Information présente'} · ${origin}${day ? ` · ${day}` : ''}`;
  }).join(' ; ');
}

/** The state chip of one statement. */
export function statementState(statement: Assertion, zone: string = DEFAULT_ZONE): { label: string; tone: SectionTone } {
  switch (statement.assertionKind) {
    case 'EXPLICITLY_ABSENT': {
      const day = eventDay(statement.eventDate, zone);
      return { label: absentLabel(day), tone: 'negatif' };
    }
    case 'UNKNOWN': return { label: 'Inconnu · à préciser', tone: 'manquant' };
    case 'NOT_ASKED': return { label: 'Non demandé', tone: 'manquant' };
    default:
      return statement.sourceType === 'PATIENT_REPORTED' ? { label: 'Déclaré par la patiente · non confirmé', tone: 'declare' }
        : statement.sourceType === 'DOCUMENT_REPORTED' ? { label: 'Rapporté par un document', tone: 'plain' }
          : { label: 'Observation du praticien', tone: 'observe' };
  }
}

/**
 * The conflict sentence of the I-27 panel. It says an older negative does not contradict a later report only when the
 * occurrence days show it (a strictly earlier day for the negative); otherwise it stays neutral, because the order in which
 * two statements were recorded does not tell which one describes the earlier fact.
 */
export function conflictNote(statements: Assertion[], zone: string = DEFAULT_ZONE): { lead: string; rest: string } {
  const absent = statements.find((statement) => statement.assertionKind === 'EXPLICITLY_ABSENT');
  const present = statements.find((statement) => statement.assertionKind === 'PRESENT');
  const absentDay = absent && eventDay(absent.eventDate, zone);
  const presentDay = present && eventDay(present.eventDate, zone);
  const earlier = absentDay && presentDay && absentDay.split('/').reverse().join('') < presentDay.split('/').reverse().join('');
  const lead = earlier
    ? `La déclaration négative du ${absentDay} ne contredit pas une réaction rapportée ensuite : elle date d’avant.`
    : 'Une déclaration négative et une déclaration présente se contredisent : aucune n’est retenue à la place de l’autre.';
  return { lead, rest: 'Rien n’est fusionné ni résolu automatiquement. La rubrique reste « à préciser ».' };
}

export const STATE_WITHOUT_ABSENCE: SectionState[] = ['NOT_RECORDED', 'UNKNOWN', 'NOT_ASKED', 'CONFLICTING'];

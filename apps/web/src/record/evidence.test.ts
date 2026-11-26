import { describe, expect, it } from 'vitest';
import type { Assertion, HistorySection } from '../api/generated/types.gen';
import {
  clock, conflictNote, eventDay, eventLabel, formatInstant, KIND_FILTERS, kindLabel, originLabel, originState,
  SECTION_ORDER, sectionThing, sectionTitle, sectionWords, sourceHeading, STATE_WITHOUT_ABSENCE, statementState,
} from './evidence';

const statement = (patch: Partial<Assertion>): Assertion => ({
  id: 'a', version: 1, createdAt: '2026-11-02T08:00:00Z', updatedAt: '2026-11-02T08:00:00Z', practiceId: 'p', category: 'ALLERGY',
  assertionKind: 'PRESENT', eventDate: { precision: 'UNKNOWN' }, recordedAt: '2026-11-02T08:00:00Z', ...patch,
});
const section = (state: HistorySection['state'], statements: Assertion[] = []): Pick<HistorySection, 'state' | 'statements'> => ({ state, statements });

describe('dates in the practice zone', () => {
  it('shows summer and winter instants in Paris time with a 24-hour clock', () => {
    expect(formatInstant('2026-10-20T08:12:00Z')).toBe('20/10/2026 10:12');
    expect(formatInstant('2026-11-02T08:10:00Z')).toBe('02/11/2026 09:10');
    expect(clock('2026-11-01T23:30:00Z')).toEqual({ date: '02/11/2026', time: '00:30' });
  });

  it('labels each precision and never invents a date for an unknown one', () => {
    expect(eventLabel({ precision: 'INSTANT', instant: '2026-10-20T08:12:00Z' })).toBe('20/10/2026 10:12');
    expect(eventLabel({ precision: 'DATE', date: '2026-09-30', timezone: 'Europe/Paris' })).toBe('30/09/2026');
    expect(eventLabel({ precision: 'UNKNOWN' })).toBeUndefined();
  });
});

describe('the wording of a section (BR-008, UD-67 C1)', () => {
  it('writes one wording per state and never an absence', () => {
    expect(sectionWords('ALLERGY', section('NOT_RECORDED')).chip).toBe('Non renseigné');
    expect(sectionWords('ALLERGY', section('NOT_RECORDED')).sentence).toBe('Aucune source au dossier · ne signifie pas absence d’allergie.');
    expect(sectionWords('HISTORY', section('UNKNOWN')).chip).toBe('Inconnu · à préciser');
    expect(sectionWords('REPORTED_TREATMENT', section('UNKNOWN')).sentence).toBe('Marqué inconnu au dossier. Ne pas écrire « aucun traitement ».');
    expect(sectionWords('ALLERGY', section('NOT_ASKED')).chip).toBe('Non demandé');
    expect(sectionWords('ALLERGY', section('CONFLICTING')).headline).toBe('Déclarations discordantes à préciser · ne pas conclure à l’absence');
    expect(sectionWords('ALLERGY', section('CONFLICTING')).tone).toBe('discordant');
    for (const state of ['NOT_RECORDED', 'UNKNOWN', 'NOT_ASKED', 'CONFLICTING'] as const) {
      const words = sectionWords('ALLERGY', section(state));
      expect(words.headline).not.toMatch(/^aucune? /i);
      expect(`${words.chip} ${words.headline}`).not.toMatch(/aucune allergie|pas d’allergie|normal/i);
    }
  });

  it('dates an explicit absence and never writes "Aucune allergie"', () => {
    const absent = statement({ assertionKind: 'EXPLICITLY_ABSENT', eventDate: { precision: 'INSTANT', instant: '2026-09-14T07:30:00Z' } });
    const words = sectionWords('ALLERGY', section('EXPLICITLY_ABSENT', [absent]));
    expect(words.chip).toBe('Absence déclarée le 14/09/2026');
    expect(words.headline).not.toMatch(/Aucune allergie/);
    expect(statementState(absent)).toEqual({ label: 'Absence déclarée le 14/09/2026', tone: 'negatif' });
  });

  it('names the origin of a present statement in words', () => {
    const patient = statement({ text: 'Pénicilline', sourceType: 'PATIENT_REPORTED', eventDate: { precision: 'INSTANT', instant: '2026-11-02T08:10:00Z' } });
    expect(sectionWords('ALLERGY', section('PRESENT', [patient])).headline).toBe('Pénicilline · déclaré par la patiente, non confirmé · 02/11/2026');
    expect(statementState(patient).label).toBe('Déclaré par la patiente · non confirmé');
    expect(statementState(statement({ sourceType: 'DOCUMENT_REPORTED' })).tone).toBe('plain');
    expect(statementState(statement({ sourceType: 'PRACTITIONER_OBSERVED' })).label).toBe('Observation du praticien');
  });

  it('keeps the as-of sentence honest about the date', () => {
    expect(sectionWords('HISTORY', section('NOT_RECORDED'), true).sentence).toBe('Aucune source au dossier à cette date · ne signifie pas absence d’antécédent.');
  });

  it('explains that an older negative does not contradict a later report', () => {
    const absent = statement({ assertionKind: 'EXPLICITLY_ABSENT', recordedAt: '2026-09-14T07:45:00Z', eventDate: { precision: 'INSTANT', instant: '2026-09-14T07:30:00Z' } });
    const present = statement({ recordedAt: '2026-11-02T08:10:00Z', eventDate: { precision: 'INSTANT', instant: '2026-11-02T08:10:00Z' } });
    expect(conflictNote([absent, present]).lead).toBe('La déclaration négative du 14/09/2026 ne contredit pas une réaction rapportée ensuite : elle date d’avant.');
    expect(conflictNote([present, absent].reverse()).rest).toContain('Rien n’est fusionné ni résolu automatiquement.');
    // Two statements of the same occurrence day: the recording order proves nothing, so the note stays neutral.
    const sameDay = statement({ eventDate: { precision: 'INSTANT', instant: '2026-09-14T07:30:00Z' }, recordedAt: '2026-11-02T08:10:00Z' });
    expect(conflictNote([absent, sameDay]).lead).toContain('aucune n’est retenue à la place de l’autre');
    expect(sectionWords('ALLERGY', section('EXPLICITLY_ABSENT', [statement({ assertionKind: 'EXPLICITLY_ABSENT' })])).chip).toBe('Absence déclarée · date inconnue');
    expect(conflictNote([statement({ recordedAt: '2026-09-01T08:00:00Z' }), statement({ assertionKind: 'EXPLICITLY_ABSENT', recordedAt: '2026-10-01T08:00:00Z' })]).lead)
      .toContain('aucune n’est retenue à la place de l’autre');
  });
});

describe('kinds', () => {
  it('labels all known kinds, headings, filters, section titles and states without gaps', () => {
    expect(kindLabel('CONSULTATION_NOTE')).toBe('Note de consultation');
    expect(kindLabel('PRACTITIONER_MEASUREMENT')).toBe('Mesure');
    expect(kindLabel('MANUAL_LAB_PANEL')).toBe('Biologie');
    expect(kindLabel('UPLOADED_DOCUMENT')).toBe('Document');
    expect(kindLabel('PATIENT_PREPARATION')).toBe('Préparation de visite');
    expect(kindLabel('SOMETHING_NEW')).toBe('Autre élément du dossier');

    expect(sourceHeading('PRACTITIONER_MEASUREMENT')).toBe('Mesure au cabinet');
    expect(sourceHeading('MANUAL_LAB_PANEL')).toBe('Bilan saisi manuellement');
    expect(sourceHeading('CONSULTATION_NOTE')).toBe('Note de consultation');

    expect(KIND_FILTERS.map((f) => [f.value, f.label, f.kinds])).toEqual([
      ['', 'Tous les types', []],
      ['notes', 'Notes de consultation', ['CONSULTATION_NOTE']],
      ['mesures', 'Mesures', ['PRACTITIONER_MEASUREMENT']],
      ['biologie', 'Biologie et documents', ['MANUAL_LAB_PANEL', 'UPLOADED_DOCUMENT']],
      ['patient', 'Déclarations de la patiente', ['PATIENT_PREPARATION']],
    ]);
    expect(SECTION_ORDER).toEqual(['ALLERGY', 'HISTORY', 'REPORTED_TREATMENT']);
    expect(STATE_WITHOUT_ABSENCE).toEqual(['NOT_RECORDED', 'UNKNOWN', 'NOT_ASKED', 'CONFLICTING']);
    expect(sectionTitle('ALLERGY')).toBe('Allergies');
    expect(sectionTitle('HISTORY')).toBe('Antécédents');
    expect(sectionTitle('REPORTED_TREATMENT')).toBe('Traitements déclarés');
    expect(sectionThing('ALLERGY')).toBe('allergie');
    expect(sectionThing('HISTORY')).toBe('antécédent');
    expect(sectionThing('REPORTED_TREATMENT')).toBe('traitement');
    expect(originLabel).toEqual({
      PATIENT_REPORTED: 'Déclaration de la patiente',
      PRACTITIONER_OBSERVED: 'Observation du praticien',
      DOCUMENT_REPORTED: 'Rapporté par un document',
    });
    expect(originState.PATIENT_REPORTED).toEqual({ label: 'Déclaré par la patiente · non confirmé', tone: 'declare' });
    expect(originState.PRACTITIONER_OBSERVED).toEqual({ label: 'Observation du praticien', tone: 'observe' });
    expect(originState.DOCUMENT_REPORTED).toEqual({ label: 'Rapporté par un document', tone: 'plain' });
  });

  it('covers every assertionKind, section state, summary origin, conflict boundary and unknown date edge', () => {
    expect(eventDay({ precision: 'DATE', date: '2026-09-30' })).toBe('30/09/2026');
    expect(eventDay({ precision: 'UNKNOWN' })).toBeUndefined();
    expect(eventLabel({ precision: 'INSTANT', instant: '2026-10-20T08:12:00Z' }, 'America/Martinique')).toBe('20/10/2026 04:12');
    expect(eventLabel({ precision: 'INSTANT' })).toBeUndefined();
    expect(eventLabel({ precision: 'DATE' })).toBeUndefined();

    expect(sectionWords('ALLERGY', section('UNKNOWN'))).toEqual({
      chip: 'Inconnu · à préciser',
      tone: 'manquant',
      sentence: 'Marqué inconnu au dossier · à préciser.',
      panel: 'Marqué inconnu au dossier · ne pas conclure à l’absence d’allergie',
      headline: 'Inconnu · à préciser',
      lead: 'inconnu · à préciser',
    });
    expect(sectionWords('REPORTED_TREATMENT', section('NOT_ASKED'))).toEqual({
      chip: 'Non demandé',
      tone: 'manquant',
      sentence: 'Cette rubrique n’a pas été demandée.',
      panel: 'Non demandé au dossier · ne pas conclure à l’absence de traitement',
      headline: 'Non demandé',
      lead: 'non demandé',
    });
    expect(sectionWords('ALLERGY', section('NOT_RECORDED'))).toEqual({
      chip: 'Non renseigné',
      tone: 'manquant',
      sentence: 'Aucune source au dossier · ne signifie pas absence d’allergie.',
      panel: 'Aucune source au dossier · ne signifie pas absence d’allergie',
      headline: 'Non renseigné',
      lead: 'non renseigné',
    });
    expect(sectionWords('ALLERGY', section('EXPLICITLY_ABSENT', []))).toEqual({
      chip: 'Absence déclarée · date inconnue',
      tone: 'negatif',
      sentence: 'Déclaration datée, avec son auteur et son origine · une information plus récente peut la remplacer.',
      panel: 'Absence déclarée · date inconnue',
      headline: 'Absence déclarée · date inconnue',
      lead: 'absence déclarée',
    });
    expect(sectionWords('ALLERGY', section('PRESENT', []))).toEqual({
      chip: 'Déclaré',
      tone: 'declare',
      sentence: 'Chaque déclaration est datée, avec l’origine en toutes lettres.',
      panel: 'Déclaré',
      headline: 'Déclaré',
      lead: 'signalé',
    });

    const docsAndUnknown = sectionWords('ALLERGY', section('PRESENT', [
      statement({ text: 'Latex', sourceType: 'DOCUMENT_REPORTED', eventDate: { precision: 'DATE', date: '2026-08-10' } }),
      statement({ text: 'Iode', sourceType: 'PRACTITIONER_OBSERVED', eventDate: { precision: 'UNKNOWN' } }),
      statement({ text: undefined, sourceType: undefined }),
    ]));
    expect(docsAndUnknown.panel).toBe(
      'Latex · rapporté par un document · 10/08/2026 ; Iode · observation du praticien ; Information présente · origine non précisée',
    );
    expect(sectionWords('ALLERGY', section('CONFLICTING', [
      statement({ assertionKind: 'EXPLICITLY_ABSENT', eventDate: { precision: 'DATE', date: '2026-01-15' } }),
      statement({ assertionKind: 'PRESENT', sourceType: 'PATIENT_REPORTED', eventDate: { precision: 'DATE', date: '2026-03-20' } }),
      statement({ assertionKind: 'PRESENT', sourceType: 'PRACTITIONER_OBSERVED', eventDate: { precision: 'UNKNOWN' } }),
      statement({ assertionKind: 'UNKNOWN' }),
    ]))).toEqual({
      chip: 'Déclarations discordantes',
      tone: 'discordant',
      sentence: 'Rubrique à préciser : deux déclarations datées se contredisent · ne pas conclure à l’absence d’allergie.',
      panel: 'Absence déclarée le 15/01/2026, Déclaration présente signalée le 20/03/2026 · non confirmée, Déclaration présente',
      headline: 'Déclarations discordantes à préciser · ne pas conclure à l’absence',
      lead: 'déclarations discordantes',
    });
    expect(sectionWords('ALLERGY', section('OTHER' as HistorySection['state']))).toEqual({
      chip: 'État inconnu',
      tone: 'manquant',
      sentence: 'Ne pas conclure à l’absence d’allergie (allergie).',
      panel: '',
      headline: 'Non renseigné',
      lead: 'état inconnu',
    });

    expect(statementState(statement({ assertionKind: 'UNKNOWN' }))).toEqual({ label: 'Inconnu · à préciser', tone: 'manquant' });
    expect(statementState(statement({ assertionKind: 'NOT_ASKED' }))).toEqual({ label: 'Non demandé', tone: 'manquant' });
    expect(statementState(statement({ assertionKind: 'EXPLICITLY_ABSENT' }))).toEqual({ label: 'Absence déclarée · date inconnue', tone: 'negatif' });
    expect(statementState(statement({ sourceType: 'PATIENT_REPORTED' }))).toEqual({ label: 'Déclaré par la patiente · non confirmé', tone: 'declare' });
    expect(statementState(statement({ sourceType: 'DOCUMENT_REPORTED' }))).toEqual({ label: 'Rapporté par un document', tone: 'plain' });
    expect(statementState(statement({ sourceType: 'PRACTITIONER_OBSERVED' }))).toEqual({ label: 'Observation du praticien', tone: 'observe' });

    const laterNegative = statement({ assertionKind: 'EXPLICITLY_ABSENT', eventDate: { precision: 'DATE', date: '2026-10-01' } });
    const earlierPositive = statement({ assertionKind: 'PRESENT', eventDate: { precision: 'DATE', date: '2026-09-01' } });
    expect(conflictNote([laterNegative, earlierPositive])).toEqual({
      lead: 'Une déclaration négative et une déclaration présente se contredisent : aucune n’est retenue à la place de l’autre.',
      rest: 'Rien n’est fusionné ni résolu automatiquement. La rubrique reste « à préciser ».',
    });
    expect(conflictNote([statement({ assertionKind: 'EXPLICITLY_ABSENT' })]).lead).toContain('se contredisent');
  });
});

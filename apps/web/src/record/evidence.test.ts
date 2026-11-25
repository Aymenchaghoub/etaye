import { describe, expect, it } from 'vitest';
import type { Assertion, HistorySection } from '../api/generated/types.gen';
import { clock, conflictNote, eventLabel, formatInstant, kindLabel, sectionWords, statementState } from './evidence';

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
  it('labels the known kinds and gives an unknown kind a neutral label', () => {
    expect(kindLabel('PRACTITIONER_MEASUREMENT')).toBe('Mesure');
    expect(kindLabel('PATIENT_PREPARATION')).toBe('Préparation de visite');
    expect(kindLabel('SOMETHING_NEW')).toBe('Autre élément du dossier');
  });
});

import { describe, expect, it } from 'vitest';
import { editPatientPath, historyPath, measurementsPath, newPatientPath, patientRoute, patientsPath, recordPath } from './routes';

const grant = '11111111-1111-4111-8111-111111111111';
const record = '55555555-5555-4555-8555-555555555555';
const base = `/compte/pratiques/${grant}`;

describe('patient routes', () => {
  it('recognizes the list, the creation and the correction of a staff grant', () => {
    expect(patientRoute(`${base}/patients`)).toEqual({ base, view: 'list' });
    expect(patientRoute(`${base}/patients/nouveau`)).toEqual({ base, view: 'new' });
    expect(patientRoute(`${base}/patients/${record}/modifier`)).toEqual({ base, view: 'edit', recordId: record });
  });

  it('recognizes the chronology and the history of a record (story 3.2)', () => {
    expect(patientRoute(`${base}/patients/${record}`)).toEqual({ base, view: 'record', recordId: record });
    expect(patientRoute(`${base}/patients/${record}/antecedents`)).toEqual({ base, view: 'history', recordId: record });
    expect(patientRoute(recordPath(base, record))?.view).toBe('record');
    expect(patientRoute(historyPath(base, record))?.view).toBe('history');
    expect(recordPath(base, 'not-a-uuid')).toBe(patientsPath(base));
    expect(historyPath(base, '../x')).toBe(patientsPath(base));
  });

  it('recognizes the measurements of a record (story 3.3)', () => {
    expect(patientRoute(`${base}/patients/${record}/mesures`)).toEqual({ base, view: 'measurements', recordId: record });
    expect(patientRoute(measurementsPath(base, record))?.view).toBe('measurements');
    expect(measurementsPath(base, 'not-a-uuid')).toBe(patientsPath(base));
    expect(measurementsPath(base, '../x')).toBe(patientsPath(base));
    expect(patientRoute(`${base}/patients/${record}/mesures/`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/${record}/mesures?asOf=2026-10-20`)).toBeUndefined();
    expect(patientRoute(`/compte/patient/${grant}/patients/${record}/mesures`)).toBeUndefined();
  });

  it('builds the same paths it recognizes', () => {
    expect(patientRoute(patientsPath(base))?.view).toBe('list');
    expect(patientRoute(newPatientPath(base))?.view).toBe('new');
    expect(patientRoute(editPatientPath(base, record))).toMatchObject({ recordId: record });
  });

  it('ignores a patient grant, other segments, trailing paths and query text', () => {
    expect(patientRoute(`/compte/patient/${grant}/patients`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/other`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/${record}/`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/${record}/antecedents/`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/${record}/antecedents?asOf=2026-10-20`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/${record}/modifier/`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/nouveau?q=Martin`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/not-a-uuid/modifier`)).toBeUndefined();
    expect(patientRoute('/compte')).toBeUndefined();
  });
});

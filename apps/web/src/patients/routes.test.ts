import { describe, expect, it } from 'vitest';
import { editPatientPath, newPatientPath, patientRoute, patientsPath } from './routes';

const grant = '11111111-1111-4111-8111-111111111111';
const record = '55555555-5555-4555-8555-555555555555';
const base = `/compte/pratiques/${grant}`;

describe('patient routes', () => {
  it('recognizes the list, the creation and the correction of a staff grant', () => {
    expect(patientRoute(`${base}/patients`)).toEqual({ base, view: 'list' });
    expect(patientRoute(`${base}/patients/nouveau`)).toEqual({ base, view: 'new' });
    expect(patientRoute(`${base}/patients/${record}/modifier`)).toEqual({ base, view: 'edit', recordId: record });
  });

  it('builds the same paths it recognizes', () => {
    expect(patientRoute(patientsPath(base))?.view).toBe('list');
    expect(patientRoute(newPatientPath(base))?.view).toBe('new');
    expect(patientRoute(editPatientPath(base, record))).toMatchObject({ recordId: record });
  });

  it('ignores a patient grant, other segments, trailing paths and query text', () => {
    expect(patientRoute(`/compte/patient/${grant}/patients`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/other`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/${record}`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/${record}/modifier/`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/nouveau?q=Martin`)).toBeUndefined();
    expect(patientRoute(`${base}/patients/not-a-uuid/modifier`)).toBeUndefined();
    expect(patientRoute('/compte')).toBeUndefined();
  });
});

const uuid = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const patientPath = new RegExp(
  `^(/compte/pratiques/${uuid})/patients(?:/(nouveau)|/(${uuid})/modifier|/(${uuid})(/antecedents|/mesures)?)?$`);

export type PatientRoute =
  | { base: string; view: 'list' }
  | { base: string; view: 'new' }
  | { base: string; view: 'edit'; recordId: string }
  /** The chronology of one record (S-09), its history and allergies (S-10) and its measurements (S-11). */
  | { base: string; view: 'record'; recordId: string }
  | { base: string; view: 'history'; recordId: string }
  | { base: string; view: 'measurements'; recordId: string };

export function patientRoute(path: string): PatientRoute | undefined {
  const match = patientPath.exec(path);
  if (!match) return undefined;
  if (match[2]) return { base: match[1], view: 'new' };
  if (match[3]) return { base: match[1], view: 'edit', recordId: match[3] };
  if (match[4]) return { base: match[1], view: match[5] === '/antecedents' ? 'history' : match[5] === '/mesures' ? 'measurements' : 'record', recordId: match[4] };
  return { base: match[1], view: 'list' };
}

export const patientsPath = (base: string) => `${base}/patients`;
export const newPatientPath = (base: string) => `${base}/patients/nouveau`;
const identifier = new RegExp(`^${uuid}$`);
/** A record identifier that is not a UUID never reaches a link: the list path is used instead. */
export const editPatientPath = (base: string, recordId: string) =>
  identifier.test(recordId) ? `${base}/patients/${recordId}/modifier` : patientsPath(base);
export const recordPath = (base: string, recordId: string) =>
  identifier.test(recordId) ? `${base}/patients/${recordId}` : patientsPath(base);
export const historyPath = (base: string, recordId: string) =>
  identifier.test(recordId) ? `${base}/patients/${recordId}/antecedents` : patientsPath(base);
export const measurementsPath = (base: string, recordId: string) =>
  identifier.test(recordId) ? `${base}/patients/${recordId}/mesures` : patientsPath(base);

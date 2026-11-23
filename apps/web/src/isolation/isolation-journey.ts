import type { AccessContext, Record as PatientRecord } from '../api/generated/types.gen';

/**
 * Fixed fictional identifiers of the synthetic fixture `etaye-design-v1` (`md5('etaye-design-v1/' || alias)::uuid`).
 * They only address the demonstration reads. They are never rendered, stored or placed in a URL, and the journey
 * refuses to run when the fixture version differs. The server alone decides every outcome.
 */
export const ISOLATION_DATASET = 'etaye-design-v1';
export const PRACTICE_A = 'be25ecc3-a0e7-d887-ab64-49147f706718';
export const RECORD_A = '13add2de-e7d5-342c-5ac5-7f718d04f6a4';
export const RECORD_B = 'ee8875a3-50a9-d526-3c99-d250cc43bdad';

export const READ_PATH = '/compte/isolation/lecture';
export const OUTSIDE_PATH = '/compte/isolation/hors-cabinet';

export type Step = 'lecture' | 'hors-cabinet';
export type Audience = 'staff' | 'patient';

const ISOLATION_PREFIX = '/compte/isolation/';

/** Every path under the journey namespace: an unknown one is an old link and shows the generic refusal. */
export function isIsolationArea(path: string): boolean {
  return path.startsWith(ISOLATION_PREFIX);
}

export function isIsolationPath(path: string | null | undefined): path is string {
  return path === READ_PATH || path === OUTSIDE_PATH;
}

export function stepOf(path: string): Step | undefined {
  if (path === READ_PATH) return 'lecture';
  if (path === OUTSIDE_PATH) return 'hors-cabinet';
  return undefined;
}

/** The sign-in that starts the journey, or continues it with another fictional account. */
export function journeySignInPath(patient: boolean) {
  return `${patient ? '/connexion/patient' : '/connexion'}?retour=${encodeURIComponent(READ_PATH)}`;
}

/**
 * The current grant the journey reads through: the account's own context in the fictional practice. A staff
 * membership is preferred; a portal-only account keeps the patient audience.
 */
export function journeyContext(contexts: AccessContext[]): { context: AccessContext; audience: Audience } | undefined {
  const inPractice = contexts.filter((context) => context.practiceId === PRACTICE_A);
  const staff = inPractice.find((context) => context.kind === 'MEMBERSHIP');
  if (staff) return { context: staff, audience: 'staff' };
  const portal = inPractice.find((context) => context.kind === 'PORTAL');
  return portal ? { context: portal, audience: 'patient' } : undefined;
}

export type ReadAnswer =
  | { ok: true; data: PatientRecord | undefined }
  | { ok: false; status: number };

export type Outcome =
  | { kind: 'allowed'; name: string; birthDate: string }
  | { kind: 'restricted' }
  | { kind: 'hidden' }
  | { kind: 'patient' }
  | { kind: 'expired' }
  | { kind: 'failed' };

const isoDate = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Turns the actual response of the record read into what the journey may show. A success is shown only for the
 * authorized step and only when it is the expected fictional record; any other success is an unexpected answer and
 * is reported as a failed verification without rendering its content.
 */
export function classify(step: Step, audience: Audience, answer: ReadAnswer): Outcome {
  if (answer.ok) {
    const record = answer.data;
    const patient = record?.patient;
    if (step === 'lecture' && audience === 'staff' && record?.id === RECORD_A && record.practiceId === PRACTICE_A
      && typeof patient?.firstName === 'string' && typeof patient.lastName === 'string'
      && typeof patient.dateOfBirth === 'string' && isoDate.test(patient.dateOfBirth)) {
      return { kind: 'allowed', name: `${patient.firstName} ${patient.lastName}`, birthDate: patient.dateOfBirth };
    }
    return { kind: 'failed' };
  }
  if (answer.status === 401) return { kind: 'expired' };
  if (answer.status === 403 && audience === 'staff') return { kind: 'restricted' };
  if (answer.status === 404) return audience === 'patient' ? { kind: 'patient' } : { kind: 'hidden' };
  return { kind: 'failed' };
}

/** The stable label that ties a replayed refusal to its measured case in the CI matrix report. */
export function matrixCase(step: Step, audience: Audience, outcome: Outcome['kind']): string | undefined {
  if (step === 'lecture' && audience === 'staff' && outcome === 'allowed') return 'REC-A-ALLOWED';
  if (step === 'lecture' && audience === 'staff' && outcome === 'restricted') return 'ADM-NO-CLINICAL';
  if (step === 'lecture' && audience === 'patient' && outcome === 'patient') return 'PAT-OWN-RECORD-STAFF-ROUTE';
  if (step === 'hors-cabinet' && audience === 'staff' && outcome === 'hidden') return 'REC-HIDDEN-FOREIGN-ID';
  return undefined;
}

const formatter = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });

export function frenchDate(iso: string) {
  return formatter.format(new Date(`${iso}T00:00:00Z`));
}

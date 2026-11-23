import type { Patient } from '../api/generated/types.gen';

/**
 * The query text and the unsaved identity drafts live only in this module's memory, scoped to one account and one
 * practice grant. They are never written to the URL, to browser storage or to a log (BR-015, security design).
 * Result rows are never kept: a list is shown only after a successful response of the current mount, so a revoked
 * capability cannot leave patients visible.
 */
export type SearchMemory = { q: string };
export type DraftValues = {
  lastName: string; firstName: string; day: string; month: string; year: string; phone: string; email: string;
};
/** The request attempt of one body: the key it was sent with, so an unresolved send is replayed, not duplicated. */
export type AttemptState = { body: string; key: string };
export type DraftRecord = {
  values: DraftValues;
  /** Version of the patient the correction was based on, so a newer saved version is compared, never overwritten. */
  baseVersion?: number;
  /** Record IDs of the candidates a person judged distinct; part of the registration body and of its key. */
  confirmed?: string[];
  attempt?: AttemptState;
};
/** The patient a save just returned, shown once on the list (pinned if the page does not contain it). */
export type Confirmation = { kind: 'created' | 'updated'; patient: Patient };

let activeScope: string | undefined;
const searches = new Map<string, SearchMemory>();
const drafts = new Map<string, DraftRecord>();
const confirmations = new Map<string, Confirmation>();

export const scopeKey = (accountId: string, grantId: string) => `${accountId}:${grantId}`;

export function clearPatientMemory() {
  searches.clear();
  drafts.clear();
  confirmations.clear();
  activeScope = undefined;
}

/** Entering another account or practice grant drops everything held for the previous scope. */
export function activateScope(scope: string) {
  if (activeScope !== undefined && activeScope !== scope) clearPatientMemory();
  activeScope = scope;
}

/**
 * Called by every destination that is not the patient directory of `scope`: memory held for another practice, or
 * for no practice at all (the chooser), is dropped so a previous search cannot reappear after a practice switch.
 */
export function retainScope(scope: string | undefined) {
  if (activeScope !== undefined && activeScope !== scope) clearPatientMemory();
}

export const readSearch = (scope: string) => searches.get(scope);
export const writeSearch = (scope: string, value: SearchMemory) => { searches.set(scope, value); };

const draftKey = (scope: string, form: string) => `${scope}|${form}`;
export const readDraft = (scope: string, form: string) => drafts.get(draftKey(scope, form));
export const writeDraft = (scope: string, form: string, value: DraftRecord) => { drafts.set(draftKey(scope, form), value); };
export const clearDraft = (scope: string, form: string) => { drafts.delete(draftKey(scope, form)); };

export const rememberConfirmation = (scope: string, value: Confirmation) => { confirmations.set(scope, value); };
export function takeConfirmation(scope: string): Confirmation | undefined {
  const value = confirmations.get(scope);
  confirmations.delete(scope);
  return value;
}

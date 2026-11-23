import type { Patient } from '../api/generated/types.gen';

/**
 * Search results and unsaved identity drafts live only in this module's memory, scoped to one account and one
 * practice grant. They are never written to the URL, to browser storage or to a log (BR-015, security design).
 */
export type SearchMemory = {
  q: string;
  items: Patient[];
  nextCursor: string | null;
  loadedAt: number;
  /** Set when a correction detected a newer version after the list was loaded. */
  staleAt?: number;
};
export type Draft = {
  lastName: string; firstName: string; day: string; month: string; year: string; phone: string; email: string;
};
export type Confirmation = { kind: 'created' | 'updated'; recordId: string; name: string };

let activeScope: string | undefined;
const searches = new Map<string, SearchMemory>();
const drafts = new Map<string, Draft>();
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

export const readSearch = (scope: string) => searches.get(scope);
export const writeSearch = (scope: string, value: SearchMemory) => { searches.set(scope, value); };
export function markStale(scope: string, at: number = Date.now()) {
  const current = searches.get(scope);
  if (current) searches.set(scope, { ...current, staleAt: at });
}

const draftKey = (scope: string, form: string) => `${scope}|${form}`;
export const readDraft = (scope: string, form: string) => drafts.get(draftKey(scope, form));
export const writeDraft = (scope: string, form: string, value: Draft) => { drafts.set(draftKey(scope, form), value); };
export const clearDraft = (scope: string, form: string) => { drafts.delete(draftKey(scope, form)); };

export const rememberConfirmation = (scope: string, value: Confirmation) => { confirmations.set(scope, value); };
export function takeConfirmation(scope: string): Confirmation | undefined {
  const value = confirmations.get(scope);
  confirmations.delete(scope);
  return value;
}

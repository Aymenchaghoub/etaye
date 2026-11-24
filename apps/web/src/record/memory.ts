import type { DayParts } from './asof';

/**
 * What the practitioner chose on one record: the past instant of the view and the applied timeline filters. Held only in
 * this module's memory, scoped to one account and one practice grant, so that the two tabs of a record share the choice
 * and nothing about it reaches the URL or browser storage (BR-015, C14). Record content is never kept here.
 */
export type RecordView = {
  asOf?: string;
  filters: { kind: string; from: DayParts; to: DayParts };
};

const views = new Map<string, RecordView>();
const key = (scope: string, recordId: string) => `${scope}|${recordId}`;

export const readView = (scope: string, recordId: string): RecordView | undefined => views.get(key(scope, recordId));
export const writeView = (scope: string, recordId: string, value: RecordView) => { views.set(key(scope, recordId), value); };
export const clearRecordMemory = () => views.clear();

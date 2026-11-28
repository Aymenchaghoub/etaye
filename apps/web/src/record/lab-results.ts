import type { EventDate, LabResult } from '../api/generated/types.gen';
import { eventLabel } from './evidence';
import { displayDecimal } from './measurements';

const comparators: Record<string, string> = { EQ: '=', LT: '<', LE: '≤', GT: '>', GE: '≥' };

export function labValue(row: LabResult): string {
  if (row.valueKind === 'QUALITATIVE') return row.qualitativeValue ?? 'Résultat non renseigné';
  if (row.valueKind !== 'NUMERIC' || row.numericValue === undefined) return 'Résultat non renseigné';
  const value = displayDecimal(row.numericValue);
  const comparator = row.comparator ? comparators[row.comparator] : undefined;
  return comparator ? `${comparator} ${value}` : value;
}

export function labInterval(row: LabResult): string {
  const lower = row.lowerBound === undefined ? null : displayDecimal(row.lowerBound);
  const upper = row.upperBound === undefined ? null : displayDecimal(row.upperBound);
  const unit = row.intervalUnit ? ` ${row.intervalUnit}` : '';
  const bounds = lower !== null && upper !== null ? `${lower}–${upper}${unit}`
    : lower !== null ? `Borne basse fournie ${lower}${unit}`
      : upper !== null ? `Borne haute fournie ${upper}${unit}` : null;
  const interval = row.referenceIntervalText ?? bounds ?? 'Intervalle non fourni';
  const details = bounds && row.referenceIntervalText && bounds !== row.referenceIntervalText ? bounds : null;
  const textUnit = !bounds && row.intervalUnit ? `Unité de l’intervalle ${row.intervalUnit}` : null;
  return [interval, details, textUnit, row.intervalSource, row.intervalContext].filter(Boolean).join(' · ');
}

export function labSourceNotice(row: LabResult): string | undefined {
  if (row.outsideInterval === 'ABOVE') return 'Au-dessus de l’intervalle fourni · mention de la source';
  if (row.outsideInterval === 'BELOW') return 'En dessous de l’intervalle fourni · mention de la source';
  return undefined;
}

function dateValue(precision: EventDate | undefined, instant: string | undefined, zone: string) {
  return eventLabel(precision ?? (instant ? { precision: 'INSTANT', instant } : { precision: 'UNKNOWN' }), zone);
}

export function labDates(row: LabResult, zone: string) {
  return {
    specimen: dateValue(row.specimenDate, row.specimenAt, zone),
    result: dateValue(row.resultDate, row.resultAt, zone),
  };
}

export type LabPoint = { date: string; scaledValue: bigint; valueLabel: string; label: string };
export type LabSeries = { analyte: string; unit: string; interval: string; points: LabPoint[] };

function pointTime(date: EventDate): string | undefined {
  if (date.precision === 'INSTANT' && date.instant && Number.isFinite(Date.parse(date.instant))) return date.instant;
  if (date.precision === 'DATE' && date.date && Number.isFinite(Date.parse(date.date))) return date.date;
  return undefined;
}

function scaledDecimal(value: string): bigint | undefined {
  const parts = /^(-?)(\d{1,12})(?:\.(\d{1,6}))?$/.exec(value);
  if (!parts) return undefined;
  const magnitude = BigInt(parts[2] + (parts[3] ?? '').padEnd(6, '0'));
  return parts[1] ? -magnitude : magnitude;
}

function comparableBound(value: string | undefined): string | undefined {
  return value === undefined ? undefined : scaledDecimal(value)?.toString() ?? value;
}

/** Same reported context is an eligibility requirement, never inferred from matching concentrations. */
export function comparableSeries(rows: LabResult[]): LabSeries[] {
  const groups = new Map<string, LabSeries>();
  for (const row of rows) {
    const date = pointTime(row.eventDate);
    const context = row.comparisonContext;
    if (row.valueKind !== 'NUMERIC' || !date || row.numericValue === undefined
      || (row.comparator && row.comparator !== 'EQ') || row.unconfirmedTranscription
      || !row.unit || row.intervalUnit !== row.unit || !row.intervalSource
      || !context?.method || !context.fasting || !context.samplingWindow
      || (row.lowerBound === undefined && row.upperBound === undefined && !row.referenceIntervalText)) continue;
    const scaledValue = scaledDecimal(row.numericValue);
    if (scaledValue === undefined) continue;
    const key = JSON.stringify([row.analyte, row.unit, row.eventDate.precision, row.intervalSource,
      context.method, context.fasting, context.samplingWindow,
      comparableBound(row.lowerBound), comparableBound(row.upperBound), row.referenceIntervalText, row.intervalContext]);
    const group = groups.get(key) ?? { analyte: row.analyte, unit: row.unit, interval: labInterval(row), points: [] };
    group.points.push({ date, scaledValue, valueLabel: displayDecimal(row.numericValue), label: labValue(row) });
    groups.set(key, group);
  }
  return [...groups.values()]
    .filter(group => group.points.length <= 100 && new Set(group.points.map(point => Date.parse(point.date))).size >= 2)
    .slice(0, 2);
}

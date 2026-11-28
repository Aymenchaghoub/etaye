import { describe, expect, it } from 'vitest';
import type { LabResult } from '../api/generated/types.gen';
import { comparableSeries, labDates, labInterval, labSourceNotice, labValue } from './lab-results';

const row: LabResult = {
  id: '00000000-0000-0000-0000-000000000000', version: 1, practiceId: 'practice',
  createdAt: '2026-10-20T10:00:00Z', updatedAt: '2026-10-20T10:00:00Z', sourceRevisionId: 'source',
  eventDate: { precision: 'DATE', date: '2026-10-20' }, valueKind: 'NUMERIC', analyte: 'Analyse A',
  numericValue: '7.040', unit: 'unité A', sourceType: 'DOCUMENT_REPORTED',
};

describe('laboratory values retain what their source supplied', () => {
  it('keeps the comparator, decimal scale and qualitative text', () => {
    expect(labValue({ ...row, comparator: 'LT', numericValue: '0.060' })).toBe('< 0,060');
    expect(labValue({ ...row, comparator: 'EQ' })).toBe('= 7,040');
    expect(labValue({ ...row, comparator: 'GE' })).toBe('≥ 7,040');
    expect(labValue({ ...row, valueKind: 'QUALITATIVE', qualitativeValue: 'Détecté, à confirmer' })).toBe('Détecté, à confirmer');
    expect(labValue({ ...row, numericValue: undefined })).toBe('Résultat non renseigné');
    expect(labValue({ ...row, valueKind: 'QUALITATIVE' })).toBe('Résultat non renseigné');
    expect(labValue({ ...row, valueKind: 'future' as LabResult['valueKind'] })).toBe('Résultat non renseigné');
  });

  it('preserves interval text and does not infer a bound, unit or classification', () => {
    expect(labInterval(row)).toBe('Intervalle non fourni');
    expect(labInterval({ ...row, lowerBound: '0' })).toBe('Borne basse fournie 0');
    expect(labInterval({ ...row, upperBound: '11.0', intervalUnit: 'unité B' })).toBe('Borne haute fournie 11,0 unité B');
    expect(labInterval({ ...row, lowerBound: '0', upperBound: '11.0', intervalUnit: 'unité A', intervalSource: 'Laboratoire fictif' }))
      .toBe('0–11,0 unité A · Laboratoire fictif');
    expect(labInterval({ ...row, referenceIntervalText: 'Tel que fourni', intervalContext: 'À jeun' }))
      .toBe('Tel que fourni · À jeun');
    expect(labInterval({ ...row, lowerBound: '0', upperBound: '11.0', intervalUnit: 'unité A', referenceIntervalText: 'À vérifier' }))
      .toBe('À vérifier · 0–11,0 unité A');
    expect(labInterval({ ...row, intervalUnit: 'unité B', referenceIntervalText: 'Texte fourni' }))
      .toBe('Texte fourni · Unité de l’intervalle unité B');
    expect(labSourceNotice({ ...row, upperBound: '1' })).toBeUndefined();
    expect(labSourceNotice({ ...row, outsideInterval: 'ABOVE' })).toContain('mention de la source');
    expect(labSourceNotice({ ...row, outsideInterval: 'BELOW' })).toContain('En dessous');
  });

  it('keeps the specimen and result dates separate with their own precision', () => {
    expect(labDates(row, 'Europe/Paris')).toEqual({ specimen: undefined, result: undefined });
    expect(labDates({ ...row, specimenAt: '2026-10-20T10:00:00Z', resultDate: { precision: 'DATE', date: '2026-10-22' } }, 'Europe/Paris'))
      .toEqual({ specimen: '20/10/2026 12:00', result: '22/10/2026' });
    expect(labDates({ ...row, specimenDate: { precision: 'UNKNOWN' } }, 'Europe/Paris').specimen).toBeUndefined();
  });
});

describe('point series require exact, sufficiently dated compatible facts', () => {
  const compatible: LabResult = {
    ...row, lowerBound: '2', upperBound: '8', intervalUnit: row.unit,
    intervalSource: 'Laboratoire fictif', intervalContext: 'Méthode A, à jeun, matin', comparisonContext: { method: 'A', fasting: 'À jeun', samplingWindow: 'Matin' },
  };
  const another = { ...compatible, eventDate: { precision: 'DATE', date: '2026-10-22' } } as const;

  it('keeps only exact numeric points in one matching context', () => {
    const ineligible: LabResult[] = [
      { ...another, comparator: 'LT' }, { ...another, unit: 'unité B' },
      { ...another, comparisonContext: undefined }, { ...another, comparisonContext: { method: 'B', fasting: 'À jeun', samplingWindow: 'Matin' } },
      { ...another, intervalContext: 'Autres conditions' },
      { ...another, eventDate: { precision: 'UNKNOWN' } }, { ...another, unconfirmedTranscription: true },
      { ...another, lowerBound: undefined, upperBound: undefined }, { ...another, intervalSource: 'Autre laboratoire' },
      { ...another, numericValue: 'not a decimal' }, { ...another, valueKind: 'QUALITATIVE' },
      { ...another, eventDate: { precision: 'INSTANT', instant: '2026-10-22T10:00:00Z' } },
      { ...another, eventDate: { precision: 'DATE', date: 'invalide' } },
      { ...another, numericValue: undefined },
    ];
    expect(comparableSeries([compatible, ...ineligible])).toEqual([]);
    expect(comparableSeries([compatible, another, ...ineligible])).toMatchObject([{ points: [
      { date: '2026-10-20', scaledValue: 7040000n, label: '7,040' }, { date: '2026-10-22', scaledValue: 7040000n, label: '7,040' },
    ] }]);
    expect(comparableSeries([compatible, compatible])).toEqual([]);
    expect(comparableSeries([compatible, another, ...Array<LabResult>(100).fill(compatible)])).toEqual([]);
  });

  it('distinguishes observations that differ by one micro-unit near the decimal limit', () => {
    const [series] = comparableSeries([
      { ...compatible, numericValue: '999999999999.123455' },
      { ...another, numericValue: '999999999999.123456' },
      { ...another, numericValue: '-0.01' },
    ]);
    const [first, second, negative] = series.points;
    expect(second.scaledValue - first.scaledValue).toBe(1n);
    expect(second.valueLabel).toBe('999999999999,123456');
    expect(negative.scaledValue).toBe(-10000n);
  });

  it('compares numerically equal bounds without changing their displayed scale', () => {
    const equivalent = { ...another, lowerBound: '2.0', upperBound: '8.00' };
    expect(comparableSeries([compatible, equivalent])[0].points).toHaveLength(2);
    expect(labInterval(equivalent)).toContain('2,0–8,00');
    expect(comparableSeries([compatible, { ...equivalent, upperBound: '8.01' }])).toEqual([]);
  });

  it('keeps the source instant rather than the day grouping for instant points', () => {
    const make = (instant: string): LabResult => ({ ...compatible, eventDate: { precision: 'INSTANT', instant } });
    const first = '2026-10-20T10:00:00Z';
    const second = '2026-10-20T11:00:00Z';
    expect(comparableSeries([make(first), make(second)])[0].points.map(point => point.date)).toEqual([first, second]);
    expect(comparableSeries([make('bad'), make(second)])).toEqual([]);
  });
});

import { describe, expect, it } from 'vitest';
import type { Measurement } from '../api/generated/types.gen';
import {
  buildRows, buildSeries, chartAlternative, chartAxis, conceptLabel, conceptsOf, DIASTOLIC, displayDecimal, MAX_CHART_PANELS, measureOptions,
  originText, panelMeta, panelTitle, periodFrom, plot, PRESSURE_OPTION, previousRevision, SYSTOLIC, splitPanels,
} from './measurements';

let counter = 0;
const measurement = (patch: Partial<Measurement> & Pick<Measurement, 'concept' | 'value' | 'unit' | 'observedAt'>): Measurement => {
  counter += 1;
  return {
    id: `m-${counter}`, version: 1, createdAt: patch.observedAt, updatedAt: patch.observedAt, practiceId: 'p',
    sourceType: 'PRACTITIONER_OBSERVED', sourceRevisionId: `s-${counter}`, recordedAt: patch.observedAt, authorLabel: 'Dr Nora Lefebvre',
    sourceReference: `SRC-${counter}`, revision: 1, ...patch,
  };
};
const pressure = (group: string, at: string, sys: string, dia: string, unit = 'mmHg'): Measurement[] => [
  measurement({ concept: SYSTOLIC, value: sys, unit, observedAt: at, groupId: group }),
  measurement({ concept: DIASTOLIC, value: dia, unit, observedAt: at, groupId: group }),
];
const claire = (): Measurement[] => [
  ...pressure('g2', '2026-10-20T08:10:00Z', '134', '82'),
  measurement({ concept: 'WEIGHT', value: '68.4', unit: 'kg', observedAt: '2026-10-20T08:12:00Z', revision: 2, version: 2, correctionReason: 'x' }),
  ...pressure('g1', '2026-09-14T07:35:00Z', '128', '80'),
];

describe('decimal display', () => {
  it('keeps every digit as stored and only writes the French comma', () => {
    expect(displayDecimal('68.4')).toBe('68,4');
    expect(displayDecimal('68.40')).toBe('68,40');
    expect(displayDecimal('-0.000001')).toBe('-0,000001');
    expect(displayDecimal('123456789012.123456')).toBe('123456789012,123456');
    expect(displayDecimal('134')).toBe('134');
    expect(displayDecimal('1e3')).toBe('1e3');
  });

  it('labels the concepts it knows and shows any other code verbatim', () => {
    expect(conceptLabel('WEIGHT')).toBe('Poids');
    expect(conceptLabel('HEIGHT')).toBe('HEIGHT');
    expect(conceptLabel('constructor')).toBe('constructor');
  });
});

describe('the rows of the table', () => {
  it('pairs the two components of a group and orders the rows by observation, newest first', () => {
    const rows = buildRows(claire());
    expect(rows.map((row) => [row.label, row.value, row.unit])).toEqual([
      ['Poids', '68,4', 'kg'], ['Pression artérielle', '134/82', 'mmHg'], ['Pression artérielle', '128/80', 'mmHg'],
    ]);
    expect(rows[0].corrected).toBe(true);
    expect(rows[1].corrected).toBe(false);
    expect(rows[1].plotted).toEqual([134, 82]);
  });

  it('shows an incomplete reading as incomplete and never completes it', () => {
    const [systolic] = pressure('g3', '2026-11-12T07:40:00Z', '130', '75');
    const rows = buildRows([systolic]);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ complete: false, missing: 'diastolique', value: '130 / —', plotted: [] });
    const diastolicOnly = buildRows([pressure('g4', '2026-11-12T07:40:00Z', '130', '75')[1]]);
    expect(diastolicOnly[0]).toMatchObject({ complete: false, missing: 'systolique', value: '— / 75' });
  });

  it('never joins two components in different units, nor a group that repeats a component', () => {
    const mixed = buildRows([...pressure('g5', '2026-11-12T07:40:00Z', '130', '75')].map((m, index) => (index === 1 ? { ...m, unit: 'kPa' } : m)));
    expect(mixed.map((row) => row.kind)).toEqual(['single', 'single']);
    expect(new Set(mixed.map((row) => row.unit))).toEqual(new Set(['mmHg', 'kPa']));
    const twice = buildRows([...pressure('g6', '2026-11-12T07:40:00Z', '130', '75'), ...pressure('g6', '2026-11-12T07:40:00Z', '131', '76')]);
    expect(twice).toHaveLength(4);
    expect(twice.every((row) => row.kind === 'single')).toBe(true);
  });

  it('never pairs two components observed at different instants, even in the same group and unit', () => {
    const [systolic, diastolic] = pressure('g7', '2026-11-12T07:40:00Z', '130', '75');
    const later = { ...diastolic, observedAt: '2026-11-12T07:41:00Z' };
    const rows = buildRows([systolic, later]);
    expect(rows).toHaveLength(2);
    expect(rows.every((row) => row.kind === 'single')).toBe(true);
    // The same instant written with another offset is the same instant and does pair.
    const sameInstant = { ...diastolic, observedAt: '2026-11-12T09:40:00+02:00' };
    expect(buildRows([systolic, sameInstant])).toHaveLength(1);
  });

  it('keeps the stored decimals of every plotted value as the texts a chart may label', () => {
    const [paired] = buildRows(pressure('g8', '2026-11-12T07:40:00Z', '130.0', '75.50'));
    expect(paired.texts).toEqual(['130,0', '75,50']);
    const [single] = buildRows([measurement({ concept: 'WEIGHT', value: '68.40', unit: 'kg', observedAt: '2026-10-20T08:12:00Z' })]);
    expect(single.texts).toEqual(['68,40']);
    expect(buildRows([pressure('g9', '2026-11-12T07:40:00Z', '130', '75')[0]])[0].texts).toEqual([]);
  });

  it('treats a blood-pressure component without a group, and any other concept, as a plain measurement', () => {
    const rows = buildRows([measurement({ concept: SYSTOLIC, value: '120', unit: 'mmHg', observedAt: '2026-11-01T08:00:00Z' }),
      measurement({ concept: 'HEIGHT', value: '1.72', unit: 'm', observedAt: '2026-11-02T08:00:00Z' })]);
    expect(rows.map((row) => [row.label, row.value])).toEqual([['HEIGHT', '1,72'], ['Pression systolique', '120']]);
  });

  it('holds back an incomplete reading at the last observed time while more pages exist, never otherwise', () => {
    const [systolic] = pressure('g7', '2026-09-01T08:00:00Z', '120', '70');
    const complete = pressure('g8', '2026-10-01T08:00:00Z', '125', '75');
    expect(buildRows([...complete, systolic], true).map((row) => row.key)).toEqual(['g8']);
    expect(buildRows([...complete, systolic], false).map((row) => row.key)).toEqual(['g8', 'g7']);
    const incompleteNewer = pressure('g9', '2026-11-01T08:00:00Z', '120', '70')[0];
    expect(buildRows([incompleteNewer, ...complete], true).map((row) => row.key)).toEqual(['g9', 'g8']);
  });

  it('keeps long untrusted text and a 12-and-6-digit value exactly as stored', () => {
    const value = '123456789012.123456';
    const [row] = buildRows([measurement({ concept: 'C'.repeat(100), value, unit: 'u'.repeat(40), observedAt: '2026-11-01T08:00:00Z' })]);
    expect(row.value).toBe('123456789012,123456');
    expect(row.label).toHaveLength(100);
    expect(row.unit).toHaveLength(40);
  });

  it('writes the origin in words with the right agreement', () => {
    const [weight, bp] = buildRows(claire());
    expect(originText(weight)).toBe('Observé au cabinet');
    expect(originText(bp)).toBe('Observée au cabinet');
  });
});

describe('the series (BR-009)', () => {
  it('is the exact concept and unit: a second unit is a second series, never converted or merged', () => {
    const rows = buildRows([
      measurement({ concept: 'WEIGHT', value: '68.4', unit: 'kg', observedAt: '2026-10-20T08:12:00Z' }),
      measurement({ concept: 'WEIGHT', value: '150', unit: 'lb', observedAt: '2026-10-25T08:12:00Z' }),
      measurement({ concept: 'WEIGHT', value: '69', unit: 'kg', observedAt: '2026-10-30T08:12:00Z' }),
      measurement({ concept: 'WEIGHT', value: '70', unit: 'Kg', observedAt: '2026-11-02T08:12:00Z' }),
    ]);
    const series = buildSeries(rows);
    expect(series.map((s) => [s.unit, s.rows.length])).toEqual([['Kg', 1], ['kg', 2], ['lb', 1]]);
    expect(panelTitle(series[1], series)).toBe('Poids · kg');
    expect(series[1].rows.map((row) => row.value)).toEqual(['68,4', '69']);
  });

  it('never merges the synthetic blood-pressure series with a stored concept spelled like its option', () => {
    // A concept code is free text (D-66): "p" in mmHg is its own single-value series beside the paired blood pressure.
    const rows = buildRows([
      ...pressure('g1', '2026-09-14T07:35:00Z', '128', '80'),
      measurement({ concept: PRESSURE_OPTION, value: '5', unit: 'mmHg', observedAt: '2026-10-01T08:00:00Z' }),
      measurement({ concept: PRESSURE_OPTION, value: '6', unit: 'mmHg', observedAt: '2026-10-02T08:00:00Z' }),
    ]);
    const series = buildSeries(rows);
    expect(series.map((entry) => [entry.kind, entry.concept, entry.rows.length])).toEqual([
      ['pressure', PRESSURE_OPTION, 1], ['single', PRESSURE_OPTION, 2],
    ]);
    expect(series[0].rows[0].plotted).toEqual([128, 80]);
    expect(series[1].rows.every((row) => row.plotted.length === 1)).toBe(true);
  });

  it('keeps the plain label when the concept has one unit, and states the unit when it is too long for the chart', () => {
    const [bp] = buildSeries(buildRows(pressure('g1', '2026-09-14T07:35:00Z', '128', '80')));
    expect(panelTitle(bp, [bp])).toBe('Pression artérielle');
    const [long] = buildSeries(buildRows([measurement({ concept: 'C', value: '1', unit: 'unité-de-gabarit-très-longue', observedAt: '2026-10-01T08:00:00Z' })]));
    expect(panelTitle(long, [long])).toBe('C · unité-de-gabarit-très-longue');
  });

  it('tells two series apart by their exact code when the label and the unit read the same', () => {
    const rows = buildRows([
      measurement({ concept: 'WEIGHT', value: '68.4', unit: 'kg', observedAt: '2026-10-20T08:12:00Z' }),
      measurement({ concept: 'Poids', value: '70', unit: 'kg', observedAt: '2026-10-21T08:12:00Z' }),
    ]);
    const series = buildSeries(rows);
    expect(series).toHaveLength(2);
    expect(series.map((entry) => panelTitle(entry, series)).sort()).toEqual(['Poids · kg · Poids', 'Poids · kg · WEIGHT']);
  });

  it('counts incomplete readings of a series without drawing them', () => {
    const rows = buildRows([...pressure('g1', '2026-09-14T07:35:00Z', '128', '80'), ...pressure('g2', '2026-10-20T08:10:00Z', '134', '82'),
      pressure('g3', '2026-11-12T07:40:00Z', '130', '75')[0]]);
    const [bp] = buildSeries(rows);
    expect(bp.rows).toHaveLength(2);
    expect(bp.incomplete).toBe(1);
    expect(panelMeta(bp)).toBe('2 mesures · unité unique mmHg · 1 incomplète non tracée');
  });

  it('says a single point draws no curve and that a series may continue on the next page', () => {
    const [weight] = buildSeries(buildRows([measurement({ concept: 'WEIGHT', value: '68.4', unit: 'kg', observedAt: '2026-10-20T08:12:00Z' })]));
    expect(panelMeta(weight)).toBe('1 mesure · pas de courbe');
    expect(panelMeta(weight, true)).toBe('1 mesure · pas de courbe · d’autres mesures existent');
  });

  it('puts blood pressure first, as in the approved reference, then the series by their most recent observation', () => {
    const rows = buildRows([...claire(), measurement({ concept: 'HEIGHT', value: '172', unit: 'cm', observedAt: '2026-11-02T08:00:00Z' })]);
    expect(buildSeries(rows).map((series) => series.label)).toEqual(['Pression artérielle', 'HEIGHT', 'Poids']);
  });

  it('draws at most two panels and leaves the other series in the table', () => {
    const rows = buildRows(['a', 'b', 'c'].map((code, index) => measurement({ concept: code, value: '1', unit: 'u', observedAt: `2026-10-0${index + 1}T08:00:00Z` })));
    const { drawn, tableOnly } = splitPanels(buildSeries(rows));
    expect(drawn).toHaveLength(MAX_CHART_PANELS);
    expect(tableOnly.map((s) => s.concept)).toEqual(['a']);
  });

  it('ignores a value that is not a finite number in the chart and keeps it in the table', () => {
    const [row] = buildRows([measurement({ concept: 'X', value: 'abc', unit: 'u', observedAt: '2026-10-01T08:00:00Z' })]);
    expect(row.value).toBe('abc');
    expect(buildSeries([row])).toEqual([]);
  });
});

describe('the chart', () => {
  it('keeps two close values of 12 integer digits apart on the axis and plots finite points', () => {
    const axis = chartAxis([123456789012.1, 123456789012.2]);
    expect(axis.max).toBeGreaterThan(axis.min);
    expect(axis.ticks).toEqual([...axis.ticks].sort((a, b) => a - b));
    expect(new Set(axis.ticks).size).toBe(axis.ticks.length);
    const [big] = buildSeries(buildRows([
      measurement({ concept: 'BIG', value: '123456789012.1', unit: 'u', observedAt: '2026-10-01T08:00:00Z' }),
      measurement({ concept: 'BIG', value: '123456789012.2', unit: 'u', observedAt: '2026-10-02T08:00:00Z' }),
    ]));
    const drawn = plot(big, 'wide');
    for (const line of drawn.lines) for (const point of line.points) {
      expect(Number.isFinite(point.x)).toBe(true);
      expect(Number.isFinite(point.y)).toBe(true);
    }
    expect(drawn.ticks.every((tick) => Number.isFinite(tick.y))).toBe(true);
  });

  it('computes its axis from the data and never draws a band or a threshold', () => {
    expect(chartAxis([128, 134, 80, 82])).toEqual({ min: 60, max: 150, ticks: [60, 90, 120, 150] });
    expect(chartAxis([10, 14, 12])).toEqual({ min: 9, max: 15, ticks: [9, 12, 15] });
    expect(chartAxis([5, 5])).toEqual({ min: 4, max: 6, ticks: [4, 5, 6] });
    const huge = chartAxis([1e12, 2e12]);
    expect(huge.ticks.length).toBeGreaterThan(1);
    expect(huge.ticks.length).toBeLessThanOrEqual(4);
    const small = chartAxis([0.001, 0.003]);
    expect(small.ticks.every((tick) => Number.isFinite(tick))).toBe(true);
  });

  it('has an alternative text that states every plotted value and whether more measurements exist', () => {
    const [bp] = buildSeries(buildRows([...pressure('g1', '2026-09-14T07:35:00Z', '128', '80'), ...pressure('g2', '2026-10-20T08:10:00Z', '134', '82')]));
    expect(chartAlternative(bp, 'Pression artérielle')).toBe(
      'Pression artérielle : 128/80 mmHg le 14/09/2026, 134/82 mmHg le 20/10/2026. Aucune autre mesure.');
    expect(chartAlternative(bp, 'Pression artérielle', 'Europe/Paris', true)).toContain('Courbe limitée aux mesures chargées.');
    const withIncomplete = buildSeries(buildRows([...pressure('g1', '2026-09-14T07:35:00Z', '128', '80'), ...pressure('g2', '2026-10-20T08:10:00Z', '134', '82'),
      pressure('g3', '2026-11-12T07:40:00Z', '130', '75')[0]]))[0];
    expect(chartAlternative(withIncomplete, 'Pression artérielle')).toBe(
      'Pression artérielle : 128/80 mmHg le 14/09/2026, 134/82 mmHg le 20/10/2026. 1 mesure incomplète n’est pas tracée : voir le tableau.');
    const [two] = buildSeries(buildRows([measurement({ concept: 'A', value: '1', unit: 'u', observedAt: '2026-10-20T06:00:00Z' }),
      measurement({ concept: 'A', value: '2', unit: 'u', observedAt: '2026-10-20T14:00:00Z' })]));
    expect(chartAlternative(two, 'A')).toBe('A : 1 u le 20/10/2026 08:00, 2 u le 20/10/2026 16:00. Aucune autre mesure.');
  });

  it('places a point per observation, the first series above the second, with markers only where the text fits', () => {
    const [bp] = buildSeries(buildRows([...pressure('g1', '2026-09-14T07:35:00Z', '128', '80'), ...pressure('g2', '2026-10-20T08:10:00Z', '134', '82')]));
    const wide = plot(bp, 'wide', 'Europe/Paris');
    expect(wide.lines.map((line) => line.className)).toEqual(['serie', 'serie-2']);
    expect(wide.lines[0].points.map((point) => [point.x, point.y, point.label])).toEqual([[140, 62, '128'], [536, 52, '134']]);
    expect(wide.lines[1].points.map((point) => [point.x, point.y, point.label])).toEqual([[140, 145, '80'], [536, 142, '82']]);
    expect(wide.ticks.map((tick) => [tick.y, tick.label])).toEqual([[180, '60'], [128, '90'], [76, '120'], [24, '150']]);
    expect(wide.dates.map((date) => date.text)).toEqual(['14/09/2026', '20/10/2026']);
    expect(wide.unit).toBe('mmHg');
    const narrow = plot(bp, 'narrow', 'Europe/Paris');
    expect(narrow.lines[0].points.map((point) => point.x)).toEqual([102, 274]);
  });

  it('leaves out the labels and the unit that would clip, since an SVG text never wraps', () => {
    const rows = buildRows([measurement({ concept: 'C', value: '123456789012.123456', unit: 'unité-de-gabarit-très-longue-sans-espace', observedAt: '2026-09-14T07:35:00Z' }),
      measurement({ concept: 'C', value: '123456789013.5', unit: 'unité-de-gabarit-très-longue-sans-espace', observedAt: '2026-10-20T08:10:00Z' })]);
    const [series] = buildSeries(rows);
    const drawn = plot(series, 'wide');
    expect(drawn.unit).toBeUndefined();
    expect(drawn.lines[0].points.every((point) => point.label === undefined)).toBe(true);
    expect(drawn.ticks.every((tick) => tick.label === undefined)).toBe(true);
  });

  it('labels only the first and the last date of a long series, and no value', () => {
    const rows = buildRows(Array.from({ length: 25 }, (_, n) => measurement({ concept: 'A', value: String(30 + (n * 7) % 23), unit: 'u',
      observedAt: new Date(Date.UTC(2026, 4, 26 + 7 * n, 7)).toISOString() })));
    const [series] = buildSeries(rows);
    const drawn = plot(series, 'wide', 'Europe/Paris');
    expect(series.rows).toHaveLength(25);
    expect(drawn.dates.map((date) => date.text)).toEqual(['26/05/2026', '10/11/2026']);
    expect(drawn.lines[0].points.every((point) => point.label === undefined)).toBe(true);
  });
});

describe('the period and the measure filter', () => {
  it('counts a relative period back from the instant of the view, on the wall clock of the practice zone', () => {
    expect(periodFrom('all', new Date('2026-11-25T10:00:00Z'))).toBeUndefined();
    expect(periodFrom('3m', new Date('2026-11-25T10:00:00Z'), 'Europe/Paris')).toBe('2026-08-25T09:00:00.000Z');
    expect(periodFrom('12m', new Date('2026-11-25T10:00:00Z'), 'Europe/Paris')).toBe('2025-11-25T10:00:00.000Z');
    // A past view reads the period from the chosen instant, not from today.
    expect(periodFrom('3m', new Date('2026-10-20T08:14:00Z'), 'Europe/Paris')).toBe('2026-07-20T08:14:00.000Z');
  });

  it('keeps the day or falls back to the last day of a shorter month', () => {
    // 12:00 in summer time (UTC+2) becomes 12:00 in winter time (UTC+1) on the last day of February.
    expect(periodFrom('3m', new Date('2026-05-31T10:00:00Z'), 'Europe/Paris')).toBe('2026-02-28T11:00:00.000Z');
    expect(periodFrom('12m', new Date('2024-02-29T10:00:00Z'), 'Europe/Paris')).toBe('2023-02-28T10:00:00.000Z');
  });

  it('never narrows the window on a day when the clocks change', () => {
    // 29 March 2026, 02:30 does not exist in Paris: the next hour is used.
    expect(periodFrom('3m', new Date('2026-06-29T00:30:00Z'), 'Europe/Paris')).toBe('2026-03-29T01:30:00.000Z');
    // 25 October 2026, 02:30 exists twice in Paris: the earlier reading is used.
    expect(periodFrom('3m', new Date('2027-01-25T01:30:00Z'), 'Europe/Paris')).toBe('2026-10-25T00:30:00.000Z');
  });

  it('reads the period on the wall clock of any zone', () => {
    // Martinique is UTC-4 all year: 10:00 local is 14:00 UTC.
    expect(periodFrom('3m', new Date('2026-11-25T14:00:00Z'), 'America/Martinique')).toBe('2026-08-25T14:00:00.000Z');
    // Nouméa is UTC+11: 10:00 local is 23:00 UTC the day before.
    expect(periodFrom('3m', new Date('2026-11-24T23:00:00Z'), 'Pacific/Noumea')).toBe('2026-08-24T23:00:00.000Z');
  });

  it('builds the measure options from the codes present, blood pressure first, and keeps a selected code', () => {
    const options = measureOptions([SYSTOLIC, DIASTOLIC, 'WEIGHT', 'HEIGHT'], '');
    expect(options.map((option) => [option.value, option.label])).toEqual([
      ['', 'Toutes'], [PRESSURE_OPTION, 'Pression artérielle'], ['c:HEIGHT', 'HEIGHT'], ['c:WEIGHT', 'Poids'],
    ]);
    expect(options[1].concepts).toEqual([SYSTOLIC, DIASTOLIC]);
    expect(measureOptions([], 'c:WEIGHT').map((option) => option.value)).toEqual(['', 'c:WEIGHT']);
    expect(measureOptions([], PRESSURE_OPTION).map((option) => option.value)).toEqual(['', PRESSURE_OPTION]);
    expect(conceptsOf('')).toBeUndefined();
    expect(conceptsOf(PRESSURE_OPTION)).toEqual([SYSTOLIC, DIASTOLIC]);
    expect(conceptsOf('c:WEIGHT')).toEqual(['WEIGHT']);
  });

  it('never mistakes a code for the blood-pressure option: a concept spelled like it keeps its own prefixed value', () => {
    // A concept code is free text (D-66): one literally called "p" must not select the paired blood pressure.
    const options = measureOptions(['p'], '');
    expect(options.map((option) => option.value)).toEqual(['', 'c:p']);
    expect(conceptsOf('c:p')).toEqual(['p']);
    expect(conceptsOf(PRESSURE_OPTION)).toEqual([SYSTOLIC, DIASTOLIC]);
    expect(measureOptions([], 'c:p').map((option) => option.value)).toEqual(['', 'c:p']);
  });
});

describe('the lineage', () => {
  it('finds the revision before the current one', () => {
    const v1 = measurement({ concept: 'WEIGHT', value: '86.4', unit: 'kg', observedAt: '2026-10-20T08:12:00Z' });
    const v2 = measurement({ concept: 'WEIGHT', value: '68.4', unit: 'kg', observedAt: '2026-10-20T08:12:00Z', revision: 2, version: 2 });
    expect(previousRevision([v1, v2], v2.id)).toBe(v1);
    expect(previousRevision([v1, v2], v1.id)).toBeUndefined();
    expect(previousRevision([v1, v2], 'unknown')).toBeUndefined();
  });
});

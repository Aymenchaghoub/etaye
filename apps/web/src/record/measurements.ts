import type { Measurement } from '../api/generated/types.gen';
import { clock, formatInstant, originLabel } from './evidence';
import { monthsBefore } from './asof';
import { DEFAULT_ZONE } from './zone';

/**
 * The display rules of S-11 (BR-009, UD-71 C1), written as pure functions so that the screen and the tests share them.
 * A series is the exact stored concept together with the exact stored unit: nothing is converted, nothing is normalized,
 * and two spellings of a unit are two series, so the failure mode is a split and never a silent merge.
 */

export const SYSTOLIC = 'SYSTOLIC_BLOOD_PRESSURE';
export const DIASTOLIC = 'DIASTOLIC_BLOOD_PRESSURE';
export const PRESSURE_LABEL = 'Pression artérielle';
/**
 * The value of the measure filter that stands for the two components of a blood-pressure reading. Every other option is
 * `c:` followed by the exact concept code, so no free-text code can ever collide with this one.
 */
export const PRESSURE_OPTION = 'p';
const CODE_PREFIX = 'c:';

const LABELS = new Map<string, string>([
  ['WEIGHT', 'Poids'],
  [SYSTOLIC, 'Pression systolique'],
  [DIASTOLIC, 'Pression diastolique'],
]);
/** The French label of a concept code this client knows, the code itself otherwise: no label is ever invented. */
export const conceptLabel = (code: string): string => LABELS.get(code) ?? code;
const isPressureCode = (code: string) => code === SYSTOLIC || code === DIASTOLIC;

/** A decimal as stored, with the French comma: every digit is kept, nothing is rounded or reformatted. */
export function displayDecimal(value: string): string {
  return /^-?\d+(\.\d+)?$/.test(value) ? value.replace('.', ',') : value;
}

export type Row = {
  key: string;
  at: number;
  observedAt: string;
  kind: 'single' | 'pressure';
  concept: string;
  label: string;
  unit: string;
  /** The value as shown: "68,4", "134/82", or "130 / —" for a reading whose other component is missing. */
  value: string;
  /** The numbers a chart may draw: one value, or systolic then diastolic. Empty when the row is incomplete. */
  plotted: number[];
  /** The same values as stored, for the chart labels: a label never reformats a decimal (128.40 stays 128,40). */
  texts: string[];
  complete: boolean;
  missing?: 'systolique' | 'diastolique';
  members: Measurement[];
  /** The measurement whose source, author and revision the row shows. */
  lead: Measurement;
  revision: number;
  corrected: boolean;
  feminine: boolean;
};

const revisionOf = (measurement: Measurement) => measurement.revision ?? measurement.version;

function singleRow(measurement: Measurement): Row {
  const number = Number(measurement.value);
  return {
    key: measurement.id, at: Date.parse(measurement.observedAt), observedAt: measurement.observedAt, kind: 'single',
    concept: measurement.concept, label: conceptLabel(measurement.concept), unit: measurement.unit,
    value: displayDecimal(measurement.value), plotted: Number.isFinite(number) ? [number] : [], texts: [displayDecimal(measurement.value)], complete: true,
    members: [measurement], lead: measurement, revision: revisionOf(measurement), corrected: revisionOf(measurement) > 1,
    feminine: isPressureCode(measurement.concept),
  };
}

/**
 * The rows of the table, newest observation first. A blood-pressure reading is two measurements that share a group; it is a
 * pair only when both components exist with one unit. A group that is incomplete, malformed or in two units is never
 * completed: its components stay as they are and the row says what is missing. With more pages to come, an incomplete
 * reading at the last observed time is held back, because its other component may be on the next page.
 */
export function buildRows(items: Measurement[], more = false): Row[] {
  const order = new Map(items.map((item, index) => [item.id, index]));
  const groups = new Map<string, Measurement[]>();
  const loose: Measurement[] = [];
  for (const item of items) {
    if (item.groupId && isPressureCode(item.concept)) groups.set(item.groupId, [...(groups.get(item.groupId) ?? []), item]);
    else loose.push(item);
  }
  const rows: Row[] = [];
  for (const [group, members] of groups) {
    const systolic = members.filter((member) => member.concept === SYSTOLIC);
    const diastolic = members.filter((member) => member.concept === DIASTOLIC);
    const [s] = systolic;
    const [d] = diastolic;
    // Components of one reading share one unit and one observed instant; a group that does not is never paired.
    if (systolic.length > 1 || diastolic.length > 1 || (s && d && (s.unit !== d.unit || Date.parse(s.observedAt) !== Date.parse(d.observedAt)))) {
      loose.push(...members);
      continue;
    }
    const lead = s ?? d;
    const complete = s !== undefined && d !== undefined;
    const numbers = complete ? [Number(s.value), Number(d.value)] : [];
    rows.push({
      key: group, at: Date.parse(lead.observedAt), observedAt: lead.observedAt, kind: 'pressure', concept: PRESSURE_OPTION,
      label: PRESSURE_LABEL, unit: lead.unit,
      value: complete ? `${displayDecimal(s.value)}/${displayDecimal(d.value)}`
        : s ? `${displayDecimal(s.value)} / —` : `— / ${displayDecimal(d.value)}`,
      plotted: numbers.every(Number.isFinite) ? numbers : [],
      texts: complete ? [displayDecimal(s.value), displayDecimal(d.value)] : [], complete, ...(complete ? {} : { missing: s ? 'diastolique' as const : 'systolique' as const }),
      members, lead, revision: Math.max(...members.map(revisionOf)), corrected: members.some((member) => revisionOf(member) > 1), feminine: true,
    });
  }
  rows.push(...loose.map(singleRow));
  rows.sort((a, b) => b.at - a.at || (order.get(a.lead.id) ?? 0) - (order.get(b.lead.id) ?? 0));
  if (!more || items.length === 0) return rows;
  const boundary = Math.min(...items.map((item) => Date.parse(item.observedAt)));
  return rows.filter((row) => row.complete || row.kind !== 'pressure' || row.at !== boundary);
}

export type Series = {
  key: string;
  kind: 'single' | 'pressure';
  concept: string;
  label: string;
  unit: string;
  /** The complete rows that can be drawn, oldest first. */
  rows: Row[];
  /** Incomplete readings of this series: in the table, never on the curve. */
  incomplete: number;
};

const SEPARATOR = '\u001f';

/** The series of the rows: blood pressure first, as in the approved reference, then by most recent observation (no ranking). */
export function buildSeries(rows: Row[]): Series[] {
  const series = new Map<string, Series>();
  for (const row of rows) {
    if (!row.complete || row.plotted.length === 0) continue;
    const key = `${row.concept}${SEPARATOR}${row.unit}`;
    const known = series.get(key);
    if (known) known.rows.unshift(row);
    else series.set(key, { key, kind: row.kind, concept: row.concept, label: row.label, unit: row.unit, rows: [row], incomplete: 0 });
  }
  for (const row of rows) {
    if (row.complete) continue;
    const known = series.get(`${row.concept}${SEPARATOR}${row.unit}`);
    if (known) known.incomplete += 1;
  }
  return [...series.values()].sort((a, b) => Number(b.kind === 'pressure') - Number(a.kind === 'pressure'));
}

/** The most series a screen draws at once: the others stay in the table, and the practitioner picks one with the filter. */
export const MAX_CHART_PANELS = 2;
export function splitPanels(series: Series[]): { drawn: Series[]; tableOnly: Series[] } {
  return { drawn: series.slice(0, MAX_CHART_PANELS), tableOnly: series.slice(MAX_CHART_PANELS) };
}

/** A unit longer than this cannot be written in the chart, whose text never wraps: the panel title says it instead. */
export const UNIT_IN_CHART_MAX = 10;

/** The title of a panel: the label, and the unit when another series shares the label or the unit is too long for the chart. */
export function panelTitle(series: Series, all: Series[]): string {
  const sameLabel = all.filter((other) => other !== series && other.label === series.label);
  const withUnit = sameLabel.length > 0 || series.unit.length > UNIT_IN_CHART_MAX ? `${series.label} · ${series.unit}` : series.label;
  // Two codes can read the same (the code Poids and the label of WEIGHT, in one unit): the exact code then tells them apart.
  return sameLabel.some((other) => other.unit === series.unit && other.concept !== series.concept) ? `${withUnit} · ${series.concept}` : withUnit;
}

export function panelMeta(series: Series, more = false): string {
  const count = series.rows.length;
  const incomplete = series.incomplete > 0
    ? ` · ${series.incomplete} incomplète${series.incomplete > 1 ? 's' : ''} non tracée${series.incomplete > 1 ? 's' : ''}` : '';
  const base = count === 1 ? '1 mesure · pas de courbe' : `${count} mesures${series.kind === 'pressure' ? ` · unité unique ${series.unit}` : ''}`;
  return `${base}${incomplete}${more ? ' · d’autres mesures existent' : ''}`;
}

/** The nice steps an axis may use; a step is the smallest that covers the data in at most three intervals. */
const STEPS = [1, 2, 3, 5, 10];

/** An axis computed from the data alone: no reference band and no threshold is ever drawn (BR-009). */
export function chartAxis(values: number[]): { min: number; max: number; ticks: number[] } {
  const low = Math.min(...values);
  const high = Math.max(...values);
  const span = high - low;
  const pad = span === 0 ? Math.max(Math.abs(low) * 0.1, 1) : span * 0.1;
  const from = low - pad;
  const to = high + pad;
  const start = Math.floor(Math.log10((to - from) / 3)) - 1;
  for (let exponent = start; exponent < start + 40; exponent += 1) {
    for (const base of STEPS) {
      const step = base * 10 ** exponent;
      const min = Math.floor(from / step) * step;
      const max = Math.ceil(to / step) * step;
      if (Math.round((max - min) / step) <= 3) {
        const count = Math.round((max - min) / step);
        const ticks = Array.from({ length: count + 1 }, (_, index) => Number((min + index * step).toPrecision(12)));
        return { min: ticks[0], max: ticks[count], ticks };
      }
    }
  }
  return { min: low, max: high === low ? low + 1 : high, ticks: [low, high] };
}

export type PlotVariant = 'wide' | 'narrow';
const GEOMETRY = {
  wide: { viewBox: '0 0 640 220', x0: 60, x1: 616, top: 24, bottom: 180, inset: 80, dates: 212 },
  narrow: { viewBox: '0 0 340 200', x0: 60, x1: 316, top: 24, bottom: 160, inset: 42, dates: 192 },
} as const;

export type Plot = {
  viewBox: string;
  x0: number;
  x1: number;
  dateY: number;
  /** The unit written in the chart, or undefined when it is too long: the title of the panel says it. */
  unit?: string;
  ticks: { y: number; label?: string }[];
  lines: { className: 'serie' | 'serie-2'; points: { x: number; y: number; label?: string; labelY: number }[] }[];
  dates: { x: number; text: string }[];
};

const LABEL_MAX = 7;
const LABELLED_POINTS_MAX = 6;

/** The geometry of one chart: markers are the observations, nothing is interpolated, and text that would clip is left out. */
export function plot(series: Series, variant: PlotVariant, zone: string = DEFAULT_ZONE): Plot {
  const g = GEOMETRY[variant];
  const columns = series.rows.map((row) => row.plotted);
  const lines = series.kind === 'pressure' ? [columns.map((c) => c[0]), columns.map((c) => c[1])] : [columns.map((c) => c[0])];
  const axis = chartAxis(lines.flat());
  const y = (value: number) => Math.round(g.bottom - ((value - axis.min) / (axis.max - axis.min)) * (g.bottom - g.top));
  const first = series.rows[0].at;
  const last = series.rows[series.rows.length - 1].at;
  const x = (at: number) => Math.round(last === first ? (g.x0 + g.x1) / 2
    : g.x0 + g.inset + ((at - first) / (last - first)) * (g.x1 - g.x0 - 2 * g.inset));
  const tickLabels = axis.ticks.map((tick) => displayDecimal(String(tick)));
  const showTicks = tickLabels.every((label) => label.length <= LABEL_MAX && !label.includes('e'));
  const texts = series.rows.flatMap((row) => row.texts);
  const showLabels = series.rows.length <= LABELLED_POINTS_MAX && texts.every((text) => text.length <= LABEL_MAX);
  const shown = series.rows.length <= 3 ? series.rows : [series.rows[0], series.rows[series.rows.length - 1]];
  const labelled = shown.map((row) => clock(row.observedAt, zone).date);
  const sameDay = new Set(labelled).size < labelled.length;
  return {
    viewBox: g.viewBox, x0: g.x0, x1: g.x1, dateY: g.dates,
    unit: series.unit.length <= UNIT_IN_CHART_MAX ? series.unit : undefined,
    ticks: axis.ticks.map((tick, index) => ({ y: y(tick), ...(showTicks ? { label: tickLabels[index] } : {}) })),
    lines: lines.map((values, index) => ({
      className: index === 0 ? 'serie' : 'serie-2',
      points: values.map((value, at) => {
        const py = y(value);
        return { x: x(series.rows[at].at), y: py, labelY: index === 0 ? py - 10 : py + 20, ...(showLabels ? { label: texts[at * lines.length + index] } : {}) };
      }),
    })),
    dates: shown.map((row, index) => ({ x: x(row.at), text: sameDay ? formatInstant(row.observedAt, zone) : labelled[index] })),
  };
}

/** The text alternative of a chart: every plotted value with its date, then whether more measurements exist. */
export function chartAlternative(series: Series, title: string, zone: string = DEFAULT_ZONE, more = false): string {
  const days = series.rows.map((row) => clock(row.observedAt, zone).date);
  const parts = series.rows.map((row, index) => {
    const repeated = days.filter((day) => day === days[index]).length > 1;
    return `${row.value} ${series.unit} le ${repeated ? formatInstant(row.observedAt, zone) : days[index]}`;
  });
  const closing = more ? 'Courbe limitée aux mesures chargées.'
    : series.incomplete > 0 ? `${series.incomplete} mesure${series.incomplete > 1 ? 's incomplètes ne sont pas tracées' : ' incomplète n’est pas tracée'} : voir le tableau.`
      : 'Aucune autre mesure.';
  return `${title} : ${parts.join(', ')}. ${closing}`;
}

export const PERIODS = [
  { value: 'all', label: 'Tout le dossier', months: 0 },
  { value: '3m', label: '3 derniers mois', months: 3 },
  { value: '12m', label: '12 derniers mois', months: 12 },
] as const;
export type Period = (typeof PERIODS)[number]['value'];

/**
 * The inclusive lower bound of a relative period. It counts back from the instant of the view (now, or the past instant
 * chosen) on the wall clock of the practice zone, so a past view never reads "3 derniers mois" from today.
 */
export function periodFrom(period: Period, view: Date, zone: string = DEFAULT_ZONE): string | undefined {
  const months = PERIODS.find((candidate) => candidate.value === period)?.months ?? 0;
  return months === 0 ? undefined : monthsBefore(zone, view, months).toISOString();
}

export type MeasureOption = { value: string; label: string; concepts: string[] };

/** The measure filter: all, blood pressure when the record has it, then the other concepts present, by label. */
export function measureOptions(codes: Iterable<string>, selected: string): MeasureOption[] {
  const present = new Set(codes);
  const options: MeasureOption[] = [{ value: '', label: 'Toutes', concepts: [] }];
  if ([...present].some(isPressureCode) || selected === PRESSURE_OPTION) {
    options.push({ value: PRESSURE_OPTION, label: PRESSURE_LABEL, concepts: [SYSTOLIC, DIASTOLIC] });
  }
  const others = new Set([...present].filter((code) => !isPressureCode(code)));
  if (selected.startsWith(CODE_PREFIX)) others.add(selected.slice(CODE_PREFIX.length));
  [...others].sort((a, b) => conceptLabel(a).localeCompare(conceptLabel(b), 'fr') || a.localeCompare(b))
    .forEach((code) => options.push({ value: `${CODE_PREFIX}${code}`, label: conceptLabel(code), concepts: [code] }));
  return options;
}

export const conceptsOf = (option: string): string[] | undefined => (option === '' ? undefined
  : option === PRESSURE_OPTION ? [SYSTOLIC, DIASTOLIC]
    : option.startsWith(CODE_PREFIX) ? [option.slice(CODE_PREFIX.length)] : undefined);

/** The origin of a row in words; "Observé" or "Observée" agrees with the measure, as in the approved reference. */
export function originText(row: Row): string {
  switch (row.lead.sourceType) {
    case 'PATIENT_REPORTED': return row.feminine ? 'Déclarée par la patiente' : 'Déclaré par la patiente';
    case 'DOCUMENT_REPORTED': return row.feminine ? 'Rapportée par un document' : 'Rapporté par un document';
    default: return row.feminine ? 'Observée au cabinet' : 'Observé au cabinet';
  }
}

/** The line that names a source under the table: what it is, when, by whom. */
export function sourceHeadline(row: Row, zone: string = DEFAULT_ZONE): string {
  const what = row.lead.sourceType === 'PRACTITIONER_OBSERVED' ? 'Mesure au cabinet' : originLabel[row.lead.sourceType];
  return [what, clock(row.observedAt, zone).date, row.lead.authorLabel].filter(Boolean).join(' · ');
}

/** What the revision before the current one said, from the lineage oldest first: the value and unit kept, and its time. */
export function previousRevision(lineage: Measurement[], currentId: string): Measurement | undefined {
  const index = lineage.findIndex((revision) => revision.id === currentId);
  return index > 0 ? lineage[index - 1] : undefined;
}

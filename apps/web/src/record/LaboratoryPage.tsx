import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type MutableRefObject, type ReactNode } from 'react';
import { accountApi, type AccountResult } from '../api/browser';
import type { LabAnalytePage, LabResult, LabResultPage, ListLabResultsData } from '../api/generated/types.gen';
import { Notice, Skeleton } from '../patients/patient-ui';
import { formatInstant } from './evidence';
import { comparableSeries, labDates, labInterval, labSourceNotice, labValue, type LabSeries } from './lab-results';
import { PERIODS, periodFrom } from './measurements';
import { NO_LAB_FILTER, type LabFilters } from './memory';
import { useRecord } from './RecordPage';
import { type Remote, useRemote } from './remote';
import { useSource } from './SourceDisclosure';
import './laboratory.css';

type Page<T> = { items: T[]; nextCursor: string | null };
type More = 'idle' | 'loading' | 'failed' | 'restart' | 'limited';

/** Extra pages belong to the initial snapshot. The keyed owner unmounts whenever that snapshot changes. */
function useMore<T>(first: Page<T>, load: (cursor: string, signal: AbortSignal) => Promise<AccountResult<Page<T>>>) {
  const { expire, refused } = useRecord();
  const [page, setPage] = useState(first);
  const [status, setStatus] = useState<More>('idle');
  const [wait, setWait] = useState(0);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);

  async function next() {
    if (page.nextCursor === null || active.current) return;
    const controller = new AbortController();
    active.current = controller;
    setWait(0);
    setStatus('loading');
    const result = await load(page.nextCursor, controller.signal);
    if (controller.signal.aborted) return;
    active.current = null;
    if (result.ok) {
      setPage({ items: [...page.items, ...result.data.items], nextCursor: result.data.nextCursor });
      setStatus('idle');
    } else if (result.status === 401) expire();
    else if (result.status === 403 || result.status === 404) refused(result.status);
    else if (result.status === 429) { setWait(result.retryAfter); setStatus('limited'); }
    else setStatus(result.status === 400 ? 'restart' : 'failed');
  }
  return { page, status, wait, next };
}

function useRefusal(state: Remote<unknown>) {
  const { refused } = useRecord();
  useEffect(() => {
    if (state.phase === 'forbidden') refused(403);
    if (state.phase === 'hidden') refused(404);
  }, [state.phase, refused]);
  return state.phase === 'forbidden' || state.phase === 'hidden';
}

export function LaboratoryPage() {
  const ctx = useRecord();
  const applied = ctx.view.labs ?? NO_LAB_FILTER;
  const [draft, setDraft] = useState(applied);
  const [refresh, setRefresh] = useState(0);
  const { anchor, revision } = useMemo(() => ({
    anchor: new Date(ctx.asOf ?? Date.now()),
    revision: refresh,
  }), [ctx.asOf, refresh]);
  const query = useMemo(() => ({
    from: periodFrom(applied.period, anchor, ctx.zone),
    to: applied.period === 'all' ? undefined : anchor.toISOString(),
    analyteKey: applied.analyteKey || undefined,
    asOf: ctx.asOf,
  }), [ctx.zone, ctx.asOf, anchor, applied]);
  const scope = JSON.stringify([ctx.practiceId, ctx.recordId, query, revision]);
  const [results] = useRemote((signal) => accountApi.readLabResults(ctx.practiceId, ctx.recordId, query, signal), scope, ctx.expire);
  const refused = useRefusal(results);
  const viewAt = results.phase === 'ready' ? results.data.viewAt ?? ctx.asOf : undefined;
  const reload = () => setRefresh(value => value + 1);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    reload();
    ctx.setView({ labs: draft });
  }
  function reset() {
    setDraft(NO_LAB_FILTER);
    reload();
    ctx.setView({ labs: NO_LAB_FILTER });
  }

  if (refused) return null;
  return <div className="etaye-pile etaye-biologie">
    <form className="etaye-filtres" role="search" aria-label="Filtrer les résultats de biologie" onSubmit={submit}>
      <div className="etaye-champ">
        <label className="etaye-champ__libelle" htmlFor="lab-analyte">Analyse</label>
        {results.phase === 'ready' ? <AnalyteFilter viewAt={viewAt} selected={draft.analyteKey}
          select={(analyteKey) => setDraft({ ...draft, analyteKey })} />
          : <select id="lab-analyte" className="etaye-champ__saisie" disabled><option>Toutes</option></select>}
      </div>
      <div className="etaye-champ">
        <label className="etaye-champ__libelle" htmlFor="lab-period">Période</label>
        <select id="lab-period" className="etaye-champ__saisie" value={draft.period}
          onChange={event => setDraft({ ...draft, period: event.target.value as LabFilters['period'] })}>
          {PERIODS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </div>
      <div className="etaye-ligne">
        <button type="submit" className="etaye-bouton etaye-bouton--secondaire">Appliquer les filtres</button>
        {(applied.analyteKey || applied.period !== 'all') && <button type="button" className="etaye-bouton etaye-bouton--discret" onClick={reset}>
          Réinitialiser les filtres
        </button>}
      </div>
    </form>
    {results.phase === 'loading' && <Skeleton label="Chargement des résultats de biologie…" />}
    {results.phase === 'failed' && <Notice kind="attention" alert title="Résultats non chargés"
      actions={<Retry run={reload} />}>Les résultats de biologie sont temporairement indisponibles. Le dossier reste accessible.</Notice>}
    {results.phase === 'limited' && <Notice kind="attention" alert title="Limite temporaire de requêtes"
      actions={<Retry run={reload} wait={results.wait} />}>Les demandes de biologie sont temporairement limitées.</Notice>}
    {results.phase === 'ready' && <LabResults key={scope} first={results.data} query={query}
      historical={Boolean(ctx.asOf)} filtered={Boolean(applied.analyteKey || applied.period !== 'all')}
      bounded={applied.period !== 'all'} reset={reset} reload={reload} />}
  </div>;
}

function Retry({ run, wait = 0, label = 'Réessayer' }: { run: () => void; wait?: number; label?: string }) {
  const [remaining, setRemaining] = useState(wait);
  useEffect(() => {
    if (wait <= 0) { setRemaining(0); return undefined; }
    const until = Date.now() + wait * 1000;
    setRemaining(wait);
    const timer = setInterval(() => {
      const seconds = Math.max(0, Math.ceil((until - Date.now()) / 1000));
      setRemaining(seconds);
      if (!seconds) clearInterval(timer);
    }, 1000);
    return () => clearInterval(timer);
  }, [wait]);
  return <button type="button" className="etaye-bouton etaye-bouton--secondaire" onClick={run} disabled={remaining > 0}>
    {remaining ? `${label} dans ${remaining} s` : label}
  </button>;
}

function AnalyteFilter({ viewAt, selected, select }: { viewAt?: string; selected: string; select: (key: string) => void }) {
  const ctx = useRecord();
  const key = JSON.stringify([ctx.practiceId, ctx.recordId, viewAt]);
  const [options, retry] = useRemote(
    signal => accountApi.readLabAnalytes(ctx.practiceId, ctx.recordId, { asOf: viewAt }, signal), key, ctx.expire);
  const denied = useRefusal(options);
  if (denied) return null;
  if (options.phase === 'ready') return <AnalyteOptions key={key} first={options.data} viewAt={viewAt}
    selected={selected} select={select} retry={retry} />;
  return <>
    <select id="lab-analyte" className="etaye-champ__saisie" disabled><option>{options.phase === 'loading' ? 'Chargement…' : 'Indisponible'}</option></select>
    {(options.phase === 'failed' || options.phase === 'limited') && <Retry run={retry} label="Réessayer les analyses"
      wait={options.phase === 'limited' ? options.wait : 0} />}
  </>;
}

function AnalyteOptions({ first, viewAt, selected, select, retry }: {
  first: LabAnalytePage; viewAt?: string; selected: string; select: (key: string) => void; retry: () => void;
}) {
  const ctx = useRecord();
  const options = useMore(first, (cursor, signal) => accountApi.readLabAnalytes(ctx.practiceId, ctx.recordId,
    { asOf: viewAt, cursor }, signal));
  const hasSelection = options.page.items.some(item => item.key === selected);
  return <>
    <select id="lab-analyte" className="etaye-champ__saisie" value={selected} onChange={event => select(event.target.value)}>
      <option value="">Toutes</option>
      {selected && !hasSelection && <option value={selected}>Analyse sélectionnée</option>}
      {options.page.items.map(option => <option key={option.key} value={option.key}>{option.label}</option>)}
    </select>
    {options.status === 'loading' && <span role="status">Chargement des analyses…</span>}
    {options.page.nextCursor && options.status !== 'loading' && <Retry run={options.status === 'restart' ? retry : () => { void options.next(); }}
      wait={options.wait} label={options.status === 'idle' ? 'Afficher plus d’analyses' : 'Réessayer les analyses'} />}
  </>;
}

function LabResults({ first, query, historical, filtered, bounded, reset, reload }: {
  first: LabResultPage; query: ListLabResultsData['query']; historical: boolean; filtered: boolean;
  bounded: boolean; reset: () => void; reload: () => void;
}) {
  const ctx = useRecord();
  const results = useMore(first, (cursor, signal) => accountApi.readLabResults(ctx.practiceId, ctx.recordId, { ...query, cursor }, signal));
  const { items, nextCursor } = results.page;
  if (!items.length) return <div className="etaye-vide">
    <strong>{filtered ? 'Aucun résultat pour ces filtres' : 'Aucun résultat de biologie'}</strong>
    <span>{filtered ? 'Modifiez l’analyse ou la période.' : historical ? 'Aucun résultat enregistré à cette date.'
      : `Aucun résultat de biologie enregistré pour ${ctx.patientLabel}.`}</span>
    {bounded && <span>La période ne comprend que les résultats dont la date est connue.</span>}
    {filtered && <Retry run={reset} label="Effacer les filtres" />}
  </div>;

  const viewAt = first.viewAt ?? ctx.asOf;
  const series = nextCursor ? [] : comparableSeries(items);
  const numericAnalytes = items.filter(row => row.valueKind === 'NUMERIC').map(row => row.analyte);
  const insufficientContext = !nextCursor && !series.length && new Set(numericAnalytes).size < numericAnalytes.length;
  return <>
    {bounded && <p className="etaye-2">La période ne comprend que les résultats dont la date est connue.</p>}
    <ResultsTable rows={items} viewAt={viewAt}>
      {results.status === 'loading' && <p role="status">Chargement de la suite…</p>}
      {results.status !== 'idle' && results.status !== 'loading' &&
        <Notice kind="attention" alert title="Suite de la liste indisponible">Les résultats déjà affichés restent consultables.</Notice>}
      {nextCursor && results.status !== 'loading' &&
        <div className="etaye-ligne"><Retry run={results.status === 'restart' ? reload : () => { void results.next(); }}
          wait={results.wait} label={results.status === 'idle' ? 'Afficher plus de résultats'
            : results.status === 'restart' ? 'Recharger la liste' : 'Réessayer d’afficher la suite'} /></div>}
      {nextCursor && <p className="etaye-2">Affichez la liste complète pour comparer les résultats chargés.</p>}
      {insufficientContext && <p className="etaye-2">La comparaison nécessite des dates distinctes et des unités, intervalles et conditions de prélèvement compatibles.</p>}
      {series.map((group, index) => <LabChart key={index} series={group} />)}
    </ResultsTable>
  </>;
}

type MarkerRegistry = MutableRefObject<Map<string, (element: string) => void>>;

function ResultsTable({ rows, viewAt, children }: { rows: LabResult[]; viewAt?: string; children?: ReactNode }) {
  const ctx = useRecord();
  const id = useId();
  const registry = useRef(new Map<string, (element: string) => void>());
  const sources = [...new Map(rows.map(row => [row.sourceRevisionId, row])).values()];
  const corrected = rows.filter(row => row.supersedesId);
  return <>
    <section className="etaye-pile" aria-labelledby={id}>
      <h2 className="ui-section" id={id}>Résultats de biologie</h2>
      <table className="etaye-tableau etaye-tableau--empile" aria-labelledby={id}>
        <thead><tr>{['Prélèvement', 'Analyse', 'Résultat', 'Unité', 'Intervalle fourni', 'Source'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
        <tbody>{rows.map(row => {
          const dates = labDates(row, ctx.zone);
          const number = sources.findIndex(source => source.sourceRevisionId === row.sourceRevisionId) + 1;
          const markerId = `lab-mark-${row.id}`;
          const value = labValue(row);
          const outside = labSourceNotice(row);
          return <tr key={row.id}>
            <td data-l="Prélèvement"><span className="etaye-num">{dates.specimen ?? 'Date inconnue'}</span>
              {dates.result && <div>Résultat du <span className="etaye-num">{dates.result}</span></div>}</td>
            <td data-l="Analyse">{row.analyte}</td>
            <td data-l="Résultat" className={row.valueKind === 'NUMERIC' ? `etaye-col-num${value.length > 19 ? ' etaye-lab-long-value' : ''}` : undefined}>{value}</td>
            <td data-l="Unité">{row.unit ?? 'Unité non fournie'}</td>
            <td data-l="Intervalle fourni">{labInterval(row)}
              {outside && <div><strong>{outside}</strong></div>}
              {row.unconfirmedTranscription && <div><strong>Saisie manuelle · à vérifier contre la source</strong></div>}</td>
            <td data-l="Source"><a className="etaye-repere etaye-repere--seul" id={markerId} href={`#lab-source-${row.sourceRevisionId}`}
              aria-label={`Source ${number} · ${row.analyte} · ${dates.specimen ?? dates.result ?? 'date inconnue'}`}
              onClick={event => { event.preventDefault(); registry.current.get(row.sourceRevisionId)?.(markerId); }}>{number}</a></td>
          </tr>;
        })}</tbody>
      </table>
    </section>
    {children}
    {corrected.map(row => <LabHistory key={row.id} row={row} viewAt={viewAt} />)}
    <section className="etaye-pile" aria-label="Sources datées">
      <h2 className="ui-section">Sources datées</h2>
      {sources.map((source, index) => {
        const first = rows.find(row => row.sourceRevisionId === source.sourceRevisionId) ?? source;
        return <LabSource key={source.sourceRevisionId} row={source} viewAt={viewAt} number={index + 1}
          markerId={`lab-mark-${first.id}`} registry={registry} />;
      })}
    </section>
  </>;
}

function LabSource({ row, viewAt, number, markerId, registry }: {
  row: LabResult; viewAt?: string; number: number; markerId: string; registry: MarkerRegistry;
}) {
  const ctx = useRecord();
  const [back, setBack] = useState(markerId);
  const reference = row.sourceReference ?? row.sourceRevisionId;
  const source = useSource({ id: row.sourceRevisionId, number, reference }, ctx.practiceId, viewAt, ctx.expire,
    'alone', { markerId: back, label: `${number} · ${reference}`, backLabel: 'Revenir au résultat',
      failureText: 'Le résultat enregistré reste affiché.' });
  useEffect(() => {
    const markers = registry.current;
    markers.set(row.sourceRevisionId, element => { setBack(element); source.activate(); });
    return () => { markers.delete(row.sourceRevisionId); };
  });
  return <div className="etaye-lab-details" id={`lab-source-${row.sourceRevisionId}`}>{source.panel}</div>;
}

function LabHistory({ row, viewAt }: { row: LabResult; viewAt?: string }) {
  const id = useId();
  const [open, setOpen] = useState(false);
  return <div className="etaye-lab-details">
    <button type="button" className="etaye-bouton etaye-bouton--discret" aria-expanded={open} aria-controls={id}
      onClick={() => setOpen(value => !value)}>Historique du résultat · {row.analyte}</button>
    <div id={id} hidden={!open}>{open && <LabRevisions row={row} viewAt={viewAt} />}</div>
  </div>;
}

function LabRevisions({ row, viewAt }: { row: LabResult; viewAt?: string }) {
  const ctx = useRecord();
  const [history, retry] = useRemote(signal => accountApi.readLabRevisions(ctx.practiceId, ctx.recordId, row.id, viewAt, signal),
    JSON.stringify([ctx.practiceId, ctx.recordId, row.id, viewAt]), ctx.expire);
  const denied = useRefusal(history);
  if (denied) return null;
  if (history.phase === 'loading') return <Skeleton label="Chargement de l’historique…" />;
  if (history.phase !== 'ready') return <Notice kind="attention" alert title="Historique temporairement indisponible"
    actions={<Retry run={retry} wait={history.phase === 'limited' ? history.wait : 0} />}>La version affichée reste consultable.</Notice>;
  return <div className="etaye-pile">
    {history.data.truncated && <p role="status">Historique partiel · version originale et dernières versions affichées.</p>}
    <ol className="etaye-lab-revisions">{history.data.revisions.map((entry, index) => <LabRevision key={entry.id}
      row={entry} current={entry.id === row.id} viewAt={viewAt} number={index + 1} />)}</ol>
  </div>;
}

function LabRevision({ row, current, viewAt, number }: { row: LabResult; current: boolean; viewAt?: string; number: number }) {
  const ctx = useRecord();
  const source = useSource({ id: row.sourceRevisionId, number, reference: row.sourceReference ?? row.sourceRevisionId },
    ctx.practiceId, viewAt, ctx.expire, 'inline');
  return <li>
    <strong>Version {row.revision ?? row.version} · {labValue(row)}{row.unit && ` ${row.unit}`}{current && ' · version affichée'}</strong>
    {row.recordedAt && <div>Enregistrée le {formatInstant(row.recordedAt, ctx.zone)}{row.authorLabel && ` · ${row.authorLabel}`}</div>}
    {row.correctionReason && <div>Motif consigné · {row.correctionReason}</div>}
    <div>{labInterval(row)} {source.marker}</div>
    {source.panel}
  </li>;
}

function LabChart({ series }: { series: LabSeries }) {
  const id = useId();
  const { zone } = useRecord();
  const ticks = series.points.map(point => Date.parse(point.date));
  const first = Math.min(...ticks);
  const last = Math.max(...ticks);
  const initial = series.points[0];
  const low = series.points.reduce((min, point) => point.scaledValue < min.scaledValue ? point : min, initial);
  const high = series.points.reduce((max, point) => point.scaledValue > max.scaledValue ? point : max, initial);
  const span = high.scaledValue - low.scaledValue;
  const axis = Math.max(68, Math.max(low.valueLabel.length, high.valueLabel.length) * 8 + 10);
  const firstX = axis + 22;
  const lastX = 278;
  const dateLabel = (date: string) => date.includes('T') ? formatInstant(date, zone) : date.split('-').reverse().join('/');
  const axisDate = (date: number) => new Intl.DateTimeFormat('fr-FR', {
    day: '2-digit', month: '2-digit', year: '2-digit',
    timeZone: initial.date.includes('T') ? zone : 'UTC',
  }).format(date);
  const y = (value: bigint) => span === 0n ? 78 : 126 - Number(value - low.scaledValue) / Number(span) * 88;
  return <section className="etaye-panneau" aria-labelledby={id}>
    <div className="etaye-panneau__tete"><h2 id={id}>Résultats comparables · {series.analyte}</h2></div>
    <div className="etaye-panneau__corps etaye-pile">
      <p>{series.interval}. Chaque point correspond à un résultat daté. Le tableau reste la référence.</p>
      <svg className="etaye-lab-chart" viewBox="0 0 320 152" role="img"
        aria-label={`${series.analyte} · ${series.unit} · ${series.points.map(point => `${dateLabel(point.date)} · ${point.label}`).join(' ; ')}`}>
        <line x1={axis} x2={axis} y1="18" y2="126" /><line x1={axis} x2="306" y1="126" y2="126" />
        <text x={axis - 5} y={y(low.scaledValue) - 4} textAnchor="end">{low.valueLabel}</text>
        {span > 0n && <text x={axis - 5} y={y(high.scaledValue) - 4} textAnchor="end">{high.valueLabel}</text>}
        <text x={firstX} y="146" textAnchor="middle">{axisDate(first)}</text>
        <text x={lastX} y="146" textAnchor="middle">{axisDate(last)}</text>
        {series.points.map((point, index) =>
          <circle key={index} cx={firstX + (Date.parse(point.date) - first) / (last - first) * (lastX - firstX)} cy={y(point.scaledValue)} r="5">
            <title>{dateLabel(point.date)} · {point.label} {series.unit}</title>
          </circle>)}
      </svg>
    </div>
  </section>;
}

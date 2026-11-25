import { type MutableRefObject, useEffect, useId, useMemo, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { Measurement, MeasurementRevisionList } from '../api/generated/types.gen';
import { Notice, Skeleton } from '../patients/patient-ui';
import { Courbe } from './Courbe';
import { clock, formatInstant, originLabel, originState } from './evidence';
import { NO_MEASURE_FILTER } from './memory';
import {
  buildRows, buildSeries, chartAlternative, conceptsOf, displayDecimal, measureOptions, originText, panelMeta, panelTitle, PERIODS,
  type Period, periodFrom, plot, previousRevision, type Row, type Series, sourceHeadline, splitPanels, SYSTOLIC,
} from './measurements';
import { useRecord } from './RecordPage';
import { outcome, type Remote } from './remote';
import { useSource } from './SourceDisclosure';

const PAGE = 25;
/** Corrected measurements whose previous value is read when the panel appears; the others are read on "Voir l'historique". */
const AUTOLOAD_LINEAGES = 5;
type Phase = 'loading' | 'ready' | 'failed' | 'limited';

/**
 * S-11: the measurements of the record, their trend and the lineage of every correction, current or as known at a past
 * instant. It reads only: no measurement is entered or corrected here (D-24). A missing value is never drawn as zero, units
 * are never converted, and a series longer than the loaded pages says so instead of passing for the whole trend.
 */
export function MeasurementsPage() {
  const ctx = useRecord();
  const applied = ctx.view.measures ?? NO_MEASURE_FILTER;
  const [period, setPeriod] = useState<Period>(applied.period);
  const [concept, setConcept] = useState(applied.concept);
  const [itemsState, setItems] = useState<Measurement[]>([]);
  const [nextState, setNext] = useState<string | null>(null);
  const [phaseState, setPhase] = useState<Phase>('loading');
  // The view (instant, period and measure) the rows on screen were requested for: rows of another view are never shown.
  const [rowsOf, setRowsOf] = useState<string | undefined>();
  const [wait, setWait] = useState(0);
  const [more, setMore] = useState<'idle' | 'loading' | 'failed'>('idle');
  const [attempt, setAttempt] = useState(0);
  const [answered, setAnswered] = useState(false);
  // The codes this record showed since the screen opened stay selectable, so a filter without result at another instant stays reachable.
  const [seen, setSeen] = useState<ReadonlySet<string>>(new Set());

  // The instant a relative period counts back from: now, or the past instant of the view, read in the practice zone.
  const anchor = useMemo(() => (ctx.asOf ? new Date(ctx.asOf) : new Date()), [ctx.asOf, applied.period, applied.concept, attempt]);
  const query = useMemo(() => ({
    concept: conceptsOf(applied.concept),
    from: periodFrom(applied.period, anchor, ctx.zone),
    asOf: ctx.asOf,
  }), [applied.period, applied.concept, anchor, ctx.zone, ctx.asOf]);
  const { practiceId, recordId, refused } = ctx;
  // The session poll hands the screen a new `expire` function every time: it is read through a ref so a poll never reloads.
  const expireRef = useRef(ctx.expire);
  expireRef.current = ctx.expire;
  const moreRequest = useRef<AbortController | null>(null);
  const viewKey = JSON.stringify(query);
  const sameView = rowsOf === viewKey;
  const items = sameView ? itemsState : [];
  const next = sameView ? nextState : null;
  const phase: Phase = sameView ? phaseState : 'loading';
  const filtered = applied.period !== 'all' || applied.concept !== '';

  useEffect(() => {
    const request = new AbortController();
    moreRequest.current?.abort();
    setRowsOf(viewKey);
    setPhase('loading');
    setItems([]);
    setNext(null);
    setMore('idle');
    void accountApi.readMeasurements(practiceId, recordId, { ...query, limit: PAGE }, request.signal).then((result) => {
      if (request.signal.aborted) return;
      if (result.ok) {
        setItems(result.data.items);
        setNext(result.data.nextCursor);
        setSeen((known) => new Set([...known, ...result.data.items.map((item) => item.concept)]));
        setAnswered(true);
        setPhase('ready');
      } else if (result.status === 401) expireRef.current();
      else if (result.status === 403 || result.status === 404) refused(result.status);
      else if (result.status === 429) { setWait(result.retryAfter); setPhase('limited'); }
      else setPhase('failed');
    });
    return () => { request.abort(); moreRequest.current?.abort(); };
  }, [practiceId, recordId, query, viewKey, attempt, refused]);

  async function showMore() {
    if (!next || more === 'loading') return;
    const request = new AbortController();
    moreRequest.current = request;
    setMore('loading');
    const result = await accountApi.readMeasurements(practiceId, recordId, { ...query, cursor: next, limit: PAGE }, request.signal);
    // The view changed or the page was left while the page was loading: this answer belongs to something else.
    if (request.signal.aborted) return;
    if (!result.ok) {
      if (result.status === 401) expireRef.current();
      else if (result.status === 403 || result.status === 404) refused(result.status);
      else setMore('failed');
      return;
    }
    const known = new Set(items.map((item) => item.id));
    setItems([...items, ...result.data.items.filter((item) => !known.has(item.id))]);
    setNext(result.data.nextCursor);
    setSeen((codes) => new Set([...codes, ...result.data.items.map((item) => item.concept)]));
    setMore('idle');
  }

  function apply(event: { preventDefault: () => void }) {
    event.preventDefault();
    ctx.setView({ measures: { period, concept } });
  }
  function reset() {
    setPeriod('all');
    setConcept('');
    ctx.setView({ measures: NO_MEASURE_FILTER });
  }

  const rows = useMemo(() => buildRows(items, next !== null), [items, next]);
  const series = useMemo(() => buildSeries(rows), [rows]);
  const { drawn, tableOnly } = splitPanels(series);
  const past = ctx.asOf ? clock(new Date(ctx.asOf), ctx.zone) : undefined;
  const emptyRecord = phase === 'ready' && items.length === 0 && !filtered;
  const noMatch = phase === 'ready' && items.length === 0 && filtered;
  // The most units one measure is recorded in: more than one means two series that must never be read as one.
  const sharedLabelUnits = Math.max(0, ...series.map((entry) => series.filter((other) => other.label === entry.label).length));
  const registry = useRef(new Map<number, () => void>());
  const sources = useId();
  // The first corrected measurements shown read their previous value at once; the others read it when their history opens.
  const autoloaded = new Set(drawn.flatMap((entry) => entry.rows.filter((row) => row.corrected)
    .flatMap((row) => row.members.filter((member) => (member.revision ?? member.version) > 1).map((member) => member.id))).slice(0, AUTOLOAD_LINEAGES));

  return <>
    {answered && !emptyRecord && phase !== 'failed' && phase !== 'limited' && (
      <MeasureFilters period={period} setPeriod={setPeriod} concept={concept} setConcept={setConcept}
        codes={seen} apply={apply} reset={reset} />
    )}
    {phase === 'loading' && (
      <section aria-labelledby="h-mes" className="etaye-pile" aria-busy="true">
        <h2 className="ui-section" id="h-mes">Mesures du dossier</h2>
        <Skeleton label="Chargement des mesures…" />
      </section>
    )}
    {(phase === 'failed' || phase === 'limited') && (
      <section aria-labelledby="h-mes" className="etaye-pile">
        <h2 className="ui-section" id="h-mes">Mesures du dossier</h2>
        <Notice kind="attention" alert title={phase === 'limited' ? 'Trop de requêtes' : 'Les mesures n’ont pas pu être chargées'}
          actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => setAttempt((n) => n + 1)}>
            <span aria-hidden="true" className="etaye-icone etaye-icone--relancer" />Réessayer
          </button>}>
          {phase === 'limited'
            ? `Patientez ${wait > 0 ? `${wait} secondes` : 'un instant'} avant de recharger les mesures.`
            : 'Rien n’a été supprimé. Réessayez ; si le problème continue, prévenez l’administration du cabinet.'}
        </Notice>
        <div className="etaye-vide">
          <strong>Mesures indisponibles</strong>
          <span>Ceci n’est pas un dossier sans mesure : les mesures n’ont pas pu être lues.</span>
        </div>
      </section>
    )}
    {emptyRecord && past && (
      <section aria-labelledby="h-mes" className="etaye-pile">
        <h2 className="ui-section" id="h-mes">Mesures du dossier</h2>
        <Notice kind="neutre" title="Rien n’était enregistré à cette date">
          Cela ne signifie pas que les valeurs étaient normales : aucune mesure n’avait encore été consignée.
        </Notice>
        <div className="etaye-vide">
          <strong>Aucune mesure enregistrée au {past.date} à {past.time}</strong>
          <span>Revenez à la situation actuelle ou choisissez une date plus récente.</span>
        </div>
      </section>
    )}
    {emptyRecord && !past && (
      <section aria-labelledby="h-tab" className="etaye-pile">
        <h2 className="ui-section" id="h-tab">Toutes les mesures</h2>
        <div className="etaye-vide">
          <strong>Aucune mesure au dossier</strong>
          <span>Aucune mesure n’a été enregistrée pour {ctx.patientLabel}. Aucune valeur n’est supposée : ce n’est ni zéro ni une valeur normale.</span>
        </div>
      </section>
    )}
    {phase === 'ready' && drawn.length > 0 && (
      <div className="etaye-cols etaye-cols--egal">
        {drawn.map((entry) => (
          <SeriesPanel key={entry.key} series={entry} all={series} more={next !== null} zone={ctx.zone} past={past !== undefined}
            showMore={() => void showMore()} loadingMore={more === 'loading'} autoloaded={autoloaded} />
        ))}
      </div>
    )}
    {phase === 'ready' && sharedLabelUnits > 1 && (
      <Notice kind="neutre" title={sharedLabelUnits === 2 ? 'Deux unités pour une même mesure' : 'Plusieurs unités pour une même mesure'}>
        Séries séparées : jamais converties, jamais additionnées, jamais reliées entre elles. Deux écritures d’une même unité donneraient aussi deux séries : on sépare, on ne fusionne pas.
      </Notice>
    )}
    {phase === 'ready' && tableOnly.length > 0 && (
      <p className="etaye-2" style={{ margin: 0, fontSize: 14 }}>
        {tableOnly.length} autre{tableOnly.length > 1 ? 's' : ''} série{tableOnly.length > 1 ? 's' : ''} ne {tableOnly.length > 1 ? 'sont' : 'est'} pas
        tracée{tableOnly.length > 1 ? 's' : ''} : {tableOnly.length > 1 ? 'elles restent' : 'elle reste'} dans le tableau. Choisissez une mesure pour {tableOnly.length > 1 ? 'les' : 'la'} tracer.
      </p>
    )}
    {noMatch && (
      <section aria-labelledby="h-tab" className="etaye-pile">
        <h2 className="ui-section" id="h-tab">Toutes les mesures</h2>
        <div className="etaye-vide">
          <strong>Aucune mesure ne correspond à ces filtres</strong>
          <span>Cela ne signifie pas que le dossier est sans mesure : des mesures peuvent exister en dehors de ces filtres.</span>
          <div className="etaye-ligne"><button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={reset}>Réinitialiser les filtres</button></div>
        </div>
        <div className="etaye-ligne"><span className="etaye-3" role="status" style={{ fontSize: 13 }}>0 mesure affichée</span></div>
      </section>
    )}
    {phase === 'ready' && items.length > 0 && (
      <section aria-labelledby="h-tab" className="etaye-pile">
        <h2 className="ui-section" id="h-tab">Toutes les mesures</h2>
        <MeasurementTable rows={rows} zone={ctx.zone} registry={registry} listId={sources} />
        {more === 'failed' && (
          <Notice kind="attention" alert title="La suite n’a pas pu être chargée"
            actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => void showMore()}>Réessayer</button>}>
            Les mesures déjà affichées restent consultables.
          </Notice>
        )}
        <div className="etaye-ligne">
          <span className="etaye-3" role="status" style={{ fontSize: 13 }}>
            {rows.length} mesure{rows.length > 1 ? 's' : ''} affichée{rows.length > 1 ? 's' : ''}{next ? ' · d’autres mesures existent' : ''}
          </span>
          {next && more !== 'failed' && drawn.length === 0 && (
            <button className="etaye-bouton etaye-bouton--secondaire" type="button" disabled={more === 'loading'} onClick={() => void showMore()}>
              {more === 'loading' ? 'Chargement…' : 'Afficher plus de mesures'}
            </button>
          )}
        </div>
      </section>
    )}
    {phase === 'ready' && rows.length > 0 && (
      <SourceList rows={rows} zone={ctx.zone} registry={registry} listId={sources} />
    )}
  </>;
}

function MeasureFilters({ period, setPeriod, concept, setConcept, codes, apply, reset }: {
  period: Period; setPeriod: (value: Period) => void; concept: string; setConcept: (value: string) => void;
  codes: ReadonlySet<string>; apply: (event: { preventDefault: () => void }) => void; reset: () => void;
}) {
  const options = measureOptions(codes, concept);
  return (
    <form aria-label="Filtrer les mesures" className="etaye-filtres" role="search" noValidate onSubmit={apply}>
      <div className="etaye-champ">
        <label className="etaye-champ__libelle" htmlFor="f-per">Période</label>
        <select className="etaye-champ__saisie" id="f-per" value={period} onChange={(event) => setPeriod(event.target.value as Period)}>
          {PERIODS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </div>
      <div className="etaye-champ">
        <label className="etaye-champ__libelle" htmlFor="f-mes">Mesure</label>
        <select className="etaye-champ__saisie" id="f-mes" value={concept} onChange={(event) => setConcept(event.target.value)}>
          {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </div>
      <div className="etaye-ligne">
        <button className="etaye-bouton etaye-bouton--secondaire" type="submit">Appliquer les filtres</button>
        <button className="etaye-bouton etaye-bouton--discret" type="button" onClick={reset}>Réinitialiser les filtres</button>
      </div>
    </form>
  );
}

function SeriesPanel({ series, all, more, zone, past, showMore, loadingMore, autoloaded }: {
  series: Series; all: Series[]; more: boolean; zone: string; past: boolean; showMore: () => void; loadingMore: boolean; autoloaded: ReadonlySet<string>;
}) {
  const heading = useId();
  const title = panelTitle(series, all);
  const first = series.rows[0];
  const last = series.rows[series.rows.length - 1];
  const single = series.rows.length === 1;
  const unitWords = series.unit;
  const label = useMemo(() => chartAlternative(series, title, zone, more), [series, title, zone, more]);
  const wide = useMemo(() => (single ? undefined : plot(series, 'wide', zone)), [series, single, zone]);
  const narrow = useMemo(() => (single ? undefined : plot(series, 'narrow', zone)), [series, single, zone]);
  const corrected = series.rows.filter((row) => row.corrected);
  const dated = (row: Row) => `${clock(row.observedAt, zone).date} à ${clock(row.observedAt, zone).time}`;
  return (
    <section aria-labelledby={heading} className="etaye-panneau">
      <div className="etaye-panneau__tete">
        <h2 id={heading}>{title}</h2>
        <span className="etaye-3" style={{ fontSize: 12 }}>{panelMeta(series, more)}</span>
      </div>
      <div className="etaye-panneau__corps etaye-pile" style={single ? { gap: 'var(--espace-2)' } : undefined}>
        {single && <>
          <div>
            <span className="donnee-forte etaye-num" style={{ fontSize: 28 }}>{first.value}</span> {unitWords}{' '}
            <span className="etaye-2" style={{ fontSize: 14 }}>le {dated(first)}</span>
          </div>
          {!first.corrected && past && (
            <div className="etaye-ligne">
              <span className="etaye-3" style={{ fontSize: 12 }}>
                Version {first.revision} · enregistrée le {first.lead.recordedAt ? formatInstant(first.lead.recordedAt, zone) : '—'}
                {first.lead.authorLabel ? ` · ${first.lead.authorLabel}` : ''}
              </span>
            </div>
          )}
        </>}
        {!single && wide && narrow && <>
          <Courbe plot={wide} variant="wide" label={label} />
          <Courbe plot={narrow} variant="narrow" label={label} />
          <p className="etaye-3" style={{ fontSize: 12, margin: 0 }}>
            {series.rows.length === 2
              ? `Deux points reliés par un trait : aucune mesure entre le ${clock(first.observedAt, zone).date} et le ${clock(last.observedAt, zone).date}. `
              : 'Points reliés par un trait : '}
            Le trait n’est pas une mesure. Le tableau ci-dessous est la référence.
          </p>
          {series.incomplete > 0 && (
            <p className="etaye-3" style={{ fontSize: 12, margin: 0 }}>
              {series.incomplete > 1 ? `${series.incomplete} mesures incomplètes ne sont pas tracées` : '1 mesure incomplète n’est pas tracée'} sur la courbe : voir le tableau.
            </p>
          )}
          {more && (
            <Notice kind="attention" title="Courbe limitée aux mesures chargées"
              actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" disabled={loadingMore} onClick={showMore}>
                {loadingMore ? 'Chargement…' : 'Afficher plus de mesures'}
              </button>}>
              {series.rows.length} mesure{series.rows.length > 1 ? 's sont affichées' : ' est affichée'} ; d’autres existent. Cette courbe n’est pas la tendance complète de la période.
            </Notice>
          )}
        </>}
        {single && series.incomplete > 0 && (
          <p className="etaye-3" style={{ fontSize: 12, margin: 0 }}>
            {series.incomplete > 1 ? `${series.incomplete} mesures incomplètes ne sont pas tracées` : '1 mesure incomplète n’est pas tracée'} : voir le tableau.
          </p>
        )}
        {corrected.map((row) => row.members.filter((member) => (member.revision ?? member.version) > 1).map((member) => (
          <CorrectedNote key={member.id} measurement={member} zone={zone}
            name={row.kind === 'pressure' ? `${series.label} ${member.concept === SYSTOLIC ? 'systolique' : 'diastolique'}` : title}
            when={dated(row)} autoload={autoloaded.has(member.id)} />
        )))}
      </div>
    </section>
  );
}

/**
 * A corrected measurement: the current value is the latest revision, the previous value and the reason stay visible, and
 * the whole lineage opens in place. The previous value comes from the lineage read, so it is shown only once that read
 * answered; the reason is already on the measurement.
 */
function CorrectedNote({ measurement, zone, name, when, autoload }: { measurement: Measurement; zone: string; name: string; when: string; autoload: boolean }) {
  const ctx = useRecord();
  const uid = useId();
  const [open, setOpen] = useState(false);
  const [remote, setRemote] = useState<Remote<MeasurementRevisionList> | undefined>();
  const [attempt, setAttempt] = useState(0);
  const { practiceId, asOf, refused } = ctx;
  const expireRef = useRef(ctx.expire);
  expireRef.current = ctx.expire;
  const wanted = open || autoload;

  useEffect(() => {
    if (!wanted) return undefined;
    const request = new AbortController();
    setRemote({ phase: 'loading' });
    void accountApi.readMeasurementRevisions(practiceId, measurement.id, asOf, request.signal).then((result) => {
      if (request.signal.aborted) return;
      // A permission denial removes the protected content at once, like every other panel of the record (I-27).
      if (!result.ok && (result.status === 403 || result.status === 404)) { refused(result.status); return; }
      setRemote(outcome(result, () => expireRef.current()));
    });
    return () => request.abort();
  }, [wanted, attempt, practiceId, measurement.id, asOf, refused]);

  const revision = measurement.revision ?? measurement.version;
  const lineage = remote?.phase === 'ready' ? remote.data.items : undefined;
  const previous = lineage ? previousRevision(lineage, measurement.id) : undefined;
  const regionId = `lin-${uid}`;
  return <>
    <div className="etaye-ligne">
      <span className="etaye-etat etaye-etat--corrige">Corrigé · version {revision}</span>
      <span className="etaye-3" style={{ fontSize: 12 }}>
        version précédente {previous ? `${displayDecimal(previous.value)} ${previous.unit} ` : ''}conservée
        {measurement.correctionReason ? ` · motif : ${measurement.correctionReason}` : ''}
      </span>
    </div>
    <div>
      <button type="button" className="etaye-bouton etaye-bouton--discret" aria-expanded={open} aria-controls={regionId}
        onClick={() => setOpen((value) => !value)}>
        {open ? 'Masquer l’historique' : 'Voir l’historique'}<span className="etaye-vh"> : {name} du {when}</span>
      </button>
    </div>
    {open && (
      <div className="etaye-source__corps" id={regionId} role="region" aria-label={`Historique des versions : ${name}`}
        aria-busy={remote?.phase === 'loading' ? true : undefined}>
        {(!remote || remote.phase === 'loading') && <p className="etaye-3" role="status" style={{ fontSize: 13, margin: 0 }}>Chargement de l’historique…</p>}
        {remote && remote.phase !== 'loading' && remote.phase !== 'ready' && (
          <Notice kind="attention" alert title="L’historique n’a pas pu être chargé"
            actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => setAttempt((n) => n + 1)}>Réessayer</button>}>
            La valeur courante reste affichée au-dessus. Rien n’a été supprimé.
          </Notice>
        )}
        {lineage && (
          <ol className="etaye-lignee">
            {lineage.map((entry, index) => <LineageEntry key={entry.id} entry={entry} next={lineage[index + 1]} zone={zone} />)}
          </ol>
        )}
      </div>
    )}
  </>;
}

function LineageEntry({ entry, next, zone }: { entry: Measurement; next: Measurement | undefined; zone: string }) {
  const revision = entry.revision ?? entry.version;
  return (
    <li>
      <strong>Version {revision}</strong> · <span className="etaye-num">{displayDecimal(entry.value)}</span> {entry.unit}
      {entry.recordedAt && <> · enregistrée le <span className="etaye-num">{formatInstant(entry.recordedAt, zone)}</span></>}
      {entry.authorLabel && ` · ${entry.authorLabel}`}
      {' · '}
      {next
        ? <><span className={`etaye-etat etaye-etat--${originState[entry.sourceType].tone === 'plain' ? 'observe' : originState[entry.sourceType].tone}`}>{originLabel[entry.sourceType]}</span>
          {' · '}<span>remplacée par la version {next.revision ?? next.version}</span></>
        : <span className="etaye-etat etaye-etat--corrige">Version courante</span>}
      {entry.correctionReason ? ` · motif : ${entry.correctionReason}` : ''}
    </li>
  );
}

function MeasurementTable({ rows, zone, registry, listId }: {
  rows: Row[]; zone: string; registry: MutableRefObject<Map<number, () => void>>; listId: string;
}) {
  return (
    <table className="etaye-tableau etaye-tableau--empile" aria-labelledby="h-tab">
      <thead>
        <tr>
          <th scope="col">Date de mesure</th><th scope="col">Mesure</th><th className="etaye-col-num" scope="col">Valeur</th>
          <th scope="col">Unité</th><th scope="col">Origine</th><th scope="col">Source</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => {
          const number = index + 1;
          return (
            <tr key={row.key}>
              <td data-l="Date de mesure"><span className="etaye-num">{formatInstant(row.observedAt, zone)}</span></td>
              <td data-l="Mesure">{row.label}</td>
              <td className="etaye-col-num" data-l="Valeur"><span className="etaye-num">{row.value}</span></td>
              <td data-l="Unité">{row.unit}</td>
              <td data-l="Origine">
                {!row.complete && <><span className="etaye-etat etaye-etat--manquant">Incomplète · {row.missing} manquante</span><br /></>}
                {originText(row)}{row.lead.authorLabel ? ` · ${row.lead.authorLabel}` : ''}
                {row.corrected && <>{' · '}<span className="etaye-etat etaye-etat--corrige">corrigé · v{row.revision}</span></>}
              </td>
              <td data-l="Source">
                <a className="etaye-repere" id={`${listId}-m-${number}`} href={`#${listId}`}
                  aria-label={`Source ${number} : ${row.lead.sourceReference ?? row.lead.sourceRevisionId}`}
                  onClick={(event) => { event.preventDefault(); registry.current.get(number)?.(); }}>{number}</a>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

/** The sources of the table, one disclosure each, opened in place by their numbered marker (E1, D-45): no popover, no drawer. */
function SourceList({ rows, zone, registry, listId }: { rows: Row[]; zone: string; registry: MutableRefObject<Map<number, () => void>>; listId: string }) {
  return (
    <section aria-labelledby={`${listId}-t`} className="etaye-pile" id={listId}>
      <h2 className="ui-section" id={`${listId}-t`}>Sources</h2>
      <ul className="etaye-sources-liste">
        {rows.map((row, index) => <SourceEntry key={row.key} row={row} number={index + 1} zone={zone} registry={registry} markerId={`${listId}-m-${index + 1}`} />)}
      </ul>
    </section>
  );
}

function SourceEntry({ row, number, zone, registry, markerId }: {
  row: Row; number: number; zone: string; registry: MutableRefObject<Map<number, () => void>>; markerId: string;
}) {
  const ctx = useRecord();
  const reference = row.lead.sourceReference ?? row.lead.sourceRevisionId;
  const source = useSource({ id: row.lead.sourceRevisionId, reference, number }, ctx.practiceId, ctx.asOf, ctx.expire, 'alone', { markerId });
  useEffect(() => {
    registry.current.set(number, source.activate);
  });
  useEffect(() => () => { registry.current.delete(number); }, [registry, number]);
  return (
    <li>
      <span className="etaye-3" style={{ fontSize: 12, display: 'inline-flex', gap: 6, alignItems: 'baseline', flexWrap: 'wrap' }}>
        <span className="etaye-repere etaye-repere--seul" aria-hidden="true">{number}</span>
        <span>{sourceHeadline(row, zone)} · <span className="etaye-num">{reference}</span></span>
      </span>
      {source.panel}
    </li>
  );
}

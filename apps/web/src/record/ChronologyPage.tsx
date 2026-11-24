import { useEffect, useMemo, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { HistorySection, HistorySummary, TimelineItem } from '../api/generated/types.gen';
import { ErrorSummary, type FieldError } from '../auth/AuthComponents';
import { Notice, Skeleton } from '../patients/patient-ui';
import { historyPath } from '../patients/routes';
import { dayStart, type DayParts, nextDayStart, parseFilterDay } from './asof';
import { clock, eventLabel, kindLabel, KIND_FILTERS, originLabel, originState, sectionThing, sectionTitle, sectionWords, SECTION_ORDER } from './evidence';
import { type Remote } from './remote';
import { useSource } from './SourceDisclosure';
import { useRecord } from './RecordPage';

const PAGE = 25;
type Phase = 'loading' | 'ready' | 'failed' | 'limited';

/** S-09: the chronology of the record, evidence items with their occurrence and recording dates, and the sections panel. */
export function ChronologyPage() {
  const ctx = useRecord();
  const applied = ctx.view.filters;
  const [items, setItems] = useState<TimelineItem[]>([]);
  const [next, setNext] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>('loading');
  const [wait, setWait] = useState(0);
  const [more, setMore] = useState<'idle' | 'loading' | 'failed'>('idle');
  const [attempt, setAttempt] = useState(0);
  const [answered, setAnswered] = useState(false);
  const [kind, setKind] = useState(applied.kind);
  const [from, setFrom] = useState<DayParts>(applied.from);
  const [to, setTo] = useState<DayParts>(applied.to);
  const [errors, setErrors] = useState<(FieldError & { field: string })[]>([]);

  const query = useMemo(() => {
    const group = KIND_FILTERS.find((candidate) => candidate.value === applied.kind);
    const start = parseFilterDay('Du', applied.from);
    const end = parseFilterDay('Au', applied.to);
    return {
      kind: group && group.kinds.length ? group.kinds : undefined,
      from: start.ok && start.value ? dayStart(start.value)?.toISOString() : undefined,
      to: end.ok && end.value ? nextDayStart(end.value)?.toISOString() : undefined,
      asOf: ctx.asOf,
    };
  }, [applied, ctx.asOf]);
  const dateFiltered = applied.from.day !== '' || applied.to.day !== '';
  const filtered = applied.kind !== '' || dateFiltered;
  const { practiceId, recordId, refused } = ctx;
  // The session poll hands the screen a new `expire` function every time: it is read through a ref so a poll never
  // reloads the first page. `epoch` numbers the first-page loads, so a late "Afficher plus" of an earlier view is dropped.
  const expireRef = useRef(ctx.expire);
  expireRef.current = ctx.expire;
  const epoch = useRef(0);

  useEffect(() => {
    const request = new AbortController();
    epoch.current += 1;
    setPhase('loading');
    setItems([]);
    setNext(null);
    setMore('idle');
    void accountApi.readTimeline(practiceId, recordId, { ...query, limit: PAGE }, request.signal).then((result) => {
      if (request.signal.aborted) return;
      if (result.ok) {
        setItems(result.data.items);
        setNext(result.data.nextCursor);
        setAnswered(true);
        setPhase('ready');
      } else if (result.status === 401) expireRef.current();
      else if (result.status === 403 || result.status === 404) refused(result.status);
      else if (result.status === 429) { setWait(result.retryAfter); setPhase('limited'); }
      else setPhase('failed');
    });
    return () => request.abort();
  }, [practiceId, recordId, query, attempt, refused]);

  async function showMore() {
    if (!next || more === 'loading') return;
    const started = epoch.current;
    setMore('loading');
    const result = await accountApi.readTimeline(practiceId, recordId, { ...query, cursor: next, limit: PAGE });
    // The view changed while the page was loading (another instant or filter): this page belongs to the earlier view.
    if (started !== epoch.current) return;
    if (!result.ok) {
      if (result.status === 401) expireRef.current();
      else if (result.status === 403 || result.status === 404) refused(result.status);
      else setMore('failed');
      return;
    }
    const known = new Set(items.map((item) => item.id));
    setItems([...items, ...result.data.items.filter((item) => !known.has(item.id))]);
    setNext(result.data.nextCursor);
    setMore('idle');
  }

  function apply(event: { preventDefault: () => void }) {
    event.preventDefault();
    const start = parseFilterDay('Du', from);
    const end = parseFilterDay('Au', to);
    const problems: (FieldError & { field: string })[] = [];
    if (!start.ok) problems.push({ id: 'f-du-j', field: 'du', summary: start.summary, detail: start.detail });
    if (!end.ok) problems.push({ id: 'f-au-j', field: 'au', summary: end.summary, detail: end.detail });
    if (start.ok && end.ok && start.value && end.value
      && Date.UTC(start.value.year, start.value.month - 1, start.value.day) > Date.UTC(end.value.year, end.value.month - 1, end.value.day)) {
      problems.push({ id: 'f-du-j', field: 'du', summary: 'Du : après la date « Au »', detail: 'Erreur : la date de début est après la date de fin.' });
    }
    setErrors(problems);
    if (problems.length) return;
    ctx.setView({ filters: { kind, from, to } });
  }

  function reset() {
    const empty = { day: '', month: '', year: '' };
    setKind(''); setFrom(empty); setTo(empty); setErrors([]);
    ctx.setView({ filters: { kind: '', from: empty, to: empty } });
  }

  const dated = items.filter((item) => item.eventDate.precision !== 'UNKNOWN');
  const undated = items.filter((item) => item.eventDate.precision === 'UNKNOWN');
  const shown = items.length;
  const past = ctx.asOf ? clock(new Date(ctx.asOf)) : undefined;
  const emptyRecord = phase === 'ready' && shown === 0 && !filtered;
  const noMatch = phase === 'ready' && shown === 0 && filtered;
  let number = 0;
  const row = (item: TimelineItem) => { number += 1; return <ChronologyItem key={item.id} item={item} number={number} />; };

  return <>
    {(ctx.history.phase === 'failed' || ctx.history.phase === 'limited') && (
      <Notice kind="attention" alert title={ctx.history.phase === 'limited' ? 'Trop de requêtes' : 'Les rubriques n’ont pas pu être chargées'}
        actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={ctx.retryHistory}>Réessayer</button>}>
        {ctx.history.phase === 'limited' ? `Patientez ${ctx.history.wait > 0 ? `${ctx.history.wait} secondes` : 'un instant'} avant de recharger les rubriques. ` : ''}
        Ne pas conclure à l’absence d’allergie, d’antécédent ou de traitement : l’état de ces rubriques est inconnu de l’affichage.
        La chronologie reste consultable ; rien n’a été supprimé.
      </Notice>
    )}
    {phase === 'failed' && (
      <Notice kind="attention" alert title="La chronologie n’a pas pu être chargée"
        actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => setAttempt((n) => n + 1)}>Réessayer</button>}>
        Le panneau des rubriques et le bloc Allergies restent affichés. Aucun événement n’a été supprimé ; réessayer ne recharge que la chronologie.
      </Notice>
    )}
    {phase === 'limited' && (
      <Notice kind="attention" alert title="Trop de requêtes"
        actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => setAttempt((n) => n + 1)}>Réessayer</button>}>
        Patientez {wait > 0 ? `${wait} secondes` : 'un instant'} avant de recharger la chronologie.
      </Notice>
    )}
    <div className="etaye-cols etaye-cols--2">
      <section aria-labelledby="h-chrono" className="etaye-pile" aria-busy={phase === 'loading' ? true : undefined}>
        <h2 className="ui-section" id="h-chrono">Événements du dossier</h2>
        {emptyRecord && !past && (
          <Notice kind="neutre" title="Historique peu renseigné">
            Aucun événement n’est enregistré pour ce dossier. Cela ne signifie pas qu’il n’y a rien à signaler.
          </Notice>
        )}
        {emptyRecord && past && (
          <Notice kind="info" title="Rien n’était enregistré à cette date">
            Cela ne signifie pas que le dossier était normal : aucune information n’avait encore été consignée.
          </Notice>
        )}
        {answered && !emptyRecord && phase !== 'failed' && phase !== 'limited' && (
          <Filters kind={kind} setKind={setKind} from={from} setFrom={setFrom} to={to} setTo={setTo} errors={errors}
            apply={apply} reset={reset} filtered={filtered} />
        )}
        {dateFiltered && phase === 'ready' && (
          <Notice kind="info" title="Les événements sans date de survenue restent affichés">
            Un filtre de dates ne masque jamais un fait : le groupe « Date inconnue » est conservé, quelles que soient les dates choisies.
          </Notice>
        )}
        {phase === 'loading' && <Skeleton label="Chargement de la chronologie…" />}
        {phase === 'failed' && (
          <div className="etaye-vide">
            <strong>Chronologie indisponible</strong>
            <span>Ceci n’est pas un dossier vide : les événements n’ont pas pu être lus.</span>
          </div>
        )}
        {emptyRecord && (
          <div className="etaye-vide">
            <strong>{past ? `Aucun événement enregistré au ${past.date} à ${past.time}` : 'Aucun événement au dossier'}</strong>
            <span>{past ? 'Revenez à la situation actuelle ou choisissez une date plus récente.'
              : 'Les sources enregistrées apparaîtront ici avec leur date de survenue et leur date d’enregistrement.'}</span>
          </div>
        )}
        {noMatch && (
          <div className="etaye-vide">
            <strong>Aucun événement ne correspond à ces filtres</strong>
            <span>Cela ne signifie pas que le dossier est vide : des événements peuvent exister en dehors de ces filtres.</span>
            <div className="etaye-ligne"><button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={reset}>Réinitialiser les filtres</button></div>
          </div>
        )}
        {phase === 'ready' && shown > 0 && <>
          {dated.length > 0 && <ol className="etaye-chrono">{dated.map(row)}</ol>}
          {undated.length > 0 && <>
            <h3 className="ui-section etaye-separation">Date inconnue</h3>
            <p className="etaye-2" style={{ fontSize: 13, margin: 0 }}>
              Ces événements sont regroupés en fin de chronologie, triés par date d’enregistrement. La date d’enregistrement ne remplace jamais la date de survenue.
            </p>
            <ol className="etaye-chrono">{undated.map(row)}</ol>
          </>}
        </>}
        {more === 'failed' && (
          <Notice kind="attention" alert title="La suite n’a pas pu être chargée"
            actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => void showMore()}>Réessayer</button>}>
            Les événements déjà affichés restent consultables.
          </Notice>
        )}
        {((phase === 'ready' && !emptyRecord) || noMatch) && (
          <div className="etaye-ligne">
            <span className="etaye-3" role="status" style={{ fontSize: 13 }}>
              {shown} événement{shown > 1 ? 's' : ''} affiché{shown > 1 ? 's' : ''}{next ? ' · d’autres événements existent' : ''}
            </span>
            {next && more !== 'failed' && (
              <button className="etaye-bouton etaye-bouton--secondaire" type="button" disabled={more === 'loading'} onClick={() => void showMore()}>
                {more === 'loading' ? 'Chargement…' : 'Afficher plus'}
              </button>
            )}
          </div>
        )}
      </section>
      <div className="etaye-pile">
        <SectionsPanel history={ctx.history} past={past !== undefined} to={historyPath(ctx.base, ctx.recordId)} go={ctx.go} />
      </div>
    </div>
  </>;
}

function Filters({ kind, setKind, from, setFrom, to, setTo, errors, apply, reset, filtered }: {
  kind: string; setKind: (value: string) => void; from: DayParts; setFrom: (value: DayParts) => void; to: DayParts;
  setTo: (value: DayParts) => void; errors: (FieldError & { field: string })[]; apply: (event: { preventDefault: () => void }) => void;
  reset: () => void; filtered: boolean;
}) {
  const dayFields = (prefix: string, label: string, value: DayParts, set: (value: DayParts) => void, field: string) => {
    const error = errors.find((candidate) => candidate.field === field);
    const bad = error ? true : undefined;
    return (
      <div role="group" aria-labelledby={`${prefix}-l`} aria-describedby={error ? `${prefix}-e` : undefined}
        className={`etaye-champ${error ? ' etaye-champ--erreur' : ''}`}>
        <span className="etaye-champ__libelle" id={`${prefix}-l`}>{label}</span>
        <div className="etaye-date">
          <div className="etaye-champ etaye-date__j">
            <label className="etaye-champ__libelle etaye-vh" htmlFor={`${prefix}-j`}>Jour</label>
            <input id={`${prefix}-j`} className="etaye-champ__saisie etaye-num" inputMode="numeric" maxLength={2} placeholder="JJ"
              aria-invalid={bad} autoComplete="off" value={value.day} onChange={(event) => set({ ...value, day: event.target.value })} />
          </div>
          <div className="etaye-champ etaye-date__m">
            <label className="etaye-champ__libelle etaye-vh" htmlFor={`${prefix}-m`}>Mois</label>
            <input id={`${prefix}-m`} className="etaye-champ__saisie etaye-num" inputMode="numeric" maxLength={2} placeholder="MM"
              aria-invalid={bad} autoComplete="off" value={value.month} onChange={(event) => set({ ...value, month: event.target.value })} />
          </div>
          <div className="etaye-champ etaye-date__a">
            <label className="etaye-champ__libelle etaye-vh" htmlFor={`${prefix}-a`}>Année</label>
            <input id={`${prefix}-a`} className="etaye-champ__saisie etaye-num" inputMode="numeric" maxLength={4} placeholder="AAAA"
              aria-invalid={bad} autoComplete="off" value={value.year} onChange={(event) => set({ ...value, year: event.target.value })} />
          </div>
        </div>
        {error && <span className="etaye-champ__aide" id={`${prefix}-e`}>{error.detail}</span>}
      </div>
    );
  };
  return (
    <form aria-label="Filtrer la chronologie" className="etaye-filtres" role="search" noValidate onSubmit={apply}>
      {errors.length > 0 && <div style={{ flexBasis: '100%' }}><ErrorSummary errors={errors} /></div>}
      <div className="etaye-champ">
        <label className="etaye-champ__libelle" htmlFor="f-type">Type d’événement</label>
        <select className="etaye-champ__saisie" id="f-type" value={kind} onChange={(event) => setKind(event.target.value)}>
          {KIND_FILTERS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </div>
      {dayFields('f-du', 'Du', from, setFrom, 'du')}
      {dayFields('f-au', 'Au', to, setTo, 'au')}
      <div className="etaye-ligne">
        <button className="etaye-bouton etaye-bouton--secondaire" type="submit">Appliquer les filtres</button>
        {filtered && <button className="etaye-bouton etaye-bouton--discret" type="button" onClick={reset}>Réinitialiser les filtres</button>}
      </div>
    </form>
  );
}

function ChronologyItem({ item, number }: { item: TimelineItem; number: number }) {
  const ctx = useRecord();
  const known = item.eventDate.precision !== 'UNKNOWN';
  const label = eventLabel(item.eventDate);
  const [day, time] = label ? label.split(' ') : [];
  const recorded = clock(item.recordedAt);
  const state = item.sourceType ? originState[item.sourceType] : undefined;
  const carriesText = item.kind === 'CONSULTATION_NOTE' || item.kind === 'PATIENT_PREPARATION';
  const source = useSource({ id: item.sourceRevisionId ?? item.id, reference: item.reference ?? item.label, number },
    ctx.practiceId, ctx.asOf, ctx.expire);
  const quoted = item.kind === 'PATIENT_PREPARATION' && item.excerpt ? `« ${item.excerpt} »` : item.excerpt;
  return (
    <li className="etaye-chrono__item">
      <div className="etaye-chrono__date">
        {known ? <>{day}{time && <span className="etaye-chrono__heure">{time}</span>}</> : 'Date inconnue'}
      </div>
      <div className="etaye-chrono__rail" />
      <div className="etaye-chrono__corps">
        <div className="etaye-chrono__type">{kindLabel(item.kind)}</div>
        <div className="etaye-chrono__titre">{item.label}</div>
        {item.corrected && (
          <div className="etaye-chrono__detail">
            <span className="etaye-etat etaye-etat--corrige">Corrigé · version {item.revision}</span>{' '}
            version précédente conservée{item.correctionReason ? ` · motif : ${item.correctionReason}` : ''}
          </div>
        )}
        {carriesText && quoted && (
          <div className="etaye-chrono__detail">
            {quoted}
            {item.kind === 'PATIENT_PREPARATION' && state && <>{' '}<span className={`etaye-etat etaye-etat--${state.tone}`}>{state.label}</span></>}
          </div>
        )}
        {!known && (
          <div className="etaye-chrono__detail">
            <span className="etaye-etat etaye-etat--manquant">Date de survenue inconnue</span>{' '}
            la date d’enregistrement ne remplace jamais la date de survenue
          </div>
        )}
        <div className="etaye-3 etaye-provenance">
          {known && <>survenu le <span className="etaye-num">{label}</span> · </>}
          enregistré le <span className="etaye-num">{recorded.date} {recorded.time}</span>
          {item.authorLabel && ` · ${item.authorLabel}`}
          {item.sourceType && ` · ${originLabel[item.sourceType]}`}
          {item.reference && <> · <span className="etaye-num">{item.reference}</span></>}
          {' '}{source.marker}
        </div>
        {source.panel}
      </div>
    </li>
  );
}

/** "Rubriques du dossier": the derived state of each section, never a silence, with a path to the detail. */
export function SectionsPanel({ history, past, to, go }: { history: Remote<HistorySummary>; past: boolean; to: string; go: (path: string) => void }) {
  return (
    <section aria-labelledby="h-rub" className="etaye-panneau" aria-busy={history.phase === 'loading' ? true : undefined}>
      <div className="etaye-panneau__tete"><h2 id="h-rub">Rubriques du dossier</h2></div>
      <div className="etaye-panneau__corps">
        {history.phase === 'loading' && <Skeleton label="Chargement des rubriques…" />}
        {history.phase !== 'loading' && history.phase !== 'ready' && <p className="etaye-2" style={{ margin: 0, fontSize: 14 }}>États non chargés.</p>}
        {history.phase === 'ready' && (
          <ul className="etaye-controle">
            {SECTION_ORDER.map((category) => {
              const section = history.data.sections.find((candidate) => candidate.category === category)
                ?? ({ category, state: 'NOT_RECORDED', statements: [], truncated: false } as HistorySection);
              return <SectionRow key={category} section={section} past={past} to={to} go={go} />;
            })}
          </ul>
        )}
      </div>
    </section>
  );
}

function SectionRow({ section, past, to, go }: { section: HistorySection; past: boolean; to: string; go: (path: string) => void }) {
  const words = sectionWords(section.category, section, past);
  const conflicting = section.state === 'CONFLICTING';
  const alert = conflicting;
  const action = section.category === 'ALLERGY' ? (conflicting ? 'Examiner' : 'Voir') : undefined;
  return (
    <li>
      <span aria-hidden="true" className={`etaye-icone etaye-icone--18 ${alert ? 'etaye-icone--alerte etaye-controle__bloque' : 'etaye-icone--info etaye-2'}`} />
      <div>
        {sectionTitle(section.category)} : {words.lead}
        <small>{words.panel}</small>
      </div>
      {action ? (
        <a className="etaye-bouton etaye-bouton--discret" href={to} onClick={(event) => { event.preventDefault(); go(to); }}>
          {action}<span className="etaye-vh"> {sectionThing(section.category)}s</span>
        </a>
      ) : <span />}
    </li>
  );
}

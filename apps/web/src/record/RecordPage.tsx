import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { accountApi } from '../api/browser';
import type { HistorySummary, Record as PatientRecord } from '../api/generated/types.gen';
import { ErrorSummary, type FieldError, FocusedHeading } from '../auth/AuthComponents';
import { ACCOUNT_PATH } from '../auth/session-work';
import { ageOn, formatDate } from '../patients/date';
import { EntryHeader, Notice, patientName, Skeleton } from '../patients/patient-ui';
import { useShell } from '../patients/PractitionerShell';
import { historyPath, patientsPath, recordPath } from '../patients/routes';
import { EMPTY_INSTANT, instantParts, type InstantParts, parseAsOf } from './asof';
import { clock, sectionWords } from './evidence';
import { readView, type RecordView, writeView } from './memory';
import { type Remote, useRemote } from './remote';
import './record.css';

export type RecordTab = 'record' | 'history';

export type RecordContext = {
  scope: string;
  practiceId: string;
  recordId: string;
  base: string;
  practiceName: string;
  patientLabel: string;
  asOf: string | undefined;
  history: Remote<HistorySummary>;
  retryHistory: () => void;
  view: RecordView;
  setView: (patch: Partial<RecordView>) => void;
  /** A panel was refused (403 or 404): the whole record leaves the screen at once. */
  refused: (status: 403 | 404) => void;
  expire: () => void;
  go: (path: string) => void;
};

const Context = createContext<RecordContext | undefined>(undefined);
export function useRecord(): RecordContext {
  const value = useContext(Context);
  if (!value) throw new Error('useRecord must be used inside RecordPage');
  return value;
}

const emptyView = (): RecordView => ({ filters: { kind: '', from: { day: '', month: '', year: '' }, to: { day: '', month: '', year: '' } } });

/**
 * The frame of one record (S-09 and S-10): the identity banner with the derived allergy state, the tabs, the control of
 * the past instant (UD-32) and the independent loading of the record and of its history. The server decides every
 * request; this component only shows what the current session was granted and never keeps clinical content.
 */
export function RecordPage({ tab, scope, practiceId, base, recordId, expire, children }: {
  tab: RecordTab; scope: string; practiceId: string; base: string; recordId: string; expire: () => void;
  children: (tab: RecordTab) => ReactNode;
}) {
  const shell = useShell();
  const [view, setViewState] = useState<RecordView>(() => readView(scope, recordId) ?? emptyView());
  const [refusal, setRefusal] = useState<403 | 404 | undefined>();
  const [record, retryRecord] = useRemote<PatientRecord>(
    (signal) => accountApi.readRecord(practiceId, recordId, signal), `${practiceId}|${recordId}`, expire);
  const [history, retryHistory] = useRemote<HistorySummary>(
    (signal) => accountApi.readHistory(practiceId, recordId, view.asOf, signal),
    `${practiceId}|${recordId}|${view.asOf ?? 'now'}`, expire);

  const setView = useCallback((patch: Partial<RecordView>) => {
    setViewState((current) => {
      const next = { ...current, ...patch };
      writeView(scope, recordId, next);
      return next;
    });
  }, [scope, recordId]);

  const patient = record.phase === 'ready' ? record.data.patient : undefined;
  const patientLabel = patient ? patientName(patient) : 'Dossier patient';
  const status = refusal ?? (record.phase === 'forbidden' || history.phase === 'forbidden' ? 403
    : record.phase === 'hidden' || history.phase === 'hidden' ? 404 : undefined);

  const context = useMemo<RecordContext>(() => ({
    scope, practiceId, recordId, base, practiceName: shell.practiceName, patientLabel, asOf: view.asOf, history, retryHistory,
    view, setView, refused: setRefusal, expire, go: shell.navigate,
  }), [scope, practiceId, recordId, base, shell.practiceName, patientLabel, view, history, retryHistory, setView, expire, shell.navigate]);

  const toList = (event: { preventDefault: () => void }) => { event.preventDefault(); shell.navigate(patientsPath(base)); };
  const listLink = (label: string) => (
    <a className="etaye-bouton etaye-bouton--secondaire" href={patientsPath(base)} onClick={toList}>{label}</a>
  );

  // The approved S-36 states (story 1.4): a refusal discloses nothing about the record and offers one way back.
  const backToSpace = (
    <button type="button" className="etaye-bouton etaye-bouton--principal"
      onClick={() => shell.navigate(ACCOUNT_PATH)}>Revenir à mon espace</button>
  );
  if (status === 403) return (
    <div className="etaye-etat-page" data-record-state="restricted">
      <span className="etaye-etat-page__code">Accès non autorisé · 403</span>
      <FocusedHeading>Cet espace est réservé aux soins</FocusedHeading>
      <p>Votre accès actuel au {shell.practiceName} ne donne pas accès aux dossiers patients.</p>
      <div className="etaye-ligne">{backToSpace}</div>
    </div>
  );
  if (status === 404) return (
    <div className="etaye-etat-page" data-record-state="hidden">
      <span className="etaye-etat-page__code">Élément introuvable · 404</span>
      <FocusedHeading>Élément introuvable</FocusedHeading>
      <p>Vérifiez le lien ou revenez à votre espace.</p>
      <div className="etaye-ligne">{backToSpace}</div>
    </div>
  );
  if (record.phase === 'failed' || record.phase === 'limited') return <>
    <EntryHeader title="Dossier patient" meta={shell.practiceName} crumb="Dossier patient" onCrumb={() => shell.navigate(patientsPath(base))} />
    <Notice kind="attention" alert title={record.phase === 'limited' ? 'Trop de requêtes' : 'Le dossier n’a pas pu être chargé'}
      actions={<>
        <button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={retryRecord}>Réessayer</button>
        {listLink('Retour aux patients')}
      </>}>
      {record.phase === 'limited'
        ? `Patientez ${record.wait > 0 ? `${record.wait} secondes` : 'un instant'} avant de réessayer.`
        : 'Aucune information du dossier n’est affichée et rien n’a été supprimé. Réessayez ; si le problème continue, prévenez l’administration du cabinet.'}
    </Notice>
  </>;

  return (
    <Context.Provider value={context}>
      {patient && shell.bannerSlot && createPortal(
        <Banner patient={patient} history={history} past={view.asOf !== undefined} />, shell.bannerSlot)}
      <Frame key={tab} tab={tab} patientLabel={patientLabel} ready={record.phase === 'ready'}>
        {record.phase === 'ready' ? children(tab) : <Skeleton label="Chargement du dossier…" />}
      </Frame>
    </Context.Provider>
  );
}

function Banner({ patient, history, past }: { patient: PatientRecord['patient']; history: Remote<HistorySummary>; past: boolean }) {
  const years = ageOn(patient.dateOfBirth);
  const allergy = history.phase === 'ready' ? history.data.sections.find((section) => section.category === 'ALLERGY') : undefined;
  const words = allergy ? sectionWords('ALLERGY', allergy, past) : undefined;
  const stated = allergy?.state === 'PRESENT' || allergy?.state === 'CONFLICTING';
  const variant = allergy?.state === 'PRESENT' ? '' : allergy?.state === 'CONFLICTING' ? ' etaye-allergies--attention' : ' etaye-allergies--neutre';
  return (
    <header aria-label="Patient affiché" className="etaye-banniere">
      <div>
        <div className="etaye-banniere__nom">{patientName(patient)}</div>
        <div className="etaye-banniere__traits">
          <span>Naissance <span className="etaye-num">{formatDate(patient.dateOfBirth)}</span>{years !== undefined && <> · <span className="etaye-num">{years}</span> {years <= 1 ? 'an' : 'ans'}</>}</span>
        </div>
      </div>
      <div className={`etaye-allergies${variant}`} role="note">
        <div className="etaye-allergies__titre">
          {stated && <span aria-hidden="true" className="etaye-icone etaye-icone--alerte" />}Allergies
        </div>
        <div className="etaye-allergies__liste">
          {history.phase === 'loading' && <>
            <span className="etaye-squelette" aria-hidden="true"><span /><span /></span>
            <span className="etaye-vh" role="status">Chargement des allergies…</span>
          </>}
          {(history.phase === 'failed' || history.phase === 'limited') && <>Allergies non chargées<br />Ne pas conclure à l’absence</>}
          {words && (stated ? words.headline
            : <span className={`etaye-etat etaye-etat--${words.tone === 'plain' ? 'observe' : words.tone}`}>{words.headline}</span>)}
        </div>
      </div>
    </header>
  );
}

function Frame({ tab, patientLabel, ready, children }: { tab: RecordTab; patientLabel: string; ready: boolean; children: ReactNode }) {
  const ctx = useRecord();
  const [open, setOpen] = useState(false);
  const toggle = useRef<HTMLButtonElement>(null);
  const banner = useRef<HTMLDivElement>(null);
  const focusBanner = useRef(false);
  const focusToggle = useRef(false);
  // One Date per applied instant: a session poll re-renders the frame and must not refill a draft of the control.
  const past = useMemo(() => (ctx.asOf ? new Date(ctx.asOf) : undefined), [ctx.asOf]);
  const { date, time } = past ? clock(past) : { date: '', time: '' };

  useEffect(() => {
    if (focusBanner.current && past) { focusBanner.current = false; banner.current?.focus(); }
    if (focusToggle.current && !past) { focusToggle.current = false; toggle.current?.focus(); }
  });

  const apply = (instant: Date) => {
    focusBanner.current = true;
    setOpen(false);
    ctx.setView({ asOf: instant.toISOString() });
  };
  const reset = () => {
    focusToggle.current = true;
    setOpen(false);
    ctx.setView({ asOf: undefined });
  };
  const tabLink = (target: RecordTab, label: string, path: string) => (
    <a aria-current={tab === target ? 'page' : undefined} href={path}
      onClick={(event) => { event.preventDefault(); ctx.go(path); }}>{label}</a>
  );
  const title = tab === 'record' ? 'Chronologie' : 'Antécédents et allergies';
  const meta = past ? `${ctx.practiceName} · vue historique au ${date} à ${time}`
    : tab === 'record' ? `${ctx.practiceName} · dates de survenue et d’enregistrement affichées séparément`
      : 'Chaque déclaration garde sa date, son auteur et son origine';

  return <>
    <EntryHeader title={title} meta={meta} crumb={patientLabel} onCrumb={() => ctx.go(patientsPath(ctx.base))}
      actions={ready ? <button ref={toggle} type="button" className="etaye-bouton etaye-bouton--secondaire" aria-expanded={open}
        aria-controls="asof-region" onClick={() => setOpen((value) => !value)}>
        {open ? 'Masquer le choix de date' : past ? 'Changer de date' : 'Voir le dossier à une date passée'}
      </button> : undefined} />
    {ready && <AsOfPanel open={open} current={past} onApply={apply} />}
    {ready && past && (
      <div ref={banner} tabIndex={-1} className="etaye-bandeau etaye-bandeau--info" role="status" style={{ outline: 'none' }}>
        <span aria-hidden="true" className="etaye-icone etaye-icone--18 etaye-icone--info" />
        <div>
          <div className="etaye-bandeau__titre">Vue historique — lecture seule</div>
          <div>
            Dossier tel qu’il était le <span className="etaye-num">{date}</span> à <span className="etaye-num">{time}</span> (heure de Paris).
            Les informations enregistrées ensuite ne sont pas affichées. L’identité affichée est l’identité actuelle.
          </div>
        </div>
        <div className="etaye-bandeau__actions">
          <button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={reset}>Revenir à la situation actuelle</button>
        </div>
      </div>
    )}
    <nav aria-label="Sections" className="etaye-onglets">
      {tabLink('record', 'Chronologie', recordPath(ctx.base, ctx.recordId))}
      {tabLink('history', 'Antécédents et allergies', historyPath(ctx.base, ctx.recordId))}
    </nav>
    {children}
  </>;
}

/** The control of the past instant: date and time in Paris, refused before any request when it is in the future. */
function AsOfPanel({ open, current, onApply }: { open: boolean; current: Date | undefined; onApply: (instant: Date) => void }) {
  const [parts, setParts] = useState<InstantParts>(() => (current ? instantParts(current) : EMPTY_INSTANT));
  const [errors, setErrors] = useState<(FieldError & { field: string })[]>([]);
  useEffect(() => { setParts(current ? instantParts(current) : EMPTY_INSTANT); }, [current]);
  const bad = errors.length > 0 ? true : undefined;
  const field = (id: string, label: string, key: keyof InstantParts, width: string, max: number, placeholder: string) => (
    <div className={`etaye-champ ${width}`}>
      <label className="etaye-champ__libelle" htmlFor={id}>{label}</label>
      <input id={id} className="etaye-champ__saisie etaye-num" inputMode="numeric" maxLength={max} placeholder={placeholder}
        aria-invalid={bad} aria-required autoComplete="off" value={parts[key]}
        onChange={(event) => setParts({ ...parts, [key]: event.target.value })} />
    </div>
  );
  const submit = (event: { preventDefault: () => void }) => {
    event.preventDefault();
    const result = parseAsOf(parts);
    if (!result.ok) {
      setErrors([{ id: 'as-j', field: 'asof', summary: result.summary, detail: result.detail }]);
      return;
    }
    setErrors([]);
    onApply(result.value);
  };
  return (
    <section aria-label="Choix de la date de la vue historique" className="etaye-panneau" id="asof-region" hidden={!open}>
      <form className="etaye-panneau__corps etaye-pile" noValidate onSubmit={submit}>
        {errors.length > 0 && <ErrorSummary errors={errors} />}
        <div role="group" aria-labelledby="as-l" aria-describedby="as-aide" className={`etaye-champ${errors.length ? ' etaye-champ--erreur' : ''}`}>
          <span className="etaye-champ__libelle" id="as-l">Voir le dossier tel qu’il était le</span>
          <div className="etaye-date">
            {field('as-j', 'Jour', 'day', 'etaye-date__j', 2, 'JJ')}
            {field('as-m', 'Mois', 'month', 'etaye-date__m', 2, 'MM')}
            {field('as-a', 'Année', 'year', 'etaye-date__a', 4, 'AAAA')}
            {field('as-h', 'Heure', 'hour', 'etaye-date__h', 2, 'HH')}
            {field('as-mi', 'Minute', 'minute', 'etaye-date__mi', 2, 'MM')}
            <button className="etaye-bouton etaye-bouton--secondaire" type="submit">Afficher cette date</button>
          </div>
          <span className="etaye-champ__aide" id="as-aide">
            {errors[0]?.detail ?? 'Heure de Paris. Lecture seule : le dossier s’affiche tel qu’il était à cet instant. Une date future est refusée.'}
          </span>
        </div>
      </form>
    </section>
  );
}

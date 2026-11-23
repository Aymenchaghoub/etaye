import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { Patient } from '../api/generated/types.gen';
import { ACCOUNT_PATH } from '../auth/session-work';
import { ageLabel, formatDate } from './date';
import { clearDraft, readSearch, takeConfirmation, writeSearch } from './memory';
import { clockTime, EntryHeader, Notice, patientName, Skeleton } from './patient-ui';
import { useShell } from './PractitionerShell';
import { editPatientPath, newPatientPath } from './routes';

type Phase = 'loading' | 'ready' | 'refreshing' | 'failed' | 'forbidden' | 'hidden' | 'limited';
const PAGE = 25;

export function PatientsPage({ scope, practiceId, base, expire }: {
  scope: string; practiceId: string; base: string; expire: () => void;
}) {
  const shell = useShell();
  const [initial] = useState(() => readSearch(scope));
  const [confirmation] = useState(() => takeConfirmation(scope));
  // Only the query is remembered: rows are shown after a successful response of this mount, never from memory.
  const [q, setQ] = useState(confirmation ? '' : initial?.q ?? '');
  const [items, setItems] = useState<Patient[]>([]);
  const [next, setNext] = useState<string | null>(null);
  const [loadedAt, setLoadedAt] = useState<number | undefined>();
  const [staleAt, setStaleAt] = useState<number | undefined>();
  const [phase, setPhase] = useState<Phase>('loading');
  const [wait, setWait] = useState(0);
  const [more, setMore] = useState<'idle' | 'loading' | 'failed'>('idle');
  // The query of the last request sent, so a retry repeats what failed and not an older successful search.
  const attempted = useRef(confirmation ? '' : initial?.q ?? '');
  // The query of the rows on screen.
  const submitted = useRef(attempted.current);
  const itemsRef = useRef(items);
  const controller = useRef<AbortController | null>(null);
  const [queried, setQueried] = useState(false);
  itemsRef.current = items;

  const expireRef = useRef(expire);
  expireRef.current = expire;
  const fail = useCallback((status: number, retryAfter: number) => {
    if (status === 401) { expireRef.current(); return; }
    if (status === 403 || status === 404) {
      // A refusal removes every row at once: nothing validated earlier stays visible.
      setItems([]);
      setNext(null);
      setPhase(status === 403 ? 'forbidden' : 'hidden');
    } else if (status === 429) { setWait(retryAfter); setPhase('limited'); }
    else setPhase('failed');
  }, []);

  const load = useCallback(async (query: string) => {
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    const trimmed = query.trim();
    attempted.current = trimmed;
    writeSearch(scope, { q: trimmed });
    setMore('idle');
    setPhase(itemsRef.current.length ? 'refreshing' : 'loading');
    const result = await accountApi.searchPatients(practiceId, { q: trimmed || undefined, limit: PAGE }, request.signal);
    if (request.signal.aborted) return;
    if (!result.ok) { fail(result.status, result.retryAfter); return; }
    const page = result.data;
    submitted.current = trimmed;
    setQueried(trimmed !== '');
    setItems(page.items);
    setNext(page.nextCursor);
    setLoadedAt(Date.now());
    setStaleAt(undefined);
    setPhase('ready');
  }, [fail, practiceId, scope]);

  useEffect(() => {
    void load(attempted.current);
    return () => controller.current?.abort();
  }, [load]);

  async function showMore() {
    // The next page continues the rows on screen: it never starts while a refresh is pending (that would abort it).
    if (!next || more === 'loading' || phase !== 'ready') return;
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    setMore('loading');
    const result = await accountApi.searchPatients(practiceId,
      { q: submitted.current || undefined, cursor: next, limit: PAGE }, request.signal);
    if (request.signal.aborted) return;
    if (!result.ok) {
      if (result.status === 401 || result.status === 403 || result.status === 404) fail(result.status, result.retryAfter);
      else setMore('failed');
      return;
    }
    // A corrected name can move a loaded patient past the cursor: a page that repeats a row shows the list changed.
    const known = new Set(itemsRef.current.map((patient) => patient.id));
    const fresh = result.data.items.filter((patient) => !known.has(patient.id));
    if (fresh.length !== result.data.items.length) setStaleAt(Date.now());
    setItems([...itemsRef.current, ...fresh]);
    setNext(result.data.nextCursor);
    setMore('idle');
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void load(q);
  }

  function clear() {
    setQ('');
    void load('');
  }

  const retry = () => void load(attempted.current);
  const practice = shell.practiceName;
  const newPath = newPatientPath(base);
  const create = (
    <a className="etaye-bouton etaye-bouton--principal" href={newPath}
      onClick={(event) => { event.preventDefault(); clearDraft(scope, 'new'); shell.navigate(newPath); }}>
      <span aria-hidden="true" className="etaye-icone etaye-icone--18 etaye-icone--ajouter" />Créer un dossier patient
    </a>
  );
  const header = (
    <EntryHeader title="Patients" meta={`${practice} · les dossiers des autres cabinets n’apparaissent jamais ici`}
      actions={phase === 'forbidden' || phase === 'hidden' ? undefined : create} />
  );

  if (phase === 'forbidden') return <>
    <EntryHeader title="Patients" meta={practice} />
    <Notice kind="info" alert title="Accès aux patients non autorisé"
      actions={<a className="etaye-bouton etaye-bouton--secondaire" href={ACCOUNT_PATH}
        onClick={(event) => { event.preventDefault(); shell.navigate(ACCOUNT_PATH); }}>Revenir à mon espace</a>}>
      Votre accès actuel ne permet pas de consulter les patients de ce cabinet.
    </Notice>
  </>;
  if (phase === 'hidden') return <>
    <EntryHeader title="Élément introuvable" meta={practice} />
    <Notice kind="info" alert title="Élément introuvable"
      actions={<a className="etaye-bouton etaye-bouton--secondaire" href={ACCOUNT_PATH}
        onClick={(event) => { event.preventDefault(); shell.navigate(ACCOUNT_PATH); }}>Revenir à mon espace</a>}>
      Vérifiez le lien ou revenez à votre espace.
    </Notice>
  </>;

  const trimmed = submitted.current;
  const saved = confirmation?.patient;
  // A patient that the loaded page does not contain (it sorts later) is pinned first, from the save response.
  const pinned = saved && !items.some((patient) => patient.id === saved.id) ? saved : undefined;
  const entry = (patient: Patient, marked: boolean) => {
    const name = patientName(patient);
    const edit = editPatientPath(base, patient.recordId);
    const age = ageLabel(patient.dateOfBirth);
    return (
      <li key={patient.id} className={marked ? 'etaye-ligne--nouvelle' : undefined}>
        <span aria-hidden="true" className="etaye-lignes__heure">
          <span aria-hidden="true" className="etaye-icone etaye-icone--18 etaye-icone--compte etaye-2" />
        </span>
        <div>
          <div className="etaye-lignes__titre">{name}</div>
          <div className="etaye-lignes__detail">
            Naissance <span className="etaye-num">{formatDate(patient.dateOfBirth)}</span>{age && ` · ${age}`}
          </div>
        </div>
        <div className="etaye-lignes__actions">
          {marked && <span className="etaye-statut etaye-statut--valide">
            {confirmation?.kind === 'created' ? 'Dossier créé' : 'Identité enregistrée'}
          </span>}
          <a className="etaye-bouton etaye-bouton--secondaire" href={edit}
            onClick={(event) => { event.preventDefault(); shell.navigate(edit); }}>
            Modifier l’identité<span className="etaye-vh"> de {name}</span>
          </a>
        </div>
      </li>
    );
  };
  const rows = [
    ...(pinned ? [entry(pinned, true)] : []),
    ...items.map((patient) => entry(patient, saved?.id === patient.id)),
  ];
  const shown = rows.length;
  const countText = `${shown} patient${shown > 1 ? 's' : ''} affiché${shown > 1 ? 's' : ''}`
    + (next ? ' · d’autres patients existent' : '');
  const showList = shown > 0 && phase !== 'loading';
  const refreshFailed = phase === 'failed' && items.length > 0;
  const hardFailed = phase === 'failed' && items.length === 0;

  return <>
    {header}
    {confirmation && <Notice kind="info" icon="valide" title={confirmation.kind === 'created' ? 'Dossier créé' : 'Identité enregistrée'}>
      {confirmation.kind === 'created'
        ? `${patientName(confirmation.patient)} existe dans ce cabinet uniquement. Aucun espace patient n’a été créé.`
        : `L’identité de ${patientName(confirmation.patient)} a été enregistrée.`}
    </Notice>}
    <form aria-label="Rechercher un patient" className="etaye-filtres" role="search" noValidate onSubmit={submit}>
      <div style={{ flex: 1, minWidth: 240 }}>
        <div className="etaye-champ">
          <label className="etaye-champ__libelle" htmlFor="q">Nom, prénom ou date de naissance</label>
          <div className="etaye-recherche">
            <input id="q" type="search" className="etaye-champ__saisie" value={q} maxLength={100}
              aria-describedby="q-a" autoComplete="off" onChange={(event) => setQ(event.target.value)} />
            <button className="etaye-bouton etaye-bouton--secondaire" type="submit">Rechercher</button>
            {(q !== '' || queried) && <button className="etaye-bouton etaye-bouton--discret" type="button" onClick={clear}>
              Effacer la recherche
            </button>}
          </div>
          <span className="etaye-champ__aide" id="q-a">Exemple : Martin ou 18/04/1978</span>
        </div>
      </div>
    </form>

    {staleAt && phase === 'ready' && <Notice kind="info" title="Résultats à actualiser"
      actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => void load(submitted.current)}>Actualiser</button>}>
      Un dossier a été modifié depuis votre recherche ({clockTime(staleAt)}).
    </Notice>}
    {phase === 'limited' && <Notice kind="attention" alert title="Trop de recherches"
      actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={retry}>Réessayer</button>}>
      Patientez {wait > 0 ? `${wait} secondes` : 'un instant'} avant de relancer la recherche.
    </Notice>}
    {refreshFailed && <Notice kind="attention" alert title="La liste n’a pas pu être chargée entièrement"
      actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={retry}>Réessayer</button>}>
      Les résultats affichés datent de {loadedAt ? clockTime(loadedAt) : 'tout à l’heure'}. Certains dossiers peuvent manquer.
    </Notice>}
    {hardFailed && <Notice kind="attention" alert title="La liste n’a pas pu être chargée"
      actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={retry}>Réessayer</button>}>
      Le service est momentanément indisponible. Aucune information n’est perdue.
    </Notice>}
    {more === 'failed' && <Notice kind="attention" alert title="La suite n’a pas pu être chargée"
      actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => void showMore()}>Réessayer</button>}>
      Les patients déjà affichés restent consultables.
    </Notice>}

    {phase === 'loading' && <Skeleton label="Chargement des patients…" />}
    {phase === 'refreshing' && <p className="etaye-3" role="status" style={{ fontSize: 13, margin: 0 }}>Actualisation en cours…</p>}
    {showList && <>
      <div className="etaye-ligne" style={{ justifyContent: 'space-between' }}>
        <span className="etaye-3" role="status" style={{ fontSize: 13 }}>{countText}</span>
      </div>
      <ul className="etaye-lignes">{rows}</ul>
      {next && more !== 'failed' && <div className="etaye-ligne">
        <button className="etaye-bouton etaye-bouton--secondaire" type="button" disabled={more === 'loading' || phase !== 'ready'}
          onClick={() => void showMore()}>{more === 'loading' ? 'Chargement…' : 'Afficher plus'}</button>
      </div>}
    </>}
    {phase === 'ready' && shown === 0 && (trimmed === '' ? (
      <div className="etaye-vide">
        <strong>Aucun patient dans ce cabinet</strong>
        <span>Les dossiers créés ici n’apparaissent que dans ce cabinet.</span>
        {create}
      </div>
    ) : (
      <div className="etaye-vide">
        <strong>Aucun patient trouvé pour « {trimmed} » au {practice}</strong>
        <span>La recherche porte uniquement sur les dossiers de ce cabinet. Vérifiez l’orthographe ou créez le dossier.</span>
        <div className="etaye-ligne">
          <button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={clear}>Effacer la recherche</button>
          {create}
        </div>
      </div>
    ))}
  </>;
}

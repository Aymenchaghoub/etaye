import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { Patient, PatientRegistration, SchedulingPatient } from '../api/generated/types.gen';
import { ErrorSummary, type FieldError } from '../auth/AuthComponents';
import { ageLabel, formatDate, isoToParts, parseBirthDate } from './date';
import { AttemptKey } from './attempt-key';
import { hasContactEmailShape } from './contact-email';
import { clearDraft, type DraftValues, readDraft, rememberConfirmation, takeConfirmation, writeDraft } from './memory';
import { clockTime, EntryHeader, Notice, patientName, Skeleton } from './patient-ui';
import { useShell } from './PractitionerShell';
import { patientsPath, recordPath } from './routes';

type Phase = 'idle' | 'saving' | 'verifying' | 'confirmed' | 'failed' | 'unconfirmed' | 'limited' | 'restricted' | 'gone';
type Duplicate = { candidates: SchedulingPatient[]; changed: boolean };
type ServerField = 'lastName' | 'firstName' | 'dateOfBirth' | 'contactEmail' | 'phone';

const EMPTY: DraftValues = { lastName: '', firstName: '', day: '', month: '', year: '', phone: '', email: '' };
const CONFIRMED_DISPLAY_MS = 600;
const FIELD_IDS: Record<ServerField, string> = {
  lastName: 'i-nom', firstName: 'i-pre', dateOfBirth: 'dn-j', contactEmail: 'i-mail', phone: 'i-tel',
};
const FIELD_LABELS: Record<ServerField, string> = {
  lastName: 'Nom de naissance', firstName: 'Prénom', dateOfBirth: 'Date de naissance', contactEmail: 'Adresse e-mail',
  phone: 'Téléphone',
};

function fromPatient(patient: Patient): DraftValues {
  const { day, month, year } = isoToParts(patient.dateOfBirth);
  return { lastName: patient.lastName, firstName: patient.firstName, day, month, year,
    phone: patient.phone ?? '', email: patient.contactEmail ?? '' };
}

const same = (a: DraftValues, b: DraftValues) => (Object.keys(a) as (keyof DraftValues)[]).every((key) => a[key].trim() === b[key].trim());

type Identity = { firstName: string; lastName: string; dateOfBirth: string; contactEmail: string | null; phone: string | null };
/** Whether a saved patient carries exactly the identity that was sent, so a lost response is never read as a stranger's edit. */
const carries = (saved: Patient, sent: Identity) => saved.firstName === sent.firstName && saved.lastName === sent.lastName
  && saved.dateOfBirth === sent.dateOfBirth && (saved.contactEmail ?? '') === (sent.contactEmail ?? '')
  && (saved.phone ?? '') === (sent.phone ?? '');

function TextField({ id, label, value, onChange, required = false, kind = 'text', error, help, readOnly, maxLength, autoComplete }: {
  id: string; label: string; value: string; onChange: (value: string) => void; required?: boolean;
  kind?: 'text' | 'tel' | 'email'; error?: string; help?: string; readOnly: boolean; maxLength?: number; autoComplete?: string;
}) {
  const describedBy = error || help ? `${id}-a` : undefined;
  return (
    <div className={`etaye-champ${error ? ' etaye-champ--erreur' : ''}`}>
      <label className="etaye-champ__libelle" htmlFor={id}>
        {label} {required ? <span aria-hidden="true" className="etaye-req">*</span> : <span className="etaye-opt">(facultatif)</span>}
      </label>
      <input id={id} type={kind} className="etaye-champ__saisie" value={value} readOnly={readOnly} maxLength={maxLength}
        autoComplete={autoComplete} aria-required={required || undefined} aria-invalid={error ? true : undefined}
        aria-describedby={describedBy} onChange={(event) => onChange(event.target.value)} />
      {(error || help) && <span className="etaye-champ__aide" id={`${id}-a`}>{error ?? help}</span>}
    </div>
  );
}

function BirthDate({ values, onChange, error, readOnly }: {
  values: DraftValues; onChange: (patch: Partial<DraftValues>) => void; error?: string; readOnly: boolean;
}) {
  const bad = error ? true : undefined;
  return (
    <div className={`etaye-champ${error ? ' etaye-champ--erreur' : ''}`} role="group" aria-labelledby="dn-l" aria-describedby="dn-a">
      <span className="etaye-champ__libelle" id="dn-l">Date de naissance <span aria-hidden="true" className="etaye-req">*</span></span>
      <div className="etaye-date">
        <div className="etaye-champ etaye-date__j">
          <label className="etaye-champ__libelle" htmlFor="dn-j">Jour</label>
          <input id="dn-j" className="etaye-champ__saisie etaye-num" inputMode="numeric" maxLength={2} placeholder="JJ" aria-required
            aria-invalid={bad} readOnly={readOnly} autoComplete="off" value={values.day}
            onChange={(event) => onChange({ day: event.target.value })} />
        </div>
        <div className="etaye-champ etaye-date__m">
          <label className="etaye-champ__libelle" htmlFor="dn-m">Mois</label>
          <input id="dn-m" className="etaye-champ__saisie etaye-num" inputMode="numeric" maxLength={2} placeholder="MM" aria-required
            aria-invalid={bad} readOnly={readOnly} autoComplete="off" value={values.month}
            onChange={(event) => onChange({ month: event.target.value })} />
        </div>
        <div className="etaye-champ etaye-date__a">
          <label className="etaye-champ__libelle" htmlFor="dn-a2">Année</label>
          <input id="dn-a2" className="etaye-champ__saisie etaye-num" inputMode="numeric" maxLength={4} placeholder="AAAA" aria-required
            aria-invalid={bad} readOnly={readOnly} autoComplete="off" value={values.year}
            onChange={(event) => onChange({ year: event.target.value })} />
        </div>
      </div>
      <span className="etaye-champ__aide" id="dn-a">{error ?? 'Format JJ/MM/AAAA.'}</span>
    </div>
  );
}

function candidateLine(candidate: SchedulingPatient) {
  const contact = candidate.contactEmail || candidate.phone ? 'renseignées' : 'non renseignées';
  return <>naissance <span className="etaye-num">{formatDate(candidate.dateOfBirth)}</span> · coordonnées : {contact}</>;
}

export function PatientForm({ mode, scope, practiceId, base, recordId, expire }: {
  mode: 'new' | 'edit'; scope: string; practiceId: string; base: string; recordId?: string; expire: () => void;
}) {
  const shell = useShell();
  const [loaded, setLoaded] = useState<'new' | 'loading' | 'failed' | 'restricted' | 'gone' | Patient>(mode === 'new' ? 'new' : 'loading');
  const [attempt, setAttempt] = useState(0);
  const expireRef = useRef(expire);
  expireRef.current = expire;

  useEffect(() => {
    if (mode !== 'edit' || !recordId) return;
    const request = new AbortController();
    setLoaded('loading');
    void accountApi.readRecord(practiceId, recordId, request.signal).then((result) => {
      if (request.signal.aborted) return;
      if (result.ok) setLoaded(result.data.patient);
      else if (result.status === 401) expireRef.current();
      else if (result.status === 403) setLoaded('restricted');
      else if (result.status === 404) setLoaded('gone');
      else setLoaded('failed');
    });
    return () => request.abort();
  }, [attempt, mode, practiceId, recordId]);

  const practice = shell.practiceName;
  const back = () => shell.navigate(patientsPath(base));
  const title = mode === 'new' ? 'Nouveau dossier patient' : 'Modifier l’identité';
  const backLink = (
    <a className="etaye-bouton etaye-bouton--secondaire" href={patientsPath(base)}
      onClick={(event) => { event.preventDefault(); back(); }}>Retour aux patients</a>
  );

  if (loaded === 'loading') return <>
    <EntryHeader title={title} meta={practice} crumb={title} onCrumb={back} />
    <Skeleton label="Chargement du dossier…" />
  </>;
  if (loaded === 'failed') return <>
    <EntryHeader title={title} meta={practice} crumb={title} onCrumb={back} />
    <Notice kind="attention" alert title="Le dossier n’a pas pu être chargé"
      actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => setAttempt((n) => n + 1)}>Réessayer</button>}>
      Le service est momentanément indisponible. Aucune modification n’a été enregistrée.
    </Notice>
  </>;
  if (loaded === 'restricted') return <>
    <EntryHeader title={title} meta={practice} crumb={title} onCrumb={back} />
    <Notice kind="info" alert title="Modification non autorisée" actions={backLink}>
      Votre accès actuel ne permet pas de créer ou de corriger un dossier patient. Demandez à un membre habilité du cabinet.
    </Notice>
  </>;
  if (loaded === 'gone') return <>
    <EntryHeader title="Élément introuvable" meta={practice} />
    <Notice kind="info" alert title="Élément introuvable" actions={backLink}>
      Vérifiez le lien ou revenez à la liste des patients.
    </Notice>
  </>;
  if (loaded === 'new' && mode === 'edit') return null;
  return <IdentityForm key={mode === 'edit' ? (loaded as Patient).id : 'new'} mode={mode} scope={scope} practiceId={practiceId}
    base={base} patient={mode === 'edit' ? loaded as Patient : undefined} expire={expire} />;
}

function IdentityForm({ mode, scope, practiceId, base, patient, expire }: {
  mode: 'new' | 'edit'; scope: string; practiceId: string; base: string; patient?: Patient; expire: () => void;
}) {
  const shell = useShell();
  const formId = patient ? patient.recordId : 'new';
  const [restored] = useState(() => {
    const draft = readDraft(scope, formId);
    // A draft that already equals the saved identity has nothing left to protect.
    if (!draft || (patient && same(draft.values, fromPatient(patient)))) return { draft: undefined, stale: false };
    // A correction based on an older version is compared with the saved one, never silently re-based on it.
    return { draft, stale: Boolean(patient && draft.baseVersion !== undefined && draft.baseVersion !== patient.version) };
  });
  const [baseline, setBaseline] = useState<DraftValues>(() => (patient ? fromPatient(patient) : EMPTY));
  const [values, setValues] = useState<DraftValues>(() => restored.draft?.values ?? (patient ? fromPatient(patient) : EMPTY));
  const [version, setVersion] = useState(patient?.version ?? 0);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [phase, setPhase] = useState<Phase>('idle');
  const [duplicate, setDuplicate] = useState<Duplicate>();
  const [conflict, setConflict] = useState<{ current?: Patient } | undefined>(() => (restored.stale ? { current: patient } : undefined));
  const [wait, setWait] = useState(0);
  // The attempt and the confirmed candidates survive an unmount with the draft, so an unresolved send is replayed with its key.
  const keys = useRef(new AttemptKey(undefined, restored.draft?.attempt));
  const confirmed = useRef<string[] | undefined>(restored.draft?.confirmed);
  const request = useRef<AbortController | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const expireRef = useRef(expire);
  expireRef.current = expire;
  // Once the server has saved the work nothing is unsaved: no draft is written back and the leave guard stays off.
  const [done, setDone] = useState(false);
  const dirty = !done && !same(values, baseline);
  const crumb = mode === 'new' ? 'Nouveau dossier patient' : 'Modifier l’identité';
  const practice = shell.practiceName;
  const busy = phase === 'saving' || phase === 'verifying' || phase === 'confirmed';

  useEffect(() => {
    if (dirty) {
      writeDraft(scope, formId, {
        values, baseVersion: patient ? version : undefined, confirmed: confirmed.current, attempt: keys.current.snapshot(),
      });
    } else clearDraft(scope, formId);
  }, [dirty, formId, patient, scope, values, version]);

  useEffect(() => {
    shell.setDirty(dirty ? {
      label: crumb, crumb, onDiscard: () => clearDraft(scope, formId),
    } : null);
    return () => shell.setDirty(null);
  }, [crumb, dirty, formId, scope, shell]);

  useEffect(() => () => {
    request.current?.abort();
    window.clearTimeout(timer.current);
  }, []);

  const change = useCallback((patch: Partial<DraftValues>) => {
    setValues((current) => ({ ...current, ...patch }));
    // The confirmation names candidates for one body: a changed body is a new registration to check again.
    confirmed.current = undefined;
    // Any edit invalidates the duplicate warning and the previous field errors; the server re-evaluates on submit.
    setDuplicate(undefined);
    setErrors([]);
    if (phase === 'failed' || phase === 'unconfirmed') setPhase('idle');
  }, [phase]);

  const fieldError = useMemo(() => {
    const map = new Map<string, string>();
    for (const error of errors) map.set(error.id, error.detail);
    return map;
  }, [errors]);

  function finish(kind: 'created' | 'updated', saved: Patient) {
    request.current = null;
    setPhase('confirmed');
    // Success is shown only after the server commit; the guard must not intercept the planned return to the list.
    setDone(true);
    shell.finishWork();
    clearDraft(scope, formId);
    rememberConfirmation(scope, { kind, patient: saved });
    timer.current = window.setTimeout(() => {
      // A navigation asked for during the save is completed now; the list's confirmation then no longer applies.
      if (!shell.completeSave(patientsPath(base))) takeConfirmation(scope);
    }, CONFIRMED_DISPLAY_MS);
  }

  function serverErrors(problem: { fieldErrors?: { field: string; code: string }[] } | undefined): FieldError[] {
    const out: FieldError[] = [];
    for (const item of problem?.fieldErrors ?? []) {
      const field = item.field as ServerField;
      if (!(field in FIELD_IDS)) continue;
      const future = field === 'dateOfBirth' && (item.code === 'PastOrPresent' || item.code === 'Past');
      out.push({
        id: FIELD_IDS[field],
        summary: `${FIELD_LABELS[field]} : ${future ? 'dans le futur' : 'à vérifier'}`,
        detail: future ? 'Erreur : la date de naissance ne peut pas être dans le futur.' : 'Erreur : cette valeur est refusée. Vérifiez le champ.',
      });
    }
    return out;
  }

  async function submit(named?: string[]) {
    if (busy) return;
    const problems: FieldError[] = [];
    const lastName = values.lastName.trim();
    const firstName = values.firstName.trim();
    if (!lastName) problems.push({ id: 'i-nom', summary: 'Nom de naissance : obligatoire', detail: 'Erreur : indiquez le nom de naissance.' });
    if (!firstName) problems.push({ id: 'i-pre', summary: 'Prénom : obligatoire', detail: 'Erreur : indiquez le prénom.' });
    const date = parseBirthDate(values);
    if (!date.ok) problems.push({ id: 'dn-j', summary: date.problem.summary, detail: date.problem.detail });
    const email = values.email.trim();
    if (email && !hasContactEmailShape(email)) {
      problems.push({ id: 'i-mail', summary: 'Adresse e-mail : format incorrect', detail: 'Erreur : vérifiez le format de l’adresse e-mail.' });
    }
    if (values.phone.trim().length > 40) {
      problems.push({ id: 'i-tel', summary: 'Téléphone : trop long', detail: 'Erreur : 40 caractères au plus.' });
    }
    if (problems.length || !date.ok) {
      setErrors(problems);
      return;
    }
    setErrors([]);
    setDuplicate(undefined);
    const body = { firstName, lastName, dateOfBirth: date.iso, contactEmail: email || null, phone: values.phone.trim() || null };
    const controller = new AbortController();
    request.current = controller;
    setPhase('saving');
    // A retry of the same body repeats its confirmation, so the replayed key still names the same registration.
    if (named) confirmed.current = named;
    const sent = confirmed.current;
    if (mode === 'new') await create({ ...body, ...(sent ? { confirmedDistinctRecordIds: sent } : {}) }, controller, Boolean(sent));
    else await edit(body, controller);
  }

  async function create(payload: PatientRegistration, controller: AbortController, confirmation: boolean) {
    const key = keys.current.keyFor(payload);
    writeDraft(scope, formId, { values, confirmed: confirmed.current, attempt: keys.current.snapshot() });
    let result = await accountApi.createPatient(practiceId, payload, key, controller.signal);
    if (controller.signal.aborted) return;
    if (!result.ok && result.status === 0) {
      // The response may have been lost after the commit: replay the same key to learn the outcome, never a new attempt.
      setPhase('verifying');
      result = await accountApi.createPatient(practiceId, payload, key, controller.signal);
      if (controller.signal.aborted) return;
      if (!result.ok && result.status === 0) { setPhase('unconfirmed'); return; }
    }
    if (result.ok) { finish('created', result.data); return; }
    if (result.status === 409 && result.code === 'DUPLICATE_PATIENT' && result.problem?.candidates?.length) {
      setDuplicate({ candidates: result.problem.candidates, changed: confirmation });
      setPhase('idle');
      return;
    }
    handleFailure(result.status, result.retryAfter, result.problem);
  }

  async function edit(body: Identity, controller: AbortController) {
    if (!patient) return;
    const result = await accountApi.editPatient(practiceId, patient.id, version, body, controller.signal);
    if (controller.signal.aborted) return;
    if (!result.ok && result.status === 0) {
      setPhase('verifying');
      const read = await accountApi.readRecord(practiceId, patient.recordId, controller.signal);
      if (controller.signal.aborted) return;
      if (!read.ok) {
        if (read.status === 401) expireRef.current();
        else setPhase('unconfirmed');
        return;
      }
      const now = read.data.patient;
      // A newer version is proof of our save only when it carries exactly what we sent; otherwise it is someone else's.
      if (now.version === version) { request.current = null; setPhase('failed'); return; }
      if (carries(now, body)) { finish('updated', now); return; }
      showConflict(now);
      return;
    }
    if (result.ok) { setVersion(result.data.version); finish('updated', result.data); return; }
    if (result.status === 412) { await openConflict(controller); return; }
    handleFailure(result.status, result.retryAfter, result.problem);
  }

  function showConflict(current: Patient) {
    request.current = null;
    setPhase('idle');
    // The next save carries the version the person has now seen, so the comparison is deliberate (BR-015).
    setVersion(current.version);
    setConflict({ current });
  }

  async function openConflict(controller: AbortController) {
    const read = await accountApi.readRecord(practiceId, patient!.recordId, controller.signal);
    if (controller.signal.aborted) return;
    if (read.ok) { showConflict(read.data.patient); return; }
    request.current = null;
    setPhase('idle');
    if (read.status === 401) expireRef.current();
    else setConflict({});
  }

  function handleFailure(status: number, retryAfter: number, problem: Parameters<typeof serverErrors>[0]) {
    request.current = null;
    if (status === 401) { expireRef.current(); return; }
    if (status === 403) { setPhase('restricted'); return; }
    if (status === 404) { setPhase('gone'); return; }
    if (status === 429) { setWait(retryAfter); setPhase('limited'); return; }
    if (status === 400 || status === 422) {
      const mapped = serverErrors(problem);
      if (mapped.length) { setErrors(mapped); setPhase('idle'); return; }
    }
    setPhase('failed');
  }

  function reload() {
    const current = conflict?.current;
    if (!current) return;
    const fresh = fromPatient(current);
    setBaseline(fresh);
    setValues(fresh);
    setVersion(current.version);
    setConflict(undefined);
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void submit();
  }

  const differences = useMemo(() => {
    const current = conflict?.current;
    if (!current) return [];
    const server = fromPatient(current);
    const rows: { label: string; mine: string; theirs: string }[] = [];
    const add = (label: string, mine: string, theirs: string) => { if (mine.trim() !== theirs.trim()) rows.push({ label, mine, theirs }); };
    add('Nom', values.lastName, server.lastName);
    add('Prénom', values.firstName, server.firstName);
    add('Naissance', `${values.day}/${values.month}/${values.year}`, `${server.day}/${server.month}/${server.year}`);
    add('Téléphone', values.phone, server.phone);
    add('E-mail', values.email, server.email);
    return rows;
  }, [conflict, values]);

  if (phase === 'restricted') return <>
    <EntryHeader title={mode === 'new' ? 'Nouveau dossier patient' : 'Modifier l’identité'} meta={practice} crumb={crumb}
      onCrumb={() => shell.navigate(patientsPath(base))} />
    <Notice kind="info" alert title={mode === 'new' ? 'Création non autorisée' : 'Modification non autorisée'}
      actions={<a className="etaye-bouton etaye-bouton--secondaire" href={patientsPath(base)}
        onClick={(event) => { event.preventDefault(); shell.setDirty(null); shell.navigate(patientsPath(base)); }}>Retour aux patients</a>}>
      Votre accès actuel ne permet pas de créer ou de corriger un dossier patient. Demandez à un membre habilité du cabinet.
    </Notice>
  </>;
  if (phase === 'gone') return <>
    <EntryHeader title="Élément introuvable" meta={practice} />
    <Notice kind="info" alert title="Élément introuvable"
      actions={<a className="etaye-bouton etaye-bouton--secondaire" href={patientsPath(base)}
        onClick={(event) => { event.preventDefault(); shell.setDirty(null); shell.navigate(patientsPath(base)); }}>Retour aux patients</a>}>
      Vérifiez le lien ou revenez à la liste des patients.
    </Notice>
  </>;

  const header = mode === 'new'
    ? <EntryHeader title="Nouveau dossier patient" meta={`${practice} · le dossier n’existera que dans ce cabinet`}
      crumb="Nouveau dossier patient" onCrumb={() => shell.navigate(patientsPath(base))} />
    : <EntryHeader title="Modifier l’identité" meta={`${patientName(patient!)} · version ${version} ouverte`}
      crumb="Modifier l’identité" onCrumb={() => shell.navigate(patientsPath(base))} />;
  const submitLabel = phase === 'confirmed' ? 'Enregistré' : busy ? 'Enregistrement…' : 'Enregistrer l’identité';
  const names = duplicate?.candidates.map((candidate) => patientName(candidate)).join(', ');

  return <>
    {header}
    {patient && !conflict && (
      <div className="etaye-banniere" style={{ maxWidth: 720 }}>
        <div>
          <div className="etaye-banniere__nom">{patient.lastName.toLocaleUpperCase('fr-FR')} <span className="etaye-2">{patient.firstName}</span></div>
          <div className="etaye-banniere__traits">
            <span>Naissance <span className="etaye-num">{formatDate(patient.dateOfBirth)}</span></span>
            {ageLabel(patient.dateOfBirth) && <span>{ageLabel(patient.dateOfBirth)}</span>}
          </div>
        </div>
      </div>
    )}
    {conflict && <Notice kind="attention" alert title="Ce dossier a été modifié entre-temps"
      actions={conflict.current ? <button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={reload}>
        Recharger la version enregistrée</button> : undefined}>
      {conflict.current
        ? `La version ${conflict.current.version} a été enregistrée pendant votre saisie. Vos modifications sont conservées ci-dessous ; comparez avant d’enregistrer.`
        : 'Une version plus récente existe, mais elle n’a pas pu être chargée. Vos modifications sont conservées ci-dessous ; réessayez dans un instant.'}
    </Notice>}
    {conflict?.current && differences.length > 0 && (
      <div className="etaye-panneau" style={{ maxWidth: 720 }}>
        <div className="etaye-panneau__corps">
          <div className="etaye-comparaison">
            <dl>
              <dt>Votre saisie</dt>
              {differences.map((row) => <dd key={row.label}>{row.label} : <span className="etaye-difference">{row.mine || '—'}</span></dd>)}
            </dl>
            <dl>
              <dt>Enregistré · version {conflict.current.version} · {clockTime(Date.parse(conflict.current.updatedAt))}</dt>
              {differences.map((row) => <dd key={row.label}>{row.label} : <span className="etaye-difference">{row.theirs || '—'}</span></dd>)}
            </dl>
          </div>
        </div>
      </div>
    )}
    {phase === 'verifying' && <Notice kind="info" title="Vérification du résultat…">
      La connexion a été interrompue. Ne renvoyez pas le formulaire : nous vérifions si l’enregistrement a abouti.
    </Notice>}
    {phase === 'failed' && <Notice kind="critique" alert title="L’enregistrement a échoué">
      Vos informations sont conservées. Réessayez dans un instant.
    </Notice>}
    {phase === 'unconfirmed' && <Notice kind="critique" alert title="Enregistrement non confirmé">
      Nous n’avons pas pu vérifier si l’enregistrement a abouti. Vos informations sont conservées ; réessayez, un même envoi ne crée jamais deux dossiers.
    </Notice>}
    {phase === 'limited' && <Notice kind="attention" alert title="Trop de tentatives">
      Patientez {wait > 0 ? `${wait} secondes` : 'un instant'} avant de réessayer. Vos informations sont conservées.
    </Notice>}
    {busy && <p className="etaye-3" role="status" style={{ fontSize: 13, margin: 0 }}>
      {phase === 'confirmed' ? 'Identité enregistrée.' : 'Enregistrement en cours…'}
    </p>}

    <form className={`etaye-form${busy ? ' etaye-champ--lecture' : ''}`} noValidate style={{ maxWidth: 720 }} onSubmit={onSubmit}>
      {duplicate && (
        <Notice kind="attention" alert title={duplicate.changed ? 'La liste des dossiers semblables a changé' : 'Un dossier semblable existe dans ce cabinet'}
          actions={<>
            {duplicate.candidates.length === 1 && <a className="etaye-bouton etaye-bouton--secondaire"
              href={recordPath(base, duplicate.candidates[0].recordId)}
              onClick={(event) => {
                event.preventDefault();
                shell.navigate(recordPath(base, duplicate.candidates[0].recordId));
              }}>Ouvrir le dossier existant</a>}
            <button className="etaye-bouton etaye-bouton--discret" type="button"
              onClick={() => void submit(duplicate.candidates.map((candidate) => candidate.recordId))}>Créer quand même</button>
          </>}>
          {duplicate.changed
            ? 'Un autre dossier semblable est apparu depuis votre vérification. Vérifiez-le avant de confirmer.'
            : 'Vérifiez avant de créer un second dossier. Rien n’est fusionné automatiquement.'}
          <ul className="etaye-candidats">
            {duplicate.candidates.map((candidate) => (
              <li key={candidate.recordId}>
                <strong>{patientName(candidate)}</strong>
                <span>{candidateLine(candidate)}</span>
                {duplicate.candidates.length > 1 && <a href={recordPath(base, candidate.recordId)}
                  onClick={(event) => { event.preventDefault(); shell.navigate(recordPath(base, candidate.recordId)); }}>
                  Ouvrir ce dossier<span className="etaye-vh"> de {patientName(candidate)}</span>
                </a>}
              </li>
            ))}
          </ul>
          <div style={{ marginTop: 'var(--espace-2)' }}>
            « Créer quand même » confirme qu’il s’agit d’une autre personne que {names}.
          </div>
        </Notice>
      )}
      {errors.length > 0 && <ErrorSummary errors={errors} />}
      <div className="etaye-form__ligne">
        <TextField id="i-nom" label="Nom de naissance" required value={values.lastName} readOnly={busy} autoComplete="family-name"
          error={fieldError.get('i-nom')} onChange={(value) => change({ lastName: value })} />
        <TextField id="i-pre" label="Prénom" required value={values.firstName} readOnly={busy} autoComplete="given-name"
          error={fieldError.get('i-pre')} onChange={(value) => change({ firstName: value })} />
      </div>
      <BirthDate values={values} readOnly={busy} error={fieldError.get('dn-j')} onChange={change} />
      <div className="etaye-form__ligne">
        <TextField id="i-tel" label="Téléphone" kind="tel" value={values.phone} readOnly={busy} maxLength={40}
          error={fieldError.get('i-tel')} onChange={(value) => change({ phone: value })} />
        <TextField id="i-mail" label="Adresse e-mail" kind="email" value={values.email} readOnly={busy}
          help={mode === 'edit' && !values.email ? 'Non renseignée au dossier' : undefined}
          error={fieldError.get('i-mail')} onChange={(value) => change({ email: value })} />
      </div>
      <p className="etaye-3" style={{ fontSize: 13, margin: 0 }}>
        L’espace patient n’est pas créé ici : une invitation séparée est envoyée par l’équipe soignante.
      </p>
      <div className="etaye-ligne">
        <button className="etaye-bouton etaye-bouton--principal etaye-bouton--stable" type="submit" disabled={busy}
          aria-busy={busy || undefined}>{submitLabel}</button>
        {/* Disabled while a request is in flight: leaving then would offer to discard input that may already be saved. */}
        <button className="etaye-bouton etaye-bouton--discret" type="button" disabled={busy}
          onClick={() => shell.navigate(patientsPath(base))}>Annuler</button>
      </div>
    </form>
  </>;
}

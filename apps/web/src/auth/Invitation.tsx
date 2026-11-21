import { type FormEvent, useEffect, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { ActivationWritable, InvitationPreview, Session } from '../api/generated/types.gen';
import { AuthShell, Banner, ErrorSummary, Field, type FieldError, FocusedHeading, useRetryDelay } from './AuthComponents';
import { SignIn } from './SignIn';

const invitationToken = /^[A-Za-z0-9_-]{32,512}$/;
const visitDate = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris', weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
  hour: 'numeric', minute: 'numeric',
});
const shortDate = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris', dateStyle: 'short', timeStyle: 'short',
});

type Terminal = 'invalid' | 'EXPIRED' | 'REVOKED' | 'ALREADY_USED' | 'already-active';
type Accepted = { onAccepted: (session: Session) => void };

function readFragment() {
  const value = window.location.hash.slice(1);
  return invitationToken.test(value) ? value : '';
}

export function Invitation({ onAccepted }: Accepted) {
  const [token, setToken] = useState(readFragment);
  useEffect(() => {
    function clearFragment() {
      if (window.location.hash) {
        window.history.replaceState(null, '', window.location.pathname + window.location.search);
      }
    }
    clearFragment();
    function followNewLink() {
      setToken(readFragment());
      clearFragment();
    }
    window.addEventListener('hashchange', followNewLink);
    return () => window.removeEventListener('hashchange', followNewLink);
  }, []);

  return token
    ? <InvitationLookup key={token} token={token} onAccepted={onAccepted} />
    : <InvitationStop state="invalid" />;
}

function InvitationLookup({ token, onAccepted }: Accepted & { token: string }) {
  const [preview, setPreview] = useState<InvitationPreview>();
  const [signedIn, setSignedIn] = useState(false);
  const [status, setStatus] = useState<'loading' | 'invalid' | 'failed'>('loading');
  const [attempt, setAttempt] = useState(0);
  const { waiting, block } = useRetryDelay();

  useEffect(() => {
    const request = new AbortController();
    async function inspect() {
      const result = await accountApi.inspectInvitation(token, request.signal);
      if (request.signal.aborted) return;
      if (!result.ok) {
        if (result.status === 429) block(result.retryAfter);
        setStatus(result.status === 404 || result.status === 400 ? 'invalid' : 'failed');
        return;
      }
      if (result.data.eligibility === 'USABLE') {
        const session = await accountApi.readSession(request.signal);
        if (request.signal.aborted) return;
        setSignedIn(session.ok);
      }
      setPreview(result.data);
    }
    void inspect();
    return () => request.abort();
  }, [token, attempt, block]);

  if (preview) {
    return preview.eligibility === 'USABLE'
      ? <InvitationForm token={token} preview={preview} initiallySignedIn={signedIn} onAccepted={onAccepted} />
      : <InvitationStop state={preview.eligibility} preview={preview} />;
  }
  if (status === 'invalid') return <InvitationStop state="invalid" />;
  return (
    <AuthShell>
      <FocusedHeading>Invitation</FocusedHeading>
      <div className="etaye-auth__carte" aria-busy={status === 'loading'}>
        {status === 'loading' ? <p role="status">Vérification de l'invitation…</p> : <>
          <Banner title={waiting ? 'Trop de demandes' : "La vérification de l'invitation a échoué"}>
            {waiting ? 'Réessayez dans quelques minutes.' : 'Le service est temporairement indisponible.'}
          </Banner>
          <button type="button" className="etaye-bouton etaye-bouton--secondaire" disabled={waiting}
            onClick={() => { setStatus('loading'); setAttempt((value) => value + 1); }}>Réessayer</button>
        </>}
      </div>
    </AuthShell>
  );
}

function ContactPractice() {
  return (
    <details className="etaye-invitation__contact">
      <summary className="etaye-bouton etaye-bouton--secondaire etaye-bouton--patient">Contacter le cabinet</summary>
      <p>Utilisez les coordonnées de votre cabinet ou répondez à l'e-mail d'invitation.</p>
    </details>
  );
}

function practiceWithArticle(practice: string, capitalized = false) {
  return /^cabinet\s/i.test(practice) ? `${capitalized ? 'Le' : 'le'} ${practice}` : practice;
}

function InvitationStop({ state, preview }: { state: Terminal; preview?: InvitationPreview }) {
  const patient = preview?.purpose === 'PATIENT_PORTAL';
  const signIn = patient ? '/connexion/patient' : '/connexion';
  let heading: string;
  let description: string;
  let action = 'Revenir à la connexion';
  switch (state) {
    case 'EXPIRED':
      heading = patient ? "Ce lien d'activation a expiré" : 'Cette invitation a expiré';
      description = patient && preview
        ? `Il était valable jusqu'au ${visitDate.format(new Date(preview.expiresAt))}. Aucun espace n'a été créé avec ce lien.`
        : "Les invitations ont une durée de validité limitée. Demandez à l'administration du cabinet de vous en envoyer une nouvelle.";
      break;
    case 'REVOKED':
      heading = "Cette invitation n'est plus valable";
      description = "Elle a été annulée par le cabinet. Aucun compte n'a été activé.";
      break;
    case 'ALREADY_USED':
    case 'already-active':
      heading = patient ? 'Votre espace patient est déjà activé' : "Cette invitation vient d'être utilisée";
      description = patient
        ? preview?.acceptedAt
          ? `Il a été activé le ${visitDate.format(new Date(preview.acceptedAt))}. Ce lien ne peut plus servir.`
          : 'Ce lien ne peut plus servir. Contactez le cabinet si vous avez besoin d’aide.'
        : "Elle a déjà servi à activer un compte, peut-être dans un autre onglet. Connectez-vous pour continuer.";
      action = patient ? 'Me connecter' : 'Se connecter';
      break;
    default:
      heading = "Lien d'invitation invalide";
      description = "Le lien est incomplet ou ne correspond à aucune invitation en cours. Vérifiez que vous avez ouvert le lien complet de l'e-mail.";
  }
  return (
    <AuthShell patient={patient}>
      <FocusedHeading>{heading}</FocusedHeading>
      <div className="etaye-auth__carte">
        <p>{description}</p>
        {patient && state === 'EXPIRED' ? <>
          <p>{preview && practiceWithArticle(preview.practiceName, true)} peut vous envoyer un nouveau lien.</p>
          <ContactPractice />
        </> : <a className={`etaye-bouton etaye-bouton--secondaire${patient ? ' etaye-bouton--patient' : ''}`}
          href={signIn}>{action}</a>}
      </div>
    </AuthShell>
  );
}

const terminalByCode: Partial<Record<string, Terminal>> = {
  INVITATION_EXPIRED: 'EXPIRED', INVITATION_REVOKED: 'REVOKED', INVITATION_ALREADY_USED: 'ALREADY_USED',
  PORTAL_LINK_EXISTS: 'already-active', NOT_FOUND: 'invalid',
};

function InvitationForm({ token, preview, initiallySignedIn, onAccepted }: Accepted & {
  token: string; preview: InvitationPreview; initiallySignedIn: boolean;
}) {
  const patient = preview.purpose === 'PATIENT_PORTAL';
  const [signedIn, setSignedIn] = useState(initiallySignedIn);
  const [signingIn, setSigningIn] = useState(false);
  const [pending, setPending] = useState(false);
  const [terminal, setTerminal] = useState<Terminal>();
  const [issue, setIssue] = useState<'verify' | 'sign-in' | 'unavailable' | 'membership'>();
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [birthDate, setBirthDate] = useState('');
  const [name, setName] = useState('');
  const { waiting, block } = useRetryDelay();
  const receipt = useRef<string | undefined>(undefined);
  const request = useRef<AbortController | undefined>(undefined);
  useEffect(() => () => request.current?.abort(), []);

  function fieldError(id: string, summary: string, detail: string) {
    return { id, summary, detail: `Erreur : ${detail}` };
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending || waiting) return;
    const fields = event.currentTarget.elements;
    const passwordField = fields.namedItem('password') as HTMLInputElement | null;
    const confirmationField = fields.namedItem('confirmation') as HTMLInputElement | null;
    const password = passwordField?.value;
    const confirmation = confirmationField?.value;
    const verification = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(birthDate.trim());
    const validation: FieldError[] = [];
    if (patient && !verification) {
      validation.push(fieldError('p-dn', 'Votre date de naissance : format JJ/MM/AAAA', 'saisissez la date au format JJ/MM/AAAA.'));
    }
    if (!patient && !signedIn && !name.trim()) {
      validation.push(fieldError('a-nom', 'Nom affiché : obligatoire', 'indiquez votre nom affiché.'));
    }
    const length = password ? Array.from(password).length : 0;
    if (!signedIn && (length < 12 || length > 1024)) {
      validation.push(fieldError('a-pw', 'Mot de passe : de 12 à 1 024 caractères', 'saisissez de 12 à 1 024 caractères.'));
    }
    if (passwordField && confirmationField && password !== confirmation) {
      passwordField.value = '';
      confirmationField.value = '';
      receipt.current = undefined;
      validation.push(fieldError('a-pw-confirm', 'Confirmation : les deux mots de passe sont différents',
        'les deux mots de passe sont différents. Saisissez-les à nouveau.'));
    }
    setErrors(validation);
    setIssue(undefined);
    if (validation.length) return;

    const body: ActivationWritable = { token, password, displayName: !patient && !signedIn ? name : undefined };
    if (verification) body.recipientVerification = `${verification[3]}-${verification[2]}-${verification[1]}`;
    receipt.current ??= crypto.randomUUID();
    const controller = new AbortController();
    request.current = controller;
    setPending(true);
    const result = await accountApi.acceptInvitation(body, receipt.current, controller.signal);
    if (controller.signal.aborted) return;
    setPending(false);
    if (result.ok) {
      onAccepted(result.data);
      return;
    }
    const stop = terminalByCode[result.code ?? ''];
    if (stop) setTerminal(stop);
    else if (result.code === 'RECIPIENT_MISMATCH') {
      if (passwordField) passwordField.value = '';
      receipt.current = undefined;
      setIssue('verify');
    }
    else if (result.code === 'SIGN_IN_REQUIRED' || result.status === 401) setIssue('sign-in');
    else if (result.code === 'MEMBERSHIP_EXISTS') setIssue('membership');
    else if (result.status === 429) block(result.retryAfter);
    else setIssue('unavailable');
  }

  async function useNewAccount() {
    const controller = new AbortController();
    request.current = controller;
    setPending(true);
    const result = await accountApi.signOut(controller.signal);
    if (controller.signal.aborted) return;
    setPending(false);
    if (result.ok || result.status === 401) {
      receipt.current = undefined;
      setSignedIn(false);
      setIssue(undefined);
    } else {
      setIssue('unavailable');
    }
  }

  if (terminal) return <InvitationStop state={terminal} preview={preview} />;
  if (signingIn) return <SignIn patient={patient} returning={false} onSignedIn={() => {
    receipt.current = undefined;
    setSignedIn(true);
    setSigningIn(false);
    setIssue(undefined);
  }} />;

  const error = (id: string) => errors.find((item) => item.id === id);
  return (
    <AuthShell patient={patient}>
      <div className="etaye-invitation__heading">
        <p className="etaye-invitation__label etaye-2">{patient ? 'Invitation de votre cabinet' : 'Invitation à rejoindre une équipe'}</p>
        <FocusedHeading>{patient ? 'Activer mon espace patient' : `Rejoindre ${practiceWithArticle(preview.practiceName)}`}</FocusedHeading>
        <p className="etaye-2 etaye-auth__introduction">{patient
          ? `${practiceWithArticle(preview.practiceName, true)} vous propose un espace pour préparer vos visites, voir vos rendez-vous et lire ce que votre praticienne publie pour vous.`
          : "Cette invitation donne accès à l'espace des professionnels du cabinet. Elle ne donne accès à aucun dossier avant l'activation et la vérification de vos droits."}</p>
      </div>
      <div className="etaye-auth__carte">
        <dl className="etaye-invitation__details">
          {patient && <><dt>Cabinet</dt><dd>{preview.practiceName}</dd></>}
          <dt>Destinataire</dt><dd>{preview.recipientHint}</dd>
          {patient ? <><dt>Ce lien sert à</dt><dd>Relier votre compte à votre dossier dans ce cabinet, une seule fois</dd></> : <>
            <dt>Expire le</dt><dd className="etaye-invitation__date">{shortDate.format(new Date(preview.expiresAt))}</dd>
            <dt>Droits</dt><dd>Fixés par l'administration du cabinet · <span className="etaye-3">non précisés dans le jeu de démonstration</span></dd>
          </>}
        </dl>
      </div>
      <div className="etaye-auth__carte">
        <form className="etaye-form" noValidate aria-busy={pending} onSubmit={submit}
          onChange={() => { receipt.current = undefined; }}>
          {errors.length > 0 && <ErrorSummary errors={errors} />}
          {issue === 'verify' && <Banner title="Activation impossible avec ces informations">
            Pour protéger votre dossier, nous ne pouvons pas vous dire quelle information ne correspond pas. Contactez le cabinet.
          </Banner>}
          {issue === 'sign-in' && <Banner title="Connectez-vous pour continuer">
            Utilisez le compte auquel cette invitation a été envoyée.
            <div className="etaye-auth__actions">
              <button type="button" className="etaye-bouton etaye-bouton--secondaire" disabled={pending}
                onClick={() => setSigningIn(true)}>Se connecter</button>
              {signedIn && <button type="button" className="etaye-bouton etaye-bouton--secondaire"
                disabled={pending} onClick={useNewAccount}>Continuer avec un nouveau compte</button>}
            </div>
          </Banner>}
          {issue === 'membership' && <Banner title="Vous avez déjà accès à ce cabinet">
            Contactez le cabinet pour toute modification de vos droits.
          </Banner>}
          {issue === 'unavailable' && <Banner title="L'activation n'a pas pu être confirmée">
            Réessayez avec les mêmes informations.
          </Banner>}
          {waiting && <Banner title="Trop de demandes">Réessayez dans quelques minutes.</Banner>}
          {patient && <Field id="p-dn" name="birthDate" label="Votre date de naissance" type="text"
            autoComplete="bday" maxLength={10} value={birthDate} readOnly={pending} error={error('p-dn')}
            onChange={(event) => setBirthDate(event.target.value)}
            hint="Format JJ/MM/AAAA. Elle sert à vérifier que l'invitation vous est destinée." />}
          {!patient && !signedIn && <Field id="a-nom" name="displayName" label="Nom affiché" type="text"
            autoComplete="name" maxLength={200} value={name} readOnly={pending} error={error('a-nom')}
            onChange={(event) => setName(event.target.value)} />}
          {!signedIn && <Field id="a-pw" name="password" label={patient ? 'Créer un mot de passe' : 'Mot de passe'}
            type="password" autoComplete="new-password" maxLength={2048} readOnly={pending} error={error('a-pw')}
            hint={patient ? '12 caractères au moins.' : '12 caractères au moins. Une phrase facile à retenir convient.'} />}
          {!patient && !signedIn && <Field id="a-pw-confirm" name="confirmation" label="Confirmer le mot de passe"
            type="password" autoComplete="new-password" maxLength={2048} readOnly={pending} error={error('a-pw-confirm')} />}
          <button type="submit" className={`etaye-bouton etaye-bouton--principal${patient ? ' etaye-bouton--patient' : ''}`}
            disabled={pending || waiting}>{pending ? 'Activation en cours…' : patient ? 'Activer mon espace' : 'Activer mon compte'}</button>
        </form>
        {patient && <ContactPractice />}
      </div>
      {!patient && <p className="etaye-auth__first etaye-3">Ce n'est pas vous ? Ne créez pas de compte et prévenez le cabinet.</p>}
    </AuthShell>
  );
}

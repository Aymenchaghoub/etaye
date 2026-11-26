import { type FormEvent, useEffect, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import { AuthHeading, AuthShell, Banner, emailError, ErrorSummary, Field, type FieldError, FocusedHeading, useRetryDelay } from './AuthComponents';

export function RecoveryRequest({ patient }: { patient: boolean }) {
  const [address, setAddress] = useState('');
  const [sent, setSent] = useState(false);
  const [pending, setPending] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const { waiting, block } = useRetryDelay();
  const receipt = useRef<{ address: string; key: string } | undefined>(undefined);
  const request = useRef<AbortController | undefined>(undefined);
  useEffect(() => () => request.current?.abort(), []);
  const signIn = patient ? '/connexion/patient' : '/connexion';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (waiting || pending) return;
    const input = event.currentTarget.elements.namedItem('email') as HTMLInputElement;
    const error = emailError(input);
    setErrors(error ? [error] : []);
    setUnavailable(false);
    if (error) return;

    const email = input.value;
    const normalized = email.trim().toLowerCase();
    if (receipt.current?.address !== normalized) {
      receipt.current = { address: normalized, key: crypto.randomUUID() };
    }
    const controller = new AbortController();
    request.current = controller;
    setPending(true);
    const result = await accountApi.requestRecovery(email, receipt.current.key, controller.signal);
    if (controller.signal.aborted) return;
    setPending(false);
    if (result.ok) {
      setAddress(email);
      setSent(true);
    } else if (result.status === 429) {
      block(result.retryAfter);
    } else {
      setUnavailable(true);
    }
  }

  if (sent) return (
    <AuthShell>
      <FocusedHeading>Demande de récupération reçue</FocusedHeading>
      <div className="etaye-auth__carte">
        <p>Si un compte correspond à <strong>{address}</strong>, cette demande permet de récupérer votre accès.</p>
        <p className="etaye-2 etaye-auth__detail">
          Le lien de récupération est valable 15 minutes et ne sert qu'une fois. Vous pouvez refaire la demande si nécessaire.
        </p>
        <a className="etaye-bouton etaye-bouton--secondaire" href={signIn}>Revenir à la connexion</a>
      </div>
    </AuthShell>
  );

  return (
    <AuthShell>
      <AuthHeading title="Récupérer votre accès">
        Indiquez l'adresse de votre compte pour demander un lien et choisir un nouveau mot de passe.
      </AuthHeading>
      {errors.length > 0 && <ErrorSummary errors={errors} />}
      {waiting && <Banner title="Trop de demandes">
        Les demandes sont temporairement limitées. Réessayez dans quelques minutes.
      </Banner>}
      {unavailable && <Banner title="La demande n'a pas pu être envoyée">
        <div>Problème de connexion. Votre adresse est conservée ; vous pouvez réessayer.</div>
        <button type="submit" form="recovery-form" className="etaye-bouton etaye-bouton--secondaire">Réessayer</button>
      </Banner>}
      <div className="etaye-auth__carte">
        <form className="etaye-form" id="recovery-form" noValidate onSubmit={(event) => void submit(event)} aria-busy={pending}>
          <Field id="r-mail" name="email" type="email" label="Adresse e-mail" autoComplete="email"
            autoFocus maxLength={254} value={address} readOnly={pending} onChange={(event) => setAddress(event.target.value)}
            error={errors[0]} />
          <button type="submit" className="etaye-bouton etaye-bouton--principal" disabled={waiting || pending}>
            {pending ? 'Envoi en cours…' : 'Recevoir un lien'}
          </button>
        </form>
      </div>
      <a href={signIn}>Revenir à la connexion</a>
    </AuthShell>
  );
}

const RESET_TOKEN = /^[A-Za-z0-9_-]{43}$/;

export function ResetPassword() {
  const [state, setState] = useState(() => RESET_TOKEN.test(window.location.hash.slice(1)) ? 'ready' : 'invalid');
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [pending, setPending] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const { waiting, block } = useRetryDelay();
  const request = useRef<AbortController | undefined>(undefined);
  useEffect(() => () => request.current?.abort(), []);

  function removeFragment() {
    window.history.replaceState(null, '', window.location.pathname + window.location.search);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending || waiting) return;
    const password = event.currentTarget.elements.namedItem('password') as HTMLInputElement;
    const confirmation = event.currentTarget.elements.namedItem('confirmation') as HTMLInputElement;
    const value = password.value;
    const lengthError = {
      id: password.id, summary: 'Nouveau mot de passe : 12 caractères au moins',
      detail: 'Erreur : 12 caractères au moins. Le champ a été effacé.',
    };

    setUnavailable(false);
    const characters = Array.from(value).length;
    if (characters < 12 || characters > 1024) {
      password.value = '';
      confirmation.value = '';
      setErrors([characters < 12 ? lengthError : {
        id: password.id,
        summary: 'Nouveau mot de passe : 1 024 caractères au maximum',
        detail: 'Erreur : le mot de passe est trop long. Le champ a été effacé.',
      }]);
      return;
    }
    if (value !== confirmation.value) {
      confirmation.value = '';
      setErrors([{
        id: confirmation.id, summary: 'Confirmer le nouveau mot de passe',
        detail: 'Erreur : saisissez le même mot de passe dans les deux champs.',
      }]);
      return;
    }

    const token = window.location.hash.slice(1);
    if (!RESET_TOKEN.test(token)) {
      removeFragment();
      setState('invalid');
      return;
    }
    password.value = '';
    confirmation.value = '';
    setErrors([]);
    const controller = new AbortController();
    request.current = controller;
    setPending(true);
    const result = await accountApi.resetPassword(token, value, crypto.randomUUID(), controller.signal);
    if (controller.signal.aborted) return;
    setPending(false);
    if (result.ok || result.code === 'INVALID_RECOVERY_LINK') {
      removeFragment();
      setState(result.ok ? 'complete' : 'invalid');
    } else if (result.code === 'PASSWORD_TOO_SHORT') {
      setErrors([lengthError]);
    } else if (result.status === 429) {
      block(result.retryAfter);
    } else {
      setUnavailable(true);
    }
  }

  if (state === 'invalid') return (
    <AuthShell>
      <FocusedHeading>Ce lien n'est plus valable</FocusedHeading>
      <div className="etaye-auth__carte">
        <p>Il a expiré ou a déjà été utilisé. Votre mot de passe n'a pas changé lors de cette demande.</p>
        <a className="etaye-bouton etaye-bouton--principal" href="/recuperation">Faire une nouvelle demande</a>
      </div>
    </AuthShell>
  );

  if (state === 'complete') return (
    <AuthShell>
      <FocusedHeading>Mot de passe modifié</FocusedHeading>
      <div className="etaye-auth__carte">
        <p>Vos autres sessions ont été fermées. Connectez-vous avec votre nouveau mot de passe.</p>
        <a className="etaye-bouton etaye-bouton--principal" href="/connexion">Se connecter</a>
      </div>
    </AuthShell>
  );

  return (
    <AuthShell>
      <AuthHeading title="Choisir un nouveau mot de passe" />
      {waiting && <Banner title="Trop de demandes">
        Les demandes sont temporairement limitées. Réessayez dans quelques minutes.
      </Banner>}
      {unavailable && <Banner title="Le changement n'a pas pu être confirmé">
        En cas de coupure, essayez d'abord de vous connecter avec votre nouveau mot de passe.
      </Banner>}
      <div className="etaye-auth__carte">
        <form className="etaye-form" noValidate onSubmit={(event) => void submit(event)} aria-busy={pending}>
          {errors.length > 0 && <ErrorSummary errors={errors} amber />}
          <Field id="r-pw" name="password" type="password" label="Nouveau mot de passe"
            autoComplete="new-password" autoFocus maxLength={2048} hint="12 caractères au moins."
            error={errors.find(({ id }) => id === 'r-pw')} />
          <Field id="r-pw2" name="confirmation" type="password" label="Confirmer le nouveau mot de passe"
            autoComplete="new-password" maxLength={2048} error={errors.find(({ id }) => id === 'r-pw2')} />
          <p className="etaye-2 etaye-auth__detail">Après le changement, vos autres sessions ouvertes seront fermées.</p>
          <button type="submit" className="etaye-bouton etaye-bouton--principal" disabled={pending || waiting}>
            {pending ? 'Enregistrement en cours…' : 'Enregistrer le mot de passe'}
          </button>
        </form>
      </div>
    </AuthShell>
  );
}

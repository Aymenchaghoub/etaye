import { type FormEvent, useEffect, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { Session } from '../api/generated/types.gen';
import { SHOWCASE } from '../demo/demo-entry';
import { AuthHeading, AuthShell, Banner, emailError, ErrorSummary, Field, type FieldError, useRetryDelay } from './AuthComponents';

export function SignIn({ patient, returning, returnTo, demo, onCancel, onSignedIn }: {
  patient: boolean; returning: boolean; returnTo?: string; demo?: { actor?: string; email?: string };
  onCancel?: () => void; onSignedIn: (session: Session) => void;
}) {
  const [showPassword, setShowPassword] = useState(false);
  const [pending, setPending] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const { waiting, block } = useRetryDelay();
  const request = useRef<AbortController | undefined>(undefined);
  useEffect(() => () => request.current?.abort(), []);
  const recoveryPath = patient ? '/recuperation?espace=patient' : '/recuperation';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (waiting || pending) return;
    const email = event.currentTarget.elements.namedItem('email') as HTMLInputElement;
    const password = event.currentTarget.elements.namedItem('password') as HTMLInputElement;
    const validation = [];
    const invalidEmail = emailError(email);
    if (invalidEmail) validation.push(invalidEmail);
    if (!password.value) validation.push({
      id: password.id, summary: 'Mot de passe : obligatoire', detail: 'Erreur : indiquez votre mot de passe.',
    });
    setErrors(validation);
    setUnavailable(false);
    if (validation.length) return;

    const controller = new AbortController();
    request.current = controller;
    const credentials = { email: email.value, password: password.value, returnTo };
    password.value = '';
    setShowPassword(false);
    setPending(true);
    const result = await accountApi.signIn(credentials, controller.signal);
    if (controller.signal.aborted) return;
    setPending(false);
    if (result.ok) {
      onSignedIn(result.data);
      return;
    }
    if (result.status === 401) {
      setErrors([{
        id: password.id, summary: 'Adresse e-mail ou mot de passe incorrect',
        detail: 'Erreur : adresse e-mail ou mot de passe incorrect. Le mot de passe a été effacé.',
      }]);
    } else if (result.status === 429) {
      block(result.retryAfter);
    } else {
      setUnavailable(true);
    }
  }

  return (
    <AuthShell patient={patient}>
      <AuthHeading title={patient ? 'Se connecter à votre espace patient' : 'Se connecter'}>
        {patient
          ? 'Consultez vos rendez-vous et les informations que votre cabinet a publiées pour vous.'
          : "Espace des professionnels et de l'administration des cabinets."}
      </AuthHeading>
      {demo?.actor && <Banner title="Scénario fictif" information>
        <p>Vous avez choisi {demo.actor}. <a href={SHOWCASE}>Choisir un autre scénario</a></p>
      </Banner>}
      {returning && <Banner title="Votre session a expiré" information>
        Reconnectez-vous pour revenir à la page demandée. Aucune information du dossier n'est affichée avant la connexion.
      </Banner>}
      {errors.length > 0 && <ErrorSummary errors={errors} />}
      {waiting && <Banner title="Trop de tentatives">
        Les connexions sont temporairement bloquées pour cette adresse. Réessayez dans quelques minutes ou{' '}
        <a href={recoveryPath}>récupérez votre accès</a>.
      </Banner>}
      {unavailable && <Banner title="La connexion a échoué">
        Le service est temporairement indisponible. Réessayez dans quelques instants.
      </Banner>}
      <div className="etaye-auth__carte">
        <form className="etaye-form" noValidate onSubmit={(event) => void submit(event)} aria-busy={pending}>
          <Field id="l-mail" name="email" type="email" label="Adresse e-mail" autoComplete="username"
            autoFocus defaultValue={demo?.email} maxLength={254} error={errors.find(({ id }) => id === 'l-mail')} />
          <Field id="l-pw" name="password" type={showPassword ? 'text' : 'password'} label="Mot de passe"
            autoComplete="current-password" maxLength={2048} error={errors.find(({ id }) => id === 'l-pw')} />
          <div className="etaye-auth__options">
            <label className="etaye-choix">
              <input type="checkbox" checked={showPassword} onChange={(event) => setShowPassword(event.target.checked)} />
              <span>Afficher le mot de passe</span>
            </label>
            <a href={recoveryPath}>Mot de passe oublié ?</a>
          </div>
          <button type="submit" className={`etaye-bouton etaye-bouton--principal${patient ? ' etaye-bouton--patient' : ''}`}
            disabled={pending || waiting}>
            {pending ? 'Connexion en cours…' : 'Se connecter'}
          </button>
        </form>
      </div>
      {onCancel && <button type="button" className="etaye-bouton etaye-bouton--discret" disabled={pending}
        onClick={onCancel}>Revenir à l’invitation</button>}
      <p className="etaye-auth__first etaye-3">
        Première connexion ? Ouvrez le lien d'invitation reçu par e-mail.{' '}
        <a href={patient ? '/connexion' : '/connexion/patient'}>{patient ? 'Espace professionnel' : 'Espace patient'}</a>
      </p>
    </AuthShell>
  );
}

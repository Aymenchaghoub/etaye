import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components';
import '../../../../design/received/2026-11-03/icons.css';
import { Banner } from './AuthComponents';
import './auth.css';
import './practice-switch-dialog.css';

type SaveOutcome = 'entered' | 'failed' | 'invalid' | 'unconfirmed' | 'target-unavailable' | 'source-unavailable';

const failures = {
  failed: ['Enregistrement impossible', 'Votre saisie est conservée. Vous pouvez réessayer ou rester sur cette page.'],
  invalid: ['Saisie à vérifier', 'Restez sur cette page pour corriger le champ signalé.'],
  unconfirmed: ['Enregistrement non confirmé', 'Revenez à votre saisie pour vérifier son état avant de réessayer.'],
  'target-unavailable': ['Modifications enregistrées', 'La destination choisie n’est plus disponible.'],
} as const;

export function PracticeSwitchDialog({ destination, onStay, onDiscard, onChooseAnother, onEntered,
  onSourceUnavailable, saveAndVerify }: {
  destination: string;
  onStay: () => void;
  onDiscard: () => void;
  onChooseAnother: () => void;
  onEntered: () => void;
  onSourceUnavailable: () => void;
  saveAndVerify: (signal: AbortSignal) => Promise<SaveOutcome>;
}) {
  const heading = useRef<HTMLHeadingElement>(null);
  const request = useRef<AbortController | null>(null);
  const [pending, setPending] = useState(false);
  const [status, setStatus] = useState<keyof typeof failures>();

  useLayoutEffect(() => {
    heading.current?.focus({ preventScroll: true });
  }, []);
  useEffect(() => () => request.current?.abort(), []);

  const dismiss = () => {
    if (request.current === null) onStay();
  };

  async function save() {
    if (request.current || status === 'unconfirmed' || status === 'target-unavailable') return;
    const active = new AbortController();
    request.current = active;
    heading.current?.focus({ preventScroll: true });
    setPending(true);
    setStatus(undefined);
    let result: SaveOutcome;
    try {
      result = await saveAndVerify(active.signal);
    } catch {
      result = 'unconfirmed';
    }

    if (active.signal.aborted) return;
    request.current = null;
    setPending(false);
    if (result === 'entered') onEntered();
    else if (result === 'source-unavailable') onSourceUnavailable();
    else setStatus(result);
  }

  return (
    <ModalOverlay className="etaye-root etaye-switch-overlay" isOpen isKeyboardDismissDisabled={pending}
      onOpenChange={(open) => { if (!open) dismiss(); }}>
      <Modal className="etaye-switch-modal">
        <Dialog className="etaye-switch-dialog">
          <header className="etaye-switch-dialog__header">
            <Heading slot="title" level={2} ref={heading} tabIndex={-1}>Modifications non enregistrées</Heading>
            <button className="etaye-switch-dialog__close" type="button" aria-label="Fermer" onClick={dismiss} disabled={pending}>
              <span className="etaye-icone etaye-icone--18 etaye-icone--fermer" aria-hidden="true" />
            </button>
          </header>
          <div className="etaye-switch-dialog__body">
            {status !== 'target-unavailable' && <>
              <p>Vous avez des modifications en cours dans le contexte actuel.</p>
              <p className="etaye-2">Destination · <strong>{destination}</strong></p>
            </>}
            {pending && <p role="status">Enregistrement en cours…</p>}
            {status && <Banner title={failures[status][0]} information={status === 'target-unavailable'}>
              <p>{failures[status][1]}</p>
            </Banner>}
          </div>
          <footer className="etaye-switch-dialog__actions">
            {status === 'target-unavailable' ? <>
              <button type="button" className="etaye-bouton etaye-bouton--principal" onClick={dismiss}>Retourner à mon espace</button>
              <button type="button" className="etaye-bouton etaye-bouton--secondaire" onClick={onChooseAnother}>Choisir un autre contexte</button>
            </> : <>
              <button type="button" className="etaye-bouton etaye-bouton--principal" disabled={pending || status === 'unconfirmed'}
                onClick={() => void save()}>{pending ? 'Enregistrement…' : 'Enregistrer et continuer'}</button>
              <button type="button" className="etaye-bouton etaye-bouton--secondaire" disabled={pending}
                onClick={dismiss}>Rester sur cette page</button>
              <button type="button" className="etaye-bouton etaye-bouton--discret" disabled={pending}
                onClick={() => { if (request.current === null) onDiscard(); }}>Quitter sans enregistrer</button>
            </>}
          </footer>
        </Dialog>
      </Modal>
    </ModalOverlay>
  );
}

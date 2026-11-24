import { type ReactNode, useEffect, useId, useRef, useState } from 'react';
import { accountApi } from '../api/browser';
import type { Source } from '../api/generated/types.gen';
import { Notice } from '../patients/patient-ui';
import { eventLabel, formatInstant, originLabel, originState, sourceHeading } from './evidence';
import { outcome, type Remote } from './remote';

/** What a row already knows about its source, so the marker and the button are named before the source is fetched. */
export type SourceHandle = { id: string; reference: string; number: number };

/**
 * The marker and the in-place source of one evidence item (E1, D-45). The marker is a link to the source entry with
 * its identifier in its name; the button discloses the revision, loaded on first opening, with a loading and a failure
 * state. There is no popover and no drawer. Focus stays on the button, and "Revenir à l'élément" returns it to the marker.
 * The two parts are returned separately so a screen can place the marker beside the item and the button under it.
 */
export function useSource(source: SourceHandle, practiceId: string, asOf: string | undefined, expire: () => void,
  variant: 'inline' | 'alone' = 'inline'): { marker: ReactNode; panel: ReactNode } {
  const uid = useId();
  const markerId = `m-${uid}`;
  const regionId = `r-${uid}`;
  const [open, setOpen] = useState(false);
  const [remote, setRemote] = useState<Remote<Source> | undefined>();
  const [attempt, setAttempt] = useState(0);
  const button = useRef<HTMLButtonElement>(null);
  const focusButton = useRef(false);
  const expireRef = useRef(expire);
  expireRef.current = expire;

  useEffect(() => {
    if (!open) return undefined;
    const request = new AbortController();
    setRemote({ phase: 'loading' });
    void accountApi.readSource(practiceId, source.id, asOf, request.signal).then((result) => {
      if (!request.signal.aborted) setRemote(outcome(result, () => expireRef.current()));
    });
    return () => request.abort();
  }, [open, attempt, practiceId, source.id, asOf]);

  useEffect(() => {
    if (focusButton.current) {
      focusButton.current = false;
      button.current?.focus();
    }
  });

  const marker = (
    <a className={`etaye-repere${variant === 'alone' ? ' etaye-repere--seul' : ''}`} id={markerId} href={`#${regionId}`}
      aria-label={`Source ${source.number} : ${source.reference}`}
      onClick={(event) => { event.preventDefault(); focusButton.current = true; setOpen(true); }}>{source.number}</a>
  );
  const panel = (
    <div className="etaye-source">
      <button ref={button} type="button" className="etaye-bouton etaye-bouton--discret" aria-expanded={open}
        aria-controls={regionId} onClick={() => setOpen((value) => !value)}>
        {open ? 'Masquer la source' : 'Afficher la source'}<span className="etaye-vh"> {source.reference}</span>
      </button>
      {open && (
        <div className="etaye-source__corps" id={regionId} role="region" aria-label={`Source ${source.reference}`}
          aria-busy={remote?.phase === 'loading' ? true : undefined}>
          {(!remote || remote.phase === 'loading') && <p className="etaye-3" role="status" style={{ fontSize: 13, margin: 0 }}>Chargement de la source…</p>}
          {remote && remote.phase !== 'loading' && remote.phase !== 'ready' && (
            <Notice kind="attention" alert title="La source n’a pas pu être chargée"
              actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={() => setAttempt((n) => n + 1)}>Réessayer</button>}>
              La déclaration affichée au-dessus reste visible ; seul l’extrait manque. Rien n’a été supprimé.
            </Notice>
          )}
          {remote?.phase === 'ready' && <SourceBody source={remote.data} back={markerId} />}
        </div>
      )}
    </div>
  );
  return { marker, panel };
}

function SourceBody({ source, back }: { source: Source; back: string }) {
  const event = eventLabel(source.eventDate);
  const state = originState[source.sourceType];
  return <>
    <div className="etaye-source-tete">
      <div className="etaye-apercu__ref">
        {source.reference} · version {source.revision}{source.locator ? ` · ${source.locator}` : ''}
      </div>
      {source.kind && <div className="ui-section">{sourceHeading(source.kind)}</div>}
      <div className="etaye-2" style={{ fontSize: 14 }}>
        {event ? <>Survenu le <span className="etaye-num">{event}</span></> : 'Date de survenue inconnue'}
        {' '}· enregistré le <span className="etaye-num">{formatInstant(source.recordedAt)}</span> · {source.authorLabel}
      </div>
      <div className="etaye-ligne">
        {state.tone !== 'plain' && <span className={`etaye-etat etaye-etat--${state.tone}`}>{state.label}</span>}
        <span className="etaye-3" style={{ fontSize: 12 }}>{originLabel[source.sourceType]}</span>
      </div>
      {source.supersedesReference && (
        <div className="etaye-2" style={{ fontSize: 13 }}>
          Remplace {source.supersedesReference}{source.correctionReason ? ` · motif : ${source.correctionReason}` : ''}
        </div>
      )}
    </div>
    {source.excerpt && <div className="etaye-source-texte">{source.excerpt}</div>}
    <div>
      <a className="etaye-bouton etaye-bouton--discret" href={`#${back}`}
        onClick={(event) => { event.preventDefault(); document.getElementById(back)?.focus(); }}>Revenir à l’élément</a>
    </div>
  </>;
}

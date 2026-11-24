import { useEffect, useRef, useState } from 'react';
import type { Assertion, AssertionCategory, HistorySection } from '../api/generated/types.gen';
import { Notice, Skeleton } from '../patients/patient-ui';
import { clock, conflictNote, eventDay, eventLabel, kindLabel, originLabel, SECTION_ORDER, sectionTitle, sectionWords, statementState } from './evidence';
import { useRecord } from './RecordPage';
import { useSource } from './SourceDisclosure';

/**
 * S-10: the three history sections with their derived state, every statement dated with its author and origin, and
 * the read-only review of a conflict (I-27). Missing information is never shown as a normal finding: a section says
 * "Non renseigné", "Inconnu · à préciser" or "Non demandé", and nothing here concludes an absence.
 */
export function HistoryPage() {
  const ctx = useRecord();
  const [examining, setExamining] = useState<AssertionCategory | undefined>();
  const returnTo = useRef<AssertionCategory | undefined>(undefined);
  const past = ctx.asOf !== undefined;

  useEffect(() => {
    if (!examining && returnTo.current) {
      document.getElementById(`examiner-${returnTo.current}`)?.focus();
      returnTo.current = undefined;
    }
  }, [examining]);

  const { history } = ctx;
  if (history.phase === 'loading') return <Skeleton label="Chargement des rubriques…" />;
  if (history.phase !== 'ready') {
    return (
      <Notice kind="attention" alert title="Les rubriques n’ont pas pu être chargées"
        actions={<button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={ctx.retryHistory}>Réessayer</button>}>
        Ne pas conclure à l’absence d’allergie, d’antécédent ou de traitement : l’état de ces rubriques est inconnu de l’affichage.
        Rien n’a été supprimé.
      </Notice>
    );
  }
  const sections = SECTION_ORDER.map((category) => history.data.sections.find((section) => section.category === category)
    ?? ({ category, state: 'NOT_RECORDED', statements: [], truncated: false } as HistorySection));

  if (examining) {
    const section = sections.find((candidate) => candidate.category === examining)!;
    return <Review section={section} past={past} back={() => { returnTo.current = examining; setExamining(undefined); }} />;
  }

  let number = 0;
  return (
    <div className="etaye-pile" style={{ maxWidth: 880 }}>
      {sections.map((section) => {
        const first = number + 1;
        const listed = listedStatements(section);
        number += listed.length;
        return <SectionPanel key={section.category} section={section} past={past} first={first} listed={listed}
          examine={() => setExamining(section.category)} />;
      })}
    </div>
  );
}

/** The statements worth a line: stated facts, and UNKNOWN or NOT_ASKED ones only beside a stated fact (dated history). */
function listedStatements(section: HistorySection): Assertion[] {
  const stated = section.statements.some((s) => s.assertionKind === 'PRESENT' || s.assertionKind === 'EXPLICITLY_ABSENT');
  return section.statements.filter((s) => s.assertionKind === 'PRESENT' || s.assertionKind === 'EXPLICITLY_ABSENT' || stated);
}

function SectionPanel({ section, past, first, listed, examine }: {
  section: HistorySection; past: boolean; first: number; listed: Assertion[]; examine: () => void;
}) {
  const words = sectionWords(section.category, section, past);
  const id = `h-${section.category}`;
  return (
    <section aria-labelledby={id} className="etaye-panneau">
      <div className="etaye-panneau__corps etaye-pile" style={{ gap: 'var(--espace-2)' }}>
        <div className="etaye-titre-rubrique">
          <h2 className="ui-section" id={id}>{sectionTitle(section.category)}</h2>
          <span className={`etaye-etat etaye-etat--${words.tone === 'plain' ? 'observe' : words.tone}`}>{words.chip}</span>
        </div>
        <p className="etaye-2" style={{ margin: 0, fontSize: 14 }}>{words.sentence}</p>
        {section.state === 'CONFLICTING' && (
          <div className="etaye-bandeau etaye-bandeau--attention" role="note">
            <span aria-hidden="true" className="etaye-icone etaye-icone--18 etaye-icone--alerte" />
            <div>
              <div className="etaye-bandeau__titre">Informations à vérifier</div>
              <div>Deux déclarations datées se contredisent. Aucune n’est supprimée ; rien n’est conclu automatiquement.</div>
            </div>
            <div className="etaye-bandeau__actions">
              <button id={`examiner-${section.category}`} className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={examine}>
                Examiner les sources<span className="etaye-vh"> : {sectionTitle(section.category)}</span>
              </button>
            </div>
          </div>
        )}
        {listed.length > 0 && (
          <ul className="etaye-controle">
            {listed.map((statement, index) => <StatementRow key={statement.id} statement={statement} number={first + index} />)}
          </ul>
        )}
        {section.truncated && (
          <p className="etaye-2" style={{ margin: 0, fontSize: 13 }}>
            D’autres déclarations existent pour cette rubrique ; seules les {section.statements.length} premières sont listées. L’état affiché tient compte de toutes.
          </p>
        )}
      </div>
    </section>
  );
}

function StatementRow({ statement, number }: { statement: Assertion; number: number }) {
  const ctx = useRecord();
  const state = statementState(statement);
  const day = eventDay(statement.eventDate);
  const hasSource = statement.sourceRevisionId !== undefined;
  const source = useSource({ id: statement.sourceRevisionId ?? statement.id, reference: statement.sourceReference ?? '', number },
    ctx.practiceId, ctx.asOf, ctx.expire, 'alone');
  const recorded = clock(statement.recordedAt);
  const heading = day
    ? `${day}${statement.sourceKind ? ` · ${kindLabel(statement.sourceKind)}` : ''}`
    : `Date de survenue inconnue${statement.sourceKind ? ` · ${kindLabel(statement.sourceKind)}` : ''}`;
  return (
    <li style={{ gridTemplateColumns: '24px minmax(0, 1fr)' }}>
      {hasSource ? source.marker : <span aria-hidden="true" />}
      <div>
        <strong>{heading}</strong>
        {statement.text && <div className="etaye-declaration">« {statement.text} »</div>}
        <div className="etaye-ligne">
          <span className={state.tone === 'plain' ? 'etaye-2' : `etaye-etat etaye-etat--${state.tone}`}>{state.label}</span>
          <span className="etaye-3" style={{ fontSize: 12 }}>
            {[statement.authorLabel, statement.sourceType ? originLabel[statement.sourceType] : undefined, statement.sourceReference]
              .filter(Boolean).join(' · ')}
            {!statement.sourceReference && ` enregistré le ${recorded.date} ${recorded.time}`}
          </span>
        </div>
        {hasSource && source.panel}
      </div>
    </li>
  );
}

/** I-27, read-only subset: the two sides of a conflict next to each other, with their sources, nothing merged or resolved. */
function Review({ section, past, back }: { section: HistorySection; past: boolean; back: () => void }) {
  const ctx = useRecord();
  const title = useRef<HTMLHeadingElement>(null);
  useEffect(() => { title.current?.focus({ preventScroll: true }); }, []);
  const sides = section.statements.filter((s) => s.assertionKind === 'PRESENT' || s.assertionKind === 'EXPLICITLY_ABSENT');
  const words = sectionWords(section.category, section, past);
  return (
    <div className="etaye-pile" style={{ maxWidth: 880 }}>
      <section aria-labelledby="h-i27" className="etaye-panneau">
        <div className="etaye-panneau__corps etaye-pile">
          <div className="etaye-titre-rubrique">
            <h2 id="h-i27" ref={title} tabIndex={-1} className="ui-section" style={{ outline: 'none' }}>Informations à vérifier</h2>
            <span className={`etaye-etat etaye-etat--${words.tone === 'plain' ? 'observe' : words.tone}`}>{sectionTitle(section.category)} · lecture seule</span>
          </div>
          <p className="etaye-2" style={{ margin: 0, fontSize: 14 }}>Examen des sources en ligne, sans fenêtre.</p>
          <div className="etaye-cols etaye-cols--egal">
            {sides.map((statement, index) => (
              <Side key={statement.id} statement={statement} number={index + 1}
                label={index === 0 ? 'Plus ancienne' : index === sides.length - 1 ? 'Plus récente' : 'Intermédiaire'} />
            ))}
          </div>
          <div className="etaye-bandeau etaye-bandeau--info" role="note">
            <span aria-hidden="true" className="etaye-icone etaye-icone--18 etaye-icone--info" />
            <div>{conflictNote(sides)}</div>
          </div>
          <div><button className="etaye-bouton etaye-bouton--secondaire" type="button" onClick={back}>Retour aux antécédents et allergies</button></div>
        </div>
      </section>
      <span hidden>{ctx.patientLabel}</span>
    </div>
  );
}

function Side({ statement, number, label }: { statement: Assertion; number: number; label: string }) {
  const ctx = useRecord();
  const state = statementState(statement);
  const source = useSource({ id: statement.sourceRevisionId ?? statement.id, reference: statement.sourceReference ?? '', number },
    ctx.practiceId, ctx.asOf, ctx.expire, 'alone');
  const when = eventLabel(statement.eventDate);
  return (
    <div className="etaye-pile">
      <div className="etaye-ligne"><span className="etaye-num">{source.marker}</span><span className="etaye-2" style={{ fontSize: 13 }}>{label}</span></div>
      <div className="etaye-num" style={{ fontSize: 14 }}>{when ?? 'Date de survenue inconnue'}</div>
      {statement.text && <div className="etaye-declaration">« {statement.text} »</div>}
      <div className="etaye-ligne">
        <span className={state.tone === 'plain' ? 'etaye-2' : `etaye-etat etaye-etat--${state.tone}`}>{state.label}</span>
      </div>
      <span className="etaye-3" style={{ fontSize: 12 }}>
        {[statement.authorLabel, statement.sourceType ? originLabel[statement.sourceType] : undefined,
          statement.sourceReference ? `${statement.sourceReference} v${statement.revision}` : undefined].filter(Boolean).join(' · ')}
      </span>
      {statement.sourceRevisionId && source.panel}
    </div>
  );
}

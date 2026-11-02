# Interaction, modal and panel catalog

Status: Ready for design; no interaction is designed or approved. 2026-11-02. Each I-ID is linked to a surface and requirement. [Business rules](../planning-artifacts/business-rules.md) own transitions; [API contract](../contracts/openapi.yaml) owns transport. French copy below is proposed new copy, existing Figma copy remains untouched.

## Shared behavior contract (applies to every entry)

A page handles long editing/irreversible multi-part review; a drawer preserves evidence context; a dialog holds a bounded decision; inline controls suit local reversible changes. Use one overlay at a time. On mobile, long dialogs/drawers become full-screen pages with explicit back/close actions. Do not shrink the desktop overlay.

Initial focus goes to the dialog/page heading for consequential summaries or the first editable field for simple entry. Keyboard tab order follows reading order; dialogs trap focus; Escape closes only safe non-submitting work. Restore focus to the initiating control, or the nearest surviving logical target after a destructive action. Inline changes do not steal focus; announce confirmed success/failure. Source drawer focuses the source heading and selected locator, restoring the exact marker.

Disable duplicate submits while pending, keep a stable idempotency key, and do not claim success before server commit. If the response is lost, query operation outcome before retry. Show field-specific validation plus summary, retain safe input on errors, and clear password/token fields. Closing dirty work replaces the current content with an integrated discard/stay guard; never open nested dialogs. While pending, closing a view does not cancel committed server work; explain outcome recovery. Failed/partial/unavailable cases retain previous confirmed data. Session expiry uses I-22; stale versions use I-21. Clinical drafts remain scoped to account/practice memory and do not persist in shared browser storage. Destructive actions use explicit audience/target wording and reason fields where required.

## Inventory

| ID | Interaction | Pattern | Surface |
|---|---|---|---|
| [I-01](#i-01) | Invite team member | dialog | S-31 |
| [I-02](#i-02) | Deactivate membership | dialog | S-32 |
| [I-03](#i-03) | Activate patient portal | page | S-03 |
| [I-04](#i-04) | Create or edit patient identity | page | S-08 |
| [I-05](#i-05) | Edit structured notes or patient report | inline | S-10, S-17, S-19, S-27 |
| [I-06](#i-06) | Enter or correct measurement | drawer | S-11 |
| [I-07](#i-07) | Enter or correct laboratory result | drawer | S-12 |
| [I-08](#i-08) | Upload file | drawer | S-13 |
| [I-09](#i-09) | Open supporting source | drawer | S-11, S-12, S-13, S-18, S-20, S-21, S-29 |
| [I-10](#i-10) | Create appointment | dialog | S-07, S-08, S-14, S-16, S-26 |
| [I-11](#i-11) | Move appointment | dialog | S-14, S-16, S-26 |
| [I-12](#i-12) | Cancel appointment | dialog | S-16, S-26 |
| [I-13](#i-13) | Record attendance or no-show | inline | S-16 |
| [I-14](#i-14) | Generate or regenerate draft | dialog | S-18, S-20, S-21 |
| [I-15](#i-15) | Edit generated content | page | S-18, S-20, S-21 |
| [I-16](#i-16) | Validate human-reviewed version | dialog | S-23 |
| [I-17](#i-17) | Publish to named patient | dialog | S-23 |
| [I-18](#i-18) | Replace or withdraw published content | page | S-24 |
| [I-19](#i-19) | Export choices and preview | drawer | S-39 |
| [I-20](#i-20) | Leave with unsaved changes | dialog | Dirty surface or practice switch |
| [I-21](#i-21) | Resolve version conflict | page | Editable surfaces |
| [I-22](#i-22) | Session expiration | page | S-38 |
| [I-23](#i-23) | Failed processing and retry | inline | S-37, S-40 |
| [I-24](#i-24) | Availability exception or practice policy | drawer | S-15, S-33 |
| [I-25](#i-25) | Audit filters and event detail | inline | S-34 |
| [I-26](#i-26) | Choose/reset demo scenario | page | S-01 |
| [I-27](#i-27) | Review missing or conflicting evidence | inline | S-10, S-18, S-21 |
| [I-28](#i-28) | Authorized download outcome | inline | S-13, S-30, S-39 |

<a id="i-01"></a>
## I-01 — Invite team member

- **Traceability / trigger:** FR-001, FR-003; S-31, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** TEAM_MANAGE; recheck resource practice and audience server-side.
- **Title / content / fields:** “Inviter un membre”. Recipient email, explicit capabilities, expiry; no clinical details.
- **Validation:** Validate email, permitted grant and final recipient; resend revokes old token.
- **Actions:** primary “Envoyer l’invitation”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Revoke old invitation on resend; after-commit email, delivery status.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-01.

<a id="i-02"></a>
## I-02 — Deactivate membership

- **Traceability / trigger:** FR-003; S-32, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** TEAM_MANAGE; recheck resource practice and audience server-side.
- **Title / content / fields:** “Désactiver cet accès”. Named member/practice, granted capabilities, reason.
- **Validation:** Current version; cannot remove final active administrator.
- **Actions:** primary “Désactiver l’accès”; secondary “Conserver l’accès”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Suspend selected membership; revoke effective access, retain audit.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-02.

<a id="i-03"></a>
## I-03 — Activate patient portal

- **Traceability / trigger:** FR-001, FR-004; S-03, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** page; Long content, identity-sensitive access or multi-part review needs an addressable full context.
- **Permission:** Intended invited recipient; recheck resource practice and audience server-side.
- **Title / content / fields:** “Activer mon espace patient”. Practice, masked intended identity, token purpose, account/recipient verification.
- **Validation:** Unexpired unused intended token; ambiguous identity stops.
- **Actions:** primary “Activer mon espace”; secondary “Contacter le cabinet”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Create exact portal link only; no staff capability.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-03.

<a id="i-04"></a>
## I-04 — Create or edit patient identity

- **Traceability / trigger:** FR-004; S-08, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** page; Long content, identity-sensitive access or multi-part review needs an addressable full context.
- **Permission:** CLINICAL_WRITE for clinical create/edit; both SCHEDULE_MANAGE and PATIENT_REGISTER for create-only scheduling variant. No clinical access follows registration.
- **Title / content / fields:** “Identité du patient”. Name, birth date, contacts and local duplicate suggestions.
- **Validation:** Required identity, valid date/contact, current version for edit; no automatic merge.
- **Actions:** primary “Enregistrer l’identité”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Create/correct local identity for clinician; create only minimum identity plus empty record for scheduler, then return to I-10. Retain audit/history; no portal link/invitation or appointment side effect.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-04.

<a id="i-05"></a>
## I-05 — Edit structured notes or patient report

- **Traceability / trigger:** FR-005, FR-010, FR-012; S-10, S-17, S-19, S-27, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** inline; A local state change belongs next to its evidence and avoids unnecessary interruption.
- **Permission:** CLINICAL_WRITE or own eligible SELF_PREPARATION; recheck resource practice and audience server-side.
- **Title / content / fields:** “Informations pour la consultation”. Dated section, source type, explicit unknown/negative choices, narrative.
- **Validation:** Field limits, provenance, no invented negatives, encounter lock for preparation.
- **Actions:** primary “Enregistrer”; secondary “Annuler les modifications”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Save scoped fact/preparation revision; acknowledgement does not change reporter.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-05.

<a id="i-06"></a>
## I-06 — Enter or correct measurement

- **Traceability / trigger:** FR-006; S-11, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** drawer; Contextual detail should retain the originating record/statement.
- **Permission:** CLINICAL_WRITE; recheck resource practice and audience server-side.
- **Title / content / fields:** “Saisir une mesure / Corriger la mesure”. Concept, value, unit, observed time, source; correction reason and old value.
- **Validation:** Allowed concept/unit, valid value format/time; current revision; preserve original.
- **Actions:** primary “Enregistrer la mesure”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Append observation or correction; recompute dependent freshness.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-06.

<a id="i-07"></a>
## I-07 — Enter or correct laboratory result

- **Traceability / trigger:** FR-007; S-12, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** drawer; Contextual detail should retain the originating record/statement.
- **Permission:** CLINICAL_WRITE; recheck resource practice and audience server-side.
- **Title / content / fields:** “Saisir un résultat de biologie”. Analyte, qualitative/numeric value, comparator, unit, specimen/result dates, supplied interval/source.
- **Validation:** Numeric/qualitative distinction; no fabricated interval; correction reason.
- **Actions:** primary “Enregistrer le résultat”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Append manual result/correction; no document extraction.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-07.

<a id="i-08"></a>
## I-08 — Upload file

- **Traceability / trigger:** FR-008; S-13, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** drawer; Contextual detail should retain the originating record/statement.
- **Permission:** CLINICAL_WRITE; recheck resource practice and audience server-side.
- **Title / content / fields:** “Ajouter un document”. File picker, type/size, event date or unknown, title/source metadata.
- **Validation:** PDF/PNG/JPEG proposed allowlist; finite size; signature/type check; quarantine.
- **Actions:** primary “Envoyer le document”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Create guarded document and inspection task; pending files unavailable.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-08.

<a id="i-09"></a>
## I-09 — Open supporting source

- **Traceability / trigger:** FR-008, FR-011, FR-016; S-13, S-18, S-21, S-29, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** drawer; Contextual detail should retain the originating record/statement.
- **Permission:** CLINICAL_READ or publication excerpt grant; recheck resource practice and audience server-side.
- **Title / content / fields:** “Source datée”. Selected statement, dated author/source, exact revision/page/excerpt and support relationship.
- **Validation:** Reauthorize exact source; patient sees only curated permitted excerpt.
- **Actions:** primary “Ouvrir le document autorisé”; secondary “Fermer”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Read-only access event; no implicit attachment publication.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-09.

<a id="i-10"></a>
## I-10 — Create appointment

- **Traceability / trigger:** FR-009; S-07, S-08, S-14, S-16, S-26, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** SCHEDULE_MANAGE or own SELF_APPOINTMENTS; recheck resource practice and audience server-side.
- **Title / content / fields:** “Prendre rendez-vous”. Eligible patient/practitioner, offered interval/timezone, scheduling reason. Staff lookup uses S-07 minimal SchedulingPatient variant; if no match, I-04/S-08 registration requires separate PATIENT_REGISTER. Preserve selected slot when returning; no nested dialogs.
- **Validation:** Current availability; start<end; atomic conflict check; cutoff policy.
- **Actions:** primary “Confirmer le rendez-vous”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** One BOOKED appointment; no consultation created.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-10.

<a id="i-11"></a>
## I-11 — Move appointment

- **Traceability / trigger:** FR-009; S-14, S-16, S-26, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** SCHEDULE_MANAGE or own eligible appointment; recheck resource practice and audience server-side.
- **Title / content / fields:** “Déplacer le rendez-vous”. Old/new date, practitioner, timezone, reason.
- **Validation:** Version, policy, target availability/overlap; original retained on failure.
- **Actions:** primary “Confirmer le déplacement”; secondary “Garder ce rendez-vous”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Same identity/time revision; after-commit change notification.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-11.

<a id="i-12"></a>
## I-12 — Cancel appointment

- **Traceability / trigger:** FR-009; S-16, S-26, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** SCHEDULE_MANAGE or own eligible appointment; recheck resource practice and audience server-side.
- **Title / content / fields:** “Annuler ce rendez-vous”. Patient-safe appointment summary and cancellation reason.
- **Validation:** Current version, allowed state/window; explain released slot.
- **Actions:** primary “Annuler le rendez-vous”; secondary “Conserver le rendez-vous”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** CANCELLED terminal; retain history and event.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-12.

<a id="i-13"></a>
## I-13 — Record attendance or no-show

- **Traceability / trigger:** FR-009, FR-012; S-16, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** inline; A local state change belongs next to its evidence and avoids unnecessary interruption.
- **Permission:** SCHEDULE_MANAGE; recheck resource practice and audience server-side.
- **Title / content / fields:** “Présence au rendez-vous”. Check-in, complete/no-show choices with time and reason.
- **Validation:** Allowed transition; no-show after start; no contradictory encounter state.
- **Actions:** primary “Enregistrer la présence”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Apply attendance only; no invented clinical report.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-13.

<a id="i-14"></a>
## I-14 — Generate or regenerate draft

- **Traceability / trigger:** FR-011, FR-013; S-18, S-20, S-21, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** Authorized clinical writer; recheck resource practice and audience server-side.
- **Title / content / fields:** “Générer un brouillon sourcé”. Purpose, selected dated inputs, missing/contradictory warnings, existing draft notice.
- **Validation:** Quota, source snapshot, idempotency, preserve human text; no direct publish.
- **Actions:** primary “Générer un nouveau brouillon”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Queue scoped task; output is new DRAFT candidate.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-14.

<a id="i-15"></a>
## I-15 — Edit generated content

- **Traceability / trigger:** FR-011, FR-013, FR-014; S-18, S-20, S-21, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** page; Long content, identity-sensitive access or multi-part review needs an addressable full context.
- **Permission:** CLINICAL_WRITE; recheck resource practice and audience server-side.
- **Title / content / fields:** “Relire et corriger”. Generated baseline, human editor, source markers, unsupported statement flags.
- **Validation:** Revision guard; source support check; approved version creates new draft.
- **Actions:** primary “Enregistrer les corrections”; secondary “Revenir à la lecture”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Preserve correction/baseline/version authorship.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-15.

<a id="i-16"></a>
## I-16 — Validate human-reviewed version

- **Traceability / trigger:** FR-014; S-23, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** CLINICAL_VALIDATE; recheck resource practice and audience server-side.
- **Title / content / fields:** “Valider cette version”. Exact version, patient, source checklist, missing/contradiction disposition.
- **Validation:** Current reviewed snapshot, required metadata, source support; validator identity.
- **Actions:** primary “Valider la version”; secondary “Poursuivre la relecture”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Immutable VALIDATED; explicitly still not patient-visible.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-16.

<a id="i-17"></a>
## I-17 — Publish to named patient

- **Traceability / trigger:** FR-015; S-23, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** PATIENT_PUBLISH and clinical access; recheck resource practice and audience server-side.
- **Title / content / fields:** “Publier pour Claire Martin”. Exact eligible version, patient audience preview, selected excerpts/attachments.
- **Validation:** VALIDATED CURRENT; verified link/audience; current guard; clinician brief forbidden.
- **Actions:** primary “Publier pour le patient”; secondary “Garder en interne”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Create PUBLISHED projection/grants; minimal post-commit notification.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-17.

<a id="i-18"></a>
## I-18 — Replace or withdraw published content

- **Traceability / trigger:** FR-015; S-24, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** page; Long content, identity-sensitive access or multi-part review needs an addressable full context.
- **Permission:** PATIENT_PUBLISH; recheck resource practice and audience server-side.
- **Title / content / fields:** “Remplacer ou retirer la publication”. Current publication, replacement version or withdrawal reason, audience consequences.
- **Validation:** Current guard; replacement validated/current; withdrawal reason required.
- **Actions:** primary “Remplacer la publication / Retirer l’accès”; secondary “Conserver la publication”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Atomic replacement or WITHDRAWN; downloaded copies cannot be recalled.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-18.

<a id="i-19"></a>
## I-19 — Export choices and preview

- **Traceability / trigger:** FR-017; S-39, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** drawer; Contextual detail should retain the originating record/statement.
- **Permission:** Authorized version/audience reader; recheck resource practice and audience server-side.
- **Title / content / fields:** “Exporter un document”. Template, exact status/version, audience, sources and A4 preview.
- **Validation:** Reauthorize snapshot; patient only current publication; draft labeling.
- **Actions:** primary “Créer le PDF”; secondary “Fermer”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Guarded export artifact; task outcome distinct from business state.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-19.

<a id="i-20"></a>
## I-20 — Leave with unsaved changes

- **Traceability / trigger:** FR-005, FR-010, FR-012, FR-014; Dirty surface or practice switch, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** dialog; A bounded decision needs focused fields and explicit consequences.
- **Permission:** Current editor; recheck resource practice and audience server-side.
- **Title / content / fields:** “Modifications non enregistrées”. What is unsaved, destination; safe save or discard/stay choices.
- **Validation:** No success before save; no nested dialog; current permission.
- **Actions:** primary “Enregistrer et continuer”; secondary “Rester sur cette page”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Discard is explicit destructive alternative; preserve safe account/practice memory.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-20.

<a id="i-21"></a>
## I-21 — Resolve version conflict

- **Traceability / trigger:** FR-009, FR-014; Editable surfaces, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** page; Long content, identity-sensitive access or multi-part review needs an addressable full context.
- **Permission:** Current authorized editor; recheck resource practice and audience server-side.
- **Title / content / fields:** “Une version plus récente existe”. Local text, server author/time/version and readable diff.
- **Validation:** 412 on stale write; explicit reconciliation; no automatic overwrite.
- **Actions:** primary “Comparer et préparer une nouvelle version”; secondary “Conserver ma copie en mémoire”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** New edit based on fresh token; never last-write-wins.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-21.

<a id="i-22"></a>
## I-22 — Session expiration

- **Traceability / trigger:** FR-001; S-38, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** page; Long content, identity-sensitive access or multi-part review needs an addressable full context.
- **Permission:** Previously authenticated actor; recheck resource practice and audience server-side.
- **Title / content / fields:** “Votre session a expiré”. Safe return destination; locally unsaved work notice.
- **Validation:** Reauthenticate; recheck current grants; never resend mutation automatically.
- **Actions:** primary “Se reconnecter”; secondary “Quitter la session”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** New session then explicit safe recovery, clear secrets/context on exit.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-22.

<a id="i-23"></a>
## I-23 — Failed processing and retry

- **Traceability / trigger:** FR-013, FR-017, FR-019; S-37, S-40, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** inline; A local state change belongs next to its evidence and avoids unnecessary interruption.
- **Permission:** Authorized requester/operator; recheck resource practice and audience server-side.
- **Title / content / fields:** “Le traitement a échoué”. Safe reason, attempt/time, prior valid output, retry eligibility.
- **Validation:** Reauthorize, cap attempts/backoff; ambiguous outcome checked by operation identity.
- **Actions:** primary “Réessayer si autorisé”; secondary “Revenir au dossier”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** New bounded attempt; no duplicate output overwrite or email/business effect.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-23.

<a id="i-24"></a>
## I-24 — Availability exception or practice policy

- **Traceability / trigger:** FR-009, FR-018; S-15, S-33, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** drawer; Contextual detail should retain the originating record/statement.
- **Permission:** Own practitioner or explicitly authorized manager; recheck resource practice and audience server-side.
- **Title / content / fields:** “Modifier les disponibilités”. Timezone, interval/recurrence/exception and impacted booking list.
- **Validation:** Version, DST offset, serialization with booking; existing commitments explicit.
- **Actions:** primary “Enregistrer les disponibilités”; secondary “Annuler”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** New effective availability; no silent cancellation.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-24.

<a id="i-25"></a>
## I-25 — Audit filters and event detail

- **Traceability / trigger:** FR-018; S-34, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** inline; A local state change belongs next to its evidence and avoids unnecessary interruption.
- **Permission:** ADMIN_AUDIT or CLINICAL_AUDIT; recheck resource practice and audience server-side.
- **Title / content / fields:** “Détail de l’événement”. Allowed actor/time/resource/version/correlation/outcome.
- **Validation:** Allowlisted filters, bounded pagination and capability projection.
- **Actions:** primary “Afficher le détail autorisé”; secondary “Fermer”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Read event only; no clinical text in admin view.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-25.

<a id="i-26"></a>
## I-26 — Choose/reset demo scenario

- **Traceability / trigger:** FR-019; S-01, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** page; Long content, identity-sensitive access or multi-part review needs an addressable full context.
- **Permission:** Visitor scenario grant or operator reset; recheck resource practice and audience server-side.
- **Title / content / fields:** “Démonstration avec données fictives”. Scenario cast, practice, fixture version, limits; reset effects.
- **Validation:** Rate limit/reset authorization; invalidate stale sessions; no real-data input.
- **Actions:** primary “Ouvrir la démonstration / Réinitialiser”; secondary “Retour”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Enter bounded synthetic context or reset atomically with notice.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-26.

<a id="i-27"></a>
## I-27 — Review missing or conflicting evidence

- **Traceability / trigger:** FR-005, FR-011, FR-014; S-10, S-18, S-21, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** inline; A local state change belongs next to its evidence and avoids unnecessary interruption.
- **Permission:** CLINICAL_READ/WRITE reviewer; recheck resource practice and audience server-side.
- **Title / content / fields:** “Informations à vérifier”. Dated opposing assertions and source types, explicit missing field.
- **Validation:** No automatic resolution or normality inference; correction needs evidence/reason.
- **Actions:** primary “Examiner les sources”; secondary “Conserver à vérifier”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Record human disposition separately; original reports preserved.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-27.

<a id="i-28"></a>
## I-28 — Authorized download outcome

- **Traceability / trigger:** FR-008, FR-016, FR-017; S-13, S-30, S-39, invoked by its named action. See shared business rules and access matrix.
- **Pattern / reason:** inline; A local state change belongs next to its evidence and avoids unnecessary interruption.
- **Permission:** Authorized file/publication reader; recheck resource practice and audience server-side.
- **Title / content / fields:** “Télécharger cette version”. File name/type/size/version/audience and download status.
- **Validation:** Authorization at delivery; expired/revoked access denies; no public file URL.
- **Actions:** primary “Télécharger”; secondary “Fermer”. Any destructive choice is explicitly labeled, separate from safe cancel, and never default-focused.
- **Business result:** Audited delivery; downloaded copies cannot be recalled.
- **Loading/error/duplicate behavior:** shared contract; show safe processing state and exact failure without erasing entered information. Invalid input has field error; permission denial removes protected content. Ambiguous response checks operation outcome.
- **Closing/input/focus/mobile:** shared contract applies fully; preserve safe entered work on validation/network failure, require explicit discard for dirty work, restore trigger focus. This long action becomes a full-width mobile page when content does not fit.
- **Acceptance:** keyboard completion and error recovery work; result follows the exact allowed lifecycle, no duplicate side effect, no premature visibility. Capture opened desktop/tablet/mobile review evidence under I-28.

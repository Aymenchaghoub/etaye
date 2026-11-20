# Roadmap and increment allocation

Status: Proposal derived from the user decisions of 2026-11-17 (UD-12 to UD-37), amended on 2026-11-19 (UD-38 to UD-41). The allocation itself is
[ASSUMPTION A-16] until Aymen reorders it. Development is authorized for stories 10.1 (UD-41) and 10.2 (UD-45) only; UD-28 still applies to every other story. The machine-readable source is
[roadmap-assignments.json](roadmap-assignments.json); the tables below are rendered from it and
`tools/validate_docs.py` fails if a story, design family or API operation is left unassigned.

Purpose: give long-term visibility. Nothing is left implicit: what each increment builds, what it deliberately
leaves for later, why, and what triggers the resumption. The [deferred register](deferred-work.md) owns the
rationale and trigger of each deferred item; this document owns the order.

Sources consulted in full for this version (2026-11-17): [PRD](prd/prd.md), [business rules](business-rules.md),
[access matrix](access-matrix.md), [functional specification](functional-specification.md),
[decision register](decisions-assumptions.md), [architecture spine](architecture/ARCHITECTURE-SPINE.md),
[data model](data-model.md), [data dictionary](data-dictionary.md), [AI specification](ai-specification.md),
[security and privacy](security-privacy.md), [NFRs](non-functional-requirements.md),
[test strategy](../test-artifacts/test-strategy.md), [hosting assessment](../operations/hosting-cost-assessment.md),
[API contract](../contracts/README.md), the nine original epics and the screen/interaction/PDF/email catalogs
(inventories). Design evidence under `docs/design/` was read, not modified.

## 1. Increments at a glance

| Increment | Name | Stories | Goal |
|---|---|---|---|
| 1 | Red-thread slice (web) | 33 | One complete, demonstrable journey on the web: invited access, practice context, record reading, visit preparation, sourced brief, consultation, human-reviewed report, explicit publication, patient reading, isolation proof, with the multi-client foundations in place. |
| 2 | Web completion | 7 | Everything the specification requires on the web that increment 1 deliberately left out: team administration, availability and rescheduling, data entry screens, uploads and attachments, remaining PDFs and emails, audit and profile. |
| 3 | Mobile (installable web app, then Android app) | 4 | The same product usable as an installed app: installable PWA, native session transport, a React Native Android app built with Expo (ADR-010), content-free push notifications. |
| 4 | Release engineering, iOS demo gate and quality extensions | 1 | iOS build and distribution gate, promotion pipeline, optional real-model evaluation, advanced audit and device privacy options. |

Increments 2 and 3 both depend only on increment 1 and may run in either order; the order is 3 then 2 (UD-40, ADR-011,
replacing the order of UD-37). Increment 3 needs a stable HTTPS origin, hence the hosting decision of story 10.3. Relative size
[ASSUMPTION]: increment 1 is the largest, increment 2 about two thirds of it, increments 3 and 4 smaller.

## 2. Gates, owners and what blocks what

| Gate | Owner | Blocks | State |
|---|---|---|---|
| Development authorization | Aymen | Every implementation story | Given for stories 10.1 (UD-41) and 10.2 (UD-45) only; not given for the others (UD-28) |
| Core design families reviewed (UD-16) | Aymen, with Codex for the Figma side | Increment 1 build of those families | Pending; named exceptions for the UI-free stories 10.1 (UD-41) and 10.2 (UD-45) |
| Increment 1 exit review | Aymen | Increments 2 and 3 | Future |
| ADR-008 and ADR-009 accepted | Aymen | Stories 10.2 (API meta), 10.4 to 10.8 | Done (UD-29); contract operations are added before stories 10.2 and 10.5 |
| ADR-010 accepted (native client: React Native with Expo) | Aymen | Stories 10.2 and 10.5 to 10.8 | Done (UD-38, UD-39) |
| Native patient screens designed and reviewed (DF-19) | Aymen, design owner | Story 10.6 build | Pending |
| Hosting decision (story 10.3, UD-25) | Aymen | Story 10.4 onward, any store step | Future |
| Store publication (D-15) | Aymen | Any store submission | Not planned (UD-39) |
| iOS native demonstration | Aymen | Story 10.8 build | A borrowed Mac gives the simulator (A-17, Xcode version To verify); a physical iPhone build still needs an explicit Apple-program decision |

## 3. Increment 1: red-thread slice (web)

**Demonstration journey** (all data synthetic, `etaye-design-v1`):

1. A visitor chooses the fictional actor Dr Nora Lefebvre at Cabinet des Tilleuls (S-01, I-26), signs in (S-02) and
   lands on the day view (S-06).
2. Nora opens Claire Martin's visit of 3 November (S-14, S-16), reads her record (S-09, S-10, S-11, S-12 read-only)
   and sees the conflicting allergy sources and the missing treatments (I-27).
3. Nora reads Claire's preparation (S-17) and acknowledges that exact submission.
4. Nora opens the clinician brief (S-18), follows a citation to the exact dated source (I-09, S-13 read) and
   acknowledges the brief version. The brief has no validation or publication path (UD-22).
5. Nora conducts the encounter (S-19), requests a report draft (I-14, S-40), corrects an unsupported generated
   sentence (I-15, S-21), compares versions (S-22) and validates the exact version (I-16, S-23).
6. Nora prepares and validates the separate patient explanation, publishes it to Claire (I-17), then replaces
   version 1 by version 2 (I-18, S-24). The notification email M-05 lands in a local mail sink.
7. Claire signs in (S-02, S-05), reads the published explanation and its approved excerpts (S-25, S-28, S-29),
   books and cancels a slot of the seeded grid (S-26), submits a preparation (S-27) and exports P-03 (S-39, I-19).
8. Isolation and failure demonstrations: a Cabinet du Canal practitioner and Mila (administration only) are refused
   (S-36); Mila looks up and registers a patient for scheduling under PD-001 (S-07, S-08 restricted variants);
   Dr Sami Rahmani switches practice (S-05, I-20); a double booking and a stale edit are refused (I-21);
   Louis Bernard's AI timeout (S-37, I-23) and an expired session (S-38, I-22) recover honestly.

**Build order (ADR-011, UD-40).** Increment 1 is built in six milestones, M1 secure skeleton, M2 record and schedule, M3 preparation and brief, M4 consultation to validated report, M5 publication and patient reading, M6 proof and release evidence. Each is demonstrable, has effort budgets instead of dates and a cut rule; the stories, tests and budgets of each milestone are in ADR-011.

**Built from decisions:** PD-001 registration (UD-17), the acknowledgement operations (UD-18, UD-22),
multi-membership switching (UD-19), the fixed slot grid with atomic booking and cancellation (UD-21), overlap
forbidden for every actor (UD-23), demo files in PostgreSQL or a volume (UD-24), €0 first (UD-25).

**Not in increment 1 (and where it goes):**

| Left out | Why | Goes to |
|---|---|---|
| Team administration UI, invitation issuance, M-01, M-02 | Needs no part of the red thread; increment 1 activates seeded invitations | Inc 2, D-23 |
| Practice settings and booking policy | Policy defaults are a decision (A-07) | Inc 2, D-21 |
| Availability management | Seeded grid is enough to book | Inc 2, D-20 |
| Rescheduling, no-show | Cancel and attendance cover the demo | Inc 2, D-22 |
| Measurement and laboratory entry | Seeded values and lineage show the model | Inc 2, D-24 |
| Uploads and patient attachments | One seeded document; file adapter decision | Inc 2, D-25 |
| PDFs P-01, P-02 | One PDF proves the export boundary | Inc 2, D-27 |
| Audit screen, profile screen | Audit events are written from story 10.1 on | Inc 2, D-30, D-31 |
| Installable web app, native session, Android app, push | Needs a stable origin, ADR-008 and ADR-010 | Inc 3 |
| iOS demo build, promotion pipeline, Sonar, real AI evaluation, biometric lock | Costs, a Mac or the Apple program, and policies need Aymen | Inc 4 |
| D-01 to D-14, D-16, D-17, D-28 | Existing scope exclusions or no trigger yet | Unscheduled |

**Exit criteria (increment 1):** the acceptance designs A and B of every increment 1 story pass on a real
PostgreSQL, API and UI stack with deterministic AI, email and scan boundaries; the CORE families have opened visual
evidence and the SAMPLED families their sample; the scripted demonstration journey passes in CI; the isolation
report is published; the hosting benchmark and decision are recorded; no paid service was activated; no clinical
validation or compliance claim exists anywhere.

**Budget:** €0 recurring. The hosting decision may introduce a VPS (about €4 to €7 per month, To verify at
purchase) only if Aymen chooses it.

**Risks:** the Java 25, Spring Boot 4.1.2, React 19.3, Vite 8, Node 26 and PostgreSQL 18.6 combination is untested
(spike in story 10.1 with the ADR-002 fallbacks); memory on a small host is unmeasured (story 10.3); PD-001 and
multi-membership enlarge the first increment relative to the leanest slice.

## 4. Increment 2: web completion

Delivers, in this order of value: team administration and invitation issuance (2.1, 2.2, M-01, M-02); practice
settings and booking policy (2.3, D-21); availability management (4.1, D-20); rescheduling and no-show (D-22);
measurement and laboratory entry (D-24); uploads and patient attachments (3.5, D-25); PDFs P-01 and P-02
(8.1, D-27); audit screen and profile (2.4, D-31). Exit criteria: the acceptance designs of those stories pass in
the same real-stack conditions, the adversarial matrix covers each new resource group, and the specification's
twenty requirements are covered on the web.

## 5. Increment 3: mobile

Delivers the installable web app (10.4), the native session transport and device registry (10.5), the Android
app (10.6), a React Native app built with Expo (ADR-010), and content-free push notifications (10.7).
Prerequisites: increment 1 exit, accepted ADR-008, ADR-009 and ADR-010, a stable HTTPS origin, the native patient
screens designed and reviewed (DF-19), the four planned native contract operations added to the contract (see
section 8). Exit criteria: the app installs on Android Chrome and iOS Safari in standalone mode (opened
evidence); the Android app on an emulator signs in, reads a published explanation and shares its PDF; no
clinical data is at rest in the app sandbox, including the temporary PDF copy (inspected); a push payload
carries no clinical text; real-device evidence is claimed only when a real device was used. Budget: €0 for the
web app, an Android emulator and package-file distribution; a domain for universal links is a decision (D-32);
no store fee is planned (UD-39).

## 6. Increment 4: release engineering, iOS, stores and quality extensions

Delivers the iOS build and distribution gate (10.8), the promotion pipeline, SBOM and signing and the
optional SonarQube Cloud analysis (D-29), the optional real-model evaluation and live provider (D-26), advanced
audit filters (D-30) and device privacy options (D-18). A native iOS demonstration needs a Mac for the
simulator, or the Apple program for a device: Linux supports neither the iOS Simulator nor an iPhone build, and a
physical iPhone with Expo Go needs the program as well (Expo documentation, read 2026-11-19). The installed web
app serves iOS users for free meanwhile. Costs kept for a possible reopening of D-15 (not planned, UD-39): Apple
Developer Program US$99 per year (official page, 2026-11-17); Google Play registration fee To verify, plus a
closed test with at least 12 testers over 14 days for new personal accounts; real-model cost To verify.

## 7. Deferred and unscheduled work

Every deferred item has rationale, dependencies, impact and an explicit trigger in the
[deferred register](deferred-work.md). Order of resumption:

| ID | Work | Increment | Trigger |
|---|---|---|---|
| D-15 | Store publication (Google Play, App Store) | Not planned (UD-39) | Aymen decides to publish; costs, review policies and an always-on backend must then be accepted first |
| D-16 | Native practitioner application | unscheduled | Aymen requests it after the patient app and slice usage |
| D-17 | Offline clinical mode and device cache | unscheduled | A concrete offline use case plus an encrypted-store security design |
| D-18 | Biometric lock, app-switcher privacy screen, remote wipe | 4 | Android app exists and Aymen selects the option |
| D-19 | Native session duration and refresh policy (A-15) | 3 | Decided when ADR-008 is accepted |
| D-20 | Rich availability: recurring rules, exceptions, DST, impacted bookings | 2 | Increment 1 delivered with its fixed grid |
| D-21 | Booking and cancellation window policy (A-07 remainder) | 2 | Increment 2 starts; Aymen sets defaults |
| D-22 | Rescheduling and no-show marking | 2 | Increment 2 starts |
| D-23 | Team administration UI, invitation issuance, M-01/M-02 | 2 | Increment 2 starts |
| D-24 | Measurement and laboratory entry and correction screens | 2 | Increment 2 starts |
| D-25 | Uploads and patient attachments | 2 | Increment 2 starts and the file adapter is chosen |
| D-26 | Real-model evaluation, optional live provider and AI rephrasing | 4 | Aymen funds and approves a provider, corpus size decided |
| D-27 | PDF P-01 and P-02 | 2 | Increment 2 starts |
| D-28 | Object storage, real antivirus, signed URLs, in-browser PDF excerpt viewer | unscheduled | Persistent disk or database storage proves insufficient, or larger files are accepted |
| D-29 | Promotion pipeline, SBOM and signing, SonarQube Cloud, rollback rehearsal | 4 | Application code is stable and Sonar binding is verified |
| D-30 | Advanced audit filters and clinical-audit detail | 4 | Basic audit (increment 2) is in use |
| D-31 | Profile and preferences screen | 2 | Increment 2 starts |
| D-32 | Custom domain, universal links, privacy-policy page | 3 | A store or deep-link decision is made |
| D-33 | Dynamic security scan (HawkScan) in local runs and CI | 1 (after the spike) | The backend runs locally after the spike and Aymen opens the StackHawk trial |

D-01 to D-14 keep their existing triggers and stay unscheduled.

## 8. Assignment tables

### 8.1 Stories (45)

| Inc | Story | Title | Depends on | Deferred parts |
|---|---|---|---|---|
| 1 | [10.1](epics/epic-10.md) | Walking skeleton and secure request path | — | — |
| 1 | [1.1](epics/epic-1.md) | Activate an intended invitation | 10.1 | Issuing, resending and revoking invitations (issueInvitation, revokeInvitation, resendInvitation) and the invitation emails M-01 and M-02; increment 1 activates seeded pending, expired, revoked and recipient-mismatched invitations → Inc 2 (D-23) |
| 1 | [1.2](epics/epic-1.md) | Sign in, recover and expire safely | 10.1 | — |
| 1 | [9.1](epics/epic-9.md) | Enter and reset bounded synthetic journeys | 10.1 | — |
| 1 | [9.2](epics/epic-9.md) | Produce real-stack quality and release evidence | 10.1 | Promotion pipeline, SBOM and provenance signing, SonarQube Cloud analysis → Inc 4 (D-29) |
| 1 | [10.2](epics/epic-10.md) | Generated client, token bridge and multi-client foundations | 10.1 | — |
| 1 | [1.3](epics/epic-1.md) | Select and switch practice context | 1.2 | — |
| 1 | [9.3](epics/epic-9.md) | Recover unavailable services and releases | 10.1, 1.2 | Release rollback rehearsal → Inc 4 (D-29) |
| 1 | [1.4](epics/epic-1.md) | Demonstrate server-side resource isolation | 10.1, 1.3 | Extending the adversarial matrix to each resource group added later (uploads, availability, audit, devices); every later story adds its resource to the matrix → Inc 2 (10.1) |
| 1 | [3.1](epics/epic-3.md) | Find and register a local patient | 1.3 | — |
| 1 | [3.2](epics/epic-3.md) | Explore chronology and explicit assertions | 3.1 | — |
| 1 | [4.2](epics/epic-4.md) | Book a real appointment | 3.1 | — |
| 1 | [3.3](epics/epic-3.md) | Enter and correct measurements | 3.2 | Entering and correcting measurements (I-06; appendMeasurement, correctMeasurement) → Inc 2 (D-24) |
| 1 | [3.4](epics/epic-3.md) | Enter and correct laboratory results | 3.2 | Entering and correcting laboratory results (I-07; appendLabResult, correctLabResult) → Inc 2 (D-24) |
| 1 | [4.3](epics/epic-4.md) | Reschedule or cancel an appointment | 4.2 | Rescheduling and patient move (I-11; rescheduleAppointment, moveOwnAppointment) → Inc 2 (D-22) |
| 1 | [4.4](epics/epic-4.md) | Record arrival, completion or no-show | 4.2 | No-show marking (appointmentNoShow) → Inc 2 (D-22) |
| 1 | [4.5](epics/epic-4.md) | Prepare a visit and inspect patient reports | 4.2 | — |
| 1 | [6.1](epics/epic-6.md) | Conduct an encounter with or without appointment | 3.2 | — |
| 1 | [5.2](epics/epic-5.md) | Generate a bounded clinician brief | 3.2, 3.3, 10.1 | Optional AI rephrasing of the brief and any real model provider → Inc 4 (D-26) |
| 1 | [5.1](epics/epic-5.md) | Read a daily preparation workspace | 4.2, 5.2 | — |
| 1 | [5.3](epics/epic-5.md) | Inspect dated statement support | 5.2 | — |
| 1 | [5.4](epics/epic-5.md) | Handle changed sources and failed tasks | 5.2 | — |
| 1 | [6.2](epics/epic-6.md) | Draft a consultation report | 6.1, 5.2 | — |
| 1 | [6.3](epics/epic-6.md) | Review, correct and compare versions | 6.2 | — |
| 1 | [6.4](epics/epic-6.md) | Validate the exact reviewed version | 6.3 | — |
| 1 | [7.1](epics/epic-7.md) | Prepare a separately reviewed patient explanation | 6.4 | — |
| 1 | [7.2](epics/epic-7.md) | Publish selected validated content | 7.1 | — |
| 1 | [7.3](epics/epic-7.md) | Replace or withdraw a publication | 7.2 | — |
| 1 | [7.4](epics/epic-7.md) | Read only own published information | 7.2, 1.2 | — |
| 1 | [7.5](epics/epic-7.md) | Manage patient appointments and authorized files | 4.2, 4.3, 7.2 | Authorized publication attachments (S-30; downloadPublishedAttachment); increment 1 covers the patient appointment screens only → Inc 2 (D-25) |
| 1 | [8.2](epics/epic-8.md) | Export reports and patient explanations | 7.2 | P-02 consultation report PDF → Inc 2 (D-27) |
| 1 | [8.3](epics/epic-8.md) | Deliver minimal transactional notifications | 1.2, 4.2, 7.2 | Team invitation email M-01 and patient activation email M-02 (they need invitation issuance) → Inc 2 (D-23) |
| 1 | [10.3](epics/epic-10.md) | Hosting benchmark and decision | 7.4, 9.1, 9.3 | — |
| 2 | [2.1](epics/epic-2.md) | Invite and manage team permissions | 1.2, 1.3 | — |
| 2 | [2.3](epics/epic-2.md) | Maintain operational practice settings | 1.3 | — |
| 2 | [2.4](epics/epic-2.md) | Inspect scoped audit and own preferences | 10.1, 1.2 | Advanced audit filters and clinical-audit detail beyond actor, event type and period → Inc 4 (D-30) |
| 2 | [3.5](epics/epic-3.md) | Upload and inspect authorized documents | 3.2, 10.1 | — |
| 2 | [4.1](epics/epic-4.md) | Define availability and exceptions | 1.3 | — |
| 2 | [8.1](epics/epic-8.md) | Export a practitioner-only brief | 5.2 | — |
| 2 | [2.2](epics/epic-2.md) | Deactivate access without losing accountability | 2.1 | — |
| 3 | [10.4](epics/epic-10.md) | Installable web app (PWA) | 10.2, 10.3 | — |
| 3 | [10.5](epics/epic-10.md) | Native session transport and device registry | 1.2, 10.2 | — |
| 3 | [10.6](epics/epic-10.md) | Native Android app (React Native) | 10.4, 10.5 | — |
| 3 | [10.7](epics/epic-10.md) | Content-free push notifications | 10.5, 10.6, 8.3 | — |
| 4 | [10.8](epics/epic-10.md) | iOS build and distribution gate | 10.6 | — |

### 8.2 Design families (76)

CORE families need the full evidence of the named design exception (UD-16); every other family is SAMPLED
(desktop-first in code, automated accessibility checks, sampled visual evidence).

| ID | Kind | Name | Increment | Evidence | Parts left for later |
|---|---|---|---|---|---|
| I-01 | interaction | Invite team member | 2 | SAMPLED | — |
| I-02 | interaction | Deactivate membership | 2 | SAMPLED | — |
| I-03 | interaction | Activate patient portal | 1 | SAMPLED | — |
| I-04 | interaction | Create or edit patient identity | 1 | SAMPLED | — |
| I-05 | interaction | Edit structured notes or patient report | 1 | SAMPLED | — |
| I-06 | interaction | Enter or correct measurement | 2 | SAMPLED | — |
| I-07 | interaction | Enter or correct laboratory result | 2 | SAMPLED | — |
| I-08 | interaction | Upload file | 2 | SAMPLED | — |
| I-09 | interaction | Open supporting source | 1 | CORE | — |
| I-10 | interaction | Create appointment | 1 | SAMPLED | — |
| I-11 | interaction | Move appointment | 2 | SAMPLED | — |
| I-12 | interaction | Cancel appointment | 1 | SAMPLED | — |
| I-13 | interaction | Record attendance or no-show | 1 | SAMPLED | No-show marking → Inc 2 (D-22) |
| I-14 | interaction | Generate or regenerate draft | 1 | SAMPLED | — |
| I-15 | interaction | Edit generated content | 1 | CORE | — |
| I-16 | interaction | Validate human-reviewed version | 1 | CORE | — |
| I-17 | interaction | Publish to named patient | 1 | CORE | — |
| I-18 | interaction | Replace or withdraw published content | 1 | SAMPLED | — |
| I-19 | interaction | Export choices and preview | 1 | SAMPLED | Export choices for P-01 and P-02 → Inc 2 (D-27) |
| I-20 | interaction | Leave with unsaved changes | 1 | SAMPLED | — |
| I-21 | interaction | Resolve version conflict | 1 | SAMPLED | — |
| I-22 | interaction | Session expiration | 1 | SAMPLED | — |
| I-23 | interaction | Failed processing and retry | 1 | SAMPLED | — |
| I-24 | interaction | Availability exception or practice policy | 2 | SAMPLED | — |
| I-25 | interaction | Audit filters and event detail | 2 | SAMPLED | Advanced audit filters → Inc 4 (D-30) |
| I-26 | interaction | Choose/reset demo scenario | 1 | SAMPLED | — |
| I-27 | interaction | Review missing or conflicting evidence | 1 | SAMPLED | — |
| I-28 | interaction | Authorized download outcome | 1 | SAMPLED | — |
| M-01 | email | Team invitation | 2 | SAMPLED | — |
| M-02 | email | Patient activation | 2 | SAMPLED | — |
| M-03 | email | Access recovery | 1 | SAMPLED | — |
| M-04 | email | Appointment event | 1 | SAMPLED | — |
| M-05 | email | Publication event | 1 | SAMPLED | — |
| P-01 | PDF | Practitioner pre-visit brief | 2 | SAMPLED | — |
| P-02 | PDF | Consultation report | 2 | SAMPLED | — |
| P-03 | PDF | Published patient explanation | 1 | SAMPLED | — |
| S-01 | screen | Showcase and demo entry | 1 | CORE | — |
| S-02 | screen | Login | 1 | CORE | — |
| S-03 | screen | Invitation and account activation | 1 | SAMPLED | Team and patient invitation issuance variants → Inc 2 (D-23) |
| S-04 | screen | Access recovery | 1 | SAMPLED | — |
| S-05 | screen | Practice and access-context selection | 1 | CORE | — |
| S-06 | screen | Practitioner home and daily preparation | 1 | CORE | — |
| S-07 | screen | Patient directory and search | 1 | SAMPLED | — |
| S-08 | screen | Patient identity creation and editing | 1 | SAMPLED | — |
| S-09 | screen | Patient record and longitudinal timeline | 1 | CORE | — |
| S-10 | screen | History, allergies and reported treatments | 1 | CORE | — |
| S-11 | screen | Vital signs, measurements and trends | 1 | SAMPLED | Entry and correction drawer I-06 → Inc 2 (D-24) |
| S-12 | screen | Laboratory results and reference intervals | 1 | SAMPLED | Entry and correction drawer I-07 → Inc 2 (D-24) |
| S-13 | screen | Record documents and clinician source viewer | 1 | SAMPLED | Upload drawer I-08 and inspection states → Inc 2 (D-25) |
| S-14 | screen | Practice schedule | 1 | SAMPLED | Availability-aware schedule with exceptions → Inc 2 (D-20) |
| S-15 | screen | Availability and scheduling settings | 2 | SAMPLED | — |
| S-16 | screen | Appointment details | 1 | SAMPLED | Reschedule and no-show variants → Inc 2 (D-22) |
| S-17 | screen | Practitioner view of patient visit preparation | 1 | SAMPLED | — |
| S-18 | screen | Sourced pre-visit brief | 1 | CORE | — |
| S-19 | screen | Consultation workspace and structured notes | 1 | CORE | — |
| S-20 | screen | Consultation report editor | 1 | SAMPLED | — |
| S-21 | screen | AI draft review and processing result | 1 | CORE | — |
| S-22 | screen | Version history and comparison | 1 | CORE | — |
| S-23 | screen | Human validation and patient publication review | 1 | CORE | — |
| S-24 | screen | Stale, replaced or withdrawn content detail | 1 | SAMPLED | — |
| S-25 | screen | Patient home | 1 | CORE | — |
| S-26 | screen | Patient appointments and booking | 1 | SAMPLED | Patient move (reschedule) variant → Inc 2 (D-22) |
| S-27 | screen | Patient visit preparation form | 1 | SAMPLED | — |
| S-28 | screen | Published patient reports and explanations | 1 | CORE | — |
| S-29 | screen | Patient published content detail | 1 | CORE | — |
| S-30 | screen | Patient authorized document viewer and download | 2 | SAMPLED | — |
| S-31 | screen | Team administration and invitations | 2 | SAMPLED | — |
| S-32 | screen | Membership permissions and deactivation | 2 | SAMPLED | — |
| S-33 | screen | Relevant practice settings | 2 | SAMPLED | — |
| S-34 | screen | Permission-appropriate audit history | 2 | SAMPLED | Advanced filters and clinical-audit detail → Inc 4 (D-30) |
| S-35 | screen | Profile and preferences | 2 | SAMPLED | — |
| S-36 | screen | Forbidden and not-found states | 1 | CORE | — |
| S-37 | screen | Unavailable services and partial failure | 1 | SAMPLED | — |
| S-38 | screen | Expired session and return | 1 | SAMPLED | — |
| S-39 | screen | Export options and print preview | 1 | SAMPLED | Export options for P-01 and P-02 → Inc 2 (D-27) |
| S-40 | screen | AI processing activity and task detail | 1 | SAMPLED | — |

### 8.3 API operations (96 in the contract, 5 planned)

| Inc | Operation | Method and path | Permission | State |
|---|---|---|---|---|
| 1 | getCsrfToken | GET /csrf | PUBLIC | in contract |
| 1 | signIn | POST /session | PUBLIC | in contract |
| 1 | getSession | GET /session | AUTHENTICATED | in contract |
| 1 | signOut | DELETE /session | AUTHENTICATED | in contract |
| 1 | requestRecovery | POST /access-recovery | PUBLIC | in contract |
| 1 | completeRecovery | POST /access-recovery/complete | PUBLIC | in contract |
| 1 | acceptInvitation | POST /invitations/accept | INTENDED_RECIPIENT | in contract |
| 1 | getOwnAccount | GET /account | AUTHENTICATED | in contract |
| 1 | listAuthorizedPractices | GET /practices | AUTHENTICATED | in contract |
| 1 | getPractice | GET /practices/{practiceId} | MEMBERSHIP_OR_PORTAL | in contract |
| 1 | searchPatients | GET /practices/{practiceId}/patients | CLINICAL_READ | in contract |
| 1 | createPatient | POST /practices/{practiceId}/patients | CLINICAL_WRITE | in contract |
| 1 | editPatient | PATCH /practices/{practiceId}/patients/{patientId} | CLINICAL_WRITE | in contract |
| 1 | getPatientRecord | GET /practices/{practiceId}/records/{recordId} | CLINICAL_READ | in contract |
| 1 | getTimeline | GET /practices/{practiceId}/records/{recordId}/timeline | CLINICAL_READ | in contract |
| 1 | listRecordSources | GET /practices/{practiceId}/records/{recordId}/sources | CLINICAL_READ | in contract |
| 1 | getSourceRevision | GET /practices/{practiceId}/sources/{sourceId} | CLINICAL_READ | in contract |
| 1 | listAssertions | GET /practices/{practiceId}/records/{recordId}/assertions | CLINICAL_READ | in contract |
| 1 | appendAssertion | POST /practices/{practiceId}/records/{recordId}/assertions | CLINICAL_WRITE | in contract |
| 1 | correctAssertion | POST /practices/{practiceId}/assertions/{assertionId}/corrections | CLINICAL_WRITE | in contract |
| 1 | listMeasurements | GET /practices/{practiceId}/records/{recordId}/measurements | CLINICAL_READ | in contract |
| 1 | listLabResults | GET /practices/{practiceId}/records/{recordId}/lab-results | CLINICAL_READ | in contract |
| 1 | listRecordDocuments | GET /practices/{practiceId}/records/{recordId}/documents | CLINICAL_READ | in contract |
| 1 | listOfferedSlots | GET /practices/{practiceId}/slots | SCHEDULE_MANAGE_OR_SELF_APPOINTMENTS | in contract |
| 1 | listAppointments | GET /practices/{practiceId}/appointments | SCHEDULE_MANAGE | in contract |
| 1 | bookAppointment | POST /practices/{practiceId}/appointments | SCHEDULE_MANAGE | in contract |
| 1 | getAppointment | GET /practices/{practiceId}/appointments/{appointmentId} | SCHEDULE_MANAGE | in contract |
| 1 | cancelAppointment | POST /practices/{practiceId}/appointments/{appointmentId}/cancel | SCHEDULE_MANAGE | in contract |
| 1 | getVisitPreparation | GET /practices/{practiceId}/appointments/{appointmentId}/preparation | CLINICAL_READ | in contract |
| 1 | acknowledgePreparation | POST /practices/{practiceId}/appointments/{appointmentId}/preparation/acknowledge | CLINICAL_WRITE | in contract |
| 1 | startConsultation | POST /practices/{practiceId}/consultations | CLINICAL_WRITE | in contract |
| 1 | getConsultation | GET /practices/{practiceId}/consultations/{consultationId} | CLINICAL_READ | in contract |
| 1 | saveConsultationNotes | PATCH /practices/{practiceId}/consultations/{consultationId}/notes | CLINICAL_WRITE | in contract |
| 1 | createDocumentDraft | POST /practices/{practiceId}/document-versions | CLINICAL_WRITE | in contract |
| 1 | listDocumentVersions | GET /practices/{practiceId}/records/{recordId}/document-versions | CLINICAL_READ | in contract |
| 1 | getDocumentVersion | GET /practices/{practiceId}/document-versions/{documentVersionId} | CLINICAL_READ | in contract |
| 1 | editDraftVersion | PATCH /practices/{practiceId}/document-versions/{documentVersionId} | CLINICAL_WRITE | in contract |
| 1 | deriveNewDraft | POST /practices/{practiceId}/document-versions/{documentVersionId}/new-draft | CLINICAL_WRITE | in contract |
| 1 | validateDocumentVersion | POST /practices/{practiceId}/document-versions/{documentVersionId}/validate | CLINICAL_VALIDATE | in contract |
| 1 | acknowledgeBrief | POST /practices/{practiceId}/document-versions/{documentVersionId}/acknowledge | CLINICAL_WRITE | in contract |
| 1 | listProcessingTasks | GET /practices/{practiceId}/tasks | CLINICAL_READ | in contract |
| 1 | requestGeneration | POST /practices/{practiceId}/tasks | CLINICAL_WRITE | in contract |
| 1 | getProcessingTask | GET /practices/{practiceId}/tasks/{taskId} | AUTHORIZED_TASK_READER | in contract |
| 1 | retryProcessingTask | POST /practices/{practiceId}/tasks/{taskId}/retry | AUTHORIZED_REQUESTER | in contract |
| 1 | cancelProcessingTask | POST /practices/{practiceId}/tasks/{taskId}/cancel | AUTHORIZED_REQUESTER | in contract |
| 1 | publishVersion | POST /practices/{practiceId}/publications | PATIENT_PUBLISH | in contract |
| 1 | replacePublication | POST /practices/{practiceId}/publications/{publicationId}/replace | PATIENT_PUBLISH | in contract |
| 1 | withdrawPublication | POST /practices/{practiceId}/publications/{publicationId}/withdraw | PATIENT_PUBLISH | in contract |
| 1 | requestExport | POST /practices/{practiceId}/exports | AUTHORIZED_EXPORT_READER | in contract |
| 1 | getExport | GET /practices/{practiceId}/exports/{exportId} | AUTHORIZED_EXPORT_READER | in contract |
| 1 | appointmentCheckIn | POST /practices/{practiceId}/appointments/{appointmentId}/check-in | SCHEDULE_MANAGE | in contract |
| 1 | appointmentComplete | POST /practices/{practiceId}/appointments/{appointmentId}/complete | SCHEDULE_MANAGE | in contract |
| 1 | completeConsultation | POST /practices/{practiceId}/consultations/{consultationId}/complete | CLINICAL_WRITE | in contract |
| 1 | cancelConsultation | POST /practices/{practiceId}/consultations/{consultationId}/cancel | CLINICAL_WRITE | in contract |
| 1 | submitDocumentForReview | POST /practices/{practiceId}/document-versions/{documentVersionId}/submit-review | CLINICAL_WRITE_OR_REVIEWER | in contract |
| 1 | requestDocumentChanges | POST /practices/{practiceId}/document-versions/{documentVersionId}/request-changes | CLINICAL_WRITE_OR_REVIEWER | in contract |
| 1 | listOwnAppointments | GET /portal/practices/{practiceId}/records/{recordId}/appointments | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | bookOwnAppointment | POST /portal/practices/{practiceId}/records/{recordId}/appointments | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | cancelOwnAppointment | POST /portal/practices/{practiceId}/records/{recordId}/appointments/{appointmentId}/cancel | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | readOwnPreparation | GET /portal/practices/{practiceId}/records/{recordId}/appointments/{appointmentId}/preparation | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | editOwnPreparation | PATCH /portal/practices/{practiceId}/records/{recordId}/appointments/{appointmentId}/preparation | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | submitOwnPreparation | POST /portal/practices/{practiceId}/records/{recordId}/appointments/{appointmentId}/preparation | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | listOwnPublications | GET /portal/practices/{practiceId}/records/{recordId}/publications | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | readOwnPublication | GET /portal/practices/{practiceId}/records/{recordId}/publications/{publicationId} | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | readPublishedSources | GET /portal/practices/{practiceId}/records/{recordId}/publications/{publicationId}/sources | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | exportOwnPublication | POST /portal/practices/{practiceId}/records/{recordId}/publications/{publicationId}/exports | VERIFIED_OWN_PORTAL_LINK | in contract |
| 1 | downloadRecordDocument | GET /practices/{practiceId}/documents/{documentId}/content | CLINICAL_READ_CLEAN_FILE | in contract |
| 1 | downloadExport | GET /practices/{practiceId}/exports/{exportId}/content | AUTHORIZED_EXPORT_READER | in contract |
| 1 | listDemoScenarios | GET /demo/scenarios | PUBLIC_SYNTHETIC | in contract |
| 1 | resetSyntheticDemo | POST /demo/reset | OPERATOR_DEMO_RESET | in contract |
| 1 | listOwnPortalLinks | GET /account/portal-links | AUTHENTICATED | in contract |
| 1 | listPractitioners | GET /practices/{practiceId}/practitioners | SCHEDULE_MANAGE_OR_PORTAL_SLOTS | in contract |
| 1 | searchSchedulingPatients | GET /practices/{practiceId}/scheduling/patients | SCHEDULE_MANAGE | in contract |
| 1 | registerSchedulingPatient | POST /practices/{practiceId}/scheduling/patients | SCHEDULE_MANAGE AND PATIENT_REGISTER | in contract |
| 1 | getApiMeta | — | — | planned: Report the supported API version and minimum client version; owned by ADR-009, needed by story 10.2. |
| 2 | editOwnAccount | PATCH /account | AUTHENTICATED | in contract |
| 2 | editPractice | PATCH /practices/{practiceId} | PRACTICE_SETTINGS | in contract |
| 2 | listMemberships | GET /practices/{practiceId}/memberships | TEAM_MANAGE | in contract |
| 2 | editMembershipCapabilities | PATCH /practices/{practiceId}/memberships/{membershipId} | TEAM_MANAGE | in contract |
| 2 | suspendMembership | POST /practices/{practiceId}/memberships/{membershipId}/suspend | TEAM_MANAGE | in contract |
| 2 | listInvitations | GET /practices/{practiceId}/invitations | TEAM_MANAGE | in contract |
| 2 | issueInvitation | POST /practices/{practiceId}/invitations | TEAM_MANAGE_OR_PORTAL_LINK_ISSUER | in contract |
| 2 | revokeInvitation | POST /practices/{practiceId}/invitations/{invitationId}/revoke | AUTHORIZED_ISSUER | in contract |
| 2 | resendInvitation | POST /practices/{practiceId}/invitations/{invitationId}/resend | AUTHORIZED_ISSUER | in contract |
| 2 | appendMeasurement | POST /practices/{practiceId}/records/{recordId}/measurements | CLINICAL_WRITE | in contract |
| 2 | correctMeasurement | POST /practices/{practiceId}/measurements/{measurementId}/corrections | CLINICAL_WRITE | in contract |
| 2 | appendLabResult | POST /practices/{practiceId}/records/{recordId}/lab-results | CLINICAL_WRITE | in contract |
| 2 | correctLabResult | POST /practices/{practiceId}/lab-results/{labResultId}/corrections | CLINICAL_WRITE | in contract |
| 2 | uploadRecordDocument | POST /practices/{practiceId}/records/{recordId}/documents | CLINICAL_WRITE | in contract |
| 2 | listAvailability | GET /practices/{practiceId}/availability | SCHEDULE_MANAGE | in contract |
| 2 | createAvailability | POST /practices/{practiceId}/availability | OWN_PRACTITIONER_OR_MANAGER | in contract |
| 2 | editAvailability | PATCH /practices/{practiceId}/availability/{availabilityId} | OWN_PRACTITIONER_OR_MANAGER | in contract |
| 2 | rescheduleAppointment | POST /practices/{practiceId}/appointments/{appointmentId}/reschedule | SCHEDULE_MANAGE | in contract |
| 2 | listScopedAudit | GET /practices/{practiceId}/audit | ADMIN_AUDIT_OR_CLINICAL_AUDIT | in contract |
| 2 | appointmentNoShow | POST /practices/{practiceId}/appointments/{appointmentId}/no-show | SCHEDULE_MANAGE | in contract |
| 2 | moveOwnAppointment | POST /portal/practices/{practiceId}/records/{recordId}/appointments/{appointmentId}/reschedule | VERIFIED_OWN_PORTAL_LINK | in contract |
| 2 | downloadPublishedAttachment | GET /portal/practices/{practiceId}/records/{recordId}/publications/{publicationId}/attachments/{documentId}/content | CURRENT_PUBLICATION_ATTACHMENT_GRANT | in contract |
| 3 | createNativeSession | — | — | planned: Native session transport: issue the opaque session for a native client; owned by ADR-008, story 10.5. |
| 3 | registerDevice | — | — | planned: Register a device and its push token for the signed-in account; ADR-008, stories 10.5 and 10.7. |
| 3 | listOwnDevices | — | — | planned: List the account's registered devices and native sessions; ADR-008, story 10.5. |
| 3 | revokeDevice | — | — | planned: Revoke one device, its native session and its push token; ADR-008, story 10.5. |

Planned operations are added to the contract when the ADR that owns them is accepted, before the story that needs
them starts.

### 8.4 Requirements by increment (stories delivering them)

| Requirement | Inc 1 | Inc 2 | Inc 3 | Inc 4 |
|---|---|---|---|---|
| FR-001 | 1.1, 1.2, 8.3 | — | 10.5 | — |
| FR-002 | 1.3, 1.4, 10.1 | — | — | — |
| FR-003 | — | 2.1, 2.2 | — | — |
| FR-004 | 1.1, 3.1 | — | — | — |
| FR-005 | 3.1, 3.2 | — | — | — |
| FR-006 | 3.3 | — | — | — |
| FR-007 | 3.4 | — | — | — |
| FR-008 | 5.3 | 3.5 | — | — |
| FR-009 | 4.2, 4.3, 4.4, 5.1, 7.5, 8.3 | 2.3, 4.1 | 10.7 | — |
| FR-010 | 4.5 | — | — | — |
| FR-011 | 5.1, 5.2, 5.3 | 8.1 | — | — |
| FR-012 | 4.4, 6.1, 6.2 | — | — | — |
| FR-013 | 5.2, 5.3, 5.4, 6.2, 7.1 | — | — | — |
| FR-014 | 5.4, 6.3, 6.4, 7.1 | — | — | — |
| FR-015 | 7.1, 7.2, 7.3, 8.2, 8.3 | — | 10.7 | — |
| FR-016 | 1.3, 4.5, 7.4, 7.5, 8.2, 10.2 | — | 10.4, 10.5, 10.6 | 10.8 |
| FR-017 | 8.2, 8.3 | 8.1 | — | — |
| FR-018 | — | 2.3, 2.4 | — | — |
| FR-019 | 1.4, 9.1, 9.3, 10.3 | — | — | — |
| FR-020 | 9.2, 9.3, 10.1, 10.2, 10.3 | — | — | 10.8 |

## 9. Core design families and evidence policy (UD-16)

Named exception accepted by Aymen on 2026-11-17: only the families below must be designed, reviewed and approved
before the increment 1 build (all 19 were approved by Aymen as a design reference on 2026-11-20, UD-46); every other family is built desktop-first in the code and checked with a sample of
visual evidence.

- Screens: S-01, S-02, S-05, S-06, S-09, S-10, S-18, S-19, S-21, S-22, S-23, S-25, S-28, S-29, S-36.
- Interactions: I-09, I-15, I-16, I-17.
- CORE evidence: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.
- SAMPLED evidence: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.
- Families added by later decisions (S-07, S-08, S-17, S-26, S-27) stay SAMPLED (confirmed UD-37, O-3).
- The honest tracking columns of `docs/design/coverage.csv` are unchanged; a Figma node or an approval is still
  recorded only when it exists.

## 10. Adopted proposals (UD-30 to UD-36)

Aymen validated these on 2026-11-17; they are decisions now. The rules, specification, contract, stories and tests named below were updated in the change that records them; the design alignments are DF-15 to DF-18.

| ID | Adopted proposal | Where it lands | Why |
|---|---|---|---|
| P-1 (UD-30) | Brief composed by a deterministic, side-effect-free service from structured facts: changes since the previous visit, contradictions (an explicit negative followed by a later positive report), explicit gaps; every statement cited by construction; AI may only rephrase later | Story 5.2; BR-011 and the AI specification changed | Reproducible, €0, no latency or hallucination risk, makes the demo independent of a provider |
| P-2 (UD-31) | Mechanical support checks for citations: value, date and unit found in the cited excerpt, negation, later contradicting source; result is flags, never a score, and never the word verified | Stories 5.3, 6.3, 6.4 | First line of defense for the product's defining rule; the fixture `SC-UNSUPPORTED-DRAFT` is the first test |
| P-3 (UD-32) | Record viewed as known at a past instant (`asOf` on read operations, last revision per lineage with `recorded_at` at or before the instant, read-only) | Stories 3.2, 3.3 | Uses the immutable revisions already specified; shows the weight 86,4 to 68,4 kg correction |
| P-4 (UD-33) | Isolation proof: a CI-generated report of the adversarial matrix plus an interactive journey | Story 1.4 | One investment serves test and demonstration |
| P-5 (UD-34) | Reproducible demo package: `docker compose up`, scripted Playwright journey that is also the test and the video recorder, static warm-up page, portfolio-first README | Stories 9.1, 10.3 | Recruiters see the product in 90 seconds |
| P-6 (UD-35) | Module-boundary verification (ArchUnit or Spring Modulith, compatibility with Boot 4.1.2 to verify) and axe-core in Playwright | Stories 10.1, 10.2 | Enforces ADR-001 and NFR-05 cheaply |
| P-7 (UD-36) | Real-model evaluation reduced to 12–15 hand-annotated cases instead of at least 30 with two judges | D-26 | The specified protocol assumes a second human |
| P-8 | Spec freeze lifted only through one-page mini-ADRs (accepted as UD-27; listed for completeness) | Governance | Limits the documentation tax |

## 11. Decisions: settled, gated and kept by Aymen

On 2026-11-17 Aymen validated every recommendation of this list except the development authorization, which he keeps (UD-28). Gated decisions are not blocking: they need a measurement, a cost or a spike first.

| ID | Decision | State |
|---|---|---|
| O-1 | Native session duration and refresh (A-15, D-19) | Open; decided in story 10.5 (increment 3) |
| O-2 | Order of increments 2 and 3 | Settled: 3 then 2 (UD-40, ADR-011), replacing UD-37 |
| O-3 | Promote S-07, S-08, S-17, S-26, S-27 to CORE | Settled: they stay SAMPLED (UD-37) |
| O-4 | Sampling numbers for SAMPLED evidence | Settled: 8–10 screens, 2 widths (UD-37) |
| O-5 | Hosting decision after the benchmark | Gated: story 10.3 benchmark, then Aymen chooses; €0 best effort until then (UD-25) |
| O-6 | Accept ADR-008 and ADR-009 | Settled: accepted (UD-29) |
| O-7 | Adopt proposals P-1 to P-7 | Settled: adopted (UD-30 to UD-36) |
| O-8 | Booking and cancellation windows (D-21) | Gated: decided at the start of increment 2; fixed grid until then |
| O-9 | Store decision (D-15): Apple program, Google fee and tester rule | Settled: no store publication is planned (UD-39); a native iOS demonstration needs a Mac or an explicit Apple-program decision at the story 10.8 gate |
| O-10 | Custom domain and privacy-policy page (D-32) | Gated: with the store or deep-link decision |
| O-11 | Assumptions A-02, A-04, A-05, A-08, A-09, A-10, A-11 | Settled: confirmed as working baselines (UD-37); each keeps its revisit trigger |
| O-12 | Real-model evaluation corpus size | Settled: 12 to 15 hand-annotated cases (UD-36) |
| O-13 | Module-verification tool and exact dependency versions | Delegated: chosen at the story 10.1 spike and recorded |
| O-14 | Native client technology and distribution | Settled: React Native with Expo, demo-only (UD-38, UD-39, ADR-010); spike checks listed in ADR-010 |

Still kept by Aymen: the development authorization (UD-28) and the review and approval of the core design families (UD-16).

## 12. Follow-ups outside this change, by owner

**Design owner (Aymen and Codex).** This change does not edit `docs/design/` or the HTML prototype because Codex
is working there. These alignments are needed; items DF-11 to DF-13 were observed in local captures outside Git on
2026-11-17 and are not re-verified here.

| ID | Alignment needed | Source of the need |
|---|---|---|
| DF-1 | S-18: replace the DRAFT / IN_REVIEW / VALIDATED states of the brief by DRAFT, acknowledged marker and freshness banner; remove review and validate actions for briefs; prototype and dataset follow | UD-22, BR-012 |
| DF-2 | P-01 footer: draft label plus "relu par … le …" instead of validator and date | UD-22 |
| DF-3 | S-07, S-08, I-04, I-10: drop the "Proposal" labels of PD-001; dataset wording for Mila | UD-17 |
| DF-4 | S-17: align "Marquer comme lue" with acknowledgePreparation (If-Match, idempotent, shows who and when; a new patient submission needs a new acknowledgement) | UD-18 |
| DF-5 | Interaction inventory I-09 lists seven screens, its sheet four: align | Design review finding |
| DF-6 | Replace the per-family visual-acceptance wording and add an `evidence_level` column (CORE or SAMPLED) in the tracker | UD-16 |
| DF-7 | I-10 copy for the refused patient overlap (PATIENT_OVERLAP) with no staff override | UD-23 |
| DF-8 | Conflict copy per error code (SLOT_CONFLICT, PATIENT_OVERLAP, STALE_VERSION) | Contract README catalog |
| DF-9 | Multi-client design (native specifics in DF-19): safe areas, navigation convention, notification permission copy, biometric lock screen (D-18), invitation landing with and without the app (S-03), native unavailable state (S-37), install prompt, app icon and splash from the existing logos | UD-12 |
| DF-10 | S-01: a later "install the app" entry point | UD-12 |
| DF-11 | Source excerpts are not in chronological order in I-17 and in the patient view (14/09, 02/11, 20/10; numbering ^1 = 2 November): decide an ordering rule | Local captures I-17, S-29 |
| DF-12 | S-06 repeats the empty-day message in two sentences | Local capture S-06 |
| DF-13 | ADR-004 expires a session after 30 minutes of inactivity: design autosave or heartbeat states for I-05, I-15, I-22 | ADR-004, I-22 |
| DF-14 | S-07, S-08, S-17, S-26 and S-27 stay SAMPLED; revisit only if a review finds a risk | UD-37 |
| DF-15 | The fixture AI-A-002 and the S-06, S-37, S-40, I-14 copy assume the brief is generated by a provider and can time out; with the deterministic composer the brief is computed, so the timeout fixture moves to the report draft or to the optional rephrasing, and the brief action becomes a recalculation | UD-30 |
| DF-16 | S-18, S-21, S-23: show each citation's mechanical check result and its flags, the copy "Contrôles mécaniques passés — relecture humaine requise" (never "vérifié") and the validation-blocked state | UD-31 |
| DF-17 | S-09, S-11: the as-of selector, the "Vue historique — lecture seule" banner and the link to the correction | UD-32 |
| DF-18 | S-01, S-36: the guided "Prouvez l'isolation" journey and the static warm-up page of the demo package | UD-33, UD-34 |
| DF-19 | Native app (React Native, ADR-010): design the native patient screens from the approved 390×844 designs of S-02, S-04, S-25 to S-29, S-36 to S-38 and I-22, with native navigation, safe areas, the notification-permission copy, app icon and splash from the existing logos; map the tokens to the native token module; decide the native rendering of the background layer, which is CSS and SVG (`docs/design/background-study`) and cannot be reused as is, so the study's line about precaching the tile with the Capacitor shell is obsolete; the screens must be modern and fluid yet keep the identity of the web app (same tokens, fonts, logos, light and dark themes), within the DESIGN.md limits on motion; the visual evidence for the native screens is separate from the web evidence and is a side-by-side comparison with the 390×844 web designs | UD-38, ADR-010 |

**Contract and rules (this change did the following; pending items are listed).**

| ID | Item | State |
|---|---|---|
| CF-1 | Error responses per operation, ETag only on versioned aggregates, `operationId` names, `/tasks` collection, `Capability` schema, conditional schemas, acknowledgement operations, manifest rule | Done in this change |
| CF-2 | Five planned operations (section 8.3) | Pending: add `getApiMeta` before story 10.2 and the other four before story 10.5 (ADR-008 and ADR-009 are accepted) |
| CF-3 | Update `docs/design/synthetic-data.json` if Aymen wants an acknowledgement fixture | Optional, design owner |
| CF-4 | Version comparison is computed on the client in increment 1 (no diff operation) | Decided in the contract README |
| CF-5 | `AsOf` parameter on five record read operations and `Citation.mechanicalCheck` (contract 0.2.1-planning) | Done in this change |

**Tooling.**

| ID | Item | State |
|---|---|---|
| TF-1 | `tools/validate_docs.py` checks roadmap completeness, story dependencies and the error and ETag policies | Done in this change |
| TF-2 | BMAD implementation tracking after authorization | Pending authorization |
| TF-3 | Adapt the validator's no-scaffold invariant in the PR that creates the skeleton | Pending story 10.1 |

## 13. Change log

- 2026-11-17: first version, from the user decisions of that date.
- 2026-11-17 (later): UD-29 to UD-37 settle the open decisions and adopt proposals P-1 to P-7.
- 2026-11-19: UD-38 and UD-39 replace the Capacitor shell with a React Native app built with Expo and make the mobile apps demo-only (ADR-010). Increments 3 and 4 are renamed, story titles 10.6 and 10.8 reworded, D-15 becomes Not planned, O-9 is settled and DF-19 is added. Sources consulted in full for this amendment: PRD, business rules, access matrix, functional specification, UX experience and design, architecture spine, data model and dictionary, AI specification, security and privacy, NFRs, test strategy and acceptance index, contract README, decision register, deferred register, ADR-002, ADR-004, ADR-007, ADR-008, ADR-009 and epic 10; the Expo documentation read on 2026-11-19 is cited in ADR-010. The allocation of stories to increments is unchanged.
- 2026-11-19 (later): UD-40 accepts ADR-011 (order 3 then 2, six milestones of increment 1) and UD-41 authorizes story 10.1 only; O-2 and the gates of section 2 are updated.

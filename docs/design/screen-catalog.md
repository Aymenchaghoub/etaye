# Screen and surface catalog

Status: Draft / Ready for design. Updated 2026-11-02. **40 required surface families**. These are functional design specifications, not completed Figma screens. Minor variants live inside the relevant family. Existing source file: [Étaye Figma](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=0-1).

Authoritative links: [PRD](../planning-artifacts/prd/prd.md), [business rules](../planning-artifacts/business-rules.md), [access matrix](../planning-artifacts/access-matrix.md), [UX behavior](../planning-artifacts/ux/EXPERIENCE.md), [synthetic dataset](synthetic-data.md), [interactions](interaction-catalog.md), [PDF/email surfaces](pdf-email-specifications.md), [design coverage](coverage.csv).

## Shared acceptance patterns

Every row inherits these checks; the local acceptance criterion adds the product-specific test. Keep annotations in English and product copy in French. Resolve conflicting business behavior against business-rules.md, and visual values against [verified token/asset reference](token-reference.md), not generic component-library defaults.

- **SP-STATE:** Specify cold loading, empty, no-results, error, success, read-only and permission-denied where data/actions exist. Render existing data with a failed-section notice on partial failures; do not substitute empty success. Include long content and at least one non-happy-path frame per family.
- **SP-SAVE:** Show dirty/saving/saved/failed independently. Keep entered information after validation/network failure. Reject stale versions through I-21; prevent duplicate submission; never show success before durable server acknowledgement. I-20 guards navigation, practice switching and closing dirty forms.
- **SP-SECURITY:** Every list/item/source/download checks server membership, capability, active practice and resource ownership or patient publication grant. UI hiding is presentation, not security. Expired sessions use S-38; revoked permissions clear protected context.
- **SP-RESPONSIVE:** Review desktop 1440×1024, tablet 1024×768, mobile 390×844 and minimum reflow at 320 CSS px. Professional multi-pane layouts become sequential full-width panels; patient flows are mobile-first. Do not shrink whole workspaces to fit. Test long French labels and 200% zoom. Datatables may use a labeled horizontal scroll region only where row-to-column comparison genuinely needs it.
- **SP-A11Y:** Visible keyboard focus, logical heading/order, labeled form fields, error summary and field association; focus restoration after panels; status announcements without excessive interruption; no color-only meaning. Touch targets at least 44×44 CSS px as design target. Use a chart table alternative. Accessibility targets are proposed and require implementation verification.
- **SP-FIGMA:** No complete application screen was established by the source snapshot. Each S-ID requires a new named frame in the next design phase. Reuse existing logo nodes 4:13/4:19/4:20/4:33, colors 3:2/3:87, typography 3:172, buttons 5:22, statuses 5:46, reference marker 5:47 and annotation example 6:2 after checking current revisions. Never overwrite existing foundations to make a generic theme fit.

## Inventory

| ID | Surface | Actor | Journey | Requirements |
|---|---|---|---|---|
| [S-01](#s-01) | Showcase and demo entry | Public visitor | J-08 | FR-019 |
| [S-02](#s-02) | Login | Practitioner, administrator or patient | J-01 | FR-001 |
| [S-03](#s-03) | Invitation and account activation | Invited team member or patient | J-01 | FR-001, FR-004 |
| [S-04](#s-04) | Access recovery | Account holder | J-01 | FR-001 |
| [S-05](#s-05) | Practice and access-context selection | Account with several authorized contexts | J-01 | FR-002, FR-016 |
| [S-06](#s-06) | Practitioner home and daily preparation | Clinical practitioner | J-02 | FR-009, FR-011, FR-014 |
| [S-07](#s-07) | Patient directory and search | Clinical practitioner | J-02 | FR-004, FR-005 |
| [S-08](#s-08) | Patient identity creation and editing | Clinical practitioner with record-write capability | J-02 | FR-004 |
| [S-09](#s-09) | Patient record and longitudinal timeline | Clinical practitioner | J-02 | FR-005, FR-008, FR-014 |
| [S-10](#s-10) | History, allergies and reported treatments | Clinical practitioner | J-02 | FR-005, FR-010 |
| [S-11](#s-11) | Vital signs, measurements and trends | Clinical practitioner | J-02 | FR-006 |
| [S-12](#s-12) | Laboratory results and reference intervals | Clinical practitioner | J-02 | FR-007 |
| [S-13](#s-13) | Record documents and clinician source viewer | Clinical practitioner | J-02 | FR-008, FR-011 |
| [S-14](#s-14) | Practice schedule | Clinical practitioner | J-03 | FR-009 |
| [S-15](#s-15) | Availability and scheduling settings | Clinical practitioner for own availability; authorized practice manager where granted | J-03 | FR-009 |
| [S-16](#s-16) | Appointment details | Practitioner; patient in own variant | J-03 | FR-009, FR-010 |
| [S-17](#s-17) | Practitioner view of patient visit preparation | Clinical practitioner | J-02 | FR-010 |
| [S-18](#s-18) | Sourced pre-visit brief | Clinical practitioner | J-02 | FR-011, FR-013, FR-014 |
| [S-19](#s-19) | Consultation workspace and structured notes | Clinical practitioner | J-04 | FR-012 |
| [S-20](#s-20) | Consultation report editor | Clinical practitioner | J-04 | FR-012, FR-013 |
| [S-21](#s-21) | AI draft review and processing result | Clinical practitioner reviewer | J-04 | FR-013, FR-014 |
| [S-22](#s-22) | Version history and comparison | Clinical practitioner; patient only permitted publication metadata | J-04 | FR-014 |
| [S-23](#s-23) | Human validation and patient publication review | Practitioner with validate/publish capabilities | J-04, J-05 | FR-014, FR-015 |
| [S-24](#s-24) | Stale, replaced or withdrawn content detail | Clinical practitioner; patient-safe lifecycle variant | J-05 | FR-014, FR-015, FR-016 |
| [S-25](#s-25) | Patient home | Patient with verified portal record grant | J-06 | FR-016 |
| [S-26](#s-26) | Patient appointments and booking | Patient | J-06 | FR-009, FR-016 |
| [S-27](#s-27) | Patient visit preparation form | Patient | J-06 | FR-010, FR-016 |
| [S-28](#s-28) | Published patient reports and explanations | Patient | J-06 | FR-015, FR-016 |
| [S-29](#s-29) | Patient published content detail | Patient | J-06 | FR-015, FR-016 |
| [S-30](#s-30) | Patient authorized document viewer and download | Patient | J-06 | FR-008, FR-016, FR-017 |
| [S-31](#s-31) | Team administration and invitations | Practice administrator | J-07 | FR-003 |
| [S-32](#s-32) | Membership permissions and deactivation | Practice administrator | J-07 | FR-003 |
| [S-33](#s-33) | Relevant practice settings | Practice administrator; practitioner for own availability | J-07 | FR-018 |
| [S-34](#s-34) | Permission-appropriate audit history | Clinical auditor or practice administrator under separate scope | J-07 | FR-018 |
| [S-35](#s-35) | Profile and preferences | Authenticated account holder | J-01, J-06 | FR-018 |
| [S-36](#s-36) | Forbidden and not-found states | Any actor | J-01, J-06, J-07 | FR-001, FR-002, FR-016 |
| [S-37](#s-37) | Unavailable services and partial failure | Any actor | J-08 | FR-013, FR-019 |
| [S-38](#s-38) | Expired session and return | Any authenticated actor | J-01 | FR-001 |
| [S-39](#s-39) | Export options and print preview | Clinical practitioner; patient with publication grant | J-02, J-06 | FR-017 |
| [S-40](#s-40) | AI processing activity and task detail | Clinical practitioner | J-04 | FR-013 |

<a id="s-01"></a>
## S-01 — Showcase and demo entry

| Field | Specification |
|---|---|
| Goal / actor | Showcase and demo entry for public visitor |
| Traceability | FR-019; BR-018; J-08 |
| Entry / exit | Public link → guided practitioner, patient or administrator scenario |
| Content / example | Étaye purpose; dated-source product preview; synthetic-data notice; planning/design status until implementation exists |
| Actions / validation | Choose scenario; explain current capability honestly; no invented adoption claims or live application link |
| Permission / visibility | Public synthetic examples only; no account list or private data |
| Necessary states | Demo preparing, ready, resetting, unavailable; reduced motion; long explanatory copy; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Showcase; scenario selector; demo notice; visual/behavioral specification in the UX spines |
| Figma | Required: new S-01 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S01-F:** The chosen scenario names the fictional actor, practice and data-reset limits before entry. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S01-V:** A real Étaye logo and one readable source relationship anchor the preview; no generic AI illustration. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-02"></a>
## S-02 — Login

| Field | Specification |
|---|---|
| Goal / actor | Login for practitioner, administrator or patient |
| Traceability | FR-001; BR-003; J-01 |
| Entry / exit | Demo scenario or protected link → S-05, S-06 or S-25 |
| Content / example | Email, password, recovery link, current synthetic-demo context |
| Actions / validation | Submit once; show generic invalid credentials; retain email only; never expose whether account exists |
| Permission / visibility | Unauthenticated view; no clinical information in error or return URL |
| Necessary states | Initial, submitting, invalid, suspended-access guidance, throttled, expired-session return; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Auth form; error summary; field; button; visual/behavioral specification in the UX spines |
| Figma | Required: new S-02 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S02-F:** Successful server authentication resolves actual memberships or portal grants; password is cleared after rejection. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S02-V:** French labels and errors remain visible at 200% zoom; no placeholder-only labels. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-03"></a>
## S-03 — Invitation and account activation

| Field | Specification |
|---|---|
| Goal / actor | Invitation and account activation for invited team member or patient |
| Traceability | FR-001, FR-004; BR-004, BR-005; J-01 |
| Entry / exit | One-time invitation link or M-01/M-02 → authenticated relevant home |
| Content / example | Invitation purpose, practice, recipient hint, expiry; password and acceptance fields when necessary |
| Actions / validation | Accept invite once; verify recipient/linking evidence; resend requires authorized actor; never infer patient identity from email alone |
| Permission / visibility | Token authorizes only its defined membership or portal link; clinical content appears only after activation and access checks |
| Necessary states | PENDING, ACCEPTED, EXPIRED, REVOKED; already active; invalid; concurrent acceptance; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Auth form; invitation banner; status; visual/behavioral specification in the UX spines |
| Figma | Required: new S-03 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S03-F:** Expired or revoked invitation cannot activate membership or disclose a patient record; accepted token is single-use. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S03-V:** The membership invitation and patient-link invitation have distinct titles and consequences. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-04"></a>
## S-04 — Access recovery

| Field | Specification |
|---|---|
| Goal / actor | Access recovery for account holder |
| Traceability | FR-001; BR-003, BR-005; J-01 |
| Entry / exit | S-02 recovery → generic confirmation → reset link → login |
| Content / example | Email request then password/reset confirmation; expiry and secure return |
| Actions / validation | Generic response; request limits; token use once; clear password fields; expire other sessions per security policy |
| Permission / visibility | No patient names, membership details or clinical information |
| Necessary states | Request, sent, invalid/expired token, reset succeeded, throttled, network failure; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Auth form; confirmation; error summary; visual/behavioral specification in the UX spines |
| Figma | Required: new S-04 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S04-F:** All request outcomes avoid account enumeration; expired links lead to a new request with no secret in UI. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S04-V:** Security instructions are short and readable on mobile; primary action remains unambiguous. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-05"></a>
## S-05 — Practice and access-context selection

| Field | Specification |
|---|---|
| Goal / actor | Practice and access-context selection for account with several authorized contexts |
| Traceability | FR-002, FR-016; BR-001, BR-003, BR-004; J-01 |
| Entry / exit | Authenticated account → authorized practitioner/admin practice or own patient context |
| Content / example | Cabinet des Tilleuls / Cabinet du Canal, membership role and portal context; current selection |
| Actions / validation | Choose permitted context; switch from shell; warn on unsaved edits via I-20; clear old context data |
| Permission / visibility | Server-resolved active memberships/grants only; no clinical previews in selector |
| Necessary states | Single context bypass, multiple contexts, deactivated membership, context removed during selection; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Context selector; shell; status; visual/behavioral specification in the UX spines |
| Figma | Required: new S-05 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S05-F:** Changing context never carries patient search results, document links or drafts into the other practice. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S05-V:** Practice name and working role are persistent in the destination shell; do not rely only on color. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-06"></a>
## S-06 — Practitioner home and daily preparation

| Field | Specification |
|---|---|
| Goal / actor | Practitioner home and daily preparation for clinical practitioner |
| Traceability | FR-009, FR-011, FR-014; BR-002, BR-006, BR-014; J-02 |
| Entry / exit | S-02/S-05 → appointment, patient record, preparation or task |
| Content / example | Today’s scheduled people, time, preparation availability, missing brief, reviews awaiting action; no risk ranking |
| Actions / validation | Filter own/all authorized practice agenda; open record; start consultation; generate only from explicit action |
| Permission / visibility | Current practice clinical membership; administration-only account receives its own administration landing at S-31 |
| Necessary states | Loading sections, empty day, partial task failure, stale brief, cancelled visit, timezone/date boundary; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Daily list; task status; appointment row; source badge; visual/behavioral specification in the UX spines |
| Figma | Required: new S-06 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S06-F:** A cancelled appointment does not disappear from audit or become an active consultation; unavailable AI does not block record access. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S06-V:** Dense list has aligned times and quiet hierarchy; no repetitive dashboard card grid. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-07"></a>
## S-07 — Patient directory and search

| Field | Specification |
|---|---|
| Goal / actor | Patient directory and search for practitioner; restricted scheduling lookup for SCHEDULE_MANAGE |
| Traceability | FR-004, FR-005; BR-001, BR-002, BR-004; J-02 |
| Entry / exit | Shell / S-06 → S-09 or S-08; I-10 staff lookup → selected patient or S-08 restricted registration → return to I-10 |
| Content / example | Name, birth date, practice-local identifier, portal-link status; query and applied filters |
| Actions / validation | Search, paginate, clear filters, create patient; preserve filters on back; bounded result counts |
| Permission / visibility | CLINICAL_READ for clinical directory; SCHEDULE_MANAGE uses only SchedulingPatient identity/contact projection. No cross-practice autocomplete, clinical columns or record links in scheduling mode |
| Necessary states | Initial, loading, no patients, no results, partial error, long names, stale results; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Search; filter; paginated table; empty state; visual/behavioral specification in the UX spines |
| Figma | Required: new S-07 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S07-F:** A search for Hugo Petit from PRA-A returns no match; search never changes active practice implicitly. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S07-V:** Columns collapse into readable identity rows on mobile; birth date is never hidden behind truncation. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-08"></a>
## S-08 — Patient identity creation and editing

| Field | Specification |
|---|---|
| Goal / actor | Patient identity creation and editing for clinical practitioner; restricted create-only variant for scheduler with PATIENT_REGISTER (PD-001) |
| Traceability | FR-004; BR-002, BR-004, BR-015; J-02 |
| Entry / exit | S-07 new / S-09 identity edit → S-09 for clinician; I-10 / S-07 scheduling variant → new minimal identity → I-10, never S-09 |
| Content / example | Identity fields, date of birth, contact information and provenance; local possible-duplicate warning |
| Actions / validation | Validate required identity/date fields; no global automatic merge; save expected version for clinical edits; portal linking is a separate clinical-team-issued invitation and recipient activation, never a registration side effect |
| Permission / visibility | CLINICAL_WRITE for clinical create/edit; both SCHEDULE_MANAGE and PATIENT_REGISTER for demographic-only creation. No scheduler correction, clinical data or portal linking. Admin role alone grants neither path |
| Necessary states | New, edit, validation error, possible duplicate, version conflict, saving, unsaved changes; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Form; date input; duplicate notice; conflict panel; visual/behavioral specification in the UX spines |
| Figma | Required: new S-08 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S08-F:** Concurrent edits cannot silently overwrite identity; a possible match prompts inspection without cross-practice search. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S08-V:** Required and optional fields are explicit; paired identity fields reflow rather than compress. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-09"></a>
## S-09 — Patient record and longitudinal timeline

| Field | Specification |
|---|---|
| Goal / actor | Patient record and longitudinal timeline for clinical practitioner |
| Traceability | FR-005, FR-008, FR-014; BR-001, BR-008, BR-014; J-02 |
| Entry / exit | S-07 / appointment → history, observations, labs, document or consultation |
| Content / example | Claire Martin identifier, dated events, authors, origin, source relationships, contradictions, stale summary warning |
| Actions / validation | Filter by date/type; expand source; open structured sections; correct by version; start consultation |
| Permission / visibility | Practice clinical read; write actions require additional capability; patient portal is separate |
| Necessary states | Minimal Louis history, long chronology, corrected event, unknown occurrence date, partial fetch error, read-only membership; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Record header; timeline; source marker; information-state label; visual/behavioral specification in the UX spines |
| Figma | Required: new S-09 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S09-F:** An event’s occurrence date and recording date can be distinguished; missing information is not rendered as “none”. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S09-V:** Chronology remains the main organizing structure at desktop and mobile sizes; source references are keyboard readable. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-10"></a>
## S-10 — History, allergies and reported treatments

| Field | Specification |
|---|---|
| Goal / actor | History, allergies and reported treatments for clinical practitioner |
| Traceability | FR-005, FR-010; BR-008, BR-015; J-02 |
| Entry / exit | S-09 → source, correction or consultation |
| Content / example | Patient-reported penicillin reaction beside older explicit-negative note; current treatments non renseignés |
| Actions / validation | Record origin and dated statements; correct with reason; mark unresolved conflict; no automatic reconciliation |
| Permission / visibility | Practice clinical read/write; original and corrected source history retained |
| Necessary states | Missing, explicit negative, reported unconfirmed, contradictory, historical, corrected, read-only; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Information state; structured list; source panel; correction form; visual/behavioral specification in the UX spines |
| Figma | Required: new S-10 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S10-F:** The contradiction shows both dated sources; a missing treatment list is never converted to “aucun traitement”. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S10-V:** Origin/status is conveyed in words and not only colored dots; long notes wrap. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-11"></a>
## S-11 — Vital signs, measurements and trends

| Field | Specification |
|---|---|
| Goal / actor | Vital signs, measurements and trends for clinical practitioner |
| Traceability | FR-006; BR-008, BR-009, BR-015; J-02 |
| Entry / exit | S-09 → I-06 entry/correction or source |
| Content / example | 128/80 then 134/82 mmHg; corrected weight 68,4 kg; date, unit, measurement origin |
| Actions / validation | Add/correct measurement; select period; inspect points; show historical value/version; no derived global risk |
| Permission / visibility | Current practice clinical read/write; patient does not see unpublished measurements by default |
| Necessary states | No measurements, one point, mixed units, unknown date, corrected point, loading, impossible entry, conflict; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Measurement table; trend chart; unit input; source marker; visual/behavioral specification in the UX spines |
| Figma | Required: new S-11 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S11-F:** Trend uses compatible units and corrected current versions; date/unit/provenance remain inspectable. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S11-V:** Accessible table accompanies chart; missing periods do not imply continuous measurements. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-12"></a>
## S-12 — Laboratory results and reference intervals

| Field | Specification |
|---|---|
| Goal / actor | Laboratory results and reference intervals for clinical practitioner |
| Traceability | FR-007; BR-008, BR-009; J-02 |
| Entry / exit | S-09 → I-07 entry/correction or I-09 / S-13 source |
| Content / example | Creatinine 96 then 104 µmol/L with sample dates and lab-provided intervals; glucose 5,2 mmol/L |
| Actions / validation | Manual entry/correction; filter analyte/period; inspect source page; no extraction button in initial scope |
| Permission / visibility | Practice clinical read/write; uploaded document has its own access check |
| Necessary states | Missing interval, textual result, outside supplied interval, incompatible units, unconfirmed transcription, empty, corrected; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Lab table; interval label; trend chart; source marker; visual/behavioral specification in the UX spines |
| Figma | Required: new S-12 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S12-F:** Interval comes from the associated lab source or reads “Intervalle non fourni”; no generic normal interval is invented. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S12-V:** Numeric columns align; units and interval never depend on hover or color alone. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-13"></a>
## S-13 — Record documents and clinician source viewer

| Field | Specification |
|---|---|
| Goal / actor | Record documents and clinician source viewer for clinical practitioner |
| Traceability | FR-008, FR-011; BR-010, BR-011; J-02 |
| Entry / exit | S-09 / I-09 citation → document list / page anchor / return to statement |
| Content / example | Bilan fictif du 12 octobre 2026, upload metadata, source date, page location, title and version |
| Actions / validation | Upload via I-08; preview authorized file; open cited page; download; metadata correction with history |
| Permission / visibility | Each document and citation checked against active practice and record; no public storage URLs |
| Necessary states | Upload pending, scanning, ready, rejected, unsupported preview, missing page, expired access, long filename; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | File row; secure preview; source panel; upload queue; visual/behavioral specification in the UX spines |
| Figma | Required: new S-13 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S13-F:** Citation opens the exact dated source/version/page; an unavailable source never appears as successfully inspected. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S13-V:** Viewer preserves readable source text and return focus; document UI is not squeezed beside an unreadable page. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-14"></a>
## S-14 — Practice schedule

| Field | Specification |
|---|---|
| Goal / actor | Practice schedule for practitioner or explicitly assigned scheduler |
| Traceability | FR-009; BR-006, BR-007; J-03 |
| Entry / exit | Shell / S-06 → S-16 / I-10 / I-11 |
| Content / example | Day/week agenda, practitioner availability, appointment status, local timezone |
| Actions / validation | Book, move, open visit; drag optional with I-11 confirmation and keyboard equivalent; navigate dates |
| Permission / visibility | SCHEDULE_MANAGE for agenda and minimal identity/contact, without clinical notes or source/preparation content for nonclinical staff; clinical links additionally require CLINICAL_READ. Own patient portal uses S-26 |
| Necessary states | Empty day, overlap rejected, concurrent booking, cancellation, no-show, service failure, date DST ambiguity; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Schedule grid; agenda list; appointment row; date navigation; visual/behavioral specification in the UX spines |
| Figma | Required: new S-14 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S14-F:** Two conflicting commits cannot both succeed; failure refreshes slots and preserves entered reason. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S14-V:** Mobile uses agenda list with exact start/end times; appointment status remains text-visible. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-15"></a>
## S-15 — Availability and scheduling settings

| Field | Specification |
|---|---|
| Goal / actor | Availability and scheduling settings for clinical practitioner for own availability; authorized practice manager where granted |
| Traceability | FR-009; BR-002, BR-006; J-03 |
| Entry / exit | S-14 / settings → saved availability and impact preview |
| Content / example | Dated availability blocks, exceptions, slot duration, patient-bookable indicator, Europe/Paris |
| Actions / validation | Create/edit/remove block; preview effects on existing bookings; no silent cancellation |
| Permission / visibility | Explicit scheduling-management capability; admin-only does not receive appointment clinical notes |
| Necessary states | No availability, exceptions, conflicting existing booking, invalid end time, concurrent edit; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Availability editor; schedule grid; impact list; visual/behavioral specification in the UX spines |
| Figma | Required: new S-15 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S15-F:** Removing availability never silently cancels booked visits; affected bookings require an explicit separate decision. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S15-V:** Week template and dated exception are visually distinct; mobile editor uses full width. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-16"></a>
## S-16 — Appointment details

| Field | Specification |
|---|---|
| Goal / actor | Appointment details for practitioner, restricted scheduler, or patient in own variant |
| Traceability | FR-009, FR-010; BR-006, BR-007; J-03 |
| Entry / exit | S-14 / S-26 → record, preparation, consultation or back |
| Content / example | APT-A-001, Claire, Dr Lefebvre, 3 November 10:30–11:00; reason and status |
| Actions / validation | Reschedule I-11; cancel I-12; check in/complete/no-show I-13 where authorized; start consultation independently |
| Permission / visibility | SCHEDULE_MANAGE for staff appointment metadata and attendance; clinical/preparation/record links require CLINICAL_READ. Patient sees only own visit and permitted reason/preparation; no other slots’ identities |
| Necessary states | BOOKED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW; edited elsewhere; session expired; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Appointment detail; status; action group; visual/behavioral specification in the UX spines |
| Figma | Required: new S-16 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S16-F:** Appointment and consultation IDs/statuses stay distinct; starting a consultation does not silently mark attendance complete. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S16-V:** Status and action consequences remain visible above fold without hiding identity/time. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-17"></a>
## S-17 — Practitioner view of patient visit preparation

| Field | Specification |
|---|---|
| Goal / actor | Practitioner view of patient visit preparation for clinical practitioner |
| Traceability | FR-010; BR-004, BR-008; J-02 |
| Entry / exit | S-16 / S-09 → brief or consultation |
| Content / example | PREP-A-001 patient concerns, submitted time, self-reported change, unknown reaction details |
| Actions / validation | Inspect original; acknowledge reading; incorporate by cited source into consultation; never label as observed fact |
| Permission / visibility | Current practice record access; patient can read own submitted preparation through S-27 |
| Necessary states | Not submitted, draft unavailable to clinician, submitted, corrected submission, long text, stale linkage; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Preparation sheet; origin label; source marker; visual/behavioral specification in the UX spines |
| Figma | Required: new S-17 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S17-F:** Patient-reported entries retain their origin when used as sources; clinician cannot silently edit the patient’s submitted text. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S17-V:** Question/answer blocks support long content and show submission date without unnecessary AI badges. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-18"></a>
## S-18 — Sourced pre-visit brief

| Field | Specification |
|---|---|
| Goal / actor | Sourced pre-visit brief for clinical practitioner |
| Traceability | FR-011, FR-013, FR-014; BR-011, BR-012, BR-014; J-02 |
| Entry / exit | S-06 / S-09 / S-17 → source, review, consultation or clinician PDF |
| Content / example | BRIEF-A-001-v1; changes since last consultation; missing data; conflicting allergy sources; evidence list |
| Actions / validation | Generate I-14; open citation I-09; correct I-15; submit/review; export P-01; never directly publish |
| Permission / visibility | Clinician-only even if VALIDATED; publication action absent for this document kind |
| Necessary states | No prior visit, insufficient sources, QUEUED/RUNNING task, DRAFT/IN_REVIEW/VALIDATED business state, STALE, failed task; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Annotated document; source marker; freshness banner; task status; visual/behavioral specification in the UX spines |
| Figma | Required: new S-18 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S18-F:** Every important statement exposes supporting dated source/version or is flagged unsupported; missing inputs produce explicit gaps. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S18-V:** Source annotation follows 6:2 identity while professional reading width and long paragraphs remain legible. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-19"></a>
## S-19 — Consultation workspace and structured notes

| Field | Specification |
|---|---|
| Goal / actor | Consultation workspace and structured notes for clinical practitioner |
| Traceability | FR-012; BR-007, BR-008, BR-015; J-04 |
| Entry / exit | S-16 / S-09 start with or without appointment → S-20 or completed consultation |
| Content / example | Patient identity, optional appointment link, reason, observed/reported sections, measurements, source links |
| Actions / validation | Save draft; add structured note; consult history; complete/cancel consultation with explicit guard; unsaved I-20 |
| Permission / visibility | Clinical write in current practice; inactive membership makes the workspace read-only immediately |
| Necessary states | IN_PROGRESS, COMPLETED, CANCELLED; failed save; concurrent edits; session expired; long notes; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Workspace; section navigation; editor; save indicator; visual/behavioral specification in the UX spines |
| Figma | Required: new S-19 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S19-F:** Consultation completion requires specified notes/review guards; appointment remains an independent resource. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S19-V:** Desktop offers record context without hiding editor; tablet/mobile use sequential sections and persistent save state. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-20"></a>
## S-20 — Consultation report editor

| Field | Specification |
|---|---|
| Goal / actor | Consultation report editor for clinical practitioner |
| Traceability | FR-012, FR-013; BR-008, BR-011, BR-015; J-04 |
| Entry / exit | S-19 / S-21 → review, version history or save |
| Content / example | Structured narrative, source-backed statements, author edits, unsupported-text flags, changed segments |
| Actions / validation | Edit I-15; save expected version; compare original generation; add/remove reference with support review; no silent auto-save success |
| Permission / visibility | Clinical edit permission for DRAFT only; VALIDATED opens a new version instead |
| Necessary states | DRAFT, dirty, saving, failed, saved, source removed, unsupported citation, conflict, read-only; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Annotated editor; diff; source panel; save indicator; visual/behavioral specification in the UX spines |
| Figma | Required: new S-20 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S20-F:** A save failure preserves entered text; regeneration cannot replace human corrections without explicit new-version choice. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S20-V:** Editor distinguishes generated text and human corrections without coloring entire paragraphs or reducing text contrast. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-21"></a>
## S-21 — AI draft review and processing result

| Field | Specification |
|---|---|
| Goal / actor | AI draft review and processing result for clinical practitioner reviewer |
| Traceability | FR-013, FR-014; BR-011, BR-012; J-04 |
| Entry / exit | S-18 / S-20 / S-40 → sources, edit or submit review |
| Content / example | Claim-by-claim support, dated source excerpt, contradiction/missing-data sections, model/provider mode |
| Actions / validation | Accept/correct/reject passage; inspect evidence; retry failed task; create new draft; never display fake confidence percentages |
| Permission / visibility | Clinical reviewer access; source visibility checked independently; no automatic validation or patient disclosure |
| Necessary states | Partial output rejected as incomplete, timeout, malformed citation, quota, duplicate request, source changed during task; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Review workspace; source panel; task status; error summary; visual/behavioral specification in the UX spines |
| Figma | Required: new S-21 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S21-F:** An existing citation that does not support its claim is still a review defect; manual corrections survive new generation. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S21-V:** Failure and progress occupy calm inline space; source excerpts remain readable at tablet width. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-22"></a>
## S-22 — Version history and comparison

| Field | Specification |
|---|---|
| Goal / actor | Version history and comparison for clinical practitioner; patient only permitted publication metadata |
| Traceability | FR-014; BR-011, BR-012, BR-015; J-04 |
| Entry / exit | Document menu → selected immutable version / compare / new draft |
| Content / example | Version number, author, validation identity/date, corrections, source-set version and publication relationship |
| Actions / validation | Compare two versions; open cited source at captured version; create new draft from validated version |
| Permission / visibility | Clinical full history under clinical permission; patient never receives internal generated drafts or reviewer notes |
| Necessary states | One version, long diff, missing historic source, concurrent new version, read-only, replaced publication link; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Version list; semantic diff; source marker; visual/behavioral specification in the UX spines |
| Figma | Required: new S-22 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S22-F:** Comparison distinguishes source facts, original generation and human edits; immutable approved text is not editable in place. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S22-V:** Insertions/deletions use text labels and accessible contrast; mobile diff is sequential rather than tiny side-by-side columns. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-23"></a>
## S-23 — Human validation and patient publication review

| Field | Specification |
|---|---|
| Goal / actor | Human validation and patient publication review for practitioner with validate/publish capabilities |
| Traceability | FR-014, FR-015; BR-012, BR-013; J-04, J-05 |
| Entry / exit | S-20 / S-21 → VALIDATED document, then separate I-17 publication |
| Content / example | Content version, unresolved issues, evidence inspected, validator identity; publication audience preview separately |
| Actions / validation | Submit for review; validate I-16; return for edits; then publish patient-eligible kind I-17 with audience/attachments |
| Permission / visibility | CLINICAL_VALIDATE and PATIENT_PUBLISH evaluated separately; clinician brief may be validated internally but publication is prohibited; stale or unresolved content blocked as specified |
| Necessary states | Missing source, unresolved contradiction, stale inputs, changed draft, publication forbidden, publish failure; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Review checklist; document preview; audience summary; status; visual/behavioral specification in the UX spines |
| Figma | Required: new S-23 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S23-F:** Validation alone grants no patient visibility; publication requires a distinct explicit action on an immutable eligible version. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S23-V:** Two steps have different labels and consequences; no combined “validate and share” button. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-24"></a>
## S-24 — Stale, replaced or withdrawn content detail

| Field | Specification |
|---|---|
| Goal / actor | Stale, replaced or withdrawn content detail for clinical practitioner; patient-safe lifecycle variant |
| Traceability | FR-014, FR-015, FR-016; BR-013, BR-014; J-05 |
| Entry / exit | Old version link / publication detail → current version or withdrawal notice |
| Content / example | Source-change reason, version lineage, replacement link and timestamp; patient-safe withdrawal message |
| Actions / validation | Regenerate as new draft; compare; replace/withdraw I-18; follow latest permitted publication |
| Permission / visibility | Clinician sees internal reason; patient sees only authorized lifecycle metadata and current published content |
| Necessary states | STALE independent of VALIDATED; PUBLISHED/REPLACED/WITHDRAWN; attachment access revoked; old download link; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Freshness banner; publication notice; version list; visual/behavioral specification in the UX spines |
| Figma | Required: new S-24 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S24-F:** Withdrawn content is not downloadable anew; replacement redirects only after access check and never exposes internal drafts. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S24-V:** Warning explains what changed and what action is possible; old text is not presented as current. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-25"></a>
## S-25 — Patient home

| Field | Specification |
|---|---|
| Goal / actor | Patient home for patient with verified portal record grant |
| Traceability | FR-016; BR-004, BR-013; J-06 |
| Entry / exit | Patient login/context → appointment, preparation or current publication |
| Content / example | Next appointment APT-A-001, preparation progress, published explanation v2, practice contact |
| Actions / validation | Prepare visit; manage own appointment; open published content; change authorized practice record context |
| Permission / visibility | Own separately verified practice record only; no clinical brief or administrative audit |
| Necessary states | No appointments, no publications, replaced/withdrawn notice, revoked link, API unavailable; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Patient shell; next-visit panel; publication row; empty state; visual/behavioral specification in the UX spines |
| Figma | Required: new S-25 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S25-F:** Validated REPORT-A-001-v1 remains absent until explicitly published; staff identity selectors never appear. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S25-V:** Patient typography uses existing larger styles; one clear primary next action and accessible touch targets. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-26"></a>
## S-26 — Patient appointments and booking

| Field | Specification |
|---|---|
| Goal / actor | Patient appointments and booking for patient |
| Traceability | FR-009, FR-016; BR-004, BR-006; J-06 |
| Entry / exit | S-25 → available slots, S-16 own variant or preparation |
| Content / example | Own upcoming/past appointments; anonymized bookable slots; dates/timezone and practitioner |
| Actions / validation | Book I-10; reschedule I-11; cancel I-12 under policy; view no-show fact without editing attendance |
| Permission / visibility | Own record only; slots show availability, never another patient’s identity or reason |
| Necessary states | No slots, conflict on submit, BOOKED/CANCELLED/COMPLETED/NO_SHOW, unavailable service; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Patient appointment list; slot picker; status; visual/behavioral specification in the UX spines |
| Figma | Required: new S-26 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S26-F:** A stale slot losing a booking race fails clearly and preserves choice context; no duplicate visit created. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S26-V:** Mobile date and time selection has a keyboard-friendly list equivalent; status text readable without badges. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-27"></a>
## S-27 — Patient visit preparation form

| Field | Specification |
|---|---|
| Goal / actor | Patient visit preparation form for patient |
| Traceability | FR-010, FR-016; BR-004, BR-008, BR-015; J-06 |
| Entry / exit | S-25 / S-26 → saved draft or submitted preparation; S-17 clinician view |
| Content / example | Concerns, changes since last visit, self-reported treatment/allergy updates; “Je ne sais pas” permitted |
| Actions / validation | Save draft; submit; revise with version; confirm sharing with practice; no diagnosis request or promised immediate response |
| Permission / visibility | Patient own record and linked appointment; draft stays patient-only until submission |
| Necessary states | Empty, draft, submitted, reopened revision, validation error, changed appointment, session expired; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Accessible form; origin help; submission summary; save indicator; visual/behavioral specification in the UX spines |
| Figma | Required: new S-27 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S27-F:** Unknown and unanswered are stored/displayed separately from explicit “non”; submission makes origin/date clear. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S27-V:** Readable prompts and no forced long medical jargon; mobile keyboard does not obscure save action. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-28"></a>
## S-28 — Published patient reports and explanations

| Field | Specification |
|---|---|
| Goal / actor | Published patient reports and explanations for patient |
| Traceability | FR-015, FR-016; BR-013; J-06 |
| Entry / exit | S-25 / patient navigation → S-29 |
| Content / example | Title, publication date, author/validator, document kind, current/replaced/withdrawn metadata |
| Actions / validation | Open current authorized publication; filter period; inspect safe replacement history |
| Permission / visibility | Only explicit publication grants for own record; no unpublished counts or hidden clinical draft titles |
| Necessary states | No publications, long list, withdrawn row, replacement, access removed, pagination failure; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Publication list; status; empty state; visual/behavioral specification in the UX spines |
| Figma | Required: new S-28 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S28-F:** List queries enforce publication grants and exclude clinician-only briefs even if validated. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S28-V:** Patient sees clear publication date rather than technical task timestamps; rows have descriptive link names. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-29"></a>
## S-29 — Patient published content detail

| Field | Specification |
|---|---|
| Goal / actor | Patient published content detail for patient |
| Traceability | FR-015, FR-016; BR-013, BR-016; J-06 |
| Entry / exit | S-28 / notification deep link → authorized attachments or P-03 |
| Content / example | PATIENT-A-001-v2 exact published text, validator/date, published date, version, patient-safe source references |
| Actions / validation | Read, inspect allowed source excerpt, download current published PDF; contact practice through existing contact info |
| Permission / visibility | Audience-allowed snapshot only; internal source documents do not become accessible merely by citation |
| Necessary states | Current, stale warning where public content is affected, replaced, withdrawn, broken permitted source; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Patient document; source reference; lifecycle notice; visual/behavioral specification in the UX spines |
| Figma | Required: new S-29 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S29-F:** Patient cannot open an unpublished source attachment; reference provides approved excerpt/metadata or restriction explanation. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S29-V:** Plain language and generous line length; citation marker remains touchable and readable. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-30"></a>
## S-30 — Patient authorized document viewer and download

| Field | Specification |
|---|---|
| Goal / actor | Patient authorized document viewer and download for patient |
| Traceability | FR-008, FR-016, FR-017; BR-010, BR-013, BR-016; J-06 |
| Entry / exit | S-29 selected attachment / PDF → preview / download / back |
| Content / example | Only included published attachment/version, document title, status and synthetic label |
| Actions / validation | Preview; download; open accessible alternative; recheck permission before retrieval |
| Permission / visibility | File grant is explicit per publication and version; expiring download URL never bypasses authorization |
| Necessary states | Loading, PDF unavailable, unsupported preview, expired link, withdrawn grant, download failure; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Secure preview; publication notice; error summary; visual/behavioral specification in the UX spines |
| Figma | Required: new S-30 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S30-F:** A copied file URL cannot retrieve a document from another practice or after grant withdrawal; local already-downloaded copies cannot be recalled. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S30-V:** Mobile offers download/open alternative; toolbar labels do not cover clinical text. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-31"></a>
## S-31 — Team administration and invitations

| Field | Specification |
|---|---|
| Goal / actor | Team administration and invitations for practice administrator |
| Traceability | FR-003; BR-002, BR-003, BR-005; J-07 |
| Entry / exit | Admin landing / shell → I-01, S-32 or audit |
| Content / example | Members, capabilities, active state and invitation expiry; no clinical snippets |
| Actions / validation | Invite I-01; revoke pending invite; resend; inspect member; paginate |
| Permission / visibility | TEAM_MANAGE in selected practice; no inferred clinical access |
| Necessary states | Empty team, pending/expired/revoked invitation, suspended member, last-admin guard, request failure; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Team table; capability tags; invitation status; empty state; visual/behavioral specification in the UX spines |
| Figma | Required: new S-31 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S31-F:** Admin can manage memberships without opening patient records; invalid role combination is rejected server-side. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S31-V:** Capabilities are concrete readable names; list density supports long emails without clipping actions. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-32"></a>
## S-32 — Membership permissions and deactivation

| Field | Specification |
|---|---|
| Goal / actor | Membership permissions and deactivation for practice administrator |
| Traceability | FR-003; BR-002, BR-003; J-07 |
| Entry / exit | S-31 member → updated membership or deactivation I-02 |
| Content / example | Account, practice-specific capabilities, active status, dependencies and last-admin warning |
| Actions / validation | Change explicit capability set; deactivate with reason; protect last administrator; no global account suspension from practice-only action |
| Permission / visibility | TEAM_MANAGE scoped to practice; own permissions changes guarded; global account state separate |
| Necessary states | Read-only self view, concurrent permission edit, deactivation, pending invitation, dependencies; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Permission form; impact panel; conflict panel; visual/behavioral specification in the UX spines |
| Figma | Required: new S-32 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S32-F:** Removing PRA-A membership does not remove PRA-B membership; active sessions lose the revoked authority. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S32-V:** Clinical, publication and administration permissions grouped by consequence with plain explanatory copy. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-33"></a>
## S-33 — Relevant practice settings

| Field | Specification |
|---|---|
| Goal / actor | Relevant practice settings for practice administrator; practitioner for own availability |
| Traceability | FR-018; BR-002, BR-006, BR-016; J-07 |
| Entry / exit | Admin shell → practice settings / S-15 availability |
| Content / example | Practice name/contact, timezone, booking policy, patient portal contact text, logo usage reference |
| Actions / validation | Edit permitted settings with version; preview timezone impact; clinical defaults cannot invent medical rules |
| Permission / visibility | PRACTICE_SETTINGS; no unrestricted clinical access; clinically meaningful changes require separate authorized capability |
| Necessary states | Initial, unsaved, validation error, saved, concurrent edit, timezone impact; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Settings form; impact panel; save indicator; visual/behavioral specification in the UX spines |
| Figma | Required: new S-33 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S33-F:** Timezone display change never rewrites stored instants or existing source dates; no production credential field. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S33-V:** Settings remain task-oriented; no decorative analytics or unrelated subscription controls. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-34"></a>
## S-34 — Permission-appropriate audit history

| Field | Specification |
|---|---|
| Goal / actor | Permission-appropriate audit history for clinical auditor or practice administrator under separate scope |
| Traceability | FR-018; BR-002, BR-017; J-07 |
| Entry / exit | Record / admin audit → filtered event detail |
| Content / example | Actor, time, action, resource identifier, outcome, correlation ID; clinical detail only with clinical audit authority |
| Actions / validation | Filter date/type/actor; paginate; inspect correction/publication sequence; no edit/delete |
| Permission / visibility | ADMIN_AUDIT sees membership/configuration metadata; clinical events require CLINICAL_AUDIT or equivalent capability |
| Necessary states | No events, long history, restricted fields, export unavailable, partial service error; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Audit table; event detail; filter; restricted field; visual/behavioral specification in the UX spines |
| Figma | Required: new S-34 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S34-F:** Admin-only Mila cannot read source text from an audit payload; publication actions record immutable version and actor. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S34-V:** Dates align and outcome words remain readable; event hierarchy avoids overwhelming full JSON payloads. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-35"></a>
## S-35 — Profile and preferences

| Field | Specification |
|---|---|
| Goal / actor | Profile and preferences for authenticated account holder |
| Traceability | FR-018; BR-003; J-01, J-06 |
| Entry / exit | Shell account menu → saved preference / sign out |
| Content / example | Display identity, locale preference decision notice, theme, timezone display, account security links |
| Actions / validation | Edit own permitted fields; choose existing light/dark theme; sign out; recovery through S-04 |
| Permission / visibility | Own account only; practice membership edits stay in S-32; no silent portal identity relink |
| Necessary states | Saved, unsaved, email-change re-verification if supported later, session stale, error; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Profile form; theme selector; save indicator; visual/behavioral specification in the UX spines |
| Figma | Required: new S-35 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S35-F:** Preference changes do not grant clinical capability or alter medical source dates; French UI remains the design baseline. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S35-V:** Existing light/dark foundations used; all preference controls work with keyboard. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-36"></a>
## S-36 — Forbidden and not-found states

| Field | Specification |
|---|---|
| Goal / actor | Forbidden and not-found states for any actor |
| Traceability | FR-001, FR-002, FR-016; BR-001, BR-002, BR-004; J-01, J-06, J-07 |
| Entry / exit | Unauthorized navigation/object URL → safe home or context selection |
| Content / example | Context-level “Accès non autorisé” vs non-disclosing resource “Élément introuvable”; safe return action |
| Actions / validation | Return to authorized home; switch context when legitimately available; never show guessed patient title |
| Permission / visibility | No resource existence, name, attachment title or clinical content leaked; policy maps 403/404 deliberately |
| Necessary states | 403 for known workspace capability failure; 404 for unowned clinical object; deleted/moved safe link; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Exception page; safe navigation; visual/behavioral specification in the UX spines |
| Figma | Required: new S-36 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S36-F:** SC-ISOLATION returns a non-disclosing state; SC-ADMIN-CLINICAL explains workspace restriction without clinical metadata. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S36-V:** Message and one useful return action are prominent; no alarming illustration or support dead end. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-37"></a>
## S-37 — Unavailable services and partial failure

| Field | Specification |
|---|---|
| Goal / actor | Unavailable services and partial failure for any actor |
| Traceability | FR-013, FR-019; BR-011, BR-018; J-08 |
| Entry / exit | Any failing operation → retry, manual workflow or safe return |
| Content / example | API unavailable, AI unavailable, failed PDF or storage preview; correlation reference without secrets |
| Actions / validation | Retry appropriate boundary; continue manual notes when AI down; preserve safe unsaved data; no fake success |
| Permission / visibility | Only current authorized context data; never use another demo user’s cached state as fallback |
| Necessary states | Offline, reconnecting, 429, 503, timeout, degraded partial content, maintenance/reset; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Error summary; task status; retry action; visual/behavioral specification in the UX spines |
| Figma | Required: new S-37 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S37-F:** AI unavailability preserves existing record and edits; empty fallback never becomes normal-data conclusion. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S37-V:** Essential record information remains separate from failed section; retry does not shift focus unpredictably. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-38"></a>
## S-38 — Expired session and return

| Field | Specification |
|---|---|
| Goal / actor | Expired session and return for any authenticated actor |
| Traceability | FR-001; BR-003, BR-015; J-01 |
| Entry / exit | 401 or inactivity → reauthentication → original authorized context |
| Content / example | Session-expiry explanation; unsaved-text preservation policy; login action |
| Actions / validation | Reauthenticate once; restore eligible unsaved draft only after same account/practice authorization; discard explicitly |
| Permission / visibility | No private data retained across account switch or shown on unauthenticated screen |
| Necessary states | Expired while saving, expired in modal, permission revoked during return, other user signs in; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Session dialog; auth form; unsaved guard; visual/behavioral specification in the UX spines |
| Figma | Required: new S-38 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S38-F:** Expired request is not replayed automatically for publication/booking; sensitive draft is recovered only in verified same context. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S38-V:** Focus moves to the session notice; behind-dialog clinical details are visually obscured. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-39"></a>
## S-39 — Export options and print preview

| Field | Specification |
|---|---|
| Goal / actor | Export options and print preview for clinical practitioner; patient with publication grant |
| Traceability | FR-017; BR-010, BR-013, BR-016; J-02, J-06 |
| Entry / exit | Document export I-19 → preview / generated PDF / return |
| Content / example | Audience, exact version/status, source-reference inclusion, synthetic labeling, page preview |
| Actions / validation | Generate authorized P-01/P-02/P-03; preview long pages; download; no hidden patient publication side effect |
| Permission / visibility | Clinician can export authorized internal draft labeled DRAFT; patient only published allowed version/attachments |
| Necessary states | Generating, failed, too long, stale source snapshot, unauthorized, font/render error; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Export form; A4 preview; task status; visual/behavioral specification in the UX spines |
| Figma | Required: new S-39 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S39-F:** Export freezes permitted version and status; server generation alone is not treated as proof of access control. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S39-V:** A4 composition uses print layout, selectable text and repeated headers rather than application screenshots. Also satisfy SP-A11Y and matching-viewport visual review |

<a id="s-40"></a>
## S-40 — AI processing activity and task detail

| Field | Specification |
|---|---|
| Goal / actor | AI processing activity and task detail for clinical practitioner |
| Traceability | FR-013; BR-011; J-04 |
| Entry / exit | Generate action / S-06 task link → result S-21 or retry |
| Content / example | QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED task, source snapshot, request time, duplicate link, safe error |
| Actions / validation | Open result; cancel queued/running if supported; retry failed task with idempotency and quota; no patient notification on task success |
| Permission / visibility | Authorized task owner/practice record access; no provider payloads or patient data in error traces |
| Necessary states | Queued, running, completion after navigation, failure, timeout, duplicate request, quota exhausted, source changed; shared SP-STATE/SP-SAVE where applicable |
| Responsive | SP-RESPONSIVE; preserve identity, dates, source readability and action order |
| Reused components | Task list; status; error summary; source snapshot; visual/behavioral specification in the UX spines |
| Figma | Required: new S-40 journey frame and relevant state variants; foundations/component source refs per SP-FIGMA |
| Functional acceptance | **AC-S40-F:** Processing success creates a DRAFT output, never validation/publication; duplicate request reuses task instead of duplicating content. Also satisfy SP-SECURITY and the applicable business-rule guards |
| Visual acceptance | **AC-S40-V:** Task state and document state are visibly separate; do not invent a completion percentage. Also satisfy SP-A11Y and matching-viewport visual review |

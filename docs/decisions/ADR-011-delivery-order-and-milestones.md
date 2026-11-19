# ADR-011 — Delivery order and increment 1 milestones, without calendar dates

Status: Accepted by Aymen on 2026-11-19 (UD-40). Drafted the same day at his request. Date: 2026-11-19. Related: UD-16, UD-27, UD-28, UD-37 (O-2), A-16, ADR-002, ADR-010, roadmap sections 1, 3, 8 and 11, stories 10.1 to 10.8.

Étaye is a personal project, so Aymen sets no calendar dates (his statement of 2026-11-19). Without dates, only scope and effort can end a phase; otherwise nothing ends the documentation or the build. **Verified observation (roadmap-assignments.json, 2026-11-19):** increment 1 holds 33 of the 45 stories and 74 of the 96 contract operations, and the mobile increment comes last (UD-37, order 2 then 3) although the portfolio goal is to show an API, a web app and a mobile app. A dependency check shows that the stories of increment 3 depend only on stories of increments 1 and 3, and that the native patient families (S-02, S-04, S-25 to S-29, S-36 to S-38) all belong to increment 1.

**Choice:**

1. **Order.** Increment 1, then increment 3 (mobile), then increment 2 (web completion). Increment numbers stay as identities: renumbering would rewrite every story, trigger and test text. Prerequisites of increment 3 are unchanged: the hosting decision of story 10.3 (inside increment 1) and the native patient screens of DF-19, designed and reviewed before story 10.6.
2. **Six milestones inside increment 1**, in dependency order. A milestone is done when the acceptance designs A and B of its stories pass on the real stack and the journey steps (roadmap section 3) run.

   | Milestone | Stories | Demonstrable result | Acceptance designs |
   |---|---|---|---|
   | M1 Secure skeleton | 10.1, 1.2, 1.1, 10.2, 1.3, 9.1, 1.4 | Sign-in with seeded accounts, practice selection, refused cross-practice access, demo reset (steps 1 and 8, in part) | AT-038, 002, 001, 039, 003, 035, 004 |
   | M2 Record and schedule | 3.1, 3.2, 3.3, 3.4, 4.2, 4.3, 4.4 | Nora reads Claire's record with its conflicting allergy sources, books, cancels and checks in (step 2) | AT-009 to 012, 015 to 017 |
   | M3 Preparation and brief | 4.5, 5.2, 5.3, 5.4, 5.1 | Day view, acknowledged preparation, deterministic brief with citations to the exact source (steps 1, 3 and 4) | AT-018 to 022 |
   | M4 Consultation to validated report | 6.1, 6.2, 6.3, 6.4 | Encounter, draft, correction of the unsupported sentence, version comparison, validation (step 5) | AT-023 to 026 |
   | M5 Publication and patient reading | 7.1, 7.2, 7.3, 7.4, 7.5, 8.2, 8.3 | Separate explanation, explicit publication, replacement, Claire reads, books and exports P-03, mail in the sink (steps 6 and 7) | AT-027 to 031, 033, 034 |
   | M6 Proof and release evidence | 9.3, 10.3, 9.2 | Honest failure recoveries, published isolation report, measured hosting decision, scripted journey in CI (step 8 and the increment 1 exit criteria) | AT-037, 040, 036 |

3. **Effort budgets, not dates.** The unit is a work session, one focused sitting as Aymen counts it. The spike inside story 10.1 is capped at 3 sessions. The other budgets are an [ASSUMPTION] of 1.5 sessions per story, rounded up: M1 11, M2 11, M3 8, M4 6, M5 11, M6 5. They are placeholders: the sessions actually spent on M1 replace them as the measured reference before M2 starts.
4. **Spike exit criteria (story 10.1).** Success means all of: the backend builds with Testcontainers PostgreSQL; one adversarial test passes for two practices under a non-owner role with a transaction-local context, including a reused pooled connection (AT-038-B); one Playwright smoke runs on the real stack; the module verification tool (ArchUnit or Spring Modulith) works with the chosen Spring Boot version; and the workspace tooling is decided and recorded: package manager (pnpm is a candidate, To verify), `apps/*` and `packages/*` layout, TypeScript project references, one CI job per app, and React version alignment between the web app and the Expo SDK (ADR-010). If a criterion fails at the cap, the fallbacks of ADR-002 apply (Spring Boot 3.5, Java 21, PostgreSQL 17) and the report says so. The report goes to `docs/test-artifacts/`.
5. **Cut rule.** A milestone that reaches 150% of its budget stops. Aymen then decides which named items move to increment 2, recorded as a dated roadmap row. [Proposal] Candidates, first to cut first: PD-001 registration variants (UD-17), practice switching (UD-19), patient self-booking and cancelling in S-26 (parts of 4.2, 4.3 and 7.5), and the P-03 export (8.2). Never cut: isolation (10.1, 1.4), the deterministic brief with its citations (5.2, 5.3), human review and validation (6.1 to 6.4), explicit publication and patient reading (7.1 to 7.4).
6. **Documentation follows code.** From the development authorization on, a document changes in the story that changes the behavior, or through a mini-ADR (UD-27); no documentation-only increment is planned.

**Alternatives:**

- Calendar dates: rejected; the project is personal. The only date worth using would be an external moment (an interview, an application) that Aymen names himself.
- Keep the order 2 then 3: rejected; the mobile app would arrive after the whole web completion.
- Remove PD-001, practice switching, patient booking and the patient PDF from increment 1 now: rejected; they are Aymen's decisions (UD-17, UD-19, UD-21) and steps 7 and 8 of the demonstration journey. The cut rule keeps that option open at a milestone gate.
- Renumber the increments: rejected, see point 1.

**Consequences:** Applied on acceptance: UD-40 in the decision register, the order sentence of roadmap section 1 and O-2 changed to 3 then 2, and the milestone markers added to roadmap section 3. Nothing else moves: stories, acceptance designs, the UD-16 and UD-28 gates and the allocation of stories to increments are unchanged, and none of this has any effect before the written development authorization. Story 10.6 comes earlier, so DF-19 becomes the next design task after the CORE families; with Figma limited, it is produced from the offline design-system reference. Budgets are recalibrated after M1. No account, credential or paid service is requested by this ADR.

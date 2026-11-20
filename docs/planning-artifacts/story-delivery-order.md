# Story delivery order

The agreed increment sequence is **1 → 3 → 2**. Increment 1 delivers a complete web journey, increment 3 adds the installable web app and Android, and increment 2 completes the remaining web scope. The iOS gate in increment 4 depends on an explicit decision and access to the required tooling. [Delivery decision](../decisions/ADR-011-delivery-order-and-milestones.md) · [Roadmap and split scope](roadmap.md)

## Where to start

- **10.1** is merged. **10.2** followed as the second platform story. [10.1 implementation record](../implementation-artifacts/story-10-1-implementation-record.md) · [10.2 integration status](https://github.com/Aymenchaghoub/etaye/pull/44)
- **Next: prepare 1.2**, covering sign-in, access recovery and session expiry. It unlocks practice selection in 1.3. Aymen gave written authorization to start after 10.2 merged (UD-48). S-02 (sign-in), S-38 (expired session) and I-22 (session expiry) are approved. The revised password validation state is the remaining approval for S-04 (recovery). [Design review](../design/reviews/2026-11-20-story-1-2-review.md) · [Decision register](decisions-assumptions.md)
- Each row links to its story and lists its direct dependencies. The working order reflects the delivery of 10.1 and 10.2 first and proposes an order for the remaining stories within the accepted milestones. A story can start once its dependencies, design review or named exception, and written authorization are in place. The acceptance tests and scope splits live in the linked stories and roadmap.

## Increment 1 — Complete web journey

### M1 — Secure skeleton

| Story | Depends on |
|---|---|
| [10.1 · Walking skeleton and secure request path](epics/epic-10.md#story-101-walking-skeleton-and-secure-request-path) | — |
| [10.2 · Generated client, token bridge and multi-client foundations](epics/epic-10.md#story-102-generated-client-token-bridge-and-multi-client-foundations) | 10.1 |
| [1.2 · Sign in, recover and expire safely](epics/epic-1.md#story-12-sign-in-recover-and-expire-safely) | 10.1 |
| [1.1 · Activate an intended invitation](epics/epic-1.md#story-11-activate-an-intended-invitation) | 10.1 |
| [1.3 · Select and switch practice context](epics/epic-1.md#story-13-select-and-switch-practice-context) | 1.2 |
| [9.1 · Enter and reset bounded synthetic journeys](epics/epic-9.md#story-91-enter-and-reset-bounded-synthetic-journeys) | 10.1 |
| [1.4 · Demonstrate server-side resource isolation](epics/epic-1.md#story-14-demonstrate-server-side-resource-isolation) | 10.1, 1.3 |

### M2 — Record and schedule

| Story | Depends on |
|---|---|
| [3.1 · Find and register a local patient](epics/epic-3.md#story-31-find-and-register-a-local-patient) | 1.3 |
| [3.2 · Explore chronology and explicit assertions](epics/epic-3.md#story-32-explore-chronology-and-explicit-assertions) | 3.1 |
| [3.3 · Enter and correct measurements](epics/epic-3.md#story-33-enter-and-correct-measurements) | 3.2 |
| [3.4 · Enter and correct laboratory results](epics/epic-3.md#story-34-enter-and-correct-laboratory-results) | 3.2 |
| [4.2 · Book a real appointment](epics/epic-4.md#story-42-book-a-real-appointment) | 3.1 |
| [4.3 · Reschedule or cancel an appointment](epics/epic-4.md#story-43-reschedule-or-cancel-an-appointment) | 4.2 |
| [4.4 · Record arrival, completion or no-show](epics/epic-4.md#story-44-record-arrival-completion-or-no-show) | 4.2 |

### M3 — Preparation and brief

| Story | Depends on |
|---|---|
| [4.5 · Prepare a visit and inspect patient reports](epics/epic-4.md#story-45-prepare-a-visit-and-inspect-patient-reports) | 4.2 |
| [5.2 · Generate a bounded clinician brief](epics/epic-5.md#story-52-generate-a-bounded-clinician-brief) | 3.2, 3.3, 10.1 |
| [5.3 · Inspect dated statement support](epics/epic-5.md#story-53-inspect-dated-statement-support) | 5.2 |
| [5.4 · Handle changed sources and failed tasks](epics/epic-5.md#story-54-handle-changed-sources-and-failed-tasks) | 5.2 |
| [5.1 · Read a daily preparation workspace](epics/epic-5.md#story-51-read-a-daily-preparation-workspace) | 4.2, 5.2 |

### M4 — Consultation to validated report

| Story | Depends on |
|---|---|
| [6.1 · Conduct an encounter with or without appointment](epics/epic-6.md#story-61-conduct-an-encounter-with-or-without-appointment) | 3.2 |
| [6.2 · Draft a consultation report](epics/epic-6.md#story-62-draft-a-consultation-report) | 6.1, 5.2 |
| [6.3 · Review, correct and compare versions](epics/epic-6.md#story-63-review-correct-and-compare-versions) | 6.2 |
| [6.4 · Validate the exact reviewed version](epics/epic-6.md#story-64-validate-the-exact-reviewed-version) | 6.3 |

### M5 — Publication and patient reading

| Story | Depends on |
|---|---|
| [7.1 · Prepare a separately reviewed patient explanation](epics/epic-7.md#story-71-prepare-a-separately-reviewed-patient-explanation) | 6.4 |
| [7.2 · Publish selected validated content](epics/epic-7.md#story-72-publish-selected-validated-content) | 7.1 |
| [7.3 · Replace or withdraw a publication](epics/epic-7.md#story-73-replace-or-withdraw-a-publication) | 7.2 |
| [7.4 · Read only own published information](epics/epic-7.md#story-74-read-only-own-published-information) | 7.2, 1.2 |
| [7.5 · Manage patient appointments and authorized files](epics/epic-7.md#story-75-manage-patient-appointments-and-authorized-files) | 4.2, 4.3, 7.2 |
| [8.2 · Export reports and patient explanations](epics/epic-8.md#story-82-export-reports-and-patient-explanations) | 7.2 |
| [8.3 · Deliver minimal transactional notifications](epics/epic-8.md#story-83-deliver-minimal-transactional-notifications) | 1.2, 4.2, 7.2 |

### M6 — Proof and release evidence

| Story | Depends on |
|---|---|
| [9.3 · Recover unavailable services and releases](epics/epic-9.md#story-93-recover-unavailable-services-and-releases) | 10.1, 1.2 |
| [10.3 · Hosting benchmark and decision](epics/epic-10.md#story-103-hosting-benchmark-and-decision) | 7.4, 9.1, 9.3 |
| [9.2 · Produce real-stack quality and release evidence](epics/epic-9.md#story-92-produce-real-stack-quality-and-release-evidence) | 10.1 |

## Increment 3 — Installable web app and Android

The hosting decision in 10.3 precedes the PWA. The native patient designs (DF-19) need review before 10.6.

| Story | Depends on |
|---|---|
| [10.4 · Installable web app (PWA)](epics/epic-10.md#story-104-installable-web-app-pwa) | 10.2, 10.3 |
| [10.5 · Native session transport and device registry](epics/epic-10.md#story-105-native-session-transport-and-device-registry) | 1.2, 10.2 |
| [10.6 · Native Android app (React Native)](epics/epic-10.md#story-106-native-android-app-react-native) | 10.4, 10.5 |
| [10.7 · Content-free push notifications](epics/epic-10.md#story-107-content-free-push-notifications) | 10.5, 10.6, 8.3 |

## Increment 2 — Web completion

| Story | Depends on |
|---|---|
| [2.1 · Invite and manage team permissions](epics/epic-2.md#story-21-invite-and-manage-team-permissions) | 1.2, 1.3 |
| [2.2 · Deactivate access without losing accountability](epics/epic-2.md#story-22-deactivate-access-without-losing-accountability) | 2.1 |
| [2.3 · Maintain operational practice settings](epics/epic-2.md#story-23-maintain-operational-practice-settings) | 1.3 |
| [2.4 · Inspect scoped audit and own preferences](epics/epic-2.md#story-24-inspect-scoped-audit-and-own-preferences) | 10.1, 1.2 |
| [3.5 · Upload and inspect authorized documents](epics/epic-3.md#story-35-upload-and-inspect-authorized-documents) | 3.2, 10.1 |
| [4.1 · Define availability and exceptions](epics/epic-4.md#story-41-define-availability-and-exceptions) | 1.3 |
| [8.1 · Export a practitioner-only brief](epics/epic-8.md#story-81-export-a-practitioner-only-brief) | 5.2 |

## Increment 4 — Conditional iOS gate

The gate requires a Mac with an iOS Simulator for simulator evidence. Physical distribution or store submission requires a separate decision. [Native client decision](../decisions/ADR-010-native-client-react-native-expo.md)

| Story | Depends on |
|---|---|
| [10.8 · iOS build and distribution gate](epics/epic-10.md#story-108-ios-build-and-distribution-gate) | 10.6 |

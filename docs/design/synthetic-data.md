# Shared synthetic design dataset

Status: Draft / Ready for design. Dataset `etaye-design-v1`, frozen baseline **2 November 2026, 16:00 Europe/Paris**. Authoritative machine-readable content: [synthetic-data.json](synthetic-data.json). All people, practices, events and clinical values are fictional; examples are not medical advice. Existing Figma clinical text is illustration, not a medical rule.

## Reuse rules

Use these identifiers and exact names in every prototype, screen annotation, email and PDF. Readable IDs are design aliases, not UUID-formatted API payloads; a future fixture adapter must map them to deterministic UUIDs. `example.test` addresses must never receive external email. The future public demo must deliver transactional emails to a local preview or safe sink. No real data may be added to screenshots or acceptance fixtures.

Dates in the dataset are ISO 8601 with offsets. French UI renders `3 novembre 2026 à 10 h 30` (appointment APT-A-001) and decimal `68,4 kg`; APIs retain numeric values and units separately. The baseline falls after the daylight-saving change, while September and 20 October use `+02:00`; never replace historical offsets by the current offset.

## Cast and isolation

| Alias | Display | Practice / purpose |
|---|---|---|
| PRA-A | Cabinet des Tilleuls | Main practitioner and patient journey |
| PRA-B | Cabinet du Canal | Independent practice and forbidden-access fixture |
| PR-NORA | Dr Nora Lefebvre | Practitioner, validator and publisher in PRA-A |
| PR-SAMI-A / PR-SAMI-B | Dr Sami Rahmani | One account, two independent memberships; practice-switch fixture |
| PR-ELODIE | Dr Élodie Morel | Practitioner in PRA-B |
| ACC-MILA | Mila Dubois | Administration only in PRA-A; no clinical permission |
| R-A-001 | Claire Martin, born 18 April 1978 | Rich record, portal linked to ACC-CLAIRE |
| R-A-002 | Louis Bernard, born 9 July 1991 | Minimal history, expired portal invitation, no portal access |
| R-B-001 | Hugo Petit, born 25 February 1986 | Record inaccessible from PRA-A |

Practice-wide practitioner sharing and multi-practice accounts are `[ASSUMPTION]` baselines; see [decisions and assumptions](../planning-artifacts/decisions-assumptions.md). No shared clinical record is inferred from a shared account.

## Narrative continuity

1. Claire's 14 September consultation records **an explicit negative declaration at that time** (`SRC-A-001`), not a universal absence of allergy. Laboratory values come from dated manually entered results (`SRC-A-002`, `SRC-A-003`).
2. At the 20 October consultation (`CONS-A-001`), pressure is 134/82 mmHg and the corrected weight is 68,4 kg. The old 86,4 kg entry remains in correction history with reason; trends use the corrected version and expose history.
3. A patient explanation v1 is validated and published on 20 October. A clinical report is also validated but **has no publication grant**.
4. On 2 November, Claire submits preparation for 3 November at 10:30. She reports a past reaction to penicillin but cannot describe it. The source remains patient-reported and unconfirmed; it conflicts with the older declaration. Current reported treatments are **missing**, never “none”.
5. The pre-visit brief (`BRIEF-A-001-v1`) is a clinician-only DRAFT with dated citations and a contradiction flag. It is not a patient explanation and cannot be directly published.
6. The patient explanation v2 preserves Dr Lefebvre's correction of an unsupported generated statement. It is separately validated at 14:20 and explicitly published at 14:25. Publication v1 is atomically REPLACED; its document freshness remains STALE for the record history. No version is silently overwritten.
7. Louis demonstrates minimal inputs and `AI-A-002` demonstrates timeout without a generated output, a risk classification or a reassuring substitute.
8. `SC-ISOLATION`, `SC-ADMIN-CLINICAL`, `SC-NOT-PUBLISHED` and `SC-BOOKING-RACE` supply explicit denial, publication and concurrency design states.

## Fixture variations

These are scenario overrides, not contradictory baseline records. Label the prototype starting point clearly: `V-EMPTY` selects Louis; `V-LONG` repeats dated synthetic observations as distinct IDs with dates (never duplicate an apparent event); `V-STALE` pauses before publication v2; `V-WITHDRAWN` branches from PUB-A-002 through an authorized withdrawal with a reason; `V-CONFLICT` has two editors on the same document version; `V-SESSION` expires the session before a save; `V-AI-DOWN` returns AI-A-002's timeout; `V-LAB-NO-INTERVAL` omits a lab-supplied interval and displays “Intervalle non fourni”.

Never create extra clinical findings merely to fill a chart. Long documents expand the existing narrative with dated source rows and preserved units. Short, long and incomplete print examples are defined in [PDF and email specifications](pdf-email-specifications.md).

Mila has explicitly assigned SCHEDULE_MANAGE and PATIENT_REGISTER in PRA-A in addition to her operational administrator capabilities (PD-001 proposal). This demonstrates appointment lookup/registration without any CLINICAL_READ/WRITE grant. The SCHEDULE_MANAGE-only and administrator-only permission variants for AT-015-E remove those explicit extra grants; they are named test variants, not an assumption that every administrator schedules.

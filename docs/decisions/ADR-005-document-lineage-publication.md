# ADR-005 — Immutable versions and explicit publication

Status: Proposed. Date: 2026-11-02. Related: BR-008/011–015, AD-5/6.

**Choice:** clinical facts have immutable source revisions. An authored document has immutable approved versions, source snapshots, statement-level citations, human edits and a review record. The current mutable draft is guarded by an optimistic version. Publication is a distinct grant to an exact validated version; it does not mutate document content.

Alternative: a single summary field plus `published=true` is simpler but loses the reviewed snapshot, changes patient content while clinicians edit and conflates processing with publication. Event sourcing of every aggregate would preserve history but adds reconstruction and projection complexity beyond this project's needs.

Generated candidate, human edits and source facts remain separately attributable. On a relevant source revision, mark dependent content STALE with the changed source IDs; never silently regenerate or remove an existing publication. A clinician can acknowledge, revise, replace or withdraw. Validation requires source inspection and records validator, date and document revision. Publishing a patient explanation never exposes a clinician brief or an unrestricted source attachment.

A publication's patient-source projection may include safe source title/date/locator. Following a citation opens an underlying attachment only if it has its own explicit patient document grant. Withdrawal blocks future authenticated delivery, but copies already downloaded remain outside system control. Preserve an audit tombstone and replacement relationship.

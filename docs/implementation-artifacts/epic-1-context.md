# Epic 1 Context: Safe access and isolated practice work

## Goal

People reach exactly the practice or patient context granted to their account, including after an invitation, session expiry or a rights change. The demonstration proves refusals with two synthetic practices and a real server-side authorization path.

## Stories

- 1.1 Activate an intended invitation.
- 1.2 Sign in, recover and expire safely.
- 1.3 Select and switch practice context.
- 1.4 Demonstrate server-side resource isolation.

## Requirements & Constraints

An active global account is distinct from an active practice membership and a verified patient portal link. Recheck current capability, practice and record ownership at the operation boundary. Administrative rights grant no clinical access. Missing and inaccessible records share an existence-hiding response. Treat a suspended membership as a change to that practice only. Recovery uses identical public responses for known and unknown accounts and cannot reactivate suspended access.

Invitation acceptance grants exactly its intended membership or patient link, once, to the verified intended recipient. Subsequent invitations and patient-link management follow their own scope. The initial demonstration uses synthetic fixtures and exercises both the allowed and refused operations.

## Technical Decisions

Browser authentication uses one account authority, a secure host-only opaque Spring Session JDBC cookie, header CSRF, ID rotation, finite expiry and reauthorization on each protected request. Recover using a one-use, short-lived digest and invalidate affected sessions after the password changes. Keep credential and token operations inside the identity boundary. Use transaction-local PostgreSQL RLS with restricted roles for practice data. Track successful changes and bounded authorization denials without exposing information about the target practice.

## UX & Interaction Patterns

Clinician and patient sign-in entry copy resolve the same identity without implying a grant. Preserve only eligible unsaved input in memory and restore it after rechecking the same account, practice and resource. Clear protected views promptly on expiry and discard ineligible work. Resolve ambiguous writes before any retry. Practice switching clears scoped navigation and data after unsaved-change handling. Feature acceptance pairs real-stack behavior with opened Étaye reference comparisons, keyboard and axe checks.

## Cross-Story Dependencies

The secure request skeleton from 10.1 is the runtime base. The 10.2 client and token bridge are available. Story 1.3 consumes 1.2 session grants, and 1.4 consumes the practice context from 1.3. The branded recovery notification and its general delivery path belong to 8.3, which depends on the recovery flow in 1.2.

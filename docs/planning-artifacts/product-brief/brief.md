---
title: "Étaye — Product brief"
status: draft
created: 2026-11-02
updated: 2026-11-02
review_status: ready-for-document-review
user_approval: not-requested
---

# Product brief: Étaye

Étaye is a portfolio web application for general medical practices with several practitioners. It helps a physician prepare a consultation, understand what changed in a longitudinal patient record, inspect the evidence behind important statements, and turn reviewed work into information deliberately shared with the patient. A complementary patient portal supports the same journey through appointments, visit preparation and access to published reports.

The product name means supporting a statement with evidence. Its defining rule is practical: **important statements should be supported by identifiable, dated sources that the reviewer can inspect**. A citation must support its statement; the mere presence of a source marker does not establish truth. AI produces drafts and helps navigate relevant information. The practitioner remains responsible for review, corrections, validation and publication.

## The problem and proposed value

A physician preparing a follow-up visit must assemble history, observations, measurements, results and documents recorded at different times. A useful preparation tool must distinguish a missing fact from a negative observation, reveal contradictions and preserve corrections. The portfolio hypothesis is that a chronology and a sourced brief can make that work easier to inspect. No user research or clinical effectiveness measurement has yet validated this hypothesis.

The demonstrable outcome is a complete, inspectable journey: a practitioner sees the upcoming appointment, reviews changes and missing information, opens a source from a brief, conducts a consultation, corrects an AI draft, validates a version and explicitly publishes selected content. The patient then sees only that publication and authorized attachments. A second practice demonstrates that isolation is real rather than a filtered view.

## Who it serves

The primary user is a general practitioner who needs a dense, calm workspace. The patient needs a simpler, readable experience for appointment management, preparation and published information. A practice administrator manages memberships and operational settings; that role does not grant clinical access. Aymen Chaghoub is the portfolio author and final product/design decision-maker.

## Scope and character

The planned V2 includes isolated practices, team permissions, longitudinal records, appointments, visit preparation, structured clinical observations and laboratory results, ordinary document management, AI-assisted drafts with source lineage, human review, explicit patient publication, audit history and branded PDFs. Intelligent extraction from laboratory PDFs is a later increment with mandatory extraction review; ordinary uploads and manual result entry remain in the initial scope.

The visual starting point is the existing Étaye Figma identity and logo. The interface remains French during this phase. English repository documentation serves international contributors; an English-facing demo is an open product decision, not authorization for duplicated screens or a translation project.

Billing, commercial subscriptions, prescriptions, audio transcription, autonomous diagnosis or treatment, a global medical risk score and conversational search are excluded from the initial scope. All public examples use fictional people and synthetic data. Étaye claims no clinical validation, certification, regulatory compliance or suitability for real patient data.

## Success and next gate

Proposed portfolio success signals are demonstrable, not measured: a complete practitioner, patient and administration journey; inspectable support for important generated statements; preserved human corrections; denied cross-practice access; and an evidence-backed future release process. Speed of generation and visual polish must never be improved by silently removing missing-data warnings, review steps or publication checks.

The current deliverable is a planning package for complete Figma design. Every required surface, interaction, error and print layout must be designed and reviewed before development, unless Aymen explicitly accepts a specific exception. Documentation completion does not authorize implementation.

Detailed scope and verification live in [the PRD](../prd/prd.md); structural assumptions live in [the decision register](../decisions-assumptions.md). The [addendum](addendum.md) routes the supplied depth to its authoritative documents.

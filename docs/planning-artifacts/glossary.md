# Domain glossary

Status: Draft for design. Date: 2026-11-02. Use these terms consistently in requirements, contracts, designs and stories. Interface examples remain French.

| English term | French interface term | Definition and boundary |
|---|---|---|
| Account | Compte | Authentication identity. One account may have several practice memberships and separately verified patient portal links. It is not a clinical record. |
| Practice | Cabinet | Isolated care organization owning its records and operational resources. |
| Practice membership | Appartenance au cabinet | Account-to-practice relationship with activation state and explicit capabilities. |
| Practitioner | Praticien | A clinical professional profile associated with an active practice membership. Administration alone does not make an account a practitioner. |
| Practice administrator | Administration du cabinet | Membership allowed to manage team and settings. No implicit clinical access. |
| Patient | Patient | Practice-local person identity used to organize care. No global patient matching is assumed. |
| Patient record | Dossier patient | Longitudinal clinical aggregate owned by exactly one practice and associated with its local patient identity. |
| Portal link | Accès à l’espace patient | Verified grant linking an account to one practice-local patient record. Email similarity is insufficient proof. |
| Appointment | Rendez-vous | Reserved time interval with a practitioner. A distinct scheduling object, not a consultation. |
| Availability | Disponibilité | Practitioner working intervals and exceptions from which valid appointment slots can be offered. |
| Consultation | Consultation | Clinical encounter, with an optional appointment link. Walk-in or unscheduled encounters are allowed. |
| Consultation report | Compte rendu de consultation | Versioned account of an encounter, authored or reviewed by a practitioner. Patient access still requires publication. |
| Pre-visit brief | Brief de préparation | Clinician-only synthesis of relevant changes, sources and gaps before a visit. It cannot be directly published to a patient. |
| Patient explanation | Explication destinée au patient | Separately reviewed content written for patient comprehension, with its own validation and explicit publication. |
| Observation | Observation | A dated clinical assertion with provenance and assertion status. Patient-reported and practitioner-observed facts remain distinguishable. |
| Measurement | Mesure | Structured observed value with measured time, unit, provenance and correction lineage. |
| Laboratory result | Résultat biologique | Manually recorded analyte value or qualitative result with specimen/result dates, unit and supplied reference interval where known. No inferred normality. |
| Source | Source | Identifiable dated record item or document version that can support a statement; may include an exact excerpt/page/location. |
| Source citation | Référence de source | Link from a statement to a specific source version/location. Relevance must be reviewed. |
| Document version | Version du document | Immutable validated text or editable draft snapshot with author, business status and lineage. |
| AI processing task | Traitement IA | Asynchronous execution record with processing status, input snapshot and attempt metadata. Not a document status. |
| Human review | Relecture humaine | Practitioner examination of draft facts, citations, omissions, language and corrections. |
| Validation | Validation | Explicit practitioner attestation for a particular document version. It grants no patient visibility. |
| Publication | Publication au patient | Explicit audience grant to an immutable validated version plus a controlled patient-visible projection and chosen attachments. |
| Freshness | Actualité des sources | CURRENT or STALE indication, independent of validation and publication. New relevant data can make old content stale without erasing it. |
| Replacement | Remplacement | Atomic publication of a new validated version and marking the former publication REPLACED. |
| Withdrawal | Retrait | Ending access through the portal to a publication, retaining audit/history; prior downloads cannot be recalled. |
| Correction | Correction | New version or superseding fact preserving the original, reason, actor and date. |
| Missing information | Information manquante | Not known or not recorded. It must never be represented as explicitly absent/normal. |
| Explicitly negative information | Absence explicitement renseignée | A source states the absence of something, for an identified scope and date; it is not inferred from an empty field. |
| Reference interval | Intervalle de référence | Range supplied with a laboratory result, including source/context. It is not a universal diagnostic threshold. |
| Reported treatment | Traitement déclaré | Historical or patient-reported treatment information. This is not a prescribing capability. |
| Audit event | Événement d’audit | Attributable, dated event about access or change with resource and practice scope; avoid clinical body text in logs. |
| Synthetic data | Données fictives | Invented people and clinical examples for demonstration, never real patient information or clinical advice. |

## Status axes

Use the exact enums in [business rules](business-rules.md#canonical-state-vocabulary). “Approved” is not a document enum. “Approved by Aymen” is reserved for an actual product/design approval; “Ready for design” never means ready for implementation.

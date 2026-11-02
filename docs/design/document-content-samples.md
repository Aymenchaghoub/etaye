# Synthetic document content samples

Status: Ready for design. These are actual fictional content blocks for A4 layout work, not generated PDFs or clinical advice. Data aliases map to synthetic-data.json. Long versions expand explanation/source detail without inventing further clinical events. Final page count depends on the reviewed composition.

## P-01 — Practitioner brief

### Short content

Démonstration — personnes et données fictives. Réservé aux professionnels. Brouillon v 1. Claire Martin, Cabinet des Tilleuls. Visite prévue le 3 novembre 2026 à 10 h 30. Dernière consultation : 20 octobre 2026.

Depuis la dernière consultation, la patiente a signalé le souvenir d’une réaction à la pénicilline [SRC-A-005, 2 novembre]. Cette déclaration diffère de celle du 14 septembre [SRC-A-001]. Les traitements actuels ne sont pas renseignés [SRC-A-006]. Les résultats de créatinine disponibles sont 96 puis 104 µmol/L [SRC-A-002, SRC-A-003]. Ces observations ne constituent pas une proposition de diagnostic ou de traitement.

### Long content

Use the short content followed by these complete sections and the source appendix below. Keep all numeric values/units together; the appendix is deliberately sufficiently wide and detailed to test wrapping and repeated table headers.

**Chronologie et provenance.** La déclaration du 14 septembre est une déclaration négative recueillie ce jour. Elle ne permet pas de conclure à une absence permanente d’allergie. La déclaration du 2 novembre est rapportée par la patiente, reste non confirmée et ne précise ni date exacte ni nature de la réaction. Présenter les deux sources sans en effacer une ni les fusionner en une certitude.

**Mesures relues.** La pression artérielle du 14 septembre est 128/80 mmHg ; celle du 20 octobre est 134/82 mmHg. Le poids du 20 octobre a été corrigé de 86,4 à 68,4 kg pour une erreur de saisie. La valeur historique et le motif restent consultables. La correction ne crée pas un nouvel événement de mesure.

**Biologie disponible.** Créatinine 96 µmol/L au prélèvement du 14 septembre puis 104 µmol/L au prélèvement du 12 octobre ; intervalle fictif fourni 60–110 µmol/L. Glucose 5,2 mmol/L au 12 octobre, intervalle fictif fourni 4,1–6,1 mmol/L. Les unités, dates et intervalles proviennent des sources du jeu fictif ; aucun seuil clinique supplémentaire n’est appliqué. Les résultats du document ont été saisis manuellement, sans extraction intelligente.

**Informations à compléter.** La liste actuelle des traitements est absente du jeu de données. Ne pas écrire «aucun traitement». La réaction évoquée à la pénicilline reste à préciser. Une source datée et consultable ne garantit pas à elle seule qu’elle soutient une interprétation : chaque phrase de synthèse doit être relue dans son contexte.

**Travail préparatoire.** Les questions et déclarations de la patiente restent séparées des observations du praticien. L’ouverture d’une source retourne à la phrase correspondante. Une nouvelle source pertinente rend le brouillon obsolète ; une nouvelle génération ne doit pas effacer une correction humaine. Ce document n’est pas publiable directement dans l’espace patient.

### Incomplete content

Louis Bernard — historique minimal. Aucun antécédent renseigné dans le jeu fictif ne signifie pas absence d’antécédent. Traitements actuels : non renseignés. Aucune synthèse produite après l’échec du traitementAI-A-002. Résultat de traitement : échec, délai dépassé ; aucune classification de risque et aucun texte rassurant de remplacement.

## P-02 — Consultation report

### Short content

Compte rendu de consultation — version 1 VALIDATED, réservée aux professionnels tant qu’aucune publication n’existe. Claire Martin.20 octobre 2026. Dr Nora Lefebvre, Cabinet des Tilleuls. Résultats disponibles relus avec la patiente. Pression artérielle 134/82 mmHg. Liste des traitements non renseignée dans le dossier fictif. Validation :20 octobre 2026 à 10h 40. Sources SRC-A-003,004,006.

### Long content

Use the short content plus the following structured paragraphs and the clinician-authorized appendix.

**Motif et contexte.** Consultation de suivi du 20 octobre. Les données présentées sont celles disponibles pour cet événement. Ce compte rendu historique ne se transforme pas automatiquement lorsqu’une préparation d’une visite ultérieure est saisie. Une correction d’une source citée doit en revanche entraîner une nouvelle revue de la version concernée.

**Informations rapportées et observations.** La pression a été mesurée au cabinet à 10h 10 ; la source en conserve la date et l’auteur. Les résultats du 12 octobre ont été relus. Les traitements actuels ne sont pas renseignés ; cette absence est une limite du contenu, pas une constatation d’absence de traitement. Aucun diagnostic ni prescription n’est ajouté pour compléter la présentation.

**Traçabilité.** La consultationCONS-A-001 est reliée à un rendez-vous terminé, mais les deux objets restent distincts. La fin de la consultation n’a pas publié ce rapport. L’auteur et le validateur sont affichés séparément même s’il s’agit ici de la même praticienne. Les versions ultérieures nécessiteront un nouveau brouillon et conserveront la version validée.

**Diffusion.** Cette version du rapport n’a pas de publication dans le jeu de données. Le portail patient doit donc refuser son téléchargement, même si une autre explication a été publiée. Une publication future sélectionnerait explicitement la version, le destinataire et les pièces autorisées. Les brouillons et annotations internes resteraient exclus de cette projection.

### Incomplete content

Compte rendu — BROUILLON, non validé. Motif saisi ; préparation de visite non reçue. Partie observations à compléter. Intervalle de référence non fourni pour le scénarioV-LAB-NO-INTERVAL. Ne pas afficher de date de validation ou de publication. Une zone incomplète reste explicitement incomplète, sans texte automatique qui suggère un examen normal.

## P-03 — Patient explanation

### Short content

Démonstration — personnes et données fictives. Claire Martin. Explicationv 2, validée le 2 novembre 2026 à 14h 20 et publiée à 14h 25 par Dr Nora Lefebvre.

Vous avez signalé un souvenir de réaction à la pénicilline. Cette information sera précisée lors de votre prochaine consultation. La liste actuelle de vos traitements reste à compléter. [Extraits datés autorisés de SRC-A-001,005,006.]

### Long content

Use the short content plus the following patient-facing paragraphs. Do not include the full clinician appendix: use only the exact excerpts approved in the selected Publication sourceGrants.

**Ce qui a été partagé.** Cette explication reprend les informations relues puis publiées pour votre espace. Elle ne donne pas accès à tous les documents de votre dossier. Les dates de rédaction, de validation et de publication sont indiquées séparément pour que vous puissiez reconnaître la version consultée.

**Votre déclaration.** Le 2 novembre, vous avez indiqué vous souvenir d’une réaction à la pénicilline dans votre jeunesse, sans pouvoir préciser laquelle. La déclaration recueillie le 14 septembre mentionnait qu’aucune allergie n’était connue à cette date. Ces déclarations sont présentées avec leur date ; aucune des deux n’est supprimée pour simplifier le texte.

**Informations manquantes.** La liste actuelle de vos traitements n’est pas renseignée dans cet exemple fictif. Cela ne signifie pas que vous ne prenez aucun traitement. La préparation de votre visite permet de signaler les informations et questions que vous souhaitez aborder ; elle reste distincte de ce que le praticien a personnellement observé.

**Sources et corrections.** Les repères ouvrent les extraits que votre praticien a choisi de partager. Un document non partagé reste inaccessible. Cette version remplace une explication précédente après correction d’une phrase qui présentait une ancienne déclaration comme une certitude permanente. Le brouillon d’origine n’est pas publié.

**Version disponible.** Une information publiée peut être remplacée ou retirée explicitement. Un lien vers une version retirée ne permet plus d’en lire le contenu ou de la télécharger. Un fichier déjà téléchargé ne peut pas être repris automatiquement. L’espace indique la version actuelle ou un avis de retrait sans révéler les annotations internes du praticien.

### Incomplete content

Explication non publiable : traitements actuels non renseignés et informations à relire. Un traitementAIen échec ne produit aucun texte publiable. Le praticien peut compléter une explication manuelle puis la relire et la valider ; aucune apparition automatique dans l’espace patient ne doit être représentée.

## Clinician source appendix for long P-01/P-02 layouts

A source may be displayed as historical context without becoming evidence for every statement. Show correction/unknown markers and repeat column headings across pages. Patient P-03 uses only curated grants, not this internal appendix.

| Source revision | Event / recorded date | Type / author | Original fictional content and locator |
|---|---|---|---|
| SRC-A-001 | 2026-09-14T09:30:00+02:00 / 2026-09-14T09:45:00+02:00 | PATIENT_REPORTED / PR-NORA | Aucune allergie connue, selon la déclaration recueillie ce jour. — section:allergies |
| SRC-A-002 | 2026-09-14T08:00:00+02:00 / 2026-09-15T10:00:00+02:00 | DOCUMENT_REPORTED / PR-NORA | Créatinine 96 µmol/L, intervalle fourni 60–110 µmol/L. — LAB-A-001 |
| SRC-A-003 | 2026-10-12T08:00:00+02:00 / 2026-10-13T10:00:00+02:00 | DOCUMENT_REPORTED / PR-SAMI-A | Exemple de compte rendu de laboratoire fictif : créatinine 104 µmol/L ; glucose 5,2 mmol/L. — DOC-A-001:page-1 |
| SRC-A-004 | 2026-10-20T10:10:00+02:00 / 2026-10-20T10:12:00+02:00 | PRACTITIONER_OBSERVED / PR-NORA | Pression artérielle mesurée au cabinet : 134/82 mmHg. — MEAS-A-002 |
| SRC-A-005 | 2026-11-02T09:10:00+01:00 / 2026-11-02T09:10:00+01:00 | PATIENT_REPORTED / ACC-CLAIRE | Je me souviens d’une réaction à la pénicilline dans ma jeunesse. Je ne sais plus laquelle. — PREP-A-001:allergies |
| SRC-A-006 | 2026-10-20T10:30:00+02:00 / 2026-10-20T10:35:00+02:00 | PRACTITIONER_OBSERVED / PR-NORA | Consultation de suivi : résultats disponibles relus avec la patiente. Traitements actuels non renseignés dans ce jeu de données. — CONS-A-001:report |
| SRC-A-007-v 1 | 2026-10-20T10:12:00+02:00 / 2026-10-20T10:13:00+02:00 | PRACTITIONER_OBSERVED / PR-NORA | Poids saisi : 86,4 kg. Valeur historique corrigée ensuite. — MEAS-A-003:version-1 |
| SRC-A-007-v 2 | 2026-10-20T10:12:00+02:00 / 2026-10-20T10:15:00+02:00 | PRACTITIONER_OBSERVED / PR-NORA | Poids corrigé : 68,4 kg. — MEAS-A-003:version-2 |
| SRC-A-008 | 2026-09-14T09:35:00+02:00 / 2026-09-14T09:36:00+02:00 | PRACTITIONER_OBSERVED / PR-NORA | Pression artérielle : 128/80 mmHg. — MEAS-A-001 |

For deterministic long-table pagination testing, repeat the appendix as explicitly labeled “Layout test copy 1/2/3 — same source events, not additional observations”. This is a print stress variant, never extra medical history or a chart series. Review each resulting page and all repeated headers.

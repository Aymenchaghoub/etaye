#!/usr/bin/env python3
"""Apply review corrections to a disposable generated HTML design, never to the receipt."""

import argparse
import importlib
import json
from pathlib import Path
import re
import shutil
import sys

from bs4 import BeautifulSoup

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--root", type=Path, required=True)
args = parser.parse_args()
root = args.root.resolve()
if root.is_relative_to(HERE.parents[1]) or not root.parent.name.startswith(
    "etaye-design-rebuild-"
):
    parser.error("Corrections must run in a disposable external reconstruction")
sys.path.insert(0, str(root / "gen"))
L = importlib.import_module("lib")
D = importlib.import_module("screens_dossier")
C = importlib.import_module("screens_consult")
B = importlib.import_module("screens_brief")
A = importlib.import_module("screens_admin")
P = importlib.import_module("screens_patient")
G = importlib.import_module("screens_agenda")
site = root / "site"
additions = []


def href(key):
    return "../" + key + ".html"


def add(key, name, body, note="Scenario variant; synthetic design only."):
    sid, slug = key.split("/")
    path = site / "ecrans" / sid / (slug + ".html")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(L.page(name, body))
    additions.append(
        {
            "id": sid,
            "slug": slug,
            "name": name,
            "label": note,
            "module": "review_corrections",
            "path": f"ecrans/{key}.html",
            "viewports": ["desktop", "tablet", "mobile", "narrow"],
        }
    )


def professional(title, body, actor="nora", record="R-A-001"):
    return L.pro_shell(
        "Patients",
        '<div class="etaye-page">' + L.entete(title, "", "") + body + "</div>",
        actor,
        banner=L.banniere(record) if record else None,
    )


def link_button(soup, button, target, close=False):
    button["data-next"] = target
    if close:
        button["data-close"] = "true"


def replace_text(soup, old, new):
    for node in list(soup.find_all(string=lambda text: text and old in text)):
        node.replace_with(str(node).replace(old, new))


# Exact clinician source versions and only authorized patient excerpts.
for source in L.DATA["sources"]:
    sid = source["id"]
    body = L.source_drawer_body(sid, 1)
    own_record = source["recordId"]
    own_actor = "sami_b" if source["practiceId"] == "PRA-B" else "nora"
    add(
        "I-09/source-" + sid.lower(),
        "Source datée",
        L.tiroir(
            professional("Source du dossier", "", own_actor, own_record),
            "Source datée",
            body,
            L.btn(
                "Revenir au dossier",
                href_=href("S-09/hugo" if own_actor == "sami_b" else "S-09/claire"),
            ),
        ),
    )
for grant in L.by_id("publications", "PUB-A-002")["sourceGrants"]:
    source = L.by_id("sources", grant["sourceId"])
    body = (
        "<p>"
        + L.esc(grant["publicLabel"])
        + " · "
        + L.d_long(source["occurredAt"])
        + "</p><blockquote>"
        + L.esc(grant["approvedExcerpt"])
        + "</blockquote>"
    )
    add(
        "S-29/extrait-" + source["id"].lower(),
        "Extrait partagé",
        L.tiroir(
            P.s29(),
            "Extrait partagé",
            body,
            L.btn("Revenir à la phrase", patient=True, href_=href("S-29/v2")),
        ),
    )

# Preserve practice and current actor, with independently scoped Canal surfaces.
add(
    "S-06/sami-a",
    "Aujourd'hui — Dr Sami Rahmani",
    professional(
        "Lundi 2 novembre 2026",
        "<p>Cabinet des Tilleuls · Dr Sami Rahmani</p><p>Louis Bernard · 14:00 · arrivée enregistrée ; consultation distincte en cours.</p>"
        + L.btn("Ouvrir la consultation de Louis", href_=href("S-19/en-cours")),
        "sami_a",
        None,
    ),
)
hugo = L.by_id("records", "R-B-001")
add(
    "S-07/canal",
    "Patients — Cabinet du Canal",
    professional(
        "Patients du Cabinet du Canal",
        "<p>Recherche limitée à ce cabinet.</p>"
        + L.btn("Ouvrir Hugo Petit", href_=href("S-09/hugo")),
        "sami_b",
        None,
    ),
)
add(
    "S-09/hugo",
    "Dossier de Hugo Petit",
    professional(
        "Chronologie de Hugo Petit",
        "<p>Note datée du Cabinet du Canal : "
        + L.esc(L.by_id("sources", "SRC-B-001")["text"])
        + "</p>"
        + L.btn("Ouvrir la source de cette note", href_=href("I-09/source-src-b-001"))
        + "<p>Aucune information du Cabinet des Tilleuls n’est affichée.</p>"
        + L.btn("Voir le rendez-vous", href_=href("S-16/hugo")),
        "sami_b",
        "R-B-001",
    ),
)
appointment_b = L.by_id("appointments", "APT-B-001")
add(
    "S-14/canal",
    "Agenda — Cabinet du Canal",
    professional(
        "Agenda du Cabinet du Canal",
        "<p>Hugo Petit · "
        + L.dh_patient(appointment_b["startsAt"])
        + " · Dr Élodie Morel</p>"
        + L.btn("Voir le rendez-vous", href_=href("S-16/hugo")),
        "sami_b",
        None,
    ),
)
add(
    "S-16/hugo",
    "Rendez-vous de Hugo Petit",
    professional(
        "Rendez-vous de Hugo Petit",
        "<p>"
        + L.dh_patient(appointment_b["startsAt"])
        + " · Dr Élodie Morel · réservé</p><p>Aucune consultation n’est créée automatiquement.</p>",
        "sami_b",
        "R-B-001",
    ),
)
add(
    "S-15/canal",
    "Disponibilités — Cabinet du Canal",
    professional(
        "Vos disponibilités",
        "<p>Aucune disponibilité enregistrée pour Dr Sami Rahmani dans ce cabinet.</p>",
        "sami_b",
        None,
    ),
)
add(
    "S-35/canal",
    "Profil — Cabinet du Canal",
    professional(
        "Dr Sami Rahmani",
        "<p>Contexte actif : Cabinet du Canal. La publication patient n’est pas autorisée dans ce cabinet.</p>",
        "sami_b",
        None,
    ),
)
for sid, title in [("S-18", "Briefs"), ("S-20", "Comptes rendus"), ("S-40", "Tâches")]:
    add(
        sid + "/canal",
        title + " — Cabinet du Canal",
        professional(title, "<p>Aucun élément dans ce cabinet.</p>", "sami_b", None),
    )

# A revoked membership is not an active clinical read-only capability.
add(
    "S-19/acces-revoque",
    "Accès au cabinet retiré",
    L.auth_shell(
        "<h1>Votre accès à ce cabinet a été retiré</h1><p>Les informations du dossier ne sont plus affichées. Choisissez un autre contexte autorisé.</p>"
        + L.btn("Choisir un contexte", href_=href("S-05/choix"))
    ),
)

# A retry creates a new task: no invented output, identifier or timestamp.
add(
    "S-40/louis-en-file",
    "Nouvelle tâche pour Louis Bernard",
    professional(
        "Brief de préparation — Louis Bernard",
        "<p>Nouvelle demande en file d’attente · tâche précédente AI-A-002 en échec.</p><p>Le nouvel identifiant et les dates seront attribués à l’envoi. Aucun brouillon produit, aucune publication.</p>"
        + L.btn("Revenir au dossier de Louis", href_=href("S-09/louis")),
        "sami_a",
        "R-A-002",
    ),
    "Retry scenario: linked new task, identity allocated on submission; no fabricated dataset ID.",
)

# Context-specific conflict resolution, using actual current fields rather than another document.
for slug, title, body, target in [
    (
        "identite",
        "Comparer les données d’identité",
        "<p>Claire Martin · données administratives. La copie locale et la version courante sont conservées. Choisissez les champs à reporter dans une nouvelle modification.</p>",
        "S-08/modifier",
    ),
    (
        "mesure",
        "Comparer les versions de la mesure",
        "<p>Poids du 20 octobre · 68,4 kg, correction SRC-A-007-v2. La saisie locale ne remplace pas cette révision sans réconciliation et motif.</p>",
        "I-06/correction",
    ),
    (
        "disponibilite",
        "Comparer les disponibilités",
        "<p>AVL-A-001 · Dr Nora Lefebvre. Les créneaux déjà réservés restent protégés. La copie locale est conservée avant rechargement.</p>",
        "S-15/semaine",
    ),
]:
    comparison = {
        "identite": [
            ("Nom", "Martin", "Martin"),
            ("Prénom", "Claire", "Claire"),
            ("Naissance", "18/04/1978", "18/04/1978"),
            ("Révision", "Copie ouverte avant conflit", "Version courante à recharger"),
        ],
        "mesure": [
            (
                "Ancienne référence",
                "86,4 kg · avant correction",
                "68,4 kg · SRC-A-007-v2",
            ),
            (
                "Nouvelle valeur proposée",
                "68,4 kg · saisie non enregistrée",
                "68,4 kg · SRC-A-007-v2",
            ),
            (
                "Motif",
                "Correction d’une erreur de saisie",
                "Correction d’une erreur de saisie",
            ),
            ("Mesurée le", "20/10/2026 10:12", "20/10/2026 10:12"),
        ],
        "disponibilite": [
            ("Praticienne", "Dr Nora Lefebvre", "Dr Nora Lefebvre"),
            (
                "Intervalle demandé",
                "03/11/2026 09:00–12:00",
                "03/11/2026 09:00–12:00 · AVL-A-001",
            ),
            ("Engagement conservé", "APT-A-001", "10:30–11:00 réservé pour Claire"),
        ],
    }[slug]
    body += L.tableau(
        ["Champ", "Copie locale", "État de référence courant"],
        [[L.esc(v) for v in row] for row in comparison],
        stack=True,
    )
    body += "<p>Les valeurs de référence sont affichées sans inventer de changement concurrent absent du jeu fictif. Rechargez la version courante puis choisissez explicitement les champs à reporter ; aucune fusion automatique.</p>"
    add(
        "I-21/" + slug,
        title,
        professional(
            title,
            body + L.btn("Préparer une nouvelle modification", href_=href(target)),
        ),
    )

# New patient-explanation descendant preserves corrected text and purpose.
v2 = L.by_id("documentVersions", "PATIENT-A-001-v2")
add(
    "S-21/nouvelle-explication",
    "Nouveau brouillon d’explication",
    professional(
        "Nouvelle version de l’explication pour Claire",
        L.st("business", "DRAFT")
        + "<p>Issue de la version 2 ; la version validée et sa publication restent intactes.</p>"
        + L.champ(
            "Texte de la nouvelle version",
            v2["humanEditedText"] if "humanEditedText" in v2 else v2["text"],
            "new-explanation",
            kind="textarea",
            rows=6,
        )
        + L.btn("Relire ce nouveau brouillon", href_=href("S-21/nouvelle-relecture")),
    ),
    "New-version scenario, no new dataset version ID assigned; corrected baseline preserved.",
)
add(
    "S-21/nouvelle-relecture",
    "Relire la nouvelle version",
    professional(
        "Relecture de la nouvelle version",
        "<p>Brouillon descendant de PATIENT-A-001-v2. Le texte corrigé est conservé ; aucune validation ni publication nouvelle n’est simulée sans revue du nouveau contenu et de ses sources.</p>"
        + "<blockquote>"
        + L.esc(v2["text"])
        + "</blockquote>"
        + L.btn(
            "Revenir au nouveau brouillon", href_=href("S-21/nouvelle-explication")
        ),
    ),
)

# Missing laboratory interactions: qualitative entry and immutable correction lineage.
qualitative = (
    L.champ("Analyse", "", "qual-analyte", required=True)
    + L.champ("Résultat textuel tel que fourni", "", "qual-value", required=True)
    + L.champ("Date de prélèvement", "", "qual-date", placeholder="JJ/MM/AAAA")
    + L.champ("Source et emplacement", "", "qual-source", required=True)
    + "<p>Aucune valeur numérique ou unité n’est déduite d’un résultat textuel.</p>"
)
add(
    "I-07/qualitatif",
    "Saisir un résultat textuel",
    L.tiroir(
        D.s12(),
        "Résultat textuel",
        qualitative,
        L.btn("Vérifier le résultat", href_=href("I-07/qualitatif-revue"))
        + L.btn("Annuler", href_=href("S-12/claire")),
    ),
    "Qualitative template: no new clinical result invented.",
)
add(
    "I-07/qualitatif-revue",
    "Vérifier la saisie textuelle",
    professional(
        "Vérifier avant l’enregistrement",
        "<p>Le texte original, sa source et sa date restent distincts d’une interprétation. Aucun résultat fictif supplémentaire n’est enregistré dans ce prototype.</p>"
        + L.btn("Revenir à la saisie", href_=href("I-07/qualitatif")),
    ),
)
correction = (
    "<p>Révision conservée : LAB-A-002 · créatinine 104 µmol/L · 12 octobre 2026 · SRC-A-003.</p>"
    + L.champ("Nouvelle valeur", "", "correct-lab", required=True)
    + L.champ("Unité d’origine", "µmol/L", "correct-unit", readonly=True)
    + L.champ(
        "Motif de correction", "", "correct-reason", required=True, kind="textarea"
    )
    + "<p>Une nouvelle révision conservera la valeur précédente, l’auteur, la date et le motif. La source n’est jamais écrasée.</p>"
)
add(
    "I-07/correction",
    "Corriger un résultat",
    L.tiroir(
        D.s12(),
        "Corriger ce résultat",
        correction,
        L.btn("Vérifier la correction", href_=href("I-07/correction-revue"))
        + L.btn("Annuler", href_=href("S-12/claire")),
    ),
    "Correction template of an existing lab; no unprovided replacement value.",
)
add(
    "I-07/correction-revue",
    "Historique de correction",
    professional(
        "Vérifier la nouvelle révision",
        "<p>Avant : 104 µmol/L, SRC-A-003. Après : valeur à confirmer depuis la source et motif obligatoire.</p><p>Les documents dépendants seront réévalués ; aucune validation ni publication automatique.</p>"
        + L.btn("Revenir à la correction", href_=href("I-07/correction")),
    ),
)

# Retain Louis through manual consultation/report/export, with a draft rather than Claire's approval.
add(
    "S-20/louis-enregistre",
    "Compte rendu de Louis — brouillon",
    C.s20("nouveau"),
    "Manual report scenario; no dated clinical source or validation invented.",
)
add(
    "I-19/louis",
    "Exporter le brouillon de Louis",
    L.tiroir(
        C.s20("nouveau"),
        "Exporter le brouillon de Louis",
        "<p>CONS-A-002 · Louis Bernard · brouillon interne non validé. Le PDF reprend uniquement ce contenu incomplet.</p>",
        L.btn("Ouvrir le PDF du brouillon", href_="../../docs/P-02-incomplet.pdf")
        + L.btn("Revenir au compte rendu", href_=href("S-20/louis-enregistre")),
    ),
)
add(
    "I-05/motif",
    "Saisir le motif de Louis",
    professional(
        "Motif de la consultation de Louis",
        L.champ(
            "Motif de la consultation",
            "Première consultation",
            "louis-reason",
            required=True,
            kind="textarea",
        )
        + "<p>Saisie du praticien à partir du motif du rendez-vous, qui reste non associé à la consultation.</p>"
        + L.btn("Enregistrer le motif", href_=href("I-05/enregistre")),
        "sami_a",
        "R-A-002",
    ),
)
add(
    "S-19/louis-terminee",
    "Consultation de Louis terminée",
    professional(
        "Consultation terminée",
        "<p>Louis Bernard · CONS-A-002 · Dr Sami Rahmani. Variante après l’enregistrement des notes : le rendez-vous n’est toujours pas associé.</p><p>Compte rendu : brouillon interne. La fin de la consultation ne valide ni ne publie ce document. La date de fin sera attribuée lors de l’action.</p>"
        + L.btn("Revenir au compte rendu", href_=href("S-20/louis-enregistre")),
        "sami_a",
        "R-A-002",
    ),
    "Completion scenario after the frozen baseline, no fabricated timestamp or validation.",
)

# A complete known Claire booking scenario, distinct from a live reservation.
add(
    "I-10/claire",
    "Réserver pour Claire Martin",
    professional(
        "Vérifier le rendez-vous de Claire",
        "<p>Cabinet des Tilleuls · Dr Nora Lefebvre · 3 novembre 2026, 10:30–11:00 · consultation de suivi.</p>"
        + L.btn("Réserver ce créneau", href_=href("S-16/claire"))
        + L.btn("Voir le conflit de réservation", href_=href("I-10/conflit-claire")),
    ),
    "Replay before APT-A-001 booking; synthetic transition only, no new appointment ID.",
)
add(
    "I-10/conflit-claire",
    "Créneau déjà réservé",
    professional(
        "Ce créneau n’est plus disponible",
        "<p>Claire Martin · Cabinet des Tilleuls · Dr Nora Lefebvre · 3 novembre, 10:30. Aucune seconde réservation créée ; le choix et le motif de consultation de suivi sont conservés.</p>"
        + L.btn("Voir le rendez-vous existant", href_=href("S-16/claire")),
    ),
)
add(
    "I-11/claire",
    "Déplacer le rendez-vous de Claire",
    G.i11(),
    "Move scenario for APT-A-001; required reason; no other patient.",
)

# Missing lab states use labeled layout scenarios, not invented measured values.
for slug, title, content in [
    (
        "textuel",
        "Résultat textuel",
        "Un résultat qualitatif conserve le texte exact fourni, sa date et sa source. Aucun résultat supplémentaire n’est présent dans le jeu fictif.",
    ),
    (
        "hors-intervalle",
        "Valeur hors intervalle fourni",
        "Gabarit sans valeur inventée : l’étiquette « Hors intervalle fourni » est textuelle et ne signifie pas un diagnostic.",
    ),
    (
        "unites-incompatibles",
        "Unités différentes",
        "Les séries d’unités incompatibles restent séparées. Aucune conversion implicite ni courbe commune.",
    ),
    (
        "a-verifier",
        "Transcription à vérifier",
        "Résultat saisi manuellement depuis le document : la vérification reste nécessaire. Aucune extraction automatique.",
    ),
    (
        "corrige",
        "Historique du résultat",
        "Une nouvelle révision conserve la valeur antérieure, la source et le motif ; les références historiques restent identifiables.",
    ),
]:
    add(
        "S-12/" + slug,
        title,
        professional(
            title,
            '<section class="etaye-panneau"><div class="etaye-panneau__corps"><h2>'
            + title
            + "</h2><p>"
            + content
            + "</p>"
            + L.btn(
                "Saisir un résultat textuel"
                if slug == "textuel"
                else "Examiner la correction",
                href_=href(
                    "I-07/qualitatif" if slug == "textuel" else "I-07/correction"
                ),
            )
            + "</div></section>",
        ),
        "Explicit laboratory presentation template; no new clinical value or dataset observation.",
    )

# Audit details belong to the selected event, not one reused publication.
for index, row in enumerate(A.CLIN_EV):
    when, who, action, resource, note = row
    detail = (
        '<dl class="etaye-def">'
        + "".join(
            "<dt>" + L.esc(k) + "</dt><dd>" + L.esc(v) + "</dd>"
            for k, v in [
                ("Date", when),
                ("Auteur", who),
                ("Action", action),
                ("Ressource", resource),
                ("Résultat", "Réussi"),
                ("Corrélation", "Non fournie"),
            ]
        )
        + "</dl>"
    )
    add(
        "I-25/evenement-" + str(index),
        "Détail de l’événement",
        L.tiroir(
            A.s34("clinique"),
            "Détail de l’événement",
            detail,
            L.btn("Revenir à l’historique", href_=href("S-34/clinique")),
        ),
        "Illustrative audit projection from dataset facts; not a real event log.",
    )

source_maps = {
    "S-10": ["SRC-A-005", "SRC-A-001"],
    "S-11": ["SRC-A-004", "SRC-A-007-v2", "SRC-A-008"],
    "S-12": ["SRC-A-003", "SRC-A-002"],
    "S-17": ["SRC-A-005", "SRC-A-001"],
    "S-18": ["SRC-A-005", "SRC-A-001", "SRC-A-002", "SRC-A-003"],
    "S-20": ["SRC-A-003", "SRC-A-004", "SRC-A-006"],
    "S-23": ["SRC-A-001", "SRC-A-005", "SRC-A-006"],
    "S-29": ["SRC-A-005", "SRC-A-001", "SRC-A-006"],
    "I-15": ["SRC-A-001", "SRC-A-005", "SRC-A-006"],
    "S-19": ["SRC-A-006", "SRC-A-004", "SRC-A-007-v2"],
    "S-22": ["SRC-A-001", "SRC-A-005", "SRC-A-006"],
}

# Named prototype transitions. Unimplemented secondary behaviors are explicitly identified, never fake success.
transitions = {
    "S-02/connexion": {"Se connecter": "S-05/un-seul"},
    "S-03/equipe": {"Activer mon compte": "S-31/equipe"},
    "I-01/ouvert": {"Envoyer l'invitation": "I-01/envoye"},
    "I-03/ouvert": {
        "Activer mon espace patient": "S-25/accueil",
        "Activer mon espace": "S-25/accueil",
    },
    "S-27/brouillon": {
        "Envoyer au cabinet": "S-27/envoyee",
        "Enregistrer le brouillon": "S-27/brouillon",
    },
    "S-19/en-cours": {"Enregistrer": "I-05/enregistre"},
    "I-05/ouvert": {
        "Enregistrer": "I-05/enregistre",
        "Enregistrer la section": "I-05/enregistre",
    },
    "I-05/enregistre": {"Fermer": "S-19/en-cours"},
    "S-20/nouveau": {
        "Enregistrer": "S-20/louis-enregistre",
        "Soumettre à la relecture": "S-20/louis-enregistre",
    },
    "I-15/ouvert": {
        "Enregistrer la correction": "I-15/sale",
        "Enregistrer": "S-21/corrigee",
    },
    "I-15/sale": {
        "Enregistrer la correction": "S-21/corrigee",
        "Enregistrer": "S-21/corrigee",
    },
    "I-16/ouvert": {
        "Valider la version": "S-23/valide",
        "Valider cette version": "S-23/valide",
        "Poursuivre la lecture": "S-23/validation",
        "Poursuivre la relecture": "S-23/validation",
    },
    "I-17/ouvert": {
        "Publier pour le patient": "S-23/publie",
        "Garder en interne": "S-23/valide",
    },
    "I-18/remplacer": {"Remplacer la publication": "I-18/termine"},
    "I-18/retrait": {
        "Retirer l’accès": "S-24/retire",
        "Retirer l'accès": "S-24/retire",
    },
    "I-23/ouvert": {"Réessayer": "I-23/relance"},
    "I-23/relance": {"Suivre la tâche": "S-40/louis-en-file"},
    "S-40/louis-en-file": {"Annuler la tâche": "S-40/annule"},
}
manifests = [
    p
    for p in sorted((site / "manifest").glob("*.json"))
    if p.name != "review_additions.json"
]
states = [m for p in manifests for m in json.loads(p.read_text())] + additions
by_key = {m["id"] + "/" + m["slug"]: m for m in states}

for key, m in by_key.items():
    path = site / m["path"]
    soup = BeautifulSoup(path.read_text(), "html.parser")
    sid, slug = key.split("/")
    if key == "S-19/lecture-seule":
        soup = BeautifulSoup(
            (site / "ecrans/S-19/acces-revoque.html").read_text(), "html.parser"
        )
    if key == "I-09/source-src-b-001":
        soup.body["data-safe-return"] = href("S-09/hugo")
    # Move template commentary into a clearly separate design annotation after the product surface.
    notes = []
    for node in list(soup.select(".doc-note, .etaye-3, .mail-var")):
        text = node.get_text(" ", strip=True)
        if (
            "gabarit" in text.lower()
            or "variante" in text.lower()
            or "fixture" in text.lower()
            or "ne pas écrire" in text.lower()
        ) and len(text) < 650:
            notes.append(text)
            node.decompose()
    if sid.startswith("P-"):
        replace_text(soup, ", fixture déterministe", "")
        replace_text(
            soup,
            "Aucune date de validation ni de publication : ce brouillon ne porte pas de pied de validation.",
            "Document interne · non validé et non publié.",
        )
        replace_text(soup, "Ne pas écrire « aucun traitement ».", "")
    if sid == "M-02":
        for paragraph in list(soup.select(".mail__corps p, .mail-texte")):
            if "date d'expiration" in paragraph.get_text():
                text = paragraph.get_text().replace(
                    "date d'expiration (non fournie par le jeu de démonstration)",
                    "la date indiquée sur la page d’activation",
                )
                paragraph.clear()
                paragraph.append(text)
                notes.append(
                    "Portal invitation expiry is absent from the dataset; the real configured expiry must be inserted before delivery."
                )
        replace_text(soup, "expire le la date indiquée", "expire à la date indiquée")
    if key == "S-05/choix":
        choices = soup.find_all("a", string=lambda s: s and s.strip() == "Ouvrir")
        for choice, target in zip(choices, ["S-06/sami-a", "S-06/jour-vide"]):
            choice["href"] = href(target)
    # Stable demographic/grant context through every Canal navigation target.
    if "Cabinet du Canal" in soup.get_text() and key in [
        "S-06/jour-vide",
        "I-09/source-src-b-001",
    ] + [
        a["id"] + "/" + a["slug"] for a in additions if a["slug"] in ["canal", "hugo"]
    ]:
        for count in soup.select(".etaye-nav__compte"):
            count.decompose()
        for a in soup.find_all("a", href=True):
            match = re.fullmatch(r"\.\./(S-\d+)/[^?]+\.html", a["href"])
            if match and match[1] in [
                "S-06",
                "S-07",
                "S-14",
                "S-15",
                "S-18",
                "S-20",
                "S-35",
                "S-40",
            ]:
                a["href"] = href(
                    match[1] + ("/jour-vide" if match[1] == "S-06" else "/canal")
                )
        for a in soup.find_all("a", href="#"):
            if "Hugo" in a.get_text():
                a["href"] = href("S-09/hugo")
    if sid == "S-10":
        for item in soup.select("li"):
            if "SRC-A-005" in item.get_text():
                for a in item.find_all("a"):
                    if "Ouvrir la source" in a.get_text():
                        a["href"] = href("I-09/source-src-a-005")
    if sid in ["S-08", "I-06", "S-15", "I-24"] and slug in ["conflit", "erreur"]:
        target = {
            "S-08": "identite",
            "I-06": "mesure",
            "S-15": "disponibilite",
            "I-24": "disponibilite",
        }[sid]
        for a in soup.find_all("a", href=True):
            if "/I-21/" in a["href"]:
                a["href"] = href("I-21/" + target)
    if sid == "S-22":
        for a in soup.find_all("a", href=True):
            if "Créer un brouillon" in a.get_text():
                a["href"] = href("S-21/nouvelle-explication")
    if key == "I-06/erreur":
        for node in soup.find_all(string=lambda s: s and "40 à 300" in s):
            node.replace_with(
                "Erreur : vérifiez le format et l’unité de la mesure ; aucune borne clinique n’est déduite de cet exemple."
            )
    replace_text(
        soup,
        "Les traitements n'ont pas pu être chargés",
        "Les tâches n’ont pas pu être chargées",
    )
    replace_text(
        soup, "Aucun traitement n'a été annulé", "Aucune tâche n’a été annulée"
    )
    if sid in ["I-11", "S-26"]:
        for label in soup.find_all("label"):
            if (
                "Motif" in label.get_text()
                or "Raison du déplacement" in label.get_text()
            ):
                replace_text(label, "(facultatif)", "")
                control = soup.find(id=label.get("for"))
                if control:
                    control["required"] = ""
                    control["aria-required"] = "true"
        if (
            sid == "S-26"
            and slug == "deplacer"
            and not any("Motif" in l.get_text() for l in soup.find_all("label"))
        ):
            target = soup.select_one("main")
            target.append(
                BeautifulSoup(
                    L.champ("Motif du déplacement", "", "move-reason", required=True),
                    "html.parser",
                )
            )
    if key == "I-15/non-soutenu":
        for error in soup.select(".etaye-resume-erreurs, .etaye-champ__aide"):
            if "repère" in error.get_text():
                error.decompose()
        for control in soup.select("[aria-invalid]"):
            del control["aria-invalid"]
        for button in soup.find_all("button", disabled=True):
            if "Enregistrer" in button.get_text():
                del button["disabled"]
        for node in soup.find_all(
            string=lambda s: (
                s
                and (
                    "source" in s.lower()
                    and ("obligatoire" in s.lower() or "soutenu" in s.lower())
                )
            )
        ):
            if node.parent.name not in ["script", "style"]:
                node.replace_with(
                    "Une information explicitement manquante peut être enregistrée sans lui inventer de source."
                )
    if key == "S-38/enregistrement":
        for node in soup.find_all(
            string=lambda s: (
                s
                and (
                    "rien" in s.lower()
                    or "aucun" in s.lower()
                    or "enregistr" in s.lower()
                )
            )
        ):
            if node.parent.name not in ["script", "style", "title", "button", "a"]:
                node.replace_with(
                    "L’issue de l’enregistrement est inconnue. Après reconnexion, vérifiez le résultat avant de réessayer ; aucune opération ne sera rejouée automatiquement."
                )
    if key == "S-16/louis":
        for h in soup.find_all(["h2", "h3"]):
            if "Consultation" in h.get_text():
                h.string = "Consultation distincte · non associée à ce rendez-vous"
    if sid == "S-34" and slug == "clinique":
        for i, a in enumerate(
            soup.find_all(
                "a", string=lambda s: s and "Afficher le détail autorisé" in s
            )
        ):
            a["href"] = href("I-25/evenement-" + str(i))
    # The practitioner's future intention is not supported by an old negative allergy declaration.
    if sid in ["S-29", "P-03"]:
        for marker in list(soup.select("button.etaye-repere, span.doc-ref")):
            if marker.get_text(strip=True).strip("[]") == "2":
                previous = str(marker.previous_sibling or "")
                if "prochaine consultation" in previous:
                    marker.decompose()
    for marker in soup.select("button.etaye-repere"):
        if marker.find_parent(attrs={"inert": True}):
            continue
        if sid == "I-09" or marker.find_parent(attrs={"role": "dialog"}):
            marker.name = "span"
            marker.attrs = {"class": marker.get("class", [])}
            continue
        number = re.sub(r"\D", "", marker.get_text())
        label = marker.get("aria-label", "")
        match = re.search(r"SRC-A-\d+(?:-v\d+)?", label)
        mapping = source_maps.get(sid, [])
        if sid == "S-21":
            mapping = (
                source_maps["I-15"]
                if slug in ["non-soutenu", "corrigee"]
                else source_maps["S-18"] + ["SRC-A-006"]
            )
        if sid == "S-23" and slug == "brief":
            mapping = source_maps["S-18"]
        local_source = None
        for parent in marker.parents:
            if parent.name in ["body", "main"]:
                break
            ids = set(re.findall(r"SRC-A-\d+(?:-v\d+)?", parent.get_text()))
            if len(ids) == 1:
                local_source = next(iter(ids))
                break
        source = (
            match[0]
            if match
            else local_source
            or (
                mapping[int(number) - 1]
                if number and int(number) <= len(mapping)
                else None
            )
        )
        if source:
            marker["data-next"] = href(
                ("S-29/extrait-" if sid == "S-29" else "I-09/source-") + source.lower()
            )
            marker["data-source"] = source
            # Inline immutable source markup avoids a nested dialog/iframe and keeps the editor alive.
            template_id = "source-content-" + source.lower()
            if not soup.find(id=template_id):
                template = soup.new_tag("template", id=template_id)
                if sid == "S-29":
                    grant = next(
                        g
                        for g in L.by_id("publications", "PUB-A-002")["sourceGrants"]
                        if g["sourceId"] == source
                    )
                    content = (
                        "<p>"
                        + L.esc(grant["publicLabel"])
                        + "</p><blockquote>"
                        + L.esc(grant["approvedExcerpt"])
                        + "</blockquote>"
                    )
                else:
                    content = L.source_drawer_body(source, 1)
                content = BeautifulSoup(content, "html.parser")
                for button in content.find_all("button"):
                    button.name = "span"
                    button.attrs = {"class": button.get("class", [])}
                template.append(content)
                soup.body.append(template)
            marker["data-template"] = template_id
    for a in soup.find_all("a", href=True):
        if key == "I-23/relance" and "/S-40/en-file.html" in a["href"]:
            a["href"] = href("S-40/louis-en-file")
    if sid in ["I-16", "I-17", "I-18", "I-19", "I-09"]:
        for b in soup.find_all(["button", "a"]):
            if (
                b.get_text(strip=True)
                in ["Fermer", "Revenir à la phrase", "Poursuivre la lecture"]
                or b.get("aria-label") == "Fermer"
            ):
                b["data-close"] = "true"
    if key == "I-19/patient" or (sid == "S-29" and slug.startswith("extrait-")):
        soup.body["data-safe-return"] = href("S-29/v2")
        for button in soup.find_all("button", attrs={"aria-label": "Fermer"}):
            button["data-close"] = "true"
    # Buttons route to actual designed outcomes; an action never mutates the synthetic dataset.
    for b in soup.find_all("button"):
        label = b.get_text(" ", strip=True)
        for name, target in transitions.get(key, {}).items():
            if label == name:
                link_button(soup, b, href(target))
        if sid == "I-19" and "Créer le PDF" in label:
            link_button(
                soup,
                b,
                "../../docs/"
                + ("P-03-court.pdf" if slug == "patient" else "P-01-court.pdf"),
            )
        if sid == "I-28" and label == "Télécharger":
            link_button(soup, b, "../../docs/P-03-court.pdf")
        if sid == "I-07" and "Enregistrer le résultat" in label:
            link_button(soup, b, href("S-12/claire"))
        if sid == "I-15" and "Enregistrer" in label:
            link_button(soup, b, href("S-21/corrigee"))
        if sid == "I-11" and "Confirmer le déplacement" in label:
            link_button(soup, b, href("M-04/modifie"))
            b["data-required-fields"] = "mv-r"
        if sid == "S-26" and slug == "annuler" and "Annuler" in label:
            b["data-required-fields"] = "cancel-reason"
            link_button(soup, b, href("S-26/annule"))
        if b.get("aria-label") == "Fermer":
            b["data-close"] = "true"
        if b.get("aria-label") == "Ouvrir le menu":
            b["data-menu"] = "true"
            b["aria-expanded"] = "false"
    # Existing native fields retain real labels; visual required indicators also become semantic.
    for label in soup.select("label[for]"):
        if label.select_one(".etaye-req"):
            control = soup.find(id=label["for"])
            if control:
                control["aria-required"] = "true"
    # Repair known local anchors rather than hiding failures in a link checker.
    for a in soup.find_all("a", href=True):
        if a["href"] == "#n-mesures":
            a["href"] = "#mesures"
            if not soup.find(id="mesures"):
                target = soup.select_one("main")
                if target:
                    target["id"] = "mesures"
        if a["href"] == "#n-sources":
            a["href"] = "#sources"
            if not soup.find(id="sources"):
                target = soup.select_one(".etaye-notes") or soup.select_one("main")
                if target:
                    target["id"] = "sources"
        if a["href"] == "#rv-c" and not soup.find(id="rv-c"):
            control = soup.select_one("input")
            if control:
                a["href"] = "#" + control.get("id", "contenu")
        if a["href"] == "#v1" and not soup.find(id="v1"):
            target = soup.select_one("main")
            if target:
                target["id"] = "v1"
    if key == "S-26/annuler":
        control = soup.find("textarea")
        if control:
            label = soup.find("label", attrs={"for": control.get("id")})
            if label:
                label["for"] = "cancel-reason"
                label.clear()
                label.append("Motif de l’annulation *")
            control["id"] = "cancel-reason"
            control["aria-required"] = "true"
            control["required"] = ""
    for anchor in soup.find_all("a", href=True):
        label = anchor.get_text(" ", strip=True)
        if label in transitions.get(key, {}):
            anchor["href"] = href(transitions[key][label])
    if sid in ["S-30", "I-28"]:
        replace_text(soup, "2 pages", "1 page")
        replace_text(soup, "Page 1 / 2", "Page 1 / 1")
        preview = soup.select_one(".etaye-visionneuse__page")
        if preview:
            preview.clear()
            img = soup.new_tag(
                "img",
                src="../../docs/P-03-court-p1.png",
                alt="Page 1 du PDF publié pour Claire Martin, version 2",
            )
            img["style"] = "width:100%;height:auto;background:white"
            preview.append(img)
        for b in soup.find_all("button", string=lambda s: s and "Page suivante" in s):
            b.decompose()
    if sid == "S-27":
        # Retain the unapproved sharing-choice variant as a proposal; do not silently adopt a new policy.
        for label in list(soup.find_all("label")):
            if "accepte de partager" in label.get_text().lower():
                label["data-proposal"] = "sharing-acknowledgement"
                notes.append(
                    "Sharing acknowledgement is a design proposal, not a legal consent decision or approved API field."
                )
    if sid == "I-09" and ("source-src-a-001" in slug or slug == "allergie"):
        for block in soup.select("blockquote"):
            if "Aucune allergie connue" in block.get_text() and not block.get_text(
                strip=True
            ).startswith("«"):
                block.insert(0, "« ")
                block.append(" »")
    if not sid.startswith("P-"):
        aside = soup.new_tag(
            "aside",
            attrs={"class": "design-notes", "lang": "en", "data-review-only": ""},
        )
        aside.string = (
            "Design prototype — fictional scenarios, no application persistence or real service calls. "
            + (" ".join(notes) if notes else "")
        )
        soup.body.append(aside)
        soup.head.append(
            soup.new_tag(
                "link", rel="stylesheet", href="../../css/review-corrections.css"
            )
        )
        script = soup.new_tag("script", src="../../js/prototype.js", defer=True)
        soup.body.append(script)
    path.write_text(str(soup))

for name, folder in [("prototype.js", "js"), ("review-corrections.css", "css")]:
    target = site / folder
    target.mkdir(exist_ok=True)
    shutil.copyfile(HERE / name, target / name)
(site / "manifest/review_additions.json").write_text(
    json.dumps(additions, ensure_ascii=False, indent=2) + "\n"
)
# Prefer the exact canonical SVG geometry over separately transformed producer copies.
canonical = HERE.parents[1] / "docs/design/figma/assets"
logo_names = {
    "logo-light.svg": "etaye-logo.svg",
    "logo-dark.svg": "etaye-logo-sombre.svg",
    "logo-monochrome.svg": "etaye-logo-monochrome.svg",
    "logo-patient.svg": "etaye-logo-espace-patient.svg",
    "mark-light.svg": "etaye-signe.svg",
    "mark-monochrome.svg": "etaye-signe-monochrome.svg",
    "app-icon-light.svg": "etaye-icone-app.svg",
    "app-icon-dark.svg": "etaye-icone-app-sombre.svg",
}
for original, target in logo_names.items():
    shutil.copyfile(canonical / original, site / "logos" / target)
# This is changed output: the producer's old self-review cannot approve the new rendering.
evidence_path = root / "review/evidence.json"
previous = json.loads(evidence_path.read_text())
historical = root / "review/evidence.received.json"
if not historical.exists():
    historical.write_text(json.dumps(previous, ensure_ascii=False, indent=2) + "\n")
for entry in previous.values():
    entry["status"] = "Changed — independent re-review required"
    entry["reviewer"] = ""
    entry["date"] = ""
    entry["opened"] = []
evidence_path.write_text(json.dumps(previous, ensure_ascii=False, indent=2) + "\n")
print(
    "Corrected",
    len(by_key),
    "design states; added",
    len(additions),
    "review variants; no application code.",
)

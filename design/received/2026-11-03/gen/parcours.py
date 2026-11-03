"""Navigable journeys: site/parcours.html lists each journey as ordered steps that open the exact state pages.
Every step must exist in the manifests (the script fails otherwise). Run after build.py."""
import glob, json, os, sys
import os as _os, sys as _sys; _sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__))); from paths import ROOT
import lib

SITE = f"{ROOT}/site"
J = [
    ("Praticienne — entrée et journée", "Dr Nora Lefebvre se connecte, choisit son contexte et ouvre sa journée.",
     ["S-01/accueil", "S-02/connexion", "S-05/un-seul", "S-06/nora"]),
    ("Préparation d'une visite", "De l'accueil au brief sourcé de Claire Martin, puis à l'export A4 réservé aux professionnels.",
     ["S-06/nora", "S-16/claire", "S-17/claire", "S-18/brouillon", "S-21/revue", "I-09/ouvert", "S-39/options", "P-01/court"]),
    ("Texte généré : correction, validation, publication", "SC-UNSUPPORTED-DRAFT : la phrase générée est rejetée et corrigée, validée à 14:20, publiée séparément à 14:25, puis lue par la patiente.",
     ["S-21/non-soutenu", "I-15/ouvert", "I-15/sale", "S-21/corrigee", "S-22/comparaison", "S-23/validation", "I-16/ouvert", "S-23/valide", "I-17/ouvert", "S-23/publie", "M-05/html", "S-29/v2", "P-03/court"]),
    ("Remplacement et retrait d'une publication", "La version 1 devient « Sources modifiées » ; elle est remplacée, puis la variante V-WITHDRAWN retire l'accès.",
     ["S-24/perime", "I-18/ouvert", "I-18/remplacer", "I-18/termine", "S-24/remplace", "S-29/remplacee", "I-18/retrait", "S-24/retire", "M-05/retrait", "S-29/retiree"]),
    ("Consultation et compte rendu", "Consultation de Louis Bernard sans rendez-vous lié, notes structurées, compte rendu et export.",
     ["S-19/en-cours", "I-05/ouvert", "I-05/enregistre", "S-19/terminer", "S-20/nouveau", "S-20/lecture-seule", "I-19/ouvert", "P-02/court"]),
    ("Agenda", "Prendre, déplacer et suivre un rendez-vous ; conflit de réservation.",
     ["S-14/jour", "I-10/ouvert", "I-10/conflit", "S-16/claire", "I-11/ouvert", "M-04/modifie", "I-13/ouvert"]),
    ("Patiente — espace patient", "Activation, préparation de visite, rendez-vous, information publiée et téléchargement.",
     ["M-02/html", "I-03/ouvert", "S-25/accueil", "S-27/brouillon", "S-27/envoyee", "S-26/liste", "S-28/liste", "S-29/v2", "S-29/extrait", "S-30/apercu", "I-28/ouvert", "P-03/court"]),
    ("Administration", "Mila Dubois gère l'équipe sans accès clinique ; dernier administrateur protégé ; audit administratif.",
     ["S-31/equipe", "I-01/ouvert", "I-01/envoye", "M-01/html", "S-03/equipe", "S-32/membre", "I-02/ouvert", "S-32/soi-meme", "S-34/admin", "I-25/admin", "S-36/interdit"]),
    ("Échec d'une tâche et reprise", "AI-A-002 échoue (V-AI-DOWN) : rien n'est inventé, la consultation continue à la main.",
     ["S-06/nora", "S-40/echec", "I-23/ouvert", "I-23/relance", "S-18/echec", "S-37/ia", "P-01/incomplet"]),
    ("Sécurité et isolement", "Session expirée, accès refusé, isolement entre cabinets.",
     ["S-38/expiree", "I-22/ouvert", "S-36/introuvable", "S-05/choix", "S-06/jour-vide"]),
]


def main():
    man = {}
    for f in glob.glob(f"{SITE}/manifest/*.json"):
        for m in json.load(open(f)):
            man[f'{m["id"]}/{m["slug"]}'] = m
    missing = [s for _, _, steps in J for s in steps if s not in man]
    if missing:
        sys.exit(f"missing steps: {missing}")
    esc = lib.esc
    parts = []
    for i, (t, d, steps) in enumerate(J, 1):
        li = "".join(f'<li><a href="{man[s]["path"]}"><strong>{esc(s.split("/")[0])}</strong> {esc(man[s]["name"])} · {esc(s.split("/")[1])}</a>'
                     f'<div class="etaye-3" style="font-size:13px">{esc(man[s]["label"])}</div></li>' for s in steps)
        parts.append(f'<section class="etaye-pile"><h2 class="ui-section" style="margin:0">{i}. {esc(t)}</h2><p class="etaye-2" style="margin:0">{esc(d)}</p>'
                     f'<ol style="margin:0;padding-left:24px;display:grid;gap:6px">{li}</ol></section>')
    body = (f'<main class="etaye-page" style="max-width:1100px;margin:0 auto" id="contenu"><header class="etaye-ligne" style="justify-content:space-between">'
            f'<a href="index.html"><img src="logos/etaye-logo.svg" alt="Étaye" style="height:28px"></a>{lib.demo_notice()}</header>'
            f'<div><h1 style="font:500 28px/36px var(--font-texte);margin:0">Parcours navigables</h1><p class="etaye-2" style="margin:8px 0 0">'
            f'{len(J)} parcours, {sum(len(s) for _, _, s in J)} étapes. Chaque étape ouvre l\'état exact ; les boutons des écrans relient aussi les étapes voisines. '
            f'<a href="index.html">Tous les états</a>.</p></div>{"".join(parts)}</main>')
    html = lib.page("Parcours", body).replace('href="../../', 'href="').replace("../../logos", "logos")
    open(f"{SITE}/parcours.html", "w").write(html)
    idx = open(f"{SITE}/index.html").read()
    if "parcours.html" not in idx:
        idx = idx.replace("Figma non importé.</p>", 'Figma non importé. <a href="parcours.html">Parcours navigables</a>.</p>')
        open(f"{SITE}/index.html", "w").write(idx)
    print(len(J), "journeys,", sum(len(s) for _, _, s in J), "steps")


if __name__ == "__main__":
    main()

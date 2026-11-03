"""Patient record family — S-09 record/timeline, S-10 history & allergies, S-11 measurements, S-12 labs,
S-13 documents & source viewer; interactions I-06, I-07, I-08, I-09, I-27.

Data: synthetic-data.json only. States without a dataset fixture are labelled "template" and listed as
dataset gaps in review/notes-dossier.md; they never introduce a clinical value.
"""
from lib import *

CL, LO = "R-A-001", "R-A-002"
SLUG = {CL: "claire", LO: "louis"}
S = lambda i: by_id("sources", i)
TABS = [("Chronologie", "S-09"), ("Antécédents et allergies", "S-10"), ("Mesures", "S-11"), ("Biologie", "S-12"), ("Documents", "S-13")]


def prov(src_id, n=None, occurred=True):
    """Provenance line: occurrence vs recording dates, author, origin, source id."""
    s = S(src_id)
    occ = f'survenu le <span class="etaye-num">{dh_pro(s["occurredAt"])}</span>' if occurred else ""
    rec = f'enregistré le <span class="etaye-num">{dh_pro(s["recordedAt"])}</span>'
    parts = [x for x in (occ, rec, esc(auteur(s)), SOURCE_TYPE_FR.get(s.get("sourceType"), "")) if x]
    r = repere(n, label=f"Source {n} : {s['id']}") if n is not None else ""
    return f'<div class="etaye-3" style="font-size:12px;line-height:18px;margin-top:2px">{" · ".join(parts)} · <span class="etaye-num">{esc(s["id"])}</span>{r}</div>'


def dossier(rid, onglet, body, actor="nora", titre=None, meta="", actions="", bandeau_="", sous_onglets=True):
    tabs = onglets([(l, href(sid, SLUG[rid]), None) for l, sid in TABS], onglet) if sous_onglets else ""
    fil = f'<a href="{href("S-07")}">Patients</a> › {esc(nom(rid, "famille"))}'
    head = entete(titre or onglet, meta, actions, fil)
    return pro_shell("Patients", f'<div class="etaye-page">{head}{bandeau_}{tabs}{body}</div>', actor, banner=banniere(rid))


# ── Chronology events (Claire) — every row maps to dataset objects ───────────
def ev_claire():
    E = []
    add = lambda **k: E.append(k)
    add(iso="2026-11-02T14:25:00+01:00", type="Publication", titre="Explication destinée à Claire Martin, version 2",
        detail=f'{st("publication", "PUBLISHED")} Publiée par Dr Nora Lefebvre · remplace la version 1 (PUB-A-001)', cat="gen")
    add(iso="2026-11-02T14:20:00+01:00", type="Validation", titre="Explication patient v2 validée",
        detail=f'{st("business", "VALIDATED")} Dr Nora Lefebvre · 1 correction humaine d\'une phrase générée non étayée', cat="gen")
    add(iso="2026-11-02T14:05:00+01:00", type="Brief de préparation", titre="Brouillon généré pour la visite du 03/11/2026",
        detail=f'{st("business", "DRAFT")} {etat("MISSING", "1 information manquante")} {etat("CONTRADICTORY", "1 contradiction")} · réservé aux professionnels', cat="gen", action=True)
    add(iso="2026-11-02T09:10:00+01:00", type="Préparation de visite", titre="Reçue de Claire Martin",
        detail=f'« {esc(S("SRC-A-005")["text"])} » {etat_source(S("SRC-A-005"))}' + prov("SRC-A-005", 1), cat="decl")
    add(iso="2026-10-20T10:50:00+02:00", type="Publication", titre="Explication destinée à Claire Martin, version 1",
        detail=f'{st("publication", "REPLACED")} {st("freshness", "STALE")} Publiée par Dr Nora Lefebvre · remplacée le 02/11/2026 à 14:25', cat="gen")
    add(iso="2026-10-20T10:40:00+02:00", type="Compte rendu", titre="Compte rendu de consultation v1 validé",
        detail=f'{st("business", "VALIDATED")} {st("publication", None)} Dr Nora Lefebvre · aucune publication', cat="gen")
    add(iso="2026-10-20T10:30:00+02:00", type="Note de consultation", titre="Consultation de suivi · Dr Nora Lefebvre",
        detail=esc(S("SRC-A-006")["text"]) + prov("SRC-A-006", 2), cat="cons")
    add(iso="2026-10-20T10:12:00+02:00", type="Mesure", titre=f'Poids {val(68.4, "kg")}',
        detail=f'{etat("CORRECTED", "Corrigé · version 2")} ancienne valeur {val(86.4, "kg")} conservée · motif : erreur de saisie' + prov("SRC-A-007-v2", 3), cat="mes")
    add(iso="2026-10-20T10:10:00+02:00", type="Mesure", titre=f'Pression artérielle {val("134/82", "mmHg")}',
        detail=prov("SRC-A-004", 4), cat="mes")
    add(iso="2026-10-20T10:00:00+02:00", type="Rendez-vous", titre="Consultation de suivi · terminée",
        detail=f'{st("appointment", "COMPLETED")} Dr Nora Lefebvre · consultation CONS-A-001 terminée à 10:35', cat="cons")
    add(iso="2026-10-18T12:00:00+02:00", type="Espace patient", titre="Invitation acceptée par Claire Martin",
        detail=st("invitation", "ACCEPTED", "Espace patient activé"), cat="adm")
    add(iso="2026-10-15T09:00:00+02:00", type="Rendez-vous", titre="Rendez-vous non honoré",
        detail=f'{st("appointment", "NO_SHOW")} Dr Nora Lefebvre · reste visible dans l\'historique', cat="adm")
    add(iso="2026-10-12T08:00:00+02:00", type="Document déposé", titre="Bilan fictif du 12 octobre 2026",
        detail=f'Créatinine {val(104, "µmol/L")} · glucose {val(5.2, "mmol/L")} · saisis manuellement, sans extraction' + prov("SRC-A-003", 5), cat="bio")
    add(iso="2026-09-14T09:35:00+02:00", type="Mesure", titre=f'Pression artérielle {val("128/80", "mmHg")}', detail=prov("SRC-A-008", 6), cat="mes")
    add(iso="2026-09-14T09:30:00+02:00", type="Note de consultation", titre="Déclaration sur les allergies",
        detail=f'« {esc(S("SRC-A-001")["text"])} » {etat_source(S("SRC-A-001"))}' + prov("SRC-A-001", 7), cat="decl")
    add(iso="2026-09-14T08:00:00+02:00", type="Biologie", titre=f'Créatinine {val(96, "µmol/L")}',
        detail="Intervalle fourni 60–110 µmol/L · saisi manuellement" + prov("SRC-A-002", 8), cat="bio")
    return E


def chrono_rows(E):
    return chrono([{"date": d_court(e["iso"]), "heure": h_pro(e["iso"]), "type": e["type"], "titre": e["titre"], "detail": e["detail"], "action": e.get("action")} for e in E])


def filtres_chrono(type_="Tous les types", puces=()):
    types = ["Tous les types", "Consultations et notes", "Mesures", "Biologie et documents", "Déclarations de la patiente", "Documents générés et diffusion", "Rendez-vous et accès"]
    p = "".join(f'<span class="etaye-puce">{esc(x)}<button type="button" aria-label="Retirer le filtre {esc(x)}">{ic("fermer")}</button></span>' for x in puces)
    return (f'<div class="etaye-filtres" role="search" aria-label="Filtrer la chronologie">'
            f'{champ("Type d\'événement", type_, "f-type", kind="select", options=types)}'
            f'{champ("Du", "", "f-du", num=True, placeholder="JJ/MM/AAAA")}{champ("Au", "", "f-au", num=True, placeholder="JJ/MM/AAAA")}'
            f'{btn("Effacer les filtres", "discret") if p else ""}</div>' + (f'<div class="etaye-ligne" style="flex-wrap:wrap">{p}</div>' if p else ""))


def aside_claire(fail=False):
    apt = by_id("appointments", "APT-A-001")
    verif = (f'<ul class="etaye-controle">'
             f'<li>{ic("alerte", 18, "etaye-controle__bloque")}<div>Allergies : déclarations discordantes<small>Absence déclarée le 14/09/2026, réaction à la pénicilline signalée le 02/11/2026 · non confirmée</small></div>{lien("Examiner", href("S-10", "claire"))}</li>'
             f'<li>{ic("info", 18, "etaye-2")}<div>Traitements déclarés : non renseignés<small>Aucune source au dossier · ne pas conclure à l\'absence de traitement</small></div><span></span></li></ul>')
    rdv = (f'<div class="etaye-pile" style="gap:var(--espace-2)"><div class="etaye-ligne" style="justify-content:space-between"><strong class="etaye-num">{dh_pro(apt["startsAt"])}</strong>{st("appointment", "BOOKED")}</div>'
           f'<div class="etaye-2" style="font-size:14px">{esc(apt["reason"])} · Dr Nora Lefebvre<br>Préparation reçue le 02/11/2026 à 09:10 · brief en brouillon</div>'
           f'<div>{btn("Ouvrir la préparation", "secondaire", href_=href("S-18", "brouillon"))}</div></div>')
    diff = (f'<div class="etaye-pile" style="gap:var(--espace-3)">'
            f'<div><div class="etaye-ligne" style="justify-content:space-between"><span>Explication patient v2</span>{st("publication", "PUBLISHED")}</div><div class="etaye-3" style="font-size:12px">Publiée le <span class="etaye-num">02/11/2026 14:25</span></div></div>'
            f'<div><div class="etaye-ligne" style="justify-content:space-between"><span>Compte rendu v1</span>{st("publication", None)}</div><div class="etaye-3" style="font-size:12px">Validé le <span class="etaye-num">20/10/2026 10:40</span> · non publié</div></div></div>')
    return panneau("À vérifier", verif) + panneau("Prochain rendez-vous", rdv) + panneau("Diffusion", diff, lien("Historique des versions", href("S-22", "comparaison")))


# ── S-09 ─────────────────────────────────────────────────────────────────────
def s09_claire(n=9, fail=False, readonly=False, theme_note=""):
    E = ev_claire()
    shown = E if n is None else E[:n]
    more = "" if n is None else f'<div class="etaye-ligne">{btn(f"Afficher les {len(E) - n} événements plus anciens", "secondaire")}<span class="etaye-3" style="font-size:13px">{len(E)} événements au dossier · du 14/09/2026 au 02/11/2026</span></div>'
    if fail:
        E2 = [e for e in shown if e["cat"] != "mes"]
        band = bandeau("attention", "Les mesures n'ont pas pu être chargées",
                       "Les autres événements sont affichés. Les mesures du 14/09/2026 et du 20/10/2026 manquent dans cette vue ; aucune n'a été supprimée.",
                       btn("Réessayer", "secondaire", "relancer"), role="alert")
        shown = E2
    else:
        band = ""
    if readonly:
        band += bandeau("neutre", "Lecture seule", "Votre accès à ce cabinet permet de lire le dossier, pas de le modifier ni de démarrer une consultation.", icon="verrou")
    actions = "" if readonly else (btn("Exporter", "secondaire", "imprimer", href_=href("S-39", "options")) + btn("Ouvrir la préparation du 03/11", "principal", href_=href("S-18", "brouillon")))
    main = (f'<section class="etaye-pile" aria-labelledby="h-chrono"><h2 class="ui-section" id="h-chrono">Événements du dossier</h2>'
            f'{filtres_chrono()}{chrono_rows(shown)}{more}</section>')
    body = f'<div class="etaye-cols etaye-cols--2">{main}<div class="etaye-pile">{aside_claire()}</div></div>'
    meta = "Cabinet des Tilleuls · dossier version 8 · dates de survenue et d'enregistrement affichées séparément"
    return dossier(CL, "Chronologie", band + body, titre="Chronologie", meta=meta, actions=actions)


def s09_louis():
    ap2, ap4 = by_id("appointments", "APT-A-002"), by_id("appointments", "APT-A-004")
    ai = by_id("aiTasks", "AI-A-002")
    E = [
        {"date": "02/11/2026", "heure": "14:30", "type": "Tâche", "titre": "Brief de préparation non produit",
         "detail": f'{st("processing", "FAILED")} Le service n\'a pas répondu à temps (14:30). Aucune synthèse, aucun texte de remplacement.'},
        {"date": "02/11/2026", "heure": "14:10", "type": "Consultation", "titre": "Consultation en cours · Dr Sami Rahmani",
         "detail": "Commencée à 14:10 · CONS-A-002"},
        {"date": "02/11/2026", "heure": "14:00", "type": "Rendez-vous", "titre": esc(ap2["reason"]),
         "detail": f'{st("appointment", "CHECKED_IN")} Dr Sami Rahmani'},
        {"date": "30/10/2026", "heure": "12:00", "type": "Espace patient", "titre": "Invitation expirée",
         "detail": f'{st("invitation", "EXPIRED")} Aucun espace patient actif'},
        {"date": "26/10/2026", "heure": "11:00", "type": "Rendez-vous", "titre": "Rendez-vous annulé",
         "detail": f'{st("appointment", "CANCELLED")} Motif : {esc(ap4["cancellationReason"])} · Dr Sami Rahmani'},
    ]
    vide_ = (f'<ul class="etaye-controle">'
             f'<li>{ic("info", 18, "etaye-2")}<div>Antécédents : non renseignés<small>Aucune source · ne signifie pas absence d\'antécédent</small></div><span></span></li>'
             f'<li>{ic("info", 18, "etaye-2")}<div>Allergies : inconnues<small>Jamais renseignées</small></div><span></span></li>'
             f'<li>{ic("info", 18, "etaye-2")}<div>Traitements déclarés : non renseignés</div><span></span></li>'
             f'<li>{ic("info", 18, "etaye-2")}<div>Mesures, biologie, documents : aucun au dossier</div><span></span></li></ul>')
    main = (f'<section class="etaye-pile" aria-labelledby="h-chrono"><h2 class="ui-section" id="h-chrono">Événements du dossier</h2>'
            f'{bandeau("neutre", "Historique peu renseigné", "Ce dossier a été créé récemment. Les rubriques vides restent vides : elles ne sont pas « normales ».")}'
            f'{chrono(E)}</section>')
    side = panneau("Ce qui manque", vide_) + panneau("Consultation en cours", f'<div class="etaye-2" style="font-size:14px">Dr Sami Rahmani · depuis 14:10<br>Vous pouvez lire le dossier ; la consultation reste celle de Dr Sami Rahmani.</div>')
    return dossier(LO, "Chronologie", f'<div class="etaye-cols etaye-cols--2">{main}<div class="etaye-pile">{side}</div></div>',
                   titre="Chronologie", meta="Cabinet des Tilleuls · dossier version 1 · scénario V-EMPTY",
                   actions=btn("Relancer le brief", "secondaire", "relancer", href_=href("S-40", "echec")))


def s09_corrige():
    E = ev_claire()
    sel = [e for e in E if e["titre"].startswith("Poids")][0]
    v1, v2 = S("SRC-A-007-v1"), S("SRC-A-007-v2")
    hist = (f'<div class="etaye-panneau" style="margin-top:var(--espace-2)"><div class="etaye-panneau__corps">'
            f'<div class="ui-libelle etaye-2">Historique de la mesure MEAS-A-003</div>'
            + f'<ul class="etaye-controle">'
              f'<li>{ic("valide", 18, "etaye-controle__ok")}<div><strong>Version 2 · actuelle · <span class="etaye-num">{val(68.4, "kg")}</span></strong><small>Enregistrée le <span class="etaye-num">{dh_pro(v2["recordedAt"])}</span> par Dr Nora Lefebvre · motif : {esc(v2["correctionReason"])}</small></div>{repere(3)}</li>'
              f'<li>{ic("remplacer", 18, "etaye-2")}<div><strong>Version 1 · remplacée · <span class="etaye-barre etaye-num">{val(86.4, "kg")}</span></strong><small>Enregistrée le <span class="etaye-num">{dh_pro(v1["recordedAt"])}</span> par Dr Nora Lefebvre · {esc(v1["id"])}</small></div><span></span></li></ul>'
            + f'<div class="etaye-3" style="font-size:12px">La date de mesure ne change pas : 20/10/2026 10:12. La correction ne crée pas de nouvel événement.</div></div></div>')
    sel = dict(sel, detail=sel["detail"] + hist, action=True)
    shown = [sel if e["titre"].startswith("Poids") else e for e in E[5:11]]
    main = (f'<section class="etaye-pile" aria-labelledby="h-chrono"><h2 class="ui-section" id="h-chrono">Événements du dossier</h2>'
            f'{filtres_chrono("Mesures", ("Mesures",))}{chrono_rows([e for e in shown if e["cat"] == "mes"])}</section>')
    return dossier(CL, "Chronologie", f'<div class="etaye-cols etaye-cols--2">{main}<div class="etaye-pile">{aside_claire()}</div></div>',
                   titre="Chronologie", meta="Filtre actif : mesures · événement corrigé déplié")


def s09_date_inconnue():
    gab = (f'<div class="etaye-panneau"><div class="etaye-panneau__corps etaye-pile">'
           f'<div class="ui-libelle etaye-2">Gabarit — aucune entrée du jeu etaye-design-v1 n\'a une date de survenue inconnue</div>'
           + chrono([{"date": "Date inconnue", "type": "Document déposé", "titre": "[Titre saisi au dépôt]",
                      "detail": f'{etat("UNKNOWN", "Date de survenue inconnue")} enregistré le <span class="etaye-num">JJ/MM/AAAA HH:MM</span> · [auteur du dépôt] · la date d\'enregistrement ne remplace jamais la date de survenue'}])
           + '<p class="etaye-2" style="font-size:13px;margin:0">Rangement : les événements sans date de survenue sont regroupés en fin de chronologie sous « Date inconnue », triés par date d\'enregistrement, et ne sont jamais placés à la date du dépôt.</p></div></div>')
    E = ev_claire()[:4]
    main = (f'<section class="etaye-pile"><h2 class="ui-section">Événements du dossier</h2>{chrono_rows(E)}'
            f'<h3 class="ui-section" style="margin-top:var(--espace-4)">Date inconnue</h3>{gab}</section>')
    return dossier(CL, "Chronologie", f'<div class="etaye-cols etaye-cols--2">{main}<div class="etaye-pile">{aside_claire()}</div></div>', titre="Chronologie",
                   meta="Règle d'affichage d'une date de survenue inconnue (gabarit)")


# ── S-10 History, allergies, reported treatments ─────────────────────────────
def allergies_bloc(i27="ferme"):
    s1, s5 = S("SRC-A-001"), S("SRC-A-005")
    ligne = lambda s, n: (f'<li style="grid-template-columns:24px minmax(0,1fr) auto">{repere(n, seul=True)}<div><strong>{d_court(s["occurredAt"])} · {KIND_FR[s["kind"]]}</strong>'
                          f'<div class="doc-corps" style="font-size:15px;line-height:24px;margin:4px 0">« {esc(s["text"])} »</div>'
                          f'<div class="etaye-ligne">{etat_source(s)}<span class="etaye-3" style="font-size:12px">{esc(auteur(s))} · {SOURCE_TYPE_FR[s["sourceType"]]} · {esc(s["id"])}</span></div></div>'
                          f'{lien("Ouvrir la source", href("I-09", "allergie"))}</li>')
    sources = f'<ul class="etaye-controle">{ligne(s5, 1)}{ligne(s1, 2)}</ul>'
    if i27 == "ferme":
        act = (f'<div class="etaye-bandeau etaye-bandeau--attention" role="note">{ic("alerte", 18)}<div><div class="etaye-bandeau__titre">Informations à vérifier</div>'
               f'<div>Deux déclarations datées se contredisent. Aucune n\'est supprimée ; rien n\'est conclu automatiquement.</div></div>'
               f'<div class="etaye-bandeau__actions">{btn("Examiner les sources", "secondaire", href_=href("I-27", "ouvert"))}{btn("Conserver à vérifier", "discret")}</div></div>')
    else:
        act = ""
    head = (f'<div class="etaye-ligne" style="justify-content:space-between"><h2 class="ui-section" style="margin:0">Allergies</h2>{etat("CONTRADICTORY")}</div>'
            f'<p class="etaye-2" style="margin:0;font-size:14px">État de la rubrique : inconnu · déclarations discordantes à préciser · ne pas conclure à l\'absence d\'allergie (HIST-A-001)</p>')
    return f'<section class="etaye-panneau"><div class="etaye-panneau__corps etaye-pile">{head}{act}{sources}</div></section>'


def rubrique(titre, code, texte, action=""):
    return (f'<section class="etaye-panneau"><div class="etaye-panneau__corps etaye-pile" style="gap:var(--espace-2)">'
            f'<div class="etaye-ligne" style="justify-content:space-between"><h2 class="ui-section" style="margin:0">{titre}</h2>{etat(code)}</div>'
            f'<p class="etaye-2" style="margin:0;font-size:14px">{texte}</p>{f"<div>{action}</div>" if action else ""}</div></section>')


def s10(rid=CL, mode="claire"):
    if rid == LO:
        body = (rubrique("Allergies", "UNKNOWN", "Jamais renseignées. Aucune source au dossier.", btn("Ajouter une déclaration datée", "secondaire", "ajouter"))
                + rubrique("Antécédents", "UNKNOWN", "Non renseignés (HIST-A-003). L'absence de source ne signifie pas absence d'antécédent.", btn("Ajouter un antécédent", "secondaire", "ajouter"))
                + rubrique("Traitements déclarés", "MISSING", "Non renseignés. Ne pas écrire « aucun traitement ».", btn("Ajouter un traitement déclaré", "secondaire", "ajouter")))
        return dossier(LO, "Antécédents et allergies", f'<div class="etaye-pile" style="max-width:880px">{body}</div>', titre="Antécédents et allergies",
                       meta="Scénario V-EMPTY · aucune rubrique renseignée")
    readonly = mode == "lecture"
    act = lambda l: "" if readonly else btn(l, "secondaire", "ajouter")
    body = (allergies_bloc("ouvert" if mode == "i27" else "ferme")
            + rubrique("Traitements déclarés", "MISSING", "Non renseignés dans le dossier (HIST-A-002). La note du 20/10/2026 le confirme : « Traitements actuels non renseignés dans ce jeu de données. »", act("Ajouter un traitement déclaré"))
            + rubrique("Antécédents", "MISSING", "Aucune rubrique d'antécédents enregistrée pour ce dossier.", act("Ajouter un antécédent")))
    band = bandeau("neutre", "Lecture seule — variante de permission", "CLINICAL_READ seul : pas d'ajout, de correction ni d'évaluation. Variante de test ; aucun membre du jeu n'a ce seul droit.", icon="verrou") if readonly else ""
    return dossier(CL, "Antécédents et allergies", band + f'<div class="etaye-pile" style="max-width:880px">{body}</div>', titre="Antécédents et allergies",
                   meta="Chaque déclaration garde sa date, son auteur et son origine")


def s10_evaluation(error=False):
    """Practitioner adds a separate assessment (I-05 inline) — the patient report keeps its provenance."""
    err = resume_erreurs([("ev-motif", "Motif de l'évaluation : obligatoire")]) if error else ""
    form = (f'<section class="etaye-panneau" aria-labelledby="h-ev"><div class="etaye-panneau__tete"><h2 id="h-ev">Ajouter une évaluation du praticien</h2>{enregistrement("sale")}</div>'
            f'<div class="etaye-panneau__corps etaye-form">{err}'
            f'<p class="etaye-2" style="margin:0;font-size:14px">L\'évaluation s\'ajoute à côté des déclarations ; elle ne les modifie pas et ne les supprime pas.</p>'
            f'<p style="margin:0;font-size:14px"><span class="etaye-2">Concerne :</span> allergies · déclarations du 14/09/2026 et du 02/11/2026</p>'
            f'{choix("ev-etat", [("Reste à préciser", "La rubrique reste « inconnue »"), ("Allergie présente", "Nécessite une source datée"), ("Absence déclarée ce jour", "Nouvelle déclaration datée, sans effacer la précédente")], legend="Disposition", checked=(0,))}'
            f'{champ("Motif de l\'évaluation", "", "ev-motif", kind="textarea", required=True, error="indiquez sur quoi repose cette évaluation." if error else None, help_=None if error else "Exemple : à préciser lors de la consultation du 03/11/2026.", focus=not error)}'
            f'<div class="etaye-ligne">{btn("Enregistrer l\'évaluation", "principal")}{btn("Annuler", "discret")}</div></div></section>')
    body = allergies_bloc("ouvert") + form
    return dossier(CL, "Antécédents et allergies", f'<div class="etaye-pile" style="max-width:880px">{body}</div>', titre="Antécédents et allergies",
                   meta="Évaluation en cours de saisie · non enregistrée")


def i27_open():
    s1, s5 = S("SRC-A-001"), S("SRC-A-005")
    col = lambda s, n, t: (f'<div class="etaye-diff__col"><div class="etaye-diff__tete"><span>{repere(n, seul=True)} {t}</span><span class="etaye-num">{dh_pro(s["occurredAt"])}</span></div>'
                           f'<div class="etaye-diff__corps"><p style="margin:0">« {esc(s["text"])} »</p>'
                           f'<div class="etaye-ligne" style="font:400 13px/20px var(--font-texte)">{etat_source(s)}</div>'
                           f'<div class="etaye-3" style="font:400 12px/18px var(--font-texte)">{esc(auteur(s))} · {SOURCE_TYPE_FR[s["sourceType"]]} · {esc(s["id"])} v{s["version"]} · {esc(s["locator"])}</div></div></div>')
    panel = (f'<section class="etaye-panneau" aria-labelledby="h-i27" style="border-color:var(--accent)"><div class="etaye-panneau__tete"><h2 id="h-i27">Informations à vérifier</h2>'
             f'<span class="etaye-3" style="font-size:12px">Allergies · HIST-A-001</span></div><div class="etaye-panneau__corps etaye-pile">'
             f'<div class="etaye-diff">{col(s1, 2, "Plus ancienne")}{col(s5, 1, "Plus récente")}</div>'
             f'<ul class="etaye-controle"><li>{ic("info", 18, "etaye-2")}<div>La déclaration négative du 14/09/2026 ne contredit pas une réaction rapportée ensuite : elle date d\'avant.<small>Rien n\'est fusionné ni résolu automatiquement. La rubrique reste « inconnue ».</small></div><span></span></li>'
             f'<li>{ic("info", 18, "etaye-2")}<div>Champ manquant : nature et date de la réaction<small>Non précisées par la patiente</small></div><span></span></li></ul>'
             f'<div class="etaye-ligne">{btn("Ajouter une évaluation du praticien", "principal", href_=href("S-10", "evaluation"))}{btn("Conserver à vérifier", "secondaire")}'
             f'<span class="etaye-3" style="font-size:13px">« Conserver à vérifier » enregistre votre passage ; les deux sources restent.</span></div></div></section>')
    body = panel + rubrique("Traitements déclarés", "MISSING", "Non renseignés dans le dossier (HIST-A-002).")
    return dossier(CL, "Antécédents et allergies", f'<div class="etaye-pile" style="max-width:960px">{body}</div>', titre="Antécédents et allergies", meta="Examen des sources en ligne, sans fenêtre")


def i27_enregistre():
    body = (allergies_bloc("ouvert").replace('<h2 class="ui-section" style="margin:0">Allergies</h2>', '<h2 class="ui-section" style="margin:0">Allergies</h2>', 1)
            + toast("Passage enregistré à 16:02 · « à vérifier » conservé, deux sources inchangées"))
    return dossier(CL, "Antécédents et allergies", f'<div class="etaye-pile" style="max-width:880px">{body}</div>', titre="Antécédents et allergies", meta="Après « Conserver à vérifier »")


# ── S-11 Measurements ────────────────────────────────────────────────────────
def mesures_table(corr_open=False):
    m1, m2, m3 = (by_id("measurements", i) for i in ("MEAS-A-001", "MEAS-A-002", "MEAS-A-003"))
    bp = lambda m: f'{m["values"]["systolic"]}/{m["values"]["diastolic"]}'
    rows = [[f'<span class="etaye-num">{dh_pro(m2["occurredAt"])}</span>', "Pression artérielle", f'<span class="etaye-num">{bp(m2)}</span>', "mmHg", "Observée au cabinet · Dr Nora Lefebvre", repere(1)],
            [f'<span class="etaye-num">{dh_pro(m3["occurredAt"])}</span>', "Poids", f'<span class="etaye-num">{num(68.4)}</span>', "kg", f'Observé au cabinet · {etat("CORRECTED", "corrigé · v2")}', repere(2)],
            [f'<span class="etaye-num">{dh_pro(m1["occurredAt"])}</span>', "Pression artérielle", f'<span class="etaye-num">{bp(m1)}</span>', "mmHg", "Observée au cabinet · Dr Nora Lefebvre", repere(3)]]
    t = tableau(["Date de mesure", "Mesure", "Valeur", "Unité", "Origine", "Source"], rows, num=(2,), stack=True)
    leg = f'<div class="etaye-3" style="font-size:12px;display:grid;gap:2px">{source_ref("SRC-A-004", 1)}{source_ref("SRC-A-007-v2", 2)}{source_ref("SRC-A-008", 3)}</div>'
    return t + leg


def s11(mode="claire"):
    if mode == "louis":
        body = vide("Aucune mesure au dossier", "Aucune mesure n'a été enregistrée pour Louis Bernard. Aucune valeur n'est supposée.", btn("Saisir une mesure", "principal", "ajouter", href_=href("I-06", "ouvert")))
        return dossier(LO, "Mesures", body, titre="Mesures", meta="Scénario V-EMPTY")
    if mode == "chargement":
        body = f'<div class="etaye-cols etaye-cols--egal"><section class="etaye-panneau"><div class="etaye-panneau__corps">{squelette(5)}</div></section><section class="etaye-panneau"><div class="etaye-panneau__corps">{squelette(4)}</div></section></div>'
        return dossier(CL, "Mesures", body, titre="Mesures", meta="Chargement des mesures…")
    chart = (f'<section class="etaye-panneau" aria-labelledby="h-pa"><div class="etaye-panneau__tete"><h2 id="h-pa">Pression artérielle</h2><span class="etaye-3" style="font-size:12px">2 mesures · unité unique mmHg</span></div>'
             f'<div class="etaye-panneau__corps etaye-pile">{courbe_tension()}'
             f'<p class="etaye-3" style="font-size:12px;margin:0">Deux points reliés par un trait : aucune mesure entre le 14/09/2026 et le 20/10/2026. Le trait n\'est pas une mesure. Le tableau ci-dessous est la référence.</p></div></section>')
    poids = (f'<section class="etaye-panneau" aria-labelledby="h-poids"><div class="etaye-panneau__tete"><h2 id="h-poids">Poids</h2><span class="etaye-3" style="font-size:12px">1 mesure · pas de courbe</span></div>'
             f'<div class="etaye-panneau__corps etaye-pile" style="gap:var(--espace-2)"><div><span class="donnee-forte etaye-num" style="font-size:28px">{num(68.4)}</span>{NB}kg <span class="etaye-2" style="font-size:14px">le 20/10/2026 à 10:12</span></div>'
             f'<div class="etaye-ligne">{etat("CORRECTED", "Corrigé · version 2")}<span class="etaye-3" style="font-size:12px">86,4 kg remplacé le 20/10/2026 à 10:15 · erreur de saisie</span></div>'
             f'<div>{btn("Voir l\'historique", "discret", href_=href("S-09", "corrige"))}</div></div></section>')
    filt = (f'<div class="etaye-filtres">{champ("Période", "Tout le dossier", "m-per", kind="select", options=["Tout le dossier", "3 derniers mois", "12 derniers mois"])}'
            f'{champ("Mesure", "Toutes", "m-type", kind="select", options=["Toutes", "Pression artérielle", "Poids"])}</div>')
    actions = btn("Saisir une mesure", "principal", "ajouter", href_=href("I-06", "ouvert"))
    tab = f'<section class="etaye-pile" aria-labelledby="h-tab"><h2 class="ui-section" id="h-tab">Toutes les mesures</h2>{mesures_table()}</section>'
    body = f'{filt}<div class="etaye-cols etaye-cols--egal">{chart}{poids}</div>{tab}'
    return dossier(CL, "Mesures", body, titre="Mesures", meta="Valeurs actuelles · les versions corrigées restent consultables", actions=actions)


def s11_gabarits():
    """Mixed units / unknown date: rendering rules only — no dataset fixture (dataset gap)."""
    rows = [["<em>Date inconnue</em>", "[Mesure]", "[valeur]", "[unité]", etat("UNKNOWN", "Date de mesure inconnue") + " · hors courbe"],
            ["JJ/MM/AAAA", "[Mesure]", "[valeur]", "[unité B]", etat("MISSING", "Unité différente · courbe séparée") + " · jamais convertie en silence"]]
    body = (bandeau("neutre", "Gabarits sans données", "Le jeu etaye-design-v1 ne contient ni mesure sans date ni unités mélangées. Ces règles d'affichage n'ajoutent aucune valeur clinique.")
            + tableau(["Date de mesure", "Mesure", "Valeur", "Unité", "Affichage"], rows, stack=True)
            + '<ul class="etaye-2" style="font-size:14px;margin:0;padding-left:20px"><li>Une unité incompatible ouvre une seconde série avec son propre axe, ou reste dans le tableau seulement.</li>'
              '<li>Une mesure sans date n\'apparaît pas sur la courbe ; elle reste dans le tableau avec « Date inconnue ».</li>'
              '<li>Saisie impossible : la valeur est refusée avec un message de champ (voir I-06 « erreur »).</li></ul>')
    return dossier(CL, "Mesures", f'<div class="etaye-pile" style="max-width:960px">{body}</div>', titre="Mesures", meta="Règles : unités mélangées, date inconnue (gabarits)")


def i06(mode="ouvert"):
    fond = s11()
    corr = mode in ("correction", "conflit")
    err = mode == "erreur"
    titre = "Corriger la mesure" if corr else "Saisir une mesure"
    f = []
    if err:
        f.append(resume_erreurs([("m-sys", "Pression systolique : valeur hors des limites de saisie"), ("m-date", "Date de mesure : obligatoire")]))
    if mode == "conflit":
        f.append(bandeau("attention", "Cette mesure a été modifiée entre-temps",
                         "Version 2 enregistrée par Dr Nora Lefebvre le 20/10/2026 à 10:15 (68,4 kg). Votre saisie n'a pas été enregistrée ; elle est conservée ci-dessous.",
                         btn("Comparer les versions", "secondaire", href_=href("I-21", "ouvert")), role="alert"))
    if corr:
        f.append(champ("Mesure", "Poids · MEAS-A-003", "m-c", readonly=True))
        f.append(f'<div class="etaye-form__ligne">{champ("Valeur actuelle", "86,4" if mode == "conflit" else "68,4", "m-old", readonly=True, num=True, help_="Version 1 au moment de l\'ouverture" if mode == "conflit" else "Version 2")}'
                 f'{champ("Nouvelle valeur (kg)", "68,4" if mode == "conflit" else "", "m-new", num=True, required=True, focus=mode == "correction")}</div>')
        f.append(champ("Motif de la correction", "Erreur de saisie" if mode == "conflit" else "", "m-mot", kind="textarea", required=True, help_="Le motif et l'ancienne valeur restent dans l'historique."))
    else:
        f.append(champ("Mesure", "Pression artérielle", "m-c", kind="select", options=["Pression artérielle", "Poids"], required=True))
        f.append(f'<div class="etaye-form__ligne">{champ("Systolique (mmHg)", "1340" if err else "", "m-sys", num=True, required=True, error="1340 dépasse les limites de saisie (40 à 300 mmHg)." if err else None, focus=not err)}'
                 f'{champ("Diastolique (mmHg)", "82" if err else "", "m-dia", num=True, required=True)}</div>')
        f.append(f'<div class="etaye-form__ligne">{champ("Date de mesure", "", "m-date", num=True, required=True, placeholder="JJ/MM/AAAA", error="indiquez la date de la mesure." if err else None)}{champ("Heure", "", "m-h", num=True, placeholder="HH:MM", optional=True)}</div>')
        f.append(champ("Origine", "Observée au cabinet", "m-o", kind="select", options=["Observée au cabinet", "Rapportée par un document", "Déclarée par le patient"]))
    f.append(f'<div>{enregistrement("echec" if mode == "conflit" else "sale")}</div>')
    pied = btn("Enregistrer la mesure", "principal", disabled=False) + btn("Annuler", "discret")
    return tiroir(fond, titre, f'<div class="etaye-form">{"".join(f)}</div>', pied, intro="MARTIN Claire · R-A-001")


# ── S-12 Labs ────────────────────────────────────────────────────────────────
def labs_rows(no_interval=False):
    l1, l2, l3 = (by_id("labResults", i) for i in ("LAB-A-001", "LAB-A-002", "LAB-A-003"))
    ri = lambda l: etat("MISSING", "Intervalle non fourni") if no_interval else f'<span class="etaye-num">{num(l["referenceInterval"]["low"])}–{num(l["referenceInterval"]["high"])}</span> <span class="etaye-3" style="font-size:12px">fourni par le laboratoire</span>'
    return [[f'<span class="etaye-num">{d_court(l2["sampledAt"])}</span>', "Créatinine", f'<span class="etaye-num">{num(l2["value"])}</span>', "µmol/L", ri(l2), "Saisie manuelle · DOC-A-001", repere(1)],
            [f'<span class="etaye-num">{d_court(l3["sampledAt"])}</span>', "Glucose", f'<span class="etaye-num">{num(l3["value"])}</span>', "mmol/L", ri(l3), "Saisie manuelle · DOC-A-001", repere(1)],
            [f'<span class="etaye-num">{d_court(l1["sampledAt"])}</span>', "Créatinine", f'<span class="etaye-num">{num(l1["value"])}</span>', "µmol/L", ri(l1), "Saisie manuelle", repere(2)]]


def courbe_creat(w=None, h=200):
    if w is None:
        return (courbe_creat(640, 220).replace('class="etaye-courbe"', 'class="etaye-courbe etaye-courbe--large"', 1)
                + courbe_creat(340, 200).replace('class="etaye-courbe"', 'class="etaye-courbe etaye-courbe--etroite"', 1))
    pts = [(by_id("labResults", i)) for i in ("LAB-A-001", "LAB-A-002")]
    x0, x1 = 60, w - 24
    y = lambda v: 24 + (120 - v) * (h - 64) / (120 - 50)
    xs = [x0 + min(80, (x1 - x0) // 6), x1 - min(80, (x1 - x0) // 6)]
    band = f'<rect x="{x0}" y="{y(110):.0f}" width="{x1 - x0}" height="{y(60) - y(110):.0f}" fill="var(--surface-2)"/>'
    grid = "".join(f'<line class="grille" x1="{x0}" x2="{x1}" y1="{y(v):.0f}" y2="{y(v):.0f}"/><text class="axe" x="{x0 - 8}" y="{y(v) + 4:.0f}" text-anchor="end">{v}</text>' for v in (60, 80, 100, 120))
    line = f'<polyline class="serie" points="{xs[0]},{y(96):.0f} {xs[1]},{y(104):.0f}"/>'
    dots = "".join(f'<circle class="point" cx="{xs[i]}" cy="{y(p["value"]):.0f}" r="4"/><text class="val" x="{xs[i]}" y="{y(p["value"]) - 10:.0f}" text-anchor="middle">{p["value"]}</text>'
                   f'<text class="axe" x="{xs[i]}" y="{h - 8}" text-anchor="middle">{d_court(p["sampledAt"])}</text>' for i, p in enumerate(pts))
    return (f'<svg class="etaye-courbe" viewBox="0 0 {w} {h}" role="img" aria-label="Créatinine : 96 µmol/L le 14/09/2026, 104 µmol/L le 12/10/2026. Bande grise : intervalle fourni 60 à 110 µmol/L.">'
            f'{band}{grid}{line}{dots}<text class="axe" x="{x0}" y="14">µmol/L · bande grise : intervalle fourni</text></svg>')


def s12(mode="claire"):
    if mode == "louis":
        return dossier(LO, "Biologie", vide("Aucun résultat de biologie", "Aucun résultat n'a été saisi pour Louis Bernard.", btn("Saisir un résultat", "principal", "ajouter", href_=href("I-07", "ouvert"))), titre="Biologie", meta="Scénario V-EMPTY")
    noint = mode == "sans-intervalle"
    t = tableau(["Prélèvement", "Analyse", "Résultat", "Unité", "Intervalle", "Saisie", "Source"], labs_rows(noint), num=(2,), stack=True)
    leg = f'<div class="etaye-3" style="font-size:12px;display:grid;gap:2px">{source_ref("SRC-A-003", 1)}{source_ref("SRC-A-002", 2)}</div>'
    note = bandeau("neutre", "Variante V-LAB-NO-INTERVAL", "Le document ne fournit pas d'intervalle : la colonne l'écrit. Aucun intervalle générique n'est ajouté et aucune valeur n'est qualifiée.") if noint else ""
    chart = "" if noint else (f'<section class="etaye-panneau" aria-labelledby="h-cr"><div class="etaye-panneau__tete"><h2 id="h-cr">Créatinine</h2><span class="etaye-3" style="font-size:12px">2 résultats · même unité · même intervalle fourni</span></div>'
                              f'<div class="etaye-panneau__corps">{courbe_creat()}</div></section>')
    gab = (f'<details class="etaye-panneau"><summary class="etaye-panneau__tete" style="cursor:pointer"><h2>Règles d\'affichage sans exemple dans le jeu</h2></summary><div class="etaye-panneau__corps">'
           + tableau(["Cas", "Affichage"], [["Résultat textuel", "Texte tel que fourni, aucune conversion en nombre, pas de courbe"],
                                             ["Hors de l'intervalle fourni", "Flèche ↑ ou ↓ et libellé « au-dessus de l'intervalle fourni » ; information, pas un diagnostic"],
                                             ["Unités incompatibles", "Séries séparées ; aucune conversion silencieuse"],
                                             ["Transcription non vérifiée", "« Saisie manuelle · à vérifier contre le document » jusqu'à relecture"],
                                             ["Résultat corrigé", "Version actuelle + ancienne valeur barrée dans l'historique, motif visible"]])
           + '</div></details>')
    actions = btn("Saisir un résultat", "principal", "ajouter", href_=href("I-07", "ouvert"))
    body = (note + f'<div class="etaye-filtres">{champ("Analyse", "Toutes", "l-a", kind="select", options=["Toutes", "Créatinine", "Glucose"])}{champ("Période", "Tout le dossier", "l-p", kind="select", options=["Tout le dossier", "3 derniers mois"])}</div>'
            + f'<section class="etaye-pile" aria-labelledby="h-lab"><h2 class="ui-section" id="h-lab">Résultats saisis</h2>{t}{leg}'
            + '<p class="etaye-3" style="font-size:12px;margin:0">Saisie manuelle depuis les documents déposés. Aucune extraction automatique n\'est proposée dans cette version.</p></section>'
            + chart + gab)
    return dossier(CL, "Biologie", body, titre="Biologie", meta="Intervalles tels que fournis par chaque source", actions=actions)


def i07(mode="ouvert"):
    err = mode == "erreur"
    f = [resume_erreurs([("l-u", "Unité : obligatoire"), ("l-src", "Source : obligatoire")])] if err else []
    f += [champ("Analyse", "Créatinine" if err else "", "l-an", required=True, focus=not err),
          choix("l-type", [("Valeur numérique", None), ("Résultat textuel", None)], legend="Type de résultat", checked=(0,)),
          f'<div class="etaye-form__ligne">{champ("Comparateur", "=", "l-cmp", kind="select", options=["=", "<", ">", "≤", "≥"], optional=True)}{champ("Valeur", "104" if err else "", "l-v", num=True, required=True)}</div>',
          f'<div class="etaye-form__ligne">{champ("Unité", "", "l-u", required=True, error="indiquez l\'unité telle qu\'elle figure sur le document." if err else None)}{champ("Date de prélèvement", "12/10/2026" if err else "", "l-d", num=True, required=True, placeholder="JJ/MM/AAAA")}</div>',
          f'<fieldset style="border:0;padding:0;margin:0" class="etaye-form"><legend class="etaye-champ__libelle">Intervalle fourni par la source</legend>'
          f'<div class="etaye-form__ligne">{champ("Borne basse", "", "l-lo", num=True, optional=True)}{champ("Borne haute", "", "l-hi", num=True, optional=True)}</div>'
          f'{choix("l-noint", [("Intervalle non fourni par la source", "Rien n\'est déduit")], typ="checkbox")}</fieldset>',
          champ("Source", "", "l-src", kind="select", options=["Choisir un document déposé…", "DOC-A-001 · Bilan fictif du 12 octobre 2026 · page 1"], required=True, error="choisissez le document d\'où provient la valeur." if err else None),
          f'<div>{enregistrement("sale")}</div>']
    return tiroir(s12(), "Saisir un résultat de biologie", f'<div class="etaye-form">{"".join(f)}</div>', btn("Enregistrer le résultat", "principal") + btn("Annuler", "discret"), intro="Saisie manuelle · aucune extraction")


# ── S-13 Documents and source viewer ─────────────────────────────────────────
def doc_row(nom_, meta, statut_, action="", icon="document"):
    return (f'<div class="etaye-fichier">{ic(icon, 18, "etaye-2")}<div class="etaye-fichier__nom">{nom_}</div><div class="etaye-ligne">{statut_}{action}</div>'
            f'<div class="etaye-fichier__meta">{meta}</div></div>')


def doc_a001_row():
    d, s = by_id("documents", "DOC-A-001"), S("SRC-A-003")
    return doc_row(esc(d["title"]), f'PDF · {d["pages"]} pages · document du <span class="etaye-num">{d_court(s["occurredAt"])}</span> · déposé le <span class="etaye-num">{dh_pro(s["recordedAt"])}</span> par {esc(auteur(s))} · réservé aux professionnels · {esc(d["id"])}',
                   statut("neutre", "Prêt", "valide"), lien("Ouvrir", href("S-13", "visionneuse")))


def s13(mode="claire"):
    if mode == "louis":
        return dossier(LO, "Documents", vide("Aucun document", "Aucun document n'a été déposé pour Louis Bernard.", btn("Ajouter un document", "principal", "ajouter", href_=href("I-08", "ouvert"))), titre="Documents", meta="Scénario V-EMPTY")
    rows = [doc_a001_row()]
    if mode == "file":
        rows = [doc_row('Fichier en cours d\'envoi · <span class="etaye-num">scan-2026-11-02.pdf</span>', "Envoi 1,2 Mo · indisponible tant qu'il n'est pas vérifié", statut("neutre", "Envoi en cours", "relancer"), icon="deposer"),
                doc_row('<span class="etaye-num">courrier-numerise-cabinet-des-tilleuls-transmis-par-la-patiente-version-longue-du-nom-de-fichier.pdf</span>', "Vérification du fichier · aperçu indisponible", statut("neutre", "Vérification", "horloge"), icon="deposer"),
                doc_row('<span class="etaye-num">photo-document.heic</span>', "Format non accepté (PDF, PNG ou JPEG) · fichier non conservé", statut("attention", "Refusé", "alerte"), btn("Choisir un autre fichier", "discret"), icon="alerte")] + rows
    body = (f'<div class="etaye-pile">{bandeau("neutre", "Fichiers déposés : PDF, PNG, JPEG", "Les fichiers sont vérifiés avant d\'être consultables. Les noms de fichier ci-dessous en cours d\'envoi sont des exemples d\'interface, pas des documents du dossier.") if mode == "file" else ""}'
            f'<section aria-labelledby="h-docs"><h2 class="ui-section" id="h-docs">Documents du dossier</h2>{"".join(rows)}</section></div>')
    return dossier(CL, "Documents", body, titre="Documents", meta="Chaque ouverture est vérifiée : cabinet actif, dossier, droit d'accès",
                   actions=btn("Ajouter un document", "principal", "ajouter", href_=href("I-08", "ouvert")))


def page_doc(mark=True, page_=1):
    s = S("SRC-A-003")
    t = s["text"].replace("créatinine 104 µmol/L", "<mark>créatinine 104 µmol/L</mark>") if mark else esc(s["text"])
    return (f'<div class="etaye-visionneuse__page"><div style="font:500 14px/20px var(--font-texte)">Bilan fictif du 12 octobre 2026</div>'
            f'<div class="etaye-3" style="font:400 11px/16px var(--font-texte)">Document de démonstration · page {page_} / 2</div><div style="height:1px;background:var(--filet)"></div>'
            f'<div style="font:400 13px/22px var(--font-document)">{t}</div></div>')


def s13_visionneuse(mode="ok"):
    d, s = by_id("documents", "DOC-A-001"), S("SRC-A-003")
    if mode == "page-manquante":
        vis = (f'<div class="etaye-visionneuse"><div class="etaye-visionneuse__barre"><span>Page 3 demandée · le document a 2 pages</span></div>'
               f'{bandeau("attention", "Page citée introuvable", "La citation renvoie à une page absente de cette version. La phrase qui la cite ne doit pas être considérée comme vérifiée.", btn("Ouvrir la page 1", "secondaire"), role="alert")}</div>')
    elif mode == "expire":
        vis = (f'<div class="etaye-visionneuse">{bandeau("neutre", "Accès au fichier expiré", "Le lien d\'aperçu a expiré après 5 minutes d\'inactivité. Rien n\'a été téléchargé.", btn("Recharger l\'aperçu", "secondaire", "relancer"))}</div>')
    elif mode == "non-pris-en-charge":
        vis = (f'<div class="etaye-visionneuse">{bandeau("neutre", "Aperçu indisponible pour ce format", "Le fichier est conservé et vérifié. Téléchargez-le pour le lire.", btn("Télécharger", "secondaire", "telecharger"))}</div>')
    else:
        vis = (f'<div class="etaye-visionneuse"><div class="etaye-visionneuse__barre"><span>Page 1 / 2 · locator {esc(s["locator"])}</span>'
               f'<span class="etaye-ligne">{btn("Page précédente", "discret", disabled=True)}{btn("Page suivante", "discret")}</span></div>{page_doc()}</div>')
    meta = (f'<dl class="etaye-def"><dt>Document</dt><dd>{esc(d["title"])}</dd><dt>Date du document</dt><dd class="etaye-num">{dh_pro(s["occurredAt"])}</dd>'
            f'<dt>Déposé</dt><dd><span class="etaye-num">{dh_pro(s["recordedAt"])}</span> · {esc(auteur(s))}</dd><dt>Version</dt><dd>{s["version"]} · {esc(s["id"])}</dd>'
            f'<dt>Accès</dt><dd>Réservé aux professionnels · non partagé avec la patiente</dd><dt>Saisie</dt><dd>Résultats saisis manuellement · aucune extraction</dd></dl>')
    cite = (f'<section class="etaye-panneau"><div class="etaye-panneau__tete"><h2>Phrases qui citent cette page</h2></div><div class="etaye-panneau__corps etaye-pile" style="gap:var(--espace-2)">'
            f'<div class="doc-corps" style="font-size:15px;line-height:24px">« Les résultats de créatinine disponibles sont de 96 puis 104 µmol/L. »</div>'
            f'<div class="etaye-3" style="font-size:12px">Brief de préparation v1 · brouillon · 02/11/2026 14:05</div><div>{lien("Revenir à la phrase", href("S-18", "brouillon"))}</div></div></section>')
    body = f'<div class="etaye-cols etaye-cols--2"><div class="etaye-pile">{vis}</div><div class="etaye-pile">{panneau("Métadonnées", meta)}{cite}</div></div>'
    return dossier(CL, "Documents", body, titre="Bilan fictif du 12 octobre 2026", meta="Visionneuse sécurisée · aperçu sans adresse publique",
                   actions=btn("Télécharger", "secondaire", "telecharger") + btn("Revenir à la liste", "discret", href_=href("S-13", "claire")))


def i08(mode="ouvert"):
    err = mode == "erreur"
    depot = (f'<div class="etaye-depot">{ic("deposer", 24, "etaye-2")}<strong>Déposez un fichier ou choisissez-le</strong><span>PDF, PNG ou JPEG · 20 Mo au plus</span>{btn("Choisir un fichier", "secondaire")}</div>')
    if mode in ("envoi", "erreur"):
        depot = doc_row('<span class="etaye-num">scan-2026-11-02.pdf</span>', "1,2 Mo · PDF", statut("neutre", "Prêt à envoyer"), icon="document")
    f = [resume_erreurs([("u-date", "Date du document : indiquez une date ou cochez « inconnue »")])] if err else []
    f += [depot, champ("Titre", "", "u-t", required=True, help_="Exemple : intitulé figurant sur le document.", focus=mode == "envoi"),
          f'<div class="etaye-form__ligne">{champ("Date du document", "", "u-date", num=True, placeholder="JJ/MM/AAAA", error="indiquez une date ou cochez « date inconnue »." if err else None)}'
          f'<div style="align-self:end">{choix("u-inc", [("Date inconnue", "La date de dépôt ne la remplace pas")], typ="checkbox")}</div></div>',
          champ("Type", "Résultats de laboratoire", "u-ty", kind="select", options=["Résultats de laboratoire", "Courrier", "Autre document"]),
          '<p class="etaye-3" style="font-size:12px;margin:0">Le fichier reste indisponible pendant sa vérification. Il ne devient pas une source d\'information tant qu\'il n\'est pas accepté.</p>']
    return tiroir(s13(), "Ajouter un document", f'<div class="etaye-form">{"".join(f)}</div>', btn("Envoyer le document", "principal", disabled=mode == "ouvert") + btn("Annuler", "discret"), intro="MARTIN Claire · R-A-001")


# ── I-09 Source drawer ───────────────────────────────────────────────────────
def i09(mode="ouvert"):
    if mode == "allergie":
        fond, sid, n, phrase, sur = s10(), "SRC-A-001", 2, "Absence déclarée le 14/09/2026", "Aucune allergie connue"
    else:
        fond, sid, n, phrase, sur = s12(), "SRC-A-003", 1, "Créatinine 104 µmol/L le 12/10/2026", "créatinine 104 µmol/L"
    if mode == "indisponible":
        corps = (f'<div class="etaye-pile"><div class="etaye-2" style="font-size:13px">Phrase sélectionnée</div><div class="doc-corps" style="font-size:15px">« {phrase} »</div>'
                 f'{bandeau("attention", "Source indisponible", "La version citée (SRC-A-003 v1) n\'a pas pu être chargée. La phrase n\'est pas considérée comme vérifiée.", btn("Réessayer", "secondaire", "relancer"), role="alert")}</div>')
        return tiroir(fond, "Source datée", corps, btn("Fermer", "secondaire"))
    s = S(sid)
    corps = (f'<div class="etaye-pile"><div><div class="etaye-2" style="font-size:13px">Phrase sélectionnée</div><div class="doc-corps" style="font-size:15px">« {phrase} »</div></div>'
             f'{source_drawer_body(sid, n, sur)}'
             f'<div class="etaye-3" style="font-size:12px">Relation : la source contient exactement cette valeur ou cette déclaration. Consultation enregistrée dans l\'audit clinique.</div></div>')
    pied = (btn("Ouvrir le document autorisé", "principal", href_=href("S-13", "visionneuse")) if s.get("documentId") else "") + btn("Fermer", "secondaire")
    return tiroir(fond, "Source datée", corps, pied)


SCREENS = [
    {"id": "S-09", "name": "Dossier patient et chronologie", "states": [
        {"slug": "claire", "label": "Default — Claire Martin chronology, occurrence vs recording dates, contradiction & missing data aside", "html": s09_claire()},
        {"slug": "long", "label": "Long chronology — all 16 dataset events, no pagination (V-LONG baseline without duplicated events)", "html": s09_claire(None)},
        {"slug": "louis", "label": "Minimal history — Louis Bernard (V-EMPTY): checked-in, consultation in progress, cancelled visit, failed brief", "html": s09_louis()},
        {"slug": "corrige", "label": "Corrected event — weight 86,4 → 68,4 kg, version history inline (SRC-A-007-v1/v2)", "html": s09_corrige()},
        {"slug": "date-inconnue", "label": "Unknown occurrence date — rendering template (dataset gap, no clinical value)", "html": s09_date_inconnue()},
        {"slug": "erreur-partielle", "label": "Partial failure — measurements failed to load, other events kept (SP-STATE)", "html": s09_claire(9, fail=True)},
        {"slug": "lecture-seule", "label": "Read-only membership — permission test variant (CLINICAL_READ only)", "html": s09_claire(9, readonly=True)},
    ]},
    {"id": "S-10", "name": "Antécédents, allergies et traitements déclarés", "states": [
        {"slug": "claire", "label": "Default — contradictory allergy statements, both dated sources; treatments missing (not 'none')", "html": s10()},
        {"slug": "louis", "label": "Missing — Louis Bernard, every section unknown/not documented (V-EMPTY)", "html": s10(LO)},
        {"slug": "evaluation", "label": "Practitioner assessment being added — dirty form, focus on reason, patient report preserved", "html": s10_evaluation()},
        {"slug": "evaluation-erreur", "label": "Assessment save refused — error summary + field error, input kept", "html": s10_evaluation(True)},
        {"slug": "lecture-seule", "label": "Read-only — permission test variant, no add/correct actions", "html": s10(CL, "lecture")},
    ]},
    {"id": "I-27", "name": "Examiner des informations manquantes ou discordantes", "states": [
        {"slug": "ouvert", "label": "Inline review — opposing dated statements side by side, missing field, no auto-resolution", "html": i27_open()},
        {"slug": "conserve", "label": "After 'Conserver à vérifier' — disposition recorded, both sources unchanged (toast)", "html": i27_enregistre()},
    ]},
    {"id": "S-11", "name": "Mesures et tendances", "states": [
        {"slug": "claire", "label": "Default — BP 128/80 then 134/82 mmHg chart + table; weight single corrected point (no curve)", "html": s11()},
        {"slug": "louis", "label": "No measurements — Louis Bernard (V-EMPTY)", "html": s11("louis")},
        {"slug": "chargement", "label": "Loading — skeleton sections", "html": s11("chargement")},
        {"slug": "gabarits", "label": "Mixed units / unknown date — rendering templates only (dataset gap)", "html": s11_gabarits()},
    ]},
    {"id": "I-06", "name": "Saisir ou corriger une mesure", "states": [
        {"slug": "ouvert", "label": "Drawer — new measurement entry, focus on first value, dirty", "html": i06()},
        {"slug": "erreur", "label": "Impossible entry — 1340 mmHg refused, missing date; summary + field errors, input kept", "html": i06("erreur")},
        {"slug": "correction", "label": "Correction — weight v2, reason required, old value kept in history", "html": i06("correction")},
        {"slug": "conflit", "label": "Version conflict — v2 saved at 10:15 while user edited v1; input preserved → I-21", "html": i06("conflit")},
    ]},
    {"id": "S-12", "name": "Biologie et intervalles fournis", "states": [
        {"slug": "claire", "label": "Default — creatinine 96 → 104 µmol/L, glucose 5,2 mmol/L, lab-supplied intervals, chart + table", "html": s12()},
        {"slug": "sans-intervalle", "label": "V-LAB-NO-INTERVAL — 'Intervalle non fourni', no generic interval invented", "html": s12("sans-intervalle")},
        {"slug": "louis", "label": "Empty — Louis Bernard (V-EMPTY)", "html": s12("louis")},
    ]},
    {"id": "I-07", "name": "Saisir ou corriger un résultat de biologie", "states": [
        {"slug": "ouvert", "label": "Drawer — manual entry, numeric/textual choice, supplied interval or 'non fourni', source required", "html": i07()},
        {"slug": "erreur", "label": "Validation — unit and source missing; input kept", "html": i07("erreur")},
    ]},
    {"id": "S-13", "name": "Documents du dossier et visionneuse de sources", "states": [
        {"slug": "claire", "label": "Default — DOC-A-001 ready, document vs upload dates, clinician-only access", "html": s13()},
        {"slug": "file", "label": "Upload queue — sending, scanning (long filename), rejected format; UI placeholder filenames", "html": s13("file")},
        {"slug": "visionneuse", "label": "Viewer — page 1 with cited excerpt highlighted, metadata, citing statement + return link", "html": s13_visionneuse()},
        {"slug": "page-manquante", "label": "Missing page — citation points to absent page; not shown as verified", "html": s13_visionneuse("page-manquante")},
        {"slug": "acces-expire", "label": "Expired preview access — reload, nothing downloaded", "html": s13_visionneuse("expire")},
        {"slug": "apercu-indisponible", "label": "Unsupported preview — download only", "html": s13_visionneuse("non-pris-en-charge")},
        {"slug": "louis", "label": "Empty — no documents (V-EMPTY)", "html": s13("louis")},
    ]},
    {"id": "I-08", "name": "Ajouter un document", "states": [
        {"slug": "ouvert", "label": "Drawer — empty drop zone, submit disabled until a file is chosen", "html": i08()},
        {"slug": "envoi", "label": "File chosen — metadata entry, unknown-date option", "html": i08("envoi")},
        {"slug": "erreur", "label": "Validation — date missing; file kept", "html": i08("erreur")},
    ]},
    {"id": "I-09", "name": "Ouvrir la source", "states": [
        {"slug": "ouvert", "label": "Source drawer over labs — SRC-A-003 excerpt highlighted, dates, author, version, locator", "html": i09()},
        {"slug": "allergie", "label": "Source drawer over history — SRC-A-001 dated explicit negative (patient-reported)", "html": i09("allergie")},
        {"slug": "indisponible", "label": "Source unavailable — statement not treated as verified", "html": i09("indisponible")},
    ]},
]

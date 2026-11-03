"""Patient directory and identity — S-07 directory/search, S-08 identity create/edit, I-04 identity page.
Scheduling-only variants follow PD-001 (proposal, not approved): Mila Dubois with SCHEDULE_MANAGE + PATIENT_REGISTER.
"""
from lib import *

RECS = [r for r in DATA["records"] if r["practiceId"] == "PRA-A"]


def portail(r):
    if r["portalAccountId"]:
        return st("invitation", "ACCEPTED", "Espace patient activé")
    inv = [i for i in DATA["invitations"] if i.get("recordId") == r["id"]]
    return st("invitation", inv[0]["status"], "Invitation expirée") if inv else statut("neutre", "Sans espace patient")


def lignes_patients(recs, sched=False):
    rows = []
    for r in recs:
        n = f'{esc(r["name"]["family"].upper())} {esc(r["name"]["given"])}'
        link = n if sched else f'<a href="{href("S-09", "claire" if r["id"] == "R-A-001" else "louis")}">{n}</a>'
        detail = (f'Naissance <span class="etaye-num">{d_court(r["birthDate"])}</span> · {age(r["birthDate"])}{NB}ans · dossier <span class="etaye-num">{esc(r["id"])}</span>'
                  if not sched else f'Naissance <span class="etaye-num">{d_court(r["birthDate"])}</span> · coordonnées : non renseignées')
        act = btn("Choisir", "secondaire") if sched else portail(r)
        rows.append(f'<li><span class="etaye-lignes__heure" aria-hidden="true">{ic("compte", 18, "etaye-2")}</span><div><div class="etaye-lignes__titre">{link}</div>'
                    f'<div class="etaye-lignes__detail">{detail}</div></div><div class="etaye-lignes__actions">{act}</div></li>')
    return f'<ul class="etaye-lignes">{"".join(rows)}</ul>'


def recherche(q="", filtres=True):
    f = (f'<div class="etaye-filtres" role="search" aria-label="Rechercher un patient"><div style="flex:1;min-width:240px">{champ("Nom, prénom ou date de naissance", q, "q", typ="search", help_="Exemple : Martin ou 18/04/1978")}</div>'
         + (champ("Espace patient", "Tous", "f-portail", kind="select", options=["Tous", "Activé", "Invitation en attente ou expirée", "Sans espace patient"]) if filtres else "")
         + '</div>')
    return f.replace('class="etaye-champ"', 'class="etaye-champ"', 1)


def s07(mode="liste"):
    actor, sched = ("mila", True) if mode == "planificateur" else ("nora", False)
    band, body = "", ""
    q = {"aucun-resultat": "Petit", "liste": "", "chargement": "Mar", "erreur": "", "perime": "", "planificateur": "Mar", "vide": "", "longs": ""}.get(mode, "")
    if mode == "aucun-resultat":
        body = vide("Aucun patient trouvé pour « Petit » au Cabinet des Tilleuls", "La recherche porte uniquement sur les dossiers de ce cabinet. Vérifiez l'orthographe ou créez le dossier.",
                    btn("Créer un dossier patient", "secondaire", "ajouter", href_=href("S-08")))
    elif mode == "chargement":
        body = squelette(4)
    elif mode == "vide":
        body = vide("Aucun patient dans ce cabinet", "Les dossiers créés ici n'apparaissent que dans ce cabinet.", btn("Créer un dossier patient", "principal", "ajouter", href_=href("S-08")))
        band = '<p class="etaye-3" style="font-size:12px;margin:0">Gabarit : le Cabinet des Tilleuls a deux dossiers dans le jeu de données.</p>'
    elif mode == "erreur":
        band = bandeau("attention", "La liste n'a pas pu être chargée entièrement", "Les résultats affichés datent de 16:00. Certains dossiers peuvent manquer.", btn("Réessayer", "secondaire", "relancer"), role="alert")
        body = lignes_patients(RECS[:1])
    elif mode == "perime":
        band = bandeau("info", "Résultats à actualiser", "Un dossier a été modifié depuis votre recherche (16:00).", btn("Actualiser", "secondaire", "relancer"))
        body = lignes_patients(RECS)
    elif mode == "longs":
        band = '<p class="etaye-3" style="font-size:12px;margin:0">Gabarit de mise en page : noms longs fictifs pour tester le retour à la ligne ; ce ne sont pas des dossiers du jeu.</p>'
        body = (f'<ul class="etaye-lignes"><li><span class="etaye-lignes__heure" aria-hidden="true">{ic("compte", 18, "etaye-2")}</span><div><div class="etaye-lignes__titre">[NOM DE NAISSANCE COMPOSÉ TRÈS LONG-NOM D\'USAGE] [Prénom composé]</div>'
                f'<div class="etaye-lignes__detail">Naissance <span class="etaye-num">JJ/MM/AAAA</span> · la date reste entière, jamais tronquée</div></div><div class="etaye-lignes__actions">{statut("neutre", "Sans espace patient")}</div></li></ul>' + lignes_patients(RECS))
    elif mode == "planificateur":
        band = bandeau("neutre", "Recherche pour l'agenda", "Vous voyez seulement le nom, la date de naissance et les coordonnées. Aucun dossier clinique n'est accessible. Proposition PD-001, non validée.", icon="verrou")
        body = lignes_patients(RECS[:1], sched=True)
    else:
        body = lignes_patients(RECS)
    count = {"liste": "2 patients", "perime": "2 patients", "erreur": "1 patient affiché", "planificateur": "1 résultat pour « Mar »", "longs": "3 lignes"}.get(mode, "")
    acts = "" if sched else btn("Créer un dossier patient", "principal", "ajouter", href_=href("S-08"))
    page = (f'<div class="etaye-page">{entete("Patients", "Cabinet des Tilleuls · les dossiers des autres cabinets n\'apparaissent jamais ici", acts)}'
            f'{recherche(q, not sched)}{band}<div class="etaye-ligne" style="justify-content:space-between"><span class="etaye-3" style="font-size:13px">{count}</span></div>{body}</div>')
    return pro_shell("Patients" if not sched else "", page, actor)


# ── S-08 / I-04 Identity ─────────────────────────────────────────────────────
def form_identite(mode="nouveau", sched=False):
    claire = by_id("records", "R-A-001")
    edit = mode in ("modifier", "conflit", "enregistrement", "sale")
    v = (lambda k, d="": d)
    fam = "Martin" if edit or mode == "doublon" else ("" if mode in ("nouveau",) else "Martin")
    giv = "Claire" if edit or mode in ("doublon", "erreur") else ""
    born = "18/04/1978" if edit or mode == "doublon" else ("31/02/1978" if mode == "erreur" else "")
    top = ""
    if mode == "erreur":
        top = resume_erreurs([("i-nom", "Nom de naissance : obligatoire"), ("i-dn", "Date de naissance : cette date n'existe pas")])
        fam = ""
    if mode == "doublon":
        top = bandeau("attention", "Un dossier semblable existe dans ce cabinet",
                      f'MARTIN Claire · naissance 18/04/1978 · dossier R-A-001. Vérifiez avant de créer un second dossier. Rien n\'est fusionné automatiquement.',
                      (btn("Ouvrir le dossier existant", "secondaire", href_=href("S-09")) if not sched else "") + btn("Créer quand même", "discret"), role="alert")
    if mode == "conflit":
        top = bandeau("attention", "Ce dossier a été modifié entre-temps",
                      "La version 8 a été enregistrée pendant votre saisie. Vos modifications sont conservées ci-dessous ; comparez avant d'enregistrer.",
                      btn("Comparer les versions", "secondaire", href_=href("I-21")), role="alert")
    req = lambda: True
    f = [top,
         f'<div class="etaye-form__ligne">{champ("Nom de naissance", fam, "i-nom", required=True, error="indiquez le nom de naissance." if mode == "erreur" else None, focus=mode == "nouveau")}'
         f'{champ("Prénom", giv, "i-pre", required=True)}</div>',
         f'<div class="etaye-form__ligne">{champ("Nom d\'usage", "", "i-usage", optional=True)}'
         f'{champ("Date de naissance", born, "i-dn", num=True, required=True, placeholder="JJ/MM/AAAA", error="le 31 février n\'existe pas. Format JJ/MM/AAAA." if mode == "erreur" else None)}</div>',
         f'<div class="etaye-form__ligne">{champ("Téléphone", "", "i-tel", optional=True, typ="tel")}{champ("Adresse e-mail", "", "i-mail", optional=True, typ="email", help_="Non renseignée au dossier" if edit else None)}</div>',
         champ("Origine de ces informations", "Déclarées par la patiente au cabinet", "i-orig", kind="select", options=["Déclarées par la patiente au cabinet", "Document d'identité présenté", "Saisie depuis un courrier"]),
         '<p class="etaye-3" style="font-size:13px;margin:0">L\'espace patient n\'est pas créé ici : une invitation séparée est envoyée par l\'équipe soignante.</p>']
    state = {"sale": enregistrement("sale"), "enregistrement": enregistrement("encours"), "conflit": enregistrement("echec")}.get(mode, "")
    busy = mode == "enregistrement"
    pied = (btn("Enregistrement…", "principal", disabled=True) if busy else btn("Enregistrer l'identité", "principal")) + btn("Annuler", "discret")
    return f'<form class="etaye-form" novalidate style="max-width:720px">{"".join(f)}<div class="etaye-ligne">{pied}{state}</div></form>'


def s08(mode="nouveau"):
    sched = mode == "planificateur"
    actor = "mila" if sched else "nora"
    titre = "Modifier l'identité" if mode in ("modifier", "conflit", "enregistrement", "sale") else "Nouveau dossier patient"
    meta = ("MARTIN Claire · dossier R-A-001 · version 7 ouverte" if titre.startswith("Modifier") else "Cabinet des Tilleuls · le dossier n'existera que dans ce cabinet")
    band = ""
    if sched:
        band = bandeau("neutre", "Enregistrement administratif pour l'agenda", "Identité et coordonnées uniquement. Vous revenez ensuite à la prise de rendez-vous ; aucun dossier clinique ne s'ouvre. Proposition PD-001, non validée.", icon="verrou")
    fil = f'<a href="{href("S-07")}">Patients</a> › {titre}'
    page = f'<div class="etaye-page">{entete(titre, meta, "", fil)}{band}{form_identite("nouveau" if sched else mode, sched)}</div>'
    banner = banniere("R-A-001") if titre.startswith("Modifier") else None
    return pro_shell("Patients" if not sched else "", page, actor, banner=banner)


def i20_identite():
    fond = s08("sale")
    corps = ('<p style="margin:0">Vous avez modifié l\'identité de MARTIN Claire. Ces modifications ne sont pas enregistrées.</p>'
             '<p class="etaye-2" style="margin:0;font-size:14px">Destination : Aujourd\'hui</p>')
    pied = btn("Enregistrer et continuer", "principal") + btn("Rester sur cette page", "secondaire") + btn("Quitter sans enregistrer", "discret")
    return dialogue(fond, "Modifications non enregistrées", corps, pied)


SCREENS = [
    {"id": "S-07", "name": "Annuaire et recherche de patients", "states": [
        {"slug": "liste", "label": "Initial — two Cabinet des Tilleuls records, birth date and portal status visible", "html": s07()},
        {"slug": "aucun-resultat", "label": "No result — 'Petit' from PRA-A never finds Hugo Petit (AC-S07-F, SC-ISOLATION)", "html": s07("aucun-resultat")},
        {"slug": "chargement", "label": "Loading", "html": s07("chargement")},
        {"slug": "vide", "label": "No patients (template)", "html": s07("vide")},
        {"slug": "erreur", "label": "Partial error — results kept with timestamp", "html": s07("erreur")},
        {"slug": "perime", "label": "Stale results — refresh offered", "html": s07("perime")},
        {"slug": "longs", "label": "Long names — layout template, birth date never truncated", "html": s07("longs")},
        {"slug": "planificateur", "label": "Scheduling lookup — Mila Dubois, minimal projection, no record links (PD-001 proposal)", "html": s07("planificateur")},
    ]},
    {"id": "S-08", "name": "Création et modification de l'identité", "states": [
        {"slug": "nouveau", "label": "New — required/optional explicit, focus on first field", "html": s08()},
        {"slug": "modifier", "label": "Edit — Claire Martin, version 7 opened", "html": s08("modifier")},
        {"slug": "erreur", "label": "Validation — missing name, impossible date", "html": s08("erreur")},
        {"slug": "doublon", "label": "Possible local duplicate — inspect, no automatic merge, no cross-practice search", "html": s08("doublon")},
        {"slug": "conflit", "label": "Version conflict — edits kept, compare via I-21 (AC-S08-F)", "html": s08("conflit")},
        {"slug": "enregistrement", "label": "Saving — single submit", "html": s08("enregistrement")},
        {"slug": "sale", "label": "Unsaved changes indicator", "html": s08("sale")},
        {"slug": "planificateur", "label": "Scheduler create-only — Mila Dubois, demographic identity only (PD-001 proposal)", "html": s08("planificateur")},
    ]},
    {"id": "I-04", "name": "Créer ou modifier l'identité du patient", "states": [
        {"slug": "ouvert", "label": "Identity page — create, same form as S-08 'nouveau'", "html": s08()},
        {"slug": "doublon", "label": "Duplicate suggestion inside the page", "html": s08("doublon")},
        {"slug": "planificateur", "label": "Scheduler variant returns to I-10 after save", "html": s08("planificateur")},
    ]},
    {"id": "I-20", "name": "Quitter avec des modifications non enregistrées", "states": [
        {"slug": "ouvert", "label": "Dirty identity edit, leaving to Today — save and continue / stay / explicit discard", "html": i20_identite()},
    ]},
]

"""Scheduling family — S-14 practice schedule, S-15 availability, S-16 appointment detail,
interactions I-10 create, I-11 move, I-12 cancel, I-13 attendance, I-24 availability change.
Appointments, availability and practitioners come from synthetic-data.json (all 30-minute visits).
"""
from lib import *

A = lambda i: by_id("appointments", i)
PR = {"PR-NORA": "Dr Nora Lefebvre", "PR-SAMI-A": "Dr Sami Rahmani"}
SLUGREC = {"R-A-001": "claire", "R-A-002": "louis"}
APTSLUG = {"APT-A-001": "claire", "APT-A-002": "louis", "APT-A-003": "termine", "APT-A-004": "annule", "APT-A-005": "non-honore"}


def hm(h, m):
    return f"{h:02d}:{m:02d}"


def agenda(jour_titre, cols, items, h0=9, h1=12, sched=False, note=""):
    """cols: [(label, dispo:[(h,m,h,m)])]; items: {(col, 'HH:MM'): html}. 30-minute rows."""
    slots = [(h, m) for h in range(h0, h1) for m in (0, 30)]
    head = '<div class="etaye-agenda__tete" style="border-left:0"></div>' + "".join(f'<div class="etaye-agenda__tete">{esc(c[0])}</div>' for c in cols)
    cells = []
    for h, m in slots:
        cells.append(f'<div class="etaye-agenda__heure">{hm(h, m)}</div>')
        for ci, (lab, dispo) in enumerate(cols):
            inside = any((dh, dm) <= (h, m) < (eh, em) for dh, dm, eh, em in dispo)
            c = items.get((ci, hm(h, m)), "")
            cls = "etaye-agenda__case" + (" etaye-agenda__case--dispo" if inside and not c else "")
            lab_ = f' aria-label="{hm(h, m)} · {esc(lab)} · créneau libre"' if inside and not c else ""
            cells.append(f'<div class="{cls}"{lab_}>{c}</div>')
    grid = f'<div class="etaye-agenda etaye-agenda--grille" style="--cols:{len(cols)}" role="grid" aria-label="{esc(jour_titre)}">{head}{"".join(cells)}</div>'
    return grid


def rdv(apt_id, sched=False):
    a = A(apt_id)
    r = by_id("records", a["recordId"])
    n = f'{r["name"]["family"].upper()} {r["name"]["given"]}'
    cls = "etaye-rdv" + (" etaye-rdv--annule" if a["status"] in ("CANCELLED", "NO_SHOW") else "")
    stl = AXES["appointment"][a["status"]][1]
    inner = f'<strong>{esc(n)}</strong><span>{h_pro(a["startsAt"])}–{h_pro(a["endsAt"])} · {stl}</span>'
    return f'<a class="{cls}" href="{href("S-16", APTSLUG.get(a["id"], "claire"))}" style="text-decoration:none;color:inherit">{inner}</a>'


def liste_mobile(apts, sched=False, libres=""):
    rows = []
    for i in apts:
        a = A(i)
        r = by_id("records", a["recordId"])
        n = f'{r["name"]["family"].upper()} {r["name"]["given"]}'
        reason = "" if sched else (f' · {esc(a["reason"])}' if a.get("reason") else "")
        extra = f' · motif d\'annulation : {esc(a["cancellationReason"])}' if a.get("cancellationReason") else ""
        rows.append(f'<li><span class="etaye-lignes__heure">{h_pro(a["startsAt"])}<br>{h_pro(a["endsAt"])}</span><div><div class="etaye-lignes__titre">{esc(n)}</div>'
                    f'<div class="etaye-lignes__detail">{PR[a["practitionerId"]]}{reason}{extra}</div></div><div class="etaye-lignes__actions">{st("appointment", a["status"])}</div></li>')
    l = f'<ul class="etaye-lignes">{"".join(rows)}</ul>' if rows else ""
    return f'<div class="etaye-agenda--liste">{l}{libres}</div>'


def jours_nav(actif):
    days = [("2026-11-02", "lun."), ("2026-11-03", "mar."), ("2026-11-04", "mer."), ("2026-11-05", "jeu."), ("2026-11-06", "ven.")]
    b = "".join(f'<button class="etaye-jour" type="button" aria-pressed="{"true" if d == actif else "false"}">{l}<b>{int(d[-2:])}</b></button>' for d, l in days)
    return f'<div class="etaye-jours" role="group" aria-label="Choisir le jour">{b}</div>'


def page_agenda(titre, meta, corps, actor="nora", band="", actif="2026-11-03", outils=True):
    acts = (btn("Jour précédent", "discret", "precedent") + btn("Aujourd'hui", "secondaire") + btn("Prendre rendez-vous", "principal", "ajouter", href_=href("I-10"))) if outils else ""
    filt = (f'<div class="etaye-filtres">{champ("Praticien", "Tous les praticiens", "ag-pr", kind="select", options=["Tous les praticiens", "Dr Nora Lefebvre", "Dr Sami Rahmani"])}'
            f'{champ("Vue", "Jour", "ag-vue", kind="select", options=["Jour", "Semaine"])}</div>')
    body = f'<div class="etaye-page">{entete(titre, meta, acts)}{band}{jours_nav(actif)}{filt}{corps}</div>'
    return pro_shell("Agenda" if actor != "mila" else "", body, actor)


COLS_MAR = [("Dr Nora Lefebvre", [(9, 0, 12, 0)]), ("Dr Sami Rahmani", [])]
LIBRES_MAR = '<p class="etaye-3" style="font-size:13px">Dr Nora Lefebvre · créneaux libres 09:00, 09:30, 10:00, 11:00, 11:30 · Dr Sami Rahmani : aucune disponibilité enregistrée</p>'


def s14(mode="jour"):
    sched = mode == "planificateur"
    actor = "mila" if sched else "nora"
    band = ""
    if mode == "lundi":
        g = agenda("Lundi 2 novembre", [("Dr Nora Lefebvre", []), ("Dr Sami Rahmani", [])], {(1, "14:00"): rdv("APT-A-002")}, 13, 16)
        corps = g + liste_mobile(["APT-A-002"]) + '<p class="etaye-3" style="font-size:13px">Aucune disponibilité ouverte à la réservation ce jour. La consultation CONS-A-002 est en cours depuis 14:10 ; elle est distincte du rendez-vous.</p>'
        return page_agenda("Lundi 2 novembre 2026", "Cabinet des Tilleuls · Europe/Paris", corps, actif="2026-11-02")
    if mode == "vide":
        corps = vide("Aucun rendez-vous ni disponibilité le mercredi 4 novembre", "Ouvrez une disponibilité pour permettre des réservations.", btn("Gérer les disponibilités", "secondaire", href_=href("S-15")))
        return page_agenda("Mercredi 4 novembre 2026", "Cabinet des Tilleuls · Europe/Paris", corps, actif="2026-11-04")
    if mode == "erreur":
        band = bandeau("attention", "L'agenda n'a pas pu être chargé", "Aucun rendez-vous n'a été modifié. Les rendez-vous affichés ailleurs (Aujourd'hui) restent valables.", btn("Réessayer", "secondaire", "relancer"), role="alert")
        return page_agenda("Mardi 3 novembre 2026", "Cabinet des Tilleuls · Europe/Paris", squelette(6), band=band)
    if mode == "annule":
        g = agenda("Lundi 26 octobre", [("Dr Nora Lefebvre", []), ("Dr Sami Rahmani", [])], {(1, "11:00"): rdv("APT-A-004")}, 10, 12)
        corps = g + liste_mobile(["APT-A-004"]) + '<p class="etaye-3" style="font-size:13px">Un rendez-vous annulé reste visible et barré de gris ; le créneau peut être proposé à nouveau.</p>'
        return page_agenda("Lundi 26 octobre 2026", "Instantané historique · Europe/Paris, heure d'hiver depuis le 25/10", corps, actor="sami_a", actif="")
    if mode == "non-honore":
        g = agenda("Jeudi 15 octobre", [("Dr Nora Lefebvre", []), ("Dr Sami Rahmani", [])], {(0, "09:00"): rdv("APT-A-005")}, 9, 10)
        corps = g + liste_mobile(["APT-A-005"])
        return page_agenda("Jeudi 15 octobre 2026", "Instantané historique · Europe/Paris, heure d'été", corps, actif="")
    if mode == "changement-heure":
        band = bandeau("info", "Changement d'heure le dimanche 25 octobre 2026", "À 03:00, les horloges reculent à 02:00 (heure d'hiver). Les heures affichées sont toujours l'heure locale de Paris ; un rendez-vous ne change pas d'heure affichée.")
        g = agenda("Mardi 20 octobre", [("Dr Nora Lefebvre", []), ("Dr Sami Rahmani", [])], {(0, "10:00"): rdv("APT-A-003")}, 9, 11)
        corps = g + liste_mobile(["APT-A-003"]) + '<p class="etaye-3" style="font-size:13px">20/10/2026 10:00 est en heure d\'été (UTC+2) ; 26/10/2026 11:00 est en heure d\'hiver (UTC+1).</p>'
        return page_agenda("Mardi 20 octobre 2026", "Semaine du changement d'heure · Europe/Paris", corps, band=band, actif="")
    if mode == "conflit":
        band = bandeau("attention", "Ce créneau vient d'être réservé", "Une autre réservation a été enregistrée à 10:30 juste avant la vôtre (SC-BOOKING-RACE). La vôtre n'a pas été créée ; le motif saisi est conservé dans la fenêtre de rendez-vous.",
                       btn("Choisir un autre créneau", "secondaire", href_=href("I-10", "conflit")), role="alert")
    g = agenda("Mardi 3 novembre", COLS_MAR, {(0, "10:30"): rdv("APT-A-001", sched)}, 9, 12, sched)
    corps = g + liste_mobile(["APT-A-001"], sched, LIBRES_MAR)
    if sched:
        band = bandeau("neutre", "Agenda — accès administratif", "Noms et horaires seulement ; motifs, préparations et dossiers ne sont pas visibles. Proposition PD-001, non validée.", icon="verrou")
    return page_agenda("Mardi 3 novembre 2026", "Cabinet des Tilleuls · Europe/Paris · créneaux de 30 minutes", corps, actor, band)


# ── S-15 availability ────────────────────────────────────────────────────────
def s15(mode="semaine"):
    if mode == "vide":
        body = (f'<div class="etaye-page">{entete("Disponibilités", "Dr Sami Rahmani · Cabinet des Tilleuls · Europe/Paris", btn("Ajouter une disponibilité", "principal", "ajouter", href_=href("I-24")))}'
                + vide("Aucune disponibilité", "Les patients ne peuvent pas réserver avec vous tant qu'aucune disponibilité n'est ouverte.", btn("Ajouter une disponibilité", "secondaire", "ajouter", href_=href("I-24"))) + '</div>')
        return pro_shell("Disponibilités", body, "sami_a")
    av = by_id("availability", "AVL-A-001")
    modele = panneau("Modèle hebdomadaire", '<p class="etaye-2" style="margin:0;font-size:14px">Aucun modèle récurrent. Les disponibilités ci-dessous sont des plages datées.</p>', btn("Créer un modèle", "discret"))
    datee = (f'<ul class="etaye-lignes"><li><span class="etaye-lignes__heure">{h_pro(av["startsAt"])}<br>{h_pro(av["endsAt"])}</span><div><div class="etaye-lignes__titre">Mardi 3 novembre 2026</div>'
             f'<div class="etaye-lignes__detail">Créneaux de {av["slotMinutes"]} minutes · réservable par les patients · 1 rendez-vous réservé (10:30) · AVL-A-001</div></div>'
             f'<div class="etaye-lignes__actions">{btn("Modifier", "secondaire", "modifier", href_=href("I-24"))}</div></li></ul>')
    exc = panneau("Exceptions", '<p class="etaye-2" style="margin:0;font-size:14px">Aucune exception (fermeture ou plage ponctuelle) enregistrée.</p>', btn("Ajouter une exception", "discret", "ajouter"))
    body = (f'<div class="etaye-page">{entete("Disponibilités", "Dr Nora Lefebvre · Cabinet des Tilleuls · fuseau Europe/Paris", btn("Ajouter une disponibilité", "principal", "ajouter", href_=href("I-24")))}'
            f'<div class="etaye-cols etaye-cols--2"><section class="etaye-pile"><h2 class="ui-section">Plages datées</h2>{datee}</section><div class="etaye-pile">{modele}{exc}</div></div></div>')
    return pro_shell("Disponibilités", body, "nora")


def i24(mode="ouvert"):
    fond = s15()
    err = mode == "erreur"
    impact = mode == "impact"
    top = resume_erreurs([("av-fin", "Fin : doit être après le début")]) if err else ""
    if mode == "conflit":
        top = bandeau("attention", "Ces disponibilités ont été modifiées entre-temps", "Votre saisie est conservée. Rechargez pour voir la version actuelle avant d'enregistrer.", btn("Comparer", "secondaire", href_=href("I-21")), role="alert")
    imp = ""
    if impact:
        imp = (f'<div class="etaye-bandeau etaye-bandeau--attention" role="alert">{ic("alerte", 18)}<div><div class="etaye-bandeau__titre">1 rendez-vous concerné</div>'
               f'<div>MARTIN Claire · mardi 03/11/2026 10:30–11:00 est hors de la nouvelle plage. Il reste réservé : aucune annulation automatique. Décidez séparément de le déplacer ou de le garder.</div></div>'
               f'<div class="etaye-bandeau__actions">{btn("Ouvrir le rendez-vous", "secondaire", href_=href("S-16"))}</div></div>')
    f = (f'{top}{champ("Date", "03/11/2026", "av-d", num=True, required=True)}'
         f'<div class="etaye-form__ligne">{champ("Début", "09:00", "av-deb", num=True, required=True)}{champ("Fin", "08:30" if err else ("10:30" if impact else "12:00"), "av-fin", num=True, required=True, error="la fin doit être après le début (09:00)." if err else None, focus=mode == "ouvert")}</div>'
         f'{champ("Durée des créneaux", "30 minutes", "av-dur", kind="select", options=["15 minutes", "30 minutes", "45 minutes"])}'
         f'{choix("av-pat", [("Réservable par les patients", "Visible dans leur espace")], typ="checkbox", checked=(0,))}'
         f'<p class="etaye-3" style="font-size:13px;margin:0">Fuseau du cabinet : Europe/Paris. Un changement d\'heure est appliqué à l\'heure locale.</p>{imp}')
    return tiroir(fond, "Modifier les disponibilités", f'<div class="etaye-form">{f}</div>', btn("Enregistrer les disponibilités", "principal") + btn("Annuler", "discret"), intro="AVL-A-001 · Dr Nora Lefebvre")


# ── S-16 appointment detail ──────────────────────────────────────────────────
def s16(apt_id="APT-A-001", mode=""):
    a = A(apt_id)
    r = by_id("records", a["recordId"])
    actor = "sami_a" if a["practitionerId"] == "PR-SAMI-A" else "nora"
    rows = [("Patient", f'<a href="{href("S-09", SLUGREC[a["recordId"]])}">{nom(a["recordId"], "famille")}</a> · naissance <span class="etaye-num">{d_court(r["birthDate"])}</span>'),
            ("Praticien", PR[a["practitionerId"]]),
            ("Date et heure", f'<span class="etaye-num">{d_court(a["startsAt"])} {h_pro(a["startsAt"])}–{h_pro(a["endsAt"])}</span> · Europe/Paris'),
            ("Motif", esc(a.get("reason") or "Non renseigné")),
            ("Statut", st("appointment", a["status"])),
            ("Version", f'{a["version"]} · {esc(a["id"])}')]
    if a.get("cancellationReason"):
        rows.append(("Motif d'annulation", esc(a["cancellationReason"])))
    cons = [c for c in DATA["consultations"] if c.get("appointmentId") == apt_id or (apt_id == "APT-A-002" and c["id"] == "CONS-A-002")]
    cons_html = ""
    if cons:
        c = cons[0]
        etat_c = f'terminée le {dh_pro(c["completedAt"])}' if c["status"] == "COMPLETED" else f'en cours depuis {h_pro(c["startedAt"])}'
        cons_html = panneau("Consultation", f'<p style="margin:0">{esc(c["id"])} · {etat_c} · {PR[c["practitionerId"]]}</p><p class="etaye-3" style="font-size:13px;margin:0">La consultation est distincte du rendez-vous : la démarrer ne marque pas le rendez-vous comme terminé.</p>')
    prep = ""
    if apt_id == "APT-A-001":
        prep = panneau("Préparation de visite", f'<p style="margin:0">Reçue le 02/11/2026 à 09:10 · déclarée par la patiente</p><div class="etaye-ligne">{btn("Lire la préparation", "secondaire", href_=href("S-17"))}{btn("Ouvrir le brief", "discret", href_=href("S-18"))}</div>')
    acts = ""
    if a["status"] == "BOOKED":
        acts = btn("Déplacer", "secondaire", href_=href("I-11")) + btn("Annuler le rendez-vous", "discret", href_=href("I-12"))
    band = ""
    if mode == "modifie":
        band = bandeau("attention", "Ce rendez-vous a été modifié entre-temps", "Dr Sami Rahmani a enregistré l'arrivée (version 2) pendant que vous consultiez la version 1. La page affiche maintenant la version 2.", role="alert")
    att = ""
    if apt_id == "APT-A-002":
        att = panneau("Présence", f'<p style="margin:0">{st("appointment", "CHECKED_IN")} Heure d\'arrivée non renseignée dans le jeu de démonstration.</p>'
                      f'<div>{btn("Modifier la présence", "discret", href_=href("I-13"))}</div>')
    dl = "".join(f"<dt>{k}</dt><dd>{v}</dd>" for k, v in rows)
    body = (f'<div class="etaye-page">{entete("Rendez-vous", f"{d_long(a["startsAt"], jour=True)} · {h_pro(a["startsAt"])}", acts, f"<a href=\"{href("S-14")}\">Agenda</a> › {esc(a["id"])}")}{band}'
            f'<div class="etaye-cols etaye-cols--2"><section class="etaye-panneau"><div class="etaye-panneau__corps"><dl class="etaye-def">{dl}</dl></div></section>'
            f'<div class="etaye-pile">{cons_html}{att}{prep}</div></div></div>')
    return pro_shell("Agenda", body, actor)


# ── I-10 / I-11 / I-12 / I-13 ────────────────────────────────────────────────
def creneaux(pris=("10:30",), choisi=None, perdu=None):
    out = []
    for t in ("09:00", "09:30", "10:00", "10:30", "11:00", "11:30"):
        dis = t in pris
        lab = f"{t} · réservé" if dis else t
        out.append(f'<button class="etaye-creneau" type="button" aria-pressed="{"true" if t == choisi else "false"}"{" disabled" if dis else ""} aria-label="{lab}">{t}</button>')
    return f'<fieldset style="border:0;padding:0;margin:0"><legend class="etaye-champ__libelle">Créneau · mardi 3 novembre 2026 · Europe/Paris</legend><div class="etaye-creneaux">{"".join(out)}</div></fieldset>'


def i10(mode="ouvert"):
    fond = s14()
    top = ""
    choisi = "11:00"
    if mode == "conflit":
        top = bandeau("attention", "Ce créneau vient d'être réservé", "10:30 a été réservé juste avant votre confirmation. Votre rendez-vous n'a pas été créé. Le patient et le motif sont conservés ; choisissez un autre créneau.", role="alert")
        choisi = None
    if mode == "erreur":
        top = resume_erreurs([("rv-c", "Créneau : choisissez un horaire")])
        choisi = None
    pat = (champ("Patient", "BERNARD Louis · 09/07/1991", "rv-p", required=True) if mode != "recherche" else
           champ("Patient", "Ber", "rv-p", required=True, focus=True) +
           f'<ul class="etaye-lignes" role="listbox" aria-label="Patients trouvés"><li><span class="etaye-lignes__heure">{ic("compte", 18, "etaye-2")}</span><div><div class="etaye-lignes__titre">BERNARD Louis</div><div class="etaye-lignes__detail">Naissance 09/07/1991</div></div><div class="etaye-lignes__actions">{btn("Choisir", "secondaire")}</div></li></ul>'
           f'<p class="etaye-3" style="font-size:13px;margin:0">Patient absent ? <a href="{href("I-04", "planificateur")}">Enregistrer un nouveau patient</a> (droit séparé requis).</p>')
    f = (f'{top}{pat}{champ("Praticien", "Dr Nora Lefebvre", "rv-pr", kind="select", options=["Dr Nora Lefebvre", "Dr Sami Rahmani"], required=True)}'
         f'{creneaux(("10:30",), choisi)}{champ("Motif du rendez-vous", "Consultation de suivi" if mode == "conflit" else "", "rv-m", optional=True, help_="Visible par l\'équipe soignante et par le patient dans son espace.")}'
         '<p class="etaye-3" style="font-size:13px;margin:0">Aucune consultation n\'est créée par la prise de rendez-vous.</p>')
    return dialogue(fond, "Prendre rendez-vous", f'<div class="etaye-form">{f}</div>', btn("Confirmer le rendez-vous", "principal") + btn("Annuler", "discret"), large=True)


def i11(mode="ouvert"):
    fond = s16()
    top = ""
    if mode == "conflit":
        top = bandeau("attention", "Le rendez-vous a changé pendant votre saisie", "Une autre modification a été enregistrée. Le rendez-vous d'origine est conservé ; rien n'a été déplacé.", btn("Recharger", "secondaire", "relancer"), role="alert")
    f = (f'{top}<dl class="etaye-def" style="grid-template-columns:120px minmax(0,1fr)"><dt>Actuel</dt><dd class="etaye-num">03/11/2026 10:30–11:00 · Dr Nora Lefebvre</dd><dt>Patient</dt><dd>MARTIN Claire</dd></dl>'
         f'{creneaux(("10:30",), "11:00")}{champ("Raison du déplacement", "", "mv-r", optional=True, help_="Conservée dans l\'historique du rendez-vous.")}'
         '<p class="etaye-3" style="font-size:13px;margin:0">La patiente sera prévenue après l\'enregistrement (M-04). Le rendez-vous garde son identifiant APT-A-001.</p>')
    return dialogue(fond, "Déplacer le rendez-vous", f'<div class="etaye-form">{f}</div>', btn("Confirmer le déplacement", "principal") + btn("Garder ce rendez-vous", "secondaire"))


def i12(mode="ouvert"):
    fond = s16()
    err = mode == "erreur"
    f = (f'{resume_erreurs([("an-r", "Motif d\'annulation : obligatoire")]) if err else ""}'
         f'<p style="margin:0">MARTIN Claire · mardi 3 novembre 2026 · 10:30–11:00 · Dr Nora Lefebvre</p>'
         f'{champ("Motif d\'annulation", "", "an-r", kind="textarea", required=True, error="indiquez pourquoi le rendez-vous est annulé." if err else None)}'
         '<p class="etaye-2" style="margin:0;font-size:14px">L\'annulation est définitive : le créneau sera de nouveau proposé, et le rendez-vous annulé reste dans l\'historique. La patiente sera prévenue.</p>')
    pied = btn("Conserver le rendez-vous", "secondaire", attrs='autofocus') + btn("Annuler le rendez-vous", "discret", "interdit")
    return dialogue(fond, "Annuler ce rendez-vous", f'<div class="etaye-form">{f}</div>', pied)


def i13(mode="ouvert"):
    a = A("APT-A-002")
    if mode == "enregistre":
        return s16("APT-A-002").replace("Modifier la présence", "Modifier la présence", 1).replace('<main class="etaye-main" id="contenu">', '<main class="etaye-main" id="contenu">' + toast("Présence enregistrée · version 2 · aucune consultation ni compte rendu créé"), 1)
    form = (f'<section class="etaye-panneau" aria-labelledby="h-pres" style="border-color:var(--accent)"><div class="etaye-panneau__tete"><h2 id="h-pres">Présence au rendez-vous</h2>{enregistrement("sale")}</div>'
            f'<div class="etaye-panneau__corps etaye-form">'
            f'{choix("pres", [("Arrivée enregistrée", "Le patient est au cabinet"), ("Non honoré", "Possible à partir de l’heure de début, 14:00")], legend="État", checked=(0,))}'
            f'{champ("Heure d\'arrivée", "", "pres-h", num=True, optional=True, placeholder="HH:MM", focus=True)}'
            f'<div class="etaye-ligne">{btn("Enregistrer la présence", "principal")}{btn("Annuler", "discret")}</div></div></section>')
    page = s16("APT-A-002")
    # replace the attendance panel with the inline form (BOOKED moment, version 1)
    start = page.find('<section class="etaye-panneau"><div class="etaye-panneau__tete"><h2>Présence</h2>')
    end = page.find("</section>", start) + len("</section>")
    page = page[:start] + form + page[end:]
    return page.replace(st("appointment", "CHECKED_IN"), st("appointment", "BOOKED"), 1).replace("2 · APT-A-002", "1 · APT-A-002")


SCREENS = [
    {"id": "S-14", "name": "Agenda du cabinet", "states": [
        {"slug": "jour", "label": "Day — Tue 3 Nov: Nora's availability 09:00–12:00 hatched, Claire 10:30 booked; mobile agenda list", "html": s14()},
        {"slug": "lundi", "label": "Day — Mon 2 Nov: Louis 14:00 checked in; consultation distinct from appointment", "html": s14("lundi")},
        {"slug": "vide", "label": "Empty day — Wed 4 Nov", "html": s14("vide")},
        {"slug": "conflit", "label": "Concurrent booking lost — SC-BOOKING-RACE, entered reason kept (AC-S14-F)", "html": s14("conflit")},
        {"slug": "annule", "label": "Cancellation — APT-A-004 on 26 Oct, visible and greyed", "html": s14("annule")},
        {"slug": "non-honore", "label": "No-show — APT-A-005 on 15 Oct", "html": s14("non-honore")},
        {"slug": "changement-heure", "label": "DST — week of 25 Oct, local Paris time kept", "html": s14("changement-heure")},
        {"slug": "erreur", "label": "Service failure — nothing changed, retry", "html": s14("erreur")},
        {"slug": "planificateur", "label": "Administrative agenda — Mila Dubois, names and times only (PD-001 proposal)", "html": s14("planificateur")},
    ]},
    {"id": "S-15", "name": "Disponibilités et réglages d'agenda", "states": [
        {"slug": "semaine", "label": "Dated block AVL-A-001 vs (empty) weekly template, distinct", "html": s15()},
        {"slug": "vide", "label": "No availability — Dr Sami Rahmani", "html": s15("vide")},
    ]},
    {"id": "I-24", "name": "Modifier les disponibilités", "states": [
        {"slug": "ouvert", "label": "Drawer — edit AVL-A-001, timezone stated", "html": i24()},
        {"slug": "impact", "label": "Impact preview — shortening to 10:30 leaves Claire's booking outside; never cancelled silently (AC-S15-F)", "html": i24("impact")},
        {"slug": "erreur", "label": "Invalid end time", "html": i24("erreur")},
        {"slug": "conflit", "label": "Concurrent edit (template)", "html": i24("conflit")},
    ]},
    {"id": "S-16", "name": "Détail du rendez-vous", "states": [
        {"slug": "claire", "label": "BOOKED — APT-A-001 Claire, 03/11 10:30–11:00, preparation received", "html": s16()},
        {"slug": "louis", "label": "CHECKED_IN — APT-A-002 Louis; CONS-A-002 in progress shown separately (AC-S16-F)", "html": s16("APT-A-002")},
        {"slug": "termine", "label": "COMPLETED — APT-A-003 with CONS-A-001 completed 10:35", "html": s16("APT-A-003")},
        {"slug": "annule", "label": "CANCELLED — APT-A-004 with reason", "html": s16("APT-A-004")},
        {"slug": "non-honore", "label": "NO_SHOW — APT-A-005", "html": s16("APT-A-005")},
        {"slug": "modifie", "label": "Edited elsewhere — check-in recorded by Sami while Nora viewed version 1", "html": s16("APT-A-002", "modifie")},
    ]},
    {"id": "I-10", "name": "Prendre rendez-vous", "states": [
        {"slug": "ouvert", "label": "Dialog — Louis Bernard, Nora, 11:00 chosen, 10:30 shown as taken", "html": i10()},
        {"slug": "recherche", "label": "Patient lookup — minimal identity, registration link requires separate right", "html": i10("recherche")},
        {"slug": "conflit", "label": "Lost race — slot taken, fields kept, refreshed slots", "html": i10("conflit")},
        {"slug": "erreur", "label": "No slot chosen", "html": i10("erreur")},
    ]},
    {"id": "I-11", "name": "Déplacer le rendez-vous", "states": [
        {"slug": "ouvert", "label": "Move APT-A-001 10:30 → 11:00, same identity", "html": i11()},
        {"slug": "conflit", "label": "Version changed — original kept", "html": i11("conflit")},
    ]},
    {"id": "I-12", "name": "Annuler le rendez-vous", "states": [
        {"slug": "ouvert", "label": "Cancel dialog — safe choice first and focused, destructive separated", "html": i12()},
        {"slug": "erreur", "label": "Reason required", "html": i12("erreur")},
    ]},
    {"id": "I-13", "name": "Enregistrer la présence", "states": [
        {"slug": "ouvert", "label": "Inline attendance on APT-A-002 (version 1, booked) — no-show only after start", "html": i13()},
        {"slug": "enregistre", "label": "Saved — checked in, no consultation or report created", "html": i13("enregistre")},
    ]},
]

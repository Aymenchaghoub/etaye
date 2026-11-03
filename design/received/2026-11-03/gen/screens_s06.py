"""S-06 — Practitioner home and daily preparation. Reference module: copy its structure.

States: default (Nora, Mon 2 Nov 16:00), loading, empty day (Sami in Cabinet du Canal), partial task failure,
stale publication before v2 (V-STALE snapshot, Mon 2 Nov 12:00), cancelled visit (Sami, Mon 26 Oct snapshot),
date boundary (Nora, Tue 3 Nov 00:05).
"""
from lib import *

claire, louis = "R-A-001", "R-A-002"
apt1, apt2 = by_id("appointments", "APT-A-001"), by_id("appointments", "APT-A-002")
brief = by_id("documentVersions", "BRIEF-A-001-v1")
prep = by_id("preparations", "PREP-A-001")
ai2 = by_id("aiTasks", "AI-A-002")
pub2 = by_id("publications", "PUB-A-002")


def ligne(heure, rid, detail, statut_, actions="", lien_=None, nom_=None):
    n = nom_ or f'<a href="{lien_ or href("S-09", "claire" if rid == claire else "louis")}">{nom(rid, "famille")}</a> <span class="etaye-2" style="font-weight:400">· {age(by_id("records", rid)["birthDate"])}{NB}ans</span>'
    return (f'<li><span class="etaye-lignes__heure">{heure}</span><div><div class="etaye-lignes__titre">{n}</div>'
            f'<div class="etaye-lignes__detail">{detail}</div></div><div class="etaye-lignes__actions">{statut_}{actions}</div></li>')


def louis_row():
    return ligne(h_pro(apt2["startsAt"]), louis, f'{esc(apt2["reason"])} · Dr Sami Rahmani · consultation en cours depuis {h_pro("2026-11-02T14:10:00+01:00")}', st("appointment", "CHECKED_IN"))


def claire_row(brief_txt="brief en brouillon", prep_txt=None):
    p = prep_txt or f'préparation reçue le {d_court(prep["submittedAt"])} à {h_pro(prep["submittedAt"])}'
    return ligne(h_pro(apt1["startsAt"]), claire, f'{esc(apt1["reason"])} · {p} · {brief_txt}', st("appointment", "BOOKED"),
                 btn("Ouvrir la préparation", "secondaire", href_=href("S-18", "brouillon")))


def section(hid, titre, rows, note="", choix_=True):
    head = (f'<div class="etaye-ligne" style="justify-content:space-between"><h2 class="ui-section" id="{hid}">{titre}</h2>'
            f'{choix("portee-" + hid, ["Mes rendez-vous", "Tout le cabinet"], checked=(1,)).replace("etaye-choix", "etaye-choix etaye-ligne")}</div>') if choix_ else f'<h2 class="ui-section" id="{hid}">{titre}</h2>'
    body = f'<ul class="etaye-lignes">{"".join(rows)}</ul>' if rows else ""
    n = f'<p class="etaye-3" style="font-size:13px">{note}</p>' if note else ""
    return f'<section class="etaye-pile" aria-labelledby="{hid}">{head}{body}{n}</section>'


def arelire_brief():
    return (f'<div class="etaye-assertion"><div class="etaye-assertion__texte"><a href="{href("S-18", "brouillon")}" style="color:var(--encre)">Brief de préparation · Claire Martin</a></div>'
            f'<div>{st("business", "DRAFT")}</div><div class="etaye-assertion__prov"><span>Généré le <span class="etaye-num">{dh_pro(brief["generatedAt"])}</span></span>'
            f'<span>{etat("MISSING", "1 information manquante")}</span><span>{etat("CONTRADICTORY", "1 contradiction")}</span></div></div>')


def taches_louis():
    return (f'<div class="etaye-tache">{ic("alerte", 18, "etaye-2")}<div><div class="etaye-tache__titre">Brief de préparation · Louis Bernard</div>'
            f'<div class="etaye-tache__detail">Le service n\'a pas répondu à temps ({h_pro(ai2["failedAt"])}). Aucune synthèse n\'a été produite ; le dossier reste consultable.</div></div>'
            f'<div class="etaye-ligne">{st("processing", "FAILED")}</div></div>'
            f'<div class="etaye-ligne">{btn("Relancer la génération", "secondaire", "relancer", href_=href("S-40", "echec"))}{lien("Voir les tâches", href("S-40", "liste"))}</div>')


def publie_v2():
    return (f'<div class="etaye-assertion"><div class="etaye-assertion__texte">Explication destinée à Claire Martin, version 2</div><div>{st("publication", "PUBLISHED")}</div>'
            f'<div class="etaye-assertion__prov"><span>Publiée le <span class="etaye-num">{dh_pro(pub2["publishedAt"])}</span> par Dr Nora Lefebvre · remplace la version 1</span></div></div>')


def page_(titre, meta, left, side, actor="nora", counts=None):
    acts = btn("Rechercher un patient", "secondaire", "recherche", href_=href("S-07")) + btn("Ouvrir l'agenda", "principal", "agenda", href_=href("S-14"))
    body = (f'<div class="etaye-page">{entete(titre, meta, acts)}'
            f'<div class="etaye-cols etaye-cols--2"><div class="etaye-pile etaye-pile--6">{left}</div><div class="etaye-pile">{side}</div></div></div>')
    return pro_shell("Aujourd'hui", body, actor, counts=counts)


def body_default(tache_fail=False):
    left = (section("h-auj", "Aujourd'hui · lundi 2 novembre", [louis_row()], "Aucun autre rendez-vous aujourd'hui au Cabinet des Tilleuls. Vous n'avez pas de rendez-vous aujourd'hui.")
            + section("h-dem", "Demain · mardi 3 novembre", [claire_row()], "Vos disponibilités : 09:00 à 12:00 · 5 créneaux libres de 30 minutes", choix_=False))
    t = (bandeau("attention", "Les traitements n'ont pas pu être chargés", "Le reste de la page est à jour. Aucun traitement n'a été annulé.", btn("Réessayer", "secondaire", "relancer"), role="alert")
         if tache_fail else taches_louis())
    side = panneau("À relire", arelire_brief()) + panneau("Tâches", t) + panneau("Publié aujourd'hui", publie_v2())
    return page_("Lundi 2 novembre 2026", "Cabinet des Tilleuls · fuseau Europe/Paris", left, side)


def body_chargement():
    left = f'<section class="etaye-pile"><h2 class="ui-section">Aujourd\'hui · lundi 2 novembre</h2>{squelette(3)}</section><section class="etaye-pile"><h2 class="ui-section">Demain · mardi 3 novembre</h2>{squelette(2)}</section>'
    side = panneau("À relire", squelette(2)) + panneau("Tâches", squelette(2)) + panneau("Publié aujourd'hui", squelette(1))
    return page_("Lundi 2 novembre 2026", "Chargement des sections…", left, side, counts={})


def body_vide_canal():
    apt = by_id("appointments", "APT-B-001")
    hugo = ligne(h_pro(apt["startsAt"]), "R-B-001", "Dr Élodie Morel", st("appointment", "BOOKED"), lien_="#")
    left = (section("h-auj", "Aujourd'hui · lundi 2 novembre", [], "")
            + vide("Aucun rendez-vous aujourd'hui", "Ni pour vous ni pour le Cabinet du Canal.", btn("Ouvrir l'agenda", "secondaire", "agenda", href_=href("S-14")))
            + section("h-dem", "Demain · mardi 3 novembre", [hugo], "Vous n'avez pas de disponibilité enregistrée au Cabinet du Canal.", choix_=False))
    side = (panneau("À relire", vide("Rien à relire", "Aucun brouillon ne vous attend dans ce cabinet."))
            + panneau("Tâches", vide("Aucune tâche en cours", "")) + panneau("Publication", '<p class="etaye-2" style="margin:0;font-size:14px">La publication pour les patients n\'est pas autorisée pour vous dans ce cabinet.</p>'))
    return page_("Lundi 2 novembre 2026", "Cabinet du Canal · fuseau Europe/Paris", left, side, actor="sami_b", counts={})


def body_perime():
    """V-STALE snapshot: Mon 2 Nov 12:00 — preparation received 09:10, explanation v1 still published, v2 not yet validated."""
    v1 = by_id("documentVersions", "PATIENT-A-001-v1")
    left = (section("h-auj", "Aujourd'hui · lundi 2 novembre", [ligne(h_pro(apt2["startsAt"]), louis, f'{esc(apt2["reason"])} · Dr Sami Rahmani', st("appointment", "BOOKED"))], "")
            + section("h-dem", "Demain · mardi 3 novembre", [claire_row("brief non généré")], "", choix_=False))
    stale = (f'<div class="etaye-pile" style="gap:var(--espace-2)"><div style="font-weight:500">Explication destinée à Claire Martin, version 1</div>'
             f'<div class="etaye-ligne" style="flex-wrap:wrap">{st("publication", "PUBLISHED")}{st("freshness", "STALE")}</div>'
             f'<div class="etaye-assertion__prov" style="display:grid;gap:2px"><span>Publiée le 20/10/2026 10:50 · sources modifiées le {dh_pro(v1["staleSince"])} : nouvelle déclaration de la patiente</span>'
             f'<span>La patiente voit toujours la version 1 tant qu\'aucune nouvelle version n\'est publiée.</span></div></div>'
             f'<div class="etaye-ligne">{btn("Préparer une nouvelle version", "secondaire", href_=href("S-24", "perime"))}</div>')
    side = panneau("À relire", stale) + panneau("Tâches", vide("Aucune tâche en cours", "")) + panneau("Publié aujourd'hui", vide("Rien de publié aujourd'hui", ""))
    return page_("Lundi 2 novembre 2026", "Instantané V-STALE à 12:00 · avant la version 2", left, side, counts={"Briefs": 0})


def body_annule():
    """Historical snapshot for Sami Rahmani, Mon 26 Oct 2026: cancelled visit stays visible."""
    a4 = by_id("appointments", "APT-A-004")
    row = ligne(h_pro(a4["startsAt"]), louis, f'Dr Sami Rahmani · motif d\'annulation : {esc(a4["cancellationReason"])}', st("appointment", "CANCELLED"),
                lien("Voir le rendez-vous", href("S-16", "annule")))
    left = (section("h-auj", "Aujourd'hui · lundi 26 octobre", [row], "Le créneau libéré reste visible ici ; l'annulation est conservée dans l'historique du rendez-vous.")
            + section("h-dem", "Demain · mardi 27 octobre", [], "Aucun rendez-vous.", choix_=False))
    side = panneau("À relire", vide("Rien à relire", "")) + panneau("Tâches", vide("Aucune tâche en cours", ""))
    return page_("Lundi 26 octobre 2026", "Instantané historique · Cabinet des Tilleuls · heure d'hiver depuis le 25/10", left, side, actor="sami_a", counts={})


def body_minuit():
    left = (bandeau("info", "Nouvelle journée", "Il est 00:05 à Paris : la liste est passée au mardi 3 novembre. Les horaires restent dans le fuseau Europe/Paris du cabinet.")
            + section("h-auj", "Aujourd'hui · mardi 3 novembre", [claire_row()], "Vos disponibilités : 09:00 à 12:00 · 5 créneaux libres de 30 minutes")
            + section("h-dem", "Demain · mercredi 4 novembre", [], "Aucun rendez-vous.", choix_=False))
    side = panneau("À relire", arelire_brief()) + panneau("Tâches", taches_louis())
    return page_("Mardi 3 novembre 2026", "Cabinet des Tilleuls · 00:05 · fuseau Europe/Paris", left, side)


SCREENS = [{"id": "S-06", "name": "Accueil praticien et préparation du jour", "states": [
    {"slug": "nora", "label": "Default — Dr Nora Lefebvre, Monday 2 Nov 2026 16:00, practice scope", "html": body_default()},
    {"slug": "chargement", "label": "Loading sections — skeletons per section", "html": body_chargement()},
    {"slug": "jour-vide", "label": "Empty day — Dr Sami Rahmani in Cabinet du Canal (no appointment on 2 Nov; Hugo Petit tomorrow with Dr Élodie Morel)", "html": body_vide_canal()},
    {"slug": "echec-partiel", "label": "Partial failure — task section failed, agenda and reviews kept", "html": body_default(True)},
    {"slug": "perime", "label": "Stale publication — V-STALE snapshot at 12:00, explanation v1 published but sources changed at 09:10", "html": body_perime()},
    {"slug": "annule", "label": "Cancelled visit — historical snapshot Mon 26 Oct, APT-A-004 stays visible with reason (AC-S06-F)", "html": body_annule()},
    {"slug": "minuit", "label": "Date boundary — Tue 3 Nov 00:05 Europe/Paris, lists roll over", "html": body_minuit()},
]}]

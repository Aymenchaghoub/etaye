"""Étaye design generator — shared shells, components and French formatting.

Every screen module imports from here. Product copy is French; code/comments are English.
Data comes ONLY from synthetic-data.json (dataset etaye-design-v1). Never invent patients,
clinical values or claims. Use DATA[...] lookups or the helpers below.
"""
import html as _html, json, os
from datetime import datetime

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = json.load(open(os.path.join(HERE, "synthetic-data.json")))
BASELINE = "2026-11-02T16:00:00+01:00"


def by_id(collection, _id):
    for x in DATA[collection]:
        if x["id"] == _id:
            return x
    raise KeyError(f"{collection}:{_id}")


# ── French formatting ────────────────────────────────────────────────────────
MOIS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
JOURS = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]
NB = " "   # non-breaking space (value/unit, “10 h 30”)
NNB = " "  # narrow no-break space (before ; : ! ? in French)


def esc(s):
    return _html.escape(str(s), quote=True)


def _dt(iso):
    return datetime.fromisoformat(iso)


def d_long(iso, jour=False, annee=True):
    """'3 novembre 2026' (optionally 'mardi 3 novembre 2026'). Keeps the stored offset."""
    d = _dt(iso) if "T" in iso else datetime.fromisoformat(iso + "T00:00:00")
    s = f"{d.day}{'er' if d.day == 1 else ''} {MOIS[d.month - 1]}" + (f" {d.year}" if annee else "")
    return (JOURS[d.weekday()] + " " + s) if jour else s


def d_court(iso):
    d = _dt(iso) if "T" in iso else datetime.fromisoformat(iso + "T00:00:00")
    return f"{d.day:02d}/{d.month:02d}/{d.year}"


def h_pro(iso):
    d = _dt(iso)
    return f"{d.hour:02d}:{d.minute:02d}"


def h_patient(iso):
    d = _dt(iso)
    return f"{d.hour}{NB}h{NB}{d.minute:02d}" if d.minute else f"{d.hour}{NB}h"


def dh_pro(iso):
    return f"{d_court(iso)} {h_pro(iso)}"


def dh_patient(iso, jour=True):
    return f"{d_long(iso, jour=jour)} à {h_patient(iso)}"


def num(v):
    if isinstance(v, float):
        return f"{v:.1f}".replace(".", ",")
    return str(v)


def val(v, u):
    return f"{num(v)}{NB}{u}"


def age(birth, at=BASELINE):
    b, a = datetime.fromisoformat(birth), _dt(at)
    return a.year - b.year - ((a.month, a.day) < (b.month, b.day))


def nom(record_id, ordre="prenom"):
    r = by_id("records", record_id)
    g, f = r["name"]["given"], r["name"]["family"]
    return f"{g} {f}" if ordre == "prenom" else f"{f.upper()} {g}"


# ── Paths (prototype pages live in proto/<ID>/<slug>.html) ───────────────────
ROOT = "../.."
LOGO = {
    "clair": f"{ROOT}/logos/etaye-logo.svg", "sombre": f"{ROOT}/logos/etaye-logo-sombre.svg",
    "mono": f"{ROOT}/logos/etaye-logo-monochrome.svg", "patient": f"{ROOT}/logos/etaye-logo-espace-patient.svg",
    "signe": f"{ROOT}/logos/etaye-signe.svg",
}


# Canonical main state slug of every family — every module MUST create a state with this slug,
# so cross-links between groups resolve. Add further states with any other slug.
MAIN = {"S-01": "accueil", "S-02": "connexion", "S-03": "equipe", "S-04": "demande", "S-05": "choix", "S-06": "nora",
        "S-07": "liste", "S-08": "nouveau", "S-09": "claire", "S-10": "claire", "S-11": "claire", "S-12": "claire",
        "S-13": "claire", "S-14": "jour", "S-15": "semaine", "S-16": "claire", "S-17": "claire", "S-18": "brouillon",
        "S-19": "en-cours", "S-20": "brouillon", "S-21": "revue", "S-22": "comparaison", "S-23": "validation",
        "S-24": "perime", "S-25": "accueil", "S-26": "liste", "S-27": "brouillon", "S-28": "liste", "S-29": "v2",
        "S-30": "visionneuse", "S-31": "equipe", "S-32": "membre", "S-33": "cabinet", "S-34": "admin", "S-35": "pro",
        "S-36": "interdit", "S-37": "indisponible", "S-38": "expiree", "S-39": "options", "S-40": "liste"}
MAIN.update({f"I-{i:02d}": "ouvert" for i in range(1, 29)})
MAIN.update({"P-01": "court", "P-02": "court", "P-03": "court", "M-01": "html", "M-02": "html", "M-03": "html", "M-04": "html", "M-05": "html"})
# Extra slugs other groups may link to (must exist): S-09 louis, S-35 admin, S-35 patient, S-40 echec, S-09 hugo-refuse? no.
REQUIRED_EXTRA = {"S-09": ["louis"], "S-35": ["admin", "patient"], "S-40": ["echec"]}


def href(screen_id, slug=None):
    return f"../{screen_id}/{slug or MAIN[screen_id]}.html"


def page(title, body, theme="clair", lang="fr"):
    return f"""<!doctype html>
<html lang="{lang}" data-theme="{theme}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(title)} · Étaye</title>
<link rel="stylesheet" href="{ROOT}/tokens/tokens.css">
<link rel="stylesheet" href="{ROOT}/css/etaye.css">
<script>try{{var t=new URLSearchParams(location.search).get('theme');if(t)document.documentElement.setAttribute('data-theme',t);}}catch(e){{}}</script>
<style>body{{margin:0;background:var(--fond);}}</style>
</head>
<body class="etaye-root">
{body}
</body>
</html>
"""


# ── Atoms ────────────────────────────────────────────────────────────────────
def ic(name, size=None, cls=""):
    s = f" etaye-icone--{size}" if size else ""
    return f'<span class="etaye-icone{s} etaye-icone--{name} {cls}" aria-hidden="true"></span>'


def logo(kind="clair", h=22, alt="Étaye"):
    """Light + dark images swapped by CSS on [data-theme] (no script needed)."""
    dark = {"clair": LOGO["sombre"], "patient": f"{ROOT}/logos/etaye-logo-espace-patient-sombre.svg", "mono": LOGO["sombre"]}.get(kind)
    a = f'<img class="etaye-logo-c" src="{LOGO[kind]}" alt="{esc(alt)}" style="height:{h}px">'
    b = f'<img class="etaye-logo-s" src="{dark}" alt="{esc(alt)}" style="height:{h}px">' if dark else ""
    return a + b


def btn(label, kind="secondaire", icon=None, key=None, disabled=False, href_=None, patient=False, full=False, attrs=""):
    """kind: principal | valider | secondaire | discret. Labels say exactly what happens (no 'OK')."""
    cls = f"etaye-bouton etaye-bouton--{kind}" + (" etaye-bouton--patient" if patient else "")
    st = ' style="width:100%"' if full else ""
    inner = (ic(icon) if icon else "") + esc(label) + (f' <span class="etaye-touche">{esc(key)}</span>' if key else "")
    if href_ and not disabled:
        return f'<a class="{cls}" href="{href_}"{st} {attrs}>{inner}</a>'
    return f'<button class="{cls}" type="button"{" disabled" if disabled else ""}{st} {attrs}>{inner}</button>'


def lien(label, href_="#", icon=None):
    return f'<a class="etaye-bouton etaye-bouton--discret" href="{href_}">{ic(icon) if icon else ""}{esc(label)}</a>'


def statut(variant, label, icon=None, big=False):
    """variant: critique | attention | valide | neutre | publie. Always a word, never colour only."""
    st = ' style="font-size:15px;height:28px"' if big else ""
    return f'<span class="etaye-statut etaye-statut--{variant}"{st}>{ic(icon) if icon else ""}{esc(label)}</span>'


# Distinct axes (business-rules.md canonical vocabulary) — never merge them into one pill.
BUSINESS = {"DRAFT": ("neutre", "Brouillon", "brouillon"), "IN_REVIEW": ("attention", "En relecture", "liste"), "VALIDATED": ("valide", "Validé", "valide")}
PROCESSING = {"QUEUED": ("neutre", "En file d'attente", "horloge"), "RUNNING": ("neutre", "Tâche en cours", "relancer"),
              "SUCCEEDED": ("neutre", "Tâche terminée", "valide"), "FAILED": ("attention", "Tâche en échec", "alerte"),
              "CANCELLED": ("neutre", "Tâche annulée", "interdit")}
FRESHNESS = {"CURRENT": ("neutre", "Sources à jour", None), "STALE": ("attention", "Sources modifiées depuis cette version", "alerte")}
PUBLICATION = {None: ("neutre", "Interne · non publié", "verrou"), "PUBLISHED": ("publie", "Publié", "publier"),
               "REPLACED": ("neutre", "Remplacé", "remplacer"), "WITHDRAWN": ("neutre", "Retiré", "retirer")}
APPOINTMENT = {"BOOKED": ("neutre", "Réservé", "agenda"), "CHECKED_IN": ("valide", "Arrivée enregistrée", "valide"),
               "COMPLETED": ("neutre", "Terminé", "valide"), "CANCELLED": ("neutre", "Annulé", "interdit"),
               "NO_SHOW": ("attention", "Non honoré", "alerte")}
INVITATION = {"PENDING": ("attention", "En attente", "horloge"), "ACCEPTED": ("valide", "Acceptée", "valide"),
              "EXPIRED": ("neutre", "Expirée", "horloge"), "REVOKED": ("neutre", "Révoquée", "interdit")}
MEMBERSHIP = {"ACTIVE": ("valide", "Actif", "valide"), "SUSPENDED": ("neutre", "Suspendu", "interdit")}
AXES = {"business": BUSINESS, "processing": PROCESSING, "freshness": FRESHNESS, "publication": PUBLICATION,
        "appointment": APPOINTMENT, "invitation": INVITATION, "membership": MEMBERSHIP}


def st(axis, value, label=None, big=False):
    v, l, i = AXES[axis][value]
    return statut(v, label or l, i, big)


# Information state (BR-008): PRESENT | EXPLICITLY_ABSENT | UNKNOWN | NOT_ASKED, plus display codes.
# Missing ≠ explicit negative ≠ patient-reported. An empty allergy section is UNKNOWN, never “no known allergies”.
ETAT = {"MISSING": ("manquant", "Non renseigné"), "UNKNOWN": ("manquant", "Inconnu · à préciser"), "NOT_ASKED": ("manquant", "Non demandé"),
        "EXPLICITLY_ABSENT": ("negatif", "Absence déclarée à cette date"),
        "PATIENT_REPORTED_UNCONFIRMED": ("declare", "Déclaré par la patiente · non confirmé"),
        "CONTRADICTORY": ("discordant", "Déclarations discordantes"), "PRACTITIONER_OBSERVED": ("observe", "Observé au cabinet"),
        "DOCUMENT_REPORTED": ("observe", "Rapporté par un document"), "CORRECTED": ("corrige", "Corrigé")}
ETAT["EXPLICIT_NEGATIVE"] = ETAT["EXPLICITLY_ABSENT"]  # legacy alias
SOURCE_TYPE_FR = {"PATIENT_REPORTED": "Déclaration de la patiente", "PRACTITIONER_OBSERVED": "Observation du praticien",
                  "DOCUMENT_REPORTED": "Rapporté par un document"}
CAP_FR = {"CLINICAL_READ": "Lire les dossiers", "CLINICAL_WRITE": "Modifier les dossiers", "CLINICAL_VALIDATE": "Valider les documents",
          "PATIENT_PUBLISH": "Publier pour les patients", "SCHEDULE_MANAGE": "Gérer l'agenda", "CLINICAL_AUDIT": "Consulter l'audit clinique",
          "PRACTICE_SETTINGS": "Réglages du cabinet", "TEAM_MANAGE": "Gérer l'équipe", "ADMIN_AUDIT": "Consulter l'audit administratif",
          "PATIENT_REGISTER": "Enregistrer des patients (données administratives)"}


def etat(code, label=None):
    k, l = ETAT[code]
    return f'<span class="etaye-etat etaye-etat--{k}">{esc(label or l)}</span>'


def etat_source(src):
    """Derive the display state of a dataset source (sourceType + informationState + verificationState)."""
    ist, typ, ver = src.get("informationState"), src.get("sourceType"), src.get("verificationState")
    if ist == "EXPLICITLY_ABSENT":
        return etat("EXPLICITLY_ABSENT", f"Absence déclarée le {d_court(src['occurredAt'])}")
    if typ == "PATIENT_REPORTED" and ver == "UNCONFIRMED":
        return etat("PATIENT_REPORTED_UNCONFIRMED")
    if src.get("supersedesId"):
        return etat("CORRECTED", f"Version {src['version']} · corrige la version précédente")
    if typ in ("PRACTITIONER_OBSERVED", "DOCUMENT_REPORTED"):
        return etat(typ, SOURCE_TYPE_FR[typ])
    return ""


def repere(n, seul=False, label=None):
    """Signature source marker. Must point at a source that supports the exact assertion."""
    c = "etaye-repere" + (" etaye-repere--seul" if seul else "")
    return f'<button class="{c}" type="button" aria-label="{esc(label or f"Source {n}")}">{esc(n)}</button>'


def demo_notice():
    return '<span class="etaye-demo">Démonstration — personnes et données fictives</span>'


# ── Forms ────────────────────────────────────────────────────────────────────
def champ(label, value="", fid=None, help_=None, error=None, required=False, optional=False, kind="input",
          readonly=False, disabled=False, num=False, placeholder=None, options=None, rows=3, focus=False, typ="text"):
    fid = fid or ("c-" + str(abs(hash(label)) % 10 ** 6))
    lab = esc(label) + (' <span class="etaye-req" aria-hidden="true">*</span>' if required else "") + (' <span class="etaye-opt">(facultatif)</span>' if optional else "")
    cls = "etaye-champ__saisie" + (" etaye-num" if num else "") + (" etaye-champ__saisie--focus" if focus else "")
    a = (" readonly" if readonly else "") + (" disabled" if disabled else "") + (' aria-invalid="true"' if error else "") + (f' aria-describedby="{fid}-a"' if (help_ or error) else "")
    ph = f' placeholder="{esc(placeholder)}"' if placeholder else ""
    if kind == "textarea":
        ctl = f'<textarea class="{cls}" id="{fid}" rows="{rows}"{a}{ph}>{esc(value)}</textarea>'
    elif kind == "select":
        opts = "".join(f'<option{" selected" if o == value else ""}>{esc(o)}</option>' for o in (options or [value]))
        ctl = f'<select class="{cls}" id="{fid}"{a}>{opts}</select>'
    else:
        ctl = f'<input class="{cls}" id="{fid}" type="{typ}" value="{esc(value)}"{a}{ph}>'
    msg = ""
    if error:
        msg = f'<span class="etaye-champ__aide" id="{fid}-a">Erreur{NNB}: {esc(error)}</span>'
    elif help_:
        msg = f'<span class="etaye-champ__aide" id="{fid}-a">{esc(help_)}</span>'
    w = "etaye-champ" + (" etaye-champ--erreur" if error else "") + (" etaye-champ--lecture" if readonly else "")
    return f'<div class="{w}"><label class="etaye-champ__libelle" for="{fid}">{lab}</label>{ctl}{msg}</div>'


def choix(name, options, typ="radio", legend=None, checked=()):
    """options: list of (label, hint) or str."""
    items = []
    for i, o in enumerate(options):
        l, h = (o if isinstance(o, tuple) else (o, None))
        c = " checked" if (l in checked or i in checked) else ""
        items.append(f'<label><input type="{typ}" name="{name}"{c}><span>{esc(l)}{f"<small>{esc(h)}</small>" if h else ""}</span></label>')
    lg = f'<legend class="etaye-champ__libelle" style="margin-bottom:var(--espace-2)">{esc(legend)}</legend>' if legend else ""
    return f'<fieldset class="etaye-choix" style="border:0;padding:0;margin:0">{lg}{"".join(items)}</fieldset>'


def resume_erreurs(items):
    li = "".join(f'<li><a href="#{fid}">{esc(t)}</a></li>' for fid, t in items)
    return f'<div class="etaye-resume-erreurs" role="alert"><h2>{len(items)} champ{"s" if len(items) > 1 else ""} à corriger</h2><ul style="margin:0;padding-left:20px">{li}</ul></div>'


def enregistrement(etat_="ok", heure=None):
    if etat_ == "ok":
        return f'<span class="etaye-enregistrement">{ic("valide")}Enregistré à <span class="etaye-num">{heure or "14:05"}</span></span>'
    if etat_ == "sale":
        return '<span class="etaye-enregistrement etaye-enregistrement--sale">Modifications non enregistrées</span>'
    if etat_ == "encours":
        return '<span class="etaye-enregistrement">Enregistrement…</span>'
    return f'<span class="etaye-enregistrement etaye-enregistrement--echec">{ic("alerte")}Échec de l\'enregistrement · vos modifications sont conservées</span>'


# ── Feedback ─────────────────────────────────────────────────────────────────
def bandeau(kind, titre, texte="", actions="", icon=None, role="status"):
    """kind: info | attention | critique | neutre. critique is reserved for clinical/blocking."""
    icon = icon or {"info": "info", "attention": "alerte", "critique": "alerte", "neutre": "info"}[kind]
    t = f'<div class="etaye-bandeau__titre">{esc(titre)}</div>' if titre else ""
    x = f"<div>{texte}</div>" if texte else ""
    a = f'<div class="etaye-bandeau__actions">{actions}</div>' if actions else "<span></span>"
    return f'<div class="etaye-bandeau etaye-bandeau--{kind}" role="{role}">{ic(icon, 18)}<div>{t}{x}</div>{a}</div>'


def vide(titre, texte="", action=""):
    return f'<div class="etaye-vide"><strong>{esc(titre)}</strong>{f"<span>{texte}</span>" if texte else ""}{action}</div>'


def squelette(n=4):
    return '<div class="etaye-squelette" aria-busy="true" aria-label="Chargement">' + "<span></span>" * n + "</div>"


def toast(texte, action=""):
    return f'<div class="etaye-toast" role="status">{ic("valide")}<span>{texte}</span>{action}</div>'


# ── Data display ─────────────────────────────────────────────────────────────
def tableau(headers, rows, num=(), stack=False, caption=None, cls=""):
    """headers: list[str]; rows: list[list[str(html)]]; num: indexes of numeric columns (right-aligned, mono)."""
    th = "".join(f'<th scope="col"{" class=\"etaye-col-num\"" if i in num else ""}>{h}</th>' for i, h in enumerate(headers))
    body = []
    for r in rows:
        tds = "".join(f'<td{" class=\"etaye-col-num\"" if i in num else ""} data-l="{esc(_strip(headers[i]))}">{c}</td>' for i, c in enumerate(r))
        body.append(f"<tr>{tds}</tr>")
    cap = f'<caption class="ui-section" style="text-align:left;margin-bottom:var(--espace-2)">{caption}</caption>' if caption else ""
    return f'<table class="etaye-tableau{" etaye-tableau--empile" if stack else ""} {cls}">{cap}<thead><tr>{th}</tr></thead><tbody>{"".join(body)}</tbody></table>'


def _strip(s):
    import re
    return re.sub(r"<[^>]+>", "", s)


def pagination(debut, fin, total, page_=1, pages=1):
    p = "".join(f'<a href="#"{" aria-current=\"page\"" if i == page_ else ""}>{i}</a>' for i in range(1, pages + 1))
    return f'<div class="etaye-pagination"><span>Résultats <span class="etaye-num">{debut}–{fin}</span> sur <span class="etaye-num">{total}</span></span><nav class="etaye-pagination__pages" aria-label="Pagination">{p}</nav></div>'


def chrono(items):
    """items: dicts {date, heure?, type, titre, detail?, action? (bool)}"""
    li = []
    for it in items:
        cls = "etaye-chrono__item" + (" etaye-chrono__item--action" if it.get("action") else "")
        h = f'<br>{it["heure"]}' if it.get("heure") else ""
        det = f'<div class="etaye-chrono__detail">{it["detail"]}</div>' if it.get("detail") else ""
        li.append(f'<li class="{cls}"><div class="etaye-chrono__date">{it["date"]}{h}</div><div class="etaye-chrono__rail"></div>'
                  f'<div class="etaye-chrono__corps"><div class="etaye-chrono__type">{it["type"]}</div><div class="etaye-chrono__titre">{it["titre"]}</div>{det}</div></li>')
    return f'<ol class="etaye-chrono">{"".join(li)}</ol>'


def assertion(texte, prov, cote=""):
    return f'<div class="etaye-assertion"><div class="etaye-assertion__texte">{texte}</div><div>{cote}</div><div class="etaye-assertion__prov">{prov}</div></div>'


def panneau(titre, corps, actions="", tag="h2"):
    return f'<section class="etaye-panneau"><div class="etaye-panneau__tete"><{tag}>{titre}</{tag}><div class="etaye-ligne">{actions}</div></div><div class="etaye-panneau__corps">{corps}</div></section>'


def entete(titre, meta="", actions="", fil=""):
    f = f'<div class="etaye-entete__fil">{fil}</div>' if fil else ""
    m = f'<div class="etaye-entete__meta">{meta}</div>' if meta else ""
    a = f'<div class="etaye-entete__actions">{actions}</div>' if actions else ""
    return f'<div class="etaye-entete"><div>{f}<h1>{titre}</h1>{m}</div>{a}</div>'


def onglets(items, actif):
    """items: list of (label, href, count|None)"""
    out = []
    for l, h, c in items:
        cur = ' aria-current="page"' if l == actif else ""
        cnt = f'<span class="etaye-nav__compte">{c}</span>' if c is not None else ""
        out.append(f'<a href="{h}"{cur}>{esc(l)}{cnt}</a>')
    return f'<nav class="etaye-onglets" aria-label="Sections">{"".join(out)}</nav>'


# ── Identity banner (dataset only) ───────────────────────────────────────────
def banniere(record_id, allergies=True):
    r = by_id("records", record_id)
    f, g = r["name"]["family"], r["name"]["given"]
    born = d_court(r["birthDate"])
    portal = ""
    if r["portalAccountId"]:
        portal = st("invitation", "ACCEPTED", "Espace patient activé")
    else:
        inv = [i for i in DATA["invitations"] if i.get("recordId") == record_id]
        portal = st("invitation", inv[0]["status"], "Invitation à l'espace patient expirée") if inv else statut("neutre", "Sans espace patient")
    al = ""
    if allergies:
        h = [x for x in DATA["history"] if x["recordId"] == record_id and x["category"] == "ALLERGY"]
        if h and h[0].get("hasContradictorySources"):
            al = (f'<div class="etaye-allergies" role="note" style="background:var(--attention-fond)"><div class="etaye-allergies__titre" style="color:var(--attention)">{ic("alerte")}Allergies</div>'
                  f'<div class="etaye-allergies__liste" style="font-weight:400">Déclarations discordantes à préciser · ne pas conclure à l\'absence</div></div>')
        else:
            al = (f'<div class="etaye-allergies etaye-allergies--aucune" role="note"><div class="etaye-allergies__titre">Allergies</div>'
                  f'<div class="etaye-allergies__liste">Inconnues · non renseignées</div></div>')
    return (f'<header class="etaye-banniere" aria-label="Patient affiché"><div>'
            f'<div class="etaye-banniere__nom">{esc(f.upper())} {esc(g)}</div>'
            f'<div class="etaye-banniere__traits"><span>Naissance <span class="etaye-num">{born}</span> · <span class="etaye-num">{age(r["birthDate"])}</span>{NB}ans</span>'
            f'<span class="etaye-identifiant">Dossier <b>{esc(r["id"])}</b></span></div>'
            f'<div class="etaye-banniere__ident">{portal}</div></div>{al}</header>')


# ── Shells ───────────────────────────────────────────────────────────────────
ACTORS = {
    "nora": {"nom": "Dr Nora Lefebvre", "cabinet": "Cabinet des Tilleuls", "role": "Praticienne", "type": "pro"},
    "sami_a": {"nom": "Dr Sami Rahmani", "cabinet": "Cabinet des Tilleuls", "role": "Praticien", "type": "pro"},
    "sami_b": {"nom": "Dr Sami Rahmani", "cabinet": "Cabinet du Canal", "role": "Praticien", "type": "pro"},
    "elodie": {"nom": "Dr Élodie Morel", "cabinet": "Cabinet du Canal", "role": "Praticienne", "type": "pro"},
    "mila": {"nom": "Mila Dubois", "cabinet": "Cabinet des Tilleuls", "role": "Administration", "type": "admin"},
}

# Navigation targets: (label, icon, screen, slug, count)
NAV_PRO = [("Aujourd'hui", "aujourdhui", "S-06", "nora"), ("Patients", "patients", "S-07", "liste"),
           ("Agenda", "agenda", "S-14", "jour"), ("Tâches", "ia", "S-40", "liste")]
NAV_PRO_RELIRE = [("Briefs", "preparation", "S-18", "brouillon"), ("Comptes rendus", "document", "S-20", "brouillon")]
NAV_ADMIN = [("Équipe", "equipe", "S-31", "equipe"), ("Réglages du cabinet", "reglages", "S-33", "cabinet"),
             ("Audit administratif", "audit", "S-34", "admin")]


def _nav(items, actif, counts=None):
    counts = counts or {}
    li = []
    for label, icon, sid, slug in items:
        cur = ' aria-current="page"' if label == actif else ""
        c = f'<span class="etaye-nav__compte">{counts[label]}</span>' if label in counts else ""
        li.append(f'<li><a class="etaye-nav__rangee" href="{href(sid, slug)}"{cur}>{ic(icon, 18)}{esc(label)}{c}</a></li>')
    return f'<ul class="etaye-nav__liste">{"".join(li)}</ul>'


def pro_shell(actif, body, actor="nora", banner=None, counts=None, extra_top=""):
    """Practitioner or administration shell. actor: nora | sami_a | sami_b | elodie | mila.
    banner: html of etaye-banniere (record context) or None. Practice + role stay visible (AC-S05-V)."""
    a = ACTORS[actor]
    if a["type"] == "admin":
        nav = _nav(NAV_ADMIN, actif)
        nav2 = f'<div class="etaye-nav__section">Mon compte</div>{_nav([("Profil", "compte", "S-35", "admin")], actif)}'
    else:
        counts = counts if counts is not None else {"Briefs": 1}
        nav = _nav(NAV_PRO, actif, counts)
        nav2 = (f'<div class="etaye-nav__section">À relire</div>{_nav(NAV_PRO_RELIRE, actif, counts)}'
                f'<div class="etaye-nav__section">Mon compte</div>{_nav([("Disponibilités", "horloge", "S-15", "semaine"), ("Profil", "compte", "S-35", "pro")], actif)}')
    top = (f'<header class="etaye-top"><button class="etaye-fermer etaye-top__menu" type="button" aria-label="Ouvrir le menu">{ic("menu", 18)}</button>'
           f'<a class="etaye-top__logo" href="{href("S-06" if a["type"] == "pro" else "S-31", "nora" if a["type"] == "pro" else "equipe")}">{logo("clair", 22)}</a>'
           f'<a class="etaye-contexte" href="{href("S-05", "choix")}" aria-label="Contexte actif : {esc(a["cabinet"])}, {esc(a["role"])}. Changer de contexte">'
           f'{ic("cabinet", 18)}<span><span class="etaye-contexte__cabinet">{esc(a["cabinet"])}</span><span class="etaye-contexte__role">{esc(a["role"])} · {esc(a["nom"])}</span></span>{ic("deplier")}</a>'
           f'{extra_top}<div class="etaye-top__droite">{demo_notice()}<a class="etaye-compte" href="{href("S-35", "pro" if a["type"] == "pro" else "admin")}">{ic("compte", 18)}<span class="etaye-compte__nom">{esc(a["nom"])}</span></a></div></header>')
    navhtml = f'<nav class="etaye-nav" aria-label="Navigation principale">{nav}{nav2}</nav>'
    b = banner or ""
    return f'<div class="etaye-root etaye-app">{top}{navhtml}<main class="etaye-main" id="contenu">{b}{body}</main></div>'


PTABS = [("Accueil", "S-25", "accueil"), ("Rendez-vous", "S-26", "liste"), ("Préparer ma visite", "S-27", "brouillon"), ("Mes documents", "S-28", "liste")]


def patient_shell(actif, body, cabinet="Cabinet des Tilleuls"):
    tabs = "".join(f'<a href="{href(s, sl)}"{" aria-current=\"page\"" if l == actif else ""}>{esc(l)}</a>' for l, s, sl in PTABS)
    return (f'<div class="etaye-root etaye-papp etaye-patient"><header class="etaye-ptop"><a href="{href("S-25", "accueil")}">{logo("patient", 24, "Étaye — Espace patient")}</a>'
            f'<span class="etaye-ptop__cabinet">{esc(cabinet)}<br><a href="{href("S-35", "patient")}" style="color:var(--accent)">Claire Martin</a></span></header>'
            f'<nav class="etaye-ptabs" aria-label="Espace patient">{tabs}</nav><main class="etaye-pmain" id="contenu">{body}</main>'
            f'<footer class="etaye-ppied"><span>{esc(cabinet)} · contact par le secrétariat du cabinet</span>{demo_notice()}</footer></div>')


def auth_shell(body, large=False, patient=False):
    lg = logo("patient" if patient else "clair", 28, "Étaye — Espace patient" if patient else "Étaye")
    return (f'<div class="etaye-root etaye-auth{" etaye-patient" if patient else ""}"><div>{lg}</div><main class="etaye-auth__corps{" etaye-auth__corps--large" if large else ""}" id="contenu">{body}</main>'
            f'<footer class="etaye-auth__pied">{demo_notice()}<span class="etaye-3" style="font-size:12px">Projet de démonstration — aucun usage clinique</span></footer></div>')


# ── Overlays (one at a time; full page on mobile) ────────────────────────────
def dialogue(fond, titre, corps, pied, intro="", large=False, role="dialog", fermer=True):
    """fond: the full underlying screen html (rendered behind the veil, inert)."""
    i = f"<p>{intro}</p>" if intro else ""
    return (f'<div class="etaye-root etaye-scene"><div aria-hidden="true" inert>{fond}</div><div class="etaye-voile"></div>'
            f'<section class="etaye-dialogue{" etaye-dialogue--large" if large else ""}" role="{role}" aria-modal="true" aria-labelledby="dlg-t">'
            f'<div class="etaye-dialogue__tete"><div><h2 id="dlg-t">{titre}</h2>{i}</div>{f'<button class="etaye-fermer" type="button" aria-label="Fermer">{ic("fermer", 18)}</button>' if fermer else ""}</div>'
            f'<div class="etaye-dialogue__corps">{corps}</div><div class="etaye-dialogue__pied">{pied}</div></section></div>')


def tiroir(fond, titre, corps, pied="", intro="", large=False):
    i = f"<p>{intro}</p>" if intro else ""
    p = f'<div class="etaye-tiroir__pied">{pied}</div>' if pied else ""
    return (f'<div class="etaye-root etaye-scene"><div aria-hidden="true" inert>{fond}</div><div class="etaye-voile"></div>'
            f'<aside class="etaye-tiroir{" etaye-tiroir--large" if large else ""}" role="dialog" aria-modal="true" aria-labelledby="tir-t">'
            f'<div class="etaye-tiroir__tete"><div><h2 id="tir-t">{titre}</h2>{i}</div><button class="etaye-fermer" type="button" aria-label="Fermer">{ic("fermer", 18)}</button></div>'
            f'<div class="etaye-tiroir__corps">{corps}</div>{p}</aside></div>')


# ── Source helpers (dataset sources) ─────────────────────────────────────────
KIND_FR = {"CONSULTATION_NOTE": "Note de consultation", "MANUAL_LAB_PANEL": "Résultats saisis manuellement", "UPLOADED_DOCUMENT": "Document déposé",
           "PRACTITIONER_MEASUREMENT": "Mesure au cabinet", "PATIENT_PREPARATION": "Préparation de visite"}


def auteur(src):
    if src.get("authorId"):
        return by_id("practitioners", src["authorId"])["displayName"]
    if src.get("authorAccountId"):
        return by_id("accounts", src["authorAccountId"])["name"] + " (patiente)"
    return "Auteur non renseigné"


def source_ref(src_id, n=None):
    """One-line dated reference: '1 · Note de consultation · 14/09/2026 · Dr Nora Lefebvre'."""
    s = by_id("sources", src_id)
    r = repere(n, seul=True) if n is not None else ""
    return f'<span style="display:inline-flex;gap:6px;align-items:baseline;flex-wrap:wrap">{r}<span>{KIND_FR[s["kind"]]} · <span class="etaye-num">{d_court(s["occurredAt"])}</span> · {esc(auteur(s))}</span></span>'


def source_drawer_body(src_id, n, surligne=None):
    s = by_id("sources", src_id)
    txt = esc(s["text"])
    if surligne:
        txt = txt.replace(esc(surligne), f"<mark>{esc(surligne)}</mark>")
    rec = f' · enregistré le <span class="etaye-num">{dh_pro(s["recordedAt"])}</span>' if s["recordedAt"] != s["occurredAt"] else ""
    st_ = etat_source(s)
    corr = ""
    if s.get("supersedesId"):
        corr = (f'<div class="etaye-2" style="font-size:13px">Remplace {esc(s["supersedesId"])} · motif{NNB}: {esc(s.get("correctionReason", "non renseigné"))}</div>')
    return (f'<div class="etaye-source-tete"><div class="etaye-apercu__ref">{repere(n, seul=True)}{esc(src_id)} · version {s["version"]} · {esc(s["locator"])}</div>'
            f'<div class="ui-section">{KIND_FR[s["kind"]]}</div>'
            f'<div class="etaye-2" style="font-size:14px">Survenu le <span class="etaye-num">{dh_pro(s["occurredAt"])}</span>{rec} · {esc(auteur(s))}</div>'
            f'<div class="etaye-ligne">{st_}</div>{corr}</div>'
            f'<div class="etaye-source-texte">{txt}</div>')


# ── Trend chart with gaps (paired with a table, never alone) ─────────────────
def courbe_tension(w=None, h=220):
    """Wide and narrow renderings swapped by CSS so axis text stays legible on phones."""
    if w is None:
        return (_courbe_tension(640, 220).replace('class="etaye-courbe"', 'class="etaye-courbe etaye-courbe--large"', 1)
                + _courbe_tension(340, 200).replace('class="etaye-courbe"', 'class="etaye-courbe etaye-courbe--etroite"', 1))
    return _courbe_tension(w, h)


def _courbe_tension(w=640, h=220):
    pts = [m for m in DATA["measurements"] if m["recordId"] == "R-A-001" and m["code"] == "BLOOD_PRESSURE"]
    x0, x1 = 60, w - 24
    y = lambda v: 24 + (150 - v) * (h - 64) / (150 - 60)
    xs = [x0 + min(80, (x1 - x0) // 6), x1 - min(80, (x1 - x0) // 6)]
    grid = "".join(f'<line class="grille" x1="{x0}" x2="{x1}" y1="{y(v):.0f}" y2="{y(v):.0f}"/><text class="axe" x="{x0 - 8}" y="{y(v) + 4:.0f}" text-anchor="end">{v}</text>' for v in (60, 90, 120, 150))
    sys = f'<polyline class="serie" points="{xs[0]},{y(pts[0]["values"]["systolic"]):.0f} {xs[1]},{y(pts[1]["values"]["systolic"]):.0f}"/>'
    dia = f'<polyline class="serie-2" points="{xs[0]},{y(pts[0]["values"]["diastolic"]):.0f} {xs[1]},{y(pts[1]["values"]["diastolic"]):.0f}"/>'
    dots = ""
    for i, m in enumerate(pts):
        s, d = m["values"]["systolic"], m["values"]["diastolic"]
        dots += (f'<circle class="point" cx="{xs[i]}" cy="{y(s):.0f}" r="4"/><text class="val" x="{xs[i]}" y="{y(s) - 10:.0f}" text-anchor="middle">{s}</text>'
                 f'<circle class="point-2" cx="{xs[i]}" cy="{y(d):.0f}" r="4"/><text class="val" x="{xs[i]}" y="{y(d) + 20:.0f}" text-anchor="middle">{d}</text>'
                 f'<text class="axe" x="{xs[i]}" y="{h - 8}" text-anchor="middle">{d_court(m["occurredAt"])}</text>')
    return (f'<svg class="etaye-courbe" viewBox="0 0 {w} {h}" role="img" aria-label="Pression artérielle : 128/80 mmHg le 14/09/2026, 134/82 mmHg le 20/10/2026. Aucune autre mesure.">'
            f'{grid}{sys}{dia}{dots}<text class="axe" x="{x0}" y="14">mmHg</text></svg>')

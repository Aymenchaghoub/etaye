"""Consultation to publication (batch 7b) — S-19 consultation workspace, I-05 inline structured notes,
S-20 report editor, I-21 version conflict, S-22 version history, S-23 validation then publication review,
I-16 validate, I-17 publish, S-24 stale/replaced/withdrawn detail, I-18 replace or withdraw.

Data: synthetic-data.json only. CONS-A-001 (Claire, completed), CONS-A-002 (Louis, in progress, no appointment),
REPORT-A-001-v1 (validated, not published), PATIENT-A-001-v1/v2, PUB-A-001/002, BRIEF-A-001-v1.
States without a dataset example are labelled "template" or "variant" and add no clinical value.
"""
from lib import *

CL, LO = "R-A-001", "R-A-002"
S = lambda i: by_id("sources", i)
C1, C2 = by_id("consultations", "CONS-A-001"), by_id("consultations", "CONS-A-002")
A2, A3 = by_id("appointments", "APT-A-002"), by_id("appointments", "APT-A-003")
REP = by_id("documentVersions", "REPORT-A-001-v1")
BRIEF = by_id("documentVersions", "BRIEF-A-001-v1")
PV1, PV2 = by_id("documentVersions", "PATIENT-A-001-v1"), by_id("documentVersions", "PATIENT-A-001-v2")
PUB1, PUB2 = by_id("publications", "PUB-A-001"), by_id("publications", "PUB-A-002")
NORA, SAMI = "Dr Nora Lefebvre", "Dr Sami Rahmani"
EXPL = "Explication destinée à Claire Martin"
# Dataset stores these internal reasons in English; the French UI shows a faithful rendering (see notes-consult.md).
CORR_FR = ("Ne pas transformer une déclaration négative ancienne en fait durable ; conserver la déclaration plus récente "
           "de la patiente et le caractère manquant de l'information.")
N = lambda x: f'<span class="etaye-num">{x}</span>'
NOTE = lambda t: f'<p class="etaye-3" style="font-size:13px;margin:0">{t}</p>'


def phrases(text):
    parts = [p.strip() for p in text.split(". ") if p.strip()]
    return [p if p.endswith(".") else p + "." for p in parts]


R = phrases(REP["text"])          # 4 exact sentences of REPORT-A-001-v1
R2_EDIT = "Résultats de laboratoire du 12 octobre 2026 relus avec Claire Martin."   # human correction, supported by SRC-A-003
V1T, V2T = phrases(PV1["text"]), phrases(PV2["text"])


def fil(rid, *parts):
    out = [f'<a href="{href("S-07")}">Patients</a>', f'<a href="{href("S-09", "claire" if rid == CL else "louis")}">{esc(nom(rid, "famille"))}</a>'] + list(parts)
    return " › ".join(out)


def ecran(rid, titre, body, actor="nora", meta="", actions="", bande="", fil_=None, actif="Patients"):
    head = entete(titre, meta, actions, fil_ or fil(rid))
    return pro_shell(actif, f'<div class="etaye-page etaye-page--consult">{head}{bande}{body}</div>', actor, banner=banniere(rid))


def axes(contenu=None, fraicheur=None, publication="__", traitement=None, extra=""):
    items = []
    if contenu: items.append(f'<span class="etaye-axe">Contenu {st("business", contenu)}</span>')
    if fraicheur: items.append(f'<span class="etaye-axe">Sources {st("freshness", fraicheur)}</span>')
    if publication != "__": items.append(f'<span class="etaye-axe">Patiente {st("publication", publication)}</span>')
    if traitement: items.append(f'<span class="etaye-axe">Tâche {st("processing", traitement)}</span>')
    return f'<div class="etaye-axes">{"".join(items)}{extra}</div>'


def etiq(label, kind=""):
    return f'<span class="etaye-etiquette{f" etaye-etiquette--{kind}" if kind else ""}">{esc(label)}</span>'


def seg(t, refs=(), kind=None, lab=None):
    r = "".join(repere(n) for n in refs)
    span = f'<span class="etaye-segment etaye-segment--{kind}">{esc(t)}</span>' if kind else esc(t)
    return span + r + (etiq(lab, kind or "") if lab else "")


def src_li(sid, n, note=""):
    s = S(sid)
    return (f'<li>{repere(n, seul=True, label=f"Source {n} : {sid}")}<span>{KIND_FR[s["kind"]]} · {N(dh_pro(s["occurredAt"]))} · {esc(auteur(s))} · {N(sid)}</span>'
            f'<blockquote>« {esc(s["text"])} »</blockquote>{f"<div>{note}</div>" if note else ""}</li>')


def sauve(texte, kind=""):
    k = {"sale": " etaye-enregistrement--sale", "echec": " etaye-enregistrement--echec"}.get(kind, "")
    i = ic("alerte") if kind == "echec" else (ic("valide") if kind == "ok" else "")
    return f'<span class="etaye-enregistrement{k}" role="status">{i}{texte}</span>'


# ═════════════════════════════════ S-19 consultation workspace ═════════════════════════════════
SEC = [("motif", "Motif"), ("rapporte", "Rapporté par le patient"), ("observe", "Observé au cabinet"),
       ("mesures", "Mesures du jour"), ("sources", "Éléments du dossier consultés")]


def plan(etats, actif="motif"):
    li = "".join(f'<li><a href="#n-{k}"{" aria-current=\"true\"" if k == actif else ""}><span>{esc(l)}</span><small>{etats.get(k, "Non renseigné")}</small></a></li>' for k, l in SEC)
    return f'<nav class="etaye-notes__plan" aria-label="Sections des notes"><div class="ui-libelle etaye-2">Sections</div><ol>{li}</ol></nav>'


def section(k, titre, corps, etat_="", origine="", actions="", edition=False):
    o = f'<div class="etaye-notes__origine">{origine}</div>' if origine else ""
    return (f'<section class="etaye-notes__section{" etaye-notes__section--edition" if edition else ""}" id="n-{k}" aria-labelledby="h-{k}">'
            f'<div class="etaye-notes__tete"><h3 id="h-{k}">{esc(titre)}</h3><div class="etaye-ligne">{etat_}{actions}</div></div>{o}'
            f'<div class="etaye-notes__corps">{corps}</div></section>')


def vide_sec(k, titre, texte, lecture=False):
    act = "" if lecture else btn("Ajouter", "discret", "ajouter")
    return section(k, titre, f'<p class="etaye-notes__vide">{texte}</p>', etat("MISSING"), actions=act)


def i05_editeur(mode="ouvert"):
    """Inline editor 'Informations pour la consultation' (I-05). Context: Louis, allergies section, or motif for save states."""
    if mode in ("echec", "annuler", "enregistre"):
        motif = A2["reason"]
        if mode == "enregistre":
            corps = (f'<p style="margin:0">{esc(motif)}</p>')
            return section("motif", "Motif", corps, sauve("Enregistré · version 1 des notes", "ok"),
                           origine=f"Saisi par {SAMI} · le {N(d_court(C2["startedAt"]))} · repris du motif du rendez-vous du {N(dh_pro(A2['startsAt']))}",
                           actions=btn("Modifier", "discret", "modifier"))
        top = ""
        if mode == "echec":
            top = bandeau("attention", "L'enregistrement a échoué", "Le service n'a pas répondu. Votre saisie est conservée sur cette page ; rien n'a été enregistré.",
                          btn("Réessayer", "secondaire", "relancer"), role="alert")
        conf = ""
        if mode == "annuler":
            conf = (f'<div class="etaye-confirmer" role="alertdialog" aria-labelledby="cf-t"><strong id="cf-t">Abandonner la saisie du motif ?</strong>'
                    f'<span style="font-size:14px">Le texte saisi sera effacé de cette page. Les notes déjà enregistrées ne changent pas.</span>'
                    f'<div class="etaye-notes__actions">{btn("Continuer la saisie", "secondaire")}{btn("Abandonner les modifications", "discret", "supprimer")}</div></div>')
        corps = (top + f'<form class="etaye-form" style="gap:var(--espace-3)">'
                 + champ("Motif de la consultation", motif, "n5-motif", kind="textarea", rows=2, focus=mode != "annuler",
                         help_=f"Repris du rendez-vous du {d_court(A2['startsAt'])} {h_pro(A2['startsAt'])} ; le rendez-vous n'est pas associé à cette consultation.")
                 + (conf or f'<div class="etaye-notes__actions">{btn("Enregistrer", "principal")}{btn("Annuler les modifications", "secondaire")}</div>') + '</form>')
        ind = sauve("Échec de l'enregistrement · saisie conservée", "echec") if mode == "echec" else sauve("Modifications non enregistrées", "sale")
        return section("motif", "Informations pour la consultation · motif", corps, ind, edition=True)
    err = mode == "erreur"
    checked = ("Présente",) if err else ("Inconnue · à préciser",)
    etats = choix("n5-etat", [("Présente", "Une information a été rapportée ou observée"), ("Absence déclarée à cette date", "Uniquement si quelqu'un l'a déclarée"),
                              ("Inconnue · à préciser", "État actuel du dossier"), ("Non demandée", "La question n'a pas été posée")], legend="État de l'information", checked=checked)
    origine = choix("n5-origine", [("Rapporté par le patient", None), ("Observé au cabinet", None), ("Rapporté par un document", None)],
                    legend="Origine", checked=("Rapporté par le patient",) if err else ())
    resume = resume_erreurs([("n5-texte", "Décrivez l'information rapportée")]) if err else ""
    corps = (f'{resume}<form class="etaye-form" style="gap:var(--espace-4)">'
             f'{champ("Rubrique", "Allergies", "n5-rub", kind="select", options=["Allergies", "Antécédents médicaux", "Traitements déclarés"])}'
             f'{etats}{origine}'
             f'{champ("Date de l’information", d_court(C2["startedAt"]), "n5-date", num=True, help_="Date à laquelle l’information a été rapportée ou observée")}'
             + champ("Texte", "", "n5-texte", kind="textarea", rows=3, focus=err,
                     error="Décrivez l'information rapportée, ou choisissez « Inconnue · à préciser »." if err else None,
                     help_=None if err else "Écrivez ce qui a été dit ou observé, sans conclure.")
             + f'<div class="etaye-notes__actions">{btn("Enregistrer", "principal")}{btn("Annuler les modifications", "secondaire")}</div></form>')
    ind = sauve("Modifications non enregistrées", "sale") if err else sauve("Aucune modification")
    return section("allergies", "Informations pour la consultation · allergies", corps, ind,
                   origine="Dossier de Louis Bernard : allergies jamais renseignées · aucune source", edition=True)


def louis_aside(lecture=False):
    manque = (f'<ul class="etaye-controle">'
              f'<li>{ic("info", 18, "etaye-2")}<div>Antécédents : non renseignés<small>HIST-A-003 · inconnu · ne signifie pas absence</small></div><span></span></li>'
              f'<li>{ic("info", 18, "etaye-2")}<div>Allergies : jamais renseignées</div><span></span></li>'
              f'<li>{ic("info", 18, "etaye-2")}<div>Traitements déclarés : non renseignés</div><span></span></li>'
              f'<li>{ic("info", 18, "etaye-2")}<div>Mesures, biologie, documents : aucun au dossier</div><span></span></li></ul>')
    rdv = (f'<dl class="etaye-def"><dt>Rendez-vous</dt><dd>{N(dh_pro(A2["startsAt"]))} · {esc(A2["reason"])}</dd><dt>Statut</dt><dd>{st("appointment", A2["status"])}</dd>'
           f'<dt>Lien</dt><dd>Non associé à cette consultation</dd></dl>'
           + ("" if lecture else f'<div class="etaye-ligne" style="margin-top:var(--espace-3)">{btn("Associer ce rendez-vous", "secondaire", "lien")}</div>')
           + NOTE("Le rendez-vous reste une ressource distincte : terminer la consultation ne le modifie pas."))
    ai = f'<div class="etaye-2" style="font-size:14px">{st("processing", "FAILED")} Brief du {N(dh_pro(by_id("aiTasks", "AI-A-002")["requestedAt"]))} non produit · aucun texte de remplacement</div><div class="etaye-ligne" style="margin-top:var(--espace-2)">{lien("Voir la tâche", href("S-40", "echec"))}</div>'
    return panneau("Ce qui manque au dossier", manque) + panneau("Rendez-vous du jour", rdv) + panneau("Brief de préparation", ai)


def s19(mode="en-cours"):
    if mode in ("termine", "longue"):
        return s19_claire(mode)
    actor = "sami_a"
    lecture = mode in ("lecture-seule", "annulee")
    bande, ind = "", sauve("Aucune modification depuis l'ouverture")
    etats = {}
    motif_sec = vide_sec("motif", "Motif", f"Non renseigné. Le rendez-vous du {d_court(A2['startsAt'])} indique « {esc(A2['reason'])} » ; il n'est pas associé à cette consultation.", lecture)
    if mode in ("echec", "conflit", "session"):
        motif_val = A2["reason"]
        etats["motif"] = "Saisie non enregistrée"
        if mode == "echec":
            ind = sauve("Échec de l'enregistrement · saisie conservée", "echec")
            bande = bandeau("attention", "L'enregistrement a échoué", "Le service n'a pas répondu. Votre saisie est conservée sur cette page ; aucune note n'a encore été enregistrée pour cette consultation.",
                            btn("Réessayer l'enregistrement", "secondaire", "relancer"), role="alert")
        elif mode == "conflit":
            ind = sauve("Non enregistré · version plus récente sur le serveur", "echec")
            bande = bandeau("attention", "Ces notes ont été modifiées par Dr Nora Lefebvre", "Variante V-CONFLICT : deux personnes ont modifié la même version des notes. Votre saisie est conservée ; rien n'a été écrasé.",
                            btn("Comparer les versions", "secondaire", "comparer", href_=href("I-21")), role="alert")
        else:
            ind = sauve("Non enregistré · session expirée", "echec")
            bande = bandeau("attention", "Votre session a expiré avant l'enregistrement", "Variante V-SESSION : votre saisie est conservée sur cet appareil et n'a pas été envoyée. Reconnectez-vous pour l'enregistrer.",
                            btn("Se reconnecter", "secondaire", href_=href("S-38", "expiree")), role="alert")
        motif_sec = section("motif", "Motif", champ("Motif de la consultation", motif_val, "c19-motif", kind="textarea", rows=2,
                                                    help_="Repris du motif du rendez-vous du 02/11/2026 14:00."),
                            sauve("Modifications non enregistrées", "sale"), edition=True)
    if mode == "lecture-seule":
        ind = sauve("Lecture seule")
        bande = bandeau("neutre", "Lecture seule · votre accès à ce cabinet n'est plus actif", "Gabarit : l'appartenance de Dr Sami Rahmani au Cabinet des Tilleuls a été désactivée. Les notes ne peuvent plus être modifiées ; aucune saisie n'a été envoyée après la désactivation.", icon="verrou")
    if mode == "annulee":
        ind = sauve("Consultation annulée · lecture seule")
        bande = bandeau("neutre", "Consultation annulée", f"Gabarit : annulée par {SAMI}. Motif saisi : « consultation ouverte par erreur ». Les notes restent consultables ; le rendez-vous du {N(dh_pro(A2['startsAt']))} n'est pas modifié.", icon="interdit")
    secs = (motif_sec
            + vide_sec("rapporte", "Rapporté par le patient", "Non renseigné. Rien n'a été déclaré dans cette consultation.", lecture)
            + vide_sec("observe", "Observé au cabinet", "Non renseigné.", lecture)
            + vide_sec("mesures", "Mesures du jour", "Aucune mesure saisie. Le dossier ne contient aucune mesure antérieure.", lecture)
            + vide_sec("sources", "Éléments du dossier consultés", "Aucun élément : le dossier de Louis Bernard ne contient pas encore de source datée.", lecture))
    notes = f'<div class="etaye-notes" aria-label="Notes structurées">{secs}</div>'
    body = f'<div class="etaye-cols etaye-cols--3">{plan(etats)}{notes}<div class="etaye-pile">{louis_aside(lecture)}</div></div>'
    statut_c = statut("neutre", "Annulée", "interdit") if mode == "annulee" else statut("neutre", "En cours", "horloge")
    meta = f'{statut_c} Commencée le {N(dh_pro(C2["startedAt"]))} par {SAMI} · {C2["id"]} · sans rendez-vous associé'
    if lecture:
        actions = ind
    else:
        actions = (ind + btn("Rédiger le compte rendu", "secondaire", "document", href_=href("S-20", "nouveau"))
                   + btn("Terminer la consultation", "principal", href_=href("S-19", "terminer")))
    page_ = ecran(LO, "Consultation", body, actor, meta=meta, actions=actions, bande=bande)
    if mode == "terminer":
        corps = (f'<ul class="etaye-controle">'
                 f'<li>{ic("valide", 18, "etaye-controle__ok")}<div>Aucune modification non enregistrée</div><span></span></li>'
                 f'<li>{ic("alerte", 18, "etaye-controle__bloque")}<div><strong>Motif non renseigné</strong><small>Requis pour terminer (garde proposée). Ajoutez le motif ou annulez la consultation.</small></div>{btn("Ajouter le motif", "discret")}</li>'
                 f'<li>{ic("info", 18, "etaye-2")}<div>Aucun compte rendu rédigé<small>Vous pourrez le rédiger après ; il restera interne tant qu\'il n\'est ni validé ni publié.</small></div><span></span></li>'
                 f'<li>{ic("info", 18, "etaye-2")}<div>Rendez-vous du {N(dh_pro(A2["startsAt"]))} : {st("appointment", A2["status"])}<small>Il n\'est pas associé et ne change pas.</small></div><span></span></li></ul>')
        return dialogue(page_, "Terminer la consultation", corps,
                        btn("Poursuivre la consultation", "secondaire") + btn("Terminer la consultation", "principal", disabled=True),
                        intro=f"Louis Bernard · {C2['id']} · commencée à {h_pro(C2["startedAt"])}")
    return page_


def s19_claire(mode):
    """CONS-A-001 completed 20 Oct 10:35 (read-only). 'longue' = V-LONG variant quoting every dated Claire source in full."""
    s4, s6, s7, s7b = S("SRC-A-004"), S("SRC-A-006"), S("SRC-A-007-v2"), S("SRC-A-007-v1")
    q = lambda s, n: f'<blockquote>« {esc(s["text"])} »{repere(n)}</blockquote>'
    org = lambda s: f'{SOURCE_TYPE_FR[s["sourceType"]]} · survenu le {N(dh_pro(s["occurredAt"]))} · enregistré le {N(dh_pro(s["recordedAt"]))} · {esc(auteur(s))} · {N(s["id"])}'
    motif = section("motif", "Motif", f'<p style="margin:0">Consultation de suivi{repere(1)}</p>', etat("PRACTITIONER_OBSERVED", "Note du praticien"), origine=f"Extrait de la note {N(s6['id'])} · {N(dh_pro(s6['occurredAt']))} · {esc(auteur(s6))}")
    rap = section("rapporte", "Rapporté par la patiente", '<p class="etaye-notes__vide">Non renseigné pour cette consultation.</p>', etat("MISSING"))
    obs = section("observe", "Observé au cabinet", q(s6, 1), etat("PRACTITIONER_OBSERVED"), origine=org(s6))
    mes = section("mesures", "Mesures du jour",
                  f'{q(s4, 2)}<div>{q(s7, 3)}<div class="etaye-3" style="font-size:13px;margin-top:var(--espace-1)">Version 2 · corrige la version 1 « <span class="etaye-barre">{esc(s7b["text"])}</span> » · motif : {esc(s7["correctionReason"])}</div></div>',
                  etat("PRACTITIONER_OBSERVED"), origine=f"Mesures du {N(d_court(s4['occurredAt']))} · {esc(auteur(s4))}")
    ids = ["SRC-A-003", "SRC-A-002"] if mode == "termine" else ["SRC-A-005", "SRC-A-003", "SRC-A-002", "SRC-A-001", "SRC-A-008", "SRC-A-007-v1"]
    lis = "".join(src_li(i, k + 4) for k, i in enumerate(ids))
    note_long = NOTE("Variante V-LONG : chaque source datée de Claire Martin est citée en entier, sans nouvelle information clinique. SRC-A-005 est postérieure à la consultation et n'y figure qu'à titre de lecture du dossier actuel.") if mode == "longue" else ""
    srcs = section("sources", "Éléments du dossier consultés", f'{note_long}<ol class="etaye-sources-liste">{lis}</ol>', f'<span class="etaye-3" style="font-size:13px">{len(ids)} sources</span>')
    notes = f'<div class="etaye-notes">{motif}{rap}{obs}{mes}{srcs}</div>'
    cr = (f'<dl class="etaye-def"><dt>Document</dt><dd>Compte rendu · version {REP["version"]}</dd><dt>Contenu</dt><dd>{st("business", "VALIDATED")}</dd>'
          f'<dt>Validé</dt><dd>{N(dh_pro(REP["validatedAt"]))} · {NORA}</dd><dt>Patiente</dt><dd>{st("publication", None)}</dd></dl>'
          f'<div class="etaye-ligne" style="margin-top:var(--espace-3)">{btn("Ouvrir le compte rendu", "secondaire", "document", href_=href("S-20", "lecture-seule"))}</div>')
    rdv = f'<dl class="etaye-def"><dt>Rendez-vous</dt><dd>{N(dh_pro(A3["startsAt"]))}</dd><dt>Statut</dt><dd>{st("appointment", A3["status"])}</dd><dt>Lien</dt><dd>Associé · {A3["id"]}</dd></dl>'
    etats = {"motif": "Note du praticien", "rapporte": "Non renseigné", "observe": "1 note", "mesures": "2 mesures · 1 corrigée", "sources": f"{len(ids)} sources"}
    body = f'<div class="etaye-cols etaye-cols--3">{plan(etats)}{notes}<div class="etaye-pile">{panneau("Compte rendu", cr)}{panneau("Rendez-vous", rdv)}</div></div>'
    meta = f'{statut("neutre", "Terminée", "valide")} Terminée le {N(dh_pro(C1["completedAt"]))} par {NORA} · {C1["id"]} · lecture seule'
    return ecran(CL, "Consultation du 20 octobre 2026", body, "nora", meta=meta, actions=sauve("Lecture seule"))


# ═════════════════════════════════ S-20 consultation report editor ═════════════════════════════════
def rapport_corps(mode):
    r1 = seg(R[0], (3,))
    r2 = seg(R[1], (1, 3))
    r3 = seg(R[2], (2,))
    r4 = seg(R[3], (3,))
    if mode in ("sale", "enregistrement", "echec", "enregistre", "conflit"):
        r2 = seg(R2_EDIT, (1,), "humain", "Correction humaine" + (" · non enregistrée" if mode in ("sale", "enregistrement", "echec", "conflit") else ""))
    if mode == "non-soutenu":
        r3 = seg(R[2], (1,), "alerte", "Source à revoir")
    if mode == "source-retiree":
        r2 = seg(R[1], (1, 3), "alerte", "Source 1 retirée du dossier")
    return (f'<h3>Consultation</h3><p>{r1}</p><h3>Observations</h3><p>{r2} {r3}</p><h3>Informations à compléter</h3><p>{r4}</p>')


def s20(mode="brouillon"):
    if mode == "nouveau":
        return s20_louis()
    lecture = mode == "lecture-seule"
    v = 1 if lecture else 2
    srcs = [("SRC-A-003", 1), ("SRC-A-004", 2), ("SRC-A-006", 3)]
    notes = {}
    bande = ""
    ind = {"brouillon": sauve("Enregistré · brouillon version 2", "ok"), "sale": sauve("Modifications non enregistrées", "sale"),
           "enregistrement": sauve("Enregistrement…"), "echec": sauve("Échec de l'enregistrement · texte conservé", "echec"),
           "enregistre": sauve("Enregistré · brouillon version 2", "ok"), "conflit": sauve("Non enregistré · version plus récente", "echec"),
           "non-soutenu": sauve("Enregistré · brouillon version 2", "ok"), "source-retiree": sauve("Enregistré · brouillon version 2", "ok"),
           "lecture-seule": sauve("Version validée · non modifiable")}[mode]
    if mode == "echec":
        bande = bandeau("attention", "Le compte rendu n'a pas été enregistré", "Le service n'a pas confirmé l'enregistrement. Votre texte est conservé dans l'éditeur ; réessayez avant de quitter la page.",
                        btn("Réessayer l'enregistrement", "secondaire", "relancer"), role="alert")
    if mode == "conflit":
        bande = bandeau("attention", "Une version plus récente existe", "Variante V-CONFLICT : Dr Sami Rahmani a enregistré ce brouillon pendant votre saisie. Votre texte n'a pas été envoyé et rien n'a été écrasé.",
                        btn("Comparer et préparer une nouvelle version", "secondaire", "comparer", href_=href("I-21", "ouvert")), role="alert")
    if mode == "non-soutenu":
        bande = bandeau("attention", "Une citation ne soutient pas sa phrase", "Variante : la phrase sur la pression artérielle cite le document du 12/10/2026 (SRC-A-003), qui ne contient aucune pression artérielle. Corrigez la citation ou retirez la valeur avant la relecture.", role="alert")
        notes["SRC-A-003"] = f'{etiq("Source à revoir", "alerte")} Citée pour « pression artérielle » : aucune valeur de ce type dans l’extrait.'
        notes["SRC-A-004"] = "Contient la mesure du 20/10/2026 · non citée par cette phrase"
    if mode == "source-retiree":
        bande = bandeau("attention", "Une source citée a été retirée du dossier", "Gabarit : le document du 12/10/2026 (SRC-A-003, DOC-A-001) n'est plus disponible. La phrase qui le cite reste affichée telle quelle, marquée « à revoir ». Elle ne peut pas être soumise à la relecture sans nouvelle vérification.", role="alert")
        notes["SRC-A-003"] = f'{etiq("Retirée du dossier", "alerte")} Extrait figé à la date de citation · la source actuelle n’est plus accessible'
    edition = mode in ("sale", "enregistrement", "echec", "conflit")
    cls = "etaye-redaction" + (" etaye-redaction--edition" if edition else "") + (" etaye-redaction--lecture" if lecture else "")
    barre = (f'<div class="etaye-redaction__barre"><span>Compte rendu de consultation · Claire Martin · consultation du {N("20/10/2026")}</span>'
             f'<span>{"Validé · figé" if lecture else "Repères : source datée · étiquette : origine du texte"}</span></div>')
    editeur = f'<div class="{cls}" role="{"document" if lecture else "textbox"}" aria-label="Texte du compte rendu"{"" if lecture else " aria-multiline=\"true\""}>{barre}{rapport_corps(mode)}</div>'
    raison = "" if lecture else champ("Motif de la nouvelle version", "Préciser la date des résultats relus", "c20-motif", required=True,
                                       help_="Visible dans l'historique des versions ; la version 1 reste inchangée.")
    lignee = (f'<dl class="etaye-def"><dt>Version 1</dt><dd>{st("business", "VALIDATED")} {N(dh_pro(REP["validatedAt"]))} · {NORA} · inchangée</dd>'
              + ("" if lecture else f'<dt>Version 2</dt><dd>{st("business", "DRAFT")} créée à partir de la version 1 · variante</dd>') + '</dl>')
    src_panel = panneau("Sources citées", f'<ol class="etaye-sources-liste">{"".join(src_li(i, n, notes.get(i, "")) for i, n in srcs)}</ol>',
                        "" if lecture else btn("Ajouter une source", "discret", "ajouter"))
    left = f'<div class="etaye-pile">{editeur}{raison}</div>'
    body = f'<div class="etaye-cols etaye-cols--2">{left}<div class="etaye-pile">{src_panel}{panneau("Versions", lignee, lien("Historique", href("S-22", "une-version")))}</div></div>'
    variante = "" if lecture else NOTE("Variante : la version 2 n'existe pas dans le jeu etaye-design-v1 ; son texte de départ est exactement celui de REPORT-A-001-v1. La seule modification montrée s'appuie sur la date de SRC-A-003.")
    if lecture:
        acts = ind + btn("Créer une nouvelle version", "secondaire", "ajouter", href_=href("S-20", "brouillon"))
        ax = axes("VALIDATED", "CURRENT", None)
        titre = "Compte rendu du 20 octobre 2026 · version 1"
    else:
        busy = mode == "enregistrement"
        acts = (ind + btn("Enregistrement…" if busy else "Enregistrer", "secondaire", disabled=busy or mode == "brouillon" or mode == "enregistre")
                + btn("Soumettre à la relecture", "principal", disabled=mode in ("non-soutenu", "source-retiree", "conflit", "echec", "enregistrement", "sale")))
        ax = axes("DRAFT", "CURRENT", None)
        titre = "Compte rendu du 20 octobre 2026 · version 2"
    return ecran(CL, titre, f'{ax}{variante}{body}', "nora", meta=f"CONS-A-001 · rédigé par {NORA}", actions=acts, bande=bande,
                 fil_=fil(CL, "Comptes rendus"), actif="Comptes rendus")


def s20_louis():
    h = by_id("history", "HIST-A-003")
    corps = (f'<div class="etaye-redaction__barre"><span>Compte rendu de consultation · Louis Bernard · {C2["id"]}</span><span>Nouveau · aucun texte généré</span></div>'
             f'<h3>Consultation</h3><p class="etaye-3" style="font-family:var(--font-texte);font-size:15px">Saisissez le compte rendu. Le motif des notes n\'est pas renseigné.</p>'
             f'<h3>Informations à compléter</h3>'
             f'<p>{seg("Antécédents médicaux non renseignés.", (), "manque", "Information manquante")}</p>'
             f'<p>{seg("Allergies jamais renseignées.", (), "manque", "Information manquante")}</p>'
             f'<p>{seg("Traitements déclarés non renseignés.", (), "manque", "Information manquante")}</p>')
    editeur = f'<div class="etaye-redaction etaye-redaction--edition" role="textbox" aria-multiline="true" aria-label="Texte du compte rendu">{corps}</div>'
    side = (panneau("Sources citables", vide("Aucune source", "Le dossier de Louis Bernard ne contient aucune note, mesure, biologie ni document daté. Les phrases ci-contre décrivent un manque ; elles n'ont pas besoin de source."))
            + panneau("Origine", f'<p class="etaye-2" style="margin:0;font-size:14px">Les lignes « Information manquante » reprennent l\'état du dossier ({h["id"]} : inconnu). Aucun brouillon généré : le brief du 02/11/2026 a échoué.</p>'))
    body = f'<div class="etaye-cols etaye-cols--2"><div class="etaye-pile">{editeur}</div><div class="etaye-pile">{side}</div></div>'
    acts = sauve("Brouillon créé · rien d'autre enregistré") + btn("Enregistrer", "secondaire", disabled=True) + btn("Soumettre à la relecture", "principal", disabled=True)
    return ecran(LO, "Compte rendu · nouveau brouillon", axes("DRAFT", None, None) + body, "sami_a",
                 meta=f"{C2['id']} · consultation en cours depuis {h_pro(C2["startedAt"])} · {SAMI}", actions=acts, fil_=fil(LO, "Comptes rendus"), actif="Comptes rendus")


# ═════════════════════════════════ I-21 version conflict ═════════════════════════════════
def i21(mode="ouvert"):
    local = [seg(R[0], (3,)), seg(R2_EDIT, (1,), "humain", "Votre modification"), seg(R[2], (2,)), seg(R[3], (3,))]
    serveur = [seg(R[0], (3,)), seg(R[1], (1, 3)), seg(R[2], (2,)), seg(R[3], (3, 4)) + etiq("Source ajoutée")]
    meta = (f'<dl class="etaye-def"><dt>Votre copie</dt><dd>Brouillon version 2 · modifiée par {NORA} · non enregistrée</dd>'
            f'<dt>Sur le serveur</dt><dd>Brouillon version 2 · enregistrée par {SAMI} pendant votre saisie (variante V-CONFLICT, heure non fournie par le jeu)</dd>'
            f'<dt>Écart</dt><dd>2 modifications · 1 de votre part, 1 de Dr Sami Rahmani</dd></dl>')
    if mode == "copie":
        top = toast("Votre copie est conservée dans cette page jusqu’à la fin de la session. Elle n’est pas enregistrée.")
    else:
        top = ""
    diff = (f'<div class="etaye-diff__legende"><span>{etiq("Votre modification", "humain")} texte que vous avez changé</span><span>{etiq("Source ajoutée")} citation ajoutée sur le serveur</span><span>Rien n’est fusionné automatiquement.</span></div>'
            f'<div class="etaye-diff"><section class="etaye-diff__col" aria-label="Votre copie"><div class="etaye-diff__tete"><span>Votre copie</span><span>{NORA}</span></div>'
            f'<div class="etaye-diff__corps">{"".join(f"<p>{x}</p>" for x in local)}</div></section>'
            f'<section class="etaye-diff__col" aria-label="Version du serveur"><div class="etaye-diff__tete"><span>Version enregistrée</span><span>{SAMI}</span></div>'
            f'<div class="etaye-diff__corps">{"".join(f"<p>{x}</p>" for x in serveur)}</div></section></div>')
    if mode == "ouvert":
        main = (panneau("Ce qui s'est passé", meta + f'<p class="etaye-2" style="margin:var(--espace-3) 0 0;font-size:14px">Votre enregistrement a été refusé pour ne pas écraser le travail de Dr Sami Rahmani. Comparez les deux textes, puis préparez une nouvelle version à partir de la version enregistrée.</p>')
                + panneau("Aperçu des écarts", f'<ul class="etaye-controle"><li>{ic("modifier", 18, "etaye-2")}<div>Phrase « Résultats … relus avec Claire Martin »<small>Vous : date des résultats ajoutée (12/10/2026, SRC-A-003)</small></div><span></span></li>'
                          f'<li>{ic("lien", 18, "etaye-2")}<div>Phrase « Liste des traitements … »<small>Dr Sami Rahmani : source 4 ajoutée (note du 20/10/2026)</small></div><span></span></li></ul>'))
    else:
        main = top + panneau("Comparer les deux textes", diff)
    acts = btn("Conserver ma copie en mémoire", "secondaire", "copier", href_=href("I-21", "copie")) + btn("Comparer et préparer une nouvelle version", "principal", "comparer", href_=href("I-21", "comparaison"))
    head = entete("Une version plus récente existe", "Compte rendu du 20 octobre 2026 · brouillon version 2 · variante V-CONFLICT", acts, fil(CL, f'<a href="{href("S-20")}">Compte rendu</a>'))
    return pro_shell("Comptes rendus", f'<div class="etaye-page etaye-page--consult">{head}{main}</div>', "nora", banner=banniere(CL))


# ═════════════════════════════════ S-22 version history and comparison ═════════════════════════════════
def lignee_expl(courant=2, nouvelle=False, pub2="PUBLISHED"):
    v3 = (f'<li><div class="etaye-lignee__titre">Version 3 {st("business", "DRAFT")}</div><span>Créée pendant votre lecture par {NORA} · variante</span></li>') if nouvelle else ""
    li2 = (f'<li{" aria-current=\"true\"" if courant == 2 else ""}><div class="etaye-lignee__titre">Version 2 {st("business", "VALIDATED")}{st("publication", pub2)}</div>'
           f'<span>Validée le {N(dh_pro(PV2["validatedAt"]))} par {NORA} · {st("freshness", "CURRENT")}</span>'
           f'<span>Publiée le {N(dh_pro(PUB2["publishedAt"]))} ({PUB2["id"]}){" · retirée ensuite (V-WITHDRAWN)" if pub2 == "WITHDRAWN" else ""} · 1 correction humaine</span>'
           f'<span>Sources : SRC-A-001, SRC-A-005, SRC-A-006</span></li>')
    li1 = (f'<li{" aria-current=\"true\"" if courant == 1 else ""}><div class="etaye-lignee__titre">Version 1 {st("business", "VALIDATED")}{st("publication", "REPLACED")}</div>'
           f'<span>Validée le {N(dh_pro(PV1["validatedAt"]))} par {NORA} · {st("freshness", "STALE")} · signalé le {N(dh_pro(PV1["staleSince"]))}</span>'
           f'<span>Publiée le {N(dh_pro(PUB1["publishedAt"]))} ({PUB1["id"]}) · remplacée le {N(dh_pro(PUB2["publishedAt"]))}</span>'
           f'<span>Sources : SRC-A-001, SRC-A-006</span></li>')
    return f'<ol class="etaye-lignee" reversed>{v3}{li2}{li1}</ol>'


def diff_v1_v2():
    a = "".join(f'<p>{etiq("Supprimé")}<del>{esc(t)}</del></p>' for t in V1T)
    b = "".join(f'<p>{etiq("Ajouté")}<ins>{esc(t)}</ins></p>' for t in V2T)
    return (f'<div class="etaye-diff__legende"><span>{etiq("Supprimé")} texte de la version 1 absent de la version 2</span><span>{etiq("Ajouté")} texte nouveau</span></div>'
            f'<div class="etaye-diff"><section class="etaye-diff__col" aria-label="Version 1"><div class="etaye-diff__tete"><span>Version 1</span><span class="etaye-num">{dh_pro(PV1["validatedAt"])}</span></div><div class="etaye-diff__corps">{a}</div></section>'
            f'<section class="etaye-diff__col" aria-label="Version 2"><div class="etaye-diff__tete"><span>Version 2</span><span class="etaye-num">{dh_pro(PV2["validatedAt"])}</span></div><div class="etaye-diff__corps">{b}</div></section></div>')


def couches():
    return (f'<ol class="etaye-couches">'
            f'<li><span class="etaye-couches__nom">Faits des sources</span><div class="etaye-pile" style="gap:var(--espace-2)">'
            f'<blockquote>« {esc(S("SRC-A-001")["text"])} »{repere(1)}</blockquote><span class="etaye-3" style="font-size:13px">{etat_source(S("SRC-A-001"))}</span>'
            f'<blockquote>« {esc(S("SRC-A-005")["text"])} »{repere(2)}</blockquote><span class="etaye-3" style="font-size:13px">{etat_source(S("SRC-A-005"))} · {N(dh_pro(S("SRC-A-005")["occurredAt"]))}</span></div></li>'
            f'<li><span class="etaye-couches__nom">Texte généré</span><div class="etaye-pile" style="gap:var(--espace-2)"><blockquote><del>{esc(PV2["aiOriginalText"])}</del></blockquote>'
            f'<span>{etiq("Rejeté", "alerte")} Non soutenu : la source 2 est plus récente et rapporte un souvenir de réaction ; la source 1 ne permet pas de conclure à l\'absence aujourd\'hui.</span></div></li>'
            f'<li><span class="etaye-couches__nom">Correction humaine</span><div class="etaye-pile" style="gap:var(--espace-2)"><blockquote>{esc(PV2["humanEditedText"])}</blockquote>'
            f'<span>{etiq("Correction humaine", "humain")} {NORA} · motif : {esc(CORR_FR)}</span></div></li></ol>')


def s22(mode="comparaison"):
    if mode == "une-version":
        li = (f'<ol class="etaye-lignee"><li aria-current="true"><div class="etaye-lignee__titre">Version 1 {st("business", "VALIDATED")}{st("publication", None)}</div>'
              f'<span>Validée le {N(dh_pro(REP["validatedAt"]))} par {NORA} · {st("freshness", "CURRENT")}</span><span>Sources : SRC-A-003, SRC-A-004, SRC-A-006</span></li></ol>')
        right = panneau("Comparer", vide("Une seule version", "Ce compte rendu n'a qu'une version validée. Il n'y a rien à comparer ; une nouvelle version laisserait la version 1 inchangée.",
                                         btn("Créer une nouvelle version", "secondaire", "ajouter", href_=href("S-20", "brouillon"))))
        body = f'<div class="etaye-cols etaye-cols--2g">{panneau("Versions", li)}{right}</div>'
        return ecran(CL, "Compte rendu du 20 octobre 2026 · historique", body, meta="CONS-A-001 · réservé aux professionnels", fil_=fil(CL, "Comptes rendus"), actif="Comptes rendus")
    bande, courant = "", 2
    if mode == "nouvelle-version":
        bande = bandeau("info", "Une nouvelle version vient d'être créée", f"Variante : {NORA} a ouvert un brouillon version 3 pendant votre lecture. Les versions 1 et 2 ne changent pas ; la comparaison affichée reste valable.",
                        btn("Afficher la version 3", "secondaire"))
    if mode == "source-manquante":
        bande = bandeau("attention", "Une source n'est plus lisible dans sa version d'origine", "Gabarit : l'extrait de SRC-A-006 tel qu'il était le 20/10/2026 n'a pas pu être chargé. Le texte des versions reste affiché ; le repère 3 indique « extrait indisponible ».", btn("Réessayer", "secondaire", "relancer"), role="alert")
    if mode == "lien-remplace":
        courant = 1
        bande = bandeau("neutre", "Vous avez suivi un lien vers une publication remplacée", f"La publication {PUB1['id']} (version 1) a été remplacée le {N(dh_pro(PUB2['publishedAt']))}. Vous lisez la version 1 dans l'historique interne ; la patiente, elle, est renvoyée vers la version actuelle.",
                        btn("Ouvrir la version actuelle", "secondaire", href_=href("S-23", "publie")), icon="remplacer")
    lecture = mode == "lecture-seule"
    if lecture:
        bande = bandeau("neutre", "Consultation seule", "Gabarit : votre accès permet de lire l'historique, pas de créer une nouvelle version.", icon="verrou")
    comp = panneau(f"Comparer la version 1 et la version 2", diff_v1_v2()
                   + f'<div class="ui-libelle etaye-2" style="margin-top:var(--espace-4)">Sources de la version</div>'
                     f'<ul class="etaye-controle"><li>{ic("valide", 18, "etaye-2")}<div>SRC-A-001 · absence déclarée le 14/09/2026<small>Dans les deux versions</small></div><span></span></li>'
                     f'<li>{ic("valide", 18, "etaye-2")}<div>SRC-A-006 · note du 20/10/2026<small>Dans les deux versions</small></div><span></span></li>'
                     f'<li>{ic("ajouter", 18, "etaye-controle__ok")}<div>SRC-A-005 · préparation du 02/11/2026 {etiq("Ajoutée")}<small>Déclaration de la patiente · non confirmée</small></div><span></span></li></ul>')
    origine = panneau("D’où vient le texte de la version 2", couches())
    if mode == "generation":
        right = origine + comp
    elif mode == "longue":
        lin = (f'<ol class="etaye-couches"><li><span class="etaye-couches__nom">Version 1 · validée<br><span class="etaye-num etaye-3">{dh_pro(PV1["validatedAt"])}</span></span><blockquote>{esc(PV1["text"])}</blockquote></li>'
               f'<li><span class="etaye-couches__nom">Sources modifiées<br><span class="etaye-num etaye-3">{dh_pro(PV1["staleSince"])}</span></span><div>Nouvelle déclaration de la patiente (SRC-A-005) en contradiction avec une source de la version 1 (SRC-A-001).</div></li>'
               f'<li><span class="etaye-couches__nom">Version 2 · texte généré</span><blockquote><del>{esc(PV2["aiOriginalText"])}</del></blockquote></li>'
               f'<li><span class="etaye-couches__nom">Version 2 · corrigée et validée<br><span class="etaye-num etaye-3">{dh_pro(PV2["validatedAt"])}</span></span><blockquote>{esc(PV2["text"])}</blockquote></li>'
               f'<li><span class="etaye-couches__nom">Publication<br><span class="etaye-num etaye-3">{dh_pro(PUB2["publishedAt"])}</span></span><div>{PUB2["id"]} remplace {PUB1["id"]} (publiée le {N(dh_pro(PUB1["publishedAt"]))}).</div></li></ol>')
        right = panneau("Toute la lignée, dans l’ordre", lin) + comp + origine
    else:
        right = comp + origine
    acts = "" if lecture else btn("Créer un brouillon à partir de la version 2", "secondaire", "ajouter", href_=href("S-21"))
    body = f'<div class="etaye-cols etaye-cols--2g">{panneau("Versions", lignee_expl(courant, mode == "nouvelle-version"))}<div class="etaye-pile">{right}</div></div>'
    return ecran(CL, f"{EXPL} · historique", body, meta="Versions figées · les brouillons générés et les notes de relecture ne sont jamais montrés à la patiente", actions=acts, bande=bande)


# ═════════════════════════════════ S-23 validation, then publication ═════════════════════════════════
def doc_v2(mode):
    if mode == "contradiction":
        p = f'<p>{seg(PV2["aiOriginalText"], (1,), "alerte", "Non soutenu")}</p>'
        barre = "Brouillon généré · avant correction (SC-UNSUPPORTED-DRAFT)"
    else:
        refs = {0: (2,), 1: (), 2: (3,)}
        alerte = mode == "source-manquante"
        p = (f'<p>{etiq("Correction humaine", "humain")}' + " ".join(seg(t, refs[i], "alerte" if (alerte and i == 2) else None, "Source 3 non chargée" if (alerte and i == 2) else None) for i, t in enumerate(V2T)) + "</p>")
        barre = f"Version 2 · texte rédigé par {NORA} à la place du texte généré"
    return f'<div class="etaye-redaction etaye-redaction--lecture" role="document" aria-label="Texte relu"><div class="etaye-redaction__barre"><span>{barre}</span><span>Repères : sources datées</span></div>{p}</div>'


def points(mode):
    ok, bl, inf = (lambda: ic("valide", 18, "etaye-controle__ok")), (lambda: ic("alerte", 18, "etaye-controle__bloque")), (lambda: ic("info", 18, "etaye-2"))
    if mode == "contradiction":
        l1 = f'<li>{bl()}<div><strong>Affirmation non soutenue</strong><small>« {esc(PV2["aiOriginalText"])} » contredit la déclaration du 02/11/2026 (SRC-A-005). La source du 14/09/2026 ne permet pas de conclure aujourd\'hui.</small><div class="etaye-ligne">{btn("Corriger le texte", "discret", href_=href("S-21"))}</div></div><span></span></li>'
    else:
        l1 = f'<li>{ok()}<div>Texte généré non soutenu retiré<small>« {esc(PV2["aiOriginalText"])} » · corrigé par {NORA}</small></div><span></span></li>'
    src_ok = mode != "source-manquante"
    l2 = (f'<li>{ok() if src_ok else bl()}<div>{"Sources relues : 3 sur 3" if src_ok else "<strong>Sources relues : 2 sur 3</strong>"}<small>SRC-A-001 du 14/09/2026 · SRC-A-005 du 02/11/2026 · SRC-A-006 du 20/10/2026{"" if src_ok else " · la source 3 n’a pas pu être chargée"}</small>{"" if src_ok else '<div class="etaye-ligne">' + btn("Réessayer", "discret", "relancer") + '</div>'}</div><span></span></li>')
    l3 = f'<li>{inf()}<div>Allergies : déclarations discordantes<small>Absence déclarée le 14/09/2026 et souvenir de réaction rapporté le 02/11/2026, non confirmé. Le texte ne tranche pas : « sera précisée lors de votre prochaine consultation ».</small></div><span></span></li>'
    l4 = f'<li>{inf()}<div>Information manquante signalée<small>Traitements actuels non renseignés · le texte le dit</small></div><span></span></li>'
    l5 = ""
    if mode == "perime":
        l5 = f'<li>{bl()}<div><strong>Sources modifiées depuis le début de la relecture</strong><small>Gabarit : une source du dossier a changé. Relisez-la ; la validation porte sur un instantané à jour.</small><div class="etaye-ligne">{btn("Relire les sources", "discret")}</div></div><span></span></li>'
    if mode == "brouillon-modifie":
        l5 = f'<li>{bl()}<div><strong>Le brouillon a changé pendant la relecture</strong><small>Gabarit : une révision plus récente existe. La relecture reprend sur cette révision ; les points déjà vérifiés sont à revoir.</small><div class="etaye-ligne">{btn("Afficher les changements", "discret", href_=href("S-22", "comparaison"))}</div></div><span></span></li>'
    return f'<ul class="etaye-controle">{l5}{l1}{l2}{l3}{l4}</ul>'


def etapes(mode):
    valide = mode in ("valide", "publie", "echec-publication")
    pub = mode == "publie"
    if valide:
        e1 = (f'<li data-etat="fait"><span class="etaye-deuxtemps__num">Étape 1</span><h2 class="etaye-deuxtemps__titre">Validation</h2>'
              f'<p>{st("business", "VALIDATED")} Le {N(dh_pro(PV2["validatedAt"]))} par {NORA} · version figée</p><p class="etaye-2">La validation ne rend rien visible pour la patiente.</p></li>')
    else:
        blocked = mode in ("contradiction", "source-manquante", "perime", "brouillon-modifie")
        e1 = (f'<li aria-current="step"><span class="etaye-deuxtemps__num">Étape 1 · en cours</span><h2 class="etaye-deuxtemps__titre">Validation</h2>'
              f'<p>{st("business", "IN_REVIEW")} Relecture par {NORA}</p>'
              f'<p class="etaye-2">{"Validation impossible tant qu’un point bloquant reste ouvert." if blocked else "Valider fige cette version. Rien n’est encore partagé."}</p>'
              f'<div class="etaye-ligne">{btn("Valider cette version", "valider", "valide", disabled=blocked, href_=href("I-16"))}{btn("Renvoyer en rédaction", "secondaire")}</div></li>')
    if pub:
        e2 = (f'<li data-etat="fait"><span class="etaye-deuxtemps__num">Étape 2</span><h2 class="etaye-deuxtemps__titre">Publication pour Claire Martin</h2>'
              f'<p>{st("publication", "PUBLISHED")} Le {N(dh_pro(PUB2["publishedAt"]))} par {NORA} · {PUB2["id"]}</p>'
              f'<p class="etaye-2">Remplace {PUB1["id"]} du {N(dh_pro(PUB1["publishedAt"]))}. 3 extraits partagés, aucune pièce jointe.</p>'
              f'<div class="etaye-ligne">{btn("Aperçu de la page patiente", "secondaire", "voir", href_=href("S-29", "v2"))}{btn("Remplacer ou retirer la publication", "discret", href_=href("I-18", "retrait"))}</div></li>')
    elif valide:
        e2 = (f'<li aria-current="step"><span class="etaye-deuxtemps__num">Étape 2 · disponible</span><h2 class="etaye-deuxtemps__titre">Publication pour Claire Martin</h2>'
              f'<p>{st("publication", None)} Action distincte, avec aperçu de ce que verra la patiente.</p>'
              f'<div class="etaye-ligne">{btn("Publier pour Claire Martin", "principal", "publier", href_=href("I-17"))}{btn("Garder en interne", "secondaire")}</div></li>')
    else:
        e2 = (f'<li data-etat="attente"><span class="etaye-deuxtemps__num">Étape 2 · après la validation</span><h2 class="etaye-deuxtemps__titre">Publication pour Claire Martin</h2>'
              f'<p>Une action distincte, avec la liste de ce que verra la patiente. Aucune publication n’est créée par la validation.</p></li>')
    return f'<ol class="etaye-deuxtemps" aria-label="Deux étapes distinctes">{e1}{e2}</ol>'


def s23(mode="validation"):
    if mode == "brief":
        return s23_brief()
    bande = ""
    if mode == "valide":
        bande = toast(f"Version 2 validée à {h_pro(PV2['validatedAt'])} · rien n’est encore visible pour Claire Martin")
    if mode == "echec-publication":
        bande = bandeau("attention", "La publication n'a pas abouti", f"Gabarit : aucune publication n'a été créée. Claire Martin ne voit pas la version 2 ; la publication du {N(dh_pro(PUB1['publishedAt']))} (version 1) reste celle qu'elle peut lire.",
                        btn("Réessayer la publication", "secondaire", "relancer", href_=href("I-17")), role="alert")
    if mode == "perime":
        bande = bandeau("attention", "Les sources ont changé pendant la relecture", "Gabarit : cette version ne peut être ni validée ni publiée avant une nouvelle lecture des sources modifiées.", role="alert")
    bus = "VALIDATED" if mode in ("valide", "publie", "echec-publication") else "IN_REVIEW"
    pub = "PUBLISHED" if mode == "publie" else None
    ax = axes(bus, "STALE" if mode == "perime" else "CURRENT", pub)
    side = panneau("Points de relecture", points(mode)) + panneau("Relecteur", f'<dl class="etaye-def"><dt>Nom</dt><dd>{NORA}</dd><dt>Droits</dt><dd>Valider les documents · Publier pour les patients<br><span class="etaye-3" style="font-size:13px">Deux droits vérifiés séparément</span></dd></dl>')
    body = f'{ax}{etapes(mode)}<div class="etaye-cols etaye-cols--2"><div class="etaye-pile">{doc_v2(mode)}</div><div class="etaye-pile">{side}</div></div>'
    titre = f"{EXPL} · version 2"
    return ecran(CL, titre, body, meta="Relecture humaine, puis publication séparée · réservé aux professionnels jusqu'à publication", bande=bande)


def s23_brief():
    order = ["SRC-A-005", "SRC-A-001", "SRC-A-002", "SRC-A-003"]
    t = BRIEF["text"]
    import re
    k = {"SRC-A-005": 1, "SRC-A-001": 2, "SRC-A-002": 3, "SRC-A-003": 4}
    html = re.sub(r"\s*\[([^\]]+)\]", lambda m: "".join(repere(k[x.strip()]) for x in m.group(1).split(",")), esc(t))
    doc = f'<div class="etaye-redaction etaye-redaction--lecture" role="document"><div class="etaye-redaction__barre"><span>Brief de préparation · version {BRIEF["version"]} · généré le {N(dh_pro(BRIEF["generatedAt"]))}</span><span>Réservé aux professionnels</span></div><p>{html}</p></div>'
    e = (f'<ol class="etaye-deuxtemps"><li aria-current="step"><span class="etaye-deuxtemps__num">Relecture interne</span><h2 class="etaye-deuxtemps__titre">Relire le brief</h2>'
         f'<p>{st("business", "DRAFT")} Brouillon généré par la tâche AI-A-001</p><div class="etaye-ligne">{btn("Soumettre à la relecture", "principal")}{btn("Relire et corriger", "secondaire", href_=href("S-21"))}</div></li>'
         f'<li data-etat="attente"><span class="etaye-deuxtemps__num">Publication</span><h2 class="etaye-deuxtemps__titre">Non publiable</h2>'
         f'<p>Un brief de préparation est réservé aux professionnels, même validé. Aucune action de publication n\'existe pour ce document.</p></li></ol>')
    pts = (f'<ul class="etaye-controle"><li>{ic("info", 18, "etaye-2")}<div>Information manquante<small>Traitements déclarés actuels</small></div><span></span></li>'
           f'<li>{ic("info", 18, "etaye-2")}<div>Sources discordantes<small>SRC-A-001 (14/09/2026) et SRC-A-005 (02/11/2026)</small></div><span></span></li></ul>')
    body = f'{axes("DRAFT", BRIEF["freshness"], extra="")}{e}<div class="etaye-cols etaye-cols--2"><div class="etaye-pile">{doc}</div><div class="etaye-pile">{panneau("Points de relecture", pts)}</div></div>'
    return ecran(CL, "Brief de préparation · version 1", body, meta="Pour la consultation du 03/11/2026 10:30 · BRIEF-A-001-v1", fil_=fil(CL, "Briefs"), actif="Briefs")


# ═════════════════════════════════ I-16 validate ═════════════════════════════════
def i16(mode="ouvert"):
    fond = s23("contradiction" if mode == "bloque" else "validation")
    recap = (f'<dl class="etaye-def"><dt>Document</dt><dd>{EXPL} · version 2</dd><dt>Patiente</dt><dd>Claire Martin · naissance {N("18/04/1978")} · R-A-001</dd>'
             f'<dt>Sources relues</dt><dd>SRC-A-001, SRC-A-005, SRC-A-006</dd><dt>Manque</dt><dd>Traitements actuels · signalé dans le texte</dd>'
             f'<dt>Discordance</dt><dd>Allergies · non tranchée, signalée</dd><dt>Validée par</dt><dd>{NORA}</dd></dl>')
    att = choix("i16-att", [("J'ai relu cette version et ses sources. Elle ne présente aucune information manquante comme un fait.", None)], typ="checkbox",
                checked=(0,) if mode in ("envoi", "erreur") else ())
    info = bandeau("info", "", "La version validée est figée. Elle n'est pas visible pour Claire Martin : la publication est une étape distincte.")
    top = ""
    if mode == "bloque":
        top = bandeau("attention", "Validation impossible pour l'instant", f"Une affirmation non soutenue reste dans le texte : « {esc(PV2['aiOriginalText'])} ». Corrigez-la ou retirez-la, puis revenez valider.", btn("Corriger le texte", "secondaire", href_=href("S-21")), role="alert")
    if mode == "erreur":
        top = bandeau("attention", "La validation n'a pas été confirmée", "Le service n'a pas répondu. Nous avons vérifié : la version n'est pas validée et rien n'a été partagé. Vous pouvez réessayer.", role="alert")
    corps = f'<div class="etaye-pile">{top}{recap}{att}{info}</div>'
    busy = mode == "envoi"
    pied = btn("Poursuivre la relecture", "secondaire", disabled=busy) + btn("Validation en cours…" if busy else ("Réessayer la validation" if mode == "erreur" else "Valider la version"), "valider", "valide", disabled=busy or mode == "bloque")
    return dialogue(fond, "Valider cette version", corps, pied, intro=f"{EXPL} · version 2 · relecture en cours")


# ═════════════════════════════════ I-17 publish ═════════════════════════════════
def apercu_patient(texte=None):
    t = texte or PV2["text"]
    return f'<div class="etaye-vu-patient" aria-label="Ce que verra la patiente"><span class="etaye-vu-patient__titre">Ce que verra Claire Martin</span><strong style="font:500 15px/22px var(--font-texte)">Informations après votre consultation du 20 octobre 2026</strong><p>{esc(t)}</p></div>'


def i17(mode="ouvert"):
    if mode == "brief":
        fond = s23_brief()
        corps = bandeau("neutre", "Ce document ne peut pas être publié", "Un brief de préparation est destiné aux professionnels. Le lien suivi ne correspond à aucune action autorisée ; rien n'a été partagé.", icon="verrou")
        return dialogue(fond, "Publication impossible", corps, btn("Revenir au brief", "secondaire"), intro="BRIEF-A-001-v1 · réservé aux professionnels")
    if mode == "perime":
        fond = s24("perime")
        corps = (bandeau("attention", "Cette version ne peut pas être publiée à nouveau", f"La version 1 est périmée depuis le {N(dh_pro(PV1['staleSince']))} : nouvelle déclaration de la patiente (SRC-A-005). Préparez et validez une nouvelle version avant toute publication.", role="alert")
                 + NOTE("Instantané V-STALE à 12:00."))
        return dialogue(fond, "Publier pour Claire Martin", corps, btn("Garder en interne", "secondaire") + btn("Publier pour le patient", "principal", "publier", disabled=True),
                        intro=f"{EXPL} · version 1 · validée, sources modifiées")
    fond = s23("valide")
    grants = "".join(f'<li>{ic("valide", 18, "etaye-controle__ok")}<div>{esc(g["publicLabel"])} · {N(d_court(S(g["sourceId"])["occurredAt"]))}<small>« {esc(g["approvedExcerpt"])} »</small></div><span class="etaye-num etaye-3" style="font-size:12px">{g["sourceId"]}</span></li>' for g in PUB2["sourceGrants"])
    aud = (f'<div class="etaye-audience"><div class="etaye-audience__qui">{ic("compte", 18)}Claire Martin · naissance {N("18/04/1978")}</div>'
           f'<div class="etaye-2" style="font-size:14px">Espace patient activé (ACC-CLAIRE) · dossier R-A-001 · Cabinet des Tilleuls</div></div>')
    cons = (f'<ul class="etaye-controle">'
            f'<li>{ic("remplacer", 18, "etaye-2")}<div>Remplace la publication du {N(dh_pro(PUB1["publishedAt"]))}<small>Claire Martin ne pourra plus ouvrir la version 1 ; elle verra un renvoi vers la version actuelle.</small></div><span></span></li>'
            f'<li>{ic("courrier", 18, "etaye-2")}<div>Un e-mail sans contenu médical l’informe<small>Envoyé après l’enregistrement de la publication (M-05)</small></div><span></span></li>'
            f'<li>{ic("verrou", 18, "etaye-2")}<div>Reste interne<small>Texte généré, notes de relecture, sources non partagées</small></div><span></span></li></ul>')
    top = ""
    if mode == "erreur":
        top = bandeau("attention", "La publication n'a pas été créée", "Le service n'a pas confirmé la publication. Nous avons vérifié : rien n'est visible pour Claire Martin, et la version 1 reste publiée. Vous pouvez réessayer.", role="alert")
    corps = (f'<div class="etaye-pile">{top}{aud}<dl class="etaye-def"><dt>Version</dt><dd>{EXPL} · version 2 · {st("business", "VALIDATED")} {N(dh_pro(PV2["validatedAt"]))}</dd>'
             f'<dt>Sources</dt><dd>{st("freshness", "CURRENT")}</dd><dt>Pièces jointes</dt><dd>Aucune</dd></dl>{apercu_patient()}'
             f'<div class="ui-libelle etaye-2">Extraits partagés (3)</div><ul class="etaye-controle">{grants}</ul>'
             f'<div class="ui-libelle etaye-2">Conséquences</div>{cons}</div>')
    busy = mode == "envoi"
    pied = btn("Garder en interne", "secondaire", disabled=busy) + btn("Publication en cours…" if busy else ("Réessayer la publication" if mode == "erreur" else "Publier pour le patient"), "principal", "publier", disabled=busy)
    return dialogue(fond, "Publier pour Claire Martin", corps, pied, intro=f"{EXPL} · version 2 validée", large=True)


# ═════════════════════════════════ S-24 stale, replaced or withdrawn ═════════════════════════════════
def s24(mode="perime"):
    v = PV1 if mode in ("perime", "remplace", "lien-ancien") else PV2
    vn = v["version"]
    lin = lignee_expl(vn, pub2="WITHDRAWN" if mode == "retire" else "PUBLISHED")
    vu, acts, bande = "", "", ""
    if mode == "perime":
        ax = axes("VALIDATED", "STALE", "PUBLISHED")
        bande = bandeau("attention", "Les sources ont changé depuis cette version",
                        f"Le {N(dh_pro(PV1['staleSince']))}, Claire Martin a envoyé une préparation de visite : elle signale un souvenir de réaction à la pénicilline (SRC-A-005). "
                        f"Cette déclaration contredit une source utilisée par la version 1 (SRC-A-001, absence déclarée le 14/09/2026). La version reste validée ; elle n'est plus à jour.", role="alert")
        vu = (f'<div class="etaye-vu-patient"><span class="etaye-vu-patient__titre">Ce que voit Claire Martin</span><p style="font-family:var(--font-texte);font-size:15px;line-height:24px">Toujours la version 1, publiée le {N(dh_pro(PUB1["publishedAt"]))}. Rien ne change pour elle tant qu\'aucune nouvelle version n\'est publiée ou que celle-ci n\'est pas retirée.</p></div>')
        acts = (btn("Comparer avec les sources", "secondaire", "comparer", href_=href("S-22", "comparaison")) + btn("Remplacer ou retirer la publication", "secondaire", href_=href("I-18"))
                + btn("Préparer une nouvelle version", "principal", href_=href("S-21")))
        lin = (f'<ol class="etaye-lignee"><li aria-current="true"><div class="etaye-lignee__titre">Version 1 {st("business", "VALIDATED")}{st("publication", "PUBLISHED")}</div>'
               f'<span>Validée le {N(dh_pro(PV1["validatedAt"]))} · publiée le {N(dh_pro(PUB1["publishedAt"]))} ({PUB1["id"]})</span><span>{st("freshness", "STALE")} · signalé le {N(dh_pro(PV1["staleSince"]))}</span></li></ol>'
               + NOTE("Instantané V-STALE à 12:00 : la version 2 n'existe pas encore."))
    elif mode == "remplace":
        ax = axes("VALIDATED", "STALE", "REPLACED")
        bande = bandeau("neutre", "Version remplacée", f"La publication {PUB1['id']} a été remplacée le {N(dh_pro(PUB2['publishedAt']))} par la version 2 ({PUB2['id']}). Le texte ci-dessous n'est plus celui que lit la patiente.",
                        icon="remplacer")
        vu = (f'<div class="etaye-vu-patient"><span class="etaye-vu-patient__titre">Ce que voit Claire Martin</span><p style="font-family:var(--font-texte);font-size:15px;line-height:24px">L\'ancien lien affiche « Cette version a été remplacée » et renvoie vers la version actuelle.</p>'
              f'<div class="etaye-ligne">{lien("Aperçu de la page patiente", href("S-29", "remplacee"))}</div></div>')
        acts = btn("Ouvrir la version actuelle", "principal", href_=href("S-23", "publie"))
    elif mode == "retire":
        ax = axes("VALIDATED", "CURRENT", "WITHDRAWN")
        bande = bandeau("neutre", "Publication retirée", f"Variante V-WITHDRAWN : {PUB2['id']} a été retirée par {NORA}. Motif interne : saisi lors du retrait (non fourni par le jeu de démonstration). Claire Martin ne peut plus l'ouvrir ni la télécharger.", icon="retirer")
        vu = (f'<div class="etaye-vu-patient"><span class="etaye-vu-patient__titre">Ce que voit Claire Martin</span><p style="font-family:var(--font-texte);font-size:15px;line-height:24px">« Cette information n\'est plus disponible. » Le motif interne n\'est pas affiché. Les copies déjà téléchargées ne peuvent pas être effacées.</p>'
              f'<div class="etaye-ligne">{lien("Aperçu de la page patiente", href("S-29", "retiree"))}</div></div>')
        acts = btn("Préparer une nouvelle version", "principal", href_=href("S-21"))
    elif mode == "lien-ancien":
        ax = axes("VALIDATED", "STALE", "REPLACED")
        bande = bandeau("neutre", "Ce lien de téléchargement n'est plus actif", f"Il visait le PDF de la version 1. Cette version a été remplacée le {N(dh_pro(PUB2['publishedAt']))} : le fichier n'est plus distribué. Vous pouvez lire la version 1 dans l'historique interne.", icon="lien")
        acts = btn("Lire la version 1 dans l'historique", "secondaire", href_=href("S-22", "lien-remplace")) + btn("Ouvrir la version actuelle", "principal", href_=href("S-23", "publie"))
    else:  # piece-revoquee
        ax = axes("VALIDATED", "CURRENT", "PUBLISHED")
        bande = bandeau("neutre", "Accès à une pièce jointe retiré", "Gabarit : aucune pièce jointe n'est partagée dans le jeu etaye-design-v1. Cet état montre une pièce jointe dont l'accès a été retiré alors que la publication reste en ligne.", icon="verrou")
        vu = (f'<div class="etaye-vu-patient"><span class="etaye-vu-patient__titre">Ce que voit Claire Martin</span><p style="font-family:var(--font-texte);font-size:15px;line-height:24px">Le texte publié reste lisible. La ligne de la pièce jointe indique « Document retiré par votre cabinet » ; les copies déjà téléchargées ne peuvent pas être effacées.</p></div>')
        acts = btn("Gérer les pièces jointes", "secondaire")
    txt = f'<div class="etaye-redaction etaye-redaction--lecture" role="document"><div class="etaye-redaction__barre"><span>Version {vn} · validée le {N(dh_pro(v["validatedAt"]))}</span><span>{"Texte historique · non actuel" if mode in ("perime", "remplace", "lien-ancien") else "Texte de la version 2"}</span></div><p>{esc(v["text"])}</p></div>'
    left = f'<div class="etaye-pile">{txt}{vu}</div>'
    body = f'{ax}<div class="etaye-ligne" style="flex-wrap:wrap">{acts}</div><div class="etaye-cols etaye-cols--2">{left}<div class="etaye-pile">{panneau("Versions", lin)}</div></div>'
    return ecran(CL, f"{EXPL} · version {vn}", body, meta="Cycle de vie de la publication · motifs internes visibles par les professionnels seulement", bande=bande)


# ═════════════════════════════════ I-18 replace or withdraw ═════════════════════════════════
def i18(mode="ouvert"):
    retrait = mode in ("retrait", "erreur")
    if retrait:
        cur = (f'<dl class="etaye-def"><dt>Publication</dt><dd>{PUB2["id"]} · {st("publication", "PUBLISHED")}</dd><dt>Version</dt><dd>{EXPL} · version 2 · validée le {N(dh_pro(PV2["validatedAt"]))}</dd>'
               f'<dt>Publiée</dt><dd>{N(dh_pro(PUB2["publishedAt"]))} par {NORA}</dd><dt>Pour</dt><dd>Claire Martin · espace patient activé</dd></dl>')
        meta = "Variante V-WITHDRAWN · point de départ : PUB-A-002 publiée"
    else:
        cur = (f'<dl class="etaye-def"><dt>Publication</dt><dd>{PUB1["id"]} · {st("publication", "PUBLISHED")}</dd><dt>Version</dt><dd>{EXPL} · version 1 · {st("freshness", "STALE")}</dd>'
               f'<dt>Publiée</dt><dd>{N(dh_pro(PUB1["publishedAt"]))} par {NORA}</dd><dt>Pour</dt><dd>Claire Martin · espace patient activé</dd></dl>')
        meta = f"Instantané entre la validation de la version 2 ({h_pro(PV2['validatedAt'])}) et sa publication ({h_pro(PUB2['publishedAt'])})"
    rep_dispo = not retrait
    sel_rep = mode in ("remplacer", "conflit")
    sel_ret = retrait
    opt_rep = (f'<div class="etaye-option" role="radio" aria-checked="{"true" if sel_rep else "false"}"{" aria-disabled=\"true\"" if not rep_dispo else ""}>'
               f'<label><input type="radio" name="i18"{" checked" if sel_rep else ""}{" disabled" if not rep_dispo else ""}><span>Remplacer par une version validée</span></label>'
               + (f'<ul><li>Nouvelle version : version 2 · {st("business", "VALIDATED")} {N(dh_pro(PV2["validatedAt"]))} · {st("freshness", "CURRENT")}</li><li>Remplacement immédiat : jamais deux versions actuelles</li><li>La patiente est informée sans contenu médical (M-05)</li></ul>'
                  if rep_dispo else '<ul><li>Aucune version plus récente n’est validée</li></ul>')
               + (apercu_patient() if sel_rep else "") + '</div>')
    err = mode == "erreur"
    motif = ""
    if sel_ret:
        motif = champ("Motif du retrait", "", "i18-motif", kind="textarea", rows=3, required=True, focus=err,
                      error="Indiquez pourquoi l’accès est retiré." if err else None,
                      help_=None if err else "Visible par les professionnels du cabinet, jamais par la patiente.")
    opt_ret = (f'<div class="etaye-option" role="radio" aria-checked="{"true" if sel_ret else "false"}"><label><input type="radio" name="i18"{" checked" if sel_ret else ""}><span>Retirer l’accès</span></label>'
               f'<ul><li>La patiente ne peut plus ouvrir ni télécharger ce contenu</li><li>Elle voit « Cette information n’est plus disponible », sans le motif</li><li>Les copies déjà téléchargées et l’e-mail déjà reçu ne peuvent pas être rappelés</li></ul>{motif}</div>')
    top = resume_erreurs([("i18-motif", "Motif du retrait : à renseigner")]) if err else ""
    if mode == "conflit":
        top = bandeau("attention", "La publication a changé depuis l'ouverture de cette page", "Gabarit : une autre action l'a modifiée. Rien n'a été remplacé ni retiré. Rechargez pour voir la situation actuelle avant de choisir.",
                      btn("Recharger", "secondaire", "relancer"), role="alert")
    if mode == "termine":
        body = (toast(f"Publication remplacée à {h_pro(PUB2['publishedAt'])}")
                + panneau("Résultat", f'<dl class="etaye-def"><dt>{PUB1["id"]}</dt><dd>{st("publication", "REPLACED")} version 1 · plus lisible par la patiente</dd>'
                                       f'<dt>{PUB2["id"]}</dt><dd>{st("publication", "PUBLISHED")} version 2 · {N(dh_pro(PUB2["publishedAt"]))} · {NORA}</dd>'
                                       f'<dt>Patiente</dt><dd>Claire Martin voit la version 2 et 3 extraits partagés · e-mail M-05 mis en file après l’enregistrement</dd></dl>',
                          lien("Historique des versions", href("S-22", "comparaison"))))
        head = entete("Remplacer ou retirer la publication", meta, btn("Revenir au document", "secondaire", href_=href("S-23", "publie")), fil(CL, f'<a href="{href("S-24")}">{EXPL}</a>'))
        return pro_shell("Patients", f'<div class="etaye-page etaye-page--consult">{head}{body}</div>', "nora", banner=banniere(CL))
    if sel_ret:
        prim = btn("Retirer l’accès", "retrait", "retirer")
    else:
        prim = btn("Remplacer la publication", "principal", "remplacer", disabled=not sel_rep or mode == "conflit")
    pied = f'<div class="etaye-ligne" style="justify-content:flex-end">{btn("Conserver la publication", "secondaire")}{prim}</div>'
    form = f'<div class="etaye-pile">{top}{panneau("Publication actuelle", cur)}<fieldset class="etaye-pile" style="border:0;padding:0;margin:0"><legend class="ui-section" style="margin-bottom:var(--espace-3)">Que voulez-vous faire ?</legend>{opt_rep}{opt_ret}</fieldset>{pied}</div>'
    head = entete("Remplacer ou retirer la publication", meta, "", fil(CL, f'<a href="{href("S-24")}">{EXPL}</a>'))
    return pro_shell("Patients", f'<div class="etaye-page etaye-page--consult" style="max-width:960px">{head}{form}</div>', "nora", banner=banniere(CL))


# ═════════════════════════════════ I-05 inline structured notes ═════════════════════════════════
def i05(mode="ouvert"):
    ed = i05_editeur(mode)
    others = (vide_sec("rapporte", "Rapporté par le patient", "Non renseigné.") + vide_sec("observe", "Observé au cabinet", "Non renseigné."))
    first = ed if mode in ("echec", "annuler", "enregistre") else vide_sec("motif", "Motif", "Non renseigné.") + ed
    notes = f'<div class="etaye-notes">{first}{others}</div>'
    etats = {"motif": "Enregistré" if mode == "enregistre" else ("En cours de saisie" if mode in ("echec", "annuler") else "Non renseigné")}
    body = f'<div class="etaye-cols etaye-cols--3">{plan(etats, "motif" if mode in ("echec", "annuler", "enregistre") else "rapporte")}{notes}<div class="etaye-pile">{louis_aside()}</div></div>'
    meta = f'{statut("neutre", "En cours", "horloge")} Commencée le {N(dh_pro(C2["startedAt"]))} par {SAMI} · {C2["id"]}'
    return ecran(LO, "Consultation", body, "sami_a", meta=meta, actions=btn("Terminer la consultation", "principal", href_=href("S-19", "terminer")))


SCREENS = [
    {"id": "S-19", "name": "Consultation et notes structurées", "states": [
        {"slug": "en-cours", "label": "IN_PROGRESS — CONS-A-002 Louis Bernard, Dr Sami Rahmani since 14:10, no appointment linked, every section 'Non renseigné'", "html": s19()},
        {"slug": "echec", "label": "Failed save — motif typed from APT-A-002 reason kept, nothing saved", "html": s19("echec")},
        {"slug": "conflit", "label": "Concurrent edit — V-CONFLICT variant, nothing overwritten, link to I-21", "html": s19("conflit")},
        {"slug": "session", "label": "Session expired before save — V-SESSION, entry kept on device", "html": s19("session")},
        {"slug": "lecture-seule", "label": "Membership deactivated — read-only immediately (template)", "html": s19("lecture-seule")},
        {"slug": "terminer", "label": "Completion guard dialog — motif missing blocks completion (proposed guard); appointment stays independent", "html": s19("terminer")},
        {"slug": "termine", "label": "COMPLETED — CONS-A-001 Claire Martin 20 Oct 10:35, read-only, dated sources, corrected weight lineage", "html": s19("termine")},
        {"slug": "annulee", "label": "CANCELLED — template, notes read-only, appointment unchanged", "html": s19("annulee")},
        {"slug": "longue", "label": "Long notes — V-LONG variant: every dated Claire source quoted in full (no new clinical content)", "html": s19("longue")},
    ]},
    {"id": "I-05", "name": "Saisir des informations pour la consultation", "states": [
        {"slug": "ouvert", "label": "Inline editor — Louis allergies, unknown/negative/not asked distinct, origin and date", "html": i05()},
        {"slug": "erreur", "label": "Validation — 'Présente' without text: field error and summary, no invented negative", "html": i05("erreur")},
        {"slug": "echec", "label": "Network failure — motif kept, nothing saved", "html": i05("echec")},
        {"slug": "annuler", "label": "Cancel with unsaved entry — explicit discard, safe choice first", "html": i05("annuler")},
        {"slug": "enregistre", "label": "Saved — motif with author, date and origin (template)", "html": i05("enregistre")},
    ]},
    {"id": "S-20", "name": "Rédaction du compte rendu", "states": [
        {"slug": "brouillon", "label": "DRAFT v2 variant from REPORT-A-001-v1 — exact v1 text, saved, sources panel, v1 unchanged", "html": s20()},
        {"slug": "nouveau", "label": "New report for CONS-A-002 (Louis) — missing-data statements only, no citable source, no generated text", "html": s20("nouveau")},
        {"slug": "sale", "label": "Dirty — human correction labelled, supported by SRC-A-003 date", "html": s20("sale")},
        {"slug": "enregistrement", "label": "Saving — actions disabled, no success shown yet", "html": s20("enregistrement")},
        {"slug": "echec", "label": "Save failed — text kept in the editor", "html": s20("echec")},
        {"slug": "enregistre", "label": "Saved — human correction kept and labelled", "html": s20("enregistre")},
        {"slug": "source-retiree", "label": "Cited source removed from record — sentence marked for review (template)", "html": s20("source-retiree")},
        {"slug": "non-soutenu", "label": "Unsupported citation — variant: blood pressure sentence cites the lab document SRC-A-003", "html": s20("non-soutenu")},
        {"slug": "conflit", "label": "Stale write rejected — V-CONFLICT variant, link to I-21", "html": s20("conflit")},
        {"slug": "lecture-seule", "label": "REPORT-A-001-v1 VALIDATED — read-only, 'Créer une nouvelle version' instead of editing", "html": s20("lecture-seule")},
    ]},
    {"id": "I-21", "name": "Résoudre un conflit de version", "states": [
        {"slug": "ouvert", "label": "Stale write explained — your copy vs saved version, authors, no automatic overwrite (V-CONFLICT variant)", "html": i21()},
        {"slug": "comparaison", "label": "Readable side-by-side diff, text labels, sequential on mobile", "html": i21("comparaison")},
        {"slug": "copie", "label": "Local copy kept in memory for the session, not saved", "html": i21("copie")},
    ]},
    {"id": "S-22", "name": "Historique et comparaison des versions", "states": [
        {"slug": "comparaison", "label": "PATIENT-A-001 v1 vs v2 — Supprimé/Ajouté labels, source set change, origin layers", "html": s22()},
        {"slug": "generation", "label": "Origin first — source facts, rejected generated text, human correction and reason", "html": s22("generation")},
        {"slug": "longue", "label": "Full lineage in order — v1, stale event, generated draft, corrected v2, publication", "html": s22("longue")},
        {"slug": "une-version", "label": "One version — REPORT-A-001-v1 only, nothing to compare", "html": s22("une-version")},
        {"slug": "source-manquante", "label": "Historic source excerpt unavailable (template)", "html": s22("source-manquante")},
        {"slug": "nouvelle-version", "label": "Concurrent new draft created while reading (variant)", "html": s22("nouvelle-version")},
        {"slug": "lecture-seule", "label": "Read-only history, no new-draft action (template)", "html": s22("lecture-seule")},
        {"slug": "lien-remplace", "label": "Followed a replaced publication link — v1 shown as history", "html": s22("lien-remplace")},
    ]},
    {"id": "S-23", "name": "Validation humaine puis publication", "states": [
        {"slug": "validation", "label": "IN_REVIEW PATIENT-A-001-v2 — checklist, two separate steps, publication not yet available", "html": s23()},
        {"slug": "contradiction", "label": "Blocked — unsupported generated statement still present (SC-UNSUPPORTED-DRAFT)", "html": s23("contradiction")},
        {"slug": "source-manquante", "label": "Blocked — one source could not be loaded (template)", "html": s23("source-manquante")},
        {"slug": "perime", "label": "Blocked — sources changed during review (template)", "html": s23("perime")},
        {"slug": "brouillon-modifie", "label": "Blocked — draft changed during review (template)", "html": s23("brouillon-modifie")},
        {"slug": "valide", "label": "VALIDATED 14:20, not published — step 2 offered separately", "html": s23("valide")},
        {"slug": "publie", "label": "PUBLISHED 14:25 as PUB-A-002, replaces PUB-A-001", "html": s23("publie")},
        {"slug": "brief", "label": "Clinician brief BRIEF-A-001-v1 — internal review only, no publication action", "html": s23("brief")},
        {"slug": "echec-publication", "label": "Publication failed — still VALIDATED and internal, v1 remains published (template)", "html": s23("echec-publication")},
    ]},
    {"id": "I-16", "name": "Valider la version relue", "states": [
        {"slug": "ouvert", "label": "Validate dialog — exact version, patient, sources, gaps, attestation; not patient-visible", "html": i16()},
        {"slug": "bloque", "label": "Blocked — unsupported statement remains, primary disabled", "html": i16("bloque")},
        {"slug": "envoi", "label": "Submitting — controls disabled, no success yet", "html": i16("envoi")},
        {"slug": "erreur", "label": "Ambiguous response checked — not validated, nothing shared (template)", "html": i16("erreur")},
    ]},
    {"id": "I-17", "name": "Publier pour la patiente", "states": [
        {"slug": "ouvert", "label": "Publish dialog — audience, exact version, patient preview, 3 approved excerpts, no attachment, consequences", "html": i17()},
        {"slug": "envoi", "label": "Publishing — controls disabled", "html": i17("envoi")},
        {"slug": "erreur", "label": "Publication failed — nothing visible, v1 still published (template)", "html": i17("erreur")},
        {"slug": "brief", "label": "Forbidden — clinician brief cannot be published", "html": i17("brief")},
        {"slug": "perime", "label": "Blocked — STALE v1 cannot be published again (V-STALE 12:00)", "html": i17("perime")},
    ]},
    {"id": "S-24", "name": "Contenu périmé, remplacé ou retiré", "states": [
        {"slug": "perime", "label": "V-STALE 12:00 — v1 VALIDATED + STALE + still PUBLISHED, reason SRC-A-005 vs SRC-A-001", "html": s24()},
        {"slug": "remplace", "label": "PUB-A-001 REPLACED by PUB-A-002 at 14:25 — old text not presented as current", "html": s24("remplace")},
        {"slug": "retire", "label": "V-WITHDRAWN — PUB-A-002 withdrawn, internal reason for professionals only, copies not recalled", "html": s24("retire")},
        {"slug": "lien-ancien", "label": "Old download link of v1 — inactive, history and current version offered", "html": s24("lien-ancien")},
        {"slug": "piece-revoquee", "label": "Attachment access revoked (template — no attachment granted in the dataset)", "html": s24("piece-revoquee")},
    ]},
    {"id": "I-18", "name": "Remplacer ou retirer la publication", "states": [
        {"slug": "ouvert", "label": "Choice page — PUB-A-001 (v1 STALE) between 14:20 and 14:25, replace or withdraw, nothing preselected", "html": i18()},
        {"slug": "remplacer", "label": "Replace selected — v2 validated, patient preview, atomic replacement", "html": i18("remplacer")},
        {"slug": "termine", "label": "Replaced at 14:25 — PUB-A-001 REPLACED, PUB-A-002 PUBLISHED, M-05 queued", "html": i18("termine")},
        {"slug": "retrait", "label": "Withdraw PUB-A-002 — V-WITHDRAWN start, reason required, no newer version to replace with", "html": i18("retrait")},
        {"slug": "erreur", "label": "Withdrawal reason missing — error summary and field error", "html": i18("erreur")},
        {"slug": "conflit", "label": "Publication changed meanwhile — nothing replaced (template)", "html": i18("conflit")},
    ]},
]

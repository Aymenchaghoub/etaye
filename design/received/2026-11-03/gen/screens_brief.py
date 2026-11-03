"""Preparation and AI drafting (batch 7a) — S-17 practitioner view of the visit preparation, S-18 sourced brief,
I-14 generate a draft, S-21 claim-by-claim review of a generated draft, I-15 review and correct, S-40 processing
activity and task detail, I-23 failed processing and retry.

Data: synthetic-data.json only. PREP-A-001 (Claire, 2 Nov 09:10), BRIEF-A-001-v1 (DRAFT, AI-A-001 14:04:50→14:05:00),
PATIENT-A-001-v2 (aiOriginalText rejected and corrected, SC-UNSUPPORTED-DRAFT), AI-A-002 (Louis, PROVIDER_TIMEOUT
14:30:30, retryable). Louis Bernard has no dated source and no completed consultation (V-EMPTY).
A processing success creates a draft only; nothing here validates or publishes. States without a dataset example
are labelled "template" or "variant" and add no clinical value.
"""
from lib import *

CL, LO = "R-A-001", "R-A-002"
S = lambda i: by_id("sources", i)
PREP = by_id("preparations", "PREP-A-001")
A1 = by_id("appointments", "APT-A-001")
BRIEF = by_id("documentVersions", "BRIEF-A-001-v1")
PV2 = by_id("documentVersions", "PATIENT-A-001-v2")
T1, T2 = by_id("aiTasks", "AI-A-001"), by_id("aiTasks", "AI-A-002")
NORA, SAMI = "Dr Nora Lefebvre", "Dr Sami Rahmani"
N = lambda x: f'<span class="etaye-num">{x}</span>'
NOTE = lambda t: f'<p class="etaye-3" style="font-size:13px;margin:0">{t}</p>'
# Dataset correctionReason is stored in English; the UI shows the same French rendering as screens_consult.
CORR_FR = ("Ne pas transformer une déclaration négative ancienne en fait durable ; conserver la déclaration plus récente "
           "de la patiente et le caractère manquant de l'information.")
INTERNE = statut("neutre", "Réservé aux professionnels · jamais publié", "verrou")
PROVIDER = "Fixture déterministe de démonstration"


def etiq(label, kind=""):
    return f'<span class="etaye-etiquette{f" etaye-etiquette--{kind}" if kind else ""}">{esc(label)}</span>'


def axes(*items):
    return f'<div class="etaye-axes">{"".join(f"<span class=\"etaye-axe\">{k} {v}</span>" for k, v in items)}</div>'


def fil(rid, *parts):
    out = [f'<a href="{href("S-07")}">Patients</a>', f'<a href="{href("S-09", "claire" if rid == CL else "louis")}">{esc(nom(rid, "famille"))}</a>'] + list(parts)
    return " › ".join(out)


def ecran(rid, titre, body, actor="nora", meta="", actions="", bande="", fil_=None, actif="Patients", counts=None):
    head = entete(titre, meta, actions, fil_ if fil_ is not None else fil(rid))
    return pro_shell(actif, f'<div class="etaye-page etaye-page--consult">{head}{bande}{body}</div>', actor,
                     banner=banniere(rid) if rid else None, counts=counts)


def src_row(sid, n=None, cite=True):
    s = S(sid)
    r = repere(n, seul=True, label=f"Source {n}") if n else '<span class="etaye-3">—</span>'
    return [r, f'{KIND_FR[s["kind"]]}<div class="etaye-3" style="font-size:12px">{esc(sid)} · version {s["version"]}</div>',
            N(dh_pro(s["occurredAt"])), N(dh_pro(s["recordedAt"])), esc(auteur(s)),
            "Citée" if cite else "Consultée, non citée"]


def snapshot_table():
    rows = []
    for i in BRIEF["sourceIds"]:
        x = S(i)
        rows.append([f'{KIND_FR[x["kind"]]}<div class="etaye-3" style="font-size:12px">{esc(i)} · version {x["version"]}</div>', N(dh_pro(x["occurredAt"])), N(dh_pro(x["recordedAt"])), esc(auteur(x))])
    return tableau(["Source", "Survenue", "Enregistrement", "Auteur"], rows, stack=True)


def sources_table(rows, caption="Sources du dossier utilisées"):
    return tableau(["Repère", "Source", "Survenue", "Enregistrement", "Auteur", "Dans le texte"], rows, stack=True, caption=caption)


# ═════════════════════════════════ S-17 visit preparation (practitioner view) ═════════════════════════════════
def qa(question, reponse, origine, extra=""):
    return (f'<section class="etaye-notes__section"><div class="etaye-notes__tete"><h3>{esc(question)}</h3>{origine}</div>'
            f'<div class="etaye-notes__corps">{reponse}{extra}</div></section>')


def s17(mode="claire"):
    s5, s1 = S("SRC-A-005"), S("SRC-A-001")
    if mode in ("non-soumise", "brouillon-patient"):
        if mode == "non-soumise":
            body = (vide("Aucune préparation reçue", "Louis Bernard n'a pas envoyé de préparation de visite. Le dossier ne contient pas d'autre déclaration datée.",
                         btn("Ouvrir le dossier", "secondaire", href_=href("S-09", "louis")))
                    + NOTE("Absence de préparation ≠ absence de symptôme ou de traitement : rien n'est déduit de ce vide. Variante V-EMPTY."))
            return ecran(LO, "Préparation de visite", body, actor="sami_a",
                         meta=f"Rendez-vous du {N(dh_pro(by_id('appointments', 'APT-A-002')['startsAt']))} · {SAMI}")
        body = (vide("Pas encore envoyée", "Une préparation n'est visible par le cabinet qu'après son envoi par la patiente. Les brouillons restent sur son espace.",
                     lien("Voir le rendez-vous", href("S-16", "claire")))
                + NOTE("Variante : lundi 2 novembre 2026 à 09:05, avant l'envoi de 09:10. Rien n'est affiché du brouillon enregistré à 09:02 côté patiente."))
        return ecran(CL, "Préparation de visite", body, meta=f"Rendez-vous du {N(dh_pro(A1['startsAt']))} · {NORA}")

    orig = statut("neutre", "Déclaration de la patiente", "compte")
    sujets = qa("Ce que la patiente souhaite aborder", f'<blockquote>« {esc(PREP["concerns"])} »</blockquote>', orig)
    longtxt = ""
    if mode == "longue":
        longtxt = ('<blockquote>« Je souhaite aussi savoir comment retrouver mes documents dans l\'espace patient, à qui m\'adresser au secrétariat '
                   'si je dois déplacer un rendez-vous, et si je peux envoyer une nouvelle version de cette préparation avant la consultation si je me souviens '
                   'd\'autres informations. »</blockquote>'
                   + NOTE("Texte administratif de longueur (variante V-LONG) : aucun contenu clinique ajouté."))
        sujets = qa("Ce que la patiente souhaite aborder", f'<blockquote>« {esc(PREP["concerns"])} »</blockquote>{longtxt}', orig)
    allerg = qa("Allergie ou réaction à un médicament",
                f'<div class="etaye-ligne"><strong>Oui</strong>{etat("PATIENT_REPORTED_UNCONFIRMED")}</div>'
                f'<blockquote>« {esc(s5["text"])} »{repere(1, label="Source 1 : SRC-A-005")}</blockquote>'
                f'<div class="etaye-ligne">{etat("UNKNOWN", "Nature de la réaction · inconnue")}</div>',
                orig,
                bandeau("attention", "Diffère d'une déclaration antérieure",
                        f'Le {N(d_court(s1["occurredAt"]))}{NNB}: « {esc(s1["text"])} »{repere(2, label="Source 2 : SRC-A-001")} Ne pas conclure à l\'absence d\'allergie ; à préciser en consultation.',
                        lien("Comparer les sources", href("I-27", "ouvert"))))
    trt_rep = (f'<div class="etaye-ligne">{etat("MISSING", "Sans réponse · non renseigné")}</div>'
               + NOTE("La patiente n'a pas répondu : ce n'est pas un « non »."))
    if mode == "corrigee":
        trt_rep = (f'<div class="etaye-ligne"><strong>« Je ne sais pas »</strong>{etiq("Modifié en version 2", "humain")}</div>'
                   + NOTE("Version 1 (02/11/2026 09:10) : sans réponse. « Je ne sais pas » reste une information inconnue, pas un « non »."))
    trt = qa("Traitements modifiés depuis la dernière visite", trt_rep, orig)

    lue = mode == "lue"
    bande = ""
    if mode == "corrigee":
        bande = bandeau("info", "Nouvelle version envoyée par la patiente",
                        "Version 2 (variante) : seule la réponse sur les traitements a changé. La version 1 du 02/11/2026 09:10 reste consultable et datée.",
                        lien("Voir la version 1", "#v1"))
    if mode == "lien-perime":
        bande = bandeau("attention", "Le rendez-vous lié a été déplacé",
                        "Préparation envoyée pour le 03/11/2026 10:30 ; le rendez-vous est maintenant le 03/11/2026 11:00 (variante, voir S-27). La préparation reste rattachée au rendez-vous déplacé.",
                        lien("Ouvrir le rendez-vous", href("S-16", "modifie")), role="alert")
    lecture = (statut("valide", "Lue par Dr Nora Lefebvre", "voir") + NOTE("Variante : la lecture est horodatée ; elle ne change pas l'auteur des réponses.")
               if lue else btn("Marquer comme lue", "secondaire", "voir"))
    meta = (f'Version 2 envoyée par Claire Martin (variante) · version 1 du {N(dh_pro(PREP["submittedAt"]))} · ' if mode == "corrigee"
            else f'Envoyée par Claire Martin le {N(dh_pro(PREP["submittedAt"]))} · version {PREP["version"]} · ') + f'pour le rendez-vous du {N(dh_pro(A1["startsAt"]))}' 
    acts = btn("Citer dans le brief", "secondaire", "preparation", href_=href("S-18", "brouillon")) + btn("Ouvrir l'original", "secondaire", "ouvrir", href_=href("I-09", "ouvert"))
    regle = NOTE("Le texte de la patiente ne peut pas être modifié par le cabinet. Toute reprise dans un brief ou un compte rendu garde son origine et sa date.")
    body = (f'<div class="etaye-pile" style="max-width:860px">{sujets}{allerg}{trt}'
            f'<div class="etaye-ligne">{lecture}</div>{regle}</div>')
    return ecran(CL, "Préparation de visite", body, meta=meta, actions=acts, bande=bande)


# ═════════════════════════════════ S-18 sourced brief ═════════════════════════════════
BRIEF_SRC = [("SRC-A-005", 1), ("SRC-A-001", 2), ("SRC-A-002", 3), ("SRC-A-003", 4)]


def brief_texte(mark_missing=True):
    r = lambda n: repere(n, label=f"Source {n}")
    gap = etiq("Manquant · aucune source", "manque") if mark_missing else ""
    return (f'Depuis la consultation du 20 octobre, Claire Martin a signalé un souvenir de réaction à la pénicilline{r(1)}. '
            f'Cette déclaration diffère de la mention du 14 septembre{r(2)}. '
            f'La liste actuelle des traitements n’est pas renseignée.{gap} '
            f'Les résultats de créatinine disponibles sont de {N("96")} puis {N(f"104{NB}µmol/L")}{r(3)}{r(4)}.')


def brief_doc(etat_txt="Brouillon généré · à relire", kind="brouillon", extra_meta=""):
    li = "".join(f'<li>{repere(n, seul=True)}<span>{KIND_FR[S(i)["kind"]]} · {N(d_court(S(i)["occurredAt"]))}</span></li>' for i, n in BRIEF_SRC)
    return (f'<div class="etaye-annote etaye-annote--{kind}"><p class="etaye-annote__texte">{brief_texte()}</p>'
            f'<aside class="etaye-annote__marge" aria-label="Annotation"><div class="etaye-annote__etat">{ic("brouillon" if kind == "brouillon" else "valide")}{etat_txt}</div>'
            f'<div class="etaye-annote__meta">Brief de préparation v{BRIEF["version"]} · {N(dh_pro(BRIEF["generatedAt"]))} · tâche {N(BRIEF["taskId"])}</div>'
            f'{extra_meta}<ol class="etaye-annote__sources">{li}</ol></aside></div>')


def brief_sections():
    s1, s5 = S("SRC-A-001"), S("SRC-A-005")
    chg = (f'<ul class="etaye-pile" style="margin:0;padding-left:20px;gap:var(--espace-2)"><li>Nouvelle déclaration de la patiente · préparation de visite du {N(dh_pro(s5["occurredAt"]))} '
           f'{repere(1, label="Source 1")} {etat("PATIENT_REPORTED_UNCONFIRMED")}</li></ul>')
    manq = (f'<div class="etaye-ligne"><span>Traitements actuels déclarés</span>{etat("MISSING")}</div>'
            + NOTE("Le brief signale le manque ; il ne le remplace par aucune valeur."))
    disc = assertion(f"Allergies{NNB}: « {esc(s1['text'])} » ({N(d_court(s1['occurredAt']))}) et « {esc(s5['text'])} » ({N(d_court(s5['occurredAt']))})",
                     f"{etat('CONTRADICTORY')} · à préciser en consultation, ne pas conclure à l'absence",
                     repere(2, label="Source 2") + repere(1, label="Source 1"))
    rows = [src_row("SRC-A-005", 1), src_row("SRC-A-001", 2), src_row("SRC-A-002", 3), src_row("SRC-A-003", 4),
            src_row("SRC-A-004", None, False), src_row("SRC-A-006", None, False)]
    return (panneau("Changements depuis la consultation du 20 octobre", chg)
            + f'<div class="etaye-cols etaye-cols--egal">{panneau("Informations manquantes", manq)}{panneau("Sources discordantes", disc)}</div>'
            + panneau(f"Sources consultées pour ce brief · {N(len(BRIEF['sourceIds']))}", sources_table(rows, None)))


def brief_axes(contenu="DRAFT", fraicheur="CURRENT", traitement="SUCCEEDED"):
    items = [("Contenu", st("business", contenu)), ("Sources", st("freshness", fraicheur)), ("Patiente", INTERNE)]
    if traitement:
        items.append(("Tâche", st("processing", traitement)))
    return axes(*items)


def s18(mode="brouillon"):
    meta = f'Pour le rendez-vous du {N(dh_pro(A1["startsAt"]))} · consultation précédente le {N("20/10/2026")}'
    if mode in ("louis", "echec"):
        a2 = by_id("appointments", "APT-A-002")
        gaps = (f'<ul class="etaye-pile" style="margin:0;padding-left:20px;gap:var(--espace-2)">'
                f'<li>Consultation précédente{NNB}: aucune consultation terminée dans le dossier</li>'
                f'<li>Sources datées{NNB}: aucune</li><li>Préparation de visite{NNB}: non reçue</li></ul>')
        if mode == "louis":
            body = (bandeau("info", "Pas encore de brief",
                            "Avec aucune source datée, un brouillon ne pourrait que lister ces manques. Vous pouvez le demander ou préparer la consultation sans brief.",
                            btn("Générer un brouillon sourcé", "secondaire", "ia", href_=href("I-14", "louis")))
                    + panneau("Ce que le dossier ne contient pas", gaps)
                    + NOTE("Variante V-EMPTY à 14:20, avant la demande AI-A-002 de 14:30. « Sans objet » remplace la rubrique « changements depuis la dernière consultation »."))
        else:
            body = (i23_panel()
                    + panneau("Ce que le dossier ne contient pas", gaps)
                    + NOTE("V-AI-DOWN : AI-A-002 a échoué le 02/11/2026 à 14:30:30 (PROVIDER_TIMEOUT). Aucun brouillon n'a été créé."))
        return ecran(LO, "Brief de préparation", body, actor="sami_a",
                     meta=f'Dr Sami Rahmani · rendez-vous du {N(dh_pro(a2["startsAt"]))}, arrivée enregistrée · aucune consultation précédente',
                     bande=axes(("Contenu", statut("neutre", "Aucun brouillon")), ("Tâche", st("processing", "FAILED"))) if mode == "echec" else "")
    if mode == "aucun":
        body = vide("Pas encore de brief", "Aucun brief n'a été généré pour ce rendez-vous.", btn("Générer un brouillon sourcé", "secondaire", "ia"))
        return ecran(CL, "Brief de préparation", body, meta=meta)
    if mode in ("en-file", "en-cours"):
        p = "QUEUED" if mode == "en-file" else "RUNNING"
        txt = ("Demandé le 02/11/2026 à 14:04:50. La demande attend son tour ; elle n'a encore rien produit." if p == "QUEUED"
               else "Demandé le 02/11/2026 à 14:04:50. Vous pouvez quitter cette page : le résultat arrivera dans Tâches et ici, comme brouillon à relire.")
        body = (bandeau("neutre", "En file d'attente" if p == "QUEUED" else "Génération en cours", txt,
                        lien("Voir la tâche", href("S-40", "en-file" if p == "QUEUED" else "en-cours")), icon="horloge")
                + panneau("Sources figées pour cette demande", snapshot_table())
                + NOTE("Variante d'AI-A-001 avant 14:05:00. Pas de pourcentage d'avancement : la tâche n'en fournit pas."))
        return ecran(CL, "Brief de préparation", body, meta=meta,
                     bande=axes(("Contenu", statut("neutre", "Aucun brouillon pour l'instant")), ("Patiente", INTERNE), ("Tâche", st("processing", p))))

    contenu, fr, band, etat_txt, kind, extra = "DRAFT", "CURRENT", "", "Brouillon généré · à relire", "brouillon", ""
    acts = (btn("Relire phrase par phrase", "principal", "liste", href_=href("S-21", "revue"))
            + btn("Exporter en PDF", "secondaire", "imprimer", href_=href("S-39", "options"))
            + btn("Générer un nouveau brouillon", "secondaire", "relancer", href_=href("I-14", "existant")))
    if mode == "relecture":
        contenu, etat_txt = "IN_REVIEW", "En relecture · Dr Nora Lefebvre"
        acts = btn("Reprendre la relecture", "principal", "liste", href_=href("S-21", "revue")) + btn("Exporter en PDF", "secondaire", "imprimer", href_=href("S-39", "options"))
        band = NOTE("Variante : envoyé en relecture après les décisions de S-21. Le texte reste un brouillon tant qu'il n'est pas validé.")
    if mode == "valide":
        contenu, etat_txt, kind = "VALIDATED", "Validé par Dr Nora Lefebvre (variante)", "valide"
        extra = '<div class="etaye-annote__meta">Usage interne : ce type de document ne peut pas être publié.</div>'
        acts = btn("Exporter en PDF", "secondaire", "imprimer", href_=href("S-39", "options")) + btn("Créer une nouvelle version", "secondaire", "modifier")
        band = bandeau("info", "Validé, réservé aux professionnels",
                       "Un brief de préparation reste interne même validé : aucune action de publication n'existe pour ce document.")
    if mode == "perime":
        fr = "STALE"
        band = bandeau("attention", "Sources modifiées depuis ce brouillon",
                       "La patiente a envoyé une version 2 de sa préparation (variante, voir S-17) après la génération de 14:05. Ce brief ne la prend pas en compte. "
                       "Le texte reste lisible ; une nouvelle version peut être générée sans effacer vos corrections.",
                       btn("Générer un nouveau brouillon", "secondaire", "relancer", href_=href("I-14", "existant")), role="alert")
    doc = brief_doc(etat_txt, kind, extra)
    body = f'{band}<div class="etaye-carte" style="background:var(--papier);padding:var(--espace-6)">{doc}</div>{brief_sections()}'
    return ecran(CL, "Brief de préparation", body, meta=meta, actions=acts, bande=brief_axes(contenu, fr))


# ═════════════════════════════════ I-14 generate or regenerate a draft ═════════════════════════════════
def i14(mode="ouvert"):
    if mode == "louis":
        fond = s18("louis")
        corps = (f'<p style="margin:0">Brief de préparation pour <strong>Louis Bernard</strong>.</p>'
                 + bandeau("attention", "Aucune source datée", "Le brouillon ne pourra que lister les informations manquantes. Aucune valeur ne sera déduite.")
                 + NOTE("Le résultat sera un brouillon à relire, réservé aux professionnels. Il ne sera ni validé ni publié automatiquement."))
        pied = btn("Annuler", "secondaire") + btn("Générer un nouveau brouillon", "principal", "ia", href_=href("S-18", "echec"))
        return dialogue(fond, "Générer un brouillon sourcé", corps, pied)
    fond = s18("brouillon")
    rows = "".join(f'<li><span>{KIND_FR[S(i)["kind"]]} · {N(dh_pro(S(i)["occurredAt"]))} · {esc(i)}</span></li>' for i in BRIEF["sourceIds"])
    entrees = (f'<fieldset class="etaye-pile" style="border:0;padding:0;margin:0;gap:var(--espace-2)"><legend class="ui-libelle">Sources datées prises en compte · {N(6)}</legend>'
               f'<ul class="etaye-annote__sources" style="margin:0">{rows}</ul></fieldset>')
    alertes = (bandeau("attention", "Avant de lancer", "Allergies : deux déclarations discordantes (14/09 et 02/11). Traitements actuels : non renseignés. "
                       "Le brouillon les signalera ; il ne tranchera pas."))
    regle = NOTE("Le résultat sera un brouillon à relire, réservé aux professionnels. Il ne sera ni validé ni publié automatiquement.")
    if mode == "existant":
        corps = (bandeau("info", "Un brouillon existe déjà", f"Brief v1 généré le {N(dh_pro(BRIEF['generatedAt']))}. Il est conservé tel quel ; le nouveau sera une version distincte. "
                         "Vos corrections déjà enregistrées ne seront pas remplacées.") + entrees + alertes + regle)
        return dialogue(fond, "Générer un brouillon sourcé", corps, btn("Annuler", "secondaire") + btn("Générer un nouveau brouillon", "principal", "ia"))
    if mode == "doublon":
        corps = (bandeau("info", "Une génération est déjà en cours", "Demandée le 02/11/2026 à 14:04:50 pour ce dossier (AI-A-001). Une seconde demande créerait un doublon : suivez celle-ci.")
                 + NOTE("Variante : double clic ou deuxième onglet. La demande existante est réutilisée."))
        return dialogue(fond, "Générer un brouillon sourcé", corps, btn("Fermer", "secondaire") + btn("Suivre la tâche", "principal", "ia", href_=href("S-40", "en-cours")))
    if mode == "quota":
        corps = (bandeau("attention", "Limite de génération atteinte", "Le cabinet a atteint la limite de générations prévue pour la journée. Aucune demande n'a été créée. "
                         "Le brief v1 reste disponible et modifiable.") + NOTE("Gabarit : aucune limite chiffrée n'est définie dans le jeu de démonstration."))
        return dialogue(fond, "Générer un brouillon sourcé", corps, btn("Fermer", "secondaire") + btn("Générer un nouveau brouillon", "principal", "ia", disabled=True))
    if mode == "erreur":
        corps = (bandeau("critique", "La demande n'a pas été envoyée", "Le service ne répond pas. Rien n'a été lancé ; vous pouvez réessayer.", role="alert") + entrees + regle)
        return dialogue(fond, "Générer un brouillon sourcé", corps, btn("Annuler", "secondaire") + btn("Réessayer", "principal", "relancer"))
    # ouvert: first generation for Claire at 14:04:50 (before AI-A-001)
    corps = (f'<p style="margin:0">Brief de préparation pour <strong>Claire Martin</strong>, rendez-vous du {N(dh_pro(A1["startsAt"]))}.</p>' + entrees + alertes + regle)
    fond = s18("aucun")
    return dialogue(fond, "Générer un brouillon sourcé", corps, btn("Annuler", "secondaire") + btn("Générer un nouveau brouillon", "principal", "ia", href_=href("S-18", "en-file")))


# ═════════════════════════════════ S-21 claim-by-claim review ═════════════════════════════════
def decision(name, checked=None, corriger_href=None):
    opts = [("Conserver", ""), ("Corriger", ""), ("Retirer", "")]
    ch = () if checked is None else (checked,)
    c = choix(name, opts, legend="Votre décision", checked=ch)
    return c + (f'<div>{lien("Corriger ce passage", corriger_href)}</div>' if corriger_href else "")


def claim(n, texte, soutien, preuves, name, checked=None, corriger=None, alerte=False):
    k = " etaye-notes__section--edition" if alerte else ""
    return (f'<section class="etaye-notes__section{k}"><div class="etaye-notes__tete"><h3>Passage {n}</h3>{soutien}</div>'
            f'<div class="etaye-notes__corps"><p class="doc-corps" style="margin:0;font-size:17px;line-height:28px">{texte}</p>{preuves}'
            f'{decision(name, checked, corriger)}</div></section>')


def preuve(sid, n, note=""):
    s = S(sid)
    return (f'<div class="etaye-pile" style="gap:var(--espace-1)"><div class="etaye-2" style="font-size:13px">{repere(n, seul=True)} {KIND_FR[s["kind"]]} · survenu {N(dh_pro(s["occurredAt"]))} · '
            f'enregistré {N(dh_pro(s["recordedAt"]))} · {esc(auteur(s))} · {esc(sid)}</div><blockquote>« {esc(s["text"])} »</blockquote>{f"<div class=\"etaye-2\" style=\"font-size:13px\">{note}</div>" if note else ""}</div>')


def s21_shell(body, titre="Relire le brouillon généré", rid=CL, meta=None, bande="", actor="nora", actions=""):
    meta = meta if meta is not None else f'Brief de préparation v1 · généré le {N(dh_pro(BRIEF["generatedAt"]))} · {PROVIDER} · tâche AI-A-001'
    return ecran(rid, titre, body, actor=actor, meta=meta, bande=bande, actions=actions)


def s21_claims(checked=(None, None, None, None)):
    SUP = statut("valide", "Soutenu par une source datée", "valide")
    return (claim(1, "Depuis la consultation du 20 octobre, Claire Martin a signalé un souvenir de réaction à la pénicilline.", SUP,
                  preuve("SRC-A-005", 1, "Déclaration de la patiente, non confirmée : à reprendre comme telle."), "c1", checked[0])
            + claim(2, "Cette déclaration diffère de la mention du 14 septembre.", SUP, preuve("SRC-A-001", 2, "Absence déclarée à cette date, pas une absence durable."), "c2", checked[1])
            + claim(3, "La liste actuelle des traitements n’est pas renseignée.", statut("neutre", "Manque explicite · aucun repère"),
                    preuve("SRC-A-006", 5, "La note du 20/10 mentionne aussi l'absence de liste. Ajoutez-la comme repère 5 si vous le jugez exact."), "c3", checked[2])
            + claim(4, f"Les résultats de créatinine disponibles sont de {N('96')} puis {N(f'104{NB}µmol/L')}.", SUP,
                    preuve("SRC-A-002", 3) + preuve("SRC-A-003", 4), "c4", checked[3]))


def s21(mode="revue"):
    resume = lambda d: (f'<div class="etaye-axes"><span class="etaye-axe">Passages {N(4)}</span><span class="etaye-axe">Soutenus {N(3)}</span>'
                        f'<span class="etaye-axe">Manque déclaré {N(1)}</span><span class="etaye-axe">Non soutenus {N(0)}</span><span class="etaye-axe">Décisions prises {N(d)}</span></div>')
    pied = f'<div class="etaye-ligne">{btn("Revenir au brief", "secondaire", href_=href("S-18"))}{btn("Enregistrer mes décisions", "principal")}</div>'
    if mode == "revue":
        body = resume(0) + s21_claims() + pied + NOTE("Aucun score de confiance : chaque passage montre la source exacte qui le soutient, ou son absence.")
        return s21_shell(body, bande=brief_axes())
    if mode in ("non-soutenu", "corrigee"):
        meta = 'Explication destinée à Claire Martin · version 2 · publiable seulement après validation, par une action distincte'
        s1, s5 = S("SRC-A-001"), S("SRC-A-005")
        if mode == "non-soutenu":
            c = claim(1, f'« {esc(PV2["aiOriginalText"])} »{etiq("Texte généré", "genere")}', statut("attention", "Non soutenu", "alerte"),
                      preuve("SRC-A-001", 1, "Absence déclarée le 14/09/2026, « selon la déclaration recueillie ce jour » : ne soutient pas une absence durable.")
                      + preuve("SRC-A-005", 2, "Déclaration plus récente de la patiente : contredit la phrase générée."), "u1", 1, href("I-15", "ouvert"), alerte=True)
            gap = panneau("Non abordé par le texte généré", f'<div class="etaye-ligne"><span>Traitements actuels déclarés</span>{etat("MISSING")}</div>')
            body = (bandeau("attention", "Un passage n'est soutenu par aucune source à cette date",
                            "Une source existe, mais elle ne dit pas ce que la phrase affirme. Le passage ne peut pas être conservé tel quel.", role="alert")
                    + c + gap + NOTE("SC-UNSUPPORTED-DRAFT : ce texte généré n'a jamais été montré à la patiente. Génération non rattachée à une tâche du jeu de démonstration."))
            return s21_shell(body, "Relire le brouillon généré", meta=meta, bande=axes(("Contenu", st("business", "DRAFT")), ("Patiente", st("publication", None))))
        lignes = (f'<ol class="etaye-pile" style="margin:0;padding-left:20px;gap:var(--espace-3)">'
                  + "".join(f'<li><p class="doc-corps" style="margin:0;font-size:17px;line-height:28px"><span class="etaye-segment etaye-segment--humain">{esc(p)}</span>'
                            f'{etiq("Correction humaine", "humain")}</p></li>' for p in [x.strip() + ("" if x.strip().endswith(".") else ".") for x in PV2["humanEditedText"].split(". ") if x.strip()])
                  + "</ol>")
        rejet = (f'<div class="etaye-pile" style="gap:var(--espace-1)"><div class="ui-libelle">Texte généré rejeté</div><p style="margin:0"><s>« {esc(PV2["aiOriginalText"])} »</s> {etiq("Rejeté", "alerte")}</p>'
                 f'<div class="etaye-2" style="font-size:13px">Motif de correction{NNB}: {esc(CORR_FR)}</div></div>')
        body = (toast("Corrections enregistrées · Dr Nora Lefebvre") + panneau("Texte relu", lignes + rejet)
                + f'<div class="etaye-ligne">{btn("Comparer les versions", "secondaire", "comparer", href_=href("S-22"))}{btn("Envoyer en validation", "principal", href_=href("S-23"))}</div>'
                + NOTE("Une nouvelle génération ne remplacera pas ces corrections sans un choix explicite de nouvelle version."))
        return s21_shell(body, "Relire le brouillon généré", meta=meta, bande=axes(("Contenu", st("business", "DRAFT")), ("Patiente", st("publication", None))))
    if mode == "delai":
        return ecran(LO, "Relire le brouillon généré", i23_panel() + NOTE("V-AI-DOWN · AI-A-002 : rien à relire, aucun brouillon créé."), actor="sami_a",
                     meta=f"Brief de préparation · {PROVIDER} · tâche AI-A-002", bande=axes(("Tâche", st("processing", "FAILED"))))
    bands = {
        "incomplet": ("attention", "Résultat incomplet rejeté", "La dernière génération (variante) s'est arrêtée au milieu d'un passage. Elle a été rejetée ; aucun brouillon n'a été créé. Le brief v1 ci-dessous reste inchangé.", "Variante d'une régénération : sortie partielle rejetée."),
        "citation-invalide": ("attention", "Un repère ne renvoie à aucune source", "Dans une régénération (variante), le passage 4 citait une référence absente du dossier. Il est marqué « À vérifier » et ne peut pas être conservé tel quel.", "Gabarit : référence mal formée détectée à la réception."),
        "quota": ("attention", "Limite de génération atteinte", "Aucune nouvelle génération n'est possible aujourd'hui. Vous pouvez relire et corriger le brief v1 à la main.", "Gabarit : aucune limite chiffrée dans le jeu de démonstration."),
        "doublon": ("info", "Génération déjà en cours", "Une demande identique pour ce dossier est en cours (demandée le 02/11/2026 à 14:04:50). Elle sera réutilisée ; aucun doublon n'est créé.", "Variante d'AI-A-001."),
        "source-modifiee": ("attention", "Une source a changé pendant la tâche", "La préparation de visite a reçu une version 2 (variante) pendant la génération. Le brouillon reçu est marqué « Sources modifiées » : relisez-le ou régénérez.", "Gabarit : changement de source pendant la tâche."),
    }
    k, t, x, note = bands[mode]
    acts = {"doublon": lien("Suivre la tâche", href("S-40", "en-cours")), "source-modifiee": btn("Générer un nouveau brouillon", "secondaire", "relancer", href_=href("I-14", "existant"))}.get(mode, "")
    checked = (None, None, None, None)
    claims = s21_claims(checked)
    if mode == "citation-invalide":
        claims = claims.replace("<h3>Passage 4</h3>", f"<h3>Passage 4</h3>{etiq('À vérifier · repère sans source', 'alerte')}")
    fr = "STALE" if mode == "source-modifiee" else "CURRENT"
    body = bandeau(k, t, x, acts, role="alert" if k == "attention" else "status") + resume(0) + claims + NOTE(note)
    return s21_shell(body, bande=brief_axes(fraicheur=fr))


# ═════════════════════════════════ I-15 review and correct (page) ═════════════════════════════════
def i15(mode="ouvert"):
    meta = 'Explication destinée à Claire Martin · version 2 · publiable seulement après validation, par une action distincte'
    base = (f'<div class="etaye-pile" style="gap:var(--espace-2)"><div class="ui-libelle">Texte généré (référence, non modifiable)</div>'
            f'<p class="doc-corps" style="margin:0;font-size:17px;line-height:28px"><span class="etaye-segment etaye-segment--genere">« {esc(PV2["aiOriginalText"])} »</span>'
            f'{etiq("Non soutenu", "alerte")}</p></div>')
    filled = mode != "ouvert"
    txt = PV2["humanEditedText"] if filled else PV2["aiOriginalText"]
    err = None
    if mode == "non-soutenu":
        txt = PV2["humanEditedText"]
    editeur = champ("Votre texte", txt, "i15-texte", kind="textarea", rows=5,
                    help_="Chaque phrase doit s'appuyer sur une source datée, ou dire ce qui manque.", focus=mode == "ouvert")
    motif = champ("Motif de la correction", CORR_FR if filled else "", "i15-motif", kind="textarea", rows=3, optional=True,
                  help_="Conservé dans l'historique des versions, jamais montré à la patiente.")
    srcs = (f'<ol class="etaye-sources-liste">'
            + "".join(f'<li>{repere(n, seul=True)}<span>{KIND_FR[S(i)["kind"]]} · {N(dh_pro(S(i)["occurredAt"]))} · {esc(auteur(S(i)))}</span><blockquote>« {esc(S(i)["text"])} »</blockquote></li>'
                      for i, n in [("SRC-A-001", 1), ("SRC-A-005", 2), ("SRC-A-006", 3)]) + "</ol>")
    etat_sv = {"ouvert": enregistrement("ok", "14:12"), "sale": enregistrement("sale"), "enregistre": enregistrement("ok", "14:18"),
               "echec": enregistrement("echec"), "non-soutenu": enregistrement("sale")}[mode]
    top = ""
    if mode == "echec":
        top = bandeau("critique", "Vos corrections n'ont pas été enregistrées", "La connexion a été interrompue. Votre texte est conservé dans l'éditeur ; réessayez.",
                      btn("Réessayer l'enregistrement", "secondaire", "relancer"), role="alert")
    if mode == "non-soutenu":
        top = resume_erreurs([("i15-texte", "Phrase 3 : « La liste actuelle de vos traitements reste à compléter. » n'a pas encore de repère de source")])
        editeur = champ("Votre texte", txt, "i15-texte", kind="textarea", rows=5,
                        error="Ajoutez le repère 3 (note du 20/10/2026) ou reformulez comme une information manquante.")
    if mode == "enregistre":
        top = toast("Corrections enregistrées · version 2 reste un brouillon")
    if mode == "ouvert":
        top = NOTE("Les heures d'enregistrement de cette relecture (14:12, 14:18) sont des variantes : le jeu de démonstration ne date que la validation de 14:20.")
    gauche = f'<div class="etaye-pile">{top}{base}{editeur}{motif}<div class="etaye-ligne">{etat_sv}</div></div>'
    droite = panneau("Sources disponibles", srcs)
    acts = (btn("Revenir à la lecture", "secondaire", href_=href("S-21", "non-soutenu"))
            + btn("Enregistrer les corrections", "principal", disabled=mode == "non-soutenu", href_=href("S-21", "corrigee") if mode != "non-soutenu" else None))
    body = f'<div class="etaye-cols etaye-cols--2">{gauche}{droite}</div><div class="etaye-ligne">{acts}</div>'
    return ecran(CL, "Relire et corriger", body, meta=meta, bande=axes(("Contenu", st("business", "DRAFT")), ("Patiente", st("publication", None))))


# ═════════════════════════════════ S-40 processing activity ═════════════════════════════════
def tache_row(t, statut_html=None):
    rid = t["recordId"]
    doc = (f'{st("business", "DRAFT")}<div class="etaye-3" style="font-size:12px">Brief v1</div>' if t.get("outputVersionId") else '<span class="etaye-2">Aucun document</span>')
    fin = t.get("completedAt") or t.get("failedAt")
    act = (lien("Ouvrir le résultat", href("S-21", "revue")) if t["status"] == "SUCCEEDED" else lien("Voir le détail", href("S-40", "echec")))
    quand = N(dh_pro(t["requestedAt"]) + ":" + t["requestedAt"][17:19]) + (f'<div class="etaye-3" style="font-size:12px">{"terminé" if t["status"] == "SUCCEEDED" else "échec"} à {N(h_pro(fin) + ":" + fin[17:19])}</div>' if fin else "")
    return [f'{esc(nom(rid))}<div class="etaye-3" style="font-size:12px">{esc(t["id"])} · brief de préparation</div>',
            statut_html or st("processing", t["status"]), quand, doc, act]


def taches(rows):
    return tableau(["Patient et objet", "Tâche", "Demandé", "Document produit", ""], rows, stack=True)


def s40(mode="liste"):
    regle = NOTE("L'état de la tâche et l'état du document sont séparés : une tâche terminée produit un brouillon à relire, jamais une validation ni une publication. Aucune notification n'est envoyée au patient.")
    if mode in ("liste", "termine"):
        body = (panneau("Tâches récentes · Cabinet des Tilleuls", taches([tache_row(T2), tache_row(T1)]))
                + regle + NOTE(f"Mode de génération{NNB}: {PROVIDER} (DETERMINISTIC_FIXTURE)."))
        if mode == "termine":
            body = toast("Le brief de Claire Martin est prêt : brouillon à relire", lien("Ouvrir", href("S-21", "revue"))) + body
            body += NOTE("Variante : la tâche s'est terminée à 14:05:00 pendant que vous étiez sur une autre page.")
        return ecran(None, "Tâches", body, actif="Tâches", fil_="", meta="Demandes de génération du cabinet et leur résultat")
    if mode in ("detail", "en-file", "en-cours", "doublon", "source-modifiee", "annule"):
        p = {"detail": "SUCCEEDED", "en-file": "QUEUED", "en-cours": "RUNNING", "doublon": "RUNNING", "source-modifiee": "SUCCEEDED", "annule": "CANCELLED"}[mode]
        snap = (sources_table([src_row(i, n) for i, n in BRIEF_SRC] + [src_row("SRC-A-004", None, False), src_row("SRC-A-006", None, False)], None)
                if p == "SUCCEEDED" else snapshot_table())
        fin = {"SUCCEEDED": N("02/11/2026 14:05:00"), "QUEUED": "—", "RUNNING": "—", "CANCELLED": "Annulé (gabarit)"}[p]
        out = {"SUCCEEDED": f'{lien("Brief de préparation v1", href("S-18"))} · {st("business", "DRAFT")} · {st("freshness", "STALE" if mode == "source-modifiee" else "CURRENT")}',
               "QUEUED": "Aucun pour l'instant", "RUNNING": "Aucun pour l'instant", "CANCELLED": "Aucun"}[p]
        dl = (f'<dl class="etaye-def"><dt>Patient</dt><dd>Claire Martin · R-A-001</dd><dt>Objet</dt><dd>Brief de préparation</dd>'
              f'<dt>Tâche</dt><dd>{st("processing", p)}</dd><dt>Demandé</dt><dd>{N("02/11/2026 14:04:50")} · {NORA}</dd><dt>Terminé</dt><dd>{fin}</dd>'
              f'<dt>Mode</dt><dd>{PROVIDER}</dd><dt>Document produit</dt><dd>{out}</dd></dl>')
        band = {
            "detail": "",
            "en-file": bandeau("neutre", "En file d'attente", "La demande attend son tour. Vous pouvez quitter cette page.", btn("Annuler la demande", "secondaire", "fermer"), icon="horloge"),
            "en-cours": bandeau("neutre", "Génération en cours", "Vous pouvez quitter cette page ; le résultat apparaîtra ici et dans le dossier. Aucun pourcentage n'est affiché : la tâche n'en fournit pas.", icon="horloge"),
            "doublon": bandeau("info", "Demande déjà en cours", "Vous avez relancé la même génération : la demande existante (AI-A-001) est réutilisée, aucun second brouillon ne sera créé."),
            "source-modifiee": bandeau("attention", "Une source a changé pendant la tâche", "La préparation de visite a reçu une version 2 (variante) après le figeage des sources. Le brouillon produit est marqué « Sources modifiées ».", lien("Ouvrir le brief", href("S-18", "perime")), role="alert"),
            "annule": bandeau("neutre", "Demande annulée", "Annulée avant son exécution (gabarit). Rien n'a été produit ; le dossier est inchangé.", icon="interdit"),
        }[mode]
        notes = {"detail": "AI-A-001 tel qu'enregistré.", "en-file": "Variante d'AI-A-001 avant 14:04:55.", "en-cours": "Variante d'AI-A-001 entre 14:04:50 et 14:05:00.",
                 "doublon": "Variante : seconde demande identique.", "source-modifiee": "Gabarit.", "annule": "Gabarit : annulation d'une demande en file."}[mode]
        acts = btn("Ouvrir le résultat", "principal", href_=href("S-21", "revue")) if p == "SUCCEEDED" else ""
        body = band + panneau("Demande AI-A-001", dl) + panneau("Sources figées à la demande", snap) + regle + NOTE(notes)
        return ecran(None, "Tâche AI-A-001", body, actif="Tâches", fil_=f'<a href="{href("S-40")}">Tâches</a>', actions=acts,
                     meta="Brief de préparation · Claire Martin")
    if mode == "quota":
        body = (bandeau("attention", "Limite de génération atteinte", "Aucune nouvelle demande n'est acceptée aujourd'hui pour le cabinet. Les brouillons existants restent disponibles ; la saisie manuelle n'est pas limitée.")
                + panneau("Tâches récentes · Cabinet des Tilleuls", taches([tache_row(T2), tache_row(T1)])) + NOTE("Gabarit : aucune limite chiffrée dans le jeu de démonstration."))
        return ecran(None, "Tâches", body, actif="Tâches", fil_="")
    # echec: AI-A-002 detail with I-23 inline
    dl = (f'<dl class="etaye-def"><dt>Patient</dt><dd>Louis Bernard · R-A-002</dd><dt>Objet</dt><dd>Brief de préparation</dd>'
          f'<dt>Tâche</dt><dd>{st("processing", "FAILED")}</dd><dt>Demandé</dt><dd>{N("02/11/2026 14:30:00")}</dd><dt>Échec</dt><dd>{N("02/11/2026 14:30:30")}</dd>'
          f'<dt>Mode</dt><dd>{PROVIDER}</dd><dt>Document produit</dt><dd>Aucun</dd></dl>')
    body = i23_panel() + panneau("Demande AI-A-002", dl) + regle + NOTE("V-AI-DOWN. Le message d'erreur ne contient ni données patient ni contenu échangé avec le service.")
    return ecran(None, "Tâche AI-A-002", body, actor="sami_a", actif="Tâches", fil_=f'<a href="{href("S-40")}">Tâches</a>', meta="Brief de préparation · Louis Bernard")


# ═════════════════════════════════ I-23 failed processing and retry (inline) ═════════════════════════════════
def i23_panel(mode="ouvert"):
    if mode == "relance":
        return bandeau("neutre", "Nouvelle tentative en file d'attente",
                       "Tentative 2 (variante), demandée après l'échec de 14:30:30. La première demande reste tracée ; aucun brouillon n'existe encore.",
                       lien("Suivre la tâche", href("S-40", "en-file")), icon="horloge")
    if mode == "limite":
        return bandeau("attention", "Nouvelle tentative impossible pour le moment",
                       "Le nombre de tentatives prévu est atteint (gabarit). Vous pouvez préparer la consultation sans brief ; le dossier reste disponible.",
                       btn("Revenir au dossier", "secondaire", href_=href("S-09", "louis")), role="alert")
    if mode == "ambigu":
        return bandeau("neutre", "Vérification de la demande",
                       "La réponse à votre nouvelle tentative s'est perdue (gabarit). Nous vérifions si elle a été prise en compte avant d'en proposer une autre : aucun doublon ne sera créé.",
                       icon="horloge")
    return bandeau("attention", "La tâche a échoué",
                   f"Le service de génération n'a pas répondu dans le délai prévu (délai dépassé) · 1re tentative · 02/11/2026 14:30:30. Aucun brouillon n'a été créé ; rien n'a été envoyé au patient.",
                   btn("Réessayer", "secondaire", "relancer", href_=href("I-23", "relance")) + btn("Revenir au dossier", "discret", href_=href("S-09", "louis")), role="alert")


def i23(mode="ouvert"):
    return ecran(LO, "Brief de préparation", i23_panel(mode) + NOTE({"ouvert": "AI-A-002 (retryable).", "relance": "Variante.", "limite": "Gabarit.", "ambigu": "Gabarit."}[mode]),
                 actor="sami_a", meta="Dr Sami Rahmani · aucune consultation précédente",
                 bande=axes(("Contenu", statut("neutre", "Aucun brouillon")), ("Tâche", st("processing", "QUEUED" if mode == "relance" else "FAILED"))))


SCREENS = [
    {"id": "S-17", "name": "Préparation de visite (vue praticien)", "states": [
        {"slug": "claire", "label": "PREP-A-001 submitted 2 Nov 09:10 — patient's own words, unknown reaction details, contradiction with SRC-A-001, treatments unanswered ≠ 'Non'", "html": s17()},
        {"slug": "lue", "label": "Acknowledged reading (variant) — reporter unchanged", "html": s17("lue")},
        {"slug": "non-soumise", "label": "Not submitted — Louis Bernard (V-EMPTY), nothing inferred from the absence", "html": s17("non-soumise")},
        {"slug": "brouillon-patient", "label": "Patient draft not visible to the practice (variant at 09:05)", "html": s17("brouillon-patient")},
        {"slug": "corrigee", "label": "Corrected submission v2 (variant) — treatments 'Je ne sais pas', v1 kept and dated", "html": s17("corrigee")},
        {"slug": "longue", "label": "Long text (V-LONG variant, administrative text only)", "html": s17("longue")},
        {"slug": "lien-perime", "label": "Linked appointment moved (variant) — preparation stays attached", "html": s17("lien-perime")},
    ]},
    {"id": "S-18", "name": "Brief de préparation sourcé", "states": [
        {"slug": "brouillon", "label": "BRIEF-A-001-v1 DRAFT exact text — 4 markers, missing treatments flagged, contradiction, 6 sources (2 consulted, not cited), internal only", "html": s18()},
        {"slug": "louis", "label": "No prior visit and no dated source — Louis Bernard (V-EMPTY), explicit gaps, no generated text", "html": s18("louis")},
        {"slug": "en-file", "label": "QUEUED (variant of AI-A-001) — frozen source snapshot, no percentage", "html": s18("en-file")},
        {"slug": "en-cours", "label": "RUNNING (variant of AI-A-001) — can leave the page", "html": s18("en-cours")},
        {"slug": "relecture", "label": "IN_REVIEW (variant)", "html": s18("relecture")},
        {"slug": "valide", "label": "VALIDATED (variant) — still internal, no publication action exists", "html": s18("valide")},
        {"slug": "perime", "label": "STALE (variant) — preparation v2 after 14:05, text kept, regenerate as new version", "html": s18("perime")},
        {"slug": "echec", "label": "Failed task — AI-A-002 PROVIDER_TIMEOUT 14:30:30 (V-AI-DOWN), I-23 inline, no draft", "html": s18("echec")},
    ]},
    {"id": "I-14", "name": "Générer un brouillon sourcé", "states": [
        {"slug": "ouvert", "label": "First generation for Claire (before AI-A-001 14:04:50) — dated inputs, warnings, draft only", "html": i14()},
        {"slug": "existant", "label": "Regenerate — v1 kept, corrections not replaced, new version", "html": i14("existant")},
        {"slug": "louis", "label": "No dated source warning — Louis Bernard before AI-A-002", "html": i14("louis")},
        {"slug": "doublon", "label": "Duplicate request — existing task reused (variant)", "html": i14("doublon")},
        {"slug": "quota", "label": "Quota reached — primary disabled (template)", "html": i14("quota")},
        {"slug": "erreur", "label": "Request not sent — nothing launched", "html": i14("erreur")},
    ]},
    {"id": "S-21", "name": "Relecture du brouillon généré", "states": [
        {"slug": "revue", "label": "BRIEF-A-001-v1 claim by claim — exact excerpts with occurred/recorded dates, counts not scores, keep/correct/remove", "html": s21()},
        {"slug": "non-soutenu", "label": "SC-UNSUPPORTED-DRAFT — 'Vous n'avez pas d'allergie connue.' cites nothing that supports it at this date", "html": s21("non-soutenu")},
        {"slug": "corrigee", "label": "Corrected — humanEditedText labelled, generated text rejected with reason, then validation", "html": s21("corrigee")},
        {"slug": "incomplet", "label": "Partial output rejected (variant) — v1 unchanged", "html": s21("incomplet")},
        {"slug": "delai", "label": "Timeout — AI-A-002, nothing to review", "html": s21("delai")},
        {"slug": "citation-invalide", "label": "Malformed citation (template) — passage flagged", "html": s21("citation-invalide")},
        {"slug": "quota", "label": "Quota reached (template) — manual review continues", "html": s21("quota")},
        {"slug": "doublon", "label": "Duplicate request reused (variant)", "html": s21("doublon")},
        {"slug": "source-modifiee", "label": "Source changed during task (template) — result marked STALE", "html": s21("source-modifiee")},
    ]},
    {"id": "I-15", "name": "Relire et corriger", "states": [
        {"slug": "ouvert", "label": "Generated baseline (read-only) and editor — PATIENT-A-001-v2 before correction", "html": i15()},
        {"slug": "sale", "label": "Dirty — humanEditedText typed, correction reason", "html": i15("sale")},
        {"slug": "non-soutenu", "label": "Save blocked — sentence without a source marker, field error and summary", "html": i15("non-soutenu")},
        {"slug": "echec", "label": "Save failed — text kept in the editor", "html": i15("echec")},
        {"slug": "enregistre", "label": "Saved — still a draft", "html": i15("enregistre")},
    ]},
    {"id": "S-40", "name": "Tâches de génération et détail", "states": [
        {"slug": "liste", "label": "AI-A-002 FAILED and AI-A-001 SUCCEEDED — task state and document state in separate columns", "html": s40()},
        {"slug": "detail", "label": "AI-A-001 detail — requested 14:04:50, done 14:05:00, frozen sources, output DRAFT, no patient notification", "html": s40("detail")},
        {"slug": "echec", "label": "AI-A-002 detail — PROVIDER_TIMEOUT, retry, safe error with no patient data", "html": s40("echec")},
        {"slug": "en-file", "label": "QUEUED (variant) — cancel", "html": s40("en-file")},
        {"slug": "en-cours", "label": "RUNNING (variant) — no completion percentage", "html": s40("en-cours")},
        {"slug": "termine", "label": "Completion after navigation (variant) — toast, draft only", "html": s40("termine")},
        {"slug": "doublon", "label": "Duplicate request reuses the task (variant)", "html": s40("doublon")},
        {"slug": "quota", "label": "Quota exhausted (template)", "html": s40("quota")},
        {"slug": "source-modifiee", "label": "Source changed during task (template) — output STALE", "html": s40("source-modifiee")},
        {"slug": "annule", "label": "CANCELLED (template)", "html": s40("annule")},
    ]},
    {"id": "I-23", "name": "Échec d'une tâche et nouvelle tentative", "states": [
        {"slug": "ouvert", "label": "Inline failure — AI-A-002 safe reason, attempt and time, no draft, retry allowed", "html": i23()},
        {"slug": "relance", "label": "New bounded attempt queued (variant)", "html": i23("relance")},
        {"slug": "limite", "label": "Attempt cap reached (template)", "html": i23("limite")},
        {"slug": "ambigu", "label": "Ambiguous outcome checked by operation identity (template)", "html": i23("ambigu")},
    ]},
]

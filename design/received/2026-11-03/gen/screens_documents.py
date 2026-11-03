"""A4 documents, export and e-mails (batch 9) — P-01 practitioner brief, P-02 consultation report, P-03 published
patient explanation (short, long, incomplete; real PDFs made by gen/pdf.py), S-39 export options and print preview,
I-19 export drawer, M-01…M-05 transactional e-mails (HTML, plain text, dark client).

Data: synthetic-data.json only; body text is the exact dataset version text (BRIEF-A-001-v1, REPORT-A-001-v1,
PATIENT-A-001-v2) plus the fictional content samples of spec/04-pdfs-emails.md. Long layouts repeat the clinician
source appendix as labelled layout test copies (same source events, no additional observation).
"""
import os, re
from lib import *

S = lambda i: by_id("sources", i)
NORA = "Dr Nora Lefebvre"
BRIEF = by_id("documentVersions", "BRIEF-A-001-v1")
REP = by_id("documentVersions", "REPORT-A-001-v1")
PV2 = by_id("documentVersions", "PATIENT-A-001-v2")
PUB2 = by_id("publications", "PUB-A-002")
A1 = by_id("appointments", "APT-A-001")
DOCS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "site", "docs")
EXPORT = "02/11/2026 16:00"   # dataset baseline used as export time
TYPE_FR = SOURCE_TYPE_FR


def n(x):
    return f'<span class="doc-num">{x}</span>'


def ref(k):
    return f'<span class="doc-ref">[{k}]</span>'


def page_style(top_right, doc_id):
    tr = top_right.replace('"', "'")
    return ('<style>@page { '
            '@top-left { content: "Étaye · Cabinet des Tilleuls"; font: 400 8pt "Atkinson Hyperlegible Next", sans-serif; color: #4a4944; } '
            f'@top-right {{ content: "{tr}"; font: 400 8pt "Atkinson Hyperlegible Next", sans-serif; color: #4a4944; }} '
            f'@bottom-left {{ content: "Démonstration — personnes et données fictives · {doc_id}"; font: 400 8pt "Atkinson Hyperlegible Next", sans-serif; color: #4a4944; }} '
            '@bottom-right { content: "Page " counter(page) " / " counter(pages); font: 400 8pt "Atkinson Hyperlegible Next", sans-serif; color: #4a4944; } } '
            '@page :first { @top-left { content: none; } @top-right { content: none; } }</style>')


def doc_entete(emetteur):
    return (f'<div class="doc-entete"><img src="{ROOT}/logos/etaye-logo-monochrome.svg" alt="Étaye">'
            f'<div class="doc-emetteur">{emetteur}</div></div>')


def meta(rows):
    return '<table class="doc-meta"><tbody>' + "".join(f"<tr><th scope=\"row\">{k}</th><td>{v}</td></tr>" for k, v in rows) + "</tbody></table>"


def doc(body, top_right, doc_id, label):
    return f'{page_style(top_right, doc_id)}<article class="doc-a4" lang="fr" aria-label="{esc(label)}">{body}</article>'


def src_list(items):
    """items: (k, source_id) — author/type/date/revision/locator, statement numbers cross-linked."""
    li = []
    for k, sid in items:
        s = S(sid)
        rec = f' · enregistré {n(dh_pro(s["recordedAt"]))}' if s["recordedAt"] != s["occurredAt"] else ""
        li.append(f'<li><span>[{k}]</span><span>{KIND_FR[s["kind"]]} · {TYPE_FR.get(s.get("sourceType"), "")} · {esc(auteur(s))} · survenu {n(dh_pro(s["occurredAt"]))}{rec} · '
                  f'{esc(sid)} révision {s["version"]} · {esc(s["locator"])}</span></li>')
    return f'<ol class="doc-sources">{"".join(li)}</ol>'


APPENDIX = ["SRC-A-001", "SRC-A-002", "SRC-A-003", "SRC-A-004", "SRC-A-005", "SRC-A-006", "SRC-A-007-v1", "SRC-A-007-v2", "SRC-A-008"]


def annexe(copies=0, titre="Annexe — sources du dossier (professionnels)"):
    def table(cap):
        rows = []
        for sid in APPENDIX:
            s = S(sid)
            corr = f'<br><span class="doc-manque">Corrigée par SRC-A-007-v2</span>' if sid == "SRC-A-007-v1" else (f'<br>Motif{NNB}: {esc(s["correctionReason"])}' if s.get("correctionReason") else "")
            rows.append(f'<tr><td>{n(esc(sid))}<br>révision {s["version"]}</td><td>{n(dh_pro(s["occurredAt"]))}<br>{n(dh_pro(s["recordedAt"]))}</td>'
                        f'<td>{TYPE_FR.get(s.get("sourceType"), "")}<br>{esc(auteur(s))}</td><td><blockquote>« {esc(s["text"])} »</blockquote>{esc(s["locator"])}{corr}</td></tr>')
        return (f'<table class="doc-table"><caption>{cap}</caption><thead><tr><th scope="col" style="width:24mm">Source</th><th scope="col" style="width:30mm">Survenue<br>Enregistrement</th>'
                f'<th scope="col" style="width:34mm">Type<br>Auteur</th><th scope="col">Contenu d\'origine et emplacement</th></tr></thead><tbody>{"".join(rows)}</tbody></table>')
    out = f'<h2 class="doc-saut">{titre}</h2><p class="doc-note">Une source affichée comme contexte historique n\'est pas pour autant une preuve de chaque phrase. Dates de survenue et d\'enregistrement séparées ; heure de Paris.</p>'
    out += table("Sources du dossier R-A-001")
    for c in range(1, copies + 1):
        out += f'<div class="doc-copie">Copie de test de mise en page {c}/{copies} — mêmes sources, aucune observation supplémentaire</div>' + table(f"Copie de test {c}/{copies} · sources du dossier R-A-001")
    return out


# ═════════════════════════════════ P-01 practitioner brief ═════════════════════════════════
def p01(mode="court"):
    if mode == "incomplet":
        t2 = by_id("aiTasks", "AI-A-002")
        body = (doc_entete("Cabinet des Tilleuls<br>Dr Sami Rahmani")
                + '<h1>Brief de préparation</h1>'
                + '<div class="doc-statut doc-statut--echec">AUCUN BRIEF GÉNÉRÉ — réservé aux professionnels<small>Tâche AI-A-002 en échec (délai dépassé). Ce document n\'est pas un brief et n\'est pas publiable.</small></div>'
                + meta([("Patient", f"BERNARD Louis · naissance {n('09/07/1991')} · dossier {n('R-A-002')}"), ("Rendez-vous", f"{n('02/11/2026 14:00')} · arrivée enregistrée"),
                        ("Consultation précédente", "aucune consultation terminée"), ("Tâche", f"AI-A-002 · demandé {n('02/11/2026 14:30:00')} · échec {n('14:30:30')} · nouvelle tentative possible"),
                        ("Exporté", f"{n(EXPORT)} · heure de référence du jeu de démonstration")])
                + '<h2>Ce que le dossier contient</h2><p>Louis Bernard — historique minimal. Aucun antécédent renseigné dans le jeu fictif ne signifie pas absence d\'antécédent.</p>'
                + '<h2>Informations manquantes</h2><table class="doc-table"><thead><tr><th scope="col">Rubrique</th><th scope="col">État</th></tr></thead><tbody>'
                + "".join(f'<tr><td>{r}</td><td><span class="doc-manque">{e}</span></td></tr>' for r, e in [("Antécédents médicaux", "Inconnu · non renseigné"), ("Allergies", "Inconnu · jamais renseigné"),
                                                                                                         ("Traitements actuels", "Non renseignés"), ("Préparation de visite", "Non reçue"), ("Sources datées", "Aucune")])
                + '</tbody></table>'
                + '<h2>Synthèse</h2><p>Aucune synthèse produite après l\'échec de la tâche AI-A-002. Résultat de la tâche : échec, délai dépassé ; aucune classification de risque et aucun texte rassurant de remplacement.</p>'
                + '<p class="doc-note">Ces informations ne constituent pas une proposition de diagnostic ou de traitement.</p>')
        return doc(body, "BERNARD Louis · R-A-002 · Brief de préparation", "R-A-002 · aucun brief", "Brief de préparation, Louis Bernard, aucun brief généré")
    text = (f'Depuis la consultation du 20 octobre, Claire Martin a signalé un souvenir de réaction à la pénicilline{ref(1)}. '
            f'Cette déclaration diffère de la mention du 14 septembre{ref(2)}. '
            f'La liste actuelle des traitements n’est pas renseignée.<span class="doc-manque">manquant · aucune source</span> '
            f'Les résultats de créatinine disponibles sont de {n("96")} puis {n(f"104{NB}µmol/L")}{ref(3)}{ref(4)}.')
    mes = ('<table class="doc-table"><caption>Mesures du dossier (hors texte généré)</caption><thead><tr><th scope="col">Mesure</th><th scope="col">Date</th><th scope="col">Valeur</th><th scope="col">Source</th></tr></thead><tbody>'
           f'<tr><td>Pression artérielle</td><td>{n("14/09/2026 09:35")}</td><td>{n(f"128/80{NB}mmHg")}</td><td>SRC-A-008</td></tr>'
           f'<tr><td>Pression artérielle</td><td>{n("20/10/2026 10:10")}</td><td>{n(f"134/82{NB}mmHg")}</td><td>SRC-A-004</td></tr>'
           f'<tr><td>Poids</td><td>{n("20/10/2026 10:12")}</td><td>{n(f"68,4{NB}kg")} <span class="doc-manque">corrigé</span><br>saisi d\'abord {n(f"86,4{NB}kg")} (SRC-A-007-v1), corrigé à {n("10:15")} : erreur de saisie</td><td>SRC-A-007-v2</td></tr>'
           '</tbody></table>')
    body = (doc_entete(f"Cabinet des Tilleuls<br>Préparation pour {NORA}")
            + '<h1>Brief de préparation</h1>'
            + '<div class="doc-statut">BROUILLON — réservé aux professionnels<small>Version 1, non validée · sources à jour · ne peut pas être publiée dans l\'espace patient</small></div>'
            + meta([("Patiente", f"MARTIN Claire · naissance {n('18/04/1978')} · dossier {n('R-A-001')}"), ("Visite prévue", f"{n(dh_pro(A1['startsAt']))} · {NORA} · APT-A-001"),
                    ("Dernière consultation", n("20/10/2026")), ("Version", f"BRIEF-A-001-v1 · brouillon · généré le {n(dh_pro(BRIEF['generatedAt']))} (tâche AI-A-001, fixture déterministe)"),
                    ("Validation", "aucune"), ("Exporté", f"{n(EXPORT)} · heure de référence du jeu de démonstration")])
            + f'<h2>Synthèse générée à relire</h2><div class="doc-encadre doc-encadre--declare"><p>{text}</p></div>'
            + '<p class="doc-note">Texte généré, non validé. Ces observations ne constituent pas une proposition de diagnostic ou de traitement.</p>'
            + '<h2>Informations manquantes et discordantes</h2>'
            + f'<p><strong>Traitements actuels déclarés</strong> : non renseignés. Ne pas écrire « aucun traitement ».</p>'
            + f'<p><strong>Allergies</strong> : deux déclarations datées divergent{ref(2)}{ref(1)} — absence déclarée le {n("14/09/2026")} ; souvenir d\'une réaction rapporté le {n("02/11/2026")}, non confirmé. Ne pas conclure à l\'absence.</p>'
            + mes)
    if mode == "long":
        body += (
            '<h2>Chronologie et provenance</h2><p>La déclaration du 14 septembre est une déclaration négative recueillie ce jour. Elle ne permet pas de conclure à une absence permanente d’allergie. La déclaration du 2 novembre est rapportée par la patiente, reste non confirmée et ne précise ni date exacte ni nature de la réaction. Présenter les deux sources sans en effacer une ni les fusionner en une certitude.</p>'
            + f'<h2>Mesures relues</h2><p>La pression artérielle du 14 septembre est {n(f"128/80{NB}mmHg")} ; celle du 20 octobre est {n(f"134/82{NB}mmHg")}. Le poids du 20 octobre a été corrigé de {n(f"86,4")} à {n(f"68,4{NB}kg")} pour une erreur de saisie. La valeur historique et le motif restent consultables. La correction ne crée pas un nouvel événement de mesure.</p>'
            + '<h2>Biologie disponible</h2>'
            + '<table class="doc-table"><thead><tr><th scope="col">Analyse</th><th scope="col">Prélèvement</th><th scope="col">Valeur</th><th scope="col">Intervalle fourni</th><th scope="col">Source</th></tr></thead><tbody>'
            + "".join(f'<tr><td>{esc(l["analyte"])}</td><td>{n(d_court(l["sampledAt"]))}</td><td>{n(val(l["value"], l["unit"]))}</td><td>{n(num(l["referenceInterval"]["low"]) + "–" + num(l["referenceInterval"]["high"]) + NB + l["referenceInterval"]["unit"])}</td><td>{l["sourceId"]}</td></tr>' for l in DATA["labResults"])
            + '</tbody></table><p>Les unités, dates et intervalles proviennent des sources du jeu fictif ; aucun seuil clinique supplémentaire n’est appliqué. Les résultats du document ont été saisis manuellement, sans extraction intelligente.</p>'
            + '<h2>Informations à compléter</h2><p>La liste actuelle des traitements est absente du jeu de données. Ne pas écrire « aucun traitement ». La réaction évoquée à la pénicilline reste à préciser. Une source datée et consultable ne garantit pas à elle seule qu’elle soutient une interprétation : chaque phrase de synthèse doit être relue dans son contexte.</p>'
            + '<h2>Travail préparatoire</h2><p>Les questions et déclarations de la patiente restent séparées des observations du praticien. L’ouverture d’une source retourne à la phrase correspondante. Une nouvelle source pertinente rend le brouillon obsolète ; une nouvelle génération ne doit pas effacer une correction humaine. Ce document n’est pas publiable directement dans l’espace patient.</p>')
    body += '<h2>Sources citées</h2>' + src_list([(1, "SRC-A-005"), (2, "SRC-A-001"), (3, "SRC-A-002"), (4, "SRC-A-003")])
    body += '<p class="doc-note">Sources consultées non citées : SRC-A-004, SRC-A-006.</p>'
    if mode == "long":
        body += annexe(copies=3)
    return doc(body, "MARTIN Claire · R-A-001 · Brief de préparation", "BRIEF-A-001-v1 · brouillon", "Brief de préparation, Claire Martin, brouillon version 1")


# ═════════════════════════════════ P-02 consultation report ═════════════════════════════════
def p02(mode="court"):
    if mode == "incomplet":
        body = (doc_entete("Cabinet des Tilleuls<br>Dr Sami Rahmani")
                + '<h1>Compte rendu de consultation</h1>'
                + '<div class="doc-statut">BROUILLON — non validé · réservé aux professionnels<small>Aucune date de validation ni de publication : ce brouillon ne porte pas de pied de validation.</small></div>'
                + meta([("Patient", f"BERNARD Louis · naissance {n('09/07/1991')} · dossier {n('R-A-002')}"), ("Consultation", f"CONS-A-002 · en cours depuis {n('02/11/2026 14:10')} · sans rendez-vous associé"),
                        ("Auteur", "Dr Sami Rahmani"), ("Validation", "aucune"), ("Exporté", f"{n(EXPORT)} · heure de référence du jeu de démonstration")])
                + f'<h2>Motif</h2><p>Première consultation (repris du motif du rendez-vous du {n("02/11/2026 14:00")}).</p>'
                + '<h2>Rapporté par le patient</h2><p><span class="doc-manque">Préparation de visite non reçue</span> Rien n\'a été déclaré dans cette consultation.</p>'
                + '<h2>Observations</h2><p><span class="doc-manque">À compléter</span></p><p class="doc-note">Une zone incomplète reste explicitement incomplète, sans texte automatique qui suggère un examen normal.</p>'
                + '<h2>Biologie</h2><p><span class="doc-manque">Aucun résultat dans le dossier</span> Ne pas conclure à un bilan normal.</p>'
                + '<h2>Informations à compléter</h2><p>Antécédents, allergies et traitements déclarés : non renseignés dans le dossier fictif.</p>')
        return doc(body, "BERNARD Louis · R-A-002 · Compte rendu", "CONS-A-002 · brouillon", "Compte rendu de consultation, Louis Bernard, brouillon")
    texte = REP["text"].replace("134/82 mmHg", f'{n(f"134/82{NB}mmHg")}{ref(2)}').replace("relus avec Claire Martin.", f"relus avec Claire Martin.{ref(1)}{ref(3)}").replace("fictif.", f"fictif.{ref(3)}")
    body = (doc_entete(f"Cabinet des Tilleuls<br>{NORA}")
            + '<h1>Compte rendu de consultation</h1>'
            + '<div class="doc-statut doc-statut--valide">VALIDÉ — réservé aux professionnels<small>Version 1 · aucune publication : cette version n\'est pas visible dans l\'espace patient</small></div>'
            + meta([("Patiente", f"MARTIN Claire · naissance {n('18/04/1978')} · dossier {n('R-A-001')}"), ("Consultation", f"CONS-A-001 · {n('20/10/2026')} · terminée à {n('10:35')} · rendez-vous APT-A-003"),
                    ("Auteur", NORA), ("Validation", f"{NORA} · {n('20/10/2026 10:40')}"), ("Publication", "aucune"), ("Version", "REPORT-A-001-v1"),
                    ("Exporté", f"{n(EXPORT)} · heure de référence du jeu de démonstration")])
            + f'<h2>Compte rendu</h2><p>{texte}</p>'
            + '<table class="doc-table"><caption>Observations datées de la consultation</caption><thead><tr><th scope="col">Mesure</th><th scope="col">Heure</th><th scope="col">Valeur</th><th scope="col">Source</th></tr></thead><tbody>'
            + f'<tr><td>Pression artérielle</td><td>{n("10:10")}</td><td>{n(f"134/82{NB}mmHg")}</td><td>SRC-A-004</td></tr>'
            + f'<tr><td>Poids</td><td>{n("10:12")}</td><td>{n(f"68,4{NB}kg")} <span class="doc-manque">corrigé</span> (saisi {n("86,4")} puis corrigé à {n("10:15")})</td><td>SRC-A-007-v2</td></tr></tbody></table>')
    if mode == "long":
        body += ('<h2>Motif et contexte</h2><p>Consultation de suivi du 20 octobre. Les données présentées sont celles disponibles pour cet événement. Ce compte rendu historique ne se transforme pas automatiquement lorsqu’une préparation d’une visite ultérieure est saisie. Une correction d’une source citée doit en revanche entraîner une nouvelle revue de la version concernée.</p>'
                 + '<h2>Informations rapportées et observations</h2><p>La pression a été mesurée au cabinet à 10 h 10 ; la source en conserve la date et l’auteur. Les résultats du 12 octobre ont été relus. Les traitements actuels ne sont pas renseignés ; cette absence est une limite du contenu, pas une constatation d’absence de traitement. Aucun diagnostic ni prescription n’est ajouté pour compléter la présentation.</p>'
                 + '<h2>Traçabilité</h2><p>La consultation CONS-A-001 est reliée à un rendez-vous terminé, mais les deux objets restent distincts. La fin de la consultation n’a pas publié ce rapport. L’auteur et le validateur sont affichés séparément même s’il s’agit ici de la même praticienne. Les versions ultérieures nécessiteront un nouveau brouillon et conserveront la version validée.</p>'
                 + '<h2>Diffusion</h2><p>Cette version du rapport n’a pas de publication dans le jeu de données. Le portail patient doit donc refuser son téléchargement, même si une autre explication a été publiée. Une publication future sélectionnerait explicitement la version, le destinataire et les pièces autorisées. Les brouillons et annotations internes resteraient exclus de cette projection.</p>')
    body += '<h2>Sources citées</h2>' + src_list([(1, "SRC-A-003"), (2, "SRC-A-004"), (3, "SRC-A-006")])
    body += (f'<div class="doc-signature">Rédigé par {NORA} · validé par {NORA} le {n("20/10/2026")} à {n("10:40")} · aucune publication.</div>')
    if mode == "long":
        body += annexe(copies=5)
    return doc(body, "MARTIN Claire · R-A-001 · Compte rendu du 20/10/2026", "REPORT-A-001-v1 · validé, non publié", "Compte rendu de consultation, Claire Martin, version 1 validée")


# ═════════════════════════════════ P-03 published patient explanation ═════════════════════════════════
def p03(mode="court"):
    if mode == "incomplet":
        body = (doc_entete("Cabinet des Tilleuls<br>Aperçu interne")
                + '<h1>Informations après votre consultation</h1>'
                + '<div class="doc-statut doc-statut--echec">APERÇU INTERNE — BROUILLON NON PUBLIÉ<small>Non publiable en l\'état · ce document n\'apparaît pas dans l\'espace patient</small></div>'
                + meta([("Destinataire prévue", "Claire Martin"), ("État", "brouillon · informations à relire"), ("Publication", "aucune"), ("Exporté", f"{n(EXPORT)} · par l\'équipe soignante")])
                + '<h2>Ce qui manque</h2><p>Les traitements actuels ne sont pas renseignés. <span class="doc-manque">à compléter</span></p>'
                + '<p>Une génération automatique en échec ne produit aucun texte publiable. Le praticien peut compléter une explication manuelle puis la relire et la valider ; aucune apparition automatique dans l\'espace patient n\'est prévue.</p>'
                + '<p class="doc-note">Variante de gabarit : les sources manquantes bloquent les affirmations non soutenues, elles ne les remplacent pas.</p>')
        return doc(body, "Aperçu interne · Claire Martin", "Brouillon non publié", "Aperçu interne d'une explication non publiée")
    grants = {g["sourceId"]: g for g in PUB2["sourceGrants"]}
    order = [("SRC-A-005", 1), ("SRC-A-001", 2), ("SRC-A-006", 3)]
    phr = [p.strip() + ("" if p.strip().endswith(".") else ".") for p in PV2["text"].split(". ") if p.strip()]
    text = f'{phr[0][:-1]}{ref(1)}. {phr[1][:-1]}{ref(2)}. {phr[2][:-1]}{ref(3)}.'
    body = (doc_entete("Cabinet des Tilleuls<br>Espace patient de Claire Martin")
            + '<h1>Informations après votre consultation du 20 octobre 2026</h1>'
            + f'<div class="doc-statut doc-statut--publie">PUBLIÉE POUR CLAIRE MARTIN<small>Version 2 · relue et validée par {NORA} le 2 novembre 2026 à 14 h 20 · publiée le 2 novembre 2026 à 14 h 25 · remplace la version du 20 octobre 2026</small></div>'
            + meta([("Pour", "Claire Martin · espace patient du Cabinet des Tilleuls"), ("Version", "PATIENT-A-001-v2 · publication PUB-A-002"), ("Téléchargé", f"{n(EXPORT)}")])
            + f'<h2>Ce que votre praticienne a écrit pour vous</h2><p style="font-size:11.5pt;line-height:1.55">{text}</p>'
            + '<h2>Extraits partagés par votre praticienne</h2><table class="doc-table"><thead><tr><th scope="col" style="width:12mm">Repère</th><th scope="col" style="width:36mm">Nature et date</th><th scope="col">Extrait partagé</th></tr></thead><tbody>'
            + "".join(f'<tr><td>[{k}]</td><td>{esc(grants[s]["publicLabel"])}<br>{n(d_court(S(s)["occurredAt"]))}</td><td><blockquote>« {esc(grants[s]["approvedExcerpt"])} »</blockquote></td></tr>' for s, k in order)
            + '</tbody></table><p class="doc-note">Seuls ces extraits ont été partagés. Les autres documents de votre dossier ne sont pas inclus. Ce document ne remplace pas un avis médical ; en cas d\'urgence, appelez le 15.</p>')
    if mode == "long":
        body += ('<h2>Ce qui a été partagé</h2><p>Cette explication reprend les informations relues puis publiées pour votre espace. Elle ne donne pas accès à tous les documents de votre dossier. Les dates de rédaction, de validation et de publication sont indiquées séparément pour que vous puissiez reconnaître la version consultée.</p>'
                 + '<h2>Votre déclaration</h2><p>Le 2 novembre, vous avez indiqué vous souvenir d’une réaction à la pénicilline dans votre jeunesse, sans pouvoir préciser laquelle. La déclaration recueillie le 14 septembre mentionnait qu’aucune allergie n’était connue à cette date. Ces déclarations sont présentées avec leur date ; aucune des deux n’est supprimée pour simplifier le texte.</p>'
                 + '<h2>Informations manquantes</h2><p>La liste actuelle de vos traitements n’est pas renseignée dans cet exemple fictif. Cela ne signifie pas que vous ne prenez aucun traitement. La préparation de votre visite permet de signaler les informations et questions que vous souhaitez aborder ; elle reste distincte de ce que votre praticienne a personnellement observé.</p>'
                 + '<h2>Sources et corrections</h2><p>Les repères ouvrent les extraits que votre praticienne a choisi de partager. Un document non partagé reste inaccessible. Cette version remplace une explication précédente après correction d’une phrase qui présentait une ancienne déclaration comme une certitude permanente. Le brouillon d’origine n’est pas publié.</p>'
                 + '<h2>Version disponible</h2><p>Une information publiée peut être remplacée ou retirée explicitement. Un lien vers une version retirée ne permet plus d’en lire le contenu ou de la télécharger. Un fichier déjà téléchargé ne peut pas être repris automatiquement. L’espace indique la version actuelle ou un avis de retrait sans révéler les annotations internes de la praticienne.</p>')
        for c in (1, 2, 3):
            body += (f'<div class="doc-copie{" doc-saut" if c == 1 else ""}">Copie de test de mise en page {c}/3 — mêmes extraits partagés, aucune information supplémentaire</div>'
                     + '<table class="doc-table"><thead><tr><th scope="col" style="width:12mm">Repère</th><th scope="col" style="width:36mm">Nature et date</th><th scope="col">Extrait partagé</th></tr></thead><tbody>'
                     + "".join(f'<tr><td>[{k}]</td><td>{esc(grants[s]["publicLabel"])}<br>{n(d_court(S(s)["occurredAt"]))}</td><td><blockquote>« {esc(grants[s]["approvedExcerpt"])} »</blockquote>'
                               f'<span class="doc-note">Comment lire cet extrait : il est cité tel qu\'il a été partagé, avec sa date. Il ne remplace pas un échange avec votre praticienne.</span></td></tr>' for s, k in order)
                     + '</tbody></table>')
    return doc(body, "Claire Martin · Informations du 20/10/2026", "PUB-A-002 · PATIENT-A-001-v2", "Explication publiée pour Claire Martin, version 2")


# ═════════════════════════════════ S-39 export options and print preview / I-19 drawer ═════════════════════════════════
def pages(p, slug):
    """Preview images produced by gen/pdf.py from the real PDF (site/docs/<P>-<slug>-p<n>.png)."""
    out, k = [], 1
    while os.path.exists(os.path.join(DOCS_DIR, f"{p}-{slug}-p{k}.png")):
        out.append(f"{ROOT}/docs/{p}-{slug}-p{k}.png"); k += 1
    return out


def apercu(p, slug, maxn=6):
    imgs = pages(p, slug)
    if not imgs:
        return '<p class="etaye-3" style="margin:0">Aperçu non généré : lancez gen/pdf.py.</p>'
    fig = "".join(f'<figure><img src="{src}" alt="Page {i + 1} sur {len(imgs)}" loading="lazy"><figcaption>Page {i + 1} / {len(imgs)}</figcaption></figure>' for i, src in enumerate(imgs[:maxn]))
    more = f'<p class="etaye-3" style="margin:0;font-size:13px">{len(imgs) - maxn} autres pages dans le PDF.</p>' if len(imgs) > maxn else ""
    return f'<div class="etaye-apercu-a4">{fig}</div>{more}'


def options_form(doc_label, statut_html, audience, annexe_ok=True, disabled=False):
    return (f'<dl class="etaye-def" style="grid-template-columns:minmax(0,1fr);row-gap:2px"><dt>Document</dt><dd>{doc_label}</dd><dt>Version figée</dt><dd><div class="etaye-ligne" style="gap:4px 6px">{statut_html.replace(" · ", " ")}</div></dd><dt>Destinataires</dt><dd>{audience}</dd></dl>'
            + choix("x-opt", [("Inclure la liste des sources citées", "Auteur, type, dates, révision et emplacement"), ("Inclure l'annexe des sources du dossier", "Réservé aux professionnels")],
                    typ="checkbox", legend="Contenu", checked=(0, 1) if annexe_ok else (0,))
            + NOTE("Chaque page porte « Démonstration — personnes et données fictives », l'identifiant de version et « Page x / y ». L'export ne publie rien."))


NOTE = lambda t: f'<p class="etaye-3" style="font-size:13px;margin:0">{t}</p>'


def s39(mode="options"):
    fil_ = f'<a href="{href("S-07")}">Patients</a> › <a href="{href("S-09", "claire")}">MARTIN Claire</a> › <a href="{href("S-18")}">Brief de préparation</a>'
    stat = f'BRIEF-A-001-v1 · {st("business", "DRAFT")} · {statut("neutre", "Réservé aux professionnels", "verrou")}'
    form = options_form("Brief de préparation (P-01)", stat, "Professionnels du Cabinet des Tilleuls")
    top, prev, acts = "", apercu("P-01", "court"), btn("Fermer", "secondaire", href_=href("S-18")) + btn("Créer le PDF", "principal", "imprimer")
    if mode == "long":
        form = options_form("Compte rendu de consultation (P-02)", f'REPORT-A-001-v1 · {st("business", "VALIDATED")} · {st("publication", None)}', "Professionnels du Cabinet des Tilleuls")
        prev = apercu("P-02", "long", maxn=8)
        fil_ = f'<a href="{href("S-07")}">Patients</a> › <a href="{href("S-09", "claire")}">MARTIN Claire</a> › <a href="{href("S-20", "lecture-seule")}">Compte rendu du 20/10/2026</a>'
        top = bandeau("info", "Document long", f"{len(pages('P-02', 'long'))} pages : les en-têtes de tableau, l'identité de la patiente et la version se répètent sur chaque page.")
    if mode == "generation":
        top = bandeau("neutre", "Création du PDF en cours", "La version BRIEF-A-001-v1 est figée pour cet export. Vous pouvez quitter la page ; le fichier restera disponible ici. Aucun pourcentage n'est affiché.", icon="horloge")
        acts = btn("Fermer", "secondaire", href_=href("S-18")) + btn("Créer le PDF", "principal", "imprimer", disabled=True)
    if mode == "echec":
        top = bandeau("attention", "Le PDF n'a pas été créé", "La composition a échoué. Rien n'a été partagé ; la version reste inchangée. Réessayez ou revenez plus tard.", btn("Réessayer", "secondaire", "relancer"), role="alert")
    if mode == "perime":
        stat = f'BRIEF-A-001-v1 · {st("business", "DRAFT")} · {st("freshness", "STALE")}'
        form = options_form("Brief de préparation (P-01)", stat, "Professionnels du Cabinet des Tilleuls")
        top = bandeau("attention", "Les sources ont changé depuis cette version", "L'export reproduit la version telle qu'elle a été générée, avec la mention « Sources modifiées depuis cette version » sur chaque page (variante).", lien("Générer un nouveau brouillon", href("I-14", "existant")), role="alert")
    if mode == "police":
        top = bandeau("attention", "Le PDF n'a pas pu être composé", "Une police nécessaire n'a pas été chargée (gabarit). Aucun fichier dégradé n'est proposé : un PDF sans accents ou avec des caractères manquants serait trompeur.", btn("Réessayer", "secondaire", "relancer"), role="alert")
        acts = btn("Fermer", "secondaire", href_=href("S-18")) + btn("Créer le PDF", "principal", "imprimer", disabled=True)
    if mode == "non-autorise":
        body = (bandeau("neutre", "Export non autorisé", "Ce compte rendu n'a pas de publication : il ne peut pas être téléchargé depuis l'espace patient (SC-NOT-PUBLISHED). Seule l'information publiée le 2 novembre 2026 est disponible.", lien("Voir mes documents", href("S-28")), icon="verrou")
                + NOTE("Variante patient : la vérification d'accès précède la génération ; un fichier généré ne prouve pas un droit d'accès."))
        return patient_shell("Mes documents", body)
    body = (top + f'<div class="etaye-cols etaye-cols--2g"><div class="etaye-pile">{panneau("Options", form)}<div class="etaye-ligne">{acts}</div></div>'
            f'<div>{panneau("Aperçu A4", prev + NOTE("Pages du PDF réel, texte sélectionnable ; ce n'est pas une capture d'écran de l'application."))}</div></div>')
    return pro_shell("Briefs" if mode != "long" else "Comptes rendus", f'<div class="etaye-page">{entete("Exporter un document", "Format A4 · aperçu avant création", "", fil_)}{body}</div>', "nora", banner=banniere("R-A-001"))


def i19(mode="ouvert"):
    if mode == "patient":
        fond = patient_shell("Mes documents", '<h1 style="margin:0">Informations après votre consultation du 20 octobre 2026</h1>')
        corps = (f'<dl class="etaye-def" style="grid-template-columns:minmax(0,1fr)"><dt>Document</dt><dd>Informations après votre consultation du 20 octobre 2026</dd><dt>Version</dt><dd>Version 2, publiée le 2 novembre 2026 à 14 h 25</dd></dl>'
                 + apercu("P-03", "court", 2) + NOTE("Seule la version publiée actuelle peut être téléchargée. Une copie téléchargée ne peut pas être reprise."))
        return tiroir(fond, "Télécharger en PDF", corps, btn("Fermer", "secondaire", patient=True) + btn("Créer le PDF", "principal", patient=True))
    fond = s39("options")
    stat = f'BRIEF-A-001-v1 · {st("business", "DRAFT")}'
    corps = options_form("Brief de préparation (P-01)", stat, "Professionnels du Cabinet des Tilleuls") + apercu("P-01", "court", 2)
    if mode == "brouillon":
        corps = bandeau("info", "Brouillon exporté comme tel", "Chaque page portera « BROUILLON — réservé aux professionnels ». Aucun pied de validation n'est ajouté.") + corps
    if mode == "erreur":
        corps = bandeau("attention", "Accès à vérifier de nouveau", "Vos droits ou la version ont changé depuis l'ouverture (gabarit). Rien n'a été créé ; rechargez pour exporter la version actuelle.", btn("Recharger", "secondaire", "relancer"), role="alert") + corps
    return tiroir(fond, "Exporter un document", corps, btn("Fermer", "secondaire") + btn("Créer le PDF", "principal", "imprimer", disabled=mode == "erreur"), large=True)


# ═════════════════════════════════ M-01…M-05 e-mails ═════════════════════════════════
ORIGIN = "https://etaye.example.test"


def mnote(t):
    return f'<p class="mail-entete" style="margin-top:8px">Annotation de maquette (hors e-mail){NNB}: {esc(t)}</p>' if t else ""


def mail(sujet, preheader, dest, titre, paras, action, lien_aff, aide, mode="html", note=""):
    if mode == "texte":
        t = (f"Objet : {sujet}\nÀ : {dest}\nDe : Étaye <ne-pas-repondre@example.test>\n\n{titre}\n\n" + "\n\n".join(re.sub(r"<[^>]+>", "", p) for p in paras)
             + f"\n\n{action} :\n{lien_aff}\n\n{aide}\n\n—\nÉtaye · Démonstration — personnes et données fictives\nMessage automatique envoyé à {dest}. Ne répondez pas à ce message.")
        return f'<div class="mail-cadre"><div class="mail-entete"><span>Version texte brut</span></div><pre class="mail-texte">{esc(t)}</pre>{mnote(note)}</div>'
    sombre = mode == "sombre"
    lg = f"{ROOT}/logos/etaye-logo-sombre.svg" if sombre else f"{ROOT}/logos/etaye-logo.svg"
    return (f'<div class="mail-cadre{" mail-cadre--sombre" if sombre else ""}"><div class="mail-entete"><span>De{NNB}: <b>Étaye</b> &lt;ne-pas-repondre@example.test&gt;</span><span>À{NNB}: <b>{esc(dest)}</b></span>'
            f'<span>Objet{NNB}: <b>{esc(sujet)}</b></span><span>Aperçu{NNB}: {esc(preheader)}</span></div>'
            f'<div class="mail" role="article" aria-label="{esc(sujet)}"><div class="mail__logo"><img src="{lg}" alt="Étaye"></div><div class="mail__corps"><h1>{esc(titre)}</h1>'
            + "".join(f"<p>{p}</p>" for p in paras)
            + f'<a class="mail__action" href="#">{esc(action)}</a><p class="mail__lien">{esc(lien_aff)}</p><p class="mail__aide">{aide}</p></div>'
            f'<div class="mail__pied">Étaye · Démonstration — personnes et données fictives<br>Message automatique envoyé à {esc(dest)}. Ne répondez pas à ce message.</div></div>{mnote(note)}</div>')


def m01(mode="html"):
    inv = by_id("invitations", "INV-TEAM-01")
    return mail("Invitation à rejoindre Étaye", "Le Cabinet des Tilleuls vous invite à rejoindre son équipe.", "alex.riviere@example.test", "Invitation à rejoindre une équipe",
                ["Le Cabinet des Tilleuls vous invite à rejoindre son équipe sur Étaye.",
                 f"Ce lien est personnel et expire le <strong>{d_long(inv['expiresAt'], jour=True)} à {h_patient(inv['expiresAt'])}</strong> (heure de Paris). Les droits proposés s'affichent après ouverture, sur une page protégée."],
                "Consulter l'invitation", f"{ORIGIN}/invitation/… (lien personnel, jeton masqué)",
                "Vous n'attendiez pas cette invitation ? Ignorez ce message : aucun accès n'est créé sans activation.", mode)


def m02(mode="html"):
    return mail("Votre invitation à l'espace patient Étaye", "Votre cabinet vous propose d'activer votre espace.", "claire.martin@example.test", "Activez votre espace patient",
                ["Le Cabinet des Tilleuls vous propose d'activer votre espace patient sur Étaye.",
                 'Ce lien est personnel, ne sert qu\'une fois et expire le <span class="mail-var">date d\'expiration</span> (non fournie par le jeu de démonstration).'],
                "Activer mon espace patient", f"{ORIGIN}/activation/… (lien personnel, jeton masqué)",
                "Pour toute question, contactez le secrétariat du Cabinet des Tilleuls. Vous n'êtes pas à l'origine de cette demande ? Ignorez ce message.", mode)


def m03(mode="html"):
    return mail("Récupérer votre accès Étaye", "Une demande de récupération a été effectuée.", "nora.lefebvre@example.test", "Récupérer votre accès",
                ["Une demande de récupération a été effectuée pour ce compte le lundi 2 novembre 2026 à 16 h 00.",
                 "Si vous en êtes à l'origine, choisissez un nouveau mot de passe. Le lien ne sert qu'une fois et expire rapidement."],
                "Choisir un nouveau mot de passe", f"{ORIGIN}/recuperation/… (lien à usage unique, jeton masqué)",
                "Vous n'êtes pas à l'origine de cette demande ? Ignorez ce message : votre mot de passe ne change pas.", mode,
                "heure de la demande = heure de référence du jeu de démonstration (variante).")


def m04(mode="html", evt="confirme"):
    t = {"confirme": ("a été confirmé", "Votre rendez-vous du mardi 3 novembre 2026 à 10 h 30 (heure de Paris) au Cabinet des Tilleuls a été confirmé."),
         "modifie": ("a été modifié", "Votre rendez-vous a été déplacé au mardi 3 novembre 2026 à 11 h 00 (heure de Paris), au Cabinet des Tilleuls."),
         "annule": ("a été annulé", "Votre rendez-vous du mardi 3 novembre 2026 à 10 h 30 (heure de Paris) au Cabinet des Tilleuls a été annulé.")}[evt]
    return mail("Votre rendez-vous Étaye", f"Votre rendez-vous {t[0]}.", "claire.martin@example.test", f"Votre rendez-vous {t[0]}",
                [t[1], "Le détail est disponible dans votre espace patient."],
                "Consulter le rendez-vous", f"{ORIGIN}/espace/rendez-vous", "Pour toute question, contactez le secrétariat du Cabinet des Tilleuls.", mode,
                {"confirme": "APT-A-001.", "modifie": "variante (déplacement vers 11:00, comme S-27 rdv-change).", "annule": "variante (annulation)."}[evt])


def m05(mode="html", evt="mise-a-jour"):
    t = {"disponible": ("Une information est disponible dans votre espace Étaye", "Une information est disponible", "Le Cabinet des Tilleuls a partagé une information avec vous."),
         "mise-a-jour": ("Une publication de votre espace a été mise à jour", "Une publication a été mise à jour", "Le Cabinet des Tilleuls a mis à jour une information de votre espace."),
         "retrait": ("Une publication de votre espace a été mise à jour", "Un document n'est plus disponible", "Une information n'est plus disponible dans votre espace.")}[evt]
    return mail(t[0], "Connectez-vous pour consulter les informations disponibles.", "claire.martin@example.test", t[1],
                [t[2], "Connectez-vous pour consulter les informations disponibles."],
                "Ouvrir mon espace", f"{ORIGIN}/espace", "Ce message ne contient aucune information médicale. Pour toute question, contactez le secrétariat du Cabinet des Tilleuls.", mode,
                {"disponible": "PUB-A-001, publiée le 20/10/2026 10:50.", "mise-a-jour": "PUB-A-002 remplace PUB-A-001 le 02/11/2026 14:25.", "retrait": "variante V-WITHDRAWN ; le motif interne n'est jamais envoyé."}[evt])


A4 = ["a4"]
MV = ["mail", "mail-narrow"]


def mstates(fn, label, extra=None):
    st_ = [{"slug": "html", "label": f"{label} — HTML 600 px, one action, masked token link", "html": fn("html"), "viewports": MV},
           {"slug": "texte", "label": f"{label} — plain-text equivalent", "html": fn("texte"), "viewports": MV},
           {"slug": "sombre", "label": f"{label} — dark client rendering", "html": fn("sombre"), "viewports": MV}]
    return st_ + (extra or [])


SCREENS = [
    {"id": "P-01", "name": "Brief de préparation (A4)", "states": [
        {"slug": "court", "label": "Short — BRIEF-A-001-v1 exact text, DRAFT internal, measurements with SRC-A-007 correction lineage", "html": p01(), "viewports": A4, "title": "Brief de préparation — MARTIN Claire — BRIEF-A-001-v1 (brouillon)"},
        {"slug": "long", "label": "Long — content samples + clinician appendix and 3 labelled layout test copies", "html": p01("long"), "viewports": A4, "title": "Brief de préparation — MARTIN Claire — BRIEF-A-001-v1 (brouillon, mise en page longue)"},
        {"slug": "incomplet", "label": "Incomplete — Louis Bernard, AI-A-002 failed, no synthesis, no risk wording", "html": p01("incomplet"), "viewports": A4, "title": "Brief de préparation — BERNARD Louis — aucun brief généré"},
    ]},
    {"id": "P-02", "name": "Compte rendu de consultation (A4)", "states": [
        {"slug": "court", "label": "Short — REPORT-A-001-v1 exact text, VALIDATED 20 Oct 10:40, internal (no publication)", "html": p02(), "viewports": A4, "title": "Compte rendu de consultation — MARTIN Claire — REPORT-A-001-v1"},
        {"slug": "long", "label": "Long — structured paragraphs + appendix and 5 labelled layout test copies", "html": p02("long"), "viewports": A4, "title": "Compte rendu de consultation — MARTIN Claire — REPORT-A-001-v1 (mise en page longue)"},
        {"slug": "incomplet", "label": "Incomplete — CONS-A-002 draft, preparation not received, no validation footer", "html": p02("incomplet"), "viewports": A4, "title": "Compte rendu de consultation — BERNARD Louis — brouillon"},
    ]},
    {"id": "P-03", "name": "Explication publiée pour la patiente (A4)", "states": [
        {"slug": "court", "label": "Short — PATIENT-A-001-v2 exact text, validated 14:20 vs published 14:25, three approved excerpts", "html": p03(), "viewports": A4, "title": "Informations après votre consultation — Claire Martin — version 2"},
        {"slug": "long", "label": "Long — patient-facing paragraphs + 3 labelled layout test copies of the authorized table", "html": p03("long"), "viewports": A4, "title": "Informations après votre consultation — Claire Martin — version 2 (mise en page longue)"},
        {"slug": "incomplet", "label": "Incomplete — internal draft preview, not publishable", "html": p03("incomplet"), "viewports": A4, "title": "Aperçu interne — explication non publiée"},
    ]},
    {"id": "S-39", "name": "Options d'export et aperçu avant impression", "states": [
        {"slug": "options", "label": "P-01 options — frozen version and status, audience, sources, real A4 page previews", "html": s39()},
        {"slug": "long", "label": "Long P-02 preview — page count and repeated headers", "html": s39("long")},
        {"slug": "generation", "label": "Generating — version frozen, no percentage", "html": s39("generation")},
        {"slug": "echec", "label": "Generation failed — nothing shared", "html": s39("echec")},
        {"slug": "perime", "label": "Stale source snapshot (variant) — mention on every page", "html": s39("perime")},
        {"slug": "police", "label": "Font/render error (template) — no degraded file", "html": s39("police")},
        {"slug": "non-autorise", "label": "Unauthorized — patient, SC-NOT-PUBLISHED", "html": s39("non-autorise")},
    ]},
    {"id": "I-19", "name": "Exporter un document", "states": [
        {"slug": "ouvert", "label": "Drawer — template, exact status/version, audience, sources, preview", "html": i19()},
        {"slug": "brouillon", "label": "Draft labelling notice", "html": i19("brouillon")},
        {"slug": "erreur", "label": "Reauthorization needed (template) — nothing created", "html": i19("erreur")},
        {"slug": "patient", "label": "Patient — current publication only", "html": i19("patient")},
    ]},
    {"id": "M-01", "name": "Invitation d'équipe (e-mail)", "states": mstates(m01, "INV-TEAM-01 to alex.riviere@example.test, expires 5 Nov 12:00")},
    {"id": "M-02", "name": "Activation de l'espace patient (e-mail)", "states": mstates(m02, "Portal activation to claire.martin@example.test, expiry not in dataset")},
    {"id": "M-03", "name": "Récupération d'accès (e-mail)", "states": mstates(m03, "Recovery to nora.lefebvre@example.test (request time is a variant)")},
    {"id": "M-04", "name": "Rendez-vous (e-mail)", "states": mstates(m04, "APT-A-001 confirmed, Paris time") + [
        {"slug": "modifie", "label": "Rescheduled to 11:00 (variant)", "html": m04("html", "modifie"), "viewports": MV},
        {"slug": "annule", "label": "Cancelled (variant)", "html": m04("html", "annule"), "viewports": MV}]},
    {"id": "M-05", "name": "Publication (e-mail)", "states": mstates(m05, "PUB-A-002 replaces PUB-A-001 — no clinical content") + [
        {"slug": "disponible", "label": "First publication PUB-A-001", "html": m05("html", "disponible"), "viewports": MV},
        {"slug": "retrait", "label": "Withdrawal — neutral notice, no reason (V-WITHDRAWN)", "html": m05("html", "retrait"), "viewports": MV}]},
]

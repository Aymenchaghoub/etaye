"""Patient portal — S-25 home, S-26 appointments & booking, S-27 visit preparation, S-28 published list,
S-29 published detail (patient source excerpt = I-09 patient variant), S-30 authorized document viewer, I-28 download.
Claire Martin (ACC-CLAIRE, R-A-001). Only PUB-A-002 is published; REPORT-A-001-v1 is validated but never shown (SC-NOT-PUBLISHED).
"""
from lib import *

A1, A3, A5 = (by_id("appointments", i) for i in ("APT-A-001", "APT-A-003", "APT-A-005"))
PREP = by_id("preparations", "PREP-A-001")
PUB1, PUB2 = by_id("publications", "PUB-A-001"), by_id("publications", "PUB-A-002")
V1, V2 = by_id("documentVersions", "PATIENT-A-001-v1"), by_id("documentVersions", "PATIENT-A-001-v2")
NORA = "Dr Nora Lefebvre"


def quand(iso, jour=True):
    s = dh_patient(iso, jour)
    return s[0].upper() + s[1:]


def carte(body, extra=""):
    return f'<section class="etaye-carte-patient"{extra}>{body}</section>'


def rdv_carte(prep_etat="envoyee", actions=True):
    a = A1
    if prep_etat == "envoyee":
        item = (f'<li><span class="etaye-case etaye-case--faite" aria-hidden="true">{ic("valide")}</span>'
                f'<span class="etaye-fait">Préparation de visite envoyée le {d_long(PREP["submittedAt"], jour=True)} à {h_patient(PREP["submittedAt"])}</span></li>')
        acts = btn("Voir ma préparation", "principal", patient=True, href_=href("S-27", "envoyee")) + btn("Déplacer le rendez-vous", "secondaire", patient=True, href_=href("S-26", "deplacer"))
    else:
        item = f'<li><span class="etaye-case" aria-hidden="true"></span><span>Préparer ma visite (facultatif)</span></li>'
        acts = btn("Préparer ma visite", "principal", patient=True, href_=href("S-27")) + btn("Déplacer le rendez-vous", "secondaire", patient=True, href_=href("S-26", "deplacer"))
    return carte(f'{st("appointment", "BOOKED", "Rendez-vous réservé")}'
                 f'<p class="etaye-carte-patient__quand" style="margin:var(--espace-3) 0 0">{quand(a["startsAt"])}</p>'
                 f'<p style="margin:var(--espace-1) 0 0">{NORA} · {esc(a["reason"])}</p><p class="etaye-carte-patient__ou" style="margin:0">Cabinet des Tilleuls · 30 minutes</p>'
                 f'<ul class="etaye-a-faire" aria-label="À préparer">{item}</ul>'
                 + (f'<div class="etaye-actions-patient">{acts}</div>' if actions else ""))


def pub_ligne(pub, ver, etat="PUBLISHED", lien_=True):
    t = "Informations après votre consultation du 20 octobre 2026"
    d = f'Publiée le {d_long(pub["publishedAt"])} à {h_patient(pub["publishedAt"])} par {NORA} · version {ver["version"]}'
    a = f'<a href="{href("S-29", "v2" if ver["version"] == 2 else "remplacee")}">{t}</a>' if lien_ else f'<span>{t}</span>'
    return (f'<li>{ic("document", 24, "etaye-2")}<div class="etaye-pliste__txt">{a}<small>{d}</small></div>'
            f'{st("publication", etat, "Version actuelle" if etat == "PUBLISHED" else None)}</li>')


# ── S-25 home ────────────────────────────────────────────────────────────────
def s25(mode="accueil"):
    parts = ['<div><h1 style="margin:0">Votre espace</h1></div>']
    if mode == "indisponible":
        parts.append(bandeau("attention", "Votre espace ne répond pas pour le moment", "Vos rendez-vous et documents ne sont pas affichés. Réessayez dans quelques minutes ou appelez le secrétariat du cabinet.", btn("Réessayer", "secondaire", "relancer", patient=True), role="alert"))
        return patient_shell("Accueil", "".join(parts))
    if mode == "lien-retire":
        parts.append(bandeau("neutre", "Votre espace n'est plus relié à ce cabinet", "Le lien entre votre compte et votre dossier au Cabinet des Tilleuls a été retiré. Les informations de ce cabinet ne sont plus affichées. Contactez le secrétariat si vous pensez que c'est une erreur.", icon="verrou"))
        parts.append('<p class="etaye-3" style="font-size:14px;margin:0">Gabarit : le lien de Claire Martin est actif dans le jeu de données.</p>')
        return patient_shell("Accueil", "".join(parts))
    parts.append(f'<section class="etaye-pile" aria-labelledby="h-rdv"><h2 id="h-rdv" style="margin:0">Prochain rendez-vous</h2>'
                 + (rdv_carte() if mode != "sans-rdv" else carte(f'<p style="margin:0">Vous n\'avez pas de rendez-vous à venir au Cabinet des Tilleuls.</p><div class="etaye-actions-patient" style="margin-top:var(--espace-4)">{btn("Prendre rendez-vous", "principal", patient=True, href_=href("S-26", "creneaux"))}</div>'))
                 + '</section>')
    if mode == "sans-publication":
        docs = '<p style="margin:0" class="etaye-2">Votre praticienne n\'a encore rien publié pour vous. Les informations apparaissent ici seulement quand elle les publie.</p>'
    elif mode == "retiree":
        docs = (bandeau("neutre", "Une information a été retirée", f"L'information publiée le {d_long(PUB2['publishedAt'])} n'est plus disponible dans votre espace. Pour toute question, contactez le cabinet.", icon="info")
                + '<p class="etaye-3" style="font-size:14px;margin:0">Variante V-WITHDRAWN, issue de PUB-A-002.</p>')
    else:
        note = (f'<p class="etaye-3" style="font-size:15px;margin:var(--espace-2) 0 0">La version du 20 octobre a été remplacée le {d_long(PUB2["publishedAt"])}.</p>' if mode == "remplacee" else "")
        docs = f'<ul class="etaye-pliste">{pub_ligne(PUB2, V2)}</ul>{note}'
    parts.append(f'<section class="etaye-pile" aria-labelledby="h-docs"><div class="etaye-ligne" style="justify-content:space-between"><h2 id="h-docs" style="margin:0">Ce que votre cabinet a publié pour vous</h2></div>{docs}'
                 f'<div>{lien("Tous mes documents", href("S-28"))}</div></section>')
    parts.append(f'<section class="etaye-pile"><h2 style="margin:0">Contacter le cabinet</h2><p style="margin:0" class="etaye-2">Cabinet des Tilleuls · par le secrétariat. Cet espace ne remplace pas un avis médical ; en cas d\'urgence, appelez le 15.</p></section>')
    return patient_shell("Accueil", "".join(parts))


# ── S-26 appointments & booking (patient variants of I-10/I-11/I-12, S-16) ──
def liste_rdv():
    def li(a, label, act=""):
        return (f'<li>{ic("agenda", 24, "etaye-2")}<div class="etaye-pliste__txt"><span>{quand(a["startsAt"])}</span>'
                f'<small>{NORA} · {esc(a.get("reason") or "Rendez-vous")}</small></div>{st("appointment", a["status"], label)}{act}</li>')
    return (f'<section class="etaye-pile"><h2 style="margin:0">À venir</h2><ul class="etaye-pliste">{li(A1, "Réservé", "")}</ul>'
            f'<div class="etaye-actions-patient">{btn("Voir le rendez-vous", "principal", patient=True, href_=href("S-26", "detail"))}{btn("Prendre un autre rendez-vous", "secondaire", patient=True, href_=href("S-26", "creneaux"))}</div></section>'
            f'<section class="etaye-pile"><h2 style="margin:0">Passés</h2><ul class="etaye-pliste">{li(A3, "Terminé")}{li(A5, "Non honoré")}</ul>'
            f'<p class="etaye-3" style="font-size:15px;margin:0">Un rendez-vous non honoré reste dans votre historique. Vous ne pouvez pas le modifier ; contactez le cabinet si c\'est une erreur.</p></section>')


def creneaux_patient(pris=("10:30",), choisi=None, perdu=None):
    out = []
    for t in ("09:00", "09:30", "10:00", "10:30", "11:00", "11:30"):
        dis = t in pris
        out.append(f'<button class="etaye-creneau" type="button" aria-pressed="{"true" if t == choisi else "false"}"{" disabled" if dis else ""} aria-label="{t.replace(":", " h ")}{" · indisponible" if dis else ""}">{t.replace(":", " h ")}</button>')
    return f'<div class="etaye-creneaux" role="group" aria-label="Horaires disponibles">{"".join(out)}</div>'


def jours_patient():
    d = [("lun.", 2, True), ("mar.", 3, False), ("mer.", 4, True), ("jeu.", 5, True), ("ven.", 6, True)]
    b = "".join(f'<button class="etaye-jour" type="button" aria-pressed="{"true" if n == 3 else "false"}"{" disabled" if dis else ""}>{l}<b>{n}</b>{"<small>complet</small>" if dis and n != 2 else ""}</button>' for l, n, dis in d)
    return f'<div class="etaye-jours" role="group" aria-label="Choisir un jour">{b}</div>'


def s26(mode="liste"):
    if mode == "liste":
        return patient_shell("Rendez-vous", f'<h1 style="margin:0">Mes rendez-vous</h1>{liste_rdv()}')
    if mode == "detail":
        dl = (f'<dl class="etaye-def" style="grid-template-columns:140px minmax(0,1fr);font-size:17px;line-height:26px"><dt>Quand</dt><dd>{quand(A1["startsAt"])} · 30 minutes</dd>'
              f'<dt>Avec</dt><dd>{NORA}</dd><dt>Où</dt><dd>Cabinet des Tilleuls</dd><dt>Motif</dt><dd>{esc(A1["reason"])}</dd><dt>Statut</dt><dd>{st("appointment", "BOOKED", "Réservé")}</dd>'
              f'<dt>Préparation</dt><dd>Envoyée le {d_long(PREP["submittedAt"], jour=True)} à {h_patient(PREP["submittedAt"])}</dd></dl>')
        acts = btn("Déplacer le rendez-vous", "principal", patient=True, href_=href("S-26", "deplacer")) + btn("Annuler le rendez-vous", "secondaire", patient=True, href_=href("S-26", "annuler"))
        return patient_shell("Rendez-vous", f'<a href="{href("S-26")}">Mes rendez-vous</a><h1 style="margin:0">Votre rendez-vous</h1>{carte(dl)}<div class="etaye-actions-patient">{acts}</div>'
                             '<p class="etaye-3" style="font-size:15px;margin:0">Variante patient de S-16 : seuls votre rendez-vous et son motif sont visibles.</p>')
    if mode in ("creneaux", "conflit", "aucun"):
        top = ""
        choisi = "11:00"
        if mode == "conflit":
            top = bandeau("attention", "Cet horaire vient d'être pris", "Quelqu'un a réservé 11 h 00 juste avant vous. Votre rendez-vous n'a pas été créé. Choisissez un autre horaire ; votre motif est conservé.", role="alert")
            body = creneaux_patient(("10:30", "11:00"), None)
        elif mode == "aucun":
            body = carte('<p style="margin:0">Aucun horaire n\'est disponible en ligne cette semaine avec Dr Nora Lefebvre.</p><p class="etaye-2" style="margin:var(--espace-2) 0 0">Le secrétariat du cabinet peut vous proposer un rendez-vous.</p>')
            return patient_shell("Rendez-vous", f'<h1 style="margin:0">Prendre rendez-vous</h1>{body}'
                                 '<p class="etaye-3" style="font-size:14px;margin:0">Gabarit : le jeu de données ne contient qu\'une plage, le mardi 3 novembre.</p>')
        else:
            body = creneaux_patient(("10:30",), choisi)
        form = (f'{top}<p style="margin:0">Avec {NORA} · Cabinet des Tilleuls · heure de Paris</p>{jours_patient()}'
                f'<h2 style="margin:0">Mardi 3 novembre 2026</h2>{body}'
                f'{champ("Motif (facultatif)", "Consultation de suivi" if mode == "conflit" else "", "p-motif", help_="Le motif est visible par l\'équipe du cabinet.")}'
                f'<div class="etaye-actions-patient">{btn("Choisir ce créneau", "principal", patient=True, disabled=mode == "conflit")}{btn("Retour", "secondaire", patient=True, href_=href("S-26"))}</div>')
        return patient_shell("Rendez-vous", f'<h1 style="margin:0">Prendre rendez-vous</h1><div class="etaye-form">{form}</div>')
    if mode == "deplacer":
        form = (f'<p style="margin:0">Rendez-vous actuel : {quand(A1["startsAt"])}</p><h2 style="margin:0">Nouvel horaire · mardi 3 novembre 2026</h2>{creneaux_patient(("10:30",), "11:00")}'
                f'<p class="etaye-2" style="margin:0">Votre rendez-vous actuel reste réservé tant que le nouveau n\'est pas confirmé.</p>'
                f'<div class="etaye-actions-patient">{btn("Confirmer le déplacement", "principal", patient=True)}{btn("Garder ce rendez-vous", "secondaire", patient=True, href_=href("S-26", "detail"))}</div>')
        return patient_shell("Rendez-vous", f'<h1 style="margin:0">Déplacer le rendez-vous</h1><div class="etaye-form">{form}</div>')
    if mode == "annuler":
        form = (f'<p style="margin:0">{quand(A1["startsAt"])} · {NORA}</p>'
                f'{champ("Pourquoi annulez-vous ? (facultatif)", "", "p-annul", kind="textarea", help_="Ce message est transmis au cabinet.")}'
                f'<p class="etaye-2" style="margin:0">L\'horaire sera de nouveau proposé à d\'autres personnes. Votre préparation reste enregistrée.</p>'
                f'<div class="etaye-actions-patient">{btn("Garder ce rendez-vous", "principal", patient=True, href_=href("S-26", "detail"), attrs="autofocus")}{btn("Annuler le rendez-vous", "secondaire", patient=True)}</div>')
        return patient_shell("Rendez-vous", f'<h1 style="margin:0">Annuler ce rendez-vous</h1><div class="etaye-form">{form}</div>')
    if mode == "annule":
        c = carte(f'{st("appointment", "CANCELLED", "Annulé")}<p class="etaye-carte-patient__quand" style="margin:var(--espace-3) 0 0">{quand(A1["startsAt"])}</p><p style="margin:var(--espace-1) 0 0">{NORA}</p>')
        return patient_shell("Rendez-vous", f'<h1 style="margin:0">Rendez-vous annulé</h1>{toast("Votre rendez-vous est annulé. Un e-mail de confirmation vous est envoyé.")}{c}'
                             '<p class="etaye-3" style="font-size:14px;margin:0">Gabarit : APT-A-001 reste réservé dans le jeu de données.</p>')
    if mode == "indisponible":
        return patient_shell("Rendez-vous", f'<h1 style="margin:0">Mes rendez-vous</h1>' + bandeau("attention", "La prise de rendez-vous en ligne ne répond pas", "Vos rendez-vous existants ne sont pas modifiés. Réessayez plus tard ou appelez le secrétariat.", btn("Réessayer", "secondaire", "relancer", patient=True), role="alert"))
    return ""


# ── S-27 visit preparation ───────────────────────────────────────────────────
def q(label, name, opts, checked=None, help_=None):
    h = f'<small style="display:block;color:var(--encre-2);font-size:15px">{help_}</small>' if help_ else ""
    return f'<div class="etaye-pile" style="gap:var(--espace-2)">{choix(name, opts, legend=label, checked=checked if checked is not None else ())}{h}</div>'


def s27(mode="brouillon"):
    s5 = by_id("sources", "SRC-A-005")
    head = f'<div><h1 style="margin:0">Préparer ma visite</h1><p class="etaye-2" style="margin:var(--espace-2) 0 0">Pour votre rendez-vous du {d_long(A1["startsAt"], jour=True)} à {h_patient(A1["startsAt"])} avec {NORA}. Tout est facultatif.</p></div>'
    if mode == "envoyee":
        recap = (f'<dl class="etaye-def" style="grid-template-columns:minmax(0,1fr);font-size:17px;line-height:26px">'
                 f'<dt>Ce que vous souhaitez aborder</dt><dd>« {esc(PREP["concerns"])} »</dd>'
                 f'<dt>Allergies ou réactions</dt><dd>« {esc(s5["text"])} »</dd>'
                 f'<dt>Traitements actuels</dt><dd>Non renseigné</dd></dl>')
        body = (head + bandeau("info", "Préparation envoyée au cabinet", f"Envoyée le {d_long(PREP['submittedAt'], jour=True)} à {h_patient(PREP['submittedAt'])}. Vos réponses restent vos déclarations : votre praticienne les relira, elles ne sont pas un avis médical.")
                + carte(recap) + f'<div class="etaye-actions-patient">{btn("Modifier ma préparation", "secondaire", patient=True, href_=href("S-27", "revision"))}</div>'
                + '<p class="etaye-3" style="font-size:15px;margin:0">Vous pouvez la modifier jusqu\'au début de la consultation. Une nouvelle version sera envoyée, l\'ancienne reste datée.</p>')
        return patient_shell("Préparer ma visite", body)
    err = mode == "erreur"
    filled = mode in ("brouillon", "erreur", "revision", "rdv-change", "session")
    top = ""
    if err:
        top = resume_erreurs([("pv-partage", "Partage : cochez la case pour envoyer votre préparation au cabinet")])
    if mode == "rdv-change":
        top = bandeau("attention", "Votre rendez-vous a changé", "Il a été déplacé au mardi 3 novembre 2026 à 11 h 00. Votre préparation est conservée et sera liée au nouveau rendez-vous.", role="alert")
    if mode == "session":
        top = bandeau("attention", "Votre session a expiré", "Vos réponses sont gardées sur cet appareil. Reconnectez-vous pour les envoyer ; rien n'a été transmis.", btn("Me reconnecter", "principal", patient=True, href_=href("S-02", "patient")), role="alert")
    if mode == "revision":
        top = bandeau("info", "Nouvelle version de votre préparation", f"La version envoyée le {d_long(PREP['submittedAt'])} à {h_patient(PREP['submittedAt'])} reste enregistrée et datée.")
    f = [top,
         champ("Ce que vous souhaitez aborder", PREP["concerns"] if filled else "", "pv-sujets", kind="textarea", rows=3, optional=True, focus=mode == "vide"),
         q("Avez-vous eu une allergie ou une réaction à un médicament ?", "pv-all", ["Oui", "Non", "Je ne sais pas", "Je préfère ne pas répondre"], checked=(0,) if filled else ()),
         champ("Si oui, décrivez ce dont vous vous souvenez", s5["text"] if filled else "", "pv-all-d", kind="textarea", rows=3, optional=True, help_="Même un souvenir imprécis est utile."),
         q("Vos traitements ont-ils changé depuis votre dernière visite ?", "pv-trt", ["Oui", "Non", "Je ne sais pas", "Je préfère ne pas répondre"], checked=(),
           help_="Sans réponse, votre praticienne verra « non renseigné », pas « non »."),
         f'<div class="etaye-pile" style="gap:var(--espace-1)">{choix("pv-partage", [("J\'accepte de partager cette préparation avec le Cabinet des Tilleuls", "Elle sera lue par l\'équipe soignante")], typ="checkbox", checked=(0,) if mode in ("brouillon", "revision") else ())}'
         + (f'<p id="pv-partage" style="margin:0;color:var(--encre);font-weight:500">Erreur{NNB}: cochez cette case pour envoyer votre préparation, ou enregistrez-la comme brouillon.</p>' if err else "") + '</div>',
         '<p class="etaye-2" style="margin:0">Cette préparation est envoyée à votre cabinet. Elle ne remplace pas une consultation et ne reçoit pas de réponse immédiate.</p>']
    state = {"brouillon": enregistrement("ok", "09:02"), "revision": enregistrement("sale"), "erreur": enregistrement("sale"), "session": enregistrement("echec")}.get(mode, "")
    pied = f'<div class="etaye-actions-patient">{btn("Envoyer au cabinet", "principal", patient=True)}{btn("Enregistrer le brouillon", "secondaire", patient=True)}</div>'
    note = ""
    if mode == "brouillon":
        note = '<p class="etaye-3" style="font-size:14px;margin:0">État historique de PREP-A-001 le 2 novembre avant 09:10 (brouillon visible par vous seule).</p>'
    if mode in ("erreur", "rdv-change", "session", "revision"):
        note = '<p class="etaye-3" style="font-size:14px;margin:0">Variante de démonstration construite sur PREP-A-001.</p>'
    return patient_shell("Préparer ma visite", head + f'<div class="etaye-form">{"".join(x for x in f if x)}<div>{state}</div>{pied}</div>{note}')


# ── S-28 / S-29 / S-30 / I-28 published content ──────────────────────────────
def s28(mode="liste"):
    if mode == "vide":
        b = carte('<p style="margin:0">Votre praticienne n\'a encore rien publié pour vous.</p><p class="etaye-2" style="margin:var(--espace-2) 0 0">Les informations apparaissent ici seulement quand elles sont publiées pour vous. Les comptes rendus internes du cabinet n\'y figurent pas.</p>')
    elif mode == "retiree":
        b = (f'<ul class="etaye-pliste"><li>{ic("document", 24, "etaye-2")}<div class="etaye-pliste__txt"><span>Information du {d_long(PUB2["publishedAt"])}</span><small>Retirée par le cabinet · plus consultable</small></div>{st("publication", "WITHDRAWN")}</li></ul>'
             '<p class="etaye-3" style="font-size:14px;margin:0">Variante V-WITHDRAWN. Le motif interne du retrait n\'est pas affiché.</p>')
    elif mode == "erreur":
        b = f'<ul class="etaye-pliste">{pub_ligne(PUB2, V2)}</ul>' + bandeau("attention", "La suite de la liste n'a pas pu être chargée", "Les documents déjà affichés restent consultables.", btn("Réessayer", "secondaire", "relancer", patient=True), role="alert")
    else:
        b = (f'<ul class="etaye-pliste">{pub_ligne(PUB2, V2)}</ul>'
             f'<details><summary style="cursor:pointer;min-height:48px;display:flex;align-items:center">Versions remplacées (1)</summary><ul class="etaye-pliste">{pub_ligne(PUB1, V1, "REPLACED", lien_=False)}</ul>'
             f'<p class="etaye-3" style="font-size:15px;margin:0">Une version remplacée ne s\'ouvre plus : seule la version actuelle fait foi.</p></details>')
    return patient_shell("Mes documents", f'<h1 style="margin:0">Mes documents</h1><p class="etaye-2" style="margin:0">Ce que le Cabinet des Tilleuls a publié pour vous.</p>{b}')


def reperes_patient(texte):
    return texte


def s29(mode="v2"):
    if mode == "remplacee":
        b = (f'<h1 style="margin:0">Cette version a été remplacée</h1>'
             + carte(f'<p style="margin:0">La version du {d_long(PUB1["publishedAt"])} a été remplacée le {d_long(PUB2["publishedAt"])} à {h_patient(PUB2["publishedAt"])}. Elle ne peut plus être lue ni téléchargée.</p>'
                     f'<div class="etaye-actions-patient" style="margin-top:var(--espace-4)">{btn("Lire la version actuelle", "principal", patient=True, href_=href("S-29"))}</div>'))
        return patient_shell("Mes documents", b)
    if mode == "retiree":
        b = (f'<h1 style="margin:0">Cette information n\'est plus disponible</h1>'
             + carte('<p style="margin:0">Votre cabinet l\'a retirée de votre espace. Si vous l\'aviez téléchargée, votre copie n\'est pas effacée, mais elle n\'est plus à jour.</p>'
                     f'<p class="etaye-2" style="margin:var(--espace-2) 0 0">Pour toute question, contactez le secrétariat du Cabinet des Tilleuls.</p>')
             + '<p class="etaye-3" style="font-size:14px;margin:0">Variante V-WITHDRAWN depuis PUB-A-002.</p>')
        return patient_shell("Mes documents", b)
    grants = {g["sourceId"]: g for g in PUB2["sourceGrants"]}
    r = lambda n: f'<button class="etaye-repere" type="button" aria-label="Extrait {n}" style="min-width:24px;min-height:24px;margin-right:-2px">{n}</button>'
    texte = (f'<p class="doc-corps" style="font-size:19px;line-height:32px;margin:0">Vous avez signalé un souvenir de réaction à la pénicilline{r(1)}. '
             f'Cette information sera précisée lors de votre prochaine consultation{r(2)}. La liste actuelle de vos traitements reste à compléter{r(3)}.</p>')
    meta = (f'<dl class="etaye-def" style="grid-template-columns:minmax(0,1fr);font-size:16px;line-height:24px">'
            f'<dt>Relue et validée</dt><dd>par {NORA} le {d_long(V2["validatedAt"], jour=True)} à {h_patient(V2["validatedAt"])}</dd>'
            f'<dt>Publiée pour vous</dt><dd>le {d_long(PUB2["publishedAt"], jour=True)} à {h_patient(PUB2["publishedAt"])} · version {V2["version"]} · remplace la version du 20 octobre</dd></dl>')
    order = [("SRC-A-005", 1), ("SRC-A-001", 2), ("SRC-A-006", 3)]
    ext = "".join(f'<li><span class="etaye-repere etaye-repere--seul">{n}</span><div><div>{esc(grants[s]["publicLabel"])} · {d_long(by_id("sources", s)["occurredAt"])}</div>'
                  f'<blockquote class="doc-corps" style="margin:var(--espace-1) 0 0;font-size:16px;line-height:26px">« {esc(grants[s]["approvedExcerpt"])} »</blockquote></div></li>' for s, n in order)
    ext = f'<ol style="list-style:none;padding:0;margin:0;display:grid;gap:var(--espace-4)">{ext}</ol>'
    band = ""
    if mode == "source-indisponible":
        ext = bandeau("attention", "Les extraits n'ont pas pu être chargés", "Le texte publié reste valable. Réessayez plus tard.", btn("Réessayer", "secondaire", "relancer", patient=True), role="alert")
    if mode == "a-jour":
        band = bandeau("info", "Une mise à jour est en préparation", "Votre cabinet prépare une nouvelle version. Celle-ci reste valable tant que la nouvelle n'est pas publiée.")
    b = (f'<a href="{href("S-28")}">Mes documents</a><h1 style="margin:0">Informations après votre consultation du 20 octobre 2026</h1>{band}'
         f'{carte(texte)}{meta}<section class="etaye-pile"><h2 style="margin:0">Extraits partagés par votre praticienne</h2>{ext}'
         f'<p class="etaye-3" style="font-size:15px;margin:0">Seuls ces extraits ont été partagés. Les autres documents de votre dossier ne sont pas accessibles ici.</p></section>'
         f'<div class="etaye-actions-patient">{btn("Télécharger en PDF", "principal", "telecharger", patient=True, href_=href("I-28"))}{btn("Contacter le cabinet", "secondaire", patient=True)}</div>')
    return patient_shell("Mes documents", b)


def s29_extrait():
    """I-09 patient variant: approved excerpt only (full page on mobile)."""
    g = {x["sourceId"]: x for x in PUB2["sourceGrants"]}["SRC-A-005"]
    corps = (f'<div class="etaye-pile"><div class="etaye-2" style="font-size:15px">Phrase concernée</div><p class="doc-corps" style="margin:0;font-size:18px">« Vous avez signalé un souvenir de réaction à la pénicilline. »</p>'
             f'<div class="ui-section">{esc(g["publicLabel"])}</div><div class="etaye-2" style="font-size:15px">Votre préparation de visite · {d_long("2026-11-02T09:10:00+01:00", jour=True)} à 9 h 10</div>'
             f'<blockquote class="etaye-source-texte" style="margin:0">« {esc(g["approvedExcerpt"])} »</blockquote>'
             f'<p class="etaye-3" style="font-size:14px;margin:0">C\'est votre déclaration ; elle n\'a pas encore été confirmée par votre praticienne.</p></div>')
    return tiroir(s29(), "Extrait partagé", corps, btn("Fermer", "secondaire", patient=True))


def page_pdf(n, total):
    return (f'<div class="etaye-visionneuse__page"><div style="font:500 14px/20px var(--font-texte)">Informations après votre consultation du 20 octobre 2026</div>'
            f'<div class="etaye-3" style="font:400 11px/16px var(--font-texte)">Claire Martin · version 2 · publiée le 02/11/2026 · page {n} / {total}</div><div style="height:1px;background:var(--filet)"></div>'
            f'<div style="font:400 13px/22px var(--font-document)">{esc(V2["text"]) if n == 1 else "Extraits partagés : déclaration du 14/09/2026, préparation du 02/11/2026, note du 20/10/2026."}</div></div>')


def s30(mode="apercu"):
    if mode == "chargement":
        vis = f'<div class="etaye-visionneuse">{squelette(5)}</div>'
    elif mode in ("indisponible", "lien-expire", "retire", "echec"):
        t = {"indisponible": ("Le document n'est pas disponible pour le moment", "Le texte reste lisible dans votre espace. Réessayez plus tard.", btn("Réessayer", "secondaire", "relancer", patient=True)),
             "lien-expire": ("Ce lien de téléchargement a expiré", "Pour votre sécurité, les liens expirent vite. Demandez-en un nouveau.", btn("Télécharger à nouveau", "principal", "telecharger", patient=True)),
             "retire": ("Ce document n'est plus partagé avec vous", "Votre cabinet a retiré cette publication. Une copie déjà téléchargée n'est pas effacée, mais elle n'est plus à jour.", ""),
             "echec": ("Le téléchargement a échoué", "Aucun fichier incomplet n'a été gardé. Vérifiez votre connexion puis réessayez.", btn("Réessayer", "secondaire", "relancer", patient=True))}[mode]
        vis = f'<div class="etaye-visionneuse">{bandeau("attention" if mode != "retire" else "neutre", t[0], t[1], t[2], role="alert")}</div>'
    else:
        vis = (f'<div class="etaye-visionneuse"><div class="etaye-visionneuse__barre"><span>Page 1 / 2</span><span class="etaye-ligne">{btn("Page suivante", "discret", patient=True)}</span></div>{page_pdf(1, 2)}</div>')
    if mode == "non-pris-en-charge":
        vis = f'<div class="etaye-visionneuse">{bandeau("neutre", "L\'aperçu ne s\'affiche pas sur cet appareil", "Téléchargez le PDF ou lisez le même texte dans votre espace.", btn("Lire le texte", "secondaire", patient=True, href_=href("S-29")))}</div>'
    acts = f'<div class="etaye-actions-patient">{btn("Télécharger le PDF", "principal", "telecharger", patient=True, href_=href("I-28"))}{btn("Revenir au texte", "secondaire", patient=True, href_=href("S-29"))}</div>' if mode in ("apercu", "chargement", "non-pris-en-charge") else f'<div>{lien("Revenir à mes documents", href("S-28"))}</div>'
    meta = '<p class="etaye-2" style="margin:0;font-size:16px">PDF · 2 pages · version 2 publiée le 2 novembre 2026 · document de démonstration</p>'
    return patient_shell("Mes documents", f'<a href="{href("S-29")}">Informations après votre consultation</a><h1 style="margin:0">Document PDF</h1>{meta}{vis}{acts}')


def i28(mode="ouvert"):
    if mode == "termine":
        inner = toast("Téléchargement terminé · etaye-informations-2026-11-02-v2.pdf")
        extra = '<p class="etaye-2" style="margin:0">Ce fichier est maintenant sur votre appareil : s\'il est remplacé ou retiré plus tard, votre copie ne sera pas mise à jour.</p>'
    elif mode == "refuse":
        inner = bandeau("neutre", "Téléchargement refusé", "Cette version n'est plus partagée avec vous. Aucun fichier n'a été envoyé.", role="alert")
        extra = ""
    else:
        inner = (f'<section class="etaye-carte-patient" aria-labelledby="h-dl"><h2 id="h-dl" style="margin:0">Télécharger cette version</h2>'
                 f'<dl class="etaye-def" style="grid-template-columns:120px minmax(0,1fr);font-size:16px;margin-top:var(--espace-3)"><dt>Fichier</dt><dd>etaye-informations-2026-11-02-v2.pdf</dd>'
                 f'<dt>Type</dt><dd>PDF · 2 pages</dd><dt>Version</dt><dd>2 · publiée le 2 novembre 2026</dd><dt>Pour</dt><dd>Claire Martin</dd></dl>'
                 f'<p class="etaye-2" style="margin:var(--espace-2) 0 0;font-size:16px">Une fois téléchargé, le fichier ne peut pas être repris par le cabinet.</p>'
                 f'<div class="etaye-actions-patient" style="margin-top:var(--espace-4)">{btn("Télécharger", "principal", "telecharger", patient=True)}{btn("Fermer", "secondaire", patient=True)}</div></section>')
        extra = ""
    page = s29()
    marker = '<div class="etaye-actions-patient"><a class="etaye-bouton etaye-bouton--principal etaye-bouton--patient"'
    i = page.rfind(marker)
    return page[:i] + inner + extra + page[i:] if i > 0 else page


SCREENS = [
    {"id": "S-25", "name": "Accueil de l'espace patient", "states": [
        {"slug": "accueil", "label": "Default — Claire Martin: next visit APT-A-001, preparation sent, explanation v2 published; REPORT-A-001-v1 absent (SC-NOT-PUBLISHED)", "html": s25()},
        {"slug": "sans-rdv", "label": "No appointment (template)", "html": s25("sans-rdv")},
        {"slug": "sans-publication", "label": "No publication (template)", "html": s25("sans-publication")},
        {"slug": "remplacee", "label": "Replaced notice — v1 replaced by v2 on 2 Nov", "html": s25("remplacee")},
        {"slug": "retiree", "label": "Withdrawn notice — V-WITHDRAWN, no internal reason", "html": s25("retiree")},
        {"slug": "lien-retire", "label": "Revoked portal link (template)", "html": s25("lien-retire")},
        {"slug": "indisponible", "label": "API unavailable — nothing shown as empty success", "html": s25("indisponible")},
    ]},
    {"id": "S-26", "name": "Rendez-vous et réservation (patient)", "states": [
        {"slug": "liste", "label": "Own appointments — upcoming BOOKED, past COMPLETED and NO_SHOW (read-only)", "html": s26()},
        {"slug": "detail", "label": "Own appointment detail — patient variant of S-16", "html": s26("detail")},
        {"slug": "creneaux", "label": "Booking — anonymised slots Tue 3 Nov (10:30 unavailable), 11:00 chosen (I-10 patient)", "html": s26("creneaux")},
        {"slug": "conflit", "label": "Booking race lost — choice context and reason kept, no duplicate (AC-S26-F)", "html": s26("conflit")},
        {"slug": "aucun", "label": "No slots (template)", "html": s26("aucun")},
        {"slug": "deplacer", "label": "Move — current visit kept until confirmed (I-11 patient)", "html": s26("deplacer")},
        {"slug": "annuler", "label": "Cancel — keep is primary and focused (I-12 patient)", "html": s26("annuler")},
        {"slug": "annule", "label": "Cancelled confirmation (template)", "html": s26("annule")},
        {"slug": "indisponible", "label": "Booking service unavailable", "html": s26("indisponible")},
    ]},
    {"id": "S-27", "name": "Préparation de visite (patient)", "states": [
        {"slug": "brouillon", "label": "Draft — PREP-A-001 before 09:10, saved 09:02, patient-only", "html": s27()},
        {"slug": "vide", "label": "Empty — all optional, 'Je ne sais pas' and 'Je préfère ne pas répondre' distinct from 'Non'", "html": s27("vide")},
        {"slug": "envoyee", "label": "Submitted — 2 Nov 09:10, own statements, treatments 'Non renseigné'", "html": s27("envoyee")},
        {"slug": "revision", "label": "Reopened revision — previous version kept and dated", "html": s27("revision")},
        {"slug": "erreur", "label": "Validation error — sharing consent not checked on send; summary + message, answers kept", "html": s27("erreur")},
        {"slug": "rdv-change", "label": "Appointment changed — preparation kept (template)", "html": s27("rdv-change")},
        {"slug": "session", "label": "Session expired — answers kept on device, nothing sent", "html": s27("session")},
    ]},
    {"id": "S-28", "name": "Documents publiés (patient)", "states": [
        {"slug": "liste", "label": "Published list — PUB-A-002 current, PUB-A-001 replaced (not openable); no internal report", "html": s28()},
        {"slug": "vide", "label": "No publications (template)", "html": s28("vide")},
        {"slug": "retiree", "label": "Withdrawn row — V-WITHDRAWN", "html": s28("retiree")},
        {"slug": "erreur", "label": "Pagination failure — loaded rows kept", "html": s28("erreur")},
    ]},
    {"id": "S-29", "name": "Information publiée (patient)", "states": [
        {"slug": "v2", "label": "PATIENT-A-001-v2 exact text, validated 14:20 vs published 14:25, three approved excerpts", "html": s29()},
        {"slug": "extrait", "label": "Approved excerpt drawer — I-09 patient variant (SRC-A-005, own statement, unconfirmed)", "html": s29_extrait()},
        {"slug": "a-jour", "label": "Update in preparation notice (template)", "html": s29("a-jour")},
        {"slug": "remplacee", "label": "Replaced — v1 link no longer readable", "html": s29("remplacee")},
        {"slug": "retiree", "label": "Withdrawn — V-WITHDRAWN, downloaded copies not recalled", "html": s29("retiree")},
        {"slug": "source-indisponible", "label": "Excerpts failed to load — published text kept", "html": s29("source-indisponible")},
    ]},
    {"id": "S-30", "name": "Document autorisé et téléchargement (patient)", "states": [
        {"slug": "apercu", "label": "PDF preview of the current published version (no attachment granted: attachmentDocumentIds empty)", "html": s30()},
        {"slug": "chargement", "label": "Loading", "html": s30("chargement")},
        {"slug": "indisponible", "label": "PDF unavailable", "html": s30("indisponible")},
        {"slug": "non-pris-en-charge", "label": "Unsupported preview — text alternative", "html": s30("non-pris-en-charge")},
        {"slug": "lien-expire", "label": "Expired download link", "html": s30("lien-expire")},
        {"slug": "retire", "label": "Withdrawn grant", "html": s30("retire")},
        {"slug": "echec", "label": "Download failure", "html": s30("echec")},
    ]},
    {"id": "I-28", "name": "Résultat du téléchargement autorisé", "states": [
        {"slug": "ouvert", "label": "Inline download panel — file, type, version, audience; copies cannot be recalled", "html": i28()},
        {"slug": "termine", "label": "Download complete", "html": i28("termine")},
        {"slug": "refuse", "label": "Refused — grant no longer valid, nothing sent", "html": i28("refuse")},
    ]},
]

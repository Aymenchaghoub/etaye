"""Access and entry family — S-01 showcase, I-26 demo scenario, S-02 login, S-03 invitation/activation,
I-03 patient portal activation, S-04 access recovery, S-05 context selection, S-36 forbidden/not found,
S-38 expired session, I-22 session expiration page.

Accounts and invitations come from synthetic-data.json. No account enumeration in any message.
"""
from lib import *
import screens_dossier as dos

ZOOM = ["desktop", "tablet", "mobile", "narrow", "zoom"]


def carte(body):
    return f'<div class="etaye-auth__carte">{body}</div>'


def pied_auth(patient=False):
    return (f'<p class="etaye-3" style="font-size:13px;line-height:20px;margin:0">Première connexion ? Ouvrez le lien d\'invitation reçu par e-mail. '
            f'<a href="{href("S-01")}">Comptes de démonstration</a></p>')


# ── S-02 Login ───────────────────────────────────────────────────────────────
def s02(mode="connexion", patient=False):
    email = "claire.martin@example.test" if patient else "nora.lefebvre@example.test"
    titre = "Se connecter à votre espace patient" if patient else "Se connecter"
    intro = ("Consultez vos rendez-vous et les informations que votre cabinet a publiées pour vous." if patient
             else "Espace des professionnels et de l'administration des cabinets.")
    top = ""
    err_mail = err_pw = None
    if mode == "invalide":
        top = resume_erreurs([("l-pw", "Adresse e-mail ou mot de passe incorrect")])
        err_pw = "adresse e-mail ou mot de passe incorrect. Le mot de passe a été effacé."
    elif mode == "suspendu":
        top = bandeau("attention", "Accès suspendu", "Votre accès a été suspendu par l'administration du cabinet. Vos données ne sont pas supprimées. Contactez le secrétariat du cabinet pour le rétablir.", role="alert")
    elif mode == "limite":
        top = bandeau("attention", "Trop de tentatives", f'Les connexions sont temporairement bloquées pour cette adresse. Réessayez dans quelques minutes ou <a href="{href("S-04", "demande")}">récupérez votre accès</a>.', role="alert")
    elif mode == "retour":
        top = bandeau("info", "Votre session a expiré", "Reconnectez-vous pour revenir à la page demandée. Aucune information du dossier n'est affichée avant la connexion.")
    elif mode == "champ-vide":
        top = resume_erreurs([("l-mail", "Adresse e-mail : obligatoire")])
        err_mail = "indiquez votre adresse e-mail."
    busy = mode == "envoi"
    mail_val = "" if mode in ("connexion", "champ-vide") else email
    f = (f'{champ("Adresse e-mail", mail_val, "l-mail", typ="email", required=True, error=err_mail, readonly=busy, focus=mode == "connexion")}'
         f'{champ("Mot de passe", "••••••••••" if busy else "", "l-pw", typ="password", required=True, error=err_pw, readonly=busy)}'
         f'<div class="etaye-ligne" style="justify-content:space-between">{choix("l-show", ["Afficher le mot de passe"], typ="checkbox")}'
         f'<a href="{href("S-04", "demande")}">Mot de passe oublié ?</a></div>')
    action = btn("Connexion en cours…", "principal", disabled=True, full=True, patient=patient) if busy else btn("Se connecter", "principal", full=True, patient=patient, href_=href("S-25" if patient else "S-05", "accueil" if patient else "choix"))
    body = (f'<div><h1>{titre}</h1><p class="etaye-2" style="margin:var(--espace-2) 0 0">{intro}</p></div>{top}'
            f'{carte(f"<form class=\"etaye-form\" novalidate>{f}{action}</form>")}{pied_auth(patient)}')
    return auth_shell(body, patient=patient)


# ── S-03 Invitation / activation (team membership) ───────────────────────────
def s03(mode="equipe"):
    inv = by_id("invitations", "INV-TEAM-01")
    acc = by_id("accounts", inv["accountId"])
    if mode in ("expiree", "revoquee", "invalide", "deja-utilisee"):
        t = {"expiree": ("Cette invitation a expiré", "Les invitations ont une durée de validité limitée. Demandez à l'administration du cabinet de vous en envoyer une nouvelle."),
             "revoquee": ("Cette invitation n'est plus valable", "Elle a été annulée par le cabinet. Aucun compte n'a été activé."),
             "invalide": ("Lien d'invitation invalide", "Le lien est incomplet ou ne correspond à aucune invitation en cours. Vérifiez que vous avez ouvert le lien complet de l'e-mail."),
             "deja-utilisee": ("Cette invitation vient d'être utilisée", "Elle a déjà servi à activer un compte, peut-être dans un autre onglet. Connectez-vous pour continuer.")}[mode]
        act = btn("Se connecter", "principal", full=True, href_=href("S-02")) if mode == "deja-utilisee" else btn("Revenir à la connexion", "secondaire", full=True, href_=href("S-02"))
        note = '<p class="etaye-3" style="font-size:12px;line-height:18px;margin:0">Variante de gabarit : INV-TEAM-01 est en attente dans le jeu de données.</p>' if mode in ("revoquee", "deja-utilisee", "expiree") else ""
        return auth_shell(f'<div><h1>{t[0]}</h1></div>{carte(f"<p style=\"margin:0\">{t[1]}</p>{act}")}{note}')
    err = mode == "erreur"
    head = (f'<div><div class="ui-libelle etaye-2">Invitation à rejoindre une équipe</div><h1 style="margin-top:var(--espace-1)">Rejoindre le Cabinet des Tilleuls</h1>'
            f'<p class="etaye-2" style="margin:var(--espace-2) 0 0">Cette invitation donne accès à l\'espace des professionnels du cabinet. Elle ne donne accès à aucun dossier avant l\'activation et la vérification de vos droits.</p></div>')
    meta = (f'<dl class="etaye-def" style="grid-template-columns:140px minmax(0,1fr)"><dt>Destinataire</dt><dd>{esc(acc["name"])} · al…@example.test</dd>'
            f'<dt>Expire le</dt><dd class="etaye-num">{dh_pro(inv["expiresAt"])}</dd>'
            f'<dt>Droits</dt><dd>Fixés par l\'administration du cabinet · <span class="etaye-3">non précisés dans le jeu de démonstration</span></dd></dl>')
    top = resume_erreurs([("a-pw2", "Confirmation : les deux mots de passe sont différents")]) if err else ""
    f = (f'{top}{champ("Nom affiché", acc["name"], "a-nom", required=True)}'
         f'{champ("Mot de passe", "", "a-pw", typ="password", required=True, help_="12 caractères au moins. Une phrase facile à retenir convient.", focus=not err)}'
         f'{champ("Confirmer le mot de passe", "", "a-pw2", typ="password", required=True, error="les deux mots de passe sont différents. Saisissez-les à nouveau." if err else None)}'
         f'{btn("Activer mon compte", "principal", full=True, href_=href("S-05", "choix"))}')
    return auth_shell(f'{head}{carte(meta)}{carte(f"<form class=\"etaye-form\" novalidate>{f}</form>")}'
                      f'<p class="etaye-3" style="font-size:13px;line-height:20px;margin:0">Ce n\'est pas vous ? Ne créez pas de compte et prévenez le cabinet.</p>', large=False)


# ── I-03 Patient portal activation ───────────────────────────────────────────
def i03(mode="ouvert"):
    if mode == "deja-active":
        return auth_shell(f'<div><h1>Votre espace patient est déjà activé</h1></div>'
                          + carte(f'<p style="margin:0">Il a été activé le {d_long("2026-10-18T12:00:00+02:00", jour=True)} à {h_patient("2026-10-18T12:00:00+02:00")}. Ce lien ne peut plus servir.</p>'
                                  + btn("Me connecter", "principal", full=True, patient=True, href_=href("S-02", "patient"))), patient=True)
    if mode == "expiree":
        inv = by_id("invitations", "INV-PORTAL-02")
        return auth_shell(f'<div><h1>Ce lien d\'activation a expiré</h1></div>'
                          + carte(f'<p style="margin:0">Il était valable jusqu\'au {d_long(inv["expiresAt"], jour=True)} à {h_patient(inv["expiresAt"])}. Aucun espace n\'a été créé.</p>'
                                  f'<p style="margin:0">Le Cabinet des Tilleuls peut vous envoyer un nouveau lien.</p>'
                                  + btn("Contacter le cabinet", "secondaire", full=True, patient=True))
                          + '<p class="etaye-3" style="font-size:12px;line-height:18px;margin:0">INV-PORTAL-02 · Louis Bernard · expirée</p>', patient=True)
    err = mode == "erreur"
    head = (f'<div><div class="ui-libelle etaye-2">Invitation de votre cabinet</div><h1 style="margin-top:var(--espace-1)">Activer mon espace patient</h1>'
            f'<p class="patient-corps etaye-2" style="margin:var(--espace-2) 0 0">Le Cabinet des Tilleuls vous propose un espace pour préparer vos visites, voir vos rendez-vous et lire ce que votre praticienne publie pour vous.</p></div>')
    who = (f'<dl class="etaye-def" style="grid-template-columns:120px minmax(0,1fr)"><dt>Cabinet</dt><dd>Cabinet des Tilleuls</dd>'
           f'<dt>Destinataire</dt><dd>C… M… · cl…@example.test</dd><dt>Ce lien sert à</dt><dd>Relier votre compte à votre dossier dans ce cabinet, une seule fois</dd></dl>')
    top = bandeau("attention", "Activation impossible avec ces informations", "Pour protéger votre dossier, nous ne pouvons pas vous dire quelle information ne correspond pas. Contactez le cabinet.", role="alert") if err else ""
    f = (f'{top}{champ("Votre date de naissance", "18/04/1978" if not err else "18/04/1987", "p-dn", num=True, required=True, help_="Format JJ/MM/AAAA. Elle sert à vérifier que l\'invitation vous est destinée.", focus=not err)}'
         f'{champ("Créer un mot de passe", "", "p-pw", typ="password", required=True, help_="12 caractères au moins.")}'
         f'<div class="etaye-actions-patient">{btn("Activer mon espace", "principal", patient=True, full=True, href_=href("S-25"))}{btn("Contacter le cabinet", "secondaire", patient=True, full=True)}</div>')
    note = '<p class="etaye-3" style="font-size:12px;line-height:18px;margin:0">État historique d\'INV-PORTAL-01 avant son acceptation le 18/10/2026 à 12:00.</p>'
    return auth_shell(f'{head}{carte(who)}{carte(f"<form class=\"etaye-form\" novalidate>{f}</form>")}{note}', patient=True)


# ── S-04 Access recovery ─────────────────────────────────────────────────────
def s04(mode="demande"):
    if mode == "envoye":
        body = (f'<div><h1>Vérifiez votre messagerie</h1></div>'
                + carte('<p style="margin:0">Si un compte correspond à <strong>nora.lefebvre@example.test</strong>, un e-mail vient d\'être envoyé avec un lien pour choisir un nouveau mot de passe.</p>'
                        '<p class="etaye-2" style="margin:0;font-size:14px">Le lien est valable pour une durée limitée et ne sert qu\'une fois. Rien reçu ? Vérifiez les indésirables, puis refaites la demande.</p>'
                        + btn("Revenir à la connexion", "secondaire", full=True, href_=href("S-02"))))
        return auth_shell(body)
    if mode == "lien-expire":
        return auth_shell('<div><h1>Ce lien n\'est plus valable</h1></div>' + carte('<p style="margin:0">Il a expiré ou a déjà été utilisé. Votre mot de passe n\'a pas changé.</p>' + btn("Faire une nouvelle demande", "principal", full=True, href_=href("S-04", "demande"))))
    if mode in ("nouveau", "nouveau-erreur"):
        err = mode == "nouveau-erreur"
        top = resume_erreurs([("r-pw", "Nouveau mot de passe : 12 caractères au moins")]) if err else ""
        f = (f'{top}{champ("Nouveau mot de passe", "", "r-pw", typ="password", required=True, error="12 caractères au moins. Le champ a été effacé." if err else None, help_=None if err else "12 caractères au moins.", focus=not err)}'
             f'{champ("Confirmer le nouveau mot de passe", "", "r-pw2", typ="password", required=True)}'
             f'<p class="etaye-2" style="margin:0;font-size:14px">Après le changement, vos autres sessions ouvertes seront fermées.</p>{btn("Enregistrer le mot de passe", "principal", full=True, href_=href("S-04", "reussi"))}')
        return auth_shell(f'<div><h1>Choisir un nouveau mot de passe</h1></div>{carte(f"<form class=\"etaye-form\" novalidate>{f}</form>")}')
    if mode == "reussi":
        return auth_shell('<div><h1>Mot de passe modifié</h1></div>' + carte('<p style="margin:0">Vos autres sessions ont été fermées. Connectez-vous avec votre nouveau mot de passe.</p>' + btn("Se connecter", "principal", full=True, href_=href("S-02"))))
    top = ""
    if mode == "limite":
        top = bandeau("attention", "Trop de demandes", "Les demandes sont temporairement limitées. Réessayez dans quelques minutes.", role="alert")
    elif mode == "reseau":
        top = bandeau("attention", "La demande n'a pas pu être envoyée", "Problème de connexion. Votre adresse est conservée ; vous pouvez réessayer.", btn("Réessayer", "secondaire", "relancer"), role="alert")
    f = (f'{champ("Adresse e-mail", "" if mode == "demande" else "nora.lefebvre@example.test", "r-mail", typ="email", required=True, focus=mode == "demande")}'
         f'{btn("Recevoir un lien", "principal", full=True, href_=href("S-04", "envoye"))}')
    return auth_shell(f'<div><h1>Récupérer votre accès</h1><p class="etaye-2" style="margin:var(--espace-2) 0 0">Indiquez l\'adresse de votre compte. Nous vous enverrons un lien pour choisir un nouveau mot de passe.</p></div>'
                      f'{top}{carte(f"<form class=\"etaye-form\" novalidate>{f}</form>")}<a href="{href("S-02")}">Revenir à la connexion</a>')


# ── S-05 Context selection ───────────────────────────────────────────────────
def ctx_row(cab, role, detail, href_, selected=False, disabled=False, etat_=""):
    st_ = ' style="border-color:var(--accent)"' if selected else ""
    a = (f'<span class="etaye-3" style="font-size:13px">{etat_}</span>' if disabled else btn("Ouvrir", "secondaire" if not selected else "principal", href_=href_))
    return (f'<li{st_}>{ic("cabinet", 24, "etaye-2")}<div class="etaye-pliste__txt"><strong>{esc(cab)}</strong><small style="font-size:14px;line-height:20px">{esc(role)} · {detail}</small></div>{a}</li>')


def s05(mode="choix"):
    if mode == "un-seul":
        return auth_shell('<div><h1>Ouverture du Cabinet des Tilleuls</h1></div>'
                          + carte('<p style="margin:0">Vous n\'avez qu\'un contexte de travail : Dr Nora Lefebvre, praticienne au Cabinet des Tilleuls. La page Aujourd\'hui s\'ouvre directement.</p>'
                                  + btn("Ouvrir Aujourd'hui", "principal", full=True, href_=href("S-06"))))
    rows = [ctx_row("Cabinet des Tilleuls", "Praticien", "dossiers, agenda, validation et publication patient", href("S-06")),
            ctx_row("Cabinet du Canal", "Praticien", "dossiers, agenda et validation · publication patient non autorisée dans ce cabinet", href("S-06"))]
    top = ""
    if mode == "retire":
        rows[1] = ctx_row("Cabinet du Canal", "Praticien", "accès retiré à 16:01", "#", disabled=True, etat_="N'est plus disponible")
        top = bandeau("attention", "Ce contexte n'est plus disponible", "Votre accès au Cabinet du Canal a changé pendant votre choix. Aucune donnée de ce cabinet n'a été ouverte.", role="alert")
    if mode == "desactive":
        rows[1] = ctx_row("Cabinet du Canal", "Praticien", "accès suspendu par l'administration du cabinet", "#", disabled=True, etat_="Suspendu")
    note = '<p class="etaye-3" style="font-size:12px;line-height:18px;margin:0">Variante de gabarit : les deux appartenances de Sami Rahmani sont actives dans le jeu de données.</p>' if mode in ("retire", "desactive") else ""
    body = (f'<div><h1>Choisir un contexte de travail</h1><p class="etaye-2" style="margin:var(--espace-2) 0 0">Dr Sami Rahmani · sami.rahmani@example.test. '
            f'Chaque cabinet a ses propres dossiers ; rien n\'est partagé entre eux.</p></div>{top}'
            f'<ul class="etaye-pliste">{"".join(rows)}</ul>{note}'
            f'<p class="etaye-3" style="font-size:13px;line-height:20px;margin:0">Vous pourrez changer de contexte depuis l\'en-tête. Les recherches et brouillons ouverts ne passent pas d\'un cabinet à l\'autre.</p>')
    return auth_shell(body, large=True)


# ── S-36 Forbidden / not found ───────────────────────────────────────────────
def etat_page(code, titre, texte, actions):
    return f'<div class="etaye-page"><div class="etaye-etat-page" role="alert"><div class="etaye-etat-page__code">{code}</div><h1>{titre}</h1><p>{texte}</p><div class="etaye-ligne">{actions}</div></div></div>'


def s36(mode="interdit"):
    if mode == "interdit":
        b = etat_page("Accès non autorisé · 403", "Cet espace est réservé aux soins",
                      "Votre rôle d'administration au Cabinet des Tilleuls ne donne pas accès aux dossiers patients. Rien n'a été affiché.",
                      btn("Revenir à l'équipe", "principal", href_=href("S-31")))
        return pro_shell("", b, "mila")
    if mode == "introuvable":
        b = etat_page("Élément introuvable · 404", "Élément introuvable",
                      "Cette adresse ne correspond à aucun élément disponible dans le Cabinet des Tilleuls. Vérifiez le lien ou revenez à la liste.",
                      btn("Revenir aux patients", "principal", href_=href("S-07")) + btn("Aujourd'hui", "discret", href_=href("S-06")))
        return pro_shell("Patients", b, "nora")
    b = etat_page("Lien obsolète", "Ce lien ne mène plus à rien",
                  "L'élément a été déplacé ou remplacé. Pour des raisons de confidentialité, sa nouvelle place n'est pas indiquée ici.",
                  btn("Aujourd'hui", "principal", href_=href("S-06")))
    return pro_shell("", b, "nora")


def s36_patient():
    b = (f'<div class="etaye-etat-page" role="alert"><div class="etaye-etat-page__code">Élément introuvable</div><h1 class="patient-titre">Cette page n\'est pas disponible</h1>'
         f'<p>Elle n\'existe pas ou ne vous a pas été partagée.</p>{btn("Revenir à l\'accueil", "principal", patient=True, href_=href("S-25"))}</div>')
    return patient_shell("", b)


# ── S-38 / I-22 Session expiration ───────────────────────────────────────────
def session_panel(mode):
    msg = {"note": "Vos notes en cours sont gardées en mémoire pour ce compte et ce cabinet. Elles ne seront pas envoyées automatiquement : vous les retrouverez après reconnexion et choisirez de les enregistrer.",
           "enregistrement": "L'enregistrement en cours n'a pas abouti avant l'expiration. Votre texte est conservé ; rien n'a été enregistré sur le serveur.",
           "publication": "La publication n'a pas été envoyée. Après reconnexion, vous reverrez la vérification avant de publier ; rien ne part automatiquement.",
           "revoque": "Après reconnexion, votre accès à ce dossier a changé. Le brouillon gardé en mémoire ne peut pas être restauré et a été effacé de cet appareil.",
           "autre": "Un autre compte s'est connecté sur cet appareil. Les brouillons de la session précédente ne sont pas disponibles pour ce compte et ont été effacés."}[mode]
    if mode in ("revoque", "autre"):
        acts = btn("Aujourd'hui", "principal", href_=href("S-06")) if mode == "autre" else btn("Choisir un contexte", "principal", href_=href("S-05"))
        return f'<p style="margin:0">{msg}</p>', acts
    acts = btn("Se reconnecter", "principal", href_=href("S-02", "retour")) + btn("Quitter la session", "discret")
    return f'<p style="margin:0">{msg}</p><p class="etaye-3" style="font-size:13px;line-height:20px;margin:0">Session expirée à 16:00 après inactivité · variante V-SESSION</p>', acts


def s38(mode="note"):
    fond = dos.s10_evaluation()
    corps, acts = session_panel(mode)
    titre = {"revoque": "Accès modifié", "autre": "Autre compte connecté"}.get(mode, "Votre session a expiré")
    d = dialogue(fond, titre, f'<div class="etaye-pile" style="gap:var(--espace-3)">{corps}</div>', acts, role="alertdialog", fermer=False)
    return d.replace('<div class="etaye-voile"></div>', '<div class="etaye-voile etaye-voile--opaque"></div>', 1)


def i22(mode="ouvert"):
    corps, acts = session_panel("publication" if mode == "publication" else "note")
    body = (f'<div><h1>Votre session a expiré</h1><p class="etaye-2" style="margin:var(--espace-2) 0 0">Pour protéger les dossiers, la session se ferme après une période d\'inactivité.</p></div>'
            f'{carte(f"{corps}<div class=\"etaye-ligne\">{acts}</div>")}')
    return auth_shell(body)


# ── S-01 Showcase / I-26 demo scenario ───────────────────────────────────────
SCEN = [("Praticienne", "Dr Nora Lefebvre", "Cabinet des Tilleuls", "Préparer la consultation de Claire Martin du 03/11/2026, relire un brouillon généré et ses sources, valider puis publier une explication.", "S-02"),
        ("Patiente", "Claire Martin", "Cabinet des Tilleuls", "Préparer sa visite sur téléphone, voir son rendez-vous, lire l'explication publiée et ouvrir un extrait de source autorisé.", "S-02"),
        ("Administration", "Mila Dubois", "Cabinet des Tilleuls", "Inviter une personne dans l'équipe, régler le cabinet et consulter l'audit administratif, sans accès aux dossiers.", "S-02")]


def s01(mode="accueil"):
    s5 = by_id("sources", "SRC-A-005")
    preview = (f'<div class="etaye-document" style="padding:var(--espace-6)"><p class="doc-corps" style="margin:0">Depuis la consultation du 20 octobre, Claire Martin a signalé un souvenir de réaction à la pénicilline'
               f'<button class="etaye-repere" type="button" aria-expanded="true" aria-label="Source 1">1</button>.</p>'
               f'<div class="etaye-apercu" style="margin-top:var(--espace-4);max-width:none;width:auto;position:static"><div class="etaye-apercu__ref"><span class="etaye-repere etaye-repere--seul">1</span><span>{KIND_FR[s5["kind"]]} · <span class="etaye-num">{dh_pro(s5["occurredAt"])}</span></span></div>'
               f'<div class="etaye-apercu__extrait"><mark>{esc(s5["text"])}</mark></div><div class="etaye-ligne" style="margin-top:var(--espace-2)">{etat_source(s5)}</div></div></div>')
    sc = "".join(f'<li>{ic({"Praticienne": "patients", "Patiente": "compte", "Administration": "equipe"}[r], 24, "etaye-2")}<div class="etaye-pliste__txt"><strong>{r} · {n}</strong>'
                 f'<small style="font-size:14px;line-height:20px">{c} · {t}</small></div>{btn("Choisir", "secondaire", href_=href("I-26", "ouvert"))}</li>' for r, n, c, t, _ in SCEN)
    etat_ = ""
    if mode == "preparation":
        etat_ = bandeau("info", "Démonstration en préparation", "Les données fictives sont chargées. Cela prend généralement moins d'une minute.", icon="horloge")
    elif mode == "reinitialisation":
        etat_ = bandeau("attention", "Réinitialisation en cours", "Toutes les modifications faites dans la démonstration sont effacées et le jeu etaye-design-v1 est rechargé. Les sessions ouvertes seront fermées.", role="alert")
    elif mode == "indisponible":
        etat_ = bandeau("attention", "Démonstration indisponible", "Le service de démonstration ne répond pas. Les maquettes restent consultables.", btn("Réessayer", "secondaire", "relancer"), role="alert")
    body = (f'<header style="display:flex;justify-content:space-between;align-items:center;gap:var(--espace-4);flex-wrap:wrap">{logo("clair", 28)}{demo_notice()}</header>'
            f'<div class="etaye-cols etaye-cols--egal" style="align-items:center"><div class="etaye-pile">'
            f'<h1 style="font:500 clamp(26px, 5vw, 34px)/1.25 var(--font-texte);margin:0">Préparer une consultation avec des informations datées et vérifiables</h1>'
            f'<p class="etaye-2" style="font-size:18px;line-height:28px;margin:0">Étaye aide un cabinet à préparer ses consultations, à tenir le dossier et à rédiger des comptes rendus. Les brouillons générés citent leurs sources et restent des brouillons tant qu\'un praticien ne les a pas relus et validés.</p>'
            f'<p class="etaye-3" style="font-size:14px;margin:0">Projet en conception : ces pages sont des maquettes. Aucune application n\'est en service et aucun usage clinique n\'est possible.</p></div>{preview}</div>'
            f'{etat_}<section class="etaye-pile" aria-labelledby="h-sc"><h2 class="ui-section" id="h-sc">Choisir un scénario de démonstration</h2><ul class="etaye-pliste">{sc}</ul>'
            f'<p class="etaye-3" style="font-size:13px;line-height:20px;margin:0">Personnes, cabinets et données entièrement fictifs (jeu etaye-design-v1). La démonstration peut être réinitialisée à tout moment.</p></section>')
    return f'<div class="etaye-root etaye-auth" style="background:var(--fond)"><main class="etaye-page" style="max-width:1200px;margin:0 auto;width:100%" id="contenu">{body}</main></div>'


def i26(mode="ouvert"):
    r, n, c, t, _ = SCEN[0]
    if mode == "reinitialiser":
        corps = (f'<p style="margin:0">La réinitialisation efface toutes les modifications faites dans la démonstration, recharge le jeu etaye-design-v1 et ferme les sessions ouvertes.</p>'
                 f'<p class="etaye-2" style="margin:0;font-size:14px">Réservé à l\'opérateur de la démonstration. Aucune donnée réelle n\'existe à effacer.</p>'
                 f'<div class="etaye-ligne">{btn("Retour", "secondaire", href_=href("S-01"))}{btn("Réinitialiser la démonstration", "discret", "supprimer")}</div>')
        return auth_shell(f'<div><h1>Réinitialiser la démonstration</h1></div>{carte(corps)}', large=True)
    cast = (f'<dl class="etaye-def" style="grid-template-columns:150px minmax(0,1fr)"><dt>Vous jouez</dt><dd>{n} · {r.lower()}</dd><dt>Cabinet</dt><dd>{c} (fictif)</dd>'
            f'<dt>Autres personnes</dt><dd>Claire Martin, Louis Bernard (patients) · Dr Sami Rahmani · Mila Dubois</dd><dt>Données</dt><dd>Jeu etaye-design-v1 · référence lundi 2 novembre 2026 16:00</dd>'
            f'<dt>Limites</dt><dd>Données fictives uniquement ; ne saisissez aucune donnée réelle. Les modifications disparaissent à la réinitialisation.</dd></dl>')
    corps = f'{cast}<p style="margin:0">{t}</p><div class="etaye-ligne">{btn("Ouvrir la démonstration", "principal", href_=href("S-02"))}{btn("Retour", "discret", href_=href("S-01"))}</div>'
    return auth_shell(f'<div><div class="ui-libelle etaye-2">Démonstration avec données fictives</div><h1 style="margin-top:var(--espace-1)">Scénario praticienne</h1></div>{carte(corps)}', large=True)


SCREENS = [
    {"id": "S-01", "name": "Vitrine et entrée de démonstration", "states": [
        {"slug": "accueil", "label": "Ready — purpose, one readable sourced sentence, three fictional scenarios, design-status notice", "html": s01()},
        {"slug": "preparation", "label": "Demo preparing", "html": s01("preparation")},
        {"slug": "reinitialisation", "label": "Demo resetting — sessions closed, dataset reloaded", "html": s01("reinitialisation")},
        {"slug": "indisponible", "label": "Demo unavailable — mockups stay readable, retry", "html": s01("indisponible")},
    ]},
    {"id": "I-26", "name": "Choisir ou réinitialiser le scénario de démonstration", "states": [
        {"slug": "ouvert", "label": "Scenario page — cast, practice, fixture version, limits before entry (AC-S01-F)", "html": i26()},
        {"slug": "reinitialiser", "label": "Operator reset — destructive action separated, not primary", "html": i26("reinitialiser")},
    ]},
    {"id": "S-02", "name": "Connexion", "states": [
        {"slug": "connexion", "label": "Initial — professional login, visible label, focus on email", "html": s02(), "viewports": ZOOM},
        {"slug": "envoi", "label": "Submitting — single submit, fields locked", "html": s02("envoi"), "viewports": ZOOM},
        {"slug": "invalide", "label": "Invalid credentials — generic message, email kept, password cleared", "html": s02("invalide"), "viewports": ZOOM},
        {"slug": "champ-vide", "label": "Missing email — error summary + field error", "html": s02("champ-vide"), "viewports": ZOOM},
        {"slug": "suspendu", "label": "Suspended-access guidance after authentication (template: no suspended account in dataset)", "html": s02("suspendu"), "viewports": ZOOM},
        {"slug": "limite", "label": "Throttled — no account disclosure, recovery link", "html": s02("limite"), "viewports": ZOOM},
        {"slug": "retour", "label": "Expired-session return — no clinical data before login", "html": s02("retour"), "viewports": ZOOM},
        {"slug": "patient", "label": "Patient variant — espace patient logo, larger targets, Claire Martin", "html": s02("connexion", True), "viewports": ZOOM},
        {"slug": "patient-invalide", "label": "Patient variant — invalid credentials", "html": s02("invalide", True), "viewports": ZOOM},
    ]},
    {"id": "S-03", "name": "Invitation et activation du compte", "states": [
        {"slug": "equipe", "label": "PENDING team invitation — INV-TEAM-01 Alex Rivière, expiry 05/11/2026 12:00, masked recipient", "html": s03()},
        {"slug": "erreur", "label": "Validation — passwords differ, fields cleared", "html": s03("erreur")},
        {"slug": "expiree", "label": "EXPIRED (template on team invitation)", "html": s03("expiree")},
        {"slug": "revoquee", "label": "REVOKED (template)", "html": s03("revoquee")},
        {"slug": "invalide", "label": "Invalid link", "html": s03("invalide")},
        {"slug": "deja-utilisee", "label": "Concurrent acceptance — already used (template)", "html": s03("deja-utilisee")},
    ]},
    {"id": "I-03", "name": "Activer l'espace patient", "states": [
        {"slug": "ouvert", "label": "Patient activation page — INV-PORTAL-01 before acceptance; birth date check, single-use purpose", "html": i03()},
        {"slug": "erreur", "label": "Ambiguous identity — stops without saying which field differs", "html": i03("erreur")},
        {"slug": "deja-active", "label": "ACCEPTED — already activated 18/10/2026, link unusable", "html": i03("deja-active")},
        {"slug": "expiree", "label": "EXPIRED — INV-PORTAL-02 Louis Bernard, no space created", "html": i03("expiree")},
    ]},
    {"id": "S-04", "name": "Récupération d'accès", "states": [
        {"slug": "demande", "label": "Request — email only", "html": s04()},
        {"slug": "envoye", "label": "Sent — generic, no account enumeration", "html": s04("envoye")},
        {"slug": "nouveau", "label": "Reset form from link", "html": s04("nouveau")},
        {"slug": "nouveau-erreur", "label": "Reset validation — too short, field cleared", "html": s04("nouveau-erreur")},
        {"slug": "lien-expire", "label": "Invalid/expired token — new request, no secret shown", "html": s04("lien-expire")},
        {"slug": "reussi", "label": "Reset succeeded — other sessions closed", "html": s04("reussi")},
        {"slug": "limite", "label": "Throttled", "html": s04("limite")},
        {"slug": "reseau", "label": "Network failure — email kept, retry", "html": s04("reseau")},
    ]},
    {"id": "S-05", "name": "Choix du cabinet et du contexte d'accès", "states": [
        {"slug": "choix", "label": "Multiple contexts — Sami Rahmani in Tilleuls and Canal; publication not allowed in Canal", "html": s05()},
        {"slug": "un-seul", "label": "Single context bypass — Nora Lefebvre goes straight to Today", "html": s05("un-seul")},
        {"slug": "desactive", "label": "Deactivated membership (template)", "html": s05("desactive")},
        {"slug": "retire", "label": "Context removed during selection (template) — nothing opened", "html": s05("retire")},
    ]},
    {"id": "S-36", "name": "Accès refusé et élément introuvable", "states": [
        {"slug": "interdit", "label": "403 — SC-ADMIN-CLINICAL: Mila Dubois tries the clinical workspace; no clinical metadata", "html": s36()},
        {"slug": "introuvable", "label": "404 — SC-ISOLATION: Nora Lefebvre opens R-B-001; no name, no existence disclosure", "html": s36("introuvable")},
        {"slug": "lien-obsolete", "label": "Moved/replaced safe link", "html": s36("obsolete")},
        {"slug": "patient", "label": "Patient side — non-disclosing unavailable page", "html": s36_patient()},
    ]},
    {"id": "S-38", "name": "Session expirée et retour", "states": [
        {"slug": "expiree", "label": "Expired during editing (V-SESSION) — background obscured, draft kept in memory, nothing replayed", "html": s38()},
        {"slug": "enregistrement", "label": "Expired while saving — nothing saved server-side, text kept", "html": s38("enregistrement")},
        {"slug": "publication", "label": "Expired in a publication dialog — never replayed automatically (AC-S38-F)", "html": s38("publication")},
        {"slug": "droits-modifies", "label": "Permission revoked during return — draft not restored", "html": s38("revoque")},
        {"slug": "autre-compte", "label": "Other user signs in — previous drafts unavailable", "html": s38("autre")},
    ]},
    {"id": "I-22", "name": "Expiration de session", "states": [
        {"slug": "ouvert", "label": "Full-page session expiry — Se reconnecter / Quitter la session", "html": i22()},
        {"slug": "publication", "label": "Full-page variant after an interrupted publication", "html": i22("publication")},
    ]},
]

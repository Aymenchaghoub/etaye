"""Administration, account and system (batch 8) — S-31 team and invitations, I-01 invite, S-32 membership permissions,
I-02 deactivate membership, S-33 practice settings, S-34 audit history, I-25 audit event detail, S-35 profile and
preferences, S-37 unavailable services and partial failure.

Data: synthetic-data.json only. Cabinet des Tilleuls (PRA-A): MEM-NORA-A, MEM-SAMI-A, MEM-MILA-A (only TEAM_MANAGE
holder → last-admin guard); INV-TEAM-01 for Alex Rivière (PENDING, expires 5 Nov 12:00, no capability set in the
dataset). Administration never sees clinical text. The dataset contains no audit log: audit rows are derived from
dated dataset facts (publications, validations, corrections, invitations) and scenario outcomes (SC-ADMIN-CLINICAL,
SC-ISOLATION), with "non fourni" where the dataset gives no time or correlation reference.
States without a dataset example are labelled "template" or "variant" and add no clinical value.
"""
from lib import *

N = lambda x: f'<span class="etaye-num">{x}</span>'
NOTE = lambda t: f'<p class="etaye-3" style="font-size:13px;margin:0">{t}</p>'
ACC = {a["id"]: a for a in DATA["accounts"]}
MEMS = [m for m in DATA["memberships"] if m["practiceId"] == "PRA-A"]
INV = by_id("invitations", "INV-TEAM-01")
GROUPS = [
    ("Dossiers cliniques", "Voir, modifier et valider les informations médicales des patients du cabinet.", ["CLINICAL_READ", "CLINICAL_WRITE", "CLINICAL_VALIDATE"]),
    ("Partage avec les patients", "Rendre visible une version déjà validée dans l'espace d'un patient nommé.", ["PATIENT_PUBLISH"]),
    ("Organisation", "Agenda et enregistrement administratif des patients, sans accès aux dossiers cliniques.", ["SCHEDULE_MANAGE", "PATIENT_REGISTER"]),
    ("Administration", "Équipe, réglages et audit administratif, sans accès clinique.", ["TEAM_MANAGE", "PRACTICE_SETTINGS", "ADMIN_AUDIT"]),
    ("Audit clinique", "Consulter l'historique des actions sur les dossiers.", ["CLINICAL_AUDIT"]),
]
SHORT = {"CLINICAL_READ": "lire", "CLINICAL_WRITE": "modifier", "CLINICAL_VALIDATE": "valider", "PATIENT_PUBLISH": "publier",
         "SCHEDULE_MANAGE": "agenda", "PATIENT_REGISTER": "enregistrer des patients", "TEAM_MANAGE": "équipe",
         "PRACTICE_SETTINGS": "réglages", "ADMIN_AUDIT": "audit administratif", "CLINICAL_AUDIT": "audit clinique"}
PD = '<span class="etaye-3" style="font-size:12px"> · PD-001, proposition</span>'


def mail(e):
    """Break e-mail addresses only after @ and before dots, never mid-word."""
    return esc(e).replace("@", "@<wbr>").replace(".", "<wbr>.")


def nom_acc(mem):
    a = ACC[mem["accountId"]]
    pr = [p for p in DATA["practitioners"] if p["membershipId"] == mem["id"]]
    return pr[0]["displayName"] if pr else a["name"]


def caps_resume(caps):
    out = []
    for g, _, ks in GROUPS:
        sel = [SHORT[k] for k in ks if k in caps]
        if sel:
            extra = PD if "PATIENT_REGISTER" in caps and "PATIENT_REGISTER" in ks else ""
            out.append(f'<div><span class="etaye-2">{g}{NNB}:</span> {", ".join(sel)}{extra}</div>')
    return f'<div class="etaye-pile" style="gap:2px;font-size:13px;line-height:20px">{"".join(out)}</div>'


def caps_form(caps, disabled=False, changed=(), name="cap"):
    out = []
    for g, expl, ks in GROUPS:
        opts = []
        for k in ks:
            lab = CAP_FR[k] + (" (PD-001, proposition)" if k == "PATIENT_REGISTER" else "")
            ch = " checked" if k in caps else ""
            dis = " disabled" if disabled else ""
            mark = f' {etiq_mod()}' if k in changed else ""
            opts.append(f'<label><input type="checkbox" name="{name}-{k}"{ch}{dis}><span>{esc(lab)}{mark}</span></label>')
        out.append(f'<fieldset class="etaye-choix" style="border:0;padding:0;margin:0"><legend class="etaye-champ__libelle">{g}</legend>'
                   f'<p class="etaye-2" style="margin:0 0 var(--espace-2);font-size:13px">{expl}</p>{"".join(opts)}</fieldset>')
    return f'<div class="etaye-pile">{"".join(out)}</div>'


def etiq_mod():
    return '<span class="etaye-etiquette etaye-etiquette--humain">Modifié</span>'


def admin(actif, body, titre, meta="", actions="", bande="", fil_=""):
    return pro_shell(actif, f'<div class="etaye-page">{entete(titre, meta, actions, fil_)}{bande}{body}</div>', "mila")


# ═════════════════════════════════ S-31 team and invitations ═════════════════════════════════
def membres_table(rows_override=None, suspendu=None):
    rows = []
    for m in MEMS:
        a = ACC[m["accountId"]]
        statut_ = st("membership", "SUSPENDED") if suspendu == m["id"] else st("membership", "ACTIVE")
        moi = ' <span class="etaye-3" style="font-size:12px">(vous)</span>' if m["accountId"] == "ACC-MILA" else ""
        rows.append([f'<strong>{esc(nom_acc(m))}</strong>{moi}<div class="etaye-2" style="font-size:13px;overflow-wrap:break-word">{mail(a["email"])}</div>',
                     caps_resume(m["capabilities"]), statut_, lien("Voir l'accès", href("S-32", "soi-meme" if m["accountId"] == "ACC-MILA" else "membre"))])
    return tableau(["Membre", "Droits dans ce cabinet", "Accès", ""], rows_override or rows, stack=True)


def invitation_row(status="PENDING", email=None):
    email = email or ACC["ACC-NEW"]["email"]
    exp = {"PENDING": f'expire le {N(dh_pro(INV["expiresAt"]))}', "EXPIRED": f'expirée le {N(dh_pro(INV["expiresAt"]))}', "REVOKED": "révoquée (gabarit)"}[status]
    acts = (btn("Renvoyer", "secondaire", "envoyer", href_=href("I-01", "renvoyer")) + btn("Révoquer", "discret")) if status == "PENDING" else btn("Inviter à nouveau", "secondaire", "envoyer", href_=href("I-01", "ouvert"))
    return [f'<strong>{esc(ACC["ACC-NEW"]["name"])}</strong><div class="etaye-2" style="font-size:13px;overflow-wrap:break-word">{mail(email)}</div>',
            '<span class="etaye-2" style="font-size:13px">Droits non précisés dans le jeu de démonstration</span>',
            f'{st("invitation", status)}<div class="etaye-3" style="font-size:12px">{exp}</div>', f'<div class="etaye-ligne">{acts}</div>']


def s31(mode="equipe"):
    acts = btn("Inviter un membre", "principal", "ajouter", href_=href("I-01"))
    regle = NOTE("L'administration gère les accès sans ouvrir les dossiers : aucun nom de patient ni extrait clinique n'apparaît ici.")
    inv_status = {"invitation-expiree": "EXPIRED", "invitation-revoquee": "REVOKED"}.get(mode, "PENDING")
    bande = ""
    if mode == "erreur":
        body = (bandeau("attention", "La liste de l'équipe n'a pas pu être chargée", "Rien n'a été modifié. Réessayez ; les invitations en cours restent valables.",
                        btn("Réessayer", "secondaire", "relancer"), role="alert") + squelette(3) + regle)
        return admin("Équipe", body, "Équipe du Cabinet des Tilleuls", actions=acts)
    if mode == "vide":
        body = (vide("Vous êtes seule dans ce cabinet", "Invitez les praticiens et le secrétariat. Chacun reçoit uniquement les droits que vous cochez.", acts)
                + NOTE("Gabarit : le cabinet de démonstration compte trois membres."))
        return admin("Équipe", body, "Équipe du Cabinet des Tilleuls")
    if mode == "suspendu":
        bande = bandeau("info", "Accès suspendu", "L'accès de Dr Sami Rahmani au Cabinet des Tilleuls est suspendu (variante). Son compte n'est pas modifié ; ses actions passées restent dans l'audit.")
    if mode == "dernier-admin":
        bande = bandeau("attention", "Vous êtes la seule administratrice", "Personne d'autre ne détient « Gérer l'équipe » dans ce cabinet. Vos droits d'administration ne peuvent pas être retirés tant qu'un autre membre ne les a pas reçus.",
                        lien("Voir mon accès", href("S-32", "soi-meme")))
    email = "alex.riviere.secretariat.cabinet-des-tilleuls@example.test" if mode == "longs" else None
    inv = tableau(["Personne invitée", "Droits proposés", "Invitation", ""], [invitation_row(inv_status, email)], stack=True)
    body = (panneau(f"Membres · {N(len(MEMS))}", membres_table(suspendu="MEM-SAMI-A" if mode == "suspendu" else None))
            + panneau("Invitations", inv) + regle
            + (NOTE("Variante : adresse longue (example.test) pour vérifier le retour à la ligne sans masquer les actions.") if mode == "longs" else "")
            + (NOTE("Variante : INV-TEAM-01 après son expiration du 05/11/2026 12:00. Un lien expiré n'ouvre aucun accès.") if mode == "invitation-expiree" else "")
            + (NOTE("Gabarit : invitation révoquée ; le lien envoyé ne fonctionne plus.") if mode == "invitation-revoquee" else ""))
    return admin("Équipe", body, "Équipe du Cabinet des Tilleuls", meta="Mila Dubois · administration", actions=acts, bande=bande)


# ═════════════════════════════════ I-01 invite a team member ═════════════════════════════════
def i01(mode="ouvert"):
    fond = s31()
    pied = btn("Annuler", "secondaire") + btn("Envoyer l'invitation", "principal", "envoyer")
    exp = NOTE("L'invitation expire 72 heures après l'envoi (proposition à valider). Elle ne donne accès qu'au Cabinet des Tilleuls.")
    if mode == "envoye":
        corps = (bandeau("info", "Invitation enregistrée", f"Alex Rivière ({esc(ACC['ACC-NEW']['email'])}) recevra un lien à usage unique. E-mail M-01 en file d'attente : l'envoi est confirmé séparément.", icon="valide")
                 + f'<dl class="etaye-def"><dt>Invitation</dt><dd>INV-TEAM-01 · {st("invitation", "PENDING")}</dd><dt>Expire</dt><dd>{N(dh_pro(INV["expiresAt"]))}</dd><dt>Distribution</dt><dd>En file d\'attente</dd></dl>'
                 + NOTE("Rien n'est accordé avant l'activation du compte par la personne invitée."))
        return dialogue(fond, "Inviter un membre", corps, btn("Fermer", "secondaire"))
    if mode == "renvoyer":
        corps = (f'<p style="margin:0">Renvoyer l\'invitation à <strong>{esc(ACC["ACC-NEW"]["email"])}</strong>{NNB}?</p>'
                 + bandeau("attention", "L'ancien lien sera révoqué", "Le lien envoyé précédemment ne fonctionnera plus. Un nouveau lien, avec une nouvelle date d'expiration, sera envoyé.")
                 + exp)
        return dialogue(fond, "Renvoyer l'invitation", corps, btn("Annuler", "secondaire") + btn("Renvoyer l'invitation", "principal", "envoyer"))
    err = mode == "erreur"
    email_v = {"erreur": "alex.riviere@example", "existant": ACC["ACC-NEW"]["email"], "combinaison": "alex.riviere@example.test"}.get(mode, "")
    top = ""
    caps, changed = (), ()
    if err:
        top = resume_erreurs([("i01-mail", "Adresse e-mail : format incomplet"), ("i01-caps", "Droits : cochez au moins un droit")])
    if mode == "existant":
        top = bandeau("info", "Une invitation est déjà en attente pour cette adresse",
                      f"INV-TEAM-01 expire le {N(dh_pro(INV['expiresAt']))}. Renvoyez-la plutôt que d'en créer une seconde.",
                      btn("Renvoyer l'invitation", "secondaire", "envoyer", href_=href("I-01", "renvoyer")))
    if mode == "combinaison":
        caps = ("PATIENT_PUBLISH",)
        top = resume_erreurs([("i01-caps", "« Publier pour les patients » nécessite « Lire les dossiers » : combinaison refusée")])
    mail = champ("Adresse e-mail", email_v, "i01-mail", required=True, typ="email", focus=mode == "ouvert",
                 error="Saisissez une adresse complète, par exemple prenom.nom@example.test" if err else None,
                 help_="La personne crée son mot de passe à l'activation." if not err else None)
    caps_html = f'<div id="i01-caps">{caps_form(caps, changed=changed, name="i01")}</div>'
    if err:
        caps_html = f'<div id="i01-caps" class="etaye-champ--erreur">{caps_form((), name="i01")}<p style="margin:var(--espace-2) 0 0;font-weight:500">Erreur{NNB}: cochez au moins un droit.</p></div>'
    if mode == "combinaison":
        caps_html = f'<div id="i01-caps">{caps_form(caps, name="i01")}<p style="margin:var(--espace-2) 0 0;font-weight:500">Erreur{NNB}: la publication pour les patients demande l\'accès en lecture aux dossiers. Refusé par le serveur ; rien n\'a été envoyé.</p></div>'
    corps = top + mail + caps_html + exp + NOTE("Aucun détail clinique n'est inclus dans l'e-mail d'invitation.")
    if mode == "combinaison":
        corps += NOTE("Gabarit : contrôle de combinaison côté serveur.")
    return dialogue(fond, "Inviter un membre", corps, pied, large=True)


# ═════════════════════════════════ S-32 membership permissions ═════════════════════════════════
def s32(mode="membre"):
    ids = {"membre": "MEM-SAMI-A", "modifie": "MEM-SAMI-A", "conflit": "MEM-SAMI-A", "desactive": "MEM-SAMI-A",
           "soi-meme": "MEM-MILA-A", "dependances": "MEM-NORA-A"}
    fil_ = f'<a href="{href("S-31")}">Équipe</a>'
    if mode == "invitation":
        a = ACC["ACC-NEW"]
        body = (panneau("Invitation en attente", f'<dl class="etaye-def"><dt>Personne</dt><dd>{esc(a["name"])} · {esc(a["email"])}</dd><dt>Compte</dt><dd>En attente d\'activation</dd>'
                        f'<dt>Invitation</dt><dd>{st("invitation", "PENDING")} · expire le {N(dh_pro(INV["expiresAt"]))}</dd><dt>Droits</dt><dd>Non précisés dans le jeu de démonstration</dd></dl>')
                + NOTE("Les droits ne s'appliquent qu'après l'activation. Pour les changer avant, renvoyez une invitation : l'ancien lien est révoqué.")
                + f'<div class="etaye-ligne">{btn("Renvoyer l’invitation", "secondaire", "envoyer", href_=href("I-01", "renvoyer"))}{btn("Révoquer l’invitation", "retrait", "retirer")}</div>')
        return admin("Équipe", body, "Alex Rivière", meta="Cabinet des Tilleuls · invitation INV-TEAM-01", fil_=fil_)
    m = by_id("memberships", ids[mode])
    a = ACC[m["accountId"]]
    soi = mode == "soi-meme"
    caps = list(m["capabilities"])
    changed = ()
    if mode == "modifie":
        caps.remove("PATIENT_PUBLISH"); changed = ("PATIENT_PUBLISH",)
    ident = (f'<dl class="etaye-def"><dt>Compte</dt><dd>{esc(a["name"])} · {esc(a["email"])}</dd><dt>Cabinet</dt><dd>Cabinet des Tilleuls · {esc(m["id"])}</dd>'
             f'<dt>Accès</dt><dd>{st("membership", "SUSPENDED" if mode == "desactive" else "ACTIVE")}</dd></dl>')
    bande = ""
    side = panneau("À savoir", '<ul style="margin:0;padding-left:20px"><li>Chaque droit est accordé explicitement ; aucun n\'est déduit d\'un autre.</li>'
                   '<li>Les droits d\'administration ne donnent aucun accès aux dossiers.</li><li>Un changement s\'applique aux sessions ouvertes dès l\'enregistrement.</li></ul>')
    sv = enregistrement("ok", "15:40") if mode != "modifie" else enregistrement("sale")
    acts = (btn("Enregistrer les droits", "principal", disabled=soi or mode in ("desactive",))
            + btn("Désactiver l'accès à ce cabinet", "retrait", "retirer", href_=href("I-02", "dependances" if mode == "dependances" else ("dernier-admin" if soi else "ouvert")), disabled=mode == "desactive"))
    if mode == "modifie":
        side = panneau("Conséquences", f'<ul style="margin:0;padding-left:20px"><li>Dr Sami Rahmani ne pourra plus publier pour les patients du Cabinet des Tilleuls.</li>'
                       '<li>Les publications déjà faites restent visibles pour leurs patients.</li><li>Ses sessions ouvertes perdent ce droit dès l\'enregistrement.</li></ul>')
    if mode == "conflit":
        bande = bandeau("attention", "Ces droits ont changé pendant votre modification", "Une autre session a enregistré une version plus récente (gabarit). Rien n'a été écrasé : rechargez pour voir les droits actuels, puis refaites votre choix.",
                        btn("Recharger", "secondaire", "relancer"), role="alert")
    if soi:
        bande = bandeau("info", "Votre propre accès", "Vous ne pouvez pas modifier vos propres droits. Vous êtes la seule administratrice du cabinet : ces droits restent en place tant qu'un autre membre n'a pas reçu « Gérer l'équipe ».")
    if mode == "desactive":
        bande = bandeau("info", "Accès suspendu (variante)", "Dr Sami Rahmani ne peut plus ouvrir le Cabinet des Tilleuls ; ses sessions ouvertes ont perdu ces droits. Son compte n'est pas suspendu et l'audit conserve ses actions.",
                        btn("Réactiver l'accès", "secondaire"))
    if mode == "dependances":
        apt, av = by_id("appointments", "APT-A-001"), by_id("availability", "AVL-A-001")
        side = panneau("Éléments liés à cet accès", f'<ul style="margin:0;padding-left:20px"><li>Rendez-vous à venir{NNB}: {N(dh_pro(apt["startsAt"]))} ({esc(apt["id"])})</li>'
                       f'<li>Plage de disponibilité{NNB}: {N(dh_pro(av["startsAt"]))}–{N(h_pro(av["endsAt"]))} ({esc(av["id"])})</li></ul>'
                       + NOTE("Avant une désactivation, une personne autorisée réattribue ou annule ces rendez-vous. Aucun contenu clinique n'est affiché ici."))
    form = panneau("Droits dans ce cabinet", caps_form(caps, disabled=soi or mode == "desactive", changed=changed) + f'<div class="etaye-ligne" style="margin-top:var(--espace-4)">{sv}</div>')
    body = (f'<div class="etaye-cols etaye-cols--2"><div class="etaye-pile">{panneau("Compte et accès", ident)}{form}</div><div class="etaye-pile">{side}</div></div>'
            f'<div class="etaye-ligne">{acts}</div>'
            + NOTE("Ces réglages ne concernent que le Cabinet des Tilleuls. Le compte global et les accès éventuels à d'autres cabinets ne sont ni visibles ni modifiés ici."))
    return admin("Équipe", body, esc(nom_acc(m)), meta=f"Accès au Cabinet des Tilleuls{' · vous' if soi else ''}", fil_=fil_, bande=bande)


# ═════════════════════════════════ I-02 deactivate membership ═════════════════════════════════
def i02(mode="ouvert"):
    who = {"ouvert": "MEM-SAMI-A", "erreur": "MEM-SAMI-A", "conflit": "MEM-SAMI-A", "dernier-admin": "MEM-MILA-A", "dependances": "MEM-NORA-A"}[mode]
    m = by_id("memberships", who)
    fond = s32({"MEM-SAMI-A": "membre", "MEM-MILA-A": "soi-meme", "MEM-NORA-A": "dependances"}[who])
    n = nom_acc(m)
    resume = (f'<dl class="etaye-def"><dt>Membre</dt><dd>{esc(n)}</dd><dt>Cabinet</dt><dd>Cabinet des Tilleuls</dd><dt>Droits retirés</dt><dd>{caps_resume(m["capabilities"])}</dd></dl>')
    cons = ('<ul style="margin:0;padding-left:20px"><li>Plus aucun accès au Cabinet des Tilleuls ; les sessions ouvertes perdent ces droits.</li>'
            '<li>Le compte n\'est pas supprimé ; les actions passées restent dans l\'audit.</li></ul>')
    motif = champ("Motif", "", "i02-motif", kind="textarea", rows=2, required=True,
                  error="Indiquez pourquoi cet accès est désactivé." if mode == "erreur" else None,
                  help_=None if mode == "erreur" else "Visible dans l'audit administratif.")
    pied = btn("Conserver l'accès", "secondaire") + btn("Désactiver l'accès", "retrait", "retirer")
    if mode == "dernier-admin":
        corps = (bandeau("attention", "Désactivation impossible", "Vous êtes la seule personne à pouvoir gérer l'équipe de ce cabinet. Donnez d'abord « Gérer l'équipe » à un autre membre.", role="alert") + resume)
        return dialogue(fond, "Désactiver cet accès", corps, btn("Conserver l'accès", "secondaire") + btn("Désactiver l'accès", "retrait", "retirer", disabled=True))
    top = ""
    if mode == "erreur":
        top = resume_erreurs([("i02-motif", "Motif : à renseigner")])
    if mode == "conflit":
        top = bandeau("attention", "Cet accès a changé depuis l'ouverture", "Ses droits ont été modifiés dans une autre session (gabarit). Rien n'a été désactivé ; relisez la situation actuelle.", btn("Recharger", "secondaire", "relancer"), role="alert")
    if mode == "dependances":
        apt = by_id("appointments", "APT-A-001")
        top = bandeau("attention", "Un rendez-vous et une plage de disponibilité dépendent de cet accès",
                      f"Rendez-vous du {N(dh_pro(apt['startsAt']))} et plage AVL-A-001. Ils restent dans l'agenda : réattribuez-les ou annulez-les avant, ou juste après, avec une personne autorisée.")
    corps = top + resume + panneau("Conséquences", cons) + motif
    return dialogue(fond, "Désactiver cet accès", corps, pied)


# ═════════════════════════════════ S-33 practice settings ═════════════════════════════════
def s33(mode="cabinet"):
    contact = "Contact par le secrétariat du cabinet"
    if mode in ("sale", "erreur"):
        contact = "" if mode == "erreur" else "Contact par le secrétariat du cabinet, du lundi au vendredi"
    top = ""
    if mode == "erreur":
        top = resume_erreurs([("s33-contact", "Texte de contact affiché dans l'espace patient : à renseigner")])
    if mode == "conflit":
        top = bandeau("attention", "Ces réglages ont été modifiés ailleurs", "Une autre session a enregistré une version plus récente (gabarit). Votre saisie est conservée ici ; rien n'a été écrasé.",
                      btn("Comparer", "secondaire", "comparer"), role="alert")
    if mode == "enregistre":
        top = toast("Réglages enregistrés")
    tz = "Europe/Paris"
    tz_panel = ""
    if mode == "fuseau":
        tz = "America/Martinique"
        tz_panel = panneau("Effet du changement de fuseau (aperçu)", f'<p style="margin:0">Les instants enregistrés ne changent pas. Seul l\'affichage change{NNB}:</p>'
                           + tableau(["Élément", "Affiché aujourd'hui (Europe/Paris)", "Affiché après (America/Martinique)"],
                                     [["Rendez-vous APT-A-001", N("03/11/2026 10:30"), N("03/11/2026 05:30")], ["Plage AVL-A-001", N("09:00–12:00"), N("04:00–07:00")],
                                      ["Dates des sources", "inchangées", "inchangées"]], stack=True)
                           + NOTE("Variante : le cabinet de démonstration reste sur Europe/Paris."))
    form = (champ("Nom du cabinet", "Cabinet des Tilleuls", "s33-nom", required=True)
            + champ("Fuseau horaire", tz, "s33-tz", kind="select", options=["Europe/Paris", "America/Martinique"], help_="Change l'affichage des heures, jamais les instants enregistrés.")
            + choix("s33-resa", [("Les patients peuvent réserver les créneaux ouverts à la réservation", "Créneaux de 30 minutes, selon les disponibilités de chaque praticien")], typ="checkbox", checked=(0,), legend="Réservation en ligne")
            + champ("Texte de contact affiché dans l'espace patient", contact, "s33-contact", kind="textarea", rows=2, required=True,
                    error="Indiquez comment les patients peuvent joindre le cabinet." if mode == "erreur" else None,
                    help_=None if mode == "erreur" else "Pas de conseil médical : uniquement le moyen de contact.")
            + f'<div class="etaye-pile" style="gap:var(--espace-1)"><div class="etaye-champ__libelle">Logo</div><p class="etaye-2" style="margin:0;font-size:14px">Le logo Étaye est fixe ; le nom du cabinet s\'affiche en texte dans les en-têtes et les documents.</p></div>')
    sv = {"sale": enregistrement("sale"), "erreur": enregistrement("sale"), "conflit": enregistrement("echec"), "fuseau": enregistrement("sale")}.get(mode, enregistrement("ok", "15:40"))
    acts = f'<div class="etaye-ligne">{sv}{btn("Annuler les modifications", "secondaire", disabled=mode in ("cabinet", "enregistre"))}{btn("Enregistrer les réglages", "principal")}</div>'
    body = (top + f'<div class="etaye-cols etaye-cols--2"><div class="etaye-pile">{panneau("Cabinet", form)}{tz_panel}{acts}</div><div class="etaye-pile">'
            + panneau("Hors de ces réglages", '<ul style="margin:0;padding-left:20px"><li>Aucune règle médicale par défaut</li><li>Aucun identifiant de production ni clé d\'accès</li><li>Les disponibilités se règlent par praticien (S-15)</li></ul>')
            + "</div></div>" + (NOTE("Heure d'enregistrement 15:40 : variante, le jeu de démonstration ne date pas les réglages.") if mode == "cabinet" else ""))
    return admin("Réglages du cabinet", body, "Réglages du cabinet", meta="Cabinet des Tilleuls · Mila Dubois")


# ═════════════════════════════════ S-34 audit history and I-25 event detail ═════════════════════════════════
NF = '<span class="etaye-3">non fournie</span>'
ADMIN_EV = [
    (NF, "Mila Dubois", "Ouverture d'un dossier clinique", "Dossier R-A-001", statut("attention", "Refusé · 403", "interdit"), "SC-ADMIN-CLINICAL"),
    (NF, "Mila Dubois", "Invitation d'équipe envoyée", "INV-TEAM-01 · alex.riviere@example.test", st("invitation", "PENDING"), "expire le 05/11/2026 12:00"),
    (N("30/10/2026 12:00"), "Système", "Invitation à l'espace patient expirée", "INV-PORTAL-02 · dossier R-A-002", st("invitation", "EXPIRED"), ""),
    (N("18/10/2026 12:00"), "Claire Martin", "Invitation à l'espace patient acceptée", "INV-PORTAL-01 · dossier R-A-001", st("invitation", "ACCEPTED"), ""),
]
NORA_ = "Dr Nora Lefebvre"
CLIN_EV = [
    ("02/11/2026 14:25", NORA_, "Publication pour Claire Martin", "PUB-A-002 · PATIENT-A-001-v2", "remplace PUB-A-001"),
    ("02/11/2026 14:20", NORA_, "Validation", "PATIENT-A-001-v2", "texte généré rejeté et corrigé"),
    ("02/11/2026 14:05:00", "Tâche AI-A-001", "Brouillon généré", "BRIEF-A-001-v1", "brouillon, non validé"),
    ("02/11/2026 14:04:50", NORA_, "Génération demandée", "AI-A-001", ""),
    ("02/11/2026 09:10", "Claire Martin", "Préparation de visite reçue", "PREP-A-001 · SRC-A-005", "PATIENT-A-001-v1 devient « Sources modifiées »"),
    ("20/10/2026 10:50", NORA_, "Publication pour Claire Martin", "PUB-A-001 · PATIENT-A-001-v1", "remplacée le 02/11/2026"),
    ("20/10/2026 10:45", NORA_, "Validation", "PATIENT-A-001-v1", ""),
    ("20/10/2026 10:40", NORA_, "Validation", "REPORT-A-001-v1", "non publié"),
    ("20/10/2026 10:35", NORA_, "Consultation terminée", "CONS-A-001", ""),
    ("20/10/2026 10:15", NORA_, "Correction d'une mesure", "SRC-A-007-v2 remplace SRC-A-007-v1", "motif enregistré"),
    ("20/10/2026 10:13", NORA_, "Mesure enregistrée", "SRC-A-007-v1", ""),
    ("20/10/2026 10:12", NORA_, "Mesure enregistrée", "SRC-A-004", ""),
]


def filtres(actif=None):
    f = champ("Période", "Du 14/09/2026 au 02/11/2026", "aud-periode") + champ("Type d'action", actif or "Toutes", "aud-type", kind="select", options=["Toutes", "Publication", "Validation", "Correction", "Accès"]) + champ("Auteur", "Tous", "aud-auteur", kind="select", options=["Tous", "Dr Nora Lefebvre", "Claire Martin", "Mila Dubois"])
    return f'<div class="etaye-form__ligne">{f}</div>'


def clin_table(rows, detail=None):
    out = []
    for i, (t, who, act, res, note) in enumerate(rows):
        out.append([N(t), esc(who), f'{esc(act)}{f"<div class=\"etaye-3\" style=\"font-size:12px\">{esc(note)}</div>" if note else ""}', f'<span style="overflow-wrap:anywhere">{esc(res)}</span>', statut("neutre", "Réussi", "valide"),
                    lien("Afficher le détail autorisé", href("I-25", "ouvert"))])
    return tableau(["Date", "Auteur", "Action", "Ressource", "Résultat", ""], out, stack=True)


def s34(mode="admin"):
    if mode in ("admin", "restreint", "export", "erreur-partielle", "vide"):
        rows = [[t, esc(w), esc(a), f'<span style="overflow-wrap:break-word">{mail(r)}</span>', o, f'<span class="etaye-3" style="font-size:12px">{esc(x)}</span>'] for t, w, a, r, o, x in ADMIN_EV]
        top, extra = "", ""
        if mode == "vide":
            body = (filtres("Accès") + vide("Aucun événement pour ces filtres", "Élargissez la période ou retirez un filtre.", btn("Retirer les filtres", "secondaire")))
            return admin("Audit administratif", body, "Audit administratif", meta="Cabinet des Tilleuls · lecture seule")
        if mode == "restreint":
            top = bandeau("info", "Événements cliniques non affichés", "Votre accès « Consulter l'audit administratif » montre les membres, invitations, réglages et refus d'accès. Les actions sur les dossiers demandent « Consulter l'audit clinique ».", icon="verrou")
        if mode == "export":
            top = bandeau("neutre", "Export indisponible", "L'export de l'audit n'est pas proposé dans cette démonstration (gabarit). La consultation à l'écran reste possible.")
        if mode == "erreur-partielle":
            top = bandeau("attention", "Une partie de l'historique n'a pas pu être chargée", "Les événements avant le 18/10/2026 ne sont pas affichés pour le moment. Ceux ci-dessous sont complets ; ne pas conclure à l'absence d'événement plus ancien.", btn("Réessayer", "secondaire", "relancer"), role="alert")
        body = (top + filtres() + panneau("Événements", tableau(["Date", "Auteur", "Action", "Ressource", "Résultat", "Repère"], rows, stack=True))
                + NOTE("Lecture seule : aucun événement ne peut être modifié ou supprimé. Les heures et références de corrélation non fournies par le jeu de démonstration sont indiquées comme telles."))
        acts = btn("Exporter", "secondaire", "telecharger", disabled=mode == "export")
        return admin("Audit administratif", body, "Audit administratif", meta="Cabinet des Tilleuls · lecture seule", actions=acts)
    # clinique: Nora with CLINICAL_AUDIT on Claire's record
    body = (filtres() + panneau("Historique du dossier de Claire Martin", clin_table(CLIN_EV))
            + NOTE("Dérivé des dates du jeu de démonstration. Les publications enregistrent la version exacte et son auteur ; aucun texte de source n'est recopié dans l'audit."))
    return pro_shell("Patients", f'<div class="etaye-page">{entete("Historique des actions", "Dossier R-A-001 · audit clinique · lecture seule", "", fil=f"<a href=\"{href("S-07")}\">Patients</a> › <a href=\"{href("S-09", "claire")}\">MARTIN Claire</a>")}{body}</div>',
                     "nora", banner=banniere("R-A-001"))


def i25(mode="ouvert"):
    if mode == "admin":
        det = (f'<dl class="etaye-def"><dt>Action</dt><dd>Ouverture d\'un dossier clinique</dd><dt>Auteur</dt><dd>Mila Dubois · administration</dd>'
               f'<dt>Ressource</dt><dd>Dossier R-A-001</dd><dt>Résultat</dt><dd>{statut("attention", "Refusé · 403", "interdit")}</dd><dt>Date</dt><dd>{NF}</dd><dt>Corrélation</dt><dd>{NF}</dd></dl>'
               + NOTE("SC-ADMIN-CLINICAL. Aucun contenu du dossier n'est enregistré dans l'événement."))
        fond = s34("admin")
        return tiroir(fond, "Détail de l'événement", det, btn("Fermer", "secondaire"))
    if mode in ("filtres", "aucun"):
        rows = [r for r in CLIN_EV if r[2].startswith("Publication")]
        if mode == "aucun":
            body = filtres("Correction").replace("Du 14/09/2026 au 02/11/2026", "Du 01/11/2026 au 02/11/2026") + vide("Aucun événement pour ces filtres", "Aucune correction entre le 01/11/2026 et le 02/11/2026.", btn("Retirer les filtres", "secondaire"))
        else:
            body = filtres("Publication") + panneau(f"Événements · {N(len(rows))}", clin_table(rows))
        return pro_shell("Patients", f'<div class="etaye-page">{entete("Historique des actions", "Dossier R-A-001 · audit clinique · lecture seule")}{body}</div>', "nora", banner=banniere("R-A-001"))
    pub = by_id("publications", "PUB-A-002")
    det = (f'<dl class="etaye-def"><dt>Action</dt><dd>Publication pour Claire Martin</dd><dt>Auteur</dt><dd>Dr Nora Lefebvre</dd><dt>Date</dt><dd>{N(dh_pro(pub["publishedAt"]))}</dd>'
           f'<dt>Ressource</dt><dd>PUB-A-002</dd><dt>Version publiée</dt><dd>PATIENT-A-001-v2 · validée le {N("02/11/2026 14:20")} · figée</dd><dt>Remplace</dt><dd>PUB-A-001</dd>'
           f'<dt>Extraits partagés</dt><dd>SRC-A-001, SRC-A-005, SRC-A-006 (identifiants seulement)</dd><dt>Résultat</dt><dd>{statut("neutre", "Réussi", "valide")}</dd><dt>Corrélation</dt><dd>{NF}</dd></dl>'
           + NOTE("Le texte des sources n'est jamais recopié dans l'audit."))
    return tiroir(s34("clinique"), "Détail de l'événement", det, btn("Fermer", "secondaire"))


# ═════════════════════════════════ S-35 profile and preferences ═════════════════════════════════
def theme_choix(checked=0, patient=False):
    return choix("theme", [("Clair", None), ("Sombre", None), ("Suivre le réglage de l'appareil", None)], legend="Thème", checked=(checked,))


def s35(mode="pro"):
    if mode == "patient":
        a = ACC["ACC-CLAIRE"]
        body = (f'<h1 style="margin:0">Mon profil</h1>'
                + f'<section class="etaye-carte-patient"><dl class="etaye-def" style="grid-template-columns:minmax(0,1fr);font-size:17px"><dt>Nom</dt><dd>{esc(a["name"])}</dd><dt>Adresse e-mail</dt><dd style="overflow-wrap:anywhere">{esc(a["email"])}</dd>'
                  f'<dt>Votre espace est relié à</dt><dd>Cabinet des Tilleuls</dd></dl>'
                  + NOTE("Pour corriger votre nom ou votre date de naissance, contactez le secrétariat du cabinet.") + "</section>"
                + f'<section class="etaye-carte-patient">{theme_choix(0, True)}</section>'
                + f'<div class="etaye-actions-patient">{btn("Changer mon mot de passe", "secondaire", patient=True, href_=href("S-04"))}{btn("Me déconnecter", "secondaire", "sortie", patient=True, href_=href("S-02", "patient"))}</div>')
        return patient_shell("", body)
    actor = "mila" if mode == "admin" else "nora"
    a = ACC["ACC-MILA" if actor == "mila" else "ACC-NORA"]
    affiche = "Mila Dubois" if actor == "mila" else "Dr Nora Lefebvre"
    role = "Administration · Cabinet des Tilleuls" if actor == "mila" else "Praticienne · Cabinet des Tilleuls"
    top = ""
    if mode == "enregistre":
        top = toast("Préférences enregistrées")
    if mode == "erreur":
        top = bandeau("critique", "Vos préférences n'ont pas été enregistrées", "Le service ne répond pas. Votre choix est conservé sur cette page ; réessayez.", btn("Réessayer", "secondaire", "relancer"), role="alert")
    if mode == "session":
        top = bandeau("attention", "Votre session a expiré", "Reconnectez-vous pour enregistrer. Votre choix de thème n'a pas été envoyé.", btn("Se reconnecter", "principal", href_=href("S-38", "expiree")), role="alert")
    ident = (f'<dl class="etaye-def"><dt>Nom affiché</dt><dd>{esc(affiche)}</dd><dt>Adresse e-mail</dt><dd style="overflow-wrap:anywhere">{esc(a["email"])}</dd>'
             f'<dt>Accès</dt><dd>{esc(role)}</dd></dl>'
             + NOTE("Les droits dans un cabinet sont gérés par son administration ; cette page ne les modifie pas."))
    prefs = (theme_choix(1 if mode in ("sale", "erreur", "session") else 0)
             + champ("Langue", "Français", "s35-langue", readonly=True, help_="L'interface est conçue en français ; aucune autre langue n'est prévue dans cette version.")
             + champ("Fuseau d'affichage", "Fuseau du cabinet · Europe/Paris", "s35-tz", readonly=True, help_="Les dates des sources et des rendez-vous ne sont jamais réécrites."))
    sv = {"sale": enregistrement("sale"), "erreur": enregistrement("echec"), "session": enregistrement("sale"), "enregistre": enregistrement("ok", "15:42")}.get(mode, enregistrement("ok", "15:40"))
    secu = f'<div class="etaye-ligne">{btn("Changer le mot de passe", "secondaire", "cle", href_=href("S-04"))}{btn("Se déconnecter", "secondaire", "sortie", href_=href("S-02"))}</div>'
    body = (top + f'<div class="etaye-cols etaye-cols--egal">{panneau("Identité", ident)}{panneau("Préférences", prefs + f"<div class=\"etaye-ligne\">{sv}{btn("Enregistrer", "principal")}</div>")}</div>'
            + panneau("Sécurité du compte", secu) + (NOTE("Heures d'enregistrement : variantes.") if mode in ("pro", "admin") else ""))
    return pro_shell("Profil", f'<div class="etaye-page">{entete("Profil et préférences", esc(affiche))}{body}</div>', actor)


# ═════════════════════════════════ S-37 unavailable services and partial failure ═════════════════════════════════
REF = "Référence de l'incident : non fournie dans la démonstration (jamais de donnée patient ni de secret)"


def s37(mode="indisponible"):
    if mode in ("indisponible", "maintenance", "limite", "delai"):
        t = {"indisponible": ("Le service ne répond pas", "Étaye est momentanément indisponible (503). Aucune action n'a été enregistrée pendant l'interruption ; vos saisies non envoyées restent sur cette page."),
             "maintenance": ("Démonstration en cours de réinitialisation", "Les données fictives sont remises à leur état de départ (lundi 2 novembre 2026 16:00). Les comptes de démonstration seront de nouveau disponibles ensuite."),
             "limite": ("Trop de demandes en peu de temps", "Le service a refusé la demande (429). Attendez quelques instants avant de réessayer ; rien n'a été enregistré deux fois."),
             "delai": ("La demande a pris trop de temps", "Nous ne savons pas si l'action a abouti. Vérifiez son résultat avant de la refaire : aucune action n'est renvoyée automatiquement.")}[mode]
        act = {"indisponible": btn("Réessayer", "principal", "relancer"), "maintenance": btn("Revenir à l'accueil", "secondaire", href_=href("S-01")),
               "limite": btn("Réessayer", "secondaire", "relancer"), "delai": btn("Vérifier le résultat", "principal", "recherche")}[mode]
        body = f'<div class="etaye-pile" style="max-width:640px">{bandeau("attention" if mode != "maintenance" else "neutre", t[0], t[1], role="alert")}<div class="etaye-ligne">{act}</div>{NOTE(REF)}</div>'
        if mode == "maintenance":
            return auth_shell(body)
        return pro_shell("Aujourd'hui", f'<div class="etaye-page">{entete("Aujourd'hui", "Lundi 2 novembre 2026")}{body}</div>', "nora")
    if mode in ("hors-ligne", "reconnexion", "partiel"):
        b = {"hors-ligne": bandeau("attention", "Hors ligne", "Vous consultez la dernière version chargée (16:00). Les modifications sont désactivées jusqu'au retour de la connexion.", icon="horsligne", role="alert"),
             "reconnexion": bandeau("neutre", "Reconnexion en cours", "La page se met à jour dès que la connexion revient. Rien n'est envoyé tant qu'elle n'est pas rétablie.", icon="horloge"),
             "partiel": bandeau("attention", "Une partie du dossier n'a pas pu être chargée", "Les résultats de biologie ne sont pas affichés. L'identité, les allergies et la chronologie ci-dessous sont à jour.", btn("Recharger les résultats", "secondaire", "relancer"), role="alert")}[mode]
        chron = chrono([{"date": N(d_court(S_["occurredAt"])), "type": KIND_FR[S_["kind"]], "titre": esc(S_["text"])} for S_ in
                        [by_id("sources", i) for i in ("SRC-A-005", "SRC-A-006", "SRC-A-004", "SRC-A-003")]])
        bio = (panneau("Biologie", f'<div class="etaye-vide"><strong>Résultats non chargés</strong><span>Ne pas conclure à l\'absence de résultat : la section n\'a pas pu être lue.</span></div>')
               if mode == "partiel" else panneau("Biologie", NOTE("Section non ouverte avant la coupure : elle sera chargée au retour de la connexion. Ne pas conclure à l'absence de résultat.") if mode == "hors-ligne" else squelette(3)))
        body = b + f'<div class="etaye-cols etaye-cols--2"><div>{panneau("Chronologie", chron)}</div><div>{bio}</div></div>'
        return pro_shell("Patients", f'<div class="etaye-page">{entete("Dossier de Claire Martin", "", btn("Modifier", "secondaire", "modifier", disabled=mode != "partiel"))}{body}</div>', "nora", banner=banniere("R-A-001"))
    # ia: AI unavailable during consultation — manual notes continue (V-AI-DOWN)
    body = (bandeau("attention", "La génération de brouillons est indisponible", "Le service de génération ne répond pas (V-AI-DOWN). Vos notes manuelles et le dossier restent disponibles ; aucune donnée n'est perdue.", lien("Voir les tâches", href("S-40", "echec")), role="alert")
            + panneau("Notes de consultation", champ("Motif de la consultation", by_id("appointments", "APT-A-002")["reason"], "s37-motif", kind="textarea", rows=3, help_="Repris du motif du rendez-vous du 02/11/2026 14:00 · saisie manuelle disponible.") + f'<div class="etaye-ligne">{enregistrement("ok", "14:12")}{btn("Enregistrer", "principal")}</div>')
            + NOTE("CONS-A-002, Dr Sami Rahmani. Heure d'enregistrement : variante. Aucun brouillon de remplacement n'est affiché."))
    return pro_shell("Patients", f'<div class="etaye-page">{entete("Consultation", "Louis Bernard · commencée le 02/11/2026 14:10")}{body}</div>', "sami_a", banner=banniere("R-A-002"))


SCREENS = [
    {"id": "S-31", "name": "Équipe et invitations", "states": [
        {"slug": "equipe", "label": "Three PRA-A members with readable grouped capabilities, INV-TEAM-01 pending (expires 5 Nov 12:00), no clinical data", "html": s31()},
        {"slug": "vide", "label": "Empty team (template)", "html": s31("vide")},
        {"slug": "invitation-expiree", "label": "INV-TEAM-01 expired (variant after 5 Nov 12:00)", "html": s31("invitation-expiree")},
        {"slug": "invitation-revoquee", "label": "Revoked invitation (template)", "html": s31("invitation-revoquee")},
        {"slug": "suspendu", "label": "Suspended membership (variant) — account untouched", "html": s31("suspendu")},
        {"slug": "dernier-admin", "label": "Last-admin guard — Mila is the only TEAM_MANAGE holder", "html": s31("dernier-admin")},
        {"slug": "longs", "label": "Long e-mail address (variant, example.test) — actions not clipped", "html": s31("longs")},
        {"slug": "erreur", "label": "List failed to load — nothing changed", "html": s31("erreur")},
    ]},
    {"id": "I-01", "name": "Inviter un membre", "states": [
        {"slug": "ouvert", "label": "Dialog — e-mail, explicit grouped capabilities, expiry (72 h proposal), no clinical detail", "html": i01()},
        {"slug": "erreur", "label": "Invalid e-mail and no capability — summary and field errors", "html": i01("erreur")},
        {"slug": "existant", "label": "Pending invitation already exists — resend instead", "html": i01("existant")},
        {"slug": "combinaison", "label": "Invalid capability combination rejected server-side (template)", "html": i01("combinaison")},
        {"slug": "renvoyer", "label": "Resend — old link revoked", "html": i01("renvoyer")},
        {"slug": "envoye", "label": "Invitation recorded — M-01 queued, delivery status separate", "html": i01("envoye")},
    ]},
    {"id": "S-32", "name": "Droits d'un membre et désactivation", "states": [
        {"slug": "membre", "label": "MEM-SAMI-A — capabilities grouped by consequence with plain explanations; practice-only scope", "html": s32()},
        {"slug": "modifie", "label": "Unsaved change — PATIENT_PUBLISH removed, consequences panel", "html": s32("modifie")},
        {"slug": "conflit", "label": "Concurrent permission edit (template) — nothing overwritten", "html": s32("conflit")},
        {"slug": "soi-meme", "label": "Read-only self view — Mila, last administrator", "html": s32("soi-meme")},
        {"slug": "dependances", "label": "Dependencies — Nora's APT-A-001 and AVL-A-001 before deactivation", "html": s32("dependances")},
        {"slug": "desactive", "label": "Deactivated (variant) — sessions lose authority, account kept", "html": s32("desactive")},
        {"slug": "invitation", "label": "Pending invitation member view — Alex Rivière, capabilities not set in dataset", "html": s32("invitation")},
    ]},
    {"id": "I-02", "name": "Désactiver un accès", "states": [
        {"slug": "ouvert", "label": "Dialog — named member and practice, capabilities removed, reason required", "html": i02()},
        {"slug": "erreur", "label": "Reason missing — summary and field error", "html": i02("erreur")},
        {"slug": "dernier-admin", "label": "Blocked — final active administrator", "html": i02("dernier-admin")},
        {"slug": "dependances", "label": "Dependencies warning — appointment and availability stay in the agenda", "html": i02("dependances")},
        {"slug": "conflit", "label": "Membership changed since opening (template)", "html": i02("conflit")},
    ]},
    {"id": "S-33", "name": "Réglages du cabinet", "states": [
        {"slug": "cabinet", "label": "Initial — name, timezone Europe/Paris, booking policy, portal contact text, logo reference", "html": s33()},
        {"slug": "sale", "label": "Unsaved change", "html": s33("sale")},
        {"slug": "erreur", "label": "Validation error — contact text required", "html": s33("erreur")},
        {"slug": "enregistre", "label": "Saved", "html": s33("enregistre")},
        {"slug": "conflit", "label": "Concurrent edit (template)", "html": s33("conflit")},
        {"slug": "fuseau", "label": "Timezone impact preview (variant) — instants unchanged, display only", "html": s33("fuseau")},
    ]},
    {"id": "S-34", "name": "Historique d'audit", "states": [
        {"slug": "admin", "label": "ADMIN_AUDIT (Mila) — membership, invitation and access-denied events only; missing times shown as not provided", "html": s34()},
        {"slug": "clinique", "label": "CLINICAL_AUDIT (Nora) — R-A-001 history derived from dataset dates, versions and actors, no source text", "html": s34("clinique")},
        {"slug": "restreint", "label": "Restricted — clinical events need CLINICAL_AUDIT", "html": s34("restreint")},
        {"slug": "vide", "label": "No events for filters", "html": s34("vide")},
        {"slug": "export", "label": "Export unavailable (template)", "html": s34("export")},
        {"slug": "erreur-partielle", "label": "Partial service error — older events missing, no absence conclusion", "html": s34("erreur-partielle")},
    ]},
    {"id": "I-25", "name": "Filtres et détail d'un événement d'audit", "states": [
        {"slug": "ouvert", "label": "PUB-A-002 event detail — exact version, actor, replaced publication, source IDs only", "html": i25()},
        {"slug": "admin", "label": "Admin event detail — SC-ADMIN-CLINICAL 403, no record content", "html": i25("admin")},
        {"slug": "filtres", "label": "Filtered to publications", "html": i25("filtres")},
        {"slug": "aucun", "label": "No match for filters", "html": i25("aucun")},
    ]},
    {"id": "S-35", "name": "Profil et préférences", "states": [
        {"slug": "pro", "label": "Practitioner profile — display identity, theme, French-only notice, timezone display, security links", "html": s35()},
        {"slug": "admin", "label": "Administrator profile — Mila Dubois", "html": s35("admin")},
        {"slug": "patient", "label": "Patient profile — Claire Martin, vouvoiement, no relink", "html": s35("patient")},
        {"slug": "sale", "label": "Unsaved theme change", "html": s35("sale")},
        {"slug": "enregistre", "label": "Saved", "html": s35("enregistre")},
        {"slug": "erreur", "label": "Save failed — choice kept", "html": s35("erreur")},
        {"slug": "session", "label": "Session stale — nothing sent", "html": s35("session")},
    ]},
    {"id": "S-37", "name": "Services indisponibles et échec partiel", "states": [
        {"slug": "indisponible", "label": "503 — no action recorded, unsent input kept, reference without secrets", "html": s37()},
        {"slug": "hors-ligne", "label": "Offline — last loaded version, edits disabled", "html": s37("hors-ligne")},
        {"slug": "reconnexion", "label": "Reconnecting", "html": s37("reconnexion")},
        {"slug": "limite", "label": "429 — wait, nothing duplicated", "html": s37("limite")},
        {"slug": "delai", "label": "Timeout — outcome checked before any retry", "html": s37("delai")},
        {"slug": "partiel", "label": "Degraded partial content — lab section failed, identity and chronology kept, no absence conclusion", "html": s37("partiel")},
        {"slug": "ia", "label": "AI unavailable (V-AI-DOWN) — manual notes continue", "html": s37("ia")},
        {"slug": "maintenance", "label": "Demo reset / maintenance", "html": s37("maintenance")},
    ]},
]

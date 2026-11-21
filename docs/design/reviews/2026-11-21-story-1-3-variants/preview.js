/* Static review fixtures for S-05 and I-20. They have no server or persistence. */
'use strict';

const query = new URLSearchParams(location.search);
const state = query.get('etat') || 'index';
const theme = query.get('theme') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'sombre' : 'clair');
const assets = '../../../../design/offline-reference/site/';
document.documentElement.dataset.theme = theme === 'sombre' ? 'sombre' : 'clair';
// ADR-014 supplies the value used by the application token generator.
document.documentElement.style.setProperty('--etaye-revue-scrim', 'rgba(15, 16, 18, 0.48)');

const labels = {
  choix: 'Choix avec plusieurs contextes',
  long: 'Libellés longs',
  unique: 'Ouverture directe du seul contexte',
  aucun: 'Aucun contexte actif',
  chargement: 'Chargement initial',
  verification: 'Vérification de l’accès',
  echec: 'Échec et relance',
  retire: 'Accès retiré pendant le choix',
  praticien: 'Destination praticien',
  administration: 'Destination administrateur',
  patient: 'Destination patient',
  garde: 'Changement de cabinet avec un brouillon',
  enregistrement: 'Enregistrement en cours',
  erreur: 'Échec de l’enregistrement',
  invalide: 'Erreur de validation',
  destinationRetiree: 'Enregistrement confirmé, destination indisponible',
  sourceRetiree: 'Accès au contexte d’origine retiré',
};

const contexts = {
  praticien: ['Cabinet des Tilleuls', 'Praticien', 'etaye-logo'],
  administration: ['Cabinet du Canal', 'Administration', 'etaye-logo'],
  patient: ['Cabinet du Canal', 'Espace patient', 'etaye-logo-espace-patient'],
};

function link(next, selectedTheme = theme) {
  return '?etat=' + encodeURIComponent(next) + '&theme=' + encodeURIComponent(selectedTheme);
}

function clone(id) {
  return document.getElementById(id).content.cloneNode(true);
}

function render(template, title) {
  const surface = document.getElementById('surface');
  surface.replaceChildren(clone(template));
  surface.querySelector('h1').textContent = title;
  document.title = 'Étaye · ' + title;
  return surface;
}

function logo(container, name = 'etaye-logo') {
  for (const [mode, suffix] of [['c', ''], ['s', '-sombre']]) {
    const img = document.createElement('img');
    img.alt = name.endsWith('patient') ? 'Étaye — Espace patient' : 'Étaye';
    img.className = 'etaye-logo-' + mode;
    img.src = assets + 'logos/' + name + suffix + '.svg';
    container.append(img);
  }
}

function action(label, next, primary = false) {
  const a = document.createElement('a');
  a.className = 'etaye-bouton etaye-bouton--' + (primary ? 'principal' : 'secondaire');
  a.textContent = label;
  a.href = link(next);
  return a;
}

function paragraph(text, role) {
  const p = document.createElement('p');
  p.textContent = text;
  if (role) p.setAttribute('role', role);
  return p;
}

function notice(title, text) {
  const fragment = clone('notice');
  fragment.querySelector('.etaye-bandeau__titre').textContent = title;
  fragment.querySelector('p').textContent = text;
  return fragment;
}

function options(panel, keys, useLongCopy = false) {
  const list = document.createElement('ul');
  list.className = 'etaye-pliste';
  for (const key of keys) {
    const [practice, role] = contexts[key];
    const row = clone('option');
    const name = useLongCopy && key === 'praticien'
      ? 'Cabinet des Tilleuls — Maison de santé pluriprofessionnelle de Saint‑Martin‑des‑Rives'
      : practice;
    row.querySelector('strong').textContent = name;
    row.querySelector('small').textContent = useLongCopy && key === 'praticien'
      ? 'Praticien · agenda, dossiers, validation et publication de documents aux patients'
      : role;
    const button = row.querySelector('a');
    button.setAttribute('aria-label', 'Ouvrir · ' + role + ' · ' + name);
    button.href = link(key);
    list.append(row);
  }
  panel.append(list);
}

function selector(variant) {
  const shell = render('selection', 'Choisir un contexte de travail');
  logo(shell.querySelector('[data-logo]'));
  const content = shell.querySelector('[data-state]');
  content.className = 'etaye-revue-menu';
  const intro = shell.querySelector('[data-intro]');
  intro.textContent = 'Chaque cabinet a ses propres dossiers. Les recherches et brouillons ouverts restent dans leur contexte.';
  intro.hidden = false;

  switch (variant) {
    case 'unique':
      content.append(paragraph('Ouverture de votre espace…', 'status'));
      break;
    case 'aucun':
      content.append(paragraph('Aucun contexte actif. Si vous attendiez un accès, contactez le cabinet.'), action('Vérifier mes accès', 'chargement'));
      break;
    case 'chargement':
      content.append(paragraph('Chargement de vos accès…', 'status'));
      break;
    case 'verification':
      content.append(paragraph('Vérification de l’accès…', 'status'));
      break;
    case 'echec':
      content.append(notice('Impossible de charger vos accès', 'Le service est temporairement indisponible.'), action('Réessayer', 'choix'));
      break;
    case 'retire':
      content.append(notice('Ce contexte n’est plus disponible', 'Votre accès a changé. Choisissez un contexte encore disponible.'));
      options(content, ['praticien']);
      break;
    case 'sourceRetiree':
      content.append(notice('Votre accès a changé', 'Cet espace est fermé. Choisissez un contexte encore disponible.'));
      options(content, ['administration']);
      break;
    case 'destinationRetiree':
      content.append(notice('Modifications enregistrées', 'La destination choisie n’est plus disponible. Vous êtes resté dans votre contexte d’origine.'));
      content.append(action('Retourner à mon espace', 'praticien'), action('Choisir un autre contexte', 'retire'));
      break;
    default:
      options(content, Object.keys(contexts), variant === 'long');
  }
  shell.querySelector('h1').focus();
}

function destination(key, title) {
  const [practice, role, brand] = contexts[key];
  const shell = render('destination', title || (key === 'patient' ? 'Votre espace patient' : 'Votre espace de travail'));
  logo(shell.querySelector('[data-logo]'), brand);
  shell.querySelector('[data-practice]').textContent = practice;
  shell.querySelector('[data-role]').textContent = role;
  const switcher = shell.querySelector('[data-switch]');
  switcher.href = link('choix');
  switcher.setAttribute('aria-label', practice + ' ' + role + ' — Changer de contexte');
  shell.querySelector('h1').focus();
  return shell;
}

function guard(variant) {
  const shell = destination('praticien', 'Votre saisie');
  const content = shell.querySelector('[data-state]');
  const switcher = shell.querySelector('[data-switch]');
  const field = document.createElement('textarea');
  field.className = 'etaye-champ__saisie';
  field.id = 'saisie';
  field.rows = 3;
  field.value = 'Saisie de démonstration en cours.';
  const label = document.createElement('label');
  label.className = 'etaye-champ__libelle';
  label.htmlFor = field.id;
  label.textContent = 'Saisie de démonstration';
  content.append(label, field);
  shell.append(clone('guard'));

  const dialog = shell.querySelector('dialog');
  const heading = dialog.querySelector('#dlg-t');
  const message = dialog.querySelector('[data-result]');
  const save = dialog.querySelector('[data-save]');
  const pending = variant === 'enregistrement';
  if (pending) {
    message.append(paragraph('Enregistrement en cours…', 'status'));
    for (const button of dialog.querySelectorAll('button')) button.disabled = true;
    save.textContent = 'Enregistrement…';
    dialog.setAttribute('closedby', 'none');
    dialog.addEventListener('cancel', event => event.preventDefault());
  }
  if (variant === 'erreur') {
    message.append(notice('Enregistrement impossible', 'Votre saisie est conservée. Vous pouvez réessayer ou rester sur cette page.'));
  }
  if (variant === 'invalide') {
    message.append(notice('Saisie à vérifier', 'Restez sur cette page pour corriger le champ signalé.'));
    field.setAttribute('aria-invalid', 'true');
    field.setAttribute('aria-describedby', 'saisie-erreur');
    const error = paragraph('Vérifiez le contenu de ce champ.');
    error.id = 'saisie-erreur';
    content.append(error);
  }
  dialog.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      event.preventDefault();
      if (!pending) dialog.close();
    }
    if (event.key !== 'Tab') return;
    const buttons = dialog.querySelectorAll('button:not(:disabled)');
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (!first) {
      event.preventDefault();
    } else if (event.shiftKey && [heading, first].includes(event.target)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && event.target === last) {
      event.preventDefault();
      first.focus();
    }
  });
  dialog.querySelector('[aria-label="Fermer"]').addEventListener('click', () => dialog.close());
  dialog.querySelector('[data-stay]').addEventListener('click', () => dialog.close());
  dialog.querySelector('[data-discard]').addEventListener('click', () => location.href = link('administration'));
  save.addEventListener('click', () => location.href = link(variant === 'invalide' ? 'invalide' : 'enregistrement'));
  dialog.addEventListener('close', () => switcher.focus());
  switcher.addEventListener('click', event => {
    event.preventDefault();
    dialog.showModal();
    heading.focus();
  });
  dialog.showModal();
  heading.focus();
}

function index() {
  const shell = render('selection', 'Revue · Changement de contexte');
  logo(shell.querySelector('[data-logo]'));
  const content = shell.querySelector('[data-state]');
  content.className = 'etaye-revue-menu';
  const themes = document.createElement('div');
  themes.className = 'etaye-revue-actions';
  for (const t of ['clair', 'sombre']) {
    const themeLink = action(t === 'clair' ? 'Thème clair' : 'Thème sombre', 'index');
    themeLink.href = link('index', t);
    themes.append(themeLink);
  }
  content.append(themes);
  const groups = [
    ['S-05 · Sélection et destination', Object.keys(labels).slice(0, 11)],
    ['I-20 · Modifications non enregistrées', Object.keys(labels).slice(11)],
  ];
  for (const [title, keys] of groups) {
    const section = document.createElement('section');
    const heading = document.createElement('h2');
    heading.className = 'ui-section';
    heading.textContent = title;
    const list = document.createElement('ul');
    for (const key of keys) {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.className = 'etaye-lien';
      a.href = link(key);
      a.textContent = labels[key];
      li.append(a);
      list.append(li);
    }
    section.append(heading, list);
    content.append(section);
  }
}

if (Object.hasOwn(contexts, state)) destination(state);
else if (['garde', 'enregistrement', 'erreur', 'invalide'].includes(state)) guard(state);
else if (Object.hasOwn(labels, state)) selector(state);
else index();

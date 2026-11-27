// Static documentary specimen. All transitions use local examples.
const query = new URLSearchParams(location.search);
const theme = query.get('theme') === 'sombre' ? 'sombre' : 'clair';
document.documentElement.dataset.theme = theme;

const defaultRows = [
  ['12/10/2026 · 08:00', 'Créatinine', '104', 'µmol/L', '60–110 µmol/L · fourni par le laboratoire', 'A003'],
  ['12/10/2026 · 08:00', 'Glucose', '5,2', 'mmol/L', '4,1–6,1 mmol/L · fourni par le laboratoire', 'A003'],
  ['14/09/2026 · 08:00', 'Créatinine', '96', 'µmol/L', '60–110 µmol/L · fourni par le laboratoire', 'A002'],
];
const proofBefore = ['20/10/2026', 'Analyse A', '7,40', 'unité A', 'Intervalle non fourni', 'P1'];
const proofAfter = ['20/10/2026', 'Analyse A', '7,04', 'unité A', 'Intervalle non fourni', 'P2'];
const sources = {
  A002: ['SRC-A-002 · v1 · LAB-A-001', 'Survenu le 14/09/2026 à 08:00 · enregistré le 15/09/2026 à 10:00 · Dr Nora Lefebvre', 'Créatinine 96 µmol/L, intervalle fourni 60–110 µmol/L.'],
  A003: ['SRC-A-003 · v1 · DOC-A-001, page 1', 'Prélevé le 12/10/2026 à 08:00 · enregistré le 13/10/2026 à 10:00 · Dr Sami Rahmani', 'Créatinine 104 µmol/L (intervalle fourni 60–110 µmol/L) et glucose 5,2 mmol/L (intervalle fourni 4,1–6,1 mmol/L). Extrait synthétique proposé pour la démonstration.'],
  P1: ['Exemple synthétique · source originale', 'Enregistrée le 21/10/2026 à 10:00 · Dr Nora Lefebvre', 'Analyse A, 7,40 unité A. Intervalle non fourni.'],
  P2: ['Exemple synthétique · révision 2', 'Enregistrée le 21/10/2026 à 10:15 · Dr Nora Lefebvre', 'Analyse A, 7,04 unité A. Intervalle non fourni. Motif consigné : chiffres inversés lors de la transcription.'],
  V: ['Source fictive · exemples de valeurs', 'Enregistrée le 23/10/2026 à 10:00 · Dr Nora Lefebvre', 'Analyse B : < 0,060 unité B (aucun intervalle fourni). Analyse C : Non détecté dans le prélèvement. Analyse D : ≥ 7,00 unité D et référence ≤ 8 unité D à jeun. Analyse E : En attente de confirmation.'],
  C: ['Source fictive · exemples de contexte', 'Enregistrée le 23/10/2026 à 10:00 · Dr Nora Lefebvre', 'Résultats de l’analyse A : 9,02 unité A à jeun (2–8 unité A ; mention de la source : au-dessus de l’intervalle fourni) ; 7,10 unité B à jeun (2–8 unité B ; transcription à vérifier) ; 7,20 unité A (2–8 unité A ; contexte non renseigné).'],
  G: ['Source fictive · analyse A', 'Enregistrée le 23/10/2026 à 10:00 · Dr Nora Lefebvre', 'Analyse A (méthode A) : 6,80 unité A le 14/10 à 08:20 puis 7,04 unité A le 22/10 à 08:15, à jeun le matin. Intervalle fourni sur les deux résultats : 2–8 unité A.'],
  X: ['Source fictive · contenu long', 'Enregistrée le 23/10/2026 à 10:00 · Dr Nora Lefebvre', 'Exemple de repli. Résultat transcrit : ≤ 999999999999,123456 ; le contexte fourni est lisible dans le tableau.'],
};

const cases = {
  claire: { title: 'Résultats du dossier', rows: defaultRows },
  valeurs: { title: 'Comparateur, texte et intervalle absent', demo: true, rows: [
    ['22/10/2026', 'Analyse B', '< 0,060', 'unité B', 'Intervalle non fourni', 'V'],
    ['21/10/2026', 'Analyse C', 'Non détecté dans le prélèvement', 'Unité non fournie', 'Intervalle non fourni', 'V'],
    ['Date inconnue', 'Analyse D', '≥ 7,00', 'unité D', '≤ 8 unité D · à jeun', 'V', '', '20/10/2026'],
    ['Dates non fournies', 'Analyse E', 'En attente de confirmation', 'Unité non fournie', 'Intervalle non fourni', 'V'],
  ] },
  contexte: { title: 'Unités et contextes différents', demo: true, rows: [
    ['22/10/2026', 'Analyse A', '9,02', 'unité A', '2–8 unité A · à jeun', 'C', 'Au-dessus de l’intervalle fourni · mention de la source'],
    ['20/10/2026', 'Analyse A', '7,10', 'unité B', '2–8 unité B · à jeun', 'C', 'Saisie manuelle · à vérifier contre la source'],
    ['18/10/2026', 'Analyse A', '7,20', 'unité A', '2–8 unité A · contexte non renseigné', 'C'],
  ], message: 'Unités ou contextes différents. Les résultats sont consultables individuellement dans le tableau.' },
  tendance: { title: 'Points comparables', demo: true, chart: true, rows: [
    ['22/10/2026 · 08:15', 'Analyse A', '7,04', 'unité A', '2–8 unité A · laboratoire fictif, méthode A, à jeun, matin', 'G'],
    ['14/10/2026 · 08:20', 'Analyse A', '6,80', 'unité A', '2–8 unité A · laboratoire fictif, méthode A, à jeun, matin', 'G'],
  ] },
  correction: { title: 'Historique du résultat', demo: true, rows: [proofAfter], history: 'open' },
  'history-loading': { title: 'Chargement de l’historique', demo: true, rows: [proofAfter], history: 'loading' },
  'history-error': { title: 'Échec de l’historique', demo: true, rows: [proofAfter], history: 'error' },
  source: { title: 'Source datée ouverte', rows: defaultRows, source: 'open' },
  'source-loading': { title: 'Chargement de la source', rows: defaultRows, source: 'loading' },
  'source-error': { title: 'Source temporairement indisponible', rows: defaultRows, source: 'error' },
  vide: { title: 'Dossier sans résultat', empty: 'Aucun résultat de biologie enregistré pour Louis Bernard.', louis: true },
  filtre: { title: 'Filtre sans résultat', empty: 'Aucun résultat pour cette analyse et cette période.', filtered: true },
  'filtre-resultat': { title: 'Filtre avec résultats', rows: [defaultRows[0], defaultRows[2]], filtered: true },
  chargement: { title: 'Chargement des résultats', loading: true },
  erreur: { title: 'Échec du panneau Biologie', error: 'Les résultats de biologie sont temporairement indisponibles. Le dossier reste accessible.' },
  pagination: { title: 'Échec de la page suivante', rows: defaultRows, nextError: true },
  attente: { title: 'Limite temporaire de requêtes', error: 'Les demandes sont temporairement limitées. Réessayez dans 30 secondes.', rate: true },
  'avant-tout': { title: 'Vue avant le premier résultat', past: '01/09/2026 à 09:00', empty: 'Aucun résultat enregistré à cette date.' },
  'avant-correction': { title: 'Vue avant la correction', past: '21/10/2026 à 10:14', rows: [proofBefore], demo: true },
  'apres-correction': { title: 'Vue après la correction', past: '21/10/2026 à 10:15', rows: [proofAfter], history: 'open', demo: true },
  'date-invalide': { title: 'Date future refusée', rows: defaultRows, dateError: true },
  long: { title: 'Contenu long et focus clavier', demo: true, rows: [
    ['22/10/2026', 'Analyse ' + 'ExtrêmementLong'.repeat(6), '≤ 999999999999,123456', 'unité' + 'A'.repeat(34), 'Intervalle et contexte tels que fournis par la source. '.repeat(9), 'X'],
  ], source: 'open', focus: true },
};
const key = Object.hasOwn(cases, query.get('etat')) ? query.get('etat') : 'claire';
const example = cases[key];
document.title = example.title + ' · Étaye';

const button = (label, attrs = '') => `<button type="button" class="etaye-bouton etaye-bouton--secondaire" ${attrs}>${label}</button>`;
const notice = (title, text, actions = '') => `<div class="lab-details etaye-pile"><p><strong>${title}</strong></p><p>${text}</p>${actions}</div>`;
const stateLink = (to, label) => `<a class="etaye-bouton etaye-bouton--discret" href="?etat=${to}&amp;theme=${theme}">${label}</a>`;
const skeleton = (label) => `<div class="etaye-pile" role="status"><p>${label}</p><div class="etaye-squelette" aria-hidden="true"><span></span><span></span><span></span></div></div>`;
const disclosure = (id, title, content, expanded) => `<div class="lab-details" id="rep-${id}">
  <button type="button" class="etaye-bouton etaye-bouton--discret" data-disclosure aria-controls="${id}" aria-expanded="${expanded}">${title}</button>
  <div id="${id}" role="region" aria-label="${title}" ${expanded ? '' : 'hidden'}>${content}</div>
</div>`;

function renderResults(rows) {
  const seen = [...new Set(rows.map(row => row[5]))];
  if (example.history === 'open') seen.push('P1');
  const headers = ['Prélèvement', 'Analyse', 'Résultat', 'Unité', 'Intervalle fourni', 'Source'];
  const body = rows.map(([date, analyte, value, unit, interval, source, note, resultDate], index) => {
    const reference = seen.indexOf(source) + 1;
    const textValue = /^(Non détecté|En attente)/.test(value);
    return `<tr>
      <td data-l="Prélèvement"><span class="etaye-num">${date}</span>${resultDate ? `<div>Résultat du ${resultDate}</div>` : ''}</td>
      <td data-l="Analyse">${analyte}</td>
      <td data-l="Résultat" class="${textValue ? '' : value.length > 19 ? 'etaye-col-num lab-long-value' : 'etaye-col-num'}">${value.replace('<', '&lt;')}</td>
      <td data-l="Unité">${unit}</td>
      <td data-l="Intervalle fourni">${interval}${note ? `<div><strong>${note}</strong></div>` : ''}</td>
      <td data-l="Source"><a id="mark-${index}" class="etaye-repere" href="#rep-source-${source}" data-source="${source}" aria-label="Source ${reference} · ${analyte} · ${date}">${reference}</a></td>
    </tr>`;
  }).join('');
  return `<section class="etaye-pile" aria-labelledby="results-title"><h2 class="ui-section" id="results-title">Résultats de biologie</h2>
    <table class="etaye-tableau etaye-tableau--empile" aria-labelledby="results-title"><thead><tr>${headers.map(header => `<th scope="col">${header}</th>`).join('')}</tr></thead><tbody>${body}</tbody></table>
    ${example.nextError ? notice('Suite de la liste indisponible', 'Les résultats déjà affichés restent consultables.', stateLink('claire', 'Réessayer d’afficher la suite')) : ''}
  </section>
  ${example.message ? `<p>${example.message}</p>` : ''}
  ${example.chart ? renderChart() : ''}
  ${example.history ? renderHistory() : ''}
  <section class="etaye-pile" aria-labelledby="sources-title"><h2 class="ui-section" id="sources-title">Sources datées</h2>
    ${seen.map((source, index) => {
      const [label, meta, excerpt] = sources[source];
      const first = rows.findIndex(row => row[5] === source);
      const isOpen = index === 0 && Boolean(example.source);
      const sourceBody = index === 0 && example.source === 'loading' ? skeleton('Chargement de la source…')
        : index === 0 && example.source === 'error'
          ? notice('Source temporairement indisponible', 'Le résultat enregistré reste affiché.', stateLink('source', 'Réessayer la lecture de la source'))
          : `<p>${meta}</p><p>${excerpt}</p>`;
      return disclosure('source-' + source, `${index + 1} · ${label}`,
        sourceBody + `<a data-return-source="${source}" href="${first < 0 ? '#history-P1' : '#mark-' + first}">Revenir au résultat</a>`, isOpen);
    }).join('')}
  </section>`;
}

function renderHistory() {
  const details = example.history === 'loading' ? skeleton('Chargement de l’historique…')
    : example.history === 'error' ? notice('Historique temporairement indisponible', 'La version affichée reste consultable.', stateLink('correction', 'Réessayer l’historique'))
    : `<ol class="lab-history">
      <li><strong>Version 1 · 7,40 unité A</strong><p>Enregistrée le 21/10/2026 à 10:00 · Dr Nora Lefebvre</p><p><a id="history-P1" data-source="P1" href="#rep-source-P1">Source de la version 1</a></p></li>
      <li><strong>Version 2 · 7,04 unité A · version affichée</strong><p>Enregistrée le 21/10/2026 à 10:15 · Dr Nora Lefebvre</p><p>Motif consigné : chiffres inversés lors de la transcription.</p><p><a id="history-P2" data-source="P2" href="#rep-source-P2">Source de la version 2</a></p></li>
    </ol>`;
  return `<section aria-label="Versions du résultat">${disclosure('history', 'Historique du résultat · Analyse A', details, true)}</section>`;
}

function renderChart() {
  return `<section class="etaye-panneau" aria-labelledby="trend-title">
    <div class="etaye-panneau__tete"><h2 id="trend-title">Résultats comparables · Analyse A</h2></div>
    <div class="etaye-panneau__corps etaye-pile">
      <p>Même unité, même intervalle, méthode A, prélèvements à jeun le matin. Chaque point correspond à un résultat daté.</p>
      <svg class="lab-chart" viewBox="0 0 320 148" role="img" aria-label="Analyse A : 6,80 unité A le 14 octobre 2026 et 7,04 unité A le 22 octobre 2026.">
        <line x1="42" x2="42" y1="20" y2="115" /><line x1="42" x2="305" y1="115" y2="115" />
        <text x="1" y="42">7,1</text><text x="1" y="102">6,8</text>
        <circle cx="98" cy="98" r="5" /><circle cx="254" cy="50" r="5" />
        <text x="98" y="134" text-anchor="middle">14/10</text><text x="254" y="134" text-anchor="middle">22/10</text>
      </svg>
    </div>
  </section>`;
}

const filter = `<form class="etaye-filtres" aria-label="Filtrer les résultats de biologie" id="lab-filters">
  <div class="etaye-champ"><label class="etaye-champ__libelle" for="analyte">Analyse</label>
    <select id="analyte" class="etaye-champ__saisie">
      <option>Toutes</option><option ${key === 'filtre-resultat' ? 'selected' : ''}>Créatinine</option><option>Glucose</option><option ${key === 'filtre' ? 'selected' : ''}>Analyse A</option>
    </select>
  </div>
  <div class="etaye-champ"><label class="etaye-champ__libelle" for="period">Période</label>
    <select id="period" class="etaye-champ__saisie">
      <option>Tout le dossier</option><option ${key === 'filtre' ? 'selected' : ''}>3 derniers mois</option><option>12 derniers mois</option>
    </select>
  </div>
  <div class="spec-actions"><button class="etaye-bouton etaye-bouton--secondaire" type="submit">Appliquer les filtres</button>
    ${key === 'filtre-resultat' ? stateLink('claire', 'Réinitialiser les filtres') : ''}</div>
</form>`;
const past = example.past ? `<div class="lab-details etaye-pile"><p><strong>Dossier au ${example.past} · heure de Paris</strong></p>
  <p>Lecture seule · identité actuelle affichée.</p>${stateLink('claire', 'Revenir au dossier actuel')}</div>` : '';
const demo = example.demo ? '<p class="etaye-2">Exemple synthétique de revue.</p>' : '';
const result = example.rows ? renderResults(example.rows)
  : example.loading ? skeleton('Chargement des résultats de biologie…')
  : example.error ? notice('Résultats non chargés', example.error, example.rate
    ? button('Réessayer', 'disabled') : stateLink('claire', 'Réessayer'))
  : `<div class="etaye-vide"><strong>${example.title}</strong><p>${example.empty}</p>${example.filtered ? stateLink('claire', 'Effacer les filtres') : ''}</div>`;
document.getElementById('lab-content').innerHTML = past + demo + filter + result;
if (example.louis) {
  document.querySelector('.etaye-banniere__nom').textContent = 'BERNARD Louis';
  document.querySelector('.etaye-banniere__traits').textContent = 'Naissance 09/07/1991 · 35 ans';
  document.querySelector('.etaye-allergies').textContent = 'Allergies · Non renseigné';
  const trail = document.querySelector('.etaye-entete__fil');
  trail.lastChild.textContent = ' › BERNARD Louis';
}

const sourceTriggers = new Map();
function expand(control, open) {
  control.setAttribute('aria-expanded', String(open));
  document.getElementById(control.getAttribute('aria-controls')).hidden = !open;
}

document.querySelectorAll('[data-disclosure]').forEach(control => {
  control.addEventListener('click', () => {
    const open = control.getAttribute('aria-expanded') !== 'true';
    expand(control, open);
    if (control.dataset.closed) control.textContent = open ? control.dataset.open : control.dataset.closed;
  });
});
document.querySelectorAll('[data-source]').forEach(marker => {
  marker.addEventListener('click', event => {
    event.preventDefault();
    const control = document.querySelector('#rep-source-' + marker.dataset.source + ' button');
    sourceTriggers.set(marker.dataset.source, marker);
    expand(control, true);
    control.focus();
  });
});
document.querySelectorAll('[data-return-source]').forEach(link => {
  link.addEventListener('click', event => {
    event.preventDefault();
    (sourceTriggers.get(link.dataset.returnSource) ?? document.getElementById(link.hash.slice(1))).focus();
  });
});

function go(to) {
  location.search = new URLSearchParams({ etat: to, theme }).toString();
}
document.getElementById('lab-filters').addEventListener('submit', event => {
  event.preventDefault();
  const analyte = document.getElementById('analyte').value;
  go(analyte === 'Toutes' ? 'claire' : analyte === 'Créatinine' ? 'filtre-resultat' : 'filtre');
});
document.querySelector('#asof-region form').addEventListener('submit', event => {
  event.preventDefault();
  const year = document.getElementById('as-a').value;
  go(year === '2099' ? 'date-invalide' : document.getElementById('as-mi').value === '15' ? 'apres-correction' : 'avant-correction');
});
if (example.dateError) {
  const control = document.querySelector('[aria-controls="asof-region"]');
  control.click();
  const year = document.getElementById('as-a');
  year.value = '2099';
  year.setAttribute('aria-invalid', 'true');
  year.setAttribute('aria-describedby', 'as-error');
  const error = document.createElement('p');
  error.id = 'as-error';
  error.setAttribute('role', 'alert');
  error.textContent = 'Date future. Choisissez une date passée ou revenez au dossier actuel.';
  document.querySelector('#asof-region form').prepend(error);
}

const menuButton = document.querySelector('[data-menu]');
const navigation = document.querySelector('.etaye-nav');
function menu(open) {
  navigation.toggleAttribute('data-open', open);
  if (open) navigation.dataset.open = 'true';
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.setAttribute('aria-label', open ? 'Fermer le menu' : 'Ouvrir le menu');
  (open ? navigation.querySelector('a') : menuButton).focus();
}
menuButton.addEventListener('click', () => menu(!navigation.hasAttribute('data-open')));
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && navigation.hasAttribute('data-open')) menu(false);
});
matchMedia('(min-width: 761px)').addEventListener('change', event => {
  if (event.matches && navigation.hasAttribute('data-open')) {
    navigation.removeAttribute('data-open');
    menuButton.setAttribute('aria-expanded', 'false');
    menuButton.setAttribute('aria-label', 'Ouvrir le menu');
  }
});
if (example.focus) document.getElementById('mark-0').focus({ preventScroll: true });

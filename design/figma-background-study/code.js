/* Native Figma development plugin: builds the paper-and-blueprint background study frames in a NEW section of
   a dedicated page. Design tooling only: no account token, no network, no application code, and no rewrite of any
   existing node. Everything it creates sits inside one section that is removed again if the run fails. */
const TARGET = {
  fileKey: 'vfh4BSnbRAbaDE1q0CyssL',
  pageName: 'Étaye — Fond papier (étude)',
  protectedPageIds: ['2:74', '2:75', '15:2'], // foundations, components, imported HTML states
};
const FORMAT = 'etaye-background-study-v1';
const COLLECTIONS = {light: 'Couleurs · Clair', dark: 'Couleurs · Sombre'};
const LOGOS = {light: {id: '4:13', name: 'Logo/Clair'}, dark: {id: '4:33', name: 'Logo/Sombre'}};
// Adopted tone (2026-11-18): the dark paper starts from fond, the light paper from papier.
const BASE = {light: 'surface/papier', dark: 'surface/fond'};
const LAYER_VARIABLES = ['surface/papier', 'texte/encre', 'filet/filet', 'accent/accent-fond'];
const FILL_PREFERENCE = ['surface/', 'accent/accent-fond', 'accent/sur-accent'];
const TEXT_PREFERENCE = ['texte/', 'accent/accent'];
const STROKE_PREFERENCE = ['filet/'];
const NODE_TYPES = ['frame', 'text', 'instance'];
const PNG_SIGNATURE = [137, 80, 78, 71, 13, 10, 26, 10];

figma.showUI(__html__, {width: 460, height: 560});
let busy = false, cancelled = false;
const tell = (message, done = false, evidence = null) => figma.ui.postMessage({message, done, evidence});
const fail = message => { throw new Error(message); };
const normalized = value => String(value).toLowerCase().replace(/[\s_-]/g, '');
const finite = value => typeof value === 'number' && Number.isFinite(value) && Math.abs(value) < 200000;
const weightOf = style => {
  const s = normalized(style).replace(/italic/g, '');
  const names = {thin: 100, extralight: 200, ultralight: 200, light: 300, regular: 400, normal: 400, book: 400, medium: 500, semibold: 600, demibold: 600, bold: 700, extrabold: 800, ultrabold: 800, black: 900, heavy: 900};
  return names[s || 'regular'];
};
const fontKey = node => [node.family, node.weight, Boolean(node.italic)].join('|');
function walk(node, visit) { visit(node); for (const child of node.children || []) walk(child, visit); }

function validate(batch) {
  if (!batch || batch.format !== FORMAT || batch.fileKey !== TARGET.fileKey) fail('Lot inconnu ou destiné à un autre fichier. Aucun calque créé.');
  if (!/^[a-f0-9]{64}$/.test(batch.sourceSha256 || '')) fail('Empreinte source manquante.');
  if (!Array.isArray(batch.frames) || !batch.frames.length || batch.frames.length > 12) fail('Nombre d’écrans invalide.');
  for (const theme of ['light', 'dark']) {
    const bytes = typeof batch.tiles?.[theme] === 'string' ? figma.base64Decode(batch.tiles[theme]) : null;
    if (!bytes || bytes.length < 100 || PNG_SIGNATURE.some((value, index) => bytes[index] !== value)) fail('Tuile de grain « ' + theme + ' » invalide (PNG attendu).');
    const size = (bytes[16] << 24 | bytes[17] << 16 | bytes[18] << 8 | bytes[19]) + 'x' + (bytes[20] << 24 | bytes[21] << 16 | bytes[22] << 8 | bytes[23]);
    if (size !== '256x256') fail('Tuile de grain « ' + theme + ' » : 256×256 px attendus, ' + size + ' reçus.');
  }
  const k = batch.constants || {};
  for (const name of ['grainOpacity', 'calmOpacity', 'tickLight', 'tickDark', 'ruler', 'band', 'markBox', 'markInset'])
    if (!finite(k[name]) || k[name] < 0) fail('Constante « ' + name + ' » invalide.');
  if (k.grainOpacity > 1 || k.calmOpacity > 1 || k.tickLight > 1 || k.tickDark > 1) fail('Opacité hors de [0, 1].');
  const ids = new Set();
  for (const frame of batch.frames) {
    if (typeof frame.id !== 'string' || !frame.id || ids.has(frame.id) || !['light', 'dark'].includes(frame.theme)) fail('Écran invalide ou en double.');
    ids.add(frame.id);
    if (frame.base !== BASE[frame.theme]) fail('Fond « ' + frame.base + ' » refusé pour le thème ' + frame.theme + ' (ton adopté : ' + BASE[frame.theme] + ').');
    for (const value of [frame.width, frame.height, frame.x, frame.y]) if (!finite(value)) fail('Géométrie d’écran invalide.');
    if (frame.width < 1 || frame.height < 1 || frame.width > 4000 || frame.height > 8000) fail('Dimensions d’écran hors limites.');
    const p = frame.plate || {};
    for (const name of ['x', 'y', 'w', 'h', 'inset', 'feather']) if (!finite(p[name]) || p[name] < 0) fail('Plaque calme invalide.');
    if (!frame.scene?.node) fail('Contenu d’écran manquant.');
    walk(frame.scene.node, node => {
      if (!NODE_TYPES.includes(node.type)) fail('Type de calque refusé : ' + node.type + '.');
      for (const name of ['x', 'y', 'w', 'h']) if (!finite(node[name])) fail('Géométrie de calque invalide.');
      if (node.type === 'text' && (typeof node.text !== 'string' || typeof node.family !== 'string' || !finite(node.size))) fail('Texte invalide.');
      if (node.type === 'instance' && !Object.values(LOGOS).some(logo => logo.name === node.component)) fail('Instance refusée : ' + node.component + '.');
    });
  }
}

function hex(color) {
  return '#' + [color.r, color.g, color.b].map(value => Math.round(value * 255).toString(16).padStart(2, '0')).join('');
}

async function preflight(batch) {
  if (figma.fileKey !== TARGET.fileKey) fail('Mauvais fichier Figma ou clé indisponible. Ouvrez le fichier Étaye original avec le manifeste fourni (enablePrivatePluginApi). Aucun calque créé.');
  const page = figma.currentPage;
  if (TARGET.protectedPageIds.includes(page.id) || page.name !== TARGET.pageName) fail('Ouvrez la page dédiée « ' + TARGET.pageName + ' » (créée vide à la main ; jamais une page protégée). Aucun calque créé.');
  const duplicate = page.children.find(node => node.getPluginData('etayeBackgroundBatch') === batch.sourceSha256);
  if (duplicate) fail('Ce lot existe déjà sur cette page : ' + duplicate.name + '. Rien remplacé.');

  // Fonts: exact family, weight and style; no silent substitution.
  const available = await figma.listAvailableFontsAsync();
  const needs = new Map();
  for (const frame of batch.frames) walk(frame.scene.node, node => { if (node.type === 'text') needs.set(fontKey(node), node); });
  const fonts = new Map(), missingFonts = [];
  for (const [key, node] of needs) {
    const match = available.find(f => normalized(f.fontName.family) === normalized(node.family) && weightOf(f.fontName.style) === (node.weight || 400) && /italic/i.test(f.fontName.style) === Boolean(node.italic));
    if (match) fonts.set(key, match.fontName); else missingFonts.push(node.family + ' · ' + node.weight + (node.italic ? ' Italic' : ''));
  }
  if (missingFonts.length) fail('Polices à installer ou à rendre disponibles avant import :\n' + missingFonts.join('\n') + '\nAucun calque créé. Aucune substitution silencieuse.');
  await Promise.all([...fonts.values()].map(font => figma.loadFontAsync(font)));

  // Variables: existing local color variables only; a missing layer variable stops the run.
  const themes = [...new Set(batch.frames.map(frame => frame.theme))];
  const collections = await figma.variables.getLocalVariableCollectionsAsync();
  const variables = {}, missing = [];
  for (const theme of themes) {
    const matches = collections.filter(collection => collection.name === COLLECTIONS[theme]);
    if (matches.length !== 1) { missing.push('collection « ' + COLLECTIONS[theme] + ' » (' + matches.length + ' trouvée(s))'); continue; }
    const map = new Map();
    for (const id of matches[0].variableIds) {
      const variable = await figma.variables.getVariableByIdAsync(id);
      if (variable && variable.resolvedType === 'COLOR') map.set(variable.name, variable);
    }
    variables[theme] = map;
    for (const name of [...LAYER_VARIABLES, BASE[theme]]) if (!map.has(name)) missing.push(COLLECTIONS[theme] + ' / ' + name);
  }
  if (missing.length) fail('Variables introuvables (aucune couleur n’est inventée) :\n' + [...new Set(missing)].join('\n') + '\nAucun calque créé.');

  // The original logo components are reused as instances, never redrawn.
  const logos = {};
  for (const theme of themes) {
    const node = await figma.getNodeByIdAsync(LOGOS[theme].id);
    if (!node || node.type !== 'COMPONENT' || node.name !== LOGOS[theme].name) fail('Composant logo « ' + LOGOS[theme].name + ' » (' + LOGOS[theme].id + ') introuvable ou renommé. Aucun calque créé.');
    logos[theme] = node;
  }
  return {page, fonts, variables, logos};
}

function pickVariable(batch, theme, variables, color, preference) {
  const candidates = (batch.tokens?.[theme]?.[hex(color)] || []).filter(name => variables.get(name));
  const preferred = candidates.find(name => preference.some(prefix => name.startsWith(prefix)));
  if (preferred) return {name: preferred};
  if (candidates.length === 1) return {name: candidates[0]};
  return {name: null, ambiguous: candidates.length > 1};
}

async function buildRun(batch, context) {
  const {page, fonts, variables, logos} = context;
  const created = [], stats = {bound: 0, raw: 0, ambiguous: 0}, used = new Set(), evidenceFrames = [];
  const section = figma.createSection(); created.push(section.id);
  try {
    section.name = 'Étaye · fond papier + blueprint · étude · ' + batch.frames.length + ' écrans';
    section.x = Math.max(0, ...page.children.filter(node => node.id !== section.id).map(node => node.x + node.width)) + 160; section.y = 80;
    section.setPluginData('etayeBackgroundBatch', batch.sourceSha256);
    const add = (node, parent) => { created.push(node.id); parent.appendChild(node); return node; };
    const imageHash = {};
    for (const theme of ['light', 'dark']) imageHash[theme] = figma.createImage(figma.base64Decode(batch.tiles[theme])).hash;

    const solid = (theme, name, opacity = 1) => {
      used.add(theme + ':' + name);
      return figma.variables.setBoundVariableForPaint({type: 'SOLID', color: {r: 0, g: 0, b: 0}, opacity}, 'color', variables[theme].get(name));
    };
    const contentPaint = (theme, color, preference) => {
      if (!color || color.a === 0) return null;
      const choice = pickVariable(batch, theme, variables[theme], color, preference);
      if (choice.name) { stats.bound++; return solid(theme, choice.name, color.a ?? 1); }
      stats[choice.ambiguous ? 'ambiguous' : 'raw']++;
      return {type: 'SOLID', color: {r: color.r, g: color.g, b: color.b}, opacity: color.a ?? 1};
    };
    const rect = (parent, name, x, y, w, h, fills) => {
      const node = add(figma.createRectangle(), parent); node.name = name; node.resize(w, h); node.x = x; node.y = y; node.fills = fills; return node;
    };

    function buildContent(item, parent, theme) {
      let node;
      if (item.type === 'text') {
        node = add(figma.createText(), parent); node.fontName = fonts.get(fontKey(item)); node.fontSize = item.size; node.characters = item.text;
        node.textAutoResize = 'HEIGHT'; node.resize(Math.max(1, item.w + 1), Math.max(1, item.h));
        node.lineHeight = {unit: 'PIXELS', value: item.lineHeight || item.size * 1.2};
        const paint = contentPaint(theme, item.color, TEXT_PREFERENCE); node.fills = paint ? [paint] : [];
      } else if (item.type === 'instance') {
        node = logos[theme].createInstance(); add(node, parent); node.resize(Math.max(1, item.w), Math.max(1, item.h));
      } else {
        node = add(figma.createFrame(), parent); node.resize(Math.max(1, item.w), Math.max(1, item.h)); node.clipsContent = Boolean(item.clip);
        const paint = contentPaint(theme, item.fill, FILL_PREFERENCE); node.fills = paint ? [paint] : [];
        node.cornerRadius = Math.min(item.radius || 0, Math.min(item.w, item.h) / 2);
        const edges = item.borders || [], shown = edges.filter(edge => edge.width > 0 && edge.color?.a);
        const same = (a, b) => a.r === b.r && a.g === b.g && a.b === b.b && a.a === b.a;
        if (edges.length === 4 && shown.length === 4 && shown.every(edge => edge.width === shown[0].width && same(edge.color, shown[0].color))) {
          // A uniform CSS border is an inside stroke: it follows the corner radius, as the verified native import does.
          node.strokes = [contentPaint(theme, shown[0].color, STROKE_PREFERENCE)]; node.strokeWeight = shown[0].width; node.strokeAlign = 'INSIDE';
        } else edges.forEach((edge, i) => {
          if (!(edge.width > 0 && edge.color?.a)) return;
          const border = rect(node, 'Bordure ' + i, i === 1 ? item.w - edge.width : 0, i === 2 ? item.h - edge.width : 0, i % 2 ? edge.width : item.w, i % 2 ? item.h : edge.width, []);
          border.fills = [contentPaint(theme, edge.color, STROKE_PREFERENCE)];
        });
      }
      node.name = item.type === 'text' ? item.text.slice(0, 80) : item.name; node.x = item.x; node.y = item.y;
      if (item.opacity !== undefined) node.opacity = item.opacity;
      for (const child of item.children || []) buildContent(child, node, theme);
      return node;
    }

    function ruler(parent, theme, name, corner) {
      const k = batch.constants, R = k.ruler, B = k.band;
      const strip = (axis) => { // axis 'v': strip along the left edge, fading downwards; 'h': along the top edge, fading rightwards
        const parts = [];
        const frameOf = (x, y, w, h) => (axis === 'v' ? [x, y, w, h] : [y, x, h, w]);
        const piece = (label, x, y, w, h, fills) => { const [px, py, pw, ph] = frameOf(x, y, w, h); return {label, x: px, y: py, w: pw, h: ph, fills}; };
        parts.push(piece('Bande', 0, 0, B, R, [solid(theme, 'accent/accent-fond')]));
        parts.push(piece('Filet', B / 2, 0, 1, R, [solid(theme, 'filet/filet')]));
        const ticks = [];
        for (let y = 0; y < R; y += 8) ticks.push(piece('Graduation', 9, y, 3, 1, [solid(theme, 'texte/encre')]));
        for (let y = 0; y < R; y += 24) ticks.push(piece('Graduation majeure', 9, y, 7, 1, [solid(theme, 'texte/encre')]));
        return {parts, ticks};
      };
      const holder = add(figma.createFrame(), parent); holder.name = name; holder.fills = []; holder.clipsContent = false; holder.resize(R, R);
      const place = corner === 'tl' ? [1, 0, 0, 0, 1, 0] : [-1, 0, parent.width, 0, -1, parent.height];
      holder.relativeTransform = [[place[0], place[1], place[2]], [place[3], place[4], place[5]]];
      for (const axis of ['v', 'h']) {
        const {parts, ticks} = strip(axis);
        const axisFrame = add(figma.createFrame(), holder); axisFrame.name = axis === 'v' ? 'Bord vertical' : 'Bord horizontal'; axisFrame.fills = []; axisFrame.clipsContent = false; axisFrame.resize(R, R); axisFrame.x = 0; axisFrame.y = 0;
        const mask = rect(axisFrame, 'Masque de fondu', 0, 0, R, R, [{type: 'GRADIENT_LINEAR', gradientTransform: axis === 'v' ? [[0, 1, 0], [-1, 0, 1]] : [[1, 0, 0], [0, 1, 0]],
          gradientStops: [{position: 0.5, color: {r: 1, g: 1, b: 1, a: 1}}, {position: 1, color: {r: 1, g: 1, b: 1, a: 0}}]}]);
        mask.isMask = true;
        for (const p of parts) rect(axisFrame, p.label, p.x, p.y, p.w, p.h, p.fills);
        const tickGroup = add(figma.createFrame(), axisFrame); tickGroup.name = 'Graduations'; tickGroup.fills = []; tickGroup.clipsContent = false; tickGroup.resize(R, R); tickGroup.x = 0; tickGroup.y = 0;
        tickGroup.opacity = theme === 'light' ? k.tickLight : k.tickDark;
        for (const t of ticks) rect(tickGroup, t.label, t.x, t.y, t.w, t.h, t.fills);
      }
      return holder;
    }

    function mark(parent, theme, name, x, y) {
      const k = batch.constants, S = k.markBox, tick = theme === 'light' ? k.tickLight : k.tickDark;
      const holder = add(figma.createFrame(), parent); holder.name = name; holder.fills = []; holder.clipsContent = false; holder.resize(S, S); holder.x = x; holder.y = y; holder.opacity = tick;
      const ring = add(figma.createEllipse(), holder); ring.name = 'Cercle'; ring.resize(8, 8); ring.x = 4.5; ring.y = 4.5; ring.fills = []; ring.strokes = [solid(theme, 'texte/encre')]; ring.strokeWeight = 1; ring.strokeAlign = 'CENTER';
      rect(holder, 'Trait horizontal', 0, 8, S, 1, [solid(theme, 'texte/encre')]);
      rect(holder, 'Trait vertical', 8, 0, 1, S, [solid(theme, 'texte/encre')]);
      return holder;
    }

    let done = 0;
    for (const frame of batch.frames) {
      if (cancelled) fail('Import arrêté ; nouvelle section supprimée.');
      const theme = frame.theme, k = batch.constants;
      const root = add(figma.createFrame(), section); root.name = frame.name; root.resize(frame.width, frame.height); root.x = frame.x; root.y = frame.y; root.clipsContent = true;
      root.fills = [solid(theme, frame.base)];
      root.setPluginData('etayeState', frame.id); root.setPluginData('etayeBackgroundBatch', batch.sourceSha256);
      const grain = rect(root, 'Fond · grain de papier', 0, 0, frame.width, frame.height,
        [{type: 'IMAGE', imageHash: imageHash[theme], scaleMode: 'TILE', scalingFactor: 1, opacity: k.grainOpacity}]);
      const p = frame.plate, reach = p.inset + p.feather / 2;
      const plate = rect(root, 'Fond · plaque calme', p.x - reach, p.y - reach, p.w + 2 * reach, p.h + 2 * reach, [solid(theme, 'surface/papier', k.calmOpacity)]);
      plate.effects = [{type: 'LAYER_BLUR', blurType: 'NORMAL', radius: Math.max(1, Math.round(0.8 * p.feather * 10) / 10), visible: true}];
      const blueprint = add(figma.createFrame(), root); blueprint.name = 'Fond · touche blueprint'; blueprint.fills = []; blueprint.clipsContent = false; blueprint.resize(frame.width, frame.height); blueprint.x = 0; blueprint.y = 0;
      const rulerTL = ruler(blueprint, theme, 'Règle haut-gauche', 'tl'), rulerBR = ruler(blueprint, theme, 'Règle bas-droite', 'br');
      const markTR = mark(blueprint, theme, 'Repère haut-droite', frame.width - k.markInset - k.markBox, k.markInset);
      const markBL = mark(blueprint, theme, 'Repère bas-gauche', k.markInset, frame.height - k.markInset - k.markBox);
      const content = buildContent(frame.scene.node, root, theme); content.name = 'Contenu d’exemple';
      evidenceFrames.push({state: frame.id, nodeId: root.id, name: frame.name, theme, viewport: frame.viewport, grainId: grain.id, plateId: plate.id, blueprintId: blueprint.id,
        rulerIds: [rulerTL.id, rulerBR.id], markIds: [markTR.id, markBL.id], contentId: content.id});
      done++; tell('Écran ' + done + ' / ' + batch.frames.length + '…');
      await new Promise(resolve => setTimeout(resolve, 0));
    }
    const right = Math.max(...batch.frames.map(frame => frame.x + frame.width)), bottom = Math.max(...batch.frames.map(frame => frame.y + frame.height));
    section.resizeWithoutConstraints(right + 40, bottom + 40);
    if (figma.currentPage.id !== page.id || figma.fileKey !== TARGET.fileKey) fail('La cible a changé pendant l’import. La section courante a été supprimée.');
    return {section, created, stats, used, evidenceFrames};
  } catch (error) {
    section.remove();
    throw error;
  }
}

async function run(batch) {
  validate(batch);
  const context = await preflight(batch);
  const result = await buildRun(batch, context);
  figma.currentPage.selection = [result.section];
  figma.viewport.scrollAndZoomIntoView([result.section]);
  const evidence = {format: 'etaye-background-study-evidence-v1', createdAt: new Date().toISOString(), fileKey: figma.fileKey, pageId: figma.currentPage.id,
    sectionId: result.section.id, sourceSha256: batch.sourceSha256, frames: result.evidenceFrames, createdNodeCount: result.created.length, createdNodeIds: result.created,
    colorBinding: result.stats, variablesUsed: [...result.used].sort(), fontsUsed: [...context.fonts.values()],
    figmaImported: true, visuallyReviewed: false, approved: false};
  tell('Étude créée en calques modifiables (' + result.created.length + ' calques). À vérifier à l’écran : fondu des règles, plaque calme, grain. Aucune approbation.', true, evidence);
}

figma.ui.onmessage = async message => {
  if (message.type === 'cancel') { cancelled = true; if (!busy) figma.closePlugin(); return; }
  if (message.type !== 'import' || busy) return;
  busy = true; cancelled = false;
  try { await run(message.data); } catch (error) { tell(String(error.message || error), true, {status: 'failed', message: String(error.message || error)}); } finally { busy = false; }
};

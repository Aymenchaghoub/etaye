// Offline checks of the background-study plugin against a small fake of the Figma Plugin API.
// Run: node --test design/figma-background-study/test-plugin.cjs
// This proves the plugin's own guards and structure. It does NOT prove how Figma Desktop renders the result:
// that needs a real run (see README, "What is not verified").
const test = require('node:test'), assert = require('node:assert/strict'), vm = require('node:vm'), fs = require('node:fs'), path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, 'code.js'), 'utf8');
const BATCH = JSON.parse(fs.readFileSync(path.join(__dirname, 'batches/background-study-v1.json'), 'utf8'));
const PAGE_NAME = 'Étaye — Fond papier (étude)';
const ROLES = ['surface/fond', 'surface/surface', 'surface/surface-2', 'surface/papier', 'texte/encre', 'texte/encre-2', 'texte/encre-3', 'texte/encre-inverse', 'filet/filet', 'filet/filet-controle', 'accent/accent', 'accent/accent-fond', 'accent/sur-accent'];
const STYLE = {400: 'Regular', 500: 'Medium', 600: 'SemiBold', 700: 'Bold'};
const batch = () => structuredClone(BATCH);
const walk = (node, visit) => {visit(node); (node.children || []).forEach(child => walk(child, visit));};

function fontsOf(data) {
  const fonts = new Map();
  for (const frame of data.frames) walk(frame.scene.node, node => {if (node.type === 'text') fonts.set(node.family + node.weight, {family: node.family, style: STYLE[node.weight] || 'Regular'});});
  return [...fonts.values()];
}

function harness(data, options = {}) {
  let nextId = 0, creations = 0, textCreations = 0, yields = 0;
  const nodes = new Map(), messages = [], removed = [], closed = [];
  function make(type, id, parent, props = {}) {
    const node = {id: id || 'new:' + ++nextId, type, name: '', children: [], parent: null, x: 0, y: 0, width: 10, height: 10, opacity: 1, visible: true, fills: [], strokes: [], effects: [], data: {}, removed: false,
      appendChild(child) {if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1); this.children.push(child); child.parent = this;},
      resize(w, h) {this.width = w; this.height = h;}, resizeWithoutConstraints(w, h) {this.resize(w, h);},
      setPluginData(k, v) {this.data[k] = v;}, getPluginData(k) {return this.data[k] || '';},
      remove() {if (this.removed) return; for (const child of this.children.slice()) child.remove(); this.removed = true; removed.push(this.id); if (this.parent) this.parent.children.splice(this.parent.children.indexOf(this), 1); this.parent = null;}, ...props};
    nodes.set(node.id, node); if (parent) parent.appendChild(node); return node;
  }
  const page = make('PAGE', '40:1', null, {name: options.pageName || PAGE_NAME});
  const foundations = make('PAGE', '2:74', null, {name: 'Fondations'}), components = make('PAGE', '2:75', null, {name: 'Composants'}), imported = make('PAGE', '15:2', null, {name: 'Étaye — Import HTML'});
  make('FRAME', 'protected-tokens', foundations, {name: 'Jetons', fills: [{type: 'SOLID', color: {r: 1, g: 1, b: 1}}]});
  const logos = {light: make('COMPONENT', '4:13', components, {name: options.logoName || 'Logo/Clair'}), dark: make('COMPONENT', '4:33', components, {name: 'Logo/Sombre'})};
  if (options.noLogo) logos.light.removed = true;
  for (const component of Object.values(logos)) component.createInstance = () => {creations++; return make('INSTANCE', null, null, {name: component.name, mainComponent: component});};
  make('SECTION', '26:2', imported, {name: 'Étaye · S-06 · import', width: 4560, height: 3659});
  make('FRAME', '26:3', nodes.get('26:2'), {name: 'S-06/annule', width: 1440, height: 1113});
  const variables = new Map(), collections = [];
  for (const [theme, name] of [['light', 'Couleurs · Clair'], ['dark', 'Couleurs · Sombre']]) {
    if (options.noCollection === theme) continue;
    const ids = [];
    for (const role of ROLES) {
      if (options.missingVariable === theme + ':' + role) continue;
      const id = `VariableID:${theme}/${role}`; variables.set(id, {id, name: role, resolvedType: 'COLOR'}); ids.push(id);
    }
    collections.push({name, variableIds: ids});
  }
  const create = type => {if (options.failAfterTexts !== undefined && type === 'TEXT' && ++textCreations > options.failAfterTexts) throw Error('Simulated native failure'); creations++; return make(type, null, page);};
  const figma = {fileKey: options.fileKey === undefined ? 'vfh4BSnbRAbaDE1q0CyssL' : options.fileKey, currentPage: page, showUI() {}, closePlugin() {closed.push(true);},
    viewport: {scrollAndZoomIntoView() {}}, ui: {postMessage(message) {messages.push(structuredClone(message));}},
    root: {children: [foundations, components, imported, page]},
    listAvailableFontsAsync: async () => (options.fonts || fontsOf(data)).map(fontName => ({fontName})),
    loadFontAsync: async () => {if (options.loadError) throw Error('Font loading failed');},
    getNodeByIdAsync: async id => {const node = nodes.get(id); return node && !node.removed ? node : null;},
    createSection: () => create('SECTION'), createFrame: () => create('FRAME'), createRectangle: () => create('RECTANGLE'), createEllipse: () => create('ELLIPSE'), createText: () => create('TEXT'),
    createImage(bytes) {return {hash: 'image-' + bytes.length};}, base64Decode: value => new Uint8Array(Buffer.from(value, 'base64')),
    variables: {getLocalVariableCollectionsAsync: async () => collections, getVariableByIdAsync: async id => variables.get(id) || null,
      setBoundVariableForPaint: (paint, field, variable) => ({...paint, boundVariables: {[field]: {type: 'VARIABLE_ALIAS', id: variable.id}}})}};
  const api = {figma, page, nodes, messages, removed, closed, logos, make, get creations() {return creations;}, last: () => messages.at(-1), send: message => figma.ui.onmessage(message)};
  vm.runInNewContext(script, {figma, __html__: '', setTimeout(callback) {queueMicrotask(() => {yields++; if (options.onYield) options.onYield(api, yields); callback();});}});
  return api;
}
// What must never change: the protected pages and the imported section (a stable JSON projection).
const protectedSnapshot = h => JSON.stringify([h.nodes.get('2:74'), h.nodes.get('2:75'), h.nodes.get('15:2')].map(function project(node) {
  return {id: node.id, name: node.name, fills: node.fills, x: node.x, y: node.y, width: node.width, height: node.height, removed: node.removed, children: node.children.map(project)};
}));
const sections = h => h.page.children.filter(node => node.type === 'SECTION');
const untouched = h => {assert.equal(h.creations, 0); assert.equal(h.removed.length, 0); assert.equal(h.page.children.length, 0);};
const run = async (data, options) => {const h = harness(data, options); const before = protectedSnapshot(h); await h.send({type: 'import', data}); return {h, before};};

test('the committed batch is well-formed and matches the specification constants', () => {
  assert.equal(BATCH.format, 'etaye-background-study-v1'); assert.equal(BATCH.frames.length, 8);
  assert.deepEqual(BATCH.frames.map(f => f.id).sort(), ['B-desktop-dark', 'B-desktop-light', 'B-mobile-dark', 'B-mobile-light', 'B-tablet-dark', 'B-tablet-landscape-dark', 'B-tablet-landscape-light', 'B-tablet-light']);
  assert.deepEqual(BATCH.constants, {grainOpacity: 0.12, tickLight: 0.32, tickDark: 0.38, calmOpacity: 0.8, ruler: 192, band: 16, markBox: 16, markInset: 8});
  for (const f of BATCH.frames) assert.equal(f.base, f.theme === 'light' ? 'surface/papier' : 'surface/fond');
  for (const theme of ['light', 'dark']) {const png = Buffer.from(BATCH.tiles[theme], 'base64'); assert.equal(png.readUInt32BE(16), 256); assert.equal(png.readUInt32BE(20), 256); assert.equal(png.subarray(1, 4).toString(), 'PNG');}
});

test('happy path: one new section, eight frames, layers in the specified order and bound to existing variables', async () => {
  const {h, before} = await run(batch());
  assert.equal(h.last().evidence.figmaImported, true, h.last().message); assert.equal(h.last().evidence.approved, false); assert.equal(h.last().evidence.visuallyReviewed, false);
  assert.equal(sections(h).length, 1); const section = sections(h)[0]; assert.equal(section.children.length, 8); assert.equal(h.removed.length, 0);
  assert.equal(section.getPluginData('etayeBackgroundBatch'), BATCH.sourceSha256);
  for (const frame of section.children) {
    const spec = BATCH.frames.find(f => f.id === frame.getPluginData('etayeState')), theme = spec.theme;
    assert.deepEqual([frame.width, frame.height, frame.x, frame.y], [spec.width, spec.height, spec.x, spec.y]);
    assert.equal(frame.fills[0].boundVariables.color.id, `VariableID:${theme}/${spec.base}`, 'base fill is the adopted-tone variable');
    assert.deepEqual(frame.children.map(c => c.name), ['Fond · grain de papier', 'Fond · plaque calme', 'Fond · touche blueprint', 'Contenu d’exemple']);
    const [grain, plate, blueprint, content] = frame.children;
    assert.deepEqual([grain.width, grain.height], [spec.width, spec.height]);
    assert.deepEqual([grain.fills[0].type, grain.fills[0].scaleMode, grain.fills[0].scalingFactor, grain.fills[0].opacity], ['IMAGE', 'TILE', 1, 0.12]);
    const p = spec.plate, reach = p.inset + p.feather / 2;
    assert.deepEqual([plate.x, plate.y, plate.width, plate.height], [p.x - reach, p.y - reach, p.w + 2 * reach, p.h + 2 * reach]);
    assert.equal(plate.fills[0].opacity, 0.8); assert.equal(plate.fills[0].boundVariables.color.id, `VariableID:${theme}/surface/papier`);
    assert.deepEqual([plate.effects[0].type, plate.effects[0].radius], ['LAYER_BLUR', Math.round(0.8 * p.feather * 10) / 10]);
    const [tl, br, tr, bl] = blueprint.children; assert.deepEqual(blueprint.children.map(c => c.name), ['Règle haut-gauche', 'Règle bas-droite', 'Repère haut-droite', 'Repère bas-gauche']);
    assert.equal(JSON.stringify(tl.relativeTransform), JSON.stringify([[1, 0, 0], [0, 1, 0]])); assert.equal(JSON.stringify(br.relativeTransform), JSON.stringify([[-1, 0, spec.width], [0, -1, spec.height]]), 'the bottom-right ruler is the same symbol turned by 180 degrees');
    assert.deepEqual([tr.x, tr.y, bl.x, bl.y], [spec.width - 8 - 16, 8, 8, spec.height - 8 - 16]);
    for (const ruler of [tl, br]) for (const edge of ruler.children) {
      assert.equal(edge.children[0].isMask, true, 'the fade mask is the lowest layer of each edge'); assert.equal(edge.children[0].fills[0].type, 'GRADIENT_LINEAR');
      const ticks = edge.children.at(-1); assert.equal(ticks.opacity, theme === 'light' ? 0.32 : 0.38); assert.equal(ticks.children.length, 24 + 8);
      assert.deepEqual(edge.children.slice(1, 3).map(c => c.name), ['Bande', 'Filet']);
    }
    assert.equal(tr.opacity, theme === 'light' ? 0.32 : 0.38);
    assert.equal(content.type, 'FRAME'); const logo = content.children.flatMap(function find(n) {return [n, ...n.children.flatMap(find)];}).find(n => n.type === 'INSTANCE');
    assert.equal(logo.mainComponent.id, theme === 'light' ? '4:13' : '4:33', 'the original logo component is reused');
  }
  const evidence = h.last().evidence; assert.equal(evidence.frames.length, 8); assert.ok(evidence.colorBinding.bound > 100 && evidence.colorBinding.bound > evidence.colorBinding.raw, JSON.stringify(evidence.colorBinding));
  assert.equal(evidence.createdNodeCount, evidence.createdNodeIds.length); assert.equal(new Set(evidence.createdNodeIds).size, evidence.createdNodeCount);
  assert.equal(h.creations, evidence.createdNodeCount, 'every created node is recorded in the evidence');
  assert.equal(protectedSnapshot(h), before, 'protected pages and the imported section are unchanged');
});

test('wrong or unavailable file key, wrong page and protected pages are refused before any canvas write', async () => {
  for (const fileKey of ['another-file', null]) {const {h} = await run(batch(), {fileKey}); untouched(h); assert.match(h.last().message, /Mauvais fichier/);}
  {const {h} = await run(batch(), {pageName: 'Another page'}); untouched(h); assert.match(h.last().message, /page dédiée/);}
  {const h = harness(batch()); h.page.id = '15:2'; await h.send({type: 'import', data: batch()}); untouched(h); assert.match(h.last().message, /page dédiée/);}
});

test('missing exact fonts, collections, variables or logo components stop the run with nothing created', async () => {
  for (const [options, pattern] of [[{fonts: []}, /Polices/], [{fonts: [{family: 'atkinson hyperlegible next', style: 'Regular'}]}, /Polices/], [{loadError: true}, /Font loading/],
    [{noCollection: 'dark'}, /collection « Couleurs · Sombre »/], [{missingVariable: 'light:surface/papier'}, /Couleurs · Clair \/ surface\/papier/], [{missingVariable: 'dark:surface/fond'}, /Couleurs · Sombre \/ surface\/fond/],
    [{logoName: 'Logo / Clair'}, /Composant logo/], [{noLogo: true}, /Composant logo/]]) {
    const {h} = await run(batch(), options); untouched(h); assert.equal(h.last().evidence.status, 'failed'); assert.match(h.last().message, pattern);
  }
});

test('a batch already on the page is refused and nothing is replaced', async () => {
  const h = harness(batch()); await h.send({type: 'import', data: batch()}); assert.equal(sections(h).length, 1); const created = h.creations;
  await h.send({type: 'import', data: batch()}); assert.equal(sections(h).length, 1); assert.equal(h.creations, created); assert.match(h.last().message, /existe déjà/);
});

test('a failure or a stop in the middle removes the whole new section and leaves the file as it was', async () => {
  {const {h, before} = await run(batch(), {failAfterTexts: 5}); assert.equal(h.page.children.length, 0); assert.ok(h.removed.length > 5); assert.match(h.last().message, /Simulated native failure/); assert.equal(protectedSnapshot(h), before);}
  {const options = {onYield(api, count) {if (count === 3) api.send({type: 'cancel'});}}; const {h, before} = await run(batch(), options); assert.equal(h.page.children.length, 0); assert.match(h.last().message, /arrêté/); assert.equal(protectedSnapshot(h), before);}
  {const options = {onYield(api, count) {if (count === 2) api.figma.currentPage = api.nodes.get('2:75');}}; const {h} = await run(batch(), options); assert.equal(h.page.children.length, 0); assert.match(h.last().message, /cible a changé/);}
});

test('malformed or unsafe batches are rejected before any canvas write', async () => {
  const mutations = [
    d => {d.format = 'other';}, d => {d.fileKey = 'another-file';}, d => {d.sourceSha256 = 'xyz';}, d => {d.frames = [];}, d => {d.frames = Array.from({length: 13}, (_, i) => ({...d.frames[0], id: 'f' + i}));},
    d => {d.tiles.light = 'AAAA';}, d => {d.constants.grainOpacity = 2;}, d => {d.constants.ruler = -1;},
    d => {d.frames[0].base = 'surface/surface';}, d => {d.frames[4].base = 'surface/papier';}, d => {d.frames[1].id = d.frames[0].id;}, d => {d.frames[0].width = NaN;},
    d => {d.frames[0].plate.feather = -1;}, d => {d.frames[0].scene.node.type = 'svg';}, d => {d.frames[0].scene.node.children[0].x = Infinity;},
    d => {const logo = []; walk(d.frames[0].scene.node, n => {if (n.type === 'instance') logo.push(n);}); logo[0].component = 'Logo/Autre';},
    d => {const t = []; walk(d.frames[0].scene.node, n => {if (n.type === 'text') t.push(n);}); delete t[0].text;},
  ];
  for (const mutate of mutations) {const data = batch(); mutate(data); const {h} = await run(data); untouched(h); assert.equal(h.last().evidence.status, 'failed', mutate.toString());}
});

test('the plugin source makes no network call and does not touch existing nodes by id', () => {
  assert.doesNotMatch(script, /\b(fetch|XMLHttpRequest|WebSocket|importScripts|eval)\b|new Function/);
  assert.deepEqual([...script.matchAll(/getNodeByIdAsync\(([^)]*)\)/g)].map(m => m[1]), ['LOGOS[theme].id']);
  assert.doesNotMatch(script, /\.(findAll|findOne|children\.forEach)\([^)]*\)\.?[a-zA-Z]*\s*\.remove\(|currentPage\.remove|\.children\[0\]\.remove/);
});

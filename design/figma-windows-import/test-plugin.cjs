const test = require('node:test'), assert = require('node:assert/strict'), vm = require('node:vm'), fs = require('node:fs');
const script = fs.readFileSync(__dirname + '/code.js', 'utf8');
const CANONICAL = JSON.parse(fs.readFileSync(__dirname + '/canonical-queue.json', 'utf8'));
const CAPTURE = 'a6c9010bc215d86d32e430c0e50abac5b75bca16c1ff6102f46892e62b17974b';
const FONT = {family: 'Atkinson Hyperlegible Next', style: 'Regular'};
const ROOTS = {'S-06/annule': '26:3', 'S-06/chargement': '26:222', 'S-06/echec-partiel': '26:416', 'S-06/jour-vide': '26:734', 'S-06/minuit': '26:987', 'S-06/nora': '26:1282', 'S-06/perime': '26:1610', 'S-06/sami-a': '26:1898'};
const CLEANUP = [
  {id: '15:3', type: 'SECTION', name: 'Étaye · import à revoir · I-01/combinaison', imageHashes: []},
  {id: '19:2', type: 'SECTION', name: 'Étaye · test MCP · S-06/nora · 2026-11-17', imageHashes: []},
  ...['becf0f1701765f081d309ff74441c1c527fb5935', '3e923d6d3f5ff2d3f55f33b20cd9e0b41ff1e172', '3ad84d0036d2eec8f0163543f0f66778be013606', 'b0972785305a654beac44215d0f03f3648cd88c4', '942623a83f471af6ba47c4ea3e9e46e392333f80', '190d61c42f3f7d2956b392dae905189c858f11de', '6c1ef3a8aa9a93a677f189d427db62bc95c8396a', '28eac2593de6959e16dc7ea77bacbbc7ef4a9b56', 'e5acc091386c4f9271d3c696b4fcf9615aefd63a', 'fbc7df3ec9d99814f40d8cb9445579e9c08317ce'].map((hash, index) => ({id: '22:' + (index + 2), type: 'FRAME', name: 'Uploaded Image', imageHashes: [hash]}))
];
function queue() {
  const existing = [{sectionId: '26:2', name: 'Étaye · S-06 · import complet à revoir · 2026-11-17 · 1/1', roots: Object.entries(ROOTS).map(([state, nodeId]) => ({state, nodeId, name: state + ' · desktop1440 · à revoir', width: 1440, height: 1113}))}];
  return Array.from({length: 94}, (_, packetIndex) => {
    const ids = CANONICAL[packetIndex].states;
    return {format: 'etaye-windows-native-v2', packetIndex, totalPackets: 94, totalStates: 456, fileKey: 'vfh4BSnbRAbaDE1q0CyssL', pageId: '15:2',
      snapshot: 'd2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6', capture: CAPTURE, rawPacketSha256: CANONICAL[packetIndex].rawSha256, name: packetIndex === 0 ? existing[0].name : 'Prepared packet ' + packetIndex,
      expectedExisting: structuredClone(existing), cleanupCandidates: structuredClone(CLEANUP), x: 4000, y: packetIndex * 2000 + 80,
      frames: ids.map(id => ({id, width: 1440, height: 1113, background: 0, node: packetIndex === 0 ? 1 : 0, texts: 1})),
      nodes: [[1, 24, 24, 200, 18, 'Prepared editable text', 0, 14, 18, 0, 0, null, false], ...(packetIndex === 0 ? [[0, 0, 0, 400, 300, 'Source link', 0, 0, false, 1, [], null, [0], 'S-06/nora', []]] : [])], fonts: [structuredClone(FONT)], colors: [{r: 0.1, g: 0.1, b: 0.1, a: 1}], svgs: [], images: {}, imageData: {}};
  });
}
function harness(packets, options = {}) {
  let nextId = 0, creationCount = 0, yields = 0;
  const nodes = new Map(), messages = [], removed = [];
  function make(type, id, parent, props = {}) {
    const node = {id: id || 'new:' + ++nextId, type, name: '', children: [], parent: null, x: 0, y: 0, width: 10, height: 10, opacity: 1, visible: true, fills: [], strokes: [], effects: [], data: {}, removed: false,
      get absoluteBoundingBox() {let x = this.x, y = this.y, parent = this.parent; while (parent) {x += parent.x; y += parent.y; parent = parent.parent;} return {x, y, width: this.width, height: this.height};},
      appendChild(child) {if (options.failTextAppend && this.type === 'FRAME' && child.type === 'TEXT' && child.id.startsWith('new:')) throw Error('Simulated append failure'); if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1); this.children.push(child); child.parent = this;},
      resize(width, height) {this.width = width; this.height = height;}, resizeWithoutConstraints(width, height) {this.resize(width, height);},
      setPluginData(key, value) {this.data[key] = value;}, getPluginData(key) {return this.data[key] || '';},
      findAll(predicate) {return this.children.flatMap(child => [...(predicate(child) ? [child] : []), ...(typeof child.findAll === 'function' ? child.findAll(predicate) : [])]);},
      remove() {if (this.removed) return; for (const child of this.children.slice()) child.remove(); this.removed = true; removed.push(this.id); if (this.parent) this.parent.children.splice(this.parent.children.indexOf(this), 1); this.parent = null;}, ...props};
    // Native leaf nodes do not implement ChildrenMixin; rollback must handle them.
    if (['TEXT', 'RECTANGLE', 'VECTOR'].includes(type)) delete node.findAll;
    nodes.set(node.id, node); if (parent) parent.appendChild(node); return node;
  }
  const page = make('PAGE', '15:2', null, {name: 'Étaye — Import HTML'});
  const foundations = make('PAGE', '2:74', null, {name: 'Fondations'}), components = make('PAGE', '2:75', null, {name: 'Composants'});
  const original = make('FRAME', 'protected-frame', foundations, {name: 'Original logo and tokens'});
  for (const candidate of CLEANUP) {const node = make(candidate.type, candidate.id, page, {name: candidate.name}); for (const hash of candidate.imageHashes) make('RECTANGLE', 'image-' + candidate.id, node, {fills: [{type: 'IMAGE', imageHash: hash}]});}
  const recorded = packets[0].expectedExisting[0], section = make('SECTION', recorded.sectionId, page, {name: recorded.name, x: 4000, y: 80, width: 4560, height: 3659});
  for (const [index, root] of recorded.roots.entries()) {const frame = make('FRAME', root.nodeId, section, {name: root.name, width: root.width, height: root.height, x: 40 + index % 3 * 1520, y: 40 + Math.floor(index / 3) * 1233, clipsContent: true}), body = make('FRAME', 'body-' + root.nodeId, frame, {name: 'Source link', width: 400, height: 300, clipsContent: false}); make('TEXT', 'text-' + root.nodeId, body, {x: 24, y: 24, width: 200, height: 18, characters: 'Prepared editable text', fontName: FONT, fontSize: 14, fills: [{type: 'SOLID', color: {r: 0.1, g: 0.1, b: 0.1}}]});}
  // Independently materialize the captured REST contract from the initial native fixture.
  const baseline = root => {
    const walk = node => [node, ...node.children.flatMap(walk)];
    const paints = values => values.map(paint => [paint.type, paint.visible ?? true, paint.opacity ?? 1, paint.type === 'SOLID' ? paint.color.r : null, paint.type === 'SOLID' ? paint.color.g : null, paint.type === 'SOLID' ? paint.color.b : null, paint.type === 'IMAGE' ? paint.imageHash : null, paint.type === 'IMAGE' ? paint.scaleMode ?? null : null]);
    return walk(root).map(node => {const box = node.absoluteBoundingBox; return {id: node.id, parentId: node.parent?.id || null, type: node.type, name: node.name, box: [box.x, box.y, box.width, box.height].map(value => Math.round(value * 1000) / 1000), opacity: node.opacity, visible: node.visible, clipsContent: node.type === 'FRAME' ? Boolean(node.clipsContent) : null, fills: paints(node.fills), strokes: paints(node.strokes), text: node.type === 'TEXT' ? {characters: node.characters, family: node.fontName.family, style: node.fontName.style, size: node.fontSize} : null};});
  };
  recorded.nativeInventory = baseline(section);
  packets[0].cleanupCandidates.forEach(candidate => candidate.nativeInventory = baseline(nodes.get(candidate.id)));
  const create = type => {creationCount++; return make(type, null, figma.currentPage);};
  const figma = {fileKey: options.fileKey === undefined ? 'vfh4BSnbRAbaDE1q0CyssL' : options.fileKey, currentPage: page, showUI() {},
    ui: {postMessage(message) {messages.push(structuredClone(message)); if (options.onMessage) options.onMessage(message, api);}},
    listAvailableFontsAsync: async () => (options.fonts || [FONT]).map(fontName => ({fontName})),
    loadFontAsync: async font => {if (options.loadError) throw Error('Font loading failed');},
    getNodeByIdAsync: async id => {const node = nodes.get(id); return node && !node.removed ? node : null;},
    createSection: () => create('SECTION'), createFrame: () => create('FRAME'), createText: () => create('TEXT'), createRectangle: () => create('RECTANGLE'),
    createNodeFromSvg(svg) {if (svg.includes('native-failure')) throw Error('Simulated native SVG failure'); const root = create('FRAME'); make('VECTOR', null, root, {vectorPaths: [{windingRule: 'NONZERO', data: 'M0 0L20 20'}]}); return root;},
    createImage(bytes) {if (options.badImage) throw Error('Native PNG decoder refused input'); return {hash: 'local-image-hash'};},
    base64Decode: value => new Uint8Array(Buffer.from(value, 'base64'))};
  const api = {figma, page, foundations, components, original, nodes, messages, removed, make, get creationCount() {return creationCount;}, get yields() {return yields;},
    send: message => figma.ui.onmessage(message), last: () => messages.at(-1)};
  vm.runInNewContext(script, {figma, __html__: '', setTimeout(callback) {queueMicrotask(() => {yields++; if (options.onYield) options.onYield(api); callback();});}});
  return api;
}
const untouched = h => {assert.equal(h.creationCount, 0); assert.equal(h.removed.length, 0); assert.equal(h.original.removed, false);};
test('exact missing font, wrong case/style and failed load leave the canvas unchanged', async () => {
  for (const options of [{fonts: []}, {fonts: [{family: FONT.family.toLowerCase(), style: 'Regular'}]}, {fonts: [{family: FONT.family, style: 'Medium'}]}, {loadError: true}]) {
    const packets = queue(), h = harness(packets, options); await h.send({type: 'import', packets}); untouched(h); assert.equal(h.last().evidence.status, 'failed'); assert.match(h.last().message, /[Pp]olice|Font/);
  }
});
test('wrong or unavailable file key and wrong page reject before any canvas mutation', async () => {
  for (const fileKey of ['another-file', null]) {const packets = queue(), h = harness(packets, {fileKey}); await h.send({type: 'import', packets}); untouched(h); assert.match(h.last().message, /Mauvais fichier/);}
  const packets = queue(), h = harness(packets); h.page.name = 'Another page'; await h.send({type: 'import', packets}); untouched(h); assert.match(h.last().message, /15:2/);
});
test('incomplete selection, duplicate packet and invalid later graph cannot partially import', async () => {
  for (const mutate of [p => p.pop(), p => p[93] = structuredClone(p[92]), p => p[93].frames[0].node = 999, p => {p[93].nodes = [[0, 0, 0, 10, 10, 'cycle', 0, 0, false, 1, [], null, [0], null, []]]; p[93].frames.forEach(f => f.texts = 0);}, p => p[93].nodes[0][7] = NaN, p => p[93].nodes[0][11] = {line: 'underline', style: 'solid', color: {r: 2, g: 0, b: 0}, thickness: 1, offset: 0}]) {
    const packets = queue(), h = harness(packets); mutate(packets); await h.send({type: 'import', packets}); untouched(h); assert.equal(h.last().evidence.status, 'failed');
  }
});
test('canonical capture, raw packet provenance and exact per-packet state assignment are required', async () => {
  for (const mutate of [p => p[93].frames[0].id = 'P-03/invented', p => {const state = p[1].frames[0].id; p[1].frames[0].id = p[2].frames[0].id; p[2].frames[0].id = state;}, p => p.forEach(packet => packet.capture = 'b'.repeat(64)), p => p[93].rawPacketSha256 = 'f'.repeat(64)]) {
    const packets = queue(), h = harness(packets); mutate(packets); await h.send({type: 'import', packets}); untouched(h); assert.equal(h.last().evidence.status, 'failed'); assert.match(h.last().message, /[Pp]rovenance|canonique|capture/);
  }
});
test('native baseline refuses moved, hidden or cleared recorded text with unchanged content and dimensions', async () => {
  for (const mutate of [node => node.x++, node => node.visible = false, node => node.fills = []]) {const packets = queue(), h = harness(packets); mutate(h.nodes.get('text-26:3')); await h.send({type: 'import', packets}); untouched(h); assert.match(h.last().message, /Inventaire natif modifié/);}
});
test('new-section fingerprints detect moved or cleared text and changed vector paths or effects on resume', async () => {
  for (const [vector, mutate] of [[false, section => section.children[0].children[0].x++], [false, section => section.children[0].children[0].fills = []], [false, section => section.children[0].effects = [{type: 'DROP_SHADOW', radius: 99}]], [true, section => section.children[0].children[0].children[0].vectorPaths[0].data = 'M0 0L9 9']]) {
    const packets = queue(); if (vector) {packets[1].svgs = ['<svg/>']; packets[1].nodes = [[2, 0, 0, 20, 20, 'Editable vector', 0, 1, null, []]]; packets[1].frames.forEach(frame => frame.texts = 0);}
    const h = harness(packets); await h.send({type: 'import', packets}); const section = h.nodes.get(h.last().evidence.packets[1].sectionId), count = h.creationCount; mutate(section); await h.send({type: 'import', packets}); assert.equal(h.creationCount, count); assert.match(h.last().message, /Propriétés natives modifiées/); assert.equal(section.removed, false);
  }
});
test('fingerprint SHA-256 matches standard UTF-8 vectors and canonical literal matches its source', () => {
  const context = {figma: {showUI() {}, ui: {}}, __html__: ''}; vm.createContext(context); vm.runInContext(script, context);
  assert.equal(context.textSignature('complet', FONT, Math.fround(11.6667)), context.textSignature('complet', FONT, 11.6667));
  assert.notEqual(context.textSignature('complet', FONT, 11.6668), context.textSignature('complet', FONT, 11.6667));
  const reconciliation = vm.runInContext('RECONCILED_SECTION', context);
  assert.equal(context.acceptsNativeFingerprint(reconciliation.id, reconciliation.packetIndex, reconciliation.stored, reconciliation.rebuilt), true);
  assert.equal(context.acceptsNativeFingerprint('other', reconciliation.packetIndex, reconciliation.stored, reconciliation.rebuilt), false);
  assert.equal(context.acceptsNativeFingerprint(reconciliation.id, reconciliation.packetIndex, reconciliation.stored, 'changed'), false);
  for (const value of ['', 'abc', 'Étaye — preuve 🧭', 'x'.repeat(10000)]) assert.equal(context.sha256(value), require('node:crypto').createHash('sha256').update(value).digest('hex'));
  assert.deepEqual(JSON.parse(vm.runInContext('JSON.stringify(CANONICAL)', context)), CANONICAL);
});
test('observed REST baseline records round-trip through the native common-property normalization', () => {
  const observed = JSON.parse(fs.readFileSync(__dirname + '/../../docs/design/windows-handoff/EXISTING-FIGMA-STATE.json', 'utf8'));
  const context = {figma: {showUI() {}, ui: {}}, __html__: ''}; vm.createContext(context); vm.runInContext(script, context);
  let count = 0;
  for (const item of [...observed.completedSections, ...observed.cleanupCandidates]) {
    const rows = item.nativeInventory, nodes = new Map();
    const paints = tuples => tuples.map(([type, visible, opacity, r, g, b, imageHash, scaleMode]) => ({type, visible, opacity, ...(type === 'SOLID' ? {color: {r, g, b}} : type === 'IMAGE' ? {imageHash, scaleMode} : {})}));
    for (const row of rows) nodes.set(row.id, {id: row.id, type: row.type, name: row.name, children: [], opacity: row.opacity, visible: row.visible, clipsContent: row.clipsContent,
      absoluteBoundingBox: row.box ? {x: row.box[0], y: row.box[1], width: row.box[2], height: row.box[3]} : null, fills: paints(row.fills), strokes: paints(row.strokes),
      ...(row.text ? {characters: row.text.characters, fontName: {family: row.text.family, style: row.text.style}, fontSize: row.text.size} : {})});
    for (const row of rows) {const node = nodes.get(row.id); node.parent = nodes.get(row.parentId) || {id: row.parentId}; if (node.parent.children) node.parent.children.push(node);}
    const root = nodes.get(rows[0].id), walk = node => node.children.flatMap(child => [child, ...walk(child)]); root.findAll = predicate => walk(root).filter(predicate);
    assert.deepEqual(JSON.parse(JSON.stringify(context.nativeInventory(root))), rows); count += rows.length;
    assert.equal(context.matchesCapturedInventory(root, rows), true);
    const vector = rows.find(row => row.type === 'FRAME' && row.box) || rows.find(row => row.box);
    if (vector) {
      const box = nodes.get(vector.id).absoluteBoundingBox;
      const width = box.width;
      box.width = width + 0.001;
      assert.equal(context.matchesCapturedInventory(root, rows), true);
      box.width = width + 0.002;
      assert.equal(context.matchesCapturedInventory(root, rows), false);
      box.width = width;
    }
  }
  assert.equal(count, 5527);
});
test('native S-06 IDs, dimensions, font and text are validated before reuse', async () => {
  for (const mutate of [h => h.nodes.get('26:3').width = 100, h => h.nodes.get('text-26:3').characters = 'Edited', h => h.nodes.get('text-26:3').fontName = {...FONT, style: 'Bold'}, h => h.nodes.get('26:3').remove(), h => h.nodes.get('26:2').name = 'Renamed']) {
    const packets = queue(), h = harness(packets); mutate(h); const removedBefore = h.removed.length; await h.send({type: 'import', packets}); assert.equal(h.creationCount, 0); assert.equal(h.removed.length, removedBefore); assert.equal(h.original.removed, false); assert.equal(h.last().evidence.status, 'failed');
  }
});
test('all 94 packets / 456 states import deterministically, reuse 26:2, and retry without duplicates', async () => {
  const packets = queue(), h = harness(packets); await h.send({type: 'import', packets: packets.slice().reverse()});
  const report = h.last().evidence; assert.equal(report.status, 'complete'); assert.equal(report.packets.length, 94); assert.equal(report.verifiedPackets.flatMap(p => p.roots).length, 456); assert.equal(report.packets[0].status, 'reused'); assert.equal(report.packets[0].sectionId, '26:2'); assert.equal(report.packets[0].createdNodeIds.length, 0); assert.equal(report.approved, false); assert.equal(report.visuallyReviewed, false); assert.ok(report.finishedAt); assert.equal(report.captureSha256, CAPTURE); assert.deepEqual(report.rawPacketSha256s, CANONICAL.map(packet => packet.rawSha256));
  assert.equal(report.packets[0].links.length, 8); assert.equal(report.packets[0].links[0].state, 'S-06/annule'); assert.equal(report.packets[0].links[0].target, 'S-06/nora'); assert.deepEqual(report.packets[0].links[0].tuplePath, [1]); assert.deepEqual(report.packets[0].links[0].childPath, []); assert.equal(report.packets[0].links[0].nodeId, null);
  const first = h.nodes.get(report.packets[1].sectionId); assert.equal(first.x, packets[1].x); assert.equal(first.y, packets[1].y); assert.equal(first.children[0].x, 40); assert.equal(first.children[1].x, 1560); assert.equal(first.children[3].y, 1273); assert.equal(first.children[0].children[0].characters, 'Prepared editable text');
  const count = h.creationCount; await h.send({type: 'import', packets}); assert.equal(h.creationCount, count); assert.ok(h.last().evidence.packets.every(p => p.status === 'reused')); assert.equal(h.removed.length, 0); assert.equal(h.nodes.get('15:3').removed, false); assert.equal(h.original.removed, false);
});
test('an unmarked homonymous section refuses overwrite and duplicate creation', async () => {
  const packets = queue(), h = harness(packets); h.nodes.get('19:2').name = packets[1].name;
  await h.send({type: 'import', packets}); untouched(h); assert.match(h.last().message, /incomplète|sans identité/);
});
test('a renamed section with stripped plugin data is still detected by its actual roots', async () => {
  const packets = queue(), h = harness(packets); await h.send({type: 'import', packets}); const section = h.nodes.get(h.last().evidence.packets[1].sectionId); section.name = 'Renamed by user'; section.data = {};
  const count = h.creationCount; await h.send({type: 'import', packets}); assert.equal(h.creationCount, count); assert.match(h.last().message, /hors de sa section/); assert.equal(section.removed, false);
});
test('a later native error rolls back only its own section, keeps earlier complete sections, and exports failure IDs', async () => {
  const packets = queue(), h = harness(packets); packets[2].svgs = ['<svg id="native-failure"/>']; packets[2].nodes = [[2, 0, 0, 20, 20, 'native failure', 0, 1, null, []]]; packets[2].frames.forEach(frame => frame.texts = 0);
  await h.send({type: 'import', packets}); const report = h.last().evidence;
  assert.equal(report.status, 'failed'); assert.match(report.error, /Simulated/); assert.equal(report.packets.length, 2); assert.ok(report.rolledBackSectionId); assert.ok(report.rolledBackNodeIds.includes(report.rolledBackSectionId)); assert.equal(h.nodes.get(report.rolledBackSectionId).removed, true); assert.equal(h.nodes.get(report.packets[1].sectionId).removed, false); assert.equal(h.nodes.get('26:2').removed, false); assert.equal(h.nodes.get('15:3').removed, false); assert.equal(h.original.removed, false); assert.equal(h.page.children.some(node => node.name === packets[2].name), false);
  await h.send({type: 'cleanup'}); assert.match(h.last().message, /Nettoyage refusé/); assert.equal(h.nodes.get('15:3').removed, false);
});
test('cancel yields between states, rolls back active section and retains a previous complete packet', async () => {
  const packets = queue(); let requested = false;
  const h = harness(packets, {onMessage(message, api) {if (!requested && message.message.startsWith('Lot 3/94')) {requested = true; api.send({type: 'cancel'});}}});
  await h.send({type: 'import', packets}); const report = h.last().evidence; assert.equal(report.status, 'cancelled'); assert.equal(report.packets.length, 2); assert.equal(h.nodes.get(report.rolledBackSectionId).removed, true); assert.equal(h.nodes.get(report.packets[1].sectionId).removed, false); assert.equal(h.nodes.get('26:2').removed, false); assert.equal(h.original.removed, false);
});
test('failed append also removes its newly created orphan without touching existing nodes', async () => {
  const packets = queue(), h = harness(packets, {failTextAppend: true}); await h.send({type: 'import', packets}); assert.equal(h.last().evidence.status, 'failed'); assert.match(h.last().message, /append failure/); assert.ok([...h.nodes.values()].filter(node => node.id.startsWith('new:')).every(node => node.removed)); assert.equal(h.nodes.get('26:2').removed, false); assert.equal(h.original.removed, false);
});
test('bad PNG data fails before canvas nodes and images use local decoded bytes', async () => {
  const sha = 'b'.repeat(64), png = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10, ...Array(24).fill(0)]).toString('base64');
  for (const [base64, badImage] of [[Buffer.from('invalid').toString('base64'), false], [png, true]]) {
    const packets = queue(), h = harness(packets, {badImage}); packets[93].nodes = [[3, 0, 0, 100, 100, 'Preview only', sha, 1, null]]; packets[93].imageData = {[sha]: base64}; packets[93].frames.forEach(f => f.texts = 0); await h.send({type: 'import', packets}); untouched(h); assert.equal(h.last().evidence.status, 'failed');
  }
  const packets = queue(), h = harness(packets); packets[93].nodes = [[3, 0, 0, 100, 100, 'Preview only', sha, 1, null]]; packets[93].imageData = {[sha]: png}; packets[93].frames.forEach(f => f.texts = 0); await h.send({type: 'import', packets}); assert.equal(h.last().evidence.status, 'complete'); const image = [...h.nodes.values()].find(node => node.name === 'Preview only'); assert.equal(image.fills[0].imageHash, 'local-image-hash');
});
test('corrected native renderer preserves multiline decoration, editable vectors, clipping and zero-blur outlines', async () => {
  const packets = queue(), h = harness(packets), packet = packets[93];
  packet.svgs = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"><path d="M0 0L20 20"/></svg>'];
  packet.nodes = [
    [1, 12, 18, 200, 44, 'Line one\nLine two', 0, 14, 22, 0, 0, {line: 'underline', style: 'wavy', color: {r: 1, g: 0.25, b: 0, a: 0.75}, thickness: 1.5, offset: 3}, true],
    [2, 240, 20, 20, 20, 'Editable icon', 0, 1, 'S-06/nora', []],
    [1, 12, 70, 200, 20, 'Editable strikethrough', 0, 14, 22, 0, 0, {line: 'line-through', style: 'solid', color: packet.colors[0], thickness: 1.5, offset: 0}, false],
    [0, 10, 20, 400, 300, 'Measured scroll body', 0, 8, true, 1, [], null, [0, 1, 2], null, [{type: 'DROP_SHADOW', color: packet.colors[0], offset: {x: 0, y: 0}, radius: 0, spread: 1, visible: true, blendMode: 'NORMAL'}]]];
  packet.frames.forEach(frame => {frame.node = 3; frame.texts = 2;}); await h.send({type: 'import', packets}); assert.equal(h.last().evidence.status, 'complete');
  const body = [...h.nodes.values()].find(node => node.name === 'Measured scroll body'), text = body.children.find(node => node.type === 'TEXT'), icon = body.children.find(node => node.name === 'Editable icon');
  assert.equal(body.clipsContent, true); assert.equal(body.strokeWeight, 1); assert.equal(body.strokeAlign, 'OUTSIDE'); assert.equal(body.effects.length, 0); assert.equal(text.characters, 'Line one\nLine two'); assert.equal(text.lineHeight.value, 22); assert.equal(text.textAutoResize, 'HEIGHT'); assert.equal(text.textDecorationStyle, 'WAVY'); assert.equal(text.textDecorationThickness.value, 1.5); assert.equal(text.textDecorationColor.value.type, 'SOLID'); assert.equal(text.textDecorationColor.value.color.r, 1); assert.equal(text.textDecorationColor.value.opacity, 0.75); assert.equal(icon.children[0].type, 'VECTOR'); assert.equal(h.last().evidence.packets[93].links[0].target, 'S-06/nora'); assert.deepEqual(h.nodes.get('26:2').data, {});
  const strike = body.children.find(node => node.characters === 'Editable strikethrough');
  assert.equal(strike.textDecoration, 'STRIKETHROUGH');
  for (const property of ['textDecorationStyle', 'textDecorationColor', 'textDecorationThickness', 'textDecorationOffset']) assert.equal(strike[property], undefined);
  const links = h.last().evidence.packets[93].links; await h.send({type: 'import', packets}); const reused = h.last().evidence.packets[93].links; assert.equal(reused.length, links.length); assert.equal(reused[0].state, links[0].state); assert.equal(reused[0].target, links[0].target); assert.deepEqual(reused[0].tuplePath, links[0].tuplePath); assert.deepEqual(reused[0].childPath, links[0].childPath); assert.equal(reused[0].nativeNodeResolution, 'source-only-on-reuse');
});
test('cleanup refuses before complete set and revalidates actual roots before deleting anything', async () => {
  const packets = queue(), h = harness(packets); await h.send({type: 'cleanup'}); untouched(h); assert.match(h.last().message, /Nettoyage refusé/);
  await h.send({type: 'import', packets}); const report = h.last().evidence; h.nodes.get(report.packets[93].roots[0].nodeId).height = 50; await h.send({type: 'cleanup'}); assert.equal(h.nodes.get('15:3').removed, false); assert.equal(h.nodes.get('19:2').removed, false); assert.match(h.last().message, /Propriétés natives/);
});
test('cleanup guards all IDs, names, parents and image hashes before first deletion', async () => {
  for (const mutate of [h => h.nodes.get('22:11').name = 'Renamed', h => h.foundations.appendChild(h.nodes.get('22:11')), h => h.nodes.get('image-22:11').fills[0].imageHash = 'different-image']) {
    const packets = queue(), h = harness(packets); await h.send({type: 'import', packets}); mutate(h); await h.send({type: 'cleanup'}); assert.match(h.last().message, /identité ou inventaire natif modifié/); assert.equal(h.nodes.get('15:3').removed, false); assert.equal(h.nodes.get('19:2').removed, false); assert.equal(h.original.removed, false);
  }
  const packets = queue(), h = harness(packets); packets.forEach(p => p.cleanupCandidates[0].id = '2:74'); await h.send({type: 'import', packets}); untouched(h); assert.match(h.last().message, /non autorisée/);
});
test('adding a child or moving an old-trial descendant blocks the whole cleanup set', async () => {
  for (const mutate of [h => h.make('TEXT', 'new-old-trial-child', h.nodes.get('15:3'), {name: 'Added after snapshot', characters: 'Preserve me', fontName: FONT, fontSize: 14}), h => h.nodes.get('image-22:11').x++]) {
    const packets = queue(), h = harness(packets); await h.send({type: 'import', packets}); mutate(h); await h.send({type: 'cleanup'}); assert.match(h.last().message, /inventaire natif modifié/); assert.ok(CLEANUP.every(candidate => !h.nodes.get(candidate.id).removed)); assert.equal(h.original.removed, false);
  }
});
test('cleanup rechecks both replacement and old trees after its final asynchronous lookup', async () => {
  for (const modifyReplacement of [false, true]) {
    const packets = queue(), h = harness(packets); await h.send({type: 'import', packets}); const replacement = h.nodes.get(h.last().evidence.packets[1].sectionId), lookup = h.figma.getNodeByIdAsync;
    h.figma.getNodeByIdAsync = async id => {const node = await lookup(id); if (id === '22:11') {if (modifyReplacement) replacement.children[0].children[0].x++; else h.make('TEXT', 'concurrent-old-child', h.nodes.get('15:3'), {characters: 'Added during lookup', fontName: FONT, fontSize: 14});} return node;};
    await h.send({type: 'cleanup'}); assert.match(h.last().message, /modifié/); assert.ok(CLEANUP.every(candidate => !h.nodes.get(candidate.id).removed));
  }
});
test('asymmetric dashed borders preserve gaps, edge position, width and final segment bounds', async () => {
  const packets = queue(), h = harness(packets), packet = packets[93]; packet.nodes = [[0, 0, 0, 23, 31, 'Asymmetric dashed boundary', 0, 0, false, 1, [[0, 2, 0, 'dashed'], [1, 1, 0, 'solid'], [2, 3, 0, 'dashed']], null, [], null, []]]; packet.frames.forEach(frame => {frame.node = 0; frame.texts = 0;});
  await h.send({type: 'import', packets}); assert.equal(h.last().evidence.status, 'complete'); const body = [...h.nodes.values()].find(node => node.name === 'Asymmetric dashed boundary'), top = body.children.filter(node => node.name.startsWith('Border 0')), bottom = body.children.filter(node => node.name.startsWith('Border 2'));
  assert.deepEqual(top.map(node => [node.x, node.y, node.width, node.height]), [[0, 0, 6, 2], [12, 0, 6, 2]]); assert.deepEqual(bottom.map(node => [node.x, node.y, node.width, node.height]), [[0, 28, 9, 3], [18, 28, 5, 3]]); assert.ok(top[1].x > top[0].x + top[0].width); const right = body.children.find(node => node.name === 'Border 1'); assert.equal(right.x, 22); assert.equal(right.height, 31);
});
test('cleanup reconciles missing captured image hash only against the exact authorized hash', async () => {
  for (const changed of [false, true]) {
    const packets = queue(), h = harness(packets);
    const candidate = packets[0].cleanupCandidates.find(item => item.id === '22:2');
    const reference = candidate.nativeInventory.find(item => item.fills.some(fill => fill[0] === 'IMAGE'));
    reference.fills[0][6] = null;
    await h.send({type: 'import', packets});
    if (changed) h.nodes.get(reference.id).fills[0].imageHash = 'changed-image';
    await h.send({type: 'cleanup'});
    assert.equal(h.nodes.get('22:2').removed, !changed);
    assert.equal(h.original.removed, false);
  }
});
test('eligible cleanup deletes only authorized old trees after all 456 states verify', async () => {
  const packets = queue(), h = harness(packets); await h.send({type: 'import', packets}); const sections = h.last().evidence.packets.map(packet => packet.sectionId); await h.send({type: 'cleanup'});
  assert.equal(h.last().evidence.cleanup.executed, true); assert.equal(h.last().evidence.completeExpectedStateSet, true); assert.ok(CLEANUP.every(candidate => h.nodes.get(candidate.id).removed)); assert.ok(sections.every(id => !h.nodes.get(id).removed)); assert.equal(h.original.removed, false); assert.equal(h.foundations.removed, false); assert.equal(h.components.removed, false);
  await h.send({type: 'cleanup'}); assert.deepEqual(h.last().evidence.cleanup.removedNodeIds, []);
});
test('UI accepts multiple packets and exposes actual downloadable JSON evidence', async () => {
  const html = fs.readFileSync(__dirname + '/ui.html', 'utf8'), source = html.match(/<script>([\s\S]*?)<\/script>/)[1], elements = {}, sent = [], revoked = [], blobs = [];
  const get = id => elements[id] ||= {disabled: false, hidden: true, removeAttribute(key) {delete this[key];}};
  const editor = {}, parent = {parent: {parent: editor}, postMessage: message => sent.push(message)}, context = {document: {getElementById: get}, parent, Blob,
    URL: {createObjectURL(blob) {blobs.push(blob); return 'blob:' + blobs.length;}, revokeObjectURL(url) {revoked.push(url);}}};
  vm.createContext(context); vm.runInContext(source, context); get('files').files = queue().map(packet => ({text: async () => JSON.stringify(packet)})); await get('files').onchange(); assert.equal(get('import').disabled, false); get('import').onclick(); assert.equal(sent[0].pluginMessage.packets.length, 94);
  const evidence = {status: 'complete', roots: [{state: 'S-06/nora', nodeId: '26:1282'}]}; const response = {pluginMessage: {message: 'Actual evidence', done: true, evidence, cleanupReady: true}}; context.onmessage({source: {}, data: response}); assert.equal(get('evidence').hidden, true); context.onmessage({source: editor, data: response}); assert.equal(get('evidence').hidden, false); assert.match(get('evidence').download, /\.json$/); assert.deepEqual(JSON.parse(await blobs[0].text()), evidence); assert.equal(get('cleanup').disabled, false);
  get('files').files = []; await get('files').onchange(); assert.deepEqual(revoked, ['blob:1']); assert.equal(get('evidence').hidden, true); assert.equal(get('import').disabled, true); assert.equal(get('cleanup').disabled, true);
});

'use strict';

const SECTION = 'Étaye · Fond papier · S-01, S-02 et S-25 · revue 1440';
const VERSION = 'Étaye — fond papier sur les écrans d’entrée — revue desktop';
const PAPER_KEY = 'etayeEntryBackgroundSnapshot';
const FAMILIES = {'S-01': 4, 'S-02': 9, 'S-25': 7};
const TITLE_INSET = 24;
const TITLE_GROWTH = TITLE_INSET * 2;

function expect(condition, detail) {
  if (!condition) throw new Error(detail);
}

function screenNodes(root) {
  const body = root.children.find(node => node.name === 'body.etaye-root');
  const shell = body?.children.find(node => node.name.startsWith('div.etaye-root.etaye-'));
  const main = shell?.children.find(node => node.name === 'main#contenu');
  expect(body?.type === 'FRAME' && shell?.type === 'FRAME' && main?.type === 'FRAME',
    'Structure d’écran différente : ' + root.name);
  return {shell, main};
}

function preflightPlacement(root, state) {
  const {shell, main} = screenNodes(root);
  if (state.type === 'patient-heading') {
    const [title, ...following] = main.children;
    expect(title?.type === 'FRAME' && title.children.length === 1 && title.children[0].type === 'FRAME'
      && title.children[0].name === 'h1.' && Math.abs(title.width - state.width) < .01
      && Math.abs(title.height + TITLE_GROWTH - state.height) < .01
      && following.every(node => node.y + TITLE_GROWTH + node.height <= main.height + .01),
    'Dimensions du bandeau différentes : ' + root.name);
    return {host: title, following};
  }
  expect(Math.abs(shell.width - state.width) < .01 && Math.abs(shell.height - state.height) < .01,
    'Dimensions du fond différentes : ' + root.name);
  const document = state.type === 'showcase' ? main.findAll(node => node.name === 'div.etaye-document') : [];
  expect(state.type !== 'showcase' || document.length === 1, 'Extrait de démonstration introuvable : ' + root.name);
  return {host: shell, document: document[0]};
}

async function checkSources(packet, page) {
  const originals = [];
  const protectedPages = [];
  for (const protectedPage of packet.scope.protectedPages) {
    const node = await figma.getNodeByIdAsync(protectedPage.id);
    expect(node?.type === 'PAGE', 'Page protégée introuvable : ' + protectedPage.id);
    await node.loadAsync();
    protectedPages.push({node, expected: protectedPage.fingerprint});
  }
  for (const item of packet.states) {
    const root = await figma.getNodeByIdAsync(item.root.nodeId);
    expect(root?.type === 'FRAME' && root.parent?.type === 'SECTION' && root.parent?.id === item.root.sectionId
      && root.parent.parent?.id === page.id && root.name === item.root.name
      && root.width === item.root.width && root.height === item.root.height,
    'Identité du cadre différente : ' + item.root.state);
    expect(!root.findAll(node => node.type === 'TEXT' && node.hasMissingFont).length,
      'Installer les polices du cadre : ' + item.root.state);
    preflightPlacement(root, item);
    originals.push({root, item});
  }
  assertUnchanged(originals, protectedPages);
  return {originals, protectedPages};
}

function assertUnchanged(originals, protectedPages) {
  for (const {root, item} of originals)
    expect(nativeFingerprint(root) === item.root.fingerprint, 'Cadre source différent : ' + item.root.state);
  for (const {node, expected} of protectedPages)
    expect(nativeFingerprint(node) === expected, 'Page protégée différente : ' + node.name);
}

async function run(packet) {
  expect(figma.editorType === 'figma' && figma.fileKey === packet?.scope?.fileKey
    && packet.scope.pageId === '15:2' && packet.scope.finalVersion?.id === '2423224260331333575'
    && /^[a-f0-9]{64}$/.test(packet.scope.sourceSnapshot),
  'Ouvrir le fichier Étaye utilisé pour le lot de référence.');
  expect(packet.states?.length === 20 && new Set(packet.states.map(item => item.root.nodeId)).size === 20
    && new Set(packet.states.map(item => item.root.state)).size === 20,
  'Le lot doit contenir les 20 états distincts.');
  expect(packet.scope.protectedPages?.length === 2 && ['2:74', '2:75'].every(id =>
    packet.scope.protectedPages.some(page => page.id === id)), 'Pages protégées non reconnues.');
  for (const [family, count] of Object.entries(FAMILIES))
    expect(packet.states.filter(item => item.root.state.startsWith(family + '/')).length === count,
      'Famille incomplète : ' + family);

  const page = await figma.getNodeByIdAsync(packet.scope.pageId);
  expect(page?.type === 'PAGE', 'Page des maquettes importées introuvable.');
  await figma.setCurrentPageAsync(page);
  expect(!page.children.some(node => node.name === SECTION || node.getPluginData(PAPER_KEY)),
    'La section de revue existe déjà.');

  const {originals, protectedPages} = await checkSources(packet, page);
  const surface = await figma.variables.getVariableByIdAsync('VariableID:2:5');
  expect(surface?.name === 'surface/surface' && surface.resolvedType === 'COLOR',
    'Variable de surface claire introuvable.');
  const opaqueSurface = figma.variables.setBoundVariableForPaint(
    {type: 'SOLID', color: {r: 1, g: 1, b: 1}}, 'color', surface);
  const images = new Map();
  for (const {item} of originals) {
    if (!images.has(item.image)) {
      const source = packet.artwork[item.image];
      expect(typeof source === 'string' && source.startsWith('iVBORw0KGgo'), 'Image de fond illisible.');
      const image = figma.createImage(figma.base64Decode(source));
      const size = await image.getSizeAsync();
      images.set(item.image, {hash: image.hash, ...size});
    }
    const size = images.get(item.image);
    expect(size.width === item.width && size.height === item.height, 'Image de fond mal dimensionnée.');
  }
  assertUnchanged(originals, protectedPages);

  const destinationX = Math.max(0, ...page.children.map(node => node.x + node.width)) + 160;
  const clones = [];
  let section;
  try {
    section = figma.createSection();
    section.name = SECTION;
    section.x = destinationX;
    section.y = 80;
    section.setPluginData(PAPER_KEY, packet.scope.sourceSnapshot);
    const columns = {'S-25': 0, 'S-02': 1, 'S-01': 2};
    const nextY = {'S-25': 40, 'S-02': 40, 'S-01': 40};
    const evidence = [];
    for (const {root, item} of originals) {
      const clone = root.clone();
      clones.push(clone);
      section.appendChild(clone);
      const family = item.root.state.split('/')[0];
      clone.name = item.root.state + ' · fond papier · clair · à revoir';
      clone.x = 40 + columns[family] * 1480;
      clone.y = nextY[family];
      nextY[family] += clone.height + 40;

      const {host, following, document} = preflightPlacement(clone, item);
      if (following) {
        const heading = host.children[0];
        host.resizeWithoutConstraints(host.width, item.height);
        heading.x += TITLE_INSET;
        heading.y += TITLE_INSET;
        heading.resizeWithoutConstraints(host.width - TITLE_GROWTH, heading.height);
        for (const node of following) node.y += TITLE_GROWTH;
      }
      if (document) document.fills = [opaqueSurface];
      const paper = figma.createRectangle();
      host.insertChild(0, paper);
      paper.name = 'Fond papier et blueprint · décor';
      paper.resize(item.width, item.height);
      paper.x = 0;
      paper.y = 0;
      paper.fills = [{type: 'IMAGE', scaleMode: 'FILL', imageHash: images.get(item.image).hash}];
      paper.locked = true;
      expect(clone.findAll(node => node.type === 'TEXT').length === item.root.texts,
        'Texte modifiable manquant : ' + item.root.state);
      evidence.push({state: item.root.state, sourceId: root.id, reviewId: clone.id, artworkId: paper.id});
    }
    section.resizeWithoutConstraints(4480, Math.max(...Object.values(nextY)));
    for (const [index, frame] of clones.entries()) {
      const png = await frame.exportAsync({format: 'PNG', contentsOnly: true, useAbsoluteBounds: true,
        constraint: {type: 'SCALE', value: 1}});
      evidence[index].png = figma.base64Encode(png);
    }
    assertUnchanged(originals, protectedPages);
    const saved = await figma.saveVersionHistoryAsync(VERSION);
    expect(saved?.id, 'Figma n’a pas retourné la version enregistrée.');
    return {sectionId: section.id, version: saved.id, sourceVersion: packet.scope.finalVersion.id,
      sourceSnapshot: packet.scope.sourceSnapshot, states: evidence};
  } catch (error) {
    section?.remove();
    for (const clone of clones) if (!clone.removed) clone.remove();
    throw error;
  }
}

if (typeof __html__ !== 'undefined') {
  figma.showUI(__html__, {width: 420, height: 285});
  let busy = false;
  figma.ui.onmessage = async message => {
    if (message?.type !== 'import' || busy) return;
    busy = true;
    try {
      const evidence = await run(message.packet);
      figma.ui.postMessage({evidence, status: '20 variantes créées et exportées. Télécharge les preuves pour la revue.'});
    } catch (error) {
      figma.ui.postMessage({failed: true, status: error.message});
    } finally {
      busy = false;
    }
  };
}

if (typeof module !== 'undefined') module.exports = {run};

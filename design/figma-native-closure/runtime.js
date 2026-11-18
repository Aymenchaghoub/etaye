/* Targeted corrections keep original roots and immutable import fingerprints. */
const EDITABLE_PROPERTIES = new Set(['x', 'y', 'width', 'height', 'characters', 'textAutoResize', 'clipsContent', 'strokes', 'strokeWeight', 'strokeAlign']);
async function populateInlineFlowEdits(scope, plan, flows) {
  for (const flow of flows) {
    const target = plan.plans.find(p => p.rootId === flow.rootId && p.state === flow.state);
    const root = await figma.getNodeByIdAsync(flow.rootId);
    if (!target || !root || nativeFingerprint(root) !== target.beforeFingerprint) throw Error('Inline-flow baseline changed');
    const members = new Map(descendants(root).map(n => [n.id, n])), positions = new Map();
    for (const chain of flow.chains) {
      const parent = members.get(chain.parentId);
      if (!parent) throw Error('Inline-flow parent outside root');
      for (const segment of chain.segments) if (members.get(segment.id)?.parent !== parent) throw Error('Inline-flow sibling outside parent');
      for (let i = 1; i < chain.segments.length; i++) {
        const previous = chain.segments[i - 1], next = chain.segments[i];
        const a = members.get(previous.id), b = members.get(next.id);
        const gap = next.sourceX - previous.sourceX - previous.sourceWidth;
        if (!Number.isFinite(gap) || gap < -1 || gap > 20) throw Error('Invalid measured inline gap');
        // Auto-width text excludes trailing spaces; inline wrappers may be narrower than their text.
        const contentWidth = 'children' in a ? Math.max(a.width, ...a.children.map(c => c.x + c.width)) : a.width;
        const trailing = previous.trailingSpaceAdvance ?? 0;
        if (!Number.isFinite(trailing) || trailing < 0 || trailing > 20) throw Error('Invalid measured trailing space');
        const x = (positions.get(a.id) ?? a.x) + contentWidth + trailing + gap;
        positions.set(b.id, x);
      }
    }
    target.edits = [...positions].filter(([id, x]) => Math.abs(members.get(id).x - x) > 0.05)
      .map(([id, x]) => ({id, name: members.get(id).name, type: members.get(id).type, props: {x}}));
  }
}
async function applyCorrections(scope, plans) {
  if (figma.fileKey !== scope.fileKey || figma.currentPage.id !== scope.pageId) throw Error('Wrong file/page');
  const prepared = [], fonts = new Map();
  const evidence = {kind: 'native-visual-correction', startedAt: new Date().toISOString(), fileKey: scope.fileKey,
    pageId: scope.pageId, states: [], protectedBefore: [], protectedAfter: [], status: 'preflight', approved: false, applicationImplemented: false};
  for (const expected of scope.protectedPages) {
    const page = await figma.getNodeByIdAsync(expected.id);
    if (!page || page.type !== 'PAGE') throw Error('Missing protected page');
    await page.loadAsync();
    const fingerprint = nativeFingerprint(page);
    if (fingerprint !== expected.fingerprint) throw Error('Protected page changed before correction: ' + page.id);
    evidence.protectedBefore.push({id: page.id, fingerprint});
  }
  for (const plan of plans) {
    const root = await figma.getNodeByIdAsync(plan.rootId);
    const expected = scope.roots.find(r => r.nodeId === plan.rootId && r.state === plan.state);
    if (!expected || !root || root.type !== 'FRAME' || root.parent.id !== expected.sectionId || root.parent.parent.id !== scope.pageId || root.name !== expected.name) throw Error('Root identity changed: ' + plan.state);
    if (nativeFingerprint(root) !== plan.beforeFingerprint) throw Error('Root fingerprint changed: ' + plan.state);
    const nodes = descendants(root), members = new Map(nodes.map(n => [n.id, n]));
    for (const node of nodes) if (node.type === 'TEXT') fonts.set(fontKey(node.fontName), node.fontName);
    const edits = plan.edits.map(edit => {
      const node = members.get(edit.id);
      if (!node || node.type !== edit.type || node.name !== edit.name) throw Error('Edit outside verified root: ' + edit.id);
      for (const [key, value] of Object.entries(edit.props)) {
        if (!EDITABLE_PROPERTIES.has(key) || !(key in node)) throw Error('Unsupported edit: ' + key);
        if (['x', 'y', 'width', 'height', 'strokeWeight'].includes(key) && (!Number.isFinite(value) || (['width', 'height'].includes(key) && value <= 0))) throw Error('Invalid geometry');
        if (key === 'characters' && (node.type !== 'TEXT' || typeof value !== 'string')) throw Error('Invalid text edit');
      }
      return {node, props: edit.props, before: Object.fromEntries(Object.keys(edit.props).map(key => [key, node[key]]))};
    });
    const orders = new Map();
    for (const reorder of plan.reorder) {
      const parent = members.get(reorder.parentId), node = members.get(reorder.nodeId);
      if (!parent || !node || node.parent !== parent || !('children' in parent)) throw Error('Invalid sibling reorder');
      if (!orders.has(parent)) orders.set(parent, parent.children.slice());
    }
    for (const marker of plan.markers) {
      const parent = members.get(marker.parentId);
      if (!parent || !('children' in parent) || parent.children.some(n => n.name === marker.name)) throw Error('Invalid/duplicate marker');
    }
    for (const split of plan.splits ?? []) {
      const source = members.get(split.sourceId);
      if (!source || source.type !== 'TEXT' || source.parent.id !== split.parentId ||
          typeof split.characters !== 'string' || !Number.isFinite(split.x) || !Number.isFinite(split.y)) throw Error('Invalid text-line split');
    }
    const adoptions = [];
    for (const adoption of plan.adoptions ?? []) {
      const node = await figma.getNodeByIdAsync(adoption.id), source = members.get(adoption.sourceId), parent = members.get(adoption.parentId);
      if (!scope.adoptableTextIds?.includes(adoption.id) || !node || node.type !== 'TEXT' || node.parent !== figma.currentPage ||
          !source || source.type !== 'TEXT' || !parent || source.parent !== parent || node.characters !== adoption.characters ||
          node.name !== adoption.characters || node.x !== adoption.x || node.y !== adoption.y ||
          fontKey(node.fontName) !== fontKey(source.fontName) || node.fontSize !== source.fontSize) throw Error('Unverified created text adoption');
      adoptions.push({node, parent, x: adoption.x, y: adoption.y, fingerprint: nativeFingerprint(node), oldParent: node.parent, oldIndex: node.parent.children.indexOf(node)});
    }
    prepared.push({plan, root, edits, orders, members, adoptions});
  }
  for (const font of fonts.values()) await figma.loadFontAsync(font);
  // Font loading yields to the editor; recheck every target before the first write.
  for (const item of prepared) if (nativeFingerprint(item.root) !== item.plan.beforeFingerprint) throw Error('Concurrent target change');
  for (const item of prepared) for (const adoption of item.adoptions) if (adoption.node.parent !== adoption.oldParent || nativeFingerprint(adoption.node) !== adoption.fingerprint) throw Error('Concurrent created text change');
  const made = [], madeTexts = [], changed = [], adopted = [];
  const assign = (node, props) => {
    const {width, height, ...other} = props;
    if (width !== undefined || height !== undefined) node.resizeWithoutConstraints(width ?? node.width, height ?? node.height);
    if (other.textAutoResize !== undefined) node.textAutoResize = other.textAutoResize;
    for (const [key, value] of Object.entries(other)) if (key !== 'textAutoResize') node[key] = value;
  };
  try {
    for (const item of prepared) {
      for (const edit of item.edits) {changed.push(edit); assign(edit.node, edit.props);}
      for (const reorder of item.plan.reorder) item.members.get(reorder.parentId).appendChild(item.members.get(reorder.nodeId));
      for (const marker of item.plan.markers) {
        const node = figma.createEllipse(); made.push(node);
        item.members.get(marker.parentId).appendChild(node);
        node.name = marker.name; node.resize(marker.width, marker.height); node.x = marker.x; node.y = marker.y;
        node.fills = [{type: 'SOLID', color: marker.color}]; node.strokes = [];
      }
      for (const split of item.plan.splits ?? []) {
        const node = item.members.get(split.sourceId).clone(); made.push(node); madeTexts.push(node);
        item.members.get(split.parentId).appendChild(node);
        node.characters = split.characters; node.x = split.x; node.y = split.y;
        node.name = split.characters;
      }
      for (const adoption of item.adoptions) {
        adopted.push(adoption); adoption.parent.appendChild(adoption.node); adoption.node.x = adoption.x; adoption.node.y = adoption.y;
      }
      evidence.states.push({state: item.plan.state, rootId: item.root.id, beforeFingerprint: item.plan.beforeFingerprint,
        afterFingerprint: nativeFingerprint(item.root), editedNodeIds: item.edits.map(e => e.node.id), reorderedNodeIds: item.plan.reorder.map(r => r.nodeId),
        width: item.root.width, height: item.root.height});
      figma.ui.postMessage({status: evidence.states.length + '/' + plans.length + ' corrigés : ' + item.plan.state});
    }
    for (const expected of scope.protectedPages) {
      const page = await figma.getNodeByIdAsync(expected.id), fingerprint = nativeFingerprint(page);
      if (fingerprint !== expected.fingerprint) throw Error('Protected page changed during correction');
      evidence.protectedAfter.push({id: page.id, fingerprint});
    }
    evidence.createdMarkerIds = made.filter(n => n.type === 'ELLIPSE').map(n => n.id);
    evidence.createdTextIds = madeTexts.map(n => n.id);
    evidence.adoptedTextIds = adopted.map(a => a.node.id);
    evidence.status = 'corrected'; evidence.completedAt = new Date().toISOString();
    figma.commitUndo();
    return evidence;
  } catch (error) {
    for (const adoption of adopted.reverse()) {
      adoption.oldParent.insertChild(adoption.oldIndex, adoption.node); adoption.node.x = adoption.x; adoption.node.y = adoption.y;
    }
    for (const node of made) node.remove();
    for (const edit of changed.reverse()) assign(edit.node, edit.before);
    for (const item of prepared) for (const [parent, order] of item.orders) for (const node of order) parent.appendChild(node);
    throw Error('Correction failed; scoped edits rolled back: ' + error.message);
  }
}

figma.showUI(__html__, {width: 540, height: 360});
let active = false, result = null;
figma.ui.onmessage = async message => {
  if (active || !['apply', 'export', 'save'].includes(message.type)) return;
  active = true;
  try {
    if (message.type === 'apply') {
      if (typeof INLINE_FLOW_PLAN !== 'undefined') await populateInlineFlowEdits(SCOPE, PLAN, INLINE_FLOW_PLAN);
      result = await applyCorrections(SCOPE, PLAN.plans);
      figma.ui.postMessage({status: 'Corrections appliquées. Exportez les preuves.', evidence: result});
    } else if (message.type === 'export') {
      if (!result) throw Error('Apply the guarded correction first');
      result.exportComplete = false;
      result.observation = {observedAt: new Date().toISOString(), roots: [], protectedPages: result.protectedAfter,
        pages: figma.root.children.map(p => ({id: p.id, name: p.name})), topLevel: figma.currentPage.children.map(n => ({id: n.id, name: n.name, type: n.type}))};
      for (const expected of SCOPE.roots) {
        const root = await figma.getNodeByIdAsync(expected.nodeId);
        if (!root || root.parent.id !== expected.sectionId || root.name !== expected.name) throw Error('Root drift during export');
        const nodes = descendants(root), fingerprint = nativeFingerprint(root);
        const target = PLAN.plans.find(p => p.rootId === root.id);
        if (!target && fingerprint !== expected.fingerprint) throw Error('Untouched state drift: ' + expected.state);
        if (target && fingerprint !== result.states.find(s => s.rootId === root.id)?.afterFingerprint) throw Error('Corrected state drift since apply: ' + expected.state);
        result.observation.roots.push({state: expected.state, nodeId: root.id, sectionId: root.parent.id, name: root.name,
          width: root.width, height: root.height, texts: nodes.filter(n => n.type === 'TEXT').length, fingerprint});
        figma.ui.postMessage({status: result.observation.roots.length + '/456 revérifiés : ' + expected.state});
      }
      result.renders = [];
      for (const plan of PLAN.plans) {
        const root = await figma.getNodeByIdAsync(plan.rootId);
        const png = await root.exportAsync({format: 'PNG', constraint: {type: 'SCALE', value: 1}, contentsOnly: true, useAbsoluteBounds: true});
        result.renders.push({state: plan.state, nodeId: root.id, png: figma.base64Encode(png)});
        figma.ui.postMessage({status: result.renders.length + '/52 exports natifs : ' + plan.state});
      }
      result.exportComplete = true;
      figma.ui.postMessage({status: '52 exports et 456 états revérifiés.', evidence: result});
    } else {
      if (!result?.exportComplete || result.observation?.roots.length !== 456 || result.renders?.length !== 52) throw Error('Export before saving final version');
      result.finalVersion = await figma.saveVersionHistoryAsync('Etaye native visual corrections — 52 states — 2026-11-18');
      figma.ui.postMessage({status: 'Version enregistrée.', evidence: result});
    }
  } catch (error) {figma.ui.postMessage({status: String(error.stack || error), failed: true});}
  finally {active = false;}
};

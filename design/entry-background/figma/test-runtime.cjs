const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');

const script = fs.readFileSync(__dirname + '/runtime.js', 'utf8');

function fixture({failExport = false, failSave = false} = {}) {
  let nextId = 0;
  let writes = 0;
  let saves = 0;
  const nodes = new Map();

  function node(type, name, width, height, id = 'new:' + ++nextId) {
    const obj = {
      id, type, name, width, height, x: 0, y: 0, children: [], fingerprint: 'fingerprint-' + id,
      appendChild(child) { this.insertChild(this.children.length, child); },
      insertChild(index, child) {
        if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1);
        child.parent = this;
        this.children.splice(index, 0, child);
      },
      remove() {
        for (const child of this.children.slice()) child.remove();
        if (this.parent) this.parent.children.splice(this.parent.children.indexOf(this), 1);
        this.removed = true;
      },
      resize(w, h) { this.width = w; this.height = h; },
      resizeWithoutConstraints(w, h) { this.resize(w, h); },
      setPluginData(key, value) { this[key] = value; },
      getPluginData(key) { return this[key] || ''; },
      findAll(predicate) {
        return this.children.flatMap(child => [...(predicate(child) ? [child] : []), ...child.findAll(predicate)]);
      },
      clone() {
        const copy = node(this.type, this.name, this.width, this.height);
        copy.x = this.x;
        copy.y = this.y;
        for (const child of this.children) copy.appendChild(child.clone());
        if (this.parent) this.parent.appendChild(copy);
        return copy;
      },
      async loadAsync() {},
      async exportAsync() {
        if (failExport) throw new Error('export failed');
        return [1, 2, 3];
      },
    };
    nodes.set(id, obj);
    return obj;
  }

  const page = node('PAGE', 'Import', 0, 0, '15:2');
  const protectedPages = ['2:74', '2:75'].map(id => node('PAGE', id, 0, 0, id));
  const states = [];
  for (const [family, count, type] of [['S-01', 4, 'showcase'], ['S-02', 9, 'login'], ['S-25', 7, 'patient-heading']]) {
    const source = node('SECTION', family, 8600, 3000);
    page.appendChild(source);
    for (let index = 0; index < count; index++) {
      const root = node('FRAME', family + '/' + index, 1440, 1113);
      const body = node('FRAME', 'body.etaye-root', 1440, 1113);
      const shell = node('FRAME', 'div.etaye-root.etaye-shell', 1440, 1024);
      const main = node('FRAME', 'main#contenu', 720, 857);
      const headingHost = node('FRAME', 'div.', 680, 36);
      const heading = node('FRAME', 'h1.', 680, 36);
      const copy = node('TEXT', 'Label', 100, 30);
      const content = node('FRAME', type === 'showcase' ? 'div.etaye-document' : 'section.', 680, 100);
      source.appendChild(root);
      root.appendChild(body);
      body.appendChild(shell);
      shell.appendChild(main);
      main.appendChild(headingHost);
      main.appendChild(content);
      headingHost.appendChild(heading);
      heading.appendChild(copy);
      content.y = 84;
      states.push({
        type,
        width: type === 'patient-heading' ? 680 : 1440,
        height: type === 'patient-heading' ? 84 : 1024,
        image: type === 'patient-heading' ? 'heading' : 'shell',
        root: {state: root.name, nodeId: root.id, sectionId: source.id, name: root.name,
          fingerprint: root.fingerprint, width: root.width, height: root.height, texts: 1},
      });
    }
  }

  const figma = {
    editorType: 'figma', fileKey: 'test-file',
    variables: {
      async getVariableByIdAsync() { return {name: 'surface/surface', resolvedType: 'COLOR'}; },
      setBoundVariableForPaint(paint) { return paint; },
    },
    async getNodeByIdAsync(id) { return nodes.get(id); },
    async setCurrentPageAsync() {},
    createSection() { writes++; const section = node('SECTION', 'new', 100, 100); page.appendChild(section); return section; },
    createRectangle() { return node('RECTANGLE', 'new', 100, 100); },
    base64Decode(source) { return source; },
    base64Encode(bytes) { return Buffer.from(bytes).toString('base64'); },
    createImage(source) {
      return {hash: source, async getSizeAsync() {
        return source.endsWith('heading') ? {width: 680, height: 84} : {width: 1440, height: 1024};
      }};
    },
    async saveVersionHistoryAsync() {
      saves++;
      if (failSave) throw new Error('save failed');
      return {id: 'saved-version'};
    },
  };

  const packet = {scope: {fileKey: figma.fileKey, pageId: page.id, sourceSnapshot: 'a'.repeat(64),
    finalVersion: {id: '2423224260331333575'}, protectedPages: protectedPages.map(node => ({id: node.id, fingerprint: node.fingerprint}))},
  artwork: {heading: 'iVBORw0KGgoheading', shell: 'iVBORw0KGgoshell'}, states};
  const mod = {exports: {}};
  vm.runInNewContext(script, {module: mod, figma, nativeFingerprint: node => node.fingerprint});
  return {packet, page, nodes, run: mod.exports.run, writes: () => writes, saves: () => saves};
}

test('one run creates twenty editable variants and returns native evidence', async () => {
  const f = fixture();
  const result = await f.run(f.packet);
  assert.equal(f.writes(), 1);
  assert.equal(f.saves(), 1);
  assert.equal(result.states.length, 20);
  assert.equal(result.version, 'saved-version');
  assert.equal(f.page.children.length, 4);
  assert.ok(result.states.every(row => row.png === 'AQID' && row.reviewId !== row.sourceId));
  for (const row of result.states.filter(r => r.state.startsWith('S-25/'))) {
    const [, main] = f.nodes.get(row.reviewId).findAll(node => node.name === 'main#contenu' || node.name === 'h1.');
    assert.equal(main.width, 632);
    assert.equal(main.x, 24);
    assert.equal(main.y, 24);
  }
  await assert.rejects(f.run(f.packet), /existe déjà/);
  assert.equal(f.writes(), 1);
});

test('a changed native source or protected page stops before writing', async () => {
  for (const id of ['2:74', 'first state']) {
    const f = fixture();
    f.nodes.get(id === 'first state' ? f.packet.states[0].root.nodeId : id).fingerprint = 'changed';
    await assert.rejects(f.run(f.packet), /différente?/);
    assert.equal(f.writes(), 0);
    assert.equal(f.page.children.length, 3);
  }
});

test('incorrect asset dimensions stop before any review section exists', async () => {
  const f = fixture();
  const state = f.packet.states.find(item => item.type === 'patient-heading');
  state.image = 'shell';
  await assert.rejects(f.run(f.packet), /mal dimensionnée/);
  assert.equal(f.writes(), 0);
});

test('failures in export or save remove all new content', async () => {
  for (const [option, error] of [['failExport', /export failed/], ['failSave', /save failed/]]) {
    const f = fixture({[option]: true});
    await assert.rejects(f.run(f.packet), error);
    assert.equal(f.writes(), 1);
    assert.equal(f.page.children.length, 3);
    assert.equal(f.page.findAll(node => node.type === 'TEXT').length, 20);
  }
});

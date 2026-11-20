import { execFileSync } from 'node:child_process';

const trees = JSON.parse(execFileSync('corepack', ['pnpm', 'list', '--depth', 'Infinity', '--json'], {
  encoding: 'utf8',
}));
if (trees.length !== 1 || trees[0].name !== 'etaye-web') {
  throw new Error('Expected the etaye-web dependency graph');
}

const inlinePdfViewer = /^(?:pdfjs|pdfjs-dist|react-pdf|pdfobject|@react-pdf-viewer\/|@embedpdf\/)/;
function check(tree) {
  for (const dependencies of [tree.dependencies, tree.devDependencies, tree.optionalDependencies]) {
    for (const [name, dependency] of Object.entries(dependencies ?? {})) {
      if (inlinePdfViewer.test(name) || inlinePdfViewer.test(dependency.from ?? '')) {
        throw new Error(`Unexpected inline PDF viewer dependency: ${name}`);
      }
      check(dependency);
    }
  }
}
check(trees[0]);

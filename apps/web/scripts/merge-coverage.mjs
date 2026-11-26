import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const webRoot = path.resolve(import.meta.dirname, '..');
const require = createRequire(path.join(webRoot, 'package.json'));
const covRequire = createRequire(require.resolve('@vitest/coverage-v8'));
const { convert } = covRequire('ast-v8-to-istanbul');
const libCoverage = covRequire('@vitest/istanbul-lib-coverage');
const libReport = covRequire('@vitest/istanbul-lib-report');
const { parseAstAsync } = await import(require.resolve('vite'));

function normalizeCoveragePath(rawPath) {
  const withoutScheme = rawPath.startsWith('file://') ? rawPath.slice('file://'.length) : rawPath;
  const rel = path.isAbsolute(withoutScheme) ? path.relative(webRoot, withoutScheme) : withoutScheme;
  return rel.replaceAll('\\', '/');
}

function includeSource(relPath) {
  return relPath.startsWith('src/')
    && /\.(?:ts|tsx)$/.test(relPath)
    && !relPath.startsWith('src/api/generated/')
    && !/\.test\.(?:ts|tsx)$/.test(relPath)
    && !relPath.endsWith('.d.ts');
}

function extractInlineSourceMap(source) {
  const match = source.match(/\/\/# sourceMappingURL=data:application\/json[^,]*,([A-Za-z0-9+/=]+)/);
  if (!match) return undefined;
  return JSON.parse(Buffer.from(match[1], 'base64').toString('utf8'));
}

function writeReports(map, outDir) {
  mkdirSync(outDir, { recursive: true });
  const normalized = libCoverage.createCoverageMap({});
  for (const file of map.files()) {
    const fc = map.fileCoverageFor(file).toJSON();
    const clean = normalizeCoveragePath(fc.path || file);
    if (!includeSource(clean)) continue;
    fc.path = clean;
    normalized.merge({ [clean]: fc });
  }
  const ctx = libReport.createContext({ dir: outDir, coverageMap: normalized });
  libReport.create('lcovonly', { file: 'lcov.info' }).execute(ctx);
  libReport.create('json', { file: 'coverage-final.json' }).execute(ctx);
  libReport.create('text-summary').execute(ctx);
  writeFileSync(path.join(outDir, '.generated-at'), `${new Date().toISOString()}\n`, 'utf8');
}

async function collectRaw(rawDir, outDir, cleanRaw) {
  if (!existsSync(rawDir)) {
    throw new Error(`Missing raw browser coverage directory: ${rawDir}`);
  }
  const files = readdirSync(rawDir).filter((name) => name.endsWith('.json')).sort();
  if (files.length === 0) {
    throw new Error(`No browser coverage files found in ${rawDir}`);
  }
  const astCache = new Map();
  const map = libCoverage.createCoverageMap({});

  for (const name of files) {
    const entries = JSON.parse(readFileSync(path.join(rawDir, name), 'utf8'));
    for (const entry of entries) {
      let rel;
      try {
        rel = new URL(entry.url).pathname.slice(1).split('?')[0];
      } catch {
        continue;
      }
      if (!includeSource(rel)) continue;
      const abs = path.join(webRoot, rel);
      const code = entry.source ?? '';
      let parsed = astCache.get(code);
      if (!parsed) {
        parsed = {
          ast: await parseAstAsync(code),
          sourceMap: extractInlineSourceMap(code),
        };
        astCache.set(code, parsed);
      }
      const data = await convert({
        code,
        sourceMap: parsed.sourceMap,
        coverage: { url: pathToFileURL(abs).href, functions: entry.functions },
        ast: parsed.ast,
      });
      for (const [key, fc] of Object.entries(data)) {
        const clean = normalizeCoveragePath(key);
        if (!includeSource(clean)) continue;
        fc.path = clean;
        map.merge({ [clean]: fc });
      }
    }
  }

  writeReports(map, outDir);
  if (cleanRaw) {
    rmSync(rawDir, { recursive: true, force: true });
  }
}

function mergeJsonReports(outDir, inputFiles) {
  const map = libCoverage.createCoverageMap({});
  for (const file of inputFiles) {
    if (!existsSync(file)) {
      throw new Error(`Missing coverage JSON report: ${file}`);
    }
    const raw = JSON.parse(readFileSync(file, 'utf8'));
    for (const [key, fc] of Object.entries(raw)) {
      const clean = normalizeCoveragePath(fc.path || key);
      if (!includeSource(clean)) continue;
      fc.path = clean;
      map.merge({ [clean]: fc });
    }
  }
  writeReports(map, outDir);
}

const [, , command, ...rest] = process.argv;
if (command === 'collect') {
  const cleanRaw = rest.includes('--clean-raw');
  const positional = rest.filter((arg) => arg !== '--clean-raw');
  if (positional.length !== 2) {
    throw new Error('Usage: node scripts/merge-coverage.mjs collect <rawDir> <outDir> [--clean-raw]');
  }
  await collectRaw(path.resolve(positional[0]), path.resolve(positional[1]), cleanRaw);
} else if (command === 'merge') {
  if (rest.length < 2) {
    throw new Error('Usage: node scripts/merge-coverage.mjs merge <outDir> <coverage-final.json...>');
  }
  const [outDir, ...inputs] = rest;
  mergeJsonReports(path.resolve(outDir), inputs.map((item) => path.resolve(item)));
} else {
  throw new Error('Unknown command. Expected "collect" or "merge".');
}

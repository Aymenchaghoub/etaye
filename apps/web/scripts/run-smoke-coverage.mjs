import { execFileSync } from 'node:child_process';
import { existsSync, rmSync } from 'node:fs';
import path from 'node:path';

const webRoot = path.resolve(import.meta.dirname, '..');
const rawDir = path.join(webRoot, 'reports', 'smoke-coverage', 'raw');
const outDir = path.join(webRoot, 'reports', 'smoke-coverage');

if (existsSync(outDir)) {
  rmSync(outDir, { recursive: true, force: true });
}

const args = process.argv.slice(2);
const pnpmBin = process.platform === 'win32' ? 'corepack.cmd' : 'corepack';
execFileSync(pnpmBin, ['pnpm', 'exec', 'playwright', 'test', ...args], {
  cwd: webRoot,
  stdio: 'inherit',
  env: { ...process.env, ETAYE_BROWSER_COVERAGE_DIR: rawDir },
});

execFileSync(process.execPath, [path.join(webRoot, 'scripts', 'merge-coverage.mjs'), 'collect', rawDir, outDir, '--clean-raw'], {
  cwd: webRoot,
  stdio: 'inherit',
});

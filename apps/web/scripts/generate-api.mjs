import { mkdtemp, readdir, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createClient } from '@hey-api/openapi-ts';
import config from '../openapi.config.mjs';

const pathsUnder = async (dir) => (await readdir(dir, { recursive: true, withFileTypes: true }))
  .filter((entry) => entry.isFile())
  .map((entry) => join(entry.parentPath, entry.name).slice(dir.length + 1))
  .sort();

if (process.argv.includes('--check')) {
  const temporary = await mkdtemp(join(tmpdir(), 'etaye-openapi-'));
  try {
    await createClient({ ...config, output: { ...config.output, path: temporary } });
    const expected = new Set(await pathsUnder(temporary));
    const committed = new Set(await pathsUnder(config.output.path));
    const different = [];
    for (const path of expected.union(committed)) {
      if (!expected.has(path) || !committed.has(path)) {
        different.push(path);
        continue;
      }
      const current = await readFile(join(config.output.path, path));
      if (!current.equals(await readFile(join(temporary, path)))) different.push(path);
    }
    if (different.length) {
      throw new Error(`Generated API drift in ${different.join(', ')}. Run generate:api.`);
    }
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
} else {
  await createClient(config);
}

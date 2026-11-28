import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { expect, type Page } from '@playwright/test';

export const fixturePassword = 'Etaye-Jeu-Fictif-91!';

export function fixtureId(alias: string) {
  const hash = createHash('md5').update(`etaye-design-v1/${alias}`).digest('hex');
  return [hash.slice(0, 8), hash.slice(8, 12), hash.slice(12, 16), hash.slice(16, 20), hash.slice(20)].join('-');
}

export function practicePath(membership: string) {
  return `/compte/pratiques/${fixtureId(membership)}`;
}

export function demoCompose(args: string[], input?: string) {
  const file = fileURLToPath(new URL('../../../infra/demo/compose.yml', import.meta.url));
  return execFileSync('docker', ['compose', '-f', file, ...args], {
    encoding: 'utf8', input, timeout: 10_000,
  }).trim();
}

export async function signIn(page: Page, email: string, password = fixturePassword) {
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page).not.toHaveURL(/\/connexion(?:\/patient)?(?:\?|$)/);
}

export function heading(page: Page, name: string) {
  return page.getByRole('heading', { name, level: 1, exact: true });
}

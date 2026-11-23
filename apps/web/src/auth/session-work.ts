import type { Session } from '../api/generated/types.gen';
import { isIsolationPath } from '../isolation/isolation-journey';

export const ACCOUNT_PATH = '/compte';
const uuid = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const grantPath = new RegExp(`^/compte/(pratiques|patient)/(${uuid})$`);

export function isAccountPath(path: string): boolean {
  return path === ACCOUNT_PATH || grantPath.test(path);
}

export function allowsAccountPath(session: Session, path: string): boolean {
  if (path === ACCOUNT_PATH) return true;
  // The journey steps are client routes only: the record read they perform is authorized by the server each time.
  if (isIsolationPath(path)) return true;
  const match = grantPath.exec(path);
  if (!match) return false;
  return (match[1] === 'pratiques' ? session.membershipIds : session.portalLinkIds).includes(match[2]);
}

type PausedWork<T> = {
  accountId: string;
  path: string;
  value: T;
};

export function pauseWork<T>(session: Session, path: string, value: T): PausedWork<T> | undefined {
  if (!allowsAccountPath(session, path)) return undefined;
  return { accountId: session.account.id, path, value };
}

export function checkPausedWork<T>(paused: PausedWork<T>, session: Session, path: string) {
  if (paused.accountId !== session.account.id) return { status: 'account-changed' } as const;
  if (paused.path !== path || !allowsAccountPath(session, path)) return { status: 'grant-changed' } as const;
  return { status: 'restored', value: paused.value } as const;
}

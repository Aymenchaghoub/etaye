import { version } from '../../package.json';
import { createClient } from './generated/client';
import { acceptInvitation, completeRecovery, inspectInvitation, getCsrfToken, getReadiness, getSession, requestRecovery, signIn, signOut } from './generated/sdk.gen';
import type { ActivationWritable, CredentialsWritable } from './generated/types.gen';

type AccountResult<T> =
  | { ok: true; data: T }
  | { ok: false; status: number; code?: string; retryAfter: number };

type AccountResponse<T> = { data?: T; error?: unknown; response?: Response };

function accountResult<T>(result: AccountResponse<T>): AccountResult<T> {
  if (result.response?.ok) return { ok: true, data: result.data as T };
  const header = Number(result.response?.headers.get('Retry-After'));
  const retryAfter = Number.isFinite(header) && header > 0 ? Math.ceil(header) : 0;
  const code = (result.error as { code?: string } | undefined)?.code;
  return { ok: false, status: result.response?.status ?? 0, code, retryAfter };
}

type BrowserTransport = { fetch: typeof fetch };
const browserTransport: BrowserTransport = {
  fetch: (input, init) => globalThis.fetch(input, init),
};

export function createBrowserApi(origin: string, transportEnabled: boolean, transport = browserTransport) {
  const send = transportEnabled ? transport.fetch : browserTransport.fetch;
  const client = createClient({
    baseUrl: `${origin}/api/v1`,
    credentials: 'same-origin',
    redirect: 'error',
    headers: { 'X-Client-Version': `web/${version}` },
    fetch: send,
  });

  function readSession(signal?: AbortSignal) {
    return accountRequest((deadline) => getSession({ client, signal: deadline, cache: 'no-store' }), signal);
  }

  async function accountRequest<T>(
    call: (signal: AbortSignal) => Promise<AccountResponse<T>>, signal?: AbortSignal,
  ): Promise<AccountResult<T>> {
    const timeout = AbortSignal.timeout(6_000);
    const deadline = signal ? AbortSignal.any([signal, timeout]) : timeout;
    try {
      return accountResult(await call(deadline));
    } catch {
      return { ok: false, status: 0, retryAfter: 0 };
    }
  }

  async function withCsrf<T>(
    write: (headers: { 'X-CSRF-TOKEN': string }, signal: AbortSignal) => Promise<AccountResponse<T>>,
    signal?: AbortSignal,
  ): Promise<AccountResult<T>> {
    const token = await accountRequest((deadline) => getCsrfToken({ client, signal: deadline, cache: 'no-store' }), signal);
    if (!token.ok) return token;
    return accountRequest((deadline) => write({ 'X-CSRF-TOKEN': token.data.token }, deadline), signal);
  }

  return {
    readSession,
    signIn(body: CredentialsWritable, signal?: AbortSignal) {
      return withCsrf((headers, deadline) => signIn({ client, headers, body, signal: deadline, cache: 'no-store' }), signal);
    },
    signOut(signal?: AbortSignal) {
      return withCsrf((headers, deadline) => signOut({ client, headers, signal: deadline, cache: 'no-store' }), signal);
    },
    requestRecovery(email: string, key: string, signal?: AbortSignal) {
      return withCsrf((csrf, deadline) => requestRecovery({
        client, headers: { ...csrf, 'Idempotency-Key': key }, body: { email }, signal: deadline, cache: 'no-store',
      }), signal);
    },
    resetPassword(token: string, newPassword: string, key: string, signal?: AbortSignal) {
      return withCsrf((csrf, deadline) => completeRecovery({
        client, headers: { ...csrf, 'Idempotency-Key': key }, body: { token, newPassword },
        signal: deadline, cache: 'no-store',
      }), signal);
    },
    inspectInvitation(token: string, signal?: AbortSignal) {
      return withCsrf((headers, deadline) => inspectInvitation({
        client, headers, body: { token }, signal: deadline, cache: 'no-store',
      }), signal);
    },
    acceptInvitation(body: ActivationWritable, key: string, signal?: AbortSignal) {
      return withCsrf((headers, deadline) => acceptInvitation({
        client, headers: { ...headers, 'Idempotency-Key': key }, body, signal: deadline, cache: 'no-store',
      }), signal);
    },
    async isReady(signal: AbortSignal, onHttpFailure: () => void): Promise<boolean> {
      const { data, response } = await getReadiness({
        client,
        signal,
        cache: 'no-store',
        fetch: async (input, init) => {
          const received = await send(input, init);
          // The SDK resolves after reading the body, which can stall even after an HTTP error.
          if (!received.ok) onHttpFailure();
          return received;
        },
      });
      return response?.ok === true && data?.status === 'UP';
    },
  };
}

export function isApiReady(signal: AbortSignal, onHttpFailure: () => void): Promise<boolean> {
  const transportEnabled = import.meta.env.VITE_ETAYE_MULTI_CLIENT_TRANSPORT === 'true';
  return createBrowserApi(window.location.origin, transportEnabled).isReady(signal, onHttpFailure);
}

export const accountApi = createBrowserApi(window.location.origin, false);

import { version } from '../../package.json';
import { createClient } from './generated/client';
import { getReadiness } from './generated/sdk.gen';

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

  return {
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

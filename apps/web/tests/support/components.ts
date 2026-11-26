import { vi } from 'vitest';

export function deferred<T>() {
  let complete!: (value: T) => void;
  const promise = new Promise<T>((resolve) => { complete = resolve; });
  return { promise, resolve: complete };
}

export function stubAccountRequest(paths: string[], respond: (request: Request) => Response | Promise<Response>) {
  const fetch = vi.fn((request: Request) => {
    const path = new URL(request.url).pathname;
    if (path === '/api/v1/csrf' && request.method === 'GET') {
      return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'component-test-token' });
    }
    if (paths.includes(path) && request.method === 'POST') return respond(request);
    throw new Error(`Unexpected request: ${request.method} ${path}`);
  });
  vi.stubGlobal('fetch', fetch);
  return fetch;
}

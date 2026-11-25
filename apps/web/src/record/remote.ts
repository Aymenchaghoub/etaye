import { useCallback, useEffect, useRef, useState } from 'react';
import type { AccountResult } from '../api/browser';

/** One independently loaded panel. A failure of one panel never empties another (C9). */
export type Remote<T> =
  | { phase: 'loading' }
  | { phase: 'ready'; data: T }
  | { phase: 'failed' }
  | { phase: 'limited'; wait: number }
  | { phase: 'forbidden' }
  | { phase: 'hidden' };

/**
 * Loads one resource when `key` changes and again on retry. A 401 ends the session through `expire`; any other refusal
 * keeps its own phase so the page can say honestly what happened instead of showing an empty success.
 */
export function useRemote<T>(
  load: (signal: AbortSignal) => Promise<AccountResult<T>>, key: string, expire: () => void,
): [Remote<T>, () => void] {
  const [held, setHeld] = useState<{ key: string; state: Remote<T> }>({ key, state: { phase: 'loading' } });
  const [attempt, setAttempt] = useState(0);
  const loader = useRef(load);
  loader.current = load;
  const expireRef = useRef(expire);
  expireRef.current = expire;
  useEffect(() => {
    const request = new AbortController();
    setHeld({ key, state: { phase: 'loading' } });
    void loader.current(request.signal).then((result) => {
      if (request.signal.aborted) return;
      setHeld({ key, state: outcome(result, () => expireRef.current()) });
    });
    return () => request.abort();
  }, [key, attempt]);
  // A result belongs to the key it was requested for: in the render that carries a new key, the old result is not shown.
  const state: Remote<T> = held.key === key ? held.state : { phase: 'loading' };
  return [state, useCallback(() => setAttempt((count) => count + 1), [])];
}

export function outcome<T>(result: AccountResult<T>, expire: () => void): Remote<T> {
  if (result.ok) return { phase: 'ready', data: result.data };
  if (result.status === 401) { expire(); return { phase: 'loading' }; }
  if (result.status === 403) return { phase: 'forbidden' };
  if (result.status === 404) return { phase: 'hidden' };
  if (result.status === 429) return { phase: 'limited', wait: result.retryAfter };
  return { phase: 'failed' };
}

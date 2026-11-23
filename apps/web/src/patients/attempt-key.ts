/** One idempotency key per distinct request body: a retry of the same body replays, a changed body is a new attempt. */
export function newIdempotencyKey(): string {
  return globalThis.crypto.randomUUID();
}

function canonical(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(canonical).sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .filter(([, item]) => item !== undefined)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, item]) => [key, canonical(item)]));
  }
  return value;
}

export class AttemptKey {
  private body?: string;
  private key?: string;

  constructor(private readonly generate: () => string = newIdempotencyKey) {}

  keyFor(body: unknown): string {
    const next = JSON.stringify(canonical(body));
    if (next !== this.body || !this.key) {
      this.body = next;
      this.key = this.generate();
    }
    return this.key;
  }

  reset() {
    this.body = undefined;
    this.key = undefined;
  }
}

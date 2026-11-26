import { describe, expect, it } from 'vitest';
import { hasContactEmailShape } from './contact-email';

describe('contact email shape', () => {
  it.each([
    'fiction@example.test',
    'a@example.test',
    'patient+essai@cabinet.exemple.test',
    'patïent@exemple.fr',
    'fiction@example.test.',
  ])('accepts the existing form shape for %s', (address) => {
    expect(hasContactEmailShape(address)).toBe(true);
  });

  it.each([
    '',
    '@example.test',
    'fiction',
    'fiction@example',
    'fiction@.test',
    'fiction@example.',
    'fiction@@example.test',
    'fiction@exam ple.test',
    'patient\u00a0test@example.test',
  ])('rejects incomplete or malformed addresses such as %s', (address) => {
    expect(hasContactEmailShape(address)).toBe(false);
  });
});

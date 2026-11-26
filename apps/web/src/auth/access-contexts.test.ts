import { describe, expect, it } from 'vitest';
import type { AccessContext, Session } from '../api/generated/types.gen';
import { contextPath, currentContexts, practiceLabel, roleLabel } from './access-contexts';

const M1 = '11111111-1111-4111-8111-111111111111';
const P1 = '22222222-2222-4222-8222-222222222222';
const PR1 = '33333333-3333-4333-8333-333333333333';
const PR2 = '44444444-4444-4444-8444-444444444444';
const P2 = '55555555-5555-4555-8555-555555555555';

const membership = (patch: Partial<AccessContext> = {}): AccessContext => ({
  kind: 'MEMBERSHIP',
  grantId: M1,
  practiceId: PR1,
  practiceName: 'Cabinet Voltaire',
  displayRole: 'CLINICAL',
  ...patch,
});

const portal = (grantId = P1, practiceId = PR1): AccessContext => ({
  kind: 'PORTAL',
  grantId,
  practiceId,
  practiceName: 'Cabinet Voltaire',
  displayRole: 'PATIENT',
});

const sessionWith = (patch: Partial<Session>): Session => ({
  account: { id: 'a', version: 0, createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z', displayName: 'Nora', email: 'nora@example.test', state: 'ACTIVE', locale: 'fr-FR' },
  membershipIds: [M1],
  portalLinkIds: [P1],
  expiresAt: '2026-11-26T12:00:00Z',
  contexts: [membership(), portal()],
  ...patch,
});

describe('access contexts', () => {
  it('builds membership and portal paths and validates matching session grants', () => {
    const m = membership();
    const p = portal();
    expect(contextPath(m)).toBe(`/compte/pratiques/${M1}`);
    expect(contextPath(p)).toBe(`/compte/patient/${P1}`);
    expect(currentContexts(sessionWith({}))).toEqual([m, p]);
  });

  it('rejects missing arrays, malformed fields, unknown grants, and mismatched leftovers', () => {
    expect(currentContexts(sessionWith({ contexts: undefined }))).toBeUndefined();
    expect(currentContexts(sessionWith({ contexts: [null as unknown as AccessContext] }))).toBeUndefined();
    expect(currentContexts(sessionWith({ contexts: [{ ...membership(), kind: 'OTHER' as AccessContext['kind'] }] }))).toBeUndefined();
    expect(currentContexts(sessionWith({
      membershipIds: [], portalLinkIds: [], contexts: [{ ...membership(), grantId: 'bad-uuid' }],
    }))).toBeUndefined();
    expect(currentContexts(sessionWith({
      portalLinkIds: [], contexts: [{ ...membership(), practiceId: 'bad' }],
    }))).toBeUndefined();
    expect(currentContexts(sessionWith({
      portalLinkIds: [], contexts: [{ ...membership(), displayRole: undefined as unknown as AccessContext['displayRole'] }],
    }))).toBeUndefined();
    expect(currentContexts(sessionWith({ portalLinkIds: [P1, P2] }))).toBeUndefined();
  });

  it('formats blank practice labels and all clinical/non-clinical/portal role labels', () => {
    expect(practiceLabel(membership({ practiceName: '   ' }))).toBe('Cabinet');
    expect(practiceLabel(membership({ practiceName: ' Cabinet Nord ' }))).toBe('Cabinet Nord');

    const singlePortal = portal(P1, PR1);
    const otherPracticePortal = portal(P2, PR2);
    expect(roleLabel(singlePortal, [singlePortal, otherPracticePortal])).toBe('Espace patient');

    const secondSamePractice = portal(P2, PR1);
    expect(roleLabel(singlePortal, [singlePortal, secondSamePractice])).toBe('Espace patient 1');
    expect(roleLabel(secondSamePractice, [singlePortal, secondSamePractice])).toBe('Espace patient 2');

    expect(roleLabel(membership({ displayRole: 'CLINICAL' }), [])).toBe('Praticien');
    expect(roleLabel(membership({ displayRole: 'ADMINISTRATION' }), [])).toBe('Administration');
    expect(roleLabel(membership({ displayRole: 'SCHEDULING' }), [])).toBe('Agenda');
    expect(roleLabel(membership({ displayRole: 'TEAM' }), [])).toBe('Équipe');
  });
});

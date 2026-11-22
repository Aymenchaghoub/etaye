import { describe, expect, it } from 'vitest';
import type { AccessContext, Session } from '../api/generated/types.gen';
import { contextPath, currentContexts } from './access-contexts';
import { allowsAccountPath, checkPausedWork, pauseWork } from './session-work';

const membership = '11111111-1111-4111-8111-111111111111';
const portal = '22222222-2222-4222-8222-222222222222';
const practicePath = `/compte/pratiques/${membership}`;
const portalPath = `/compte/patient/${portal}`;
const session: Session = {
  account: {
    id: '33333333-3333-4333-8333-333333333333',
    displayName: 'Nora Lefebvre',
    email: 'nora.lefebvre@example.test',
    state: 'ACTIVE',
    locale: 'fr-FR',
    version: 0,
    createdAt: '2026-11-02T16:00:00+01:00',
    updatedAt: '2026-11-02T16:00:00+01:00',
  },
  membershipIds: [membership],
  portalLinkIds: [portal],
  expiresAt: '2026-11-22T18:00:00+01:00',
};

const practiceId = '55555555-5555-4555-8555-555555555555';
const clinicalContext: AccessContext = {
  kind: 'MEMBERSHIP', grantId: membership, practiceId, practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL',
};
const patientContext: AccessContext = {
  kind: 'PORTAL', grantId: portal, practiceId, practiceName: 'Cabinet des Tilleuls', displayRole: 'PATIENT',
};

describe('current access contexts', () => {
  it('keeps separate practice and patient grants even in the same cabinet', () => {
    const contexts = currentContexts({ ...session, contexts: [clinicalContext, patientContext] });
    expect(contexts?.map(contextPath)).toEqual([practicePath, portalPath]);
  });

  it('rejects a partial projection, a duplicate grant and a grant of the wrong kind', () => {
    expect(currentContexts({ ...session, contexts: [clinicalContext] })).toBeUndefined();
    expect(currentContexts({ ...session, contexts: [clinicalContext, clinicalContext, patientContext] })).toBeUndefined();
    expect(currentContexts({ ...session, contexts: [{ ...clinicalContext, grantId: portal }, patientContext] })).toBeUndefined();
  });

  it('distinguishes an absent projection from an account with no active grants', () => {
    const noGrants = { ...session, membershipIds: [], portalLinkIds: [] };
    expect(currentContexts(noGrants)).toBeUndefined();
    expect(currentContexts({ ...noGrants, contexts: [] })).toEqual([]);
  });
});

describe('paused account work', () => {
  it('keeps access bound to the grant kind and exact local path', () => {
    expect(allowsAccountPath(session, practicePath)).toBe(true);
    expect(allowsAccountPath(session, portalPath)).toBe(true);
    expect(pauseWork(session, `/compte/patient/${membership}`, 'Brouillon fictif')).toBeUndefined();
    expect(pauseWork(session, `${practicePath}/extra`, 'Brouillon fictif')).toBeUndefined();
  });

  it('restores draft work only while the account and selected grant still match', () => {
    const draft = 'Brouillon fictif';
    const paused = pauseWork(session, practicePath, draft);
    expect(paused).toBeDefined();
    if (!paused) throw new Error('Expected authorized work to be paused');

    expect(checkPausedWork(paused, session, practicePath)).toEqual({ status: 'restored', value: draft });
    expect(checkPausedWork(paused, session, portalPath)).toEqual({ status: 'grant-changed' });
    expect(checkPausedWork(paused, { ...session, membershipIds: [] }, practicePath)).toEqual({ status: 'grant-changed' });
    const otherAccount = { ...session.account, id: '44444444-4444-4444-8444-444444444444' };
    expect(checkPausedWork(paused, { ...session, account: otherAccount }, practicePath)).toEqual({ status: 'account-changed' });
  });
});

import type { AccessContext, Session } from '../api/generated/types.gen';
import { ACCOUNT_PATH } from './session-work';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export function contextPath(context: AccessContext): string {
  const audience = context.kind === 'MEMBERSHIP' ? 'pratiques' : 'patient';
  return `${ACCOUNT_PATH}/${audience}/${context.grantId}`;
}

export function currentContexts(session: Session): AccessContext[] | undefined {
  if (!Array.isArray(session.contexts) || !Array.isArray(session.membershipIds) || !Array.isArray(session.portalLinkIds)) {
    return undefined;
  }
  const memberships = new Set(session.membershipIds);
  const portalLinks = new Set(session.portalLinkIds);
  const current: AccessContext[] = [];
  for (const context of session.contexts) {
    if (!context || typeof context.kind !== 'string') return undefined;
    if (context.kind !== 'MEMBERSHIP' && context.kind !== 'PORTAL') continue;
    const remaining = context.kind === 'MEMBERSHIP' ? memberships : portalLinks;
    if (typeof context.grantId !== 'string' || !uuid.test(context.grantId)
      || typeof context.practiceId !== 'string' || !uuid.test(context.practiceId)
      || typeof context.practiceName !== 'string' || typeof context.displayRole !== 'string'
      || !remaining.delete(context.grantId)) {
      return undefined;
    }
    current.push(context);
  }
  if (memberships.size || portalLinks.size) return undefined;
  return current;
}

export function practiceLabel(context: AccessContext) {
  return context.practiceName.trim() || 'Cabinet';
}

export function roleLabel(context: AccessContext, contexts: AccessContext[]) {
  if (context.kind === 'PORTAL') {
    const ownLinks = contexts.filter((entry) => entry.kind === 'PORTAL' && entry.practiceId === context.practiceId);
    return ownLinks.length > 1 ? `Espace patient ${ownLinks.indexOf(context) + 1}` : 'Espace patient';
  }
  switch (context.displayRole) {
    case 'CLINICAL': return 'Praticien';
    case 'ADMINISTRATION': return 'Administration';
    case 'SCHEDULING': return 'Agenda';
    default: return 'Équipe';
  }
}

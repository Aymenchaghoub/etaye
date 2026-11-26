/** Preserve the form's address shape. The API owns the authoritative validation. */
export function hasContactEmailShape(email: string): boolean {
  const at = email.indexOf('@');
  if (at < 1 || email.indexOf('@', at + 1) !== -1 || /\s/.test(email)) return false;
  // A separator must have at least one domain character on either side.
  const dot = email.indexOf('.', at + 2);
  return dot !== -1 && dot < email.length - 1;
}

/**
 * Browser Back and Forward reach the router as a popstate that has already changed the address. While a form holds
 * unsaved input, the practitioner shell registers this guard so the router keeps the page and the shell asks first.
 */
export type LeaveGuard = (target: string) => boolean;

let guard: LeaveGuard | undefined;

export function setLeaveGuard(next: LeaveGuard | undefined) {
  guard = next;
}

/** True when unsaved work took over the decision: the caller must restore the page it was showing. */
export function interceptLeave(target: string): boolean {
  return guard ? guard(target) : false;
}

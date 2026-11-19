# ADR-008 — Multi-client access: web, installed web app and native shell

Status: Accepted by Aymen on 2026-11-17 (UD-29). Amended on 2026-11-19 by [ADR-010](ADR-010-native-client-react-native-expo.md) (UD-38, UD-39): point 5, the Capacitor wording of points 1 and 4 and the React Native alternative are superseded; read the amendment at the end. Date: 2026-11-17. Related: UD-12 to UD-15, AD-4, AD-11, ADR-004, NFR-15, stories 10.2 and 10.4 to 10.8.

Aymen's direction (UD-12) is that Étaye runs on the web and as an installable app on Android and iOS, from one API and as much shared front-end code as possible, at a budget close to zero. ADR-004 chose same-origin cookie sessions with CSRF protection. That choice stays correct for the web, but a native shell loads its pages from another origin (typically `capacitor://localhost` on iOS and `https://localhost` on Android, To verify in the Capacitor configuration), so a host-only `SameSite=Lax` cookie is not sent on its API calls.

**Choice:**

1. **One account authority, one server-side opaque session store, two transports.** The web app and the installed web app (PWA) keep the `__Host-etaye-session` cookie and CSRF header exactly as ADR-004 defines. A native shell carries the same opaque session identifier in an `Authorization: Bearer` header. The identifier is issued by a dedicated operation (`createNativeSession`, planned) only to a request from an allowlisted native origin that declares its client kind, is stored only in platform secure storage (Keychain on iOS, Keystore on Android), never in webview storage or a URL, and is never a JWT.
2. **Revocation is unchanged.** Logout, suspension, password reset and privilege withdrawal invalidate native sessions on the next request. Each native session is bound to a `DeviceRegistration`; the account can list and revoke its devices.
3. **No mixing.** A request that carries both a session cookie and a bearer header is refused. Bearer mode is never accepted from a web origin. CSRF rules for cookie mode do not change.
4. **CORS.** An explicit, exact-origin allowlist, empty by default, without wildcard; credentials are not allowed for bearer mode; each entry has a test.
5. **Packaging ladder.** Installable web app first, then a Capacitor shell with bundled web assets. A shell that loads the remote site through `server.url` is rejected: Capacitor documents that option as development-oriented, it exposes the native bridge to remote content and it raises the App Store risk of a repackaged website (guideline 4.2).
6. **Data on the device.** No clinical data at rest: secure storage holds the session secret only, there is no offline clinical cache (UD-15), and the PWA service worker caches the application shell only. Crash reports and logs carry no clinical text.
7. **Push notifications are one more outbox channel.** The payload is content-free ("new information is available"), the destination is re-authorized at delivery, and a device registration is revoked on logout and suspension. Web Push serves the installed web app (on iOS only for an app added to the Home Screen, iOS 16.4 or later, standalone display, no Apple Developer Program required, no silent pushes); FCM serves Android; APNs requires an Apple program decision (D-15).
8. **Links.** Invitation and recovery links are HTTPS URLs on the application origin and work without the app. Universal links and app links are optional (D-32).

Alternatives:

- JWT access and refresh tokens for native clients: rejected for the reasons of ADR-004 (rotation and revocation complexity), and they would create a second session model.
- Cookies in the webview with `SameSite=None`: rejected; it weakens the CSRF model and depends on third-party cookie behavior.
- React Native, Flutter or Kotlin Multiplatform: rejected for now; no demonstrated benefit for Étaye, a second user interface, and the reuse of React and TypeScript (UD-04) is lost.
- Remote-URL shell: rejected (point 5).

Consequences: four planned contract operations (`createNativeSession`, `registerDevice`, `listOwnDevices`, `revokeDevice`), one planned entity (`DeviceRegistration`, see the data dictionary), tests for both transports, the CORS allowlist and the push payload canary. Native session duration is open (A-15, D-19). Store policy risk (guidelines 4.2, 1.4.1, 5.1.1), costs and the Google Play tester rule belong to D-15.

To verify during the first spikes (acceptance does not wait for them): the session-id resolver by header in Spring Session with Spring Boot 4.1.2; the native origins of the chosen Capacitor version; Capacitor's license file (expected MIT); whether FCM has any cost for this use. No account, credential or paid service is requested by this ADR.

## Amendment 2026-11-19 (ADR-010, UD-38, UD-39)

The body above is kept as written on 2026-11-17. What changed:

- **Point 5 is superseded.** The packaging ladder is the installable web app first, then a React Native app built with Expo (ADR-010); a Capacitor shell is no longer planned. The rejection of a remote-URL shell has no object any more, because the native app loads no remote page.
- **Points 1 and 4, the native origin.** A React Native client has no browser origin, so session issuance no longer depends on an allowlisted native origin. ADR-010 point 3 replaces it with credential verification, a declared client kind, a device registration and the refusal of bearer requests that carry a cookie or browser origin headers. The CORS allowlist keeps browser origins only.
- **Alternatives.** React Native is now the chosen technology; Flutter and Kotlin Multiplatform stay rejected.
- **Point 7, push and stores.** Apple's push service stays gated behind the Apple program, and every store step is Not planned (D-15, UD-39).
- **Unchanged:** one account authority and one server-side opaque session store (point 1), revocation (point 2), no mixing of transports (point 3), no clinical data at rest (point 6), content-free push (point 7), HTTPS links (point 8) and the planned operations `createNativeSession`, `registerDevice`, `listOwnDevices` and `revokeDevice`.
- **Spike checks.** The two Capacitor items (native origins and license) are replaced by the ADR-010 list; the Spring Session header resolver and the FCM cost remain.

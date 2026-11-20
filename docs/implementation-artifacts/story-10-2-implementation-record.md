# Story 10.2 implementation record

**Phase.** Implementation and review of the authorized screenless slice. Aymen approved the [phase 0 plan](story-10-2-phase-0.md) on 2026-11-20. The authorization is UD-45 and the evidence follows C1–C9 in the [context packet](story-10-2-context-packet.md). The contract preparation was committed before the application change. Version enforcement and stale-client recovery belong to story 10.4. Native transport, package extraction and delivery remain with their owning stories.

## Implementation

The OpenAPI contract and its indexes now include 98 operations. The generated Web SDK uses pinned Hey API 0.99.0 and Fetch. The Web build regenerates the committed SDK in a temporary directory, compares both file sets and bytes, and rejects drift. The readiness adapter keeps same-origin cookies, no-store, the six-second abort and the immediate failure state on 503 headers. The client sends its package version as `X-Client-Version`. The flag selects the injectable browser transport seam. [Contract](../contracts/README.md), [Hey API license](../references/hey-api-license.md)

The public `/meta` endpoint serves version data without a session or database access. Spring applies an empty-by-default browser origin policy on both security chains. The existing cookie and CSRF path stays active. Bearer requests are refused by this browser stack. The push port receives only the closed `INFORMATION_AVAILABLE` kind. Its default adapter does no I/O. The Web build checks its dependency graph, including development packages that can be bundled by Vite, for inline PDF viewers.

The token bridge reads the frozen received JSON plus the approved ADR-014 delta. The base serialization matches the received CSS byte for byte. The bridge emits two theme blocks, 29 type roles, scrim, color scheme and trace duration. Layout widths are in normalized data, independent of CSS. The build emits the golden-matched CSS, seven original fonts and their SIL OFL notices. The received sources and archived Figma reference are preserved. The added roles still need Figma reconciliation when access returns.

## Verification actually run on 2026-11-20

| Check | Result |
|---|---|
| C1, C2 | Node 26.13.1 and pnpm 12.21.0. Frozen install, strict TypeScript and Web build passed. The generated SDK matched the contract. A temporary contract rename of `minimumClientVersion` failed drift verification and the original input passed after restoration. Real HTTP confirmed the authorized record’s empty 304 and ETag before adding it to the contract. |
| C3, C4, C6 | `./mvnw -B -ntp clean verify` on Temurin 25.0.5.1 and PostgreSQL 18.7 through Testcontainers passed 143 tests with zero failures, errors or skips. Surefire evidence gate passed with the updated required suites. Tests cover mixed credentials on both chains, exact configured origins and their response headers, rejected default ports, confirmed 406 shapes, same-origin CSRF and metadata with configured versions and during database outage. |
| C5 | The same Maven suite proved that the closed event reaches the port, arbitrary and clinical-text payloads are rejected at the boundary and the no-op adapter accepts the content-free event. |
| C7 | `python3 -m unittest discover -s tools -p 'test_*.py'` passed 94 tests. Token tests detect drift from verified values and from one theme’s ADR delta, validate selected contrast pairs and verify the CSS, fonts and licenses. The design-system reference check passed. |
| C8, C9 | The dependency check and Web build passed. An aliased viewer in a synthetic development dependency graph was rejected. `corepack pnpm test:smoke --reporter=json` in `apps/web` passed 13 Chromium tests. Two axe scans retained reports and had zero violations. These are smoke-page results. |
| Real stack | `ETAYE_E2E_DB_CONTAINER=<isolated container> ETAYE_E2E_BASE_URL=http://127.0.0.1:5173 corepack pnpm test:e2e-real --reporter=json` passed one Chromium test against real Spring and PostgreSQL. It exercised stop, restart, pause and recovery. The run passed again after the review fixes. The container used only generated credentials and was removed after capture. |
| Visual | Opened six live smoke-page captures at 1440 × 1024, 1024 × 768 and 390 × 844. `READY` and `UNAVAILABLE` matched captures of the story 10.1 smoke page byte for byte at those widths. AT-039-C has no product surface. |
| Documentation | `python3 tools/validate_docs.py` passed in stories-10.1-10.2 mode with zero errors. |

## Review

One local Zcode review targeted `b2684a592cb2387b2fda93d0fdab6d131ec1f9f4` from base `8afd51c2a7e41a29bfa9825620fc6cafaaaaf1e1`. The advertised plan mode was rejected before execution. The guarded session reached its initial time limit and was resumed to return one final report. Zcode rated C1–C9 passing with seven low findings and one informational finding. It independently ran the SDK drift check, token bridge, 94 tooling tests and documentary validation. It inspected the recorded application tests and did not independently repeat those suites.

### Silent failures

- The PDF guard now walks development dependencies as well as required and optional packages. A synthetic alias for a development-only `pdfjs-dist` failed the guard.
- Metadata validates both configured versions against SemVer 2.0.0 at construction. Tests reject bad inputs and verify non-default values over HTTP.
- Browser origin configuration rejects explicit default ports. A separate HTTP check showed that Spring initially accepted a trailing slash on a configured origin. The API now compares the raw origin exactly, and the integration test rejects the slash and a different port.

### Edge cases

- CORS runs before bearer rejection, which itself runs before identity lookup on the browser chain. The problem writer preserves the validated `Access-Control-Allow-Origin` and all `Vary` values when clearing a partial response. A configured-origin HTTP test covers the resulting 403 and CORS headers.
- Mixed cookie and bearer credentials are tested on both chains. The public operations and the carrier read now exercise the declared 406 problem shape.
- The active HTTP operations declare the optional version header in OpenAPI. The server description reflects the same-origin runtime. The SDK was regenerated and parity verified.

### Acceptance-criteria compliance

The implementation record above captures the reruns after triage. The Web has no unsafe browser operation in this slice. Its cookie and CSRF behavior is exercised at the real HTTP API boundary, while browser tests exercise both flag states on readiness. The PDF check guards known inline viewer package families. AT-039-C still has no new product surface. The real smoke page matched its 10.1 baseline at three widths. Eight synthetic Git changes verified contract, token, license and general documentation routing to the required Web job.

## Integration

[PR #44](https://github.com/Aymenchaghoub/etaye/pull/44) is the integration point. All five required GitHub checks ran green on the implementation revision and on `a66433a05fdeab0f9707d5fcd236ae972094463e` after the origin correction. The [API run](https://github.com/Aymenchaghoub/etaye/actions/runs/44812052128) ran PostgreSQL tests and the [Web run](https://github.com/Aymenchaghoub/etaye/actions/runs/44812052232) ran the build and Chromium smoke suite.

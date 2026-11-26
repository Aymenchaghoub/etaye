# API quality remediation

Verified source baseline `389c48da64a2aed78c57418b122b0e5585989a50`. SonarQube Cloud reported 16 distinct open API issues at analysis revision `52d672e609d615a089ffb2319afe5113d8f41958`, dated 2026-11-26 11:12:38 UTC. The referenced source hunks match the local baseline. Eight SQL findings each count in two quality dimensions. The 10 security, six reliability and eight maintainability observations therefore describe 16 tickets. Security and reliability ratings were D, maintainability A, and the Quality Gate was OK. The API reported 86.4% overall, 89.5% line and 78.8% branch coverage. No new-code coverage figure was returned. [SonarQube Cloud API project](https://sonarcloud.io/dashboard?id=aymenchaghoub_etaye-api)

## Finding disposition

Locations use the analyzed revision. Severity lists Sonar's software-quality impact. Source inspection confirmed one startup reliability defect, one redundant browser CSRF exemption to remove, one overly broad privilege-test assertion and four unreachable fallback checks. The SQL findings and remaining CSRF alert have compensating source evidence. SonarQube status changes are subject to the existing review process.

| Sonar issue | Rule and quality impact | Location | Evidence and disposition |
| --- | --- | --- | --- |
| `AaIXi5ZzhUcSC1ur7toN` | java:S2077, security medium / maintainability low | MeasurementRepository.java:141 | Page SQL combines fixed CTE, columns, join and developer-owned predicates. Concepts expand solely as named JDBC bindings. Dates, cursor, practice, record and limit are bound. Existing measurement HTTP tests cover a filtered next page, date bounds, exact concept codes and cross-practice concealment. Source review found no injection path. Retained query shape and predicates. |
| `AaIXi5ZzhUcSC1ur7toO` | java:S2077, security medium / maintainability low | MeasurementRepository.java:160 | Anchor SELECT uses one fixed optional recorded-at predicate. The practice and measurement identifiers and the optional timestamp are bound. Existing measurements HTTP tests cover past and current lineages and a foreign measurement. Source review found no injection path. |
| `AaIXi5ZzhUcSC1ur7toP` | java:S2077, security medium / maintainability low | MeasurementRepository.java:173 | Newest revisions use fixed practice, record and lineage predicates plus the same fixed optional predicate with an SQL alias. Anchor-derived record and lineage values and limit are bound. Existing measurements HTTP tests cover newest and past revisions. Source review found no injection path. |
| `AaIXi5ZzhUcSC1ur7toQ` | java:S2077, security medium / maintainability low | MeasurementRepository.java:181 | Original revision uses the same scope as the preceding query, with a literal ascending order and bound limit. The long-lineage HTTP test checks that the original remains. Source review found no injection path. |
| `AaIXi5Z2hUcSC1ur7toR` | java:S2077, security medium / maintainability low | EvidenceRepository.java:146 | Source page predicates are fixed SQL fragments. Source kinds become enum names and timestamps and keyset positions use named bindings. The supersession join has matching practice and record IDs. Existing evidence HTTP tests cover kinds, dates, paging and cross-practice denials. Source review found no injection path. |
| `AaIXi5Z2hUcSC1ur7toS` | java:S2077, security medium / maintainability low | EvidenceRepository.java:157 | Both conditional fragments are selected from an empty string and a fixed alias-specific as-of predicate. The timestamp, source ID and practice ID use named bindings. Existing evidence HTTP tests cover historical visibility and foreign sources. Source review found no injection path. |
| `AaIXi5Z2hUcSC1ur7toT` | java:S2077, security medium / maintainability low | EvidenceRepository.java:247 | Assertion predicates and order are literals. Category is an enum bound as a value. Cursor values are bound, including the nullable timestamp cast for an undated cursor. Existing evidence HTTP tests cover the undated group, category and source provenance. Source review found no injection path. |
| `AaIXi5ZwhUcSC1ur7toM` | java:S2077, security medium / maintainability low | PatientDirectoryRepository.java:134 | Only a loop index from zero to the number of normalized query words becomes part of each parameter name. Search words, birth date and cursor fields are bound. The search service normalizes words before building each LIKE value. Existing patient HTTP tests cover accents, punctuation, cursor binding and tenant isolation. Source review found no injection path. |
| `AaIXi5aFhUcSC1ur7toX` | java:S4502, security high | SecurityConfig.java:89 | The first chain serves only the existing read-only metadata, readiness and conditional demo catalogue handlers. It suppresses session access with a request wrapper and rejects bearer transport. New real HTTP tests verify POST returns 405 with no credential or session issuance on these paths. Under this model the current finding has no state-changing handler to exercise. Review both matchers and controller mappings before adding any route in this scope. |
| `AaIXi5aFhUcSC1ur7toY` | java:S4502, security high | SecurityConfig.java:148 | The browser chain exempted every method on `/api/v1/csrf` even though issuance uses GET. No state-changing handler is present on that path. Removed the exemption and retained the budgeted GET. The HTTP regression checks a session-bearing POST without a CSRF token is rejected and a POST with a valid token still reaches the existing 405. |
| `AaIXi5aIhUcSC1ur7toa` | java:S5998, reliability medium | ApiMetaController.java:22 | Confirmed startup failure for a valid version with many identifiers. The owner-controlled configuration reached recursive regex repetitions. Possessive repetitions use the disjoint dot/plus boundaries so the matching stack stays bounded. The regression checks long prerelease and build strings, unchanged metadata values, and invalid numeric and empty identifiers. |
| `AaIXi5aBhUcSC1ur7toW` | java:S2583, reliability medium | DatabaseUnavailableFilter.java:73 | The catch parameter is non-null. Spring's `getMostSpecificCause` returns the input if it has no cause. Removed the unreachable fallback. Existing filter tests cover outage, unexpected errors, disconnects, committed responses and private error detail. |
| `AaIXi5Z_hUcSC1ur7toU` | java:S2583, reliability medium | GlobalExceptionHandler.java:57 | The general problem builder receives an exception and derives its most specific cause. Removed the unreachable fallback. Existing REST tests check permitted problem fields and error classification. |
| `AaIXi5Z_hUcSC1ur7toV` | java:S2583, reliability medium | GlobalExceptionHandler.java:127 | The content-negotiation handler receives a non-null exception. Removed the unreachable fallback. The security HTTP suite checks the documented 406 representation on public routes. |
| `AaIXi5aGhUcSC1ur7toZ` | java:S2583, reliability medium | ProblemAccessDeniedHandler.java:38 | Spring passes a concrete access-denial exception. Removed the unreachable fallback. Existing 403 tests exercise distinct CSRF and authorization responses. |
| `AaIXi5YZhUcSC1ur7toL` | java:S5783, reliability high | IdentityLookupTest.java:71 | Connection setup, statement construction and the permission-sensitive SELECT were in one throwable lambda. Set up the connection and statement before the assertion. Require PostgreSQL insufficient privilege (SQLSTATE 42501) from the SELECT. |

The SQL conclusions depend on the bound-parameter contract and literals and on continuing to enforce the enclosing practice transaction. Reassess when a statement starts to accept externally supplied SQL identifiers or predicates, when the binding mechanism changes, or when a query moves outside that transaction. The SemVer and CSRF corrections are small local changes. SQL and public-chain dispositions are source assessments for individual review, with no automated suppression or remote dismissal.

## Regression scope

- The opt-in synthetic recovery inbox now has a deterministic clock and an OS-selected port for its test instance. Runtime defaults retain the same loopback address, port and current time. Real local HTTP verifies the protected headers, synthetic recipient boundary, latest-link ordering, expiry at the boundary, redemption removal, bounded history and refusals for unsupported requests.
- The 422 response from the validation handler keeps the same status, title and payload while using Spring's current enum constant. Two production deprecation diagnostics and two identity integration-test deprecations were removed from the exact baseline. The rest of the compiler policy still applies.
- The 40-case isolation report represents the record, source and measurement families and three refusal parity groups. Separate HTTP suites exercise the patient directory, scheduling carrier, timeline, assertions, history, practice/actor-bound cursors and connection reuse. The matrix is narrower than the full API test scope and is not a complete per-route inventory. Patient source projections, processing task status and exports retain their pending rows.

## Local verification

All verification uses Java 25 and isolated synthetic PostgreSQL 18 containers. No external deployment or remote database was involved.

- Before the fix, `./mvnw -B -ntp -Dtest=ApiMetaControllerTest test` ran seven tests against the original implementation with the new regression assertions. One failed with `StackOverflowError` in the long configured version test.
- Baseline `./mvnw -B -ntp clean verify` ran 259 tests with zero failures or skips in 159.68 seconds wall clock. JaCoCo measured 2,065/2,307 lines and 718/910 branches. This local report is separate from the earlier Sonar analysis.
- Candidate `./mvnw -B -ntp clean verify` ran 270 tests across 39 suites in 192.50 seconds wall clock. JaCoCo floors, the Surefire report check and the 40-case isolation matrix passed. Compiler and CI evidence are tracked with the pull request's exact revision.

| JaCoCo scope | Baseline lines | Candidate lines | Baseline branches | Candidate branches |
| --- | ---: | ---: | ---: | ---: |
| API | 2,065/2,307 (89.51%) | 2,125/2,307 (92.11%) | 718/910 (78.90%) | 742/900 (82.44%) |
| Identity recovery | 0/64 | 65/69 (94.20%) | 0/32 | 29/32 (90.62%) |
| Infrastructure security | 422/450 (93.78%) | 420/448 (93.75%) | 167/216 (77.31%) | 166/214 (77.57%) |
| Infrastructure transaction | 65/66 (98.48%) | 65/66 (98.48%) | 19/22 (86.36%) | 19/22 (86.36%) |
| Record domain | 335/339 (98.82%) | 335/339 (98.82%) | 76/84 (90.48%) | 76/84 (90.48%) |
| Record services | 301/311 (96.78%) | 301/311 (96.78%) | 158/166 (95.18%) | 158/166 (95.18%) |
| Record HTTP | 83/86 (96.51%) | 83/86 (96.51%) | 21/26 (80.77%) | 21/26 (80.77%) |

Line coverage meets the 90% target. Branch coverage is 23 additional branch outcomes short of 85% at the current denominator. Remaining API gaps include identity reload failures and persisted-session cleanup exceptions, authorization audit failure handling, and uncommon error conversions. Cover reachable failures at their repository or HTTP boundaries before considering a higher floor. JaCoCo counts are local. Overall coverage, new-code measures, ratings and issue status require SonarQube analysis.

## Java mutation pilot

The BR-008 section-state derivation is a pure policy with user-visible outcomes. A disposable copy of the baseline module used PIT Maven 1.30.2 and `org.pitest:pitest-junit5-plugin:1.2.3` as a Maven plugin dependency. An initial CLI invocation that passed the adapter version as a property discovered zero tests and failed. With the explicit plugin dependency, PIT selected the same JUnit Platform launcher version (6.0.3) as the test runtime. [PIT Maven configuration](https://pitest.org/quickstart/maven/) · [JUnit adapter](https://github.com/pitest/pitest-junit5-plugin) · [PIT release](https://github.com/hcoles/pitest/releases/tag/1.30.2)

From a disposable baseline module with that plugin dependency configured and production/test classes compiled, run

```sh
/usr/bin/time -v ./mvnw -B -ntp org.pitest:pitest-maven:1.30.2:mutationCoverage \
  -DtargetClasses=dev.aymen.etaye.records.domain.SectionStateRule \
  -DtargetTests=dev.aymen.etaye.records.SectionStateRuleTest \
  -Dthreads=2 -DoutputFormats=XML,HTML
```

PIT generated 15 default mutants and killed all 15, with zero survivors, uncovered mutants or timeouts in `target/pit-reports/mutations.xml`. Wall time was 11.82 seconds, with 286,352 KiB maximum RSS measured by GNU time. The pilot covers one pure class and its six-test suite. It provides no mutation score for authorization, database integration, full API code or behavior outside the default operators. No repository build dependency or per-PR gate was added.

Grammar and platform references [Semantic Versioning 2.0.0](https://semver.org/) · [Java 25 regex quantifiers](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html) · [Spring exception traversal](https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/org/springframework/core/NestedExceptionUtils.html) · [PostgreSQL SQLSTATE codes](https://www.postgresql.org/docs/18/errcodes-appendix.html)

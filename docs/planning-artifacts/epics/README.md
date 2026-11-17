# Epics and planning stories

Status: Backlog / Ready for design. No story is ready for development. Every story requires the reviewed core design families (UD-16) or Aymen's specific exception, current source reading and explicit development authorization. No implementation story file or sprint has been started.

| Epic | User value | Stories | Increments | Dependencies |
|---|---|---|---|---|
| [E1](epic-1.md) | Safe access and isolated practice work | 4 | 1, 2 | Story 10.1 (walking skeleton) and the design/authorization gate |
| [E2](epic-2.md) | Team and operational administration | 4 | 2, 4 | E1 and E10 foundations; story dependencies in the files |
| [E3](epic-3.md) | Longitudinal patient evidence | 5 | 1, 2 | E1 and E10 foundations; story dependencies in the files |
| [E4](epic-4.md) | Scheduling and preparation | 5 | 1, 2 | E1 and E10 foundations; story dependencies in the files |
| [E5](epic-5.md) | Sourced clinician preparation | 4 | 1, 4 | E1 and E10 foundations; story dependencies in the files |
| [E6](epic-6.md) | Consultation and human-reviewed reports | 4 | 1 | E1 and E10 foundations; story dependencies in the files |
| [E7](epic-7.md) | Explicit publication and patient access | 5 | 1, 2 | E1 and E10 foundations; story dependencies in the files |
| [E8](epic-8.md) | Branded documents and communications | 3 | 1, 2 | E1 and E10 foundations; story dependencies in the files |
| [E9](epic-9.md) | Credible synthetic demonstration and delivery | 3 | 1, 4 | E1 and E10 foundations; story dependencies in the files |
| [E10](epic-10.md) | Platform foundation and multi-client delivery | 8 | 1, 3, 4 | None beyond the design/authorization gate |

Epic sequence groups value, not delivery promises or time estimates. The [roadmap](../roadmap.md) allocates every story, design family and API operation to an increment and records what each increment leaves for the next; its machine-readable form is [roadmap-assignments.json](../roadmap-assignments.json). Story dependencies are in each story file and in `story-index.json` (`dependsOn`). Do not skip source/permission design by treating infrastructure as the only first epic; story 10.1 is the first to build because every other story inherits its request path. These slices may be refined after full design review; preserve identifiers or explicitly supersede them.

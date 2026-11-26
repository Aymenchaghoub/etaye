import { defineConfig } from 'vitest/config';

// Coverage floors, set just under the baseline measured on 2026-11-25: 73 % of lines in the 18 logic modules (.ts) and
// 98 % in the nine pure rule modules listed below. A ratchet: raise a floor when coverage grows, never lower one to
// pass a build. Vitest applies each glob to its matching files in aggregate.
//
// There is deliberately no overall floor. The 23 page and component files (.tsx, 1,769 lines) read 0 % here because
// the Playwright suite drives them and collects no coverage, so an overall percentage falls with every UI story
// (stories 3.2 and 3.3 added 996 and 643 .tsx lines; the baseline tolerates 103) and would fail the required check
// without any regression. The .tsx files are verified by the browser suite; see docs/operations/ci-cd-quality-audit.md.
const ruleModules = [
  'isolation/isolation-journey',
  'auth/session-work',
  'record/asof',
  'record/measurements',
  'record/zone',
  'patients/date',
  'patients/routes',
  'patients/attempt-key',
  'patients/memory',
];

export default defineConfig({
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/api/generated/**', '**/*.test.{ts,tsx}', '**/*.d.ts'],
      reporter: ['text-summary', 'lcov', 'json'],
      reportsDirectory: 'reports/unit-coverage',
      thresholds: {
        'src/**/*.ts': { lines: 71, statements: 71, functions: 61, branches: 71 },
        [`src/{${ruleModules.join(',')}}.ts`]: { lines: 95, statements: 95, functions: 90, branches: 87 },
      },
    },
  },
});

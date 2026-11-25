import { defineConfig } from 'vitest/config';

// Coverage floors. A ratchet set just under the baseline measured on 2026-11-25: 18.8 % of lines overall, because the
// 23 page and component files (.tsx, 1,769 lines) are driven by the Playwright suite, which collects no coverage;
// 73 % of lines in the 18 logic modules (.ts); 98 % of lines in the pure rule modules listed below. Raise a floor when
// coverage grows; never lower one to pass a build. Vitest applies each glob to its matching files in aggregate.
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
      reporter: ['text-summary', 'lcov'],
      reportsDirectory: 'reports/unit-coverage',
      thresholds: {
        lines: 18,
        statements: 18,
        functions: 20,
        branches: 15,
        'src/**/*.ts': { lines: 71, statements: 71, functions: 61, branches: 71 },
        [`src/{${ruleModules.join(',')}}.ts`]: { lines: 95, statements: 95, functions: 90, branches: 87 },
      },
    },
  },
});

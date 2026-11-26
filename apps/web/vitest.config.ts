import { defineConfig } from 'vitest/config';

// Vitest applies the established logic and pure-rule floors to all matching files in aggregate.
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

export default defineConfig(({ mode }) => {
  const unitOnly = mode === 'unit-coverage';
  const componentsOnly = mode === 'component-coverage';

  return {
    test: {
      include: unitOnly ? ['src/**/*.test.ts'] : componentsOnly ? ['src/**/*.test.tsx'] : ['src/**/*.test.{ts,tsx}'],
      coverage: {
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/api/generated/**', '**/*.test.{ts,tsx}', '**/*.d.ts'],
        reporter: ['text-summary', 'lcov', 'json'],
        reportsDirectory: unitOnly ? 'reports/unit-only-coverage'
          : componentsOnly ? 'reports/component-coverage' : 'reports/unit-coverage',
        // The component-only report measures its standalone contribution. The logic floors apply in both full and unit-only runs.
        thresholds: componentsOnly ? undefined : {
          'src/**/*.ts': { lines: 71, statements: 71, functions: 61, branches: 71 },
          [`src/{${ruleModules.join(',')}}.ts`]: { lines: 95, statements: 95, functions: 90, branches: 87 },
        },
      },
    },
  };
});

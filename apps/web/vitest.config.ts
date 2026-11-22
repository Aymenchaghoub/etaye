import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/api/generated/**', '**/*.test.{ts,tsx}', '**/*.d.ts'],
      reporter: ['text-summary', 'lcov'],
      reportsDirectory: 'reports/unit-coverage',
    },
  },
});

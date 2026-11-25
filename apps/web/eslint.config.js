import js from '@eslint/js';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// Correctness, security and accessibility rules only: no formatting or style rules, and no rules aimed at the
// React Compiler (the project does not use it). `tsc -b` stays the type checker; these rules add what it cannot see
// (floating promises, Hooks misuse, ARIA misuse). Existing violations live in eslint-suppressions.json, so a new
// violation fails the build while the recorded debt stays visible and shrinks (`pnpm lint:prune`).
const testFiles = ['tests/**', 'e2e/**', 'demo-e2e/**', 'src/**/*.test.{ts,tsx}'];

export default tseslint.config(
  // The generated SDK is rewritten from the OpenAPI contract and checked for drift by `pnpm check:api`.
  { ignores: ['dist/**', '.cache/**', 'reports/**', 'playwright-report/**', 'test-results/**', 'src/api/generated/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,
  {
    languageOptions: {
      globals: { ...globals.browser },
      parserOptions: {
        projectService: { allowDefaultProject: ['vite.config.ts'] },
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { 'react-hooks': reactHooks },
    rules: { 'react-hooks/rules-of-hooks': 'error', 'react-hooks/exhaustive-deps': 'error' },
  },
  { files: ['src/**/*.tsx'], ...jsxA11y.flatConfigs.recommended },
  {
    // Test doubles read untyped JSON bodies and stub async interfaces without awaiting; the production source
    // keeps the full set.
    files: testFiles,
    rules: {
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-unsafe-return': 'off',
      '@typescript-eslint/require-await': 'off',
    },
  },
  // Build scripts and tool configuration run on Node and are not part of the type-checked project.
  { files: ['**/*.{js,mjs}', '*.config.ts'], languageOptions: { globals: { ...globals.node } } },
  { files: ['**/*.{js,mjs}'], ...tseslint.configs.disableTypeChecked },
);

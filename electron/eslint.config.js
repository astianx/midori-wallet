export default [{ ignores: ['dist/**', 'out/**', 'node_modules/**'] }, {
  files: ['**/*.js', '**/*.cjs', '**/*.mjs'],
  languageOptions: { ecmaVersion: 2022, sourceType: 'module', globals: { process: 'readonly', Buffer: 'readonly', console: 'readonly', setTimeout: 'readonly', clearTimeout: 'readonly', URL: 'readonly', fetch: 'readonly', AbortSignal: 'readonly', structuredClone: 'readonly', require: 'readonly' } },
  rules: { 'no-undef': 'error', 'no-unreachable': 'error', 'no-dupe-args': 'error', 'no-dupe-keys': 'error', 'no-constant-condition': ['error', { checkLoops: false }] },
}];

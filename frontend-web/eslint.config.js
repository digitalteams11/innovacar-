import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      // Innovacar's confirmation UX is a single shared ConfirmProvider/
      // useConfirm/ConfirmDialog system (src/context/ConfirmContext.tsx), not
      // browser-chrome dialogs — no window.confirm/alert/prompt allowed
      // anywhere in the app. ESLint's own scope analysis means this only
      // flags real references to the global functions; a local `const
      // confirm = useConfirm()` (or any other shadowing binding) is never
      // mistaken for the native one, so no false positives on the very
      // pattern this migration introduced everywhere.
      'no-alert': 'error',
    },
  },
])

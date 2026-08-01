import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// Shared source files physically live inside frontend-web/src, which has its
// own node_modules (its own React install). Node's module resolution walks
// up from a file's own directory, so a bare `import ... from 'react'` inside
// one of those files would resolve to frontend-web/node_modules/react —
// a second copy of React alongside this project's own, which breaks every
// hook ("Invalid hook call", "Cannot read properties of null (reading
// 'useContext')") the moment a context provider from one copy is read by a
// component from the other. These aliases force every context-sensitive
// shared dependency to resolve to this project's single copy instead.
const singletonDeps = [
  'react',
  'react-dom',
  'react/jsx-runtime',
  'react/jsx-dev-runtime',
  'react-router-dom',
  'react-i18next',
  'i18next',
  'i18next-browser-languagedetector',
  'framer-motion',
  'recharts',
];

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: './',
  resolve: {
    alias: Object.fromEntries(
      singletonDeps.map((dep) => [dep, path.resolve(__dirname, 'node_modules', dep)])
    ),
    dedupe: singletonDeps,
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    // The renderer's entire source lives in ../frontend-web/src (shared, not
    // duplicated) — the dev server must be allowed to serve files from
    // outside this project's own root to reach it.
    fs: { allow: ['..'] },
  },
})

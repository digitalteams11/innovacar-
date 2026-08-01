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

// Injects a real Content-Security-Policy into the packaged production build
// only — dev needs Vite's HMR client (a websocket + eval-based module
// runtime) unrestricted, and Electron's own "Insecure CSP" console warning
// already documents that it disappears once packaged, so this is the actual
// fix rather than silencing that warning without any policy at all. `file:`
// is required for 'self' here because that's the packaged app's own origin
// (see main.cjs's loadFile) — never any other scheme.
function productionCsp() {
  return {
    name: 'innovacar-desktop-csp',
    apply: 'build' as const,
    transformIndexHtml(html: string) {
      const csp = [
        "default-src 'self' file:",
        "script-src 'self' file:",
        "style-src 'self' file: 'unsafe-inline' https://fonts.googleapis.com",
        "font-src 'self' file: https://fonts.gstatic.com",
        "img-src 'self' file: data: https:",
        "connect-src 'self' https://api.innovacar.app",
      ].join('; ');
      return html.replace('<head>', `<head>\n    <meta http-equiv="Content-Security-Policy" content="${csp}" />`);
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), productionCsp()],
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
    // strictPort: Electron's main.cjs scans 5173-5177 for whichever port
    // answers first (see findDevServer()). If this dev server silently
    // drifted to another port because 5173 was already held by an unrelated
    // process (e.g. frontend-web's own `npm run dev`), that scan could latch
    // onto the wrong server's response on 5173 and load an entirely
    // different app — a real, reproduced cause of a blank/wrong Electron
    // window. Failing loudly here instead forces freeing the port rather
    // than silently drifting.
    port: 5173,
    strictPort: true,
  },
})

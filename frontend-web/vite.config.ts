import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { networkInterfaces } from 'os'

// server.host is 0.0.0.0 so the dev server accepts LAN connections (e.g. testing
// on a phone or a second machine). But 0.0.0.0 isn't a real address a browser can
// open a WebSocket back to — without an explicit hmr.host, Vite's client defaults
// to `localhost`, which only resolves for someone on the same machine as the
// server. Anyone connecting via the LAN IP gets a permanently failing HMR socket;
// Fast Refresh then silently breaks and can leave a stale module graph where a
// provider and its consumer resolve to two different module instances — this is
// what caused "useAuth must be used within an AuthProvider" even though the
// provider tree itself was correctly structured. Binding hmr.host to the actual
// LAN IP keeps the WebSocket reachable from any device that can reach the HTTP
// server, so HMR (and therefore React context) stays consistent.
function firstLanIPv4(): string | undefined {
  const interfaces = networkInterfaces()
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name] ?? []) {
      if (net.family === 'IPv4' && !net.internal) return net.address
    }
  }
  return undefined
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5174,
    strictPort: true,
    hmr: {
      host: firstLanIPv4() ?? 'localhost',
    },
  },
})

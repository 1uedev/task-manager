import { defineConfig } from 'vite';

// Browser target is set explicitly: current Chromium based browsers (decision of 2026-10-18).
// During development `/api` is forwarded to a locally running backend, so session cookies stay same origin.
const api = { '/api': { target: process.env.TASKMANAGER_API ?? 'http://127.0.0.1:8080', changeOrigin: false } };

export default defineConfig({
  build: { target: 'es2022', sourcemap: false },
  server: { host: '127.0.0.1', proxy: api },
  preview: { host: '127.0.0.1', proxy: api },
});

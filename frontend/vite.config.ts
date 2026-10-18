import { defineConfig } from 'vite';

// Browser target is set explicitly: current Chromium based browsers (decision of 2026-10-18).
export default defineConfig({
  build: { target: 'es2022', sourcemap: false },
  server: { host: '127.0.0.1' },
  preview: { host: '127.0.0.1' },
});

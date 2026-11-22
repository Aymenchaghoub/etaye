import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

const demo = process.env.VITE_ETAYE_LOCAL_DEMO === 'true';

export default defineConfig({
  plugins: [react()],
  build: demo ? {
    rollupOptions: {
      input: {
        app: fileURLToPath(new URL('./index.html', import.meta.url)),
        warmup: fileURLToPath(new URL('./warmup.html', import.meta.url)),
      },
    },
  } : undefined,
  publicDir: ".cache/design",
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false
      },
      '/actuator': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
});

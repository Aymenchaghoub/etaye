import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      input: {
        app: fileURLToPath(new URL('./index.html', import.meta.url)),
        ...(process.env.VITE_ETAYE_LOCAL_DEMO === 'true'
          ? { warmup: fileURLToPath(new URL('./warmup.html', import.meta.url)) }
          : {}),
      },
    },
  },
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

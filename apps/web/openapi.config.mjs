import { fileURLToPath } from 'node:url';

export default {
  input: fileURLToPath(new URL('../../docs/contracts/openapi.yaml', import.meta.url)),
  output: { path: fileURLToPath(new URL('./src/api/generated', import.meta.url)) },
  plugins: ['@hey-api/typescript', '@hey-api/client-fetch', '@hey-api/sdk'],
};

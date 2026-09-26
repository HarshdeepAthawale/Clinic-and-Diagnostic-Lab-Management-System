import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { transformWithOxc } from 'vite';
import { defineConfig } from 'vitest/config';

/**
 * Next.js allows JSX in plain .js files (this project is JavaScript-only, ADR-008), but Vite
 * picks the parser from the extension. Compile our own .js files as JSX before other plugins.
 */
function jsxInJs() {
  return {
    name: 'jsx-in-js',
    enforce: 'pre',
    async transform(code, id) {
      if (!id.endsWith('.js') || id.includes('node_modules')) return null;
      return transformWithOxc(code, id, { lang: 'jsx', jsx: { runtime: 'automatic' } });
    },
  };
}

export default defineConfig({
  plugins: [jsxInJs(), react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.js'],
    css: false,
  },
});

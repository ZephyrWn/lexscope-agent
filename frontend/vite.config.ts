import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';

function applyDemoHeaders(proxyReq: import('node:http').ClientRequest): void {
  proxyReq.removeHeader('authorization');
  proxyReq.removeHeader('origin');
  proxyReq.setHeader('X-API-Key', 'dev-admin-key-2026');
  proxyReq.setHeader('X-Tenant-ID', 'public');
}

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
          'vendor-markdown': ['marked', 'dompurify', 'highlight.js']
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api/ai/pdf/upload/': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (requestPath) => requestPath.replace(/^\/api/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', applyDemoHeaders);
        }
      },
      '/api/ai/react/': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (requestPath) => requestPath.replace(/^\/api/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', applyDemoHeaders);
        }
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (requestPath) => requestPath.replace(/^\/api/, '')
      }
    }
  }
});

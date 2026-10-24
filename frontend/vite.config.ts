import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('/node_modules/zrender/')) return 'campus-chart-renderer'
            if (id.includes('/node_modules/echarts/')) return 'campus-charts'
          },
        },
      },
    },
    server: {
      host: '127.0.0.1',
      port: Number(env.WEB_PORT || 5173),
      strictPort: true,
      proxy: {
        '/api': {
          target: env.CAMPUS_API_PROXY_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
          ws: true,
        },
      },
    },
  }
})

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // El service worker es un archivo propio (src/sw.js) en vez de uno generado —
      // conserva la lógica hecha a mano (push notifications, estrategias por ruta) y
      // solo delega en Workbox el precache de los assets versionados del build.
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.js',
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2,woff}'],
      },
      // No activo el SW en dev para no interferir con hot-reload
      devOptions: { enabled: false },
      // Usamos el manifest.json manual en /public
      manifest: false,
      injectRegister: null,
    }),
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    // Permite exponer el dev server vía túnel (cloudflared/ngrok) para probar
    // integraciones que exigen una URL pública, como el checkout de Wompi.
    allowedHosts: ['.trycloudflare.com', '.ngrok-free.app', '.ngrok-free.dev', '.ngrok.io'],
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})

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
        // OJO: "html" se sacó a propósito de esta lista. Workbox precachea el HTML como
        // cache-first por defecto (con directoryIndex, "/" resuelve directo contra esa entrada
        // precacheada) — eso corre ANTES que el fetch handler propio de src/sw.js (network-first
        // para navegaciones), así que una pestaña podía quedar sirviendo el index.html viejo para
        // siempre, sin darle nunca la oportunidad de detectar que hay una versión nueva (el propio
        // arreglo de "recargar cuando cambia el SW" vivía adentro de ese HTML atrapado). Sin "html"
        // acá, ninguna navegación pasa por el precache — siempre va network-first al servidor real.
        // El fallback offline en sw.js (matchPrecache('/index.html')) ya no encuentra nada, así que
        // sin conexión ahora se ve el aviso "Sin conexión" en vez de un shell potencialmente viejo —
        // trade-off correcto: preferir un error claro sobre servir una app desactualizada.
        globPatterns: ['**/*.{js,css,ico,png,svg,woff2,woff}'],
        // El bundle principal ya pasó los 2MiB por defecto de Workbox (varios módulos admin
        // nuevos en poco tiempo) — sin esto, el build entero falla en el paso del service
        // worker aunque el bundle de la app haya compilado bien. Subir el límite es la salida
        // correcta acá, no dividir el bundle a la fuerza solo para esquivar un precache check.
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
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

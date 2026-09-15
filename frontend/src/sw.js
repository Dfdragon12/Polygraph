import { precacheAndRoute, cleanupOutdatedCaches, matchPrecache } from 'workbox-precaching'

const API_CACHE  = 'polygraph-api-v1'
const FONT_CACHE = 'polygraph-fonts-v1'

/* ── Precache de los assets del build (JS/CSS/HTML con hash) ──
   self.__WB_MANIFEST lo reemplaza vite-plugin-pwa en build time con la lista real de
   archivos generados — este es el único punto de integración obligatorio con Workbox. */
precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

self.skipWaiting()

self.addEventListener('activate', event => {
  const VALID = [API_CACHE, FONT_CACHE]
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => !VALID.includes(k) && !k.startsWith('workbox-precache')).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  )
})

/* ── Fetch ── */
self.addEventListener('fetch', event => {
  const { request } = event
  const url = new URL(request.url)

  // Solo GET — el resto (y lo precacheado por precacheAndRoute) ya se maneja aparte
  if (request.method !== 'GET') return

  // Cross-origin — dejar pasar, salvo fuentes de Google
  if (url.origin !== self.location.origin && !url.hostname.includes('fonts.googleapis') && !url.hostname.includes('fonts.gstatic')) return

  // Fuentes de Google — cache first (largo plazo)
  if (url.hostname.includes('fonts.gstatic.com') || url.hostname.includes('fonts.googleapis.com')) {
    event.respondWith(cacheFirst(request, FONT_CACHE))
    return
  }

  // API — network first, fallback a cache (5 min)
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(request, API_CACHE, 8000))
    return
  }

  // Navegación (rutas SPA) — network first, fallback al index.html precacheado. OJO:
  // matchPrecache() puede resolver a undefined si no encuentra la entrada — respondWith(undefined)
  // revienta con "Failed to convert value to 'Response'" (visto en consola con el túnel de ngrok
  // inestable), por eso el fallback final a una Response sintética real, nunca undefined.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .catch(() => matchPrecache('/index.html'))
        .then(response => response ?? new Response(
          '<h1>Sin conexión</h1><p>No se pudo cargar la página — revisa tu conexión e intenta de nuevo.</p>',
          { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } },
        ))
    )
  }

  // Cualquier otro asset estático ya lo resuelve precacheAndRoute() arriba.
})

/* ── Estrategias ── */

async function cacheFirst(request, cacheName) {
  const cached = await caches.match(request)
  if (cached) return cached
  const response = await fetch(request)
  if (response.ok) {
    const cache = await caches.open(cacheName)
    cache.put(request, response.clone())
  }
  return response
}

async function networkFirst(request, cacheName, timeoutMs = 6000) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(request, { signal: controller.signal })
    clearTimeout(timeout)
    if (response.ok) {
      const cache = await caches.open(cacheName)
      cache.put(request, response.clone())
    }
    return response
  } catch {
    clearTimeout(timeout)
    const cached = await caches.match(request)
    return cached ?? new Response(JSON.stringify({ error: 'Sin conexión' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    })
  }
}

/* ── Push notifications ── */
self.addEventListener('push', event => {
  if (!event.data) return
  const data = event.data.json()
  event.waitUntil(
    self.registration.showNotification(data.title ?? 'Polygraph ERP', {
      body: data.body ?? '',
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      data: data.url ? { url: data.url } : undefined,
    })
  )
})

self.addEventListener('notificationclick', event => {
  event.notification.close()
  if (event.notification.data?.url) {
    event.waitUntil(clients.openWindow(event.notification.data.url))
  }
})

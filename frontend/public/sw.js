const APP_CACHE   = 'polygraph-app-v1'
const API_CACHE   = 'polygraph-api-v1'
const FONT_CACHE  = 'polygraph-fonts-v1'

const PRECACHE_URLS = ['/', '/index.html', '/icon.svg', '/favicon.svg']

/* ── Install ── */
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(APP_CACHE)
      .then(cache => cache.addAll(PRECACHE_URLS).catch(() => {}))
      .then(() => self.skipWaiting())
  )
})

/* ── Activate — purge old caches ── */
self.addEventListener('activate', event => {
  const VALID = [APP_CACHE, API_CACHE, FONT_CACHE]
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => !VALID.includes(k)).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  )
})

/* ── Fetch ── */
self.addEventListener('fetch', event => {
  const { request } = event
  const url = new URL(request.url)

  // Solo GET
  if (request.method !== 'GET') return

  // Cross-origin — dejar pasar
  if (url.origin !== self.location.origin && !url.hostname.includes('fonts.googleapis')) return

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

  // Navegación (rutas SPA) — network first, fallback a index.html
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .catch(() => caches.match('/index.html'))
    )
    return
  }

  // Assets estáticos (JS, CSS, imágenes) — stale-while-revalidate
  event.respondWith(staleWhileRevalidate(request, APP_CACHE))
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

async function staleWhileRevalidate(request, cacheName) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)
  const fetchPromise = fetch(request).then(response => {
    if (response.ok) cache.put(request, response.clone())
    return response
  }).catch(() => null)
  return cached ?? fetchPromise
}

/* ── Push notifications (futuro) ── */
self.addEventListener('push', event => {
  if (!event.data) return
  const data = event.data.json()
  event.waitUntil(
    self.registration.showNotification(data.title ?? 'Polygraph ERP', {
      body: data.body ?? '',
      icon: '/icon.svg',
      badge: '/icon.svg',
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

// MatMinne Service Worker v3
const CACHE = 'matminne-v3';

const PRECACHE = [
  '/manifest.json',
  '/icons/icon.svg',
  '/icons/icon-maskable.svg',
  '/offline.html',
];

// ── INSTALL: pre-cache critical assets ──────────────────────
self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE)
      .then(c => c.addAll(PRECACHE))
      .then(() => self.skipWaiting())
  );
});

// ── ACTIVATE: clear old caches ───────────────────────────────
self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => k !== CACHE).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

// ── FETCH ────────────────────────────────────────────────────
self.addEventListener('fetch', e => {
  const req = e.request;
  const url = new URL(req.url);

  // Only handle GET requests from our own origin
  if (req.method !== 'GET') return;
  if (url.origin !== self.location.origin) return;

  // Skip auth, CSRF, API endpoints (always network)
  const skip = ['/oauth2', '/login', '/api/', '/logout', '/actuator'];
  if (skip.some(p => url.pathname.startsWith(p))) return;

  // Static assets: cache-first, background update
  if (url.pathname.match(/\.(css|js|svg|png|jpg|jpeg|webp|woff2?|ico|json)$/)) {
    e.respondWith(cacheFirst(req));
    return;
  }

  // HTML pages: network-first with cache fallback
  e.respondWith(networkFirst(req));
});

// Cache-first: serve from cache, update in background
async function cacheFirst(req) {
  const cached = await caches.match(req);
  if (cached) {
    // Background revalidation
    fetch(req).then(res => {
      if (res && res.ok) {
        caches.open(CACHE).then(c => c.put(req, res));
      }
    }).catch(() => {});
    return cached;
  }
  try {
    const res = await fetch(req);
    if (res && res.ok) {
      const clone = res.clone();
      caches.open(CACHE).then(c => c.put(req, clone));
    }
    return res;
  } catch {
    return new Response('', { status: 503 });
  }
}

// Network-first: try network, fall back to cache, then offline page
async function networkFirst(req) {
  try {
    const res = await fetch(req);
    if (res && res.ok) {
      const clone = res.clone();
      caches.open(CACHE).then(c => c.put(req, clone));
    }
    return res;
  } catch {
    const cached = await caches.match(req);
    if (cached) return cached;
    // Return offline page
    const offline = await caches.match('/offline.html');
    return offline || new Response('<h1>Offline</h1>', {
      headers: { 'Content-Type': 'text/html' }
    });
  }
}

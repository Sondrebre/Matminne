// MatMinne Service Worker
const CACHE = 'matminne-v1';

const PRECACHE = [
  '/manifest.json',
  '/icons/icon.svg',
];

// Installer — forhåndslast kritiske ressurser
self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE).then(c => c.addAll(PRECACHE)).then(() => self.skipWaiting())
  );
});

// Aktiver — rydd gamle cacher
self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Fetch — cache-first for statiske filer, network-first for sider
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);

  // Hopp over: ikke-GET, cross-origin, OAuth, API-kall
  if (e.request.method !== 'GET') return;
  if (!url.origin.includes(self.location.origin.replace('https://','').replace('http://',''))) return;
  if (url.pathname.startsWith('/oauth2')) return;
  if (url.pathname.startsWith('/api/')) return;
  if (url.pathname.startsWith('/login')) return;

  // Statiske ressurser: cache-first
  if (url.pathname.match(/\.(css|js|svg|png|jpg|jpeg|webp|woff2?|ico)$/)) {
    e.respondWith(
      caches.match(e.request).then(cached => {
        if (cached) return cached;
        return fetch(e.request).then(res => {
          const clone = res.clone();
          caches.open(CACHE).then(c => c.put(e.request, clone));
          return res;
        });
      })
    );
    return;
  }

  // Sider: network-first, fall back til cache
  e.respondWith(
    fetch(e.request)
      .then(res => {
        if (res.ok) {
          const clone = res.clone();
          caches.open(CACHE).then(c => c.put(e.request, clone));
        }
        return res;
      })
      .catch(() => caches.match(e.request))
  );
});

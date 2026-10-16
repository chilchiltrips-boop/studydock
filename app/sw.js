const CACHE='studydock-shell-v1';
const SHELL=['./','./index.html','./styles.css','./app.js','./model.mjs','./seed.mjs','./manifest.webmanifest','./assets/logo.svg','./assets/flower.svg','./assets/water-cycle.svg','./assets/experiment-graph.svg'];
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;let url=new URL(e.request.url);if(url.origin!==self.location.origin)return;e.respondWith(caches.match(e.request).then(hit=>hit||fetch(e.request).then(response=>{if(response.ok){const copy=response.clone();caches.open(CACHE).then(c=>c.put(e.request,copy));}return response;})));});

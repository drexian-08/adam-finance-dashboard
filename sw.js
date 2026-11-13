const CACHE='adam-finance-v64';
const FILES=['./','./index.html','./manifest.webmanifest','./assets/liquid-glass.css?v=14','./assets/glisse-liquid-glass.css?v=34'];
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('adam-finance-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET')return;
  const url=new URL(event.request.url);
  if(url.origin===self.location.origin&&(url.pathname.endsWith('/')||url.pathname.endsWith('.html'))){
    event.respondWith(fetch(event.request).then(response=>{const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(event.request,copy));return response;}).catch(()=>caches.match(event.request).then(cached=>cached||caches.match('./index.html'))));
  }else{
    event.respondWith(caches.match(event.request).then(cached=>cached||fetch(event.request)));
  }
});
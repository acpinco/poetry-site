// Bump the version to discard everything cached by earlier service workers.
const CACHE = "think-or-drink-public-v2";
const OFFLINE_PAGE = "/offline.html";
// Network-first with a small fallback cache: enough for recently read pages to
// open offline without letting the cache grow forever.
const MAX_ENTRIES = 60;

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.add(OFFLINE_PAGE)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)),
        ),
      ),
  );
  self.clients.claim();
});

/** Deletes the oldest entries (cache keys are kept in insertion order). */
async function trim(cache) {
  const entries = (await cache.keys()).filter(
    (request) => new URL(request.url).pathname !== OFFLINE_PAGE,
  );
  await Promise.all(
    entries
      .slice(0, Math.max(0, entries.length - MAX_ENTRIES))
      .map((request) => cache.delete(request)),
  );
}

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (
    request.method !== "GET" ||
    url.origin !== self.location.origin ||
    url.pathname.startsWith("/api/")
  )
    return;

  event.respondWith(
    (async () => {
      try {
        const response = await fetch(request);
        if (
          response.ok &&
          (request.mode === "navigate" ||
            ["script", "style", "image", "font"].includes(request.destination))
        ) {
          const cache = await caches.open(CACHE);
          event.waitUntil(
            cache.put(request, response.clone()).then(() => trim(cache)),
          );
        }
        return response;
      } catch {
        return (
          (await caches.match(request)) ||
          (request.mode === "navigate"
            ? caches.match(OFFLINE_PAGE)
            : Response.error())
        );
      }
    })(),
  );
});

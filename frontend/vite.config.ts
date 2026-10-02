import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? "http://app:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    // Mirror deploy/Caddyfile: the backend renders the landing page, poem,
    // poet, and Poem of the Day pages; Vite serves only the React app.
    proxy: {
      "^/$": apiProxyTarget,
      "/poem-of-the-day": apiProxyTarget,
      "/api": apiProxyTarget,
      "/swagger-ui": apiProxyTarget,
      "/v3": apiProxyTarget,
      "/poems": apiProxyTarget,
      "/poets": apiProxyTarget,
      "/sitemap.xml": apiProxyTarget,
      "/robots.txt": apiProxyTarget,
      "/share.png": apiProxyTarget,
    },
  },
});

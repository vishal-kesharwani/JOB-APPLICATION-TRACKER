import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In production the app is served by nginx, which reverse-proxies /api/* to the
// three backend services. For `npm run dev` we replicate that proxy here so the
// frontend code can always just call relative /api/v1/... paths.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api/v1/applications": "http://localhost:8081",
      "/api/v1/reminders": "http://localhost:8082",
      "/api/v1/analytics": "http://localhost:8083",
    },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
  },
});

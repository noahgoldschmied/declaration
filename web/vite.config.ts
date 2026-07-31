import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Override with `BACKEND_PORT=8081 pnpm dev` if 8080 is taken locally.
const backendPort = process.env.BACKEND_PORT ?? "8080";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Dev-time proxy to the Spring Boot server (`cd server && ./gradlew bootRun`).
    // Keeps the browser client same-origin so no CORS config is needed on the server.
    proxy: {
      "/api": `http://localhost:${backendPort}`,
      "/ws": {
        target: `ws://localhost:${backendPort}`,
        ws: true,
      },
    },
  },
});

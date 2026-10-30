import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import path from "node:path"
import { defineConfig } from "vite"
import { buildRoutesPlugin } from "./build-routes.ts"

// https://vite.dev/config/
export default defineConfig({
  resolve: {
    alias: {
      "@dewey": path.resolve(import.meta.dirname, "src"),
      "@": path.resolve(import.meta.dirname, "src"),
    },
  },
  plugins: [buildRoutesPlugin(), tailwindcss(), react()],
  server: {
    host: "0.0.0.0",
    proxy: {
      "/api": `http://localhost:${process.env.DEWEY_PORT ?? 8080}`,
    },
  },
})

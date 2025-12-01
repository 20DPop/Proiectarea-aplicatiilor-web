import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            // Orice cerere care începe cu /api va fi trimisă către Backend (Java)
            '/api': {
                target: 'http://localhost:3000',
                changeOrigin: true,
                secure: false,
            },
            // Proxy și pentru WebSocket (dacă vrei să fie pe același port în dev)
            '/ws': {
                target: 'ws://localhost:3000',
                ws: true,
            }
        }
    }
})
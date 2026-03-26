/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,jsx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50:  '#eff6ff',
          100: '#dbeafe',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          900: '#1e3a8a',
        },
        semaforo: {
          pendiente:    '#f59e0b',
          programando:  '#3b82f6',
          ejecucion:    '#8b5cf6',
          finalizado:   '#10b981',
          publicado:    '#06b6d4',
          cancelado:    '#ef4444',
          reprogramado: '#f97316',
        },
      },
    },
  },
  plugins: [],
}

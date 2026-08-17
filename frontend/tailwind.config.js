/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,jsx}',
  ],
  theme: {
    extend: {
      colors: {
        // Paleta de marca — anclada en #E83535 (oklch(0.61 0.21 26)) como peso 600,
        // el mismo tono que reemplaza al índigo como color de acción principal.
        primary: {
          50:  'oklch(0.97 0.015 26)',
          100: 'oklch(0.94 0.035 26)',
          200: 'oklch(0.88 0.065 26)',
          300: 'oklch(0.80 0.115 26)',
          400: 'oklch(0.70 0.175 26)',
          500: 'oklch(0.65 0.205 26)',
          600: 'oklch(0.61 0.21 26)',
          700: 'oklch(0.53 0.19 26)',
          800: 'oklch(0.45 0.16 26)',
          900: 'oklch(0.38 0.13 26)',
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
      keyframes: {
        fadeIn: {
          '0%':   { opacity: 0, transform: 'translateY(4px)' },
          '100%': { opacity: 1, transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-in': 'fadeIn .2s ease-out',
      },
    },
  },
  plugins: [],
}

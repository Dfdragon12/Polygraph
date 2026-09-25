/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,jsx}',
  ],
  theme: {
    extend: {
      // Reemplaza el font-sans de Tailwind (que por defecto usa la fuente nativa del sistema —
      // San Francisco en Mac, Segoe UI en Windows) por Arial en toda la aplicación. Como nada más
      // en el código fija un font-family propio, esto solo hace que html/body (que ya usan
      // font-sans vía el preflight de Tailwind) hereden Arial en cada elemento.
      fontFamily: {
        sans: ['Arial', 'Helvetica', 'sans-serif'],
      },
      colors: {
        // Paleta de marca — coral suave anclado en #CD575D (oklch(0.61 0.15 20)) como peso 600.
        // Reemplaza al rojo saturado anterior (hue 26, mismo esquema de claridad, menos croma) por
        // pedido explícito: mismo tono cálido, pero que no se lea como alerta/error.
        primary: {
          50:  'oklch(0.97 0.011 20)',
          100: 'oklch(0.94 0.03 20)',
          200: 'oklch(0.88 0.045 20)',
          300: 'oklch(0.80 0.08 20)',
          400: 'oklch(0.70 0.12 20)',
          500: 'oklch(0.65 0.14 20)',
          600: 'oklch(0.61 0.15 20)',
          700: 'oklch(0.53 0.14 20)',
          800: 'oklch(0.45 0.11 20)',
          900: 'oklch(0.38 0.09 20)',
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
        scaleIn: {
          '0%':   { opacity: 0, transform: 'scale(0.95) translateY(-6px)' },
          '100%': { opacity: 1, transform: 'scale(1) translateY(0)' },
        },
        popIn: {
          '0%':   { transform: 'scale(0.5)' },
          '60%':  { transform: 'scale(1.2)' },
          '100%': { transform: 'scale(1)' },
        },
        // Desliza el SVG del monitor de latido exactamente la mitad de su ancho — como el path
        // se repite cada ANCHO_UNIDAD y el SVG tiene el doble del ancho visible, el final
        // engancha perfecto con el principio (loop sin salto). Usado en HeartbeatMonitor.jsx.
        heartbeatScroll: {
          '0%':   { transform: 'translateX(0)' },
          '100%': { transform: 'translateX(-50%)' },
        },
        // Zoom lento y continuo ("Ken Burns") para las fotos del carrusel de marca — corre
        // siempre por CSS puro, nunca depende de un cambio de estado de React que dispare la
        // transición (evita el problema de que un transition agregado y cambiado en el mismo
        // render a veces no anima). Usado en BrandShowcase.jsx.
        kenBurns: {
          '0%, 100%': { transform: 'scale(1)' },
          '50%':      { transform: 'scale(1.08)' },
        },
        // Deriva grande y continua para los blobs de fondo del panel oscuro (coral/negro) —
        // recorrido amplio (8%/10%) a propósito, para que el movimiento del FONDO del panel se
        // note incluso detrás del carrusel de fotos. Usado en Login/Register/RecuperarPassword/
        // ResetPassword.
        floatBg: {
          '0%, 100%': { transform: 'translate(0, 0) scale(1)' },
          '33%':      { transform: 'translate(8%, -10%) scale(1.15)' },
          '66%':      { transform: 'translate(-8%, 8%) scale(0.9)' },
        },
      },
      animation: {
        'fade-in': 'fadeIn .2s ease-out',
        'scale-in': 'scaleIn .18s ease-out',
        'pop-in': 'popIn .35s cubic-bezier(0.34, 1.56, 0.64, 1)',
        'heartbeat-scroll': 'heartbeatScroll 3s linear infinite',
        'ken-burns': 'kenBurns 12s ease-in-out infinite',
        'float-bg': 'floatBg 8s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}

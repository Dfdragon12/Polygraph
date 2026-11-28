import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import { activarEtiquetasTablas } from './utils/etiquetasTablas'
import './index.css'

// PC-08: después de publicar una versión nueva, una pestaña que sigue abierta pide pedazos (React.lazy) que ya no existen en el servidor. Vite avisa con este evento; se recarga
// UNA vez para traer la versión nueva (la marca evita un bucle si el fallo es otro, como estar sin conexión).
window.addEventListener('vite:preloadError', (evento) => {
  evento.preventDefault()
  try {
    if (sessionStorage.getItem('polygraph.recargaPorVersion') === '1') return
    sessionStorage.setItem('polygraph.recargaPorVersion', '1')
  } catch { /* sin almacenamiento: se recarga igual */ }
  window.location.reload()
})
// La marca vale 10 s: si la recarga trajo la versión nueva y todo anda, una publicación futura vuelve a poder recargar.
setTimeout(() => { try { sessionStorage.removeItem('polygraph.recargaPorVersion') } catch { /* nada */ } }, 10000)

// En celular las tablas "table-stack" son tarjetas: cada celda toma el título de su columna
activarEtiquetasTablas()

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 5 * 60 * 1000,
    },
  },
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </BrowserRouter>
  </StrictMode>,
)

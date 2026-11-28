// PC-08 · Lo que se ve mientras llega el pedazo de la aplicación que pide la pantalla (carga por demanda, React.lazy). Anuncia «Cargando» a un lector de pantalla.
export default function Cargando({ pantallaCompleta = false }) {
  return (
    <div role="status" aria-live="polite" className={`flex items-center justify-center ${pantallaCompleta ? 'min-h-screen bg-slate-50' : 'py-24'}`}>
      <div aria-hidden="true" className="h-9 w-9 animate-spin rounded-full border-4 border-primary-600 border-t-transparent" />
      <span className="sr-only">Cargando…</span>
    </div>
  )
}

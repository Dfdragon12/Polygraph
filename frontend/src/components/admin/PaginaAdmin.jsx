// Piezas comunes de las pantallas de administración, para que todas se vean iguales:
// encabezado (sección · título · descripción · acciones), tarjetas de sección, indicadores,
// avisos de error, estado vacío, esqueleto de carga y barra fija de "cambios sin guardar".

export function EncabezadoPagina({ seccion, titulo, descripcion, icono, meta, acciones }) {
  return (
    <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div className="flex items-start gap-4 min-w-0">
        {icono && (
          <div className="hidden sm:flex shrink-0 w-12 h-12 rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 text-white items-center justify-center shadow-sm shadow-primary-600/20">
            {icono}
          </div>
        )}
        <div className="min-w-0">
          {seccion && <p className="text-[11px] font-semibold uppercase tracking-wider text-primary-600">{seccion}</p>}
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">{titulo}</h1>
          {descripcion && <p className="mt-1 max-w-3xl text-sm leading-relaxed text-gray-500">{descripcion}</p>}
          {meta && <div className="mt-2 flex flex-wrap items-center gap-2">{meta}</div>}
        </div>
      </div>
      {acciones && <div className="flex flex-wrap items-center gap-2">{acciones}</div>}
    </header>
  )
}

export function Etiqueta({ children, tono = 'gris' }) {
  const tonos = {
    gris: 'bg-gray-100 text-gray-600 ring-gray-500/10',
    primario: 'bg-primary-50 text-primary-700 ring-primary-600/20',
    verde: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
    ambar: 'bg-amber-50 text-amber-700 ring-amber-600/20',
    rojo: 'bg-red-50 text-red-700 ring-red-600/20',
  }
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-medium ring-1 ring-inset ${tonos[tono] ?? tonos.gris}`}>
      {children}
    </span>
  )
}

export function TarjetaSeccion({ titulo, descripcion, icono, acciones, children, sinRelleno = false, className = '' }) {
  return (
    <section className={`overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm ${className}`}>
      {(titulo || acciones) && (
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-gray-100 px-5 py-4">
          <div className="flex items-start gap-3 min-w-0">
            {icono && <div className="shrink-0 mt-0.5 w-8 h-8 rounded-lg bg-gray-50 text-gray-500 ring-1 ring-gray-200 flex items-center justify-center">{icono}</div>}
            <div className="min-w-0">
              {titulo && <h2 className="text-sm font-semibold text-gray-900">{titulo}</h2>}
              {descripcion && <p className="mt-0.5 text-xs leading-relaxed text-gray-500">{descripcion}</p>}
            </div>
          </div>
          {acciones && <div className="flex flex-wrap items-center gap-2">{acciones}</div>}
        </div>
      )}
      <div className={sinRelleno ? '' : 'p-5'}>{children}</div>
    </section>
  )
}

const TONOS_INDICADOR = {
  primario: 'bg-primary-50 text-primary-600',
  verde: 'bg-emerald-50 text-emerald-600',
  ambar: 'bg-amber-50 text-amber-600',
  rojo: 'bg-red-50 text-red-600',
  gris: 'bg-gray-100 text-gray-500',
}

export function Indicador({ etiqueta, valor, detalle, icono, tono = 'primario', alerta = false }) {
  return (
    <div className={`rounded-2xl border bg-white p-4 shadow-sm ${alerta ? 'border-red-200 ring-1 ring-red-100' : 'border-gray-200'}`}>
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs font-medium text-gray-500">{etiqueta}</p>
        {icono && <span className={`w-8 h-8 rounded-lg flex items-center justify-center ${TONOS_INDICADOR[alerta ? 'rojo' : tono]}`}>{icono}</span>}
      </div>
      <p className={`mt-2 text-2xl font-bold tabular-nums tracking-tight ${alerta ? 'text-red-700' : 'text-gray-900'}`}>{valor}</p>
      {detalle && <p className="mt-0.5 text-[11px] text-gray-400">{detalle}</p>}
    </div>
  )
}

export function AlertaError({ mensaje, onCerrar }) {
  if (!mensaje) return null
  return (
    <div role="alert" className="mb-5 flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
      <svg className="mt-0.5 h-5 w-5 shrink-0 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
      </svg>
      <p className="flex-1">{mensaje}</p>
      {onCerrar && (
        <button type="button" onClick={onCerrar} aria-label="Cerrar" className="text-red-400 hover:text-red-600">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}><path strokeLinecap="round" d="M6 18L18 6M6 6l12 12" /></svg>
        </button>
      )}
    </div>
  )
}

export function EstadoVacio({ icono, titulo, descripcion, accion }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      {icono && <div className="mb-3 w-12 h-12 rounded-full bg-gray-50 text-gray-400 ring-1 ring-gray-200 flex items-center justify-center">{icono}</div>}
      <p className="text-sm font-medium text-gray-700">{titulo}</p>
      {descripcion && <p className="mt-1 max-w-sm text-xs text-gray-500">{descripcion}</p>}
      {accion && <div className="mt-4">{accion}</div>}
    </div>
  )
}

/** Bloques grises animados con la forma de la página mientras carga (mejor que un spinner solo). */
export function EsqueletoPagina({ indicadores = 4, bloques = 2 }) {
  return (
    <div className="animate-pulse space-y-5" aria-hidden="true">
      {indicadores > 0 && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {Array.from({ length: indicadores }).map((_, i) => <div key={i} className="h-24 rounded-2xl bg-gray-100" />)}
        </div>
      )}
      {Array.from({ length: bloques }).map((_, i) => <div key={i} className="h-48 rounded-2xl bg-gray-100" />)}
    </div>
  )
}

/** Barra fija abajo cuando hay cambios sin guardar — el botón de guardar siempre a la vista. */
export function BarraCambios({ visible, mensaje, onDescartar, onGuardar, guardando, deshabilitado, textoGuardar = 'Guardar cambios' }) {
  if (!visible) return null
  return (
    <div className="sticky bottom-4 z-20 mt-6">
      <div className="mx-auto flex max-w-3xl flex-wrap items-center gap-3 rounded-2xl border border-gray-200 bg-white/95 px-4 py-3 shadow-lg shadow-gray-900/10 backdrop-blur">
        <span className="flex items-center gap-2 text-sm text-gray-700">
          <span className="h-2 w-2 rounded-full bg-amber-500" />
          {mensaje ?? 'Tienes cambios sin guardar'}
        </span>
        <div className="ml-auto flex gap-2">
          <button type="button" onClick={onDescartar} disabled={guardando}
            className="rounded-lg border border-gray-200 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            Descartar
          </button>
          <button type="button" onClick={onGuardar} disabled={guardando || deshabilitado}
            className="rounded-lg bg-primary-600 px-5 py-2 text-sm font-semibold text-white shadow-sm hover:bg-primary-700 disabled:opacity-50">
            {guardando ? 'Guardando…' : textoGuardar}
          </button>
        </div>
      </div>
    </div>
  )
}

export const botonPrimario = 'inline-flex items-center gap-1.5 rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-primary-700 disabled:opacity-50 transition-colors'
export const botonSecundario = 'inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3.5 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 disabled:opacity-50 transition-colors'
export const botonPeligro = 'inline-flex items-center gap-1.5 rounded-lg border border-red-200 bg-white px-3.5 py-2 text-sm font-medium text-red-600 shadow-sm hover:bg-red-50 disabled:opacity-40 transition-colors'

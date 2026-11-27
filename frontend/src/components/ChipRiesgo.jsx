// El riesgo de un estudio en una etiqueta con TEXTO (nunca un punto de color solo): «BAJO 0,0», «ALTO · crítico» o «Sin calificar». La pasta
// (pendiente · preliminar · emitida) NO va aquí: es un indicador del estudio y ya está en la columna Avance.
// El verde de BAJO (riesgo) no es el verde de Finalizado (estado): por eso el riesgo va siempre escrito. Usada por Solicitudes y la bandeja del gestor.

const ESTILOS = {
  BAJO: 'bg-green-50 text-green-800 ring-green-700/30',
  MEDIO: 'bg-amber-50 text-amber-900 ring-amber-700/30',
  ALTO: 'bg-red-50 text-red-800 ring-red-700/30',
}

const un = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

export default function ChipRiesgo({ nivel, puntaje, critico = false, className = '' }) {
  const base = `inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-[11px] font-bold ring-1 ring-inset tabular-nums ${className}`
  if (!nivel) return <span className={`${base} bg-slate-100 text-slate-700 ring-slate-500/30`}>Sin calificar</span>
  return (
    <span className={`${base} ${ESTILOS[nivel] ?? ESTILOS.MEDIO}`}>
      {nivel}{puntaje != null ? ` ${un.format(Number(puntaje))}` : ''}{critico ? ' · crítico' : ''}
    </span>
  )
}

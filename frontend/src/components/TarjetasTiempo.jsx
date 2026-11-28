import { COLOR_ESTADO_TIEMPO } from './EstadoTiempo'

// Tarjetas del tablero del cliente que leen el resumen ÚNICO de tiempos (GET /tiempos/resumen): "En curso" dividida por estado de tiempo,
// "Cumplimiento de entregas" y el aviso de vencidas.

const EN_CURSO = ['PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION', 'REPROGRAMADO']
const PARTES = [
  { estado: 'A_TIEMPO', etiqueta: 'A tiempo' },
  { estado: 'POR_VENCER', etiqueta: 'Por vencer' },
  { estado: 'VENCIDA', etiqueta: 'Vencidas' },
  { estado: 'EN_PAUSA', etiqueta: 'En pausa' },
]

/** Cuántas solicitudes EN CURSO (pendiente, programando, en ejecución, reprogramado) hay en cada estado de tiempo. */
export function enCursoPorTiempo(resumen) {
  const cuenta = { A_TIEMPO: 0, POR_VENCER: 0, VENCIDA: 0, EN_PAUSA: 0 }
  for (const fila of resumen?.matriz ?? []) {
    if (!EN_CURSO.includes(fila.etapa)) continue
    for (const p of PARTES) cuenta[p.estado] += fila.porEstadoTiempo?.[p.estado] ?? 0
  }
  return cuenta
}

export function TarjetaEnCurso({ resumen, onVer, className = '' }) {
  const cuenta = enCursoPorTiempo(resumen)
  const total = PARTES.reduce((s, p) => s + cuenta[p.estado], 0)
  return (
    <section className={`rounded-2xl border border-gray-200 bg-white p-4 shadow-sm ${className}`} aria-label="Solicitudes en curso por estado de tiempo">
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-xs font-medium text-gray-600">En curso</p>
        <p className="text-3xl font-bold tabular-nums tracking-tight text-gray-900">{resumen ? total : '—'}</p>
      </div>
      <p className="mt-0.5 text-xs text-gray-600">{total === 0 && resumen ? 'Ninguna solicitud en proceso' : 'Estudios en proceso, según su plazo en días hábiles'}</p>
      <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
        {PARTES.map(p => (
          <button key={p.estado} type="button" onClick={() => onVer(p.estado)} disabled={!resumen}
            className="rounded-xl border border-gray-100 px-3 py-2 text-left transition-colors hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500">
            <span className="flex items-center gap-1.5 text-xs text-gray-700">
              <span aria-hidden="true" className="h-2 w-2 rounded-full" style={{ backgroundColor: COLOR_ESTADO_TIEMPO[p.estado] }} />{p.etiqueta}
            </span>
            <span className="mt-0.5 block text-xl font-bold tabular-nums text-gray-900">{resumen ? cuenta[p.estado] : '—'}</span>
          </button>
        ))}
      </div>
    </section>
  )
}

export function TarjetaCumplimiento({ cumplimiento, dias = 90, className = '' }) {
  const hayDatos = cumplimiento && cumplimiento.publicadas > 0
  const pct = hayDatos ? cumplimiento.porcentajeATiempo : null
  return (
    <section className={`rounded-2xl border border-gray-200 bg-white p-4 shadow-sm ${className}`} aria-label="Cumplimiento de entregas">
      <p className="text-xs font-medium text-gray-600">Cumplimiento de entregas</p>
      {hayDatos ? (
        <>
          <p className="mt-1 text-3xl font-bold tabular-nums tracking-tight text-gray-900">{String(pct).replace('.', ',')} %</p>
          <div className="mt-2 h-2.5 w-full overflow-hidden rounded-full bg-gray-100" role="img" aria-label={`${pct} % de las entregas fueron a tiempo`}>
            <div className="h-full rounded-full bg-emerald-600" style={{ width: `${pct}%` }} />
          </div>
          <p className="mt-2 text-xs text-gray-700">
            {cumplimiento.aTiempo} de {cumplimiento.publicadas} {cumplimiento.publicadas === 1 ? 'entrega' : 'entregas'} a tiempo
            {cumplimiento.tarde > 0 ? ` · ${cumplimiento.tarde} con retraso` : ''}
          </p>
          <p className="text-[11px] text-gray-500">Últimos {dias} días</p>
        </>
      ) : (
        <p className="mt-2 text-sm text-gray-600">{cumplimiento ? `Aún no hay entregas en los últimos ${dias} días.` : '—'}</p>
      )}
    </section>
  )
}

export function AvisoVencidas({ cantidad, onVer }) {
  if (!cantidad) return null
  return (
    <div role="alert" className="flex flex-wrap items-center gap-3 rounded-2xl border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">
      <span aria-hidden="true" className="h-2.5 w-2.5 shrink-0 rounded-full bg-red-600" />
      <p>Tienes <strong>{cantidad} {cantidad === 1 ? 'solicitud vencida' : 'solicitudes vencidas'}</strong>: pasaron su fecha de compromiso.</p>
      <button type="button" onClick={onVer}
        className="ml-auto whitespace-nowrap rounded-lg bg-red-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-red-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500">
        Ver vencidas
      </button>
    </div>
  )
}

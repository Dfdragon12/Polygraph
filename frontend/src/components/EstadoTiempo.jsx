// Estado de tiempo de una solicitud (A tiempo, Por vencer, Vencida, En pausa, Entregada a tiempo / con retraso). Lo calcula el servidor en días
// hábiles de Colombia y trae su texto listo ("Vencida · 17 días hábiles"): toda pantalla lo muestra con este chip para decir siempre lo mismo.
// El texto siempre está presente (el color solo refuerza) y los pares de color cumplen contraste ≥ 4,5:1 (texto 800 sobre fondo 50).

const ESTILOS = {
  A_TIEMPO:           { clase: 'bg-green-50 text-green-800 ring-green-700/30',       punto: 'bg-green-600' },
  POR_VENCER:         { clase: 'bg-amber-50 text-amber-800 ring-amber-700/30',       punto: 'bg-amber-600' },
  VENCIDA:            { clase: 'bg-red-50 text-red-800 ring-red-700/30',             punto: 'bg-red-600' },
  EN_PAUSA:           { clase: 'bg-slate-100 text-slate-700 ring-slate-600/30',      punto: 'bg-slate-500' },
  ENTREGADA_A_TIEMPO: { clase: 'bg-emerald-50 text-emerald-800 ring-emerald-700/30', punto: 'bg-emerald-600' },
  ENTREGADA_TARDE:    { clase: 'bg-orange-50 text-orange-800 ring-orange-700/30',    punto: 'bg-orange-600' },
}

/** Etiqueta corta de cada estado (para tarjetas, filtros y la matriz). */
export const NOMBRE_ESTADO_TIEMPO = {
  A_TIEMPO: 'A tiempo', POR_VENCER: 'Por vencer', VENCIDA: 'Vencidas', EN_PAUSA: 'En pausa',
  ENTREGADA_A_TIEMPO: 'Entregadas a tiempo', ENTREGADA_TARDE: 'Entregadas con retraso',
}

export const COLOR_ESTADO_TIEMPO = {
  A_TIEMPO: '#16a34a', POR_VENCER: '#d97706', VENCIDA: '#dc2626', EN_PAUSA: '#64748b', ENTREGADA_A_TIEMPO: '#059669', ENTREGADA_TARDE: '#ea580c',
}

/** Opciones del filtro "Tiempo" de las listas. El valor es el que entiende el servidor (uno o varios estados separados por coma). */
export const OPCIONES_TIEMPO = [
  { valor: 'VENCIDA', etiqueta: 'Vencidas' },
  { valor: 'POR_VENCER', etiqueta: 'Por vencer' },
  { valor: 'EN_PAUSA', etiqueta: 'En pausa' },
  { valor: 'A_TIEMPO', etiqueta: 'A tiempo' },
  { valor: 'ENTREGADA_A_TIEMPO,ENTREGADA_TARDE', etiqueta: 'Entregadas' },
]

export default function EstadoTiempo({ tiempo, className = '' }) {
  if (!tiempo) return null
  const e = ESTILOS[tiempo.estadoTiempo] ?? ESTILOS.A_TIEMPO
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${e.clase} ${className}`}>
      <span aria-hidden="true" className={`h-1.5 w-1.5 shrink-0 rounded-full ${e.punto}`} />
      {tiempo.etiqueta}
    </span>
  )
}

const ddmm = (iso) => iso ? new Date(`${String(iso).slice(0, 10)}T12:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: '2-digit' }) : ''

/** "Reprogramada (antes 23/09)" cuando la fecha de compromiso vigente ya no es la primera que se prometió. */
export function Reprogramada({ tiempo, className = '' }) {
  if (!tiempo?.fechaCompromisoOriginal || !tiempo?.fechaCompromiso || tiempo.fechaCompromisoOriginal === tiempo.fechaCompromiso) return null
  return <span className={`text-[11px] text-gray-600 ${className}`}>Reprogramada (antes {ddmm(tiempo.fechaCompromisoOriginal)})</span>
}

/** Filtro por estado de tiempo + orden por retraso, para las listas de solicitudes. */
export function FiltroTiempo({ tiempo, onTiempo, ordenRetraso, onOrden }) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <label className="flex items-center gap-2 text-sm text-gray-600">
        <span>Tiempo</span>
        <select value={tiempo} onChange={e => onTiempo(e.target.value)}
          className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
          <option value="">Todos</option>
          {OPCIONES_TIEMPO.map(o => <option key={o.valor} value={o.valor}>{o.etiqueta}</option>)}
        </select>
      </label>
      <label className="flex items-center gap-2 text-sm text-gray-600">
        <input type="checkbox" checked={ordenRetraso} onChange={e => onOrden(e.target.checked)}
          className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
        Más atrasadas primero
      </label>
    </div>
  )
}

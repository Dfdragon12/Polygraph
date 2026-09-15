import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import solicitudService from '../../services/solicitudService'
import ServiceStatusBadge from '../../components/ServiceStatusBadge'

const formatearCOP = (valor) =>
  new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor ?? 0)

const BADGE_SUBPROCESO = {
  PENDIENTE:  { color: 'bg-amber-100 text-amber-700',  dot: 'bg-amber-400',  texto: 'Pendiente' },
  ASIGNADO:   { color: 'bg-blue-100 text-blue-700',    dot: 'bg-blue-400',   texto: 'Programado' },
  COMPLETADO: { color: 'bg-green-100 text-green-700',  dot: 'bg-green-400',  texto: 'Completado' },
  CANCELADO:  { color: 'bg-red-100 text-red-600',      dot: 'bg-red-400',    texto: 'Cancelado' },
}

function formatFechaHoraCorta(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}

export default function SolicitudDetalle() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [detalle, setDetalle] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    solicitudService.detalle(id)
      .then(r => setDetalle(r.data))
      .catch(() => setError('No se pudo cargar el detalle de la solicitud.'))
      .finally(() => setCargando(false))
  }, [id])

  if (cargando) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
    </div>
  )

  if (error || !detalle) return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <p className="text-red-500 text-sm">{error || 'No encontrado.'}</p>
      <button onClick={() => navigate('/cliente/solicitudes')} className="text-sm text-primary-600 hover:underline">
        ← Volver a solicitudes
      </button>
    </div>
  )

  return (
    <div className="max-w-3xl mx-auto py-6 space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/cliente/solicitudes')}
          className="text-sm text-gray-500 hover:text-gray-800">
          ← Volver
        </button>
        <h1 className="text-xl font-bold text-gray-800">Solicitud #{detalle.idServicio}</h1>
      </div>

      {/* Estado y fechas */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 flex flex-wrap items-center gap-6">
        <div className="flex items-center gap-3">
          <ServiceStatusBadge status={detalle.estado} />
          <span className="text-sm font-medium text-gray-700">Estado actual</span>
        </div>
        <div>
          <p className="text-xs text-gray-400">Fecha de solicitud</p>
          <p className="text-sm font-medium text-gray-700">
            {detalle.fechaSolicitud ? new Date(detalle.fechaSolicitud).toLocaleDateString('es-CO') : '—'}
          </p>
        </div>
        {detalle.fechaEntregaEstimada && (
          <div className="ml-auto">
            <p className="text-xs text-gray-400">Entrega estimada</p>
            <p className="text-sm font-semibold text-primary-700">{detalle.fechaEntregaEstimada}</p>
          </div>
        )}
      </div>

      {/* Progreso del estudio — resumen de cada subproceso (antecedentes, poligrafía, visita, etc.) */}
      {detalle.subprocesos?.length > 0 && (() => {
        const total = detalle.subprocesos.length
        const completados = detalle.subprocesos.filter(sp => sp.estado === 'COMPLETADO').length
        const pct = total > 0 ? Math.round((completados / total) * 100) : 0
        return (
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Progreso del estudio</h2>
              <span className="text-xs font-medium text-gray-500">{completados}/{total} completados</span>
            </div>
            <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden mb-4">
              <div className="h-full bg-green-500 rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
            </div>
            <div className="space-y-2">
              {detalle.subprocesos.map((sp, i) => {
                const cfg = BADGE_SUBPROCESO[sp.estado] ?? { color: 'bg-gray-100 text-gray-500', dot: 'bg-gray-300', texto: sp.estado }
                const fechaCorta = formatFechaHoraCorta(sp.fechaProgramada)
                return (
                  <div key={i} className="flex items-center justify-between gap-3 rounded-lg border border-gray-100 px-3 py-2.5">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <span className={`w-2 h-2 rounded-full flex-shrink-0 ${cfg.dot}`} />
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-gray-800 truncate">{sp.nombre}</p>
                        {fechaCorta && <p className="text-xs text-gray-400">Programado: {fechaCorta}</p>}
                      </div>
                    </div>
                    <span className={`flex-shrink-0 text-xs font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap ${cfg.color}`}>
                      {cfg.texto}
                    </span>
                  </div>
                )
              })}
            </div>
          </div>
        )
      })()}

      {/* Datos del evaluado */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
        <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-4">Datos del evaluado</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-x-6 gap-y-3 text-sm">
          <div>
            <span className="block text-xs text-gray-400">Cédula</span>
            <span className="font-medium text-gray-800">{detalle.cedulaEvaluado}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Nombre</span>
            <span className="font-medium text-gray-800">{detalle.nombresEvaluado} {detalle.apellidosEvaluado}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Cargo</span>
            <span className="text-gray-800">{detalle.cargo || '—'}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Celular</span>
            <span className="text-gray-800">{detalle.celularEvaluado || '—'}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Email</span>
            <span className="text-gray-800 break-all">{detalle.emailEvaluado || '—'}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Ciudad</span>
            <span className="text-gray-800">{detalle.nombreCiudad || '—'}</span>
          </div>
        </div>
        {detalle.notas && (
          <div className="mt-4 bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm">
            <span className="block text-xs font-semibold text-amber-700 mb-1">Novedades del gestor</span>
            <p className="text-amber-800">{detalle.notas}</p>
          </div>
        )}
      </div>

      {/* Servicio contratado */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
        <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-4">Servicio contratado</h2>
        <div className="flex items-center justify-between py-2.5">
          <div>
            <p className="text-sm font-medium text-gray-800">{detalle.proceso}</p>
            {detalle.clasificacion && (
              <p className="text-xs text-gray-400">{detalle.clasificacion}</p>
            )}
          </div>
          <ServiceStatusBadge status={detalle.estado} />
        </div>

        {detalle.historialRecargoCiudad?.length > 0 && (
          <div className="mt-3 rounded-lg border border-gray-200 px-3 py-2.5 text-sm">
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-gray-700">
              <span>
                📍 Recargo por ciudad ({detalle.nombreCiudad}) — pagado:{' '}
                <span className="font-semibold text-green-700">{formatearCOP(detalle.totalPagadoRecargoCiudad)}</span>
              </span>
              {detalle.totalPendienteRecargoCiudad !== 0 && (
                <span className={detalle.totalPendienteRecargoCiudad < 0 ? 'text-green-700' : 'text-red-700'}>
                  {detalle.totalPendienteRecargoCiudad < 0 ? 'crédito a favor: ' : 'pendiente por cobrar: '}
                  <span className="font-semibold">{formatearCOP(detalle.totalPendienteRecargoCiudad)}</span>
                </span>
              )}
            </div>
            <ul className="mt-2 space-y-1 border-t border-gray-100 pt-2 text-xs text-gray-500">
              {detalle.historialRecargoCiudad.map((m, i) => (
                <li key={i} className="flex items-center justify-between gap-2">
                  <span>
                    {m.ciudad}{m.motivo ? ` — ${m.motivo}` : ''} · {new Date(m.fechaGenerado).toLocaleDateString('es-CO')}
                  </span>
                  <span className={`flex-shrink-0 font-medium ${m.monto < 0 ? 'text-green-600' : 'text-gray-700'}`}>
                    {formatearCOP(m.monto)}
                    <span className={`ml-1.5 rounded-full px-1.5 py-0.5 text-[10px] ${m.estado === 'PAGADO' ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-700'}`}>
                      {m.estado}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* Historial de cambios */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
        <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-4">Historial de cambios</h2>
        {detalle.historial?.length ? (
          <ol className="relative border-l border-gray-200 space-y-5 ml-2 pl-5">
            {detalle.historial.map((h, i) => (
              <li key={i} className="relative">
                <div className="absolute w-3 h-3 bg-primary-400 rounded-full -left-[1.65rem] top-0.5 border-2 border-white" />
                <p className="text-xs text-gray-400">
                  {h.fechaCambio ? new Date(h.fechaCambio).toLocaleString('es-CO') : '—'}
                  {h.usuario && <> · <span className="text-gray-500">{h.usuario}</span></>}
                </p>
                <p className="text-sm text-gray-800 font-medium mt-0.5">
                  {h.estadoAnterior ? `${h.estadoAnterior} → ${h.estadoNuevo}` : h.estadoNuevo}
                </p>
                {h.observacion && (
                  <p className="text-xs text-gray-500 mt-0.5 italic">{h.observacion}</p>
                )}
              </li>
            ))}
          </ol>
        ) : (
          <p className="text-sm text-gray-400">Sin cambios de estado registrados.</p>
        )}
      </div>

      {/* Descargar informe — solo si PUBLICADO */}
      {detalle.estado === 'PUBLICADO' && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
          <button className="w-full py-3 text-sm font-medium text-white bg-green-600 rounded-lg hover:bg-green-700 transition-colors flex items-center justify-center gap-2">
            <span>📄</span>
            Descargar informe
          </button>
        </div>
      )}
    </div>
  )
}

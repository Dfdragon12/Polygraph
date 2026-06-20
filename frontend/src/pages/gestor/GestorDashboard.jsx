import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import api from '../../services/api'

const CONFIG_ESTADO = {
  PENDIENTE:    { label: 'Pendientes',   color: 'bg-amber-50 border-amber-200',   num: 'text-amber-600',  dot: 'bg-amber-400',  accion: 'Requieren atención'  },
  PROGRAMANDO:  { label: 'Programando', color: 'bg-blue-50 border-blue-200',     num: 'text-blue-600',   dot: 'bg-blue-400',   accion: 'En preparación'      },
  EN_EJECUCION: { label: 'En ejecución',color: 'bg-indigo-50 border-indigo-200', num: 'text-indigo-600', dot: 'bg-indigo-400', accion: 'Siendo trabajados'   },
  FINALIZADO:   { label: 'Finalizados', color: 'bg-green-50 border-green-200',   num: 'text-green-600',  dot: 'bg-green-400',  accion: 'Listos para revisar' },
  PUBLICADOS:   { label: 'Publicados',  color: 'bg-cyan-50 border-cyan-200',     num: 'text-cyan-600',   dot: 'bg-cyan-400',   accion: 'Entregados'          },
  CANCELADOS:   { label: 'Cancelados',  color: 'bg-red-50 border-red-200',       num: 'text-red-500',    dot: 'bg-red-400',    accion: 'No activos'          },
}

const BADGE_ESTADO = {
  PENDIENTE:    'bg-amber-100 text-amber-700',
  PROGRAMANDO:  'bg-blue-100 text-blue-700',
  EN_EJECUCION: 'bg-indigo-100 text-indigo-700',
  FINALIZADO:   'bg-green-100 text-green-700',
  PUBLICADO:    'bg-cyan-100 text-cyan-700',
  CANCELADO:    'bg-red-100 text-red-600',
  REPROGRAMADO: 'bg-orange-100 text-orange-700',
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function formatRelativo(iso) {
  if (!iso) return '—'
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 60) return `Hace ${min} min`
  const h = Math.floor(min / 60)
  if (h < 24) return `Hace ${h} h`
  return `Hace ${Math.floor(h / 24)} día(s)`
}

export default function GestorDashboard() {
  const { usuario } = useAuth()
  const navigate    = useNavigate()
  const [datos, setDatos]       = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError]       = useState(null)

  useEffect(() => {
    api.get('/gestor/dashboard')
      .then(r => setDatos(r.data))
      .catch(() => setError('No se pudieron cargar los datos del dashboard.'))
      .finally(() => setCargando(false))
  }, [])

  if (cargando) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-10 w-10 border-4 border-indigo-600 border-t-transparent" />
    </div>
  )

  const kpis = [
    { key: 'pendientes',  valor: datos?.pendientes  ?? 0, ...CONFIG_ESTADO.PENDIENTE   },
    { key: 'programando', valor: datos?.programando ?? 0, ...CONFIG_ESTADO.PROGRAMANDO },
    { key: 'enEjecucion', valor: datos?.enEjecucion ?? 0, ...CONFIG_ESTADO.EN_EJECUCION },
    { key: 'finalizados', valor: datos?.finalizados ?? 0, ...CONFIG_ESTADO.FINALIZADO  },
    { key: 'publicados',  valor: datos?.publicados  ?? 0, ...CONFIG_ESTADO.PUBLICADOS  },
    { key: 'cancelados',  valor: datos?.cancelados  ?? 0, ...CONFIG_ESTADO.CANCELADOS  },
  ]

  return (
    <div className="space-y-6">

      {/* ── Encabezado ── */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Hola, {usuario?.nombre}</h2>
          <p className="text-sm text-gray-500 capitalize mt-0.5">
            {new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
          </p>
        </div>
        <button onClick={() => navigate('/gestor/solicitudes')}
          className="inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          Ver todas las solicitudes
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{error}</div>
      )}

      {/* ── KPIs ── */}
      <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6 gap-4">
        {kpis.map(k => (
          <div key={k.key} className={`bg-white rounded-xl border ${k.color} p-4`}>
            <div className="flex items-center gap-2 mb-2">
              <span className={`w-2 h-2 rounded-full ${k.dot}`} />
              <span className="text-xs font-medium text-gray-500">{k.label}</span>
            </div>
            <p className={`text-3xl font-bold ${k.num}`}>{k.valor}</p>
            <p className="text-xs text-gray-400 mt-1">{k.accion}</p>
          </div>
        ))}
      </div>

      {/* ── Alerta: finalizados esperando concepto ── */}
      {(datos?.finalizados ?? 0) > 0 && (
        <div className="bg-green-50 border border-green-200 rounded-xl px-4 py-3 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="w-2.5 h-2.5 rounded-full bg-green-500 animate-pulse flex-shrink-0" />
            <p className="text-sm font-medium text-green-800">
              {datos.finalizados} estudio{datos.finalizados !== 1 ? 's' : ''} finalizado{datos.finalizados !== 1 ? 's' : ''} — pendiente{datos.finalizados !== 1 ? 's' : ''} de tu concepto final para publicar
            </p>
          </div>
          <button onClick={() => navigate('/gestor/solicitudes?estado=FINALIZADO')}
            className="text-xs font-semibold text-green-700 hover:text-green-900 border border-green-300 px-3 py-1 rounded-lg hover:bg-green-100 transition-colors whitespace-nowrap">
            Revisar
          </button>
        </div>
      )}

      {/* ── Solicitudes recientes ── */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-800">Solicitudes recientes</h3>
          <span className="text-xs text-gray-400">{datos?.total ?? 0} en total</span>
        </div>

        {!datos?.recientes?.length ? (
          <div className="flex items-center justify-center h-28 text-gray-400 text-sm">
            Sin solicitudes registradas aún
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  {['Evaluado', 'Cargo', 'Cliente', 'Servicios', 'Estado', 'Hace', 'Entrega'].map(h => (
                    <th key={h} className="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {datos.recientes.map(s => (
                  <tr key={s.idSolicitud}
                    onClick={() => navigate('/gestor/solicitudes')}
                    className="hover:bg-indigo-50/30 transition-colors cursor-pointer">
                    <td className="px-4 py-3">
                      <p className="text-sm font-medium text-gray-900 whitespace-nowrap">{s.nombresEvaluado} {s.apellidosEvaluado}</p>
                      <p className="text-xs text-gray-400">{s.cedulaEvaluado}</p>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 whitespace-nowrap">{s.cargo ?? '—'}</td>
                    <td className="px-4 py-3 text-sm text-gray-600 whitespace-nowrap max-w-[140px] truncate">{s.nombreCliente}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {s.servicios?.slice(0, 2).map(sv => (
                          <span key={sv} className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded whitespace-nowrap">{sv}</span>
                        ))}
                        {(s.servicios?.length ?? 0) > 2 && (
                          <span className="text-xs text-gray-400">+{s.servicios.length - 2}</span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${BADGE_ESTADO[s.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                        {s.estado}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{formatRelativo(s.fechaSolicitud)}</td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{formatFecha(s.fechaEntregaEstimada)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

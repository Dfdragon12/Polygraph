import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import api from '../../services/api'
import tokenService from '../../services/tokenService'
import asignacionService from '../../services/asignacionService'
import {
  Cell, Tooltip, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts'

const CONFIG_ESTADO = {
  PENDIENTE:    { label: 'Pendientes',   color: '#f59e0b', borde: 'border-amber-200',   num: 'text-amber-600',  dot: 'bg-amber-400',  accion: 'Requieren atención',  estadoQuery: 'PENDIENTE'    },
  PROGRAMANDO:  { label: 'Programando', color: '#3b82f6',  borde: 'border-blue-200',    num: 'text-blue-600',   dot: 'bg-blue-400',   accion: 'En preparación',      estadoQuery: 'PROGRAMANDO'  },
  EN_EJECUCION: { label: 'En ejecución',color: '#6366f1',   borde: 'border-primary-200', num: 'text-primary-600', dot: 'bg-primary-400', accion: 'Siendo trabajados',   estadoQuery: 'EN_EJECUCION' },
  FINALIZADO:   { label: 'Finalizados', color: '#10b981',  borde: 'border-green-200',   num: 'text-green-600',  dot: 'bg-green-400',  accion: 'Listos para revisar', estadoQuery: 'FINALIZADO'   },
  PUBLICADOS:   { label: 'Publicados',  color: '#06b6d4',  borde: 'border-cyan-200',    num: 'text-cyan-600',   dot: 'bg-cyan-400',   accion: 'Entregados',          estadoQuery: 'PUBLICADO'    },
  CANCELADOS:   { label: 'Cancelados',  color: '#ef4444',  borde: 'border-red-200',     num: 'text-red-500',    dot: 'bg-red-400',    accion: 'No activos',          estadoQuery: 'CANCELADO'    },
  REPROGRAMADOS:{ label: 'Reprogramados', color: '#f97316', borde: 'border-orange-200', num: 'text-orange-600', dot: 'bg-orange-400', accion: 'Cambiaron de fecha',  estadoQuery: 'REPROGRAMADO' },
}

const CONFIG_LINK = {
  pendientes: { label: 'Pendientes', color: '#10b981', borde: 'border-emerald-200', num: 'text-emerald-600', dot: 'bg-emerald-400' },
  usados:     { label: 'Usados',     color: '#64748b', borde: 'border-slate-200',   num: 'text-slate-500',   dot: 'bg-slate-400'   },
  expirados:  { label: 'Expirados',  color: '#ef4444', borde: 'border-red-200',     num: 'text-red-500',     dot: 'bg-red-400'     },
  bloqueados: { label: 'Bloqueados', color: '#f97316', borde: 'border-orange-200',  num: 'text-orange-600',  dot: 'bg-orange-400'  },
}

const BADGE_ESTADO = {
  PENDIENTE:    'bg-amber-100 text-amber-700',
  PROGRAMANDO:  'bg-blue-100 text-blue-700',
  EN_EJECUCION: 'bg-primary-100 text-primary-700',
  FINALIZADO:   'bg-green-100 text-green-700',
  PUBLICADO:    'bg-cyan-100 text-cyan-700',
  CANCELADO:    'bg-red-100 text-red-600',
  REPROGRAMADO: 'bg-orange-100 text-orange-700',
}

function iniciales(nombres, apellidos) {
  const a = (nombres ?? '').trim()[0] ?? ''
  const b = (apellidos ?? '').trim()[0] ?? ''
  return (a + b).toUpperCase() || '—'
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function diasHasta(fechaISO) {
  if (!fechaISO) return null
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0)
  const fecha = new Date(fechaISO); fecha.setHours(0, 0, 0, 0)
  return Math.round((fecha - hoy) / 86400000)
}

function EtiquetaEntrega({ fecha }) {
  const dias = diasHasta(fecha)
  if (dias === null) return <span className="text-gray-400">Sin fecha</span>
  if (dias < 0) return <span className="text-red-600 font-semibold">Vencido hace {Math.abs(dias)}d</span>
  if (dias === 0) return <span className="text-amber-600 font-semibold">Entrega hoy</span>
  if (dias === 1) return <span className="text-amber-600 font-medium">Entrega mañana</span>
  return <span className="text-gray-500">Entrega {formatFecha(fecha)}</span>
}

function tiempoRestante(iso) {
  if (!iso) return '—'
  const diff = new Date(iso).getTime() - Date.now()
  if (diff <= 0) return 'Expirado'
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  if (h >= 24) return `${Math.floor(h / 24)}d ${h % 24}h`
  return `${h}h ${m}m`
}

export default function GestorDashboard() {
  const { usuario } = useAuth()
  const navigate    = useNavigate()
  const [datos, setDatos]           = useState(null)
  const [linkStats, setLinkStats]   = useState(null)
  const [pendientesAsignar, setPendientesAsignar] = useState(null)
  const [totalPendientesAsignar, setTotalPendientesAsignar] = useState(0)
  const [cargando, setCargando]     = useState(true)
  const [error, setError]           = useState(null)

  useEffect(() => {
    Promise.all([
      api.get('/gestor/dashboard').then(r => r.data),
      tokenService.estadisticas().catch(() => null),
    ])
      .then(([dashboard, stats]) => { setDatos(dashboard); setLinkStats(stats) })
      .catch(() => setError('No se pudieron cargar los datos del dashboard.'))
      .finally(() => setCargando(false))

    asignacionService.listar({ estado: 'PENDIENTE', size: 8 })
      .then(r => { setPendientesAsignar(r.content ?? []); setTotalPendientesAsignar(r.totalElements ?? 0) })
      .catch(() => setPendientesAsignar([]))
  }, [])

  const kpis = [
    { key: 'pendientes',  valor: datos?.pendientes  ?? 0, ...CONFIG_ESTADO.PENDIENTE   },
    { key: 'programando', valor: datos?.programando ?? 0, ...CONFIG_ESTADO.PROGRAMANDO },
    { key: 'enEjecucion', valor: datos?.enEjecucion ?? 0, ...CONFIG_ESTADO.EN_EJECUCION },
    { key: 'finalizados', valor: datos?.finalizados ?? 0, ...CONFIG_ESTADO.FINALIZADO  },
    { key: 'publicados',  valor: datos?.publicados  ?? 0, ...CONFIG_ESTADO.PUBLICADOS  },
    { key: 'cancelados',  valor: datos?.cancelados  ?? 0, ...CONFIG_ESTADO.CANCELADOS  },
    { key: 'reprogramados', valor: datos?.reprogramados ?? 0, ...CONFIG_ESTADO.REPROGRAMADOS },
  ]

  const kpisLink = [
    { key: 'pendientes',  valor: linkStats?.pendientes ?? 0, ...CONFIG_LINK.pendientes },
    { key: 'usados',      valor: linkStats?.usados     ?? 0, ...CONFIG_LINK.usados     },
    { key: 'expirados',   valor: linkStats?.expirados  ?? 0, ...CONFIG_LINK.expirados  },
    { key: 'bloqueados',  valor: linkStats?.bloqueados ?? 0, ...CONFIG_LINK.bloqueados },
  ]

  const barLinks = useMemo(() => {
    const total = linkStats?.total ?? 0
    return kpisLink.map(k => ({
      name: k.label,
      value: k.valor,
      color: k.color,
      pct: total > 0 ? Math.round((k.valor / total) * 100) : 0,
    }))
  }, [linkStats])

  if (cargando) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-10 w-10 border-4 border-primary-600 border-t-transparent" />
    </div>
  )

  return (
    <div className="space-y-8">

      {/* ── Encabezado ── */}
      <div className="flex items-center justify-between flex-wrap gap-4 bg-white rounded-2xl border border-gray-200 shadow-sm px-6 py-5">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-primary-600 text-white flex items-center justify-center text-lg font-semibold flex-shrink-0">
            {iniciales(usuario?.nombre, usuario?.apellido)}
          </div>
          <div>
            <h2 className="text-2xl font-bold text-gray-900">Hola, {usuario?.nombre}</h2>
            <p className="text-sm text-gray-500 capitalize mt-0.5">
              {new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
            </p>
          </div>
        </div>
        <button onClick={() => navigate('/gestor/solicitudes')}
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2.5 rounded-xl transition-colors shadow-sm">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          Ver todas las solicitudes
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{error}</div>
      )}

      {/* ── KPIs de solicitudes ── */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-gray-700">Solicitudes por estado</h3>
          <span className="text-xs text-gray-400">{datos?.total ?? 0} en total</span>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-7 gap-4">
          {kpis.map(k => (
            <div key={k.key} onClick={() => navigate(`/gestor/solicitudes?estado=${k.estadoQuery}`)}
              className={`bg-white rounded-xl border ${k.borde} shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow`}>
              <div className="flex items-center gap-2 mb-2">
                <span className={`w-2 h-2 rounded-full ${k.dot}`} />
                <span className="text-xs font-medium text-gray-500">{k.label}</span>
              </div>
              <p className={`text-3xl font-bold ${k.num}`}>{k.valor}</p>
              <p className="text-xs text-gray-400 mt-1">{k.accion}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ── Pendientes de hoy: por fecha de entrega + links que requieren atención ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-800">Pendientes por fecha de entrega</h3>
            <button onClick={() => navigate('/gestor/solicitudes')}
              className="text-xs font-medium text-primary-600 hover:text-primary-700">Ver todas →</button>
          </div>
          {!datos?.pendientesPorEntrega?.length ? (
            <div className="flex items-center justify-center h-28 text-gray-400 text-sm">No hay servicios pendientes</div>
          ) : (
            <div className="divide-y divide-gray-50">
              {datos.pendientesPorEntrega.map(s => (
                <div key={s.idServicio} onClick={() => navigate(`/gestor/solicitudes?id=${s.idServicio}`)}
                  className="px-5 py-3 flex items-center justify-between gap-3 hover:bg-gray-50 cursor-pointer transition-colors">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{s.nombresEvaluado} {s.apellidosEvaluado}</p>
                    <p className="text-xs text-gray-400 truncate">{s.proceso} · {s.nombreCliente}</p>
                  </div>
                  <div className="text-right flex-shrink-0">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${BADGE_ESTADO[s.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                      {s.estado}
                    </span>
                    <p className="text-xs mt-1"><EtiquetaEntrega fecha={s.fechaEntregaEstimada} /></p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-800">Links que requieren tu atención</h3>
          </div>
          {!datos?.linksPorAtender?.length ? (
            <div className="flex items-center justify-center h-28 text-gray-400 text-sm">Todo al día</div>
          ) : (
            <div className="divide-y divide-gray-50">
              {datos.linksPorAtender.map(l => (
                <div key={l.id} onClick={() => navigate(`/gestor/solicitudes?id=${l.idServicio}`)}
                  className="px-5 py-3 flex items-center justify-between gap-3 hover:bg-gray-50 cursor-pointer transition-colors">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{l.nombresEvaluado} {l.apellidosEvaluado}</p>
                    <p className="text-xs text-gray-400 truncate">{l.proceso}</p>
                  </div>
                  <div className="text-right flex-shrink-0">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${
                      l.estado === 'BLOQUEADO' ? 'bg-orange-100 text-orange-700' : 'bg-emerald-100 text-emerald-700'
                    }`}>
                      {l.estado === 'BLOQUEADO' ? 'Bloqueado' : 'Pendiente'}
                    </span>
                    {l.estado !== 'BLOQUEADO' && (
                      <p className="text-xs text-gray-400 mt-1">Expira en {tiempoRestante(l.fechaExpiracion)}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
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

      {/* ── Gráficas: donut de solicitudes + barras de links ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Subprocesos pendientes por asignar */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-800">Subprocesos pendientes por asignar</h3>
            <span className="text-xs text-gray-400">{totalPendientesAsignar} en total</span>
          </div>
          {pendientesAsignar === null ? (
            <div className="flex items-center justify-center h-60"><div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" /></div>
          ) : pendientesAsignar.length === 0 ? (
            <div className="flex items-center justify-center h-60 text-gray-400 text-sm">Todo está asignado</div>
          ) : (
            <div className="divide-y divide-gray-50 max-h-[260px] overflow-y-auto">
              {pendientesAsignar.map(a => (
                <div key={a.id} onClick={() => navigate(`/gestor/solicitudes?id=${a.idServicio}`)}
                  className="px-5 py-3 flex items-center justify-between gap-3 hover:bg-gray-50 cursor-pointer transition-colors">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{a.subproceso}</p>
                    <p className="text-xs text-gray-400 truncate">{a.nombresEvaluado} {a.apellidosEvaluado} · {a.proceso}</p>
                  </div>
                  <div className="text-right flex-shrink-0">
                    <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 whitespace-nowrap">Pendiente</span>
                    {a.duracionMinutos != null && <p className="text-xs text-gray-400 mt-1">{a.duracionMinutos} min</p>}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Barras: links de validación */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <div className="flex items-center justify-between mb-1">
            <div>
              <h3 className="text-sm font-semibold text-gray-800">Links de validación de evaluados</h3>
              <p className="text-xs text-gray-400 mt-0.5">Se gestionan desde el detalle de cada solicitud</p>
            </div>
            <span className="text-xs text-gray-400">{linkStats?.total ?? 0} total</span>
          </div>
          {(linkStats?.total ?? 0) === 0 ? (
            <div className="flex items-center justify-center h-60 text-gray-400 text-sm">Sin links generados aún</div>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={barLinks} layout="vertical" margin={{ left: 4, right: 24, top: 4, bottom: 4 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#f0f0f0" />
                <XAxis type="number" tick={{ fontSize: 10, fill: '#9ca3af' }} axisLine={false} tickLine={false} allowDecimals={false} />
                <YAxis type="category" dataKey="name" width={88} tick={{ fontSize: 10, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                <Tooltip
                  formatter={(val, name, props) => [`${val} links (${props.payload.pct}%)`, 'Cantidad']}
                  contentStyle={{ fontSize: 11, borderRadius: 8, border: '1px solid #e5e7eb' }}
                />
                <Bar dataKey="value" radius={[0, 6, 6, 0]} maxBarSize={28}>
                  {barLinks.map((e, i) => <Cell key={i} fill={e.color} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
          {(linkStats?.bloqueados ?? 0) > 0 && (
            <div className="mt-3 bg-orange-50 border border-orange-200 rounded-xl px-4 py-3 flex items-center gap-3">
              <span className="w-2.5 h-2.5 rounded-full bg-orange-500 flex-shrink-0" />
              <p className="text-xs text-orange-800">
                {linkStats.bloqueados} link{linkStats.bloqueados !== 1 ? 's' : ''} bloqueado{linkStats.bloqueados !== 1 ? 's' : ''} por intentos fallidos — revisa el detalle de la solicitud del evaluado para generar uno nuevo.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

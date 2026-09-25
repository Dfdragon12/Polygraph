import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import asignacionService from '../../services/asignacionService'
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'

// Compartido entre analista, poligrafista y visitador — mismo criterio que MisAsignaciones.jsx
// (misma pantalla, el backend ya acota los datos al usuario autenticado): un solo dashboard real,
// construido con lo que YA existe (conteo() y listar() de /gestor/asignaciones), en vez de repetir
// un dashboard de mentira por rol con tarjetas que no llevaban a ningún lado.
const CONFIG = {
  analista: {
    titulo: 'Mis Asignaciones', ruta: '/analista/asignaciones', icono: '📌',
    subtitulo: 'Subprocesos de validación que el gestor te asignó',
    porHacer: 'Servicios asignados pendientes de validar',
    unidad: 'servicio',
  },
  poligrafista: {
    titulo: 'Mis Servicios', ruta: '/poligrafista/servicios', icono: '🧪',
    subtitulo: 'Poligrafías que el programador te asignó',
    porHacer: 'Poligrafías pendientes por ejecutar',
    unidad: 'poligrafía',
  },
  visitador: {
    titulo: 'Mis Visitas', ruta: '/visitador/visitas', icono: '🏠',
    subtitulo: 'Visitas domiciliarias que el programador te asignó',
    porHacer: 'Visitas pendientes por ejecutar',
    unidad: 'visita',
  },
}

// Mismo semáforo que en Mis Asignaciones/Agenda: azul = en progreso, violeta = terminado pero
// pendiente de que el gestor lo apruebe, verde = ya aprobado y cerrado de verdad, rojo = cancelado.
const CONFIG_ESTADO = {
  ASIGNADO:   { label: 'Por hacer',   color: '#3b82f6', dot: 'bg-blue-400',    borde: 'border-blue-200',    num: 'text-blue-600'    },
  COMPLETADO: { label: 'Por revisar', color: '#8b5cf6', dot: 'bg-violet-400',  borde: 'border-violet-200',  num: 'text-violet-600'  },
  PUBLICADO:  { label: 'Aprobadas',   color: '#10b981', dot: 'bg-emerald-400', borde: 'border-emerald-200', num: 'text-emerald-600' },
  CANCELADO:  { label: 'Canceladas',  color: '#ef4444', dot: 'bg-red-400',     borde: 'border-red-200',     num: 'text-red-500'     },
}

function iniciales(nombre, apellido) {
  const a = (nombre ?? '').trim()[0] ?? ''
  const b = (apellido ?? '').trim()[0] ?? ''
  return (a + b).toUpperCase() || '—'
}

function formatFecha(iso) {
  if (!iso) return 'Sin fecha'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}

function CustomTooltip({ active, payload, total }) {
  if (!active || !payload?.length) return null
  const { name, value } = payload[0]
  const pct = total > 0 ? Math.round((value / total) * 100) : 0
  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-lg px-3 py-2 text-xs">
      <p className="font-semibold text-gray-800">{name}</p>
      <p className="text-gray-500">{value} · {pct}%</p>
    </div>
  )
}

// Diferencia en horas entre dos ISO — se usa para el tiempo promedio de respuesta (asignación →
// completado). Null si falta alguna de las dos fechas (ej. algo completado antes de que existiera
// fechaAsignacion, muy poco probable pero mejor no reventar con NaN).
function horasEntre(desdeIso, hastaIso) {
  if (!desdeIso || !hastaIso) return null
  const horas = (new Date(hastaIso).getTime() - new Date(desdeIso).getTime()) / 3600000
  return horas >= 0 ? horas : null
}

function formatDuracion(horas) {
  if (horas == null) return '—'
  if (horas < 24) return `${Math.round(horas)} h`
  return `${(horas / 24).toFixed(1)} d`
}

function mismoMes(iso, hoy) {
  if (!iso) return false
  const d = new Date(iso)
  return d.getFullYear() === hoy.getFullYear() && d.getMonth() === hoy.getMonth()
}

export default function AsignacionDashboard() {
  const prefijo = useLocation().pathname.split('/')[1]
  const config = CONFIG[prefijo] ?? CONFIG.analista
  const { usuario } = useAuth()
  const navigate = useNavigate()

  const [conteos, setConteos] = useState(null)
  const [proximas, setProximas] = useState(null)
  // Historial de lo ya trabajado (COMPLETADO + PUBLICADO) — sirve para calcular estadísticas
  // reales (tiempo promedio de respuesta, cuántas este mes) en vez de solo mostrar conteos secos.
  const [historial, setHistorial] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    Promise.all([
      asignacionService.conteo(),
      asignacionService.listar({ estado: 'ASIGNADO', size: 5, sort: 'fechaProgramada,asc' }),
      asignacionService.listar({ estado: 'COMPLETADO', size: 100, sort: 'fechaCompletado,desc' }),
      asignacionService.listar({ estado: 'PUBLICADO', size: 100, sort: 'fechaCompletado,desc' }),
    ])
      .then(([c, r, completados, publicados]) => {
        setConteos(c)
        setProximas(r.content ?? [])
        setHistorial([...(completados.content ?? []), ...(publicados.content ?? [])])
      })
      .catch(() => setError('No se pudieron cargar tus datos del dashboard.'))
  }, [])

  const kpis = [
    { key: 'ASIGNADO',   valor: conteos?.asignados,   desc: config.porHacer, ...CONFIG_ESTADO.ASIGNADO },
    { key: 'COMPLETADO', valor: conteos?.completados, desc: 'Ya las diligenciaste, falta que el gestor las apruebe', ...CONFIG_ESTADO.COMPLETADO },
    { key: 'PUBLICADO',  valor: conteos?.publicados,  desc: 'El gestor ya las revisó y aprobó', ...CONFIG_ESTADO.PUBLICADO },
    { key: 'CANCELADO',  valor: conteos?.cancelados,  desc: 'No requieren acción', ...CONFIG_ESTADO.CANCELADO },
  ]

  const donutData = useMemo(() => kpis
    .filter(k => (k.valor ?? 0) > 0)
    .map(k => ({ name: k.label, value: k.valor, color: k.color })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [conteos])

  const estadisticas = useMemo(() => {
    if (!historial) return null
    const horas = historial
      .map(a => horasEntre(a.fechaAsignacion, a.fechaCompletado))
      .filter(h => h != null)
    const promedioHoras = horas.length > 0 ? horas.reduce((a, b) => a + b, 0) / horas.length : null
    const hoy = new Date()
    const esteMes = historial.filter(a => mismoMes(a.fechaCompletado, hoy)).length
    return { promedioHoras, esteMes, totalHistorico: historial.length }
  }, [historial])

  return (
    <div className="space-y-6">
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
        <button onClick={() => navigate(config.ruta)}
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2.5 rounded-xl transition-colors shadow-sm">
          <span className="text-base leading-none">{config.icono}</span>
          Ver {config.titulo.toLowerCase()}
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{error}</div>
      )}

      {/* ── KPIs ── */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-gray-700">{config.subtitulo}</h3>
          {conteos && <span className="text-xs text-gray-400">{conteos.total} en total</span>}
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {kpis.map(k => (
            <div key={k.key} onClick={() => navigate(`${config.ruta}?estado=${k.key}`)}
              className={`bg-white rounded-xl border ${k.borde} shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow`}>
              <div className="flex items-center gap-2 mb-2">
                <span className={`w-2 h-2 rounded-full ${k.dot}`} />
                <span className="text-xs font-medium text-gray-500">{k.label}</span>
              </div>
              <p className={`text-3xl font-bold ${k.num}`}>{conteos ? (k.valor ?? 0) : '—'}</p>
              <p className="text-xs text-gray-400 mt-1">{k.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ── Estadísticas: distribución + rendimiento ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <h3 className="text-sm font-semibold text-gray-800 mb-1">Distribución de tu trabajo</h3>
          <p className="text-xs text-gray-400 mb-2">Todo lo que tienes registrado, por estado</p>
          {conteos === null ? (
            <div className="flex items-center justify-center h-56"><div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" /></div>
          ) : donutData.length === 0 ? (
            <div className="flex items-center justify-center h-56 text-gray-400 text-sm">Todavía no tienes nada asignado</div>
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={donutData} cx="50%" cy="50%"
                  innerRadius={62} outerRadius={92} paddingAngle={2} dataKey="value">
                  {donutData.map((e, i) => <Cell key={i} fill={e.color} />)}
                </Pie>
                <Tooltip content={<CustomTooltip total={conteos?.total ?? 0} />} />
                <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 11 }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5 flex flex-col">
          <h3 className="text-sm font-semibold text-gray-800 mb-1">Tu rendimiento</h3>
          <p className="text-xs text-gray-400 mb-4">Calculado con lo que ya completaste</p>
          {estadisticas === null ? (
            <div className="flex-1 flex items-center justify-center"><div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" /></div>
          ) : estadisticas.totalHistorico === 0 ? (
            <div className="flex-1 flex items-center justify-center text-gray-400 text-sm text-center px-4">
              Todavía no has completado ningún {config.unidad} — cuando completes el primero, aquí verás tus estadísticas.
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 flex-1">
              <div className="bg-gray-50 rounded-xl p-4 flex flex-col justify-center">
                <p className="text-2xl font-bold text-gray-900">{formatDuracion(estadisticas.promedioHoras)}</p>
                <p className="text-xs text-gray-500 mt-1">Tiempo promedio de respuesta</p>
                <p className="text-[11px] text-gray-400 mt-0.5">Desde que te lo asignan hasta que lo completas</p>
              </div>
              <div className="bg-gray-50 rounded-xl p-4 flex flex-col justify-center">
                <p className="text-2xl font-bold text-gray-900">{estadisticas.esteMes}</p>
                <p className="text-xs text-gray-500 mt-1">Completadas este mes</p>
                <p className="text-[11px] text-gray-400 mt-0.5">{new Date().toLocaleDateString('es-CO', { month: 'long', year: 'numeric' })}</p>
              </div>
              <div className="bg-gray-50 rounded-xl p-4 flex flex-col justify-center">
                <p className="text-2xl font-bold text-gray-900">{conteos?.publicados ?? 0}</p>
                <p className="text-xs text-gray-500 mt-1">Aprobadas por el gestor</p>
                <p className="text-[11px] text-gray-400 mt-0.5">De {estadisticas.totalHistorico} completadas en total</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Próximas por hacer ── */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-800">Próximas por hacer</h3>
          <button onClick={() => navigate(`${config.ruta}?estado=ASIGNADO`)}
            className="text-xs font-medium text-primary-600 hover:text-primary-700">Ver todas →</button>
        </div>
        {proximas === null ? (
          <div className="flex items-center justify-center h-28">
            <div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" />
          </div>
        ) : proximas.length === 0 ? (
          <div className="flex items-center justify-center h-28 text-gray-400 text-sm">Todo al día — no tienes pendientes.</div>
        ) : (
          <div className="divide-y divide-gray-50">
            {proximas.map(a => (
              <div key={a.id} onClick={() => navigate(`${config.ruta}?estado=ASIGNADO`)}
                className="px-5 py-3 flex items-center justify-between gap-3 hover:bg-gray-50 cursor-pointer transition-colors">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{a.nombresEvaluado} {a.apellidosEvaluado}</p>
                  <p className="text-xs text-gray-400 truncate">{a.subproceso} · {a.proceso ?? '—'}</p>
                </div>
                <p className="text-xs text-gray-500 flex-shrink-0 whitespace-nowrap">{formatFecha(a.fechaProgramada)}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

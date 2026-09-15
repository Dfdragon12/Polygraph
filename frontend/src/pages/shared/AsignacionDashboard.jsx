import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import asignacionService from '../../services/asignacionService'

// Compartido entre analista, poligrafista y visitador — mismo criterio que MisAsignaciones.jsx
// (misma pantalla, el backend ya acota los datos al usuario autenticado): un solo dashboard real,
// construido con lo que YA existe (conteo() y listar() de /gestor/asignaciones), en vez de repetir
// un dashboard de mentira por rol con tarjetas que no llevaban a ningún lado.
const CONFIG = {
  analista: {
    titulo: 'Mis Asignaciones', ruta: '/analista/asignaciones', icono: '📌',
    subtitulo: 'Subprocesos de validación que el gestor te asignó',
    porHacer: 'Servicios asignados pendientes de validar',
  },
  poligrafista: {
    titulo: 'Mis Servicios', ruta: '/poligrafista/servicios', icono: '🧪',
    subtitulo: 'Poligrafías que el programador te asignó',
    porHacer: 'Poligrafías pendientes por ejecutar',
  },
  visitador: {
    titulo: 'Mis Visitas', ruta: '/visitador/visitas', icono: '🏠',
    subtitulo: 'Visitas domiciliarias que el programador te asignó',
    porHacer: 'Visitas pendientes por ejecutar',
  },
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

export default function AsignacionDashboard() {
  const prefijo = useLocation().pathname.split('/')[1]
  const config = CONFIG[prefijo] ?? CONFIG.analista
  const { usuario } = useAuth()
  const navigate = useNavigate()

  const [conteos, setConteos] = useState(null)
  const [proximas, setProximas] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    Promise.all([
      asignacionService.conteo(),
      asignacionService.listar({ estado: 'ASIGNADO', size: 5 }),
    ])
      .then(([c, r]) => { setConteos(c); setProximas(r.content ?? []) })
      .catch(() => setError('No se pudieron cargar tus datos del dashboard.'))
  }, [])

  const kpis = [
    { key: 'ASIGNADO',   label: 'Por hacer',   valor: conteos?.asignados,   dot: 'bg-blue-400',   borde: 'border-blue-200',   num: 'text-blue-600',   desc: config.porHacer },
    { key: 'COMPLETADO', label: 'Completadas', valor: conteos?.completados, dot: 'bg-green-400',  borde: 'border-green-200',  num: 'text-green-600',  desc: 'Ya marcadas como completadas' },
    { key: 'CANCELADO',  label: 'Canceladas',  valor: conteos?.cancelados,  dot: 'bg-red-400',    borde: 'border-red-200',    num: 'text-red-500',    desc: 'No requieren acción' },
  ]

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
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {kpis.map(k => (
            <div key={k.key} onClick={() => navigate(`${config.ruta}?estado=${k.key}`)}
              className={`bg-white rounded-xl border ${k.borde} shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow`}>
              <div className="flex items-center gap-2 mb-2">
                <span className={`w-2 h-2 rounded-full ${k.dot}`} />
                <span className="text-xs font-medium text-gray-500">{k.label}</span>
              </div>
              <p className={`text-3xl font-bold ${k.num}`}>{conteos ? k.valor : '—'}</p>
              <p className="text-xs text-gray-400 mt-1">{k.desc}</p>
            </div>
          ))}
        </div>
      </section>

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

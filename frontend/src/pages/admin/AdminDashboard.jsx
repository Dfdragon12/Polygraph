import { useState, useEffect, useCallback, useMemo } from 'react'
import { useAuth } from '../../hooks/useAuth'
import { useNavigate } from 'react-router-dom'
import api from '../../services/api'
import { ConfirmModal } from '../../components/ui/Modal'
import {
  PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
} from 'recharts'

/* ════════ CONSTANTES ════════ */

const ETIQUETA_ROL = {
  ADMIN_POLYGRAPH:  'Administrador',
  GESTOR:           'Gestor',
  ANALISTA_INTERNO: 'Analista Interno',
  PROGRAMADOR:      'Programador',
  POLIGRAFISTA:     'Poligrafista',
  VISITADOR:        'Visitador',
  ADMIN_CLIENTE:    'Admin Cliente',
  ANALISTA_CLIENTE: 'Analista Cliente',
}

const TIPO_NOTIF = {
  CATALOGO:        { color: 'bg-primary-100 text-primary-700',   label: 'Catálogo'    },
  USUARIO:         { color: 'bg-emerald-100 text-emerald-700', label: 'Usuario'     },
  CLIENTE:         { color: 'bg-amber-100 text-amber-700',     label: 'Cliente'     },
  SISTEMA:         { color: 'bg-slate-100 text-slate-600',     label: 'Sistema'     },
  SESION:          { color: 'bg-sky-100 text-sky-700',         label: 'Sesión'      },
  ENLACES:         { color: 'bg-teal-100 text-teal-700',       label: 'Referencias' },
  NUEVA_SOLICITUD:    { color: 'bg-orange-100 text-orange-700', label: 'Solicitud'  },
  SOLICITUD_CREADA:   { color: 'bg-blue-100 text-blue-700',     label: 'Solicitud'  },
  SOLICITUD_REVERSION:{ color: 'bg-purple-100 text-purple-700', label: 'Reversión'  },
}

const CONFIG_ESTADO = {
  PENDIENTE:    { label: 'Pendiente',    color: '#f59e0b', bg: 'bg-amber-50',   border: 'border-amber-200',   num: 'text-amber-600',   dot: 'bg-amber-400'   },
  PROGRAMANDO:  { label: 'Programando',  color: '#3b82f6', bg: 'bg-blue-50',    border: 'border-blue-200',    num: 'text-blue-600',    dot: 'bg-blue-400'    },
  EN_EJECUCION: { label: 'En Ejecución', color: '#6366f1', bg: 'bg-primary-50',  border: 'border-primary-200',  num: 'text-primary-600',  dot: 'bg-primary-400'  },
  FINALIZADO:   { label: 'Finalizado',   color: '#10b981', bg: 'bg-green-50',   border: 'border-green-200',   num: 'text-green-600',   dot: 'bg-green-400'   },
  PUBLICADO:    { label: 'Publicado',    color: '#06b6d4', bg: 'bg-cyan-50',    border: 'border-cyan-200',    num: 'text-cyan-600',    dot: 'bg-cyan-400'    },
  CANCELADO:    { label: 'Cancelado',    color: '#ef4444', bg: 'bg-red-50',     border: 'border-red-200',     num: 'text-red-600',     dot: 'bg-red-400'     },
  REPROGRAMADO: { label: 'Reprogramado', color: '#f97316', bg: 'bg-orange-50',  border: 'border-orange-200',  num: 'text-orange-600',  dot: 'bg-orange-400'  },
}

/* ════════ HELPERS ════════ */

function iniciales(nombre, apellido) {
  const a = nombre?.trim()?.[0] ?? ''
  const b = apellido?.trim()?.[0] ?? ''
  return (a + b).toUpperCase() || '?'
}

function formatRelativo(fechaIso) {
  if (!fechaIso) return '—'
  const diff = Date.now() - new Date(fechaIso).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1)  return 'Ahora'
  if (min < 60) return `Hace ${min} min`
  const h = Math.floor(min / 60)
  if (h < 24)   return `Hace ${h} h`
  const d = Math.floor(h / 24)
  if (d < 7)    return `Hace ${d} día${d > 1 ? 's' : ''}`
  return new Date(fechaIso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short' })
}

/* ════════ KPI CARD ════════ */
function KpiCard({ titulo, valor, sub, color, icono }) {
  const styles = {
    primary:  { wrap: 'border-primary-100',  icon: 'bg-primary-100 text-primary-600',  val: 'text-primary-700' },
    emerald: { wrap: 'border-emerald-100', icon: 'bg-emerald-100 text-emerald-600',val: 'text-emerald-700'},
    amber:   { wrap: 'border-amber-100',   icon: 'bg-amber-100 text-amber-600',    val: 'text-amber-700'  },
  }
  const s = styles[color] ?? styles.primary
  return (
    <div className={`bg-white rounded-xl border ${s.wrap} shadow-sm p-5 flex items-center gap-4`}>
      <div className={`w-12 h-12 rounded-xl flex items-center justify-center text-2xl flex-shrink-0 ${s.icon}`}>
        {icono}
      </div>
      <div>
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">{titulo}</p>
        <p className={`text-3xl font-bold mt-0.5 ${s.val}`}>{valor}</p>
        <p className="text-xs text-gray-400 mt-0.5">{sub}</p>
      </div>
    </div>
  )
}

/* ════════ TOOLTIP DONUT ════════ */
function DonutTooltip({ active, payload, total }) {
  if (!active || !payload?.length) return null
  const { name, value } = payload[0]
  const pct = total > 0 ? Math.round((value / total) * 100) : 0
  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-lg px-3 py-2">
      <p className="text-xs font-semibold text-gray-800">{name}</p>
      <p className="text-xs text-gray-500">{value} · {pct}%</p>
    </div>
  )
}

/* ════════ LABEL CENTRO DONUT ════════ */
function LabelCentro({ cx, cy, total, subtitulo }) {
  return (
    <>
      <text x={cx} y={cy - 6} textAnchor="middle" dominantBaseline="middle"
        style={{ fontSize: 24, fontWeight: 700, fill: '#1e293b' }}>
        {total}
      </text>
      <text x={cx} y={cy + 14} textAnchor="middle" dominantBaseline="middle"
        style={{ fontSize: 10, fill: '#94a3b8' }}>
        {subtitulo}
      </text>
    </>
  )
}

/* ════════ MODAL CONFIRMACIÓN ════════ */
function ModalConfirmar({ mensaje, onConfirmar, onCancelar }) {
  return (
    <ConfirmModal titulo="Eliminar notificación" danger confirmLabel="Eliminar"
      onConfirmar={onConfirmar} onCancelar={onCancelar}>
      <p className="text-sm text-gray-500 mt-1">{mensaje}</p>
    </ConfirmModal>
  )
}

/* ════════ PANEL NOTIFICACIONES ════════ */
function PanelNotificaciones({ notifs, cargando, onMarcarLeida, onEliminar }) {
  if (cargando) return (
    <div className="flex justify-center py-10">
      <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" />
    </div>
  )
  if (!notifs?.length) return (
    <div className="flex flex-col items-center justify-center h-28 gap-2 text-gray-400">
      <svg className="h-8 w-8 opacity-40" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
      </svg>
      <span className="text-sm">Sin notificaciones</span>
    </div>
  )
  return (
    <div className="divide-y divide-gray-100 max-h-72 overflow-y-auto scrollbar-thin">
      {notifs.map(n => {
        const cfg = TIPO_NOTIF[n.tipo] ?? TIPO_NOTIF.SISTEMA
        return (
          <div key={n.id} className={`group flex gap-3 px-1 py-2.5 transition-colors ${n.leida ? '' : 'bg-primary-50/60'}`}>
            <div className="flex flex-col items-center gap-1.5 pt-0.5 flex-shrink-0">
              <span className={`text-xs px-1.5 py-0.5 rounded font-medium whitespace-nowrap ${cfg.color}`}>
                {cfg.label}
              </span>
              {!n.leida && <span className="w-1.5 h-1.5 bg-primary-500 rounded-full" />}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-gray-900 leading-snug">{n.titulo}</p>
              <p className="text-xs text-gray-600 mt-0.5 leading-snug line-clamp-2">{n.mensaje}</p>
              <div className="flex items-center gap-2 mt-1 flex-wrap">
                <span className="text-xs text-gray-400">{formatRelativo(n.fechaCreacion)}</span>
                {n.realizadoPorNombre && (
                  <span className="text-xs text-gray-400">
                    · <span className="font-medium text-gray-600">{n.realizadoPorNombre}</span>
                  </span>
                )}
              </div>
            </div>
            <div className="flex gap-0.5 flex-shrink-0 pt-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
              {!n.leida && (
                <button onClick={() => onMarcarLeida(n.id)} title="Marcar leída"
                  className="p-1 text-gray-400 hover:text-primary-600 rounded transition-colors">
                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                </button>
              )}
              <button onClick={() => onEliminar(n.id)} title="Eliminar"
                className="p-1 text-gray-400 hover:text-red-500 rounded transition-colors">
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        )
      })}
    </div>
  )
}

/* ════════ ÚLTIMOS ACCESOS ════════ */
function UltimosAccesos({ datos, ordenAccesos, setOrdenAccesos }) {
  const ordenado = useMemo(() => {
    if (!datos?.length) return []
    const copia = [...datos]
    if (ordenAccesos === 'nombre') return copia.sort((a, b) => `${a.nombre}`.localeCompare(`${b.nombre}`))
    if (ordenAccesos === 'rol')    return copia.sort((a, b) => (a.rol ?? '').localeCompare(b.rol ?? ''))
    return copia.sort((a, b) => new Date(b.ultimoAcceso) - new Date(a.ultimoAcceso))
  }, [datos, ordenAccesos])

  const OPCIONES = [
    { key: 'reciente', label: 'Más reciente' },
    { key: 'nombre',   label: 'Nombre A-Z'  },
    { key: 'rol',      label: 'Por rol'     },
  ]

  return (
    <>
      <div className="flex items-center gap-1 flex-wrap mb-3">
        {OPCIONES.map(o => (
          <button key={o.key} onClick={() => setOrdenAccesos(o.key)}
            className={`text-xs px-2.5 py-1 rounded-full transition-colors ${
              ordenAccesos === o.key ? 'bg-primary-100 text-primary-700 font-medium' : 'text-gray-500 hover:bg-gray-100'
            }`}>
            {o.label}
          </button>
        ))}
      </div>
      {!ordenado.length ? (
        <div className="flex items-center justify-center h-28 text-gray-400 text-sm">Sin registros de acceso.</div>
      ) : (
        <div className="divide-y divide-gray-100 max-h-72 overflow-y-auto scrollbar-thin">
          {ordenado.map(u => (
            <div key={u.idUsuario} className="flex items-center gap-3 py-2.5">
              <div className="w-8 h-8 rounded-full bg-primary-100 text-primary-600 flex items-center justify-center text-xs font-bold flex-shrink-0">
                {(u.nombre?.[0] ?? '?').toUpperCase()}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">{u.nombre} {u.apellido ?? ''}</p>
                <p className="text-xs text-gray-400 truncate">{u.email}</p>
              </div>
              <div className="text-right flex-shrink-0">
                <span className="text-xs text-gray-500 block">{formatRelativo(u.ultimoAcceso)}</span>
                <span className="text-xs text-gray-400">{ETIQUETA_ROL[u.rol] ?? u.rol}</span>
              </div>
              <span className={`w-2 h-2 rounded-full flex-shrink-0 ${u.activo ? 'bg-green-400' : 'bg-red-400'}`} />
            </div>
          ))}
        </div>
      )}
    </>
  )
}

/* ════════ SEMÁFORO ════════ */
function Semaforo({ estados, total, ordenEstados, setOrdenEstados }) {
  const ordenado = useMemo(() => {
    if (!estados?.length) return []
    const copia = [...estados]
    if (ordenEstados === 'mayor') return copia.sort((a, b) => b.total - a.total)
    if (ordenEstados === 'menor') return copia.sort((a, b) => a.total - b.total)
    return copia
  }, [estados, ordenEstados])

  if (!ordenado.length) return (
    <div className="flex items-center justify-center h-20 text-gray-400 text-sm">Sin servicios registrados.</div>
  )
  return (
    <>
      <div className="flex items-center gap-1 mb-3">
        {[
          { key: 'orden',  label: 'Orden por defecto' },
          { key: 'mayor',  label: 'Mayor a menor'     },
          { key: 'menor',  label: 'Menor a mayor'     },
        ].map(o => (
          <button key={o.key} onClick={() => setOrdenEstados(o.key)}
            className={`text-xs px-2.5 py-1 rounded-full transition-colors ${
              ordenEstados === o.key ? 'bg-primary-100 text-primary-700 font-medium' : 'text-gray-500 hover:bg-gray-100'
            }`}>
            {o.label}
          </button>
        ))}
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7 gap-3">
        {ordenado.map(e => {
          const cfg = CONFIG_ESTADO[e.estado] ?? { label: e.estado, bg: 'bg-gray-50', border: 'border-gray-200', num: 'text-gray-600', dot: 'bg-gray-400' }
          const pct = total > 0 ? Math.round((e.total / total) * 100) : 0
          return (
            <div key={e.estado} className={`rounded-xl border ${cfg.border} ${cfg.bg} p-4 flex flex-col items-center text-center`}>
              <span className={`w-2.5 h-2.5 rounded-full ${cfg.dot} mb-2`} />
              <span className={`text-2xl font-bold ${cfg.num}`}>{e.total}</span>
              <span className="text-xs text-gray-500 mt-0.5 leading-tight">{cfg.label}</span>
              {total > 0 && <span className="text-xs text-gray-400 mt-1">{pct}%</span>}
            </div>
          )
        })}
      </div>
    </>
  )
}

/* ════════ PÁGINA PRINCIPAL ════════ */
export default function AdminDashboard() {
  const { usuario } = useAuth()
  const [datos,             setDatos]             = useState(null)
  const [cargando,          setCargando]          = useState(true)
  const [error,             setError]             = useState(null)
  const [notifs,            setNotifs]            = useState([])
  const [cargandoNotifs,    setCargandoNotifs]    = useState(true)
  const [filtroNotif,       setFiltroNotif]       = useState('todas')
  const [ordenAccesos,      setOrdenAccesos]      = useState('reciente')
  const [confirmarId,       setConfirmarId]       = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    api.get('/admin/dashboard')
      .then(r => setDatos(r.data))
      .catch(() => setError('No se pudieron cargar los datos del dashboard.'))
      .finally(() => setCargando(false))
  }, [])

  useEffect(() => {
    api.get('/notificaciones')
      .then(r => setNotifs(r.data))
      .finally(() => setCargandoNotifs(false))
  }, [])

  const marcarTodas   = async () => { try { await api.patch('/notificaciones/leer-todas'); setNotifs(p => p.map(n => ({ ...n, leida: true }))) } catch {} }
  const marcarLeida   = async (id) => { try { await api.patch(`/notificaciones/${id}/leer`); setNotifs(p => p.map(n => n.id === id ? { ...n, leida: true } : n)) } catch {} }
  const pedirEliminar = (id) => setConfirmarId(id)
  const confirmarEliminar = async () => {
    if (!confirmarId) return
    try { await api.delete(`/notificaciones/${confirmarId}`); setNotifs(p => p.filter(n => n.id !== confirmarId)) } catch {}
    setConfirmarId(null)
  }

  const dataUsuariosDonut = useMemo(() => {
    if (!datos) return []
    return [
      { name: 'Equipo interno', value: datos.internosActivos, color: '#4f46e5' },
      { name: 'Clientes',       value: datos.clientesActivos, color: '#059669' },
    ].filter(d => d.value > 0)
  }, [datos])

  const totalUsuariosDonut = dataUsuariosDonut.reduce((s, e) => s + e.value, 0)
  const noLeidas = notifs.filter(n => !n.leida).length
  const notifsFiltradas = filtroNotif === 'sin_leer' ? notifs.filter(n => !n.leida) : notifs

  if (cargando) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-10 w-10 border-4 border-primary-600 border-t-transparent" />
    </div>
  )

  return (
    <div className="space-y-6">

      {/* ── Encabezado ── */}
      <div className="flex items-center gap-4 bg-white rounded-2xl border border-gray-200 shadow-sm px-6 py-5">
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

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl flex items-center justify-between">
          {error}
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      {/* ── KPIs ── */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiCard titulo="Usuarios activos"      valor={datos?.totalActivos ?? 0}    sub="En toda la plataforma"  color="primary"  icono="👤" />
        <KpiCard titulo="Equipo interno activo" valor={datos?.internosActivos ?? 0} sub="Polygraph Service"       color="emerald" icono="🛡️" />
        <KpiCard titulo="Clientes activos"      valor={datos?.clientesActivos ?? 0} sub="Empresas y personas"    color="amber"   icono="🏢" />
      </div>

      {/* ── Gráficas de rueda (donut) ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Acceso rápido al semáforo */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5 flex flex-col">
          <div className="flex items-center gap-2.5 mb-4">
            <span className="w-9 h-9 bg-gray-100 rounded-lg flex items-center justify-center text-lg flex-shrink-0">🚦</span>
            <div>
              <h3 className="text-sm font-semibold text-gray-800">Semáforo de servicios</h3>
              <p className="text-xs text-gray-400">Resumen del estado de los servicios</p>
            </div>
          </div>

          <div className="flex flex-wrap gap-2 flex-1 mb-5">
            {Object.entries(CONFIG_ESTADO).map(([key, cfg]) => (
              <span
                key={key}
                className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full border ${cfg.border} ${cfg.bg} ${cfg.num}`}
              >
                <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${cfg.dot}`} />
                {cfg.label}
              </span>
            ))}
          </div>

          <button
            onClick={() => navigate('/admin/semaforo')}
            className="w-full flex items-center justify-center gap-2 bg-gray-900 hover:bg-gray-700 text-white text-sm font-medium px-4 py-2.5 rounded-xl transition-colors"
          >
            Ver semáforo completo
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
            </svg>
          </button>
        </div>

        {/* Donut: Distribución usuarios */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <div className="flex items-center justify-between mb-1">
            <h3 className="text-sm font-semibold text-gray-800">Distribución de usuarios activos</h3>
            <span className="text-xs text-gray-400">{totalUsuariosDonut} total</span>
          </div>
          {totalUsuariosDonut === 0 ? (
            <div className="flex items-center justify-center h-52 text-gray-400 text-sm">Sin usuarios registrados</div>
          ) : (
            <ResponsiveContainer width="100%" height={230}>
              <PieChart>
                <Pie
                  data={dataUsuariosDonut}
                  cx="50%" cy="50%"
                  innerRadius={65} outerRadius={95}
                  paddingAngle={3}
                  dataKey="value"
                >
                  {dataUsuariosDonut.map((e, i) => (
                    <Cell key={i} fill={e.color} />
                  ))}
                  <LabelCentro cx={0} cy={0} total={totalUsuariosDonut} subtitulo="activos" />
                </Pie>
                <Tooltip content={<DonutTooltip total={totalUsuariosDonut} />} />
                <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 11 }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* ── Últimos accesos + Notificaciones ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-gray-800">Últimos accesos</h3>
            <span className="text-xs text-gray-400">{datos?.ultimosLogins?.length ?? 0} registros</span>
          </div>
          <UltimosAccesos
            datos={datos?.ultimosLogins}
            ordenAccesos={ordenAccesos}
            setOrdenAccesos={setOrdenAccesos}
          />
        </div>

        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          {/* Cabecera */}
          <div className="flex items-start justify-between mb-3 gap-2">
            <div>
              <h3 className="text-sm font-semibold text-gray-800 flex items-center gap-2">
                Notificaciones recientes
                {noLeidas > 0 && (
                  <span className="text-xs bg-primary-100 text-primary-700 px-1.5 py-0.5 rounded-full font-medium">
                    {noLeidas} sin leer
                  </span>
                )}
              </h3>
              <p className="text-xs text-gray-400 mt-0.5">{notifs.length} en total</p>
            </div>
            {noLeidas > 0 && (
              <button onClick={marcarTodas}
                className="text-xs text-primary-600 hover:text-primary-800 transition-colors flex-shrink-0">
                Marcar leídas
              </button>
            )}
          </div>

          {/* Filtros */}
          <div className="flex items-center gap-1 mb-3">
            {[
              { key: 'todas',    label: 'Todas'    },
              { key: 'sin_leer', label: 'Sin leer' },
            ].map(f => (
              <button key={f.key} onClick={() => setFiltroNotif(f.key)}
                className={`text-xs px-2.5 py-1 rounded-full transition-colors ${
                  filtroNotif === f.key
                    ? 'bg-primary-100 text-primary-700 font-medium'
                    : 'text-gray-500 hover:bg-gray-100'
                }`}>
                {f.label}
                {f.key === 'sin_leer' && noLeidas > 0 && (
                  <span className="ml-1 bg-primary-500 text-white text-xs w-4 h-4 rounded-full inline-flex items-center justify-center leading-none">
                    {noLeidas}
                  </span>
                )}
              </button>
            ))}
          </div>

          <PanelNotificaciones
            notifs={notifsFiltradas}
            cargando={cargandoNotifs}
            onMarcarLeida={marcarLeida}
            onEliminar={pedirEliminar}
          />
        </div>
      </div>

      {confirmarId !== null && (
        <ModalConfirmar
          mensaje="¿Estás seguro de que deseas eliminar esta notificación? Esta acción no se puede deshacer."
          onConfirmar={confirmarEliminar}
          onCancelar={() => setConfirmarId(null)}
        />
      )}
    </div>
  )
}

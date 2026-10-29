// Centro de control del administrador: la operación (solicitudes y trabajo del equipo), lo que
// pide su atención, el riesgo, el dinero y la gente conectada, TODO en vivo. No hay que refrescar:
// el servidor avisa cuando cambia algo (servicios, documentos de clientes, pagos, reversiones,
// usuarios...) y la pantalla se vuelve a calcular sola; además se refresca cada minuto como respaldo.
import { parseFecha } from '../../utils/fechas'
import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { useAuth } from '../../hooks/useAuth'
import { useNavigate } from 'react-router-dom'
import api from '../../services/api'
import tiempoService from '../../services/tiempoService'
import { useCambios } from '../../hooks/useCambios'
import { iniciarIntervaloVisible } from '../../hooks/useIntervaloVisible'
import { useConexionVivo } from '../../hooks/useConexionVivo'
import { ConfirmModal } from '../../components/ui/Modal'
import { Numero, Seccion, entero, pesos, pesosDeCentavos } from '../../components/admin/PiezasTablero'
import { PanelDetalle, SelectorPeriodo, PestanaPanorama, PESTANAS_PANORAMA } from '../../components/admin/PanoramaAdmin'
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid,
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
  PENDIENTE:    { label: 'Pendiente',    bg: 'bg-amber-50',   border: 'border-amber-200',   num: 'text-amber-600',   dot: 'bg-amber-400',   barra: 'bg-amber-400'   },
  PROGRAMANDO:  { label: 'Programando',  bg: 'bg-blue-50',    border: 'border-blue-200',    num: 'text-blue-600',    dot: 'bg-blue-400',    barra: 'bg-blue-400'    },
  EN_EJECUCION: { label: 'En ejecución', bg: 'bg-primary-50', border: 'border-primary-200', num: 'text-primary-600', dot: 'bg-primary-400', barra: 'bg-primary-400' },
  FINALIZADO:   { label: 'Finalizado',   bg: 'bg-green-50',   border: 'border-green-200',   num: 'text-green-600',   dot: 'bg-green-400',   barra: 'bg-green-400'   },
  PUBLICADO:    { label: 'Publicado',    bg: 'bg-cyan-50',    border: 'border-cyan-200',    num: 'text-cyan-600',    dot: 'bg-cyan-400',    barra: 'bg-cyan-400'    },
  CANCELADO:    { label: 'Cancelado',    bg: 'bg-red-50',     border: 'border-red-200',     num: 'text-red-600',     dot: 'bg-red-400',     barra: 'bg-red-400'     },
  REPROGRAMADO: { label: 'Reprogramado', bg: 'bg-orange-50',  border: 'border-orange-200',  num: 'text-orange-600',  dot: 'bg-orange-400',  barra: 'bg-orange-400'  },
}

const TONOS = {
  primary: { caja: 'border-primary-100', icono: 'bg-primary-100 text-primary-600', valor: 'text-primary-700' },
  emerald: { caja: 'border-emerald-100', icono: 'bg-emerald-100 text-emerald-600', valor: 'text-emerald-700' },
  amber:   { caja: 'border-amber-100',   icono: 'bg-amber-100 text-amber-600',     valor: 'text-amber-700'   },
  red:     { caja: 'border-red-100',     icono: 'bg-red-100 text-red-600',         valor: 'text-red-700'     },
  sky:     { caja: 'border-sky-100',     icono: 'bg-sky-100 text-sky-600',         valor: 'text-sky-700'     },
  violet:  { caja: 'border-violet-100',  icono: 'bg-violet-100 text-violet-600',   valor: 'text-violet-700'  },
}

const COLOR_RIESGO = { BAJO: '#10b981', MEDIO: '#f59e0b', ALTO: '#ef4444' }
const REFRESCO_RESPALDO_MS = 60000

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
  return parseFecha(fechaIso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short' })
}

function textoActualizado(desde, ahora) {
  if (!desde) return ''
  const s = Math.max(0, Math.round((ahora - desde) / 1000))
  if (s < 10) return 'ahora mismo'
  if (s < 60) return `hace ${s} s`
  return `hace ${Math.round(s / 60)} min`
}

/* ════════ PIEZAS ════════ */

function Kpi({ titulo, valor, sub, tono = 'primary', icono, to, tema, onAbrir }) {
  const navigate = useNavigate()
  const t = TONOS[tono]
  const contenido = (
    <>
      <div className={`w-11 h-11 rounded-xl flex items-center justify-center text-xl flex-shrink-0 ${t.icono}`} aria-hidden="true">{icono}</div>
      <div className="min-w-0 text-left">
        <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">{titulo}</p>
        <p className={`text-3xl font-bold mt-0.5 ${t.valor}`}><Numero valor={valor} /></p>
        <p className="text-xs text-gray-400 mt-0.5 truncate">{sub}</p>
      </div>
    </>
  )
  const base = `bg-white rounded-xl border ${t.caja} shadow-sm p-4 flex items-center gap-3.5 w-full`
  return tema || to
    ? <button type="button" onClick={() => (tema ? onAbrir(tema) : navigate(to))} className={`${base} hover:shadow-md transition-shadow`}>{contenido}</button>
    : <div className={base}>{contenido}</div>
}

function VerMas({ to, children }) {
  const navigate = useNavigate()
  return (
    <button type="button" onClick={() => navigate(to)} className="text-xs font-medium text-primary-600 hover:text-primary-800 whitespace-nowrap">
      {children} →
    </button>
  )
}

/* ════════ ATENCIÓN ════════ */

function Atencion({ a, onAbrir }) {
  if (!a) return null
  const items = [
    { clave: 'rev', tema: 'reversiones', n: a.reversionesPendientes, texto: 'Reversiones por resolver', ayuda: 'Solicitudes de cambio de estado esperando tu decisión', gravedad: 'amber', to: '/admin/reversiones' },
    { clave: 'doc', tema: 'documentos', n: a.documentosPorRevisar, texto: 'Documentos de clientes por revisar', ayuda: 'Cargados y sin validar', gravedad: 'amber', to: '/admin/clientes' },
    { clave: 'nov', tema: 'novedades', n: a.novedadesAbiertas, texto: 'Novedades abiertas', ayuda: a.novedadesViejas > 0 ? `${a.novedadesViejas} llevan más de 48 horas sin respuesta` : 'Reportadas por el equipo, sin respuesta', gravedad: a.novedadesViejas > 0 ? 'red' : 'amber', to: '/admin/semaforo' },
    { clave: 'job', tema: 'trabajos', n: a.trabajosFallidos, texto: 'Trabajos en segundo plano fallidos', ayuda: 'Informes o cargas que no terminaron: revisa y reintenta', gravedad: 'red', to: '/admin/trabajos' },
    { clave: 'fac', tema: 'facturas', n: a.facturasVencidas, texto: 'Facturas pospago vencidas', ayuda: 'Clientes con pago atrasado', gravedad: 'red', to: '/admin/facturacion' },
    { clave: 'pag', tema: 'pagos', n: a.pagosPendientesViejos, texto: 'Pagos pendientes hace más de 1 hora', ayuda: 'Puede que la confirmación del banco no llegó', gravedad: 'amber', to: '/admin/facturacion' },
    { clave: 'ciu', tema: 'ciudades', n: a.ciudadesPorConfirmar, texto: 'Ciudades por confirmar', ayuda: 'Agregadas por candidatos o clientes', gravedad: 'sky', to: '/admin/ciudades' },
    { clave: 'ges', tema: 'sinGestor', n: a.clientesSinGestor, texto: 'Clientes activos sin gestor', ayuda: 'Nadie los está atendiendo', gravedad: 'red', to: '/admin/clientes' },
  ].filter(i => i.n > 0)

  const estilo = {
    red:   { punto: 'bg-red-500',   badge: 'bg-red-100 text-red-700' },
    amber: { punto: 'bg-amber-500', badge: 'bg-amber-100 text-amber-700' },
    sky:   { punto: 'bg-sky-500',   badge: 'bg-sky-100 text-sky-700' },
  }

  return (
    <Seccion titulo="Requiere tu atención"
      subtitulo={items.length ? `${items.length} ${items.length === 1 ? 'tema pendiente' : 'temas pendientes'} ahora mismo` : 'Nada pendiente'}
      derecha={a.trabajosEnCola > 0 ? <span className="text-xs text-gray-400">{a.trabajosEnCola} trabajo{a.trabajosEnCola === 1 ? '' : 's'} en cola</span> : null}>
      {items.length === 0 ? (
        <div className="flex items-center gap-3 rounded-xl bg-emerald-50 border border-emerald-100 px-4 py-4">
          <span className="text-2xl" aria-hidden="true">✅</span>
          <div>
            <p className="text-sm font-semibold text-emerald-800">Todo en orden</p>
            <p className="text-xs text-emerald-600">No hay reversiones, documentos ni trabajos que requieran tu intervención.</p>
          </div>
        </div>
      ) : (
        <ul className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
          {items.map(i => (
            <li key={i.clave}>
              <button type="button" onClick={() => onAbrir(i.tema)}
                className="w-full flex items-center gap-3 text-left rounded-xl border border-gray-100 hover:border-gray-200 hover:bg-gray-50 px-3.5 py-3 transition-colors">
                <span className={`w-2 h-2 rounded-full flex-shrink-0 ${estilo[i.gravedad].punto}`} aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-medium text-gray-800">{i.texto}</span>
                  <span className="block text-xs text-gray-400">{i.ayuda}</span>
                </span>
                <span className={`text-sm font-bold px-2.5 py-1 rounded-lg ${estilo[i.gravedad].badge}`}><Numero valor={i.n} /></span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Seccion>
  )
}

/* ════════ SEMÁFORO ════════ */

function SemaforoServicios({ s, onAbrir }) {
  const total = s.total
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-3">
      {Object.entries(s.porEstado).map(([estado, n]) => {
        const cfg = CONFIG_ESTADO[estado] ?? { label: estado, bg: 'bg-gray-50', border: 'border-gray-200', num: 'text-gray-600', dot: 'bg-gray-400', barra: 'bg-gray-400' }
        const pct = total > 0 ? Math.round((n / total) * 100) : 0
        return (
          <button key={estado} type="button" onClick={() => onAbrir(`estado-${estado}`)}
            className={`text-left rounded-xl border ${cfg.border} ${cfg.bg} p-3.5 hover:shadow-sm transition-shadow`}>
            <div className="flex items-center gap-1.5 mb-1.5">
              <span className={`w-2 h-2 rounded-full ${cfg.dot}`} />
              <span className="text-xs text-gray-600">{cfg.label}</span>
            </div>
            <p className={`text-2xl font-bold ${cfg.num}`}><Numero valor={n} /></p>
            <div className="mt-2 h-1.5 rounded-full bg-white/70 overflow-hidden" aria-hidden="true">
              <div className={`h-full rounded-full ${cfg.barra} transition-all duration-500`} style={{ width: `${pct}%` }} />
            </div>
            <p className="text-[11px] text-gray-400 mt-1">{pct} % del total</p>
          </button>
        )
      })}
    </div>
  )
}

function GraficaDias({ porDia }) {
  const datos = porDia.map(d => ({
    dia: new Date(`${d.fecha}T00:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: 'short' }),
    total: d.total,
  }))
  const suma = porDia.reduce((acc, d) => acc + d.total, 0)
  return (
    <>
      <p className="text-xs text-gray-500 mb-2"><strong className="text-gray-800 text-base">{entero(suma)}</strong> solicitudes en los últimos 14 días</p>
      <div style={{ width: '100%', height: 190 }}>
        <ResponsiveContainer>
          <BarChart data={datos} margin={{ top: 4, right: 4, left: -24, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
            <XAxis dataKey="dia" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} interval={1} />
            <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} />
            <Tooltip cursor={{ fill: '#f8fafc' }} formatter={(v) => [v, 'Solicitudes']}
              contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid #e2e8f0' }} />
            <Bar dataKey="total" fill="#4f46e5" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </>
  )
}

/* ════════ TRABAJO DEL EQUIPO ════════ */

const ETAPAS_TRABAJO = [
  { clave: 'PENDIENTE', label: 'Sin asignar', caja: 'bg-amber-50 border-amber-200', num: 'text-amber-600' },
  { clave: 'EN_EJECUCION', label: 'En curso', caja: 'bg-blue-50 border-blue-200', num: 'text-blue-600' },
  { clave: 'FINALIZADO', label: 'Por aprobar', caja: 'bg-violet-50 border-violet-200', num: 'text-violet-600' },
  { clave: 'PUBLICADO', label: 'Publicados', caja: 'bg-green-50 border-green-200', num: 'text-green-600' },
]

function TrabajoEquipo({ t }) {
  const maxCarga = Math.max(1, ...t.carga.map(c => c.enCurso))
  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
        {ETAPAS_TRABAJO.map(e => (
          <div key={e.clave} className={`rounded-xl border ${e.caja} px-3 py-2.5`}>
            <p className="text-xs text-gray-500">{e.label}</p>
            <p className={`text-xl font-bold ${e.num}`}><Numero valor={t.porEstado[e.clave] ?? 0} /></p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <div>
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Por tipo de trabajo</p>
          {t.porRol.length === 0 ? (
            <p className="text-sm text-gray-400">No hay subprocesos abiertos.</p>
          ) : (
            <table className="table-stack w-full text-sm">
              <thead>
                <tr className="text-left text-[11px] text-gray-400 uppercase">
                  <th className="py-1 font-semibold">Rol</th>
                  <th className="py-1 font-semibold text-right">Sin asignar</th>
                  <th className="py-1 font-semibold text-right">En curso</th>
                  <th className="py-1 font-semibold text-right">Por aprobar</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {t.porRol.map(r => (
                  <tr key={r.rol}>
                    <td className="py-1.5 text-gray-700">{ETIQUETA_ROL[r.rol] ?? r.rol}</td>
                    <td className={`py-1.5 text-right ${r.sinAsignar > 0 ? 'font-semibold text-amber-600' : 'text-gray-400'}`}>{entero(r.sinAsignar)}</td>
                    <td className="py-1.5 text-right text-gray-700">{entero(r.enCurso)}</td>
                    <td className={`py-1.5 text-right ${r.porAprobar > 0 ? 'font-semibold text-violet-600' : 'text-gray-400'}`}>{entero(r.porAprobar)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div>
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Quién tiene más trabajo en curso</p>
          {t.carga.length === 0 ? (
            <p className="text-sm text-gray-400">Nadie tiene trabajo asignado ahora.</p>
          ) : (
            <ul className="space-y-2.5">
              {t.carga.map(c => (
                <li key={c.idUsuario}>
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="text-gray-700 truncate">{c.nombre} <span className="text-gray-400">· {ETIQUETA_ROL[c.rol] ?? c.rol}</span></span>
                    <span className="font-semibold text-gray-800 ml-2"><Numero valor={c.enCurso} /></span>
                  </div>
                  <div className="h-1.5 rounded-full bg-gray-100 overflow-hidden" aria-hidden="true">
                    <div className="h-full rounded-full bg-primary-500 transition-all duration-500" style={{ width: `${(c.enCurso / maxCarga) * 100}%` }} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}

/* ════════ RIESGO ════════ */

function RiesgoDonut({ r }) {
  const datos = [
    { name: 'Bajo', clave: 'BAJO', value: r.bajo },
    { name: 'Medio', clave: 'MEDIO', value: r.medio },
    { name: 'Alto', clave: 'ALTO', value: r.alto },
  ]
  if (r.total === 0) return <div className="flex items-center justify-center h-44 text-gray-400 text-sm text-center">Sin estudios calificados en los últimos 30 días.</div>
  return (
    <div className="flex items-center gap-4">
      <div style={{ width: 150, height: 150 }} className="flex-shrink-0">
        <ResponsiveContainer>
          <PieChart>
            <Pie data={datos.filter(d => d.value > 0)} dataKey="value" innerRadius={46} outerRadius={70} paddingAngle={3} stroke="none">
              {datos.filter(d => d.value > 0).map(d => <Cell key={d.clave} fill={COLOR_RIESGO[d.clave]} />)}
            </Pie>
            <Tooltip formatter={(v, n) => [v, n]} contentStyle={{ fontSize: 12, borderRadius: 8 }} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="flex-1 space-y-2">
        {datos.map(d => (
          <li key={d.clave} className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-2 text-gray-700">
              <span className="w-2.5 h-2.5 rounded-full" style={{ background: COLOR_RIESGO[d.clave] }} />{d.name}
            </span>
            <span className="font-semibold text-gray-800"><Numero valor={d.value} />
              <span className="text-xs text-gray-400 font-normal ml-1">{Math.round((d.value / r.total) * 100)} %</span>
            </span>
          </li>
        ))}
        <li className="pt-2 border-t border-gray-100 text-xs text-gray-400">{entero(r.total)} estudios calificados</li>
      </ul>
    </div>
  )
}

/* ════════ DINERO ════════ */

function Dinero({ f }) {
  const bloques = [
    { t: 'Pagos aprobados hoy', v: pesosDeCentavos(f.montoAprobadoHoyCentavos), s: `${entero(f.pagosAprobadosHoy)} pago${f.pagosAprobadosHoy === 1 ? '' : 's'}`, c: 'text-emerald-700' },
    { t: 'Aprobados este mes', v: pesosDeCentavos(f.montoAprobadoMesCentavos), s: `${entero(f.pagosAprobadosMes)} pago${f.pagosAprobadosMes === 1 ? '' : 's'}`, c: 'text-emerald-700' },
    { t: 'Facturas por cobrar', v: pesos(f.montoFacturasPendientes), s: `${entero(f.facturasPendientes)} factura${f.facturasPendientes === 1 ? '' : 's'} pospago`, c: 'text-amber-700' },
    { t: 'Facturas vencidas', v: pesos(f.montoFacturasVencidas), s: `${entero(f.facturasVencidas)} factura${f.facturasVencidas === 1 ? '' : 's'}`, c: f.facturasVencidas > 0 ? 'text-red-700' : 'text-gray-500' },
  ]
  return (
    <div className="grid grid-cols-2 gap-3">
      {bloques.map(b => (
        <div key={b.t} className="rounded-xl border border-gray-100 bg-gray-50/60 px-3.5 py-3">
          <p className="text-xs text-gray-500">{b.t}</p>
          <p className={`text-lg font-bold mt-0.5 break-words ${b.c}`}>{b.v}</p>
          <p className="text-[11px] text-gray-400">{b.s}</p>
        </div>
      ))}
    </div>
  )
}

/* ════════ NOTIFICACIONES Y ACCESOS (los de siempre) ════════ */

function ModalConfirmar({ mensaje, onConfirmar, onCancelar }) {
  return (
    <ConfirmModal titulo="Eliminar notificación" danger confirmLabel="Eliminar"
      onConfirmar={onConfirmar} onCancelar={onCancelar}>
      <p className="text-sm text-gray-500 mt-1">{mensaje}</p>
    </ConfirmModal>
  )
}

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
            <div className="flex gap-0.5 flex-shrink-0 pt-0.5 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
              {!n.leida && (
                <button onClick={() => onMarcarLeida(n.id)} title="Marcar leída" aria-label="Marcar leída"
                  className="p-1 text-gray-400 hover:text-primary-600 rounded transition-colors">
                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                </button>
              )}
              <button onClick={() => onEliminar(n.id)} title="Eliminar" aria-label="Eliminar notificación"
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

/* ════════ PÁGINA PRINCIPAL ════════ */

export default function AdminDashboard() {
  const { usuario } = useAuth()
  const vivo = useConexionVivo()

  const [datos,          setDatos]          = useState(null)
  const [resumenTiempo,   setResumenTiempo]   = useState(null)
  const [cargando,       setCargando]       = useState(true)
  const [error,          setError]          = useState(null)
  const [actualizando,   setActualizando]   = useState(false)
  const [actualizadoEn,  setActualizadoEn]  = useState(null)
  const [ahora,          setAhora]          = useState(() => Date.now())
  const [notifs,         setNotifs]         = useState([])
  const [cargandoNotifs, setCargandoNotifs] = useState(true)
  const [filtroNotif,    setFiltroNotif]    = useState('todas')
  const [ordenAccesos,   setOrdenAccesos]   = useState('reciente')
  const [confirmarId,    setConfirmarId]    = useState(null)
  const [pestana,        setPestana]        = useState(() => { try { return localStorage.getItem('admin.tablero.pestana') || 'resumen' } catch { return 'resumen' } })
  const [dias,           setDias]           = useState(() => { try { return Number(localStorage.getItem('admin.tablero.dias')) || 30 } catch { return 30 } })
  const [tema,           setTema]           = useState(null)
  const cerrarDetalle = useCallback(() => setTema(null), [])

  const irAPestana = (id) => { setPestana(id); try { localStorage.setItem('admin.tablero.pestana', id) } catch { /* sin almacenamiento */ } }
  const cambiarDias = (d) => { setDias(d); try { localStorage.setItem('admin.tablero.dias', String(d)) } catch { /* sin almacenamiento */ } }

  // Trae todo de una vez. "silencioso" = refresco por un aviso en vivo: no tapa la pantalla con el
  // círculo de carga ni borra lo que ya se ve si falla.
  const cargar = useCallback((silencioso = false) => {
    if (silencioso) setActualizando(true)
    return Promise.allSettled([api.get('/admin/dashboard'), api.get('/notificaciones'), tiempoService.resumen({ top: 1 })])
      .then(([tablero, notificaciones, tiempo]) => {
        if (tiempo.status === 'fulfilled') setResumenTiempo(tiempo.value)
        if (tablero.status === 'fulfilled') {
          setDatos(tablero.value.data)
          setActualizadoEn(Date.now())
          setError(null)
        } else if (!silencioso) {
          setError('No se pudieron cargar los datos del dashboard.')
        } else {
          setError('No se pudo actualizar: se muestran los últimos datos recibidos.')
        }
        if (notificaciones.status === 'fulfilled') setNotifs(notificaciones.value.data)
      })
      .finally(() => { setCargando(false); setCargandoNotifs(false); setActualizando(false) })
  }, [])

  useEffect(() => { cargar(false) }, [cargar])
  // En vivo: cualquier cambio que le incumbe al administrador (servicios o temas) recarga todo.
  // esperaMs agrupa ráfagas (un solo informe puede tocar varias tablas) en una sola recarga.
  useCambios(() => cargar(true), { temas: true, esperaMs: 1200 })
  // Respaldo por si algún cambio no avisa (ej. se vence un plazo solo con el paso del tiempo).
  useEffect(() => iniciarIntervaloVisible(() => cargar(true), REFRESCO_RESPALDO_MS), [cargar])
  // Reloj del "actualizado hace…"
  useEffect(() => { const t = setInterval(() => setAhora(Date.now()), 10000); return () => clearInterval(t) }, [])

  const marcarTodas   = async () => { try { await api.patch('/notificaciones/leer-todas'); setNotifs(p => p.map(n => ({ ...n, leida: true }))) } catch { /* sin conexión */ } }
  const marcarLeida   = async (id) => { try { await api.patch(`/notificaciones/${id}/leer`); setNotifs(p => p.map(n => n.id === id ? { ...n, leida: true } : n)) } catch { /* sin conexión */ } }
  const confirmarEliminar = async () => {
    if (!confirmarId) return
    try { await api.delete(`/notificaciones/${confirmarId}`); setNotifs(p => p.filter(n => n.id !== confirmarId)) } catch { /* sin conexión */ }
    setConfirmarId(null)
  }

  const noLeidas = notifs.filter(n => !n.leida).length
  const notifsFiltradas = filtroNotif === 'sin_leer' ? notifs.filter(n => !n.leida) : notifs

  if (cargando) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-10 w-10 border-4 border-primary-600 border-t-transparent" />
    </div>
  )

  const s = datos?.servicios
  const t = datos?.trabajo
  const e = datos?.equipo
  // Entregas en riesgo: del resumen único de tiempos (el mismo que el Semáforo); mientras llega, las cifras del tablero, que salen de él también.
  const vencidas = resumenTiempo?.porEstadoTiempo?.VENCIDA ?? s?.entregasVencidas ?? 0
  const porVencer = resumenTiempo?.porEstadoTiempo?.POR_VENCER ?? s?.entregasPorVencer ?? 0
  const enRiesgo = vencidas + porVencer
  const conectados = e ? e.conectadosInternos + e.conectadosClientes : 0

  return (
    <div className="space-y-6">

      {/* ── Encabezado ── */}
      <div className="flex flex-wrap items-center gap-4 bg-white rounded-2xl border border-gray-200 shadow-sm px-6 py-5">
        <div className="w-12 h-12 rounded-full bg-primary-600 text-white flex items-center justify-center text-lg font-semibold flex-shrink-0">
          {iniciales(usuario?.nombre, usuario?.apellido)}
        </div>
        <div className="flex-1 min-w-0">
          <h2 className="text-2xl font-bold text-gray-900">Hola, {usuario?.nombre}</h2>
          <p className="text-sm text-gray-500 capitalize mt-0.5">
            {new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full ${vivo ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}
            role="status" aria-live="polite">
            <span className="relative flex h-2 w-2" aria-hidden="true">
              {vivo && <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />}
              <span className={`relative inline-flex rounded-full h-2 w-2 ${vivo ? 'bg-emerald-500' : 'bg-amber-500'}`} />
            </span>
            {vivo ? 'En vivo' : 'Reconectando…'}
          </span>
          <span className="text-xs text-gray-400 hidden sm:inline">Actualizado {textoActualizado(actualizadoEn, ahora)}</span>
          <button type="button" onClick={() => cargar(true)} disabled={actualizando} aria-label="Actualizar ahora"
            className="p-2 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-60 transition-colors">
            <svg className={`h-4 w-4 ${actualizando ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl flex items-center justify-between" role="alert">
          {error}
          <button onClick={() => setError(null)} aria-label="Cerrar aviso" className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      {/* ── Pestañas: el resumen de hoy y el panorama de todo el sistema ── */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="tablist" aria-label="Secciones del tablero" className="flex flex-wrap gap-1 bg-white rounded-xl border border-gray-200 p-1">
          {[{ id: 'resumen', etiqueta: 'Resumen' }, ...PESTANAS_PANORAMA].map(x => (
            <button key={x.id} type="button" role="tab" aria-selected={pestana === x.id} onClick={() => irAPestana(x.id)}
              className={`text-sm px-3.5 py-1.5 rounded-lg transition-colors ${pestana === x.id ? 'bg-primary-600 text-white font-medium' : 'text-gray-600 hover:bg-gray-100'}`}>
              {x.etiqueta}
            </button>
          ))}
        </div>
        {pestana !== 'resumen' && <SelectorPeriodo dias={dias} onCambiar={cambiarDias} />}
      </div>

      {pestana !== 'resumen' && (
        <div role="tabpanel">
          <PestanaPanorama pestana={pestana} dias={dias} refresco={actualizadoEn} onAbrir={setTema} />
        </div>
      )}

      {pestana === 'resumen' && datos && s && t && e && (
        <>
          {/* ── Indicadores ── */}
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
            <Kpi titulo="Solicitudes en curso" valor={s.enCurso} sub={`${entero(s.total)} en total`} tono="primary" icono="📋" to="/admin/semaforo" />
            <Kpi titulo="Nuevas hoy" valor={s.nuevasHoy} sub={`${entero(s.nuevasSemana)} en 7 días · ${entero(s.nuevasMes)} este mes`} tono="sky" icono="🆕" to="/admin/semaforo" />
            <Kpi titulo="Por aprobar" valor={t.porEstado.FINALIZADO ?? 0} sub="Subprocesos terminados, esperando al gestor" tono="violet" icono="✍️" to="/admin/semaforo" />
            <Kpi titulo="Entregas en riesgo" valor={enRiesgo} sub={`${entero(vencidas)} vencidas · ${entero(porVencer)} por vencer`} tono={vencidas > 0 ? 'red' : enRiesgo > 0 ? 'amber' : 'emerald'} icono="⏰" to="/admin/semaforo?estadoTiempo=VENCIDA,POR_VENCER" />
            <Kpi titulo="Conectados ahora" valor={conectados} sub={`${e.conectadosInternos} equipo · ${e.conectadosClientes} clientes · ${e.conectadosCandidatos} candidatos`} tono="emerald" icono="🟢" />
            <Kpi titulo="Clientes activos" valor={datos.clientesActivos} sub={`${entero(datos.internosActivos)} personas en el equipo · ${entero(e.clientesNuevosMes)} clientes nuevos este mes`} tono="amber" icono="🏢" to="/admin/clientes" />
          </div>

          {/* ── Atención ── */}
          <Atencion a={datos.atencion} onAbrir={setTema} />

          {/* ── Solicitudes ── */}
          <div className="grid grid-cols-1 xl:grid-cols-5 gap-6">
            <Seccion titulo="Semáforo de solicitudes" subtitulo="Cuántas hay en cada estado ahora mismo"
              derecha={<VerMas to="/admin/semaforo">Ver semáforo</VerMas>} className="xl:col-span-3">
              <SemaforoServicios s={s} onAbrir={setTema} />
            </Seccion>
            <Seccion titulo="Solicitudes por día" subtitulo="Lo que entró en las últimas dos semanas" className="xl:col-span-2">
              <GraficaDias porDia={s.porDia} />
            </Seccion>
          </div>

          {/* ── Equipo ── */}
          <Seccion titulo="Trabajo del equipo" subtitulo="Subprocesos por etapa y quién los tiene"
            derecha={<VerMas to="/admin/usuarios-internos">Ver equipo</VerMas>}>
            <TrabajoEquipo t={t} />
            <div className="flex flex-wrap gap-2 mt-5 pt-4 border-t border-gray-100">
              {Object.entries(e.internosPorRol).map(([rol, n]) => (
                <span key={rol} className="text-xs bg-gray-100 text-gray-600 px-2.5 py-1 rounded-full">
                  {ETIQUETA_ROL[rol] ?? rol}: <strong className="text-gray-800">{n}</strong>
                </span>
              ))}
            </div>
          </Seccion>

          {/* ── Riesgo y dinero ── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Seccion titulo="Riesgo de los estudios" subtitulo="Nivel que quedó en los últimos 30 días">
              <RiesgoDonut r={datos.riesgo} />
            </Seccion>
            <Seccion titulo="Dinero" subtitulo="Pagos en línea y facturas pospago"
              derecha={<VerMas to="/admin/facturacion">Ver facturación</VerMas>}>
              <Dinero f={datos.finanzas} />
            </Seccion>
          </div>
        </>
      )}

      {/* ── Últimos accesos + Notificaciones ── */}
      {pestana === 'resumen' && (
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-gray-800">Últimos accesos</h3>
            <span className="text-xs text-gray-400">{datos?.ultimosLogins?.length ?? 0} registros</span>
          </div>
          <UltimosAccesos datos={datos?.ultimosLogins} ordenAccesos={ordenAccesos} setOrdenAccesos={setOrdenAccesos} />
        </div>

        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
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
              <button onClick={marcarTodas} className="text-xs text-primary-600 hover:text-primary-800 transition-colors flex-shrink-0">
                Marcar leídas
              </button>
            )}
          </div>

          <div className="flex items-center gap-1 mb-3">
            {[
              { key: 'todas',    label: 'Todas'    },
              { key: 'sin_leer', label: 'Sin leer' },
            ].map(f => (
              <button key={f.key} onClick={() => setFiltroNotif(f.key)}
                className={`text-xs px-2.5 py-1 rounded-full transition-colors ${
                  filtroNotif === f.key ? 'bg-primary-100 text-primary-700 font-medium' : 'text-gray-500 hover:bg-gray-100'
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

          <PanelNotificaciones notifs={notifsFiltradas} cargando={cargandoNotifs}
            onMarcarLeida={marcarLeida} onEliminar={setConfirmarId} />
        </div>
      </div>
      )}

      {tema && <PanelDetalle tema={tema} refresco={actualizadoEn} onCerrar={cerrarDetalle} />}

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

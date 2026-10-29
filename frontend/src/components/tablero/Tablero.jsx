import { useState } from 'react'
import {
  ResponsiveContainer, PieChart, Pie, Cell, Tooltip,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Legend, ReferenceLine,
} from 'recharts'

// Piezas de los tableros internos en gráficas (gestor, programador): tarjetas de "requiere tu
// atención", marco de gráfica, dona con total, barras horizontales, barra lineal de estados y la
// gráfica de agenda (semana pasada + próximos 14 días). Un solo lugar para que todos los tableros
// se vean iguales.

/* ── Paleta ── */
export const C = {
  ambar: '#f59e0b', cielo: '#0ea5e9', indigo: '#6366f1', naranja: '#fb923c', verde: '#10b981',
  teal: '#0d9488', gris: '#9ca3af', rojo: '#ef4444', coral: '#d9565c', violeta: '#8b5cf6', pizarra: '#334155',
}

/** Color de una categoría de novedad (se guarda como nombre de Tailwind: red, orange, blue…). */
const COLOR_NOMBRE = {
  red: '#ef4444', orange: '#fb923c', amber: '#f59e0b', yellow: '#eab308', green: '#10b981', emerald: '#10b981',
  teal: '#0d9488', cyan: '#06b6d4', sky: '#0ea5e9', blue: '#3b82f6', indigo: '#6366f1', violet: '#8b5cf6',
  purple: '#a855f7', pink: '#ec4899', rose: '#f43f5e', slate: '#64748b', gray: '#9ca3af',
}
export const colorCategoria = (nombre) => COLOR_NOMBRE[nombre] ?? C.gris

export const CONFIRMACION = [
  { clave: 'CONFIRMADO',  label: 'Confirmadas', color: C.verde },
  { clave: 'SIN_LLAMAR',  label: 'Sin llamar',  color: C.gris },
  { clave: 'NO_CONTESTA', label: 'No contesta', color: C.ambar },
  { clave: 'REPROGRAMAR', label: 'Reprogramar', color: C.rojo },
]

export const DIAS_ATRAS = 7
export const DIAS_ADELANTE = 14
export const TIPO_CITA = { POLIGRAFISTA: 'Poligrafía', VISITADOR: 'Visita', ANALISTA_INTERNO: 'Análisis' }
const COLOR_TIPO = { Análisis: C.indigo, Poligrafía: C.violeta, Visita: C.cielo }

export const eje = { fontSize: 11, fill: '#9ca3af' }
export const tooltipEstilo = { fontSize: 12, borderRadius: 10, border: '1px solid #e5e7eb', boxShadow: '0 4px 12px rgba(0,0,0,.06)' }

/* ── Íconos ── */
const svg = (d, c = 'h-5 w-5', sw = 1.7) => (
  <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={sw}>
    {[].concat(d).map((p, i) => <path key={i} strokeLinecap="round" strokeLinejoin="round" d={p} />)}
  </svg>
)
export const Icono = {
  alerta: (c) => svg('M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z', c),
  reloj: (c) => svg('M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z', c),
  sello: (c) => svg('M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z', c),
  persona: (c) => svg('M18 7.5v3m0 0v3m0-3h3m-3 0h-3m-2.25-4.125a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zM3 19.235v-.11a6.375 6.375 0 0112.75 0v.109A12.318 12.318 0 019.374 21c-2.331 0-4.512-.645-6.374-1.766z', c),
  calendario: (c) => svg('M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5', c),
  telefono: (c) => svg('M2.25 6.75c0 8.284 6.716 15 15 15h2.25a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106c-.44-.11-.902.055-1.173.417l-.97 1.293c-.282.376-.769.542-1.21.38a12.035 12.035 0 01-7.143-7.143c-.162-.441.004-.928.38-1.21l1.293-.97c.363-.271.527-.734.417-1.173L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z', c),
  repetir: (c) => svg('M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99', c),
  lista: (c) => svg('M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08M15.75 18H18a2.25 2.25 0 002.25-2.25M8.25 8.25H4.875c-.621 0-1.125.504-1.125 1.125v11.25c0 .621.504 1.125 1.125 1.125h9.75c.621 0 1.125-.504 1.125-1.125V9.375c0-.621-.504-1.125-1.125-1.125H8.25z', c),
  panel: (c) => svg('M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z', c),
  flecha: (c) => svg('M8.25 4.5l7.5 7.5-7.5 7.5', c, 2),
}

/* ── Utilidades ── */
export const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const diaCorto = (d) => d.toLocaleDateString('es-CO', { weekday: 'short', day: 'numeric' }).replace('.', '')
export const semanaCorta = (s) => new Date(`${s}T12:00:00`).toLocaleDateString('es-CO', { day: 'numeric', month: 'short' }).replace('.', '')
const recortar = (t, n = 22) => (t ?? '').length > n ? `${t.slice(0, n - 1)}…` : t
export const suma = (arr, k = 'value') => arr.reduce((a, x) => a + (x[k] ?? 0), 0)
export const fechaLarga = () => new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

export function saludo() {
  const h = new Date().getHours()
  return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches'
}

/** Rango que consulta la gráfica de agenda: desde hace DIAS_ATRAS hasta DIAS_ADELANTE (00:00). */
export function rangoAgenda() {
  const desde = new Date(); desde.setHours(0, 0, 0, 0); desde.setDate(desde.getDate() - DIAS_ATRAS)
  const hasta = new Date(); hasta.setHours(0, 0, 0, 0); hasta.setDate(hasta.getDate() + DIAS_ADELANTE)
  return [`${iso(desde)}T00:00:00`, `${iso(hasta)}T00:00:00`]
}

/** Agrupa las asignaciones del calendario por día y tipo (poligrafía, visita, análisis) y resume
 *  la confirmación de las citas con el candidato de hoy en adelante. */
export function resumirAgenda(citas) {
  const lista = (citas ?? []).filter(c => c.fechaProgramada && c.estado !== 'CANCELADO' && TIPO_CITA[c.rolResponsable])
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0)
  const hoyIso = iso(hoy)
  const dias = Array.from({ length: DIAS_ATRAS + DIAS_ADELANTE }, (_, i) => { const d = new Date(hoy); d.setDate(d.getDate() - DIAS_ATRAS + i); return d })
  const porDia = Object.fromEntries(dias.map(d => [iso(d), {
    etiqueta: iso(d) === hoyIso ? 'Hoy' : diaCorto(d), pasado: d < hoy, Poligrafía: 0, Visita: 0, Análisis: 0,
  }]))
  lista.forEach(c => {
    const k = String(c.fechaProgramada).slice(0, 10)
    if (porDia[k]) porDia[k][TIPO_CITA[c.rolResponsable]] += 1
  })
  const futuras = lista.filter(c => c.rolResponsable !== 'ANALISTA_INTERNO' && String(c.fechaProgramada).slice(0, 10) >= hoyIso)
  return {
    lista,
    futuras,
    agenda: dias.map(d => porDia[iso(d)]),
    confirmacion: CONFIRMACION.map(x => ({ name: x.label, color: x.color, clave: x.clave,
      value: futuras.filter(c => (c.estadoConfirmacion ?? 'SIN_LLAMAR') === x.clave).length })),
    citasHoy: porDia[hoyIso]['Poligrafía'] + porDia[hoyIso].Visita,
  }
}

/** Cuenta una lista por la clave que devuelva `clave(item)` y deja los `n` más frecuentes. */
export function contarPor(lista, clave, n = 6) {
  const m = {}
  ;(lista ?? []).forEach(x => { const k = clave(x); if (k) m[k] = (m[k] ?? 0) + 1 })
  return Object.entries(m).map(([nombre, cantidad]) => ({ nombre, cantidad })).sort((a, b) => b.cantidad - a.cantidad).slice(0, n)
}

/* ── Piezas ── */

/** Tarjeta de "requiere tu atención": se colorea solo si hay algo (valor > 0). */
export function Atencion({ titulo, valor, detalle, vacio = 'Al día', icono, tono, onClick }) {
  const activo = valor > 0
  const tonos = {
    rojo: { borde: 'border-red-200', icono: 'bg-red-50 text-red-600', num: 'text-red-700', barra: 'bg-red-500' },
    ambar: { borde: 'border-amber-200', icono: 'bg-amber-50 text-amber-600', num: 'text-amber-700', barra: 'bg-amber-400' },
    primario: { borde: 'border-primary-200', icono: 'bg-primary-50 text-primary-600', num: 'text-primary-700', barra: 'bg-primary-500' },
  }
  const t = activo ? tonos[tono] : { borde: 'border-gray-200', icono: 'bg-gray-100 text-gray-400', num: 'text-gray-400', barra: 'bg-gray-200' }
  return (
    <button type="button" onClick={onClick}
      className={`group relative overflow-hidden rounded-2xl border bg-white p-4 text-left shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md ${t.borde}`}>
      <span className={`absolute inset-x-0 top-0 h-1 ${t.barra}`} />
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium text-gray-500">{titulo}</p>
        <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${t.icono}`}>{icono}</span>
      </div>
      <p className={`mt-1 text-3xl font-bold tabular-nums tracking-tight ${t.num}`}>{valor}</p>
      <p className="mt-0.5 flex items-center justify-between gap-2 text-xs text-gray-500">
        <span>{activo ? detalle : vacio}</span>
        <span className="text-gray-300 transition-colors group-hover:text-primary-500">{Icono.flecha('h-4 w-4')}</span>
      </p>
    </button>
  )
}

export function Grafica({ titulo, descripcion, accion, children, className = '' }) {
  return (
    <section className={`flex flex-col rounded-2xl border border-gray-200 bg-white p-5 shadow-sm ${className}`}>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-gray-900">{titulo}</h2>
          {descripcion && <p className="text-xs text-gray-500">{descripcion}</p>}
        </div>
        {accion}
      </div>
      <div className="flex-1">{children}</div>
    </section>
  )
}

export const Enlace = ({ onClick, children }) => (
  <button type="button" onClick={onClick} className="inline-flex shrink-0 items-center gap-0.5 text-xs font-semibold text-primary-600 hover:text-primary-700">
    {children} {Icono.flecha('h-3.5 w-3.5')}
  </button>
)

export const SinDatos = ({ texto, alto = 220 }) => (
  <div className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-gray-200 px-4 text-center" style={{ height: alto }}>
    <span className="flex h-9 w-9 items-center justify-center rounded-full bg-gray-50 text-gray-300">
      {svg('M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z', 'h-5 w-5')}
    </span>
    <p className="text-sm text-gray-400">{texto}</p>
  </div>
)

export const Cargando = ({ alto = 220 }) => <div className="animate-pulse rounded-xl bg-gray-100" style={{ height: alto }} />

/** Dona con el total en el centro y leyenda con números al lado. */
export function Dona({ datos, total, etiquetaTotal, onClick, alto = 220, vacio = 'Sin datos todavía' }) {
  const visibles = datos.filter(d => d.value > 0)
  if (!total) return <SinDatos texto={vacio} alto={alto} />
  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row">
      <div className="relative shrink-0" style={{ width: alto, height: alto }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={visibles} dataKey="value" nameKey="name" innerRadius="64%" outerRadius="92%" paddingAngle={2}
              stroke="none" onClick={(d) => onClick?.(d.payload ?? d)} className={onClick ? 'cursor-pointer' : ''}>
              {visibles.map(d => <Cell key={d.name} fill={d.color} />)}
            </Pie>
            <Tooltip contentStyle={tooltipEstilo} formatter={(v, n) => [`${v} (${Math.round((v / total) * 100)}%)`, n]} />
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-3xl font-bold tabular-nums text-gray-900">{total}</span>
          <span className="text-[11px] text-gray-500">{etiquetaTotal}</span>
        </div>
      </div>
      <ul className="grid w-full grid-cols-2 gap-x-4 gap-y-2 sm:grid-cols-1">
        {datos.map(d => (
          <li key={d.name}>
            <button type="button" onClick={() => onClick?.(d)} disabled={!onClick}
              className="flex w-full items-center gap-2 rounded-lg px-1.5 py-0.5 text-left text-xs enabled:hover:bg-gray-50">
              <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: d.color }} />
              <span className="flex-1 truncate text-gray-600">{d.name}</span>
              <span className="font-semibold tabular-nums text-gray-900">{d.value}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Barras horizontales [{ nombre, cantidad }] — carga por cliente, por estudio, por profesional… */
export function BarrasHorizontales({ datos, color, alto = 220, unidad = 'Servicios en curso', vacio = 'Sin servicios en curso' }) {
  if (!datos.length) return <SinDatos texto={vacio} alto={alto} />
  return (
    <ResponsiveContainer width="100%" height={alto}>
      <BarChart data={datos.map(d => ({ ...d, corto: recortar(d.nombre) }))} layout="vertical" margin={{ left: 0, right: 16, top: 0, bottom: 0 }}>
        <CartesianGrid horizontal={false} stroke="#f3f4f6" />
        <XAxis type="number" allowDecimals={false} tick={eje} axisLine={false} tickLine={false} />
        <YAxis type="category" dataKey="corto" width={140} tick={{ ...eje, fill: '#4b5563' }} axisLine={false} tickLine={false} />
        <Tooltip contentStyle={tooltipEstilo} cursor={{ fill: '#f9fafb' }}
          formatter={(v) => [v, unidad]} labelFormatter={(_, p) => p?.[0]?.payload?.nombre ?? ''} />
        <Bar dataKey="cantidad" fill={color} radius={[0, 6, 6, 0]} maxBarSize={22} />
      </BarChart>
    </ResponsiveContainer>
  )
}

/** Barra lineal de estados (semáforo) con el número y porcentaje de cada uno.
 *  estados: [{ name, value, color, clave }] */
export function BarraEstados({ titulo, descripcion, estados, destacado, onClick }) {
  const total = suma(estados)
  const cols = { 4: 'lg:grid-cols-4', 5: 'lg:grid-cols-5', 6: 'lg:grid-cols-6', 7: 'lg:grid-cols-7' }[estados.length] ?? 'lg:grid-cols-7'
  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-gray-900">{titulo}</h2>
          {descripcion && <p className="text-xs text-gray-500">{descripcion}</p>}
        </div>
        <div className="flex items-baseline gap-4 text-right">
          {destacado && <>
            <div><p className="text-2xl font-bold tabular-nums text-gray-900">{destacado.valor}</p><p className="text-[11px] text-gray-500">{destacado.etiqueta}</p></div>
            <div className="h-8 w-px bg-gray-200" />
          </>}
          <div><p className="text-2xl font-bold tabular-nums text-gray-400">{total}</p><p className="text-[11px] text-gray-500">en total</p></div>
        </div>
      </div>

      <div className="mt-4 flex h-3 overflow-hidden rounded-full bg-gray-100">
        {total > 0 && estados.filter(x => x.value > 0).map(x => (
          <button key={x.clave} type="button" onClick={() => onClick?.(x)} disabled={!onClick}
            className="h-full transition-opacity enabled:hover:opacity-80" style={{ width: `${(x.value / total) * 100}%`, background: x.color }}
            title={`${x.name}: ${x.value}`} aria-label={`${x.name}: ${x.value}`} />
        ))}
      </div>

      <div className={`mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4 ${cols}`}>
        {estados.map(x => (
          <button key={x.clave} type="button" onClick={() => onClick?.(x)} disabled={!onClick}
            className="rounded-xl border border-transparent px-3 py-2 text-left transition-colors enabled:hover:border-gray-200 enabled:hover:bg-gray-50">
            <span className="flex items-center gap-1.5 text-xs text-gray-500">
              <span className="h-2 w-2 rounded-full" style={{ background: x.color }} />{x.name}
            </span>
            <span className="mt-0.5 flex items-baseline gap-1.5">
              <span className="text-lg font-semibold tabular-nums text-gray-900">{x.value}</span>
              <span className="text-[11px] text-gray-400">{total > 0 ? `${Math.round((x.value / total) * 100)}%` : ''}</span>
            </span>
          </button>
        ))}
      </div>
    </section>
  )
}

/** Barras apiladas por día (semana pasada en tono claro, "Hoy" marcado, próximos 14 días). */
export function GraficaAgenda({ agenda, series = ['Análisis', 'Poligrafía', 'Visita'], alto = 220 }) {
  return (
    <ResponsiveContainer width="100%" height={alto}>
      <BarChart data={agenda} margin={{ left: -20, right: 8, top: 8, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="#f3f4f6" />
        <XAxis dataKey="etiqueta" tick={eje} axisLine={false} tickLine={false} interval={0} angle={-35} textAnchor="end" height={42} />
        <YAxis allowDecimals={false} tick={eje} axisLine={false} tickLine={false} />
        <Tooltip contentStyle={tooltipEstilo} cursor={{ fill: '#f9fafb' }} />
        <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
        <ReferenceLine x="Hoy" stroke={C.coral} strokeDasharray="4 4" />
        {series.map((serie, i) => (
          <Bar key={serie} dataKey={serie} stackId="c" fill={COLOR_TIPO[serie]} maxBarSize={22}
            radius={i === series.length - 1 ? [5, 5, 0, 0] : 0}>
            {agenda.map((d, j) => <Cell key={j} fill={COLOR_TIPO[serie]} fillOpacity={d.pasado ? 0.35 : 1} />)}
          </Bar>
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}

/** "hace 5 h" / "hace 3 d" */
function hace(isoFecha) {
  const h = Math.floor((Date.now() - new Date(isoFecha).getTime()) / 3600000)
  return h < 1 ? 'hace minutos' : h < 48 ? `hace ${h} h` : `hace ${Math.floor(h / 24)} d`
}

/** Novedades internas de subproceso (GraficasGestorResponse.Novedades): abiertas por categoría,
 *  las que más llevan esperando respuesta, registradas vs. resueltas por semana y abiertas por tipo. */
export function SeccionNovedades({ nov, onAbrir, descripcion, tituloEspera = 'Esperando tu respuesta',
  descripcionEspera = 'Las abiertas más antiguas · clic para responder', etiquetaRespuesta = 'Tiempo promedio de respuesta (30 días)' }) {
  const novCategorias = (nov?.porCategoria ?? []).map(c => ({ name: c.nombre, value: c.cantidad, color: colorCategoria(c.color) }))
  const novSemanas = (nov?.porSemana ?? []).map(x => ({ semana: semanaCorta(x.inicio), Registradas: x.registradas, Resueltas: x.resueltas }))
  const horasRespuesta = nov?.horasPromedioResolucion
  const textoRespuesta = horasRespuesta == null ? 'sin datos'
    : horasRespuesta < 1 ? `${Math.max(1, Math.round(horasRespuesta * 60))} min`
    : horasRespuesta < 48 ? `${horasRespuesta} h` : `${Math.round(horasRespuesta / 24)} d`
  return (
    <section id="novedades" className="space-y-3 scroll-mt-4">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h2 className="text-xs font-semibold uppercase tracking-wider text-gray-500">Novedades internas</h2>
          {descripcion && <p className="text-xs text-gray-400">{descripcion}</p>}
        </div>
        <span className="rounded-full bg-white px-3 py-1 text-xs text-gray-600 ring-1 ring-gray-200">
          {etiquetaRespuesta}: <span className="font-semibold text-gray-900">{textoRespuesta}</span>
        </span>
      </div>
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <Grafica titulo="Abiertas por categoría" descripcion="Novedades que todavía no tienen respuesta">
          <Dona datos={novCategorias} total={nov?.abiertas ?? 0} etiquetaTotal="abiertas" vacio="No hay novedades abiertas" />
        </Grafica>

        <Grafica titulo={tituloEspera} descripcion={descripcionEspera}>
          {!nov?.masAntiguas?.length ? <SinDatos texto="No hay novedades abiertas" /> : (
            <ul className="-mx-2 space-y-1">
              {nov.masAntiguas.map(n => {
                const horas = (Date.now() - new Date(n.fechaRegistro).getTime()) / 3600000
                return (
                  <li key={n.id}>
                    <button type="button" onClick={() => onAbrir?.(n)}
                      className="flex w-full items-center gap-3 rounded-xl px-2 py-2.5 text-left transition-colors hover:bg-gray-50">
                      <span className="h-9 w-1.5 shrink-0 rounded-full" style={{ background: colorCategoria(n.categoriaColor) }} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium text-gray-900">{n.tipo}</span>
                        <span className="block truncate text-xs text-gray-500">{n.subproceso} · {n.candidato}</span>
                      </span>
                      <span className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ${
                        horas >= 48 ? 'bg-red-50 text-red-700 ring-red-600/20' : 'bg-amber-50 text-amber-700 ring-amber-600/20'}`}>
                        {hace(n.fechaRegistro)}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </Grafica>

        <Grafica titulo="Registradas vs. resueltas" descripcion="Últimas 8 semanas">
          {novSemanas.every(x => x.Registradas + x.Resueltas === 0) ? <SinDatos texto="Sin novedades en estas semanas" /> : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={novSemanas} margin={{ left: -20, right: 8, top: 8, bottom: 0 }}>
                <CartesianGrid vertical={false} stroke="#f3f4f6" />
                <XAxis dataKey="semana" tick={eje} axisLine={false} tickLine={false} />
                <YAxis allowDecimals={false} tick={eje} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={tooltipEstilo} cursor={{ fill: '#f9fafb' }} labelFormatter={(l) => `Semana del ${l}`} />
                <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="Registradas" fill={C.naranja} radius={[5, 5, 0, 0]} maxBarSize={18} />
                <Bar dataKey="Resueltas" fill={C.verde} radius={[5, 5, 0, 0]} maxBarSize={18} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Grafica>

        <Grafica titulo="Abiertas por tipo" descripcion="Qué es lo que más se está reportando">
          <BarrasHorizontales datos={nov?.porTipo ?? []} color={C.rojo} unidad="Abiertas" vacio="No hay novedades abiertas" />
        </Grafica>
      </div>
    </section>
  )
}

/** Asignaciones de hoy agrupadas por hora (6 a. m. a 7 p. m.) y tipo (Poligrafía/Visita/Análisis). */
export function porHoraHoy(lista) {
  const hoy = iso(new Date())
  const horas = Array.from({ length: 14 }, (_, i) => ({
    hora: `${((i + 6) % 12) || 12}${i + 6 < 12 ? 'a' : 'p'}`, h: i + 6, Poligrafía: 0, Visita: 0, Análisis: 0,
  }))
  ;(lista ?? []).filter(c => String(c.fechaProgramada).slice(0, 10) === hoy).forEach(c => {
    const fila = horas.find(x => x.h === new Date(c.fechaProgramada).getHours())
    if (fila && TIPO_CITA[c.rolResponsable]) fila[TIPO_CITA[c.rolResponsable]] += 1
  })
  return horas
}

/** Barras por hora del día (datos de porHoraHoy). */
export function GraficaHoras({ datos, series, alto = 220 }) {
  return (
    <ResponsiveContainer width="100%" height={alto}>
      <BarChart data={datos} margin={{ left: -20, right: 8, top: 8, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="#f3f4f6" />
        <XAxis dataKey="hora" tick={eje} axisLine={false} tickLine={false} interval={0} />
        <YAxis allowDecimals={false} tick={eje} axisLine={false} tickLine={false} />
        <Tooltip contentStyle={tooltipEstilo} cursor={{ fill: '#f9fafb' }} />
        {series.length > 1 && <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />}
        {series.map((serie, i) => (
          <Bar key={serie} dataKey={serie} stackId="h" fill={COLOR_TIPO[serie]} maxBarSize={26}
            radius={i === series.length - 1 ? [5, 5, 0, 0] : 0} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}

/** Pestaña activa de un tablero, recordada en este navegador (clave de localStorage). */
export function usePestana(clave, defecto) {
  const [pestana, setEstado] = useState(() => {
    try { return localStorage.getItem(clave) || defecto } catch { return defecto }
  })
  const cambiar = (id) => { setEstado(id); try { localStorage.setItem(clave, id) } catch { /* sin almacenamiento */ } }
  return [pestana, cambiar]
}

/** Barra de pestañas del tablero. pestanas: [{ id, label, badge?, alerta? }] */
export function Pestanas({ pestanas, activa, onCambiar }) {
  return (
    <div id="pestanas" role="tablist" className="flex flex-wrap scroll-mt-4 gap-1 rounded-xl border border-gray-200 bg-white p-1 shadow-sm">
      {pestanas.map(t => {
        const sel = activa === t.id
        return (
          <button key={t.id} type="button" role="tab" aria-selected={sel} onClick={() => onCambiar(t.id)}
            className={`flex shrink-0 items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              sel ? 'bg-primary-600 text-white shadow-sm shadow-primary-600/20' : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'}`}>
            {t.label}
            {t.badge > 0 && (
              <span className={`min-w-[20px] rounded-full px-1.5 text-center text-[11px] font-semibold tabular-nums ${
                t.alerta ? 'bg-red-500 text-white' : sel ? 'bg-white/20 text-white' : 'bg-gray-100 text-gray-700'}`}>
                {t.badge}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}

/** Cambia de pestaña y lleva la vista hasta la barra de pestañas (desde las tarjetas de arriba). */
export function irAPestana(cambiar, id) {
  cambiar(id)
  document.getElementById('pestanas')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

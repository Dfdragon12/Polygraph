import { useState, useEffect, useCallback, useMemo } from 'react'
import api from '../../services/api'
import {
  PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'

/* ════════ CONSTANTES ════════ */

const CONFIG_ESTADO = {
  PENDIENTE:    { label: 'Pendiente',    color: '#f59e0b', bg: 'bg-amber-50',   border: 'border-amber-200',   num: 'text-amber-600',   dot: 'bg-amber-400',   badge: 'bg-amber-100 text-amber-800'   },
  PROGRAMANDO:  { label: 'Programando',  color: '#3b82f6', bg: 'bg-blue-50',    border: 'border-blue-200',    num: 'text-blue-600',    dot: 'bg-blue-400',    badge: 'bg-blue-100 text-blue-800'     },
  EN_EJECUCION: { label: 'En Ejecución', color: '#6366f1', bg: 'bg-indigo-50',  border: 'border-indigo-200',  num: 'text-indigo-600',  dot: 'bg-indigo-400',  badge: 'bg-indigo-100 text-indigo-800' },
  FINALIZADO:   { label: 'Finalizado',   color: '#10b981', bg: 'bg-green-50',   border: 'border-green-200',   num: 'text-green-600',   dot: 'bg-green-400',   badge: 'bg-green-100 text-green-800'   },
  PUBLICADO:    { label: 'Publicado',    color: '#06b6d4', bg: 'bg-cyan-50',    border: 'border-cyan-200',    num: 'text-cyan-600',    dot: 'bg-cyan-400',    badge: 'bg-cyan-100 text-cyan-800'     },
  CANCELADO:    { label: 'Cancelado',    color: '#ef4444', bg: 'bg-red-50',     border: 'border-red-200',     num: 'text-red-600',     dot: 'bg-red-400',     badge: 'bg-red-100 text-red-800'       },
  REPROGRAMADO: { label: 'Reprogramado', color: '#f97316', bg: 'bg-orange-50',  border: 'border-orange-200',  num: 'text-orange-600',  dot: 'bg-orange-400',  badge: 'bg-orange-100 text-orange-800' },
}

const TODOS_ESTADOS = Object.keys(CONFIG_ESTADO)

const GRUPOS = {
  'En curso':    ['PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION'],
  'Completados': ['FINALIZADO', 'PUBLICADO'],
  'Cerrados':    ['CANCELADO', 'REPROGRAMADO'],
}

function formatFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/* ════════ TOOLTIP CUSTOM ════════ */
function CustomTooltip({ active, payload, total }) {
  if (!active || !payload?.length) return null
  const { name, value } = payload[0]
  const pct = total > 0 ? Math.round((value / total) * 100) : 0
  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-lg px-3 py-2 text-xs">
      <p className="font-semibold text-gray-800">{name}</p>
      <p className="text-gray-500">{value} servicios · {pct}%</p>
    </div>
  )
}

/* ════════ PDF SEMÁFORO ════════ */
function exportarPDF({ servicios, clienteNombre, estadosFiltro }) {
  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
  const MAR = 14; const ANCHO = doc.internal.pageSize.getWidth(); let y = MAR

  // Header
  doc.setFillColor(79, 70, 229)
  doc.rect(0, 0, ANCHO, 20, 'F')
  doc.setFontSize(14); doc.setFont('helvetica', 'bold'); doc.setTextColor(255, 255, 255)
  doc.text('Semáforo de Servicios — Polygraph Service ERP', MAR, 12)
  y = 26
  doc.setFontSize(8); doc.setFont('helvetica', 'normal'); doc.setTextColor(100, 116, 139)
  const filtroTexto = [
    clienteNombre ? `Cliente: ${clienteNombre}` : 'Todos los clientes',
    estadosFiltro.length === TODOS_ESTADOS.length ? 'Todos los estados' : `Estados: ${estadosFiltro.map(e => CONFIG_ESTADO[e]?.label ?? e).join(', ')}`,
    `Generado: ${new Date().toLocaleString('es-CO')}`,
  ].join(' · ')
  doc.text(filtroTexto, MAR, y); y += 8

  // KPIs resumen
  const total = servicios?.totalServicios ?? 0
  const estados = servicios?.estados ?? []
  const enCurso = estados.filter(e => GRUPOS['En curso'].includes(e.estado)).reduce((s, e) => s + e.total, 0)
  const completados = estados.filter(e => GRUPOS['Completados'].includes(e.estado)).reduce((s, e) => s + e.total, 0)
  const cerrados = estados.filter(e => GRUPOS['Cerrados'].includes(e.estado)).reduce((s, e) => s + e.total, 0)

  doc.setFontSize(10); doc.setFont('helvetica', 'bold'); doc.setTextColor(15, 23, 42)
  doc.text('Resumen', MAR, y); y += 2
  autoTable(doc, {
    startY: y, margin: { left: MAR, right: MAR },
    head: [['Indicador', 'Cantidad', 'Porcentaje']],
    body: [
      ['Total servicios', String(total), '100%'],
      ['En curso (Pendiente + Programando + En ejecución)', String(enCurso), total > 0 ? `${Math.round(enCurso/total*100)}%` : '0%'],
      ['Completados (Finalizado + Publicado)', String(completados), total > 0 ? `${Math.round(completados/total*100)}%` : '0%'],
      ['Cerrados (Cancelado + Reprogramado)', String(cerrados), total > 0 ? `${Math.round(cerrados/total*100)}%` : '0%'],
    ],
    styles: { fontSize: 9, cellPadding: 3 },
    headStyles: { fillColor: [79, 70, 229], textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: [248, 250, 252] },
  })
  y = doc.lastAutoTable.finalY + 8

  // Detalle por estado
  doc.setFontSize(10); doc.setFont('helvetica', 'bold'); doc.setTextColor(15, 23, 42)
  doc.text('Detalle por estado', MAR, y); y += 2
  autoTable(doc, {
    startY: y, margin: { left: MAR, right: MAR },
    head: [['Estado', 'Cantidad', 'Porcentaje', 'Grupo']],
    body: estados.map(e => {
      const grupo = Object.entries(GRUPOS).find(([, arr]) => arr.includes(e.estado))?.[0] ?? 'Otro'
      return [CONFIG_ESTADO[e.estado]?.label ?? e.estado, String(e.total), total > 0 ? `${Math.round(e.total/total*100)}%` : '0%', grupo]
    }),
    styles: { fontSize: 9, cellPadding: 3 },
    headStyles: { fillColor: [79, 70, 229], textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: [248, 250, 252] },
  })

  const pags = doc.internal.getNumberOfPages()
  for (let i = 1; i <= pags; i++) {
    doc.setPage(i); doc.setFontSize(8); doc.setTextColor(148, 163, 184)
    doc.text(`Página ${i} de ${pags} · Polygraph Service ERP`, MAR, doc.internal.pageSize.getHeight() - 8)
  }
  doc.save(`semaforo-servicios-${new Date().toISOString().split('T')[0]}.pdf`)
}

/* ════════ PÁGINA PRINCIPAL ════════ */
export default function SemaforoServicios() {
  const [servicios,       setServicios]       = useState(null)
  const [cargando,        setCargando]        = useState(true)
  const [error,           setError]           = useState(null)
  const [clienteSel,      setClienteSel]      = useState('')
  const [estadosFiltro,   setEstadosFiltro]   = useState(new Set(TODOS_ESTADOS))
  const [ordenSemaforo,   setOrdenSemaforo]   = useState('orden')
  const [exportando,      setExportando]      = useState(false)

  const cargar = useCallback((id) => {
    setCargando(true)
    api.get(`/admin/dashboard/servicios${id ? `?idCliente=${id}` : ''}`)
      .then(r => setServicios(r.data))
      .catch(() => setError('No se pudieron cargar los servicios.'))
      .finally(() => setCargando(false))
  }, [])

  useEffect(() => { cargar(clienteSel || null) }, [clienteSel, cargar])

  const toggleEstado = (estado) => {
    setEstadosFiltro(prev => {
      const next = new Set(prev)
      next.has(estado) ? next.delete(estado) : next.add(estado)
      return next.size === 0 ? new Set(TODOS_ESTADOS) : next
    })
  }

  const selTodos = () => setEstadosFiltro(new Set(TODOS_ESTADOS))

  // Datos filtrados
  const estadosFiltrados = useMemo(() => {
    let lista = (servicios?.estados ?? []).filter(e => estadosFiltro.has(e.estado))
    if (ordenSemaforo === 'mayor') return [...lista].sort((a, b) => b.total - a.total)
    if (ordenSemaforo === 'menor') return [...lista].sort((a, b) => a.total - b.total)
    return lista
  }, [servicios, estadosFiltro, ordenSemaforo])

  const totalFiltrado = estadosFiltrados.reduce((s, e) => s + e.total, 0)
  const totalGlobal   = servicios?.totalServicios ?? 0

  // KPIs calculados
  const kpis = useMemo(() => {
    const est = servicios?.estados ?? []
    const sum = (keys) => est.filter(e => keys.includes(e.estado)).reduce((s, e) => s + e.total, 0)
    return {
      total:      totalGlobal,
      enCurso:    sum(GRUPOS['En curso']),
      completados:sum(GRUPOS['Completados']),
      cerrados:   sum(GRUPOS['Cerrados']),
    }
  }, [servicios, totalGlobal])

  // Donut data
  const donutData = useMemo(() =>
    estadosFiltrados.filter(e => e.total > 0).map(e => ({
      name: CONFIG_ESTADO[e.estado]?.label ?? e.estado,
      value: e.total,
      color: CONFIG_ESTADO[e.estado]?.color ?? '#94a3b8',
    })),
    [estadosFiltrados]
  )

  // Barras horizontales
  const barData = useMemo(() =>
    [...estadosFiltrados]
      .sort((a, b) => b.total - a.total)
      .map(e => ({
        name: CONFIG_ESTADO[e.estado]?.label ?? e.estado,
        value: e.total,
        color: CONFIG_ESTADO[e.estado]?.color ?? '#94a3b8',
        pct: totalFiltrado > 0 ? Math.round((e.total / totalFiltrado) * 100) : 0,
      })),
    [estadosFiltrados, totalFiltrado]
  )

  const clienteNombre = servicios?.clientes?.find(c => String(c.idCliente) === clienteSel)?.nombre ?? ''

  const handleExportPDF = () => {
    setExportando(true)
    try { exportarPDF({ servicios, clienteNombre, estadosFiltro: [...estadosFiltro] }) }
    finally { setExportando(false) }
  }

  return (
    <div className="space-y-6">

      {/* ── Encabezado ── */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Semáforo de servicios</h2>
          <p className="text-sm text-gray-500 mt-0.5">Distribución, análisis y reporte de servicios por estado</p>
        </div>
        <button
          onClick={handleExportPDF}
          disabled={exportando || cargando}
          className="inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-medium px-4 py-2.5 rounded-xl transition-colors flex-shrink-0 shadow-sm"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          {exportando ? 'Generando…' : 'Exportar PDF'}
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl flex justify-between">
          {error} <button onClick={() => setError(null)} className="ml-4 text-red-400 hover:text-red-600">×</button>
        </div>
      )}

      {/* ── Panel de filtros ── */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
        <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-4">Filtros</p>
        <div className="flex flex-wrap items-start gap-6">
          {/* Filtro cliente */}
          <div className="flex-1 min-w-[200px] max-w-xs">
            <label className="block text-xs font-medium text-gray-600 mb-1.5">Cliente</label>
            <div className="flex gap-2">
              <select
                value={clienteSel}
                onChange={e => setClienteSel(e.target.value)}
                className="flex-1 border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
              >
                <option value="">Todos los clientes</option>
                {servicios?.clientes?.map(c => (
                  <option key={c.idCliente} value={c.idCliente}>{c.nombre}</option>
                ))}
              </select>
              {clienteSel && (
                <button onClick={() => setClienteSel('')}
                  className="px-2.5 py-2 text-gray-400 hover:text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors text-sm">
                  ✕
                </button>
              )}
            </div>
          </div>

          {/* Filtro estados */}
          <div className="flex-1 min-w-[280px]">
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-xs font-medium text-gray-600">Estados</label>
              <button onClick={selTodos} className="text-xs text-indigo-600 hover:text-indigo-800">
                Seleccionar todos
              </button>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {TODOS_ESTADOS.map(estado => {
                const cfg = CONFIG_ESTADO[estado]
                const activo = estadosFiltro.has(estado)
                return (
                  <button
                    key={estado}
                    onClick={() => toggleEstado(estado)}
                    className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg border transition-colors ${
                      activo ? cfg.badge + ' border-transparent' : 'bg-gray-50 text-gray-400 border-gray-200'
                    }`}
                  >
                    <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: activo ? cfg.color : '#d1d5db' }} />
                    {cfg.label}
                  </button>
                )
              })}
            </div>
          </div>

          {/* Ordenamiento semáforo */}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1.5">Orden semáforo</label>
            <div className="flex gap-1">
              {[
                { key: 'orden', label: 'Por defecto' },
                { key: 'mayor', label: '↓ Mayor'     },
                { key: 'menor', label: '↑ Menor'     },
              ].map(o => (
                <button key={o.key} onClick={() => setOrdenSemaforo(o.key)}
                  className={`text-xs px-3 py-2 rounded-lg border transition-colors ${
                    ordenSemaforo === o.key
                      ? 'bg-indigo-600 text-white border-indigo-600'
                      : 'bg-white text-gray-500 border-gray-200 hover:border-indigo-300'
                  }`}>
                  {o.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Info del filtro activo */}
        <div className="mt-4 pt-4 border-t border-gray-100 flex flex-wrap gap-2 items-center">
          <span className="text-xs text-gray-500">
            Mostrando <strong>{estadosFiltro.size}</strong> de {TODOS_ESTADOS.length} estados ·
            <strong> {totalFiltrado}</strong> de {totalGlobal} servicios
          </span>
          {clienteNombre && (
            <span className="text-xs bg-indigo-50 text-indigo-700 border border-indigo-100 px-2 py-0.5 rounded-full">
              {clienteNombre}
            </span>
          )}
        </div>
      </div>

      {cargando ? (
        <div className="flex items-center justify-center h-48">
          <div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" />
        </div>
      ) : (
        <>
          {/* ── KPIs ── */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {[
              { label: 'Total servicios',  valor: kpis.total,       color: 'indigo', icono: '📋' },
              { label: 'En curso',         valor: kpis.enCurso,     color: 'blue',   icono: '⚡' },
              { label: 'Completados',      valor: kpis.completados, color: 'green',  icono: '✅' },
              { label: 'Cerrados',         valor: kpis.cerrados,    color: 'red',    icono: '🔒' },
            ].map(k => (
              <div key={k.label} className={`bg-white rounded-xl border p-4 shadow-sm ${
                k.color === 'indigo' ? 'border-indigo-100' :
                k.color === 'blue'   ? 'border-blue-100'   :
                k.color === 'green'  ? 'border-green-100'  : 'border-red-100'
              }`}>
                <div className="flex items-center gap-3">
                  <span className="text-2xl">{k.icono}</span>
                  <div>
                    <p className="text-xs text-gray-500 font-medium">{k.label}</p>
                    <p className={`text-2xl font-bold ${
                      k.color === 'indigo' ? 'text-indigo-700' :
                      k.color === 'blue'   ? 'text-blue-700'   :
                      k.color === 'green'  ? 'text-green-700'  : 'text-red-700'
                    }`}>{k.valor}</p>
                    <p className="text-xs text-gray-400">
                      {kpis.total > 0 ? `${Math.round(k.valor/kpis.total*100)}% del total` : '0%'}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* ── Gráficas ── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Donut */}
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
              <h3 className="text-sm font-semibold text-gray-800 mb-4">Distribución por estado</h3>
              {donutData.length === 0 ? (
                <div className="flex items-center justify-center h-52 text-gray-400 text-sm">Sin datos con los filtros actuales</div>
              ) : (
                <ResponsiveContainer width="100%" height={240}>
                  <PieChart>
                    <Pie data={donutData} cx="50%" cy="50%"
                      innerRadius={65} outerRadius={95} paddingAngle={2} dataKey="value">
                      {donutData.map((e, i) => <Cell key={i} fill={e.color} />)}
                    </Pie>
                    <Tooltip content={<CustomTooltip total={totalFiltrado} />} />
                    <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 11 }} />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* Barras horizontales */}
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
              <h3 className="text-sm font-semibold text-gray-800 mb-4">Comparativa por estado</h3>
              {barData.length === 0 ? (
                <div className="flex items-center justify-center h-52 text-gray-400 text-sm">Sin datos con los filtros actuales</div>
              ) : (
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={barData} layout="vertical" margin={{ left: 4, right: 24, top: 4, bottom: 4 }}>
                    <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#f0f0f0" />
                    <XAxis type="number" tick={{ fontSize: 10, fill: '#9ca3af' }} axisLine={false} tickLine={false} />
                    <YAxis type="category" dataKey="name" width={88} tick={{ fontSize: 10, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                    <Tooltip
                      formatter={(val, name, props) => [`${val} servicios (${props.payload.pct}%)`, 'Cantidad']}
                      contentStyle={{ fontSize: 11, borderRadius: 8, border: '1px solid #e5e7eb' }}
                    />
                    <Bar dataKey="value" radius={[0, 6, 6, 0]} maxBarSize={28}>
                      {barData.map((e, i) => <Cell key={i} fill={e.color} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          {/* ── Grupos resumen ── */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {Object.entries(GRUPOS).map(([grupo, estados]) => {
              const items = estadosFiltrados.filter(e => estados.includes(e.estado))
              const subtotal = items.reduce((s, e) => s + e.total, 0)
              return (
                <div key={grupo} className="bg-white rounded-xl border border-gray-200 shadow-sm p-4">
                  <div className="flex items-center justify-between mb-3">
                    <h4 className="text-sm font-semibold text-gray-800">{grupo}</h4>
                    <span className="text-lg font-bold text-gray-900">{subtotal}</span>
                  </div>
                  <div className="space-y-2">
                    {items.length === 0 ? (
                      <p className="text-xs text-gray-400">Sin datos</p>
                    ) : items.map(e => {
                      const cfg = CONFIG_ESTADO[e.estado]
                      const pct = totalGlobal > 0 ? Math.round(e.total / totalGlobal * 100) : 0
                      return (
                        <div key={e.estado}>
                          <div className="flex items-center justify-between text-xs mb-1">
                            <span className="flex items-center gap-1.5 text-gray-700">
                              <span className="w-2 h-2 rounded-full" style={{ background: cfg.color }} />
                              {cfg.label}
                            </span>
                            <span className="font-medium text-gray-800">{e.total} <span className="text-gray-400">({pct}%)</span></span>
                          </div>
                          <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
                            <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, background: cfg.color }} />
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )
            })}
          </div>

          {/* ── Semáforo en cards ── */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
            <h3 className="text-sm font-semibold text-gray-800 mb-4">Semáforo detallado</h3>
            {estadosFiltrados.length === 0 ? (
              <div className="flex items-center justify-center h-20 text-gray-400 text-sm">Sin estados seleccionados</div>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7 gap-3">
                {estadosFiltrados.map(e => {
                  const cfg = CONFIG_ESTADO[e.estado] ?? { label: e.estado, bg: 'bg-gray-50', border: 'border-gray-200', num: 'text-gray-600', dot: 'bg-gray-400' }
                  const pct = totalGlobal > 0 ? Math.round((e.total / totalGlobal) * 100) : 0
                  return (
                    <div key={e.estado} className={`rounded-xl border ${cfg.border} ${cfg.bg} p-4 flex flex-col items-center text-center`}>
                      <span className={`w-2.5 h-2.5 rounded-full ${cfg.dot} mb-2`} />
                      <span className={`text-3xl font-bold ${cfg.num}`}>{e.total}</span>
                      <span className="text-xs text-gray-600 mt-1 leading-tight font-medium">{cfg.label}</span>
                      <span className="text-xs text-gray-400 mt-1">{pct}%</span>
                      <div className="w-full h-1 bg-white/50 rounded-full mt-2 overflow-hidden">
                        <div className="h-full rounded-full" style={{ width: `${pct}%`, background: cfg.color }} />
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}

// Pestañas del tablero del administrador que abarcan todo el sistema (solicitudes, clientes, candidatos,
// dinero y configuración) en un periodo que él escoge, y el panel lateral que abre el detalle de cualquier
// recuadro sin salir del tablero.
import { parseFecha } from '../../utils/fechas'
import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  AreaChart, Area, PieChart, Pie, Cell,
} from 'recharts'
import api from '../../services/api'
import { Seccion, Recuadro, BarrasRanking, entero, pesos, pesosDeCentavos } from './PiezasTablero'

export const PERIODOS = [
  { dias: 7,  etiqueta: '7 días' },
  { dias: 30, etiqueta: '30 días' },
  { dias: 90, etiqueta: '90 días' },
]

const fechaCorta = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: 'short' })

function fechaHora(iso) {
  if (!iso) return ''
  return parseFecha(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

/* ════════ PANEL LATERAL DE DETALLE ════════ */

/** Abre las filas más importantes de un tema (reversiones, documentos, novedades, estado de solicitudes...).
 *  Se vuelve a pedir cada vez que `refresco` cambia, así que también está en vivo. */
export function PanelDetalle({ tema, refresco, onCerrar }) {
  const navigate = useNavigate()
  const [detalle, setDetalle] = useState(null)
  const [error, setError] = useState(false)
  const cerrar = useRef(null)

  useEffect(() => {
    let vigente = true
    api.get(`/admin/dashboard/detalle/${encodeURIComponent(tema)}`)
      .then(({ data }) => { if (vigente) { setDetalle(data); setError(false) } })
      .catch(() => { if (vigente) setError(true) })
    return () => { vigente = false }
  }, [tema, refresco])

  useEffect(() => {
    cerrar.current?.focus()
    const alTeclear = (e) => { if (e.key === 'Escape') onCerrar() }
    document.addEventListener('keydown', alTeclear)
    return () => document.removeEventListener('keydown', alTeclear)
  }, [onCerrar])

  return (
    <div className="fixed inset-0 z-50 flex justify-end" role="dialog" aria-modal="true" aria-label="Detalle">
      <button type="button" tabIndex={-1} aria-label="Cerrar detalle" onClick={onCerrar} className="absolute inset-0 bg-black/30" />
      <aside className="relative w-full max-w-md bg-white shadow-xl flex flex-col h-full">
        <header className="flex items-start justify-between gap-3 px-5 py-4 border-b border-gray-100">
          <div className="min-w-0">
            <h3 className="text-base font-semibold text-gray-900">{detalle?.titulo ?? 'Cargando…'}</h3>
            {detalle && (
              <p className="text-xs text-gray-500 mt-0.5">
                {detalle.total === 0 ? 'Nada por mostrar' : `${entero(detalle.total)} en total · mostrando ${detalle.items.length}`}
              </p>
            )}
          </div>
          <button ref={cerrar} type="button" onClick={onCerrar} aria-label="Cerrar"
            className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-100 text-lg leading-none">×</button>
        </header>

        <div className="flex-1 overflow-y-auto px-5 py-3">
          {error && <p className="text-sm text-red-600" role="alert">No se pudo cargar el detalle.</p>}
          {!error && !detalle && (
            <div className="flex justify-center py-10">
              <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" />
            </div>
          )}
          {detalle && detalle.items.length === 0 && (
            <div className="rounded-xl bg-emerald-50 border border-emerald-100 px-4 py-4 text-sm text-emerald-800">
              No hay nada pendiente en este tema.
            </div>
          )}
          {detalle && detalle.items.length > 0 && (
            <ul className="divide-y divide-gray-100">
              {detalle.items.map((it, i) => (
                <li key={`${it.titulo}-${i}`}>
                  <button type="button" onClick={() => navigate(it.ruta)}
                    className="w-full text-left py-3 px-1 hover:bg-gray-50 rounded-lg transition-colors">
                    <span className="block text-sm font-medium text-gray-800">{it.titulo}</span>
                    {it.detalle && <span className="block text-xs text-gray-600 mt-0.5">{it.detalle}</span>}
                    {it.fecha && <span className="block text-xs text-gray-500 mt-0.5">{fechaHora(it.fecha)}</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {detalle && (
          <footer className="px-5 py-3 border-t border-gray-100">
            <button type="button" onClick={() => navigate(detalle.rutaVerTodo)}
              className="w-full text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 rounded-lg py-2.5 transition-colors">
              Ir a la pantalla{detalle.total > detalle.items.length ? ` y ver los ${entero(detalle.total)}` : ''}
            </button>
          </footer>
        )}
      </aside>
    </div>
  )
}

/* ════════ SELECTOR DE PERIODO ════════ */

export function SelectorPeriodo({ dias, onCambiar }) {
  return (
    <div role="group" aria-label="Periodo" className="inline-flex rounded-lg border border-gray-200 bg-white p-0.5">
      {PERIODOS.map(p => (
        <button key={p.dias} type="button" onClick={() => onCambiar(p.dias)} aria-pressed={dias === p.dias}
          className={`text-xs px-3 py-1.5 rounded-md transition-colors ${dias === p.dias ? 'bg-primary-600 text-white font-medium' : 'text-gray-600 hover:bg-gray-100'}`}>
          {p.etiqueta}
        </button>
      ))}
    </div>
  )
}

/* ════════ PIEZAS ════════ */

function Dona({ datos, vacio = 'Sin datos.' }) {
  const total = datos.reduce((a, d) => a + d.valor, 0)
  if (total === 0) return <p className="text-sm text-gray-500">{vacio}</p>
  return (
    <div className="flex items-center gap-4">
      <div style={{ width: 120, height: 120 }} className="flex-shrink-0">
        <ResponsiveContainer>
          <PieChart>
            <Pie data={datos.filter(d => d.valor > 0)} dataKey="valor" nameKey="nombre" innerRadius={36} outerRadius={56} paddingAngle={3} stroke="none">
              {datos.filter(d => d.valor > 0).map(d => <Cell key={d.nombre} fill={d.color} />)}
            </Pie>
            <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="flex-1 space-y-1.5">
        {datos.map(d => (
          <li key={d.nombre} className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-2 text-gray-700"><span className="w-2.5 h-2.5 rounded-full" style={{ background: d.color }} />{d.nombre}</span>
            <span className="font-semibold text-gray-800">{entero(d.valor)}
              <span className="text-xs text-gray-500 font-normal ml-1">{Math.round((d.valor / total) * 100)} %</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

const ejes = { tick: { fontSize: 10, fill: '#64748b' }, tickLine: false, axisLine: false }
const tooltip = { contentStyle: { fontSize: 12, borderRadius: 8, border: '1px solid #e2e8f0' } }

/* ════════ PESTAÑAS ════════ */

function Solicitudes({ p, onAbrir }) {
  const s = p.solicitudes
  const serie = s.porDia.map(d => ({ dia: fechaCorta(d.fecha), Creadas: d.creadas, Publicadas: d.cerradas }))
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <Recuadro titulo="Solicitudes creadas" valor={s.creadas} texto={`En los últimos ${p.dias} días`} tono="primary" />
        <Recuadro titulo="Publicadas" valor={s.publicadas} texto={`${s.porcentajePublicadas} % de lo creado`} tono="emerald" />
        <Recuadro titulo="Días promedio de entrega" valor={s.diasPromedioEntrega.toLocaleString('es-CO')} texto="De solicitar a publicar" tono="sky" />
        <Recuadro titulo="Canceladas" valor={s.canceladas} texto="Clic para ver cuáles" tono="red" onClick={() => onAbrir('estado-CANCELADO')} />
      </div>

      <Seccion titulo="Qué entra y qué se entrega" subtitulo="Solicitudes creadas y publicadas, día por día">
        <div style={{ width: '100%', height: 230 }}>
          <ResponsiveContainer>
            <BarChart data={serie} margin={{ top: 4, right: 4, left: -24, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
              <XAxis dataKey="dia" {...ejes} interval="preserveStartEnd" minTickGap={18} />
              <YAxis allowDecimals={false} {...ejes} />
              <Tooltip cursor={{ fill: '#f8fafc' }} {...tooltip} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Bar dataKey="Creadas" fill="#4f46e5" radius={[3, 3, 0, 0]} />
              <Bar dataKey="Publicadas" fill="#10b981" radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Seccion>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Seccion titulo="Procesos más pedidos" subtitulo={`Últimos ${p.dias} días`}>
          <BarrasRanking datos={p.topProcesos} />
        </Seccion>
        <Seccion titulo="Clientes que más piden" subtitulo={`Últimos ${p.dias} días`}>
          <BarrasRanking datos={p.topClientes} color="bg-emerald-500" />
        </Seccion>
      </div>

      <Seccion titulo="Estado de lo que se pidió en el periodo" subtitulo="Clic en un estado para ver esas solicitudes (todas, no solo las del periodo)">
        <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6 gap-3">
          {Object.entries(s.porEstado).map(([estado, n]) => (
            <Recuadro key={estado} titulo={estado.replace('_', ' ')} valor={n} tono="gray" onClick={() => onAbrir(`estado-${estado}`)} />
          ))}
        </div>
      </Seccion>
    </div>
  )
}

function Clientes({ p, onAbrir }) {
  const c = p.clientes
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 xl:grid-cols-5 gap-4">
        <Recuadro titulo="Clientes" valor={c.total} texto={`${entero(c.activos)} activos`} tono="primary" />
        <Recuadro titulo="Nuevos en el periodo" valor={c.nuevosPeriodo} texto={`Últimos ${p.dias} días`} tono="sky" />
        <Recuadro titulo="Con solicitudes" valor={c.conActividad} texto="Pidieron algo en el periodo" tono="emerald" />
        <Recuadro titulo="Sin gestor" valor={c.sinGestor} texto={c.sinGestor > 0 ? 'Clic para ver cuáles' : 'Todos atendidos'}
          tono={c.sinGestor > 0 ? 'red' : 'gray'} onClick={c.sinGestor > 0 ? () => onAbrir('sinGestor') : undefined} />
        <Recuadro titulo="Mensajes sin leer" valor={c.mensajesSinLeer} texto="De clientes a sus gestores" tono={c.mensajesSinLeer > 0 ? 'amber' : 'gray'} />
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Seccion titulo="Cómo pagan" subtitulo="Clientes por modalidad">
          <Dona datos={[{ nombre: 'Prepago', valor: c.prepago, color: '#4f46e5' }, { nombre: 'Pospago', valor: c.pospago, color: '#f59e0b' }]} />
        </Seccion>
        <Seccion titulo="Qué tipo de cliente son" subtitulo="Empresas y personas">
          <Dona datos={[{ nombre: 'Empresas', valor: c.juridicas, color: '#0ea5e9' }, { nombre: 'Personas', valor: c.naturales, color: '#10b981' }]} />
        </Seccion>
      </div>
    </div>
  )
}

function Candidatos({ p }) {
  const c = p.candidatos
  const navigate = useNavigate()
  const etapas = [
    { nombre: 'Links generados', total: c.linksGenerados },
    { nombre: 'Links usados', total: c.linksUsados },
    { nombre: 'Hojas de vida enviadas', total: c.hojasDeVidaEnviadas },
  ]
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <Recuadro titulo="Links por usar" valor={c.linksPorUsar} texto="Vigentes, el candidato aún no entra" tono="sky" />
        <Recuadro titulo="Links bloqueados" valor={c.linksBloqueados} texto="3 documentos equivocados" tono={c.linksBloqueados > 0 ? 'red' : 'gray'} />
        <Recuadro titulo="Documentos por revisar" valor={c.documentosPorRevisar} texto="Cargados por candidatos" tono={c.documentosPorRevisar > 0 ? 'amber' : 'gray'} />
        <Recuadro titulo="Documentos devueltos" valor={c.documentosDevueltos} texto="Esperan que el candidato cargue otro" tono={c.documentosDevueltos > 0 ? 'violet' : 'gray'} />
      </div>
      <Seccion titulo="Recorrido del candidato" subtitulo={`Del link a la hoja de vida, últimos ${p.dias} días`}
        derecha={<span className="text-xs text-gray-500">{entero(c.linksRevocados)} link{c.linksRevocados === 1 ? '' : 's'} revocado{c.linksRevocados === 1 ? '' : 's'}</span>}>
        <BarrasRanking datos={etapas} color="bg-sky-500" />
        <button type="button" onClick={() => navigate('/admin/semaforo')} className="mt-4 text-xs font-medium text-primary-600 hover:text-primary-800">
          Ver solicitudes →
        </button>
      </Seccion>
      <p className="text-xs text-gray-500">Los links bloqueados solo los reabre el gestor generando uno nuevo.</p>
    </div>
  )
}

function Dinero({ p, onAbrir }) {
  const d = p.dinero
  const serie = p.solicitudes.porDia.map(x => ({ dia: fechaCorta(x.fecha), Pagado: Math.round(x.pagadoCentavos / 100) }))
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <Recuadro titulo="Cobrado en el periodo" valor={pesosDeCentavos(d.montoAprobadoCentavos)} texto={`${entero(d.pagosAprobados)} pagos aprobados`} tono="emerald" />
        <Recuadro titulo="Pagos rechazados" valor={d.pagosRechazados} texto="Rechazados, con error o anulados" tono={d.pagosRechazados > 0 ? 'amber' : 'gray'} />
        <Recuadro titulo="Pagos pendientes" valor={d.pagosPendientes} texto="Esperando respuesta del banco" tono={d.pagosPendientes > 0 ? 'amber' : 'gray'} onClick={() => onAbrir('pagos')} />
        <Recuadro titulo="Facturas pospago" valor={d.facturasEmitidas} texto={`${entero(d.facturasPagadas)} pagadas en el periodo`} tono="primary" onClick={() => onAbrir('facturas')} />
      </div>
      <Seccion titulo="Dinero cobrado por día" subtitulo="Pagos en línea aprobados">
        <div style={{ width: '100%', height: 230 }}>
          <ResponsiveContainer>
            <AreaChart data={serie} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
              <XAxis dataKey="dia" {...ejes} interval="preserveStartEnd" minTickGap={18} />
              <YAxis {...ejes} tickFormatter={(v) => (v >= 1000000 ? `${(v / 1000000).toLocaleString('es-CO')} M` : v >= 1000 ? `${Math.round(v / 1000)} mil` : v)} width={52} />
              <Tooltip formatter={(v) => [pesos(v), 'Cobrado']} {...tooltip} />
              <Area type="monotone" dataKey="Pagado" stroke="#10b981" fill="#10b98133" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </Seccion>
    </div>
  )
}

function Sistema({ p, onAbrir }) {
  const s = p.sistema
  const navigate = useNavigate()
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <Recuadro titulo="Procesos activos" valor={s.procesosActivos} texto={`${entero(s.procesosTotal)} en el catálogo`} tono="primary" onClick={() => navigate('/admin/catalogo')} />
        <Recuadro titulo="Ciudades" valor={s.ciudades} texto="Con niveles y precios" tono="sky" onClick={() => navigate('/admin/ciudades')} />
        <Recuadro titulo="Hallazgos de riesgo" valor={s.hallazgosActivos} texto="Base de la calificación" tono="violet" onClick={() => navigate('/admin/riesgo/hallazgos')} />
        <Recuadro titulo="Tipos de novedad" valor={s.tiposNovedadActivos} texto="Que puede reportar el equipo" tono="amber" onClick={() => navigate('/admin/tipos-novedad')} />
        <Recuadro titulo="Equipo activo" valor={s.internosActivos} texto={`${entero(s.internosInactivos)} inactivos`} tono="emerald" onClick={() => navigate('/admin/usuarios-internos')} />
        <Recuadro titulo="Enlaces de referencia" valor={s.enlacesExternos} texto="Entidades que consulta el equipo" tono="gray" />
        <Recuadro titulo="Trabajos terminados" valor={s.trabajosCompletadosPeriodo} texto={`Informes y cargas, últimos ${p.dias} días`} tono="emerald" />
        <Recuadro titulo="Trabajos fallidos" valor={s.trabajosFallidos} texto={s.trabajosFallidos > 0 ? 'Clic para ver cuáles' : 'Ninguno'}
          tono={s.trabajosFallidos > 0 ? 'red' : 'gray'} onClick={s.trabajosFallidos > 0 ? () => onAbrir('trabajos') : undefined} />
      </div>
    </div>
  )
}

const CONTENIDO = { solicitudes: Solicitudes, clientes: Clientes, candidatos: Candidatos, dinero: Dinero, sistema: Sistema }

export const PESTANAS_PANORAMA = [
  { id: 'solicitudes', etiqueta: 'Solicitudes' },
  { id: 'clientes',    etiqueta: 'Clientes' },
  { id: 'candidatos',  etiqueta: 'Candidatos' },
  { id: 'dinero',      etiqueta: 'Dinero' },
  { id: 'sistema',     etiqueta: 'Sistema' },
]

/** Contenido de una pestaña del panorama. Pide sus datos al cambiar de periodo y cada vez que `refresco`
 *  cambia (aviso en vivo); mientras recarga deja lo que ya se ve. */
export function PestanaPanorama({ pestana, dias, refresco, onAbrir }) {
  const [panorama, setPanorama] = useState(null)
  const [error, setError] = useState(false)

  const cargar = useCallback(() => {
    let vigente = true
    api.get('/admin/dashboard/panorama', { params: { dias } })
      .then(({ data }) => { if (vigente) { setPanorama(data); setError(false) } })
      .catch(() => { if (vigente) setError(true) })
    return () => { vigente = false }
  }, [dias])

  useEffect(() => cargar(), [cargar, refresco])

  const Contenido = CONTENIDO[pestana]
  if (error && !panorama) return <p className="text-sm text-red-600" role="alert">No se pudo cargar esta sección. Inténtalo de nuevo en un momento.</p>
  if (!panorama || panorama.dias !== dias) return (
    <div className="flex justify-center py-16">
      <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
    </div>
  )
  return <Contenido p={panorama} onAbrir={onAbrir} />
}


import { useState, useEffect, useCallback } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import asignacionService from '../../services/asignacionService'
import Toast from '../../components/Toast'
import Paginacion from '../../components/ui/Paginacion'

const POR_PAGINA = 10

// Compartida entre analista, poligrafista y visitador — el backend ya filtra por el rol/usuario
// autenticado (cada quien solo ve lo suyo), así que la misma pantalla sirve para los tres
// portales sin duplicar código; solo cambia el prefijo de ruta según en cuál se está montada.
const TITULOS = {
  analista: { titulo: 'Mis Asignaciones', subtitulo: 'Subprocesos que el gestor te asignó.' },
  poligrafista: { titulo: 'Mis Servicios', subtitulo: 'Poligrafías que el programador te asignó.' },
  visitador: { titulo: 'Mis Servicios', subtitulo: 'Visitas domiciliarias que el programador te asignó.' },
}

// Misma etiqueta que ya muestra el breadcrumb (ver ETIQUETAS_RUTA en InternalLayout) para la
// sección de esta pantalla en cada portal — se manda como state.origenSeccion al entrar al
// formulario, para que el breadcrumb diga "Servicios"/"Mis Asignaciones"/"Mis Visitas" en vez del
// genérico "Formulario" cuando se llega desde acá.
const SECCION_BREADCRUMB = {
  analista: 'Mis Asignaciones',
  poligrafista: 'Servicios',
  visitador: 'Mis Visitas',
}

const BADGE = {
  ASIGNADO:   'bg-blue-100 text-blue-700',
  COMPLETADO: 'bg-green-100 text-green-700',
  CANCELADO:  'bg-red-100 text-red-600',
}

const TABS = [
  { estado: 'ASIGNADO',   label: 'Por hacer' },
  { estado: 'COMPLETADO', label: 'Completadas' },
  { estado: 'CANCELADO',  label: 'Canceladas' },
]

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}

// Días completos transcurridos desde la fecha programada hasta ahora — negativo si todavía no
// llega (no es retraso). Solo tiene sentido para lo que sigue ASIGNADO (ya completado/cancelado no
// "se atrasa" más).
function diasDeRetraso(fechaProgramadaIso) {
  if (!fechaProgramadaIso) return 0
  const ms = Date.now() - new Date(fechaProgramadaIso).getTime()
  return Math.floor(ms / (1000 * 60 * 60 * 24))
}

function EstadoBadge({ estado }) {
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${BADGE[estado] ?? 'bg-gray-100 text-gray-600'}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {estado}
    </span>
  )
}

/* ─── Iconos de acciones de tabla ─── */
function IconoHoja() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
    </svg>
  )
}
function IconoCheck() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
    </svg>
  )
}

export default function MisAsignaciones() {
  const prefijo = useLocation().pathname.split('/')[1]
  const { titulo, subtitulo } = TITULOS[prefijo] ?? TITULOS.analista

  // Llega con ?estado= cuando se entra desde una tarjeta del dashboard (AsignacionDashboard) —
  // así el clic en "Completadas" abre directo esa pestaña, en vez de siempre "Por hacer".
  const [searchParams] = useSearchParams()
  const [estado, setEstado] = useState(() => searchParams.get('estado') ?? 'ASIGNADO')
  const [asignaciones, setAsignaciones] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)
  const [conteos, setConteos] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [completandoId, setCompletandoId] = useState(null)

  const cargar = useCallback(() => {
    setAsignaciones(null)
    Promise.all([
      // Más antiguo primero — lo que llevas esperando más tiempo por hacer queda arriba, en vez
      // de quedar enterrado al final de la página.
      asignacionService.listar({ estado, page: pagina, size: POR_PAGINA, sort: 'fechaAsignacion,asc' }),
      asignacionService.conteo(),
    ])
      .then(([r, c]) => { setAsignaciones(r.content ?? []); setTotalElementos(r.totalElements ?? 0); setConteos(c) })
      .catch(() => setError('No se pudieron cargar tus asignaciones.'))
  }, [estado, pagina])

  useEffect(() => { cargar() }, [cargar])

  const cambiarEstado = (nuevoEstado) => { setEstado(nuevoEstado); setPagina(0) }

  const completar = async (id) => {
    setCompletandoId(id)
    try {
      await asignacionService.completar(id)
      setToast({ mensaje: 'Marcada como completada', tipo: 'exito' })
      cargar()
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo completar.', tipo: 'error' })
    } finally {
      setCompletandoId(null)
    }
  }

  return (
    <div>
      <div className="mb-5">
        <h2 className="text-2xl font-bold text-gray-900">{titulo}</h2>
        <p className="text-sm text-gray-500 mt-0.5">{subtitulo}</p>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
      )}

      <div className="inline-flex gap-1 mb-4 p-1 bg-gray-100 rounded-xl">
        {TABS.map(t => (
          <button key={t.estado} onClick={() => cambiarEstado(t.estado)}
            className={`px-3.5 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              estado === t.estado
                ? 'bg-white text-primary-700 shadow-sm'
                : 'text-gray-500 hover:text-gray-700'
            }`}>
            {t.label}
            {conteos && t.estado === 'ASIGNADO' && conteos.asignados > 0 && (
              <span className={`ml-1.5 text-xs ${estado === t.estado ? 'text-primary-500' : 'text-gray-400'}`}>
                ({conteos.asignados})
              </span>
            )}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
        {asignaciones === null ? (
          <div className="flex justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : asignaciones.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">No tienes asignaciones en este estado.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50/80">
                <tr>
                  {/* Cargo y Proceso ya no son columna aparte — quedan junto al evaluado y al
                      subproceso respectivamente, para no amontonar la tabla. Fechas fusionadas en
                      una sola columna, igual que en gestor/programador → Agenda. "Retraso" solo
                      aplica en "Por hacer". */}
                  {['Evaluado', 'Subproceso', 'Estado', 'Fechas',
                    ...(estado === 'ASIGNADO' ? ['Retraso'] : []),
                    'Acciones'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {asignaciones.map(a => {
                  const retraso = a.estado === 'ASIGNADO' ? diasDeRetraso(a.fechaProgramada) : 0
                  return (
                  <tr key={a.id} className="hover:bg-gray-50/70 transition-colors">
                    <td className="px-4 py-3">
                      <p className="text-sm font-medium text-gray-900 whitespace-nowrap">{a.nombresEvaluado} {a.apellidosEvaluado}</p>
                      <p className="text-xs text-gray-400">{a.cedulaEvaluado}{a.cargo ? ` · ${a.cargo}` : ''}</p>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">
                      {a.subproceso}
                      <span className="ml-1.5 text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-md whitespace-nowrap">{a.proceso ?? '—'}</span>
                    </td>
                    <td className="px-4 py-3">
                      <EstadoBadge estado={a.estado} />
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                      <p>Asignado: {formatFecha(a.fechaAsignacion)}</p>
                      <p>Programado: {formatFecha(a.fechaProgramada)}</p>
                      {estado === 'COMPLETADO' && <p>Completado: {formatFecha(a.fechaCompletado)}</p>}
                    </td>
                    {estado === 'ASIGNADO' && (
                      <td className="px-4 py-3 whitespace-nowrap">
                        {retraso > 0 ? (
                          <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full bg-red-50 text-red-700 ring-1 ring-red-100">
                            {retraso} {retraso === 1 ? 'día' : 'días'} de retraso
                          </span>
                        ) : (
                          <span className="text-xs text-gray-300">—</span>
                        )}
                      </td>
                    )}
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1">
                        {a.requiereDocumento && (
                          <Link to={`/${prefijo}/formulario/${a.id}`} title="Formulario"
                            state={{ origenSeccion: SECCION_BREADCRUMB[prefijo] }}
                            className="p-1.5 rounded-lg text-primary-600 hover:bg-primary-50 hover:text-primary-800 transition-colors">
                            <IconoHoja />
                          </Link>
                        )}
                        {a.estado === 'ASIGNADO' && (
                          <button onClick={() => completar(a.id)} disabled={completandoId === a.id} title="Completar"
                            className="p-1.5 rounded-lg text-green-600 hover:bg-green-50 hover:text-green-800 disabled:opacity-50 transition-colors">
                            {completandoId === a.id ? (
                              <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-green-600 border-t-transparent" />
                            ) : (
                              <IconoCheck />
                            )}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {asignaciones && asignaciones.length > 0 && (
          <Paginacion pagina={pagina + 1} total={totalElementos} porPagina={POR_PAGINA}
            onChange={(p) => setPagina(p - 1)} />
        )}
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

import SemaforoEstados from '../../components/semaforo/SemaforoEstados'
import AlertasAparte from '../../components/semaforo/AlertasAparte'
import { useState, useEffect, useCallback } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import asignacionService from '../../services/asignacionService'
import { useCambios } from '../../hooks/useCambios'
import Toast from '../../components/Toast'
import Paginacion from '../../components/ui/Paginacion'
import MenuAcciones from '../../components/ui/MenuAcciones'
import { ChipNivel } from '../../components/RiesgoServicio'
import EstadoTiempo from '../../components/EstadoTiempo'
import BandejaCitas from '../../components/citas/BandejaCitas'
import ModalCitaAccion from '../../components/citas/ModalCitaAccion'

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

// Mismo criterio de "semáforo" en las 3 pantallas de este módulo (Mis Asignaciones/Agenda de
// gestor/programador): azul = en progreso, violeta = terminado pero pendiente de que gestor lo
// revise, verde = ya aprobado y cerrado de verdad, rojo = cancelado.
const BADGE = {
  EN_EJECUCION:   'bg-blue-100 text-blue-700',
  FINALIZADO: 'bg-violet-100 text-violet-700',
  PUBLICADO:  'bg-emerald-100 text-emerald-700',
  CANCELADO:  'bg-red-100 text-red-600',
}

// Filtros de «En ejecución»: lo que frena un subproceso. El valor es el que entiende el servidor
// (`indicador`), así el filtro recorre TODAS las asignaciones y no solo la página visible; el
// conteo de cada chip sale de /conteo con las mismas consultas del listado.
const FILTROS_INDICADOR = [
  { valor: 'revisar', label: 'Documentos por revisar', desc: 'El candidato ya los cargó', conteo: 'conDocumentosPorRevisar', border: 'border-sky-200', dot: 'bg-sky-400', texto: 'text-sky-600', bgActivo: 'bg-sky-50/70', ring: 'ring-sky-300' },
  { valor: 'devueltos', label: 'Con devoluciones', desc: 'Esperan que el candidato corrija', conteo: 'conDevoluciones', border: 'border-amber-200', dot: 'bg-amber-400', texto: 'text-amber-600', bgActivo: 'bg-amber-50/70', ring: 'ring-amber-300' },
  { valor: 'novedades', label: 'Con novedades abiertas', desc: 'Sin respuesta todavía', conteo: 'conNovedadesAbiertas', border: 'border-red-200', dot: 'bg-red-400', texto: 'text-red-600', bgActivo: 'bg-red-50/70', ring: 'ring-red-300' },
  { valor: 'urgentes', label: 'Entrega vencida o en 1 día hábil', desc: 'Para priorizar', conteo: 'porVencer', border: 'border-violet-200', dot: 'bg-violet-400', texto: 'text-violet-600', bgActivo: 'bg-violet-50/70', ring: 'ring-violet-300' },
]

function Etiqueta({ cls, children }) {
  return <span className={`inline-block text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${cls}`}>{children}</span>
}

// Plazo (solo "Por hacer"): cuánto falta para entregarle el informe al cliente y, aparte, si la
// hora programada del subproceso ya pasó. Un solo lugar para los dos "qué tan atrasado voy".
function Plazo({ a, conCita }) {
  // Una sola cifra de tiempo: la del SERVICIO, en días hábiles, igual que ve el cliente (SE-05). Antes aquí se contaban días calendario.
  const chip = a.tiempo ? <EstadoTiempo tiempo={a.tiempo} /> : <span className="text-xs text-gray-500">Sin fecha de entrega</span>
  const [anio, mes, dia] = (a.fechaEntregaEstimada ?? '').split('-').map(Number)
  return (
    <div className="space-y-1">
      {conCita && (
        a.fechaProgramada ? (
          <div className="pb-1">
            <p className="text-sm font-semibold text-gray-800">
              {new Date(a.fechaProgramada).toLocaleString('es-CO', { weekday: 'short', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
            </p>
            {a.lugar && <p className="text-[11px] text-gray-500 break-words">{a.lugar}</p>}
          </div>
        ) : (
          <p className="text-xs text-gray-400 pb-1">Cita sin agendar</p>
        )
      )}
      {chip}
      {a.fechaEntregaEstimada && (
        <p className="text-[11px] text-gray-400">
          Entrega {new Date(anio, mes - 1, dia).toLocaleDateString('es-CO', { day: '2-digit', month: 'short' })}
        </p>
      )}
    </div>
  )
}

// Qué frena este subproceso (solo "Por hacer"): una línea por cosa, con su punto de color — los
// mismos colores de los recuadros de arriba. Sin nada pendiente dice "Al día".
function QueFalta({ a }) {
  const items = []
  if (a.documentosPorRevisar > 0) items.push({ k: 'rev', dot: 'bg-sky-500',
    texto: `${a.documentosPorRevisar} ${a.documentosPorRevisar === 1 ? 'documento por revisar' : 'documentos por revisar'}` })
  if (a.documentosDevueltos > 0) items.push({ k: 'dev', dot: 'bg-amber-500',
    texto: `${a.documentosDevueltos} ${a.documentosDevueltos === 1 ? 'documento devuelto' : 'documentos devueltos'}` })
  if (a.novedadesAbiertas > 0) items.push({ k: 'nov', dot: 'bg-red-500',
    texto: `${a.novedadesAbiertas} ${a.novedadesAbiertas === 1 ? 'novedad abierta' : 'novedades abiertas'}` })
  if (items.length === 0) {
    return (
      <p className="inline-flex items-center gap-1.5 text-xs text-emerald-600">
        <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5} aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
        </svg>
        Al día
      </p>
    )
  }
  return (
    <ul className="space-y-1">
      {items.map(i => (
        <li key={i.k} className="flex items-center gap-1.5 text-xs text-gray-700">
          <span className={`h-2 w-2 rounded-full flex-shrink-0 ${i.dot}`} aria-hidden="true" />{i.texto}
        </li>
      ))}
    </ul>
  )
}

// Calificación de riesgo de ESTE subproceso (la misma que se ve arriba del formulario): nivel,
// puntaje y si hubo una respuesta crítica. Solo en "Por revisar" y "Aprobadas".
function CeldaRiesgo({ a }) {
  if (!a.riesgoCalifica) return <span className="text-xs text-gray-400">No califica riesgo</span>
  if (!a.riesgoNivel) return <span className="text-xs text-gray-400">Sin calificar</span>
  return (
    <div className="space-y-1">
      <div className="flex items-center gap-2">
        <ChipNivel nivel={a.riesgoNivel} />
        {a.riesgoPuntaje != null && (
          <span className="text-xs text-gray-500 whitespace-nowrap">
            {Number(a.riesgoPuntaje).toLocaleString('es-CO', { maximumFractionDigits: 1 })} pts
          </span>
        )}
      </div>
      {a.riesgoCritico && (
        <span className="inline-block text-[10px] font-semibold text-red-700 bg-red-50 ring-1 ring-red-200 px-1.5 py-0.5 rounded">
          Respuesta crítica
        </span>
      )}
    </div>
  )
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
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
function IconoMasMenos({ abierto }) {
  return (
    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d={abierto ? 'M19.5 12h-15' : 'M12 4.5v15m7.5-7.5h-15'} />
    </svg>
  )
}

/* ─── Columna "Fechas": oculta por defecto, un "+" la despliega — evita que la tabla se vea
   amontonada con fechas que la mayoría del tiempo nadie necesita mirar. Qué filas mostrar depende
   del estado REAL de esa fila (no de la pestaña activa), así siempre es correcto sin importar
   cómo se llegó a ver esa fila. */
function CeldaFechas({ asignacion: a, abierta, onToggle }) {
  if (!abierta) {
    return (
      <button onClick={onToggle}
        className="inline-flex items-center gap-1 text-[11px] font-medium text-gray-400 hover:text-primary-600 transition-colors">
        <IconoMasMenos abierto={false} /> Fechas
      </button>
    )
  }
  return (
    <div className="grid grid-cols-[3.2rem_auto] gap-x-1.5 gap-y-0.5">
      <span className="font-medium text-blue-500">Asig.</span><span className="text-gray-600">{formatFecha(a.fechaAsignacion)}</span>
      <span className="font-medium text-gray-400">Prog.</span><span className="text-gray-600">{formatFecha(a.fechaProgramada)}</span>
      {(a.estado === 'FINALIZADO' || a.estado === 'PUBLICADO') && (
        <>
          <span className="font-medium text-violet-500">Compl.</span>
          <span className="text-gray-600">{formatFecha(a.fechaCompletado)}</span>
        </>
      )}
      {a.estado === 'PUBLICADO' && (
        <>
          <span className="font-medium text-emerald-500">Publ.</span>
          <span className="text-gray-600">{formatFecha(a.fechaPublicado)}</span>
        </>
      )}
      <button onClick={onToggle}
        className="col-span-2 inline-flex items-center gap-1 text-[10px] text-gray-400 hover:text-gray-600 mt-0.5">
        <IconoMasMenos abierto /> Ocultar
      </button>
    </div>
  )
}
export default function MisAsignaciones() {
  const navigate = useNavigate()
  const prefijo = useLocation().pathname.split('/')[1]
  const { titulo, subtitulo } = TITULOS[prefijo] ?? TITULOS.analista

  // Llega con ?estado= cuando se entra desde una tarjeta del dashboard (AsignacionDashboard) —
  // así el clic en "Completadas" abre directo esa pestaña, en vez de siempre "Por hacer".
  const [searchParams] = useSearchParams()
  const [estado, setEstado] = useState(() => searchParams.get('estado') ?? 'EN_EJECUCION')
  const [indicador, setIndicador] = useState('')
  const [asignaciones, setAsignaciones] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)
  const [conteos, setConteos] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [accionCita, setAccionCita] = useState(null)    // { cita, modo } — «No se presentó» (SE-07)
  // Buscador: se consulta al servidor (recorre todas las páginas, no solo la visible) medio
  // segundo después de dejar de escribir.
  const [busqueda, setBusqueda] = useState('')
  const [buscar, setBuscar] = useState('')
  useEffect(() => {
    const t = setTimeout(() => { setBuscar(busqueda.trim()); setPagina(0) }, 400)
    return () => clearTimeout(t)
  }, [busqueda])
  const [fechasAbiertas, setFechasAbiertas] = useState(() => new Set())
  const toggleFechas = (id) => setFechasAbiertas(prev => {
    const next = new Set(prev)
    if (next.has(id)) next.delete(id); else next.add(id)
    return next
  })

  const cargar = useCallback((silencioso = false) => {
    if (!silencioso) setAsignaciones(null)
    Promise.all([
      // Más antiguo primero — lo que llevas esperando más tiempo por hacer queda arriba, en vez
      // de quedar enterrado al final de la página.
      asignacionService.listar({ estado, page: pagina, size: POR_PAGINA, sort: 'fechaAsignacion,asc', buscar: buscar || undefined,
        indicador: estado === 'EN_EJECUCION' && indicador ? indicador : undefined }),
      asignacionService.conteo(),
    ])
      .then(([r, c]) => { setAsignaciones(r.content ?? []); setTotalElementos(r.totalElements ?? 0); setConteos(c) })
      .catch(() => setError('No se pudieron cargar tus asignaciones.'))
  }, [estado, pagina, buscar, indicador])

  useEffect(() => { cargar() }, [cargar])
  useCambios(() => cargar(true))

  // Acciones de la cita (poligrafía/visita/VerifEye) del profesional (SE-07). Que estén disponibles lo decide el servidor, con la hora de Colombia.
  const iniciarInforme = async (a) => {
    try {
      await asignacionService.iniciarInforme(a.id)
      navigate(`/${prefijo}/formulario/${a.id}`, { state: { origenSeccion: SECCION_BREADCRUMB[prefijo] } })
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo iniciar el informe.', tipo: 'error' })
    }
  }
  const evaluadoNoHaLlegado = async (a) => {
    try {
      await asignacionService.evaluadoNoHaLlegado(a.id)
      setToast({ mensaje: 'Listo: se le avisó al programador que el evaluado no ha llegado.', tipo: 'exito' })
      cargar(true)
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo avisar al programador.', tipo: 'error' })
    }
  }

  const conRiesgo = estado === 'FINALIZADO' || estado === 'PUBLICADO'
  const cambiarEstado = (nuevoEstado) => { setEstado(nuevoEstado); setIndicador(''); setPagina(0) }
  const cambiarIndicador = (valor) => { setIndicador(valor); setPagina(0) }

  return (
    <div>
      <div className="mb-5">
        <h2 className="text-2xl font-bold text-gray-900">{titulo}</h2>
        <p className="text-sm text-gray-500 mt-0.5">{subtitulo}</p>
      </div>

      {/* Citas por confirmar (poligrafista y visitador): pasaron 15 minutos de la hora y no hay registro de qué pasó. */}
      {prefijo !== 'analista' && <BandejaCitas rol="profesional" prefijo={prefijo} onCambio={() => cargar(true)} />}

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
      )}

      <SemaforoEstados seleccionado={indicador ? '' : estado} onSeleccionar={(e) => cambiarEstado(e || 'EN_EJECUCION')} titulo="Estado de tus subprocesos" />
      <AlertasAparte />

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-4">
        {FILTROS_INDICADOR.map(f => {
          const activo = estado === 'EN_EJECUCION' && indicador === f.valor
          return (
            <button key={f.valor} type="button" aria-pressed={activo} onClick={() => { setEstado('EN_EJECUCION'); cambiarIndicador(activo ? '' : f.valor) }}
              className={`text-left bg-white rounded-xl border shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow ${f.border} ${
                activo ? `${f.bgActivo} ring-2 ${f.ring}` : ''
              }`}>
              <div className="flex items-center gap-2 mb-2">
                <span className={`w-2 h-2 rounded-full ${f.dot}`} />
                <span className="text-xs font-medium text-gray-500">{f.label}</span>
              </div>
              <p className={`text-3xl font-bold ${f.texto}`}>{conteos ? (conteos[f.conteo] ?? 0) : '—'}</p>
              <p className="text-xs text-gray-400 mt-1">{f.desc}</p>
            </button>
          )
        })}
      </div>

      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-100 flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[220px] max-w-md">
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 pointer-events-none" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z" />
            </svg>
            <input aria-label="Buscar por evaluado, cédula, cargo, subproceso o N° de servicio" type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)}
              placeholder="Buscar por evaluado, cédula, cargo, subproceso o N° de servicio"
              className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
          </div>
          {buscar && asignaciones !== null && (
            <p className="text-xs text-gray-500">
              {totalElementos} {totalElementos === 1 ? 'resultado' : 'resultados'} para <span className="font-semibold text-gray-700">“{buscar}”</span>
            </p>
          )}
        </div>
        {asignaciones === null ? (
          <div className="flex justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : asignaciones.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">
            {buscar ? `No hay asignaciones en este estado que coincidan con “${buscar}”.`
              : indicador ? 'Ninguna asignación tiene este filtro.' : 'No tienes asignaciones en este estado.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-stack min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50/80">
                <tr>
                  {/* Cargo y Proceso ya no son columna aparte — quedan junto al evaluado y al
                      subproceso respectivamente, para no amontonar la tabla. Fechas fusionadas en
                      una sola columna, igual que en gestor/programador → Agenda. "Retraso" solo
                      aplica en "Por hacer". */}
                  {[...(estado === 'EN_EJECUCION' ? ['Evaluado', 'Subproceso', prefijo === 'analista' ? 'Plazo' : 'Cita y plazo', 'Qué falta']
                    : conRiesgo ? ['Evaluado', 'Subproceso', 'Calificación de riesgo', 'Fechas']
                    : ['Evaluado', 'Subproceso', 'Estado', 'Fechas']),
                    '···'].map(h => (
                    <th key={h} className="px-3 py-3 text-left text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {asignaciones.map(a => {
                  return (
                  <tr key={a.id} className="hover:bg-gray-50/70 transition-colors">
                    <td className="px-3 py-3">
                      <p className="text-sm font-medium text-gray-900">{a.nombresEvaluado} {a.apellidosEvaluado}</p>
                      <p className="text-xs text-gray-400">{a.cedulaEvaluado}{a.cargo ? ` · ${a.cargo}` : ''}</p>
                    </td>
                    <td className="px-3 py-3 text-sm text-gray-700">
                      {a.subproceso}
                      <span className="ml-1.5 inline-block text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-md whitespace-nowrap">{a.proceso ?? '—'}</span>
                      {a.idServicio && <p className="text-[11px] text-gray-400 mt-0.5">Servicio #{a.idServicio}</p>}
                    </td>
                    {estado === 'EN_EJECUCION' ? (
                      <>
                        <td className="px-3 py-3"><Plazo a={a} conCita={prefijo !== 'analista'} /></td>
                        <td className="px-3 py-3"><QueFalta a={a} /></td>
                      </>
                    ) : (
                      <>
                        <td className="px-3 py-3">
                          {conRiesgo ? <CeldaRiesgo a={a} /> : <EstadoBadge estado={a.estado} />}
                        </td>
                        <td className="px-3 py-3 text-xs whitespace-nowrap">
                          <CeldaFechas asignacion={a} abierta={fechasAbiertas.has(a.id)} onToggle={() => toggleFechas(a.id)} />
                        </td>
                      </>
                    )}
                    <td className="px-3 py-3 text-center">
                      <MenuAcciones opciones={[
                        a.rolResponsable !== 'ANALISTA_INTERNO' && a.estado === 'EN_EJECUCION' && !a.iniciado && {
                          label: 'Se realizó: iniciar informe', icono: <IconoHoja />, onClick: () => iniciarInforme(a),
                        },
                        a.noHaLlegadoDisponible && {
                          label: 'El evaluado no ha llegado', icono: <IconoHoja />, onClick: () => evaluadoNoHaLlegado(a),
                        },
                        a.noSePresentoDisponible && {
                          label: 'No se presentó', icono: <IconoHoja />, destacado: 'text-red-600', onClick: () => setAccionCita({ cita: a, modo: 'no-se-presento' }),
                        },
                        a.requiereDocumento && {
                          label: 'Formulario', icono: <IconoHoja />,
                          onClick: () => navigate(`/${prefijo}/formulario/${a.id}`, { state: { origenSeccion: SECCION_BREADCRUMB[prefijo] } }),
                        },
                      ]} />
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

      {accionCita && (
        <ModalCitaAccion cita={accionCita.cita} modo={accionCita.modo} onClose={() => setAccionCita(null)}
          onHecho={() => { setAccionCita(null); setToast({ mensaje: 'Registrado: la cita pasó a Reprogramado y salió de tu agenda.', tipo: 'exito' }); cargar(true) }} />
      )}
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

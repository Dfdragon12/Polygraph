import { useState, useEffect, useCallback } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import asignacionService from '../../services/asignacionService'
import Toast from '../../components/Toast'
import Paginacion from '../../components/ui/Paginacion'
import MenuAcciones from '../../components/ui/MenuAcciones'

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
  ASIGNADO:   'bg-blue-100 text-blue-700',
  COMPLETADO: 'bg-violet-100 text-violet-700',
  PUBLICADO:  'bg-emerald-100 text-emerald-700',
  CANCELADO:  'bg-red-100 text-red-600',
}

const CAMPO_CONTEO = {
  ASIGNADO:   'asignados',
  COMPLETADO: 'completados',
  PUBLICADO:  'publicados',
  CANCELADO:  'cancelados',
}

// Mismas tarjetas de resumen que ya tienen gestor/programador en su Agenda — antes esta pantalla
// solo tenía pestañas de texto, sin esa vista rápida de cuántas hay en cada estado.
const TARJETAS_ESTADO = [
  { estado: 'ASIGNADO',   label: 'Por hacer',   desc: 'Pendientes de diligenciar', border: 'border-blue-200',    dot: 'bg-blue-400',    texto: 'text-blue-600',    bgActivo: 'bg-blue-50/70',    ring: 'ring-blue-300' },
  { estado: 'COMPLETADO', label: 'Por revisar', desc: 'Ya las diligenciaste',      border: 'border-violet-200',  dot: 'bg-violet-400',  texto: 'text-violet-600',  bgActivo: 'bg-violet-50/70',  ring: 'ring-violet-300' },
  { estado: 'PUBLICADO',  label: 'Aprobadas',   desc: 'El gestor ya las aprobó',   border: 'border-emerald-200', dot: 'bg-emerald-400', texto: 'text-emerald-600', bgActivo: 'bg-emerald-50/70', ring: 'ring-emerald-300' },
  { estado: 'CANCELADO',  label: 'Canceladas',  desc: 'No requieren acción',       border: 'border-red-200',     dot: 'bg-red-400',     texto: 'text-red-500',     bgActivo: 'bg-red-50/70',     ring: 'ring-red-300' },
]

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}

// Cuánto lleva vencido desde la fecha programada — en horas si todavía no llega a un día completo
// (antes un retraso de unas horas no mostraba nada, igual que "sin retraso"), en días una vez pasa
// las 24h. null si todavía no llega la fecha (no es retraso) o no tiene fecha. Solo tiene sentido
// para lo que sigue ASIGNADO (ya completado/cancelado no "se atrasa" más).
function textoRetraso(fechaProgramadaIso) {
  if (!fechaProgramadaIso) return null
  const ms = Date.now() - new Date(fechaProgramadaIso).getTime()
  if (ms <= 0) return null
  const horas = ms / (1000 * 60 * 60)
  if (horas < 24) {
    const h = Math.max(1, Math.floor(horas))
    return `${h} ${h === 1 ? 'hora' : 'horas'} de retraso`
  }
  const dias = Math.floor(horas / 24)
  return `${dias} ${dias === 1 ? 'día' : 'días'} de retraso`
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
      {(a.estado === 'COMPLETADO' || a.estado === 'PUBLICADO') && (
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
  const [estado, setEstado] = useState(() => searchParams.get('estado') ?? 'ASIGNADO')
  const [asignaciones, setAsignaciones] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)
  const [conteos, setConteos] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [fechasAbiertas, setFechasAbiertas] = useState(() => new Set())
  const toggleFechas = (id) => setFechasAbiertas(prev => {
    const next = new Set(prev)
    if (next.has(id)) next.delete(id); else next.add(id)
    return next
  })

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

  return (
    <div>
      <div className="mb-5">
        <h2 className="text-2xl font-bold text-gray-900">{titulo}</h2>
        <p className="text-sm text-gray-500 mt-0.5">{subtitulo}</p>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
      )}

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-4">
        {TARJETAS_ESTADO.map(t => {
          const activo = estado === t.estado
          return (
            <button key={t.estado} type="button" onClick={() => cambiarEstado(t.estado)}
              className={`text-left bg-white rounded-xl border shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow ${t.border} ${
                activo ? `${t.bgActivo} ring-2 ${t.ring}` : ''
              }`}>
              <div className="flex items-center gap-2 mb-2">
                <span className={`w-2 h-2 rounded-full ${t.dot}`} />
                <span className="text-xs font-medium text-gray-500">{t.label}</span>
              </div>
              <p className={`text-3xl font-bold ${t.texto}`}>{conteos ? (conteos[CAMPO_CONTEO[t.estado]] ?? 0) : '—'}</p>
              <p className="text-xs text-gray-400 mt-1">{t.desc}</p>
            </button>
          )
        })}
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
                    '···'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {asignaciones.map(a => {
                  const retraso = a.estado === 'ASIGNADO' ? textoRetraso(a.fechaProgramada) : null
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
                    <td className="px-4 py-3 text-xs whitespace-nowrap">
                      <CeldaFechas asignacion={a} abierta={fechasAbiertas.has(a.id)} onToggle={() => toggleFechas(a.id)} />
                    </td>
                    {estado === 'ASIGNADO' && (
                      <td className="px-4 py-3 whitespace-nowrap">
                        {retraso ? (
                          <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full bg-red-50 text-red-700 ring-1 ring-red-100">
                            {retraso}
                          </span>
                        ) : (
                          <span className="text-xs text-gray-300">—</span>
                        )}
                      </td>
                    )}
                    <td className="px-4 py-3 text-center">
                      <MenuAcciones opciones={[
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

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

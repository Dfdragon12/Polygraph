import { useState, useEffect, useCallback, useMemo } from 'react'
import { Link } from 'react-router-dom'
import asignacionService from '../../services/asignacionService'
import formulariosService from '../../services/formulariosService'
import Toast from '../../components/Toast'
import Paginacion from '../../components/ui/Paginacion'
import ModalAsignarSubproceso from '../../components/ModalAsignarSubproceso'
import CronogramaDia from '../../components/CronogramaDia'
import { toLocalDateTimeString, toLocalDateString, hoyLocalDateString, rangoDelDia } from '../../utils/fechas'

/* ─── Qué tan lleno está el formulario de un subproceso — para que programador vea el avance
   sin abrir cada uno. ─── */
function BadgeProgresoFormulario({ idServicioSubproceso }) {
  const [progreso, setProgreso] = useState(null)

  useEffect(() => {
    formulariosService.progreso(idServicioSubproceso).then(setProgreso).catch(() => {})
  }, [idServicioSubproceso])

  if (!progreso) return null
  const { camposLlenos, camposTotal } = progreso
  const completo = camposTotal > 0 && camposLlenos >= camposTotal
  const color = completo
    ? 'bg-green-100 text-green-700'
    : camposLlenos > 0
      ? 'bg-amber-100 text-amber-700'
      : 'bg-gray-100 text-gray-500'

  return (
    <span title="Campos llenos del formulario" className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full whitespace-nowrap ${color}`}>
      📄 {camposLlenos}/{camposTotal}
    </span>
  )
}

/* ─── Iconos de acciones de tabla ─── */
function IconoAsignar() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M17.5 10.5l1.5 1.5 3-3" />
    </svg>
  )
}
function IconoLapiz() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
    </svg>
  )
}
function IconoTelefono() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 6.75c0 8.284 6.716 15 15 15h1.5a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106a2.25 2.25 0 00-2.239.649l-.973.973a1.125 1.125 0 01-1.191.26 12.35 12.35 0 01-7.135-7.135 1.125 1.125 0 01.26-1.191l.973-.973a2.25 2.25 0 00.649-2.239L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z" />
    </svg>
  )
}
// Mismo ícono que MisAsignaciones.jsx (vista de analista/poligrafista/visitador) — para que el
// acceso al formulario se vea y funcione igual en todos los portales.
function IconoHoja() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
    </svg>
  )
}

const CAMPO_CONTEO = {
  PENDIENTE:  'pendientes',
  ASIGNADO:   'asignados',
  COMPLETADO: 'completados',
  CANCELADO:  'cancelados',
}

const TARJETAS_ESTADO = [
  { estado: 'PENDIENTE',  label: 'Pendientes',  desc: 'Por asignar',      border: 'border-amber-200', dot: 'bg-amber-400', texto: 'text-amber-600', bgActivo: 'bg-amber-50/70', ring: 'ring-amber-300' },
  { estado: 'ASIGNADO',   label: 'Asignados',   desc: 'Ya tienen fecha',  border: 'border-blue-200',  dot: 'bg-blue-400',  texto: 'text-blue-600',  bgActivo: 'bg-blue-50/70',  ring: 'ring-blue-300' },
  { estado: 'COMPLETADO', label: 'Completados', desc: 'Ya se atendieron', border: 'border-green-200', dot: 'bg-green-400', texto: 'text-green-600', bgActivo: 'bg-green-50/70', ring: 'ring-green-300' },
  { estado: 'CANCELADO',  label: 'Cancelados',  desc: 'No activos',      border: 'border-red-200',   dot: 'bg-red-400',   texto: 'text-red-500',   bgActivo: 'bg-red-50/70',   ring: 'ring-red-300' },
]

const BADGE_ESTADO = {
  PENDIENTE:  'bg-amber-100 text-amber-700',
  ASIGNADO:   'bg-blue-100 text-blue-700',
  COMPLETADO: 'bg-emerald-100 text-emerald-700',
  CANCELADO:  'bg-red-100 text-red-600',
}

const ACENTO_ESTADO = {
  PENDIENTE:  'border-l-amber-400',
  ASIGNADO:   'border-l-blue-500',
  COMPLETADO: 'border-l-emerald-500',
  CANCELADO:  'border-l-red-400',
}

function formatFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function formatHora(iso) {
  return new Date(iso).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

// Hora límite de ESTE subproceso puntual — no la fecha de entrega del servicio completo, sino
// "programado + cuánto se demora este tipo de subproceso". Ej: programado 27/09 8:30am, dura 15
// minutos → debe estar listo a las 27/09 8:45am.
function formatHoraLimite(fechaProgramadaIso, duracionMinutos) {
  if (!fechaProgramadaIso || !duracionMinutos) return '—'
  const limite = new Date(fechaProgramadaIso)
  limite.setMinutes(limite.getMinutes() + duracionMinutos)
  return limite.toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}

// Mismo cálculo que en MisAsignaciones.jsx (vista de analista/poligrafista/visitador) — días
// completos vencidos desde la fecha programada, solo tiene sentido mientras sigue ASIGNADO.
function diasDeRetraso(fechaProgramadaIso) {
  if (!fechaProgramadaIso) return 0
  const ms = Date.now() - new Date(fechaProgramadaIso).getTime()
  return Math.floor(ms / (1000 * 60 * 60 * 24))
}

function inicioDeSemana(fecha) {
  const d = new Date(fecha)
  const dia = d.getDay() // 0 = domingo
  const offset = dia === 0 ? -6 : 1 - dia // lunes como inicio
  d.setDate(d.getDate() + offset)
  d.setHours(0, 0, 0, 0)
  return d
}

function formatFechaCorta(d) {
  return d.toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'short' })
}

function mismoDia(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function iniciales(nombre) {
  const partes = (nombre ?? '').trim().split(/\s+/).filter(Boolean)
  const a = partes[0]?.[0] ?? ''
  const b = partes[1]?.[0] ?? ''
  return (a + b).toUpperCase() || '—'
}

function formatDiaLargo(diaStr) {
  const [y, m, d] = diaStr.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' })
}

const CLASE_BOTON_NAV = 'w-8 h-8 flex items-center justify-center rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 hover:text-gray-700 hover:border-gray-300 transition-colors flex-shrink-0'
const CLASE_BOTON_HOY = 'px-3 py-1.5 text-xs font-semibold rounded-lg border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors flex-shrink-0'

function IconoFlecha({ direccion }) {
  return (
    <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
      {direccion === 'izq'
        ? <path fillRule="evenodd" d="M12.79 5.23a.75.75 0 010 1.06L9.06 10l3.73 3.71a.75.75 0 11-1.06 1.06l-4.25-4.25a.75.75 0 010-1.06l4.25-4.25a.75.75 0 011.06 0z" clipRule="evenodd" />
        : <path fillRule="evenodd" d="M7.21 14.77a.75.75 0 010-1.06L10.94 10 7.21 6.29a.75.75 0 111.06-1.06l4.25 4.25a.75.75 0 010 1.06l-4.25 4.25a.75.75 0 01-1.06 0z" clipRule="evenodd" />}
    </svg>
  )
}

// Tamaño de bloque fijo para "Disponibilidad" — no depende de un subproceso puntual, así que no
// tiene sentido pedirle al usuario que lo escriba. Se usa 15 min (el estimado más chico del
// catálogo, ej. "Antecedentes") para que ninguna franja ocupada quede oculta dentro de un bloque
// más grande — con 60 min, 4 citas de 15 min pegadas en la misma hora se veían como "1 ocupada".
const DURACION_DISPONIBILIDAD_MIN = 15

/* Agenda de Programador: un solo módulo con tres pestañas — "Servicios" (la cola de trabajo por
   programar, con tarjetas de estado y filtros), "Cronograma" (semana/día) y "Disponibilidad"
   (el día completo de UN empleado puntual, libre/ocupado, sin depender de un subproceso
   pendiente). Antes Servicios y Cronograma eran páginas separadas; se combinaron para no tener
   que saltar entre pantallas para lo mismo. */
export default function ProgramadorAgenda() {
  const [seccion, setSeccion] = useState('servicios') // 'servicios' | 'cronograma' | 'disponibilidad'
  const [seleccionado, setSeleccionado] = useState(null)
  const [toast, setToast]               = useState(null)

  // ══════════════════════ Pestaña: Servicios ══════════════════════
  const [listaServicios, setListaServicios]         = useState([])
  const [cargandoServicios, setCargandoServicios]   = useState(true)
  const [errorServicios, setErrorServicios]         = useState(null)
  const [estadoServicios, setEstadoServicios]       = useState('PENDIENTE')
  const [busquedaServicios, setBusquedaServicios]   = useState('')
  const [filtroSubprocesoServicios, setFiltroSubprocesoServicios] = useState('')
  const [filtroEmpleadoServicios, setFiltroEmpleadoServicios]     = useState('')
  const [opcionesSubprocesoServicios, setOpcionesSubprocesoServicios] = useState([])
  const [opcionesEmpleadoServicios, setOpcionesEmpleadoServicios] = useState(new Map())
  const [paginaServicios, setPaginaServicios]         = useState(0)
  const [totalElementosServicios, setTotalElementosServicios] = useState(0)
  const [conteosServicios, setConteosServicios]       = useState(null)
  const POR_PAGINA = 10

  useEffect(() => {
    asignacionService.subprocesos().then(setOpcionesSubprocesoServicios).catch(() => {})
  }, [])

  const cargarServicios = useCallback(async (estadoActual, subprocesoActual, empleadoActual, page = 0) => {
    setCargandoServicios(true); setErrorServicios(null)
    try {
      const [rLista, rConteo] = await Promise.all([
        asignacionService.listar({
          estado: estadoActual || undefined,
          idTipoProgreso: subprocesoActual || undefined,
          idUsuarioAsignado: empleadoActual || undefined,
          page, size: POR_PAGINA,
        }),
        asignacionService.conteo(),
      ])
      const lista = rLista.content ?? []
      setListaServicios(lista)
      setTotalElementosServicios(rLista.totalElements ?? 0)
      setConteosServicios(rConteo)
      // El catálogo de empleados asignables no está expuesto a Programador — se va acumulando
      // con quienes van apareciendo en los resultados, para que el filtro no pierda opciones al paginar.
      setOpcionesEmpleadoServicios(prev => {
        const nuevo = new Map(prev)
        lista.forEach(a => { if (a.idUsuarioAsignado != null) nuevo.set(a.idUsuarioAsignado, a.nombreAsignado) })
        return nuevo
      })
    } catch { setErrorServicios('Error al cargar los servicios.') }
    finally { setCargandoServicios(false) }
  }, [])

  useEffect(() => {
    setPaginaServicios(0)
    cargarServicios(estadoServicios, filtroSubprocesoServicios, filtroEmpleadoServicios, 0)
  }, [estadoServicios, filtroSubprocesoServicios, filtroEmpleadoServicios, cargarServicios])

  const alternarTarjeta = (e) => setEstadoServicios(prev => prev === e ? '' : e)

  const serviciosFiltrados = busquedaServicios.trim()
    ? listaServicios.filter(a =>
        `${a.nombresEvaluado ?? ''} ${a.apellidosEvaluado ?? ''}`.toLowerCase().includes(busquedaServicios.toLowerCase()) ||
        a.cedulaEvaluado?.includes(busquedaServicios)
      )
    : listaServicios

  // ══════════════════════ Pestaña: Cronograma (semana / día) ══════════════════════
  const [vista, setVista] = useState('semana') // 'semana' | 'dia'

  const [inicioSemana, setInicioSemana] = useState(() => inicioDeSemana(new Date()))
  const [asignacionesSemana, setAsignacionesSemana] = useState([])
  const [cargandoSemana, setCargandoSemana]         = useState(true)
  const [errorSemana, setErrorSemana]               = useState(null)
  const [filtroSubprocesoSemana, setFiltroSubprocesoSemana] = useState('')
  const [filtroEmpleadoSemana, setFiltroEmpleadoSemana]     = useState('')
  // Cada tarjeta de día arranca colapsada (solo se ve la cantidad) — se despliega con un click
  // para no amontonar de entrada todos los servicios de todos los días a la vez.
  const [diasExpandidosSemana, setDiasExpandidosSemana] = useState(() => new Set())
  const alternarDiaSemana = (claveDia) => {
    setDiasExpandidosSemana(prev => {
      const next = new Set(prev)
      if (next.has(claveDia)) next.delete(claveDia); else next.add(claveDia)
      return next
    })
  }

  const [diaSeleccionado, setDiaSeleccionado] = useState(() => hoyLocalDateString())
  const [asignacionesDia, setAsignacionesDia] = useState([])
  const [cargandoDia, setCargandoDia]         = useState(true)
  const [errorDia, setErrorDia]               = useState(null)

  const dias = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(inicioSemana)
    d.setDate(d.getDate() + i)
    return d
  })

  const cargarSemana = useCallback(async (desdeFecha) => {
    setCargandoSemana(true); setErrorSemana(null)
    try {
      const hasta = new Date(desdeFecha)
      hasta.setDate(hasta.getDate() + 7)
      const data = await asignacionService.calendario(toLocalDateTimeString(desdeFecha), toLocalDateTimeString(hasta))
      setAsignacionesSemana((data ?? []).filter(a => a.fechaProgramada).sort((a, b) => new Date(a.fechaProgramada) - new Date(b.fechaProgramada)))
    } catch { setErrorSemana('Error al cargar la agenda.') }
    finally { setCargandoSemana(false) }
  }, [])

  useEffect(() => { cargarSemana(inicioSemana) }, [inicioSemana, cargarSemana])
  useEffect(() => { setDiasExpandidosSemana(new Set()) }, [inicioSemana])

  const cargarDia = useCallback(async (dia) => {
    setCargandoDia(true); setErrorDia(null)
    try {
      const { desde, hasta } = rangoDelDia(dia)
      const data = await asignacionService.calendario(desde, hasta)
      setAsignacionesDia(data ?? [])
    } catch { setErrorDia('Error al cargar el cronograma del día.') }
    finally { setCargandoDia(false) }
  }, [])

  useEffect(() => { if (seccion === 'cronograma' && vista === 'dia') cargarDia(diaSeleccionado) }, [seccion, vista, diaSeleccionado, cargarDia])

  const cambiarSemana = (delta) => {
    const nueva = new Date(inicioSemana)
    nueva.setDate(nueva.getDate() + delta * 7)
    setInicioSemana(nueva)
  }

  const cambiarDia = (delta) => {
    const [y, m, d] = diaSeleccionado.split('-').map(Number)
    setDiaSeleccionado(toLocalDateString(new Date(y, m - 1, d + delta)))
  }

  const hoy = new Date()
  const finSemana = dias[6]

  const opcionesSubprocesoSemana = useMemo(() => {
    const map = new Map()
    asignacionesSemana.forEach(a => { if (a.idTipoProgreso != null) map.set(a.idTipoProgreso, a.subproceso) })
    return [...map.entries()].sort((a, b) => a[1].localeCompare(b[1]))
  }, [asignacionesSemana])

  const opcionesEmpleadoSemana = useMemo(() => {
    const map = new Map()
    asignacionesSemana.forEach(a => { if (a.idUsuarioAsignado != null) map.set(a.idUsuarioAsignado, a.nombreAsignado) })
    return [...map.entries()].sort((a, b) => (a[1] ?? '').localeCompare(b[1] ?? ''))
  }, [asignacionesSemana])

  const semanaFiltrada = asignacionesSemana.filter(a =>
    (!filtroSubprocesoSemana || String(a.idTipoProgreso) === filtroSubprocesoSemana) &&
    (!filtroEmpleadoSemana || String(a.idUsuarioAsignado) === filtroEmpleadoSemana)
  )

  // ══════════════════════ Pestaña: Disponibilidad (un empleado, un día) ══════════════════════
  // Igual estilo que el selector de horas del modal de asignar (franjas libres/ocupadas), pero
  // sin depender de un subproceso puntual — solo para consultar de un vistazo la agenda de alguien.
  const [empleadoDisponibilidad, setEmpleadoDisponibilidad] = useState('')
  const [diaDisponibilidad, setDiaDisponibilidad] = useState(() => hoyLocalDateString())
  const [franjasDisponibilidad, setFranjasDisponibilidad] = useState([])
  const [cargandoDisponibilidad, setCargandoDisponibilidad] = useState(false)
  const [errorDisponibilidad, setErrorDisponibilidad]       = useState(null)

  const cargarDisponibilidad = useCallback(async (idEmpleado, dia) => {
    if (!idEmpleado) { setFranjasDisponibilidad([]); return }
    setCargandoDisponibilidad(true); setErrorDisponibilidad(null)
    try {
      const data = await asignacionService.franjasDisponibles(idEmpleado, dia, undefined, DURACION_DISPONIBILIDAD_MIN, undefined)
      setFranjasDisponibilidad(data ?? [])
    } catch { setErrorDisponibilidad('Error al cargar la disponibilidad.') }
    finally { setCargandoDisponibilidad(false) }
  }, [])

  useEffect(() => {
    if (seccion === 'disponibilidad') cargarDisponibilidad(empleadoDisponibilidad, diaDisponibilidad)
  }, [seccion, empleadoDisponibilidad, diaDisponibilidad, cargarDisponibilidad])

  const cambiarDiaDisponibilidad = (delta) => {
    const [y, m, d] = diaDisponibilidad.split('-').map(Number)
    setDiaDisponibilidad(toLocalDateString(new Date(y, m - 1, d + delta)))
  }

  // El catálogo de empleados no está expuesto a Programador — se combinan las opciones vistas
  // esta semana y en Servicios para tener la lista más completa posible.
  const opcionesEmpleadoDisponibilidad = useMemo(() => {
    const map = new Map([...opcionesEmpleadoServicios, ...opcionesEmpleadoSemana])
    return [...map.entries()].sort((a, b) => (a[1] ?? '').localeCompare(b[1] ?? ''))
  }, [opcionesEmpleadoServicios, opcionesEmpleadoSemana])

  // ══════════════════════ Compartido: modal ══════════════════════
  const onGuardado = () => {
    setSeleccionado(null)
    cargarServicios(estadoServicios, filtroSubprocesoServicios, filtroEmpleadoServicios, paginaServicios)
    cargarSemana(inicioSemana)
    if (vista === 'dia') cargarDia(diaSeleccionado)
    if (seccion === 'disponibilidad') cargarDisponibilidad(empleadoDisponibilidad, diaDisponibilidad)
  }

  return (
    <div>
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm px-5 py-4 mb-5">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">Agenda</h2>
            <p className="text-sm text-gray-500 mt-0.5">
              {seccion === 'servicios'
                ? `${totalElementosServicios} servicio${totalElementosServicios !== 1 ? 's' : ''} de poligrafía y visita domiciliaria`
                : seccion === 'cronograma'
                  ? (vista === 'semana'
                      ? `Poligrafías y visitas programadas — ${inicioSemana.toLocaleDateString('es-CO', { day: 'numeric', month: 'short' })} al ${finSemana.toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })}`
                      : 'Cronograma del día por empleado')
                  : 'Libre/ocupado de un empleado puntual, todo el día'}
            </p>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
              <button onClick={() => setSeccion('servicios')}
                className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${seccion === 'servicios' ? 'bg-white shadow-sm text-primary-700' : 'text-gray-500 hover:text-gray-700'}`}>
                Servicios
              </button>
              <button onClick={() => setSeccion('cronograma')}
                className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${seccion === 'cronograma' ? 'bg-white shadow-sm text-primary-700' : 'text-gray-500 hover:text-gray-700'}`}>
                Cronograma
              </button>
              <button onClick={() => setSeccion('disponibilidad')}
                className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${seccion === 'disponibilidad' ? 'bg-white shadow-sm text-primary-700' : 'text-gray-500 hover:text-gray-700'}`}>
                Disponibilidad
              </button>
            </div>

            {seccion === 'cronograma' && (
              <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
                <button onClick={() => setVista('semana')}
                  className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${vista === 'semana' ? 'bg-white shadow-sm text-primary-700' : 'text-gray-500 hover:text-gray-700'}`}>
                  Semana
                </button>
                <button onClick={() => setVista('dia')}
                  className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${vista === 'dia' ? 'bg-white shadow-sm text-primary-700' : 'text-gray-500 hover:text-gray-700'}`}>
                  Día
                </button>
              </div>
            )}

            {seccion === 'cronograma' && (vista === 'semana' ? (
              <div className="flex items-center gap-1.5">
                <button onClick={() => cambiarSemana(-1)} title="Semana anterior" className={CLASE_BOTON_NAV}>
                  <IconoFlecha direccion="izq" />
                </button>
                <button onClick={() => setInicioSemana(inicioDeSemana(new Date()))} className={CLASE_BOTON_HOY}>
                  Hoy
                </button>
                <button onClick={() => cambiarSemana(1)} title="Semana siguiente" className={CLASE_BOTON_NAV}>
                  <IconoFlecha direccion="der" />
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-1.5">
                <button onClick={() => cambiarDia(-1)} title="Día anterior" className={CLASE_BOTON_NAV}>
                  <IconoFlecha direccion="izq" />
                </button>
                <input type="date" value={diaSeleccionado} onChange={e => setDiaSeleccionado(e.target.value)}
                  className="px-3 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-500" />
                <button onClick={() => setDiaSeleccionado(hoyLocalDateString())} className={CLASE_BOTON_HOY}>
                  Hoy
                </button>
                <button onClick={() => cambiarDia(1)} title="Día siguiente" className={CLASE_BOTON_NAV}>
                  <IconoFlecha direccion="der" />
                </button>
              </div>
            ))}

            {seccion === 'disponibilidad' && (
              <div className="flex items-center gap-1.5">
                <button onClick={() => cambiarDiaDisponibilidad(-1)} title="Día anterior" className={CLASE_BOTON_NAV}>
                  <IconoFlecha direccion="izq" />
                </button>
                <input type="date" value={diaDisponibilidad} onChange={e => setDiaDisponibilidad(e.target.value)}
                  className="px-3 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-500" />
                <button onClick={() => setDiaDisponibilidad(hoyLocalDateString())} className={CLASE_BOTON_HOY}>
                  Hoy
                </button>
                <button onClick={() => cambiarDiaDisponibilidad(1)} title="Día siguiente" className={CLASE_BOTON_NAV}>
                  <IconoFlecha direccion="der" />
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ══════════════════════ Contenido: Servicios ══════════════════════ */}
      {seccion === 'servicios' && (
        <>
          {errorServicios && (
            <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
              {errorServicios} <button onClick={() => setErrorServicios(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
            </div>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-4">
            {TARJETAS_ESTADO.map(t => {
              const activo = estadoServicios === t.estado
              return (
                <button key={t.estado} type="button" onClick={() => alternarTarjeta(t.estado)}
                  className={`text-left bg-white rounded-xl border shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow ${t.border} ${
                    activo ? `${t.bgActivo} ring-2 ${t.ring}` : ''
                  }`}>
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`w-2 h-2 rounded-full ${t.dot}`} />
                    <span className="text-xs font-medium text-gray-500">{t.label}</span>
                  </div>
                  <p className={`text-3xl font-bold ${t.texto}`}>{conteosServicios ? conteosServicios[CAMPO_CONTEO[t.estado]] : '—'}</p>
                  <p className="text-xs text-gray-400 mt-1">{t.desc}</p>
                </button>
              )
            })}
          </div>

          <div className="flex items-center gap-2 flex-wrap mb-4">
            <input type="search" value={busquedaServicios} onChange={e => setBusquedaServicios(e.target.value)}
              placeholder="Buscar por nombre o cédula del evaluado…"
              className="flex-1 min-w-[220px] max-w-sm border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            <select value={filtroSubprocesoServicios} onChange={e => setFiltroSubprocesoServicios(e.target.value)}
              className="border border-gray-200 rounded-lg px-3 py-2 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
              <option value="">Todos los subprocesos</option>
              {opcionesSubprocesoServicios.map(o => <option key={o.idTipoProgreso} value={o.idTipoProgreso}>{o.nombreProgreso}</option>)}
            </select>
            <select value={filtroEmpleadoServicios} onChange={e => setFiltroEmpleadoServicios(e.target.value)}
              className="border border-gray-200 rounded-lg px-3 py-2 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
              <option value="">Todos los empleados</option>
              {[...opcionesEmpleadoServicios.entries()].sort((a, b) => (a[1] ?? '').localeCompare(b[1] ?? '')).map(([id, nombre]) => (
                <option key={id} value={id}>{nombre}</option>
              ))}
            </select>
            {(filtroSubprocesoServicios || filtroEmpleadoServicios) && (
              <button onClick={() => { setFiltroSubprocesoServicios(''); setFiltroEmpleadoServicios('') }}
                className="text-xs text-gray-400 hover:text-gray-600 px-1">
                Limpiar filtros
              </button>
            )}
          </div>

          <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
            {cargandoServicios ? (
              <div className="flex justify-center py-16">
                <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
              </div>
            ) : serviciosFiltrados.length === 0 ? (
              <div className="text-center py-16 text-gray-400 text-sm">No hay servicios para este filtro.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full divide-y divide-gray-100 text-sm">
                  <thead className="bg-gray-50/80">
                    <tr>
                      {/* Mismo criterio de MisAsignaciones.jsx (analista/poligrafista/visitador):
                          fechas juntas en una sola columna, "Formulario" pasa a ser un ícono
                          dentro de Acciones en vez de columna aparte, y "Retraso" solo se ve en
                          la pestaña "Asignados" — menos columnas, menos amontonado. */}
                      {['Evaluado', 'Subproceso', 'Fechas', 'Asignado a',
                        ...(estadoServicios === 'ASIGNADO' ? ['Retraso'] : []),
                        'Estado', 'Acciones'].map(h => (
                        <th key={h} className="px-4 py-3 text-left text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {serviciosFiltrados.map(a => {
                      const retraso = a.estado === 'ASIGNADO' ? diasDeRetraso(a.fechaProgramada) : 0
                      return (
                      <tr key={a.id} className="hover:bg-gray-50/70 transition-colors">
                        <td className="px-4 py-3">
                          <p className="font-medium text-gray-900 whitespace-nowrap">{a.nombresEvaluado} {a.apellidosEvaluado ?? ''}</p>
                          <p className="text-xs text-gray-400">{a.cedulaEvaluado ?? '—'} · Servicio #{a.idServicio}</p>
                        </td>
                        <td className="px-4 py-3 text-gray-700 whitespace-nowrap">
                          {a.subproceso}
                          <span className="ml-1.5 text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-md whitespace-nowrap">{a.nombreCiudad ?? '—'}</span>
                        </td>
                        <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                          <p>Asignado: {formatFechaHora(a.fechaAsignacion)}</p>
                          <p>Programado: {formatFechaHora(a.fechaProgramada)}</p>
                          <p>Debe estar listo: {formatHoraLimite(a.fechaProgramada, a.duracionMinutos)}</p>
                          {estadoServicios === 'COMPLETADO' && <p>Completado: {formatFechaHora(a.fechaCompletado)}</p>}
                        </td>
                        <td className="px-4 py-3 text-gray-500 whitespace-nowrap">{a.nombreAsignado ?? 'Sin asignar'}</td>
                        {estadoServicios === 'ASIGNADO' && (
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
                          <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap ${BADGE_ESTADO[a.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                            <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
                            {a.estado}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-1.5">
                            {a.requiereDocumento && (
                              <>
                                <BadgeProgresoFormulario idServicioSubproceso={a.id} />
                                <Link to={`/programador/formulario/${a.id}`} title="Formulario"
                                  state={{ origenSeccion: 'Agenda' }}
                                  className="p-1.5 rounded-lg text-primary-600 hover:bg-primary-50 hover:text-primary-800 transition-colors">
                                  <IconoHoja />
                                </Link>
                              </>
                            )}
                            <button onClick={() => setSeleccionado(a)}
                              title={
                                a.estado !== 'PENDIENTE' ? 'Ver / editar'
                                  : a.estadoConfirmacion === 'SIN_LLAMAR' ? 'Llamar para confirmar'
                                    : 'Asignar'
                              }
                              className="p-1.5 rounded-lg text-primary-600 hover:bg-primary-50 hover:text-primary-800 transition-colors">
                              {a.estado !== 'PENDIENTE' ? <IconoLapiz />
                                : a.estadoConfirmacion === 'SIN_LLAMAR' ? <IconoTelefono />
                                  : <IconoAsignar />}
                            </button>
                          </div>
                        </td>
                      </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}

            <Paginacion
              pagina={paginaServicios + 1}
              total={totalElementosServicios}
              porPagina={POR_PAGINA}
              onChange={(p) => {
                const nueva = p - 1
                setPaginaServicios(nueva)
                cargarServicios(estadoServicios, filtroSubprocesoServicios, filtroEmpleadoServicios, nueva)
              }}
            />
          </div>
        </>
      )}

      {/* ══════════════════════ Contenido: Cronograma ══════════════════════ */}
      {seccion === 'cronograma' && (
        <>
          {vista === 'semana' && (
            <div className="flex items-center gap-2 flex-wrap mb-4">
              <select value={filtroSubprocesoSemana} onChange={e => setFiltroSubprocesoSemana(e.target.value)}
                className="border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
                <option value="">Todos los subprocesos</option>
                {opcionesSubprocesoSemana.map(([id, nombre]) => <option key={id} value={id}>{nombre}</option>)}
              </select>
              <select value={filtroEmpleadoSemana} onChange={e => setFiltroEmpleadoSemana(e.target.value)}
                className="border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
                <option value="">Todos los empleados</option>
                {opcionesEmpleadoSemana.map(([id, nombre]) => <option key={id} value={id}>{nombre}</option>)}
              </select>
              {(filtroSubprocesoSemana || filtroEmpleadoSemana) && (
                <button onClick={() => { setFiltroSubprocesoSemana(''); setFiltroEmpleadoSemana('') }}
                  className="text-xs text-gray-400 hover:text-gray-600 px-1">
                  Limpiar filtros
                </button>
              )}
            </div>
          )}

          {vista === 'semana' && errorSemana && (
            <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
              {errorSemana} <button onClick={() => setErrorSemana(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
            </div>
          )}

          {vista === 'semana' ? (
            cargandoSemana ? (
              <div className="flex justify-center py-16">
                <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
              </div>
            ) : (
              <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
                {dias.map(dia => {
                  const claveDia = dia.toISOString()
                  const delDia = semanaFiltrada.filter(a => mismoDia(new Date(a.fechaProgramada), dia))
                  const esHoy = mismoDia(dia, hoy)
                  const expandido = diasExpandidosSemana.has(claveDia)
                  return (
                    <div key={claveDia} className={`bg-white rounded-xl border shadow-sm overflow-hidden transition-shadow hover:shadow-md ${esHoy ? 'border-primary-300 ring-1 ring-primary-100' : 'border-gray-200'}`}>
                      <button type="button" onClick={() => alternarDiaSemana(claveDia)} disabled={delDia.length === 0}
                        className={`w-full flex items-center justify-between px-4 py-3 border-b transition-colors ${esHoy ? 'bg-primary-50/70 border-primary-100' : 'bg-gray-50/80 border-gray-100'} ${delDia.length > 0 ? 'hover:bg-gray-100/70' : 'cursor-default'}`}>
                        <p className={`text-xs font-semibold capitalize ${esHoy ? 'text-primary-700' : 'text-gray-600'}`}>
                          {formatFechaCorta(dia)}
                        </p>
                        <div className="flex items-center gap-1.5">
                          {esHoy && <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-primary-600 text-white">Hoy</span>}
                          {delDia.length > 0 && (
                            <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-gray-200 text-gray-600">{delDia.length}</span>
                          )}
                          {delDia.length > 0 && (
                            <svg className={`w-3.5 h-3.5 text-gray-400 transition-transform ${expandido ? 'rotate-0' : '-rotate-90'}`} viewBox="0 0 20 20" fill="currentColor">
                              <path fillRule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clipRule="evenodd" />
                            </svg>
                          )}
                        </div>
                      </button>
                      {delDia.length === 0 ? (
                        <p className="text-xs text-gray-400 px-4 py-6 text-center">Sin servicios programados</p>
                      ) : !expandido ? (
                        <p className="text-xs text-gray-400 px-4 py-3 text-center">Click para ver los {delDia.length} servicios</p>
                      ) : (
                        <div className="divide-y divide-gray-100">
                          {delDia.map(a => (
                            <button key={a.id} onClick={() => setSeleccionado(a)}
                              className={`w-full text-left px-4 py-3 border-l-[3px] hover:bg-gray-50 transition-colors ${ACENTO_ESTADO[a.estado] ?? 'border-l-gray-300'}`}>
                              <div className="flex items-center justify-between gap-2">
                                <span className="text-xs font-semibold text-gray-700">{formatHora(a.fechaProgramada)}</span>
                                <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${BADGE_ESTADO[a.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                                  {a.estado}
                                </span>
                              </div>
                              <p className="text-sm text-gray-800 font-medium truncate mt-0.5">{a.subproceso}</p>
                              <p className="text-xs text-gray-400 truncate">
                                {a.nombresEvaluado} {a.apellidosEvaluado ?? ''} · {a.nombreCiudad ?? 'Sin ciudad'}
                              </p>
                              <p className="text-xs text-gray-400 truncate">{a.nombreAsignado ?? 'Sin asignar'}</p>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )
          ) : (
            <CronogramaDia
              asignaciones={asignacionesDia}
              cargando={cargandoDia}
              error={errorDia}
              onSeleccionar={setSeleccionado}
              dia={diaSeleccionado}
            />
          )}
        </>
      )}

      {/* ══════════════════════ Contenido: Disponibilidad ══════════════════════ */}
      {seccion === 'disponibilidad' && (
        <>
          <div className="mb-4">
            <select value={empleadoDisponibilidad} onChange={e => setEmpleadoDisponibilidad(e.target.value)}
              className="border border-gray-200 rounded-lg px-3 py-2 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white min-w-[240px]">
              <option value="">Selecciona un empleado…</option>
              {opcionesEmpleadoDisponibilidad.map(([id, nombre]) => <option key={id} value={id}>{nombre}</option>)}
            </select>
          </div>

          {!empleadoDisponibilidad ? (
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm text-center py-16">
              <p className="text-sm text-gray-500 font-medium">Selecciona un empleado para ver su disponibilidad.</p>
              <p className="text-xs text-gray-400 mt-1">Verás sus horas libres y ocupadas de ese día.</p>
            </div>
          ) : (
            <DisponibilidadEmpleado
              nombre={opcionesEmpleadoDisponibilidad.find(([id]) => String(id) === String(empleadoDisponibilidad))?.[1]}
              dia={diaDisponibilidad}
              cargando={cargandoDisponibilidad}
              error={errorDisponibilidad}
              franjas={franjasDisponibilidad}
            />
          )}
        </>
      )}

      {seleccionado && (
        <ModalAsignarSubproceso
          asignacion={seleccionado}
          onClose={() => setSeleccionado(null)}
          onGuardado={onGuardado}
          onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo })}
        />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

/** Tarjeta de disponibilidad de un empleado para un día: encabezado con avatar + resumen
 *  libre/ocupado, y la grilla de franjas agrupada en mañana/tarde. */
function DisponibilidadEmpleado({ nombre, dia, cargando, error, franjas }) {
  const libres = franjas.filter(f => f.disponible).length
  const ocupadas = franjas.length - libres
  const manana = franjas.filter(f => new Date(f.inicio).getHours() < 12)
  const tarde = franjas.filter(f => new Date(f.inicio).getHours() >= 12)

  return (
    <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-3 flex-wrap justify-between">
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-10 h-10 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center text-sm font-bold flex-shrink-0">
            {iniciales(nombre)}
          </div>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-900 truncate">{nombre ?? '—'}</p>
            <p className="text-xs text-gray-400 capitalize truncate">{formatDiaLargo(dia)}</p>
          </div>
        </div>
        {!cargando && !error && franjas.length > 0 && (
          <div className="flex items-center gap-2 flex-shrink-0">
            <span className="flex items-center gap-1.5 text-xs font-medium text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-full px-2.5 py-1">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" /> {libres} libres
            </span>
            <span className="flex items-center gap-1.5 text-xs font-medium text-gray-500 bg-gray-50 border border-gray-100 rounded-full px-2.5 py-1">
              <span className="w-1.5 h-1.5 rounded-full bg-gray-400" /> {ocupadas} ocupadas
            </span>
          </div>
        )}
      </div>

      <div className="p-5">
        {cargando ? (
          <div className="flex items-center gap-2 text-xs text-gray-400 py-2">
            <div className="animate-spin rounded-full h-4 w-4 border-2 border-primary-500 border-t-transparent" />
            Cargando disponibilidad...
          </div>
        ) : error ? (
          <p className="text-xs text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">{error}</p>
        ) : franjas.length === 0 ? (
          <p className="text-xs text-gray-400 py-2">Sin franjas para ese día (fuera de jornada 8:00–17:00).</p>
        ) : (
          <div className="space-y-5">
            <BloqueFranjas titulo="Mañana" franjas={manana} />
            <BloqueFranjas titulo="Tarde" franjas={tarde} />
          </div>
        )}
      </div>
    </div>
  )
}

/** Une franjas consecutivas del mismo estado (y, si están ocupadas, del mismo servicio — el
 *  texto de "ocupadoPor" ya incluye el número de servicio, así que dos citas distintas nunca se
 *  confunden) en un solo bloque con su rango real de horas. Así una cita de 45 min no se ve como
 *  3 casillas idénticas de 15 min sin relación entre sí — se ve como un solo bloque de 08:00 a
 *  08:45 con el nombre del servicio que la ocupa, sin depender de un hover para identificarlo. */
function agruparFranjas(franjas) {
  const grupos = []
  franjas.forEach(f => {
    const ultimo = grupos[grupos.length - 1]
    const contiguo = ultimo && ultimo.fin === f.inicio && ultimo.disponible === f.disponible && ultimo.ocupadoPor === f.ocupadoPor
    if (contiguo) ultimo.fin = f.fin
    else grupos.push({ inicio: f.inicio, fin: f.fin, disponible: f.disponible, ocupadoPor: f.ocupadoPor, unidades: 1 })
    if (contiguo) ultimo.unidades += 1
  })
  return grupos
}

function BloqueFranjas({ titulo, franjas }) {
  const grupos = agruparFranjas(franjas)
  if (grupos.length === 0) return null
  return (
    <div>
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">{titulo}</p>
      <div className="grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-6 gap-2">
        {grupos.map(g => (
          <div key={g.inicio} style={{ gridColumn: `span ${g.unidades}` }}
            title={!g.disponible ? g.ocupadoPor : undefined}
            className={`text-xs rounded-lg px-2 py-1.5 border ${
              g.disponible
                ? 'border-gray-200 text-gray-600'
                : 'border-gray-200 bg-gray-50 text-gray-700'
            }`}>
            <span className="block font-semibold tabular-nums">
              {formatHora(g.inicio)} – {formatHora(g.fin)}
            </span>
            {g.disponible ? (
              <span className="block text-[10px] text-emerald-600 font-medium mt-0.5">Libre</span>
            ) : (
              <span className="block text-[10px] text-gray-500 truncate mt-0.5">{g.ocupadoPor}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

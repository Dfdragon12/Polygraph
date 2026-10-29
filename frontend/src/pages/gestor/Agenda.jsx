import SemaforoEstados from '../../components/semaforo/SemaforoEstados'
import AlertasAparte from '../../components/semaforo/AlertasAparte'
import { useState, useEffect, useCallback, useRef, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import asignacionService from '../../services/asignacionService'
import { useCambios } from '../../hooks/useCambios'
import formulariosService from '../../services/formulariosService'
import Toast from '../../components/Toast'
import Paginacion from '../../components/ui/Paginacion'
import MenuAcciones from '../../components/ui/MenuAcciones'
import ModalAsignarSubproceso from '../../components/ModalAsignarSubproceso'
import EstadoTiempo from '../../components/EstadoTiempo'
import ModalCitaAccion from '../../components/citas/ModalCitaAccion'
import { useDiasNoHabiles } from '../../hooks/useDiasNoHabiles'
import DisponibilidadEquipo from '../../components/agenda/DisponibilidadEquipo'
import BandejaParaReprogramar from '../../components/agenda/BandejaParaReprogramar'
import CronogramaDia from '../../components/CronogramaDia'
import { toLocalDateTimeString, toLocalDateString, hoyLocalDateString, rangoDelDia } from '../../utils/fechas'

/* ─── Qué tan lleno está el formulario de un subproceso — para que gestor vea el avance sin
   abrir cada uno. ─── */
function BadgeProgresoFormulario({ idServicioSubproceso }) {
  const [progreso, setProgreso] = useState(null)

  useEffect(() => {
    formulariosService.progreso(idServicioSubproceso).then(setProgreso).catch(() => {})
  }, [idServicioSubproceso])

  // porcentaje: avance por secciones (incluye tablas) — antes solo se contaban campos y una
  // plantilla hecha solo de tablas salía "0/0". null = la plantilla no tiene secciones.
  if (!progreso || progreso.porcentaje == null) return null
  const { porcentaje, seccionesCompletas, seccionesTotal } = progreso
  const color = porcentaje >= 100
    ? 'bg-green-100 text-green-700'
    : porcentaje > 0
      ? 'bg-amber-100 text-amber-700'
      : 'bg-gray-100 text-gray-500'

  return (
    <span title={`Formulario: ${seccionesCompletas} de ${seccionesTotal} secciones completas`}
      className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full whitespace-nowrap ${color}`}>
      📄 {porcentaje}%
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
function IconoMasMenos({ abierto }) {
  return (
    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d={abierto ? 'M19.5 12h-15' : 'M12 4.5v15m7.5-7.5h-15'} />
    </svg>
  )
}

/* ─── Columna "Fechas": oculta por defecto, un "+" la despliega — evita que la tabla se vea
   amontonada con fechas que la mayoría del tiempo nadie necesita mirar. Qué filas mostrar depende
   del estado REAL de esa fila, no del filtro de tarjeta activo. ─── */
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
      <span className="font-medium text-blue-500">Asig.</span><span className="text-gray-600">{formatFechaHora(a.fechaAsignacion)}</span>
      <span className="font-medium text-gray-400">Prog.</span><span className="text-gray-600">{formatFechaHora(a.fechaProgramada)}</span>
      <span className="font-medium text-gray-400">Listo</span><span className="text-gray-600">{formatHoraLimite(a.fechaProgramada, a.duracionMinutos)}</span>
      {(a.estado === 'FINALIZADO' || a.estado === 'PUBLICADO') && (
        <>
          <span className="font-medium text-violet-500">Compl.</span>
          <span className="text-gray-600">{formatFechaHora(a.fechaCompletado)}</span>
        </>
      )}
      {a.estado === 'PUBLICADO' && (
        <>
          <span className="font-medium text-emerald-500">Publ.</span>
          <span className="text-gray-600">{formatFechaHora(a.fechaPublicado)}</span>
        </>
      )}
      <button onClick={onToggle}
        className="col-span-2 inline-flex items-center gap-1 text-[10px] text-gray-400 hover:text-gray-600 mt-0.5">
        <IconoMasMenos abierto /> Ocultar
      </button>
    </div>
  )
}
function IconoLapiz() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
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


const BADGE_ESTADO = {
  PENDIENTE:  'bg-amber-100 text-amber-700',
  PROGRAMANDO: 'bg-sky-100 text-sky-700',
  REPROGRAMADO: 'bg-orange-100 text-orange-700',
  EN_EJECUCION:   'bg-blue-100 text-blue-700',
  FINALIZADO: 'bg-violet-100 text-violet-700',
  PUBLICADO:  'bg-emerald-100 text-emerald-700',
  CANCELADO:  'bg-red-100 text-red-600',
}

const ACENTO_ESTADO = {
  PENDIENTE:  'border-l-amber-400',
  PROGRAMANDO: 'border-l-sky-400',
  REPROGRAMADO: 'border-l-orange-400',
  EN_EJECUCION:   'border-l-blue-500',
  FINALIZADO: 'border-l-violet-400',
  PUBLICADO:  'border-l-emerald-500',
  CANCELADO:  'border-l-red-400',
}

function formatFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
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

function formatHora(iso) {
  return new Date(iso).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
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
  return d.toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' })   // lunes, 26 de octubre
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

/* Agenda de Gestor: un solo módulo con tres pestañas — "Servicios" (la cola de trabajo de
   análisis, con tarjetas de estado y filtros), "Cronograma" (semana/día) y "Disponibilidad" (el
   día completo de UN empleado puntual, libre/ocupado, sin depender de un subproceso pendiente).
   Antes Servicios y Cronograma eran páginas separadas; se combinaron para no tener que saltar
   entre pantallas para lo mismo. Acotado a subprocesos de Analista Interno (el backend ya filtra
   esto para GESTOR) — el gestor no agenda poligrafía ni visita domiciliaria, eso lo coordina el
   Programador. */
export default function GestorAgenda() {
  const navigate = useNavigate()
  // 'servicios' | 'cronograma' | 'disponibilidad' — ?seccion= abre directo una (ej. desde el tablero)
  const [seccion, setSeccion] = useState(() => {
    const s = new URLSearchParams(window.location.search).get('seccion')
    return ['servicios', 'cronograma', 'disponibilidad'].includes(s) ? s : 'servicios'
  })
  const [seleccionado, setSeleccionado] = useState(null)
  const [toast, setToast]               = useState(null)
  const [accionCita, setAccionCita]       = useState(null)   // { cita, modo } — cita no realizada / reprogramar / reasignar (SE-07)
  const [fechasAbiertas, setFechasAbiertas] = useState(() => new Set())
  const toggleFechas = (id) => setFechasAbiertas(prev => {
    const next = new Set(prev)
    if (next.has(id)) next.delete(id); else next.add(id)
    return next
  })

  // ══════════════════════ Pestaña: Servicios ══════════════════════
  const [listaServicios, setListaServicios]         = useState([])
  const [cargandoServicios, setCargandoServicios]   = useState(true)
  const [errorServicios, setErrorServicios]         = useState(null)
  const [estadoServicios, setEstadoServicios]       = useState('PENDIENTE')
  const [busquedaServicios, setBusquedaServicios]   = useState('')
  const [buscarServicios, setBuscarServicios]       = useState('')
  const buscarServiciosRef                          = useRef('')
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

  const cargarServicios = useCallback(async (estadoActual, subprocesoActual, empleadoActual, page = 0, silencioso = false) => {
    if (!silencioso) { setCargandoServicios(true); setErrorServicios(null) }
    try {
      const [rLista, rConteo] = await Promise.all([
        asignacionService.listar({
          estado: estadoActual || undefined,
          idTipoProgreso: subprocesoActual || undefined,
          idUsuarioAsignado: empleadoActual || undefined,
          page, size: POR_PAGINA,
          // El buscador filtra en el servidor: busca en TODOS los servicios, no solo en esta página.
          buscar: buscarServiciosRef.current || undefined,
          // Más antiguo primero — igual que MisAsignaciones.jsx, lo que lleva más tiempo
          // esperando queda arriba en vez de enterrado al final de la página.
          sort: 'servicio.fechaCompromisoAjustada,asc',   // lo que vence antes al cliente primero (SE-08)
        }),
        asignacionService.conteo(),
      ])
      const lista = rLista.content ?? []
      setListaServicios(lista)
      setTotalElementosServicios(rLista.totalElements ?? 0)
      setConteosServicios(rConteo)
      // El catálogo de empleados asignables no está expuesto a Gestor — se va acumulando con
      // quienes van apareciendo en los resultados, para que el filtro no pierda opciones al paginar.
      setOpcionesEmpleadoServicios(prev => {
        const nuevo = new Map(prev)
        lista.forEach(a => { if (a.idUsuarioAsignado != null) nuevo.set(a.idUsuarioAsignado, a.nombreAsignado) })
        return nuevo
      })
    } catch { if (!silencioso) setErrorServicios('Error al cargar los servicios.') }
    finally { if (!silencioso) setCargandoServicios(false) }
  }, [])

  useEffect(() => {
    const t = setTimeout(() => setBuscarServicios(busquedaServicios.trim()), 350)
    return () => clearTimeout(t)
  }, [busquedaServicios])

  useEffect(() => {
    buscarServiciosRef.current = buscarServicios
    setPaginaServicios(0)
    cargarServicios(estadoServicios, filtroSubprocesoServicios, filtroEmpleadoServicios, 0)
  }, [estadoServicios, filtroSubprocesoServicios, filtroEmpleadoServicios, buscarServicios, cargarServicios])

  const alternarTarjeta = (e) => setEstadoServicios(prev => prev === e ? '' : e)

  // El filtro del buscador ya lo aplicó el servidor (parámetro buscar).
  const serviciosFiltrados = listaServicios

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

  // Sábados, domingos y festivos de la semana (y del día elegido): no se muestran como días de trabajo.
  const desdeNoHabiles = [toLocalDateString(inicioSemana), diaSeleccionado].sort()[0]
  const hastaNoHabiles = [toLocalDateString(dias[6]), diaSeleccionado].sort()[1]
  const noHabiles = useDiasNoHabiles(desdeNoHabiles, hastaNoHabiles)

  const cargarSemana = useCallback(async (desdeFecha, silencioso = false) => {
    if (!silencioso) { setCargandoSemana(true); setErrorSemana(null) }
    try {
      const hasta = new Date(desdeFecha)
      hasta.setDate(hasta.getDate() + 7)
      const data = await asignacionService.calendario(toLocalDateTimeString(desdeFecha), toLocalDateTimeString(hasta))
      setAsignacionesSemana((data ?? []).filter(a => a.fechaProgramada).sort((a, b) => new Date(a.fechaProgramada) - new Date(b.fechaProgramada)))
    } catch { if (!silencioso) setErrorSemana('Error al cargar la agenda.') }
    finally { if (!silencioso) setCargandoSemana(false) }
  }, [])

  useEffect(() => { cargarSemana(inicioSemana) }, [inicioSemana, cargarSemana])
  useEffect(() => { setDiasExpandidosSemana(new Set()) }, [inicioSemana])

  const cargarDia = useCallback(async (dia, silencioso = false) => {
    if (!silencioso) { setCargandoDia(true); setErrorDia(null) }
    try {
      const { desde, hasta } = rangoDelDia(dia)
      const data = await asignacionService.calendario(desde, hasta)
      setAsignacionesDia(data ?? [])
    } catch { if (!silencioso) setErrorDia('Error al cargar el cronograma del día.') }
    finally { if (!silencioso) setCargandoDia(false) }
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


  // ══════════════════════ Compartido: modal ══════════════════════
  const onGuardado = () => {
    setSeleccionado(null)
    cargarServicios(estadoServicios, filtroSubprocesoServicios, filtroEmpleadoServicios, paginaServicios)
    cargarSemana(inicioSemana)
    if (vista === 'dia') cargarDia(diaSeleccionado)
  }

  // Tiempo real: si alguien agenda, reasigna o completa algo, la agenda se actualiza sola (sin spinner).
  useCambios(() => {
    cargarServicios(estadoServicios, filtroSubprocesoServicios, filtroEmpleadoServicios, paginaServicios, true)
    cargarSemana(inicioSemana, true)
    if (seccion === 'cronograma' && vista === 'dia') cargarDia(diaSeleccionado, true)
  })

  return (
    <div>
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm px-5 py-4 mb-5">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">Agenda</h2>
            <p className="text-sm text-gray-500 mt-0.5">
              {seccion === 'servicios'
                ? `${totalElementosServicios} servicio${totalElementosServicios !== 1 ? 's' : ''} con subprocesos de análisis`
                : seccion === 'cronograma'
                  ? (vista === 'semana'
                      ? `Análisis programados — ${inicioSemana.toLocaleDateString('es-CO', { day: 'numeric', month: 'short' })} al ${finSemana.toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })}`
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
                <input type="date" aria-label="Día de la agenda" value={diaSeleccionado} onChange={e => setDiaSeleccionado(e.target.value)}
                  className="px-3 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-500" />
                <button onClick={() => setDiaSeleccionado(hoyLocalDateString())} className={CLASE_BOTON_HOY}>
                  Hoy
                </button>
                <button onClick={() => cambiarDia(1)} title="Día siguiente" className={CLASE_BOTON_NAV}>
                  <IconoFlecha direccion="der" />
                </button>
              </div>
            ))}

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

          <SemaforoEstados seleccionado={estadoServicios} onSeleccionar={setEstadoServicios} titulo="Estado de los subprocesos" />
          <AlertasAparte />
          <BandejaParaReprogramar rolUsuario="gestor" onReprogramar={setSeleccionado} onNoRealizada={(a) => setAccionCita({ cita: a, modo: 'no-realizada' })} />

          <div className="flex items-center gap-2 flex-wrap mb-4">
            <input aria-label="Buscar por nombre o cédula del evaluado…" type="search" value={busquedaServicios} onChange={e => setBusquedaServicios(e.target.value)}
              placeholder="Buscar por nombre o cédula del evaluado…"
              className="flex-1 min-w-[220px] max-w-sm border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            <select aria-label="Todos los subprocesos" value={filtroSubprocesoServicios} onChange={e => setFiltroSubprocesoServicios(e.target.value)}
              className="border border-gray-200 rounded-lg px-3 py-2 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
              <option value="">Todos los subprocesos</option>
              {opcionesSubprocesoServicios.map(o => <option key={o.idTipoProgreso} value={o.idTipoProgreso}>{o.nombreProgreso}</option>)}
            </select>
            <select aria-label="Todos los empleados" value={filtroEmpleadoServicios} onChange={e => setFiltroEmpleadoServicios(e.target.value)}
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
                <table className="table-stack w-full divide-y divide-gray-100 text-sm">
                  <thead className="bg-gray-50/80">
                    <tr>
                      {/* Mismo criterio de MisAsignaciones.jsx (analista/poligrafista/visitador):
                          fechas juntas en una sola columna, "Formulario" pasa a ser un ícono
                          dentro de Acciones en vez de columna aparte, y "Retraso" solo se ve en
                          la pestaña "Asignados" — menos columnas, menos amontonado. */}
                      {['Evaluado', 'Subproceso', 'Fechas', 'Asignado a',
                        'Entrega al cliente',
                        'Estado', '···'].map(h => (
                        <th key={h} className="px-3 py-3 text-left text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {serviciosFiltrados.map(a => {
                      return (
                      <tr key={a.id} className="hover:bg-gray-50/70 transition-colors">
                        <td className="px-3 py-3">
                          <p className="font-medium text-gray-900">{a.nombresEvaluado} {a.apellidosEvaluado ?? ''}</p>
                          <p className="text-xs text-gray-400">{a.cedulaEvaluado ?? '—'} · Servicio #{a.idServicio}</p>
                        </td>
                        <td className="px-3 py-3 text-gray-700">{a.subproceso}</td>
                        <td className="px-3 py-3 text-xs whitespace-nowrap">
                          <CeldaFechas asignacion={a} abierta={fechasAbiertas.has(a.id)} onToggle={() => toggleFechas(a.id)} />
                        </td>
                        <td className="px-3 py-3 text-gray-500">{a.nombreAsignado ?? 'Sin asignar'}</td>
                        <td className="px-3 py-3 whitespace-nowrap">
                          {a.tiempo ? <EstadoTiempo tiempo={a.tiempo} /> : <span className="text-xs text-gray-500">—</span>}
                          {a.atrasadoAgenda && (
                            <span className="mt-1 block w-fit text-[11px] font-semibold bg-orange-50 text-orange-800 ring-1 ring-orange-300 rounded-full px-2 py-0.5">
                              Atrasado en agenda · {a.diasHabilesAtrasoAgenda} {a.diasHabilesAtrasoAgenda === 1 ? 'día hábil' : 'días hábiles'}
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-3">
                          <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap ${BADGE_ESTADO[a.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                            <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
                            {a.estado}
                          </span>
                        </td>
                        <td className="px-3 py-3">
                          <div className="flex items-center gap-1.5 justify-end">
                            {a.requiereDocumento && <BadgeProgresoFormulario idServicioSubproceso={a.id} />}
                            {/* Aprobar/Devolver ya no son íconos sueltos acá — se hacen dentro del
                                formulario (mismo criterio que "Marcar como completado" en
                                analista/poligrafista/visitador), revisando de verdad lo
                                diligenciado antes de aprobar o devolver. */}
                            <MenuAcciones opciones={[
                              // SE-07: una cita ya agendada no se edita; si no se realizó (p. ej. el evaluado avisó que no asistirá) se registra con motivo.
                              ['POLIGRAFISTA', 'VISITADOR'].includes(a.rolResponsable) && a.estado === 'EN_EJECUCION' && !a.iniciado && {
                                label: 'Registrar cita no realizada', icono: <IconoLapiz />, destacado: 'text-red-600',
                                onClick: () => setAccionCita({ cita: a, modo: 'no-realizada' }),
                              },
                              a.estado === 'REPROGRAMADO' && false && {
                                label: 'Reprogramar', icono: <IconoAsignar />, onClick: () => setAccionCita({ cita: a, modo: 'reprogramar' }),
                              },
                              a.estado === 'REPROGRAMADO' && false && {
                                label: 'Reasignar a otro profesional', icono: <IconoAsignar />, onClick: () => setAccionCita({ cita: a, modo: 'reasignar' }),
                              },
                              a.requiereDocumento && {
                                label: 'Formulario', icono: <IconoHoja />,
                                onClick: () => navigate(`/gestor/formulario/${a.id}`, { state: { origenSeccion: 'Agenda' } }),
                              },
                              {
                                label: a.estado === 'PENDIENTE' ? 'Asignar' : 'Ver / editar',
                                icono: a.estado === 'PENDIENTE' ? <IconoAsignar /> : <IconoLapiz />,
                                onClick: () => setSeleccionado(a),
                              },
                            ]} />
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
              <select aria-label="Todos los subprocesos" value={filtroSubprocesoSemana} onChange={e => setFiltroSubprocesoSemana(e.target.value)}
                className="border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
                <option value="">Todos los subprocesos</option>
                {opcionesSubprocesoSemana.map(([id, nombre]) => <option key={id} value={id}>{nombre}</option>)}
              </select>
              <select aria-label="Todos los empleados" value={filtroEmpleadoSemana} onChange={e => setFiltroEmpleadoSemana(e.target.value)}
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
                  const motivoNoHabil = noHabiles.get(toLocalDateString(dia))     // sábado, domingo o festivo: no es día de trabajo
                  const expandido = diasExpandidosSemana.has(claveDia)
                  return (
                    <div key={claveDia} className={`${motivoNoHabil ? 'bg-slate-100' : 'bg-white'} rounded-xl border shadow-sm overflow-hidden transition-shadow hover:shadow-md ${esHoy ? 'border-primary-300 ring-1 ring-primary-100' : 'border-gray-200'}`}>
                      <button type="button" onClick={() => alternarDiaSemana(claveDia)} disabled={delDia.length === 0}
                        className={`w-full flex items-center justify-between px-4 py-3 border-b transition-colors ${esHoy ? 'bg-primary-50/70 border-primary-100' : 'bg-gray-50/80 border-gray-100'} ${delDia.length > 0 ? 'hover:bg-gray-100/70' : 'cursor-default'}`}>
                        <p className={`text-xs font-semibold capitalize ${esHoy ? 'text-primary-700' : 'text-gray-600'}`}>
                          {formatFechaCorta(dia)}
                        </p>
                        <div className="flex items-center gap-1.5">
                          {motivoNoHabil && <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-slate-300 text-slate-900">No hábil · {motivoNoHabil}</span>}
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
                        <p className="text-xs text-gray-400 px-4 py-6 text-center">{motivoNoHabil ? 'No se trabaja este día' : 'Sin servicios programados'}</p>
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
                                {a.nombresEvaluado} {a.apellidosEvaluado ?? ''}
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
            <>
              <p className="mb-3 text-sm font-semibold text-gray-900 capitalize">
                {formatDiaLargo(diaSeleccionado)}
                {noHabiles.get(diaSeleccionado) && (
                  <span className="ml-2 text-[11px] font-semibold px-2 py-0.5 rounded-full bg-slate-300 text-slate-900 normal-case">No hábil · {noHabiles.get(diaSeleccionado)}</span>
                )}
              </p>
            <CronogramaDia
              asignaciones={asignacionesDia}
              cargando={cargandoDia}
              error={errorDia}
              onSeleccionar={setSeleccionado}
              dia={diaSeleccionado}
            />
            </>
          )}
        </>
      )}

      {/* ══════════════════════ Contenido: Disponibilidad (equipo hoy y el día de cada persona) ══════════════════════ */}
      {seccion === 'disponibilidad' && <DisponibilidadEquipo rolUsuario="gestor" onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo })} onAsignado={onGuardado} />}

      {seleccionado && (
        <ModalAsignarSubproceso
          asignacion={seleccionado}
          onClose={() => setSeleccionado(null)}
          onGuardado={onGuardado}
          onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo })}
        />
      )}

      {accionCita && (
        <ModalCitaAccion cita={accionCita.cita} modo={accionCita.modo} onClose={() => setAccionCita(null)}
          onHecho={() => { setAccionCita(null); setToast({ mensaje: 'Listo: quedó registrado.', tipo: 'exito' }) }} />
      )}
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}


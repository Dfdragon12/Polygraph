import { useState, useEffect } from 'react'
import asignacionService from '../services/asignacionService'
import { Modal, ConfirmModal } from './ui/Modal'

function formatFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function toDatetimeLocal(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function hoyLocal() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function toDiaLocal(iso) {
  return iso ? toDatetimeLocal(iso).slice(0, 10) : hoyLocal()
}

function formatHora(iso) {
  return new Date(iso).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

/* Una franja ya pasó si es hoy y su hora de inicio ya quedó atrás — no tiene sentido ofrecerla
 * ("si son las 2:30pm no puedo asignar 8:00am"), aunque el empleado esté libre a esa hora. */
function yaPaso(iso) {
  return new Date(iso).getTime() < Date.now()
}

const BADGE_ESTADO = {
  PENDIENTE:  'bg-amber-100 text-amber-700',
  ASIGNADO:   'bg-blue-100 text-blue-700',
  COMPLETADO: 'bg-violet-100 text-violet-700',
  PUBLICADO:  'bg-emerald-100 text-emerald-700',
  CANCELADO:  'bg-red-100 text-red-600',
}

const DOT_ACCION = {
  ASIGNADO:    'bg-blue-400',
  REASIGNADO:  'bg-indigo-400',
  DESASIGNADO: 'bg-amber-400',
  COMPLETADO:  'bg-violet-400',
  DEVUELTO:    'bg-amber-400',
  PUBLICADO:   'bg-emerald-400',
  CANCELADO:   'bg-red-400',
  REPROGRAMADO: 'bg-sky-400',
}

// Confirmación de asistencia — solo aplica a poligrafía/visita (trabajo presencial, hay que
// llamar antes para no desplazar al empleado en vano si el candidato no va a estar).
const ROLES_CON_CONFIRMACION = ['POLIGRAFISTA', 'VISITADOR']
const BADGE_CONFIRMACION = {
  SIN_LLAMAR:  'bg-gray-100 text-gray-500',
  CONFIRMADO:  'bg-green-100 text-green-700',
  NO_CONTESTA: 'bg-amber-100 text-amber-700',
  REPROGRAMAR: 'bg-red-100 text-red-600',
}
const ETIQUETA_CONFIRMACION = {
  SIN_LLAMAR:  'Sin llamar',
  CONFIRMADO:  'Confirmado',
  NO_CONTESTA: 'No contesta',
  REPROGRAMAR: 'Reprogramar',
}
const OPCIONES_CONFIRMACION = [
  { valor: 'CONFIRMADO',  clase: 'border-green-200 text-green-700 hover:bg-green-50', claseActiva: 'bg-green-600 border-green-600 text-white' },
  { valor: 'NO_CONTESTA', clase: 'border-amber-200 text-amber-600 hover:bg-amber-50', claseActiva: 'bg-amber-500 border-amber-500 text-white' },
  { valor: 'REPROGRAMAR', clase: 'border-red-200 text-red-600 hover:bg-red-50',       claseActiva: 'bg-red-600 border-red-600 text-white' },
]

function IconoTelefono() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 6.75c0 8.284 6.716 15 15 15h1.5a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106a2.25 2.25 0 00-2.239.649l-.973.973a1.125 1.125 0 01-1.191.26 12.35 12.35 0 01-7.135-7.135 1.125 1.125 0 01.26-1.191l.973-.973a2.25 2.25 0 00.649-2.239L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z" />
    </svg>
  )
}

function nombreCorto(nombre) {
  return nombre?.trim() || 'Sin asignar'
}

/* Frase legible por tipo de evento — la fecha programada solo se muestra cuando cambió. */
function describirEvento(h) {
  const fechaAnt = h.fechaProgramadaAnterior ? formatFechaHora(h.fechaProgramadaAnterior) : null
  const fechaNueva = h.fechaProgramadaNueva ? formatFechaHora(h.fechaProgramadaNueva) : null

  switch (h.accion) {
    case 'ASIGNADO':
      return `Asignado a ${nombreCorto(h.usuarioNuevoNombre)}${fechaNueva ? ` — programado ${fechaNueva}` : ''}`
    case 'REASIGNADO':
      return `Reasignado de ${nombreCorto(h.usuarioAnteriorNombre)} a ${nombreCorto(h.usuarioNuevoNombre)}${fechaNueva ? ` — programado ${fechaNueva}` : ''}`
    case 'DESASIGNADO':
      return `Desasignado — antes estaba con ${nombreCorto(h.usuarioAnteriorNombre)}${fechaAnt ? ` (programado ${fechaAnt})` : ''}`
    case 'COMPLETADO':
      return `Marcado como completado (asignado a ${nombreCorto(h.usuarioNuevoNombre)})`
    case 'CANCELADO':
      return `Cancelado${h.usuarioNuevoNombre ? ` (estaba con ${nombreCorto(h.usuarioNuevoNombre)})` : ''}`
    case 'REPROGRAMADO':
      return `Reprogramado${fechaAnt ? ` de ${fechaAnt}` : ''}${fechaNueva ? ` a ${fechaNueva}` : ''}${h.observaciones ? ` — ${h.observaciones}` : ''}`
    default:
      return h.accion
  }
}

/* ─── Modal: asignar / editar / completar / desasignar un subproceso ─── */
export default function ModalAsignarSubproceso({ asignacion, onClose, onGuardado, onToast }) {
  const [empleados, setEmpleados] = useState([])
  const [cargandoEmpleados, setCargandoEmpleados] = useState(true)
  const [errorEmpleados, setErrorEmpleados] = useState(null)
  const [idUsuarioAsignado, setIdUsuarioAsignado] = useState(asignacion.idUsuarioAsignado ?? '')
  const [dia, setDia] = useState(toDiaLocal(asignacion.fechaProgramada))
  const [fechaProgramada, setFechaProgramada] = useState(toDatetimeLocal(asignacion.fechaProgramada))
  // Por defecto se usa el tiempo estimado del catálogo tal cual — solo se ajusta si el usuario
  // explícitamente lo pide, porque el tiempo real varía caso a caso.
  const [ajustarDuracion, setAjustarDuracion] = useState(false)
  const [duracionMinutos, setDuracionMinutos] = useState(String(asignacion.duracionMinutos ?? ''))
  const [franjas, setFranjas] = useState([])
  const [cargandoFranjas, setCargandoFranjas] = useState(false)
  const [errorFranjas, setErrorFranjas] = useState(null)
  const [observaciones, setObservaciones] = useState(asignacion.observaciones ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const [confirmarAccion, setConfirmarAccion] = useState(null) // 'completar' | 'desasignar' | 'cancelar' | 'reasignar'
  const [historial, setHistorial] = useState([])
  const [cargandoHistorial, setCargandoHistorial] = useState(true)
  const [mostrarHistorial, setMostrarHistorial] = useState(false)
  const [historialLlamadas, setHistorialLlamadas] = useState([])
  const [cargandoHistorialLlamadas, setCargandoHistorialLlamadas] = useState(true)
  const [mostrarHistorialLlamadas, setMostrarHistorialLlamadas] = useState(false)

  const [estadoConfirmacion, setEstadoConfirmacion] = useState(asignacion.estadoConfirmacion ?? 'SIN_LLAMAR')
  const [notasConfirmacion, setNotasConfirmacion] = useState(asignacion.notasConfirmacion ?? '')
  const [mostrarConfirmacion, setMostrarConfirmacion] = useState(false)
  const puedeConfirmar = ROLES_CON_CONFIRMACION.includes(asignacion.rolResponsable)
  // Solo tiene sentido mientras sigue PENDIENTE: la idea es llamar primero para confirmar que el
  // candidato va a estar, y con eso decidir a quién y cuándo asignar — no al revés. Una vez
  // asignado, ya se decidió — el botón "Reasignar" (más abajo) desasigna primero para volver aquí.
  const requiereConfirmacion = puedeConfirmar && asignacion.estado === 'PENDIENTE'
  // El resto del formulario (a quién asignar, día, hora, observaciones) sigue bloqueado y en gris
  // hasta que la llamada quede en CONFIRMADO — si no contesta o hay que reprogramar, no tiene
  // sentido dejar asignar a nadie todavía.
  const formHabilitado = !requiereConfirmacion || estadoConfirmacion === 'CONFIRMADO'

  useEffect(() => {
    setCargandoEmpleados(true); setErrorEmpleados(null)
    asignacionService.empleadosDisponibles(asignacion.idTipoProgreso, asignacion.idCiudad)
      .then(setEmpleados)
      .catch(err => {
        setEmpleados([])
        setErrorEmpleados(err.response?.data?.mensaje ?? 'No se pudo cargar la lista de empleados disponibles.')
      })
      .finally(() => setCargandoEmpleados(false))
  }, [asignacion.idTipoProgreso, asignacion.idCiudad])

  const cargarHistorial = () => {
    setCargandoHistorial(true)
    asignacionService.historial(asignacion.id)
      .then(setHistorial)
      .catch(() => setHistorial([]))
      .finally(() => setCargandoHistorial(false))
  }

  useEffect(() => { cargarHistorial() }, [asignacion.id])

  useEffect(() => {
    if (!puedeConfirmar) { setCargandoHistorialLlamadas(false); return }
    setCargandoHistorialLlamadas(true)
    asignacionService.historialLlamadas(asignacion.id)
      .then(setHistorialLlamadas)
      .catch(() => setHistorialLlamadas([]))
      .finally(() => setCargandoHistorialLlamadas(false))
  }, [asignacion.id, puedeConfirmar])

  // Franjas libres/ocupadas del empleado elegido ese día — evita adivinar la hora a mano.
  // Si no se ajustó la duración, se usa el estimado del catálogo (el backend lo resuelve solo).
  useEffect(() => {
    if (!idUsuarioAsignado || !dia) { setFranjas([]); return }
    setCargandoFranjas(true); setErrorFranjas(null)
    const duracionParaFranjas = ajustarDuracion ? (duracionMinutos || undefined) : undefined
    asignacionService.franjasDisponibles(idUsuarioAsignado, dia, asignacion.idTipoProgreso, duracionParaFranjas, asignacion.id)
      .then(setFranjas)
      .catch(err => {
        setFranjas([])
        setErrorFranjas(err.response?.data?.mensaje ?? 'No se pudieron cargar las horas disponibles.')
      })
      .finally(() => setCargandoFranjas(false))
  }, [idUsuarioAsignado, dia, ajustarDuracion, duracionMinutos, asignacion.idTipoProgreso, asignacion.id])

  // Si la llamada no quedó en CONFIRMADO, el formulario de asignación sigue bloqueado — este botón
  // solo guarda el resultado de la llamada (sin exigir empleado/día/hora, que ni se pueden tocar).
  const soloGuardaConfirmacion = requiereConfirmacion && !formHabilitado

  const guardar = async (e) => {
    e.preventDefault()
    if (!soloGuardaConfirmacion) {
      if (!idUsuarioAsignado) { setError('Selecciona a quién asignar.'); return }
      if (!dia) { setError('Selecciona el día.'); return }
      if (!fechaProgramada) { setError('Selecciona una hora de la lista.'); return }
      if (yaPaso(fechaProgramada)) {
        setError('No puedes programar una fecha/hora que ya pasó.')
        return
      }
      if (ajustarDuracion && Number(duracionMinutos) < (asignacion.duracionMinutos ?? 0)) {
        setError(`La duración no puede ser menor al estimado del catálogo (${asignacion.duracionMinutos} min).`)
        return
      }
    }
    setGuardando(true); setError(null)
    try {
      // La confirmación de asistencia se guarda junto con la asignación, en el mismo click de
      // "Guardar asignación" — no hay un botón de guardado aparte para la llamada.
      if (requiereConfirmacion) {
        await asignacionService.confirmarAsistencia(asignacion.id, estadoConfirmacion, notasConfirmacion || null)
      }
      if (soloGuardaConfirmacion) {
        onToast('Confirmación de asistencia guardada')
      } else {
        await asignacionService.asignar(asignacion.id, {
          idUsuarioAsignado: Number(idUsuarioAsignado),
          // El backend guarda esto como LocalDateTime (sin zona horaria) — se envía tal cual la
          // hora local que se escogió en el input. Convertir con toISOString() aquí desplazaría
          // la hora por el offset del navegador (ej. 5h en Colombia) al guardarse como si fuera local.
          fechaProgramada: fechaProgramada || null,
          observaciones: observaciones || null,
          duracionMinutos: ajustarDuracion && duracionMinutos ? Number(duracionMinutos) : null,
        })
        onToast('Asignación guardada correctamente')
      }
      onGuardado()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al guardar.')
    } finally { setGuardando(false) }
  }

  const ejecutarAccion = async () => {
    setGuardando(true)
    try {
      if (confirmarAccion === 'completar') await asignacionService.completar(asignacion.id)
      // "Reasignar" es, en el fondo, un desasignar — vuelve a Pendiente para pedir la llamada de
      // confirmación de nuevo antes de poder elegir a quién y cuándo asignar.
      if (confirmarAccion === 'desasignar' || confirmarAccion === 'reasignar') await asignacionService.desasignar(asignacion.id)
      if (confirmarAccion === 'cancelar')  await asignacionService.cancelar(asignacion.id)
      onToast(
        confirmarAccion === 'completar'  ? 'Marcada como completada' :
        confirmarAccion === 'desasignar' ? 'Asignación removida — vuelve a Pendiente' :
        confirmarAccion === 'reasignar'  ? 'Se quitó la asignación — vuelve a llamar para reprogramar' :
        'Asignación cancelada'
      )
      onGuardado()
    } catch (err) {
      onToast(err.response?.data?.mensaje ?? 'Error al procesar la acción.', 'error')
    } finally { setGuardando(false); setConfirmarAccion(null) }
  }

  return (
    <Modal
      titulo={`${asignacion.subproceso} — Servicio #${asignacion.idServicio}`}
      subtitulo={`${asignacion.nombresEvaluado ?? ''} ${asignacion.apellidosEvaluado ?? ''}`.trim() || 'Sin evaluado'}
      onClose={onClose} ancho="max-w-lg">
      <form onSubmit={guardar} className="px-6 py-5 space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        <div className="flex items-center gap-2 flex-wrap text-xs text-gray-500">
          <span className={`px-2 py-0.5 rounded-full font-medium ${BADGE_ESTADO[asignacion.estado] ?? 'bg-gray-100 text-gray-600'}`}>
            {asignacion.estado}
          </span>
          {asignacion.proceso && <span>· Proceso: {asignacion.proceso}</span>}
          {asignacion.cargo && <span>· Cargo: {asignacion.cargo}</span>}
          {asignacion.duracionMinutos != null && <span>· Tiempo estimado: {asignacion.duracionMinutos} min</span>}
          {asignacion.nombreCiudad && <span>· Ciudad: {asignacion.nombreCiudad}</span>}
        </div>

        {puedeConfirmar && asignacion.estado === 'ASIGNADO' && (
          <div className="border border-gray-200 rounded-lg px-3 py-2.5">
            <button type="button" onClick={() => setConfirmarAccion('reasignar')}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary-600 hover:text-primary-800 hover:underline transition-colors">
              <IconoTelefono /> Reasignar
            </button>
            <p className="text-[11px] text-gray-400 mt-1">Vuelve a llamar para confirmar antes de cambiar a quién o cuándo está asignado.</p>
          </div>
        )}

        {requiereConfirmacion && (
          <div className="border border-gray-200 rounded-lg px-3 py-2.5 bg-primary-50/30">
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs font-medium text-gray-600">
                Confirmación de asistencia <span className="text-gray-400 font-normal">— llama antes de asignar</span>
              </p>
              <span className={`text-[11px] font-medium px-2 py-0.5 rounded-full flex-shrink-0 ${BADGE_CONFIRMACION[estadoConfirmacion] ?? BADGE_CONFIRMACION.SIN_LLAMAR}`}>
                {ETIQUETA_CONFIRMACION[estadoConfirmacion] ?? estadoConfirmacion}
              </span>
            </div>

            {!mostrarConfirmacion ? (
              <button type="button" onClick={() => setMostrarConfirmacion(true)}
                className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-primary-600 hover:text-primary-800 hover:underline transition-colors">
                <IconoTelefono /> {estadoConfirmacion === 'SIN_LLAMAR' ? 'Llamar' : 'Actualizar llamada'}
              </button>
            ) : (
              <div className="mt-2 space-y-2">
                <div className="flex gap-1.5 flex-wrap">
                  {OPCIONES_CONFIRMACION.map(o => (
                    <button key={o.valor} type="button"
                      onClick={() => setEstadoConfirmacion(o.valor)}
                      className={`text-xs font-medium px-2.5 py-1 rounded-lg border transition-colors ${
                        estadoConfirmacion === o.valor ? o.claseActiva : o.clase
                      }`}>
                      {ETIQUETA_CONFIRMACION[o.valor]}
                    </button>
                  ))}
                </div>
                <textarea value={notasConfirmacion} onChange={e => setNotasConfirmacion(e.target.value)} rows={2}
                  placeholder="Notas de la llamada…"
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
                <p className="text-[11px] text-gray-400">
                  {estadoConfirmacion === 'CONFIRMADO'
                    ? 'Confirmado — ya puedes elegir a quién y cuándo asignar, más abajo.'
                    : estadoConfirmacion === 'SIN_LLAMAR'
                      ? 'Elige un resultado para poder guardar.'
                      : 'Con este resultado el resto del formulario sigue bloqueado — solo se guarda la llamada.'}
                </p>
              </div>
            )}
          </div>
        )}

        {!formHabilitado && (
          <p className="text-xs text-gray-400 italic -mb-2">
            Primero registra la llamada de confirmación para poder asignar.
          </p>
        )}

        <fieldset disabled={!formHabilitado} className={`space-y-4 ${!formHabilitado ? 'opacity-50' : ''}`}>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Asignar a <span className="text-red-500">*</span>
          </label>
          {cargandoEmpleados ? (
            <div className="flex items-center gap-2 text-xs text-gray-400 py-2">
              <div className="animate-spin rounded-full h-4 w-4 border-2 border-primary-500 border-t-transparent" />
              Cargando empleados disponibles...
            </div>
          ) : errorEmpleados ? (
            <p className="text-xs text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
              {errorEmpleados}
            </p>
          ) : empleados.length === 0 ? (
            <p className="text-xs text-amber-600 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
              {asignacion.nombreCiudad
                ? `Ningún empleado con la capacidad "${asignacion.subproceso}" cubre ${asignacion.nombreCiudad} todavía. Revísalo desde Equipo Polygraph.`
                : `Ningún empleado tiene "${asignacion.subproceso}" marcado como capacidad todavía. Asígnaselo desde Equipo Polygraph.`}
            </p>
          ) : (
            // Si el subproceso requiere ciudad (poligrafista/visitador), el backend ya filtró la
            // lista a solo quienes cubren la ciudad del evaluado — no aparece ni se puede
            // seleccionar a quien no la cubre.
            <select value={idUsuarioAsignado} onChange={e => { setIdUsuarioAsignado(e.target.value); setFechaProgramada('') }} required
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
              <option value="">Selecciona...</option>
              {empleados.map(emp => (
                <option key={emp.idUsuario} value={emp.idUsuario}>
                  {emp.nombre} {emp.apellido ?? ''}
                  {emp.numeroSala ? ` — Sala ${emp.numeroSala}` : ''}
                </option>
              ))}
            </select>
          )}
        </div>

        <div className="border border-gray-200 rounded-lg px-3 py-2.5">
          <p className="text-xs text-gray-600">
            Tiempo estimado del catálogo:{' '}
            <span className="font-semibold text-gray-800">
              {asignacion.duracionMinutos != null ? `${asignacion.duracionMinutos} min` : '—'}
            </span>
          </p>
          <label className="flex items-center gap-2 mt-2 text-xs text-gray-600 cursor-pointer">
            <input type="checkbox" checked={ajustarDuracion}
              onChange={e => { setAjustarDuracion(e.target.checked); setFechaProgramada('') }}
              className="accent-primary-600" />
            ¿Deseas aumentar el tiempo para este caso?
          </label>
          {ajustarDuracion && (
            <>
              <input type="number" min={asignacion.duracionMinutos ?? 1} step={5} value={duracionMinutos}
                onChange={e => { setDuracionMinutos(e.target.value); setFechaProgramada('') }}
                placeholder="Minutos"
                className="mt-2 w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
              <p className="text-xs text-gray-400 mt-1">No puede ser menor al estimado del catálogo ({asignacion.duracionMinutos} min).</p>
            </>
          )}
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Día <span className="text-red-500">*</span>
          </label>
          <input type="date" value={dia} min={hoyLocal()} required
            onChange={e => { setDia(e.target.value); setFechaProgramada('') }}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Hora <span className="text-red-500">*</span> {fechaProgramada && <span className="text-gray-400 font-normal">({formatHora(fechaProgramada)})</span>}
          </label>
          {!idUsuarioAsignado ? (
            <p className="text-xs text-gray-400 py-2">Primero selecciona a quién vas a asignar.</p>
          ) : cargandoFranjas ? (
            <div className="flex items-center gap-2 text-xs text-gray-400 py-2">
              <div className="animate-spin rounded-full h-4 w-4 border-2 border-primary-500 border-t-transparent" />
              Cargando horas disponibles...
            </div>
          ) : errorFranjas ? (
            <p className="text-xs text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">{errorFranjas}</p>
          ) : franjas.length === 0 ? (
            <p className="text-xs text-gray-400 py-2">Sin franjas para ese día (fuera de jornada 8:00–17:00).</p>
          ) : (
            <div className="grid grid-cols-3 gap-1.5 max-h-40 overflow-y-auto scrollbar-thin pr-1">
              {franjas.map(f => {
                const seleccionada = fechaProgramada && fechaProgramada.slice(0, 16) === f.inicio.slice(0, 16)
                const pasada = yaPaso(f.inicio)
                const disponible = f.disponible && !pasada
                return (
                  <button key={f.inicio} type="button" disabled={!disponible}
                    title={pasada ? 'Esa hora ya pasó' : !f.disponible ? `Ocupado: ${f.ocupadoPor}` : undefined}
                    onClick={() => setFechaProgramada(f.inicio.slice(0, 16))}
                    className={`text-xs rounded-lg px-2 py-1.5 border transition-colors ${
                      seleccionada
                        ? 'bg-primary-600 border-primary-600 text-white font-semibold'
                        : disponible
                          ? 'border-gray-200 text-gray-700 hover:border-primary-300 hover:bg-primary-50'
                          : 'border-gray-100 bg-gray-50 text-gray-300 cursor-not-allowed line-through'
                    }`}>
                    {formatHora(f.inicio)}
                  </button>
                )
              })}
            </div>
          )}
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Observaciones</label>
          <textarea value={observaciones} onChange={e => setObservaciones(e.target.value)} rows={3}
            placeholder="Notas para quien ejecuta este subproceso…"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
        </div>
        </fieldset>

        {(asignacion.fechaAsignacion || asignacion.fechaCompletado) && (
          <div className="text-xs text-gray-400 space-y-0.5">
            {asignacion.fechaAsignacion && <p>Asignado el {formatFechaHora(asignacion.fechaAsignacion)}</p>}
            {asignacion.fechaCompletado && <p>Completado el {formatFechaHora(asignacion.fechaCompletado)}</p>}
          </div>
        )}

        {/* ─── Historial de llamadas: cada intento de confirmación de asistencia ─── */}
        {!cargandoHistorialLlamadas && historialLlamadas.length > 0 && (
          <div className="border-t border-gray-100 pt-3">
            <button type="button" onClick={() => setMostrarHistorialLlamadas(v => !v)}
              className="flex items-center gap-1.5 text-xs font-medium text-gray-500 hover:text-gray-700 transition-colors">
              <span className={`transition-transform ${mostrarHistorialLlamadas ? 'rotate-90' : ''}`}>›</span>
              Historial de llamadas ({historialLlamadas.length})
            </button>

            {mostrarHistorialLlamadas && (
              <ol className="mt-3 space-y-3">
                {historialLlamadas.map(h => (
                  <li key={h.id} className="flex gap-2.5">
                    <span className={`mt-1 w-2 h-2 rounded-full flex-shrink-0 ${
                      h.estadoConfirmacion === 'CONFIRMADO' ? 'bg-green-400'
                        : h.estadoConfirmacion === 'NO_CONTESTA' ? 'bg-amber-400'
                          : h.estadoConfirmacion === 'REPROGRAMAR' ? 'bg-red-400' : 'bg-gray-300'
                    }`} />
                    <div className="min-w-0">
                      <p className="text-xs text-gray-700 leading-snug">
                        {ETIQUETA_CONFIRMACION[h.estadoConfirmacion] ?? h.estadoConfirmacion}
                        {h.notas ? ` — ${h.notas}` : ''}
                      </p>
                      <p className="text-[11px] text-gray-400 mt-0.5">
                        {formatFechaHora(h.fechaLlamada)}{h.actorNombre ? ` · por ${h.actorNombre}` : ''}
                      </p>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}

        {/* ─── Historial: quién estuvo asignado antes, quién queda y quién hizo cada cambio ─── */}
        {!cargandoHistorial && historial.length > 0 && (
          <div className="border-t border-gray-100 pt-3">
            <button type="button" onClick={() => setMostrarHistorial(v => !v)}
              className="flex items-center gap-1.5 text-xs font-medium text-gray-500 hover:text-gray-700 transition-colors">
              <span className={`transition-transform ${mostrarHistorial ? 'rotate-90' : ''}`}>›</span>
              Historial de asignación ({historial.length})
            </button>

            {mostrarHistorial && (
              <ol className="mt-3 space-y-3">
                {historial.map(h => (
                  <li key={h.id} className="flex gap-2.5">
                    <span className={`mt-1 w-2 h-2 rounded-full flex-shrink-0 ${DOT_ACCION[h.accion] ?? 'bg-gray-300'}`} />
                    <div className="min-w-0">
                      <p className="text-xs text-gray-700 leading-snug">{describirEvento(h)}</p>
                      <p className="text-[11px] text-gray-400 mt-0.5">
                        {formatFechaHora(h.fechaEvento)}{h.actorNombre ? ` · por ${h.actorNombre}` : ''}
                      </p>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}

        <div className="flex items-center justify-between gap-3 pt-2">
          <div className="flex items-center gap-2">
            {asignacion.estado === 'ASIGNADO' && (
              <button type="button" onClick={() => setConfirmarAccion('completar')}
                className="text-xs font-medium px-3 py-1.5 rounded-lg border border-green-200 text-green-700 hover:bg-green-50 transition-colors">
                Completar
              </button>
            )}
            {asignacion.estado === 'ASIGNADO' && (
              <button type="button" onClick={() => setConfirmarAccion('desasignar')}
                className="text-xs font-medium px-3 py-1.5 rounded-lg border border-amber-200 text-amber-600 hover:bg-amber-50 transition-colors">
                Desasignar
              </button>
            )}
            {asignacion.estado !== 'CANCELADO' && asignacion.estado !== 'COMPLETADO' && (
              <button type="button" onClick={() => setConfirmarAccion('cancelar')}
                className="text-xs font-medium px-3 py-1.5 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 transition-colors">
                Cancelar
              </button>
            )}
          </div>
          <div className="flex gap-3">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cerrar</button>
            <button type="submit"
              disabled={guardando || (formHabilitado && empleados.length === 0) || (requiereConfirmacion && estadoConfirmacion === 'SIN_LLAMAR')}
              className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
              {guardando ? 'Guardando...' : soloGuardaConfirmacion ? 'Guardar llamada' : 'Guardar asignación'}
            </button>
          </div>
        </div>
      </form>

      {confirmarAccion && (
        <ConfirmModal
          titulo={
            confirmarAccion === 'completar' ? 'Marcar como completada' :
            confirmarAccion === 'desasignar' ? 'Quitar asignación' :
            confirmarAccion === 'reasignar' ? 'Reasignar' : 'Cancelar asignación'
          }
          danger={confirmarAccion === 'cancelar'}
          confirmLabel={
            confirmarAccion === 'completar' ? 'Completar' :
            confirmarAccion === 'desasignar' ? 'Desasignar' :
            confirmarAccion === 'reasignar' ? 'Sí, reasignar' : 'Cancelar asignación'
          }
          cargando={guardando}
          onConfirmar={ejecutarAccion}
          onCancelar={() => setConfirmarAccion(null)}>
          <p className="text-sm text-gray-600">
            {confirmarAccion === 'completar' && 'Se marcará este subproceso como completado.'}
            {confirmarAccion === 'desasignar' && 'Se quitará la persona y fecha asignadas; el subproceso vuelve a Pendiente.'}
            {confirmarAccion === 'reasignar' && 'Se quitará a la persona y la fecha/hora ya asignadas, y el subproceso vuelve a Pendiente — vas a tener que llamar de nuevo para confirmar antes de elegir a quién y cuándo asignar.'}
            {confirmarAccion === 'cancelar' && 'Este subproceso quedará cancelado. Esta acción no elimina el registro.'}
          </p>
        </ConfirmModal>
      )}
    </Modal>
  )
}

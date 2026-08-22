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

const BADGE_ESTADO = {
  PENDIENTE:  'bg-amber-100 text-amber-700',
  ASIGNADO:   'bg-blue-100 text-blue-700',
  COMPLETADO: 'bg-green-100 text-green-700',
  CANCELADO:  'bg-red-100 text-red-600',
}

/* ─── Modal: asignar / editar / completar / desasignar un subproceso ─── */
export default function ModalAsignarSubproceso({ asignacion, onClose, onGuardado, onToast }) {
  const [empleados, setEmpleados] = useState([])
  const [cargandoEmpleados, setCargandoEmpleados] = useState(true)
  const [idUsuarioAsignado, setIdUsuarioAsignado] = useState(asignacion.idUsuarioAsignado ?? '')
  const [fechaProgramada, setFechaProgramada] = useState(toDatetimeLocal(asignacion.fechaProgramada))
  const [observaciones, setObservaciones] = useState(asignacion.observaciones ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const [confirmarAccion, setConfirmarAccion] = useState(null) // 'completar' | 'desasignar' | 'cancelar'

  useEffect(() => {
    asignacionService.empleadosDisponibles(asignacion.idTipoProgreso)
      .then(setEmpleados)
      .catch(() => setEmpleados([]))
      .finally(() => setCargandoEmpleados(false))
  }, [asignacion.idTipoProgreso])

  const guardar = async (e) => {
    e.preventDefault()
    if (!idUsuarioAsignado) { setError('Selecciona a quién asignar.'); return }
    setGuardando(true); setError(null)
    try {
      await asignacionService.asignar(asignacion.id, {
        idUsuarioAsignado: Number(idUsuarioAsignado),
        fechaProgramada: fechaProgramada ? new Date(fechaProgramada).toISOString() : null,
        observaciones: observaciones || null,
      })
      onToast('Asignación guardada correctamente')
      onGuardado()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al guardar la asignación.')
    } finally { setGuardando(false) }
  }

  const ejecutarAccion = async () => {
    setGuardando(true)
    try {
      if (confirmarAccion === 'completar')  await asignacionService.completar(asignacion.id)
      if (confirmarAccion === 'desasignar') await asignacionService.desasignar(asignacion.id)
      if (confirmarAccion === 'cancelar')   await asignacionService.cancelar(asignacion.id)
      onToast(
        confirmarAccion === 'completar'  ? 'Marcada como completada' :
        confirmarAccion === 'desasignar' ? 'Asignación removida — vuelve a Pendiente' :
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
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Asignar a <span className="text-red-500">*</span>
          </label>
          {cargandoEmpleados ? (
            <div className="flex items-center gap-2 text-xs text-gray-400 py-2">
              <div className="animate-spin rounded-full h-4 w-4 border-2 border-primary-500 border-t-transparent" />
              Cargando empleados disponibles...
            </div>
          ) : empleados.length === 0 ? (
            <p className="text-xs text-amber-600 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
              Ningún empleado tiene "{asignacion.subproceso}" marcado como capacidad todavía. Asígnaselo desde Equipo Polygraph.
            </p>
          ) : (
            <select value={idUsuarioAsignado} onChange={e => setIdUsuarioAsignado(e.target.value)} required
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
              <option value="">Selecciona...</option>
              {empleados.map(emp => (
                <option key={emp.idUsuario} value={emp.idUsuario}>{emp.nombre} {emp.apellido ?? ''}</option>
              ))}
            </select>
          )}
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Fecha y hora programada</label>
          <input type="datetime-local" value={fechaProgramada} onChange={e => setFechaProgramada(e.target.value)}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Observaciones</label>
          <textarea value={observaciones} onChange={e => setObservaciones(e.target.value)} rows={3}
            placeholder="Notas para quien ejecuta este subproceso…"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
        </div>

        {(asignacion.fechaAsignacion || asignacion.fechaCompletado) && (
          <div className="text-xs text-gray-400 space-y-0.5">
            {asignacion.fechaAsignacion && <p>Asignado el {formatFechaHora(asignacion.fechaAsignacion)}</p>}
            {asignacion.fechaCompletado && <p>Completado el {formatFechaHora(asignacion.fechaCompletado)}</p>}
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
            <button type="submit" disabled={guardando || empleados.length === 0}
              className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
              {guardando ? 'Guardando...' : 'Guardar asignación'}
            </button>
          </div>
        </div>
      </form>

      {confirmarAccion && (
        <ConfirmModal
          titulo={
            confirmarAccion === 'completar' ? 'Marcar como completada' :
            confirmarAccion === 'desasignar' ? 'Quitar asignación' : 'Cancelar asignación'
          }
          danger={confirmarAccion === 'cancelar'}
          confirmLabel={confirmarAccion === 'completar' ? 'Completar' : confirmarAccion === 'desasignar' ? 'Desasignar' : 'Cancelar asignación'}
          cargando={guardando}
          onConfirmar={ejecutarAccion}
          onCancelar={() => setConfirmarAccion(null)}>
          <p className="text-sm text-gray-600">
            {confirmarAccion === 'completar' && 'Se marcará este subproceso como completado.'}
            {confirmarAccion === 'desasignar' && 'Se quitará la persona y fecha asignadas; el subproceso vuelve a Pendiente.'}
            {confirmarAccion === 'cancelar' && 'Este subproceso quedará cancelado. Esta acción no elimina el registro.'}
          </p>
        </ConfirmModal>
      )}
    </Modal>
  )
}

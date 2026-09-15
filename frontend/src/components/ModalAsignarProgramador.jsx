import { useState, useEffect } from 'react'
import asignacionService from '../services/asignacionService'
import { Modal } from './ui/Modal'

/** @param subprocesos array de {id, subproceso} a encargar — 1 desde la tarjeta puntual, varios
 *  desde el botón de la tabla principal (todos los pendientes de poligrafía/visita del servicio). */
export default function ModalAsignarProgramador({ subprocesos, onClose, onGuardado, onToast }) {
  const [programadores, setProgramadores] = useState(null)
  const [idProgramador, setIdProgramador] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    asignacionService.programadores()
      .then(setProgramadores)
      .catch(() => setError('No se pudo cargar la lista de programadores.'))
  }, [])

  const asignar = async () => {
    if (!idProgramador) { setError('Selecciona un programador.'); return }
    setGuardando(true)
    setError(null)
    try {
      await Promise.all(subprocesos.map(sp => asignacionService.asignarProgramador(sp.id, idProgramador)))
      onToast(subprocesos.length > 1
        ? `Se le encargaron ${subprocesos.length} subprocesos al programador.`
        : 'Se le encargó al programador.')
      onGuardado()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo asignar el programador.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Asignar programador"
      subtitulo={subprocesos.length === 1 ? subprocesos[0].subproceso : `${subprocesos.length} subprocesos pendientes`}
      onClose={onClose} ancho="max-w-md">
      <div className="px-6 py-5 space-y-4">
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg">{error}</div>
        )}

        <ul className="text-xs text-gray-500 space-y-0.5">
          {subprocesos.map(sp => <li key={sp.id}>• {sp.subproceso}</li>)}
        </ul>

        {programadores === null ? (
          <div className="flex justify-center py-6">
            <div className="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent" />
          </div>
        ) : programadores.length === 0 ? (
          <p className="text-sm text-amber-600">No hay programadores activos en el sistema.</p>
        ) : (
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Programador</label>
            <select value={idProgramador} onChange={e => setIdProgramador(e.target.value)}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
              <option value="">Selecciona…</option>
              {programadores.map(p => (
                <option key={p.idUsuario} value={p.idUsuario}>{p.nombre} {p.apellido ?? ''}</option>
              ))}
            </select>
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose}
            className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 rounded-lg transition-colors">
            Cancelar
          </button>
          <button type="button" onClick={asignar} disabled={guardando || !idProgramador}
            className="px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-lg hover:bg-primary-700 disabled:opacity-50 transition-colors">
            {guardando ? 'Asignando…' : 'Asignar'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

import { useEffect, useId, useState } from 'react'
import { Modal } from '../ui/Modal'
import SelectorMotivo, { motivoIncompleto, MOTIVO_VACIO } from '../SelectorMotivo'
import motivosService from '../../services/motivosService'
import asignacionService from '../../services/asignacionService'

// Acciones sobre una cita de poligrafía, visita o VerifEye después de agendada (SE-07). Siempre con motivo de la lista de reprogramación.
//   modo 'no-se-presento'  → el profesional registra que el evaluado no se presentó (solo pasados los 15 minutos; el motivo es fijo)
//   modo 'no-realizada'    → el programador o el gestor registra que la cita no se realizó (p. ej. el evaluado avisó que no asistirá)
//   modo 'reprogramar'     → mismo profesional, nueva fecha y hora
//   modo 'reasignar'       → otro profesional, con su fecha y hora
const TITULOS = {
  'no-se-presento': 'El evaluado no se presentó',
  'no-realizada': 'La cita no se realizó',
  reprogramar: 'Reprogramar la cita',
  reasignar: 'Reasignar la cita',
}
const BOTON = {
  'no-se-presento': 'Registrar que no se presentó',
  'no-realizada': 'Registrar cita no realizada',
  reprogramar: 'Reprogramar',
  reasignar: 'Reasignar',
}
const campoCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'

export default function ModalCitaAccion({ cita, modo, onClose, onHecho }) {
  const idCampos = useId()
  const [motivo, setMotivo] = useState(MOTIVO_VACIO)
  const [fecha, setFecha] = useState('')
  const [lugar, setLugar] = useState(cita.lugar ?? '')
  const [idProfesional, setIdProfesional] = useState('')
  const [profesionales, setProfesionales] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const [idMotivoFijo, setIdMotivoFijo] = useState(null)

  // «No se presentó»: el motivo es el que tiene el código NO_SE_PRESENTO (no depende de cómo lo llame el administrador).
  useEffect(() => {
    if (modo !== 'no-se-presento') return
    motivosService.activos('REPROGRAMACION')
      .then(l => setIdMotivoFijo(l.find(m => m.codigo === 'NO_SE_PRESENTO')?.id ?? null))
      .catch(() => setError('No se pudo cargar el motivo.'))
  }, [modo])

  useEffect(() => {
    if (modo !== 'reasignar') return
    asignacionService.empleadosDisponibles(cita.idTipoProgreso, cita.idCiudad)
      .then(l => setProfesionales(l.filter(p => p.idUsuario !== cita.idUsuarioAsignado)))
      .catch(() => setError('No se pudo cargar la lista de profesionales.'))
  }, [modo, cita.idTipoProgreso, cita.idCiudad, cita.idUsuarioAsignado])

  const enviar = async (e) => {
    e.preventDefault()
    setError(null)
    if (modo !== 'no-se-presento') {
      const falta = motivoIncompleto(motivo)
      if (falta) return setError(falta)
    } else if (!idMotivoFijo) {
      return setError('Falta el motivo «No se presentó» en la lista de motivos de reprogramación; avísale al administrador.')
    }
    if ((modo === 'reprogramar' || modo === 'reasignar') && !fecha) return setError('Indica la fecha y hora de la cita.')
    if (modo === 'reasignar' && !idProfesional) return setError('Elige al nuevo profesional.')
    setGuardando(true)
    try {
      const texto = motivo.texto?.trim() || null
      if (modo === 'no-se-presento') await asignacionService.citaNoRealizada(cita.id, { idMotivo: idMotivoFijo, texto: null })
      else if (modo === 'no-realizada') await asignacionService.citaNoRealizada(cita.id, { idMotivo: motivo.idMotivo, texto })
      else if (modo === 'reprogramar') await asignacionService.reprogramarCita(cita.id, { fechaProgramada: fecha, idMotivo: motivo.idMotivo, texto, lugar: lugar.trim() || null })
      else await asignacionService.reasignarCita(cita.id, { idUsuarioAsignado: Number(idProfesional), fechaProgramada: fecha, idMotivo: motivo.idMotivo, texto, lugar: lugar.trim() || null })
      onHecho(modo)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo completar la acción.')
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={TITULOS[modo]} subtitulo={`${cita.subproceso} — ${cita.nombresEvaluado ?? ''} ${cita.apellidosEvaluado ?? ''}`.trim()} onClose={onClose} ancho="max-w-lg">
      <form onSubmit={enviar} className="px-6 py-5 space-y-4">
        {error && <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        {modo === 'no-se-presento' ? (
          <p className="text-sm text-gray-700">
            Pasaron más de 15 minutos de la hora de la cita y el evaluado no llegó. La cita pasa a <strong>Reprogramado</strong>, sale de tu agenda y
            se le avisa al programador y al cliente.
          </p>
        ) : (
          <SelectorMotivo tipo="REPROGRAMACION" valor={motivo} onChange={setMotivo} etiqueta="Motivo" autoFocus />
        )}

        {(modo === 'reprogramar' || modo === 'reasignar') && (
          <>
            {modo === 'reasignar' && (
              <div>
                <label htmlFor={`${idCampos}-prof`} className="block text-xs font-medium text-gray-700 mb-1">Nuevo profesional <span className="text-red-600">*</span></label>
                <select id={`${idCampos}-prof`} value={idProfesional} onChange={e => setIdProfesional(e.target.value)} disabled={!profesionales} className={campoCls}>
                  <option value="">{profesionales ? 'Elige…' : 'Cargando…'}</option>
                  {profesionales?.map(p => (
                    <option key={p.idUsuario} value={p.idUsuario}>{p.nombre} {p.apellido ?? ''}{p.cubreLaCiudad ? ' · cubre la ciudad' : ''}</option>
                  ))}
                </select>
              </div>
            )}
            <div>
              <label htmlFor={`${idCampos}-fecha`} className="block text-xs font-medium text-gray-700 mb-1">Nueva fecha y hora <span className="text-red-600">*</span></label>
              <input id={`${idCampos}-fecha`} type="datetime-local" value={fecha} onChange={e => setFecha(e.target.value)} className={campoCls} />
            </div>
            <div>
              <label htmlFor={`${idCampos}-lugar`} className="block text-xs font-medium text-gray-700 mb-1">Lugar <span className="text-gray-500 font-normal">(opcional)</span></label>
              <input id={`${idCampos}-lugar`} value={lugar} onChange={e => setLugar(e.target.value)} maxLength={300} className={campoCls} />
            </div>
          </>
        )}

        <div className="flex justify-end gap-3 border-t border-gray-100 pt-4">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Guardando…' : BOTON[modo]}
          </button>
        </div>
      </form>
    </Modal>
  )
}

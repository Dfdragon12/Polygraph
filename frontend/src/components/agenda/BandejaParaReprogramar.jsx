import { useCallback, useEffect, useState } from 'react'
import agendaService from '../../services/agendaService'
import { useCambios } from '../../hooks/useCambios'
import EstadoTiempo from '../EstadoTiempo'
import { parseFecha } from '../../utils/fechas'

// «Para reprogramar» (SE-08): lo que estaba programado para un día que ya pasó y no se hizo («Atrasado en agenda», en días hábiles; un nombre
// distinto de «Vencida», que es la entrega al cliente). Sin esto la agenda de la semana salía vacía con decenas de tareas asignadas.
//  · Gestor (análisis): «Reprogramar» abre la asignación para fijar otra fecha.
//  · Programador (citas): «Registrar cita no realizada» (con motivo); luego la cita queda en «Por reprogramar».
const fechaCita = (iso) => iso
  ? parseFecha(iso).toLocaleString('es-CO', { weekday: 'short', day: '2-digit', month: 'short', hour: 'numeric', minute: '2-digit' })
  : 'Sin fecha'

export default function BandejaParaReprogramar({ rolUsuario, onReprogramar, onNoRealizada }) {
  const [lista, setLista] = useState(null)
  const [abierta, setAbierta] = useState(false)   // cerrada: solo el título y el conteo, para no empujar el semáforo ni la tabla
  const cargar = useCallback(() => {
    agendaService.paraReprogramar().then(setLista).catch(() => setLista([]))
  }, [])
  useEffect(() => { cargar() }, [cargar])
  useCambios(cargar)

  if (!lista?.length) return null
  return (
    <section className="bg-white rounded-xl border border-orange-300 shadow-sm overflow-hidden mb-5" aria-label="Para reprogramar">
      <button type="button" onClick={() => setAbierta(v => !v)} aria-expanded={abierta}
        className={`w-full px-4 py-3 flex items-center gap-3 text-left hover:bg-orange-50/50 transition-colors ${abierta ? 'border-b border-gray-100' : ''}`}>
        <h3 className="text-sm font-semibold text-gray-900">Para reprogramar</h3>
        <span className="text-xs font-semibold bg-gray-900 text-white rounded-full px-2 py-0.5">{lista.length}</span>
        <p className="text-xs text-gray-700 hidden sm:block flex-1 min-w-0 truncate">Estaba programado para un día que ya pasó y no se hizo.</p>
        <span className="ml-auto text-xs font-medium text-primary-700 flex-shrink-0">{abierta ? 'Ocultar' : 'Ver lista'}</span>
        <svg aria-hidden="true" className={`w-4 h-4 text-gray-500 flex-shrink-0 transition-transform ${abierta ? 'rotate-180' : ''}`} viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clipRule="evenodd" />
        </svg>
      </button>
      {abierta && <ul className="divide-y divide-gray-100 max-h-80 overflow-y-auto">
        {lista.map(a => (
          <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
            <div className="min-w-0">
              <p className="text-sm font-medium text-gray-900 truncate">{a.nombresEvaluado} {a.apellidosEvaluado ?? ''} · <span className="font-normal text-gray-700">{a.subproceso}</span></p>
              <p className="text-xs text-gray-700">
                Programado: {fechaCita(a.fechaProgramada)}{a.nombreAsignado ? ` · ${a.nombreAsignado}` : ''} · Servicio #{a.idServicio}
              </p>
              <div className="flex flex-wrap items-center gap-2 mt-1.5">
                <span className="text-[11px] font-semibold bg-orange-50 text-orange-800 ring-1 ring-orange-300 rounded-full px-2 py-0.5">
                  Atrasado en agenda · {a.diasHabilesAtrasoAgenda} {a.diasHabilesAtrasoAgenda === 1 ? 'día hábil' : 'días hábiles'}
                </span>
                {a.tiempo && <EstadoTiempo tiempo={a.tiempo} />}
              </div>
            </div>
            {rolUsuario === 'programador' ? (
              <button type="button" onClick={() => onNoRealizada(a)}
                className="text-xs font-medium px-3 py-1.5 rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50 transition-colors">Registrar cita no realizada</button>
            ) : (
              <button type="button" onClick={() => onReprogramar(a)}
                className="text-xs font-medium px-3 py-1.5 rounded-lg bg-primary-600 text-white hover:bg-primary-700 transition-colors">Reprogramar</button>
            )}
          </li>
        ))}
      </ul>}
    </section>
  )
}

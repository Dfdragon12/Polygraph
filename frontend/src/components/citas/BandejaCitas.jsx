import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import asignacionService from '../../services/asignacionService'
import { useCambios } from '../../hooks/useCambios'
import Toast from '../Toast'
import ModalCitaAccion from './ModalCitaAccion'

// Las bandejas de citas (SE-07), siempre calculadas en el servidor:
//  · «Por confirmar»: pasaron 15 minutos de la hora de la cita y nadie inició el informe ni registró qué pasó.
//  · «Por reprogramar» (programador): citas que no se realizaron, a la espera de reprogramarse o reasignarse.
// rol: 'profesional' (poligrafista o visitador: ve sus citas), 'programador' o 'gestor'. prefijo: ruta del módulo del profesional (para abrir su informe).

const fechaHora = (iso) => iso
  ? new Date(iso).toLocaleString('es-CO', { weekday: 'short', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
  : 'Sin fecha'

const botonCls = 'text-xs font-medium px-3 py-1.5 rounded-lg border transition-colors'

function Fila({ a, children }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-900 truncate">{a.nombresEvaluado} {a.apellidosEvaluado ?? ''} · <span className="text-gray-600 font-normal">{a.subproceso}</span></p>
        <p className="text-xs text-gray-600">
          Cita: {fechaHora(a.fechaProgramada)}{a.nombreAsignado ? ` · ${a.nombreAsignado}` : ''}{a.lugar ? ` · ${a.lugar}` : ''} · Servicio #{a.idServicio}
        </p>
      </div>
      <div className="flex flex-wrap gap-2">{children}</div>
    </li>
  )
}

function Panel({ titulo, detalle, cantidad, tono, children }) {
  return (
    <section className={`bg-white rounded-xl border shadow-sm overflow-hidden ${tono}`} aria-label={titulo}>
      <header className="px-4 py-3 border-b border-gray-100 flex items-center gap-3">
        <h3 className="text-sm font-semibold text-gray-900">{titulo}</h3>
        <span className="text-xs font-semibold bg-gray-900 text-white rounded-full px-2 py-0.5">{cantidad}</span>
        <p className="text-xs text-gray-600 hidden sm:block">{detalle}</p>
      </header>
      <ul className="divide-y divide-gray-100">{children}</ul>
    </section>
  )
}

export default function BandejaCitas({ rol, prefijo, onCambio }) {
  const navigate = useNavigate()
  const [porConfirmar, setPorConfirmar] = useState(null)
  const [porReprogramar, setPorReprogramar] = useState([])
  const [accion, setAccion] = useState(null)       // { cita, modo }
  const [toast, setToast] = useState(null)
  const [ocupado, setOcupado] = useState(null)

  const cargar = useCallback(() => {
    asignacionService.citasPorConfirmar().then(setPorConfirmar).catch(() => setPorConfirmar([]))
    if (rol === 'programador') {
      asignacionService.listar({ estado: 'REPROGRAMADO', size: 50, sort: 'fechaProgramada,asc' })
        .then(r => setPorReprogramar(r.content ?? [])).catch(() => setPorReprogramar([]))
    }
  }, [rol])

  useEffect(() => { cargar() }, [cargar])
  useCambios(cargar)

  const hecho = (modo) => {
    setAccion(null)
    setToast({ mensaje: { 'no-se-presento': 'Registrado: la cita pasó a Reprogramado.', 'no-realizada': 'Registrado: la cita pasó a Reprogramado.',
      reprogramar: 'Cita reprogramada.', reasignar: 'Cita reasignada.' }[modo], tipo: 'exito' })
    cargar()
    onCambio?.()
  }

  const iniciarInforme = async (a) => {
    setOcupado(a.id)
    try {
      await asignacionService.iniciarInforme(a.id)
      onCambio?.()
      navigate(`/${prefijo}/formulario/${a.id}`)
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo iniciar el informe.', tipo: 'error' })
      setOcupado(null)
    }
  }

  const hayAlgo = (porConfirmar?.length ?? 0) > 0 || porReprogramar.length > 0
  if (!hayAlgo) return toast ? <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} /> : null

  return (
    <div className="space-y-4 mb-5">
      {porConfirmar?.length > 0 && (
        <Panel titulo="Por confirmar" cantidad={porConfirmar.length} tono="border-amber-300"
          detalle="Pasaron 15 minutos de la hora de la cita y no hay registro de qué pasó.">
          {porConfirmar.map(a => (
            <Fila key={a.id} a={a}>
              {rol === 'profesional' ? (
                <>
                  <button type="button" disabled={ocupado === a.id} onClick={() => iniciarInforme(a)}
                    className={`${botonCls} bg-primary-600 text-white border-primary-600 hover:bg-primary-700 disabled:opacity-60`}>Se realizó: iniciar informe</button>
                  <button type="button" onClick={() => setAccion({ cita: a, modo: 'no-se-presento' })}
                    className={`${botonCls} border-gray-300 text-gray-700 hover:bg-gray-50`}>No se presentó</button>
                </>
              ) : (
                <button type="button" onClick={() => setAccion({ cita: a, modo: 'no-realizada' })}
                  className={`${botonCls} border-gray-300 text-gray-700 hover:bg-gray-50`}>Registrar cita no realizada</button>
              )}
            </Fila>
          ))}
        </Panel>
      )}

      {porReprogramar.length > 0 && (
        <Panel titulo="Por reprogramar" cantidad={porReprogramar.length} tono="border-orange-300"
          detalle="La cita no se realizó: reprográmala con el mismo profesional o reasígnala a otro. El reloj del servicio está en pausa.">
          {porReprogramar.map(a => (
            <Fila key={a.id} a={a}>
              <button type="button" onClick={() => setAccion({ cita: a, modo: 'reprogramar' })}
                className={`${botonCls} bg-primary-600 text-white border-primary-600 hover:bg-primary-700`}>Reprogramar</button>
              <button type="button" onClick={() => setAccion({ cita: a, modo: 'reasignar' })}
                className={`${botonCls} border-gray-300 text-gray-700 hover:bg-gray-50`}>Reasignar</button>
            </Fila>
          ))}
        </Panel>
      )}

      {accion && <ModalCitaAccion cita={accion.cita} modo={accion.modo} onClose={() => setAccion(null)} onHecho={hecho} />}
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

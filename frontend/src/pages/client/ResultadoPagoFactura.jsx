import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import pospagoService from '../../services/pospagoService'
import { formatearPrecio } from '../../utils/catalogoDisplay'

const INTERVALO_MS = 2000
const MAX_INTENTOS = 20

function formatFecha(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

const ETIQUETA_METODO_PAGO = {
  CARD: 'Tarjeta',
  NEQUI: 'Nequi',
  DAVIPLATA: 'Daviplata',
  PSE: 'PSE',
  BANCOLOMBIA_TRANSFER: 'Transferencia Bancolombia',
  BANCOLOMBIA_QR: 'QR Bancolombia',
}

function FilaInfo({ label, valor }) {
  if (!valor) return null
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-gray-400">{label}</span>
      <span className="font-medium text-gray-700 truncate" title={valor}>{valor}</span>
    </div>
  )
}

export default function ResultadoPagoFactura() {
  const { referencia } = useParams()
  const navigate = useNavigate()
  const [pago, setPago] = useState(null)
  const [factura, setFactura] = useState(null)
  const [movimientos, setMovimientos] = useState([])
  const [error, setError] = useState(null)
  const intentos = useRef(0)
  const facturaSolicitada = useRef(null)

  useEffect(() => {
    let cancelado = false
    let timeoutId

    const consultar = async () => {
      try {
        const data = await pospagoService.obtenerEstadoPago(referencia)
        if (cancelado) return
        setPago(data)

        if (data.estado === 'PENDIENTE' && intentos.current < MAX_INTENTOS) {
          intentos.current += 1
          timeoutId = setTimeout(consultar, INTERVALO_MS)
        }
      } catch {
        if (!cancelado) setError('No se pudo consultar el estado del pago.')
      }
    }

    consultar()
    return () => { cancelado = true; clearTimeout(timeoutId) }
  }, [referencia])

  // La factura (ítems facturados) se carga una sola vez, en cuanto sabemos a cuál apunta el pago —
  // no depende de que el pago ya esté aprobado, así el desglose se ve también en estado pendiente/rechazado.
  useEffect(() => {
    if (!pago?.facturaOConceptoId || facturaSolicitada.current === pago.facturaOConceptoId) return
    facturaSolicitada.current = pago.facturaOConceptoId
    pospagoService.obtenerFactura(pago.facturaOConceptoId)
      .then((data) => { setFactura(data.factura); setMovimientos(data.movimientos ?? []) })
      .catch(() => {})
  }, [pago])

  if (error) {
    return (
      <div className="max-w-md mx-auto flex flex-col items-center justify-center h-64 gap-3 text-center">
        <p className="text-red-500 text-sm">{error}</p>
        <Link to="/cliente/pospago" className="text-sm text-primary-600 hover:underline">Volver a Mi Crédito</Link>
      </div>
    )
  }

  if (!pago || (pago.estado === 'PENDIENTE' && intentos.current < MAX_INTENTOS)) {
    return (
      <div className="max-w-md mx-auto flex flex-col items-center justify-center h-64 gap-3 text-center">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
        <p className="text-sm text-gray-500">Confirmando el estado de tu pago...</p>
      </div>
    )
  }

  const aprobado = pago.estado === 'APROBADO'
  const rechazado = pago.estado === 'RECHAZADO' || pago.estado === 'ANULADO' || pago.estado === 'ERROR'

  const iconoCfg = aprobado
    ? { fondo: 'bg-green-100', color: 'text-green-600', path: 'M5 13l4 4L19 7' }
    : rechazado
    ? { fondo: 'bg-red-100', color: 'text-red-500', path: 'M6 18L18 6M6 6l12 12' }
    : { fondo: 'bg-amber-100', color: 'text-amber-600', path: 'M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z' }

  return (
    <div className="max-w-md mx-auto space-y-6 text-center">
      <div className={`w-16 h-16 mx-auto rounded-full flex items-center justify-center ${iconoCfg.fondo}`}>
        <svg className={`h-8 w-8 ${iconoCfg.color}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d={iconoCfg.path} />
        </svg>
      </div>

      <div>
        <h2 className="text-xl font-bold text-gray-800">
          {aprobado ? 'Pago aprobado' : rechazado ? 'Pago rechazado' : 'Pago pendiente'}
        </h2>
        <p className="text-sm text-gray-500 mt-1">
          {aprobado
            ? 'Tu factura quedó pagada y tu cupo de crédito se restituyó.'
            : rechazado
            ? 'No pudimos procesar el pago de esta factura.'
            : 'Todavía no hemos recibido la confirmación de este pago.'}
        </p>
      </div>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 text-left space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-gray-800">Factura {factura?.referencia ?? pago.referencia}</p>
            {factura?.fechaGeneracion && (
              <p className="text-xs text-gray-400 mt-0.5">{formatFecha(factura.fechaGeneracion)}</p>
            )}
          </div>
          <span className="flex-shrink-0 text-xs font-medium px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">Wompi</span>
        </div>

        <div className="bg-gray-50 rounded-lg px-3 py-2.5 text-xs space-y-1.5">
          <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Información de la transacción</p>
          <FilaInfo label="Referencia de pago" valor={pago.referencia} />
          <FilaInfo label="ID de transacción" valor={pago.idTransaccionPasarela} />
          <FilaInfo label="Número de aprobación" valor={pago.numeroAprobacion} />
          <FilaInfo label="Método de pago" valor={ETIQUETA_METODO_PAGO[pago.metodoPago] ?? pago.metodoPago} />
        </div>

        {(pago.nombrePagador || pago.telefonoPagador || pago.emailPagador) && (
          <div className="bg-gray-50 rounded-lg px-3 py-2.5 text-xs space-y-1.5">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Información del pagador</p>
            <FilaInfo label="Nombre" valor={pago.nombrePagador} />
            <FilaInfo label="Teléfono" valor={pago.telefonoPagador} />
            <FilaInfo label="Email" valor={pago.emailPagador} />
          </div>
        )}

        {movimientos.length > 0 && (
          <ul className="divide-y divide-gray-100">
            {movimientos.map((m) => (
              <li key={m.idMovimiento} className="py-2.5 flex items-center justify-between text-sm gap-3">
                <div className="min-w-0">
                  <p className="text-gray-700 truncate">{m.nombreProceso}</p>
                  <p className="text-xs text-gray-400 mt-0.5 truncate">{m.nombreCandidato} · {m.nombreCiudad}</p>
                </div>
                <div className="text-right flex-shrink-0">
                  {m.montoRecargoCiudad > 0 && (
                    <p className="text-xs text-red-500">+ {formatearPrecio(m.montoRecargoCiudad)} recargo</p>
                  )}
                  <span className="font-medium text-gray-800">{formatearPrecio(m.montoTotal)}</span>
                </div>
              </li>
            ))}
          </ul>
        )}

        <div className="flex items-center justify-between border-t border-gray-100 pt-3">
          <span className="text-sm font-semibold text-gray-600">Total</span>
          <span className="text-lg font-bold text-primary-700">{formatearPrecio(pago.montoEnCentavos / 100)}</span>
        </div>
      </div>

      <button onClick={() => navigate('/cliente/pospago')}
        className="w-full bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium py-2.5 rounded-lg transition-colors">
        Volver a Mi Crédito
      </button>
    </div>
  )
}

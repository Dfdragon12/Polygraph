import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import pagosService from '../../services/pagosService'
import { formatearPrecio } from '../../utils/catalogoDisplay'

const INTERVALO_MS = 2000
const MAX_INTENTOS = 20

export default function ResultadoPago() {
  const { idOrden } = useParams()
  const navigate = useNavigate()
  const [orden, setOrden] = useState(null)
  const [error, setError] = useState(null)
  const [reintentando, setReintentando] = useState(false)
  const intentos = useRef(0)

  useEffect(() => {
    let cancelado = false
    let timeoutId

    const consultar = async () => {
      try {
        const data = await pagosService.obtenerOrden(idOrden)
        if (cancelado) return
        setOrden(data)

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
  }, [idOrden])

  const reintentarPago = async () => {
    setReintentando(true)
    try {
      const pago = await pagosService.iniciarPago(idOrden)
      window.location.href = pago.urlCheckout
    } catch {
      setError('No se pudo reiniciar el pago.')
      setReintentando(false)
    }
  }

  if (error) {
    return (
      <div className="max-w-md mx-auto flex flex-col items-center justify-center h-64 gap-3 text-center">
        <p className="text-red-500 text-sm">{error}</p>
        <Link to="/cliente/comprar-servicios" className="text-sm text-primary-600 hover:underline">
          Volver a Comprar Servicios
        </Link>
      </div>
    )
  }

  if (!orden || (orden.estado === 'PENDIENTE' && intentos.current < MAX_INTENTOS)) {
    return (
      <div className="max-w-md mx-auto flex flex-col items-center justify-center h-64 gap-3 text-center">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
        <p className="text-sm text-gray-500">Confirmando el estado de tu pago...</p>
      </div>
    )
  }

  const aprobada = orden.estado === 'APROBADA'

  return (
    <div className="max-w-md mx-auto space-y-6 text-center">
      <div className={`w-16 h-16 mx-auto rounded-full flex items-center justify-center ${aprobada ? 'bg-green-100' : 'bg-red-100'}`}>
        <svg className={`h-8 w-8 ${aprobada ? 'text-green-600' : 'text-red-500'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          {aprobada
            ? <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            : <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />}
        </svg>
      </div>

      <div>
        <h2 className="text-xl font-bold text-gray-800">{aprobada ? 'Pago aprobado' : 'Pago rechazado'}</h2>
        <p className="text-sm text-gray-500 mt-1">
          {aprobada
            ? 'Tu compra fue acreditada a tu bolsa de servicios prepago.'
            : 'No pudimos procesar el pago de esta orden.'}
        </p>
      </div>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 text-left space-y-3">
        <p className="text-xs text-gray-400">Orden {orden.referencia}</p>
        <ul className="divide-y divide-gray-100">
          {orden.items.map((i) => (
            <li key={i.idProceso} className="py-2 flex items-center justify-between text-sm gap-3">
              <span className="text-gray-700">{i.cantidad} × {i.nombreProceso}</span>
              <span className="font-medium text-gray-800 flex-shrink-0">{formatearPrecio(i.subtotal)}</span>
            </li>
          ))}
        </ul>
        <div className="flex items-center justify-between border-t border-gray-100 pt-3">
          <span className="text-sm font-semibold text-gray-600">Total</span>
          <span className="text-lg font-bold text-primary-700">{formatearPrecio(orden.montoTotal)}</span>
        </div>
      </div>

      {aprobada ? (
        <button onClick={() => navigate('/cliente/comprar-servicios')}
          className="w-full bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium py-2.5 rounded-lg transition-colors">
          Volver a Comprar Servicios
        </button>
      ) : (
        <div className="flex flex-col gap-3">
          <button onClick={reintentarPago} disabled={reintentando}
            className="w-full bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium py-2.5 rounded-lg transition-colors">
            {reintentando ? 'Redirigiendo...' : 'Reintentar pago'}
          </button>
          <Link to="/cliente/comprar-servicios" className="text-sm text-gray-500 hover:text-gray-700">
            Volver a Comprar Servicios
          </Link>
        </div>
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import pagosService from '../../services/pagosService'
import { formatearPrecio } from '../../utils/catalogoDisplay'

export default function SimuladorPago() {
  const { idOrden } = useParams()
  const navigate = useNavigate()
  const [orden, setOrden] = useState(null)
  const [error, setError] = useState(null)
  const [procesando, setProcesando] = useState(false)

  useEffect(() => {
    pagosService.obtenerOrden(idOrden)
      .then((data) => {
        if (!data.modoSimulado) {
          navigate(`/cliente/comprar-servicios/resultado/${idOrden}`, { replace: true })
          return
        }
        setOrden(data)
      })
      .catch(() => setError('No se pudo cargar la orden.'))
  }, [idOrden, navigate])

  const simular = async (resultado) => {
    setProcesando(true)
    try {
      await pagosService.simularPago(idOrden, resultado)
      navigate(`/cliente/comprar-servicios/resultado/${idOrden}`)
    } catch {
      setError('No se pudo procesar la simulación de pago.')
      setProcesando(false)
    }
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-2">
        <p className="text-red-500 text-sm">{error}</p>
      </div>
    )
  }

  if (!orden) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  return (
    <div className="max-w-md mx-auto space-y-6">
      <div className="text-center space-y-1">
        <span className="inline-block text-xs font-semibold px-2.5 py-1 rounded-full bg-amber-100 text-amber-700 mb-2">
          Simulador de pago — sin llaves de Wompi configuradas
        </span>
        <h2 className="text-xl font-bold text-gray-800">Simular pago</h2>
        <p className="text-sm text-gray-500">Orden {orden.referencia}</p>
      </div>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 space-y-3">
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

      <div className="flex flex-col gap-3">
        <button onClick={() => simular('APROBADO')} disabled={procesando}
          className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-sm font-medium py-3 rounded-lg transition-colors">
          {procesando ? 'Procesando...' : 'Simular pago aprobado'}
        </button>
        <button onClick={() => simular('RECHAZADO')} disabled={procesando}
          className="bg-white hover:bg-red-50 disabled:opacity-60 text-red-600 border border-red-200 text-sm font-medium py-3 rounded-lg transition-colors">
          {procesando ? 'Procesando...' : 'Simular pago rechazado'}
        </button>
      </div>
    </div>
  )
}

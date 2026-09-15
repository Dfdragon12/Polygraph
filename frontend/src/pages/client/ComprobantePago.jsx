import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import pagosService from '../../services/pagosService'
import ComprobanteVista from '../../components/ComprobanteVista'

export default function ComprobantePago() {
  const { idComprobante } = useParams()
  const [comprobante, setComprobante] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    pagosService.obtenerComprobante(idComprobante)
      .then(setComprobante)
      .catch(() => setError('No se pudo cargar el comprobante.'))
  }, [idComprobante])

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

  if (!comprobante) {
    return (
      <div className="max-w-md mx-auto flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  return <ComprobanteVista comprobante={comprobante} volverA="/cliente/comprar-servicios" volverLabel="Volver a Comprar Servicios" />
}

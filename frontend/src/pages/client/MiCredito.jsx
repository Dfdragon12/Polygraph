import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import pospagoService from '../../services/pospagoService'
import { formatearPrecio } from '../../utils/catalogoDisplay'

const ESTADO_ESTILO = {
  PENDIENTE: 'bg-amber-50 text-amber-700 border-amber-200',
  PAGADA: 'bg-green-50 text-green-700 border-green-200',
  VENCIDA: 'bg-red-50 text-red-700 border-red-200',
}

const ESTADO_LABEL = {
  PENDIENTE: 'Pendiente',
  PAGADA: 'Pagada',
  VENCIDA: 'Vencida',
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

export default function MiCredito() {
  const [estadoCuenta, setEstadoCuenta] = useState(null)
  const [facturas, setFacturas] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    Promise.all([pospagoService.obtenerEstadoCuenta(), pospagoService.listarFacturas()])
      .then(([cuenta, listaFacturas]) => {
        setEstadoCuenta(cuenta)
        setFacturas(listaFacturas)
      })
      .catch(() => setError('No se pudo cargar tu estado de cuenta.'))
      .finally(() => setCargando(false))
  }, [])

  if (cargando) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
    </div>
  )

  if (error || !estadoCuenta) return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <p className="text-red-500 text-sm">{error || 'No se pudo cargar tu información.'}</p>
    </div>
  )

  const { limiteCredito, creditoDisponible, consumidoSinFacturar, estadoMora } = estadoCuenta
  const consumidoTotal = Math.max((limiteCredito ?? 0) - (creditoDisponible ?? 0), 0)
  const porcentajeUsado = limiteCredito > 0 ? Math.min((consumidoTotal / limiteCredito) * 100, 100) : 0

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 className="text-xl font-bold text-gray-800">Mi Crédito</h1>
        <p className="text-sm text-gray-500 mt-1">Tu cupo de crédito pospago y las facturas de tus servicios.</p>
      </div>

      {estadoMora && estadoMora !== 'NORMAL' && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">
          {estadoMora === 'SUSPENDIDO'
            ? 'Tu cuenta está suspendida por mora — no puedes solicitar nuevos servicios hasta regularizar tus facturas pendientes.'
            : 'Tu cuenta está en mora — tienes facturas vencidas sin pagar. Regulariza tu situación para seguir solicitando servicios.'}
        </div>
      )}

      {/* Tarjeta de crédito */}
      <div className="bg-gradient-to-br from-primary-700 to-primary-900 rounded-2xl p-6 text-white shadow-lg space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-xs uppercase tracking-wide text-primary-200">Cupo de crédito</span>
          <span className="text-2xl">💳</span>
        </div>
        <p className="text-3xl font-bold">{formatearPrecio(creditoDisponible)}</p>
        <p className="text-xs text-primary-200">disponible de {formatearPrecio(limiteCredito)}</p>

        <div className="w-full h-2 bg-white/20 rounded-full overflow-hidden">
          <div className="h-full bg-white rounded-full transition-all" style={{ width: `${porcentajeUsado}%` }} />
        </div>
        <div className="flex items-center justify-between text-xs text-primary-200">
          <span>Consumido: {formatearPrecio(consumidoTotal)}</span>
          {consumidoSinFacturar > 0 && <span>Sin facturar: {formatearPrecio(consumidoSinFacturar)}</span>}
        </div>
      </div>

      {/* Facturas */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
        <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-4">Facturas</h2>
        {facturas.length === 0 ? (
          <p className="text-sm text-gray-400 py-6 text-center">Todavía no tienes facturas generadas.</p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {facturas.map((f) => (
              <li key={f.idFactura} className="py-3 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <Link to={`/cliente/pospago/facturas/${f.idFactura}`} className="text-sm font-medium text-gray-800 hover:text-primary-600">
                    {f.referencia}
                  </Link>
                  <p className="text-xs text-gray-400 mt-0.5">
                    Corte: {formatFecha(f.fechaCorte)} · Vence: {formatFecha(f.fechaVencimiento)}
                  </p>
                </div>
                <div className="text-right flex-shrink-0 flex items-center gap-3">
                  <span className="font-semibold text-gray-800">{formatearPrecio(f.montoTotal)}</span>
                  <span className={`text-xs font-medium px-2 py-0.5 rounded-full border ${ESTADO_ESTILO[f.estado] ?? ''}`}>
                    {ESTADO_LABEL[f.estado] ?? f.estado}
                  </span>
                  <Link to={`/cliente/pospago/facturas/${f.idFactura}`}
                    className="text-xs font-medium text-primary-600 hover:underline flex-shrink-0">
                    Ver
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

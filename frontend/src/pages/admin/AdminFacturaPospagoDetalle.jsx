import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import pospagoService from '../../services/pospagoService'

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function formatearPrecio(valor) {
  if (valor == null) return '—'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor)
}

const ESTADO_CFG = {
  PENDIENTE: { label: 'Pendiente', badge: 'bg-amber-100 text-amber-700' },
  PAGADA:    { label: 'Pagada',    badge: 'bg-green-100 text-green-700' },
  VENCIDA:   { label: 'Vencida',   badge: 'bg-red-100 text-red-600' },
}

function formatFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}

const ESTADO_PAGO_ESTILO = {
  PENDIENTE: 'bg-amber-50 text-amber-700',
  APROBADO: 'bg-green-50 text-green-700',
  RECHAZADO: 'bg-red-50 text-red-700',
  ANULADO: 'bg-gray-100 text-gray-500',
  ERROR: 'bg-red-50 text-red-700',
}

export default function AdminFacturaPospagoDetalle() {
  const { idFactura } = useParams()
  const [detalle, setDetalle] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    pospagoService.adminObtenerFactura(idFactura)
      .then(setDetalle)
      .catch(() => setError('No se pudo cargar la factura.'))
  }, [idFactura])

  if (error) return (
    <div className="max-w-2xl mx-auto flex flex-col items-center justify-center h-64 gap-3 text-center">
      <p className="text-red-500 text-sm">{error}</p>
      <Link to="/admin/facturacion" className="text-sm text-primary-600 hover:underline">← Volver a Facturación</Link>
    </div>
  )

  if (!detalle) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
    </div>
  )

  const { factura, movimientos, intentosPago } = detalle
  const cfg = ESTADO_CFG[factura.estado] ?? { label: factura.estado, badge: 'bg-gray-100 text-gray-500' }

  return (
    <div className="max-w-2xl mx-auto space-y-4">
      <Link to={`/admin/facturacion/clientes/${factura.idCliente}`} className="text-sm text-gray-500 hover:text-gray-700 flex items-center gap-1.5">
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
        Facturación del cliente
      </Link>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8 space-y-6">
        <div className="flex items-start justify-between border-b border-gray-100 pb-5">
          <div>
            <p className="text-xs text-gray-400 uppercase tracking-wide">Factura pospago</p>
            <p className="text-2xl font-bold text-gray-900 mt-0.5">{factura.referencia}</p>
            <p className="text-sm text-gray-600 mt-1">{factura.nombreCliente}</p>
            <p className="text-xs text-gray-400 mt-1">
              Corte: {formatFecha(factura.fechaCorte)} · Vence: {formatFecha(factura.fechaVencimiento)}
              {factura.fechaPago && <> · Pagada: {formatFecha(factura.fechaPago)}</>}
            </p>
          </div>
          <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${cfg.badge}`}>{cfg.label}</span>
        </div>

        <div>
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">Servicios incluidos</p>
          <div className="overflow-x-auto">
          <table className="table-stack w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left text-xs text-gray-400">
                <th className="py-2 font-medium">Servicio</th>
                <th className="py-2 font-medium">Candidato</th>
                <th className="py-2 font-medium">Ciudad</th>
                <th className="py-2 font-medium text-right">Valor</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {movimientos.map((m) => (
                <tr key={m.idMovimiento}>
                  <td data-label="Servicio" className="py-2.5 text-gray-700">{m.nombreProceso}</td>
                  <td data-label="Candidato" className="py-2.5 text-gray-600">{m.nombreCandidato}</td>
                  <td data-label="Ciudad" className="py-2.5 text-gray-600">{m.nombreCiudad ?? '—'}</td>
                  <td data-label="Valor" className="py-2.5 text-right">
                    <span className="font-medium text-gray-800">{formatearPrecio(m.montoTotal)}</span>
                    {m.montoRecargoCiudad > 0 && (
                      <p className="text-[11px] text-gray-400">
                        {formatearPrecio(m.montoBase)} + {formatearPrecio(m.montoRecargoCiudad)} recargo
                      </p>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </div>

        <div className="flex items-center justify-between border-t border-gray-100 pt-4">
          <span className="text-sm font-semibold text-gray-600">Total</span>
          <span className="text-xl font-bold text-primary-700">{formatearPrecio(factura.montoTotal)}</span>
        </div>

        {intentosPago?.length > 0 && (
          <div className="border-t border-gray-100 pt-4">
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">Intentos de pago (referencia Wompi)</p>
            <ul className="space-y-1.5">
              {intentosPago.map((p) => (
                <li key={p.referencia} className="flex items-center justify-between gap-2 text-xs">
                  <span className="font-mono text-gray-600 truncate">{p.referencia}</span>
                  <span className="flex items-center gap-2 flex-shrink-0">
                    <span className="text-gray-400">{formatFechaHora(p.fechaActualizacion)}</span>
                    <span className={`px-1.5 py-0.5 rounded-full font-medium ${ESTADO_PAGO_ESTILO[p.estado] ?? 'bg-gray-100 text-gray-500'}`}>
                      {p.estado}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  )
}

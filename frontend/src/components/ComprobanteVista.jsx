import { Link } from 'react-router-dom'
import { formatearPrecio } from '../utils/catalogoDisplay'

function formatFecha(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

/** Vista de un comprobante de compra — compartida entre el portal cliente y el admin.
 * No es una factura electrónica DIAN válida (ver backend ComprobanteCompra). */
export default function ComprobanteVista({ comprobante, volverA, volverLabel = 'Volver' }) {
  return (
    <div className="max-w-2xl mx-auto space-y-4">
      {/* Acciones — no se imprimen */}
      <div className="print:hidden flex items-center justify-between">
        <Link to={volverA} className="text-sm text-gray-500 hover:text-gray-700 flex items-center gap-1.5">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          {volverLabel}
        </Link>
        <button onClick={() => window.print()}
          className="flex items-center gap-1.5 text-sm font-medium px-4 py-2 rounded-lg bg-primary-600 hover:bg-primary-700 text-white transition-colors">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M6.72 13.829c-.24.03-.48.062-.72.096m.72-.096a42.415 42.415 0 0110.56 0m-10.56 0L6.34 18m10.94-4.171c.24.03.48.062.72.096m-.72-.096L17.66 18m0 0l.229 2.523a1.125 1.125 0 01-1.12 1.227H7.231c-.662 0-1.18-.568-1.12-1.227L6.34 18m11.318 0h1.091A2.25 2.25 0 0021 15.75V9.456c0-1.081-.768-2.015-1.837-2.175a48.055 48.055 0 00-1.913-.247M6.34 18H5.25A2.25 2.25 0 013 15.75V9.456c0-1.081.768-2.015 1.837-2.175a48.041 48.041 0 011.913-.247m10.5 0a48.536 48.536 0 00-10.5 0m10.5 0V3.375c0-.621-.504-1.125-1.125-1.125h-8.25c-.621 0-1.125.504-1.125 1.125v3.659M18 10.5h.008v.008H18V10.5zm-3 0h.008v.008H15V10.5z" />
          </svg>
          Imprimir / Guardar PDF
        </button>
      </div>

      {/* Aviso — no es factura DIAN, sí se imprime, para que quede claro en el papel */}
      {comprobante.esDePrueba && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 text-xs text-amber-700">
          Este es un <strong>comprobante interno de prueba</strong> — no es una factura electrónica
          válida ante la DIAN. La facturación electrónica real se habilitará más adelante.
        </div>
      )}

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8 space-y-6">
        <div className="flex items-start justify-between border-b border-gray-100 pb-5">
          <div>
            <p className="text-xs text-gray-400 uppercase tracking-wide">Comprobante de compra</p>
            <p className="text-2xl font-bold text-gray-900 mt-0.5">{comprobante.numero}</p>
          </div>
          <div className="text-right">
            <p className="text-xs text-gray-400">Fecha de emisión</p>
            <p className="text-sm font-medium text-gray-700 mt-0.5">{formatFecha(comprobante.fechaEmision)}</p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-6 text-sm">
          <div>
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1">Cliente</p>
            {comprobante.nombreCliente && <p className="text-gray-700 font-medium">{comprobante.nombreCliente}</p>}
            {comprobante.nitCliente && <p className="text-gray-500 text-xs mt-0.5">NIT/Cédula: {comprobante.nitCliente}</p>}
            {comprobante.emailCliente && <p className="text-gray-500 text-xs mt-0.5">{comprobante.emailCliente}</p>}
            {comprobante.telefonoCliente && <p className="text-gray-500 text-xs mt-0.5">{comprobante.telefonoCliente}</p>}
            {(comprobante.direccionCliente || comprobante.ciudadCliente) && (
              <p className="text-gray-500 text-xs mt-0.5">
                {[comprobante.direccionCliente, comprobante.ciudadCliente].filter(Boolean).join(', ')}
              </p>
            )}
          </div>
          <div>
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1">Orden asociada</p>
            <p className="text-gray-700">{comprobante.referenciaOrden}</p>
            {(comprobante.nombrePagador || comprobante.emailPagador || comprobante.telefonoPagador) && (
              <div className="mt-3">
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1">Pagador</p>
                {comprobante.nombrePagador && <p className="text-gray-700">{comprobante.nombrePagador}</p>}
                {comprobante.emailPagador && <p className="text-gray-400 text-xs mt-0.5">{comprobante.emailPagador}</p>}
                {comprobante.telefonoPagador && <p className="text-gray-400 text-xs mt-0.5">{comprobante.telefonoPagador}</p>}
              </div>
            )}
          </div>
        </div>

        <div>
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">Servicios</p>
          <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left text-xs text-gray-400">
                <th className="py-2 font-medium">Servicio</th>
                <th className="py-2 font-medium text-right">Cant.</th>
                <th className="py-2 font-medium text-right">Valor unitario</th>
                <th className="py-2 font-medium text-right">Descuento</th>
                <th className="py-2 font-medium text-right">Subtotal</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {comprobante.items.map((i) => {
                const conDescuento = i.valorOriginal != null && i.valorOriginal > i.valorUnitario
                const ahorroItem = conDescuento ? (i.valorOriginal - i.valorUnitario) * i.cantidad : 0
                return (
                  <tr key={i.idProceso}>
                    <td className="py-2.5 text-gray-700">{i.nombreProceso}</td>
                    <td className="py-2.5 text-right text-gray-600">{i.cantidad}</td>
                    <td className="py-2.5 text-right text-gray-600">
                      {conDescuento && (
                        <span className="line-through text-gray-300 mr-1.5">{formatearPrecio(i.valorOriginal)}</span>
                      )}
                      {formatearPrecio(i.valorUnitario)}
                    </td>
                    <td className="py-2.5 text-right">
                      {conDescuento ? (
                        <div>
                          <span className="text-green-600 font-medium">− {formatearPrecio(ahorroItem)}</span>
                          {i.nombreDescuentoAplicado && (
                            <p className="text-[11px] text-green-500 mt-0.5">{i.nombreDescuentoAplicado}</p>
                          )}
                        </div>
                      ) : (
                        <span className="text-gray-300">—</span>
                      )}
                    </td>
                    <td className="py-2.5 text-right font-medium text-gray-800">{formatearPrecio(i.subtotal)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          </div>
        </div>

        {comprobante.montoDescuentoTotal > 0 && (
          <div className="bg-green-50 border border-green-100 rounded-lg px-4 py-3 text-sm space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-green-700 font-medium">
                Descuento aplicado
                {comprobante.codigoCuponAplicado && (
                  <span className="ml-1.5 font-mono text-xs bg-white border border-green-200 px-1.5 py-0.5 rounded">
                    {comprobante.codigoCuponAplicado}
                  </span>
                )}
              </span>
              <span className="text-green-700 font-semibold">− {formatearPrecio(comprobante.montoDescuentoTotal)}</span>
            </div>
            {comprobante.nombreDescuentoAplicado && (
              <p className="text-xs text-green-600">{comprobante.nombreDescuentoAplicado}</p>
            )}
          </div>
        )}

        {comprobante.montoRecargosPendientes !== 0 && comprobante.montoRecargosPendientes != null && (
          <div className={`rounded-lg border px-4 py-3 text-sm ${
            comprobante.montoRecargosPendientes < 0 ? 'bg-green-50 border-green-100' : 'bg-red-50 border-red-100'
          }`}>
            <div className="flex items-center justify-between">
              <span className={`font-medium ${comprobante.montoRecargosPendientes < 0 ? 'text-green-700' : 'text-red-700'}`}>
                {comprobante.montoRecargosPendientes < 0
                  ? 'Crédito por ciudad a tu favor'
                  : 'Recargos por ciudad de solicitudes anteriores'}
              </span>
              <span className={`font-semibold ${comprobante.montoRecargosPendientes < 0 ? 'text-green-700' : 'text-red-700'}`}>
                {comprobante.montoRecargosPendientes < 0 ? '− ' : '+ '}
                {formatearPrecio(Math.abs(comprobante.montoRecargosPendientes))}
              </span>
            </div>
          </div>
        )}

        <div className="flex items-center justify-between border-t border-gray-100 pt-4">
          <span className="text-sm font-semibold text-gray-600">Total pagado</span>
          <span className="text-xl font-bold text-primary-700">{formatearPrecio(comprobante.montoTotal)}</span>
        </div>
      </div>
    </div>
  )
}

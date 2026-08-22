import { useEffect, useRef, useState } from 'react'
import { useCarrito } from '../hooks/useCarrito'
import pagosService from '../services/pagosService'
import { formatearPrecio } from '../utils/catalogoDisplay'
import { Modal } from './ui/Modal'
import Toast from './Toast'

function IconoCarrito({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
    </svg>
  )
}

function IconoBasura({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6M9.5 4h5a1 1 0 011 1v2h-7V5a1 1 0 011-1z" />
    </svg>
  )
}

function ModalConfirmarCompra({ onClose, onConfirmar, procesando, codigoCupon, onCambiarCupon }) {
  const { items, total, precioUnitario } = useCarrito()

  return (
    <Modal titulo="Confirmar compra" subtitulo="Revisa tu pedido antes de pagar"
      onClose={procesando ? undefined : onClose} ancho="max-w-md">
      <div className="p-6 space-y-4">
        <ul className="divide-y divide-gray-100">
          {items.map((i) => {
            const unitario = precioUnitario(i)
            const conDescuento = unitario < i.valorBase
            return (
              <li key={i.idProceso} className="py-2 flex items-center justify-between text-sm gap-3">
                <span className="text-gray-700">
                  {i.cantidad} × {i.nombreProceso}
                  {conDescuento && (
                    <span className="ml-1.5 text-[10px] font-semibold text-green-600 bg-green-50 px-1.5 py-0.5 rounded-full align-middle">
                      precio por volumen
                    </span>
                  )}
                </span>
                <span className="font-medium text-gray-800 flex-shrink-0">{formatearPrecio(unitario * i.cantidad)}</span>
              </li>
            )
          })}
        </ul>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Código de descuento</label>
          <input value={codigoCupon} onChange={(e) => onCambiarCupon(e.target.value.toUpperCase())}
            placeholder="Opcional"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm font-mono uppercase focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>

        <div className="flex items-center justify-between border-t border-gray-100 pt-3">
          <span className="text-sm font-semibold text-gray-600">Total estimado</span>
          <span className="text-lg font-bold text-primary-700">{formatearPrecio(total)}</span>
        </div>

        <p className="text-xs text-gray-400">
          El total final (con descuentos aplicados) se confirma al procesar el pago. Serás redirigido a Wompi para completarlo de forma segura.
        </p>

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} disabled={procesando}
            className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 disabled:opacity-50">
            Cancelar
          </button>
          <button onClick={onConfirmar} disabled={procesando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {procesando ? 'Redirigiendo...' : 'Pagar con Wompi'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

/** Ícono de carrito del header que despliega el resumen de compra — el carrito ya no vive fijo en la página. */
export default function CarritoDropdown() {
  const { items, quitarItem, vaciarCarrito, total, totalItems, precioUnitario } = useCarrito()
  const [abierto, setAbierto] = useState(false)
  const [modalConfirmar, setModalConfirmar] = useState(false)
  const [procesando, setProcesando] = useState(false)
  const [codigoCupon, setCodigoCupon] = useState('')
  const [toast, setToast] = useState(null)
  const ref = useRef(null)

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const carritoVacio = items.length === 0

  const confirmarCompra = async () => {
    setProcesando(true)
    try {
      const itemsReq = items.map((i) => ({ idProceso: i.idProceso, cantidad: i.cantidad }))
      const orden = await pagosService.crearOrden(itemsReq, codigoCupon)
      const pago = await pagosService.iniciarPago(orden.idOrdenCompra)
      vaciarCarrito()
      window.location.href = pago.urlCheckout
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo iniciar el pago', tipo: 'error' })
      setProcesando(false)
      setModalConfirmar(false)
    }
  }

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setAbierto((v) => !v)} title="Carrito de compras"
        className="relative p-2 text-gray-500 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors">
        <IconoCarrito className="h-5 w-5" />
        {totalItems > 0 && (
          <span className="absolute -top-0.5 -right-0.5 bg-primary-600 text-white text-[10px] font-bold rounded-full h-4 min-w-4 px-1 flex items-center justify-center">
            {totalItems}
          </span>
        )}
      </button>

      {abierto && (
        <div className="absolute right-0 top-11 w-80 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-gray-800">Resumen de compra</h3>
              <p className="text-xs text-gray-400 mt-0.5">
                {totalItems > 0 ? `${totalItems} unidad${totalItems !== 1 ? 'es' : ''}` : 'Sin servicios agregados'}
              </p>
            </div>
            {!carritoVacio && (
              <button onClick={vaciarCarrito}
                className="flex-shrink-0 text-xs font-medium text-gray-400 hover:text-red-500 transition-colors">
                Vaciar
              </button>
            )}
          </div>

          {carritoVacio ? (
            <div className="flex flex-col items-center justify-center py-10 px-6 text-center gap-2">
              <div className="w-12 h-12 rounded-full bg-gray-50 flex items-center justify-center text-gray-300">
                <IconoCarrito className="h-6 w-6" />
              </div>
              <p className="text-sm font-semibold text-gray-600">Tu carrito está vacío</p>
              <p className="text-xs text-gray-400">Agrega servicios desde el catálogo.</p>
            </div>
          ) : (
            <>
              <ul className="divide-y divide-gray-50 max-h-72 overflow-y-auto">
                {items.map((i) => {
                  const unitario = precioUnitario(i)
                  const conDescuento = unitario < i.valorBase
                  return (
                    <li key={i.idProceso} className="px-4 py-3 flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm text-gray-800 font-medium truncate">{i.nombreProceso}</p>
                        <p className="text-xs text-gray-400 mt-0.5">
                          {formatearPrecio(unitario)} c/u
                          {conDescuento && <span className="ml-1 text-green-600 font-medium">· precio por volumen</span>}
                        </p>
                        <div className="flex items-center gap-2 mt-2">
                          <span className="text-xs font-semibold text-gray-600 bg-gray-100 px-2 py-1 rounded-lg">
                            {i.cantidad} unidad{i.cantidad !== 1 ? 'es' : ''}
                          </span>
                          <button onClick={() => quitarItem(i.idProceso)} title="Quitar del carrito"
                            className="ml-auto text-gray-300 hover:text-red-500 transition-colors">
                            <IconoBasura className="h-4 w-4" />
                          </button>
                        </div>
                      </div>
                      <p className="text-sm font-semibold text-gray-800 flex-shrink-0 whitespace-nowrap">
                        {formatearPrecio(unitario * i.cantidad)}
                      </p>
                    </li>
                  )
                })}
              </ul>

              <div className="px-4 py-3.5 border-t border-gray-100 bg-gray-50/60 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-500">Total a pagar</span>
                  <span className="text-lg font-bold text-gray-800">{formatearPrecio(total)}</span>
                </div>
                <button onClick={() => { setModalConfirmar(true); setAbierto(false) }}
                  className="w-full bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold py-2.5 rounded-lg transition-colors flex items-center justify-center gap-1.5">
                  Confirmar y pagar
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                  </svg>
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {modalConfirmar && (
        <ModalConfirmarCompra
          onClose={() => setModalConfirmar(false)}
          onConfirmar={confirmarCompra}
          procesando={procesando}
          codigoCupon={codigoCupon}
          onCambiarCupon={setCodigoCupon}
        />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

import { useId, useEffect, useRef, useState } from 'react'
import { useCarrito } from '../hooks/useCarrito'
import pagosService from '../services/pagosService'
import catalogoService from '../services/catalogoService'
import dashboardService from '../services/dashboardService'
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

function IconoEtiqueta({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M7 7h.01M3 11V6a2 2 0 012-2h5.172a2 2 0 011.414.586l7.828 7.828a2 2 0 010 2.828l-6.172 6.172a2 2 0 01-2.828 0L3.586 13.414A2 2 0 013 12.172V11z" />
    </svg>
  )
}

function IconoCheckCirculo({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 20 20">
      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
    </svg>
  )
}

function IconoErrorCirculo({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 20 20">
      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
    </svg>
  )
}

function IconoMas({ className = 'h-3.5 w-3.5' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
    </svg>
  )
}

function IconoBolsa({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5m16.5 0h-16.5m16.5 0l-1.5-3H5.25l-1.5 3m6-1.5v-3m3 3v-3" />
    </svg>
  )
}

/** Sugerencias tipo "también te puede interesar" (al estilo Rappi) justo antes de pagar — solo
 *  servicios donde el saldo ya llegó a cero (candidatos claros a recompra) y que no estén ya en el
 *  carrito. Deliberadamente chips compactos, no la tarjeta completa del catálogo (esa vive en la
 *  pestaña Servicios) — acá es un empujón rápido, no una segunda vitrina. */
function SugerenciasAlPagar({ idsEnCarrito, onAgregar }) {
  const [sugerencias, setSugerencias] = useState([])
  const [agregados, setAgregados] = useState(() => new Set())

  useEffect(() => {
    Promise.all([dashboardService.obtenerBolsaServicios(), catalogoService.listarServicios()])
      .then(([bolsa, catalogo]) => {
        const idsEnCero = new Set(bolsa.filter((s) => s.cantidadDisponible === 0).map((s) => s.idProceso))
        setSugerencias(catalogo.filter((s) => idsEnCero.has(s.idProceso) && s.valor))
      })
      .catch(() => setSugerencias([]))
  }, [])

  const visibles = sugerencias.filter((s) => !idsEnCarrito.has(s.idProceso))
  if (!visibles.length) return null

  return (
    <div className="rounded-xl border border-dashed border-primary-200 bg-gradient-to-br from-primary-50/70 to-white p-3">
      <p className="text-xs font-semibold text-primary-700 flex items-center gap-1 mb-2.5">
        💡 También te podría interesar <span className="font-normal text-gray-400">— te quedaste sin saldo</span>
      </p>
      <div className="flex gap-2.5 overflow-x-auto pb-1 -mx-1 px-1">
        {visibles.map((s) => {
          const agregado = agregados.has(s.idProceso)
          return (
            <div key={s.idProceso}
              className="flex-shrink-0 w-36 rounded-xl border border-gray-200 bg-white p-2.5 flex flex-col gap-1.5 shadow-sm hover:shadow-md hover:-translate-y-0.5 transition-all duration-200">
              <p className="text-xs font-medium text-gray-700 leading-snug line-clamp-2">{s.nombreProceso}</p>
              <div className="mt-auto flex items-center justify-between gap-1.5">
                <span className="text-xs font-bold text-gray-800 whitespace-nowrap">{formatearPrecio(s.valor)}</span>
                <button type="button" disabled={agregado}
                  onClick={() => { onAgregar(s, 1); setAgregados((prev) => new Set(prev).add(s.idProceso)) }}
                  title={agregado ? 'Agregado' : 'Agregar al carrito'}
                  className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-white transition-all duration-200 ${
                    agregado ? 'bg-green-500 animate-pop-in' : 'bg-primary-600 hover:bg-primary-700 hover:scale-110'
                  }`}>
                  {agregado ? <IconoCheckCirculo className="h-3.5 w-3.5" /> : <IconoMas />}
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function ModalConfirmarCompra({ onClose, onConfirmar, procesando, codigoCupon, onCambiarCupon, recargosPendientes }) {
  const idCampos = useId()
  const { items, total, precioUnitario, agregarItem, quitarItem } = useCarrito()
  const [cupon, setCupon] = useState({ estado: 'idle', mensaje: null, montoDescuento: 0, totalConDescuento: null })
  const [verDetalleRecargos, setVerDetalleRecargos] = useState(false)
  const tokenRef = useRef(0)
  const idsEnCarrito = new Set(items.map((i) => i.idProceso))

  const montoRecargosPendientes = recargosPendientes?.montoTotal ?? 0

  // Si el cliente vacía el carrito quitando ítems desde acá mismo, no tiene sentido dejar el modal
  // de confirmación abierto con un pedido vacío — se cierra solo.
  useEffect(() => {
    if (items.length === 0 && !procesando) onClose?.()
  }, [items.length, procesando, onClose])

  useEffect(() => {
    const codigo = codigoCupon.trim()
    if (!codigo) {
      setCupon({ estado: 'idle', mensaje: null, montoDescuento: 0, totalConDescuento: null })
      return
    }
    setCupon((p) => ({ ...p, estado: 'validando', mensaje: null }))
    const miToken = ++tokenRef.current
    const timer = setTimeout(async () => {
      try {
        const itemsReq = items.map((i) => ({ idProceso: i.idProceso, cantidad: i.cantidad }))
        const res = await pagosService.validarCupon(itemsReq, codigo)
        if (tokenRef.current !== miToken) return
        setCupon({
          estado: 'valido',
          mensaje: `Código válido — ahorras ${formatearPrecio(res.montoDescuento)}`,
          montoDescuento: res.montoDescuento,
          totalConDescuento: res.montoTotalConDescuento,
        })
      } catch (err) {
        if (tokenRef.current !== miToken) return
        setCupon({
          estado: 'invalido',
          mensaje: err.response?.data?.mensaje ?? 'No se pudo validar el código',
          montoDescuento: 0,
          totalConDescuento: null,
        })
      }
    }, 500)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [codigoCupon])

  // Sin cupón, el total con recargos pendientes se calcula en el cliente; con cupón, se usa el
  // total ya calculado por el backend (que también los incluye — ver OrdenCompraService.validarCupon).
  const totalMostrado = cupon.estado === 'valido' && cupon.totalConDescuento != null
    ? cupon.totalConDescuento
    : total + montoRecargosPendientes

  return (
    <Modal titulo="Confirmar compra" subtitulo="Revisa tu pedido antes de pagar"
      onClose={procesando ? undefined : onClose} ancho="max-w-md">
      <div className="p-6 space-y-4">
        <ul className="space-y-2">
          {items.map((i, idx) => {
            const unitario = precioUnitario(i)
            const conDescuento = unitario < i.valorBase
            return (
              <li key={i.idProceso}
                className="group animate-fade-in rounded-xl border border-gray-100 bg-gray-50/50 hover:bg-gray-50 px-3 py-2.5 flex items-center gap-3 transition-colors"
                style={{ animationDelay: `${idx * 40}ms` }}>
                <span className="flex-shrink-0 w-8 h-8 rounded-full bg-primary-100 text-primary-700 text-xs font-bold flex items-center justify-center">
                  {i.cantidad}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm text-gray-800 font-medium truncate">{i.nombreProceso}</p>
                  {conDescuento && (
                    <span className="text-[10px] font-semibold text-green-600 bg-green-50 px-1.5 py-0.5 rounded-full">
                      🏷️ precio por volumen
                    </span>
                  )}
                </div>
                <span className="font-semibold text-gray-800 flex-shrink-0 text-sm">{formatearPrecio(unitario * i.cantidad)}</span>
                <button type="button" onClick={() => quitarItem(i.idProceso)} disabled={procesando} title="Quitar del carrito"
                  className="flex-shrink-0 text-gray-300 hover:text-red-500 opacity-60 group-hover:opacity-100 disabled:opacity-30 transition-all">
                  <IconoBasura className="h-4 w-4" />
                </button>
              </li>
            )
          })}
        </ul>

        <SugerenciasAlPagar idsEnCarrito={idsEnCarrito} onAgregar={agregarItem} />

        {montoRecargosPendientes !== 0 && (
          <div className={`rounded-lg border px-3 py-2.5 text-xs space-y-1.5 ${
            montoRecargosPendientes < 0 ? 'border-green-200 bg-green-50 text-green-700' : 'border-red-200 bg-red-50 text-red-700'
          }`}>
            <div className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-1.5 font-medium">
                <span>📍</span> {montoRecargosPendientes < 0 ? 'Crédito por ciudad a tu favor' : 'Recargos por ciudad de solicitudes anteriores'}
              </span>
              <span className="font-semibold flex-shrink-0">{formatearPrecio(montoRecargosPendientes)}</span>
            </div>
            <div className="flex items-center justify-between gap-2">
              <p className={montoRecargosPendientes < 0 ? 'text-green-600' : 'text-red-600'}>
                {montoRecargosPendientes < 0 ? 'Se descuenta de esta compra.' : 'Se suman a esta compra — es la forma en que se cobran.'}
              </p>
              <button type="button" onClick={() => setVerDetalleRecargos((v) => !v)}
                className={`flex-shrink-0 font-medium underline ${montoRecargosPendientes < 0 ? 'text-green-600 hover:text-green-800' : 'text-red-600 hover:text-red-800'}`}>
                {verDetalleRecargos ? 'Ocultar' : 'Ver detalle'}
              </button>
            </div>
            {verDetalleRecargos && (
              <ul className={`space-y-1 pt-1.5 border-t ${montoRecargosPendientes < 0 ? 'border-green-200' : 'border-red-200'}`}>
                {recargosPendientes.items.map((r) => (
                  <li key={r.idRecargoPendiente} className="flex items-start justify-between gap-2">
                    <span>
                      {r.motivo ? r.motivo : `${r.nombreProceso} — ${r.nombreCandidato}`} ({r.nombreCiudad})
                    </span>
                    <span className={`flex-shrink-0 font-medium ${r.monto < 0 ? 'text-green-600' : 'text-red-700'}`}>
                      {r.monto < 0 ? '' : '+'} {formatearPrecio(r.monto)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <div>
          <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1.5">Código de descuento</label>
          <div className="relative">
            <span className={`absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none ${
              cupon.estado === 'valido' ? 'text-green-500' : cupon.estado === 'invalido' ? 'text-red-400' : 'text-gray-300'
            }`}>
              <IconoEtiqueta className="h-4 w-4" />
            </span>
            <input id={`${idCampos}-1`} value={codigoCupon} onChange={(e) => onCambiarCupon(e.target.value.toUpperCase())}
              placeholder="Ingresa tu código (opcional)"
              className={`w-full border rounded-lg pl-9 pr-9 py-2.5 text-sm font-mono uppercase tracking-wide transition-colors focus:outline-none focus:ring-2 focus:ring-offset-0 ${
                cupon.estado === 'valido' ? 'border-green-300 bg-green-50/40 focus:ring-green-500'
                  : cupon.estado === 'invalido' ? 'border-red-300 bg-red-50/40 focus:ring-red-500'
                    : 'border-gray-200 focus:ring-primary-500'
              }`} />
            {cupon.estado === 'validando' && (
              <div className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 border-2 border-gray-300 border-t-primary-600 rounded-full animate-spin" />
            )}
            {cupon.estado === 'valido' && (
              <IconoCheckCirculo className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-green-500 animate-pop-in" />
            )}
            {cupon.estado === 'invalido' && (
              <IconoErrorCirculo className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-red-500" />
            )}
          </div>

          {cupon.mensaje && (
            <div className={`mt-2 flex items-start gap-2 rounded-lg px-3 py-2 text-xs font-medium animate-fade-in ${
              cupon.estado === 'valido' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-600'
            }`}>
              {cupon.estado === 'valido'
                ? <IconoCheckCirculo className="h-3.5 w-3.5 flex-shrink-0 mt-0.5" />
                : <IconoErrorCirculo className="h-3.5 w-3.5 flex-shrink-0 mt-0.5" />}
              <span>{cupon.mensaje}</span>
            </div>
          )}
        </div>

        <div className="rounded-xl border border-primary-100 bg-gradient-to-br from-primary-50 to-white px-4 py-3.5 space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="text-gray-500">Subtotal</span>
            <span className={`text-gray-600 ${cupon.estado === 'valido' ? 'line-through decoration-gray-300' : 'font-medium'}`}>
              {formatearPrecio(total)}
            </span>
          </div>
          {montoRecargosPendientes !== 0 && (
            <div className={`flex items-center justify-between text-sm ${montoRecargosPendientes < 0 ? 'text-green-600' : 'text-red-600'}`}>
              <span>{montoRecargosPendientes < 0 ? 'Crédito por ciudad a tu favor' : 'Recargos por ciudad pendientes'}</span>
              <span className="font-medium">{formatearPrecio(montoRecargosPendientes)}</span>
            </div>
          )}
          {cupon.estado === 'valido' && (
            <div className="flex items-center justify-between text-sm text-green-600">
              <span className="flex items-center gap-1.5">
                <IconoEtiqueta className="h-3.5 w-3.5" /> Descuento aplicado
              </span>
              <span className="font-medium">− {formatearPrecio(cupon.montoDescuento)}</span>
            </div>
          )}
          <div className="flex items-center justify-between border-t border-primary-100 pt-2">
            <span className="text-sm font-semibold text-gray-700">Total estimado</span>
            <span className="text-xl font-bold text-primary-700">{formatearPrecio(totalMostrado)}</span>
          </div>
        </div>

        <p className="text-xs text-gray-400">
          El total final (con descuentos aplicados) se confirma al procesar el pago. Serás redirigido a Wompi para completarlo de forma segura.
        </p>

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} disabled={procesando}
            className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 disabled:opacity-50">
            Cancelar
          </button>
          <button onClick={onConfirmar} disabled={procesando || cupon.estado === 'validando'}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-all duration-200 hover:scale-105 hover:shadow-lg hover:shadow-primary-600/25 flex items-center gap-1.5">
            {procesando && <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />}
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
  const [recargosPendientes, setRecargosPendientes] = useState(null)
  const [toast, setToast] = useState(null)
  const ref = useRef(null)

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  useEffect(() => {
    pagosService.obtenerRecargosPendientes().then(setRecargosPendientes).catch(() => setRecargosPendientes(null))
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
        className={`relative p-2 rounded-lg transition-all duration-200 ${
          abierto ? 'text-primary-600 bg-primary-50' : 'text-gray-500 hover:text-gray-800 hover:bg-gray-100'
        }`}>
        <IconoCarrito className="h-5 w-5" />
        {totalItems > 0 && (
          <span key={totalItems}
            className="animate-pop-in absolute -top-0.5 -right-0.5 bg-primary-600 text-white text-[10px] font-bold rounded-full h-4 min-w-4 px-1 flex items-center justify-center shadow-sm ring-2 ring-white">
            {totalItems}
          </span>
        )}
      </button>

      {abierto && (
        <div className="animate-scale-in origin-top-right fixed left-3 right-3 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-11 sm:w-80 bg-white rounded-2xl shadow-2xl border border-gray-100 z-50 overflow-hidden">
          <div className="px-4 py-3.5 bg-gradient-to-r from-primary-600 to-primary-700 flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-full bg-white/15 flex items-center justify-center text-white">
                <IconoBolsa className="h-4 w-4" />
              </div>
              <div>
                <h3 className="text-sm font-semibold text-white leading-tight">Tu carrito</h3>
                <p className="text-[11px] text-primary-100">
                  {totalItems > 0 ? `${totalItems} unidad${totalItems !== 1 ? 'es' : ''}` : 'Sin servicios agregados'}
                </p>
              </div>
            </div>
            {!carritoVacio && (
              <button onClick={vaciarCarrito}
                className="flex-shrink-0 text-[11px] font-medium text-primary-100 hover:text-white bg-white/10 hover:bg-white/20 px-2.5 py-1 rounded-full transition-colors">
                Vaciar
              </button>
            )}
          </div>

          {carritoVacio ? (
            <div className="flex flex-col items-center justify-center py-10 px-6 text-center gap-2">
              <div className="w-14 h-14 rounded-full bg-gray-50 border-2 border-dashed border-gray-200 flex items-center justify-center text-gray-300">
                <IconoCarrito className="h-6 w-6" />
              </div>
              <p className="text-sm font-semibold text-gray-600">Tu carrito está vacío</p>
              <p className="text-xs text-gray-400">Agrega servicios desde el catálogo.</p>
            </div>
          ) : (
            <>
              <ul className="divide-y divide-gray-50 max-h-72 overflow-y-auto">
                {items.map((i, idx) => {
                  const unitario = precioUnitario(i)
                  const conDescuento = unitario < i.valorBase
                  return (
                    <li key={i.idProceso}
                      className="group animate-fade-in px-4 py-3 flex items-start justify-between gap-3 hover:bg-primary-50/30 transition-colors"
                      style={{ animationDelay: `${idx * 40}ms` }}>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm text-gray-800 font-medium truncate">{i.nombreProceso}</p>
                        <p className="text-xs text-gray-400 mt-0.5">
                          {formatearPrecio(unitario)} c/u
                          {conDescuento && <span className="ml-1 text-green-600 font-medium">· 🏷️ precio por volumen</span>}
                        </p>
                        <div className="flex items-center gap-2 mt-2">
                          <span className="text-xs font-semibold text-primary-700 bg-primary-50 px-2 py-1 rounded-lg">
                            {i.cantidad} unidad{i.cantidad !== 1 ? 'es' : ''}
                          </span>
                          <button onClick={() => quitarItem(i.idProceso)} title="Quitar del carrito"
                            className="ml-auto text-gray-300 hover:text-red-500 opacity-60 group-hover:opacity-100 transition-all">
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

              <div className="px-4 py-3.5 border-t border-gray-100 bg-gradient-to-b from-gray-50/80 to-white space-y-3">
                {recargosPendientes?.montoTotal !== 0 && recargosPendientes?.montoTotal != null && (
                  <div className={`flex items-start gap-1.5 text-xs rounded-lg px-2.5 py-2 border ${
                    recargosPendientes.montoTotal < 0 ? 'text-green-600 bg-green-50 border-green-200' : 'text-red-600 bg-red-50 border-red-200'
                  }`}>
                    <span className="flex-shrink-0">📍</span>
                    {recargosPendientes.montoTotal < 0 ? (
                      <span>
                        Tienes <span className="font-semibold">{formatearPrecio(recargosPendientes.montoTotal)}</span> de
                        crédito por ciudad — se descuenta de esta compra.
                      </span>
                    ) : (
                      <span>
                        Tienes <span className="font-semibold">{formatearPrecio(recargosPendientes.montoTotal)}</span> en
                        recargos por ciudad de solicitudes anteriores — se suman a esta compra.
                      </span>
                    )}
                  </div>
                )}
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-500">Total a pagar</span>
                  <span className="text-lg font-bold text-primary-700">
                    {formatearPrecio(total + (recargosPendientes?.montoTotal ?? 0))}
                  </span>
                </div>
                <button onClick={() => { setModalConfirmar(true); setAbierto(false) }}
                  className="w-full bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold py-2.5 rounded-xl transition-all duration-200 hover:scale-[1.02] hover:shadow-lg hover:shadow-primary-600/25 flex items-center justify-center gap-1.5">
                  Confirmar y pagar
                  <svg className="h-4 w-4 transition-transform group-hover:translate-x-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
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
          recargosPendientes={recargosPendientes}
        />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom'
import pagosService from '../../services/pagosService'
import { formatearPrecio } from '../../utils/catalogoDisplay'
import { useAuth } from '../../hooks/useAuth'

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

/* ─── Fila label/valor para las secciones de info (transacción / pagador) ─── */
function FilaInfo({ label, valor }) {
  if (!valor) return null
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-gray-400">{label}</span>
      <span className="font-medium text-gray-700 truncate" title={valor}>{valor}</span>
    </div>
  )
}

export default function ResultadoPago() {
  const { idOrden } = useParams()
  const navigate = useNavigate()
  const { refrescarUsuario } = useAuth()
  // Estos vienen del redirect de Wompi (?id=...&env=...) — solo para mostrar, nunca para decidir
  // el estado del pago: eso siempre se confirma contra el backend, que a su vez lo saca del webhook.
  const [searchParams] = useSearchParams()
  const idTransaccionWompi = searchParams.get('id')
  const entornoWompi = searchParams.get('env')
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

  // Con la primera compra, el backend habilita "Solicitudes" (ver AuthService.
  // resolverPuedeSolicitar) — sin esto, el usuario tenía que cerrar sesión y volver a entrar
  // para verlo aparecer en el menú.
  useEffect(() => {
    if (orden?.estado === 'APROBADA') refrescarUsuario()
  }, [orden?.estado, refrescarUsuario])

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
  const rechazada = orden.estado === 'RECHAZADA'
  // Cualquier otro estado (PENDIENTE tras agotar los reintentos de consulta, EXPIRADA, etc.) se
  // trata como "pendiente" — nunca como rechazado, para no informar mal un pago que sigue vivo.
  const pendiente = !aprobada && !rechazada

  const iconoCfg = aprobada
    ? { fondo: 'bg-green-100', color: 'text-green-600', path: 'M5 13l4 4L19 7' }
    : rechazada
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
          {aprobada ? 'Pago aprobado' : rechazada ? 'Pago rechazado' : 'Pago pendiente'}
        </h2>
        <p className="text-sm text-gray-500 mt-1">
          {aprobada
            ? 'Tu compra fue acreditada a tu bolsa de servicios prepago.'
            : rechazada
            ? 'No pudimos procesar el pago de esta orden.'
            : 'Todavía no hemos recibido la confirmación de este pago.'}
        </p>
      </div>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 text-left space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-gray-800">Orden {orden.referencia}</p>
            {orden.fechaCreacion && (
              <p className="text-xs text-gray-400 mt-0.5">{formatFecha(orden.fechaCreacion)}</p>
            )}
          </div>
          <span className={`flex-shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${
            orden.modoSimulado ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-600'
          }`}>
            {orden.modoSimulado ? 'Pago simulado' : 'Wompi'}
          </span>
        </div>

        {!orden.modoSimulado && (
          <div className="bg-gray-50 rounded-lg px-3 py-2.5 text-xs space-y-1.5">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Información de la transacción</p>
            <FilaInfo label="ID de transacción" valor={idTransaccionWompi} />
            <FilaInfo label="Número de aprobación" valor={orden.numeroAprobacion} />
            <FilaInfo label="Método de pago" valor={ETIQUETA_METODO_PAGO[orden.metodoPago] ?? orden.metodoPago} />
            <FilaInfo label="Entorno" valor={entornoWompi === 'test' ? 'Pruebas (sandbox)' : entornoWompi === 'prod' ? 'Producción' : entornoWompi} />
          </div>
        )}

        {(orden.nombrePagador || orden.telefonoPagador || orden.emailPagador) && (
          <div className="bg-gray-50 rounded-lg px-3 py-2.5 text-xs space-y-1.5">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Información del pagador</p>
            <FilaInfo label="Nombre" valor={orden.nombrePagador} />
            <FilaInfo label="Teléfono" valor={orden.telefonoPagador} />
            <FilaInfo label="Email" valor={orden.emailPagador} />
          </div>
        )}

        <ul className="divide-y divide-gray-100">
          {orden.items.map((i) => {
            const conDescuento = i.valorOriginal != null && i.valorOriginal > i.valorUnitario
            const ahorroItem = conDescuento ? (i.valorOriginal - i.valorUnitario) * i.cantidad : 0
            return (
              <li key={i.idProceso} className="py-2.5 flex items-center justify-between text-sm gap-3">
                <div className="min-w-0">
                  <p className="text-gray-700 truncate">{i.cantidad} × {i.nombreProceso}</p>
                  <p className="text-xs text-gray-400 mt-0.5">
                    {conDescuento && (
                      <span className="line-through text-gray-300 mr-1">{formatearPrecio(i.valorOriginal)}</span>
                    )}
                    {formatearPrecio(i.valorUnitario)} c/u
                    {conDescuento && (
                      <span className="ml-1.5 text-green-600 font-medium">· ahorras {formatearPrecio(ahorroItem)}</span>
                    )}
                  </p>
                </div>
                <div className="text-right flex-shrink-0">
                  {conDescuento && (
                    <p className="text-xs text-gray-300 line-through">{formatearPrecio(i.valorOriginal * i.cantidad)}</p>
                  )}
                  <span className="font-medium text-gray-800">{formatearPrecio(i.subtotal)}</span>
                </div>
              </li>
            )
          })}
        </ul>

        {orden.montoDescuentoTotal > 0 && (
          <div className="bg-green-50 border border-green-100 rounded-lg px-3 py-2.5 text-xs space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-green-700 font-medium flex items-center gap-1.5">
                Descuento aplicado
                {orden.codigoCuponAplicado && (
                  <span className="font-mono bg-white border border-green-200 px-1.5 py-0.5 rounded">{orden.codigoCuponAplicado}</span>
                )}
              </span>
              <span className="text-green-700 font-semibold">− {formatearPrecio(orden.montoDescuentoTotal)}</span>
            </div>
            {orden.nombreDescuentoAplicado && <p className="text-green-600">{orden.nombreDescuentoAplicado}</p>}
          </div>
        )}

        {orden.montoRecargosPendientes !== 0 && orden.montoRecargosPendientes != null && (
          <div className={`rounded-lg border px-3 py-2.5 text-xs space-y-1 ${
            orden.montoRecargosPendientes < 0
              ? 'bg-green-50 border-green-100'
              : 'bg-red-50 border-red-100'
          }`}>
            <div className="flex items-center justify-between">
              <span className={`font-medium ${orden.montoRecargosPendientes < 0 ? 'text-green-700' : 'text-red-700'}`}>
                {orden.montoRecargosPendientes < 0
                  ? 'Crédito por ciudad a tu favor'
                  : 'Recargos por ciudad de solicitudes anteriores'}
              </span>
              <span className={`font-semibold ${orden.montoRecargosPendientes < 0 ? 'text-green-700' : 'text-red-700'}`}>
                {orden.montoRecargosPendientes < 0 ? '− ' : '+ '}
                {formatearPrecio(Math.abs(orden.montoRecargosPendientes))}
              </span>
            </div>
          </div>
        )}

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
            {reintentando ? 'Redirigiendo...' : rechazada ? 'Reintentar pago' : 'Completar pago'}
          </button>
          <Link to="/cliente/comprar-servicios" className="text-sm text-gray-500 hover:text-gray-700">
            Volver a Comprar Servicios
          </Link>
        </div>
      )}
    </div>
  )
}

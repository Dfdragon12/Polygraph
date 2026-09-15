import { useCallback, useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import api from '../../services/api'
import pagosService from '../../services/pagosService'
import pospagoService from '../../services/pospagoService'
import Toast from '../../components/Toast'
import { Modal, ConfirmModal } from '../../components/ui/Modal'
import Paginacion from '../../components/ui/Paginacion'

const POR_PAGINA = 10

/** El backend siempre manda mensaje="Errores de validación" para cualquier fallo de @Valid — el
 * detalle real (qué campo y por qué) viaja aparte en `errores: {campo: mensaje}`. */
function mensajeErrorValidacion(err, fallback = 'Error al guardar.') {
  const data = err.response?.data
  if (data?.errores && typeof data.errores === 'object' && Object.keys(data.errores).length) {
    return Object.entries(data.errores).map(([campo, msg]) => `${campo}: ${msg}`).join(' · ')
  }
  return data?.mensaje ?? data?.message ?? fallback
}

function formatearPrecio(valor) {
  if (valor == null) return '—'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor)
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

const OPCIONES_MORA = [
  { valor: 'NORMAL',     etiqueta: 'Al día' },
  { valor: 'EN_MORA',    etiqueta: 'En mora' },
  { valor: 'SUSPENDIDO', etiqueta: 'Suspendido' },
]

const ESTADO_ORDEN_CFG = {
  PENDIENTE: { label: 'Pendiente', badge: 'bg-amber-100 text-amber-700' },
  APROBADA:  { label: 'Aprobada',  badge: 'bg-green-100 text-green-700' },
  RECHAZADA: { label: 'Rechazada', badge: 'bg-red-100 text-red-600' },
  EXPIRADA:  { label: 'Expirada',  badge: 'bg-gray-100 text-gray-500' },
}

const ESTADO_FACTURA_CFG = {
  PENDIENTE: { label: 'Pendiente', badge: 'bg-amber-100 text-amber-700' },
  PAGADA:    { label: 'Pagada',    badge: 'bg-green-100 text-green-700' },
  VENCIDA:   { label: 'Vencida',   badge: 'bg-red-100 text-red-600' },
}

const ESTADO_MORA_CFG = {
  NORMAL:     { label: 'Al día',     badge: 'bg-green-100 text-green-700' },
  EN_MORA:    { label: 'En mora',    badge: 'bg-amber-100 text-amber-700' },
  SUSPENDIDO: { label: 'Suspendido', badge: 'bg-red-100 text-red-600' },
}

function EstadoBadge({ estado, cfg }) {
  const c = cfg[estado] ?? { label: estado, badge: 'bg-gray-100 text-gray-500' }
  return <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${c.badge}`}>{c.label}</span>
}

function formatFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const ETIQUETA_METODO_PAGO = {
  CARD: 'Tarjeta',
  NEQUI: 'Nequi',
  DAVIPLATA: 'Daviplata',
  PSE: 'PSE',
  BANCOLOMBIA_TRANSFER: 'Transferencia Bancolombia',
  BANCOLOMBIA_QR: 'QR Bancolombia',
}

/* ─── Icono + título de estado, igual al recibo que ve el cliente tras pagar ─── */
function IconoEstadoPago({ aprobado, rechazado }) {
  const cfg = aprobado
    ? { fondo: 'bg-green-100', color: 'text-green-600', path: 'M5 13l4 4L19 7' }
    : rechazado
    ? { fondo: 'bg-red-100', color: 'text-red-500', path: 'M6 18L18 6M6 6l12 12' }
    : { fondo: 'bg-amber-100', color: 'text-amber-600', path: 'M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z' }
  return (
    <div className={`w-14 h-14 mx-auto rounded-full flex items-center justify-center ${cfg.fondo}`}>
      <svg className={`h-7 w-7 ${cfg.color}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d={cfg.path} />
      </svg>
    </div>
  )
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

function IconoBasura({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
    </svg>
  )
}

function IconoDocumento({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  )
}

function IconoDocumentoMas({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h4m-2-2v4m2 7H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  )
}

function IconoOjo({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

/** Botón de eliminar reservado a filas Pendiente — confirma antes de borrar, nunca sin preguntar. */
function BotonEliminar({ onConfirmar, titulo, cuerpo }) {
  const [confirmando, setConfirmando] = useState(false)
  return (
    <>
      <button onClick={(e) => { e.stopPropagation(); setConfirmando(true) }} title="Eliminar"
        className="text-gray-300 hover:text-red-500 transition-colors flex-shrink-0">
        <IconoBasura />
      </button>
      {confirmando && (
        <ConfirmModal titulo={titulo} danger confirmLabel="Eliminar" cancelLabel="Cancelar"
          onConfirmar={() => { setConfirmando(false); onConfirmar() }} onCancelar={() => setConfirmando(false)}>
          <p className="text-sm text-gray-500">{cuerpo}</p>
        </ConfirmModal>
      )}
    </>
  )
}

/** Historial de compras (prepago) o crédito + facturas (pospago) de UN cliente — reemplaza la
 * antigua tabla plana de "Órdenes de compra" / "Facturas pospago" mezclando todos los clientes,
 * y el modal "Editar crédito" que vivía en Clientes.jsx. */
export default function FacturacionCliente() {
  const { idCliente } = useParams()
  const [cliente, setCliente] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    api.get(`/clientes/${idCliente}`)
      .then((res) => setCliente(res.data))
      .catch(() => setError('No se pudo cargar el cliente.'))
  }, [idCliente])

  if (error) return (
    <div className="flex flex-col items-center justify-center h-64 gap-3 text-center">
      <p className="text-red-500 text-sm">{error}</p>
      <Link to="/admin/facturacion" className="text-sm text-primary-600 hover:underline">← Volver a Facturación</Link>
    </div>
  )

  if (!cliente) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
    </div>
  )

  return (
    <div>
      <Link to="/admin/facturacion" className="text-sm text-gray-500 hover:text-gray-700 flex items-center gap-1.5 mb-4">
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
        Facturación
      </Link>

      <div className="mb-6 flex items-center gap-3">
        <h2 className="text-2xl font-bold text-gray-900">{cliente.nombreDisplay}</h2>
        <span className={`text-xs px-2 py-0.5 rounded font-medium ${
          cliente.tipoCliente === 'PREPAGO' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'
        }`}>
          {cliente.tipoCliente === 'PREPAGO' ? 'Prepago' : 'Pospago'}
        </span>
      </div>

      {cliente.tipoCliente === 'PREPAGO'
        ? <SeccionPrepago idCliente={cliente.idCliente} setToast={setToast} />
        : <SeccionPospago cliente={cliente} setToast={setToast} />}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

// ---------- Prepago: historial de órdenes de compra ----------

const FILTROS_ESTADO_ORDEN = [
  { key: '',           label: 'Todas' },
  { key: 'APROBADA',   label: 'Aprobadas' },
  { key: 'PENDIENTE',  label: 'Pendientes' },
  { key: 'RECHAZADA',  label: 'Rechazadas' },
]

function SeccionPrepago({ idCliente, setToast }) {
  const [ordenes, setOrdenes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [filtroEstado, setFiltroEstado] = useState('')
  const [generandoId, setGenerandoId] = useState(null)
  const [detalleDe, setDetalleDe] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)

  const cargar = useCallback(() => {
    setCargando(true)
    setError(null)
    pagosService.adminListarOrdenes({ idCliente, estado: filtroEstado || undefined, page: pagina, size: POR_PAGINA })
      .then((data) => { setOrdenes(data.content ?? data ?? []); setTotalElementos(data.totalElements ?? 0) })
      .catch(() => setError('No se pudo cargar el historial de compras.'))
      .finally(() => setCargando(false))
  }, [idCliente, filtroEstado, pagina])

  useEffect(() => { cargar() }, [cargar])
  useEffect(() => { setPagina(0) }, [filtroEstado])

  const generarFactura = async (idOrdenCompra) => {
    setGenerandoId(idOrdenCompra)
    try {
      await pagosService.adminGenerarComprobante(idOrdenCompra)
      setToast({ mensaje: 'Factura generada correctamente', tipo: 'exito' })
      cargar()
    } catch (err) {
      setToast({ mensaje: mensajeErrorValidacion(err, 'No se pudo generar la factura'), tipo: 'error' })
    } finally {
      setGenerandoId(null)
    }
  }

  const eliminar = async (orden) => {
    try {
      await pagosService.adminEliminarOrden(orden.idOrdenCompra)
      setToast({ mensaje: `Orden ${orden.referencia} eliminada`, tipo: 'exito' })
      cargar()
    } catch (err) {
      setToast({ mensaje: mensajeErrorValidacion(err, 'No se pudo eliminar la orden'), tipo: 'error' })
    }
  }

  return (
    <div>
      <div className="flex gap-1 mb-4">
        {FILTROS_ESTADO_ORDEN.map((f) => (
          <button key={f.key} onClick={() => setFiltroEstado(f.key)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
              filtroEstado === f.key ? 'bg-primary-100 text-primary-700' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
            }`}>
            {f.label}
          </button>
        ))}
      </div>

      {cargando ? (
        <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
      ) : error ? (
        <div className="flex flex-col items-center justify-center py-16 gap-2">
          <p className="text-red-500 text-sm">{error}</p>
          <button onClick={cargar} className="text-xs text-primary-600 hover:underline">Reintentar</button>
        </div>
      ) : !ordenes.length ? (
        <div className="text-center py-16 text-gray-400 text-sm">Este cliente no tiene compras que coincidan con el filtro.</div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table-stack w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Referencia</th>
                  <th className="px-4 py-3 text-left">Fecha</th>
                  <th className="px-4 py-3 text-right">Total</th>
                  <th className="px-4 py-3 text-left">Estado</th>
                  <th className="px-4 py-3 text-center">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {ordenes.map((o) => (
                  <tr key={o.idOrdenCompra} className="hover:bg-gray-50 transition-colors">
                    <td data-label="Referencia" className="px-4 py-3 font-medium text-gray-800 whitespace-nowrap">{o.referencia}</td>
                    <td data-label="Fecha" className="px-4 py-3 text-gray-500 whitespace-nowrap">{formatFecha(o.fechaCreacion)}</td>
                    <td data-label="Total" className="px-4 py-3 text-right font-semibold text-gray-800 whitespace-nowrap">{formatearPrecio(o.montoTotal)}</td>
                    <td data-label="Estado" className="px-4 py-3"><EstadoBadge estado={o.estado} cfg={ESTADO_ORDEN_CFG} /></td>
                    <td data-label="Acciones" className="px-4 py-3">
                      <div className="flex items-center justify-center gap-3">
                        <button onClick={() => setDetalleDe(o)} title="Ver detalle de pago"
                          className="text-gray-400 hover:text-primary-600 transition-colors">
                          <IconoOjo />
                        </button>

                        {o.numeroComprobante ? (
                          <Link to={`/admin/facturacion/ordenes/comprobantes/${o.idComprobante}`} title={`Ver factura ${o.numeroComprobante}`}
                            className="text-gray-400 hover:text-primary-600 transition-colors">
                            <IconoDocumento />
                          </Link>
                        ) : o.estado === 'APROBADA' ? (
                          <button onClick={() => generarFactura(o.idOrdenCompra)} disabled={generandoId === o.idOrdenCompra}
                            title="Generar factura" className="text-gray-400 hover:text-primary-600 transition-colors disabled:opacity-40">
                            <IconoDocumentoMas />
                          </button>
                        ) : null}

                        {o.estado === 'PENDIENTE' && (
                          <BotonEliminar
                            titulo={`Eliminar orden ${o.referencia}`}
                            cuerpo="Esta orden está pendiente de pago y todavía no ha sido cobrada. Se eliminará por completo."
                            onConfirmar={() => eliminar(o)} />
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion pagina={pagina + 1} total={totalElementos} porPagina={POR_PAGINA}
            onChange={(p) => setPagina(p - 1)} />
        </div>
      )}

      {detalleDe && (
        <Modal titulo="Detalle de pago" onClose={() => setDetalleDe(null)} ancho="max-w-lg">
          <div className="px-6 py-6">
            <ReciboOrden orden={detalleDe} />
          </div>
        </Modal>
      )}
    </div>
  )
}

/** Recibo de la orden con el mismo formato que ve el cliente en la pantalla de resultado del pago. */
function ReciboOrden({ orden: o }) {
  const aprobada = o.estado === 'APROBADA'
  const rechazada = o.estado === 'RECHAZADA'

  return (
    <div className="space-y-5 text-center">
      <IconoEstadoPago aprobado={aprobada} rechazado={rechazada} />

      <div>
        <h3 className="text-lg font-bold text-gray-800">
          {aprobada ? 'Pago aprobado' : rechazada ? 'Pago rechazado' : 'Pago pendiente'}
        </h3>
        <p className="text-xs text-gray-400 mt-1">Orden {o.referencia}</p>
      </div>

      <div className="bg-gray-50 rounded-xl border border-gray-100 p-5 text-left space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-800 truncate">{o.nombreCliente ?? 'Cliente'}</p>
            {o.fechaCreacion && <p className="text-xs text-gray-400 mt-0.5">{formatFechaHora(o.fechaCreacion)}</p>}
          </div>
          <span className={`flex-shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${
            o.modoSimulado ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-600'
          }`}>
            {o.modoSimulado ? 'Pago simulado' : 'Wompi'}
          </span>
        </div>

        {!o.modoSimulado && (
          <div className="bg-white rounded-lg border border-gray-100 px-3 py-2.5 text-xs space-y-1.5">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Información de la transacción</p>
            <FilaInfo label="ID de transacción" valor={o.idTransaccionPasarela} />
            <FilaInfo label="Número de aprobación" valor={o.numeroAprobacion} />
            <FilaInfo label="Método de pago" valor={ETIQUETA_METODO_PAGO[o.metodoPago] ?? o.metodoPago} />
          </div>
        )}

        {(o.nombrePagador || o.telefonoPagador || o.emailPagador) && (
          <div className="bg-white rounded-lg border border-gray-100 px-3 py-2.5 text-xs space-y-1.5">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Información del pagador</p>
            <FilaInfo label="Nombre" valor={o.nombrePagador} />
            <FilaInfo label="Teléfono" valor={o.telefonoPagador} />
            <FilaInfo label="Email" valor={o.emailPagador} />
          </div>
        )}

        <ul className="divide-y divide-gray-100">
          {(o.items ?? []).map((i) => {
            const conDescuento = i.valorOriginal != null && i.valorOriginal > i.valorUnitario
            const ahorroItem = conDescuento ? (i.valorOriginal - i.valorUnitario) * i.cantidad : 0
            return (
              <li key={i.idProceso} className="py-2.5 flex items-center justify-between text-sm gap-3">
                <div className="min-w-0">
                  <p className="text-gray-700 truncate">{i.cantidad} × {i.nombreProceso}</p>
                  <p className="text-xs text-gray-400 mt-0.5">
                    {conDescuento && <span className="line-through text-gray-300 mr-1">{formatearPrecio(i.valorOriginal)}</span>}
                    {formatearPrecio(i.valorUnitario)} c/u
                    {conDescuento && <span className="ml-1.5 text-green-600 font-medium">· ahorras {formatearPrecio(ahorroItem)}</span>}
                    {i.nombreDescuentoAplicado && <span className="ml-1.5 text-gray-400">· {i.nombreDescuentoAplicado}</span>}
                  </p>
                </div>
                <div className="text-right flex-shrink-0">
                  {conDescuento && <p className="text-xs text-gray-300 line-through">{formatearPrecio(i.valorOriginal * i.cantidad)}</p>}
                  <span className="font-medium text-gray-800">{formatearPrecio(i.subtotal)}</span>
                </div>
              </li>
            )
          })}
        </ul>

        {o.montoDescuentoTotal > 0 && (
          <div className="bg-green-50 border border-green-100 rounded-lg px-3 py-2.5 text-xs space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-green-700 font-medium flex items-center gap-1.5">
                Descuento aplicado
                {o.codigoCuponAplicado && <span className="font-mono bg-white border border-green-200 px-1.5 py-0.5 rounded">{o.codigoCuponAplicado}</span>}
              </span>
              <span className="text-green-700 font-semibold">− {formatearPrecio(o.montoDescuentoTotal)}</span>
            </div>
            {o.nombreDescuentoAplicado && <p className="text-green-600">{o.nombreDescuentoAplicado}</p>}
          </div>
        )}

        {o.montoRecargosPendientes !== 0 && o.montoRecargosPendientes != null && (
          <div className={`rounded-lg border px-3 py-2.5 text-xs space-y-1 ${
            o.montoRecargosPendientes < 0 ? 'bg-green-50 border-green-100' : 'bg-red-50 border-red-100'
          }`}>
            <div className="flex items-center justify-between">
              <span className={`font-medium ${o.montoRecargosPendientes < 0 ? 'text-green-700' : 'text-red-700'}`}>
                {o.montoRecargosPendientes < 0 ? 'Crédito por ciudad a favor' : 'Recargos por ciudad de solicitudes anteriores'}
              </span>
              <span className={`font-semibold ${o.montoRecargosPendientes < 0 ? 'text-green-700' : 'text-red-700'}`}>
                {o.montoRecargosPendientes < 0 ? '− ' : '+ '}{formatearPrecio(Math.abs(o.montoRecargosPendientes))}
              </span>
            </div>
          </div>
        )}

        <div className="flex items-center justify-between border-t border-gray-100 pt-3">
          <span className="text-sm font-semibold text-gray-600">Total</span>
          <span className="text-lg font-bold text-primary-700">{formatearPrecio(o.montoTotal)}</span>
        </div>
      </div>

      {o.numeroComprobante && (
        <Link to={`/admin/facturacion/ordenes/comprobantes/${o.idComprobante}`}
          className="inline-block text-sm text-primary-600 hover:underline">
          Ver factura {o.numeroComprobante} →
        </Link>
      )}
    </div>
  )
}

// ---------- Pospago: crédito (antes "Editar crédito" en Clientes.jsx) + facturas ----------

function SeccionPospago({ cliente, setToast }) {
  const p = cliente.pospago
  const [form, setForm] = useState({
    limiteCredito:      p?.limiteCredito != null ? String(p.limiteCredito) : '',
    estadoMora:         p?.estadoMora ?? 'NORMAL',
    requiereAprobacion: p?.requiereAprobacion ?? false,
  })
  const [creditoDisponible, setCreditoDisponible] = useState(p?.creditoDisponible)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const [pendientes, setPendientes] = useState([])
  const [cargandoPendientes, setCargandoPendientes] = useState(true)
  const [fechaVencimiento, setFechaVencimiento] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() + 15)
    return d.toISOString().slice(0, 10)
  })
  const [generandoFactura, setGenerandoFactura] = useState(false)
  const [errorFactura, setErrorFactura] = useState(null)

  const [facturas, setFacturas] = useState([])
  const [cargandoFacturas, setCargandoFacturas] = useState(true)
  const [paginaFacturas, setPaginaFacturas] = useState(1)

  const [detalleFacturaId, setDetalleFacturaId] = useState(null)
  const [detalleFactura, setDetalleFactura] = useState(null)
  const [cargandoDetalleFactura, setCargandoDetalleFactura] = useState(false)

  const cargarPendientes = useCallback(() => {
    setCargandoPendientes(true)
    pospagoService.adminMovimientosPendientes(cliente.idCliente)
      .then(setPendientes)
      .catch(() => setPendientes([]))
      .finally(() => setCargandoPendientes(false))
  }, [cliente.idCliente])

  const cargarFacturas = useCallback(() => {
    setCargandoFacturas(true)
    pospagoService.adminFacturasDelCliente(cliente.idCliente)
      .then(setFacturas)
      .catch(() => setFacturas([]))
      .finally(() => setCargandoFacturas(false))
  }, [cliente.idCliente])

  useEffect(() => { cargarPendientes() }, [cargarPendientes])
  useEffect(() => { cargarFacturas() }, [cargarFacturas])

  const totalPendiente = pendientes.reduce((acc, m) => acc + (m.montoTotal ?? 0), 0)
  const facturasPagina = facturas.slice((paginaFacturas - 1) * POR_PAGINA, paginaFacturas * POR_PAGINA)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      const payload = {
        limiteCredito:      form.limiteCredito !== '' ? Number(form.limiteCredito) : null,
        estadoMora:         form.estadoMora,
        requiereAprobacion: form.requiereAprobacion,
      }
      const res = await api.patch(`/clientes/${cliente.idCliente}/pospago`, payload)
      setCreditoDisponible(res.data?.pospago?.creditoDisponible)
      setToast({ mensaje: 'Crédito actualizado correctamente', tipo: 'exito' })
    } catch (err) {
      setError(mensajeErrorValidacion(err, 'Error al actualizar el crédito.'))
    } finally { setGuardando(false) }
  }

  const generarFactura = async () => {
    setGenerandoFactura(true); setErrorFactura(null)
    try {
      await pospagoService.adminGenerarFactura(cliente.idCliente, fechaVencimiento)
      setToast({ mensaje: 'Factura generada correctamente', tipo: 'exito' })
      cargarPendientes()
      cargarFacturas()
    } catch (err) {
      setErrorFactura(mensajeErrorValidacion(err, 'Error al generar la factura.'))
    } finally { setGenerandoFactura(false) }
  }

  const eliminarFactura = async (factura) => {
    try {
      await pospagoService.adminEliminarFactura(factura.idFactura)
      setToast({ mensaje: `Factura ${factura.referencia} eliminada`, tipo: 'exito' })
      cargarPendientes()
      cargarFacturas()
    } catch (err) {
      setToast({ mensaje: mensajeErrorValidacion(err, 'No se pudo eliminar la factura'), tipo: 'error' })
    }
  }

  const verDetalleFactura = async (factura) => {
    setDetalleFacturaId(factura.idFactura)
    setCargandoDetalleFactura(true)
    setDetalleFactura(null)
    try {
      const data = await pospagoService.adminObtenerFactura(factura.idFactura)
      setDetalleFactura(data)
    } catch (err) {
      setToast({ mensaje: mensajeErrorValidacion(err, 'No se pudo cargar el detalle de la factura'), tipo: 'error' })
      setDetalleFacturaId(null)
    } finally {
      setCargandoDetalleFactura(false)
    }
  }

  const limiteNum = form.limiteCredito !== '' ? Number(form.limiteCredito) : null
  const utilizado = limiteNum != null && creditoDisponible != null ? limiteNum - creditoDisponible : null

  return (
    <div className="space-y-6">
      {/* Resumen de cuenta */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-4">
          <p className="text-xs text-gray-400 mb-1">Límite de crédito</p>
          <p className="text-lg font-bold text-gray-800">{formatearPrecio(limiteNum)}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-4">
          <p className="text-xs text-gray-400 mb-1">Crédito disponible</p>
          <p className="text-lg font-bold text-primary-700">{formatearPrecio(creditoDisponible)}</p>
          {utilizado != null && <p className="text-xs text-gray-400 mt-0.5">{formatearPrecio(utilizado)} utilizado</p>}
        </div>
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-4">
          <p className="text-xs text-gray-400 mb-1">Consumo sin facturar</p>
          <p className="text-lg font-bold text-gray-800">{formatearPrecio(totalPendiente)}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-4">
          <p className="text-xs text-gray-400 mb-1">Estado</p>
          <EstadoBadge estado={form.estadoMora} cfg={ESTADO_MORA_CFG} />
          {p?.diasMora > 0 && <p className="text-xs text-gray-400 mt-1">{p.diasMora} días en mora</p>}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Editar cupo */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
          <h3 className="text-sm font-semibold text-gray-800 mb-4">Editar cupo de crédito</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Límite de crédito (COP)</label>
              <input type="number" value={form.limiteCredito} placeholder="Ej: 5000000"
                onChange={(e) => setForm((p2) => ({ ...p2, limiteCredito: e.target.value }))}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Estado de mora</label>
              <select value={form.estadoMora} onChange={(e) => setForm((p2) => ({ ...p2, estadoMora: e.target.value }))}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
                {OPCIONES_MORA.map((o) => (
                  <option key={o.valor} value={o.valor}>{o.etiqueta}</option>
                ))}
              </select>
              {form.estadoMora !== 'NORMAL' && (
                <p className="text-xs text-amber-600 mt-1">Mientras esté en este estado, el cliente no podrá crear nuevas solicitudes.</p>
              )}
            </div>

            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" checked={form.requiereAprobacion}
                onChange={(e) => setForm((p2) => ({ ...p2, requiereAprobacion: e.target.checked }))}
                className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
              Requiere aprobación de estudio de crédito
            </label>

            <button type="submit" disabled={guardando}
              className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
              {guardando ? 'Guardando...' : 'Guardar'}
            </button>
          </form>
        </div>

        {/* Consumo pendiente de facturar */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
          <h3 className="text-sm font-semibold text-gray-800 mb-4">Consumo pendiente de facturar</h3>
          {cargandoPendientes ? (
            <p className="text-xs text-gray-400">Cargando...</p>
          ) : pendientes.length === 0 ? (
            <p className="text-xs text-gray-400">Este cliente no tiene consumo pendiente de facturar.</p>
          ) : (
            <>
              <ul className="max-h-40 overflow-y-auto divide-y divide-gray-50 border border-gray-100 rounded-lg mb-3">
                {pendientes.map((m) => (
                  <li key={m.idMovimiento} className="px-3 py-2 flex items-center justify-between text-xs">
                    <span className="text-gray-600 truncate">{m.nombreProceso} — {m.nombreCandidato}</span>
                    <span className="font-medium text-gray-800 flex-shrink-0 ml-2">{formatearPrecio(m.montoTotal)}</span>
                  </li>
                ))}
              </ul>
              {errorFactura && <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-lg mb-3">{errorFactura}</div>}
              <div className="flex items-end gap-3">
                <div className="flex-1">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Fecha de vencimiento</label>
                  <input type="date" value={fechaVencimiento} onChange={(e) => setFechaVencimiento(e.target.value)}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                </div>
                <button type="button" onClick={generarFactura} disabled={generandoFactura}
                  className="bg-gray-800 hover:bg-gray-900 disabled:opacity-60 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors whitespace-nowrap">
                  {generandoFactura ? 'Generando...' : `Generar factura (${formatearPrecio(totalPendiente)})`}
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Facturas */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <h3 className="text-sm font-semibold text-gray-800 px-6 pt-6 pb-4">Facturas</h3>
        {cargandoFacturas ? (
          <div className="flex justify-center py-8"><div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" /></div>
        ) : !facturas.length ? (
          <p className="text-sm text-gray-400 text-center py-8">Este cliente no tiene facturas generadas.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-stack w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Referencia</th>
                  <th className="px-4 py-3 text-left">Corte / Vence</th>
                  <th className="px-4 py-3 text-right">Total</th>
                  <th className="px-4 py-3 text-left">Estado</th>
                  <th className="px-4 py-3 text-center">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {facturasPagina.map((f) => (
                  <tr key={f.idFactura} className="hover:bg-gray-50 transition-colors">
                    <td data-label="Referencia" className="px-4 py-3 font-medium text-gray-800 whitespace-nowrap">{f.referencia}</td>
                    <td data-label="Corte / Vence" className="px-4 py-3 text-gray-500 whitespace-nowrap">{formatFecha(f.fechaCorte)} · {formatFecha(f.fechaVencimiento)}</td>
                    <td data-label="Total" className="px-4 py-3 text-right font-semibold text-gray-800 whitespace-nowrap">{formatearPrecio(f.montoTotal)}</td>
                    <td data-label="Estado" className="px-4 py-3"><EstadoBadge estado={f.estado} cfg={ESTADO_FACTURA_CFG} /></td>
                    <td data-label="Acciones" className="px-4 py-3">
                      <div className="flex items-center justify-center gap-3">
                        <button onClick={() => verDetalleFactura(f)} title="Ver detalle de pago"
                          className="text-gray-400 hover:text-primary-600 transition-colors">
                          <IconoOjo />
                        </button>
                        <Link to={`/admin/facturacion/facturas/${f.idFactura}`} title={`Ver factura ${f.referencia}`}
                          className="text-gray-400 hover:text-primary-600 transition-colors">
                          <IconoDocumento />
                        </Link>
                        {f.estado === 'PENDIENTE' && (
                          <BotonEliminar
                            titulo={`Eliminar factura ${f.referencia}`}
                            cuerpo="Esta factura está pendiente de pago. Sus servicios volverán a quedar pendientes de facturar."
                            onConfirmar={() => eliminarFactura(f)} />
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!cargandoFacturas && facturas.length > 0 && (
          <Paginacion pagina={paginaFacturas} total={facturas.length} porPagina={POR_PAGINA} onChange={setPaginaFacturas} />
        )}
      </div>

      {detalleFacturaId && (
        <Modal titulo="Detalle de pago" onClose={() => setDetalleFacturaId(null)} ancho="max-w-lg">
          <div className="px-6 py-6">
            {cargandoDetalleFactura ? (
              <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
            ) : detalleFactura ? (
              <ReciboFactura detalle={detalleFactura} />
            ) : null}
          </div>
        </Modal>
      )}
    </div>
  )
}

/** Recibo de una factura pospago: movimientos facturados + intentos de pago cruzados con Wompi. */
function ReciboFactura({ detalle }) {
  const f = detalle.factura
  const pagada = f.estado === 'PAGADA'
  const vencida = f.estado === 'VENCIDA'

  return (
    <div className="space-y-5 text-center">
      <IconoEstadoPago aprobado={pagada} rechazado={vencida} />

      <div>
        <h3 className="text-lg font-bold text-gray-800">
          {pagada ? 'Factura pagada' : vencida ? 'Factura vencida' : 'Factura pendiente'}
        </h3>
        <p className="text-xs text-gray-400 mt-1">{f.referencia}</p>
      </div>

      <div className="bg-gray-50 rounded-xl border border-gray-100 p-5 text-left space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-800 truncate">{f.nombreCliente ?? 'Cliente'}</p>
            <p className="text-xs text-gray-400 mt-0.5">Corte: {formatFecha(f.fechaCorte)} · Vence: {formatFecha(f.fechaVencimiento)}</p>
          </div>
          <EstadoBadge estado={f.estado} cfg={ESTADO_FACTURA_CFG} />
        </div>

        {detalle.intentosPago?.length > 0 && (
          <div className="bg-white rounded-lg border border-gray-100 px-3 py-2.5 text-xs space-y-2">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Intentos de pago</p>
            {detalle.intentosPago.map((ip) => (
              <div key={ip.referencia} className="space-y-1 pb-2 last:pb-0 border-b border-gray-50 last:border-0">
                <FilaInfo label="Referencia" valor={ip.referencia} />
                <FilaInfo label="Estado" valor={ip.estado} />
                <FilaInfo label="Método de pago" valor={ETIQUETA_METODO_PAGO[ip.metodoPago] ?? ip.metodoPago} />
              </div>
            ))}
          </div>
        )}

        <ul className="divide-y divide-gray-100">
          {(detalle.movimientos ?? []).map((m) => (
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

        <div className="flex items-center justify-between border-t border-gray-100 pt-3">
          <span className="text-sm font-semibold text-gray-600">Total</span>
          <span className="text-lg font-bold text-primary-700">{formatearPrecio(f.montoTotal)}</span>
        </div>
      </div>

      <Link to={`/admin/facturacion/facturas/${f.idFactura}`} className="inline-block text-sm text-primary-600 hover:underline">
        Ver factura completa →
      </Link>
    </div>
  )
}

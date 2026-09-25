import { useEffect, useRef, useState } from 'react'
import pagosService from '../../services/pagosService'
import pospagoService from '../../services/pospagoService'
import facturacionService from '../../services/facturacionService'
import notasAjusteService from '../../services/notasAjusteService'
import Toast from '../../components/Toast'
import Paginacion from '../../components/ui/Paginacion'
import { Modal, ConfirmModal } from '../../components/ui/Modal'
import MenuAcciones from '../../components/ui/MenuAcciones'

const POR_PAGINA = 20

function formatearPrecio(valor) {
  if (valor == null) return '—'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor)
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

/** El número de documento visible (CP-000123 / FP-000123) es siempre prefijo + id correlativo
 * con ceros — así prepago y pospago se leen con el mismo formato, en vez de la referencia interna
 * (PYG-... / FP-...-timestamp) que no se parece entre sí y no sirve como "número de factura". */
function numeroDocumento(d) {
  if (d.tipo === 'PREPAGO') return d.idComprobante ? `CP-${String(d.idComprobante).padStart(6, '0')}` : null
  return d.idFactura ? `FP-${String(d.idFactura).padStart(6, '0')}` : null
}

/** El backend siempre manda mensaje="Errores de validación" para cualquier fallo de @Valid — el
 * detalle real viaja aparte en `errores`. */
function mensajeError(err, fallback) {
  return err.response?.data?.mensaje ?? fallback
}

function IconoOjo({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
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

function IconoBasura({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
    </svg>
  )
}

function IconoNota({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 7h6m-6 4h6m-6 4h4M5 3.75h14A1.25 1.25 0 0120.25 5v14A1.25 1.25 0 0119 20.25H5A1.25 1.25 0 013.75 19V5A1.25 1.25 0 015 3.75z" />
    </svg>
  )
}

const ESTADO_PILL = {
  PAGADO:        { cls: 'bg-green-100 text-green-700',  label: 'Pagado' },
  POR_FACTURAR:  { cls: 'bg-blue-100 text-blue-700',    label: 'Por facturar' },
  PENDIENTE:     { cls: 'bg-amber-100 text-amber-700',  label: 'Pendiente' },
  VENCIDO:       { cls: 'bg-red-100 text-red-700',      label: 'Vencido' },
  RECHAZADO:     { cls: 'bg-gray-200 text-gray-600',    label: 'Rechazado' },
  EXPIRADA:      { cls: 'bg-gray-100 text-gray-400',    label: 'Expirada' },
}

function PillEstado({ estado }) {
  const cfg = ESTADO_PILL[estado] ?? { cls: 'bg-gray-100 text-gray-500', label: estado }
  return <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium whitespace-nowrap ${cfg.cls}`}>{cfg.label}</span>
}

const FILTROS_TIPO = [
  { value: '',         label: 'Todos los tipos' },
  { value: 'PREPAGO',  label: 'Prepago' },
  { value: 'POSPAGO',  label: 'Pospago' },
]

const FILTROS_VISTA = [
  { value: '',             label: 'Todos' },
  { value: 'POR_FACTURAR', label: 'Por facturar' },
  { value: 'FACTURADAS',   label: 'Facturadas' },
]

const FILTROS_ESTADO = [
  { value: '',             label: 'Todos los estados' },
  { value: 'PAGADO',       label: 'Pagado' },
  { value: 'POR_FACTURAR', label: 'Por facturar' },
  { value: 'PENDIENTE',    label: 'Pendiente' },
  { value: 'VENCIDO',      label: 'Vencido' },
  { value: 'RECHAZADO',    label: 'Rechazado' },
  { value: 'EXPIRADA',     label: 'Expirada' },
]

function FilaInfo({ label, valor }) {
  if (!valor) return null
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-gray-400">{label}</span>
      <span className="font-medium text-gray-700 truncate" title={valor}>{valor}</span>
    </div>
  )
}

function BotonEliminar({ onConfirmar, titulo, cuerpo }) {
  const [confirmando, setConfirmando] = useState(false)
  return (
    <>
      <button type="button" onClick={() => setConfirmando(true)}
        className="inline-flex items-center gap-1.5 text-xs font-medium text-red-500 hover:text-red-700">
        <IconoBasura className="h-3.5 w-3.5" />
        Eliminar
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

/** Los modales de detalle se dividen siempre en dos partes: "Información del pago" (lo
 *  transaccional — estado, fecha, método) y "Factura" (el documento en sí — cliente, ítems,
 *  total), cada una con su propio rótulo en mayúsculas para que se lean como dos bloques
 *  distintos y no como un solo recibo genérico. */
function SeccionInfoPago({ estado, fecha, simulado, filas = [] }) {
  return (
    <div>
      <h4 className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Información del pago</h4>
      <div className="bg-gray-50 rounded-lg border border-gray-100 p-4 space-y-2">
        <div className="flex items-center justify-between gap-3">
          <span className="text-xs text-gray-500">{fecha}</span>
          <PillEstado estado={estado} />
        </div>
        {simulado && <p className="text-xs font-medium text-amber-600">Pago simulado</p>}
        {filas.length > 0 && (
          <div className="pt-2 mt-1 border-t border-gray-200 space-y-1.5 text-xs">
            {filas.map((f, idx) => <FilaInfo key={idx} label={f.label} valor={f.valor} />)}
          </div>
        )}
      </div>
    </div>
  )
}

/** El documento como tal: número destacado en la franja superior (resaltada solo cuando ya
 *  existe una factura de verdad; en gris cuando todavía no se ha generado), y el resto del
 *  contenido de la factura debajo. */
function BloqueFactura({ numero, generada, children }) {
  return (
    <div>
      <h4 className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Factura</h4>
      <div className="border border-gray-200 rounded-lg overflow-hidden">
        <div className={`px-4 py-3 flex items-center justify-between gap-3 ${generada ? 'bg-primary-50' : 'bg-gray-50'}`}>
          <p className={`text-base font-bold truncate ${generada ? 'text-primary-900' : 'text-gray-500'}`}>{numero}</p>
          {!generada && <span className="flex-shrink-0 text-[11px] font-medium text-gray-400 uppercase tracking-wide">Sin generar</span>}
        </div>
        <div className="p-4 space-y-4">
          {children}
        </div>
      </div>
    </div>
  )
}

const MOTIVOS_NOTA = [
  { value: 'ANULACION',          label: 'Anulación' },
  { value: 'DEVOLUCION',         label: 'Devolución' },
  { value: 'DESCUENTO',          label: 'Descuento' },
  { value: 'AJUSTE_PRECIO',      label: 'Ajuste de precio' },
  { value: 'INTERESES_MORA',     label: 'Intereses de mora' },
  { value: 'GASTOS_ADICIONALES', label: 'Gastos adicionales' },
  { value: 'CORRECCION',         label: 'Corrección' },
  { value: 'OTRO',               label: 'Otro' },
]

/** Formulario para generar una nota crédito o débito sobre una orden o factura ya cargada en el
 *  modal de detalle — nunca navega a otra parte, todo pasa acá mismo. */
function ModalNuevaNota({ tipo, generar, montoSugerido, onClose, onCreada, onToast }) {
  const [monto, setMonto] = useState(montoSugerido != null ? String(montoSugerido) : '')
  const [motivo, setMotivo] = useState('ANULACION')
  const [observaciones, setObservaciones] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const esCredito = tipo === 'CREDITO'

  const guardar = async (e) => {
    e.preventDefault()
    const montoNum = Number(monto)
    if (!montoNum || montoNum <= 0) { setError('Ingresa un monto válido.'); return }
    setGuardando(true); setError(null)
    try {
      const nota = await generar({ monto: montoNum, motivo, observaciones: observaciones || null })
      onToast(`${esCredito ? 'Nota crédito' : 'Nota débito'} ${nota.referencia} generada`, 'exito')
      onCreada(nota)
      onClose()
    } catch (err) {
      setError(mensajeError(err, 'No se pudo generar la nota.'))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={esCredito ? 'Nueva nota crédito' : 'Nueva nota débito'} onClose={onClose} ancho="max-w-md">
      <form onSubmit={guardar} className="px-6 py-5 space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Monto</label>
          <input type="number" min="0.01" step="0.01" value={monto} onChange={(e) => setMonto(e.target.value)}
            required placeholder="Ej: 50000"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Motivo</label>
          <select value={motivo} onChange={(e) => setMotivo(e.target.value)}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            {MOTIVOS_NOTA.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Observaciones (opcional)</label>
          <textarea value={observaciones} onChange={(e) => setObservaciones(e.target.value)} rows={3}
            placeholder="Detalle interno de por qué se genera esta nota..."
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
        </div>

        <p className="text-xs text-gray-400">
          {esCredito
            ? 'Si el monto es igual al valor total del documento, se le restituye el saldo o cupo al cliente automáticamente.'
            : 'Queda como registro de que el cliente debe un mayor valor — no mueve saldo ni cupo automáticamente.'}
        </p>

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="submit" disabled={guardando}
            className={`text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors disabled:opacity-60 ${
              esCredito ? 'bg-primary-600 hover:bg-primary-700' : 'bg-gray-700 hover:bg-gray-800'
            }`}>
            {guardando ? 'Generando...' : `Generar nota ${esCredito ? 'crédito' : 'débito'}`}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/** Lista las notas crédito/débito ya generadas sobre un documento, más los botones para generar
 *  una nueva — ver ModalNuevaNota. `montoSugerido` es lo que falta por acreditar (para precargar
 *  el campo cuando se abre "Nota crédito"), no un tope forzado en el input. */
function SeccionNotasAjuste({ notas, montoDisponibleCredito, onGenerarCredito, onGenerarDebito, onNotaCreada, onToast, accionInicial }) {
  const [tipoNuevaNota, setTipoNuevaNota] = useState(accionInicial ?? null)

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <h4 className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Notas crédito / débito</h4>
        <div className="flex items-center gap-3">
          <button type="button" onClick={() => setTipoNuevaNota('CREDITO')}
            className="text-xs font-medium text-primary-600 hover:text-primary-800">+ Nota crédito</button>
          <button type="button" onClick={() => setTipoNuevaNota('DEBITO')}
            className="text-xs font-medium text-gray-500 hover:text-gray-700">+ Nota débito</button>
        </div>
      </div>

      {notas.length > 0 && (
        <div className="border border-gray-100 rounded-lg overflow-hidden divide-y divide-gray-50">
          {notas.map((n) => (
            <div key={n.idNota} className="px-3 py-2.5 flex items-center justify-between gap-3 text-xs">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className={`font-semibold ${n.tipo === 'CREDITO' ? 'text-primary-600' : 'text-gray-600'}`}>{n.referencia}</span>
                  <span className="text-gray-400">{MOTIVOS_NOTA.find((m) => m.value === n.motivo)?.label ?? n.motivo}</span>
                </div>
                <p className="text-gray-400 mt-0.5">{formatFecha(n.fechaGeneracion)}{n.generadaPorNombre ? ` · ${n.generadaPorNombre}` : ''}</p>
              </div>
              <span className={`font-medium flex-shrink-0 ${n.tipo === 'CREDITO' ? 'text-primary-700' : 'text-gray-700'}`}>
                {n.tipo === 'CREDITO' ? '− ' : '+ '}{formatearPrecio(n.montoTotal)}
              </span>
            </div>
          ))}
        </div>
      )}

      {tipoNuevaNota && (
        <ModalNuevaNota
          tipo={tipoNuevaNota}
          montoSugerido={tipoNuevaNota === 'CREDITO' ? montoDisponibleCredito : null}
          generar={tipoNuevaNota === 'CREDITO' ? onGenerarCredito : onGenerarDebito}
          onClose={() => setTipoNuevaNota(null)}
          onCreada={onNotaCreada}
          onToast={onToast}
        />
      )}
    </div>
  )
}

function DatosCliente({ nombre, nit, email, telefono, direccion, ciudad }) {
  return (
    <div className="bg-gray-50 rounded-lg border border-gray-100 p-4 grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
      <div className="col-span-2">
        <p className="text-gray-400">Cliente</p>
        <p className="font-medium text-gray-800">{nombre}</p>
      </div>
      {nit && <div><p className="text-gray-400">NIT</p><p className="font-medium text-gray-700">{nit}</p></div>}
      {telefono && <div><p className="text-gray-400">Teléfono</p><p className="font-medium text-gray-700">{telefono}</p></div>}
      {email && <div className="col-span-2"><p className="text-gray-400">Email</p><p className="font-medium text-gray-700 truncate">{email}</p></div>}
      {direccion && (
        <div className="col-span-2">
          <p className="text-gray-400">Dirección</p>
          <p className="font-medium text-gray-700">{direccion}{ciudad ? `, ${ciudad}` : ''}</p>
        </div>
      )}
    </div>
  )
}

/** Tabla de ítems con el mismo lenguaje visual que la tabla principal (thead gris, texto en
 *  mayúsculas) — para que un "detalle" se vea como parte del mismo documento, no como un recibo
 *  aparte. */
function TablaItemsFactura({ filas }) {
  if (!filas.length) return <p className="text-xs text-gray-400 text-center py-4">Sin ítems.</p>
  return (
    <div className="border border-gray-100 rounded-lg overflow-hidden">
      <table className="w-full text-xs">
        <thead className="bg-gray-50 text-gray-500 uppercase">
          <tr>
            <th className="px-3 py-2 text-left">Descripción</th>
            <th className="px-3 py-2 text-right">Valor</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-50">
          {filas.map((f, idx) => (
            <tr key={idx}>
              <td className="px-3 py-2">
                <p className="text-gray-700">{f.descripcion}</p>
                {f.subdescripcion && <p className="text-gray-400 text-[11px] mt-0.5">{f.subdescripcion}</p>}
              </td>
              <td className="px-3 py-2 text-right font-medium text-gray-800 whitespace-nowrap">{formatearPrecio(f.valor)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TotalesFactura({ filas = [], total }) {
  return (
    <div className="space-y-1.5 text-sm">
      {filas.map((f, idx) => (
        <div key={idx} className="flex items-center justify-between">
          <span className={f.resaltar ? 'text-green-600' : 'text-gray-500'}>{f.label}</span>
          <span className={f.resaltar ? 'text-green-600 font-medium' : 'text-gray-700'}>{f.valor}</span>
        </div>
      ))}
      <div className="flex items-center justify-between border-t border-gray-100 pt-2 mt-2">
        <span className="text-sm font-semibold text-gray-700">Total</span>
        <span className="text-lg font-bold text-primary-700">{formatearPrecio(total)}</span>
      </div>
    </div>
  )
}

/** Detalle de una orden prepago, con formato de factura — muestra el número de comprobante real
 *  (no solo la referencia interna de la orden) y los datos de facturación del cliente cuando ya
 *  se generó uno. Sin navegar a ninguna otra página: todo pasa en este modal, incluyendo
 *  eliminarla si sigue pendiente. */
function ModalDetalleOrden({ idOrdenCompra, onClose, onCambio, onToast, abrirNotaInicial }) {
  const [orden, setOrden] = useState(null)
  const [comprobante, setComprobante] = useState(null)
  const [notas, setNotas] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    pagosService.adminObtenerOrden(idOrdenCompra)
      .then((data) => {
        setOrden(data)
        if (data.idComprobante) {
          pagosService.adminObtenerComprobante(data.idComprobante).then(setComprobante).catch(() => {})
        }
      })
      .catch(() => setError('No se pudo cargar la orden.'))
    notasAjusteService.adminListarNotasOrden(idOrdenCompra).then(setNotas).catch(() => {})
  }, [idOrdenCompra])

  const eliminar = async () => {
    try {
      await pagosService.adminEliminarOrden(idOrdenCompra)
      onToast(`Orden ${orden.referencia} eliminada`, 'exito')
      onCambio()
      onClose()
    } catch (err) {
      onToast(mensajeError(err, 'No se pudo eliminar la orden'), 'error')
    }
  }

  const estadoUnificado = orden && (
    orden.estado === 'APROBADA' ? 'PAGADO' : orden.estado === 'RECHAZADA' ? 'RECHAZADO' : orden.estado === 'EXPIRADA' ? 'EXPIRADA' : 'PENDIENTE'
  )

  return (
    <Modal titulo="Detalle de facturación" onClose={onClose} ancho="max-w-lg">
      <div className="px-6 py-6">
        {error ? (
          <p className="text-sm text-red-500 text-center py-8">{error}</p>
        ) : !orden ? (
          <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
        ) : (
          <div className="space-y-6">
            <SeccionInfoPago
              estado={estadoUnificado}
              fecha={formatFecha(orden.fechaCreacion)}
              simulado={orden.modoSimulado}
              filas={[
                orden.metodoPago && { label: 'Método de pago', valor: orden.metodoPago },
                orden.numeroAprobacion && { label: 'N° de aprobación', valor: orden.numeroAprobacion },
              ].filter(Boolean)} />

            <BloqueFactura numero={comprobante ? comprobante.numero : orden.referencia} generada={!!comprobante}>
              <DatosCliente nombre={orden.nombreCliente}
                nit={comprobante?.nitCliente}
                email={comprobante?.emailCliente ?? orden.emailPagador}
                telefono={comprobante?.telefonoCliente ?? orden.telefonoPagador}
                direccion={comprobante?.direccionCliente}
                ciudad={comprobante?.ciudadCliente} />

              <TablaItemsFactura filas={(orden.items ?? []).map((i) => ({
                descripcion: i.nombreProceso,
                subdescripcion: `${i.cantidad} × ${formatearPrecio(i.valorUnitario)}`,
                valor: i.subtotal,
              }))} />

              <TotalesFactura
                filas={[
                  orden.montoDescuentoTotal > 0 && {
                    label: `Descuento${orden.codigoCuponAplicado ? ` (${orden.codigoCuponAplicado})` : ''}`,
                    valor: `− ${formatearPrecio(orden.montoDescuentoTotal)}`, resaltar: true,
                  },
                  orden.montoRecargosPendientes ? {
                    label: orden.montoRecargosPendientes < 0 ? 'Crédito por ciudad' : 'Recargos por ciudad',
                    valor: formatearPrecio(Math.abs(orden.montoRecargosPendientes)),
                  } : null,
                ].filter(Boolean)}
                total={orden.montoTotal} />
            </BloqueFactura>

            {orden.estado === 'APROBADA' && (
              <SeccionNotasAjuste
                notas={notas}
                montoDisponibleCredito={orden.montoTotal - notas.filter((n) => n.tipo === 'CREDITO').reduce((acc, n) => acc + n.montoTotal, 0)}
                onGenerarCredito={(payload) => notasAjusteService.adminGenerarNotaOrden(idOrdenCompra, 'CREDITO', payload)}
                onGenerarDebito={(payload) => notasAjusteService.adminGenerarNotaOrden(idOrdenCompra, 'DEBITO', payload)}
                onNotaCreada={(nota) => { setNotas((prev) => [nota, ...prev]); onCambio() }}
                onToast={onToast}
                accionInicial={abrirNotaInicial} />
            )}

            {orden.estado === 'PENDIENTE' && (
              <div className="flex justify-end">
                <BotonEliminar titulo={`Eliminar orden ${orden.referencia}`}
                  cuerpo="Esta orden está pendiente de pago y todavía no ha sido cobrada. Se eliminará por completo."
                  onConfirmar={eliminar} />
              </div>
            )}
          </div>
        )}
      </div>
    </Modal>
  )
}

/** Detalle de una factura pospago, con el mismo formato de factura — movimientos incluidos en el
 *  corte + intentos de pago. Igual que ModalDetalleOrden, todo pasa en el modal, sin navegar a
 *  otra página. */
function ModalDetalleFactura({ idFactura, onClose, onCambio, onToast, abrirNotaInicial }) {
  const [detalle, setDetalle] = useState(null)
  const [notas, setNotas] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    pospagoService.adminObtenerFactura(idFactura)
      .then(setDetalle)
      .catch(() => setError('No se pudo cargar la factura.'))
    notasAjusteService.adminListarNotasFactura(idFactura).then(setNotas).catch(() => {})
  }, [idFactura])

  const eliminar = async () => {
    try {
      await pospagoService.adminEliminarFactura(idFactura)
      onToast(`Factura ${detalle.factura.referencia} eliminada`, 'exito')
      onCambio()
      onClose()
    } catch (err) {
      onToast(mensajeError(err, 'No se pudo eliminar la factura'), 'error')
    }
  }

  return (
    <Modal titulo="Detalle de facturación" onClose={onClose} ancho="max-w-lg">
      <div className="px-6 py-6">
        {error ? (
          <p className="text-sm text-red-500 text-center py-8">{error}</p>
        ) : !detalle ? (
          <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
        ) : (() => {
          const f = detalle.factura
          const estadoUnificado = f.estado === 'PAGADA' ? 'PAGADO' : f.estado === 'VENCIDA' ? 'VENCIDO' : 'PENDIENTE'
          return (
            <div className="space-y-6">
              <SeccionInfoPago
                estado={estadoUnificado}
                fecha={`Corte ${formatFecha(f.fechaCorte)} · Vence ${formatFecha(f.fechaVencimiento)}`}
                filas={(detalle.intentosPago ?? []).map((ip) => ({ label: ip.referencia, valor: ip.estado }))} />

              <BloqueFactura numero={f.referencia} generada>
                <DatosCliente nombre={f.nombreCliente} />

                <TablaItemsFactura filas={(detalle.movimientos ?? []).map((m) => ({
                  descripcion: m.nombreProceso,
                  subdescripcion: m.nombreCandidato,
                  valor: m.montoTotal,
                }))} />

                <TotalesFactura total={f.montoTotal} />
              </BloqueFactura>

              <SeccionNotasAjuste
                notas={notas}
                montoDisponibleCredito={f.montoTotal - notas.filter((n) => n.tipo === 'CREDITO').reduce((acc, n) => acc + n.montoTotal, 0)}
                onGenerarCredito={(payload) => notasAjusteService.adminGenerarNotaFactura(idFactura, 'CREDITO', payload)}
                onGenerarDebito={(payload) => notasAjusteService.adminGenerarNotaFactura(idFactura, 'DEBITO', payload)}
                onNotaCreada={(nota) => { setNotas((prev) => [nota, ...prev]); onCambio() }}
                onToast={onToast}
                accionInicial={abrirNotaInicial} />

              {f.estado === 'PENDIENTE' && (
                <div className="flex justify-end">
                  <BotonEliminar titulo={`Eliminar factura ${f.referencia}`}
                    cuerpo="Esta factura está pendiente de pago. Sus servicios volverán a quedar pendientes de facturar."
                    onConfirmar={eliminar} />
                </div>
              )}
            </div>
          )
        })()}
      </div>
    </Modal>
  )
}

function fechaVencimientoPorDefecto() {
  const d = new Date()
  d.setDate(d.getDate() + 15)
  return d.toISOString().slice(0, 10)
}

/** Consumo pospago de un cliente que TODAVÍA no tiene ninguna factura generada — lista los
 *  servicios pendientes y deja generar la factura eligiendo fecha de vencimiento, todo en el
 *  mismo modal (antes esto solo se veía entrando al cliente puntual). */
function ModalConsumoPendiente({ idCliente, nombreCliente, onClose, onCambio, onToast }) {
  const [movimientos, setMovimientos] = useState(null)
  const [error, setError] = useState(null)
  const [fechaVencimiento, setFechaVencimiento] = useState(fechaVencimientoPorDefecto)
  const [generando, setGenerando] = useState(false)
  const [seleccionados, setSeleccionados] = useState(new Set())

  useEffect(() => {
    pospagoService.adminMovimientosPendientes(idCliente)
      .then((data) => { setMovimientos(data); setSeleccionados(new Set(data.map((m) => m.idMovimiento))) })
      .catch(() => setError('No se pudo cargar el consumo pendiente.'))
  }, [idCliente])

  const todosSeleccionados = movimientos != null && seleccionados.size === movimientos.length
  const total = (movimientos ?? []).filter((m) => seleccionados.has(m.idMovimiento)).reduce((acc, m) => acc + (m.montoTotal ?? 0), 0)

  const alternar = (idMovimiento) => {
    setSeleccionados((prev) => {
      const next = new Set(prev)
      if (next.has(idMovimiento)) next.delete(idMovimiento); else next.add(idMovimiento)
      return next
    })
  }

  const generarFactura = async () => {
    setGenerando(true)
    try {
      await pospagoService.adminGenerarFactura(idCliente, fechaVencimiento, Array.from(seleccionados))
      onToast(`Factura generada para ${nombreCliente}`, 'exito')
      onCambio()
      onClose()
    } catch (err) {
      onToast(mensajeError(err, 'No se pudo generar la factura'), 'error')
    } finally {
      setGenerando(false)
    }
  }

  return (
    <Modal titulo="Detalle de facturación" onClose={onClose} ancho="max-w-lg">
      <div className="px-6 py-6">
        {error ? (
          <p className="text-sm text-red-500 text-center py-8">{error}</p>
        ) : movimientos === null ? (
          <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
        ) : movimientos.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-8">Este cliente ya no tiene consumo pendiente de facturar.</p>
        ) : (
          <div className="space-y-6">
            <SeccionInfoPago estado="PENDIENTE" fecha={`Consumo acumulado de ${nombreCliente}, todavía sin corte`} />

            <BloqueFactura numero={nombreCliente} generada={false}>
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[11px] text-gray-400">Elige qué incluir en este corte — lo que no marques queda pendiente para el próximo.</span>
                  <button type="button"
                    onClick={() => setSeleccionados(todosSeleccionados ? new Set() : new Set(movimientos.map((m) => m.idMovimiento)))}
                    className="text-xs font-medium text-primary-600 hover:text-primary-800 whitespace-nowrap ml-3">
                    {todosSeleccionados ? 'Ninguno' : 'Todos'}
                  </button>
                </div>
                <div className="border border-gray-100 rounded-lg overflow-hidden divide-y divide-gray-50">
                  {movimientos.map((m) => (
                    <label key={m.idMovimiento} className="flex items-center gap-3 px-3 py-2 text-xs cursor-pointer hover:bg-gray-50">
                      <input type="checkbox" checked={seleccionados.has(m.idMovimiento)} onChange={() => alternar(m.idMovimiento)}
                        className="h-3.5 w-3.5 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                      <div className="flex-1 min-w-0">
                        <p className="text-gray-700 truncate">{m.nombreProceso}</p>
                        <p className="text-gray-400 text-[11px] mt-0.5 truncate">
                          {m.nombreCandidato}{m.nombreCiudad ? ` · ${m.nombreCiudad}` : ''}{m.montoRecargoCiudad > 0 ? ` · + ${formatearPrecio(m.montoRecargoCiudad)} recargo` : ''}
                        </p>
                      </div>
                      <span className="font-medium text-gray-800 whitespace-nowrap">{formatearPrecio(m.montoTotal)}</span>
                    </label>
                  ))}
                </div>
              </div>

              <TotalesFactura total={total} />

              <div className="flex items-end gap-3 pt-3 border-t border-gray-100">
                <div className="flex-1">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Fecha de vencimiento</label>
                  <input type="date" value={fechaVencimiento} onChange={(e) => setFechaVencimiento(e.target.value)}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                </div>
                <button type="button" onClick={generarFactura} disabled={generando || seleccionados.size === 0}
                  className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors whitespace-nowrap">
                  {generando ? 'Generando...' : `Generar factura (${seleccionados.size})`}
                </button>
              </div>
            </BloqueFactura>
          </div>
        )}
      </div>
    </Modal>
  )
}

/** Barra "Pagado / Pendiente / Vencido" estilo Quipu: el número grande de cada estado en su
 *  color, y debajo una barra de progreso apilada con el porcentaje que representa cada uno sobre
 *  el total (prepago + pospago sumados). Son montos en pesos — el conteo de "Por facturar" vive en
 *  su propia tarjeta aparte (TarjetaPorFacturar), porque es una cantidad de documentos, no plata,
 *  y mezclarlos en la misma fila confundía. */
function BarraResumenPagos({ pagado, pendiente, vencido }) {
  const cargando = pagado == null
  const total = (pagado ?? 0) + (pendiente ?? 0) + (vencido ?? 0)
  const pct = (valor) => (total > 0 ? ((valor ?? 0) / total) * 100 : 0)
  const pctPagado = pct(pagado)
  const pctPendiente = pct(pendiente)
  const pctVencido = pct(vencido)

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-5">
      <div className="grid grid-cols-3 gap-4 mb-4">
        <div>
          <p className="text-xs font-medium text-gray-500 mb-1">Pagado</p>
          <p className="text-2xl font-semibold text-green-600 tabular-nums">{cargando ? '—' : formatearPrecio(pagado)}</p>
        </div>
        <div>
          <p className="text-xs font-medium text-gray-500 mb-1">Pendiente</p>
          <p className="text-2xl font-semibold text-amber-500 tabular-nums">{cargando ? '—' : formatearPrecio(pendiente)}</p>
        </div>
        <div>
          <p className="text-xs font-medium text-gray-500 mb-1">Vencido</p>
          <p className="text-2xl font-semibold text-red-600 tabular-nums">{cargando ? '—' : formatearPrecio(vencido)}</p>
        </div>
      </div>

      {!cargando && total > 0 && (
        <>
          <div className="h-2 rounded-full bg-gray-100 overflow-hidden flex">
            {pctPagado > 0 && <div className="h-full bg-green-500" style={{ width: `${pctPagado}%` }} />}
            {pctPendiente > 0 && <div className="h-full bg-amber-400" style={{ width: `${pctPendiente}%` }} />}
            {pctVencido > 0 && <div className="h-full bg-red-500" style={{ width: `${pctVencido}%` }} />}
          </div>
          <div className="flex items-center justify-between text-[11px] text-gray-400 mt-1.5">
            <span className="text-green-600 font-medium">{pctPagado.toFixed(0)}%</span>
            <span className="flex gap-3">
              <span className="text-amber-500 font-medium">{pctPendiente.toFixed(0)}%</span>
              <span className="text-red-600 font-medium">{pctVencido.toFixed(0)}%</span>
            </span>
          </div>
        </>
      )}
    </div>
  )
}

function IconoDocumentoReloj({ className = 'h-5 w-5' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h3m-3 4h2m-5 4h9a2 2 0 002-2V7.828a2 2 0 00-.586-1.414l-3.828-3.828A2 2 0 0012.172 2H6a2 2 0 00-2 2v14a2 2 0 002 2z" />
    </svg>
  )
}

function IconoDocumentoCheck({ className = 'h-5 w-5' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.5l1.5 1.5L15 9.5M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h9.172a2 2 0 011.414.586l3.828 3.828A2 2 0 0121 8.828V19a2 2 0 01-2 2z" />
    </svg>
  )
}

function FilaConteoDocumento({ label, subtitulo, count, colorActivo, onVer }) {
  const cargando = count == null
  const activo = !cargando && count > 0
  return (
    <button type="button" onClick={onVer} disabled={!activo}
      className={`w-full flex items-center gap-3 p-4 text-left transition-colors ${activo ? `${colorActivo.hoverBg} cursor-pointer` : 'cursor-default'}`}>
      <div className={`flex-shrink-0 h-10 w-10 rounded-full flex items-center justify-center ${activo ? colorActivo.iconBg : 'bg-gray-100 text-gray-300'}`}>
        {colorActivo.icono}
      </div>
      <div>
        <p className="text-xs font-medium text-gray-500 mb-0.5">{label}</p>
        <p className={`text-2xl font-semibold tabular-nums ${activo ? colorActivo.texto : 'text-gray-300'}`}>{cargando ? '—' : count}</p>
        <p className="text-[11px] text-gray-400">{count === 1 ? subtitulo.singular : subtitulo.plural}</p>
      </div>
    </button>
  )
}

/** Tarjeta aparte para las CANTIDADES de documentos (por facturar / ya facturados) — separada a
 *  propósito de BarraResumenPagos, que son montos en pesos; mezclarlas en la misma fila de números
 *  confundía. Cada fila es clicable y activa el filtro correspondiente en la tabla de abajo. */
function TarjetaConteoDocumentos({ porFacturarCount, facturadasCount, onVerPorFacturar, onVerFacturadas }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 divide-y divide-gray-100 h-full flex flex-col">
      <FilaConteoDocumento label="Por facturar" count={porFacturarCount} onVer={onVerPorFacturar}
        subtitulo={{ singular: 'documento sin generar', plural: 'documentos sin generar' }}
        colorActivo={{ iconBg: 'bg-blue-100 text-blue-600', texto: 'text-blue-700', hoverBg: 'hover:bg-blue-50', icono: <IconoDocumentoReloj /> }} />
      <FilaConteoDocumento label="Facturadas" count={facturadasCount} onVer={onVerFacturadas}
        subtitulo={{ singular: 'documento ya generado', plural: 'documentos ya generados' }}
        colorActivo={{ iconBg: 'bg-green-100 text-green-600', texto: 'text-green-700', hoverBg: 'hover:bg-green-50', icono: <IconoDocumentoCheck /> }} />
    </div>
  )
}

/** Tabla unificada de documentos de facturación — mezcla órdenes prepago y facturas pospago (ver
 *  AdminFacturacionController en el backend) para que el admin vea todo en un solo lugar. El chip
 *  "Por facturar" filtra a lo que realmente necesita una acción (factura por generar); las filas
 *  que caen en ese grupo también quedan resaltadas en la tabla para que salten a la vista sin
 *  tener que activar el filtro. Todas las acciones de una fila (ver, generar, notas) viven en un
 *  solo menú de 3 puntos, en vez de íconos sueltos. */
function TablaDocumentos({ onToast, vistaDocumento, setVistaDocumento, onListaActualizada }) {
  const [documentos, setDocumentos] = useState(null)
  const [total, setTotal] = useState(0)
  const [error, setError] = useState(null)
  const [tipo, setTipo] = useState('')
  const [estado, setEstado] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [busquedaDebounced, setBusquedaDebounced] = useState('')
  const [pagina, setPagina] = useState(1)
  const [generandoRef, setGenerandoRef] = useState(null)
  const [verDoc, setVerDoc] = useState(null)
  const debounceRef = useRef(null)

  useEffect(() => {
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => setBusquedaDebounced(busqueda.trim()), 300)
    return () => clearTimeout(debounceRef.current)
  }, [busqueda])

  useEffect(() => { setPagina(1) }, [tipo, estado, vistaDocumento, busquedaDebounced])

  const cargar = () => {
    facturacionService.adminListarDocumentos({
      tipo: tipo || undefined,
      estado: estado || undefined,
      porFacturar: vistaDocumento === 'POR_FACTURAR' || undefined,
      facturado: vistaDocumento === 'FACTURADAS' || undefined,
      q: busquedaDebounced || undefined,
      page: pagina - 1,
      size: POR_PAGINA,
    })
      .then((data) => { setDocumentos(data.content ?? []); setTotal(data.totalElements ?? 0); onListaActualizada?.() })
      .catch(() => setError('No se pudo cargar el listado.'))
  }

  useEffect(cargar, [tipo, estado, vistaDocumento, busquedaDebounced, pagina])

  const generarFactura = async (doc) => {
    setGenerandoRef(doc.referencia)
    try {
      await pagosService.adminGenerarComprobante(doc.idOrdenCompra)
      onToast(`Factura generada para ${doc.nombreCliente}`, 'exito')
      cargar()
    } catch (err) {
      onToast(mensajeError(err, 'No se pudo generar la factura'), 'error')
    } finally {
      setGenerandoRef(null)
    }
  }

  const abrirDetalle = (d) => setVerDoc({ ...d, accionInicial: null })
  const abrirNota = (d, tipoNota) => setVerDoc({ ...d, accionInicial: tipoNota })

  const desde = total === 0 ? 0 : (pagina - 1) * POR_PAGINA + 1
  const hasta = Math.min(pagina * POR_PAGINA, total)

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2 mb-4">
        <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
          {FILTROS_TIPO.map((f) => (
            <button key={f.value} type="button" onClick={() => setTipo(f.value)}
              className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                tipo === f.value ? 'bg-white text-gray-800 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>
              {f.label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
          {FILTROS_VISTA.map((f) => (
            <button key={f.value} type="button" onClick={() => setVistaDocumento(f.value)}
              className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                vistaDocumento === f.value ? 'bg-white text-gray-800 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>
              {f.label}{vistaDocumento === f.value && f.value && total > 0 ? ` (${total})` : ''}
            </button>
          ))}
        </div>

        <select value={estado} onChange={(e) => setEstado(e.target.value)}
          className="border border-gray-200 rounded-lg px-3 py-2 text-xs font-medium focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white text-gray-600">
          {FILTROS_ESTADO.map((f) => <option key={f.value} value={f.value}>{f.label}</option>)}
        </select>

        <input type="search" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Buscar por cliente o referencia..."
          className="flex-1 min-w-[220px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>

      {documentos === null ? (
        <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
      ) : error ? (
        <p className="text-sm text-red-500 text-center py-16">{error}</p>
      ) : !documentos.length ? (
        <p className="text-sm text-gray-400 text-center py-16">
          {vistaDocumento === 'POR_FACTURAR'
            ? 'No hay nada pendiente por facturar con este filtro.'
            : vistaDocumento === 'FACTURADAS'
              ? 'No hay documentos ya facturados con este filtro.'
              : 'No hay documentos que coincidan con el filtro.'}
        </p>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <div className="px-4 py-2.5 border-b border-gray-100 text-xs text-gray-400">
            Mostrando {desde}-{hasta} de {total} resultado{total !== 1 ? 's' : ''}
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Estado</th>
                  <th className="px-4 py-3 text-left">Cliente</th>
                  <th className="px-4 py-3 text-left">Documento</th>
                  <th className="px-4 py-3 text-left">Emisión</th>
                  <th className="px-4 py-3 text-left">Vencimiento</th>
                  <th className="px-4 py-3 text-right">Nota crédito</th>
                  <th className="px-4 py-3 text-right">Nota débito</th>
                  <th className="px-4 py-3 text-right">Total</th>
                  <th className="px-4 py-3 text-center">···</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {documentos.map((d) => {
                  const requiereAccion = d.puedeGenerarFactura || d.esConsumoPendiente
                  const numero = numeroDocumento(d)
                  const puedeNotas = !d.esConsumoPendiente && (d.tipo === 'POSPAGO' || d.estado === 'PAGADO' || d.estado === 'POR_FACTURAR')
                  return (
                    <tr key={`${d.tipo}-${d.referencia}-${d.idCliente}`}
                      className={`transition-colors ${requiereAccion ? 'bg-amber-50/50 hover:bg-amber-50' : 'hover:bg-gray-50'}`}>
                      <td className={`px-4 py-2.5 ${requiereAccion ? 'border-l-2 border-amber-400' : ''}`}><PillEstado estado={d.estado} /></td>
                      <td className="px-4 py-2.5">
                        <button onClick={() => abrirDetalle(d)} className="text-left block group max-w-[220px]">
                          <span className={`text-[10px] font-semibold uppercase tracking-wide ${d.tipo === 'PREPAGO' ? 'text-blue-600' : 'text-purple-600'}`}>
                            {d.tipo === 'PREPAGO' ? 'Prepago' : 'Pospago'}
                          </span>
                          <div className="font-medium text-gray-800 group-hover:text-primary-600 group-hover:underline transition-colors truncate">
                            {d.nombreCliente}
                          </div>
                          {d.nit && <div className="text-[11px] text-gray-400">NIT {d.nit}</div>}
                        </button>
                      </td>
                      <td className="px-4 py-2.5 whitespace-nowrap">
                        {numero
                          ? <span className="font-mono text-xs font-medium text-gray-700">{numero}</span>
                          : <span className="text-xs text-gray-400 italic">Sin generar</span>}
                      </td>
                      <td className="px-4 py-2.5 text-gray-500 whitespace-nowrap">{formatFecha(d.fechaEmision)}</td>
                      <td className="px-4 py-2.5 text-gray-500 whitespace-nowrap">{formatFecha(d.fechaVencimiento)}</td>
                      <td className="px-4 py-2.5 text-right whitespace-nowrap tabular-nums">
                        {d.montoNotasCredito > 0
                          ? <span className="text-primary-600 font-medium">− {formatearPrecio(d.montoNotasCredito)}</span>
                          : <span className="text-gray-300">—</span>}
                      </td>
                      <td className="px-4 py-2.5 text-right whitespace-nowrap tabular-nums">
                        {d.montoNotasDebito > 0
                          ? <span className="text-gray-600 font-medium">+ {formatearPrecio(d.montoNotasDebito)}</span>
                          : <span className="text-gray-300">—</span>}
                      </td>
                      <td className="px-4 py-2.5 text-right whitespace-nowrap">
                        {(d.montoNotasCredito > 0 || d.montoNotasDebito > 0) ? (
                          <div>
                            <div className="text-gray-400 text-[11px] line-through tabular-nums">{formatearPrecio(d.montoTotal)}</div>
                            <div className="font-semibold text-gray-800 tabular-nums">
                              {formatearPrecio(d.montoTotal - d.montoNotasCredito + d.montoNotasDebito)}
                            </div>
                          </div>
                        ) : (
                          <span className="font-semibold text-gray-800 tabular-nums">{formatearPrecio(d.montoTotal)}</span>
                        )}
                      </td>
                      <td className="px-4 py-2.5 text-center">
                        <MenuAcciones opciones={[
                          {
                            label: d.esConsumoPendiente ? 'Ver y generar factura' : 'Ver detalle',
                            icono: <IconoOjo className="h-3.5 w-3.5" />,
                            onClick: () => abrirDetalle(d),
                          },
                          d.puedeGenerarFactura && {
                            label: generandoRef === d.referencia ? 'Generando...' : 'Generar factura',
                            icono: <IconoDocumentoMas className="h-3.5 w-3.5" />,
                            onClick: () => generarFactura(d),
                          },
                          puedeNotas && {
                            label: 'Nota crédito',
                            icono: <IconoNota className="h-3.5 w-3.5" />,
                            destacado: 'text-primary-600',
                            onClick: () => abrirNota(d, 'CREDITO'),
                          },
                          puedeNotas && {
                            label: 'Nota débito',
                            icono: <IconoNota className="h-3.5 w-3.5" />,
                            onClick: () => abrirNota(d, 'DEBITO'),
                          },
                        ]} />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <Paginacion pagina={pagina} total={total} porPagina={POR_PAGINA} onChange={setPagina} />
        </div>
      )}

      {verDoc?.tipo === 'PREPAGO' && (
        <ModalDetalleOrden idOrdenCompra={verDoc.idOrdenCompra} abrirNotaInicial={verDoc.accionInicial}
          onClose={() => setVerDoc(null)} onCambio={cargar} onToast={onToast} />
      )}
      {verDoc?.tipo === 'POSPAGO' && verDoc.esConsumoPendiente && (
        <ModalConsumoPendiente idCliente={verDoc.idCliente} nombreCliente={verDoc.nombreCliente}
          onClose={() => setVerDoc(null)} onCambio={cargar} onToast={onToast} />
      )}
      {verDoc?.tipo === 'POSPAGO' && !verDoc.esConsumoPendiente && (
        <ModalDetalleFactura idFactura={verDoc.idFactura} abrirNotaInicial={verDoc.accionInicial}
          onClose={() => setVerDoc(null)} onCambio={cargar} onToast={onToast} />
      )}
    </div>
  )
}

/** Facturación admin: arriba, la barra "Pagado / Pendiente / Vencido" (prepago + pospago
 *  sumados); debajo, la tabla unificada con todos los documentos de facturación — desde ahí se
 *  filtra por tipo/estado, se busca por cliente y se generan las facturas prepago pendientes. */
export default function Facturacion() {
  const [toast, setToast] = useState(null)
  const [resumen, setResumen] = useState({ pagado: null, pendiente: null, vencido: null })
  const [porFacturarCount, setPorFacturarCount] = useState(null)
  const [facturadasCount, setFacturadasCount] = useState(null)
  const [vistaDocumento, setVistaDocumento] = useState('')
  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  const cargarConteos = () => {
    facturacionService.adminListarDocumentos({ porFacturar: true, page: 0, size: 1 })
      .then((data) => setPorFacturarCount(data.totalElements ?? 0))
      .catch(() => {})
    facturacionService.adminListarDocumentos({ facturado: true, page: 0, size: 1 })
      .then((data) => setFacturadasCount(data.totalElements ?? 0))
      .catch(() => {})
  }

  useEffect(() => {
    Promise.all([pagosService.adminResumenPagos(), pospagoService.adminResumenFacturas()])
      .then(([prepago, pospago]) => {
        setResumen({
          pagado: (prepago.pagado ?? 0) + (pospago.pagado ?? 0),
          pendiente: (prepago.pendiente ?? 0) + (pospago.pendiente ?? 0),
          vencido: pospago.vencido ?? 0,
        })
      })
      .catch(() => {})
    cargarConteos()
  }, [])

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Facturación</h2>
        <p className="text-sm text-gray-500 mt-0.5">Todas las órdenes y facturas, prepago y pospago, en un solo lugar.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_260px] gap-4 mb-6 items-stretch">
        <BarraResumenPagos pagado={resumen.pagado} pendiente={resumen.pendiente} vencido={resumen.vencido} />
        <TarjetaConteoDocumentos porFacturarCount={porFacturarCount} facturadasCount={facturadasCount}
          onVerPorFacturar={() => setVistaDocumento('POR_FACTURAR')} onVerFacturadas={() => setVistaDocumento('FACTURADAS')} />
      </div>

      <TablaDocumentos onToast={mostrarToast} vistaDocumento={vistaDocumento} setVistaDocumento={setVistaDocumento}
        onListaActualizada={cargarConteos} />

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

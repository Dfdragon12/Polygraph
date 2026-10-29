import { useId, useEffect, useRef, useState } from 'react'
import gestionCuentasService from '../../services/gestionCuentasService'
import pospagoService from '../../services/pospagoService'
import Toast from '../../components/Toast'
import Paginacion from '../../components/ui/Paginacion'
import { Modal } from '../../components/ui/Modal'
import MenuAcciones from '../../components/ui/MenuAcciones'

const POR_PAGINA = 10

function formatearPrecio(valor) {
  if (valor == null) return '—'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor)
}

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

const ESTADO_MORA_PILL = {
  NORMAL:     { cls: 'bg-green-100 text-green-700',  label: 'Al día' },
  EN_MORA:    { cls: 'bg-amber-100 text-amber-700',  label: 'En mora' },
  SUSPENDIDO: { cls: 'bg-red-100 text-red-700',      label: 'Suspendido' },
}

function PillEstadoMora({ estado }) {
  if (!estado) return <span className="text-gray-300">—</span>
  const cfg = ESTADO_MORA_PILL[estado] ?? { cls: 'bg-gray-100 text-gray-500', label: estado }
  return <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium whitespace-nowrap ${cfg.cls}`}>{cfg.label}</span>
}

const FILTROS_TIPO = [
  { value: '',         label: 'Todos' },
  { value: 'PREPAGO',  label: 'Prepago' },
  { value: 'POSPAGO',  label: 'Pospago' },
]

function FilaInfo({ label, valor }) {
  if (valor == null) return null
  return (
    <div className="flex items-center justify-between gap-3 text-xs py-1.5">
      <span className="text-gray-400">{label}</span>
      <span className="font-medium text-gray-700">{valor}</span>
    </div>
  )
}

/** Detalle de un cliente PREPAGO — saldo disponible desglosado por proceso. Es solo lectura: el
 * saldo prepago se acredita comprando (ver módulo de Pagos), no se asigna a mano desde acá. */
function ModalDetallePrepago({ idCliente, nombreCliente, onClose }) {
  const [saldos, setSaldos] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    gestionCuentasService.adminObtenerSaldos(idCliente)
      .then(setSaldos)
      .catch(() => setError('No se pudo cargar el saldo.'))
  }, [idCliente])

  return (
    <Modal titulo="Saldo prepago" subtitulo={nombreCliente} onClose={onClose} ancho="max-w-lg">
      <div className="px-6 py-6">
        {error ? (
          <p className="text-sm text-red-500 text-center py-8">{error}</p>
        ) : saldos === null ? (
          <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
        ) : !saldos.length ? (
          <p className="text-sm text-gray-400 text-center py-8">Este cliente no tiene saldo comprado todavía.</p>
        ) : (
          <div className="border border-gray-100 rounded-lg overflow-hidden">
            <table className="table-stack w-full text-xs">
              <thead className="bg-gray-50 text-gray-500 uppercase">
                <tr>
                  <th className="px-3 py-2 text-left">Proceso</th>
                  <th className="px-3 py-2 text-right">Disponible</th>
                  <th className="px-3 py-2 text-right">Comprado</th>
                  <th className="px-3 py-2 text-right">Consumido</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {saldos.map((s) => (
                  <tr key={s.idProceso}>
                    <td className="px-3 py-2 text-gray-700">{s.nombreProceso}</td>
                    <td className="px-3 py-2 text-right font-semibold text-gray-800 tabular-nums">{s.cantidadDisponible}</td>
                    <td className="px-3 py-2 text-right text-gray-500 tabular-nums">{s.cantidadComprada}</td>
                    <td className="px-3 py-2 text-right text-gray-500 tabular-nums">{s.cantidadConsumida}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Modal>
  )
}

/** Detalle de un cliente POSPAGO — estado de cuenta (crédito, consumo sin facturar, facturas
 * pendientes) más el formulario para asignar/ajustar el cupo de crédito. Sigue la misma regla que
 * ya tenía el backend: creditoDisponible nunca se edita a mano, solo limiteCredito — el disponible
 * se ajusta solo por el delta para no borrar el consumo que ya llevaba registrado. */
function ModalDetallePospago({ idCliente, nombreCliente, onClose, onCambio, onToast }) {
  const idCampos = useId()
  const [detalle, setDetalle] = useState(null)
  const [error, setError] = useState(null)
  const [limiteCredito, setLimiteCredito] = useState('')
  const [estadoMora, setEstadoMora] = useState('NORMAL')
  const [requiereAprobacion, setRequiereAprobacion] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [errorForm, setErrorForm] = useState(null)

  const cargar = () => {
    gestionCuentasService.adminListarEstadoCuentas()
      .then((filas) => {
        const fila = filas.find((f) => f.idCliente === idCliente)
        if (fila) {
          setLimiteCredito(fila.limiteCredito != null ? String(fila.limiteCredito) : '0')
          setEstadoMora(fila.estadoMora ?? 'NORMAL')
          setRequiereAprobacion(!!fila.requiereAprobacion)
        }
      })
      .catch(() => {})
    pospagoService.adminEstadoCuenta(idCliente)
      .then(setDetalle)
      .catch(() => setError('No se pudo cargar el estado de cuenta.'))
  }

  useEffect(cargar, [idCliente])

  const guardar = async (e) => {
    e.preventDefault()
    setErrorForm(null)
    const monto = Number(limiteCredito)
    if (Number.isNaN(monto) || monto < 0) { setErrorForm('Ingresa un límite de crédito válido.'); return }
    setGuardando(true)
    try {
      await gestionCuentasService.adminActualizarPospago(idCliente, {
        limiteCredito: monto, estadoMora, requiereAprobacion,
      })
      onToast(`Crédito de ${nombreCliente} actualizado`, 'exito')
      onCambio()
      cargar()
    } catch (err) {
      setErrorForm(mensajeError(err, 'No se pudo actualizar el crédito.'))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Crédito pospago" subtitulo={nombreCliente} onClose={onClose} ancho="max-w-lg">
      <div className="px-6 py-6 space-y-6">
        {error ? (
          <p className="text-sm text-red-500 text-center py-4">{error}</p>
        ) : !detalle ? (
          <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
        ) : (
          <div className="bg-gray-50 rounded-lg border border-gray-100 p-4">
            <FilaInfo label="Límite de crédito" valor={formatearPrecio(detalle.limiteCredito)} />
            <FilaInfo label="Crédito disponible" valor={formatearPrecio(detalle.creditoDisponible)} />
            <FilaInfo label="Consumo sin facturar" valor={formatearPrecio(detalle.consumidoSinFacturar)} />
            <FilaInfo label="Facturas pendientes" valor={formatearPrecio(detalle.montoFacturasPendientes)} />
          </div>
        )}

        <form onSubmit={guardar} className="space-y-4 pt-1 border-t border-gray-100">
          <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider pt-3">Asignar / ajustar cupo</p>

          {errorForm && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{errorForm}</div>}

          <div>
            <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Límite de crédito</label>
            <input id={`${idCampos}-1`} type="number" min="0" step="1000" value={limiteCredito} onChange={(e) => setLimiteCredito(e.target.value)}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            <p className="text-[11px] text-gray-400 mt-1">El crédito disponible se ajusta solo, por la diferencia — no borra el consumo ya registrado.</p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Estado de mora</label>
              <select id={`${idCampos}-2`} value={estadoMora} onChange={(e) => setEstadoMora(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
                <option value="NORMAL">Al día</option>
                <option value="EN_MORA">En mora</option>
                <option value="SUSPENDIDO">Suspendido</option>
              </select>
            </div>
            <div className="flex items-end pb-2.5">
              <label className="flex items-center gap-2 text-xs text-gray-600">
                <input type="checkbox" checked={requiereAprobacion} onChange={(e) => setRequiereAprobacion(e.target.checked)}
                  className="h-3.5 w-3.5 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                Requiere estudio de crédito
              </label>
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-1">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
            <button type="submit" disabled={guardando}
              className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
              {guardando ? 'Guardando...' : 'Guardar cupo'}
            </button>
          </div>
        </form>
      </div>
    </Modal>
  )
}

/** Tabla unificada de estado de cuenta de clientes — prepago y pospago juntos, mismo patrón que
 * la tabla de Facturacion.jsx (chips de tipo, búsqueda, menú de 3 puntos por fila). */
export default function GestionCuentas() {
  const [filas, setFilas] = useState(null)
  const [error, setError] = useState(null)
  const [tipo, setTipo] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [pagina, setPagina] = useState(1)
  const [verCliente, setVerCliente] = useState(null)
  const [toast, setToast] = useState(null)

  const mostrarToast = (mensaje, tipoToast = 'exito') => setToast({ mensaje, tipo: tipoToast })

  const cargar = () => {
    gestionCuentasService.adminListarEstadoCuentas()
      .then(setFilas)
      .catch(() => setError('No se pudo cargar la información de cuentas.'))
  }

  useEffect(cargar, [])
  useEffect(() => setPagina(1), [tipo, busqueda])

  const filtradas = (filas ?? [])
    .filter((f) => !tipo || f.tipoCliente === tipo)
    .filter((f) => {
      const q = busqueda.trim().toLowerCase()
      if (!q) return true
      return f.nombreCliente?.toLowerCase().includes(q) || f.nit?.toLowerCase().includes(q)
    })

  const total = filtradas.length
  const desde = total === 0 ? 0 : (pagina - 1) * POR_PAGINA + 1
  const hasta = Math.min(pagina * POR_PAGINA, total)
  const pagina_actual = filtradas.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA)

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Gestión de cuentas</h2>
        <p className="text-sm text-gray-500 mt-0.5">Saldo prepago y cupo de crédito pospago de cada cliente, en un solo lugar.</p>
      </div>

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
        <input aria-label="Buscar por cliente o NIT..." type="search" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Buscar por cliente o NIT..."
          className="flex-1 min-w-[220px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>

      {filas === null ? (
        <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
      ) : error ? (
        <p className="text-sm text-red-500 text-center py-16">{error}</p>
      ) : !filtradas.length ? (
        <p className="text-sm text-gray-400 text-center py-16">No hay clientes que coincidan con el filtro.</p>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <div className="px-4 py-2.5 border-b border-gray-100 text-xs text-gray-400">
            Mostrando {desde}-{hasta} de {total} resultado{total !== 1 ? 's' : ''}
          </div>
          <div className="overflow-x-auto">
            <table className="table-stack w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Cliente</th>
                  <th className="px-4 py-3 text-left">Estado</th>
                  <th className="px-4 py-3 text-right">Saldo / Crédito</th>
                  <th className="px-4 py-3 text-center">···</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {pagina_actual.map((f) => (
                  <tr key={f.idCliente} className="hover:bg-gray-50/70 transition-colors">
                    <td className="px-4 py-2.5">
                      <button onClick={() => setVerCliente(f)} className="text-left block group max-w-[260px]">
                        <span className={`text-[10px] font-semibold uppercase tracking-wide ${f.tipoCliente === 'PREPAGO' ? 'text-blue-600' : 'text-purple-600'}`}>
                          {f.tipoCliente === 'PREPAGO' ? 'Prepago' : 'Pospago'}
                        </span>
                        <div className="font-medium text-gray-800 group-hover:text-primary-600 group-hover:underline transition-colors truncate">
                          {f.nombreCliente}
                        </div>
                        {f.nit && <div className="text-[11px] text-gray-400">NIT {f.nit}</div>}
                      </button>
                    </td>
                    <td className="px-4 py-2.5">
                      {f.tipoCliente === 'POSPAGO' ? <PillEstadoMora estado={f.estadoMora} /> : <span className="text-gray-300">—</span>}
                    </td>
                    <td className="px-4 py-2.5 text-right whitespace-nowrap">
                      {f.tipoCliente === 'PREPAGO' ? (
                        <span className="font-semibold text-gray-800 tabular-nums">{f.totalUnidadesDisponibles ?? 0} unidades</span>
                      ) : (
                        <div>
                          <div className="font-semibold text-gray-800 tabular-nums">{formatearPrecio(f.creditoDisponible)}</div>
                          <div className="text-[11px] text-gray-400">de {formatearPrecio(f.limiteCredito)}</div>
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-2.5 text-center">
                      <MenuAcciones opciones={[
                        {
                          label: f.tipoCliente === 'PREPAGO' ? 'Ver saldo' : 'Ver y asignar crédito',
                          icono: <IconoOjo className="h-3.5 w-3.5" />,
                          onClick: () => setVerCliente(f),
                        },
                      ]} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion pagina={pagina} total={total} porPagina={POR_PAGINA} onChange={setPagina} />
        </div>
      )}

      {verCliente?.tipoCliente === 'PREPAGO' && (
        <ModalDetallePrepago idCliente={verCliente.idCliente} nombreCliente={verCliente.nombreCliente}
          onClose={() => setVerCliente(null)} />
      )}
      {verCliente?.tipoCliente === 'POSPAGO' && (
        <ModalDetallePospago idCliente={verCliente.idCliente} nombreCliente={verCliente.nombreCliente}
          onClose={() => setVerCliente(null)} onCambio={cargar} onToast={mostrarToast} />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

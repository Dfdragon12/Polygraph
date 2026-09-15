import { useState, useEffect, useCallback, useMemo } from 'react'
import api from '../../services/api'
import Toast from '../../components/Toast'
import { Modal } from '../../components/ui/Modal'
import Paginacion from '../../components/ui/Paginacion'

const POR_PAGINA = 10

function mensajeErrorValidacion(err, fallback = 'Error al guardar.') {
  const data = err.response?.data
  if (data?.errores && typeof data.errores === 'object' && Object.keys(data.errores).length) {
    return Object.entries(data.errores).map(([campo, msg]) => `${campo}: ${msg}`).join(' · ')
  }
  return data?.mensaje ?? data?.message ?? fallback
}

function fmtValor(valor) {
  if (valor == null) return '—'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor)
}

function fmtFecha(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function IconoOjo({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

/**
 * Estado real de un descuento — no solo activo/inactivo, sino si ya se agotó su uso.
 * El límite de uso (descuentos_usos) solo existe para descuentos con código (cupón); los
 * automáticos (sin código) no tienen tope de clientes, así que nunca se marcan "Agotado".
 */
function estadoDescuento(d) {
  if (!d.activo) return { label: 'Inactivo', clase: 'bg-red-100 text-red-600', agotado: false }
  if (d.fechaFin && new Date(d.fechaFin) < new Date()) return { label: 'Vencido', clase: 'bg-gray-200 text-gray-600', agotado: false }

  if (d.codigo) {
    const totalElegibles = d.publicoObjetivo === 'CLIENTES_ESPECIFICOS' ? (d.clientesElegibles?.length ?? 0) : null
    if (totalElegibles != null && totalElegibles > 0 && d.vecesUsado >= totalElegibles) {
      return { label: 'Agotado', clase: 'bg-purple-100 text-purple-700', agotado: true }
    }
    if (d.vecesUsado > 0) {
      return {
        label: totalElegibles != null ? `En uso (${d.vecesUsado}/${totalElegibles})` : `En uso (${d.vecesUsado})`,
        clase: 'bg-blue-100 text-blue-700',
        agotado: false,
      }
    }
  }

  return { label: 'Vigente', clase: 'bg-green-100 text-green-700', agotado: false }
}

const ALCANCES = [
  { valor: 'GLOBAL', etiqueta: 'Todo el catálogo' },
  { valor: 'CATEGORIA', etiqueta: 'Una categoría' },
  { valor: 'PROCESO', etiqueta: 'Un proceso puntual' },
]

const PUBLICOS = [
  { valor: 'TODOS', etiqueta: 'Todos los clientes' },
  { valor: 'CLIENTES_ESPECIFICOS', etiqueta: 'Solo clientes específicos' },
]

/* ─── Formulario crear/editar ─── */
function FormDescuento({ descuento, clasificaciones, procesos, clientes, onClose, onGuardado }) {
  const [form, setForm] = useState({
    nombre: descuento?.nombre ?? '',
    codigo: descuento?.codigo ?? '',
    tipo: descuento?.tipo ?? 'PORCENTAJE',
    valor: descuento?.valor != null ? String(descuento.valor) : '',
    alcance: descuento?.alcance ?? 'GLOBAL',
    idClasificacion: descuento?.idClasificacion ?? '',
    idProceso: descuento?.idProceso ?? '',
    montoMaximoDescuento: descuento?.montoMaximoDescuento != null ? String(descuento.montoMaximoDescuento) : '',
    fechaInicio: descuento?.fechaInicio ? descuento.fechaInicio.slice(0, 16) : '',
    fechaFin: descuento?.fechaFin ? descuento.fechaFin.slice(0, 16) : '',
    activo: descuento?.activo ?? true,
    publicoObjetivo: descuento?.publicoObjetivo ?? 'TODOS',
    idsClientesElegibles: descuento?.clientesElegibles?.map(c => c.idCliente) ?? [],
  })
  const [busquedaCliente, setBusquedaCliente] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const clientesFiltrados = useMemo(() => {
    const q = busquedaCliente.trim().toLowerCase()
    if (!q) return clientes
    return clientes.filter(c => c.nombreDisplay?.toLowerCase().includes(q) || c.emailPrincipal?.toLowerCase().includes(q) || c.nit?.toLowerCase().includes(q))
  }, [clientes, busquedaCliente])

  const toggleCliente = (idCliente) => {
    setForm(p => ({
      ...p,
      idsClientesElegibles: p.idsClientesElegibles.includes(idCliente)
        ? p.idsClientesElegibles.filter(id => id !== idCliente)
        : [...p.idsClientesElegibles, idCliente],
    }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault(); setGuardando(true); setError(null)
    if (form.publicoObjetivo === 'CLIENTES_ESPECIFICOS' && form.idsClientesElegibles.length === 0) {
      setError('Selecciona al menos un cliente.'); setGuardando(false); return
    }
    const payload = {
      nombre: form.nombre,
      codigo: form.codigo || null,
      tipo: form.tipo,
      valor: Number(form.valor),
      alcance: form.alcance,
      idClasificacion: form.alcance === 'CATEGORIA' ? Number(form.idClasificacion) : null,
      idProceso: form.alcance === 'PROCESO' ? Number(form.idProceso) : null,
      montoMaximoDescuento: form.montoMaximoDescuento !== '' ? Number(form.montoMaximoDescuento) : null,
      fechaInicio: form.fechaInicio || null,
      fechaFin: form.fechaFin || null,
      activo: form.activo,
      publicoObjetivo: form.publicoObjetivo,
      idsClientesElegibles: form.publicoObjetivo === 'CLIENTES_ESPECIFICOS' ? form.idsClientesElegibles : null,
    }
    try {
      if (descuento) {
        await api.put(`/descuentos/${descuento.idDescuento}`, payload)
        onGuardado('Descuento actualizado correctamente')
      } else {
        await api.post('/descuentos', payload)
        onGuardado('Descuento creado correctamente')
      }
      onClose()
    } catch (err) {
      setError(mensajeErrorValidacion(err))
    } finally { setGuardando(false) }
  }

  return (
    <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
        <input value={form.nombre} onChange={e => setForm(p => ({ ...p, nombre: e.target.value }))} required
          placeholder="Ej: Black Friday 20%"
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">
          Código
          <span className="ml-1.5 text-gray-400 font-normal">— déjalo vacío para que se aplique automático, sin que el cliente escriba nada</span>
        </label>
        <input value={form.codigo} onChange={e => setForm(p => ({ ...p, codigo: e.target.value.toUpperCase() }))}
          placeholder="Ej: BLACKFRIDAY20"
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm font-mono uppercase focus:outline-none focus:ring-2 focus:ring-primary-500" />
        {form.codigo && (
          <p className="mt-1 text-xs text-gray-400">Cada cliente solo puede usar este código una vez.</p>
        )}
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Dirigido a <span className="text-red-500">*</span></label>
        <select value={form.publicoObjetivo} onChange={e => setForm(p => ({ ...p, publicoObjetivo: e.target.value }))} required
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
          {PUBLICOS.map(p => <option key={p.valor} value={p.valor}>{p.etiqueta}</option>)}
        </select>
      </div>

      {form.publicoObjetivo === 'CLIENTES_ESPECIFICOS' && (
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Clientes elegibles <span className="text-red-500">*</span>
            <span className="ml-1.5 text-gray-400 font-normal">— {form.idsClientesElegibles.length} seleccionado{form.idsClientesElegibles.length === 1 ? '' : 's'}</span>
          </label>
          <input value={busquedaCliente} onChange={e => setBusquedaCliente(e.target.value)}
            placeholder="Buscar cliente por nombre, correo o NIT…"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm mb-2 focus:outline-none focus:ring-2 focus:ring-primary-500" />
          <div className="max-h-48 overflow-y-auto border border-gray-200 rounded-lg divide-y divide-gray-100">
            {clientesFiltrados.length === 0 ? (
              <p className="text-xs text-gray-400 px-3 py-3">Sin resultados.</p>
            ) : clientesFiltrados.map(c => (
              <label key={c.idCliente} className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-gray-50 cursor-pointer">
                <input type="checkbox" checked={form.idsClientesElegibles.includes(c.idCliente)}
                  onChange={() => toggleCliente(c.idCliente)}
                  className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                <span className="flex-1 truncate">{c.nombreDisplay}</span>
                <span className="text-xs text-gray-400 truncate">{c.emailPrincipal}</span>
              </label>
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Tipo <span className="text-red-500">*</span></label>
          <select value={form.tipo} onChange={e => setForm(p => ({ ...p, tipo: e.target.value }))} required
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            <option value="PORCENTAJE">Porcentaje (%)</option>
            <option value="MONTO_FIJO">Monto fijo ($)</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Valor <span className="text-red-500">*</span>
          </label>
          <input type="number" min={0} step="0.01" value={form.valor} required
            onChange={e => setForm(p => ({ ...p, valor: e.target.value }))}
            placeholder={form.tipo === 'PORCENTAJE' ? 'Ej: 20' : 'Ej: 5000'}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
      </div>

      {form.tipo === 'PORCENTAJE' && (
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Tope de descuento (COP)
            <span className="ml-1.5 text-gray-400 font-normal">— opcional, límite en pesos para que el % no reduzca de más en compras grandes</span>
          </label>
          <input type="number" min={0} step="0.01" value={form.montoMaximoDescuento}
            onChange={e => setForm(p => ({ ...p, montoMaximoDescuento: e.target.value }))}
            placeholder="Sin límite"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
      )}

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Aplica a <span className="text-red-500">*</span></label>
        <select value={form.alcance} onChange={e => setForm(p => ({ ...p, alcance: e.target.value }))} required
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
          {ALCANCES.map(a => <option key={a.valor} value={a.valor}>{a.etiqueta}</option>)}
        </select>
      </div>

      {form.alcance === 'CATEGORIA' && (
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Categoría <span className="text-red-500">*</span></label>
          <select value={form.idClasificacion} onChange={e => setForm(p => ({ ...p, idClasificacion: e.target.value }))} required
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            <option value="">Seleccionar categoría…</option>
            {clasificaciones.map(c => <option key={c.idClasificacion} value={c.idClasificacion}>{c.codigo} — {c.nombre}</option>)}
          </select>
        </div>
      )}

      {form.alcance === 'PROCESO' && (
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Proceso <span className="text-red-500">*</span></label>
          <select value={form.idProceso} onChange={e => setForm(p => ({ ...p, idProceso: e.target.value }))} required
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            <option value="">Seleccionar proceso…</option>
            {procesos.map(p => <option key={p.idProceso} value={p.idProceso}>{p.nombreProceso}</option>)}
          </select>
        </div>
      )}

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Vigente desde</label>
          <input type="datetime-local" value={form.fechaInicio} onChange={e => setForm(p => ({ ...p, fechaInicio: e.target.value }))}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Vigente hasta</label>
          <input type="datetime-local" value={form.fechaFin} onChange={e => setForm(p => ({ ...p, fechaFin: e.target.value }))}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
      </div>
      <p className="text-xs text-gray-400 -mt-2">Deja ambas vacías para que no tenga fecha de vencimiento.</p>

      <label className="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" checked={form.activo} onChange={e => setForm(p => ({ ...p, activo: e.target.checked }))}
          className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
        <span className="text-sm text-gray-700">Activo</span>
      </label>

      <div className="flex justify-end gap-3 pt-1">
        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
        <button type="submit" disabled={guardando}
          className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
          {guardando ? 'Guardando...' : descuento ? 'Guardar cambios' : 'Crear descuento'}
        </button>
      </div>
    </form>
  )
}

const ESTADO_ORDEN_ESTILO = {
  APROBADA: 'bg-green-100 text-green-700',
  PENDIENTE: 'bg-amber-100 text-amber-700',
  RECHAZADA: 'bg-red-100 text-red-600',
}

/* ─── Detalle: quién puede usar el descuento y quién ya lo usó ─── */
function ModalDetalleDescuento({ idDescuento, onClose, onEditar, onCambiarActivo }) {
  const [detalle, setDetalle] = useState(null)
  const [error, setError] = useState(null)
  const [actualizando, setActualizando] = useState(false)

  const recargar = useCallback(() => {
    return api.get(`/descuentos/${idDescuento}/detalle`)
      .then(r => setDetalle(r.data))
      .catch(() => setError('No se pudo cargar el desglose de este descuento.'))
  }, [idDescuento])

  useEffect(() => { recargar() }, [recargar])

  const d = detalle?.descuento
  const estado = d ? estadoDescuento(d) : null

  const handleCambiarActivo = async () => {
    setActualizando(true)
    try {
      await onCambiarActivo(d)
      await recargar()
    } finally { setActualizando(false) }
  }

  return (
    <Modal titulo={d ? d.nombre : 'Desglose del descuento'} subtitulo={d?.codigo ? `Código ${d.codigo}` : 'Aplicación automática'}
      onClose={onClose} ancho="max-w-lg">
      <div className="px-6 py-5 space-y-5">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        {!detalle && !error && (
          <div className="flex items-center justify-center py-10">
            <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" />
          </div>
        )}

        {d && (
          <>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="bg-gray-50 rounded-lg px-3 py-2.5">
                <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Valor</p>
                <p className="text-gray-800 font-semibold mt-0.5">{d.tipo === 'PORCENTAJE' ? `${d.valor}%` : fmtValor(d.valor)}</p>
              </div>
              <div className="bg-gray-50 rounded-lg px-3 py-2.5">
                <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Estado</p>
                <p className="mt-0.5">
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${estado.clase}`}>
                    {estado.label}
                  </span>
                </p>
              </div>
              <div className="bg-gray-50 rounded-lg px-3 py-2.5 col-span-2">
                <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide">Dirigido a</p>
                {d.publicoObjetivo === 'CLIENTES_ESPECIFICOS' ? (
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {d.clientesElegibles.map(c => (
                      <span key={c.idCliente} className="text-xs bg-amber-50 text-amber-700 border border-amber-200 px-2 py-0.5 rounded-full">
                        {c.nombreCliente}
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className="text-gray-700 mt-0.5">Todos los clientes</p>
                )}
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-2">
                <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Historial de uso</p>
                <span className="text-xs font-semibold text-primary-700 bg-primary-50 px-2 py-0.5 rounded-full">
                  {d.vecesUsado} uso{d.vecesUsado === 1 ? '' : 's'}
                </span>
              </div>

              {detalle.usos.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-6 bg-gray-50 rounded-lg">Nadie ha usado este descuento todavía.</p>
              ) : (
                <ul className="divide-y divide-gray-100 border border-gray-100 rounded-lg overflow-hidden">
                  {detalle.usos.map((u, idx) => (
                    <li key={idx} className="px-3 py-2.5 flex items-center justify-between gap-3 text-sm">
                      <div className="min-w-0">
                        <p className="text-gray-800 font-medium truncate">{u.nombreCliente}</p>
                        <p className="text-xs text-gray-400 mt-0.5">
                          {fmtFecha(u.fechaUso)}
                          {u.referenciaOrden && <> · {u.referenciaOrden}</>}
                        </p>
                      </div>
                      <div className="text-right flex-shrink-0">
                        {u.montoOrden != null && <p className="text-gray-700 font-medium">{fmtValor(u.montoOrden)}</p>}
                        {u.estadoOrden && (
                          <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${ESTADO_ORDEN_ESTILO[u.estadoOrden] ?? 'bg-gray-100 text-gray-600'}`}>
                            {u.estadoOrden}
                          </span>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </>
        )}

        {d && estado.agotado && (
          <p className="text-xs text-purple-600 bg-purple-50 border border-purple-100 rounded-lg px-3 py-2">
            Este descuento ya fue usado por todos sus clientes elegibles — no se puede volver a aplicar, así que no tiene sentido editarlo ni activarlo/desactivarlo.
          </p>
        )}

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cerrar</button>
          {d && !estado.agotado && (
            <>
              <button type="button" onClick={handleCambiarActivo} disabled={actualizando}
                className={`text-sm font-medium px-4 py-2 rounded-lg border transition-colors disabled:opacity-50 ${
                  d.activo ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'
                }`}>
                {d.activo ? 'Desactivar' : 'Activar'}
              </button>
              <button type="button" onClick={() => onEditar(d)}
                className="bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
                Editar
              </button>
            </>
          )}
        </div>
      </div>
    </Modal>
  )
}

export default function Descuentos() {
  const [descuentos, setDescuentos] = useState([])
  const [clasificaciones, setClasificaciones] = useState([])
  const [procesos, setProcesos] = useState([])
  const [clientes, setClientes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [modalCrear, setModalCrear] = useState(false)
  const [editando, setEditando] = useState(null)
  const [viendoDetalle, setViendoDetalle] = useState(null)
  const [toast, setToast] = useState(null)
  const [errorGlobal, setErrorGlobal] = useState(null)
  const [filtroEstado, setFiltroEstado] = useState('todos')
  const [pagina, setPagina] = useState(1)

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      const [rDescuentos, rClasificaciones, rProcesos, rClientes] = await Promise.all([
        api.get('/descuentos'),
        api.get('/catalogo/clasificaciones'),
        api.get('/catalogo/procesos'),
        api.get('/clientes'),
      ])
      setDescuentos(rDescuentos.data)
      setClasificaciones(rClasificaciones.data)
      setProcesos(rProcesos.data)
      setClientes(rClientes.data)
    } catch {
      setErrorGlobal('Error al cargar los descuentos.')
    } finally { setCargando(false) }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const cambiarActivo = async (d) => {
    try {
      await api.patch(`/descuentos/${d.idDescuento}/activo`, null, { params: { activo: !d.activo } })
      cargar()
    } catch { setErrorGlobal('Error al cambiar el estado.') }
  }

  const descuentosVisibles = useMemo(() => {
    if (filtroEstado === 'activos') return descuentos.filter(d => d.activo)
    if (filtroEstado === 'inactivos') return descuentos.filter(d => !d.activo)
    return descuentos
  }, [descuentos, filtroEstado])

  useEffect(() => { setPagina(1) }, [filtroEstado])

  const descuentosPagina = descuentosVisibles.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA)

  const alcanceEtiqueta = (d) => {
    if (d.alcance === 'GLOBAL') return 'Todo el catálogo'
    if (d.alcance === 'CATEGORIA') return d.nombreClasificacion ?? '—'
    if (d.alcance === 'PROCESO') return d.nombreProceso ?? '—'
    return d.alcance
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Descuentos</h2>
          <p className="text-sm text-gray-500 mt-0.5">Promociones y cupones aplicables al catálogo, una categoría o un proceso</p>
        </div>
        <button onClick={() => setModalCrear(true)}
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          <span className="text-base leading-none">+</span> Nuevo descuento
        </button>
      </div>

      {errorGlobal && <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{errorGlobal}</div>}

      <div className="flex gap-1 mb-3">
        {[
          { key: 'todos', label: 'Todos', count: descuentos.length },
          { key: 'activos', label: 'Activos', count: descuentos.filter(d => d.activo).length },
          { key: 'inactivos', label: 'Inactivos', count: descuentos.filter(d => !d.activo).length },
        ].map(f => (
          <button key={f.key} onClick={() => setFiltroEstado(f.key)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
              filtroEstado === f.key
                ? f.key === 'inactivos' ? 'bg-red-100 text-red-700' : f.key === 'activos' ? 'bg-green-100 text-green-700' : 'bg-primary-100 text-primary-700'
                : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
            }`}>
            {f.label}<span className="ml-1.5 font-normal opacity-70">{f.count}</span>
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : descuentosVisibles.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">No hay descuentos registrados.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  {['Nombre', 'Código', 'Valor', 'Aplica a', 'Dirigido a', 'Usos', 'Vigencia', 'Estado', 'Acciones'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {descuentosPagina.map(d => {
                  const estado = estadoDescuento(d)
                  return (
                  <tr key={d.idDescuento} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">{d.nombre}</td>
                    <td className="px-4 py-3 text-sm">
                      {d.codigo
                        ? <span className="font-mono text-xs bg-gray-100 px-2 py-0.5 rounded">{d.codigo}</span>
                        : <span className="text-xs text-gray-400 italic">Automático</span>}
                    </td>
                    <td className="px-4 py-3 text-sm font-semibold text-primary-700 whitespace-nowrap">
                      {d.tipo === 'PORCENTAJE' ? `${d.valor}%` : fmtValor(d.valor)}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">{alcanceEtiqueta(d)}</td>
                    <td className="px-4 py-3 text-xs text-gray-600">
                      {d.publicoObjetivo === 'CLIENTES_ESPECIFICOS' ? (
                        <span title={d.clientesElegibles?.map(c => c.nombreCliente).join(', ')}
                          className="inline-flex items-center px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200">
                          {d.clientesElegibles?.length ?? 0} cliente{(d.clientesElegibles?.length ?? 0) === 1 ? '' : 's'}
                        </span>
                      ) : (
                        <span className="text-gray-400">Todos</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-600">{d.vecesUsado}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {d.fechaInicio || d.fechaFin
                        ? <>{fmtFecha(d.fechaInicio) ?? 'Ahora'} → {fmtFecha(d.fechaFin) ?? 'Sin fin'}</>
                        : <span className="text-gray-300">Sin vencimiento</span>}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${estado.clase}`}>
                        {estado.label}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <button onClick={() => setViendoDetalle(d.idDescuento)} title="Ver desglose"
                        className="p-1.5 rounded-full border border-gray-200 text-gray-500 hover:bg-gray-100 hover:text-gray-700 transition-colors">
                        <IconoOjo className="h-3.5 w-3.5" />
                      </button>
                    </td>
                  </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {descuentosVisibles.length > 0 && (
          <Paginacion pagina={pagina} total={descuentosVisibles.length} porPagina={POR_PAGINA} onChange={setPagina} />
        )}
      </div>

      {modalCrear && (
        <Modal titulo="Nuevo descuento" onClose={() => setModalCrear(false)}>
          <FormDescuento descuento={null} clasificaciones={clasificaciones} procesos={procesos} clientes={clientes}
            onClose={() => setModalCrear(false)}
            onGuardado={(msg) => { cargar(); setToast({ mensaje: msg, tipo: 'exito' }) }} />
        </Modal>
      )}

      {editando && (
        <Modal titulo={`Editar — ${editando.nombre}`} onClose={() => setEditando(null)}>
          <FormDescuento descuento={editando} clasificaciones={clasificaciones} procesos={procesos} clientes={clientes}
            onClose={() => setEditando(null)}
            onGuardado={(msg) => { cargar(); setToast({ mensaje: msg, tipo: 'exito' }) }} />
        </Modal>
      )}

      {viendoDetalle != null && (
        <ModalDetalleDescuento idDescuento={viendoDetalle} onClose={() => setViendoDetalle(null)}
          onEditar={(d) => { setViendoDetalle(null); setEditando(d) }}
          onCambiarActivo={async (d) => { await cambiarActivo(d) }} />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

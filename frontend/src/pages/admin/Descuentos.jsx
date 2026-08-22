import { useState, useEffect, useCallback, useMemo } from 'react'
import api from '../../services/api'
import Toast from '../../components/Toast'
import { Modal } from '../../components/ui/Modal'

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

function Badge({ activo }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

const ALCANCES = [
  { valor: 'GLOBAL', etiqueta: 'Todo el catálogo' },
  { valor: 'CATEGORIA', etiqueta: 'Una categoría' },
  { valor: 'PROCESO', etiqueta: 'Un proceso puntual' },
]

/* ─── Formulario crear/editar ─── */
function FormDescuento({ descuento, clasificaciones, procesos, onClose, onGuardado }) {
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
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault(); setGuardando(true); setError(null)
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
      </div>

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

export default function Descuentos() {
  const [descuentos, setDescuentos] = useState([])
  const [clasificaciones, setClasificaciones] = useState([])
  const [procesos, setProcesos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [modalCrear, setModalCrear] = useState(false)
  const [editando, setEditando] = useState(null)
  const [toast, setToast] = useState(null)
  const [errorGlobal, setErrorGlobal] = useState(null)
  const [filtroEstado, setFiltroEstado] = useState('todos')

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      const [rDescuentos, rClasificaciones, rProcesos] = await Promise.all([
        api.get('/descuentos'),
        api.get('/catalogo/clasificaciones'),
        api.get('/catalogo/procesos'),
      ])
      setDescuentos(rDescuentos.data)
      setClasificaciones(rClasificaciones.data)
      setProcesos(rProcesos.data)
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
                  {['Nombre', 'Código', 'Valor', 'Aplica a', 'Vigencia', 'Estado', 'Acciones'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {descuentosVisibles.map(d => (
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
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {d.fechaInicio || d.fechaFin
                        ? <>{fmtFecha(d.fechaInicio) ?? 'Ahora'} → {fmtFecha(d.fechaFin) ?? 'Sin fin'}</>
                        : <span className="text-gray-300">Sin vencimiento</span>}
                    </td>
                    <td className="px-4 py-3"><Badge activo={d.activo} /></td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button onClick={() => setEditando(d)}
                          className="text-xs font-medium px-3 py-1 rounded-full border border-primary-200 text-primary-600 hover:bg-primary-50">
                          Editar
                        </button>
                        <button onClick={() => cambiarActivo(d)}
                          className={`text-xs font-medium px-3 py-1 rounded-full border transition-colors ${
                            d.activo ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'
                          }`}>
                          {d.activo ? 'Desactivar' : 'Activar'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {modalCrear && (
        <Modal titulo="Nuevo descuento" onClose={() => setModalCrear(false)}>
          <FormDescuento descuento={null} clasificaciones={clasificaciones} procesos={procesos}
            onClose={() => setModalCrear(false)}
            onGuardado={(msg) => { cargar(); setToast({ mensaje: msg, tipo: 'exito' }) }} />
        </Modal>
      )}

      {editando && (
        <Modal titulo={`Editar — ${editando.nombre}`} onClose={() => setEditando(null)}>
          <FormDescuento descuento={editando} clasificaciones={clasificaciones} procesos={procesos}
            onClose={() => setEditando(null)}
            onGuardado={(msg) => { cargar(); setToast({ mensaje: msg, tipo: 'exito' }) }} />
        </Modal>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

import { useState, useEffect, useMemo } from 'react'
import api from '../../../services/api'

function Badge({ activo }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
      activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
    }`}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

function Modal({ titulo, onClose, ancho = 'max-w-md', children }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className={`bg-white rounded-2xl shadow-xl w-full ${ancho} mx-4 max-h-[90vh] flex flex-col`}>
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between flex-shrink-0">
          <h3 className="text-base font-semibold text-gray-900">{titulo}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>
        <div className="overflow-y-auto flex-1">{children}</div>
      </div>
    </div>
  )
}

function IconoOrden({ activo, dir }) {
  if (!activo) return <span className="ml-1 text-gray-300 text-xs">↕</span>
  return <span className="ml-1 text-primary-500 text-xs">{dir === 'asc' ? '↑' : '↓'}</span>
}

/* ─── Modal crear / editar proceso ─── */
function ModalProceso({ proceso, onClose, onGuardado }) {
  const [form, setForm] = useState({
    nombreProceso: proceso?.nombreProceso ?? '',
    descripcion: proceso?.descripcion ?? '',
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleChange = (e) => setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      if (proceso) await api.put(`/catalogo/procesos/${proceso.idProceso}`, form)
      else await api.post('/catalogo/procesos', form)
      onGuardado()
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? err.response?.data?.message ?? 'Error al guardar.')
    } finally { setGuardando(false) }
  }

  return (
    <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Nombre del proceso <span className="text-red-500">*</span></label>
        <input name="nombreProceso" value={form.nombreProceso} onChange={handleChange} required
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Descripción</label>
        <textarea name="descripcion" value={form.descripcion} onChange={handleChange} rows={3}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
      </div>

      <div className="flex justify-end gap-3 pt-1">
        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
        <button type="submit" disabled={guardando}
          className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
          {guardando ? 'Guardando...' : proceso ? 'Guardar cambios' : 'Crear proceso'}
        </button>
      </div>
    </form>
  )
}

/* ─── Modal configurar pasos de un proceso ─── */
function ModalPasos({ proceso, onClose }) {
  const [pasos, setPasos] = useState([])
  const [tipos, setTipos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [formPaso, setFormPaso] = useState({ idTipoProgreso: '', habilitado: true, obligatorio: true })
  const [guardandoPaso, setGuardandoPaso] = useState(false)
  const [error, setError] = useState(null)

  const cargarPasos = async () => {
    setCargando(true)
    try {
      const [rPasos, rTipos] = await Promise.all([
        api.get(`/catalogo/procesos/${proceso.idProceso}/pasos`),
        api.get('/catalogo/tipos-progreso'),
      ])
      setPasos(rPasos.data)
      setTipos(rTipos.data)
    } finally { setCargando(false) }
  }

  useEffect(() => { cargarPasos() }, [])

  const idsAsignados = new Set(pasos.map(p => p.idTipoProgreso))
  const tiposDisponibles = tipos.filter(t => !idsAsignados.has(t.idTipoProgreso) && t.activo)

  const agregarPaso = async (e) => {
    e.preventDefault()
    if (!formPaso.idTipoProgreso) return
    setGuardandoPaso(true); setError(null)
    try {
      await api.post(`/catalogo/procesos/${proceso.idProceso}/pasos`, {
        ...formPaso,
        idTipoProgreso: Number(formPaso.idTipoProgreso),
      })
      setFormPaso({ idTipoProgreso: '', habilitado: true, obligatorio: true })
      cargarPasos()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al agregar el paso.')
    } finally { setGuardandoPaso(false) }
  }

  const eliminarPaso = async (pasoId) => {
    try {
      await api.delete(`/catalogo/procesos/${proceso.idProceso}/pasos/${pasoId}`)
      cargarPasos()
    } catch { setError('Error al eliminar el paso.') }
  }

  const toggleField = async (paso, campo) => {
    try {
      await api.put(`/catalogo/procesos/${proceso.idProceso}/pasos/${paso.id}`, {
        idTipoProgreso: paso.idTipoProgreso,
        habilitado: campo === 'habilitado' ? !paso.habilitado : paso.habilitado,
        obligatorio: campo === 'obligatorio' ? !paso.obligatorio : paso.obligatorio,
      })
      cargarPasos()
    } catch { setError('Error al actualizar el paso.') }
  }

  return (
    <div className="px-6 py-5 space-y-5">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      <div>
        <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">
          Pasos configurados ({pasos.length})
        </p>
        {cargando ? (
          <div className="flex justify-center py-6">
            <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : pasos.length === 0 ? (
          <p className="text-sm text-gray-400 py-4 text-center">Sin pasos asignados aún.</p>
        ) : (
          <div className="space-y-2">
            {pasos.map((p, i) => (
              <div key={p.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                <span className="w-6 h-6 rounded-full bg-primary-100 text-primary-600 text-xs font-bold flex items-center justify-center flex-shrink-0">
                  {i + 1}
                </span>
                <span className="flex-1 text-sm text-gray-800 font-medium">{p.nombreProgreso}</span>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => toggleField(p, 'obligatorio')}
                    className={`text-xs px-2 py-0.5 rounded-full border transition-colors ${
                      p.obligatorio ? 'bg-orange-50 border-orange-200 text-orange-600' : 'bg-gray-100 border-gray-200 text-gray-400'
                    }`}
                    title="Obligatorio"
                  >
                    {p.obligatorio ? 'Obligatorio' : 'Opcional'}
                  </button>
                  <button
                    onClick={() => toggleField(p, 'habilitado')}
                    className={`text-xs px-2 py-0.5 rounded-full border transition-colors ${
                      p.habilitado ? 'bg-green-50 border-green-200 text-green-600' : 'bg-gray-100 border-gray-200 text-gray-400'
                    }`}
                    title="Habilitado"
                  >
                    {p.habilitado ? 'Habilitado' : 'Deshabilitado'}
                  </button>
                  <button onClick={() => eliminarPaso(p.id)}
                    className="text-xs text-red-400 hover:text-red-600 px-2 py-0.5 rounded border border-transparent hover:border-red-200 transition-colors">
                    Quitar
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {tiposDisponibles.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Agregar paso</p>
          <form onSubmit={agregarPaso} className="flex items-end gap-3">
            <div className="flex-1">
              <label className="block text-xs font-medium text-gray-600 mb-1">Tipo de subproceso</label>
              <select value={formPaso.idTipoProgreso} onChange={e => setFormPaso(p => ({ ...p, idTipoProgreso: e.target.value }))}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
                <option value="">Seleccionar...</option>
                {tiposDisponibles.map(t => (
                  <option key={t.idTipoProgreso} value={t.idTipoProgreso}>{t.nombreProgreso}</option>
                ))}
              </select>
              <p className="text-xs text-gray-400 mt-1">El orden lo define el catálogo de Tipos de subproceso.</p>
            </div>
            <button type="submit" disabled={guardandoPaso || !formPaso.idTipoProgreso}
              className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors whitespace-nowrap">
              {guardandoPaso ? '...' : 'Agregar'}
            </button>
          </form>
        </div>
      )}

      <div className="flex justify-end pt-2 border-t border-gray-100">
        <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cerrar</button>
      </div>
    </div>
  )
}

/* ─── Página principal ─── */
export default function Procesos() {
  const [procesos, setProcesos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [modalCrear, setModalCrear] = useState(false)
  const [editando, setEditando] = useState(null)
  const [configurando, setConfigurando] = useState(null)
  const [errorGlobal, setErrorGlobal] = useState(null)

  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('todos')
  const [ordenCol, setOrdenCol] = useState(null)
  const [ordenDir, setOrdenDir] = useState('asc')

  const cargar = async () => {
    setCargando(true)
    try {
      const res = await api.get('/catalogo/procesos')
      setProcesos(res.data)
    } catch { setErrorGlobal('Error al cargar los procesos.') }
    finally { setCargando(false) }
  }

  useEffect(() => { cargar() }, [])

  const cambiarEstado = async (p) => {
    try {
      await api.patch(`/catalogo/procesos/${p.idProceso}/${p.activo ? 'desactivar' : 'activar'}`)
      cargar()
    } catch { setErrorGlobal('Error al cambiar el estado.') }
  }

  const manejarOrden = (col) => {
    if (ordenCol === col) setOrdenDir(d => d === 'asc' ? 'desc' : 'asc')
    else { setOrdenCol(col); setOrdenDir('asc') }
  }

  const procesosVisibles = useMemo(() => {
    let lista = procesos

    if (filtroEstado === 'activos')   lista = lista.filter(p => p.activo)
    if (filtroEstado === 'inactivos') lista = lista.filter(p => !p.activo)

    if (busqueda.trim()) {
      const q = busqueda.toLowerCase()
      lista = lista.filter(p =>
        p.nombreProceso.toLowerCase().includes(q) ||
        (p.descripcion ?? '').toLowerCase().includes(q)
      )
    }

    if (ordenCol) {
      lista = [...lista].sort((a, b) => {
        let va = ordenCol === 'activo' ? (a.activo ? 1 : 0) : (a[ordenCol] ?? '')
        let vb = ordenCol === 'activo' ? (b.activo ? 1 : 0) : (b[ordenCol] ?? '')
        if (typeof va === 'string') va = va.toLowerCase()
        if (typeof vb === 'string') vb = vb.toLowerCase()
        if (va < vb) return ordenDir === 'asc' ? -1 : 1
        if (va > vb) return ordenDir === 'asc' ? 1 : -1
        return 0
      })
    }

    return lista
  }, [procesos, filtroEstado, busqueda, ordenCol, ordenDir])

  const cuentaActivos   = procesos.filter(p => p.activo).length
  const cuentaInactivos = procesos.filter(p => !p.activo).length

  const columnas = [
    { key: 'nombreProceso', label: 'Nombre del proceso', sortable: true  },
    { key: 'descripcion',   label: 'Descripción',        sortable: false },
    { key: 'totalPasos',    label: 'Pasos',              sortable: true  },
    { key: 'activo',        label: 'Estado',             sortable: true  },
    { key: '_acciones',     label: 'Acciones',           sortable: false },
  ]

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Procesos</h2>
          <p className="text-sm text-gray-500 mt-0.5">Flujos de trabajo y sus subprocesos</p>
        </div>
        <button onClick={() => setModalCrear(true)}
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          <span className="text-base leading-none">+</span> Nuevo proceso
        </button>
      </div>

      {errorGlobal && <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{errorGlobal}</div>}

      {/* Barra de filtros */}
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <input
          type="search"
          value={busqueda}
          onChange={e => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre o descripción…"
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
        <div className="flex gap-1">
          {[
            { key: 'todos',     label: 'Todos',     count: procesos.length },
            { key: 'activos',   label: 'Activos',   count: cuentaActivos   },
            { key: 'inactivos', label: 'Inactivos', count: cuentaInactivos },
          ].map(f => (
            <button
              key={f.key}
              onClick={() => setFiltroEstado(f.key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                filtroEstado === f.key
                  ? f.key === 'inactivos'
                    ? 'bg-red-100 text-red-700'
                    : f.key === 'activos'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-primary-100 text-primary-700'
                  : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}
            >
              {f.label}
              <span className="ml-1.5 font-normal opacity-70">{f.count}</span>
            </button>
          ))}
        </div>
        {(busqueda || filtroEstado !== 'todos') && (
          <span className="text-xs text-gray-400">
            {procesosVisibles.length} resultado{procesosVisibles.length !== 1 ? 's' : ''}
          </span>
        )}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : procesosVisibles.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">
            {procesos.length === 0 ? 'No hay procesos registrados.' : 'No hay procesos que coincidan con el filtro.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  {columnas.map(col => (
                    <th
                      key={col.key}
                      onClick={col.sortable ? () => manejarOrden(col.key) : undefined}
                      className={`px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider select-none ${
                        col.sortable ? 'cursor-pointer hover:text-gray-700' : ''
                      }`}
                    >
                      {col.label}
                      {col.sortable && <IconoOrden activo={ordenCol === col.key} dir={ordenDir} />}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {procesosVisibles.map(p => (
                  <tr key={p.idProceso} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-sm text-gray-900 font-medium whitespace-nowrap">{p.nombreProceso}</td>
                    <td className="px-4 py-3 text-sm text-gray-500 max-w-xs truncate">{p.descripcion ?? '—'}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary-100 text-primary-700">
                        {p.totalPasos} paso{p.totalPasos !== 1 ? 's' : ''}
                      </span>
                    </td>
                    <td className="px-4 py-3"><Badge activo={p.activo} /></td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2 flex-wrap">
                        <button onClick={() => setConfigurando(p)}
                          className="text-xs font-medium px-3 py-1 rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors">
                          Pasos
                        </button>
                        <button onClick={() => setEditando(p)}
                          className="text-xs font-medium px-3 py-1 rounded-full border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors">
                          Editar
                        </button>
                        <button onClick={() => cambiarEstado(p)}
                          className={`text-xs font-medium px-3 py-1 rounded-full border transition-colors ${
                            p.activo ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'
                          }`}>
                          {p.activo ? 'Desactivar' : 'Activar'}
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
        <Modal titulo="Nuevo proceso" onClose={() => setModalCrear(false)}>
          <ModalProceso proceso={null} onClose={() => setModalCrear(false)} onGuardado={cargar} />
        </Modal>
      )}

      {editando && (
        <Modal titulo={`Editar — ${editando.nombreProceso}`} onClose={() => setEditando(null)}>
          <ModalProceso proceso={editando} onClose={() => setEditando(null)} onGuardado={cargar} />
        </Modal>
      )}

      {configurando && (
        <Modal titulo={`Pasos — ${configurando.nombreProceso}`} onClose={() => { setConfigurando(null); cargar() }} ancho="max-w-2xl">
          <ModalPasos proceso={configurando} onClose={() => { setConfigurando(null); cargar() }} />
        </Modal>
      )}
    </div>
  )
}

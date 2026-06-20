import { useState, useEffect, useMemo } from 'react'
import api from '../../../services/api'

const FORM_VACIO = { nombreProgreso: '', descripcion: '', orden: 0 }

function Badge({ activo }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
      activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
    }`}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

function Modal({ titulo, onClose, children }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-base font-semibold text-gray-900">{titulo}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>
        {children}
      </div>
    </div>
  )
}

function IconoOrden({ activo, dir }) {
  if (!activo) return <span className="ml-1 text-gray-300 text-xs">↕</span>
  return <span className="ml-1 text-indigo-500 text-xs">{dir === 'asc' ? '↑' : '↓'}</span>
}

function Formulario({ form, onChange, onSubmit, onCancel, guardando, error, esEdicion }) {
  return (
    <form onSubmit={onSubmit} className="px-6 py-5 space-y-4">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
        <input name="nombreProgreso" value={form.nombreProgreso} onChange={onChange} required
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Descripción</label>
        <textarea name="descripcion" value={form.descripcion} onChange={onChange} rows={3}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none" />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Orden</label>
        <input name="orden" type="number" min={0} value={form.orden} onChange={onChange}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
      </div>

      <div className="flex justify-end gap-3 pt-1">
        <button type="button" onClick={onCancel} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
        <button type="submit" disabled={guardando}
          className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
          {guardando ? 'Guardando...' : esEdicion ? 'Guardar cambios' : 'Crear tipo'}
        </button>
      </div>
    </form>
  )
}

export default function TiposProgreso() {
  const [tipos, setTipos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [modalCrear, setModalCrear] = useState(false)
  const [editando, setEditando] = useState(null)
  const [form, setForm] = useState(FORM_VACIO)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const [errorGlobal, setErrorGlobal] = useState(null)

  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('todos')
  const [ordenCol, setOrdenCol] = useState(null)
  const [ordenDir, setOrdenDir] = useState('asc')

  const cargar = async () => {
    setCargando(true)
    try {
      const res = await api.get('/catalogo/tipos-progreso')
      setTipos(res.data)
    } catch { setErrorGlobal('Error al cargar los tipos de progreso.') }
    finally { setCargando(false) }
  }

  useEffect(() => { cargar() }, [])

  const abrirCrear = () => { setForm(FORM_VACIO); setError(null); setModalCrear(true) }
  const abrirEditar = (t) => {
    setForm({ nombreProgreso: t.nombreProgreso, descripcion: t.descripcion ?? '', orden: t.orden ?? 0 })
    setError(null)
    setEditando(t)
  }

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm(prev => ({ ...prev, [name]: name === 'orden' ? Number(value) : value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      if (editando) {
        await api.put(`/catalogo/tipos-progreso/${editando.idTipoProgreso}`, form)
        setEditando(null)
      } else {
        await api.post('/catalogo/tipos-progreso', form)
        setModalCrear(false)
      }
      cargar()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? err.response?.data?.message ?? 'Error al guardar.')
    } finally { setGuardando(false) }
  }

  const cambiarEstado = async (t) => {
    try {
      await api.patch(`/catalogo/tipos-progreso/${t.idTipoProgreso}/${t.activo ? 'desactivar' : 'activar'}`)
      cargar()
    } catch { setErrorGlobal('Error al cambiar el estado.') }
  }

  const manejarOrden = (col) => {
    if (ordenCol === col) setOrdenDir(d => d === 'asc' ? 'desc' : 'asc')
    else { setOrdenCol(col); setOrdenDir('asc') }
  }

  const tiposVisibles = useMemo(() => {
    let lista = tipos

    if (filtroEstado === 'activos')   lista = lista.filter(t => t.activo)
    if (filtroEstado === 'inactivos') lista = lista.filter(t => !t.activo)

    if (busqueda.trim()) {
      const q = busqueda.toLowerCase()
      lista = lista.filter(t =>
        t.nombreProgreso.toLowerCase().includes(q) ||
        (t.descripcion ?? '').toLowerCase().includes(q)
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
  }, [tipos, filtroEstado, busqueda, ordenCol, ordenDir])

  const cuentaActivos   = tipos.filter(t => t.activo).length
  const cuentaInactivos = tipos.filter(t => !t.activo).length

  const columnas = [
    { key: 'orden',         label: 'Orden',       sortable: true  },
    { key: 'nombreProgreso',label: 'Nombre',       sortable: true  },
    { key: 'descripcion',   label: 'Descripción',  sortable: false },
    { key: 'activo',        label: 'Estado',       sortable: true  },
    { key: '_acciones',     label: 'Acciones',     sortable: false },
  ]

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Tipos de Subproceso</h2>
          <p className="text-sm text-gray-500 mt-0.5">Pasos disponibles para configurar en los procesos</p>
        </div>
        <button onClick={abrirCrear}
          className="inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          <span className="text-base leading-none">+</span> Nuevo tipo
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
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <div className="flex gap-1">
          {[
            { key: 'todos',     label: 'Todos',     count: tipos.length    },
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
                      : 'bg-indigo-100 text-indigo-700'
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
            {tiposVisibles.length} resultado{tiposVisibles.length !== 1 ? 's' : ''}
          </span>
        )}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" />
          </div>
        ) : tiposVisibles.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">
            {tipos.length === 0 ? 'No hay tipos de subproceso registrados.' : 'No hay tipos que coincidan con el filtro.'}
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
                {tiposVisibles.map(t => (
                  <tr key={t.idTipoProgreso} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-sm text-gray-500 text-center w-16">{t.orden ?? 0}</td>
                    <td className="px-4 py-3 text-sm text-gray-900 font-medium">{t.nombreProgreso}</td>
                    <td className="px-4 py-3 text-sm text-gray-500 max-w-xs truncate">{t.descripcion ?? '—'}</td>
                    <td className="px-4 py-3"><Badge activo={t.activo} /></td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button onClick={() => abrirEditar(t)}
                          className="text-xs font-medium px-3 py-1 rounded-full border border-indigo-200 text-indigo-600 hover:bg-indigo-50 transition-colors">
                          Editar
                        </button>
                        <button onClick={() => cambiarEstado(t)}
                          className={`text-xs font-medium px-3 py-1 rounded-full border transition-colors ${
                            t.activo ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'
                          }`}>
                          {t.activo ? 'Desactivar' : 'Activar'}
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
        <Modal titulo="Nuevo tipo de subproceso" onClose={() => setModalCrear(false)}>
          <Formulario form={form} onChange={handleChange} onSubmit={handleSubmit}
            onCancel={() => setModalCrear(false)} guardando={guardando} error={error} esEdicion={false} />
        </Modal>
      )}

      {editando && (
        <Modal titulo={`Editar — ${editando.nombreProgreso}`} onClose={() => setEditando(null)}>
          <Formulario form={form} onChange={handleChange} onSubmit={handleSubmit}
            onCancel={() => setEditando(null)} guardando={guardando} error={error} esEdicion={true} />
        </Modal>
      )}
    </div>
  )
}

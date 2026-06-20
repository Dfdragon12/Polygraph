import { useState, useEffect, useMemo, useCallback } from 'react'
import api from '../../services/api'
import Toast from '../../components/Toast'

/* ─── Helpers ─── */
function Badge({ activo }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
      activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
    }`}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

function BadgeMora({ mora }) {
  const cfg = {
    SIN_MORA:      { cls: 'bg-green-100 text-green-700',  label: 'Sin mora' },
    EN_MORA:       { cls: 'bg-red-100 text-red-700',      label: 'En mora' },
    MORA_CRITICA:  { cls: 'bg-red-200 text-red-900',      label: 'Mora crítica' },
    SUSPENDIDO:    { cls: 'bg-gray-200 text-gray-700',    label: 'Suspendido' },
  }
  const c = cfg[mora] ?? { cls: 'bg-gray-100 text-gray-500', label: mora }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${c.cls}`}>
      {c.label}
    </span>
  )
}

function IconoOrden({ activo, dir }) {
  if (!activo) return <span className="ml-1 text-gray-300 text-xs">↕</span>
  return <span className="ml-1 text-indigo-500 text-xs">{dir === 'asc' ? '↑' : '↓'}</span>
}

/* ─── Paginación ─── */
function Paginacion({ pagina, total, porPagina, onChange }) {
  const totalPags = Math.ceil(total / porPagina)
  if (totalPags <= 1) return null
  const inicio = (pagina - 1) * porPagina + 1
  const fin    = Math.min(pagina * porPagina, total)
  const nums = []
  for (let i = Math.max(1, pagina - 2); i <= Math.min(totalPags, pagina + 2); i++) nums.push(i)
  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
      <span className="text-xs text-gray-400">{inicio}–{fin} de {total}</span>
      <div className="flex items-center gap-1">
        <button onClick={() => onChange(pagina - 1)} disabled={pagina === 1}
          className="px-2.5 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          ← Anterior
        </button>
        {nums[0] > 1 && <span className="text-xs text-gray-400 px-1">…</span>}
        {nums.map(p => (
          <button key={p} onClick={() => onChange(p)}
            className={`w-8 h-8 text-xs rounded-lg border transition-colors ${
              p === pagina ? 'bg-indigo-600 border-indigo-600 text-white font-semibold' : 'border-gray-200 text-gray-500 hover:bg-gray-50'
            }`}>
            {p}
          </button>
        ))}
        {nums[nums.length - 1] < totalPags && <span className="text-xs text-gray-400 px-1">…</span>}
        <button onClick={() => onChange(pagina + 1)} disabled={pagina === totalPags}
          className="px-2.5 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          Siguiente →
        </button>
      </div>
    </div>
  )
}

/* ─── Modal editar cliente ─── */
function ModalEditar({ cliente, onClose, onGuardado }) {
  const esNatural = cliente.tipoPersona === 'NATURAL'
  const [form, setForm] = useState({
    nombre:             cliente.nombreDisplay?.split(' ')[0] ?? '',
    apellido:           esNatural ? (cliente.nombreDisplay?.split(' ').slice(1).join(' ') ?? '') : '',
    razonSocial:        !esNatural ? cliente.nombreDisplay : '',
    nombreComercial:    cliente.nombreComercial ?? '',
    representanteLegal: cliente.representanteLegal ?? '',
    telefono:           cliente.telefono ?? '',
    direccion:          cliente.direccion ?? '',
    observaciones:      cliente.observaciones ?? '',
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm(p => ({ ...p, [name]: value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      await api.put(`/clientes/${cliente.idCliente}`, form)
      onGuardado()
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al actualizar el cliente.')
    } finally { setGuardando(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between sticky top-0 bg-white z-10">
          <div>
            <h3 className="text-base font-semibold text-gray-900">Editar cliente</h3>
            <p className="text-xs text-gray-400">{cliente.tipoPersona} · {cliente.tipoCliente}</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
          )}

          {esNatural ? (
            <div className="grid grid-cols-2 gap-4">
              <Campo label="Nombre" name="nombre" value={form.nombre} onChange={handleChange} required />
              <Campo label="Apellido" name="apellido" value={form.apellido} onChange={handleChange} />
            </div>
          ) : (
            <>
              <Campo label="Razón social" name="razonSocial" value={form.razonSocial} onChange={handleChange} required />
              <Campo label="Nombre comercial" name="nombreComercial" value={form.nombreComercial} onChange={handleChange} />
              <Campo label="Representante legal" name="representanteLegal" value={form.representanteLegal} onChange={handleChange} />
            </>
          )}

          <Campo label="Teléfono" name="telefono" value={form.telefono} onChange={handleChange} placeholder="Ej: 3001234567" />
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Dirección</label>
            <input name="direccion" value={form.direccion} onChange={handleChange}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Observaciones</label>
            <textarea name="observaciones" value={form.observaciones} onChange={handleChange} rows={3}
              placeholder="Notas internas sobre el cliente…"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none" />
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
            <button type="submit" disabled={guardando}
              className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
              {guardando ? 'Guardando...' : 'Guardar cambios'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function Campo({ label, name, value, onChange, required = false, placeholder = '' }) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <input name={name} value={value} onChange={onChange} required={required} placeholder={placeholder}
        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
    </div>
  )
}

/* ─── Modal detalle / validaciones ─── */
function ModalDetalle({ cliente, onClose }) {
  const pospago = cliente.pospago
  const puedeSolicitar = cliente.estado === 'ACTIVO'
    && cliente.usuarioActivo
    && (!pospago || (pospago.estadoMora === 'SIN_MORA' && !pospago.requiereAprobacion))

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-base font-semibold text-gray-900">Validaciones — {cliente.nombreDisplay}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>
        <div className="px-6 py-5 space-y-4">
          {/* Estado general */}
          <div className="space-y-2">
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Estado del cliente</p>
            <Fila label="Estado cuenta" valor={<Badge activo={cliente.estado === 'ACTIVO'} />} />
            <Fila label="Usuario activo" valor={<Badge activo={!!cliente.usuarioActivo} />} />
            <Fila label="Tipo" valor={`${cliente.tipoPersona} · ${cliente.tipoCliente}`} />
          </div>

          {/* Info pospago */}
          {pospago && (
            <div className="space-y-2">
              <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Crédito pospago</p>
              <Fila label="Estado mora" valor={<BadgeMora mora={pospago.estadoMora} />} />
              {pospago.diasMora > 0 && <Fila label="Días en mora" valor={`${pospago.diasMora} días`} rojo />}
              <Fila label="Límite crédito" valor={formatPesos(pospago.limiteCredito)} />
              <Fila label="Crédito disponible" valor={formatPesos(pospago.creditoDisponible)} />
              <Fila label="Requiere aprobación" valor={pospago.requiereAprobacion ? 'Sí' : 'No'} rojo={pospago.requiereAprobacion} />
            </div>
          )}

          {/* Validación de servicio */}
          <div className={`rounded-lg p-4 ${puedeSolicitar ? 'bg-green-50 border border-green-200' : 'bg-red-50 border border-red-200'}`}>
            <div className="flex items-center gap-2">
              <span className={`w-3 h-3 rounded-full ${puedeSolicitar ? 'bg-green-500' : 'bg-red-500'}`} />
              <p className={`text-sm font-semibold ${puedeSolicitar ? 'text-green-800' : 'text-red-800'}`}>
                {puedeSolicitar ? 'Puede solicitar servicios' : 'No puede solicitar servicios'}
              </p>
            </div>
            {!puedeSolicitar && (
              <ul className="mt-2 space-y-1">
                {cliente.estado !== 'ACTIVO' && <li className="text-xs text-red-700">• Cuenta inactiva</li>}
                {!cliente.usuarioActivo && <li className="text-xs text-red-700">• Usuario deshabilitado</li>}
                {pospago?.estadoMora && pospago.estadoMora !== 'SIN_MORA' && <li className="text-xs text-red-700">• Cliente en mora</li>}
                {pospago?.requiereAprobacion && <li className="text-xs text-red-700">• Requiere aprobación de crédito</li>}
              </ul>
            )}
          </div>
        </div>
        <div className="px-6 pb-5 flex justify-end">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-50">
            Cerrar
          </button>
        </div>
      </div>
    </div>
  )
}

function Fila({ label, valor, rojo = false }) {
  return (
    <div className="flex items-center justify-between py-1">
      <span className="text-xs text-gray-500">{label}</span>
      <span className={`text-xs font-medium ${rojo ? 'text-red-600' : 'text-gray-800'}`}>{valor}</span>
    </div>
  )
}

function formatPesos(val) {
  if (val == null) return '—'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val)
}

function formatRelativo(iso) {
  if (!iso) return '—'
  const diff = Date.now() - new Date(iso).getTime()
  const d = Math.floor(diff / 86400000)
  if (d === 0) return 'Hoy'
  if (d === 1) return 'Ayer'
  if (d < 30) return `Hace ${d} días`
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

/* ─── Página principal ─── */
export default function Clientes() {
  const [clientes, setClientes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  const [editando, setEditando] = useState(null)
  const [detalle, setDetalle] = useState(null)

  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('todos')
  const [filtroTipo, setFiltroTipo] = useState('todos')
  const [ordenCol, setOrdenCol] = useState(null)
  const [ordenDir, setOrdenDir] = useState('asc')
  const [pagina, setPagina] = useState(1)
  const POR_PAGINA = 7

  const mostrarToast = useCallback((msg, tipo = 'exito') => setToast({ mensaje: msg, tipo }), [])

  const cargar = useCallback(async () => {
    setCargando(true); setError(null)
    try {
      const res = await api.get('/clientes')
      setClientes(res.data)
    } catch { setError('No se pudieron cargar los clientes.') }
    finally { setCargando(false) }
  }, [])

  useEffect(() => { cargar() }, [cargar])
  useEffect(() => { setPagina(1) }, [busqueda, filtroEstado, filtroTipo])

  const cambiarEstado = async (cliente, activar) => {
    try {
      await api.patch(`/clientes/${cliente.idCliente}/${activar ? 'activar' : 'desactivar'}`)
      cargar()
      mostrarToast(activar ? 'Cliente activado correctamente' : 'Cliente desactivado correctamente')
    } catch { setError('Error al cambiar el estado del cliente.') }
  }

  const manejarOrden = (col) => {
    if (ordenCol === col) setOrdenDir(d => d === 'asc' ? 'desc' : 'asc')
    else { setOrdenCol(col); setOrdenDir('asc') }
  }

  const clientesFiltrados = useMemo(() => {
    let lista = clientes

    if (filtroEstado === 'activos')   lista = lista.filter(c => c.estado === 'ACTIVO')
    if (filtroEstado === 'inactivos') lista = lista.filter(c => c.estado !== 'ACTIVO')
    if (filtroTipo === 'natural')     lista = lista.filter(c => c.tipoPersona === 'NATURAL')
    if (filtroTipo === 'juridica')    lista = lista.filter(c => c.tipoPersona === 'JURIDICA')
    if (filtroTipo === 'prepago')     lista = lista.filter(c => c.tipoCliente === 'PREPAGO')
    if (filtroTipo === 'pospago')     lista = lista.filter(c => c.tipoCliente === 'POSPAGO')

    if (busqueda.trim()) {
      const q = busqueda.toLowerCase()
      lista = lista.filter(c =>
        c.nombreDisplay?.toLowerCase().includes(q) ||
        c.emailPrincipal?.toLowerCase().includes(q) ||
        c.nit?.toLowerCase().includes(q) ||
        c.telefono?.toLowerCase().includes(q)
      )
    }

    if (ordenCol) {
      lista = [...lista].sort((a, b) => {
        let va = a[ordenCol] ?? ''
        let vb = b[ordenCol] ?? ''
        if (typeof va === 'string') va = va.toLowerCase()
        if (typeof vb === 'string') vb = vb.toLowerCase()
        if (va < vb) return ordenDir === 'asc' ? -1 : 1
        if (va > vb) return ordenDir === 'asc' ? 1 : -1
        return 0
      })
    }
    return lista
  }, [clientes, filtroEstado, filtroTipo, busqueda, ordenCol, ordenDir])

  const activos   = clientes.filter(c => c.estado === 'ACTIVO').length
  const inactivos = clientes.filter(c => c.estado !== 'ACTIVO').length

  const clientesVisibles = clientesFiltrados.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA)

  const columnas = [
    { key: 'nombreDisplay',  label: 'Nombre / Razón social', sortable: true  },
    { key: 'emailPrincipal', label: 'Email',                  sortable: true  },
    { key: 'tipoPersona',    label: 'Tipo',                   sortable: true  },
    { key: 'tipoCliente',    label: 'Modalidad',              sortable: true  },
    { key: 'telefono',       label: 'Teléfono',               sortable: false },
    { key: 'ultimoAcceso',   label: 'Último acceso',          sortable: true  },
    { key: 'estado',         label: 'Estado',                 sortable: true  },
    { key: '_acc',           label: 'Acciones',               sortable: false },
  ]

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Clientes</h2>
          <p className="text-sm text-gray-500 mt-0.5">Gestión y validaciones de cuentas cliente</p>
        </div>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error}
          <button onClick={() => setError(null)} className="ml-4 text-red-400 hover:text-red-600">×</button>
        </div>
      )}

      {/* Filtros */}
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <input
          type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, email, NIT…"
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />

        {/* Estado */}
        <div className="flex gap-1">
          {[
            { k: 'todos',    l: 'Todos',    n: clientes.length },
            { k: 'activos',  l: 'Activos',  n: activos         },
            { k: 'inactivos',l: 'Inactivos',n: inactivos       },
          ].map(f => (
            <button key={f.k} onClick={() => setFiltroEstado(f.k)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                filtroEstado === f.k
                  ? f.k === 'inactivos' ? 'bg-red-100 text-red-700' : f.k === 'activos' ? 'bg-green-100 text-green-700' : 'bg-indigo-100 text-indigo-700'
                  : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}>
              {f.l} <span className="ml-1 opacity-70">{f.n}</span>
            </button>
          ))}
        </div>

        {/* Tipo */}
        <select value={filtroTipo} onChange={e => setFiltroTipo(e.target.value)}
          className="border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white">
          <option value="todos">Todos los tipos</option>
          <option value="natural">Persona natural</option>
          <option value="juridica">Persona jurídica</option>
          <option value="prepago">Prepago</option>
          <option value="pospago">Pospago</option>
        </select>

        <span className="text-xs text-gray-400">{clientesFiltrados.length} resultado{clientesFiltrados.length !== 1 ? 's' : ''}</span>
      </div>

      {/* Tabla */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" />
          </div>
        ) : clientesFiltrados.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">
            {clientes.length === 0 ? 'No hay clientes registrados.' : 'No hay clientes que coincidan con el filtro.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  {columnas.map(col => (
                    <th key={col.key}
                      onClick={col.sortable ? () => manejarOrden(col.key) : undefined}
                      className={`px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider select-none whitespace-nowrap ${col.sortable ? 'cursor-pointer hover:text-gray-700' : ''}`}>
                      {col.label}
                      {col.sortable && <IconoOrden activo={ordenCol === col.key} dir={ordenDir} />}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {clientesVisibles.map(c => (
                  <tr key={c.idCliente} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3">
                      <p className="text-sm font-medium text-gray-900">{c.nombreDisplay}</p>
                      {c.nit && <p className="text-xs text-gray-400">NIT: {c.nit}{c.dv ? `-${c.dv}` : ''}</p>}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 whitespace-nowrap">{c.emailPrincipal}</td>
                    <td className="px-4 py-3">
                      <span className="text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-600 font-medium">
                        {c.tipoPersona === 'NATURAL' ? 'Natural' : 'Jurídica'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-col gap-1">
                        <span className={`text-xs px-2 py-0.5 rounded font-medium w-fit ${
                          c.tipoCliente === 'PREPAGO' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'
                        }`}>
                          {c.tipoCliente === 'PREPAGO' ? 'Prepago' : 'Pospago'}
                        </span>
                        {c.pospago?.estadoMora && c.pospago.estadoMora !== 'SIN_MORA' && (
                          <BadgeMora mora={c.pospago.estadoMora} />
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">{c.telefono ?? '—'}</td>
                    <td className="px-4 py-3 text-sm text-gray-500 whitespace-nowrap">{formatRelativo(c.ultimoAcceso)}</td>
                    <td className="px-4 py-3"><Badge activo={c.estado === 'ACTIVO'} /></td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5 flex-nowrap">
                        <button onClick={() => setDetalle(c)}
                          className="text-xs font-medium px-2.5 py-1 rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors whitespace-nowrap">
                          Validar
                        </button>
                        <button onClick={() => setEditando(c)}
                          className="text-xs font-medium px-2.5 py-1 rounded-full border border-indigo-200 text-indigo-600 hover:bg-indigo-50 transition-colors whitespace-nowrap">
                          Editar
                        </button>
                        <button
                          onClick={() => cambiarEstado(c, c.estado !== 'ACTIVO')}
                          className={`text-xs font-medium px-2.5 py-1 rounded-full border transition-colors whitespace-nowrap ${
                            c.estado === 'ACTIVO'
                              ? 'border-red-200 text-red-600 hover:bg-red-50'
                              : 'border-green-200 text-green-700 hover:bg-green-50'
                          }`}>
                          {c.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Paginacion
          pagina={pagina}
          total={clientesFiltrados.length}
          porPagina={POR_PAGINA}
          onChange={setPagina}
        />
      </div>

      {editando && (
        <ModalEditar
          cliente={editando}
          onClose={() => setEditando(null)}
          onGuardado={() => { cargar(); mostrarToast('Cliente actualizado correctamente') }}
        />
      )}

      {detalle && <ModalDetalle cliente={detalle} onClose={() => setDetalle(null)} />}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

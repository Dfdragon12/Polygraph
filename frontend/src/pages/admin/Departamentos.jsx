import { useId, useState, useEffect, useCallback, useMemo } from 'react'
import departamentoService from '../../services/departamentoService'
import Toast from '../../components/Toast'
import { Modal, ConfirmModal } from '../../components/ui/Modal'
import MenuAcciones from '../../components/ui/MenuAcciones'
import Paginacion, { usePaginacion } from '../../components/ui/Paginacion'

const ICONO_LAPIZ = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M15 6.75l2.25 2.25" />
  </svg>
)

const ICONO_BASURA = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
  </svg>
)

/** Alta/edición de un departamento — mismo modal para ambos casos (si viene `departamento`, es edición). */
function ModalDepartamento({ departamento, onClose, onGuardado }) {
  const idCampos = useId()
  const esEdicion = !!departamento
  const [nombre, setNombre] = useState(departamento?.nombre ?? '')
  const [codigoDane, setCodigoDane] = useState(departamento?.codigoDane ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const guardar = async (e) => {
    e.preventDefault()
    if (!nombre.trim() || !codigoDane.trim()) {
      setError('Completa el nombre y el código DANE.')
      return
    }
    setGuardando(true)
    setError(null)
    try {
      const payload = { nombre: nombre.trim(), codigoDane: codigoDane.trim() }
      const data = esEdicion
        ? await departamentoService.actualizar(departamento.idDepartamento, payload)
        : await departamentoService.crear(payload)
      onGuardado(data, esEdicion)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo guardar el departamento.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={esEdicion ? 'Editar departamento' : 'Agregar departamento'} onClose={onClose} ancho="max-w-sm">
      <form onSubmit={guardar} className="px-6 py-5 space-y-4">
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
        )}
        <div>
          <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
          <input id={`${idCampos}-1`} value={nombre} onChange={(e) => setNombre(e.target.value)} autoFocus
            placeholder="Ej: Cundinamarca"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" />
        </div>
        <div>
          <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Código DANE <span className="text-red-500">*</span></label>
          <input id={`${idCampos}-2`} value={codigoDane} inputMode="numeric" maxLength={2}
            onChange={(e) => setCodigoDane(e.target.value.replace(/\D/g, ''))}
            placeholder="Ej: 25"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" />
          <p className="text-[11px] text-gray-400 mt-1">Numérico, 2 dígitos.</p>
        </div>
        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} disabled={guardando}
            className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
            Cancelar
          </button>
          <button type="submit" disabled={guardando}
            className="px-4 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50 transition-colors">
            {guardando ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default function Departamentos() {
  const [departamentos, setDepartamentos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [editando, setEditando] = useState(null)
  const [agregando, setAgregando] = useState(false)
  const [aEliminar, setAEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      setDepartamentos(await departamentoService.listar())
    } catch { setError('Error al cargar departamentos.') }
    finally { setCargando(false) }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const visibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    if (!q) return departamentos
    return departamentos.filter(d => d.nombre.toLowerCase().includes(q) || d.codigoDane.includes(q))
  }, [departamentos, busqueda])
  const pag = usePaginacion(visibles, busqueda)

  const onGuardado = (data, esEdicion) => {
    setDepartamentos(prev => {
      const actualizada = esEdicion
        ? prev.map(d => d.idDepartamento === data.idDepartamento ? data : d)
        : [...prev, data]
      return [...actualizada].sort((a, b) => a.nombre.localeCompare(b.nombre))
    })
    setToast({ mensaje: esEdicion ? `"${data.nombre}" actualizado` : `"${data.nombre}" agregado`, tipo: 'exito' })
    setEditando(null)
    setAgregando(false)
  }

  const eliminar = async () => {
    if (!aEliminar) return
    setEliminando(true)
    try {
      await departamentoService.eliminar(aEliminar.idDepartamento)
      setDepartamentos(prev => prev.filter(d => d.idDepartamento !== aEliminar.idDepartamento))
      setToast({ mensaje: `"${aEliminar.nombre}" eliminado`, tipo: 'exito' })
      setAEliminar(null)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo eliminar el departamento.')
      setAEliminar(null)
    } finally {
      setEliminando(false)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Departamentos</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            Catálogo de departamentos usado al registrar ciudades — antes era una lista fija, ahora se administra desde acá.
          </p>
        </div>
        <button onClick={() => setAgregando(true)}
          className="bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors whitespace-nowrap">
          + Agregar departamento
        </button>
      </div>

      {error && (
        <div className="mb-3 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error}
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      <input aria-label="Buscar por nombre o código DANE..." type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)}
        placeholder="Buscar por nombre o código DANE..."
        className="mb-3 w-full max-w-sm border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
        ) : visibles.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">
            {departamentos.length === 0 ? 'No hay departamentos registrados todavía.' : 'No hay resultados para la búsqueda.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-stack min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  {['Departamento', 'Código DANE', 'Estado', '···'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {pag.visibles.map(d => (
                  <tr key={d.idDepartamento} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">{d.nombre}</td>
                    <td className="px-4 py-3 text-sm text-gray-400 font-mono">{d.codigoDane}</td>
                    <td className="px-4 py-3">
                      {d.enUso ? (
                        <span className="text-xs text-gray-400">En uso por ciudades registradas</span>
                      ) : (
                        <span className="text-xs text-gray-300">Sin ciudades registradas</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <MenuAcciones opciones={[
                        { label: 'Editar', icono: ICONO_LAPIZ, onClick: () => setEditando(d) },
                        {
                          label: 'Eliminar', icono: ICONO_BASURA, destacado: 'text-red-600',
                          disabled: d.enUso,
                          title: d.enUso ? 'No se puede eliminar: hay ciudades registradas en este departamento.' : undefined,
                          onClick: () => setAEliminar(d),
                        },
                      ]} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Paginacion pagina={pag.pagina} total={pag.total} porPagina={pag.porPagina} onChange={pag.setPagina} />
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}

      {(agregando || editando) && (
        <ModalDepartamento
          departamento={editando}
          onClose={() => { setAgregando(false); setEditando(null) }}
          onGuardado={onGuardado}
        />
      )}

      {aEliminar && (
        <ConfirmModal
          titulo="Eliminar departamento"
          danger
          cargando={eliminando}
          confirmLabel="Eliminar"
          cancelLabel="Cancelar"
          onConfirmar={eliminar}
          onCancelar={() => setAEliminar(null)}
        >
          <p className="text-sm text-gray-500">
            ¿Eliminar <span className="font-semibold text-gray-700">"{aEliminar.nombre}"</span> del catálogo?
            Esta acción no se puede deshacer.
          </p>
        </ConfirmModal>
      )}
    </div>
  )
}

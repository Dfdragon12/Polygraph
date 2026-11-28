import { useId, useEffect, useMemo, useState } from 'react'
import preguntasPoligrafiaService from '../../../services/preguntasPoligrafiaService'
import Toast from '../../../components/Toast'
import { Modal, ConfirmModal } from '../../../components/ui/Modal'
import MenuAcciones from '../../../components/ui/MenuAcciones'
import Paginacion, { usePaginacion } from '../../../components/ui/Paginacion'

// Temas que el cliente puede pedir evaluar cuando solicita una poligrafía (máximo 4 por solicitud).
// Mismo diseño de las demás pantallas de administración: buscador, tabla, menú de acciones y modal.
// La solicitud guarda el texto del tema que se pidió: renombrar o borrar uno no cambia lo ya solicitado.

const inputCls = 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white'
const vacio = { id: null, nombre: '', grupo: '', requiereDetalle: false, orden: '', activo: true }

function IconoLapiz() {
  return <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
}
function IconoPower() {
  return <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M5.636 5.636a9 9 0 1012.728 0M12 3v9" /></svg>
}
function IconoBasura() {
  return <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
}

function Badge({ activo }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

function Th({ children, cls = '' }) {
  return <th className={`px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap ${cls}`}>{children}</th>
}

function ModalTema({ inicial, grupos, onClose, onGuardado }) {
  const idCampos = useId()
  const [f, setF] = useState(inicial)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const nuevo = !inicial.id
  const set = (k, v) => setF(x => ({ ...x, [k]: v }))

  const guardar = async (e) => {
    e.preventDefault()
    if (!f.nombre.trim()) return setError('Escribe el nombre del tema.')
    setGuardando(true)
    setError(null)
    try {
      const datos = { nombre: f.nombre.trim(), grupo: (f.grupo ?? '').trim() || null, requiereDetalle: f.requiereDetalle, orden: f.orden === '' ? null : Number(f.orden), activo: f.activo }
      const r = nuevo ? await preguntasPoligrafiaService.crear(datos) : await preguntasPoligrafiaService.actualizar(f.id, datos)
      onGuardado(r, nuevo)
    } catch (err) {
      const d = err.response?.data
      setError(d?.mensaje ?? (d?.errores ? Object.values(d.errores).join(' ') : 'No se pudo guardar.'))
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={nuevo ? 'Nuevo tema' : 'Editar tema'} subtitulo="Temas de poligrafía" onClose={onClose} ancho="max-w-xl">
      <form onSubmit={guardar} className="px-6 py-5 space-y-5">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        <div>
          <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Tema <span className="text-red-600">*</span></label>
          <input id={`${idCampos}-1`} value={f.nombre} onChange={e => set('nombre', e.target.value)} maxLength={150} autoFocus
            placeholder="Ej: Robos" className={inputCls} />
          <p className="mt-1 text-[11px] text-gray-500">Así lo ve el cliente al solicitar y el poligrafista en su formulario.</p>
        </div>

        <div>
          <label htmlFor={`${idCampos}-5`} className="block text-xs font-medium text-gray-600 mb-1">Grupo</label>
          <input id={`${idCampos}-5`} value={f.grupo ?? ''} onChange={e => set('grupo', e.target.value)} maxLength={100} list={`${idCampos}-grupos`}
            placeholder="Ej: Conducta laboral" className={inputCls} />
          <datalist id={`${idCampos}-grupos`}>{grupos.map(g => <option key={g} value={g} />)}</datalist>
          <p className="mt-1 text-[11px] text-gray-500">Agrupa los temas parecidos: el cliente los ve juntos y el admin puede escoger un grupo completo para una solicitud. Escribe uno nuevo o elige uno existente.</p>
        </div>

        <div className="flex items-start gap-2.5">
          <input id={`${idCampos}-2`} type="checkbox" checked={f.requiereDetalle} onChange={e => set('requiereDetalle', e.target.checked)}
            className="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
          <div>
            <label htmlFor={`${idCampos}-2`} className="text-sm text-gray-800 cursor-pointer">El cliente debe escribir cuál</label>
            <p className="text-[11px] text-gray-500">Para temas abiertos como «Otros ¿Cuál?»: al elegirlo, el cliente escribe el tema.</p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor={`${idCampos}-3`} className="block text-xs font-medium text-gray-600 mb-1">Posición en la lista</label>
            <input id={`${idCampos}-3`} type="number" min={0} max={9999} value={f.orden} onChange={e => set('orden', e.target.value)}
              placeholder={nuevo ? 'Al final' : ''} className={`${inputCls} tabular-nums`} />
            <p className="mt-1 text-[11px] text-gray-500">De menor a mayor. Vacío = al final.</p>
          </div>
          <div>
            <label htmlFor={`${idCampos}-4`} className="block text-xs font-medium text-gray-600 mb-1">Estado</label>
            <select id={`${idCampos}-4`} value={f.activo ? 'SI' : 'NO'} onChange={e => set('activo', e.target.value === 'SI')} className={inputCls}>
              <option value="SI">Activo — lo ve el cliente</option>
              <option value="NO">Inactivo — no se muestra</option>
            </select>
          </div>
        </div>

        <div className="flex justify-end gap-3 border-t border-gray-100 pt-4">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 transition-colors">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Guardando...' : nuevo ? 'Crear' : 'Guardar cambios'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default function TemasPoligrafia() {
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [grupoFiltro, setGrupoFiltro] = useState('')
  const [modal, setModal] = useState(null)
  const [borrar, setBorrar] = useState(null)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    preguntasPoligrafiaService.listar().then(setLista).catch(() => setError('No se pudieron cargar los temas de poligrafía.'))
  }, [])

  const visibles = useMemo(() => (lista ?? [])
    .filter(t => !busqueda || t.nombre.toLowerCase().includes(busqueda.toLowerCase()) || (t.grupo ?? '').toLowerCase().includes(busqueda.toLowerCase()))
    .filter(t => !grupoFiltro || (grupoFiltro === '__sin' ? !t.grupo : t.grupo === grupoFiltro))
    .sort((a, b) => a.orden - b.orden || a.id - b.id), [lista, busqueda, grupoFiltro])
  const grupos = useMemo(() => [...new Set((lista ?? []).map(t => t.grupo).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es')), [lista])
  const pag = usePaginacion(visibles, `${busqueda}|${grupoFiltro}`)
  const activos = (lista ?? []).filter(t => t.activo).length

  const guardado = (r, nuevo) => {
    setLista(l => (nuevo ? [...l, r] : l.map(x => (x.id === r.id ? r : x))))
    setModal(null)
    setToast({ mensaje: nuevo ? 'Creado correctamente' : 'Actualizado correctamente', tipo: 'exito' })
  }

  const cambiarEstado = async (t) => {
    try {
      const r = await preguntasPoligrafiaService.actualizar(t.id, { ...t, activo: !t.activo })
      setLista(l => l.map(x => (x.id === r.id ? r : x)))
      setToast({ mensaje: r.activo ? 'Activado correctamente' : 'Desactivado correctamente', tipo: 'exito' })
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al cambiar el estado.')
    }
  }

  const eliminar = async () => {
    try {
      await preguntasPoligrafiaService.eliminar(borrar.id)
      setLista(l => l.filter(x => x.id !== borrar.id))
      setToast({ mensaje: 'Eliminado correctamente', tipo: 'exito' })
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo eliminar.', tipo: 'error' })
    } finally {
      setBorrar(null)
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Temas de poligrafía</h2>
          <p className="text-sm text-gray-500 mt-0.5">Los temas que el cliente puede pedir evaluar al solicitar una poligrafía (máximo 4 por solicitud), organizados por grupo. El poligrafista los ve en su formulario y el admin puede cambiar los de una solicitud.</p>
        </div>
        <button onClick={() => setModal(vacio)}
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          <span className="text-base leading-none">+</span> Nuevo tema
        </button>
      </div>

      {error && <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      <div className="flex flex-wrap items-center gap-3 mb-3">
        <input aria-label="Buscar tema" type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)} placeholder="Buscar…"
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        <select aria-label="Filtrar por grupo" value={grupoFiltro} onChange={e => setGrupoFiltro(e.target.value)}
          className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
          <option value="">Todos los grupos</option>
          {grupos.map(g => <option key={g} value={g}>{g}</option>)}
          <option value="__sin">Sin grupo</option>
        </select>
        <span className="text-xs text-gray-500">
          {visibles.length} resultado{visibles.length !== 1 ? 's' : ''}{lista ? ` · ${activos} activo${activos !== 1 ? 's' : ''}` : ''}
        </span>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {!lista ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : visibles.length === 0 ? (
          <div className="text-center py-16 text-gray-500 text-sm">
            {busqueda ? 'Nada coincide con la búsqueda.' : 'No hay temas: el cliente no podrá elegir qué evaluar en la poligrafía.'}
          </div>
        ) : (
          <div className="overflow-x-auto scrollbar-thin">
            <table className="table-stack w-full divide-y divide-gray-100 text-sm">
              <thead className="bg-gray-50">
                <tr><Th cls="w-16">N.°</Th><Th>Tema</Th><Th>Grupo</Th><Th cls="hidden lg:table-cell">Pide escribir cuál</Th><Th>Estado</Th><Th>···</Th></tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {pag.visibles.map((t, n) => (
                  <tr key={t.id} className="hover:bg-gray-50/70 transition-colors">
                    <td className="px-4 py-3 text-gray-500 tabular-nums">{pag.desde + n + 1}</td>
                    <td className="px-4 py-3 font-medium text-gray-900 min-w-[14rem]">{t.nombre}</td>
                    <td className="px-4 py-3 text-xs text-gray-700">{t.grupo || <span className="text-gray-500">Sin grupo</span>}</td>
                    <td className="hidden lg:table-cell px-4 py-3 text-xs text-gray-600">{t.requiereDetalle ? 'Sí' : 'No'}</td>
                    <td className="px-4 py-3"><Badge activo={t.activo} /></td>
                    <td className="px-4 py-3 text-center">
                      <MenuAcciones opciones={[
                        { label: 'Editar', icono: <IconoLapiz />, onClick: () => setModal({ ...t }) },
                        { label: t.activo ? 'Desactivar' : 'Activar', icono: <IconoPower />, destacado: t.activo ? 'text-red-600' : 'text-green-700', onClick: () => cambiarEstado(t) },
                        { label: 'Eliminar', icono: <IconoBasura />, destacado: 'text-red-600', onClick: () => setBorrar(t) },
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

      {modal && <ModalTema inicial={modal} grupos={grupos} onClose={() => setModal(null)} onGuardado={guardado} />}
      {borrar && (
        <ConfirmModal titulo="¿Eliminar este tema?" danger confirmLabel="Eliminar" onConfirmar={eliminar} onCancelar={() => setBorrar(null)}>
          <p className="text-sm text-gray-600">{borrar.nombre}</p>
          <p className="mt-2 text-xs text-gray-500">Las solicitudes que ya lo pidieron conservan el tema. Si solo quieres que deje de ofrecerse, mejor desactívalo.</p>
        </ConfirmModal>
      )}
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

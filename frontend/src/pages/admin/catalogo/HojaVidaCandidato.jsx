import { useId, useEffect, useMemo, useState } from 'react'
import hojaVidaCatalogoService from '../../../services/hojaVidaCatalogoService'
import Toast from '../../../components/Toast'
import { Modal, ConfirmModal } from '../../../components/ui/Modal'
import MenuAcciones from '../../../components/ui/MenuAcciones'
import Paginacion, { usePaginacion } from '../../../components/ui/Paginacion'

// Listas y preguntas que ve el candidato en su hoja de vida. Mismo diseño de las demás pantallas
// de administración (Equipo Polygraph, Catálogo de hallazgos): tarjetas de filtro, buscador, tabla
// y menú de acciones; se crea y edita en un modal. La hoja de vida guarda el texto que el
// candidato eligió, así que borrar una opción no cambia lo que ya respondió alguien.

const inputCls = 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white disabled:bg-gray-50 disabled:text-gray-500'

const LISTAS = [
  { value: 'LIBRETA_MILITAR', label: 'Libreta militar', singular: 'opción de libreta militar', corto: 'LM', border: 'border-sky-200', badge: 'bg-sky-500', texto: 'text-sky-600', bgActivo: 'bg-sky-50/70', ring: 'ring-sky-300' },
  { value: 'EPS', label: 'EPS', singular: 'EPS', corto: 'EP', border: 'border-emerald-200', badge: 'bg-emerald-500', texto: 'text-emerald-600', bgActivo: 'bg-emerald-50/70', ring: 'ring-emerald-300' },
  { value: 'FONDO_PENSIONES', label: 'Fondos de pensiones', singular: 'fondo de pensiones', corto: 'FP', border: 'border-violet-200', badge: 'bg-violet-500', texto: 'text-violet-600', bgActivo: 'bg-violet-50/70', ring: 'ring-violet-300' },
  { value: 'TIPO_CONTRATO', label: 'Tipos de contrato', singular: 'tipo de contrato', corto: 'TC', border: 'border-amber-200', badge: 'bg-amber-500', texto: 'text-amber-600', bgActivo: 'bg-amber-50/70', ring: 'ring-amber-300' },
  { value: 'PREGUNTA', label: 'Preguntas', singular: 'pregunta', corto: '?', border: 'border-red-200', badge: 'bg-red-500', texto: 'text-red-600', bgActivo: 'bg-red-50/70', ring: 'ring-red-300' },
]
const LISTA = Object.fromEntries(LISTAS.map(l => [l.value, l]))
const vacio = (lista) => ({ id: null, lista, nombre: '', descripcion: '', orden: '', activo: true })

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

function ModalItem({ inicial, onClose, onGuardado }) {
  const idCampos = useId()
  const [f, setF] = useState(inicial)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const nuevo = !inicial.id
  const esPregunta = f.lista === 'PREGUNTA'
  const set = (k, v) => setF(x => ({ ...x, [k]: v }))

  const guardar = async (e) => {
    e.preventDefault()
    if (!f.nombre.trim()) return setError(esPregunta ? 'Escribe la pregunta.' : 'Escribe el nombre de la opción.')
    setGuardando(true)
    setError(null)
    try {
      const datos = { lista: f.lista, nombre: f.nombre.trim(), descripcion: esPregunta ? f.descripcion?.trim() || null : null,
        orden: f.orden === '' ? null : Number(f.orden), activo: f.activo }
      const r = nuevo ? await hojaVidaCatalogoService.crear(datos) : await hojaVidaCatalogoService.actualizar(f.id, datos)
      onGuardado(r, nuevo)
    } catch (err) {
      const d = err.response?.data
      setError(d?.mensaje ?? (d?.errores ? Object.values(d.errores).join(' ') : 'No se pudo guardar.'))
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={nuevo ? `Nueva ${LISTA[f.lista].singular}` : `Editar ${LISTA[f.lista].singular}`}
      subtitulo="Hoja de vida del candidato" onClose={onClose} ancho="max-w-2xl">
      <form onSubmit={guardar} className="px-6 py-5 space-y-5">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{esPregunta ? 'Pregunta' : 'Opción'}</p>
          {nuevo && (
            <div>
              <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Lista <span className="text-red-500">*</span></label>
              <select id={`${idCampos}-1`} value={f.lista} onChange={e => set('lista', e.target.value)} className={inputCls}>
                {LISTAS.map(l => <option key={l.value} value={l.value}>{l.label}</option>)}
              </select>
            </div>
          )}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{esPregunta ? 'Pregunta' : 'Nombre'} <span className="text-red-500">*</span></label>
            {esPregunta ? (
              <textarea aria-label="Pregunta" value={f.nombre} onChange={e => set('nombre', e.target.value)} rows={2} maxLength={500} autoFocus spellCheck lang="es"
                placeholder="Ej: ¿Tiene o ha tenido sanciones disciplinarias o fiscales?" className={`${inputCls} resize-none`} />
            ) : (
              <input aria-label="Como lo verá el candidato en la lista" value={f.nombre} onChange={e => set('nombre', e.target.value)} maxLength={150} autoFocus
                placeholder="Como lo verá el candidato en la lista" className={inputCls} />
            )}
          </div>
          {esPregunta && (
            <div>
              <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Explicación para el candidato</label>
              <textarea id={`${idCampos}-2`} value={f.descripcion ?? ''} onChange={e => set('descripcion', e.target.value)} rows={4} maxLength={2000} spellCheck lang="es"
                placeholder="Qué debe indicar si responde Sí (sale debajo de la pregunta)." className={`${inputCls} resize-none`} />
              <p className="mt-1 text-[11px] text-gray-400">El candidato responde Sí o No; si responde Sí, debe explicar su respuesta.</p>
            </div>
          )}
        </div>

        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Presentación</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor={`${idCampos}-3`} className="block text-xs font-medium text-gray-600 mb-1">Posición en la lista</label>
              <input id={`${idCampos}-3`} type="number" min={0} max={9999} value={f.orden} onChange={e => set('orden', e.target.value)}
                placeholder={nuevo ? 'Al final' : ''} className={`${inputCls} tabular-nums`} />
              <p className="mt-1 text-[11px] text-gray-400">De menor a mayor. Vacío = al final.</p>
            </div>
            <div>
              <label htmlFor={`${idCampos}-4`} className="block text-xs font-medium text-gray-600 mb-1">Estado</label>
              <select id={`${idCampos}-4`} value={f.activo ? 'SI' : 'NO'} onChange={e => set('activo', e.target.value === 'SI')} className={inputCls}>
                <option value="SI">Activo — lo ve el candidato</option>
                <option value="NO">Inactivo — no se muestra</option>
              </select>
            </div>
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

export default function HojaVidaCandidato() {
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(null)
  const [filtro, setFiltro] = useState('LIBRETA_MILITAR')
  const [busqueda, setBusqueda] = useState('')
  const [modal, setModal] = useState(null)
  const [borrar, setBorrar] = useState(null)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    hojaVidaCatalogoService.listar().then(setLista).catch(() => setError('No se pudieron cargar las listas de la hoja de vida.'))
  }, [])

  const visibles = useMemo(() => (lista ?? [])
    .filter(i => i.lista === filtro && (!busqueda || `${i.nombre} ${i.descripcion ?? ''}`.toLowerCase().includes(busqueda.toLowerCase())))
    .sort((a, b) => a.orden - b.orden || a.id - b.id), [lista, filtro, busqueda])
  const pag = usePaginacion(visibles, `${filtro}|${busqueda}`)
  const conteo = useMemo(() => {
    const m = {}
    LISTAS.forEach(l => { m[l.value] = (lista ?? []).filter(i => i.lista === l.value).length })
    return m
  }, [lista])
  const esPregunta = filtro === 'PREGUNTA'

  const guardado = (r, nuevo) => {
    setLista(l => (nuevo ? [...l, r] : l.map(x => (x.id === r.id ? r : x))))
    setFiltro(r.lista)
    setModal(null)
    setToast({ mensaje: nuevo ? 'Creado correctamente' : 'Actualizado correctamente', tipo: 'exito' })
  }

  const cambiarEstado = async (i) => {
    try {
      const r = await hojaVidaCatalogoService.actualizar(i.id, { ...i, activo: !i.activo })
      setLista(l => l.map(x => (x.id === r.id ? r : x)))
      setToast({ mensaje: r.activo ? 'Activado correctamente' : 'Desactivado correctamente', tipo: 'exito' })
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al cambiar el estado.')
    }
  }

  const eliminar = async () => {
    try {
      await hojaVidaCatalogoService.eliminar(borrar.id)
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
          <h2 className="text-2xl font-bold text-gray-900">Hoja de vida del candidato</h2>
          <p className="text-sm text-gray-500 mt-0.5">Las listas y preguntas que el candidato ve al diligenciar su hoja de vida. Un cambio se ve de inmediato en su link.</p>
        </div>
        <button onClick={() => setModal(vacio(filtro))}
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          <span className="text-base leading-none">+</span> {esPregunta ? 'Nueva pregunta' : 'Nueva opción'}
        </button>
      </div>

      {error && <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      {/* Tarjetas: una por lista */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 mb-4">
        {LISTAS.map(r => {
          const activo = filtro === r.value
          return (
            <button key={r.value} type="button" onClick={() => setFiltro(r.value)}
              className={`text-left bg-white rounded-xl border shadow-sm p-3.5 cursor-pointer hover:shadow-md transition-shadow ${r.border} ${activo ? `${r.bgActivo} ring-2 ${r.ring}` : ''}`}>
              <div className="flex items-center gap-2 mb-2.5">
                <span className={`w-7 h-7 rounded-full ${r.badge} text-white text-[10px] font-bold flex items-center justify-center flex-shrink-0`}>{r.corto}</span>
                <span className="text-xs font-medium text-gray-600 leading-tight">{r.label}</span>
              </div>
              <p className={`text-2xl font-bold ${r.texto}`}>{conteo[r.value] ?? 0}</p>
            </button>
          )
        })}
      </div>

      <div className="flex flex-wrap items-center gap-3 mb-3">
        <input aria-label="Buscar…" type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)} placeholder="Buscar…"
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        <span className="text-xs text-gray-400">
          {visibles.length} resultado{visibles.length !== 1 ? 's' : ''}{' · '}
          <span className="font-medium text-gray-600">{LISTA[filtro].label}</span>
        </span>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {!lista ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : visibles.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">
            {busqueda ? 'Nada coincide con la búsqueda.' : esPregunta ? 'No hay preguntas: el candidato no verá el paso de declaraciones con preguntas.' : 'Esta lista está vacía: el candidato no tendrá opciones para elegir.'}
          </div>
        ) : (
          <div className="overflow-x-auto scrollbar-thin">
            <table className="table-stack w-full divide-y divide-gray-100 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <Th cls="w-16">N.°</Th><Th>{esPregunta ? 'Pregunta' : 'Nombre'}</Th>
                  {esPregunta && <Th cls="hidden lg:table-cell">Explicación</Th>}
                  <Th>Estado</Th><Th>···</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {pag.visibles.map((i, n) => (
                  <tr key={i.id} className="hover:bg-gray-50/70 transition-colors">
                    <td className="px-4 py-3 text-gray-400 tabular-nums">{pag.desde + n + 1}</td>
                    <td className="px-4 py-3 font-medium text-gray-900 min-w-[14rem]">{i.nombre}</td>
                    {esPregunta && <td className="hidden lg:table-cell px-4 py-3 text-xs text-gray-500 max-w-md">{i.descripcion || '—'}</td>}
                    <td className="px-4 py-3"><Badge activo={i.activo} /></td>
                    <td className="px-4 py-3 text-center">
                      <MenuAcciones opciones={[
                        { label: 'Editar', icono: <IconoLapiz />, onClick: () => setModal({ ...i, descripcion: i.descripcion ?? '' }) },
                        { label: i.activo ? 'Desactivar' : 'Activar', icono: <IconoPower />, destacado: i.activo ? 'text-red-600' : 'text-green-700', onClick: () => cambiarEstado(i) },
                        { label: 'Eliminar', icono: <IconoBasura />, destacado: 'text-red-600', onClick: () => setBorrar(i) },
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

      {modal && <ModalItem inicial={modal} onClose={() => setModal(null)} onGuardado={guardado} />}
      {borrar && (
        <ConfirmModal titulo={`¿Eliminar esta ${LISTA[borrar.lista].singular}?`} danger confirmLabel="Eliminar" onConfirmar={eliminar} onCancelar={() => setBorrar(null)}>
          <p className="text-sm text-gray-600">{borrar.nombre}</p>
          <p className="mt-2 text-xs text-gray-400">Lo que los candidatos ya respondieron no cambia.</p>
        </ConfirmModal>
      )}
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

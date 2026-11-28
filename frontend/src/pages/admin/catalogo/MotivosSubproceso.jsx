import { useId, useEffect, useMemo, useState } from 'react'
import motivosService from '../../../services/motivosService'
import Toast from '../../../components/Toast'
import { Modal } from '../../../components/ui/Modal'
import MenuAcciones from '../../../components/ui/MenuAcciones'
import Paginacion, { usePaginacion } from '../../../components/ui/Paginacion'

// Motivos que se eligen al reprogramar una cita, cancelar un subproceso o devolverlo a corrección. Lo usado no se borra: se
// desactiva. El motivo «Otro» (pide texto) lo marca la casilla «Pide escribir cuál».

const inputCls = 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white'
const TIPOS = [
  { valor: 'REPROGRAMACION', etiqueta: 'Reprogramación', ayuda: 'Cuando una cita no se realiza y se reagenda.' },
  { valor: 'CANCELACION', etiqueta: 'Cancelación', ayuda: 'Cuando se cancela un subproceso o la solicitud.' },
  { valor: 'DEVOLUCION', etiqueta: 'Devolución', ayuda: 'Cuando el gestor devuelve un informe a corrección.' },
]
const nombreTipo = (v) => TIPOS.find(t => t.valor === v)?.etiqueta ?? v
const vacio = (tipo) => ({ id: null, tipo, nombre: '', requiereTexto: false, orden: '', activo: true })

function IconoLapiz() {
  return <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
}
function IconoPower() {
  return <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M5.636 5.636a9 9 0 1012.728 0M12 3v9" /></svg>
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

function ModalMotivo({ inicial, onClose, onGuardado }) {
  const idCampos = useId()
  const [f, setF] = useState(inicial)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const nuevo = !inicial.id
  const set = (k, v) => setF(x => ({ ...x, [k]: v }))

  const guardar = async (e) => {
    e.preventDefault()
    if (!f.nombre.trim()) return setError('Escribe el motivo.')
    setGuardando(true)
    setError(null)
    try {
      const datos = { tipo: f.tipo, nombre: f.nombre.trim(), requiereTexto: f.requiereTexto, orden: f.orden === '' ? null : Number(f.orden), activo: f.activo }
      const r = nuevo ? await motivosService.crear(datos) : await motivosService.actualizar(f.id, datos)
      onGuardado(r, nuevo)
    } catch (err) {
      const d = err.response?.data
      setError(d?.mensaje ?? (d?.errores ? Object.values(d.errores).join(' ') : 'No se pudo guardar.'))
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={nuevo ? 'Nuevo motivo' : 'Editar motivo'} subtitulo={nombreTipo(f.tipo)} onClose={onClose} ancho="max-w-xl">
      <form onSubmit={guardar} className="px-6 py-5 space-y-5">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        <div>
          <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Lista <span className="text-red-600">*</span></label>
          <select id={`${idCampos}-1`} value={f.tipo} onChange={e => set('tipo', e.target.value)} disabled={!nuevo} className={inputCls}>
            {TIPOS.map(t => <option key={t.valor} value={t.valor}>{t.etiqueta}</option>)}
          </select>
          {!nuevo && <p className="mt-1 text-[11px] text-gray-500">La lista de un motivo no se cambia; crea uno nuevo en la lista que quieras.</p>}
        </div>

        <div>
          <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Motivo <span className="text-red-600">*</span></label>
          <input id={`${idCampos}-2`} value={f.nombre} onChange={e => set('nombre', e.target.value)} maxLength={150} autoFocus
            placeholder="Ej: Falla técnica o de conexión" className={inputCls} />
        </div>

        <div className="flex items-start gap-2.5">
          <input id={`${idCampos}-3`} type="checkbox" checked={f.requiereTexto} onChange={e => set('requiereTexto', e.target.checked)}
            className="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
          <div>
            <label htmlFor={`${idCampos}-3`} className="text-sm text-gray-800 cursor-pointer">Pide escribir cuál</label>
            <p className="text-[11px] text-gray-500">Para el motivo «Otro»: quien actúa debe explicar cuál es.</p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor={`${idCampos}-4`} className="block text-xs font-medium text-gray-600 mb-1">Posición en la lista</label>
            <input id={`${idCampos}-4`} type="number" min={0} max={9999} value={f.orden} onChange={e => set('orden', e.target.value)}
              placeholder={nuevo ? 'Al final' : ''} className={`${inputCls} tabular-nums`} />
          </div>
          <div>
            <label htmlFor={`${idCampos}-5`} className="block text-xs font-medium text-gray-600 mb-1">Estado</label>
            <select id={`${idCampos}-5`} value={f.activo ? 'SI' : 'NO'} onChange={e => set('activo', e.target.value === 'SI')} className={inputCls}>
              <option value="SI">Activo — se ofrece</option>
              <option value="NO">Inactivo — no se ofrece</option>
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

export default function MotivosSubproceso() {
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(null)
  const [tipo, setTipo] = useState('REPROGRAMACION')
  const [modal, setModal] = useState(null)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    motivosService.todos().then(setLista).catch(() => setError('No se pudieron cargar los motivos.'))
  }, [])

  const visibles = useMemo(() => (lista ?? []).filter(m => m.tipo === tipo).sort((a, b) => a.orden - b.orden || a.id - b.id), [lista, tipo])
  const pag = usePaginacion(visibles, tipo)
  const cuantos = (t) => (lista ?? []).filter(m => m.tipo === t && m.activo).length

  const guardado = (r, nuevo) => {
    setLista(l => (nuevo ? [...l, r] : l.map(x => (x.id === r.id ? r : x))))
    setModal(null)
    setToast({ mensaje: nuevo ? 'Creado correctamente' : 'Actualizado correctamente', tipo: 'exito' })
  }

  const cambiarEstado = async (m) => {
    try {
      const r = await motivosService.actualizar(m.id, { ...m, activo: !m.activo })
      setLista(l => l.map(x => (x.id === r.id ? r : x)))
      setToast({ mensaje: r.activo ? 'Activado correctamente' : 'Desactivado correctamente', tipo: 'exito' })
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'Error al cambiar el estado.', tipo: 'error' })
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Motivos de reprogramación, cancelación y devolución</h2>
          <p className="text-sm text-gray-500 mt-0.5">Las listas que ven el gestor, el programador y el equipo al reprogramar una cita, cancelar un subproceso o devolver un informe. Lo usado no se borra: se desactiva.</p>
        </div>
        <button onClick={() => setModal(vacio(tipo))}
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          <span className="text-base leading-none">+</span> Nuevo motivo
        </button>
      </div>

      {error && <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      <div className="flex flex-wrap gap-2 mb-3" role="group" aria-label="Lista de motivos">
        {TIPOS.map(t => (
          <button key={t.valor} type="button" aria-pressed={tipo === t.valor} onClick={() => setTipo(t.valor)}
            className={`px-3 py-1.5 rounded-lg border text-sm transition-colors ${tipo === t.valor ? 'bg-primary-600 border-primary-600 text-white' : 'bg-white border-gray-200 text-gray-700 hover:bg-gray-50'}`}>
            {t.etiqueta} <span className={`ml-1 text-xs ${tipo === t.valor ? 'text-white/90' : 'text-gray-500'}`}>{lista ? cuantos(t.valor) : ''}</span>
          </button>
        ))}
      </div>
      <p className="text-xs text-gray-500 mb-3">{TIPOS.find(t => t.valor === tipo)?.ayuda}</p>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {!lista ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : visibles.length === 0 ? (
          <div className="text-center py-16 text-gray-500 text-sm">No hay motivos en esta lista: nadie podrá completar la acción.</div>
        ) : (
          <div className="overflow-x-auto scrollbar-thin">
            <table className="table-stack w-full divide-y divide-gray-100 text-sm">
              <thead className="bg-gray-50">
                <tr><Th cls="w-16">N.°</Th><Th>Motivo</Th><Th cls="hidden lg:table-cell">Pide escribir cuál</Th><Th>Estado</Th><Th>···</Th></tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {pag.visibles.map((m, n) => (
                  <tr key={m.id} className="hover:bg-gray-50/70 transition-colors">
                    <td className="px-4 py-3 text-gray-500 tabular-nums">{pag.desde + n + 1}</td>
                    <td className="px-4 py-3 font-medium text-gray-900 min-w-[14rem]">{m.nombre}</td>
                    <td className="hidden lg:table-cell px-4 py-3 text-xs text-gray-600">{m.requiereTexto ? 'Sí' : 'No'}</td>
                    <td className="px-4 py-3"><Badge activo={m.activo} /></td>
                    <td className="px-4 py-3 text-center">
                      <MenuAcciones opciones={[
                        { label: 'Editar', icono: <IconoLapiz />, onClick: () => setModal({ ...m }) },
                        { label: m.activo ? 'Desactivar' : 'Activar', icono: <IconoPower />, destacado: m.activo ? 'text-red-600' : 'text-green-700', onClick: () => cambiarEstado(m) },
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

      {modal && <ModalMotivo inicial={modal} onClose={() => setModal(null)} onGuardado={guardado} />}
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

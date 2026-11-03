import { useEffect, useId, useMemo, useState } from 'react'
import preguntasPoligrafiaService from '../../services/preguntasPoligrafiaService'
import { Modal } from '../ui/Modal'

// Admin o gestor cambian los temas a evaluar de una poligrafía ya solicitada: se marcan uno a uno o se agrega un GRUPO completo del catálogo
// (Admin → Novedades → Temas de poligrafía). Sin el tope de 4 del cliente (hasta MAXIMO).
const MAXIMO = 20
const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none'

// actuales: [{ nombre, detalle }] tal como los trae la ficha; se casan con el catálogo por nombre.
export default function EditarTemasSolicitud({ idSubproceso, actuales, onClose, onGuardado }) {
  const idCampos = useId()
  const [catalogo, setCatalogo] = useState(null)
  const [elegidos, setElegidos] = useState(null)   // [{ idPregunta, detalle }]
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    preguntasPoligrafiaService.activas().then(c => {
      setCatalogo(c)
      setElegidos((actuales ?? []).map(a => {
        const t = c.find(x => x.nombre === a.nombre)
        return t ? { idPregunta: t.id, detalle: a.detalle ?? '' } : null
      }).filter(Boolean))
    }).catch(() => setError('No se pudo cargar el catálogo de temas.'))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const grupos = useMemo(() => {
    const m = new Map()
    ;(catalogo ?? []).forEach(t => {
      const g = t.grupo || 'Otros'
      if (!m.has(g)) m.set(g, [])
      m.get(g).push(t)
    })
    return [...m.entries()]
  }, [catalogo])

  const esta = (id) => (elegidos ?? []).some(e => e.idPregunta === id)
  const alternar = (t) => setElegidos(l => (esta(t.id) ? l.filter(e => e.idPregunta !== t.id) : l.length >= MAXIMO ? l : [...l, { idPregunta: t.id, detalle: '' }]))
  const grupoCompleto = (lista) => lista.every(t => esta(t.id))
  const alternarGrupo = (lista) => setElegidos(l => {
    if (grupoCompleto(lista)) return l.filter(e => !lista.some(t => t.id === e.idPregunta))
    const nuevos = lista.filter(t => !l.some(e => e.idPregunta === t.id)).map(t => ({ idPregunta: t.id, detalle: '' }))
    return [...l, ...nuevos].slice(0, MAXIMO)
  })
  const escribir = (id, detalle) => setElegidos(l => l.map(e => (e.idPregunta === id ? { ...e, detalle } : e)))

  const guardar = async () => {
    const falta = (elegidos ?? []).map(e => ({ e, t: catalogo.find(x => x.id === e.idPregunta) })).find(({ e, t }) => t?.requiereDetalle && !(e.detalle ?? '').trim())
    if (falta) return setError(`Escribe cuál es el tema en «${falta.t.nombre}».`)
    setGuardando(true)
    setError(null)
    try {
      const r = await preguntasPoligrafiaService.reemplazarEnSolicitud(idSubproceso, elegidos)
      onGuardado(r)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudieron guardar los temas.')
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Temas a evaluar" subtitulo="Cambia lo que se evaluará en esta poligrafía" onClose={onClose} ancho="max-w-2xl">
      <div className="px-6 py-5 space-y-4 overflow-y-auto">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
        {!catalogo || !elegidos ? (
          <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-7 w-7 border-4 border-primary-600 border-t-transparent" /></div>
        ) : (
          <>
            <p className="text-xs text-gray-700" aria-live="polite">{elegidos.length} de {MAXIMO} temas. Marca los que quieras o agrega un grupo completo. El poligrafista los verá en «Preguntas relevantes».</p>
            {grupos.map(([nombre, lista], gi) => (
              <div key={nombre}>
                <div className="flex items-center justify-between gap-3 mb-1.5">
                  <h3 className="text-[11px] font-semibold text-gray-600 uppercase tracking-wider">{nombre}</h3>
                  <button type="button" onClick={() => alternarGrupo(lista)} className="text-xs font-medium text-primary-700 hover:underline">
                    {grupoCompleto(lista) ? 'Quitar el grupo' : 'Agregar el grupo completo'}
                  </button>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {lista.map((t, i) => {
                    const id = `${idCampos}-${gi}-${i}`
                    const marcado = esta(t.id)
                    const actual = (elegidos ?? []).find(e => e.idPregunta === t.id)
                    return (
                      <div key={t.id} className={`rounded-lg border px-3 py-2.5 ${marcado ? 'border-primary-300 bg-primary-50/60' : 'border-gray-200'}`}>
                        <div className="flex items-start gap-2.5">
                          <input id={id} type="checkbox" checked={marcado} onChange={() => alternar(t)} className="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                          <label htmlFor={id} className="text-sm text-gray-800 cursor-pointer">{t.nombre}</label>
                        </div>
                        {marcado && t.requiereDetalle && (
                          <input aria-label={`¿Cuál? — ${t.nombre}`} className={`${inputCls} mt-2`} maxLength={300} placeholder="Escribe cuál"
                            value={actual?.detalle ?? ''} onChange={e => escribir(t.id, e.target.value)} />
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </>
        )}
      </div>
      <div className="flex justify-end gap-3 border-t border-gray-100 px-6 py-4">
        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
        <button type="button" onClick={guardar} disabled={guardando || !elegidos}
          className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
          {guardando ? 'Guardando...' : 'Guardar temas'}
        </button>
      </div>
    </Modal>
  )
}

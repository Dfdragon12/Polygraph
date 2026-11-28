import { useId, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import hallazgosService from '../../../services/hallazgosService'
import Toast from '../../../components/Toast'
import { Modal, ConfirmModal } from '../../../components/ui/Modal'
import MenuAcciones from '../../../components/ui/MenuAcciones'
import Paginacion, { usePaginacion } from '../../../components/ui/Paginacion'

// Catálogo de hallazgos: la base de la calificación. Mismo diseño de las demás pantallas de
// administración (Equipo Polygraph): tarjetas de filtro, buscador, tabla y menú de acciones; se
// crea y edita en un modal. Un cambio de puntos se usa de inmediato en el cálculo.

const inputCls = 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white disabled:bg-gray-50 disabled:text-gray-500'

// Tarjetas de filtro por tipo. A quién le sale cada hallazgo NO se define aquí: lo decide la
// plantilla al escoger qué hallazgos ofrece cada campo (V210).
const FILTROS = [
  { value: 'PUNTOS', label: 'Suman puntos', corto: 'PT', border: 'border-sky-200', badge: 'bg-sky-500', texto: 'text-sky-600', bgActivo: 'bg-sky-50/70', ring: 'ring-sky-300', cumple: h => h.tipo === 'PUNTOS' },
  { value: 'CRITICO', label: 'Críticos', corto: 'CR', border: 'border-red-200', badge: 'bg-red-500', texto: 'text-red-600', bgActivo: 'bg-red-50/70', ring: 'ring-red-300', cumple: h => h.tipo === 'CRITICO' },
  { value: 'ALERTA', label: 'Alertas', corto: 'AL', border: 'border-amber-200', badge: 'bg-amber-500', texto: 'text-amber-600', bgActivo: 'bg-amber-50/70', ring: 'ring-amber-300', cumple: h => h.tipo === 'ALERTA' },
  { value: 'INACTIVO', label: 'Inactivos', corto: 'IN', border: 'border-gray-200', badge: 'bg-gray-400', texto: 'text-gray-500', bgActivo: 'bg-gray-50', ring: 'ring-gray-300', cumple: h => !h.activo },
]
const FILTRO = Object.fromEntries(FILTROS.map(f => [f.value, f]))
const vacio = () => ({ id: null, codigo: '', descripcion: '', tipo: 'PUNTOS', puntos: '', acumula: false, soloCondicion: false, activo: true })

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

function Valor({ h }) {
  if (h.tipo === 'CRITICO') return <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-red-100 text-red-700">Crítico</span>
  if (h.tipo === 'ALERTA') return <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600">Alerta</span>
  return <span className="font-semibold text-gray-900 tabular-nums">{h.puntos}</span>
}

function Th({ children, cls = '' }) {
  return <th className={`px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap ${cls}`}>{children}</th>
}

const TIPOS = [
  { valor: 'PUNTOS', etiqueta: 'Puntos', detalle: 'Suma sus puntos al subproceso.' },
  { valor: 'CRITICO', etiqueta: 'Crítico', detalle: 'El subproceso queda en 100 y el estudio en ALTO.' },
  { valor: 'ALERTA', etiqueta: 'Alerta', detalle: 'Informativa: no suma ni sale en la tabla de hallazgos.' },
]

// Opción que se elige con un clic (tipo).
function Opcion({ activa, onClick, titulo, detalle, peligro, children }) {
  return (
    <button type="button" onClick={onClick}
      className={`text-left rounded-lg border px-3 py-2.5 transition-colors ${activa
        ? peligro ? 'border-red-300 bg-red-50 ring-1 ring-red-200' : 'border-primary-300 bg-primary-50 ring-1 ring-primary-200'
        : 'border-gray-200 bg-white hover:bg-gray-50'}`}>
      <span className="flex items-center gap-2">
        {children}
        <span className={`text-sm font-medium ${activa ? (peligro ? 'text-red-700' : 'text-primary-700') : 'text-gray-700'}`}>{titulo}</span>
      </span>
      <span className="mt-0.5 block text-[11px] leading-snug text-gray-400">{detalle}</span>
    </button>
  )
}

// Interruptor tipo switch con su explicación.
function Interruptor({ activo, onChange, titulo, detalle, deshabilitado }) {
  return (
    <button type="button" onClick={() => !deshabilitado && onChange(!activo)} disabled={deshabilitado}
      className={`flex w-full items-start gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors ${
        activo ? 'border-primary-300 bg-primary-50/60' : 'border-gray-200 bg-white hover:bg-gray-50'} disabled:opacity-50`}>
      <span className={`mt-0.5 relative inline-flex h-5 w-9 flex-shrink-0 rounded-full transition-colors ${activo ? 'bg-primary-600' : 'bg-gray-300'}`}>
        <span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-transform ${activo ? 'translate-x-4' : 'translate-x-0.5'}`} />
      </span>
      <span>
        <span className="block text-sm text-gray-700">{titulo}</span>
        <span className="block text-[11px] text-gray-400">{detalle}</span>
      </span>
    </button>
  )
}

function ModalHallazgo({ inicial, onClose, onGuardado }) {
  const idCampos = useId()
  const [f, setF] = useState(inicial)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const nuevo = !inicial.id
  const set = (k, v) => setF(x => ({ ...x, [k]: v }))

  const guardar = async (e) => {
    e.preventDefault()
    if (!f.codigo.trim()) return setError('Escribe el código, por ejemplo LEG-25 o POL-01.')
    if (!f.descripcion.trim()) return setError('Escribe la descripción del hallazgo.')
    if (f.tipo === 'PUNTOS' && (f.puntos === '' || f.puntos < 0 || f.puntos > 100)) return setError('Los puntos van de 0 a 100.')
    setGuardando(true)
    setError(null)
    try {
      const datos = { ...f, codigo: f.codigo.trim().toUpperCase(), descripcion: f.descripcion.trim(),
        puntos: f.tipo === 'PUNTOS' ? Number(f.puntos) : null }
      const r = nuevo ? await hallazgosService.crear(datos) : await hallazgosService.actualizar(f.id, datos)
      onGuardado(r, nuevo)
    } catch (err) {
      const d = err.response?.data
      setError(d?.mensaje ?? (d?.errores ? Object.values(d.errores).join(' ') : 'No se pudo guardar el hallazgo.'))
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={nuevo ? 'Nuevo hallazgo' : `Editar hallazgo — ${inicial.codigo}`} subtitulo="Catálogo de hallazgos" onClose={onClose} ancho="max-w-2xl">
      <form onSubmit={guardar} className="px-6 py-5 space-y-5">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Hallazgo</p>
          <div className="grid gap-4 sm:grid-cols-[9rem_minmax(0,1fr)]">
            <div>
              <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Código <span className="text-red-500">*</span></label>
              <input id={`${idCampos}-1`} value={f.codigo} onChange={e => set('codigo', e.target.value.toUpperCase())} disabled={!nuevo} autoFocus={nuevo}
                placeholder="LEG-25" maxLength={10} className={`${inputCls} font-semibold`} />
              <p className="mt-1 text-[11px] text-gray-400">{nuevo ? 'Letras, guion y número.' : 'No se puede cambiar.'}</p>
            </div>
            <div>
              <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Descripción <span className="text-red-500">*</span></label>
              <textarea id={`${idCampos}-2`} value={f.descripcion} onChange={e => set('descripcion', e.target.value)} rows={3} maxLength={500} spellCheck lang="es"
                placeholder="Ej: Medida correctiva registrada (RNMC), en cualquier estado" className={`${inputCls} resize-none`} />
              <p className="mt-0.5 text-right text-[11px] text-gray-400 tabular-nums">{f.descripcion.length} / 500</p>
            </div>
          </div>
        </div>

        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Tipo</p>
          <div className="grid gap-2 sm:grid-cols-3">
            {TIPOS.map(t => (
              <Opcion key={t.valor} activa={f.tipo === t.valor} onClick={() => set('tipo', t.valor)}
                titulo={t.etiqueta} detalle={t.detalle} peligro={t.valor === 'CRITICO'} />
            ))}
          </div>
          {f.tipo === 'PUNTOS' && (
            <div className="flex items-center gap-3">
              <label htmlFor={`${idCampos}-3`} className="text-xs font-medium text-gray-600">Puntos</label>
              <input id={`${idCampos}-3`} type="number" min={0} max={100} value={f.puntos} onChange={e => set('puntos', e.target.value === '' ? '' : Number(e.target.value))}
                className={`${inputCls} w-28 text-right tabular-nums`} />
              <span className="text-xs text-gray-400">de 0 a 100</span>
            </div>
          )}
        </div>

        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Reglas</p>
          <div className="grid gap-2 sm:grid-cols-3">
            <Interruptor activo={f.acumula} onChange={v => set('acumula', v)} deshabilitado={f.tipo === 'ALERTA'}
              titulo="Acumula por fila" detalle="Suma cada vez que aparece (ej. cada proceso)." />
            <Interruptor activo={f.soloCondicion} onChange={v => set('soloCondicion', v)} deshabilitado={f.tipo === 'ALERTA'}
              titulo="Solo con condición" detalle="Solo suma si el cargo implica conducción." />
            <Interruptor activo={f.activo} onChange={v => set('activo', v)}
              titulo="Activo" detalle="Inactivo: no sale en las listas ni suma." />
          </div>
        </div>

        <div className="flex justify-end gap-3 border-t border-gray-100 pt-4">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 transition-colors">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Guardando...' : nuevo ? 'Crear hallazgo' : 'Guardar cambios'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default function CatalogoHallazgos() {
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(null)
  const [filtro, setFiltro] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [modal, setModal] = useState(null)
  const [borrar, setBorrar] = useState(null)
  const [toast, setToast] = useState(null)
  const [cargandoEjemplo, setCargandoEjemplo] = useState(false)
  const navigate = useNavigate()

  const cargar = () => hallazgosService.listar().then(setLista).catch(() => setError('No se pudo cargar el catálogo de hallazgos.'))
  useEffect(() => { cargar() }, [])

  const visibles = useMemo(() => (lista ?? []).filter(h =>
    (!filtro || FILTRO[filtro].cumple(h)) &&
    (!busqueda || `${h.codigo} ${h.descripcion}`.toLowerCase().includes(busqueda.toLowerCase()))), [lista, filtro, busqueda])
  const pag = usePaginacion(visibles, `${filtro}|${busqueda}`)
  const conteo = useMemo(() => {
    const m = {}
    FILTROS.forEach(f => { m[f.value] = (lista ?? []).filter(f.cumple).length })
    return m
  }, [lista])

  const guardado = (r, nuevo) => {
    setLista(l => (nuevo ? [...l, r] : l.map(x => (x.id === r.id ? r : x))))
    setModal(null)
    setToast({ mensaje: `Hallazgo ${r.codigo} ${nuevo ? 'creado' : 'actualizado'} correctamente`, tipo: 'exito' })
  }

  const cambiarEstado = async (h) => {
    try {
      const r = await hallazgosService.actualizar(h.id, { ...h, activo: !h.activo })
      setLista(l => l.map(x => (x.id === r.id ? r : x)))
      setToast({ mensaje: r.activo ? 'Hallazgo activado correctamente' : 'Hallazgo desactivado correctamente', tipo: 'exito' })
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al cambiar el estado del hallazgo.')
    }
  }

  const eliminar = async () => {
    try {
      await hallazgosService.eliminar(borrar.id)
      setLista(l => l.filter(x => x.id !== borrar.id))
      setToast({ mensaje: `Hallazgo ${borrar.codigo} eliminado`, tipo: 'exito' })
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo eliminar.', tipo: 'error' })
    } finally {
      setBorrar(null)
    }
  }

  // Estudio de prueba con el caso del PDF de ejemplo (Laura Ejemplo Pérez, 60,0 puntos, MEDIO).
  const cargarEjemplo = async () => {
    setCargandoEjemplo(true)
    try {
      const r = await hallazgosService.cargarEjemploAntecedentes()
      navigate(`/admin/formulario/${r.idServicioSubproceso}`)
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo cargar el estudio de ejemplo.', tipo: 'error' })
      setCargandoEjemplo(false)
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Catálogo de hallazgos</h2>
          <p className="text-sm text-gray-500 mt-0.5">Puntos, tipo y reglas de cada hallazgo — la base de la calificación. Qué hallazgos ofrece cada formulario se define en su plantilla.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button onClick={cargarEjemplo} disabled={cargandoEjemplo}
            className="inline-flex items-center gap-2 border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 text-sm font-medium px-4 py-2 rounded-lg transition-colors disabled:opacity-60">
            {cargandoEjemplo ? 'Cargando...' : 'Ver estudio de ejemplo'}
          </button>
          <button onClick={() => setModal(vacio())}
            className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
            <span className="text-base leading-none">+</span> Nuevo hallazgo
          </button>
        </div>
      </div>

      {error && <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      {/* Tarjetas de filtro por tipo — clic en la activa vuelve a todos */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
        {FILTROS.map(r => {
          const activo = filtro === r.value
          return (
            <button key={r.value} type="button" onClick={() => setFiltro(p => (p === r.value ? '' : r.value))}
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
        <input aria-label="Buscar por código o descripción…" type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)} placeholder="Buscar por código o descripción…"
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        <span className="text-xs text-gray-400">
          {visibles.length} resultado{visibles.length !== 1 ? 's' : ''}
          {filtro && <>{' · '}<span className="font-medium text-gray-600">{FILTRO[filtro].label}</span>{' — '}
            <button onClick={() => setFiltro('')} className="text-primary-600 hover:underline">ver todos</button></>}
        </span>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {!lista ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : visibles.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">No hay hallazgos que coincidan con el filtro.</div>
        ) : (
          <div className="overflow-x-auto scrollbar-thin">
            <table className="table-stack w-full divide-y divide-gray-100 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <Th>Código</Th><Th>Descripción</Th><Th cls="text-right">Puntos</Th><Th cls="hidden md:table-cell">Uso</Th>
                  <Th cls="hidden lg:table-cell">Reglas</Th><Th>Estado</Th><Th>···</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {pag.visibles.map(h => (
                  <tr key={h.id} className="hover:bg-gray-50/70 transition-colors">
                    <td className="px-3 py-3 font-semibold text-gray-900 whitespace-nowrap">{h.codigo}</td>
                    <td className="px-3 py-3 text-gray-700 min-w-[12rem]">
                      {h.descripcion}
                    </td>
                    <td className="px-3 py-3 text-right whitespace-nowrap"><Valor h={h} /></td>
                    <td className="hidden md:table-cell px-3 py-3 whitespace-nowrap">
                      {h.enUso
                        ? <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-primary-50 text-primary-700">En una plantilla</span>
                        : <span className="text-xs text-gray-400">Sin usar</span>}
                    </td>
                    <td className="hidden lg:table-cell px-3 py-3 text-xs text-gray-500">
                      {[h.acumula && 'Acumula por fila', h.soloCondicion && 'Solo con conducción'].filter(Boolean).join(' · ') || '—'}
                    </td>
                    <td className="px-3 py-3"><Badge activo={h.activo} /></td>
                    <td className="px-3 py-3 text-center">
                      <MenuAcciones opciones={[
                        { label: 'Editar', icono: <IconoLapiz />, onClick: () => setModal({ ...h, puntos: h.puntos ?? '' }) },
                        { label: h.activo ? 'Desactivar' : 'Activar', icono: <IconoPower />, destacado: h.activo ? 'text-red-600' : 'text-green-700', onClick: () => cambiarEstado(h) },
                        { label: 'Eliminar', icono: <IconoBasura />, destacado: 'text-red-600', disabled: h.enUso,
                          title: h.enUso ? 'Está en uso en una plantilla: desactívalo' : undefined, onClick: () => setBorrar(h) },
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

      {modal && <ModalHallazgo inicial={modal} onClose={() => setModal(null)} onGuardado={guardado} />}
      {borrar && (
        <ConfirmModal titulo={`¿Eliminar ${borrar.codigo}?`} danger confirmLabel="Eliminar" onConfirmar={eliminar} onCancelar={() => setBorrar(null)}>
          <p className="text-sm text-gray-600">{borrar.descripcion}</p>
        </ConfirmModal>
      )}
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

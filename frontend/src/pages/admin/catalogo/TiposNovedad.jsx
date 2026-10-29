import { useId, useState, useEffect, useCallback, useMemo } from 'react'
import tiposNovedadService from '../../../services/tiposNovedadService'
import Toast from '../../../components/Toast'
import { Modal, ConfirmModal } from '../../../components/ui/Modal'
import MenuAcciones from '../../../components/ui/MenuAcciones'
import {
  EncabezadoPagina, Indicador, AlertaError, EsqueletoPagina, EstadoVacio, botonPrimario, botonSecundario,
} from '../../../components/admin/PaginaAdmin'
import { PALETA_NOVEDAD, colorNovedad, ROLES_NOVEDAD } from '../../../utils/paletaNovedades'

// Plantilla de novedades: las opciones de "¿Qué pasó?" que ven analista/poligrafista/visitador al
// reportar una novedad de un subproceso, clasificadas por categoría. Lo que ya se usó no se
// borra — se desactiva, para que las novedades viejas conserven su tipo.

const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none'
const ETIQUETA_ROL = Object.fromEntries(ROLES_NOVEDAD.map(r => [r.valor, r.etiqueta]))

const ICONO_LAPIZ = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z" />
  </svg>
)
const ICONO_BASURA = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
  </svg>
)
const ICONO_OJO = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
  </svg>
)

function ModalCategoria({ categoria, onClose, onGuardado }) {
  const idCampos = useId()
  const esEdicion = !!categoria
  const [nombre, setNombre] = useState(categoria?.nombre ?? '')
  const [color, setColor] = useState(categoria?.color ?? 'gray')
  const [orden, setOrden] = useState(categoria?.orden ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const guardar = async (e) => {
    e.preventDefault()
    if (!nombre.trim()) { setError('Escribe el nombre de la categoría.'); return }
    setGuardando(true); setError(null)
    try {
      const payload = { nombre: nombre.trim(), color, orden: orden === '' ? null : Number(orden) }
      const data = esEdicion
        ? await tiposNovedadService.actualizarCategoria(categoria.idCategoriaNovedad, payload)
        : await tiposNovedadService.crearCategoria(payload)
      onGuardado(data, esEdicion)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo guardar la categoría.')
    } finally { setGuardando(false) }
  }

  return (
    <Modal titulo={esEdicion ? 'Editar categoría' : 'Nueva categoría'} onClose={onClose} ancho="max-w-md">
      <form onSubmit={guardar} className="px-6 py-5 space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
        <div>
          <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
          <input id={`${idCampos}-1`} value={nombre} onChange={e => setNombre(e.target.value)} autoFocus maxLength={100}
            placeholder="Ej: Asistencia del candidato" className={inputCls} />
          <p className="text-[11px] text-gray-400 mt-1">Es el título del grupo en la lista "¿Qué pasó?".</p>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1.5">Color</label>
          <div className="flex flex-wrap gap-2">
            {Object.entries(PALETA_NOVEDAD).map(([clave, c]) => (
              <button key={clave} type="button" onClick={() => setColor(clave)} title={c.nombre}
                className={`w-8 h-8 rounded-full flex items-center justify-center ring-offset-2 transition ${
                  color === clave ? 'ring-2 ring-gray-800' : 'hover:ring-2 hover:ring-gray-300'}`}>
                <span className={`w-6 h-6 rounded-full ${c.punto}`} />
              </button>
            ))}
          </div>
        </div>
        <div>
          <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Orden</label>
          <input id={`${idCampos}-2`} type="number" min={0} value={orden} onChange={e => setOrden(e.target.value)}
            placeholder="Al final" className={`${inputCls} max-w-[120px]`} />
        </div>
        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} disabled={guardando}
            className="px-4 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="px-4 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50">
            {guardando ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ModalTipo({ tipo, categorias, idCategoriaInicial, onClose, onGuardado }) {
  const idCampos = useId()
  const esEdicion = !!tipo
  const [idCategoria, setIdCategoria] = useState(tipo?.idCategoriaNovedad ?? idCategoriaInicial ?? categorias[0]?.idCategoriaNovedad ?? '')
  const [nombre, setNombre] = useState(tipo?.nombre ?? '')
  const [roles, setRoles] = useState(new Set(tipo?.roles ?? []))
  const [textoAyuda, setTextoAyuda] = useState(tipo?.textoAyuda ?? '')
  const [visibleCliente, setVisibleCliente] = useState(!!tipo?.visibleCliente)
  const [pausaReloj, setPausaReloj] = useState(!!tipo?.pausaReloj)
  const [imputableA, setImputableA] = useState(tipo?.imputableA ?? 'POLYGRAPH')
  const [orden, setOrden] = useState(tipo?.orden ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const alternarRol = (r) => setRoles(prev => {
    const s = new Set(prev)
    s.has(r) ? s.delete(r) : s.add(r)
    return s
  })

  const guardar = async (e) => {
    e.preventDefault()
    if (!nombre.trim()) { setError('Escribe qué pasó (el nombre del tipo).'); return }
    if (!idCategoria) { setError('Elige la categoría.'); return }
    if (roles.size === 0) { setError('Elige al menos un rol al que le aparezca.'); return }
    setGuardando(true); setError(null)
    try {
      const payload = {
        idCategoriaNovedad: Number(idCategoria), nombre: nombre.trim(), roles: [...roles],
        textoAyuda: textoAyuda.trim() || null, orden: orden === '' ? null : Number(orden), visibleCliente, pausaReloj, imputableA,
      }
      const data = esEdicion
        ? await tiposNovedadService.actualizarTipo(tipo.idTipoNovedad, payload)
        : await tiposNovedadService.crearTipo(payload)
      onGuardado(data, esEdicion)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo guardar el tipo de novedad.')
    } finally { setGuardando(false) }
  }

  return (
    <Modal titulo={esEdicion ? 'Editar tipo de novedad' : 'Nuevo tipo de novedad'}
      subtitulo="Una opción de la lista “¿Qué pasó?”" onClose={onClose} ancho="max-w-lg">
      <form onSubmit={guardar} className="px-6 py-5 space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
        <div>
          <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">¿Qué pasó? <span className="text-red-500">*</span></label>
          <input id={`${idCampos}-1`} value={nombre} onChange={e => setNombre(e.target.value)} autoFocus maxLength={150}
            placeholder="Ej: El candidato llegó en estado de embriaguez" className={inputCls} />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-[1fr_120px] gap-3">
          <div>
            <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Categoría <span className="text-red-500">*</span></label>
            <select id={`${idCampos}-2`} value={idCategoria} onChange={e => setIdCategoria(e.target.value)} className={inputCls}>
              {categorias.map(c => (
                <option key={c.idCategoriaNovedad} value={c.idCategoriaNovedad}>
                  {c.nombre}{c.activo ? '' : ' (inactiva)'}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor={`${idCampos}-3`} className="block text-xs font-medium text-gray-600 mb-1">Orden</label>
            <input id={`${idCampos}-3`} type="number" min={0} value={orden} onChange={e => setOrden(e.target.value)} placeholder="Al final" className={inputCls} />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1.5">¿A quién le aparece? <span className="text-red-500">*</span></label>
          <div className="flex flex-wrap gap-2">
            {ROLES_NOVEDAD.map(r => {
              const activo = roles.has(r.valor)
              return (
                <button key={r.valor} type="button" onClick={() => alternarRol(r.valor)}
                  className={`px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors ${
                    activo ? 'bg-primary-600 border-primary-600 text-white' : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
                  {activo ? '✓ ' : ''}{r.etiqueta}
                </button>
              )
            })}
          </div>
          <p className="text-[11px] text-gray-400 mt-1">Solo le sale a quien ejecuta subprocesos de ese rol.</p>
        </div>
        <div>
          <label htmlFor={`${idCampos}-4`} className="block text-xs font-medium text-gray-600 mb-1">Texto de ayuda (ejemplo para el campo "Detalle")</label>
          <textarea id={`${idCampos}-4`} value={textoAyuda} onChange={e => setTextoAyuda(e.target.value)} rows={2} maxLength={300}
            placeholder="Ej: Llegó con aliento alcohólico; no se realizó la prueba." className={inputCls} />
          <p className="text-[11px] text-gray-400 mt-0.5 text-right">{textoAyuda.length}/300</p>
        </div>
        <label className="flex items-start gap-3 rounded-lg border border-gray-200 px-3 py-2.5 cursor-pointer hover:bg-gray-50">
          <input type="checkbox" checked={visibleCliente} onChange={e => setVisibleCliente(e.target.checked)}
            className="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
          <span>
            <span className="block text-sm font-medium text-gray-800">Visible para el cliente</span>
            <span className="block text-[11px] text-gray-400">El cliente la ve en el detalle de su solicitud: el tipo, el estado y la respuesta al resolverla. Nunca ve lo que escribió el ejecutor.</span>
          </span>
        </label>
        <div className="rounded-lg border border-gray-200 px-3 py-2.5 space-y-2.5">
          <label className="flex items-start gap-3 cursor-pointer">
            <input type="checkbox" checked={pausaReloj} onChange={e => setPausaReloj(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
            <span>
              <span className="block text-sm font-medium text-gray-800">Detiene el reloj de la solicitud</span>
              <span className="block text-[11px] text-gray-400">Mientras la novedad esté abierta, los días hábiles no cuentan para el plazo de entrega.</span>
            </span>
          </label>
          <div>
            <label htmlFor={`${idCampos}-imputable`} className="block text-xs font-medium text-gray-600 mb-1">¿De quién es la causa?</label>
            <select id={`${idCampos}-imputable`} value={imputableA} onChange={e => setImputableA(e.target.value)} className={inputCls}>
              <option value="POLYGRAPH">Polygraph</option>
              <option value="CANDIDATO">El candidato</option>
              <option value="CLIENTE">El cliente</option>
            </select>
          </div>
        </div>
        {tipo?.clave && (
          <p className="text-[11px] text-gray-400">Código interno: <span className="font-mono">{tipo.clave}</span> (no cambia al renombrar)</p>
        )}
        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} disabled={guardando}
            className="px-4 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="px-4 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50">
            {guardando ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

// Cómo ve la lista "¿Qué pasó?" quien reporta, según su rol — mismo filtro que el backend
// (solo tipos y categorías activos, que apliquen al rol).
function ModalVistaPrevia({ categorias, tipos, onClose }) {
  const [rol, setRol] = useState('POLIGRAFISTA')
  const grupos = categorias
    .filter(c => c.activo)
    .map(c => ({ c, tipos: tipos.filter(t => t.activo && t.idCategoriaNovedad === c.idCategoriaNovedad && t.roles.includes(rol)) }))
    .filter(g => g.tipos.length > 0)
  const total = grupos.reduce((acc, g) => acc + g.tipos.length, 0)
  return (
    <Modal titulo="Vista previa" subtitulo='Así le sale la lista "¿Qué pasó?" a cada rol' onClose={onClose} ancho="max-w-md">
      <div className="px-6 py-5">
        <div className="grid grid-cols-3 gap-1 rounded-lg bg-gray-100 p-1 text-xs mb-4">
          {ROLES_NOVEDAD.map(r => (
            <button key={r.valor} type="button" onClick={() => setRol(r.valor)}
              className={`py-1.5 rounded-md font-medium ${rol === r.valor ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>
              {r.etiqueta}
            </button>
          ))}
        </div>
        <div className="rounded-xl border border-gray-200 overflow-hidden">
          <div className="px-3 py-2 bg-gray-50 border-b border-gray-200 text-xs text-gray-500">
            Selecciona el tipo de novedad… <span className="float-right text-gray-400">{total} opciones</span>
          </div>
          {grupos.length === 0 ? (
            <p className="text-xs text-gray-400 px-3 py-6 text-center">Este rol no tiene ninguna opción disponible.</p>
          ) : (
            <div className="max-h-96 overflow-y-auto py-1">
              {grupos.map(({ c, tipos: ts }) => (
                <div key={c.idCategoriaNovedad} className="py-1">
                  <p className="flex items-center gap-1.5 px-3 py-1 text-[11px] font-semibold text-gray-500">
                    <span className={`w-2 h-2 rounded-full ${colorNovedad(c.color).punto}`} />{c.nombre}
                  </p>
                  {ts.map(t => (
                    <p key={t.idTipoNovedad} className="px-3 pl-7 py-1.5 text-sm text-gray-700 hover:bg-gray-50">{t.nombre}</p>
                  ))}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  )
}


const ICONO_CARPETA = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" />
  </svg>
)
const ICONO_LISTA = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM3.75 12h.007v.008H3.75V12zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm-.375 5.25h.007v.008H3.75v-.008zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z" />
  </svg>
)
const ICONO_PAUSA = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M14.25 9v6m-4.5 0V9M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
)
const ICONO_BANDERA = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M3 3v1.5M3 21v-6m0 0l2.77-.693a9 9 0 016.208.682l.108.054a9 9 0 006.086.71l3.114-.732a48.524 48.524 0 01-.005-10.499l-3.11.732a9 9 0 01-6.085-.711l-.108-.054a9 9 0 00-6.208-.682L3 4.5M3 15V4.5" />
  </svg>
)

// Punto de color por rol en las etiquetas de "a quién le aparece".
const PUNTO_ROL = { ANALISTA_INTERNO: 'bg-blue-400', POLIGRAFISTA: 'bg-violet-400', VISITADOR: 'bg-emerald-400' }

function FilaTipo({ t, onEditar, onEstado, onEliminar }) {
  return (
    <li className={`group px-4 py-3 flex items-start gap-3 hover:bg-gray-50/70 ${t.activo ? '' : 'bg-gray-50/60'}`}>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2 flex-wrap">
          <p className={`text-sm font-medium ${t.activo ? 'text-gray-900' : 'text-gray-400'}`}>{t.nombre}</p>
          {!t.activo && <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-gray-200 text-gray-500">Inactivo</span>}
          {t.pausaReloj && <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 ring-1 ring-slate-200" title={`Detiene el reloj de la solicitud · causa: ${t.imputableA === 'CANDIDATO' ? 'el candidato' : t.imputableA === 'CLIENTE' ? 'el cliente' : 'Polygraph'}`}>Pausa el reloj</span>}
          {t.visibleCliente && <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-sky-50 text-sky-700 ring-1 ring-sky-200" title="El cliente la ve en el detalle de su solicitud">Visible al cliente</span>}
        </div>
        {t.textoAyuda && (
          <p className="text-xs text-gray-400 mt-0.5 line-clamp-1" title={t.textoAyuda}>
            <span className="text-gray-300">Ayuda:</span> {t.textoAyuda}
          </p>
        )}
        <div className="flex flex-wrap items-center gap-1.5 mt-2">
          {t.roles.map(r => (
            <span key={r} className="inline-flex items-center gap-1 text-[10px] font-medium px-2 py-0.5 rounded-full bg-white border border-gray-200 text-gray-600">
              <span className={`w-1.5 h-1.5 rounded-full ${PUNTO_ROL[r] ?? 'bg-gray-300'}`} />{ETIQUETA_ROL[r] ?? r}
            </span>
          ))}
        </div>
      </div>
      <div className="flex items-center gap-2 flex-shrink-0">
        <span className={`text-[10px] font-medium px-2 py-0.5 rounded-full ${t.enUso > 0 ? 'bg-primary-50 text-primary-700' : 'bg-gray-100 text-gray-400'}`}
          title={t.enUso > 0 ? `Usado en ${t.enUso} novedad${t.enUso === 1 ? '' : 'es'}` : 'Todavía no se ha usado'}>
          {t.enUso > 0 ? `${t.enUso} uso${t.enUso === 1 ? '' : 's'}` : 'Sin usar'}
        </span>
        <MenuAcciones opciones={[
          { label: 'Editar', icono: ICONO_LAPIZ, onClick: onEditar },
          { label: t.activo ? 'Desactivar' : 'Activar', icono: ICONO_OJO, onClick: onEstado },
          {
            label: 'Eliminar', icono: ICONO_BASURA, destacado: 'text-red-600', disabled: t.enUso > 0,
            title: t.enUso > 0 ? 'Ya se usó en novedades: desactívalo en vez de eliminarlo.' : undefined,
            onClick: onEliminar,
          },
        ]} />
      </div>
    </li>
  )
}

function TarjetaCategoria({ c, tipos, filtrando, onEditar, onEstado, onEliminar, onAgregarTipo, accionesTipo }) {
  const color = colorNovedad(c.color)
  const activos = tipos.filter(t => t.activo).length
  return (
    <section className={`bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden ${c.activo ? '' : 'opacity-70'}`}>
      <header className="flex items-center gap-3 px-4 py-3 border-b border-gray-100">
        <span className={`flex-shrink-0 w-3 h-3 rounded-full ${color.punto}`} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-sm font-semibold text-gray-900">{c.nombre}</h3>
            {!c.activo && <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-gray-200 text-gray-500">Inactiva — no sale en la lista</span>}
          </div>
          <p className="text-[11px] text-gray-400">{activos} activo{activos === 1 ? '' : 's'}{tipos.length > activos ? ` · ${tipos.length - activos} inactivo${tipos.length - activos === 1 ? '' : 's'}` : ''}</p>
        </div>
        <button type="button" onClick={onAgregarTipo}
          className="text-xs font-medium text-primary-700 hover:text-primary-800 border border-primary-100 hover:bg-primary-50 px-2.5 py-1.5 rounded-lg">
          + Tipo
        </button>
        <MenuAcciones opciones={[
          { label: 'Editar categoría', icono: ICONO_LAPIZ, onClick: onEditar },
          { label: c.activo ? 'Desactivar categoría' : 'Activar categoría', icono: ICONO_OJO, onClick: onEstado },
          {
            label: 'Eliminar categoría', icono: ICONO_BASURA, destacado: 'text-red-600', disabled: c.totalTipos > 0,
            title: c.totalTipos > 0 ? 'Tiene tipos de novedad: muévelos o desactívala.' : undefined,
            onClick: onEliminar,
          },
        ]} />
      </header>
      {tipos.length === 0 ? (
        <div className="px-4 py-6 text-center">
          <p className="text-xs text-gray-400">{filtrando ? 'Ningún tipo coincide con el filtro.' : 'Esta categoría todavía no tiene tipos.'}</p>
          {!filtrando && (
            <button type="button" onClick={onAgregarTipo} className="mt-2 text-xs font-medium text-primary-600 hover:text-primary-800">
              + Agregar el primero
            </button>
          )}
        </div>
      ) : (
        <ul className="divide-y divide-gray-100">
          {tipos.map(t => <FilaTipo key={t.idTipoNovedad} t={t} {...accionesTipo(t)} />)}
        </ul>
      )}
    </section>
  )
}

export default function TiposNovedad() {
  const [categorias, setCategorias] = useState([])
  const [tipos, setTipos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [filtroRol, setFiltroRol] = useState('todos')
  const [verInactivos, setVerInactivos] = useState(false)
  const [vistaPrevia, setVistaPrevia] = useState(false)
  const [modalCategoria, setModalCategoria] = useState(null) // null | 'nueva' | categoria
  const [modalTipo, setModalTipo] = useState(null)           // null | { tipo?, idCategoria? }
  const [aEliminar, setAEliminar] = useState(null)           // { clase: 'categoria'|'tipo', item }
  const [eliminando, setEliminando] = useState(false)

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      const [cs, ts] = await Promise.all([tiposNovedadService.listarCategorias(), tiposNovedadService.listarTipos()])
      setCategorias(cs); setTipos(ts)
    } catch { setError('No se pudo cargar el catálogo de novedades.') }
    finally { setCargando(false) }
  }, [])
  useEffect(() => { cargar() }, [cargar])

  const aviso = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })
  const filtrando = busqueda.trim() !== '' || filtroRol !== 'todos'

  const tiposVisibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    return tipos
      .filter(t => verInactivos || t.activo)
      .filter(t => filtroRol === 'todos' || t.roles.includes(filtroRol))
      .filter(t => !q || t.nombre.toLowerCase().includes(q) || (t.textoAyuda ?? '').toLowerCase().includes(q))
  }, [tipos, busqueda, filtroRol, verInactivos])

  const categoriasVisibles = categorias
    .filter(c => verInactivos || c.activo)
    .map(c => ({ c, tipos: tiposVisibles.filter(t => t.idCategoriaNovedad === c.idCategoriaNovedad) }))
    .filter(g => !filtrando || g.tipos.length > 0)

  // Recarga todo tras cualquier cambio: los contadores (totalTipos, enUso) y el orden dependen
  // del backend, así no hay que replicar esa lógica aquí.
  const trasGuardar = async (mensaje) => {
    setModalCategoria(null); setModalTipo(null)
    aviso(mensaje)
    await cargar()
  }

  const cambiarEstado = async (clase, item) => {
    try {
      if (clase === 'categoria') await tiposNovedadService.cambiarEstadoCategoria(item.idCategoriaNovedad, !item.activo)
      else await tiposNovedadService.cambiarEstadoTipo(item.idTipoNovedad, !item.activo)
      aviso(`"${item.nombre}" ${item.activo ? 'desactivado' : 'activado'}`)
      await cargar()
    } catch (err) { setError(err.response?.data?.mensaje ?? 'No se pudo cambiar el estado.') }
  }

  const eliminar = async () => {
    if (!aEliminar) return
    setEliminando(true)
    try {
      if (aEliminar.clase === 'categoria') await tiposNovedadService.eliminarCategoria(aEliminar.item.idCategoriaNovedad)
      else await tiposNovedadService.eliminarTipo(aEliminar.item.idTipoNovedad)
      aviso(`"${aEliminar.item.nombre}" eliminado`)
      await cargar()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo eliminar.')
    } finally { setEliminando(false); setAEliminar(null) }
  }

  const categoriasActivas = categorias.filter(c => c.activo).length
  const tiposActivos = tipos.filter(t => t.activo).length
  const tiposInactivos = tipos.length - tiposActivos
  const totalUsos = tipos.reduce((acc, t) => acc + (t.enUso ?? 0), 0)

  return (
    <div className="max-w-6xl">
      <EncabezadoPagina
        seccion="Novedades"
        titulo="Tipos de novedad"
        icono={<svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M3 3v1.5M3 21v-6m0 0l2.77-.693a9 9 0 016.208.682l.108.054a9 9 0 006.086.71l3.114-.732a48.524 48.524 0 01-.005-10.499l-3.11.732a9 9 0 01-6.085-.711l-.108-.054a9 9 0 00-6.208-.682L3 4.5M3 15V4.5" /></svg>}
        descripcion={'Las opciones de "¿Qué pasó?" que ven analista, poligrafista y visitador al reportar una novedad. Lo que ya se usó no se borra: se desactiva y deja de aparecer en la lista.'}
        acciones={<>
          <button onClick={() => setVistaPrevia(true)} className={botonSecundario}>{ICONO_OJO} Vista previa</button>
          <button onClick={() => setModalCategoria('nueva')} className={botonSecundario}>+ Categoría</button>
          <button onClick={() => setModalTipo({})} disabled={categorias.length === 0} className={botonPrimario}>+ Tipo de novedad</button>
        </>}
      />

      <AlertaError mensaje={error} onCerrar={() => setError(null)} />

      {cargando ? (
        <EsqueletoPagina indicadores={4} bloques={2} />
      ) : (
        <>
          {/* Indicadores */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
            <Indicador valor={categoriasActivas} etiqueta="Categorías activas" detalle={categorias.length > categoriasActivas ? `${categorias.length - categoriasActivas} inactivas` : 'Todas activas'} icono={ICONO_CARPETA} />
            <Indicador valor={tiposActivos} etiqueta="Tipos activos" detalle="Aparecen al reportar" icono={ICONO_LISTA} tono="verde" />
            <Indicador valor={tiposInactivos} etiqueta="Tipos inactivos" detalle="Ocultos, se conservan" icono={ICONO_PAUSA} tono="gris" />
            <Indicador valor={totalUsos} etiqueta="Novedades registradas" detalle="Con estos tipos" icono={ICONO_BANDERA} tono="ambar" />
          </div>

          {/* Filtros */}
          <div className="bg-white border border-gray-200 rounded-2xl shadow-sm px-4 py-3 mb-5 flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[220px]">
              <svg className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" /></svg>
              <input aria-label="Buscar un tipo de novedad…" type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)} placeholder="Buscar un tipo de novedad…"
                className="w-full border border-gray-200 rounded-lg pl-9 pr-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
            <div className="inline-flex rounded-lg bg-gray-100 p-0.5 text-xs">
              {[{ valor: 'todos', etiqueta: 'Todos los roles' }, ...ROLES_NOVEDAD].map(r => (
                <button key={r.valor} type="button" onClick={() => setFiltroRol(r.valor)}
                  className={`px-2.5 py-1.5 rounded-md font-medium ${filtroRol === r.valor ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>
                  {r.etiqueta}
                </button>
              ))}
            </div>
            <label className="flex items-center gap-1.5 text-xs text-gray-600">
              <input type="checkbox" checked={verInactivos} onChange={e => setVerInactivos(e.target.checked)} className="rounded border-gray-300 text-primary-600" />
              Mostrar inactivos
            </label>
          </div>

          {/* Categorías con sus tipos */}
          {categorias.length === 0 ? (
            <div className="bg-white border border-dashed border-gray-300 rounded-2xl">
              <EstadoVacio icono={ICONO_CARPETA} titulo="Todavía no hay categorías"
                descripcion={'Crea una categoría (ej. "Asistencia del candidato") y luego agrégale tipos.'}
                accion={<button onClick={() => setModalCategoria('nueva')} className={botonPrimario}>+ Crear categoría</button>} />
            </div>
          ) : categoriasVisibles.length === 0 ? (
            <div className="bg-white border border-gray-200 rounded-2xl">
              <EstadoVacio icono={ICONO_LISTA} titulo="Ningún tipo coincide con el filtro"
                descripcion="Prueba con otra palabra, cambia el rol o activa «Mostrar inactivos»." />
            </div>
          ) : (
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4 items-start">
              {categoriasVisibles.map(({ c, tipos: ts }) => (
                <TarjetaCategoria key={c.idCategoriaNovedad} c={c} tipos={ts} filtrando={filtrando}
                  onEditar={() => setModalCategoria(c)}
                  onEstado={() => cambiarEstado('categoria', c)}
                  onEliminar={() => setAEliminar({ clase: 'categoria', item: c })}
                  onAgregarTipo={() => setModalTipo({ idCategoria: c.idCategoriaNovedad })}
                  accionesTipo={t => ({
                    onEditar: () => setModalTipo({ tipo: t }),
                    onEstado: () => cambiarEstado('tipo', t),
                    onEliminar: () => setAEliminar({ clase: 'tipo', item: t }),
                  })} />
              ))}
            </div>
          )}
        </>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}

      {vistaPrevia && <ModalVistaPrevia categorias={categorias} tipos={tipos} onClose={() => setVistaPrevia(false)} />}
      {modalCategoria && (
        <ModalCategoria categoria={modalCategoria === 'nueva' ? null : modalCategoria}
          onClose={() => setModalCategoria(null)}
          onGuardado={(d, edicion) => trasGuardar(edicion ? `"${d.nombre}" actualizada` : `"${d.nombre}" creada`)} />
      )}
      {modalTipo && (
        <ModalTipo tipo={modalTipo.tipo ?? null} categorias={categorias} idCategoriaInicial={modalTipo.idCategoria ?? null}
          onClose={() => setModalTipo(null)}
          onGuardado={(d, edicion) => trasGuardar(edicion ? `"${d.nombre}" actualizado` : `"${d.nombre}" agregado`)} />
      )}
      {aEliminar && (
        <ConfirmModal titulo={aEliminar.clase === 'categoria' ? 'Eliminar categoría' : 'Eliminar tipo de novedad'}
          danger cargando={eliminando} confirmLabel="Eliminar" cancelLabel="Cancelar"
          onConfirmar={eliminar} onCancelar={() => setAEliminar(null)}>
          <p className="text-sm text-gray-500">
            ¿Eliminar <span className="font-semibold text-gray-700">"{aEliminar.item.nombre}"</span>? Esta acción no se puede deshacer.
          </p>
        </ConfirmModal>
      )}
    </div>
  )
}

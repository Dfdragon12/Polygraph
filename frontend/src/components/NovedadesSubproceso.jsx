import { useId, useState, useEffect, useCallback } from 'react'
import asignacionService from '../services/asignacionService'
import { useCambios } from '../hooks/useCambios'
import { Modal } from './ui/Modal'
import { colorNovedad } from '../utils/paletaNovedades'

// Novedades de un subproceso (ej. "el candidato nunca llegó") — las reporta quien lo ejecuta
// (analista/poligrafista/visitador) y las resuelve quien lo coordina (gestor/programador). El
// backend dice qué tipos aplican a este subproceso y qué puede hacer quien consulta
// (puedeRegistrar/puedeResolver/puedeComentar/puedeReprogramar), así que este componente no
// decide permisos por su cuenta. Tipos, colores y textos de ayuda vienen del catálogo
// administrable (admin → Novedades → Tipos de novedad).

const AYUDA_GENERAL = 'Describe qué pasó, cuándo y qué intentaste hacer.'
const inputCls = 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'

const NOMBRE_ROL = {
  ANALISTA_INTERNO: 'Analista', POLIGRAFISTA: 'Poligrafista', VISITADOR: 'Visitador',
  GESTOR: 'Gestor', PROGRAMADOR: 'Programador', ADMIN_POLYGRAPH: 'Administración',
}

function formatoFecha(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' })
}

// "2026-09-24T08:00:00" -> valor para <input type="datetime-local"> ("2026-09-24T08:00").
function aInputFechaHora(iso) {
  return iso ? String(iso).slice(0, 16) : ''
}

function IconoBandera({ className = 'h-5 w-5' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 3v1.5M3 21v-6m0 0l2.77-.693a9 9 0 016.208.682l.108.054a9 9 0 006.086.71l3.114-.732a48.524 48.524 0 01-.005-10.499l-3.11.732a9 9 0 01-6.085-.711l-.108-.054a9 9 0 00-6.208-.682L3 4.5M3 15V4.5" />
    </svg>
  )
}

function IconoChevron({ abierto }) {
  return (
    <svg className={`h-4 w-4 transition-transform ${abierto ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
    </svg>
  )
}

/* ─── Reportar novedad (ventana aparte, para no amontonar la lista) ─── */
function ModalNuevaNovedad({ tipos, onRegistrar, onCerrar, enviando }) {
  const idCampos = useId()
  const [tipo, setTipo] = useState('')
  const [descripcion, setDescripcion] = useState('')

  // Agrupa las opciones por categoría, en el orden del catálogo.
  const categorias = []
  tipos.forEach(t => {
    let grupo = categorias.find(c => c.codigo === t.categoria)
    if (!grupo) { grupo = { codigo: t.categoria, etiqueta: t.categoriaEtiqueta, color: t.categoriaColor, tipos: [] }; categorias.push(grupo) }
    grupo.tipos.push(t)
  })
  const seleccionado = tipos.find(t => String(t.idTipoNovedad) === String(tipo))

  const enviar = (e) => {
    e.preventDefault()
    if (!tipo || !descripcion.trim()) return
    onRegistrar(Number(tipo), descripcion.trim())
  }

  return (
    <Modal titulo="Reportar novedad" subtitulo="Queda registrada y se le avisa a quien coordina el servicio" onClose={onCerrar} ancho="max-w-lg">
      <form onSubmit={enviar} className="px-6 py-5 space-y-5">
        <div>
          <label htmlFor={`${idCampos}-1`} className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-1.5">
            <span className="w-5 h-5 rounded-full bg-amber-100 text-amber-700 text-[11px] font-bold flex items-center justify-center">1</span>
            ¿Qué pasó?
          </label>
          <select id={`${idCampos}-1`} value={tipo} onChange={e => setTipo(e.target.value)} className={inputCls} autoFocus>
            <option value="">Selecciona el tipo de novedad…</option>
            {categorias.map(c => (
              <optgroup key={c.codigo} label={c.etiqueta}>
                {c.tipos.map(t => <option key={t.idTipoNovedad} value={t.idTipoNovedad}>{t.etiqueta}</option>)}
              </optgroup>
            ))}
          </select>
          {seleccionado && (
            <p className="mt-1.5 flex items-center gap-1.5 text-[11px] text-gray-500">
              <span className={`w-2 h-2 rounded-full ${colorNovedad(seleccionado.categoriaColor).punto}`} />
              {seleccionado.categoriaEtiqueta}
            </p>
          )}
        </div>
        <div>
          <label htmlFor={`${idCampos}-2`} className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-1.5">
            <span className="w-5 h-5 rounded-full bg-amber-100 text-amber-700 text-[11px] font-bold flex items-center justify-center">2</span>
            Cuéntanos el detalle
          </label>
          <textarea id={`${idCampos}-2`} value={descripcion} onChange={e => setDescripcion(e.target.value)} rows={4} maxLength={2000}
            placeholder={seleccionado?.textoAyuda || AYUDA_GENERAL} className={inputCls} />
          <p className="text-[10px] text-gray-400 text-right mt-0.5">{descripcion.length}/2000</p>
        </div>
        <div className="rounded-lg bg-gray-50 px-3 py-2.5 text-[11px] text-gray-500">
          Tu formulario no se bloquea: puedes seguir trabajando. Si pasa algo más, lo agregas como observación en esta misma novedad.
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onCerrar}
            className="border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 text-sm font-medium px-4 py-2 rounded-lg">
            Cancelar
          </button>
          <button type="submit" disabled={!tipo || !descripcion.trim() || enviando}
            className="bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white text-sm font-medium px-4 py-2 rounded-lg shadow-sm">
            {enviando ? 'Registrando…' : 'Registrar novedad'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/* ─── Línea de tiempo de una novedad ─── */
function EntradaSeguimiento({ punto, titulo, autor, fecha, children }) {
  return (
    <li className="relative pl-7">
      <span className={`absolute left-[3px] top-1 w-2.5 h-2.5 rounded-full ring-4 ring-gray-50 ${punto}`} />
      <p className="text-xs">
        <span className="font-semibold text-gray-700">{titulo}</span>
        <span className="text-gray-400"> · {autor ? `${autor} · ` : ''}{formatoFecha(fecha)}</span>
      </p>
      {children && <div className="mt-1 text-sm text-gray-600 whitespace-pre-line break-words">{children}</div>}
    </li>
  )
}

/* ─── Una novedad: cabecera siempre visible, detalle desplegable ─── */
function TarjetaNovedad({ n, permisos, abierta: expandida, onAlternar, onResolver, onComentar }) {
  const [modo, setModo] = useState(null) // null | 'comentar' | 'resolver'
  const [texto, setTexto] = useState('')
  const [reprogramar, setReprogramar] = useState(false)
  const [noRealizado, setNoRealizado] = useState(false)
  const [nuevaFecha, setNuevaFecha] = useState(aInputFechaHora(permisos.fechaProgramadaActual))
  const [enviando, setEnviando] = useState(false)
  const color = colorNovedad(n.categoriaColor)
  const estaAbierta = n.estado === 'ABIERTA'
  const seguimientos = n.seguimientos ?? []
  const observaciones = seguimientos.filter(s => s.tipo === 'OBSERVACION').length
  const reprogramaciones = seguimientos.filter(s => s.tipo === 'REPROGRAMACION').length

  const cerrarForm = () => { setModo(null); setTexto(''); setReprogramar(false); setNoRealizado(false) }
  const enviar = async () => {
    if (!texto.trim() || (modo === 'resolver' && reprogramar && !nuevaFecha)) return
    setEnviando(true)
    const ok = modo === 'resolver'
      ? await onResolver(n.id, texto.trim(), reprogramar ? `${nuevaFecha}:00` : null, noRealizado)
      : await onComentar(n.id, texto.trim())
    setEnviando(false)
    if (ok) cerrarForm()
  }

  return (
    <li className={`rounded-xl border bg-white overflow-hidden transition-shadow ${expandida ? 'shadow-sm border-gray-300' : 'border-gray-200 hover:border-gray-300'}`}>
      {/* Cabecera — clic para desplegar */}
      <button type="button" onClick={onAlternar} className="w-full text-left flex items-stretch">
        <span className={`w-1 flex-shrink-0 ${color.punto}`} />
        <div className="flex-1 min-w-0 px-4 py-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-gray-900">{n.tipoEtiqueta}</p>
              <p className="text-[11px] text-gray-400 mt-0.5">
                {n.categoriaEtiqueta} · {n.registradoPorNombre ?? 'Usuario'} · {formatoFecha(n.fechaRegistro)}
              </p>
            </div>
            <div className="flex items-center gap-2 flex-shrink-0">
              <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                estaAbierta ? 'bg-amber-100 text-amber-700' : 'bg-green-100 text-green-700'}`}>
                {estaAbierta ? 'Abierta' : 'Resuelta'}
              </span>
              <span className="text-gray-400"><IconoChevron abierto={expandida} /></span>
            </div>
          </div>
          {!expandida && (
            <>
              <p className="text-sm text-gray-500 mt-1.5 line-clamp-1">{n.descripcion}</p>
              {(observaciones > 0 || reprogramaciones > 0) && (
                <p className="text-[11px] text-gray-400 mt-1">
                  {observaciones > 0 && `${observaciones} observaci${observaciones === 1 ? 'ón' : 'ones'}`}
                  {observaciones > 0 && reprogramaciones > 0 && ' · '}
                  {reprogramaciones > 0 && `reprogramada ${reprogramaciones} ${reprogramaciones === 1 ? 'vez' : 'veces'}`}
                </p>
              )}
            </>
          )}
        </div>
      </button>

      {expandida && (
        <div className="border-t border-gray-100">
          {/* Seguimiento completo, en orden */}
          <div className="px-4 py-4 bg-gray-50">
            <ol className="relative space-y-3.5 before:absolute before:left-[7px] before:top-2 before:bottom-2 before:w-px before:bg-gray-200">
              <EntradaSeguimiento punto="bg-amber-400" titulo="Reportó" autor={n.registradoPorNombre} fecha={n.fechaRegistro}>
                {n.descripcion}
              </EntradaSeguimiento>
              {seguimientos.map(s => s.tipo === 'REPROGRAMACION' ? (
                <EntradaSeguimiento key={s.id} punto="bg-sky-500" titulo="Reprogramó" autor={s.autorNombre} fecha={s.fecha}>
                  <span className="inline-flex flex-wrap items-center gap-1.5 text-xs bg-white border border-sky-100 text-sky-800 rounded-lg px-2 py-1">
                    {s.fechaAnterior ? <span className="line-through text-sky-400">{formatoFecha(s.fechaAnterior)}</span> : <span className="text-sky-400">Sin hora</span>}
                    <span>→</span>
                    <span className="font-semibold">{formatoFecha(s.fechaNueva)}</span>
                  </span>
                </EntradaSeguimiento>
              ) : (
                <EntradaSeguimiento key={s.id} punto="bg-gray-400"
                  titulo={`Observación${s.autorRol ? ` (${NOMBRE_ROL[s.autorRol] ?? s.autorRol})` : ''}`}
                  autor={s.autorNombre} fecha={s.fecha}>
                  {s.texto}
                </EntradaSeguimiento>
              ))}
              {!estaAbierta && (
                <EntradaSeguimiento punto="bg-green-500" titulo="Resolvió" autor={n.resueltaPorNombre} fecha={n.fechaResolucion}>
                  {n.respuesta}
                </EntradaSeguimiento>
              )}
            </ol>
          </div>

          {/* Acciones — solo mientras siga abierta */}
          {estaAbierta && (permisos.puedeComentar || permisos.puedeResolver) && (
            <div className="px-4 py-3 border-t border-gray-100">
              {modo === null ? (
                <div className="flex flex-wrap gap-2 justify-end">
                  {permisos.puedeComentar && (
                    <button type="button" onClick={() => setModo('comentar')}
                      className="text-xs font-medium text-gray-700 bg-white border border-gray-200 hover:bg-gray-50 px-3 py-1.5 rounded-lg">
                      + Agregar observación
                    </button>
                  )}
                  {permisos.puedeResolver && (
                    <button type="button" onClick={() => setModo('resolver')}
                      className="text-xs font-medium text-white bg-green-600 hover:bg-green-700 px-3 py-1.5 rounded-lg">
                      Resolver{permisos.puedeReprogramar ? ' / reprogramar' : ''}
                    </button>
                  )}
                </div>
              ) : (
                <div className="space-y-2.5">
                  <p className="text-xs font-semibold text-gray-600">{modo === 'comentar' ? 'Nueva observación' : 'Resolver la novedad'}</p>
                  <textarea aria-label={modo === 'comentar' ? 'Nueva observación' : 'Respuesta a la novedad'} value={texto} onChange={e => setTexto(e.target.value)} rows={2} maxLength={2000} autoFocus
                    placeholder={modo === 'comentar'
                      ? 'Ej: El candidato llamó a las 9:15, dice que llega en 30 minutos.'
                      : 'Ej: Se habló con el cliente y se corre la cita para mañana a las 8:00.'}
                    className={inputCls} />
                  {modo === 'resolver' && permisos.puedeReprogramar && (
                    <div className="rounded-lg border border-sky-100 bg-sky-50/60 px-3 py-2.5">
                      <label className="flex items-center gap-2 text-xs font-medium text-sky-800">
                        <input type="checkbox" checked={reprogramar} onChange={e => { setReprogramar(e.target.checked); if (e.target.checked) setNoRealizado(false) }}
                          className="rounded border-sky-300 text-sky-600 focus:ring-sky-500" />
                        Correr la hora del subproceso
                      </label>
                      {reprogramar && (
                        <div className="mt-2 flex flex-wrap items-center gap-2">
                          <input type="datetime-local" aria-label="Nueva fecha y hora programada" value={nuevaFecha} onChange={e => setNuevaFecha(e.target.value)}
                            className="border border-sky-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-sky-400 bg-white" />
                          <p className="text-[11px] text-sky-700">
                            {permisos.fechaProgramadaActual ? `Hora actual: ${formatoFecha(permisos.fechaProgramadaActual)}. ` : ''}
                            Se valida la agenda y se le avisa a quien lo ejecuta.
                          </p>
                        </div>
                      )}
                    </div>
                  )}
                  {modo === 'resolver' && permisos.puedeReprogramar && (
                    <div className="rounded-lg border border-gray-200 bg-gray-50 px-3 py-2.5">
                      <label className="flex items-center gap-2 text-xs font-medium text-gray-700">
                        <input type="checkbox" checked={noRealizado} onChange={e => { setNoRealizado(e.target.checked); if (e.target.checked) setReprogramar(false) }}
                          className="rounded border-gray-300 text-gray-700 focus:ring-gray-500" />
                        Marcar el subproceso como No realizado
                      </label>
                      {noRealizado && (
                        <p className="text-[11px] text-gray-500 mt-1.5">
                          Se cierra sin penalizar al candidato: su peso en el riesgo se reparte entre los demás subprocesos. La respuesta de
                          arriba queda como motivo y se le avisa a quien lo ejecutaba.
                        </p>
                      )}
                    </div>
                  )}
                  <div className="flex justify-end gap-2">
                    <button type="button" onClick={cerrarForm}
                      className="text-xs border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 px-3 py-1.5 rounded-lg">
                      Cancelar
                    </button>
                    <button type="button" onClick={enviar}
                      disabled={!texto.trim() || enviando || (modo === 'resolver' && reprogramar && !nuevaFecha)}
                      className={`text-xs disabled:opacity-50 text-white font-medium px-3 py-1.5 rounded-lg ${
                        modo === 'resolver' ? 'bg-green-600 hover:bg-green-700' : 'bg-amber-600 hover:bg-amber-700'}`}>
                      {enviando ? 'Guardando…' : modo === 'comentar' ? 'Agregar observación' : reprogramar ? 'Resolver y reprogramar' : noRealizado ? 'Resolver y marcar No realizado' : 'Resolver y avisar'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </li>
  )
}

function Indicador({ valor, etiqueta, clase }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <span className={`text-lg font-bold leading-none ${clase}`}>{valor}</span>
      <span className="text-[11px] text-gray-500">{etiqueta}</span>
    </div>
  )
}

export default function NovedadesSubproceso({ idServicioSubproceso, onError, onExito, onConteo, onReprogramado }) {
  const [datos, setDatos] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [modalAbierto, setModalAbierto] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [filtro, setFiltro] = useState('todas') // 'todas' | 'abiertas' | 'resueltas'
  const [expandida, setExpandida] = useState(null) // id de la novedad desplegada

  const aplicar = useCallback((r) => {
    setDatos(r)
    onConteo?.((r.novedades ?? []).filter(n => n.estado === 'ABIERTA').length)
  }, [onConteo])

  useEffect(() => {
    setCargando(true)
    asignacionService.novedades(idServicioSubproceso)
      .then(r => {
        aplicar(r)
        // Se abre sola la abierta más reciente — es la que pide atención.
        setExpandida((r.novedades ?? []).find(n => n.estado === 'ABIERTA')?.id ?? null)
      })
      .catch(() => onError?.('No se pudieron cargar las novedades.'))
      .finally(() => setCargando(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idServicioSubproceso])

  // Tiempo real: si alguien registra, comenta o resuelve una novedad, la lista se actualiza sola.
  useCambios(() => asignacionService.novedades(idServicioSubproceso).then(aplicar).catch(() => {}))

  const reemplazar = (actualizada) =>
    aplicar({ ...datos, novedades: datos.novedades.map(n => n.id === actualizada.id ? actualizada : n) })

  const registrar = async (idTipoNovedad, descripcion) => {
    setEnviando(true)
    try {
      const nueva = await asignacionService.registrarNovedad(idServicioSubproceso, idTipoNovedad, descripcion)
      aplicar({ ...datos, novedades: [nueva, ...datos.novedades] })
      setExpandida(nueva.id)
      setFiltro('todas')
      setModalAbierto(false)
      onExito?.('Novedad registrada — ya se le avisó a quien coordina el servicio.')
    } catch (err) {
      onError?.(err.response?.data?.mensaje ?? 'No se pudo registrar la novedad.')
    } finally {
      setEnviando(false)
    }
  }

  const resolver = async (idNovedad, respuesta, nuevaFechaProgramada, marcarNoRealizado = false) => {
    try {
      reemplazar(await asignacionService.resolverNovedad(idServicioSubproceso, idNovedad, respuesta, nuevaFechaProgramada, marcarNoRealizado))
      if (marcarNoRealizado) setDatos(prev => ({ ...prev, puedeReprogramar: false }))
      if (nuevaFechaProgramada) {
        setDatos(prev => ({ ...prev, fechaProgramadaActual: nuevaFechaProgramada }))
        onReprogramado?.(nuevaFechaProgramada)
      }
      onExito?.(marcarNoRealizado
        ? 'Novedad resuelta — el subproceso quedó como No realizado.'
        : nuevaFechaProgramada
          ? 'Novedad resuelta y subproceso reprogramado — se le avisó a quien lo ejecuta.'
          : 'Novedad resuelta — se le avisó a quien la reportó.')
      return true
    } catch (err) {
      onError?.(err.response?.data?.mensaje ?? 'No se pudo resolver la novedad.')
      return false
    }
  }

  const comentar = async (idNovedad, texto) => {
    try {
      reemplazar(await asignacionService.agregarSeguimientoNovedad(idServicioSubproceso, idNovedad, texto))
      onExito?.('Observación agregada.')
      return true
    } catch (err) {
      onError?.(err.response?.data?.mensaje ?? 'No se pudo agregar la observación.')
      return false
    }
  }

  const novedades = datos?.novedades ?? []
  const abiertas = novedades.filter(n => n.estado === 'ABIERTA').length
  const resueltas = novedades.length - abiertas
  // Abiertas primero; dentro de cada grupo, la más reciente arriba (así llegan del backend).
  const visibles = novedades
    .filter(n => filtro === 'todas' || (filtro === 'abiertas' ? n.estado === 'ABIERTA' : n.estado === 'RESUELTA'))
    .sort((a, b) => (a.estado === 'ABIERTA' ? 0 : 1) - (b.estado === 'ABIERTA' ? 0 : 1))
  const permisos = {
    puedeResolver: datos?.puedeResolver,
    puedeComentar: datos?.puedeComentar,
    puedeReprogramar: datos?.puedeReprogramar,
    fechaProgramadaActual: datos?.fechaProgramadaActual,
  }

  return (
    <section className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
      {/* Encabezado */}
      <div className="px-5 py-4 flex items-center gap-3 flex-wrap">
        <span className="flex-shrink-0 w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
          <IconoBandera />
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="text-base font-semibold text-gray-900">Novedades del subproceso</h3>
          <p className="text-xs text-gray-500">Lo que pasó fuera de lo normal, con su seguimiento.</p>
        </div>
        {datos?.puedeRegistrar && (
          <button type="button" onClick={() => setModalAbierto(true)}
            className="inline-flex items-center gap-1.5 bg-amber-600 hover:bg-amber-700 text-white text-sm font-medium px-4 py-2 rounded-lg shadow-sm">
            + Reportar novedad
          </button>
        )}
      </div>

      {/* Resumen + filtro */}
      {novedades.length > 0 && (
        <div className="px-5 py-2.5 bg-gray-50 border-y border-gray-100 flex flex-wrap items-center gap-x-6 gap-y-2">
          <Indicador valor={abiertas} etiqueta={abiertas === 1 ? 'abierta' : 'abiertas'} clase="text-amber-600" />
          <Indicador valor={resueltas} etiqueta={resueltas === 1 ? 'resuelta' : 'resueltas'} clase="text-green-600" />
          {novedades.length > 1 && (
            <div className="ml-auto inline-flex rounded-lg bg-white border border-gray-200 p-0.5 text-xs">
              {[['todas', 'Todas'], ['abiertas', 'Abiertas'], ['resueltas', 'Resueltas']].map(([valor, etiqueta]) => (
                <button key={valor} type="button" onClick={() => setFiltro(valor)}
                  className={`px-2.5 py-1 rounded-md font-medium transition-colors ${
                    filtro === valor ? 'bg-gray-900 text-white' : 'text-gray-500 hover:text-gray-700'}`}>
                  {etiqueta}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Lista */}
      <div className="p-5">
        {cargando ? (
          <div className="flex justify-center py-8">
            <div className="animate-spin rounded-full h-6 w-6 border-4 border-amber-500 border-t-transparent" />
          </div>
        ) : novedades.length === 0 ? (
          <div className="text-center py-8">
            <span className="mx-auto w-12 h-12 rounded-full bg-gray-100 text-gray-400 flex items-center justify-center mb-3">
              <IconoBandera className="h-6 w-6" />
            </span>
            <p className="text-sm font-medium text-gray-700">Sin novedades</p>
            <p className="text-xs text-gray-400 mt-1 max-w-sm mx-auto">
              {datos?.puedeRegistrar
                ? '¿Pasó algo fuera de lo normal? (el candidato no llegó, una fuente no respondió…) Repórtalo para que quede registrado.'
                : 'No hay novedades reportadas en este subproceso.'}
            </p>
          </div>
        ) : visibles.length === 0 ? (
          <p className="text-xs text-gray-400 text-center py-6">No hay novedades en este filtro.</p>
        ) : (
          <ol className="space-y-2.5">
            {visibles.map(n => (
              <TarjetaNovedad key={n.id} n={n} permisos={permisos}
                abierta={expandida === n.id} onAlternar={() => setExpandida(e => e === n.id ? null : n.id)}
                onResolver={resolver} onComentar={comentar} />
            ))}
          </ol>
        )}
      </div>

      {modalAbierto && (
        <ModalNuevaNovedad tipos={datos?.tipos ?? []} enviando={enviando}
          onRegistrar={registrar} onCerrar={() => setModalAbierto(false)} />
      )}
    </section>
  )
}

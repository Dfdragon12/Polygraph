import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import formulariosService from '../../services/formulariosService'
import asignacionService from '../../services/asignacionService'
import Formulario from '../shared/Formulario'
import Toast from '../../components/Toast'
import FichaCandidato from '../../components/FichaCandidato'
import NovedadesSubproceso from '../../components/NovedadesSubproceso'
import SoportesInforme from '../../components/SoportesInforme'
import RevisionEnvio from '../poligrafista/RevisionEnvio'
import { FUENTE_DOCUMENTO, Tarjeta, useMasAngostoQue } from '../poligrafista/componentesHoja'
import { Parte, armarSecciones } from '../poligrafista/FormatoPoligrafia'
import { FASES, TODOS_LOS_TEMAS, esFormatoVisita, partesDelTema, avanceDelTema, faltantesDeVisita } from './fasesVisita'

// VD-01 · Visita domiciliaria en 3 fases y 10 temas, como el formato en Word. Usa el mismo backend y el mismo guardado que el formulario
// genérico (Formulario.jsx): los datos son los mismos, solo cambia cómo y en qué orden se capturan. Otro subservicio del visitador sigue
// usando el formulario genérico.

const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const NOOP = () => {}
const SIN_CONFIRMAR = new Set()

// Un solo GET: si el servicio no es la visita domiciliaria, se deja el formulario de siempre.
export default function FormularioVisitador() {
  const { idServicioSubproceso } = useParams()
  const [datos, setDatos] = useState(null)
  const [generico, setGenerico] = useState(false)
  useEffect(() => {
    setDatos(null)
    setGenerico(false)
    formulariosService.obtenerFormulario(idServicioSubproceso)
      .then(r => (esFormatoVisita(r.secciones) ? setDatos(r) : setGenerico(true)))
      .catch(() => setGenerico(true))
  }, [idServicioSubproceso])
  if (generico) return <Formulario />
  if (!datos) {
    return <div className="flex justify-center py-20"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  }
  return <FormatoVisita key={idServicioSubproceso} inicial={datos} idServicioSubproceso={idServicioSubproceso} />
}

// Lo que el visitador necesita tener a la vista durante toda la visita.
function PanelCaso({ ficha, nota }) {
  const temas = ficha?.solicitudCliente?.temas ?? []
  const datos = ficha?.solicitudCliente?.datos ?? []
  const decl = ficha?.declaraciones ?? []
  return (
    <div className="space-y-3">
      <Tarjeta key={`ev-${decl.length > 0}`} titulo="Lo que registró el evaluado" vacia={decl.length === 0}>
        <ul className="space-y-2">
          {decl.map((d, i) => (
            <li key={i}>
              <p className="text-xs text-gray-600">{d.pregunta}</p>
              <p className="text-gray-900 break-words"><b>{d.respuesta}</b>{d.detalle ? ` — ${d.detalle}` : ''}</p>
            </li>
          ))}
        </ul>
      </Tarjeta>
      <Tarjeta key={`nota-${!!nota}`} titulo="Nota del gestor" vacia={!nota} tono="nota">
        <p className="text-gray-900 whitespace-pre-line break-words">{nota}</p>
      </Tarjeta>
      <Tarjeta key={`cliente-${temas.length + datos.length > 0}`} titulo="Lo que pidió el cliente" vacia={temas.length === 0 && datos.length === 0}>
        {temas.length > 0 && (
          <ul className="list-disc pl-4 space-y-0.5 text-gray-800">
            {temas.map((t, i) => <li key={i} className="break-words">{t.nombre}{t.detalle ? `: ${t.detalle}` : ''}</li>)}
          </ul>
        )}
        {datos.length > 0 && (
          <dl className="mt-2 space-y-1">
            {datos.map((d, i) => <div key={i}><dt className="text-xs text-gray-600">{d.etiqueta}</dt><dd className="text-gray-900 break-words">{d.valor}</dd></div>)}
          </dl>
        )}
      </Tarjeta>
    </div>
  )
}

export function FormatoVisita({ inicial, idServicioSubproceso }) {
  const navigate = useNavigate()
  const angosto = useMasAngostoQue(900)
  const [secciones, setSecciones] = useState(() => armarSecciones(inicial))
  const [meta, setMeta] = useState({ idFormulario: inicial.idFormulario })
  const [omitidas] = useState(() => new Set([...(inicial.secciones ?? []), ...(inicial.tablasFijas ?? [])].filter(s => s.omitida).map(s => s.idSeccion)))
  const soloLectura = !!inicial.soloLectura
  const [temaId, setTemaId] = useState(TODOS_LOS_TEMAS[0].id)
  const [sucio, setSucio] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [fallido, setFallido] = useState(false)
  const [guardadoEn, setGuardadoEn] = useState(null)
  const [ahora, setAhora] = useState(Date.now())
  const [toast, setToast] = useState(null)
  const [descargando, setDescargando] = useState(false)
  const [ficha, setFicha] = useState(null)
  const [verCaso, setVerCaso] = useState(false)
  const [pedidoVista, setPedidoVista] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const cambios = useRef(0)
  const inicioRef = useRef(null)
  const error = (m) => setToast({ mensaje: m, tipo: 'error' })

  useEffect(() => { formulariosService.fichaCandidato(idServicioSubproceso).then(setFicha).catch(() => setFicha(null)) }, [idServicioSubproceso])
  useEffect(() => { const t = setInterval(() => setAhora(Date.now()), 5000); return () => clearInterval(t) }, [])

  const tema = TODOS_LOS_TEMAS.find(t => t.id === temaId)
  const indice = TODOS_LOS_TEMAS.findIndex(t => t.id === temaId)
  const fase = FASES.find(f => f.id === tema.fase)
  const anterior = TODOS_LOS_TEMAS[indice - 1]
  const siguiente = TODOS_LOS_TEMAS[indice + 1]
  const posEnFase = fase.temas.findIndex(t => t.id === temaId)

  const avances = useMemo(() => Object.fromEntries(TODOS_LOS_TEMAS.map(t => {
    const { llenos, total } = avanceDelTema(t, secciones)
    return [t.id, total === 0 ? 'sin' : llenos >= total ? 'completo' : llenos === 0 ? 'vacio' : 'parcial']
  })), [secciones])
  const modulosActivos = (t) => partesDelTema(t, secciones).filter(p => p.modulo && p.activo).map(p => p.modulo.split(' · ')[0])
  const estadoFase = (f) => {
    const conCampos = f.temas.filter(t => avances[t.id] !== 'sin')
    if (conCampos.length > 0 && conCampos.every(t => avances[t.id] === 'completo')) return 'completa'
    if (f.id === tema.fase) return 'en curso'
    return conCampos.every(t => avances[t.id] === 'vacio' || avances[t.id] === 'sin') ? 'pendiente' : 'con faltantes'
  }

  const faltantes = useMemo(() => faltantesDeVisita(secciones), [secciones])

  const marcarCambio = () => { cambios.current += 1; setSucio(true); setFallido(false) }
  const editarCampo = (idSeccion, idColumna, valor) => {
    marcarCambio()
    setSecciones(prev => prev.map(s => (s.idSeccion === idSeccion ? { ...s, valores: { ...s.valores, [idColumna]: valor } } : s)))
  }
  const editarCeldaFila = (idSeccion, localId, idColumna, valor) => {
    marcarCambio()
    setSecciones(prev => prev.map(s => (s.idSeccion !== idSeccion ? s
      : { ...s, filas: s.filas.map(f => (f.localId === localId ? { ...f, valores: { ...f.valores, [idColumna]: valor } } : f)) })))
  }
  const agregarFila = (idSeccion) => {
    marcarCambio()
    setSecciones(prev => prev.map(s => (s.idSeccion === idSeccion
      ? { ...s, filas: [...s.filas, { localId: `vd-${Date.now()}-${s.filas.length}`, idFila: null, etiqueta: '', valores: {}, justificaciones: {} }] } : s)))
  }

  const guardar = async (silencioso = true) => {
    const version = cambios.current
    setGuardando(true)
    try {
      const payload = secciones.filter(s => !omitidas.has(s.idSeccion)).map(s => ({
        idSeccion: s.idSeccion,
        valores: s.tipo === 'CAMPOS' ? s.valores : null,
        justificaciones: s.tipo === 'CAMPOS' ? (s.justificaciones ?? {}) : null,
        filas: s.tipo === 'TABLA' ? s.filas.map(f => ({ etiqueta: f.etiqueta, valores: f.valores, idOrigenFijo: f.idOrigenFijo ?? null, justificaciones: f.justificaciones ?? {} })) : null,
      }))
      const r = await formulariosService.guardarFormulario(idServicioSubproceso, payload, [...omitidas])
      setMeta({ idFormulario: r.idFormulario })
      // Lo calculado por la plataforma (campos y tablas de solo lectura) se toma de la respuesta.
      setSecciones(prev => prev.map(s => {
        const n = (r.secciones ?? []).find(x => x.idSeccion === s.idSeccion)
        if (!n) return s
        if (s.calculada) return { ...s, filas: (n.filas ?? []).map((f, i) => ({ localId: `vd-c-${s.idSeccion}-${i}-${Date.now()}`, idFila: f.idFila, etiqueta: f.etiqueta, valores: { ...f.valores }, justificaciones: {} })) }
        if (s.tipo !== 'CAMPOS') return s
        const valores = { ...s.valores }
        s.columnas.filter(c => c.automatico).forEach(c => { valores[c.idColumna] = n.valores?.[c.idColumna] ?? '' })
        return { ...s, valores }
      }))
      if (cambios.current === version) setSucio(false)
      setFallido(false)
      setGuardadoEn(Date.now())
      if (!silencioso) setToast({ mensaje: 'Formulario guardado', tipo: 'exito' })
      return true
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo guardar el formulario.', tipo: 'error' })
      setFallido(true)
      return false
    } finally {
      setGuardando(false)
    }
  }

  // Guardado automático, igual que el formulario genérico: unos segundos después de cada cambio.
  useEffect(() => {
    if (soloLectura || !sucio || guardando || fallido) return undefined
    const t = setTimeout(() => { guardar(true) }, 2500)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secciones, sucio, guardando, fallido, soloLectura])

  useEffect(() => {
    const aviso = (e) => { if (sucio) { e.preventDefault(); e.returnValue = '' } }
    window.addEventListener('beforeunload', aviso)
    return () => window.removeEventListener('beforeunload', aviso)
  }, [sucio])

  const irA = (id) => {
    setTemaId(id)
    setVerCaso(false)
    inicioRef.current?.scrollIntoView({ block: 'start' })
  }

  // Antes de generar la vista o enviar: lo último que escribió ya está guardado (true) o se avisa y no se sigue (false).
  const asegurarGuardado = async () => (sucio ? guardar(true) : true)

  const enviar = async () => {
    setEnviando(true)
    try {
      if (!(await asegurarGuardado())) return
      await asignacionService.completar(idServicioSubproceso)
      setToast({ mensaje: 'Informe enviado a revisión del gestor', tipo: 'exito' })
      setTimeout(() => navigate('/visitador/visitas'), 900)
    } catch (err) {
      error(err.response?.data?.mensaje ?? 'No se pudo enviar el informe.')
    } finally {
      setEnviando(false)
    }
  }

  const descargarWord = async () => {
    setDescargando(true)
    try {
      if (sucio) await guardar(true)
      const blob = await formulariosService.descargarDocumento(idServicioSubproceso)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `Visita_${inicial.cedulaEvaluado ?? idServicioSubproceso}.docx`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      error('No se pudo generar el documento.')
    } finally {
      setDescargando(false)
    }
  }

  const textoGuardado = guardando ? 'Guardando…' : fallido ? 'No se pudo guardar' : sucio ? 'Cambios sin guardar'
    : guardadoEn ? `Guardado hace ${Math.max(0, Math.round((ahora - guardadoEn) / 1000))} s` : 'Todo guardado'
  const datosGenerales = secciones.find(s => s.clave === 'datos_generales')
  const valorDe = (clave) => { const c = datosGenerales?.columnas.find(x => x.clave === clave); return c ? datosGenerales.valores[c.idColumna] : null }
  const fechaCorta = (iso) => (iso ? new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: 'numeric', minute: '2-digit' }) : null)
  const partes = partesDelTema(tema, secciones)

  const propsParte = (p) => ({
    parte: p, soloLectura: soloLectura || p.soloLectura, idServicioSubproceso, secciones, frases: {}, temasCliente: [], declarado: {}, aplicar: NOOP,
    onCampo: editarCampo, onCelda: editarCeldaFila, onAgregar: agregarFila, confirmados: SIN_CONFIRMAR, onConfirmar: NOOP, onError: error,
    // Un módulo lleva su propio título; los demás, el nombre de su sección cuando el tema reúne varias.
    tema: { ...tema, soloLectura: false, partes: p.modulo ? [{}] : tema.partes },
  })

  return (
    <div className="max-w-[1280px] mx-auto">
      <div ref={inicioRef} />
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}

      {/* Encabezado fijo del caso */}
      <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
        <div className="min-w-0">
          <Link to="/visitador/visitas" className="text-xs text-primary-700 hover:underline">← Mis Visitas</Link>
          <h1 className="text-lg font-semibold text-gray-900 truncate">{inicial.nombreEvaluado}</h1>
          <p className="text-xs text-gray-700">
            {[inicial.nombreProceso, inicial.idServicio ? `Servicio #${inicial.idServicio}` : null, inicial.cargo, valorDe('ev_cliente')].filter(Boolean).join(' · ')}
          </p>
          <p className="text-xs text-gray-700">
            {[valorDe('ev_criticidad') ? `Criticidad del cargo: ${valorDe('ev_criticidad')}` : null,
              inicial.fechaProgramada ? `Cita: ${fechaCorta(inicial.fechaProgramada)}` : null].filter(Boolean).join(' · ')}
          </p>
        </div>
        <div className="flex flex-col items-end gap-2">
          <p className={`text-xs ${fallido ? 'text-red-600' : 'text-gray-700'}`} aria-live="polite">{textoGuardado}</p>
          <div className="flex gap-2">
            {!soloLectura && (
              <button type="button" onClick={() => guardar(false)} disabled={guardando || !sucio}
                className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50 disabled:opacity-50">Guardar</button>
            )}
            <button type="button" onClick={() => { irA('revision'); setPedidoVista(n => n + 1) }}
              className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">Ver el informe</button>
          </div>
        </div>
      </div>

      {/* Las 3 fases */}
      <nav aria-label="Fases del formato" className="grid grid-cols-3 gap-2 mb-5">
        {FASES.map(f => {
          const estado = estadoFase(f)
          const actual = f.id === tema.fase
          const color = estado === 'completa' ? 'text-green-700' : estado === 'con faltantes' ? 'text-amber-700' : 'text-gray-700'
          return (
            <button key={f.id} type="button" onClick={() => irA(f.temas[0].id)} aria-current={actual ? 'step' : undefined}
              className={`text-left rounded-lg border px-3 py-2.5 transition-colors ${actual ? 'border-primary-500 bg-primary-50' : 'border-gray-200 bg-white hover:bg-gray-50'}`}>
              <span className="block text-[11px] font-semibold uppercase tracking-wider text-gray-700">Fase {f.id}</span>
              <span className="block text-sm font-medium text-gray-900">{f.nombre}</span>
              <span className={`block text-xs ${color}`}>{estado === 'completa' ? '✓ completa' : estado}</span>
            </button>
          )
        })}
      </nav>

      <div className={angosto ? 'space-y-4' : 'grid grid-cols-[250px_minmax(0,1fr)] gap-5'}>
        {angosto ? (
          <div className="space-y-3">
            <div>
              <label htmlFor="selector-tema" className="block text-xs font-semibold text-gray-700 mb-1">Tema de la fase {fase.id}</label>
              <select id="selector-tema" value={temaId} onChange={e => irA(e.target.value)} className="w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white">
                {fase.temas.map(t => <option key={t.id} value={t.id}>{avances[t.id] === 'completo' ? '✓ ' : ''}{t.titulo}</option>)}
              </select>
            </div>
            <button type="button" onClick={() => setVerCaso(v => !v)} aria-expanded={verCaso}
              className="w-full min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 bg-white text-gray-800">
              {verCaso ? 'Ocultar la información del caso' : 'Información del caso'}
            </button>
            {verCaso && <PanelCaso ficha={ficha} nota={inicial.observacionesAsignacion} />}
          </div>
        ) : (
          <aside aria-label={`Temas de la fase ${fase.id}`} className="self-start sticky top-0 space-y-3 overflow-y-auto pr-1" style={{ maxHeight: 'calc(100vh - 6rem)' }}>
            <ul className="space-y-1">
              {fase.temas.map(t => {
                const activo = t.id === temaId
                const completo = avances[t.id] === 'completo'
                return (
                  <li key={t.id}>
                    <button type="button" onClick={() => irA(t.id)} aria-current={activo ? 'true' : undefined}
                      className={`w-full text-left flex items-start gap-2 rounded-lg px-3 py-2 text-sm ${activo ? 'bg-primary-600 text-white' : 'text-gray-800 hover:bg-gray-100'}`}>
                      <span className={`mt-0.5 w-4 flex-shrink-0 text-xs ${activo ? 'text-white' : 'text-green-700'}`}>{completo ? '✓' : ''}</span>
                      <span className="min-w-0 break-words">
                        {t.titulo}
                        {modulosActivos(t).map(m => (
                          <span key={m} className={`block mt-0.5 w-fit rounded px-1.5 text-[11px] font-medium ${activo ? 'bg-white/20 text-white' : 'bg-purple-100 text-purple-800'}`}>{t.titulo.replace(/^\d+\.\s*/, '')} · {m}</span>
                        ))}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
            <hr className="border-gray-200" />
            <PanelCaso ficha={ficha} nota={inicial.observacionesAsignacion} />
          </aside>
        )}

        {/* La hoja */}
        <div className="min-w-0 bg-gray-100 rounded-xl p-2 sm:p-6">
          <section className="bg-white shadow-md mx-auto w-full max-w-[880px] px-4 sm:px-10 py-8 text-[15px] text-gray-900 leading-relaxed"
            style={FUENTE_DOCUMENTO} aria-labelledby="titulo-tema">
            <header className="mb-6">
              <p className="text-base font-bold tracking-wide text-gray-900">POLYGRAPH SERVICE</p>
              <div className="h-0.5 bg-primary-600 my-1.5" />
              <p className="text-xs text-gray-700">Informe de visita domiciliaria{inicial.idServicio ? ` · Servicio #${inicial.idServicio}` : ''}</p>
            </header>
            <h2 id="titulo-tema" className="text-[15px] font-bold uppercase tracking-wide text-gray-900 border-b border-gray-300 pb-1 mb-4">{tema.titulo}</h2>

            <div className="space-y-6">
              {tema.novedades && (
                <NovedadesSubproceso idServicioSubproceso={idServicioSubproceso} onConteo={NOOP} onReprogramado={NOOP} onError={error} onExito={m => setToast({ mensaje: m, tipo: 'exito' })} />
              )}
              {tema.candidato && (
                <div className="space-y-4">
                  <FichaCandidato idServicioSubproceso={idServicioSubproceso} seccion="datos" onError={error} />
                  <FichaCandidato idServicioSubproceso={idServicioSubproceso} seccion="soportes" onResumen={NOOP} onError={error} />
                </div>
              )}

              {partes.map((p, i) => {
                if (p.modulo && !p.activo) {
                  return (
                    <div key={i} className="rounded-lg border border-dashed border-gray-300 px-3 py-2 flex flex-wrap items-center justify-between gap-2">
                      <p className="text-sm text-gray-700">Módulo {p.modulo} · no activo</p>
                      {!soloLectura && (
                        <button type="button" onClick={() => agregarFila(p.seccion.idSeccion)}
                          className="min-h-11 px-4 text-sm font-medium rounded-lg border border-purple-300 text-purple-800 hover:bg-purple-50">Activar módulo</button>
                      )}
                    </div>
                  )
                }
                return (
                  <div key={i}>
                    {p.modulo && (
                      <h3 className="text-sm font-bold text-purple-800 mb-2">
                        Módulo {p.modulo}{p.confidencial && <span className="ml-2 text-xs font-medium text-purple-700">Confidencial · lo registra Polygraph</span>}
                      </h3>
                    )}
                    <Parte {...propsParte(p)} />
                  </div>
                )
              })}

              {tema.informe && (
                <>
                  <SoportesInforme idServicioSubproceso={idServicioSubproceso} secciones={secciones} omitidas={omitidas} />
                  <RevisionEnvio idServicioSubproceso={idServicioSubproceso} faltantes={faltantes} onIrA={irA} asegurarGuardado={asegurarGuardado}
                    version={cambios.current} pedidoVista={pedidoVista} soloLectura={soloLectura} enviando={enviando} onEnviar={enviar} onError={error} />
                  <button type="button" onClick={descargarWord} disabled={descargando || !meta.idFormulario}
                    className="text-sm text-primary-700 underline min-h-11 disabled:opacity-60">
                    {descargando ? 'Generando…' : 'Descargar el informe en Word (.docx)'}
                  </button>
                </>
              )}
              {!tema.informe && !tema.novedades && !tema.candidato && partes.length === 0 && <p className="text-gray-700">Este tema no tiene campos en la plantilla.</p>}
            </div>
          </section>
        </div>
      </div>

      {/* Pie fijo: dentro del área de trabajo (no tapa el menú lateral) */}
      <div className="sticky bottom-0 z-30 mt-5 -mx-4 md:-mx-6 bg-white border-t border-gray-200 px-4 md:px-6 py-3">
        <div className="flex items-center justify-between gap-3">
          <button type="button" disabled={!anterior} onClick={() => irA(anterior.id)}
            className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 disabled:opacity-40 flex-shrink-0">← Anterior</button>
          <p className="text-xs text-gray-700 text-center">Fase {fase.id} de {FASES.length} · tema {posEnFase + 1} de {fase.temas.length}</p>
          {siguiente ? (
            <button type="button" onClick={() => irA(siguiente.id)}
              className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 hover:bg-primary-700 text-white truncate min-w-0 max-w-[50%]">
              Siguiente: {siguiente.titulo.replace(/^\d+\.\s*/, '')} →
            </button>
          ) : (
            <button type="button" onClick={() => navigate('/visitador/visitas')}
              className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 flex-shrink-0">Salir</button>
          )}
        </div>
      </div>
    </div>
  )
}

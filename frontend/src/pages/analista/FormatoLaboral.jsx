import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import formulariosService from '../../services/formulariosService'
import asignacionService from '../../services/asignacionService'
import Toast from '../../components/Toast'
import FichaCandidato from '../../components/FichaCandidato'
import NovedadesSubproceso from '../../components/NovedadesSubproceso'
import SoportesInforme from '../../components/SoportesInforme'
import RevisionEnvio from '../poligrafista/RevisionEnvio'
import { FUENTE_DOCUMENTO, Tarjeta, useMasAngostoQue } from '../poligrafista/componentesHoja'
import { Parte, armarSecciones } from '../poligrafista/FormatoPoligrafia'
import { parseFecha } from '../../utils/fechas'
import ConsultaLaboral from './ConsultaLaboral'
import AvisoCargo from './AvisoCargo'
import { DatosLaboral, AlcanceLaboral, periodoSugerido } from './TemasLaboral'
import VinculacionesLaboral, { valoresPorId, experienciasSinVinculacion } from './VinculacionesLaboral'
import InactividadLaboral from './InactividadLaboral'
import { ConceptoAnalista } from './RevisionAntecedentes'
import { SugerenciasInformativas, armarBorradorLaboral } from './RevisionLaboral'
import { vinculacionDesde, intervalosDeVinculaciones, huecosDeInactividad, sincronizarInactividad } from './reglasLaboral'
import { FASES, TODOS_LOS_TEMAS, partesDelTema, estadoDeTemas, faltantesDeLaboral } from './fasesLaboral'

// LB-01 · Verificación laboral en 3 fases y 5 temas, como el formato en Word. Usa el mismo backend y el mismo guardado que el formulario genérico
// (Formulario.jsx): los datos son los mismos, solo cambia cómo y en qué orden se capturan.

const NOOP = () => {}
const SIN_CONFIRMAR = new Set()
const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''

// Lo que el evaluado declaró en su hoja de vida (tabla fija): solo lectura, el analista no la edita.
const experienciasDe = (inicial) => (inicial.tablasFijas ?? []).find(t => t.clave === 'experiencia_laboral')?.filas ?? []
const esActual = (e) => /^s[ií]$/i.test(String(e.labora_actualmente ?? '')) && !tieneValor(e.fecha_fin)

function ExperienciaDeclarada({ filas }) {
  if (filas.length === 0) return <p className="text-sm text-gray-700">El evaluado no registró experiencia laboral en su hoja de vida.</p>
  return (
    <div className="overflow-x-auto" data-clave="experiencia_declarada">
      <p className="text-xs text-gray-700 mb-2">Lo que registró el evaluado en su hoja de vida. Solo lectura: el analista no la modifica.</p>
      <table className="w-full text-sm border border-gray-300">
        <thead className="bg-gray-100 text-xs text-gray-700">
          <tr>{['Empresa', 'Cargo', 'Inicio', 'Retiro'].map(t => <th key={t} scope="col" className="text-left p-2 border-b border-gray-300">{t}</th>)}</tr>
        </thead>
        <tbody>
          {filas.map(e => (
            <tr key={e.id} className="border-b border-gray-200 align-top bg-gray-50">
              <td className="p-2 break-words">{e.empresa || '—'}</td>
              <td className="p-2 break-words">{e.cargo || '—'}</td>
              <td className="p-2 whitespace-nowrap">{e.fecha_inicio || '—'}</td>
              <td className="p-2 whitespace-nowrap">{esActual(e) ? 'Actual' : (e.fecha_fin || '—')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function PanelCaso({ filas, notas, nota }) {
  return (
    <div className="space-y-3">
      <Tarjeta key={`ev-${filas.length > 0}`} titulo="Lo que registró el evaluado" vacia={filas.length === 0}>
        <ul className="space-y-3">
          {filas.map(e => (
            <li key={e.id}>
              <p className="text-gray-900 font-semibold break-words">{e.empresa || '—'}</p>
              <p className="text-xs text-gray-700 break-words">{[e.cargo, `${e.fecha_inicio || '—'} → ${esActual(e) ? 'Actual' : (e.fecha_fin || '—')}`].filter(Boolean).join(' · ')}</p>
              {(e.nombre_jefe || e.telefono_empresa) && (
                <p className="text-xs text-gray-700 break-words">{[e.nombre_jefe ? `Jefe: ${e.nombre_jefe}` : null, e.telefono_empresa ? `Tel.: ${e.telefono_empresa}` : null].filter(Boolean).join(' · ')}</p>
              )}
            </li>
          ))}
        </ul>
      </Tarjeta>
      <Tarjeta key={`nota-${notas.length > 0 || !!nota}`} titulo="Nota de seguimiento" vacia={notas.length === 0 && !nota} tono="nota">
        {nota && <p className="text-gray-900 whitespace-pre-line break-words mb-2">{nota}</p>}
        {notas.map((n, i) => <p key={i} className="text-gray-900 whitespace-pre-line break-words"><b>{n.titulo}:</b> {n.texto}</p>)}
        <p className="mt-2 text-[11px] text-gray-600">No se imprime.</p>
      </Tarjeta>
      <Tarjeta titulo="Consulta interna" abierta>
        <ConsultaLaboral />
      </Tarjeta>
    </div>
  )
}

export default function FormatoLaboral({ inicial, idServicioSubproceso }) {
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
  const [verCaso, setVerCaso] = useState(false)
  const [panel, setPanel] = useState(null)   // 'novedades' | 'soportes' | null
  const [pedidoVista, setPedidoVista] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const [objetivo, setObjetivo] = useState(null)
  const cambios = useRef(0)
  const inicioRef = useRef(null)
  const error = (m) => setToast({ mensaje: m, tipo: 'error' })

  useEffect(() => { const t = setInterval(() => setAhora(Date.now()), 5000); return () => clearInterval(t) }, [])

  const tema = TODOS_LOS_TEMAS.find(t => t.id === temaId)
  const indice = TODOS_LOS_TEMAS.findIndex(t => t.id === temaId)
  const fase = FASES.find(f => f.id === tema.fase)
  const anterior = TODOS_LOS_TEMAS[indice - 1]
  const siguiente = TODOS_LOS_TEMAS[indice + 1]
  const posEnFase = fase.temas.findIndex(t => t.id === temaId)

  const { estados: avances, llenos: avanceLlenos, total: avanceTotal } = useMemo(() => estadoDeTemas(secciones), [secciones])
  const estadoFase = (f) => {
    const conCampos = f.temas.filter(t => avances[t.id] !== 'sin')
    if (conCampos.length > 0 && conCampos.every(t => avances[t.id] === 'completo')) return 'completa'
    if (f.id === tema.fase) return 'en curso'
    return conCampos.every(t => avances[t.id] === 'vacio' || avances[t.id] === 'sin') ? 'pendiente' : 'con faltantes'
  }
  const faltantes = useMemo(() => faltantesDeLaboral(secciones), [secciones])
  const filasDeclaradas = useMemo(() => experienciasDe(inicial), [inicial])

  const alcance = secciones.find(x => x.clave === 'alcance')
  const valAlcance = (clave) => { const c = alcance?.columnas.find(x => x.clave === clave); return c ? (alcance.valores[c.idColumna] ?? '') : '' }
  const periodo = { desde: valAlcance('lab_periodo_desde'), hasta: valAlcance('lab_periodo_hasta') }

  // LB-02 · El periodo llega sugerido una sola vez, al abrir, mientras no tenga nada escrito.
  const periodoPuesto = useRef(false)
  useEffect(() => {
    if (periodoPuesto.current || soloLectura || !alcance) return
    periodoPuesto.current = true
    const sug = periodoSugerido(inicial.aniosPeriodoLaboral, inicial.fechaSolicitud)
    if (sug && !tieneValor(periodo.desde) && !tieneValor(periodo.hasta)) {
      const id = (clave) => alcance.columnas.find(c => c.clave === clave)?.idColumna
      editarCampo(alcance.idSeccion, id('lab_periodo_desde'), sug.desde)
      editarCampo(alcance.idSeccion, id('lab_periodo_hasta'), sug.hasta)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // LB-03 · Una vinculación por cada experiencia declarada dentro del periodo (al abrir y cuando el periodo se amplía). Una experiencia solo se crea una vez.
  const creadas = useRef(new Set())
  useEffect(() => {
    if (soloLectura) return
    const sv = secciones.find(x => x.clave === 'vinculaciones')
    if (!sv || !tieneValor(periodo.desde) || !tieneValor(periodo.hasta)) return
    experienciasSinVinculacion(sv, filasDeclaradas, periodo.desde, periodo.hasta, creadas.current).forEach(e => {
      creadas.current.add(e.id)
      agregarFila(sv.idSeccion, valoresPorId(sv, vinculacionDesde(e)))
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secciones, filasDeclaradas, soloLectura])

  // LB-04 · Los periodos de inactividad se calculan con las fechas confirmadas de las vinculaciones y se mantienen al día solos.
  const contadorFilas = useRef(0)
  useEffect(() => {
    if (soloLectura) return
    const sv = secciones.find(x => x.clave === 'vinculaciones')
    const si = secciones.find(x => x.clave === 'inactividad')
    if (!sv || !si || sv.filas.length === 0 || !tieneValor(periodo.desde) || !tieneValor(periodo.hasta)) return
    const claveDe = (sec, f) => Object.fromEntries(sec.columnas.map(c => [c.clave, f.valores[c.idColumna] ?? '']))
    const huecos = huecosDeInactividad(periodo.desde, periodo.hasta, intervalosDeVinculaciones(sv.filas.map(f => claveDe(sv, f))))
    const actuales = si.filas.map(f => ({ localId: f.localId, idFila: f.idFila, v: claveDe(si, f) }))
    const r = sincronizarInactividad(actuales, huecos, inicial.inactividadesDeclaradas, () => `in-${Date.now()}-${contadorFilas.current++}`)
    if (!r.cambio) return
    marcarCambio()
    setSecciones(prev => prev.map(x => (x.idSeccion !== si.idSeccion ? x : {
      ...x,
      filas: r.filas.map(nf => {
        const previa = x.filas.find(f => f.localId === nf.localId)
        return { localId: nf.localId, idFila: nf.idFila, etiqueta: previa?.etiqueta ?? '', justificaciones: previa?.justificaciones ?? {},
          valores: { ...(previa?.valores ?? {}), ...valoresPorId(si, nf.v) } }
      }),
    })))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secciones, soloLectura])

  const vinculacionesPorClave = useMemo(() => {
    const sv = secciones.find(x => x.clave === 'vinculaciones')
    return sv ? sv.filas.map(f => Object.fromEntries(sv.columnas.map(c => [c.clave, f.valores[c.idColumna] ?? '']))) : []
  }, [secciones])

  // La nota de seguimiento de cada vinculación (v_seguimiento) se junta aquí; no se imprime.
  const notasSeguimiento = useMemo(() => {
    const s = secciones.find(x => x.clave === 'vinculaciones')
    if (!s) return []
    const id = (clave) => s.columnas.find(c => c.clave === clave)?.idColumna
    return s.filas.map((f, i) => ({ titulo: f.valores[id('v_empresa')] || `Vinculación ${i + 1}`, texto: f.valores[id('v_seguimiento')] })).filter(n => tieneValor(n.texto))
  }, [secciones])

  // Un aviso al gestor (dato que no coincide) se registra como novedad del subproceso.
  const tiposNovedad = useRef(null)
  const avisarGestor = async (clave, descripcion) => {
    try {
      if (!tiposNovedad.current) tiposNovedad.current = (await asignacionService.novedades(idServicioSubproceso)).tipos ?? []
      const tipo = tiposNovedad.current.find(t => t.clave === clave)
      if (!tipo) throw new Error('sin-tipo')
      await asignacionService.registrarNovedad(idServicioSubproceso, tipo.idTipoNovedad, descripcion)
    } catch (err) {
      error(err.response?.data?.mensaje ?? 'No se pudo avisar al gestor. Inténtalo de nuevo.')
      throw err
    }
  }

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
  const agregarFila = (idSeccion, valores = {}) => {
    marcarCambio()
    setSecciones(prev => prev.map(s => (s.idSeccion === idSeccion
      ? { ...s, filas: [...s.filas, { localId: `lb-${Date.now()}-${s.filas.length}`, idFila: null, etiqueta: '', valores: { ...valores }, justificaciones: {} }] } : s)))
  }

  // Solo se quita una fila que todavía no se ha guardado (lo guardado no lo borra un analista).
  const quitarFila = (idSeccion, localId) => {
    marcarCambio()
    setSecciones(prev => prev.map(s => (s.idSeccion !== idSeccion ? s : { ...s, filas: s.filas.filter(f => !(f.localId === localId && f.idFila == null)) })))
  }

  const guardar = async (silencioso = true, base = secciones) => {
    const version = cambios.current
    setGuardando(true)
    try {
      const payload = base.filter(s => !omitidas.has(s.idSeccion)).map(s => ({
        idSeccion: s.idSeccion,
        valores: s.tipo === 'CAMPOS' ? s.valores : null,
        justificaciones: s.tipo === 'CAMPOS' ? (s.justificaciones ?? {}) : null,
        filas: s.tipo === 'TABLA' ? s.filas.map(f => ({ etiqueta: f.etiqueta, valores: f.valores, idOrigenFijo: f.idOrigenFijo ?? null, justificaciones: f.justificaciones ?? {} })) : null,
      }))
      const r = await formulariosService.guardarFormulario(idServicioSubproceso, payload, [...omitidas])
      setMeta({ idFormulario: r.idFormulario })
      // Lo calculado por la plataforma (resultado, hallazgos, notas) se toma de la respuesta.
      setSecciones(prev => prev.map(s => {
        const n = (r.secciones ?? []).find(x => x.idSeccion === s.idSeccion)
        if (!n) return s
        if (s.calculada) return { ...s, filas: (n.filas ?? []).map((f, i) => ({ localId: `lb-c-${s.idSeccion}-${i}-${Date.now()}`, idFila: f.idFila, etiqueta: f.etiqueta, valores: { ...f.valores }, justificaciones: {} })) }
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

  const irA = (id, clave = null) => {
    setTemaId(id)
    setVerCaso(false)
    setObjetivo(clave ? { id, clave, n: Date.now() } : null)
    if (!clave) inicioRef.current?.scrollIntoView({ block: 'start' })
  }
  // Un faltante lleva al campo exacto: se desplaza hasta él y lo resalta unos segundos.
  useEffect(() => {
    if (!objetivo) return undefined
    const t = setTimeout(() => {
      const el = document.querySelector(`[data-clave="${objetivo.clave.split(':').pop()}"]`) ?? inicioRef.current
      el?.scrollIntoView({ block: 'center', behavior: 'smooth' })
      if (el && el !== inicioRef.current) {
        el.classList.add('ring-2', 'ring-amber-500', 'bg-amber-50')
        setTimeout(() => el.classList.remove('ring-2', 'ring-amber-500', 'bg-amber-50'), 3000)
      }
    }, 80)
    return () => clearTimeout(t)
  }, [objetivo])

  const asegurarGuardado = async () => (sucio ? guardar(true) : true)

  const enviar = async () => {
    setEnviando(true)
    try {
      // La fecha del concepto la pone el sistema al marcar como completado (el profesional ya viene del estudio).
      const hoy = new Date()
      const fechaCierre = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`
      const nuevas = secciones.map(x => {
        if (x.clave !== 'concepto_analista') return x
        const cFecha = x.columnas.find(c => c.clave === 'analista_fecha')
        return cFecha ? { ...x, valores: { ...x.valores, [cFecha.idColumna]: fechaCierre } } : x
      })
      setSecciones(nuevas)
      cambios.current += 1
      if (!(await guardar(true, nuevas))) return
      await asignacionService.completar(idServicioSubproceso)
      setToast({ mensaje: 'Informe enviado a revisión del gestor', tipo: 'exito' })
      setTimeout(() => navigate('/analista/asignaciones'), 900)
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
      a.download = `Laboral_${inicial.cedulaEvaluado ?? idServicioSubproceso}.docx`
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
  const datosEvaluado = secciones.find(s => s.clave === 'datos_evaluado')
  const valorDe = (clave) => { const c = datosEvaluado?.columnas.find(x => x.clave === clave); return c ? datosEvaluado.valores[c.idColumna] : null }
  // La criticidad es la de la solicitud: aquí solo se muestra.
  const criticidad = valorDe('ev_criticidad')
  const fechaCorta = (iso) => (iso ? parseFecha(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }) : null)
  const partes = partesDelTema(tema, secciones)

  const propsParte = (p) => ({
    parte: p, soloLectura: soloLectura || p.soloLectura, idServicioSubproceso, secciones, frases: {}, temasCliente: [], declarado: {}, aplicar: NOOP,
    onCampo: editarCampo, onCelda: editarCeldaFila, onAgregar: agregarFila, confirmados: SIN_CONFIRMAR, onConfirmar: NOOP, onError: error,
    tema: { ...tema, soloLectura: false },
  })

  return (
    <div className="max-w-[1280px] mx-auto">
      <div ref={inicioRef} />
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}

      {/* Encabezado fijo del caso */}
      <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
        <div className="min-w-0">
          <Link to="/analista/asignaciones" className="text-xs text-primary-700 hover:underline">← Mis asignaciones</Link>
          <h1 className="text-lg font-semibold text-gray-900 truncate">{inicial.nombreEvaluado}</h1>
          <p className="text-xs text-gray-700">
            {[inicial.nombreProceso, inicial.idServicio ? `Servicio #${inicial.idServicio}` : null, inicial.cargo, valorDe('ev_cliente')].filter(Boolean).join(' · ')}
          </p>
          <p className="text-xs text-gray-700">
            {[`Criticidad del cargo (de la solicitud): ${criticidad || 'sin definir'}`, inicial.fechaEntregaEstimada ? `Entrega: ${fechaCorta(inicial.fechaEntregaEstimada)}` : null].filter(Boolean).join(' · ')}
          </p>
          {!soloLectura && <AvisoCargo cargo={inicial.cargo} onAvisar={avisarGestor} clave="LAB_CARGO_NO_COINCIDE" />}
          {!criticidad && <p role="alert" className="text-xs text-amber-800">La solicitud no trae la criticidad del cargo. Avisa al gestor desde «Novedades».</p>}
        </div>
        <div className="flex flex-col items-end gap-2">
          <p className={`text-xs ${fallido ? 'text-red-600' : 'text-gray-700'}`} aria-live="polite">{textoGuardado}</p>
          <div className="flex flex-wrap justify-end gap-2">
            <button type="button" onClick={() => setPanel(p => (p === 'novedades' ? null : 'novedades'))} aria-expanded={panel === 'novedades'}
              className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Novedades</button>
            <button type="button" onClick={() => setPanel(p => (p === 'soportes' ? null : 'soportes'))} aria-expanded={panel === 'soportes'}
              className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Soportes del evaluado</button>
            {!soloLectura && (
              <button type="button" onClick={() => guardar(false)} disabled={guardando || !sucio}
                className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50 disabled:opacity-50">Guardar</button>
            )}
            <button type="button" onClick={() => { irA('revision'); setPedidoVista(n => n + 1) }}
              className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">Ver el informe</button>
          </div>
        </div>
      </div>

      {panel === 'novedades' && (
        <div className="mb-4 rounded-lg border border-gray-200 bg-white p-4">
          <NovedadesSubproceso idServicioSubproceso={idServicioSubproceso} onConteo={NOOP} onReprogramado={NOOP} onError={error} onExito={m => setToast({ mensaje: m, tipo: 'exito' })} />
        </div>
      )}
      {panel === 'soportes' && (
        <div className="mb-4 rounded-lg border border-gray-200 bg-white p-4">
          <FichaCandidato idServicioSubproceso={idServicioSubproceso} seccion="soportes" onResumen={NOOP} onError={error} />
        </div>
      )}

      <div className="mb-3" role="group" aria-label="Avance del formato">
        <p className="text-xs text-gray-800 mb-1">Avance: {avanceLlenos} de {avanceTotal} campos diligenciados</p>
        <div className="h-2 bg-gray-200 rounded" role="progressbar" aria-valuemin={0} aria-valuemax={100}
          aria-valuenow={avanceTotal ? Math.round(avanceLlenos / avanceTotal * 100) : 0} aria-label="Avance del formato">
          <div className="h-2 bg-primary-600 rounded" style={{ width: `${avanceTotal ? Math.round(avanceLlenos / avanceTotal * 100) : 0}%` }} />
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
            {verCaso && <PanelCaso filas={filasDeclaradas} notas={notasSeguimiento} nota={inicial.observacionesAsignacion} />}
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
                      <span className="min-w-0 break-words">{t.titulo}</span>
                    </button>
                  </li>
                )
              })}
            </ul>
            <hr className="border-gray-200" />
            <PanelCaso filas={filasDeclaradas} notas={notasSeguimiento} nota={inicial.observacionesAsignacion} />
          </aside>
        )}

        {/* La hoja */}
        <div className="min-w-0 bg-gray-100 rounded-xl p-2 sm:p-6">
          <section className="bg-white shadow-md mx-auto w-full max-w-[880px] px-4 sm:px-10 py-8 text-[15px] text-gray-900 leading-relaxed"
            style={FUENTE_DOCUMENTO} aria-labelledby="titulo-tema">
            <header className="mb-6">
              <p className="text-base font-bold tracking-wide text-gray-900">POLYGRAPH SERVICE</p>
              <div className="h-0.5 bg-primary-600 my-1.5" />
              <p className="text-xs text-gray-700">Verificación laboral{inicial.idServicio ? ` · Servicio #${inicial.idServicio}` : ''}</p>
            </header>
            <h2 id="titulo-tema" className="text-[15px] font-bold uppercase tracking-wide text-gray-900 border-b border-gray-300 pb-1 mb-4">{tema.titulo}</h2>

            <div className="space-y-6">
              {tema.vista === 'datos' && (
                <DatosLaboral secciones={secciones} datosCandidato={inicial.datosCandidato} cargoSolicitud={inicial.cargo} nombreProceso={inicial.nombreProceso}
                  criticidad={criticidad} soloLectura={soloLectura} onAvisar={avisarGestor} />
              )}
              {tema.declarada && <ExperienciaDeclarada filas={filasDeclaradas} />}
              {tema.vista === 'vinculaciones' && (
                <VinculacionesLaboral secciones={secciones} experiencias={filasDeclaradas} periodo={periodo} objetivo={objetivo} soloLectura={soloLectura} idServicioSubproceso={idServicioSubproceso}
                  onCelda={editarCeldaFila} onAgregar={agregarFila} onQuitar={quitarFila} onError={error} />
              )}
              {tema.vista === 'inactividad' && (
                <InactividadLaboral secciones={secciones} soloLectura={soloLectura} onCelda={editarCeldaFila} onAgregar={agregarFila} onQuitar={quitarFila} onCampo={editarCampo} />
              )}
              {tema.vista === 'alcance' && (
                <AlcanceLaboral secciones={secciones} experiencias={filasDeclaradas} vinculaciones={vinculacionesPorClave} aniosPeriodo={inicial.aniosPeriodoLaboral} fechaSolicitud={inicial.fechaSolicitud}
                  soloLectura={soloLectura} onCampo={editarCampo} />
              )}
              {!tema.vista && partes.map((p, i) => (
                <div key={`${p.seccion.idSeccion}-${i}`}>
                  {p.seccion.clave === 'concepto_analista'
                    ? <ConceptoAnalista secciones={secciones} nombreProfesional={inicial.nombreProfesionalEncargado} soloLectura={soloLectura} onCampo={editarCampo} armarBorrador={armarBorradorLaboral} />
                    : (
                      <>
                        {p.seccion.clave === 'informativos' && <div className="mb-3"><SugerenciasInformativas secciones={secciones} soloLectura={soloLectura} onAgregar={agregarFila} /></div>}
                        <Parte {...propsParte(p)} />
                      </>
                    )}
                </div>
              ))}

              {tema.informe && (
                <>
                  <SoportesInforme idServicioSubproceso={idServicioSubproceso} secciones={secciones} omitidas={omitidas} />
                  <RevisionEnvio etiquetaEnvio="Marcar como completado" idServicioSubproceso={idServicioSubproceso} faltantes={faltantes} onIrA={irA} asegurarGuardado={asegurarGuardado}
                    version={cambios.current} pedidoVista={pedidoVista} soloLectura={soloLectura} enviando={enviando} onEnviar={enviar} onError={error} />
                  <button type="button" onClick={descargarWord} disabled={descargando || !meta.idFormulario}
                    className="text-sm text-primary-700 underline min-h-11 disabled:opacity-60">
                    {descargando ? 'Generando…' : 'Descargar el informe en Word (.docx)'}
                  </button>
                </>
              )}
              {!tema.informe && !tema.vista && partes.length === 0 && <p className="text-gray-700">Este tema no tiene campos en la plantilla.</p>}
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
              Siguiente: {siguiente.titulo} →
            </button>
          ) : (
            <button type="button" onClick={() => navigate('/analista/asignaciones')}
              className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 flex-shrink-0">Salir</button>
          )}
        </div>
      </div>
    </div>
  )
}

import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import formulariosService from '../../services/formulariosService'
import Formulario from '../shared/Formulario'
import { CampoHoja, PanelContexto, FUENTE_DOCUMENTO, useMasAngostoQue } from './componentesHoja'
import { PreguntaSiNo, AdmisionesManuales, ComparacionModulo } from './BloquesEntrevista'
import { PREGUNTA_POR_COLUMNA, OBSERVACIONES_VACIAS, COMPARACION_POR_COLUMNA, COMPARACIONES, declaradoPorComparacion, textoDeclarado, hayConflicto } from './preguntasEntrevista'
import { leer, poner } from './logicaPregunta'
import RevisionEnvio, { ResumenAdmisiones } from './RevisionEnvio'
import { revisarTema, faltantesDelFormato, tituloLimpio } from './revisionFormato'
import asignacionService from '../../services/asignacionService'
import { PreguntasRelevantes, enLetras } from './PreguntasRelevantes'
import { FotoEvaluado } from './FotoEvaluado'
import Toast from '../../components/Toast'
import colaOffline from '../../services/colaOffline'
import { FASES, TODOS_LOS_TEMAS, contenidoDelTema, esFormatoPoligrafia } from './fasesFormato'

// PF-09 · true en pantallas de celular (menos de 768 px): en modo proveedor la entrevista se muestra una pregunta por pantalla.
function useCelular() {
  const [celular, setCelular] = useState(() => typeof window !== 'undefined' && window.matchMedia('(max-width: 767px)').matches)
  useEffect(() => {
    const m = window.matchMedia('(max-width: 767px)')
    const alCambiar = (e) => setCelular(e.matches)
    m.addEventListener('change', alCambiar)
    return () => m.removeEventListener('change', alCambiar)
  }, [])
  return celular
}

// PF-01 · Formato de poligrafía pre empleo en 4 fases, en el orden de la sesión. Usa el mismo backend
// y el mismo guardado que el formulario genérico (Formulario.jsx): los datos son los mismos, solo cambia
// cómo y en qué orden se capturan. Otro subservicio del poligrafista sigue usando el formulario genérico.

// Temas donde lo que el candidato respondió se compara con lo que dice en la entrevista: ahí «Su hoja de vida dice» empieza abierta.
const TEMAS_CON_COMPARACION = ['financiera', 'antecedentes']

let contador = 0
const idLocal = () => `pf-${++contador}`
const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''

// Un solo GET: si el servicio no es el formato de poligrafía, se deja el formulario de siempre.
export default function FormularioPoligrafista() {
  const { idServicioSubproceso } = useParams()
  const [datos, setDatos] = useState(null)
  const [generico, setGenerico] = useState(false)
  useEffect(() => {
    setDatos(null)
    setGenerico(false)
    formulariosService.obtenerFormulario(idServicioSubproceso)
      .then(r => (esFormatoPoligrafia(r.secciones) ? setDatos(r) : setGenerico(true)))
      .catch(() => setGenerico(true))
  }, [idServicioSubproceso])
  if (generico) return <Formulario />
  if (!datos) {
    return <div className="flex justify-center py-20"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  }
  return <FormatoPoligrafia key={idServicioSubproceso} inicial={datos} idServicioSubproceso={idServicioSubproceso} />
}

export function armarSecciones(r) {
  const editables = (r.secciones ?? []).map(s => ({
    idSeccion: s.idSeccion, clave: s.clave, nombre: s.nombre, tipo: s.tipo, orden: s.orden, calculada: !!s.calculada,
    columnas: s.columnas, valores: { ...s.valores }, justificaciones: { ...(s.justificaciones ?? {}) },
    filas: (s.filas ?? []).map(f => ({ localId: idLocal(), idFila: f.idFila, etiqueta: f.etiqueta, valores: { ...f.valores }, justificaciones: { ...(f.justificaciones ?? {}) } })),
  }))
  // Columnas de validación por fila de las tablas de la hoja de vida (educación, experiencia): se guardan
  // igual que en el formulario genérico, como una tabla más enlazada a la fila real del candidato.
  const validacion = (r.tablasFijas ?? []).filter(t => (t.columnasValidacion ?? []).length > 0).map(t => ({
    idSeccion: t.idSeccion, clave: null, nombre: t.nombre, tipo: 'TABLA', orden: t.orden, columnas: t.columnasValidacion, valores: {},
    origenFijoClave: t.clave,
    filas: (t.filas ?? []).map(fija => {
      const v = t.validacionPorId?.[fija.id] ?? { idFila: null, valores: {}, justificaciones: {} }
      return { localId: idLocal(), idFila: v.idFila, idOrigenFijo: Number(fija.id), etiqueta: '', valores: { ...v.valores }, justificaciones: { ...(v.justificaciones ?? {}) } }
    }),
  }))
  return [...editables, ...validacion]
}

// Avance de un tema: cuántos campos que alguien diligencia ya tienen valor (la misma cuenta de «Revisión y envío»).
function avanceDeTema(tema, secciones, tablasFijas, foto) {
  const { total, faltantes } = revisarTema(tema, secciones, tablasFijas, foto)
  return { llenos: total - faltantes.length, total }
}

function estadoDeAvance({ llenos, total }) {
  if (total === 0) return 'completo'
  if (llenos >= total) return 'completo'
  return llenos === 0 ? 'vacio' : 'parcial'
}

// `proveedor` (PF-09) = { onEnviado }: la pantalla la usa un proveedor externo con su enlace. Sin menú ni "volver", el guardado pasa por la
// cola local para no perder nada sin señal, y al enviar avisa a la página que la contiene en vez de ir a «Mis servicios».
export function FormatoPoligrafia({ inicial, idServicioSubproceso, proveedor = null }) {
  const navigate = useNavigate()
  const celular = useCelular()
  const modoUna = !!proveedor && celular
  const angosto = useMasAngostoQue(900)
  const [verCaso, setVerCaso] = useState(false)
  const [secciones, setSecciones] = useState(() => armarSecciones(inicial))
  const [meta, setMeta] = useState({ idFormulario: inicial.idFormulario })
  const [omitidas] = useState(() => new Set([...(inicial.secciones ?? []), ...(inicial.tablasFijas ?? [])].filter(s => s.omitida).map(s => s.idSeccion)))
  const tablasFijas = inicial.tablasFijas ?? []
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
  // "Confirmar" de los datos precargados: solo marca en pantalla que ya los revisó (no cambia ningún dato).
  const [confirmados, setConfirmados] = useState(() => new Set())
  const [frases, setFrases] = useState({})
  // Foto del evaluado (PF-06): sin foto y sin verificación de la cédula no se pasa a la fase 2.
  const [foto, setFoto] = useState(null)
  useEffect(() => { formulariosService.fotoEstado(idServicioSubproceso).then(setFoto).catch(() => setFoto({ existe: false, puedeTomar: false })) }, [idServicioSubproceso])
  const [cambiosPreguntas, setCambiosPreguntas] = useState([])
  const cargarHistorial = () => formulariosService.historialPreguntas(idServicioSubproceso).then(setCambiosPreguntas).catch(() => setCambiosPreguntas([]))
  useEffect(() => { cargarHistorial() }, [idServicioSubproceso])
  useEffect(() => {
    formulariosService.frasesEstandar().then(l => setFrases(Object.fromEntries(l.map(f => [f.clave, f.texto])))).catch(() => setFrases({}))
  }, [])
  useEffect(() => { formulariosService.fichaCandidato(idServicioSubproceso).then(setFicha).catch(() => setFicha(null)) }, [idServicioSubproceso])
  const cambios = useRef(0)
  const inicioRef = useRef(null)

  useEffect(() => { const t = setInterval(() => setAhora(Date.now()), 5000); return () => clearInterval(t) }, [])

  const tema = TODOS_LOS_TEMAS.find(t => t.id === temaId)
  const indice = TODOS_LOS_TEMAS.findIndex(t => t.id === temaId)
  const fase = FASES.find(f => f.id === tema.fase)
  const anterior = TODOS_LOS_TEMAS[indice - 1]
  const siguiente = TODOS_LOS_TEMAS[indice + 1]
  const temasDeFase = fase.temas
  const posEnFase = temasDeFase.findIndex(t => t.id === temaId)

  const avances = useMemo(() => Object.fromEntries(TODOS_LOS_TEMAS.map(t => [t.id, estadoDeAvance(avanceDeTema(t, secciones, tablasFijas, foto))])), [secciones, tablasFijas, foto])
  const estadoFase = (f) => {
    const estados = f.temas.map(t => avances[t.id])
    const todos = estados.every(e => e === 'completo')
    const ninguno = estados.every(e => e === 'completo' || e === 'vacio') && !estados.some(e => e === 'parcial') && f.temas.every(t => avanceDeTema(t, secciones, tablasFijas, foto).llenos === 0)
    if (todos) return 'completa'
    if (f.id === tema.fase) return 'en curso'
    return ninguno ? 'pendiente' : 'con faltantes'
  }

  // Cambio hecho por las preguntas de la entrevista: recibe las secciones y devuelve las nuevas.
  const aplicar = (fn) => {
    cambios.current += 1
    setSucio(true)
    setFallido(false)
    setSecciones(prev => fn(prev))
  }

  const editarCampo = (idSeccion, idColumna, valor) => {
    cambios.current += 1
    setSucio(true)
    setFallido(false)
    setSecciones(prev => prev.map(s => (s.idSeccion === idSeccion ? { ...s, valores: { ...s.valores, [idColumna]: valor } } : s)))
  }
  const editarCeldaFila = (idSeccion, localId, idColumna, valor) => {
    cambios.current += 1
    setSucio(true)
    setFallido(false)
    setSecciones(prev => prev.map(s => (s.idSeccion !== idSeccion ? s
      : { ...s, filas: s.filas.map(f => (f.localId === localId ? { ...f, valores: { ...f.valores, [idColumna]: valor } } : f)) })))
  }
  const agregarFila = (idSeccion) => {
    cambios.current += 1
    setSucio(true)
    setSecciones(prev => prev.map(s => (s.idSeccion === idSeccion
      ? { ...s, filas: [...s.filas, { localId: idLocal(), idFila: null, etiqueta: '', valores: {}, justificaciones: {} }] } : s)))
  }

  // ── PF-09 · Cola local del proveedor ──
  // Cada guardado se escribe PRIMERO en este dispositivo (IndexedDB) y luego se sube. Si no hay señal queda ahí y sube solo al volver la
  // conexión. Solo hay una versión pendiente (la última) porque el servidor reemplaza el formulario completo. Subidas en fila: una vieja
  // nunca pisa a una nueva.
  const claveCola = `form:${idServicioSubproceso}`
  const [pendiente, setPendiente] = useState(false)
  const pendienteRef = useRef(false)
  const marcarPendiente = (v) => { pendienteRef.current = v; setPendiente(v) }
  const fila = useRef(Promise.resolve())
  const enFila = (fn) => { const p = fila.current.then(fn, fn); fila.current = p.catch(() => {}); return p }
  const sinConexion = () => Object.assign(new Error('sin conexión'), { sinConexion: true })

  const empujarCola = async () => {
    const e = await colaOffline.leer(claveCola)
    if (!e) { marcarPendiente(false); return null }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) { marcarPendiente(true); throw sinConexion() }
    try {
      const r = await formulariosService.guardarFormulario(idServicioSubproceso, e.payload, e.omitidas)
      await colaOffline.borrarSiIgual(claveCola, e.ts)
      marcarPendiente(!!(await colaOffline.leer(claveCola)))
      return r
    } catch (err) {
      if (!err.response) { marcarPendiente(true); throw sinConexion() }
      throw err
    }
  }
  const guardarConCola = (payload) => enFila(async () => {
    await colaOffline.poner(claveCola, { payload, omitidas: [...omitidas], ts: Date.now() })
    marcarPendiente(true)
    return empujarCola()
  })
  const sincronizar = () => enFila(async () => {
    try { await empujarCola(); return true } catch (err) {
      if (err.response) setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo subir lo guardado en este dispositivo.', tipo: 'error' })
      return false
    }
  })

  // Al volver la señal (y cada 20 s mientras algo siga pendiente) sube lo guardado en el dispositivo.
  useEffect(() => {
    if (!proveedor) return undefined
    colaOffline.leer(claveCola).then(e => { if (e) { marcarPendiente(true); sincronizar() } })
    const intentar = () => { if (pendienteRef.current) sincronizar() }
    window.addEventListener('online', intentar)
    const t = setInterval(intentar, 20000)
    return () => { window.removeEventListener('online', intentar); clearInterval(t) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // exigirServidor: antes de generar la vista o enviar no basta con tenerlo en el dispositivo; tiene que estar en el servidor.
  const guardar = async (silencioso = true, exigirServidor = false) => {
    const version = cambios.current
    setGuardando(true)
    try {
      const payload = secciones.filter(s => !omitidas.has(s.idSeccion)).map(s => ({
        idSeccion: s.idSeccion,
        valores: s.tipo === 'CAMPOS' ? s.valores : null,
        justificaciones: s.tipo === 'CAMPOS' ? (s.justificaciones ?? {}) : null,
        filas: s.tipo === 'TABLA' ? s.filas.map(f => ({ etiqueta: f.etiqueta, valores: f.valores, idOrigenFijo: f.idOrigenFijo ?? null, justificaciones: f.justificaciones ?? {} })) : null,
      }))
      const r = proveedor ? await guardarConCola(payload) : await formulariosService.guardarFormulario(idServicioSubproceso, payload, [...omitidas])
      if (!r) {   // la cola ya estaba vacía: la sincronización de fondo lo subió
        if (cambios.current === version) setSucio(false)
        setFallido(false)
        return true
      }
      setMeta({ idFormulario: r.idFormulario })
      // Lo calculado por la plataforma (campos y tablas de solo lectura) se toma de la respuesta.
      setSecciones(prev => prev.map(s => {
        const n = (r.secciones ?? []).find(x => x.idSeccion === s.idSeccion)
        if (!n) return s
        if (s.calculada) return { ...s, filas: (n.filas ?? []).map(f => ({ localId: idLocal(), idFila: f.idFila, etiqueta: f.etiqueta, valores: { ...f.valores }, justificaciones: {} })) }
        if (s.tipo !== 'CAMPOS') return s
        const valores = { ...s.valores }
        s.columnas.filter(c => c.automatico).forEach(c => { valores[c.idColumna] = n.valores?.[c.idColumna] ?? '' })
        return { ...s, valores }
      }))
      if (cambios.current === version) setSucio(false)
      setFallido(false)
      setGuardadoEn(Date.now())
      cargarHistorial()
      if (!silencioso) setToast({ mensaje: 'Formulario guardado', tipo: 'exito' })
      return true
    } catch (err) {
      if (err.sinConexion) {
        // Sin señal: quedó en este dispositivo y sube solo. Para enviar sí hace falta conexión.
        if (cambios.current === version) setSucio(false)
        setFallido(false)
        if (exigirServidor) {
          setToast({ mensaje: 'Sin conexión. Tus respuestas están guardadas en este dispositivo; vuelve a intentarlo cuando tengas señal.', tipo: 'error' })
          return false
        }
        return true
      }
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo guardar el formulario.', tipo: 'error' })
      setFallido(true)
      return false
    } finally {
      setGuardando(false)
    }
  }

  // Guardado automático, igual que el formulario genérico: unos segundos después de cada cambio.
  useEffect(() => {
    if (soloLectura || !sucio || guardando || fallido) return
    const t = setTimeout(() => { guardar(true) }, 2500)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secciones, sucio, guardando, fallido, soloLectura])

  useEffect(() => {
    const aviso = (e) => { if (sucio || pendienteRef.current) { e.preventDefault(); e.returnValue = '' } }
    window.addEventListener('beforeunload', aviso)
    return () => window.removeEventListener('beforeunload', aviso)
  }, [sucio])

  const irA = async (id) => {
    const destino = TODOS_LOS_TEMAS.find(t => t.id === id)
    if (destino && destino.fase >= 2 && !soloLectura) {
      if (!foto?.existe || !foto.vigente?.cedulaVerificada) {
        setToast({ mensaje: 'Toma la foto del evaluado y verifica su cédula.', tipo: 'error' })
        setTemaId('foto')
        inicioRef.current?.scrollIntoView({ block: 'start' })
        return
      }
      // Al entrar a la entrevista la foto queda fija.
      if (!foto.bloqueada) {
        try { setFoto(await formulariosService.fotoBloquear(idServicioSubproceso)) } catch (err) {
          setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudo iniciar la entrevista.', tipo: 'error' })
          return
        }
      }
    }
    setTemaId(id)
    inicioRef.current?.scrollIntoView({ block: 'start' })
  }

  // Antes de generar la vista o enviar: lo último que escribió ya está guardado (true) o se avisa y no se sigue (false).
  // Con enlace de proveedor además vacía la cola del dispositivo: el servidor tiene que tener TODO antes de generar o enviar.
  const asegurarGuardado = async () => {
    if (proveedor && pendienteRef.current && !(await sincronizar())) {
      setToast({ mensaje: 'Sin conexión. Tus respuestas están guardadas en este dispositivo; vuelve a intentarlo cuando tengas señal.', tipo: 'error' })
      return false
    }
    return sucio ? guardar(true, true) : true
  }

  const faltantes = useMemo(() => faltantesDelFormato(secciones, tablasFijas, foto), [secciones, tablasFijas, foto])
  const [pedidoVista, setPedidoVista] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const enviar = async () => {
    setEnviando(true)
    try {
      if (!(await asegurarGuardado())) return
      await asignacionService.completar(idServicioSubproceso)
      if (proveedor) { proveedor.onEnviado(); return }
      setToast({ mensaje: 'Informe enviado a revisión del gestor', tipo: 'exito' })
      setTimeout(() => navigate('/poligrafista/servicios'), 900)
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? (err.response ? 'No se pudo enviar el informe.' : 'Sin conexión. Intenta de nuevo cuando tengas señal.'), tipo: 'error' })
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
      a.download = `Poligrafia_${inicial.cedulaEvaluado ?? idServicioSubproceso}.docx`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      setToast({ mensaje: 'No se pudo generar el documento.', tipo: 'error' })
    } finally {
      setDescargando(false)
    }
  }

  const textoGuardado = guardando ? 'Guardando…' : fallido ? 'No se pudo guardar' : pendiente ? 'Guardado en este dispositivo · se enviará al volver la señal' : sucio ? 'Cambios sin guardar'
    : guardadoEn ? `Guardado hace ${Math.max(0, Math.round((ahora - guardadoEn) / 1000))} s` : 'Todo guardado'

  // Observaciones de educación y de vehículos: vacías dicen "Ninguna." (editable), una sola vez al abrir el tema.
  useEffect(() => {
    if (soloLectura || !frases.obs_vacia || !['educacion', 'vehiculos'].includes(temaId)) return
    const claves = OBSERVACIONES_VACIAS.filter(c => (temaId === 'educacion') === (c === 'ed_obs'))
    const faltan = claves.filter(c => secciones.some(sec => sec.columnas.some(x => x.clave === c)) && !tieneValor(leer(secciones, c)))
    if (faltan.length > 0) aplicar(sec => faltan.reduce((acc, c) => poner(acc, c, frases.obs_vacia), sec))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [temaId, frases])

  // «N° de preguntas relevantes (en letras)» se cuenta solo.
  const filasPreguntas = secciones.find(x => x.clave === 'preguntas')?.filas.length ?? 0
  useEffect(() => {
    if (soloLectura) return
    const texto = filasPreguntas > 0 ? enLetras(filasPreguntas) : ''
    if (leer(secciones, 'pol_num_relevantes') !== texto) aplicar(sec => poner(sec, 'pol_num_relevantes', texto))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filasPreguntas, soloLectura])
  const estadoExamen = leer(secciones, 'examen_estado')

  const partes = contenidoDelTema(tema, secciones, tablasFijas)
  const temasCliente = ficha?.solicitudCliente?.temas ?? []
  const declarado = useMemo(() => declaradoPorComparacion(ficha?.declaraciones ?? []), [ficha])

  // Lo que el candidato respondió en su link llega solo al campo «respondió en el módulo» (una vez, mientras esté vacío).
  useEffect(() => {
    if (soloLectura || !ficha) return
    const faltan = COMPARACIONES.filter(c => declarado[c.id] && !tieneValor(leer(secciones, c.modulo)))
    if (faltan.length > 0) aplicar(sec => faltan.reduce((acc, c) => poner(acc, c.modulo, textoDeclarado(declarado[c.id])), sec))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ficha])
  const datosPrueba = secciones.find(s => s.clave === 'datos_prueba')
  const valorDe = (clave) => { const c = datosPrueba?.columnas.find(x => x.clave === clave); return c ? datosPrueba.valores[c.idColumna] : null }
  const fechaCorta = (iso) => (iso ? new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: 'numeric', minute: '2-digit' }) : null)
  const rutaCorregir = inicial.idCandidato ? `/poligrafista/candidatos/${inicial.idCandidato}` : null

  return (
    <div className="max-w-[1280px] mx-auto">
      <div ref={inicioRef} />
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}

      <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
        <div className="min-w-0">
          {!proveedor && <Link to="/poligrafista/servicios" className="text-xs text-primary-700 hover:underline">← Mis Servicios</Link>}
          <h1 className="text-lg font-semibold text-gray-900 truncate">{inicial.nombreEvaluado}</h1>
          <p className="text-xs text-gray-700">
            {[inicial.nombreProceso, inicial.idServicio ? `Servicio #${inicial.idServicio}` : null, inicial.cargo, valorDe('ev_cliente')].filter(Boolean).join(' · ')}
          </p>
          <p className="text-xs text-gray-700">
            {[inicial.fechaProgramada ? `Cita: ${fechaCorta(inicial.fechaProgramada)}` : null,
              inicial.fechaEntregaEstimada ? `Entrega: ${new Date(`${inicial.fechaEntregaEstimada}T12:00:00`).toLocaleDateString('es-CO', { day: 'numeric', month: 'long' })}` : null].filter(Boolean).join(' · ')}
          </p>
        </div>
        <div className="flex flex-col items-end gap-2">
          <p className={`text-xs ${fallido ? 'text-red-600' : 'text-gray-700'}`} aria-live="polite">{textoGuardado}</p>
          <button type="button" onClick={async () => { await irA('informe'); setPedidoVista(n => n + 1) }}
            className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">Ver como lo verá el cliente</button>
        </div>
      </div>

      {/* Las 4 fases */}
      <nav aria-label="Fases del formato" className="grid grid-cols-2 lg:grid-cols-4 gap-2 mb-5">
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

      <div className={angosto ? 'space-y-4' : 'grid grid-cols-[15rem_minmax(0,1fr)] gap-5'}>
        {/* FA-03 · Menos de 900 px: los temas son un selector y el panel del caso se abre con un botón */}
        {angosto && (
          <div className="space-y-3">
            <div>
              <label htmlFor="selector-tema" className="block text-xs font-semibold text-gray-700 mb-1">Tema de la fase {fase.id}</label>
              <select id="selector-tema" value={temaId} onChange={e => irA(e.target.value)} className="w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white">
                {temasDeFase.map(t => <option key={t.id} value={t.id}>{avances[t.id] === 'completo' && !t.informe ? '✓ ' : ''}{t.titulo}</option>)}
              </select>
            </div>
            <button type="button" onClick={() => setVerCaso(v => !v)} aria-expanded={verCaso}
              className="w-full min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 bg-white text-gray-800">
              {verCaso ? 'Ocultar la información del caso' : 'Información del caso'}
            </button>
            {verCaso && <PanelContexto ficha={ficha} nota={inicial.observacionesAsignacion} abrirHojaDeVida={TEMAS_CON_COMPARACION.includes(temaId)} />}
          </div>
        )}
        {/* Temas de la fase actual y, debajo, el panel del caso: la columna acompaña el desplazamiento */}
        {!angosto && <aside aria-label={`Temas de la fase ${fase.id}`} className="self-start sticky top-0 space-y-3 overflow-y-auto pr-1" style={{ maxHeight: 'calc(100vh - 6rem)' }}>
          <ul className="space-y-1">
            {temasDeFase.map(t => {
              const activo = t.id === temaId
              const completo = avances[t.id] === 'completo' && !t.informe
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
          <PanelContexto ficha={ficha} nota={inicial.observacionesAsignacion} abrirHojaDeVida={TEMAS_CON_COMPARACION.includes(temaId)} />
        </aside>}

        {/* La hoja */}
        <div className="min-w-0 bg-gray-100 rounded-xl p-2 sm:p-6">
          <section className="bg-white shadow-md mx-auto w-full max-w-[816px] px-5 sm:px-12 py-8 text-[15px] text-gray-900 leading-relaxed"
            style={FUENTE_DOCUMENTO} aria-labelledby="titulo-tema">
            <header className="mb-6">
              <p className="text-base font-bold tracking-wide text-gray-900">POLYGRAPH SERVICE</p>
              <div className="h-0.5 bg-primary-600 my-1.5" />
              <p className="text-xs text-gray-700">Informe de poligrafía pre empleo{inicial.idServicio ? ` · Servicio #${inicial.idServicio}` : ''}</p>
            </header>
            <h2 id="titulo-tema" className="text-[15px] font-bold uppercase tracking-wide text-gray-900 border-b border-gray-300 pb-1 mb-4">{tema.titulo}</h2>
            {tema.informe ? (
              <div className="space-y-4">
                <RevisionEnvio idServicioSubproceso={idServicioSubproceso} faltantes={faltantes} onIrA={irA} asegurarGuardado={asegurarGuardado}
                  version={cambios.current} pedidoVista={pedidoVista} soloLectura={soloLectura} enviando={enviando} onEnviar={enviar}
                  onError={m => setToast({ mensaje: m, tipo: 'error' })} />
                <button type="button" onClick={descargarWord} disabled={descargando || !meta.idFormulario}
                  className="text-sm text-primary-700 underline min-h-11 disabled:opacity-60">
                  {descargando ? 'Generando…' : 'Descargar el informe en Word (.docx)'}
                </button>
              </div>
            ) : tema.foto ? (
              <FotoEvaluado idServicioSubproceso={idServicioSubproceso} estado={foto} onEstado={setFoto} onError={m => setToast({ mensaje: m, tipo: 'error' })} />
            ) : partes.length === 0 && tema.id !== 'admisiones' && tema.id !== 'preguntas' ? (
              <p className="text-gray-700">Este tema no tiene campos en la plantilla.</p>
            ) : (
              <div className="space-y-6">
                {tema.id === 'preguntas' && <PreguntasRelevantes seccion={secciones.find(x => x.clave === 'preguntas')} temasCliente={temasCliente} cambios={cambiosPreguntas}
                  noRealizado={!!estadoExamen && estadoExamen !== 'Realizado'} disabled={soloLectura} onCelda={editarCeldaFila} onAgregar={agregarFila} />}
                {tema.id === 'admisiones' && <AdmisionesManuales secciones={secciones} disabled={soloLectura} aplicar={aplicar} />}
                {tema.id !== 'admisiones' && tema.id !== 'preguntas' && !modoUna && partes.map((p, i) => (
                  <Parte key={i} parte={p} tema={tema} soloLectura={soloLectura} idServicioSubproceso={idServicioSubproceso}
                    secciones={secciones} frases={frases} temasCliente={temasCliente} declarado={declarado} aplicar={aplicar}
                    onCampo={editarCampo} onCelda={editarCeldaFila} onAgregar={agregarFila}
                    confirmados={confirmados} onConfirmar={id => setConfirmados(prev => new Set(prev).add(id))} rutaCorregir={rutaCorregir}
                    onError={m => setToast({ mensaje: m, tipo: 'error' })} />
                ))}
                {/* PF-09 · En celular, con enlace de proveedor: una pregunta por pantalla, con Sí / No grandes. */}
                {tema.id !== 'admisiones' && tema.id !== 'preguntas' && modoUna && (
                  <UnaPorPantalla key={tema.id} unidades={partes.flatMap((p, i) => unidadesDeParte({
                    parte: p, clave: i, tema, soloLectura, idServicioSubproceso, secciones, frases, temasCliente, declarado, aplicar,
                    onCampo: editarCampo, onCelda: editarCeldaFila, onAgregar: agregarFila,
                    confirmados, onConfirmar: id => setConfirmados(prev => new Set(prev).add(id)), rutaCorregir,
                    onError: m => setToast({ mensaje: m, tipo: 'error' }), grande: true,
                  }))} />
                )}
                {tema.id === 'calculado' && <ResumenAdmisiones secciones={secciones} />}
              </div>
            )}
          </section>
        </div>

      </div>

      {/* Barra inferior: dentro del área de trabajo (no tapa el menú lateral) */}
      <div className="sticky bottom-0 z-30 mt-5 -mx-4 md:-mx-6 bg-white border-t border-gray-200 px-4 md:px-6 py-3">
        <div className="flex items-center justify-between gap-3">
          <button type="button" disabled={!anterior} onClick={() => irA(anterior.id)}
            className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 disabled:opacity-40 flex-shrink-0">← Anterior</button>
          <p className="text-xs text-gray-700 text-center">Fase {fase.id} de {FASES.length} · tema {posEnFase + 1} de {temasDeFase.length}</p>
          {siguiente ? (
            <button type="button" onClick={() => irA(siguiente.id)}
              className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 hover:bg-primary-700 text-white truncate min-w-0 max-w-[50%]">
              Siguiente: {siguiente.titulo.replace(/^\d+\.\s*/, '')} →
            </button>
          ) : proveedor ? <span /> : (
            <button type="button" onClick={() => navigate('/poligrafista/servicios')}
              className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 flex-shrink-0">Salir</button>
          )}
        </div>
      </div>
    </div>
  )
}

// Los campos de una parte CAMPOS, uno por elemento (las preguntas de la entrevista se pintan una sola vez, en su campo principal).
// `grande` (PF-09, celular con enlace de proveedor): botones Sí / No grandes.
function elementosDeCampos({ parte, tema, soloLectura, idServicioSubproceso, secciones, frases, temasCliente, declarado, aplicar, onCampo, confirmados, onConfirmar, rutaCorregir, onError, grande = false }) {
  const { seccion, columnas } = parte
  const bloqueada = soloLectura || tema.soloLectura || seccion.calculada
  return columnas.map(c => {
    const cm = COMPARACION_POR_COLUMNA[c.clave]
    if (cm) {
      return cm.principal ? <ComparacionModulo key={cm.comparacion.id} c={cm.comparacion} secciones={secciones} declarado={declarado[cm.comparacion.id]}
        titulo={c.nombre.replace(/\s*[–-]\s*respondió en el link$/i, '')} disabled={bloqueada} aplicar={aplicar} /> : null
    }
    const pq = PREGUNTA_POR_COLUMNA[c.clave]
    if (pq) {
      return pq.principal ? <PreguntaSiNo key={pq.pregunta.id} q={pq.pregunta} secciones={secciones} frases={frases} temasCliente={temasCliente} conflicto={hayConflicto(pq.pregunta.id, declarado)}
        disabled={bloqueada} aplicar={aplicar} idServicioSubproceso={idServicioSubproceso} onError={onError} grande={grande} /> : null
    }
    return (
      <CampoHoja key={c.idColumna} c={c} value={seccion.valores[c.idColumna]} disabled={bloqueada || c.clave === 'pol_num_relevantes'}
        onChange={v => onCampo(seccion.idSeccion, c.idColumna, v)} idServicioSubproceso={idServicioSubproceso} onError={onError}
        confirmado={confirmados.has(c.idColumna)} onConfirmar={() => onConfirmar(c.idColumna)} rutaCorregir={rutaCorregir} />
    )
  }).filter(Boolean)
}

// PF-09 · Lo que se diligencia como una «pregunta» de pantalla: cada campo/pregunta de una parte CAMPOS, o una parte entera (tabla).
function unidadesDeParte(props) {
  const { parte, clave } = props
  if (parte.fija || parte.seccion.tipo !== 'CAMPOS') return [<Parte key={clave} {...props} />]
  return elementosDeCampos(props)
}

// Una unidad a la vez, con «Anterior» y «Siguiente» grandes. Al terminar las de un tema, la barra de abajo pasa al tema siguiente.
function UnaPorPantalla({ unidades }) {
  const [paso, setPaso] = useState(0)
  const n = unidades.length
  if (n === 0) return <p className="text-gray-700">Este tema no tiene campos en la plantilla.</p>
  const i = Math.min(paso, n - 1)
  const nav = 'min-h-14 flex-1 px-4 text-base font-semibold rounded-lg border-2 disabled:opacity-40'
  return (
    <div>
      <p className="text-xs font-semibold text-gray-700 mb-3" aria-live="polite">Pregunta {i + 1} de {n}</p>
      <div key={i}>{unidades[i]}</div>
      <div className="mt-4 flex gap-3">
        <button type="button" disabled={i === 0} onClick={() => setPaso(i - 1)} className={`${nav} border-gray-300 text-gray-800 bg-white`}>← Anterior</button>
        <button type="button" disabled={i === n - 1} onClick={() => setPaso(i + 1)} className={`${nav} border-primary-600 bg-primary-600 text-white`}>Siguiente →</button>
      </div>
    </div>
  )
}

// Una parte de un tema: campos sueltos, una tabla editable, una tabla de la hoja de vida o una calculada.
export function Parte({ parte, tema, soloLectura, idServicioSubproceso, secciones, frases, temasCliente, declarado, aplicar, onCampo, onCelda, onAgregar, onError, confirmados, onConfirmar, rutaCorregir }) {
  if (parte.fija) return <TablaHojaVida fija={parte.fija} validacion={parte.validacion} soloLectura={soloLectura} idServicioSubproceso={idServicioSubproceso} onCelda={onCelda} onError={onError} />
  const { seccion, columnas } = parte
  const bloqueada = soloLectura || tema.soloLectura || seccion.calculada
  const subtitulo = tema.partes.length > 1 ? tituloLimpio(seccion.nombre) : null
  const Subtitulo = () => (subtitulo ? <h3 className="text-sm font-bold text-gray-700 mb-2">{subtitulo}</h3> : null)
  if (seccion.tipo === 'CAMPOS') {
    return (
      <div>
        <Subtitulo />
        {elementosDeCampos({ parte, tema, soloLectura, idServicioSubproceso, secciones, frases, temasCliente, declarado, aplicar, onCampo, confirmados, onConfirmar, rutaCorregir, onError })}
      </div>
    )
  }
  return (
    <div>
      <Subtitulo />
      {seccion.filas.length === 0 && <p className="text-gray-700 mb-2">Sin filas.</p>}
      <div className="space-y-3">
        {seccion.filas.map((f, n) => (
          <div key={f.localId} className="rounded border border-gray-300 p-3">
            {f.etiqueta && <p className="text-sm font-bold text-gray-800 mb-2">{f.etiqueta}</p>}
            {columnas.map(c => (
              <CampoHoja key={`${f.localId}-${c.idColumna}`} c={c} value={f.valores[c.idColumna]} disabled={bloqueada}
                onChange={v => onCelda(seccion.idSeccion, f.localId, c.idColumna, v)} idServicioSubproceso={idServicioSubproceso} onError={onError}
                confirmado onConfirmar={() => {}} />
            ))}
            <span className="sr-only">Fila {n + 1}</span>
          </div>
        ))}
      </div>
      {!bloqueada && (
        <button type="button" onClick={() => onAgregar(seccion.idSeccion)}
          className="mt-3 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar</button>
      )}
    </div>
  )
}


// Tabla de la hoja de vida del candidato (educación, experiencia): se ve de solo lectura y, debajo de cada
// fila, las columnas que el poligrafista valida en este servicio.
function TablaHojaVida({ fija, validacion, soloLectura, idServicioSubproceso, onCelda, onError }) {
  const filasVal = new Map((validacion?.filas ?? []).map(f => [String(f.idOrigenFijo), f]))
  if ((fija.filas ?? []).length === 0) return <p className="text-sm text-gray-600">El candidato no registró información en esta sección.</p>
  return (
    <div className="space-y-3">
      <p className="text-xs text-gray-600">Información que registró el candidato en su hoja de vida.</p>
      {fija.filas.map(fila => {
        const val = filasVal.get(String(fila.id))
        return (
          <div key={fila.id} className="rounded-lg border border-gray-200 p-3">
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1 text-sm">
              {fija.columnas.map(c => (
                <div key={c.clave} className="flex gap-2 bg-gray-50 rounded px-2 py-1">
                  <dt className="text-gray-600 flex-shrink-0">{c.etiqueta}:</dt>
                  <dd className="text-gray-900 min-w-0 break-words">{fila[c.clave] || '—'}</dd>
                </div>
              ))}
            </dl>
            {val && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-3">
                {validacion.columnas.filter(c => !c.automatico).map(c => (
                  <CampoHoja key={`${val.localId}-${c.idColumna}`} c={c} value={val.valores[c.idColumna]} disabled={soloLectura}
                    onChange={v => onCelda(validacion.idSeccion, val.localId, c.idColumna, v)} idServicioSubproceso={idServicioSubproceso} onError={onError}
                    confirmado onConfirmar={() => {}} />
                ))}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}


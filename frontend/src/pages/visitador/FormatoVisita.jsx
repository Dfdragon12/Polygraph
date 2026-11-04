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
import ProtocoloVisita from './ProtocoloVisita'
import IndicadoresVisita from './IndicadoresVisita'
import EgresosVisita from './EgresosVisita'
import TeletrabajoVisita from './TeletrabajoVisita'
import FotosVisita from './FotosVisita'
import { calcularVisita, CLAVES_CALCULADAS, integrantes as integrantesDe, soloDigitos } from './calculosVisita'
import DatosGeneralesVisita, { FILAS_REGISTRO, resultadoDeFila } from './DatosGeneralesVisita'
import { FASES, TODOS_LOS_TEMAS, esFormatoVisita, partesDelTema, avanceDelTema, faltantesDeVisita, PREGUNTAS_PROTOCOLO, numerosProtocolo, INDICADORES, FOTOS, PADRE_DE_FOTOS, estadoDeTemas } from './fasesVisita'

// VD-01 · Visita domiciliaria en 3 fases y 10 temas, como el formato en Word. Usa el mismo backend y el mismo guardado que el formulario
// genérico (Formulario.jsx): los datos son los mismos, solo cambia cómo y en qué orden se capturan. Otro subservicio del visitador sigue
// usando el formulario genérico.

const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const NOOP = () => {}
const SIN_CONFIRMAR = new Set()

// VD-02 · Módulos que el visitador enciende o apaga: cada uno agrupa las secciones de la plantilla que lo componen.
const MODULOS = [
  { nombre: 'OEA', secs: ['oea_bd', 'oea'] },
  { nombre: 'Teletrabajo', secs: ['teletrabajo', 'fotos_teletrabajo'] },
  { nombre: 'Vehicular', secs: ['vehicular', 'fotos_vehicular'] },
]
// Lo que la clase y el tipo de la visita encienden solos.
const SECS_RUTINA = ['rutina']
const SECS_VIRTUAL = ['virtual']

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
// Resumen del link del evaluado: dirección, estrato, estado civil, estudios y empleos (las referencias no se muestran).
function resumenDelEvaluado(inicial) {
  const filas = []
  ;(inicial.datosCandidato ?? []).filter(c => c.valor && /direcci|barrio|ciudad de resid|estrato|civil/i.test(`${c.clave} ${c.etiqueta}`))
    .forEach(c => filas.push({ etiqueta: c.etiqueta, valor: c.valor }))
  ;(inicial.tablasFijas ?? []).forEach(t => {
    const esEstudio = /educ|estudi/i.test(t.clave ?? '')
    const esEmpleo = /experiencia|emple/i.test(t.clave ?? '')
    if (!esEstudio && !esEmpleo) return
    const lineas = (t.filas ?? []).map(f => (t.columnas ?? []).map(c => f[c.clave]).filter(Boolean).join(' · ')).filter(Boolean)
    if (lineas.length > 0) filas.push({ etiqueta: esEstudio ? 'Estudios' : 'Empleos', valor: lineas.join(' | ') })
  })
  return filas
}

function PanelCaso({ ficha, nota, resumen = [] }) {
  const temas = ficha?.solicitudCliente?.temas ?? []
  const datos = ficha?.solicitudCliente?.datos ?? []
  const decl = ficha?.declaraciones ?? []
  return (
    <div className="space-y-3">
      <Tarjeta key={`ev-${resumen.length + decl.length > 0}`} titulo="Lo que registró el evaluado" vacia={resumen.length + decl.length === 0}>
        {resumen.length > 0 && (
          <dl className="space-y-1 mb-2">
            {resumen.map((r, i) => <div key={i}><dt className="text-xs text-gray-600">{r.etiqueta}</dt><dd className="text-gray-900 break-words">{r.valor}</dd></div>)}
          </dl>
        )}
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
  const [secciones, setSecciones] = useState(() => armarSecciones(inicial).map(s => ({
    ...s, columnas: s.columnas.map(c => (CLAVES_CALCULADAS.has(c.clave) ? { ...c, automatico: true, calculado: true } : c)),
  })))
  const [meta, setMeta] = useState({ idFormulario: inicial.idFormulario })
  const [omitidas, setOmitidas] = useState(() => new Set([...(inicial.secciones ?? []), ...(inicial.tablasFijas ?? [])].filter(s => s.omitida).map(s => s.idSeccion)))
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

  const { estados: avances, llenos: avanceLlenos, total: avanceTotal } = useMemo(() => estadoDeTemas(secciones), [secciones])
  const modulosActivos = (t) => partesDelTema(t, secciones).filter(p => p.modulo && p.activo).map(p => p.modulo.split(' · ')[0])
  const estadoFase = (f) => {
    const conCampos = f.temas.filter(t => avances[t.id] !== 'sin')
    if (conCampos.length > 0 && conCampos.every(t => avances[t.id] === 'completo')) return 'completa'
    if (f.id === tema.fase) return 'en curso'
    return conCampos.every(t => avances[t.id] === 'vacio' || avances[t.id] === 'sin') ? 'pendiente' : 'con faltantes'
  }

  const faltantes = useMemo(() => faltantesDeVisita(secciones), [secciones])
  const resumen = useMemo(() => resumenDelEvaluado(inicial), [inicial])
  const ocultos = useRef({})   // filas de los módulos apagados: se conservan mientras la pantalla siga abierta
  const tiposNovedad = useRef(null)

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
  const cambiarFilas = (idSeccion, fn) => {
    marcarCambio()
    setSecciones(prev => prev.map(s => (s.idSeccion === idSeccion ? { ...s, filas: fn(s.filas) } : s)))
  }
  const agregarFila = (idSeccion) => {
    marcarCambio()
    setSecciones(prev => prev.map(s => (s.idSeccion === idSeccion
      ? { ...s, filas: [...s.filas, { localId: `vd-${Date.now()}-${s.filas.length}`, idFila: null, etiqueta: '', valores: {}, justificaciones: {} }] } : s)))
  }

  // VD-02 · Prellenar «confirmado en la visita» con lo que registró el candidato y dejar el resultado coherente (una sola vez, al abrir).
  useEffect(() => {
    if (soloLectura) return
    let cambio = false
    const nuevas = secciones.map(s => {
      if (s.clave !== 'datos_generales') return s
      const valores = { ...s.valores }
      const c = (clave) => s.columnas.find(x => x.clave === clave)
      FILAS_REGISTRO.forEach(([, pre]) => {
        const reg = c(`${pre}_reg`), vis = c(`${pre}_vis`), res = c(`${pre}_res`)
        if (!reg || !vis || !res) return
        if (!tieneValor(valores[vis.idColumna]) && tieneValor(valores[reg.idColumna])) { valores[vis.idColumna] = valores[reg.idColumna]; cambio = true }
        const r = resultadoDeFila(valores[reg.idColumna], valores[vis.idColumna])
        if ((valores[res.idColumna] ?? '') !== r) { valores[res.idColumna] = r; cambio = true }
      })
      // Ciudad y fecha de la cita, solo si siguen vacías.
      const cf = c('v_ciudad_fecha')
      if (cf && !tieneValor(valores[cf.idColumna]) && inicial.fechaProgramada) {
        const f = new Date(inicial.fechaProgramada)
        const fecha = `${String(f.getDate()).padStart(2, '0')}/${String(f.getMonth() + 1).padStart(2, '0')}/${f.getFullYear()}`
        valores[cf.idColumna] = [inicial.ciudadEvaluado, fecha].filter(Boolean).join(', '); cambio = true
      }
      return cambio ? { ...s, valores } : s
    })
    if (cambio) { setSecciones(nuevas); marcarCambio() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // VD-05 · Lo que el sistema calcula se recalcula al cambiar cualquier dato del que depende y se guarda en sus columnas de siempre.
  useEffect(() => {
    if (soloLectura) return
    const calc = calcularVisita(secciones)
    let cambio = false
    const nuevas = secciones.map(s => {
      const dif = calc[s.clave]
      if (!dif || s.origenFijoClave) return s
      const idDe = (clave) => s.columnas.find(c => c.clave === clave)?.idColumna
      if (s.tipo === 'TABLA') {
        const fila = s.filas[0]
        if (!fila) return s
        const valores = { ...fila.valores }
        let mio = false
        Object.entries(dif).forEach(([clave, v]) => { const id = idDe(clave); if (id !== undefined && (valores[id] ?? '') !== v) { valores[id] = v; cambio = true; mio = true } })
        return mio ? { ...s, filas: [{ ...fila, valores }, ...s.filas.slice(1)] } : s
      }
      const valores = { ...s.valores }
      let mio = false
      Object.entries(dif).forEach(([clave, v]) => { const id = idDe(clave); if (id !== undefined && (valores[id] ?? '') !== v) { valores[id] = v; cambio = true; mio = true } })
      return mio ? { ...s, valores } : s
    })
    if (cambio) { setSecciones(nuevas); marcarCambio() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secciones])

  // Un cambio que el visitador hace sobre lo que llegó de la solicitud queda como novedad: el gestor y el programador se enteran.
  const registrarNovedadVisita = async (clave, descripcion) => {
    try {
      if (!tiposNovedad.current) tiposNovedad.current = (await asignacionService.novedades(idServicioSubproceso)).tipos ?? []
      const tipo = tiposNovedad.current.find(t => t.clave === clave)
      if (!tipo) throw new Error('sin-tipo')
      await asignacionService.registrarNovedad(idServicioSubproceso, tipo.idTipoNovedad, descripcion)
      return true
    } catch (err) {
      error(err.response?.data?.mensaje ?? 'No se pudo avisar al gestor. Inténtalo de nuevo.')
      throw err
    }
  }

  // Enciende o apaga las secciones de un módulo. Al apagar, sus filas se guardan aparte y la sección sale vacía.
  const fijarSecciones = (claves, activar) => {
    marcarCambio()
    const ids = secciones.filter(s => claves.includes(s.clave) && !s.origenFijoClave).map(s => s.idSeccion)
    // Apagar = «quitar sección» (opcional desde V244): el servidor puede borrar sus filas. Encender la vuelve a incluir.
    setOmitidas(prev => { const n = new Set(prev); ids.forEach(id => (activar ? n.delete(id) : n.add(id))); return n })
    setSecciones(prev => prev.map(s => {
      if (!ids.includes(s.idSeccion)) return s
      if (activar) {
        if (s.filas.length > 0 || PADRE_DE_FOTOS[s.clave]) return s
        const guardadas = ocultos.current[s.idSeccion]
        const filas = guardadas?.length ? guardadas : [{ localId: `vd-${Date.now()}-${s.idSeccion}`, idFila: null, etiqueta: '', valores: {}, justificaciones: {} }]
        return { ...s, filas }
      }
      if (s.filas.length > 0) ocultos.current[s.idSeccion] = s.filas
      return { ...s, filas: [] }
    }))
  }
  const estaActivo = (claves) => claves.filter(c => !PADRE_DE_FOTOS[c]).every(c => { const s = secciones.find(x => x.clave === c && !x.origenFijoClave); return !s || s.filas.length > 0 })
  const modulos = MODULOS.map(m => ({ nombre: m.nombre, activo: estaActivo(m.secs) }))

  const cambiarModulo = (nombre, activar) => {
    const m = MODULOS.find(x => x.nombre === nombre)
    fijarSecciones(m.secs, activar)
    registrarNovedadVisita('VISITA_CAMBIO_CONFIGURACION', `Módulo ${nombre}: ${activar ? 'activado' : 'desactivado'} por el visitador.`).catch(() => {})
  }
  const cambiarConfig = (clave, etiqueta, antes, nuevo) => {
    // La clase y el tipo encienden solos lo que les corresponde.
    if (clave === 'v_clase') fijarSecciones(SECS_RUTINA, nuevo === 'Rutina')
    if (clave === 'v_tipo') fijarSecciones(SECS_VIRTUAL, nuevo === 'Virtual')
    if (tieneValor(antes)) registrarNovedadVisita('VISITA_CAMBIO_CONFIGURACION', `${etiqueta}: de «${antes}» a «${nuevo}», cambiado por el visitador.`).catch(() => {})
  }
  const avisarCargo = async (real) => {
    await registrarNovedadVisita('VISITA_CARGO_NO_COINCIDE', `El cargo de la solicitud es «${inicial.cargo ?? ''}» y el visitador observó: ${real}.`)
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

  const [objetivo, setObjetivo] = useState(null)
  const irA = (id, clave = null) => {
    setTemaId(id)
    setVerCaso(false)
    setObjetivo(clave ? { id, clave, n: Date.now() } : null)
    if (!clave) inicioRef.current?.scrollIntoView({ block: 'start' })
  }
  // VD-08 · Un faltante lleva a la pregunta exacta: se desplaza hasta ella y la resalta unos segundos.
  useEffect(() => {
    if (!objetivo) return undefined
    const t = setTimeout(() => {
      const el = document.querySelector(`[data-clave="${objetivo.clave}"]`) ?? inicioRef.current
      el?.scrollIntoView({ block: 'center', behavior: 'smooth' })
      if (el && el !== inicioRef.current) {
        el.classList.add('ring-2', 'ring-amber-500', 'bg-amber-50')
        setTimeout(() => el.classList.remove('ring-2', 'ring-amber-500', 'bg-amber-50'), 3000)
      }
    }, 80)
    return () => clearTimeout(t)
  }, [objetivo])

  // Antes de generar la vista o enviar: lo último que escribió ya está guardado (true) o se avisa y no se sigue (false).
  const asegurarGuardado = async () => (sucio ? guardar(true) : true)

  const enviar = async () => {
    setEnviando(true)
    try {
      // Profesional encargado y fecha de cierre: los pone el sistema al enviar.
      const hoy = new Date()
      const fechaCierre = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`
      const nuevas = secciones.map(x => {
        if (x.clave !== 'concepto_final') return x
        const cNombre = x.columnas.find(c => c.clave === 'analista_nombre'), cFecha = x.columnas.find(c => c.clave === 'analista_fecha')
        if (!cNombre || !cFecha) return x
        return { ...x, valores: { ...x.valores, [cNombre.idColumna]: inicial.nombreProfesionalEncargado || x.valores[cNombre.idColumna] || '', [cFecha.idColumna]: fechaCierre } }
      })
      setSecciones(nuevas)
      cambios.current += 1
      if (!(await guardar(true, nuevas))) return
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
  const protocolo = secciones.find(s => s.clave === 'protocolo' && !s.origenFijoClave)
  // Egresos mayores que los ingresos y «Economía equilibrada»: se avisa para revisar, sin bloquear.
  const econ = useMemo(() => calcularVisita(secciones)._economia, [secciones])
  const estadoEconomia = (() => {
    const e = secciones.find(s => s.clave === 'economia')
    const c = e?.columnas.find(x => x.clave === 'eco_estado')
    return c ? e.valores[c.idColumna] : ''
  })()
  const avisoEconomia = econ?.hayEg && econ?.hayIng && econ.totalEg > econ.totalIng && estadoEconomia === 'Economía equilibrada'
    ? 'Los egresos superan los ingresos y elegiste «Economía equilibrada». Revisa el estado de la economía.' : null
  const refsProtocolo = !protocolo ? [] : PREGUNTAS_PROTOCOLO.map(p => {
    const c = protocolo.columnas.find(x => x.clave === `ps_${p.n}_ref`)
    return { n: p.n, num: numerosProtocolo()[p.n], ref: c ? protocolo.valores[c.idColumna] : '' }
  }).filter(r => tieneValor(r.ref))
  const valorDe = (clave) => { const c = datosGenerales?.columnas.find(x => x.clave === clave); return c ? datosGenerales.valores[c.idColumna] : null }
  const fechaCorta = (iso) => (iso ? new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: 'numeric', minute: '2-digit' }) : null)
  const partes = partesDelTema(tema, secciones)

  const propsParte = (p) => ({
    parte: { ...p, columnas: p.columnasVista ?? p.columnas }, soloLectura: soloLectura || p.soloLectura, idServicioSubproceso, secciones, frases: {}, temasCliente: [], declarado: {}, aplicar: NOOP,
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

      <div className="mb-3" role="group" aria-label="Avance de la visita">
        <p className="text-xs text-gray-800 mb-1">Avance: {avanceLlenos} de {avanceTotal} campos diligenciados</p>
        <div className="h-2 bg-gray-200 rounded" role="progressbar" aria-valuemin={0} aria-valuemax={100}
          aria-valuenow={avanceTotal ? Math.round(avanceLlenos / avanceTotal * 100) : 0} aria-label="Avance de la visita">
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
            {verCaso && <PanelCaso ficha={ficha} nota={inicial.observacionesAsignacion} resumen={resumen} />}
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
            <PanelCaso ficha={ficha} nota={inicial.observacionesAsignacion} resumen={resumen} />
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

              {tema.id === 'generales' && datosGenerales && (
                <DatosGeneralesVisita seccion={datosGenerales} soloLectura={soloLectura} criticidad={valorDe('ev_criticidad')} cargoSolicitud={inicial.cargo}
                  modulos={modulos} onCampo={editarCampo} onModulo={cambiarModulo} onCambioConfig={cambiarConfig} onAvisarCargo={avisarCargo}
                  cita={inicial.ciudadEvaluado ?? ''} />
              )}
              {tema.id === 'protocolo' && protocolo && <ProtocoloVisita seccion={protocolo} soloLectura={soloLectura} onCampo={editarCampo} />}
              {partes.filter(p => (tema.id !== 'generales' || p.seccion.clave !== 'datos_generales') && (tema.id !== 'protocolo' || p.seccion.clave !== 'protocolo')).map((p, i) => {
                // VD-06: un módulo inactivo no se muestra (se enciende en «Datos generales»).
                if (p.modulo && !p.activo) return null
                return (
                  <div key={i}>
                    {p.modulo && (
                      <h3 className="text-sm font-bold text-purple-800 mb-2">
                        Módulo {p.modulo}{p.confidencial && <span className="ml-2 text-xs font-medium text-purple-700">Confidencial · lo registra Polygraph</span>}
                      </h3>
                    )}
                    {p.seccion.clave === 'teletrabajo' && p.seccion.filas[0] && (() => {
                      const sec = p.seccion, fila = sec.filas[0]
                      const porClave = Object.fromEntries(sec.columnas.map(c => [c.clave, c]))
                      const valores = Object.fromEntries(sec.columnas.map(c => [c.clave, fila.valores[c.idColumna]]))
                      return <TeletrabajoVisita valores={valores} soloLectura={soloLectura} onValor={(clave, v) => { const c = porClave[clave]; if (c) editarCeldaFila(sec.idSeccion, fila.localId, c.idColumna, v) }} />
                    })()}
                    {FOTOS[p.seccion.clave] && (
                      <FotosVisita seccion={p.seccion} config={FOTOS[p.seccion.clave]} idServicioSubproceso={idServicioSubproceso} soloLectura={soloLectura}
                        onCambiarFilas={cambiarFilas} onError={error} />
                    )}
                    {p.seccion.clave === 'egresos_hogar'
                      ? <EgresosVisita seccion={p.seccion} integrantes={integrantesDe(secciones.find(x => x.clave === 'familia_convive'))} soloLectura={soloLectura} onCelda={editarCeldaFila} onAgregar={agregarFila} />
                      : !FOTOS[p.seccion.clave] && (p.columnasVista ?? p.columnas).length > 0 && <Parte {...propsParte(p)} />}
                    {p.seccion.clave === 'economia' && avisoEconomia && (
                      <p role="alert" className="mt-3 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2">{avisoEconomia}</p>
                    )}
                    {INDICADORES[p.seccion.clave] && (() => {
                      const sec = p.seccion
                      const fila = sec.tipo === 'TABLA' ? sec.filas[0] : null
                      if (sec.tipo === 'TABLA' && !fila) return null
                      const porClave = Object.fromEntries(sec.columnas.map(c => [c.clave, c]))
                      const valores = Object.fromEntries(sec.columnas.map(c => [c.clave, (fila ? fila.valores : sec.valores)[c.idColumna]]))
                      const poner = (clave, v) => {
                        const c = porClave[clave]
                        if (!c) return
                        if (fila) editarCeldaFila(sec.idSeccion, fila.localId, c.idColumna, v)
                        else editarCampo(sec.idSeccion, c.idColumna, v)
                      }
                      return <IndicadoresVisita claveSeccion={sec.clave} valores={valores} soloLectura={soloLectura} onValor={poner} />
                    })()}
                  </div>
                )
              })}

              {tema.informe && (
                <>
                  <SoportesInforme idServicioSubproceso={idServicioSubproceso} secciones={secciones} omitidas={omitidas} />
                  {refsProtocolo.length > 0 && (
                    <section aria-labelledby="refs-protocolo">
                      <h3 id="refs-protocolo" className="text-sm font-bold text-gray-900 mb-2">Protocolo de seguridad · referencias (las calcula el sistema)</h3>
                      <ul className="text-sm text-gray-800 space-y-0.5">{refsProtocolo.map(r => <li key={r.n}>Pregunta {r.num}: {r.ref}</li>)}</ul>
                    </section>
                  )}
                  <RevisionEnvio etiquetaEnvio="Marcar como completado" idServicioSubproceso={idServicioSubproceso} faltantes={faltantes} onIrA={irA} asegurarGuardado={asegurarGuardado}
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

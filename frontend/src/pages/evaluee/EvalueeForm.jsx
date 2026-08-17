import { useState, useEffect, useRef, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import evaluadoService from '../../services/evaluadoService'
import WorkTimeline from '../../components/WorkTimeline'
import CiudadSelect from '../../components/CiudadSelect'

const PASOS = ['Autorización', 'Datos personales', 'Educación', 'Experiencia laboral', 'Referencias', 'Documentos', 'Revisión']

// Sistema educativo colombiano: categoría → niveles específicos que aplican dentro de esa categoría
const NIVELES_EDUCATIVOS = {
  'Preescolar': ['Preescolar'],
  'Básica (Bachillerato)': ['Básica (Bachillerato)'],
  'Pregrado': ['Técnica profesional', 'Tecnológica', 'Universitaria (profesional)'],
  'Posgrado': ['Especialización', 'Maestría', 'Doctorado'],
}
const CATEGORIAS_EDUCATIVAS = Object.keys(NIVELES_EDUCATIVOS)

function categoriaDeNivel(nivel) {
  return CATEGORIAS_EDUCATIVAS.find((cat) => NIVELES_EDUCATIVOS[cat].includes(nivel)) || ''
}

// Documentos por servicio — se seleccionan mediante coincidencia de palabras clave en el nombre del servicio
const DOCS_CONFIG = [
  { id: 'cedula', nombre: 'Cédula por ambas caras', obligatorio: true,
    palabrasClave: ['antecedentes', 'estudio de seguridad', 'estudio básico'] },
  { id: 'acta_diploma', nombre: 'Acta o diploma del último estudio', obligatorio: true,
    palabrasClave: ['validación académica', 'estudio de seguridad'] },
  { id: 'certificados_laborales', nombre: 'Certificados laborales', obligatorio: true,
    palabrasClave: ['validación laboral', 'estudio de seguridad'] },
  { id: 'sabana_pensional', nombre: 'Sábana pensional', obligatorio: true,
    palabrasClave: ['validación laboral'] },
  { id: 'hoja_vida', nombre: 'Hoja de vida actualizada', obligatorio: true,
    palabrasClave: ['visita domiciliaria', 'estudio de seguridad'] },
  { id: 'autorizacion_datos', nombre: 'Autorización manejo de datos', obligatorio: false,
    palabrasClave: ['estudio de seguridad'] },
  { id: 'libreta_militar', nombre: 'Libreta militar (si aplica)', obligatorio: false,
    palabrasClave: ['estudio de seguridad'] },
  { id: 'referencias_doc', nombre: 'Referencias personales (formulario)', obligatorio: false,
    palabrasClave: ['estudio de seguridad'] },
  { id: 'autorizacion_poligrafo', nombre: 'Autorización de polígrafo firmada', obligatorio: true,
    palabrasClave: ['polígrafo', 'poligrafo', 'poligrafía', 'poligrafias'] },
]

const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none transition-shadow'
const selectCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none bg-white transition-shadow'
const smallInputCls = 'w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:ring-1 focus:ring-primary-500 focus:outline-none'
const smallSelectCls = 'w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:ring-1 focus:ring-primary-500 focus:outline-none bg-white'

function Requerido() { return <span className="text-red-500">*</span> }

function Campo({ label, requerido, error, children }) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">
        {label} {requerido && <Requerido />}
      </label>
      {children}
      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
    </div>
  )
}

function ResumenErrores({ errores }) {
  if (!errores || errores.length === 0) return null
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3">
      <p className="text-sm font-medium text-red-700 mb-1">Faltan datos obligatorios para continuar:</p>
      <ul className="text-xs text-red-600 list-disc list-inside space-y-0.5">
        {errores.map((e, i) => <li key={i}>{e}</li>)}
      </ul>
    </div>
  )
}

function IconoPaso({ estado }) {
  if (estado === 'completo') {
    return (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
      </svg>
    )
  }
  if (estado === 'incompleto') return <span className="text-[13px] font-bold">!</span>
  return null
}

function EvalueeForm() {
  const { token } = useParams()
  const [paso, setPaso] = useState(0)
  const [info, setInfo] = useState(null)
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [completado, setCompletado] = useState(false)
  const [pasosIntentados, setPasosIntentados] = useState({})
  const autoSaveRef = useRef(null)

  const [form, setForm] = useState({
    autorizacionDatos: false,
    fechaNacimiento: '', lugarNacimiento: '',
    idCiudadNacimiento: null, idCiudadResidencia: null,
    estadoCivil: '', rh: '', nivelEducativo: '',
    libretaMilitar: '', visa: '', pasaporte: '',
    fondoPensiones: '', eps: '',
    estrato: '', barrio: '', direccion: '',
    telefonoFijo: '', celular: '', email: '',
    educacion: [],
    experienciaLaboral: [],
    referencias: [],
  })

  const [inactividades, setInactividades] = useState([])
  const [justificaciones, setJustificaciones] = useState({}) // { 'fechaInicio_fechaFin': string }
  const [archivos, setArchivos] = useState({}) // { docId: { idDocumento, nombreArchivo, subiendo, error } }

  // Documentos requeridos según el proceso del evaluado
  const documentosRequeridos = useMemo(() => {
    if (!info?.proceso) {
      // Sin información de proceso: mostrar todos como opcionales
      return DOCS_CONFIG.map(d => ({ ...d, obligatorio: false }))
    }
    const procesoLower = info.proceso.toLowerCase()
    return DOCS_CONFIG.filter(doc =>
      doc.palabrasClave.some(kw => procesoLower.includes(kw))
    )
  }, [info])

  const docsObligatoriosPendientes = documentosRequeridos.filter(d => d.obligatorio && !archivos[d.id]?.idDocumento)

  useEffect(() => {
    evaluadoService.validarLink(token)
      .then((r) => setInfo(r.data))
      .catch((e) => setError(e.response?.status === 410
        ? 'Este link ya fue utilizado o ha expirado.'
        : 'Link inválido o no encontrado.'))
  }, [token])

  // Los documentos ya subidos sí se precargan: a diferencia de las respuestas del formulario,
  // un archivo subido ya es un hecho consumado en el servidor, no un borrador.
  useEffect(() => {
    if (!info) return
    evaluadoService.listarDocumentos(token)
      .then((r) => {
        const mapa = {}
        r.data.forEach((d) => { mapa[d.tipoDocumento] = { idDocumento: d.id, nombreArchivo: d.nombreArchivo } })
        setArchivos(mapa)
      })
      .catch(() => {})
  }, [info, token])

  // Al abrir un link (aunque sea uno nuevo generado tras un diligenciamiento previo) se recupera
  // la información ya registrada del candidato, pero SIEMPRE se arranca desde el paso 0: el
  // candidato puede revisar y corregir todo de nuevo, no se le salta al final. Mientras la carga
  // no termine, NINGÚN guardado (autosave ni cambio de paso) puede salir: si el formulario
  // todavía está en blanco y se dispara un guardado, pisa en el servidor los datos reales con
  // vacíos (por eso "se borraba" al volver a abrir el link).
  const restauradoRef = useRef(false)
  useEffect(() => {
    if (!info) return
    evaluadoService.obtenerFormulario(token)
      .then((r) => {
        const data = r.data
        if (!data) return
        setForm((prev) => ({
          ...prev,
          autorizacionDatos: data.autorizacionDatos ?? prev.autorizacionDatos,
          fechaNacimiento: data.fechaNacimiento ?? '',
          lugarNacimiento: data.lugarNacimiento ?? '',
          idCiudadNacimiento: data.idCiudadNacimiento ?? null,
          idCiudadResidencia: data.idCiudadResidencia ?? null,
          estadoCivil: data.estadoCivil ?? '',
          nivelEducativo: data.nivelEducativo ?? '',
          direccion: data.direccion ?? '',
          barrio: data.barrio ?? '',
          estrato: data.estrato ?? '',
          email: data.email ?? '',
          celular: data.celular ?? '',
          rh: data.rh ?? '',
          libretaMilitar: data.libretaMilitar ?? '',
          visa: data.visa ?? '',
          pasaporte: data.pasaporte ?? '',
          fondoPensiones: data.fondoPensiones ?? '',
          eps: data.eps ?? '',
          telefonoFijo: data.telefonoFijo ?? '',
          educacion: data.educacion?.length ? data.educacion : prev.educacion,
          experienciaLaboral: data.experienciaLaboral?.length ? data.experienciaLaboral : prev.experienciaLaboral,
          referencias: data.referencias?.length ? data.referencias : prev.referencias,
        }))
        if (data.justificacionesInactividad) setJustificaciones(data.justificacionesInactividad)
        // No se restaura pasoActual a propósito: el candidato siempre revisa desde el inicio.
      })
      .catch(() => {})
      .finally(() => { restauradoRef.current = true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [info, token])

  useEffect(() => {
    if (!info || paso === 0) return
    autoSaveRef.current = setInterval(() => {
      if (!restauradoRef.current) return
      evaluadoService.guardarProgreso(token, { ...form, pasoActual: paso, justificacionesInactividad: justificaciones }).catch(() => {})
    }, 120000)
    return () => clearInterval(autoSaveRef.current)
  }, [form, info, paso, token, justificaciones])

  const primerPasoRef = useRef(true)
  useEffect(() => {
    if (primerPasoRef.current) { primerPasoRef.current = false; return }
    if (!info || !restauradoRef.current) return
    evaluadoService.guardarProgreso(token, { ...form, pasoActual: paso, justificacionesInactividad: justificaciones }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paso])

  useEffect(() => {
    if (form.experienciaLaboral.length === 0) { setInactividades([]); return }
    const sorted = [...form.experienciaLaboral].sort((a, b) => new Date(a.fechaInicio) - new Date(b.fechaInicio))
    const gaps = []
    let anterior = null
    for (const exp of sorted) {
      if (anterior) {
        const fin = anterior.fechaFin ? new Date(anterior.fechaFin) : new Date()
        const inicio = new Date(exp.fechaInicio)
        const dias = Math.floor((inicio - fin) / 86400000)
        if (dias > 0) gaps.push({
          fechaInicio: anterior.fechaFin || new Date().toISOString().slice(0, 10),
          fechaFin: exp.fechaInicio,
          diasInactivo: dias,
        })
      }
      anterior = exp
    }
    setInactividades(gaps)
  }, [form.experienciaLaboral])

  const f = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))

  const agregarEducacion = () =>
    setForm((prev) => ({
      ...prev,
      educacion: [...prev.educacion, {
        categoriaNivel: '', nivel: '', institucion: '', titulo: '',
        fechaInicio: '', fechaFin: '', enCurso: false, ciudad: '',
      }],
    }))

  const actualizarEducacion = (i, campo, valor) =>
    setForm((prev) => {
      const e = [...prev.educacion]
      e[i] = { ...e[i], [campo]: valor }
      return { ...prev, educacion: e }
    })

  const actualizarCategoriaEducacion = (i, categoria) => {
    const opciones = NIVELES_EDUCATIVOS[categoria] || []
    setForm((prev) => {
      const e = [...prev.educacion]
      e[i] = { ...e[i], categoriaNivel: categoria, nivel: opciones.length === 1 ? opciones[0] : '' }
      return { ...prev, educacion: e }
    })
  }

  const actualizarEstadoEstudio = (i, enCurso) =>
    setForm((prev) => {
      const e = [...prev.educacion]
      e[i] = { ...e[i], enCurso, fechaFin: enCurso ? '' : e[i].fechaFin }
      return { ...prev, educacion: e }
    })

  const eliminarEducacion = (i) =>
    setForm((prev) => ({ ...prev, educacion: prev.educacion.filter((_, idx) => idx !== i) }))

  const agregarExperiencia = () =>
    setForm((prev) => ({
      ...prev,
      experienciaLaboral: [...prev.experienciaLaboral, {
        empresa: '', cargo: '', fechaInicio: '', fechaFin: '',
        laboraActualmente: false, ciudad: '', telefonoEmpresa: '',
        motivoRetiro: '', nombreJefe: '', cargoJefe: '',
      }],
    }))

  const actualizarExperiencia = (i, campo, valor) =>
    setForm((prev) => {
      const e = [...prev.experienciaLaboral]
      e[i] = { ...e[i], [campo]: valor }
      return { ...prev, experienciaLaboral: e }
    })

  const eliminarExperiencia = (i) =>
    setForm((prev) => ({ ...prev, experienciaLaboral: prev.experienciaLaboral.filter((_, idx) => idx !== i) }))

  const agregarReferencia = () =>
    setForm((prev) => ({
      ...prev,
      referencias: [...prev.referencias, { nombre: '', parentesco: '', telefono: '', tiempoConocimiento: '' }],
    }))

  const actualizarReferencia = (i, campo, valor) =>
    setForm((prev) => {
      const r = [...prev.referencias]
      r[i] = { ...r[i], [campo]: valor }
      return { ...prev, referencias: r }
    })

  const eliminarReferencia = (i) =>
    setForm((prev) => ({ ...prev, referencias: prev.referencias.filter((_, idx) => idx !== i) }))

  const manejarArchivo = async (docId, file) => {
    if (!file) return
    setArchivos(prev => ({ ...prev, [docId]: { subiendo: true } }))
    try {
      const { data } = await evaluadoService.subirDocumento(token, docId, file)
      setArchivos(prev => ({ ...prev, [docId]: { idDocumento: data.id, nombreArchivo: data.nombreArchivo } }))
    } catch (e) {
      setArchivos(prev => ({ ...prev, [docId]: { error: e.response?.data?.mensaje || 'No se pudo cargar el archivo' } }))
    }
  }

  const quitarArchivo = async (docId) => {
    const actual = archivos[docId]
    setArchivos(prev => { const n = { ...prev }; delete n[docId]; return n })
    if (actual?.idDocumento) {
      try { await evaluadoService.eliminarDocumento(token, actual.idDocumento) } catch { /* ya se quitó de la vista */ }
    }
  }

  /* ─── Validación por paso ─── */
  const gapsSinJustificar = inactividades.filter(g => g.diasInactivo > 30 && !justificaciones[gapsClave(g)])

  const erroresPorPaso = useMemo(() => {
    const datosPersonales = []
    if (!form.fechaNacimiento) datosPersonales.push('Fecha de nacimiento')
    if (!form.idCiudadNacimiento) datosPersonales.push('Ciudad de nacimiento')
    if (!form.idCiudadResidencia) datosPersonales.push('Ciudad de residencia')
    if (!form.estadoCivil) datosPersonales.push('Estado civil')
    if (!form.rh) datosPersonales.push('RH (grupo sanguíneo)')
    if (!form.celular) datosPersonales.push('Celular')
    if (!form.email) datosPersonales.push('Email')
    if (!form.direccion) datosPersonales.push('Dirección de residencia')
    if (!form.barrio) datosPersonales.push('Barrio de residencia')
    if (!form.estrato) datosPersonales.push('Estrato')

    const educacion = []
    if (form.educacion.length === 0) educacion.push('Agrega al menos un registro de educación')
    form.educacion.forEach((e, i) => {
      if (!e.nivel) educacion.push(`Educación #${i + 1}: nivel educativo`)
      if (!e.institucion) educacion.push(`Educación #${i + 1}: institución`)
      if (!e.ciudad) educacion.push(`Educación #${i + 1}: ciudad`)
      if (!e.fechaInicio) educacion.push(`Educación #${i + 1}: fecha de inicio`)
      if (!e.enCurso && !e.fechaFin) educacion.push(`Educación #${i + 1}: fecha de fin`)
    })

    const experiencia = []
    if (form.experienciaLaboral.length === 0) experiencia.push('Agrega al menos una experiencia laboral')
    form.experienciaLaboral.forEach((exp, i) => {
      if (!exp.empresa) experiencia.push(`Experiencia #${i + 1}: empresa`)
      if (!exp.cargo) experiencia.push(`Experiencia #${i + 1}: cargo`)
      if (!exp.ciudad) experiencia.push(`Experiencia #${i + 1}: ciudad`)
      if (!exp.fechaInicio) experiencia.push(`Experiencia #${i + 1}: fecha de inicio`)
    })
    if (gapsSinJustificar.length > 0) experiencia.push('Justifica los tiempos sin empleo mayores a 30 días')

    const referencias = []
    if (form.referencias.length < 2) referencias.push('Se requieren mínimo 2 referencias personales')
    form.referencias.forEach((r, i) => {
      if (!r.nombre) referencias.push(`Referencia #${i + 1}: nombre`)
      if (!r.telefono) referencias.push(`Referencia #${i + 1}: teléfono`)
    })

    const documentos = docsObligatoriosPendientes.map(d => `Falta cargar: ${d.nombre}`)

    return [[], datosPersonales, educacion, experiencia, referencias, documentos, []]
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form, gapsSinJustificar, docsObligatoriosPendientes])

  const pasoValido = (i) => i === 0 ? form.autorizacionDatos : erroresPorPaso[i].length === 0

  const intentarAvanzar = () => {
    if (pasoValido(paso)) {
      setPaso((p) => p + 1)
    } else {
      setPasosIntentados((prev) => ({ ...prev, [paso]: true }))
    }
  }

  const mostrarErroresPaso = pasosIntentados[paso] && erroresPorPaso[paso]?.length > 0

  const enviar = async () => {
    setEnviando(true)
    setError(null)
    try {
      await evaluadoService.enviarFormulario(token, {
        ...form,
        justificacionesInactividad: justificaciones,
      })
      setCompletado(true)
    } catch (e) {
      setError(e.response?.data?.mensaje || 'Error al enviar el formulario')
    } finally {
      setEnviando(false)
    }
  }

  if (error) return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="text-center p-8">
        <p className="text-red-600 font-medium mb-2">{error}</p>
        <p className="text-gray-500 text-sm">Si crees que es un error, contacta a Polygraph Service.</p>
      </div>
    </div>
  )

  if (!info) return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <p className="text-gray-500">Validando enlace...</p>
    </div>
  )

  if (completado) return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="text-center p-8 max-w-md">
        <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <span className="text-3xl text-green-600">✓</span>
        </div>
        <h2 className="text-xl font-bold text-gray-900 mb-2">Formulario completado</h2>
        <p className="text-gray-500 text-sm">Tu hoja de vida fue enviada exitosamente a Polygraph Service. Te contactaremos pronto.</p>
      </div>
    </div>
  )

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-50 to-slate-100 py-8 px-4">
      <div className="max-w-2xl mx-auto">
        {/* Header */}
        <div className="text-center mb-6">
          <div className="inline-flex items-center gap-2 mb-1">
            <span className="w-2 h-2 rounded-full bg-primary-500" />
            <h1 className="text-xl font-bold text-gray-900">Polygraph Service</h1>
          </div>
          <p className="text-gray-500 text-sm mt-1">
            Hola, <strong>{info.nombresEvaluado} {info.apellidosEvaluado}</strong>. Por favor completa tu hoja de vida.
          </p>
          {info.cargo && <p className="text-gray-400 text-xs mt-0.5">Cargo: {info.cargo}</p>}
        </div>

        {/* Stepper */}
        <div className="flex items-center mb-2">
          {PASOS.map((nombre, i) => {
            const activo = i === paso
            const completo = i < paso && pasoValido(i)
            const incompleto = i < paso && !pasoValido(i)
            const estado = completo ? 'completo' : incompleto ? 'incompleto' : activo ? 'activo' : 'pendiente'
            return (
              <div key={nombre} className="flex items-center flex-1 last:flex-none">
                <div
                  title={nombre}
                  className={`w-7 h-7 shrink-0 rounded-full flex items-center justify-center text-xs font-semibold transition-colors ${
                    estado === 'completo' ? 'bg-green-500 text-white' :
                    estado === 'incompleto' ? 'bg-amber-400 text-white' :
                    estado === 'activo' ? 'bg-primary-600 text-white ring-4 ring-primary-100' :
                    'bg-gray-200 text-gray-500'
                  }`}>
                  <IconoPaso estado={estado === 'activo' || estado === 'pendiente' ? null : estado} />
                  {(estado === 'activo' || estado === 'pendiente') && (i + 1)}
                </div>
                {i < PASOS.length - 1 && (
                  <div className={`h-0.5 flex-1 mx-1 rounded transition-colors ${i < paso ? 'bg-primary-300' : 'bg-gray-200'}`} />
                )}
              </div>
            )
          })}
        </div>
        <p className="text-xs text-gray-400 text-center mb-4">Paso {paso + 1} de {PASOS.length}: <span className="font-medium text-gray-600">{PASOS[paso]}</span></p>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">

          {/* Paso 0: Autorización */}
          {paso === 0 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-800">Autorización de datos</h2>
              <div className="bg-gray-50 rounded-lg p-4 text-sm text-gray-600 leading-relaxed max-h-48 overflow-y-auto">
                <p>Yo, <strong>{info.nombresEvaluado} {info.apellidosEvaluado}</strong>, identificado(a) con cédula <strong>{info.cedulaEvaluado}</strong>, autorizo de manera libre, previa, expresa e informada a <strong>Polygraph Service Ltda</strong> para recolectar, almacenar, usar, circular, suprimir y en general tratar mis datos personales con la finalidad de adelantar el proceso de verificación de información.</p>
                <p className="mt-2">Esta autorización incluye datos de naturaleza sensible y financiera conforme a la Ley 1581 de 2012 y sus decretos reglamentarios.</p>
              </div>
              <label className="flex items-start gap-3 cursor-pointer">
                <input type="checkbox" className="mt-0.5 accent-primary-600 w-4 h-4"
                  checked={form.autorizacionDatos}
                  onChange={(e) => setForm({ ...form, autorizacionDatos: e.target.checked })} />
                <span className="text-sm text-gray-700">Acepto la autorización para el manejo de mis datos personales <Requerido /></span>
              </label>
              {pasosIntentados[0] && !form.autorizacionDatos && (
                <p className="text-xs text-red-500">Debes aceptar para continuar.</p>
              )}
            </div>
          )}

          {/* Paso 1: Datos personales */}
          {paso === 1 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-800">Datos personales</h2>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[1] : []} />
              <div className="grid grid-cols-2 gap-4">
                <Campo label="Fecha de nacimiento" requerido>
                  <input type="date" className={inputCls} value={form.fechaNacimiento || ''} onChange={f('fechaNacimiento')} />
                </Campo>
                <Campo label="Ciudad de nacimiento" requerido>
                  <CiudadSelect value={form.idCiudadNacimiento}
                    onChange={(idCiudad) => setForm(p => ({ ...p, idCiudadNacimiento: idCiudad }))} />
                </Campo>
                <Campo label="Estado civil" requerido>
                  <select className={selectCls} value={form.estadoCivil || ''} onChange={f('estadoCivil')}>
                    <option value="">Selecciona...</option>
                    {['Soltero', 'Casado', 'Unión libre', 'Divorciado', 'Viudo', 'Separado'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </Campo>
                <Campo label="RH (grupo sanguíneo)" requerido>
                  <select className={selectCls} value={form.rh || ''} onChange={f('rh')}>
                    <option value="">Selecciona...</option>
                    {['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </Campo>
                <Campo label="Celular" requerido>
                  <input type="text" className={inputCls} value={form.celular || ''} onChange={f('celular')} />
                </Campo>
                <Campo label="Teléfono fijo">
                  <input type="text" className={inputCls} value={form.telefonoFijo || ''} onChange={f('telefonoFijo')} />
                </Campo>
                <Campo label="Email" requerido>
                  <input type="email" className={inputCls} value={form.email || ''} onChange={f('email')} />
                </Campo>
                <Campo label="Nivel educativo (resumen)">
                  <input type="text" className={inputCls} value={form.nivelEducativo || ''} onChange={f('nivelEducativo')} />
                </Campo>
                <Campo label="Ciudad de residencia" requerido>
                  <CiudadSelect value={form.idCiudadResidencia}
                    onChange={(idCiudad) => setForm(p => ({ ...p, idCiudadResidencia: idCiudad }))} />
                </Campo>
                <Campo label="Estrato" requerido>
                  <select className={selectCls} value={form.estrato || ''} onChange={f('estrato')}>
                    <option value="">Selecciona...</option>
                    {['1', '2', '3', '4', '5', '6'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </Campo>
                <Campo label="Barrio de residencia" requerido>
                  <input type="text" className={inputCls} value={form.barrio || ''} onChange={f('barrio')} />
                </Campo>
                <Campo label="Libreta militar">
                  <input type="text" className={inputCls} value={form.libretaMilitar || ''} onChange={f('libretaMilitar')} />
                </Campo>
                <Campo label="Visa">
                  <input type="text" className={inputCls} value={form.visa || ''} onChange={f('visa')} />
                </Campo>
                <Campo label="Pasaporte">
                  <input type="text" className={inputCls} value={form.pasaporte || ''} onChange={f('pasaporte')} />
                </Campo>
                <Campo label="Fondo de pensiones">
                  <input type="text" className={inputCls} value={form.fondoPensiones || ''} onChange={f('fondoPensiones')} />
                </Campo>
                <Campo label="EPS">
                  <input type="text" className={inputCls} value={form.eps || ''} onChange={f('eps')} />
                </Campo>
                <div className="col-span-2">
                  <Campo label="Dirección de residencia" requerido>
                    <input type="text" className={inputCls} value={form.direccion || ''} onChange={f('direccion')} />
                  </Campo>
                </div>
              </div>
            </div>
          )}

          {/* Paso 2: Educación */}
          {paso === 2 && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-semibold text-gray-800">Educación</h2>
                  <p className="text-xs text-gray-400 mt-0.5">Registra tu formación académica, desde la más reciente.</p>
                </div>
                <button onClick={agregarEducacion} className="text-sm text-primary-600 hover:text-primary-800 font-medium whitespace-nowrap">+ Agregar</button>
              </div>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[2] : []} />
              {form.educacion.length === 0 && (
                <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg">
                  <p className="text-sm text-gray-400">No hay registros educativos.</p>
                  <button onClick={agregarEducacion} className="text-sm text-primary-600 hover:text-primary-800 font-medium mt-2">+ Agregar el primero</button>
                </div>
              )}
              {form.educacion.map((edu, i) => (
                <div key={i} className="border border-gray-200 rounded-lg p-4 space-y-3 border-l-4 border-l-primary-300">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium text-gray-700">{edu.nivel || `Registro ${i + 1}`}</span>
                    <button onClick={() => eliminarEducacion(i)} className="text-xs text-red-500 hover:text-red-700">Eliminar</button>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Categoría <Requerido /></label>
                      <select className={smallSelectCls} value={edu.categoriaNivel || categoriaDeNivel(edu.nivel)}
                        onChange={(e) => actualizarCategoriaEducacion(i, e.target.value)}>
                        <option value="">Selecciona...</option>
                        {CATEGORIAS_EDUCATIVAS.map(cat => <option key={cat} value={cat}>{cat}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Nivel específico <Requerido /></label>
                      <select className={smallSelectCls} value={edu.nivel || ''}
                        disabled={!(edu.categoriaNivel || categoriaDeNivel(edu.nivel))}
                        onChange={(e) => actualizarEducacion(i, 'nivel', e.target.value)}>
                        <option value="">Selecciona...</option>
                        {(NIVELES_EDUCATIVOS[edu.categoriaNivel || categoriaDeNivel(edu.nivel)] || []).map(o => (
                          <option key={o} value={o}>{o}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Institución <Requerido /></label>
                      <input className={smallInputCls} value={edu.institucion || ''} onChange={(e) => actualizarEducacion(i, 'institucion', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Título obtenido</label>
                      <input className={smallInputCls} value={edu.titulo || ''} onChange={(e) => actualizarEducacion(i, 'titulo', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Ciudad <Requerido /></label>
                      <CiudadSelect valueType="nombre" className={smallSelectCls}
                        value={edu.ciudad || ''} onChange={(v) => actualizarEducacion(i, 'ciudad', v)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Estado del estudio</label>
                      <select className={smallSelectCls}
                        value={edu.enCurso ? 'En curso' : (edu.fechaFin ? 'Finalizado' : '')}
                        onChange={(e) => actualizarEstadoEstudio(i, e.target.value === 'En curso')}>
                        <option value="">Selecciona...</option>
                        <option value="En curso">En curso</option>
                        <option value="Finalizado">Finalizado</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha inicio <Requerido /></label>
                      <input type="date" className={smallInputCls} value={edu.fechaInicio || ''} onChange={(e) => actualizarEducacion(i, 'fechaInicio', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha fin {!edu.enCurso && <Requerido />}</label>
                      <input type="date" className={smallInputCls}
                        value={edu.fechaFin || ''}
                        disabled={edu.enCurso}
                        onChange={(e) => actualizarEducacion(i, 'fechaFin', e.target.value)} />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Paso 3: Experiencia laboral */}
          {paso === 3 && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-800">Experiencia laboral</h2>
                <button onClick={agregarExperiencia} className="text-sm text-primary-600 hover:text-primary-800 font-medium">+ Agregar</button>
              </div>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[3] : []} />

              <WorkTimeline experiencias={form.experienciaLaboral} inactividades={inactividades} />

              {/* Justificaciones para tiempos muertos > 30 días */}
              {inactividades.filter(g => g.diasInactivo > 30).length > 0 && (
                <div className="border border-amber-200 rounded-lg p-4 bg-amber-50 space-y-3">
                  <h4 className="text-sm font-semibold text-amber-800">Tiempos sin empleo que requieren justificación</h4>
                  {inactividades.filter(g => g.diasInactivo > 30).map((gap, i) => {
                    const key = gapsClave(gap)
                    return (
                      <div key={i}>
                        <label className="block text-xs text-amber-700 mb-1">
                          Inactividad de <strong>{gap.diasInactivo} días</strong> ({gap.fechaInicio} → {gap.fechaFin}) <Requerido />
                        </label>
                        <textarea
                          rows={2}
                          className="w-full border border-amber-300 rounded px-2 py-1.5 text-sm focus:ring-1 focus:ring-amber-500 focus:outline-none bg-white"
                          placeholder="Explica el motivo de este tiempo sin empleo..."
                          value={justificaciones[key] || ''}
                          onChange={(e) => setJustificaciones(prev => ({ ...prev, [key]: e.target.value }))}
                        />
                      </div>
                    )
                  })}
                </div>
              )}

              {form.experienciaLaboral.length === 0 && (
                <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg">
                  <p className="text-sm text-gray-400">No hay registros de experiencia.</p>
                  <button onClick={agregarExperiencia} className="text-sm text-primary-600 hover:text-primary-800 font-medium mt-2">+ Agregar el primero</button>
                </div>
              )}
              {form.experienciaLaboral.map((exp, i) => (
                <div key={i} className="border border-gray-200 rounded-lg p-4 space-y-3 border-l-4 border-l-primary-300">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium text-gray-700">{exp.empresa || `Empleo ${i + 1}`}</span>
                    <button onClick={() => eliminarExperiencia(i)} className="text-xs text-red-500 hover:text-red-700">Eliminar</button>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Empresa <Requerido /></label>
                      <input className={smallInputCls} value={exp.empresa || ''} onChange={(e) => actualizarExperiencia(i, 'empresa', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Cargo <Requerido /></label>
                      <input className={smallInputCls} value={exp.cargo || ''} onChange={(e) => actualizarExperiencia(i, 'cargo', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Ciudad <Requerido /></label>
                      <CiudadSelect valueType="nombre" className={smallSelectCls}
                        value={exp.ciudad || ''} onChange={(v) => actualizarExperiencia(i, 'ciudad', v)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Teléfono empresa</label>
                      <input className={smallInputCls} value={exp.telefonoEmpresa || ''} onChange={(e) => actualizarExperiencia(i, 'telefonoEmpresa', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha inicio <Requerido /></label>
                      <input type="date" className={smallInputCls}
                        value={exp.fechaInicio || ''} onChange={(e) => actualizarExperiencia(i, 'fechaInicio', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha retiro</label>
                      <input type="date" className={smallInputCls}
                        value={exp.fechaFin || ''} disabled={exp.laboraActualmente}
                        onChange={(e) => actualizarExperiencia(i, 'fechaFin', e.target.value)} />
                    </div>
                    <div className="col-span-2">
                      <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                        <input type="checkbox" checked={exp.laboraActualmente || false}
                          onChange={(e) => actualizarExperiencia(i, 'laboraActualmente', e.target.checked)} />
                        Labora actualmente aquí
                      </label>
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Nombre del jefe</label>
                      <input className={smallInputCls}
                        value={exp.nombreJefe || ''} onChange={(e) => actualizarExperiencia(i, 'nombreJefe', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Cargo del jefe</label>
                      <input className={smallInputCls}
                        value={exp.cargoJefe || ''} onChange={(e) => actualizarExperiencia(i, 'cargoJefe', e.target.value)} />
                    </div>
                    <div className="col-span-2">
                      <label className="block text-xs text-gray-500 mb-1">Motivo de retiro</label>
                      <textarea rows={2} className={smallInputCls}
                        value={exp.motivoRetiro || ''} onChange={(e) => actualizarExperiencia(i, 'motivoRetiro', e.target.value)} />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Paso 4: Referencias personales */}
          {paso === 4 && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-800">Referencias personales</h2>
                <button onClick={agregarReferencia} className="text-sm text-primary-600 hover:text-primary-800 font-medium">+ Agregar</button>
              </div>
              <p className="text-xs text-gray-400">Se requieren mínimo 2 referencias personales.</p>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[4] : []} />
              {form.referencias.length === 0 && (
                <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg">
                  <p className="text-sm text-gray-400">No hay referencias.</p>
                  <button onClick={agregarReferencia} className="text-sm text-primary-600 hover:text-primary-800 font-medium mt-2">+ Agregar la primera</button>
                </div>
              )}
              {form.referencias.map((ref, i) => (
                <div key={i} className="border border-gray-200 rounded-lg p-4 space-y-3 border-l-4 border-l-primary-300">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium text-gray-700">Referencia {i + 1}</span>
                    <button onClick={() => eliminarReferencia(i)} className="text-xs text-red-500">Eliminar</button>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Nombre <Requerido /></label>
                      <input className={smallInputCls} value={ref.nombre || ''} onChange={(e) => actualizarReferencia(i, 'nombre', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Parentesco</label>
                      <input className={smallInputCls} value={ref.parentesco || ''} onChange={(e) => actualizarReferencia(i, 'parentesco', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Teléfono <Requerido /></label>
                      <input className={smallInputCls} value={ref.telefono || ''} onChange={(e) => actualizarReferencia(i, 'telefono', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Tiempo de conocimiento</label>
                      <input className={smallInputCls} value={ref.tiempoConocimiento || ''} onChange={(e) => actualizarReferencia(i, 'tiempoConocimiento', e.target.value)} />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Paso 5: Documentos */}
          {paso === 5 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-800">Documentos requeridos</h2>

              {documentosRequeridos.length === 0 ? (
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 text-sm text-blue-700">
                  No se requieren documentos físicos para los servicios solicitados. Puedes continuar.
                </div>
              ) : (
                <>
                  <p className="text-sm text-gray-500">
                    Carga los siguientes documentos. Los marcados con <Requerido /> son obligatorios para continuar.
                  </p>
                  <div className="space-y-3">
                    {documentosRequeridos.map((doc) => {
                      const estado = archivos[doc.id]
                      const cargado = !!estado?.idDocumento
                      const subiendo = !!estado?.subiendo
                      return (
                        <div
                          key={doc.id}
                          className={`border rounded-lg p-4 transition-colors ${cargado ? 'border-green-300 bg-green-50' : estado?.error ? 'border-red-300 bg-red-50' : 'border-gray-200'}`}>
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium text-gray-800">
                                {doc.nombre}
                                {doc.obligatorio && <span className="ml-1"><Requerido /></span>}
                                {!doc.obligatorio && <span className="text-gray-400 text-xs ml-2">(opcional)</span>}
                              </p>
                              {cargado && (
                                <p className="text-xs text-green-600 mt-0.5 truncate">{estado.nombreArchivo}</p>
                              )}
                              {estado?.error && (
                                <p className="text-xs text-red-600 mt-0.5">{estado.error}</p>
                              )}
                            </div>
                            <span className="text-lg shrink-0">
                              {subiendo ? '⏳' : cargado ? '✅' : estado?.error ? '⚠️' : '⏳'}
                            </span>
                          </div>
                          <div className="mt-3 flex items-center gap-3">
                            <label className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
                              subiendo
                                ? 'text-gray-400 bg-gray-50 border border-gray-200 cursor-not-allowed'
                                : 'text-primary-700 bg-primary-50 border border-primary-200 cursor-pointer hover:bg-primary-100'
                            }`}>
                              <input
                                type="file"
                                className="hidden"
                                disabled={subiendo}
                                accept=".pdf,.jpg,.jpeg,.png"
                                onChange={(e) => manejarArchivo(doc.id, e.target.files?.[0])}
                              />
                              {subiendo ? 'Subiendo...' : cargado ? 'Cambiar archivo' : 'Seleccionar archivo'}
                            </label>
                            {cargado && (
                              <button
                                type="button"
                                onClick={() => quitarArchivo(doc.id)}
                                className="text-xs text-red-500 hover:text-red-700">
                                Quitar
                              </button>
                            )}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                  {mostrarErroresPaso && docsObligatoriosPendientes.length > 0 && (
                    <p className="text-xs text-red-500">
                      Faltan {docsObligatoriosPendientes.length} documento{docsObligatoriosPendientes.length !== 1 ? 's' : ''} obligatorio{docsObligatoriosPendientes.length !== 1 ? 's' : ''} por cargar.
                    </p>
                  )}
                </>
              )}

              {error && <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>}
            </div>
          )}

          {/* Paso 6: Revisión */}
          {paso === 6 && (
            <div className="space-y-6">
              <div>
                <h2 className="text-lg font-semibold text-gray-800">Revisión del formulario</h2>
                <p className="text-sm text-gray-500 mt-1">Verifica que todo esté correcto antes de enviar. Usa "Anterior" para corregir cualquier sección.</p>
              </div>

              {/* Datos personales */}
              <section>
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Datos personales</h3>
                <div className="bg-gray-50 rounded-lg p-4 grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
                  <div><span className="text-gray-400">Nombre: </span><span className="font-medium text-gray-800">{info.nombresEvaluado} {info.apellidosEvaluado}</span></div>
                  <div><span className="text-gray-400">Cédula: </span><span className="text-gray-800">{info.cedulaEvaluado}</span></div>
                  {form.fechaNacimiento && <div><span className="text-gray-400">F. nacimiento: </span><span className="text-gray-800">{form.fechaNacimiento}</span></div>}
                  {form.estadoCivil && <div><span className="text-gray-400">Estado civil: </span><span className="text-gray-800">{form.estadoCivil}</span></div>}
                  {form.rh && <div><span className="text-gray-400">RH: </span><span className="text-gray-800">{form.rh}</span></div>}
                  {form.eps && <div><span className="text-gray-400">EPS: </span><span className="text-gray-800">{form.eps}</span></div>}
                  {form.fondoPensiones && <div><span className="text-gray-400">Pensiones: </span><span className="text-gray-800">{form.fondoPensiones}</span></div>}
                  {form.celular && <div><span className="text-gray-400">Celular: </span><span className="text-gray-800">{form.celular}</span></div>}
                  {form.email && <div><span className="text-gray-400">Email: </span><span className="text-gray-800">{form.email}</span></div>}
                  {form.direccion && <div className="col-span-2"><span className="text-gray-400">Dirección: </span><span className="text-gray-800">{form.direccion}</span></div>}
                </div>
              </section>

              {/* Educación */}
              {form.educacion.length > 0 && (
                <section>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Educación</h3>
                  <div className="overflow-x-auto rounded-lg border border-gray-200">
                    <table className="w-full text-sm">
                      <thead className="bg-gray-50 text-gray-500 text-xs">
                        <tr>
                          <th className="px-3 py-2 text-left">Nivel</th>
                          <th className="px-3 py-2 text-left">Institución</th>
                          <th className="px-3 py-2 text-left">Título</th>
                          <th className="px-3 py-2 text-left">Estado</th>
                          <th className="px-3 py-2 text-left">Fin</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {form.educacion.map((edu, i) => (
                          <tr key={i}>
                            <td className="px-3 py-2 text-gray-800">{edu.nivel || '—'}</td>
                            <td className="px-3 py-2 text-gray-800">{edu.institucion || '—'}</td>
                            <td className="px-3 py-2 text-gray-600">{edu.titulo || '—'}</td>
                            <td className="px-3 py-2 text-gray-600">{edu.enCurso ? 'En curso' : 'Finalizado'}</td>
                            <td className="px-3 py-2 text-gray-600">
                              {edu.enCurso ? 'En curso' : (edu.fechaFin || '—')}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}

              {/* Experiencia laboral */}
              {form.experienciaLaboral.length > 0 && (
                <section>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Experiencia laboral</h3>
                  <WorkTimeline experiencias={form.experienciaLaboral} inactividades={inactividades} />
                  <div className="mt-3 overflow-x-auto rounded-lg border border-gray-200">
                    <table className="w-full text-sm">
                      <thead className="bg-gray-50 text-gray-500 text-xs">
                        <tr>
                          <th className="px-3 py-2 text-left">Empresa</th>
                          <th className="px-3 py-2 text-left">Cargo</th>
                          <th className="px-3 py-2 text-left">Inicio</th>
                          <th className="px-3 py-2 text-left">Fin</th>
                          <th className="px-3 py-2 text-left">Motivo retiro</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {form.experienciaLaboral.map((exp, i) => (
                          <tr key={i}>
                            <td className="px-3 py-2 text-gray-800">{exp.empresa || '—'}</td>
                            <td className="px-3 py-2 text-gray-800">{exp.cargo || '—'}</td>
                            <td className="px-3 py-2 text-gray-600 whitespace-nowrap">{exp.fechaInicio || '—'}</td>
                            <td className="px-3 py-2 text-gray-600 whitespace-nowrap">
                              {exp.laboraActualmente ? 'Actual' : (exp.fechaFin || '—')}
                            </td>
                            <td className="px-3 py-2 text-gray-600 max-w-[120px]">
                              <span className="truncate block">{exp.motivoRetiro || '—'}</span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {inactividades.filter(g => g.diasInactivo > 30).map((gap, i) => {
                    const key = gapsClave(gap)
                    return (
                      <div key={i} className="mt-2 p-3 bg-red-50 border border-red-200 rounded-lg text-sm">
                        <p className="text-red-700 font-medium">
                          Tiempo muerto de {gap.diasInactivo} días ({gap.fechaInicio} → {gap.fechaFin})
                        </p>
                        {justificaciones[key]
                          ? <p className="text-red-600 text-xs mt-1">Justificación: {justificaciones[key]}</p>
                          : <p className="text-amber-600 text-xs mt-1 italic">Sin justificación — puedes agregar una en el paso de Experiencia laboral.</p>}
                      </div>
                    )
                  })}
                </section>
              )}

              {/* Referencias */}
              {form.referencias.length > 0 && (
                <section>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Referencias personales</h3>
                  <ul className="space-y-2">
                    {form.referencias.map((ref, i) => (
                      <li key={i} className="text-sm bg-gray-50 rounded-lg px-4 py-2.5 flex flex-wrap gap-x-4 gap-y-0.5">
                        <span className="font-medium text-gray-800">{ref.nombre || '—'}</span>
                        {ref.parentesco && <span className="text-gray-500">{ref.parentesco}</span>}
                        {ref.tiempoConocimiento && <span className="text-gray-500">{ref.tiempoConocimiento}</span>}
                        {ref.telefono && <span className="text-gray-400">{ref.telefono}</span>}
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              {/* Documentos */}
              {documentosRequeridos.length > 0 && (
                <section>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Documentos</h3>
                  <ul className="space-y-2">
                    {documentosRequeridos.map((doc) => {
                      const cargado = !!archivos[doc.id]?.idDocumento
                      return (
                        <li key={doc.id} className="flex items-center gap-2 text-sm">
                          <span>{cargado ? '✅' : '⏳'}</span>
                          <span className={cargado ? 'text-gray-800' : 'text-gray-400'}>
                            {doc.nombre}
                          </span>
                          {doc.obligatorio && !cargado && (
                            <span className="text-red-500 text-xs">(pendiente)</span>
                          )}
                        </li>
                      )
                    })}
                  </ul>
                </section>
              )}

              {error && <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>}
            </div>
          )}
        </div>

        {/* Navegación */}
        <div className="flex justify-between mt-4">
          <button
            onClick={() => setPaso((p) => p - 1)}
            disabled={paso === 0}
            className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50 transition-colors">
            {paso === 6 ? 'Volver y editar' : 'Anterior'}
          </button>
          {paso < PASOS.length - 1 ? (
            <button
              onClick={intentarAvanzar}
              className="px-5 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 transition-colors">
              Siguiente
            </button>
          ) : (
            <button
              onClick={enviar}
              disabled={enviando}
              className="px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-lg disabled:opacity-40 hover:bg-green-700 transition-colors">
              {enviando ? 'Enviando...' : 'Confirmar y enviar'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function gapsClave(gap) { return `${gap.fechaInicio}_${gap.fechaFin}` }

export default EvalueeForm

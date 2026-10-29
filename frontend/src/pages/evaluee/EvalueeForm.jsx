import { parseFecha } from '../../utils/fechas'
import { useState, useEffect, useRef, useMemo, useCallback, useId } from 'react'
import { asociarEtiqueta } from '../../utils/asociarEtiqueta'
import { useParams } from 'react-router-dom'
import evaluadoService, { accesoCandidato, olvidarAcceso } from '../../services/evaluadoService'
import WorkTimeline from '../../components/WorkTimeline'
import CiudadSelect from '../../components/CiudadSelect'
import BarrioSelect from '../../components/BarrioSelect'
import DocumentosCandidato, { resumenDocumentos, validarArchivo } from './DocumentosCandidato'
import CitasCandidato, { proximaCita, yaPaso } from './CitasCandidato'
import { useCambios } from '../../hooks/useCambios'
import { PanelMarca, Encabezado, NavInferior, CabeceraSeccion, Inicio, Pie, PantallaEstado, PantallaAutorizacion, PantallaAntesDeIniciar, PantallaDocumento } from './PortalCandidatoLayout'

const VISTAS = ['inicio', 'info', 'documentos', 'citas']

// Los documentos NO son un paso de la hoja de vida: se cargan en la sección "Documentos" del portal.
// La autorización de datos tampoco es un paso: es la primera pantalla del portal (PantallaAutorizacion).
const PASOS = ['Datos personales', 'Educación', 'Experiencia laboral', 'Referencias', 'Declaraciones', 'Revisión']
const PASO_REVISION = PASOS.length - 1

// Categoría → niveles específicos. El "nivel educativo" del candidato ya no se pregunta aparte:
// el servidor toma el estudio más alto de esta lista (EvaluadoService.NIVELES_EDUCATIVOS).
const NIVELES_EDUCATIVOS = {
  'Bachillerato': ['Bachiller básico (hasta noveno grado)', 'Bachiller académico (hasta grado once)'],
  'Formación para el trabajo': ['Técnico laboral (SENA u otras instituciones)'],
  'Pregrado': ['Técnico profesional', 'Tecnológico', 'Profesional'],
  'Posgrado': ['Especialización', 'Maestría', 'Doctorado', 'Posdoctorado'],
}
const CATEGORIAS_EDUCATIVAS = Object.keys(NIVELES_EDUCATIVOS)
// Nombres de la lista anterior que tienen equivalente exacto en la nueva
const NIVELES_ANTERIORES = { 'Técnica profesional': 'Técnico profesional', 'Técnico': 'Técnico profesional', 'Tecnológica': 'Tecnológico', 'Universitaria (profesional)': 'Profesional' }

const TOTAL_REFERENCIAS = 4
const ANOS_HISTORIAL = 4

// Estado del estudio → cómo se llama la fecha (revisión del módulo del candidato). Los valores
// deben coincidir con EvaluadoService.ESTADOS_ESTUDIO.
const ESTADOS_ESTUDIO = {
  'Graduado': 'Fecha de grado',
  'Terminó materias, pendiente de grado': 'Fecha de terminación',
  'En curso': 'Fecha prevista de terminación',
  'Retirado sin graduarse': 'Fecha de retiro',
  'Aplazado': 'Fecha de aplazamiento',
}
const ESTADO_EN_CURSO = 'En curso'
const RELACIONES_REFERENCIA = ['Amigo(a)', 'Excompañero(a) de trabajo', 'Compañero(a) de estudio', 'Vecino(a)', 'Otro']
const TIEMPOS_CONOCIMIENTO = ['Menos de 1 año', '1 año', '2 años', '3 años', '4 años', '5 años', '6 a 10 años', 'Más de 10 años']
const TIEMPO_INSUFICIENTE = 'Menos de 1 año'
const ACTIVIDADES_INACTIVIDAD = ['Estudios', 'Trabajo independiente', 'Emprendimiento', 'Trabajo informal', 'Actividades del hogar', 'Apoyo familiar', 'Otra']
const referenciaVacia = () => ({ nombre: '', parentesco: '', telefono: '', tiempoConocimiento: '', confirmaNoFamiliar: false, habloUltimos30Dias: false })
const completarReferencias = (lista) => {
  const r = [...(lista ?? [])]
  while (r.length < TOTAL_REFERENCIAS) r.push(referenciaVacia())
  return r
}

const soloDigitos = (v) => (v || '').replace(/\D/g, '')
// Celular colombiano: 10 dígitos, empieza por 3. Quita el +57 si venía escrito con indicativo.
const normalizarCelular = (v) => {
  const d = soloDigitos(v)
  return (d.length === 12 && d.startsWith('57') ? d.slice(2) : d).slice(0, 10)
}
const celularValido = (v) => /^3\d{9}$/.test(v || '')
// Teléfono fijo o WhatsApp con código de país: +57 300 123 4567
const telefonoValido = (v) => /^\+?[\d ]{7,17}$/.test(v || '') && soloDigitos(v).length >= 7
const documentoViajeValido = (v) => /^[A-Za-z0-9]{6,12}$/.test(v || '')

/* ─── Tiempos sin empleo (mismo cálculo que HistorialLaboralService) ─── */
const aDia = (iso) => Math.round(Date.parse(`${iso}T00:00:00Z`) / 86400000)
const deDia = (n) => new Date(n * 86400000).toISOString().slice(0, 10)
const hoyIso = () => { const d = new Date(); return deDia(Math.floor((d.getTime() - d.getTimezoneOffset() * 60000) / 86400000)) }
const fechaLarga = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })

// Periodos de más de 30 días sin empleo dentro de los últimos 4 años, incluidos el comienzo de
// esa ventana y el tiempo desde el último empleo hasta hoy. `clave` casa la justificación con el
// servidor: fechas de los empleos que lo rodean, o INICIO / HOY en los extremos que cambian cada día.
function calcularInactividades(experiencias) {
  const validas = experiencias.filter(e => e.fechaInicio)
  if (validas.length === 0) return []
  const hoy = hoyIso()
  const [a, m, d] = hoy.split('-').map(Number)
  const limite = deDia(Math.round(Date.UTC(a - ANOS_HISTORIAL, m - 1, d) / 86400000))
  const finDe = (e) => (e.laboraActualmente || !e.fechaFin ? hoy : e.fechaFin)
  const ordenadas = [...validas].sort((x, y) => x.fechaInicio.localeCompare(y.fechaInicio))
  const gaps = []
  let finAnterior = limite
  let desdeInicio = true
  const avanzar = (e) => { if (finDe(e) > finAnterior) { finAnterior = finDe(e); desdeInicio = false } }
  for (const e of ordenadas) {
    if (e.fechaInicio >= limite) {
      const dias = aDia(e.fechaInicio) - aDia(finAnterior)
      const hasta = deDia(aDia(e.fechaInicio) - 1)
      if (dias > 30) gaps.push({ fechaInicio: finAnterior, fechaFin: hasta, diasInactivo: dias, clave: `${desdeInicio ? 'INICIO' : finAnterior}_${hasta}` })
    }
    avanzar(e)
  }
  const diasFinal = aDia(hoy) - aDia(finAnterior)
  if (diasFinal > 30) gaps.push({ fechaInicio: finAnterior, fechaFin: hoy, diasInactivo: diasFinal, clave: `${desdeInicio ? 'INICIO' : finAnterior}_HOY` })
  return gaps
}

function categoriaDeNivel(nivel) {
  return CATEGORIAS_EDUCATIVAS.find((cat) => NIVELES_EDUCATIVOS[cat].includes(nivel)) || ''
}

// Todos los campos con la misma altura (h-11), también fechas y listas; los textos largos usan areaCls.
const inputCls = 'h-11 w-full rounded-lg border border-gray-300 bg-white px-3.5 text-base text-gray-900 placeholder:text-gray-400 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/30 disabled:bg-gray-50 disabled:text-gray-400 transition-shadow'
const selectCls = inputCls
const smallInputCls = inputCls
const smallSelectCls = inputCls
const areaCls = 'w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5 text-base text-gray-900 placeholder:text-gray-400 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/30 disabled:bg-gray-50 disabled:text-gray-400 transition-shadow'
const etiquetaCls = 'mb-1.5 block text-sm font-medium text-gray-800'
const ayudaCls = 'mt-1.5 text-xs leading-snug text-gray-500'

function Requerido() { return <span className="text-red-500">*</span> }

function Campo({ label, requerido, error, ayuda, children }) {
  const id = useId()
  const ids = [ayuda && `${id}-ayuda`, error && `${id}-err`].filter(Boolean).join(' ')
  return (
    <div className="min-w-0">
      <label htmlFor={id} className={etiquetaCls}>
        {label} {requerido && <Requerido />}
      </label>
      {asociarEtiqueta(children, { id, describedby: ids || undefined, invalido: !!error })}
      {ayuda && <p id={`${id}-ayuda`} className={ayudaCls}>{ayuda}</p>}
      {error && <p id={`${id}-err`} role="alert" className="mt-1 text-xs text-red-700">{error}</p>}
    </div>
  )
}

// Bloque de campos de "Datos personales": un recuadro propio con título y una línea de qué va ahí
function Grupo({ titulo, descripcion, children }) {
  return (
    <section className="rounded-xl border border-gray-200 bg-gray-50/40 p-4 sm:p-6">
      <h3 className="text-sm font-semibold text-gray-900">{titulo}</h3>
      {descripcion && <p className="mt-0.5 text-xs text-gray-500">{descripcion}</p>}
      <div className="mt-4 grid grid-cols-1 gap-x-6 gap-y-5 sm:grid-cols-2">{children}</div>
    </section>
  )
}

// Párrafo de ayuda debajo de un campo de las listas (educación, experiencia, referencias)
function Ayuda({ children }) {
  return <p className={ayudaCls}>{children}</p>
}

// Párrafo de instrucciones al comienzo de un paso
function Instructivo({ children }) {
  return <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-sm leading-relaxed text-gray-600 space-y-2">{children}</div>
}

// Lista que administra el admin (libreta militar, EPS, fondo de pensiones, tipo de contrato). Si el
// candidato tenía guardado un valor que ya no está en la lista, se conserva como opción.
function SelectLista({ opciones = [], value, onChange, className = selectCls, ...resto }) {
  const nombres = opciones.map(o => o.nombre)
  return (
    <select className={className} value={value || ''} onChange={(e) => onChange(e.target.value)} {...resto}>
      <option value="">Selecciona...</option>
      {value && !nombres.includes(value) && <option value={value}>{value}</option>}
      {nombres.map(n => <option key={n} value={n}>{n}</option>)}
    </select>
  )
}

function SelectSiNo({ value, onChange, ...resto }) {
  return (
    <select className={selectCls} value={value == null ? '' : value ? 'SI' : 'NO'} {...resto}
      onChange={(e) => onChange(e.target.value === '' ? null : e.target.value === 'SI')}>
      <option value="">Selecciona...</option>
      <option value="SI">Sí</option>
      <option value="NO">No</option>
    </select>
  )
}

SelectLista.aceptaId = true
SelectSiNo.aceptaId = true

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
  const idCampos = useId()
  const { token } = useParams()
  const [paso, setPaso] = useState(0)
  const [info, setInfo] = useState(null)
  // Llave que da escribir el número de documento (PantallaDocumento); sin ella no se pide nada más.
  const [acceso, setAcceso] = useState(() => accesoCandidato(token))
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [completado, setCompletado] = useState(false)
  // Pestaña del portal: 'info' (hoja de vida por pasos) · 'documentos' · 'citas'
  // ?vista=citas|documentos abre directo esa pestaña (ej. desde un correo).
  const [vista, setVista] = useState(() => {
    const v = new URLSearchParams(window.location.search).get('vista')
    return VISTAS.includes(v) ? v : 'inicio'
  })
  const [citas, setCitas] = useState(null)
  const [pasosIntentados, setPasosIntentados] = useState({})
  const autoSaveRef = useRef(null)

  const [form, setForm] = useState({
    autorizacionDatos: false,
    fechaNacimiento: '', lugarNacimiento: '',
    idCiudadNacimiento: null, idCiudadResidencia: null,
    estadoCivil: '', rh: '', nivelEducativo: '',
    libretaMilitar: '', visa: '', pasaporte: '',
    fondoPensiones: '', eps: '',
    estrato: '', barrio: '', idBarrio: null, direccion: '', direccionAdicional: '',
    telefonoFijo: '', celular: '', email: '',
    fechaExpedicion: '', idCiudadExpedicion: null,
    tieneVisa: null, visaPais: '', tienePasaporte: null,
    sinEstudios: false,
    educacion: [],
    experienciaLaboral: [],
    referencias: completarReferencias([]),
  })
  // Declaraciones: { [idPregunta]: { respuesta: true|false|null, detalle } }
  const [respuestas, setRespuestas] = useState({})
  // Listas y preguntas que administra el admin (Admin → Novedades → Hoja de vida del candidato)
  const [catalogo, setCatalogo] = useState({})
  // "Antes de iniciar": se muestra una vez por sesión del navegador, mientras no haya enviado.
  const [leyoInstrucciones, setLeyoInstrucciones] = useState(() => {
    try { return sessionStorage.getItem(`antes-de-iniciar-${token}`) === '1' } catch { return false }
  })

  const [inactividades, setInactividades] = useState([])
  const [justificaciones, setJustificaciones] = useState({}) // { 'fechaInicio_fechaFin': string }
  const [detallesInactividad, setDetallesInactividad] = useState({}) // { clave: { actividad, quienConfirma } }
  const [archivos, setArchivos] = useState({}) // { docId: { idDocumento, nombreArchivo, subiendo, error } }

  // Documentos requeridos según el proceso del evaluado — configurados por admin en
  // admin/catálogo → editar proceso (ver ProcesoService.obtenerDocumentosRequeridos), ya vienen
  // resueltos desde el backend en validarLink(). `clave` hace de identificador estable (lo que se
  // manda como tipoDocumento al subir el archivo), igual que antes usaba `id`.
  const documentosRequeridos = useMemo(() => {
    return (info?.documentosRequeridos ?? []).map(d => ({ id: d.clave, nombre: d.nombre, obligatorio: !!d.obligatorio }))
  }, [info])


  useEffect(() => {
    if (!acceso) return
    evaluadoService.validarLink(token)
      .then((r) => setInfo(r.data))
      .catch((e) => {
        // Llave vieja o de otro documento: se vuelve a pedir el documento.
        if (e.response?.status === 401) { olvidarAcceso(token); setAcceso(null); return }
        setError(e.response?.data?.mensaje
          ?? (e.response?.status === 410 ? 'Este link ya no está disponible.' : 'Link inválido o no encontrado.'))
      })
  }, [token, acceso])

  // Citas (poligrafía/visita) del servicio de este link — se recargan al proponer disponibilidad.
  const cargarCitas = useCallback(() => {
    evaluadoService.citas(token).then(setCitas).catch(() => setCitas([]))
  }, [token])
  const autorizo = !!info?.autorizacionDatos
  useEffect(() => { if (autorizo) cargarCitas() }, [autorizo, cargarCitas])
  useEffect(() => {
    if (autorizo) evaluadoService.catalogo(token).then(setCatalogo).catch(() => setCatalogo({}))
  }, [autorizo, token])

  // Los documentos ya subidos sí se precargan: a diferencia de las respuestas del formulario,
  // un archivo subido ya es un hecho consumado en el servidor, no un borrador. Al recargar se
  // respeta lo que se esté subiendo en ese momento (no se pisa el "Subiendo…" ni su error).
  const cargarDocumentos = useCallback(() => {
    evaluadoService.listarDocumentos(token)
      .then((r) => {
        const mapa = {}
        r.data.forEach((d) => {
          mapa[d.tipoDocumento] = {
            idDocumento: d.id, nombreArchivo: d.nombreArchivo,
            estadoRevision: d.estadoRevision, observacionRevision: d.observacionRevision,
          }
        })
        setArchivos(prev => {
          const n = { ...mapa }
          Object.entries(prev).forEach(([k, v]) => { if (v?.subiendo || v?.error) n[k] = v })
          return n
        })
      })
      .catch(() => {})
  }, [token])
  useEffect(() => { if (autorizo) cargarDocumentos() }, [autorizo, cargarDocumentos])

  // Tiempo real: cuando un interno agenda una cita, revisa un documento o cambia el servicio, el
  // portal lo muestra sin refrescar. La hoja de vida que el candidato está diligenciando NO se toca.
  useCambios(() => {
    cargarCitas()
    cargarDocumentos()
    evaluadoService.validarLink(token)
      .then(r => setInfo(prev => ({ ...prev, ...r.data })))
      .catch(() => {})
  }, { candidato: token, activo: autorizo })

  // Al abrir un link (aunque sea uno nuevo generado tras un diligenciamiento previo) se recupera
  // la información ya registrada del candidato, pero SIEMPRE se arranca desde el paso 0: el
  // candidato puede revisar y corregir todo de nuevo, no se le salta al final. Mientras la carga
  // no termine, NINGÚN guardado (autosave ni cambio de paso) puede salir: si el formulario
  // todavía está en blanco y se dispara un guardado, pisa en el servidor los datos reales con
  // vacíos (por eso "se borraba" al volver a abrir el link).
  const restauradoRef = useRef(false)
  useEffect(() => {
    if (!autorizo) return
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
          direccionAdicional: data.direccionAdicional ?? '',
          fechaExpedicion: data.fechaExpedicion ?? '',
          idCiudadExpedicion: data.idCiudadExpedicion ?? null,
          tieneVisa: data.tieneVisa ?? null,
          visaPais: data.visaPais ?? '',
          tienePasaporte: data.tienePasaporte ?? null,
          sinEstudios: !!data.sinEstudios,
          barrio: data.barrio ?? '',
          idBarrio: data.idBarrio ?? null,
          estrato: data.estrato ?? '',
          email: data.email ?? '',
          celular: normalizarCelular(data.celular),
          rh: data.rh ?? '',
          libretaMilitar: data.libretaMilitar ?? '',
          visa: data.visa ?? '',
          pasaporte: data.pasaporte ?? '',
          fondoPensiones: data.fondoPensiones ?? '',
          eps: data.eps ?? '',
          telefonoFijo: data.telefonoFijo ?? '',
          educacion: data.educacion?.length
            ? data.educacion.map(e => ({ ...e, nivel: NIVELES_ANTERIORES[e.nivel] ?? e.nivel }))
            : prev.educacion,
          experienciaLaboral: data.experienciaLaboral?.length ? data.experienciaLaboral : prev.experienciaLaboral,
          referencias: completarReferencias((data.referencias?.length ? data.referencias : prev.referencias).map(r => ({ ...r, telefono: normalizarCelular(r.telefono) }))),
        }))
        if (data.respuestas?.length) {
          setRespuestas(Object.fromEntries(data.respuestas.map(r => [r.idPregunta, { respuesta: r.respuesta, detalle: r.detalle ?? '' }])))
        }
        if (data.justificacionesInactividad) setJustificaciones(data.justificacionesInactividad)
        if (data.detallesInactividad) setDetallesInactividad(data.detallesInactividad)
        // No se restaura pasoActual a propósito: el candidato siempre revisa desde el inicio.
      })
      .catch(() => {})
      .finally(() => { restauradoRef.current = true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autorizo, token])

  const datosParaGuardar = () => ({
    ...form,
    educacion: form.sinEstudios ? [] : form.educacion,
    experienciaLaboral: form.experienciaLaboral.map(e => ({ ...e, salario: soloDigitos(String(e.salario ?? '')) || null })),
    respuestas: Object.entries(respuestas).map(([id, r]) => ({ idPregunta: Number(id), respuesta: r.respuesta, detalle: r.detalle })),
    justificacionesInactividad: justificaciones,
    detallesInactividad,
  })

  useEffect(() => {
    if (!info?.autorizacionDatos) return
    autoSaveRef.current = setInterval(() => {
      if (!restauradoRef.current) return
      evaluadoService.guardarProgreso(token, { ...datosParaGuardar(), pasoActual: paso }).catch(() => {})
    }, 120000)
    return () => clearInterval(autoSaveRef.current)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form, info, paso, token, justificaciones, detallesInactividad, respuestas])

  const primerPasoRef = useRef(true)
  useEffect(() => {
    if (primerPasoRef.current) { primerPasoRef.current = false; return }
    if (!info?.autorizacionDatos || !restauradoRef.current) return
    evaluadoService.guardarProgreso(token, { ...datosParaGuardar(), pasoActual: paso }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paso])

  useEffect(() => {
    setInactividades(calcularInactividades(form.experienciaLaboral))
  }, [form.experienciaLaboral])

  const f = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))

  const agregarEducacion = () =>
    setForm((prev) => ({
      ...prev,
      educacion: [...prev.educacion, {
        categoriaNivel: '', nivel: '', institucion: '', titulo: '',
        fechaInicio: '', fechaFin: '', enCurso: false, ciudad: '',
        estado: '', enExterior: null, paisExterior: '',
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

  const actualizarEstadoEstudio = (i, estado) =>
    setForm((prev) => {
      const e = [...prev.educacion]
      e[i] = { ...e[i], estado, enCurso: estado === ESTADO_EN_CURSO }
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
        tipoContrato: '', salario: '', llamadosAtencion: '', contratadoPorTemporal: null, nombreTemporal: '',
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

  const actualizarReferencia = (i, campo, valor) =>
    setForm((prev) => {
      const r = [...prev.referencias]
      r[i] = { ...r[i], [campo]: valor }
      return { ...prev, referencias: r }
    })

  const eliminarReferencia = (i) =>
    setForm((prev) => ({ ...prev, referencias: completarReferencias(prev.referencias.filter((_, idx) => idx !== i)) }))

  const preguntas = catalogo.PREGUNTA ?? []
  const responder = (idPregunta, cambios) =>
    setRespuestas((prev) => ({ ...prev, [idPregunta]: { respuesta: null, detalle: '', ...prev[idPregunta], ...cambios } }))

  const manejarArchivo = async (docId, file) => {
    if (!file) return
    const problema = validarArchivo(file, info)
    if (problema) {
      setArchivos(prev => ({ ...prev, [docId]: { ...prev[docId], subiendo: false, error: problema } }))
      return
    }
    setArchivos(prev => ({ ...prev, [docId]: { subiendo: true } }))
    try {
      const { data } = await evaluadoService.subirDocumento(token, docId, file)
      setArchivos(prev => ({ ...prev, [docId]: {
        idDocumento: data.id, nombreArchivo: data.nombreArchivo,
        estadoRevision: data.estadoRevision, observacionRevision: data.observacionRevision,
      } }))
    } catch (e) {
      setArchivos(prev => ({ ...prev, [docId]: { error: e.response?.data?.mensaje || 'No se pudo cargar el archivo' } }))
    }
  }

  const quitarArchivo = async (docId) => {
    const actual = archivos[docId]
    if (!actual?.idDocumento) {
      setArchivos(prev => { const n = { ...prev }; delete n[docId]; return n })
      return
    }
    try {
      await evaluadoService.eliminarDocumento(token, actual.idDocumento)
      setArchivos(prev => { const n = { ...prev }; delete n[docId]; return n })
    } catch (e) {
      setArchivos(prev => ({ ...prev, [docId]: { ...actual, error: e.response?.data?.mensaje || 'No se pudo quitar el archivo' } }))
    }
  }

  /* ─── Validación por paso ─── */
  const gapsSinJustificar = inactividades.filter(g => {
    const d = detallesInactividad[gapsClave(g)] || {}
    return !(justificaciones[gapsClave(g)] || '').trim() || !d.actividad || !(d.quienConfirma || '').trim()
  })

  const erroresPorPaso = useMemo(() => {
    const datosPersonales = []
    if (!form.fechaNacimiento) datosPersonales.push('Fecha de nacimiento')
    if (!form.idCiudadNacimiento) datosPersonales.push('Ciudad de nacimiento')
    if (!form.estadoCivil) datosPersonales.push('Estado civil')
    if (!form.rh) datosPersonales.push('Grupo sanguíneo / RH')
    if (!form.fechaExpedicion) datosPersonales.push('Fecha de expedición del documento')
    if (!form.idCiudadExpedicion) datosPersonales.push('Lugar de expedición del documento')
    if (!form.celular) datosPersonales.push('Celular')
    else if (!celularValido(form.celular)) datosPersonales.push('Celular: debe tener 10 dígitos y empezar por 3 (ej. 300 123 4567)')
    if (form.telefonoFijo && !telefonoValido(form.telefonoFijo)) datosPersonales.push('Teléfono fijo o WhatsApp: solo números, con código de país (ej. +57 300 123 4567)')
    if (!form.email) datosPersonales.push('Correo electrónico')
    if (!form.idCiudadResidencia) datosPersonales.push('Ciudad de residencia')
    if (!form.barrio) datosPersonales.push('Barrio de residencia')
    if (!form.direccion) datosPersonales.push('Dirección de residencia')
    if (!form.estrato) datosPersonales.push('Estrato')
    if (!form.libretaMilitar) datosPersonales.push('Libreta militar')
    if (form.tienePasaporte == null) datosPersonales.push('Pasaporte: indique si tiene')
    else if (form.tienePasaporte && !documentoViajeValido(form.pasaporte)) datosPersonales.push('Número de pasaporte: de 6 a 12 letras y números, sin espacios')
    if (form.tieneVisa == null) datosPersonales.push('Visa: indique si tiene')
    else if (form.tieneVisa) {
      if (!(form.visaPais || '').trim()) datosPersonales.push('Visa: país que la expidió')
      if (!documentoViajeValido(form.visa)) datosPersonales.push('Número de visa: de 6 a 12 letras y números, sin espacios')
    }
    if (!form.eps) datosPersonales.push('EPS')
    if (!form.fondoPensiones) datosPersonales.push('Fondo de pensiones')

    const educacion = []
    if (!form.sinEstudios) {
      if (form.educacion.length === 0) educacion.push('Agregue al menos un estudio, o marque "No tengo estudios para registrar"')
      form.educacion.forEach((e, i) => {
        if (!categoriaDeNivel(e.nivel)) educacion.push(`Estudio #${i + 1}: categoría y nivel específico`)
        if (!e.institucion) educacion.push(`Estudio #${i + 1}: institución`)
        if (!e.ciudad) educacion.push(`Estudio #${i + 1}: ciudad`)
        if (!e.fechaInicio) educacion.push(`Estudio #${i + 1}: fecha de inicio`)
        if (!e.estado) educacion.push(`Estudio #${i + 1}: estado del estudio`)
        else if (e.estado !== ESTADO_EN_CURSO && !e.fechaFin) educacion.push(`Estudio #${i + 1}: ${(ESTADOS_ESTUDIO[e.estado] || 'fecha').toLowerCase()}`)
        if (e.enExterior == null) educacion.push(`Estudio #${i + 1}: indique si lo realizó en el exterior`)
        else if (e.enExterior && !(e.paisExterior || '').trim()) educacion.push(`Estudio #${i + 1}: país donde estudió`)
      })
    }

    const experiencia = []
    if (form.experienciaLaboral.length === 0) experiencia.push('Agregue al menos una experiencia laboral')
    form.experienciaLaboral.forEach((exp, i) => {
      const n = `Experiencia #${i + 1}`
      if (!exp.empresa) experiencia.push(`${n}: empresa`)
      if (!exp.cargo) experiencia.push(`${n}: cargo desempeñado`)
      if (!exp.ciudad) experiencia.push(`${n}: ciudad`)
      if (!exp.fechaInicio) experiencia.push(`${n}: fecha de inicio`)
      if (!exp.laboraActualmente && !exp.fechaFin) experiencia.push(`${n}: fecha de retiro`)
      if (exp.fechaInicio && exp.fechaFin && !exp.laboraActualmente && exp.fechaFin < exp.fechaInicio) experiencia.push(`${n}: la fecha de retiro es anterior a la de inicio`)
      if (!exp.tipoContrato) experiencia.push(`${n}: tipo de contrato`)
      if (exp.contratadoPorTemporal == null) experiencia.push(`${n}: indique si lo contrató una empresa de servicios temporales`)
      else if (exp.contratadoPorTemporal && !(exp.nombreTemporal || '').trim()) experiencia.push(`${n}: nombre de la empresa de servicios temporales`)
      if (!soloDigitos(String(exp.salario ?? ''))) experiencia.push(`${n}: salario devengado`)
      if (!(exp.llamadosAtencion || '').trim()) experiencia.push(`${n}: llamados de atención (escriba "Ninguno" si no tuvo)`)
      if (!exp.laboraActualmente && !(exp.motivoRetiro || '').trim()) experiencia.push(`${n}: motivo del retiro`)
    })
    if (gapsSinJustificar.length > 0) experiencia.push('En cada periodo sin empleo de más de 30 días indique la actividad que realizó, en qué consistió y quién o qué documento puede confirmarlo')

    const referencias = []
    if (form.referencias.length < TOTAL_REFERENCIAS) referencias.push(`Se requieren ${TOTAL_REFERENCIAS} referencias personales`)
    form.referencias.forEach((r, i) => {
      const n = `Referencia #${i + 1}`
      if (!(r.nombre || '').trim()) referencias.push(`${n}: nombre completo`)
      if (!celularValido(normalizarCelular(r.telefono))) referencias.push(`${n}: celular de 10 dígitos que empiece por 3`)
      else if (normalizarCelular(r.telefono) === form.celular) referencias.push(`${n}: el celular no puede ser el suyo`)
      else if (form.referencias.some((o, j) => j < i && normalizarCelular(o.telefono) === normalizarCelular(r.telefono))) referencias.push(`${n}: el celular está repetido en otra referencia`)
      if (!(r.parentesco || '').trim()) referencias.push(`${n}: relación o tipo de vínculo`)
      if (!(r.tiempoConocimiento || '').trim()) referencias.push(`${n}: años de conocerle`)
      else if (r.tiempoConocimiento === TIEMPO_INSUFICIENTE) referencias.push(`${n}: debe conocerle hace mínimo un (1) año; escoja otra persona`)
      if ((r.parentesco || '').trim() === 'Otro' || (r.parentesco || '').trim() === 'Otro:') referencias.push(`${n}: indique cuál es la relación`)
      if (!r.confirmaNoFamiliar) referencias.push(`${n}: confirme que no es su familiar y no vive con usted`)
      if (!r.habloUltimos30Dias) referencias.push(`${n}: confirme que han hablado en los últimos 30 días`)
    })

    const declaraciones = []
    preguntas.forEach((p, i) => {
      const r = respuestas[p.id]
      if (r?.respuesta == null) declaraciones.push(`Pregunta ${i + 1}: responda Sí o No`)
      else if (r.respuesta && !(r.detalle || '').trim()) declaraciones.push(`Pregunta ${i + 1}: explique su respuesta`)
    })

    return [datosPersonales, educacion, experiencia, referencias, declaraciones, []]
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form, gapsSinJustificar, preguntas, respuestas])

  const pasoValido = (i) => erroresPorPaso[i].length === 0

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
      await evaluadoService.enviarFormulario(token, datosParaGuardar())
      setCompletado(true)
      setInfo(prev => ({ ...prev, hojaVidaCompletada: true }))
      setVista('inicio')
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      const d = e.response?.data
      setError(d?.mensaje || (d?.errores ? Object.values(d.errores).join(' ') : 'Error al enviar el formulario'))
    } finally {
      setEnviando(false)
    }
  }

  if (error) return <PantallaEstado titulo="Este enlace no está disponible" mensaje={error} />
  if (!acceso) {
    return <PantallaDocumento onVerificar={async (documento) => setAcceso((await evaluadoService.verificarDocumento(token, documento)).acceso)} />
  }
  if (!info) return <PantallaEstado cargando />

  const aceptarAutorizacion = async () => {
    await evaluadoService.autorizarDatos(token)
    setForm(prev => ({ ...prev, autorizacionDatos: true }))
    setInfo(prev => ({ ...prev, autorizacionDatos: true, fechaAutorizacion: new Date().toISOString() }))
    window.scrollTo({ top: 0 })
  }
  if (!info.autorizacionDatos) return <PantallaAutorizacion info={info} onAceptar={aceptarAutorizacion} />

  if (!info.hojaVidaCompletada && !leyoInstrucciones) {
    return (
      <PantallaAntesDeIniciar info={info} onContinuar={() => {
        try { sessionStorage.setItem(`antes-de-iniciar-${token}`, '1') } catch { /* sin almacenamiento: se vuelve a mostrar */ }
        setLeyoInstrucciones(true)
        window.scrollTo({ top: 0 })
      }} />
    )
  }

  // Desde V197 el link sigue abierto después de enviar: el candidato puede corregir sus datos,
  // cargar documentos devueltos y ver sus citas hasta que el estudio se cierre.
  const enviado = !!info.hojaVidaCompletada
  const pasosDatos = [0, 1, 2, 3, 4]
  const avance = Math.round((pasosDatos.filter(i => pasoValido(i)).length / pasosDatos.length) * 100)
  const docs = resumenDocumentos(documentosRequeridos, archivos)
  const proxima = proximaCita(citas)
  const citasActivas = (citas ?? []).filter(c => !['REALIZADA', 'CANCELADA'].includes(c.estado))
  const fechaCorta = (iso) => parseFecha(iso).toLocaleDateString('es-CO', { weekday: 'short', day: 'numeric', month: 'short' })
  const horaCorta = (iso) => new Date(iso).toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' })

  // Estado de cada tarjeta del inicio. tono: hecho | actual (le toca) | atencion | pendiente
  const hojaVida = enviado
    ? { tono: 'hecho', texto: 'Enviada', detalle: 'Ya la recibimos. Puedes corregirla si algo cambió.', accion: 'Revisar', porcentaje: 100 }
    : { tono: 'actual', texto: `${avance}%`, detalle: 'Completa tus datos, estudios, experiencia, referencias y declaraciones.', accion: avance > 0 ? 'Continuar' : 'Empezar', porcentaje: avance }
  const documentos = docs.total === 0
    ? { tono: 'hecho', texto: 'No requeridos', detalle: 'Tu estudio no pide documentos.', accion: 'Ver' }
    : docs.devueltos > 0 ? { tono: 'atencion', texto: `${docs.devueltos} por corregir`, detalle: 'Revisa el motivo y vuelve a cargarlos.', accion: 'Corregir' }
    : docs.aprobados === docs.total ? { tono: 'hecho', texto: 'Aprobados', detalle: 'Todos tus documentos fueron aprobados.', accion: 'Ver' }
    : docs.cargados === docs.total ? { tono: 'pendiente', texto: 'En revisión', detalle: 'Nuestro equipo los está revisando.', accion: 'Ver' }
    : { tono: 'actual', texto: `${docs.total - docs.cargados} por subir`, detalle: `Has cargado ${docs.cargados} de ${docs.total}.`, accion: 'Subir' }
  const cita = citas == null ? { tono: 'pendiente', texto: '…', detalle: 'Cargando…', accion: 'Ver' }
    : citas.length === 0 ? { tono: 'hecho', texto: 'No aplica', detalle: 'Tu estudio no incluye citas presenciales.', accion: 'Ver' }
    : proxima ? { tono: 'actual', texto: 'Programada', detalle: '', accion: 'Ver detalle' }
    : citasActivas.length === 0 ? { tono: 'hecho', texto: 'Realizadas', detalle: 'Ya realizaste tus citas.', accion: 'Ver' }
    : citasActivas.every(yaPaso) ? { tono: 'pendiente', texto: 'En revisión', detalle: 'Tu cita ya pasó; el equipo la está cerrando.', accion: 'Ver' }
    : { tono: 'pendiente', texto: 'Por programar', detalle: 'Te avisaremos la fecha. Puedes proponer cuándo tienes tiempo.', accion: 'Proponer horario' }
  // Antes de enviar la hoja de vida, Documentos y Citas no se muestran (ni por ?vista=).
  const bloqueado = !enviado
  const vistaActual = bloqueado && (vista === 'documentos' || vista === 'citas') ? 'inicio' : vista
  const irA = (v) => { setVista(v); window.scrollTo({ top: 0 }) }
  const alertas = { documentos: docs.devueltos > 0 }

  const estadosMenu = {
    info: { tono: hojaVida.tono, texto: hojaVida.texto },
    documentos: { tono: documentos.tono, texto: documentos.texto },
    citas: { tono: cita.tono, texto: cita.texto },
  }

  return (
    <div className="flex min-h-screen bg-gray-50">
      <PanelMarca info={info} vista={vistaActual} onIr={irA} estados={estadosMenu} bloqueado={bloqueado} />

      <div className="relative flex min-w-0 flex-1 flex-col pb-20 lg:pb-0">
        <Encabezado info={info} onIr={irA} />

      <main className="relative mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:px-8 lg:py-12">
        {vistaActual === 'inicio' && <Inicio info={info} resumen={{ hojaVida, documentos, cita }} cita={proxima} onIr={irA} bloqueado={bloqueado} />}

        {vistaActual === 'documentos' && (
          <div>
            <CabeceraSeccion onVolver={() => irA('inicio')} titulo="Documentos"
              descripcion="Carga los soportes que pide tu estudio. Si alguno necesita corrección, aquí verás el motivo." />
            <DocumentosCandidato documentos={documentosRequeridos} archivos={archivos}
              onSubir={manejarArchivo} onQuitar={quitarArchivo}
              maxPdfMb={info.maxPdfMb} maxImagenMb={info.maxImagenMb} />
          </div>
        )}

        {vistaActual === 'citas' && (
          <div>
            <CabeceraSeccion onVolver={() => irA('inicio')} titulo="Citas"
              descripcion="Tus citas de poligrafía y visita domiciliaria. Si no puedes asistir, propón otro horario." />
            <CitasCandidato token={token} citas={citas} onCambio={cargarCitas} />
          </div>
        )}

        {vistaActual === 'info' && (<div className="space-y-5">
        <CabeceraSeccion onVolver={() => irA('inicio')} titulo="Hoja de vida"
          descripcion="Tu avance se guarda solo. Puedes salir y volver cuando quieras." />
        {(completado || enviado) && (
          <div className="flex items-start gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3.5">
            <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-500 text-xs font-bold text-white">✓</span>
            <div className="text-sm text-emerald-900">
              <p className="font-semibold">{completado ? '¡Listo! Tu hoja de vida fue enviada.' : 'Tu hoja de vida ya fue enviada.'}</p>
              <p className="text-emerald-800/80">Si necesitas corregir algo, puedes hacerlo aquí y volver a enviar. Revisa también tus documentos y citas.</p>
            </div>
          </div>
        )}

        {/* Stepper */}
        <div className="rounded-2xl border border-gray-200 bg-white px-4 py-4 shadow-sm sm:px-6">
        <ol className="flex items-start">
          {PASOS.map((nombre, i) => {
            const activo = i === paso
            const completo = i < paso && pasoValido(i)
            const incompleto = i < paso && !pasoValido(i)
            const estado = completo ? 'completo' : incompleto ? 'incompleto' : activo ? 'activo' : 'pendiente'
            return (
              <li key={nombre} className="relative flex flex-1 flex-col items-center gap-1.5">
                {i < PASOS.length - 1 && (
                  <span aria-hidden="true" className={`absolute left-1/2 top-4 h-0.5 w-full rounded transition-colors ${i < paso ? 'bg-primary-300' : 'bg-gray-200'}`} />
                )}
                <button type="button" title={nombre}
                  onClick={() => { if (i < paso || pasoValido(paso)) setPaso(i) }}
                  className={`relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-colors ${
                    estado === 'completo' ? 'bg-green-500 text-white' :
                    estado === 'incompleto' ? 'bg-amber-400 text-white' :
                    estado === 'activo' ? 'bg-primary-600 text-white ring-4 ring-primary-100' :
                    'bg-gray-100 text-gray-500 ring-1 ring-gray-200'
                  }`}>
                  <IconoPaso estado={estado === 'activo' || estado === 'pendiente' ? null : estado} />
                  {(estado === 'activo' || estado === 'pendiente') && (i + 1)}
                </button>
                <span className={`hidden px-1 text-center text-[11px] leading-tight md:block ${activo ? 'font-semibold text-gray-900' : 'text-gray-400'}`}>{nombre}</span>
              </li>
            )
          })}
        </ol>
        <p className="mt-3 text-center text-xs text-gray-400 md:hidden">Paso {paso + 1} de {PASOS.length}: <span className="font-medium text-gray-600">{PASOS[paso]}</span></p>
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 sm:p-7">

          {/* Paso 0: Datos personales */}
          {paso === 0 && (
            <div className="space-y-5">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">Datos personales</h2>
                <p className="mt-1 text-sm text-gray-500">Escribe tus datos tal como aparecen en tu documento de identidad.</p>
              </div>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[0] : []} />

              <Grupo titulo="Identificación" descripcion="Tus datos de nacimiento y de tu documento.">
                <Campo label="Fecha de nacimiento" requerido ayuda="Día, mes y año, como aparece en tu documento.">
                  <input type="date" className={inputCls} value={form.fechaNacimiento || ''} onChange={f('fechaNacimiento')} />
                </Campo>
                <Campo label="Ciudad de nacimiento" requerido ayuda="Escribe y elige el municipio donde naciste.">
                  <CiudadSelect className={inputCls} value={form.idCiudadNacimiento}
                    onChange={(idCiudad) => setForm(p => ({ ...p, idCiudadNacimiento: idCiudad }))} />
                </Campo>
                <Campo label="Estado civil" requerido ayuda="Tu estado civil actual.">
                  <select className={selectCls} value={form.estadoCivil || ''} onChange={f('estadoCivil')}>
                    <option value="">Selecciona...</option>
                    {['Soltero', 'Casado', 'Unión libre', 'Divorciado', 'Viudo', 'Separado'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </Campo>
                <Campo label="Grupo sanguíneo / RH" requerido ayuda="Aparece al respaldo de tu cédula.">
                  <select className={selectCls} value={form.rh || ''} onChange={f('rh')}>
                    <option value="">Selecciona...</option>
                    {['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </Campo>
                <Campo label="Fecha de expedición del documento" requerido ayuda="La fecha de expedición impresa en tu documento.">
                  <input type="date" className={inputCls} value={form.fechaExpedicion || ''} onChange={f('fechaExpedicion')} />
                </Campo>
                <Campo label="Lugar de expedición del documento" requerido ayuda="Municipio donde se expidió tu documento.">
                  <CiudadSelect className={inputCls} value={form.idCiudadExpedicion}
                    onChange={(idCiudad) => setForm(p => ({ ...p, idCiudadExpedicion: idCiudad }))} />
                </Campo>
              </Grupo>

              <Grupo titulo="Contacto" descripcion="Por aquí nos comunicaremos contigo durante el estudio.">
                <Campo label="Celular" requerido ayuda="10 dígitos. Ejemplo: 300 123 4567">
                  <input type="tel" inputMode="numeric" maxLength={10} placeholder="3001234567" className={inputCls}
                    value={form.celular || ''}
                    onChange={(e) => setForm(p => ({ ...p, celular: soloDigitos(e.target.value).slice(0, 10) }))} />
                </Campo>
                <Campo label="Teléfono fijo y/o número de WhatsApp" ayuda="WhatsApp: escríbelo con código de país. Ejemplo: +57 300 123 4567">
                  <input type="tel" maxLength={17} placeholder="+57 300 123 4567" className={inputCls}
                    value={form.telefonoFijo || ''}
                    onChange={(e) => setForm(p => ({ ...p, telefonoFijo: e.target.value.replace(/[^\d+ ]/g, '') }))} />
                </Campo>
                <Campo label="Correo electrónico" requerido ayuda="Aquí te enviaremos los avisos de tu estudio.">
                  <input type="email" className={inputCls} placeholder="nombre@correo.com" value={form.email || ''} onChange={f('email')} />
                </Campo>
              </Grupo>

              <Grupo titulo="Residencia" descripcion="Dónde vives actualmente.">
                <Campo label="Ciudad de residencia" requerido ayuda="Municipio donde vives hoy.">
                  <CiudadSelect className={inputCls} value={form.idCiudadResidencia}
                    onChange={(idCiudad) => setForm(p => ({ ...p, idCiudadResidencia: idCiudad }))} />
                </Campo>
                <Campo label="Barrio de residencia" requerido ayuda="Búscalo en la lista; si no aparece, escríbelo.">
                  <BarrioSelect className={inputCls} idCiudad={form.idCiudadResidencia} value={form.idBarrio}
                    onChange={(idBarrio, nombre) => setForm(p => ({ ...p, idBarrio, barrio: nombre }))} />
                </Campo>
                <Campo label="Dirección de residencia" requerido ayuda="Dirección completa de tu vivienda.">
                  <input type="text" className={inputCls} placeholder="Ej. Calle 45 # 12-30" value={form.direccion || ''} onChange={f('direccion')} />
                </Campo>
                <Campo label="Adicionales (número de casa - apartamento)" ayuda="Opcional: torre, bloque, casa o apartamento.">
                  <input type="text" maxLength={150} className={inputCls} placeholder="Ej. Torre 2, apto 301" value={form.direccionAdicional || ''} onChange={f('direccionAdicional')} />
                </Campo>
                <Campo label="Estrato" requerido ayuda="Aparece en los recibos de servicios públicos.">
                  <select className={selectCls} value={form.estrato || ''} onChange={f('estrato')}>
                    <option value="">Selecciona...</option>
                    {['1', '2', '3', '4', '5', '6'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </Campo>
              </Grupo>

              <Grupo titulo="Documentos" descripcion="Libreta militar, pasaporte y visa.">
                <Campo label="Libreta militar" requerido ayuda="Si no aplica en tu caso, elige “No aplica”.">
                  <SelectLista opciones={catalogo.LIBRETA_MILITAR} value={form.libretaMilitar}
                    onChange={(v) => setForm(p => ({ ...p, libretaMilitar: v }))} />
                </Campo>
                <div className="hidden sm:block" />
                <Campo label="¿Tiene pasaporte?" requerido ayuda="Responde “Sí” solo si está vigente.">
                  <SelectSiNo value={form.tienePasaporte}
                    onChange={(v) => setForm(p => ({ ...p, tienePasaporte: v, pasaporte: v ? p.pasaporte : '' }))} />
                </Campo>
                {form.tienePasaporte ? (
                  <Campo label="Número de pasaporte" requerido ayuda="Generalmente 9 caracteres (letras y números), sin espacios.">
                    <input type="text" maxLength={12} className={inputCls} placeholder="Ej. AB1234567" value={form.pasaporte || ''}
                      onChange={(e) => setForm(p => ({ ...p, pasaporte: e.target.value.replace(/[^A-Za-z0-9]/g, '').toUpperCase() }))} />
                  </Campo>
                ) : <div className="hidden sm:block" />}
                <Campo label="¿Tiene visa?" requerido ayuda="Visa vigente de cualquier país.">
                  <SelectSiNo value={form.tieneVisa}
                    onChange={(v) => setForm(p => ({ ...p, tieneVisa: v, visa: v ? p.visa : '', visaPais: v ? p.visaPais : '' }))} />
                </Campo>
                {form.tieneVisa ? (
                  <>
                    <Campo label="País que expidió la visa" requerido ayuda="País que te otorgó la visa.">
                      <input type="text" maxLength={100} className={inputCls} placeholder="Ej. Estados Unidos" value={form.visaPais || ''} onChange={f('visaPais')} />
                    </Campo>
                    <Campo label="Número de visa" requerido ayuda="Visa estadounidense: suele tener 8 dígitos. En otros países el formato varía.">
                      <input type="text" maxLength={12} className={inputCls} placeholder="Ej. 12345678" value={form.visa || ''}
                        onChange={(e) => setForm(p => ({ ...p, visa: e.target.value.replace(/[^A-Za-z0-9]/g, '').toUpperCase() }))} />
                    </Campo>
                  </>
                ) : null}
              </Grupo>

              <Grupo titulo="Seguridad social" descripcion="Tu afiliación actual a salud y pensión.">
                <Campo label="EPS a la que está afiliado actualmente" requerido ayuda="Entidad de salud a la que estás afiliado hoy.">
                  <SelectLista opciones={catalogo.EPS} value={form.eps}
                    onChange={(v) => setForm(p => ({ ...p, eps: v }))} />
                </Campo>
                <Campo label="Fondo de pensiones al que está afiliado actualmente" requerido ayuda="Fondo donde se hacen tus aportes de pensión.">
                  <SelectLista opciones={catalogo.FONDO_PENSIONES} value={form.fondoPensiones}
                    onChange={(v) => setForm(p => ({ ...p, fondoPensiones: v }))} />
                </Campo>
              </Grupo>
            </div>
          )}

          {/* Paso 1: Educación */}
          {paso === 1 && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-800">Educación</h2>
                {!form.sinEstudios && (
                  <button onClick={agregarEducacion} className="text-sm text-primary-600 hover:text-primary-800 font-medium whitespace-nowrap">+ Agregar</button>
                )}
              </div>
              <Instructivo>
                <p>Registre todos sus estudios formales, comenzando por el más reciente: bachillerato, técnicos, tecnologías, carreras profesionales y posgrados. No incluya cursos cortos ni diplomados. Si está graduado, indique el título y la fecha de grado tal como aparecen en el diploma. Si no tiene estudios para registrar, seleccione “No tengo estudios para registrar” y continúe.</p>
              </Instructivo>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[1] : []} />

              <label className={`flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-3 transition-colors ${
                form.sinEstudios ? 'border-primary-300 bg-primary-50/60' : 'border-gray-200 hover:bg-gray-50'}`}>
                <input type="checkbox" className="mt-0.5 h-4 w-4 accent-primary-600" checked={form.sinEstudios}
                  onChange={(e) => setForm(p => ({ ...p, sinEstudios: e.target.checked }))} />
                <span className="text-sm text-gray-800">
                  No tengo estudios para registrar <span className="text-gray-500">(no finalicé el bachillerato)</span>
                </span>
              </label>

              {!form.sinEstudios && form.educacion.length === 0 && (
                <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg">
                  <p className="text-sm text-gray-400">No hay registros educativos.</p>
                  <button onClick={agregarEducacion} className="text-sm text-primary-600 hover:text-primary-800 font-medium mt-2">+ Agregar el primero</button>
                </div>
              )}
              {!form.sinEstudios && form.educacion.map((edu, i) => (
                <div key={i} className="space-y-4 rounded-xl border border-gray-200 border-l-4 border-l-primary-300 p-4 sm:p-6">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-semibold text-gray-900">{edu.nivel || `Registro ${i + 1}`}</span>
                    <button onClick={() => eliminarEducacion(i)} className="text-xs text-red-500 hover:text-red-700">Eliminar</button>
                  </div>
                  <div className="grid grid-cols-1 gap-x-6 gap-y-5 sm:grid-cols-2">
                    <div>
                      <label htmlFor={`${idCampos}-1-${i}`} className={etiquetaCls}>Categoría <Requerido /></label>
                      <select id={`${idCampos}-1-${i}`} className={smallSelectCls} value={edu.categoriaNivel || categoriaDeNivel(edu.nivel)}
                        onChange={(e) => actualizarCategoriaEducacion(i, e.target.value)}>
                        <option value="">Selecciona...</option>
                        {CATEGORIAS_EDUCATIVAS.map(cat => <option key={cat} value={cat}>{cat}</option>)}
                      </select>
                      <Ayuda>Bachillerato, formación para el trabajo (SENA u otras instituciones), pregrado o posgrado.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-2-${i}`} className={etiquetaCls}>Nivel específico <Requerido /></label>
                      <select id={`${idCampos}-2-${i}`} className={smallSelectCls} value={categoriaDeNivel(edu.nivel) ? edu.nivel : ''}
                        disabled={!(edu.categoriaNivel || categoriaDeNivel(edu.nivel))}
                        onChange={(e) => actualizarEducacion(i, 'nivel', e.target.value)}>
                        <option value="">Selecciona...</option>
                        {(NIVELES_EDUCATIVOS[edu.categoriaNivel || categoriaDeNivel(edu.nivel)] || []).map(o => (
                          <option key={o} value={o}>{o}</option>
                        ))}
                      </select>
                      <Ayuda>Primero elige la categoría.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-3-${i}`} className={etiquetaCls}>Institución <Requerido /></label>
                      <input id={`${idCampos}-3-${i}`} className={smallInputCls} placeholder="Ej. Universidad Nacional de Colombia" value={edu.institucion || ''} onChange={(e) => actualizarEducacion(i, 'institucion', e.target.value)} />
                      <Ayuda>Nombre completo del colegio, instituto o universidad.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-4-${i}`} className={etiquetaCls}>Título obtenido</label>
                      <input id={`${idCampos}-4-${i}`} className={smallInputCls} placeholder="Ej. Bachiller académico" value={edu.titulo || ''} onChange={(e) => actualizarEducacion(i, 'titulo', e.target.value)} />
                      <Ayuda>Como aparece en el diploma. Si aún estudias, déjalo vacío.</Ayuda>
                    </div>
                    <div>
                      <label className={etiquetaCls}>Ciudad <Requerido /></label>
                      <CiudadSelect valueType="nombre" className={smallSelectCls}
                        value={edu.ciudad || ''} onChange={(v) => actualizarEducacion(i, 'ciudad', v)} />
                      <Ayuda>Ciudad donde estudiaste.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-5-${i}`} className={etiquetaCls}>Estado del estudio <Requerido /></label>
                      <select id={`${idCampos}-5-${i}`} className={smallSelectCls} value={edu.estado || ''}
                        onChange={(e) => actualizarEstadoEstudio(i, e.target.value)}>
                        <option value="">Selecciona...</option>
                        {Object.keys(ESTADOS_ESTUDIO).map(o => <option key={o} value={o}>{o}</option>)}
                      </select>
                      <Ayuda>“Graduado” es solo si ya tiene el título; si terminó las materias y falta el grado, escoja “Terminó materias”.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-6-${i}`} className={etiquetaCls}>Fecha inicio <Requerido /></label>
                      <input id={`${idCampos}-6-${i}`} type="date" className={smallInputCls} value={edu.fechaInicio || ''} onChange={(e) => actualizarEducacion(i, 'fechaInicio', e.target.value)} />
                      <Ayuda>Fecha aproximada en que empezaste.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-7-${i}`} className={etiquetaCls}>{ESTADOS_ESTUDIO[edu.estado] || 'Fecha de grado o terminación'} {edu.estado && edu.estado !== ESTADO_EN_CURSO && <Requerido />}</label>
                      <input id={`${idCampos}-7-${i}`} type="date" className={smallInputCls}
                        value={edu.fechaFin || ''}
                        disabled={!edu.estado}
                        onChange={(e) => actualizarEducacion(i, 'fechaFin', e.target.value)} />
                      <Ayuda>{edu.estado ? 'Tal como aparece en el diploma o el certificado.' : 'Primero escoja el estado del estudio.'}</Ayuda>
                    </div>
                    <div>
                      <label className={etiquetaCls}>¿Lo realizó en el exterior? <Requerido /></label>
                      <SelectSiNo value={edu.enExterior} onChange={(v) => actualizarEducacion(i, 'enExterior', v)} />
                      <Ayuda>Los estudios en el exterior se verifican por otro canal y pueden requerir convalidación.</Ayuda>
                    </div>
                    {edu.enExterior === true && (
                      <div>
                        <label htmlFor={`${idCampos}-8-${i}`} className={etiquetaCls}>País donde estudió <Requerido /></label>
                        <input id={`${idCampos}-8-${i}`} maxLength={100} className={smallInputCls} placeholder="Ej. España" value={edu.paisExterior || ''}
                          onChange={(e) => actualizarEducacion(i, 'paisExterior', e.target.value)} />
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Paso 2: Experiencia laboral */}
          {paso === 2 && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-800">Experiencia laboral</h2>
                <button onClick={agregarExperiencia} className="text-sm text-primary-600 hover:text-primary-800 font-medium">+ Agregar</button>
              </div>
              <Instructivo>
                <p>Registre su experiencia laboral de los últimos cuatro (4) años, comenzando por la más reciente, incluidos los trabajos independientes o informales. Diligencie todos los campos con información completa, exacta y verificable.</p>
                <p>Si tuvo periodos sin empleo de más de 30 días, indique qué actividad realizó durante ese tiempo y quién puede confirmarlo.</p>
                <p className="font-medium text-gray-700">No deje periodos sin registrar dentro de los últimos cuatro (4) años.</p>
              </Instructivo>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[2] : []} />

              <WorkTimeline experiencias={form.experienciaLaboral} inactividades={inactividades} />

              {/* Periodos sin empleo de más de 30 días dentro de los últimos 4 años */}
              {inactividades.length > 0 && (
                <div className="border border-amber-200 rounded-lg p-4 bg-amber-50 space-y-3">
                  <div>
                    <h4 className="text-sm font-semibold text-amber-800">Periodos sin empleo registrado</h4>
                    <p className="text-xs text-amber-700 mt-0.5">Indique qué actividad realizó en cada uno para solventar sus gastos o generar ingresos, o agregue arriba el empleo que falta.</p>
                  </div>
                  {inactividades.map((gap, i) => {
                    const key = gapsClave(gap)
                    const det = detallesInactividad[key] || {}
                    return (
                      <div key={i} className="space-y-2 rounded-lg border border-amber-200 bg-white/60 p-3">
                        <p className="text-xs text-amber-800">
                          <strong>{gap.diasInactivo} días</strong> sin empleo ({fechaLarga(gap.fechaInicio)} → {fechaLarga(gap.fechaFin)})
                        </p>
                        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                          <div>
                            <label htmlFor={`${idCampos}-9-${i}`} className="mb-1 block text-xs font-medium text-amber-800">¿Qué actividad realizó para cubrir sus gastos? <Requerido /></label>
                            <select id={`${idCampos}-9-${i}`} className={smallSelectCls} value={det.actividad || ''}
                              onChange={(e) => setDetallesInactividad(prev => ({ ...prev, [key]: { ...prev[key], actividad: e.target.value } }))}>
                              <option value="">Selecciona...</option>
                              {ACTIVIDADES_INACTIVIDAD.map(a => <option key={a} value={a}>{a}</option>)}
                            </select>
                          </div>
                          <div>
                            <label htmlFor={`${idCampos}-10-${i}`} className="mb-1 block text-xs font-medium text-amber-800">¿Quién o qué documento puede confirmarlo? <Requerido /></label>
                            <input id={`${idCampos}-10-${i}`} maxLength={300} className={smallInputCls} placeholder="Ej. Mi cliente Juan Pérez, 300 123 4567; o el RUT"
                              value={det.quienConfirma || ''}
                              onChange={(e) => setDetallesInactividad(prev => ({ ...prev, [key]: { ...prev[key], quienConfirma: e.target.value } }))} />
                          </div>
                        </div>
                        <div>
                          <label htmlFor={`${idCampos}-11-${i}`} className="mb-1 block text-xs font-medium text-amber-800">Cuéntenos en qué consistió <Requerido /></label>
                          <textarea id={`${idCampos}-11-${i}`}
                            rows={2}
                            className="w-full rounded-lg border border-amber-300 bg-white px-3.5 py-2.5 text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-amber-500/30"
                            placeholder="Ej. vendí productos por catálogo, cuidé a un familiar, estudié un técnico..."
                            value={justificaciones[key] || ''}
                            onChange={(e) => setJustificaciones(prev => ({ ...prev, [key]: e.target.value }))}
                          />
                        </div>
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
                <div key={i} className="space-y-4 rounded-xl border border-gray-200 border-l-4 border-l-primary-300 p-4 sm:p-6">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-semibold text-gray-900">{exp.empresa || `Empleo ${i + 1}`}</span>
                    <button onClick={() => eliminarExperiencia(i)} className="text-xs text-red-500 hover:text-red-700">Eliminar</button>
                  </div>
                  <div className="grid grid-cols-1 gap-x-6 gap-y-5 sm:grid-cols-2">
                    <div>
                      <label htmlFor={`${idCampos}-12-${i}`} className={etiquetaCls}>Empresa <Requerido /></label>
                      <input id={`${idCampos}-12-${i}`} className={smallInputCls} placeholder="Ej. Almacenes Éxito S.A." value={exp.empresa || ''} onChange={(e) => actualizarExperiencia(i, 'empresa', e.target.value)} />
                      <Ayuda>Nombre o razón social de la empresa.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-13-${i}`} className={etiquetaCls}>Cargo desempeñado <Requerido /></label>
                      <input id={`${idCampos}-13-${i}`} className={smallInputCls} placeholder="Ej. Auxiliar administrativo" value={exp.cargo || ''} onChange={(e) => actualizarExperiencia(i, 'cargo', e.target.value)} />
                      <Ayuda>Tal como aparece en la certificación laboral.</Ayuda>
                    </div>
                    <div>
                      <label className={etiquetaCls}>Ciudad <Requerido /></label>
                      <CiudadSelect valueType="nombre" className={smallSelectCls}
                        value={exp.ciudad || ''} onChange={(v) => actualizarExperiencia(i, 'ciudad', v)} />
                      <Ayuda>Ciudad donde trabajaste.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-14-${i}`} className={etiquetaCls}>Teléfono empresa</label>
                      <input id={`${idCampos}-14-${i}`} type="tel" maxLength={20} className={smallInputCls} placeholder="Ej. 601 234 5678" value={exp.telefonoEmpresa || ''}
                        onChange={(e) => actualizarExperiencia(i, 'telefonoEmpresa', e.target.value.replace(/[^\d+ ]/g, ''))} />
                      <Ayuda>Fijo con indicativo o celular, para confirmar la información con la empresa.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-15-${i}`} className={etiquetaCls}>Fecha inicio <Requerido /></label>
                      <input id={`${idCampos}-15-${i}`} type="date" className={smallInputCls}
                        value={exp.fechaInicio || ''} onChange={(e) => actualizarExperiencia(i, 'fechaInicio', e.target.value)} />
                      <Ayuda>Fecha de ingreso.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-16-${i}`} className={etiquetaCls}>Fecha retiro {!exp.laboraActualmente && <Requerido />}</label>
                      <input id={`${idCampos}-16-${i}`} type="date" className={smallInputCls}
                        value={exp.laboraActualmente ? '' : (exp.fechaFin || '')} disabled={exp.laboraActualmente}
                        onChange={(e) => actualizarExperiencia(i, 'fechaFin', e.target.value)} />
                      <Ayuda>Déjala vacía si aún trabajas aquí.</Ayuda>
                    </div>
                    <div className="sm:col-span-2">
                      <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                        <input type="checkbox" checked={exp.laboraActualmente || false}
                          onChange={(e) => actualizarExperiencia(i, 'laboraActualmente', e.target.checked)} />
                        Labora actualmente aquí
                      </label>
                    </div>
                    <div>
                      <label className={etiquetaCls}>Tipo de contrato <Requerido /></label>
                      <SelectLista className={smallSelectCls} opciones={catalogo.TIPO_CONTRATO} value={exp.tipoContrato}
                        onChange={(v) => actualizarExperiencia(i, 'tipoContrato', v)} />
                      <Ayuda>Escoja el que figura en su contrato; si no tuvo contrato, “Informal (sin contrato)”.</Ayuda>
                    </div>
                    <div>
                      <label className={etiquetaCls}>¿Lo contrató una empresa de servicios temporales? <Requerido /></label>
                      <SelectSiNo value={exp.contratadoPorTemporal} onChange={(v) => actualizarExperiencia(i, 'contratadoPorTemporal', v)} />
                      <Ayuda>Si trabajó en una empresa pero su contrato era con una temporal, responda Sí.</Ayuda>
                    </div>
                    {exp.contratadoPorTemporal === true && (
                      <div>
                        <label htmlFor={`${idCampos}-17-${i}`} className={etiquetaCls}>Nombre de la empresa de servicios temporales <Requerido /></label>
                        <input id={`${idCampos}-17-${i}`} maxLength={200} className={smallInputCls} placeholder="Ej. Adecco Colombia S.A."
                          value={exp.nombreTemporal || ''} onChange={(e) => actualizarExperiencia(i, 'nombreTemporal', e.target.value)} />
                        <Ayuda>Es la que certifica el contrato.</Ayuda>
                      </div>
                    )}
                    <div>
                      <label htmlFor={`${idCampos}-18-${i}`} className={etiquetaCls}>Salario devengado (mensual, en pesos) <Requerido /></label>
                      <input id={`${idCampos}-18-${i}`} inputMode="numeric" className={smallInputCls} placeholder="Ej. 1.800.000"
                        value={soloDigitos(String(exp.salario ?? '')) ? Number(soloDigitos(String(exp.salario))).toLocaleString('es-CO') : ''}
                        onChange={(e) => actualizarExperiencia(i, 'salario', soloDigitos(e.target.value).slice(0, 12))} />
                      <Ayuda>Último salario mensual, solo números.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-19-${i}`} className={etiquetaCls}>Llamados de atención <Requerido /></label>
                      <textarea id={`${idCampos}-19-${i}`} rows={2} className={areaCls} placeholder='Indique cuáles y el motivo, o escriba "Ninguno"'
                        value={exp.llamadosAtencion || ''} onChange={(e) => actualizarExperiencia(i, 'llamadosAtencion', e.target.value)} />
                      <Ayuda>Si no tuviste, escribe “Ninguno”.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-20-${i}`} className={etiquetaCls}>Motivo del retiro {!exp.laboraActualmente && <Requerido />}</label>
                      <textarea id={`${idCampos}-20-${i}`} rows={2} className={areaCls} placeholder="Ej. Renuncia voluntaria, terminación de contrato" disabled={exp.laboraActualmente}
                        value={exp.motivoRetiro || ''} onChange={(e) => actualizarExperiencia(i, 'motivoRetiro', e.target.value)} />
                      <Ayuda>Por qué terminó esta relación laboral.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-21-${i}`} className={etiquetaCls}>Nombre del jefe</label>
                      <input id={`${idCampos}-21-${i}`} className={smallInputCls} placeholder="Nombres y apellidos"
                        value={exp.nombreJefe || ''} onChange={(e) => actualizarExperiencia(i, 'nombreJefe', e.target.value)} />
                      <Ayuda>Tu jefe inmediato (opcional).</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-22-${i}`} className={etiquetaCls}>Cargo del jefe</label>
                      <input id={`${idCampos}-22-${i}`} className={smallInputCls} placeholder="Ej. Coordinador de operaciones"
                        value={exp.cargoJefe || ''} onChange={(e) => actualizarExperiencia(i, 'cargoJefe', e.target.value)} />
                      <Ayuda>Opcional.</Ayuda>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Paso 3: Referencias personales */}
          {paso === 3 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-800">Referencias personales</h2>
              <Instructivo>
                <p>Registre cuatro (4) referencias personales que cumplan estos requisitos: no ser familiares, no vivir con usted, conocerle desde hace mínimo un (1) año y haber hablado con usted en los últimos 30 días.</p>
                <ul className="list-disc space-y-1 pl-5">
                  <li>Para cada referencia, indique el nombre completo, el celular y la relación que tiene con la persona.</li>
                  <li>Verifique que la información sea completa, correcta y actualizada, ya que las referencias podrán ser contactadas para su validación.</li>
                </ul>
                <p>Las referencias podrán ser contactadas; las que no cumplan los requisitos serán descartadas. No se requieren cartas de referencia.</p>
              </Instructivo>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[3] : []} />
              {form.referencias.map((ref, i) => (
                <div key={i} className="space-y-4 rounded-xl border border-gray-200 border-l-4 border-l-primary-300 p-4 sm:p-6">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-semibold text-gray-900">Referencia {i + 1} de {Math.max(TOTAL_REFERENCIAS, form.referencias.length)}</span>
                    {form.referencias.length > TOTAL_REFERENCIAS && (
                      <button onClick={() => eliminarReferencia(i)} className="text-xs text-red-500">Eliminar</button>
                    )}
                  </div>
                  <div className="grid grid-cols-1 gap-x-6 gap-y-5 sm:grid-cols-2">
                    <div>
                      <label htmlFor={`${idCampos}-23-${i}`} className={etiquetaCls}>Nombre completo <Requerido /></label>
                      <input id={`${idCampos}-23-${i}`} maxLength={150} className={smallInputCls} placeholder="Nombres y apellidos" value={ref.nombre || ''} onChange={(e) => actualizarReferencia(i, 'nombre', e.target.value)} />
                      <Ayuda>No puede ser un familiar ni alguien que viva contigo.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-24-${i}`} className={etiquetaCls}>Celular (10 dígitos) <Requerido /></label>
                      <input id={`${idCampos}-24-${i}`} type="tel" inputMode="numeric" maxLength={10} className={smallInputCls} placeholder="Ej. 300 123 4567" value={ref.telefono || ''}
                        onChange={(e) => actualizarReferencia(i, 'telefono', normalizarCelular(e.target.value))} />
                      <Ayuda>Debe ser distinto al suyo y no repetirse en otra referencia. No se imprime en el informe.</Ayuda>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-25-${i}`} className={etiquetaCls}>Relación o vínculo <Requerido /></label>
                      <select id={`${idCampos}-25-${i}`} className={smallSelectCls}
                        value={RELACIONES_REFERENCIA.includes((ref.parentesco || '').split(':')[0]) ? (ref.parentesco || '').split(':')[0] : ''}
                        onChange={(e) => actualizarReferencia(i, 'parentesco', e.target.value)}>
                        <option value="">Selecciona...</option>
                        {RELACIONES_REFERENCIA.map(o => <option key={o} value={o}>{o === 'Otro' ? 'Otro (¿cuál?)' : o}</option>)}
                      </select>
                      <Ayuda>No se aceptan familiares.</Ayuda>
                    </div>
                    {(ref.parentesco || '').startsWith('Otro') && (
                      <div>
                        <label htmlFor={`${idCampos}-26-${i}`} className={etiquetaCls}>¿Cuál? <Requerido /></label>
                        <input id={`${idCampos}-26-${i}`} maxLength={40} className={smallInputCls} placeholder="Ej. Compañero de equipo deportivo"
                          value={(ref.parentesco || '').slice(5).replace(/^:\s*/, '')}
                          onChange={(e) => actualizarReferencia(i, 'parentesco', `Otro: ${e.target.value}`)} />
                      </div>
                    )}
                    <div>
                      <label htmlFor={`${idCampos}-27-${i}`} className={etiquetaCls}>Años de conocerlo <Requerido /></label>
                      <select id={`${idCampos}-27-${i}`} className={smallSelectCls} value={ref.tiempoConocimiento || ''}
                        onChange={(e) => actualizarReferencia(i, 'tiempoConocimiento', e.target.value)}>
                        <option value="">Selecciona...</option>
                        {TIEMPOS_CONOCIMIENTO.map(o => <option key={o} value={o}>{o}</option>)}
                      </select>
                      {ref.tiempoConocimiento === TIEMPO_INSUFICIENTE
                        ? <p className="mt-1.5 text-xs font-medium text-red-600">Esta persona no cumple el requisito: debe conocerle hace mínimo un (1) año. Escoja otra referencia.</p>
                        : <Ayuda>Debe conocerle hace mínimo 1 año.</Ayuda>}
                    </div>
                    <div className="space-y-2 sm:col-span-2">
                      <label className="flex cursor-pointer items-start gap-2 text-sm text-gray-700">
                        <input type="checkbox" className="mt-0.5 h-4 w-4 accent-primary-600" checked={!!ref.confirmaNoFamiliar}
                          onChange={(e) => actualizarReferencia(i, 'confirmaNoFamiliar', e.target.checked)} />
                        <span>Confirmo que no es mi familiar y no vive conmigo <Requerido /></span>
                      </label>
                      <label className="flex cursor-pointer items-start gap-2 text-sm text-gray-700">
                        <input type="checkbox" className="mt-0.5 h-4 w-4 accent-primary-600" checked={!!ref.habloUltimos30Dias}
                          onChange={(e) => actualizarReferencia(i, 'habloUltimos30Dias', e.target.checked)} />
                        <span>Hemos hablado en los últimos 30 días <Requerido /></span>
                      </label>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Paso 4: Declaraciones — las preguntas las administra el admin */}
          {paso === 4 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-800">Declaraciones</h2>
              <Instructivo>
                <p>Responda con sinceridad. Tener un antecedente o una deuda no descarta su proceso por sí solo; omitirlo sí se tiene en cuenta en la calificación.</p>
                <p>Si su respuesta es “Sí”, explique la situación en el espacio que aparece.</p>
              </Instructivo>
              <ResumenErrores errores={mostrarErroresPaso ? erroresPorPaso[4] : []} />
              {preguntas.length === 0 && (
                <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg">
                  <p className="text-sm text-gray-400">No hay preguntas por responder. Continúe al siguiente paso.</p>
                </div>
              )}
              {preguntas.map((p, i) => {
                const r = respuestas[p.id] ?? { respuesta: null, detalle: '' }
                return (
                  <div key={p.id} className="space-y-4 rounded-xl border border-gray-200 border-l-4 border-l-primary-300 p-4 sm:p-6">
                    <div>
                      <p className="text-sm font-medium text-gray-800">{i + 1}. {p.nombre} <Requerido /></p>
                      {p.descripcion && <p className="text-xs leading-relaxed text-gray-500 mt-1">{p.descripcion}</p>}
                    </div>
                    <div className="flex gap-2">
                      {[{ valor: true, texto: 'Sí' }, { valor: false, texto: 'No' }].map(o => (
                        <button key={o.texto} type="button" onClick={() => responder(p.id, { respuesta: o.valor })}
                          className={`rounded-lg border px-5 py-1.5 text-sm font-medium transition-colors ${
                            r.respuesta === o.valor ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'}`}>
                          {o.texto}
                        </button>
                      ))}
                    </div>
                    {r.respuesta === true && (
                      <div>
                        <label htmlFor={`${idCampos}-28-${i}`} className={etiquetaCls}>Explique su respuesta <Requerido /></label>
                        <textarea id={`${idCampos}-28-${i}`} rows={3} maxLength={2000} className={areaCls} placeholder="Cuéntanos qué pasó, cuándo y en qué estado está hoy."
                          value={r.detalle || ''} onChange={(e) => responder(p.id, { detalle: e.target.value })} />
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}

          {/* Paso 5: Revisión */}
          {paso === PASO_REVISION && (
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
                  {form.fechaExpedicion && <div><span className="text-gray-400">Expedición del documento: </span><span className="text-gray-800">{form.fechaExpedicion}</span></div>}
                  {form.libretaMilitar && <div><span className="text-gray-400">Libreta militar: </span><span className="text-gray-800">{form.libretaMilitar}</span></div>}
                  {form.tienePasaporte != null && <div><span className="text-gray-400">Pasaporte: </span><span className="text-gray-800">{form.tienePasaporte ? form.pasaporte : 'No tiene'}</span></div>}
                  {form.tieneVisa != null && <div><span className="text-gray-400">Visa: </span><span className="text-gray-800">{form.tieneVisa ? `${form.visaPais} · ${form.visa}` : 'No tiene'}</span></div>}
                  {form.eps && <div><span className="text-gray-400">EPS: </span><span className="text-gray-800">{form.eps}</span></div>}
                  {form.fondoPensiones && <div><span className="text-gray-400">Pensiones: </span><span className="text-gray-800">{form.fondoPensiones}</span></div>}
                  {form.celular && <div><span className="text-gray-400">Celular: </span><span className="text-gray-800">{form.celular}</span></div>}
                  {form.telefonoFijo && <div><span className="text-gray-400">Fijo / WhatsApp: </span><span className="text-gray-800">{form.telefonoFijo}</span></div>}
                  {form.email && <div><span className="text-gray-400">Correo: </span><span className="text-gray-800">{form.email}</span></div>}
                  {form.direccion && <div className="col-span-2"><span className="text-gray-400">Dirección: </span><span className="text-gray-800">{[form.direccion, form.direccionAdicional, form.barrio].filter(Boolean).join(', ')}</span></div>}
                </div>
              </section>

              {/* Educación */}
              {form.sinEstudios && (
                <section>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Educación</h3>
                  <p className="text-sm bg-gray-50 rounded-lg px-4 py-2.5 text-gray-700">No tengo estudios para registrar.</p>
                </section>
              )}
              {!form.sinEstudios && form.educacion.length > 0 && (
                <section>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Educación</h3>
                  <div className="overflow-x-auto rounded-lg border border-gray-200">
                    <table className="table-stack w-full text-sm">
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
                            <td className="px-3 py-2 text-gray-600">{edu.estado || '—'}</td>
                            <td className="px-3 py-2 text-gray-600">
                              {edu.fechaFin || '—'}
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
                    <table className="table-stack w-full text-sm">
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
                  {inactividades.map((gap, i) => {
                    const key = gapsClave(gap)
                    return (
                      <div key={i} className="mt-2 p-3 bg-red-50 border border-red-200 rounded-lg text-sm">
                        <p className="text-red-700 font-medium">
                          Periodo sin empleo de {gap.diasInactivo} días ({fechaLarga(gap.fechaInicio)} → {fechaLarga(gap.fechaFin)})
                        </p>
                        {justificaciones[key]
                          ? <p className="text-red-600 text-xs mt-1">Actividad realizada: {detallesInactividad[key]?.actividad ? `${detallesInactividad[key].actividad} — ` : ''}{justificaciones[key]}{detallesInactividad[key]?.quienConfirma ? ` (lo confirma: ${detallesInactividad[key].quienConfirma})` : ''}</p>
                          : <p className="text-amber-600 text-xs mt-1 italic">Sin registrar — indique la actividad en el paso de Experiencia laboral.</p>}
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

              {/* Declaraciones */}
              {preguntas.length > 0 && (
                <section>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Declaraciones</h3>
                  <ul className="space-y-2">
                    {preguntas.map((p) => {
                      const r = respuestas[p.id]
                      return (
                        <li key={p.id} className="text-sm bg-gray-50 rounded-lg px-4 py-2.5">
                          <p className="text-gray-500">{p.nombre}</p>
                          <p className="font-medium text-gray-800">{r?.respuesta == null ? '—' : r.respuesta ? 'Sí' : 'No'}</p>
                          {r?.respuesta && r.detalle && <p className="text-gray-600 mt-0.5 whitespace-pre-line">{r.detalle}</p>}
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
        <div className="flex items-center justify-between gap-3">
          <button
            onClick={() => setPaso((p) => p - 1)}
            disabled={paso === 0}
            className="rounded-xl border border-gray-300 bg-white px-5 py-2.5 text-sm font-medium text-gray-700 shadow-sm transition-colors hover:bg-gray-50 disabled:opacity-40">
            ← {paso === PASOS.length - 1 ? 'Volver y editar' : 'Anterior'}
          </button>
          <span className="hidden text-xs text-gray-400 sm:block">Tu avance se guarda automáticamente</span>
          {paso < PASOS.length - 1 ? (
            <button
              onClick={intentarAvanzar}
              className="rounded-xl bg-primary-600 px-6 py-2.5 text-sm font-semibold text-white shadow-md shadow-primary-600/25 transition-colors hover:bg-primary-700">
              Siguiente →
            </button>
          ) : (
            <button
              onClick={enviar}
              disabled={enviando}
              className="rounded-xl bg-emerald-600 px-6 py-2.5 text-sm font-semibold text-white shadow-md shadow-emerald-600/25 transition-colors hover:bg-emerald-700 disabled:opacity-40">
              {enviando ? 'Enviando…' : enviado ? 'Guardar cambios' : 'Confirmar y enviar'}
            </button>
          )}
        </div>
        </div>)}
      </main>

        <Pie />
      </div>
      <NavInferior vista={vistaActual} onIr={irA} alertas={alertas} bloqueado={bloqueado} />
    </div>
  )
}

function gapsClave(gap) { return gap.clave }

export default EvalueeForm

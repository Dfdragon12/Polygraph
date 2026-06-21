import { useState, useEffect, useRef, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import evaluadoService from '../../services/evaluadoService'
import WorkTimeline from '../../components/WorkTimeline'

const PASOS = ['Autorización', 'Datos personales', 'Educación', 'Experiencia laboral', 'Referencias', 'Documentos', 'Revisión']

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

const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none'
const selectCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none bg-white'
const smallInputCls = 'w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:ring-1 focus:ring-indigo-500 focus:outline-none'
const smallSelectCls = 'w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:ring-1 focus:ring-indigo-500 focus:outline-none bg-white'

function EvalueeForm() {
  const { token } = useParams()
  const [paso, setPaso] = useState(0)
  const [info, setInfo] = useState(null)
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [completado, setCompletado] = useState(false)
  const autoSaveRef = useRef(null)

  const [form, setForm] = useState({
    autorizacionDatos: false,
    fechaNacimiento: '', lugarNacimiento: '',
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
  const [archivos, setArchivos] = useState({}) // { docId: File }

  // Documentos requeridos según los servicios del evaluado
  const documentosRequeridos = useMemo(() => {
    if (!info?.servicios?.length) {
      // Sin información de servicios: mostrar todos como opcionales
      return DOCS_CONFIG.map(d => ({ ...d, obligatorio: false }))
    }
    const serviciosLower = info.servicios.map(s => s.toLowerCase())
    return DOCS_CONFIG.filter(doc =>
      doc.palabrasClave.some(kw => serviciosLower.some(s => s.includes(kw)))
    )
  }, [info])

  const docsObligatoriosPendientes = documentosRequeridos.filter(d => d.obligatorio && !archivos[d.id])

  useEffect(() => {
    evaluadoService.validarLink(token)
      .then((r) => setInfo(r.data))
      .catch((e) => setError(e.response?.status === 410
        ? 'Este link ya fue utilizado o ha expirado.'
        : 'Link inválido o no encontrado.'))
  }, [token])

  useEffect(() => {
    if (!info) return
    evaluadoService.obtenerFormulario(token)
      .then((r) => {
        const d = r.data
        setForm((prev) => ({
          ...prev, ...d,
          educacion: d.educacion || [],
          experienciaLaboral: d.experienciaLaboral || [],
          referencias: d.referencias || [],
        }))
      })
      .catch(() => {})
  }, [info, token])

  useEffect(() => {
    if (!info || paso === 0) return
    autoSaveRef.current = setInterval(() => {
      evaluadoService.guardarProgreso(token, form).catch(() => {})
    }, 120000)
    return () => clearInterval(autoSaveRef.current)
  }, [form, info, paso, token])

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
        nivel: '', institucion: '', titulo: '',
        fechaInicio: '', fechaFin: '', estadoEstudio: '', ciudad: '',
      }],
    }))

  const actualizarEducacion = (i, campo, valor) =>
    setForm((prev) => {
      const e = [...prev.educacion]
      e[i] = { ...e[i], [campo]: valor }
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

  const manejarArchivo = (docId, file) => {
    if (file) setArchivos(prev => ({ ...prev, [docId]: file }))
  }

  const quitarArchivo = (docId) =>
    setArchivos(prev => { const n = { ...prev }; delete n[docId]; return n })

  const enviar = async () => {
    setEnviando(true)
    setError(null)
    try {
      const documentosCargados = Object.entries(archivos).map(([id, file]) => ({
        id, nombre: file.name, tipo: file.type,
      }))
      await evaluadoService.enviarFormulario(token, {
        ...form,
        justificacionesInactividad: justificaciones,
        documentosCargados,
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
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="text-center p-8 max-w-md">
        <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <span className="text-3xl text-green-600">✓</span>
        </div>
        <h2 className="text-xl font-bold text-gray-900 mb-2">Formulario completado</h2>
        <p className="text-gray-500 text-sm">Tu hoja de vida fue enviada exitosamente a Polygraph Service. Te contactaremos pronto.</p>
      </div>
    </div>
  )

  const gapsClave = (gap) => `${gap.fechaInicio}_${gap.fechaFin}`

  return (
    <div className="min-h-screen bg-slate-50 py-8 px-4">
      <div className="max-w-2xl mx-auto">
        {/* Header */}
        <div className="text-center mb-6">
          <h1 className="text-xl font-bold text-gray-900">Polygraph Service</h1>
          <p className="text-gray-500 text-sm mt-1">
            Hola, <strong>{info.nombresEvaluado} {info.apellidosEvaluado}</strong>. Por favor completa tu hoja de vida.
          </p>
          {info.cargo && <p className="text-gray-400 text-xs mt-0.5">Cargo: {info.cargo}</p>}
        </div>

        {/* Stepper compacto */}
        <div className="flex gap-1 mb-6">
          {PASOS.map((_, i) => (
            <div key={i} className={`flex-1 h-1.5 rounded-full transition-colors ${i <= paso ? 'bg-indigo-500' : 'bg-gray-200'}`} />
          ))}
        </div>
        <p className="text-xs text-gray-400 text-center mb-4">Paso {paso + 1} de {PASOS.length}: {PASOS[paso]}</p>

        <div className="bg-white rounded-xl border border-gray-200 p-6">

          {/* Paso 0: Autorización */}
          {paso === 0 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-800">Autorización de datos</h2>
              <div className="bg-gray-50 rounded-lg p-4 text-sm text-gray-600 leading-relaxed max-h-48 overflow-y-auto">
                <p>Yo, <strong>{info.nombresEvaluado} {info.apellidosEvaluado}</strong>, identificado(a) con cédula <strong>{info.cedulaEvaluado}</strong>, autorizo de manera libre, previa, expresa e informada a <strong>Polygraph Service Ltda</strong> para recolectar, almacenar, usar, circular, suprimir y en general tratar mis datos personales con la finalidad de adelantar el proceso de verificación de información.</p>
                <p className="mt-2">Esta autorización incluye datos de naturaleza sensible y financiera conforme a la Ley 1581 de 2012 y sus decretos reglamentarios.</p>
              </div>
              <label className="flex items-start gap-3 cursor-pointer">
                <input type="checkbox" className="mt-0.5 accent-indigo-600 w-4 h-4"
                  checked={form.autorizacionDatos}
                  onChange={(e) => setForm({ ...form, autorizacionDatos: e.target.checked })} />
                <span className="text-sm text-gray-700">Acepto la autorización para el manejo de mis datos personales <span className="text-red-500">*</span></span>
              </label>
              {!form.autorizacionDatos && (
                <p className="text-xs text-red-500">Debes aceptar para continuar.</p>
              )}
            </div>
          )}

          {/* Paso 1: Datos personales */}
          {paso === 1 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold text-gray-800">Datos personales</h2>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Fecha de nacimiento</label>
                  <input type="date" className={inputCls} value={form.fechaNacimiento || ''} onChange={f('fechaNacimiento')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Lugar de nacimiento</label>
                  <input type="text" className={inputCls} value={form.lugarNacimiento || ''} onChange={f('lugarNacimiento')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Estado civil</label>
                  <select className={selectCls} value={form.estadoCivil || ''} onChange={f('estadoCivil')}>
                    <option value="">Selecciona...</option>
                    {['Soltero', 'Casado', 'Unión libre', 'Divorciado', 'Viudo', 'Separado'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">RH (grupo sanguíneo)</label>
                  <select className={selectCls} value={form.rh || ''} onChange={f('rh')}>
                    <option value="">Selecciona...</option>
                    {['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Celular</label>
                  <input type="text" className={inputCls} value={form.celular || ''} onChange={f('celular')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Teléfono fijo</label>
                  <input type="text" className={inputCls} value={form.telefonoFijo || ''} onChange={f('telefonoFijo')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                  <input type="email" className={inputCls} value={form.email || ''} onChange={f('email')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Nivel educativo</label>
                  <input type="text" className={inputCls} value={form.nivelEducativo || ''} onChange={f('nivelEducativo')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Estrato</label>
                  <select className={selectCls} value={form.estrato || ''} onChange={f('estrato')}>
                    <option value="">Selecciona...</option>
                    {['1', '2', '3', '4', '5', '6'].map(o => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Barrio de residencia</label>
                  <input type="text" className={inputCls} value={form.barrio || ''} onChange={f('barrio')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Libreta militar</label>
                  <input type="text" className={inputCls} value={form.libretaMilitar || ''} onChange={f('libretaMilitar')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Visa</label>
                  <input type="text" className={inputCls} value={form.visa || ''} onChange={f('visa')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Pasaporte</label>
                  <input type="text" className={inputCls} value={form.pasaporte || ''} onChange={f('pasaporte')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Fondo de pensiones</label>
                  <input type="text" className={inputCls} value={form.fondoPensiones || ''} onChange={f('fondoPensiones')} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">EPS</label>
                  <input type="text" className={inputCls} value={form.eps || ''} onChange={f('eps')} />
                </div>
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">Dirección de residencia</label>
                  <input type="text" className={inputCls} value={form.direccion || ''} onChange={f('direccion')} />
                </div>
              </div>
            </div>
          )}

          {/* Paso 2: Educación */}
          {paso === 2 && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-800">Educación</h2>
                <button onClick={agregarEducacion} className="text-sm text-indigo-600 hover:text-indigo-800 font-medium">+ Agregar</button>
              </div>
              {form.educacion.length === 0 && <p className="text-sm text-gray-400">No hay registros educativos.</p>}
              {form.educacion.map((edu, i) => (
                <div key={i} className="border border-gray-200 rounded-lg p-4 space-y-3">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium text-gray-700">Registro {i + 1}</span>
                    <button onClick={() => eliminarEducacion(i)} className="text-xs text-red-500 hover:text-red-700">Eliminar</button>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Nivel educativo</label>
                      <select className={smallSelectCls} value={edu.nivel || ''} onChange={(e) => actualizarEducacion(i, 'nivel', e.target.value)}>
                        <option value="">Selecciona...</option>
                        {['Bachillerato', 'Técnico', 'Tecnólogo', 'Profesional', 'Especialización', 'Maestría', 'Doctorado'].map(o => (
                          <option key={o} value={o}>{o}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Estado del estudio</label>
                      <select className={smallSelectCls} value={edu.estadoEstudio || ''} onChange={(e) => actualizarEducacion(i, 'estadoEstudio', e.target.value)}>
                        <option value="">Selecciona...</option>
                        {['En curso', 'Graduado', 'Suspendido', 'No culminado'].map(o => (
                          <option key={o} value={o}>{o}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Institución *</label>
                      <input className={smallInputCls} value={edu.institucion || ''} onChange={(e) => actualizarEducacion(i, 'institucion', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Título obtenido</label>
                      <input className={smallInputCls} value={edu.titulo || ''} onChange={(e) => actualizarEducacion(i, 'titulo', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Ciudad</label>
                      <input className={smallInputCls} value={edu.ciudad || ''} onChange={(e) => actualizarEducacion(i, 'ciudad', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha inicio</label>
                      <input type="date" className={smallInputCls} value={edu.fechaInicio || ''} onChange={(e) => actualizarEducacion(i, 'fechaInicio', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha fin</label>
                      <input type="date" className={smallInputCls}
                        value={edu.fechaFin || ''}
                        disabled={edu.estadoEstudio === 'En curso'}
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
                <button onClick={agregarExperiencia} className="text-sm text-indigo-600 hover:text-indigo-800 font-medium">+ Agregar</button>
              </div>

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
                          Inactividad de <strong>{gap.diasInactivo} días</strong> ({gap.fechaInicio} → {gap.fechaFin}) *
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

              {form.experienciaLaboral.length === 0 && <p className="text-sm text-gray-400">No hay registros de experiencia.</p>}
              {form.experienciaLaboral.map((exp, i) => (
                <div key={i} className="border border-gray-200 rounded-lg p-4 space-y-3">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium text-gray-700">{exp.empresa || `Empleo ${i + 1}`}</span>
                    <button onClick={() => eliminarExperiencia(i)} className="text-xs text-red-500 hover:text-red-700">Eliminar</button>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    {[['empresa', 'Empresa *'], ['cargo', 'Cargo *'], ['ciudad', 'Ciudad'], ['telefonoEmpresa', 'Teléfono empresa']].map(([campo, label]) => (
                      <div key={campo}>
                        <label className="block text-xs text-gray-500 mb-1">{label}</label>
                        <input className={smallInputCls}
                          value={exp[campo] || ''} onChange={(e) => actualizarExperiencia(i, campo, e.target.value)} />
                      </div>
                    ))}
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha inicio *</label>
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
                <button onClick={agregarReferencia} className="text-sm text-indigo-600 hover:text-indigo-800 font-medium">+ Agregar</button>
              </div>
              <p className="text-xs text-gray-400">Se requieren mínimo 2 referencias personales.</p>
              {form.referencias.length === 0 && <p className="text-sm text-gray-400">No hay referencias.</p>}
              {form.referencias.map((ref, i) => (
                <div key={i} className="border border-gray-200 rounded-lg p-4 space-y-3">
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium text-gray-700">Referencia {i + 1}</span>
                    <button onClick={() => eliminarReferencia(i)} className="text-xs text-red-500">Eliminar</button>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    {[['nombre', 'Nombre *'], ['parentesco', 'Parentesco'], ['telefono', 'Teléfono'], ['tiempoConocimiento', 'Tiempo de conocimiento']].map(([campo, label]) => (
                      <div key={campo}>
                        <label className="block text-xs text-gray-500 mb-1">{label}</label>
                        <input className={smallInputCls}
                          value={ref[campo] || ''} onChange={(e) => actualizarReferencia(i, campo, e.target.value)} />
                      </div>
                    ))}
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
                    Carga los siguientes documentos. Los marcados con <span className="text-red-500 font-medium">*</span> son obligatorios para continuar.
                  </p>
                  <div className="space-y-3">
                    {documentosRequeridos.map((doc) => (
                      <div
                        key={doc.id}
                        className={`border rounded-lg p-4 transition-colors ${archivos[doc.id] ? 'border-green-300 bg-green-50' : 'border-gray-200'}`}>
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-medium text-gray-800">
                              {doc.nombre}
                              {doc.obligatorio && <span className="text-red-500 ml-1">*</span>}
                              {!doc.obligatorio && <span className="text-gray-400 text-xs ml-2">(opcional)</span>}
                            </p>
                            {archivos[doc.id] && (
                              <p className="text-xs text-green-600 mt-0.5 truncate">{archivos[doc.id].name}</p>
                            )}
                          </div>
                          <span className="text-lg shrink-0">{archivos[doc.id] ? '✅' : '⏳'}</span>
                        </div>
                        <div className="mt-3 flex items-center gap-3">
                          <label className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-lg cursor-pointer hover:bg-indigo-100 transition-colors">
                            <input
                              type="file"
                              className="hidden"
                              accept=".pdf,.jpg,.jpeg,.png"
                              onChange={(e) => manejarArchivo(doc.id, e.target.files?.[0])}
                            />
                            {archivos[doc.id] ? 'Cambiar archivo' : 'Seleccionar archivo'}
                          </label>
                          {archivos[doc.id] && (
                            <button
                              type="button"
                              onClick={() => quitarArchivo(doc.id)}
                              className="text-xs text-red-500 hover:text-red-700">
                              Quitar
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                  {docsObligatoriosPendientes.length > 0 && (
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
                  {form.lugarNacimiento && <div><span className="text-gray-400">Lugar nacimiento: </span><span className="text-gray-800">{form.lugarNacimiento}</span></div>}
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
                            <td className="px-3 py-2 text-gray-600">{edu.estadoEstudio || '—'}</td>
                            <td className="px-3 py-2 text-gray-600">
                              {edu.estadoEstudio === 'En curso' ? 'En curso' : (edu.fechaFin || '—')}
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
                    {documentosRequeridos.map((doc) => (
                      <li key={doc.id} className="flex items-center gap-2 text-sm">
                        <span>{archivos[doc.id] ? '✅' : '⏳'}</span>
                        <span className={archivos[doc.id] ? 'text-gray-800' : 'text-gray-400'}>
                          {doc.nombre}
                        </span>
                        {doc.obligatorio && !archivos[doc.id] && (
                          <span className="text-red-500 text-xs">(pendiente)</span>
                        )}
                      </li>
                    ))}
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
            className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50">
            {paso === 6 ? 'Volver y editar' : 'Anterior'}
          </button>
          {paso < PASOS.length - 1 ? (
            <button
              onClick={() => setPaso((p) => p + 1)}
              disabled={
                (paso === 0 && !form.autorizacionDatos) ||
                (paso === 5 && docsObligatoriosPendientes.length > 0)
              }
              className="px-5 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg disabled:opacity-40 hover:bg-indigo-700">
              Siguiente
            </button>
          ) : (
            <button
              onClick={enviar}
              disabled={enviando}
              className="px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-lg disabled:opacity-40 hover:bg-green-700">
              {enviando ? 'Enviando...' : 'Confirmar y enviar'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export default EvalueeForm

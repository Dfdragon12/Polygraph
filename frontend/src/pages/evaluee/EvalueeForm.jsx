import { useState, useEffect, useRef } from 'react'
import { useParams } from 'react-router-dom'
import evaluadoService from '../../services/evaluadoService'
import WorkTimeline from '../../components/WorkTimeline'

const PASOS = ['Autorización', 'Datos personales', 'Educación', 'Experiencia laboral', 'Referencias', 'Documentos']

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
    fechaNacimiento: '', lugarNacimiento: '', estadoCivil: '', nivelEducativo: '',
    direccion: '', barrio: '', estrato: '', email: '', celular: '',
    educacion: [],
    experienciaLaboral: [],
    referencias: [],
  })

  const [inactividades, setInactividades] = useState([])

  useEffect(() => {
    evaluadoService.validarLink(token)
      .then((r) => setInfo(r.data))
      .catch((e) => setError(e.response?.status === 410
        ? 'Este link ya fue utilizado o ha expirado.'
        : 'Link inválido o no encontrado.'))
  }, [token])

  // Cargar progreso existente
  useEffect(() => {
    if (!info) return
    evaluadoService.obtenerFormulario(token)
      .then((r) => {
        const d = r.data
        setForm((prev) => ({ ...prev, ...d, educacion: d.educacion || [], experienciaLaboral: d.experienciaLaboral || [], referencias: d.referencias || [] }))
      })
      .catch(() => {})
  }, [info, token])

  // Auto-save cada 2 minutos
  useEffect(() => {
    if (!info || paso === 0) return
    autoSaveRef.current = setInterval(() => {
      evaluadoService.guardarProgreso(token, form).catch(() => {})
    }, 120000)
    return () => clearInterval(autoSaveRef.current)
  }, [form, info, paso, token])

  // Calcular inactividades cuando cambia la experiencia
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
        if (dias > 0) gaps.push({ fechaInicio: anterior.fechaFin || new Date().toISOString().slice(0,10), fechaFin: exp.fechaInicio, diasInactivo: dias })
      }
      anterior = exp
    }
    setInactividades(gaps)
  }, [form.experienciaLaboral])

  const agregarEducacion = () =>
    setForm((f) => ({ ...f, educacion: [...f.educacion, { nivel: '', institucion: '', titulo: '', fechaInicio: '', fechaFin: '', enCurso: false, ciudad: '' }] }))

  const actualizarEducacion = (i, campo, valor) =>
    setForm((f) => { const e = [...f.educacion]; e[i] = { ...e[i], [campo]: valor }; return { ...f, educacion: e } })

  const eliminarEducacion = (i) =>
    setForm((f) => ({ ...f, educacion: f.educacion.filter((_, idx) => idx !== i) }))

  const agregarExperiencia = () =>
    setForm((f) => ({ ...f, experienciaLaboral: [...f.experienciaLaboral, { empresa: '', cargo: '', fechaInicio: '', fechaFin: '', laboraActualmente: false, ciudad: '', telefonoEmpresa: '', motivoRetiro: '', nombreJefe: '', cargoJefe: '' }] }))

  const actualizarExperiencia = (i, campo, valor) =>
    setForm((f) => { const e = [...f.experienciaLaboral]; e[i] = { ...e[i], [campo]: valor }; return { ...f, experienciaLaboral: e } })

  const eliminarExperiencia = (i) =>
    setForm((f) => ({ ...f, experienciaLaboral: f.experienciaLaboral.filter((_, idx) => idx !== i) }))

  const agregarReferencia = () =>
    setForm((f) => ({ ...f, referencias: [...f.referencias, { nombre: '', parentesco: '', telefono: '', tiempoConocimiento: '' }] }))

  const actualizarReferencia = (i, campo, valor) =>
    setForm((f) => { const r = [...f.referencias]; r[i] = { ...r[i], [campo]: valor }; return { ...f, referencias: r } })

  const eliminarReferencia = (i) =>
    setForm((f) => ({ ...f, referencias: f.referencias.filter((_, idx) => idx !== i) }))

  const enviar = async () => {
    setEnviando(true)
    setError(null)
    try {
      await evaluadoService.enviarFormulario(token, form)
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
                {[
                  ['fecha_nacimiento', 'Fecha de nacimiento', 'date', 'fechaNacimiento'],
                  ['lugar_nacimiento', 'Lugar de nacimiento', 'text', 'lugarNacimiento'],
                  ['estado_civil', 'Estado civil', 'text', 'estadoCivil'],
                  ['nivel_educativo', 'Nivel educativo', 'text', 'nivelEducativo'],
                  ['celular', 'Celular', 'text', 'celular'],
                  ['email', 'Email', 'email', 'email'],
                  ['barrio', 'Barrio', 'text', 'barrio'],
                  ['estrato', 'Estrato', 'text', 'estrato'],
                ].map(([, label, type, key]) => (
                  <div key={key}>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
                    <input type={type} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                      value={form[key] || ''}
                      onChange={(e) => setForm({ ...form, [key]: e.target.value })} />
                  </div>
                ))}
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">Dirección de residencia</label>
                  <input className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                    value={form.direccion || ''} onChange={(e) => setForm({ ...form, direccion: e.target.value })} />
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
                    {[['nivel', 'Nivel'], ['institucion', 'Institución *'], ['titulo', 'Título obtenido'], ['ciudad', 'Ciudad']].map(([campo, label]) => (
                      <div key={campo}>
                        <label className="block text-xs text-gray-500 mb-1">{label}</label>
                        <input className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:ring-1 focus:ring-indigo-500 focus:outline-none"
                          value={edu[campo] || ''} onChange={(e) => actualizarEducacion(i, campo, e.target.value)} />
                      </div>
                    ))}
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha inicio</label>
                      <input type="date" className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
                        value={edu.fechaInicio || ''} onChange={(e) => actualizarEducacion(i, 'fechaInicio', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha fin</label>
                      <input type="date" className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
                        value={edu.fechaFin || ''} disabled={edu.enCurso}
                        onChange={(e) => actualizarEducacion(i, 'fechaFin', e.target.value)} />
                    </div>
                    <div className="col-span-2">
                      <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                        <input type="checkbox" checked={edu.enCurso || false}
                          onChange={(e) => actualizarEducacion(i, 'enCurso', e.target.checked)} />
                        Actualmente en curso
                      </label>
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
                        <input className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
                          value={exp[campo] || ''} onChange={(e) => actualizarExperiencia(i, campo, e.target.value)} />
                      </div>
                    ))}
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha inicio *</label>
                      <input type="date" className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
                        value={exp.fechaInicio || ''} onChange={(e) => actualizarExperiencia(i, 'fechaInicio', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Fecha retiro</label>
                      <input type="date" className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
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
                      <input className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
                        value={exp.nombreJefe || ''} onChange={(e) => actualizarExperiencia(i, 'nombreJefe', e.target.value)} />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Cargo del jefe</label>
                      <input className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
                        value={exp.cargoJefe || ''} onChange={(e) => actualizarExperiencia(i, 'cargoJefe', e.target.value)} />
                    </div>
                    <div className="col-span-2">
                      <label className="block text-xs text-gray-500 mb-1">Motivo de retiro</label>
                      <textarea rows={2} className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
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
                        <input className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none"
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
              <h2 className="text-lg font-semibold text-gray-800">Documentos</h2>
              <p className="text-sm text-gray-500">En esta fase los documentos serán solicitados directamente por el equipo de Polygraph Service. El envío del formulario no requiere archivos adjuntos por el momento.</p>
              <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 text-sm text-blue-700">
                Una vez enviado este formulario, recibirás instrucciones para entregar tus documentos.
              </div>
              {error && <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>}
            </div>
          )}
        </div>

        {/* Navegación */}
        <div className="flex justify-between mt-4">
          <button onClick={() => setPaso((p) => p - 1)} disabled={paso === 0}
            className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50">
            Anterior
          </button>
          {paso < PASOS.length - 1 ? (
            <button onClick={() => setPaso((p) => p + 1)}
              disabled={paso === 0 && !form.autorizacionDatos}
              className="px-5 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg disabled:opacity-40 hover:bg-indigo-700">
              Siguiente
            </button>
          ) : (
            <button onClick={enviar} disabled={enviando}
              className="px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-lg disabled:opacity-40 hover:bg-green-700">
              {enviando ? 'Enviando...' : 'Enviar hoja de vida'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export default EvalueeForm

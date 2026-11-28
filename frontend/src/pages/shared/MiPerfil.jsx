// "Mi perfil" de analista, poligrafista, programador, visitador y gestor: cada uno ve y edita SU
// información (nombre, apellido, teléfono y ciudad). Lo que administra el administrador (correo,
// rol, documento, ciudades que cubre, subprocesos) se muestra aparte, de solo lectura. Quien
// además ve cuánto falta para cambiar su contraseña.
import { useEffect, useId, useState } from 'react'
import perfilInternoService from '../../services/perfilInternoService'
import CiudadSelect from '../../components/CiudadSelect'
import Toast from '../../components/Toast'
import TarjetaPassword from '../../components/TarjetaPassword'

const ROLES = {
  ANALISTA_INTERNO: { etiqueta: 'Analista', cls: 'bg-sky-100 text-sky-700' },
  POLIGRAFISTA: { etiqueta: 'Poligrafista', cls: 'bg-violet-100 text-violet-700' },
  VISITADOR: { etiqueta: 'Visitador', cls: 'bg-emerald-100 text-emerald-700' },
  PROGRAMADOR: { etiqueta: 'Programador', cls: 'bg-amber-100 text-amber-700' },
  GESTOR: { etiqueta: 'Gestor', cls: 'bg-primary-100 text-primary-700' },
}

const TELEFONO_VALIDO = /^$|^[0-9+()\s-]{7,20}$/
const inputCls = 'w-full h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white focus:ring-2 focus:ring-primary-500 focus:outline-none aria-[invalid=true]:border-red-400'

const fechaHora = (iso) => (iso ? new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—')
const soloFecha = (iso) => (iso ? new Date(iso.length === 10 ? `${iso}T00:00:00` : iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'long', year: 'numeric' }) : '—')

function Tarjeta({ titulo, descripcion, children, className = '' }) {
  return (
    <section className={`bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5 ${className}`}>
      <h3 className="text-base font-semibold text-gray-900">{titulo}</h3>
      {descripcion && <p className="text-sm text-gray-500 mt-0.5">{descripcion}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

function Dato({ etiqueta, children }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-400">{etiqueta}</dt>
      <dd className="text-sm text-gray-800 mt-0.5 break-words">{children || '—'}</dd>
    </div>
  )
}

// Mantiene al día el nombre que muestran el encabezado y el menú, sin recargar la página.
function sincronizarSesion(perfil) {
  try {
    const guardado = JSON.parse(localStorage.getItem('usuario') ?? 'null')
    if (!guardado) return
    localStorage.setItem('usuario', JSON.stringify({ ...guardado, nombre: [perfil.nombre, perfil.apellido].filter(Boolean).join(' ') }))
    window.dispatchEvent(new Event('usuario-refrescado'))
  } catch { /* sin almacenamiento disponible, el nombre se actualiza en el próximo inicio de sesión */ }
}

// El backend manda las fallas de validación como { mensaje: 'Errores de validación', errores: { campo: 'detalle' } }:
// se muestra el detalle del campo, que es lo que le sirve a quien está llenando el formulario.
function mensajeDeError(err, porDefecto) {
  const datos = err.response?.data
  const primero = datos?.errores ? Object.values(datos.errores)[0] : null
  return primero ?? datos?.mensaje ?? porDefecto
}

export default function MiPerfil() {
  const idCampos = useId()
  const [perfil, setPerfil] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [form, setForm] = useState({ nombre: '', apellido: '', telefono: '', idCiudadResidencia: null })
  const [intentoGuardar, setIntentoGuardar] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [errorForm, setErrorForm] = useState(null)

  const aplicar = (p) => {
    setPerfil(p)
    setForm({ nombre: p.nombre ?? '', apellido: p.apellido ?? '', telefono: p.telefono ?? '', idCiudadResidencia: p.idCiudadResidencia ?? null })
    setIntentoGuardar(false)
    setErrorForm(null)
  }

  useEffect(() => {
    perfilInternoService.obtener().then(aplicar).catch(() => setError('No se pudo cargar tu perfil.'))
  }, [])

  const errores = {
    nombre: form.nombre.trim() ? null : 'El nombre es obligatorio.',
    telefono: TELEFONO_VALIDO.test(form.telefono.trim()) ? null : 'Usa solo números, espacios, + , - y paréntesis (7 a 20 caracteres).',
  }
  const hayErrores = Boolean(errores.nombre || errores.telefono)
  const hayCambios = perfil && (
    form.nombre !== (perfil.nombre ?? '') || form.apellido !== (perfil.apellido ?? '')
    || form.telefono !== (perfil.telefono ?? '') || (form.idCiudadResidencia ?? null) !== (perfil.idCiudadResidencia ?? null))

  const guardar = async (e) => {
    e.preventDefault()
    setIntentoGuardar(true)
    setErrorForm(null)
    if (hayErrores) return
    setGuardando(true)
    try {
      const actualizado = await perfilInternoService.actualizar({
        nombre: form.nombre, apellido: form.apellido, telefono: form.telefono, idCiudadResidencia: form.idCiudadResidencia,
      })
      aplicar(actualizado)
      sincronizarSesion(actualizado)
      setToast({ mensaje: 'Tus datos se guardaron.', tipo: 'exito' })
    } catch (err) {
      setErrorForm(mensajeDeError(err, 'No se pudieron guardar tus datos.'))
    } finally {
      setGuardando(false)
    }
  }

  if (error) return <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg" role="alert">{error}</div>
  if (!perfil) {
    return <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  }

  const rol = ROLES[perfil.rol] ?? { etiqueta: perfil.rol, cls: 'bg-gray-100 text-gray-700' }
  const nombreCompleto = [perfil.nombre, perfil.apellido].filter(Boolean).join(' ')
  const iniciales = [perfil.nombre, perfil.apellido].filter(Boolean).map(t => t[0]).join('').slice(0, 2).toUpperCase()
  const conSala = perfil.ciudadesAsignadas?.some(c => c.numeroSala != null)
  const tieneTrabajo = perfil.ciudadesAsignadas?.length > 0 || perfil.subprocesosAsignados?.length > 0

  return (
    <div className="max-w-5xl">
      {/* Encabezado */}
      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5 mb-4 flex flex-wrap items-center gap-4">
        <span className="h-16 w-16 rounded-full bg-primary-600 text-white text-xl font-bold flex items-center justify-center flex-shrink-0" aria-hidden="true">{iniciales}</span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-2xl font-bold text-gray-900 break-words">{nombreCompleto}</h2>
            <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${rol.cls}`}>{rol.etiqueta}</span>
          </div>
          <p className="text-sm text-gray-500 mt-0.5 break-all">{perfil.email}</p>
        </div>
        <dl className="flex gap-6 text-sm">
          <Dato etiqueta="Miembro desde">{soloFecha(perfil.fechaCreacion)}</Dato>
          <Dato etiqueta="Último acceso">{fechaHora(perfil.ultimoAcceso)}</Dato>
        </dl>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
        <div className="lg:col-span-2 space-y-4">
          <Tarjeta titulo="Mis datos" descripcion="Mantén tu información al día: es la que ve el equipo cuando necesita contactarte.">
            <form onSubmit={guardar} noValidate>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor={`${idCampos}-nombre`} className="block text-sm font-medium text-gray-700 mb-1">Nombre *</label>
                  <input id={`${idCampos}-nombre`} className={inputCls} value={form.nombre} maxLength={100} autoComplete="given-name"
                    aria-invalid={intentoGuardar && Boolean(errores.nombre)} aria-describedby={`${idCampos}-nombre-error`}
                    onChange={e => setForm(f => ({ ...f, nombre: e.target.value }))} />
                  {intentoGuardar && errores.nombre && <p id={`${idCampos}-nombre-error`} className="text-xs text-red-600 mt-1">{errores.nombre}</p>}
                </div>
                <div>
                  <label htmlFor={`${idCampos}-apellido`} className="block text-sm font-medium text-gray-700 mb-1">Apellido</label>
                  <input id={`${idCampos}-apellido`} className={inputCls} value={form.apellido} maxLength={100} autoComplete="family-name"
                    onChange={e => setForm(f => ({ ...f, apellido: e.target.value }))} />
                </div>
                <div>
                  <label htmlFor={`${idCampos}-telefono`} className="block text-sm font-medium text-gray-700 mb-1">Teléfono</label>
                  <input id={`${idCampos}-telefono`} type="tel" className={inputCls} value={form.telefono} maxLength={20} autoComplete="tel"
                    placeholder="310 555 1234" aria-invalid={Boolean(errores.telefono)} aria-describedby={`${idCampos}-telefono-ayuda`}
                    onChange={e => setForm(f => ({ ...f, telefono: e.target.value }))} />
                  <p id={`${idCampos}-telefono-ayuda`} className={`text-xs mt-1 ${errores.telefono ? 'text-red-600' : 'text-gray-400'}`}>
                    {errores.telefono ?? 'Para que el equipo pueda llamarte.'}
                  </p>
                </div>
                <div>
                  <label htmlFor={`${idCampos}-ciudad`} className="block text-sm font-medium text-gray-700 mb-1">Ciudad de residencia</label>
                  <CiudadSelect id={`${idCampos}-ciudad`} value={form.idCiudadResidencia}
                    onChange={v => setForm(f => ({ ...f, idCiudadResidencia: v || null }))} />
                </div>
              </div>

              {errorForm && <p className="mt-4 text-sm text-red-600" role="alert">{errorForm}</p>}

              <div className="mt-5 pt-4 border-t border-gray-100 flex flex-wrap items-center gap-3">
                <button type="submit" disabled={!hayCambios || guardando}
                  className="bg-primary-600 hover:bg-primary-700 disabled:opacity-40 text-white text-sm font-semibold px-5 py-2 rounded-lg transition-colors">
                  {guardando ? 'Guardando…' : 'Guardar cambios'}
                </button>
                {hayCambios && !guardando && (
                  <button type="button" onClick={() => aplicar(perfil)} className="text-sm text-gray-500 hover:text-gray-700">Descartar</button>
                )}
                <span className="text-xs text-gray-400" role="status">{hayCambios ? 'Tienes cambios sin guardar.' : 'Todo al día.'}</span>
              </div>
            </form>
          </Tarjeta>

          {tieneTrabajo && (
            <Tarjeta titulo="Mi trabajo" descripcion="Lo asigna el administrador. Si algo no es correcto, avísale.">
              <dl className="space-y-4">
                {perfil.ciudadesAsignadas?.length > 0 && (
                  <div>
                    <dt className="text-xs font-medium text-gray-400">Ciudades que cubres</dt>
                    <dd className="mt-1.5 flex flex-wrap gap-1.5">
                      {perfil.ciudadesAsignadas.map(c => (
                        <span key={c.idCiudad} className="text-xs bg-slate-100 text-slate-700 px-2.5 py-1 rounded-md">
                          {c.nombreCiudad}{conSala && c.numeroSala != null ? ` · Sala ${c.numeroSala}` : ''}
                        </span>
                      ))}
                    </dd>
                  </div>
                )}
                {perfil.subprocesosAsignados?.length > 0 && (
                  <div>
                    <dt className="text-xs font-medium text-gray-400">Subprocesos que realizas</dt>
                    <dd className="mt-1.5 flex flex-wrap gap-1.5">
                      {perfil.subprocesosAsignados.map(s => (
                        <span key={s.idTipoProgreso} className="text-xs bg-primary-50 text-primary-700 px-2.5 py-1 rounded-md">{s.nombreProgreso}</span>
                      ))}
                    </dd>
                  </div>
                )}
              </dl>
            </Tarjeta>
          )}
        </div>

        <div className="space-y-4">
          <TarjetaPassword datos={perfil} />

          <Tarjeta titulo="Mi cuenta" descripcion="Los define el administrador.">
            <dl className="space-y-4">
              <Dato etiqueta="Correo">{perfil.email}</Dato>
              <Dato etiqueta="Rol">{rol.etiqueta}</Dato>
              <Dato etiqueta="Documento">{perfil.documento ? `${perfil.tipoDocumento ?? ''} ${perfil.documento}`.trim() : null}</Dato>
              {perfil.fechaIngreso && <Dato etiqueta="Fecha de ingreso">{soloFecha(perfil.fechaIngreso)}</Dato>}
            </dl>
          </Tarjeta>
        </div>
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

// "Mi perfil" del analista de un cliente: sus datos personales (nombre y apellido), su empresa y su
// gestor de solo lectura, y el estado de su contraseña. Mismo diseño que "Mi perfil" de los usuarios
// internos y "Información de la empresa" del administrador del cliente.
import { parseFecha } from '../../utils/fechas'
import { useCallback, useEffect, useId, useState } from 'react'
import perfilService from '../../services/perfilService'
import Toast from '../../components/Toast'
import TarjetaPassword from '../../components/TarjetaPassword'

const inputCls = 'w-full h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white focus:ring-2 focus:ring-primary-500 focus:outline-none aria-[invalid=true]:border-red-400'

const fechaHora = (iso) => (iso ? new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—')
const fechaLarga = (iso) => (iso ? parseFecha(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'long', year: 'numeric' }) : '—')

function Tarjeta({ titulo, descripcion, children }) {
  return (
    <section className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5">
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

export default function MiPerfilAnalista() {
  const idCampos = useId()
  const [perfil, setPerfil] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [form, setForm] = useState({ nombre: '', apellido: '' })
  const [intentoGuardar, setIntentoGuardar] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [errorForm, setErrorForm] = useState(null)

  const aplicar = useCallback((p) => {
    setPerfil(p)
    setForm({ nombre: p.nombre ?? '', apellido: p.apellido ?? '' })
    setIntentoGuardar(false)
    setErrorForm(null)
  }, [])

  useEffect(() => {
    perfilService.miPerfil().then(aplicar).catch(() => setError('No se pudo cargar tu perfil.'))
  }, [aplicar])

  if (error) return <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg" role="alert">{error}</div>
  if (!perfil) {
    return <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  }

  const nombreCompleto = [perfil.nombre, perfil.apellido].filter(Boolean).join(' ')
  const iniciales = [perfil.nombre, perfil.apellido].filter(Boolean).map(t => t[0]).join('').slice(0, 2).toUpperCase()
  const errorNombre = form.nombre.trim() ? null : 'El nombre es obligatorio.'
  const hayCambios = form.nombre !== (perfil.nombre ?? '') || form.apellido !== (perfil.apellido ?? '')

  const guardar = async (e) => {
    e.preventDefault()
    setIntentoGuardar(true)
    setErrorForm(null)
    if (errorNombre) return
    setGuardando(true)
    try {
      const actualizado = await perfilService.actualizarMiPerfil({ nombre: form.nombre, apellido: form.apellido })
      aplicar(actualizado)
      sincronizarSesion(actualizado)
      setToast({ mensaje: 'Tus datos se guardaron.', tipo: 'exito' })
    } catch (err) {
      const datos = err.response?.data
      setErrorForm((datos?.errores ? Object.values(datos.errores)[0] : null) ?? datos?.mensaje ?? 'No se pudieron guardar tus datos.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="max-w-5xl">
      {/* Encabezado */}
      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5 mb-4 flex flex-wrap items-center gap-4">
        <span className="h-16 w-16 rounded-full bg-primary-600 text-white text-xl font-bold flex items-center justify-center flex-shrink-0" aria-hidden="true">{iniciales}</span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-2xl font-bold text-gray-900 break-words">{nombreCompleto}</h2>
            <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-sky-100 text-sky-700">Analista</span>
          </div>
          <p className="text-sm text-gray-500 mt-0.5 break-all">{perfil.nombreEmpresa ? `${perfil.nombreEmpresa} · ` : ''}{perfil.email}</p>
        </div>
        <dl className="flex gap-6 text-sm">
          <Dato etiqueta="Miembro desde">{fechaLarga(perfil.miembroDesde)}</Dato>
          <Dato etiqueta="Último acceso">{fechaHora(perfil.ultimoAcceso)}</Dato>
        </dl>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
        <div className="lg:col-span-2">
          <Tarjeta titulo="Mis datos" descripcion="Así apareces en las solicitudes que haces y en los mensajes con tu gestor.">
            <form onSubmit={guardar} noValidate>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor={`${idCampos}-nombre`} className="block text-sm font-medium text-gray-700 mb-1">Nombre *</label>
                  <input id={`${idCampos}-nombre`} className={inputCls} value={form.nombre} maxLength={100} autoComplete="given-name"
                    aria-invalid={intentoGuardar && Boolean(errorNombre)} aria-describedby={`${idCampos}-nombre-error`}
                    onChange={e => setForm(f => ({ ...f, nombre: e.target.value }))} />
                  {intentoGuardar && errorNombre && <p id={`${idCampos}-nombre-error`} className="text-xs text-red-600 mt-1">{errorNombre}</p>}
                </div>
                <div>
                  <label htmlFor={`${idCampos}-apellido`} className="block text-sm font-medium text-gray-700 mb-1">Apellido</label>
                  <input id={`${idCampos}-apellido`} className={inputCls} value={form.apellido} maxLength={100} autoComplete="family-name"
                    onChange={e => setForm(f => ({ ...f, apellido: e.target.value }))} />
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
        </div>

        <div className="space-y-4">
          <TarjetaPassword datos={perfil} />
          <Tarjeta titulo="Mi cuenta" descripcion="Estos datos no se pueden cambiar desde aquí.">
            <dl className="space-y-4">
              <Dato etiqueta="Correo">{perfil.email}</Dato>
              <Dato etiqueta="Empresa">{perfil.nombreEmpresa}</Dato>
              <Dato etiqueta="Tu gestor">
                {perfil.nombreGestor ? (
                  <>
                    <span className="block">{perfil.nombreGestor}</span>
                    {perfil.emailGestor && <span className="block text-xs text-gray-500 break-all">{perfil.emailGestor}</span>}
                  </>
                ) : null}
              </Dato>
            </dl>
          </Tarjeta>
        </div>
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

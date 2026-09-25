import { useState, useEffect, useCallback } from 'react'
import perfilService from '../../services/perfilService'
import Toast from '../../components/Toast'
import { Modal } from '../../components/ui/Modal'

// Mismo tope que ClienteAdminService.MAX_ANALISTAS_CLIENTE en el backend — el backend es la
// fuente de verdad real (rechaza con 409 si se pasa), esto solo evita mostrar el formulario de
// una vez que ya se sabe que no va a funcionar.
const MAX_USUARIOS = 2

function formatFecha(iso) {
  if (!iso) return 'Nunca ha ingresado'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function iniciales(nombre, apellido) {
  const a = (nombre ?? '').trim()[0] ?? ''
  const b = (apellido ?? '').trim()[0] ?? ''
  return (a + b).toUpperCase() || '—'
}

function ModalCrearUsuario({ onClose, onCreado, onToast }) {
  const [form, setForm] = useState({ nombre: '', apellido: '', email: '', password: '' })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const cambiar = (e) => {
    const { name, value } = e.target
    setForm((p) => ({ ...p, [name]: value }))
  }

  const guardar = async (e) => {
    e.preventDefault()
    if (form.password.length < 8) { setError('La contraseña debe tener mínimo 8 caracteres.'); return }
    setGuardando(true)
    setError(null)
    try {
      const nuevo = await perfilService.crearUsuario(form)
      onCreado(nuevo)
      onToast(`Usuario ${nuevo.nombre} creado correctamente.`)
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo crear el usuario.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Nuevo usuario de tu equipo" subtitulo="Podrá solicitar y ver el estado de tus estudios" onClose={onClose} ancho="max-w-md">
      <form onSubmit={guardar} className="px-6 py-5 space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
            <input name="nombre" value={form.nombre} onChange={cambiar} required maxLength={100}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Apellido</label>
            <input name="apellido" value={form.apellido} onChange={cambiar} maxLength={100}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Correo electrónico <span className="text-red-500">*</span></label>
          <input type="email" name="email" value={form.email} onChange={cambiar} required maxLength={150}
            placeholder="usuario@empresa.com"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Contraseña temporal <span className="text-red-500">*</span></label>
          <input type="password" name="password" value={form.password} onChange={cambiar} required minLength={8} maxLength={50}
            placeholder="Mínimo 8 caracteres"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          <p className="text-xs text-gray-400 mt-1">Se le pedirá cambiarla la primera vez que inicie sesión.</p>
        </div>
        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Creando...' : 'Crear usuario'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ModalEditarUsuario({ usuario, onClose, onGuardado, onToast }) {
  const [form, setForm] = useState({ nombre: usuario.nombre, apellido: usuario.apellido ?? '', email: usuario.email, password: '' })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const cambiar = (e) => {
    const { name, value } = e.target
    setForm((p) => ({ ...p, [name]: value }))
  }

  const guardar = async (e) => {
    e.preventDefault()
    if (form.password && form.password.length < 8) { setError('La contraseña debe tener mínimo 8 caracteres.'); return }
    setGuardando(true)
    setError(null)
    try {
      const actualizado = await perfilService.actualizarUsuario(usuario.idUsuario, form)
      onGuardado(actualizado)
      onToast(`Usuario ${actualizado.nombre} actualizado correctamente.`)
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo actualizar el usuario.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Editar usuario" subtitulo={usuario.email} onClose={onClose} ancho="max-w-md">
      <form onSubmit={guardar} className="px-6 py-5 space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
            <input name="nombre" value={form.nombre} onChange={cambiar} required maxLength={100}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Apellido</label>
            <input name="apellido" value={form.apellido} onChange={cambiar} maxLength={100}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Correo electrónico <span className="text-red-500">*</span></label>
          <input type="email" name="email" value={form.email} onChange={cambiar} required maxLength={150}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Nueva contraseña</label>
          <input type="password" name="password" value={form.password} onChange={cambiar} minLength={8} maxLength={50}
            placeholder="Déjalo vacío para no cambiarla"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function IconoLapiz() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
    </svg>
  )
}
function IconoPower() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5.636 5.636a9 9 0 1012.728 0M12 3v9" />
    </svg>
  )
}

export default function MiEquipo() {
  const [usuarios, setUsuarios] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [modalAbierto, setModalAbierto] = useState(false)
  const [editando, setEditando] = useState(null)
  const [cambiandoEstadoId, setCambiandoEstadoId] = useState(null)

  const cargar = useCallback(() => {
    perfilService.misUsuarios()
      .then(setUsuarios)
      .catch(() => setError('No se pudieron cargar tus usuarios.'))
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const enElTope = (usuarios?.length ?? 0) >= MAX_USUARIOS

  const actualizarEnLista = (actualizado) =>
    setUsuarios((prev) => (prev ?? []).map((u) => (u.idUsuario === actualizado.idUsuario ? actualizado : u)))

  const alternarActivo = async (u) => {
    setCambiandoEstadoId(u.idUsuario)
    try {
      const actualizado = u.activo
        ? await perfilService.desactivarUsuario(u.idUsuario)
        : await perfilService.activarUsuario(u.idUsuario)
      actualizarEnLista(actualizado)
    } catch {
      setError('No se pudo cambiar el estado del usuario.')
    } finally {
      setCambiandoEstadoId(null)
    }
  }

  return (
    <div className="max-w-3xl mx-auto space-y-5">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Mi Equipo</h2>
        <p className="text-sm text-gray-500 mt-0.5">
          Usuarios adicionales de tu empresa que pueden solicitar y ver el estado de los estudios — hasta {MAX_USUARIOS}.
        </p>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
      )}

      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-800">
            Usuarios {usuarios && <span className="text-gray-400 font-normal">({usuarios.length}/{MAX_USUARIOS})</span>}
          </h3>
          {!enElTope && (
            <button onClick={() => setModalAbierto(true)}
              className="inline-flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-xs font-medium px-3.5 py-2 rounded-lg transition-colors">
              <span className="text-sm leading-none">+</span> Nuevo usuario
            </button>
          )}
        </div>

        {usuarios === null ? (
          <div className="flex items-center justify-center h-32">
            <div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" />
          </div>
        ) : usuarios.length === 0 ? (
          <div className="text-center py-12 px-4">
            <p className="text-sm text-gray-400 mb-1">Todavía no tienes usuarios adicionales.</p>
            <p className="text-xs text-gray-300">Crea hasta {MAX_USUARIOS} para que tu equipo pueda solicitar y consultar estudios.</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-50">
            {usuarios.map((u) => (
              <div key={u.idUsuario} className="px-5 py-3 flex items-center gap-3">
                <div className="w-9 h-9 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center text-xs font-bold flex-shrink-0">
                  {iniciales(u.nombre, u.apellido)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-gray-900 truncate">{u.nombre} {u.apellido ?? ''}</p>
                  <p className="text-xs text-gray-400 truncate">{u.email}</p>
                </div>
                <div className="text-right flex-shrink-0">
                  <span className={`text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${
                    u.activo ? 'bg-emerald-100 text-emerald-700' : 'bg-gray-100 text-gray-500'
                  }`}>
                    {u.activo ? 'Activo' : 'Inactivo'}
                  </span>
                  <p className="text-[11px] text-gray-400 mt-1">{formatFecha(u.ultimoAcceso)}</p>
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                  <button onClick={() => setEditando(u)} title="Editar"
                    className="p-1.5 rounded-lg text-primary-600 hover:bg-primary-50 hover:text-primary-800 transition-colors">
                    <IconoLapiz />
                  </button>
                  <button onClick={() => alternarActivo(u)} disabled={cambiandoEstadoId === u.idUsuario}
                    title={u.activo ? 'Desactivar' : 'Activar'}
                    className={`p-1.5 rounded-lg transition-colors disabled:opacity-40 ${
                      u.activo ? 'text-red-600 hover:bg-red-50 hover:text-red-800' : 'text-green-700 hover:bg-green-50 hover:text-green-900'
                    }`}>
                    <IconoPower />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {enElTope && (
        <div className="bg-amber-50 border border-amber-200 rounded-2xl px-4 py-3 flex items-start gap-3">
          <span className="text-lg leading-none flex-shrink-0">✉️</span>
          <p className="text-sm text-amber-800">
            Ya tienes el máximo de {MAX_USUARIOS} usuarios. Si necesitas uno adicional, escríbele a tu gestor
            desde el ícono de mensajes en la parte superior.
          </p>
        </div>
      )}

      {modalAbierto && (
        <ModalCrearUsuario
          onClose={() => setModalAbierto(false)}
          onCreado={(nuevo) => setUsuarios((prev) => [...(prev ?? []), nuevo])}
          onToast={(mensaje) => setToast({ mensaje, tipo: 'exito' })}
        />
      )}

      {editando && (
        <ModalEditarUsuario
          usuario={editando}
          onClose={() => setEditando(null)}
          onGuardado={actualizarEnLista}
          onToast={(mensaje) => setToast({ mensaje, tipo: 'exito' })}
        />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

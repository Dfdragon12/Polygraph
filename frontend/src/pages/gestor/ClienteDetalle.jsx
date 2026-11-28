import { useId, useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import mensajeService from '../../services/mensajeService'
import gestorClienteService from '../../services/gestorClienteService'
import Toast from '../../components/Toast'
import { Modal } from '../../components/ui/Modal'

const ETIQUETA_ROL = { ADMIN_CLIENTE: 'Admin', ANALISTA_CLIENTE: 'Analista' }

function iniciales(nombre) {
  return (nombre ?? '').trim()[0]?.toUpperCase() ?? '?'
}

function formatHora(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

function formatRelativo(iso) {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return 'Ahora'
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h} h`
  return `${Math.floor(h / 24)} d`
}

function ModalNuevoUsuario({ idCliente, onClose, onCreado, onToast }) {
  const idCampos = useId()
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
      const nuevo = await gestorClienteService.crearUsuario(idCliente, form)
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
    <Modal titulo="Nuevo usuario para este cliente" subtitulo="Se crea sin tope, como excepción" onClose={onClose} ancho="max-w-md">
      <form onSubmit={guardar} className="px-6 py-5 space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
            <input id={`${idCampos}-1`} name="nombre" value={form.nombre} onChange={cambiar} required maxLength={100}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Apellido</label>
            <input id={`${idCampos}-2`} name="apellido" value={form.apellido} onChange={cambiar} maxLength={100}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
        </div>
        <div>
          <label htmlFor={`${idCampos}-3`} className="block text-xs font-medium text-gray-600 mb-1">Correo electrónico <span className="text-red-500">*</span></label>
          <input id={`${idCampos}-3`} type="email" name="email" value={form.email} onChange={cambiar} required maxLength={150}
            placeholder="usuario@empresa.com"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div>
          <label htmlFor={`${idCampos}-4`} className="block text-xs font-medium text-gray-600 mb-1">Contraseña temporal <span className="text-red-500">*</span></label>
          <input id={`${idCampos}-4`} type="password" name="password" value={form.password} onChange={cambiar} required minLength={8} maxLength={50}
            placeholder="Mínimo 8 caracteres"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
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

/** Detalle de un cliente para el gestor: sus usuarios a la izquierda (cada uno con su propio
 *  hilo de mensajes — ver modulos/mensajes), el hilo activo a la derecha. Reemplaza el panelito
 *  desplegable que tenía HeaderMensajesGestor antes — el usuario pidió entrar a un módulo en vez
 *  de expandir inline. */
export default function ClienteDetalle() {
  const { idCliente } = useParams()
  const navigate = useNavigate()
  const idClienteNum = Number(idCliente)

  const [conversaciones, setConversaciones] = useState(null)
  const [usuarioActivo, setUsuarioActivo] = useState(null)
  const [mensajes, setMensajes] = useState([])
  const [cargandoHilo, setCargandoHilo] = useState(false)
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [modalNuevoAbierto, setModalNuevoAbierto] = useState(false)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const finMensajesRef = useRef(null)

  const cargarConversaciones = useCallback(() => {
    return mensajeService.misConversaciones()
      .then((data) => {
        const propias = data.filter((c) => c.idCliente === idClienteNum)
        setConversaciones(propias)
        return propias
      })
      .catch(() => { setError('No se pudo cargar la información de este cliente.'); return [] })
  }, [idClienteNum])

  useEffect(() => { cargarConversaciones() }, [cargarConversaciones])

  const abrirHilo = useCallback((conv) => {
    setUsuarioActivo(conv)
    setCargandoHilo(true)
    mensajeService.listarGestor(conv.idUsuarioCliente)
      .then((data) => {
        setMensajes(data)
        // Refresca los contadores de no leídos de la lista (listarGestor ya los marcó leídos en el backend).
        cargarConversaciones()
      })
      .catch(() => setError('No se pudo cargar la conversación.'))
      .finally(() => setCargandoHilo(false))
  }, [cargarConversaciones])

  useEffect(() => {
    finMensajesRef.current?.scrollIntoView({ block: 'nearest' })
  }, [mensajes])

  const nombreCliente = conversaciones?.[0]?.nombreCliente ?? ''

  const enviar = async (e) => {
    e.preventDefault()
    const valor = texto.trim()
    if (!valor || !usuarioActivo) return
    setEnviando(true)
    try {
      const nuevo = await mensajeService.enviarGestor(usuarioActivo.idUsuarioCliente, valor)
      setMensajes((prev) => [...prev, nuevo])
      setTexto('')
      cargarConversaciones()
    } catch {
      setError('No se pudo enviar el mensaje.')
    } finally {
      setEnviando(false)
    }
  }

  const usuarioCreado = (nuevo) => {
    cargarConversaciones().then((propias) => {
      const conv = propias.find((c) => c.idUsuarioCliente === nuevo.idUsuario)
      if (conv) abrirHilo(conv)
    })
  }

  return (
    <div className="max-w-5xl mx-auto space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 min-w-0">
          <button onClick={() => navigate(-1)} title="Volver"
            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors flex-shrink-0">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h2 className="text-xl font-bold text-gray-900 truncate">{nombreCliente || 'Cliente'}</h2>
        </div>
        <button onClick={() => setModalNuevoAbierto(true)}
          className="inline-flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-xs font-medium px-3.5 py-2 rounded-lg transition-colors flex-shrink-0">
          <span className="text-sm leading-none">+</span> Nuevo usuario
        </button>
      </div>

      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden grid grid-cols-1 sm:grid-cols-[220px_1fr]" style={{ minHeight: '520px' }}>
        <div className="border-b sm:border-b-0 sm:border-r border-gray-100 flex flex-col">
          <div className="px-4 py-3 border-b border-gray-100 flex-shrink-0">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Usuarios</p>
          </div>
          <div className="flex-1 overflow-y-auto divide-y divide-gray-50 max-h-80 sm:max-h-none">
            {conversaciones === null ? (
              <div className="flex items-center justify-center h-32">
                <div className="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent" />
              </div>
            ) : conversaciones.length === 0 ? (
              <p className="text-xs text-gray-400 text-center py-8 px-3">Este cliente aún no tiene usuarios.</p>
            ) : conversaciones.map((c) => (
              <button key={c.idUsuarioCliente} onClick={() => abrirHilo(c)}
                className={`w-full text-left px-4 py-2.5 flex items-center gap-2.5 transition-colors ${
                  usuarioActivo?.idUsuarioCliente === c.idUsuarioCliente ? 'bg-primary-50' : 'hover:bg-gray-50'
                }`}>
                <div className="w-8 h-8 rounded-full bg-primary-600 text-white text-xs font-semibold flex items-center justify-center flex-shrink-0">
                  {iniciales(c.nombreUsuarioCliente)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium text-gray-900 truncate">{c.nombreUsuarioCliente}</p>
                  <p className="text-[10px] text-gray-400">{ETIQUETA_ROL[c.rolUsuarioCliente] ?? c.rolUsuarioCliente}</p>
                </div>
                {c.noLeidos > 0 && (
                  <span className="text-[9px] bg-primary-600 text-white rounded-full px-1.5 py-0.5 font-semibold flex-shrink-0">{c.noLeidos}</span>
                )}
              </button>
            ))}
          </div>
        </div>

        <div className="flex flex-col">
          {!usuarioActivo ? (
            <div className="flex-1 flex items-center justify-center">
              <p className="text-sm text-gray-400">Selecciona un usuario para ver la conversación.</p>
            </div>
          ) : (
            <>
              <div className="px-4 py-3 border-b border-gray-100 flex-shrink-0">
                <p className="text-sm font-semibold text-gray-800">{usuarioActivo.nombreUsuarioCliente}</p>
                <p className="text-[11px] text-gray-400">{ETIQUETA_ROL[usuarioActivo.rolUsuarioCliente] ?? usuarioActivo.rolUsuarioCliente}</p>
              </div>
              <div className="flex-1 overflow-y-auto px-4 py-3 space-y-2" style={{ maxHeight: '380px' }}>
                {cargandoHilo ? (
                  <div className="flex items-center justify-center h-32">
                    <div className="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent" />
                  </div>
                ) : mensajes.length === 0 ? (
                  <p className="text-xs text-gray-400 text-center py-8">Todavía no hay mensajes en esta conversación.</p>
                ) : mensajes.map((m) => {
                  const esMio = m.origen === 'GESTOR'
                  return (
                    <div key={m.id} className={`flex ${esMio ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[75%] rounded-xl px-3 py-2 text-sm ${
                        esMio ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-800'
                      }`}>
                        <p className="whitespace-pre-wrap break-words">{m.mensaje}</p>
                        <p className={`text-[10px] mt-1 text-right ${esMio ? 'text-primary-100' : 'text-gray-400'}`}>
                          {formatHora(m.fechaEnvio)}
                        </p>
                      </div>
                    </div>
                  )
                })}
                <div ref={finMensajesRef} />
              </div>
              <form onSubmit={enviar} className="px-4 py-3 border-t border-gray-100 flex gap-2 flex-shrink-0">
                <input aria-label="Escribe un mensaje..." value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Escribe un mensaje..."
                  className="flex-1 border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                <button type="submit" disabled={enviando || !texto.trim()}
                  className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors flex-shrink-0">
                  Enviar
                </button>
              </form>
            </>
          )}
        </div>
      </div>

      {modalNuevoAbierto && (
        <ModalNuevoUsuario idCliente={idClienteNum} onClose={() => setModalNuevoAbierto(false)}
          onCreado={usuarioCreado} onToast={(mensaje) => setToast({ mensaje, tipo: 'exito' })} />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

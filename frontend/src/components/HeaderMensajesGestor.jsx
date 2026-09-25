import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import mensajeService from '../services/mensajeService'

const ETIQUETA_ROL = { ADMIN_CLIENTE: 'Admin', ANALISTA_CLIENTE: 'Analista' }

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

function Avatar({ texto }) {
  return (
    <div className="w-8 h-8 rounded-full bg-primary-600 text-white text-xs font-semibold flex items-center justify-center flex-shrink-0">
      {texto?.trim()[0]?.toUpperCase() ?? '?'}
    </div>
  )
}

function IconoAtras() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
    </svg>
  )
}

function IconoExpandir() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5l-5-5m5 5v-4m0 4h-4" />
    </svg>
  )
}

/** Campanita de mensajes del gestor: lista de empresas con no leídos, y al hacer clic se puede
 *  escribir directo ahí mismo (sin salir del cuadro) — si la empresa tiene varios usuarios
 *  (admin + analistas) primero se elige con cuál. La página de detalle del cliente
 *  (pages/gestor/ClienteDetalle.jsx) sigue existiendo para crear usuarios nuevos y ver todo con
 *  más espacio; un ícono en el hilo lleva para allá. */
export default function HeaderMensajesGestor() {
  const navigate = useNavigate()
  const [abierto, setAbierto] = useState(false)
  const [conversaciones, setConversaciones] = useState([])
  const [totalNoLeidos, setTotalNoLeidos] = useState(0)
  const [vista, setVista] = useState('empresas') // 'empresas' | 'usuarios' | 'hilo'
  const [idClienteActivo, setIdClienteActivo] = useState(null)
  const [usuarioActivo, setUsuarioActivo] = useState(null)
  const [mensajes, setMensajes] = useState(null)
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const ref = useRef(null)
  const finRef = useRef(null)

  const cargarConversaciones = useCallback(() => {
    return mensajeService.misConversaciones().then((data) => {
      setConversaciones(data)
      setTotalNoLeidos(data.reduce((acc, c) => acc + c.noLeidos, 0))
      return data
    }).catch(() => [])
  }, [])

  useEffect(() => {
    cargarConversaciones()
    const t = setInterval(cargarConversaciones, 20000)
    return () => clearInterval(t)
  }, [cargarConversaciones])

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const empresas = useMemo(() => {
    const mapa = new Map()
    for (const c of conversaciones) {
      if (!mapa.has(c.idCliente)) mapa.set(c.idCliente, { idCliente: c.idCliente, nombreCliente: c.nombreCliente, noLeidos: 0, ultimoMensaje: null, fechaUltimoMensaje: null, usuarios: [] })
      const grupo = mapa.get(c.idCliente)
      grupo.noLeidos += c.noLeidos
      grupo.usuarios.push(c)
      if (!grupo.fechaUltimoMensaje || (c.fechaUltimoMensaje && c.fechaUltimoMensaje > grupo.fechaUltimoMensaje)) {
        grupo.fechaUltimoMensaje = c.fechaUltimoMensaje
        grupo.ultimoMensaje = c.ultimoMensaje
      }
    }
    return [...mapa.values()].sort((a, b) => (b.fechaUltimoMensaje ?? '').localeCompare(a.fechaUltimoMensaje ?? ''))
  }, [conversaciones])

  const empresaActiva = useMemo(() => empresas.find((e) => e.idCliente === idClienteActivo) ?? null, [empresas, idClienteActivo])

  const abrirHilo = useCallback((conv) => {
    setUsuarioActivo(conv)
    setVista('hilo')
    setMensajes(null)
    mensajeService.listarGestor(conv.idUsuarioCliente)
      .then((data) => { setMensajes(data); cargarConversaciones() })
      .catch(() => setMensajes([]))
  }, [cargarConversaciones])

  const elegirEmpresa = (emp) => {
    setIdClienteActivo(emp.idCliente)
    if (emp.usuarios.length === 1) {
      abrirHilo(emp.usuarios[0])
    } else {
      setVista('usuarios')
    }
  }

  const volver = () => {
    if (vista === 'hilo' && empresaActiva && empresaActiva.usuarios.length > 1) {
      setVista('usuarios')
    } else {
      setVista('empresas')
      setIdClienteActivo(null)
      setUsuarioActivo(null)
    }
  }

  // Refresca el hilo activo mientras está abierto, igual que el widget del cliente.
  useEffect(() => {
    if (vista !== 'hilo' || !usuarioActivo) return
    const t = setInterval(() => {
      mensajeService.listarGestor(usuarioActivo.idUsuarioCliente).then(setMensajes).catch(() => {})
    }, 6000)
    return () => clearInterval(t)
  }, [vista, usuarioActivo])

  useEffect(() => {
    if (vista === 'hilo') finRef.current?.scrollIntoView({ block: 'end' })
  }, [mensajes, vista])

  const enviar = async (e) => {
    e.preventDefault()
    const valor = texto.trim()
    if (!valor || !usuarioActivo) return
    setEnviando(true)
    try {
      const nuevo = await mensajeService.enviarGestor(usuarioActivo.idUsuarioCliente, valor)
      setMensajes((prev) => [...(prev ?? []), nuevo])
      setTexto('')
      cargarConversaciones()
    } catch { /* silencioso */ }
    finally { setEnviando(false) }
  }

  const irADetalle = () => {
    setAbierto(false)
    navigate(`/gestor/clientes/${idClienteActivo}`)
  }

  const tituloHeader = vista === 'empresas' ? 'Mensajes con clientes'
    : vista === 'usuarios' ? empresaActiva?.nombreCliente
    : usuarioActivo?.nombreUsuarioCliente

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setAbierto((v) => !v)} title="Mensajes con clientes"
        className="relative p-2 text-gray-500 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors">
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8-1.284 0-2.503-.24-3.61-.673L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
        </svg>
        {totalNoLeidos > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold leading-none">
            {totalNoLeidos > 9 ? '9+' : totalNoLeidos}
          </span>
        )}
      </button>

      {abierto && (
        <div className="fixed left-3 right-3 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-11 sm:w-80 bg-white rounded-xl shadow-xl border border-gray-200 z-50 flex flex-col" style={{ height: 'min(460px, 70vh)' }}>
          <div className="px-4 py-3 border-b border-gray-100 bg-primary-50 flex items-center gap-2 flex-shrink-0">
            {vista !== 'empresas' && (
              <button onClick={volver} className="text-primary-700 hover:text-primary-900 flex-shrink-0">
                <IconoAtras />
              </button>
            )}
            <p className="text-xs font-semibold text-primary-700 uppercase tracking-wider truncate flex-1 min-w-0">{tituloHeader}</p>
            {vista === 'hilo' && (
              <button onClick={irADetalle} title="Ver detalle completo del cliente"
                className="text-primary-600 hover:text-primary-900 flex-shrink-0">
                <IconoExpandir />
              </button>
            )}
          </div>

          {vista === 'empresas' && (
            <div className="flex-1 overflow-y-auto divide-y divide-gray-50">
              {empresas.length === 0 ? (
                <p className="text-xs text-gray-400 text-center py-8 px-3">No tienes clientes asignados todavía.</p>
              ) : empresas.map((emp) => (
                <button key={emp.idCliente} onClick={() => elegirEmpresa(emp)}
                  className="w-full text-left px-4 py-2.5 flex items-center gap-2.5 hover:bg-gray-50 transition-colors">
                  <Avatar texto={emp.nombreCliente} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs font-medium text-gray-900 truncate">{emp.nombreCliente}</p>
                      {emp.fechaUltimoMensaje && <span className="text-[9px] text-gray-400 flex-shrink-0">{formatRelativo(emp.fechaUltimoMensaje)}</span>}
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-[11px] text-gray-400 truncate">{emp.ultimoMensaje ?? 'Sin mensajes'}</p>
                      {emp.noLeidos > 0 && (
                        <span className="text-[9px] bg-primary-600 text-white rounded-full px-1.5 py-0.5 font-semibold flex-shrink-0">{emp.noLeidos}</span>
                      )}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}

          {vista === 'usuarios' && empresaActiva && (
            <div className="flex-1 overflow-y-auto divide-y divide-gray-50">
              {empresaActiva.usuarios.map((u) => (
                <button key={u.idUsuarioCliente} onClick={() => abrirHilo(u)}
                  className="w-full text-left px-4 py-2.5 flex items-center gap-2.5 hover:bg-gray-50 transition-colors">
                  <Avatar texto={u.nombreUsuarioCliente} />
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-medium text-gray-900 truncate">{u.nombreUsuarioCliente}</p>
                    <p className="text-[10px] text-gray-400">{ETIQUETA_ROL[u.rolUsuarioCliente] ?? u.rolUsuarioCliente}</p>
                  </div>
                  {u.noLeidos > 0 && (
                    <span className="text-[9px] bg-primary-600 text-white rounded-full px-1.5 py-0.5 font-semibold flex-shrink-0">{u.noLeidos}</span>
                  )}
                </button>
              ))}
            </div>
          )}

          {vista === 'hilo' && (
            <>
              <div className="flex-1 overflow-y-auto px-3 py-3 space-y-2">
                {mensajes === null ? (
                  <div className="flex justify-center py-8">
                    <div className="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent" />
                  </div>
                ) : mensajes.length === 0 ? (
                  <p className="text-xs text-gray-400 text-center py-6 px-2">Todavía no hay mensajes en esta conversación.</p>
                ) : mensajes.map((m) => {
                  const esMio = m.origen === 'GESTOR'
                  return (
                    <div key={m.id} className={`flex ${esMio ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[80%] rounded-2xl px-3 py-2 text-xs ${
                        esMio ? 'bg-primary-600 text-white rounded-br-sm' : 'bg-gray-100 text-gray-800 rounded-bl-sm'
                      }`}>
                        <p className="whitespace-pre-wrap break-words">{m.mensaje}</p>
                        <p className={`text-[9px] mt-0.5 text-right ${esMio ? 'text-primary-100' : 'text-gray-400'}`}>{formatHora(m.fechaEnvio)}</p>
                      </div>
                    </div>
                  )
                })}
                <div ref={finRef} />
              </div>
              <form onSubmit={enviar} className="border-t border-gray-100 px-3 py-2.5 flex items-center gap-2 flex-shrink-0">
                <input value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Escribe un mensaje..." maxLength={2000}
                  className="flex-1 border border-gray-200 rounded-full px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-primary-500" />
                <button type="submit" disabled={enviando || !texto.trim()}
                  className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white p-1.5 rounded-full transition-colors flex-shrink-0">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                  </svg>
                </button>
              </form>
            </>
          )}
        </div>
      )}
    </div>
  )
}

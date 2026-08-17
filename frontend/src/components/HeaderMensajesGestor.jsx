import { useState, useEffect, useRef, useCallback } from 'react'
import mensajeService from '../services/mensajeService'

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

export default function HeaderMensajesGestor() {
  const [abierto, setAbierto]             = useState(false)
  const [vista, setVista]                 = useState('lista') // 'lista' | 'hilo'
  const [conversaciones, setConversaciones] = useState([])
  const [idClienteSel, setIdClienteSel]   = useState(null)
  const [mensajes, setMensajes]           = useState(null)
  const [texto, setTexto]                 = useState('')
  const [enviando, setEnviando]           = useState(false)
  const [totalNoLeidos, setTotalNoLeidos] = useState(0)
  const ref = useRef(null)
  const finRef = useRef(null)

  const cargarConversaciones = useCallback(() => {
    mensajeService.misConversaciones().then(data => {
      setConversaciones(data)
      setTotalNoLeidos(data.reduce((acc, c) => acc + c.noLeidos, 0))
    }).catch(() => {})
  }, [])

  const cargarHilo = useCallback((idCliente) => {
    mensajeService.listarGestor(idCliente).then(setMensajes).catch(() => {})
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

  useEffect(() => {
    if (vista !== 'hilo' || !idClienteSel) return
    cargarHilo(idClienteSel)
    const t = setInterval(() => cargarHilo(idClienteSel), 6000)
    return () => clearInterval(t)
  }, [vista, idClienteSel, cargarHilo])

  useEffect(() => {
    if (vista === 'hilo') finRef.current?.scrollIntoView({ block: 'end' })
  }, [mensajes, vista])

  const abrirConversacion = (idCliente) => {
    setIdClienteSel(idCliente)
    setMensajes(null)
    setVista('hilo')
  }

  const volver = () => {
    setVista('lista')
    cargarConversaciones()
  }

  const enviar = async (e) => {
    e.preventDefault()
    if (!texto.trim() || !idClienteSel) return
    setEnviando(true)
    try {
      await mensajeService.enviarGestor(idClienteSel, texto.trim())
      setTexto('')
      cargarHilo(idClienteSel)
    } catch { /* silencioso */ }
    finally { setEnviando(false) }
  }

  const clienteSel = conversaciones.find(c => c.idCliente === idClienteSel)

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setAbierto(v => !v)} title="Mensajes con clientes"
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
        <div className="absolute right-0 top-11 w-80 bg-white rounded-xl shadow-xl border border-gray-200 z-50 flex flex-col" style={{ height: 440 }}>
          {vista === 'lista' ? (
            <>
              <div className="px-4 py-3 border-b border-gray-100 bg-primary-50 flex-shrink-0">
                <p className="text-xs font-semibold text-primary-700 uppercase tracking-wider">Mensajes con clientes</p>
              </div>
              <div className="flex-1 overflow-y-auto divide-y divide-gray-50">
                {conversaciones.length === 0 ? (
                  <p className="text-xs text-gray-400 text-center py-8 px-3">No tienes clientes asignados todavía.</p>
                ) : conversaciones.map(c => (
                  <button key={c.idCliente} onClick={() => abrirConversacion(c.idCliente)}
                    className="w-full text-left px-4 py-2.5 flex items-start gap-2.5 hover:bg-gray-50 transition-colors">
                    <div className="w-8 h-8 rounded-full bg-slate-500 text-white text-xs font-semibold flex items-center justify-center flex-shrink-0">
                      {c.nombreCliente?.trim()[0]?.toUpperCase() ?? '?'}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-xs font-medium text-gray-900 truncate">{c.nombreCliente}</p>
                        {c.fechaUltimoMensaje && <span className="text-[9px] text-gray-400 flex-shrink-0">{formatRelativo(c.fechaUltimoMensaje)}</span>}
                      </div>
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-[11px] text-gray-400 truncate">{c.ultimoMensaje ?? 'Sin mensajes'}</p>
                        {c.noLeidos > 0 && (
                          <span className="text-[9px] bg-primary-600 text-white rounded-full px-1.5 py-0.5 font-semibold flex-shrink-0">{c.noLeidos}</span>
                        )}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </>
          ) : (
            <>
              <div className="px-3 py-2.5 border-b border-gray-100 bg-primary-50 flex items-center gap-2 flex-shrink-0">
                <button onClick={volver} className="p-1 text-primary-700 hover:bg-primary-100 rounded-lg transition-colors">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
                  </svg>
                </button>
                <p className="text-xs font-semibold text-primary-700 truncate">{clienteSel?.nombreCliente}</p>
              </div>

              <div className="flex-1 overflow-y-auto px-3 py-3 space-y-2">
                {mensajes === null ? (
                  <div className="flex justify-center py-8">
                    <div className="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent" />
                  </div>
                ) : mensajes.length === 0 ? (
                  <p className="text-xs text-gray-400 text-center py-6 px-2">Aún no hay mensajes con este cliente.</p>
                ) : mensajes.map(m => {
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
                <input value={texto} onChange={e => setTexto(e.target.value)}
                  placeholder="Escribe un mensaje..."
                  maxLength={2000}
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

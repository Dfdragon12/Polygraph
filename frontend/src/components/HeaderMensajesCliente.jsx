import { useState, useEffect, useRef, useCallback } from 'react'
import mensajeService from '../services/mensajeService'
import dashboardService from '../services/dashboardService'

function formatHora(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

export default function HeaderMensajesCliente() {
  const [abierto, setAbierto]   = useState(false)
  const [mensajes, setMensajes] = useState(null)
  const [gestor, setGestor]     = useState(null)
  const [noLeidos, setNoLeidos] = useState(0)
  const [texto, setTexto]       = useState('')
  const [enviando, setEnviando] = useState(false)
  const ref = useRef(null)
  const finRef = useRef(null)

  const cargarBadge = useCallback(() => {
    mensajeService.noLeidosCliente().then(r => setNoLeidos(r.total)).catch(() => {})
  }, [])

  const cargarHilo = useCallback(() => {
    mensajeService.listarCliente().then(data => { setMensajes(data); setNoLeidos(0) }).catch(() => {})
  }, [])

  useEffect(() => {
    cargarBadge()
    const t = setInterval(cargarBadge, 30000)
    return () => clearInterval(t)
  }, [cargarBadge])

  useEffect(() => {
    if (!abierto) return
    cargarHilo()
    dashboardService.obtenerGestor().then(setGestor).catch(() => setGestor(null))
    const t = setInterval(cargarHilo, 6000)
    return () => clearInterval(t)
  }, [abierto, cargarHilo])

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  useEffect(() => {
    if (abierto) finRef.current?.scrollIntoView({ block: 'end' })
  }, [mensajes, abierto])

  const tieneGestor = !!gestor?.nombreCompleto

  const enviar = async (e) => {
    e.preventDefault()
    if (!texto.trim()) return
    setEnviando(true)
    try {
      await mensajeService.enviarCliente(texto.trim())
      setTexto('')
      cargarHilo()
    } catch { /* silencioso */ }
    finally { setEnviando(false) }
  }

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setAbierto(v => !v)} title="Mensajes con tu gestor"
        className="relative p-2 text-gray-500 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors">
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8-1.284 0-2.503-.24-3.61-.673L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
        </svg>
        {noLeidos > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold leading-none">
            {noLeidos > 9 ? '9+' : noLeidos}
          </span>
        )}
      </button>

      {abierto && (
        <div className="absolute right-0 top-11 w-80 bg-white rounded-xl shadow-xl border border-gray-200 z-50 flex flex-col" style={{ height: 460 }}>
          <div className="px-4 py-3 border-b border-gray-100 bg-primary-50 flex-shrink-0">
            <p className="text-xs font-semibold text-primary-700 uppercase tracking-wider">Tu gestor en Polygraph</p>
          </div>

          {tieneGestor && (
            <div className="px-4 py-3 border-b border-gray-100 flex items-center gap-3 flex-shrink-0">
              <div className="w-9 h-9 rounded-full bg-primary-600 text-white text-sm font-bold flex items-center justify-center flex-shrink-0">
                {gestor.nombreCompleto.trim()[0]?.toUpperCase() ?? '?'}
              </div>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-gray-900 truncate">{gestor.nombreCompleto}</p>
                {gestor.telefono ? (
                  <a href={`tel:${gestor.telefono}`} className="text-xs text-primary-600 hover:underline">{gestor.telefono}</a>
                ) : (
                  <p className="text-xs text-gray-400">Teléfono no registrado</p>
                )}
              </div>
            </div>
          )}

          <div className="flex-1 overflow-y-auto px-3 py-3 space-y-2">
            {mensajes === null ? (
              <div className="flex justify-center py-8">
                <div className="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent" />
              </div>
            ) : !tieneGestor ? (
              <p className="text-xs text-gray-400 text-center py-6 px-2">Aún no tienes un gestor asignado. Contacta a Polygraph para más información.</p>
            ) : mensajes.length === 0 ? (
              <p className="text-xs text-gray-400 text-center py-6 px-2">Aún no hay mensajes. Escríbele a tu gestor para empezar.</p>
            ) : mensajes.map(m => {
              const esMio = m.origen === 'CLIENTE'
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
              placeholder={tieneGestor ? 'Escribe un mensaje...' : 'Sin gestor asignado'}
              disabled={!tieneGestor}
              maxLength={2000}
              className="flex-1 border border-gray-200 rounded-full px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-gray-50" />
            <button type="submit" disabled={enviando || !texto.trim() || !tieneGestor}
              className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white p-1.5 rounded-full transition-colors flex-shrink-0">
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
              </svg>
            </button>
          </form>
        </div>
      )}
    </div>
  )
}

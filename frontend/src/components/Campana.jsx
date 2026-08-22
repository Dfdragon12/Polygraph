import { useState, useEffect, useRef, useCallback } from 'react'
import api from '../services/api'

const TIPO_CONFIG = {
  CATALOGO:          { color: 'bg-primary-100 text-primary-700',   label: 'Catálogo'   },
  USUARIO:           { color: 'bg-emerald-100 text-emerald-700', label: 'Usuario'    },
  CLIENTE:           { color: 'bg-amber-100 text-amber-700',     label: 'Cliente'    },
  NUEVA_SOLICITUD:   { color: 'bg-orange-100 text-orange-700',   label: 'Solicitud'  },
  SOLICITUD_CREADA:  { color: 'bg-blue-100 text-blue-700',       label: 'Solicitud'  },
  SISTEMA:           { color: 'bg-slate-100 text-slate-600',     label: 'Sistema'    },
  SESION:            { color: 'bg-sky-100 text-sky-700',          label: 'Sesión'     },
  ENLACES:           { color: 'bg-teal-100 text-teal-700',        label: 'Referencias'},
  SOLICITUD_REVERSION:{ color: 'bg-purple-100 text-purple-700',   label: 'Reversión'  },
}

function formatRelativo(fechaIso) {
  if (!fechaIso) return ''
  const diff = Date.now() - new Date(fechaIso).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1)  return 'Ahora'
  if (min < 60) return `Hace ${min} min`
  const h = Math.floor(min / 60)
  if (h < 24)   return `Hace ${h} h`
  return `Hace ${Math.floor(h / 24)} día(s)`
}

export default function Campana() {
  const [notifs, setNotifs]     = useState([])
  const [noLeidas, setNoLeidas] = useState(0)
  const [abierto, setAbierto]   = useState(false)
  const ref = useRef(null)

  const cargar = useCallback(async () => {
    try {
      const [rN, rC] = await Promise.all([
        api.get('/notificaciones'),
        api.get('/notificaciones/no-leidas'),
      ])
      setNotifs(rN.data)
      setNoLeidas(rC.data.total)
    } catch { /* silencioso */ }
  }, [])

  useEffect(() => {
    cargar()
    const t = setInterval(cargar, 30000)
    return () => clearInterval(t)
  }, [cargar])

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const marcarLeidas = async () => {
    try {
      await api.patch('/notificaciones/leer-todas')
      setNoLeidas(0)
      setNotifs(p => p.map(n => ({ ...n, leida: true })))
    } catch { /* silencioso */ }
  }

  const marcarUnaLeida = async (id) => {
    try {
      await api.patch(`/notificaciones/${id}/leer`)
      setNotifs(p => p.map(n => n.id === id ? { ...n, leida: true } : n))
      setNoLeidas(c => Math.max(0, c - 1))
    } catch { /* silencioso */ }
  }

  const eliminarNotif = async (id) => {
    const era = notifs.find(n => n.id === id)
    try {
      await api.delete(`/notificaciones/${id}`)
      setNotifs(p => p.filter(n => n.id !== id))
      if (era && !era.leida) setNoLeidas(c => Math.max(0, c - 1))
    } catch { /* silencioso */ }
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => { setAbierto(v => !v); if (!abierto) cargar() }}
        className="relative p-2 text-gray-500 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors">
        <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
        {noLeidas > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold leading-none">
            {noLeidas > 9 ? '9+' : noLeidas}
          </span>
        )}
      </button>

      {abierto && (
        <div className="absolute right-0 top-10 w-80 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
            <span className="text-sm font-semibold text-gray-900">Notificaciones</span>
            {noLeidas > 0 && (
              <button onClick={marcarLeidas} className="text-xs text-primary-600 hover:text-primary-800">
                Marcar todas leídas
              </button>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto divide-y divide-gray-50">
            {notifs.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-8">Sin notificaciones</p>
            ) : notifs.map(n => {
              const cfg = TIPO_CONFIG[n.tipo] ?? TIPO_CONFIG.SISTEMA
              return (
                <div key={n.id} className={`px-4 py-3 group ${n.leida ? '' : 'bg-primary-50/40'}`}>
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-start gap-2 min-w-0">
                      <span className={`mt-0.5 text-xs px-1.5 py-0.5 rounded font-medium flex-shrink-0 ${cfg.color}`}>
                        {cfg.label}
                      </span>
                      {!n.leida && <span className="w-2 h-2 bg-primary-500 rounded-full flex-shrink-0 mt-1" />}
                    </div>
                    <div className="flex items-center gap-1 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                      {!n.leida && (
                        <button onClick={() => marcarUnaLeida(n.id)} title="Marcar como leída"
                          className="p-1 text-gray-400 hover:text-primary-600 rounded transition-colors">
                          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                          </svg>
                        </button>
                      )}
                      <button onClick={() => eliminarNotif(n.id)} title="Eliminar"
                        className="p-1 text-gray-400 hover:text-red-500 rounded transition-colors">
                        <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>
                    </div>
                  </div>
                  <p className="text-sm font-medium text-gray-900 mt-1">{n.titulo}</p>
                  <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{n.mensaje}</p>
                  <div className="flex items-center justify-between mt-1 gap-2">
                    <p className="text-xs text-gray-400">{formatRelativo(n.fechaCreacion)}</p>
                    {n.realizadoPorNombre && (
                      <p className="text-xs text-gray-400 truncate">
                        Por: <span className="font-medium text-gray-600">{n.realizadoPorNombre}</span>
                      </p>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}

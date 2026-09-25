import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useAuth } from '../hooks/useAuth'

// A qué portal pertenece el formulario de un servicio, según el rol de quien está viendo la
// notificación — usado por FORMULARIO_ACTUALIZADO/SUBPROCESO_COMPLETADO (GESTOR/PROGRAMADOR),
// SUBPROCESO_DEVUELTO/PUBLICADO/ASIGNADO (ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR) y las
// novedades (todos), cada uno con su propia ruta.
const PREFIJO_FORMULARIO_POR_ROL = {
  ADMIN_POLYGRAPH: '/admin', GESTOR: '/gestor', PROGRAMADOR: '/programador',
  ANALISTA_INTERNO: '/analista', POLIGRAFISTA: '/poligrafista', VISITADOR: '/visitador',
}

// A qué portal pertenece la ficha de un cliente, según el rol de quien ve la notificación — hoy
// solo el gestor tiene una pantalla de detalle de cliente (ver CLIENTE_ASIGNADO más abajo).
const RUTA_CLIENTE_POR_ROL = { GESTOR: '/gestor/clientes' }

const rutaFormulario = (sufijo = '') => (n, usuario) => {
  const prefijo = PREFIJO_FORMULARIO_POR_ROL[usuario?.rol]
  return n.referenciaId && prefijo ? `${prefijo}/formulario/${n.referenciaId}${sufijo}` : null
}

// Adónde llevar al usuario al hacer clic en una notificación, según su tipo. Solo se resuelven
// los tipos con una pantalla clara a la que aterrizar — el resto solo se marca como leída.
const RUTA_POR_TIPO = {
  PROGRAMAR_ASIGNACION: () => '/programador/agenda',
  NUEVA_SOLICITUD:      (n) => n.referenciaId ? `/gestor/solicitudes?id=${n.referenciaId}` : '/gestor/solicitudes',
  FORMULARIO_ACTUALIZADO: rutaFormulario(),
  SUBPROCESO_DEVUELTO:    rutaFormulario(),
  SUBPROCESO_PUBLICADO:   rutaFormulario(),
  SUBPROCESO_ASIGNADO:    rutaFormulario(),
  // Quien diligenció lo marcó como completado — el revisor aterriza en el formulario para
  // aprobarlo o devolverlo.
  SUBPROCESO_COMPLETADO:  rutaFormulario(),
  // Le corrieron la hora (p.ej. al resolver una novedad) — al formulario de ese subproceso.
  SUBPROCESO_REPROGRAMADO: rutaFormulario(),
  // Novedades — directo al paso "Novedades" del formulario (ver Formulario.jsx, ?paso=novedades).
  NOVEDAD_REGISTRADA:     rutaFormulario('?paso=novedades'),
  NOVEDAD_RESUELTA:       rutaFormulario('?paso=novedades'),
  // Cambio de estado de una solicitud — solo lo recibe el cliente, lleva al detalle de su solicitud.
  SOLICITUD_ESTADO: (n) => n.referenciaId ? `/cliente/solicitudes/${n.referenciaId}` : null,
  // Le aprobaron el cupo pospago — lleva a "Mi Crédito", no apunta a un registro puntual.
  POSPAGO_APROBADO: () => '/cliente/pospago',
  // Al gestor le asignaron un cliente nuevo — lleva a la ficha de ese cliente.
  CLIENTE_ASIGNADO: (n, usuario) => {
    const base = RUTA_CLIENTE_POR_ROL[usuario?.rol]
    return n.referenciaId && base ? `${base}/${n.referenciaId}` : null
  },
}

const TIPO_CONFIG = {
  CATALOGO:          { color: 'bg-primary-100 text-primary-700',   label: 'Catálogo'   },
  USUARIO:           { color: 'bg-emerald-100 text-emerald-700', label: 'Usuario'    },
  CLIENTE:           { color: 'bg-amber-100 text-amber-700',     label: 'Cliente'    },
  CLIENTE_ASIGNADO:  { color: 'bg-amber-100 text-amber-700',     label: 'Cliente'    },
  NUEVA_SOLICITUD:   { color: 'bg-orange-100 text-orange-700',   label: 'Solicitud'  },
  SOLICITUD_CREADA:  { color: 'bg-blue-100 text-blue-700',       label: 'Solicitud'  },
  SOLICITUD_ESTADO:  { color: 'bg-blue-100 text-blue-700',       label: 'Solicitud'  },
  PROGRAMAR_ASIGNACION: { color: 'bg-indigo-100 text-indigo-700', label: 'Programar' },
  FORMULARIO_ACTUALIZADO: { color: 'bg-cyan-100 text-cyan-700',   label: 'Formulario' },
  SISTEMA:           { color: 'bg-slate-100 text-slate-600',     label: 'Sistema'    },
  SESION:            { color: 'bg-sky-100 text-sky-700',          label: 'Sesión'     },
  ENLACES:           { color: 'bg-teal-100 text-teal-700',        label: 'Referencias'},
  SOLICITUD_REVERSION:{ color: 'bg-purple-100 text-purple-700',   label: 'Reversión'  },
  SUBPROCESO_ASIGNADO:{ color: 'bg-blue-100 text-blue-700',       label: 'Asignado'   },
  SUBPROCESO_COMPLETADO:{ color: 'bg-green-100 text-green-700',   label: 'Por revisar'},
  SUBPROCESO_DEVUELTO:{ color: 'bg-amber-100 text-amber-700',     label: 'Devuelto'   },
  SUBPROCESO_REPROGRAMADO:{ color: 'bg-sky-100 text-sky-700',      label: 'Reprogramado'},
  SUBPROCESO_PUBLICADO:{ color: 'bg-emerald-100 text-emerald-700', label: 'Publicado'  },
  SUBPROCESO_DESASIGNADO:{ color: 'bg-gray-100 text-gray-600',    label: 'Desasignado'},
  SUBPROCESO_CANCELADO:{ color: 'bg-red-100 text-red-600',        label: 'Cancelado'  },
  NOVEDAD_REGISTRADA:{ color: 'bg-amber-100 text-amber-800',      label: 'Novedad'    },
  NOVEDAD_RESUELTA:  { color: 'bg-green-100 text-green-700',      label: 'Novedad'    },
  SOPORTE:           { color: 'bg-violet-100 text-violet-700',    label: 'Documento'  },
  FACTURACION:       { color: 'bg-rose-100 text-rose-700',        label: 'Facturación'},
  FACTURA_POSPAGO_GENERADA:{ color: 'bg-rose-100 text-rose-700',  label: 'Factura'    },
  NOTA_AJUSTE:       { color: 'bg-rose-100 text-rose-700',        label: 'Ajuste'     },
  PAGO_APROBADO:     { color: 'bg-emerald-100 text-emerald-700',  label: 'Pago'       },
  PAGO_RECHAZADO:    { color: 'bg-red-100 text-red-600',          label: 'Pago'       },
  POSPAGO_APROBADO:  { color: 'bg-emerald-100 text-emerald-700',  label: 'Crédito'    },
}

const TAMANO_PAGINA = 15

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

// Agrupa por día para que la lista no sea un solo bloque indistinto: Hoy / Ayer / Esta semana /
// Anteriores. Las notificaciones ya vienen ordenadas de la más nueva a la más vieja.
function grupoDe(fechaIso) {
  if (!fechaIso) return 'Anteriores'
  const inicioHoy = new Date(); inicioHoy.setHours(0, 0, 0, 0)
  const t = new Date(fechaIso).getTime()
  const dia = 24 * 60 * 60 * 1000
  if (t >= inicioHoy.getTime()) return 'Hoy'
  if (t >= inicioHoy.getTime() - dia) return 'Ayer'
  if (t >= inicioHoy.getTime() - 6 * dia) return 'Esta semana'
  return 'Anteriores'
}

export default function Campana() {
  const [notifs, setNotifs]       = useState([])
  const [noLeidas, setNoLeidas]   = useState(0)
  const [abierto, setAbierto]     = useState(false)
  const [filtro, setFiltro]       = useState('todas') // 'todas' | 'noLeidas'
  const [pagina, setPagina]       = useState(0)
  const [hayMas, setHayMas]       = useState(false)
  const [cargando, setCargando]   = useState(false)
  const ref = useRef(null)
  const noLeidasRef = useRef(0)
  const navigate = useNavigate()
  const { usuario } = useAuth()

  const cargarConteo = useCallback(async () => {
    try {
      const r = await api.get('/notificaciones/no-leidas')
      setNoLeidas(r.data.total)
      return r.data.total
    } catch { return null }
  }, [])

  const cargarPagina = useCallback(async (numPagina, filtroActual) => {
    setCargando(true)
    try {
      const r = await api.get('/notificaciones/pagina', {
        params: { pagina: numPagina, tamano: TAMANO_PAGINA, soloNoLeidas: filtroActual === 'noLeidas' },
      })
      setNotifs(prev => numPagina === 0 ? r.data.items : [...prev, ...r.data.items.filter(n => !prev.some(p => p.id === n.id))])
      setHayMas(r.data.hayMas)
      setPagina(numPagina)
    } catch { /* silencioso */ } finally {
      setCargando(false)
    }
  }, [])

  // Cada 30s solo se consulta el contador; la lista se recarga si llegó algo nuevo mientras el
  // panel está abierto (así "Cargar más" no se pierde por un refresco que no aportaba nada).
  useEffect(() => {
    cargarConteo()
    const t = setInterval(async () => {
      const total = await cargarConteo()
      if (total != null && total > noLeidasRef.current && abierto) cargarPagina(0, filtro)
    }, 30000)
    return () => clearInterval(t)
  }, [cargarConteo, cargarPagina, abierto, filtro])

  useEffect(() => { noLeidasRef.current = noLeidas }, [noLeidas])

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const alternar = () => {
    const abrir = !abierto
    setAbierto(abrir)
    if (abrir) { cargarPagina(0, filtro); cargarConteo() }
  }

  const cambiarFiltro = (nuevo) => {
    if (nuevo === filtro) return
    setFiltro(nuevo)
    cargarPagina(0, nuevo)
  }

  const marcarLeidas = async () => {
    try {
      await api.patch('/notificaciones/leer-todas')
      setNoLeidas(0)
      setNotifs(p => filtro === 'noLeidas' ? [] : p.map(n => ({ ...n, leida: true })))
      setHayMas(h => filtro === 'noLeidas' ? false : h)
    } catch { /* silencioso */ }
  }

  const limpiarLeidas = async () => {
    try {
      await api.delete('/notificaciones/leidas')
      cargarPagina(0, filtro)
    } catch { /* silencioso */ }
  }

  const marcarUnaLeida = async (id) => {
    try {
      await api.patch(`/notificaciones/${id}/leer`)
      setNotifs(p => filtro === 'noLeidas' ? p.filter(n => n.id !== id) : p.map(n => n.id === id ? { ...n, leida: true } : n))
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

  const irANotificacion = (n) => {
    const ruta = RUTA_POR_TIPO[n.tipo]?.(n, usuario)
    if (!ruta) return
    if (!n.leida) marcarUnaLeida(n.id)
    setAbierto(false)
    navigate(ruta)
  }

  const hayLeidas = notifs.some(n => n.leida)
  const grupos = []
  notifs.forEach(n => {
    const g = grupoDe(n.fechaCreacion)
    const ultimo = grupos[grupos.length - 1]
    if (ultimo && ultimo.nombre === g) ultimo.items.push(n)
    else grupos.push({ nombre: g, items: [n] })
  })

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={alternar}
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
        <div className="fixed left-3 right-3 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-10 sm:w-96 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden">
          <div className="px-4 pt-3 pb-2 border-b border-gray-100">
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold text-gray-900">
                Notificaciones {noLeidas > 0 && <span className="text-xs font-normal text-gray-400">· {noLeidas} sin leer</span>}
              </span>
              {noLeidas > 0 && (
                <button onClick={marcarLeidas} className="text-xs text-primary-600 hover:text-primary-800">
                  Marcar todas leídas
                </button>
              )}
            </div>
            <div className="flex items-center justify-between mt-2">
              <div className="inline-flex rounded-lg bg-gray-100 p-0.5 text-xs">
                {[['todas', 'Todas'], ['noLeidas', 'No leídas']].map(([valor, etiqueta]) => (
                  <button key={valor} onClick={() => cambiarFiltro(valor)}
                    className={`px-2.5 py-1 rounded-md font-medium transition-colors ${
                      filtro === valor ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>
                    {etiqueta}
                  </button>
                ))}
              </div>
              {filtro === 'todas' && hayLeidas && (
                <button onClick={limpiarLeidas} className="text-xs text-gray-400 hover:text-red-500">
                  Borrar leídas
                </button>
              )}
            </div>
          </div>

          <div className="max-h-[26rem] overflow-y-auto">
            {notifs.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-8">
                {cargando ? 'Cargando…' : filtro === 'noLeidas' ? 'Estás al día — no tienes notificaciones sin leer' : 'Sin notificaciones'}
              </p>
            ) : grupos.map(g => (
              <div key={g.nombre}>
                <p className="sticky top-0 z-10 bg-gray-50/95 backdrop-blur px-4 py-1 text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                  {g.nombre}
                </p>
                <div className="divide-y divide-gray-50">
                  {g.items.map(n => {
                    const cfg = TIPO_CONFIG[n.tipo] ?? TIPO_CONFIG.SISTEMA
                    const clicable = Boolean(RUTA_POR_TIPO[n.tipo]?.(n, usuario))
                    return (
                      <div key={n.id}
                        onClick={clicable ? () => irANotificacion(n) : undefined}
                        className={`px-4 py-3 group ${n.leida ? '' : 'bg-primary-50/40'} ${clicable ? 'cursor-pointer hover:bg-gray-50' : ''}`}>
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex items-start gap-2 min-w-0">
                            <span className={`mt-0.5 text-xs px-1.5 py-0.5 rounded font-medium flex-shrink-0 ${cfg.color}`}>
                              {cfg.label}
                            </span>
                            {!n.leida && <span className="w-2 h-2 bg-primary-500 rounded-full flex-shrink-0 mt-1" />}
                          </div>
                          <div className="flex items-center gap-1 flex-shrink-0 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
                            {!n.leida && (
                              <button onClick={(e) => { e.stopPropagation(); marcarUnaLeida(n.id) }} title="Marcar como leída"
                                className="p-1 text-gray-400 hover:text-primary-600 rounded transition-colors">
                                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                </svg>
                              </button>
                            )}
                            <button onClick={(e) => { e.stopPropagation(); eliminarNotif(n.id) }} title="Eliminar"
                              className="p-1 text-gray-400 hover:text-red-500 rounded transition-colors">
                              <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                              </svg>
                            </button>
                          </div>
                        </div>
                        <p className="text-sm font-medium text-gray-900 mt-1">{n.titulo}</p>
                        <p className="text-xs text-gray-500 mt-0.5 line-clamp-3">{n.mensaje}</p>
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
            ))}
            {hayMas && (
              <button onClick={() => cargarPagina(pagina + 1, filtro)} disabled={cargando}
                className="w-full py-2.5 text-xs font-medium text-primary-600 hover:bg-primary-50 disabled:opacity-60 border-t border-gray-100">
                {cargando ? 'Cargando…' : 'Cargar más'}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

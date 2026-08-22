import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useState, useEffect, useRef, Fragment } from 'react'
import { useAuth } from '../../hooks/useAuth'
import Campana from '../Campana'
import HeaderMensajesCliente from '../HeaderMensajesCliente'
import CarritoDropdown from '../CarritoDropdown'

const ETIQUETAS_ROL = {
  ADMIN_CLIENTE:    'Administrador',
  ANALISTA_CLIENTE: 'Analista',
}

/* ─── Ruta de navegación (breadcrumb) ─── */
const ETIQUETAS_RUTA = {
  dashboard:            'Dashboard',
  solicitudes:          'Mis Solicitudes',
  'nueva-solicitud':    'Nueva Solicitud',
  'carga-masiva':       'Carga Masiva',
  'comprar-servicios':  'Comprar Servicios',
  simulador:            'Simulador de Pago',
  resultado:            'Resultado del Pago',
  documentos:           'Información',
  estadisticas:         'Estadísticas',
  catalogo:             'Catálogo',
}

function Breadcrumb() {
  const { pathname } = useLocation()
  const segmentos = pathname.split('/').filter(Boolean).slice(1)

  const migas = [
    { label: 'Portal Cliente', ruta: '/cliente/dashboard' },
    ...segmentos.map((seg, i) => ({
      label: ETIQUETAS_RUTA[seg] ?? (/^\d+$/.test(seg) ? 'Detalle' : seg),
      ruta: '/cliente/' + segmentos.slice(0, i + 1).join('/'),
    })),
  ]

  return (
    <nav aria-label="Ruta de navegación" className="bg-white border-b border-gray-100 px-4 md:px-6 py-2 flex items-center gap-1 text-xs text-gray-400">
      {migas.map((miga, i) => (
        <Fragment key={miga.ruta + i}>
          {i > 0 && (
            <svg className="h-3 w-3 text-gray-300 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          )}
          {i < migas.length - 1 ? (
            <NavLink to={miga.ruta} className="hover:text-gray-700 transition-colors">
              {miga.label}
            </NavLink>
          ) : (
            <span className="text-gray-700 font-medium">{miga.label}</span>
          )}
        </Fragment>
      ))}
    </nav>
  )
}

/* ─── Secciones del menú, agrupadas igual que en el resto de roles ─── */
const SECCIONES_ADMIN = [
  {
    titulo: 'General',
    items: [
      { ruta: '/cliente/dashboard', etiqueta: 'Dashboard', icono: '📊' },
    ],
  },
  {
    titulo: 'Solicitudes',
    items: [
      { ruta: '/cliente/solicitudes',     etiqueta: 'Mis Solicitudes', icono: '📁' },
      { ruta: '/cliente/nueva-solicitud', etiqueta: 'Nueva Solicitud', icono: '➕' },
      { ruta: '/cliente/carga-masiva',    etiqueta: 'Carga Masiva',    icono: '📤' },
    ],
  },
  {
    titulo: 'Cuenta',
    items: [
      { ruta: '/cliente/comprar-servicios', etiqueta: 'Comprar Servicios',  icono: '🛒' },
      { ruta: '/cliente/documentos',        etiqueta: 'Información',        icono: '🗂️' },
      { ruta: '/cliente/estadisticas',      etiqueta: 'Estadísticas',       icono: '📈' },
    ],
  },
]

const SECCIONES_ANALISTA = [
  {
    titulo: 'General',
    items: [
      { ruta: '/cliente/dashboard', etiqueta: 'Dashboard', icono: '📊' },
    ],
  },
  {
    titulo: 'Solicitudes',
    items: [
      { ruta: '/cliente/solicitudes',     etiqueta: 'Mis Solicitudes', icono: '📁' },
      { ruta: '/cliente/nueva-solicitud', etiqueta: 'Nueva Solicitud', icono: '➕' },
    ],
  },
]

/* ─── Contenido del sidebar (agrupado por secciones) ─── */
function SidebarContent({ usuario, secciones, onNavClick, collapsed }) {
  return (
    <>
      <div className={`border-b border-slate-700 ${collapsed ? 'px-2 py-4 flex justify-center' : 'px-6 py-5'}`}>
        {collapsed ? (
          <div className="w-8 h-8 rounded-full bg-primary-600 text-white text-xs font-bold flex items-center justify-center">
            {(usuario?.nombre?.[0] ?? '?').toUpperCase()}
          </div>
        ) : (
          <>
            <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">Polygraph Service</p>
            <p className="text-sm font-semibold text-white truncate">{usuario?.nombre ?? 'Cliente'}</p>
            <span className="text-xs text-slate-400">{ETIQUETAS_ROL[usuario?.rol] ?? usuario?.rol}</span>
          </>
        )}
      </div>

      <nav className={`flex-1 py-4 overflow-y-auto space-y-5 ${collapsed ? 'px-1' : 'px-3'}`}>
        {secciones.map(seccion => (
          <div key={seccion.titulo}>
            {!collapsed && (
              <p className="px-3 mb-1.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                {seccion.titulo}
              </p>
            )}
            <div className="space-y-0.5">
              {seccion.items.map(({ ruta, etiqueta, icono }) => (
                <NavLink key={ruta} to={ruta} onClick={onNavClick}
                  title={collapsed ? etiqueta : undefined}
                  className={({ isActive }) =>
                    `flex items-center rounded-lg text-sm transition-colors ${
                      collapsed ? 'justify-center p-2.5' : 'gap-3 px-3 py-2.5'
                    } ${isActive ? 'bg-primary-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`
                  }>
                  <span className="text-base flex-shrink-0">{icono}</span>
                  {!collapsed && etiqueta}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>
    </>
  )
}

/* ─── Buscador global ─── */
function BuscadorGlobal({ secciones }) {
  const [abierto, setAbierto] = useState(false)
  const [query, setQuery]     = useState('')
  const inputRef = useRef(null)
  const navigate = useNavigate()

  const items = secciones.flatMap(s => s.items)
  const resultados = query.trim()
    ? items.filter(i => i.etiqueta.toLowerCase().includes(query.toLowerCase()))
    : items

  useEffect(() => {
    if (abierto) setTimeout(() => inputRef.current?.focus(), 50)
    else setQuery('')
  }, [abierto])

  useEffect(() => {
    const h = (e) => { if (e.key === 'Escape') setAbierto(false) }
    document.addEventListener('keydown', h)
    return () => document.removeEventListener('keydown', h)
  }, [])

  const ir = (ruta) => { navigate(ruta); setAbierto(false) }

  return (
    <>
      <button onClick={() => setAbierto(true)} title="Buscar"
        className="p-2 text-gray-500 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors">
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
        </svg>
      </button>

      {abierto && (
        <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 bg-black/40 backdrop-blur-sm"
          onClick={() => setAbierto(false)}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden"
            onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-100">
              <svg className="h-5 w-5 text-gray-400 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
              </svg>
              <input ref={inputRef} value={query} onChange={e => setQuery(e.target.value)}
                placeholder="Buscar sección..."
                className="flex-1 text-sm text-gray-900 placeholder-gray-400 focus:outline-none" />
              <kbd className="hidden sm:inline text-xs text-gray-400 border border-gray-200 rounded px-1.5 py-0.5">Esc</kbd>
            </div>
            <div className="max-h-64 overflow-y-auto py-2">
              {resultados.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-6">Sin resultados</p>
              ) : resultados.map(item => (
                <button key={item.ruta} onClick={() => ir(item.ruta)}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-left hover:bg-primary-50 transition-colors group">
                  <span className="text-lg w-6 text-center flex-shrink-0">{item.icono}</span>
                  <span className="text-sm text-gray-700 group-hover:text-primary-700 font-medium">{item.etiqueta}</span>
                </button>
              ))}
            </div>
            <div className="px-4 py-2 border-t border-gray-50 bg-gray-50">
              <p className="text-xs text-gray-400">Cierra con Esc</p>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

/* ─── Menú configuración ─── */
function ConfiguracionMenu() {
  const [abierto, setAbierto] = useState(false)
  const ref = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setAbierto(v => !v)} title="Configuración"
        className="p-2 text-gray-500 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors">
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round"
            d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </button>

      {abierto && (
        <div className="absolute right-0 top-10 w-52 bg-white rounded-xl shadow-xl border border-gray-200 z-50 py-1">
          <button onClick={() => { navigate('/cambiar-password'); setAbierto(false) }}
            className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors text-left">
            <span>🔑</span> Cambiar contraseña
          </button>
        </div>
      )}
    </div>
  )
}

/* ─── Avatar de usuario ─── */
function AvatarUsuario({ usuario, cerrarSesion }) {
  const [abierto, setAbierto] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const iniciales = (usuario?.nombre?.[0] ?? '?').toUpperCase()

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setAbierto(v => !v)}
        className="flex items-center gap-2 p-1 rounded-lg hover:bg-gray-100 transition-colors">
        <div className="w-8 h-8 rounded-full bg-primary-600 text-white text-xs font-bold flex items-center justify-center flex-shrink-0">
          {iniciales}
        </div>
        <div className="hidden md:block text-left">
          <p className="text-xs font-semibold text-gray-800 leading-tight max-w-[140px] truncate">
            {usuario?.nombre ?? 'Cliente'}
          </p>
          <p className="text-xs text-gray-400 leading-tight">{ETIQUETAS_ROL[usuario?.rol] ?? usuario?.rol}</p>
        </div>
        <svg className="hidden md:block h-3.5 w-3.5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {abierto && (
        <div className="absolute right-0 top-11 w-56 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-100">
            <p className="text-sm font-semibold text-gray-900 truncate">{usuario?.nombre ?? 'Cliente'}</p>
            <p className="text-xs text-gray-400 truncate mt-0.5">{usuario?.email}</p>
            <span className="inline-block mt-1.5 text-xs bg-primary-100 text-primary-700 px-2 py-0.5 rounded-full font-medium">
              {ETIQUETAS_ROL[usuario?.rol] ?? usuario?.rol}
            </span>
          </div>
          <div className="py-1">
            <button onClick={() => { cerrarSesion(); setAbierto(false) }}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors text-left">
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
              Cerrar sesión
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default function ClientLayout() {
  const { usuario, logout } = useAuth()
  const navigate = useNavigate()
  const esPospago = usuario?.tipoCliente === 'POSPAGO'
  const seccionesBase = usuario?.rol === 'ADMIN_CLIENTE' ? SECCIONES_ADMIN : SECCIONES_ANALISTA
  // Pospago solicita directo con cupo de crédito — no compra saldo por adelantado.
  const secciones = esPospago
    ? seccionesBase.map(s => ({ ...s, items: s.items.filter(i => i.ruta !== '/cliente/comprar-servicios') }))
    : seccionesBase
  const [menuMovil, setMenuMovil] = useState(false)
  const [collapsed, setCollapsed] = useState(() =>
    localStorage.getItem('client-sidebar-collapsed') === 'true'
  )

  const cerrarSesion = () => { logout(); navigate('/login') }

  const toggleSidebar = () => {
    const next = !collapsed
    setCollapsed(next)
    localStorage.setItem('client-sidebar-collapsed', String(next))
  }

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar desktop */}
      <aside className={`hidden md:flex md:flex-col bg-slate-900 text-white flex-shrink-0 transition-all duration-300 ${collapsed ? 'w-16' : 'w-64'}`}>
        <SidebarContent usuario={usuario} secciones={secciones} collapsed={collapsed} />
      </aside>

      {/* Sidebar móvil */}
      {menuMovil && (
        <>
          <div className="fixed inset-0 bg-black/50 z-40 md:hidden" onClick={() => setMenuMovil(false)} />
          <aside className="fixed inset-y-0 left-0 z-50 flex flex-col w-72 bg-slate-900 text-white md:hidden shadow-2xl">
            <button onClick={() => setMenuMovil(false)}
              className="absolute top-4 right-4 p-1 text-slate-400 hover:text-white">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
            <SidebarContent usuario={usuario} secciones={secciones} onNavClick={() => setMenuMovil(false)} collapsed={false} />
          </aside>
        </>
      )}

      {/* Área principal */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        <header className="bg-white border-b border-gray-200 px-4 md:px-6 py-3 flex items-center justify-between flex-shrink-0">
          <div className="flex items-center gap-1">
            <button onClick={() => setMenuMovil(true)}
              className="md:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>
            <button onClick={toggleSidebar}
              title={collapsed ? 'Expandir menú' : 'Colapsar menú'}
              className="hidden md:flex p-2 rounded-lg text-gray-500 hover:text-gray-800 hover:bg-gray-100 transition-colors">
              <svg className={`h-5 w-5 transition-transform duration-300 ${collapsed ? 'rotate-180' : ''}`}
                fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M11 19l-7-7 7-7M18 19l-7-7 7-7" />
              </svg>
            </button>
          </div>

          <div className="flex items-center gap-1">
            <BuscadorGlobal secciones={secciones} />
            {usuario?.rol === 'ADMIN_CLIENTE' && !esPospago && <CarritoDropdown />}
            <ConfiguracionMenu />
            <HeaderMensajesCliente />
            <Campana />
            <div className="w-px h-6 bg-gray-200 mx-1" />
            <AvatarUsuario usuario={usuario} cerrarSesion={cerrarSesion} />
          </div>
        </header>

        <Breadcrumb />

        <main className="flex-1 overflow-y-auto overflow-x-hidden p-4 md:p-6 scrollbar-thin">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

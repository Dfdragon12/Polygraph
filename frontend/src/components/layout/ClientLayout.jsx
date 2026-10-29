import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useState, useEffect, useRef, Fragment } from 'react'
import { useAuth } from '../../hooks/useAuth'
import Campana from '../Campana'
import HeaderMensajesCliente from '../HeaderMensajesCliente'
import CarritoDropdown from '../CarritoDropdown'
import BuscadorGlobal from '../BuscadorGlobal'
import SidebarSeccion from './SidebarSeccion'
import { IconoDashboard, IconoCarpeta, IconoMas, IconoSubir, IconoCarrito, IconoEquipo, IconoInfo, IconoEstadisticas, IconoCuenta } from './IconosNav'

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
  pospago:              'Mi Crédito',
  facturas:             'Factura',
  equipo:               'Mi Equipo',
  perfil:               'Mi perfil',
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
      { ruta: '/cliente/dashboard', etiqueta: 'Dashboard', icono: <IconoDashboard /> },
    ],
  },
  {
    titulo: 'Solicitudes',
    items: [
      { ruta: '/cliente/solicitudes',     etiqueta: 'Mis Solicitudes', icono: <IconoCarpeta /> },
      { ruta: '/cliente/nueva-solicitud', etiqueta: 'Nueva Solicitud', icono: <IconoMas /> },
      { ruta: '/cliente/carga-masiva',    etiqueta: 'Carga Masiva',    icono: <IconoSubir /> },
    ],
  },
  {
    titulo: 'Cuenta',
    items: [
      { ruta: '/cliente/comprar-servicios', etiqueta: 'Comprar Servicios',  icono: <IconoCarrito /> },
      { ruta: '/cliente/equipo',            etiqueta: 'Mi Equipo',          icono: <IconoEquipo /> },
      { ruta: '/cliente/documentos',        etiqueta: 'Información',        icono: <IconoInfo /> },
      { ruta: '/cliente/estadisticas',      etiqueta: 'Estadísticas',       icono: <IconoEstadisticas /> },
    ],
  },
]

const SECCIONES_ANALISTA = [
  {
    titulo: 'General',
    items: [
      { ruta: '/cliente/dashboard', etiqueta: 'Dashboard', icono: <IconoDashboard /> },
    ],
  },
  {
    titulo: 'Solicitudes',
    items: [
      { ruta: '/cliente/solicitudes',     etiqueta: 'Mis Solicitudes', icono: <IconoCarpeta /> },
      { ruta: '/cliente/nueva-solicitud', etiqueta: 'Nueva Solicitud', icono: <IconoMas /> },
    ],
  },
  {
    titulo: 'Mi Perfil',
    items: [
      { ruta: '/cliente/perfil', etiqueta: 'Mis datos', icono: <IconoCuenta /> },
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

      <nav className={`flex-1 py-4 overflow-y-auto scrollbar-thin space-y-5 ${collapsed ? 'px-1' : 'px-3'}`}>
        {secciones.map(seccion => (
          <SidebarSeccion key={seccion.titulo} titulo={seccion.titulo} items={seccion.items}
            collapsed={collapsed} onNavClick={onNavClick} />
        ))}
      </nav>
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
        <div className="fixed left-3 right-3 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-10 sm:w-52 bg-white rounded-xl shadow-xl border border-gray-200 z-50 py-1">
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
function AvatarUsuario({ usuario, cerrarSesion, rutaPerfil }) {
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
        <div className="fixed left-3 right-3 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-11 sm:w-56 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-100">
            <p className="text-sm font-semibold text-gray-900 truncate">{usuario?.nombre ?? 'Cliente'}</p>
            <p className="text-xs text-gray-400 truncate mt-0.5">{usuario?.email}</p>
            <span className="inline-block mt-1.5 text-xs bg-primary-100 text-primary-700 px-2 py-0.5 rounded-full font-medium">
              {ETIQUETAS_ROL[usuario?.rol] ?? usuario?.rol}
            </span>
          </div>
          <div className="py-1">
            {rutaPerfil && (
              <NavLink to={rutaPerfil} onClick={() => setAbierto(false)}
                className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
                </svg>
                Mi perfil
              </NavLink>
            )}
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
  const { usuario, logout, refrescarUsuario } = useAuth()
  const navigate = useNavigate()

  // El menú (Solicitudes, Mi Equipo, Comprar Servicios vs. Mi Crédito) depende de
  // usuario.puedeSolicitar/tipoCliente, cacheados en localStorage desde el login — si un admin
  // aprueba el crédito pospago (GestionCuentas → quita "requiere aprobación") o cambia el tipo de
  // cliente MIENTRAS la sesión sigue abierta, ese caché queda desactualizado hasta que el JWT
  // expire (hasta 24h) y el cliente sigue viendo el menú reducido de "cliente nuevo sin aprobar".
  // refrescarUsuario() ya recalcula ambos campos en vivo contra la BD (AuthService.
  // resolverPuedeSolicitar/resolverTipoCliente) — se llama una vez al entrar al portal cliente.
  useEffect(() => {
    refrescarUsuario()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const esPospago = usuario?.tipoCliente === 'POSPAGO'
  const esAdminCliente = usuario?.rol === 'ADMIN_CLIENTE'
  // false explícito = cliente que se registró solo y todavía no completó su primera compra
  // (prepago) ni tiene un acuerdo de crédito vigente (pospago) — se calcula solo, nadie lo
  // "aprueba" a mano (ver AuthService.resolverPuedeSolicitar). undefined/true (roles internos,
  // clientes ya habilitados, o sesiones de antes de que existiera este campo) no ocultan nada.
  const noPuedeSolicitar = usuario?.puedeSolicitar === false
  const seccionesBase = esAdminCliente ? SECCIONES_ADMIN : SECCIONES_ANALISTA
  // Pospago solicita directo con cupo de crédito — no compra saldo por adelantado, ve "Mi
  // Crédito" en su lugar. El módulo de pagos (incluido el crédito pospago) solo lo ve ADMIN_CLIENTE.
  const secciones = seccionesBase
    .filter((s) => !(noPuedeSolicitar && s.titulo === 'Solicitudes'))
    .map((s) => {
      if (s.titulo !== 'Cuenta') return s
      let items = s.items
      // "Mi Equipo" tampoco tiene sentido antes de la primera compra/acuerdo — mismo criterio
      // que "Solicitudes".
      if (noPuedeSolicitar) items = items.filter((i) => i.ruta !== '/cliente/equipo')
      if (esPospago) {
        items = items.filter((i) => i.ruta !== '/cliente/comprar-servicios')
        if (esAdminCliente) items = [{ ruta: '/cliente/pospago', etiqueta: 'Mi Crédito', icono: <IconoCuenta /> }, ...items]
      }
      return { ...s, items }
    })
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
    <div className="flex h-screen h-[100dvh] bg-gray-50">
      {/* Sidebar desktop */}
      <aside className={`hidden md:flex md:flex-col bg-slate-900 text-white flex-shrink-0 transition-all duration-300 ${collapsed ? 'w-16' : 'w-64'}`}>
        <SidebarContent usuario={usuario} secciones={secciones} collapsed={collapsed} />
      </aside>

      {/* Sidebar móvil */}
      {menuMovil && (
        <>
          <div className="fixed inset-0 bg-black/50 z-40 md:hidden" onClick={() => setMenuMovil(false)} />
          <aside className="fixed inset-y-0 left-0 z-50 flex flex-col w-72 bg-slate-900 text-white md:hidden shadow-2xl pt-safe pb-safe">
            <button onClick={() => setMenuMovil(false)} aria-label="Cerrar menú"
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
        <header className="bg-white border-b border-gray-200 px-4 md:px-6 pt-[calc(0.75rem+var(--sat))] pb-3 flex items-center justify-between flex-shrink-0">
          <div className="flex items-center gap-1">
            <button onClick={() => setMenuMovil(true)} aria-label="Abrir menú"
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
            <AvatarUsuario usuario={usuario} cerrarSesion={cerrarSesion} rutaPerfil={esAdminCliente ? null : '/cliente/perfil'} />
          </div>
        </header>

        <Breadcrumb />

        <main id="contenido" tabIndex={-1} className="flex-1 overflow-y-auto focus:outline-none overflow-x-hidden p-4 md:p-6 pb-[calc(1rem+var(--sab))] md:pb-[calc(1.5rem+var(--sab))] scrollbar-thin">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

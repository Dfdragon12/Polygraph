import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useState, useEffect, useRef, Fragment } from 'react'
import { useAuth } from '../../hooks/useAuth'
import Campana from '../Campana'
import PanelEnlaces from '../PanelEnlaces'
import BuscadorGlobal from '../BuscadorGlobal'
import SidebarSeccion from './SidebarSeccion'
import { IconoDashboard, IconoEquipo, IconoClientes, IconoSemaforo, IconoReversion, IconoCatalogo, IconoFacturacion, IconoCuenta, IconoDescuento, IconoCiudades, IconoDepartamento, IconoLlave, IconoNovedad, IconoRiesgo } from './IconosNav'
import api from '../../services/api'
import reversionService from '../../services/reversionService'
import ciudadService from '../../services/ciudadService'

const ETIQUETAS_ROL = {
  ADMIN_POLYGRAPH: 'Administrador',
  GESTOR: 'Gestor',
  ANALISTA_INTERNO: 'Analista Interno',
  PROGRAMADOR: 'Programador',
  POLIGRAFISTA: 'Poligrafista',
  VISITADOR: 'Visitador',
}

const SECCIONES_ADMIN = [
  {
    titulo: 'General',
    items: [
      { ruta: '/admin/dashboard',          etiqueta: 'Dashboard',         icono: <IconoDashboard /> },
      { ruta: '/admin/usuarios-internos',  etiqueta: 'Equipo Polygraph',  icono: <IconoEquipo /> },
      { ruta: '/admin/clientes',           etiqueta: 'Clientes',          icono: <IconoClientes /> },
    ],
  },
  /*{
    titulo: 'Servicios',
    items: [
      { ruta: '/admin/semaforo', etiqueta: 'Semáforo',  icono: <IconoSemaforo /> },
    ],
  },*/
  {
    titulo: 'Solicitudes',
    items: [
      { ruta: '/admin/reversiones', etiqueta: 'Reversiones', icono: <IconoReversion />, badgeKey: 'reversiones' },
    ],
  },
  {
    titulo: 'Catálogo',
    items: [
      { ruta: '/admin/catalogo', etiqueta: 'Catálogo', icono: <IconoCatalogo /> },
    ],
  },
  {
    titulo: 'Novedades',
    items: [
      { ruta: '/admin/tipos-novedad', etiqueta: 'Tipos de novedad', icono: <IconoNovedad /> },
    ],
  },
  {
    titulo: 'Calificación de riesgo',
    items: [
      { ruta: '/admin/riesgo/parametros', etiqueta: 'Parámetros', icono: <IconoRiesgo /> },
    ],
  },
  {
    titulo: 'Facturación',
    items: [
      { ruta: '/admin/facturacion', etiqueta: 'Facturación', icono: <IconoFacturacion /> },
      { ruta: '/admin/gestion-cuentas', etiqueta: 'Gestión de cuentas', icono: <IconoCuenta /> },
      { ruta: '/admin/descuentos', etiqueta: 'Descuentos', icono: <IconoDescuento /> },
    ],
  },
  {
    titulo: 'Ciudades',
    items: [
      { ruta: '/admin/ciudades', etiqueta: 'Ciudades', icono: <IconoCiudades />, badgeKey: 'ciudades' },
      { ruta: '/admin/departamentos', etiqueta: 'Departamentos', icono: <IconoDepartamento /> },
    ],
  },
]


/* ─── Breadcrumb ─── */
const ETIQUETAS_RUTA = {
  dashboard:           'Dashboard',
  'usuarios-internos': 'Equipo Polygraph',
  clientes:            'Clientes',
  catalogo:            'Catálogo',
  procesos:            'Procesos',
  'tipos-progreso':    'Tipos de Subproceso',
  'tipos-novedad':     'Tipos de novedad',
  riesgo:              'Calificación de riesgo',
  parametros:          'Parámetros',
  semaforo:            'Semáforo de Servicios',
  candidatos:          'Candidatos',
  reversiones:         'Solicitudes de reversión',
  descuentos:          'Descuentos',
  ciudades:            'Ciudades',
  departamentos:       'Departamentos',
  facturacion:         'Facturación',
  'gestion-cuentas':   'Gestión de cuentas',
  ordenes:             'Órdenes de compra',
  comprobantes:        'Comprobante',
  facturas:            'Factura',
}

function Breadcrumb() {
  const { pathname } = useLocation()
  const [clsMap, setClsMap] = useState({})

  useEffect(() => {
    if (!pathname.match(/\/admin\/catalogo\/.+/)) return
    api.get('/catalogo/clasificaciones')
      .then(r => {
        const m = {}
        r.data.forEach(c => { m[c.codigo] = c.nombre })
        setClsMap(m)
      })
      .catch(() => {})
  }, [pathname])

  const segmentos = pathname.split('/').filter(Boolean).slice(1)

  const migas = [
    { label: 'Panel Interno', ruta: '/admin/dashboard' },
    ...segmentos.map((seg, i) => ({
      label: ETIQUETAS_RUTA[seg] ?? clsMap[seg] ?? seg,
      ruta: '/admin/' + segmentos.slice(0, i + 1).join('/'),
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

/* ─── Contenido del sidebar ─── */
function SidebarContent({ usuario, onNavClick, collapsed, badges = {} }) {
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
            <p className="text-sm font-semibold text-white truncate">{usuario?.nombre ?? 'Usuario'}</p>
            <span className="text-xs text-slate-400">{ETIQUETAS_ROL[usuario?.rol] ?? usuario?.rol}</span>
          </>
        )}
      </div>

      <nav className={`flex-1 py-4 overflow-y-auto scrollbar-thin space-y-5 ${collapsed ? 'px-1' : 'px-3'}`}>
        {SECCIONES_ADMIN.map(seccion => (
          <SidebarSeccion key={seccion.titulo} titulo={seccion.titulo} items={seccion.items}
            collapsed={collapsed} badges={badges} onNavClick={onNavClick} />
        ))}
      </nav>
    </>
  )
}

/* ─── Menú de configuración ─── */
function ConfiguracionMenu() {
  const [abierto, setAbierto] = useState(false)
  const ref = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setAbierto(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const opciones = [
    { label: 'Cambiar contraseña', icono: <IconoLlave />, accion: () => { navigate('/cambiar-password'); setAbierto(false) } },
  ]

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setAbierto(v => !v)}
        title="Configuración"
        className="p-2 text-gray-500 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors">
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round"
            d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </button>

      {abierto && (
        <div className="fixed left-3 right-3 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-10 sm:w-52 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden py-1">
          {opciones.map(o => (
            <button key={o.label} onClick={o.accion}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors text-left">
              <span>{o.icono}</span>
              {o.label}
            </button>
          ))}
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

  const iniciales = [usuario?.nombre?.[0], usuario?.apellido?.[0]]
    .filter(Boolean).join('').toUpperCase() || '?'

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setAbierto(v => !v)}
        className="flex items-center gap-2 p-1 rounded-lg hover:bg-gray-100 transition-colors">
        <div className="w-8 h-8 rounded-full bg-primary-600 text-white text-xs font-bold flex items-center justify-center flex-shrink-0">
          {iniciales}
        </div>
        <div className="hidden md:block text-left">
          <p className="text-xs font-semibold text-gray-800 leading-tight max-w-[120px] truncate">
            {usuario?.nombre} {usuario?.apellido ?? ''}
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
            <p className="text-sm font-semibold text-gray-900 truncate">{usuario?.nombre} {usuario?.apellido ?? ''}</p>
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

export default function AdminLayout() {
  const { usuario, logout } = useAuth()
  const navigate = useNavigate()
  const [menuMovil, setMenuMovil] = useState(false)
  const [panelEnlaces, setPanelEnlaces] = useState(false)
  const [collapsed, setCollapsed] = useState(() =>
    localStorage.getItem('admin-sidebar-collapsed') === 'true'
  )
  const [badges, setBadges] = useState({})
  const cerrarSesion = () => { logout(); navigate('/login') }

  useEffect(() => {
    const cargarBadges = () => {
      reversionService.contarPendientes()
        .then(n => setBadges(b => ({ ...b, reversiones: n })))
        .catch(() => {})
      ciudadService.contarPendientes()
        .then(n => setBadges(b => ({ ...b, ciudades: n })))
        .catch(() => {})
    }
    cargarBadges()
    const t = setInterval(cargarBadges, 30000)
    return () => clearInterval(t)
  }, [])

  const toggleSidebar = () => {
    const next = !collapsed
    setCollapsed(next)
    localStorage.setItem('admin-sidebar-collapsed', String(next))
  }

  return (
    <div className="flex h-screen h-[100dvh] bg-gray-50">
      {/* ─── Sidebar desktop ─── */}
      <aside className={`hidden md:flex md:flex-col bg-slate-900 text-white flex-shrink-0 transition-all duration-300 ${collapsed ? 'w-16' : 'w-64'}`}>
        <SidebarContent usuario={usuario} collapsed={collapsed} badges={badges} />
      </aside>

      {/* ─── Sidebar móvil ─── */}
      {menuMovil && (
        <>
          <div className="fixed inset-0 bg-black/50 z-40 md:hidden" onClick={() => setMenuMovil(false)} />
          <aside className="fixed inset-y-0 left-0 z-50 flex flex-col w-72 bg-slate-900 text-white md:hidden shadow-2xl pt-safe pb-safe">
            <button onClick={() => setMenuMovil(false)}
              className="absolute top-4 right-4 p-1 text-slate-400 hover:text-white">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
            <SidebarContent usuario={usuario} onNavClick={() => setMenuMovil(false)} collapsed={false} badges={badges} />
          </aside>
        </>
      )}

      {/* ─── Área principal ─── */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        <header className="bg-white border-b border-gray-200 px-4 md:px-6 pt-[calc(0.75rem+var(--sat))] pb-3 flex items-center justify-between flex-shrink-0">
          {/* Izquierda — hamburger móvil + toggle desktop */}
          <div className="flex items-center gap-1">
            {/* Móvil: abre overlay */}
            <button onClick={() => setMenuMovil(true)}
              className="md:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>
            {/* Desktop: colapsa / expande sidebar */}
            <button onClick={toggleSidebar}
              title={collapsed ? 'Expandir menú' : 'Colapsar menú'}
              className="hidden md:flex p-2 rounded-lg text-gray-500 hover:text-gray-800 hover:bg-gray-100 transition-colors">
              <svg className={`h-5 w-5 transition-transform duration-300 ${collapsed ? 'rotate-180' : ''}`}
                fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M11 19l-7-7 7-7M18 19l-7-7 7-7" />
              </svg>
            </button>
          </div>

          {/* Derecha — acciones */}
          <div className="flex items-center gap-1">
            <BuscadorGlobal secciones={SECCIONES_ADMIN} busquedaEntidades />
            <ConfiguracionMenu />
            <button onClick={() => setPanelEnlaces(true)} title="Referencias externas"
              className="relative p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
              </svg>
            </button>
            <Campana />
            <div className="w-px h-6 bg-gray-200 mx-1" />
            <AvatarUsuario usuario={usuario} cerrarSesion={cerrarSesion} />
          </div>
        </header>

        <Breadcrumb />

        <main className="flex-1 overflow-y-auto overflow-x-hidden p-4 md:p-6 pb-[calc(1rem+var(--sab))] md:pb-[calc(1.5rem+var(--sab))] scrollbar-thin">
          <Outlet />
        </main>
      </div>

      <PanelEnlaces abierto={panelEnlaces} onCerrar={() => setPanelEnlaces(false)} />
    </div>
  )
}

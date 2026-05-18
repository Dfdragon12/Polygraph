import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'

const MENU_ADMIN = [
  { ruta: '/cliente/dashboard',       etiqueta: 'Dashboard',            icono: '📊' },
  { ruta: '/cliente/catalogo',        etiqueta: 'Catálogo de Servicios', icono: '📋' },
  { ruta: '/cliente/nueva-solicitud', etiqueta: 'Nueva Solicitud',       icono: '➕' },
  { ruta: '/cliente/carga-masiva',    etiqueta: 'Carga Masiva',          icono: '📤' },
  { ruta: '/cliente/solicitudes',     etiqueta: 'Mis Solicitudes',       icono: '📁' },
]

const MENU_ANALISTA = [
  { ruta: '/cliente/dashboard',       etiqueta: 'Dashboard',       icono: '📊' },
  { ruta: '/cliente/nueva-solicitud', etiqueta: 'Nueva Solicitud', icono: '➕' },
  { ruta: '/cliente/solicitudes',     etiqueta: 'Mis Solicitudes', icono: '📁' },
]

export default function ClientLayout() {
  const { usuario, logout } = useAuth()
  const navigate = useNavigate()
  const menu = usuario?.rol === 'ADMIN_CLIENTE' ? MENU_ADMIN : MENU_ANALISTA

  const cerrarSesion = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="hidden md:flex md:flex-col w-64 bg-slate-900 text-white flex-shrink-0">
        {/* Cabecera */}
        <div className="px-6 py-5 border-b border-slate-700">
          <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">Polygraph Service</p>
          <p className="text-sm font-semibold text-white truncate">
            {usuario?.nombre ?? 'Cliente'}
          </p>
          <span className="text-xs text-slate-400">
            {usuario?.rol === 'ADMIN_CLIENTE' ? 'Administrador' : 'Analista'}
          </span>
        </div>

        {/* Navegación */}
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {menu.map(({ ruta, etiqueta, icono }) => (
            <NavLink
              key={ruta}
              to={ruta}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                  isActive
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`
              }
            >
              <span className="text-base">{icono}</span>
              {etiqueta}
            </NavLink>
          ))}
        </nav>

        {/* Cerrar sesión */}
        <div className="px-3 py-4 border-t border-slate-700">
          <button
            onClick={cerrarSesion}
            className="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm text-slate-300 hover:bg-slate-800 hover:text-white transition-colors"
          >
            <span className="text-base">🚪</span>
            Cerrar sesión
          </button>
        </div>
      </aside>

      {/* Área principal */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <header className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between flex-shrink-0">
          <h1 className="text-base font-semibold text-gray-800">Portal Cliente</h1>
          <span className="text-sm text-gray-500">{usuario?.email}</span>
        </header>

        {/* Contenido */}
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

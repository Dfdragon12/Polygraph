import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { useAuth } from './hooks/useAuth'
import ProtectedRoute from './components/ProtectedRoute'
import OfflineIndicator from './components/OfflineIndicator'
import PWAInstallBanner from './components/PWAInstallBanner'

// Layouts
import ClientLayout      from './components/layout/ClientLayout'
import AdminLayout       from './components/layout/AdminLayout'
import GestorLayout      from './components/layout/GestorLayout'
import AnalistaLayout    from './components/layout/AnalistaLayout'
import ProgramadorLayout from './components/layout/ProgramadorLayout'
import PoligrafistLayout from './components/layout/PoligrafistLayout'
import VisitadorLayout   from './components/layout/VisitadorLayout'

// Páginas públicas
import Login           from './pages/Login'
import Register        from './pages/Register'
import EvalueeForm     from './pages/evaluee/EvalueeForm'
import CambiarPassword from './pages/CambiarPassword'

// Portal cliente
import Dashboard        from './pages/client/Dashboard'
import ServiceCatalog   from './pages/client/ServiceCatalog'
import NewRequest       from './pages/client/NewRequest'
import BulkUpload       from './pages/client/BulkUpload'
import Solicitudes      from './pages/client/Solicitudes'
import SolicitudDetalle from './pages/client/SolicitudDetalle'

// Portal admin
import AdminDashboard    from './pages/admin/AdminDashboard'
import UsuariosInternos  from './pages/admin/UsuariosInternos'
import Clientes          from './pages/admin/Clientes'
import SemaforoServicios from './pages/admin/SemaforoServicios'
import Catalogo          from './pages/admin/catalogo/Catalogo'

// Portales de rol
import GestorDashboard       from './pages/gestor/GestorDashboard'
import GestorSolicitudes     from './pages/gestor/Solicitudes'
import AnalistaDashboard     from './pages/analista/AnalistaDashboard'
import ProgramadorDashboard  from './pages/programador/ProgramadorDashboard'
import PoligrafistaDashboard from './pages/poligrafista/PoligrafistaDashboard'
import VisitadorDashboard    from './pages/visitador/VisitadorDashboard'

const ROLES_CLIENTE = ['ADMIN_CLIENTE', 'ANALISTA_CLIENTE']

const RUTA_POR_ROL = {
  ADMIN_POLYGRAPH:  '/admin/dashboard',
  GESTOR:           '/gestor/dashboard',
  ANALISTA_INTERNO: '/analista/dashboard',
  PROGRAMADOR:      '/programador/dashboard',
  POLIGRAFISTA:     '/poligrafista/dashboard',
  VISITADOR:        '/visitador/dashboard',
  ADMIN_CLIENTE:    '/cliente/dashboard',
  ANALISTA_CLIENTE: '/cliente/dashboard',
}

function RootRedirect() {
  const { usuario } = useAuth()
  const ruta = RUTA_POR_ROL[usuario?.rol] ?? '/login'
  return <Navigate to={ruta} replace />
}

function Proximamente({ titulo }) {
  return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <span className="text-4xl">🚧</span>
      <h2 className="text-lg font-semibold text-gray-700">{titulo}</h2>
      <p className="text-gray-400 text-sm">Esta sección estará disponible próximamente.</p>
    </div>
  )
}

function PaginaPublica({ titulo, mensaje }) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="text-center">
        <h2 className="text-xl font-bold text-gray-900 mb-2">{titulo}</h2>
        {mensaje && <p className="text-gray-500 text-sm">{mensaje}</p>}
      </div>
    </div>
  )
}

function App() {
  return (
    <AuthProvider>
      <OfflineIndicator />
      <PWAInstallBanner />
      <Routes>
        {/* ── Públicas ── */}
        <Route path="/login"            element={<Login />} />
        <Route path="/registro"         element={<Register />} />
        <Route path="/cambiar-password" element={<ProtectedRoute><CambiarPassword /></ProtectedRoute>} />
        <Route path="/evaluado/link/:token"      element={<EvalueeForm />} />
        <Route path="/evaluado/completar/:token" element={<EvalueeForm />} />
        <Route path="/activate" element={<PaginaPublica titulo="Activando tu cuenta..." mensaje="Serás redirigido en un momento." />} />
        <Route path="/no-autorizado" element={
          <div className="min-h-screen flex items-center justify-center bg-slate-50">
            <div className="text-center">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">Acceso denegado</h2>
              <p className="text-gray-500 mb-4">No tienes permisos para ver esta página.</p>
              <a href="/" className="text-indigo-600 hover:underline text-sm">Volver al inicio</a>
            </div>
          </div>
        } />

        {/* ── Portal cliente ── */}
        <Route path="/cliente" element={<ProtectedRoute roles={ROLES_CLIENTE}><ClientLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard"         element={<Dashboard />} />
          <Route path="catalogo"          element={<ServiceCatalog />} />
          <Route path="nueva-solicitud"   element={<NewRequest />} />
          <Route path="carga-masiva"      element={<BulkUpload />} />
          <Route path="solicitudes"       element={<Solicitudes />} />
          <Route path="solicitudes/:id"   element={<SolicitudDetalle />} />
          <Route path="comprar-servicios" element={<Proximamente titulo="Comprar Servicios" />} />
          <Route path="documentos"        element={<Proximamente titulo="Documentos de la empresa" />} />
          <Route path="estadisticas"      element={<Proximamente titulo="Estadísticas" />} />
        </Route>

        {/* ── Portal ADMIN_POLYGRAPH ── */}
        <Route path="/admin" element={<ProtectedRoute roles={['ADMIN_POLYGRAPH']}><AdminLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard"         element={<AdminDashboard />} />
          <Route path="usuarios-internos" element={<UsuariosInternos />} />
          <Route path="clientes"          element={<Clientes />} />
          <Route path="semaforo"          element={<SemaforoServicios />} />
          <Route path="catalogo"                  element={<Catalogo key="grid" />} />
          <Route path="catalogo/:clasificacion"   element={<Catalogo key="detalle" />} />
        </Route>

        {/* ── Portal GESTOR ── */}
        <Route path="/gestor" element={<ProtectedRoute roles={['GESTOR']}><GestorLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard"   element={<GestorDashboard />} />
          <Route path="solicitudes" element={<GestorSolicitudes />} />
        </Route>

        {/* ── Portal ANALISTA_INTERNO ── */}
        <Route path="/analista" element={<ProtectedRoute roles={['ANALISTA_INTERNO']}><AnalistaLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AnalistaDashboard />} />
        </Route>

        {/* ── Portal PROGRAMADOR ── */}
        <Route path="/programador" element={<ProtectedRoute roles={['PROGRAMADOR']}><ProgramadorLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<ProgramadorDashboard />} />
        </Route>

        {/* ── Portal POLIGRAFISTA ── */}
        <Route path="/poligrafista" element={<ProtectedRoute roles={['POLIGRAFISTA']}><PoligrafistLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<PoligrafistaDashboard />} />
        </Route>

        {/* ── Portal VISITADOR ── */}
        <Route path="/visitador" element={<ProtectedRoute roles={['VISITADOR']}><VisitadorLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<VisitadorDashboard />} />
        </Route>

        {/* ── Raíz — redirige según rol ── */}
        <Route path="/" element={<ProtectedRoute><RootRedirect /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

export default App

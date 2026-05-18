import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import ClientLayout from './components/layout/ClientLayout'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/client/Dashboard'
import ServiceCatalog from './pages/client/ServiceCatalog'
import NewRequest from './pages/client/NewRequest'
import BulkUpload from './pages/client/BulkUpload'
import EvalueeForm from './pages/evaluee/EvalueeForm'

const ROLES_CLIENTE = ['ADMIN_CLIENTE', 'ANALISTA_CLIENTE']

function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Rutas públicas */}
        <Route path="/login" element={<Login />} />
        <Route path="/registro" element={<Register />} />

        {/* Formulario del evaluado (público, sin auth) */}
        <Route path="/evaluado/link/:token" element={<EvalueeForm />} />
        <Route path="/evaluado/completar/:token" element={<EvalueeForm />} />

        {/* Activación de cuenta */}
        <Route path="/activate" element={
          <div className="min-h-screen flex items-center justify-center bg-slate-50">
            <div className="text-center">
              <h2 className="text-xl font-bold text-gray-900 mb-2">Activando tu cuenta...</h2>
              <p className="text-gray-500 text-sm">Serás redirigido en un momento.</p>
            </div>
          </div>
        } />

        {/* No autorizado */}
        <Route path="/no-autorizado" element={
          <div className="min-h-screen flex items-center justify-center bg-slate-50">
            <div className="text-center">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">Acceso denegado</h2>
              <p className="text-gray-500 mb-4">No tienes permisos para ver esta página.</p>
              <a href="/" className="text-indigo-600 hover:underline text-sm">Volver al inicio</a>
            </div>
          </div>
        } />

        {/* Portal cliente */}
        <Route
          path="/cliente"
          element={
            <ProtectedRoute roles={ROLES_CLIENTE}>
              <ClientLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="catalogo" element={<ServiceCatalog />} />
          <Route path="nueva-solicitud" element={<NewRequest />} />
          <Route path="carga-masiva" element={<BulkUpload />} />
        </Route>

        {/* Raíz */}
        <Route path="/" element={
          <ProtectedRoute>
            <Navigate to="/cliente/dashboard" replace />
          </ProtectedRoute>
        } />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

export default App

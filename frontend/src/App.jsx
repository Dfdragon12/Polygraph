import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import Login from './pages/Login'
import Register from './pages/Register'

function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Rutas públicas */}
        <Route path="/login" element={<Login />} />
        <Route path="/registro" element={<Register />} />

        {/* Ruta de activación */}
        <Route path="/activate" element={
          <div className="min-h-screen flex items-center justify-center bg-slate-50">
            <div className="text-center">
              <h2 className="text-xl font-bold text-gray-900 mb-2">Activando tu cuenta...</h2>
              <p className="text-gray-500 text-sm">Serás redirigido en un momento.</p>
            </div>
          </div>
        } />

        {/* Ruta de no autorizado */}
        <Route path="/no-autorizado" element={
          <div className="min-h-screen flex items-center justify-center bg-slate-50">
            <div className="text-center">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">Acceso denegado</h2>
              <p className="text-gray-500 mb-4">No tienes permisos para ver esta página.</p>
              <a href="/" className="text-indigo-600 hover:underline text-sm">Volver al inicio</a>
            </div>
          </div>
        } />

        {/* Rutas protegidas */}
        <Route path="/" element={
          <ProtectedRoute>
            <div className="min-h-screen flex items-center justify-center bg-slate-50">
              <div className="text-center">
                <h2 className="text-2xl font-bold text-gray-900 mb-2">Dashboard</h2>
                <p className="text-gray-500 text-sm">En construcción — módulos en desarrollo.</p>
              </div>
            </div>
          </ProtectedRoute>
        } />

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

export default App

import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { CarritoProvider } from './context/CarritoContext'
import { useAuth } from './hooks/useAuth'
import useInactivityLogout from './hooks/useInactivityLogout'
import ProtectedRoute from './components/ProtectedRoute'
import OfflineIndicator from './components/OfflineIndicator'
import PWAInstallBanner from './components/PWAInstallBanner'
import { ConfirmModal } from './components/ui/Modal'
import BannerCookies from './components/legal/BannerCookies'
import PaginaLegal from './pages/legal/PaginaLegal'

// Layouts
import ClientLayout      from './components/layout/ClientLayout'
import AdminLayout       from './components/layout/AdminLayout'
import GestorLayout      from './components/layout/GestorLayout'
import AnalistaLayout    from './components/layout/AnalistaLayout'
import ProgramadorLayout from './components/layout/ProgramadorLayout'
import PoligrafistLayout from './components/layout/PoligrafistLayout'
import VisitadorLayout   from './components/layout/VisitadorLayout'

// Páginas públicas
import Login             from './pages/Login'
import Register          from './pages/Register'
import EvalueeForm       from './pages/evaluee/EvalueeForm'
import CambiarPassword   from './pages/CambiarPassword'
import RecuperarPassword from './pages/RecuperarPassword'
import ResetPassword     from './pages/ResetPassword'

// Portal cliente
import Dashboard        from './pages/client/Dashboard'
import ServiceCatalog   from './pages/client/ServiceCatalog'
import NewRequest       from './pages/client/NewRequest'
import BulkUpload       from './pages/client/BulkUpload'
import Solicitudes      from './pages/client/Solicitudes'
import SolicitudDetalle from './pages/client/SolicitudDetalle'
import Documentos       from './pages/client/Documentos'
import MiEquipo         from './pages/client/MiEquipo'
import ComprarServicios from './pages/client/ComprarServicios'
import SimuladorPago    from './pages/client/SimuladorPago'
import ResultadoPago    from './pages/client/ResultadoPago'
import ComprobantePago  from './pages/client/ComprobantePago'
import MiCredito             from './pages/client/MiCredito'
import FacturaPospagoDetalle from './pages/client/FacturaPospagoDetalle'
import ResultadoPagoFactura  from './pages/client/ResultadoPagoFactura'

// Portal admin
import AdminDashboard    from './pages/admin/AdminDashboard'
import UsuariosInternos  from './pages/admin/UsuariosInternos'
import Clientes          from './pages/admin/Clientes'
import Productividad from './pages/shared/Productividad'
import SemaforoServicios from './pages/admin/SemaforoServicios'
import Catalogo          from './pages/admin/catalogo/Catalogo'
import PlantillaFormulario from './pages/admin/catalogo/PlantillaFormulario'
import TiposNovedad from './pages/admin/catalogo/TiposNovedad'
import HojaVidaCandidato from './pages/admin/catalogo/HojaVidaCandidato'
import TemasPoligrafia from './pages/admin/catalogo/TemasPoligrafia'
import MotivosSubproceso from './pages/admin/catalogo/MotivosSubproceso'
import ParametrosRiesgo from './pages/admin/riesgo/ParametrosRiesgo'
import CentralesRiesgo from './pages/admin/riesgo/CentralesRiesgo'
import CatalogoHallazgos from './pages/admin/riesgo/CatalogoHallazgos'
import TrabajosSegundoPlano from './pages/admin/TrabajosSegundoPlano'
import AdminReversiones  from './pages/admin/AdminReversiones'
import Descuentos        from './pages/admin/Descuentos'
import Ciudades          from './pages/admin/Ciudades'
import Festivos          from './pages/admin/Festivos'
import ParametrosTiempo  from './pages/admin/ParametrosTiempo'
import Departamentos     from './pages/admin/Departamentos'
import Facturacion         from './pages/admin/Facturacion'
import GestionCuentas      from './pages/admin/GestionCuentas'

// Portales de rol
import GestorDashboard       from './pages/gestor/GestorDashboard'
import GestorSolicitudes     from './pages/gestor/Solicitudes'
import GestorReversiones     from './pages/gestor/GestorReversiones'
import GestorAgenda          from './pages/gestor/Agenda'
import GestorClienteDetalle  from './pages/gestor/ClienteDetalle'
import ProgramadorDashboard  from './pages/programador/ProgramadorDashboard'
import ProgramadorAgenda     from './pages/programador/Agenda'
// Compartidas entre analista/poligrafista/visitador — mismo backend filtra por rol autenticado
import AsignacionDashboard from './pages/shared/AsignacionDashboard'
import MisAsignaciones from './pages/shared/MisAsignaciones'
import MiPerfil from './pages/shared/MiPerfil'
import MiPerfilAnalista from './pages/client/MiPerfilAnalista'
import Formulario       from './pages/shared/Formulario'
import FormularioPoligrafista from './pages/poligrafista/FormatoPoligrafia'
import FormularioVisitador from './pages/visitador/FormatoVisita'
import FormularioAnalista from './pages/analista/FormatoAntecedentes'
import ProveedorExterno from './pages/proveedor/ProveedorExterno'
import CandidatoPerfil  from './pages/shared/CandidatoPerfil'

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

function InactivityWatcher() {
  const { mostrarAlerta, seguirConectado } = useInactivityLogout()
  if (!mostrarAlerta) return null

  // Sin botones ni contador en vivo a propósito — cerrar con la X ya significa "sigo aquí"
  // (equivale a lo que antes hacía "Seguir conectado"); si de verdad no hay nadie, el cierre de
  // sesión automático de todos modos sigue corriendo en segundo plano.
  return (
    <ConfirmModal
      titulo="¿Sigues ahí?"
      onClose={seguirConectado}
      sinBotones
      bloquearFondo
    >
      <p className="text-sm text-gray-500">
        Por seguridad, vamos a cerrar tu sesión pronto por inactividad — cierra este aviso si sigues aquí.
      </p>
    </ConfirmModal>
  )
}

function App() {
  return (
    <AuthProvider>
      <a href="#contenido" className="saltar-contenido">Saltar al contenido</a>
      <InactivityWatcher />
      <OfflineIndicator />
      <PWAInstallBanner />
      <BannerCookies />
      <Routes>
        {/* ── Públicas ── */}
        <Route path="/login"              element={<Login />} />
        <Route path="/legal/:tipo"        element={<PaginaLegal />} />
        <Route path="/registro"           element={<Register />} />
        <Route path="/recuperar-password" element={<RecuperarPassword />} />
        <Route path="/reset-password"     element={<ResetPassword />} />
        <Route path="/cambiar-password"   element={<ProtectedRoute><CambiarPassword /></ProtectedRoute>} />
        <Route path="/evaluado/link/:token"      element={<EvalueeForm />} />
        <Route path="/evaluado/completar/:token" element={<EvalueeForm />} />
        {/* PF-09: proveedor externo de poligrafía — entra con su enlace, sin usuario ni contraseña */}
        <Route path="/proveedor/:token"          element={<ProveedorExterno />} />
        <Route path="/no-autorizado" element={
          <div className="min-h-screen flex items-center justify-center bg-slate-50">
            <div className="text-center">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">Acceso denegado</h2>
              <p className="text-gray-500 mb-4">No tienes permisos para ver esta página.</p>
              <a href="/" className="text-primary-600 hover:underline text-sm">Volver al inicio</a>
            </div>
          </div>
        } />

        {/* ── Portal cliente ── */}
        <Route path="/cliente" element={
          <ProtectedRoute roles={ROLES_CLIENTE}>
            <CarritoProvider><ClientLayout /></CarritoProvider>
          </ProtectedRoute>
        }>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard"         element={<Dashboard />} />
          <Route path="catalogo"          element={<ServiceCatalog />} />
          <Route path="nueva-solicitud"   element={<NewRequest />} />
          <Route path="carga-masiva"      element={<BulkUpload />} />
          <Route path="solicitudes"       element={<Solicitudes />} />
          <Route path="solicitudes/:id"   element={<SolicitudDetalle />} />
          <Route path="comprar-servicios" element={<ComprarServicios />} />
          <Route path="comprar-servicios/simulador/:idOrden" element={<SimuladorPago />} />
          <Route path="comprar-servicios/resultado/:idOrden" element={<ResultadoPago />} />
          <Route path="comprar-servicios/comprobante/:idComprobante" element={<ComprobantePago />} />
          <Route path="pospago"                       element={<MiCredito />} />
          <Route path="pospago/facturas/:idFactura"   element={<FacturaPospagoDetalle />} />
          <Route path="pospago/resultado/:referencia" element={<ResultadoPagoFactura />} />
          <Route path="documentos"        element={<Documentos />} />
          <Route path="equipo"            element={<MiEquipo />} />
          <Route path="perfil"            element={<ProtectedRoute roles={['ANALISTA_CLIENTE']}><MiPerfilAnalista /></ProtectedRoute>} />
          <Route path="estadisticas"      element={<Proximamente titulo="Estadísticas" />} />
        </Route>

        {/* ── Portal ADMIN_POLYGRAPH ── */}
        <Route path="/admin" element={<ProtectedRoute roles={['ADMIN_POLYGRAPH']}><AdminLayout /></ProtectedRoute>}>
          <Route path="productividad" element={<Productividad />} />
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard"         element={<AdminDashboard />} />
          <Route path="usuarios-internos" element={<UsuariosInternos />} />
          <Route path="clientes"          element={<Clientes />} />
          <Route path="semaforo"          element={<SemaforoServicios />} />
          <Route path="reversiones"       element={<AdminReversiones />} />
          <Route path="catalogo"                  element={<Catalogo key="grid" />} />
          <Route path="catalogo/plantilla/:idTipoProgreso" element={<PlantillaFormulario />} />
          <Route path="tipos-novedad" element={<TiposNovedad />} />
          <Route path="hoja-vida-candidato" element={<HojaVidaCandidato />} />
          <Route path="temas-poligrafia" element={<TemasPoligrafia />} />
          <Route path="motivos-subproceso" element={<MotivosSubproceso />} />
          <Route path="riesgo/parametros" element={<ParametrosRiesgo />} />
          <Route path="riesgo/centrales" element={<CentralesRiesgo />} />
          <Route path="riesgo/hallazgos" element={<CatalogoHallazgos />} />
          <Route path="trabajos" element={<TrabajosSegundoPlano />} />
          <Route path="formulario/:idServicioSubproceso" element={<Formulario />} />
          <Route path="catalogo/:clasificacion"   element={<Catalogo key="detalle" />} />
          <Route path="descuentos"                element={<Descuentos />} />
          <Route path="ciudades"                  element={<Ciudades />} />
          <Route path="festivos"                  element={<Festivos />} />
          <Route path="parametros-tiempo"         element={<ParametrosTiempo />} />
          <Route path="departamentos"             element={<Departamentos />} />
          <Route path="facturacion"                          element={<Facturacion />} />
          <Route path="gestion-cuentas"                      element={<GestionCuentas />} />
        </Route>

        {/* ── Portal GESTOR ── */}
        <Route path="/gestor" element={<ProtectedRoute roles={['GESTOR']}><GestorLayout /></ProtectedRoute>}>
          <Route path="productividad" element={<Productividad />} />
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard"    element={<GestorDashboard />} />
          <Route path="solicitudes"   element={<GestorSolicitudes />} />
          <Route path="agenda"       element={<GestorAgenda />} />
          <Route path="reversiones"  element={<GestorReversiones />} />
          <Route path="perfil" element={<MiPerfil />} />
          <Route path="clientes/:idCliente" element={<GestorClienteDetalle />} />
          <Route path="formulario/:idServicioSubproceso" element={<Formulario />} />
          <Route path="candidatos/:idCandidato" element={<CandidatoPerfil />} />
        </Route>

        {/* ── Portal ANALISTA_INTERNO ── */}
        <Route path="/analista" element={<ProtectedRoute roles={['ANALISTA_INTERNO']}><AnalistaLayout /></ProtectedRoute>}>
          <Route path="productividad" element={<Productividad />} />
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AsignacionDashboard />} />
          <Route path="asignaciones" element={<MisAsignaciones />} />
          <Route path="perfil" element={<MiPerfil />} />
          <Route path="formulario/:idServicioSubproceso" element={<FormularioAnalista />} />
          <Route path="candidatos/:idCandidato" element={<CandidatoPerfil />} />
        </Route>

        {/* ── Portal PROGRAMADOR ── */}
        <Route path="/programador" element={<ProtectedRoute roles={['PROGRAMADOR']}><ProgramadorLayout /></ProtectedRoute>}>
          <Route path="productividad" element={<Productividad />} />
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<ProgramadorDashboard />} />
          <Route path="agenda" element={<ProgramadorAgenda />} />
          <Route path="perfil" element={<MiPerfil />} />
          <Route path="formulario/:idServicioSubproceso" element={<Formulario />} />
          <Route path="candidatos/:idCandidato" element={<CandidatoPerfil />} />
        </Route>

        {/* ── Portal POLIGRAFISTA ── */}
        <Route path="/poligrafista" element={<ProtectedRoute roles={['POLIGRAFISTA']}><PoligrafistLayout /></ProtectedRoute>}>
          <Route path="productividad" element={<Productividad />} />
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AsignacionDashboard />} />
          <Route path="servicios" element={<MisAsignaciones />} />
          <Route path="perfil" element={<MiPerfil />} />
          <Route path="formulario/:idServicioSubproceso" element={<FormularioPoligrafista />} />
          <Route path="candidatos/:idCandidato" element={<CandidatoPerfil />} />
        </Route>

        {/* ── Portal VISITADOR ── */}
        <Route path="/visitador" element={<ProtectedRoute roles={['VISITADOR']}><VisitadorLayout /></ProtectedRoute>}>
          <Route path="productividad" element={<Productividad />} />
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AsignacionDashboard />} />
          <Route path="visitas" element={<MisAsignaciones />} />
          <Route path="perfil" element={<MiPerfil />} />
          <Route path="formulario/:idServicioSubproceso" element={<FormularioVisitador />} />
          <Route path="candidatos/:idCandidato" element={<CandidatoPerfil />} />
        </Route>

        {/* ── Raíz — redirige según rol ── */}
        <Route path="/" element={<ProtectedRoute><RootRedirect /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

export default App

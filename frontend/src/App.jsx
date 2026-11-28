import { lazy, Suspense, useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { CarritoProvider } from './context/CarritoContext'
import { useAuth } from './hooks/useAuth'
import useInactivityLogout from './hooks/useInactivityLogout'
import { prepararAvisos } from './components/Toast'
import ProtectedRoute from './components/ProtectedRoute'
import OfflineIndicator from './components/OfflineIndicator'
import PWAInstallBanner from './components/PWAInstallBanner'
import { ConfirmModal } from './components/ui/Modal'
import Cargando from './components/Cargando'
import BannerCookies from './components/legal/BannerCookies'
const PaginaLegal = lazy(() => import('./pages/legal/PaginaLegal'))

// PC-08: cada pantalla y cada portal (layout) se descarga SOLO cuando se entra a ella (React.lazy). El paquete principal lleva el login, la sesión y lo que se ve en todas
// partes; quien abre el login no descarga los módulos de admin, gestor, analista, visitador, poligrafista, programador ni cliente.
// Layouts
const ClientLayout = lazy(() => import('./components/layout/ClientLayout'))
const AdminLayout = lazy(() => import('./components/layout/AdminLayout'))
const GestorLayout = lazy(() => import('./components/layout/GestorLayout'))
const AnalistaLayout = lazy(() => import('./components/layout/AnalistaLayout'))
const ProgramadorLayout = lazy(() => import('./components/layout/ProgramadorLayout'))
const PoligrafistLayout = lazy(() => import('./components/layout/PoligrafistLayout'))
const VisitadorLayout = lazy(() => import('./components/layout/VisitadorLayout'))

// Páginas públicas
import Login             from './pages/Login'
const Register = lazy(() => import('./pages/Register'))
const EvalueeForm = lazy(() => import('./pages/evaluee/EvalueeForm'))
const CambiarPassword = lazy(() => import('./pages/CambiarPassword'))
const RecuperarPassword = lazy(() => import('./pages/RecuperarPassword'))
const ResetPassword = lazy(() => import('./pages/ResetPassword'))

// Portal cliente
const Dashboard = lazy(() => import('./pages/client/Dashboard'))
const ServiceCatalog = lazy(() => import('./pages/client/ServiceCatalog'))
const NewRequest = lazy(() => import('./pages/client/NewRequest'))
const BulkUpload = lazy(() => import('./pages/client/BulkUpload'))
const Solicitudes = lazy(() => import('./pages/client/Solicitudes'))
const SolicitudDetalle = lazy(() => import('./pages/client/SolicitudDetalle'))
const Documentos = lazy(() => import('./pages/client/Documentos'))
const MiEquipo = lazy(() => import('./pages/client/MiEquipo'))
const ComprarServicios = lazy(() => import('./pages/client/ComprarServicios'))
const SimuladorPago = lazy(() => import('./pages/client/SimuladorPago'))
const ResultadoPago = lazy(() => import('./pages/client/ResultadoPago'))
const ComprobantePago = lazy(() => import('./pages/client/ComprobantePago'))
const MiCredito = lazy(() => import('./pages/client/MiCredito'))
const FacturaPospagoDetalle = lazy(() => import('./pages/client/FacturaPospagoDetalle'))
const ResultadoPagoFactura = lazy(() => import('./pages/client/ResultadoPagoFactura'))

// Portal admin
const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'))
const UsuariosInternos = lazy(() => import('./pages/admin/UsuariosInternos'))
const Clientes = lazy(() => import('./pages/admin/Clientes'))
const Productividad = lazy(() => import('./pages/shared/Productividad'))
const SemaforoServicios = lazy(() => import('./pages/admin/SemaforoServicios'))
const Catalogo = lazy(() => import('./pages/admin/catalogo/Catalogo'))
const PlantillaFormulario = lazy(() => import('./pages/admin/catalogo/PlantillaFormulario'))
const TiposNovedad = lazy(() => import('./pages/admin/catalogo/TiposNovedad'))
const HojaVidaCandidato = lazy(() => import('./pages/admin/catalogo/HojaVidaCandidato'))
const TemasPoligrafia = lazy(() => import('./pages/admin/catalogo/TemasPoligrafia'))
const MotivosSubproceso = lazy(() => import('./pages/admin/catalogo/MotivosSubproceso'))
const ParametrosRiesgo = lazy(() => import('./pages/admin/riesgo/ParametrosRiesgo'))
const CentralesRiesgo = lazy(() => import('./pages/admin/riesgo/CentralesRiesgo'))
const CatalogoHallazgos = lazy(() => import('./pages/admin/riesgo/CatalogoHallazgos'))
const TrabajosSegundoPlano = lazy(() => import('./pages/admin/TrabajosSegundoPlano'))
const AdminReversiones = lazy(() => import('./pages/admin/AdminReversiones'))
const Descuentos = lazy(() => import('./pages/admin/Descuentos'))
const Ciudades = lazy(() => import('./pages/admin/Ciudades'))
const Festivos = lazy(() => import('./pages/admin/Festivos'))
const ParametrosTiempo = lazy(() => import('./pages/admin/ParametrosTiempo'))
const Departamentos = lazy(() => import('./pages/admin/Departamentos'))
const Facturacion = lazy(() => import('./pages/admin/Facturacion'))
const GestionCuentas = lazy(() => import('./pages/admin/GestionCuentas'))

// Portales de rol
const GestorDashboard = lazy(() => import('./pages/gestor/GestorDashboard'))
const GestorSolicitudes = lazy(() => import('./pages/gestor/Solicitudes'))
const GestorReversiones = lazy(() => import('./pages/gestor/GestorReversiones'))
const GestorAgenda = lazy(() => import('./pages/gestor/Agenda'))
const GestorClienteDetalle = lazy(() => import('./pages/gestor/ClienteDetalle'))
const ProgramadorDashboard = lazy(() => import('./pages/programador/ProgramadorDashboard'))
const ProgramadorAgenda = lazy(() => import('./pages/programador/Agenda'))
// Compartidas entre analista/poligrafista/visitador — mismo backend filtra por rol autenticado
const AsignacionDashboard = lazy(() => import('./pages/shared/AsignacionDashboard'))
const MisAsignaciones = lazy(() => import('./pages/shared/MisAsignaciones'))
const MiPerfil = lazy(() => import('./pages/shared/MiPerfil'))
const MiPerfilAnalista = lazy(() => import('./pages/client/MiPerfilAnalista'))
const Formulario = lazy(() => import('./pages/shared/Formulario'))
const FormularioPoligrafista = lazy(() => import('./pages/poligrafista/FormatoPoligrafia'))
const FormularioVisitador = lazy(() => import('./pages/visitador/FormatoVisita'))
const FormularioAnalista = lazy(() => import('./pages/analista/FormatoAntecedentes'))
const ProveedorExterno = lazy(() => import('./pages/proveedor/ProveedorExterno'))
const CandidatoPerfil = lazy(() => import('./pages/shared/CandidatoPerfil'))

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

// PC-06 (PB-H-41): 2 minutos antes de cerrar la sesión por inactividad sale este aviso, con «Seguir conectado» (un botón de verdad, que además es donde cae el foco) y el
// tiempo que queda. Las cifras del reloj se esconden del lector de pantalla (cambian cada segundo); él oye una sola vez «en 2 minutos».
function InactivityWatcher() {
  const { mostrarAlerta, segundosRestantes, seguirConectado, cerrarSesion } = useInactivityLogout()
  if (!mostrarAlerta) return null
  const mm = Math.floor(segundosRestantes / 60)
  const ss = String(segundosRestantes % 60).padStart(2, '0')
  return (
    <ConfirmModal
      titulo="¿Sigues ahí?"
      rol="alertdialog"
      enfocarConfirmar
      confirmLabel="Seguir conectado"
      cancelLabel="Cerrar sesión ahora"
      onConfirmar={seguirConectado}
      onCancelar={cerrarSesion}
      bloquearFondo
    >
      <p className="text-sm text-gray-700">
        Tu sesión se cerrará en <strong aria-hidden="true" className="tabular-nums">{mm}:{ss}</strong><span className="sr-only">2 minutos</span> por inactividad.
        Lo que escribiste en los formatos y en las ventanas Publicar, Emitir y Devolver se conserva, y al volver a entrar regresas a esta misma pantalla.
      </p>
    </ConfirmModal>
  )
}

function App() {
  useEffect(() => { prepararAvisos() }, [])   // las regiones vivas de los avisos existen (vacías) antes del primer aviso
  return (
    <AuthProvider>
      <a href="#contenido" className="saltar-contenido">Saltar al contenido</a>
      <InactivityWatcher />
      <OfflineIndicator />
      <PWAInstallBanner />
      <BannerCookies />
      <Suspense fallback={<Cargando pantallaCompleta />}>
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
      </Suspense>
    </AuthProvider>
  )
}

export default App

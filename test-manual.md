# Plan de Pruebas Manual — Polygraph Service ERP
> Fecha: 2026-06-28 | Branch: `feature/prompt-4-catalogo-servicios`

---

## PARTE 1 — INVENTARIO COMPLETO DE FUNCIONALIDADES

### BACKEND

#### Módulo `auth` — Autenticación y Seguridad
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/v1/auth/login` | Login con email/password; devuelve JWT + refresh token |
| POST | `/api/v1/auth/register/natural` | Registro persona natural (cliente externo) |
| POST | `/api/v1/auth/register/juridica` | Registro persona jurídica (cliente externo) |
| POST | `/api/v1/auth/activate?token=` | Activación de cuenta vía token email |
| POST | `/api/v1/auth/refresh` | Renovar JWT con refresh token |
| POST | `/api/v1/auth/forgot-password` | Solicitar reset de contraseña por email |
| POST | `/api/v1/auth/reset-password` | Establecer nueva contraseña con token |
| POST | `/api/v1/auth/cambiar-password` | Cambiar contraseña (usuario autenticado) |

**Roles soportados:** `ADMIN_POLYGRAPH`, `GESTOR`, `ANALISTA_INTERNO`, `PROGRAMADOR`, `POLIGRAFISTA`, `VISITADOR`, `ADMIN_CLIENTE`, `ANALISTA_CLIENTE`, `EVALUADO`

**Seguridad:** JWT (JJWT 0.12.6), BCrypt, refresh tokens en BD, activación por email SMTP.

---

#### Módulo `servicios/catalogo` — Catálogo de Servicios
| Método | Ruta | Roles | Descripción |
|--------|------|-------|-------------|
| GET | `/api/v1/services` | Público (autenticado) | Lista todos los servicios activos |
| GET | `/api/v1/services/{id}` | Público (autenticado) | Obtiene un servicio por ID |
| POST | `/api/v1/services` | ADMIN_POLYGRAPH | Crea nuevo servicio en catálogo |
| PUT | `/api/v1/services/{id}` | ADMIN_POLYGRAPH | Actualiza un servicio |
| DELETE | `/api/v1/services/{id}` | ADMIN_POLYGRAPH | Desactiva (soft-delete) un servicio |

**17 servicios precargados** por `DataInitializer` en 4 categorías:
- `PRUEBAS_CONFIABILIDAD`: Polígrafo Pre-empleo, Polígrafo Rutina, Polígrafo Específico, Verifeye
- `ESTUDIOS_SEGURIDAD`: Estudio Básico, Avanzado, Quick (1 día), OEA, Combo Básico+Visita, Combo Avanzado+Polígrafo, Paquete Completo
- `VISITAS_DOMICILIARIAS`: Visita Estándar, Visita Express, Visita Rural
- `VALIDACION_HOJA_VIDA`: Validación Básica HV, Validación Completa HV, Validación Express HV

---

#### Módulo `solicitudes` — Solicitudes de Servicio
| Método | Ruta | Roles | Descripción |
|--------|------|-------|-------------|
| POST | `/api/v1/requests` | ADMIN_CLIENTE, ANALISTA_CLIENTE, GESTOR, ADMIN_POLYGRAPH | Crear nueva solicitud individual |
| GET | `/api/v1/requests` | ADMIN_CLIENTE, ANALISTA_CLIENTE, GESTOR, ADMIN_POLYGRAPH, ANALISTA_INTERNO | Listar solicitudes (paginado, filtro por estado) |
| GET | `/api/v1/requests/{id}` | Mismos roles | Obtener detalle de una solicitud |
| PATCH | `/api/v1/requests/{id}/status` | Mismos roles | Cambiar estado del semáforo |
| POST | `/api/v1/requests/bulk` | ADMIN_CLIENTE, GESTOR, ADMIN_POLYGRAPH | Carga masiva desde archivo Excel |
| GET | `/api/v1/requests/bulk/template` | Todos | Descargar plantilla Excel para carga masiva |

**Reglas de negocio implementadas:**
- Validación duplicados: mismo candidato + mismo cliente en los últimos 3 meses
- Regla 4pm: solicitudes después de las 4pm cuentan como día hábil siguiente
- Sábados: día de entrega, no de elaboración
- Cálculo automático `fechaEntregaEstimada` via `DiasHabilesService`
- Notificación automática a gestores al crear solicitud

---

#### Módulo `evaluados` — Formulario Evaluado (Público)
| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/api/v1/evaluees/link/{token}/validate` | No requerida | Valida si el link es válido y no expiró (36h) |
| GET | `/api/v1/evaluees/link/{token}` | No requerida | Obtiene datos pre-cargados del formulario |
| POST | `/api/v1/evaluees/link/{token}/progress` | No requerida | Guarda progreso parcial (auto-save) |
| POST | `/api/v1/evaluees/link/{token}/submit` | No requerida | Envía formulario completo (final) |

**6 secciones de la hoja de vida:**
datos personales → educación → experiencia laboral → inactividades → referencias personales → documentos

**Cálculo automático:** `HistorialLaboralService` detecta tiempos muertos >30 días en últimos 4 años y los marca con `requiere_cuestionario=true`.

---

#### Módulo `gestor` — Portal Gestor
| Método | Ruta | Rol | Descripción |
|--------|------|-----|-------------|
| GET | `/api/v1/gestor/dashboard` | GESTOR | KPIs por estado + 8 solicitudes recientes |
| GET | `/api/v1/gestor/solicitudes` | GESTOR | Bandeja de solicitudes paginada (filtro estado, page 20) |
| GET | `/api/v1/gestor/solicitudes/{id}` | GESTOR | Detalle de solicitud |
| PATCH | `/api/v1/gestor/solicitudes/{id}/estado` | GESTOR | Cambiar estado del semáforo |

---

#### Módulo `dashboard/admin` — Dashboard Admin Polygraph
| Método | Ruta | Rol | Descripción |
|--------|------|-----|-------------|
| GET | `/api/v1/admin/dashboard` | ADMIN_POLYGRAPH | KPIs globales del sistema |
| GET | `/api/v1/admin/dashboard/servicios` | ADMIN_POLYGRAPH | Servicios agrupados por estado (filtro por cliente) |

---

#### Módulo `dashboard/cliente` — Dashboard Cliente
| Método | Ruta | Roles | Descripción |
|--------|------|-------|-------------|
| GET | `/api/v1/client/dashboard` | ADMIN_CLIENTE, ANALISTA_CLIENTE | KPIs del cliente (activas, en proceso, finalizadas, etc.) |
| GET | `/api/v1/client/service-packages` | ADMIN_CLIENTE, ANALISTA_CLIENTE | Bolsa de servicios prepago (stub — devuelve lista vacía) |
| GET | `/api/v1/client/requests` | ADMIN_CLIENTE, ANALISTA_CLIENTE | Solicitudes paginadas del cliente |

---

#### Módulo `clientes` — Gestión de Clientes (Admin)
| Método | Ruta | Rol | Descripción |
|--------|------|-----|-------------|
| GET | `/api/v1/clientes` | ADMIN_POLYGRAPH | Lista todos los clientes |
| GET | `/api/v1/clientes/{id}` | ADMIN_POLYGRAPH | Obtiene un cliente por ID |
| PUT | `/api/v1/clientes/{id}` | ADMIN_POLYGRAPH | Actualiza datos del cliente |
| PATCH | `/api/v1/clientes/{id}/activar` | ADMIN_POLYGRAPH | Activa un cliente |
| PATCH | `/api/v1/clientes/{id}/desactivar` | ADMIN_POLYGRAPH | Desactiva un cliente |

---

#### Módulo `usuarios-internos` — Gestión de Usuarios Internos
| Método | Ruta | Rol | Descripción |
|--------|------|-----|-------------|
| GET | `/api/v1/usuarios-internos` | ADMIN_POLYGRAPH | Lista usuarios internos (Polygraph) |
| GET | `/api/v1/usuarios-internos/clientes` | ADMIN_POLYGRAPH | Lista usuarios de clientes |
| POST | `/api/v1/usuarios-internos` | ADMIN_POLYGRAPH | Crea usuario interno |
| PUT | `/api/v1/usuarios-internos/{id}` | ADMIN_POLYGRAPH | Actualiza usuario interno |
| PATCH | `/api/v1/usuarios-internos/{id}/activar` | ADMIN_POLYGRAPH | Activa usuario interno |
| PATCH | `/api/v1/usuarios-internos/{id}/desactivar` | ADMIN_POLYGRAPH | Desactiva usuario interno |

---

#### Módulo `notificaciones`
| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| GET | `/api/v1/notificaciones` | Autenticado | Lista notificaciones del usuario |
| GET | `/api/v1/notificaciones/no-leidas` | Autenticado | Cuenta notificaciones no leídas |
| PATCH | `/api/v1/notificaciones/leer-todas` | Autenticado | Marca todas como leídas |
| PATCH | `/api/v1/notificaciones/{id}/leer` | Autenticado | Marca una como leída |
| DELETE | `/api/v1/notificaciones/{id}` | Autenticado | Elimina una notificación |

---

#### Módulo `catalogo/procesos` — Procesos Operativos (Admin)
| Método | Ruta | Rol | Descripción |
|--------|------|-----|-------------|
| GET | `/api/v1/catalogo/clasificaciones` | ADMIN_POLYGRAPH | Lista clasificaciones de procesos |
| POST | `/api/v1/catalogo/clasificaciones` | ADMIN_POLYGRAPH | Crea clasificación |
| PUT | `/api/v1/catalogo/clasificaciones/{id}` | ADMIN_POLYGRAPH | Actualiza clasificación |
| PATCH | `/api/v1/catalogo/clasificaciones/{id}/activar` | ADMIN_POLYGRAPH | Activa/desactiva clasificación |
| GET | `/api/v1/catalogo/procesos` | ADMIN_POLYGRAPH | Lista procesos |
| POST | `/api/v1/catalogo/procesos` | ADMIN_POLYGRAPH | Crea proceso |
| PUT | `/api/v1/catalogo/procesos/{id}` | ADMIN_POLYGRAPH | Actualiza proceso |
| GET | `/api/v1/catalogo/procesos/{id}/pasos` | ADMIN_POLYGRAPH | Obtiene pasos de un proceso |
| POST | `/api/v1/catalogo/procesos/{id}/pasos` | ADMIN_POLYGRAPH | Asigna un paso a proceso |
| PUT | `/api/v1/catalogo/procesos/{id}/pasos/{pasoId}` | ADMIN_POLYGRAPH | Actualiza un paso |
| DELETE | `/api/v1/catalogo/procesos/{id}/pasos/{pasoId}` | ADMIN_POLYGRAPH | Elimina un paso |
| GET | `/api/v1/catalogo/tipos-progreso` | ADMIN_POLYGRAPH | Lista tipos de progreso |
| POST | `/api/v1/catalogo/tipos-progreso` | ADMIN_POLYGRAPH | Crea tipo de progreso |
| PUT | `/api/v1/catalogo/tipos-progreso/{id}` | ADMIN_POLYGRAPH | Actualiza tipo de progreso |
| GET | `/api/v1/catalogo/tipos-progreso/{id}/impacto` | ADMIN_POLYGRAPH | Calcula impacto de un tipo de progreso |

---

### FRONTEND

#### Páginas Públicas
| Ruta | Página | Funcionalidad | Componentes clave |
|------|--------|---------------|-------------------|
| `/login` | `Login.jsx` | Formulario email/password, JWT almacenado en contexto | `AuthContext` |
| `/registro` | `Register.jsx` | Registro persona natural o jurídica, validaciones | `AuthContext` |
| `/cambiar-password` | `CambiarPassword.jsx` | Cambio de contraseña para usuario autenticado | `ProtectedRoute` |
| `/evaluado/link/:token` | `EvalueeForm.jsx` | Formulario hoja de vida evaluado, 6 pasos, auto-save 2min | `WorkTimeline`, `ServiceStatusBadge` |
| `/evaluado/completar/:token` | `EvalueeForm.jsx` | Misma página (ruta alternativa) | — |
| `/no-autorizado` | inline | Página de acceso denegado con link a inicio | — |

#### Portal Cliente (`/cliente/*`) — Roles: ADMIN_CLIENTE, ANALISTA_CLIENTE
| Ruta | Página | Funcionalidad | Componentes clave |
|------|--------|---------------|-------------------|
| `/cliente/dashboard` | `Dashboard.jsx` | KPIs: activas/en proceso/finalizadas/vencidas, últimas 5 solicitudes, indicador bolsa prepago | `ServiceStatusBadge`, `ClientLayout` |
| `/cliente/catalogo` | `ServiceCatalog.jsx` | Catálogo de 17 servicios agrupados por categoría, cards con precio y días entrega | `ClientLayout` |
| `/cliente/nueva-solicitud` | `NewRequest.jsx` | Wizard 3 pasos: (1) datos evaluado, (2) selección servicios, (3) confirmación | `ClientLayout` |
| `/cliente/carga-masiva` | `BulkUpload.jsx` | Drag & drop Excel, previsualización de filas, reporte de errores por fila | `ClientLayout` |
| `/cliente/solicitudes` | `Solicitudes.jsx` | Tabla 8 cols, filtro por estado, paginación 10/pág, panel detalle lateral | `ServiceStatusBadge`, `ClientLayout` |
| `/cliente/solicitudes/:id` | `SolicitudDetalle.jsx` | Detalle completo de una solicitud con historial de estados | `ServiceStatusBadge` |
| `/cliente/comprar-servicios` | Proximamente | Placeholder — bolsa prepago (no implementado) | — |
| `/cliente/documentos` | Proximamente | Placeholder — documentos empresa (no implementado) | — |
| `/cliente/estadisticas` | Proximamente | Placeholder — estadísticas (no implementado) | — |

#### Portal Admin (`/admin/*`) — Rol: ADMIN_POLYGRAPH
| Ruta | Página | Funcionalidad |
|------|--------|---------------|
| `/admin/dashboard` | `AdminDashboard.jsx` | KPIs globales + servicios por estado |
| `/admin/usuarios-internos` | `UsuariosInternos.jsx` | CRUD usuarios internos (Gestor, Analista, Programador, Poligrafista, Visitador) |
| `/admin/clientes` | `Clientes.jsx` | Lista y gestión de clientes registrados |
| `/admin/semaforo` | `SemaforoServicios.jsx` | Vista semáforo de todos los servicios activos |
| `/admin/catalogo` | `Catalogo.jsx` | Grid por clasificación de procesos |
| `/admin/catalogo/:clasificacion` | `Catalogo.jsx` (detalle) | Procesos y pasos de una clasificación específica |

#### Portales de Rol Interno
| Ruta | Página | Estado |
|------|--------|--------|
| `/gestor/dashboard` | `GestorDashboard.jsx` | Implementado — KPIs + resumen solicitudes recientes |
| `/gestor/solicitudes` | `gestor/Solicitudes.jsx` | Implementado — bandeja completa con filtros |
| `/analista/dashboard` | `AnalistaDashboard.jsx` | Placeholder |
| `/programador/dashboard` | `ProgramadorDashboard.jsx` | Placeholder |
| `/poligrafista/dashboard` | `PoligrafistaDashboard.jsx` | Placeholder |
| `/visitador/dashboard` | `VisitadorDashboard.jsx` | Placeholder |

#### Componentes Compartidos
| Componente | Descripción |
|------------|-------------|
| `ServiceStatusBadge.jsx` | Badge coloreado para los 7 estados del semáforo (acepta `status` o `estado`) |
| `WorkTimeline.jsx` | Línea de tiempo de historial laboral con alertas de inactividad >30 días |
| `Campana.jsx` | Ícono de notificaciones con contador de no leídas |
| `ProtectedRoute.jsx` | Guardia de rutas por rol |
| `OfflineIndicator.jsx` | Banner cuando no hay conexión a internet |
| `PWAInstallBanner.jsx` | Prompt de instalación PWA |
| `Toast.jsx` | Notificaciones toast del sistema |

---

## PARTE 2 — PLAN DE PRUEBAS MANUAL

### 1. Módulo Autenticación

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| A-01 | Login exitoso (admin) | POST `/api/v1/auth/login` con `{"email":"admin@polygraph.com","password":"Admin1234!"}` | 200 OK con `accessToken`, `refreshToken`, `rol: ADMIN_POLYGRAPH` | ☐ |
| A-02 | Login fallido (password incorrecta) | POST `/api/v1/auth/login` con password errada | 401 Unauthorized con mensaje de error | ☐ |
| A-03 | Login fallido (usuario inactivo) | POST login con usuario con `activo=false` | 401 con mensaje "cuenta inactiva" | ☐ |
| A-04 | Registro persona natural | POST `/api/v1/auth/register/natural` con datos válidos | 201 Created; email de activación enviado | ☐ |
| A-05 | Registro persona jurídica | POST `/api/v1/auth/register/juridica` con NIT + razón social | 201 Created; email de activación enviado | ☐ |
| A-06 | Registro duplicado (mismo email) | POST register con email ya existente | 409 Conflict | ☐ |
| A-07 | Activación de cuenta | POST `/api/v1/auth/activate?token=<token_email>` | 200 OK; usuario queda `activo=true` | ☐ |
| A-08 | Activación con token expirado | POST activate con token viejo | 400 o 422 con error descriptivo | ☐ |
| A-09 | Refresh token | POST `/api/v1/auth/refresh` con refresh token válido | 200 OK con nuevo `accessToken` | ☐ |
| A-10 | Refresh token inválido | POST refresh con token manipulado | 401 Unauthorized | ☐ |
| A-11 | Forgot password | POST `/api/v1/auth/forgot-password` con email registrado | 200 OK; email con link de reset enviado | ☐ |
| A-12 | Reset password | POST `/api/v1/auth/reset-password` con token válido y nueva password | 200 OK; login exitoso con nueva password | ☐ |
| A-13 | Cambiar password (autenticado) | POST `/api/v1/auth/cambiar-password` con JWT válido y body `{passwordActual, passwordNueva}` | 200 OK; login con nueva password funciona | ☐ |
| A-14 | Cambiar password sin autenticación | POST cambiar-password sin JWT | 401 Unauthorized | ☐ |
| A-15 | Redireccionamiento por rol | Login frontend con cada rol; observar URL destino | ADMIN_POLYGRAPH→`/admin/dashboard`, GESTOR→`/gestor/dashboard`, ADMIN_CLIENTE→`/cliente/dashboard` | ☐ |

---

### 2. Módulo Cliente — Dashboard

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| C-01 | Dashboard cliente — KPIs | Login ADMIN_CLIENTE; ir a `/cliente/dashboard` | Tarjetas con conteos de solicitudes por estado, indicador bolsa prepago | ☐ |
| C-02 | Últimas solicitudes en dashboard | Dashboard con al menos 5 solicitudes creadas | Lista con hasta 5 solicitudes más recientes con badge de estado coloreado | ☐ |
| C-03 | Catálogo de servicios | Ir a `/cliente/catalogo` | 17 servicios agrupados por 4 categorías; cada card muestra nombre, descripción, días entrega | ☐ |
| C-04 | Acceso ANALISTA_CLIENTE | Login como ANALISTA_CLIENTE | Solo ve Dashboard, Solicitudes y Catálogo (sin "Nueva Solicitud" si no tiene permiso) | ☐ |

---

### 3. Módulo Cliente — Solicitudes

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| S-01 | Crear solicitud individual | POST `/api/v1/requests` con datos de evaluado y lista de IDs de servicios | 201 Created con `SolicitudDetalleResponse`; fechaEntregaEstimada calculada | ☐ |
| S-02 | Regla 4pm | Crear solicitud después de las 4:00pm | `fechaEntregaEstimada` se calcula desde el siguiente día hábil | ☐ |
| S-03 | Regla sábado | Crear solicitud un sábado | El sábado cuenta como día de entrega pero no de elaboración | ☐ |
| S-04 | Duplicado 3 meses | Crear 2 solicitudes con misma cédula y mismo cliente en < 3 meses | 409 Conflict con mensaje de duplicado | ☐ |
| S-05 | Listar solicitudes paginadas | GET `/api/v1/requests?pagina=0&tamano=10` | 200 con Page<SolicitudResponse>, total items correcto | ☐ |
| S-06 | Filtrar por estado | GET `/api/v1/requests?estado=PENDIENTE` | Solo solicitudes PENDIENTE | ☐ |
| S-07 | Detalle de solicitud | GET `/api/v1/requests/{id}` | `SolicitudDetalleResponse` con servicios, historial de estados, token link evaluado | ☐ |
| S-08 | Cambiar estado | PATCH `/api/v1/requests/{id}/status` con `{"estadoNuevo":"PROGRAMANDO","observacion":"OK"}` | 200 OK; historial actualizado; badge cambia de color | ☐ |
| S-09 | Listar solicitudes — vista frontend | `/cliente/solicitudes` | Tabla 8 columnas, filtro estado, paginación 10/pág | ☐ |
| S-10 | Panel detalle lateral | Clic en fila de solicitud en `/cliente/solicitudes` | Panel lateral deslizable con detalle completo y badge de estado | ☐ |
| S-11 | Detalle individual | Navegar a `/cliente/solicitudes/{id}` | Página de detalle con todos los datos de la solicitud | ☐ |

---

### 4. Módulo Cliente — Carga Masiva

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| BU-01 | Descargar plantilla | GET `/api/v1/requests/bulk/template` | Descarga `plantilla-carga-masiva.xlsx` con 9 columnas encabezado + 1 fila ejemplo | ☐ |
| BU-02 | Carga masiva exitosa | POST `/api/v1/requests/bulk` con Excel válido (multipart) | `BulkUploadResponse` con lista de solicitudes creadas y conteo de errores | ☐ |
| BU-03 | Carga masiva con errores | Excel con filas inválidas (cédulas duplicadas, campos vacíos) | Respuesta lista ítems OK + lista de errores por fila con descripción | ☐ |
| BU-04 | Carga masiva frontend | `/cliente/carga-masiva`; drag & drop archivo Excel | Previsualización de filas, botón "Subir", reporte de errores en UI | ☐ |
| BU-05 | Nueva Solicitud wizard | `/cliente/nueva-solicitud` — completar los 3 pasos | Paso 1: datos evaluado; Paso 2: selección servicios del catálogo; Paso 3: confirmación y envío | ☐ |

---

### 5. Módulo Evaluado

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| E-01 | Validar link (válido) | POST `/api/v1/evaluees/link/{token}/validate` con token activo | 200 OK con `{valido:true, nombreEvaluado, servicios[]}` | ☐ |
| E-02 | Validar link (expirado 36h) | POST validate con token creado hace > 36 horas | 200 con `{valido:false}` o 422 | ☐ |
| E-03 | Validar link (ya usado) | POST validate con token marcado `usado=true` | Respuesta indica link ya completado | ☐ |
| E-04 | Obtener formulario pre-cargado | GET `/api/v1/evaluees/link/{token}` | Devuelve `HojaVidaRequest` con datos del candidato si existen | ☐ |
| E-05 | Guardar progreso parcial | POST `/api/v1/evaluees/link/{token}/progress` con datos parciales | 200 OK; BD actualiza `progreso_porcentaje` | ☐ |
| E-06 | Enviar formulario completo | POST `/api/v1/evaluees/link/{token}/submit` con todos los campos requeridos | 200 OK; `completado=true`; inactividades calculadas | ☐ |
| E-07 | Inactividad > 30 días detectada | Enviar HV con gaps laborales > 30 días en los últimos 4 años | `inactividad_laboral_evaluado` registra períodos con `requiere_cuestionario=true` | ☐ |
| E-08 | Formulario frontend 6 pasos | Abrir `/evaluado/link/{token}` | Progreso visual 6 pasos, auto-save cada 2 minutos, validaciones por paso | ☐ |
| E-09 | Auto-save | Llenar parcialmente y esperar 2 minutos | POST a `/progress` ejecutado automáticamente; datos persisten al recargar | ☐ |

---

### 6. Módulo Gestor

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| G-01 | Dashboard gestor | Login GESTOR; GET `/api/v1/gestor/dashboard` | JSON con conteos por estado (pendientes, programando, en_ejecucion, finalizados, publicados, cancelados) + 8 solicitudes recientes | ☐ |
| G-02 | Dashboard frontend | `/gestor/dashboard` | KPIs visuales + lista de solicitudes recientes con nombre cliente resuelto | ☐ |
| G-03 | Bandeja de solicitudes | GET `/api/v1/gestor/solicitudes?page=0&size=20` | Page<SolicitudResponse> con `ciudadEvaluado` incluido (bug corregido hoy) | ☐ |
| G-04 | Filtrar por estado (gestor) | GET `/api/v1/gestor/solicitudes?estado=PENDIENTE` | Solo solicitudes PENDIENTE | ☐ |
| G-05 | Ver detalle (gestor) | GET `/api/v1/gestor/solicitudes/{id}` | Detalle completo con historial | ☐ |
| G-06 | Cambiar estado (gestor) | PATCH `/api/v1/gestor/solicitudes/{id}/estado` con `{estadoNuevo:"EN_EJECUCION"}` | 204 No Content; historial registra cambio | ☐ |
| G-07 | Restricción de rol | Acceder a `/api/v1/gestor/*` con token de ADMIN_CLIENTE | 403 Forbidden | ☐ |

---

### 7. Módulo Administrador (ADMIN_POLYGRAPH)

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| AD-01 | Dashboard admin | GET `/api/v1/admin/dashboard` | Conteos globales de todas las solicitudes | ☐ |
| AD-02 | Servicios por estado | GET `/api/v1/admin/dashboard/servicios?idCliente=1` | Desglose por estado para ese cliente | ☐ |
| AD-03 | Listar clientes | GET `/api/v1/clientes` | Lista completa de clientes con tipo, estado, mora | ☐ |
| AD-04 | Activar/desactivar cliente | PATCH `/api/v1/clientes/{id}/activar` | 200 OK; cliente queda `estado=ACTIVO` | ☐ |
| AD-05 | Crear usuario interno | POST `/api/v1/usuarios-internos` con rol GESTOR | 201 Created; usuario creado con password temporal | ☐ |
| AD-06 | Activar/desactivar usuario interno | PATCH `.../activar` | 200 OK; usuario puede o no puede hacer login | ☐ |
| AD-07 | Crear servicio catálogo | POST `/api/v1/services` con datos nuevos | 201 Created; aparece en listado | ☐ |
| AD-08 | Desactivar servicio | DELETE `/api/v1/services/{id}` | 204 No Content; servicio no aparece en GET /services | ☐ |
| AD-09 | Semáforo admin frontend | `/admin/semaforo` | Vista de todos los servicios activos por color de estado | ☐ |
| AD-10 | Gestión clientes frontend | `/admin/clientes` | Tabla con clientes, opciones activar/desactivar | ☐ |
| AD-11 | Usuarios internos frontend | `/admin/usuarios-internos` | CRUD completo de usuarios internos | ☐ |

---

### 8. Catálogo de Servicios (DataInitializer)

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| CS-01 | Catálogo precargado | GET `/api/v1/services` en BD limpia (primer arranque) | 17 servicios activos en 4 categorías | ☐ |
| CS-02 | Estudio Quick — días entrega | GET `/api/v1/services` | "Estudio Quick" tiene `diasHabilesEntrega=1`, los demás tienen 5 | ☐ |
| CS-03 | Catálogo idempotente | Reiniciar servidor con catálogo ya cargado | No se duplican servicios; count permanece en 17 | ☐ |

---

### 9. Semáforo de Estados

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| SM-01 | Color PENDIENTE | Ver badge en solicitud con estado PENDIENTE | Badge gris | ☐ |
| SM-02 | Color PROGRAMANDO | Cambiar estado a PROGRAMANDO | Badge amarillo | ☐ |
| SM-03 | Color EN_EJECUCION | Cambiar estado a EN_EJECUCION | Badge azul | ☐ |
| SM-04 | Color FINALIZADO | Cambiar estado a FINALIZADO | Badge naranja | ☐ |
| SM-05 | Color PUBLICADO | Cambiar estado a PUBLICADO | Badge verde | ☐ |
| SM-06 | Color CANCELADO | Cambiar estado a CANCELADO | Badge rojo | ☐ |
| SM-07 | Color REPROGRAMADO | Cambiar estado a REPROGRAMADO | Badge morado | ☐ |
| SM-08 | Historial registrado | Hacer 3 cambios de estado en una solicitud | `historial_solicitudes` tiene 3 filas con usuario + fecha + observación | ☐ |

---

### 10. Notificaciones

| # | Funcionalidad | Pasos para probar | Resultado esperado | Estado |
|---|---------------|-------------------|--------------------|--------|
| N-01 | Notificación al crear solicitud | Crear solicitud como cliente | Gestores reciben notificación automática en BD | ☐ |
| N-02 | Listar notificaciones | GET `/api/v1/notificaciones` con JWT de gestor | Lista con la notificación creada | ☐ |
| N-03 | Contador no leídas | GET `/api/v1/notificaciones/no-leidas` | `{total: N}` con el conteo correcto | ☐ |
| N-04 | Marcar como leída | PATCH `/api/v1/notificaciones/{id}/leer` | 204; notificación queda `leida=true` | ☐ |
| N-05 | Marcar todas leídas | PATCH `/api/v1/notificaciones/leer-todas` | 204; contador pasa a 0 | ☐ |

---

## PARTE 3 — CONFIGURACIÓN DEL ENTORNO DE PRUEBAS

### Prerrequisitos
```
- PostgreSQL 15 corriendo en localhost:5432
- Base de datos: erp_polygraph (creada y con migraciones V1-V13 ejecutadas)
- Backend Spring Boot corriendo en localhost:8080
- Frontend Vite corriendo en localhost:5173
```

### Credenciales de prueba (tras ejecutar V99__datos_prueba.sql)
```
ADMIN_POLYGRAPH : admin@polygraph.com       / Admin1234!
GESTOR          : gestor@polygraph.com      / Gestor1234!
ADMIN_CLIENTE   : cliente1@empresa-abc.com  / Cliente1234!
ANALISTA_CLIENTE: analista@empresa-abc.com  / Analista1234!
```

### Herramientas recomendadas
- **API:** Postman o Bruno (colección en `/docs/postman` — por crear)
- **BD:** DBeaver o pgAdmin conectado a localhost:5432/erp_polygraph
- **Frontend:** Chrome DevTools (Network tab para verificar peticiones API)

---

## Leyenda
- ☐ = Sin probar
- ✅ = Pasó
- ❌ = Falló (anotar bug en issue tracker)
- ⚠️ = Pasó parcialmente (comportamiento inesperado pero no bloqueante)

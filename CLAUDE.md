# POLYGRAPH SERVICE ERP — Contexto del Proyecto

## ¿Qué es este proyecto?

Plataforma SaaS multirol para Polygraph Service Ltda, empresa colombiana
con 15 años de experiencia en estudios de confiabilidad del talento humano.
Gestiona: poligrafías, visitas domiciliarias, estudios de seguridad y
validaciones de hoja de vida.

## OBJETIVO DEL PROYECTO:

Desarrollar una plataforma web saas tipo ERP especializado en estudios de seguridad, poligrafías y visitas domiciliarias, que permita gestión de clientes, solicitud de servicios, asignación operativa, seguimiento en tiempo real (Semáforo de Servicio), generación de informes, control de tiempos, facturación y estadísticas.

## Stack tecnológico

- Backend: Spring Boot 3.3 + Java 17 + Maven
- Frontend: React 18 + Vite + TailwindCSS
- Base de datos: PostgreSQL 15
- ORM: Spring Data JPA + Hibernate
- Auth: Spring Security + JWT + 2FA (admin)
- Contenedores: Docker + Docker Compose
- Email: JavaMailSender (SMTP)
- Pagos: Wompi (Web Checkout/Widget) — implementado, ver sección "Módulo de Pagos (Wompi)"
- Facturación: API Factus (DIAN Colombia) — fase 3, módulo aún no implementado
  (solo existe el paquete `modules/facturacion/` vacío)

## Estructura de carpetas

El repo NO usa una carpeta wrapper `erp-polygraph/`: `backend/` y `frontend/`
están en la raíz del repo.

Polygraph/
├── backend/
│ └── src/main/java/com/polygraph/erp/
│ ├── modules/
│ │ ├── auth/
│ │ ├── clientes/ (incluye soporte y perfil/admin de clientes)
│ │ ├── evaluados/
│ │ ├── servicios/
│ │ ├── solicitudes/
│ │ ├── catalogo/ (ciudades, procesos, clasificaciones, tipos de progreso)
│ │ ├── gestor/ (asignación, links de candidato)
│ │ ├── analista/
│ │ ├── programador/
│ │ ├── facturacion/
│ │ ├── dashboard/ (dashboard cliente y dashboard admin)
│ │ ├── enlaces/ (enlaces externos)
│ │ ├── mensajes/ (mensajería cliente-gestor)
│ │ ├── notificaciones/
│ │ ├── pagos/ (módulo Wompi: órdenes, pagos, webhook, auditoría, saldo)
│ │ └── usuarios/ (usuarios internos)
│ ├── security/
│ ├── config/
│ ├── shared/
│ │ ├── exceptions/
│ │ ├── dto/
│ │ └── utils/
│ └── ErpApplication.java
└── frontend/
└── src/
├── modules/ (auth, clientes, dashboard, evaluados, servicios, solicitudes)
├── components/
├── pages/
│ ├── admin/
│ ├── analista/
│ ├── client/
│ ├── evaluee/
│ ├── gestor/
│ ├── poligrafista/
│ ├── programador/
│ └── visitador/
├── hooks/
└── services/

## Roles del sistema

| Rol              | Tipo             | Descripción                            |
| ---------------- | ---------------- | -------------------------------------- |
| ADMIN_POLYGRAPH  | Interno          | Control total del sistema              |
| GESTOR           | Interno          | Gestiona clientes y asigna trabajo     |
| ANALISTA_INTERNO | Interno          | Realiza validaciones y genera informes |
| PROGRAMADOR      | Interno          | Agenda poligrafías y visitas           |
| POLIGRAFISTA     | Interno/Externo  | Ejecuta prueba de polígrafo            |
| VISITADOR        | Interno/Externo  | Ejecuta visita domiciliaria            |
| ADMIN_CLIENTE    | Externo          | Compra y solicita servicios            |
| ANALISTA_CLIENTE | Externo          | Solo solicita y ve estados             |
| EVALUADO         | Externo especial | Carga hoja de vida vía link temporal   |

## Estados del servicio (Semáforo)

1. PENDIENTE
2. PROGRAMANDO
3. EN_EJECUCION
4. FINALIZADO
5. PUBLICADO
6. CANCELADO
7. REPROGRAMADO

## Reglas de negocio críticas

- Solicitudes después de las 4pm cuentan como día hábil siguiente
- Sábados cuentan como día de entrega, no de elaboración
- Link evaluado (candidato) expira en 36 horas
- Validar evaluados duplicados (mismo servicio, mismo cliente, últimos 3 meses)
- Tiempos muertos laborales > 30 días activan cuestionario automático
- Cliente con mora no puede solicitar servicios
- Cliente pospago requiere estudio de crédito previo
- Un pago APPROVED (Wompi) levanta la restricción de acceso por deuda del cliente
- Los documentos/soportes que suben los clientes (cédula, RUT, cámara de comercio,
  habeas data) NO se versionan en git (`backend/uploads`)

## Módulo Soporte (documentos legales de clientes)

- Entidad `SoporteCliente`: documento legal/soporte cargado por el cliente
  (cédula, RUT, cámara de comercio, habeas data, etc. — enum `TipoSoporte`).
- Flujo: cliente carga soporte → estado `EstadoSoporte` pendiente → gestor/admin
  lo valida (`validadoPor`, `fechaValidacion`) o lo rechaza con observaciones.
- Controladores: `ClienteSoporteController` (cliente sube/consulta sus soportes),
  `AdminSoporteController` (gestor/admin valida).

## Módulo Mensajería (cliente ↔ gestor)

- Entidad `MensajeClienteGestor`; conversación 1:1 por cliente-gestor asignado.
- Controladores: `ClienteMensajeController`, `GestorMensajeController`.
- Soporta resumen de conversación, contador de no leídos y envío de mensajes.
- Notificaciones asociadas vía `notificaciones` module.

## Novedades de subproceso

- Entidad `NovedadSubproceso` (tabla `novedades_subproceso`, V187): quien ejecuta el subproceso
  (analista/poligrafista/visitador asignado) reporta algo fuera de lo normal, p.ej. "el candidato
  no llegó". Estados `EstadoNovedad`: ABIERTA → RESUELTA (con respuesta obligatoria).
- Tipos de novedad = catálogo administrable (V189, módulo `catalogo`), ya NO un enum:
  `categorias_novedad` 1─N `tipos_novedad` 1─N `tipos_novedad_roles` (a qué rol ejecutor le
  aparece), y `novedades_subproceso.id_tipo_novedad` FK → `tipos_novedad`. Pantalla
  admin/catálogo → "Tipos de novedad" (`TiposNovedad.jsx`, `TipoNovedadController` en
  `/api/v1/catalogo/novedades`). Lo usado no se borra, se desactiva (`activo=false`); la lista
  que ve el ejecutor solo trae tipos y categorías activos que apliquen al `rolResponsable`.
- Resuelven: ADMIN_POLYGRAPH, el GESTOR del cliente, o el PROGRAMADOR responsable (solo
  poligrafía/visita). NO cambia el estado del subproceso ni bloquea completarlo.
- Notificaciones: `NOVEDAD_REGISTRADA` (a gestor del cliente + programador responsable) y
  `NOVEDAD_RESUELTA` (a quien reportó). Endpoints en `NovedadSubprocesoController`
  (`/api/v1/gestor/asignaciones/{id}/novedades`).
- Seguimiento (`NovedadSeguimiento`, tabla `novedad_seguimientos`, V188): mientras está ABIERTA,
  quien la reportó o quien coordina agregan observaciones (`POST .../novedades/{id}/seguimientos`).
  Al resolver, quien coordina puede mandar `nuevaFechaProgramada` y se corre la hora del
  subproceso vía `AsignacionService.reprogramar` (valida agenda, historial `REPROGRAMADO`,
  notificación `SUBPROCESO_REPROGRAMADO`); queda como entrada REPROGRAMACION del seguimiento.
- Visibilidad: analista/poligrafista/visitador solo ven las novedades que ellos mismos
  registraron; gestor/programador/admin ven todas. Orden: la más reciente primero.
- Frontend: pasos de `pages/shared/Formulario.jsx`: 0 "Novedades" (`NovedadesSubproceso.jsx`),
  1 "Candidato" (`FichaCandidato seccion="datos"`), 2 "Soportes" (`FichaCandidato
  seccion="soportes"`), luego las secciones de la plantilla y al final "Revisión y envío" (solo
  quien diligencia).

## Motor de calificación de riesgo (en construcción)

- Especificación y decisiones: `docs/modelo-calificacion-riesgo.md` — toda lógica de riesgo debe
  cumplirla; si algo no está definido allí, preguntar antes de asumir.
- Fase A (hecha, V190): peso por subproceso en `procesos_tipos_progreso.peso` (NULL = sin
  configurar, 0 = no califica; el proceso califica solo si suman 100), `procesos.cobertura_minima`,
  `criticidad_defecto`, `version_calificacion`; tablas `umbrales_riesgo` y `parametros_riesgo`.
  Backend: `catalogo/service/CalificacionProcesoService` (`/catalogo/procesos/{id}/calificacion`) y
  módulo `riesgo` (`ParametrosRiesgoService`, `/catalogo/riesgo/parametros`). Frontend: bloque
  "Calificación de riesgo" en el modal de subprocesos de `Catalogo.jsx` y
  `pages/admin/riesgo/ParametrosRiesgo.jsx`.
- Fase B (hecha, V191): puntos por respuesta en `formulario_columna_puntajes` (campos Lista/Sí-No
  con `formulario_columnas.califica_riesgo`); `ColumnaResponse.de(...)` es el ÚNICO lugar que arma
  columnas — `puntajes` solo va al admin (`/estructura` los oculta al resto), `opcionesCriticas` va
  a todos. Respuesta crítica exige `formulario_datos.justificacion` para completar el subproceso.
- Fase C (hecha): motor puro en `modules/riesgo/motor` (sin Spring/JPA) — `MotorCalificacionRiesgo.calcular`
  orquesta `CalificadorServicio` → `DetectorCriticos` → `CalculadorPonderado` → `EvaluadorPisos` →
  `EvaluadorRevisionManual`. Pruebas de aceptación de la especificación en
  `MotorCalificacionRiesgoAceptacionTest`: si una no da, se revisa el motor, NO se cambia el valor esperado.
- Fase D (hecha, V192): `riesgo/service/CalificacionRiesgoService` arma la entrada del motor (catálogo +
  respuestas) y guarda `calificaciones_riesgo` (una fila vigente + historial; solo inserta si el
  resultado cambió). Se recalcula por evento `RecalculoRiesgoSolicitado` DESPUÉS del commit (lo
  publican `FormularioService.guardar` y `AsignacionService.recalcularEstadoServicio`) — un error ahí
  nunca revierte lo guardado. "No realizado" = CANCELADO + `no_realizado` (desde resolver una novedad,
  `AsignacionService.marcarNoRealizado`). El nivel final es automático (= recomendación del motor);
  el gestor puede cambiarlo con justificación o volver al automático. Publicar solo exige cálculo
  `CALCULADO` y bloquea el cálculo; si el servicio sale de PUBLICADO, se
  desbloquea y se recalcula (`CalificacionRiesgoService.recalcular`).
- Fase E (hecha): marcadores Word `${riesgo['nivel'|'puntaje']}` (estudio, nivel que vale) y
  `${calificacion['nivel'|'puntaje']}` (subproceso del documento), vía
  `CalificacionRiesgoService.marcadoresWord` → `ContextoPlantillaWord`. Sin cálculo: "En proceso" /
  "Sin calificar" y puntaje vacío (nunca rompen la generación).
- Encabezado del formulario (`FranjaRiesgo` en `Formulario.jsx`): el ejecutor ve nivel/puntaje de su
  subproceso (parcial mientras lo llena); gestor del cliente/admin ven además el estudio.
  `GET /api/v1/riesgo/subprocesos/{id}/resumen` (`RiesgoSubprocesoController`).
  Endpoints `/api/v1/riesgo/servicios/{id}` (GET, PUT /criticidad, PUT /decision); frontend
  `components/RiesgoServicio.jsx` (pestaña "Riesgo" de `pages/gestor/Solicitudes.jsx`).

## Módulo Enlaces Externos

- Entidad `EnlaceExterno`: directorio de entidades externas (categoría, URL,
  contacto, ciudad, si requiere login) que usan los roles internos como
  referencia rápida (ej. entidades de verificación, centrales de riesgo, etc.).
- CRUD administrado por roles internos (`creadoPor` referencia a `Usuario`).

## Convenciones de código

- Idioma del código: español (variables, métodos, clases)
- Idioma de comentarios y mensajes al usuario: español
- DTOs siempre separados de las entidades
- Usar records de Java para DTOs de respuesta
- Validaciones con Bean Validation (@Valid)
- Manejo de excepciones centralizado con @ControllerAdvice
- Logs con SLF4J en cada operación crítica
- Todos los endpoints bajo /api/v1/

## Módulo de Pagos (Wompi)

- Pasarela: Wompi (Bancolombia), moneda COP, montos SIEMPRE en centavos (entero long).
- Integración elegida: Web Checkout / Widget. NO usamos API REST pura (evitamos alcance PCI-DSS).
- Ambientes: sandbox y produccion, con llaves y URL de eventos separadas.
- Fuente de verdad del estado de un pago: el WEBHOOK de eventos, nunca el redirect.
- Estados posibles de transacción: APPROVED, DECLINED, VOIDED, ERROR, PENDING.
- Roles con acceso al módulo: ADMIN_POLYGRAPH y GESTOR (gestión total),
  ADMIN_CLIENTE (pagar y ver sus propios pagos). Ningún otro rol accede.
- Regla de negocio: un pago APPROVED debe levantar la restricción de acceso por deuda
  del cliente correspondiente.
- Entidades: `OrdenCompra` + `OrdenCompraItem` (carrito), `PagoWompi` /
  `TransaccionPago` (registro del pago), `SaldoServicioCliente` (bolsa prepago
  por servicio), `AuditoriaPago` (trazabilidad de cambios de estado).
- `ConciliacionPagosJob`: job programado que concilia transacciones que quedaron
  en estado no terminal (ej. PENDING) contra Wompi, por si el webhook no llegó.
- Frontend: `pages/client/SimuladorPago.jsx` (checkout) y
  `pages/client/ResultadoPago.jsx` (pantalla de resultado tras redirect;
  NO decide el estado final, solo lo informativo — ver regla del webhook arriba).

## Cambios aplicados tras auditoría del 2026-08-22

Esta sección corrige lo que el código demostró que estaba desactualizado o era falso en este
documento — no repite todos los hallazgos, solo los que contradicen algo escrito arriba.

**Nota 2026-09-15: `docs/auditoria/` (y `docs/DICCIONARIO.md`, `docs/adr/`, el `README.md`
mencionados más abajo) NO existen en el repo.** Esta sección los referencia como si tuvieran el
detalle completo con evidencia archivo+línea, pero nunca se comprometieron a git o se perdieron.
Los hallazgos H01-H12 solo quedan documentados aquí, en este resumen.

- ~~**"Cliente pospago requiere estudio de crédito previo" (línea de Reglas de negocio
  críticas) NO está implementado.**~~ **RESUELTO 2026-09-15** — `SolicitudService.validarMoraPospago`
  ahora bloquea la solicitud (403) si `ClientePospago.requiereAprobacion` sigue en `true`, antes de
  llegar a la validación de cupo de crédito (hallazgo H02).
- **"Roles con acceso al módulo [de Pagos]: ADMIN_POLYGRAPH y GESTOR (gestión total)" es
  parcialmente falso.** GESTOR SÍ tiene acceso a `PagoController` (iniciar/consultar pagos),
  pero NO tiene acceso a `OrdenCompraController` (carrito de compra — restringido solo a
  ADMIN_CLIENTE, hallazgo H05) ni a `AuditoriaPagoController` (historial de auditoría —
  restringido solo a ADMIN_POLYGRAPH, hallazgo H12). "Gestión total" no describe el estado
  real del código.
- **Existen DOS webhooks de Wompi activos y paralelos**, no uno solo como sugiere la
  redacción de esta sección: el documentado arriba (`POST /api/v1/webhooks/wompi`,
  `PagoWompiWebhookService`) y un flujo "viejo" de carrito/`OrdenCompra`
  (`POST /api/v1/pagos/wompi/webhook`, `OrdenCompraService.procesarEventoWompi`) — ver
  hallazgo H04. Antes de tocar el webhook, confirmar cuál de los dos está configurado
  como URL de eventos real en el dashboard de Wompi.
- ~~**El cálculo de "día hábil" no maneja festivos colombianos**~~ **RESUELTO 2026-09-15** —
  nuevo `FestivosColombiaService` (Ley Emiliani + fecha de Pascua vía Meeus/Jones/Butcher),
  consultado desde `DiasHabilesService.esDiaHabilElaboracion` (hallazgo H07).
- **"Auth: Spring Security + JWT + 2FA (admin)" (sección Stack tecnológico) es
  parcialmente falso: el 2FA NO está implementado.** `Usuario.requiere2fa`/`secreto2fa`
  existen como columnas y se pueden activar/desactivar desde la administración
  (`ClienteAdminService`, `UsuariosInternosService`), pero `AuthService.login` nunca
  verifica ningún código TOTP — un usuario con `requiere2fa=true` inicia sesión igual con
  solo su contraseña. Es un campo de base de datos sin lógica de negocio detrás.
- ~~**Aislamiento multi-tenant**: `GET /api/v1/requests/{id}` y
  `PATCH /api/v1/requests/{id}/status` no validan que la solicitud pertenezca al cliente
  autenticado~~ **RESUELTO 2026-09-15** — `SolicitudService.validarPropiedad` ahora exige que
  `Servicio.cliente` coincida con el `idCliente` del usuario autenticado (403 si no) para
  `ADMIN_CLIENTE`/`ANALISTA_CLIENTE`; roles internos sin restricción (hallazgo H01, CRÍTICO — era
  la brecha de seguridad más seria encontrada en la auditoría).

**Otros hallazgos corregidos el 2026-09-15 (encontrados en una auditoría de seguimiento, no en la
original del 22 de agosto):** Swagger/OpenAPI quedaba público sin login (ahora apagado por
defecto, `SWAGGER_ENABLED=false`); `GET /requests/bulk/template` no tenía `@PreAuthorize`; y
`/api/v1/auth/login` y `/forgot-password` no tenían ningún límite de intentos (nuevo
`RateLimitFilter`, 10 intentos/minuto por IP).

**Corregido 2026-09-24:** `PATCH /api/v1/gestor/asignaciones/{id}/completar` y
`GET .../{id}/historial` solo validaban el ROL del subproceso, no a quién estaba asignado — un
analista/poligrafista/visitador podía completar o ver el historial del subproceso de un colega
llamando la API directo. Ahora `AsignacionService.validarEsDelEjecutor` exige que sea el
`usuarioAsignado` (403 si no).

**Corregido 2026-09-25:** un GESTOR podía abrir, guardar, aprobar, devolver, asignar, desasignar,
cancelar o ver el historial de subprocesos de clientes de OTRO gestor (solo se validaba el rol), y
también su ficha de candidato, documento Word/PDF, novedades y hoja de vida. Ahora
`shared/utils/AccesoGestor.validar` exige `Cliente.idGestor == gestor` (403 si no) en
`FormularioService`, `FormularioDocumentoService`, `FichaCandidatoService`,
`NovedadSubprocesoService`, `AsignacionService` y `CandidatoHojaVidaService`. Todo endpoint nuevo
que reciba un subproceso/servicio y lo abra a GESTOR debe llamarlo.

**Documentación nueva generada por la auditoría** (no reemplaza este archivo, lo
complementa): `docs/DICCIONARIO.md` (glosario de negocio↔código↔BD), `docs/adr/`
(decisiones de arquitectura), `README.md` (cómo levantar el proyecto), y `docs/auditoria/`
(inventario, hallazgos, cobertura de pruebas, reporte final).

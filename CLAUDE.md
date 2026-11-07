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
- ~~Link evaluado (candidato) expira en 36 horas~~ **Cambiado 2026-09-28 (V197):** el link sigue
  abierto (editar hoja de vida, documentos, citas) mientras el servicio no esté FINALIZADO,
  PUBLICADO o CANCELADO; enviar el formulario ya no lo cierra. Solo lo cierran: revocarlo
  (`links_candidato.revocado`), bloqueo por intentos, o el cierre del servicio. Regla única en
  `servicios/entity/EstadoLink` — la usan el portal y el gestor. UN SOLO link por servicio (V201,
  índice único `ux_links_candidato_servicio`): "Generar nuevo link" ELIMINA el anterior (su token deja
  de funcionar) y el nuevo hereda `usado`/`fechaUso`; hoja de vida, documentos y autorización van por
  candidato, no por link, y se conservan. Ya no hay historial de links en la pantalla del gestor.
- Validar evaluados duplicados (mismo servicio, mismo cliente, últimos 3 meses)
- Tiempos muertos laborales > 30 días activan cuestionario automático
- Cliente con mora no puede solicitar servicios
- Cliente pospago requiere estudio de crédito previo
- Un pago APPROVED (Wompi) levanta la restricción de acceso por deuda del cliente
- Gestor que deja de atender (2026-10-01): al desactivarlo o cambiarle el rol, sus clientes pasan
  solos a los gestores activos, cada uno al menos cargado (`clientes/service/ReasignacionGestorService`,
  lo llama `UsuariosInternosService.cambiarEstado/actualizar`). Avisa al cliente (`GESTOR_ASIGNADO`),
  al gestor que recibe (`CLIENTE_ASIGNADO`) y a los admins. Sin otro gestor activo no mueve nada y
  avisa a los admins. Reactivarlo NO le devuelve los clientes. No existe borrado de usuarios internos.
- Tipografía (2026-10-01): toda la app usa Arial — `font-sans` y `font-mono` apuntan a la misma
  fuente en `tailwind.config.js`; no fijar `font-family` en componentes.
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
- Cliente (V193, 2026-09-25): ve en el detalle de su solicitud SOLO las novedades de tipos con
  `tipos_novedad.visible_cliente = true` (interruptor en Admin → Novedades) — tipo, subproceso,
  estado, respuesta y nueva fecha si se reprogramó. Nunca la `descripcion` del ejecutor ni el
  seguimiento interno (`SolicitudService.novedadesParaCliente`). El historial que recibe el
  cliente no trae el correo del empleado interno. Excepción (V200, 2026-09-29): las novedades
  automáticas de documento (`DOCUMENTO_*`) son visibles por defecto y SÍ mandan su descripción
  como `detalle` (documento + observación, la misma que ve el candidato). En la lista de documentos
  el cliente también ve `estadoRevision` y `observacionRevision` de cada uno (sin abrir el archivo).
- Tablero del gestor (2026-09-29): sección "Novedades internas" con abiertas (y cuántas >48 h),
  por categoría y tipo, registradas vs. resueltas (8 semanas), tiempo promedio de respuesta (30 días)
  y las 5 abiertas más antiguas (clic → `/gestor/formulario/{idServicioSubproceso}`). Viene en
  `GET /api/v1/gestor/dashboard/graficas` (`GestorService.obtenerGraficas`, solo clientes del gestor).
  El programador ve lo mismo de los subprocesos que coordina: `GET /api/v1/programador/dashboard/novedades`
  (`NovedadesTableroService`, un solo cálculo con alcance gestor, programador o ejecutor). Analista,
  poligrafista y visitador comparten `pages/shared/AsignacionDashboard.jsx` (mismo formato, en
  pestañas) y ven las novedades que ellos registraron: `GET /api/v1/ejecutor/dashboard/novedades`.
  Los tableros de gestor y programador usan las piezas de `components/tablero/Tablero.jsx`.
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
- Calificación por hallazgos (2026-10-05/06): la especificación de Antecedentes está en
  `docs/antecedentes/` y `motor_ant.py` manda (casos C1–C5 en `casos_prueba.py`). GENÉRICO para todos
  los servicios. Si los subservicios de un estudio difieren en criticidad, el estudio usa la MÁS
  EXIGENTE (Crítico > Sensible > Estándar). Umbrales: `umbrales_riesgo` (V190).
- **Catálogo de hallazgos (V206, decisión del usuario 2026-10-06) — LA BASE.** Admin → Calificación de
  riesgo → Catálogo de hallazgos (`pages/admin/riesgo/CatalogoHallazgos.jsx`,
  `riesgo/service/CatalogoHallazgosService`, `/api/v1/catalogo/hallazgos`; leer = roles internos,
  cambiar = admin). Tabla `codigos_calificacion` (código ÚNICO global; `grupo` ya no se usa, 'GENERAL'):
  puntos, tipo PUNTOS/CRITICO/ALERTA (`critico`, `es_alerta`), acumula (`por_proceso`), solo con
  condición (`solo_conduccion`). SIN "Aplica a": V210 quitó la columna `area` (decisión del usuario
  2026-10-06) — a quién le sale un hallazgo lo define la plantilla al escoger qué códigos ofrece cada
  campo; el constructor de cualquier plantilla ve todo el catálogo activo.
  El código no se cambia; lo que una plantilla usa no se borra, se desactiva. Un cambio de puntos se
  usa de inmediato (el cálculo lee el catálogo en cada uso). NO se maneja nada en Excel: el módulo
  Diccionarios (V205) se descartó y V206 borró sus tablas (V205 sigue en el repo SOLO porque ya está
  aplicada en dev: no tocar su archivo, Flyway valida el checksum).
- En el constructor de plantillas cada campo dice para qué sirve (`formulario_columnas.uso_calificacion`,
  `CalificacionColumna.jsx`, `CalificacionColumnaDto`): HALLAZGO (lista cuyas opciones son códigos que
  el admin marca del catálogo; quien diligencia elige uno), CRITICIDAD (Estándar/Sensible/Crítico, una
  por plantilla; sin ella se usa la del estudio), CONDICION ("Sí" habilita los "solo con condición").
  Una lista normal puede generar hallazgo por opción (`formulario_columna_hallazgos`, ej. "No
  encontrada" → LEG-01). `fuente_hallazgo`/`detalle_hallazgo` = cómo sale en la tabla de hallazgos
  ({clave} = valor). Columnas calculadas: `calculo` PUNTAJE, FORMULA, NIVEL, SEMAFORO_*, NOTA
  (`referencia` + `texto_nota`: sale si eso no tiene valor), NUMERO_FILA (en tablas) y, en la sección
  TABLA `calculada` (tabla de hallazgos, el constructor le crea las 5 columnas), REF, DESCRIPCION,
  FUENTE, DETALLE, PUNTOS. Al guardar, `FormularioPlantillaService` valida todo y pone
  `tipos_progreso.grupo_calificacion = 'HALLAZGOS'` si la plantilla aplica hallazgos (NULL si no).
- Pantallas (2026-10-06, pedido del usuario): el Catálogo de hallazgos es la tabla estilo Equipo Polygraph
  (tarjetas de filtro por tipo: Suman puntos, Críticos, Alertas, Inactivos; columna "Uso") con modal
  para crear/editar (el admin escribe el código). El constructor de plantillas
  (`PlantillaFormulario.jsx`) usa el MISMO diseño del formulario del analista (decisión del usuario
  2026-10-06: "guíate con el formulario de analista"): encabezado con franja, índice de pasos a la
  izquierda (Documento Word, Anexo 1, Campos archivados y luego las secciones), la sección como
  tarjeta con los campos en cuadrícula de 3 columnas tal como los verá quien diligencia (clic = se
  edita debajo, sin modal) y barra inferior Anterior / Guardar / Siguiente. Rechazados antes: pestañas
  con índice lateral, tabla + modales, y el diseño apilado original. Cada tarjeta de campo muestra su
  marcador Word (`${campos['clave']}`, o `${['clave']}` en tablas) con botón Copiar y aviso si la
  clave se repite; en un campo NUEVO la clave se arma sola desde el título (un campo guardado la conserva).
- Anexo 1 del informe = el catálogo de la plataforma, no texto del Word: `tablas['anexo_catalogo']`
  y `tablas['anexo_alertas']` (columnas a1_ref, a1_hallazgo, a1_calificacion; SOLO los hallazgos
  activos que usa la plantilla — `hallazgosUsados`, pedido del usuario 2026-10-06), siempre disponibles (`FormularioPlantillaWordService.tablasCatalogo`). El Word de
  `docs/antecedentes` ya trae el anexo con repeatTableRow. Panel "Anexo 1 · Catálogo" en el
  constructor (`PanelAnexo.jsx`, panel plegable).
- Secciones opcionales (V212, pedido del usuario 2026-10-14): en el constructor cada sección es
  Obligatoria u Opcional (`formulario_secciones.opcional`). En una opcional, quien diligencia ve
  "Quitar sección" (`Formulario.jsx`, confirmación) y puede volver a agregarla; lo quitado va en
  `GuardarFormularioRequest.seccionesOmitidas` → `formulario_secciones_omitidas`. Una sección quitada: no se
  guarda nada de ella (lo que tenía se borra, incluso filas de tabla que un ejecutor no podría borrar), no
  exige obligatorios ni cuenta en el avance, y en el Word sus campos/filas salen vacíos. Para que
  desaparezca el bloque completo (título incluido) el Word lo encierra con el comentario
  `repeatDocPart(secciones['clave'])` (`ContextoPlantillaWord.getSecciones`: incluida = lista con el mismo
  contexto, quitada = lista vacía; clave inexistente = incluida). Una obligatoria no se puede quitar (400).
  La tabla de hallazgos que arma el sistema se puede dejar opcional: quitarla solo la oculta, la
  calificación sigue saliendo de los demás campos. Pruebas `SeccionesOpcionales*Test`.
- Máximo de caracteres por campo de texto (V209, `formulario_columnas.longitud_maxima`, NULL = 2.000,
  1–10.000): se pone en el constructor, el formulario muestra "n / máx." y el backend lo valida al
  guardar (`FormularioService.validarLongitud`). Los textos del formulario crecen solos y tienen la
  ortografía del navegador en español (`TextoAjustable` en `Formulario.jsx`).
- Cálculo: `riesgo/hallazgos/MotorHallazgos` (puro; numera, criticidad, condición, recolecta en el
  orden de la plantilla, `CalificadorPorCodigos`, salidas). `CalculoSubservicioService.calcular(secciones,
  criticidadDelEstudio, campos, tablas)` arma la plantilla y el catálogo (activo, sin alertas). Las
  alertas no suman ni salen en la tabla de hallazgos.
- Campos calculados (Prompt 4): `CalculoFormulario` (campos, tablas calculadas como hallazgos,
  columnas calculadas por fila como p_numero, `aviso` si no se pudo calcular — ej. falta ev_criticidad,
  y `avisos` al analista). Se calcula EN VIVO, nunca se guarda: `FormularioService.obtener` (al abrir y
  después de guardar; la pantalla toma solo lo calculado con `aplicarCalculados`) y
  `FormularioDocumentoService.generar` (justo antes del Word). `ValoresPorClave` pasa lo guardado (por
  id) a claves.
- Integración con el estudio (Prompt 5, parcial): `CalificacionRiesgoService.hallazgos` usa, para
  un subservicio con grupo, los hallazgos de su cálculo propio (`ModeloCodigos.paraMotorEstudio`,
  vía `formularios/service/CalculoSubprocesoService`); el motor del estudio no se duplicó. La
  criticidad del estudio sale de los formularios (`CalculoFormulario.criticidad`, la más exigente);
  la del gestor/proceso solo si ningún formulario la tiene. Subservicio sin cómo calificar (sin grupo
  y sin campos que califiquen) = `EstadoSubproceso.SIN_CALIFICACION` (decisión del usuario
  2026-10-06, "opción b"): queda fuera del estudio en cualquier estado y su peso se reparte entre los
  que sí tienen resultado; no deja el caso EN_PROCESO ni cuenta como obligatorio no realizado (sí baja
  la cobertura). Prueba de punta a punta: `AntecedentesPuntaAPuntaTest`. PENDIENTE de decisión del
  usuario: reparto del 20 % de Validaciones y los pesos del proceso AVANZADO (hoy sin pesos: no califica).
- Avisos al analista: `CalculoFormulario.avisos` → `FormularioResponse.avisosFormulario`, recuadro ámbar.
  Hoy solo "LEG-17 no suma porque el cargo no implica conducción." Los avisos específicos del Prompt 6
  (calidad de víctima, relación con el cargo, procesos ejecutivos, solo nombre) se quitaron con el
  rediseño a hallazgos (2026-10-06).
- Constructor de formularios, ampliado para esto (V203, genérico): opciones de lista UNA POR LÍNEA
  (leer/escribir solo con `formularios/service/OpcionesLista`; antes coma y se partían), columnas
  `calculado` (las llena el código, solo lectura, nunca en `formulario_datos`), `campo_servicio`
  (dato del estudio: `CampoServicio` — N° de estudio, fecha de emisión, cliente, cargo, profesional),
  `valor_defecto`, `obligatorio` (bloquea completar: `FormularioService.camposObligatoriosVacios`) y
  secciones TABLA `calculada`. `FormularioColumna.esAutomatica()` = nadie la diligencia.
- Formulario de Antecedentes: V207 lo creó DESDE CERO y V208 lo volvió a crear ORGANIZADO como
  `docs/antecedentes/Ejemplo_Antecedentes_ficticio.pdf` (pedidos del usuario: cada una borra secciones,
  campos y datos diligenciados de esa plantilla; el Word subido se conserva): 22 secciones (la
  declaración en 3 bloques con la pregunta completa), filas sugeridas con las fuentes del ejemplo (la
  etiqueta llena la primera lista que la tenga como opción, `FormularioService.valoresDeFilaSugerida`),
  "Sin novedad"/"—" por defecto y obligatorios. Claves = marcadores del Word
  (`PlantillaAntecedentesWordTest`). V207 y V208 ya están aplicadas en dev: no editarlas. Estudio de
  ejemplo (Laura Ejemplo Pérez, datos del PDF, 60,0 MEDIO): `EjemploAntecedentesService`,
  `POST /api/v1/catalogo/ejemplos/antecedentes`, botón en el Catálogo de hallazgos (`EjemploAntecedentesTest`).
  Manual de uso: https://claude.ai/artifact/35qotaVtKa6ZdZWDbbCxxm · manual técnico:
  https://claude.ai/artifact/WarfeFhTLBq8at9jJhWqmi (sección "Calificación por hallazgos"). C1–C5 en `AntecedentesHallazgosTest`, punta a
  punta en `AntecedentesPuntaAPuntaTest`, catálogo + constructor en `PlantillaHallazgosTest`. Diferencias
  con motor_ant.py solo de texto: el detalle de LEG-12 dice "La respuesta a la pregunta N…" (la primera
  que no coincide). `analista_nombre` = dato del estudio (Profesional). Falta subir el Word
  `docs/antecedentes/VERIFICACION_ANTECEDENTES_LEGALES_FINAL.docx` en Admin → Catálogo → Plantilla.
- Validación académica (V216, 2026-10-15, pedido del usuario): plantilla creada DESDE CERO (versión 1; la migración borra
  secciones, campos, datos diligenciados y el Word cargado) con `docs/Validacion Academica/` (`motor_aca.py` manda). Califica
  por hallazgos con UN solo campo (`aca_ref`, códigos ACA-01..05 y alertas ALR-ACA-01/02 del catálogo); con un solo campo
  de calificación la fórmula sale "ACA-03: 45 de 100 posibles" (`CalificadorPorCodigos`, `calificacionUnica`). Word:
  `VERIFICACION_ACADEMICA_PLATAFORMA_FINAL.docx` (usa la tabla fija `educacion`, no `educacion_declarada`). Pruebas
  `AcademicaHallazgosTest` (A1–A9 + marcadores) y `AcademicaPuntaAPuntaTest`. PENDIENTE: avisos al guardar del Prompt 6,
  precarga desde `aca_estudio_sel`, `aca_fecha_limite` calculada con festivos y el peso 6 % en el proceso AVANZADO.
- Validación laboral (V218, 2026-10-15, pedido del usuario): igual que la académica pero con `docs/Validacion Laboral/`
  (`motor_lab.py` manda). Subservicio "Validacion Laboral" (id 16; "Validacion Laboral (4 años)" no se tocó). Hasta DOS
  códigos por vinculación (`v_ref1`, `v_ref2`, LAB-01..12 `por_proceso`; el mismo código en la misma fila cuenta una vez —
  `MotorHallazgos.agregar` con `vistosEnLaFila`); LAB-13/14 salen de `in_corroboracion` (opción→código, una vez por estudio).
  Cálculos nuevos del constructor: por fila `CODIGOS_FILA` y `TEXTO_SI` (referencia `campo=Valor`), y en `FORMULA` la
  referencia `CON_CODIGO` (un solo hallazgo → "LAB-07: 50 de 100 posibles"). Word:
  `VERIFICACION_LABORAL_PLATAFORMA_FINAL.docx` (usa la tabla fija `experiencia_laboral`). Pruebas `LaboralHallazgosTest`
  (L1–L10 + marcadores) y `LaboralPuntaAPuntaTest`. PENDIENTE: avisos al guardar, precarga de vinculaciones/inactividad,
  fecha límite con festivos, bloqueo del informe final con vinculaciones "Pendiente" y el peso 10 % en AVANZADO.
- Validación personal / referencias (V219, 2026-10-22, pedido del usuario): igual que las anteriores con
  `docs/Validacion Personal/` (`motor_per.py` manda). Subservicio "Validacion Personal" (V219 lo crea si no existe). Catálogo
  PER-01/02/03 (`por_proceso`), PER-05/06 (una vez por estudio) y ALR-PER-01; sin críticos. El analista marca UN código por
  referencia validada (`r_ref`) y el estado de cada declarada (`rd_estado`). Tres cálculos NUEVOS y genéricos del constructor
  (`MotorHallazgos`, `FormularioPlantillaService`, `CalificacionColumna.jsx`): `PROMEDIO_FILA` (referencia = claves a promediar;
  `texto_nota` "PER-03<3" = hallazgo automático si queda por debajo, sin repetir el que ya marcó el analista), `MINIMO_FILAS`
  (campo; referencia = tabla, "PER-05<2" = hallazgo si hay menos filas, se evalúa al terminar de leer esa tabla) y
  `PROMEDIO_TABLA`. En fuente/detalle de un hallazgo por opción o por regla, `{#}` = cuántas filas tienen esa opción (o cuántas
  hay). PER-06 sale de la opción "Descartada – no cumple requisitos" de `rd_estado`. ORDEN: las validadas (`referencias`) van
  ANTES que las declaradas (`referencias_declaradas`) para que la fórmula salga como en motor_per.py (referencias, PER-05, PER-06).
  Precarga: las filas de `referencias_declaradas` nacen de las 4 referencias del candidato (`CandidatoInfoService.PRECARGAS`,
  solo mientras no haya filas guardadas); `ev_telefono` = `campo_candidato` CELULAR. La sección con clave `referencias` pisa la
  tabla fija del candidato en el Word (a propósito). Word: `1_Formato_Referencias_Personales.docx` tal cual, sin copia "FINAL";
  hay que subirlo en Admin → Catálogo → Plantilla. Pruebas `PersonalHallazgosTest` (P1–P6 + marcadores) y
  `PersonalPuntaAPuntaTest`. PENDIENTE: avisos al guardar de `validar()` (promedio <3, "No coincide" sin PER-02, menos de 2,
  teléfonos repetidos), "Validada" que cree sola la ficha con `r_nombre`/`r_parentesco`, y el peso 4 % en AVANZADO (con el
  reparto del 20 % de Validaciones, ver arriba).
- Análisis financiero (V220 + V221, 2026-10-22, pedido del usuario): `docs/Analisis Financiero/` (`motor_fin.py` manda). Subservicio
  "Analisis Financiero" (V221 lo crea si no existe). UN formato para síntesis, verificación y OEA. A diferencia de las demás se
  califica con SU PROPIO motor, `riesgo/financiero/MotorFinanciero` (traducción fiel de motor_fin.py, usa `CalificadorPorCodigos`
  para sumar/topar): `CalculoSubservicioService.calcular` lo elige si la plantilla tiene el campo `fin_meses_mora_90`. Catálogo
  FIN-01..08 (+ ALR-FIN-01); FIN-01/02/06 acumulan por obligación (OEA), los demás una vez. En OEA las obligaciones del reporte
  detallado mandan y la barra de 12 meses NO genera FIN-01/02; sin OEA, la barra: ≥5 meses → FIN-01, 1–4 → FIN-02. FIN-03 con ≥3 meses
  de mora 30/60; FIN-04 con >70 %; el score es informativo (fila automática en la sección 4). Avisos de `validar()` incluidos en el
  motor (ALR-FIN-01 incluido). OEA = el NOMBRE del proceso lleva la palabra OEA (`CampoServicio.ES_OEA`,
  `CalculoSubservicioService.esOea/datosDelEstudio`; el analista no lo escribe). Las secciones 8 y 9 (obligaciones, patrimonios,
  datos OEA) son opcionales: en un estudio no OEA el analista las quita. Tablas calculadas por el motor: `hallazgos`, `informativos`
  (fila del score + las del analista, que se escriben en `informativos_analista` con claves `ia_*`) y `oea` (1 elemento si es OEA, 0 si
  no; en el Word `repeatDocPart(tablas['oea'])`). Los campos que digita el analista pueden salir con el formato del cálculo en el
  Word ("$2.800 mil", "38 %", "No reportado", miles con punto): si `calculo.campos()` trae su clave se imprime esa
  (`FormularioPlantillaWordService`). Los hallazgos por opción de `fin_ejecutivo`, `c_*_res` y `oea_inconsistencia` solo documentan
  la regla y mantienen la calificación activa al guardar el constructor. Bandas del score: tablas `centrales_riesgo` y
  `centrales_riesgo_bandas` (V220, solo admin, `CentralRiesgoService`, `/api/v1/catalogo/riesgo/centrales`, pantalla
  `pages/admin/riesgo/CentralesRiesgo.jsx`); el nombre de la central NO se imprime nunca; se usa la primera activa. Word:
  `1_Formato_Financiero.docx` tal cual (subirlo en Admin → Catálogo → Plantilla). Pruebas `FinancieroHallazgosTest` (F1–F9 + marcadores
  + avisos) y `FinancieroPuntaAPuntaTest` (un Avanzado y un OEA con estudio anterior). PENDIENTE: precarga de `c_*_poli` desde la
  poligrafía y de `c_*_visita`, de `fin_ejecutivo` desde Antecedentes, de los bienes y `oea_anterior` desde el estudio OEA anterior; ocultar
  solas las secciones OEA en un estudio no OEA (hoy se quitan a mano); `cr_central` por estudio (hoy la activa); enlaces de descarga en
  el Anexo 2 (hoy el adjunto va al Word como anexo); el peso 15 % en AVANZADO.
- Grupos de secciones (V217): `formulario_secciones.grupo` clasifica las secciones en el índice del formulario y del constructor.
- Encabezado del formulario (`FranjaRiesgo` en `Formulario.jsx`): el ejecutor ve nivel/puntaje de su
  subproceso (parcial mientras lo llena); gestor del cliente/admin ven además el estudio.
  `GET /api/v1/riesgo/subprocesos/{id}/resumen` (`RiesgoSubprocesoController`).
  Endpoints `/api/v1/riesgo/servicios/{id}` (GET, PUT /criticidad, PUT /decision); frontend
  `components/RiesgoServicio.jsx` (pestaña "Riesgo" de `pages/gestor/Solicitudes.jsx`).

## Trabajos en segundo plano (cola persistente, V194)

- Lo pesado NO se procesa dentro del request: `POST` responde **202** con un trabajo (`TrabajoResponse`,
  id = UUID) y el frontend hace polling adaptativo (`services/trabajoService.js → esperarTrabajo`:
  1 s → ×1.5 hasta 8 s, pausa con la pestaña oculta, AbortController) sobre `GET /api/v1/trabajos/{id}`;
  el archivo resultado se baja en streaming con `GET /api/v1/trabajos/{id}/resultado`.
- Cola = tabla `trabajos_asincronos` (no RAM). `DespachadorTrabajos` toma con
  `UPDATE … FOR UPDATE SKIP LOCKED RETURNING` solo tantos como hilos libres tenga el pool de ese tipo
  (`EjecutoresTrabajos`, un pool fijo por `TipoTrabajo`, tamaños en `app.trabajos.tipos.*`).
  Error de usuario (ApiException 4xx) → FALLIDO directo; falla técnica → reintento con espera
  creciente hasta `max-intentos`; FALLIDO = la DLQ, visible y reintentable en Admin → Sistema →
  "Trabajos en segundo plano" (`TrabajosSegundoPlano.jsx`, `/api/v1/admin/trabajos`).
  `LimpiezaTrabajosJob` rescata los atascados en PROCESANDO (reinicio) y, cada hora, borra los
  COMPLETADOS de más de 24 h (`retencion-horas-completados`) y los FALLIDOS de más de 30 días; el
  panel tiene "Borrar completados" (`DELETE /api/v1/admin/trabajos/completados`), que respeta los de
  los últimos 10 min por si se están descargando.
- Tipos actuales: `CARGA_MASIVA` (`CargaMasivaProcesador`, revisa todo el archivo y crea todo en UNA transacción —NS-12, todo o nada—, 1 hilo, sin
  reintento automático), `INFORME_PDF` (`InformePdfProcesador`, el cliente descarga el informe) y
  `FORMULARIO_PDF` (`FormularioPdfProcesador`, "Descargar PDF" del formulario de roles internos,
  `POST /gestor/formularios/subproceso/{id}/documento-pdf`, V196). Ambos PDF comparten caché
  (`AlmacenTrabajos.rutaCacheInforme`, subproceso+fechaPublicado) SOLO con servicio y subproceso
  PUBLICADOS: antes, el `${riesgo[...]}` del estudio aún cambia. Tipo nuevo = enum + CHECK en BD + un
  bean `ProcesadorTrabajo`; `procesar()` corre SIN transacción — nunca convertir/leer archivos dentro de una.
- Errores de configuración (plantilla Word faltante → 409, marcadores mal escritos → 422) son 4xx a
  propósito: la cola no reintenta 4xx. Generar un documento NO escribe en BD:
  `CalificacionRiesgoService.marcadoresWord` es readOnly y, sin cálculo guardado, calcula en memoria.
- LibreOffice: `DocumentoPdfConverter` limita conversiones simultáneas (`app.libreoffice.max-concurrentes`,
  503 si no hay turno). `FormularioDocumentoService` ya no genera PDF: quien lo necesite llama
  `generar()` y convierte DESPUÉS, fuera de la transacción.
- `@Async` usa un ejecutor acotado (`config/AsyncConfig`, cola 500, CallerRunsPolicy).
- Los polls periódicos del frontend usan `hooks/useIntervaloVisible` (se pausan con la pestaña oculta).

## Tiempo real (avisos del servidor, SSE — 2026-09-29)

- Las pantallas se actualizan solas, sin refrescar. Backend `modules/tiemporeal`:
  `CambiosEntityListener` (JPA, en `Servicio`, `ServicioSubproceso`, `NovedadSubproceso`,
  `NovedadSeguimiento`, `SugerenciaDisponibilidad`, `LinkCandidato`, `DocumentoEvaluado`,
  `HojaVidaEvaluado`) anota qué cambió → `RegistroCambios` lo junta por transacción y, SOLO si hizo
  commit, `ResolutorCambios` (async) calcula a quién le incumbe (gestor del cliente, usuarios del
  cliente, ejecutor asignado, programador responsable, admins; el candidato de ese servicio) →
  `CanalCambios` envía `cambio {"servicios":[id...]}`. El aviso NUNCA lleva datos: cada pantalla
  vuelve a pedir lo suyo con sus permisos. Entidad nueva que se vea en pantalla → agregarle
  `@EntityListeners(CambiosEntityListener.class)` y resolverla en el listener/resolutor.
- Endpoints: `GET /api/v1/tiempo-real/eventos` (con sesión) y `GET /api/v1/evaluees/link/{token}/eventos`
  (portal del candidato). Latido cada 25 s; conexión de 30 min y el navegador reconecta; máx. 6 por
  usuario. `SecurityConfig` deja pasar los despachos ASYNC/ERROR (cierre del SseEmitter).
- Frontend: `services/tiempoReal.js` (una conexión por pestaña, con fetch para mandar el token;
  reintento con espera creciente) y `hooks/useCambios(recargar, { servicios, candidato })` —
  recarga SIN spinner, junta avisos seguidos y, si la conexión está caída, consulta cada 30 s. El
  service worker NO debe cachear `/eventos`. En el portal del candidato la recarga toca citas,
  documentos y estado, nunca la hoja de vida que está diligenciando.

## Portal del candidato (V197)

- Autorización de datos (2026-09-29): es la PRIMERA pantalla del link (`PantallaAutorizacion`), no un
  paso de la hoja de vida. Hasta aceptarla no se ve el portal ni se consultan datos;
  `POST /evaluees/link/{token}/autorizacion` (`EvaluadoService.autorizarTratamientoDatos`) guarda
  `hojas_vida_evaluado.autorizacion_datos` + `fecha_autorizacion` (primera aceptación; guardar la hoja
  de vida nunca la quita). Cargar documentos y proponer horario exigen haberla aceptado
  (`exigirAutorizacion`, 403). Pasos de la hoja de vida: Datos personales, Educación, Experiencia,
  Referencias, Revisión (los documentos van en su propia sección). El candidato la consulta (solo
  lectura, con fecha) desde el Inicio del portal y NO la puede retirar ahí; el gestor la ve en
  "Resumen del candidato" (`ResumenEvaluadoResponse.autorizacionDatos/fechaAutorizacion`).
- Hoja de vida, rediseño 2026-10-13 (V211, pedido del usuario — 10 cambios): pasos Datos personales,
  Educación, Experiencia, Referencias, **Declaraciones**, Revisión. Antes del portal, pantalla
  "Antes de iniciar" (`PantallaAntesDeIniciar`, una vez por sesión del navegador, solo si no ha enviado).
  - Listas administrables y preguntas: UNA tabla `items_hoja_vida` (`lista`: LIBRETA_MILITAR, EPS,
    FONDO_PENSIONES, TIPO_CONTRATO, PREGUNTA — en PREGUNTA `nombre` = pregunta, `descripcion` = ayuda).
    Admin → Novedades → "Hoja de vida del candidato" (`pages/admin/catalogo/HojaVidaCandidato.jsx`,
    `/api/v1/catalogo/hoja-vida`, `ItemHojaVidaService`); el portal las lee de
    `GET /evaluees/link/{token}/catalogo` (solo activas). La hoja de vida guarda el TEXTO elegido, así
    que borrar una opción no altera lo ya respondido. Respuestas a las preguntas (Sí/No + explicación
    obligatoria si es Sí) en `respuestas_hoja_vida`, con el texto de la pregunta copiado; al enviar,
    `EvaluadoService.validarDeclaraciones` exige todas las activas. El gestor las ve en "Resumen del candidato".
    Analista/poligrafista/visitador las ven en el paso "Candidato" del formulario (bloque "Declaraciones
    del candidato" de `FichaCandidato`, `FichaCandidatoResponse.declaraciones`). En Word: tabla fija
    `repeatTableRow(tablas['declaraciones'])` con `${['pregunta']}`, `${['respuesta']}` (Sí/No) y
    `${['detalle']}` — siempre disponible, sin crear sección. Es de SOLO LECTURA
    (`CandidatoInfoService.TABLAS_SOLO_LECTURA`): no se edita desde el perfil del candidato ni se
    puede usar como origen de una sección (`DeclaracionesCandidatoTest`). En el constructor de
    plantillas, paso fijo "Declaraciones" (`PanelDeclaraciones.jsx`, junto a "Anexo 1"): lista las
    preguntas existentes y cómo ponerlas en el Word (pedido del usuario 2026-10-14).
  - Datos personales en 5 grupos (Identificación, Contacto, Residencia, Documentos, Seguridad social).
    Celular = 10 dígitos empezando por 3; `telefono_fijo` ahora es "fijo y/o WhatsApp" (+57 300 123 4567).
    Visa y pasaporte: Sí/No (`tiene_visa`, `tiene_pasaporte`) y el número solo si es Sí (+ `visa_pais`).
    Expedición del documento: `candidatos.fecha_expedicion` + `id_ciudad_expedicion`
    (`CampoCandidato.CIUDAD_EXPEDICION`). `direccion_adicional` = casa/apartamento.
  - "Nivel educativo" ya no se pregunta: el servidor guarda el estudio más alto
    (`EvaluadoService.NIVELES_EDUCATIVOS`; la lista de categorías/niveles del portal debe coincidir).
    `sin_estudios` = "No tengo estudios para registrar" (educación vacía permitida solo así).
  - Experiencia: + `tipo_contrato`, `salario`, `llamados_atencion` (también en la tabla fija
    `experiencia_laboral` de formularios/Word). Periodos sin empleo > 30 días en los últimos 4 años:
    el portal usa el MISMO cálculo que `HistorialLaboralService` y la justificación se casa con
    `HistorialLaboralService.clave` ("desde_hasta", con INICIO/HOY en los extremos) — antes las claves
    del portal y del servidor no coincidían y la justificación se perdía al guardar.
  - Referencias: exactamente 4, con nombre, contacto y tipo de vínculo obligatorios.
- Revisión de Katherine (V213, 2026-10-14, 8 puntos clave): autorización amplía a centrales de riesgo
  (Ley 1266; texto por validar con el asesor jurídico); educación con `estado` (Graduado / Terminó
  materias / En curso / Retirado / Aplazado — `enCurso` se deriva), `en_exterior` + país y nivel "Técnico
  laboral"; experiencia con `contratado_por_temporal` + `nombre_temporal` y tipo de contrato en lista
  cerrada; periodo sin empleo con `actividad` + `quien_confirma` (+ la justificación); referencias:
  celular de 10 dígitos (distinto al del candidato y no repetido), relación y años en lista, menos de 1
  año no cumple y dos confirmaciones obligatorias; preguntas financieras 4 y 5 en `items_hoja_vida`.
  Lo exigido al ENVIAR vive en `EvaluadoService.validarRevisionCandidato` (guardar avance no exige nada).
  Expedición del documento ya venía de V211. Pendiente: historia laboral con enlaces de fondos y
  documentos condicionales, contacto de programación en "Antes de iniciar", validar mayoría de edad.
- Aceptaciones legales con registro (V214, 2026-10-14): tabla `aceptaciones_legales` (solo se inserta;
  usuario o email/referencia, tipo TERMINOS/PRIVACIDAD/MARKETING/AUTORIZACION_CANDIDATO, versión del texto,
  aceptado, fecha e IP SOLO como hash con sal). `AceptacionLegalService`: el registro de cliente
  (natural, jurídica y Google si la cuenta es NUEVA) exige `aceptaTerminos` y `aceptaPrivacidad` (400 si no) y
  guarda `aceptaMarketing` tal cual (opcional); la autorización del candidato también deja fila. La
  versión vigente de los textos vive en DOS sitios que deben coincidir: `AceptacionLegalService.VERSION_TEXTOS`
  y `frontend/src/config/empresa.js` (`VERSION_LEGAL`). Casillas: `components/legal/CasillasLegales.jsx`.
  Textos y banner de cookies: `pages/legal/`, `components/legal/BannerCookies.jsx` (`utils/consentimiento.js`).
- Accesibilidad WCAG 2.2 AA (2026-10-14): enlace "Saltar al contenido" (App) hacia `<main id="contenido">`;
  foco visible global y `prefers-reduced-motion` en `index.css`. TODO campo lleva etiqueta: en un componente
  `Campo` usar `asociarEtiqueta(children, { id, describedby, invalido })` (`utils/asociarEtiqueta.jsx`) con
  `useId()` y `<label htmlFor>`; un componente que arma su campo por dentro se marca `Componente.aceptaId = true`
  y pasa `id` al `<select>/<input>`. Dentro de `.map` el id lleva el índice (`${idCampos}-3-${i}`). Sin etiqueta
  visible (filtros, buscadores): `aria-label` descriptivo, nunca el ejemplo del placeholder. Botones de solo ícono:
  `aria-label`. No envolver el campo en `<label>`: reenvía clics a botones vecinos. Contraste: `text-gray-400`
  y `text-red-500` se sobrescriben en `index.css` (gray-500 / red-600; gris claro solo sobre fondos oscuros).
  Color de marca: `primary-600` pasó de #CD575D (4,08:1 con texto blanco) a oklch(0.56 0.15 20) = #BC484F (5,0:1 sobre blanco, 4,6:1 sobre primary-50), 2026-10-14. `primary-500` con texto blanco sigue en 3,5:1.
- Entrada con número de documento (2026-10-14, pedido del usuario): la PRIMERA pantalla del link es
  `PantallaDocumento`; `POST /evaluees/link/{token}/verificar` (`AccesoCandidatoService`) compara con
  `candidatos.cedula` (solo letras y números) y devuelve una llave HMAC (token + documento, firmada con
  `app.jwt.secret`) que el portal guarda en sessionStorage y manda en `X-Acceso-Candidato` en TODAS las
  demás peticiones, también la de eventos (`config/AccesoCandidatoConfig`, interceptor; sin llave = 401 y
  el portal vuelve a pedir el documento). Cada documento equivocado suma `links_candidato.intentos_fallidos`;
  al 3.º el link queda BLOQUEADO (`EstadoLink.MAX_INTENTOS`) y solo un link nuevo del gestor lo reabre.
  El aviso de inactividad de los usuarios internos NO corre en `/evaluado/...` (`useInactivityLogout`).
- Diseño de la hoja de vida (2026-10-14): cada grupo de Datos personales es un recuadro con título y
  descripción; todos los campos miden lo mismo (`inputCls` h-11, textos largos `areaCls`); cada campo
  lleva un párrafo de ayuda debajo (`Campo ayuda=` o `<Ayuda>`) y un ejemplo como placeholder.
- Portal por etapas (2026-09-29): hasta ENVIAR la hoja de vida el candidato solo ve Inicio y Hoja de
  vida (`seccionesVisibles`); Documentos y Citas aparecen al enviarla. Backend: cargar documentos y
  proponer horario exigen `hoja_vida_evaluado.completado` (`EvaluadoService.exigirHojaVidaEnviada`, 403).
- Archivos del candidato: SOLO PDF, JPG o PNG, revisando la firma real del archivo (no solo la
  extensión) y con tope `app.candidato.documentos.max-pdf-mb` / `max-imagen-mb` (5 MB cada uno,
  `ValidadorArchivoCandidato`); `LinkValidacionResponse.maxPdfMb/maxImagenMb` le dice al portal el
  límite para avisar antes de subir.
- `pages/evaluee/EvalueeForm.jsx`: encabezado del estudio + pestañas **Mi información** (hoja de
  vida por pasos, se puede re-enviar; solo el PRIMER envío notifica y mueve el servicio),
  **Documentos** (`DocumentosCandidato.jsx`, con estado de revisión) y **Mis citas**
  (`CitasCandidato.jsx`: tarjetas con estado, fecha, profesional —solo nombre—, lugar con Google
  Maps, duración). Endpoints públicos en `EvaluadoController` (`/evaluees/link/{token}/citas`,
  `.../citas/{id}/sugerencias`), lógica en `PortalCandidatoService`.
- Revisión de documentos (V199): desde el paso "Soportes", botón "Revisar" con lista desplegable —
  APROBADO, DENEGADO, ILEGIBLE, VENCIDO, INCOMPLETO, NO_CORRESPONDE (`EstadoRevisionDocumento`) — y
  observación (obligatoria si no se aprueba; la ve el candidato). Todo lo que no es Aprobado es una
  devolución y deja el documento BLOQUEADO (409) hasta que el candidato cargue uno nuevo, que nace
  PENDIENTE. Revisan admin, gestor del cliente, quien EJECUTA el subproceso (analista, poligrafista o
  visitador asignado) y el programador responsable (`FichaCandidatoService.puedeRevisar`). Si quien
  devuelve es el ejecutor, se crea sola la novedad del tipo `DOCUMENTO_<MOTIVO>` en su subproceso
  (evento `DocumentoDevueltoPorEjecutor` → `NovedadSubprocesoService.alDevolverDocumento`).
- Diseño del portal (`PortalCandidatoLayout.jsx`, decisión del usuario 2026-09-29): menú lateral
  NEGRO liso como el de los internos (slate-900, ítem activo bg-primary-600) con logo, empresa
  cliente (`LinkValidacionResponse.empresa`: nombre comercial → razón social → nombre), datos del
  candidato y el aviso de estado de cada sección ("Enviada", "3 por subir", "1 por corregir"...);
  contenido sobre gris muy claro. NADA del fondo del Login (degradado coral, línea de pulso) — se
  rechazó. En celular: barra blanca arriba y menú fijo abajo.
- Citas = subprocesos de rol POLIGRAFISTA/VISITADOR. `servicio_subprocesos.lugar` lo escribe el
  programador al agendar (`ModalAsignarSubproceso`); visita sin lugar muestra la dirección del
  candidato. Sugerencia de disponibilidad (`sugerencias_disponibilidad` + opciones): 1–3 días con
  franja + nota, una PENDIENTE por cita (la nueva reemplaza), notificación agrupada
  `SUGERENCIA_DISPONIBILIDAD` al programador responsable (o gestor); queda ATENDIDA al agendar.

## Solicitud de servicios — poligrafías (V223, 2026-10-27, pedido del usuario)

- Nueva solicitud (`pages/client/NewRequest.jsx`): el orden es 1 Servicios, 2 Datos del evaluado, 3 Confirmar
  (antes al revés). Como la ciudad ya no se conoce al elegir el servicio, el recargo por ciudad se muestra
  solo en el resumen final.
- Formato adicional por proceso (`procesos.formato_solicitud`: PRE_EMPLEO, RUTINA, ESPECIFICA, puesto por V223 a
  Poligrafia Pre Empleo / de Rutina / Especifica). Los campos viven en el código, `solicitudes/formato/FormatoSolicitud`
  (reproducen los documentos de `Downloads/solicitud de mas informacon`); el cliente los dibuja desde
  `GET /api/v1/solicitudes/formatos` y `SolicitudExtraService.validar` los valida. Lo respondido va en
  `servicio_datos_solicitud` (JSON). No se repite lo que ya está en "Datos del evaluado".
- Temas a evaluar: catálogo administrable `preguntas_poligrafia` (Admin → Novedades → Temas de poligrafía,
  `/api/v1/catalogo/preguntas-poligrafia`); el cliente elige hasta 4 (`SolicitudExtraService.MAX_TEMAS`) en cualquier
  servicio cuyo nombre, o el de uno de sus subprocesos, diga «poligrafía» (`admitePreguntas`). «Otros ¿Cuál?» =
  `requiere_detalle`. La solicitud copia el texto (`servicio_preguntas_poligrafia`): borrar/renombrar un tema no la cambia.
- Lo ve el poligrafista y quien coordina en la ficha del candidato (`FichaCandidatoResponse.solicitudCliente`,
  `components/SolicitudCliente.jsx`) y en Word con `tablas['preguntas_cliente']` (`${['tema']}`, `${['detalle']}`) y
  `tablas['solicitud_cliente']` (`${['campo']}`, `${['valor']}`), siempre disponibles. Pruebas `SolicitudExtraServiceTest`.
- PENDIENTE: los anexos de la poligrafía específica (descargos, audios, videos) son solo texto, sin subir archivos;
  no hay pantalla para cambiar `formato_solicitud` de un proceso (se pone por migración).

## Módulo Enlaces Externos

- Entidad `EnlaceExterno`: directorio de entidades externas (categoría, URL,
  contacto, ciudad, si requiere login) que usan los roles internos como
  referencia rápida (ej. entidades de verificación, centrales de riesgo, etc.).
- CRUD administrado por roles internos (`creadoPor` referencia a `Usuario`).

## Tablas (pedido del usuario 2026-10-14)

- **10 filas por página en todas las tablas** (`POR_PAGINA` / `usePaginacion` de
  `components/ui/Paginacion.jsx`). Lista ya completa en el navegador → `usePaginacion(lista, claveDelFiltro)`;
  lista paginada en el servidor → `size: 10`.
- **El buscador busca en TODOS los registros, nunca solo en la página visible.** Si la tabla pagina en el
  servidor, el texto va al backend (`q` en `/gestor/solicitudes` vía `ServicioSpecifications`, `buscar` en
  `/gestor/asignaciones`); prohibido filtrar en el navegador la página que llegó.
- **Sin desplazamiento horizontal desde 1024 px de ancho** (portátil de 1280 con zoom de 125 %): textos
  largos (nombres, cargos, procesos, correos) pueden partir en líneas — `whitespace-nowrap` solo en
  fechas, montos y etiquetas cortas; celdas `px-3`; columnas secundarias con `hidden xl:table-cell` (y el
  dato debajo del principal si hace falta, como el cargo bajo el evaluado). Verificado midiendo las 33
  pantallas con un Chrome headless a 1024, 1366 y 1920 px.
- **En celular (< 768 px) toda tabla es de tarjetas:** clase `table-stack` en el `<table>` (CSS en
  `index.css`). El título de cada dato lo pone solo `utils/etiquetasTablas.js` (copia el `<th>` al
  `data-label` de cada `<td>`, también en las filas que se dibujan después); las columnas
  `hidden xl:table-cell` se muestran en la tarjeta — para esconder una ahí, `stack-oculta`; si el dato ya
  se repite bajo otro (cargo bajo la cédula), ese duplicado va `hidden md:inline`. Las pestañas bajan de
  línea (`flex-wrap`) en vez de desplazarse. Verificado a 360 y 390 px: 33 pantallas sin desborde.

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

- Reintento de pago (2026-10-28): Wompi acepta UNA transacción por referencia; reenviar la misma da «la referencia ya ha sido usada». Cada
  intento (`OrdenCompraService.iniciarPago`, `PagoService.iniciarPago` sobre un pago PENDIENTE) genera una referencia nueva con
  `pagos/service/ReferenciaPago` (`base` + `-R<ms>`), conserva el mismo registro y olvida el id de transacción anterior. El webhook, la consulta
  de estado y `aplicarEventoTransaccion` reconocen un aviso tardío de un intento viejo por la BASE (`findFirstByReferenciaStartingWith`). La
  conciliación solo consulta el intento actual. Límite: si el cliente paga dos intentos distintos, el segundo aviso se ignora (la orden ya no
  está pendiente) y habría que devolver ese dinero a mano. `ReferenciaPagoTest`.

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

**Corregido 2026-09-29:** los links de candidato (`/api/v1/gestor/tokens`, `LinkCandidatoGestorService`)
tampoco validaban el gestor: cualquiera podía generar (y con V201, borrar el vigente), revocar,
revertir, eliminar, reenviar o ver el resumen/documentos del candidato de otro gestor, y la lista,
las estadísticas y los "links por atender" del dashboard mostraban los de TODOS (con token completo).
Ahora cada operación valida `AccesoGestor` y las consultas de `LinkCandidatoRepository` filtran por
`servicio.cliente.idGestor` (prueba `LinkCandidatoGestorServiceAccesoTest`).

**Corregido 2026-09-28:** la cadena de migraciones no corría en una base NUEVA (V152 fallaba con
"ya existe" porque V1 crea las tablas viejas de antecedentes, que en dev se habían borrado a mano;
luego V154 y V156). Se corrigieron V152/V154/V156; probado: 103 migraciones desde cero hasta V194,
esquema idéntico a dev. Como cambió su checksum, CADA base existente debe arrancar una vez con
`FLYWAY_REPARAR=true` (`config/FlywayConfig`, hace `flyway.repair()` y luego migra; no re-ejecuta
nada) y después quitar la variable. V195 eliminó las 41 tablas vacías del modelo fijo de V1
(`*_poligrafia`, `*_visita`, `visitas_domiciliarias`, `referencias_*`, `verificaciones_*`,
`documentos`, `informes_analisis_final`...), reemplazado por el módulo de formularios (V156); si en
alguna base una de ellas tiene filas, V195 se detiene sin borrar nada.

**Documentación nueva generada por la auditoría** (no reemplaza este archivo, lo
complementa): `docs/DICCIONARIO.md` (glosario de negocio↔código↔BD), `docs/adr/`
(decisiones de arquitectura), `README.md` (cómo levantar el proyecto), y `docs/auditoria/`
(inventario, hallazgos, cobertura de pruebas, reporte final).

## Semáforos de estado — siete estados por subproceso (SE-01, V224, 2026-10-27)

- `shared/enums/EstadoSubproceso` es el ÚNICO enum de estado de un subproceso: PENDIENTE, PROGRAMANDO, EN_EJECUCION, REPROGRAMADO,
  FINALIZADO, PUBLICADO, CANCELADO (nombre, color, orden y texto para el cliente viven ahí; catálogo en
  `GET /api/v1/catalogo/estados-subproceso`). Reemplaza a `EstadoAsignacion` (ASIGNADO → EN_EJECUCION, COMPLETADO → FINALIZADO).
  Las ACCIONES del historial (`AccionHistorialAsignacion`: ASIGNADO, COMPLETADO, DEVUELTO...) no cambiaron: son hechos, no estados.
- V224 migró los datos y dejó el reporte en `migracion_estados_subproceso_reporte` (por subproceso: anterior, nuevo, observación).
  PROGRAMANDO = poligrafía/visita ya encargada a un programador (`programadorResponsable`) y sin profesional. `asignarProgramador` la pone
  en PROGRAMANDO y `desasignar` la devuelve ahí. REPROGRAMADO de un subproceso solo nace con la cita no realizada (SE-07); el
  `Servicio.estado = REPROGRAMADO` de antes es otra cosa (cambio de fecha de entrega de la solicitud).
- `servicios.estado` se conserva como ciclo interno del servicio (link del candidato, cancelación, reprogramación de la solicitud). El
  semáforo por subproceso lo leerá de `servicio_subprocesos.estado` (SE-04).
- Transitorio hasta SE-04/SE-09: los filtros y conteos actuales tratan "Pendiente" como PENDIENTE + PROGRAMANDO.

## Máquina de estados del subproceso (SE-02, V225, 2026-10-27)

- `shared/estados/MaquinaEstadosSubproceso` (pura, sin Spring) define las ÚNICAS transiciones: enviar al programador, asignar, agendar,
  enviar informe, aprobar, devolver, cita no realizada, reprogramar, reasignar y cancelar. `AsignacionService.evaluar(...)` la aplica y
  traduce el rechazo a 403 (permiso), 409 (estado/condición) o 400 (falta motivo). Hoy el programador también aprueba, devuelve y cancela
  CITAS (como antes); el spec decía solo gestor, pero el usuario decidió (2026-10-28) que el programador SÍ puede. Cita = rol POLIGRAFISTA/VISITADOR (VerifEye sigue con
  rol ANALISTA_INTERNO hasta que el usuario decida pasarlo al programador: `AsignacionService.esCita` es el único lugar).
- Marcas (no son estados): `servicio_subprocesos.devuelto`, `reprogramaciones` (contador), `iniciado`/`fecha_iniciado`;
  `servicios.candidato_desistio`. ASIGNADA_NO_REALIZADA y CANCELADA van al historial. `iniciado` se activa al guardar avance el responsable
  (`FormularioService.guardar`), al enviar el informe y al aprobar; un subproceso iniciado no se cancela.
- Motivos: tabla `motivos_subproceso` (REPROGRAMACION/CANCELACION/DEVOLUCION, «Otro» = `requiere_texto`), admin en Admin → Motivos;
  `MotivoSubprocesoService.resolver` los valida. Devolver y cancelar EXIGEN `idMotivo`.
- El gestor ya no cambia el estado del servicio a mano: `SolicitudService.validarTransicion` solo le deja publicar el estudio
  (servicio FINALIZADO) o cancelar. Cancelar la solicitud arrastra a los subprocesos y el desistimiento del candidato
  (`POST /gestor/solicitudes/{id}/desistimiento`) cancela lo no iniciado: ambos en `CancelacionSolicitudService` (aparte para evitar un
  ciclo con AsignacionService). Los dos controladores de cambio de estado pasan por ahí.
- PENDIENTE (otros cambios): SE-07 registra cita no realizada/reprogramar/reasignar (la máquina ya las define); `desasignar` y
  `marcarNoRealizado` (novedades) siguen fuera de la máquina; el registro de cambios con motivo es SE-03.

## Registro de cambios y fechas en hora Colombia (SE-03, V226, 2026-10-27)

- `registro_cambios_subproceso` (V226) es de SOLO INSERCIÓN (un disparador rechaza UPDATE/DELETE): una fila por cada cambio de estado
  o de marca de un subproceso, con servicio, subproceso, tipo, estado anterior/nuevo, marca, motivo, texto, responsable, usuario y la
  hora en America/Bogota (`fecha_bogota`, y `fecha_hora` con zona). Lo escribe `RegistroCambiosService.registrar` (Clock inyectado,
  `ZONA = America/Bogota`, nunca la zona del servidor) desde `AsignacionService` en cada transición; exige estar dentro de una
  transacción (`Propagation.MANDATORY`). Consulta (admin): `GET /api/v1/admin/registro-cambios?idServicio=|idSubproceso=`.
- V226 migró 181 de los 199 hechos de `historial_asignaciones` (REASIGNADO no cambia estado ni marca). El estado anterior de ASIGNADO,
  DESASIGNADO y CANCELADO se RECONSTRUYÓ (`origen = 'MIGRADO'`); la hora se tomó como hora de Colombia. Reporte:
  `migracion_registro_cambios_reporte`. NO se registran: la reasignación de responsable, ni el cambio de estado del SERVICIO (eso sigue en
  `historial_estados_servicio`).
- Fechas en el frontend: un `LocalDate` ("2026-10-26") NO se pasa a `new Date(...)` (lo toma como medianoche UTC y en Colombia sale el día
  anterior). Usar `parseFecha` de `utils/fechas.js` (fecha sola = mediodía local; con hora o zona, igual que antes).

## Semáforo de estado como filtro (SE-04, 2026-10-27)

- UN solo cálculo para todos los perfiles: `shared/estados/SemaforoEstados` (puro: conteo, pertenencia, avance, etiqueta de publicación,
  estados visibles por perfil), `servicios/service/ConsultaSemaforo` (puro: arma el JPQL y el alcance por perfil) y
  `SemaforoEstadoService`. Endpoints `GET /api/v1/semaforo` (contadores) y `GET /api/v1/semaforo/servicios` (lista filtrada), con los
  mismos filtros: tiempo + cliente + responsable + rol + tipo de subproceso (cliente y responsable solo para gestor/admin). Frontend:
  `services/semaforoService.js`.
- El semáforo es un FILTRO: un servicio pertenece al estado E si al menos un subproceso suyo está en E; cada estado cuenta servicios
  distintos (y aparte subprocesos); la suma de estados puede superar el total (= cantidad de servicios). Avance = publicados ÷ no
  cancelados; «Publicado parcial» / «Estudio completo publicado». Cliente: su empresa; gestor: clientes que atiende; admin: todo;
  analista/poligrafista/visitador cuentan SUS subprocesos asignados y el programador los que coordina (`programadorResponsable`), solo en
  los estados de su perfil (`SemaforoEstados.estadosVisibles`).
- Prueba de punta a punta con los seis servicios del cliente «QA Semáforo» (#40–#45): `SemaforoEstadoServiceTest` (H2) y `SemaforoEstadosTest`.
- PENDIENTE (SE-09): las pantallas todavía usan sus contadores viejos (`GestorService`, `DashboardClienteService`, `contarPorEstado`...);
  se borran cuando cada pantalla pase a este endpoint. Un servicio sin subprocesos no entra al total.

## Semáforo de tiempo — ajustes (SE-05, V227, 2026-10-27)

- Pausa por candidato SOLO en servicios que necesitan su hoja de vida: `procesos.requiere_hoja_vida` (V227; Antecedentes = false; casilla
  en Admin → Catálogo → proceso). `SolicitudService` y `TiempoHistoricoInicializador` solo abren la pausa si el proceso la necesita. V227
  dejó en 0 días (fin = inicio, sin borrar) las pausas de hoja de vida de los procesos que no la necesitan (`migracion_pausas_sin_hoja_vida_reporte`).
- Pausa por reprogramación: `OrigenPausa.REPROGRAMACION`. Mientras algún subproceso del servicio esté en REPROGRAMADO el reloj se pausa;
  `SemaforoTiempoService.sincronizarPausaPorReprogramacion` abre/cierra la pausa y la llama `AsignacionService.recalcularEstadoServicio`
  tras cualquier cambio de estado de un subproceso (SE-07 hará que existan subprocesos REPROGRAMADO). La pausa corre la fecha los días
  hábiles que duró; nunca se recalcula desde hoy.
- ENTREGA: el servicio está entregado cuando el estudio queda COMPLETO PUBLICADO (todos los subprocesos no cancelados PUBLICADO) o cuando
  el gestor lo publica; la fecha real es la del último subproceso publicado. Mientras haya subprocesos en FINALIZADO (o en otro estado)
  el reloj sigue corriendo. Al entregarse se congelan cumplimiento y retraso.
- Una sola cifra: `AsignacionResponse.tiempo` (mismo `TiempoServicio` del cliente). `MisAsignaciones`, ambas `Agenda` y el panel del
  analista muestran el chip `EstadoTiempo` ("Vencida · N días hábiles"); se quitaron los «hace N días» en días calendario. Pendiente SE-08:
  «Atrasado en agenda» (la hora de la cita ya pasó) en días hábiles.
- Al arrancar, `TiempoHistoricoInicializador.reajustarPorReglasVigentes` recalcula las abiertas y las entregadas sin resultado y deja un
  CSV de lo que cambió en `{uploads}/reportes/tiempo-reajuste-*.csv`. Una pausa abierta por espera del candidato (p. ej. #1, #3, #4:
  Visita Domiciliaria) hace crecer el compromiso un día hábil por cada día hábil que pasa; se congela cuando el candidato envía su hoja de vida.

## Riesgo «Sin calificar» (SE-06, 2026-10-27)

- Mientras un subproceso no tenga puntaje CALCULADO se muestra «Sin calificar», nunca «BAJO 0» ni «Riesgo bajo · 0,0».
  `CalificacionRiesgoService.motivoSinCalcular(sp)` es la única regla: con calificación por hallazgos hace falta que el cálculo del formulario
  haya corrido (`CalculoFormulario.resultado != null`, p. ej. con la criticidad del cargo elegida; si no, el motivo es el `aviso`, «Falta la
  criticidad del cargo.»); con campos que suman puntos, que el formulario tenga algo guardado (un subproceso terminado ya lo tiene).
  `ResumenSubproceso` trae `calculado` y `motivoSinCalcular`; sin cálculo, `nivel` y `puntaje` van null. Un puntaje 0 CALCULADO sí es real (BAJO).
- El estudio no cuenta como REALIZADO un subproceso terminado sin puntaje (queda PENDIENTE para el motor → el estudio sigue EN_PROCESO y el
  cliente no ve resultado). `RevisionRiesgoSinCalcular` recalcula al arrancar los estudios que habían quedado CALCULADO con 0 y no están
  bloqueados por una publicación.
- Frontend: `SinCalificar` / `ChipNivel(nivel=null)` en `components/RiesgoServicio.jsx`; lo usan el encabezado del formulario
  (`FranjaRiesgo`), Mis asignaciones (Por revisar / Aprobadas), la vista del gestor y `SolicitudDetalle` (cliente).

## Citas: confirmación, cita no realizada y reprogramación (SE-07, V228, 2026-10-27)

- `gestor/service/CitaSubprocesoService` (+ `CitaSubprocesoController`, `/api/v1/gestor/asignaciones/...`): `iniciar-informe` (el
  profesional confirma que la cita se realizó → subproceso «iniciado», sigue EN_EJECUCION), `evaluado-no-ha-llegado` (aviso al programador, uno
  por cita), `cita-no-realizada` (EN_EJECUCION → REPROGRAMADO, conserva al profesional, pausa el reloj del servicio vía
  `recalcularEstadoServicio` → `sincronizarPausaPorReprogramacion`), `reprogramar-cita` (mismo profesional, REPROGRAMADO → EN_EJECUCION con la marca
  REPROGRAMADA y `reprogramaciones` +1) y `reasignar-cita` (otro profesional). Todo con motivo de la lista REPROGRAMACION y registro en
  `registro_cambios_subproceso`. Reglas de tiempo (puras, hora de Colombia): `shared/estados/ReglasCita` — tolerancia de 15 min.
- Motivos con regla propia por CÓDIGO (`motivos_subproceso.codigo`, V228): NO_SE_PRESENTO (solo pasados los 15 min) y AVISO_NO_ASISTIRA (lo
  registra el programador o el gestor, no el profesional). El administrador puede renombrarlos sin romper la regla.
- Reasignar NO crea un subproceso nuevo: la cita sigue en el mismo `servicio_subprocesos` con el nuevo profesional, y la asignación del
  primero queda en `historial_asignaciones` (REASIGNADO, «Asignada – no realizada») y en el registro de cambios con la marca
  ASIGNADA_NO_REALIZADA y el primer profesional como responsable. (Así el servicio no cuenta dos subprocesos del mismo tipo ni se pesa «No
  realizado» en el riesgo.) Interpretación mía del spec: confirmar.
- Una cita EN_EJECUCION o REPROGRAMADO ya NO se edita a mano: `asignar`, `asignarMasivo` y `desasignar` la rechazan (409) —
  `AsignacionService.exigirCitaNoAgendada`. `reprogramar` por novedad (`NovedadSubprocesoService`) sigue igual.
- Bandeja «Por confirmar»: cita EN_EJECUCION, sin iniciar, 15 min después de su hora (programador: las que coordina; profesional: las suyas;
  gestor: las de sus clientes). `AsignacionResponse` trae `porConfirmar`, `noHaLlegadoDisponible`, `noSePresentoDisponible`, `evaluadoAvisado`
  calculados en el servidor. `CitasPorConfirmarTareas` avisa UNA vez por cita cada minuto (`aviso_por_confirmar_en`). Frontend:
  `components/citas/BandejaCitas.jsx` y `ModalCitaAccion.jsx` (Mis asignaciones del profesional y Agenda del programador).
- Decidido 2026-10-28: el programador sigue pudiendo aprobar/devolver/cancelar sus citas; VerifEye se queda con rol analista. Desplazamiento entre visitas sigue en 0 min (falta el dato).

## Agenda: atrasado en agenda, disponibilidad y cronograma (SE-08, 2026-10-27)

- «Atrasado en agenda» (`shared/estados/ReglasAgenda.atrasadoEnAgenda`): subproceso EN_EJECUCION cuyo DÍA programado ya pasó sin hacerse; se
  cuenta en días hábiles (`AsignacionResponse.atrasadoAgenda` / `diasHabilesAtrasoAgenda`). Es otra cosa que «Vencida» (la entrega al cliente).
  Bandeja «Para reprogramar» (`GET /gestor/agenda/para-reprogramar`, componente `BandejaParaReprogramar`): el gestor ve el análisis de sus clientes
  (acción «Reprogramar» = abre la asignación); el programador, las citas que coordina (acción «Registrar cita no realizada», SE-07). No confundir con
  «Por reprogramar» (SE-07: subprocesos ya en REPROGRAMADO).
- Orden por defecto de las listas de la Agenda: `servicio.fechaCompromisoAjustada` ascendente (lo que vence antes al cliente primero), y cada
  fila trae el estado de tiempo del servicio (SE-05) además de la fecha de la cita.
- Disponibilidad rehecha (`components/agenda/DisponibilidadEquipo.jsx`, `AgendaEquipoService`, `/gestor/agenda/equipo[/{id}/dia]`): «Equipo hoy» lista a
  TODAS las personas activas del rol (el bug era que el selector solo traía las vistas esta semana: «el catálogo de empleados no está expuesto a
  Programador»), con horas ocupadas/libres/bloqueadas de la jornada y atrasados, filtro por rol; al elegir a alguien, su día por bloques (ocupado con
  subproceso·evaluado·servicio·duración del catálogo, libre con «Asignar aquí», bloqueado). El gestor ve el detalle solo de lo suyo (lo de otro gestor
  sale como «Ocupado»). Gestor: analistas; programador: poligrafistas y visitadores; admin: los tres.
- Jornada 7:00 a. m. – 6:00 p. m. (`ReglasAgenda.JORNADA_*`, antes 8–17), almuerzo 12:00–13:00 bloqueado (tampoco se ofrece al asignar:
  `AsignacionService.franjasDisponibles`). Desplazamiento: `ReglasAgenda.DESPLAZAMIENTO_MINUTOS = 0` — la especificación pide bloquearlo pero no dice cuánto;
  con N > 0 se bloquea N min después de cada visita (VISITADOR). PENDIENTE de decisión del usuario.
- Cronograma: sábados, domingos y festivos se marcan «No hábil» (`GET /gestor/agenda/dias-no-habiles`, hook `useDiasNoHabiles`) y el título del día sale
  «lunes, 26 de octubre». Si hay trabajo programado en un día no hábil igual se muestra.

## Pantallas con un solo vocabulario y un solo semáforo (SE-09, 2026-10-27)

- **Un solo componente**: `components/semaforo/SemaforoEstados.jsx` (tarjetas por estado, orden y colores del catálogo, conteo del servidor
  `GET /api/v1/semaforo`, sin porcentajes ni barra que sume 100 %, clic = filtrar) y `AlertasAparte.jsx` (Sin asignar, Novedades abiertas, Citas de
  hoy, Vencidas, Para hoy, Por confirmar; debajo, otro estilo; `GET /api/v1/semaforo/alertas`, una alerta que no aplica al perfil viene `null`).
  Regla pura de las alertas: `shared/estados/AlertasAgenda` (`AlertasAgendaTest`). `EtiquetaEstado.jsx` + `hooks/useEstadosSubproceso` pintan el
  estado con el nombre/color del catálogo (`GET /catalogo/estados-subproceso`): nunca el código interno. `hooks/useSemaforoSerie` da la serie de los
  tableros desde el mismo resumen.
- **Listas por pertenencia**: cliente (`SolicitudService.listarSolicitudes`), gestor (`GestorService.listarSolicitudes`) y admin usan
  `ServicioSpecifications.conSubprocesoEn(estado)` (EXISTS) — un servicio sale en cada estado donde tenga un subproceso. Se borraron las consultas
  por «bucket» de `ServicioRepository` y `ServicioRepositoryMultiEstadoTest`. Cada fila trae `resumen` (subprocesos por estado), `publicados`,
  `vigentes` y `etiquetaPublicacion`, calculados en el servidor (`ServicioSemaforoFila.ResumenEstado.de`).
- Pantallas cambiadas: cliente Mis solicitudes, gestor Solicitudes, Agenda del gestor y del programador, Mis asignaciones (analista, poligrafista,
  visitador; el filtro de urgentes dice «Entrega vencida o en 1 día hábil»), tableros de gestor, programador y ejecutores, y el detalle del cliente
  (siete estados por subproceso).
- Documentos del candidato por revisar/devueltos se cuentan una vez por servicio (`AsignacionService.contarIndicador`).
- PENDIENTE: los endpoints `/conteo` de asignaciones y solicitudes siguen vivos porque aún los leen los tableros (tarjetas «atención» y gráficas);
  retirarlos cuando esas piezas pasen al resumen del semáforo. `SemaforoServicios.jsx` y `AdminDashboard.jsx` del administrador no se tocaron.

## Notificaciones del semáforo (SE-10, 2026-10-27)

- Cada aviso nace UNA vez por evento, dentro de la transacción del cambio, con fechas legibles (`FechasLegibles.larga`: «27 de octubre de 2026»). Los
  textos nuevos están en `shared/estados/TextosAviso` (puro, `TextosAvisoTest`). Mapa: subproceso publicado → cliente (`SUBPROCESO_PUBLICADO_CLIENTE`,
  `AsignacionService.avisarPublicadoAlCliente`; si queda todo el estudio publicado dice «Tu estudio está completo», si no «puedes descargar ese informe
  sin esperar»); cita no realizada → cliente, programador y profesional (`CITA_REPROGRAMANDO`/`CITA_REPROGRAMADO`; con el motivo AVISO_NO_ASISTIRA el profesional
  recibe «El evaluado no asistirá»); cita reprogramada con nueva fecha → cliente y profesional (`CITA_REPROGRAMADA`); el evaluado no ha llegado → programador;
  cita por confirmar → programador y profesional (una por cita, `aviso_por_confirmar_en`); devuelto → responsable con motivo y observación (`SUBPROCESO_DEVUELTO`);
  solicitud por vencer / vencida → gestor y cliente (los administradores solo vencida), una por cambio de estado de tiempo (`NotificacionesTiempoService`).
- Un fallo al avisar nunca revierte la acción (try/catch con log). El aviso al cliente no lleva referencia: en la campana abre `/cliente/solicitudes`.

## Reporte diario de productividad (SE-11, 2026-10-27)

- `shared/estados/ProductividadDiaria` (pura, `ProductividadDiariaTest`) calcula desde `registro_cambios_subproceso`, NO desde el estado actual; el día corta a las
  11:59 p. m. hora de Colombia (todo lo anterior a la medianoche siguiente). Por persona y día: asignados (entradas a EN_EJECUCION, literal: una devolución o
  reprogramación que vuelve a EN_EJECUCION también cuenta), finalizados, publicados, devueltos (marca DEVUELTO), reprogramados por motivo (solo programador,
  poligrafista, visitador) y abiertos al cierre. Al programador se le atribuyen las citas que coordina. Por gestor y día: por verificar al cierre, servicios por
  estado (regla SE-04), estudios completos publicados en el día y vencidos al cierre (hoy: estado de tiempo guardado; días pasados: APROXIMADO con la fecha de
  compromiso actual, marcado en pantalla y Excel).
- `ProductividadService` (alcance: admin todo, gestor sus clientes, ejecutores lo suyo; máx. 93 días), `GET /api/v1/reportes/productividad` y `/exportar` (Excel, POI,
  `ProductividadExcel`). Filtros: fecha o rango, perfil, persona, tipo de subproceso y cliente. Pantalla `pages/shared/Productividad.jsx`, ítem «Productividad» en todos los menús.

## Nueva solicitud — NS-01: un solo cálculo del saldo prepago (2026-10-28)

- `pagos/service/BolsaPrepagoService` es el ÚNICO cálculo de la bolsa: `disponible = comprado − consumido − reservado` (reservado = 0 hasta que exista un flujo que
  lo use). Dashboard, Comprar servicios (`SaldoServicioClienteService.obtenerSaldos`), Gestión de cuentas (`ClienteAdminService`, incl. el bloqueo al pasar a pospago)
  y Nueva solicitud (`validarDisponibilidad`/`descontar`) leen de ahí. El contador guardado `cantidad_disponible` ya NO se usa para mostrar ni validar.
  Una compra que no está APROBADA no suma (`acreditar` solo se llama al aprobar la orden).
- Reporte previo a aplicar (solo lectura): `GET /api/v1/admin/bolsa-prepago/diferencias` compara lo guardado con las compras APROBADAS; una diferencia puede ser legítima
  (una nota crédito total quita unidades de lo guardado). No corrige nada. `BolsaPrepagoServiceTest`.
- PENDIENTE (NS-02/NS-03): lotes por compra, descuento transaccional con bloqueo e idempotencia.

## Nueva solicitud — NS-02: lotes por compra (V229, 2026-10-28)

- Cada ítem de una orden APROBADA es un lote (`lotes_prepago`, entidad `LotePrepago`): cantidad comprada/consumida, `valor_unitario` y `valor_original` de ESA compra (no cambian
  nunca). `SaldoServicioClienteService.acreditar(..., item)` lo crea al aprobar la orden (idempotente por `id_item`). Descontar es FIFO (`AsignadorLotes.siguiente`: el lote
  más antiguo con unidades) y devuelve `UnidadDescontada(idLote, valorUnitario)`; `SolicitudService` lo guarda en la solicitud (`servicios.id_lote_prepago`,
  `valor_unitario_prepago`). El cobro que ve el cliente (`SolicitudService.cobro`) usa ese valor guardado, no la última compra; una solicitud sin lote conserva el comportamiento anterior.
- Los contadores de `saldos_servicio_cliente` siguen siendo la fuente del disponible (NS-01); los lotes dan precio y trazabilidad. Una unidad sin lote que la respalde se descuenta
  igual, con lote y valor null. La nota crédito total (`NotaAjusteService.revertirSaldoOrden`) también resta del lote de esa compra y se rechaza (409) si ya se usaron unidades de él.
- V229 reconstruye los lotes de lo existente (un lote por ítem aprobado; lo consumido repartido FIFO; las solicitudes de clientes PREPAGO ligadas en orden de creación) y deja el
  reporte en `migracion_lotes_prepago_reporte` (tipos LOTE, CONSUMO_SIN_COMPRA, COMPRA_SIN_SALDO, SERVICIO_SIN_LOTE). No borra nada. Revisar el reporte antes de producción.
- PENDIENTE (NS-03/NS-04): descuento con bloqueo e idempotencia; devolver la unidad al cancelar (al mismo lote). `LotesPrepagoTest`.

## Nueva solicitud — NS-03: descuento al enviar, sin doble cobro (V230, 2026-10-28)

- El descuento prepago es UNA sentencia atómica con la condición de saldo (`SaldoServicioClienteRepository.descontarUna`: `comprada − consumida >= 1`); dos envíos a la vez
  del mismo cliente y servicio se hacen en fila en la base y el segundo recibe 0 filas → 400 «No tienes saldo». Los lotes se leen con bloqueo (`LotePrepagoRepository.bloquearPorClienteYProceso`).
  Todo ocurre en la transacción del envío: si algo falla después, el descuento se revierte solo.
- Idempotencia: cabecera `Idempotency-Key` en `POST /api/v1/requests` (`solicitudes_idempotencia`, único por usuario+clave). Misma llave después de un envío terminado → devuelve lo creado
  sin cobrar; envío simultáneo o aún en curso → 409. Un envío que falla no consume la llave. La pantalla Nueva solicitud genera una llave por carga (`claveEnvio`). La carga masiva no usa llave.
- `SolicitudIdempotenciaTest`, `LotesPrepagoTest`. PENDIENTE: la devolución al cancelar es NS-04.

## Nueva solicitud — NS-04: cancelar devuelve lo que costó (V231, 2026-10-28)

- `pagos/service/DevolucionCobroService`: al pasar la solicitud a CANCELADO (`SolicitudService.cambiarEstado`, también cliente, gestor, admin y desistimiento) se devuelve el cobro UNA vez
  (`servicios.cobro_devuelto_en`). Prepago: la unidad vuelve a la bolsa (`SaldoServicioClienteRepository.devolverUna`) y al MISMO lote (`servicios.id_lote_prepago`); sin lote, solo a la bolsa.
  Pospago: el cargo se anula (`movimientos_credito_pospago.anulado_en`; las consultas de facturación y «pendientes de facturar» excluyen los anulados) y el cupo vuelve. Un cargo pospago ya
  facturado NO se toca (hace falta una nota crédito) y queda sin marcar. Si el administrador aprueba revertir la cancelación (`ReversionSolicitudService.aprobar`) se vuelve a cobrar (`volverACobrar`);
  sin saldo/cupo, 409.
- El cliente ve «Cancelada · no se factura» (`CobroResponse.estadoPago = CANCELADA`, total 0) en el detalle de su solicitud.
- V231 corrige las ya canceladas sin devolver (p. ej. la #39 de Acme sas, $40.000) y deja `migracion_devolucion_cobro_reporte` (UNIDAD_DEVUELTA, UNIDAD_DEVUELTA_SIN_LOTE, CUPO_DEVUELTO, YA_FACTURADO).
  No borra nada. `DevolucionCobroServiceTest`. NO cubierto: el recargo por ciudad pendiente de una solicitud cancelada sigue como estaba.

## Nueva solicitud — NS-05: la mora sale de las facturas (2026-10-28)

- `pagos/mora/MoraCliente` (pura, `MoraClienteTest`): un cliente está en mora si tiene una factura sin pagar cuyo vencimiento ya pasó (la que vence hoy no cuenta; fecha de hoy en Colombia).
  Pagar una de dos facturas vencidas NO lo saca de la mora. Es también la ÚNICA fuente del mensaje («Tu cuenta está en mora: tienes 2 facturas vencidas por $1.200.000. La más antigua, F-1, venció hace 30 días. Págalas…»).
- `pagos/service/MoraService.calcular(idCliente)` lo lee de las facturas; `sincronizar` deja `clientes_pospago.estado_mora/dias_mora` igual a lo calculado (lo usan el job de vencimiento y
  `PagoAplicacionService.levantarRestriccionPorDeuda`); una SUSPENSIÓN del administrador no se toca y sigue bloqueando. Una marca EN_MORA puesta a mano ya no bloquea: manda lo calculado.
- `SolicitudService.crearSolicitud` rechaza (403) con ese mensaje a CUALQUIER cliente con facturas vencidas (prepago también). `GET /api/v1/requests/mora` devuelve el mismo resultado y Nueva solicitud
  muestra el aviso y bloquea «Confirmar y enviar». El rediseño del paso 1 (NS-09/NS-10) reutiliza este endpoint.

## Nueva solicitud — NS-06: fecha estimada de entrega desde el servidor (2026-10-28)

- La fecha que ve el cliente en Nueva solicitud ya NO se calcula en el navegador (antes: domingo no contaba, sin festivos, hora del PC). `solicitudes/service/EntregaEstimadaService` usa el MISMO cálculo con el
  que se crea la solicitud (`TiempoServicioService.datosDeCreacion`: días hábiles de Colombia con festivos, hora de corte y hora de Bogotá) y `GET /api/v1/requests/entrega-estimada?procesos=1,2` la devuelve ya
  escrita (`texto` = «miércoles, 28 de octubre»), con el plazo mayor de los servicios elegidos. Se muestra en el panel lateral y en la confirmación; se vuelve a pedir al llegar a la confirmación.
- Pausa por hoja de vida: si algún servicio elegido tiene `procesos.requiere_hoja_vida` (misma regla con la que `SolicitudService` abre la pausa) la `nota` dice que el tiempo corre desde que el evaluado envíe su
  hoja de vida y que cada día hábil de espera mueve la fecha. Si llegó después del corte, la nota dice desde qué día cuenta como recibida. `EntregaEstimadaServiceTest`.
- Fechas sin correr el día: el gestor ya usa `parseFecha`; las fechas del cliente se arman con `T00:00:00`/`T12:00:00` local, sin pasar por UTC (verificado, no cambió nada).

## Nueva solicitud — NS-07: datos del evaluado obligatorios y criticidad del cargo (2026-10-28)

- Una sola regla: `solicitudes/validacion/DatosEvaluadoValidador` (pura, `DatosEvaluadoValidadorTest`). Obligatorios: cédula numérica (5–15 dígitos), nombres, apellidos, cargo, `criticidadCargo`
  (ESTANDAR/SENSIBLE/CRITICO), celular (10 dígitos, empieza por 3), correo con formato válido y ciudad. `SolicitudService.crearSolicitud(request, email, clave)` la corre ANTES de la llave de idempotencia y responde
  400 con un texto que nombra cada campo que falla. Espejo en el navegador: `utils/validarEvaluado.js` (el servidor manda). La carga masiva (`crearSolicitud(request, email)` de 2 argumentos) NO la usa todavía: NS-12.
- La criticidad se guarda en `servicios.criticidad_cargo` (columna que ya existía; sin migración). Las solicitudes anteriores quedan sin criticidad (no se rellena nada) para que el gestor la defina (pestaña Riesgo o el campo del formulario).
- Motor: `CalculoSubservicioService.datosDelEstudio` pasa `criticidad_solicitud` y `calcular` la pone en el campo de criticidad de la plantilla (uso CRITICIDAD), así que manda sobre lo que haya en el formulario y desaparece el
  aviso «Falta la criticidad del cargo» en las solicitudes nuevas. El formulario (`FormularioService.obtener`) precarga el campo; solo gestor/admin lo cambian (`guardar` ignora el valor de los demás) y el cambio actualiza la
  solicitud (`servicios.usuario_criticidad` + log), no deja una segunda copia. `FormularioResponse.criticidadDeSolicitud` bloquea el campo en `Formulario.jsx` para analista, poligrafista y visitador.

## Nueva solicitud — NS-08: campos del evaluado según el servicio (V232, 2026-10-28)

- Dos atributos por servicio en el catálogo (`procesos.requiere_direccion`, `procesos.ciudad_es_de_prueba`; casillas en Admin → Catálogo → servicio, viajan en `ProcesoPublicoResponse`). V232 los deduce de los subprocesos: visita
  (rol VISITADOR o nombre «visita») → `requiere_direccion`; poligrafía/VerifEye (rol POLIGRAFISTA o nombre) → `ciudad_es_de_prueba`. Reporte de lo marcado: `migracion_atributos_proceso_reporte`. Revisar antes de producción.
- Nueva solicitud: con `requiereDireccion` aparece «Dirección de la visita» (obligatoria, `servicios.direccion_visita`, V232; `DatosEvaluadoValidador.errores(request, pideDireccion)` en el servidor y `validarEvaluado(e, pideDireccion)` en el
  navegador) y la ciudad se rotula «Ciudad de la visita»; con `ciudadEsDePrueba`, «Ciudad donde presentará la prueba»; con ambos, «Ciudad de la visita y de la prueba» (`rotuloCiudad`). Antecedentes y similares: solo «Ciudad».
- La ciudad se elige solo entre las confirmadas: `CiudadSelect soloConfirmadas` (sin «+ No encuentro mi ciudad») y el servidor rechaza (400) una pendiente de confirmación. La dirección escrita es la de la cita:
  `PortalCandidatoService.direccionDeVisita` la prefiere sobre la del candidato (portal del candidato y gestor). No se muestra aún en la ficha del visitador ni en el Word (pendiente).

## Nueva solicitud — NS-09: el servicio es el paso 1 y la bolsa prepago se ve (2026-10-28)

- Orden de pasos (ya vigente desde V223): 1 Servicio → 2 Datos del evaluado → 3 Confirmar y enviar; todo vive en el estado de `NewRequest.jsx`, así que al volver atrás se conserva lo escrito. Solo cambiar de servicio borra lo extra de la poligrafía.
- Prepago, paso 1: encabezado «Bolsa prepago: N servicios disponibles» (suma de `GET /client/service-packages` = `BolsaPrepagoService`, NS-01) y una tarjeta por cada servicio comprado alguna vez, con «N disponibles» y «Usados X de Y»,
  el plazo en días hábiles y SIN precio. Los de 0 disponibles salen «Agotado», no se pueden elegir (`toggleServicio` lo ignora) y traen «Comprar más» → `/cliente/comprar-servicios?idProceso=` (ese parámetro ya agrega el servicio al carrito).
  Debajo, «¿Necesitas otro servicio? Ver catálogo completo y comprar». El contador es informativo: el servidor vuelve a validar el saldo al enviar (NS-03). Pospago conserva el precio en la tarjeta hasta NS-10.
- La cuenta de Carlos (52 = 34 + 18, con agotados) está cubierta en `BolsaPrepagoServiceTest.porServicioYTotales`. El frontend no tiene corredor de pruebas: el orden de pasos y los agotados se verifican a mano.

## Nueva solicitud — NS-10: paso 1 en pospago (2026-10-28)

- Encabezado «Cupo de crédito: $disponible de $total» con el MISMO endpoint de Mi Crédito (`pospagoService.obtenerEstadoCuenta`: `creditoDisponible` y `limiteCredito`), así nunca difiere. Sin cupo asignado (403) se muestra el mensaje del servidor.
- Cada tarjeta muestra el precio del servicio (`procesos.valor`; hoy no hay precios por cliente) y el plazo en días hábiles, por categoría. Si el precio supera el cupo disponible, o el servicio no tiene precio, la tarjeta lo dice y no se puede elegir
  (`bloqueo` SIN_CUPO / SIN_PRECIO; el recargo por ciudad se conoce hasta el paso 2 y el servidor valida el total al enviar: `ClientePospagoService.validarCreditoDisponible`).
- Mora (NS-05): el recuadro rojo con la factura vencida está arriba en los tres pasos (se puede preparar la solicitud) y en el paso 3 el botón dice «Enviar · bloqueado por factura vencida» y queda deshabilitado; el servidor también rechaza.

## Nueva solicitud — NS-11: resumen del paso 3 desde el servidor (2026-10-28)

- `GET /api/v1/requests/resumen-cobro?idProceso=&idCiudad=` (`ResumenCobroService`, `ResumenCobroServiceTest`) dice qué se usa y qué queda; la pantalla (`BloqueCosto`) solo lo muestra. Prepago: «Se usará 1 {servicio} de tu bolsa · Disponibles ahora: n · Quedarán: n−1»
  con `BolsaPrepagoService.disponible` (NS-01); ya no sale «Ya tienes N unidades». Pospago: «Valor del servicio: $ · Cupo disponible: $ · Quedará: $ · Queda sin facturar hasta tu próximo corte»; el cargo es valor + recargo por ciudad
  (igual que `ClientePospagoService.descontarCredito`) y, si no alcanza, el mensaje lo dice y «Confirmar y enviar» se deshabilita. En prepago el recargo por ciudad solo se informa (queda pendiente para la próxima compra).
- El resto del resumen (servicio y categoría, evaluado con cédula/cargo/criticidad/celular/correo, entrega estimada con su plazo) ya estaba en el paso 3 (NS-06/NS-07). El servidor sigue validando saldo/cupo al enviar (NS-03).

## Nueva solicitud — NS-12: carga masiva con las mismas reglas, todo o nada (2026-10-28)

- Columnas (por NOMBRE de encabezado, no por posición): cedula, nombres, apellidos, cargo, criticidad_cargo, celular, email, ciudad, direccion, servicio. Se quitaron NIT y razón social (el cliente sale de la sesión); la plantilla
  descargable trae las nuevas y una hoja «Instrucciones». Un archivo viejo, sin esas columnas, se rechaza entero diciendo cuáles faltan.
- `CargaMasivaValidador` revisa TODO el archivo antes de crear nada (sin escribir): datos del evaluado y criticidad (`DatosEvaluadoValidador`, NS-07), dirección si el servicio incluye visita (NS-08), ciudad confirmada (acepta «Ciudad, Departamento» y sin tildes),
  duplicados dentro del archivo y de los últimos 3 meses, servicios con formato adicional de poligrafía (esos se piden desde Nueva solicitud), mora (una fila 0 = todo el archivo) y lo que depende del conjunto: saldo prepago por servicio sumando
  todas las filas, o cupo pospago acumulado en el orden del archivo (precio + recargo por ciudad). Si algo falla: no se crea nada y `BulkUploadResponse` trae `errores` por fila con el motivo (más `mensaje`).
- Si todo pasa, `SolicitudService.crearLote` crea todas en UNA transacción (cada una por el mismo camino que Nueva solicitud: lote y valor de NS-02, descuento atómico de NS-03, fecha de NS-06); si algo falla ya con todo validado, tampoco queda nada a medias.
  No se implementó la opción «crear solo las válidas con confirmación». `CargaMasivaValidadorTest`.

## Poligrafía Pre Empleo — proveedor externo con enlace (PF-09, V241, 2026-11-03)

- El programador (responsable de la cita) o el admin genera un enlace para que un proveedor externo diligencie UNA evaluación de poligrafía sin usuario ni contraseña: Agenda → menú de la cita → «Enlace para proveedor externo»
  (`components/citas/ModalEnlaceProveedor.jsx`; solo poligrafista asignado y subproceso EN_EJECUCION). Endpoints `POST|GET|DELETE /api/v1/gestor/asignaciones/{id}/enlace-proveedor` (`EnlaceProveedorController`).
- Tabla `enlaces_proveedor`: solo se guarda el SHA-256 del token (256 bits, base64url); el enlace completo se ve UNA vez al generarlo. Índice único parcial `ux_enlaces_proveedor_vigente`: un solo enlace vigente por evaluación
  (generar otro cierra el anterior como REEMPLAZADO). Vence al ENVIAR el informe (`AsignacionService.completar` → `EnlaceProveedorService.cerrarAlEnviar`, motivo ENVIADO) o a las 72 h; el programador lo revoca. Historial: quién lo generó,
  cuándo, primer/último uso y número de ingresos. «Un solo uso» = un enlace por evaluación hasta enviar el informe, no uno que se invalida al abrirse (permite volver a entrar si se corta la señal).
- Seguridad: `security/EnlaceProveedorFilter` (antes del filtro JWT) lee la cabecera `X-Acceso-Proveedor` y actúa COMO el poligrafista asignado, pero solo en las rutas de `ReglasEnlaceProveedor.rutaPermitida` (formulario, ficha, foto,
  PDF y completar de ESA evaluación; frases estándar; `/api/v1/proveedor/sesion`; trabajos). Otra ruta o evaluación = 403 `ENLACE_RUTA_NO_PERMITIDA`; enlace inválido = 401 con `codigo` (`ENLACE_ENVIADO|VENCIDO|REVOCADO|REEMPLAZADO|NO_DISPONIBLE|DESCONOCIDO`);
  20 fallos por IP y minuto = 429 (en memoria, por nodo). Rechaza rutas disfrazadas (`..`, `%`, `;`, `//`, ceros a la izquierda). Una ruta nueva que el proveedor necesite se agrega EXPLÍCITAMENTE a esa lista. `EnlaceProveedorService` es opcional en
  `AsignacionService` (`required = false`) para no romper las pruebas armadas a mano. Pruebas `ReglasEnlaceProveedorTest`, `EnlaceProveedorFilterTest`.
- Frontend: ruta pública `/proveedor/:token` (`pages/proveedor/ProveedorExterno.jsx`) guarda el token en sessionStorage (`services/accesoProveedor.js`); `services/api.js` manda la cabecera, quita `Authorization` y NO refresca ni redirige al login.
  Renderiza `FormatoPoligrafia` con la prop `proveedor`, sin «Mis Servicios» ni «Salir». Solo formato Pre Empleo; otro formato muestra un aviso.
- Celular (<768 px) con enlace: una pregunta por pantalla con Sí/No grandes y Anterior/Siguiente (`UnaPorPantalla`, `Botones grande`).
- Sin señal: cada cambio se escribe primero en IndexedDB (`services/colaOffline.js`, base `polygraph-proveedor`, clave `form:<id>`) y luego se sube en orden; se reintenta al volver la señal, cada 20 s y al reabrir el enlace (antes de leer el formulario).
  El servidor reemplaza el formulario completo: con dos dispositivos gana el último guardado. Enviar el informe EXIGE conexión.
- Límites conocidos: la foto/cédula no tiene cola sin conexión; el service worker no cachea el HTML, así que reabrir el enlace sin señal muestra «Sin conexión» (lo escrito sigue guardado); lo que guarda el proveedor queda a nombre del poligrafista
  asignado en el historial; si se reasigna la cita el enlace sigue al nuevo asignado; el token va en la URL. Sin pruebas de interfaz automáticas ni verificación en navegador real.

## Foto del evaluado y panel lateral — poligrafía (FA-01 a FA-03, 2026-11-03)

- Foto (FA-01/02): `FotoEvaluado.jsx` reduce en el navegador (recorte 3:4, 600×800, JPG, <300 KB) y sube `multipart` con `foto`, `cedulaVerificada` y `origen` (CAMARA|ARCHIVO, V242).
  `services/api.js` manda `Content-Type: application/json` por defecto: toda llamada con `FormData` debe fijar `'Content-Type': 'multipart/form-data'` o el servidor responde «Error interno»
  (esa fue la causa del 500 de la foto). `FotoEvaluadoService.tipoReal` valida la firma real (JPG/PNG); tope de 5 MB en el servicio (el límite global de multipart sigue en 10 MB porque lo comparten los
  documentos del candidato y las plantillas). Mensajes claros, nunca «Error interno»; «Reintentar» si falla la subida.
- Panel del caso (FA-03): `componentesHoja.jsx` exporta `useMasAngostoQue(px)`, `Tarjeta` (plegable; sin datos empieza plegada «· sin datos») y `PanelContexto`. Va en la columna izquierda debajo de los temas
  (sticky, con desplazamiento propio); con menos de 900 px: selector de temas + botón «Información del caso». Lo reutilizan la visita y antecedentes.

## Visita domiciliaria — formato por fases (VD-01 a VD-09, 2026-11-03)

- Pantalla del visitador: `pages/visitador/FormatoVisita.jsx` (el default `FormularioVisitador` pide el formulario y, si no trae las secciones de la visita, deja el `Formulario` genérico). 3 fases / 11 temas en
  `fasesVisita.js` (`FASES`, `partesDelTema`, `avanceDelTema`, `faltantesDeVisita`, `estadoDeTemas`). Mismos datos y mismo guardado que el formulario: las claves (`ps_*`, `dc_*`, `eco_*`, `df_*`…) no cambian,
  solo cómo se capturan. Una sola regla para temas, fases y faltantes: ✓ cuando no falta ningún obligatorio y hay algo diligenciado. Cada faltante lleva a su campo (atributo `data-clave` + resaltado).
- Datos generales (VD-02, `DatosGeneralesVisita.jsx`): tipo/clase/módulos en botones, SIN precarga desde la solicitud (la solicitud de visita no trae esos datos); cambiar lo ya elegido registra una novedad
  (V243: `VISITA_CAMBIO_CONFIGURACION`, `VISITA_CARGO_NO_COINCIDE`; `NovedadesSubprocesoResponse.TipoNovedadResponse` ahora trae `clave`). «Confirmado en la visita» nace con lo del link y «Resultado»
  (Coincide/Corregido) lo calcula la pantalla. La criticidad solo se muestra (viene de la solicitud).
- Módulos (V244): rutina, virtual, vehicular, OEA, teletrabajo y las fotos de los dos últimos son secciones OPCIONALES; apagar un módulo = «quitar sección» (un visitador no puede borrar filas de una sección no
  opcional: 403). Las fotos de vehicular/teletrabajo existen mientras su módulo principal esté activo. La rutina no tiene precarga (no hay fuente definida).
- Protocolo (VD-03, `ProtocoloVisita.jsx`, `PREGUNTAS_PROTOCOLO`): numeración continua 1–16; la pregunta 10 (sindical) está APAGADA (`NumeracionProtocolo.SINDICAL_ACTIVA = false` en el backend y `SINDICAL_ACTIVA`
  en `fasesVisita.js`; no existe interruptor de administrador). El Word usa `${campos['ps_N_num']}` y `${campos['ps_comparadas']}` (`NumeracionProtocolo.marcadores`); las claves `ps_N` no cambian.
- Indicadores y cálculos (VD-04/05, `IndicadoresVisita.jsx`, `calculosVisita.js`): porcentajes, conceptos, totales de economía y aporte del candidato se calculan EN LA PANTALLA con la regla de `motor_vis.py`
  (redondeo mitad-al-par: `redondear`) y se guardan en sus columnas de siempre; si otra pantalla edita el formulario no se recalculan. Los aportantes de cada egreso son filas en pantalla que se guardan como el
  texto «Madre $450.000; Candidato $450.000» en `eg_aportantes`. Montos (`in_monto`, `eg_monto`, `pf_valor`): `CampoDinero`. Los textos de la escala 1–4 (Deficiente/Aceptable/Bueno/Muy bueno) no están
  confirmados con el diccionario.
- Fotos (VD-07, `FotosVisita.jsx`, `utils/reducirImagen.js`): espacios con nombre (fila.etiqueta = espacio), cámara o archivo, reducción en el navegador (1600 px, JPG 0,85, <500 KB, sin EXIF/GPS, HEIC con
  `heic2any`), descripción obligatoria; «Quitar» vacía la foto (no borra la fila). El Word reduce las fotos viejas al generar (`ReductorImagen`, sin tocar el original). Los mínimos (3/2/20) solo los valida la pantalla.
- Revisión (VD-08/09): botón «Marcar como completado» (`etiquetaEnvio` de `RevisionEnvio`). El catálogo de referencias (Anexo 1) salió del Word del cliente (una sola plantilla, tipo de progreso 7); los soportes
  pasaron a ser el Anexo 1; «Ver catálogo de referencias» (`CatalogoReferencias.jsx`) es consulta interna. Hay que subir el Word nuevo en Admin → Catálogo → Plantilla en cada ambiente.

## Antecedentes legales — formato por fases (AN-01 a AN-09, 2026-11-03/04)

- Pantalla del analista: `pages/analista/FormatoAntecedentes.jsx` (el default `FormularioAnalista` deja el `Formulario` genérico si el formulario no trae las secciones de antecedentes). 3 fases / 9 temas en
  `fasesAntecedentes.js` (cada tema con `vista` propia cuando tiene componente a medida). Mismas claves y mismo guardado; la criticidad sigue en la plantilla pero oculta (el cálculo la lee de la solicitud).
- Fase 1 (AN-02, `TemasFase1.jsx`, V245): el lugar de expedición viene del link (`campo_candidato = CIUDAD_EXPEDICION`); la declaración (3 preguntas) se precarga del link en `decN_respuesta/_detalle` (si el evaluado
  no respondió: botones Sí/No). Tipo de cargo y conducción se SUGIEREN por palabras clave del cargo (`sugerenciasCargo.js`), nunca se marcan solos. «Corregir y avisar al gestor» crea una novedad
  (`ANT_DATO_NO_COINCIDE`) sin tocar el dato del link.
- Listas (AN-03, `ListasConsulta.jsx`, `codigosPorFuente.js`, V246): el resultado ya NO nace «Sin novedad» (es obligatorio y lo marca el analista); el código se filtra por fuente según la hoja «Listas» del
  diccionario (manda el diccionario sobre el prompt); coincidencia «Documento / Solo nombre»; estado = texto libre con sugerencias.
- Tránsito (AN-04): SIMIT en botones, guardado como el texto de siempre («2 pendientes», `transitoFormato.js`); LEG-10/14/17 solo se sugieren y solo en cargos de conducción.
- Identidad (AN-05, `IdentidadContacto.jsx`, V247): lo declarado va al lado de lo reportado y «¿Coincide?» se calcula con normalización (`identidadFormato.js`); ALR-06 se sugiere y se registra en hallazgos sin
  impacto. «Sin alertas» guarda `alertas_revisadas`.
- Procesos (AN-06, `ProcesosJudiciales.jsx`, `procesosReglas.js`, V248): dos pestañas, relación con el cargo sugerida con el Anexo 2 (se parsea de `anexosInternos.js`), código sugerido con su razón, radicado
  repetido entre fuentes. Columnas nuevas que no se imprimen: `pp_radicado`, `pp_tipo`, `pp_motivo`, `p_soporte`, `pp_soporte`, `procesos_revisados`, `procesos_priv_revisados`; LEG-05 agregado a `p_calificacion`.
- Revisión (AN-07/08, `RevisionAntecedentes.jsx`, `conceptoBorrador.js`): las alertas ALR marcadas llegan como filas sugeridas de hallazgos sin impacto; borrador del concepto (no usa «tiene antecedentes»); la fecha del
  concepto la pone el sistema al completar. `components/CampoFecha.jsx` muestra dd/mm/aaaa y guarda AAAA-MM-DD (también lo usa `InputHoja`). En las listas de la hoja se oculta el «—» salvo en columnas
  `*_ref` / `*_calificacion`.
- Anexos (AN-09): los anexos 1 (catálogo) y 2 (delito vs cargo) salieron del Word del cliente (una sola plantilla, tipo de progreso 1; también se quitaron sus comentarios `anexo_catalogo`/`anexo_alertas`); quedan
  como «Consulta interna» (`ConsultaInterna.jsx`). Falta mostrar el Anexo 2 junto a «Relación con el cargo».
- Estado: sin probar en navegador real; V233–V248 pendientes de aplicar en cada base (reinicio del backend). Las filas «Sin novedad» sin soporte de los servicios #9, #10, #21, #26 y #32 se vaciaron el
  2026-11-03; el servicio #49 conserva 13 sin tocar.

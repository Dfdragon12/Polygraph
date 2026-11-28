# Polygraph Service ERP

Plataforma web para estudios de confiabilidad del talento humano (poligrafías, visitas domiciliarias, estudios de seguridad y validaciones). Backend Spring Boot 3.3 / Java 17, frontend React 18 + Vite, PostgreSQL, migraciones con Flyway. El contexto del proyecto y sus reglas de negocio están en `CLAUDE.md`.

## Cómo publicar

Esta guía deja la plataforma en un servidor con **dominio propio y HTTPS**. Para probar con un túnel (ngrok, cloudflared) se usa el modo de desarrollo, nunca esto.

### 1. Qué necesitas

| Cosa | Para qué |
|---|---|
| Un servidor Linux con **Docker** y **Docker Compose v2** (2 CPU y 4 GB de RAM como mínimo: cada conversión a PDF usa ~300 MB fuera de la JVM) | Corre todo |
| Un **dominio** (por ejemplo `app.polygraph.co`) con el DNS apuntando a la IP del servidor | HTTPS y enlaces de los correos |
| Puertos **80 y 443** abiertos hacia internet (ningún otro) | Caddy pide el certificado y atiende |
| Una cuenta de **correo SMTP** | Restablecer contraseña, enlace del candidato, avisos |
| Llaves de **Wompi** (producción) | Pagos |

### 2. Variables de entorno

Copia `.env.example` a `.env` (en la raíz; el `.gitignore` ya lo excluye) y llénalo. **Obligatorias**: `DB_PASS`, `JWT_SECRET` (32+ caracteres, nueva para producción), `DOMINIO`, `FRONTEND_URL` (con `https://`), `CORS_ALLOWED_ORIGINS`, las de correo (`MAIL_*`), las de Wompi (`WOMPI_*`) y `ADMIN_EMAIL` + `ADMIN_PASSWORD` para el primer administrador. Todo lo demás tiene un valor por defecto documentado en el mismo archivo (carpetas, LibreOffice, memoria, Swagger apagado).

El backend **no arranca** si falta una obligatoria (`DB_*`, `JWT_SECRET`, `MAIL_*`, `WOMPI_*`) y **no hay contraseñas por defecto**. Rutas de archivos: ninguna va escrita en el código; en Docker todo queda dentro del volumen `/app/uploads` (documentos, fotos, plantillas Word, PDF y cola de trabajos). `LIBREOFFICE_TEMP_DIR` mueve la carpeta de trabajo de las conversiones si `/tmp` es chico.

### 3. Puertos

| Puerto | Quién | ¿Abierto a internet? |
|---|---|---|
| 80 y 443 | Caddy (HTTPS automático) | **Sí** |
| 80 | nginx del frontend (dentro de Docker) | No |
| 8080 | Backend (dentro de Docker) | No |
| 5432 | PostgreSQL (dentro de Docker) | No |

El `docker-compose.yml` de la raíz es **solo para desarrollo**: publica la base de datos y pgAdmin. En producción se usa `docker-compose.prod.yml`.

### 4. Instalar la primera vez

```bash
git clone <repositorio> polygraph && cd polygraph
cp .env.example .env            # llenar los valores
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml logs -f backend     # esperar «Started ErpApplication»
```

El frontend se compila dentro de la imagen con `npm run build` (archivos agrupados, minificados y con carga por pantalla: el login baja ~120 KB, no los módulos de cada rol) y lo sirve nginx con compresión y caché. **No** se publica el servidor de desarrollo de Vite.

### 5. Primer administrador

Ninguna migración crea usuarios. Con una base nueva, el backend crea el administrador de `ADMIN_EMAIL` / `ADMIN_PASSWORD` al arrancar **solo si no existe ningún administrador**; la primera vez que entre se le obliga a cambiar la contraseña. Después puedes quitar `ADMIN_PASSWORD` del `.env`. Si falta alguna de las dos, el registro dice `La base no tiene ningún administrador…`.

### 6. Migraciones de base de datos

Corren **solas al arrancar el backend** (Flyway, `backend/src/main/resources/db/migration`, de V1 a la última). Antes de actualizar una instalación existente: **haz una copia de la base** (ver «Copias de seguridad»). Una base nueva aplica la cadena completa; una existente, solo las que le faltan. No se edita nunca una migración ya aplicada: se agrega una nueva. `FLYWAY_REPARAR=true` se usa **una sola vez** y solo si una migración ya aplicada cambió de contenido; luego se quita.

**Una base nueva queda con las tablas, pero sin el catálogo del negocio.** Las migraciones no siembran países, departamentos, clasificaciones, **servicios (`procesos`) ni los subprocesos que cada uno habilita (`procesos_tipos_progreso`)**, ni los planes y parámetros de la tienda (`planes_tienda`, `parametros_tienda`): sin eso no hay nada que solicitar ni comprar. Hoy ese catálogo se carga copiándolo de una base que ya lo tiene (así se probó, PC-09) o a mano desde Admin → Catálogo. Pendiente de decidir: un archivo de siembra versionado para instalaciones nuevas.

La migración `V280` repara los datos que dejó el «Publicar estudio» anterior y anota todo lo que cambió en la tabla `migracion_reparacion_pb_reporte`; revísala después de actualizar.

### 7. Plantillas Word

Las plantillas de cada servicio **no están en el repositorio**: se suben en cada ambiente desde Admin → Catálogo → (servicio) → Plantilla y quedan en `/app/uploads`. En una instalación nueva hay que subirlas todas (Antecedentes, Académica, Laboral, Personal, Financiero, Poligrafía, Visita). Sin la plantilla, ese informe no se puede generar.

### 8. Revisar que todo funcione

1. Entra como administrador → **Admin → Sistema → Trabajos en segundo plano** → tarjeta **Diagnóstico del servidor**. Debe decir «Todo en orden»: LibreOffice encontrado, las tres carpetas con espacio y sin advertencias de configuración.
2. Pulsa **Probar PDF**: arma un documento de prueba y lo convierte con LibreOffice. Debe decir «Prueba de PDF correcta». Si falla, ahí mismo sale el motivo.
3. Emite un estudio de prueba con datos ficticios, de la solicitud a la descarga del PDF completo, y confirma que el cliente lo descarga.
4. **Admin → Trabajos** es el lugar de todos los días: cola, trabajos en proceso y los fallidos con su error técnico y el botón **Reintentar**. Un PDF que tarda más de 15 minutos pasa solo a «fallido» y le avisa al gestor.
5. Registro del backend: `docker compose -f docker-compose.prod.yml logs backend`. Al arrancar deja `LibreOffice disponible…`, o un `ERROR` claro si falta, y los avisos de configuración (`FRONTEND_URL apunta a localhost`, Swagger abierto…).

### 9. HTTPS y dominio

Caddy (`deploy/Caddyfile`) pide y renueva solo el certificado de `DOMINIO`; solo necesita el DNS y los puertos 80/443. Si el certificado falla, el sitio no abre: revisa `docker compose -f docker-compose.prod.yml logs caddy`. La cabecera HSTS dura un año; en la primera instalación conviene probar con `max-age=300` en el `Caddyfile` y subirla cuando todo esté bien. En el panel de Wompi, configura la URL de eventos con tu dominio (ver `docs/PAGOS_WOMPI.md`) y `WOMPI_REDIRECT_URL=https://TU-DOMINIO/cliente/comprar-servicios/resultado`.

### 10. Copias de seguridad

Hay dos cosas que respaldar, juntas: la **base** (`docker exec polygraph_postgres pg_dump -U erp_user erp_polygraph > respaldo.sql`) y el **volumen de archivos** `polygraph_uploads` (documentos de clientes, fotos, plantillas, PDF). Probar la restauración al menos una vez.

### 11. Actualizar a una versión nueva

```bash
git pull
# 1) copia de la base y de los archivos (sección 10)
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml logs -f backend     # las migraciones nuevas corren aquí
```
Después de actualizar, abre la plataforma en una ventana privada para recibir la versión nueva de inmediato.

### Sin Docker

Backend: `cd backend && ./mvnw package -DskipTests` y `java -jar target/*.jar` con las mismas variables de entorno (hace falta LibreOffice en el PATH o `LIBREOFFICE_BINARIO`). Frontend: `cd frontend && npm ci && npm run build` y servir `dist/` con cualquier servidor web con `try_files … /index.html` y `/api` apuntando al backend (ver `frontend/nginx.conf` como modelo).

## Desarrollo local

```bash
docker compose up -d postgres            # o un PostgreSQL local
cd backend && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # perfil dev: application-dev.yml (LibreOffice de Windows, CORS de localhost). NUNCA en producción
cd frontend && npm install && npm run dev   # http://localhost:5173, /api va al backend en :8080
```
Pruebas del backend: `cd backend && ./mvnw test`. La prueba con LibreOffice real se activa con `PRUEBA_LIBREOFFICE=1`.

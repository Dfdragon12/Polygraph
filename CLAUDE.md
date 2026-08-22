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
- Pagos: PSE / tarjetas (por definir)
- Facturación: API Factus (DIAN Colombia) — fase 3

## Estructura de carpetas

erp-polygraph/
├── backend/
│ └── src/main/java/com/polygraph/erp/
│ ├── modules/
│ │ ├── auth/
│ │ ├── clientes/
│ │ ├── evaluados/
│ │ ├── servicios/
│ │ ├── solicitudes/
│ │ ├── gestor/
│ │ ├── analista/
│ │ ├── programador/
│ │ ├── facturacion/
│ │ └── dashboard/
│ ├── security/
│ ├── config/
│ ├── shared/
│ │ ├── exceptions/
│ │ ├── dto/
│ │ └── utils/
│ └── ErpApplication.java
└── frontend/
└── src/
├── modules/
├── components/
├── pages/
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
- Link evaluado expira en 36 horas
- Validar evaluados duplicados (mismo servicio, mismo cliente, últimos 3 meses)
- Tiempos muertos laborales > 30 días activan cuestionario automático
- Cliente con mora no puede solicitar servicios
- Cliente pospago requiere estudio de crédito previo

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

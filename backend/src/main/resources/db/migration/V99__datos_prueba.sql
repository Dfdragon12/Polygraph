-- ============================================================
-- V99 — DATOS DE PRUEBA
-- Uso: entorno de desarrollo / QA únicamente
-- NO ejecutar en producción
-- ============================================================

-- ── 1. CIUDADES base (si no existen) ─────────────────────────
INSERT INTO ciudades (nombre_ciudad, departamento, codigo_dane_ciudad, codigo_dane_depto)
VALUES
    ('Bogotá',      'Cundinamarca', '11001', '11'),
    ('Medellín',    'Antioquia',    '05001', '05'),
    ('Cali',        'Valle del Cauca', '76001', '76')
ON CONFLICT DO NOTHING;

-- ── 2. CLIENTES ───────────────────────────────────────────────

-- Cliente 1: Prepago, persona jurídica
INSERT INTO clientes (
    tipo_cliente, tipo_persona, nit, dv, razon_social, nombre_comercial,
    representante_legal, email_principal, telefono, id_ciudad, estado
) VALUES (
    'PREPAGO', 'JURIDICA', '900100200', '5', 'Empresa ABC S.A.S.', 'ABC Seguridad',
    'Carlos Gómez', 'contacto@empresa-abc.com', '6014001234',
    (SELECT id_ciudad FROM ciudades WHERE nombre_ciudad = 'Bogotá' LIMIT 1),
    'ACTIVO'
);

-- Cliente 2: Pospago, persona jurídica
INSERT INTO clientes (
    tipo_cliente, tipo_persona, nit, dv, razon_social, nombre_comercial,
    representante_legal, email_principal, telefono, id_ciudad, estado
) VALUES (
    'POSPAGO', 'JURIDICA', '800200300', '1', 'Logística XYZ Ltda.', 'XYZ Logística',
    'Ana Torres', 'contacto@logistica-xyz.com', '6024005678',
    (SELECT id_ciudad FROM ciudades WHERE nombre_ciudad = 'Medellín' LIMIT 1),
    'ACTIVO'
);

-- Registro de crédito para cliente pospago
INSERT INTO clientes_pospago (id_cliente, limite_credito, credito_disponible, estado_mora, dias_mora)
SELECT id_cliente, 50000000.00, 50000000.00, 'NORMAL', 0
FROM clientes WHERE nit = '800200300';

-- ── 3. USUARIOS ───────────────────────────────────────────────
-- Passwords hasheadas con BCrypt para 'Xxxx1234!'
-- Nota: el DataInitializer ya crea admin@polygraph.com / Admin1234!
-- Los hashes abajo corresponden a las passwords indicadas en comentario
-- Si el backend está corriendo, usa /api/v1/auth/register en cambio.
-- Aquí usamos un hash BCrypt de ejemplo para 'Gestor1234!'  (12 rounds)

-- Usuario GESTOR (interno Polygraph)
INSERT INTO usuarios (nombre, apellido, email, password, rol, activo, email_verificado, fecha_creacion)
VALUES (
    'Laura', 'Bermúdez',
    'gestor@polygraph.com',
    '$2a$12$K8Ry1ZV6tL2mN4P9wXjOAeqHzV3sBpDf1uI7cWoT0YnRkEsGxM6Wy',
    'GESTOR', true, true, NOW()
)
ON CONFLICT (email) DO NOTHING;

-- Usuario ADMIN_CLIENTE para cliente ABC
INSERT INTO usuarios (nombre, apellido, email, password, rol, activo, email_verificado, id_cliente, fecha_creacion)
SELECT
    'Carlos', 'Gómez',
    'cliente1@empresa-abc.com',
    '$2a$12$K8Ry1ZV6tL2mN4P9wXjOAeqHzV3sBpDf1uI7cWoT0YnRkEsGxM6Wy',
    'ADMIN_CLIENTE', true, true,
    (SELECT id_cliente FROM clientes WHERE nit = '900100200'),
    NOW()
ON CONFLICT (email) DO NOTHING;

-- Usuario ANALISTA_CLIENTE para cliente ABC
INSERT INTO usuarios (nombre, apellido, email, password, rol, activo, email_verificado, id_cliente, fecha_creacion)
SELECT
    'Sofía', 'Martínez',
    'analista@empresa-abc.com',
    '$2a$12$K8Ry1ZV6tL2mN4P9wXjOAeqHzV3sBpDf1uI7cWoT0YnRkEsGxM6Wy',
    'ANALISTA_CLIENTE', true, true,
    (SELECT id_cliente FROM clientes WHERE nit = '900100200'),
    NOW()
ON CONFLICT (email) DO NOTHING;

-- ── 4. CANDIDATOS base ────────────────────────────────────────

INSERT INTO candidatos (cedula, tipo_documento, nombres, apellidos, celular, email_principal)
VALUES
    ('10000001', 'CC', 'Juan Carlos', 'Pérez Rodríguez', '3001000001', 'jc.perez@email.com'),
    ('10000002', 'CC', 'María Luisa', 'González Vargas', '3002000002', 'ml.gonzalez@email.com'),
    ('10000003', 'CC', 'Andrés Felipe', 'Mora Sánchez',   '3003000003', 'af.mora@email.com'),
    ('10000004', 'CC', 'Claudia Patricia', 'Ruiz Castro',  '3004000004', 'cp.ruiz@email.com'),
    ('10000005', 'CC', 'Diego Alejandro', 'Torres Silva',  '3005000005', 'da.torres@email.com')
ON CONFLICT (cedula) DO NOTHING;

-- ── 5. SERVICIOS con varios estados del semáforo ─────────────
-- (cada fila de "servicios" es un proceso independiente con su propio semáforo;
--  varios servicios pedidos juntos para el mismo evaluado comparten fecha_solicitud)

-- Candidato 10000001: Estudio Básico + Polígrafo Pre-empleo — PENDIENTE
INSERT INTO servicios (
    id_cliente, id_candidato, id_proceso, fecha_solicitud, hora_solicitud,
    estado, cargo, notas, fecha_entrega_estimada, id_usuario_solicita
)
SELECT c.id_cliente, ca.id_candidato, p.id_proceso, CURRENT_DATE - 1, '09:00:00',
       'PENDIENTE', 'Auxiliar de Bodega', 'Solicitud de prueba — estado PENDIENTE',
       CURRENT_DATE + 4, u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000001'
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
JOIN procesos p ON p.nombre_proceso IN ('Estudio Básico', 'Polígrafo Pre-empleo')
WHERE c.nit = '900100200';

-- Candidato 10000002: Visita Domiciliaria — PROGRAMANDO
INSERT INTO servicios (
    id_cliente, id_candidato, id_proceso, fecha_solicitud, hora_solicitud,
    estado, cargo, notas, fecha_entrega_estimada, id_usuario_solicita
)
SELECT c.id_cliente, ca.id_candidato, p.id_proceso, CURRENT_DATE - 2, '10:30:00',
       'PROGRAMANDO', 'Contador', 'Solicitud de prueba — estado PROGRAMANDO',
       CURRENT_DATE + 3, u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000002'
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
JOIN procesos p ON p.nombre_proceso = 'Visita Domiciliaria'
WHERE c.nit = '900100200';

-- Candidato 10000003 (cliente XYZ): Estudio Avanzado — EN_EJECUCION
INSERT INTO servicios (
    id_cliente, id_candidato, id_proceso, fecha_solicitud, hora_solicitud,
    estado, cargo, notas, fecha_entrega_estimada, id_usuario_solicita
)
SELECT c.id_cliente, ca.id_candidato, p.id_proceso, CURRENT_DATE - 4, '08:15:00',
       'EN_EJECUCION', 'Jefe de Logística', 'Solicitud de prueba — estado EN_EJECUCION',
       CURRENT_DATE + 1, u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000003'
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
JOIN procesos p ON p.nombre_proceso = 'Estudio Avanzado'
WHERE c.nit = '800200300';

-- Candidato 10000004: Estudio Quick — FINALIZADO
INSERT INTO servicios (
    id_cliente, id_candidato, id_proceso, fecha_solicitud, hora_solicitud,
    estado, cargo, notas, fecha_entrega_estimada, id_usuario_solicita
)
SELECT c.id_cliente, ca.id_candidato, p.id_proceso, CURRENT_DATE - 7, '14:00:00',
       'FINALIZADO', 'Gerente Comercial', 'Solicitud de prueba — estado FINALIZADO',
       CURRENT_DATE - 1, u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000004'
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
JOIN procesos p ON p.nombre_proceso = 'Estudio Quick'
WHERE c.nit = '900100200';

-- Candidato 10000005: Validación Laboral + Validación Académica — PUBLICADO
INSERT INTO servicios (
    id_cliente, id_candidato, id_proceso, fecha_solicitud, hora_solicitud,
    estado, cargo, notas, fecha_entrega_estimada, id_usuario_solicita
)
SELECT c.id_cliente, ca.id_candidato, p.id_proceso, CURRENT_DATE - 10, '16:45:00',
       'PUBLICADO', 'Analista de Seguridad', 'Solicitud de prueba — estado PUBLICADO',
       CURRENT_DATE - 3, u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000005'
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
JOIN procesos p ON p.nombre_proceso IN ('Validación Laboral', 'Validación Académica')
WHERE c.nit = '900100200';

-- ── 6. HISTORIAL DE ESTADOS DEL SEMÁFORO ──────────────────────

-- Servicio(s) de 10000001: creación como PENDIENTE
INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, NULL, 'PENDIENTE', u.id_usuario, 'Servicio creado'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
WHERE ca.cedula = '10000001' AND s.estado = 'PENDIENTE';

-- Servicio de 10000002: PENDIENTE → PROGRAMANDO
INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'PENDIENTE', 'PROGRAMANDO', u.id_usuario, 'Asignado a gestor Laura Bermúdez'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000002' AND s.estado = 'PROGRAMANDO';

-- Servicio de 10000003: PENDIENTE → PROGRAMANDO → EN_EJECUCION
INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'PENDIENTE', 'PROGRAMANDO', u.id_usuario, 'Programado'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000003' AND s.estado = 'EN_EJECUCION';

INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'PROGRAMANDO', 'EN_EJECUCION', u.id_usuario, 'Visita domiciliaria en progreso'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000003' AND s.estado = 'EN_EJECUCION';

-- Servicio de 10000004: PENDIENTE → PROGRAMANDO → EN_EJECUCION → FINALIZADO
INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'PENDIENTE', 'PROGRAMANDO', u.id_usuario, 'Asignado'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000004' AND s.estado = 'FINALIZADO';

INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'PROGRAMANDO', 'EN_EJECUCION', u.id_usuario, 'En proceso de verificación'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000004' AND s.estado = 'FINALIZADO';

INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'EN_EJECUCION', 'FINALIZADO', u.id_usuario, 'Informe generado y revisado'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000004' AND s.estado = 'FINALIZADO';

-- Servicio(s) de 10000005: ciclo completo hasta PUBLICADO
INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'PENDIENTE', 'PROGRAMANDO', u.id_usuario, 'Programado'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000005' AND s.estado = 'PUBLICADO';

INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'PROGRAMANDO', 'EN_EJECUCION', u.id_usuario, 'Inicio de verificaciones'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000005' AND s.estado = 'PUBLICADO';

INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'EN_EJECUCION', 'FINALIZADO', u.id_usuario, 'Verificaciones completadas'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000005' AND s.estado = 'PUBLICADO';

INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_servicio, 'FINALIZADO', 'PUBLICADO', u.id_usuario, 'Informe enviado al cliente'
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE ca.cedula = '10000005' AND s.estado = 'PUBLICADO';

-- ── 7. NOTIFICACIÓN DE PRUEBA ─────────────────────────────────

INSERT INTO notificaciones (id_usuario, tipo, titulo, mensaje, leida)
SELECT
    u.id_usuario,
    'NUEVA_SOLICITUD',
    'Nueva solicitud recibida',
    'El cliente Empresa ABC S.A.S. solicitó un servicio para Juan Carlos Pérez Rodríguez.',
    false
FROM usuarios u
WHERE u.email = 'gestor@polygraph.com';

-- ── 8. LINK DE EVALUADO para servicio PENDIENTE ───────────────
-- Token de prueba con 36 horas de vida desde ahora

INSERT INTO links_candidato (id_servicio, token, fecha_creacion, fecha_expiracion, usado)
SELECT
    s.id_servicio,
    'tok-prueba-evaluado-abc-001',
    NOW(),
    NOW() + INTERVAL '36 hours',
    false
FROM servicios s
JOIN candidatos ca ON ca.id_candidato = s.id_candidato
WHERE ca.cedula = '10000001'
  AND s.estado = 'PENDIENTE'
LIMIT 1;

-- ── FIN ───────────────────────────────────────────────────────
-- Verificación rápida (comentada — descomentar para debug):
-- SELECT 'clientes' AS tabla, COUNT(*) FROM clientes
-- UNION ALL SELECT 'usuarios', COUNT(*) FROM usuarios
-- UNION ALL SELECT 'servicios', COUNT(*) FROM servicios
-- UNION ALL SELECT 'historial_estados_servicio', COUNT(*) FROM historial_estados_servicio
-- UNION ALL SELECT 'notificaciones', COUNT(*) FROM notificaciones
-- UNION ALL SELECT 'links_candidato', COUNT(*) FROM links_candidato;

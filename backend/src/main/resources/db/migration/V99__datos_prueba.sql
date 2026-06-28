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

-- ── 5. SOLICITUDES con los 7 estados del semáforo ────────────

-- Solicitud 1: PENDIENTE
INSERT INTO solicitudes (
    id_cliente, id_candidato, cedula_evaluado, nombres_evaluado, apellidos_evaluado,
    celular_evaluado, email_evaluado, ciudad_evaluado, cargo, notas,
    estado, fecha_solicitud, fecha_entrega_estimada, id_usuario_solicita
)
SELECT
    c.id_cliente,
    ca.id_candidato,
    '10000001', 'Juan Carlos', 'Pérez Rodríguez',
    '3001000001', 'jc.perez@email.com', 'Bogotá', 'Auxiliar de Bodega',
    'Solicitud de prueba — estado PENDIENTE',
    'PENDIENTE',
    NOW() - INTERVAL '1 day',
    CURRENT_DATE + 4,
    u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000001'
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
WHERE c.nit = '900100200';

-- Solicitud 2: PROGRAMANDO
INSERT INTO solicitudes (
    id_cliente, id_candidato, cedula_evaluado, nombres_evaluado, apellidos_evaluado,
    celular_evaluado, email_evaluado, ciudad_evaluado, cargo, notas,
    estado, fecha_solicitud, fecha_entrega_estimada, id_usuario_solicita
)
SELECT
    c.id_cliente,
    ca.id_candidato,
    '10000002', 'María Luisa', 'González Vargas',
    '3002000002', 'ml.gonzalez@email.com', 'Bogotá', 'Contador',
    'Solicitud de prueba — estado PROGRAMANDO',
    'PROGRAMANDO',
    NOW() - INTERVAL '2 days',
    CURRENT_DATE + 3,
    u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000002'
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
WHERE c.nit = '900100200';

-- Solicitud 3: EN_EJECUCION (cliente XYZ)
INSERT INTO solicitudes (
    id_cliente, id_candidato, cedula_evaluado, nombres_evaluado, apellidos_evaluado,
    celular_evaluado, email_evaluado, ciudad_evaluado, cargo, notas,
    estado, fecha_solicitud, fecha_entrega_estimada, id_usuario_solicita
)
SELECT
    c.id_cliente,
    ca.id_candidato,
    '10000003', 'Andrés Felipe', 'Mora Sánchez',
    '3003000003', 'af.mora@email.com', 'Medellín', 'Jefe de Logística',
    'Solicitud de prueba — estado EN_EJECUCION',
    'EN_EJECUCION',
    NOW() - INTERVAL '4 days',
    CURRENT_DATE + 1,
    u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000003'
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE c.nit = '800200300';

-- Solicitud 4: FINALIZADO
INSERT INTO solicitudes (
    id_cliente, id_candidato, cedula_evaluado, nombres_evaluado, apellidos_evaluado,
    celular_evaluado, email_evaluado, ciudad_evaluado, cargo, notas,
    estado, fecha_solicitud, fecha_entrega_estimada, id_usuario_solicita
)
SELECT
    c.id_cliente,
    ca.id_candidato,
    '10000004', 'Claudia Patricia', 'Ruiz Castro',
    '3004000004', 'cp.ruiz@email.com', 'Bogotá', 'Gerente Comercial',
    'Solicitud de prueba — estado FINALIZADO',
    'FINALIZADO',
    NOW() - INTERVAL '7 days',
    CURRENT_DATE - 1,
    u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000004'
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
WHERE c.nit = '900100200';

-- Solicitud 5: PUBLICADO
INSERT INTO solicitudes (
    id_cliente, id_candidato, cedula_evaluado, nombres_evaluado, apellidos_evaluado,
    celular_evaluado, email_evaluado, ciudad_evaluado, cargo, notas,
    estado, fecha_solicitud, fecha_entrega_estimada, id_usuario_solicita
)
SELECT
    c.id_cliente,
    ca.id_candidato,
    '10000005', 'Diego Alejandro', 'Torres Silva',
    '3005000005', 'da.torres@email.com', 'Cali', 'Analista de Seguridad',
    'Solicitud de prueba — estado PUBLICADO',
    'PUBLICADO',
    NOW() - INTERVAL '10 days',
    CURRENT_DATE - 3,
    u.id_usuario
FROM clientes c
JOIN candidatos ca ON ca.cedula = '10000005'
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
WHERE c.nit = '900100200';

-- ── 6. SERVICIOS DEL CATÁLOGO por solicitud ──────────────────

-- Solicitud 1 (PENDIENTE): Estudio Básico + Polígrafo Pre-empleo
INSERT INTO solicitud_servicios (id_solicitud, id_catalogo, estado)
SELECT s.id_solicitud, cs.id_catalogo, 'PENDIENTE'
FROM solicitudes s
JOIN catalogo_servicios cs ON cs.nombre IN ('Estudio Básico', 'Polígrafo Pre-empleo')
WHERE s.cedula_evaluado = '10000001'
  AND s.estado = 'PENDIENTE';

-- Solicitud 2 (PROGRAMANDO): Visita Domiciliaria
INSERT INTO solicitud_servicios (id_solicitud, id_catalogo, estado)
SELECT s.id_solicitud, cs.id_catalogo, 'PENDIENTE'
FROM solicitudes s
JOIN catalogo_servicios cs ON cs.nombre = 'Visita Domiciliaria'
WHERE s.cedula_evaluado = '10000002'
  AND s.estado = 'PROGRAMANDO';

-- Solicitud 3 (EN_EJECUCION): Estudio Avanzado
INSERT INTO solicitud_servicios (id_solicitud, id_catalogo, estado)
SELECT s.id_solicitud, cs.id_catalogo, 'EN_EJECUCION'
FROM solicitudes s
JOIN catalogo_servicios cs ON cs.nombre = 'Estudio Avanzado'
WHERE s.cedula_evaluado = '10000003'
  AND s.estado = 'EN_EJECUCION';

-- Solicitud 4 (FINALIZADO): Estudio Quick
INSERT INTO solicitud_servicios (id_solicitud, id_catalogo, estado)
SELECT s.id_solicitud, cs.id_catalogo, 'FINALIZADO'
FROM solicitudes s
JOIN catalogo_servicios cs ON cs.nombre = 'Estudio Quick'
WHERE s.cedula_evaluado = '10000004'
  AND s.estado = 'FINALIZADO';

-- Solicitud 5 (PUBLICADO): Validación Laboral + Validación Académica
INSERT INTO solicitud_servicios (id_solicitud, id_catalogo, estado)
SELECT s.id_solicitud, cs.id_catalogo, 'PUBLICADO'
FROM solicitudes s
JOIN catalogo_servicios cs ON cs.nombre IN ('Validación Laboral', 'Validación Académica')
WHERE s.cedula_evaluado = '10000005'
  AND s.estado = 'PUBLICADO';

-- ── 7. HISTORIAL DE ESTADOS ───────────────────────────────────

-- Historial solicitud 1: creación como PENDIENTE
INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, NULL, 'PENDIENTE', u.id_usuario, 'Solicitud creada'
FROM solicitudes s
JOIN usuarios u ON u.email = 'cliente1@empresa-abc.com'
WHERE s.cedula_evaluado = '10000001' AND s.estado = 'PENDIENTE';

-- Historial solicitud 2: PENDIENTE → PROGRAMANDO
INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'PENDIENTE', 'PROGRAMANDO', u.id_usuario, 'Asignado a gestor Laura Bermúdez'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000002' AND s.estado = 'PROGRAMANDO';

-- Historial solicitud 3: PENDIENTE → PROGRAMANDO → EN_EJECUCION
INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'PENDIENTE', 'PROGRAMANDO', u.id_usuario, 'Programado'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000003' AND s.estado = 'EN_EJECUCION';

INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'PROGRAMANDO', 'EN_EJECUCION', u.id_usuario, 'Visita domiciliaria en progreso'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000003' AND s.estado = 'EN_EJECUCION';

-- Historial solicitud 4: PENDIENTE → PROGRAMANDO → EN_EJECUCION → FINALIZADO
INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'PENDIENTE', 'PROGRAMANDO', u.id_usuario, 'Asignado'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000004' AND s.estado = 'FINALIZADO';

INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'PROGRAMANDO', 'EN_EJECUCION', u.id_usuario, 'En proceso de verificación'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000004' AND s.estado = 'FINALIZADO';

INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'EN_EJECUCION', 'FINALIZADO', u.id_usuario, 'Informe generado y revisado'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000004' AND s.estado = 'FINALIZADO';

-- Historial solicitud 5: ciclo completo hasta PUBLICADO
INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'PENDIENTE', 'PROGRAMANDO', u.id_usuario, 'Programado'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000005' AND s.estado = 'PUBLICADO';

INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'PROGRAMANDO', 'EN_EJECUCION', u.id_usuario, 'Inicio de verificaciones'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000005' AND s.estado = 'PUBLICADO';

INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'EN_EJECUCION', 'FINALIZADO', u.id_usuario, 'Verificaciones completadas'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000005' AND s.estado = 'PUBLICADO';

INSERT INTO historial_solicitudes (id_solicitud, estado_anterior, estado_nuevo, id_usuario, observacion)
SELECT s.id_solicitud, 'FINALIZADO', 'PUBLICADO', u.id_usuario, 'Informe enviado al cliente'
FROM solicitudes s
JOIN usuarios u ON u.email = 'gestor@polygraph.com'
WHERE s.cedula_evaluado = '10000005' AND s.estado = 'PUBLICADO';

-- ── 8. NOTIFICACIÓN DE PRUEBA ─────────────────────────────────

INSERT INTO notificaciones (id_usuario, tipo, titulo, mensaje, leida)
SELECT
    u.id_usuario,
    'NUEVA_SOLICITUD',
    'Nueva solicitud recibida',
    'El cliente Empresa ABC S.A.S. creó una solicitud para Juan Carlos Pérez Rodríguez.',
    false
FROM usuarios u
WHERE u.email = 'gestor@polygraph.com';

-- ── 9. LINK DE EVALUADO para solicitud PENDIENTE ──────────────
-- Token de prueba con 36 horas de vida desde ahora

INSERT INTO links_candidato (id_solicitud, token, fecha_creacion, fecha_expiracion, usado)
SELECT
    s.id_solicitud,
    'tok-prueba-evaluado-abc-001',
    NOW(),
    NOW() + INTERVAL '36 hours',
    false
FROM solicitudes s
WHERE s.cedula_evaluado = '10000001'
  AND s.estado = 'PENDIENTE';

-- ── FIN ───────────────────────────────────────────────────────
-- Verificación rápida (comentada — descomentarr para debug):
-- SELECT 'clientes' AS tabla, COUNT(*) FROM clientes
-- UNION ALL SELECT 'usuarios', COUNT(*) FROM usuarios
-- UNION ALL SELECT 'solicitudes', COUNT(*) FROM solicitudes
-- UNION ALL SELECT 'solicitud_servicios', COUNT(*) FROM solicitud_servicios
-- UNION ALL SELECT 'historial_solicitudes', COUNT(*) FROM historial_solicitudes
-- UNION ALL SELECT 'notificaciones', COUNT(*) FROM notificaciones
-- UNION ALL SELECT 'links_candidato', COUNT(*) FROM links_candidato;

-- Catálogo de códigos de calificación por subservicio (2026-10-05, docs/antecedentes/).
-- Cada subservicio que califica con códigos propios (Antecedentes legales hoy; Validaciones,
-- Financiero, Visita y Poligrafía después) tiene su grupo de códigos aquí — los puntos NO se
-- queman en el código de cálculo. La fuente de verdad de los valores es docs/antecedentes/motor_ant.py.
--
-- · puntos: entero 0–100; NULL solo si el código es crítico (lleva el servicio a 100 y ALTO).
-- · por_proceso: suma una vez por cada fila en que aparece; si no, cuenta una sola vez.
-- · solo_conduccion: solo suma si el cargo implica conducción.
-- · Lo que ya se usó no se borra: se desactiva (activo = false).
-- · Las alertas ALR-01..ALR-06 no suman puntos y NO van aquí (son solo opciones de las listas).
--
-- tipos_progreso.grupo_calificacion dice qué grupo de códigos usa cada subservicio (NULL = califica
-- con los puntos del constructor, como hasta ahora).

CREATE TABLE codigos_calificacion (
    id              SERIAL       PRIMARY KEY,
    grupo           VARCHAR(30)  NOT NULL,
    codigo          VARCHAR(20)  NOT NULL,
    descripcion     VARCHAR(300) NOT NULL,
    puntos          INTEGER,
    critico         BOOLEAN      NOT NULL DEFAULT FALSE,
    por_proceso     BOOLEAN      NOT NULL DEFAULT FALSE,
    solo_conduccion BOOLEAN      NOT NULL DEFAULT FALSE,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    orden           INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ux_codigos_calificacion_grupo_codigo UNIQUE (grupo, codigo),
    CONSTRAINT ck_codigos_calificacion_puntos CHECK (
        (critico AND puntos IS NULL) OR (NOT critico AND puntos IS NOT NULL AND puntos BETWEEN 0 AND 100))
);

INSERT INTO codigos_calificacion (grupo, codigo, descripcion, puntos, critico, por_proceso, solo_conduccion, activo, orden) VALUES
('ANTECEDENTES', 'LEG-01', 'Documento cancelado o no encontrado en la Registraduría', NULL, TRUE, FALSE, FALSE, TRUE, 1),
('ANTECEDENTES', 'LEG-02', 'Coincidencia confirmada en lista vinculante o restrictiva (ONU, OFAC, HM Treasury)', NULL, TRUE, FALSE, FALSE, TRUE, 2),
('ANTECEDENTES', 'LEG-03', 'Orden de captura o requerimiento judicial vigente', NULL, TRUE, FALSE, FALSE, TRUE, 3),
('ANTECEDENTES', 'LEG-04', 'Notificación o búsqueda vigente de Interpol o Europol', NULL, TRUE, FALSE, FALSE, TRUE, 4),
('ANTECEDENTES', 'LEG-05', 'Proceso penal activo o pena vigente como investigado, relacionado con el cargo', NULL, TRUE, TRUE, FALSE, TRUE, 5),
('ANTECEDENTES', 'LEG-06', 'Registrado como proveedor ficticio en la DIAN', 50, FALSE, FALSE, FALSE, TRUE, 6),
('ANTECEDENTES', 'LEG-07', 'Inhabilitado por el Banco Mundial o el BID', 50, FALSE, FALSE, FALSE, TRUE, 7),
('ANTECEDENTES', 'LEG-08', 'Responsabilidad fiscal vigente (Contraloría)', 45, FALSE, FALSE, FALSE, TRUE, 8),
('ANTECEDENTES', 'LEG-09', 'Sanción o inhabilidad disciplinaria vigente (Procuraduría, Personería)', NULL, TRUE, FALSE, FALSE, TRUE, 9),
('ANTECEDENTES', 'LEG-10', 'Licencia de conducción suspendida o cancelada (solo cargos de conducción)', NULL, TRUE, FALSE, TRUE, TRUE, 10),
('ANTECEDENTES', 'LEG-11', 'Proceso penal activo o pena vigente como investigado, no relacionado con el cargo', 70, FALSE, TRUE, FALSE, TRUE, 11),
('ANTECEDENTES', 'LEG-12', 'Omitió declarar un antecedente vigente', 25, FALSE, FALSE, FALSE, TRUE, 12),
('ANTECEDENTES', 'LEG-13', 'Información adversa en noticias criminales o medios', 40, FALSE, FALSE, FALSE, TRUE, 13),
('ANTECEDENTES', 'LEG-14', 'Licencia vencida o categoría que no corresponde al cargo (solo cargos de conducción)', 40, FALSE, FALSE, TRUE, TRUE, 14),
('ANTECEDENTES', 'LEG-15', 'Multa o sanción como contratista del Estado (SECOP)', 20, FALSE, FALSE, FALSE, TRUE, 15),
('ANTECEDENTES', 'LEG-16', 'Reportado como deudor moroso del Estado (BDME)', 15, FALSE, FALSE, FALSE, TRUE, 16),
('ANTECEDENTES', 'LEG-17', 'Multas de tránsito pendientes sin acuerdo de pago (solo cargos de conducción)', 20, FALSE, FALSE, TRUE, TRUE, 17),
('ANTECEDENTES', 'LEG-18', 'Medida correctiva registrada (RNMC), en cualquier estado', 30, FALSE, FALSE, FALSE, TRUE, 18),
('ANTECEDENTES', 'LEG-19', 'Proceso penal terminado sin condena (archivo, prescripción, desistimiento, conciliación o principio de oportunidad)', 15, FALSE, TRUE, FALSE, TRUE, 19),
('ANTECEDENTES', 'LEG-20', 'Condena cumplida por delito relacionado con el cargo', 30, FALSE, TRUE, FALSE, TRUE, 20),
('ANTECEDENTES', 'LEG-21', 'Condena cumplida por delito no relacionado con el cargo', 20, FALSE, TRUE, FALSE, TRUE, 21),
('ANTECEDENTES', 'LEG-22', 'Demanda no penal activa en contra (civil, laboral o de familia)', 10, FALSE, TRUE, FALSE, TRUE, 22),
('ANTECEDENTES', 'LEG-23', 'Demanda no penal terminada en contra (no aplica si el fallo fue a favor del evaluado)', 5, FALSE, TRUE, FALSE, TRUE, 23),
('ANTECEDENTES', 'LEG-24', 'Suplantación de identidad confirmada (el evaluado usa una identidad ajena)', NULL, TRUE, FALSE, FALSE, TRUE, 24);

ALTER TABLE tipos_progreso ADD COLUMN grupo_calificacion VARCHAR(30);

-- El subservicio de Antecedentes usa este grupo. Por nombre porque el id cambia entre bases; si
-- en alguna base se llama distinto, queda NULL y se asigna después.
UPDATE tipos_progreso SET grupo_calificacion = 'ANTECEDENTES'
WHERE lower(trim(nombre_progreso)) = 'antecedentes';

-- Grupos de temas de poligrafía (2026-10-29, pedido del usuario): cada tema del catálogo pertenece a un grupo (texto libre administrable en
-- Admin -> Novedades -> Temas de poligrafía) para organizarlos y poder escoger un grupo completo. Las solicitudes ya hechas copian el texto del
-- tema (servicio_preguntas_poligrafia): esto no las cambia. Los temas sembrados quedan en grupos iniciales que el admin puede cambiar.
ALTER TABLE preguntas_poligrafia ADD COLUMN IF NOT EXISTS grupo VARCHAR(100);

UPDATE preguntas_poligrafia SET grupo = 'Conducta laboral'
WHERE grupo IS NULL AND nombre IN ('Robos', 'Sobornos', 'Despido de trabajos anteriores', 'Sabotajes', 'Crímenes cibernéticos (Sabotaje)');
UPDATE preguntas_poligrafia SET grupo = 'Delitos y vínculos'
WHERE grupo IS NULL AND nombre IN ('Vínculos con grupos al margen de la ley', 'Drogas ilegales / Tráfico de drogas', 'Beneficios indebidos', 'Lavado de dinero');
UPDATE preguntas_poligrafia SET grupo = 'Documentos e información'
WHERE grupo IS NULL AND nombre IN ('Fraude documental', 'Mintió en su hoja de vida', 'Mintió en su experiencia laboral');
UPDATE preguntas_poligrafia SET grupo = 'Otros'
WHERE grupo IS NULL AND nombre IN ('Temas para servicios generales / niñeras', 'La que determine POLYGRAPH', 'Otros ¿Cuál?');

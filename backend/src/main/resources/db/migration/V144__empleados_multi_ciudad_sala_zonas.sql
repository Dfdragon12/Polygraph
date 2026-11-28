-- Poligrafista y visitador ahora pueden tener varias ciudades asignadas (varias salas /
-- varias zonas de cobertura), guardadas como texto separado por comas — se amplían las
-- columnas para que quepan varias entradas.
ALTER TABLE "empleados" ALTER COLUMN "sala_encargada" TYPE varchar(500);
ALTER TABLE "empleados" ALTER COLUMN "zonas_visita" TYPE varchar(500);

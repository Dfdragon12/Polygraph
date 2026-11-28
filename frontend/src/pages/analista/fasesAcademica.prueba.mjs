// Prueba de las fases de académica (AC-01): node src/pages/analista/fasesAcademica.prueba.mjs
import * as F from './fasesAcademica.js'
const ok = (c, m) => { console.log(c ? 'OK  ' : 'FALLA', m); if (!c) process.exitCode = 1 }
const col = (id, clave, extra = {}) => ({ idColumna: id, clave, obligatorio: false, automatico: false, calculado: false, ...extra })
const secciones = [
  { idSeccion: 1, clave: 'datos_evaluado', tipo: 'CAMPOS', columnas: [col(1, 'ev_cliente', { automatico: true }), col(2, 'ev_criticidad', { obligatorio: true })], valores: {}, filas: [] },
  { idSeccion: 2, clave: 'estudio_verificado', tipo: 'CAMPOS', columnas: [col(3, 'aca_criterio', { obligatorio: true }), col(4, 'aca_institucion'), col(5, 'aca_ref'), col(12, 'aca_estudio_sel'), col(13, 'aca_preguntas'), col(14, 'aca_soporte'), col(15, 'aca_observaciones')], valores: {}, filas: [] },
  { idSeccion: 3, clave: 'gestiones', tipo: 'TABLA', columnas: [col(6, 'g_fecha')], valores: {}, filas: [] },
  { idSeccion: 4, clave: 'educacion', origenFijoClave: 'educacion', tipo: 'TABLA', columnas: [], valores: {}, filas: [] },
  { idSeccion: 5, clave: 'informativos', tipo: 'TABLA', columnas: [col(7, 'i_situacion'), col(8, 'i_tratamiento')], valores: {}, filas: [{ localId: 'a', valores: { 7: 'x', 8: '' } }] },
  { idSeccion: 6, clave: 'concepto_analista', tipo: 'CAMPOS', columnas: [col(9, 'concepto', { obligatorio: true }), col(10, 'analista_fecha', { obligatorio: true })], valores: {}, filas: [] },
  { idSeccion: 7, clave: 'resultado_servicio', tipo: 'CAMPOS', calculada: false, columnas: [col(11, 'srv_puntaje', { calculado: true })], valores: {}, filas: [] },
  { idSeccion: 8, clave: 'hallazgos', tipo: 'TABLA', calculada: true, columnas: [], valores: {}, filas: [] },
]
ok(F.esFormatoAcademica(secciones) && !F.esFormatoAcademica(secciones.slice(0, 2)), 'reconoce la plantilla académica')
ok(F.FASES.length === 3 && F.TODOS_LOS_TEMAS.length === 4, '3 fases y 4 temas')
const partesEdu = F.partesDelTema(F.TODOS_LOS_TEMAS.find(t => t.id === 'educacion'), secciones)
ok(partesEdu.length === 1 && partesEdu[0].columnas.map(c => c.clave).join() === 'aca_criterio,aca_estudio_sel', 'el criterio y la selección van en el tema de educación')
const partesEst = F.partesDelTema(F.TODOS_LOS_TEMAS.find(t => t.id === 'estudio'), secciones)
ok(partesEst[0].columnas.every(c => c.clave !== 'aca_criterio') && partesEst.some(p => p.seccion.clave === 'gestiones'), 'el resto de la sección 5 y las gestiones van en «Estudio verificado»')
const datos = F.partesDelTema(F.TODOS_LOS_TEMAS[0], secciones)
ok(datos[0].columnas.every(c => c.clave !== 'ev_criticidad'), 'la criticidad no se pide')
let f = F.faltantesDeAcademica(secciones)
ok(f.grupos.some(g => g.id === 'educacion' && g.items[0].clave === 'aca_criterio') && f.grupos.some(g => g.id === 'revision' && g.items.some(i => i.clave === 'concepto' || i.clave === 'i_tratamiento')), 'faltantes por tema con su clave')
ok(f.grupos.find(g => g.id === 'estudio').items.map(i => i.clave).join() === 'aca_preguntas,aca_soporte', 'AC-03: sin responder ni soporte faltan las preguntas y el soporte; las gestiones y la calificación vacías no cuentan')
const e0 = F.estadoDeTemas(secciones)
ok(e0.estados.educacion === 'vacio', 'tema vacío sin criterio')
secciones[1].valores[3] = 'Último estudio finalizado'
secciones[1].valores[12] = '968'
secciones[1].valores[13] = JSON.stringify({ p1: 'No', p2: 'No', p3: 'No', p4: 'No', p5: 'Sí se verificó' })
secciones[1].valores[14] = '[{"nombre":"respuesta.pdf"}]'
secciones[4].filas[0].valores[8] = 'Error de digitación'
secciones[5].valores[9] = 'Concepto'
const e1 = F.estadoDeTemas(secciones)
ok(e1.estados.educacion === 'completo' && e1.estados.revision === 'completo', 'con lo obligatorio puesto, ✓')
ok(F.faltantesDeAcademica(secciones).total === 0, 'sin faltantes → "Todo listo"')

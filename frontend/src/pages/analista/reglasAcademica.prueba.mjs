// Prueba de las reglas de AC-02: node src/pages/analista/reglasAcademica.prueba.mjs
import * as A from './reglasAcademica.js'
import * as F from './fasesAcademica.js'
const ok = (c, m) => { console.log(c ? 'OK  ' : 'FALLA', m); if (!c) process.exitCode = 1 }

const profesional = { id: 968, nivel: 'Profesional', institucion: 'Universidad Ejemplo', titulo: 'Administrador financiero', fecha_inicio: '01/02/2006', fecha_fin: '15/06/2011', estado: 'Graduado', ciudad: 'Bogotá', en_exterior: 'No', pais_exterior: '', en_curso: 'No' }
const tecnico = { id: 969, nivel: 'Técnico laboral', institucion: 'SENA', titulo: 'Asistente contable', fecha_inicio: '01/02/2003', fecha_fin: '30/11/2004', estado: 'Graduado', ciudad: 'Bogotá', en_exterior: 'No', pais_exterior: '', en_curso: 'No' }
const enCurso = { id: 970, nivel: 'Maestría', institucion: 'Universidad Ejemplo', titulo: 'Maestría en finanzas', fecha_inicio: '01/02/2024', fecha_fin: '', estado: 'En curso', ciudad: 'Bogotá', en_exterior: 'No', pais_exterior: '', en_curso: 'Sí' }
const materias = { id: 971, nivel: 'Especialización', institucion: 'Otra', titulo: 'Esp.', fecha_inicio: '01/02/2012', fecha_fin: '30/06/2013', estado: 'Terminó materias, pendiente de grado', ciudad: 'Cali', en_exterior: 'No', pais_exterior: '', en_curso: 'No' }

ok(A.esFinalizado(profesional) && A.esFinalizado(materias) && !A.esFinalizado(enCurso), 'finalizado = Graduado o Terminó materias')
ok(!A.esFinalizado({ estado: 'Retirado sin graduarse' }) && !A.esFinalizado({ estado: 'Aplazado' }), 'retirado o aplazado no cuentan')
ok(A.estudioFinalizadoMasReciente([tecnico, profesional, enCurso]).id === 968, '#3 el último estudio finalizado es el profesional')
ok(A.estudioFinalizadoMasReciente([tecnico, profesional, materias]).id === 971, 'entre finalizados gana la fecha más reciente (terminó materias en 2013)')
ok(A.estudioFinalizadoMasReciente([enCurso]) === null, 'sin finalizados no selecciona')

let r = A.estudioSegunCriterio(A.CRITERIO_POR_DEFECTO, [tecnico, profesional], false)
ok(r.automatico && r.estudio.id === 968, 'criterio por defecto: selecciona solo')
r = A.estudioSegunCriterio(A.CRITERIO_POR_DEFECTO, [enCurso], false)
ok(!r.estudio && r.mensaje.includes('no declara estudios finalizados: elige cuál verificar'), 'sin finalizados: mensaje y elige el analista')
r = A.estudioSegunCriterio(A.CRITERIO_POR_DEFECTO, [], true)
ok(!r.estudio && r.mensaje === 'El evaluado declaró que no finalizó el bachillerato.', '#11 sin estudios: mensaje del bachillerato')
r = A.estudioSegunCriterio('Por solicitud del cliente', [profesional, tecnico], false)
ok(!r.estudio && !r.automatico, 'otros criterios: elige el analista')

const copia = A.leerInstantanea(A.instantanea(profesional))
ok(A.cambioDeclaracion('968', copia, [profesional]) === null, 'sin cambios en la hoja de vida: sin aviso')
ok(A.cambioDeclaracion('968', copia, [{ ...profesional, titulo: 'Administrador de empresas' }]) === 'cambio', 'el evaluado cambió el título: aviso')
ok(A.cambioDeclaracion('968', copia, [tecnico]) === 'desaparecio', 'el estudio ya no está: aviso')
ok(A.cambioDeclaracion('', copia, [profesional]) === null && A.cambioDeclaracion('968', null, [profesional]) === null, 'sin selección o sin copia no avisa')

// Faltantes de criterio y selección
const col = (id, clave, extra = {}) => ({ idColumna: id, clave, obligatorio: false, automatico: false, calculado: false, ...extra })
const sec = (valores) => [
  { idSeccion: 1, clave: 'datos_evaluado', tipo: 'CAMPOS', columnas: [col(1, 'ev_cliente', { automatico: true })], valores: {}, filas: [] },
  { idSeccion: 2, clave: 'estudio_verificado', tipo: 'CAMPOS', columnas: [col(3, 'aca_criterio', { obligatorio: true }), col(4, 'aca_estudio_sel'), col(5, 'aca_decl'), col(6, 'aca_observaciones')], valores, filas: [] },
  { idSeccion: 3, clave: 'gestiones', tipo: 'TABLA', columnas: [col(7, 'g_fecha')], valores: {}, filas: [] },
  { idSeccion: 4, clave: 'concepto_analista', tipo: 'CAMPOS', columnas: [col(8, 'concepto')], valores: {}, filas: [] },
]
let f = F.faltantesDeAcademica(sec({}), 1)
ok(f.grupos.find(g => g.id === 'educacion').items.map(i => i.clave).join() === 'aca_criterio,aca_estudio_sel', 'sin criterio ni estudio: dos faltantes en el tema')
f = F.faltantesDeAcademica(sec({ 3: A.CRITERIO_POR_DEFECTO, 4: '968' }), 1)
ok(!f.grupos.some(g => g.id === 'educacion'), 'criterio y estudio puestos: sin faltantes en la fase 1')
f = F.faltantesDeAcademica(sec({ 3: 'Por solicitud del cliente', 4: '968' }), 1)
ok(f.grupos.find(g => g.id === 'estudio').items.some(i => i.clave === 'aca_observaciones'), '#7 criterio distinto sin explicar: Observaciones es faltante')
f = F.faltantesDeAcademica(sec({ 3: 'Por solicitud del cliente', 4: '968', 6: 'El cliente pidió verificar este título' }), 1)
ok(!(f.grupos.find(g => g.id === 'estudio')?.items ?? []).some(i => i.clave === 'aca_observaciones'), 'con la explicación ya no falta')
f = F.faltantesDeAcademica(sec({ 3: A.CRITERIO_POR_DEFECTO }), 0)
ok(!f.grupos.some(g => g.id === 'educacion'), 'sin estudios declarados no se exige elegir uno')

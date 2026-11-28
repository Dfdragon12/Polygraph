// Prueba de las reglas de AC-03: node src/pages/analista/reglasEstudio.prueba.mjs
import * as E from './reglasEstudio.js'
import * as A from './reglasAcademica.js'
import * as F from './fasesAcademica.js'
const ok = (c, m) => { console.log(c ? 'OK  ' : 'FALLA', m); if (!c) process.exitCode = 1 }

const NIVELES = ['Bachiller', 'Técnico laboral', 'Técnico profesional', 'Tecnólogo', 'Profesional', 'Especialización', 'Maestría', 'Doctorado']
const ESTADOS = ['Graduado', 'Terminó materias, pendiente de grado', 'En curso', 'Retirado sin graduarse', 'Aplazado']
const listas = { aca_nivel: NIVELES, aca_estado: ESTADOS }
const profesional = { id: 968, nivel: 'Profesional', institucion: 'Universidad Ejemplo', titulo: 'Administrador financiero', fecha_inicio: '01/02/2006', fecha_fin: '15/06/2011', estado: 'Graduado', ciudad: 'Bogotá', en_exterior: 'No', pais_exterior: '' }
const copia = A.leerInstantanea(A.instantanea(profesional))

// Precarga
const p = E.precarga(profesional, listas)
ok(p.aca_institucion === 'Universidad Ejemplo' && p.aca_nivel === 'Profesional' && p.aca_estado === 'Graduado' && p.aca_fecha_fin === '2011-06-15' && p.aca_fecha_inicio === '2006-02-01', 'la precarga lleva lo declarado (fechas en AAAA-MM-DD)')
ok(p.aca_categoria === E.CAT_SUPERIOR, 'profesional → Educación superior')
ok(E.categoriaDeNivel({ nivel: 'Bachiller' }) === E.CAT_BASICA && E.categoriaDeNivel({ nivel: 'Técnico laboral' }) === E.CAT_ETDH && E.categoriaDeNivel({ nivel: 'Maestría' }) === E.CAT_SUPERIOR && E.categoriaDeNivel({ nivel: 'Tecnólogo' }) === E.CAT_SUPERIOR, 'categoría deducida del nivel')
ok(E.categoriaDeNivel({ nivel: 'Profesional', en_exterior: 'Sí', pais_exterior: 'España' }) === E.CAT_EXTERIOR, 'en el exterior → Estudios en el exterior')
ok(E.ciudadDeclarada({ ciudad: 'Madrid', en_exterior: 'Sí', pais_exterior: 'España' }) === 'Madrid (España)', 'ciudad con país si es del exterior')
ok(E.precarga({ ...profesional, nivel: 'Otro raro' }, listas).aca_nivel === '', 'un nivel que no está en la lista no se inventa: elige el analista')

// Marcas
const v = { ...p }
let m = E.marcasDelEstudio(v, copia, listas)
ok(Object.values(m).every(x => x.nivel === 'coincide'), 'idéntico: todo coincide')
m = E.marcasDelEstudio({ ...v, aca_institucion: 'UNIVERSIDAD  ejemplo' }, copia, listas)
ok(m.aca_institucion.nivel === 'coincide', 'tildes, mayúsculas y espacios no cuentan')
m = E.marcasDelEstudio({ ...v, aca_titulo: 'Administrador de empresas' }, copia, listas)
ok(m.aca_titulo.nivel === 'revisar', 'texto distinto → revisar')
m = E.marcasDelEstudio({ ...v, aca_fecha_fin: '2011-06-18' }, copia, listas)
ok(m.aca_fecha_fin.dias === 3 && m.aca_fecha_fin.nivel === 'informativo', 'fecha a 3 días: «Diferencia: 3 días», informativo')
m = E.marcasDelEstudio({ ...v, aca_fecha_fin: '2011-09-30' }, copia, listas)
ok(m.aca_fecha_fin.nivel === 'fondo', 'más de 2 meses → de fondo')
m = E.marcasDelEstudio({ ...v, aca_fecha_fin: '2012-01-05' }, copia, listas)
ok(m.aca_fecha_fin.nivel === 'fondo', 'año de grado distinto → de fondo')
m = E.marcasDelEstudio({ ...v, aca_fecha_fin: '2011-06-01' }, copia, listas)
ok(m.aca_fecha_fin.nivel === 'informativo', 'misma fecha de grado a 14 días y el mismo año: informativo')
ok(Object.keys(E.marcasDelEstudio({ ...v, aca_titulo: '' }, copia, listas)).indexOf('aca_titulo') < 0, 'celda vacía: sin marca')

// Sugerencia de la pregunta 3
ok(E.sugerenciaDeFondo(E.marcasDelEstudio({ ...v, aca_titulo: 'Otro programa' }, copia, listas)).valor === 'Sí', 'otro título → se sugiere Sí')
ok(E.sugerenciaDeFondo(E.marcasDelEstudio({ ...v, aca_fecha_fin: '2011-06-18' }, copia, listas)).valor === 'Diferencia menor explicada', 'fecha a 3 días → diferencia menor')
ok(E.sugerenciaDeFondo(E.marcasDelEstudio({ ...v, aca_fecha_fin: '2011-09-30' }, copia, listas)).valor === 'Sí', 'fecha de fondo → Sí')
ok(E.sugerenciaDeFondo(E.marcasDelEstudio(v, copia, listas)) === null, 'todo coincide: sin sugerencia')

// Código = el de la primera pregunta que aplica
const ok5 = { p1: 'No', p2: 'No', p3: 'No', p4: 'No', p5: 'Sí se verificó' }
ok(E.codigoDePreguntas(ok5).codigo === '—', 'todo bien: «—»')
ok(E.codigoDePreguntas({ ...ok5, p1: 'Sí' }).codigo === 'ACA-01' && E.codigoDePreguntas({ ...ok5, p1: 'Sospecha sin confirmar' }).codigo === 'ALR-ACA-01', 'pregunta 1')
ok(E.codigoDePreguntas({ ...ok5, p2: 'Sí' }).codigo === 'ACA-02' && E.codigoDePreguntas({ ...ok5, p3: 'Sí' }).codigo === 'ACA-03' && E.codigoDePreguntas({ ...ok5, p4: 'Sí' }).codigo === 'ACA-05', 'preguntas 2, 3 y 4')
ok(E.codigoDePreguntas({ ...ok5, p5: 'Evaluado no aportó' }).codigo === 'ACA-04' && E.codigoDePreguntas({ ...ok5, p5: 'Institución no responde' }).codigo === 'ALR-ACA-02', 'pregunta 5')
ok(E.codigoDePreguntas({ ...ok5, p1: 'Sí', p3: 'Sí', p5: 'Evaluado no aportó' }).codigo === 'ACA-01', 'gana la primera que aplica')
ok(E.codigoDePreguntas({ ...ok5, p3: 'Diferencia menor explicada' }).codigo === '—', '«Diferencia menor explicada» no suma código')
ok(E.codigoDePreguntas({ ...ok5, p3: 'Diferencia menor explicada', p4: 'Sí' }).codigo === 'ACA-05', '…y no tapa a una pregunta posterior')
ok(E.preguntasSinResponder({ p1: 'No' }).length === 4, 'preguntas sin responder')
ok(E.leerPreguntas('basura') && Object.keys(E.leerPreguntas('basura')).length === 0 && E.leerPreguntas(E.escribirPreguntas(ok5)).p5 === 'Sí se verificó', 'las respuestas se guardan y leen en JSON')

// Aviso del documento y borrador
ok(E.avisoDocumento('En curso', 'Diploma').includes('un diploma') && E.avisoDocumento('Graduado', 'Diploma') === '' && E.avisoDocumento('En curso', 'Certificado de estudios') === '', 'aviso: estudio sin terminar con diploma o acta')
ok(E.borradorObservaciones(v) === 'La institución confirma que el evaluado es graduado de Administrador financiero en Universidad Ejemplo el 15/06/2011.', 'borrador de Observaciones')

// Reemplazar lo verificado
ok(!E.hayVerificado(v, copia, listas), 'solo lo precargado: no hay nada que perder')
ok(E.hayVerificado({ ...v, aca_titulo: 'Corregido a mano' }, copia, listas), 'un dato corregido a mano cuenta como verificado')
ok(E.hayVerificado({ ...v, aca_medio: 'Correo electrónico' }, copia, listas) && E.hayVerificado({ ...v, aca_ref: 'ACA-02' }, copia, listas), 'medio o calificación también')
ok(!E.hayVerificado({ aca_ref: '—' }, null, listas), 'sin nada diligenciado no pregunta')

// Fila sugerida de «hallazgos sin impacto»
const fi = E.filaInformativa({ ...v, aca_fecha_fin: '2011-06-18' }, E.marcasDelEstudio({ ...v, aca_fecha_fin: '2011-06-18' }, copia, listas))
ok(fi.i_situacion.startsWith('Diferencia menor') && fi.i_detalle.includes('la fecha de finalización'), 'fila sugerida para la sección 4')

// Faltantes de AC-03
const col = (id, clave, extra = {}) => ({ idColumna: id, clave, obligatorio: false, automatico: false, calculado: false, ...extra })
const claves = ['aca_criterio', 'aca_estudio_sel', 'aca_preguntas', 'aca_soporte', 'aca_ref', 'aca_observaciones', 'aca_ac01_base', 'aca_vobo', 'aca_fecha_sol_eval']
const sec = (vals) => {
  const columnas = claves.map((c, i) => col(i + 1, c))
  const valores = Object.fromEntries(Object.entries(vals).map(([c, x]) => [claves.indexOf(c) + 1, x]))
  return [
    { idSeccion: 1, clave: 'datos_evaluado', tipo: 'CAMPOS', columnas: [], valores: {}, filas: [] },
    { idSeccion: 2, clave: 'estudio_verificado', tipo: 'CAMPOS', columnas, valores, filas: [] },
    { idSeccion: 3, clave: 'gestiones', tipo: 'TABLA', columnas: [], valores: {}, filas: [] },
    { idSeccion: 4, clave: 'concepto_analista', tipo: 'CAMPOS', columnas: [], valores: {}, filas: [] },
  ]
}
const base = { aca_criterio: 'Último estudio finalizado', aca_estudio_sel: '968', aca_preguntas: E.escribirPreguntas(ok5), aca_soporte: '[{"n":1}]', aca_ref: '—' }
const items = (vals) => (F.faltantesDeAcademica(sec(vals), 1).grupos.find(g => g.id === 'estudio')?.items ?? []).map(i => i.clave)
ok(items(base).length === 0, 'AC-03: preguntas, soporte y calificación al día: sin faltantes')
ok(items({ ...base, aca_preguntas: E.escribirPreguntas({ p1: 'No' }) }).includes('aca_preguntas'), 'faltan respuestas a las preguntas')
ok(items({ ...base, aca_soporte: '' }).includes('aca_soporte') && items({ ...base, aca_soporte: '[]' }).includes('aca_soporte'), 'el soporte es obligatorio')
ok(!items({ ...base, aca_soporte: '', aca_preguntas: E.escribirPreguntas({ ...ok5, p5: 'Evaluado no aportó' }), aca_ref: 'ACA-04' }).includes('aca_soporte'), 'con ACA-04 el soporte son las gestiones')
ok(items({ ...base, aca_preguntas: E.escribirPreguntas({ ...ok5, p5: 'Evaluado no aportó' }), aca_ref: 'ACA-04' }).includes('aca_fecha_sol_eval'), 'ACA-04 exige la fecha de solicitud al evaluado')
ok(items({ ...base, aca_ref: 'ACA-02' }).includes('aca_observaciones'), 'un código distinto al sugerido exige el motivo en Observaciones')
ok(!items({ ...base, aca_ref: 'ACA-02', aca_observaciones: 'Por llamada con la institución' }).includes('aca_observaciones'), '…y con el motivo escrito ya no falta')
const p1 = E.escribirPreguntas({ ...ok5, p1: 'Sí' })
ok(items({ ...base, aca_preguntas: p1, aca_ref: 'ACA-01' }).includes('aca_ac01_base'), 'ACA-01 exige el sustento')
ok(items({ ...base, aca_preguntas: p1, aca_ref: 'ACA-01', aca_ac01_base: 'Alteración evidente en inspección visual' }).includes('aca_vobo'), 'alteración evidente exige el visto bueno')
ok(!items({ ...base, aca_preguntas: p1, aca_ref: 'ACA-01', aca_ac01_base: 'Confirmado por la institución' }).includes('aca_vobo'), 'confirmado por la institución no lo exige')

// AC-04
const sug = E.sugerenciasInformativasAcademica({ aca_preguntas: E.escribirPreguntas({ ...ok5, p3: 'Diferencia menor explicada' }), aca_ref: '—', aca_titulo: 'X' }, E.marcasDelEstudio({ ...v, aca_fecha_fin: '2011-06-18' }, copia, listas))
ok(sug.length === 1 && sug[0].situacion.startsWith('Diferencia menor'), 'AC-04: diferencia menor explicada → fila sugerida')
ok(E.sugerenciasInformativasAcademica({ aca_ref: 'ALR-ACA-01' }, {})[0].situacion.includes('ALR-ACA-01') && E.sugerenciasInformativasAcademica({ aca_ref: 'ALR-ACA-02' }, {})[0].situacion.includes('ALR-ACA-02'), 'AC-04: las alertas ALR se sugieren')
ok(E.sugerenciasInformativasAcademica({ aca_ref: 'ACA-02' }, {}).length === 0 && E.sugerenciasInformativasAcademica({ aca_ref: '—' }, {}).length === 0, 'AC-04: un código con puntos o sin código no sugiere nada')
const c0 = E.borradorConceptoAcademica({ v, hallazgos: [], informativos: [] })
ok(c0.includes('es graduado de') && c0.includes('coincide con lo declarado') && c0.includes('No registra hallazgos') && !c0.includes('poligraf'), 'AC-04: concepto sin hallazgos')
const c2 = E.borradorConceptoAcademica({ v: { ...v, aca_ref: 'ACA-02' }, hallazgos: [{}], informativos: [], impacto: 'Registra un hallazgo.' })
ok(c2.startsWith('La institución no registra el título declarado.') && c2.includes('prueba de poligrafía') && !/minti|falso|culpable/i.test(c2), 'AC-04: tono de las reglas y recomienda indagar en la poligrafía')
ok(!/minti|culpable/i.test(E.borradorConceptoAcademica({ v: { ...v, aca_ref: 'ACA-01' }, informativos: [] })), 'AC-04: ACA-01 sin acusaciones')
ok(items({ ...base, aca_preguntas: E.escribirPreguntas({ ...ok5, p5: 'Institución no responde' }), aca_ref: 'ALR-ACA-02', aca_soporte: '' }).includes('ac-gestiones'), 'AC-04: ALR-ACA-02 sin gestiones → falta registrarlas')
ok(!items({ ...base }).includes('ac-gestiones'), 'AC-04: sin código, gestiones vacías no cuentan')

// AC-06
ok(E.esDelExterior({ aca_categoria: 'Estudios en el exterior' }) && !E.esDelExterior({ aca_categoria: 'Educación superior' }), 'AC-06: la categoría decide si es del exterior')
ok(E.sugerirCriterioNoVerificable({ v: { aca_categoria: 'Estudios en el exterior' }, plazoVencido: true, criterio: 'Último estudio finalizado' }), 'AC-06: exterior con plazo vencido sugiere el criterio no verificable')
ok(!E.sugerirCriterioNoVerificable({ v: { aca_categoria: 'Estudios en el exterior' }, plazoVencido: false, criterio: 'Último estudio finalizado' }) && !E.sugerirCriterioNoVerificable({ v: { aca_categoria: 'Estudios en el exterior' }, plazoVencido: true, criterio: 'Último estudio no verificable en el plazo' }) && !E.sugerirCriterioNoVerificable({ v: { aca_categoria: 'Educación superior' }, plazoVencido: true, criterio: 'Último estudio finalizado' }), 'AC-06: sin plazo vencido, con el criterio ya puesto o en Colombia no sugiere')
const sinEst = F.faltantesDeAcademica([], 0)
ok(sinEst.total === 0, 'AC-06: sin secciones no falta nada')

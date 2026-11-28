// Prueba de las reglas de LB-03 (el frontend no tiene corredor): node src/pages/analista/reglasLaboral.prueba.mjs
import * as R from './reglasLaboral.js'
const ok = (c, m) => { console.log(c ? 'OK  ' : 'FALLA', m); if (!c) process.exitCode = 1 }
const exp = { id: 1166, empresa: 'Inversiones Ejemplo S.A.S.', cargo: 'Jefe financiero', fecha_inicio: '01/03/2019', fecha_fin: '', labora_actualmente: 'Sí', ciudad: 'Bogotá', salario: '9500000', motivo_retiro: '', llamados_atencion: 'Ninguno' }
const v = R.vinculacionDesde(exp)
ok(v.v_fecha_retiro === 'Actualmente' && v.v_decl_inicio === '2019-03-01' && v.v_decl_salario === '9500000', 'vinculación nace de lo declarado')
ok(R.compararFecha('15/03/2019', v.v_decl_inicio).dias === 14 && R.compararFecha('15/03/2019', v.v_decl_inicio).nivel === 'informativo', '#5 15/03/2019 → 14 días informativo')
ok(R.compararFecha('01/03/2019', v.v_decl_inicio).nivel === 'coincide', 'misma fecha coincide')
ok(R.compararFecha('01/06/2019', v.v_decl_inicio).nivel === 'fondo', '#6 3 meses → de fondo')
ok(R.codigosSugeridos({ ...v, v_validado_por: R.EMPRESA, v_fecha_inicio: '01/06/2019' }).codigos.includes('LAB-03'), '#6 sugiere LAB-03')
const w = { ...v, v_validado_por: R.EMPRESA, v_fecha_retiro: '30/06/2022', v_decl_fin: '2022-06-30', v_motivo_clase: 'Despido con justa causa', v_motivo: 'x' }
ok(R.codigosSugeridos(w).codigos.includes('LAB-05'), '#7 justa causa sugiere LAB-05')
const pn = R.preguntasDeCodigo({ ...v, v_tipo: R.PERSONA_NATURAL, v_validado_por: R.JEFE })
ok(pn.find(x => x.codigo === 'LAB-09').aplica && pn.find(x => x.codigo === 'LAB-12').aplica, '#8 persona natural habilita LAB-09 y LAB-12')
ok(!R.preguntasDeCodigo({ ...v, v_validado_por: R.EMPRESA }).find(x => x.codigo === 'LAB-09').aplica, 'empresa formal no ve LAB-09')
const sb = R.preguntasDeCodigo({ ...w, v_validado_por: R.SABANA })
ok(!sb.find(x => x.codigo === 'LAB-05').aplica && !sb.find(x => x.codigo === 'LAB-07').aplica, '#9 sábana: sin motivo ni llamados')
ok(R.llamadasQueFaltan({ ...v, v_validado_por: R.PENDIENTE }, 1) === 2 && R.llamadasQueFaltan({ ...v, v_validado_por: R.EMPRESA }, 0) === 0, '#10 Pendiente con 1 llamada → faltan 2')
const nd = { ...v, v_no_declarada: 'Sí', v_validado_por: R.SABANA }
ok(!R.preguntasDeCodigo(nd).find(x => x.codigo === 'LAB-10').aplica && R.preguntasDeCodigo({ ...nd, v_mas_30: 'Sí' }).find(x => x.codigo === 'LAB-10').aplica, '#12 LAB-10 solo si no declarada y > 30 días')
const f = R.fechaLimite(new Date(2026, 10, 4), new Set(['2026-11-16']))
ok(R.fechaAIso(f) === '2026-11-10', `#11 4 días hábiles desde miércoles 04/11/2026 = ${R.fechaAIso(f)}`)
const f2 = R.fechaLimite(new Date(2026, 10, 12), new Set(['2026-11-16']))
ok(R.fechaAIso(f2) === '2026-11-19', `festivo lunes 16/11 se salta: ${R.fechaAIso(f2)}`)
let c = R.conCodigo({ v_ref1: '—', v_ref2: '—' }, 'LAB-03', true); c = R.conCodigo(c, 'LAB-05', true)
ok(c.v_ref1 === 'LAB-03' && c.v_ref2 === 'LAB-05' && R.conCodigo(c, 'LAB-07', true) === null, 'máximo dos códigos')
ok(R.conCodigo(c, 'LAB-03', false).v_ref1 === 'LAB-05', 'quitar el primero sube el segundo')
ok(!R.cambioLaDeclaracion(v, exp) && R.cambioLaDeclaracion(v, { ...exp, cargo: 'Gerente' }), 'detecta cambio de declaración')
ok(R.dentroDelPeriodo(exp, '11/2022', '11/2026') && !R.dentroDelPeriodo({ ...exp, fecha_inicio: '01/01/2010', fecha_fin: '31/12/2015' }, '11/2022', '11/2026'), 'dentro/fuera del periodo')
ok(R.claseDeMotivo('Renuncia voluntaria por mejor oferta') === 'Renuncia voluntaria' && R.claseDeMotivo('despido sin justa causa') === 'Despido sin justa causa', 'clasifica motivos')

// ── LB-04: inactividad con fechas confirmadas ──
const hoy = new Date(2026, 10, 4)
const vA = { v_fecha_inicio: '01/01/2020', v_fecha_retiro: '31/01/2023', v_decl_inicio: '2020-01-01', v_decl_fin: '2023-01-31', v_empresa: 'A' }
const vB = { v_fecha_inicio: '01/06/2023', v_fecha_retiro: 'Actualmente', v_decl_inicio: '2023-06-01', v_decl_fin: '', v_empresa: 'B' }
let hu = R.huecosDeInactividad('11/2022', '11/2026', R.intervalosDeVinculaciones([vA, vB], hoy), hoy)
ok(hu.length === 1 && hu[0].dias === 121 && !hu[0].declarada, '#13 hueco de 120 días con fechas confirmadas (' + hu[0]?.dias + ')')
hu = R.huecosDeInactividad('11/2022', '11/2026', R.intervalosDeVinculaciones([vA, { ...vB, v_fecha_inicio: '' }], hoy), hoy)
ok(hu.length === 1 && hu[0].declarada, 'sin fecha confirmada usa la declarada y lo avisa')
ok(R.huecosDeInactividad('11/2022', '11/2026', R.intervalosDeVinculaciones([{ ...vA, v_fecha_retiro: '15/05/2023' }, vB], hoy), hoy).length === 0, 'hueco de 17 días no cuenta')
ok(R.huecosDeInactividad('11/2022', '11/2026', [], hoy).length === 1, 'sin vinculaciones todo el periodo es un hueco (la pantalla no lo crea sola)')
let id = 0
const nid = () => `n${++id}`
hu = R.huecosDeInactividad('11/2022', '11/2026', R.intervalosDeVinculaciones([vA, vB], hoy), hoy)
let s = R.sincronizarInactividad([], hu, [], nid)
ok(s.filas.length === 1 && s.cambio && s.filas[0].v.in_dias === '121', 'crea la fila calculada')
const filas = [{ ...s.filas[0], idFila: 5, v: { ...s.filas[0].v, in_corroboracion: 'Solo declaración del evaluado' } }]
s = R.sincronizarInactividad(filas, hu, [], nid)
ok(!s.cambio && s.filas[0].v.in_corroboracion === 'Solo declaración del evaluado', 'sin cambios conserva la corroboración')
const hu2 = R.huecosDeInactividad('11/2022', '11/2026', R.intervalosDeVinculaciones([vA, { ...vB, v_fecha_inicio: '15/06/2023' }], hoy), hoy)
s = R.sincronizarInactividad(filas, hu2, [], nid)
ok(s.filas.length === 1 && s.filas[0].v.in_corroboracion && s.filas[0].v.in_aviso.startsWith('El periodo cambió'), 'periodo que cambia: se conserva y avisa')
s = R.sincronizarInactividad(filas, [], [], nid)
ok(s.filas[0].v.in_aviso.startsWith('Ya no hay') && s.filas[0].v.in_corroboracion, 'ya no hay hueco pero con corroboración: se conserva con aviso')
s = R.sincronizarInactividad([{ ...filas[0], v: { ...filas[0].v, in_corroboracion: '' } }], [], [], nid)
ok(s.filas[0].v.in_origen === 'Descartado', 'guardada sin corroboración queda descartada')
s = R.sincronizarInactividad([{ ...filas[0], idFila: null }], [], [], nid)
ok(s.filas.length === 0, 'sin guardar se quita')
s = R.sincronizarInactividad([{ localId: 'm', idFila: 9, v: { in_origen: 'Manual', in_desde: '2023-01-01' } }], hu, [], nid)
ok(s.filas.length === 2 && s.filas[0].v.in_origen === 'Manual', 'lo manual no se toca')
const decl = [{ fecha_inicio: '15/02/2023', fecha_fin: '31/05/2023', actividad: 'Estudió inglés', justificacion: '', quien_confirma: 'Certificado del instituto' }]
s = R.sincronizarInactividad([], hu, decl, nid)
ok(s.filas[0].v.in_actividad.includes('Estudió inglés'), 'precarga la actividad que explicó el evaluado')

// ── LB-05: revisión y envío ──
import * as Q from './reglasRevisionLaboral.js'
const base = { ...R.vinculacionDesde({ id: 1, empresa: 'Inversiones Ejemplo S.A.S.', cargo: 'Jefe financiero', fecha_inicio: '01/03/2019', fecha_fin: '', labora_actualmente: 'Sí', ciudad: 'Bogotá', salario: '9500000', llamados_atencion: 'Ninguno' }), v_validado_por: R.EMPRESA, v_llamados: 'Ninguno', v_soporte: '["a.pdf"]' }
ok(Q.faltantesDeVinculacion(base, 1, 0).length === 0, 'vinculación completa no tiene faltantes')
let fl = Q.faltantesDeVinculacion({ ...base, v_validado_por: R.PENDIENTE, v_soporte: '' }, 2, 1)
ok(fl.some(x => x.texto.includes('sigue «Pendiente»')) && fl.some(x => x.texto.includes('faltan 2 llamadas')) && fl.some(x => x.clave === 'vinc-2:v_soporte'), '#10 Pendiente: bloquea, faltan 2 llamadas y soporte; cada faltante lleva su celda')
ok(Q.faltantesDeVinculacion({ ...base, v_llamados: 'Cumplimiento' }, 1, 0).some(x => x.clave === 'vinc-1:v_llamados_detalle'), 'llamados sin detalle')
ok(Q.faltantesDeVinculacion({ ...base, v_ref1: 'LAB-01' }, 1, 0).some(x => x.clave === 'vinc-1:v_lab01_base'), 'LAB-01 pide sustento')
ok(Q.estaFueraDelPeriodo({ ...base, v_decl_inicio: '2010-01-01', v_decl_fin: '2015-12-31' }, '11/2022', '11/2026') && !Q.estaFueraDelPeriodo(base, '11/2022', '11/2026'), 'fuera del periodo no se exige')
const dif = { ...base, v_fecha_inicio: '15/03/2019', v_salario: '$8.000.000', v_cargo: 'Gerente financiero', v_estado_empresa: 'En liquidación', v_observaciones: 'Por política no dan más información' }
const sg = Q.sugerenciasInformativas([dif], [{ desde: '2023-02-01', hasta: '2023-06-01', dias: '120', corroboracion: 'Referencia confirmó' }], new Date(2026, 10, 4)).map(x => x.situacion)
ok(['Salario distinto al declarado por el evaluado', 'Empresa en liquidación', 'La empresa no entrega información por política interna', 'Diferencia de fechas de hasta 2 meses frente a lo declarado', 'Cargo con otro nombre', 'Periodo de inactividad corroborado'].every(x => sg.includes(x)), 'sugiere los informativos de las reglas: ' + sg.length)
ok(Q.sugerenciasInformativas([{ ...base, v_validado_por: R.SABANA }], [], new Date(2026, 10, 4)).some(x => x.situacion.startsWith('Vinculación validada solo con sábana')), 'solo sábana pensional')
const sim = Q.sugerenciasInformativas([base, { ...base, v_empresa: 'Otra', v_fecha_inicio: '01/01/2024', v_fecha_retiro: '31/12/2024', v_decl_inicio: '2024-01-01', v_decl_fin: '2024-12-31' }], [], new Date(2026, 10, 4))
ok(sim.some(x => x.situacion === 'Vinculaciones simultáneas'), 'vinculaciones simultáneas')
const bor = Q.borradorConceptoLaboral({ periodo: { desde: '11/2022', hasta: '11/2026' }, vinculaciones: [base], hallazgos: [], informativos: [], inactividades: [] })
ok(bor.includes('la empresa informa la vinculación vigente desde el 01/03/2019') && !/incumplid|culpable/i.test(bor) && !bor.includes('poligrafía'), 'borrador: tono de las reglas, sin hallazgos no sugiere poligrafía')
const bor2 = Q.borradorConceptoLaboral({ periodo: { desde: '11/2022', hasta: '11/2026' }, vinculaciones: [{ ...base, v_validado_por: R.PENDIENTE }], hallazgos: [{ ref: 'LAB-11', descripcion: 'No aportó el certificado', fuente: 'Vinculación 1', detalle: '', puntos: '20' }], informativos: [], inactividades: [] })
ok(bor2.includes('pendiente de verificar') && bor2.includes('poligrafía'), 'borrador con Pendiente y hallazgos sugiere indagar en poligrafía')

// PF-03 · Preguntas de la entrevista con respuesta Sí / No, frase estándar y admisión en el lugar.
// Los datos que se guardan son los de siempre (el texto de la respuesta y las listas que ya calificaban); la respuesta Sí / No va en una
// columna aparte que no se imprime (`resp`). La pantalla llena sola las listas de "¿admite?" (Sí al responder Sí, en blanco al responder No).

export const PERSONAL = 'Personal o de salud (no se imprime)'

const CAT = {
  roboMenor: 'Robo en empleos: menos de un salario mínimo, una o dos veces',
  roboMayor: 'Robo en empleos: un salario mínimo o más, o más de dos veces',
  soborno: 'Sobornos o comisiones irregulares',
  complicidad: 'Complicidad en ilícitos contra un empleador',
  alteracion: 'Alteración u ocultamiento de documentos, registros o información',
  despido: 'Despido por mala conducta o faltas graves',
  falsos: 'Documentos falsos o comprados (títulos, certificados)',
  antecedentes: 'Antecedentes, detenciones o medidas correctivas',
  laboral: 'Experiencia laboral, llamados de atención o descargos',
  frecuente: 'Consumo frecuente de drogas (puede afectar su trabajo)',
  ocasional: 'Consumo ocasional de drogas',
  problemasAlcohol: 'Problemas por consumo de alcohol',
  problemasDrogas: 'Problemas por consumo de drogas',
}

const VINCULO = {
  'Activo con grupos ilegales': 'Vínculo activo con grupos ilegales',
  'Venta, transporte o tráfico de drogas': 'Venta, transporte o tráfico de drogas',
  'Beneficio de actividades ilícitas': 'Beneficio de actividades ilícitas',
  'Relación social actual con personas en actividades ilícitas': 'Relación social actual con personas en actividades ilícitas',
  'Vínculo pasado terminado': 'Vínculo pasado con personas en actividades ilícitas (terminado)',
}

// tipo 'sino': botones Sí / No (columna `resp`). tipo 'frecuencia': la frecuencia (Nulo / Ocasional / Frecuente) hace de respuesta.
// texto = campo de texto de la respuesta · flags = listas "—/Sí" que se llenan solas · listas = listas cortas que se abren con Sí ·
// subs = preguntas de Sí que se marcan con una casilla · extras = otros textos que se abren con Sí · frase = clave de la frase estándar ·
// categoria(valores por clave) = categoría de admisión por defecto · categorias = las que esta pregunta puede poner sola · temasCliente = palabras
// con las que se reconoce un tema pedido por el cliente.
export const PREGUNTAS = [
  { id: 'robos', tipo: 'sino', texto: 'al_robos', resp: 'al_robos_resp', listas: ['al_robos_alcance'], frase: 'robos',
    categorias: [CAT.roboMenor, CAT.roboMayor], categoria: (v) => (v.al_robos_alcance?.startsWith('Menos') ? CAT.roboMenor : v.al_robos_alcance ? CAT.roboMayor : null),
    temasCliente: ['robo', 'hurto', 'sabotaje', 'apropiaci'] },
  { id: 'complicidad', tipo: 'sino', texto: 'al_complicidad', resp: 'al_complicidad_resp', flags: ['al_complicidad_si'], frase: 'complicidad',
    categorias: [CAT.complicidad], categoria: () => CAT.complicidad, temasCliente: ['complicidad', 'ilícit', 'ilicit', 'lavado'] },
  { id: 'alteracion', tipo: 'sino', texto: 'al_alteracion', resp: 'al_alteracion_resp', flags: ['al_alteracion_si'], frase: 'alteracion',
    categorias: [CAT.alteracion], categoria: () => CAT.alteracion, temasCliente: ['alteraci', 'document', 'mintió', 'mintio', 'información'] },
  { id: 'despido', tipo: 'sino', texto: 'al_despido', resp: 'al_despido_resp', flags: ['al_despido_si'], frase: 'despido',
    categorias: [CAT.despido], categoria: () => CAT.despido, temasCliente: ['despido', 'conducta'] },
  { id: 'sobornos', tipo: 'sino', texto: 'al_sobornos', resp: 'al_sobornos_resp', flags: ['al_sobornos_si'], frase: 'sobornos',
    categorias: [CAT.soborno], categoria: () => CAT.soborno, temasCliente: ['soborno', 'comisi', 'corrupci'] },
  { id: 'alcohol', tipo: 'frecuencia', texto: 'ac_alcohol', frecuencia: 'ac_alcohol_frec', subs: ['ac_alcohol_problemas', 'ac_alcohol_trabajo', 'ac_alcohol_conduccion'], frase: 'alcohol',
    categorias: [CAT.problemasAlcohol], categoria: (v) => (v.ac_alcohol_problemas === 'Sí' ? CAT.problemasAlcohol : null), temasCliente: ['alcohol'] },
  { id: 'drogas', tipo: 'frecuencia', texto: 'ac_drogas', frecuencia: 'ac_drogas_frec', listas: ['ac_drogas_cuando'], subs: ['ac_drogas_problemas', 'ac_drogas_trabajo', 'ac_drogas_conduccion'], frase: 'drogas',
    categorias: [CAT.frecuente, CAT.ocasional, CAT.problemasDrogas],
    categoria: (v) => (v.ac_drogas_problemas === 'Sí' ? CAT.problemasDrogas : v.ac_drogas_frec === 'Frecuente' || v.ac_drogas_cuando?.startsWith('Frecuente') ? CAT.frecuente : v.ac_drogas_frec ? CAT.ocasional : null),
    temasCliente: ['droga', 'psicoactiv', 'estupefaciente'] },
  { id: 'falsificacion', tipo: 'sino', texto: 'ac_falsificacion', resp: 'ac_falsificacion_resp', flags: ['ac_falsificacion_si'], frase: 'falsificacion',
    categorias: [CAT.falsos], categoria: () => CAT.falsos, temasCliente: ['falsific', 'falso'] },
  { id: 'antecedentes', tipo: 'sino', texto: 'ac_antecedentes', resp: 'ac_antecedentes_resp', frase: 'antecedentes', admTipo: true,
    categorias: [CAT.antecedentes], categoria: () => CAT.antecedentes, temasCliente: ['antecedente', 'judicial', 'proceso'] },
  { id: 'vinculos', tipo: 'sino', texto: 'ac_vinculos', resp: 'ac_vinculos_resp', listas: ['ac_vinculos_tipo'], frase: 'vinculos',
    categorias: Object.values(VINCULO), categoria: (v) => VINCULO[v.ac_vinculos_tipo] ?? null, temasCliente: ['vínculo', 'vinculo', 'grupos', 'delincuen', 'tráfico', 'trafico'] },
  { id: 'llamados', tipo: 'sino', texto: 'ex_llamados', resp: 'ex_llamados_resp', frase: 'llamados',
    categorias: [CAT.laboral], categoria: () => CAT.laboral, temasCliente: ['llamado'] },
  { id: 'descargos', tipo: 'sino', texto: 'ex_descargos_empresa', resp: 'ex_descargos_resp', extras: ['ex_descargos_motivo'], frase: 'descargos',
    categorias: [CAT.laboral], categoria: () => CAT.laboral, temasCliente: ['descargo'] },
]

// Observaciones que, vacías, dicen "Ninguna." (educación y vehículos).
export const OBSERVACIONES_VACIAS = ['ed_obs', 've_obs']

// clave de columna -> { pregunta, principal } para que la pantalla sepa qué columnas pinta la pregunta y cuáles se esconden.
export const PREGUNTA_POR_COLUMNA = (() => {
  const m = {}
  PREGUNTAS.forEach(q => {
    const claves = [q.texto, q.resp, q.frecuencia, ...(q.flags ?? []), ...(q.listas ?? []), ...(q.subs ?? []), ...(q.extras ?? [])].filter(Boolean)
    claves.forEach(c => { m[c] = { pregunta: q, principal: c === (q.frecuencia ?? q.texto) } })
  })
  return m
})()

export const PREGUNTA_POR_ID = Object.fromEntries(PREGUNTAS.map(q => [q.id, q]))

// ¿El tema que escogió el cliente corresponde a esta pregunta?
export function temaDelCliente(pregunta, temas = []) {
  const claves = pregunta.temasCliente ?? []
  return temas.find(t => claves.some(k => `${t.nombre} ${t.detalle ?? ''}`.toLowerCase().includes(k))) ?? null
}

// PF-04 · Preguntas del módulo del candidato frente a lo que manifestó en la prueba. `prefijo` = comienzo del texto de la pregunta que el
// candidato respondió en su link (se reconoce por el texto porque la respuesta guarda una copia de la pregunta). `relacionada` = pregunta
// de la entrevista con Sí / No que se contrasta con lo que el candidato declaró.
export const COMPARACIONES = [
  { id: 'fd1', prefijo: '¿Tiene actualmente obligaciones en mora', modulo: 'fd1_modulo', prueba: 'fd1_prueba', cmp: 'fd1_cmp' },
  { id: 'fd2', prefijo: '¿Tiene embargos o procesos de cobro', modulo: 'fd2_modulo', prueba: 'fd2_prueba', cmp: 'fd2_cmp' },
  { id: 'pd1', prefijo: '¿Tiene o ha tenido procesos judiciales', modulo: 'pd1_modulo', prueba: 'pd1_prueba', cmp: 'pd1_cmp', relacionada: 'antecedentes' },
  { id: 'pd2', prefijo: '¿Tiene o ha tenido órdenes de captura', modulo: 'pd2_modulo', prueba: 'pd2_prueba', cmp: 'pd2_cmp', relacionada: 'antecedentes' },
  { id: 'pd3', prefijo: '¿Tiene o ha tenido sanciones disciplinarias', modulo: 'pd3_modulo', prueba: 'pd3_prueba', cmp: 'pd3_cmp', relacionada: 'antecedentes' },
]

// El módulo se pinta una vez, en su campo «respondió en el link»; las otras dos columnas las maneja la comparación.
export const COMPARACION_POR_COLUMNA = (() => {
  const m = {}
  COMPARACIONES.forEach(c => [c.modulo, c.prueba, c.cmp].forEach(k => { m[k] = { comparacion: c, principal: k === c.modulo } }))
  return m
})()

// Lo que el candidato declaró para cada comparación: { fd1: { respuesta: 'Sí', detalle }, ... } a partir de las declaraciones de la ficha.
export function declaradoPorComparacion(declaraciones = []) {
  const r = {}
  COMPARACIONES.forEach(c => {
    const d = declaraciones.find(x => (x.pregunta ?? '').startsWith(c.prefijo))
    if (d) r[c.id] = { respuesta: d.respuesta, detalle: d.detalle }
  })
  return r
}

export const textoDeclarado = (d) => (d ? `${d.respuesta}${d.detalle ? ` — ${d.detalle}` : ''}` : '')

// ¿El candidato declaró «Sí» en alguna de las preguntas que se contrastan con esta pregunta de la entrevista?
export function hayConflicto(preguntaId, declarado) {
  return COMPARACIONES.some(c => c.relacionada === preguntaId && declarado[c.id]?.respuesta === 'Sí')
}

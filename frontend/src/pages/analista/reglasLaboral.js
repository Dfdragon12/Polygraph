// LB-03 · Reglas puras de la verificación laboral que usa la pantalla de vinculaciones: comparación de lo confirmado con lo declarado, creación de vinculaciones desde la
// hoja de vida y sugerencia de códigos. Todo es SUGERENCIA: el analista confirma (hasta dos códigos por vinculación, v_ref1 y v_ref2); el cálculo de la calificación sigue
// siendo el del servidor.

export const ACTUAL = 'Actualmente'
export const EMPRESA = 'La empresa'
export const JEFE = 'Jefe inmediato (persona natural o informal)'
export const SABANA = 'Sábana pensional o bases de datos'
export const PENDIENTE = 'Pendiente'
export const PERSONA_NATURAL = 'Persona natural o informal'
export const SIN_CODIGO = '—'
export const DIAS_DE_FONDO = 61   // más de 2 meses = de fondo; hasta 2 meses es informativo
export const LLAMADAS_OBLIGATORIAS = 3

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
export const norm = (s) => String(s ?? '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, ' ').trim()
export const iguales = (a, b) => norm(a) === norm(b)
export const soloDigitos = (t) => String(t ?? '').replace(/\D/g, '')
export const aMoneda = (digitos) => (digitos ? `$${Number(digitos).toLocaleString('es-CO')}` : '')

// ── Fechas: lo confirmado se guarda como «dd/MM/aaaa» (texto, como lo imprime el Word); lo declarado, como AAAA-MM-DD ──────────────────────────────────
export const textoAFecha = (t) => {
  const x = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(String(t ?? '').trim())
  if (!x) return null
  const f = new Date(+x[3], +x[2] - 1, +x[1])
  return f.getMonth() === +x[2] - 1 ? f : null
}
export const isoAFecha = (iso) => {
  const x = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso ?? ''))
  return x ? new Date(+x[1], +x[2] - 1, +x[3]) : null
}
export const fechaATexto = (f) => `${String(f.getDate()).padStart(2, '0')}/${String(f.getMonth() + 1).padStart(2, '0')}/${f.getFullYear()}`
export const fechaAIso = (f) => `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, '0')}-${String(f.getDate()).padStart(2, '0')}`
export const textoAIso = (t) => { const f = textoAFecha(t); return f ? fechaAIso(f) : '' }
export const isoATexto = (iso) => { const f = isoAFecha(iso); return f ? fechaATexto(f) : '' }
const DIA = 86400000
export const diasEntre = (a, b) => Math.round((b - a) / DIA)

/** Compara una fecha confirmada con la declarada. null = no se puede comparar; si no, { dias, nivel: 'coincide' | 'informativo' | 'fondo' }. */
export function compararFecha(confirmada, declaradaIso) {
  const c = textoAFecha(confirmada)
  const d = isoAFecha(declaradaIso)
  if (!c || !d) return null
  const dias = Math.abs(diasEntre(c, d))
  return { dias, nivel: dias === 0 ? 'coincide' : dias > DIAS_DE_FONDO ? 'fondo' : 'informativo' }
}

/** El retiro: «Actualmente» contra una declaración sin fecha de fin coincide; una fecha contra «Actualmente» es de fondo. */
export function compararRetiro(confirmado, declaradoIso) {
  const sigue = iguales(confirmado, ACTUAL)
  if (sigue && !tiene(declaradoIso)) return { dias: 0, nivel: 'coincide' }
  if (sigue !== !tiene(declaradoIso) && (tiene(confirmado) || tiene(declaradoIso))) return { dias: null, nivel: 'fondo' }
  return compararFecha(confirmado, declaradoIso)
}

// ── Clasificaciones a partir de texto libre (solo sugieren) ──────────────────────────────────────────────────────────────────────────────────────────
export function claseDeMotivo(texto) {
  const t = norm(texto)
  if (!t) return ''
  if (/\bvigente|\bactual|\blabora/.test(t)) return 'Vigente (aún vinculado)'
  if (/sin justa/.test(t)) return 'Despido sin justa causa'
  if (/justa causa/.test(t)) return 'Despido con justa causa'
  if (/abandon/.test(t)) return 'Abandono del cargo'
  if (/mutuo/.test(t)) return 'Mutuo acuerdo'
  if (/renunc|voluntari|mejor oferta|nuevo trabajo|nueva oferta|decision propia/.test(t)) return 'Renuncia voluntaria'
  if (/despid|desvincul/.test(t)) return 'Despido sin justa causa'
  if (/terminaci|fin de contrato|vencimiento|obra o labor|termino fijo|finaliz/.test(t)) return 'Terminación o fin de contrato'
  return ''
}

export const LLAMADOS = ['Ninguno', 'Cumplimiento', 'Conducta / confianza']
export function claseDeLlamados(texto) {
  const t = norm(texto)
  if (!t) return ''
  if (/^ninguno|^no /.test(t) || t === 'no') return 'Ninguno'
  if (/^conducta|confianza|acoso|hurto|agresi/.test(t)) return 'Conducta / confianza'
  if (/^cumplimiento|llegada|desempe|incumpl|tarde/.test(t)) return 'Cumplimiento'
  return ''
}
export const detalleDeLlamados = (texto) => String(texto ?? '').replace(/^\s*(Ninguno|Cumplimiento|Conducta \/ confianza)\s*[:\-—]?\s*/i, '')
export const armarLlamados = (clase, detalle) => (clase === 'Ninguno' || !clase ? clase : `${clase}${tiene(detalle) ? `: ${detalle.trim()}` : ''}`)

// ── Vinculaciones nacidas de la hoja de vida ─────────────────────────────────────────────────────────────────────────────────────────────────────
/** ¿La experiencia declarada cae dentro del periodo validado «MM/AAAA» a «MM/AAAA»? Sin periodo, sí. */
export function dentroDelPeriodo(exp, desde, hasta, hoy = new Date()) {
  const d = /^(\d{2})\/(\d{4})$/.exec(String(desde ?? '').trim())
  const h = /^(\d{2})\/(\d{4})$/.exec(String(hasta ?? '').trim())
  if (!d || !h) return true
  const ini = new Date(+d[2], +d[1] - 1, 1)
  const fin = new Date(+h[2], +h[1], 0)
  const i = textoAFecha(exp.fecha_inicio)
  const f = textoAFecha(exp.fecha_fin) ?? hoy
  if (!i) return true
  return i <= fin && f >= ini
}

/** Valores (por clave) de una vinculación nueva, con lo declarado copiado y lo confirmado prellenado con lo declarado. */
export function vinculacionDesde(exp) {
  const sigue = !tiene(exp.fecha_fin) && /^s[ií]$/i.test(String(exp.labora_actualmente ?? ''))
  const v = {
    v_id_exp: String(exp.id),
    v_empresa: exp.empresa ?? '', v_ciudad: exp.ciudad ?? '', v_cargo: exp.cargo ?? '', v_tipo_contrato: exp.tipo_contrato ?? '',
    v_fecha_inicio: exp.fecha_inicio ?? '', v_fecha_retiro: sigue ? ACTUAL : (exp.fecha_fin ?? ''), v_telefono: exp.telefono_empresa ?? '',
    v_decl_empresa: exp.empresa ?? '', v_decl_cargo: exp.cargo ?? '', v_decl_inicio: textoAIso(exp.fecha_inicio), v_decl_fin: textoAIso(exp.fecha_fin),
    v_decl_motivo: exp.motivo_retiro ?? '', v_decl_ciudad: exp.ciudad ?? '', v_decl_contrato: exp.tipo_contrato ?? '', v_decl_salario: soloDigitos(exp.salario),
    v_decl_llamados: exp.llamados_atencion ?? '', v_decl_telefono: exp.telefono_empresa ?? '',
    v_tipo: 'Empresa', v_ref1: SIN_CODIGO, v_ref2: SIN_CODIGO,
  }
  if (sigue) v.v_motivo_clase = 'Vigente (aún vinculado)'
  return v
}

/** ¿El evaluado cambió su hoja de vida después de que se creó la vinculación? */
export function cambioLaDeclaracion(v, exp) {
  if (!exp || !tiene(v.v_id_exp)) return false
  const nuevo = vinculacionDesde(exp)
  return ['v_decl_empresa', 'v_decl_cargo', 'v_decl_motivo', 'v_decl_ciudad', 'v_decl_contrato', 'v_decl_llamados', 'v_decl_telefono']
    .some(k => !iguales(v[k], nuevo[k]))
    || (v.v_decl_inicio ?? '') !== nuevo.v_decl_inicio || (v.v_decl_fin ?? '') !== nuevo.v_decl_fin
    || soloDigitos(v.v_decl_salario) !== nuevo.v_decl_salario
}

// ── Códigos ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const tieneCodigo = (v, codigo) => v.v_ref1 === codigo || v.v_ref2 === codigo
/** Marca o quita un código en v_ref1 / v_ref2. Devuelve los dos valores nuevos o null si ya hay dos códigos y se quiere agregar un tercero. */
export function conCodigo(v, codigo, marcar) {
  let a = tiene(v.v_ref1) ? v.v_ref1 : SIN_CODIGO
  let b = tiene(v.v_ref2) ? v.v_ref2 : SIN_CODIGO
  if (marcar) {
    if (a === codigo || b === codigo) return { v_ref1: a, v_ref2: b }
    if (a === SIN_CODIGO) a = codigo
    else if (b === SIN_CODIGO) b = codigo
    else return null
  } else {
    if (a === codigo) { a = b; b = SIN_CODIGO } else if (b === codigo) b = SIN_CODIGO
  }
  return { v_ref1: a, v_ref2: b }
}

const nosePudo = (v) => v.v_validado_por === PENDIENTE
/**
 * Las preguntas de las reglas del analista, en orden. Cada una: { id, codigo, pregunta, aplica, razon, sugerido, porque }.
 * `aplica` falso = se muestra en gris con la razón. `sugerido` = el sistema cree que la respuesta es «Sí» (el analista decide).
 */
export function preguntasDeCodigo(v) {
  const validado = v.v_validado_por
  const pendiente = nosePudo(v)
  const sabana = validado === SABANA
  const vigente = iguales(v.v_fecha_retiro, ACTUAL) || v.v_motivo_clase === 'Vigente (aún vinculado)'
  const persona = v.v_tipo === PERSONA_NATURAL
  const noDeclarada = v.v_no_declarada === 'Sí'
  const sinValidar = !tiene(validado)
  const llamados = claseDeLlamados(v.v_llamados)
  const difInicio = compararFecha(v.v_fecha_inicio, v.v_decl_inicio)
  const difRetiro = compararRetiro(v.v_fecha_retiro, v.v_decl_fin)
  const fondoFechas = [difInicio, difRetiro].some(d => d?.nivel === 'fondo')
  const declarada = claseDeMotivo(v.v_decl_motivo)
  const noAplicaSinResultado = (cond, razon) => (cond ? razon : null)

  const sinMotivo = noAplicaSinResultado(vigente, 'Vigente: no hay motivo de retiro.') ?? noAplicaSinResultado(sabana, 'Sábana pensional: no valida el motivo de retiro.')
    ?? noAplicaSinResultado(pendiente, 'Pendiente: aún no hay respuesta de la empresa.') ?? noAplicaSinResultado(noDeclarada, 'Vinculación no declarada: no hay motivo declarado.')
  const sinLlamados = noAplicaSinResultado(sabana, 'Sábana pensional: no valida llamados de atención.') ?? noAplicaSinResultado(pendiente, 'Pendiente: aún no hay respuesta de la empresa.')
  const sinCertificado = noAplicaSinResultado(pendiente, 'Pendiente: aún no hay certificado.')

  const p = (id, codigo, pregunta, razonNoAplica, sugerido, porque) => ({ id, codigo, pregunta, aplica: !razonNoAplica, razon: razonNoAplica, sugerido: !razonNoAplica && !!sugerido, porque })
  return [
    p('falso', 'LAB-01', '¿El certificado es falso o adulterado?', sinCertificado ?? noAplicaSinResultado(sinValidar, 'Elige primero quién valida la vinculación.'), false, ''),
    p('desconoce', 'LAB-02', '¿La empresa no reconoce la vinculación?', noAplicaSinResultado(sabana, 'Sábana pensional: no hay respuesta de la empresa.') ?? sinCertificado ?? noAplicaSinResultado(sinValidar, 'Elige primero quién valida la vinculación.'), false, ''),
    p('fondo', 'LAB-03', '¿Los datos de fondo son distintos a lo declarado?', sinCertificado ?? noAplicaSinResultado(sinValidar, 'Elige primero quién valida la vinculación.'), fondoFechas,
      'las fechas difieren más de 2 meses (el cargo de otro nivel y el contrato que cambia la relación los confirmas tú)'),
    p('motivo', 'LAB-04', '¿El motivo de retiro declarado no coincide?', sinMotivo, declarada && v.v_motivo_clase && declarada !== v.v_motivo_clase && v.v_motivo_clase !== 'No informado',
      `declaró «${declarada}» y la empresa informa «${v.v_motivo_clase}»`),
    p('justa', 'LAB-05', '¿Hubo despido con justa causa?', sinMotivo, v.v_motivo_clase === 'Despido con justa causa', 'el motivo se clasificó como despido con justa causa'),
    p('abandono', 'LAB-06', '¿Hubo abandono del cargo?', sinMotivo, v.v_motivo_clase === 'Abandono del cargo', 'el motivo se clasificó como abandono del cargo'),
    p('llamado_cumplimiento', 'LAB-07', '¿Hubo llamados de atención por cumplimiento?', sinLlamados, llamados === 'Cumplimiento', 'los llamados son de cumplimiento'),
    p('llamado_conducta', 'LAB-08', '¿Hubo llamados de atención por conducta o confianza?', sinLlamados, llamados === 'Conducta / confianza', 'los llamados son de conducta o confianza'),
    p('concepto_malo', 'LAB-09', '¿Concepto malo, o no lo volvería a contratar?', noAplicaSinResultado(!persona, 'Solo con persona natural o informal.') ?? sinCertificado, false, ''),
    p('concepto_regular', 'LAB-12', '¿Concepto regular?', noAplicaSinResultado(!persona, 'Solo con persona natural o informal.') ?? sinCertificado, false, ''),
    p('no_declarada', 'LAB-10', '¿Vinculación no declarada, de más de 30 días, sin explicación razonable?',
      noAplicaSinResultado(!noDeclarada, 'Solo en vinculaciones halladas que el evaluado no declaró.') ?? noAplicaSinResultado(v.v_mas_30 !== 'Sí', 'Indica si dura más de 30 días.'), noDeclarada && v.v_mas_30 === 'Sí', 'es una vinculación hallada que dura más de 30 días'),
    p('no_aporto', 'LAB-11', '¿El evaluado no aportó el certificado en 4 días hábiles?', noAplicaSinResultado(!pendiente, 'Solo si la vinculación quedó Pendiente.'), false, ''),
    p('causa_ajena', 'ALR-LAB-01', '¿No se pudo verificar por una causa ajena?', noAplicaSinResultado(!pendiente, 'Solo si la vinculación quedó Pendiente.'), false, ''),
  ]
}

/** { codigos, razones } sugeridos para la vinculación. */
export function codigosSugeridos(v) {
  const s = preguntasDeCodigo(v).filter(x => x.sugerido)
  return { codigos: s.map(x => x.codigo), razones: s.map(x => `${x.codigo}: ${x.porque}`) }
}

// ── Gestiones ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
/** Cuántas llamadas faltan para las 3 obligatorias cuando la vinculación quedó Pendiente o con ALR-LAB-01. 0 = no aplica o ya están. */
export function llamadasQueFaltan(v, llamadasHechas) {
  const exige = nosePudo(v) || tieneCodigo(v, 'ALR-LAB-01')
  return exige ? Math.max(0, LLAMADAS_OBLIGATORIAS - llamadasHechas) : 0
}

/** 4 días hábiles después de `desde` (sin contar el de la solicitud), sin sábados, domingos ni festivos (`noHabiles`: Set de AAAA-MM-DD). */
export function fechaLimite(desde, noHabiles = new Set(), dias = 4) {
  const f = new Date(desde.getFullYear(), desde.getMonth(), desde.getDate())
  let n = 0
  while (n < dias) {
    f.setDate(f.getDate() + 1)
    const sem = f.getDay()
    if (sem !== 0 && sem !== 6 && !noHabiles.has(fechaAIso(f))) n += 1
  }
  return f
}

/** Borrador de la observación con lo confirmado (el analista lo edita). */
export function borradorObservacion(v) {
  const quien = tiene(v.v_contacto_cargo) ? v.v_contacto_cargo.trim() : v.v_validado_por === SABANA ? 'La sábana pensional' : v.v_validado_por === JEFE ? 'El jefe inmediato' : 'La empresa'
  const verbo = v.v_validado_por === SABANA ? 'registra' : 'confirma'
  const sigue = iguales(v.v_fecha_retiro, ACTUAL)
  const cargo = tiene(v.v_cargo) ? ` en el cargo de ${v.v_cargo.trim()}` : ''
  const periodo = sigue ? `vigente desde el ${v.v_fecha_inicio}` : `desde el ${v.v_fecha_inicio} hasta el ${v.v_fecha_retiro}`
  return `${quien} ${verbo} la vinculación ${periodo}${cargo}.`
}

// ── Inactividad (LB-04) ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const CORROBORACIONES = ['Referencia confirmó', 'Aportes como independiente', 'Solo declaración del evaluado', 'No se pudo corroborar']
export const UMBRAL_INACTIVIDAD = 30
const mesAFechas = (desde, hasta) => {
  const d = /^(\d{2})\/(\d{4})$/.exec(String(desde ?? '').trim())
  const h = /^(\d{2})\/(\d{4})$/.exec(String(hasta ?? '').trim())
  return d && h ? { ini: new Date(+d[2], +d[1] - 1, 1), fin: new Date(+h[2], +h[1], 0) } : null
}

/**
 * Las vinculaciones como intervalos de fechas, con las fechas CONFIRMADAS. Si una fecha confirmada falta o no es válida se usa la declarada y el intervalo queda marcado
 * `declarada` (los periodos calculados con él llevan el aviso «Calculado con fecha declarada»). `v` = valores por clave de cada vinculación.
 */
export function intervalosDeVinculaciones(vinculaciones, hoy = new Date()) {
  return vinculaciones.map((v, n) => {
    const iniConf = textoAFecha(v.v_fecha_inicio)
    const iniDecl = isoAFecha(v.v_decl_inicio)
    const sigue = iguales(v.v_fecha_retiro, ACTUAL)
    const finConf = sigue ? hoy : textoAFecha(v.v_fecha_retiro)
    const finDecl = tiene(v.v_decl_fin) ? isoAFecha(v.v_decl_fin) : (tiene(v.v_decl_inicio) && !finConf ? hoy : null)
    const inicio = iniConf ?? iniDecl
    const fin = finConf ?? finDecl ?? (inicio ? hoy : null)
    return { n, id: n, empresa: v.v_empresa, inicio, fin, declarada: (!iniConf && !!iniDecl) || (!finConf && !!finDecl) }
  }).filter(x => x.inicio && x.fin)
}

/** Periodos de más de 30 días sin vinculación dentro del periodo validado (hasta hoy a lo sumo). [{ desde, hasta, dias, declarada }] */
export function huecosDeInactividad(desdeMes, hastaMes, intervalos, hoy = new Date()) {
  const p = mesAFechas(desdeMes, hastaMes)
  if (!p) return []
  const fin = p.fin < hoy ? p.fin : hoy
  const dentro = intervalos.filter(x => x.inicio <= fin && x.fin >= p.ini).sort((a, b) => a.inicio - b.inicio)
  const huecos = []
  let cursor = p.ini
  let previo = null
  dentro.forEach(x => {
    if (diasEntre(cursor, x.inicio) > UMBRAL_INACTIVIDAD) huecos.push({ desde: cursor, hasta: x.inicio, dias: diasEntre(cursor, x.inicio), declarada: !!(previo?.declarada || x.declarada) })
    if (x.fin > cursor) { cursor = x.fin; previo = x }
  })
  if (diasEntre(cursor, fin) > UMBRAL_INACTIVIDAD) huecos.push({ desde: cursor, hasta: fin, dias: diasEntre(cursor, fin), declarada: !!previo?.declarada })
  return huecos
}

export const AVISO_DECLARADA = 'Calculado con fecha declarada'
const AVISO_CAMBIO = 'El periodo cambió con las fechas confirmadas: revisa cómo se corroboró.'
const AVISO_YA_NO = 'Ya no hay inactividad en este periodo con las vinculaciones actuales: revisa si sigue vigente.'
const dentroDe = (a, b) => (a.desde <= b.hasta && a.hasta >= b.desde)

/** Actividad que el evaluado explicó en su hoja de vida para un periodo que se cruza con el hueco, o ''. */
export function actividadDeclarada(hueco, declaradas) {
  for (const d of declaradas ?? []) {
    const i = textoAFecha(d.fecha_inicio)
    const f = textoAFecha(d.fecha_fin)
    if (i && f && dentroDe(hueco, { desde: i, hasta: f })) {
      const t = [d.actividad, d.justificacion].filter(tiene).join(' · ')
      if (tiene(t)) return t + (tiene(d.quien_confirma) ? ` (confirma: ${d.quien_confirma})` : '')
    }
  }
  return ''
}

/**
 * Pone al día las filas de inactividad con los huecos calculados. `filas`: [{ localId, idFila, v }] con `v` = valores por clave. Devuelve { filas, cambio }.
 * - Lo manual no se toca. Una fila calculada que ya tiene el mismo periodo se conserva con su corroboración.
 * - Si el periodo cambia (se corrigió una fecha), la fila se actualiza en su sitio y, si ya tenía corroboración, lleva un aviso.
 * - Si ya no hay inactividad: una fila sin guardar se quita; una guardada sin corroboración queda vacía («Descartado»; un analista no puede borrar filas guardadas);
 *   una guardada con corroboración se conserva con un aviso.
 */
export function sincronizarInactividad(filas, huecos, declaradas, nuevoId) {
  const calculadas = filas.filter(f => f.v.in_origen === 'Calculado')
  const libres = new Set(calculadas.map(f => f.localId))
  const asignado = new Map()   // índice de hueco → fila
  const mismoPeriodo = (f, h) => f.v.in_desde === fechaAIso(h.desde) && f.v.in_hasta === fechaAIso(h.hasta)
  huecos.forEach((h, k) => { const f = calculadas.find(x => libres.has(x.localId) && mismoPeriodo(x, h)); if (f) { asignado.set(k, { f, cambio: false }); libres.delete(f.localId) } })
  huecos.forEach((h, k) => {
    if (asignado.has(k)) return
    const f = calculadas.find(x => libres.has(x.localId) && x.v.in_desde && x.v.in_hasta && dentroDe(h, { desde: isoAFecha(x.v.in_desde), hasta: isoAFecha(x.v.in_hasta) }))
    if (f) { asignado.set(k, { f, cambio: true }); libres.delete(f.localId) }
  })
  huecos.forEach((h, k) => {
    if (asignado.has(k)) return
    const f = calculadas.find(x => libres.has(x.localId) && x.idFila != null && !tiene(x.v.in_corroboracion) && !tiene(x.v.in_actividad))   // fila guardada ya vacía de trabajo: se reutiliza
    if (f) { asignado.set(k, { f, cambio: true }); libres.delete(f.localId) }
  })

  const resultado = []
  let hubo = false
  filas.forEach(f => {
    if (f.v.in_origen !== 'Calculado') { resultado.push(f); return }
    if (libres.has(f.localId)) {   // sin hueco
      if (f.idFila == null) { hubo = true; return }
      if (!tiene(f.v.in_corroboracion)) { hubo = true; resultado.push({ ...f, v: { ...f.v, in_origen: 'Descartado', in_desde: '', in_hasta: '', in_dias: '', in_actividad: '', in_aviso: '' } }); return }
      if (f.v.in_aviso !== AVISO_YA_NO) { hubo = true; resultado.push({ ...f, v: { ...f.v, in_aviso: AVISO_YA_NO } }); return }
    }
    const idx = [...asignado.entries()].find(([, a]) => a.f.localId === f.localId)
    if (!idx) { resultado.push(f); return }
    const [k, { cambio }] = idx
    const h = huecos[k]
    const aviso = cambio && tiene(f.v.in_corroboracion) ? AVISO_CAMBIO : (cambio ? '' : (f.v.in_aviso === AVISO_CAMBIO ? AVISO_CAMBIO : ''))
    const nuevo = { ...f.v, in_origen: 'Calculado', in_desde: fechaAIso(h.desde), in_hasta: fechaAIso(h.hasta), in_dias: String(h.dias), in_aviso: aviso || (h.declarada ? AVISO_DECLARADA : '') }
    if (!tiene(nuevo.in_actividad)) nuevo.in_actividad = actividadDeclarada(h, declaradas)
    if (JSON.stringify(nuevo) !== JSON.stringify(f.v)) hubo = true
    resultado.push({ ...f, v: nuevo })
  })
  huecos.forEach((h, k) => {
    if (asignado.has(k)) return
    hubo = true
    resultado.push({ localId: nuevoId(), idFila: null, v: { in_origen: 'Calculado', in_desde: fechaAIso(h.desde), in_hasta: fechaAIso(h.hasta), in_dias: String(h.dias), in_actividad: actividadDeclarada(h, declaradas), in_corroboracion: '', in_aviso: h.declarada ? AVISO_DECLARADA : '' } })
  })
  return { filas: resultado, cambio: hubo }
}

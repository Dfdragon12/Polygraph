import {
  ACTUAL, SABANA, PENDIENTE, JEFE, LLAMADAS_OBLIGATORIAS, iguales, soloDigitos, aMoneda, norm, isoATexto, fechaATexto, diasEntre, compararFecha, compararRetiro,
  claseDeLlamados, detalleDeLlamados, tieneCodigo, dentroDelPeriodo, llamadasQueFaltan, intervalosDeVinculaciones,
} from './reglasLaboral.js'
import { borradorConcepto } from './conceptoBorrador.js'

// LB-05 · Revisión y envío de la verificación laboral: lo que falta para enviar, los hallazgos sin impacto que el sistema SUGIERE y el borrador del concepto.
// Todo es sugerencia: el analista confirma, edita o descarta; nada decide solo.

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const quita = (t) => String(t ?? '').trim().replace(/[.\s]+$/, '')
const nombre = (v, n) => `Vinculación N.º ${n}${tiene(v.v_empresa) ? ` (${v.v_empresa.trim()})` : ''}`

/** ¿La vinculación nació de una experiencia que hoy queda fuera del periodo validado? Esas no se exigen. */
export function estaFueraDelPeriodo(v, desde, hasta) {
  if (!tiene(v.v_id_exp) || v.v_no_declarada === 'Sí') return false
  const exp = { fecha_inicio: isoATexto(v.v_decl_inicio), fecha_fin: isoATexto(v.v_decl_fin) }
  return !dentroDelPeriodo(exp, desde, hasta)
}

/**
 * Lo obligatorio que falta en UNA vinculación. `n` = número (desde 1), `llamadas` = llamadas registradas. Cada ítem lleva la clave de la celda exacta (`vinc-N:campo`).
 * Una vinculación «Pendiente» es un faltante a propósito: con ella se puede generar el borrador, no el informe final.
 */
export function faltantesDeVinculacion(v, n, llamadas) {
  const items = []
  const poner = (texto, campo) => items.push({ texto: `${nombre(v, n)}: ${texto}`, clave: `vinc-${n}:${campo}` })
  const sabana = v.v_validado_por === SABANA
  const pendiente = v.v_validado_por === PENDIENTE
  const vigente = iguales(v.v_fecha_retiro, ACTUAL) || v.v_motivo_clase === 'Vigente (aún vinculado)'

  if (!tiene(v.v_empresa)) poner('escribe la empresa', 'v_empresa')
  if (!tiene(v.v_fecha_inicio)) poner('confirma la fecha de inicio', 'v_fecha_inicio')
  if (!tiene(v.v_fecha_retiro)) poner('confirma la fecha de retiro (o marca «Sigue vinculado»)', 'v_fecha_retiro')
  if (!tiene(v.v_validado_por)) poner('elige quién la validó', 'v_validado_por')
  if (pendiente) poner('sigue «Pendiente»: no se puede enviar el informe final, solo generar el borrador', 'v_validado_por')
  if (v.v_no_declarada === 'Sí' && !tiene(v.v_mas_30)) poner('indica si dura más de 30 días', 'v_mas_30')
  if (tiene(v.v_validado_por) && !pendiente && !sabana) {
    if (!tiene(v.v_llamados)) poner('elige los llamados de atención', 'v_llamados')
    else if (claseDeLlamados(v.v_llamados) !== 'Ninguno' && !tiene(detalleDeLlamados(v.v_llamados))) poner('escribe el detalle de los llamados de atención', 'v_llamados_detalle')
    if (!vigente && !tiene(v.v_motivo_clase)) poner('clasifica el motivo del retiro', 'v_motivo')
  }
  if (tieneCodigo(v, 'LAB-01') && !tiene(v.v_lab01_base)) poner('LAB-01 necesita el sustento', 'v_lab01_base')
  if (tieneCodigo(v, 'LAB-01') && v.v_lab01_base === 'Alteración evidente en inspección visual' && !tiene(v.v_vobo)) poner('LAB-01 necesita el visto bueno del segundo analista', 'v_vobo')
  if (tieneCodigo(v, 'LAB-11') && !tiene(v.v_fecha_sol_eval)) poner('LAB-11 necesita la fecha de solicitud del certificado', 'v_fecha_sol_eval')
  const faltan = llamadasQueFaltan(v, llamadas)
  if (faltan > 0 && !pendiente) poner(`faltan ${faltan} llamadas obligatorias`, 'lb-gestiones')
  else if (faltan > 0) poner(`faltan ${faltan} ${faltan === 1 ? 'llamada obligatoria' : 'llamadas obligatorias'} (${llamadas} de ${LLAMADAS_OBLIGATORIAS})`, 'lb-gestiones')
  if (!tiene(v.v_soporte) || v.v_soporte === '[]') poner('falta el soporte', 'v_soporte')
  return items
}

// ── Hallazgos sin impacto sugeridos ────────────────────────────────────────────────────────────────────────────────────────────────────────────
/**
 * Filas que el sistema SUGIERE para «4. Hallazgos sin impacto». `vinculaciones` = valores por clave; `inactividades` = [{ desde, hasta, dias, corroboracion }] (ISO).
 * Cada una: { situacion, detalle, marca } — `marca` es el texto que, si ya está en el detalle de una fila registrada, indica que se registró.
 */
export function sugerenciasInformativas(vinculaciones, inactividades, hoy = new Date()) {
  const s = []
  const agregar = (situacion, detalle, marca) => s.push({ situacion, detalle, marca })
  vinculaciones.forEach((v, i) => {
    const n = i + 1
    const marca = `N.º ${n}`
    const quien = `Vinculación ${marca}${tiene(v.v_empresa) ? ` (${v.v_empresa.trim()})` : ''}`
    if (!tiene(v.v_empresa) && !tiene(v.v_validado_por)) return
    if (tiene(v.v_salario) && tiene(v.v_decl_salario) && soloDigitos(v.v_salario) !== soloDigitos(v.v_decl_salario)) {
      agregar('Salario distinto al declarado por el evaluado', `${quien}: declaró ${aMoneda(soloDigitos(v.v_decl_salario))} y la empresa informa ${aMoneda(soloDigitos(v.v_salario))}.`, marca)
    }
    if (v.v_validado_por === SABANA) agregar('Vinculación validada solo con sábana pensional o bases de datos', `${quien}: no fue posible validar motivo de retiro ni llamados de atención.`, marca)
    if (/cancelad|liquidaci/.test(norm(v.v_estado_empresa))) agregar(`Empresa ${norm(v.v_estado_empresa).includes('liquidaci') ? 'en liquidación' : 'cancelada'}`, `${quien}: el estado actual de la empresa es «${v.v_estado_empresa}».`, marca)
    if (/politica/.test(norm(`${v.v_observaciones} ${v.v_seguimiento} ${v.v_motivo}`))) agregar('La empresa no entrega información por política interna', `${quien}: la empresa indicó que su política no le permite dar esa información.`, marca)
    const ini = compararFecha(v.v_fecha_inicio, v.v_decl_inicio)
    const ret = compararRetiro(v.v_fecha_retiro, v.v_decl_fin)
    const menores = [['inicio', ini], ['retiro', ret]].filter(([, r]) => r && r.dias > 0 && r.nivel === 'informativo')
    if (menores.length > 0) agregar('Diferencia de fechas de hasta 2 meses frente a lo declarado', `${quien}: ${menores.map(([k, r]) => `${k} con ${r.dias} días de diferencia`).join('; ')}.`, marca)
    if (tiene(v.v_cargo) && tiene(v.v_decl_cargo) && !iguales(v.v_cargo, v.v_decl_cargo) && !tieneCodigo(v, 'LAB-03')) {
      agregar('Cargo con otro nombre', `${quien}: el evaluado declaró «${v.v_decl_cargo}» y la empresa informa «${v.v_cargo}».`, marca)
    }
    if (tieneCodigo(v, 'ALR-LAB-01')) agregar('No fue posible verificar la vinculación por una causa ajena (ALR-LAB-01)', `${quien}: se agotaron las gestiones sin respuesta de la empresa.`, marca)
  })
  // Vinculaciones simultáneas (más de 30 días de cruce).
  const inter = intervalosDeVinculaciones(vinculaciones, hoy)
  for (let a = 0; a < inter.length; a += 1) {
    for (let b = a + 1; b < inter.length; b += 1) {
      const ini = new Date(Math.max(+inter[a].inicio, +inter[b].inicio))
      const fin = new Date(Math.min(+inter[a].fin, +inter[b].fin))
      if (diasEntre(ini, fin) > 30) agregar('Vinculaciones simultáneas', `Vinculaciones N.º ${inter[a].n + 1} y N.º ${inter[b].n + 1}: coinciden entre el ${fechaATexto(ini)} y el ${fechaATexto(fin)}.`, `N.º ${inter[a].n + 1} y N.º ${inter[b].n + 1}`)
    }
  }
  inactividades.filter(p => ['Referencia confirmó', 'Aportes como independiente'].includes(p.corroboracion)).forEach(p => {
    const marca = `${isoATexto(p.desde)} a ${isoATexto(p.hasta)}`
    agregar('Periodo de inactividad corroborado', `Periodo del ${marca} (${p.dias} días): ${p.corroboracion.toLowerCase()}.`, marca)
  })
  return s
}

// ── Borrador del concepto ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
/**
 * Borrador del concepto del analista con lo ya registrado. Tono de las reglas: «la empresa informa…», nunca un juicio sobre el evaluado. El analista lo edita.
 * hallazgos = [{ ref, descripcion, fuente, detalle, puntos }]; informativos = [{ situacion }]; inactividades = [{ desde, hasta, dias, corroboracion }].
 */
export function borradorConceptoLaboral({ periodo, vinculaciones, hallazgos, informativos, inactividades }) {
  const partes = []
  const validas = vinculaciones.map((v, i) => ({ v, n: i + 1 })).filter(x => tiene(x.v.v_empresa) && !estaFueraDelPeriodo(x.v, periodo.desde, periodo.hasta))
  if (validas.length > 0) {
    partes.push(`Se verificaron ${validas.length} ${validas.length === 1 ? 'vinculación laboral' : 'vinculaciones laborales'} en el periodo ${periodo.desde} a ${periodo.hasta}.`)
    validas.forEach(({ v, n }) => {
      const sigue = iguales(v.v_fecha_retiro, ACTUAL)
      const cuando = sigue ? `vigente desde el ${v.v_fecha_inicio}` : `desde el ${v.v_fecha_inicio} hasta el ${v.v_fecha_retiro}`
      const cargo = tiene(v.v_cargo) ? ` como ${v.v_cargo.trim()}` : ''
      if (v.v_validado_por === PENDIENTE) { partes.push(`N.º ${n}, ${v.v_empresa.trim()}: no fue posible obtener respuesta de la empresa; la vinculación queda pendiente de verificar.`); return }
      const fuente = v.v_validado_por === SABANA ? 'la sábana pensional registra' : v.v_validado_por === JEFE ? 'el jefe inmediato informa' : 'la empresa informa'
      let t = `N.º ${n}, ${v.v_empresa.trim()}: ${fuente} la vinculación ${cuando}${cargo}`
      if (v.v_validado_por !== SABANA) {
        if (!sigue && tiene(v.v_motivo)) t += `; motivo de retiro: ${quita(v.v_motivo).toLowerCase()}`
        const llam = claseDeLlamados(v.v_llamados)
        if (llam && llam !== 'Ninguno') t += `; informa llamados de atención de ${llam.toLowerCase()}`
        else if (llam === 'Ninguno') t += '; no informa llamados de atención'
      }
      partes.push(`${t}.`)
    })
  }
  partes.push(borradorConcepto(hallazgos, informativos))
  if (inactividades.length > 0) {
    partes.push(`Se identificaron ${inactividades.length} ${inactividades.length === 1 ? 'periodo' : 'periodos'} de inactividad superiores a 30 días: ${inactividades.map(p => `${isoATexto(p.desde)} a ${isoATexto(p.hasta)} (${(p.corroboracion || 'sin corroborar').toLowerCase()})`).join('; ')}.`)
  } else if (validas.length > 0) {
    partes.push('No se identificaron periodos de inactividad superiores a 30 días en el periodo validado.')
  }
  const pendiente = validas.some(x => x.v.v_validado_por === PENDIENTE)
  const noCorroborada = inactividades.some(p => !['Referencia confirmó', 'Aportes como independiente'].includes(p.corroboracion))
  if (hallazgos.length > 0 || pendiente || noCorroborada) partes.push('Se sugiere indagar los aspectos laborales señalados durante la prueba de poligrafía.')
  return partes.join(' ')
}

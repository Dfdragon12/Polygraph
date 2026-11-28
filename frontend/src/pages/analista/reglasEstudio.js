import { norm, instantanea, leerInstantanea } from './reglasAcademica.js'
import { textoAIso, isoATexto, isoAFecha, diasEntre, DIAS_DE_FONDO } from './reglasLaboral.js'

// AC-03 · Reglas puras de «5. Estudio verificado»: lo declarado, la marca de cada celda y las preguntas que llevan al código. Todo es sugerencia: el analista confirma.

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

export const CAT_BASICA = 'Educación básica y media'
export const CAT_SUPERIOR = 'Educación superior'
export const CAT_ETDH = 'Educación para el trabajo y el desarrollo humano (SENA, ETDH)'
export const CAT_EXTERIOR = 'Estudios en el exterior'
export const CATEGORIAS = [CAT_BASICA, CAT_ETDH, CAT_SUPERIOR, CAT_EXTERIOR]
export const SIN_CODIGO = '—'
export const DOCUMENTOS_BOTON = ['Diploma', 'Acta de grado']
const delExterior = (e) => /^s[ií]$/i.test(String(e?.en_exterior ?? ''))

/** Categoría que se deduce del nivel: bachiller → básica y media; técnico laboral → ETDH; el resto → superior; en el exterior → exterior. */
export function categoriaDeNivel(estudio) {
  if (!estudio) return ''
  if (delExterior(estudio)) return CAT_EXTERIOR
  const n = norm(estudio.nivel)
  if (!n) return ''
  if (n === 'bachiller' || n === 'bachillerato' || n.startsWith('basica') || n.startsWith('media')) return CAT_BASICA
  if (n === 'tecnico laboral') return CAT_ETDH
  return CAT_SUPERIOR
}

// Lo que dijo el evaluado, llevado a una opción de la lista de la plantilla (o '' si no hay una igual: entonces elige el analista).
const SINONIMOS_NIVEL = { universitario: 'Profesional', pregrado: 'Profesional', tecnologia: 'Tecnólogo' }
export function nivelDeLista(nivel, opciones = []) {
  const n = norm(nivel)
  if (!n) return ''
  const directa = opciones.find(o => norm(o) === n)
  if (directa) return directa
  const sin = SINONIMOS_NIVEL[n]
  return sin && opciones.includes(sin) ? sin : ''
}
export function estadoDeLista(estado, opciones = []) {
  const n = norm(estado)
  if (!n) return ''
  return opciones.find(o => norm(o) === n) ?? opciones.find(o => norm(o).split(' ')[0] === n.split(' ')[0]) ?? ''
}
export const ciudadDeclarada = (e) => [e?.ciudad, delExterior(e) && tiene(e?.pais_exterior) ? `(${e.pais_exterior})` : ''].filter(tiene).join(' ')

/** Lo declarado con la forma de las celdas de «5. Estudio verificado» (fechas en AAAA-MM-DD). `copia` = la instantánea del estudio. */
export function valoresDeclarados(copia, opciones = {}) {
  if (!copia) return null
  return {
    aca_institucion: copia.institucion ?? '',
    aca_ciudad: ciudadDeclarada(copia),
    aca_categoria: categoriaDeNivel(copia),
    aca_nivel: nivelDeLista(copia.nivel, opciones.aca_nivel) || (copia.nivel ?? ''),
    aca_fecha_inicio: textoAIso(copia.fecha_inicio),
    aca_fecha_fin: textoAIso(copia.fecha_fin),
    aca_titulo: copia.titulo ?? '',
    aca_estado: estadoDeLista(copia.estado, opciones.aca_estado) || (copia.estado ?? ''),
  }
}

export const CLAVES_PRECARGA = ['aca_institucion', 'aca_ciudad', 'aca_categoria', 'aca_nivel', 'aca_fecha_inicio', 'aca_fecha_fin', 'aca_titulo', 'aca_estado']
export const CLAVES_FECHA = ['aca_fecha_inicio', 'aca_fecha_fin']
// Lo que solo diligencia el analista: se borra si pide «Reemplazar lo verificado».
export const CLAVES_VERIFICACION = ['aca_documento', 'aca_medio', 'aca_contacto', 'aca_contacto_cargo', 'aca_contacto_tel', 'aca_fecha_respuesta', 'aca_observaciones', 'aca_preguntas', 'aca_ac01_base']

/** Lo que se PRECARGA al seleccionar un estudio: solo lo que cabe en una opción de la lista (nivel y estado), el resto tal cual. */
export function precarga(estudio, opciones = {}) {
  const d = valoresDeclarados(leerInstantanea(instantanea(estudio)), opciones)
  return { ...d, aca_nivel: nivelDeLista(estudio.nivel, opciones.aca_nivel), aca_estado: estadoDeLista(estudio.estado, opciones.aca_estado) }
}

/**
 * ¿Hay algo confirmado que se perdería al precargar otro estudio? Cuenta lo que solo diligencia el analista (documento, medio, contacto, observaciones,
 * preguntas, calificación) y lo que cambió respecto a lo que se precargó del estudio anterior.
 */
export function hayVerificado(v, copiaPrevia, opciones = {}) {
  if (CLAVES_VERIFICACION.some(k => tiene(v[k]))) return true
  if (tiene(v.aca_ref) && v.aca_ref !== SIN_CODIGO) return true
  const previo = copiaPrevia ? precargaDeCopia(copiaPrevia, opciones) : {}
  return CLAVES_PRECARGA.some(k => tiene(v[k]) && norm(v[k]) !== norm(previo[k]))
}
const precargaDeCopia = (copia, opciones) => ({ ...valoresDeclarados(copia, opciones), aca_nivel: nivelDeLista(copia.nivel, opciones.aca_nivel), aca_estado: estadoDeLista(copia.estado, opciones.aca_estado) })

/** Marca de cada celda frente a lo declarado: coincide | revisar | informativo | fondo (las fechas traen los días de diferencia). Sin marca si falta uno de los dos. */
export function marcasDelEstudio(v, copia, opciones = {}) {
  const d = valoresDeclarados(copia, opciones)
  const m = {}
  if (!d) return m
  ;['aca_institucion', 'aca_ciudad', 'aca_categoria', 'aca_nivel', 'aca_titulo', 'aca_estado'].forEach(k => {
    if (tiene(v[k]) && tiene(d[k])) m[k] = { nivel: norm(v[k]) === norm(d[k]) ? 'coincide' : 'revisar' }
  })
  CLAVES_FECHA.forEach(k => {
    const c = isoAFecha(v[k]); const x = isoAFecha(d[k])
    if (!c || !x) return
    const dias = Math.abs(diasEntre(c, x))
    // Criterio (el diccionario no lo define): más de 2 meses, o un año de grado distinto, es «de fondo»; menos, «informativo».
    const fondo = dias > DIAS_DE_FONDO || (k === 'aca_fecha_fin' && c.getFullYear() !== x.getFullYear())
    m[k] = { dias, nivel: dias === 0 ? 'coincide' : fondo ? 'fondo' : 'informativo' }
  })
  return m
}

// ── Preguntas para el código: el código es el de la primera que aplica ──
export const PREGUNTAS = [
  { id: 'p1', texto: '¿El documento es falso o adulterado?', opciones: ['No', 'Sí', 'Sospecha sin confirmar'] },
  { id: 'p2', texto: '¿La institución no confirma el título?', opciones: ['No', 'Sí'] },
  { id: 'p3', texto: '¿Datos de fondo distintos? (otro nivel, otro programa, fechas que cambian lo declarado)', opciones: ['No', 'Diferencia menor explicada', 'Sí'] },
  { id: 'p4', texto: '¿Institución no reconocida o título sin validez en Colombia?', opciones: ['No', 'Sí'] },
  { id: 'p5', texto: '¿No se pudo verificar?', opciones: ['Sí se verificó', 'Evaluado no aportó', 'Institución no responde'] },
]
const CODIGO_DE = {
  p1: { 'Sí': ['ACA-01', 'el documento es falso o adulterado'], 'Sospecha sin confirmar': ['ALR-ACA-01', 'sospecha de falsedad sin confirmar'] },
  p2: { 'Sí': ['ACA-02', 'la institución no confirma el título'] },
  p3: { 'Sí': ['ACA-03', 'datos de fondo distintos a los declarados'] },
  p4: { 'Sí': ['ACA-05', 'institución no reconocida o título sin validez en Colombia'] },
  p5: { 'Evaluado no aportó': ['ACA-04', 'el evaluado no aportó el documento'], 'Institución no responde': ['ALR-ACA-02', 'la institución no responde'] },
}
export const leerPreguntas = (texto) => { try { const o = JSON.parse(texto); return o && typeof o === 'object' && !Array.isArray(o) ? o : {} } catch { return {} } }
export const escribirPreguntas = (resp) => JSON.stringify(resp)
export const preguntasSinResponder = (resp) => PREGUNTAS.filter(q => !tiene(resp?.[q.id]))

/** { codigo, razon } de la primera pregunta que aplica; '—' si ninguna. */
export function codigoDePreguntas(resp) {
  for (const q of PREGUNTAS) {
    const r = CODIGO_DE[q.id]?.[resp?.[q.id]]
    if (r) return { codigo: r[0], razon: r[1] }
  }
  return { codigo: SIN_CODIGO, razon: '' }
}

const ROTULO = { aca_institucion: 'la institución', aca_ciudad: 'la ciudad', aca_categoria: 'la categoría', aca_nivel: 'el nivel', aca_titulo: 'el título', aca_estado: 'el estado', aca_fecha_inicio: 'la fecha de inicio', aca_fecha_fin: 'la fecha de finalización' }

/** Qué sugieren las marcas para la pregunta 3: 'Sí' (institución, nivel, título, estado o una fecha de fondo difieren), 'Diferencia menor explicada' o null. */
export function sugerenciaDeFondo(marcas) {
  const fondo = [...['aca_institucion', 'aca_nivel', 'aca_titulo', 'aca_estado'].filter(k => marcas[k]?.nivel === 'revisar'), ...CLAVES_FECHA.filter(k => marcas[k]?.nivel === 'fondo')]
  if (fondo.length > 0) return { valor: 'Sí', porque: `${fondo.map(k => ROTULO[k]).join(', ')} no coincide${fondo.length > 1 ? 'n' : ''} con lo declarado` }
  const menor = Object.entries(marcas).filter(([, x]) => x.nivel === 'revisar' || x.nivel === 'informativo').map(([k]) => ROTULO[k])
  return menor.length > 0 ? { valor: 'Diferencia menor explicada', porque: `diferencia menor en ${menor.join(', ')}` } : null
}

/** Aviso 8: un estudio sin terminar no debería traer diploma ni acta de grado. */
export function avisoDocumento(estado, documento) {
  if (!tiene(estado) || !tiene(documento)) return ''
  if (!['diploma', 'acta de grado'].includes(norm(documento))) return ''
  return norm(estado) === 'graduado' ? '' : `El estado del estudio es «${estado}», pero se aportó ${norm(documento) === 'diploma' ? 'un diploma' : 'un acta de grado'}. Revisa que el documento y el estado coincidan.`
}

/** Borrador de Observaciones con lo confirmado; el analista lo edita. */
export function borradorObservaciones(v) {
  const n = norm(v.aca_estado)
  const verbo = n === 'graduado' ? 'es graduado de' : n.startsWith('termino materias') ? 'terminó las materias de' : n === 'en curso' ? 'cursa' : n ? `figura con estado «${v.aca_estado}» en` : 'registra'
  const donde = tiene(v.aca_institucion) ? ` en ${v.aca_institucion}` : ''
  const cuando = tiene(v.aca_fecha_fin) ? ` el ${isoATexto(v.aca_fecha_fin)}` : ''
  const medio = tiene(v.aca_medio) ? ` Verificado mediante: ${v.aca_medio}.` : ''
  return `La institución confirma que el evaluado ${verbo} ${tiene(v.aca_titulo) ? v.aca_titulo : 'el programa declarado'}${donde}${cuando}.${medio}`
}

// ── AC-04 · Revisión: hallazgos sin impacto sugeridos y borrador del concepto ──
const SITUACION_ALERTA = {
  'ALR-ACA-01': 'Alerta: sospecha sin confirmar sobre la autenticidad del documento (ALR-ACA-01)',
  'ALR-ACA-02': 'Alerta: la institución no respondió a la verificación (ALR-ACA-02)',
}
const DETALLE_ALERTA = {
  'ALR-ACA-01': 'Hay indicios que generan duda sobre el documento aportado, pero no se pudo confirmar con la institución.',
  'ALR-ACA-02': 'Se hicieron las gestiones registradas y la institución no respondió dentro del plazo.',
}

/** Filas que el sistema SUGIERE para «4. Hallazgos sin impacto»: diferencia menor explicada y las alertas ALR. El analista las registra, edita o descarta. */
export function sugerenciasInformativasAcademica(v, marcas) {
  const lista = []
  if (leerPreguntas(v.aca_preguntas).p3 === 'Diferencia menor explicada') {
    const f = filaInformativa(v, marcas)
    lista.push({ situacion: f.i_situacion, detalle: f.i_detalle })
  }
  if (SITUACION_ALERTA[v.aca_ref]) lista.push({ situacion: SITUACION_ALERTA[v.aca_ref], detalle: DETALLE_ALERTA[v.aca_ref] })
  return lista
}

// Cómo se dice cada resultado en el concepto: lo que la institución registra o no registra, nunca «el evaluado mintió».
const FRASE_DE_CODIGO = {
  'ACA-01': 'El documento aportado no coincide con lo que registra la institución y presenta indicios de adulteración.',
  'ACA-02': 'La institución no registra el título declarado.',
  'ACA-03': 'La información que confirma la institución difiere de lo declarado en aspectos de fondo.',
  'ACA-04': 'El evaluado no aportó el documento solicitado dentro del plazo, por lo que el estudio no pudo verificarse.',
  'ACA-05': 'La institución no está reconocida o el título no tiene validez en Colombia.',
  'ALR-ACA-01': 'Existe una sospecha sin confirmar sobre la autenticidad del documento aportado.',
  'ALR-ACA-02': 'La institución no respondió a las solicitudes de verificación.',
}

/** Borrador del concepto con lo confirmado. `impacto` = el texto de los hallazgos con puntos (lo arma el generador común). */
export function borradorConceptoAcademica({ v, hallazgos = [], informativos = [], impacto = '' }) {
  const partes = []
  const ref = tiene(v.aca_ref) ? v.aca_ref : SIN_CODIGO
  if (FRASE_DE_CODIGO[ref]) {
    partes.push(FRASE_DE_CODIGO[ref])
  } else if (tiene(v.aca_titulo) || tiene(v.aca_institucion)) {
    partes.push(borradorObservaciones(v))
    if (informativos.length === 0) partes.push('Lo confirmado coincide con lo declarado por el evaluado.')
  }
  if (impacto) partes.push(impacto)
  else if (hallazgos.length === 0 && ref === SIN_CODIGO) partes.push('No registra hallazgos que afecten el nivel de riesgo.')
  const lista = informativos.map(a => String(a.situacion ?? '').trim().replace(/[.\s]+$/, '')).filter(Boolean)
  if (lista.length > 0) partes.push(`Además, ${lista.length === 1 ? 'queda una alerta informativa' : `quedan ${lista.length} alertas informativas`} que no suma${lista.length === 1 ? '' : 'n'} puntos: ${lista.map(t => t.charAt(0).toLowerCase() + t.slice(1)).join('; ')}.`)
  if (ref !== SIN_CODIGO || lista.length > 0) partes.push('Se sugiere indagar este aspecto durante la prueba de poligrafía.')
  return partes.join(' ')
}

/** Fila sugerida para «4. Hallazgos sin impacto» cuando la pregunta 3 es «Diferencia menor explicada». */
export function filaInformativa(v, marcas) {
  const dif = Object.entries(marcas).filter(([, x]) => x.nivel === 'revisar' || x.nivel === 'informativo').map(([k]) => ROTULO[k])
  return { i_situacion: 'Diferencia menor frente a lo declarado', i_detalle: `Se encontró una diferencia menor en ${dif.join(', ') || 'los datos del estudio'}${tiene(v.aca_titulo) ? ` (${v.aca_titulo})` : ''}.`, i_tratamiento: '' }
}

// AC-06 · Estudio del exterior: la convalidación se verifica en el Ministerio de Educación y, si el plazo del evaluado venció, se sugiere el criterio «Último estudio no verificable en el plazo».
export const AYUDA_EXTERIOR = 'Verifica la convalidación en el Ministerio de Educación'
export const CRITERIO_NO_VERIFICABLE = 'Último estudio no verificable en el plazo'
export const esDelExterior = (v) => v?.aca_categoria === CAT_EXTERIOR
export const sugerirCriterioNoVerificable = ({ v, plazoVencido, criterio }) => esDelExterior(v) && !!plazoVencido && criterio !== CRITERIO_NO_VERIFICABLE

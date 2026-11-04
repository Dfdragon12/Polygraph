import { ANEXO2 } from './anexosInternos'

// AN-06 · Reglas para SUGERIR el código de un proceso judicial y su relación con el cargo. Salen de la tabla de las «Reglas para el analista» y del Anexo 2.
// Nunca deciden solas: el analista acepta o elige otro código. Las claves y los valores que se guardan son los de siempre (p_*, pp_*).

const sinTildes = (s) => String(s ?? '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')

// ── Anexo 2: delitos por tipo de cargo ──────────────────────────────────────────────────────────────────────────────────
// Cada párrafo es «Delitos contra …: nombre (art. 239), otro nombre (art. 240 – nota), …». Se parte por cada «(art. N)».
function leerDelitos(texto) {
  const sinTitulo = texto.includes(':') ? texto.slice(texto.indexOf(':') + 1) : texto
  const salida = []
  const patron = /\(art\.\s*([0-9]+[A-Za-z]*)[^)]*\)/g
  let desde = 0
  let m
  while ((m = patron.exec(sinTitulo)) !== null) {
    const nombre = sinTitulo.slice(desde, m.index).replace(/^[\s,.;]+/, '').replace(/\s+$/, '')
    if (nombre) salida.push({ nombre, art: m[1] })
    desde = m.index + m[0].length
  }
  return salida
}

export const DELITOS_POR_CARGO = Object.fromEntries(ANEXO2.map(f => [f.tipo, f.delitos.flatMap(leerDelitos)]))

// Lista única de delitos para el buscador: «Hurto (art. 239)».
export const CATALOGO_DELITOS = (() => {
  const vistos = new Map()
  Object.values(DELITOS_POR_CARGO).flat().forEach(d => { if (!vistos.has(d.art)) vistos.set(d.art, d) })
  return [...vistos.values()].map(d => ({ art: d.art, texto: `${d.nombre.charAt(0).toUpperCase()}${d.nombre.slice(1)} (art. ${d.art})` }))
    .sort((a, b) => a.texto.localeCompare(b.texto, 'es'))
})()

export const articuloDe = (delito) => /art\.\s*([0-9]+[A-Za-z]*)/i.exec(String(delito ?? ''))?.[1] ?? null

/** 'Sí' | 'No' | null: ¿el delito está en el Anexo 2 del tipo de cargo? null si falta el cargo o no se reconoce el artículo. */
export function sugerirRelacion(tipoDeCargo, delito) {
  const art = articuloDe(delito)
  const lista = DELITOS_POR_CARGO[tipoDeCargo]
  if (!art || !lista) return null
  return lista.some(d => d.art.toLowerCase() === art.toLowerCase()) ? 'Sí' : 'No'
}

// ── Código sugerido ─────────────────────────────────────────────────────────────────────────────────────────────────────
const CALIDAD_NO_SUMA = ['Demandante', 'Denunciante', 'Víctima']
const MOTIVO_NO_SUMA = ['Preclusión', 'Sentencia absolutoria', 'Fallo a favor']
const MOTIVO_SIN_CONDENA = ['Archivo', 'Prescripción', 'Desistimiento', 'Conciliación', 'Principio de oportunidad']
const VIGENTE = ['Activo', 'Pena vigente']
const esInvestigado = (c) => c === 'Investigado / indiciado' || c === 'Imputado / acusado'

/**
 * p = { tipo, calidad, estado, motivo, relacion }.  Devuelve:
 *   { codigo: 'LEG-19' | '—', razon }   → hay sugerencia
 *   { falta: 'relacion' | 'datos', razon } → todavía no se puede sugerir
 *   null                                → sin base para sugerir
 */
export function sugerirCodigo(p) {
  const { tipo, calidad, estado, motivo, relacion } = p
  if (!tipo || !calidad) return { falta: 'datos', razon: 'Elige el tipo de proceso y la calidad del evaluado.' }
  if (tipo === 'Ejecutivo') return { codigo: '—', razon: 'es un proceso ejecutivo: se califica en el análisis financiero, aquí no suma' }
  if (CALIDAD_NO_SUMA.includes(calidad)) return { codigo: '—', razon: `el evaluado es ${sinTildes(calidad) === 'victima' ? 'víctima' : calidad.toLowerCase()}, no suma` }
  if (MOTIVO_NO_SUMA.includes(motivo)) return { codigo: '—', razon: `terminó por ${motivo.toLowerCase()}: no suma` }
  if (!estado) return { falta: 'datos', razon: 'Elige el estado del proceso.' }

  if (tipo === 'Penal') {
    const vigente = VIGENTE.includes(estado)
    const condena = calidad === 'Condenado' || motivo === 'Sentencia condenatoria'
    if (vigente && (esInvestigado(calidad) || calidad === 'Condenado')) {
      if (!relacion) return { falta: 'relacion', razon: 'Confirma si el delito se relaciona con el cargo.' }
      return relacion === 'Sí' ? { codigo: 'LEG-05', razon: 'penal activo o con pena vigente, relacionado con el cargo (crítico)' }
        : { codigo: 'LEG-11', razon: 'penal activo o con pena vigente, no relacionado con el cargo' }
    }
    if (!vigente && condena) {
      if (!relacion) return { falta: 'relacion', razon: 'Confirma si el delito se relaciona con el cargo.' }
      return relacion === 'Sí' ? { codigo: 'LEG-20', razon: 'condena cumplida, relacionada con el cargo' } : { codigo: 'LEG-21', razon: 'condena cumplida, no relacionada con el cargo' }
    }
    if (!vigente && (esInvestigado(calidad) || MOTIVO_SIN_CONDENA.includes(motivo))) return { codigo: 'LEG-19', razon: 'penal terminado sin condena o inactivo' }
    return null
  }

  // No penal, en contra del evaluado
  if (calidad === 'Demandado') {
    if (VIGENTE.includes(estado)) return { codigo: 'LEG-22', razon: 'demanda no penal activa en contra' }
    return { codigo: 'LEG-23', razon: 'demanda no penal terminada en contra' }
  }
  return null
}

const digitos = (t) => String(t ?? '').replace(/\D/g, '')
/** ¿El radicado ya está en la lista (otra fuente)? Compara solo dígitos y exige al menos 10 para no confundir números cortos. */
export function radicadoRepetido(radicado, otros) {
  const d = digitos(radicado)
  return d.length >= 10 && otros.some(o => digitos(o) === d)
}

// AN-05 · Comparación de lo que reporta Reconocer con lo que declaró el evaluado. Se normaliza antes de comparar (sin tildes, mayúsculas, puntuación ni espacios de más;
// en direcciones «Cra» = «Carrera» y «#» = «No.»). El resultado solo SUGIERE «Sí / No»: el analista puede cambiarlo si la comparación falla.

const sinTildes = (s) => String(s ?? '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')

const ABREVIATURAS = {
  cra: 'carrera', cr: 'carrera', kr: 'carrera', carr: 'carrera', kra: 'carrera',
  cl: 'calle', cll: 'calle', cal: 'calle',
  av: 'avenida', avda: 'avenida', ave: 'avenida',
  dg: 'diagonal', diag: 'diagonal', tv: 'transversal', trans: 'transversal', transv: 'transversal',
  apto: 'apartamento', ap: 'apartamento', int: 'interior', bl: 'bloque', mz: 'manzana', cs: 'casa',
}
const NUMERO = new Set(['no', 'nro', 'num', 'numero', 'n'])

export function normalizarDireccion(texto) {
  const t = sinTildes(texto).replace(/[#°º]/g, ' no ').replace(/[^a-z0-9\s]/g, ' ')
  return t.split(/\s+/).filter(Boolean).filter(x => !NUMERO.has(x)).map(x => ABREVIATURAS[x] ?? x).join(' ')
}
export const normalizarTelefono = (texto) => String(texto ?? '').replace(/\D/g, '').slice(-10)
export const normalizarCorreo = (texto) => sinTildes(texto).replace(/\s+/g, '')

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

/** true / false, o null si no hay con qué comparar (falta lo declarado o lo reportado). */
export function coincide(tipo, declarado, reportado) {
  const norm = tipo === 'direccion' ? normalizarDireccion : tipo === 'telefono' ? normalizarTelefono : normalizarCorreo
  const lista = (Array.isArray(declarado) ? declarado : [declarado]).filter(tiene).map(norm).filter(Boolean)
  const r = norm(reportado)
  if (lista.length === 0 || !r) return null
  return lista.includes(r)
}
export const comoSiNo = (b) => (b === null ? '' : b ? 'Sí' : 'No')

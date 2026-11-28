// AN-04 · Los datos de SIMIT se capturan con botones y número, pero se guardan como el texto de siempre ("2 pendientes", "Sin multas"): es lo que imprime el
// Word y lo que lee el cálculo ("Multas: 2 pendientes"). Un texto escrito antes con otra forma se conserva como «otro» y no se pierde.

export const ITEMS_SIMIT = [
  { clave: 'simit_multas', etiqueta: 'Multas', sin: 'Sin multas', estados: ['pendientes', 'con acuerdo'] },
  { clave: 'simit_comparendos', etiqueta: 'Comparendos', sin: 'Sin comparendos', estados: ['pendientes', 'con acuerdo'] },
  { clave: 'simit_acuerdos', etiqueta: 'Acuerdos de pago', sin: 'Sin acuerdos de pago', estados: ['vigentes'] },
  { clave: 'simit_inmov', etiqueta: 'Inmovilizaciones', sin: 'Sin inmovilizaciones', estados: ['activas'] },
]

/** { modo: 'vacio' | 'sin' | 'con' | 'otro', n, estado, texto } */
export function leerItem(valor, item) {
  const t = String(valor ?? '').trim()
  if (!t) return { modo: 'vacio', n: '', estado: '', texto: '' }
  if (/^sin\b/i.test(t) || /^ninguna?\b/i.test(t)) return { modo: 'sin', n: '', estado: '', texto: t }
  const m = /^(\d+)\s*(.*)$/.exec(t)
  if (m && (m[2] === '' || item.estados.some(e => e === m[2].trim().toLowerCase()))) return { modo: 'con', n: m[1], estado: m[2].trim().toLowerCase(), texto: t }
  return { modo: 'otro', n: '', estado: '', texto: t }
}

export function escribirItem(item, { modo, n, estado, texto }) {
  if (modo === 'vacio') return ''
  if (modo === 'sin') return item.sin
  if (modo === 'otro') return texto ?? ''
  const num = String(n ?? '').replace(/\D/g, '')
  if (!num) return ''
  return `${num} ${estado || item.estados[0]}`
}

// LEG-17: multas de tránsito pendientes sin acuerdo de pago, solo en cargos de conducción. Solo se SUGIERE.
export function sugerirLeg17(valorMultas, conduccion) {
  if (conduccion !== 'Sí') return false
  const m = leerItem(valorMultas, ITEMS_SIMIT[0])
  return m.modo === 'con' && m.estado === 'pendientes' && Number(m.n) > 0
}

// RUNT: licencia suspendida o cancelada → LEG-10; vencida o categoría que no corresponde → LEG-14 (solo cargos de conducción). Solo se SUGIERE.
export function sugerirRunt(estado, categoriaOk, conduccion) {
  if (conduccion !== 'Sí') return null
  if (estado === 'Suspendida' || estado === 'Cancelada') return 'LEG-10'
  if (estado === 'Vencida' || categoriaOk === 'No') return 'LEG-14'
  return null
}

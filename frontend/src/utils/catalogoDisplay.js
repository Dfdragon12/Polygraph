/* El ícono/color de cada clasificación se busca por `codigo` (estable, único en la BD) — nunca
 * por `nombre`, porque el nombre lo puede renombrar el admin en cualquier momento desde
 * Catálogo → Clasificaciones y dejaría de encontrar el estilo. El título mostrado siempre es el
 * `nombre` real que llega del backend, así que nunca queda desactualizado. */
const PALETA_CATEGORIA = [
  { icono: '🎯', borde: 'border-primary-200', fondo: 'bg-primary-50', badge: 'bg-primary-100 text-primary-700' },
  { icono: '🔍', borde: 'border-blue-200',    fondo: 'bg-blue-50',    badge: 'bg-blue-100 text-blue-700' },
  { icono: '🛡️', borde: 'border-amber-200',   fondo: 'bg-amber-50',   badge: 'bg-amber-100 text-amber-700' },
  { icono: '📄', borde: 'border-green-200',   fondo: 'bg-green-50',   badge: 'bg-green-100 text-green-700' },
  { icono: '👁️', borde: 'border-purple-200',  fondo: 'bg-purple-50',  badge: 'bg-purple-100 text-purple-700' },
  { icono: '📋', borde: 'border-rose-200',    fondo: 'bg-rose-50',    badge: 'bg-rose-100 text-rose-700' },
  { icono: '🔒', borde: 'border-teal-200',    fondo: 'bg-teal-50',    badge: 'bg-teal-100 text-teal-700' },
  { icono: '⚖️', borde: 'border-orange-200',  fondo: 'bg-orange-50',  badge: 'bg-orange-100 text-orange-700' },
]

function estiloPorCodigo(codigo) {
  if (!codigo) return { borde: 'border-gray-200', fondo: 'bg-gray-50', badge: 'bg-gray-100 text-gray-700', icono: '📋' }
  let hash = 0
  for (const c of codigo) hash = (hash * 31 + c.charCodeAt(0)) % PALETA_CATEGORIA.length
  return PALETA_CATEGORIA[Math.abs(hash)]
}

export const ORDEN_CATEGORIAS = ['POLIGRAFIA', 'PRUEBAS DE CONFIABILIDAD', 'ESTUDIOS DE SEGURIDAD', 'SERVICIOS', 'VERIFEYE']

/** `nombre` es el título a mostrar (siempre el real/actual); `codigo` solo decide el color/ícono. */
export function metaCategoria(nombre, codigo) {
  return { titulo: nombre, ...estiloPorCodigo(codigo) }
}

export function ordenarCategorias(porCategoria) {
  const conocidas = ORDEN_CATEGORIAS.filter((cat) => porCategoria[cat]?.length)
  const extra = Object.keys(porCategoria).filter((cat) => !ORDEN_CATEGORIAS.includes(cat))
  return [...conocidas, ...extra]
}

/**
 * Precio por unidad según el tramo de volumen aplicable (el de mayor cantidadMinima que la
 * cantidad alcance); si ninguno aplica, usa el precio base. Solo para *previsualizar* en el
 * frontend — el backend siempre recalcula el precio real al crear la orden, nunca confía en esto.
 */
export function resolverPrecioUnitario(valorBase, tramosPrecio, cantidad) {
  if (!tramosPrecio?.length) return valorBase
  const aplicable = [...tramosPrecio]
    .sort((a, b) => b.cantidadMinima - a.cantidadMinima)
    .find((t) => cantidad >= t.cantidadMinima)
  return aplicable ? aplicable.valorUnitario : valorBase
}

export function formatearPrecio(precio) {
  if (!precio) return 'Precio a convenir'
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    minimumFractionDigits: 0,
  }).format(precio)
}

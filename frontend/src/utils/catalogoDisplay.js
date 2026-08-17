/* Claves = valor real de `clasificacion` que devuelve el backend (clasificaciones_proceso.nombre,
 * ver V118__catalogo_real_negocio.sql) — están en MAYÚSCULAS sin tilde en la base de datos. */
export const META_CATEGORIA = {
  POLIGRAFIA: {
    titulo: 'Poligrafía',
    icono: '🎯',
    borde: 'border-primary-200',
    fondo: 'bg-primary-50',
    badge: 'bg-primary-100 text-primary-700',
  },
  'PRUEBAS DE CONFIABILIDAD': {
    titulo: 'Pruebas de Confiabilidad',
    icono: '🔍',
    borde: 'border-blue-200',
    fondo: 'bg-blue-50',
    badge: 'bg-blue-100 text-blue-700',
  },
  'ESTUDIOS DE SEGURIDAD': {
    titulo: 'Estudios de Seguridad',
    icono: '🛡️',
    borde: 'border-amber-200',
    fondo: 'bg-amber-50',
    badge: 'bg-amber-100 text-amber-700',
  },
  SERVICIOS: {
    titulo: 'Validaciones y Estudios',
    icono: '📄',
    borde: 'border-green-200',
    fondo: 'bg-green-50',
    badge: 'bg-green-100 text-green-700',
  },
  VERIFEYE: {
    titulo: 'Verifeye',
    icono: '👁️',
    borde: 'border-purple-200',
    fondo: 'bg-purple-50',
    badge: 'bg-purple-100 text-purple-700',
  },
}

export const ORDEN_CATEGORIAS = ['POLIGRAFIA', 'PRUEBAS DE CONFIABILIDAD', 'ESTUDIOS DE SEGURIDAD', 'SERVICIOS', 'VERIFEYE']

export function metaCategoria(cat) {
  return META_CATEGORIA[cat] ?? {
    titulo: cat, icono: '📋',
    borde: 'border-gray-200', fondo: 'bg-gray-50', badge: 'bg-gray-100 text-gray-700',
  }
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

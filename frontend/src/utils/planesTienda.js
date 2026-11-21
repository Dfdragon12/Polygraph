/**
 * Textos y formas de los planes de la tienda (TP-06). Solo presentación: ningún precio, descuento ni ahorro se calcula aquí;
 * todo llega del backend (planes de /catalogo/procesos/activos y la cotización del carrito).
 */
import { formatearPrecio } from './catalogoDisplay'

/** «Vigencia de 3 meses para usarlas», «Vigencia de 1 año para usarlas» o, sin vencimiento, el texto del plan de 200. */
export function textoVigencia(meses) {
  if (meses == null) return 'Sin fecha de vencimiento: úsalas cuando las necesites'
  if (meses % 12 === 0) {
    const anios = meses / 12
    return `Vigencia de ${anios} ${anios === 1 ? 'año' : 'años'} para usarlas`
  }
  return `Vigencia de ${meses} ${meses === 1 ? 'mes' : 'meses'} para usarlas`
}

/** Vigencia en una línea corta para el carrito: «3 meses», «1 año», «No vence». */
export function vigenciaCorta(meses) {
  if (meses == null) return 'No vence'
  if (meses % 12 === 0) return `${meses / 12} ${meses === 12 ? 'año' : 'años'}`
  return `${meses} ${meses === 1 ? 'mes' : 'meses'}`
}

/** Menor recargo por ciudad mayor a cero del servicio, o null si no tiene recargo. */
export function recargoMinimo(servicio) {
  if (!servicio?.aplicaPrecioCiudad) return null
  const valores = (servicio.preciosCiudad ?? []).map((p) => Number(p.valor)).filter((v) => v > 0)
  return valores.length ? Math.min(...valores) : null
}

/** Línea de cobertura de la tarjeta; solo tiene sentido con coberturaPorSede encendido y un servicio con recargo. */
export function textoCobertura(servicio, cobertura = 'LOCAL') {
  const recargo = recargoMinimo(servicio)
  if (recargo == null) return null
  if (cobertura === 'NACIONAL') return 'Cualquier ciudad del país, sin recargos'
  const unidad = servicio.unidadSingular || 'unidad'
  return `Sin recargo en las ciudades sede · otras ciudades +${formatearPrecio(recargo)} por ${unidad}`
}

export const nombreCobertura = (c) => (c === 'NACIONAL' ? 'Paquete nacional' : 'Paquete por sede')

export const tituloDe = (servicio) => (servicio.tituloComercial?.trim() || servicio.nombreProceso)

/** Días hábiles de entrega en frase: «Informe en 1 día hábil». */
export function textoEntrega(dias) {
  if (dias == null) return null
  return `Informe en ${dias} día${dias !== 1 ? 's' : ''} hábil${dias !== 1 ? 'es' : ''}`
}

export const unidadDe = (servicio, n) => (n === 1 ? (servicio.unidadSingular || 'unidad') : (servicio.unidadPlural || 'unidades'))

/** Ítems del carrito como los espera el backend: por plan o, con la tienda actual, por cantidad. */
export function itemsParaApi(items) {
  return items.map((i) => (i.unidadesPlan
    ? { idProceso: i.idProceso, unidadesPlan: i.unidadesPlan, cobertura: i.cobertura ?? 'LOCAL', cantidadPaquetes: i.cantidadPaquetes, precioUnitarioEsperado: i.precioUnitario }
    : { idProceso: i.idProceso, cantidad: i.cantidad }))
}

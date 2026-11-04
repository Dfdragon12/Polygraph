// AN-02 · Sugerencias a partir del cargo de la solicitud. NUNCA deciden solas: el analista confirma o cambia. La solicitud no trae el tipo de cargo ni la marca
// de conducción, así que se deducen del texto del cargo con palabras clave.

const sinTildes = (s) => String(s ?? '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')

const TIPOS = [
  [/escolta|transporte de valores/, 'Escoltas y transporte de valores'],
  [/conductor|chofer|mensajer|domicili|repartidor|motorizado|transportador|logistic|despachador|auxiliar de bodega/, 'Logística, transporte y conducción'],
  [/vigilante|guarda de seguridad|supervisor de seguridad|portero/, 'Seguridad y vigilancia'],
  [/cajer|tesorer|pagador|auxiliar de caja/, 'Manejo de dinero, valores o inventarios'],
  [/cobrador|cobranza|cartera/, 'Cobranza y recuperación de cartera'],
  [/vendedor|comercial|asesor de ventas|servicio al cliente|ejecutivo de cuenta/, 'Comercial, ventas y atención al cliente'],
  [/compras|abastecimiento|proveedor/, 'Compras, abastecimiento y relación con proveedores'],
  [/aduana|comercio exterior|zona franca/, 'Comercio exterior, aduanas y zonas francas'],
  [/enfermer|farmac|medic|auxiliar de salud/, 'Salud, farmacéutico y medicamentos'],
  [/maquinaria|montacargas|soldador|electricista|construccion|mantenimiento|obrero|operario/, 'Construcción, mantenimiento y operación de maquinaria'],
  [/gerente|director|jefe|subgerente|coordinador general/, 'Cargos directivos o con relación con entidades públicas'],
  [/abogad|juridic|secretari|asistente administrativ|auxiliar administrativ|contador|auxiliar contable|analista/, 'Cargos documentales, jurídicos o administrativos'],
]
const CONDUCCION = /conductor|chofer|mensajer|domicili|repartidor|motorizado|transportador|piloto|escolta|operador de (vehiculo|montacargas|maquinaria)|auxiliar de ruta/

export function sugerirTipoDeCargo(cargo) {
  const c = sinTildes(cargo)
  if (!c.trim()) return null
  return TIPOS.find(([patron]) => patron.test(c))?.[1] ?? null
}

// 'Sí' si el cargo permite deducir que conduce; null si no hay base para sugerir.
export function sugerirConduccion(cargo) {
  const c = sinTildes(cargo)
  if (!c.trim()) return null
  return CONDUCCION.test(c) ? 'Sí' : null
}

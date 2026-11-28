export const ETIQUETA_NIVEL_CIUDAD = {
  PRINCIPAL: 'Principal',
  INTERMEDIA: 'Intermedia / municipio principal',
  MUNICIPIO_SECUNDARIO: 'Municipio secundario',
}

/** Recargo adicional (en pesos) que aplica a un proceso según el nivel logístico de la ciudad del evaluado. 0 si no aplica. */
export function recargoPorCiudad(proceso, ciudad) {
  if (!proceso?.aplicaPrecioCiudad || !ciudad?.nivelCiudad) return 0
  const fila = proceso.preciosCiudad?.find((p) => p.nivelCiudad === ciudad.nivelCiudad)
  return fila?.valor ?? 0
}

/**
 * Convierte texto a tipo oración: primera letra mayúscula, resto minúsculas.
 * No afecta campos de código (p.ej. códigos de clasificación que van en MAYÚSCULAS).
 */
export function aTipoOracion(valor) {
  if (!valor) return valor
  return valor.charAt(0).toUpperCase() + valor.slice(1).toLowerCase()
}

/**
 * Handler onChange para inputs de texto que aplica tipo oración en tiempo real.
 * Úsalo como: onChange={e => setForm(p => ({ ...p, campo: alCambiarTexto(e.target.value) }))}
 */
export function alCambiarTexto(valor) {
  if (!valor) return valor
  return valor.charAt(0).toUpperCase() + valor.slice(1)
}

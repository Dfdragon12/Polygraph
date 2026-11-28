// AC-08 · UNA sola lectura de los datos del evaluado (los que llegan del link / hoja de vida) para académica, laboral y antecedentes: así no vuelven a divergir.
const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

/** Valor de un dato del link por clave (CEDULA, FECHA_EXPEDICION…); '' si no viene. */
export const datoDelLink = (datosCandidato, clave) => (datosCandidato ?? []).find(c => c.clave === clave)?.valor ?? ''

/** Las filas de identidad que comparten los tres formatos: [etiqueta, valor]. `lugarExpedicionDeLaPlantilla` = lo que haya escrito la plantilla si el link no trae la ciudad. */
export function filasDeIdentidad(datosCandidato, lugarExpedicionDeLaPlantilla = '') {
  const link = (clave) => datoDelLink(datosCandidato, clave)
  const primero = (...vs) => vs.find(tiene) ?? ''
  return [
    ['Nombres y apellidos', link('NOMBRE_COMPLETO')],
    ['N.º de documento', [link('TIPO_DOCUMENTO'), link('CEDULA')].filter(tiene).join(' ')],
    ['Fecha de expedición', link('FECHA_EXPEDICION')],
    ['Lugar de expedición', primero(link('CIUDAD_EXPEDICION'), lugarExpedicionDeLaPlantilla)],
    ['Fecha de nacimiento', link('FECHA_NACIMIENTO')],
    ['Lugar de nacimiento', primero(link('LUGAR_NACIMIENTO'), link('CIUDAD_NACIMIENTO'))],
  ]
}

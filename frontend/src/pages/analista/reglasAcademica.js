// AC-02 · Reglas puras de la verificación académica: qué estudio se verifica según el criterio y si el evaluado cambió su declaración. Todo es sugerencia: el analista confirma.

export const CRITERIOS = ['Último estudio finalizado', 'Por solicitud del cliente', 'Último estudio no verificable en el plazo']
export const CRITERIO_POR_DEFECTO = CRITERIOS[0]

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
export const norm = (s) => String(s ?? '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, ' ').trim()
export const textoAFecha = (t) => {
  const x = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(String(t ?? '').trim())
  if (!x) return null
  const f = new Date(+x[3], +x[2] - 1, +x[1])
  return f.getMonth() === +x[2] - 1 ? f : null
}

/** Un estudio está finalizado si el evaluado lo declaró «Graduado» o «Terminó materias» (pendiente de grado). */
export function esFinalizado(estudio) {
  const e = norm(estudio.estado)
  return e === 'graduado' || e.startsWith('termino materias')
}

/** El estudio finalizado con la fecha de finalización más reciente (o null si ninguno está finalizado). */
export function estudioFinalizadoMasReciente(estudios) {
  const fin = estudios.filter(esFinalizado)
  if (fin.length === 0) return null
  const tiempo = (e) => textoAFecha(e.fecha_fin)?.getTime() ?? -Infinity
  return fin.reduce((a, b) => (tiempo(b) > tiempo(a) ? b : a))
}

/** Lo que declaró el evaluado de un estudio, congelado al seleccionarlo (JSON). */
export function instantanea(estudio) {
  const { id, nivel, institucion, titulo, fecha_inicio, fecha_fin, estado, ciudad, en_exterior, pais_exterior, en_curso } = estudio
  return JSON.stringify({ id, nivel, institucion, titulo, fecha_inicio, fecha_fin, estado, ciudad, en_exterior, pais_exterior, en_curso })
}
export function leerInstantanea(texto) {
  try { const o = JSON.parse(texto); return o && typeof o === 'object' ? o : null } catch { return null }
}

/** ¿Cambió el evaluado lo declarado de ese estudio después de seleccionarlo? 'cambio' | 'desaparecio' | null. */
export function cambioDeclaracion(seleccionId, copia, estudios) {
  if (!tiene(seleccionId) || !copia) return null
  const vivo = estudios.find(e => String(e.id) === String(seleccionId))
  if (!vivo) return 'desaparecio'
  const nuevo = leerInstantanea(instantanea(vivo))
  const claves = ['nivel', 'institucion', 'titulo', 'fecha_inicio', 'fecha_fin', 'estado', 'ciudad', 'en_exterior', 'pais_exterior', 'en_curso']
  return claves.some(k => norm(copia[k]) !== norm(nuevo[k])) ? 'cambio' : null
}

/**
 * Qué estudio le corresponde al criterio. { estudio, automatico, mensaje }:
 * - «Último estudio finalizado»: se selecciona solo el más reciente finalizado; si no hay, mensaje y el analista elige.
 * - Los otros criterios: el analista elige (y explica en Observaciones).
 */
export function estudioSegunCriterio(criterio, estudios, sinEstudios) {
  if (sinEstudios && estudios.length === 0) return { estudio: null, automatico: false, mensaje: 'El evaluado declaró que no finalizó el bachillerato.' }
  if (estudios.length === 0) return { estudio: null, automatico: false, mensaje: 'El evaluado no registró estudios en su hoja de vida.' }
  if (criterio === CRITERIO_POR_DEFECTO) {
    const e = estudioFinalizadoMasReciente(estudios)
    return e ? { estudio: e, automatico: true, mensaje: '' } : { estudio: null, automatico: false, mensaje: 'El evaluado no declara estudios finalizados: elige cuál verificar.' }
  }
  return { estudio: null, automatico: false, mensaje: 'Elige el estudio que se va a verificar y explica el motivo en Observaciones.' }
}

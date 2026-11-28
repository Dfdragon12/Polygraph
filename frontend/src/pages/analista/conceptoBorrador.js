// AN-07 · Borrador del concepto del analista, armado con los hallazgos que suman y las alertas que no. Es solo un punto de partida: el analista lo edita.
// Tono de las reglas del analista: describe lo que registra o no registra; nunca dice «tiene antecedentes» ni «es culpable».

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const minuscula = (t) => (t ? t.charAt(0).toLowerCase() + t.slice(1) : t)
const sinPunto = (t) => String(t ?? '').trim().replace(/[.\s]+$/, '')

/**
 * hallazgos = [{ ref, descripcion, fuente, detalle, puntos }]   (los que arma el sistema; puntos = número o «Crítico»)
 * alertas   = [{ situacion }]                                    (hallazgos sin impacto registrados por el analista)
 */
export function borradorConcepto(hallazgos, alertas) {
  const frases = []
  const criticos = hallazgos.filter(h => String(h.puntos).trim().toLowerCase() === 'crítico' || String(h.puntos).trim().toLowerCase() === 'critico')
  const conPuntos = hallazgos.filter(h => !criticos.includes(h))

  if (criticos.length > 0) {
    frases.push(`Registra ${criticos.length === 1 ? 'un hallazgo crítico' : 'hallazgos críticos'}: ${criticos.map(h => describir(h, false)).join('; ')}.`)
  } else if (conPuntos.length > 0) {
    frases.push('No registra hallazgos críticos.')
  }
  if (conPuntos.length > 0) {
    frases.push(`Registra ${conPuntos.length === 1 ? 'un hallazgo con impacto en el nivel de riesgo' : `${conPuntos.length} hallazgos con impacto en el nivel de riesgo`}: ${conPuntos.map(h => describir(h, true)).join('; ')}.`)
  } else if (criticos.length === 0) {
    frases.push('No registra hallazgos críticos ni hallazgos que afecten el nivel de riesgo.')
  }
  const lista = alertas.map(a => sinPunto(a.situacion)).filter(Boolean)
  if (lista.length > 0) {
    frases.push(`Además, ${lista.length === 1 ? 'queda una alerta informativa' : `quedan ${lista.length} alertas informativas`} que no suma${lista.length === 1 ? '' : 'n'} puntos: ${lista.map(minuscula).join('; ')}.`)
  }
  return frases.join(' ')
}

function describir(h, conPuntos) {
  const partes = [sinPunto(h.ref)]
  if (tiene(h.fuente)) partes.push(sinPunto(h.fuente))
  if (conPuntos && tiene(h.puntos)) partes.push(`${sinPunto(h.puntos)} puntos`)
  const detalle = tiene(h.detalle) ? `: ${sinPunto(h.detalle)}` : ''
  return `${minuscula(sinPunto(h.descripcion))} (${partes.join(', ')})${detalle}`
}

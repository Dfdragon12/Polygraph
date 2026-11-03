import { FASES, contenidoDelTema } from './fasesFormato'
import { PREGUNTA_POR_COLUMNA, COMPARACION_POR_COLUMNA } from './preguntasEntrevista'
import { leer, respuestaDe } from './logicaPregunta'

// PF-07 · Qué falta por diligenciar. Es la MISMA cuenta del avance de cada tema (el semáforo de las fases) pero
// con el nombre de cada cosa que falta, para listarla en «Revisión y envío» con un enlace al tema.

export const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''

// PF-08 · Quita de un título lo técnico que solo sirve a quien arma la plantilla: «(no se imprime)», «(lo calcula el sistema)»,
// «(se guarda, NO se imprime ni suma)», «(escríbela solo si…)», y el número de sección («3. »). Lo demás entre paréntesis se conserva.
const TECNICO = /\s*\((?:no se imprime|se guarda[^)]*|lo calcula[^)]*|lo arma[^)]*|el código[^)]*|cada una[^)]*|no suman[^)]*|escríbela[^)]*|primera hoja|en letras)\)\s*$/i
export const tituloLimpio = (n) => {
  let t = String(n ?? '')
  for (let i = 0; i < 3 && TECNICO.test(t); i += 1) t = t.replace(TECNICO, '')
  return t.replace(/^\d+\.\s*/, '').replace(/\s*[–-]\s*respondió en el link$/i, '').trim()
}
export const limpiarNombre = tituloLimpio

const esSi = (v) => /^s[ií](?![a-zà-ÿ])/i.test(String(v ?? '').trim())

// Campos que solo se exigen cuando otra respuesta lo pide.
const CONDICIONALES = {
  pol_contramedidas_det: (s) => esSi(leer(s, 'pol_contramedidas')),
  pol_dificultad_instr_det: (s) => esSi(leer(s, 'pol_dificultad_instr')),
  pol_puede_repetir: (s) => /no\s+opini/i.test(String(leer(s, 'pol_resultado') ?? '')),
}
const seExige = (c, secciones) => (CONDICIONALES[c.clave] ? CONDICIONALES[c.clave](secciones) : true)

// { total, faltantes: [texto] } de un tema.
export function revisarTema(tema, secciones, tablasFijas, foto) {
  if (tema.soloLectura || tema.informe || tema.id === 'admisiones') return { total: 0, faltantes: [] }
  if (tema.foto) {
    const bien = !!(foto?.existe && foto.vigente?.cedulaVerificada)
    return { total: 1, faltantes: bien ? [] : [foto?.existe ? 'Verificar la cédula del evaluado' : 'Tomar la foto del evaluado y verificar la cédula'] }
  }
  if (tema.id === 'preguntas') {
    // Si el examen no se realizó, las preguntas no son obligatorias.
    const estado = leer(secciones, 'examen_estado')
    if (estado && estado !== 'Realizado') return { total: 0, faltantes: [] }
    const sec = secciones.find(x => x.clave === 'preguntas')
    const cp = sec?.columnas.find(c => c.clave === 'pr_pregunta')?.idColumna
    const cr = sec?.columnas.find(c => c.clave === 'pr_resultado')?.idColumna
    if (!sec || sec.filas.length === 0) return { total: 1, faltantes: ['Agregar al menos una pregunta relevante'] }
    const faltantes = []
    sec.filas.forEach((f, n) => {
      if (!tieneValor(f.valores[cp])) faltantes.push(`Pregunta relevante R${n + 1}: escribir la pregunta`)
      if (!tieneValor(f.valores[cr])) faltantes.push(`Pregunta relevante R${n + 1}: elegir el resultado`)
    })
    return { total: sec.filas.length * 2, faltantes }
  }
  let total = 0
  const faltantes = []
  const revisar = (lleno, texto) => { total += 1; if (!lleno) faltantes.push(texto) }
  contenidoDelTema(tema, secciones, tablasFijas).forEach(p => {
    if (p.fija) {
      const val = p.validacion
      if (!val) return
      val.filas.forEach((f, n) => val.columnas.filter(c => !c.automatico).forEach(c =>
        revisar(tieneValor(f.valores[c.idColumna]), `${limpiarNombre(p.fija.nombre ?? tema.titulo)} ${n + 1}: ${limpiarNombre(c.nombre)}`)))
      return
    }
    const manuales = p.columnas.filter(c => !c.automatico)
    if (p.seccion.tipo === 'CAMPOS') {
      manuales.forEach(c => {
        const cm = COMPARACION_POR_COLUMNA[c.clave]
        if (cm) {
          if (!cm.principal) return
          revisar(!!leer(secciones, cm.comparacion.cmp) && tieneValor(leer(secciones, cm.comparacion.prueba)), `Comparación: ${limpiarNombre(c.nombre)}`)
          return
        }
        const pq = PREGUNTA_POR_COLUMNA[c.clave]
        if (pq) {
          if (!pq.principal) return
          revisar(!!respuestaDe(secciones, pq.pregunta) && tieneValor(leer(secciones, pq.pregunta.texto)), `Pregunta: ${limpiarNombre(c.nombre)}`)
          return
        }
        if (!seExige(c, secciones)) return
        revisar(tieneValor(p.seccion.valores[c.idColumna]), limpiarNombre(c.nombre))
      })
    } else {
      p.seccion.filas.forEach((f, n) => manuales.forEach(c => revisar(tieneValor(f.valores[c.idColumna]), `${limpiarNombre(p.seccion.nombre)} ${n + 1}: ${limpiarNombre(c.nombre)}`)))
    }
  })
  return { total, faltantes }
}

// Lo que falta en todo el formato, agrupado por tema (en el orden de las fases) y el total.
export function faltantesDelFormato(secciones, tablasFijas, foto) {
  const grupos = []
  FASES.forEach(f => f.temas.forEach(t => {
    const { faltantes } = revisarTema(t, secciones, tablasFijas, foto)
    if (faltantes.length > 0) grupos.push({ id: t.id, titulo: t.titulo.replace(/^\d+\.\s*/, ''), fase: f.id, items: faltantes })
  }))
  return { grupos, total: grupos.reduce((n, g) => n + g.items.length, 0) }
}

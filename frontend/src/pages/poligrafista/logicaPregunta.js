import { PREGUNTA_POR_COLUMNA } from './preguntasEntrevista'
import { leerAdmision, ponerAdmision, quitarAdmision } from './admisionesPregunta'

// PF-03 · Lo que pasa cuando el poligrafista responde una pregunta. Todo son funciones puras sobre la lista de secciones.

const columna = (secciones, clave) => {
  for (const s of secciones) {
    if (s.origenFijoClave || s.tipo !== 'CAMPOS') continue
    const c = s.columnas.find(x => x.clave === clave)
    if (c) return { seccion: s, columna: c }
  }
  return null
}

export function leer(secciones, clave) {
  const r = columna(secciones, clave)
  return r ? (r.seccion.valores[r.columna.idColumna] ?? '') : ''
}

export function poner(secciones, clave, valor) {
  const r = columna(secciones, clave)
  if (!r) return secciones
  return secciones.map(s => (s === r.seccion ? { ...s, valores: { ...s.valores, [r.columna.idColumna]: valor } } : s))
}

const clavesDe = (q) => [q.texto, q.resp, q.frecuencia, ...(q.flags ?? []), ...(q.listas ?? []), ...(q.subs ?? []), ...(q.extras ?? [])].filter(Boolean)
export const valoresDe = (secciones, q) => Object.fromEntries(clavesDe(q).map(c => [c, leer(secciones, c)]))

// Respuesta de la pregunta: 'Sí' | 'No' | '' (sin responder). En las de frecuencia, Nulo = No y cualquier otra frecuencia = Sí.
export function respuestaDe(secciones, q) {
  if (q.tipo === 'frecuencia') {
    const f = leer(secciones, q.frecuencia)
    return !f ? '' : f === 'Nulo' ? 'No' : 'Sí'
  }
  return leer(secciones, q.resp)
}

const limpiar = (secciones, claves) => claves.reduce((acc, c) => poner(acc, c, ''), secciones)

// Mantiene la admisión de la pregunta al día con lo que se escribió y con las listas (categoría por defecto).
export function sincronizar(secciones, q) {
  const adm = leerAdmision(secciones, q.id)
  if (!adm) return secciones
  const v = valoresDe(secciones, q)
  const porDefecto = q.categoria(v)
  const propia = !adm.categoria || q.categorias.includes(adm.categoria)
  return ponerAdmision(secciones, q.id, {
    texto: v[q.texto],
    categoria: propia && porDefecto ? porDefecto : adm.categoria,
    cuando: adm.cuando,
    tipo: adm.tipo,
  })
}

// "Sí" o "No" en una pregunta de botones. `frase` = la frase estándar de esa pregunta.
// `conflicto`: el candidato declaró Sí en su link; con «No» no se precarga la frase, hay que explicar la diferencia (PF-04).
export function responder(secciones, q, resp, frase, conflicto = false) {
  let s = poner(secciones, q.resp, resp)
  const textoActual = leer(s, q.texto)
  if (resp === 'No') {
    s = limpiar(s, [...(q.flags ?? []), ...(q.listas ?? []), ...(q.extras ?? [])])
    s = poner(s, q.texto, conflicto ? (textoActual === frase ? '' : textoActual) : (frase ?? ''))
    return quitarAdmision(s, q.id)
  }
  if (textoActual === frase) s = poner(s, q.texto, '')
  ;(q.flags ?? []).forEach(f => { s = poner(s, f, 'Sí') })
  return leerAdmision(s, q.id) ? sincronizar(s, q) : abrirAdmision(s, q)
}

// Frecuencia de alcohol o drogas: Nulo usa la frase estándar; cualquier otra abre las preguntas de ese consumo.
export function elegirFrecuencia(secciones, q, frecuencia, frase) {
  let s = poner(secciones, q.frecuencia, frecuencia)
  const textoActual = leer(s, q.texto)
  if (frecuencia === 'Nulo') {
    s = limpiar(s, [...(q.listas ?? []), ...(q.subs ?? [])])
    s = poner(s, q.texto, frase ?? '')
    return quitarAdmision(s, q.id)
  }
  if (!frecuencia) return s
  if (textoActual === frase) s = poner(s, q.texto, '')
  return leerAdmision(s, q.id) ? sincronizar(s, q) : abrirAdmision(s, q)
}

// Crea la admisión de la pregunta (marcada por defecto al responder Sí).
export function abrirAdmision(secciones, q) {
  const v = valoresDe(secciones, q)
  return ponerAdmision(secciones, q.id, { texto: v[q.texto], categoria: q.categoria(v) ?? '', cuando: '', tipo: '' })
}

// Edita un campo de una pregunta (texto, lista, casilla) y deja la admisión al día.
export function editar(secciones, clave, valor) {
  const s = poner(secciones, clave, valor)
  const dueña = PREGUNTA_POR_COLUMNA[clave]?.pregunta
  return dueña ? sincronizar(s, dueña) : s
}

// «Lo confirma» llena el campo con «Lo confirma.» (editable); «Difiere» lo deja vacío para que explique la diferencia.
export const FRASE_CONFIRMA = 'Lo confirma.'
export function elegirComparacion(secciones, c, eleccion) {
  let s = poner(secciones, c.cmp, eleccion)
  const actual = leer(s, c.prueba)
  if (eleccion === 'Lo confirma') return actual === '' ? poner(s, c.prueba, FRASE_CONFIRMA) : s
  return actual === FRASE_CONFIRMA ? poner(s, c.prueba, '') : s
}

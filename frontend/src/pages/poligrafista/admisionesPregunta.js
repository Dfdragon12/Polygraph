import { PERSONAL } from './preguntasEntrevista'

// PF-03 · La admisión de una pregunta nace en el lugar: es una fila de la tabla de admisiones (o de «Personal o de salud», que no se imprime)
// marcada con la etiqueta «origen:<pregunta>». Estas funciones reciben y devuelven la lista de secciones sin tocar la original.

const ORIGEN = 'origen:'
let n = 0
const idLocal = () => `adm-${Date.now()}-${++n}`

const seccion = (secciones, clave) => secciones.find(s => s.clave === clave && !s.origenFijoClave)
const idCol = (sec, clave) => sec?.columnas.find(c => c.clave === clave)?.idColumna
const etiquetaDe = (qid) => `${ORIGEN}${qid}`
export const esOrigen = (fila) => (fila.etiqueta ?? '').startsWith(ORIGEN)

function valoresPorClave(sec, fila) {
  const v = {}
  sec.columnas.forEach(c => { v[c.clave] = fila.valores[c.idColumna] ?? '' })
  return v
}

// La admisión de una pregunta: { texto, categoria, cuando, tipo } o null si no es admisión.
export function leerAdmision(secciones, qid) {
  for (const clave of ['admisiones', 'admisiones_sensibles']) {
    const sec = seccion(secciones, clave)
    const fila = sec?.filas.find(f => f.etiqueta === etiquetaDe(qid))
    if (fila) {
      const v = valoresPorClave(sec, fila)
      return { texto: v.ad_texto, categoria: clave === 'admisiones_sensibles' ? PERSONAL : v.ad_categoria, cuando: v.ad_cuando ?? '', tipo: v.ad_tipo ?? '' }
    }
  }
  return null
}

function sinFila(secciones, etiqueta) {
  return secciones.map(s => (s.clave === 'admisiones' || s.clave === 'admisiones_sensibles') && !s.origenFijoClave
    ? { ...s, filas: s.filas.filter(f => f.etiqueta !== etiqueta) } : s)
}

export function quitarAdmision(secciones, qid) {
  return sinFila(secciones, etiquetaDe(qid))
}

// Crea o actualiza la admisión de una pregunta. Una categoría «Personal o de salud» va a la tabla que no se imprime.
export function ponerAdmision(secciones, qid, { texto, categoria, cuando = '', tipo = '' }) {
  const etiqueta = etiquetaDe(qid)
  const personal = categoria === PERSONAL
  const destino = seccion(secciones, personal ? 'admisiones_sensibles' : 'admisiones')
  if (!destino) return secciones
  const existente = destino.filas.find(f => f.etiqueta === etiqueta)
  const valores = {}
  const poner = (clave, valor) => { const id = idCol(destino, clave); if (id !== undefined) valores[id] = valor }
  poner('ad_texto', texto ?? '')
  if (!personal) { poner('ad_categoria', categoria ?? ''); poner('ad_cuando', cuando); poner('ad_tipo', tipo) }
  const base = sinFila(secciones, etiqueta)
  return base.map(s => {
    if (s.idSeccion !== destino.idSeccion) return s
    const fila = existente
      ? { ...existente, valores: { ...existente.valores, ...valores } }
      : { localId: idLocal(), idFila: null, etiqueta, valores, justificaciones: {} }
    return { ...s, filas: [...s.filas, fila] }
  })
}

// Admisiones que el poligrafista agrega a mano («+ Agregar otra admisión»): filas sin origen en cualquiera de las dos tablas.
export function admisionesManuales(secciones) {
  const salida = []
  for (const clave of ['admisiones', 'admisiones_sensibles']) {
    const sec = seccion(secciones, clave)
    sec?.filas.filter(f => !esOrigen(f)).forEach(f => {
      const v = valoresPorClave(sec, f)
      salida.push({ localId: f.localId, idFila: f.idFila, texto: v.ad_texto, categoria: clave === 'admisiones_sensibles' ? PERSONAL : v.ad_categoria, cuando: v.ad_cuando ?? '' })
    })
  }
  return salida
}

// Cambia una admisión manual (ponerla en otra categoría puede pasarla de una tabla a la otra).
export function editarManual(secciones, localId, cambios) {
  const actual = admisionesManuales(secciones).find(a => a.localId === localId)
  if (!actual) return secciones
  const nuevo = { ...actual, ...cambios }
  const sinEsa = secciones.map(s => (s.clave === 'admisiones' || s.clave === 'admisiones_sensibles') && !s.origenFijoClave
    ? { ...s, filas: s.filas.filter(f => f.localId !== localId) } : s)
  return agregarFila(sinEsa, nuevo, localId)
}

export function agregarManual(secciones) {
  return agregarFila(secciones, { texto: '', categoria: '', cuando: '' }, idLocal())
}

export function quitarManual(secciones, localId) {
  return secciones.map(s => (s.clave === 'admisiones' || s.clave === 'admisiones_sensibles') && !s.origenFijoClave
    ? { ...s, filas: s.filas.filter(f => f.localId !== localId) } : s)
}

function agregarFila(secciones, { texto, categoria, cuando }, localId) {
  const personal = categoria === PERSONAL
  const destino = seccion(secciones, personal ? 'admisiones_sensibles' : 'admisiones')
  if (!destino) return secciones
  const valores = {}
  const poner = (clave, valor) => { const id = idCol(destino, clave); if (id !== undefined) valores[id] = valor }
  poner('ad_texto', texto ?? '')
  if (!personal) { poner('ad_categoria', categoria ?? ''); poner('ad_cuando', cuando ?? '') }
  return secciones.map(s => (s.idSeccion === destino.idSeccion
    ? { ...s, filas: [...s.filas, { localId, idFila: null, etiqueta: '', valores, justificaciones: {} }] } : s))
}

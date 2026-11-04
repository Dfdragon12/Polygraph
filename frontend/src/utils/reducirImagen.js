// VD-07 · Reducción de fotos en el navegador antes de enviarlas: lado mayor de máximo 1600 px, JPG calidad 0,85 (baja un poco si hace falta para quedar
// por debajo de ~500 KB), con la orientación ya aplicada (EXIF) y SIN metadatos: al volver a dibujar la imagen en un canvas se pierden la ubicación GPS y
// todo lo demás. Sirve igual para lo que sale de la cámara y de un archivo; un HEIC (iPhone) se convierte a JPG.

export const MAX_LADO = 1600
export const CALIDAD = 0.85
export const OBJETIVO_BYTES = 500 * 1024
export const MAX_ORIGINAL_BYTES = 15 * 1024 * 1024

export const MENSAJES_FOTO = {
  tipo: 'El archivo debe ser una foto JPG, PNG o HEIC.',
  pesada: 'La foto es demasiado pesada. Elige una de menos de 15 MB.',
  subida: 'No se pudo subir la foto. Revisa tu conexión e inténtalo de nuevo.',
}

// Tipo REAL por los primeros bytes (no por la extensión ni por lo que diga el navegador): 'jpg' | 'png' | 'heic' | null.
export async function tipoRealDeImagen(blob) {
  const b = new Uint8Array(await blob.slice(0, 16).arrayBuffer())
  if (b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return 'jpg'
  if (b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) return 'png'
  if (String.fromCharCode(b[4], b[5], b[6], b[7]) === 'ftyp') {
    const marca = String.fromCharCode(b[8], b[9], b[10], b[11])
    if (['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'mif1', 'msf1'].includes(marca)) return 'heic'
  }
  return null
}

async function aBitmap(blob) {
  try {
    return await createImageBitmap(blob, { imageOrientation: 'from-image' })
  } catch {
    // Navegadores sin esa opción: <img> aplica la orientación EXIF por sí solo.
    const url = URL.createObjectURL(blob)
    try {
      return await new Promise((ok, mal) => { const i = new Image(); i.onload = () => ok(i); i.onerror = mal; i.src = url })
    } finally {
      URL.revokeObjectURL(url)
    }
  }
}

const aJpg = (canvas, calidad) => new Promise(ok => canvas.toBlob(ok, 'image/jpeg', calidad))

/** Devuelve { blob, ancho, alto } o lanza Error con `.mensaje` listo para mostrar. */
export async function reducirImagen(fuente) {
  const falla = (mensaje) => Object.assign(new Error(mensaje), { mensaje })
  if (fuente.size > MAX_ORIGINAL_BYTES) throw falla(MENSAJES_FOTO.pesada)
  const tipo = await tipoRealDeImagen(fuente)
  if (!tipo) throw falla(MENSAJES_FOTO.tipo)
  let blob = fuente
  if (tipo === 'heic') {
    try {
      const { default: heic2any } = await import('heic2any')
      const r = await heic2any({ blob: fuente, toType: 'image/jpeg', quality: 0.9 })
      blob = Array.isArray(r) ? r[0] : r
    } catch {
      throw falla(MENSAJES_FOTO.tipo)
    }
  }
  let imagen
  try { imagen = await aBitmap(blob) } catch { throw falla(MENSAJES_FOTO.tipo) }
  const ancho0 = imagen.width ?? imagen.naturalWidth
  const alto0 = imagen.height ?? imagen.naturalHeight
  let escala = Math.min(1, MAX_LADO / Math.max(ancho0, alto0))
  let calidad = CALIDAD
  for (let intento = 0; intento < 8; intento += 1) {
    const ancho = Math.max(1, Math.round(ancho0 * escala))
    const alto = Math.max(1, Math.round(alto0 * escala))
    const canvas = document.createElement('canvas')
    canvas.width = ancho
    canvas.height = alto
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#fff'   // un PNG con transparencia no debe quedar negro al pasar a JPG
    ctx.fillRect(0, 0, ancho, alto)
    ctx.drawImage(imagen, 0, 0, ancho, alto)
    const salida = await aJpg(canvas, calidad)
    if (!salida) throw falla(MENSAJES_FOTO.tipo)
    if (salida.size <= OBJETIVO_BYTES || intento === 7) { imagen.close?.(); return { blob: salida, ancho, alto } }
    // Primero baja la calidad; si ya está baja, achica.
    if (calidad > 0.6) calidad = Math.max(0.6, calidad - 0.1)
    else escala *= 0.85
  }
  throw falla(MENSAJES_FOTO.tipo)
}

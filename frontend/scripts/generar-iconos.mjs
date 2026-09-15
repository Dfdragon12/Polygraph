// Regenera los íconos de la PWA (public/icon-192.png, icon-512.png, icon-512-maskable.png,
// apple-touch-icon.png) a partir de public/icon.svg — correr después de reemplazar ese SVG
// por el diseño final:
//
//   npm run icons:generar
//
import sharp from 'sharp'
import { readFileSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'

const publicDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'public')
const svgPath = resolve(publicDir, 'icon.svg')
const svgBuffer = readFileSync(svgPath)
const BG = '#0f172a' // debe coincidir con el fondo del icon.svg y background_color del manifest.json

async function main() {
  // Iconos "any" — se asume que el SVG ya llega borde a borde con su propio fondo.
  await sharp(svgBuffer).resize(192, 192).png().toFile(resolve(publicDir, 'icon-192.png'))
  await sharp(svgBuffer).resize(512, 512).png().toFile(resolve(publicDir, 'icon-512.png'))

  // Maskable — el sistema puede recortar hasta ~20% del borde con formas (círculo, squircle),
  // así que el contenido se reduce y se centra sobre un lienzo cuadrado sin esquinas redondeadas
  // propias (el masking lo aplica el SO), para que nunca se corte el logo.
  const contenido = await sharp(svgBuffer).resize(370, 370).png().toBuffer()
  await sharp({ create: { width: 512, height: 512, channels: 4, background: BG } })
    .composite([{ input: contenido, gravity: 'center' }])
    .png()
    .toFile(resolve(publicDir, 'icon-512-maskable.png'))

  // apple-touch-icon — iOS aplica su propia máscara (superellipse) y no acepta transparencia
  // bien, así que se renderiza sin canal alfa sobre el mismo fondo.
  await sharp(svgBuffer)
    .resize(180, 180)
    .flatten({ background: BG })
    .png()
    .toFile(resolve(publicDir, 'apple-touch-icon.png'))

  console.log('Íconos regenerados en', publicDir)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})

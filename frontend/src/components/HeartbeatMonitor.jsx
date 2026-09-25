/* "Señal de vida" tipo monitor de hospital — línea de electrocardiograma deslizándose sin
   parar. Es una decoración de FONDO (absolute, detrás del contenido, opacidad baja) — no un
   widget con su propia caja/borde compitiendo con el logo y el carrusel. El componente padre
   debe tener `relative overflow-hidden` para que quede recortada dentro del panel. */

function generarPuntos(ciclos, anchoUnidad, base, amplitud) {
  const puntos = [[0, base]]
  for (let i = 0; i < ciclos; i++) {
    const x = i * anchoUnidad
    puntos.push(
      [x + anchoUnidad * 0.18, base],
      [x + anchoUnidad * 0.26, base - amplitud * 0.3],
      [x + anchoUnidad * 0.33, base + amplitud],
      [x + anchoUnidad * 0.42, base - amplitud * 1.35],
      [x + anchoUnidad * 0.5, base + amplitud * 0.25],
      [x + anchoUnidad * 0.58, base],
      [x + anchoUnidad, base],
    )
  }
  return puntos
}

const ANCHO_UNIDAD = 100
const CICLOS_VISIBLES = 7
// El doble de ciclos para que el tramo 0%→100% del path sea exactamente el doble del ancho
// visible — así translateX(-50%) engancha el final con el principio sin salto (loop continuo).
const PUNTOS = generarPuntos(CICLOS_VISIBLES * 2, ANCHO_UNIDAD, 20, 15)
const RUTA = 'M' + PUNTOS.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' L')
const ANCHO_VISIBLE = CICLOS_VISIBLES * ANCHO_UNIDAD

export default function HeartbeatMonitor({ className = 'absolute left-0 bottom-24 h-20 opacity-[0.14]' }) {
  return (
    <svg
      className={`${className} animate-heartbeat-scroll pointer-events-none`}
      style={{ width: ANCHO_VISIBLE * 2 }}
      viewBox={`0 0 ${ANCHO_VISIBLE * 2} 40`}
      preserveAspectRatio="none"
      fill="none"
    >
      <path d={RUTA} stroke="oklch(0.80 0.08 20)" strokeWidth="1.25" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

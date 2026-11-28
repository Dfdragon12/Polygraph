// Los tres anillos de riesgo (Bajo · Medio · Alto) del inicio del cliente y del bloque «Riesgo detectado» del gestor (PB-05). UN solo componente.
//   datos: { total, bajo, medio, alto, pctBajo, pctMedio, pctAlto } — los calcula el servidor y los porcentajes ya suman 100
//   onSeleccionar(nivel): hace cada anillo un botón (teclado y lector de pantalla); sin él, son tarjetas informativas
//   vacio: texto cuando no hay estudios con resultado en el periodo
// El porcentaje y el conteo SIEMPRE están escritos (el color Medio no alcanza 3:1 sobre blanco) y cada anillo trae su texto alternativo.

const NIVELES = [
  { codigo: 'BAJO', nombre: 'Bajo', color: '#2e8a5b', cantidad: 'bajo', pct: 'pctBajo' },
  { codigo: 'MEDIO', nombre: 'Medio', color: '#d9952b', cantidad: 'medio', pct: 'pctMedio' },
  { codigo: 'ALTO', nombre: 'Alto', color: '#b23a48', cantidad: 'alto', pct: 'pctAlto' },
]

const RADIO = 44
const CIRCUNFERENCIA = 2 * Math.PI * RADIO
const nf = new Intl.NumberFormat('es-CO')

function Anillo({ color, porcentaje }) {
  const largo = (Math.max(0, Math.min(100, porcentaje)) / 100) * CIRCUNFERENCIA
  return (
    <svg viewBox="0 0 108 108" className="h-24 w-24" aria-hidden="true" focusable="false">
      <circle cx="54" cy="54" r={RADIO} fill="none" stroke="#eef0f4" strokeWidth="11" />
      {porcentaje > 0 && (
        <circle cx="54" cy="54" r={RADIO} fill="none" stroke={color} strokeWidth="11" strokeLinecap="round"
          strokeDasharray={`${largo} ${CIRCUNFERENCIA - largo}`} transform="rotate(-90 54 54)" />
      )}
    </svg>
  )
}

export default function AnillosRiesgo({ datos, onSeleccionar, vacio = 'Todavía no hay estudios con resultado en este periodo', unidad = 'estudios', className = '' }) {
  if (!datos || datos.total === 0) {
    return <p className={`rounded-xl border border-dashed border-gray-300 bg-gray-50 px-4 py-8 text-center text-sm text-gray-700 ${className}`}>{vacio}</p>
  }
  return (
    <ul className={`grid grid-cols-1 gap-3 sm:grid-cols-3 ${className}`}>
      {NIVELES.map(n => {
        const cantidad = datos[n.cantidad]
        const pct = datos[n.pct]
        const texto = `Riesgo ${n.nombre.toLowerCase()}: ${nf.format(pct)} %, ${nf.format(cantidad)} de ${nf.format(datos.total)} ${unidad}`
        const cuerpo = (
          <>
            <span className="relative flex h-24 w-24 items-center justify-center">
              <Anillo color={n.color} porcentaje={pct} />
              <span aria-hidden="true" className="absolute text-xl font-bold tabular-nums text-gray-900">{nf.format(pct)} %</span>
            </span>
            <span aria-hidden="true" className="mt-3 block text-sm font-bold text-gray-900">Riesgo {n.nombre}</span>
            <span aria-hidden="true" className="block text-xs tabular-nums text-gray-700">{nf.format(cantidad)} de {nf.format(datos.total)} {unidad}</span>
          </>
        )
        const base = 'flex w-full flex-col items-center rounded-xl border border-gray-200 bg-white px-4 py-5'
        return (
          <li key={n.codigo}>
            {onSeleccionar ? (
              <button type="button" aria-label={texto} onClick={() => onSeleccionar(n.codigo)}
                className={`${base} min-h-[44px] transition-shadow hover:shadow-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600`}>
                {cuerpo}
              </button>
            ) : (
              <div role="img" aria-label={texto} className={base}>{cuerpo}</div>
            )}
          </li>
        )
      })}
    </ul>
  )
}

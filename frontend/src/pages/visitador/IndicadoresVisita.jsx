import { ESCALA_INDICADORES, INDICADORES, calcularEscala } from './fasesVisita'

// VD-04 · Los indicadores de una sección con su nombre (igual que en el Word) y botones 1 · 2 · 3 · 4. Los guarda en las mismas columnas de siempre
// (df_1…, hab_1…, eco_1…, tq_1…). Debajo salen el porcentaje y el concepto de solo lectura (con la regla de motor_vis.py); VD-05 los guarda.
// `valores` = clave → valor de esa sección (o de la fila del módulo); `onValor(clave, valor)` guarda.

export default function IndicadoresVisita({ claveSeccion, valores, soloLectura, onValor }) {
  const g = INDICADORES[claveSeccion]
  if (!g) return null
  const n = g.nombres.length
  const claveDe = (i) => `${g.prefijo}_${i + 1}`
  const actual = (i) => valores[claveDe(i)] ?? ''
  const { pct, concepto } = calcularEscala(g.nombres.map((_, i) => actual(i)), n)

  const elegir = (i, valor) => onValor(claveDe(i), actual(i) === valor ? '' : valor)

  return (
    <section aria-labelledby={`ind-${g.prefijo}`} className="mt-4">
      <h3 id={`ind-${g.prefijo}`} className="text-sm font-bold text-gray-900 mb-1">Indicadores · {g.titulo}</h3>
      <p className="text-xs text-gray-700 mb-3">
        {ESCALA_INDICADORES.map(e => `${e.valor} ${e.texto}`).join(' · ')}
      </p>
      <ul className="divide-y divide-gray-200 border border-gray-200 rounded-lg">
        {g.nombres.map((nombre, i) => (
          <li key={nombre} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
            <span id={`ind-${g.prefijo}-${i}`} className="text-sm text-gray-900 min-w-0 flex-1 basis-56 break-words">{nombre}</span>
            <div role="radiogroup" aria-labelledby={`ind-${g.prefijo}-${i}`} className="flex gap-1.5">
              {ESCALA_INDICADORES.map(e => {
                const activo = actual(i) === e.valor
                return (
                  <button key={e.valor} type="button" role="radio" aria-checked={activo} disabled={soloLectura} onClick={() => elegir(i, e.valor)}
                    aria-label={`${nombre}: ${e.valor}, ${e.texto}`} title={e.texto}
                    className={`min-h-11 min-w-11 px-3 text-sm font-semibold rounded-lg border ${activo ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-800 hover:bg-gray-50'} disabled:opacity-60`}>
                    {e.valor}
                  </button>
                )
              })}
            </div>
          </li>
        ))}
      </ul>
      <div className="mt-3 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-900" role="status" aria-live="polite">
        <span className="font-semibold">{g.titulo}: </span>
        {pct === null ? 'completa los indicadores para ver el resultado' : `${pct} % · ${concepto}`}
      </div>
    </section>
  )
}

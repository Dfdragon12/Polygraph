import { Link } from 'react-router-dom'

// Anexo 1 · Catálogo de referencias: los hallazgos que le salen a este servicio, tal como están en
// el Catálogo de hallazgos (no los que estén escritos en el Word). El Word los imprime con las
// tablas tablas['anexo_catalogo'] y tablas['anexo_alertas'], que la plataforma llena sola.

function Marcador({ children }) {
  return <code className="rounded bg-gray-100 px-1.5 py-0.5 font-mono text-[12px] text-gray-800">{children}</code>
}

export default function PanelAnexo({ catalogo, nombreServicio, usados }) {
  // Solo los hallazgos que usa ESTA plantilla (los que ofrece algún campo o genera alguna opción):
  // es lo mismo que imprime el Word en el Anexo 1.
  const enPlantilla = catalogo.filter(h => usados.has(h.codigo))
  const hallazgos = enPlantilla.filter(h => h.tipo !== 'ALERTA')
  const alertas = enPlantilla.filter(h => h.tipo === 'ALERTA')

  const fila = (h) => (
    <tr key={h.codigo}>
      <td className="whitespace-nowrap px-3 py-2 font-mono text-[13px] font-semibold">{h.codigo}</td>
      <td className="px-3 py-2 text-sm">{h.descripcion}</td>
      <td className="whitespace-nowrap px-3 py-2 text-right text-sm tabular-nums">
        {h.tipo === 'CRITICO' ? <span className="font-bold text-red-600">Crítico</span> : h.tipo === 'ALERTA' ? 'Alerta' : h.puntos}
      </td>
      <td className="whitespace-nowrap px-3 py-2 text-xs">
        {h.acumula && <span className="mr-1 rounded-full bg-amber-50 px-2 py-0.5 text-amber-700">por fila</span>}
        {h.soloCondicion && <span className="rounded-full bg-orange-50 px-2 py-0.5 text-orange-700">conducción</span>}
      </td>
    </tr>
  )

  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-gray-200 bg-white">
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-gray-100 px-5 py-4">
          <div>
            <h3 className="text-base font-semibold text-gray-900">Anexo 1 · Catálogo de referencias</h3>
            <p className="text-sm text-gray-500">Los {enPlantilla.length} hallazgos que usa la plantilla de {nombreServicio}. Es lo que imprime el Word en el Anexo 1.</p>
          </div>
          <Link to="/admin/riesgo/hallazgos" className="rounded-lg border border-gray-200 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50">
            Editar el catálogo
          </Link>
        </div>
        <div className="overflow-x-auto">
          <table className="table-stack min-w-full">
            <thead className="bg-gray-50 text-left text-[11px] font-semibold uppercase tracking-wide text-gray-500">
              <tr><th className="px-3 py-2">Ref.</th><th className="px-3 py-2">Hallazgo</th><th className="px-3 py-2 text-right">Calificación</th><th className="px-3 py-2">Reglas</th></tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {hallazgos.map(fila)}
              {alertas.length > 0 && (
                <tr><td colSpan={4} className="bg-gray-50 px-3 py-1.5 text-center text-[11px] font-semibold uppercase tracking-wide text-gray-500">Alertas — sin puntos, requieren revisión</td></tr>
              )}
              {alertas.map(fila)}
              {enPlantilla.length === 0 && (
                <tr><td colSpan={4} className="px-3 py-8 text-center text-sm text-gray-400">Esta plantilla todavía no usa hallazgos. Márcalos en el bloque "Calificación" de los campos.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="rounded-2xl border border-primary-100 bg-primary-50/40 px-5 py-4 text-sm text-gray-700">
        <p className="font-semibold text-gray-900">Cómo ponerlo en el Word</p>
        <ol className="mt-2 list-decimal space-y-1.5 pl-5">
          <li>Arma la tabla del anexo con su encabezado (Ref. · Hallazgo · Calificación) y <b>una sola fila</b> debajo.</li>
          <li>En esa fila escribe <Marcador>{"${['a1_ref']}"}</Marcador>, <Marcador>{"${['a1_hallazgo']}"}</Marcador> y <Marcador>{"${['a1_calificacion']}"}</Marcador>.</li>
          <li>Selecciona la fila y agrega un comentario de Word con <Marcador>{"repeatTableRow(tablas['anexo_catalogo'])"}</Marcador>.</li>
          <li>Para las alertas, lo mismo en otra fila con <Marcador>{"repeatTableRow(tablas['anexo_alertas'])"}</Marcador>.</li>
        </ol>
        <p className="mt-2 text-xs text-gray-500">Así el anexo siempre sale igual al catálogo: si cambias un punto aquí, el informe lo muestra sin tocar el Word. El Word de Antecedentes de <code className="font-mono">docs/antecedentes</code> ya viene armado así.</p>
      </div>
    </div>
  )
}

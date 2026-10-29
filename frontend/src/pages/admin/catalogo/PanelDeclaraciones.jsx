import { Link } from 'react-router-dom'

// Declaraciones del candidato: las preguntas que responde en su hoja de vida (Admin → Novedades →
// Hoja de vida del candidato). Son las mismas para todas las plantillas; aquí se ven para saber que
// existen y cómo imprimirlas. El Word las trae con la tabla fija tablas['declaraciones'], que la
// plataforma llena sola con lo que respondió el candidato (CandidatoInfoService.resolverTablasFijas).

function Marcador({ children }) {
  return <code className="rounded bg-gray-100 px-1.5 py-0.5 font-mono text-[12px] text-gray-800">{children}</code>
}

export default function PanelDeclaraciones({ preguntas }) {
  const activas = (preguntas ?? []).filter(p => p.activo)
  const inactivas = (preguntas ?? []).filter(p => !p.activo)

  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-gray-200 bg-white">
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-gray-100 px-5 py-4">
          <div>
            <h3 className="text-base font-semibold text-gray-900">Declaraciones del candidato</h3>
            <p className="text-sm text-gray-500">
              Las {activas.length} preguntas que responde el candidato en su hoja de vida (Sí / No y, si es Sí, su explicación).
              Son las mismas para todas las plantillas y quien diligencia las ve en el paso "Candidato".
            </p>
          </div>
          <Link to="/admin/hoja-vida-candidato" className="rounded-lg border border-gray-200 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50">
            Editar las preguntas
          </Link>
        </div>
        <div className="overflow-x-auto">
          <table className="table-stack min-w-full">
            <thead className="bg-gray-50 text-left text-[11px] font-semibold uppercase tracking-wide text-gray-500">
              <tr><th className="px-3 py-2 w-12">N.°</th><th className="px-3 py-2">Pregunta</th><th className="px-3 py-2">Explicación para el candidato</th></tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {activas.map((p, i) => (
                <tr key={p.id}>
                  <td className="px-3 py-2 text-sm text-gray-400 tabular-nums">{i + 1}</td>
                  <td className="px-3 py-2 text-sm font-medium text-gray-800">{p.nombre}</td>
                  <td className="px-3 py-2 text-xs text-gray-500">{p.descripcion || '—'}</td>
                </tr>
              ))}
              {inactivas.length > 0 && (
                <tr><td colSpan={3} className="bg-gray-50 px-3 py-1.5 text-center text-[11px] font-semibold uppercase tracking-wide text-gray-500">Inactivas — el candidato no las ve</td></tr>
              )}
              {inactivas.map(p => (
                <tr key={p.id} className="text-gray-400">
                  <td className="px-3 py-2 text-sm">—</td>
                  <td className="px-3 py-2 text-sm">{p.nombre}</td>
                  <td className="px-3 py-2 text-xs">{p.descripcion || '—'}</td>
                </tr>
              ))}
              {(preguntas ?? []).length === 0 && (
                <tr><td colSpan={3} className="px-3 py-8 text-center text-sm text-gray-400">No hay preguntas creadas. Créalas en "Editar las preguntas".</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="rounded-2xl border border-primary-100 bg-primary-50/40 px-5 py-4 text-sm text-gray-700">
        <p className="font-semibold text-gray-900">Cómo ponerlas en el Word</p>
        <ol className="mt-2 list-decimal space-y-1.5 pl-5">
          <li>Arma una tabla con su encabezado (Pregunta · Respuesta · Explicación) y <b>una sola fila</b> debajo.</li>
          <li>En esa fila escribe <Marcador>{"${['pregunta']}"}</Marcador>, <Marcador>{"${['respuesta']}"}</Marcador> y <Marcador>{"${['detalle']}"}</Marcador>.</li>
          <li>Selecciona la fila y agrega un comentario de Word con <Marcador>{"repeatTableRow(tablas['declaraciones'])"}</Marcador>.</li>
        </ol>
        <p className="mt-2 text-xs text-gray-500">
          Sale una fila por cada pregunta que respondió el candidato, con el texto tal como la respondió. No hace falta crear
          ninguna sección en esta plantilla. Si el candidato aún no ha respondido, la tabla queda solo con el encabezado.
        </p>
      </div>
    </div>
  )
}

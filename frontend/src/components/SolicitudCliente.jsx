// Lo que el cliente diligenció al pedir una poligrafía: el formato adicional (pre-empleo, rutina o
// específica) y los temas que pidió evaluar. Solo lectura. `solicitud` = { titulo, datos: [{etiqueta,
// valor}], temas: [{nombre, detalle}] }; sin nada que mostrar no pinta nada.
export default function SolicitudCliente({ solicitud, titulo = 'Lo que pidió el cliente', detalle = 'Información que diligenció al solicitar el servicio', onEditar }) {
  const datos = solicitud?.datos ?? []
  const temas = solicitud?.temas ?? []
  if (!datos.length && !temas.length) return null

  return (
    <section className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">{titulo}</p>
          <p className="text-[11px] text-gray-500">{solicitud.titulo ? `${solicitud.titulo}. ` : ''}{detalle}</p>
        </div>
        {onEditar && <button type="button" onClick={onEditar} aria-label={`Editar ${titulo}`} className="flex-shrink-0 text-xs font-medium text-primary-700 hover:underline">Editar</button>}
      </div>

      {temas.length > 0 && (
        <div className="mb-4">
          <p className="text-[11px] font-semibold text-gray-400 mb-1.5">Temas a evaluar ({temas.length})</p>
          <ol className="space-y-1.5">
            {temas.map((t, i) => (
              <li key={i} className="flex items-start gap-2.5 rounded-xl bg-primary-50/60 ring-1 ring-inset ring-primary-100 px-3.5 py-2.5">
                <span className="mt-0.5 flex-shrink-0 w-5 h-5 rounded-full bg-primary-600 text-white text-[10px] font-bold flex items-center justify-center">{i + 1}</span>
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-800">{t.nombre}</p>
                  {t.detalle && <p className="text-sm text-gray-700 whitespace-pre-line">{t.detalle}</p>}
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}

      {datos.length > 0 && (
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-5 gap-y-3 bg-gray-50/70 rounded-xl px-3.5 py-3">
          {datos.map((d, i) => (
            <div key={i} className="min-w-0">
              <dt className="text-[11px] text-gray-400">{d.etiqueta}</dt>
              <dd className="text-sm font-medium text-gray-800 whitespace-pre-line break-words">{d.valor}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  )
}

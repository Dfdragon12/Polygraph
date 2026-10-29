// Panel lateral de la nueva solicitud: se actualiza en vivo y dice, de un vistazo, qué ya está listo y qué
// falta por cargar según el servicio elegido. `items` = [{ clave, titulo, detalle, estado }], estado:
// 'listo' | 'pendiente' | 'opcional'. Los mismos items son el "qué debo cargar" antes de llenarlos.
function IconoEstado({ estado }) {
  if (estado === 'listo') {
    return (
      <span className="flex-shrink-0 w-5 h-5 rounded-full bg-green-600 text-white flex items-center justify-center" aria-hidden="true">
        <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3.5}><path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" /></svg>
      </span>
    )
  }
  return <span className={`flex-shrink-0 w-5 h-5 rounded-full border-2 ${estado === 'pendiente' ? 'border-primary-500' : 'border-gray-300 border-dashed'}`} aria-hidden="true" />
}

const TEXTO_ESTADO = { listo: 'Listo', pendiente: 'Pendiente', opcional: 'Opcional' }

export default function PanelLateral({ servicio, items, entrega, estadoPago }) {
  const listos = items.filter((i) => i.estado === 'listo').length
  const requeridos = items.filter((i) => i.estado !== 'opcional').length

  return (
    <aside aria-label="Resumen de tu solicitud" className="lg:sticky lg:top-4 space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100">
          <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">Tu solicitud</p>
          {servicio ? (
            <p className="mt-1 text-base font-semibold text-gray-900 leading-snug">{servicio.nombreProceso}</p>
          ) : (
            <p className="mt-1 text-sm text-gray-500">Aún no has elegido un servicio.</p>
          )}
          {servicio?.clasificacion && <p className="text-xs text-gray-500">{servicio.clasificacion}</p>}
        </div>

        {servicio && (
          <div className="px-5 py-4 border-b border-gray-100">
            <div className="flex items-center justify-between text-xs mb-2" role="status">
              <span className="font-medium text-gray-700">Información a cargar</span>
              <span className="text-gray-600">{listos} de {requeridos}</span>
            </div>
            <div className="h-1.5 rounded-full bg-gray-100 overflow-hidden" aria-hidden="true">
              <div className={`h-full rounded-full transition-all duration-300 ${listos === requeridos ? 'bg-green-600' : 'bg-primary-600'}`}
                style={{ width: `${requeridos ? (listos / requeridos) * 100 : 0}%` }} />
            </div>
            <ul className="mt-4 space-y-3">
              {items.map((i) => (
                <li key={i.clave} className="flex items-start gap-2.5">
                  <IconoEstado estado={i.estado} />
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-800">
                      {i.titulo}
                      <span className="sr-only"> — {TEXTO_ESTADO[i.estado]}</span>
                    </p>
                    {i.detalle && <p className="text-xs text-gray-600 mt-0.5">{i.detalle}</p>}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        )}

        {entrega && (
          <div className="px-5 py-4 border-b border-gray-100">
            <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">Entrega estimada</p>
            <p className="mt-1 text-sm text-gray-800">
              <span className="font-semibold text-primary-700">{entrega.texto}</span>
              <span className="text-gray-600"> · {entrega.diasHabiles} día{entrega.diasHabiles !== 1 ? 's' : ''} hábil{entrega.diasHabiles !== 1 ? 'es' : ''}</span>
            </p>
            {entrega.nota && <p className="text-xs text-amber-700 mt-1">{entrega.nota}</p>}
          </div>
        )}

        {estadoPago && (
          <div className="px-5 py-4">
            <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">Pago</p>
            <p className={`mt-1 text-sm ${estadoPago.tono === 'alerta' ? 'text-amber-800' : 'text-gray-800'}`}>{estadoPago.texto}</p>
          </div>
        )}
      </div>
    </aside>
  )
}

import { formatearPrecio } from '../../utils/catalogoDisplay'
import { textoEntrega, textoVigencia, unidadDe } from '../../utils/planesTienda'

/**
 * Tarjetas de planes de un servicio, como las ve el cliente. Componente de presentación: NO calcula precios, descuentos ni ahorros;
 * todo llega del backend (precioUnitario, precioPaquete, ahorro). Lo usan la vista previa del administrador (TP-04) y la tienda (TP-06).
 *
 * servicio: { categoria, titulo, frasePrincipal, descripcionCorta, incluye[], idealPara, unidadSingular, unidadPlural, diasHabilesEntrega }
 * planes:   [{ unidades, nombre, destacado, vigenciaMeses, precioUnitario, precioPaquete, ahorro, beneficios[] }]
 * Vigencia y cobertura solo se muestran si el interruptor correspondiente está encendido (mostrarVigencia / textoCobertura).
 */
export function EncabezadoServicio({ servicio }) {
  return (
    <div className="mb-4 min-w-0">
      {servicio.categoria && <p className="text-[11px] font-semibold uppercase tracking-wider text-primary-600">{servicio.categoria}</p>}
      <h3 className="text-xl font-extrabold text-gray-900 leading-tight break-words">{servicio.titulo}</h3>
      {servicio.frasePrincipal && <p className="text-sm font-medium text-gray-700 mt-1">{servicio.frasePrincipal}</p>}
      {servicio.descripcionCorta && <p className="text-sm text-gray-500 mt-2">{servicio.descripcionCorta}</p>}
      {servicio.incluye?.length > 0 && (
        <div className="mt-3">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400 mb-1.5">Qué incluye</p>
          <ul className="flex flex-wrap gap-1.5">
            {servicio.incluye.map((c, i) => (
              <li key={i} className="text-xs px-2.5 py-1 rounded-full bg-gray-100 border border-gray-200 text-gray-700">{c}</li>
            ))}
          </ul>
        </div>
      )}
      {servicio.idealPara && (
        <p className="text-sm text-gray-600 mt-3"><span className="font-semibold text-gray-700">Ideal para:</span> {servicio.idealPara}</p>
      )}
    </div>
  )
}

export function TarjetaPlan({ plan, servicio, conIva, mostrarVigencia = false, textoCobertura = null, onComprar }) {
  const unidad = unidadDe(servicio, plan.unidades)
  const unidadSingular = servicio.unidadSingular || 'unidad'
  const entrega = textoEntrega(servicio.diasHabilesEntrega)
  const noVence = mostrarVigencia && plan.vigenciaMeses == null
  return (
    <article aria-label={`${plan.nombre}, ${plan.unidades} ${unidad}`}
      className={`relative flex flex-col rounded-2xl border bg-white p-4 min-w-0 ${
        plan.destacado ? 'border-primary-500 ring-2 ring-primary-100 shadow-md' : 'border-gray-200'
      }`}>
      <div className="absolute -top-2.5 left-1/2 -translate-x-1/2 flex gap-1">
        {plan.destacado && (
          <span className="text-[10px] font-bold tracking-wider px-2.5 py-0.5 rounded-full bg-primary-600 text-white whitespace-nowrap">MÁS SOLICITADO</span>
        )}
        {noVence && (
          <span className="text-[10px] font-bold tracking-wider px-2.5 py-0.5 rounded-full bg-emerald-600 text-white whitespace-nowrap">No vencen</span>
        )}
      </div>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-500">{plan.nombre} · {plan.unidades} {unidad}</p>

      <p className="text-3xl font-extrabold text-gray-900 leading-none mt-3">{formatearPrecio(plan.precioUnitario)}</p>
      <p className="text-xs text-gray-600 mt-1">{conIva ? '+ IVA ' : ''}por {unidadSingular}</p>
      <p className="text-xs text-gray-600 mt-1">
        {plan.unidades === 1 ? 'Pago único' : <>Paquete: {formatearPrecio(plan.precioPaquete)}{conIva ? ' + IVA' : ''}</>}
      </p>
      {plan.ahorro > 0 && (
        <span className="inline-block self-start mt-2 text-[11px] font-semibold px-2 py-0.5 rounded-full bg-green-100 text-green-800">
          Ahorra {formatearPrecio(plan.ahorro)}
        </span>
      )}

      <button type="button" onClick={onComprar} disabled={!onComprar}
        className="mt-3 w-full text-sm font-semibold px-3 py-2.5 rounded-xl bg-primary-600 hover:bg-primary-700 disabled:cursor-default text-white transition-colors">
        Comprar aquí →
      </button>

      {plan.beneficios?.length > 0 && (
        <ul className="mt-3 space-y-1.5">
          {plan.beneficios.map((b, i) => (
            <li key={i} className="flex items-start gap-1.5 text-xs text-gray-700">
              <span className="text-primary-600 mt-0.5" aria-hidden="true">•</span><span className="min-w-0 break-words">{b}</span>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-auto pt-3 space-y-1 text-[11px] text-gray-600">
        {entrega && <p>{entrega}</p>}
        {textoCobertura && <p>{textoCobertura}</p>}
        {mostrarVigencia && <p>{textoVigencia(plan.vigenciaMeses)}</p>}
      </div>
    </article>
  )
}

/** Fila con desplazamiento horizontal en celular y cuadrícula desde tablet. La página nunca se desplaza de lado: solo esta fila. */
export default function TarjetasPlanes({ servicio, planes, conIva = false, mostrarVigencia = false, textoCobertura = null, onComprar }) {
  return (
    <div className="min-w-0">
      <EncabezadoServicio servicio={servicio} />
      {planes.length === 0 ? (
        <p className="text-sm text-gray-500 italic">Define el precio de al menos un plan para ver las tarjetas.</p>
      ) : (
        <div className="flex gap-4 overflow-x-auto snap-x snap-mandatory pt-3 pb-2 -mx-1 px-1 md:grid md:grid-cols-2 lg:grid-cols-3 md:overflow-visible">
          {planes.map((p) => (
            <div key={p.unidades} className="min-w-[16rem] w-[78%] sm:w-[60%] flex-shrink-0 snap-start md:w-auto md:min-w-0">
              <TarjetaPlan plan={p} servicio={servicio} conIva={conIva} mostrarVigencia={mostrarVigencia}
                textoCobertura={textoCobertura} onComprar={onComprar ? () => onComprar(p) : undefined} />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

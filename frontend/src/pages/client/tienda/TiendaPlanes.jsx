import { useState } from 'react'
import TarjetasPlanes from '../../../components/tienda/TarjetasPlanes'
import { formatearPrecio } from '../../../utils/catalogoDisplay'
import { nombreCobertura, recargoMinimo, textoCobertura, textoEntrega, tituloDe } from '../../../utils/planesTienda'

/**
 * Tienda por planes (TP-06): tarjeta del listado y página del servicio. Solo presentación: los precios, el paquete, el ahorro y el
 * descuento máximo los calcula el backend (/catalogo/procesos/activos).
 */

/** ¿Se vende en la tienda por planes? Visible y con al menos un plan con precio (lo que implica precio base). */
export const vendibleEnTienda = (s) => s.visibleEnTienda !== false && s.valor != null && (s.planes?.length ?? 0) > 0

function IconoCorazon({ relleno }) {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" fill={relleno ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={2} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
    </svg>
  )
}

/* ─── Tarjeta del listado ─── */
export function TarjetaServicioPlanes({ servicio, conIva, favorito, onToggleFavorito, onVerPlanes }) {
  const dias = textoEntrega(servicio.diasHabilesEntrega)
  const descuento = Number(servicio.descuentoMaximoPorcentaje ?? 0)
  return (
    <div className="flex flex-col rounded-2xl border border-gray-200 bg-white p-5 shadow-sm hover:shadow-md transition-shadow min-w-0">
      <div className="flex items-start justify-between gap-2">
        <h4 className="text-base font-bold text-gray-900 leading-snug break-words min-w-0">{tituloDe(servicio)}</h4>
        <button type="button" onClick={() => onToggleFavorito(servicio.idProceso)} aria-pressed={favorito}
          aria-label={favorito ? `Quitar ${tituloDe(servicio)} de favoritos` : `Agregar ${tituloDe(servicio)} a favoritos`}
          className={`flex-shrink-0 w-8 h-8 rounded-full border flex items-center justify-center ${favorito ? 'border-red-200 bg-red-50 text-red-600' : 'border-gray-200 text-gray-500 hover:text-red-500'}`}>
          <IconoCorazon relleno={favorito} />
        </button>
      </div>
      {servicio.frasePrincipal && <p className="text-sm text-gray-600 mt-1.5">{servicio.frasePrincipal}</p>}
      {dias && <span className="self-start mt-2 text-xs font-medium px-2 py-0.5 rounded-full bg-gray-100 border border-gray-200 text-gray-700">{dias}</span>}

      <p className="text-xs text-gray-600 mt-4">Desde</p>
      <p className="text-3xl font-extrabold text-gray-900 leading-none">
        {formatearPrecio(servicio.valor)}
        {conIva && <span className="text-sm font-semibold text-gray-600"> + IVA</span>}
      </p>
      {descuento > 0 && (
        <span className="self-start mt-2 text-[11px] font-semibold px-2 py-0.5 rounded-full bg-green-100 text-green-800">
          Hasta {descuento} % menos por volumen
        </span>
      )}

      <button type="button" onClick={() => onVerPlanes(servicio)}
        className="mt-4 w-full text-sm font-semibold px-3 py-2.5 rounded-xl bg-primary-600 hover:bg-primary-700 text-white transition-colors">
        Ver planes
      </button>
    </div>
  )
}

/* ─── Selector de cobertura (solo con coberturaPorSede y un servicio con recargo) ─── */
function SelectorCobertura({ cobertura, onChange, hayNacional }) {
  // El paquete nacional solo existe si el backend trae su precio (servicios con recargo de ciudad).
  const opciones = [
    { valor: 'LOCAL', texto: nombreCobertura('LOCAL'), activa: true },
    { valor: 'NACIONAL', texto: nombreCobertura('NACIONAL'), activa: hayNacional },
  ]
  return (
    <fieldset className="mb-4">
      <legend className="text-xs font-semibold uppercase tracking-wider text-gray-600 mb-1.5">Cobertura</legend>
      <div className="inline-flex rounded-xl border border-gray-200 overflow-hidden">
        {opciones.map((o) => (
          <label key={o.valor}
            className={`px-4 py-2 text-sm font-medium border-r last:border-r-0 border-gray-200 ${o.activa ? 'cursor-pointer' : 'cursor-not-allowed opacity-50'} ${
              cobertura === o.valor ? 'bg-primary-600 text-white' : 'bg-white text-gray-700'}`}>
            <input type="radio" name="cobertura" value={o.valor} checked={cobertura === o.valor} disabled={!o.activa}
              onChange={() => onChange(o.valor)} className="sr-only" />
            {o.texto}{!o.activa && ' (no disponible)'}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

/* ─── Página del servicio ─── */
export function PaginaServicioPlanes({ servicio, config, onVolver, onComprar }) {
  const [cobertura, setCobertura] = useState('LOCAL')
  const conRecargo = recargoMinimo(servicio) != null
  const verCobertura = config.coberturaPorSede && conRecargo
  const hayNacional = verCobertura && (servicio.planes ?? []).some((p) => p.precioUnitarioNacional != null)

  // Con el paquete nacional elegido se muestran los precios nacionales que calcula el backend (no se calcula nada aquí).
  const planesMostrados = cobertura === 'NACIONAL' && hayNacional
    ? servicio.planes.map((p) => ({ ...p, precioUnitario: p.precioUnitarioNacional, precioPaquete: p.precioPaqueteNacional, ahorro: 0 }))
    : servicio.planes

  const datos = {
    categoria: servicio.clasificacion,
    titulo: tituloDe(servicio),
    frasePrincipal: servicio.frasePrincipal,
    descripcionCorta: servicio.descripcionCorta || servicio.descripcion,
    incluye: servicio.incluye ?? [],
    idealPara: servicio.idealPara,
    unidadSingular: servicio.unidadSingular,
    unidadPlural: servicio.unidadPlural,
    diasHabilesEntrega: servicio.diasHabilesEntrega,
  }

  return (
    <div className="space-y-4 min-w-0">
      <button type="button" onClick={onVolver}
        className="inline-flex items-center gap-1.5 text-sm text-gray-700 hover:text-gray-900 border border-gray-200 px-3 py-1.5 rounded-lg hover:bg-gray-50 transition-colors">
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
        Volver a los servicios
      </button>
      <div className="rounded-2xl border border-gray-200 bg-white p-5 sm:p-6">
        {verCobertura && <SelectorCobertura cobertura={cobertura} onChange={setCobertura} hayNacional={hayNacional} />}
        <TarjetasPlanes servicio={datos} planes={planesMostrados} conIva={config.cobroConIva}
          mostrarVigencia={config.vigenciaPorPlan}
          textoCobertura={verCobertura ? textoCobertura(servicio, cobertura) : null}
          onComprar={(plan) => onComprar(servicio, plan, cobertura)} />
      </div>
    </div>
  )
}

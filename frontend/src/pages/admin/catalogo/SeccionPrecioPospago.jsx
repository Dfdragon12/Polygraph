import { useEffect, useState } from 'react'
import api from '../../../services/api'
import { formatearPrecio } from '../../../utils/catalogoDisplay'

/**
 * Sección «Precio pospago» del formulario de servicio (TP-11). Casilla de oferta, modo (automático por el plan de 100 o precio propio), valor, margen
 * y vista previa de cómo lo ve el cliente pospago en Nueva solicitud. El margen y la alerta los calcula el backend; aquí solo se muestran.
 */
export default function SeccionPrecioPospago({ form, set, precioPlan100, costoDirecto, conIva }) {
  const propio = form.modoPrecioPospago === 'PROPIO'
  const precio = propio ? form.precioPospagoPropio : precioPlan100
  const [margen, setMargen] = useState(null)

  useEffect(() => {
    const p = Number(precio)
    if (!form.ofrecerPospago || !(p > 0)) { setMargen(null); return undefined }
    let vigente = true
    const t = setTimeout(() => {
      api.get('/catalogo/pospago/margen', { params: { precio: p, costo: costoDirecto !== '' ? Number(costoDirecto) : undefined } })
        .then((r) => { if (vigente) setMargen(r.data) }).catch(() => { if (vigente) setMargen(null) })
    }, 300)
    return () => { vigente = false; clearTimeout(t) }
  }, [precio, costoDirecto, form.ofrecerPospago])

  const sinPrecio = form.ofrecerPospago && !(Number(precio) > 0)

  return (
    <section className="rounded-xl border border-gray-200 overflow-hidden">
      <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Precio pospago</p>
        <p className="text-xs text-gray-500 mt-0.5">Lo que paga un cliente pospago por este servicio. No lo ve ningún cliente prepago.</p>
      </div>
      <div className="p-4 space-y-4">
        <label className="flex items-center gap-2.5 cursor-pointer">
          <input type="checkbox" checked={form.ofrecerPospago} onChange={(e) => set('ofrecerPospago', e.target.checked)}
            className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
          <span className="text-sm font-medium text-gray-800">Ofrecer a clientes pospago</span>
        </label>

        {form.ofrecerPospago && (
          <>
            <fieldset>
              <legend className="text-xs font-semibold text-gray-700 mb-1.5">Precio</legend>
              <div className="space-y-2">
                <label className="flex items-start gap-2 cursor-pointer text-sm text-gray-700">
                  <input type="radio" name="modoPospago" checked={!propio} onChange={() => set('modoPrecioPospago', 'PLAN_100')} className="mt-1" />
                  <span>
                    Automático · plan de 100 {Number(precioPlan100) > 0 ? `(${formatearPrecio(Number(precioPlan100))})` : '(sin precio cargado)'}
                    <span className="block text-xs text-gray-500">Sigue al precio del plan de 100 de la lista prepago; cambia solo para solicitudes nuevas.</span>
                  </span>
                </label>
                <label className="flex items-start gap-2 cursor-pointer text-sm text-gray-700">
                  <input type="radio" name="modoPospago" checked={propio} onChange={() => set('modoPrecioPospago', 'PROPIO')} className="mt-1" />
                  <span className="flex-1">
                    Precio propio
                    {propio && (
                      <input aria-label="Precio pospago propio (sin IVA)" type="number" min={1} step="1" value={form.precioPospagoPropio}
                        onChange={(e) => set('precioPospagoPropio', e.target.value)} placeholder="Sin IVA"
                        className="mt-1 block w-48 border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                    )}
                  </span>
                </label>
              </div>
            </fieldset>

            {sinPrecio && <p role="alert" className="text-sm text-red-700">Este servicio se ofrece en pospago y todavía no tiene precio.</p>}

            {margen?.margenPorcentaje != null && (
              <p role={margen.margenBajo ? 'alert' : undefined}
                className={`text-sm ${margen.margenBajo ? 'font-semibold text-red-700' : 'text-gray-700'}`}>
                Margen pospago: {Number(margen.margenPorcentaje).toLocaleString('es-CO')} %
                {margen.margenBajo ? ` — por debajo del mínimo (${Number(margen.minimo)} %)` : ''}
              </p>
            )}

            {Number(precio) > 0 && (
              <div className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900">
                <p className="text-[11px] font-semibold uppercase tracking-wider text-sky-700 mb-0.5">Así lo ve el cliente pospago en Nueva solicitud</p>
                <p><span className="font-semibold">{formatearPrecio(Number(precio))}</span>{conIva ? ' + IVA' : ''}</p>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  )
}

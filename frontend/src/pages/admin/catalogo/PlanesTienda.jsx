import { useCallback, useEffect, useState } from 'react'
import api from '../../../services/api'

const inputCls = 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'

/** Parámetros numéricos que el administrador ajusta aquí (TP-04). */
const NUMERICOS = [
  { clave: 'ivaPorcentaje', etiqueta: 'IVA (%)' },
  { clave: 'factorPrecioNacional', etiqueta: 'Factor del precio nacional (%)' },
  { clave: 'margenMinimoPorcentaje', etiqueta: 'Margen mínimo de alerta (%)' },
]

const INTERRUPTORES = [
  { clave: 'cobroConIva', etiqueta: 'Cobro con IVA' },
  { clave: 'tiendaPorPlanes', etiqueta: 'Tienda por planes' },
  { clave: 'vigenciaPorPlan', etiqueta: 'Vigencia por plan' },
  { clave: 'coberturaPorSede', etiqueta: 'Cobertura por sede' },
  { clave: 'pospagoPlan100', etiqueta: 'Lista de precios pospago' },
  { clave: 'altaPospagoControlada', etiqueta: 'Alta pospago controlada' },
  { clave: 'moraAutomatica', etiqueta: 'Mora automática' },
]

function mensajeError(err, fallback) {
  return err.response?.data?.mensaje ?? fallback
}

/** Pestaña «Planes de la tienda»: planes globales, IVA, factor nacional e interruptores. Solo el administrador (el backend lo exige). */
export default function PlanesTienda({ onToast }) {
  const [planes, setPlanes] = useState(null)
  const [parametros, setParametros] = useState({})
  const [valores, setValores] = useState({})
  const [guardando, setGuardando] = useState(false)

  const cargar = useCallback(async () => {
    const [p, par] = await Promise.all([api.get('/catalogo/tienda/planes'), api.get('/catalogo/tienda/parametros')])
    setPlanes(p.data.map((x) => ({ ...x, vigenciaMeses: x.vigenciaMeses ?? '' })))
    const mapa = Object.fromEntries(par.data.map((x) => [x.clave, x]))
    setParametros(mapa)
    setValores(Object.fromEntries(par.data.map((x) => [x.clave, x.valor])))
  }, [])

  useEffect(() => { cargar().catch(() => onToast?.('No se pudieron cargar los planes de la tienda', 'error')) }, [cargar, onToast])

  const cambiarPlan = (unidades, campo, valor) => setPlanes((prev) => prev.map((p) => {
    if (p.unidades === unidades) return { ...p, [campo]: valor }
    return campo === 'destacado' && valor ? { ...p, destacado: false } : p   // solo uno destacado
  }))

  const guardarPlanes = async () => {
    setGuardando(true)
    try {
      await api.put('/catalogo/tienda/planes', {
        planes: planes.map((p) => ({
          unidades: p.unidades, nombre: p.nombre, visible: p.visible, destacado: p.destacado,
          vigenciaMeses: p.vigenciaMeses === '' ? null : Number(p.vigenciaMeses),
        })),
      })
      onToast?.('Planes guardados')
      await cargar()
    } catch (err) {
      onToast?.(mensajeError(err, 'No se pudieron guardar los planes'), 'error')
    } finally { setGuardando(false) }
  }

  const guardarParametro = async (clave, valor) => {
    try {
      await api.put(`/catalogo/tienda/parametros/${clave}`, { valor: String(valor) })
      onToast?.('Parámetro guardado')
      await cargar()
    } catch (err) {
      onToast?.(mensajeError(err, 'No se pudo guardar el parámetro'), 'error')
    }
  }

  if (!planes) return <div className="text-center py-16 text-gray-400 text-sm">Cargando…</div>

  return (
    <div className="space-y-6 max-w-4xl">
      <section className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-sm font-semibold text-gray-800">Planes de la tienda</h2>
        <p className="text-xs text-gray-500 mb-4">Valen para todos los servicios. Vigencia vacía = sin vencimiento. Solo un plan puede ser «Más solicitado».</p>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[11px] uppercase tracking-wider text-gray-400">
                <th className="py-1 pr-3 font-semibold">Unidades</th>
                <th className="py-1 pr-3 font-semibold">Nombre</th>
                <th className="py-1 pr-3 font-semibold">Visible</th>
                <th className="py-1 pr-3 font-semibold">Más solicitado</th>
                <th className="py-1 font-semibold">Vigencia (meses)</th>
              </tr>
            </thead>
            <tbody>
              {planes.map((p) => (
                <tr key={p.unidades} className="border-t border-gray-100">
                  <td className="py-2 pr-3 font-medium text-gray-700">{p.unidades}</td>
                  <td className="py-2 pr-3"><input aria-label={`Nombre del plan de ${p.unidades} unidades`} value={p.nombre} maxLength={60}
                    onChange={(e) => cambiarPlan(p.unidades, 'nombre', e.target.value)} className={inputCls} /></td>
                  <td className="py-2 pr-3"><input type="checkbox" aria-label={`Plan de ${p.unidades} visible`} checked={p.visible}
                    onChange={(e) => cambiarPlan(p.unidades, 'visible', e.target.checked)} className="w-4 h-4" /></td>
                  <td className="py-2 pr-3"><input type="checkbox" aria-label={`Plan de ${p.unidades} destacado`} checked={p.destacado}
                    onChange={(e) => cambiarPlan(p.unidades, 'destacado', e.target.checked)} className="w-4 h-4" /></td>
                  <td className="py-2"><input type="number" min={1} max={120} aria-label={`Vigencia del plan de ${p.unidades} unidades en meses`}
                    value={p.vigenciaMeses} placeholder="Sin vencimiento"
                    onChange={(e) => cambiarPlan(p.unidades, 'vigenciaMeses', e.target.value)} className={`${inputCls} w-40`} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="flex justify-end mt-4">
          <button onClick={guardarPlanes} disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
            {guardando ? 'Guardando…' : 'Guardar planes'}
          </button>
        </div>
      </section>

      <section className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-sm font-semibold text-gray-800">IVA y precios</h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-3">
          {NUMERICOS.map((n) => (
            <div key={n.clave}>
              <label htmlFor={`par-${n.clave}`} className="block text-xs font-medium text-gray-600 mb-1">{n.etiqueta}</label>
              <div className="flex gap-2">
                <input id={`par-${n.clave}`} type="number" min={0} step="0.1" value={valores[n.clave] ?? ''}
                  onChange={(e) => setValores((v) => ({ ...v, [n.clave]: e.target.value }))} className={inputCls} />
                <button type="button" onClick={() => guardarParametro(n.clave, valores[n.clave])}
                  disabled={valores[n.clave] === parametros[n.clave]?.valor || valores[n.clave] === ''}
                  className="px-3 text-sm font-medium text-primary-600 disabled:text-gray-300">Guardar</button>
              </div>
              {parametros[n.clave]?.actualizadoPor && (
                <p className="text-[11px] text-gray-400 mt-1">Último cambio: {parametros[n.clave].actualizadoPor}</p>
              )}
            </div>
          ))}
        </div>
      </section>

      <section className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-sm font-semibold text-gray-800">Interruptores</h2>
        <p className="text-xs text-gray-500 mb-3">Enciende cada parte solo cuando esté probada. Cada cambio queda registrado con usuario y fecha.</p>
        <ul className="divide-y divide-gray-100">
          {INTERRUPTORES.map((i) => {
            const activo = String(parametros[i.clave]?.valor).toLowerCase() === 'true'
            return (
              <li key={i.clave} className="py-2 flex items-center justify-between gap-3">
                <span className="text-sm text-gray-700">{i.etiqueta}
                  <span className="block text-[11px] text-gray-400">{parametros[i.clave]?.descripcion}</span></span>
                <button type="button" role="switch" aria-checked={activo} aria-label={i.etiqueta}
                  onClick={() => guardarParametro(i.clave, activo ? 'false' : 'true')}
                  className={`relative w-11 h-6 rounded-full transition-colors ${activo ? 'bg-primary-600' : 'bg-gray-300'}`}>
                  <span className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white transition-transform ${activo ? 'translate-x-5' : ''}`} />
                </button>
              </li>
            )
          })}
        </ul>
      </section>
    </div>
  )
}

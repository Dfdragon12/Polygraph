import { useEffect, useId, useRef, useState } from 'react'
import api from '../../../services/api'
import { formatearPrecio } from '../../../utils/catalogoDisplay'

/**
 * Secciones del formulario de servicio para la tienda (TP-04). Los números (paquete, ahorro, descuentos, margen, alertas) los calcula el
 * backend con /catalogo/tienda/simular-precios; aquí solo se muestran.
 */

export const UNIDADES_PLAN = [1, 5, 20, 100, 200]

const inputCls = 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'

function Seccion({ titulo, descripcion, children }) {
  return (
    <div className="rounded-xl border border-gray-200 overflow-hidden">
      <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">{titulo}</p>
        {descripcion && <p className="text-xs text-gray-400 mt-0.5">{descripcion}</p>}
      </div>
      <div className="p-4 space-y-4">{children}</div>
    </div>
  )
}

function Contador({ valor, max }) {
  const n = valor.length
  return <span className={`text-[11px] ${n > max ? 'text-red-600 font-semibold' : 'text-gray-400'}`}>{n} / {max}</span>
}

function CampoConContador({ etiqueta, valor, max, onChange, filas, placeholder, ayuda }) {
  const id = useId()
  return (
    <div>
      <div className="flex items-baseline justify-between mb-1">
        <label htmlFor={id} className="block text-xs font-medium text-gray-600">{etiqueta}</label>
        <Contador valor={valor} max={max} />
      </div>
      {filas ? (
        <textarea id={id} rows={filas} value={valor} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} className={`${inputCls} resize-none`} />
      ) : (
        <input id={id} value={valor} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} className={inputCls} />
      )}
      {ayuda && <p className="text-[11px] text-gray-400 mt-1">{ayuda}</p>}
    </div>
  )
}

/* ─── 1. Textos para el cliente ─── */
export function TextosCliente({ form, set }) {
  const lineasIncluye = form.incluye.split('\n').filter((l) => l.trim()).length
  return (
    <Seccion titulo="Textos para el cliente" descripcion="Lo que ve el cliente en la tienda. Si el título comercial está vacío se usa el nombre del servicio.">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <CampoConContador etiqueta="Título comercial" valor={form.tituloComercial} max={120} onChange={(v) => set('tituloComercial', v)}
          placeholder="Ej: Consulta de antecedentes" />
        <CampoConContador etiqueta="Frase principal" valor={form.frasePrincipal} max={160} onChange={(v) => set('frasePrincipal', v)}
          placeholder="Ej: Antes de firmar, sepa con quién está firmando." />
      </div>
      <CampoConContador etiqueta="Qué es" valor={form.descripcionCorta} max={400} filas={2} onChange={(v) => set('descripcionCorta', v)} />
      <CampoConContador etiqueta="Qué incluye" valor={form.incluye} max={600} filas={4} onChange={(v) => set('incluye', v)}
        placeholder={'Uno por línea. Ej:\nRegistros penales\nProcuraduría'}
        ayuda={`${lineasIncluye} de 6 líneas (cada una hasta 80 caracteres).`} />
      <CampoConContador etiqueta="Ideal para" valor={form.idealPara} max={200} onChange={(v) => set('idealPara', v)} />
      <div className="grid grid-cols-2 gap-4">
        <CampoConContador etiqueta="Unidad (singular)" valor={form.unidadSingular} max={30} onChange={(v) => set('unidadSingular', v)} placeholder="consulta" />
        <CampoConContador etiqueta="Unidad (plural)" valor={form.unidadPlural} max={30} onChange={(v) => set('unidadPlural', v)} placeholder="consultas" />
      </div>
    </Seccion>
  )
}

/* ─── 2. Beneficios por plan ─── */
export function BeneficiosPorPlan({ beneficios, onChange }) {
  const cambiar = (i, campo, valor) => onChange(beneficios.map((b, k) => (k === i ? { ...b, [campo]: valor } : b)))
  const mover = (i, d) => {
    const j = i + d
    if (j < 0 || j >= beneficios.length) return
    const copia = [...beneficios]
    ;[copia[i], copia[j]] = [copia[j], copia[i]]
    onChange(copia)
  }
  return (
    <Seccion titulo="Beneficios por plan" descripcion="Un beneficio aparece en todos los planes con «Desde N unidades» o más. El orden de la lista es el que verá el cliente.">
      {beneficios.length === 0 && <p className="text-xs text-gray-400">Todavía no hay beneficios.</p>}
      {beneficios.map((b, i) => (
        <div key={i} className="flex flex-wrap items-center gap-2">
          <input aria-label={`Texto del beneficio ${i + 1}`} value={b.texto} maxLength={120} placeholder="Ej: Gestor dedicado"
            onChange={(e) => cambiar(i, 'texto', e.target.value)} className={`${inputCls} flex-1 min-w-[12rem]`} />
          <select aria-label={`Desde cuántas unidades aplica el beneficio ${i + 1}`} value={b.desdeUnidades}
            onChange={(e) => cambiar(i, 'desdeUnidades', Number(e.target.value))}
            className="border border-gray-200 rounded-lg px-2 py-2 text-sm bg-white">
            {UNIDADES_PLAN.map((u) => <option key={u} value={u}>Desde {u} und.</option>)}
          </select>
          <button type="button" aria-label="Subir" onClick={() => mover(i, -1)} className="px-2 text-gray-400 hover:text-gray-700">↑</button>
          <button type="button" aria-label="Bajar" onClick={() => mover(i, 1)} className="px-2 text-gray-400 hover:text-gray-700">↓</button>
          <button type="button" aria-label="Quitar beneficio" onClick={() => onChange(beneficios.filter((_, k) => k !== i))}
            className="px-2 text-red-400 hover:text-red-600">×</button>
        </div>
      ))}
      <button type="button" onClick={() => onChange([...beneficios, { texto: '', desdeUnidades: 1 }])}
        className="text-sm font-medium text-primary-600 hover:text-primary-700">+ Agregar beneficio</button>
    </Seccion>
  )
}

/* ─── 3. Precios por plan (con análisis del backend) ─── */

/** Pide al backend el análisis de los precios que el administrador está escribiendo (sin guardar). Devuelve null mientras carga. */
export function useAnalisisPrecios(precios, costoDirecto) {
  const [analisis, setAnalisis] = useState(null)
  const token = useRef(0)
  useEffect(() => {
    const mio = ++token.current
    const t = setTimeout(async () => {
      try {
        const { data } = await api.post('/catalogo/tienda/simular-precios', {
          precios: UNIDADES_PLAN.map((u) => ({ unidades: u, precioUnitario: precios[u] !== '' && precios[u] != null ? Number(precios[u]) : null })),
          costoDirecto: costoDirecto !== '' && costoDirecto != null ? Number(costoDirecto) : null,
        })
        if (token.current === mio) setAnalisis(data)
      } catch { /* se conserva el último análisis válido */ }
    }, 250)
    return () => clearTimeout(t)
  }, [precios, costoDirecto])
  return analisis
}

const pct = (v) => (v == null ? '—' : `${Number(v)} %`)

export function PreciosPorPlan({ precios, onPrecio, costoDirecto, onCosto, analisis, referenciaInterna }) {
  const idCosto = useId()
  const hayError = analisis && !analisis.valido
  return (
    <Seccion titulo="Precios por plan" descripcion="Precio por unidad SIN IVA. Cada plan debe costar menos por unidad que el plan anterior; un plan sin precio no se vende para este servicio.">
      {hayError && (
        <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg">{analisis.errorEscalera}</div>
      )}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-[11px] uppercase tracking-wider text-gray-400">
              <th className="py-1 pr-2 font-semibold">Plan</th>
              <th className="py-1 pr-2 font-semibold">Precio por unidad</th>
              <th className="py-1 pr-2 font-semibold">Paquete</th>
              <th className="py-1 pr-2 font-semibold">Vs. anterior</th>
              <th className="py-1 pr-2 font-semibold">Total</th>
              <th className="py-1 font-semibold">Margen</th>
            </tr>
          </thead>
          <tbody>
            {(analisis?.planes ?? UNIDADES_PLAN.map((u) => ({ unidades: u, nombre: `${u} und.`, alertas: [] }))).map((f) => (
              <tr key={f.unidades} className="border-t border-gray-100 align-top">
                <td className="py-2 pr-2 whitespace-nowrap">
                  <span className="font-medium text-gray-800">{f.nombre}</span>
                  <span className="block text-[11px] text-gray-400">{f.unidades} und.</span>
                </td>
                <td className="py-2 pr-2">
                  <input type="number" min={0} step="1" aria-label={`Precio por unidad del plan de ${f.unidades}`}
                    value={precios[f.unidades] ?? ''} onChange={(e) => onPrecio(f.unidades, e.target.value)}
                    className={`w-32 border rounded-lg px-2 py-1.5 text-sm ${f.alertas?.some((a) => a.startsWith('No baja')) ? 'border-red-400 bg-red-50' : 'border-gray-200'}`} />
                </td>
                <td className="py-2 pr-2 whitespace-nowrap text-gray-700">{f.precioPaquete != null ? formatearPrecio(f.precioPaquete) : '—'}</td>
                <td className="py-2 pr-2 whitespace-nowrap text-gray-600">{pct(f.descuentoVsAnterior)}</td>
                <td className="py-2 pr-2 whitespace-nowrap text-gray-600">{pct(f.descuentoTotal)}</td>
                <td className={`py-2 whitespace-nowrap font-medium ${f.alertas?.some((a) => a.startsWith('Margen')) ? 'text-red-600' : 'text-gray-700'}`}>
                  {pct(f.margenPorcentaje)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {analisis?.planes?.some((f) => f.alertas.length > 0) && (
        <ul className="space-y-1">
          {analisis.planes.flatMap((f) => f.alertas.map((a) => (
            <li key={`${f.unidades}-${a}`} className="text-xs text-red-600">⚠ {f.nombre}: {a}</li>
          )))}
        </ul>
      )}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
        <div>
          <label htmlFor={idCosto} className="block text-xs font-medium text-gray-600 mb-1">Costo directo por unidad (COP)</label>
          <input id={idCosto} type="number" min={0} step="1" value={costoDirecto} onChange={(e) => onCosto(e.target.value)} className={inputCls} placeholder="Solo lo ve el administrador" />
          <p className="text-[11px] text-gray-400 mt-1">Con costo directo se calcula el margen bruto. Nunca se muestra al cliente.</p>
        </div>
        {referenciaInterna != null && (
          <div className="bg-gray-50 border border-gray-200 rounded-lg px-3 py-2">
            <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400">Referencia interna</p>
            <p className="text-base font-bold text-gray-700">{formatearPrecio(referenciaInterna.valor)}</p>
            <p className="text-[11px] text-gray-400">Suma de los {referenciaInterna.total} subproceso{referenciaInterna.total !== 1 ? 's' : ''} habilitados. No es el precio que se cobra; sirve de guía para el costo directo.</p>
          </div>
        )}
      </div>
    </Seccion>
  )
}

/* ─── 8. Historial de cambios de precio ─── */
export function HistorialPrecios({ idProceso }) {
  const [abierto, setAbierto] = useState(false)
  const [filas, setFilas] = useState(null)
  useEffect(() => {
    if (!abierto || filas) return
    api.get(`/catalogo/procesos/${idProceso}/historial-precios`).then((r) => setFilas(r.data)).catch(() => setFilas([]))
  }, [abierto, filas, idProceso])
  return (
    <div className="rounded-xl border border-gray-200">
      <button type="button" onClick={() => setAbierto((v) => !v)} aria-expanded={abierto}
        className="w-full flex items-center justify-between px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
        Historial de cambios de precio <span>{abierto ? '−' : '+'}</span>
      </button>
      {abierto && (
        <div className="px-4 pb-4">
          {!filas ? <p className="text-xs text-gray-400">Cargando…</p> : filas.length === 0 ? (
            <p className="text-xs text-gray-400">Todavía no hay cambios registrados.</p>
          ) : (
            <ul className="divide-y divide-gray-100 text-xs">
              {filas.map((h, i) => (
                <li key={i} className="py-1.5 flex flex-wrap gap-x-3 text-gray-600">
                  <span className="text-gray-400">{new Date(h.fecha).toLocaleString('es-CO')}</span>
                  <span className="font-medium text-gray-800">{h.campo}</span>
                  <span>{h.valorAnterior ?? '—'} → {h.valorNuevo ?? '—'}</span>
                  <span className="text-gray-400">{h.usuario}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

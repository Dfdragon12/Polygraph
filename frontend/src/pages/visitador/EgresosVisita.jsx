import { useEffect, useState } from 'react'
import CampoDinero from './CampoDinero'
import { leerAportantes, textoAportantes, soloDigitos, formatoMoneda } from './calculosVisita'

// VD-05 · Egresos del hogar: por cada egreso, concepto, monto total y filas de aportantes (integrante + monto) en vez de texto libre. Lo que se guarda en
// `eg_aportantes` es el mismo texto que ya se imprimía en el Word («Madre $450.000; Candidato $450.000»), armado aquí.

const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'

export default function EgresosVisita({ seccion, integrantes, soloLectura, onCelda, onAgregar }) {
  const col = (clave) => seccion.columnas.find(c => c.clave === clave)
  const val = (f, clave) => { const c = col(clave); return c ? (f.valores[c.idColumna] ?? '') : '' }
  const poner = (f, clave, v) => { const c = col(clave); if (c) onCelda(seccion.idSeccion, f.localId, c.idColumna, v) }

  return (
    <div>
      {seccion.filas.length === 0 && <p className="text-gray-700 mb-2">Sin filas.</p>}
      <div className="space-y-3">
        {seccion.filas.map((f, n) => {
          const total = Number(soloDigitos(val(f, 'eg_monto'))) || 0
          return (
            <div key={f.localId} className="rounded border border-gray-300 p-3 space-y-3">
              <span className="sr-only">Egreso {n + 1}</span>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label htmlFor={`eg-c-${f.localId}`} className="block text-xs font-semibold text-gray-700 mb-1">Concepto</label>
                  <input id={`eg-c-${f.localId}`} className={inputCls} value={val(f, 'eg_concepto')} disabled={soloLectura} onChange={e => poner(f, 'eg_concepto', e.target.value)} />
                </div>
                <div>
                  <label htmlFor={`eg-m-${f.localId}`} className="block text-xs font-semibold text-gray-700 mb-1">Monto total</label>
                  <CampoDinero id={`eg-m-${f.localId}`} value={val(f, 'eg_monto')} disabled={soloLectura} onChange={v => poner(f, 'eg_monto', v)} />
                </div>
              </div>
              <Aportantes n={n} texto={val(f, 'eg_aportantes')} total={total} integrantes={integrantes} soloLectura={soloLectura} onTexto={t => poner(f, 'eg_aportantes', t)} />
            </div>
          )
        })}
      </div>
      {!soloLectura && (
        <button type="button" onClick={() => onAgregar(seccion.idSeccion)}
          className="mt-3 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar</button>
      )}
    </div>
  )
}

// Las filas de aportantes viven aquí mientras se editan (una fila nueva aún vacía no tiene texto que guardar); el texto impreso se actualiza en cada cambio.
function Aportantes({ n, texto, total, integrantes, soloLectura, onTexto }) {
  const [lista, setLista] = useState(() => leerAportantes(texto))
  useEffect(() => { if (texto !== textoAportantes(lista)) setLista(leerAportantes(texto)) }, [texto]) // eslint-disable-line react-hooks/exhaustive-deps
  const cambiar = (nueva) => { setLista(nueva); onTexto(textoAportantes(nueva)) }
  const suma = lista.reduce((a, x) => a + (Number(x.monto) || 0), 0)
  const desigual = lista.length > 0 && total > 0 && suma !== total
  return (
    <fieldset>
      <legend className="text-xs font-semibold text-gray-700 mb-1">Quién lo paga y cuánto aporta cada uno</legend>
      <div className="space-y-2">
        {lista.map((a, i) => (
          <div key={i} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] gap-2 items-end">
            <select aria-label={`Egreso ${n + 1}, aportante ${i + 1}: integrante`} className={inputCls} value={a.integrante} disabled={soloLectura}
              onChange={e => cambiar(lista.map((x, j) => (j === i ? { ...x, integrante: e.target.value } : x)))}>
              <option value="">Selecciona…</option>
              {[...new Set([...integrantes, a.integrante].filter(Boolean))].map(o => <option key={o} value={o}>{o}</option>)}
            </select>
            <CampoDinero ariaLabel={`Egreso ${n + 1}, aportante ${i + 1}: monto`} value={a.monto} disabled={soloLectura}
              onChange={v => cambiar(lista.map((x, j) => (j === i ? { ...x, monto: v } : x)))} />
            {!soloLectura && (
              <button type="button" aria-label={`Quitar aportante ${i + 1}`} onClick={() => cambiar(lista.filter((_, j) => j !== i))}
                className="min-h-11 min-w-11 rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">✕</button>
            )}
          </div>
        ))}
      </div>
      {!soloLectura && (
        <button type="button" onClick={() => cambiar([...lista, { integrante: '', monto: '' }])}
          className="mt-2 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar aportante</button>
      )}
      {desigual && (
        <p role="alert" className="mt-2 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2">
          Los aportes suman {formatoMoneda(suma)} y el monto total es {formatoMoneda(total)}. Revisa que coincidan.
        </p>
      )}
    </fieldset>
  )
}

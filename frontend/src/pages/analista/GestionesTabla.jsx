import CampoFecha from '../../components/CampoFecha'

// Filas de la tabla «gestiones» (fecha, medio, a quién, resultado). La usan laboral (filtradas por vinculación) y académica. Las filas ya guardadas no se pueden quitar.
const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'

export default function GestionesTabla({ seccion, filas, soloLectura, onCelda, onQuitar }) {
  const col = (clave) => seccion?.columnas.find(c => c.clave === clave)
  if (!seccion) return null
  return (
    <div className="space-y-2">
      {filas.map(g => {
        const id = (c) => col(c)?.idColumna
        const pone = (c, x) => onCelda(seccion.idSeccion, g.localId, id(c), x)
        return (
          <div key={g.localId} className="grid grid-cols-1 sm:grid-cols-[9rem_10rem_minmax(0,1fr)_minmax(0,1fr)_auto] gap-2 items-start border border-gray-200 rounded p-2">
            <CampoFecha aria-label="Fecha de la gestión" value={g.valores[id('g_fecha')] ?? ''} disabled={soloLectura} onChange={x => pone('g_fecha', x)} />
            <select aria-label="Medio" className={inputCls} value={g.valores[id('g_medio')] ?? ''} disabled={soloLectura} onChange={e => pone('g_medio', e.target.value)}>
              <option value="">Selecciona…</option>
              {(col('g_medio')?.opciones ?? []).filter(o => o && o !== '—').map(o => <option key={o} value={o}>{o}</option>)}
            </select>
            <input aria-label="Destino (a quién se contactó)" placeholder="A quién" className={inputCls} value={g.valores[id('g_destino')] ?? ''} disabled={soloLectura} onChange={e => pone('g_destino', e.target.value)} />
            <input aria-label="Resultado" placeholder="Resultado" className={inputCls} value={g.valores[id('g_resultado')] ?? ''} disabled={soloLectura} onChange={e => pone('g_resultado', e.target.value)} />
            {!soloLectura && g.idFila == null && <button type="button" aria-label="Quitar la gestión" className="min-h-11 min-w-11 rounded-lg border border-gray-300" onClick={() => onQuitar(seccion.idSeccion, g.localId)}>✕</button>}
          </div>
        )
      })}
    </div>
  )
}

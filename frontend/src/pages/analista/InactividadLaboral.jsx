import CampoFecha from '../../components/CampoFecha'
import AreaTexto from './AreaTexto'
import { CORROBORACIONES, isoAFecha, diasEntre, fechaATexto } from './reglasLaboral'

// LB-04 · «7. Periodos de inactividad laboral» como la tabla del Word. Los periodos de más de 30 días sin vinculación los calcula el sistema con las fechas CONFIRMADAS de las
// vinculaciones (Desde, Hasta y Días son de solo lectura); el analista escribe la actividad y elige cómo se corroboró. Un periodo manual cubre casos como «nunca trabajó
// formalmente». LAB-13 y LAB-14 los genera el servidor desde la corroboración, una sola vez por estudio.

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const NOTA_SIN_PERIODOS = 'No se identificaron periodos de inactividad superiores a 30 días en el periodo validado.'

export default function InactividadLaboral({ secciones, soloLectura, onCelda, onAgregar, onQuitar, onCampo }) {
  const si = secciones.find(s => s.clave === 'inactividad')
  const so = secciones.find(s => s.clave === 'obs_inactividad')
  const sa = secciones.find(s => s.clave === 'alcance')
  if (!si) return <p className="text-gray-700">Este formato no tiene la sección de inactividad.</p>
  const col = (clave) => si.columnas.find(c => c.clave === clave)
  const val = (fila, clave) => fila.valores[col(clave)?.idColumna] ?? ''
  const poner = (fila, clave, x) => { const c = col(clave); if (c) onCelda(si.idSeccion, fila.localId, c.idColumna, x) }
  const visibles = si.filas.filter(f => val(f, 'in_origen') !== 'Descartado')
  const notaCalculada = sa ? (sa.valores[sa.columnas.find(c => c.clave === 'inactividad_nota')?.idColumna] || NOTA_SIN_PERIODOS) : NOTA_SIN_PERIODOS
  const colObs = so?.columnas.find(c => c.clave === 'inactividad_obs')

  const agregarManual = () => {
    const ids = Object.fromEntries(['in_origen', 'in_aviso'].map(k => [col(k)?.idColumna, k === 'in_origen' ? 'Manual' : '']))
    onAgregar(si.idSeccion, ids)
  }
  const cambiarFecha = (fila, clave, iso) => {
    poner(fila, clave, iso)
    const d = isoAFecha(clave === 'in_desde' ? iso : val(fila, 'in_desde'))
    const h = isoAFecha(clave === 'in_hasta' ? iso : val(fila, 'in_hasta'))
    poner(fila, 'in_dias', d && h && h >= d ? String(diasEntre(d, h)) : '')
  }

  return (
    <div className="space-y-6">
      <section aria-labelledby="lb-inact">
        <h3 id="lb-inact" className="text-sm font-bold text-gray-900 mb-2">7. Periodos de inactividad laboral</h3>
        <p className="text-xs text-gray-700 mb-2">Periodos de más de 30 días sin vinculación dentro del periodo validado, calculados con las fechas confirmadas de las vinculaciones. Se recalculan solos al cambiar fechas o vinculaciones.</p>

        {visibles.length === 0 && <p className="text-sm text-gray-900 border border-gray-300 rounded p-3 bg-gray-50" data-clave="inactividad_nota">{notaCalculada}</p>}

        <div className="space-y-3">
          {visibles.map((f, n) => {
            const manual = val(f, 'in_origen') === 'Manual'
            const aviso = val(f, 'in_aviso')
            const corrob = val(f, 'in_corroboracion')
            return (
              <div key={f.localId} className="border border-gray-300 rounded" data-clave={`inactividad-${n + 1}`}>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-0 border-b border-gray-200">
                  {[['Desde', 'in_desde'], ['Hasta', 'in_hasta']].map(([t, k]) => (
                    <div key={k} className="px-2 py-2 sm:border-r border-gray-200">
                      <p className="text-xs font-semibold text-gray-800 mb-1">{t}</p>
                      {manual
                        ? <CampoFecha aria-label={`${t} del periodo ${n + 1}`} value={val(f, k)} disabled={soloLectura} onChange={x => cambiarFecha(f, k, x)} />
                        : <p className="bg-gray-100 rounded px-2 py-2 text-sm text-gray-900">{tiene(val(f, k)) ? fechaATexto(isoAFecha(val(f, k))) : '—'}</p>}
                    </div>
                  ))}
                  <div className="px-2 py-2">
                    <p className="text-xs font-semibold text-gray-800 mb-1">Días</p>
                    <p className="bg-gray-100 rounded px-2 py-2 text-sm text-gray-900">{tiene(val(f, 'in_dias')) ? val(f, 'in_dias') : '—'}</p>
                  </div>
                </div>
                <div className="px-2 py-2 border-b border-gray-200">
                  <label htmlFor={`act-${f.localId}`} className="block text-xs font-semibold text-gray-800 mb-1">Actividad declarada</label>
                  <AreaTexto id={`act-${f.localId}`} max={col('in_actividad')?.longitudMaxima} value={val(f, 'in_actividad')} disabled={soloLectura} onChange={x => poner(f, 'in_actividad', x)} />
                </div>
                <div className="px-2 py-2" data-clave="in_corroboracion">
                  <p className="text-xs font-semibold text-gray-800 mb-1">Cómo se corroboró</p>
                  <div role="radiogroup" aria-label={`Cómo se corroboró el periodo ${n + 1}`} className="flex flex-wrap gap-2">
                    {CORROBORACIONES.map(o => (
                      <button key={o} type="button" role="radio" aria-checked={corrob === o} disabled={soloLectura} onClick={() => poner(f, 'in_corroboracion', corrob === o ? '' : o)}
                        className={`min-h-11 px-3 text-sm font-medium rounded-lg border ${corrob === o ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-800 hover:bg-gray-50'} disabled:opacity-60`}>{o}</button>
                    ))}
                  </div>
                  {!tiene(corrob) && <p className="text-xs text-red-700 mt-1">Indica cómo se corroboró.</p>}
                </div>
                {tiene(aviso) && (
                  <div className="px-2 py-2 border-t border-gray-200 bg-amber-50 flex flex-wrap items-center gap-2">
                    <p className="text-xs text-amber-900">⚠ {aviso}</p>
                    {!soloLectura && aviso !== 'Calculado con fecha declarada' && (
                      <button type="button" className="text-xs underline text-amber-900 min-h-11" onClick={() => poner(f, 'in_aviso', '')}>Ya lo revisé</button>
                    )}
                  </div>
                )}
                {manual && !soloLectura && f.idFila == null && (
                  <div className="px-2 py-2 border-t border-gray-200"><button type="button" className="min-h-11 px-3 text-sm rounded-lg border border-gray-300" onClick={() => onQuitar(si.idSeccion, f.localId)}>Quitar este periodo</button></div>
                )}
              </div>
            )
          })}
        </div>

        <p className="mt-3 text-xs text-gray-700">
          Solo declaración → <b>LAB-14</b> (10 puntos) · No se pudo corroborar → <b>LAB-13</b> (30 puntos). Cada uno cuenta una sola vez por estudio, aunque haya varios periodos.
        </p>
        {!soloLectura && (
          <div className="mt-2">
            <button type="button" onClick={agregarManual} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar periodo manual</button>
            <p className="text-xs text-gray-600 mt-1">Por ejemplo, cuando el evaluado nunca trabajó formalmente: un solo periodo con todo el lapso.</p>
          </div>
        )}
      </section>

      {colObs && (
        <section aria-labelledby="lb-obs-inact" data-clave="inactividad_obs">
          <h3 id="lb-obs-inact" className="text-sm font-bold text-gray-900 mb-1">Observaciones de la inactividad</h3>
          <p className="text-xs text-gray-700 mb-1">Relato: cómo se solventó, quién confirmó, aportes menores a 30 días.</p>
          <AreaTexto ariaLabelledby="lb-obs-inact" rows={4} max={colObs.longitudMaxima} disabled={soloLectura} value={so.valores[colObs.idColumna] ?? ''}
            onChange={x => onCampo(so.idSeccion, colObs.idColumna, x)} />
        </section>
      )}
    </div>
  )
}

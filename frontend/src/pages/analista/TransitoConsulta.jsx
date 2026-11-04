import { Botones } from '../visitador/DatosGeneralesVisita'
import { ITEMS_SIMIT, leerItem, escribirItem, sugerirLeg17, sugerirRunt } from './transitoFormato'

// AN-04 · Tránsito: SIMIT con botones y número, SIMUR y RUNT como tablas, y «Sin registros» para dejar la tabla completa. Se guarda en las mismas columnas de
// siempre (simit_*, simur_*, runt_*): el Word no cambia.

const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

function Sugerencia({ texto, onUsar }) {
  return (
    <p className="mt-1 text-sm text-gray-800">Sugerido: <b>{texto}</b>{' '}
      <button type="button" onClick={onUsar} className="underline text-primary-700 min-h-11">Usar</button>
    </p>
  )
}

function TarjetaFila({ titulo, children }) {
  return <div className="rounded border border-gray-300 p-3 space-y-3"><p className="text-xs font-bold uppercase tracking-wide text-gray-700">{titulo}</p>{children}</div>
}

export default function TransitoConsulta({ secciones, soloLectura, onCampo, onCelda, onAgregar }) {
  const sec = (clave) => secciones.find(s => s.clave === clave && !s.origenFijoClave)
  const simit = sec('transito_simit'), simur = sec('simur'), runt = sec('runt'), valid = sec('validacion_evaluado')
  const idEn = (s, clave) => s?.columnas.find(c => c.clave === clave)?.idColumna
  const conduccion = valid ? (valid.valores[idEn(valid, 'ev_conduccion')] ?? '') : ''
  const sv = (clave) => (simit ? (simit.valores[idEn(simit, clave)] ?? '') : '')
  const ponerSimit = (clave, v) => { const id = idEn(simit, clave); if (id !== undefined) onCampo(simit.idSeccion, id, v) }
  const refSimit = sv('simit_ref')

  return (
    <div className="space-y-8">
      {conduccion === 'No' && (
        <p role="note" className="rounded border border-blue-200 bg-blue-50 text-blue-900 text-sm px-3 py-2">
          Informativo: el cargo no implica conducción; los resultados de tránsito no suman puntos. Se diligencia igual.
        </p>
      )}

      {simit && (
        <section aria-labelledby="an-simit">
          <h3 id="an-simit" className="text-sm font-bold text-gray-900 mb-2">SIMIT</h3>
          <div className="space-y-3">
            {ITEMS_SIMIT.map(item => {
              const v = leerItem(sv(item.clave), item)
              const cambiar = (nuevo) => ponerSimit(item.clave, escribirItem(item, { ...v, ...nuevo }))
              const conEtiqueta = `Con ${item.etiqueta.toLowerCase()}`
              return (
                <div key={item.clave} data-clave={item.clave} className="rounded border border-gray-200 p-3">
                  <p className="text-xs font-semibold text-gray-700 mb-1">{item.etiqueta}</p>
                  <div className="flex flex-wrap items-center gap-3">
                    <Botones etiqueta={item.etiqueta} opciones={[item.sin, conEtiqueta]} deshabilitado={soloLectura}
                      valor={v.modo === 'sin' ? item.sin : v.modo === 'con' || v.modo === 'otro' ? conEtiqueta : ''}
                      onElegir={o => {
                        if (o === item.sin) ponerSimit(item.clave, item.sin)
                        else if (v.modo === 'sin' || v.modo === 'vacio') ponerSimit(item.clave, escribirItem(item, { modo: 'con', n: '1', estado: item.estados[0] }))
                      }} />
                    {v.modo === 'con' && (
                      <>
                        <label className="text-xs text-gray-700">Número
                          <input type="number" min="1" className={`${inputCls} w-24 ml-2 inline-block`} value={v.n} disabled={soloLectura}
                            onChange={e => cambiar({ modo: 'con', n: e.target.value })} />
                        </label>
                        {item.estados.length > 1
                          ? <Botones etiqueta={`${item.etiqueta}: estado`} opciones={item.estados} valor={v.estado || item.estados[0]} deshabilitado={soloLectura} onElegir={e => cambiar({ modo: 'con', estado: e })} />
                          : <span className="text-sm text-gray-800">{item.estados[0]}</span>}
                      </>
                    )}
                  </div>
                  {v.modo === 'otro' && (
                    <div className="mt-2">
                      <label className="block text-xs text-gray-700 mb-1" htmlFor={`an-t-${item.clave}`}>Texto guardado (escrito antes; edítalo si hace falta)</label>
                      <input id={`an-t-${item.clave}`} className={`${inputCls} max-w-md`} value={v.texto} disabled={soloLectura} onChange={e => ponerSimit(item.clave, e.target.value)} />
                    </div>
                  )}
                </div>
              )
            })}
          </div>
          <div data-clave="simit_ref" className="mt-3 max-w-xs rounded">
            <label htmlFor="an-simit-ref" className="block text-xs font-semibold text-gray-700 mb-1">Ref. (LEG-17)</label>
            <select id="an-simit-ref" className={inputCls} value={refSimit === '—' ? '' : refSimit} disabled={soloLectura} onChange={e => ponerSimit('simit_ref', e.target.value || '—')}>
              <option value="">Sin código</option>
              <option value="LEG-17">LEG-17</option>
            </select>
            {sugerirLeg17(sv('simit_multas'), conduccion) && refSimit !== 'LEG-17' && !soloLectura && (
              <Sugerencia texto="LEG-17 · multas pendientes sin acuerdo de pago" onUsar={() => ponerSimit('simit_ref', 'LEG-17')} />
            )}
          </div>
        </section>
      )}

      {simur && (
        <section aria-labelledby="an-simur">
          <h3 id="an-simur" className="text-sm font-bold text-gray-900 mb-1">SIMUR (complementaria Bogotá)</h3>
          <p className="text-xs text-gray-700 mb-2">Solo para consultas en Bogotá.</p>
          <div className="space-y-3">
            {simur.filas.map((f, i) => {
              const poner = (clave, v) => onCelda(simur.idSeccion, f.localId, idEn(simur, clave), v)
              const val = (clave) => f.valores[idEn(simur, clave)] ?? ''
              return (
                <TarjetaFila key={f.localId} titulo={`Comparendo ${i + 1}`}>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div><label className="block text-xs text-gray-700 mb-1">Comparendo</label><input className={inputCls} value={val('simur_comparendo')} disabled={soloLectura} onChange={e => poner('simur_comparendo', e.target.value)} /></div>
                    <div><label className="block text-xs text-gray-700 mb-1">Placa</label><input className={inputCls} value={val('simur_placa')} disabled={soloLectura} onChange={e => poner('simur_placa', e.target.value)} /></div>
                    <div><label className="block text-xs text-gray-700 mb-1">Fecha de infracción</label><input type="date" className={inputCls} value={val('simur_fecha')} disabled={soloLectura} onChange={e => poner('simur_fecha', e.target.value)} /></div>
                  </div>
                </TarjetaFila>
              )
            })}
          </div>
          {!soloLectura && (
            <div className="mt-3 flex flex-wrap gap-2">
              {simur.filas.length === 0 && (
                <button type="button" onClick={() => onAgregar(simur.idSeccion, { [idEn(simur, 'simur_comparendo')]: 'Sin comparendos', [idEn(simur, 'simur_placa')]: '—' })}
                  className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Sin registros</button>
              )}
              <button type="button" onClick={() => onAgregar(simur.idSeccion)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar comparendo</button>
            </div>
          )}
        </section>
      )}

      {runt && (
        <section aria-labelledby="an-runt">
          <h3 id="an-runt" className="text-sm font-bold text-gray-900 mb-2">RUNT</h3>
          <div className="space-y-3">
            {runt.filas.map((f, i) => {
              const poner = (clave, v) => onCelda(runt.idSeccion, f.localId, idEn(runt, clave), v)
              const val = (clave) => f.valores[idEn(runt, clave)] ?? ''
              const sugerida = sugerirRunt(val('runt_estado'), val('runt_categoria_ok'), conduccion)
              const ref = val('runt_ref')
              return (
                <TarjetaFila key={f.localId} titulo={`Licencia ${i + 1}`}>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div><label className="block text-xs text-gray-700 mb-1">Categoría</label><input className={inputCls} value={val('runt_categoria')} disabled={soloLectura} onChange={e => poner('runt_categoria', e.target.value)} /></div>
                    <div><label className="block text-xs text-gray-700 mb-1">Vencimiento</label><input type="date" className={inputCls} value={val('runt_vencimiento')} disabled={soloLectura} onChange={e => poner('runt_vencimiento', e.target.value)} /></div>
                  </div>
                  <div>
                    <p id={`an-runt-e-${i}`} className="text-xs text-gray-700 mb-1">Estado de la licencia</p>
                    <Botones etiqueta="Estado de la licencia" opciones={['Vigente', 'Vencida', 'Suspendida', 'Cancelada']} valor={val('runt_estado') === '—' ? '' : val('runt_estado')} deshabilitado={soloLectura}
                      onElegir={v => poner('runt_estado', v)} />
                  </div>
                  <div>
                    <p className="text-xs text-gray-700 mb-1">¿Categoría corresponde al cargo?</p>
                    <Botones etiqueta="Categoría corresponde al cargo" opciones={['Sí', 'No']} valor={val('runt_categoria_ok') === '—' ? '' : val('runt_categoria_ok')} deshabilitado={soloLectura}
                      onElegir={v => poner('runt_categoria_ok', v)} />
                  </div>
                  <div className="max-w-xs">
                    <label className="block text-xs text-gray-700 mb-1">Ref. (LEG-10 / LEG-14)</label>
                    <select className={inputCls} value={ref === '—' ? '' : ref} disabled={soloLectura} onChange={e => poner('runt_ref', e.target.value || '—')}>
                      <option value="">Sin código</option><option value="LEG-10">LEG-10</option><option value="LEG-14">LEG-14</option>
                    </select>
                    {sugerida && ref !== sugerida && !soloLectura && <Sugerencia texto={sugerida} onUsar={() => poner('runt_ref', sugerida)} />}
                  </div>
                </TarjetaFila>
              )
            })}
          </div>
          {!soloLectura && (
            <div className="mt-3 flex flex-wrap gap-2">
              {runt.filas.length === 0 && (
                <button type="button" onClick={() => onAgregar(runt.idSeccion, { [idEn(runt, 'runt_categoria')]: 'No inscrito', [idEn(runt, 'runt_estado')]: '—' })}
                  className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Sin registros</button>
              )}
              <button type="button" onClick={() => onAgregar(runt.idSeccion)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar licencia</button>
            </div>
          )}
        </section>
      )}
    </div>
  )
}

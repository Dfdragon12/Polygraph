import { useState } from 'react'
import CampoFecha from '../../components/CampoFecha'
import { InputPorTipo } from '../shared/Formulario'
import { Botones } from '../visitador/DatosGeneralesVisita'
import { CATALOGO_DELITOS, sugerirCodigo, sugerirRelacion, radicadoRepetido } from './procesosReglas'
import { rutasDeSoporte } from './ListasConsulta'

// AN-06 · Procesos judiciales: un solo tema con dos pestañas (Rama Judicial y bases privadas). Cada proceso es una ficha con los campos de la tabla del Word,
// con botones donde antes había listas, el delito buscable en el Anexo 2, la relación con el cargo sugerida y el código sugerido con su razón. Todo se guarda en
// las columnas de siempre (p_*, pp_*); lo nuevo que no se imprime (radicado de bases privadas, soporte, constancias) va en columnas aparte.

const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

export const FUENTES = [
  { id: 'rama', titulo: 'Rama Judicial', seccion: 'procesos', pre: 'p_', revisado: 'procesos_revisados', otra: 'privadas' },
  { id: 'privadas', titulo: 'Bases privadas', seccion: 'procesos_privados', pre: 'pp_', revisado: 'procesos_priv_revisados', otra: 'rama' },
]

function Opciones({ id, etiqueta, opciones, valor, onElegir, deshabilitado }) {
  return (
    <div>
      <p id={id} className="text-xs text-gray-700 mb-1">{etiqueta}</p>
      <Botones id={id} etiqueta={etiqueta} opciones={opciones} valor={valor} deshabilitado={deshabilitado} onElegir={onElegir} />
    </div>
  )
}

export default function ProcesosJudiciales({ secciones, soloLectura, idServicioSubproceso, onCelda, onCampo, onAgregar, onError }) {
  const [pestana, setPestana] = useState('rama')
  const sec = (clave) => secciones.find(s => s.clave === clave && !s.origenFijoClave)
  const idEn = (s, clave) => s?.columnas.find(c => c.clave === clave)?.idColumna
  const notas = sec('notas_procesos')
  const valid = sec('validacion_evaluado')
  const tipoDeCargo = valid ? (valid.valores[idEn(valid, 'ev_tipo_cargo')] ?? '') : ''
  const revisado = (f) => (notas ? notas.valores[idEn(notas, f.revisado)] === 'Sí' : false)
  const ponerRevisado = (f) => { const id = idEn(notas, f.revisado); if (id !== undefined) onCampo(notas.idSeccion, id, 'Sí') }

  const rama = sec('procesos'), privadas = sec('procesos_privados')
  const radicadosRama = rama ? rama.filas.map(f => f.valores[idEn(rama, 'p_radicado')] ?? '') : []

  const fuente = FUENTES.find(f => f.id === pestana)
  const s = sec(fuente.seccion)
  if (!s) return <p className="text-gray-700">Esta plantilla no tiene la sección de procesos.</p>
  const pre = fuente.pre
  const col = (clave) => s.columnas.find(c => c.clave === clave)
  const opcionesDe = (clave) => (col(clave)?.opciones ?? []).filter(o => o && o !== '—')

  return (
    <div className="space-y-4">
      <div role="tablist" aria-label="Fuente de los procesos" className="flex flex-wrap gap-2">
        {FUENTES.map(f => {
          const n = (sec(f.seccion)?.filas ?? []).length
          const activo = f.id === pestana
          return (
            <button key={f.id} type="button" role="tab" aria-selected={activo} onClick={() => setPestana(f.id)}
              className={`min-h-11 px-4 text-sm font-medium rounded-lg border ${activo ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-800 hover:bg-gray-50'}`}>
              {f.titulo} · {n}
            </button>
          )
        })}
      </div>

      {s.filas.length === 0 && (
        revisado(fuente)
          ? <p role="status" className="text-sm text-green-800">✓ Revisado: no se encontraron procesos en {fuente.titulo}.</p>
          : <p className="text-gray-700">Aún no hay procesos registrados en {fuente.titulo}.</p>
      )}

      <div className="space-y-4" data-clave={`seccion-${fuente.seccion}`}>
        {s.filas.map((f, i) => {
          const val = (clave) => f.valores[idEn(s, clave)] ?? ''
          const poner = (clave, v) => onCelda(s.idSeccion, f.localId, idEn(s, clave), v)
          const tipo = val(`${pre}tipo`)
          const penal = tipo === 'Penal'
          const relacionSugerida = penal ? sugerirRelacion(tipoDeCargo, val(`${pre}delito`)) : null
          const duplicado = fuente.id === 'privadas' && radicadoRepetido(val('pp_radicado'), radicadosRama)
          const indiceRama = duplicado ? radicadosRama.findIndex(r => radicadoRepetido(val('pp_radicado'), [r])) + 1 : 0
          const sug = duplicado
            ? { codigo: '—', razon: `es el mismo proceso que el N.º ${indiceRama} de Rama Judicial: no se cuenta dos veces` }
            : sugerirCodigo({ tipo, calidad: val(`${pre}calidad`), estado: val(`${pre}estado`), motivo: val(`${pre}motivo`), relacion: penal ? val(`${pre}relacion`) : '' })
          const calificacion = val(`${pre}calificacion`)
          const usarSugerencia = () => {
            poner(`${pre}calificacion`, sug.codigo)
            if (sug.codigo === '—') {
              const donde = fuente.id === 'rama' ? 'p_justificacion' : 'pp_obs'
              if (!tiene(val(donde))) poner(donde, duplicado ? `Mismo proceso que el N.º ${indiceRama} de Rama Judicial.` : `No suma: ${sug.razon}.`)
            }
          }
          const soporte = val(`${pre}soporte`)
          const hayArchivo = rutasDeSoporte(soporte).length > 0
          const base = `an-${fuente.id}-${f.localId}`
          return (
            <div key={f.localId} className="rounded-lg border border-gray-300 p-4 space-y-4" data-clave={`${pre}proceso-${i + 1}`}>
              <h4 className="text-sm font-bold text-primary-800">Proceso N.º {i + 1}</h4>

              {duplicado && (
                <p role="alert" className="rounded border border-amber-300 bg-amber-50 text-amber-900 text-sm px-3 py-2">Este proceso ya está en Rama Judicial (N.º {indiceRama}).</p>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label htmlFor={`${base}-rad`} className="block text-xs text-gray-700 mb-1">Radicado{fuente.id === 'privadas' ? ' (no se imprime)' : ''}</label>
                  <input id={`${base}-rad`} className={inputCls} value={val(`${pre}radicado`)} disabled={soloLectura} onChange={e => poner(`${pre}radicado`, e.target.value)} />
                </div>
                <div>
                  <label htmlFor={`${base}-des`} className="block text-xs text-gray-700 mb-1">Despacho</label>
                  <input id={`${base}-des`} className={inputCls} value={val(`${pre}despacho`)} disabled={soloLectura} onChange={e => poner(`${pre}despacho`, e.target.value)} />
                </div>
              </div>

              <Opciones id={`${base}-tipo`} etiqueta="Tipo de proceso" opciones={opcionesDe(`${pre}tipo`)} valor={tipo} deshabilitado={soloLectura}
                onElegir={v => poner(`${pre}tipo`, v)} />
              <Opciones id={`${base}-cal`} etiqueta="Calidad del evaluado" opciones={opcionesDe(`${pre}calidad`)} valor={val(`${pre}calidad`)} deshabilitado={soloLectura}
                onElegir={v => poner(`${pre}calidad`, v)} />

              <div>
                <label htmlFor={`${base}-del`} className="block text-xs text-gray-700 mb-1">{penal ? 'Delito (busca por nombre o artículo)' : 'Asunto'}</label>
                <input id={`${base}-del`} className={inputCls} value={val(`${pre}delito`)} disabled={soloLectura} list={penal ? 'catalogo-delitos' : undefined}
                  onChange={e => poner(`${pre}delito`, e.target.value)} />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label htmlFor={`${base}-fec`} className="block text-xs text-gray-700 mb-1">Fecha del proceso</label>
                  <CampoFecha id={`${base}-fec`} className={inputCls} value={val(`${pre}fecha`)} disabled={soloLectura} onChange={v => poner(`${pre}fecha`, v)} />
                </div>
                <div>
                  <label htmlFor={`${base}-eta`} className="block text-xs text-gray-700 mb-1">Etapa</label>
                  <input id={`${base}-eta`} className={inputCls} value={val(`${pre}etapa`)} disabled={soloLectura} onChange={e => poner(`${pre}etapa`, e.target.value)} />
                </div>
              </div>

              <Opciones id={`${base}-est`} etiqueta="Estado" opciones={opcionesDe(`${pre}estado`)} valor={val(`${pre}estado`)} deshabilitado={soloLectura}
                onElegir={v => poner(`${pre}estado`, v)} />
              <Opciones id={`${base}-mot`} etiqueta={fuente.id === 'rama' ? 'Forma de terminación' : 'Forma de terminación (no se imprime)'} opciones={opcionesDe(`${pre}motivo`)}
                valor={val(`${pre}motivo`) === '—' ? '' : val(`${pre}motivo`)} deshabilitado={soloLectura} onElegir={v => poner(`${pre}motivo`, val(`${pre}motivo`) === v ? '—' : v)} />

              {/* Relación con el cargo */}
              <div data-clave={`${pre}relacion`} className="rounded">
                <p className="text-xs text-gray-700 mb-1">Relación con el cargo</p>
                {penal ? (
                  <>
                    <Botones etiqueta="Relación con el cargo" opciones={['Sí', 'No']} valor={val(`${pre}relacion`)} deshabilitado={soloLectura} onElegir={v => poner(`${pre}relacion`, v)} />
                    {relacionSugerida && val(`${pre}relacion`) !== relacionSugerida && !soloLectura && (
                      <p className="mt-1 text-sm text-gray-800">Sugerido por el Anexo 2 ({tipoDeCargo}): <b>{relacionSugerida}</b>{' '}
                        <button type="button" onClick={() => poner(`${pre}relacion`, relacionSugerida)} className="underline text-primary-700 min-h-11">Usar</button></p>
                    )}
                    {!relacionSugerida && !tiene(tipoDeCargo) && <p className="mt-1 text-xs text-amber-800">Elige el tipo de cargo en «Datos del evaluado» para recibir una sugerencia.</p>}
                  </>
                ) : <p className="text-sm text-gray-800">{tiene(tipo) ? 'No aplica' : 'Elige primero el tipo de proceso.'}</p>}
              </div>

              {/* Código */}
              <div data-clave={`${pre}calificacion`} className="rounded border border-purple-200 bg-purple-50/50 p-3 space-y-2">
                <label htmlFor={`${base}-cod`} className="block text-xs font-semibold text-purple-900">Calificación (código)</label>
                <select id={`${base}-cod`} className={inputCls} value={calificacion} disabled={soloLectura} onChange={e => poner(`${pre}calificacion`, e.target.value)}>
                  <option value="">Selecciona…</option>
                  <option value="—">— (no suma)</option>
                  {(col(`${pre}calificacion`)?.opciones ?? []).filter(o => o && o !== '—').map(o => <option key={o} value={o}>{o}</option>)}
                </select>
                {sug && (
                  <p className="text-sm text-gray-900">
                    {sug.codigo
                      ? <>Sugerido: <b>{sug.codigo}</b> · {sug.razon}.{' '}
                        {calificacion !== sug.codigo && !soloLectura && <button type="button" onClick={usarSugerencia} className="underline text-primary-700 min-h-11">Usar</button>}</>
                      : <span className="text-gray-700">{sug.razon}</span>}
                  </p>
                )}
              </div>

              {fuente.id === 'rama' && (
                <div><label htmlFor={`${base}-jus`} className="block text-xs text-gray-700 mb-1">Justificación</label>
                  <textarea id={`${base}-jus`} rows={2} className={`${inputCls} py-2`} value={val('p_justificacion')} disabled={soloLectura} onChange={e => poner('p_justificacion', e.target.value)} /></div>
              )}
              <div><label htmlFor={`${base}-obs`} className="block text-xs text-gray-700 mb-1">Observaciones</label>
                <textarea id={`${base}-obs`} rows={2} className={`${inputCls} py-2`} value={val(`${pre}obs`)} disabled={soloLectura} onChange={e => poner(`${pre}obs`, e.target.value)} /></div>

              <div data-clave={`${pre}soporte`} className="space-y-1">
                <p className="text-xs text-gray-700">Soporte (no se imprime)</p>
                <p className={`text-xs font-semibold ${hayArchivo ? 'text-green-800' : 'text-red-700'}`}>{hayArchivo ? '✓ Soporte cargado' : 'Falta soporte'}</p>
                <InputPorTipo tipoDato="ARCHIVO" value={soporte} onChange={v => poner(`${pre}soporte`, v)} idServicioSubproceso={idServicioSubproceso} onError={onError} disabled={soloLectura} />
              </div>
            </div>
          )
        })}
      </div>

      {!soloLectura && (
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => onAgregar(s.idSeccion)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar proceso</button>
          {s.filas.length === 0 && !revisado(fuente) && (
            <button type="button" onClick={() => ponerRevisado(fuente)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">No se encontraron procesos</button>
          )}
        </div>
      )}
      <datalist id="catalogo-delitos">{CATALOGO_DELITOS.map(d => <option key={d.art} value={d.texto} />)}</datalist>
    </div>
  )
}

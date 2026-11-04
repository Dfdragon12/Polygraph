import { useEffect, useRef } from 'react'
import CampoFecha from '../../components/CampoFecha'
import { Botones } from '../visitador/DatosGeneralesVisita'
import { coincide, comoSiNo } from './identidadFormato'

// AN-05 · Identidad y contacto: alertas de Reconocer / Datacrédito, y direcciones, teléfonos y correos con lo declarado por el evaluado al lado. Se guarda en las mismas
// columnas de siempre (a_*, d_*, t_*, e_*, rec_*): el Word no cambia. «Declarado» y las sugerencias solo se ven en pantalla.

const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const TRATAMIENTO_ALERTA = 'Alerta: requiere revisión del analista. No suma.'

const TABLAS = [
  {
    clave: 'direcciones', titulo: 'Direcciones', tipo: 'direccion', principal: 'd_direccion', coincide: 'd_coincide', unidad: 'dirección',
    campos: [['d_direccion', 'Dirección'], ['d_tipo', 'Tipo'], ['d_ciudad', 'Ciudad'], ['d_desde', 'Reportada desde'], ['d_ultimo', 'Último reporte']],
    declarado: (link) => [link('DIRECCION_RESIDENCIA')],
  },
  {
    clave: 'telefonos', titulo: 'Teléfonos', tipo: 'telefono', principal: 't_numero', coincide: 't_coincide', unidad: 'teléfono',
    campos: [['t_numero', 'Teléfono'], ['t_tipo', 'Tipo (RES – CORR – LAB)'], ['t_ciudad', 'Ciudad'], ['t_ultimo', 'Último reporte']],
    declarado: (link) => [link('CELULAR'), link('TELEFONO_FIJO')],
  },
  {
    clave: 'correos', titulo: 'Correos electrónicos', tipo: 'correo', principal: 'e_correo', coincide: 'e_coincide', unidad: 'correo',
    campos: [['e_correo', 'Correo'], ['e_ultimo', 'Último reporte']],
    declarado: (link) => [link('EMAIL_PRINCIPAL')],
  },
]

export default function IdentidadContacto({ secciones, datosCandidato, soloLectura, onCampo, onCelda, onAgregar }) {
  const sec = (clave) => secciones.find(s => s.clave === clave && !s.origenFijoClave)
  const idEn = (s, clave) => s?.columnas.find(c => c.clave === clave)?.idColumna
  const link = (clave) => (datosCandidato ?? []).find(c => c.clave === clave)?.valor ?? ''
  const lastAuto = useRef({})

  const alertas = sec('alertas_id'), ident = sec('identidad_contacto'), info = sec('informativos')
  const valIdent = (clave) => (ident ? (ident.valores[idEn(ident, clave)] ?? '') : '')
  const ponerIdent = (clave, v) => { const id = idEn(ident, clave); if (id !== undefined) onCampo(ident.idSeccion, id, v) }

  // ¿Qué dicen las tablas respecto de lo declarado?
  const estadoTabla = (cfg) => {
    const s = sec(cfg.clave)
    if (!s) return { filas: 0, hay: null }
    const declarados = cfg.declarado(link).filter(tiene)
    const resultados = s.filas.map(f => coincide(cfg.tipo, declarados, f.valores[idEn(s, cfg.principal)]))
    return { filas: s.filas.length, declarados, ninguno: s.filas.length > 0 && declarados.length > 0 && resultados.every(r => r === false) }
  }
  const estados = TABLAS.map(estadoTabla)

  // «¿La dirección declarada aparece en Reconocer?» la calcula el sistema: Sí si alguna dirección coincide. Editable.
  const dirs = sec('direcciones')
  const firmaDirs = dirs ? dirs.filas.map(f => f.valores[idEn(dirs, 'd_direccion')] ?? '').join('|') : ''
  useEffect(() => {
    if (soloLectura || !ident || !dirs) return
    const declarada = link('DIRECCION_RESIDENCIA')
    const rs = dirs.filas.map(f => coincide('direccion', declarada, f.valores[idEn(dirs, 'd_direccion')]))
    const auto = !tiene(declarada) || dirs.filas.length === 0 ? '' : rs.some(r => r === true) ? 'Sí' : 'No'
    const actual = valIdent('rec_dir_coincide')
    const previo = lastAuto.current.rec ?? ''
    if ((actual === '' || actual === previo) && auto !== actual) ponerIdent('rec_dir_coincide', auto)
    lastAuto.current.rec = auto
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [firmaDirs])

  const cambiarPrincipal = (cfg, s, f, valor) => {
    const declarados = cfg.declarado(link).filter(tiene)
    const antes = f.valores[idEn(s, cfg.principal)] ?? ''
    const auto = comoSiNo(coincide(cfg.tipo, declarados, valor))
    const previo = lastAuto.current[f.localId] ?? comoSiNo(coincide(cfg.tipo, declarados, antes))
    const actual = f.valores[idEn(s, cfg.coincide)] ?? ''
    onCelda(s.idSeccion, f.localId, idEn(s, cfg.principal), valor)
    if (actual === '' || actual === previo) onCelda(s.idSeccion, f.localId, idEn(s, cfg.coincide), auto)
    lastAuto.current[f.localId] = auto
  }

  // ALR-06: lo declarado no aparece en Reconocer. Solo se sugiere; el analista lo registra en «hallazgos sin impacto».
  const noAparecen = TABLAS.filter((_, i) => estados[i].ninguno).map(t => t.unidad)
  const situacionAlr06 = 'Dirección, teléfono o correo declarados no coinciden con Reconocer (ALR-06)'
  const yaRegistrada = !!info && info.filas.some(f => String(f.valores[idEn(info, 'i_situacion')] ?? '').includes('ALR-06'))
  const registrarAlr06 = () => onAgregar(info.idSeccion, {
    [idEn(info, 'i_situacion')]: situacionAlr06,
    [idEn(info, 'i_detalle')]: `No aparece en Reconocer: ${noAparecen.join(', ')} declarado por el evaluado.`,
    [idEn(info, 'i_tratamiento')]: TRATAMIENTO_ALERTA,
  })

  return (
    <div className="space-y-8">
      {/* Alertas */}
      {alertas && (
        <section aria-labelledby="an-alertas" data-clave="alertas_id">
          <h3 id="an-alertas" className="text-sm font-bold text-gray-900 mb-2">Alertas (Reconocer / Datacrédito)</h3>
          <div className="space-y-3">
            {alertas.filas.map((f, i) => {
              const val = (clave) => f.valores[idEn(alertas, clave)] ?? ''
              const poner = (clave, v) => onCelda(alertas.idSeccion, f.localId, idEn(alertas, clave), v)
              const ref = val('a_ref')
              return (
                <div key={f.localId} className="rounded border border-gray-300 p-3 space-y-3">
                  <p className="text-xs font-bold uppercase tracking-wide text-gray-700">Alerta {i + 1}</p>
                  <div>
                    <p className="text-xs text-gray-700 mb-1">Fuente</p>
                    <Botones etiqueta="Fuente de la alerta" opciones={['Reconocer', 'Datacrédito']} valor={val('a_fuente')} deshabilitado={soloLectura} onElegir={v => poner('a_fuente', v)} />
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div><label className="block text-xs text-gray-700 mb-1">Fecha</label><CampoFecha className={inputCls} value={val('a_fecha')} disabled={soloLectura} onChange={v => poner('a_fecha', v)} /></div>
                    <div><label className="block text-xs text-gray-700 mb-1">Novedad</label><input className={inputCls} value={val('a_novedad')} disabled={soloLectura} onChange={e => poner('a_novedad', e.target.value)} /></div>
                  </div>
                  <div><label className="block text-xs text-gray-700 mb-1">Descripción</label><textarea rows={2} className={`${inputCls} py-2`} value={val('a_descripcion')} disabled={soloLectura} onChange={e => poner('a_descripcion', e.target.value)} /></div>
                  <div><label className="block text-xs text-gray-700 mb-1">Tratamiento</label><textarea rows={2} className={`${inputCls} py-2`} value={val('a_tratamiento')} disabled={soloLectura} onChange={e => poner('a_tratamiento', e.target.value)} /></div>
                  <div className="rounded border border-purple-200 bg-purple-50/50 p-2">
                    <p className="text-xs text-purple-900 mb-1">Código <span className="text-purple-700">· No se imprime</span></p>
                    <Botones etiqueta="Código de la alerta" opciones={['Sin código', 'ALR-03 · alerta de fraude']} valor={ref === 'ALR-03' || ref === 'LEG-24' ? 'ALR-03 · alerta de fraude' : ref ? 'Sin código' : ''}
                      deshabilitado={soloLectura} onElegir={v => poner('a_ref', v === 'Sin código' ? '—' : 'ALR-03')} />
                    <label className="mt-2 flex items-start gap-2 text-sm text-gray-900 min-h-11">
                      <input type="checkbox" className="mt-1 h-5 w-5" checked={ref === 'LEG-24'} disabled={soloLectura}
                        onChange={e => poner('a_ref', e.target.checked ? 'LEG-24' : 'ALR-03')} />
                      <span>Suplantación confirmada (LEG-24 · crítico). Solo si se confirmó que el evaluado usa una identidad ajena.</span>
                    </label>
                  </div>
                </div>
              )
            })}
          </div>
          {!soloLectura && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <button type="button" onClick={() => onAgregar(alertas.idSeccion)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar alerta</button>
              {alertas.filas.length === 0 && valIdent('alertas_revisadas') !== 'Sí' && (
                <button type="button" onClick={() => ponerIdent('alertas_revisadas', 'Sí')} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Sin alertas</button>
              )}
            </div>
          )}
          {alertas.filas.length === 0 && valIdent('alertas_revisadas') === 'Sí' && <p role="status" className="mt-2 text-sm text-green-800">✓ Revisado: «Sin alertas reportadas.»</p>}
        </section>
      )}

      {/* Direcciones, teléfonos y correos */}
      {TABLAS.map((cfg, k) => {
        const s = sec(cfg.clave)
        if (!s) return null
        const declarados = cfg.declarado(link).filter(tiene)
        return (
          <section key={cfg.clave} aria-labelledby={`an-${cfg.clave}`} data-clave={cfg.clave}>
            <h3 id={`an-${cfg.clave}`} className="text-sm font-bold text-gray-900 mb-1">{cfg.titulo}</h3>
            <p className="text-sm text-gray-800 mb-2">Declarado por el evaluado: {declarados.length > 0
              ? <span className="inline-block bg-gray-100 rounded px-2 py-0.5">{declarados.join(' · ')}</span> : <span className="text-gray-600 italic">sin dato en el link</span>}</p>
            <div className="space-y-3">
              {s.filas.map((f, i) => {
                const val = (clave) => f.valores[idEn(s, clave)] ?? ''
                const poner = (clave, v) => onCelda(s.idSeccion, f.localId, idEn(s, clave), v)
                return (
                  <div key={f.localId} className="rounded border border-gray-300 p-3 space-y-3">
                    <p className="text-xs font-bold uppercase tracking-wide text-gray-700">Reportado por Reconocer {i + 1}</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {cfg.campos.map(([clave, etiqueta]) => (
                        <div key={clave}>
                          <label className="block text-xs text-gray-700 mb-1" htmlFor={`an-${f.localId}-${clave}`}>{etiqueta}</label>
                          <input id={`an-${f.localId}-${clave}`} className={inputCls} value={val(clave)} disabled={soloLectura}
                            onChange={e => (clave === cfg.principal ? cambiarPrincipal(cfg, s, f, e.target.value) : poner(clave, e.target.value))} />
                        </div>
                      ))}
                    </div>
                    <div>
                      <p className="text-xs text-gray-700 mb-1">¿Coincide con {cfg.tipo === 'telefono' ? 'el declarado' : 'la declarada'}? <span className="text-gray-600">(lo calcula el sistema; puedes cambiarlo)</span></p>
                      <Botones etiqueta={`${cfg.titulo} ${i + 1}: coincide`} opciones={['Sí', 'No']} valor={val(cfg.coincide)} deshabilitado={soloLectura} onElegir={v => poner(cfg.coincide, v)} />
                    </div>
                  </div>
                )
              })}
            </div>
            {!soloLectura && (
              <button type="button" onClick={() => onAgregar(s.idSeccion)} className="mt-3 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar</button>
            )}
            {estados[k].ninguno && <p role="alert" className="mt-2 text-sm text-amber-800">Ninguno de los {cfg.unidad}s reportados coincide con el declarado.</p>}
          </section>
        )
      })}

      {/* ALR-06 sugerida */}
      {noAparecen.length > 0 && info && (
        <div role="note" className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          <p><b>Sugerido: ALR-06.</b> No aparece en Reconocer: {noAparecen.join(', ')} declarado. Es una alerta: no suma puntos.</p>
          {yaRegistrada
            ? <p className="mt-1 text-green-800">✓ Ya está registrada en «hallazgos sin impacto».</p>
            : !soloLectura && <button type="button" onClick={registrarAlr06} className="mt-2 min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 text-white">Registrar en hallazgos sin impacto</button>}
        </div>
      )}

      {/* Dirección declarada y residencia */}
      {ident && (
        <section aria-labelledby="an-ident" className="space-y-4">
          <h3 id="an-ident" className="text-sm font-bold text-gray-900">Dirección declarada y residencia</h3>
          <div data-clave="rec_dir_coincide" className="rounded">
            <p className="text-xs font-semibold text-gray-700 mb-1">¿La dirección declarada aparece en Reconocer? <span className="font-normal text-gray-600">(lo calcula el sistema; puedes cambiarlo)</span></p>
            <Botones etiqueta="La dirección declarada aparece en Reconocer" opciones={['Sí', 'No']} valor={valIdent('rec_dir_coincide')} deshabilitado={soloLectura} onElegir={v => ponerIdent('rec_dir_coincide', v)} />
          </div>
          <div data-clave="rec_residencial" className="rounded">
            <p className="text-xs font-semibold text-gray-700 mb-1">Información residencial</p>
            <Botones etiqueta="Información residencial" opciones={['Estable', 'No estable', 'Sin información']} valor={valIdent('rec_residencial')} deshabilitado={soloLectura} onElegir={v => ponerIdent('rec_residencial', v)} />
          </div>
        </section>
      )}
    </div>
  )
}

import { useState } from 'react'
import { InputPorTipo } from '../shared/Formulario'
import { Botones } from '../visitador/DatosGeneralesVisita'
import { useMasAngostoQue } from '../poligrafista/componentesHoja'
import { codigosOfrecidos } from './codigosPorFuente'

// AN-03 · Listas nacionales e internacionales como la tabla del Word: una fila por fuente, con la fuente ya escrita y el resultado SIN marcar. El analista marca
// el resultado (Sin novedad · Con hallazgo · No disponible); la fecha se pone sola, el código se filtra por fuente (hoja «Listas» del diccionario) y el soporte
// es obligatorio. Los datos se guardan en las mismas columnas de siempre (ln_*, li_*): el Word no cambia.

const RESULTADOS = ['Sin novedad', 'Con hallazgo', 'No disponible']
const ESTADOS_SUGERIDOS = ['Vigente', 'En proceso', 'Pagada', 'Cancelada', 'Archivada']
const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const hoy = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }

// ¿La columna de soporte tiene archivo? El valor es un arreglo JSON de rutas (o una ruta suelta).
export function rutasDeSoporte(valor) {
  if (!tiene(valor)) return []
  try { const a = JSON.parse(valor); return Array.isArray(a) ? a.filter(Boolean) : [String(a)] } catch { return [String(valor)] }
}
const etiquetaSoporte = (valor) => {
  const r = rutasDeSoporte(valor)
  if (r.length === 0) return null
  return r.every(x => /\.pdf$/i.test(x)) ? '✓ PDF' : '✓ Imagen'
}

export default function ListasConsulta({ seccion, tabla, soloLectura, idServicioSubproceso, onCelda, onAgregar, onError }) {
  const internacional = tabla === 'internacionales'
  const p = internacional ? 'li' : 'ln'
  const angosto = useMasAngostoQue(900)
  const [agregando, setAgregando] = useState(false)
  const col = (clave) => seccion.columnas.find(c => c.clave === clave)
  const idDe = (clave) => col(clave)?.idColumna
  const colFuente = col(internacional ? 'li_lista' : 'ln_fuente')
  const valoresFuente = (colFuente?.opciones ?? []).filter(o => o && o !== '—')
  const usadas = new Set(seccion.filas.map(f => f.valores[colFuente?.idColumna]).filter(Boolean))
  const disponibles = valoresFuente.filter(o => !usadas.has(o))

  const poner = (fila, clave, valor) => { const id = idDe(clave); if (id !== undefined) onCelda(seccion.idSeccion, fila.localId, id, valor) }
  const leer = (fila, clave) => fila.valores[idDe(clave)] ?? ''

  const elegirResultado = (fila, nuevo) => {
    poner(fila, `${p}_resultado`, nuevo)
    if (!tiene(leer(fila, `${p}_fecha`))) poner(fila, `${p}_fecha`, hoy())
    const fuente = leer(fila, colFuente.clave)
    const ofrecidos = codigosOfrecidos(tabla, fuente, internacional ? leer(fila, 'li_coincidencia') : null)
    if (nuevo !== 'Con hallazgo') poner(fila, `${p}_ref`, '—')
    else if (ofrecidos.length === 1) poner(fila, `${p}_ref`, ofrecidos[0])   // un solo código: se pone solo; el analista lo puede quitar
    else if (leer(fila, `${p}_ref`) && !ofrecidos.includes(leer(fila, `${p}_ref`))) poner(fila, `${p}_ref`, '—')
  }
  const elegirCoincidencia = (fila, nuevo) => {
    poner(fila, 'li_coincidencia', nuevo)
    // Con «Solo nombre» solo se ofrece ALR-02: un LEG-02 / LEG-04 ya elegido se quita.
    const ofrecidos = codigosOfrecidos(tabla, leer(fila, 'li_lista'), nuevo)
    const ref = leer(fila, 'li_ref')
    if (ref && ref !== '—' && !ofrecidos.includes(ref)) poner(fila, 'li_ref', ofrecidos.length === 1 ? ofrecidos[0] : '—')
    else if (leer(fila, 'li_resultado') === 'Con hallazgo' && ofrecidos.length === 1) poner(fila, 'li_ref', ofrecidos[0])
  }

  const agregarFuente = (fuente) => {
    onAgregar(seccion.idSeccion, { [colFuente.idColumna]: fuente })
    setAgregando(false)
  }

  // Las celdas, iguales en tabla y en ficha.
  const celdas = (fila) => {
    const fuente = leer(fila, colFuente.clave)
    const resultado = leer(fila, `${p}_resultado`)
    const coincidencia = internacional ? leer(fila, 'li_coincidencia') : null
    const ofrecidos = codigosOfrecidos(tabla, fuente, coincidencia)
    const ref = leer(fila, `${p}_ref`)
    const conHallazgo = resultado === 'Con hallazgo'
    const soporte = leer(fila, `${p}_soporte`)
    const marca = etiquetaSoporte(soporte)
    const id = `${p}-${fila.localId}`
    return {
      fuente: <span className="font-semibold text-gray-900 break-words">{fuente || '—'}</span>,
      resultado: (
        <div data-clave={`${p}_resultado`}>
          <Botones etiqueta={`${fuente}: resultado`} opciones={RESULTADOS} valor={resultado} deshabilitado={soloLectura} onElegir={v => elegirResultado(fila, v)} />
        </div>
      ),
      coincidencia: internacional ? (
        <Botones etiqueta={`${fuente}: coincidencia`} opciones={['Solo nombre', 'Documento']} valor={coincidencia === '—' ? '' : coincidencia} deshabilitado={soloLectura}
          onElegir={v => elegirCoincidencia(fila, coincidencia === v ? '—' : v)} />
      ) : null,
      ref: !conHallazgo ? <span className="text-gray-600" aria-label="Sin código">—</span> : ofrecidos.length === 0 ? (
        <span className="text-xs text-gray-700">Esta fuente no genera código aquí.</span>
      ) : (
        <div>
          <select aria-label={`${fuente}: código`} className={inputCls} value={ref === '—' ? '' : ref} disabled={soloLectura} onChange={e => poner(fila, `${p}_ref`, e.target.value || '—')}>
            <option value="">{ofrecidos.length === 1 ? 'Sin código' : 'Selecciona…'}</option>
            {ofrecidos.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
          {ref === '—' && ofrecidos.length > 1 && <p className="mt-1 text-xs text-amber-800">Elige el código del hallazgo.</p>}
        </div>
      ),
      estado: !internacional ? (
        <>
          <input list="estados-lista" aria-label={`${fuente}: estado`} className={inputCls} value={leer(fila, 'ln_estado')} disabled={soloLectura}
            onChange={e => poner(fila, 'ln_estado', e.target.value)} />
        </>
      ) : null,
      fecha: <input type="date" aria-label={`${fuente}: fecha`} className={inputCls} value={leer(fila, `${p}_fecha`)} disabled={soloLectura}
        onChange={e => poner(fila, `${p}_fecha`, e.target.value)} />,
      soporte: (
        <div data-clave={`${p}_soporte`} className="space-y-1">
          {tiene(resultado) && (marca
            ? <p className="text-xs font-semibold text-green-800">{marca}</p>
            : <p className="text-xs font-semibold text-red-700">Falta soporte</p>)}
          <InputPorTipo tipoDato="ARCHIVO" value={soporte} onChange={v => poner(fila, `${p}_soporte`, v)} idServicioSubproceso={idServicioSubproceso} onError={onError} disabled={soloLectura} />
        </div>
      ),
      observacion: <textarea aria-label={`${fuente}: observación`} rows={2} className={`${inputCls} py-2`} value={leer(fila, `${p}_observacion`)} disabled={soloLectura}
        onChange={e => poner(fila, `${p}_observacion`, e.target.value)} />,
      id,
    }
  }

  const encabezados = internacional
    ? [['fuente', 'Lista'], ['resultado', 'Resultado'], ['coincidencia', 'Coincidencia'], ['ref', 'Ref.'], ['fecha', 'Fecha'], ['soporte', 'Soporte'], ['observacion', 'Observación']]
    : [['fuente', 'Fuente'], ['resultado', 'Resultado'], ['ref', 'Ref.'], ['estado', 'Estado'], ['fecha', 'Fecha'], ['soporte', 'Soporte'], ['observacion', 'Observación']]

  return (
    <div>
      <datalist id="estados-lista">{ESTADOS_SUGERIDOS.map(e => <option key={e} value={e} />)}</datalist>
      {seccion.filas.length === 0 && <p className="text-gray-700 mb-2">Sin fuentes.</p>}
      {angosto ? (
        <div className="space-y-3">
          {seccion.filas.map(f => {
            const c = celdas(f)
            return (
              <div key={f.localId} data-clave={`${seccion.clave}-${leer(f, colFuente.clave)}`} className="rounded border border-gray-300 p-3 space-y-3">
                <p>{c.fuente}</p>
                {encabezados.slice(1).map(([k, titulo]) => (
                  <div key={k}><p className="text-xs font-semibold text-gray-700 mb-1">{titulo}</p>{c[k]}</div>
                ))}
              </div>
            )
          })}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm border border-gray-300">
            <thead className="bg-gray-100 text-xs text-gray-700">
              <tr>{encabezados.map(([k, t]) => <th key={k} scope="col" className="text-left p-2 border-b border-gray-300">{t}</th>)}</tr>
            </thead>
            <tbody>
              {seccion.filas.map(f => {
                const c = celdas(f)
                return (
                  <tr key={f.localId} data-clave={`${seccion.clave}-${leer(f, colFuente.clave)}`} className="border-b border-gray-200 align-top">
                    {encabezados.map(([k]) => <td key={k} className={`p-2 ${k === 'fuente' ? 'w-[14%]' : ''}`}>{c[k]}</td>)}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {!soloLectura && disponibles.length > 0 && (
        agregando ? (
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <label htmlFor={`${p}-nueva`} className="text-xs font-semibold text-gray-700">Fuente a agregar</label>
            <select id={`${p}-nueva`} className={`${inputCls} max-w-xs`} defaultValue="" onChange={e => e.target.value && agregarFuente(e.target.value)}>
              <option value="">Selecciona…</option>
              {disponibles.map(o => <option key={o} value={o}>{o}</option>)}
            </select>
            <button type="button" onClick={() => setAgregando(false)} className="min-h-11 px-3 text-sm underline text-gray-800">Cancelar</button>
          </div>
        ) : (
          <button type="button" onClick={() => setAgregando(true)}
            className="mt-3 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar fuente</button>
        )
      )}
    </div>
  )
}

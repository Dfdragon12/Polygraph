import { useEffect, useMemo, useState } from 'react'
import hallazgosService from '../../services/hallazgosService'
import { borradorConcepto } from './conceptoBorrador'

// AN-07 · Piezas de la Revisión del analista: las alertas marcadas en las consultas llegan como filas SUGERIDAS de «hallazgos sin impacto» (el analista escribe el
// tratamiento) y el concepto del analista parte de un borrador armado por el sistema. Todo se guarda en las mismas columnas de siempre.

const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

const sec = (secciones, clave) => secciones.find(s => s.clave === clave && !s.origenFijoClave)
const idEn = (s, clave) => s?.columnas.find(c => c.clave === clave)?.idColumna
const valorFila = (s, f, clave) => (s ? (f.valores[idEn(s, clave)] ?? '') : '')

/** Alertas (ALR-xx) marcadas en las consultas, con su situación y su detalle. */
export function alertasMarcadas(secciones, descripciones) {
  const lista = []
  const situacion = (codigo) => `${descripciones[codigo] ?? 'Alerta'} (${codigo})`
  const nac = sec(secciones, 'listas_nacionales')
  nac?.filas.forEach(f => {
    const ref = valorFila(nac, f, 'ln_ref')
    if (/^ALR-/.test(ref)) lista.push({ codigo: ref, situacion: situacion(ref), detalle: `Fuente: ${valorFila(nac, f, 'ln_fuente')}${tiene(valorFila(nac, f, 'ln_observacion')) ? ` · ${valorFila(nac, f, 'ln_observacion')}` : ''}` })
  })
  const intl = sec(secciones, 'listas_internacionales')
  intl?.filas.forEach(f => {
    const ref = valorFila(intl, f, 'li_ref')
    if (/^ALR-/.test(ref)) {
      const co = valorFila(intl, f, 'li_coincidencia')
      lista.push({ codigo: ref, situacion: situacion(ref), detalle: `Lista: ${valorFila(intl, f, 'li_lista')}${tiene(co) && co !== '—' ? ` · coincidencia: ${co.toLowerCase()}` : ''}` })
    }
  })
  const al = sec(secciones, 'alertas_id')
  al?.filas.forEach(f => {
    const ref = valorFila(al, f, 'a_ref')
    if (ref === 'ALR-03') {
      const texto = valorFila(al, f, 'a_descripcion') || valorFila(al, f, 'a_novedad')
      lista.push({ codigo: ref, situacion: situacion(ref), detalle: `${valorFila(al, f, 'a_fuente') || 'Alerta de identidad'}${tiene(texto) ? ` · ${texto}` : ''}` })
    }
  })
  return lista
}

export function SugerenciasAlertas({ secciones, soloLectura, onAgregar }) {
  const [descripciones, setDescripciones] = useState({})
  useEffect(() => {
    hallazgosService.listar(true).then(todos => setDescripciones(Object.fromEntries(todos.filter(h => /^ALR-/.test(h.codigo)).map(h => [h.codigo, h.descripcion])))).catch(() => {})
  }, [])
  const info = sec(secciones, 'informativos')
  const pendientes = useMemo(() => {
    if (!info) return []
    const registradas = info.filas.map(f => `${valorFila(info, f, 'i_situacion')}|${valorFila(info, f, 'i_detalle')}`)
    return alertasMarcadas(secciones, descripciones).filter(a => !registradas.some(r => r.includes(`(${a.codigo})`) && r.endsWith(`|${a.detalle}`)))
  }, [secciones, descripciones, info])
  if (!info || pendientes.length === 0) return null
  const agregar = (a) => onAgregar(info.idSeccion, { [idEn(info, 'i_situacion')]: a.situacion, [idEn(info, 'i_detalle')]: a.detalle })
  return (
    <div role="note" className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 space-y-2">
      <p><b>Alertas marcadas en las consultas.</b> No suman puntos; regístralas aquí y escribe su tratamiento.</p>
      <ul className="space-y-2">
        {pendientes.map((a, i) => (
          <li key={`${a.codigo}-${i}`} className="flex flex-wrap items-center gap-2">
            <span className="min-w-0 flex-1 basis-60 break-words">{a.situacion} — {a.detalle}</span>
            {!soloLectura && <button type="button" onClick={() => agregar(a)} className="min-h-11 px-3 text-sm font-medium rounded-lg bg-primary-600 text-white">Registrar</button>}
          </li>
        ))}
      </ul>
      {!soloLectura && pendientes.length > 1 && (
        <button type="button" onClick={() => pendientes.forEach(agregar)} className="min-h-11 px-3 text-sm font-medium rounded-lg border border-amber-400 text-amber-900">Registrar todas</button>
      )}
    </div>
  )
}

export function ConceptoAnalista({ secciones, nombreProfesional, soloLectura, onCampo }) {
  const s = sec(secciones, 'concepto_analista')
  const hall = sec(secciones, 'hallazgos')
  const info = sec(secciones, 'informativos')
  if (!s) return null
  const val = (clave) => s.valores[idEn(s, clave)] ?? ''
  const poner = (clave, v) => onCampo(s.idSeccion, idEn(s, clave), v)
  const texto = val('concepto')
  const [reemplazar, setReemplazar] = useState(false)

  const armar = () => borradorConcepto(
    (hall?.filas ?? []).map(f => ({ ref: valorFila(hall, f, 'h_ref'), descripcion: valorFila(hall, f, 'h_descripcion'), fuente: valorFila(hall, f, 'h_fuente'), detalle: valorFila(hall, f, 'h_detalle'), puntos: valorFila(hall, f, 'h_puntos') })),
    (info?.filas ?? []).map(f => ({ situacion: valorFila(info, f, 'i_situacion') })).filter(a => tiene(a.situacion)),
  )
  const generar = () => {
    if (tiene(texto) && !reemplazar) { setReemplazar(true); return }
    poner('concepto', armar())
    setReemplazar(false)
  }
  const max = 2000
  return (
    <section aria-labelledby="an-concepto" data-clave="concepto" className="space-y-3 rounded">
      <h3 id="an-concepto" className="text-sm font-bold text-gray-900">Concepto del analista <span className="text-red-600">*</span></h3>
      {!soloLectura && (
        <div className="space-y-2">
          <button type="button" onClick={generar} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">
            {tiene(texto) ? 'Rehacer el borrador' : 'Generar borrador'}
          </button>
          {reemplazar && (
            <p role="alert" className="text-sm text-amber-900">Esto reemplaza lo que escribiste. <button type="button" onClick={generar} className="underline min-h-11">Sí, reemplazar</button>{' '}
              <button type="button" onClick={() => setReemplazar(false)} className="underline min-h-11">Cancelar</button></p>
          )}
          <p className="text-xs text-gray-700">El borrador usa los hallazgos y las alertas ya guardados. Edítalo antes de enviar.</p>
        </div>
      )}
      <textarea aria-label="Concepto del analista" rows={6} maxLength={max} className={`${inputCls} py-2`} value={texto} disabled={soloLectura} onChange={e => poner('concepto', e.target.value)} />
      {texto.length >= max * 0.8 && <p className="text-xs text-amber-700" role="status">{texto.length} / {max}</p>}
      <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
        <div><dt className="text-xs text-gray-600">Profesional encargado</dt><dd className="bg-gray-100 rounded px-2 py-1">{val('analista_nombre') || nombreProfesional || '—'}</dd></div>
        <div><dt className="text-xs text-gray-600">Fecha</dt><dd className="bg-gray-100 rounded px-2 py-1">{val('analista_fecha') || 'Se pone sola al marcar como completado'}</dd></div>
      </dl>
    </section>
  )
}

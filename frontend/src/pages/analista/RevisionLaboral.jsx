import { useMemo, useState } from 'react'
import { sugerenciasInformativas, borradorConceptoLaboral } from './reglasRevisionLaboral'

// LB-05 · Piezas de la Revisión de la verificación laboral: los hallazgos sin impacto llegan como filas SUGERIDAS desde las vinculaciones y la inactividad (el analista
// las registra, las edita o las descarta) y el concepto parte de un borrador armado con lo registrado. Se guardan las mismas columnas de siempre.

const sec = (secciones, clave) => secciones.find(s => s.clave === clave && !s.origenFijoClave)
const idEn = (s, clave) => s?.columnas.find(c => c.clave === clave)?.idColumna
const valorFila = (s, f, clave) => (s ? (f.valores[idEn(s, clave)] ?? '') : '')
const porClave = (s, f) => Object.fromEntries((s?.columnas ?? []).map(c => [c.clave, f.valores[c.idColumna] ?? '']))

/** Datos de la verificación leídos de las secciones: periodo, vinculaciones (por clave) e inactividades visibles. */
export function datosDeRevision(secciones) {
  const alc = sec(secciones, 'alcance')
  const sv = sec(secciones, 'vinculaciones')
  const si = sec(secciones, 'inactividad')
  const hall = sec(secciones, 'hallazgos')
  const info = sec(secciones, 'informativos')
  return {
    periodo: { desde: alc ? (alc.valores[idEn(alc, 'lab_periodo_desde')] ?? '') : '', hasta: alc ? (alc.valores[idEn(alc, 'lab_periodo_hasta')] ?? '') : '' },
    vinculaciones: (sv?.filas ?? []).map(f => porClave(sv, f)),
    inactividades: (si?.filas ?? []).map(f => porClave(si, f)).filter(p => p.in_origen !== 'Descartado' && p.in_desde)
      .map(p => ({ desde: p.in_desde, hasta: p.in_hasta, dias: p.in_dias, corroboracion: p.in_corroboracion })),
    hallazgos: (hall?.filas ?? []).map(f => ({ ref: valorFila(hall, f, 'h_ref'), descripcion: valorFila(hall, f, 'h_descripcion'), fuente: valorFila(hall, f, 'h_fuente'), detalle: valorFila(hall, f, 'h_detalle'), puntos: valorFila(hall, f, 'h_puntos') })),
    informativos: (info?.filas ?? []).map(f => ({ situacion: valorFila(info, f, 'i_situacion'), detalle: valorFila(info, f, 'i_detalle') })),
  }
}

export const armarBorradorLaboral = (secciones) => borradorConceptoLaboral(datosDeRevision(secciones))

export function SugerenciasInformativas({ secciones, soloLectura, onAgregar }) {
  const [descartadas, setDescartadas] = useState(() => new Set())
  const info = sec(secciones, 'informativos')
  const pendientes = useMemo(() => {
    if (!info) return []
    const d = datosDeRevision(secciones)
    return sugerenciasInformativas(d.vinculaciones, d.inactividades)
      .filter(x => !d.informativos.some(r => r.situacion === x.situacion && r.detalle.includes(x.marca)))
      .filter(x => !descartadas.has(`${x.situacion}|${x.marca}`))
  }, [secciones, info, descartadas])
  if (!info || pendientes.length === 0 || soloLectura) return null
  const agregar = (x) => onAgregar(info.idSeccion, { [idEn(info, 'i_situacion')]: x.situacion, [idEn(info, 'i_detalle')]: x.detalle })
  const descartar = (x) => setDescartadas(prev => new Set(prev).add(`${x.situacion}|${x.marca}`))
  return (
    <div role="note" className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 space-y-2">
      <p><b>Sugeridos por el sistema.</b> No suman puntos; regístralos, edítalos o descártalos. Tú decides.</p>
      <ul className="space-y-2">
        {pendientes.map(x => (
          <li key={`${x.situacion}|${x.marca}`} className="flex flex-wrap items-center gap-2">
            <span className="min-w-0 flex-1 basis-60 break-words"><b>{x.situacion}</b> — {x.detalle}</span>
            <button type="button" onClick={() => agregar(x)} className="min-h-11 px-3 text-sm font-medium rounded-lg bg-primary-600 text-white">Registrar</button>
            <button type="button" onClick={() => descartar(x)} className="min-h-11 px-3 text-sm font-medium rounded-lg border border-amber-400 text-amber-900">Descartar</button>
          </li>
        ))}
      </ul>
      {pendientes.length > 1 && <button type="button" onClick={() => pendientes.forEach(agregar)} className="min-h-11 px-3 text-sm font-medium rounded-lg border border-amber-400 text-amber-900">Registrar todos</button>}
    </div>
  )
}

import { useMemo, useState } from 'react'
import { accesoEstudio, listasDeEstudio } from './TemasAcademica'
import { leerInstantanea } from './reglasAcademica'
import { marcasDelEstudio, sugerenciasInformativasAcademica, borradorConceptoAcademica } from './reglasEstudio'
import { borradorConcepto } from './conceptoBorrador'

// AC-04 · Piezas de la Revisión de la verificación académica: los hallazgos sin impacto llegan como filas SUGERIDAS desde el estudio verificado (el analista las registra, edita o
// descarta) y el concepto parte de un borrador armado con lo confirmado. Se guardan las mismas columnas de siempre.

const sec = (secciones, clave) => secciones.find(s => s.clave === clave && !s.origenFijoClave)
const idEn = (s, clave) => s?.columnas.find(c => c.clave === clave)?.idColumna
const valorFila = (s, f, clave) => (s ? (f.valores[idEn(s, clave)] ?? '') : '')
const NOOP = () => {}

function datos(secciones) {
  const a = accesoEstudio(secciones, NOOP)
  const v = a.valores()
  const marcas = marcasDelEstudio(v, leerInstantanea(v.aca_decl), listasDeEstudio(a))
  const hall = sec(secciones, 'hallazgos')
  const info = sec(secciones, 'informativos')
  return {
    v, marcas,
    hallazgos: (hall?.filas ?? []).map(f => ({ ref: valorFila(hall, f, 'h_ref'), descripcion: valorFila(hall, f, 'h_descripcion'), fuente: valorFila(hall, f, 'h_fuente'), detalle: valorFila(hall, f, 'h_detalle'), puntos: valorFila(hall, f, 'h_puntos') })),
    informativos: (info?.filas ?? []).map(f => ({ situacion: valorFila(info, f, 'i_situacion') })).filter(x => x.situacion),
  }
}

export function armarBorradorAcademica(secciones) {
  const d = datos(secciones)
  const impacto = d.hallazgos.length > 0 ? borradorConcepto(d.hallazgos, []) : ''
  return borradorConceptoAcademica({ v: d.v, hallazgos: d.hallazgos, informativos: d.informativos, impacto })
}

export function SugerenciasAcademica({ secciones, soloLectura, onAgregar }) {
  const [descartadas, setDescartadas] = useState(() => new Set())
  const info = sec(secciones, 'informativos')
  const pendientes = useMemo(() => {
    if (!info) return []
    const d = datos(secciones)
    return sugerenciasInformativasAcademica(d.v, d.marcas)
      .filter(x => !d.informativos.some(r => r.situacion === x.situacion))
      .filter(x => !descartadas.has(x.situacion))
  }, [secciones, info, descartadas])
  if (!info || pendientes.length === 0 || soloLectura) return null
  const agregar = (x) => onAgregar(info.idSeccion, { [idEn(info, 'i_situacion')]: x.situacion, [idEn(info, 'i_detalle')]: x.detalle })
  return (
    <div role="note" className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 space-y-2">
      <p><b>Sugeridos por el sistema.</b> No suman puntos; regístralos, edítalos o descártalos. Tú decides.</p>
      <ul className="space-y-2">
        {pendientes.map(x => (
          <li key={x.situacion} className="flex flex-wrap items-center gap-2">
            <span className="min-w-0 flex-1 basis-60 break-words"><b>{x.situacion}</b> — {x.detalle}</span>
            <button type="button" onClick={() => agregar(x)} className="min-h-11 px-3 text-sm font-medium rounded-lg bg-primary-600 text-white">Registrar</button>
            <button type="button" onClick={() => setDescartadas(prev => new Set(prev).add(x.situacion))} className="min-h-11 px-3 text-sm font-medium rounded-lg border border-amber-400 text-amber-900">Descartar</button>
          </li>
        ))}
      </ul>
    </div>
  )
}

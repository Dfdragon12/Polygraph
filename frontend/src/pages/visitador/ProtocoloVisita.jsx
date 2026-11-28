import { Botones } from './DatosGeneralesVisita'
import { PREGUNTAS_PROTOCOLO, numerosProtocolo } from './fasesVisita'

// VD-03 · Protocolo de seguridad: tabla de tres columnas como en el Word (pregunta, respuesta No/Sí, detalle). El número que se muestra es continuo
// (ver numerosProtocolo); las claves internas ps_N no cambian. La columna «Ref.» la calcula el sistema y no se muestra aquí (sale en Revisión y en el Word).

const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'

export default function ProtocoloVisita({ seccion, soloLectura, onCampo }) {
  const col = (clave) => seccion.columnas.find(c => c.clave === clave)
  const val = (clave) => { const c = col(clave); return c ? (seccion.valores[c.idColumna] ?? '') : '' }
  const poner = (clave, valor) => { const c = col(clave); if (c) onCampo(seccion.idSeccion, c.idColumna, valor) }
  const numeros = numerosProtocolo()

  const elegir = (p, valor) => {
    poner(`ps_${p.n}`, valor)
    // Con «No» no hay detalle ni precisión: se limpian para que no quede nada escondido.
    if (valor !== 'Sí') {
      poner(`ps_${p.n}_det`, '')
      if (p.precision) poner(p.precision.clave, '')
    }
  }

  return (
    <div>
      <p className="text-sm text-gray-800 mb-3">Las respuestas son Sí o No; cada respuesta afirmativa se explica en «Detalle».</p>
      <div className="overflow-x-auto">
        <table className="w-full text-sm border border-gray-300">
          <thead className="bg-gray-100 text-xs text-gray-700">
            <tr>
              <th scope="col" className="text-left p-2 border-b border-gray-300">Pregunta</th>
              <th scope="col" className="text-left p-2 border-b border-gray-300 whitespace-nowrap">Respuesta</th>
              <th scope="col" className="text-left p-2 border-b border-gray-300">Detalle de la respuesta afirmativa</th>
            </tr>
          </thead>
          <tbody>
            {PREGUNTAS_PROTOCOLO.filter(p => col(`ps_${p.n}`)).map(p => {
              const si = val(`ps_${p.n}`) === 'Sí'
              const colPrec = p.precision ? col(p.precision.clave) : null
              const opcionesPrec = colPrec ? (colPrec.opciones ?? []).filter(o => o && o !== '—') : []
              const idDet = `ps-${p.n}-det`
              return (
                <tr key={p.n} data-clave={`ps_${p.n}`} className="border-b border-gray-200 align-top rounded">
                  <td className="p-2 w-[42%]">
                    <p className="text-gray-900"><span className="font-semibold">{numeros[p.n]}.</span> {p.texto}</p>
                    {si && colPrec && (
                      <div className="mt-2 rounded border border-purple-200 bg-purple-50/50 p-2">
                        <p className="text-xs text-purple-900 mb-1">{p.precision.pregunta} <span className="text-purple-700">· No se imprime</span></p>
                        <Botones etiqueta={`Pregunta ${numeros[p.n]}: ${p.precision.pregunta}`} opciones={opcionesPrec} valor={val(p.precision.clave)}
                          deshabilitado={soloLectura} onElegir={v => poner(p.precision.clave, v)} />
                      </div>
                    )}
                  </td>
                  <td className="p-2">
                    <Botones etiqueta={`Pregunta ${numeros[p.n]}`} opciones={['No', 'Sí']} valor={val(`ps_${p.n}`)} deshabilitado={soloLectura} onElegir={v => elegir(p, v)} />
                  </td>
                  <td className="p-2">
                    {si
                      ? <textarea id={idDet} aria-label={`Pregunta ${numeros[p.n]}: detalle (obligatorio)`} rows={2} className={`${inputCls} py-2`} value={val(`ps_${p.n}_det`)}
                        disabled={soloLectura} onChange={e => poner(`ps_${p.n}_det`, e.target.value)} placeholder="Explica la respuesta (obligatorio)" />
                      : <span className="text-gray-600" aria-label="Sin detalle">{val(`ps_${p.n}`) === 'No' ? '—' : ''}</span>}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

import { Botones } from './DatosGeneralesVisita'
import { GRUPOS_TELETRABAJO } from './fasesVisita'

// VD-06 · Las 38 preguntas Sí/No del módulo teletrabajo, agrupadas como en el Word y con los mismos botones No / Sí del protocolo.
// `valores` = clave (tt_N) → valor de la fila del módulo; `onValor(clave, valor)` guarda.

export default function TeletrabajoVisita({ valores, soloLectura, onValor }) {
  return (
    <div className="space-y-5">
      {GRUPOS_TELETRABAJO.map(g => (
        <section key={g.titulo} aria-labelledby={`tt-${g.titulo}`}>
          <h4 id={`tt-${g.titulo}`} className="text-xs font-bold uppercase tracking-wide text-gray-700 border-b border-gray-300 pb-1 mb-2">{g.titulo}</h4>
          <ul className="divide-y divide-gray-200 border border-gray-200 rounded-lg">
            {g.preguntas.map(p => (
              <li key={p.n} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                <span className="text-sm text-gray-900 min-w-0 flex-1 basis-64 break-words">{p.texto}</span>
                <Botones etiqueta={p.texto} opciones={['No', 'Sí']} valor={valores[`tt_${p.n}`] ?? ''} deshabilitado={soloLectura}
                  onElegir={v => onValor(`tt_${p.n}`, valores[`tt_${p.n}`] === v ? '' : v)} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

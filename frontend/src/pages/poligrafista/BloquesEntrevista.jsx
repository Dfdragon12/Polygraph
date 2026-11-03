import { useId } from 'react'
import { CampoHoja, TextoHoja } from './componentesHoja'
import { PERSONAL, temaDelCliente, textoDeclarado } from './preguntasEntrevista'
import { abrirAdmision, editar, elegirComparacion, elegirFrecuencia, respuestaDe, responder, leer } from './logicaPregunta'
import { admisionesManuales, agregarManual, editarManual, leerAdmision, ponerAdmision, quitarAdmision, quitarManual } from './admisionesPregunta'
import { tituloLimpio } from './revisionFormato'

// PF-03 · Una pregunta de la entrevista: botones grandes Sí / No (sin respuesta por defecto), frase estándar con «No», y con «Sí» el texto
// vacío, las listas cortas de esa pregunta y la casilla «Es una admisión». No muestra códigos ni puntos.

const colDe = (secciones, clave) => {
  for (const s of secciones) {
    if (s.origenFijoClave) continue
    const c = s.columnas.find(x => x.clave === clave)
    if (c) return c
  }
  return null
}

// `grande` (PF-09, celular con enlace de proveedor): botones altos que ocupan todo el ancho, para pulsarlos con el pulgar.
export function Botones({ valor, opciones, onElegir, disabled, etiqueta, grande = false }) {
  return (
    <div role="group" aria-label={etiqueta} className={grande ? 'flex gap-3' : 'flex flex-wrap gap-2'}>
      {opciones.map(o => {
        const activa = valor === o
        return (
          <button key={o} type="button" disabled={disabled} aria-pressed={activa} onClick={() => onElegir(o)}
            className={`${grande ? 'min-h-16 flex-1 px-4 text-xl' : 'min-h-11 min-w-24 px-5 text-sm'} font-semibold rounded-lg border-2 transition-colors disabled:opacity-60 ${activa
              ? 'bg-primary-600 border-primary-600 text-white' : 'bg-white border-gray-300 text-gray-800 hover:border-primary-400'}`}>
            {o}
          </button>
        )
      })}
    </div>
  )
}

function Casilla({ etiqueta, marcada, onChange, disabled }) {
  const id = useId()
  return (
    <div className="flex items-center gap-2 min-h-8">
      <input id={id} type="checkbox" checked={marcada} disabled={disabled} onChange={e => onChange(e.target.checked)} className="h-4 w-4 rounded border-gray-400 text-primary-600" />
      <label htmlFor={id} className="text-[15px] text-gray-900">{etiqueta}</label>
    </div>
  )
}

export function PreguntaSiNo({ q, secciones, frases, temasCliente, conflicto = false, disabled, aplicar, idServicioSubproceso, onError, grande = false }) {
  const colTexto = colDe(secciones, q.texto)
  const resp = respuestaDe(secciones, q)
  const frase = frases[q.frase]
  const adm = leerAdmision(secciones, q.id)
  const tema = temaDelCliente(q, temasCliente)
  const titulo = (colTexto?.nombre ?? q.id).replace(/\s*[–-]\s*texto$/i, '')
  const opcionesCat = [...(colDe(secciones, 'ad_categoria')?.opciones ?? []), PERSONAL]
  const colCuando = colDe(secciones, 'ad_cuando')
  const colTipo = colDe(secciones, 'ad_tipo')
  const colFrec = q.frecuencia ? colDe(secciones, q.frecuencia) : null
  const abierta = resp === 'Sí'

  return (
    <fieldset className="mb-6 border-0 p-0 min-w-0">
      <legend className="text-[15px] font-bold text-gray-900 mb-1.5">
        {titulo}
        {tema && <span className="ml-2 align-middle text-[11px] font-semibold px-2 py-0.5 rounded bg-amber-100 text-amber-800" title={tema.nombre}>Tema pedido por el cliente</span>}
      </legend>

      {q.tipo === 'frecuencia' ? (
        <Botones valor={leer(secciones, q.frecuencia)} opciones={colFrec?.opciones ?? ['Nulo', 'Ocasional', 'Frecuente']} etiqueta={`${titulo}: frecuencia`} disabled={disabled}  grande={grande}
          onElegir={f => aplicar(s => elegirFrecuencia(s, q, f, frase))} />
      ) : (
        <Botones valor={resp} opciones={['Sí', 'No']} etiqueta={`${titulo}: Sí o No`} disabled={disabled} grande={grande}
          onElegir={r => aplicar(s => responder(s, q, r, frase, conflicto))} />
      )}

      {resp && (
        <div className="mt-3 space-y-3">
          <TextoHoja value={leer(secciones, q.texto)} disabled={disabled} maxLength={colTexto?.longitudMaxima}
            onChange={v => aplicar(s => editar(s, q.texto, v))} aria-label={`${titulo}: lo que respondió`}
            placeholder={abierta ? 'Escribe lo que admitió' : undefined} />
          {resp === 'No' && conflicto && (
            <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2" role="alert">
              En su hoja de vida respondió Sí. Explique la diferencia.
            </p>
          )}

          {abierta && (
            <>
              {(q.listas ?? []).map(clave => {
                const c = colDe(secciones, clave)
                return c && <CampoHoja key={clave} c={c} value={leer(secciones, clave)} disabled={disabled} confirmado onConfirmar={() => {}}
                  onChange={v => aplicar(s => editar(s, clave, v))} idServicioSubproceso={idServicioSubproceso} onError={onError} />
              })}
              {(q.extras ?? []).map(clave => {
                const c = colDe(secciones, clave)
                return c && <CampoHoja key={clave} c={c} value={leer(secciones, clave)} disabled={disabled} confirmado onConfirmar={() => {}}
                  onChange={v => aplicar(s => editar(s, clave, v))} idServicioSubproceso={idServicioSubproceso} onError={onError} />
              })}
              {(q.subs ?? []).map(clave => {
                const c = colDe(secciones, clave)
                return c && <Casilla key={clave} etiqueta={tituloLimpio(c.nombre)} marcada={leer(secciones, clave) === 'Sí'} disabled={disabled}
                  onChange={on => aplicar(s => editar(s, clave, on ? 'Sí' : ''))} />
              })}

              <div className="rounded border border-gray-300 bg-gray-50 p-3 space-y-2">
                <Casilla etiqueta="Es una admisión" marcada={!!adm} disabled={disabled}
                  onChange={on => aplicar(s => (on ? abrirAdmision(s, q) : quitarAdmision(s, q.id)))} />
                {adm && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1">
                    <div>
                      <label className="block text-sm font-bold text-gray-900">Categoría</label>
                      <select value={adm.categoria ?? ''} disabled={disabled} className="w-full bg-yellow-50 border-0 border-b border-amber-400 px-1.5 py-1 text-[15px]"
                        onChange={e => aplicar(s => ponerAdmision(s, q.id, { ...adm, categoria: e.target.value }))}>
                        <option value="">—</option>
                        {opcionesCat.map(o => <option key={o} value={o}>{o}</option>)}
                      </select>
                    </div>
                    {colCuando && adm.categoria !== PERSONAL && (
                      <div>
                        <label className="block text-sm font-bold text-gray-900">Cuándo</label>
                        <select value={adm.cuando} disabled={disabled} className="w-full bg-yellow-50 border-0 border-b border-amber-400 px-1.5 py-1 text-[15px]"
                          onChange={e => aplicar(s => ponerAdmision(s, q.id, { ...adm, cuando: e.target.value }))}>
                          <option value="">—</option>
                          {colCuando.opciones.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                      </div>
                    )}
                    {q.admTipo && colTipo && adm.categoria !== PERSONAL && (
                      <div className="sm:col-span-2">
                        <label className="block text-sm font-bold text-gray-900">Tipo de antecedente</label>
                        <select value={adm.tipo} disabled={disabled} className="w-full bg-yellow-50 border-0 border-b border-amber-400 px-1.5 py-1 text-[15px]"
                          onChange={e => aplicar(s => ponerAdmision(s, q.id, { ...adm, tipo: e.target.value }))}>
                          {colTipo.opciones.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      )}
    </fieldset>
  )
}

// «Otras admisiones»: las que no encajan en ninguna pregunta. Las de las preguntas ya están en su lugar.
export function AdmisionesManuales({ secciones, disabled, aplicar }) {
  const manuales = admisionesManuales(secciones)
  const colCat = colDe(secciones, 'ad_categoria')
  const colCuando = colDe(secciones, 'ad_cuando')
  return (
    <div>
      <p className="text-[15px] text-gray-700 mb-3">Las admisiones de cada pregunta se registran en la misma pregunta. Aquí agrega solo las que no encajan en ninguna.</p>
      <div className="space-y-3">
        {manuales.map(a => {
          const guardada = a.idFila != null
          return (
            <div key={a.localId} className="rounded border border-gray-300 p-3 space-y-2">
              <div>
                <label className="block text-sm font-bold text-gray-900">Admisión</label>
                <TextoHoja value={a.texto} disabled={disabled} onChange={v => aplicar(s => editarManual(s, a.localId, { texto: v }))} aria-label="Texto de la admisión" />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1">
                <div>
                  <label className="block text-sm font-bold text-gray-900">Categoría</label>
                  <select value={a.categoria ?? ''} disabled={disabled} className="w-full bg-yellow-50 border-0 border-b border-amber-400 px-1.5 py-1 text-[15px]"
                    onChange={e => aplicar(s => editarManual(s, a.localId, { categoria: e.target.value }))}>
                    <option value="">—</option>
                    {(colCat?.opciones ?? []).map(o => <option key={o} value={o}>{o}</option>)}
                    {/* Una admisión ya guardada no cambia de tabla: el servidor no deja borrar filas guardadas. */}
                    {(!guardada || a.categoria === PERSONAL) && <option value={PERSONAL}>{PERSONAL}</option>}
                  </select>
                </div>
                {a.categoria !== PERSONAL && colCuando && (
                  <div>
                    <label className="block text-sm font-bold text-gray-900">Cuándo</label>
                    <select value={a.cuando} disabled={disabled} className="w-full bg-yellow-50 border-0 border-b border-amber-400 px-1.5 py-1 text-[15px]"
                      onChange={e => aplicar(s => editarManual(s, a.localId, { cuando: e.target.value }))}>
                      <option value="">—</option>
                      {colCuando.opciones.map(o => <option key={o} value={o}>{o}</option>)}
                    </select>
                  </div>
                )}
              </div>
              {!guardada && !disabled && (
                <button type="button" onClick={() => aplicar(s => quitarManual(s, a.localId))} className="text-xs text-red-700 hover:underline">Quitar</button>
              )}
            </div>
          )
        })}
      </div>
      {!disabled && (
        <button type="button" onClick={() => aplicar(s => agregarManual(s))}
          className="mt-3 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar otra admisión</button>
      )}
    </div>
  )
}


// PF-04 · Una pregunta del módulo del candidato: lo que respondió en su link (en gris) y, debajo, «Lo confirma» o «Difiere».
export function ComparacionModulo({ c, secciones, titulo, declarado, disabled, aplicar }) {
  const eleccion = leer(secciones, c.cmp)
  const enModulo = leer(secciones, c.modulo) || textoDeclarado(declarado) || 'Sin respuesta registrada'
  const falta = eleccion === 'Difiere' && !leer(secciones, c.prueba).trim()
  return (
    <fieldset className="mb-6 border-0 p-0 min-w-0">
      <legend className="text-[15px] font-bold text-gray-900 mb-1.5">{titulo}</legend>
      <p className="text-sm text-gray-600 mb-2"><span className="font-semibold">Respondió en el módulo:</span> <span className="bg-gray-100 rounded px-2 py-0.5">{enModulo}</span></p>
      <Botones valor={eleccion} opciones={['Lo confirma', 'Difiere']} etiqueta={`${titulo}: lo confirma o difiere`} disabled={disabled}
        onElegir={e => aplicar(s => elegirComparacion(s, c, e))} />
      {eleccion && (
        <div className="mt-3">
          <label className="block text-sm font-bold text-gray-900 mb-1">Manifestó en la prueba{eleccion === 'Difiere' && <span className="text-red-600"> *</span>}</label>
          <TextoHoja value={leer(secciones, c.prueba)} disabled={disabled} aria-label={`${titulo}: lo que manifestó en la prueba`}
            placeholder={eleccion === 'Difiere' ? 'Explique la diferencia' : undefined}
            onChange={v => aplicar(s => editar(s, c.prueba, v))} />
          {falta && <p className="mt-1 text-sm text-amber-800" role="status">Explique la diferencia.</p>}
        </div>
      )}
    </fieldset>
  )
}

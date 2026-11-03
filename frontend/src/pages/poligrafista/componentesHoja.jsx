import { useEffect, useId, useRef } from 'react'
import { Link } from 'react-router-dom'
import { InputPorTipo } from '../shared/Formulario'
import { tituloLimpio } from './revisionFormato'

// PF-02 · Piezas para llenar el formato "sobre la hoja": el campo es un espacio subrayado con fondo
// amarillo muy suave dentro del texto, y los textos largos crecen con lo que se escribe.

export const FUENTE_DOCUMENTO = { fontFamily: 'Arial, Helvetica, sans-serif' }
const CAMPO = 'bg-yellow-50 border-0 border-b border-amber-400 px-1.5 py-1 text-[15px] text-gray-900 rounded-t focus:outline-none focus:bg-yellow-100 focus:border-amber-600 disabled:bg-transparent disabled:text-gray-700 disabled:border-gray-300'

// Textos largos: un área que crece sola, sin contador. El aviso aparece solo cerca del límite.
export function TextoHoja({ value, onChange, disabled, maxLength, id, ...resto }) {
  const ref = useRef(null)
  const max = maxLength ?? 2000
  useEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${el.scrollHeight}px`
  }, [value])
  const largo = (value ?? '').length
  const cerca = largo >= max * 0.9
  return (
    <>
      <textarea ref={ref} id={id} rows={1} value={value ?? ''} maxLength={max} disabled={disabled} spellCheck lang="es"
        onChange={e => onChange(e.target.value)} className={`${CAMPO} w-full resize-none overflow-hidden block leading-relaxed`} {...resto} />
      {cerca && <p className="mt-1 text-xs text-amber-700" role="status">Te quedan {max - largo} caracteres.</p>}
    </>
  )
}

// Entrada de un campo con el aspecto de la hoja. Lo que no es texto/lista/fecha/número/sí-no (archivos,
// ciudad, barrio) se delega al componente de siempre.
export function InputHoja({ c, value, onChange, disabled, idServicioSubproceso, onError, id }) {
  const t = c.tipoDato
  if (t === 'LISTA') {
    return (
      <select id={id} value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={`${CAMPO} max-w-full`}>
        <option value="">—</option>
        {(c.opciones ?? []).map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    )
  }
  if (t === 'BOOLEANO') {
    return (
      <select id={id} value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={CAMPO}>
        <option value="">—</option><option value="true">Sí</option><option value="false">No</option>
      </select>
    )
  }
  if (t === 'FECHA') return <input id={id} type="date" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={CAMPO} />
  if (t === 'NUMERO') return <input id={id} type="number" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={`${CAMPO} w-28`} />
  if (c.clave?.startsWith('pol_hora')) return <input id={id} type="time" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={CAMPO} />
  if (t === 'TEXTO') return <TextoHoja id={id} value={value} onChange={onChange} disabled={disabled} maxLength={c.longitudMaxima} />
  return <InputPorTipo tipoDato={t} value={value} opciones={c.opciones} onChange={onChange} idServicioSubproceso={idServicioSubproceso} onError={onError} disabled={disabled} maxLength={c.longitudMaxima} />
}

// Campos cortos: van en la misma línea que su título, como "Cargo: ____" en el Word.
const EN_LINEA = new Set(['pol_edad', 'ev_lugar_exp', 'pol_localizacion', 'ev_cliente', 'ev_cargo', 'estudio_numero', 'pol_anterior',
  'pol_equipo', 'pol_poligrafista', 'pol_poligrafista_cred', 'pol_num_relevantes', 've_tiene', 've_tipo', 've_marca', 've_a_nombre',
  've_licencia', 've_comparendos', 'ex_descargos_empresa', 'au_pagina'])
const esEnLinea = (c) => ['LISTA', 'BOOLEANO', 'FECHA', 'NUMERO'].includes(c.tipoDato) || EN_LINEA.has(c.clave)

// Un dato de la hoja: título en negrita y, a continuación, el campo. Los datos que llegan de la hoja de vida
// o del estudio (automáticos) salen en gris, con "Confirmar" y "Corregir" en vez de volver a escribirlos.
export function CampoHoja({ c, value, onChange, disabled, idServicioSubproceso, onError, confirmado, onConfirmar, rutaCorregir }) {
  const id = useId()
  const precargado = c.automatico && !c.calculado
  const enLinea = esEnLinea(c)
  return (
    <div className={`mb-4 ${enLinea ? 'flex flex-wrap items-baseline gap-x-2 gap-y-1' : ''}`}>
      <label htmlFor={id} className="text-[15px] font-bold text-gray-900">
        {tituloLimpio(c.nombre)}{c.obligatorio && <span className="text-red-600"> *</span>}
        {enLinea ? ':' : ''}
      </label>
      {precargado ? (
        <span className="inline-flex flex-wrap items-center gap-2">
          <span id={id} className="bg-gray-100 text-gray-600 px-2 py-1 rounded text-[15px]">{value || '—'}</span>
          {confirmado ? <span className="text-xs text-green-700">✓ Confirmado</span> : (
            <>
              <button type="button" onClick={onConfirmar} className="min-h-8 px-2.5 text-xs font-medium rounded border border-gray-300 text-gray-800 hover:bg-gray-50">Confirmar</button>
              {rutaCorregir && <Link to={rutaCorregir} className="text-xs font-medium text-primary-700 hover:underline">Corregir</Link>}
            </>
          )}
        </span>
      ) : (
        <div className={enLinea ? 'min-w-0 flex-1 basis-48' : 'mt-1'}>
          <InputHoja id={id} c={c} value={value} onChange={onChange} disabled={disabled || c.automatico} idServicioSubproceso={idServicioSubproceso} onError={onError} />
        </div>
      )}
    </div>
  )
}

// Panel derecho: lo que pidió el cliente, la nota de quien coordina y lo que el candidato dijo en su link.
export function PanelContexto({ ficha, nota }) {
  const temas = ficha?.solicitudCliente?.temas ?? []
  const datos = ficha?.solicitudCliente?.datos ?? []
  const decl = ficha?.declaraciones ?? []
  const Titulo = ({ children }) => <h3 className="text-[11px] font-semibold uppercase tracking-wider text-gray-600 mb-1.5">{children}</h3>
  return (
    <aside aria-label="Contexto del servicio" className="space-y-4 text-sm">
      <section className="bg-white border border-gray-200 rounded-xl p-3">
        <Titulo>Lo que pidió el cliente</Titulo>
        {temas.length === 0 && datos.length === 0 && <p className="text-xs text-gray-600">Sin temas ni observaciones.</p>}
        {temas.length > 0 && (
          <ul className="list-disc pl-4 space-y-0.5 text-gray-800">
            {temas.map((t, i) => <li key={i}>{t.nombre}{t.detalle ? `: ${t.detalle}` : ''}</li>)}
          </ul>
        )}
        {datos.length > 0 && (
          <dl className="mt-2 space-y-1">
            {datos.map((d, i) => <div key={i}><dt className="text-xs text-gray-600">{d.etiqueta}</dt><dd className="text-gray-900 break-words">{d.valor}</dd></div>)}
          </dl>
        )}
      </section>
      {nota && (
        <section className="bg-amber-50 border border-amber-200 rounded-xl p-3">
          <Titulo>Nota del gestor</Titulo>
          <p className="text-gray-900 whitespace-pre-line break-words">{nota}</p>
        </section>
      )}
      <section className="bg-white border border-gray-200 rounded-xl p-3">
        <Titulo>Su hoja de vida dice</Titulo>
        {decl.length === 0 ? <p className="text-xs text-gray-600">Sin respuestas registradas.</p> : (
          <ul className="space-y-2">
            {decl.map((d, i) => (
              <li key={i}>
                <p className="text-xs text-gray-600">{d.pregunta}</p>
                <p className="text-gray-900"><b>{d.respuesta}</b>{d.detalle ? ` — ${d.detalle}` : ''}</p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </aside>
  )
}

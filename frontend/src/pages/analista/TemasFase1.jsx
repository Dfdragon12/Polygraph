import { useState } from 'react'
import { Botones } from '../visitador/DatosGeneralesVisita'
import { filasDeIdentidad } from './datosDelEvaluado'
import { sugerirTipoDeCargo, sugerirConduccion } from './sugerenciasCargo'

// AN-02 · Fase 1 del formato de antecedentes: datos del evaluado, validación y declaración. Los datos que se guardan son los mismos de la plantilla (claves ev_*,
// decN_*): solo cambia cómo se capturan. Lo que llega del link del evaluado se muestra en gris y no se vuelve a pedir.

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const sinTildes = (s) => String(s ?? '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const SIN_DATO = <span className="text-gray-600 italic">Sin dato en el link</span>

// Acceso a una sección por clave: leer y poner un valor por clave de columna.
function acceso(secciones, claveSeccion, onCampo) {
  const s = secciones.find(x => x.clave === claveSeccion && !x.origenFijoClave)
  const col = (clave) => s?.columnas.find(c => c.clave === clave)
  return {
    existe: !!s,
    columna: col,
    val: (clave) => { const c = col(clave); return c ? (s.valores[c.idColumna] ?? '') : '' },
    poner: (clave, v) => { const c = col(clave); if (c) onCampo(s.idSeccion, c.idColumna, v) },
  }
}

// ── Datos del evaluado ────────────────────────────────────────────────────────────────────────────────────────────────

export function DatosEvaluado({ secciones, datosCandidato, cargoSolicitud, criticidad, soloLectura, onCampo, onAvisar }) {
  const datos = acceso(secciones, 'datos_evaluado', onCampo)
  const valid = acceso(secciones, 'validacion_evaluado', onCampo)
  const [corregir, setCorregir] = useState({ dato: '', valor: '' })
  const [avisos, setAvisos] = useState([])
  const [enviando, setEnviando] = useState(false)

  const filas = [
    ...filasDeIdentidad(datosCandidato, datos.val('ev_lugar_exp')),
    ['Cliente', datos.val('ev_cliente')],
    ['Cargo', datos.val('ev_cargo') || cargoSolicitud],
  ]

  const cargo = datos.val('ev_cargo') || cargoSolicitud
  const tipoSugerido = sugerirTipoDeCargo(cargo)
  const conduccionSugerida = sugerirConduccion(cargo)
  const colTipo = valid.columna('ev_tipo_cargo')
  const opcionesTipo = (colTipo?.opciones ?? []).filter(o => o && o !== '—')
  const tipo = valid.val('ev_tipo_cargo')
  const conduccion = valid.val('ev_conduccion')

  const enviarCorreccion = async () => {
    setEnviando(true)
    try {
      const actual = filas.find(f => f[0] === corregir.dato)?.[1] || 'sin dato'
      await onAvisar('ANT_DATO_NO_COINCIDE', `${corregir.dato}: el link dice «${actual}» y el documento dice «${corregir.valor.trim()}». El dato del link no se modificó.`)
      setAvisos(a => [...a, `${corregir.dato}: ${corregir.valor.trim()}`])
      setCorregir({ dato: '', valor: '' })
    } catch { /* el aviso de error ya lo muestra la pantalla */ } finally { setEnviando(false) }
  }

  return (
    <div className="space-y-6">
      <section aria-labelledby="an-datos">
        <h3 id="an-datos" className="text-sm font-bold text-gray-900 mb-2">Datos del evaluado</h3>
        <dl className="grid grid-cols-1 sm:grid-cols-2 border border-gray-300 rounded">
          {filas.map(([e, v]) => (
            <div key={e} className="p-2 border-b border-gray-200 sm:odd:border-r">
              <dt className="text-xs text-gray-600">{e}</dt>
              <dd className="bg-gray-100 rounded px-2 py-1 text-gray-900 break-words min-h-[1.75rem]">{tiene(v) ? v : SIN_DATO}</dd>
            </div>
          ))}
        </dl>
        {!soloLectura && (
          <details className="mt-2 text-sm">
            <summary className="cursor-pointer min-h-11 flex items-center text-primary-700 underline">Corregir y avisar al gestor</summary>
            <div className="mt-2 space-y-2 max-w-md">
              <label htmlFor="an-corr-dato" className="block text-xs font-semibold text-gray-700">¿Qué dato no coincide con el documento?</label>
              <select id="an-corr-dato" className={inputCls} value={corregir.dato} onChange={e => setCorregir(c => ({ ...c, dato: e.target.value }))}>
                <option value="">Selecciona…</option>
                {filas.map(([e]) => <option key={e} value={e}>{e}</option>)}
              </select>
              <label htmlFor="an-corr-valor" className="block text-xs font-semibold text-gray-700">Valor correcto, según el documento</label>
              <input id="an-corr-valor" className={inputCls} value={corregir.valor} onChange={e => setCorregir(c => ({ ...c, valor: e.target.value }))} maxLength={200} />
              <p className="text-xs text-gray-700">El aviso queda en el historial del servicio. El dato que registró el evaluado no se borra.</p>
              <button type="button" disabled={enviando || !corregir.dato || !tiene(corregir.valor)} onClick={enviarCorreccion}
                className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 text-white disabled:opacity-50">{enviando ? 'Enviando…' : 'Enviar aviso'}</button>
            </div>
          </details>
        )}
        {avisos.length > 0 && <ul className="mt-2 text-sm text-green-800" role="status">{avisos.map((a, i) => <li key={i}>✓ Aviso enviado: {a}</li>)}</ul>}
      </section>

      <section aria-labelledby="an-cargo" className="rounded-lg border border-purple-200 bg-purple-50/50 p-4 space-y-4">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 id="an-cargo" className="text-sm font-bold text-purple-900">Datos del cargo</h3>
          <span className="text-xs text-purple-800">No se imprime</span>
        </div>
        <p className="text-sm text-gray-900">Criticidad del cargo: <b>{criticidad || 'sin definir'}</b> <span className="text-xs text-gray-700">(viene de la solicitud)</span></p>

        <div data-clave="ev_tipo_cargo" className="rounded">
          <label htmlFor="an-tipo-cargo" className="block text-xs font-semibold text-gray-700 mb-1">Tipo de cargo <span className="text-red-600">*</span></label>
          <select id="an-tipo-cargo" className={inputCls} value={tipo} disabled={soloLectura} onChange={e => valid.poner('ev_tipo_cargo', e.target.value)}>
            <option value="">Selecciona…</option>
            {opcionesTipo.map(o => <option key={o} value={o}>{o}</option>)}
          </select>
          {tipoSugerido && tipo !== tipoSugerido && !soloLectura && (
            <p className="mt-1 text-sm text-gray-800">
              Sugerido por el cargo: <b>{tipoSugerido}</b>{' '}
              <button type="button" onClick={() => valid.poner('ev_tipo_cargo', tipoSugerido)} className="underline text-primary-700 min-h-11">Usar</button>
            </p>
          )}
        </div>

        <div data-clave="ev_conduccion" className="rounded">
          <p id="an-conduccion" className="text-xs font-semibold text-gray-700 mb-1">¿El cargo implica conducción? <span className="text-red-600">*</span></p>
          <Botones id="an-conduccion" etiqueta="El cargo implica conducción" opciones={['Sí', 'No']} valor={conduccion} deshabilitado={soloLectura}
            onElegir={v => valid.poner('ev_conduccion', v)} />
          <p className="mt-1 text-sm text-gray-800" role="note">
            {conduccionSugerida
              ? <>Sugerido por el cargo: <b>{conduccionSugerida}</b> · confírmalo.</>
              : 'Sin sugerencia para este cargo: elige la respuesta.'}
          </p>
        </div>
      </section>
    </div>
  )
}

// ── Validación del evaluado ───────────────────────────────────────────────────────────────────────────────────────────

export function ValidacionEvaluado({ secciones, datosCandidato, soloLectura, onCampo }) {
  const valid = acceso(secciones, 'validacion_evaluado', onCampo)
  const rut = (datosCandidato ?? []).find(c => c.clave === 'RUT')?.valor
  const opciones = (valid.columna('ev_estado_doc')?.opciones ?? []).filter(o => o && o !== '—')
  return (
    <div className="space-y-5">
      <div data-clave="ev_estado_doc" className="rounded">
        <p id="an-estado-doc" className="text-xs font-semibold text-gray-700 mb-1">Estado del documento <span className="text-red-600">*</span></p>
        <Botones id="an-estado-doc" etiqueta="Estado del documento" opciones={opciones} valor={valid.val('ev_estado_doc')} deshabilitado={soloLectura}
          onElegir={v => valid.poner('ev_estado_doc', v)} />
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <p className="text-xs font-semibold text-gray-700 mb-1">RUT</p>
          <p className="bg-gray-100 rounded px-2 py-2 text-gray-900 break-words">{tiene(rut) ? rut : SIN_DATO}</p>
        </div>
        <div data-clave="ev_rut_actividad" className="rounded">
          <label htmlFor="an-rut-act" className="block text-xs font-semibold text-gray-700 mb-1">Actividad económica (RUT)</label>
          <input id="an-rut-act" className={inputCls} value={valid.val('ev_rut_actividad')} disabled={soloLectura} onChange={e => valid.poner('ev_rut_actividad', e.target.value)} maxLength={300} />
        </div>
      </div>
    </div>
  )
}

// ── Declaración del evaluado ──────────────────────────────────────────────────────────────────────────────────────────

export const PREGUNTAS_DECLARACION = [
  { n: 1, texto: '¿Tiene o ha tenido procesos judiciales? ¿En qué calidad (demandado, denunciado o investigado)?', enLink: /procesos judiciales/ },
  { n: 2, texto: '¿Tiene o ha tenido órdenes de captura, requerimientos o asuntos pendientes con autoridades?', enLink: /ordenes de captura/ },
  { n: 3, texto: '¿Tiene o ha tenido sanciones disciplinarias o fiscales?', enLink: /sanciones disciplinarias/ },
]

// Lo que el evaluado respondió en su link a la pregunta N (null si no la respondió).
export const respuestaDelLink = (declaraciones, n) => {
  const p = PREGUNTAS_DECLARACION.find(x => x.n === n)
  return (declaraciones ?? []).find(d => p.enLink.test(sinTildes(d.pregunta))) ?? null
}

// Escribe en el formulario la respuesta y el detalle que el evaluado dio en su link. Devuelve las secciones nuevas o null si no hay nada que cambiar.
export function precargarDeclaraciones(secciones, declaraciones) {
  let cambio = false
  const nuevas = secciones.map(s => {
    const m = /^declaracion_p([123])$/.exec(s.clave ?? '')
    if (!m || s.origenFijoClave) return s
    const n = Number(m[1])
    const d = respuestaDelLink(declaraciones, n)
    if (!d) return s
    const idR = s.columnas.find(c => c.clave === `dec${n}_respuesta`)?.idColumna
    const idD = s.columnas.find(c => c.clave === `dec${n}_detalle`)?.idColumna
    const valores = { ...s.valores }
    if (idR !== undefined && (valores[idR] ?? '') !== d.respuesta) { valores[idR] = d.respuesta; cambio = true }
    if (idD !== undefined && tiene(d.detalle) && (valores[idD] ?? '') !== d.detalle) { valores[idD] = d.detalle; cambio = true }
    return valores === s.valores ? s : { ...s, valores }
  })
  return cambio ? nuevas : null
}

// Sugerencia del resultado de la verificación, con lo que ya se registró en las consultas. El analista la usa o la edita.
function sugerenciaVerificacion(secciones, n) {
  const resultado = (claveSeccion, colFuente, colRes, patron) => {
    const s = secciones.find(x => x.clave === claveSeccion && !x.origenFijoClave)
    const idF = s?.columnas.find(c => c.clave === colFuente)?.idColumna
    const idR = s?.columnas.find(c => c.clave === colRes)?.idColumna
    const fila = s?.filas.find(f => patron.test(sinTildes(f.valores[idF])))
    return fila ? (fila.valores[idR] ?? '') : ''
  }
  if (n === 1 && resultado('listas_nacionales', 'ln_fuente', 'ln_resultado', /rama judicial/) === 'Sin novedad') return 'Rama Judicial: sin procesos registrados.'
  if (n === 2 && resultado('listas_nacionales', 'ln_fuente', 'ln_resultado', /policia/) === 'Sin novedad') return 'Policía Nacional: sin asuntos pendientes.'
  if (n === 3 && resultado('listas_nacionales', 'ln_fuente', 'ln_resultado', /procuraduria/) === 'Sin novedad'
    && resultado('listas_nacionales', 'ln_fuente', 'ln_resultado', /contraloria/) === 'Sin novedad') return 'Procuraduría y Contraloría: sin registros.'
  return null
}

export function DeclaracionEvaluado({ secciones, declaraciones, soloLectura, onCampo }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border border-gray-300">
        <thead className="bg-gray-100 text-xs text-gray-700">
          <tr>
            <th scope="col" className="text-left p-2 border-b border-gray-300">Pregunta</th>
            <th scope="col" className="text-left p-2 border-b border-gray-300">Respuesta</th>
            <th scope="col" className="text-left p-2 border-b border-gray-300">Detalle declarado</th>
            <th scope="col" className="text-left p-2 border-b border-gray-300">Resultado de la verificación</th>
            <th scope="col" className="text-left p-2 border-b border-gray-300">Coincide</th>
          </tr>
        </thead>
        <tbody>
          {PREGUNTAS_DECLARACION.map(p => {
            const a = acceso(secciones, `declaracion_p${p.n}`, onCampo)
            const enLink = respuestaDelLink(declaraciones, p.n)
            const sugerida = sugerenciaVerificacion(secciones, p.n)
            const verif = a.val(`dec${p.n}_verificacion`)
            return (
              <tr key={p.n} className="border-b border-gray-200 align-top">
                <th scope="row" className="text-left font-normal p-2 w-[28%]"><span className="font-semibold">{p.n}.</span> {p.texto}</th>
                <td className="p-2 min-w-[7rem]" data-clave={`dec${p.n}_respuesta`}>
                  {enLink ? (
                    <span className="inline-block bg-gray-100 rounded px-2 py-1 text-gray-900">{enLink.respuesta}</span>
                  ) : (
                    <div className="space-y-1">
                      <p className="text-xs text-amber-800">Sin respuesta en el link</p>
                      <Botones etiqueta={`Pregunta ${p.n}: respuesta`} opciones={['Sí', 'No']} valor={a.val(`dec${p.n}_respuesta`)} deshabilitado={soloLectura}
                        onElegir={v => a.poner(`dec${p.n}_respuesta`, v)} />
                    </div>
                  )}
                </td>
                <td className="p-2" data-clave={`dec${p.n}_detalle`}>
                  {enLink
                    ? <span className="block bg-gray-100 rounded px-2 py-1 text-gray-900 break-words">{enLink.detalle || a.val(`dec${p.n}_detalle`) || '—'}</span>
                    : <textarea aria-label={`Pregunta ${p.n}: detalle declarado`} rows={2} className={`${inputCls} py-2`} value={a.val(`dec${p.n}_detalle`)} disabled={soloLectura}
                      onChange={e => a.poner(`dec${p.n}_detalle`, e.target.value)} />}
                </td>
                <td className="p-2" data-clave={`dec${p.n}_verificacion`}>
                  <textarea aria-label={`Pregunta ${p.n}: resultado de la verificación`} rows={2} className={`${inputCls} py-2`} value={verif} disabled={soloLectura}
                    onChange={e => a.poner(`dec${p.n}_verificacion`, e.target.value)} />
                  {sugerida && !tiene(verif) && !soloLectura && (
                    <p className="mt-1 text-xs text-gray-800">Sugerido: «{sugerida}»{' '}
                      <button type="button" onClick={() => a.poner(`dec${p.n}_verificacion`, sugerida)} className="underline text-primary-700 min-h-11">Usar</button>
                    </p>
                  )}
                </td>
                <td className="p-2" data-clave={`dec${p.n}_coincide`}>
                  <Botones etiqueta={`Pregunta ${p.n}: coincide`} opciones={['Sí', 'No']} valor={a.val(`dec${p.n}_coincide`)} deshabilitado={soloLectura}
                    onElegir={v => a.poner(`dec${p.n}_coincide`, v)} />
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

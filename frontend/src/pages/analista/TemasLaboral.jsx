import { useMemo, useState } from 'react'
import { NoImprime } from './AreaTexto'
import { filasDeIdentidad } from './datosDelEvaluado'
import { huecosDeInactividad, intervalosDeVinculaciones, fechaATexto } from './reglasLaboral'

// LB-02 · Fase 1 de la verificación laboral: los datos del evaluado (solo lectura, del link y de la solicitud), la experiencia que declaró (solo lectura) y el
// alcance: periodo validado sugerido por el tipo de estudio y línea de tiempo. Se guardan las mismas columnas de siempre (lab_periodo_desde / lab_periodo_hasta, «MM/AAAA»).

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const inputCls = 'min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const SIN_DATO = <span className="text-gray-600 italic">Sin dato</span>
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

function acceso(secciones, claveSeccion, onCampo) {
  const s = secciones.find(x => x.clave === claveSeccion && !x.origenFijoClave)
  const col = (clave) => s?.columnas.find(c => c.clave === clave)
  return {
    val: (clave) => { const c = col(clave); return c ? (s.valores[c.idColumna] ?? '') : '' },
    poner: (clave, v) => { const c = col(clave); if (c) onCampo(s.idSeccion, c.idColumna, v) },
  }
}

// ── Datos del evaluado ──────────────────────────────────────────────────────────────────────────────────────────────────────
export function DatosLaboral({ secciones, datosCandidato, cargoSolicitud, nombreProceso, criticidad, soloLectura, onAvisar, claveNovedad = 'LAB_DATO_NO_COINCIDE' }) {
  const datos = acceso(secciones, 'datos_evaluado', () => {})
  const [corregir, setCorregir] = useState({ dato: '', valor: '' })
  const [avisos, setAvisos] = useState([])
  const [enviando, setEnviando] = useState(false)

  const filas = [
    ...filasDeIdentidad(datosCandidato, datos.val('ev_lugar_exp')),
    ['Cliente', datos.val('ev_cliente')],
    ['Cargo (de la solicitud)', datos.val('ev_cargo') || cargoSolicitud],
    ['Tipo de estudio', nombreProceso],
    ['Criticidad del cargo (de la solicitud)', criticidad],
  ]
  const corregibles = filas.slice(0, 6).map(f => f[0])

  const enviarCorreccion = async () => {
    setEnviando(true)
    try {
      const actual = filas.find(f => f[0] === corregir.dato)?.[1] || 'sin dato'
      await onAvisar(claveNovedad, `${corregir.dato}: el link dice «${actual}» y el documento dice «${corregir.valor.trim()}». El dato del link no se modificó.`)
      setAvisos(a => [...a, `${corregir.dato}: ${corregir.valor.trim()}`])
      setCorregir({ dato: '', valor: '' })
    } catch { /* el aviso de error ya lo muestra la pantalla */ } finally { setEnviando(false) }
  }

  return (
    <section aria-labelledby="lb-datos">
      <h3 id="lb-datos" className="text-sm font-bold text-gray-900 mb-2">1. Datos del evaluado</h3>
      <dl className="grid grid-cols-1 sm:grid-cols-2 border border-gray-300 rounded">
        {filas.map(([e, v]) => (
          <div key={e} className="p-2 border-b border-gray-200 sm:odd:border-r">
            <dt className="text-xs text-gray-600">{e}</dt>
            <dd className="bg-gray-100 rounded px-2 py-1 text-gray-900 break-words min-h-[1.75rem]">{tiene(v) ? v : SIN_DATO}</dd>
          </div>
        ))}
      </dl>
      {!tiene(criticidad) && <p role="alert" className="mt-2 text-sm text-amber-800">La solicitud no trae la criticidad del cargo. Avisa al gestor desde «Novedades».</p>}
      {!soloLectura && (
        <details className="mt-2 text-sm">
          <summary className="cursor-pointer min-h-11 flex items-center text-primary-700 underline">Corregir y avisar al gestor</summary>
          <div className="mt-2 space-y-2 max-w-md">
            <label htmlFor="lb-corr-dato" className="block text-xs font-semibold text-gray-700">¿Qué dato no coincide con el documento?</label>
            <select id="lb-corr-dato" className={`${inputCls} w-full`} value={corregir.dato} onChange={e => setCorregir(c => ({ ...c, dato: e.target.value }))}>
              <option value="">Selecciona…</option>
              {corregibles.map(e => <option key={e} value={e}>{e}</option>)}
            </select>
            <label htmlFor="lb-corr-valor" className="block text-xs font-semibold text-gray-700">Valor correcto, según el documento</label>
            <input id="lb-corr-valor" className={`${inputCls} w-full`} value={corregir.valor} onChange={e => setCorregir(c => ({ ...c, valor: e.target.value }))} maxLength={200} />
            <p className="text-xs text-gray-700">El aviso queda en el historial del servicio. El dato que registró el evaluado no se borra.</p>
            <button type="button" disabled={!corregir.dato || !tiene(corregir.valor) || enviando} onClick={enviarCorreccion}
              className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 text-white disabled:opacity-50">{enviando ? 'Avisando…' : 'Avisar al gestor'}</button>
          </div>
        </details>
      )}
      {avisos.length > 0 && <ul className="mt-2 text-sm text-green-800 list-disc pl-5">{avisos.map((a, i) => <li key={i}>Aviso enviado al gestor · {a}</li>)}</ul>}
    </section>
  )
}

// ── Alcance: periodo validado y línea de tiempo ────────────────────────────────────────────────────────────────────────────
const aMes = (texto) => {   // «MM/AAAA» → { m, a } | null
  const x = /^(\d{2})\/(\d{4})$/.exec(String(texto ?? '').trim())
  return x && +x[1] >= 1 && +x[1] <= 12 ? { m: +x[1], a: +x[2] } : null
}
const deMes = ({ m, a }) => `${String(m).padStart(2, '0')}/${a}`
const aFecha = (texto) => {   // «dd/MM/aaaa» → Date | null
  const x = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(String(texto ?? '').trim())
  return x ? new Date(+x[3], +x[2] - 1, +x[1]) : null
}
const DIA = 86400000
const dias = (a, b) => Math.round((b - a) / DIA)

// Periodo sugerido: «hasta» = el mes de la solicitud; «desde» = ese mismo mes, N años atrás.
export function periodoSugerido(anios, fechaSolicitud) {
  if (!anios || !fechaSolicitud) return null
  const [a, m] = String(fechaSolicitud).split('-').map(Number)
  if (!a || !m) return null
  return { desde: deMes({ m, a: a - anios }), hasta: deMes({ m, a }) }
}

// Barras de la línea de tiempo y huecos de más de 30 días dentro del periodo, con las fechas que declaró el evaluado.
export function lineaDeTiempo(periodo, experiencias, hoy = new Date()) {
  const d = aMes(periodo.desde)
  const h = aMes(periodo.hasta)
  if (!d || !h) return null
  const ini = new Date(d.a, d.m - 1, 1)
  const fin = new Date(h.a, h.m, 0)
  if (fin <= ini) return null
  const total = dias(ini, fin) || 1
  const barras = experiencias.map(e => {
    const i = aFecha(e.fecha_inicio)
    const f = aFecha(e.fecha_fin) ?? hoy
    const dentro = !!i && i <= fin && f >= ini
    const a = dentro ? new Date(Math.max(+i, +ini)) : null
    const b = dentro ? new Date(Math.min(+f, +fin)) : null
    return { id: e.id, empresa: e.empresa, dentro, left: dentro ? dias(ini, a) / total * 100 : 0, ancho: dentro ? Math.max(1, dias(a, b) / total * 100) : 0, a, b }
  })
  const ocupado = barras.filter(x => x.dentro)
  const huecos = huecosDeInactividad(periodo.desde, periodo.hasta, barras.filter(x => x.dentro).map(x => ({ inicio: x.a, fin: x.b })), hoy)
  return { barras, huecos: huecos.map(x => ({ ...x, left: dias(ini, x.desde) / total * 100, ancho: Math.max(1, dias(x.desde, x.hasta) / total * 100) })), dentro: ocupado.length }
}

function SelectorMes({ etiqueta, valor, onChange, disabled, anioMin, anioMax }) {
  const v = aMes(valor)
  const anios = []
  for (let a = anioMax; a >= anioMin; a--) anios.push(a)
  const poner = (m, a) => { if (m && a) onChange(deMes({ m: +m, a: +a })); else if (!m && !a) onChange('') }
  return (
    <fieldset className="min-w-0">
      <legend className="text-xs font-semibold text-gray-700 mb-1">{etiqueta}</legend>
      <div className="flex gap-2">
        <select aria-label={`${etiqueta}: mes`} className={`${inputCls} flex-1`} disabled={disabled} value={v?.m ?? ''} onChange={e => poner(e.target.value, v?.a ?? anioMax)}>
          <option value="">Selecciona…</option>
          {MESES.map((n, i) => <option key={n} value={i + 1}>{n}</option>)}
        </select>
        <select aria-label={`${etiqueta}: año`} className={`${inputCls} w-28`} disabled={disabled} value={v?.a ?? ''} onChange={e => poner(v?.m ?? 1, e.target.value)}>
          <option value="">Año</option>
          {anios.map(a => <option key={a} value={a}>{a}</option>)}
        </select>
      </div>
    </fieldset>
  )
}

export function AlcanceLaboral({ secciones, experiencias, vinculaciones = [], aniosPeriodo, fechaSolicitud, soloLectura, onCampo }) {
  const alc = acceso(secciones, 'alcance', onCampo)
  const sugerido = useMemo(() => periodoSugerido(aniosPeriodo, fechaSolicitud), [aniosPeriodo, fechaSolicitud])
  const desde = alc.val('lab_periodo_desde')
  const hasta = alc.val('lab_periodo_hasta')

  const esSugerido = !!sugerido && desde === sugerido.desde && hasta === sugerido.hasta
  // Con vinculaciones, la línea usa las fechas CONFIRMADAS (LB-04); sin ellas, las que declaró el evaluado.
  const confirmadas = vinculaciones.length > 0
  const fuente = useMemo(() => (confirmadas
    ? intervalosDeVinculaciones(vinculaciones).map(x => ({ id: x.n, empresa: x.empresa, fecha_inicio: fechaATexto(x.inicio), fecha_fin: fechaATexto(x.fin) }))
    : experiencias), [confirmadas, vinculaciones, experiencias])
  const linea = useMemo(() => lineaDeTiempo({ desde, hasta }, fuente), [desde, hasta, fuente])
  const anioActual = new Date().getFullYear()
  const fmt = (d) => d.toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })

  return (
    <div className="space-y-6">
      <section aria-labelledby="lb-alcance" data-clave="lab_periodo_desde">
        <h3 id="lb-alcance" className="text-sm font-bold text-gray-900 mb-2">5. Alcance de la verificación</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <SelectorMes etiqueta="Periodo validado desde" valor={desde} onChange={v => alc.poner('lab_periodo_desde', v)} disabled={soloLectura} anioMin={anioActual - 15} anioMax={anioActual} />
          <div data-clave="lab_periodo_hasta">
            <SelectorMes etiqueta="Periodo validado hasta" valor={hasta} onChange={v => alc.poner('lab_periodo_hasta', v)} disabled={soloLectura} anioMin={anioActual - 15} anioMax={anioActual} />
          </div>
        </div>
        {sugerido && (
          <p className="mt-2 text-xs text-gray-700">
            {esSugerido ? `Sugerido por el tipo de estudio (${aniosPeriodo} años).` : `El tipo de estudio sugiere ${aniosPeriodo} años (${sugerido.desde} a ${sugerido.hasta}).`}
            {' '}Cámbialo solo si el cliente tiene una regla especial.
            {!esSugerido && !soloLectura && (
              <button type="button" className="ml-2 underline text-primary-700 min-h-11" onClick={() => { alc.poner('lab_periodo_desde', sugerido.desde); alc.poner('lab_periodo_hasta', sugerido.hasta) }}>Volver al sugerido</button>
            )}
          </p>
        )}
        {!sugerido && <p className="mt-2 text-xs text-gray-700">Este tipo de estudio no tiene un periodo sugerido: escríbelo según lo que pidió el cliente.</p>}
      </section>

      <section aria-labelledby="lb-linea">
        <div className="flex items-center justify-between gap-2 mb-2">
          <h3 id="lb-linea" className="text-sm font-bold text-gray-900">Línea de tiempo</h3>
          <NoImprime />
        </div>
        {!linea && <p className="text-sm text-gray-700">Elige el periodo validado para ver la línea de tiempo.</p>}
        {linea && (
          <div>
            <div className="relative h-6 bg-gray-100 rounded border border-gray-200" role="img"
              aria-label={`${linea.dentro} vinculaciones dentro del periodo y ${linea.huecos.length} periodos sin vinculación de más de 30 días`}>
              {linea.huecos.map((h, i) => <div key={`h${i}`} className="absolute top-0 h-full bg-red-300/80" style={{ left: `${h.left}%`, width: `${h.ancho}%` }} title={`Sin vinculación: ${fmt(h.desde)} a ${fmt(h.hasta)}`} />)}
              {linea.barras.filter(b => b.dentro).map(b => <div key={b.id} className="absolute top-1 h-4 bg-primary-600 rounded" style={{ left: `${b.left}%`, width: `${b.ancho}%` }} title={b.empresa} />)}
            </div>
            <ul className="mt-2 space-y-1 text-sm">
              {linea.barras.map(b => (
                <li key={b.id} className={b.dentro ? 'text-gray-900' : 'text-gray-500'}>
                  <span aria-hidden="true">{b.dentro ? '▬ ' : '▭ '}</span>{b.empresa || 'Sin nombre'}{b.dentro ? '' : ' · fuera del periodo'}
                </li>
              ))}
              {linea.huecos.map((h, i) => <li key={`t${i}`} className="text-red-700"><span aria-hidden="true">▬ </span>Sin vinculación · {fmt(h.desde)} a {fmt(h.hasta)} ({dias(h.desde, h.hasta)} días)</li>)}
            </ul>
            <p className="mt-2 text-xs text-gray-700">{linea.dentro} vinculaciones dentro del periodo · {linea.huecos.length} periodos de inactividad. {confirmadas ? 'Con las fechas confirmadas de las vinculaciones.' : 'Con las fechas que declaró el evaluado.'}</p>
          </div>
        )}
      </section>
    </div>
  )
}

import { useState, useEffect, useCallback } from 'react'
import riesgoService from '../services/riesgoService'

// Pestaña "Riesgo" del detalle de la solicitud (gestor): la recomendación del motor con su
// detalle interno, la criticidad del caso y la decisión final del gestor (docs/
// modelo-calificacion-riesgo.md §5–§7). El cliente solo verá el nivel decidido y el puntaje final.

const NIVEL = {
  BAJO:  { clase: 'bg-green-100 text-green-800 ring-green-200', punto: 'bg-green-500', texto: 'text-green-700' },
  MEDIO: { clase: 'bg-amber-100 text-amber-800 ring-amber-200', punto: 'bg-amber-500', texto: 'text-amber-700' },
  ALTO:  { clase: 'bg-red-100 text-red-800 ring-red-200',       punto: 'bg-red-500',   texto: 'text-red-700' },
}
const CRITICIDADES = [
  { valor: 'ESTANDAR', etiqueta: 'Estándar' },
  { valor: 'SENSIBLE', etiqueta: 'Sensible' },
  { valor: 'CRITICO', etiqueta: 'Crítico' },
]
const ETIQUETA_CRITICIDAD = Object.fromEntries(CRITICIDADES.map(c => [c.valor, c.etiqueta]))
const ESTADO_SUB = {
  REALIZADO:    { texto: 'Realizado',    clase: 'text-green-700' },
  NO_REALIZADO: { texto: 'No realizado', clase: 'text-gray-500' },
  PENDIENTE:    { texto: 'Pendiente',    clase: 'text-amber-600' },
}
const AJUSTE = {
  PISO: 'Subió a MEDIO por regla de piso',
  CRITICO: 'Llevado a ALTO por una respuesta crítica',
}
const REGLA = { REGLA_A: 'Regla A', REGLA_B: 'Regla B', CRITICO: 'Crítico' }

const num = (v, dec = 1) => v == null ? '—' : Number(v).toLocaleString('es-CO', { minimumFractionDigits: dec, maximumFractionDigits: dec })
const pct = (v) => v == null ? '—' : `${Number(v).toLocaleString('es-CO', { maximumFractionDigits: 2 })} %`
const fecha = (iso) => iso ? new Date(iso).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : ''

export function ChipNivel({ nivel, grande }) {
  if (!nivel) return <span className="text-gray-400">—</span>
  const c = NIVEL[nivel] ?? NIVEL.MEDIO
  return (
    <span className={`inline-flex items-center gap-1.5 font-bold rounded-full ring-1 ${c.clase} ${grande ? 'text-sm px-3 py-1' : 'text-[11px] px-2 py-0.5'}`}>
      <span className={`rounded-full ${c.punto} ${grande ? 'w-2 h-2' : 'w-1.5 h-1.5'}`} />{nivel}
    </span>
  )
}

function EstadoSinResultado({ d }) {
  const textos = {
    EN_PROCESO: ['En proceso', 'Todavía hay subprocesos pendientes. Abajo ves el puntaje de los que ya se realizaron.'],
    SIN_INFORMACION: ['Sin información', 'No se realizó ningún subproceso con peso — revisa el caso manualmente.'],
    NO_CALIFICA: ['No califica', d.motivosRevision?.[0] ?? 'Los pesos del proceso no están completos en el catálogo.'],
  }
  const [titulo, detalle] = textos[d.estado] ?? ['Sin cálculo', '']
  return (
    <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-4">
      <p className="text-sm font-semibold text-gray-700">{titulo}</p>
      <p className="text-xs text-gray-500 mt-0.5">{detalle}</p>
    </div>
  )
}

export default function RiesgoServicio({ idServicio, onToast }) {
  const [d, setD] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [editando, setEditando] = useState(false)
  const [nivelCambio, setNivelCambio] = useState('ALTO')
  const [justificacion, setJustificacion] = useState('')
  const [verHistorial, setVerHistorial] = useState(false)

  const aplicar = (data) => {
    setD(data)
    setEditando(false)
    setNivelCambio(data.cambiadoPorGestor ? data.nivelDecidido : (data.nivelFinal === 'ALTO' ? 'MEDIO' : 'ALTO'))
    setJustificacion(data.justificacionDecision ?? '')
  }

  const cargar = useCallback(async () => {
    setCargando(true); setError(null)
    try { aplicar(await riesgoService.obtener(idServicio)) }
    catch (err) { setError(err.response?.data?.mensaje ?? 'No se pudo cargar el riesgo del caso.') }
    finally { setCargando(false) }
  }, [idServicio])
  useEffect(() => { cargar() }, [cargar])

  const cambiarCriticidad = async (valor) => {
    setGuardando(true); setError(null)
    try {
      aplicar(await riesgoService.cambiarCriticidad(idServicio, valor || null))
      onToast?.('Criticidad actualizada — el riesgo se recalculó.')
    } catch (err) { setError(err.response?.data?.mensaje ?? 'No se pudo cambiar la criticidad.') }
    finally { setGuardando(false) }
  }

  // nivel null = volver al automático (vale la recomendación del motor)
  const guardarDecision = async (nivel) => {
    setGuardando(true); setError(null)
    try {
      aplicar(await riesgoService.decidir(idServicio, nivel, nivel ? justificacion : null))
      onToast?.(nivel ? `Nivel cambiado a ${nivel}` : 'Volvió al nivel automático')
    } catch (err) { setError(err.response?.data?.mensaje ?? 'No se pudo guardar el nivel.') }
    finally { setGuardando(false) }
  }

  if (cargando) return <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-7 w-7 border-4 border-primary-600 border-t-transparent" /></div>
  if (!d) return <p className="text-sm text-red-600">{error}</p>

  const calculado = d.estado === 'CALCULADO'
  const difiere = nivelCambio !== d.nivelFinal
  const faltaJustificacion = difiere && !justificacion.trim()

  return (
    <div className="space-y-4">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
      {!d.procesoCalifica && (
        <div className="bg-amber-50 border border-amber-200 text-amber-800 text-xs px-3 py-2.5 rounded-lg">
          El proceso <strong>{d.nombreProceso}</strong> todavía no califica riesgo ({d.motivoProcesoNoCalifica}). Configura sus pesos en
          Catálogo → Subprocesos. Mientras tanto se puede publicar sin decisión.
        </div>
      )}

      {/* Recomendación del motor */}
      <section className="rounded-xl border border-gray-200 overflow-hidden">
        <div className="px-4 py-3 bg-gray-50 border-b border-gray-100 flex items-center justify-between gap-3 flex-wrap">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Recomendación del motor</p>
          <div className="flex items-center gap-2 text-xs">
            <span className="text-gray-400">Criticidad del caso</span>
            <select value={d.criticidadAjustada ? d.criticidad : ''} disabled={!d.puedeCambiarCriticidad || guardando}
              onChange={e => cambiarCriticidad(e.target.value)}
              className="border border-gray-200 rounded-lg px-2 py-1 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-gray-50">
              <option value="">{`${ETIQUETA_CRITICIDAD[d.criticidadProceso] ?? 'Estándar'} (del proceso)`}</option>
              {CRITICIDADES.map(c => <option key={c.valor} value={c.valor}>{c.etiqueta}</option>)}
            </select>
          </div>
        </div>
        <div className="px-4 py-4">
          {calculado ? (
            <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
              <div>
                <p className={`text-3xl font-bold leading-none ${NIVEL[d.nivelFinal]?.texto ?? 'text-gray-800'}`}>{num(d.puntajeFinal)}</p>
                <p className="text-[11px] text-gray-400 mt-1">puntaje final</p>
              </div>
              <ChipNivel nivel={d.nivelFinal} grande />
              <div className="text-xs text-gray-500 space-y-0.5">
                {d.tipoAjuste && d.tipoAjuste !== 'NINGUNO' && (
                  <p className="font-medium text-gray-700">{AJUSTE[d.tipoAjuste]} <span className="text-gray-400">(promedio sin ajustar: {num(d.puntajePonderado)})</span></p>
                )}
                <p>Cobertura {pct(d.cobertura)} <span className="text-gray-400">(mínima {pct(d.coberturaMinima)})</span></p>
              </div>
            </div>
          ) : <EstadoSinResultado d={d} />}

          {d.motivosAjuste?.length > 0 && (
            <ul className="mt-3 space-y-1">
              {d.motivosAjuste.map((m, i) => (
                <li key={i} className="text-xs text-gray-600 flex gap-2">
                  <span className="flex-shrink-0 font-semibold text-gray-500">{REGLA[m.regla] ?? m.regla}</span>
                  <span className="min-w-0"><span className="font-medium">{m.servicio}</span> — {m.detalle}</span>
                </li>
              ))}
            </ul>
          )}
          {calculado && d.requiereRevision && (
            <div className="mt-3 rounded-lg bg-amber-50 border border-amber-200 px-3 py-2">
              <p className="text-xs font-semibold text-amber-800">Requiere revisión manual</p>
              <ul className="mt-0.5 text-xs text-amber-700 list-disc pl-4">{d.motivosRevision.map((m, i) => <li key={i}>{m}</li>)}</ul>
            </div>
          )}
        </div>

        {/* Detalle por subproceso */}
        {d.subprocesos?.length > 0 && (
          <div className="overflow-x-auto border-t border-gray-100">
            <table className="min-w-full text-xs">
              <thead className="bg-gray-50 text-gray-500">
                <tr>
                  {['Subproceso', 'Estado', 'Peso', 'Efectivo', 'Puntos', 'Nivel'].map(h => (
                    <th key={h} className={`px-3 py-2 font-semibold ${h === 'Subproceso' || h === 'Estado' ? 'text-left' : 'text-right'}`}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {d.subprocesos.map(s => (
                  <tr key={s.codigo} className={s.estado !== 'REALIZADO' ? 'text-gray-400' : 'text-gray-700'}>
                    <td className="px-3 py-2 font-medium">
                      {s.nombre}
                      {s.criticos?.length > 0 && <span className="ml-1.5 text-[10px] font-bold text-red-600" title={s.criticos.map(c => c.descripcion).join('\n')}>CRÍTICO</span>}
                    </td>
                    <td className={`px-3 py-2 ${ESTADO_SUB[s.estado]?.clase ?? ''}`}>{ESTADO_SUB[s.estado]?.texto ?? s.estado}</td>
                    <td className="px-3 py-2 text-right">{pct(s.peso)}</td>
                    <td className="px-3 py-2 text-right">{s.pesoEfectivo != null ? pct(s.pesoEfectivo) : '—'}</td>
                    <td className="px-3 py-2 text-right font-semibold">{s.puntajeFinal != null ? num(s.puntajeFinal) : '—'}</td>
                    <td className="px-3 py-2 text-right">{s.nivel ? <ChipNivel nivel={s.nivel} /> : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="px-4 py-2 border-t border-gray-100 text-[10px] text-gray-400">
          {d.fechaCalculo && <>Calculado {fecha(d.fechaCalculo)} · </>}configuración {d.versionConfiguracion ?? '—'} · se recalcula solo al guardar formularios o cambiar estados
        </p>
      </section>

      {/* Nivel final: automático, con opción de cambiarlo */}
      <section className="rounded-xl border border-primary-100 overflow-hidden">
        <div className="px-4 py-3 bg-primary-50/60 border-b border-primary-100 flex items-center justify-between gap-2 flex-wrap">
          <div>
            <p className="text-xs font-semibold text-primary-700 uppercase tracking-wider">Nivel final del informe</p>
            <p className="text-[11px] text-primary-600/80">Es el que verá el cliente. Sale automático del motor; puedes cambiarlo si lo necesitas.</p>
          </div>
          {d.bloqueado && <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-gray-800 text-white">🔒 Publicado — bloqueado</span>}
        </div>
        <div className="px-4 py-4 space-y-3">
          {!calculado ? (
            <p className="text-xs text-gray-500">Aparecerá cuando el motor tenga un resultado ({d.estado === 'EN_PROCESO' ? 'faltan subprocesos por terminar' : 'revisa la configuración del proceso'}).</p>
          ) : (
            <>
              <div className="flex flex-wrap items-center gap-3">
                <ChipNivel nivel={d.nivelEfectivo} grande />
                {d.cambiadoPorGestor ? (
                  <div className="text-xs text-gray-600">
                    <p><span className="font-semibold text-amber-700">Cambiado manualmente</span> — el motor recomendaba <ChipNivel nivel={d.nivelFinal} /></p>
                    <p className="text-gray-400 mt-0.5">por {d.decididoPorNombre} · {fecha(d.fechaDecision)}</p>
                    {d.justificacionDecision && <p className="text-gray-500 italic mt-0.5">"{d.justificacionDecision}"</p>}
                  </div>
                ) : (
                  <p className="text-xs text-gray-500"><span className="font-semibold text-green-700">Automático</span> — sigue la recomendación del motor.</p>
                )}
              </div>

              {d.puedeDecidir && !editando && (
                <div className="flex flex-wrap gap-2">
                  <button type="button" onClick={() => setEditando(true)}
                    className="text-xs font-medium px-3 py-1.5 rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-50">
                    Cambiar nivel
                  </button>
                  {d.cambiadoPorGestor && (
                    <button type="button" onClick={() => guardarDecision(null)} disabled={guardando}
                      className="text-xs font-medium px-3 py-1.5 rounded-lg border border-green-200 text-green-700 hover:bg-green-50 disabled:opacity-50">
                      Volver al automático ({d.nivelFinal})
                    </button>
                  )}
                </div>
              )}

              {d.puedeDecidir && editando && (
                <div className="rounded-lg border border-gray-200 bg-gray-50 px-3 py-3 space-y-2.5">
                  <label className="flex items-center gap-2 text-sm text-gray-700 flex-wrap">
                    Cambiar a
                    <select value={nivelCambio} onChange={e => setNivelCambio(e.target.value)}
                      className="border border-gray-200 rounded-lg px-2 py-1 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
                      {['BAJO', 'MEDIO', 'ALTO'].filter(n => n !== d.nivelFinal).map(n => <option key={n} value={n}>{n}</option>)}
                    </select>
                  </label>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Justificación <span className="text-red-500">*</span></label>
                    <textarea value={justificacion} onChange={e => setJustificacion(e.target.value)} rows={2} maxLength={2000}
                      placeholder="¿Por qué cambias el nivel que recomienda el motor?"
                      className={`w-full border rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500 ${faltaJustificacion ? 'border-red-300' : 'border-gray-200'}`} />
                  </div>
                  <div className="flex justify-end gap-2">
                    <button type="button" onClick={() => setEditando(false)}
                      className="text-xs border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 px-3 py-1.5 rounded-lg">
                      Cancelar
                    </button>
                    <button type="button" onClick={() => guardarDecision(nivelCambio)} disabled={guardando || faltaJustificacion}
                      className="text-xs bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white font-medium px-3 py-1.5 rounded-lg">
                      {guardando ? 'Guardando…' : `Cambiar a ${nivelCambio}`}
                    </button>
                  </div>
                </div>
              )}
              {!d.puedeDecidir && !d.bloqueado && <p className="text-[11px] text-gray-400">Solo el gestor del cliente puede cambiar el nivel.</p>}
            </>
          )}
        </div>
      </section>

      {/* Historial */}
      {d.historial?.length > 1 && (
        <section>
          <button type="button" onClick={() => setVerHistorial(v => !v)} className="text-xs font-medium text-gray-500 hover:text-gray-700">
            {verHistorial ? '▾' : '▸'} Historial de cálculos ({d.historial.length})
          </button>
          {verHistorial && (
            <ul className="mt-2 divide-y divide-gray-100 border border-gray-100 rounded-lg">
              {d.historial.map((h, i) => (
                <li key={h.id} className={`px-3 py-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs ${i === 0 ? 'bg-primary-50/40' : ''}`}>
                  <span className="text-gray-400 w-36">{fecha(h.fechaCalculo)}</span>
                  <span className="font-semibold text-gray-700 w-10 text-right">{h.puntajeFinal != null ? num(h.puntajeFinal) : '—'}</span>
                  {h.nivelFinal ? <ChipNivel nivel={h.nivelFinal} /> : <span className="text-gray-400">{h.estado}</span>}
                  {h.nivelDecidido && <span className="text-gray-500">decidido <strong>{h.nivelDecidido}</strong></span>}
                  {i === 0 && <span className="text-[10px] text-primary-600 font-semibold">vigente</span>}
                  {h.calculadoPorNombre && <span className="text-gray-400 ml-auto">{h.calculadoPorNombre}</span>}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  )
}

import { useState, useEffect, useCallback } from 'react'
import evaluadoService from '../../services/evaluadoService'

// "Mis citas" del portal del candidato: sus poligrafías y visitas con estado, fecha, hora,
// profesional, lugar (con mapa) y duración, y un botón para proponerle al programador cuándo
// tiene tiempo. Diseño tipo "tarjeta de cita": bloque de fecha a la izquierda, detalle a la derecha.

const ESTADO = {
  POR_PROGRAMAR:   { etiqueta: 'Por programar',   panel: 'bg-slate-600',   chip: 'bg-white/15 text-white' },
  PROGRAMADA:      { etiqueta: 'Programada',      panel: 'bg-primary-600', chip: 'bg-white/15 text-white' },
  CONFIRMADA:      { etiqueta: 'Confirmada',      panel: 'bg-emerald-600', chip: 'bg-white/15 text-white' },
  POR_REPROGRAMAR: { etiqueta: 'Por reprogramar', panel: 'bg-amber-500',   chip: 'bg-white/20 text-white' },
  REALIZADA:       { etiqueta: 'Realizada',       panel: 'bg-gray-400',    chip: 'bg-white/20 text-white' },
  CANCELADA:       { etiqueta: 'Cancelada',       panel: 'bg-red-500',     chip: 'bg-white/20 text-white' },
  // Ya pasó la hora pero el equipo todavía no la marca realizada: no es "confirmada" para el candidato.
  PASADA:          { etiqueta: 'Fecha pasada · en revisión', panel: 'bg-slate-500', chip: 'bg-white/20 text-white' },
}

// Una cita programada cuya hora ya pasó (con 2 h de margen) y que nadie cerró todavía.
export const yaPaso = (c) => !!c.fechaProgramada && !['REALIZADA', 'CANCELADA'].includes(c.estado)
  && new Date(c.fechaProgramada).getTime() < Date.now() - 2 * 3600e3
const FRANJAS = [
  { valor: 'MANANA', etiqueta: 'Mañana (7 a.m. – 12 m.)' },
  { valor: 'TARDE', etiqueta: 'Tarde (12 m. – 6 p.m.)' },
  { valor: 'CUALQUIERA', etiqueta: 'Cualquier hora' },
]
const ETIQUETA_FRANJA = { MANANA: 'en la mañana', TARDE: 'en la tarde', CUALQUIERA: 'a cualquier hora' }

const fecha = (iso) => new Date(iso)
const diaSemana = (iso) => fecha(iso).toLocaleDateString('es-CO', { weekday: 'long' })
const diaMes = (iso) => fecha(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
const hora = (iso) => fecha(iso).toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' })
const fechaCorta = (yyyyMmDd) => new Date(`${yyyyMmDd}T12:00:00`).toLocaleDateString('es-CO', { weekday: 'short', day: 'numeric', month: 'short' })
const hoyIso = () => new Date().toISOString().slice(0, 10)
const masDiasIso = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return d.toISOString().slice(0, 10) }

function duracionTexto(min) {
  if (!min) return null
  if (min < 60) return `${min} minutos`
  const h = Math.floor(min / 60), m = min % 60
  return `${h} hora${h > 1 ? 's' : ''}${m ? ` ${m} min` : ''}`
}

function mapaUrl(lugar) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(lugar)}`
}

const esVirtual = (lugar) => /virtual|videollamada|en l[ií]nea|online/i.test(lugar ?? '')

/* ── Íconos ── */
const Icono = {
  poligrafia: (c) => <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.6}><path strokeLinecap="round" strokeLinejoin="round" d="M3.75 13.5l3-3 3 4.5 3-9 3 7.5 1.5-3h3" /></svg>,
  visita: (c) => <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.6}><path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12l8.954-8.955a1.126 1.126 0 011.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25" /></svg>,
  lugar: (c) => <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.6}><path strokeLinecap="round" strokeLinejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z" /><path strokeLinecap="round" strokeLinejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z" /></svg>,
  reloj: (c) => <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.6}><path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>,
  persona: (c) => <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.6}><path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" /></svg>,
  video: (c) => <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.6}><path strokeLinecap="round" d="M15.75 10.5l4.72-4.72a.75.75 0 011.28.53v11.38a.75.75 0 01-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 002.25-2.25v-9a2.25 2.25 0 00-2.25-2.25h-9A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z" /></svg>,
  calendario: (c) => <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.6}><path strokeLinecap="round" strokeLinejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5" /></svg>,
}

function Linea({ icono, children }) {
  return (
    <div className="flex items-start gap-2.5 text-sm text-gray-600">
      <span className="mt-0.5 shrink-0 text-gray-400">{icono}</span>
      <div className="min-w-0">{children}</div>
    </div>
  )
}

function TarjetaCita({ cita, onSugerir }) {
  const pasada = yaPaso(cita)
  const est = pasada ? ESTADO.PASADA : (ESTADO[cita.estado] ?? ESTADO.PROGRAMADA)
  const programada = !!cita.fechaProgramada
  const visita = cita.tipo === 'VISITA'
  const apagada = cita.estado === 'REALIZADA' || cita.estado === 'CANCELADA' || pasada

  return (
    <article className={`overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm ${apagada ? 'opacity-75' : ''}`}>
      <div className="flex flex-col sm:flex-row">
        {/* Bloque de fecha */}
        <div className={`${est.panel} text-white sm:w-44 shrink-0 px-5 py-4 flex sm:flex-col sm:justify-center items-center sm:items-start gap-x-4 gap-y-1`}>
          <span className={`inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${est.chip}`}>{est.etiqueta}</span>
          {programada ? (
            <div className="sm:mt-2">
              <p className="text-sm font-medium capitalize opacity-90">{diaSemana(cita.fechaProgramada)}</p>
              <p className="text-lg font-bold leading-tight">{diaMes(cita.fechaProgramada)}</p>
              <p className="text-2xl font-extrabold tracking-tight">{hora(cita.fechaProgramada)}</p>
            </div>
          ) : (
            <p className="sm:mt-2 text-sm font-medium opacity-90">Fecha por definir</p>
          )}
        </div>

        {/* Detalle */}
        <div className="flex-1 px-5 py-4 space-y-3">
          <div className="flex items-start gap-3">
            <div className={`shrink-0 w-11 h-11 rounded-xl flex items-center justify-center ${visita ? 'bg-sky-50 text-sky-600' : 'bg-violet-50 text-violet-600'}`}>
              {visita ? Icono.visita('w-6 h-6') : Icono.poligrafia('w-6 h-6')}
            </div>
            <div className="min-w-0">
              <p className="text-base font-semibold text-gray-900">{cita.nombre}</p>
              <p className="text-xs text-gray-500">{visita ? 'Visita domiciliaria' : 'Prueba de poligrafía'}</p>
            </div>
          </div>

          <div className="space-y-2">
            {cita.profesional && (
              <Linea icono={Icono.persona('w-4 h-4')}>
                <span className="text-gray-500">{visita ? 'Visitador: ' : 'Poligrafista: '}</span>
                <span className="font-medium text-gray-800">{cita.profesional}</span>
              </Linea>
            )}
            {cita.lugar && (esVirtual(cita.lugar) ? (
              <Linea icono={Icono.video('w-4 h-4')}><span className="font-medium text-gray-800">{cita.lugar}</span></Linea>
            ) : (
              <Linea icono={Icono.lugar('w-4 h-4')}>
                <a href={mapaUrl(cita.lugar)} target="_blank" rel="noreferrer"
                  className="font-medium text-primary-700 hover:underline break-words">
                  {cita.lugar}
                </a>
                <span className="block text-[11px] text-gray-400">Abrir en Google Maps</span>
              </Linea>
            ))}
            {duracionTexto(cita.duracionMinutos) && (
              <Linea icono={Icono.reloj('w-4 h-4')}>
                Duración aproximada: <span className="font-medium text-gray-800">{duracionTexto(cita.duracionMinutos)}</span>
              </Linea>
            )}
            {programada && !apagada && !visita && (
              <p className="text-xs text-gray-500 pl-6">Llega 10 minutos antes con tu documento de identidad original.</p>
            )}
            {programada && !apagada && visita && (
              <p className="text-xs text-gray-500 pl-6">Te visitaremos en esta dirección. Ten a mano tu documento de identidad.</p>
            )}
            {!programada && !apagada && (
              <p className="text-xs text-gray-500">
                Todavía no tiene fecha. Cuéntanos cuándo tienes tiempo y el equipo lo tendrá en cuenta al programarla.
              </p>
            )}
          </div>

          {cita.sugerenciaPendiente && (
            <div className="rounded-lg bg-primary-50/70 border border-primary-100 px-3 py-2.5 text-xs text-primary-900">
              <p className="font-semibold">Enviaste tu disponibilidad</p>
              <p className="mt-0.5">
                {cita.sugerenciaPendiente.opciones.map(o => `${fechaCorta(o.fecha)} ${ETIQUETA_FRANJA[o.franja]}`).join(' · ')}
              </p>
              {cita.sugerenciaPendiente.comentario && <p className="mt-0.5 italic">"{cita.sugerenciaPendiente.comentario}"</p>}
            </div>
          )}

          {/* Solo sin fecha asignada (o por reprogramar): una cita ya programada no admite propuestas. */}
          {cita.puedeSugerir && !(programada && cita.estado !== 'POR_REPROGRAMAR') && (
            <div className="pt-1">
              <button type="button" onClick={() => onSugerir(cita)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-primary-200 bg-white px-3 py-1.5 text-xs font-semibold text-primary-700 hover:bg-primary-50 transition-colors">
                {Icono.calendario('w-4 h-4')}
                {cita.sugerenciaPendiente ? 'Cambiar mi disponibilidad' : 'Proponer cuándo tengo tiempo'}
              </button>
            </div>
          )}
        </div>
      </div>
    </article>
  )
}

function ModalSugerir({ cita, onCerrar, onEnviar }) {
  const [opciones, setOpciones] = useState([{ fecha: '', franja: 'MANANA' }])
  const [comentario, setComentario] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  const cambiar = (i, campo, valor) => setOpciones(prev => prev.map((o, idx) => idx === i ? { ...o, [campo]: valor } : o))

  const enviar = async () => {
    const validas = opciones.filter(o => o.fecha)
    if (validas.length === 0) { setError('Elige al menos un día.'); return }
    setEnviando(true)
    setError(null)
    try {
      await onEnviar({ opciones: validas, comentario: comentario.trim() || null })
    } catch (e) {
      setError(e.response?.data?.mensaje ?? Object.values(e.response?.data?.errores ?? {})[0] ?? 'No se pudo enviar. Intenta de nuevo.')
      setEnviando(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onCerrar} />
      <div className="relative w-full sm:max-w-lg bg-white rounded-t-2xl sm:rounded-2xl shadow-xl">
        <div className="px-6 pt-5 pb-3 border-b border-gray-100">
          <h3 className="text-base font-semibold text-gray-900">¿Cuándo tienes tiempo?</h3>
          <p className="text-sm text-gray-500 mt-0.5">{cita.nombre} — propón hasta 3 días. El equipo de programación lo tendrá en cuenta.</p>
        </div>
        <div className="px-6 py-4 space-y-3">
          {opciones.map((o, i) => (
            <div key={i} className="flex flex-col sm:flex-row gap-2">
              <input type="date" aria-label={`Día de la opción ${i + 1}`} value={o.fecha} min={hoyIso()} max={masDiasIso(60)}
                onChange={(e) => cambiar(i, 'fecha', e.target.value)}
                className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" />
              <select aria-label={`Franja horaria de la opción ${i + 1}`} value={o.franja} onChange={(e) => cambiar(i, 'franja', e.target.value)}
                className="sm:w-56 border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:ring-2 focus:ring-primary-500 focus:outline-none">
                {FRANJAS.map(f => <option key={f.valor} value={f.valor}>{f.etiqueta}</option>)}
              </select>
              {opciones.length > 1 && (
                <button type="button" onClick={() => setOpciones(prev => prev.filter((_, idx) => idx !== i))}
                  className="text-xs text-gray-400 hover:text-red-600 sm:px-1">Quitar</button>
              )}
            </div>
          ))}
          {opciones.length < 3 && (
            <button type="button" onClick={() => setOpciones(prev => [...prev, { fecha: '', franja: 'MANANA' }])}
              className="text-xs font-semibold text-primary-700 hover:underline">+ Agregar otro día</button>
          )}
          <textarea aria-label="Nota opcional (ej. después de las 3 p.m. salgo del trabajo)" value={comentario} onChange={(e) => setComentario(e.target.value)} maxLength={500} rows={3}
            placeholder="Nota opcional (ej. después de las 3 p.m. salgo del trabajo)"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none resize-none" />
          {error && <p className="text-sm text-red-600">{error}</p>}
        </div>
        <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-2">
          <button type="button" onClick={onCerrar} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50">Cancelar</button>
          <button type="button" onClick={enviar} disabled={enviando}
            className="px-4 py-2 text-sm font-semibold text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50">
            {enviando ? 'Enviando…' : 'Enviar disponibilidad'}
          </button>
        </div>
      </div>
    </div>
  )
}

export function proximaCita(citas) {
  const ahora = Date.now()
  return (citas ?? [])
    .filter(c => c.fechaProgramada && !['REALIZADA', 'CANCELADA'].includes(c.estado) && new Date(c.fechaProgramada).getTime() >= ahora - 2 * 3600e3)
    .sort((a, b) => new Date(a.fechaProgramada) - new Date(b.fechaProgramada))[0] ?? null
}

export default function CitasCandidato({ token, citas, onCambio }) {
  const [sugiriendo, setSugiriendo] = useState(null)
  const [aviso, setAviso] = useState(null)

  const enviar = useCallback(async (data) => {
    await evaluadoService.sugerirHorario(token, sugiriendo.id, data)
    setSugiriendo(null)
    setAviso('¡Listo! Enviamos tu disponibilidad al equipo de programación.')
    onCambio?.()
  }, [token, sugiriendo, onCambio])

  useEffect(() => {
    if (!aviso) return undefined
    const t = setTimeout(() => setAviso(null), 5000)
    return () => clearTimeout(t)
  }, [aviso])

  if (citas == null) {
    return <div className="flex justify-center py-10"><div className="h-6 w-6 animate-spin rounded-full border-2 border-primary-500 border-t-transparent" /></div>
  }
  if (citas.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-gray-300 bg-white px-6 py-10 text-center">
        <div className="mx-auto mb-3 w-12 h-12 rounded-full bg-gray-50 text-gray-400 flex items-center justify-center">{Icono.calendario('w-6 h-6')}</div>
        <p className="text-sm font-medium text-gray-700">Este estudio no incluye citas presenciales</p>
        <p className="text-xs text-gray-500 mt-1">No tienes poligrafía ni visita domiciliaria programadas.</p>
      </div>
    )
  }

  const proxima = proximaCita(citas)
  const otras = citas.filter(c => c !== proxima)

  return (
    <div className="space-y-6">
      {aviso && <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{aviso}</div>}

      {proxima && (
        <section>
          <h3 className="mb-3 text-sm font-semibold text-gray-900">Tu próxima cita</h3>
          <TarjetaCita cita={proxima} onSugerir={setSugiriendo} />
        </section>
      )}

      {otras.length > 0 && (
        <section>
          <h3 className="mb-3 text-sm font-semibold text-gray-900">{proxima ? 'Otras citas del estudio' : 'Citas del estudio'}</h3>
          <div className="space-y-4">
            {otras.map(c => <TarjetaCita key={c.id} cita={c} onSugerir={setSugiriendo} />)}
          </div>
        </section>
      )}

      {sugiriendo && <ModalSugerir cita={sugiriendo} onCerrar={() => setSugiriendo(null)} onEnviar={enviar} />}
    </div>
  )
}

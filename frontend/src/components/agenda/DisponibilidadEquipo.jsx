import { useCallback, useEffect, useId, useMemo, useState } from 'react'
import agendaService from '../../services/agendaService'
import asignacionService from '../../services/asignacionService'
import { useCambios } from '../../hooks/useCambios'
import { Modal } from '../ui/Modal'
import { hoyLocalDateString, parseFecha, toLocalDateString, toLocalDateTimeString } from '../../utils/fechas'

// Disponibilidad del equipo (SE-08):
//  · «Equipo hoy»: TODAS las personas de su equipo (no solo las que ya tienen trabajo esta semana), cada una con sus horas ocupadas y libres de
//    la jornada (7:00 a. m. – 6:00 p. m.) y sus atrasados en agenda, con filtro por rol.
//  · Al elegir a una persona se ve su día por bloques: ocupado (subproceso, evaluado, servicio, duración del catálogo), libre (con «Asignar aquí»)
//    y bloqueado (almuerzo o desplazamiento). Los tiempos los calcula el servidor.

const ROL_ETIQUETA = { POLIGRAFISTA: 'Poligrafista', VISITADOR: 'Visitador', ANALISTA_INTERNO: 'Analista' }
const hora = (iso) => parseFecha(iso).toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' })
const duracion = (min) => {
  const h = Math.floor(min / 60), m = min % 60
  return h && m ? `${h} h ${m} min` : h ? `${h} h` : `${m} min`
}
const diaLargo = (diaStr) => {
  const [y, m, d] = diaStr.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' })
}
const botonNav = 'w-8 h-8 flex items-center justify-center rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 transition-colors flex-shrink-0'

function BarraDia({ ocupado, bloqueado, libre }) {
  const total = ocupado + bloqueado + libre || 1
  const pct = (v) => `${(v / total) * 100}%`
  return (
    <div className="flex h-2 rounded-full overflow-hidden bg-gray-100" role="img"
      aria-label={`Ocupado ${duracion(ocupado)}, libre ${duracion(libre)}, bloqueado ${duracion(bloqueado)}`}>
      <div className="bg-indigo-500" style={{ width: pct(ocupado) }} />
      <div className="bg-gray-400" style={{ width: pct(bloqueado) }} />
      <div className="bg-green-500" style={{ width: pct(libre) }} />
    </div>
  )
}

function TarjetaPersona({ p, onElegir }) {
  return (
    <button type="button" onClick={() => onElegir(p.idUsuario)}
      className="text-left bg-white rounded-xl border border-gray-200 shadow-sm p-4 hover:shadow-md hover:border-primary-300 transition-all">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-gray-900 truncate">{p.nombre} {p.apellido ?? ''}</p>
          <p className="text-xs text-gray-600">{ROL_ETIQUETA[p.rol] ?? p.rol}</p>
        </div>
        {p.atrasados > 0 && (
          <span className="flex-shrink-0 text-[11px] font-semibold bg-orange-50 text-orange-800 ring-1 ring-orange-300 rounded-full px-2 py-0.5">
            {p.atrasados} atrasado{p.atrasados === 1 ? '' : 's'} en agenda
          </span>
        )}
      </div>
      <div className="mt-3"><BarraDia ocupado={p.minutosOcupados} bloqueado={p.minutosBloqueados} libre={p.minutosLibres} /></div>
      <dl className="mt-2 grid grid-cols-3 gap-2 text-xs">
        <div><dt className="text-gray-600">Ocupado</dt><dd className="font-semibold text-indigo-700">{duracion(p.minutosOcupados)}</dd></div>
        <div><dt className="text-gray-600">Libre</dt><dd className="font-semibold text-green-700">{duracion(p.minutosLibres)}</dd></div>
        <div><dt className="text-gray-600">Citas</dt><dd className="font-semibold text-gray-800">{p.citas}</dd></div>
      </dl>
    </button>
  )
}

/** «Asignar aquí»: elige qué pendiente va en este hueco y lo agenda. Solo ofrece lo que cabe, es del rol de la persona y ya tiene la llamada confirmada. */
function ModalAsignarAqui({ persona, bloque, rolUsuario, onClose, onHecho, onToast }) {
  const idCampos = useId()
  const [lista, setLista] = useState(null)
  const [elegido, setElegido] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    // El gestor asigna análisis pendientes; el programador agenda las citas que el gestor le envió (Programando).
    asignacionService.listar({ estado: rolUsuario === 'programador' ? 'PROGRAMANDO' : 'PENDIENTE', size: 100, sort: 'servicio.fechaCompromisoAjustada,asc' })
      .then(r => setLista((r.content ?? []).filter(a =>
        a.rolResponsable === persona.rol && (a.duracionMinutos ?? 60) <= bloque.minutos
        && (a.rolResponsable === 'ANALISTA_INTERNO' || a.estadoConfirmacion === 'CONFIRMADO'))))
      .catch(() => setError('No se pudieron cargar los pendientes.'))
  }, [persona.rol, bloque.minutos, rolUsuario])

  const asignar = async () => {
    if (!elegido) return setError('Elige qué asignar.')
    setGuardando(true); setError(null)
    try {
      await asignacionService.asignar(Number(elegido), { idUsuarioAsignado: persona.idUsuario, fechaProgramada: toLocalDateTimeString(parseFecha(bloque.inicio)) })
      onToast?.('Asignado en el hueco elegido.')
      onHecho()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo asignar.')
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Asignar aquí" subtitulo={`${persona.nombre} ${persona.apellido ?? ''} · ${hora(bloque.inicio)} – ${hora(bloque.fin)} (${duracion(bloque.minutos)} libres)`}
      onClose={onClose} ancho="max-w-lg">
      <div className="px-6 py-5 space-y-4">
        {error && <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
        {!lista && !error && <p className="text-sm text-gray-600">Cargando pendientes…</p>}
        {lista && lista.length === 0 && (
          <p className="text-sm text-gray-700">No hay nada pendiente que quepa en este hueco para este perfil. Solo aparecen los que duran {duracion(bloque.minutos)} o menos
            {persona.rol !== 'ANALISTA_INTERNO' ? ' y ya tienen la llamada de confirmación hecha' : ''}.</p>
        )}
        {lista?.length > 0 && (
          <fieldset>
            <legend className="text-xs font-medium text-gray-700 mb-2">Pendientes que caben</legend>
            <ul className="space-y-2 max-h-72 overflow-y-auto">
              {lista.map(a => (
                <li key={a.id}>
                  <label htmlFor={`${idCampos}-${a.id}`} className={`flex items-start gap-3 rounded-lg border px-3 py-2 cursor-pointer ${String(a.id) === elegido ? 'border-primary-500 bg-primary-50' : 'border-gray-200 hover:bg-gray-50'}`}>
                    <input id={`${idCampos}-${a.id}`} type="radio" name={`${idCampos}-pend`} value={a.id} checked={String(a.id) === elegido} onChange={() => setElegido(String(a.id))} className="mt-1" />
                    <span className="text-sm text-gray-900">
                      <span className="font-medium">{a.subproceso}</span> · {a.nombresEvaluado} {a.apellidosEvaluado ?? ''}
                      <span className="block text-xs text-gray-600">Servicio #{a.idServicio} · {duracion(a.duracionMinutos ?? 60)}{a.tiempo?.etiqueta ? ` · ${a.tiempo.etiqueta}` : ''}</span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          </fieldset>
        )}
        <div className="flex justify-end gap-3 border-t border-gray-100 pt-4">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="button" onClick={asignar} disabled={guardando || !lista?.length}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Asignando…' : 'Asignar'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function BloqueFila({ b, noHabil, onAsignar }) {
  if (b.tipo === 'OCUPADO') {
    return (
      <li className="flex items-start gap-4 px-4 py-3 bg-indigo-50/60 border-l-4 border-indigo-500">
        <p className="w-40 flex-shrink-0 text-xs font-semibold text-indigo-900 tabular-nums">{hora(b.inicio)} – {hora(b.fin)}</p>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-gray-900">{b.subproceso}{b.evaluado ? ` · ${b.evaluado}` : ''}</p>
          <p className="text-xs text-gray-700">{b.idServicio ? `Servicio #${b.idServicio} · ` : ''}{duracion(b.minutos)}</p>
        </div>
      </li>
    )
  }
  if (b.tipo === 'BLOQUEADO') {
    return (
      <li className="flex items-center gap-4 px-4 py-2.5 bg-gray-100 border-l-4 border-gray-400">
        <p className="w-40 flex-shrink-0 text-xs font-semibold text-gray-700 tabular-nums">{hora(b.inicio)} – {hora(b.fin)}</p>
        <p className="text-sm text-gray-700">{b.motivo === 'ALMUERZO' ? 'Almuerzo' : 'Desplazamiento'} · {duracion(b.minutos)}</p>
      </li>
    )
  }
  return (
    <li className="flex items-center justify-between gap-4 px-4 py-3 bg-green-50/60 border-l-4 border-green-500">
      <div className="flex items-center gap-4 min-w-0">
        <p className="w-40 flex-shrink-0 text-xs font-semibold text-green-900 tabular-nums">{hora(b.inicio)} – {hora(b.fin)}</p>
        <p className="text-sm text-green-900">Libre · {duracion(b.minutos)}</p>
      </div>
      {!noHabil && (
        <button type="button" onClick={() => onAsignar(b)}
          className="flex-shrink-0 text-xs font-semibold px-3 py-1.5 rounded-lg border border-green-700 text-green-800 hover:bg-green-100 transition-colors">Asignar aquí</button>
      )}
    </li>
  )
}

export default function DisponibilidadEquipo({ rolUsuario = 'programador', onToast, onAsignado }) {
  const [fecha, setFecha] = useState(() => hoyLocalDateString())
  const [rol, setRol] = useState('')
  const [equipo, setEquipo] = useState(null)
  const [idPersona, setIdPersona] = useState(null)
  const [dia, setDia] = useState(null)
  const [error, setError] = useState(null)
  const [asignando, setAsignando] = useState(null)      // bloque libre elegido

  const cargar = useCallback(() => {
    agendaService.equipo(fecha).then(setEquipo).catch(() => setError('No se pudo cargar el equipo.'))
    if (idPersona) agendaService.diaDePersona(idPersona, fecha).then(setDia).catch(() => setError('No se pudo cargar el día de esa persona.'))
  }, [fecha, idPersona])

  useEffect(() => { setError(null); cargar() }, [cargar])
  useCambios(cargar)
  useEffect(() => { if (!idPersona) setDia(null) }, [idPersona])

  const roles = useMemo(() => [...new Set((equipo?.equipo ?? []).map(p => p.rol))], [equipo])
  const visibles = (equipo?.equipo ?? []).filter(p => !rol || p.rol === rol)
  const cambiarDia = (delta) => {
    const [y, m, d] = fecha.split('-').map(Number)
    setFecha(toLocalDateString(new Date(y, m - 1, d + delta)))
  }
  const persona = dia && dia.idUsuario === idPersona ? dia : null

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h3 className="text-lg font-bold text-gray-900 capitalize">{idPersona && persona ? `${persona.nombre} ${persona.apellido ?? ''}` : 'Equipo hoy'}</h3>
          <p className="text-xs text-gray-700 capitalize">{diaLargo(fecha)} · jornada de 7:00 a. m. a 6:00 p. m.</p>
        </div>
        <div className="flex items-center gap-1.5">
          <button type="button" onClick={() => cambiarDia(-1)} title="Día anterior" aria-label="Día anterior" className={botonNav}>‹</button>
          <input type="date" aria-label="Día" value={fecha} onChange={e => e.target.value && setFecha(e.target.value)}
            className="px-3 py-1.5 text-xs rounded-lg border border-gray-300 text-gray-800 focus:outline-none focus:ring-2 focus:ring-primary-500" />
          <button type="button" onClick={() => setFecha(hoyLocalDateString())}
            className="px-3 py-1.5 text-xs font-semibold rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50 transition-colors">Hoy</button>
          <button type="button" onClick={() => cambiarDia(1)} title="Día siguiente" aria-label="Día siguiente" className={botonNav}>›</button>
        </div>
      </div>

      {error && <div role="alert" className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
      {equipo && !equipo.esDiaHabil && (
        <div className="mb-4 bg-slate-100 border border-slate-300 text-slate-800 text-sm px-4 py-3 rounded-lg">
          {equipo.motivoNoHabil} — no es un día de trabajo; no se agenda.
        </div>
      )}

      {!idPersona ? (
        <>
          {roles.length > 1 && (
            <div className="flex flex-wrap gap-2 mb-4" role="group" aria-label="Filtrar por rol">
              {[['', 'Todos'], ...roles.map(r => [r, ROL_ETIQUETA[r] ?? r])].map(([valor, etiqueta]) => (
                <button key={valor} type="button" aria-pressed={rol === valor} onClick={() => setRol(valor)}
                  className={`px-3 py-1.5 rounded-lg border text-sm transition-colors ${rol === valor ? 'bg-primary-600 border-primary-600 text-white' : 'bg-white border-gray-300 text-gray-800 hover:bg-gray-50'}`}>
                  {etiqueta}
                </button>
              ))}
            </div>
          )}
          {!equipo ? (
            <div className="flex items-center justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
          ) : visibles.length === 0 ? (
            <div className="bg-white rounded-xl border border-gray-200 text-center py-16 text-sm text-gray-700">No hay personas activas en este equipo.</div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {visibles.map(p => <TarjetaPersona key={p.idUsuario} p={p} onElegir={setIdPersona} />)}
            </div>
          )}
        </>
      ) : (
        <div>
          <button type="button" onClick={() => setIdPersona(null)}
            className="mb-3 text-sm font-medium text-primary-700 hover:underline">‹ Volver al equipo</button>
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            {!persona ? (
              <div className="flex items-center justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
            ) : (
              <>
                <div className="px-4 py-3 border-b border-gray-100 flex flex-wrap items-center gap-x-6 gap-y-2">
                  <p className="text-xs text-gray-700">{ROL_ETIQUETA[persona.rol] ?? persona.rol}</p>
                  <div className="w-48"><BarraDia ocupado={persona.minutosOcupados} bloqueado={persona.minutosBloqueados} libre={persona.minutosLibres} /></div>
                  <p className="text-xs text-gray-800">Ocupado <strong>{duracion(persona.minutosOcupados)}</strong> · Libre <strong>{duracion(persona.minutosLibres)}</strong></p>
                </div>
                <ul className="divide-y divide-white">
                  {persona.bloques.map((b, i) => <BloqueFila key={i} b={b} noHabil={!persona.esDiaHabil} onAsignar={setAsignando} />)}
                </ul>
              </>
            )}
          </div>
        </div>
      )}

      {asignando && persona && (
        <ModalAsignarAqui persona={persona} bloque={asignando} rolUsuario={rolUsuario} onToast={onToast}
          onClose={() => setAsignando(null)} onHecho={() => { setAsignando(null); cargar(); onAsignado?.() }} />
      )}
    </div>
  )
}

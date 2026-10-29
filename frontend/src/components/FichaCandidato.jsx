import { parseFecha } from '../utils/fechas'
import { useState, useEffect } from 'react'
import { useAuth } from '../hooks/useAuth'
import formulariosService from '../services/formulariosService'
import { useCambios } from '../hooks/useCambios'
import SolicitudCliente from './SolicitudCliente'

// Ficha del candidato vista desde un subproceso: sus datos (identificación, contacto, otros) y los
// soportes que cargó con el link de hoja de vida, cruzados contra los documentos que exige el
// proceso — así quien ejecuta ve de un vistazo qué tiene y qué falta sin salir del formulario.

// Revisión de un soporte cargado (V197): lo aprueban o lo devuelven con motivo al candidato.
// Todo lo que no es Aprobado ni Por revisar es una devolución: el candidato ve el motivo y el
// documento queda bloqueado hasta que cargue uno nuevo (V199).
const DEVOLUCION = { chip: 'bg-red-100 text-red-800', icono: 'bg-red-50 text-red-600' }
const REVISION = {
  PENDIENTE:      { etiqueta: 'Por revisar',    chip: 'bg-amber-100 text-amber-800', icono: 'bg-amber-50 text-amber-600' },
  APROBADO:       { etiqueta: 'Aprobado',       chip: 'bg-emerald-100 text-emerald-800', icono: 'bg-emerald-50 text-emerald-600' },
  DENEGADO:       { etiqueta: 'Denegado',       ...DEVOLUCION },
  ILEGIBLE:       { etiqueta: 'Ilegible',       ...DEVOLUCION },
  VENCIDO:        { etiqueta: 'Vencido',        ...DEVOLUCION },
  INCOMPLETO:     { etiqueta: 'Incompleto',     ...DEVOLUCION },
  NO_CORRESPONDE: { etiqueta: 'No corresponde', ...DEVOLUCION },
}
const OPCIONES_REVISION = ['APROBADO', 'DENEGADO', 'ILEGIBLE', 'VENCIDO', 'INCOMPLETO', 'NO_CORRESPONDE']
const esDevolucion = (estado) => !!estado && estado !== 'PENDIENTE' && estado !== 'APROBADO'

function formatoFecha(iso) {
  if (!iso) return ''
  return parseFecha(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function IconoPersona() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
    </svg>
  )
}

function IconoClip() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M18.375 12.739l-7.693 7.693a4.5 4.5 0 01-6.364-6.364l10.94-10.94A3 3 0 1119.5 7.372L8.552 18.32m.009-.01l-.01.01m5.699-9.941l-7.81 7.81a1.5 1.5 0 002.112 2.13" />
    </svg>
  )
}

function Encabezado({ icono, titulo, detalle, color = 'primary' }) {
  const colorIcono = color === 'sky' ? 'bg-sky-50 text-sky-600' : 'bg-primary-50 text-primary-600'
  return (
    <div className="flex items-center gap-2 mb-3">
      <span className={`flex-shrink-0 w-7 h-7 rounded-lg flex items-center justify-center ${colorIcono}`}>{icono}</span>
      <div className="min-w-0">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">{titulo}</p>
        {detalle && <p className="text-[11px] text-gray-400">{detalle}</p>}
      </div>
    </div>
  )
}

// seccion: 'datos' (va debajo de las novedades) | 'soportes' (paso propio del formulario).
// onResumen: solo con seccion="soportes" — recibe un texto corto ("3 de 5 cargados") para el índice.
export default function FichaCandidato({ idServicioSubproceso, seccion = 'datos', onError, onResumen }) {
  const [ficha, setFicha] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [descargando, setDescargando] = useState(null)
  const [verVacios, setVerVacios] = useState(false)
  const { usuario } = useAuth()
  // Analista/poligrafista/visitador: al devolver, el backend registra además una novedad automática.
  const esEjecutor = ['ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR'].includes(usuario?.rol)
  const [revisando, setRevisando] = useState(null)
  const [devolviendo, setDevolviendo] = useState(null) // formulario de revisión abierto: { idDocumento, resultado, texto }

  // Aprobar o devolver (con motivo) un soporte — la respuesta trae la ficha actualizada.
  const revisar = async (soporte, estado, observacion = null) => {
    setRevisando(soporte.idDocumento)
    try {
      setFicha(await formulariosService.revisarSoporte(idServicioSubproceso, soporte.idDocumento, estado, observacion))
      setDevolviendo(null)
    } catch (e) {
      onError?.(e.response?.data?.mensaje ?? 'No se pudo guardar la revisión del documento.')
    } finally {
      setRevisando(null)
    }
  }

  useEffect(() => {
    setCargando(true)
    formulariosService.fichaCandidato(idServicioSubproceso)
      .then(setFicha)
      .catch(() => onError?.('No se pudieron cargar los datos del candidato.'))
      .finally(() => setCargando(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idServicioSubproceso])

  // Tiempo real: si el candidato carga o cambia un documento (o alguien lo revisa), la ficha se
  // actualiza sola. No toca el formulario de revisión que se tenga abierto.
  useCambios(() => formulariosService.fichaCandidato(idServicioSubproceso).then(setFicha).catch(() => {}))

  useEffect(() => {
    if (!ficha || seccion !== 'soportes' || !onResumen) return
    const lista = ficha.soportes ?? []
    const req = lista.filter(s => s.requerido)
    onResumen(req.length > 0
      ? `${req.filter(s => s.idDocumento).length} de ${req.length} cargados`
      : lista.length > 0 ? `${lista.length} cargado${lista.length === 1 ? '' : 's'}` : 'Sin soportes')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ficha, seccion])

  const abrir = async (soporte) => {
    setDescargando(soporte.idDocumento)
    try {
      const blob = await formulariosService.descargarSoporte(idServicioSubproceso, soporte.idDocumento)
      const url = window.URL.createObjectURL(blob)
      window.open(url, '_blank')
      setTimeout(() => window.URL.revokeObjectURL(url), 60000)
    } catch {
      onError?.('No se pudo abrir el soporte.')
    } finally {
      setDescargando(null)
    }
  }

  if (cargando) {
    return (
      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-6 flex justify-center">
        <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-500 border-t-transparent" />
      </div>
    )
  }
  if (!ficha) return null

  const grupos = ficha.grupos.map(g => ({
    ...g,
    campos: verVacios ? g.campos : g.campos.filter(c => c.valor && c.valor.trim() !== ''),
  })).filter(g => g.campos.length > 0)
  const totalVacios = ficha.grupos.reduce((acc, g) => acc + g.campos.filter(c => !c.valor || !c.valor.trim()).length, 0)

  const soportes = ficha.soportes ?? []
  const requeridos = soportes.filter(s => s.requerido)
  const cargadosRequeridos = requeridos.filter(s => s.idDocumento).length
  const faltanObligatorios = requeridos.filter(s => s.obligatorio && !s.idDocumento).length

  if (seccion === 'soportes') {
    return (
      <section className="bg-white border border-gray-200 border-l-4 border-l-sky-400 rounded-2xl shadow-sm p-4">
        <Encabezado icono={<IconoClip />} titulo="Soportes del candidato" color="sky"
          detalle={requeridos.length > 0
            ? `${cargadosRequeridos} de ${requeridos.length} documentos del proceso cargados${faltanObligatorios > 0 ? ` · faltan ${faltanObligatorios} obligatorio${faltanObligatorios === 1 ? '' : 's'}` : ''}`
            : 'Documentos que subió con el link de hoja de vida'} />
        {requeridos.length > 0 && (
          <div className="mb-3 h-1.5 rounded-full bg-gray-100 overflow-hidden">
            <div className="h-full bg-sky-500 rounded-full transition-all"
              style={{ width: `${Math.round(cargadosRequeridos / requeridos.length * 100)}%` }} />
          </div>
        )}
        {soportes.length === 0 ? (
          <p className="text-xs text-gray-400 bg-gray-50 rounded-xl px-4 py-6 text-center">
            El candidato no ha cargado soportes y el proceso no tiene documentos configurados.
          </p>
        ) : (
          <ul className="grid grid-cols-1 lg:grid-cols-2 gap-2.5">
            {soportes.map(s => {
              const cargado = Boolean(s.idDocumento)
              const rev = cargado ? (REVISION[s.estadoRevision] ?? REVISION.PENDIENTE) : null
              const devolviendoEste = devolviendo?.idDocumento === s.idDocumento && cargado
              const devuelto = esDevolucion(s.estadoRevision)
              return (
                <li key={`${s.clave}-${s.idDocumento ?? 'falta'}`}
                  className={`rounded-xl border px-3.5 py-3 ${
                    !cargado ? (s.obligatorio ? 'bg-amber-50/60 border-amber-200' : 'bg-gray-50 border-gray-200')
                      : devuelto ? 'bg-red-50/40 border-red-200'
                      : s.estadoRevision === 'APROBADO' ? 'bg-white border-emerald-200' : 'bg-white border-gray-200'}`}>
                  <div className="flex items-center gap-3">
                    <span className={`flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-sm ${
                      !cargado ? (s.obligatorio ? 'bg-amber-100 text-amber-600' : 'bg-gray-100 text-gray-400') : rev.icono}`}>
                      {!cargado ? '!' : s.estadoRevision === 'APROBADO' ? '✓' : devuelto ? '↩' : '•'}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 min-w-0">
                        <p className="text-sm font-medium text-gray-800 truncate">{s.nombre}</p>
                        {rev && <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${rev.chip}`}>{rev.etiqueta}</span>}
                      </div>
                      <p className="text-[11px] text-gray-400 truncate">
                        {cargado ? `${s.nombreArchivo} · ${formatoFecha(s.fechaCarga)}`
                          : s.obligatorio ? 'Obligatorio — no lo ha cargado' : 'No lo ha cargado'}
                        {!s.requerido && ' · adicional'}
                      </p>
                    </div>
                    {cargado && (
                      <button type="button" onClick={() => abrir(s)} disabled={descargando === s.idDocumento}
                        className="flex-shrink-0 text-xs font-medium text-sky-700 hover:text-sky-800 border border-sky-200 hover:bg-sky-50 disabled:opacity-60 px-3 py-1.5 rounded-lg">
                        {descargando === s.idDocumento ? 'Abriendo…' : 'Ver'}
                      </button>
                    )}
                  </div>

                  {cargado && s.revisadoPor && s.estadoRevision !== 'PENDIENTE' && (
                    <p className="mt-2 text-[11px] text-gray-500">
                      {s.estadoRevision === 'APROBADO' ? 'Aprobado' : `Devuelto como ${rev.etiqueta.toLowerCase()}`} por {s.revisadoPor} · {formatoFecha(s.fechaRevision)}
                    </p>
                  )}
                  {cargado && s.observacionRevision && (
                    <p className={`mt-1.5 rounded-lg px-2.5 py-1.5 text-[11px] ${devuelto ? 'bg-red-50 text-red-800' : 'bg-gray-50 text-gray-700'}`}>
                      <span className="font-semibold">Observación: </span>{s.observacionRevision}
                    </p>
                  )}
                  {cargado && devuelto && (
                    <p className="mt-1.5 text-[11px] italic text-gray-500">
                      Bloqueado hasta que el candidato cargue el documento corregido; entonces vuelve a "Por revisar".
                    </p>
                  )}

                  {cargado && ficha.puedeRevisarSoportes && !devuelto && !devolviendoEste && (
                    <div className="mt-2.5">
                      <button type="button" disabled={revisando === s.idDocumento}
                        onClick={() => setDevolviendo({ idDocumento: s.idDocumento, resultado: '', texto: '' })}
                        className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-primary-600 text-white hover:bg-primary-700 disabled:opacity-60">
                        {s.estadoRevision === 'APROBADO' ? 'Cambiar revisión' : 'Revisar'}
                      </button>
                    </div>
                  )}
                  {devolviendoEste && (
                    <div className="mt-2.5 space-y-2 rounded-lg border border-gray-200 bg-gray-50/70 p-2.5">
                      <label className="block">
                        <span className="block text-[11px] font-medium text-gray-600 mb-1">Resultado de la revisión</span>
                        <select value={devolviendo.resultado} autoFocus
                          onChange={(e) => setDevolviendo(d => ({ ...d, resultado: e.target.value }))}
                          className="w-full rounded-lg border border-gray-300 bg-white px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-primary-500">
                          <option value="">Selecciona…</option>
                          {OPCIONES_REVISION.filter(o => o !== s.estadoRevision).map(o => (
                            <option key={o} value={o}>{REVISION[o].etiqueta}</option>
                          ))}
                        </select>
                      </label>
                      <label className="block">
                        <span className="block text-[11px] font-medium text-gray-600 mb-1">
                          Observación {esDevolucion(devolviendo.resultado) ? <span className="text-red-500">*</span> : <span className="text-gray-400">(opcional)</span>}
                        </span>
                        <textarea value={devolviendo.texto} maxLength={1000} rows={2}
                          onChange={(e) => setDevolviendo(d => ({ ...d, texto: e.target.value }))}
                          placeholder={esDevolucion(devolviendo.resultado) ? '¿Qué debe corregir el candidato? (lo verá en su portal)' : 'Nota interna o para el candidato'}
                          className="w-full rounded-lg border border-gray-300 bg-white px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
                      </label>
                      {esDevolucion(devolviendo.resultado) && (
                        <p className="text-[11px] text-amber-700">
                          El documento se devuelve al candidato y queda bloqueado hasta que cargue uno nuevo.
                          {esEjecutor && ` También se registrará la novedad "Documento del candidato ${REVISION[devolviendo.resultado].etiqueta.toLowerCase()}" en este subproceso.`}
                        </p>
                      )}
                      <div className="flex gap-2">
                        <button type="button"
                          disabled={!devolviendo.resultado || (esDevolucion(devolviendo.resultado) && !devolviendo.texto.trim()) || revisando === s.idDocumento}
                          onClick={() => revisar(s, devolviendo.resultado, devolviendo.texto)}
                          className={`text-xs font-semibold px-3 py-1.5 rounded-lg text-white disabled:opacity-50 ${
                            esDevolucion(devolviendo.resultado) ? 'bg-red-600 hover:bg-red-700' : 'bg-emerald-600 hover:bg-emerald-700'}`}>
                          {revisando === s.idDocumento ? 'Guardando…' : esDevolucion(devolviendo.resultado) ? 'Devolver al candidato' : 'Guardar revisión'}
                        </button>
                        <button type="button" onClick={() => setDevolviendo(null)}
                          className="text-xs px-3 py-1.5 rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50">
                          Cancelar
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </section>
    )
  }

  return (
    <div className="space-y-4">
      {/* Lo que el cliente pidió al solicitar la poligrafía (formato adicional y temas a evaluar) */}
      <SolicitudCliente solicitud={ficha.solicitudCliente} />

      <section className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
        <div className="flex items-start justify-between gap-3">
          <Encabezado icono={<IconoPersona />} titulo="Datos del candidato" detalle="Tomados de su registro y de la hoja de vida que diligenció" />
          {totalVacios > 0 && (
            <button type="button" onClick={() => setVerVacios(v => !v)}
              className="flex-shrink-0 text-[11px] font-medium text-gray-500 hover:text-gray-700">
              {verVacios ? 'Ocultar vacíos' : `Ver vacíos (${totalVacios})`}
            </button>
          )}
        </div>
        {grupos.length === 0 ? (
          <p className="text-xs text-gray-400 bg-gray-50 rounded-xl px-4 py-6 text-center">El candidato todavía no tiene datos registrados.</p>
        ) : (
          <div className="space-y-4">
            {grupos.map(g => (
              <div key={g.nombre}>
                <p className="text-[11px] font-semibold text-gray-400 mb-1.5">{g.nombre}</p>
                <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-5 gap-y-2 bg-gray-50/70 rounded-xl px-3.5 py-3">
                  {g.campos.map(c => (
                    <div key={c.clave} className="min-w-0">
                      <dt className="text-[11px] text-gray-400">{c.etiqueta}</dt>
                      <dd className={`text-sm truncate ${c.valor ? 'font-medium text-gray-800' : 'text-gray-300'}`} title={c.valor || undefined}>
                        {c.valor || 'Sin dato'}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Declaraciones: lo que el candidato respondió en su hoja de vida (solo consulta) */}
      {(ficha.declaraciones ?? []).length > 0 && (
        <section className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
          <Encabezado icono={<IconoPersona />} titulo="Declaraciones del candidato" detalle="Lo que respondió en su hoja de vida. Solo las puede cambiar él desde su link" />
          <div className="space-y-2">
            {ficha.declaraciones.map((d, i) => (
              <div key={i} className={`rounded-xl px-3.5 py-3 ${d.respuesta === 'Sí' ? 'bg-amber-50 ring-1 ring-inset ring-amber-200' : 'bg-gray-50/70'}`}>
                <p className="text-[11px] text-gray-500">{d.pregunta}</p>
                <p className="text-sm font-medium text-gray-800">{d.respuesta}</p>
                {d.detalle && <p className="mt-0.5 text-sm text-gray-700 whitespace-pre-line">{d.detalle}</p>}
              </div>
            ))}
          </div>
        </section>
      )}

    </div>
  )
}

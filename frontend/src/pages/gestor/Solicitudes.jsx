import { fechaCorta, fechaHora } from '../../utils/fechas'
import { useId, useState, useEffect, useCallback, useMemo, Fragment } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import api from '../../services/api'
import tokenService from '../../services/tokenService'
import catalogoService from '../../services/catalogoService'
import asignacionService from '../../services/asignacionService'
import formulariosService from '../../services/formulariosService'
import trabajoService, { esperarTrabajo, guardarBlob, mensajeDeError } from '../../services/trabajoService'
import PanelPublicarSubproceso from '../../components/gestor/PanelPublicarSubproceso'
import PanelEmitirEstudio from '../../components/gestor/PanelEmitirEstudio'
import MarcaDevuelto from '../../components/informe/MarcaDevuelto'
import PanelEnviarEnlace from '../../components/gestor/PanelEnviarEnlace'
import Toast from '../../components/Toast'
import { Modal, ConfirmModal } from '../../components/ui/Modal'
import Paginacion from '../../components/ui/Paginacion'
import MenuAcciones from '../../components/ui/MenuAcciones'
import EstadoTiempo from '../../components/EstadoTiempo'
import ChipRiesgo from '../../components/ChipRiesgo'
import bandejaService from '../../services/bandejaService'
import ModalAsignarSubproceso from '../../components/ModalAsignarSubproceso'
import ModalAsignarProgramador from '../../components/ModalAsignarProgramador'
import CiudadSelect from '../../components/CiudadSelect'
import RiesgoServicio from '../../components/RiesgoServicio'
import { useCambios } from '../../hooks/useCambios'
import { iniciarIntervaloVisible } from '../../hooks/useIntervaloVisible'
import SemaforoEstados from '../../components/semaforo/SemaforoEstados'
import EtiquetaEstado from '../../components/semaforo/EtiquetaEstado'

import SelectorMotivo, { motivoIncompleto, MOTIVO_VACIO } from '../../components/SelectorMotivo'
/* ─── Iconos de acciones de tabla ─── */
function IconoOjo() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
    </svg>
  )
}
function IconoCambioEstado() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" />
    </svg>
  )
}
function IconoEnviar() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
    </svg>
  )
}
function IconoAsignado() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  )
}

function formatearPrecioCOP(valor) {
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor)
}

// Debe coincidir con PASOS en frontend/src/pages/evaluee/EvalueeForm.jsx
const PASOS_EVALUADO = ['Autorización', 'Datos personales', 'Educación', 'Experiencia laboral', 'Referencias', 'Documentos', 'Revisión']

const DOT_ESTADO = {
  PENDIENTE:    'bg-amber-400',
  PROGRAMANDO:  'bg-blue-400',
  EN_EJECUCION: 'bg-primary-400',
  FINALIZADO:   'bg-green-400',
  PUBLICADO:    'bg-emerald-400',
  CANCELADO:    'bg-red-400',
  REPROGRAMADO: 'bg-orange-400',
}

// Transiciones permitidas para GESTOR
// El gestor ya no elige el estado del servicio a mano (SE-02): publica el estudio cuando todo está publicado (FINALIZADO) o
// cancela la solicitud con motivo. Lo demás se mueve con las acciones de cada subproceso. CANCELADO solo pide reversión.
const TRANSICIONES_GESTOR = {
  PENDIENTE:    ['CANCELADO'],
  PROGRAMANDO:  ['CANCELADO'],
  EN_EJECUCION: ['CANCELADO'],
  FINALIZADO:   ['PUBLICADO'],
  PUBLICADO:    [],
  CANCELADO:    ['PROGRAMANDO', 'REPROGRAMADO'],
  REPROGRAMADO: ['CANCELADO'],
}

function tiempoRestante(iso) {
  if (!iso) return '—'
  const diff = new Date(iso).getTime() - Date.now()
  if (diff <= 0) return 'Expirado'
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  if (h >= 24) return `${Math.floor(h / 24)}d ${h % 24}h`
  return `${h}h ${m}m`
}

function formatDuracion(minutos) {
  if (minutos == null) return '—'
  if (minutos < 60) return `${minutos} min`
  const h = Math.floor(minutos / 60)
  const m = minutos % 60
  return m > 0 ? `${h}h ${m}m` : `${h}h`
}

// Subprocesos de poligrafía/visita (los que coordina Programador) de un servicio, y si ya se
// les asignó un programador a TODOS — para decidir el ícono del botón "Asignar programador".
function subprocesosProgramadorDe(subprocesos) {
  const delProgramador = (subprocesos ?? []).filter(sp =>
    sp.nombre?.toLowerCase().includes('poligraf') || sp.nombre?.toLowerCase().includes('visita'))
  return {
    hayAlgunos: delProgramador.length > 0,
    todosAsignados: delProgramador.length > 0 && delProgramador.every(sp => sp.programadorAsignado),
  }
}


/* ─── Fila desplegable (PB-02): una fila por subproceso con su estado, su calificación y UN botón principal según el estado ─── */
const COLOR_NIVEL = { BAJO: 'bg-[#E6F3EC] text-[#1f6b44]', MEDIO: 'bg-[#FBF1DE] text-[#8a5a0c]', ALTO: 'bg-[#F9E8EA] text-[#8c2733]' }
const botonFila = 'min-h-[44px] w-full sm:w-[150px] px-3 rounded-lg text-xs font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#A8323F]'

// Calificación del subproceso: publicada (la del cliente) o, en Finalizado, la sugerida; siempre con el nombre del nivel escrito.
function ChipCalificacion({ sp }) {
  const nivel = sp.estado === 'PUBLICADO' ? sp.nivelPublicado : sp.estado === 'FINALIZADO' ? sp.riesgoNivel : null
  const puntaje = sp.estado === 'PUBLICADO' ? sp.puntajePublicado : sp.riesgoPuntaje
  if (!nivel) return <span className="inline-block px-2 py-0.5 rounded-full bg-gray-100 text-gray-700 text-[11px] font-semibold" aria-label="Sin calificación">—</span>
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-[11px] font-semibold tabular-nums ${COLOR_NIVEL[nivel] ?? 'bg-gray-100 text-gray-700'}`}>
      {nivel}{puntaje != null ? ` ${Number(puntaje).toLocaleString('es-CO', { maximumFractionDigits: 1 })}` : ''}{sp.estado === 'FINALIZADO' ? ' · sugerido' : ''}
    </span>
  )
}

function FilaSubprocesos({ idServicio, colSpan, onToast, candidatoCompletado, estadoServicio, onEmitir }) {
  const navigate = useNavigate()
  const [subprocesos, setSubprocesos] = useState(null)
  const [error, setError] = useState(false)
  const [seleccionado, setSeleccionado] = useState(null)
  const [publicando, setPublicando] = useState(null)
  const [asignandoProgramadorDe, setAsignandoProgramadorDe] = useState(null)
  // false = el candidato todavía no diligenció su link — null/true no restringen (ver SolicitudGestorResponse.candidatoCompletado).
  const bloqueadoPorCandidato = candidatoCompletado === false
  const tituloBloqueo = 'El candidato todavía no ha diligenciado su información (link pendiente).'

  const cargar = useCallback(() => {
    asignacionService.listar({ idServicio, size: 50 })
      .then(r => setSubprocesos(r.content ?? []))
      .catch(() => setError(true))
  }, [idServicio])

  useEffect(() => { cargar() }, [cargar])
  useCambios(cargar, { servicios: idServicio })

  const vigentes = (subprocesos ?? []).filter(sp => sp.estado !== 'CANCELADO')
  const publicados = vigentes.filter(sp => sp.estado === 'PUBLICADO').length
  const completo = vigentes.length > 0 && publicados === vigentes.length
  const puedeEmitir = completo && estadoServicio === 'FINALIZADO'

  const verInforme = async (sp) => {
    try {
      const inicial = await formulariosService.solicitarDocumentoPdf(sp.id)
      const listo = await esperarTrabajo(inicial)
      guardarBlob(await trabajoService.descargarResultado(listo.id), listo.nombreArchivo ?? `informe-${sp.id}.pdf`)
    } catch (e) {
      onToast(mensajeDeError(e, 'No se pudo generar el PDF. Reintentar'), 'error')
    }
  }

  // Poligrafía y visita las agenda el programador: se abre su asignación como «Programador»; el resto como «Asignación».
  const abrirAsignacion = (sp) => {
    if (sp.rolResponsable !== 'ANALISTA_INTERNO') setAsignandoProgramadorDe(sp)
    else setSeleccionado(sp)
  }
  const asignacionDisponible = (sp) => ['PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION', 'REPROGRAMADO'].includes(sp.estado)

  return (
    <Fragment>
    <tr className="bg-gray-50/60">
      <td colSpan={colSpan} className="px-4 py-3">
        {error ? (
          <p className="text-xs text-red-700">No se pudieron cargar los subprocesos.</p>
        ) : subprocesos === null ? (
          <div className="flex items-center gap-2 text-xs text-gray-600 py-2">
            <div className="animate-spin rounded-full h-4 w-4 border-2 border-primary-500 border-t-transparent" />
            Cargando subprocesos...
          </div>
        ) : subprocesos.length === 0 ? (
          <p className="text-xs text-gray-600">Este servicio no tiene subprocesos generados.</p>
        ) : (
          <div className="space-y-2">
            <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-700">
              Subprocesos · {publicados} de {vigentes.length} publicados
            </p>
            {subprocesos.map((sp, i) => {
              const porPublicar = sp.estado === 'FINALIZADO'
              const bloqueado = sp.estado === 'PENDIENTE' && bloqueadoPorCandidato
              return (
                <div key={sp.id} className={`flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg px-3 py-2 border ${porPublicar ? 'border-[#A8323F] bg-[#FBEFF0]' : 'border-gray-200 bg-white'}`}>
                  <span className="text-xs text-gray-600 tabular-nums w-5">{i + 1}</span>
                  <div className="min-w-[160px] flex-1">
                    <p className="text-sm font-semibold text-gray-900">{sp.subproceso}</p>
                    <p className="text-xs text-gray-600">{sp.nombreAsignado ?? (sp.nombreProgramadorResponsable ? `Programador: ${sp.nombreProgramadorResponsable}` : 'Sin asignar')}</p>
                  </div>
                  <div className="min-w-[150px]">
                    <EtiquetaEstado estado={sp.estado} pequena />
                    {/* PC-04: «Devuelto» es una marca gris con el motivo, no un estado: el subproceso sigue en Ejecución. */}
                    {sp.devuelto && sp.estado === 'EN_EJECUCION' && <MarcaDevuelto motivo={sp.motivoDevolucion} detalle={sp.detalleDevolucion} className="mt-1" />}
                    {porPublicar && <span className="block text-[11px] text-gray-700 mt-0.5">control de calidad</span>}
                  </div>
                  <div className="min-w-[110px]"><ChipCalificacion sp={sp} /></div>
                  <div className="flex flex-wrap gap-2 sm:ml-auto">
                    {porPublicar ? (
                      <button type="button" onClick={() => setPublicando(sp)} className={`${botonFila} bg-[#A8323F] text-white hover:bg-[#8f2a35]`}>Revisar y publicar</button>
                    ) : sp.estado === 'PUBLICADO' ? (
                      <button type="button" onClick={() => verInforme(sp)} className={`${botonFila} border border-[#A8323F] text-[#A8323F] hover:bg-red-50`}>Ver informe</button>
                    ) : (
                      <button type="button" onClick={() => navigate(`/gestor/formulario/${sp.id}`)} disabled={sp.estado === 'CANCELADO'}
                        className={`${botonFila} border border-[#A8323F] text-[#A8323F] hover:bg-red-50 disabled:opacity-50`}>Ver avance</button>
                    )}
                    <button type="button" onClick={() => abrirAsignacion(sp)} disabled={!asignacionDisponible(sp) || bloqueado}
                      title={bloqueado ? tituloBloqueo : undefined}
                      className={`${botonFila} border border-gray-300 text-gray-900 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed`}>Asignación</button>
                  </div>
                </div>
              )
            })}
            <div className="rounded-lg bg-gray-100 px-3 py-3 flex flex-wrap items-center gap-3">
              <p className="text-sm text-gray-900 flex-1 min-w-[200px]">
                <strong>Estudio completo:</strong> {publicados} de {vigentes.length} publicados.{' '}
                {completo ? (puedeEmitir ? 'Listo para emitir.' : 'Todos publicados.') : `Se habilita con ${vigentes.length} de ${vigentes.length}.`}
              </p>
              <button type="button" onClick={onEmitir} disabled={publicados === 0 || completo}
                title={completo ? 'Con todo publicado se emite el estudio completo' : publicados === 0 ? 'Publica al menos un informe' : undefined}
                className="min-h-[44px] px-4 rounded-lg text-sm font-semibold border border-gray-300 bg-white text-gray-900 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2">
                Emitir preliminar
              </button>
              <button type="button" onClick={onEmitir} disabled={!puedeEmitir}
                className="min-h-[44px] px-4 rounded-lg text-sm font-semibold bg-[#A8323F] text-white hover:bg-[#8f2a35] disabled:bg-gray-200 disabled:text-gray-600 disabled:cursor-not-allowed focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2">
                Emitir estudio completo
              </button>
            </div>
          </div>
        )}
      </td>
    </tr>

    {seleccionado && (
      <ModalAsignarSubproceso
        asignacion={seleccionado}
        onClose={() => setSeleccionado(null)}
        onGuardado={() => { setSeleccionado(null); cargar(); onToast('Subproceso actualizado correctamente') }}
        onToast={onToast}
      />
    )}

    {asignandoProgramadorDe && (
      <ModalAsignarProgramador
        subprocesos={[asignandoProgramadorDe]}
        onClose={() => setAsignandoProgramadorDe(null)}
        onGuardado={() => { setAsignandoProgramadorDe(null); cargar() }}
        onToast={onToast}
      />
    )}

    {publicando && (
      <PanelPublicarSubproceso
        asignacion={publicando} publicados={publicados} total={vigentes.length}
        onClose={() => setPublicando(null)}
        // PC-02: si este era el último informe (1 de 1, 3 de 3…), a continuación se abre «Emitir estudio completo».
        onHecho={(r) => { setPublicando(null); cargar(); if (r?.cierraEstudio) onEmitir?.() }}
        onToast={onToast}
      />
    )}
    </Fragment>
  )
}

// El estado de tiempo (en días hábiles, "Vencida · 17 días hábiles") lo calcula el servidor: aquí solo se muestra.
function EtiquetaEntrega({ tiempo }) {
  if (!tiempo) return <span className="text-gray-400">—</span>
  return <EstadoTiempo tiempo={tiempo} />
}

/* ── Estados del link de validación del evaluado ── */
// Desde V197 el link no vence a las 36 h: el candidato entra (y edita, ve citas y documentos)
// mientras el servicio esté abierto. PENDIENTE y USADO ("Enviado") tienen acceso; REVOCADO,
// BLOQUEADO y CERRADO no. EXPIRADO queda solo por compatibilidad con datos viejos.
const ESTADO_LINK_CFG = {
  PENDIENTE: { color: 'bg-emerald-100 text-emerald-700', label: 'Activo — sin enviar' },
  USADO:     { color: 'bg-sky-100 text-sky-700',         label: 'Enviado — activo' },
  REVOCADO:  { color: 'bg-slate-100 text-slate-600',     label: 'Revocado'  },
  CERRADO:   { color: 'bg-slate-100 text-slate-600',     label: 'Servicio cerrado' },
  EXPIRADO:  { color: 'bg-red-100 text-red-600',         label: 'Expirado'  },
  BLOQUEADO: { color: 'bg-orange-100 text-orange-700',   label: 'Bloqueado' },
}

function iniciales(nombres, apellidos) {
  const a = (nombres ?? '').trim()[0] ?? ''
  const b = (apellidos ?? '').trim()[0] ?? ''
  return (a + b).toUpperCase() || '—'
}

function inicialesTexto(texto) {
  const partes = (texto ?? '').trim().split(/\s+/).filter(Boolean)
  const a = partes[0]?.[0] ?? ''
  const b = partes[1]?.[0] ?? ''
  return (a + b).toUpperCase() || '—'
}

/* ─── Modal: resumen de la hoja de vida diligenciada por el candidato ─── */
function ModalResumenEvaluado({ idServicio, nombreEvaluado, onClose }) {
  const [resumen, setResumen]   = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError]       = useState(null)
  const [descargandoId, setDescargandoId] = useState(null)

  useEffect(() => {
    tokenService.resumenPorServicio(idServicio)
      .then(setResumen)
      .catch((e) => setError(
        e.response?.data?.mensaje ?? 'El candidato aún no ha diligenciado su hoja de vida.'
      ))
      .finally(() => setCargando(false))
  }, [idServicio])

  const descargar = async (doc) => {
    setDescargandoId(doc.id)
    try {
      const blob = await tokenService.descargarDocumento(idServicio, doc.id)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = doc.nombreArchivo || 'documento'
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      window.alert('No se pudo descargar el documento.')
    } finally {
      setDescargandoId(null)
    }
  }

  return (
    <Modal titulo="Resumen del candidato" subtitulo={nombreEvaluado} onClose={onClose} ancho="max-w-2xl">
      <div className="px-6 py-5 space-y-6">
        {cargando && (
          <div className="flex justify-center py-10">
            <div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" />
          </div>
        )}

        {!cargando && error && (
          <div className="bg-amber-50 border border-amber-200 text-amber-700 text-sm px-4 py-3 rounded-lg">{error}</div>
        )}

        {!cargando && resumen && (
          <>
            {/* Estado del formulario */}
            <div className="flex items-center justify-between gap-3 bg-gray-50 rounded-lg p-4">
              <div>
                <p className="text-sm font-medium text-gray-800">{resumen.nombres} {resumen.apellidos}</p>
                <p className="text-xs text-gray-500 mt-0.5">{resumen.tipoDocumento ?? 'CC'} {resumen.cedula}</p>
              </div>
              <div className="text-right">
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${resumen.completado ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                  {resumen.completado ? 'Completado' : 'En progreso'}
                </span>
                {typeof resumen.progresoPorcentaje === 'number' && (
                  <p className="text-xs text-gray-400 mt-1">{resumen.progresoPorcentaje}% del formulario</p>
                )}
              </div>
            </div>

            {/* Autorización de tratamiento de datos (Ley 1581) — la acepta el candidato; aquí solo se consulta */}
            <div className={`flex items-start gap-3 rounded-lg border px-4 py-3 ${
              resumen.autorizacionDatos ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'}`}>
              <span className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold text-white ${
                resumen.autorizacionDatos ? 'bg-emerald-500' : 'bg-amber-400'}`}>
                {resumen.autorizacionDatos ? '✓' : '!'}
              </span>
              <div className="text-sm">
                <p className={`font-semibold ${resumen.autorizacionDatos ? 'text-emerald-900' : 'text-amber-900'}`}>
                  Autorización de tratamiento de datos: {resumen.autorizacionDatos ? 'aceptada' : 'pendiente'}
                </p>
                <p className={`text-xs ${resumen.autorizacionDatos ? 'text-emerald-800/80' : 'text-amber-800/80'}`}>
                  {resumen.autorizacionDatos
                    ? `El candidato la aceptó el ${fechaHora(resumen.fechaAutorizacion)} desde su link (Ley 1581 de 2012).`
                    : 'El candidato todavía no la acepta. Hasta que lo haga no puede diligenciar su hoja de vida ni cargar documentos.'}
                </p>
              </div>
            </div>

            {/* Datos personales */}
            <section>
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Datos personales</h3>
              <div className="bg-gray-50 rounded-lg p-4 grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
                {resumen.fechaNacimiento && <div><span className="text-gray-400">F. nacimiento: </span><span className="text-gray-800">{fechaCorta(resumen.fechaNacimiento)}</span></div>}
                {(resumen.fechaExpedicion || resumen.ciudadExpedicion) && <div><span className="text-gray-400">Expedición del documento: </span><span className="text-gray-800">{[resumen.fechaExpedicion && fechaCorta(resumen.fechaExpedicion), resumen.ciudadExpedicion].filter(Boolean).join(' · ')}</span></div>}
                {resumen.ciudadNacimiento && <div><span className="text-gray-400">Ciudad nacimiento: </span><span className="text-gray-800">{resumen.ciudadNacimiento}</span></div>}
                {resumen.ciudadResidencia && <div><span className="text-gray-400">Ciudad residencia: </span><span className="text-gray-800">{resumen.ciudadResidencia}</span></div>}
                {resumen.estadoCivil && <div><span className="text-gray-400">Estado civil: </span><span className="text-gray-800">{resumen.estadoCivil}</span></div>}
                {resumen.rh && <div><span className="text-gray-400">RH: </span><span className="text-gray-800">{resumen.rh}</span></div>}
                {resumen.nivelEducativo && <div><span className="text-gray-400">Nivel educativo: </span><span className="text-gray-800">{resumen.nivelEducativo}</span></div>}
                {resumen.estrato && <div><span className="text-gray-400">Estrato: </span><span className="text-gray-800">{resumen.estrato}</span></div>}
                {resumen.eps && <div><span className="text-gray-400">EPS: </span><span className="text-gray-800">{resumen.eps}</span></div>}
                {resumen.fondoPensiones && <div><span className="text-gray-400">Pensiones: </span><span className="text-gray-800">{resumen.fondoPensiones}</span></div>}
                {resumen.celular && <div><span className="text-gray-400">Celular: </span><span className="text-gray-800">{resumen.celular}</span></div>}
                {resumen.telefonoFijo && <div><span className="text-gray-400">Fijo / WhatsApp: </span><span className="text-gray-800">{resumen.telefonoFijo}</span></div>}
                {resumen.email && <div><span className="text-gray-400">Email: </span><span className="text-gray-800">{resumen.email}</span></div>}
                {resumen.libretaMilitar && <div><span className="text-gray-400">Libreta militar: </span><span className="text-gray-800">{resumen.libretaMilitar}</span></div>}
                {(resumen.visa || resumen.tieneVisa != null) && <div><span className="text-gray-400">Visa: </span><span className="text-gray-800">{resumen.tieneVisa === false ? 'No tiene' : [resumen.visaPais, resumen.visa].filter(Boolean).join(' · ')}</span></div>}
                {(resumen.pasaporte || resumen.tienePasaporte != null) && <div><span className="text-gray-400">Pasaporte: </span><span className="text-gray-800">{resumen.tienePasaporte === false ? 'No tiene' : resumen.pasaporte}</span></div>}
                {resumen.direccion && (
                  <div className="col-span-2">
                    <span className="text-gray-400">Dirección: </span>
                    <span className="text-gray-800">{[resumen.direccion, resumen.direccionAdicional, resumen.barrio].filter(Boolean).join(', ')}</span>
                  </div>
                )}
              </div>
            </section>

            {/* Educación */}
            {resumen.educacion?.length > 0 && (
              <section>
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Educación</h3>
                <div className="overflow-x-auto rounded-lg border border-gray-200">
                  <table className="table-stack w-full text-sm">
                    <thead className="bg-gray-50 text-gray-500 text-xs">
                      <tr>
                        <th className="px-3 py-2 text-left">Nivel</th>
                        <th className="px-3 py-2 text-left">Institución</th>
                        <th className="px-3 py-2 text-left">Ciudad</th>
                        <th className="px-3 py-2 text-left">Estado</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {resumen.educacion.map((e, i) => (
                        <tr key={i}>
                          <td data-label="Nivel" className="px-3 py-2 text-gray-800">{e.nivel ?? '—'}</td>
                          <td data-label="Institución" className="px-3 py-2 text-gray-800">{e.institucion ?? '—'}</td>
                          <td data-label="Ciudad" className="px-3 py-2 text-gray-600">{e.ciudad ?? '—'}</td>
                          <td data-label="Estado" className="px-3 py-2 text-gray-600">{e.enCurso ? 'En curso' : (e.fechaFin ? fechaCorta(e.fechaFin) : '—')}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}

            {/* Experiencia laboral */}
            {resumen.experienciaLaboral?.length > 0 && (
              <section>
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Experiencia laboral</h3>
                <div className="overflow-x-auto rounded-lg border border-gray-200">
                  <table className="table-stack w-full text-sm">
                    <thead className="bg-gray-50 text-gray-500 text-xs">
                      <tr>
                        <th className="px-3 py-2 text-left">Empresa</th>
                        <th className="px-3 py-2 text-left">Cargo</th>
                        <th className="px-3 py-2 text-left">Inicio</th>
                        <th className="px-3 py-2 text-left">Fin</th>
                        <th className="px-3 py-2 text-left">Contrato</th>
                        <th className="px-3 py-2 text-left">Salario</th>
                        <th className="px-3 py-2 text-left">Llamados de atención</th>
                        <th className="px-3 py-2 text-left">Motivo retiro</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {resumen.experienciaLaboral.map((ex, i) => (
                        <tr key={i}>
                          <td data-label="Empresa" className="px-3 py-2 text-gray-800">{ex.empresa ?? '—'}</td>
                          <td data-label="Cargo" className="px-3 py-2 text-gray-800">{ex.cargo ?? '—'}</td>
                          <td data-label="Inicio" className="px-3 py-2 text-gray-600 whitespace-nowrap">{fechaCorta(ex.fechaInicio)}</td>
                          <td data-label="Fin" className="px-3 py-2 text-gray-600 whitespace-nowrap">{ex.laboraActualmente ? 'Actual' : fechaCorta(ex.fechaFin)}</td>
                          <td data-label="Contrato" className="px-3 py-2 text-gray-600">{ex.tipoContrato ?? '—'}</td>
                          <td data-label="Salario" className="px-3 py-2 text-gray-600 whitespace-nowrap">{ex.salario != null ? `$ ${Number(ex.salario).toLocaleString('es-CO')}` : '—'}</td>
                          <td data-label="Llamados de atención" className="px-3 py-2 text-gray-600 max-w-[140px]"><span className="truncate block" title={ex.llamadosAtencion ?? ''}>{ex.llamadosAtencion ?? '—'}</span></td>
                          <td data-label="Motivo retiro" className="px-3 py-2 text-gray-600 max-w-[140px]"><span className="truncate block" title={ex.motivoRetiro ?? ''}>{ex.motivoRetiro ?? '—'}</span></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {resumen.inactividades?.length > 0 && (
                  <div className="mt-2 space-y-2">
                    {resumen.inactividades.map((g, i) => (
                      <div key={i} className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm">
                        <p className="text-red-700 font-medium">
                          Tiempo muerto de {g.diasInactivo} días ({fechaCorta(g.fechaInicio)} → {fechaCorta(g.fechaFin)})
                        </p>
                        {g.justificacion
                          ? <p className="text-red-600 text-xs mt-1">Justificación: {g.justificacion}</p>
                          : <p className="text-amber-600 text-xs mt-1 italic">Sin justificación registrada.</p>}
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )}

            {/* Referencias */}
            {resumen.referencias?.length > 0 && (
              <section>
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Referencias personales</h3>
                <ul className="space-y-2">
                  {resumen.referencias.map((r, i) => (
                    <li key={i} className="text-sm bg-gray-50 rounded-lg px-4 py-2.5 flex flex-wrap gap-x-4 gap-y-0.5">
                      <span className="font-medium text-gray-800">{r.nombre}</span>
                      {r.parentesco && <span className="text-gray-500">{r.parentesco}</span>}
                      {r.tiempoConocimiento && <span className="text-gray-500">{r.tiempoConocimiento}</span>}
                      {r.telefono && <span className="text-gray-400">{r.telefono}</span>}
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {/* Declaraciones del candidato (preguntas de Admin → Hoja de vida del candidato) */}
            {resumen.declaraciones?.length > 0 && (
              <section>
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Declaraciones</h3>
                <ul className="space-y-2">
                  {resumen.declaraciones.map((d, i) => (
                    <li key={i} className={`text-sm rounded-lg px-4 py-2.5 ${d.respuesta ? 'bg-amber-50 border border-amber-200' : 'bg-gray-50'}`}>
                      <p className="text-gray-500">{d.pregunta}</p>
                      <p className="font-medium text-gray-800">{d.respuesta == null ? '—' : d.respuesta ? 'Sí' : 'No'}</p>
                      {d.detalle && <p className="text-gray-700 mt-0.5 whitespace-pre-line">{d.detalle}</p>}
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {/* Documentos */}
            {resumen.documentos?.length > 0 && (
              <section>
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Documentos</h3>
                <ul className="space-y-2">
                  {resumen.documentos.map((doc) => (
                    <li key={doc.id} className="flex items-center justify-between gap-3 bg-gray-50 rounded-lg px-4 py-2.5 text-sm">
                      <div className="min-w-0">
                        <p className="text-gray-800 truncate">{doc.nombreArchivo}</p>
                        <p className="text-xs text-gray-400 mt-0.5">
                          {doc.tipoDocumento}{doc.fechaCarga ? ` · ${fechaHora(doc.fechaCarga)}` : ''}
                        </p>
                      </div>
                      <button onClick={() => descargar(doc)} disabled={descargandoId === doc.id}
                        className="flex-shrink-0 text-xs font-medium px-3 py-1.5 rounded-lg bg-primary-50 text-primary-700 hover:bg-primary-100 disabled:opacity-50 transition-colors">
                        {descargandoId === doc.id ? 'Descargando...' : 'Descargar'}
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </>
        )}
      </div>
    </Modal>
  )
}

/* ─── Modal cambio de estado ─── */
function ModalCambioEstado({ solicitud, modo, onClose, onGuardado }) {
  const idCampos = useId()
  const esDesistimiento = modo === 'desistimiento'
  const esReversion = !esDesistimiento && solicitud.estado === 'CANCELADO'
  const siguientes = esDesistimiento ? ['CANCELADO'] : (TRANSICIONES_GESTOR[solicitud.estado] ?? [])
  const [motivo, setMotivo] = useState(MOTIVO_VACIO)
  const [nuevoEstado, setNuevoEstado] = useState(siguientes[0] ?? '')
  const [observacion, setObservacion] = useState('')
  const [guardando, setGuardando]     = useState(false)
  const [error, setError]             = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (esReversion && !observacion.trim()) {
      setError('Debes indicar el motivo de la solicitud.')
      return
    }
    const conMotivo = esDesistimiento || (!esReversion && nuevoEstado === 'CANCELADO')
    if (conMotivo) {
      const falta = motivoIncompleto(motivo)
      if (falta) { setError(falta); return }
    }
    setGuardando(true); setError(null)
    try {
      if (esDesistimiento) {
        await api.post(`/gestor/solicitudes/${solicitud.idServicio}/desistimiento`, {
          idMotivo: motivo.idMotivo, texto: motivo.texto.trim() || null,
        })
        onGuardado('Desistimiento registrado: se canceló lo que no había iniciado.')
      } else if (!esReversion && nuevoEstado === 'CANCELADO') {
        await api.patch(`/gestor/solicitudes/${solicitud.idServicio}/estado`, {
          estado: 'CANCELADO', idMotivo: motivo.idMotivo, observacion: motivo.texto.trim() || null,
        })
        onGuardado('Solicitud cancelada')
      } else if (esReversion) {
        await api.post(`/gestor/solicitudes/${solicitud.idServicio}/solicitar-reversion`, {
          estado: nuevoEstado,
          observacion,
        })
        onGuardado('Solicitud de reversión enviada. Un administrador debe aprobarla.')
      } else {
        await api.patch(`/gestor/solicitudes/${solicitud.idServicio}/estado`, {
          estado: nuevoEstado,
          observacion: observacion || null,
        })
        onGuardado(`Estado cambiado a ${nuevoEstado}`)
      }
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al procesar la solicitud.')
    } finally { setGuardando(false) }
  }

  if (!siguientes.length) return (
    <Modal titulo="Sin transiciones disponibles" onClose={onClose} ancho="max-w-sm">
      <div className="px-6 py-6 text-center">
        <p className="text-sm text-gray-600 mb-4">No hay transiciones disponibles para el estado <strong>{solicitud.estado}</strong>.</p>
        <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50">Cerrar</button>
      </div>
    </Modal>
  )

  return (
    <Modal
      titulo={esDesistimiento ? 'El candidato desistió' : esReversion ? 'Solicitar reversión de estado' : nuevoEstado === 'CANCELADO' ? 'Cancelar la solicitud' : 'Publicar el estudio'}
      subtitulo={`${solicitud.nombresEvaluado} ${solicitud.apellidosEvaluado}`}
      onClose={onClose} ancho="max-w-md">
      <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        {esReversion && (
          <div className="bg-amber-50 border border-amber-200 text-amber-800 text-xs px-3 py-2.5 rounded-lg">
            Esta solicitud está <strong>cancelada</strong>. El estado no cambiará de inmediato — se enviará una notificación al administrador, quien debe aprobar la reversión.
          </div>
        )}

        <div>
          <p className="text-xs text-gray-500 mb-2">
            Estado actual: <EtiquetaEstado estado={solicitud.estado} />
          </p>
          {esReversion && (
            <>
              <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">
                Estado deseado <span className="text-red-500">*</span>
              </label>
              <select id={`${idCampos}-1`} value={nuevoEstado} onChange={e => setNuevoEstado(e.target.value)} required
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
                {siguientes.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </>
          )}
        </div>

        {esDesistimiento && (
          <p className="text-xs text-gray-600 bg-gray-50 border border-gray-200 rounded-lg px-3 py-2.5">
            Si ningún subproceso ha iniciado, se cancelan todos y la solicitud queda cancelada. Si alguno ya inició, ese sigue hasta
            publicarse, el resto se cancela y la solicitud lleva la marca «Candidato desistió».
          </p>
        )}

        {(esDesistimiento || (!esReversion && nuevoEstado === 'CANCELADO')) ? (
          <SelectorMotivo tipo="CANCELACION" valor={motivo} onChange={setMotivo} etiqueta="Motivo de la cancelación" autoFocus />
        ) : (
        <div>
          <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">
            {esReversion ? 'Motivo de la solicitud' : 'Observación'} {esReversion && <span className="text-red-500">*</span>}
          </label>
          <textarea id={`${idCampos}-2`} value={observacion} onChange={e => setObservacion(e.target.value)} rows={3}
            required={esReversion}
            placeholder={esReversion ? 'Explica por qué debe revertirse esta cancelación…' : (nuevoEstado === 'PUBLICADO' ? 'Concepto final del estudio…' : 'Motivo o notas adicionales…')}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
        </div>
        )}

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Enviando...' : esReversion ? 'Enviar solicitud' : esDesistimiento ? 'Registrar desistimiento' : nuevoEstado === 'CANCELADO' ? 'Cancelar solicitud' : 'Publicar estudio'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/* ─── Sección: gestión del link de validación del evaluado ─── */
function SeccionLink({ idServicio, onToast }) {
  const [link, setLink]                 = useState(null)
  const [cargando, setCargando]         = useState(true)
  const [procesando, setProcesando]     = useState(false)
  const [copiado, setCopiado]           = useState(false)
  const [confirmarRevocar, setConfirmarRevocar]   = useState(false)
  const [confirmarRevertir, setConfirmarRevertir] = useState(false)
  const [confirmarReenvio, setConfirmarReenvio]   = useState(false)
  const [enviandoCorreo, setEnviandoCorreo]       = useState(false)
  const [resumenAbierto, setResumenAbierto]       = useState(false)

  // Un solo link por servicio: generar uno nuevo elimina el anterior (hay que confirmarlo).
  const [confirmarReemplazo, setConfirmarReemplazo] = useState(false)

  const cargar = useCallback(() => {
    setCargando(true)
    return tokenService.porServicio(idServicio)
      .then(setLink)
      .catch(() => setLink(null))
      .finally(() => setCargando(false))
  }, [idServicio])

  useEffect(() => { cargar() }, [cargar])
  useCambios(cargar, { servicios: idServicio })

  const generar = async () => {
    setProcesando(true)
    try {
      const nuevo = await tokenService.generar(idServicio)
      setLink(nuevo)
      onToast(link ? 'Link nuevo generado. El anterior se eliminó y ya no funciona.' : 'Link generado correctamente')
    } catch (e) {
      const msg = e.response?.data?.mensaje
      onToast(msg ?? 'Error al generar el link.', 'error')
    } finally { setProcesando(false); setConfirmarReemplazo(false) }
  }

  const copiar = async () => {
    const url = `${window.location.origin}/evaluado/link/${link.tokenCompleto}`
    try {
      await navigator.clipboard.writeText(url)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2500)
      onToast('Link copiado al portapapeles')
    } catch { onToast('No se pudo copiar el link.', 'error') }
  }

  const enviarCorreo = async () => {
    setEnviandoCorreo(true)
    try {
      await tokenService.enviarCorreo(link.id)
      onToast('Correo enviado al candidato')
    } catch (e) {
      onToast(e.response?.data?.mensaje ?? 'Error al enviar el correo.', 'error')
    } finally { setEnviandoCorreo(false); setConfirmarReenvio(false) }
  }

  const clickEnviarCorreo = () => {
    if (link.fechaPrimerIngreso) setConfirmarReenvio(true)
    else enviarCorreo()
  }

  const revocar = async () => {
    setProcesando(true)
    try {
      await tokenService.revocar(link.id)
      onToast('Link revocado correctamente')
      await cargar()
    } catch (e) {
      onToast(e.response?.data?.mensaje ?? 'Error al revocar el link.', 'error')
    } finally { setProcesando(false); setConfirmarRevocar(false) }
  }

  const revertir = async () => {
    setProcesando(true)
    try {
      const actualizado = await tokenService.revertir(link.id)
      setLink(actualizado)
      onToast('Link revertido a Pendiente. El evaluado puede diligenciar nuevamente.')
    } catch (e) {
      const status = e.response?.status
      const msg    = e.response?.data?.mensaje
      onToast(status === 410 ? 'El link ya expiró. Genera uno nuevo.' : (msg ?? 'Error al revertir el link.'), 'error')
    } finally { setProcesando(false); setConfirmarRevertir(false) }
  }

  if (cargando) {
    return (
      <section>
        <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Link de validación (evaluado)</p>
        <div className="flex justify-center py-4">
          <div className="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent" />
        </div>
      </section>
    )
  }

  const cfg = link ? (ESTADO_LINK_CFG[link.estado] ?? ESTADO_LINK_CFG.EXPIRADO) : null
  const esPendiente  = link?.estado === 'PENDIENTE'
  const esUsado      = link?.estado === 'USADO'
  const esReenviable = link?.estado === 'EXPIRADO' || link?.estado === 'BLOQUEADO' || link?.estado === 'REVOCADO'

  return (
    <section>
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Link de validación (evaluado)</p>

      {!link ? (
        <div className="bg-gray-50 border border-gray-200 rounded-lg px-3 py-3 flex items-center justify-between gap-3">
          <p className="text-sm text-gray-500">No se ha generado ningún link para este evaluado.</p>
          <button onClick={generar} disabled={procesando}
            className="flex-shrink-0 text-xs font-medium px-3 py-1.5 rounded-lg bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white transition-colors">
            {procesando ? 'Generando...' : 'Generar link'}
          </button>
        </div>
      ) : (
        <div className="bg-gray-50 border border-gray-200 rounded-lg px-3 py-3 space-y-2">
          <div className="flex items-center justify-between gap-2">
            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${cfg.color}`}>{cfg.label}</span>
            <span className="text-xs text-gray-400 font-mono">{link.tokenMascarado}</span>
          </div>

          <p className="text-xs text-gray-500">
            {esPendiente && <>Activo mientras el servicio esté abierto — el candidato aún no ha enviado su hoja de vida.</>}
            {esUsado && <>Enviado el {fechaHora(link.fechaUso)} — el candidato sigue pudiendo editar, ver sus citas y documentos.</>}
            {link.estado === 'REVOCADO' && <>Revocado — el candidato ya no puede entrar. Genera uno nuevo si lo necesita.</>}
            {link.estado === 'CERRADO' && <>El servicio ya se cerró; el link dejó de funcionar.</>}
            {link.estado === 'BLOQUEADO' && <>Bloqueado por intentos fallidos.</>}
            {link.estado === 'EXPIRADO' && <>Expiró el {fechaHora(link.fechaExpiracion)}</>}
          </p>

          <div className="bg-white border border-gray-100 rounded-lg px-3 py-2.5 space-y-2">
            <p className="text-xs">
              {link.fechaPrimerIngreso ? (
                <span className="text-gray-600">
                  El candidato ingresó al link el <span className="font-medium text-gray-800">{fechaHora(link.fechaPrimerIngreso)}</span>
                </span>
              ) : (
                <span className="text-amber-600 font-medium">El candidato aún no ha ingresado al link.</span>
              )}
            </p>

            {typeof link.pasoActual === 'number' && (
              <p className="text-xs text-gray-600">
                Va en el paso <span className="font-medium text-gray-800">{link.pasoActual + 1} de {PASOS_EVALUADO.length}</span>
                {PASOS_EVALUADO[link.pasoActual] ? <> — {PASOS_EVALUADO[link.pasoActual]}</> : ''}
              </p>
            )}

            {typeof link.progresoFormulario === 'number' && (
              <div>
                <div className="flex items-center justify-between text-xs text-gray-400 mb-1">
                  <span>Progreso del formulario</span>
                  <span>{link.progresoFormulario}%</span>
                </div>
                <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden">
                  <div className="h-full bg-primary-500 rounded-full" style={{ width: `${link.progresoFormulario}%` }} />
                </div>
              </div>
            )}

            <p className="text-xs text-gray-400">
              Generado el {fechaHora(link.fechaCreacion)}{link.generadoPorNombre ? ` por ${link.generadoPorNombre}` : ''}
            </p>

            {link.intentosFallidos > 0 && (
              <p className="text-xs text-red-500">
                {link.intentosFallidos} intento{link.intentosFallidos !== 1 ? 's' : ''} fallido{link.intentosFallidos !== 1 ? 's' : ''} de acceso
              </p>
            )}

            {link.fechaPrimerIngreso && (
              <button onClick={() => setResumenAbierto(true)}
                className="w-full text-xs font-medium px-3 py-2 rounded-lg bg-primary-50 text-primary-700 hover:bg-primary-100 transition-colors text-center">
                {esUsado ? 'Ver información registrada por el candidato' : 'Ver avance registrado hasta ahora'}
              </button>
            )}
          </div>

          <div className="flex items-center gap-2 flex-wrap pt-1">
            {/* Enviado también sigue activo (V197): se puede volver a compartir o revocar. */}
            {(esPendiente || esUsado) && (
              <>
                <button onClick={copiar}
                  className={`text-xs font-medium px-2.5 py-1 rounded-lg transition-colors ${
                    copiado ? 'bg-emerald-100 text-emerald-700' : 'bg-primary-100 text-primary-700 hover:bg-primary-200'
                  }`}>
                  {copiado ? '¡Copiado!' : 'Copiar link'}
                </button>
                <button onClick={clickEnviarCorreo} disabled={enviandoCorreo}
                  className="text-xs font-medium px-2.5 py-1 rounded-lg border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors disabled:opacity-50">
                  {enviandoCorreo ? 'Enviando...' : 'Enviar por correo'}
                </button>
                <button onClick={() => setConfirmarRevocar(true)} disabled={procesando}
                  className="text-xs font-medium px-2.5 py-1 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 transition-colors">
                  Revocar
                </button>
              </>
            )}
            {esUsado && (
              <button onClick={() => setConfirmarRevertir(true)} disabled={procesando}
                className="text-xs font-medium px-2.5 py-1 rounded-lg border border-amber-200 text-amber-600 hover:bg-amber-50 transition-colors">
                Revertir a Pendiente
              </button>
            )}
            {(esReenviable || esPendiente || esUsado) && (
              <button onClick={() => setConfirmarReemplazo(true)} disabled={procesando}
                className="text-xs font-medium px-2.5 py-1 rounded-lg border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors">
                {procesando ? 'Generando...' : 'Generar nuevo link'}
              </button>
            )}
          </div>
        </div>
      )}

      <p className="mt-2 text-[11px] text-gray-400">
        Cada servicio tiene un solo link. Si generas uno nuevo, el anterior se elimina y deja de funcionar;
        la información que el candidato ya registró se conserva.
      </p>

      {confirmarReemplazo && (
        <ConfirmModal
          titulo="Generar nuevo link"
          danger
          confirmLabel="Generar y reemplazar"
          cargando={procesando}
          onConfirmar={generar}
          onCancelar={() => setConfirmarReemplazo(false)}>
          <p className="text-sm text-gray-600">
            El link actual se eliminará y el candidato ya no podrá entrar con él. Tendrás que compartirle el nuevo.
            Su hoja de vida, documentos y autorización se conservan.
          </p>
        </ConfirmModal>
      )}

      {confirmarRevocar && (
        <ConfirmModal
          titulo="Revocar link"
          danger
          confirmLabel="Revocar"
          cargando={procesando}
          onConfirmar={revocar}
          onCancelar={() => setConfirmarRevocar(false)}>
          <p className="text-sm text-gray-600">El link quedará inactivo inmediatamente.</p>
        </ConfirmModal>
      )}

      {confirmarRevertir && (
        <ConfirmModal
          titulo="Revertir a Pendiente"
          confirmLabel="Revertir"
          cargando={procesando}
          onConfirmar={revertir}
          onCancelar={() => setConfirmarRevertir(false)}>
          <p className="text-sm text-gray-600">El evaluado podrá diligenciar el formulario nuevamente.</p>
          <p className="text-xs text-primary-600 mt-2 font-medium">Solo es posible si el link aún no ha expirado.</p>
        </ConfirmModal>
      )}

      {confirmarReenvio && (
        <ConfirmModal
          titulo="El candidato ya ingresó a este link"
          confirmLabel="Enviar de todas formas"
          cargando={enviandoCorreo}
          onConfirmar={enviarCorreo}
          onCancelar={() => setConfirmarReenvio(false)}>
          <p className="text-sm text-gray-600">
            Ingresó el <strong>{fechaHora(link.fechaPrimerIngreso)}</strong>
            {typeof link.progresoFormulario === 'number' && (
              <> y lleva un <strong>{link.progresoFormulario}%</strong> del formulario completado</>
            )}.
          </p>
          <p className="text-xs text-gray-500 mt-2">¿Quieres reenviarle el correo de todas formas?</p>
        </ConfirmModal>
      )}

      {resumenAbierto && (
        <ModalResumenEvaluado
          idServicio={idServicio}
          nombreEvaluado={link ? `${link.nombresEvaluado ?? ''} ${link.apellidosEvaluado ?? ''}`.trim() : undefined}
          onClose={() => setResumenAbierto(false)}
        />
      )}
    </section>
  )
}

/* ─── Fila de contacto — plana, sin recuadro, con acento sutil al pasar el mouse ─── */
function TarjetaContacto({ icono, label, valor, href, copiado, onCopiar }) {
  return (
    <div className="flex items-center gap-3 py-2 -mx-2 px-2 rounded-lg hover:bg-gray-50/80 transition-colors group">
      <span className="text-gray-400 group-hover:text-primary-500 flex-shrink-0 transition-colors">{icono}</span>
      <div className="min-w-0 flex-1">
        <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wide">{label}</p>
        {valor ? (
          <a href={href} className="text-sm font-medium text-gray-800 hover:text-primary-600 break-all transition-colors">{valor}</a>
        ) : (
          <p className="text-sm text-gray-400">—</p>
        )}
      </div>
      {valor && (
        <button onClick={onCopiar} title="Copiar" type="button"
          className={`flex-shrink-0 p-1.5 rounded-md transition-[color,background-color,opacity] ${
            copiado ? 'opacity-100 bg-emerald-100 text-emerald-600' : 'opacity-0 group-hover:opacity-100 text-gray-400 hover:text-primary-600 hover:bg-primary-100'
          }`}>
          {copiado ? (
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          ) : (
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
          )}
        </button>
      )}
    </div>
  )
}

const ICONO_TELEFONO = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
  </svg>
)

const ICONO_EMAIL = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
  </svg>
)

/* ─── Sección: ficha del candidato, segmentada e interactiva ─── */
function SeccionCandidato({ detalle, onCiudadCorregida }) {
  const [copiado, setCopiado] = useState(null)
  const [resumenAbierto, setResumenAbierto] = useState(false)
  const [editandoCiudad, setEditandoCiudad] = useState(false)
  const [nuevaCiudad, setNuevaCiudad] = useState(null)
  const [guardandoCiudad, setGuardandoCiudad] = useState(false)
  const [errorCiudad, setErrorCiudad] = useState(null)

  const copiar = async (texto, campo) => {
    if (!texto) return
    try {
      await navigator.clipboard.writeText(texto)
      setCopiado(campo)
      setTimeout(() => setCopiado(null), 2000)
    } catch { /* silencioso */ }
  }

  const abrirEdicionCiudad = () => {
    setNuevaCiudad(detalle?.idCiudad ?? null)
    setErrorCiudad(null)
    setEditandoCiudad(true)
  }

  const guardarCiudad = async () => {
    if (!nuevaCiudad) { setErrorCiudad('Selecciona una ciudad.'); return }
    setGuardandoCiudad(true)
    setErrorCiudad(null)
    try {
      const r = await api.patch(`/gestor/solicitudes/${detalle.idServicio}/ciudad`, { idCiudad: nuevaCiudad })
      onCiudadCorregida?.(r.data)
      setEditandoCiudad(false)
    } catch (err) {
      setErrorCiudad(err.response?.data?.mensaje ?? 'No se pudo actualizar la ciudad.')
    } finally {
      setGuardandoCiudad(false)
    }
  }

  return (
    <section>
      {/* Identidad — seleccionable: abre la hoja de vida completa que diligenció el candidato */}
      <button
        type="button"
        onClick={() => setResumenAbierto(true)}
        disabled={!detalle?.idServicio}
        className="w-full flex items-center gap-3 pb-3 mb-1 border-b border-gray-100 -mx-2 px-2 py-1.5 rounded-lg hover:bg-gray-50 transition-colors text-left disabled:cursor-default disabled:hover:bg-transparent group">
        <span className="w-10 h-10 rounded-full bg-primary-600 text-white flex items-center justify-center text-sm font-semibold flex-shrink-0">
          {iniciales(detalle?.nombresEvaluado, detalle?.apellidosEvaluado)}
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-gray-900 truncate">
            {detalle?.nombresEvaluado} {detalle?.apellidosEvaluado}
          </p>
          <p className="text-xs text-gray-400 mt-0.5">
            Candidato · CC {detalle?.cedulaEvaluado ?? '—'}{detalle?.cargo ? ` · ${detalle.cargo}` : ''}
          </p>
        </div>
        <svg className="h-4 w-4 text-gray-300 group-hover:text-primary-500 flex-shrink-0 transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
      </button>

      {resumenAbierto && (
        <ModalResumenEvaluado
          idServicio={detalle.idServicio}
          nombreEvaluado={`${detalle?.nombresEvaluado ?? ''} ${detalle?.apellidosEvaluado ?? ''}`.trim()}
          onClose={() => setResumenAbierto(false)}
        />
      )}

      {/* Contacto — lista plana, sin recuadros por dato */}
      <div className="divide-y divide-gray-50">
        <TarjetaContacto
          label="Celular"
          valor={detalle?.celularEvaluado}
          href={detalle?.celularEvaluado ? `tel:${detalle.celularEvaluado}` : undefined}
          copiado={copiado === 'celular'}
          onCopiar={() => copiar(detalle?.celularEvaluado, 'celular')}
          icono={ICONO_TELEFONO}
        />
        <TarjetaContacto
          label="Email"
          valor={detalle?.emailEvaluado}
          href={detalle?.emailEvaluado ? `mailto:${detalle.emailEvaluado}` : undefined}
          copiado={copiado === 'email'}
          onCopiar={() => copiar(detalle?.emailEvaluado, 'email')}
          icono={ICONO_EMAIL}
        />

        <div className="flex items-center gap-3 py-2 -mx-2 px-2 rounded-lg hover:bg-gray-50/80 transition-colors group">
          <span className="text-gray-400 group-hover:text-primary-500 flex-shrink-0 transition-colors">📍</span>
          <div className="min-w-0 flex-1">
            <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wide">Ciudad</p>
            <p className="text-sm font-medium text-gray-800">{detalle?.nombreCiudad ?? '—'}</p>
          </div>
          <button onClick={abrirEdicionCiudad} title="Corregir ciudad" type="button"
            className="flex-shrink-0 p-1.5 rounded-md opacity-0 group-hover:opacity-100 text-gray-400 hover:text-primary-600 hover:bg-primary-100 transition-[color,background-color,opacity]">
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z" />
            </svg>
          </button>
        </div>
      </div>

      {detalle?.historialRecargoCiudad?.length > 0 && (
        <div className="mt-2 rounded-lg border border-gray-200 px-3 py-2 text-xs">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-gray-700">
            <span>
              📍 Recargo ciudad — pagado: <span className="font-semibold text-green-700">{formatearPrecioCOP(detalle.totalPagadoRecargoCiudad)}</span>
            </span>
            {detalle.totalPendienteRecargoCiudad !== 0 && (
              <span className={detalle.totalPendienteRecargoCiudad < 0 ? 'text-green-700' : 'text-red-700'}>
                {detalle.totalPendienteRecargoCiudad < 0 ? 'crédito: ' : 'pendiente: '}
                <span className="font-semibold">{formatearPrecioCOP(detalle.totalPendienteRecargoCiudad)}</span>
              </span>
            )}
          </div>
          <ul className="mt-1.5 space-y-1 border-t border-gray-100 pt-1.5 text-[11px] text-gray-500">
            {detalle.historialRecargoCiudad.map((m, i) => (
              <li key={i} className="flex items-center justify-between gap-2">
                <span>
                  {m.ciudad}{m.motivo ? ` — ${m.motivo}` : ''} · {fechaCorta(m.fechaGenerado)}
                </span>
                <span className={`flex-shrink-0 font-medium ${m.monto < 0 ? 'text-green-600' : 'text-gray-700'}`}>
                  {formatearPrecioCOP(m.monto)}
                  <span className={`ml-1.5 rounded-full px-1.5 py-0.5 ${m.estado === 'PAGADO' ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-700'}`}>
                    {m.estado}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {editandoCiudad && (
        <Modal titulo="Corregir ciudad del candidato" subtitulo="Si ya se había cobrado un recargo, se genera un crédito" onClose={() => setEditandoCiudad(false)} ancho="max-w-sm">
          <div className="px-6 py-5 space-y-4">
            {errorCiudad && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{errorCiudad}</div>
            )}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Ciudad correcta</label>
              <CiudadSelect value={nuevaCiudad} onChange={setNuevaCiudad} />
            </div>
            <p className="text-xs text-gray-400">
              Si el recargo de la ciudad anterior ya se había cobrado, se registra como crédito para la
              siguiente compra del cliente en vez de un reembolso. Si la ciudad nueva también tiene
              recargo, queda pendiente de cobro.
            </p>
          </div>
          <div className="px-6 pb-5 flex justify-end gap-3">
            <button type="button" onClick={() => setEditandoCiudad(false)} disabled={guardandoCiudad}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-50/70 transition-colors">
              Cancelar
            </button>
            <button type="button" onClick={guardarCiudad} disabled={guardandoCiudad}
              className="px-4 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50 transition-colors">
              {guardandoCiudad ? 'Guardando...' : 'Guardar'}
            </button>
          </div>
        </Modal>
      )}
    </section>
  )
}

/* ─── Sección: ficha del cliente que solicitó el servicio ─── */
function SeccionCliente({ detalle }) {
  const [copiado, setCopiado] = useState(null)

  const copiar = async (texto, campo) => {
    if (!texto) return
    try {
      await navigator.clipboard.writeText(texto)
      setCopiado(campo)
      setTimeout(() => setCopiado(null), 2000)
    } catch { /* silencioso */ }
  }

  return (
    <section>
      {/* Identidad */}
      <div className="flex items-center gap-3 pb-3 mb-1 border-b border-gray-100">
        <span className="w-10 h-10 rounded-full bg-slate-500 text-white flex items-center justify-center text-sm font-semibold flex-shrink-0">
          {inicialesTexto(detalle?.nombreCliente)}
        </span>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-gray-900 truncate">{detalle?.nombreCliente ?? '—'}</p>
          <p className="text-xs text-gray-400 mt-0.5">
            Cliente{detalle?.nitCliente ? ` · NIT ${detalle.nitCliente}` : ''}
          </p>
        </div>
      </div>

      {/* Contacto — lista plana, sin recuadros por dato */}
      <div className="divide-y divide-gray-50">
        <TarjetaContacto
          label="Teléfono"
          valor={detalle?.telefonoCliente}
          href={detalle?.telefonoCliente ? `tel:${detalle.telefonoCliente}` : undefined}
          copiado={copiado === 'telefono'}
          onCopiar={() => copiar(detalle?.telefonoCliente, 'telefono')}
          icono={ICONO_TELEFONO}
        />
        <TarjetaContacto
          label="Email"
          valor={detalle?.emailCliente}
          href={detalle?.emailCliente ? `mailto:${detalle.emailCliente}` : undefined}
          copiado={copiado === 'email'}
          onCopiar={() => copiar(detalle?.emailCliente, 'email')}
          icono={ICONO_EMAIL}
        />
      </div>
    </section>
  )
}

/* ─── Sección: subprocesos (programación) del proceso solicitado ─── */
function SeccionProgramacion({ idProceso }) {
  const [pasos, setPasos]       = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError]       = useState(false)

  useEffect(() => {
    if (!idProceso) { setCargando(false); return }
    setCargando(true); setError(false)
    catalogoService.obtenerSubprocesos(idProceso)
      .then(setPasos)
      .catch(() => setError(true))
      .finally(() => setCargando(false))
  }, [idProceso])

  if (cargando) {
    return (
      <div className="flex justify-center py-10">
        <div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" />
      </div>
    )
  }

  if (error) {
    return <p className="text-sm text-red-500 text-center py-8">No se pudo cargar la programación del proceso.</p>
  }

  if (!pasos?.length) {
    return <p className="text-sm text-gray-400 text-center py-8">Este proceso no tiene subprocesos configurados en el catálogo.</p>
  }

  return (
    <section>
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">
        Subprocesos del proceso solicitado
      </p>
      <div className="space-y-2">
        {pasos.map((p, i) => (
          <div key={i} className="flex items-start gap-3 border border-gray-200 rounded-lg px-3 py-2.5">
            <span className="w-6 h-6 rounded-full bg-primary-100 text-primary-700 text-xs font-semibold flex items-center justify-center flex-shrink-0 mt-0.5">
              {p.ordenEnProceso ?? i + 1}
            </span>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-medium text-gray-900">{p.nombreProgreso}</p>
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium flex-shrink-0 ${
                  p.obligatorio ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-500'
                }`}>
                  {p.obligatorio ? 'Obligatorio' : 'Opcional'}
                </span>
              </div>
              {p.descripcion && <p className="text-xs text-gray-500 mt-0.5">{p.descripcion}</p>}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

const TABS_DETALLE = [
  { key: 'informacion',  label: 'Información básica', icono: '🧾' },
  { key: 'programacion', label: 'Programación',       icono: '🗓️' },
  { key: 'link',         label: 'Link',               icono: '🔗' },
  { key: 'riesgo',       label: 'Riesgo',             icono: '🛡️' },
  { key: 'historial',    label: 'Historial',          icono: '🕒' },
]

/* ─── Modal detalle ─── */
function ModalDetalle({ solicitud, onClose, onToast }) {
  const [detalle, setDetalle] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [tab, setTab] = useState('informacion')
  const [verTodoHistorial, setVerTodoHistorial] = useState(false)

  useEffect(() => {
    api.get(`/gestor/solicitudes/${solicitud.idServicio}`)
      .then(r => setDetalle(r.data))
      .finally(() => setCargando(false))
  }, [solicitud.idServicio])
  // Tiempo real: si otro usuario cambia este servicio, el detalle se actualiza solo.
  useCambios(() => api.get(`/gestor/solicitudes/${solicitud.idServicio}`).then(r => setDetalle(r.data)).catch(() => {}),
    { servicios: solicitud.idServicio })

  const numHistorial = detalle?.historial?.length ?? 0

  return (
    <Modal titulo={`Detalle de solicitud #${solicitud.idServicio}`} onClose={onClose} ancho="max-w-2xl">
      <>
        {cargando ? (
          <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
        ) : (
          <>
            {/* Pestañas */}
            <div className="px-6 pt-3 border-b border-gray-100 flex items-center gap-1 flex-wrap sticky top-0 bg-white z-10">
              {TABS_DETALLE.map(t => (
                <button key={t.key} onClick={() => setTab(t.key)}
                  className={`px-3 py-2 text-xs font-medium transition-colors border-b-2 -mb-px flex items-center gap-1.5 whitespace-nowrap ${
                    tab === t.key
                      ? 'border-primary-600 text-primary-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}>
                  <span>{t.icono}</span>
                  {t.label}
                  {t.key === 'historial' && numHistorial > 0 && (
                    <span className="bg-gray-100 text-gray-500 text-[10px] font-semibold rounded-full px-1.5 py-0.5">{numHistorial}</span>
                  )}
                </button>
              ))}
            </div>

            {/* Contenido de la pestaña activa */}
            <div className="px-6 py-5 min-h-[220px]">
              {tab === 'informacion' && (
                <div className="space-y-5">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
                    <SeccionCandidato detalle={detalle} onCiudadCorregida={setDetalle} />
                    <SeccionCliente detalle={detalle} />
                  </div>

                  <div className="flex items-center justify-between pt-4 border-t border-gray-100">
                    <div>
                      <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wide">Proceso solicitado</p>
                      <p className="text-sm font-medium text-gray-800 mt-0.5">{detalle?.proceso}</p>
                    </div>
                    <EtiquetaEstado estado={detalle?.estado} />
                  </div>

                  {detalle?.notas && (
                    <section>
                      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1">Notas</p>
                      <p className="text-sm text-gray-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">{detalle.notas}</p>
                    </section>
                  )}
                </div>
              )}

              {tab === 'programacion' && <SeccionProgramacion idProceso={detalle?.idProceso} />}

              {tab === 'link' && <SeccionLink idServicio={solicitud.idServicio} onToast={onToast} />}

              {tab === 'riesgo' && <RiesgoServicio idServicio={solicitud.idServicio} onToast={onToast} />}

              {tab === 'historial' && (
                numHistorial === 0 ? (
                  <p className="text-sm text-gray-400 text-center py-8">Sin historial de cambios registrado.</p>
                ) : (
                  <div>
                    {(verTodoHistorial ? detalle.historial : detalle.historial.slice(0, 4)).map((h, i, arr) => (
                      <div key={i} className="relative pl-7 pb-5 last:pb-0">
                        {i < arr.length - 1 && (
                          <span className="absolute left-[5px] top-3.5 bottom-0 w-px bg-gray-100" />
                        )}
                        <span className={`absolute left-0 top-1 w-2.5 h-2.5 rounded-full ring-4 ring-white ${DOT_ESTADO[h.estadoNuevo] ?? 'bg-gray-300'}`} />

                        <div className="flex items-center justify-between gap-3">
                          <span>
                            <EtiquetaEstado estado={h.estadoNuevo} />
                          </span>
                          <span className="text-xs text-gray-400 whitespace-nowrap">{fechaHora(h.fechaCambio)}</span>
                        </div>
                        <p className="text-xs text-gray-400 mt-1">{h.usuario}</p>
                        {h.observacion && <p className="text-sm text-gray-700 mt-1">{h.observacion}</p>}
                      </div>
                    ))}

                    {numHistorial > 4 && (
                      <button onClick={() => setVerTodoHistorial(v => !v)}
                        className="text-xs font-medium text-primary-600 hover:text-primary-700 mt-1">
                        {verTodoHistorial ? 'Mostrar menos' : `Mostrar ${numHistorial - 4} más`}
                      </button>
                    )}
                  </div>
                )
              )}
            </div>
          </>
        )}

        <div className="px-6 py-4 border-t border-gray-100 sticky bottom-0 bg-white">
          <button onClick={onClose} className="w-full py-2 text-sm text-gray-600 border border-gray-200 rounded-xl hover:bg-gray-50/70 transition-colors">Cerrar</button>
        </div>
      </>
    </Modal>
  )
}

/* ─── Página principal ─── */
// PB-05: las tarjetas de estado, el grupo de tiempo, cliente, estudio, responsable y el buscador se combinan y quedan EN LA URL (para volver atrás o
// compartir la vista). No hay filtro por riesgo; la columna Riesgo se conserva. Cada fila trae UN botón de «Próxima acción» (la calcula el servidor).

const GRUPOS_TIEMPO = [['VENCIDAS', 'Vencidas'], ['VENCE_HOY', 'Vence hoy'], ['VENCE_MANANA', 'Vence mañana'], ['A_TIEMPO', 'A tiempo'], ['EN_PAUSA', 'En pausa']]
const PASTA_TEXTO = { PENDIENTE: 'pendiente', PRELIMINAR: 'preliminar', EMITIDA: 'emitida' }
const ACCION_RELLENA = new Set(['REVISAR', 'EMITIR_PASTA', 'ENVIAR_ENLACE'])
const FOCO = 'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600'
const nfN = new Intl.NumberFormat('es-CO')

export default function Solicitudes() {
  const [searchParams, setSearchParams] = useSearchParams()
  // Enlaces viejos (?estado=VENCIDAS) pasan al filtro de tiempo
  const estadoParam = (searchParams.get('estado') ?? 'TODOS') === 'VENCIDAS' ? 'TODOS' : (searchParams.get('estado') ?? 'TODOS')
  const tiempoParam = searchParams.get('estado') === 'VENCIDAS' ? 'VENCIDAS' : (searchParams.get('tiempo') ?? '')
  const clienteParam = searchParams.get('cliente') ?? ''
  const estudioParam = searchParams.get('estudio') ?? ''
  const responsableParam = searchParams.get('responsable') ?? ''
  const qParam = searchParams.get('q') ?? ''

  const [solicitudes, setSolicitudes]     = useState([])
  const [cargando, setCargando]           = useState(true)
  const [error, setError]                 = useState(null)
  const [toast, setToast]                 = useState(null)
  const [pagina, setPagina]               = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)
  const POR_PAGINA = 10
  const [busqueda, setBusqueda]           = useState(qParam)
  const [tiempos, setTiempos]             = useState(null)
  const [opciones, setOpciones]           = useState({ clientes: [], estudios: [], responsables: [] })
  const [cambiandoEstado, setCambiandoEstado] = useState(null)
  const [emitiendo, setEmitiendo] = useState(null)   // PB-03: ventana «Emitir estudio completo»
  const [enviandoEnlace, setEnviandoEnlace] = useState(null)   // PB-04: ventana «Enviar enlace al candidato»
  const [detalle, setDetalle]             = useState(null)
  // PC-06 (PB-H-45): los servicios con sus subprocesos abiertos viven en la URL (?abierto=52 o ?abierto=52,53): al cerrar una ventana, recargar o volver a
  // entrar después de que se cerró la sesión, la pantalla queda como estaba. Se escribe con replace para no llenar el historial.
  const abiertoParam = searchParams.get('abierto') ?? ''
  const abiertos = useMemo(() => new Set(abiertoParam.split(',').map(Number).filter(Number.isFinite).filter(n => n > 0)), [abiertoParam])
  const cambiarAbiertos = useCallback((cambio) => setSearchParams(prev => {
    const n = new URLSearchParams(prev)
    const nuevo = cambio(new Set((n.get('abierto') ?? '').split(',').map(Number).filter(Number.isFinite).filter(x => x > 0)))
    if (nuevo.size > 0) n.set('abierto', [...nuevo].join(',')); else n.delete('abierto')
    return n
  }, { replace: true }), [setSearchParams])
  const [cargandoPendientesId, setCargandoPendientesId] = useState(null)
  const [asignandoProgramadorPara, setAsignandoProgramadorPara] = useState(null)
  const [avisandoId, setAvisandoId]       = useState(null)
  const [reintentandoPdf, setReintentandoPdf] = useState(null)

  /** Cambia filtros en la URL (y vuelve a la primera página). Un valor vacío quita el filtro. */
  const actualizar = useCallback((cambios) => {
    setSearchParams(prev => {
      const n = new URLSearchParams(prev)
      n.delete('expandir'); n.delete('emitir'); n.delete('enlace')
      if (n.get('estado') === 'VENCIDAS') { n.delete('estado'); n.set('tiempo', 'VENCIDAS') }
      Object.entries(cambios).forEach(([k, v]) => { if (v === '' || v == null || v === 'TODOS') n.delete(k); else n.set(k, v) })
      return n
    })
  }, [setSearchParams])

  const toggleExpandir = (idServicio) => cambiarAbiertos(prev => {
    const next = new Set(prev)
    if (next.has(idServicio)) next.delete(idServicio)
    else next.add(idServicio)
    return next
  })

  const filtrosAlcance = { idCliente: clienteParam || undefined, idProceso: estudioParam || undefined, idResponsable: responsableParam || undefined }

  const cargar = useCallback(async (page = 0, silencioso = false) => {
    if (!silencioso) { setCargando(true); setError(null) }
    try {
      const params = new URLSearchParams({ page, size: POR_PAGINA })
      if (estadoParam !== 'TODOS') params.append('estado', estadoParam)
      if (tiempoParam) params.append('tiempo', tiempoParam)
      if (tiempoParam === 'VENCIDAS') params.append('orden', 'retraso')
      if (clienteParam) params.append('idCliente', clienteParam)
      if (estudioParam) params.append('idProceso', estudioParam)
      if (responsableParam) params.append('idResponsable', responsableParam)
      if (qParam) params.append('q', qParam)
      const r = await api.get(`/gestor/solicitudes?${params}`)
      setSolicitudes(r.data.content ?? [])
      setTotalElementos(r.data.totalElements ?? 0)
    } catch { if (!silencioso) setError('No se pudieron cargar las solicitudes.') }
    finally { if (!silencioso) setCargando(false) }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [estadoParam, tiempoParam, clienteParam, estudioParam, responsableParam, qParam])

  const cargarTiempos = useCallback(() => {
    bandejaService.tiempos({ idCliente: clienteParam || undefined, idProceso: estudioParam || undefined, idResponsable: responsableParam || undefined })
      .then(t => setTiempos(t.conteo)).catch(() => setTiempos(null))
  }, [clienteParam, estudioParam, responsableParam])

  useEffect(() => { bandejaService.opcionesFiltro().then(setOpciones).catch(() => {}) }, [])
  useEffect(() => { setPagina(0); cargar(0) }, [cargar])
  useEffect(() => { cargarTiempos() }, [cargarTiempos])

  // El buscador escribe en la URL con una pausa: busca en TODAS las solicitudes del gestor, no solo en la página visible.
  useEffect(() => {
    if (busqueda.trim() === qParam) return undefined
    const t = setTimeout(() => actualizar({ q: busqueda.trim() }), 350)
    return () => clearTimeout(t)
  }, [busqueda, qParam, actualizar])

  // Tiempo real: la lista y los conteos se actualizan solos (nuevas solicitudes, cambios de estado).
  useCambios(() => { cargar(pagina, true); cargarTiempos() })

  // PC-01: mientras algún PDF del estudio se está armando, la lista se refresca sola (no hay aviso en tiempo real de ese archivo).
  const hayPdfGenerando = solicitudes.some(s => s.estudioPdfEstado === 'GENERANDO')
  useEffect(() => {
    if (!hayPdfGenerando) return undefined
    return iniciarIntervaloVisible(() => cargar(pagina, true), 8000)
  }, [hayPdfGenerando, cargar, pagina])

  const reintentarPdf = async (s) => {
    setReintentandoPdf(s.idServicio)
    try {
      const r = await api.post(`/requests/${s.idServicio}/estudio-pdf/reintentar`)
      setToast({ mensaje: `Generando de nuevo el PDF del estudio #${s.idServicio} · v${r.data.version}…`, tipo: 'exito' })
      cargar(pagina, true)
    } catch (err) {
      setToast({ mensaje: `${err.response?.data?.mensaje ?? 'No se pudo reintentar el PDF.'} Intenta de nuevo en un momento.`, tipo: 'error' })
    } finally { setReintentandoPdf(null) }
  }

  // Entradas desde el tablero: ?id= abre «Ver»; ?expandir= abre los subprocesos; ?emitir= y ?enlace= abren sus ventanas (una sola vez).
  useEffect(() => {
    const idParam = searchParams.get('id')
    const expandir = searchParams.get('expandir')
    const emitir = searchParams.get('emitir')
    const enlace = searchParams.get('enlace')
    if (!idParam && !expandir && !emitir && !enlace) return
    if (idParam) setDetalle({ idServicio: Number(idParam) })
    const abrir = async (id, fn) => {
      try {
        const r = await api.get('/gestor/solicitudes', { params: { q: `#${id}`, size: 5 } })
        const s = (r.data.content ?? []).find(x => x.idServicio === Number(id))
        if (s) fn(s)
        else setToast({ mensaje: `No se encontró la solicitud #${id}.`, tipo: 'error' })
      } catch { setToast({ mensaje: 'No se pudo abrir la solicitud.', tipo: 'error' }) }
    }
    if (emitir) abrir(emitir, setEmitiendo)
    if (enlace) abrir(enlace, setEnviandoEnlace)
    // Una sola escritura a la URL: ?expandir= (entrada desde el tablero) pasa a ?abierto= y los demás avisos de entrada se borran.
    setSearchParams(prev => {
      const n = new URLSearchParams(prev)
      if (expandir) {
        const ya = (n.get('abierto') ?? '').split(',').filter(Boolean)
        if (!ya.includes(String(Number(expandir)))) ya.push(String(Number(expandir)))
        n.set('abierto', ya.join(','))
      }
      ;['id', 'expandir', 'emitir', 'enlace'].forEach(k => n.delete(k))
      return n
    }, { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Trae los subprocesos de poligrafía/visita del servicio (Pendientes o ya Programados — a estos
  // últimos también se les puede cambiar quién los coordina) y abre el selector de programador
  // para encargárselos/reasignárselos todos de una vez.
  const abrirAsignarProgramador = async (idServicio) => {
    setCargandoPendientesId(idServicio)
    try {
      const r = await asignacionService.listar({ idServicio, size: 50 })
      const elegibles = (r.content ?? []).filter(sp =>
        sp.rolResponsable !== 'ANALISTA_INTERNO' && (sp.estado === 'PENDIENTE' || sp.estado === 'PROGRAMANDO' || sp.estado === 'EN_EJECUCION'))
      if (elegibles.length === 0) {
        setToast({ mensaje: 'No hay subprocesos de poligrafía/visita a los que asignarles programador en este servicio.', tipo: 'error' })
        return
      }
      setAsignandoProgramadorPara(elegibles)
    } catch (err) {
      setToast({ mensaje: err.response?.data?.mensaje ?? 'No se pudieron cargar los subprocesos.', tipo: 'error' })
    } finally {
      setCargandoPendientesId(null)
    }
  }

  const avisarProgramador = async (idServicio) => {
    setAvisandoId(idServicio)
    try {
      const r = await bandejaService.avisarProgramador(idServicio)
      setToast({ mensaje: `Aviso enviado al programador · ${r.citas} ${r.citas === 1 ? 'cita' : 'citas'}. Queda registrado.`, tipo: 'exito' })
    } catch (err) {
      setToast({ mensaje: `${err.response?.data?.mensaje ?? 'No se pudo avisar al programador.'} Intenta de nuevo con «Avisar al programador».`, tipo: 'error' })
    } finally { setAvisandoId(null) }
  }

  /** El botón de «Próxima acción» de la fila. */
  const ejecutarAccion = (s) => {
    switch (s.proximaAccion) {
      case 'EMITIR_PASTA': setEmitiendo(s); break
      case 'ENVIAR_ENLACE': case 'REENVIAR_ENLACE': setEnviandoEnlace(s); break
      case 'AVISAR_PROGRAMADOR': avisarProgramador(s.idServicio); break
      case 'VER_EXPEDIENTE': setDetalle(s); break
      default: cambiarAbiertos(prev => new Set(prev).add(s.idServicio))   // revisar, asignar, ver avance: se abren sus subprocesos
    }
  }

  const hayFiltros = estadoParam !== 'TODOS' || tiempoParam || clienteParam || estudioParam || responsableParam || qParam
  const limpiar = () => { setBusqueda(''); setSearchParams(new URLSearchParams()) }
  const selectCls = `min-h-[44px] rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-900 ${FOCO}`

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Solicitudes</h1>
          <p className="mt-0.5 text-sm text-gray-700">
            {nfN.format(totalElementos)} solicitud{totalElementos !== 1 ? 'es' : ''} {hayFiltros ? 'con estos filtros' : 'en total'} · un servicio cuenta en cada estado donde tenga un subproceso
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <span>Buscar</span>
          <input type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)} aria-label="Buscar por evaluado, cédula, cliente o número" placeholder="Evaluado, cédula, cliente o N.º"
            className={`min-h-[44px] w-72 max-w-full rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-900 ${FOCO}`} />
        </label>
      </div>

      {error && (
        <div role="alert" className="mb-4 flex items-center justify-between rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <span>{error} <button type="button" onClick={() => cargar(pagina)} className={`font-semibold underline ${FOCO}`}>Reintentar</button></span>
          <button type="button" aria-label="Cerrar aviso" onClick={() => setError(null)} className={`ml-4 text-red-500 hover:text-red-700 ${FOCO}`}>×</button>
        </div>
      )}

      <SemaforoEstados seleccionado={estadoParam === 'TODOS' ? '' : estadoParam.includes(',') ? '' : estadoParam} onSeleccionar={(e) => actualizar({ estado: e })}
        filtros={{ grupoTiempo: tiempoParam || undefined, ...filtrosAlcance }} titulo="Estado de las solicitudes" />

      {/* ── Filtros ── */}
      <section aria-label="Filtros" className="mb-4 space-y-3 rounded-2xl border border-gray-200 bg-white p-4">
        <div className="flex flex-wrap items-center gap-2">
          <span className="w-16 text-sm text-gray-700">Tiempo</span>
          {GRUPOS_TIEMPO.map(([k, nombre]) => (
            <button key={k} type="button" aria-pressed={tiempoParam === k} onClick={() => actualizar({ tiempo: tiempoParam === k ? '' : k })}
              className={`min-h-[44px] rounded-full border px-4 text-sm ${FOCO} ${tiempoParam === k ? 'border-slate-900 bg-slate-900 font-semibold text-white' : 'border-gray-300 bg-white text-gray-900 hover:bg-gray-50'}`}>
              {nombre} <span className="tabular-nums">{tiempos ? nfN.format(tiempos[k] ?? 0) : '…'}</span>
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <span className="w-16 text-sm text-gray-700">Filtrar</span>
          <label className="flex items-center gap-2 text-sm text-gray-700">Cliente
            <select value={clienteParam} onChange={e => actualizar({ cliente: e.target.value })} className={selectCls}>
              <option value="">Todos</option>{opciones.clientes.map(o => <option key={o.id} value={o.id}>{o.nombre}</option>)}
            </select></label>
          <label className="flex items-center gap-2 text-sm text-gray-700">Estudio
            <select value={estudioParam} onChange={e => actualizar({ estudio: e.target.value })} className={selectCls}>
              <option value="">Todos</option>{opciones.estudios.map(o => <option key={o.id} value={o.id}>{o.nombre}</option>)}
            </select></label>
          <label className="flex items-center gap-2 text-sm text-gray-700">Responsable
            <select value={responsableParam} onChange={e => actualizar({ responsable: e.target.value })} className={selectCls}>
              <option value="">Todos</option>{opciones.responsables.map(o => <option key={o.id} value={o.id}>{o.nombre} · {o.detalle}</option>)}
            </select></label>
          {hayFiltros && <button type="button" onClick={limpiar} className={`min-h-[44px] rounded-lg px-3 text-sm font-semibold text-primary-700 underline underline-offset-2 ${FOCO}`}>Quitar filtros</button>}
        </div>
      </section>

      {/* ── Tabla ── */}
      <div className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-gray-900/5">
        {cargando ? (
          <div className="flex justify-center py-16" role="status" aria-label="Cargando solicitudes">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : solicitudes.length === 0 ? (
          <div className="py-16 text-center text-sm text-gray-700">
            {qParam ? `No hay solicitudes que coincidan con «${qParam}».` : 'No hay solicitudes con estos filtros.'}
            {hayFiltros && <> <button type="button" onClick={limpiar} className={`font-semibold text-primary-700 underline ${FOCO}`}>Quitar filtros</button></>}
          </div>
        ) : (
          /* PB-H-14: la tabla se desplaza DENTRO de su caja. `relative` importa: los textos `sr-only` de las filas son absolutos y, sin un ancestro
              posicionado, se quedan en su lugar dentro de la tabla (más allá del borde derecho) y ensanchan la PÁGINA entera (1.106 px en una ventana de 804). */
          <div className="relative overflow-x-auto">
            <table className="table-stack min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50/80">
                <tr>
                  {['N.º', 'Evaluado · cargo', 'Cliente', 'Estudio', 'Avance', 'Riesgo', 'Tiempo', 'Próxima acción'].map(h => (
                    <th key={h} scope="col" className="whitespace-nowrap px-3 py-3 text-left text-[11px] font-semibold uppercase tracking-wider text-gray-700">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {solicitudes.map(s => (
                  <Fragment key={s.idServicio}>
                  <tr className="align-top transition-colors hover:bg-gray-50/70">
                    <td className="whitespace-nowrap py-3 pl-3 pr-1 text-sm font-semibold text-gray-700">
                      <button type="button" onClick={() => toggleExpandir(s.idServicio)} aria-expanded={abiertos.has(s.idServicio)} aria-label={`Subprocesos del estudio ${s.idServicio}`}
                        className={`flex min-h-[44px] items-center gap-1.5 rounded hover:text-primary-700 ${FOCO}`}>
                        <svg aria-hidden="true" className={`h-3.5 w-3.5 flex-shrink-0 transition-transform ${abiertos.has(s.idServicio) ? 'rotate-90' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                        </svg>
                        #{s.idServicio}
                      </button>
                    </td>
                    <td className="px-3 py-3">
                      <p className="text-sm font-semibold text-gray-900">{s.nombresEvaluado} {s.apellidosEvaluado}</p>
                      <p className="text-xs text-gray-700">{s.cargo ?? '—'}</p>
                      <p className="text-xs text-gray-700">{s.cedulaEvaluado}</p>
                    </td>
                    <td className="px-3 py-3 text-sm text-gray-900">{s.cliente ?? '—'}</td>
                    <td className="px-3 py-3 text-sm text-gray-900">{s.proceso}</td>
                    <td className="px-3 py-3">
                      <ul className="flex flex-wrap gap-1" aria-label="Subprocesos por estado">
                        {(s.resumen ?? []).map(r => (
                          <li key={r.codigo} className={estadoParam.split(',').includes(r.codigo) ? 'rounded-full ring-2 ring-offset-1 ring-gray-900' : ''}>
                            <EtiquetaEstado estado={r.codigo} pequena /><span className="sr-only">{r.cantidad}</span><span aria-hidden="true" className="ml-0.5 text-[11px] font-semibold text-gray-700">{r.cantidad}</span>
                          </li>
                        ))}
                      </ul>
                      {s.vigentes > 0 && (
                        <p className="mt-1 text-xs text-gray-700">{s.publicados} de {s.vigentes} publicados · pasta: {PASTA_TEXTO[s.pastaEstado] ?? 'pendiente'}
                          {s.pastaEstado !== 'PENDIENTE' && s.estudioPdfEstado === 'LISTO' && ` · PDF v${s.estudioPdfVersion} listo`}
                          {s.pastaEstado !== 'PENDIENTE' && s.estudioPdfEstado === 'GENERANDO' && ` · generando el PDF v${s.estudioPdfVersion}…`}</p>
                      )}
                      {s.pastaEstado !== 'PENDIENTE' && s.estudioPdfEstado === 'ERROR' && (
                        <p role="alert" className="mt-1 text-xs font-semibold text-red-800">No se pudo generar el PDF del estudio ·{' '}
                          <button type="button" onClick={() => reintentarPdf(s)} disabled={reintentandoPdf === s.idServicio}
                            className={`min-h-[44px] rounded px-1 font-semibold underline disabled:opacity-60 ${FOCO}`}>
                            {reintentandoPdf === s.idServicio ? 'Reintentando…' : 'Reintentar'}<span className="sr-only"> el PDF del estudio {s.idServicio}</span></button></p>
                      )}
                      {s.candidatoCompletado === false && s.estado !== 'CANCELADO' && (
                        <p className="text-xs text-gray-700">{s.enlaceEnviado ? 'Enlace enviado, sin terminar' : 'Enlace sin enviar'}</p>
                      )}
                    </td>
                    <td className="px-3 py-3"><ChipRiesgo nivel={s.riesgoNivel} puntaje={s.riesgoPuntaje} critico={s.riesgoCritico} /></td>
                    <td className="px-3 py-3 text-sm"><EtiquetaEntrega tiempo={s.tiempo} /></td>
                    <td className="px-3 py-3">
                      <div className="flex items-center gap-1.5">
                        <button type="button" onClick={() => ejecutarAccion(s)} disabled={avisandoId === s.idServicio}
                          className={`min-h-[44px] min-w-[8.5rem] rounded-lg px-3 text-sm font-semibold disabled:opacity-60 ${FOCO} ${ACCION_RELLENA.has(s.proximaAccion)
                            ? 'bg-primary-600 text-white hover:bg-primary-700' : 'border border-primary-600 bg-white text-primary-700 hover:bg-primary-50'}`}>
                          {avisandoId === s.idServicio ? 'Avisando…' : s.proximaAccionEtiqueta}<span className="sr-only"> del estudio {s.idServicio}</span>
                        </button>
                        <MenuAcciones etiqueta={`Más acciones del estudio ${s.idServicio}`} opciones={[
                        { label: 'Ver', icono: <IconoOjo />, onClick: () => setDetalle(s) },
                        (() => {
                          const { hayAlgunos, todosAsignados } = subprocesosProgramadorDe(s.subprocesos)
                          if (!hayAlgunos) return null
                          const cargando = cargandoPendientesId === s.idServicio
                          // candidatoCompletado === false: el candidato todavía no diligenció su
                          // link — el backend igual lo bloquearía (409), pero se deshabilita acá
                          // para que el gestor no intente y reciba el motivo de una vez.
                          const bloqueadoPorCandidato = s.candidatoCompletado === false
                          return {
                            label: cargando ? 'Cargando...' : (todosAsignados ? 'Programador ya asignado — reasignar' : 'Asignar programador'),
                            icono: todosAsignados ? <IconoAsignado /> : <IconoEnviar />,
                            destacado: todosAsignados ? 'text-green-600' : 'text-cyan-600',
                            disabled: cargando || bloqueadoPorCandidato,
                            title: bloqueadoPorCandidato ? 'El candidato todavía no ha diligenciado su información (link pendiente).' : undefined,
                            onClick: () => abrirAsignarProgramador(s.idServicio),
                          }
                        })(),
                        (TRANSICIONES_GESTOR[s.estado]?.length ?? 0) > 0 && {
                          label: s.estado === 'CANCELADO' ? 'Solicitar reversión' : s.estado === 'FINALIZADO' ? 'Publicar estudio' : 'Cancelar solicitud',
                          icono: <IconoCambioEstado />,
                          destacado: s.estado === 'CANCELADO' ? 'text-amber-600' : 'text-primary-600',
                          onClick: () => (s.estado === 'FINALIZADO' ? setEmitiendo(s) : setCambiandoEstado(s)),
                        },
                        // PB-04: mientras el candidato no haya enviado su información, el gestor puede enviar o reenviar el enlace
                        (s.candidatoCompletado === false || s.candidatoCompletado == null) && !['CANCELADO', 'FINALIZADO', 'PUBLICADO'].includes(s.estado) && {
                          label: s.enlaceEnviado ? 'Reenviar enlace' : 'Enviar enlace',
                          icono: <IconoEnviar />,
                          destacado: 'text-primary-600',
                          onClick: () => setEnviandoEnlace(s),
                        },
                        s.estado === 'PUBLICADO' && {
                          label: 'Reemitir estudio (PDF)',
                          icono: <IconoCambioEstado />,
                          destacado: 'text-primary-600',
                          onClick: () => setEmitiendo(s),
                        },
                        ['PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION', 'FINALIZADO', 'REPROGRAMADO'].includes(s.estado) && {
                          label: 'El candidato desistió',
                          icono: <IconoCambioEstado />,
                          destacado: 'text-amber-600',
                          onClick: () => setCambiandoEstado({ ...s, modo: 'desistimiento' }),
                        },
                      ]} />
                      </div>
                    </td>
                  </tr>
                  {abiertos.has(s.idServicio) && (
                    <FilaSubprocesos idServicio={s.idServicio} colSpan={8} candidatoCompletado={s.candidatoCompletado}
                      estadoServicio={s.estado} onEmitir={() => setEmitiendo(s)}
                      onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo: tipo === 'error' ? 'error' : 'exito' })} />
                  )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!cargando && solicitudes.length > 0 && (
          <Paginacion
            pagina={pagina + 1}
            total={totalElementos}
            porPagina={POR_PAGINA}
            onChange={(p) => { setPagina(p - 1); cargar(p - 1) }}
          />
        )}
      </div>

      {enviandoEnlace && (
        <PanelEnviarEnlace solicitud={enviandoEnlace} onClose={() => setEnviandoEnlace(null)}
          onHecho={() => cargar(pagina)}
          onToast={(mensaje) => setToast({ mensaje, tipo: 'exito' })} />
      )}

      {emitiendo && (
        <PanelEmitirEstudio solicitud={{ ...emitiendo, nombres: emitiendo.nombresEvaluado, apellidos: emitiendo.apellidosEvaluado }}
          onClose={() => setEmitiendo(null)}
          onHecho={() => { setEmitiendo(null); cargar(pagina) }}
          onToast={(mensaje) => setToast({ mensaje, tipo: 'exito' })} />
      )}

      {cambiandoEstado && (
        <ModalCambioEstado
          solicitud={cambiandoEstado}
          modo={cambiandoEstado.modo}
          onClose={() => setCambiandoEstado(null)}
          onGuardado={msg => { setToast({ mensaje: msg, tipo: 'exito' }); cargar(pagina) }}
        />
      )}

      {detalle && (
        <ModalDetalle
          solicitud={detalle}
          onClose={() => setDetalle(null)}
          onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo: tipo === 'error' ? 'error' : 'exito' })}
        />
      )}

      {asignandoProgramadorPara && (
        <ModalAsignarProgramador
          subprocesos={asignandoProgramadorPara}
          onClose={() => setAsignandoProgramadorPara(null)}
          onGuardado={() => { setAsignandoProgramadorPara(null); cargar(pagina) }}
          onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo: tipo === 'error' ? 'error' : 'exito' })}
        />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

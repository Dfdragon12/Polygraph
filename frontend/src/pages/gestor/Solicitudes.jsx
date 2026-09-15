import { useState, useEffect, useCallback, Fragment } from 'react'
import { useSearchParams } from 'react-router-dom'
import api from '../../services/api'
import tokenService from '../../services/tokenService'
import catalogoService from '../../services/catalogoService'
import asignacionService from '../../services/asignacionService'
import Toast from '../../components/Toast'
import { Modal, ConfirmModal } from '../../components/ui/Modal'
import Paginacion from '../../components/ui/Paginacion'
import ModalAsignarSubproceso from '../../components/ModalAsignarSubproceso'
import ModalAsignarProgramador from '../../components/ModalAsignarProgramador'
import CiudadSelect from '../../components/CiudadSelect'

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

// Tarjetas de resumen por estado (reemplazan los tabs de texto) — cada una filtra al hacer clic
// y se vuelve a pulsar para volver a "Todos". El color de cada campo debe ir literal (no
// interpolado) para que Tailwind lo detecte en el build.
const CAMPO_CONTEO = {
  PENDIENTE:    'pendientes',
  PROGRAMANDO:  'programando',
  EN_EJECUCION: 'enEjecucion',
  FINALIZADO:   'finalizados',
  PUBLICADO:    'publicados',
  CANCELADO:    'cancelados',
  REPROGRAMADO: 'reprogramados',
}

const TARJETAS_ESTADO = [
  { estado: 'PENDIENTE',    label: 'Pendientes',    desc: 'Requieren atención',     border: 'border-amber-200',   dot: 'bg-amber-400',   texto: 'text-amber-600',   bgActivo: 'bg-amber-50/70',   ring: 'ring-amber-300' },
  { estado: 'PROGRAMANDO',  label: 'Programando',   desc: 'En preparación',         border: 'border-blue-200',    dot: 'bg-blue-400',    texto: 'text-blue-600',    bgActivo: 'bg-blue-50/70',    ring: 'ring-blue-300' },
  { estado: 'EN_EJECUCION', label: 'En ejecución',  desc: 'Siendo trabajados',      border: 'border-primary-200', dot: 'bg-primary-400', texto: 'text-primary-600', bgActivo: 'bg-primary-50/70', ring: 'ring-primary-300' },
  { estado: 'FINALIZADO',   label: 'Finalizados',   desc: 'Listos para revisar',    border: 'border-green-200',   dot: 'bg-green-400',   texto: 'text-green-600',   bgActivo: 'bg-green-50/70',   ring: 'ring-green-300' },
  { estado: 'PUBLICADO',    label: 'Publicados',    desc: 'Entregados',             border: 'border-cyan-200',    dot: 'bg-cyan-400',    texto: 'text-cyan-600',    bgActivo: 'bg-cyan-50/70',    ring: 'ring-cyan-300' },
  { estado: 'CANCELADO',    label: 'Cancelados',    desc: 'No activos',             border: 'border-red-200',     dot: 'bg-red-400',     texto: 'text-red-500',     bgActivo: 'bg-red-50/70',     ring: 'ring-red-300' },
  { estado: 'REPROGRAMADO', label: 'Reprogramados', desc: 'Cambiaron de fecha',     border: 'border-orange-200',  dot: 'bg-orange-400',  texto: 'text-orange-600',  bgActivo: 'bg-orange-50/70',  ring: 'ring-orange-300' },
]

// Debe coincidir con PASOS en frontend/src/pages/evaluee/EvalueeForm.jsx
const PASOS_EVALUADO = ['Autorización', 'Datos personales', 'Educación', 'Experiencia laboral', 'Referencias', 'Documentos', 'Revisión']

const BADGE = {
  PENDIENTE:    'bg-amber-100 text-amber-700',
  PROGRAMANDO:  'bg-blue-100 text-blue-700',
  EN_EJECUCION: 'bg-primary-100 text-primary-700',
  FINALIZADO:   'bg-green-100 text-green-700',
  PUBLICADO:    'bg-cyan-100 text-cyan-700',
  CANCELADO:    'bg-red-100 text-red-600',
  REPROGRAMADO: 'bg-orange-100 text-orange-700',
}

const DOT_ESTADO = {
  PENDIENTE:    'bg-amber-400',
  PROGRAMANDO:  'bg-blue-400',
  EN_EJECUCION: 'bg-primary-400',
  FINALIZADO:   'bg-green-400',
  PUBLICADO:    'bg-cyan-400',
  CANCELADO:    'bg-red-400',
  REPROGRAMADO: 'bg-orange-400',
}

// Transiciones permitidas para GESTOR
const TRANSICIONES_GESTOR = {
  PENDIENTE:    ['PROGRAMANDO', 'CANCELADO'],
  PROGRAMANDO:  ['CANCELADO', 'REPROGRAMADO'],
  EN_EJECUCION: [],
  FINALIZADO:   ['PUBLICADO'],
  PUBLICADO:    [],
  CANCELADO:    ['PROGRAMANDO', 'REPROGRAMADO'],
  REPROGRAMADO: ['PROGRAMANDO', 'CANCELADO'],
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function formatFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
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

function diasHasta(fechaISO) {
  if (!fechaISO) return null
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0)
  const fecha = new Date(fechaISO); fecha.setHours(0, 0, 0, 0)
  return Math.round((fecha - hoy) / 86400000)
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

const BADGE_SUBPROCESO = {
  PENDIENTE:  'bg-amber-100 text-amber-700',
  ASIGNADO:   'bg-blue-100 text-blue-700',
  COMPLETADO: 'bg-green-100 text-green-700',
  CANCELADO:  'bg-red-100 text-red-600',
}

const DOT_SUBPROCESO = {
  PENDIENTE:  'bg-amber-400',
  ASIGNADO:   'bg-blue-400',
  COMPLETADO: 'bg-green-400',
  CANCELADO:  'bg-red-400',
}

const ORDEN_ESTADOS_SUBPROCESO = ['PENDIENTE', 'ASIGNADO', 'COMPLETADO', 'CANCELADO']

function BolitasSubprocesos({ subprocesos }) {
  if (!subprocesos?.length) return <span className="text-gray-300 text-xs">—</span>

  const conteo = subprocesos.reduce((acc, sp) => {
    acc[sp.estado] = (acc[sp.estado] ?? 0) + 1
    return acc
  }, {})

  return (
    <div className="flex items-center gap-1">
      {ORDEN_ESTADOS_SUBPROCESO.filter(e => conteo[e] > 0).map(e => (
        <span key={e} title={`${e}: ${conteo[e]}`}
          className={`flex items-center justify-center min-w-[20px] h-5 px-1 rounded-full text-white text-[10px] font-bold flex-shrink-0 ${DOT_SUBPROCESO[e]}`}>
          {conteo[e]}
        </span>
      ))}
    </div>
  )
}

/* ─── Fila desplegable: subprocesos reales del servicio (con tiempo estimado) ─── */
function FilaSubprocesos({ idServicio, colSpan, onToast }) {
  const [subprocesos, setSubprocesos] = useState(null)
  const [error, setError] = useState(false)
  const [seleccionado, setSeleccionado] = useState(null)
  const [asignandoProgramadorDe, setAsignandoProgramadorDe] = useState(null)

  const cargar = useCallback(() => {
    asignacionService.listar({ idServicio, size: 50 })
      .then(r => setSubprocesos(r.content ?? []))
      .catch(() => setError(true))
  }, [idServicio])

  useEffect(() => { cargar() }, [cargar])

  return (
    <Fragment>
    <tr className="bg-gray-50/60">
      <td colSpan={colSpan} className="px-4 py-3">
        {error ? (
          <p className="text-xs text-red-500">No se pudieron cargar los subprocesos.</p>
        ) : subprocesos === null ? (
          <div className="flex items-center gap-2 text-xs text-gray-400 py-2">
            <div className="animate-spin rounded-full h-4 w-4 border-2 border-primary-500 border-t-transparent" />
            Cargando subprocesos...
          </div>
        ) : subprocesos.length === 0 ? (
          <p className="text-xs text-gray-400">Este servicio no tiene subprocesos generados.</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2">
            {subprocesos.map(sp => {
              // Poligrafía/visita se muestran para que el gestor no vea "sin subprocesos" cuando
              // sí hay — pero solo lectura: eso lo asigna Programador, no se abre el modal.
              const soloLectura = sp.rolResponsable !== 'ANALISTA_INTERNO'
              if (soloLectura) {
                return (
                  <div key={sp.id} title="Lo coordina el Programador — solo lectura aquí"
                    className="flex items-center justify-between gap-2 bg-gray-50 border border-gray-100 rounded-lg px-3 py-2 opacity-80">
                    <div className="min-w-0">
                      <p className="text-xs font-medium text-gray-600 truncate">{sp.subproceso}</p>
                      <p className="text-[11px] text-gray-400 truncate">
                        {sp.nombreAsignado ?? 'Sin asignar'} · {formatDuracion(sp.duracionMinutos)} ·{' '}
                        <span className="italic">
                          {sp.nombreProgramadorResponsable ? `Programador: ${sp.nombreProgramadorResponsable}` : 'Programador'}
                        </span>
                      </p>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      {(sp.estado === 'PENDIENTE' || sp.estado === 'ASIGNADO') && (
                        <button type="button" onClick={() => setAsignandoProgramadorDe(sp)}
                          title="Elegir a qué programador encargarle esto"
                          className="text-[10px] font-medium text-primary-600 hover:text-primary-800 hover:underline whitespace-nowrap">
                          {sp.nombreProgramadorResponsable ? 'Reasignar programador' : 'Asignar programador'}
                        </button>
                      )}
                      <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${BADGE_SUBPROCESO[sp.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                        {sp.estado}
                      </span>
                    </div>
                  </div>
                )
              }
              return (
                <button key={sp.id} onClick={() => setSeleccionado(sp)}
                  className="flex items-center justify-between gap-2 bg-white border border-gray-200 rounded-lg px-3 py-2 text-left hover:border-primary-300 hover:shadow-sm transition-all">
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-gray-800 truncate">{sp.subproceso}</p>
                    <p className="text-[11px] text-gray-400 truncate">
                      {sp.nombreAsignado ?? 'Sin asignar'} · {formatDuracion(sp.duracionMinutos)}
                    </p>
                  </div>
                  <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full flex-shrink-0 ${BADGE_SUBPROCESO[sp.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                    {sp.estado}
                  </span>
                </button>
              )
            })}
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
    </Fragment>
  )
}

function EtiquetaEntrega({ fecha }) {
  const dias = diasHasta(fecha)
  if (dias === null) return <span className="text-gray-400">—</span>
  if (dias < 0) {
    return <span className="text-red-600 font-semibold">Se venció hace {Math.abs(dias)} día{Math.abs(dias) !== 1 ? 's' : ''}</span>
  }
  if (dias === 0) return <span className="text-amber-600 font-semibold">Vence hoy</span>
  return (
    <span className={dias <= 1 ? 'text-amber-600 font-medium' : 'text-gray-500'}>
      {dias} día{dias !== 1 ? 's' : ''} para entregar
    </span>
  )
}

/* ── Estados del link de validación del evaluado ── */
const ESTADO_LINK_CFG = {
  PENDIENTE: { color: 'bg-emerald-100 text-emerald-700', label: 'Pendiente' },
  USADO:     { color: 'bg-slate-100 text-slate-600',     label: 'Usado'     },
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

            {/* Datos personales */}
            <section>
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Datos personales</h3>
              <div className="bg-gray-50 rounded-lg p-4 grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
                {resumen.fechaNacimiento && <div><span className="text-gray-400">F. nacimiento: </span><span className="text-gray-800">{formatFecha(resumen.fechaNacimiento)}</span></div>}
                {resumen.ciudadNacimiento && <div><span className="text-gray-400">Ciudad nacimiento: </span><span className="text-gray-800">{resumen.ciudadNacimiento}</span></div>}
                {resumen.ciudadResidencia && <div><span className="text-gray-400">Ciudad residencia: </span><span className="text-gray-800">{resumen.ciudadResidencia}</span></div>}
                {resumen.estadoCivil && <div><span className="text-gray-400">Estado civil: </span><span className="text-gray-800">{resumen.estadoCivil}</span></div>}
                {resumen.rh && <div><span className="text-gray-400">RH: </span><span className="text-gray-800">{resumen.rh}</span></div>}
                {resumen.nivelEducativo && <div><span className="text-gray-400">Nivel educativo: </span><span className="text-gray-800">{resumen.nivelEducativo}</span></div>}
                {resumen.estrato && <div><span className="text-gray-400">Estrato: </span><span className="text-gray-800">{resumen.estrato}</span></div>}
                {resumen.eps && <div><span className="text-gray-400">EPS: </span><span className="text-gray-800">{resumen.eps}</span></div>}
                {resumen.fondoPensiones && <div><span className="text-gray-400">Pensiones: </span><span className="text-gray-800">{resumen.fondoPensiones}</span></div>}
                {resumen.celular && <div><span className="text-gray-400">Celular: </span><span className="text-gray-800">{resumen.celular}</span></div>}
                {resumen.telefonoFijo && <div><span className="text-gray-400">Tel. fijo: </span><span className="text-gray-800">{resumen.telefonoFijo}</span></div>}
                {resumen.email && <div><span className="text-gray-400">Email: </span><span className="text-gray-800">{resumen.email}</span></div>}
                {resumen.libretaMilitar && <div><span className="text-gray-400">Libreta militar: </span><span className="text-gray-800">{resumen.libretaMilitar}</span></div>}
                {resumen.visa && <div><span className="text-gray-400">Visa: </span><span className="text-gray-800">{resumen.visa}</span></div>}
                {resumen.pasaporte && <div><span className="text-gray-400">Pasaporte: </span><span className="text-gray-800">{resumen.pasaporte}</span></div>}
                {resumen.direccion && (
                  <div className="col-span-2">
                    <span className="text-gray-400">Dirección: </span>
                    <span className="text-gray-800">{resumen.direccion}{resumen.barrio ? `, ${resumen.barrio}` : ''}</span>
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
                          <td data-label="Estado" className="px-3 py-2 text-gray-600">{e.enCurso ? 'En curso' : (e.fechaFin ? formatFecha(e.fechaFin) : '—')}</td>
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
                        <th className="px-3 py-2 text-left">Motivo retiro</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {resumen.experienciaLaboral.map((ex, i) => (
                        <tr key={i}>
                          <td data-label="Empresa" className="px-3 py-2 text-gray-800">{ex.empresa ?? '—'}</td>
                          <td data-label="Cargo" className="px-3 py-2 text-gray-800">{ex.cargo ?? '—'}</td>
                          <td data-label="Inicio" className="px-3 py-2 text-gray-600 whitespace-nowrap">{formatFecha(ex.fechaInicio)}</td>
                          <td data-label="Fin" className="px-3 py-2 text-gray-600 whitespace-nowrap">{ex.laboraActualmente ? 'Actual' : formatFecha(ex.fechaFin)}</td>
                          <td data-label="Motivo retiro" className="px-3 py-2 text-gray-600 max-w-[140px]"><span className="truncate block">{ex.motivoRetiro ?? '—'}</span></td>
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
                          Tiempo muerto de {g.diasInactivo} días ({formatFecha(g.fechaInicio)} → {formatFecha(g.fechaFin)})
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
                          {doc.tipoDocumento}{doc.fechaCarga ? ` · ${formatFechaHora(doc.fechaCarga)}` : ''}
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
function ModalCambioEstado({ solicitud, onClose, onGuardado }) {
  const esReversion = solicitud.estado === 'CANCELADO'
  const siguientes = TRANSICIONES_GESTOR[solicitud.estado] ?? []
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
    setGuardando(true); setError(null)
    try {
      if (esReversion) {
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
      titulo={esReversion ? 'Solicitar reversión de estado' : 'Cambiar estado'}
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
            Estado actual: <span className={`font-medium px-2 py-0.5 rounded-full text-xs ${BADGE[solicitud.estado]}`}>{solicitud.estado}</span>
          </p>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            {esReversion ? 'Estado deseado' : 'Nuevo estado'} <span className="text-red-500">*</span>
          </label>
          <select value={nuevoEstado} onChange={e => setNuevoEstado(e.target.value)} required
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            {siguientes.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            {esReversion ? 'Motivo de la solicitud' : 'Observación'} {esReversion && <span className="text-red-500">*</span>}
          </label>
          <textarea value={observacion} onChange={e => setObservacion(e.target.value)} rows={3}
            required={esReversion}
            placeholder={esReversion ? 'Explica por qué debe revertirse esta cancelación…' : (nuevoEstado === 'PUBLICADO' ? 'Concepto final del estudio…' : 'Motivo o notas adicionales…')}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
        </div>

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Enviando...' : (esReversion ? 'Enviar solicitud' : 'Confirmar cambio')}
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

  const [historial, setHistorial]             = useState([])
  const [cargandoHistorial, setCargandoHistorial] = useState(true)
  const [eliminandoId, setEliminandoId]       = useState(null)
  const [confirmarEliminar, setConfirmarEliminar] = useState(null)

  const cargar = useCallback(() => {
    setCargando(true)
    return tokenService.porServicio(idServicio)
      .then(setLink)
      .catch(() => setLink(null))
      .finally(() => setCargando(false))
  }, [idServicio])

  const cargarHistorial = useCallback(() => {
    setCargandoHistorial(true)
    return tokenService.historialPorServicio(idServicio)
      .then(setHistorial)
      .catch(() => setHistorial([]))
      .finally(() => setCargandoHistorial(false))
  }, [idServicio])

  useEffect(() => { cargar(); cargarHistorial() }, [cargar, cargarHistorial])

  const eliminar = async (idLink) => {
    setEliminandoId(idLink)
    try {
      await tokenService.eliminar(idLink)
      onToast('Link eliminado correctamente')
      await cargarHistorial()
    } catch (e) {
      onToast(e.response?.data?.mensaje ?? 'Error al eliminar el link.', 'error')
    } finally { setEliminandoId(null); setConfirmarEliminar(null) }
  }

  const generar = async () => {
    setProcesando(true)
    try {
      const nuevo = await tokenService.generar(idServicio)
      setLink(nuevo)
      onToast('Link generado correctamente')
      await cargarHistorial()
    } catch (e) {
      const msg = e.response?.data?.mensaje
      onToast(msg ?? 'Error al generar el link.', 'error')
    } finally { setProcesando(false) }
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
      await cargarHistorial()
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
      await cargarHistorial()
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
  const esReenviable = link?.estado === 'EXPIRADO' || link?.estado === 'BLOQUEADO'

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
            {esPendiente && <>Expira en <span className="font-medium text-emerald-600">{tiempoRestante(link.fechaExpiracion)}</span></>}
            {esUsado && <>Usado el {formatFechaHora(link.fechaUso)}</>}
            {!esPendiente && !esUsado && <>Expiró el {formatFechaHora(link.fechaExpiracion)}</>}
          </p>

          <div className="bg-white border border-gray-100 rounded-lg px-3 py-2.5 space-y-2">
            <p className="text-xs">
              {link.fechaPrimerIngreso ? (
                <span className="text-gray-600">
                  El candidato ingresó al link el <span className="font-medium text-gray-800">{formatFechaHora(link.fechaPrimerIngreso)}</span>
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
              Generado el {formatFechaHora(link.fechaCreacion)}{link.generadoPorNombre ? ` por ${link.generadoPorNombre}` : ''}
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
            {esPendiente && (
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
            {esReenviable && (
              <button onClick={generar} disabled={procesando}
                className="text-xs font-medium px-2.5 py-1 rounded-lg border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors">
                {procesando ? 'Generando...' : 'Generar nuevo link'}
              </button>
            )}
          </div>
        </div>
      )}

      {/* Historial de links del servicio — permite ver y eliminar los que ya no sirven */}
      <div className="mt-4">
        <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">
          Historial de links {historial.length > 0 && `(${historial.length})`}
        </p>
        {cargandoHistorial ? (
          <div className="flex justify-center py-4">
            <div className="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent" />
          </div>
        ) : historial.length === 0 ? (
          <p className="text-sm text-gray-400">Sin links generados para este servicio.</p>
        ) : (
          <div className="divide-y divide-gray-100 border border-gray-200 rounded-lg overflow-hidden">
            {historial.map(h => {
              const hcfg = ESTADO_LINK_CFG[h.estado] ?? ESTADO_LINK_CFG.EXPIRADO
              const esActual = link && h.id === link.id
              return (
                <div key={h.id} className="flex items-center justify-between gap-3 px-3 py-2.5 bg-white">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${hcfg.color}`}>{hcfg.label}</span>
                      {esActual && (
                        <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-primary-50 text-primary-600">Vigente</span>
                      )}
                      <span className="text-xs text-gray-400 font-mono">{h.tokenMascarado}</span>
                    </div>
                    <p className="text-xs text-gray-400 mt-1">
                      Creado {formatFechaHora(h.fechaCreacion)}{h.generadoPorNombre ? ` · ${h.generadoPorNombre}` : ''}
                    </p>
                  </div>
                  {h.estado === 'PENDIENTE' ? (
                    <span className="text-xs text-gray-300 flex-shrink-0" title="Revócalo antes de poder eliminarlo">—</span>
                  ) : (
                    <button onClick={() => setConfirmarEliminar(h)} disabled={eliminandoId === h.id}
                      className="flex-shrink-0 text-xs font-medium px-2.5 py-1 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 transition-colors disabled:opacity-50">
                      {eliminandoId === h.id ? 'Eliminando...' : 'Eliminar'}
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {confirmarEliminar && (
        <ConfirmModal
          titulo="Eliminar link"
          danger
          confirmLabel="Eliminar"
          cargando={eliminandoId === confirmarEliminar.id}
          onConfirmar={() => eliminar(confirmarEliminar.id)}
          onCancelar={() => setConfirmarEliminar(null)}>
          <p className="text-sm text-gray-600">Este link se eliminará permanentemente. Esta acción no se puede deshacer.</p>
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
            Ingresó el <strong>{formatFechaHora(link.fechaPrimerIngreso)}</strong>
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
          className={`flex-shrink-0 p-1.5 rounded-md transition-all ${
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
            className="flex-shrink-0 p-1.5 rounded-md opacity-0 group-hover:opacity-100 text-gray-400 hover:text-primary-600 hover:bg-primary-100 transition-all">
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
                  {m.ciudad}{m.motivo ? ` — ${m.motivo}` : ''} · {new Date(m.fechaGenerado).toLocaleDateString('es-CO')}
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
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
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
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${BADGE[detalle?.estado] ?? 'bg-gray-100 text-gray-600'}`}>{detalle?.estado}</span>
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
                          <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${BADGE[h.estadoNuevo] ?? 'bg-gray-100 text-gray-600'}`}>
                            {h.estadoNuevo}
                          </span>
                          <span className="text-xs text-gray-400 whitespace-nowrap">{formatFechaHora(h.fechaCambio)}</span>
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
          <button onClick={onClose} className="w-full py-2 text-sm text-gray-600 border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors">Cerrar</button>
        </div>
      </>
    </Modal>
  )
}

/* ─── Página principal ─── */
export default function Solicitudes() {
  const [searchParams, setSearchParams] = useSearchParams()
  const estadoParam = searchParams.get('estado') ?? 'TODOS'

  const [solicitudes, setSolicitudes]     = useState([])
  const [cargando, setCargando]           = useState(true)
  const [error, setError]                 = useState(null)
  const [toast, setToast]                 = useState(null)
  const [pagina, setPagina]               = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)
  const POR_PAGINA = 10
  const [busqueda, setBusqueda]           = useState('')
  const [cambiandoEstado, setCambiandoEstado] = useState(null)
  const [detalle, setDetalle]             = useState(null)
  const [expandidos, setExpandidos]       = useState(() => new Set())
  const [conteos, setConteos]             = useState(null)
  const [cargandoPendientesId, setCargandoPendientesId] = useState(null)
  const [asignandoProgramadorPara, setAsignandoProgramadorPara] = useState(null)

  const toggleExpandir = (idServicio) => {
    setExpandidos(prev => {
      const next = new Set(prev)
      if (next.has(idServicio)) next.delete(idServicio)
      else next.add(idServicio)
      return next
    })
  }

  const cargar = useCallback(async (estado, page = 0) => {
    setCargando(true); setError(null)
    try {
      const params = new URLSearchParams({ page, size: POR_PAGINA })
      if (estado && estado !== 'TODOS') params.append('estado', estado)
      const [rSolicitudes, rConteo] = await Promise.all([
        api.get(`/gestor/solicitudes?${params}`),
        api.get('/gestor/solicitudes/conteo'),
      ])
      setSolicitudes(rSolicitudes.data.content ?? [])
      setTotalElementos(rSolicitudes.data.totalElements ?? 0)
      setConteos(rConteo.data)
    } catch { setError('Error al cargar solicitudes.') }
    finally { setCargando(false) }
  }, [])

  useEffect(() => {
    setPagina(0)
    cargar(estadoParam, 0)
  }, [estadoParam, cargar])

  useEffect(() => {
    const idParam = searchParams.get('id')
    if (!idParam) return
    setDetalle({ idServicio: Number(idParam) })
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      next.delete('id')
      return next
    }, { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const cambiarTab = (estado) => {
    const params = new URLSearchParams()
    if (estado !== 'TODOS') params.set('estado', estado)
    setSearchParams(params)
  }

  // Click en la tarjeta activa vuelve a "Todos" — no hay una tarjeta "Todos" aparte.
  const alternarTarjeta = (estado) => cambiarTab(estadoParam === estado ? 'TODOS' : estado)

  // Trae los subprocesos de poligrafía/visita del servicio (Pendientes o ya Programados — a estos
  // últimos también se les puede cambiar quién los coordina) y abre el selector de programador
  // para encargárselos/reasignárselos todos de una vez.
  const abrirAsignarProgramador = async (idServicio) => {
    setCargandoPendientesId(idServicio)
    try {
      const r = await asignacionService.listar({ idServicio, size: 50 })
      const elegibles = (r.content ?? []).filter(sp =>
        sp.rolResponsable !== 'ANALISTA_INTERNO' && (sp.estado === 'PENDIENTE' || sp.estado === 'ASIGNADO'))
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

  const solicitudesFiltradas = busqueda.trim()
    ? solicitudes.filter(s =>
        `${s.nombresEvaluado} ${s.apellidosEvaluado}`.toLowerCase().includes(busqueda.toLowerCase()) ||
        s.cedulaEvaluado?.includes(busqueda) ||
        s.cargo?.toLowerCase().includes(busqueda.toLowerCase())
      )
    : solicitudes

  return (
    <div>
      <div className="flex items-center justify-between mb-5">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Solicitudes</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {totalElementos} solicitud{totalElementos !== 1 ? 'es' : ''} en total
            {estadoParam !== 'TODOS' && (
              <>
                {' · filtrando por '}
                <span className="font-medium text-gray-600">{estadoParam.replace('_', ' ')}</span>
                {' — '}
                <button onClick={() => cambiarTab('TODOS')} className="text-primary-600 hover:underline">ver todas</button>
              </>
            )}
          </p>
        </div>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error} <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      {/* ── Tarjetas de resumen por estado ── */}
      <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-7 gap-4 mb-4">
        {TARJETAS_ESTADO.map(t => {
          const activo = estadoParam === t.estado
          return (
            <button key={t.estado} type="button" onClick={() => alternarTarjeta(t.estado)}
              className={`text-left bg-white rounded-xl border shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow ${t.border} ${
                activo ? `${t.bgActivo} ring-2 ${t.ring}` : ''
              }`}>
              <div className="flex items-center gap-2 mb-2">
                <span className={`w-2 h-2 rounded-full ${t.dot}`} />
                <span className="text-xs font-medium text-gray-500">{t.label}</span>
              </div>
              <p className={`text-3xl font-bold ${t.texto}`}>{conteos ? conteos[CAMPO_CONTEO[t.estado]] : '—'}</p>
              <p className="text-xs text-gray-400 mt-1">{t.desc}</p>
            </button>
          )
        })}
      </div>

      {/* ── Buscador ── */}
      <div className="mb-4">
        <input type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, cédula o cargo…"
          className="w-full max-w-sm border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>

      {/* ── Tabla ── */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : solicitudesFiltradas.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">No hay solicitudes para este filtro.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  {['#', 'Evaluado', 'Cargo', 'Proceso', 'Estado', 'Solicitud', 'Entrega', 'Subprocesos', 'Acciones'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {solicitudesFiltradas.map(s => (
                  <Fragment key={s.idServicio}>
                  <tr className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-xs text-gray-400">
                      <button onClick={() => toggleExpandir(s.idServicio)}
                        className="flex items-center gap-1.5 hover:text-primary-600 transition-colors">
                        <svg className={`h-3.5 w-3.5 flex-shrink-0 transition-transform ${expandidos.has(s.idServicio) ? 'rotate-90' : ''}`}
                          fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                        </svg>
                        #{s.idServicio}
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <p className="text-sm font-medium text-gray-900 whitespace-nowrap">{s.nombresEvaluado} {s.apellidosEvaluado}</p>
                      <p className="text-xs text-gray-400">{s.cedulaEvaluado}</p>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 whitespace-nowrap">{s.cargo ?? '—'}</td>
                    <td className="px-4 py-3">
                      <span className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded whitespace-nowrap">{s.proceso}</span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${BADGE[s.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                        {s.estado}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{formatFecha(s.fechaSolicitud)}</td>
                    <td className="px-4 py-3 text-xs whitespace-nowrap"><EtiquetaEntrega fecha={s.fechaEntregaEstimada} /></td>
                    <td className="px-4 py-3"><BolitasSubprocesos subprocesos={s.subprocesos} /></td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button onClick={() => setDetalle(s)} title="Ver"
                          className="p-1.5 rounded-lg text-slate-600 hover:bg-slate-50 hover:text-slate-800 transition-colors">
                          <IconoOjo />
                        </button>
                        {(() => {
                          const { hayAlgunos, todosAsignados } = subprocesosProgramadorDe(s.subprocesos)
                          if (!hayAlgunos) return null
                          return (
                            <button onClick={() => abrirAsignarProgramador(s.idServicio)}
                              disabled={cargandoPendientesId === s.idServicio}
                              title={todosAsignados ? 'Programador ya asignado — reasignar' : 'Asignar programador'}
                              className={`p-1.5 rounded-lg transition-colors disabled:opacity-40 ${
                                todosAsignados
                                  ? 'text-green-600 hover:bg-green-50 hover:text-green-800'
                                  : 'text-cyan-600 hover:bg-cyan-50 hover:text-cyan-800'
                              }`}>
                              {cargandoPendientesId === s.idServicio
                                ? <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
                                : (todosAsignados ? <IconoAsignado /> : <IconoEnviar />)}
                            </button>
                          )
                        })()}
                        {(TRANSICIONES_GESTOR[s.estado]?.length ?? 0) > 0 && (
                          <button onClick={() => setCambiandoEstado(s)}
                            title={s.estado === 'CANCELADO' ? 'Solicitar reversión' : 'Cambiar estado'}
                            className={`p-1.5 rounded-lg transition-colors ${
                              s.estado === 'CANCELADO'
                                ? 'text-amber-600 hover:bg-amber-50 hover:text-amber-800'
                                : 'text-primary-600 hover:bg-primary-50 hover:text-primary-800'
                            }`}>
                            <IconoCambioEstado />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  {expandidos.has(s.idServicio) && (
                    <FilaSubprocesos idServicio={s.idServicio} colSpan={9}
                      onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo: tipo === 'error' ? 'error' : 'exito' })} />
                  )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!cargando && solicitudesFiltradas.length > 0 && (
          <Paginacion
            pagina={pagina + 1}
            total={totalElementos}
            porPagina={POR_PAGINA}
            onChange={(p) => { setPagina(p - 1); cargar(estadoParam, p - 1) }}
          />
        )}
      </div>

      {cambiandoEstado && (
        <ModalCambioEstado
          solicitud={cambiandoEstado}
          onClose={() => setCambiandoEstado(null)}
          onGuardado={msg => { setToast({ mensaje: msg, tipo: 'exito' }); cargar(estadoParam, pagina) }}
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
          onGuardado={() => { setAsignandoProgramadorPara(null); cargar(estadoParam, pagina) }}
          onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo: tipo === 'error' ? 'error' : 'exito' })}
        />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

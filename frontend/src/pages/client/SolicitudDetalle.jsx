import { useId, useState, useEffect, useCallback, useRef } from 'react'
import EstadoTiempo from '../../components/EstadoTiempo'
import { useParams, useNavigate, Link } from 'react-router-dom'
import solicitudService from '../../services/solicitudService'
import { useCambios } from '../../hooks/useCambios'
import { compromisoPorDefinir } from '../../components/CompromisoFecha'
import EtiquetaEstado from '../../components/semaforo/EtiquetaEstado'
import { useAuth } from '../../hooks/useAuth'
import { colorNovedad } from '../../utils/paletaNovedades'
import mensajeService from '../../services/mensajeService'
import trabajoService, { esperarTrabajo, guardarBlob, mensajeDeError } from '../../services/trabajoService'

// Texto del botón mientras el PDF está en la cola del backend.
function textoEstadoDescarga(trabajo) {
  if (trabajo?.estado === 'EN_COLA' && trabajo.posicionEnCola > 1) return `En fila (${trabajo.posicionEnCola})…`
  if (trabajo?.estado === 'COMPLETADO') return 'Descargando…'
  return 'Generando…'
}
import { Modal } from '../../components/ui/Modal'

// Mismo orden que PASOS en pages/evaluee/EvalueeForm.jsx (pasoActual es el índice).
const PASOS_CANDIDATO = ['Autorización', 'Datos personales', 'Educación', 'Experiencia laboral', 'Referencias', 'Documentos', 'Revisión']

const fechaLarga = (valor) => valor
  ? new Date(`${String(valor).slice(0, 10)}T00:00:00`).toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' })
  : '—'
const fechaCorta = (valor) => valor
  ? new Date(`${String(valor).slice(0, 10)}T12:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: '2-digit' })
  : '—'
const fechaHora = (iso) => iso
  ? new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  : '—'

// Etapas del estudio tal como las entiende el cliente (Semáforo del servicio). CANCELADO no es
// una etapa: se muestra como aviso aparte; REPROGRAMADO se ubica en "Programando".
const ETAPAS = [
  { estados: ['PENDIENTE'], label: 'Recibida' },
  { estados: ['PROGRAMANDO', 'REPROGRAMADO'], label: 'Programando' },
  { estados: ['EN_EJECUCION'], label: 'En ejecución' },
  { estados: ['FINALIZADO'], label: 'Finalizada' },
  { estados: ['PUBLICADO'], label: 'Publicada' },
]

const ETIQUETA_ESTADO = {
  PENDIENTE: 'Recibida', PROGRAMANDO: 'Programando', EN_EJECUCION: 'En ejecución', FINALIZADO: 'Finalizada',
  PUBLICADO: 'Publicada', CANCELADO: 'Cancelada', REPROGRAMADO: 'Reprogramada',
}

// Estado de cada subproceso en lenguaje del cliente — sin la jerga interna ("asignado", "por revisar").
const SUBPROCESO = {
  PENDIENTE:  { texto: 'Pendiente',                clase: 'bg-gray-100 text-gray-600',       punto: 'bg-gray-300',    hecho: false },
  PROGRAMANDO: { texto: 'Programando', clase: 'bg-sky-50 text-sky-700', punto: 'bg-sky-500', hecho: false },
  REPROGRAMADO: { texto: 'Reprogramado', clase: 'bg-orange-50 text-orange-800', punto: 'bg-orange-500', hecho: false },
  EN_EJECUCION:   { texto: 'En ejecución',              clase: 'bg-blue-50 text-blue-700',        punto: 'bg-blue-500',    hecho: false },
  FINALIZADO: { texto: 'Finalizado', clase: 'bg-violet-50 text-violet-700',    punto: 'bg-violet-500',  hecho: false },
  PUBLICADO:  { texto: 'Publicado',              clase: 'bg-emerald-50 text-emerald-700',  punto: 'bg-emerald-500', hecho: true },
  CANCELADO:  { texto: 'Cancelado',              clase: 'bg-red-50 text-red-600',          punto: 'bg-red-400',     hecho: false },
}

const NIVEL_RIESGO = {
  BAJO:  { banda: 'from-emerald-500 to-emerald-600', chip: 'bg-emerald-50 text-emerald-700 ring-emerald-200', texto: 'Riesgo bajo' },
  MEDIO: { banda: 'from-amber-400 to-amber-500',     chip: 'bg-amber-50 text-amber-700 ring-amber-200',       texto: 'Riesgo medio' },
  ALTO:  { banda: 'from-red-500 to-red-600',         chip: 'bg-red-50 text-red-700 ring-red-200',             texto: 'Riesgo alto' },
}

/* ─── Iconos ─── */
function IconoAtras() {
  return <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" /></svg>
}
function IconoCheck() {
  return <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}><path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" /></svg>
}
function IconoPdf() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3M6 20h12a2 2 0 002-2V8.828a2 2 0 00-.586-1.414l-3.828-3.828A2 2 0 0014.172 3H6a2 2 0 00-2 2v13a2 2 0 002 2z" />
    </svg>
  )
}

function CajaDato({ etiqueta, valor, className = '' }) {
  return (
    <div className={`min-w-0 rounded-xl border border-gray-200 bg-white px-3.5 py-2.5 ${className}`}>
      <dt className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">{etiqueta}</dt>
      <dd className="mt-0.5 text-sm font-medium text-gray-800 truncate" title={valor ?? undefined}>{valor || '—'}</dd>
    </div>
  )
}

/* ─── Línea de etapas ─── */
function LineaEtapas({ estado }) {
  const actual = ETAPAS.findIndex(e => e.estados.includes(estado))
  return (
    <ol className="grid grid-cols-5 gap-1">
      {ETAPAS.map((etapa, i) => {
        const hecha = i < actual || estado === 'PUBLICADO'
        const enCurso = i === actual && estado !== 'PUBLICADO'
        return (
          <li key={etapa.label} className="flex flex-col items-center text-center min-w-0">
            <div className="flex items-center w-full">
              <span className={`flex-1 h-0.5 ${i === 0 ? 'bg-transparent' : i <= actual ? 'bg-primary-500' : 'bg-gray-200'}`} />
              <span className={`flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center text-[11px] font-bold ring-4 ring-white ${
                hecha ? 'bg-primary-600 text-white' : enCurso ? 'bg-white text-primary-700 border-2 border-primary-500' : 'bg-gray-100 text-gray-400'
              }`}>
                {hecha ? <IconoCheck /> : i + 1}
              </span>
              <span className={`flex-1 h-0.5 ${i === ETAPAS.length - 1 ? 'bg-transparent' : i < actual ? 'bg-primary-500' : 'bg-gray-200'}`} />
            </div>
            <p className={`mt-2 text-[11px] sm:text-xs leading-tight ${enCurso ? 'font-semibold text-primary-700' : hecha ? 'text-gray-700' : 'text-gray-400'}`}>
              {etapa.label}
            </p>
          </li>
        )
      })}
    </ol>
  )
}

/* ─── Resultado del estudio (solo publicado) ─── */
function ResultadoEstudio({ riesgo }) {
  // Sin nivel o sin puntaje calculado: nunca «Riesgo bajo · 0,0» ni un nivel inventado.
  if (!NIVEL_RIESGO[riesgo?.nivel] || riesgo.puntaje == null) {
    return (
      <section className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5">
        <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">Resultado del estudio</p>
        <span className="mt-1.5 inline-flex items-center gap-2 text-base font-semibold px-3.5 py-1.5 rounded-full ring-1 bg-gray-100 text-gray-700 ring-gray-300">
          <span className="w-2.5 h-2.5 rounded-full bg-gray-400" />Sin calificar
        </span>
      </section>
    )
  }
  const cfg = NIVEL_RIESGO[riesgo.nivel]
  const puntaje = riesgo.puntaje != null
    ? Number(riesgo.puntaje).toLocaleString('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
    : '—'
  return (
    <section className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
      <div className={`h-1.5 bg-gradient-to-r ${cfg.banda}`} />
      <div className="p-5 flex flex-wrap items-center gap-x-8 gap-y-4">
        <div>
          <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Resultado del estudio</p>
          <span className={`mt-1.5 inline-flex items-center gap-2 text-base font-bold px-3.5 py-1.5 rounded-full ring-1 ${cfg.chip}`}>
            <span className="w-2.5 h-2.5 rounded-full bg-current" />{cfg.texto}
          </span>
        </div>
        <div>
          <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Puntaje</p>
          <p className="mt-1 text-2xl font-bold text-gray-900 tabular-nums">{puntaje}<span className="text-sm font-medium text-gray-400"> / 100</span></p>
        </div>
        <p className="text-xs text-gray-500 max-w-sm leading-relaxed">
          Consolidado de todos los componentes del estudio. El detalle y las observaciones están en los informes descargables.
        </p>
      </div>
    </section>
  )
}

/* ─── Línea de tiempo de la entrega: recibida → plazo → compromiso → hoy (o la entrega, ya congelada) ─── */
const diasHabiles = (n) => `${n} ${n === 1 ? 'día hábil' : 'días hábiles'}`

function Hito({ titulo, valor, nota, tono = 'gris' }) {
  const punto = { gris: 'bg-gray-400', rojo: 'bg-red-600', ambar: 'bg-amber-600', verde: 'bg-green-600', esmeralda: 'bg-emerald-600', naranja: 'bg-orange-600', azul: 'bg-primary-600' }[tono]
  return (
    <li className="relative flex-1 pl-6 sm:pl-0 sm:pt-6">
      <span aria-hidden="true" className={`absolute left-0 top-1 h-3 w-3 rounded-full ring-4 ring-white sm:left-0 sm:top-0 ${punto}`} />
      <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-600">{titulo}</p>
      <p className="mt-0.5 text-sm font-semibold text-gray-900">{valor}</p>
      {nota && <p className="mt-0.5 text-xs text-gray-600">{nota}</p>}
    </li>
  )
}

function LineaDeTiempo({ tiempo }) {
  const entregada = tiempo.estadoTiempo === 'ENTREGADA_A_TIEMPO' || tiempo.estadoTiempo === 'ENTREGADA_TARDE'
  const tonoCompromiso = { VENCIDA: 'rojo', POR_VENCER: 'ambar', A_TIEMPO: 'verde', EN_PAUSA: 'gris', ENTREGADA_A_TIEMPO: 'esmeralda', ENTREGADA_TARDE: 'naranja' }[tiempo.estadoTiempo] ?? 'gris'
  const hoy = new Date().toLocaleDateString('en-CA')   // la fecha local del navegador (AAAA-MM-DD), no la UTC
  const reprogramada = tiempo.fechaCompromisoOriginal && tiempo.fechaCompromiso && tiempo.fechaCompromisoOriginal !== tiempo.fechaCompromiso
  const notasCompromiso = [
    reprogramada ? `Reprogramada (antes ${fechaCorta(tiempo.fechaCompromisoOriginal)})` : null,
    tiempo.diasPausaHabiles > 0 ? `incluye ${diasHabiles(tiempo.diasPausaHabiles)} de pausa` : null,
  ].filter(Boolean).join(' · ')
  return (
    <section aria-label="Línea de tiempo de la entrega">
      <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-gray-600">Tiempo de entrega</p>
      <ol className="relative flex flex-col gap-4 sm:flex-row sm:gap-6 sm:border-t-2 sm:border-gray-200">
        <Hito titulo="Recibida" valor={fechaLarga(tiempo.fechaRecepcionEfectiva) || '—'} nota="El día de recepción no cuenta en el plazo" />
        <Hito titulo="Plazo" valor={tiempo.plazoDiasHabiles ? diasHabiles(tiempo.plazoDiasHabiles) : '—'} nota="Sin fines de semana ni festivos" tono="azul" />
        {compromisoPorDefinir(tiempo)
          ? <Hito titulo="Fecha de compromiso" valor="Por definir" nota={compromisoPorDefinir(tiempo).nota} tono={tonoCompromiso} />
          : <Hito titulo="Fecha de compromiso" valor={fechaLarga(tiempo.fechaCompromisoAjustada ?? tiempo.fechaCompromiso) || '—'} nota={notasCompromiso} tono={tonoCompromiso} />}
        {entregada
          ? <Hito titulo="Entregada" valor={fechaLarga(tiempo.fechaEntregaReal) || '—'} tono={tonoCompromiso}
              nota={tiempo.estadoTiempo === 'ENTREGADA_A_TIEMPO' ? 'Resultado: a tiempo' : `Resultado: con ${diasHabiles(tiempo.diasRetrasoHabiles)} de retraso`} />
          : <Hito titulo="Hoy" valor={fechaLarga(hoy)}
              nota={tiempo.estadoTiempo === 'VENCIDA' ? `Lleva ${diasHabiles(tiempo.diasRetrasoHabiles)} de retraso`
                : tiempo.estadoTiempo === 'EN_PAUSA' ? tiempo.etiqueta
                : tiempo.diasRestantesHabiles === 0 ? 'Vence hoy' : `Faltan ${diasHabiles(tiempo.diasRestantesHabiles)}`} />}
      </ol>
    </section>
  )
}

/* ─── ¿Qué sigue? — lo primero que el cliente necesita saber ─── */
function queSigue(detalle, novedadesAbiertas, proximaCita) {
  const cand = detalle.estadoCandidato
  if (detalle.estado === 'CANCELADO') return null
  if (detalle.estado === 'PUBLICADO') {
    return { tono: 'exito', titulo: 'Tu informe está listo', texto: 'El estudio terminó. Descarga los informes aquí abajo.' }
  }
  if (cand?.estado === 'VENCIDO') {
    return { tono: 'alerta', titulo: 'El candidato no terminó de diligenciar su información',
      texto: 'El enlace que le enviamos venció. Pide uno nuevo y se lo enviamos al candidato.', pedirEnlace: true, avance: cand }
  }
  if (cand?.estado === 'PENDIENTE') {
    return { tono: 'espera', titulo: 'Esperando la información del candidato',
      texto: `El estudio arranca cuando el candidato diligencie su hoja de vida con el enlace que recibió${cand.venceEl ? ` (vence el ${fechaHora(cand.venceEl)})` : ''}. Recuérdaselo si puedes.`,
      avance: cand }
  }
  if (novedadesAbiertas.length > 0) {
    const n = novedadesAbiertas[0]
    return { tono: 'alerta', titulo: `Novedad en gestión: ${n.tipo}`,
      texto: `En ${n.subproceso}. Nuestro equipo ya la está atendiendo; verás la respuesta apenas se resuelva.`, irA: 'novedades' }
  }
  if (proximaCita) {
    return { tono: 'info', titulo: `Próxima cita: ${proximaCita.nombre}`,
      texto: `${fechaHora(proximaCita.fechaProgramada)}. Confirma con el candidato que asista puntual.` }
  }
  if (detalle.estado === 'FINALIZADO') {
    return { tono: 'info', titulo: 'Estamos preparando tu informe final', texto: 'Todos los componentes se realizaron; el informe está en revisión de calidad.' }
  }
  return { tono: 'info', titulo: 'Tu estudio está en curso', texto: 'Te avisaremos por notificación cada vez que avance.' }
}

const TONO = {
  exito:  { caja: 'bg-emerald-50 border-emerald-200', titulo: 'text-emerald-900', texto: 'text-emerald-800', punto: 'bg-emerald-500' },
  alerta: { caja: 'bg-amber-50 border-amber-200',     titulo: 'text-amber-900',   texto: 'text-amber-800',   punto: 'bg-amber-500' },
  espera: { caja: 'bg-sky-50 border-sky-200',         titulo: 'text-sky-900',     texto: 'text-sky-800',     punto: 'bg-sky-500' },
  info:   { caja: 'bg-white border-gray-200',         titulo: 'text-gray-900',    texto: 'text-gray-600',    punto: 'bg-primary-500' },
}

function AvisoQueSigue({ aviso, onPedirEnlace, enlacePedido, pidiendoEnlace, onIrA }) {
  if (!aviso) return null
  const t = TONO[aviso.tono] ?? TONO.info
  const avance = aviso.avance
  const pct = avance?.porcentaje ?? 0
  return (
    <section className={`rounded-2xl border px-5 py-4 ${t.caja}`}>
      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <span className={`hidden sm:block flex-shrink-0 w-2.5 h-2.5 rounded-full ${t.punto}`} />
        <div className="min-w-0 flex-1">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400">Qué sigue</p>
          <p className={`text-sm font-semibold ${t.titulo}`}>{aviso.titulo}</p>
          <p className={`text-sm mt-0.5 ${t.texto}`}>{aviso.texto}</p>
        </div>
        {aviso.irA && (
          <button type="button" onClick={() => onIrA(aviso.irA)}
            className="flex-shrink-0 text-sm font-semibold px-4 py-2 rounded-lg bg-white border border-amber-300 text-amber-800 hover:bg-amber-100 transition-colors">
            Ver novedad
          </button>
        )}
        {aviso.pedirEnlace && (
          enlacePedido ? (
            <span className="flex-shrink-0 text-sm font-semibold text-emerald-700">✓ Le avisamos a tu gestor</span>
          ) : (
            <button type="button" onClick={onPedirEnlace} disabled={pidiendoEnlace}
              className="flex-shrink-0 text-sm font-semibold px-4 py-2 rounded-lg bg-white border border-amber-300 text-amber-800 hover:bg-amber-100 disabled:opacity-60 transition-colors">
              {pidiendoEnlace ? 'Enviando…' : 'Pedir nuevo enlace'}
            </button>
          )
        )}
      </div>
      {avance && (
        <div className="mt-3 sm:ml-5">
          <div className="flex items-center justify-between text-xs mb-1">
            <span className="text-gray-600">
              Avance del candidato
              {avance.pasoActual != null && PASOS_CANDIDATO[avance.pasoActual] && pct > 0 && (
                <> · va en <span className="font-semibold">{PASOS_CANDIDATO[avance.pasoActual]}</span> (paso {avance.pasoActual + 1} de {PASOS_CANDIDATO.length})</>
              )}
            </span>
            <span className="font-semibold text-gray-800 tabular-nums">{pct}%</span>
          </div>
          <div className="h-1.5 rounded-full bg-white/80 overflow-hidden ring-1 ring-black/5">
            <div className="h-full rounded-full bg-sky-500 transition-all" style={{ width: `${pct}%` }} />
          </div>
          {pct === 0 && <p className="text-[11px] text-gray-500 mt-1">Todavía no ha empezado a diligenciarla.</p>}
        </div>
      )}
    </section>
  )
}

/* ─── Mensaje al gestor, con textos ya armados ─── */
function plantillasMensaje(detalle, citas) {
  const quien = `${detalle.nombresEvaluado ?? ''} ${detalle.apellidosEvaluado ?? ''}`.trim()
  const ref = `(Solicitud #${detalle.idServicio}, CC ${detalle.cedulaEvaluado})`
  const lista = [
    { clave: 'cuando', label: '¿Cuándo estará listo?',
      texto: `Hola, quisiera saber cuándo estará listo el estudio de ${quien} ${ref}. Gracias.` },
  ]
  if (detalle.estadoCandidato && detalle.estadoCandidato.estado !== 'COMPLETADO') {
    lista.push({ clave: 'enlace', label: 'Reenviar enlace al candidato',
      texto: `Hola, por favor envíale un nuevo enlace a ${quien} ${ref} para que diligencie su información. Gracias.` })
  }
  if (citas.length > 0) {
    lista.push({ clave: 'reprogramar', label: 'Reprogramar una cita',
      texto: `Hola, necesitamos reprogramar la cita de ${citas[0].nombre} de ${quien} ${ref}. Nos serviría: ` })
  }
  lista.push({ clave: 'otra', label: 'Otra consulta', texto: `Hola, sobre la solicitud de ${quien} ${ref}: ` })
  return lista
}

function MensajeGestor({ detalle, citas, textareaRef }) {
  const plantillas = plantillasMensaje(detalle, citas)
  const [elegida, setElegida] = useState(null)
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [estado, setEstado] = useState(null) // { ok, mensaje }

  const elegir = (p) => {
    setElegida(p.clave)
    setTexto(p.texto)
    setEstado(null)
    setTimeout(() => {
      const el = textareaRef.current
      if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length) }
    }, 0)
  }

  const enviar = async () => {
    if (!texto.trim()) return
    setEnviando(true)
    try {
      await mensajeService.enviarCliente(texto.trim())
      setEstado({ ok: true, mensaje: 'Mensaje enviado. Tu gestor te responderá por el chat de mensajes.' })
      setTexto('')
      setElegida(null)
    } catch (e) {
      setEstado({ ok: false, mensaje: e.response?.data?.mensaje ?? 'No se pudo enviar el mensaje. Intenta de nuevo.' })
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {plantillas.map(p => (
          <button key={p.clave} type="button" onClick={() => elegir(p)}
            className={`text-xs font-medium px-3 py-1.5 rounded-full border transition-colors ${
              elegida === p.clave ? 'bg-primary-50 border-primary-300 text-primary-800' : 'bg-white border-gray-200 text-gray-600 hover:border-gray-300'
            }`}>
            {p.label}
          </button>
        ))}
      </div>
      <textarea aria-label="Mensaje" ref={textareaRef} value={texto} onChange={e => { setTexto(e.target.value); setEstado(null) }} rows={3} maxLength={2000}
        placeholder="Elige un mensaje de arriba o escribe el tuyo…"
        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
      <div className="flex flex-wrap items-center justify-between gap-2">
        {estado ? (
          <p className={`text-xs ${estado.ok ? 'text-emerald-700' : 'text-red-600'}`}>
            {estado.mensaje}
            {estado.ok && (
              <button type="button" onClick={() => window.dispatchEvent(new Event('abrir-mensajes-gestor'))}
                className="ml-1.5 font-semibold underline">Ver conversación</button>
            )}
          </p>
        ) : <span className="text-[11px] text-gray-400">Le llega a tu gestor asignado.</span>}
        <button type="button" onClick={enviar} disabled={enviando || !texto.trim()}
          className="text-sm font-semibold px-4 py-2 rounded-lg bg-primary-600 text-white hover:bg-primary-700 disabled:opacity-50 transition-colors">
          {enviando ? 'Enviando…' : 'Enviar'}
        </button>
      </div>
    </div>
  )
}

/* ─── Documentos del candidato (lista, sin abrirlos) ─── */
// Resultado de la revisión de nuestro equipo. Todo lo que no es Aprobado ni En revisión es una
// devolución: el candidato debe cargar uno nuevo.
const REVISION_DOC = {
  PENDIENTE:      { etiqueta: 'En revisión',    chip: 'bg-amber-50 text-amber-700' },
  APROBADO:       { etiqueta: 'Aprobado',       chip: 'bg-emerald-50 text-emerald-700' },
  DENEGADO:       { etiqueta: 'Denegado',       chip: 'bg-red-50 text-red-700' },
  ILEGIBLE:       { etiqueta: 'Ilegible',       chip: 'bg-red-50 text-red-700' },
  VENCIDO:        { etiqueta: 'Vencido',        chip: 'bg-red-50 text-red-700' },
  INCOMPLETO:     { etiqueta: 'Incompleto',     chip: 'bg-red-50 text-red-700' },
  NO_CORRESPONDE: { etiqueta: 'No corresponde', chip: 'bg-red-50 text-red-700' },
}
const docDevuelto = (e) => !!e && e !== 'PENDIENTE' && e !== 'APROBADO'

function DocumentosCandidato({ documentos }) {
  const cargados = documentos.filter(d => d.cargado).length
  const devueltos = documentos.filter(d => docDevuelto(d.estadoRevision)).length
  return (
    <>
      <p className="text-xs text-gray-500 mb-3">{cargados} de {documentos.length} cargados</p>
      {devueltos > 0 && (
        <p className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-800">
          {devueltos === 1 ? '1 documento fue devuelto' : `${devueltos} documentos fueron devueltos`} al candidato; el estudio espera la corrección.
        </p>
      )}
      <ul className="space-y-2">
        {documentos.map((d, i) => {
          const rev = d.cargado ? (REVISION_DOC[d.estadoRevision] ?? REVISION_DOC.PENDIENTE) : null
          const devuelto = d.cargado && docDevuelto(d.estadoRevision)
          return (
            <li key={`${d.nombre}-${i}`} className="flex items-start gap-3">
              <span className={`mt-0.5 flex-shrink-0 w-5 h-5 rounded-full flex items-center justify-center ${
                devuelto ? 'bg-red-500 text-white text-[11px] font-bold' : d.cargado ? 'bg-emerald-500 text-white' : 'border-2 border-gray-200'}`}>
                {devuelto ? '!' : d.cargado && <IconoCheck />}
              </span>
              <div className="min-w-0 flex-1">
                <p className={`flex flex-wrap items-center gap-1.5 text-sm ${d.cargado ? 'text-gray-900' : 'text-gray-500'}`}>
                  <span className="truncate">{d.nombre}</span>
                  {d.obligatorio && !d.cargado && <span className="text-[10px] font-semibold text-red-600">Obligatorio</span>}
                  {rev && <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${rev.chip}`}>{rev.etiqueta}</span>}
                </p>
                {d.cargado && d.fechaCarga && <p className="text-[11px] text-gray-400">Cargado el {fechaHora(d.fechaCarga)}</p>}
                {d.cargado && d.observacionRevision && (
                  <p className={`mt-1 rounded-md px-2 py-1 text-[11px] ${devuelto ? 'bg-red-50 text-red-800' : 'bg-gray-50 text-gray-600'}`}>
                    <span className="font-semibold">Observación:</span> {d.observacionRevision}
                  </p>
                )}
              </div>
            </li>
          )
        })}
      </ul>
      <p className="mt-4 text-[11px] text-gray-400 leading-relaxed">
        Por protección de datos del candidato, los documentos los revisa solo nuestro equipo.
      </p>
    </>
  )
}

/* ─── Novedades visibles para el cliente ─── */
function Novedades({ novedades }) {
  return (
    <ul className="space-y-3">
      {novedades.map(n => {
        const color = colorNovedad(n.color)
        const resuelta = n.estado === 'RESUELTA'
        return (
          <li key={n.id} className="rounded-xl border border-gray-100 overflow-hidden">
            <div className="px-4 py-3 flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  {n.categoria && (
                    <span className={`inline-flex items-center gap-1.5 text-[10px] font-semibold px-2 py-0.5 rounded-full border ${color.chip}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${color.punto}`} />{n.categoria}
                    </span>
                  )}
                </div>
                <p className="mt-1.5 text-sm font-semibold text-gray-900">{n.tipo}</p>
                {n.detalle && <p className="mt-1 text-sm text-gray-600">{n.detalle}</p>}
                <p className="text-xs text-gray-400 mt-0.5">Reportada el {fechaHora(n.fechaRegistro)}</p>
              </div>
              <span className={`flex-shrink-0 text-xs font-semibold px-2.5 py-1 rounded-full ${resuelta ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                {resuelta ? 'Resuelta' : 'En gestión'}
              </span>
            </div>
            {(resuelta || n.reprogramadoPara) && (
              <div className="px-4 py-3 bg-gray-50/80 border-t border-gray-100 space-y-1.5">
                {n.reprogramadoPara && (
                  <p className="text-sm text-gray-800">
                    <span className="font-semibold">Nueva cita:</span> {fechaHora(n.reprogramadoPara)}
                  </p>
                )}
                {n.respuesta && <p className="text-sm text-gray-600"><span className="font-medium text-gray-700">Respuesta:</span> {n.respuesta}</p>}
                {n.fechaResolucion && <p className="text-[11px] text-gray-400">Resuelta el {fechaHora(n.fechaResolucion)}</p>}
              </div>
            )}
          </li>
        )
      })}
    </ul>
  )
}

/* ─── Tablero: indicadores animados ─── */
function useContador(valor, duracion = 700) {
  const [actual, setActual] = useState(0)
  useEffect(() => {
    if (valor == null || Number.isNaN(valor)) return
    let raf
    const inicio = performance.now()
    const paso = (t) => {
      const avance = Math.min(1, (t - inicio) / duracion)
      setActual(Math.round(valor * (1 - Math.pow(1 - avance, 3))))
      if (avance < 1) raf = requestAnimationFrame(paso)
    }
    raf = requestAnimationFrame(paso)
    return () => cancelAnimationFrame(raf)
  }, [valor, duracion])
  return actual
}

function AnilloAvance({ pct }) {
  const [dibujado, setDibujado] = useState(0)
  useEffect(() => { const t = setTimeout(() => setDibujado(pct), 60); return () => clearTimeout(t) }, [pct])
  const r = 22
  const c = 2 * Math.PI * r
  return (
    <svg className="w-14 h-14 -rotate-90" viewBox="0 0 56 56">
      <circle cx="28" cy="28" r={r} fill="none" strokeWidth="6" className="stroke-gray-100" />
      <circle cx="28" cy="28" r={r} fill="none" strokeWidth="6" strokeLinecap="round"
        className="stroke-emerald-500 transition-[stroke-dashoffset] duration-1000 ease-out"
        strokeDasharray={c} strokeDashoffset={c - (c * dibujado) / 100} />
    </svg>
  )
}

function Indicador({ visual, valor, sufijo, etiqueta, detalle, tono = 'gris', onClick }) {
  const numero = useContador(typeof valor === 'number' ? valor : null)
  const TONOS = {
    gris:  'text-gray-900',
    verde: 'text-emerald-600',
    ambar: 'text-amber-600',
    rojo:  'text-red-600',
  }
  const Contenedor = onClick ? 'button' : 'div'
  return (
    <Contenedor type={onClick ? 'button' : undefined} onClick={onClick}
      className={`text-left bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-4 flex items-center gap-3 min-w-0 ${
        onClick ? 'hover:shadow-md hover:-translate-y-0.5 transition-all' : ''}`}>
      {visual}
      <div className="min-w-0">
        <p className={`text-2xl font-bold leading-none tabular-nums ${TONOS[tono]}`}>
          {typeof valor === 'number' ? numero : valor}{sufijo && <span className="text-sm font-semibold">{sufijo}</span>}
        </p>
        <p className="mt-1 text-xs font-medium text-gray-500">{etiqueta}</p>
        {detalle && <p className="text-[11px] text-gray-400 truncate">{detalle}</p>}
      </div>
    </Contenedor>
  )
}

/* ─── Valor del servicio ─── */
const ESTADO_PAGO = {
  PENDIENTE:    { texto: 'Factura pendiente de pago', clase: 'bg-amber-50 text-amber-700' },
  VENCIDA:      { texto: 'Factura vencida',           clase: 'bg-red-50 text-red-700' },
  PAGADA:       { texto: 'Factura pagada',            clase: 'bg-emerald-50 text-emerald-700' },
  SIN_FACTURAR: { texto: 'Se incluirá en tu próxima factura', clase: 'bg-gray-100 text-gray-600' },
  APROBADA:     { texto: 'Pagado con tu bolsa prepago', clase: 'bg-emerald-50 text-emerald-700' },
  CANCELADA:    { texto: 'Cancelada · no se factura', clase: 'bg-gray-100 text-gray-700' },
}

function FilaValor({ etiqueta, valor, detalle, tono = 'text-gray-800', fuerte }) {
  return (
    <div className={`flex items-start justify-between gap-3 py-2.5 ${fuerte ? 'border-t border-gray-200 mt-1 pt-3' : ''}`}>
      <div className="min-w-0">
        <p className={`text-sm ${fuerte ? 'font-semibold text-gray-900' : 'text-gray-600'}`}>{etiqueta}</p>
        {detalle && <p className="text-[11px] text-gray-400">{detalle}</p>}
      </div>
      <p className={`flex-shrink-0 text-sm tabular-nums ${fuerte ? 'text-base font-bold text-gray-900' : `font-medium ${tono}`}`}>{valor}</p>
    </div>
  )
}

function ValorServicio({ cobro, proceso }) {
  const cop = (v) => new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v ?? 0)
  return (
    <section className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5">
      <div className="flex items-center justify-between gap-2 mb-2">
        <h2 className="text-sm font-semibold text-gray-900">Valor del servicio</h2>
        {cobro?.modalidad && (
          <span className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-gray-100 text-gray-500">
            {cobro.modalidad === 'POSPAGO' ? 'Pospago' : 'Prepago'}
          </span>
        )}
      </div>
      {!cobro ? (
        <p className="text-sm text-gray-400">Todavía no hay un valor registrado para esta solicitud.</p>
      ) : (
        <>
          <div className="divide-y divide-gray-50">
            {cobro.valorServicio != null && (
              <FilaValor etiqueta={proceso ?? 'Servicio'} valor={cop(cobro.valorServicio)}
                detalle={cobro.valorReferencia ? 'Valor unitario de la compra de donde salió este servicio' : 'Valor del estudio'} />
            )}
            {cobro.valorSinDescuento != null && (
              <FilaValor etiqueta={`Descuento${cobro.descuento ? ` (${cobro.descuento})` : ''}`}
                valor={`− ${cop(cobro.valorSinDescuento - cobro.valorServicio)}`} tono="text-emerald-700"
                detalle={`Precio sin descuento: ${cop(cobro.valorSinDescuento)}`} />
            )}
            {Number(cobro.recargoPagado) !== 0 && (
              <FilaValor etiqueta="Recargo por ciudad" valor={cop(cobro.recargoPagado)}
                detalle={cobro.modalidad === 'POSPAGO' ? 'Incluido en el cargo' : 'Pagado'} />
            )}
            {Number(cobro.recargoPendiente) !== 0 && (
              <FilaValor etiqueta={cobro.recargoPendiente < 0 ? 'Crédito a tu favor (ciudad)' : 'Recargo por ciudad pendiente'}
                valor={cop(cobro.recargoPendiente)} tono={cobro.recargoPendiente < 0 ? 'text-emerald-700' : 'text-red-700'}
                detalle={cobro.recargoPendiente < 0 ? 'Se descuenta en tu próxima compra' : 'Por pagar'} />
            )}
            <FilaValor etiqueta="Total" valor={cop(cobro.total)} fuerte />
          </div>
          {cobro.estadoPago && ESTADO_PAGO[cobro.estadoPago] && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${ESTADO_PAGO[cobro.estadoPago].clase}`}>
                {ESTADO_PAGO[cobro.estadoPago].texto}
              </span>
              {cobro.referencia && <span className="text-xs text-gray-400">Ref. {cobro.referencia}</span>}
              {cobro.fechaVencimiento && cobro.estadoPago !== 'PAGADA' && (
                <span className="text-xs text-gray-400">· vence el {fechaLarga(cobro.fechaVencimiento)}</span>
              )}
            </div>
          )}
          {cobro.estadoPago === 'CANCELADA' ? (
            <p className="mt-3 text-[11px] text-gray-600 leading-relaxed">
              {cobro.modalidad === 'POSPAGO' ? 'Esta solicitud se canceló: el valor volvió a tu cupo y no entra en ninguna factura.' : 'Esta solicitud se canceló: la unidad volvió a tu bolsa prepago.'}
            </p>
          ) : cobro.valorReferencia && (
            <p className="mt-3 text-[11px] text-gray-400 leading-relaxed">
              Esta solicitud usó 1 servicio de tu bolsa prepago; el valor es el de la compra de donde salió.
            </p>
          )}
        </>
      )}
    </section>
  )
}

/* ─── Etapas de UN componente del estudio ─── */
const ETAPAS_COMPONENTE = [
  { estado: 'PENDIENTE', label: 'Pendiente' },
  { estado: 'EN_EJECUCION', label: 'En ejecución' },
  { estado: 'FINALIZADO', label: 'Finalizado' },
  { estado: 'PUBLICADO', label: 'Publicado' },
]
const TEXTO_COMPONENTE = {
  PENDIENTE: 'Todavía no empieza: arranca cuando le toque su turno en el estudio.',
  PROGRAMANDO: 'Estamos agendando la cita.',
  REPROGRAMADO: 'La cita no se pudo realizar y la estamos reagendando; te avisaremos la nueva fecha.',
  EN_EJECUCION: 'Nuestro equipo ya lo está realizando.',
  FINALIZADO: 'Ya se realizó; está en control de calidad y auditoría antes de entregarlo.',
  PUBLICADO: 'Terminado y aprobado.',
  CANCELADO: 'Este componente no se realizó.',
}

function EtapasComponente({ estado }) {
  if (estado === 'CANCELADO') {
    return <p className="text-xs font-medium text-red-600 bg-red-50 rounded-lg px-3 py-2 inline-block">Cancelado</p>
  }
  const actual = ETAPAS_COMPONENTE.findIndex(e => e.estado === estado)
  return (
    <ol className="flex items-center">
      {ETAPAS_COMPONENTE.map((e, i) => {
        const hecha = i < actual || estado === 'PUBLICADO'
        const enCurso = i === actual && estado !== 'PUBLICADO'
        return (
          <li key={e.estado} className="flex items-center flex-1 last:flex-none min-w-0">
            <div className="flex flex-col items-center min-w-0">
              <span className={`w-3 h-3 rounded-full ring-4 ${
                hecha ? 'bg-emerald-500 ring-emerald-100' : enCurso ? 'bg-primary-500 ring-primary-100 animate-pulse' : 'bg-gray-200 ring-gray-50'}`} />
              <span className={`mt-1.5 text-[10px] sm:text-[11px] whitespace-nowrap ${enCurso ? 'font-semibold text-primary-700' : hecha ? 'text-gray-600' : 'text-gray-400'}`}>{e.label}</span>
            </div>
            {i < ETAPAS_COMPONENTE.length - 1 && (
              <span className={`flex-1 h-0.5 mx-1.5 -mt-4 rounded-full ${i < actual ? 'bg-emerald-400' : 'bg-gray-200'}`} />
            )}
          </li>
        )
      })}
    </ol>
  )
}

/* ─── Sección desplegable con animación de altura ─── */
function Desplegable({ abierto, children }) {
  return (
    <div className={`grid transition-[grid-template-rows] duration-300 ease-out ${abierto ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'}`}>
      <div className="overflow-hidden">{children}</div>
    </div>
  )
}

function Chevron({ abierto }) {
  return (
    <svg className={`h-4 w-4 text-gray-400 transition-transform duration-300 ${abierto ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
    </svg>
  )
}

function SeccionPlegable({ titulo, resumen, abierto, onToggle, children, id, alerta }) {
  return (
    <section id={id} className={`relative bg-white rounded-2xl ring-1 shadow-sm overflow-hidden ${alerta ? 'ring-amber-200' : 'ring-gray-900/5'}`}>
      {(abierto || alerta) && <span className={`absolute left-0 top-0 bottom-0 w-1 ${abierto ? 'bg-primary-500' : 'bg-amber-400'}`} />}
      <button type="button" onClick={onToggle} aria-expanded={abierto} className="w-full px-5 py-4 flex items-center justify-between gap-3 text-left hover:bg-gray-50/60 transition-colors">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-gray-900">{titulo}</h2>
          {resumen && <p className={`text-xs mt-0.5 truncate ${alerta ? 'font-medium text-amber-700' : 'text-gray-400'}`}>{resumen}</p>}
        </div>
        <Chevron abierto={abierto} />
      </button>
      <Desplegable abierto={abierto}>
        <div className="px-5 pb-5 pt-1 border-t border-gray-100">{children}</div>
      </Desplegable>
    </section>
  )
}

export default function SolicitudDetalle() {
  const idCampos = useId()
  const { id } = useParams()
  const navigate = useNavigate()
  const { usuario } = useAuth()
  const [detalle, setDetalle] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [descargando, setDescargando] = useState(null)
  const [estadoDescarga, setEstadoDescarga] = useState(null)
  const [errorDescarga, setErrorDescarga] = useState(null)
  // Corta el polling del PDF si el usuario sale de la página antes de que termine.
  const abortoDescargaRef = useRef(null)
  useEffect(() => () => abortoDescargaRef.current?.abort(), [])
  const [actualizado, setActualizado] = useState(null)
  const [recargando, setRecargando] = useState(false)

  const cargar = useCallback((silencioso = false) => {
    if (silencioso) setRecargando(true)
    return solicitudService.detalle(id)
      .then(r => { setDetalle(r.data); setActualizado(new Date()); setError(null) })
      .catch(() => { if (!silencioso) setError('No se pudo cargar el detalle de la solicitud.') })
      .finally(() => { setCargando(false); setRecargando(false) })
  }, [id])

  useEffect(() => { cargar() }, [cargar])
  // Tiempo real: cuando el equipo avanza el estudio (agenda, novedades, estado), se ve sin refrescar.
  useCambios(() => cargar(true), { servicios: Number(id) })

  const mensajeRef = useRef(null)
  // Acordeón: solo UN elemento abierto a la vez. undefined = todavía no se tocó (se abre solo el
  // que necesita atención, ver abiertoPorDefecto más abajo); null = todos cerrados.
  const [abiertoElegido, setAbiertoElegido] = useState(undefined)
  const abrirYVer = (clave, idElemento) => {
    setAbiertoElegido(clave)
    setTimeout(() => document.getElementById(idElemento ?? clave)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 120)
  }
  const [escribiendo, setEscribiendo] = useState(false)
  const contactarGestor = () => setEscribiendo(true)

  // "Pedir nuevo enlace": un mensaje al gestor, en un clic.
  const [enlacePedido, setEnlacePedido] = useState(false)
  const [pidiendoEnlace, setPidiendoEnlace] = useState(false)
  const pedirEnlace = async () => {
    setPidiendoEnlace(true)
    try {
      await mensajeService.enviarCliente(`Hola, el enlace de ${detalle.nombresEvaluado ?? ''} ${detalle.apellidosEvaluado ?? ''} `
        + `(Solicitud #${detalle.idServicio}, CC ${detalle.cedulaEvaluado}) venció sin que terminara. Por favor envíale uno nuevo. Gracias.`)
      setEnlacePedido(true)
    } catch {
      window.alert('No se pudo enviar la solicitud a tu gestor. Intenta de nuevo.')
    } finally {
      setPidiendoEnlace(false)
    }
  }

  // Cancelar (solo antes de la ejecución, con motivo)
  const [cancelando, setCancelando] = useState(false)
  const [motivoCancelar, setMotivoCancelar] = useState('')
  const [enviandoCancelar, setEnviandoCancelar] = useState(false)
  const [errorCancelar, setErrorCancelar] = useState(null)
  const confirmarCancelar = async () => {
    if (!motivoCancelar.trim()) { setErrorCancelar('Cuéntanos el motivo de la cancelación.'); return }
    setEnviandoCancelar(true)
    setErrorCancelar(null)
    try {
      await solicitudService.cambiarEstado(id, 'CANCELADO', motivoCancelar.trim())
      setCancelando(false)
      setMotivoCancelar('')
      await cargar(true)
    } catch (e) {
      setErrorCancelar(e.response?.data?.mensaje ?? 'No se pudo cancelar la solicitud.')
    } finally {
      setEnviandoCancelar(false)
    }
  }

  // Solo el administrador de la cuenta descarga informes (el analista del cliente solo ve estados).
  const puedeDescargar = usuario?.rol === 'ADMIN_CLIENTE'

  const descargar = async (sp) => {
    setDescargando(sp.id)
    setEstadoDescarga(null)
    setErrorDescarga(null)
    abortoDescargaRef.current?.abort()
    const aborto = new AbortController()
    abortoDescargaRef.current = aborto
    try {
      // 202 + polling: el PDF se genera en la cola del backend (o sale de caché al instante).
      const inicial = await solicitudService.solicitarInforme(id, sp.id)
      const listo = await esperarTrabajo(inicial, { signal: aborto.signal, onActualizacion: setEstadoDescarga })
      const r = await trabajoService.descargarResultado(listo.id)
      guardarBlob(r, listo.nombreArchivo ?? `Informe_${sp.nombre}_${detalle.cedulaEvaluado}.pdf`)
    } catch (e) {
      if (e.cancelado) return
      setErrorDescarga(mensajeDeError(e, 'No se pudo descargar el informe. Intenta de nuevo o contacta a tu gestor.'))
    } finally {
      if (!aborto.signal.aborted) {
        setDescargando(null)
        setEstadoDescarga(null)
      }
    }
  }

  if (cargando) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
    </div>
  )

  if (error || !detalle) return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <p className="text-red-500 text-sm">{error || 'No encontrado.'}</p>
      <button onClick={() => navigate('/cliente/solicitudes')} className="text-sm font-medium text-primary-600 hover:underline">
        ← Volver a mis solicitudes
      </button>
    </div>
  )

  const subprocesos = detalle.subprocesos ?? []
  const activos = subprocesos.filter(sp => sp.estado !== 'CANCELADO')
  const terminados = activos.filter(sp => sp.estado === 'PUBLICADO').length
  const pct = activos.length > 0 ? Math.round((terminados / activos.length) * 100) : 0
  const cancelada = detalle.estado === 'CANCELADO'
  const publicada = detalle.estado === 'PUBLICADO'
  const novedades = detalle.novedades ?? []
  const novedadesAbiertas = novedades.filter(n => n.estado !== 'RESUELTA')
  const ahora = Date.now()
  const citas = subprocesos
    .filter(sp => sp.estado === 'EN_EJECUCION' && sp.fechaProgramada && new Date(sp.fechaProgramada).getTime() > ahora)
    .sort((a, b) => new Date(a.fechaProgramada) - new Date(b.fechaProgramada))
  const aviso = queSigue(detalle, novedadesAbiertas, citas[0])

  const documentos = detalle.documentosCandidato ?? []
  const docsCargados = documentos.filter(d => d.cargado).length
  const novedadesDe = (sp) => novedades.filter(n => n.subproceso === sp.nombre)

  // Se abre solo el componente que necesita atención: novedad en gestión, luego la próxima cita,
  // luego el primero que sigue en curso.
  const spPorDefecto = subprocesos.find(sp => novedadesDe(sp).some(n => n.estado !== 'RESUELTA'))
    ?? (citas[0] ? subprocesos.find(sp => sp.id === citas[0].id) : null)
    ?? subprocesos.find(sp => sp.estado === 'EN_EJECUCION' || sp.estado === 'FINALIZADO')
  const abierto = abiertoElegido === undefined ? (spPorDefecto ? `sp-${spPorDefecto.id}` : null) : abiertoElegido
  const alternarUnico = (clave) => setAbiertoElegido(abierto === clave ? null : clave)

  // El estado de tiempo (en días hábiles) lo calcula el servidor: aquí solo se presenta.
  const tiempo = detalle.tiempo
  const indicadorEntrega = cancelada || !tiempo
    ? { valor: '—', tono: 'gris', detalle: 'Sin fecha de entrega' }
    : tiempo.estadoTiempo === 'EN_PAUSA'
      // Mientras espera la hoja de vida del candidato la fecha es provisional: se recalcula cuando la envía.
      ? { valor: 'En pausa', tono: 'gris', detalle: compromisoPorDefinir(tiempo).nota }
      : tiempo.estadoTiempo === 'ENTREGADA_A_TIEMPO'
        ? { valor: 'Entregada', tono: 'verde', detalle: `a tiempo · ${fechaLarga(detalle.fechaEntregaEstimada)}` }
        : tiempo.estadoTiempo === 'ENTREGADA_TARDE'
          ? { valor: tiempo.diasRetrasoHabiles, sufijo: tiempo.diasRetrasoHabiles === 1 ? ' día hábil' : ' días hábiles', tono: 'ambar', detalle: 'de retraso al entregarla' }
          : tiempo.estadoTiempo === 'VENCIDA'
            ? { valor: tiempo.diasRetrasoHabiles, sufijo: tiempo.diasRetrasoHabiles === 1 ? ' día hábil' : ' días hábiles', tono: 'rojo', detalle: `de retraso · debía entregarse el ${fechaLarga(detalle.fechaEntregaEstimada)}` }
            : tiempo.diasRestantesHabiles === 0
              ? { valor: 'Hoy', tono: 'ambar', detalle: 'se entrega hoy' }
              : { valor: tiempo.diasRestantesHabiles, sufijo: tiempo.diasRestantesHabiles === 1 ? ' día hábil' : ' días hábiles', tono: tiempo.estadoTiempo === 'POR_VENCER' ? 'ambar' : 'verde', detalle: `entrega el ${fechaLarga(detalle.fechaEntregaEstimada)}` }

  return (
    <div className="max-w-5xl mx-auto space-y-5">
      {/* Migas */}
      <nav className="flex items-center gap-1.5 text-sm text-gray-500">
        <Link to="/cliente/solicitudes" className="inline-flex items-center gap-1 hover:text-gray-800 transition-colors">
          <IconoAtras /> Mis solicitudes
        </Link>
        <span className="text-gray-300">/</span>
        <span className="text-gray-800 font-medium">Solicitud #{detalle.idServicio}</span>
        <span className="ml-auto flex items-center gap-2 text-xs text-gray-400">
          {actualizado && <span className="hidden sm:inline">Actualizado {actualizado.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}</span>}
          <button type="button" onClick={() => cargar(true)} disabled={recargando}
            className="font-medium text-primary-600 hover:text-primary-700 disabled:opacity-50">
            {recargando ? 'Actualizando…' : 'Actualizar'}
          </button>
        </span>
      </nav>

      {/* Encabezado */}
      <header className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
        <div className="p-5 sm:p-6 flex flex-col md:flex-row md:items-center gap-4">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-semibold text-gray-600 bg-gray-100 px-2 py-1 rounded-md">Solicitud #{detalle.idServicio}</span>
              {/* El estado que ve el cliente es el de cada subproceso (el mismo de Mis solicitudes), no uno propio de la solicitud */}
              {[...new Set((detalle.subprocesos ?? []).map(sp => sp.estado))].map(e => <EtiquetaEstado key={e} estado={e} />)}
              <EstadoTiempo tiempo={detalle.tiempo} />
            </div>
            <h1 className="mt-2.5 text-2xl font-bold text-gray-900 tracking-tight truncate">
              {detalle.nombresEvaluado} {detalle.apellidosEvaluado}
            </h1>
            <p className="mt-1 text-sm text-gray-500 truncate">{detalle.proceso}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2 flex-shrink-0">
            {detalle.puedeCancelar && (
              <button type="button" onClick={() => { setCancelando(true); setErrorCancelar(null) }}
                className="text-sm font-medium text-gray-500 hover:text-red-600 px-3 py-2 rounded-lg hover:bg-red-50 transition-colors">
                Cancelar
              </button>
            )}
            <button type="button" onClick={contactarGestor}
              className="inline-flex items-center gap-1.5 text-sm font-semibold text-white bg-primary-600 rounded-lg px-3.5 py-2 hover:bg-primary-700 transition-colors">
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M8 10h.01M12 10h.01M16 10h.01M21 12c0 4.418-4.03 8-9 8a9.86 9.86 0 01-4-.8L3 20l1.3-3.9A7.96 7.96 0 013 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
              </svg>
              Escribir a mi gestor
            </button>
          </div>
        </div>
        <div className="px-5 sm:px-6 pb-5">
          <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Datos del candidato</p>
          <dl className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2.5">
            <CajaDato etiqueta="Cédula" valor={detalle.cedulaEvaluado} />
            <CajaDato etiqueta="Cargo" valor={detalle.cargo} />
            <CajaDato etiqueta="Ciudad" valor={detalle.nombreCiudad} />
            <CajaDato etiqueta="Celular" valor={detalle.celularEvaluado} />
            <CajaDato etiqueta="Correo" valor={detalle.emailEvaluado} className="col-span-2 sm:col-span-2 lg:col-span-1" />
          </dl>
        </div>
        <div className="px-5 sm:px-6 py-5 border-t border-gray-100 bg-gray-50/50">
          {cancelada ? (
            <p className="text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-4 py-3">
              Esta solicitud fue cancelada. Si crees que es un error, escríbele a tu gestor.
            </p>
          ) : (
            <LineaEtapas estado={detalle.estado} />
          )}
        </div>
        {!cancelada && detalle.tiempo && (
          <div className="px-5 sm:px-6 py-5 border-t border-gray-100">
            <LineaDeTiempo tiempo={detalle.tiempo} />
          </div>
        )}
      </header>

      {/* Indicadores */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <Indicador visual={<AnilloAvance pct={pct} />} valor={pct} sufijo="%" etiqueta="Avance del estudio"
          detalle={`${terminados} de ${activos.length} componentes`} tono={pct === 100 ? 'verde' : 'gris'} />
        <Indicador valor={indicadorEntrega.valor} sufijo={indicadorEntrega.sufijo} tono={indicadorEntrega.tono}
          etiqueta={publicada ? 'Estado de entrega' : 'Para la entrega'} detalle={indicadorEntrega.detalle}
          visual={<span className={`flex-shrink-0 w-11 h-11 rounded-xl flex items-center justify-center ${
            indicadorEntrega.tono === 'rojo' ? 'bg-red-50 text-red-600' : indicadorEntrega.tono === 'ambar' ? 'bg-amber-50 text-amber-600' : 'bg-emerald-50 text-emerald-600'}`}>
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
          </span>} />
        <Indicador valor={novedadesAbiertas.length > 0 ? novedadesAbiertas.length : novedades.length}
          tono={novedadesAbiertas.length > 0 ? 'ambar' : 'gris'}
          etiqueta={novedadesAbiertas.length > 0 ? (novedadesAbiertas.length === 1 ? 'Novedad en gestión' : 'Novedades en gestión') : 'Novedades'}
          detalle={novedades.length === 0 ? 'Todo en orden' : novedadesAbiertas.length > 0 ? `${novedades.length} en total` : 'Todas resueltas'}
          onClick={novedades.length > 0 ? () => {
            abrirYVer('novedades', 'seccion-novedades')
          } : undefined}
          visual={<span className={`flex-shrink-0 w-11 h-11 rounded-xl flex items-center justify-center ${novedadesAbiertas.length > 0 ? 'bg-amber-50 text-amber-600' : 'bg-gray-50 text-gray-400'}`}>
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" /></svg>
          </span>} />
        <Indicador valor={documentos.length > 0 ? `${docsCargados}/${documentos.length}` : '—'}
          tono={documentos.length > 0 && docsCargados === documentos.length ? 'verde' : 'gris'}
          etiqueta="Documentos" detalle={documentos.length === 0 ? 'No se piden' : docsCargados === documentos.length ? 'Completos' : `Faltan ${documentos.length - docsCargados}`}
          onClick={() => document.getElementById('seccion-documentos')?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
          visual={<span className="flex-shrink-0 w-11 h-11 rounded-xl bg-sky-50 text-sky-600 flex items-center justify-center">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
          </span>} />
      </div>

      <AvisoQueSigue aviso={aviso} onPedirEnlace={pedirEnlace} enlacePedido={enlacePedido} pidiendoEnlace={pidiendoEnlace}
        onIrA={() => abrirYVer('novedades', 'seccion-novedades')} />

      {publicada && detalle.riesgo && <ResultadoEstudio riesgo={detalle.riesgo} />}
      {errorDescarga && (
        <p className="text-xs text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">{errorDescarga}</p>
      )}

      {/* Componentes del estudio — acordeón, uno abierto a la vez */}
      {subprocesos.length > 0 && (
        <section className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">Componentes del estudio</h2>
              <p className="text-xs text-gray-400 mt-0.5">Toca un componente para ver su detalle</p>
            </div>
            <span className="text-xs font-medium text-gray-500">{terminados} de {activos.length} terminados</span>
          </div>
          <ol className="divide-y divide-gray-100">
            {subprocesos.map((sp, i) => {
              const cfg = SUBPROCESO[sp.estado] ?? { texto: sp.estado, clase: 'bg-gray-100 text-gray-600', punto: 'bg-gray-300' }
              const clave = `sp-${sp.id}`
              const estaAbierto = abierto === clave
              const suyas = novedadesDe(sp)
              const enGestion = suyas.filter(n => n.estado !== 'RESUELTA').length
              const cita = sp.estado === 'EN_EJECUCION' && sp.fechaProgramada && new Date(sp.fechaProgramada).getTime() > ahora
                ? new Date(sp.fechaProgramada) : null
              return (
                <li key={clave} id={clave} className={`relative transition-colors ${estaAbierto ? 'bg-primary-50/30' : ''}`}>
                  <span className={`absolute left-0 top-0 bottom-0 w-1 transition-colors ${estaAbierto ? 'bg-primary-500' : enGestion > 0 ? 'bg-amber-400' : 'bg-transparent'}`} />
                  <button type="button" onClick={() => alternarUnico(clave)} aria-expanded={estaAbierto}
                    className="w-full pl-6 pr-5 py-4 flex items-center gap-4 text-left hover:bg-gray-50/70 transition-colors">
                    <span className={`flex-shrink-0 w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold ${
                      cfg.hecho ? 'bg-emerald-500 text-white'
                        : sp.estado === 'CANCELADO' ? 'bg-red-50 text-red-500'
                          : sp.estado === 'PENDIENTE' ? 'bg-gray-100 text-gray-400'
                            : 'bg-primary-100 text-primary-700'}`}>
                      {cfg.hecho ? <IconoCheck /> : i + 1}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className={`text-sm font-semibold truncate ${estaAbierto ? 'text-primary-800' : 'text-gray-900'}`}>{sp.nombre}</p>
                      <p className="text-xs text-gray-500 truncate mt-0.5">
                        {cita
                          ? <span className="font-medium text-primary-700">Cita {cita.toLocaleDateString('es-CO', { weekday: 'short', day: 'numeric', month: 'short' })} · {cita.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}</span>
                          : sp.informeDisponible ? 'Informe disponible' : cfg.texto}
                        {enGestion > 0 && <span className="ml-2 font-semibold text-amber-700">· {enGestion} novedad{enGestion === 1 ? '' : 'es'} en gestión</span>}
                      </p>
                    </div>
                    <span className={`hidden sm:inline-flex flex-shrink-0 text-xs font-medium px-2.5 py-1 rounded-full whitespace-nowrap ${cfg.clase}`}>{cfg.texto}</span>
                    <Chevron abierto={estaAbierto} />
                  </button>
                  <Desplegable abierto={estaAbierto}>
                    <div className="pl-6 sm:pl-[4.25rem] pr-5 pb-5">
                      <div className="rounded-xl bg-white ring-1 ring-gray-100 p-4 space-y-4">
                        <EtapasComponente estado={sp.estado} />
                        <p className="text-xs text-gray-500">{TEXTO_COMPONENTE[sp.estado] ?? ''}</p>
                        {(sp.fechaProgramada || suyas.length > 0 || sp.informeDisponible) && (
                          <div className="flex flex-wrap items-center gap-2 pt-3 border-t border-gray-100">
                            {sp.fechaProgramada && (
                              <span className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1.5 rounded-lg bg-primary-50 text-primary-800">
                                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                                {cita ? 'Cita' : 'Programado'}: {fechaHora(sp.fechaProgramada)}
                              </span>
                            )}
                            {suyas.length > 0 && (
                              <button type="button" onClick={() => abrirYVer('novedades', 'seccion-novedades')}
                                className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1.5 rounded-lg transition-colors ${
                                  enGestion > 0 ? 'bg-amber-50 text-amber-800 hover:bg-amber-100' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
                                Ver {suyas.length} novedad{suyas.length === 1 ? '' : 'es'} →
                              </button>
                            )}
                            {sp.informeDisponible && (
                              puedeDescargar ? (
                                <button type="button" onClick={() => descargar(sp)} disabled={descargando === sp.id}
                                  className="ml-auto inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg bg-primary-600 text-white hover:bg-primary-700 disabled:opacity-60 transition-colors">
                                  <IconoPdf /> {descargando === sp.id ? textoEstadoDescarga(estadoDescarga) : 'Descargar informe (PDF)'}
                                </button>
                              ) : (
                                <span className="ml-auto text-[11px] text-gray-400">Informe listo · lo descarga el administrador</span>
                              )
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  </Desplegable>
                </li>
              )
            })}
          </ol>
        </section>
      )}

      {detalle.notas && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50/70 px-5 py-4">
          <p className="text-[11px] font-semibold text-amber-700 uppercase tracking-wider">Nota de tu gestor</p>
          <p className="mt-1 text-sm text-amber-900 leading-relaxed">{detalle.notas}</p>
        </div>
      )}

      {/* Novedades — acordeón propio, agrupadas por componente */}
      <SeccionPlegable id="seccion-novedades" titulo="Novedades"
        resumen={novedades.length === 0 ? 'Sin novedades: todo en orden'
          : novedadesAbiertas.length > 0 ? `${novedadesAbiertas.length} en gestión · ${novedades.length} en total` : `${novedades.length} · todas resueltas`}
        alerta={novedadesAbiertas.length > 0}
        abierto={abierto === 'novedades'} onToggle={() => alternarUnico('novedades')}>
        {novedades.length === 0 ? (
          <p className="pt-4 text-sm text-gray-400">Si pasa algo con el candidato o una cita, lo verás aquí con su solución.</p>
        ) : (
          <div className="pt-4 space-y-5">
            {subprocesos.filter(sp => novedadesDe(sp).length > 0).map(sp => (
              <div key={sp.id}>
                <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">{sp.nombre}</p>
                <Novedades novedades={novedadesDe(sp)} />
              </div>
            ))}
          </div>
        )}
      </SeccionPlegable>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-start">
        {/* Documentos del candidato — tarjeta propia */}
        <section id="seccion-documentos" className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5">
          <h2 className="text-sm font-semibold text-gray-900 mb-1">Documentos del candidato</h2>
          {documentos.length > 0
            ? <DocumentosCandidato documentos={documentos} />
            : <p className="text-sm text-gray-400 mt-2">Este servicio no pide documentos al candidato.</p>}
        </section>

        {/* ── 2. Valor del servicio ── */}
        <ValorServicio cobro={detalle.cobro} proceso={detalle.proceso} />
      </div>

      <SeccionPlegable titulo="Historial"
        resumen={detalle.historial?.length ? `Último cambio: ${fechaHora(detalle.historial[0].fechaCambio)}` : 'Sin cambios todavía'}
        abierto={abierto === 'historial'} onToggle={() => alternarUnico('historial')}>
        <div className="pt-4 space-y-6">
          {detalle.historial?.length ? (
            <ol className="relative space-y-5">
              {detalle.historial.map((h, i) => (
                <li key={i} className="relative pl-7">
                  {i < detalle.historial.length - 1 && <span className="absolute left-[7px] top-4 bottom-[-1.25rem] w-px bg-gray-200" />}
                  <span className={`absolute left-0 top-1 w-[15px] h-[15px] rounded-full border-[3px] border-white ring-1 ${i === 0 ? 'bg-primary-500 ring-primary-200' : 'bg-gray-300 ring-gray-200'}`} />
                  <p className="text-sm font-medium text-gray-900">
                    {h.estadoAnterior
                      ? <>Pasó de <span className="text-gray-500">{ETIQUETA_ESTADO[h.estadoAnterior] ?? h.estadoAnterior}</span> a {ETIQUETA_ESTADO[h.estadoNuevo] ?? h.estadoNuevo}</>
                      : <>Solicitud {(ETIQUETA_ESTADO[h.estadoNuevo] ?? h.estadoNuevo).toLowerCase()}</>}
                  </p>
                  <p className="text-xs text-gray-400 mt-0.5">{fechaHora(h.fechaCambio)}</p>
                  {h.observacion && <p className="mt-1.5 text-sm text-gray-600 bg-gray-50 rounded-lg px-3 py-2">{h.observacion}</p>}
                </li>
              ))}
            </ol>
          ) : (
            <p className="text-sm text-gray-400">Sin cambios registrados todavía.</p>
          )}

        </div>
      </SeccionPlegable>

      {escribiendo && (
        <Modal titulo="Escríbele a tu gestor" subtitulo={`Solicitud #${detalle.idServicio} · ${detalle.nombresEvaluado ?? ''} ${detalle.apellidosEvaluado ?? ''}`}
          ancho="max-w-lg" onClose={() => setEscribiendo(false)}>
          <div className="px-6 py-5">
            <MensajeGestor detalle={detalle} citas={citas} textareaRef={mensajeRef} />
          </div>
        </Modal>
      )}

      {cancelando && (
        <Modal titulo="Cancelar solicitud" subtitulo={`Solicitud #${detalle.idServicio} · ${detalle.nombresEvaluado ?? ''} ${detalle.apellidosEvaluado ?? ''}`}
          onClose={() => !enviandoCancelar && setCancelando(false)}>
          <div className="px-6 py-5 space-y-3">
            <p className="text-sm text-gray-600">El estudio no se realizará. Esta acción no se puede deshacer desde tu cuenta.</p>
            <div>
              <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Motivo <span className="text-red-500">*</span></label>
              <textarea id={`${idCampos}-1`} value={motivoCancelar} onChange={e => setMotivoCancelar(e.target.value)} rows={3} maxLength={500}
                placeholder="Ej: El candidato desistió del proceso."
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-400" />
            </div>
            {errorCancelar && <p className="text-xs text-red-600">{errorCancelar}</p>}
            <div className="flex justify-end gap-2 pt-1">
              <button type="button" onClick={() => setCancelando(false)} disabled={enviandoCancelar}
                className="px-4 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50">Volver</button>
              <button type="button" onClick={confirmarCancelar} disabled={enviandoCancelar}
                className="px-4 py-2 text-sm font-semibold text-white bg-red-600 rounded-lg hover:bg-red-700 disabled:opacity-60">
                {enviandoCancelar ? 'Cancelando…' : 'Sí, cancelar'}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}

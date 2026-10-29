import { useState, useEffect, useRef, useId, Fragment } from 'react'
import { asociarEtiqueta } from '../../utils/asociarEtiqueta'
import { Link, useLocation, useParams, useNavigate } from 'react-router-dom'
import formulariosService from '../../services/formulariosService'
import trabajoService, { esperarTrabajo, guardarBlob, mensajeDeError } from '../../services/trabajoService'
import asignacionService from '../../services/asignacionService'
import riesgoService from '../../services/riesgoService'
import hallazgosService, { etiquetaHallazgo } from '../../services/hallazgosService'
import { ChipNivel, SinCalificar } from '../../components/RiesgoServicio'
import Toast from '../../components/Toast'
import TablaHojaVidaEditable from '../../components/TablaHojaVidaEditable'
import NovedadesSubproceso from '../../components/NovedadesSubproceso'
import SoportesInforme from '../../components/SoportesInforme'
import FichaCandidato from '../../components/FichaCandidato'
import { ConfirmModal } from '../../components/ui/Modal'
import CiudadSelect from '../../components/CiudadSelect'
import BarrioSelect from '../../components/BarrioSelect'
import SelectorMotivo, { motivoIncompleto, MOTIVO_VACIO } from '../../components/SelectorMotivo'

// Compartida entre analista, poligrafista y visitador — mismo formulario universal para
// cualquier subproceso, solo cambia el texto/ruta de "volver" según en cuál portal se abrió (cada
// portal nombra su lista de trabajo distinto: asignaciones / servicios / visitas).
const VOLVER = {
  analista: { ruta: '/analista/asignaciones', etiqueta: 'Mis Asignaciones' },
  poligrafista: { ruta: '/poligrafista/servicios', etiqueta: 'Mis Servicios' },
  visitador: { ruta: '/visitador/visitas', etiqueta: 'Mis Visitas' },
  gestor: { ruta: '/gestor/solicitudes', etiqueta: 'Solicitudes' },
  programador: { ruta: '/programador/agenda', etiqueta: 'Agenda' },
  admin: { ruta: '/admin/dashboard', etiqueta: 'Panel' },
}

// Mismo acento de color por tipo de sección que usa admin/catálogo → Plantilla, para que el
// formulario se sienta como la misma familia visual, no una pantalla aparte.
const ACENTO_TIPO = {
  CAMPOS: 'border-l-primary-400',
  TABLA: 'border-l-violet-400',
}
const BADGE_TIPO = {
  CAMPOS: 'bg-primary-50 text-primary-600',
  TABLA: 'bg-violet-50 text-violet-600',
}
const CHIP_TIPO = {
  CAMPOS: 'bg-primary-100 text-primary-700',
  TABLA: 'bg-violet-100 text-violet-700',
}
const ETIQUETA_TIPO = { CAMPOS: 'Campos', TABLA: 'Tabla' }

let contador = 0
const idLocal = () => `local-${++contador}`

const inputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-gray-50 disabled:text-gray-500 print:border-none print:p-0 print:text-black"

// Mismo ícono por tipo de dato que usa admin/catálogo → Plantilla, para que un campo se reconozca
// igual en los dos lados (quien arma la plantilla y quien la llena).
const ICONO_TIPO_DATO = { TEXTO: 'Aa', NUMERO: '#', FECHA: '📅', BOOLEANO: '✓', LISTA: '▾', ARCHIVO: '📎', CIUDAD: '📍', BARRIO: '🏘' }

function Campo({ label, tipoDato, automatico, calculado, obligatorio, children }) {
  const id = useId()
  return (
    <div>
      {label && label.trim() && (
        <label htmlFor={id} className="flex items-start gap-1.5 text-xs font-medium text-gray-600 mb-1 print:font-semibold">
          {tipoDato && (
            <span className="flex-shrink-0 mt-px w-4 h-4 rounded bg-gray-100 text-gray-400 text-[9px] flex items-center justify-center print:hidden">
              {ICONO_TIPO_DATO[tipoDato] ?? ''}
            </span>
          )}
          <span className="min-w-0 break-words leading-snug">{label}</span>
          {obligatorio && <span className="text-red-500" title="Obligatorio">*</span>}
          {automatico && (
            <span className="flex-shrink-0 text-[9px] font-semibold px-1 py-0.5 rounded bg-primary-50 text-primary-500 print:hidden"
              title={calculado ? 'Lo calcula el sistema' : 'Dato que se llena solo'}>
              {calculado ? 'calculado' : 'auto'}
            </span>
          )}
        </label>
      )}
      {asociarEtiqueta(children, { id })}
    </div>
  )
}

function nombreArchivo(ruta) {
  const segmento = ruta.split('/').pop()
  const idx = segmento.indexOf('_')
  return idx >= 0 ? segmento.slice(idx + 1) : segmento
}

function esImagen(ruta) {
  return /\.(jpe?g|png)$/i.test(ruta)
}

// El valor de una columna ARCHIVO es un array JSON de rutas (soporta varios adjuntos por campo).
// Si viene una ruta suelta (formularios guardados antes de que existiera esto), se trata como un
// array de un solo elemento.
function parseRutasArchivo(value) {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : [value]
  } catch {
    return [value]
  }
}

function MiniaturaArchivo({ idServicioSubproceso, ruta, onQuitar, onError, disabled }) {
  const [urlPreview, setUrlPreview] = useState(null)
  const [abriendo, setAbriendo] = useState(false)

  useEffect(() => {
    let url = null
    if (esImagen(ruta)) {
      formulariosService.descargarArchivo(idServicioSubproceso, ruta).then(blob => {
        url = window.URL.createObjectURL(blob)
        setUrlPreview(url)
      }).catch(() => {})
    }
    return () => { if (url) window.URL.revokeObjectURL(url) }
  }, [idServicioSubproceso, ruta])

  const verArchivo = async () => {
    setAbriendo(true)
    try {
      const blob = await formulariosService.descargarArchivo(idServicioSubproceso, ruta)
      const url = window.URL.createObjectURL(blob)
      window.open(url, '_blank')
      setTimeout(() => window.URL.revokeObjectURL(url), 60000)
    } catch {
      onError('No se pudo abrir el archivo.')
    } finally {
      setAbriendo(false)
    }
  }

  return (
    <div className="relative group">
      {urlPreview ? (
        <button type="button" onClick={verArchivo} disabled={abriendo} title={nombreArchivo(ruta)}
          className="h-16 w-16 rounded-lg border border-gray-200 overflow-hidden bg-gray-50 print:border-none">
          <img src={urlPreview} alt={nombreArchivo(ruta)} className="h-full w-full object-cover" />
        </button>
      ) : (
        <button type="button" onClick={verArchivo} disabled={abriendo} title={nombreArchivo(ruta)}
          className="h-16 w-16 flex flex-col items-center justify-center gap-1 rounded-lg border border-gray-200 bg-gray-50 text-gray-500 text-[10px] px-1 text-center">
          <span>📄</span>
          <span className="truncate w-full">{nombreArchivo(ruta)}</span>
        </button>
      )}
      {!disabled && (
        <button type="button" onClick={onQuitar} title="Quitar"
          className="absolute -top-1.5 -right-1.5 h-4 w-4 flex items-center justify-center rounded-full bg-red-500 text-white text-[10px] opacity-0 group-hover:opacity-100 transition-opacity print:hidden">
          ×
        </button>
      )}
    </div>
  )
}

function CampoArchivo({ idServicioSubproceso, value, onChange, onError, disabled }) {
  const [subiendo, setSubiendo] = useState(false)
  const rutas = parseRutasArchivo(value)

  const subir = async (archivo) => {
    if (!archivo) return
    setSubiendo(true)
    try {
      const ruta = await formulariosService.subirArchivo(idServicioSubproceso, archivo)
      onChange(JSON.stringify([...rutas, ruta]))
    } catch (err) {
      onError(err.response?.data?.mensaje ?? 'No se pudo subir el archivo.')
    } finally {
      setSubiendo(false)
    }
  }

  const quitar = (idx) => onChange(JSON.stringify(rutas.filter((_, i) => i !== idx)))

  return (
    <div className="flex flex-wrap gap-2 print:hidden">
      {rutas.map((ruta, idx) => (
        <MiniaturaArchivo key={ruta} idServicioSubproceso={idServicioSubproceso} ruta={ruta}
          onQuitar={() => quitar(idx)} onError={onError} disabled={disabled} />
      ))}
      {!disabled && (
        <label className="h-16 w-16 flex flex-col items-center justify-center gap-0.5 rounded-lg border border-dashed border-gray-300 text-gray-400 hover:border-primary-300 hover:text-primary-600 cursor-pointer text-[10px] text-center px-1">
          <span className="text-base leading-none">{subiendo ? '…' : '+'}</span>
          {subiendo ? 'Subiendo' : 'Agregar'}
          <input type="file" accept=".pdf,.jpg,.jpeg,.png" className="hidden" disabled={subiendo}
            onChange={e => subir(e.target.files[0])} />
        </label>
      )}
      <div className="hidden print:flex print:flex-col gap-1 w-full">
        {rutas.map(ruta => <span key={ruta} className="text-xs">📎 {nombreArchivo(ruta)}</span>)}
      </div>
    </div>
  )
}

// Texto que crece solo con lo escrito (sin barra de desplazamiento), con la ortografía del
// navegador en español y el contador "Llevas 250 de 500 caracteres" contra el máximo del campo (V209).
// Se vuelve a medir cuando aparece en pantalla (el formulario muestra una sección a la vez y,
// medido escondido, quedaba sin altura) y cada vez que se escribe.
function TextoAjustable({ value, onChange, disabled, maxLength }) {
  const ref = useRef(null)
  const texto = value ?? ''
  const ajustar = () => {
    const el = ref.current
    if (!el || el.offsetParent === null) return // escondido: se mide al aparecer
    el.style.height = 'auto'
    el.style.height = `${Math.max(el.scrollHeight + 2, 40)}px`
  }
  useEffect(ajustar, [texto])
  useEffect(() => {
    const el = ref.current
    if (!el || typeof ResizeObserver === 'undefined') return undefined
    let ancho = 0
    // Solo cuando cambia el ancho (al aparecer la sección o al cambiar la ventana): medir cambia la
    // altura y no debe volver a disparar la medición.
    const obs = new ResizeObserver(([e]) => {
      const nuevo = Math.round(e.contentRect.width)
      if (nuevo !== ancho) { ancho = nuevo; requestAnimationFrame(ajustar) }
    })
    obs.observe(el)
    return () => obs.disconnect()
  }, [])
  const total = texto.length
  const pct = maxLength ? Math.min(100, Math.round((total / maxLength) * 100)) : 0
  const tono = !maxLength ? 'text-gray-400' : pct >= 100 ? 'text-red-600' : pct >= 90 ? 'text-amber-600' : 'text-gray-400'
  const barra = pct >= 100 ? 'bg-red-500' : pct >= 90 ? 'bg-amber-500' : 'bg-primary-500'
  return (
    <div>
      <textarea ref={ref} rows={1} value={texto} onChange={e => onChange(e.target.value)} onFocus={ajustar} disabled={disabled}
        maxLength={maxLength || undefined} spellCheck lang="es" autoCorrect="on"
        style={{ minHeight: 40 }}
        className={`${inputCls} block resize-none overflow-hidden leading-relaxed`} />
      {!disabled && (
        <div className="mt-1 flex items-center justify-end gap-2 print:hidden">
          {maxLength && (
            <span className="h-1 w-16 overflow-hidden rounded-full bg-gray-100">
              <span className={`block h-full rounded-full transition-all ${barra}`} style={{ width: `${pct}%` }} />
            </span>
          )}
          <span className={`text-[11px] tabular-nums ${tono}`}>
            Llevas <b className="font-semibold">{total.toLocaleString('es-CO')}</b>
            {maxLength ? <> de {maxLength.toLocaleString('es-CO')}</> : null} caracteres
          </span>
        </div>
      )}
    </div>
  )
}

function InputPorTipo({ tipoDato, value, opciones, onChange, idServicioSubproceso, onError, disabled, etiquetas, maxLength }) {
  if (tipoDato === 'ARCHIVO') {
    return <CampoArchivo idServicioSubproceso={idServicioSubproceso} value={value} onChange={onChange} onError={onError} disabled={disabled} />
  }
  // Mismo catálogo de ciudades/barrios que usa el candidato — el valor se guarda como texto (el
  // nombre), igual que un campo LISTA/TEXTO normal. CiudadSelect ya soporta este modo
  // (valueType="nombre"); BarrioSelect en "modo libre" (sin idCiudad) busca en todo el catálogo,
  // ya que estas columnas no están necesariamente emparejadas con una de tipo CIUDAD en la misma
  // sección.
  if (tipoDato === 'CIUDAD') {
    return <CiudadSelect valueType="nombre" value={value} onChange={onChange} className={inputCls} disabled={disabled} />
  }
  if (tipoDato === 'BARRIO') {
    return <BarrioSelect valueType="nombre" value={value} onChange={onChange} className={inputCls} disabled={disabled} />
  }
  if (tipoDato === 'LISTA') {
    const elegida = value ? (etiquetas?.[value] ?? value) : ''
    return (
      <>
        <select value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls} title={elegida || undefined}>
          <option value="">—</option>
          {(opciones ?? []).filter(o => !(etiquetas && o === '—')).map(o => <option key={o} value={o}>{etiquetas?.[o] ?? o}</option>)}
        </select>
        {elegida.length > 40 && (
          <p className="mt-1 px-1 text-xs leading-snug text-gray-600 break-words print:hidden">{elegida}</p>
        )}
      </>
    )
  }
  if (tipoDato === 'BOOLEANO') {
    return (
      <select value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls}>
        <option value="">—</option>
        <option value="true">Sí</option>
        <option value="false">No</option>
      </select>
    )
  }
  if (tipoDato === 'FECHA') {
    return <input type="date" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls} />
  }
  if (tipoDato === 'NUMERO') {
    return <input type="number" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls} />
  }
  return <TextoAjustable value={value} onChange={onChange} disabled={disabled} maxLength={maxLength} />
}

// Aparece debajo de una respuesta CRÍTICA (ver ColumnaResponse.opcionesCriticas): quien diligencia
// debe explicar en qué se basa antes de poder enviar. No se le muestran puntos — solo que la
// respuesta es determinante (docs/modelo-calificacion-riesgo.md §3).
function JustificacionCritica({ valor, onChange, disabled }) {
  const vacia = !valor || !valor.trim()
  return (
    <div className={`mt-1.5 rounded-lg border px-2.5 py-2 print:border-gray-300 ${vacia ? 'border-red-200 bg-red-50/70' : 'border-amber-200 bg-amber-50/60'}`}>
      <p className={`text-[11px] font-medium mb-1 ${vacia ? 'text-red-700' : 'text-amber-800'}`}>
        Esta respuesta es determinante: explica en qué te basas{vacia ? ' (obligatorio para enviar)' : ''}.
      </p>
      <textarea aria-label="Motivo de la novedad" value={valor ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} rows={2} maxLength={2000} spellCheck lang="es"
        placeholder="Ej: El certificado no tiene firma ni sello del empleador; la empresa no reconoce al candidato."
        className="w-full border border-gray-200 bg-white rounded-md px-2 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-amber-400 disabled:bg-gray-50" />
    </div>
  )
}

const esCritica = (c, valor) => valor != null && String(valor).trim() !== '' && (c.opcionesCriticas ?? []).includes(String(valor).trim())

/* ─── Iconos ─── */
function IconoDocumento() {
  return (
    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
    </svg>
  )
}
function IconoDescargar() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
    </svg>
  )
}
function IconoGuardar() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M17.593 3.322c1.1.128 1.907 1.077 1.907 2.185V21L12 17.25 4.5 21V5.507c0-1.108.806-2.057 1.907-2.185a48.507 48.507 0 0111.186 0z" />
    </svg>
  )
}
function IconoCheckCompletar() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
    </svg>
  )
}
function IconoMas() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
    </svg>
  )
}
function IconoPapelera() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
    </svg>
  )
}
function IconoFlechaIzq() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
    </svg>
  )
}
function IconoFlechaDer() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
    </svg>
  )
}
function IconoCheck() {
  return (
    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
    </svg>
  )
}
function IconoCandado() {
  return (
    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
    </svg>
  )
}
function IconoAprobar() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
    </svg>
  )
}
function IconoDevolver() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 15L3 9m0 0l6-6M3 9h12a6 6 0 010 12h-3" />
    </svg>
  )
}

function IconoPersona() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
    </svg>
  )
}

function IconoClip() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M18.375 12.739l-7.693 7.693a4.5 4.5 0 01-6.364-6.364l10.94-10.94A3 3 0 1119.5 7.372L8.552 18.32m.009-.01l-.01.01m5.699-9.941l-7.81 7.81a1.5 1.5 0 002.112 2.13" />
    </svg>
  )
}

function IconoBandera() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 3v1.5M3 21v-6m0 0l2.77-.693a9 9 0 016.208.682l.108.054a9 9 0 006.086.71l3.114-.732a48.524 48.524 0 01-.005-10.499l-3.11.732a9 9 0 01-6.085-.711l-.108-.054a9 9 0 00-6.208-.682L3 4.5M3 15V4.5" />
    </svg>
  )
}

// Anillo de avance del encabezado — porcentaje por secciones (ver porcentajeTotal).
/** Solo en secciones que el administrador dejó como OPCIONALES (V212). */
function BotonQuitarSeccion({ onClick }) {
  return (
    <button type="button" onClick={onClick} title="Esta sección no aplica para este caso"
      className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-2 py-1 text-[11px] font-medium text-gray-500 hover:border-red-200 hover:bg-red-50 hover:text-red-600 transition-colors">
      <IconoPapelera /> Quitar sección
    </button>
  )
}

/** Lo que se ve en el paso de una sección que quien diligencia quitó. */
function SeccionQuitada({ nombre, onAgregar, disabled }) {
  return (
    <section className="rounded-2xl border border-dashed border-gray-300 bg-gray-50/60 p-6 text-center">
      <p className="text-xs font-semibold uppercase tracking-wider text-gray-400 line-through">{nombre}</p>
      <p className="mt-2 text-sm text-gray-600">Quitaste esta sección: no se diligencia y no saldrá en el informe.</p>
      {!disabled && (
        <button type="button" onClick={onAgregar}
          className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-primary-200 bg-white px-3 py-1.5 text-xs font-semibold text-primary-700 hover:bg-primary-50">
          <IconoMas /> Volver a agregarla
        </button>
      )}
    </section>
  )
}

function AnilloAvance({ porcentaje }) {
  const radio = 26
  const circunferencia = 2 * Math.PI * radio
  const color = porcentaje === 100 ? 'text-green-500' : porcentaje > 0 ? 'text-primary-500' : 'text-gray-300'
  return (
    <div className="relative w-16 h-16">
      <svg className="w-16 h-16 -rotate-90" viewBox="0 0 64 64">
        <circle cx="32" cy="32" r={radio} fill="none" strokeWidth="6" className="stroke-gray-100" />
        <circle cx="32" cy="32" r={radio} fill="none" strokeWidth="6" strokeLinecap="round"
          className={`stroke-current ${color} transition-all duration-500`}
          strokeDasharray={circunferencia} strokeDashoffset={circunferencia * (1 - porcentaje / 100)} />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-sm font-bold text-gray-800">{porcentaje}%</span>
    </div>
  )
}

// Estado del subproceso en palabras de quien lo ve (pill de la franja del encabezado).
const ESTADO_SUBPROCESO = {
  PENDIENTE:  { etiqueta: 'Pendiente',          punto: 'bg-gray-400' },
  PROGRAMANDO: { etiqueta: 'Programando', punto: 'bg-sky-500' },
  REPROGRAMADO: { etiqueta: 'Reprogramado', punto: 'bg-orange-500' },
  EN_EJECUCION:   { etiqueta: 'En curso',           punto: 'bg-primary-500' },
  FINALIZADO: { etiqueta: 'Enviado a revisión', punto: 'bg-amber-500' },
  PUBLICADO:  { etiqueta: 'Aprobado',           punto: 'bg-green-500' },
  CANCELADO:  { etiqueta: 'Cancelado',          punto: 'bg-red-500' },
}

// Plantilla (campos del formulario) y documento Word van de la mano: la plantilla define QUÉ se
// llena y el Word CÓMO se ve el documento final. Mientras el subproceso está en curso ambos
// siguen lo vigente (lo que haya hoy en admin/catálogo → Plantilla); al marcarlo completado los
// dos quedan fijos, para que el documento de un trabajo ya cerrado nunca cambie.
function InfoPlantilla({ meta }) {
  const version = meta?.versionPlantilla
  const actual = meta?.versionPlantillaActual
  if (!version) return null
  let texto
  if (meta?.soloLectura) {
    texto = `Quedó fijada en la versión ${version} al completar el subproceso.`
  } else if (actual != null && actual !== version) {
    texto = `Se creó con la versión ${version}; ahora usa la vigente (v${actual}) porque el administrador cambió campos después.`
  } else {
    texto = 'Es la versión vigente. Se fija al marcar el subproceso como completado.'
  }
  const cambio = !meta?.soloLectura && actual != null && actual !== version
  return (
    <div className="flex items-start gap-2.5 min-w-0">
      <span className="flex-shrink-0 mt-0.5 w-7 h-7 rounded-lg bg-gray-100 text-gray-500 text-[11px] font-bold flex items-center justify-center">
        v{meta?.soloLectura ? version : (actual ?? version)}
      </span>
      <div className="min-w-0">
        <p className="text-xs font-semibold text-gray-700">
          Plantilla del formulario
          {cambio && <span className="ml-1.5 text-[10px] font-medium text-amber-700">· actualizada</span>}
        </p>
        <p className="text-[11px] text-gray-500 leading-snug">{texto}</p>
      </div>
    </div>
  )
}

// Riesgo arriba del formulario (decisión 25-sep-2026): quien diligencia ve el nivel y puntaje de
// SU subproceso — parcial mientras lo llena, con lo guardado hasta ahora —; el gestor y el admin
// ven además el nivel final del estudio (automático o cambiado por el gestor).
const PREFIJOS_RIESGO = ['analista', 'poligrafista', 'visitador', 'gestor', 'admin']
const ESTUDIO_SIN_NIVEL = {
  EN_PROCESO: 'En proceso',
  SIN_INFORMACION: 'Sin calificar',
  NO_CALIFICA: 'Sin calificar',
}
const puntajeTexto = (v) => v == null ? '' : Number(v).toLocaleString('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

// Puntos de riesgo de UNA fila de tabla: suma los puntos de la respuesta elegida en cada columna
// calificable (Lista o Sí/No). Mismo criterio que el motor (hallazgos por fila, que se suman).
function puntosDeFila(columnas, valores) {
  let puntos = 0
  let critica = false
  let califica = false
  for (const c of columnas ?? []) {
    if (!c.calificaRiesgo) continue
    califica = true
    const v = valores?.[c.idColumna]
    if (v == null || String(v).trim() === '') continue
    const p = (c.puntajes ?? []).find(x => x.opcion === String(v).trim())
    if (!p) continue
    puntos += Number(p.puntos ?? 0)
    if (p.esCritico) critica = true
  }
  return { califica, puntos, critica }
}

const formatoPuntos = (n) => Number(n).toLocaleString('es-CO', { maximumFractionDigits: 1 })

// Lo máximo que puede sumar UNA fila: en cada columna calificable, la opción con más puntos.
function maximoPorFila(columnas) {
  return (columnas ?? []).filter(c => c.calificaRiesgo)
    .reduce((acc, c) => acc + Math.max(0, ...(c.puntajes ?? []).map(p => Number(p.puntos ?? 0))), 0)
}

// Resumen de una tabla calificable: filas, cuánto lleva y cuánto podría sumar como máximo.
function resumenTabla(columnas, filas) {
  if (!(columnas ?? []).some(c => c.calificaRiesgo)) return null
  const resultados = (filas ?? []).map(f => puntosDeFila(columnas, f.valores))
  return {
    filas: resultados.length,
    lleva: resultados.reduce((acc, r) => acc + r.puntos, 0),
    maximo: maximoPorFila(columnas) * resultados.length,
    conPuntos: resultados.filter(r => r.puntos > 0).length,
    criticas: resultados.filter(r => r.critica).length,
  }
}

function PuntosFila({ columnas, valores }) {
  const r = puntosDeFila(columnas, valores)
  if (!r.califica) return null
  return (
    <span className={`flex-shrink-0 text-[11px] font-semibold px-2 py-0.5 rounded-full ring-1 print:hidden ${
      r.critica ? 'bg-red-50 text-red-700 ring-red-200' : r.puntos > 0 ? 'bg-amber-50 text-amber-700 ring-amber-200' : 'bg-gray-50 text-gray-400 ring-gray-200'
    }`} title="Puntos de riesgo que suma esta fila">
      {r.critica ? 'Crítica' : `+${formatoPuntos(r.puntos)} pts`}
    </span>
  )
}

// Total de la tabla: cuántas filas suman puntos y cuánto lleva en total.
function TotalPuntosTabla({ columnas, filas }) {
  const r = resumenTabla(columnas, filas)
  if (!r || r.filas === 0) return null
  return (
    <div className="mt-3 flex flex-wrap items-center justify-end gap-x-4 gap-y-1 text-xs text-gray-500 print:hidden">
      <span>{r.conPuntos} de {r.filas} {r.filas === 1 ? 'fila suma' : 'filas suman'} puntos</span>
      {r.criticas > 0 && <span className="font-semibold text-red-700">{r.criticas} crítica{r.criticas === 1 ? '' : 's'}</span>}
      <span className="font-semibold text-gray-800 bg-gray-100 px-2.5 py-1 rounded-lg">
        Lleva {formatoPuntos(r.lleva)} de {formatoPuntos(r.maximo)} pts
      </span>
    </div>
  )
}

function FranjaRiesgo({ riesgo, tablas }) {
  if (!riesgo || (!riesgo.califica && !riesgo.estudio)) return null
  const { estudio } = riesgo
  return (
    <div className="border-t border-primary-100/70 print:hidden">
    <div className="px-5 sm:px-6 py-3 flex flex-wrap items-center gap-x-8 gap-y-3">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400 w-full sm:w-auto">Calificación de riesgo</p>

      <div className="min-w-0">
        <p className="text-[11px] text-gray-400">Este subproceso</p>
        {!riesgo.califica ? (
          <p className="text-xs text-gray-400">Su plantilla no suma puntos</p>
        ) : riesgo.estado === 'NO_REALIZADO' ? (
          <p className="text-sm font-medium text-gray-500">No realizado</p>
        ) : riesgo.calculado === false || !riesgo.nivel ? (
          // Sin puntaje calculado: nunca «BAJO 0»
          <div className="mt-0.5">
            <SinCalificar motivo={riesgo.motivoSinCalcular} />
            {riesgo.motivoSinCalcular && <p className="text-[11px] text-gray-500 mt-1">{riesgo.motivoSinCalcular}</p>}
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-2 mt-0.5">
            <ChipNivel nivel={riesgo.nivel} />
            <span className="text-sm font-semibold text-gray-800">{puntajeTexto(riesgo.puntaje)}</span>
            {riesgo.estado === 'EN_CURSO' && (
              <span className="text-[10px] font-medium text-gray-500 bg-gray-100 px-1.5 py-0.5 rounded"
                title="Se calcula con lo guardado hasta ahora; queda definitivo al enviar">Parcial</span>
            )}
            {riesgo.tieneCritica && (
              <span className="text-[10px] font-semibold text-red-700 bg-red-50 ring-1 ring-red-200 px-1.5 py-0.5 rounded">Respuesta crítica</span>
            )}
          </div>
        )}
      </div>

      {estudio && (
        <div className="min-w-0 sm:pl-8 sm:border-l sm:border-primary-100">
          <p className="text-[11px] text-gray-400">Estudio completo</p>
          {estudio.estado === 'CALCULADO' ? (
            <div className="flex flex-wrap items-center gap-2 mt-0.5">
              <ChipNivel nivel={estudio.nivel} />
              <span className="text-sm font-semibold text-gray-800">{puntajeTexto(estudio.puntaje)}</span>
              <span className="text-[10px] text-gray-500">{estudio.cambiadoPorGestor ? 'Cambiado por el gestor' : 'Automático'}</span>
            </div>
          ) : (
            <p className="text-sm font-medium text-gray-500">{ESTUDIO_SIN_NIVEL[estudio.estado] ?? 'Sin calificar'}</p>
          )}
        </div>
      )}
    </div>

    {/* Tablas que suman puntos: cuántas filas tiene cada una y cuánto lleva de lo máximo posible */}
    {tablas?.length > 0 && (
      <div className="px-5 sm:px-6 pb-3 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        {tablas.map(t => {
          const pct = t.maximo > 0 ? Math.min(100, Math.round((t.lleva / t.maximo) * 100)) : 0
          return (
            <div key={t.id} className="rounded-lg border border-gray-100 bg-gray-50/60 px-3 py-2 min-w-0">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-semibold text-gray-700 truncate" title={t.nombre}>{t.nombre}</p>
                {t.criticas > 0 && <span className="text-[10px] font-semibold text-red-700">{t.criticas} crítica{t.criticas === 1 ? '' : 's'}</span>}
              </div>
              <p className="text-[11px] text-gray-500 mt-0.5">
                {t.filas} {t.filas === 1 ? 'fila' : 'filas'} con {formatoPuntos(t.maximo)} pts posibles ·{' '}
                <span className="font-semibold text-gray-800">lleva {formatoPuntos(t.lleva)} de {formatoPuntos(t.maximo)}</span>
              </p>
              <div className="mt-1.5 h-1.5 rounded-full bg-gray-200 overflow-hidden">
                <div className={`h-full rounded-full ${t.criticas > 0 ? 'bg-red-500' : 'bg-amber-500'}`} style={{ width: `${pct}%` }} />
              </div>
            </div>
          )
        })}
      </div>
    )}
    </div>
  )
}

function InfoDocumentoWord({ meta }) {
  const nombre = meta?.nombreArchivoWord
  const texto = !nombre
    ? 'Este subproceso no tiene documento Word configurado — no se puede generar el PDF. Avísale al administrador.'
    : meta?.soloLectura
      ? 'Es el documento con el que se completó; no cambia aunque el administrador suba uno nuevo.'
      : 'El PDF se genera con este documento y la plantilla vigente. Se fija al marcar el subproceso como completado.'
  return (
    <div className="flex items-start gap-2.5 min-w-0">
      <span className={`flex-shrink-0 mt-0.5 w-7 h-7 rounded-lg flex items-center justify-center ${nombre ? 'bg-gray-100 text-gray-500' : 'bg-amber-50 text-amber-600'}`}>
        <IconoDocumentoPequeno />
      </span>
      <div className="min-w-0">
        <p className="text-xs font-semibold text-gray-700 truncate" title={nombre ?? undefined}>
          Documento Word{nombre ? `: ${nombre}` : ''}
        </p>
        <p className={`text-[11px] leading-snug ${nombre ? 'text-gray-500' : 'text-amber-700'}`}>{texto}</p>
      </div>
    </div>
  )
}

function IconoDocumentoPequeno() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
    </svg>
  )
}

// "125" -> "2 h 5 min"; "1500" -> "1 d 1 h".
function formatoDuracion(minutos) {
  const m = Math.max(0, Math.round(minutos))
  if (m < 60) return `${m} min`
  const d = Math.floor(m / 1440)
  const h = Math.floor((m % 1440) / 60)
  const resto = m % 60
  if (d > 0) return `${d} d${h > 0 ? ` ${h} h` : ''}`
  return `${h} h${resto > 0 ? ` ${resto} min` : ''}`
}

function IconoReloj() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  )
}

// Tiempo de ejecución del subproceso para el encabezado: cuánto lleva desde que empezó (la hora
// programada o, si no tiene — p.ej. analista —, desde que se asignó) y cuánto va de retraso
// frente a la duración estimada. Si una novedad lo reprograma, la hora de inicio se corre y el
// conteo arranca de nuevo desde ahí. Se refresca solo cada 30 segundos.
function TiempoEjecucion({ meta }) {
  const [ahora, setAhora] = useState(() => Date.now())
  useEffect(() => {
    const t = setInterval(() => setAhora(Date.now()), 30000)
    return () => clearInterval(t)
  }, [])

  const inicioIso = meta?.fechaProgramada ?? meta?.fechaAsignacion
  if (!inicioIso || !['EN_EJECUCION', 'FINALIZADO', 'PUBLICADO'].includes(meta?.estado)) return null
  const inicio = new Date(inicioIso).getTime()
  const estimado = meta?.duracionEstimadaMinutos ?? null
  const desdeTexto = meta?.fechaProgramada ? 'programado' : 'asignado'
  const fechaInicioTexto = new Date(inicioIso).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' })

  let chip
  if (meta.estado === 'EN_EJECUCION' && ahora < inicio) {
    chip = { clase: 'bg-gray-100 text-gray-600', texto: `Inicia en ${formatoDuracion((inicio - ahora) / 60000)}` }
  } else if (meta.estado === 'EN_EJECUCION') {
    const transcurrido = (ahora - inicio) / 60000
    if (estimado && transcurrido > estimado) {
      chip = { clase: 'bg-red-50 text-red-700 ring-1 ring-red-200',
        texto: `Lleva ${formatoDuracion(transcurrido)} · ${formatoDuracion(transcurrido - estimado)} de retraso` }
    } else {
      chip = { clase: 'bg-green-50 text-green-700 ring-1 ring-green-200',
        texto: `Lleva ${formatoDuracion(transcurrido)}${estimado ? ` · quedan ${formatoDuracion(estimado - transcurrido)}` : ''}` }
    }
  } else if (meta.fechaCompletado) {
    const total = (new Date(meta.fechaCompletado).getTime() - inicio) / 60000
    const pasado = estimado && total > estimado
    chip = { clase: pasado ? 'bg-amber-50 text-amber-700 ring-1 ring-amber-200' : 'bg-gray-100 text-gray-600',
      texto: `Se ejecutó en ${formatoDuracion(total)}${pasado ? ` · ${formatoDuracion(total - estimado)} más del estimado` : ''}` }
  }

  // Entrega comprometida con el cliente (fecha del servicio, no del subproceso).
  let entrega = null
  if (meta?.fechaEntregaEstimada) {
    const [y, m, d] = meta.fechaEntregaEstimada.split('-').map(Number)
    const finEntrega = new Date(y, m - 1, d, 23, 59, 59).getTime()
    const texto = new Date(y, m - 1, d).toLocaleDateString('es-CO', { day: '2-digit', month: 'short' })
    const vencida = meta.estado === 'EN_EJECUCION' && ahora > finEntrega
    entrega = vencida
      ? { clase: 'text-red-600 font-medium', texto: `Entrega al cliente vencida (${texto})` }
      : { clase: 'text-gray-500', texto: `Entrega al cliente: ${texto}` }
  }

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 mt-2.5 print:hidden">
      {chip && (
        <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${chip.clase}`}>
          <IconoReloj /> {chip.texto}
        </span>
      )}
      <span className="text-[11px] text-gray-400">
        {desdeTexto === 'programado' ? 'Inicio programado' : 'Asignado'}: {fechaInicioTexto}
        {estimado ? ` · duración estimada ${formatoDuracion(estimado)}` : ''}
      </span>
      {entrega && <span className={`text-[11px] ${entrega.clase}`}>· {entrega.texto}</span>}
    </div>
  )
}

export default function Formulario() {
  const { idServicioSubproceso } = useParams()
  const location = useLocation()
  const prefijo = location.pathname.split('/')[1]
  const volver = VOLVER[prefijo] ?? VOLVER.analista
  const navigate = useNavigate()

  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [descargando, setDescargando] = useState(false)
  const [completando, setCompletando] = useState(false)
  const [aprobando, setAprobando] = useState(false)
  const [devolviendo, setDevolviendo] = useState(false)
  const [motivoDevolver, setMotivoDevolver] = useState(MOTIVO_VACIO)
  const [enviandoDevolver, setEnviandoDevolver] = useState(false)
  const [toast, setToast] = useState(null)
  const [meta, setMeta] = useState(null)
  const [riesgo, setRiesgo] = useState(null)
  const [secciones, setSecciones] = useState([])
  // Secciones OPCIONALES que quien diligencia quitó (V212): no se llenan ni salen en el informe.
  const [omitidas, setOmitidas] = useState(() => new Set())
  const [quitando, setQuitando] = useState(null) // { idSeccion, nombre } mientras se confirma
  // Nombre y puntos de cada hallazgo, para las listas de los campos de hallazgo.
  const [etiquetasHallazgo, setEtiquetasHallazgo] = useState({})
  useEffect(() => {
    hallazgosService.listar().then(l => setEtiquetasHallazgo(Object.fromEntries(l.map(h => [h.codigo, etiquetaHallazgo(h)]))))
      .catch(() => setEtiquetasHallazgo({}))
  }, [])
  const etiquetasDe = (c) => (c.calificacion?.uso === 'HALLAZGO' ? etiquetasHallazgo : undefined)
  // Inicio del contenido del paso: al cambiar de sección se lleva la vista aquí (la página se
  // desplaza dentro del panel, no en la ventana, así que window.scrollTo no servía).
  const inicioContenido = useRef(null)
  const [hojaVidaCandidato, setHojaVidaCandidato] = useState(null)
  const [paso, setPaso] = useState(0)
  const [generandoPdf, setGenerandoPdf] = useState(false)
  const abortoPdfRef = useRef(null)
  useEffect(() => () => abortoPdfRef.current?.abort(), [])
  // Cambios hechos por el usuario que todavía no se guardaron — controla tanto el resaltado del
  // botón "Guardar" como el disparador del guardado automático (ver autoguardadoActivo abajo).
  const [sucio, setSucio] = useState(false)
  // Contador de ediciones — permite detectar si llegó un cambio nuevo MIENTRAS un autoguardado
  // ya estaba en camino, para no marcar "guardado" un cambio que en realidad quedó afuera de esa
  // llamada (ver guardar()).
  const cambiosRef = useRef(0)
  // Antes, si el autoguardado fallaba (ej. un conflicto real en el servidor), "sucio" se quedaba
  // en true para siempre y el efecto de abajo lo volvía a intentar cada 2.5s — sin límite, sin
  // aviso claro, machacando al servidor con el MISMO error indefinidamente (confirmado: 234 veces
  // en un rato contra la BD real). Con esta bandera, un fallo pausa los reintentos automáticos
  // hasta que el usuario edite algo de nuevo (marcarSucio la resetea) o le dé a "Guardar" a mano.
  const [autoguardadoFallido, setAutoguardadoFallido] = useState(false)
  const marcarSucio = () => { cambiosRef.current += 1; setSucio(true); setAutoguardadoFallido(false) }

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })
  const irANovedadesRef = useRef(false)
  // Cuántas novedades siguen abiertas — lo reporta NovedadesSubproceso (se monta desde el inicio,
  // aunque su paso no esté visible) para mostrar el contador en el índice lateral.
  const [novedadesAbiertas, setNovedadesAbiertas] = useState(0)
  // Texto corto para el índice ("3 de 5 cargados") — lo reporta FichaCandidato (seccion="soportes").
  const [resumenSoportes, setResumenSoportes] = useState(null)

  // Riesgo del encabezado: se consulta al abrir y después de cada guardado (el recálculo corre al
  // confirmar el guardado). Si no aplica o no hay acceso, simplemente no se muestra.
  const cargarRiesgo = () => {
    if (!PREFIJOS_RIESGO.includes(prefijo)) return
    riesgoService.resumenSubproceso(idServicioSubproceso).then(setRiesgo).catch(() => setRiesgo(null))
  }
  useEffect(() => {
    setRiesgo(null)
    cargarRiesgo()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idServicioSubproceso])

  // Depende también de location.key: una notificación que lleva al MISMO formulario (ej. otra
  // novedad del subproceso que ya está abierto) vuelve a cargarlo y aplica ?paso=novedades.
  useEffect(() => {
    setCargando(true)
    setPaso(0)
    setSucio(false)
    // Desde una notificación de novedad (ver Campana.jsx) se llega con ?paso=novedades — se abre
    // directo el último paso en vez del primero, apenas termine de cargar (ver efecto más abajo).
    irANovedadesRef.current = new URLSearchParams(window.location.search).get('paso') === 'novedades'
    // (Novedades ya es el primer paso, así que hoy basta con arrancar en 0 — se deja el
    // parámetro por si el orden de pasos cambia.)
    formulariosService.obtenerFormulario(idServicioSubproceso)
      .then(r => {
        setMeta({
          idFormulario: r.idFormulario,
          idCandidato: r.idCandidato,
          nombreEvaluado: r.nombreEvaluado,
          cedulaEvaluado: r.cedulaEvaluado,
          nombreProfesionalEncargado: r.nombreProfesionalEncargado,
          soloLectura: r.soloLectura,
          datosCandidato: r.datosCandidato ?? [],
          tablasFijas: r.tablasFijas ?? [],
          versionPlantilla: r.versionPlantilla,
          versionPlantillaActual: r.versionPlantillaActual,
          estado: r.estado,
          nombreArchivoWord: r.nombreArchivoWord,
          nombreSubproceso: r.nombreSubproceso,
          fechaProgramada: r.fechaProgramada,
          observacionesAsignacion: r.observacionesAsignacion,
          telefonoEvaluado: r.telefonoEvaluado,
          ciudadEvaluado: r.ciudadEvaluado,
          idServicio: r.idServicio,
          nombreProceso: r.nombreProceso,
          cargo: r.cargo,
          fechaAsignacion: r.fechaAsignacion,
          fechaCompletado: r.fechaCompletado,
          duracionEstimadaMinutos: r.duracionEstimadaMinutos,
          fechaEntregaEstimada: r.fechaEntregaEstimada,
          avisoCalculo: r.avisoCalculo ?? null,
          avisosFormulario: r.avisosFormulario ?? [],
          criticidadDeSolicitud: r.criticidadDeSolicitud ?? false,
        })
        const seccionesEditables = (r.secciones ?? []).map(s => ({
          idSeccion: s.idSeccion,
          nombre: s.nombre,
          tipo: s.tipo,
          orden: s.orden,
          calculada: !!s.calculada,
          opcional: !!s.opcional,
          grupo: s.grupo ?? null,
          columnas: s.columnas,
          valores: { ...s.valores },
          justificaciones: { ...(s.justificaciones ?? {}) },
          filas: (s.filas ?? []).map(f => ({ localId: idLocal(), idFila: f.idFila, etiqueta: f.etiqueta, valores: { ...f.valores }, justificaciones: { ...(f.justificaciones ?? {}) } })),
        }))
        // Columnas de VALIDACIÓN de una tabla fija (referencias, educación...) — el admin las
        // agregó en la plantilla para que analista/poligrafista/visitador opinen por fila, aparte
        // de las columnas de solo lectura de la hoja de vida. Se modelan como una sección TABLA
        // más (mismo editarValorFila/guardar() de siempre), marcadas con origenFijoClave para que
        // el render las empareje con su tabla fija en vez de mostrarlas como bloque aparte — cada
        // fila trae idOrigenFijo = el id real de la fila del candidato a la que corresponde.
        const seccionesValidacionFija = (r.tablasFijas ?? [])
          .filter(t => (t.columnasValidacion ?? []).length > 0)
          .map(t => ({
            idSeccion: t.idSeccion,
            nombre: t.nombre,
            tipo: 'TABLA',
            orden: t.orden,
            columnas: t.columnasValidacion,
            valores: {},
            origenFijoClave: t.clave,
            filas: (t.filas ?? []).map(filaFija => {
              const val = t.validacionPorId?.[filaFija.id] ?? { idFila: null, valores: {}, justificaciones: {} }
              return { localId: idLocal(), idFila: val.idFila, idOrigenFijo: Number(filaFija.id), etiqueta: '', valores: { ...val.valores }, justificaciones: { ...(val.justificaciones ?? {}) } }
            }),
          }))
        setSecciones([...seccionesEditables, ...seccionesValidacionFija])
        setOmitidas(new Set([...(r.secciones ?? []), ...(r.tablasFijas ?? [])].filter(s => s.omitida).map(s => s.idSeccion)))
        // Las tablas de hoja de vida (referencias, educación...) se editan directo contra el
        // registro real del candidato — se cargan aparte, con los id reales de cada fila, para
        // que TablaHojaVidaEditable pueda guardarlas.
        if (r.idCandidato && (r.tablasFijas ?? []).length > 0) {
          formulariosService.obtenerHojaVidaCandidato(r.idCandidato).then(setHojaVidaCandidato).catch(() => {})
        }
      })
      .catch(() => setError('No se pudo cargar el formulario.'))
      .finally(() => setCargando(false))
  }, [idServicioSubproceso, location.key])

  // Mantiene sincronizadas las filas de VALIDACIÓN con las filas reales de la hoja de vida —
  // hojaVidaCandidato se actualiza cuando se agrega/edita una fila en TablaHojaVidaEditable
  // (referencias, educación...), pero eso NO recarga el formulario completo, así que sin esto una
  // fila nueva quedaba sin su tarjeta de validación (la sección seguía con la foto inicial de
  // cuando se abrió la página). Corre en cada cambio de hojaVidaCandidato: conserva los valores ya
  // guardados de las filas que ya existían (empatadas por idOrigenFijo) y crea una fila de
  // validación en blanco para cualquier fila nueva.
  useEffect(() => {
    if (!hojaVidaCandidato) return
    setSecciones(prev => prev.map(s => {
      if (!s.origenFijoClave) return s
      const tabla = hojaVidaCandidato.tablas?.find(t => t.clave === s.origenFijoClave)
      if (!tabla) return s
      const filasExistentesPorOrigen = new Map(s.filas.map(f => [String(f.idOrigenFijo), f]))
      const filasSincronizadas = (tabla.filas ?? []).map(row => filasExistentesPorOrigen.get(String(row.id))
        ?? { localId: idLocal(), idFila: null, idOrigenFijo: Number(row.id), etiqueta: '', valores: {}, justificaciones: {} })
      return { ...s, filas: filasSincronizadas }
    }))
  }, [hojaVidaCandidato])

  // Analista/poligrafista/visitador pueden agregar y editar filas, pero no borrar ninguna —
  // evita que se pierda por accidente un dato ya guardado (más grave todavía en las tablas de
  // hoja de vida real del candidato, que comparte con otros servicios). Gestor/programador sí.
  const puedeEliminar = !['analista', 'poligrafista', 'visitador'].includes(prefijo)

  const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''

  const progresoSeccion = (s) => {
    if (s.tipo !== 'CAMPOS') return null
    const manuales = s.columnas.filter(c => !c.automatico)
    const total = manuales.length
    const llenos = manuales.filter(c => tieneValor(s.valores[c.idColumna])).length
    return { llenos, total }
  }


  // Secciones editables + tablas de hoja de vida del candidato (referencias, educación...)
  // intercaladas en el mismo orden en que las armó el administrador en el constructor.
  const bloques = [
    // Las secciones de validación (origenFijoClave) no forman su propio bloque — se renderizan
    // emparejadas con su tabla fija (bloque 'fija'), ver más abajo.
    ...secciones.filter(s => !s.origenFijoClave).map(s => ({ tipo: 'editable', orden: s.orden ?? 0, key: `s-${s.idSeccion}`, data: s })),
    ...(meta?.tablasFijas ?? []).map(t => ({ tipo: 'fija', orden: t.orden ?? 0, key: `t-${t.idSeccion}`, data: t })),
  ].sort((a, b) => a.orden - b.orden)

  // Grupo con el que el administrador clasificó la sección (null = sin grupo).
  const grupoDe = (bloque) => bloque?.data?.grupo ?? null

  const nombreDe = (bloque) => bloque?.tipo === 'fija' ? bloque.data.nombre : bloque?.data.nombre ?? ''

  // Estado de cada paso para el índice lateral: 'completo' | 'parcial' | 'vacio', con un texto
  // corto de detalle. CAMPOS = cuántos campos tienen valor; TABLA/hoja de vida = cuántos
  // registros tiene. Solo es una señal visual, no bloquea la navegación.
  const estadoBloque = (bloque) => {
    // Quitada por quien diligencia: no es trabajo pendiente ni cuenta en el avance.
    if (omitidas.has(bloque.data.idSeccion)) return { estado: 'completo', fraccion: null, detalle: 'Quitada' }
    if (bloque.tipo === 'fija') {
      const tabla = (hojaVidaCandidato?.tablas ?? []).find(t => t.clave === bloque.data.clave)
      const n = (tabla?.filas ?? []).length
      return { estado: n > 0 ? 'completo' : 'vacio', fraccion: n > 0 ? 1 : 0, detalle: n > 0 ? `${n} registro${n === 1 ? '' : 's'}` : 'Sin registros' }
    }
    const s = bloque.data
    if (s.tipo === 'CAMPOS') {
      const p = progresoSeccion(s)
      if (p.total === 0) return { estado: 'completo', fraccion: null, detalle: 'Sin campos' }
      return {
        estado: p.llenos === p.total ? 'completo' : p.llenos > 0 ? 'parcial' : 'vacio',
        fraccion: p.llenos / p.total,
        detalle: `${p.llenos}/${p.total} campos`,
      }
    }
    // Tabla que arma el sistema (ej. hallazgos): no es trabajo pendiente de quien diligencia.
    if (s.calculada) return { estado: 'completo', fraccion: null, detalle: 'La arma el sistema' }
    const n = s.filas.length
    return { estado: n > 0 ? 'completo' : 'vacio', fraccion: n > 0 ? 1 : 0, detalle: n > 0 ? `${n} fila${n === 1 ? '' : 's'}` : 'Sin filas' }
  }

  // Avance del formulario por SECCIONES — cada sección pesa igual: una de campos aporta la
  // fracción de campos llenos, una tabla cuenta completa con al menos un registro. Antes solo se
  // contaban campos, así que una plantilla hecha solo de tablas no mostraba ningún porcentaje.
  // Mismo cálculo que el backend (FormularioService.progreso).
  const avanceSecciones = bloques.map(estadoBloque).filter(e => e.fraccion !== null)
  const porcentajeTotal = avanceSecciones.length > 0
    ? Math.round(avanceSecciones.reduce((acc, e) => acc + e.fraccion, 0) / avanceSecciones.length * 100)
    : null
  const seccionesCompletas = avanceSecciones.filter(e => e.fraccion >= 1).length

  // Orden de pasos: 0 = Novedades, 1 = Datos del candidato, 2 = Soportes del candidato,
  // 3..N+2 = secciones de la plantilla (bloque idx -> paso idx + PRIMER_BLOQUE) y, solo para quien
  // diligencia, un último paso "Revisión y envío" donde se revisa qué falta y se completa.
  const esEjecutor = ['analista', 'poligrafista', 'visitador'].includes(prefijo)
  // NS-07: la criticidad del cargo la eligió el cliente al solicitar; solo el gestor (o el admin) la cambia.
  const criticidadBloqueada = (c) => c.calificacion?.uso === 'CRITICIDAD' && !!meta?.criticidadDeSolicitud && !['gestor', 'admin'].includes(prefijo)
  const PASO_NOVEDADES = 0
  const PASO_CANDIDATO = 1
  const PASO_SOPORTES = 2
  const PRIMER_BLOQUE = 3
  const enCandidato = paso === PASO_CANDIDATO
  const enSoportes = paso === PASO_SOPORTES
  const tienePasoEnvio = esEjecutor && bloques.length > 0
  // "Soportes del informe": qué archivos adjuntos saldrán en el informe (todos los roles, después de las secciones).
  const tienePasoAnexos = bloques.length > 0
  const pasoAnexos = tienePasoAnexos ? bloques.length + PRIMER_BLOQUE : null
  const enAnexos = pasoAnexos !== null && paso === pasoAnexos
  const pasoEnvio = tienePasoEnvio ? bloques.length + PRIMER_BLOQUE + 1 : null
  const ultimoPaso = tienePasoEnvio ? pasoEnvio : tienePasoAnexos ? pasoAnexos : PRIMER_BLOQUE - 1
  const enNovedades = paso === PASO_NOVEDADES
  const enEnvio = pasoEnvio !== null && paso === pasoEnvio
  const pendientes = bloques.map((b, idx) => ({ b, idx, ...estadoBloque(b) })).filter(x => x.estado !== 'completo')

  // Respuestas críticas sin explicación — bloquean el envío (el backend también lo valida).
  const pasoDeSeccion = (sec) => {
    const idx = bloques.findIndex(b => sec.origenFijoClave
      ? (b.tipo === 'fija' && b.data.clave === sec.origenFijoClave)
      : (b.tipo === 'editable' && b.data.idSeccion === sec.idSeccion))
    return idx >= 0 ? idx + PRIMER_BLOQUE : null
  }
  const criticasSinExplicar = secciones.filter(sec => !omitidas.has(sec.idSeccion)).flatMap(sec => {
    const nombreSeccion = sec.nombre || 'Sección'
    const faltan = []
    if (sec.tipo === 'CAMPOS') {
      sec.columnas.forEach(c => {
        if (esCritica(c, sec.valores[c.idColumna]) && !(sec.justificaciones?.[c.idColumna] ?? '').trim()) {
          faltan.push({ clave: `${sec.idSeccion}-${c.idColumna}`, texto: `${nombreSeccion} › ${c.nombre || c.clave}`, paso: pasoDeSeccion(sec) })
        }
      })
    } else {
      sec.filas.forEach((f, i) => sec.columnas.forEach(c => {
        if (esCritica(c, f.valores[c.idColumna]) && !(f.justificaciones?.[c.idColumna] ?? '').trim()) {
          faltan.push({ clave: `${sec.idSeccion}-${f.localId}-${c.idColumna}`, texto: `${nombreSeccion} › ${c.nombre || c.clave} (fila ${i + 1})`, paso: pasoDeSeccion(sec) })
        }
      }))
    }
    return faltan
  })

  // Campos obligatorios vacíos — el servidor tampoco deja completar con alguno (camposObligatoriosVacios).
  const obligatoriosVacios = secciones.filter(sec => !omitidas.has(sec.idSeccion)).flatMap(sec => {
    const nombreSeccion = sec.nombre || 'Sección'
    const obligatorias = sec.columnas.filter(c => c.obligatorio && !c.automatico)
    if (sec.tipo === 'CAMPOS') {
      return obligatorias.filter(c => !tieneValor(sec.valores[c.idColumna]))
        .map(c => ({ clave: `o-${sec.idSeccion}-${c.idColumna}`, texto: `${nombreSeccion} › ${c.nombre || c.clave}`, paso: pasoDeSeccion(sec) }))
    }
    return obligatorias.filter(c => sec.filas.some(f => !tieneValor(f.valores[c.idColumna])))
      .map(c => ({ clave: `o-${sec.idSeccion}-${c.idColumna}`, texto: `${nombreSeccion} › ${c.nombre || c.clave}`, paso: pasoDeSeccion(sec) }))
  })

  const irAPaso = (idx) => {
    setPaso(idx)
    requestAnimationFrame(() => {
      const el = inicioContenido.current
      if (!el) return
      // Solo si el inicio quedó fuera de la vista (arriba): así no salta si ya se ve.
      if (el.getBoundingClientRect().top < 0 || el.getBoundingClientRect().top > window.innerHeight * 0.6) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    })
  }

  const quitarSeccion = (idSeccion) => {
    setOmitidas(prev => new Set([...prev, idSeccion]))
    setQuitando(null)
    marcarSucio()
  }

  const volverAAgregar = (idSeccion) => {
    setOmitidas(prev => { const n = new Set(prev); n.delete(idSeccion); return n })
    marcarSucio()
  }

  const editarValorCampo = (idSeccion, idColumna, valor) => {
    setSecciones(prev => prev.map(s => s.idSeccion === idSeccion
      ? { ...s, valores: { ...s.valores, [idColumna]: valor } } : s))
    marcarSucio()
  }

  const editarJustificacionCampo = (idSeccion, idColumna, texto) => {
    setSecciones(prev => prev.map(s => s.idSeccion === idSeccion
      ? { ...s, justificaciones: { ...(s.justificaciones ?? {}), [idColumna]: texto } } : s))
    marcarSucio()
  }

  const editarJustificacionFila = (idSeccion, filaLocalId, idColumna, texto) => {
    setSecciones(prev => prev.map(s => s.idSeccion === idSeccion
      ? { ...s, filas: s.filas.map(f => f.localId === filaLocalId ? { ...f, justificaciones: { ...(f.justificaciones ?? {}), [idColumna]: texto } } : f) }
      : s))
    marcarSucio()
  }

  const editarValorFila = (idSeccion, filaLocalId, idColumna, valor) => {
    setSecciones(prev => prev.map(s => s.idSeccion === idSeccion
      ? { ...s, filas: s.filas.map(f => f.localId === filaLocalId ? { ...f, valores: { ...f.valores, [idColumna]: valor } } : f) }
      : s))
    marcarSucio()
  }

  const editarEtiquetaFila = (idSeccion, filaLocalId, etiqueta) => {
    setSecciones(prev => prev.map(s => s.idSeccion === idSeccion
      ? { ...s, filas: s.filas.map(f => f.localId === filaLocalId ? { ...f, etiqueta } : f) } : s))
    marcarSucio()
  }

  const agregarFila = (idSeccion) => {
    marcarSucio()
    setSecciones(prev => prev.map(s => s.idSeccion === idSeccion
      ? { ...s, filas: [...s.filas, { localId: idLocal(), idFila: null, etiqueta: '',
          valores: Object.fromEntries(s.columnas.filter(c => c.valorDefecto != null).map(c => [c.idColumna, c.valorDefecto])),
          justificaciones: {} }] } : s))
  }

  const quitarFila = (idSeccion, filaLocalId) => {
    setSecciones(prev => prev.map(s => s.idSeccion === idSeccion
      ? { ...s, filas: s.filas.filter(f => f.localId !== filaLocalId) } : s))
    marcarSucio()
  }

  const descargar = async () => {
    setDescargando(true)
    try {
      const blob = await formulariosService.descargarDocumento(idServicioSubproceso)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `formulario-${idServicioSubproceso}.docx`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      mostrarToast(err.response?.data?.mensaje ?? 'No se pudo generar el documento. Guarda el formulario primero.', 'error')
    } finally {
      setDescargando(false)
    }
  }

  // Mismo documento que descargar() (.docx) — el backend convierte el MISMO .docx ya generado a
  // partir de la plantilla Word a PDF (ver DocumentoPdfConverter), así que requiere lo mismo:
  // que el formulario esté guardado y que admin/catálogo → Plantilla tenga un .docx con
  // marcadores subido para este tipo de proceso.
  // 202 + polling: el PDF se genera en la cola del backend (o sale de caché al instante si el
  // servicio ya está publicado). Se corta el polling si el usuario sale del formulario.
  const descargarPdf = async () => {
    setGenerandoPdf(true)
    abortoPdfRef.current?.abort()
    const aborto = new AbortController()
    abortoPdfRef.current = aborto
    try {
      const inicial = await formulariosService.solicitarDocumentoPdf(idServicioSubproceso)
      const listo = await esperarTrabajo(inicial, { signal: aborto.signal })
      const r = await trabajoService.descargarResultado(listo.id)
      guardarBlob(r, listo.nombreArchivo ?? `formulario-${idServicioSubproceso}.pdf`)
    } catch (err) {
      if (err.cancelado) return
      mostrarToast(mensajeDeError(err, 'No se pudo generar el PDF. Guarda el formulario primero.'), 'error')
    } finally {
      if (!aborto.signal.aborted) setGenerandoPdf(false)
    }
  }

  // Después de guardar, el servidor devuelve lo calculado con lo recién guardado (puntaje,
  // fórmula, semáforo, notas, numeración, tabla de hallazgos — docs/antecedentes, Prompt 4). Solo
  // se reemplazan las columnas/tablas calculadas: lo que la persona siga escribiendo no se toca.
  const aplicarCalculados = (r) => {
    const porId = new Map((r.secciones ?? []).map(s => [s.idSeccion, s]))
    setSecciones(prev => prev.map(s => {
      const nueva = porId.get(s.idSeccion)
      if (!nueva || s.origenFijoClave) return s
      if (s.calculada) {
        return { ...s, filas: (nueva.filas ?? []).map(f => ({ localId: idLocal(), idFila: null, etiqueta: f.etiqueta, valores: { ...f.valores }, justificaciones: {} })) }
      }
      const calculadas = s.columnas.filter(c => c.calculado).map(c => c.idColumna)
      if (calculadas.length === 0) return s
      if (s.tipo === 'CAMPOS') {
        const valores = { ...s.valores }
        calculadas.forEach(id => { valores[id] = nueva.valores?.[id] ?? '' })
        return { ...s, valores }
      }
      return {
        ...s,
        filas: s.filas.map((f, i) => {
          const filaNueva = nueva.filas?.[i]
          if (!filaNueva) return f
          const valores = { ...f.valores }
          calculadas.forEach(id => { valores[id] = filaNueva.valores?.[id] ?? '' })
          return { ...f, valores }
        }),
      }
    }))
    setMeta(prev => (prev ? { ...prev, avisoCalculo: r.avisoCalculo ?? null, avisosFormulario: r.avisosFormulario ?? [], criticidadDeSolicitud: r.criticidadDeSolicitud ?? false } : prev))
  }

  // silencioso = true para el autoguardado (ver más abajo): guarda sin toast de éxito, para no
  // interrumpir cada pocos segundos — un error sí se muestra siempre, autoguardado o no.
  const guardar = async ({ silencioso = false } = {}) => {
    const version = cambiosRef.current
    setGuardando(true)
    setError(null)
    try {
      const payload = secciones.filter(s => !omitidas.has(s.idSeccion)).map(s => ({
        idSeccion: s.idSeccion,
        valores: s.tipo === 'CAMPOS' ? s.valores : null,
        justificaciones: s.tipo === 'CAMPOS' ? (s.justificaciones ?? {}) : null,
        filas: s.tipo === 'TABLA' ? s.filas.map(f => ({ etiqueta: f.etiqueta, valores: f.valores, idOrigenFijo: f.idOrigenFijo ?? null, justificaciones: f.justificaciones ?? {} })) : null,
      }))
      const r = await formulariosService.guardarFormulario(idServicioSubproceso, payload, [...omitidas])
      // La primera vez que se guarda, el backend recién crea el Formulario (antes idFormulario
      // era null) — sin esto, "Descargar PDF"/"Descargar (.docx)" seguían mostrando "Guarda el
      // formulario primero" después de guardar, hasta recargar la página a mano.
      setMeta(prev => (prev ? { ...prev, idFormulario: r.idFormulario } : prev))
      aplicarCalculados(r)
      // Si el usuario editó algo MIENTRAS este guardado estaba en camino, ese cambio no viajó en
      // este payload (se armó antes) — se deja "sucio" para que el autoguardado lo recoja en la
      // siguiente vuelta, en vez de marcarlo como guardado sin estarlo.
      if (cambiosRef.current === version) setSucio(false)
      setAutoguardadoFallido(false)
      cargarRiesgo()
      if (!silencioso) mostrarToast('Formulario guardado correctamente')
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo guardar el formulario.')
      if (silencioso) setAutoguardadoFallido(true)
    } finally {
      setGuardando(false)
    }
  }

  // Antes esto era un botón de check ciego en la tabla de Mis Asignaciones — se movió aquí para
  // que solo se pueda marcar como completado revisando de verdad lo que se diligenció, no de
  // pasada desde una lista. Si hay cambios sin guardar, se guardan primero (mismo payload que
  // guardar()) para no completar con datos viejos.
  const completarSubproceso = async () => {
    setCompletando(true)
    setError(null)
    try {
      if (sucio) await guardar({ silencioso: true })
      await asignacionService.completar(idServicioSubproceso)
      mostrarToast('Formulario marcado como completado')
      setTimeout(() => navigate(volver.ruta), 900)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo completar el subproceso.')
    } finally {
      setCompletando(false)
    }
  }

  // Antes eran acciones ciegas en la tabla de Agenda de gestor/programador — se movieron aquí por
  // el mismo motivo que "Marcar como completado": aprobar o devolver revisando de verdad lo que
  // se diligenció, no de pasada desde una lista.
  const aprobarSubproceso = async () => {
    setAprobando(true)
    try {
      await asignacionService.aprobar(idServicioSubproceso)
      mostrarToast('Subproceso aprobado y publicado')
      setTimeout(() => navigate(volver.ruta), 900)
    } catch (err) {
      mostrarToast(err.response?.data?.mensaje ?? 'No se pudo aprobar el subproceso.', 'error')
    } finally {
      setAprobando(false)
    }
  }

  const confirmarDevolver = async () => {
    const falta = motivoIncompleto(motivoDevolver)
    if (falta) {
      mostrarToast(falta, 'error')
      return
    }
    setEnviandoDevolver(true)
    try {
      await asignacionService.devolver(idServicioSubproceso, { idMotivo: motivoDevolver.idMotivo, observacion: motivoDevolver.texto.trim() || null })
      mostrarToast('Subproceso devuelto — ya le llegó la notificación.')
      setDevolviendo(false)
      setMotivoDevolver(MOTIVO_VACIO)
      setTimeout(() => navigate(volver.ruta), 900)
    } catch (err) {
      mostrarToast(err.response?.data?.mensaje ?? 'No se pudo devolver el subproceso.', 'error')
    } finally {
      setEnviandoDevolver(false)
    }
  }

  // Guardado automático — solo para quien diligencia el formulario (analista/poligrafista/
  // visitador): unos segundos después de cualquier cambio se guarda solo, sin que tengan que
  // acordarse de darle a "Guardar". Gestor/programador quedan fuera a propósito (ellos sí pueden
  // borrar filas manualmente y prefieren un guardado explícito, no uno que dispare solo).
  const autoguardadoActivo = ['analista', 'poligrafista', 'visitador'].includes(prefijo) && !meta?.soloLectura
  useEffect(() => {
    if (!autoguardadoActivo || !sucio || guardando || autoguardadoFallido) return
    const temporizador = setTimeout(() => { guardar({ silencioso: true }) }, 2500)
    return () => clearTimeout(temporizador)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secciones, sucio, guardando, autoguardadoActivo, autoguardadoFallido])

  // Avisa antes de cerrar/recargar la pestaña si hay cambios sin guardar — cubre tanto a gestor/
  // programador (sin autoguardado) como la ventana de unos segundos antes de que el autoguardado
  // de analista/poligrafista/visitador alcance a dispararse.
  useEffect(() => {
    if (!sucio) return
    const avisar = (e) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', avisar)
    return () => window.removeEventListener('beforeunload', avisar)
  }, [sucio])

  useEffect(() => {
    if (cargando || !irANovedadesRef.current) return
    irANovedadesRef.current = false
    setPaso(PASO_NOVEDADES)
  }, [cargando, PASO_NOVEDADES])

  if (cargando) {
    return (
      <div className="flex justify-center py-16">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  // Datos del evaluado para la grilla del encabezado — el nombre ya va como título; siempre
  // cédula, teléfono y ciudad (con "—" si no los tiene registrados).
  const datosEncabezado = [
    { etiqueta: 'Cédula', valor: meta?.cedulaEvaluado },
    { etiqueta: 'Teléfono', valor: meta?.telefonoEvaluado },
    { etiqueta: 'Ciudad', valor: meta?.ciudadEvaluado },
  ]
  const estadoInfo = ESTADO_SUBPROCESO[meta?.estado] ?? null

  return (
    <div className="max-w-6xl mx-auto">
      <div className="mb-3 print:hidden">
        <Link to={volver.ruta} className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          {volver.etiqueta}
        </Link>
      </div>

      <header className="mb-5 bg-white border border-primary-100 rounded-2xl shadow-sm shadow-primary-100/40 overflow-hidden print:border-0 print:shadow-none">
        {/* Franja: qué servicio se está realizando */}
        <div className="px-5 sm:px-6 py-3 bg-primary-50/70 border-b border-primary-100 flex flex-wrap items-center gap-x-3 gap-y-2 print:bg-transparent print:px-0">
          {/* Servicio = el proceso que compró el cliente (ej. "Estudio de seguridad");
              subservicio = el subproceso puntual que se diligencia aquí (ej. "Poligrafía"). */}
          <div className="min-w-0 flex-1 flex flex-wrap items-stretch gap-x-6 gap-y-2">
            <div className="min-w-0">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-primary-500">Servicio</p>
              <p className="text-sm font-semibold text-primary-900 truncate" title={meta?.nombreProceso ?? undefined}>
                {meta?.nombreProceso || '—'}
              </p>
            </div>
            <div className="min-w-0 sm:pl-6 sm:border-l sm:border-primary-100">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-primary-500">Subservicio</p>
              <p className="text-sm font-semibold text-primary-900 truncate" title={meta?.nombreSubproceso ?? undefined}>
                {meta?.nombreSubproceso || '—'}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-1.5 print:hidden">
            {meta?.idServicio && (
              <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-white border border-primary-100 text-primary-700">
                Servicio #{meta.idServicio}
              </span>
            )}
            {meta?.cargo && (
              <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-white border border-primary-100 text-primary-700 max-w-[220px] truncate" title={meta.cargo}>
                Cargo: {meta.cargo}
              </span>
            )}
            {estadoInfo && (
              <span className="inline-flex items-center gap-1.5 text-[11px] font-medium px-2 py-0.5 rounded-full bg-white border border-primary-100 text-gray-700">
                <span className={`w-1.5 h-1.5 rounded-full ${estadoInfo.punto}`} />
                {estadoInfo.etiqueta}
              </span>
            )}
            {meta?.soloLectura && (
              <span className="inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-full bg-white border border-primary-100 text-gray-500">
                <IconoCandado /> Solo lectura
              </span>
            )}
          </div>
        </div>

        {/* Evaluado + avance */}
        <div className="px-5 sm:px-6 pt-4 pb-4 flex flex-col md:flex-row md:items-center gap-5 print:px-0">
          <div className="flex items-start gap-4 min-w-0 flex-1">
            <div className="flex-shrink-0 w-12 h-12 rounded-xl bg-primary-600 text-white flex items-center justify-center shadow-sm shadow-primary-200 print:hidden">
              <IconoDocumento />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400">Evaluado</p>
              <h2 className="text-xl font-bold text-gray-900 truncate">{meta?.nombreEvaluado || '—'}</h2>
              <dl className="mt-2.5 grid grid-cols-1 sm:grid-cols-3 gap-x-6 gap-y-2">
                {datosEncabezado.map(d => (
                  <div key={d.etiqueta} className="min-w-0">
                    <dt className="text-[11px] text-gray-400">{d.etiqueta}</dt>
                    <dd className={`text-sm truncate ${d.valor ? 'font-medium text-gray-700' : 'text-gray-300'}`} title={d.valor || undefined}>
                      {d.valor || '—'}
                    </dd>
                  </div>
                ))}
              </dl>
              <TiempoEjecucion meta={meta} />
            </div>
          </div>

          {porcentajeTotal !== null && (
            <div className="flex-shrink-0 flex items-center gap-3 md:flex-col md:gap-1.5 md:pl-5 md:border-l md:border-primary-100/70 print:hidden">
              <AnilloAvance porcentaje={porcentajeTotal} />
              <p className="text-[11px] text-gray-500 md:text-center leading-tight">
                {seccionesCompletas} de {avanceSecciones.length}<br className="hidden md:block" /> secciones completas
              </p>
            </div>
          )}
        </div>

        <FranjaRiesgo riesgo={riesgo} tablas={secciones
          .filter(s => s.tipo === 'TABLA')
          .map(s => ({ id: s.idSeccion, nombre: s.nombre, ...resumenTabla(s.columnas, s.filas) }))
          .filter(t => t.filas != null)} />

        {/* Plantilla y documento: con qué se llena y con qué se genera este subproceso */}
        {(meta?.versionPlantilla || bloques.length > 0) && (
          <div className="px-5 sm:px-6 py-3 border-t border-primary-100/70 grid grid-cols-1 md:grid-cols-2 gap-3 print:hidden">
            <InfoPlantilla meta={meta} />
            <InfoDocumentoWord meta={meta} />
          </div>
        )}

        {/* Barra de acciones */}
        <div className="px-5 sm:px-6 py-3 bg-primary-50/40 border-t border-primary-100/70 flex flex-wrap items-center gap-2 print:hidden">
          {!meta?.soloLectura && bloques.length > 0 ? (
            <span className={`inline-flex items-center gap-1.5 text-xs font-medium mr-auto ${sucio ? 'text-gray-700' : 'text-gray-500'}`}>
              <span className={`w-2 h-2 rounded-full ${guardando ? 'bg-primary-400 animate-pulse' : sucio ? 'bg-amber-500' : 'bg-green-500'}`} />
              {guardando ? (autoguardadoActivo ? 'Guardando automáticamente…' : 'Guardando…')
                : sucio ? 'Cambios sin guardar' : 'Todo guardado'}
            </span>
          ) : <span className="mr-auto" />}
          {bloques.length > 0 && (
            <button type="button" onClick={descargarPdf} disabled={generandoPdf || !meta?.nombreArchivoWord}
              title={!meta?.nombreArchivoWord ? 'Este subproceso no tiene documento Word configurado'
                : !meta?.soloLectura && !meta?.idFormulario ? 'Guarda el formulario primero' : undefined}
              className="inline-flex items-center gap-1.5 border border-primary-100 bg-white text-gray-700 hover:bg-primary-50 disabled:opacity-50 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors">
              <IconoDescargar /> {generandoPdf ? 'Generando…' : 'PDF'}
            </button>
          )}
          {/* Word (.docx) es el archivo "de trabajo" — solo gestor/admin lo necesitan para
              editar el documento fuera de la plataforma; el resto se queda con el PDF. */}
          {bloques.length > 0 && ['gestor', 'admin'].includes(prefijo) && (
            <button type="button" onClick={descargar} disabled={descargando || !meta?.nombreArchivoWord}
              title={!meta?.soloLectura && !meta?.idFormulario ? 'Guarda el formulario primero' : undefined}
              className="inline-flex items-center gap-1.5 border border-primary-100 bg-white text-gray-700 hover:bg-primary-50 disabled:opacity-50 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors">
              <IconoDescargar /> {descargando ? 'Generando…' : 'Word'}
            </button>
          )}
          {!meta?.soloLectura && bloques.length > 0 && (
            <button type="button" onClick={() => guardar()} disabled={guardando}
              className="inline-flex items-center gap-1.5 disabled:opacity-60 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors bg-primary-600 hover:bg-primary-700 text-white shadow-sm">
              <IconoGuardar /> {guardando ? 'Guardando…' : 'Guardar'}
            </button>
          )}
          {/* Aprobar o devolver revisando de verdad lo diligenciado, aquí mismo. Solo mientras
              sigue FINALIZADO: una vez PUBLICADO ya no hay nada más que hacer. */}
          {['gestor', 'programador'].includes(prefijo) && meta?.estado === 'FINALIZADO' && (
            <>
              <button type="button" onClick={() => { setDevolviendo(true); setMotivoDevolver(MOTIVO_VACIO) }}
                className="inline-flex items-center gap-1.5 border border-primary-100 bg-white text-gray-700 hover:bg-primary-50 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors">
                <IconoDevolver /> Devolver
              </button>
              <button type="button" onClick={aprobarSubproceso} disabled={aprobando}
                className="inline-flex items-center gap-1.5 disabled:opacity-60 text-white text-sm font-medium px-3.5 py-2 rounded-lg transition-colors shadow-sm bg-primary-600 hover:bg-primary-700">
                <IconoAprobar /> {aprobando ? 'Aprobando…' : 'Aprobar'}
              </button>
            </>
          )}
        </div>
      </header>

      {/* Lo último que dejó quien coordina: indicaciones al asignar, o el motivo si lo devolvió
          para corrección — antes solo llegaba en la notificación y se perdía de vista. */}
      {meta?.observacionesAsignacion && meta?.estado === 'EN_EJECUCION' && (
        <div className="mb-5 bg-amber-50 border border-amber-200 rounded-2xl px-4 py-3 print:hidden">
          <p className="text-xs font-semibold text-amber-800 mb-0.5">Observación de quien coordina este servicio</p>
          <p className="text-sm text-amber-900 whitespace-pre-line">{meta.observacionesAsignacion}</p>
        </div>
      )}

      {meta?.avisoCalculo && !meta?.soloLectura && (
        <div className="mb-5 bg-amber-50 border border-amber-200 rounded-2xl px-4 py-3 print:hidden">
          <p className="text-xs font-semibold text-amber-800 mb-0.5">Todavía no se puede calcular el puntaje</p>
          <p className="text-sm text-amber-900">{meta.avisoCalculo} El puntaje, el semáforo y la tabla de hallazgos aparecen cuando lo completes.</p>
        </div>
      )}

      {(meta?.avisosFormulario ?? []).length > 0 && !meta?.soloLectura && (
        <div className="mb-5 bg-amber-50 border border-amber-200 rounded-2xl px-4 py-3 print:hidden">
          <p className="text-xs font-semibold text-amber-800 mb-1">
            Revisa {meta.avisosFormulario.length === 1 ? 'esto' : `estos ${meta.avisosFormulario.length} puntos`} antes de enviar (no impide guardar)
          </p>
          <ul className="space-y-1">
            {meta.avisosFormulario.map((a, i) => (
              <li key={i} className="text-sm text-amber-900">
                {a.texto} {a.lugar && <span className="text-xs text-amber-700">— {a.lugar}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      {meta?.soloLectura && (
        <div className="mb-5 bg-gray-100 border border-gray-200 rounded-2xl px-4 py-3 print:hidden">
          <p className="text-sm text-gray-600">
            Este servicio ya está completado — el formulario quedó en solo lectura, no se pueden hacer más cambios.
          </p>
        </div>
      )}

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
      )}

      {bloques.length === 0 && (
        <div className="mb-5 bg-amber-50 border border-amber-200 rounded-2xl px-4 py-3 print:hidden">
          <p className="text-sm text-amber-700">
            Este subproceso todavía no tiene una plantilla configurada — pide a un administrador que la arme desde
            admin/catálogo → Plantilla. Mientras tanto puedes reportar novedades.
          </p>
        </div>
      )}

      <div className="lg:grid lg:grid-cols-[260px_minmax(0,1fr)] lg:gap-6 lg:items-start">
        {/* Índice de pasos — en pantallas pequeñas pasa a una fila horizontal desplazable. */}
        <aside className="mb-4 lg:mb-0 lg:sticky lg:top-4 print:hidden">
          <nav className="bg-white border border-gray-200 rounded-2xl shadow-sm p-3 lg:max-h-[calc(100vh-2rem)] lg:overflow-y-auto overscroll-contain">
            <ol className="flex lg:flex-col gap-1 overflow-x-auto lg:overflow-visible -mx-1 px-1 pb-1 lg:pb-0">
              {/* 1) Novedades — siempre primero */}
              <li className="flex-shrink-0 lg:flex-shrink">
                <button type="button" onClick={() => irAPaso(PASO_NOVEDADES)}
                  className={`w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 transition-colors ${
                    enNovedades ? 'bg-amber-50 ring-1 ring-amber-200' : 'hover:bg-gray-50'}`}>
                  <span className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center border-2 ${
                    enNovedades ? 'bg-amber-500 border-amber-500 text-white' : 'bg-white border-amber-300 text-amber-500'}`}>
                    <IconoBandera />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className={`block text-xs font-medium ${enNovedades ? 'text-amber-800' : 'text-gray-700'}`}>Novedades</span>
                    <span className="block text-[10px] text-gray-400">
                      {novedadesAbiertas > 0 ? `${novedadesAbiertas} novedad${novedadesAbiertas === 1 ? '' : 'es'} abierta${novedadesAbiertas === 1 ? '' : 's'}` : 'Reportar si pasó algo'}
                    </span>
                  </span>
                  {novedadesAbiertas > 0 && (
                    <span className="flex-shrink-0 min-w-[18px] h-[18px] px-1 rounded-full bg-amber-500 text-white text-[10px] font-bold flex items-center justify-center">
                      {novedadesAbiertas}
                    </span>
                  )}
                </button>
              </li>

              {/* 2) Datos del candidato — paso propio */}
              <li className="flex-shrink-0 lg:flex-shrink">
                <button type="button" onClick={() => irAPaso(PASO_CANDIDATO)}
                  className={`w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 transition-colors ${
                    enCandidato ? 'bg-primary-50 ring-1 ring-primary-200' : 'hover:bg-gray-50'}`}>
                  <span className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center border-2 ${
                    enCandidato ? 'bg-primary-600 border-primary-600 text-white' : 'bg-white border-primary-200 text-primary-500'}`}>
                    <IconoPersona />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className={`block text-xs font-medium ${enCandidato ? 'text-primary-700' : 'text-gray-700'}`}>Candidato</span>
                    <span className="block text-[10px] text-gray-400">Datos personales y contacto</span>
                  </span>
                </button>
              </li>

              {/* 3) Soportes que cargó el candidato */}
              <li className="flex-shrink-0 lg:flex-shrink lg:mb-1 lg:pb-1 lg:border-b lg:border-gray-100">
                <button type="button" onClick={() => irAPaso(PASO_SOPORTES)}
                  className={`w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 transition-colors ${
                    enSoportes ? 'bg-sky-50 ring-1 ring-sky-200' : 'hover:bg-gray-50'}`}>
                  <span className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center border-2 ${
                    enSoportes ? 'bg-sky-500 border-sky-500 text-white' : 'bg-white border-sky-300 text-sky-500'}`}>
                    <IconoClip />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className={`block text-xs font-medium ${enSoportes ? 'text-sky-800' : 'text-gray-700'}`}>Soportes</span>
                    <span className="block text-[10px] text-gray-400">{resumenSoportes ?? 'Documentos del candidato'}</span>
                  </span>
                </button>
              </li>

              {/* 3) Secciones de la plantilla, en el orden del constructor */}
              {bloques.map((bloque, idx) => {
                const { estado, detalle } = estadoBloque(bloque)
                const activo = paso === idx + PRIMER_BLOQUE
                const grupo = grupoDe(bloque)
                const grupoNuevo = grupo && grupo !== grupoDe(bloques[idx - 1])
                return (
                  <Fragment key={bloque.key}>
                  {grupoNuevo && (
                    <li aria-hidden="true" className="hidden lg:block px-2.5 pt-3 pb-1 text-[10px] font-semibold uppercase tracking-wider text-gray-500">
                      {grupo}
                    </li>
                  )}
                  <li className="flex-shrink-0 lg:flex-shrink">
                    <button type="button" onClick={() => irAPaso(idx + PRIMER_BLOQUE)}
                      className={`w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 transition-colors ${
                        activo ? 'bg-primary-50 ring-1 ring-primary-200' : 'hover:bg-gray-50'}`}>
                      <span className={`flex-shrink-0 w-6 h-6 rounded-full text-[11px] font-bold flex items-center justify-center border-2 ${
                        activo ? 'bg-primary-600 border-primary-600 text-white'
                          : estado === 'completo' ? 'bg-green-50 border-green-400 text-green-600'
                          : estado === 'parcial' ? 'bg-amber-50 border-amber-400 text-amber-600'
                          : 'bg-white border-gray-300 text-gray-400'}`}>
                        {estado === 'completo' && !activo ? <IconoCheck /> : idx + 1}
                      </span>
                      <span className="min-w-0">
                        <span className={`block text-xs font-medium truncate max-w-[160px] lg:max-w-none lg:whitespace-normal lg:break-words lg:leading-snug ${activo ? 'text-primary-700' : 'text-gray-700'} ${omitidas.has(bloque.data.idSeccion) ? 'line-through decoration-gray-400' : ''}`}>
                          {nombreDe(bloque)}
                        </span>
                        <span className="block text-[10px] text-gray-400">{detalle}</span>
                      </span>
                    </button>
                  </li>
                  </Fragment>
                )
              })}

              {/* 4) Soportes del informe — qué adjuntos saldrán en el informe */}
              {tienePasoAnexos && (
                <li className="flex-shrink-0 lg:flex-shrink lg:mt-1 lg:pt-1 lg:border-t lg:border-gray-100">
                  <button type="button" onClick={() => irAPaso(pasoAnexos)}
                    className={`w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 transition-colors ${
                      enAnexos ? 'bg-sky-50 ring-1 ring-sky-200' : 'hover:bg-gray-50'}`}>
                    <span className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center border-2 ${
                      enAnexos ? 'bg-sky-500 border-sky-500 text-white' : 'bg-white border-sky-300 text-sky-500'}`}>
                      <IconoClip />
                    </span>
                    <span className="min-w-0">
                      <span className={`block text-xs font-medium ${enAnexos ? 'text-sky-800' : 'text-gray-700'}`}>Soportes del informe</span>
                      <span className="block text-[10px] text-gray-400">Qué adjuntos salen en el informe</span>
                    </span>
                  </button>
                </li>
              )}

              {/* 5) Revisión y envío — solo quien diligencia */}
              {esEjecutor && bloques.length > 0 && (
                <li className="flex-shrink-0 lg:flex-shrink lg:mt-1 lg:pt-1 lg:border-t lg:border-gray-100">
                  <button type="button" onClick={() => irAPaso(pasoEnvio)}
                    className={`w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 transition-colors ${
                      enEnvio ? 'bg-green-50 ring-1 ring-green-200' : 'hover:bg-gray-50'}`}>
                    <span className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center border-2 ${
                      enEnvio ? 'bg-green-600 border-green-600 text-white' : 'bg-white border-green-300 text-green-600'}`}>
                      <IconoCheck />
                    </span>
                    <span className="min-w-0">
                      <span className={`block text-xs font-medium ${enEnvio ? 'text-green-800' : 'text-gray-700'}`}>Revisión y envío</span>
                      <span className="block text-[10px] text-gray-400">
                        {meta?.soloLectura ? 'Ya enviado' : pendientes.length > 0 ? `${pendientes.length} sin terminar` : 'Listo para enviar'}
                      </span>
                    </span>
                  </button>
                </li>
              )}
            </ol>
          </nav>
        </aside>

      <div ref={inicioContenido} className="space-y-4 min-w-0 scroll-mt-4">
        {/* Pasos fijos (novedades, candidato, soportes) — no se imprimen: el documento impreso es
            solo el formulario. */}
        <div className={`${enNovedades ? 'block' : 'hidden'} print:hidden`}>
          <NovedadesSubproceso idServicioSubproceso={idServicioSubproceso} onConteo={setNovedadesAbiertas}
            onReprogramado={fecha => setMeta(prev => (prev ? { ...prev, fechaProgramada: fecha } : prev))}
            onError={m => mostrarToast(m, 'error')} onExito={m => mostrarToast(m)} />
        </div>

        <div className={`${enCandidato ? 'block' : 'hidden'} print:hidden`}>
          <FichaCandidato idServicioSubproceso={idServicioSubproceso} seccion="datos" onError={m => mostrarToast(m, 'error')} />
        </div>

        {/* Soportes del candidato — paso propio. */}
        <div className={`${enSoportes ? 'block' : 'hidden'} print:hidden`}>
          <FichaCandidato idServicioSubproceso={idServicioSubproceso} seccion="soportes"
            onResumen={setResumenSoportes} onError={m => mostrarToast(m, 'error')} />
        </div>
        {bloques.map((bloque, idx) => {
          const visible = paso === idx + PRIMER_BLOQUE
          if (bloque.tipo === 'fija') {
            const tabla = (hojaVidaCandidato?.tablas ?? []).find(t => t.clave === bloque.data.clave)
            if (!tabla) return null
            // Sección de validación emparejada con esta tabla fija (ver la carga inicial más
            // arriba) — columnas que agregó el admin para que analista/poligrafista/visitador
            // opinen por fila, aparte de las columnas de solo lectura de la hoja de vida.
            const seccionValidacion = secciones.find(s => s.origenFijoClave === bloque.data.clave)
            // tabla (arriba) es la lista EN VIVO de hojaVidaCandidato — se usa para la etiqueta en
            // vez de bloque.data (la foto estática de cuando se abrió la página), así una fila
            // agregada recién también muestra su etiqueta correcta, no solo las que ya existían.
            const primeraColumnaFija = tabla.columnas?.[0]?.clave
            if (omitidas.has(bloque.data.idSeccion)) {
              return (
                <div key={bloque.key} className={`${visible ? 'block' : 'hidden'} print:hidden`}>
                  <SeccionQuitada nombre={bloque.data.nombre} disabled={meta?.soloLectura} onAgregar={() => volverAAgregar(bloque.data.idSeccion)} />
                </div>
              )
            }
            return (
              <div key={bloque.key} className={visible ? 'block' : 'hidden print:block'}>
                {bloque.data.opcional && !meta?.soloLectura && (
                  <div className="mb-2 flex justify-end print:hidden">
                    <BotonQuitarSeccion onClick={() => setQuitando({ idSeccion: bloque.data.idSeccion, nombre: bloque.data.nombre })} />
                  </div>
                )}
                <TablaHojaVidaEditable idCandidato={meta.idCandidato} tabla={tabla}
                  onGuardado={actualizado => setHojaVidaCandidato(prev => ({
                    ...prev, tablas: prev.tablas.map(t => t.clave === actualizado.clave ? actualizado : t),
                  }))}
                  onError={m => mostrarToast(m, 'error')} disabled={meta?.soloLectura} puedeEliminar={puedeEliminar} />

                {seccionValidacion && seccionValidacion.columnas.length > 0 && (
                  <div className="mt-3 bg-white border border-gray-200 border-l-4 border-l-violet-400 rounded-2xl shadow-sm p-4
                    print:border print:border-gray-300 print:rounded-none print:shadow-none print:break-inside-avoid">
                    <div className="flex items-center gap-2 mb-3 print:mb-2">
                      <span className="flex-shrink-0 w-6 h-6 rounded-full bg-violet-50 text-violet-600 text-[11px] font-bold flex items-center justify-center print:hidden">✓</span>
                      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Validación — {bloque.data.nombre}</p>
                    </div>
                    {seccionValidacion.filas.length === 0 ? (
                      <p className="text-xs text-gray-400 bg-gray-50 rounded-xl px-4 py-6 text-center">Sin filas para validar todavía.</p>
                    ) : (
                      <div className="space-y-3">
                        {seccionValidacion.filas.map(f => {
                          const filaFija = (tabla.filas ?? []).find(rf => String(rf.id) === String(f.idOrigenFijo))
                          const etiquetaFila = (primeraColumnaFija && filaFija?.valores?.[primeraColumnaFija]) || 'Fila'
                          return (
                            <div key={f.localId} className="relative bg-gray-50/70 border border-gray-100 rounded-xl p-4 print:bg-transparent print:border-gray-300">
                              <div className="flex items-center justify-between gap-2 mb-3">
                                <p className="text-sm font-semibold text-gray-700 truncate">{etiquetaFila}</p>
                                <PuntosFila columnas={seccionValidacion.columnas} valores={f.valores} />
                              </div>
                              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-4 gap-y-3">
                                {seccionValidacion.columnas.map(c => (
                                  <Campo key={c.idColumna} label={c.nombre} tipoDato={c.tipoDato}>
                                    <InputPorTipo tipoDato={c.tipoDato} value={f.valores[c.idColumna]} opciones={c.opciones} etiquetas={etiquetasDe(c)} maxLength={c.longitudMaxima}
                                      onChange={v => editarValorFila(seccionValidacion.idSeccion, f.localId, c.idColumna, v)}
                                      idServicioSubproceso={idServicioSubproceso} onError={m => mostrarToast(m, 'error')}
                                      disabled={meta?.soloLectura} />
                                    {esCritica(c, f.valores[c.idColumna]) && (
                                      <JustificacionCritica valor={f.justificaciones?.[c.idColumna]} disabled={meta?.soloLectura}
                                        onChange={t => editarJustificacionFila(seccionValidacion.idSeccion, f.localId, c.idColumna, t)} />
                                    )}
                                  </Campo>
                                ))}
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    )}
                    <TotalPuntosTabla columnas={seccionValidacion.columnas} filas={seccionValidacion.filas} />
                  </div>
                )}
              </div>
            )
          }
          const s = bloque.data
          const p = progresoSeccion(s)
          if (omitidas.has(s.idSeccion)) {
            return (
              <div key={s.idSeccion} className={`${visible ? 'block' : 'hidden'} print:hidden`}>
                <SeccionQuitada nombre={s.nombre} disabled={meta?.soloLectura} onAgregar={() => volverAAgregar(s.idSeccion)} />
              </div>
            )
          }
          return (
          <section key={s.idSeccion}
            className={`${visible ? 'block' : 'hidden print:block'} bg-white border border-gray-200 border-l-4 ${ACENTO_TIPO[s.tipo] ?? 'border-l-gray-300'} rounded-2xl shadow-sm p-4
              print:border print:border-gray-300 print:rounded-none print:shadow-none print:break-inside-avoid`}>
            <div className="flex items-center gap-2 mb-3 print:mb-2">
              <span className={`flex-shrink-0 w-6 h-6 rounded-full ${BADGE_TIPO[s.tipo] ?? 'bg-gray-100 text-gray-500'} text-[11px] font-bold flex items-center justify-center print:hidden`}>
                {idx + 1}
              </span>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">{s.nombre}</p>
              <span className={`hidden sm:inline text-[9px] font-semibold px-1.5 py-0.5 rounded-full print:hidden ${CHIP_TIPO[s.tipo] ?? 'bg-gray-100 text-gray-500'}`}>
                {ETIQUETA_TIPO[s.tipo] ?? s.tipo}
              </span>
              {p && p.total > 0 && (
                <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full print:hidden ${
                  p.llenos === p.total ? 'bg-green-100 text-green-700' : p.llenos > 0 ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-400'
                }`}>
                  {p.llenos}/{p.total}
                </span>
              )}
              {s.opcional && !meta?.soloLectura && (
                <span className="ml-auto print:hidden">
                  <BotonQuitarSeccion onClick={() => setQuitando({ idSeccion: s.idSeccion, nombre: s.nombre })} />
                </span>
              )}
            </div>

            {s.tipo === 'CAMPOS' ? (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {s.columnas.map(c => (
                  <Campo key={c.idColumna} label={c.nombre} tipoDato={c.tipoDato} automatico={c.automatico}
                    calculado={c.calculado} obligatorio={c.obligatorio}>
                    <InputPorTipo tipoDato={c.tipoDato} value={s.valores[c.idColumna]} opciones={c.opciones} etiquetas={etiquetasDe(c)} maxLength={c.longitudMaxima}
                      onChange={v => editarValorCampo(s.idSeccion, c.idColumna, v)}
                      idServicioSubproceso={idServicioSubproceso} onError={m => mostrarToast(m, 'error')}
                      disabled={meta?.soloLectura || c.automatico || criticidadBloqueada(c)} />
                    {esCritica(c, s.valores[c.idColumna]) && (
                      <JustificacionCritica valor={s.justificaciones?.[c.idColumna]} disabled={meta?.soloLectura}
                        onChange={t => editarJustificacionCampo(s.idSeccion, c.idColumna, t)} />
                    )}
                  </Campo>
                ))}
              </div>
            ) : (
              <div>
                {s.filas.length === 0 ? (
                  <p className="text-xs text-gray-400 bg-gray-50 rounded-xl px-4 py-6 text-center">
                    {s.calculada ? 'El sistema arma esta tabla con lo que registres en las demás secciones.' : 'Todavía no hay filas.'}
                  </p>
                ) : (
                  <div className="space-y-3">
                    {s.filas.map(f => (
                      <div key={f.localId} className="relative bg-gray-50/70 border border-gray-100 rounded-xl p-4 print:bg-transparent print:border-gray-300">
                        <div className="flex items-center gap-2 mb-3">
                          <input aria-label="Nombre de la fila" type="text" value={f.etiqueta}
                            onChange={e => editarEtiquetaFila(s.idSeccion, f.localId, e.target.value)}
                            disabled={meta?.soloLectura || s.calculada} placeholder="Nombre de la fila"
                            className="flex-1 min-w-0 bg-transparent border-0 border-b border-dashed border-gray-300 focus:border-primary-400 text-sm font-semibold text-gray-700 focus:outline-none px-0 py-1 disabled:border-transparent print:border-none" />
                          <PuntosFila columnas={s.columnas} valores={f.valores} />
                          {!meta?.soloLectura && puedeEliminar && !s.calculada && (
                            <button type="button" onClick={() => quitarFila(s.idSeccion, f.localId)} title="Quitar esta fila"
                              className="flex-shrink-0 p-1 rounded-lg text-gray-300 hover:bg-red-50 hover:text-red-500 transition-colors print:hidden">
                              <IconoPapelera />
                            </button>
                          )}
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-4 gap-y-3">
                          {s.columnas.map(c => (
                            <Campo key={c.idColumna} label={c.nombre} tipoDato={c.tipoDato} automatico={c.automatico}
                              calculado={c.calculado} obligatorio={c.obligatorio}>
                              <InputPorTipo tipoDato={c.tipoDato} value={f.valores[c.idColumna]} opciones={c.opciones} etiquetas={etiquetasDe(c)} maxLength={c.longitudMaxima}
                                onChange={v => editarValorFila(s.idSeccion, f.localId, c.idColumna, v)}
                                idServicioSubproceso={idServicioSubproceso} onError={m => mostrarToast(m, 'error')}
                                disabled={meta?.soloLectura || c.automatico || s.calculada} />
                              {esCritica(c, f.valores[c.idColumna]) && (
                                <JustificacionCritica valor={f.justificaciones?.[c.idColumna]} disabled={meta?.soloLectura}
                                  onChange={t => editarJustificacionFila(s.idSeccion, f.localId, c.idColumna, t)} />
                              )}
                            </Campo>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
                <TotalPuntosTabla columnas={s.columnas} filas={s.filas} />
                {!meta?.soloLectura && !s.calculada && (
                  <button type="button" onClick={() => agregarFila(s.idSeccion)}
                    className="w-full mt-3 inline-flex items-center justify-center gap-1.5 text-xs text-violet-600 hover:text-violet-800 font-medium py-2.5 border border-dashed border-violet-200 rounded-xl hover:bg-violet-50/40 transition-colors print:hidden">
                    <IconoMas /> Agregar fila
                  </button>
                )}
              </div>
            )}
          </section>
        )})}

        {/* Soportes del informe: qué adjuntos saldrán, para todos los roles. */}
        {tienePasoAnexos && (
          <div className={`${enAnexos ? 'block' : 'hidden'} print:hidden`}>
            <SoportesInforme idServicioSubproceso={idServicioSubproceso} secciones={secciones} omitidas={omitidas} />
          </div>
        )}

        {/* Último paso (solo quien diligencia): revisión de lo que falta + envío. */}
        {esEjecutor && bloques.length > 0 && (
          <section className={`${enEnvio ? 'block' : 'hidden'} print:hidden bg-white border border-gray-200 border-l-4 border-l-green-400 rounded-2xl shadow-sm p-4`}>
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Revisión antes de enviar</p>
            {meta?.soloLectura ? (
              <p className="text-sm text-gray-600">Este subproceso ya se envió — queda pendiente la revisión de quien lo coordina.</p>
            ) : (
              <>
                {pendientes.length === 0 ? (
                  <p className="text-sm text-green-700 bg-green-50 rounded-xl px-3 py-2.5">
                    Todas las secciones tienen información. Si ya revisaste todo, puedes enviarlo.
                  </p>
                ) : (
                  <div className="mb-1">
                    <p className="text-sm text-amber-700 mb-2">
                      {pendientes.length === 1 ? 'Esta sección está' : `Estas ${pendientes.length} secciones están`} sin terminar:
                    </p>
                    <ul className="divide-y divide-gray-100 border border-gray-100 rounded-xl overflow-hidden">
                      {pendientes.map(({ b, idx, detalle }) => (
                        <li key={b.key} className="flex items-center justify-between gap-3 px-3 py-2 bg-gray-50/50">
                          <span className="text-sm text-gray-700 min-w-0 break-words">
                            <span className="text-gray-400 mr-1.5">{idx + 1}.</span>{nombreDe(b)}
                            <span className="text-[11px] text-gray-400 ml-2">{detalle}</span>
                          </span>
                          <button type="button" onClick={() => irAPaso(idx + PRIMER_BLOQUE)}
                            className="flex-shrink-0 text-xs font-medium text-primary-600 hover:text-primary-800">
                            Ir a la sección →
                          </button>
                        </li>
                      ))}
                    </ul>
                    <p className="text-[11px] text-gray-400 mt-2">
                      Puedes enviarlo igual si esa información no aplica — en ese caso deja una novedad explicando por qué.
                    </p>
                  </div>
                )}
                {obligatoriosVacios.length > 0 && (
                  <div className="mt-4 rounded-xl border border-red-200 bg-red-50/60 px-3.5 py-3">
                    <p className="text-sm font-medium text-red-700">
                      {obligatoriosVacios.length === 1 ? 'Falta 1 campo obligatorio' : `Faltan ${obligatoriosVacios.length} campos obligatorios`} — no se puede enviar todavía:
                    </p>
                    <ul className="mt-2 space-y-1">
                      {obligatoriosVacios.map(x => (
                        <li key={x.clave} className="flex items-center justify-between gap-3 text-sm text-red-800">
                          <span className="min-w-0 break-words">{x.texto}</span>
                          {x.paso != null && (
                            <button type="button" onClick={() => irAPaso(x.paso)} className="flex-shrink-0 text-xs font-medium text-red-700 hover:text-red-900 underline">
                              Ir a llenarlo
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {criticasSinExplicar.length > 0 && (
                  <div className="mt-4 rounded-xl border border-red-200 bg-red-50/60 px-3.5 py-3">
                    <p className="text-sm font-medium text-red-700">
                      {criticasSinExplicar.length === 1 ? 'Hay 1 respuesta determinante' : `Hay ${criticasSinExplicar.length} respuestas determinantes`} sin
                      explicación — no se puede enviar todavía:
                    </p>
                    <ul className="mt-2 space-y-1">
                      {criticasSinExplicar.map(x => (
                        <li key={x.clave} className="flex items-center justify-between gap-3 text-sm text-red-800">
                          <span className="min-w-0 break-words">{x.texto}</span>
                          {x.paso != null && (
                            <button type="button" onClick={() => irAPaso(x.paso)} className="flex-shrink-0 text-xs font-medium text-red-700 hover:text-red-900 underline">
                              Ir a explicarla
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                <div className="flex flex-wrap items-center justify-end gap-3 mt-4 pt-3 border-t border-gray-100">
                  <p className="text-[11px] text-gray-400 mr-auto">
                    Al enviarlo se le avisa a quien coordina para que lo apruebe o te lo devuelva.
                  </p>
                  <button type="button" onClick={completarSubproceso} disabled={completando || !meta?.idFormulario || criticasSinExplicar.length > 0 || obligatoriosVacios.length > 0}
                    title={!meta?.idFormulario ? 'Guarda el formulario primero' : undefined}
                    className="inline-flex items-center gap-1.5 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors shadow-sm bg-green-600 hover:bg-green-700 shadow-green-200">
                    <IconoCheckCompletar /> {completando ? 'Enviando…' : 'Marcar como completado'}
                  </button>
                </div>
              </>
            )}
          </section>
        )}

        <div className="flex items-center justify-between gap-3 print:hidden sticky bottom-0 bg-gradient-to-t from-gray-50 via-gray-50/95 to-transparent pt-3 pb-3 -mx-1 px-1">
          <button type="button" onClick={() => irAPaso(Math.max(0, paso - 1))} disabled={paso === 0}
            className="inline-flex items-center gap-1.5 border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
            <IconoFlechaIzq /> Anterior
          </button>
          {paso < ultimoPaso && (
            <button type="button" onClick={() => irAPaso(paso + 1)}
              className={`inline-flex items-center gap-1.5 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm ${
                pasoEnvio !== null && paso === pasoEnvio - 1
                  ? 'bg-green-600 hover:bg-green-700 text-white'
                  : 'bg-primary-600 hover:bg-primary-700 text-white'}`}>
              {paso === PASO_NOVEDADES ? 'Candidato'
                : paso === PASO_CANDIDATO ? 'Soportes'
                : paso === PASO_SOPORTES ? 'Ir al formulario'
                : paso === pasoAnexos - 1 ? 'Soportes del informe'
                : pasoEnvio !== null && paso === pasoEnvio - 1 ? 'Revisión y envío' : 'Siguiente'} <IconoFlechaDer />
            </button>
          )}
        </div>
      </div>
      </div>
      {quitando && (
        <ConfirmModal
          titulo={`Quitar "${quitando.nombre}"`}
          danger
          confirmLabel="Quitar sección"
          onConfirmar={() => quitarSeccion(quitando.idSeccion)}
          onCancelar={() => setQuitando(null)}
          onClose={() => setQuitando(null)}>
          <p className="text-sm text-gray-500">
            Esta sección no aplica para este caso: no la diligencias y no saldrá en el informe. Lo que hayas escrito en
            ella se borra al guardar. Puedes volver a agregarla mientras no hayas enviado el formulario.
          </p>
        </ConfirmModal>
      )}

      {devolviendo && (
        <ConfirmModal
          titulo="Devolver este subproceso"
          danger
          confirmLabel="Devolver"
          cargando={enviandoDevolver}
          onConfirmar={confirmarDevolver}
          onCancelar={() => setDevolviendo(false)}
          onClose={() => setDevolviendo(false)}>
          <p className="text-sm text-gray-500 mb-3">
            Vuelve a «En ejecución» para la misma persona, con la marca «Devuelto» y este motivo — le llega una notificación.
          </p>
          <SelectorMotivo tipo="DEVOLUCION" valor={motivoDevolver} onChange={setMotivoDevolver} etiqueta="Motivo de la devolución"
            detalle detalleEtiqueta="¿Qué hay que corregir?" autoFocus />
        </ConfirmModal>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

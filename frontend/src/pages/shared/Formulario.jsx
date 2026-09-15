import { useState, useEffect, useRef } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import formulariosService from '../../services/formulariosService'
import Toast from '../../components/Toast'
import TablaHojaVidaEditable from '../../components/TablaHojaVidaEditable'

// Compartida entre analista, poligrafista y visitador — mismo formulario universal para
// cualquier subproceso, solo cambia el texto/ruta de "volver" según en cuál portal se abrió (cada
// portal nombra su lista de trabajo distinto: asignaciones / servicios / visitas).
const VOLVER = {
  analista: { ruta: '/analista/asignaciones', etiqueta: 'Mis Asignaciones' },
  poligrafista: { ruta: '/poligrafista/servicios', etiqueta: 'Mis Servicios' },
  visitador: { ruta: '/visitador/visitas', etiqueta: 'Mis Visitas' },
  gestor: { ruta: '/gestor/solicitudes', etiqueta: 'Solicitudes' },
  programador: { ruta: '/programador/agenda', etiqueta: 'Agenda' },
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
const ICONO_TIPO_DATO = { TEXTO: 'Aa', NUMERO: '#', FECHA: '📅', BOOLEANO: '✓', LISTA: '▾', ARCHIVO: '📎' }

function Campo({ label, tipoDato, automatico, children }) {
  return (
    <div>
      {label && label.trim() && (
        <label className="flex items-center gap-1.5 text-xs font-medium text-gray-600 mb-1 print:font-semibold">
          {tipoDato && (
            <span className="flex-shrink-0 w-4 h-4 rounded bg-gray-100 text-gray-400 text-[9px] flex items-center justify-center print:hidden">
              {ICONO_TIPO_DATO[tipoDato] ?? ''}
            </span>
          )}
          <span className="truncate">{label}</span>
          {automatico && (
            <span className="flex-shrink-0 text-[9px] font-semibold px-1 py-0.5 rounded bg-primary-50 text-primary-500 print:hidden">
              auto
            </span>
          )}
        </label>
      )}
      {children}
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

function InputPorTipo({ tipoDato, value, opciones, onChange, idServicioSubproceso, onError, disabled }) {
  if (tipoDato === 'ARCHIVO') {
    return <CampoArchivo idServicioSubproceso={idServicioSubproceso} value={value} onChange={onChange} onError={onError} disabled={disabled} />
  }
  if (tipoDato === 'LISTA') {
    return (
      <select value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls}>
        <option value="">—</option>
        {(opciones ?? []).map(o => <option key={o} value={o}>{o}</option>)}
      </select>
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
  return <input type="text" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls} />
}

/* ─── Iconos ─── */
function IconoDocumento() {
  return (
    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
    </svg>
  )
}
function IconoImprimir() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6.72 13.829c-.24.03-.48.062-.72.096m.72-.096a42.415 42.415 0 0110.56 0m-10.56 0L6.34 18m10.94-4.171c.24.03.48.062.72.096m-.72-.096L17.66 18m0 0l.229 2.523a1.125 1.125 0 01-1.12 1.227H7.231c-.662 0-1.18-.568-1.12-1.227L6.34 18m11.318 0h1.091A2.25 2.25 0 0021 15.75V9.456c0-1.081-.768-2.015-1.837-2.175a48.055 48.055 0 00-1.913-.247M6.34 18H5.25A2.25 2.25 0 013 15.75V9.456c0-1.081.768-2.015 1.837-2.175a48.041 48.041 0 011.913-.247m10.5 0a48.536 48.536 0 00-10.5 0m10.5 0V3.375c0-.621-.504-1.125-1.125-1.125h-8.25c-.621 0-1.125.504-1.125 1.125v3.659M18 10.5h.008v.008H18V10.5zm-3 0h.008v.008H15V10.5z" />
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

// Con qué versión de la plantilla se creó este subproceso vs. la versión actual — si no
// coinciden, admin le agregó/quitó campos a la plantilla después de creado este subproceso.
function BadgeVersionPlantilla({ version, versionActual }) {
  if (!version) return null
  const desactualizada = versionActual != null && version !== versionActual
  return (
    <span
      title={desactualizada
        ? `Se creó con la plantilla v${version} — la plantilla actual es v${versionActual}, puede tener campos distintos.`
        : `Plantilla v${version}`}
      className={`flex items-center gap-1 text-[10px] font-medium px-1.5 py-0.5 rounded-full whitespace-nowrap print:hidden ${
        desactualizada ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-500'}`}>
      Plantilla v{version}{desactualizada ? ' ⚠' : ''}
    </span>
  )
}

export default function Formulario() {
  const { idServicioSubproceso } = useParams()
  const prefijo = useLocation().pathname.split('/')[1]
  const volver = VOLVER[prefijo] ?? VOLVER.analista

  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [descargando, setDescargando] = useState(false)
  const [toast, setToast] = useState(null)
  const [meta, setMeta] = useState(null)
  const [secciones, setSecciones] = useState([])
  const [hojaVidaCandidato, setHojaVidaCandidato] = useState(null)
  const [paso, setPaso] = useState(0)
  const [generandoPdf, setGenerandoPdf] = useState(false)
  // Cambios hechos por el usuario que todavía no se guardaron — controla tanto el resaltado del
  // botón "Guardar" como el disparador del guardado automático (ver autoguardadoActivo abajo).
  const [sucio, setSucio] = useState(false)
  // Contador de ediciones — permite detectar si llegó un cambio nuevo MIENTRAS un autoguardado
  // ya estaba en camino, para no marcar "guardado" un cambio que en realidad quedó afuera de esa
  // llamada (ver guardar()).
  const cambiosRef = useRef(0)
  const marcarSucio = () => { cambiosRef.current += 1; setSucio(true) }

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  useEffect(() => {
    setPaso(0)
    setSucio(false)
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
        })
        setSecciones((r.secciones ?? []).map(s => ({
          idSeccion: s.idSeccion,
          nombre: s.nombre,
          tipo: s.tipo,
          orden: s.orden,
          columnas: s.columnas,
          valores: { ...s.valores },
          filas: (s.filas ?? []).map(f => ({ localId: idLocal(), idFila: f.idFila, etiqueta: f.etiqueta, valores: { ...f.valores } })),
        })))
        // Las tablas de hoja de vida (referencias, educación...) se editan directo contra el
        // registro real del candidato — se cargan aparte, con los id reales de cada fila, para
        // que TablaHojaVidaEditable pueda guardarlas.
        if (r.idCandidato && (r.tablasFijas ?? []).length > 0) {
          formulariosService.obtenerHojaVidaCandidato(r.idCandidato).then(setHojaVidaCandidato).catch(() => {})
        }
      })
      .catch(() => setError('No se pudo cargar el formulario.'))
      .finally(() => setCargando(false))
  }, [idServicioSubproceso])

  // Solo quien está llenando el formulario ve su propio avance — gestor/programador ya tienen
  // su propio indicador de progreso en las tablas de Solicitudes/Agenda.
  const mostrarProgreso = ['analista', 'poligrafista', 'visitador'].includes(prefijo)

  // Analista/poligrafista/visitador pueden agregar y editar filas, pero no borrar ninguna —
  // evita que se pierda por accidente un dato ya guardado (más grave todavía en las tablas de
  // hoja de vida real del candidato, que comparte con otros servicios). Gestor/programador sí.
  const puedeEliminar = !['analista', 'poligrafista', 'visitador'].includes(prefijo)

  const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''

  const progresoSeccion = (s) => {
    if (s.tipo !== 'CAMPOS') return null
    const total = s.columnas.length
    const llenos = s.columnas.filter(c => tieneValor(s.valores[c.idColumna])).length
    return { llenos, total }
  }

  const progresoTotal = secciones.reduce((acc, s) => {
    const p = progresoSeccion(s)
    if (!p) return acc
    return { llenos: acc.llenos + p.llenos, total: acc.total + p.total }
  }, { llenos: 0, total: 0 })
  const porcentajeTotal = progresoTotal.total > 0 ? Math.round((progresoTotal.llenos / progresoTotal.total) * 100) : 0

  // Secciones editables + tablas de hoja de vida del candidato (referencias, educación...)
  // intercaladas en el mismo orden en que las armó el administrador en el constructor.
  const bloques = [
    ...secciones.map(s => ({ tipo: 'editable', orden: s.orden ?? 0, key: `s-${s.idSeccion}`, data: s })),
    ...(meta?.tablasFijas ?? []).map(t => ({ tipo: 'fija', orden: t.orden ?? 0, key: `t-${t.idSeccion}`, data: t })),
  ].sort((a, b) => a.orden - b.orden)

  const nombreDe = (bloque) => bloque?.tipo === 'fija' ? bloque.data.nombre : bloque?.data.nombre ?? ''

  // Para el punto de cada paso: ¿ya tiene algo cargado? CAMPOS = al menos un valor; TABLA/hoja de
  // vida = al menos una fila. Solo es una señal visual, no bloquea la navegación.
  const bloqueTieneAvance = (bloque) => {
    if (bloque.tipo === 'fija') {
      const tabla = (hojaVidaCandidato?.tablas ?? []).find(t => t.clave === bloque.data.clave)
      return (tabla?.filas ?? []).length > 0
    }
    const s = bloque.data
    if (s.tipo === 'CAMPOS') return s.columnas.some(c => tieneValor(s.valores[c.idColumna]))
    return s.filas.length > 0
  }

  const editarValorCampo = (idSeccion, idColumna, valor) => {
    setSecciones(prev => prev.map(s => s.idSeccion === idSeccion
      ? { ...s, valores: { ...s.valores, [idColumna]: valor } } : s))
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
      ? { ...s, filas: [...s.filas, { localId: idLocal(), idFila: null, etiqueta: '', valores: {} }] } : s))
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
  const descargarPdf = async () => {
    setGenerandoPdf(true)
    try {
      const blob = await formulariosService.descargarDocumentoPdf(idServicioSubproceso)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `formulario-${idServicioSubproceso}.pdf`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      mostrarToast(err.response?.data?.mensaje ?? 'No se pudo generar el PDF. Guarda el formulario primero.', 'error')
    } finally {
      setGenerandoPdf(false)
    }
  }

  // silencioso = true para el autoguardado (ver más abajo): guarda sin toast de éxito, para no
  // interrumpir cada pocos segundos — un error sí se muestra siempre, autoguardado o no.
  const guardar = async ({ silencioso = false } = {}) => {
    const version = cambiosRef.current
    setGuardando(true)
    setError(null)
    try {
      const payload = secciones.map(s => ({
        idSeccion: s.idSeccion,
        valores: s.tipo === 'CAMPOS' ? s.valores : null,
        filas: s.tipo === 'TABLA' ? s.filas.map(f => ({ etiqueta: f.etiqueta, valores: f.valores })) : null,
      }))
      const r = await formulariosService.guardarFormulario(idServicioSubproceso, payload)
      // La primera vez que se guarda, el backend recién crea el Formulario (antes idFormulario
      // era null) — sin esto, "Descargar PDF"/"Descargar (.docx)" seguían mostrando "Guarda el
      // formulario primero" después de guardar, hasta recargar la página a mano.
      setMeta(prev => (prev ? { ...prev, idFormulario: r.idFormulario } : prev))
      // Si el usuario editó algo MIENTRAS este guardado estaba en camino, ese cambio no viajó en
      // este payload (se armó antes) — se deja "sucio" para que el autoguardado lo recoja en la
      // siguiente vuelta, en vez de marcarlo como guardado sin estarlo.
      if (cambiosRef.current === version) setSucio(false)
      if (!silencioso) mostrarToast('Formulario guardado correctamente')
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo guardar el formulario.')
    } finally {
      setGuardando(false)
    }
  }

  // Guardado automático — solo para quien diligencia el formulario (analista/poligrafista/
  // visitador): unos segundos después de cualquier cambio se guarda solo, sin que tengan que
  // acordarse de darle a "Guardar". Gestor/programador quedan fuera a propósito (ellos sí pueden
  // borrar filas manualmente y prefieren un guardado explícito, no uno que dispare solo).
  const autoguardadoActivo = ['analista', 'poligrafista', 'visitador'].includes(prefijo) && !meta?.soloLectura
  useEffect(() => {
    if (!autoguardadoActivo || !sucio || guardando) return
    const temporizador = setTimeout(() => { guardar({ silencioso: true }) }, 2500)
    return () => clearTimeout(temporizador)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secciones, sucio, guardando, autoguardadoActivo])

  // Avisa antes de cerrar/recargar la pestaña si hay cambios sin guardar — cubre tanto a gestor/
  // programador (sin autoguardado) como la ventana de unos segundos antes de que el autoguardado
  // de analista/poligrafista/visitador alcance a dispararse.
  useEffect(() => {
    if (!sucio) return
    const avisar = (e) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', avisar)
    return () => window.removeEventListener('beforeunload', avisar)
  }, [sucio])

  if (cargando) {
    return (
      <div className="flex justify-center py-16">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-3 print:hidden">
        <Link to={volver.ruta} className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          {volver.etiqueta}
        </Link>
      </div>

      <div className="mb-6 rounded-2xl border border-primary-100 bg-gradient-to-br from-primary-50 via-white to-violet-50/40 px-6 py-5 print:bg-none print:border-0 print:px-0 print:py-0">
        <div className="flex items-center gap-4">
          <div className="flex-shrink-0 w-14 h-14 rounded-2xl bg-gradient-to-br from-primary-500 to-primary-600 text-white flex items-center justify-center shadow-md shadow-primary-200 print:hidden">
            <IconoDocumento />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 flex-wrap">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-primary-500">Formulario de verificación</p>
              {meta?.soloLectura && (
                <span className="flex items-center gap-1 text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-gray-200 text-gray-600 print:hidden">
                  <IconoCandado /> Solo lectura
                </span>
              )}
              <BadgeVersionPlantilla version={meta?.versionPlantilla} versionActual={meta?.versionPlantillaActual} />
            </div>
            <h2 className="text-xl font-bold text-gray-900 truncate">
              {(meta?.datosCandidato ?? []).length > 0
                ? meta.datosCandidato.map(d => `${d.etiqueta}: ${d.valor || '—'}`).join(' · ')
                : `${meta?.nombreEvaluado ?? ''} · ${meta?.cedulaEvaluado ?? ''}`}
            </h2>
          </div>
          {mostrarProgreso && progresoTotal.total > 0 && (
            <div className="hidden sm:block flex-shrink-0 text-center px-4 py-2 rounded-xl bg-white/80 border border-primary-100 print:hidden">
              <p className="text-lg font-bold text-primary-700 leading-none">{porcentajeTotal}%</p>
              <p className="text-[10px] text-gray-400 mt-1">avance</p>
            </div>
          )}
        </div>

        {bloques.length > 0 && (
          <div className="flex flex-wrap items-center justify-end gap-2.5 mt-4 pt-4 border-t border-primary-100/70 print:hidden">
            {!meta?.soloLectura && (
              <span className={`text-xs font-medium mr-auto ${sucio ? 'text-amber-600' : 'text-gray-400'}`}>
                {guardando ? (autoguardadoActivo ? 'Guardando automáticamente…' : 'Guardando…')
                  : sucio ? 'Tienes cambios sin guardar' : 'Todo guardado'}
              </span>
            )}
            <button type="button" onClick={() => window.print()}
              className="inline-flex items-center gap-1.5 border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
              <IconoImprimir /> Imprimir
            </button>
            <button type="button" onClick={descargarPdf} disabled={generandoPdf}
              title={!meta?.soloLectura && !meta?.idFormulario ? 'Guarda el formulario primero' : undefined}
              className="inline-flex items-center gap-1.5 border border-red-200 bg-red-50/60 text-red-700 hover:bg-red-50 disabled:opacity-60 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
              <IconoDescargar /> {generandoPdf ? 'Generando…' : 'PDF'}
            </button>
            {/* Word (.docx) es el archivo "de trabajo" — solo gestor/admin lo necesitan para
                editar el documento fuera de la plataforma; analista/poligrafista/visitador/
                programador se quedan con el PDF (mismo contenido, ya no editable). */}
            {['gestor', 'admin'].includes(prefijo) && (
              <button type="button" onClick={descargar} disabled={descargando}
                title={!meta?.soloLectura && !meta?.idFormulario ? 'Guarda el formulario primero' : undefined}
                className="inline-flex items-center gap-1.5 border border-blue-200 bg-blue-50/60 text-blue-700 hover:bg-blue-50 disabled:opacity-60 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
                <IconoDescargar /> {descargando ? 'Generando…' : 'Word (.docx)'}
              </button>
            )}
            {!meta?.soloLectura && (
              <button type="button" onClick={() => guardar()} disabled={guardando}
                className={`inline-flex items-center gap-1.5 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors shadow-sm ${
                  sucio ? 'bg-amber-600 hover:bg-amber-700 shadow-amber-200 animate-pulse' : 'bg-primary-600 hover:bg-primary-700 shadow-primary-200'}`}>
                <IconoGuardar /> {guardando ? 'Guardando…' : sucio ? 'Guardar cambios' : 'Guardar formulario'}
              </button>
            )}
          </div>
        )}
      </div>

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
        <div className="bg-amber-50 border border-amber-200 rounded-2xl px-4 py-3 print:hidden">
          <p className="text-sm text-amber-700">
            Este subproceso todavía no tiene una plantilla configurada — pide a un administrador que la arme desde
            admin/catálogo → Plantilla.
          </p>
        </div>
      )}

      {bloques.length > 0 && (
        <div className="mb-5 print:hidden">
          <div className="flex items-center">
            {bloques.map((bloque, idx) => {
              const lleno = bloqueTieneAvance(bloque)
              const activo = idx === paso
              return (
                <div key={bloque.key} className="flex items-center flex-1 last:flex-none">
                  <button type="button" onClick={() => setPaso(idx)} title={nombreDe(bloque)}
                    className={`flex-shrink-0 w-7 h-7 rounded-full text-[11px] font-bold flex items-center justify-center border-2 transition-colors ${
                      activo ? 'bg-primary-600 border-primary-600 text-white'
                        : lleno ? 'bg-primary-50 border-primary-300 text-primary-600'
                        : 'bg-white border-gray-300 text-gray-400 hover:border-primary-300'}`}>
                    {lleno && !activo ? <IconoCheck /> : idx + 1}
                  </button>
                  {idx < bloques.length - 1 && (
                    <div className={`h-0.5 flex-1 mx-1 rounded transition-colors ${idx < paso ? 'bg-primary-300' : 'bg-gray-200'}`} />
                  )}
                </div>
              )
            })}
          </div>
          <p className="text-xs text-gray-400 text-center mt-2">
            Paso {paso + 1} de {bloques.length}: <span className="font-medium text-gray-600">{nombreDe(bloques[paso])}</span>
            {mostrarProgreso && progresoTotal.total > 0 && <span className="text-gray-300"> · {porcentajeTotal}% del formulario</span>}
          </p>
        </div>
      )}

      <div className="space-y-4">
        {bloques.map((bloque, idx) => {
          const visible = idx === paso
          if (bloque.tipo === 'fija') {
            const tabla = (hojaVidaCandidato?.tablas ?? []).find(t => t.clave === bloque.data.clave)
            if (!tabla) return null
            return (
              <div key={bloque.key} className={visible ? 'block' : 'hidden print:block'}>
                <TablaHojaVidaEditable idCandidato={meta.idCandidato} tabla={tabla}
                  onGuardado={actualizado => setHojaVidaCandidato(prev => ({
                    ...prev, tablas: prev.tablas.map(t => t.clave === actualizado.clave ? actualizado : t),
                  }))}
                  onError={m => mostrarToast(m, 'error')} disabled={meta?.soloLectura} puedeEliminar={puedeEliminar} />
              </div>
            )
          }
          const s = bloque.data
          const p = progresoSeccion(s)
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
              {mostrarProgreso && p && (
                <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full print:hidden ${
                  p.llenos === p.total ? 'bg-green-100 text-green-700' : p.llenos > 0 ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-400'
                }`}>
                  {p.llenos}/{p.total}
                </span>
              )}
            </div>

            {s.tipo === 'CAMPOS' ? (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {s.columnas.map(c => (
                  <Campo key={c.idColumna} label={c.nombre} tipoDato={c.tipoDato} automatico={!!c.campoCandidato || !!c.campoCliente}>
                    <InputPorTipo tipoDato={c.tipoDato} value={s.valores[c.idColumna]} opciones={c.opciones}
                      onChange={v => editarValorCampo(s.idSeccion, c.idColumna, v)}
                      idServicioSubproceso={idServicioSubproceso} onError={m => mostrarToast(m, 'error')}
                      disabled={meta?.soloLectura || !!c.campoCandidato || !!c.campoCliente} />
                  </Campo>
                ))}
              </div>
            ) : (
              <div>
                {s.filas.length === 0 ? (
                  <p className="text-xs text-gray-400 bg-gray-50 rounded-xl px-4 py-6 text-center">Todavía no hay filas.</p>
                ) : (
                  <div className="space-y-3">
                    {s.filas.map(f => (
                      <div key={f.localId} className="relative bg-gray-50/70 border border-gray-100 rounded-xl p-4 print:bg-transparent print:border-gray-300">
                        <div className="flex items-center gap-2 mb-3">
                          <input type="text" value={f.etiqueta}
                            onChange={e => editarEtiquetaFila(s.idSeccion, f.localId, e.target.value)}
                            disabled={meta?.soloLectura} placeholder="Nombre de la fila"
                            className="flex-1 min-w-0 bg-transparent border-0 border-b border-dashed border-gray-300 focus:border-primary-400 text-sm font-semibold text-gray-700 focus:outline-none px-0 py-1 disabled:border-transparent print:border-none" />
                          {!meta?.soloLectura && puedeEliminar && (
                            <button type="button" onClick={() => quitarFila(s.idSeccion, f.localId)} title="Quitar esta fila"
                              className="flex-shrink-0 p-1 rounded-lg text-gray-300 hover:bg-red-50 hover:text-red-500 transition-colors print:hidden">
                              <IconoPapelera />
                            </button>
                          )}
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-4 gap-y-3">
                          {s.columnas.map(c => (
                            <Campo key={c.idColumna} label={c.nombre} tipoDato={c.tipoDato} automatico={!!c.campoCandidato || !!c.campoCliente}>
                              <InputPorTipo tipoDato={c.tipoDato} value={f.valores[c.idColumna]} opciones={c.opciones}
                                onChange={v => editarValorFila(s.idSeccion, f.localId, c.idColumna, v)}
                                idServicioSubproceso={idServicioSubproceso} onError={m => mostrarToast(m, 'error')}
                                disabled={meta?.soloLectura || !!c.campoCandidato || !!c.campoCliente} />
                            </Campo>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
                {!meta?.soloLectura && (
                  <button type="button" onClick={() => agregarFila(s.idSeccion)}
                    className="w-full mt-3 inline-flex items-center justify-center gap-1.5 text-xs text-violet-600 hover:text-violet-800 font-medium py-2.5 border border-dashed border-violet-200 rounded-xl hover:bg-violet-50/40 transition-colors print:hidden">
                    <IconoMas /> Agregar fila
                  </button>
                )}
              </div>
            )}
          </section>
        )})}

        {bloques.length > 0 && (
          <div className="flex items-center justify-between gap-3 print:hidden sticky bottom-0 bg-gradient-to-t from-gray-50 via-gray-50/95 to-transparent pt-3 pb-3 -mx-1 px-1">
            <button type="button" onClick={() => setPaso(p => Math.max(0, p - 1))} disabled={paso === 0}
              className="inline-flex items-center gap-1.5 border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
              <IconoFlechaIzq /> Anterior
            </button>
            <button type="button" onClick={() => setPaso(p => Math.min(bloques.length - 1, p + 1))} disabled={paso === bloques.length - 1}
              className="inline-flex items-center gap-1.5 border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
              Siguiente <IconoFlechaDer />
            </button>
          </div>
        )}
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

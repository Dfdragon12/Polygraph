import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from 'react'
import { Link, useParams } from 'react-router-dom'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import api from '../../../services/api'
import formulariosService from '../../../services/formulariosService'
import Toast from '../../../components/Toast'

const TIPOS_DATO = [
  { value: 'TEXTO', label: 'Texto', icono: 'Aa' },
  { value: 'NUMERO', label: 'Número', icono: '#' },
  { value: 'FECHA', label: 'Fecha', icono: '📅' },
  { value: 'BOOLEANO', label: 'Sí / No', icono: '✓' },
  { value: 'LISTA', label: 'Lista desplegable', icono: '▾' },
  { value: 'ARCHIVO', label: 'Archivo (una o varias imágenes/documentos)', icono: '📎' },
]

// Acento de color por tipo de sección — para que una plantilla larga se distinga de un vistazo
// en vez de verse como una lista plana de cajas iguales.
const ACENTO_TIPO = {
  CAMPOS: { borde: 'border-l-primary-400', badge: 'bg-primary-50 text-primary-600', chip: 'bg-primary-100 text-primary-700' },
  TABLA: { borde: 'border-l-violet-400', badge: 'bg-violet-50 text-violet-600', chip: 'bg-violet-100 text-violet-700' },
}
// Una tabla de origen automático (hoja de vida del candidato) se distingue en rojo — mismo acento
// que usa el analista al editarla (TablaHojaVidaEditable) — para que se reconozca de un vistazo
// que esa sección no es una tabla manual cualquiera.
const ACENTO_ORIGEN_FIJO = { borde: 'border-l-red-400', badge: 'bg-red-50 text-red-600', chip: 'bg-red-100 text-red-700' }

let contador = 0
const idLocal = () => `local-${++contador}`

// Tablas de la hoja de vida del candidato — fijas, siempre disponibles como marcadores de tabla
// (${tablas['clave']} + repeatTableRow), sin necesidad de configurarlas en el constructor. Deben
// coincidir con CandidatoInfoService.COLUMNAS_TABLAS_FIJAS en el backend.
const TABLAS_HOJA_VIDA = [
  { clave: 'referencias', etiqueta: 'Referencias personales', columnas: ['nombre', 'parentesco', 'telefono', 'tiempo_conocimiento'] },
  { clave: 'educacion', etiqueta: 'Educación', columnas: ['nivel', 'institucion', 'titulo', 'fecha_inicio', 'fecha_fin', 'en_curso', 'ciudad'] },
  { clave: 'experiencia_laboral', etiqueta: 'Experiencia laboral', columnas: ['empresa', 'cargo', 'fecha_inicio', 'fecha_fin', 'labora_actualmente', 'ciudad', 'telefono_empresa', 'motivo_retiro', 'nombre_jefe', 'cargo_jefe'] },
  { clave: 'inactividades', etiqueta: 'Inactividades laborales', columnas: ['fecha_inicio', 'fecha_fin', 'dias_inactivo', 'justificacion', 'requiere_cuestionario'] },
]


/* ─── Iconos ─── */
function IconoFlechaArriba() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 15.75l7.5-7.5 7.5 7.5" />
    </svg>
  )
}
function IconoFlechaAbajo() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
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
function IconoTabla() {
  return (
    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3.375 19.5h17.25m-17.25 0a1.125 1.125 0 01-1.125-1.125M3.375 19.5h7.5c.621 0 1.125-.504 1.125-1.125m-9.75 0V5.625m0 12.75v-1.5c0-.621.504-1.125 1.125-1.125m18.375 2.625V5.625m0 12.75c0 .621-.504 1.125-1.125 1.125m1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125m0 3.75h-7.5A1.125 1.125 0 0112 18.375m9.75-12.75c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125m19.5 0v1.5c0 .621-.504 1.125-1.125 1.125M2.25 5.625v1.5c0 .621.504 1.125 1.125 1.125m0 0h17.25m-17.25 0h7.5c.621 0 1.125.504 1.125 1.125M3.375 8.25c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125m17.25-3.75c.621 0 1.125.504 1.125 1.125v1.5c0 .621-.504 1.125-1.125 1.125m-17.25 0h17.25" />
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
function IconoImagen() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909M3 8.25V18a2.25 2.25 0 002.25 2.25h13.5A2.25 2.25 0 0021 18V8.25m-18 0V6a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 6v2.25m-18 0h18M9 12.75a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0z" />
    </svg>
  )
}
function IconoOjo() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
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
function IconoArchivo() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5M10 11.25h4M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
    </svg>
  )
}
function IconoRestaurar() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" />
    </svg>
  )
}
function IconoSubir() {
  return (
    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 16.5V9.75m0 0l-3.75 3.75M12 9.75l3.75 3.75M6.75 19.5a4.5 4.5 0 01-1.41-8.775 5.25 5.25 0 0110.233-2.33 3 3 0 013.758 3.848A3.752 3.752 0 0118 19.5H6.75z" />
    </svg>
  )
}
function IconoAlertaTriangulo() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
    </svg>
  )
}
function IconoCheckCirculo() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  )
}
function IconoDescargar() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
    </svg>
  )
}
function IconoX() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
    </svg>
  )
}
function IconoLupa() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
    </svg>
  )
}
function IconoReloj() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6l4 2m6-2a10 10 0 11-20 0 10 10 0 0120 0z" />
    </svg>
  )
}

// Fecha legible corta ("8 sep 2026, 1:25 p. m.") — mismo formato en la tarjeta del documento y
// en el historial de versiones.
const formatearFecha = (iso) => iso ? new Date(iso).toLocaleString('es-CO', {
  day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit',
}) : null

/* ─── "Campos antiguos": secciones/columnas que se quitaron de la plantilla pero conservan
   respuestas guardadas — se archivan en vez de borrarse (ver FormularioPlantillaService), y
   desde aquí el admin puede verlas y, si quiere, volver a activarlas. ─── */
function CamposAntiguos({ idTipoProgreso, onRestaurado, onError }) {
  const [abierto, setAbierto] = useState(false)
  const [items, setItems] = useState(null)
  const [restaurando, setRestaurando] = useState(null)

  const cargar = () => {
    formulariosService.obtenerArchivados(idTipoProgreso)
      .then(setItems)
      .catch(() => onError('No se pudieron cargar los campos antiguos.'))
  }

  useEffect(() => {
    if (abierto && items === null) cargar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto])

  const restaurar = async (item) => {
    const clave = item.tipo === 'SECCION' ? `s-${item.idSeccion}` : `c-${item.idColumna}`
    setRestaurando(clave)
    try {
      if (item.tipo === 'SECCION') {
        await formulariosService.restaurarSeccion(idTipoProgreso, item.idSeccion)
      } else {
        await formulariosService.restaurarColumna(idTipoProgreso, item.idColumna)
      }
      cargar()
      onRestaurado()
    } catch {
      onError('No se pudo restaurar ese campo.')
    } finally {
      setRestaurando(null)
    }
  }

  return (
    <div className="mb-6 bg-white border border-gray-200 rounded-2xl overflow-hidden">
      <button type="button" onClick={() => setAbierto(v => !v)}
        className="w-full flex items-center gap-3 px-5 py-3.5 text-left hover:bg-gray-50 transition-colors">
        <span className="flex-shrink-0 w-8 h-8 rounded-xl bg-gray-100 text-gray-500 flex items-center justify-center">
          <IconoArchivo />
        </span>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-gray-700">Campos antiguos</p>
          <p className="text-xs text-gray-400">Secciones y columnas que se quitaron pero conservan respuestas guardadas.</p>
        </div>
        {items !== null && items.length > 0 && (
          <span className="flex-shrink-0 px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 text-xs font-semibold">{items.length}</span>
        )}
        <span className={`flex-shrink-0 text-gray-400 transition-transform ${abierto ? 'rotate-180' : ''}`}>
          <IconoFlechaAbajo />
        </span>
      </button>
      {abierto && (
        <div className="border-t border-gray-100 px-5 py-3">
          {items === null ? (
            <p className="text-sm text-gray-400 py-2">Cargando…</p>
          ) : items.length === 0 ? (
            <p className="text-sm text-gray-400 py-2">No hay campos archivados.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {items.map(item => {
                const clave = item.tipo === 'SECCION' ? `s-${item.idSeccion}` : `c-${item.idColumna}`
                return (
                  <li key={clave} className="flex items-center justify-between gap-3 py-2.5">
                    <p className="text-sm text-gray-700 min-w-0 truncate">
                      {item.tipo === 'SECCION'
                        ? <>Sección completa: <span className="font-medium">{item.nombreSeccion}</span></>
                        : <><span className="font-medium">{item.nombreColumna}</span> <span className="text-gray-400">— {item.nombreSeccion}</span></>}
                    </p>
                    <button type="button" onClick={() => restaurar(item)} disabled={restaurando === clave}
                      className="flex-shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-primary-50 text-primary-700 hover:bg-primary-100 disabled:opacity-50 transition-colors">
                      <IconoRestaurar />
                      {restaurando === clave ? 'Restaurando…' : 'Restaurar'}
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

/* ─── Documento Word con marcadores: el administrador sube un .docx diseñado en Word (con su
   propio logo, tipografía, etc. — el diseño vive dentro del documento subido, no aquí). El
   constructor de secciones/columnas de abajo es lo que define qué marcadores existen y qué llena
   el analista. ─── */
const PlantillaWordPanel = forwardRef(function PlantillaWordPanel(
  { idTipoProgreso, nombreSubproceso, secciones, obtenerPayload, onError, onAnalizado }, ref) {
  const [config, setConfig] = useState(null)
  const [subiendo, setSubiendo] = useState(false)
  const [descargandoDocx, setDescargandoDocx] = useState(false)
  const [analisis, setAnalisis] = useState(null)
  const [analizando, setAnalizando] = useState(false)
  const [disponiblesCandidato, setDisponiblesCandidato] = useState([])
  const [mostrarHistorial, setMostrarHistorial] = useState(false)
  const [historial, setHistorial] = useState(null)
  const [descargandoVersion, setDescargandoVersion] = useState(null)
  const [eliminandoVersion, setEliminandoVersion] = useState(null)

  useEffect(() => {
    formulariosService.obtenerConfigPlantilla(idTipoProgreso).then(setConfig).catch(() => {})
    formulariosService.obtenerCamposCandidatoDisponibles().then(setDisponiblesCandidato).catch(() => {})
  }, [idTipoProgreso])

  const cargarHistorial = async () => {
    const nuevoEstado = !mostrarHistorial
    setMostrarHistorial(nuevoEstado)
    if (nuevoEstado && historial === null) {
      try {
        setHistorial(await formulariosService.obtenerHistorialPlantillaWord(idTipoProgreso))
      } catch {
        setHistorial([])
        onError('No se pudo cargar el historial de versiones.')
      }
    }
  }

  const descargarVersion = async (version) => {
    setDescargandoVersion(version.idHistorial ?? 'actual')
    try {
      const blob = version.idHistorial == null
        ? await formulariosService.descargarPlantillaWord(idTipoProgreso)
        : await formulariosService.descargarVersionHistorialPlantillaWord(idTipoProgreso, version.idHistorial)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = version.nombreArchivo || 'plantilla.docx'
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      onError('No se pudo descargar esa versión.')
    } finally {
      setDescargandoVersion(null)
    }
  }

  // Solo versiones archivadas (idHistorial != null) — la activa se quita con "Quitar plantilla"
  // más arriba en el panel, no desde aquí.
  const eliminarVersion = async (version) => {
    if (!window.confirm(`¿Borrar "${version.nombreArchivo}" del historial? Esto elimina el documento y no se puede deshacer.`)) return
    setEliminandoVersion(version.idHistorial)
    try {
      await formulariosService.eliminarVersionHistorialPlantillaWord(idTipoProgreso, version.idHistorial)
      setHistorial(prev => prev.filter(v => v.idHistorial !== version.idHistorial))
    } catch {
      onError('No se pudo borrar esa versión.')
    } finally {
      setEliminandoVersion(null)
    }
  }

  const subir = async (archivo) => {
    if (!archivo) return
    setSubiendo(true)
    setAnalisis(null)
    try {
      const c = await formulariosService.subirPlantillaWord(idTipoProgreso, archivo)
      setConfig(c)
      setHistorial(null)
      setMostrarHistorial(false)
    } catch (err) {
      onError(err.response?.data?.mensaje ?? 'No se pudo subir el documento. Verifica que sea un .docx válido.')
    } finally {
      setSubiendo(false)
    }
  }

  const descargarDocx = async () => {
    setDescargandoDocx(true)
    try {
      const blob = await formulariosService.descargarPlantillaWord(idTipoProgreso)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = nombreArchivo(config?.plantillaWordRuta) || 'plantilla.docx'
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      onError('No se pudo descargar el documento.')
    } finally {
      setDescargandoDocx(false)
    }
  }

  const analizar = async () => {
    setAnalizando(true)
    setAnalisis(null)
    try {
      const resultado = await formulariosService.analizarPlantillaWord(idTipoProgreso, obtenerPayload())
      setAnalisis(resultado)
      onAnalizado?.(resultado)
    } catch (err) {
      onError(err.response?.data?.mensaje ?? 'No se pudo analizar el documento.')
    } finally {
      setAnalizando(false)
    }
  }

  // Expuesto al padre para re-analizar solo, apenas se guarda la plantilla — así un admin que
  // borra/renombra un campo se entera de inmediato si dejó un marcador huérfano en el .docx ya
  // subido, sin depender de acordarse de darle a "Analizar" a mano (ver confirmarGuardar() más
  // abajo). No hace nada si todavía no hay documento subido.
  useImperativeHandle(ref, () => ({
    analizarSiCorresponde: () => { if (config?.plantillaWordRuta) analizar() },
  }))

  const quitar = async () => {
    setAnalisis(null)
    try {
      await formulariosService.quitarPlantillaWord(idTipoProgreso)
      setConfig(prev => ({ ...prev, plantillaWordRuta: null, fechaCarga: null, subidoPor: null }))
      setHistorial(null)
      setMostrarHistorial(false)
    } catch {
      onError('No se pudo quitar el documento.')
    }
  }

  // Mismo patrón de PDF que SemaforoServicios.jsx (jsPDF + jspdf-autotable) — se genera en el
  // navegador a partir de lo que ya devolvió /plantilla-word/analisis, sin pedirle nada nuevo al
  // backend.
  const descargarReporte = () => {
    if (!analisis) return
    const g = crearGeneradorPdf('Reporte de análisis — Plantilla Word')

    if (sinDiferencias) {
      g.doc.setFontSize(10); g.doc.setFont('helvetica', 'bold'); g.doc.setTextColor(21, 128, 61)
      g.doc.text('El documento coincide con los campos del constructor — no se encontraron diferencias.', g.MAR, g.y())
    } else {
      const tablasMalUsadas = analisis.tablasMalUsadas ?? []
      if (tablasMalUsadas.length > 0) {
        g.titulo(`Tablas mal puestas — no se van a repetir (${tablasMalUsadas.length})`, [185, 28, 28])
        g.tabla(['Marcador'], tablasMalUsadas.map(m => [`\${${m}}`]), [185, 28, 28])
        g.doc.setFontSize(7); g.doc.setFont('helvetica', 'italic'); g.doc.setTextColor(153, 27, 27)
        g.doc.text('Van dentro de un comentario repeatTableRow(...) sobre la fila de la tabla, no sueltos en el texto.', g.MAR, g.y() - 4)
      }

      g.titulo(`Marcadores sin campo en el constructor (${analisis.marcadoresDesconocidos.length})`, [185, 28, 28])
      g.tabla(['Marcador'], analisis.marcadoresDesconocidos.length > 0
        ? analisis.marcadoresDesconocidos.map(m => [`\${${m}}`]) : [['(ninguno)']], [185, 28, 28])

      g.titulo(`Campos sin marcador en el documento (${analisis.camposSinUsar.length})`, [180, 83, 9])
      g.tabla(['Campo'], analisis.camposSinUsar.length > 0
        ? analisis.camposSinUsar.map(m => [`\${${m}}`]) : [['(ninguno)']], [180, 83, 9])
    }

    g.guardar('reporte-analisis')
  }

  // El manual completo (guía + todos los marcadores disponibles) — siempre descargable, no
  // depende de haber corrido "Analizar" (eso es solo para comparar contra el .docx ya subido).
  const descargarManualMarcadores = () => {
    const g = crearGeneradorPdf('Manual de marcadores — Plantilla Word')

    g.titulo('Cómo armar tu plantilla en Word', [67, 56, 202])
    const pasos = [
      'Escribe cada marcador tal cual — mayúsculas y comillas incluidas — en el texto del documento.',
      "Para un campo simple: ${campos['clave']} o ${candidato['CLAVE']} — se reemplaza por el valor real.",
      'Para una tabla: arma la tabla en Word con su fila de encabezado normal, y UNA sola fila de datos debajo — una celda por columna, sin combinar/fusionar celdas entre filas. Esa fila de datos es la que se repite tal cual, no un bloque de varias filas.',
      "En esa fila de DATOS, escribe en cada celda la clave entre corchetes: ${['clave']} — nunca ${campos[...]} ni ${tablas[...]} adentro de la fila.",
      "Selecciona esa fila completa → clic derecho → Nuevo comentario → escribe EXACTAMENTE (sin \"${}\" alrededor): repeatTableRow(tablas['clave_de_la_tabla']) — escribir ${tablas['clave_de_la_tabla']} ahí en vez de eso es el error más común: el comentario queda como letra muerta, no repite nada.",
      'Una columna tipo Archivo (📎) incrusta la imagen solo si el analista subió exactamente un .jpg/.jpeg/.png — si no, se ve como texto.',
    ]
    g.parrafos(pasos.map((paso, i) => `${i + 1}. ${paso}`))

    g.titulo('¿El diseño de cada registro es un "cuadro" de varias filas?', [180, 83, 9])
    g.parrafos([
      'Si para cada registro ya armaste un bloque de varias filas con celdas combinadas (una tarjeta/formulario) en vez de una fila sencilla, repeatTableRow no sirve — repite exactamente UNA fila, nunca un bloque de varias.',
      'La forma confiable de resolverlo es rehacer esa parte como una tabla simple: una fila de encabezado + una sola fila de datos con una celda por columna (ver pasos arriba) — comprobado que funciona.',
    ])

    g.titulo('Dónde SÍ funcionan los marcadores', [21, 128, 61])
    g.parrafos([
      '• En un párrafo normal del cuerpo del documento.',
      '• Dentro de una celda de una tabla normal de Word (Insertar → Tabla).',
      '• Dentro de encabezados o pies de página del documento.',
      '• Dentro de un Cuadro de texto (Insertar → Cuadro de texto) — el marcador SÍ se reemplaza bien, confirmado con una prueba automática.',
    ])

    g.titulo('Imágenes', [67, 56, 202])
    g.parrafos([
      "Una columna tipo Archivo (📎) con el marcador puesto en un párrafo o celda normal incrusta la imagen de verdad en el documento — no hace falta nada especial, solo que el analista haya subido exactamente un .jpg/.jpeg/.png en esa columna (con varios adjuntos, o un PDF, se ve como texto con el nombre del archivo en vez de la imagen).",
    ])

    g.titulo(`Datos del candidato — siempre disponibles (${disponiblesCandidato.length})`, [55, 65, 81])
    g.tabla(['Marcador', 'Campo'],
      disponiblesCandidato.map(c => [`\${candidato['${c.clave}']}`, c.etiqueta]),
      [55, 65, 81], { columnStyles: { 0: { cellWidth: 75 } } })

    g.titulo('Hoja de vida del candidato — tablas fijas, siempre disponibles', [185, 28, 28])
    TABLAS_HOJA_VIDA.forEach(t => {
      g.subtitulo(`tablas['${t.clave}']`, t.etiqueta, [185, 28, 28])
      g.tabla(['Marcador por fila (dentro de repeatTableRow)', 'Columna'],
        t.columnas.map(c => [`\${['${c}']}`, c]), [185, 28, 28])
    })

    if (marcadoresCampos.length > 0) {
      g.titulo(`Campos de esta plantilla (${marcadoresCampos.length})`, [67, 56, 202])
      g.tabla(['Marcador', 'Campo'],
        marcadoresCampos.map(m => [`\${campos['${m.clave || '?'}']}`, m.etiqueta]),
        [67, 56, 202], { columnStyles: { 0: { cellWidth: 75 } } })
    }

    if (marcadoresTablas.length > 0) {
      g.titulo(`Tus secciones tipo tabla (${marcadoresTablas.length})`, [109, 40, 217])
      marcadoresTablas.forEach(s => {
        g.subtitulo(`tablas['${s.clave || '?'}']`, s.nombre, [109, 40, 217])
        g.tabla(['Marcador por fila (dentro de repeatTableRow)', 'Columna'],
          s.columnas.map(c => [`\${['${c.clave || '?'}']}`, c.nombre || '(sin título)']), [109, 40, 217])
      })
    }

    if (marcadoresCampos.length === 0 && marcadoresTablas.length === 0) {
      g.doc.setFontSize(9); g.doc.setFont('helvetica', 'italic'); g.doc.setTextColor(148, 163, 184)
      g.doc.text('Todavía no hay columnas propias definidas en el constructor de abajo.', g.MAR, g.y())
    }

    g.guardar('manual-marcadores')
  }

  // Generador de PDF compartido entre el manual y el reporte de análisis — mismo encabezado,
  // mismo manejo de salto de página, mismo estilo de tabla.
  const crearGeneradorPdf = (tituloEncabezado) => {
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
    const MAR = 14; const ANCHO = doc.internal.pageSize.getWidth(); const ALTO = doc.internal.pageSize.getHeight()
    let y = 26

    doc.setFillColor(124, 58, 237)
    doc.rect(0, 0, ANCHO, 20, 'F')
    doc.setFontSize(14); doc.setFont('helvetica', 'bold'); doc.setTextColor(255, 255, 255)
    doc.text(tituloEncabezado, MAR, 12)
    doc.setFontSize(8); doc.setFont('helvetica', 'normal'); doc.setTextColor(100, 116, 139)
    doc.text(`${nombreSubproceso ?? 'Plantilla'} · Generado: ${new Date().toLocaleString('es-CO')}`, MAR, y)
    y += 8

    // Si no cabe ni el título de la próxima sección, salta de página primero.
    const espacioAntesDe = (alturaMinima) => {
      if (y + alturaMinima > ALTO - 14) { doc.addPage(); y = 20 }
    }
    const titulo = (texto, color) => {
      espacioAntesDe(14)
      doc.setFontSize(10); doc.setFont('helvetica', 'bold'); doc.setTextColor(...color)
      doc.text(texto, MAR, y); y += 2
    }
    const subtitulo = (marcador, etiqueta, color) => {
      espacioAntesDe(9)
      doc.setFontSize(9); doc.setFont('courier', 'bold'); doc.setTextColor(...color)
      doc.text(marcador, MAR, y)
      doc.setFont('helvetica', 'normal'); doc.setTextColor(148, 163, 184)
      doc.text(` — ${etiqueta}`, MAR + doc.getTextWidth(marcador), y)
      y += 5
    }
    const parrafos = (lineasTexto) => {
      doc.setFontSize(8); doc.setFont('helvetica', 'normal'); doc.setTextColor(55, 65, 81)
      lineasTexto.forEach(linea => {
        const lineas = doc.splitTextToSize(linea, ANCHO - MAR * 2)
        espacioAntesDe(lineas.length * 4 + 2)
        doc.text(lineas, MAR, y); y += lineas.length * 4 + 2
      })
      y += 2
    }
    const tabla = (head, body, color, opts = {}) => {
      autoTable(doc, {
        startY: y, margin: { left: MAR, right: MAR },
        head: [head], body,
        theme: 'grid', styles: { fontSize: 8, font: opts.font ?? 'courier' },
        headStyles: { fillColor: color, textColor: 255, fontStyle: 'bold' },
        columnStyles: opts.columnStyles,
      })
      y = doc.lastAutoTable.finalY + 8
    }
    const guardar = (prefijoArchivo) => {
      const nombreBase = (nombreSubproceso ?? 'plantilla').toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')
        .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
      doc.save(`${prefijoArchivo}-${nombreBase || 'plantilla'}.pdf`)
    }

    return { doc, MAR, y: () => y, titulo, subtitulo, parrafos, tabla, guardar }
  }

  const marcadoresCampos = secciones.filter(s => s.tipo === 'CAMPOS')
    .flatMap(s => s.columnas.map(c => ({ clave: c.clave, etiqueta: c.nombre || '(sin título)' })))
  // Las de origen automático ya salen en el bloque "Hoja de vida del candidato" del manual en PDF
  // — no repetirlas aquí.
  const marcadoresTablas = secciones.filter(s => s.tipo === 'TABLA' && !s.origenFijo)

  const nombreArchivo = (ruta) => {
    if (!ruta) return null
    const base = ruta.split('/').pop()
    return base.replace(/^[0-9a-fA-F-]{36}_/, '')
  }

  const sinDiferencias = analisis && analisis.marcadoresDesconocidos.length === 0 && analisis.camposSinUsar.length === 0
    && (analisis.tablasMalUsadas ?? []).length === 0

  return (
    <div className="mb-5 bg-white border border-gray-200 rounded-2xl shadow-sm p-5">
      <div className="flex items-center gap-2.5 mb-4">
        <div className="flex-shrink-0 w-9 h-9 rounded-xl bg-gradient-to-br from-red-500 to-red-600 text-white flex items-center justify-center shadow-sm shadow-red-200">
          <IconoImagen />
        </div>
        <div>
          <p className="text-sm font-semibold text-gray-800">Documento Word con marcadores</p>
          <p className="text-[11px] text-gray-400">Define cómo se ve el documento final — el constructor de abajo define los campos</p>
        </div>
      </div>

      <div>
        {config?.plantillaWordRuta ? (
          <div className="flex items-center gap-3 bg-red-50/60 border border-red-100 rounded-xl px-4 py-3">
            <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-white border border-red-100 text-red-500 flex items-center justify-center">
              <IconoArchivo />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-800 truncate">{nombreArchivo(config.plantillaWordRuta)}</p>
              <p className="text-[11px] text-gray-400">
                Documento activo — define el diseño del generado final
                {config.fechaCarga && <> · Subido el {formatearFecha(config.fechaCarga)}{config.subidoPor ? ` por ${config.subidoPor}` : ''}</>}
              </p>
            </div>
            <div className="flex items-center gap-1 flex-shrink-0">
              <button type="button" onClick={analizar} disabled={analizando}
                className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-white border border-red-200 text-red-700 hover:bg-red-50 disabled:opacity-50 transition-colors">
                <IconoLupa /> {analizando ? 'Analizando…' : 'Analizar'}
              </button>
              <button type="button" onClick={cargarHistorial}
                className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                  mostrarHistorial ? 'bg-red-100 border-red-200 text-red-700' : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
                <IconoReloj /> Historial
              </button>
              <button type="button" onClick={descargarDocx} disabled={descargandoDocx} title="Descargar el documento tal como lo subiste"
                className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-white border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                <IconoDescargar /> {descargandoDocx ? 'Descargando…' : 'Descargar'}
              </button>
              <label className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-white border border-gray-200 text-gray-600 hover:bg-gray-50 cursor-pointer transition-colors">
                {subiendo ? 'Subiendo…' : 'Cambiar'}
                <input type="file" accept=".docx" className="hidden" disabled={subiendo}
                  onChange={e => subir(e.target.files[0])} />
              </label>
              <button type="button" onClick={quitar} title="Quitar documento"
                className="p-1.5 rounded-lg text-gray-400 hover:bg-red-50 hover:text-red-600 transition-colors">
                <IconoX />
              </button>
            </div>
          </div>
        ) : (
          <label className="flex flex-col items-center justify-center gap-2 border-2 border-dashed border-red-200 rounded-xl py-7 cursor-pointer hover:border-red-300 hover:bg-red-50/40 transition-colors">
            <div className="w-10 h-10 rounded-full bg-red-50 text-red-500 flex items-center justify-center">
              <IconoSubir />
            </div>
            <p className="text-sm font-semibold text-red-700">{subiendo ? 'Subiendo…' : 'Subir documento (.docx)'}</p>
            <p className="text-[11px] text-gray-400 text-center px-4">
              Diseña el documento en Word con marcadores y súbelo aquí — no se podrá generar el documento final hasta que subas uno.
            </p>
            <input type="file" accept=".docx" className="hidden" disabled={subiendo}
              onChange={e => subir(e.target.files[0])} />
          </label>
        )}

        {mostrarHistorial && (
          <div className="mt-3 bg-gray-50 border border-gray-100 rounded-xl p-3">
            <p className="text-xs font-semibold text-gray-600 mb-2">Historial de versiones</p>
            {historial === null ? (
              <p className="text-xs text-gray-400 py-1">Cargando…</p>
            ) : historial.length === 0 ? (
              <p className="text-xs text-gray-400 py-1">No hay versiones registradas todavía.</p>
            ) : (
              <ul className="divide-y divide-gray-200">
                {historial.map((v) => (
                  <li key={v.idHistorial ?? 'actual'} className="flex items-center justify-between gap-3 py-2">
                    <div className="min-w-0">
                      <p className="text-xs font-medium text-gray-700 truncate">
                        {v.nombreArchivo}
                        {v.actual && <span className="ml-1.5 px-1.5 py-0.5 rounded-full bg-red-100 text-red-700 text-[10px] font-semibold">actual</span>}
                      </p>
                      <p className="text-[11px] text-gray-400">
                        {v.fechaCarga ? formatearFecha(v.fechaCarga) : 'Fecha no registrada'}
                        {v.subidoPor ? ` · ${v.subidoPor}` : ''}
                      </p>
                    </div>
                    <div className="flex-shrink-0 flex items-center gap-1.5">
                      <button type="button" onClick={() => descargarVersion(v)}
                        disabled={descargandoVersion === (v.idHistorial ?? 'actual')}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium bg-white border border-gray-200 text-gray-600 hover:bg-gray-100 disabled:opacity-50 transition-colors">
                        <IconoDescargar /> {descargandoVersion === (v.idHistorial ?? 'actual') ? 'Descargando…' : 'Descargar'}
                      </button>
                      {/* Solo versiones archivadas — la activa se quita con "Quitar plantilla" arriba. */}
                      {v.idHistorial != null && (
                        <button type="button" onClick={() => eliminarVersion(v)}
                          disabled={eliminandoVersion === v.idHistorial}
                          title="Borrar esta versión del historial"
                          className="p-1.5 rounded-lg text-gray-400 hover:bg-red-50 hover:text-red-600 disabled:opacity-50 transition-colors">
                          <IconoPapelera />
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {analisis && (
          sinDiferencias ? (
            <div className="mt-3 flex items-center gap-2 text-xs text-green-700 bg-green-50 border border-green-100 rounded-lg px-3 py-2">
              <IconoCheckCirculo /> El documento coincide con los campos del constructor — no se encontraron diferencias.
            </div>
          ) : (
            <div className="mt-3 space-y-2">
              {(analisis.tablasMalUsadas ?? []).length > 0 && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs">
                  <p className="flex items-center gap-1.5 font-semibold text-red-700 mb-1.5">
                    <IconoAlertaTriangulo /> Tablas mal puestas — no se van a repetir ({analisis.tablasMalUsadas.length})
                  </p>
                  <ul className="font-mono text-red-600 space-y-0.5 pl-5 mb-2">
                    {analisis.tablasMalUsadas.map(m => <li key={m}>${'{'}{m}{'}'}</li>)}
                  </ul>
                  <p className="text-red-700">
                    No van a repetir ninguna fila. Dos causas posibles: están sueltos en el texto del documento (sale
                    el listado completo como texto corrido), o están dentro de un comentario pero con esta misma
                    sintaxis <span className="font-mono">{'${tablas[...]}'}</span> en vez de la función real
                    <span className="font-mono"> repeatTableRow(tablas['clave'])</span> — ese comentario no hace
                    nada. Además, la fila que se repite debe ser UNA sola fila con una celda por columna, nunca un
                    bloque de varias filas combinadas (celdas fusionadas) — descarga el manual de marcadores más
                    abajo para ver la guía completa.
                  </p>
                </div>
              )}
              {analisis.marcadoresDesconocidos.length > 0 && (
                <div className="bg-red-50 border border-red-100 rounded-lg p-3 text-xs">
                  <p className="flex items-center gap-1.5 font-semibold text-red-700 mb-1.5">
                    <IconoAlertaTriangulo /> Marcadores sin campo en el constructor ({analisis.marcadoresDesconocidos.length})
                  </p>
                  <ul className="font-mono text-red-600 space-y-0.5 pl-5">
                    {analisis.marcadoresDesconocidos.map(m => <li key={m}>${'{'}{m}{'}'}</li>)}
                  </ul>
                </div>
              )}
              {analisis.camposSinUsar.length > 0 && (
                <div className="bg-amber-50 border border-amber-100 rounded-lg p-3 text-xs">
                  <p className="flex items-center gap-1.5 font-semibold text-amber-700 mb-1.5">
                    <IconoAlertaTriangulo /> Campos sin marcador en el documento ({analisis.camposSinUsar.length})
                  </p>
                  <ul className="font-mono text-amber-700 space-y-0.5 pl-5">
                    {analisis.camposSinUsar.map(m => <li key={m}>${'{'}{m}{'}'}</li>)}
                  </ul>
                </div>
              )}
            </div>
          )
        )}
        {analisis && (
          <button type="button" onClick={descargarReporte}
            className="mt-2 inline-flex items-center gap-1.5 text-xs text-gray-500 hover:text-gray-700 font-medium">
            <IconoArchivo /> Descargar reporte (PDF)
          </button>
        )}
      </div>

      <div className="mt-4 pt-4 border-t border-gray-100 flex items-center justify-between gap-3">
        <p className="text-[11px] text-gray-400">
          Guía paso a paso + los {disponiblesCandidato.length || 26} campos del candidato, las tablas fijas de hoja de
          vida y los campos/tablas propios de esta plantilla.
        </p>
        <button type="button" onClick={descargarManualMarcadores}
          className="flex-shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-gray-800 text-white hover:bg-gray-900 transition-colors">
          <IconoDescargar /> Descargar manual de marcadores (PDF)
        </button>
      </div>
    </div>
  )
})

function VolverCatalogo() {
  return (
    <Link to="/admin/catalogo" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors">
      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
      </svg>
      Catálogo
    </Link>
  )
}

export default function PlantillaFormulario() {
  const { idTipoProgreso } = useParams()
  const [subproceso, setSubproceso] = useState(null)
  const [errorSubproceso, setErrorSubproceso] = useState(null)

  useEffect(() => {
    api.get('/catalogo/tipos-progreso')
      .then(r => {
        const encontrado = r.data.find(t => String(t.idTipoProgreso) === idTipoProgreso)
        if (!encontrado) { setErrorSubproceso('Subproceso no encontrado.'); return }
        setSubproceso(encontrado)
      })
      .catch(() => setErrorSubproceso('No se pudo cargar el subproceso.'))
  }, [idTipoProgreso])

  if (errorSubproceso) {
    return (
      <div className="max-w-4xl mx-auto">
        <VolverCatalogo />
        <div className="mt-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{errorSubproceso}</div>
      </div>
    )
  }

  if (subproceso === null) {
    return (
      <div className="flex justify-center py-16">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  return <PlantillaFormularioContenido subproceso={subproceso} />
}

function PlantillaFormularioContenido({ subproceso }) {
  const [secciones, setSecciones] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [generandoPreview, setGenerandoPreview] = useState(false)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [impactoPendiente, setImpactoPendiente] = useState(null)
  const [disponiblesCandidato, setDisponiblesCandidato] = useState([])
  const [disponiblesCliente, setDisponiblesCliente] = useState([])
  const [tiposDisponibles, setTiposDisponibles] = useState([])
  const [origenDuplicar, setOrigenDuplicar] = useState('')
  const [duplicando, setDuplicando] = useState(false)
  const panelWordRef = useRef(null)

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  // Se llama después de cada guardado (ver confirmarGuardar) cuando ya hay un .docx subido — si
  // el análisis automático encuentra un marcador huérfano (un campo/sección que se acaba de
  // borrar o renombrar y el documento Word todavía lo usa), se avisa aquí mismo, sin que el
  // admin tenga que abrir el panel de marcadores para darse cuenta.
  const alAnalizarAutomatico = (resultado) => {
    const hayProblemas = resultado.marcadoresDesconocidos.length > 0
      || resultado.camposSinUsar.length > 0
      || (resultado.tablasMalUsadas ?? []).length > 0
    if (hayProblemas) {
      mostrarToast('Guardado, pero el documento Word ya subido quedó con marcadores que no coinciden — revisa el panel de abajo antes de que alguien intente imprimir.', 'error')
    }
  }

  useEffect(() => {
    formulariosService.obtenerCamposCandidatoDisponibles().then(setDisponiblesCandidato).catch(() => {})
    formulariosService.obtenerCamposClienteDisponibles().then(setDisponiblesCliente).catch(() => {})
    api.get('/catalogo/tipos-progreso').then(r => setTiposDisponibles(r.data)).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subproceso.idTipoProgreso])

  const cargarSecciones = () => {
    formulariosService.obtenerEstructura(subproceso.idTipoProgreso)
      .then(data => setSecciones(data.map(s => ({
        localId: idLocal(),
        idSeccion: s.idSeccion,
        nombre: s.nombre,
        tipo: s.tipo,
        clave: s.clave ?? '',
        origenFijo: s.origenFijo ?? '',
        columnasOcultas: s.columnasOcultas ?? [],
        columnas: s.columnas.map(c => ({ localId: idLocal(), idColumna: c.idColumna, nombre: c.nombre ?? '', tipoDato: c.tipoDato, campoCandidato: c.campoCandidato ?? '', campoCliente: c.campoCliente ?? '', clave: c.clave ?? '', opcionesTexto: (c.opciones ?? []).join(', ') })),
        filasSugeridas: s.filasSugeridas.map(f => ({ localId: idLocal(), etiqueta: f.etiqueta })),
      }))))
      .catch(() => setError('No se pudo cargar la plantilla.'))
  }

  useEffect(() => {
    cargarSecciones()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subproceso.idTipoProgreso])

  const actualizarSecciones = (fn) => setSecciones(prev => fn(prev))

  // Trae la estructura de OTRA plantilla ya armada como punto de partida — sin idSeccion/idColumna
  // (se guardan como secciones/columnas nuevas de esta plantilla, no tocan la de origen).
  const duplicarDesde = async (idTipoProgresoOrigen) => {
    if (!idTipoProgresoOrigen) return
    setDuplicando(true)
    try {
      const data = await formulariosService.obtenerEstructura(idTipoProgresoOrigen)
      setSecciones(data.map(s => ({
        localId: idLocal(),
        idSeccion: null,
        nombre: s.nombre,
        tipo: s.tipo,
        clave: s.clave ?? '',
        origenFijo: s.origenFijo ?? '',
        columnasOcultas: s.columnasOcultas ?? [],
        columnas: s.columnas.map(c => ({ localId: idLocal(), idColumna: null, nombre: c.nombre ?? '', tipoDato: c.tipoDato, campoCandidato: c.campoCandidato ?? '', campoCliente: c.campoCliente ?? '', clave: c.clave ?? '', opcionesTexto: (c.opciones ?? []).join(', ') })),
        filasSugeridas: s.filasSugeridas.map(f => ({ localId: idLocal(), etiqueta: f.etiqueta })),
      })))
      mostrarToast('Estructura duplicada — revísala y dale Guardar cuando esté lista.')
    } catch {
      mostrarToast('No se pudo duplicar esa plantilla.', 'error')
    } finally {
      setDuplicando(false)
    }
  }

  const agregarSeccion = () => actualizarSecciones(prev => [
    ...prev, { localId: idLocal(), nombre: '', tipo: 'CAMPOS', clave: '', origenFijo: '', columnasOcultas: [], columnas: [], filasSugeridas: [] },
  ])
  const quitarSeccion = (sId) => actualizarSecciones(prev => prev.filter(s => s.localId !== sId))
  const moverSeccion = (idx, delta) => actualizarSecciones(prev => {
    const destino = idx + delta
    if (destino < 0 || destino >= prev.length) return prev
    const nuevo = [...prev]
    ;[nuevo[idx], nuevo[destino]] = [nuevo[destino], nuevo[idx]]
    return nuevo
  })
  const editarSeccion = (sId, campo, valor) => actualizarSecciones(prev =>
    prev.map(s => s.localId === sId ? { ...s, [campo]: valor } : s))

  // Al elegir un origen automático, se sugiere el nombre (si está vacío) y se vacían columnas/
  // filas sugeridas — dejan de aplicar, la forma de los datos ya está fija (ver TABLAS_HOJA_VIDA).
  // Al volver a "Manual" se limpia el origen para que el admin arme sus propias columnas.
  const elegirOrigenFijo = (sId, origenFijo) => actualizarSecciones(prev => prev.map(s => {
    if (s.localId !== sId) return s
    const tabla = TABLAS_HOJA_VIDA.find(t => t.clave === origenFijo)
    return {
      ...s,
      origenFijo,
      nombre: origenFijo && !s.nombre.trim() ? tabla.etiqueta : s.nombre,
      columnasOcultas: origenFijo ? s.columnasOcultas : [],
      columnas: origenFijo ? [] : s.columnas,
      filasSugeridas: origenFijo ? [] : s.filasSugeridas,
    }
  }))

  // Toggle de visibilidad de una columna fija en el formulario del analista — no afecta los
  // marcadores disponibles en el .docx, esa columna se sigue resolviendo igual.
  const toggleColumnaOculta = (sId, claveColumna) => actualizarSecciones(prev => prev.map(s => {
    if (s.localId !== sId) return s
    const oculta = s.columnasOcultas.includes(claveColumna)
    return { ...s, columnasOcultas: oculta ? s.columnasOcultas.filter(c => c !== claveColumna) : [...s.columnasOcultas, claveColumna] }
  }))

  const agregarColumna = (sId) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: [...s.columnas, { localId: idLocal(), nombre: '', tipoDato: 'TEXTO', campoCandidato: '', campoCliente: '', opcionesTexto: '' }] } : s))
  const quitarColumna = (sId, cId) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: s.columnas.filter(c => c.localId !== cId) } : s))
  const editarColumna = (sId, cId, campo, valor) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: s.columnas.map(c => c.localId === cId ? { ...c, [campo]: valor } : c) } : s))

  // Al elegir un dato automático (candidato o cliente — mutuamente excluyentes, solo uno de los
  // dos queda con valor), si el título todavía está vacío se sugiere la etiqueta del campo (ej:
  // "N° de documento") — el admin la puede borrar o cambiar libremente después. tipo vacío = volver
  // a campo manual, limpia ambos.
  const elegirCampoAutomatico = (sId, cId, tipo, clave, etiqueta) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: s.columnas.map(c => c.localId === cId
      ? { ...c, campoCandidato: tipo === 'candidato' ? clave : '', campoCliente: tipo === 'cliente' ? clave : '',
          nombre: clave && !c.nombre.trim() ? etiqueta : c.nombre }
      : c) } : s))

  const agregarFilaSugerida = (sId) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, filasSugeridas: [...s.filasSugeridas, { localId: idLocal(), etiqueta: '' }] } : s))
  const quitarFilaSugerida = (sId, fId) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, filasSugeridas: s.filasSugeridas.filter(f => f.localId !== fId) } : s))
  const editarFilaSugerida = (sId, fId, valor) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, filasSugeridas: s.filasSugeridas.map(f => f.localId === fId ? { ...f, etiqueta: valor } : f) } : s))

  const validar = () => {
    const camposAutomaticosUsados = new Set()
    for (const s of secciones) {
      if (!s.nombre.trim()) return 'Todas las secciones necesitan un nombre.'
      if (!s.origenFijo && s.columnas.length === 0) return `La sección "${s.nombre}" necesita al menos una columna.`
      const nombresUsadosEnSeccion = new Set()
      for (const c of s.columnas) {
        if (c.tipoDato === 'LISTA' && !(c.opcionesTexto || '').split(',').map(o => o.trim()).filter(Boolean).length) {
          return `La columna "${c.nombre || '(sin título)'}" es de tipo lista desplegable — escribe al menos una opción.`
        }
        const nombreNormalizado = (c.nombre || '').trim().toLowerCase()
        if (nombreNormalizado) {
          if (nombresUsadosEnSeccion.has(nombreNormalizado)) {
            return `La sección "${s.nombre}" tiene dos columnas tituladas "${c.nombre.trim()}" — dale un título distinto a cada una.`
          }
          nombresUsadosEnSeccion.add(nombreNormalizado)
        }
        const campoAutomatico = c.campoCandidato ? `candidato:${c.campoCandidato}` : c.campoCliente ? `cliente:${c.campoCliente}` : null
        if (campoAutomatico) {
          if (camposAutomaticosUsados.has(campoAutomatico)) {
            const lista = c.campoCandidato ? disponiblesCandidato : disponiblesCliente
            const clave = c.campoCandidato || c.campoCliente
            const etiqueta = lista.find(o => o.clave === clave)?.etiqueta ?? clave
            return `El dato "${etiqueta}" ya está usado en otra columna de esta plantilla.`
          }
          camposAutomaticosUsados.add(campoAutomatico)
        }
      }
    }
    return null
  }

  const construirPayload = () => secciones.map(s => ({
    idSeccion: s.idSeccion ?? null,
    nombre: s.nombre.trim(),
    tipo: s.tipo,
    clave: s.clave?.trim() || null,
    origenFijo: s.origenFijo || null,
    columnasOcultas: s.origenFijo ? s.columnasOcultas : [],
    columnas: s.columnas.map(c => ({
      idColumna: c.idColumna ?? null,
      nombre: c.nombre.trim() || null,
      tipoDato: c.tipoDato,
      campoCandidato: c.campoCandidato || null,
      campoCliente: c.campoCliente || null,
      clave: c.clave?.trim() || null,
      opciones: c.tipoDato === 'LISTA' ? (c.opcionesTexto || '').split(',').map(o => o.trim()).filter(Boolean) : null,
    })),
    filasSugeridas: s.tipo === 'TABLA'
      ? s.filasSugeridas.filter(f => f.etiqueta.trim()).map(f => ({ etiqueta: f.etiqueta.trim() }))
      : [],
  }))

  // Antes de guardar de verdad, se revisa si algún campo/sección con respuestas ya guardadas
  // quedaría archivado — si es así, se pide confirmación explícita en vez de guardar directo.
  const intentarGuardar = async () => {
    const mensajeError = validar()
    if (mensajeError) { setError(mensajeError); return }
    setGuardando(true)
    setError(null)
    try {
      const payload = construirPayload()
      const impacto = await formulariosService.verificarCambiosEstructura(subproceso.idTipoProgreso, payload)
      if (impacto.secciones.length > 0 || impacto.columnas.length > 0) {
        setImpactoPendiente({ ...impacto, payload })
        return
      }
      await confirmarGuardar(payload)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo guardar la plantilla.')
    } finally {
      setGuardando(false)
    }
  }

  const confirmarGuardar = async (payload) => {
    setGuardando(true)
    setError(null)
    try {
      await formulariosService.guardarEstructura(subproceso.idTipoProgreso, payload ?? impactoPendiente.payload)
      setImpactoPendiente(null)
      mostrarToast('Plantilla guardada')
      // Si ya hay un .docx subido, se revisa solo apenas se guarda — así un campo/sección que se
      // borró o renombró y dejó un marcador huérfano en el documento se detecta de inmediato, sin
      // esperar a que alguien le dé clic a "Analizar" a mano (o peor, a que un analista se
      // encuentre el error al intentar imprimir).
      panelWordRef.current?.analizarSiCorresponde()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo guardar la plantilla.')
    } finally {
      setGuardando(false)
    }
  }

  const verVistaPrevia = async () => {
    const mensajeError = validar()
    if (mensajeError) { setError(mensajeError); return }
    setGenerandoPreview(true)
    setError(null)
    try {
      const blob = await formulariosService.vistaPrevia(subproceso.idTipoProgreso, construirPayload())
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `vista-previa-${subproceso.nombreProgreso}.docx`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo generar la vista previa.')
    } finally {
      setGenerandoPreview(false)
    }
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-3"><VolverCatalogo /></div>

      <div className="mb-6 rounded-2xl border border-primary-100 bg-gradient-to-br from-primary-50 via-white to-violet-50/50 px-6 py-5">
        <div className="flex items-center gap-4">
          <div className="flex-shrink-0 w-14 h-14 rounded-2xl bg-gradient-to-br from-primary-500 to-primary-600 text-white flex items-center justify-center shadow-md shadow-primary-200">
            <IconoTabla />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[11px] font-semibold uppercase tracking-wider text-primary-500 mb-0.5">Catálogo · Plantilla</p>
            <h2 className="text-2xl font-bold text-gray-900 truncate">{subproceso.nombreProgreso}</h2>
            <p className="text-sm text-gray-500 mt-1 max-w-2xl">
              Arma las secciones, columnas y filas que necesites. El documento Word con marcadores que subas
              abajo se llena solo a partir de esta estructura.
            </p>
          </div>
          {secciones !== null && secciones.length > 0 && (
            <div className="hidden sm:block flex-shrink-0 text-center px-4 py-2 rounded-xl bg-white/80 border border-primary-100">
              <p className="text-lg font-bold text-primary-700 leading-none">{secciones.length}</p>
              <p className="text-[10px] text-gray-400 mt-1">{secciones.length === 1 ? 'sección' : 'secciones'}</p>
            </div>
          )}
        </div>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
      )}

      <PlantillaWordPanel ref={panelWordRef} idTipoProgreso={subproceso.idTipoProgreso} nombreSubproceso={subproceso.nombreProgreso}
        secciones={secciones ?? []} obtenerPayload={construirPayload} onError={m => mostrarToast(m, 'error')}
        onAnalizado={alAnalizarAutomatico} />

      <CamposAntiguos idTipoProgreso={subproceso.idTipoProgreso} onRestaurado={cargarSecciones}
        onError={m => mostrarToast(m, 'error')} />

      {secciones === null ? (
        <div className="flex justify-center py-16">
          <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
        </div>
      ) : (
        <div className="space-y-4">
          {secciones.length === 0 && (
            <div className="bg-white border border-dashed border-gray-300 rounded-2xl text-center py-10 px-6">
              <p className="text-sm text-gray-400">Esta plantilla todavía no tiene secciones.</p>
              <p className="text-xs text-gray-300 mt-1 mb-4">Empieza agregando la primera abajo, o duplica la estructura de otra plantilla ya armada.</p>
              {tiposDisponibles.filter(t => t.idTipoProgreso !== subproceso.idTipoProgreso).length > 0 && (
                <div className="flex items-center justify-center gap-2 flex-wrap">
                  <select value={origenDuplicar} onChange={e => setOrigenDuplicar(e.target.value)}
                    className="border border-gray-200 rounded-lg px-2.5 py-1.5 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
                    <option value="">Duplicar desde…</option>
                    {tiposDisponibles.filter(t => t.idTipoProgreso !== subproceso.idTipoProgreso).map(t => (
                      <option key={t.idTipoProgreso} value={t.idTipoProgreso}>{t.nombreProgreso}</option>
                    ))}
                  </select>
                  <button type="button" onClick={() => duplicarDesde(origenDuplicar)} disabled={!origenDuplicar || duplicando}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-primary-600 text-white hover:bg-primary-700 disabled:opacity-50 transition-colors">
                    {duplicando ? 'Duplicando…' : 'Duplicar formulario'}
                  </button>
                </div>
              )}
            </div>
          )}

          {secciones.map((s, idx) => {
            const acento = (s.tipo === 'TABLA' && s.origenFijo) ? ACENTO_ORIGEN_FIJO : ACENTO_TIPO[s.tipo]
            return (
            <div key={s.localId}
              className={`bg-white border border-gray-200 border-l-4 ${acento.borde} rounded-2xl shadow-sm hover:shadow-md transition-shadow overflow-hidden`}>
              <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-100">
                <span className={`flex-shrink-0 w-6 h-6 rounded-full ${acento.badge} text-[11px] font-bold flex items-center justify-center`}>
                  {idx + 1}
                </span>
                <div className="flex items-center gap-0.5 flex-shrink-0">
                  <button type="button" onClick={() => moverSeccion(idx, -1)} disabled={idx === 0} title="Subir"
                    className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30 disabled:hover:bg-transparent transition-colors">
                    <IconoFlechaArriba />
                  </button>
                  <button type="button" onClick={() => moverSeccion(idx, 1)} disabled={idx === secciones.length - 1} title="Bajar"
                    className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30 disabled:hover:bg-transparent transition-colors">
                    <IconoFlechaAbajo />
                  </button>
                </div>
                <input value={s.nombre} onChange={e => editarSeccion(s.localId, 'nombre', e.target.value)}
                  placeholder="Nombre de la sección (ej: SIMIT)"
                  className="flex-1 min-w-0 border border-gray-200 rounded-lg px-3 py-1.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-primary-500" />
                <div className="flex gap-1 flex-shrink-0">
                  {['CAMPOS', 'TABLA'].map(t => (
                    <button key={t} type="button" onClick={() => editarSeccion(s.localId, 'tipo', t)}
                      className={`px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                        s.tipo === t ? `${ACENTO_TIPO[t].chip} border-transparent` : 'bg-white border-gray-200 text-gray-500 hover:bg-gray-50'
                      }`}>
                      {t === 'CAMPOS' ? 'Campos' : 'Tabla de filas'}
                    </button>
                  ))}
                </div>
                {s.tipo === 'TABLA' && !s.origenFijo && (
                  <input value={s.clave} onChange={e => editarSeccion(s.localId, 'clave', e.target.value)}
                    placeholder={`seccion_${idx + 1}`} title="Clave de tablas['...'] para repeatTableRow — se autogenera si se deja vacía"
                    className="w-28 flex-shrink-0 border border-gray-200 rounded-lg px-2 py-1.5 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-primary-500" />
                )}
                <button type="button" onClick={() => quitarSeccion(s.localId)} title="Quitar sección"
                  className="p-1.5 rounded-lg text-red-400 hover:bg-red-50 hover:text-red-600 transition-colors flex-shrink-0">
                  <IconoPapelera />
                </button>
              </div>

              {s.tipo === 'TABLA' && (
                <div className="flex flex-wrap items-center gap-2.5 px-4 py-2.5 border-b border-gray-100 bg-red-50/30">
                  <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Origen de los datos</span>
                  <select value={s.origenFijo} onChange={e => elegirOrigenFijo(s.localId, e.target.value)}
                    className="border border-gray-200 rounded-lg px-2.5 py-1.5 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
                    <option value="">Manual — el analista la llena</option>
                    {TABLAS_HOJA_VIDA.map(t => {
                      const usadoEnOtra = secciones.some(o => o.localId !== s.localId && o.origenFijo === t.clave)
                      return (
                        <option key={t.clave} value={t.clave} disabled={usadoEnOtra}>
                          Automática — {t.etiqueta}{usadoEnOtra ? ' (ya usada en otra sección)' : ''}
                        </option>
                      )
                    })}
                  </select>
                  {s.origenFijo && (
                    <span className="text-[11px] text-red-600">
                      Arranca precargada con la hoja de vida del candidato — el analista/poligrafista/visitador
                      puede editarla o agregar filas — clave <code className="font-mono">tablas['{s.origenFijo}']</code>
                    </span>
                  )}
                </div>
              )}

              <div className="p-4 space-y-4">
                {s.origenFijo ? (
                  <div className="bg-red-50/50 border border-red-100 rounded-xl px-4 py-3">
                    <p className="text-xs font-semibold text-red-700 mb-1">
                      Columnas — {TABLAS_HOJA_VIDA.find(t => t.clave === s.origenFijo)?.etiqueta}
                    </p>
                    <p className="text-[11px] text-gray-400 mb-2.5">
                      Elige cuáles se ven en el formulario del analista — no hay que crearlas, ya vienen fijas de la
                      hoja de vida del candidato, precargadas y editables (el analista/poligrafista/visitador puede
                      corregirlas o agregar filas nuevas). Ocultar una columna aquí no la quita como marcador del .docx.
                    </p>
                    <div className="flex flex-wrap gap-x-4 gap-y-1.5">
                      {TABLAS_HOJA_VIDA.find(t => t.clave === s.origenFijo)?.columnas.map(c => (
                        <label key={c} className="flex items-center gap-1.5 text-xs text-gray-700 cursor-pointer">
                          <input type="checkbox" checked={!s.columnasOcultas.includes(c)}
                            onChange={() => toggleColumnaOculta(s.localId, c)}
                            className="rounded border-gray-300 text-red-600 focus:ring-red-500" />
                          {c}
                        </label>
                      ))}
                    </div>
                  </div>
                ) : (
                  <div>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                      Columnas {s.tipo === 'TABLA' && <span className="normal-case text-gray-400">(lo que se llena por cada fila)</span>}
                    </p>
                    <div className="space-y-1.5">
                      {s.columnas.map(c => (
                        <div key={c.localId} className="space-y-1">
                          <div className="flex items-center gap-2 group">
                            <span className="hidden sm:flex flex-shrink-0 w-7 h-7 rounded-lg bg-gray-50 text-gray-400 text-xs items-center justify-center border border-gray-100">
                              {c.campoCandidato ? '👤' : c.campoCliente ? '🏢' : TIPOS_DATO.find(td => td.value === c.tipoDato)?.icono}
                            </span>
                            <input value={c.nombre} onChange={e => editarColumna(s.localId, c.localId, 'nombre', e.target.value)}
                              placeholder="Título (opcional) — vacío = solo el valor, sin etiqueta"
                              className="flex-1 min-w-0 border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                            <select value={c.campoCandidato ? `candidato:${c.campoCandidato}` : c.campoCliente ? `cliente:${c.campoCliente}` : ''}
                              onChange={e => {
                                const valor = e.target.value
                                if (!valor) { elegirCampoAutomatico(s.localId, c.localId, '', '', ''); return }
                                const [tipo, clave] = valor.split(':')
                                const opcion = (tipo === 'candidato' ? disponiblesCandidato : disponiblesCliente).find(o => o.clave === clave)
                                elegirCampoAutomatico(s.localId, c.localId, tipo, clave, opcion?.etiqueta ?? '')
                              }}
                              title="Dato automático (opcional) — se resuelve solo, no lo llena el analista"
                              className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500 max-w-[9rem]">
                              <option value="">Campo manual</option>
                              <optgroup label="Datos del candidato">
                                {disponiblesCandidato.map(o => <option key={`cand-${o.clave}`} value={`candidato:${o.clave}`}>{o.etiqueta}</option>)}
                              </optgroup>
                              <optgroup label="Datos del cliente">
                                {disponiblesCliente.map(o => <option key={`cli-${o.clave}`} value={`cliente:${o.clave}`}>{o.etiqueta}</option>)}
                              </optgroup>
                            </select>
                            {!c.campoCandidato && !c.campoCliente && (
                              <select value={c.tipoDato} onChange={e => editarColumna(s.localId, c.localId, 'tipoDato', e.target.value)}
                                className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
                                {TIPOS_DATO.map(td => <option key={td.value} value={td.value}>{td.label}</option>)}
                              </select>
                            )}
                            <input value={c.clave} onChange={e => editarColumna(s.localId, c.localId, 'clave', e.target.value)}
                              placeholder={`campo_${s.columnas.indexOf(c) + 1}`} title="Clave del marcador — se autogenera si se deja vacía"
                              className="w-28 flex-shrink-0 border border-gray-200 rounded-lg px-2 py-1.5 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-primary-500" />
                            <button type="button" onClick={() => quitarColumna(s.localId, c.localId)} title="Quitar columna"
                              className="p-1.5 rounded-lg text-gray-300 hover:bg-red-50 hover:text-red-600 transition-colors flex-shrink-0 opacity-0 group-hover:opacity-100">
                              <IconoPapelera />
                            </button>
                          </div>
                          {!c.campoCandidato && !c.campoCliente && c.tipoDato === 'LISTA' && (
                            <input value={c.opcionesTexto ?? ''}
                              onChange={e => editarColumna(s.localId, c.localId, 'opcionesTexto', e.target.value)}
                              placeholder="Opciones separadas por coma — ej: Sí, No, N/A"
                              className="ml-9 w-[calc(100%-2.25rem)] border border-gray-200 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-primary-500" />
                          )}
                        </div>
                      ))}
                    </div>
                    <button type="button" onClick={() => agregarColumna(s.localId)}
                      className="mt-2 inline-flex items-center gap-1 text-xs text-primary-600 hover:text-primary-800 font-medium">
                      <IconoMas /> Agregar columna
                    </button>
                  </div>
                )}

                {s.tipo === 'TABLA' && !s.origenFijo && (
                  <div className="pt-3 border-t border-gray-100">
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1">Filas sugeridas</p>
                    <p className="text-xs text-gray-400 mb-2">
                      Aparecen precargadas cuando el analista abre el formulario por primera vez (ej. los nombres de
                      las fuentes que siempre se consultan). El analista puede agregar más filas libremente además de estas.
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {s.filasSugeridas.map(f => (
                        <div key={f.localId} className="flex items-center gap-1 bg-violet-50/60 border border-violet-100 rounded-lg pl-2 pr-1 py-1">
                          <input value={f.etiqueta} onChange={e => editarFilaSugerida(s.localId, f.localId, e.target.value)}
                            placeholder="Ej: Procuraduría"
                            className="text-xs bg-transparent focus:outline-none w-32" />
                          <button type="button" onClick={() => quitarFilaSugerida(s.localId, f.localId)}
                            className="text-violet-300 hover:text-red-500 text-xs px-1">×</button>
                        </div>
                      ))}
                      <button type="button" onClick={() => agregarFilaSugerida(s.localId)}
                        className="inline-flex items-center gap-1 text-xs text-violet-600 hover:text-violet-800 font-medium px-2 py-1">
                        <IconoMas /> Agregar fila
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )})}

          <button type="button" onClick={agregarSeccion}
            className="w-full flex items-center justify-center gap-1.5 border-2 border-dashed border-gray-200 rounded-2xl px-3 py-3 text-sm text-gray-400 font-medium hover:border-primary-300 hover:text-primary-600 hover:bg-primary-50/40 transition-colors">
            <IconoMas /> Agregar sección
          </button>

          <div className="flex justify-end gap-3 pt-2 sticky bottom-0 bg-gradient-to-t from-gray-50 via-gray-50/95 to-transparent py-3 -mx-1 px-1">
            {secciones.length > 0 && (
              <button type="button" onClick={verVistaPrevia} disabled={generandoPreview}
                className="inline-flex items-center gap-1.5 border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-60 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
                <IconoOjo /> {generandoPreview ? 'Generando…' : 'Vista previa'}
              </button>
            )}
            <button type="button" onClick={intentarGuardar} disabled={guardando}
              className="inline-flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors shadow-sm shadow-primary-200">
              <IconoGuardar /> {guardando ? 'Guardando…' : 'Guardar plantilla'}
            </button>
          </div>
        </div>
      )}

      {impactoPendiente && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-5">
            <div className="flex items-center gap-2.5 mb-3">
              <div className="flex-shrink-0 w-9 h-9 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">⚠️</div>
              <p className="text-sm font-semibold text-gray-800">Esto va a archivar respuestas ya guardadas</p>
            </div>
            <p className="text-xs text-gray-500 mb-3">
              No se pierden — se conservan en los formularios donde ya se habían diligenciado y en el documento
              generado, pero dejan de aparecer para llenar formularios nuevos:
            </p>
            <ul className="text-xs text-gray-700 space-y-1 mb-4 max-h-40 overflow-y-auto">
              {impactoPendiente.secciones.map(nombre => (
                <li key={`s-${nombre}`} className="flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0" /> Sección "{nombre}" completa
                </li>
              ))}
              {impactoPendiente.columnas.map(nombre => (
                <li key={`c-${nombre}`} className="flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0" /> Campo "{nombre}"
                </li>
              ))}
            </ul>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setImpactoPendiente(null)} disabled={guardando}
                className="text-sm text-gray-500 hover:text-gray-700 px-3 py-2">Cancelar</button>
              <button type="button" onClick={() => confirmarGuardar()} disabled={guardando}
                className="bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
                {guardando ? 'Guardando…' : 'Sí, archivar y guardar'}
              </button>
            </div>
          </div>
        </div>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}

import { useState, useEffect, useRef, forwardRef, useImperativeHandle, Fragment, useId } from 'react'
import { Link, useParams } from 'react-router-dom'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import api from '../../../services/api'
import formulariosService from '../../../services/formulariosService'
import hallazgosService from '../../../services/hallazgosService'
import CalificacionColumna, {
  calificacionDesdeServidor, calificacionVacia, calificacionParaServidor, validarCalificacion, RESULTADOS_HALLAZGOS, RESULTADOS_CAMPO,
} from './CalificacionColumna'
import Toast from '../../../components/Toast'
import PanelAnexo from './PanelAnexo'
import PanelDeclaraciones from './PanelDeclaraciones'
import hojaVidaCatalogoService from '../../../services/hojaVidaCatalogoService'

const TIPOS_DATO = [
  { value: 'TEXTO', label: 'Texto', icono: 'Aa' },
  { value: 'NUMERO', label: 'Número', icono: '#' },
  { value: 'FECHA', label: 'Fecha', icono: '📅' },
  { value: 'BOOLEANO', label: 'Sí / No', icono: '✓' },
  { value: 'LISTA', label: 'Lista desplegable', icono: '▾' },
  { value: 'CIUDAD', label: 'Ciudad (catálogo)', icono: '📍' },
  { value: 'BARRIO', label: 'Barrio (catálogo)', icono: '🏘' },
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
  { clave: 'experiencia_laboral', etiqueta: 'Experiencia laboral', columnas: ['empresa', 'cargo', 'fecha_inicio', 'fecha_fin', 'labora_actualmente', 'ciudad', 'telefono_empresa', 'motivo_retiro', 'nombre_jefe', 'cargo_jefe', 'tipo_contrato', 'salario', 'llamados_atencion'] },
  { clave: 'inactividades', etiqueta: 'Inactividades laborales', columnas: ['fecha_inicio', 'fecha_fin', 'dias_inactivo', 'justificacion', 'requiere_cuestionario'] },
]

// Tablas fijas que solo se consultan y salen en Word (CandidatoInfoService.TABLAS_SOLO_LECTURA):
// no se ofrecen como origen de una sección porque nadie del equipo las edita ni las valida por fila.
const TABLAS_SOLO_MARCADOR = [
  { clave: 'declaraciones', etiqueta: 'Declaraciones del candidato (preguntas de su hoja de vida)', columnas: ['pregunta', 'respuesta', 'detalle'] },
]

/* ─── Calificación de riesgo por respuesta (docs/modelo-calificacion-riesgo.md §3) ─── */
const TIPOS_CALIFICABLES = ['LISTA', 'BOOLEANO']

// Columna que nadie diligencia: dato del candidato, del cliente, del estudio o calculado por el sistema.
function esAutomatica(c) {
  return !!(c.campoCandidato || c.campoCliente || c.campoServicio || c.calculado)
}

function esCalificable(c) {
  return TIPOS_CALIFICABLES.includes(c.tipoDato) && !esAutomatica(c)
}

// Opciones de una lista: una por línea (así una opción puede llevar coma).
const opcionesDe = (texto) => (texto || '').split('\n').map(o => o.trim()).filter(Boolean)

// Respuestas posibles de un campo calificable: en Sí/No se guardan como "true"/"false".
function respuestasDe(c) {
  if (c.tipoDato === 'BOOLEANO') return [{ valor: 'true', etiqueta: 'Sí' }, { valor: 'false', etiqueta: 'No' }]
  return opcionesDe(c.opcionesTexto).map(o => ({ valor: o, etiqueta: o }))
}

// Columna tal como llega del servidor -> estado local del constructor (conId=false al duplicar
// desde otra plantilla: se guarda como columna nueva).
function columnaDesdeServidor(c, conId) {
  return {
    localId: idLocal(), idColumna: conId ? c.idColumna : null, nombre: c.nombre ?? '', tipoDato: c.tipoDato,
    campoCandidato: c.campoCandidato ?? '', campoCliente: c.campoCliente ?? '', clave: c.clave ?? '',
    campoServicio: c.campoServicio ?? '', calculado: !!c.calculado,
    valorDefecto: c.valorDefecto ?? '', obligatorio: !!c.obligatorio,
    opcionesTexto: (c.opciones ?? []).join('\n'),
    calificaRiesgo: !!c.calificaRiesgo,
    cal: calificacionDesdeServidor(c.calificacion),
    longitudMaxima: c.longitudMaximaConfigurada ?? null,
    puntajes: Object.fromEntries((c.puntajes ?? []).map(pt => [pt.opcion, { puntos: String(Number(pt.puntos)), esCritico: !!pt.esCritico }])),
  }
}

const numPuntos = (t) => {
  if (t === '' || t == null) return 0
  const n = Number(String(t).replace(',', '.'))
  return Number.isFinite(n) ? n : NaN
}

// Tabla de puntos de un campo: una fila por respuesta posible. Avisa si hay puntos guardados de
// opciones que ya no existen (renombradas/quitadas): se perderán al guardar.
function PuntajesColumna({ c, onPuntaje }) {
  const respuestas = respuestasDe(c)
  const huerfanas = Object.entries(c.puntajes || {})
    .filter(([op, v]) => !respuestas.some(r => r.valor === op) && (numPuntos(v.puntos) > 0 || v.esCritico))
    .map(([op]) => op)
  if (respuestas.length === 0) {
    return <p className="text-[11px] text-amber-600 mt-1">Escribe las opciones de la lista para asignarles puntos.</p>
  }
  return (
    <div className="mt-2 rounded-lg border border-primary-100 overflow-hidden">
      <div className="grid grid-cols-[minmax(0,1fr)_90px_80px] gap-2 px-3 py-1.5 bg-primary-50/60 text-[10px] font-semibold uppercase tracking-wider text-primary-700">
        <span>Respuesta</span><span className="text-right">Puntos</span><span className="text-center">Crítica</span>
      </div>
      {respuestas.map(r => {
        const v = c.puntajes?.[r.valor] ?? { puntos: '', esCritico: false }
        const n = numPuntos(v.puntos)
        const invalido = Number.isNaN(n) || n < 0 || n > 100
        return (
          <div key={r.valor} className={`grid grid-cols-[minmax(0,1fr)_90px_80px] gap-2 items-center px-3 py-1.5 border-t border-primary-50 ${v.esCritico ? 'bg-red-50/40' : ''}`}>
            <span className="text-xs text-gray-700 truncate" title={r.etiqueta}>{r.etiqueta}</span>
            <input aria-label={`Puntos de ${r.etiqueta}`} value={v.puntos} onChange={e => onPuntaje(r.valor, 'puntos', e.target.value.replace(/[^0-9.,]/g, ''))}
              inputMode="decimal" placeholder="0"
              className={`text-right border rounded-md px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-primary-500 ${invalido ? 'border-red-300 bg-red-50' : 'border-gray-200'}`} />
            <label className="flex justify-center" title="Si eligen esta respuesta, el subproceso queda en 100 y el estudio en ALTO">
              <input type="checkbox" checked={!!v.esCritico} onChange={e => onPuntaje(r.valor, 'esCritico', e.target.checked)}
                className="rounded border-gray-300 text-red-600 focus:ring-red-500" />
            </label>
          </div>
        )
      })}
      {huerfanas.length > 0 && (
        <p className="px-3 py-1.5 border-t border-amber-100 bg-amber-50 text-[11px] text-amber-700">
          Estas respuestas ya no existen y sus puntos se perderán al guardar: {huerfanas.join(', ')}.
        </p>
      )}
    </div>
  )
}

// Resumen de la escala de riesgo de toda la plantilla — para ver de un vistazo si quedó bien armada.
function ResumenCalificacion({ secciones }) {
  let campos = 0, criticas = 0, maximoCampos = 0, maximoPorFila = 0
  for (const s of secciones) {
    for (const c of s.columnas) {
      if (!esCalificable(c) || !c.calificaRiesgo) continue
      campos++
      const valores = respuestasDe(c).map(r => c.puntajes?.[r.valor] ?? { puntos: '', esCritico: false })
      criticas += valores.filter(v => v.esCritico).length
      const max = Math.max(0, ...valores.map(v => numPuntos(v.puntos)).filter(n => Number.isFinite(n)))
      if (s.tipo === 'TABLA') maximoPorFila += max
      else maximoCampos += max
    }
  }
  if (campos === 0) {
    return (
      <div className="bg-white border border-dashed border-gray-300 rounded-2xl px-5 py-3 text-xs text-gray-500">
        <span className="font-semibold text-gray-700">Calificación de riesgo:</span> ningún campo califica todavía. Enciende
        "Califica riesgo" en un campo Lista o Sí/No para asignar puntos a sus respuestas.
      </div>
    )
  }
  return (
    <div className="bg-white border border-primary-100 rounded-2xl px-5 py-3 flex flex-wrap items-center gap-x-6 gap-y-1.5">
      <p className="text-xs font-semibold text-primary-700">Calificación de riesgo</p>
      <p className="text-xs text-gray-600"><span className="font-bold text-gray-900">{campos}</span> campo{campos === 1 ? '' : 's'} califica{campos === 1 ? '' : 'n'}</p>
      <p className="text-xs text-gray-600">
        Puntaje máximo posible <span className="font-bold text-gray-900">{Math.round(maximoCampos * 100) / 100}</span>
        {maximoPorFila > 0 && <> + <span className="font-bold text-gray-900">{Math.round(maximoPorFila * 100) / 100}</span> por cada fila de tabla</>}
        <span className="text-gray-400"> (tope 100)</span>
      </p>
      <p className="text-xs text-gray-600"><span className={`font-bold ${criticas > 0 ? 'text-red-600' : 'text-gray-900'}`}>{criticas}</span> respuesta{criticas === 1 ? '' : 's'} crítica{criticas === 1 ? '' : 's'}</p>
    </div>
  )
}

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
        className="w-full flex items-center gap-3 px-5 py-3.5 text-left hover:bg-gray-50/70 transition-colors">
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
  { idTipoProgreso, nombreSubproceso, secciones, catalogo, usados, obtenerPayload, onError, onAnalizado }, ref) {
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
      if (analisis.errorGeneracion) {
        g.titulo('El documento NO se puede generar', [185, 28, 28])
        g.parrafos([analisis.errorGeneracion])
      }

      const tablasMalUsadas = analisis.tablasMalUsadas ?? []
      if (tablasMalUsadas.length > 0) {
        g.titulo(`Tablas mal puestas — no se van a repetir (${tablasMalUsadas.length})`, [185, 28, 28])
        g.tabla(['Marcador'], tablasMalUsadas.map(m => [`\${${m}}`]), [185, 28, 28])
        g.doc.setFontSize(7); g.doc.setFont('helvetica', 'italic'); g.doc.setTextColor(153, 27, 27)
        g.doc.text('Van dentro de un comentario repeatTableRow(...) (fila) o repeatDocPart(...) (bloque/cuadro), no sueltos en el texto.', g.MAR, g.y() - 4)
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

  // Cómo se describe una columna en el manual en PDF: de dónde sale y qué imprime su marcador
  // (opciones de la lista, hallazgos que ofrece o genera, puntos por respuesta, valor por defecto).
  const tipoDeColumna = (c) => c.campoCandidato ? 'Dato del candidato' : c.campoCliente ? 'Dato del cliente'
    : c.campoServicio ? 'Dato del estudio' : c.calculado ? 'Calculado'
    : (TIPOS_DATO.find(t => t.value === c.tipoDato)?.label ?? c.tipoDato).replace(/ \(.*\)$/, '')

  const detalleDeColumna = (c) => {
    if (c.campoCandidato) return `Se llena solo: ${disponiblesCandidato.find(o => o.clave === c.campoCandidato)?.etiqueta ?? c.campoCandidato}`
    if (c.campoCliente || c.campoServicio) return 'Se llena solo'
    const cal = c.cal ?? {}
    if (c.calculado) {
      const resultado = [...RESULTADOS_CAMPO, ...RESULTADOS_HALLAZGOS, { valor: 'NUMERO_FILA', etiqueta: 'Número de fila (1, 2, 3...)' }]
        .find(r => r.valor === cal.calculo)
      return (resultado?.etiqueta ?? 'Resultado sin elegir') + (cal.calculo === 'NOTA' && cal.textoNota ? `: "${cal.textoNota}"` : '')
    }
    const partes = []
    const opciones = opcionesDe(c.opcionesTexto)
    if (cal.modo === 'HALLAZGO') {
      partes.push(`Hallazgo del catálogo. Imprime el código elegido: ${opciones.join(' · ')}`)
    } else if (cal.modo === 'CRITICIDAD') {
      partes.push('Criticidad del cargo: Estándar · Sensible · Crítico')
    } else if (c.tipoDato === 'LISTA') {
      partes.push(`Imprime la opción elegida: ${opciones.join(' · ') || '(sin opciones)'}`)
      if (cal.modo === 'CONDICION') partes.push('Condición: "Sí" habilita los hallazgos solo con condición')
      if (cal.modo === 'OPCIONES') {
        const generan = opciones.filter(o => cal.porOpcion?.[o]).map(o => `${o} = ${cal.porOpcion[o]}`)
        if (generan.length) partes.push(`Genera hallazgo: ${generan.join(' · ')}`)
      }
    } else if (c.tipoDato === 'BOOLEANO') {
      partes.push('Imprime X si es Sí y nada si es No')
    } else if (c.tipoDato === 'ARCHIVO') {
      partes.push('Una sola imagen .jpg/.jpeg/.png se incrusta; si no, sale "Anexo: nombre"')
    } else if (c.tipoDato === 'TEXTO' && c.longitudMaxima) {
      partes.push(`Máximo ${c.longitudMaxima} caracteres`)
    }
    if (esCalificable(c) && c.calificaRiesgo && !cal.modo) {
      partes.push(`Puntos por respuesta: ${respuestasDe(c).map(r => {
        const v = c.puntajes?.[r.valor]
        return `${r.etiqueta} = ${v?.esCritico ? 'Crítica' : (v?.puntos || 0)}`
      }).join(' · ')}`)
    }
    if (c.valorDefecto) partes.push(`Por defecto: ${c.valorDefecto}`)
    if (c.obligatorio) partes.push('Obligatorio')
    return partes.join('. ')
  }

  // El manual completo de marcadores de ESTA plantilla — siempre descargable, no depende de haber
  // corrido "Analizar" (eso es solo para comparar contra el .docx ya subido). Orden: primero lo
  // propio de la plantilla (resumen y marcadores sección por sección, en el orden del constructor),
  // después lo que siempre está disponible y al final la guía de cómo escribirlo en Word.
  const descargarManualMarcadores = () => {
    const g = crearGeneradorPdf('Manual de marcadores — Plantilla Word')
    const AZUL = [67, 56, 202], VIOLETA = [109, 40, 217], ROJO = [185, 28, 28], VERDE = [21, 128, 61]
    const AMBAR = [180, 83, 9], NARANJA = [194, 65, 12], GRIS = [55, 65, 81]
    const dosColumnas = { font: 'helvetica', columnStyles: { 0: { cellWidth: 75, font: 'courier' } } }
    const cuatroColumnas = { font: 'helvetica', columnStyles: { 0: { cellWidth: 58, font: 'courier' }, 1: { cellWidth: 36 }, 2: { cellWidth: 24 } } }

    const claveTabla = (s) => s.origenFijo || s.clave || '?'
    const tipoSeccion = (s) => s.tipo !== 'TABLA' ? 'Campos' : s.calculada ? 'Tabla de hallazgos (la arma el sistema)'
      : s.origenFijo ? 'Tabla de hoja de vida' : 'Tabla'
    const hallazgosPlantilla = (catalogo ?? []).filter(h => usados?.has(h.codigo))
    const calificacionDe = (h) => h.tipo === 'CRITICO' ? 'Crítico' : h.tipo === 'ALERTA' ? 'Alerta' : String(h.puntos ?? '')

    g.titulo('Contenido', GRIS)
    g.parrafos([
      '1. Resumen de la plantilla',
      '2. Marcadores de esta plantilla, sección por sección',
      '3. Calificación de riesgo, tabla de hallazgos y Anexo 1',
      '4. Datos del candidato',
      '5. Tablas de la hoja de vida',
      '6. Cómo escribir los marcadores en Word',
      '7. Tablas: las dos formas de repetirlas',
      '8. Errores comunes',
    ])

    g.titulo('1. Resumen de la plantilla', AZUL)
    if (secciones.length === 0) {
      g.parrafos(['Todavía no hay secciones definidas en el constructor.'])
    } else {
      g.tabla(['N°', 'Sección', 'Tipo', 'Campos', 'Cómo va en el Word'],
        secciones.map((s, i) => [String(i + 1), s.nombre || '(sin nombre)', tipoSeccion(s), String(s.columnas.length),
          s.tipo === 'TABLA' ? `repeatTableRow(tablas['${claveTabla(s)}'])` : "${campos['clave']}"]),
        AZUL, { font: 'helvetica', columnStyles: { 0: { cellWidth: 10 }, 3: { cellWidth: 16 }, 4: { cellWidth: 74, font: 'courier' } } })
    }

    g.titulo('2. Marcadores de esta plantilla, sección por sección', AZUL)
    g.parrafos(['En el mismo orden del constructor. Copia cada marcador tal cual, con sus comillas simples.'])
    secciones.forEach((s, i) => {
      const esTabla = s.tipo === 'TABLA'
      const color = !esTabla ? AZUL : s.origenFijo ? ROJO : VIOLETA
      g.subtitulo(`2.${i + 1}  ${s.nombre || '(sin nombre)'}`, tipoSeccion(s), color, 'helvetica')
      if (esTabla) {
        g.parrafos([`Comentario de Word sobre la fila (o el cuadro) que se repite: repeatTableRow(tablas['${claveTabla(s)}'])  —  para un cuadro de varias filas: repeatDocPart(tablas['${claveTabla(s)}'])`])
      }
      const marcador = (clave) => esTabla ? `\${['${clave || '?'}']}` : `\${campos['${clave || '?'}']}`
      const filas = []
      if (s.origenFijo) {
        const fijas = TABLAS_HOJA_VIDA.find(t => t.clave === s.origenFijo)?.columnas ?? []
        fijas.forEach(c => filas.push([marcador(c), c, 'Hoja de vida',
          s.columnasOcultas.includes(c) ? 'Fija. Oculta en el formulario; el marcador funciona igual' : 'Fija, viene de la hoja de vida del candidato']))
      }
      const etiquetas = s.filasSugeridas.map(f => f.etiqueta.trim()).filter(Boolean)
      if (etiquetas.length > 0) {
        filas.push([marcador('etiqueta'), 'Etiqueta de la fila', 'Texto', `Filas ya puestas: ${etiquetas.join(' · ')}`])
      }
      s.columnas.forEach(c => filas.push([marcador(c.clave), c.nombre || '(sin título)', tipoDeColumna(c),
        (s.origenFijo ? 'Columna de validación. ' : '') + detalleDeColumna(c)]))
      g.tabla(['Marcador', 'Campo', 'Tipo', 'Qué imprime'], filas.length > 0 ? filas : [['(sin campos)', '', '', '']], color, cuatroColumnas)
    })

    g.titulo('3. Calificación de riesgo, tabla de hallazgos y Anexo 1', NARANJA)
    g.parrafos([
      'Marcadores siempre disponibles. Los calcula el motor de riesgo; el nivel del estudio es el que vale para el informe (el automático, o el que puso el gestor si lo cambió). El puntaje va con un decimal y coma (ej. 31,0).',
      "Si el estudio aún tiene subprocesos pendientes, ${riesgo['nivel']} dice «En proceso»; si no se puede calificar, «Sin calificar». En ambos casos el puntaje queda vacío y el documento se genera igual.",
    ])
    g.tabla(['Marcador', 'Qué muestra'], [
      ["${riesgo['nivel']}", 'Nivel final del estudio (BAJO / MEDIO / ALTO)'],
      ["${riesgo['puntaje']}", 'Puntaje final del estudio (0 a 100)'],
      ["${calificacion['nivel']}", 'Nivel de ESTE subproceso (el del formulario)'],
      ["${calificacion['puntaje']}", 'Puntaje de ESTE subproceso (0 a 100)'],
    ], NARANJA, dosColumnas)

    g.subtitulo('Anexo 1 · Catálogo de referencias', 'lo llena la plataforma con los hallazgos que usa esta plantilla', NARANJA, 'helvetica')
    g.tabla(['Comentario sobre la fila', 'Marcadores de la fila', 'Qué trae'], [
      ["repeatTableRow(tablas['anexo_catalogo'])", "${['a1_ref']}  ${['a1_hallazgo']}  ${['a1_calificacion']}", 'Hallazgos que suman puntos y críticos'],
      ["repeatTableRow(tablas['anexo_alertas'])", "${['a1_ref']}  ${['a1_hallazgo']}  ${['a1_calificacion']}", 'Alertas (sin puntos)'],
    ], NARANJA, { font: 'helvetica', columnStyles: { 0: { font: 'courier', cellWidth: 74 }, 1: { font: 'courier' }, 2: { cellWidth: 44 } } })

    g.subtitulo(`Hallazgos que usa esta plantilla (${hallazgosPlantilla.length})`, 'lo que imprime el Anexo 1', NARANJA, 'helvetica')
    g.tabla(['Ref.', 'Hallazgo', 'Calificación', 'Reglas'], hallazgosPlantilla.length > 0
      ? hallazgosPlantilla.map(h => [h.codigo, h.descripcion, calificacionDe(h),
          [h.acumula && 'Acumula por fila', h.soloCondicion && 'Solo con condición'].filter(Boolean).join(' · ')])
      : [['(ninguno)', 'Esta plantilla todavía no usa hallazgos del catálogo.', '', '']],
      NARANJA, { font: 'helvetica', columnStyles: { 0: { cellWidth: 22, font: 'courier' }, 2: { cellWidth: 24 }, 3: { cellWidth: 40 } } })

    g.titulo(`4. Datos del candidato — siempre disponibles (${disponiblesCandidato.length})`, GRIS)
    g.tabla(['Marcador', 'Campo'],
      disponiblesCandidato.map(c => [`\${candidato['${c.clave}']}`, c.etiqueta]), GRIS, dosColumnas)

    g.titulo('5. Tablas de la hoja de vida — fijas, siempre disponibles', ROJO)
    g.parrafos(['Se usan sin crear sección. Si esta plantilla les agregó columnas de validación, están en la sección 2 y van en la misma fila.'])
    ;[...TABLAS_HOJA_VIDA, ...TABLAS_SOLO_MARCADOR].forEach(t => {
      g.subtitulo(`repeatTableRow(tablas['${t.clave}'])`, t.etiqueta, ROJO)
      g.tabla(['Marcador por registro', 'Columna'], t.columnas.map(c => [`\${['${c}']}`, c]), ROJO, dosColumnas)
    })

    g.titulo('6. Cómo escribir los marcadores en Word', AZUL)
    g.parrafos([
      '• Escribe cada marcador tal cual — mayúsculas, guiones bajos y comillas simples rectas incluidos. Si Word cambia las comillas por comillas curvas, el marcador deja de funcionar.',
      "• Un campo: ${campos['clave']}. Un dato del candidato: ${candidato['CLAVE']}. Se reemplazan por el valor real y conservan el formato del texto donde están.",
      '• Funcionan en un párrafo normal, en una celda de tabla, en encabezados y pies de página y dentro de un cuadro de texto.',
      '• Una lista desplegable imprime el texto de la opción elegida. Un campo Sí / No imprime X si es Sí y nada si es No.',
      '• Lo que nadie cambió vale su valor por defecto, igual que en pantalla.',
      '• Una columna tipo Archivo incrusta la imagen solo si se subió exactamente un .jpg, .jpeg o .png; con varios adjuntos, o un PDF, sale el texto "Anexo: nombre del archivo".',
    ])

    g.titulo('7. Tablas: las dos formas de repetirlas', AZUL)
    g.tabla(['', 'Tabla SECUENCIAL', 'Tabla tipo DISEÑO'], [
      ['Función (en el comentario)', "repeatTableRow(tablas['clave'])", "repeatDocPart(tablas['clave'])"],
      ['Qué repite', 'UNA fila de la tabla, una debajo de otra', 'Un bloque completo (tabla entera, varias filas, celdas combinadas, párrafos) — una copia por registro'],
      ['Cómo se ve', 'Listado: encabezado + una fila por registro', 'Un "cuadro" / tarjeta por registro, una debajo de la otra'],
      ['Se selecciona para comentar', 'Solo la fila de datos', 'Todo el bloque (desde el párrafo de arriba hasta el de abajo)'],
      ['Marcador de cada celda', "${['clave_columna']}", "${['clave_columna']}"],
    ], AZUL, { font: 'helvetica', columnStyles: { 0: { cellWidth: 38, fontStyle: 'bold' } } })

    g.subtitulo('A. Tabla SECUENCIAL', "repeatTableRow(tablas['clave'])", VERDE, 'helvetica')
    g.parrafos([
      'Úsala cuando cada registro cabe en UNA fila: un listado clásico con columnas.',
      '1. En Word: Insertar > Tabla, con tantas columnas como datos quieras mostrar y DOS filas: la de encabezado (títulos normales) y UNA sola fila de datos debajo.',
      "2. En la fila de DATOS, escribe en cada celda el marcador de la columna entre corchetes, sin prefijo: ${['clave_columna']}. Nunca ${campos[...]} ni ${tablas[...]} dentro de la fila.",
      '3. La fila de datos debe ser una celda por columna, SIN combinar celdas con otra fila — se repite exactamente esa fila.',
      "4. Selecciona esa fila de datos completa (clic en el margen izquierdo de la fila) > Revisar > Nuevo comentario > escribe EXACTAMENTE, sin \"${}\" alrededor: repeatTableRow(tablas['clave'])",
      '5. Resultado: el encabezado queda una sola vez y la fila de datos se repite una vez por cada registro. Si no hay registros, queda solo el encabezado.',
    ])

    g.subtitulo('B. Tabla tipo DISEÑO', "repeatDocPart(tablas['clave'])", AMBAR, 'helvetica')
    g.parrafos([
      'Úsala cuando cada registro es un "cuadro" o tarjeta con diseño propio: varias filas, celdas combinadas, títulos dentro del cuadro.',
      '1. Diseña en Word el cuadro para UN solo registro: puedes combinar celdas, poner varias filas, etiquetas fijas y colores.',
      "2. Donde va cada dato, escribe el marcador de la columna entre corchetes, sin prefijo: ${['clave_columna']}.",
      '3. Deja un párrafo normal (un Enter) justo ARRIBA y otro justo DEBAJO del cuadro. Selecciona desde ese párrafo de arriba hasta el de abajo, con la tabla completa dentro de la selección.',
      "4. Con todo eso seleccionado > Revisar > Nuevo comentario > escribe EXACTAMENTE, sin \"${}\" alrededor: repeatDocPart(tablas['clave'])",
      '5. Resultado: el bloque completo se copia una vez por cada registro, uno debajo del otro. Si no hay registros, el bloque desaparece.',
      '6. Si el cuadro es una tabla "flotante" (con posición fija en la página), el sistema le quita la posición fija a las copias para que no queden encimadas.',
      'Un título que deba salir UNA sola vez (ej. "FORMACIÓN ACADÉMICA") va FUERA de la selección del comentario.',
    ])

    g.titulo('8. Errores comunes', ROJO)
    g.parrafos([
      "• Escribir ${tablas['clave']} en el comentario (o suelto en el texto) en vez de repeatTableRow(tablas['clave']) / repeatDocPart(tablas['clave']): no repite nada y en el texto sale el listado como texto corrido.",
      '• Usar repeatTableRow sobre un cuadro de varias filas combinadas: solo repite una fila y el diseño se rompe. Para cuadros, usa repeatDocPart.',
      '• Con repeatDocPart, seleccionar solo algunas celdas en vez de la tabla completa (desde el párrafo de arriba hasta el de abajo).',
      "• Usar un marcador ${['clave']} FUERA de la fila o bloque comentado: no tiene de dónde tomar el dato y el documento no se genera.",
      '• Escribir mal la clave de la tabla o de la columna — deben coincidir exactamente con las de este manual.',
      '• Poner dos funciones en el mismo comentario, o un comentario que además tenga otro texto — un comentario = una sola función.',
      '• Cambiar el título de un campo esperando que cambie su marcador: un campo ya guardado conserva su clave.',
    ])

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
      doc.text(texto, MAR, y)
      doc.setDrawColor(...color); doc.setLineWidth(0.3); doc.line(MAR, y + 1.5, ANCHO - MAR, y + 1.5)
      y += 6
    }
    const subtitulo = (marcador, etiqueta, color, fuente = 'courier') => {
      // Que el subtítulo nunca quede solo al final de la página, sin su tabla debajo.
      espacioAntesDe(24)
      doc.setFontSize(9); doc.setFont(fuente, 'bold'); doc.setTextColor(...color)
      doc.text(marcador, MAR, y)
      // El ancho se mide con la fuente del marcador, antes de cambiarla para la etiqueta.
      const anchoMarcador = doc.getTextWidth(marcador)
      doc.setFont('helvetica', 'normal'); doc.setTextColor(148, 163, 184)
      doc.text(` — ${etiqueta}`, MAR + anchoMarcador, y)
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
        theme: 'grid', styles: { fontSize: 8, font: opts.font ?? 'courier', overflow: 'linebreak' },
        pageBreak: 'avoid', rowPageBreak: 'avoid',
        headStyles: { fillColor: color, textColor: 255, fontStyle: 'bold' },
        columnStyles: opts.columnStyles,
      })
      y = doc.lastAutoTable.finalY + 8
    }
    const guardar = (prefijoArchivo) => {
      // Pie en todas las páginas: de qué plantilla es y 'Página n de N'.
      const paginas = doc.getNumberOfPages()
      for (let p = 1; p <= paginas; p++) {
        doc.setPage(p)
        doc.setFontSize(7); doc.setFont('helvetica', 'normal'); doc.setTextColor(148, 163, 184)
        doc.text(nombreSubproceso ?? 'Plantilla', MAR, ALTO - 7)
        doc.text('Página ' + p + ' de ' + paginas, ANCHO - MAR, ALTO - 7, { align: 'right' })
      }
      const nombreBase = (nombreSubproceso ?? 'plantilla').toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')
        .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
      doc.save(`${prefijoArchivo}-${nombreBase || 'plantilla'}.pdf`)
    }

    return { doc, MAR, y: () => y, titulo, subtitulo, parrafos, tabla, guardar }
  }

  const nombreArchivo = (ruta) => {
    if (!ruta) return null
    const base = ruta.split('/').pop()
    return base.replace(/^[0-9a-fA-F-]{36}_/, '')
  }

  const sinDiferencias = analisis && !analisis.errorGeneracion && analisis.marcadoresDesconocidos.length === 0
    && analisis.camposSinUsar.length === 0 && (analisis.tablasMalUsadas ?? []).length === 0

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
              {analisis.errorGeneracion && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs">
                  <p className="flex items-center gap-1.5 font-semibold text-red-700 mb-1.5">
                    <IconoAlertaTriangulo /> El documento NO se puede generar
                  </p>
                  <p className="text-red-700 mb-1.5">
                    Esto es más grave que las diferencias de abajo: si lo intentas generar de verdad (o le das a
                    "Vista previa"), va a fallar. Suele ser un marcador suelto <span className="font-mono">{"${['clave']}"}</span> mal
                    ubicado (fuera de una fila con repeatTableRow/repeatDocPart) — esta comparación por nombre no
                    lo detecta, pero generar el documento sí lo revienta.
                  </p>
                  <p className="font-mono text-red-600 bg-white border border-red-100 rounded px-2 py-1.5 break-words">
                    {analisis.errorGeneracion}
                  </p>
                </div>
              )}
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
                    <span className="font-mono"> repeatTableRow(tablas['clave'])</span> (tabla secuencial, repite
                    una fila) o <span className="font-mono">repeatDocPart(tablas['clave'])</span> (tabla tipo diseño,
                    repite un cuadro de varias filas/celdas combinadas) — ese comentario no hace nada. Descarga el
                    manual de marcadores más abajo para ver la guía completa de las dos formas.
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
  const idGrupos = useId()
  const [guardando, setGuardando] = useState(false)
  const [generandoPreview, setGenerandoPreview] = useState(false)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [impactoPendiente, setImpactoPendiente] = useState(null)
  const [disponiblesCandidato, setDisponiblesCandidato] = useState([])
  const [disponiblesCliente, setDisponiblesCliente] = useState([])
  const [disponiblesServicio, setDisponiblesServicio] = useState([])
  // Preguntas de declaración del candidato (Admin → Novedades → Hoja de vida del candidato)
  const [preguntasCandidato, setPreguntasCandidato] = useState([])
  useEffect(() => {
    hojaVidaCatalogoService.listar()
      .then(items => setPreguntasCandidato(items.filter(i => i.lista === 'PREGUNTA').sort((a, b) => a.orden - b.orden || a.id - b.id)))
      .catch(() => setPreguntasCandidato([]))
  }, [])
  // Hallazgos del catálogo que le salen a quien diligencia esta plantilla (por su rol).
  const [catalogoHallazgos, setCatalogoHallazgos] = useState([])
  useEffect(() => {
    hallazgosService.listar(true).then(setCatalogoHallazgos).catch(() => setCatalogoHallazgos([]))
  }, [subproceso.rolResponsable])
  const [tiposDisponibles, setTiposDisponibles] = useState([])
  const [origenDuplicar, setOrigenDuplicar] = useState('')
  const [duplicando, setDuplicando] = useState(false)
  const [versionPlantilla, setVersionPlantilla] = useState(null)
  const [configPlantilla, setConfigPlantilla] = useState(null)
  const [sucio, setSucio] = useState(false)   // hay cambios en la estructura sin guardar
  const panelWordRef = useRef(null)
  const [paso, setPaso] = useState(null)          // 'word' | 'anexo' | 'archivados' | localId de una sección
  const [campoSel, setCampoSel] = useState(null)  // localId del campo que se está editando

  const cargarVersion = () => {
    formulariosService.obtenerConfigPlantilla(subproceso.idTipoProgreso)
      .then(c => { setVersionPlantilla(c.versionPlantilla); setConfigPlantilla(c) }).catch(() => {})
  }

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  // Se llama después de cada guardado (ver confirmarGuardar) cuando ya hay un .docx subido — si
  // el análisis automático encuentra un marcador huérfano (un campo/sección que se acaba de
  // borrar o renombrar y el documento Word todavía lo usa), se avisa aquí mismo, sin que el
  // admin tenga que abrir el panel de marcadores para darse cuenta.
  const alAnalizarAutomatico = (resultado) => {
    const hayProblemas = resultado.marcadoresDesconocidos.length > 0
      || resultado.camposSinUsar.length > 0
      || (resultado.tablasMalUsadas ?? []).length > 0
      || !!resultado.errorGeneracion
    if (resultado.errorGeneracion) {
      mostrarToast('Guardado, pero el documento Word ya subido NO SE PUEDE GENERAR — revisa el panel de abajo antes de que alguien intente imprimir.', 'error')
    } else if (hayProblemas) {
      mostrarToast('Guardado, pero el documento Word ya subido quedó con marcadores que no coinciden — revisa el panel de abajo antes de que alguien intente imprimir.', 'error')
    }
  }

  useEffect(() => {
    formulariosService.obtenerCamposCandidatoDisponibles().then(setDisponiblesCandidato).catch(() => {})
    formulariosService.obtenerCamposClienteDisponibles().then(setDisponiblesCliente).catch(() => {})
    formulariosService.obtenerCamposServicioDisponibles().then(setDisponiblesServicio).catch(() => {})
    api.get('/catalogo/tipos-progreso').then(r => setTiposDisponibles(r.data)).catch(() => {})
    cargarVersion()
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
        calculada: !!s.calculada,
        opcional: !!s.opcional,
        grupo: s.grupo ?? '',
        columnasOcultas: s.columnasOcultas ?? [],
        columnas: s.columnas.map(c => columnaDesdeServidor(c, true)),
        filasSugeridas: s.filasSugeridas.map(f => ({ localId: idLocal(), etiqueta: f.etiqueta })),
      }))))
      .catch(() => setError('No se pudo cargar la plantilla.'))
  }

  useEffect(() => {
    cargarSecciones()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subproceso.idTipoProgreso])

  const actualizarSecciones = (fn) => { setSecciones(prev => fn(prev)); setSucio(true) }

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
        calculada: !!s.calculada,
        opcional: !!s.opcional,
        grupo: s.grupo ?? '',
        columnasOcultas: s.columnasOcultas ?? [],
        columnas: s.columnas.map(c => columnaDesdeServidor(c, false)),
        filasSugeridas: s.filasSugeridas.map(f => ({ localId: idLocal(), etiqueta: f.etiqueta })),
      })))
      setSucio(true)
      mostrarToast('Estructura duplicada — revísala y dale Guardar cuando esté lista.')
    } catch {
      mostrarToast('No se pudo duplicar esa plantilla.', 'error')
    } finally {
      setDuplicando(false)
    }
  }

  const agregarSeccion = () => actualizarSecciones(prev => [
    ...prev, { localId: idLocal(), nombre: '', tipo: 'CAMPOS', clave: '', origenFijo: '', calculada: false, opcional: false, grupo: '', columnasOcultas: [], columnas: [], filasSugeridas: [] },
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

  // Al elegir un origen automático, se sugiere el nombre (si está vacío) y se vacían las filas
  // sugeridas — no aplican, las filas ya vienen fijas de la hoja de vida (ver TABLAS_HOJA_VIDA).
  // Las columnas NO se vacían: si ya había alguna, pasa a ser una columna de VALIDACIÓN (la llena
  // analista/poligrafista/visitador por cada fila, aparte de las columnas fijas de solo lectura —
  // ver el bloque "Columnas de validación" más abajo). Al volver a "Manual" se limpia el origen.
  const elegirOrigenFijo = (sId, valor) => actualizarSecciones(prev => prev.map(s => {
    if (s.localId !== sId) return s
    // "Calculada": la tabla la arma el sistema (ej. hallazgos de Antecedentes), no la hoja de vida.
    if (valor === '__calculada') {
      const columnas = s.columnas.length ? s.columnas : RESULTADOS_HALLAZGOS.map(r => ({
        localId: idLocal(), nombre: r.nombre, tipoDato: 'TEXTO', campoCandidato: '', campoCliente: '', campoServicio: '',
        calculado: true, valorDefecto: '', obligatorio: false, opcionesTexto: '', calificaRiesgo: false, puntajes: {},
        clave: r.clave, cal: { ...calificacionVacia(), calculo: r.valor },
      }))
      return { ...s, origenFijo: '', calculada: true, columnasOcultas: [], filasSugeridas: [], columnas }
    }
    const origenFijo = valor
    const tabla = TABLAS_HOJA_VIDA.find(t => t.clave === origenFijo)
    return {
      ...s,
      calculada: false,
      origenFijo,
      nombre: origenFijo && !s.nombre.trim() ? tabla.etiqueta : s.nombre,
      columnasOcultas: origenFijo ? s.columnasOcultas : [],
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
    s.localId === sId ? { ...s, columnas: [...s.columnas, { localId: idLocal(), nombre: '', tipoDato: 'TEXTO', campoCandidato: '', campoCliente: '', campoServicio: '', calculado: false, valorDefecto: '', obligatorio: false, opcionesTexto: '', calificaRiesgo: false, puntajes: {}, cal: calificacionVacia(), longitudMaxima: null }] } : s))
  const editarPuntaje = (sId, cId, opcion, campo, valor) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: s.columnas.map(c => c.localId === cId
      ? { ...c, puntajes: { ...(c.puntajes || {}), [opcion]: { ...(c.puntajes?.[opcion] ?? { puntos: '', esCritico: false }), [campo]: valor } } }
      : c) } : s))
  const quitarColumna = (sId, cId) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: s.columnas.filter(c => c.localId !== cId) } : s))
  // Clave del marcador a partir del título ("Lugar de expedición" → lugar_de_expedicion), igual
  // que la arma el backend.
  const claveDesde = (texto) => (texto || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 50)
  const editarColumna = (sId, cId, campo, valor) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: s.columnas.map(c => {
      if (c.localId !== cId) return c
      // En un campo nuevo, la clave sigue al título mientras no la cambien a mano (un campo ya
      // guardado conserva su clave: el Word ya la usa).
      if (campo === 'nombre' && !c.idColumna && (!c.clave || c.clave === claveDesde(c.nombre))) {
        return { ...c, nombre: valor, clave: claveDesde(valor) }
      }
      return { ...c, [campo]: valor }
    }) } : s))
  const cambiarColumna = (sId, cId, cambios) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: s.columnas.map(c => c.localId === cId ? { ...c, ...cambios } : c) } : s))

  // Al elegir un dato automático (candidato o cliente — mutuamente excluyentes, solo uno de los
  // dos queda con valor), si el título todavía está vacío se sugiere la etiqueta del campo (ej:
  // "N° de documento") — el admin la puede borrar o cambiar libremente después. tipo vacío = volver
  // a campo manual, limpia ambos.
  const elegirCampoAutomatico = (sId, cId, tipo, clave, etiqueta) => actualizarSecciones(prev => prev.map(s =>
    s.localId === sId ? { ...s, columnas: s.columnas.map(c => c.localId === cId
      ? { ...c, campoCandidato: tipo === 'candidato' ? clave : '', campoCliente: tipo === 'cliente' ? clave : '',
          campoServicio: tipo === 'servicio' ? clave : '', calculado: tipo === 'calculado',
          // Lo automático no lleva valor por defecto ni es obligatorio (nadie lo llena).
          valorDefecto: tipo ? '' : c.valorDefecto, obligatorio: tipo ? false : c.obligatorio,
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
        if (!esAutomatica(c) && c.tipoDato === 'LISTA' && !opcionesDe(c.opcionesTexto).length) {
          return `La columna "${c.nombre || '(sin título)'}" es de tipo lista desplegable — escribe al menos una opción.`
        }
        if (!esAutomatica(c) && c.tipoDato === 'LISTA' && c.valorDefecto?.trim() && !opcionesDe(c.opcionesTexto).includes(c.valorDefecto.trim())) {
          return `El valor por defecto de "${c.nombre || '(sin título)'}" debe ser una de sus opciones.`
        }
        const errorCalificacion = validarCalificacion(c, opcionesDe(c.opcionesTexto))
        if (errorCalificacion) return errorCalificacion
        if (esCalificable(c) && c.calificaRiesgo) {
          for (const r of respuestasDe(c)) {
            const n = numPuntos(c.puntajes?.[r.valor]?.puntos)
            if (Number.isNaN(n) || n < 0 || n > 100) {
              return `"${c.nombre || '(sin título)'}": los puntos de "${r.etiqueta}" deben estar entre 0 y 100.`
            }
          }
        }
        const nombreNormalizado = (c.nombre || '').trim().toLowerCase()
        if (nombreNormalizado) {
          if (nombresUsadosEnSeccion.has(nombreNormalizado)) {
            return `La sección "${s.nombre}" tiene dos columnas tituladas "${c.nombre.trim()}" — dale un título distinto a cada una.`
          }
          nombresUsadosEnSeccion.add(nombreNormalizado)
        }
        const campoAutomatico = c.campoCandidato ? `candidato:${c.campoCandidato}` : c.campoCliente ? `cliente:${c.campoCliente}`
          : c.campoServicio ? `servicio:${c.campoServicio}` : null
        if (campoAutomatico) {
          if (camposAutomaticosUsados.has(campoAutomatico)) {
            const lista = c.campoCandidato ? disponiblesCandidato : c.campoCliente ? disponiblesCliente : disponiblesServicio
            const clave = c.campoCandidato || c.campoCliente || c.campoServicio
            const etiqueta = lista.find(o => o.clave === clave)?.etiqueta ?? clave
            return `El dato "${etiqueta}" ya está usado en otra columna de esta plantilla.`
          }
          camposAutomaticosUsados.add(campoAutomatico)
        }
      }
    }
    return null
  }

  const clavesNota = (secciones ?? []).flatMap(s => [
    ...(s.tipo === 'TABLA' && !s.calculada && s.clave ? [{ clave: s.clave, etiqueta: `Tabla: ${s.nombre || s.clave}` }] : []),
    ...(s.tipo === 'CAMPOS' ? s.columnas.filter(c => !esAutomatica(c) && c.clave).map(c => ({ clave: c.clave, etiqueta: `${c.nombre || c.clave} (${s.nombre})` })) : []),
  ])

  const construirPayload = () => secciones.map(s => ({
    idSeccion: s.idSeccion ?? null,
    nombre: s.nombre.trim(),
    tipo: s.tipo,
    clave: s.clave?.trim() || null,
    origenFijo: s.origenFijo || null,
    calculada: s.tipo === 'TABLA' && !s.origenFijo && !!s.calculada,
    opcional: !!s.opcional,
    grupo: s.grupo?.trim() || null,
    columnasOcultas: s.origenFijo ? s.columnasOcultas : [],
    columnas: s.columnas.map(c => ({
      idColumna: c.idColumna ?? null,
      nombre: c.nombre.trim() || null,
      tipoDato: c.tipoDato,
      campoCandidato: c.campoCandidato || null,
      campoCliente: c.campoCliente || null,
      campoServicio: c.campoServicio || null,
      calculado: !!c.calculado,
      valorDefecto: esAutomatica(c) ? null : (c.valorDefecto?.trim() || null),
      obligatorio: !esAutomatica(c) && !!c.obligatorio,
      clave: c.clave?.trim() || null,
      opciones: c.tipoDato === 'LISTA' ? opcionesDe(c.opcionesTexto) : null,
      calificacion: calificacionParaServidor(c, opcionesDe(c.opcionesTexto)),
      longitudMaxima: !esAutomatica(c) && c.tipoDato === 'TEXTO' && c.longitudMaxima ? Number(c.longitudMaxima) : null,
      calificaRiesgo: esCalificable(c) && !!c.calificaRiesgo && !c.cal?.modo,
      puntajes: esCalificable(c) && c.calificaRiesgo && !c.cal?.modo
        ? respuestasDe(c).map(r => ({ opcion: r.valor, puntos: numPuntos(c.puntajes?.[r.valor]?.puntos), esCritico: !!c.puntajes?.[r.valor]?.esCritico }))
        : [],
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
      setSucio(false)
      mostrarToast('Plantilla guardada')
      cargarVersion()
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

  const usadosEnPlantilla = new Set((secciones ?? []).flatMap(x => x.columnas.flatMap(c => [
    ...(c.cal?.modo === 'HALLAZGO' ? opcionesDe(c.opcionesTexto) : []),
    ...(c.cal?.modo === 'OPCIONES' ? Object.values(c.cal.porOpcion ?? {}).filter(Boolean) : []),
  ])))

  const totalCampos = (secciones ?? []).reduce((n, x) => n + x.columnas.length, 0)
  const ICONO_TIPO_DATO = { TEXTO: 'Aa', NUMERO: '#', FECHA: '📅', BOOLEANO: '✓', LISTA: '▾', ARCHIVO: '📎', CIUDAD: '📍', BARRIO: '🏘' }
  const ctlCls = 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-gray-50 disabled:text-gray-400'
  const PASOS_FIJOS = [
    { id: 'word', titulo: 'Documento Word', detalle: 'Plantilla y marcadores', icono: '📄', color: 'sky' },
    { id: 'anexo', titulo: 'Anexo 1 · Catálogo', detalle: `${catalogoHallazgos.filter(h => usadosEnPlantilla.has(h.codigo)).length} hallazgos de esta plantilla`, icono: 'A1', color: 'amber' },
    { id: 'declaraciones', titulo: 'Declaraciones', detalle: `${preguntasCandidato.filter(p => p.activo).length} preguntas del candidato`, icono: '?', color: 'violet' },
    { id: 'archivados', titulo: 'Campos archivados', detalle: 'Restaurar campos', icono: '↺', color: 'gray' },
  ]
  const COLOR_PASO = {
    sky: { on: 'bg-sky-50 ring-1 ring-sky-200', badgeOn: 'bg-sky-500 border-sky-500 text-white', badge: 'bg-white border-sky-300 text-sky-500', texto: 'text-sky-800' },
    amber: { on: 'bg-amber-50 ring-1 ring-amber-200', badgeOn: 'bg-amber-500 border-amber-500 text-white', badge: 'bg-white border-amber-300 text-amber-600', texto: 'text-amber-800' },
    violet: { on: 'bg-violet-50 ring-1 ring-violet-200', badgeOn: 'bg-violet-500 border-violet-500 text-white', badge: 'bg-white border-violet-300 text-violet-500', texto: 'text-violet-800' },
    gray: { on: 'bg-gray-100 ring-1 ring-gray-200', badgeOn: 'bg-gray-600 border-gray-600 text-white', badge: 'bg-white border-gray-300 text-gray-500', texto: 'text-gray-800' },
  }
  const ordenPasos = ['word', 'anexo', 'declaraciones', 'archivados', ...(secciones ?? []).map(x => x.localId)]
  const pasoActual = paso && ordenPasos.includes(paso) ? paso : (secciones?.[0]?.localId ?? 'word')
  const iPaso = ordenPasos.indexOf(pasoActual)
  const irA = (id) => { setPaso(id); setCampoSel(null) }
  const seccionActual = (secciones ?? []).find(x => x.localId === pasoActual) ?? null
  const idxSeccion = seccionActual ? secciones.indexOf(seccionActual) : -1
  const nuevaSeccion = () => {
    agregarSeccion()
    setTimeout(() => setSecciones(prev => { if (prev?.length) { setPaso(prev[prev.length - 1].localId); setCampoSel(null) } return prev }), 0)
  }
  const nuevoCampo = (sId) => {
    agregarColumna(sId)
    setTimeout(() => setSecciones(prev => {
      const sec = prev?.find(x => x.localId === sId)
      if (sec?.columnas.length) setCampoSel(sec.columnas[sec.columnas.length - 1].localId)
      return prev
    }), 0)
  }
  const moverCampo = (sId, i, d) => actualizarSecciones(prev => prev.map(x => {
    if (x.localId !== sId) return x
    const j = i + d
    if (j < 0 || j >= x.columnas.length) return x
    const columnas = [...x.columnas]
    ;[columnas[i], columnas[j]] = [columnas[j], columnas[i]]
    return { ...x, columnas }
  }))

  // Los campos comparten un solo ${campos[...]} en todo el Word; las columnas de tabla solo
  // tienen que ser únicas dentro de su tabla.
  const llaveClave = (x, c) => x.tipo === 'TABLA' ? `${x.localId}:${c.clave}` : `campos:${c.clave}`
  const conteoClaves = {}
  ;(secciones ?? []).forEach(x => x.columnas.forEach(c => {
    if (c.clave) conteoClaves[llaveClave(x, c)] = (conteoClaves[llaveClave(x, c)] ?? 0) + 1
  }))
  const copiar = (texto) => { try { navigator.clipboard?.writeText(texto) } catch { /* sin portapapeles */ } mostrarToast(`Copiado: ${texto}`) }

  // Vista previa del control tal como lo verá quien diligencia.
  const vistaControl = (c) => {
    const base = 'w-full rounded-lg border border-gray-200 bg-gray-50/60 px-3 py-2 text-sm text-gray-400 truncate'
    if (c.calculado) return <div className={`${base} italic`}>Lo calcula el sistema</div>
    if (c.campoCandidato || c.campoCliente || c.campoServicio) return <div className={`${base} italic`}>Se llena solo</div>
    if (c.tipoDato === 'LISTA') {
      const ops = opcionesDe(c.opcionesTexto)
      return <div className={`${base} flex justify-between`}><span className="truncate">{c.valorDefecto || ops[0] || 'Sin opciones'}</span><span>▾</span></div>
    }
    if (c.tipoDato === 'BOOLEANO') return <div className={`${base} flex justify-between`}><span>Sí / No</span><span>▾</span></div>
    if (c.tipoDato === 'FECHA') return <div className={base}>dd/mm/aaaa</div>
    if (c.tipoDato === 'NUMERO') return <div className={base}>0</div>
    if (c.tipoDato === 'ARCHIVO') return <div className={base}>📎 Adjuntar archivo</div>
    if (c.tipoDato === 'CIUDAD' || c.tipoDato === 'BARRIO') return <div className={base}>Buscar en el catálogo…</div>
    return <div className={base}>{c.valorDefecto || 'Texto'}{c.longitudMaxima ? ` · máx. ${c.longitudMaxima}` : ''}</div>
  }

  // Tarjeta de un campo en la cuadrícula (como en el formulario) — clic para editarlo.
  const tarjetaCampo = (s, c, i) => {
    const elegido = campoSel === c.localId
    const automatico = esAutomatica(c)
    return (
      <div key={c.localId} onClick={() => setCampoSel(elegido ? null : c.localId)}
        className={`group relative cursor-pointer rounded-xl border p-3 transition-all ${elegido
          ? 'border-primary-400 bg-primary-50/40 ring-2 ring-primary-200' : 'border-transparent hover:border-gray-200 hover:bg-gray-50/70'}`}>
        <label className="flex items-center gap-1.5 text-xs font-medium text-gray-600 mb-1 pointer-events-none">
          <span className="flex-shrink-0 w-4 h-4 rounded bg-gray-100 text-gray-400 text-[9px] flex items-center justify-center">
            {c.campoCandidato ? '👤' : c.campoCliente ? '🏢' : c.campoServicio ? '🗂' : c.calculado ? 'ƒ' : ICONO_TIPO_DATO[c.tipoDato] ?? ''}
          </span>
          <span className={`truncate ${c.nombre?.trim() ? '' : 'italic text-gray-400'}`}>{c.nombre?.trim() || '(sin título)'}</span>
          {c.obligatorio && <span className="text-red-500">*</span>}
          {automatico && (
            <span className="flex-shrink-0 text-[9px] font-semibold px-1 py-0.5 rounded bg-primary-50 text-primary-500">{c.calculado ? 'calculado' : 'auto'}</span>
          )}
          {(c.cal?.modo === 'HALLAZGO' || c.cal?.modo === 'OPCIONES') && (
            <span className="flex-shrink-0 text-[9px] font-semibold px-1 py-0.5 rounded bg-amber-50 text-amber-700">hallazgo</span>
          )}
        </label>
        {vistaControl(c)}
        {(() => {
          const marcador = s.tipo === 'TABLA' ? `\${['${c.clave}']}` : `\${campos['${c.clave}']}`
          const repetida = c.clave && conteoClaves[llaveClave(s, c)] > 1
          return c.clave ? (
            <div className="mt-1.5 flex items-center gap-1" onClick={e => e.stopPropagation()}>
              <code title="Marcador para el Word" className={`min-w-0 flex-1 truncate rounded px-1.5 py-0.5 font-mono text-[10.5px] ${
                repetida ? 'bg-red-50 text-red-600 ring-1 ring-inset ring-red-200' : 'bg-gray-100 text-gray-600'}`}>{marcador}</code>
              {repetida && <span className="text-[10px] font-semibold text-red-600" title="Otra columna ya usa esta clave">repetida</span>}
              <button type="button" onClick={() => copiar(marcador)} title="Copiar marcador"
                className="flex-shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium text-gray-400 hover:bg-gray-100 hover:text-primary-700">Copiar</button>
            </div>
          ) : (
            <p className="mt-1.5 text-[10.5px] italic text-gray-300">Escribe el título y se genera el marcador</p>
          )
        })()}
        <div className="absolute right-2 top-2 hidden gap-0.5 rounded-lg border border-gray-200 bg-white shadow-sm group-hover:flex" onClick={e => e.stopPropagation()}>
          <button type="button" title="Mover antes" disabled={i === 0} onClick={() => moverCampo(s.localId, i, -1)} className="px-1.5 py-1 text-xs text-gray-400 hover:text-gray-700 disabled:opacity-30">←</button>
          <button type="button" title="Mover después" disabled={i === s.columnas.length - 1} onClick={() => moverCampo(s.localId, i, 1)} className="px-1.5 py-1 text-xs text-gray-400 hover:text-gray-700 disabled:opacity-30">→</button>
          <button type="button" title="Quitar campo" onClick={() => { quitarColumna(s.localId, c.localId); if (elegido) setCampoSel(null) }} className="px-1.5 py-1 text-xs text-gray-400 hover:text-red-600">✕</button>
        </div>
      </div>
    )
  }

  // Edición del campo elegido, debajo de la cuadrícula.
  const editorCampo = (s, c) => {
    const automatica = esAutomatica(c)
    const listaPropia = !automatica && c.tipoDato === 'LISTA' && c.cal?.modo !== 'HALLAZGO' && c.cal?.modo !== 'CRITICIDAD'
    return (
      <div className="mt-4 rounded-2xl border border-primary-200 bg-white shadow-sm">
        <div className="flex items-center justify-between gap-3 rounded-t-2xl border-b border-primary-100 bg-primary-50/70 px-4 py-2.5">
          <p className="text-xs font-semibold uppercase tracking-wider text-primary-700">Editar campo · {c.nombre?.trim() || '(sin título)'}</p>
          <button type="button" onClick={() => setCampoSel(null)} className="text-xs font-medium text-primary-700 hover:underline">Listo</button>
        </div>
        <div className="space-y-4 p-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-3">
            <div className="lg:col-span-2">
              <label htmlFor="col-nombre" className="block text-xs font-medium text-gray-600 mb-1">Título que ve quien diligencia</label>
              <input id="col-nombre" value={c.nombre} onChange={e => editarColumna(s.localId, c.localId, 'nombre', e.target.value)} autoFocus className={ctlCls} />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">¿De dónde sale el valor?</label>
              <select aria-label="¿De dónde sale el valor?" value={c.campoCandidato ? `candidato:${c.campoCandidato}` : c.campoCliente ? `cliente:${c.campoCliente}`
                : c.campoServicio ? `servicio:${c.campoServicio}` : c.calculado ? 'calculado:' : ''}
                disabled={s.calculada} className={ctlCls}
                onChange={e => {
                  const valor = e.target.value
                  if (!valor) { elegirCampoAutomatico(s.localId, c.localId, '', '', ''); return }
                  const [tipo, clave] = valor.split(':')
                  const lista = tipo === 'candidato' ? disponiblesCandidato : tipo === 'cliente' ? disponiblesCliente : disponiblesServicio
                  elegirCampoAutomatico(s.localId, c.localId, tipo, clave, lista.find(o => o.clave === clave)?.etiqueta ?? '')
                }}>
                <option value="">Lo llena quien diligencia</option>
                <optgroup label="Datos del candidato">{disponiblesCandidato.map(o => <option key={`cand-${o.clave}`} value={`candidato:${o.clave}`}>{o.etiqueta}</option>)}</optgroup>
                <optgroup label="Datos del cliente">{disponiblesCliente.map(o => <option key={`cli-${o.clave}`} value={`cliente:${o.clave}`}>{o.etiqueta}</option>)}</optgroup>
                <optgroup label="Datos del estudio">{disponiblesServicio.map(o => <option key={`srv-${o.clave}`} value={`servicio:${o.clave}`}>{o.etiqueta}</option>)}</optgroup>
                <option value="calculado:">Lo calcula el sistema</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Tipo de dato</label>
              <select aria-label="Tipo de dato" value={c.tipoDato} onChange={e => editarColumna(s.localId, c.localId, 'tipoDato', e.target.value)}
                disabled={automatica} className={ctlCls}>
                {TIPOS_DATO.map(td => <option key={td.value} value={td.value}>{td.label}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Clave (marcador del Word)</label>
              <input aria-label="Clave del campo" value={c.clave} onChange={e => editarColumna(s.localId, c.localId, 'clave', e.target.value)}
                placeholder="se genera sola" className={`${ctlCls} font-mono text-xs`} />
            </div>
            {!automatica && (
              <>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Valor por defecto</label>
                  {c.tipoDato === 'LISTA' ? (
                    <select aria-label="Valor por defecto" value={c.valorDefecto ?? ''} onChange={e => editarColumna(s.localId, c.localId, 'valorDefecto', e.target.value)} className={ctlCls}>
                      <option value="">Ninguno</option>
                      {opcionesDe(c.opcionesTexto).map(o => <option key={o} value={o}>{o}</option>)}
                    </select>
                  ) : (
                    <input aria-label="Valor por defecto" value={c.valorDefecto ?? ''} onChange={e => editarColumna(s.localId, c.localId, 'valorDefecto', e.target.value)}
                      placeholder="Ninguno" maxLength={200} className={ctlCls} />
                  )}
                </div>
                {c.tipoDato === 'TEXTO' && (
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Máximo de caracteres</label>
                    <input type="number" aria-label="Máximo de caracteres" min={1} max={10000} value={c.longitudMaxima ?? ''} placeholder="2000"
                      onChange={e => editarColumna(s.localId, c.localId, 'longitudMaxima', e.target.value === '' ? null : Number(e.target.value))}
                      className={`${ctlCls} tabular-nums`} />
                  </div>
                )}
                <label className="flex items-end gap-2 pb-2 text-sm text-gray-700 cursor-pointer">
                  <input type="checkbox" checked={!!c.obligatorio} onChange={e => editarColumna(s.localId, c.localId, 'obligatorio', e.target.checked)}
                    className="mb-0.5 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                  Obligatorio
                </label>
              </>
            )}
          </div>
          {listaPropia && (
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Opciones de la lista <span className="font-normal text-gray-400">· una por línea · {opcionesDe(c.opcionesTexto).length}</span></label>
              <textarea aria-label="Opciones de la lista, una por línea" value={c.opcionesTexto ?? ''} rows={Math.min(8, Math.max(3, opcionesDe(c.opcionesTexto).length + 1))}
                onChange={e => editarColumna(s.localId, c.localId, 'opcionesTexto', e.target.value)}
                placeholder={'Sí\nNo\nNo aplica'} className={`${ctlCls} leading-relaxed`} />
            </div>
          )}
          {!s.origenFijo && (!automatica || c.calculado) && (
            <CalificacionColumna c={c} seccion={s} catalogo={catalogoHallazgos} opciones={opcionesDe(c.opcionesTexto)}
              claves={clavesNota} onCambio={cambios => cambiarColumna(s.localId, c.localId, cambios)} />
          )}
          {esCalificable(c) && !c.cal?.modo && (
            <div>
              <label className="inline-flex items-center gap-2 text-xs text-gray-600 cursor-pointer">
                <input type="checkbox" checked={!!c.calificaRiesgo} onChange={e => editarColumna(s.localId, c.localId, 'calificaRiesgo', e.target.checked)}
                  className="rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                <span className="font-medium">Puntos por respuesta</span>
                <span className="text-gray-400">— puntos fijos a cada opción, sin catálogo{s.tipo === 'TABLA' ? ' (suma por fila)' : ''}</span>
              </label>
              {c.calificaRiesgo && <PuntajesColumna c={c} onPuntaje={(opcion, campo, valor) => editarPuntaje(s.localId, c.localId, opcion, campo, valor)} />}
            </div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-6xl mx-auto">
      <div className="mb-3"><VolverCatalogo /></div>

      {/* Encabezado: el mismo del formulario del analista (franja, bloque principal y barra de acciones) */}
      <header className="mb-5 bg-white border border-primary-100 rounded-2xl shadow-sm shadow-primary-100/40 overflow-hidden">
        {/* Franja: qué plantilla se está armando */}
        <div className="px-5 sm:px-6 py-3 bg-primary-50/70 border-b border-primary-100 flex flex-wrap items-center gap-x-3 gap-y-2">
          <div className="min-w-0 flex-1 flex flex-wrap items-stretch gap-x-6 gap-y-2">
            <div className="min-w-0">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-primary-500">Subservicio</p>
              <p className="text-sm font-semibold text-primary-900 truncate">{subproceso.nombreProgreso}</p>
            </div>
            <div className="min-w-0 sm:pl-6 sm:border-l sm:border-primary-100">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-primary-500">Lo diligencia</p>
              <p className="text-sm font-semibold text-primary-900 truncate">
                {{ ANALISTA_INTERNO: 'Analista', POLIGRAFISTA: 'Poligrafista', VISITADOR: 'Visitador' }[subproceso.rolResponsable] ?? subproceso.rolResponsable ?? '—'}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-1.5">
            {versionPlantilla != null && (
              <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-white border border-primary-100 text-primary-700">Versión {versionPlantilla}</span>
            )}
            <span className="inline-flex items-center gap-1.5 text-[11px] font-medium px-2 py-0.5 rounded-full bg-white border border-primary-100 text-gray-700">
              <span className={`w-1.5 h-1.5 rounded-full ${configPlantilla?.plantillaWordRuta ? 'bg-green-500' : 'bg-amber-500'}`} />
              {configPlantilla?.plantillaWordRuta ? 'Con documento Word' : 'Sin documento Word'}
            </span>
          </div>
        </div>

        {/* Plantilla + resumen */}
        <div className="px-5 sm:px-6 pt-4 pb-4 flex flex-col md:flex-row md:items-center gap-5">
          <div className="flex items-start gap-4 min-w-0 flex-1">
            <div className="flex-shrink-0 w-12 h-12 rounded-xl bg-primary-600 text-white flex items-center justify-center shadow-sm shadow-primary-200">
              <IconoArchivo />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400">Plantilla del formulario</p>
              <h2 className="text-xl font-bold text-gray-900 truncate">{subproceso.nombreProgreso}</h2>
              <dl className="mt-2.5 grid grid-cols-2 sm:grid-cols-4 gap-x-6 gap-y-2">
                {[
                  { etiqueta: 'Secciones', valor: secciones?.length },
                  { etiqueta: 'Campos', valor: secciones ? totalCampos : null },
                  { etiqueta: 'Hallazgos', valor: secciones ? catalogoHallazgos.filter(h => usadosEnPlantilla.has(h.codigo)).length : null },
                  { etiqueta: 'Última carga del Word', valor: configPlantilla?.fechaCarga ? new Date(configPlantilla.fechaCarga).toLocaleDateString('es-CO') : null },
                ].map(d => (
                  <div key={d.etiqueta} className="min-w-0">
                    <dt className="text-[11px] text-gray-400">{d.etiqueta}</dt>
                    <dd className={`text-sm truncate ${d.valor != null ? 'font-medium text-gray-700' : 'text-gray-300'}`}>{d.valor ?? '—'}</dd>
                  </div>
                ))}
              </dl>
            </div>
          </div>
        </div>

        {/* Barra de acciones */}
        <div className="px-5 sm:px-6 py-3 bg-primary-50/40 border-t border-primary-100/70 flex flex-wrap items-center gap-2">
          <span className={`inline-flex items-center gap-1.5 text-xs font-medium mr-auto ${sucio ? 'text-gray-700' : 'text-gray-500'}`}>
            <span className={`w-2 h-2 rounded-full ${guardando ? 'bg-primary-400 animate-pulse' : sucio ? 'bg-amber-500' : 'bg-green-500'}`} />
            {guardando ? 'Guardando…' : sucio ? 'Cambios sin guardar' : 'Todo guardado'}
          </span>
          {secciones?.length > 0 && (
            <button type="button" onClick={verVistaPrevia} disabled={generandoPreview}
              className="inline-flex items-center gap-1.5 border border-primary-100 bg-white text-gray-700 hover:bg-primary-50 disabled:opacity-50 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors">
              <IconoOjo /> {generandoPreview ? 'Generando…' : 'Vista previa Word'}
            </button>
          )}
          <button type="button" onClick={intentarGuardar} disabled={guardando || secciones === null}
            className="inline-flex items-center gap-1.5 disabled:opacity-60 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors bg-primary-600 hover:bg-primary-700 text-white shadow-sm">
            <IconoGuardar /> {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </header>

      {error && <div className="mb-4 whitespace-pre-line bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

      {secciones === null ? (
        <div className="flex justify-center py-16">
          <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
        </div>
      ) : (
      <div className="lg:grid lg:grid-cols-[260px_minmax(0,1fr)] lg:gap-6 lg:items-start">
        {/* Índice de pasos (mismo estilo del formulario del analista) */}
        <aside className="mb-4 lg:mb-0 lg:sticky lg:top-4">
          <nav className="bg-white border border-gray-200 rounded-2xl shadow-sm p-3 lg:max-h-[calc(100vh-2rem)] lg:overflow-y-auto overscroll-contain">
            <ol className="flex lg:flex-col gap-1 overflow-x-auto lg:overflow-visible -mx-1 px-1 pb-1 lg:pb-0">
              {PASOS_FIJOS.map((f, k) => {
                const activo = pasoActual === f.id
                const col = COLOR_PASO[f.color]
                return (
                  <li key={f.id} className={`flex-shrink-0 lg:flex-shrink ${k === PASOS_FIJOS.length - 1 ? 'lg:mb-1 lg:pb-1 lg:border-b lg:border-gray-100' : ''}`}>
                    <button type="button" onClick={() => irA(f.id)}
                      className={`w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 transition-colors ${activo ? col.on : 'hover:bg-gray-50'}`}>
                      <span className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center border-2 text-[10px] font-bold ${activo ? col.badgeOn : col.badge}`}>{f.icono}</span>
                      <span className="min-w-0 flex-1">
                        <span className={`block text-xs font-medium ${activo ? col.texto : 'text-gray-700'}`}>{f.titulo}</span>
                        <span className="block text-[10px] text-gray-400">{f.detalle}</span>
                      </span>
                    </button>
                  </li>
                )
              })}
              {secciones.map((x, k) => {
                const activo = pasoActual === x.localId
                const fija = x.tipo === 'TABLA' && x.origenFijo
                const grupoNuevo = x.grupo?.trim() && x.grupo.trim() !== (secciones[k - 1]?.grupo ?? '').trim()
                return (
                  <Fragment key={x.localId}>
                  {grupoNuevo && (
                    <li aria-hidden="true" className="hidden lg:block px-2.5 pt-3 pb-1 text-[10px] font-semibold uppercase tracking-wider text-gray-500">
                      {x.grupo.trim()}
                    </li>
                  )}
                  <li className="flex-shrink-0 lg:flex-shrink">
                    <button type="button" onClick={() => irA(x.localId)}
                      className={`w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 transition-colors ${activo ? 'bg-primary-50 ring-1 ring-primary-200' : 'hover:bg-gray-50'}`}>
                      <span className={`flex-shrink-0 w-6 h-6 rounded-full text-[11px] font-bold flex items-center justify-center border-2 ${
                        activo ? 'bg-primary-600 border-primary-600 text-white'
                          : fija ? 'bg-red-50 border-red-300 text-red-600'
                          : x.tipo === 'TABLA' ? 'bg-violet-50 border-violet-300 text-violet-600' : 'bg-white border-gray-300 text-gray-500'}`}>
                        {k + 1}
                      </span>
                      <span className="min-w-0">
                        <span className={`block text-xs font-medium truncate max-w-[160px] lg:max-w-none ${activo ? 'text-primary-700' : x.nombre.trim() ? 'text-gray-700' : 'italic text-red-500'}`}>
                          {x.nombre.trim() || 'Sección sin nombre'}
                        </span>
                        <span className="block text-[10px] text-gray-400">
                          {x.tipo === 'TABLA' ? (x.calculada ? 'Tabla del sistema' : fija ? 'Hoja de vida' : 'Tabla') : 'Campos'} · {x.columnas.length} campo{x.columnas.length === 1 ? '' : 's'}{x.opcional ? ' · opcional' : ''}
                        </span>
                      </span>
                    </button>
                  </li>
                  </Fragment>
                )
              })}
              <li className="flex-shrink-0 lg:flex-shrink lg:mt-1 lg:pt-1 lg:border-t lg:border-gray-100">
                <button type="button" onClick={nuevaSeccion}
                  className="w-full flex items-center gap-2.5 text-left rounded-xl px-2.5 py-2 text-primary-700 hover:bg-primary-50 transition-colors">
                  <span className="flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center border-2 border-dashed border-primary-300 text-primary-500"><IconoMas /></span>
                  <span className="text-xs font-medium">Agregar sección</span>
                </button>
              </li>
            </ol>
          </nav>
        </aside>

        <div className="space-y-4 min-w-0">
          {pasoActual === 'word' && (
            <div>
              {secciones.length === 0 && tiposDisponibles.filter(t => t.idTipoProgreso !== subproceso.idTipoProgreso).length > 0 && (
                <div className="mb-4 flex flex-wrap items-center gap-2 rounded-2xl border border-dashed border-gray-300 bg-white px-4 py-3">
                  <p className="flex-1 text-sm text-gray-500">Esta plantilla está vacía. Puedes empezar copiando otra:</p>
                  <select aria-label="Duplicar desde" value={origenDuplicar} onChange={e => setOrigenDuplicar(e.target.value)}
                    className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
                    <option value="">Duplicar desde…</option>
                    {tiposDisponibles.filter(t => t.idTipoProgreso !== subproceso.idTipoProgreso).map(t => (
                      <option key={t.idTipoProgreso} value={t.idTipoProgreso}>{t.nombreProgreso}</option>
                    ))}
                  </select>
                  <button type="button" onClick={() => duplicarDesde(origenDuplicar)} disabled={!origenDuplicar || duplicando}
                    className="px-4 py-2 rounded-lg text-sm font-medium bg-primary-600 text-white hover:bg-primary-700 disabled:opacity-50">
                    {duplicando ? 'Duplicando…' : 'Duplicar'}
                  </button>
                </div>
              )}
            </div>
          )}
          <div className={pasoActual === 'word' ? '' : 'hidden'}>
            <PlantillaWordPanel ref={panelWordRef} idTipoProgreso={subproceso.idTipoProgreso} nombreSubproceso={subproceso.nombreProgreso}
              secciones={secciones} catalogo={catalogoHallazgos} usados={usadosEnPlantilla} obtenerPayload={construirPayload} onError={m => mostrarToast(m, 'error')}
              onAnalizado={alAnalizarAutomatico} />
          </div>
          <div className={pasoActual === 'archivados' ? '' : 'hidden'}>
            <CamposAntiguos idTipoProgreso={subproceso.idTipoProgreso} onRestaurado={cargarSecciones} onError={m => mostrarToast(m, 'error')} />
          </div>
          {pasoActual === 'anexo' && (
            <PanelAnexo catalogo={catalogoHallazgos} nombreServicio={subproceso.nombreProgreso} usados={usadosEnPlantilla} />
          )}
          {pasoActual === 'declaraciones' && <PanelDeclaraciones preguntas={preguntasCandidato} />}

          {seccionActual && (() => {
            const s = seccionActual
            const fija = s.tipo === 'TABLA' && s.origenFijo
            const acento = fija ? 'border-l-red-400' : s.tipo === 'TABLA' ? 'border-l-violet-400' : 'border-l-primary-400'
            const badge = fija ? 'bg-red-50 text-red-600' : s.tipo === 'TABLA' ? 'bg-violet-50 text-violet-600' : 'bg-primary-50 text-primary-600'
            const chip = fija ? 'bg-red-100 text-red-700' : s.tipo === 'TABLA' ? 'bg-violet-100 text-violet-700' : 'bg-primary-100 text-primary-700'
            const campoEditado = s.columnas.find(c => c.localId === campoSel)
            const cuadricula = (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-2 gap-y-1">
                {s.columnas.map((c, i) => tarjetaCampo(s, c, i))}
                {!s.calculada && (
                  <button type="button" onClick={() => nuevoCampo(s.localId)}
                    className="flex min-h-[5.5rem] flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-gray-200 text-sm font-medium text-gray-400 hover:border-primary-300 hover:bg-primary-50/40 hover:text-primary-600 transition-colors">
                    <IconoMas /> Agregar campo
                  </button>
                )}
              </div>
            )
            return (
              <section className={`bg-white border border-gray-200 border-l-4 ${acento} rounded-2xl shadow-sm p-4`}>
                {/* Encabezado de la sección, como en el formulario */}
                <div className="flex flex-wrap items-center gap-2 mb-3">
                  <span className={`flex-shrink-0 w-6 h-6 rounded-full ${badge} text-[11px] font-bold flex items-center justify-center`}>{idxSeccion + 1}</span>
                  <input aria-label="Nombre de la sección" value={s.nombre} onChange={e => editarSeccion(s.localId, 'nombre', e.target.value)} placeholder="NOMBRE DE LA SECCIÓN"
                    className="min-w-[12rem] flex-1 rounded-md border border-transparent bg-transparent px-1.5 py-1 text-xs font-semibold uppercase tracking-wider text-gray-600 placeholder:text-gray-300 hover:border-gray-200 focus:border-primary-400 focus:bg-white focus:outline-none" />
                  <span className={`text-[9px] font-semibold px-1.5 py-0.5 rounded-full ${chip}`}>
                    {s.tipo === 'TABLA' ? (s.calculada ? 'Tabla del sistema' : fija ? 'Hoja de vida' : 'Tabla') : 'Campos'}
                  </span>
                  <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-500">{s.columnas.length} campos</span>
                  {s.opcional && <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-700">Opcional</span>}
                  <div className="ml-auto flex items-center">
                    <button type="button" onClick={() => moverSeccion(idxSeccion, -1)} disabled={idxSeccion === 0} title="Subir sección"
                      className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30"><IconoFlechaArriba /></button>
                    <button type="button" onClick={() => moverSeccion(idxSeccion, 1)} disabled={idxSeccion === secciones.length - 1} title="Bajar sección"
                      className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30"><IconoFlechaAbajo /></button>
                    <button type="button" title="Quitar sección" onClick={() => { quitarSeccion(s.localId); irA(secciones[Math.max(0, idxSeccion - 1)]?.localId ?? 'word') }}
                      className="p-1.5 rounded-lg text-red-400 hover:bg-red-50 hover:text-red-600"><IconoPapelera /></button>
                  </div>
                </div>

                {/* Configuración de la sección */}
                <div className="mb-4 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl bg-gray-50 px-3 py-2.5">
                  <div className="inline-flex rounded-lg bg-white p-0.5 ring-1 ring-gray-200">
                    {['CAMPOS', 'TABLA'].map(t => (
                      <button key={t} type="button" onClick={() => editarSeccion(s.localId, 'tipo', t)}
                        className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors ${s.tipo === t
                          ? (t === 'TABLA' ? 'bg-violet-600 text-white' : 'bg-primary-600 text-white') : 'text-gray-500 hover:text-gray-700'}`}>
                        {t === 'CAMPOS' ? 'Campos' : 'Tabla de filas'}
                      </button>
                    ))}
                  </div>
                  {s.tipo === 'TABLA' && (
                    <label className="flex items-center gap-2 text-xs text-gray-500">
                      Filas:
                      <select value={s.calculada ? '__calculada' : s.origenFijo} onChange={e => elegirOrigenFijo(s.localId, e.target.value)}
                        className="border border-gray-200 rounded-lg px-2 py-1 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
                        <option value="">las llena quien diligencia</option>
                        <option value="__calculada">las arma el sistema (hallazgos)</option>
                        {TABLAS_HOJA_VIDA.map(t => {
                          const usado = secciones.some(o => o.localId !== s.localId && o.origenFijo === t.clave)
                          return <option key={t.clave} value={t.clave} disabled={usado}>hoja de vida — {t.etiqueta}{usado ? ' (ya usada)' : ''}</option>
                        })}
                      </select>
                    </label>
                  )}
                  <div className="inline-flex rounded-lg bg-white p-0.5 ring-1 ring-gray-200" title="¿Quien diligencia puede quitar esta sección de su formulario?">
                    {[[false, 'Obligatoria'], [true, 'Opcional']].map(([valor, texto]) => (
                      <button key={texto} type="button" onClick={() => editarSeccion(s.localId, 'opcional', valor)}
                        className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors ${!!s.opcional === valor
                          ? (valor ? 'bg-amber-500 text-white' : 'bg-gray-700 text-white') : 'text-gray-500 hover:text-gray-700'}`}>
                        {texto}
                      </button>
                    ))}
                  </div>
                  <label className="flex items-center gap-2 text-xs text-gray-500" title="Clasifica la sección en el índice del formulario">
                    Grupo:
                    <input value={s.grupo ?? ''} onChange={e => editarSeccion(s.localId, 'grupo', e.target.value)} maxLength={60}
                      list={`${idGrupos}-${s.localId}`} placeholder="Sin grupo"
                      className="w-44 border border-gray-200 rounded-lg px-2 py-1 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-primary-500" />
                    <datalist id={`${idGrupos}-${s.localId}`}>
                      {[...new Set(secciones.map(o => o.grupo?.trim()).filter(Boolean))].map(g => <option key={g} value={g} />)}
                    </datalist>
                  </label>
                  {s.tipo === 'TABLA' && !s.origenFijo && (
                    <label className="flex items-center gap-2 text-xs text-gray-500">
                      Clave Word:
                      <input value={s.clave} onChange={e => editarSeccion(s.localId, 'clave', e.target.value)} placeholder={`seccion_${idxSeccion + 1}`}
                        className="w-32 border border-gray-200 rounded-lg px-2 py-1 text-xs font-mono bg-white focus:outline-none focus:ring-2 focus:ring-primary-500" />
                    </label>
                  )}
                </div>

                {s.opcional && (
                  <div className="mb-3 rounded-xl border border-amber-200 bg-amber-50/60 px-3 py-2.5 text-xs text-amber-900">
                    <p><b>Quien diligencia la puede quitar</b> con el botón "Quitar sección" de su formulario: no la llena, sus campos obligatorios no lo frenan y lo que hubiera escrito en ella se borra.</p>
                    <p className="mt-1">
                      Para que tampoco salga en el Word, selecciona en el documento <b>todo</b> lo de esta sección (título, textos y tablas) y ponle el comentario{' '}
                      {s.clave
                        ? <>
                            <code className="rounded bg-white px-1.5 py-0.5 font-mono text-[11px] text-gray-800 ring-1 ring-amber-200">{`repeatDocPart(secciones['${s.clave}'])`}</code>
                            <button type="button" onClick={() => copiar(`repeatDocPart(secciones['${s.clave}'])`)}
                              className="ml-1 rounded px-1.5 py-0.5 text-[10px] font-medium text-amber-700 hover:bg-amber-100">Copiar</button>
                          </>
                        : <span className="italic">repeatDocPart(secciones['…']) — guarda la plantilla para ver la clave de esta sección.</span>}
                    </p>
                  </div>
                )}

                {fija && (
                  <div className="mb-3 flex flex-wrap items-center gap-2 rounded-xl border border-red-100 bg-red-50/40 px-3 py-2.5">
                    <span className="text-xs font-medium text-red-700">Columnas de la hoja de vida que se ven:</span>
                    {TABLAS_HOJA_VIDA.find(t => t.clave === s.origenFijo)?.columnas.map(c => {
                      const visible = !s.columnasOcultas.includes(c)
                      return (
                        <button key={c} type="button" onClick={() => toggleColumnaOculta(s.localId, c)}
                          className={`rounded-full px-2.5 py-0.5 text-[11px] font-medium ring-1 ring-inset ${visible ? 'bg-white text-red-700 ring-red-200' : 'text-gray-400 ring-gray-200 line-through'}`}>{c}</button>
                      )
                    })}
                  </div>
                )}

                {/* Campos, como los ve quien diligencia */}
                {s.tipo === 'TABLA' ? (
                  <div className="relative bg-gray-50/70 border border-gray-100 rounded-xl p-4">
                    <div className="flex items-center gap-2 mb-3">
                      <p className="text-sm font-semibold text-gray-700">{fija ? 'Validación de cada fila' : s.calculada ? 'Cada fila de la tabla (la arma el sistema)' : 'Cada fila de la tabla'}</p>
                      {!fija && !s.calculada && s.filasSugeridas.length > 0 && <span className="text-[10px] text-gray-400">· {s.filasSugeridas.length} filas ya puestas</span>}
                    </div>
                    {cuadricula}
                  </div>
                ) : cuadricula}

                {campoEditado && editorCampo(s, campoEditado)}

                {s.tipo === 'TABLA' && !s.origenFijo && !s.calculada && (
                  <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
                    <span className="mr-1 text-xs font-medium text-gray-500">Filas ya puestas:</span>
                    {s.filasSugeridas.map(f => (
                      <span key={f.localId} className="inline-flex items-center gap-1 rounded-full bg-violet-50 py-0.5 pl-3 pr-1 ring-1 ring-inset ring-violet-200">
                        <input aria-label="Fuente de la fila sugerida" value={f.etiqueta} onChange={e => editarFilaSugerida(s.localId, f.localId, e.target.value)}
                          placeholder="Ej: Procuraduría" size={Math.max(10, (f.etiqueta || '').length)}
                          className="bg-transparent text-xs text-violet-800 focus:outline-none" />
                        <button type="button" onClick={() => quitarFilaSugerida(s.localId, f.localId)} className="rounded-full px-1.5 text-xs text-violet-300 hover:text-red-500">×</button>
                      </span>
                    ))}
                    <button type="button" onClick={() => agregarFilaSugerida(s.localId)}
                      className="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium text-violet-700 hover:bg-violet-50"><IconoMas /> Agregar fila</button>
                  </div>
                )}
              </section>
            )
          })()}

          {pasoActual !== 'word' && pasoActual !== 'anexo' && pasoActual !== 'declaraciones' && pasoActual !== 'archivados' && !seccionActual && (
            <div className="bg-white border border-dashed border-gray-300 rounded-2xl text-center py-12 text-sm text-gray-400">Elige una sección en el índice.</div>
          )}

          {/* Barra inferior (como en el formulario) */}
          <div className="flex items-center justify-between gap-3 sticky bottom-0 bg-gradient-to-t from-gray-50 via-gray-50/95 to-transparent pt-3 pb-3 -mx-1 px-1">
            <button type="button" onClick={() => irA(ordenPasos[Math.max(0, iPaso - 1)])} disabled={iPaso <= 0}
              className="inline-flex items-center gap-1.5 border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
              ← Anterior
            </button>
            <div className="flex items-center gap-2">
              <button type="button" onClick={intentarGuardar} disabled={guardando}
                className="inline-flex items-center gap-1.5 border border-primary-200 bg-white text-primary-700 hover:bg-primary-50 disabled:opacity-50 text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
                <IconoGuardar /> {guardando ? 'Guardando…' : 'Guardar'}
              </button>
              {iPaso < ordenPasos.length - 1 && (
                <button type="button" onClick={() => irA(ordenPasos[iPaso + 1])}
                  className="inline-flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors shadow-sm">
                  Siguiente →
                </button>
              )}
            </div>
          </div>
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

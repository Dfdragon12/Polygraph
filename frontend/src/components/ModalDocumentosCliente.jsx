// Documentos legales de un cliente (vista del administrador). Pensada para REVISAR rápido:
//  · cada documento es una tarjeta con su estado, quién lo revisó y la decisión; «Ver» lo abre en otra pestaña;
//  · Validar de un clic; Rechazar pide el motivo, con motivos rápidos para tocar;
//  · al resolver uno, salta solo al siguiente que espera revisión;
//  · atajos de teclado (clic en una tarjeta y luego V validar, R rechazar);
//  · en vivo: si el cliente sube o cambia un documento mientras la ventana está abierta, se actualiza sola.
import { useId, useState, useEffect, useCallback, useMemo, useRef } from 'react'
import soporteService from '../services/soporteService'
import { useCambios } from '../hooks/useCambios'
import { useConexionVivo } from '../hooks/useConexionVivo'
import Toast from './Toast'
import { Modal } from './ui/Modal'

// Clave 'SIN_CARGAR' = el cliente todavía no sube nada (el backend manda estado null).
const ESTADOS = [
  { clave: 'PENDIENTE', label: 'En revisión', desc: 'Esperan tu revisión', badge: 'bg-amber-100 text-amber-700', border: 'border-amber-200', dot: 'bg-amber-400', texto: 'text-amber-600', bgActivo: 'bg-amber-50/70', ring: 'ring-amber-300' },
  { clave: 'RECHAZADO', label: 'Rechazados', desc: 'El cliente debe corregirlos', badge: 'bg-red-100 text-red-600', border: 'border-red-200', dot: 'bg-red-400', texto: 'text-red-600', bgActivo: 'bg-red-50/70', ring: 'ring-red-300' },
  { clave: 'VENCIDO', label: 'Vencidos', desc: 'Pasó su vigencia', badge: 'bg-orange-100 text-orange-700', border: 'border-orange-200', dot: 'bg-orange-400', texto: 'text-orange-600', bgActivo: 'bg-orange-50/70', ring: 'ring-orange-300' },
  { clave: 'VALIDADO', label: 'Vigentes', desc: 'Validados y al día', badge: 'bg-green-100 text-green-700', border: 'border-green-200', dot: 'bg-green-400', texto: 'text-green-600', bgActivo: 'bg-green-50/70', ring: 'ring-green-300' },
  { clave: 'SIN_CARGAR', label: 'Sin cargar', desc: 'Aún no los sube', badge: 'bg-gray-100 text-gray-500', border: 'border-gray-200', dot: 'bg-gray-400', texto: 'text-gray-500', bgActivo: 'bg-gray-50', ring: 'ring-gray-300' },
]
const POR_CLAVE = Object.fromEntries(ESTADOS.map((e, i) => [e.clave, { ...e, orden: i }]))
const claveDe = (s) => (s.estado && POR_CLAVE[s.estado] ? s.estado : 'SIN_CARGAR')

// Motivos que más se repiten al rechazar: un toque los agrega a la observación.
const MOTIVOS_RAPIDOS = ['Ilegible', 'Incompleto', 'Vencido', 'No corresponde a la empresa', 'Documento equivocado', 'Falta firma']
// Un documento vigente que vence pronto merece un aviso.
const DIAS_AVISO_VENCIMIENTO = 30

const EXT_IMAGEN = ['jpg', 'jpeg', 'png', 'gif', 'webp']
const extension = (nombre) => (nombre?.split('.').pop() ?? '').toLowerCase()

function fecha(iso) {
  if (!iso) return '—'
  return new Date(`${iso.slice(0, 10)}T00:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function diasParaVencer(iso) {
  if (!iso) return null
  const hoy = new Date()
  hoy.setHours(0, 0, 0, 0)
  return Math.round((new Date(`${iso.slice(0, 10)}T00:00:00`) - hoy) / 86400000)
}

function chipVencimiento(s, clave) {
  const dias = clave === 'VALIDADO' ? diasParaVencer(s.fechaVencimiento) : null
  if (dias === null || dias > DIAS_AVISO_VENCIMIENTO) return null
  if (dias < 0) return { texto: `Venció hace ${-dias} ${-dias === 1 ? 'día' : 'días'}`, cls: 'bg-red-50 text-red-600' }
  if (dias === 0) return { texto: 'Vence hoy', cls: 'bg-red-50 text-red-600' }
  return { texto: `Vence en ${dias} ${dias === 1 ? 'día' : 'días'}`, cls: dias <= 7 ? 'bg-red-50 text-red-600' : 'bg-amber-50 text-amber-700' }
}

/* ─── Tarjeta de un documento: estado, archivo, quién lo revisó y la decisión ─── */
const ICONO = { PENDIENTE: '⏳', RECHAZADO: '↩', VENCIDO: '⚠', VALIDADO: '✓', SIN_CARGAR: '○' }
const ESTILO_TARJETA = {
  PENDIENTE: { caja: 'bg-amber-50/40 border-amber-200', icono: 'bg-amber-50 text-amber-600', chip: 'bg-amber-100 text-amber-800' },
  RECHAZADO: { caja: 'bg-red-50/40 border-red-200', icono: 'bg-red-50 text-red-600', chip: 'bg-red-100 text-red-800' },
  VENCIDO:   { caja: 'bg-orange-50/40 border-orange-200', icono: 'bg-orange-50 text-orange-600', chip: 'bg-orange-100 text-orange-800' },
  VALIDADO:  { caja: 'bg-white border-gray-200', icono: 'bg-green-50 text-green-600', chip: 'bg-green-100 text-green-800' },
  SIN_CARGAR: { caja: 'bg-white border-gray-200', icono: 'bg-gray-100 text-gray-500', chip: 'bg-gray-100 text-gray-600' },
}
const ETIQUETA_ESTADO = { PENDIENTE: 'En revisión', RECHAZADO: 'Rechazado', VENCIDO: 'Vencido', VALIDADO: 'Vigente', SIN_CARGAR: 'Sin cargar' }

function TarjetaDocumento({ soporte, clave, seleccionado, rechazando, onSeleccionar, onRechazar, onCancelarRechazo, onValidar, onConfirmarRechazo, onVer, procesando }) {
  const idCampos = useId()
  const [motivo, setMotivo] = useState('')
  const campoMotivo = useRef(null)
  useEffect(() => { if (rechazando) campoMotivo.current?.focus() }, [rechazando])
  useEffect(() => { if (!rechazando) setMotivo('') }, [rechazando])
  const agregarMotivo = (texto) => setMotivo(m => (m.includes(texto) ? m : (m.trim() ? `${m.trim()}. ${texto}` : texto)))

  const estilo = ESTILO_TARJETA[clave]
  const chipVence = chipVencimiento(soporte, clave)
  const detalle = [
    soporte.nombreArchivo ?? 'Sin archivo',
    soporte.nombreArchivo ? `cargado el ${fecha(soporte.fechaEntrega)}` : null,
    soporte.fechaVencimiento && clave !== 'RECHAZADO' ? `vence el ${fecha(soporte.fechaVencimiento)}` : null,
  ].filter(Boolean).join(' · ')

  return (
    <li onClick={onSeleccionar}
      className={`rounded-xl border px-3.5 py-3 ${estilo.caja} ${seleccionado ? 'ring-2 ring-primary-400' : ''}`}>
      <div className="flex items-center gap-3">
        <span className={`flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-sm ${estilo.icono}`} aria-hidden="true">{ICONO[clave]}</span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 min-w-0 flex-wrap">
            <p className="text-sm font-medium text-gray-800">{soporte.nombreTipo}</p>
            <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${estilo.chip}`}>{ETIQUETA_ESTADO[clave]}</span>
            {chipVence && <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${chipVence.cls}`}>{chipVence.texto}</span>}
          </div>
          <p className="text-[11px] text-gray-500 truncate" title={detalle}>{detalle}</p>
        </div>
        <div className="flex flex-shrink-0 items-center gap-1.5">
          {soporte.nombreArchivo && (
            <button type="button" onClick={onVer} className="text-xs font-medium text-sky-700 hover:text-sky-800 border border-sky-200 hover:bg-sky-50 px-3 py-1.5 rounded-lg">Ver</button>
          )}
          {soporte.nombreArchivo && clave !== 'SIN_CARGAR' && !rechazando && (
            <>
              {clave !== 'VALIDADO' && <button type="button" onClick={onValidar} disabled={procesando}
                className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-primary-600 hover:bg-primary-700 text-white disabled:opacity-50 transition-colors">
                {procesando ? 'Validando…' : 'Validar'}
              </button>}
              {clave !== 'RECHAZADO' && <button type="button" onClick={onRechazar} disabled={procesando}
                className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-red-200 bg-white text-red-600 hover:bg-red-50 disabled:opacity-50 transition-colors">
                Rechazar
              </button>}
            </>
          )}
        </div>
      </div>

      {!soporte.nombreArchivo && <p className="mt-2 text-[11px] text-gray-500">El cliente aún no ha cargado este documento.</p>}

      {soporte.validadoPor && (
        <p className="mt-2 text-[11px] text-gray-500">
          {clave === 'RECHAZADO' ? 'Rechazado' : 'Validado'} por {soporte.validadoPor}{soporte.fechaValidacion ? ` · ${fecha(soporte.fechaValidacion)}` : ''}
        </p>
      )}
      {soporte.observaciones && (
        <p className={`mt-1.5 rounded-lg px-2.5 py-1.5 text-[11px] ${clave === 'RECHAZADO' ? 'bg-red-50 text-red-800' : 'bg-gray-50 text-gray-700'}`}>
          <span className="font-semibold">{clave === 'RECHAZADO' ? 'Motivo del rechazo: ' : 'Nota: '}</span>{soporte.observaciones}
        </p>
      )}
      {clave === 'RECHAZADO' && !rechazando && <p className="mt-1.5 text-[11px] italic text-gray-500">El cliente debe cargar el documento corregido; entonces vuelve a «En revisión».</p>}

      {rechazando ? (
        <div className="mt-3 space-y-2">
          <label htmlFor={`${idCampos}-motivo`} className="block text-xs font-medium text-gray-600">Motivo del rechazo (obligatorio)</label>
          <div className="flex flex-wrap gap-1.5">
            {MOTIVOS_RAPIDOS.map(m => (
              <button key={m} type="button" onClick={() => agregarMotivo(m)}
                className="text-[11px] px-2 py-1 rounded-full border border-gray-200 bg-white text-gray-600 hover:bg-red-50 hover:border-red-200 hover:text-red-700 transition-colors">+ {m}</button>
            ))}
          </div>
          <textarea id={`${idCampos}-motivo`} ref={campoMotivo} value={motivo} onChange={e => setMotivo(e.target.value)} rows={3}
            placeholder="Explícale al cliente qué debe corregir…"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
          <div className="flex items-center gap-2">
            <button type="button" onClick={() => onConfirmarRechazo(motivo.trim())} disabled={!motivo.trim() || procesando}
              className="text-xs font-semibold px-3.5 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-white disabled:opacity-50 transition-colors">{procesando ? 'Rechazando…' : 'Confirmar rechazo'}</button>
            <button type="button" onClick={onCancelarRechazo} className="text-xs text-gray-500 hover:text-gray-700">Cancelar</button>
          </div>
        </div>
      ) : null}
    </li>
  )
}

/* ─── Modal principal: documentos del cliente ─── */
export default function ModalDocumentosCliente({ cliente, onClose }) {
  const [soportes, setSoportes] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [abierto, setAbierto] = useState(null)       // idSoporte expandido
  const [rechazando, setRechazando] = useState(null) // idSoporte con el formulario de rechazo
  const [procesando, setProcesando] = useState(null)
  const vivo = useConexionVivo()
  const refsFilas = useRef({})

  const cargar = useCallback(() => {
    return soporteService.listarAdmin(cliente.idCliente)
      .then(r => { setSoportes(r); setError(null) })
      .catch(() => setError('No se pudieron cargar los documentos de este cliente.'))
  }, [cliente.idCliente])

  useEffect(() => { cargar() }, [cargar])
  // En vivo: si el cliente (o quien sea) cambia un documento con esta ventana abierta, se actualiza sola.
  useCambios(cargar, { temas: true, esperaMs: 800 })

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  const conteos = useMemo(() => {
    const c = Object.fromEntries(ESTADOS.map(e => [e.clave, 0]))
    ;(soportes ?? []).forEach(s => { c[claveDe(s)] += 1 })
    return c
  }, [soportes])

  // Lo que espera revisión primero; dentro de cada estado, el orden del catálogo.
  const filas = useMemo(() => [...(soportes ?? [])]
    .sort((a, b) => POR_CLAVE[claveDe(a)].orden - POR_CLAVE[claveDe(b)].orden), [soportes])

  const total = soportes?.length ?? 0
  const pendientes = conteos.PENDIENTE
  const revisados = (soportes ?? []).filter(s => ['VALIDADO', 'RECHAZADO', 'VENCIDO'].includes(claveDe(s))).length
  const conArchivo = (soportes ?? []).filter(s => s.nombreArchivo).length

  const irAlSiguientePendiente = (despuesDe = null) => {
    const lista = (soportes ?? []).filter(s => claveDe(s) === 'PENDIENTE' && s.idSoporte !== despuesDe)
      .sort((a, b) => POR_CLAVE[claveDe(a)].orden - POR_CLAVE[claveDe(b)].orden)
    if (lista.length === 0) { setAbierto(null); return }
    setRechazando(null)
    setAbierto(lista[0].idSoporte)
    requestAnimationFrame(() => refsFilas.current[lista[0].idSoporte]?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }))
  }

  // Abre el documento en otra pestaña con el visor del navegador (la pestaña se abre ya, por los bloqueadores).
  const ver = async (s) => {
    const ventana = window.open('', '_blank')
    try {
      const blob = await soporteService.descargarAdmin(s.idSoporte)
      const ext = extension(s.nombreArchivo)
      const tipo = ext === 'pdf' ? 'application/pdf' : ext === 'jpg' ? 'image/jpeg' : EXT_IMAGEN.includes(ext) ? `image/${ext}` : blob.type
      const url = window.URL.createObjectURL(new Blob([blob], { type: tipo }))
      if (ventana) ventana.location.href = url
      else window.open(url, '_blank')
      setTimeout(() => window.URL.revokeObjectURL(url), 60000)
    } catch {
      ventana?.close()
      mostrarToast('No se pudo abrir el documento', 'error')
    }
  }

  const resolver = async (s, aprobado, observaciones) => {
    setProcesando(s.idSoporte)
    try {
      await soporteService.validarAdmin(s.idSoporte, aprobado, observaciones || null)
      // Se refleja de una vez, sin esperar la recarga.
      setSoportes(prev => prev.map(x => x.idSoporte === s.idSoporte
        ? { ...x, estado: aprobado ? 'VALIDADO' : 'RECHAZADO', observaciones: observaciones || (aprobado ? null : x.observaciones) } : x))
      mostrarToast(aprobado ? `«${s.nombreTipo}» validado.` : `«${s.nombreTipo}» rechazado: se le avisó al cliente.`)
      setRechazando(null)
      irAlSiguientePendiente(s.idSoporte)
      cargar()
    } catch (e) {
      mostrarToast(e.response?.data?.mensaje ?? 'Error al procesar el documento.', 'error')
    } finally {
      setProcesando(null)
    }
  }

  // Teclado: ↑ ↓ entre documentos; V valida y R rechaza el que está abierto (si espera revisión).
  const alTeclear = (e) => {
    const destino = e.target
    if (destino.tagName === 'TEXTAREA' || destino.tagName === 'INPUT') return
    const s = (soportes ?? []).find(x => x.idSoporte === abierto)
    if (!s || claveDe(s) !== 'PENDIENTE' || procesando) return
    if (e.key.toLowerCase() === 'v') { e.preventDefault(); resolver(s, true, null) }
    if (e.key.toLowerCase() === 'r') { e.preventDefault(); setRechazando(s.idSoporte) }
  }

  return (
    <Modal titulo="Documentos legales" subtitulo={cliente.nombreDisplay} onClose={onClose} ancho="max-w-2xl">
      <div className="px-6 py-5 overflow-y-auto" onKeyDown={alTeclear}>
        {error && soportes === null ? (
          <p className="text-sm text-red-500 text-center py-8" role="alert">{error}</p>
        ) : soportes === null ? (
          <div className="flex justify-center py-10">
            <div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" />
          </div>
        ) : (
          <>
            {/* Avance de la revisión */}
            <div className="flex flex-wrap items-center gap-4 mb-4">
              <div className="flex-1 min-w-[14rem]">
                <div className="flex items-center justify-between text-xs mb-1.5">
                  <span className="text-gray-600">
                    <strong className="text-gray-900">{revisados}</strong> de {total} documentos resueltos
                    {pendientes > 0 && <> · <strong className="text-amber-600">{pendientes}</strong> esperan tu revisión</>}
                  </span>
                  <span className={`inline-flex items-center gap-1.5 font-medium ${vivo ? 'text-emerald-600' : 'text-amber-600'}`} role="status">
                    <span className={`w-1.5 h-1.5 rounded-full ${vivo ? 'bg-emerald-500' : 'bg-amber-500'}`} aria-hidden="true" />
                    {vivo ? 'En vivo' : 'Sin conexión en vivo'}
                  </span>
                </div>
                <div className="h-2 rounded-full bg-gray-100 overflow-hidden" role="progressbar" aria-valuemin={0} aria-valuemax={total}
                  aria-valuenow={revisados} aria-label="Documentos resueltos">
                  <div className="h-full rounded-full bg-emerald-500 transition-all duration-500" style={{ width: `${total ? (revisados / total) * 100 : 0}%` }} />
                </div>
                {conArchivo < total && <p className="text-[11px] text-gray-400 mt-1">{total - conArchivo} sin cargar todavía.</p>}
              </div>
              {pendientes > 0 && (
                <button type="button" onClick={() => irAlSiguientePendiente()}
                  className="text-sm font-semibold px-4 py-2 rounded-lg bg-primary-600 hover:bg-primary-700 text-white transition-colors">
                  Revisar el siguiente ({pendientes})
                </button>
              )}
            </div>

            {/* Documentos, una tarjeta cada uno */}
            <ul className="space-y-2">
              {filas.length === 0 && (
                <li className="text-center py-12 text-sm text-gray-400">No hay documentos que coincidan con la búsqueda.</li>
              )}
              {filas.map(s => (
                <TarjetaDocumento key={s.tipoSoporte} soporte={s} clave={claveDe(s)} seleccionado={abierto === s.idSoporte}
                  rechazando={rechazando === s.idSoporte} procesando={procesando === s.idSoporte}
                  onSeleccionar={() => setAbierto(s.idSoporte)}
                  onRechazar={() => { setAbierto(s.idSoporte); setRechazando(s.idSoporte) }} onCancelarRechazo={() => setRechazando(null)}
                  onValidar={() => resolver(s, true, null)}
                  onConfirmarRechazo={(motivo) => resolver(s, false, motivo)}
                  onVer={() => ver(s)} />
              ))}
            </ul>
          </>
        )}
      </div>
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </Modal>
  )
}

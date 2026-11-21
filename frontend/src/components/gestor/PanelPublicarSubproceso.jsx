import { useEffect, useId, useRef, useState } from 'react'
import asignacionService from '../../services/asignacionService'
import formulariosService from '../../services/formulariosService'
import trabajoService, { esperarTrabajo, guardarBlob, mensajeDeError } from '../../services/trabajoService'
import SelectorMotivo, { motivoIncompleto, MOTIVO_VACIO } from '../SelectorMotivo'

// PB-02 · «Publicar · <subproceso>»: el gestor revisa el informe, puede ajustar la conclusión que verá el cliente y la calificación del subproceso,
// confirma que lo revisó y publica; o lo devuelve al responsable con motivo. Es el panel lateral del diseño aprobado.
const NIVELES = [
  { valor: 'BAJO', texto: 'BAJO', clase: 'border-[#2E8A5B] bg-[#E6F3EC] text-[#1f6b44]' },
  { valor: 'MEDIO', texto: 'MEDIO', clase: 'border-[#D9952B] bg-[#FBF1DE] text-[#8a5a0c]' },
  { valor: 'ALTO', texto: 'ALTO', clase: 'border-[#B23A48] bg-[#F9E8EA] text-[#8c2733]' },
]
const campoCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'
const botonBase = 'min-h-[44px] px-4 rounded-lg text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#A8323F]'

export default function PanelPublicarSubproceso({ asignacion, publicados, total, onClose, onHecho, onToast }) {
  const idTitulo = useId()
  const sugerido = asignacion.riesgoNivel ?? null
  const [conclusion, setConclusion] = useState(asignacion.conclusion ?? '')
  const [nivel, setNivel] = useState(sugerido)
  const [motivoNivel, setMotivoNivel] = useState('')
  const [revisado, setRevisado] = useState(false)        // desmarcada al abrir, a propósito
  const [devolviendo, setDevolviendo] = useState(false)
  const [motivo, setMotivo] = useState(MOTIVO_VACIO)
  const [enviando, setEnviando] = useState(false)
  const [generando, setGenerando] = useState(false)
  const [error, setError] = useState(null)
  const cierre = useRef(null)

  useEffect(() => {
    cierre.current?.focus()
    const salir = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', salir)
    return () => window.removeEventListener('keydown', salir)
  }, [onClose])

  const cambioNivel = nivel != null && nivel !== sugerido
  const nombreResponsable = asignacion.nombreAsignado ?? 'el responsable'
  const falta = !revisado ? 'Se activa al marcar la casilla'
    : !conclusion.trim() ? 'Escribe la conclusión que verá el cliente'
    : cambioNivel && !motivoNivel.trim() ? 'Escribe el motivo del cambio de calificación'
    : null

  const verPdf = async () => {
    setGenerando(true); setError(null)
    try {
      const inicial = await formulariosService.solicitarDocumentoPdf(asignacion.id)
      const listo = await esperarTrabajo(inicial)
      const r = await trabajoService.descargarResultado(listo.id)
      guardarBlob(r, listo.nombreArchivo ?? `informe-${asignacion.id}.pdf`)
    } catch (e) {
      setError(mensajeDeError(e, 'No se pudo generar el PDF. Reintentar'))
    } finally { setGenerando(false) }
  }

  const publicar = async () => {
    if (falta) return
    setEnviando(true); setError(null)
    try {
      await asignacionService.publicar(asignacion.id, { conclusion: conclusion.trim(), nivel, motivoNivel: motivoNivel.trim() || null, revisado })
      onToast?.('Publicado · el cliente fue notificado')
      onHecho()
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo publicar. Reintentar')
    } finally { setEnviando(false) }
  }

  const devolver = async () => {
    const f = motivoIncompleto(motivo)
    if (f) { setError(f); return }
    setEnviando(true); setError(null)
    try {
      await asignacionService.devolver(asignacion.id, { idMotivo: motivo.idMotivo, observacion: motivo.texto.trim() || null })
      onToast?.(`Devuelto a ${nombreResponsable} · le llegó la notificación`)
      onHecho()
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo devolver. Reintentar')
    } finally { setEnviando(false) }
  }

  const nombreEvaluado = `${asignacion.nombresEvaluado ?? ''} ${asignacion.apellidosEvaluado ?? ''}`.trim()

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={onClose}>
      <aside role="dialog" aria-modal="true" aria-labelledby={idTitulo} onClick={e => e.stopPropagation()}
        className="w-full max-w-[430px] h-full bg-white shadow-2xl flex flex-col rounded-l-2xl">
        <header className="px-6 pt-5 pb-4 border-b border-gray-200">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-[11px] font-bold tracking-wider uppercase text-[#A8323F]">Paso 4 revisar → paso 5 publicar</p>
              <h2 id={idTitulo} className="text-lg font-bold text-gray-900">Publicar · {asignacion.subproceso}</h2>
              <p className="text-xs text-gray-600 mt-0.5">#{asignacion.idServicio} {nombreEvaluado} · lo hizo {nombreResponsable}
                {asignacion.fechaCompletado ? ` · Finalizado ${new Date(asignacion.fechaCompletado).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' })}` : ''}</p>
            </div>
            <button ref={cierre} type="button" onClick={onClose} aria-label="Cerrar"
              className="min-h-[44px] min-w-[44px] rounded-lg text-gray-600 hover:bg-gray-100 focus-visible:outline focus-visible:outline-2">✕</button>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          <button type="button" onClick={verPdf} disabled={generando}
            className={`${botonBase} w-full border border-gray-300 text-gray-900 hover:bg-gray-50 disabled:opacity-60`}>
            {generando ? 'Generando el PDF…' : 'Ver informe en PDF (como lo verá el cliente)'}
          </button>

          <div>
            <label htmlFor={`${idTitulo}-conclusion`} className="block text-sm font-semibold text-gray-900 mb-1">Conclusión que verá el cliente</label>
            <textarea id={`${idTitulo}-conclusion`} rows={4} maxLength={4000} value={conclusion} onChange={e => setConclusion(e.target.value)} className={campoCls} />
            <p className="text-xs text-gray-600 mt-1">Viene del informe de {nombreResponsable}. Si la cambias, queda la versión original en el historial.</p>
          </div>

          <fieldset>
            <legend className="text-sm font-semibold text-gray-900 mb-2">Calificación del subproceso</legend>
            {sugerido ? (
              <div className="flex flex-wrap gap-2" role="radiogroup">
                {NIVELES.map(n => {
                  const activo = nivel === n.valor
                  return (
                    <label key={n.valor} className={`cursor-pointer inline-flex items-center gap-2 px-3 min-h-[44px] rounded-lg border-2 text-sm font-semibold ${activo ? n.clase : 'border-gray-200 text-gray-700'}`}>
                      <input type="radio" name={`${idTitulo}-nivel`} checked={activo} onChange={() => setNivel(n.valor)} className="accent-[#A8323F]" />
                      {n.texto}{n.valor === sugerido && asignacion.riesgoPuntaje != null ? ` · ${Number(asignacion.riesgoPuntaje).toLocaleString('es-CO', { maximumFractionDigits: 1 })}` : ''}
                    </label>
                  )
                })}
              </div>
            ) : (
              <p className="text-sm text-gray-700">Este subproceso no tiene calificación calculada: se publica sin nivel.</p>
            )}
            {sugerido && <p className="text-xs text-gray-600 mt-1">Sugerida por los códigos que confirmó el analista. Cambiarla exige escribir el motivo.</p>}
            {cambioNivel && (
              <div className="mt-2">
                <label htmlFor={`${idTitulo}-motivo-nivel`} className="block text-xs font-medium text-gray-700 mb-1">Motivo del cambio <span className="text-red-700">*</span></label>
                <textarea id={`${idTitulo}-motivo-nivel`} rows={2} value={motivoNivel} onChange={e => setMotivoNivel(e.target.value)} className={campoCls} />
              </div>
            )}
          </fieldset>

          <div>
            <label className="flex items-start gap-3 text-sm text-gray-900 cursor-pointer">
              <input type="checkbox" checked={revisado} onChange={e => setRevisado(e.target.checked)} className="mt-1 h-4 w-4 accent-[#A8323F]" />
              Revisé el informe y los soportes. Está listo para el cliente.
            </label>
            <p className="text-xs text-gray-600 mt-2">&quot;Publicar al cliente&quot; se activa al marcar la casilla.</p>
          </div>

          <div className="rounded-lg bg-gray-100 px-4 py-3 text-xs text-gray-700 leading-relaxed">
            <strong>Al publicar:</strong> el subproceso pasa de Finalizado a <strong>Publicado</strong> y el cliente puede descargar este informe.
            El estudio sigue en <strong>Publicado parcial ({publicados + 1} de {total})</strong>.
            {publicados + 1 < total && ' El estudio completo se habilita cuando los demás subprocesos también estén publicados.'}
          </div>

          {devolviendo && (
            <div className="rounded-lg border border-gray-200 p-3">
              <SelectorMotivo tipo="DEVOLUCION" valor={motivo} onChange={setMotivo} etiqueta="Motivo de la devolución" detalle detalleEtiqueta="Qué hay que corregir" />
            </div>
          )}
          {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        </div>

        <footer className="px-6 py-4 border-t border-gray-200 flex flex-wrap items-center gap-3">
          {!devolviendo ? (
            <button type="button" onClick={() => setDevolviendo(true)} disabled={enviando}
              className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Devolver a {nombreResponsable}</button>
          ) : (
            <button type="button" onClick={devolver} disabled={enviando}
              className={`${botonBase} border border-[#A8323F] text-[#A8323F] hover:bg-red-50 disabled:opacity-60`}>{enviando ? 'Devolviendo…' : `Confirmar devolución a ${nombreResponsable}`}</button>
          )}
          <button type="button" onClick={publicar} disabled={!!falta || enviando}
            className={`${botonBase} ${falta || enviando ? 'bg-gray-200 text-gray-600 cursor-not-allowed' : 'bg-[#A8323F] text-white hover:bg-[#8f2a35]'}`}>
            {enviando ? 'Publicando…' : 'Publicar al cliente'}
          </button>
          {falta && <span className="basis-full text-xs text-gray-600">{falta}</span>}
        </footer>
      </aside>
    </div>
  )
}

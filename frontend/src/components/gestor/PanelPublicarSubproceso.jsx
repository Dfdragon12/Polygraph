import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useVentana } from '../../hooks/useVentana'
import asignacionService from '../../services/asignacionService'
import formulariosService from '../../services/formulariosService'
import trabajoService, { esperarTrabajo, guardarBlob, mensajeDeError } from '../../services/trabajoService'
import SelectorMotivo, { motivoIncompleto, MOTIVO_VACIO } from '../SelectorMotivo'
import AvisoConclusion from '../AvisoConclusion'
import { contieneFavorable } from '../../utils/conclusion'
import { fechaHora } from '../../utils/fechas'
import { useBorrador } from '../../hooks/useBorrador'

// PB-02 · «Publicar · <subproceso>»: el gestor revisa el informe, puede ajustar la conclusión que verá el cliente y la calificación del subproceso,
// confirma que lo revisó y publica; o lo devuelve al responsable con motivo. Es el panel lateral del diseño aprobado.
// PC-02: al abrirse pide al servidor lo que necesita (la conclusión que el ejecutor escribió en SU formato, la calificación sugerida y si este subproceso la
// necesita para publicarse); no publica sin nivel si el subproceso califica, ni con «favorable/desfavorable»; con 1 de 1 (o N de N) avisa que se abre el cierre
// del estudio; y la devolución es una vista aparte, con su propio «Confirmar devolución» y «Cancelar» (el botón que la abre no cambia de función en su lugar).
const NIVELES = [
  { valor: 'BAJO', texto: 'BAJO', clase: 'border-[#2E8A5B] bg-[#E6F3EC] text-[#1f6b44]' },
  { valor: 'MEDIO', texto: 'MEDIO', clase: 'border-[#D9952B] bg-[#FBF1DE] text-[#8a5a0c]' },
  { valor: 'ALTO', texto: 'ALTO', clase: 'border-[#B23A48] bg-[#F9E8EA] text-[#8c2733]' },
]
const campoCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'
const botonBase = 'min-h-[44px] px-4 rounded-lg text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#A8323F]'

export default function PanelPublicarSubproceso({ asignacion, publicados, total, onClose, onHecho, onToast }) {
  const idTitulo = useId()
  const [datos, setDatos] = useState(null)               // lo que dice el servidor al abrir; null mientras carga
  const [errorCarga, setErrorCarga] = useState(false)
  const [conclusion, setConclusion] = useState('')
  const [nivel, setNivel] = useState(null)
  const [motivoNivel, setMotivoNivel] = useState('')
  const [revisado, setRevisado] = useState(false)        // desmarcada al abrir, a propósito
  const [devolviendo, setDevolviendo] = useState(false)
  const [motivo, setMotivo] = useState(MOTIVO_VACIO)
  const [enviando, setEnviando] = useState(false)
  const [generando, setGenerando] = useState(false)
  const [error, setError] = useState(null)
  const [recuperado, setRecuperado] = useState(null)     // cuándo se escribió el borrador que se recuperó (PC-06); null si se abrió limpio
  const borrador = useBorrador('publicar', asignacion.id)
  const cierre = useRef(null)
  const capa = useRef(null)
  const tituloDevolver = useRef(null)
  const botonDevolver = useRef(null)
  const vuelveDeDevolver = useRef(false)

  // PC-06: foco adentro al abrir, Esc cierra, el foco vuelve a quien la abrió y el fondo queda inerte (hooks/useVentana); la ventana va en un portal.
  useVentana({ capa, alEscape: onClose })

  const cargar = useCallback(() => {
    setErrorCarga(false); setDatos(null)
    asignacionService.datosPublicacion(asignacion.id)
      .then(d => {
        // PC-06: si la sesión se cerró (o se recargó la página) con esta ventana a medias, lo escrito vuelve. La casilla «lo revisé» NO se recupera: se marca cada vez.
        const b = borrador.leer()?.valor
        setDatos(d)
        setConclusion(b ? (b.conclusion ?? '') : (d.conclusion ?? ''))
        setNivel(b && b.nivel !== undefined ? b.nivel : (d.nivel ?? null))
        if (b) {
          setMotivoNivel(b.motivoNivel ?? '')
          if (b.motivo && (b.motivo.idMotivo || b.motivo.texto)) { setMotivo(b.motivo); setDevolviendo(true) }
          setRecuperado(borrador.leer()?.guardadoEn ?? Date.now())
        }
      })
      .catch(() => setErrorCarga(true))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [asignacion.id])
  useEffect(() => { cargar() }, [cargar])

  // PC-06: lo escrito se guarda como borrador mientras se escribe; si todo está igual que lo que dijo el servidor, no hay borrador.
  useEffect(() => {
    if (!datos) return
    const intacto = conclusion === (datos.conclusion ?? '') && (nivel ?? null) === (datos.nivel ?? null) && !motivoNivel && !motivo.idMotivo && !motivo.texto
    if (intacto) borrador.borrar()
    else borrador.guardar({ conclusion, nivel, motivoNivel, motivo })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datos, conclusion, nivel, motivoNivel, motivo])

  const descartarBorrador = () => {
    borrador.borrar()
    setConclusion(datos?.conclusion ?? ''); setNivel(datos?.nivel ?? null); setMotivoNivel(''); setMotivo(MOTIVO_VACIO); setDevolviendo(false); setRecuperado(null)
  }

  // Foco: al abrir la devolución, a su título; al cancelarla, de vuelta al botón que la abrió.
  useEffect(() => {
    if (devolviendo) tituloDevolver.current?.focus()
    else if (vuelveDeDevolver.current) { botonDevolver.current?.focus(); vuelveDeDevolver.current = false }
  }, [devolviendo])

  const sugerido = datos?.nivel ?? null
  const necesitaCalificacion = !!datos?.califica && !datos?.nivel          // el tipo tiene motor y todavía no hay nivel: no se publica
  const cambioNivel = nivel != null && nivel !== sugerido
  const conFavorable = contieneFavorable(conclusion)
  const cierraEstudio = total > 0 && publicados + 1 >= total               // este es el último informe: al publicarlo se abre el cierre del estudio
  const nombreResponsable = asignacion.nombreAsignado ?? 'el responsable'
  const idFalta = `${idTitulo}-falta`
  const falta = errorCarga ? 'No se pudieron cargar los datos del informe'
    : !datos ? 'Cargando los datos del informe…'
    : necesitaCalificacion ? 'Este subproceso necesita su calificación para publicarse'
    : !revisado ? 'Se activa al marcar la casilla'
    : !conclusion.trim() ? 'Escribe la conclusión que verá el cliente'
    : conFavorable ? 'Quita «favorable» o «desfavorable» de la conclusión'
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
      borrador.borrar()
      onToast?.(cierraEstudio ? 'Publicado · ahora emite el estudio completo para el cliente' : 'Publicado · el cliente fue notificado')
      onHecho({ cierraEstudio })
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo publicar. Reintentar')
    } finally { setEnviando(false) }
  }

  const abrirDevolucion = () => { setError(null); setDevolviendo(true) }
  const cancelarDevolucion = () => { setError(null); setMotivo(MOTIVO_VACIO); vuelveDeDevolver.current = true; setDevolviendo(false) }

  const devolver = async () => {
    const f = motivoIncompleto(motivo)
    if (f) { setError(f); return }
    setEnviando(true); setError(null)
    try {
      await asignacionService.devolver(asignacion.id, { idMotivo: motivo.idMotivo, observacion: motivo.texto.trim() || null })
      borrador.borrar()
      onToast?.(`Devuelto a ${nombreResponsable} · le llegó la notificación`)
      onHecho({ cierraEstudio: false })
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo devolver. Reintentar')
    } finally { setEnviando(false) }
  }

  const nombreEvaluado = `${asignacion.nombresEvaluado ?? ''} ${asignacion.apellidosEvaluado ?? ''}`.trim()
  const origenTexto = datos?.origenConclusion === 'FORMATO' ? `Viene de la conclusión que escribió ${nombreResponsable} en su informe.`
    : datos?.origenConclusion === 'SUBPROCESO' ? `Viene de la conclusión que escribió ${nombreResponsable} al enviar el informe.`
    : `${nombreResponsable} no dejó conclusión: escríbela tú.`

  return createPortal(
    <div ref={capa} className="fixed inset-0 z-50 flex justify-end bg-black/30 overscroll-contain" onClick={onClose}>
      <aside role="dialog" aria-modal="true" aria-labelledby={idTitulo} onClick={e => e.stopPropagation()}
        className="w-full max-w-[430px] h-full bg-white shadow-2xl flex flex-col rounded-l-2xl">
        <header className="px-6 pt-5 pb-4 border-b border-gray-200">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-[11px] font-bold tracking-wider uppercase text-[#A8323F]">Paso 4 revisar → paso 5 publicar</p>
              <h2 id={idTitulo} className="text-lg font-bold text-gray-900">Publicar · {asignacion.subproceso}</h2>
              <p className="text-xs text-gray-600 mt-0.5">#{asignacion.idServicio} {nombreEvaluado} · lo hizo {nombreResponsable}
                {asignacion.fechaCompletado ? ` · Finalizado ${fechaHora(asignacion.fechaCompletado)}` : ''}</p>
            </div>
            <button ref={cierre} type="button" onClick={onClose} aria-label="Cerrar"
              className="min-h-[44px] min-w-[44px] rounded-lg text-gray-600 hover:bg-gray-100 focus-visible:outline focus-visible:outline-2">✕</button>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto overscroll-contain px-6 py-5 space-y-5">
          {recuperado && (
            <p role="status" className="flex flex-wrap items-center gap-x-3 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
              <span>Recuperamos lo que tenías escrito ({fechaHora(recuperado)}).</span>
              <button type="button" onClick={descartarBorrador} className="min-h-[44px] font-semibold underline focus-visible:outline focus-visible:outline-2">Descartar borrador</button>
            </p>
          )}
          {devolviendo ? (
            <section aria-labelledby={`${idTitulo}-devolver`} className="space-y-4">
              <div>
                <h3 id={`${idTitulo}-devolver`} ref={tituloDevolver} tabIndex={-1} className="text-base font-bold text-gray-900 focus:outline-none">Devolver a {nombreResponsable}</h3>
                <p className="text-xs text-gray-700 mt-1">El informe vuelve a {nombreResponsable} para que lo corrija y le llega una notificación con el motivo. No se publica nada.</p>
              </div>
              <div className="rounded-lg border border-gray-200 p-3">
                <SelectorMotivo tipo="DEVOLUCION" valor={motivo} onChange={setMotivo} etiqueta="Motivo de la devolución" detalle detalleEtiqueta="Qué hay que corregir" />
              </div>
              {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
              <div className="flex flex-wrap items-center gap-3">
                <button type="button" onClick={cancelarDevolucion} disabled={enviando}
                  className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50 disabled:opacity-60`}>Cancelar</button>
                <button type="button" onClick={devolver} disabled={enviando}
                  className={`${botonBase} border border-[#A8323F] text-[#A8323F] hover:bg-red-50 disabled:opacity-60`}>{enviando ? 'Devolviendo…' : 'Confirmar devolución'}</button>
              </div>
            </section>
          ) : (
            <>
              <button type="button" onClick={verPdf} disabled={generando}
                className={`${botonBase} w-full border border-gray-300 text-gray-900 hover:bg-gray-50 disabled:opacity-60`}>
                {generando ? 'Generando el PDF…' : 'Ver informe en PDF (como lo verá el cliente)'}
              </button>

              {errorCarga && (
                <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
                  <p>No se pudieron cargar la conclusión y la calificación de este informe.</p>
                  <button type="button" onClick={cargar} className={`${botonBase} mt-2 border border-red-300 bg-white text-red-800 hover:bg-red-100`}>Reintentar</button>
                </div>
              )}
              {!errorCarga && !datos && <p role="status" className="text-sm text-gray-700">Cargando la conclusión y la calificación del informe…</p>}

              {datos && (
                <>
                  <div>
                    <label htmlFor={`${idTitulo}-conclusion`} className="block text-sm font-semibold text-gray-900 mb-1">Conclusión que verá el cliente</label>
                    <textarea id={`${idTitulo}-conclusion`} rows={5} maxLength={4000} value={conclusion} onChange={e => setConclusion(e.target.value)}
                      aria-describedby={`${idTitulo}-ayuda-conclusion`} className={campoCls} />
                    <p id={`${idTitulo}-ayuda-conclusion`} className="text-xs text-gray-600 mt-1">{origenTexto} Si la cambias, queda la versión original en el historial.</p>
                    <AvisoConclusion texto={conclusion} />
                  </div>

                  <fieldset>
                    <legend className="text-sm font-semibold text-gray-900 mb-2">Calificación del subproceso</legend>
                    {necesitaCalificacion ? (
                      <div className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
                        <p className="font-semibold">Este subproceso necesita su calificación para publicarse.</p>
                        {datos.motivoSinCalcular && <p className="mt-1">{datos.motivoSinCalcular}</p>}
                        <p className="mt-1 text-xs">Hasta que el sistema pueda calcularla no se publica. Si algo falta en el informe, devuélvelo a {nombreResponsable}.</p>
                      </div>
                    ) : sugerido ? (
                      <div className="flex flex-wrap gap-2" role="radiogroup">
                        {NIVELES.map(n => {
                          const activo = nivel === n.valor
                          return (
                            <label key={n.valor} className={`cursor-pointer inline-flex items-center gap-2 px-3 min-h-[44px] rounded-lg border-2 text-sm font-semibold ${activo ? n.clase : 'border-gray-200 text-gray-700'}`}>
                              <input type="radio" name={`${idTitulo}-nivel`} checked={activo} onChange={() => setNivel(n.valor)} className="accent-[#A8323F]" />
                              {n.texto}{n.valor === sugerido && datos.puntaje != null ? ` · ${Number(datos.puntaje).toLocaleString('es-CO', { maximumFractionDigits: 1 })}` : ''}
                            </label>
                          )
                        })}
                      </div>
                    ) : (
                      <p className="text-sm text-gray-700">Este subproceso no tiene calificación calculada: se publica sin nivel.</p>
                    )}
                    {sugerido && !necesitaCalificacion && <p className="text-xs text-gray-600 mt-1">Sugerida por los códigos que confirmó el analista. Cambiarla exige escribir el motivo.</p>}
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
                </>
              )}

              <div className="rounded-lg bg-gray-100 px-4 py-3 text-xs text-gray-700 leading-relaxed">
                <strong>Al publicar:</strong> el subproceso pasa de Finalizado a <strong>Publicado</strong> y el cliente puede descargar este informe.{' '}
                {cierraEstudio
                  ? 'Al publicar se abre el cierre del estudio para emitir el resultado al cliente.'
                  : <>El estudio sigue en <strong>Publicado parcial ({publicados + 1} de {total})</strong>. El estudio completo se habilita cuando los demás subprocesos también estén publicados.</>}
              </div>

              {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
            </>
          )}
        </div>

        {!devolviendo && (
          <footer className="px-6 py-4 border-t border-gray-200 flex flex-wrap items-center gap-3">
            <button ref={botonDevolver} type="button" onClick={abrirDevolucion} disabled={enviando}
              className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Devolver a {nombreResponsable}</button>
            <button type="button" onClick={publicar} disabled={!!falta || enviando} aria-describedby={falta ? idFalta : undefined}
              className={`${botonBase} ${falta || enviando ? 'bg-gray-200 text-gray-600 cursor-not-allowed' : 'bg-[#A8323F] text-white hover:bg-[#8f2a35]'}`}>
              {enviando ? 'Publicando…' : 'Publicar al cliente'}
            </button>
            {falta && <span id={idFalta} className="basis-full text-xs text-gray-600">{falta}</span>}
          </footer>
        )}
      </aside>
    </div>,
    document.body
  )
}

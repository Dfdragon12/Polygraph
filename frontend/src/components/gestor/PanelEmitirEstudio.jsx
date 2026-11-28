import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useVentana } from '../../hooks/useVentana'
import asignacionService from '../../services/asignacionService'
import riesgoService from '../../services/riesgoService'
import solicitudService from '../../services/solicitudService'
import trabajoService, { esperarTrabajo, guardarBlob, mensajeDeError } from '../../services/trabajoService'
import { useBorrador } from '../../hooks/useBorrador'
import { fechaHora } from '../../utils/fechas'

// PB-03 · «Emitir estudio completo»: nivel del estudio (calculado por el modelo), nota del gestor, alertas para el cliente, confirmación de revisión
// manual si el motor la pide, orden del PDF y los dos botones de emisión. El registro interno (pesos, promedio, ajustes) es SOLO de esta pantalla.
const CHIP = {
  BAJO: 'bg-[#E6F3EC] text-[#1f6b44]',
  MEDIO: 'bg-[#FBF1DE] text-[#8a5a0c]',
  ALTO: 'bg-[#F9E8EA] text-[#8c2733]',
}
const campoCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'
const botonBase = 'min-h-[44px] px-4 rounded-lg text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#A8323F]'
const num = (v) => v == null ? '—' : Number(v).toLocaleString('es-CO', { maximumFractionDigits: 1 })

export default function PanelEmitirEstudio({ solicitud, onClose, onHecho, onToast }) {
  const idTitulo = useId()
  const cierre = useRef(null)
  const capa = useRef(null)
  const [subprocesos, setSubprocesos] = useState(null)
  const [riesgo, setRiesgo] = useState(null)
  const [errorRiesgo, setErrorRiesgo] = useState(false)
  // PC-06: la nota y las alertas que se escriben aquí se guardan como borrador; si la sesión se cierra o se recarga la página, al volver a abrir la ventana siguen ahí.
  // La casilla de revisión manual NO se recupera: se confirma cada vez.
  const borrador = useBorrador('emitir', solicitud.idServicio)
  const [recuperado] = useState(() => borrador.leer())
  const [nota, setNota] = useState(recuperado?.valor?.nota ?? solicitud.conceptoGestor ?? '')
  const [alertas, setAlertas] = useState(recuperado?.valor?.alertas ?? [])
  const [verRecuperado, setVerRecuperado] = useState(!!recuperado)
  const [confirmaRevision, setConfirmaRevision] = useState(false)
  const [verRegistro, setVerRegistro] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [generandoVista, setGenerandoVista] = useState(false)
  const [errorVista, setErrorVista] = useState(null)
  const abortoVista = useRef(null)
  useEffect(() => () => abortoVista.current?.abort(), [])

  // PC-06: foco adentro al abrir, Esc cierra, el foco vuelve a quien la abrió y el fondo queda inerte (hooks/useVentana); la ventana va en un portal.
  useVentana({ capa, alEscape: onClose })

  useEffect(() => {
    asignacionService.listar({ idServicio: solicitud.idServicio, size: 50 }).then(r => setSubprocesos(r.content ?? [])).catch(() => setSubprocesos([]))
    riesgoService.obtener(solicitud.idServicio).then(setRiesgo).catch(() => setErrorRiesgo(true))
  }, [solicitud.idServicio])

  useEffect(() => {
    const intacta = nota === (solicitud.conceptoGestor ?? '') && alertas.length === 0
    if (intacta) borrador.borrar(); else borrador.guardar({ nota, alertas })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nota, alertas])
  const descartarBorrador = () => { borrador.borrar(); setNota(solicitud.conceptoGestor ?? ''); setAlertas([]); setVerRecuperado(false) }

  const vigentes = (subprocesos ?? []).filter(sp => sp.estado !== 'CANCELADO')
  const publicados = vigentes.filter(sp => sp.estado === 'PUBLICADO').length
  const completo = vigentes.length > 0 && publicados === vigentes.length
  const nombreEvaluado = `${solicitud.nombres ?? ''} ${solicitud.apellidos ?? ''}`.trim()
  const requiereRevision = !!riesgo?.requiereRevision
  const alertasIncompletas = alertas.some(a => !a.texto.trim() || !a.justificacion.trim())

  const faltaFinal = !completo ? `Se habilita con ${vigentes.length || 'todos'} de ${vigentes.length || 'todos'} publicados`
    : !nota.trim() ? 'Escribe la nota del gestor'
    : alertasIncompletas ? 'Cada alerta lleva su texto y su justificación'
    : requiereRevision && !confirmaRevision ? 'Confirma la revisión manual que pide el modelo'
    : null
  const faltaPreliminar = publicados === 0 ? 'Publica al menos un informe' : !nota.trim() ? 'Escribe la nota del gestor' : alertasIncompletas ? 'Cada alerta lleva su texto y su justificación' : null

  const emitir = async (tipo) => {
    setEnviando(true); setError(null)
    try {
      const r = await solicitudService.emitirEstudio(solicitud.idServicio, {
        tipo, concepto: nota.trim(), confirmaRevisionManual: confirmaRevision,
        alertas: alertas.map(a => ({ texto: a.texto.trim(), justificacion: a.justificacion.trim() })),
      })
      // El PDF se arma en segundo plano: el cliente recibe el aviso cuando esté listo, no ahora.
      borrador.borrar()
      onToast?.(tipo === 'FINAL' ? `Estudio emitido · v${r.version} · generando el PDF… el cliente recibirá el aviso cuando esté listo`
        : `Preliminar emitido · v${r.version} · generando el PDF…`)
      onHecho()
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo emitir el estudio. Reintentar')
    } finally { setEnviando(false) }
  }

  const verVistaPrevia = async () => {
    setGenerandoVista(true); setErrorVista(null)
    abortoVista.current?.abort()
    const aborto = new AbortController()
    abortoVista.current = aborto
    try {
      const inicial = await solicitudService.vistaPreviaEstudio(solicitud.idServicio, {
        tipo: 'PRELIMINAR', concepto: nota.trim(), confirmaRevisionManual: false,
        alertas: alertas.filter(a => a.texto.trim()).map(a => ({ texto: a.texto.trim(), justificacion: a.justificacion.trim() })),
      })
      const listo = await esperarTrabajo(inicial, { signal: aborto.signal })
      guardarBlob(await trabajoService.descargarResultado(listo.id), listo.nombreArchivo ?? `Vista_previa_estudio_${solicitud.idServicio}.pdf`)
      onToast?.('Vista previa descargada · lleva la marca «VISTA PREVIA»')
    } catch (e) {
      if (!e.cancelado) setErrorVista(mensajeDeError(e, 'No se pudo generar la vista previa. Reintentar'))
    } finally { setGenerandoVista(false) }
  }

  return createPortal(
    <div ref={capa} className="fixed inset-0 z-50 flex justify-end bg-black/30 overscroll-contain" onClick={onClose}>
      <aside role="dialog" aria-modal="true" aria-labelledby={idTitulo} onClick={e => e.stopPropagation()}
        className="w-full max-w-[460px] h-full bg-white shadow-2xl flex flex-col rounded-l-2xl">
        <header className="px-6 pt-5 pb-4 border-b border-gray-200 flex items-start justify-between gap-3">
          <div>
            <p className="text-[11px] font-bold tracking-wider uppercase text-[#A8323F]">{publicados} de {vigentes.length} publicados</p>
            <h2 id={idTitulo} className="text-lg font-bold text-gray-900">Emitir estudio completo</h2>
            <p className="text-xs text-gray-600">#{solicitud.idServicio} {nombreEvaluado} · {solicitud.proceso}</p>
          </div>
          <button ref={cierre} type="button" onClick={onClose} aria-label="Cerrar" className="min-h-[44px] min-w-[44px] rounded-lg text-gray-600 hover:bg-gray-100">✕</button>
        </header>

        <div className="flex-1 overflow-y-auto overscroll-contain px-6 py-5 space-y-5">
          {verRecuperado && (
            <p role="status" className="flex flex-wrap items-center gap-x-3 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
              <span>Recuperamos lo que tenías escrito ({fechaHora(recuperado.guardadoEn)}).</span>
              <button type="button" onClick={descartarBorrador} className="min-h-[44px] font-semibold underline focus-visible:outline focus-visible:outline-2">Descartar borrador</button>
            </p>
          )}
          <section>
            <h3 className="text-sm font-semibold text-gray-900 mb-2">Nivel de riesgo del estudio</h3>
            {errorRiesgo ? <p className="text-sm text-red-700">No se pudo calcular el nivel. Reintentar</p>
              : !riesgo ? <p className="text-sm text-gray-600">Calculando…</p>
              : riesgo.nivelEfectivo ? (
                <>
                  <span className={`inline-block rounded-full px-4 py-1.5 text-base font-bold ${CHIP[riesgo.nivelEfectivo] ?? 'bg-gray-100 text-gray-700'}`}>{riesgo.nivelEfectivo}</span>
                  <p className="text-xs text-gray-600 mt-1">Calculado por el modelo con {riesgo.subprocesos?.length ?? 0} subprocesos.</p>
                </>
              ) : <p className="text-sm text-gray-700">Todavía sin calificar: {riesgo.motivosRevision?.[0] ?? riesgo.motivoProcesoNoCalifica ?? 'faltan informes por publicar'}.</p>}
            {riesgo && (
              <div className="mt-2">
                <button type="button" onClick={() => setVerRegistro(v => !v)} aria-expanded={verRegistro}
                  className="text-xs font-semibold text-[#A8323F] underline min-h-[44px]">{verRegistro ? 'Ocultar' : 'Ver'} registro interno (solo en pantalla)</button>
                {verRegistro && (
                  <div className="rounded-lg bg-gray-100 px-3 py-2 text-xs text-gray-800 space-y-1">
                    <p>Cobertura: {num(riesgo.cobertura)} · mínima {num(riesgo.coberturaMinima)}</p>
                    <p>Promedio sin ajustar: {num(riesgo.puntajePonderado)} · puntaje final {num(riesgo.puntajeFinal)}</p>
                    {(riesgo.motivosAjuste ?? []).map((m, i) => <p key={i}>Ajuste: {m.descripcion ?? m.texto ?? JSON.stringify(m)}</p>)}
                    {(riesgo.subprocesos ?? []).map(s => <p key={s.codigo} className="tabular-nums">{s.nombre}: peso {num(s.pesoEfectivo)} % · {num(s.puntajeFinal)} pts</p>)}
                    {(riesgo.motivosRevision ?? []).map((m, i) => <p key={`r${i}`}>Revisión manual: {m}</p>)}
                  </div>
                )}
              </div>
            )}
            {requiereRevision && (
              <label className="mt-2 flex items-start gap-3 text-sm text-gray-900 cursor-pointer rounded-lg border border-amber-300 bg-amber-50 px-3 py-2">
                <input type="checkbox" checked={confirmaRevision} onChange={e => setConfirmaRevision(e.target.checked)} className="mt-1 h-4 w-4 accent-[#A8323F]" />
                El modelo pide revisión manual. La revisé y confirmo el resultado (quedan mi usuario y la fecha).
              </label>
            )}
          </section>

          <section>
            <label htmlFor={`${idTitulo}-nota`} className="block text-sm font-semibold text-gray-900 mb-1">Nota del gestor (concepto final)</label>
            <textarea id={`${idTitulo}-nota`} rows={4} value={nota} onChange={e => setNota(e.target.value)} className={campoCls} />
            <p className="text-xs text-gray-600 mt-1">Va en el Informe de análisis final y en el portal del cliente.</p>
          </section>

          <section>
            {alertas.map((a, i) => (
              <div key={i} className="mb-2 rounded-lg border border-gray-200 p-3 space-y-2">
                <label className="block text-xs font-medium text-gray-700">Alerta para el cliente
                  <input value={a.texto} onChange={e => setAlertas(l => l.map((x, j) => j === i ? { ...x, texto: e.target.value } : x))} className={`${campoCls} mt-1`} /></label>
                <label className="block text-xs font-medium text-gray-700">Justificación (interna, el cliente no la ve)
                  <input value={a.justificacion} onChange={e => setAlertas(l => l.map((x, j) => j === i ? { ...x, justificacion: e.target.value } : x))} className={`${campoCls} mt-1`} /></label>
                <button type="button" onClick={() => setAlertas(l => l.filter((_, j) => j !== i))} className="text-xs text-red-700 underline min-h-[44px]">Quitar alerta</button>
              </div>
            ))}
            <button type="button" onClick={() => setAlertas(l => [...l, { texto: '', justificacion: '' }])} className={`${botonBase} w-full border border-gray-300 text-gray-900 hover:bg-gray-50`}>
              Agregar alerta para el cliente
            </button>
          </section>

          <section>
            <h3 className="text-sm font-semibold text-gray-900 mb-1">Contenido del PDF, en este orden</h3>
            <ol className="list-decimal pl-5 text-sm text-gray-800 space-y-0.5">
              <li><strong>Informe de análisis final</strong> (portada del sistema)</li>
              {vigentes.filter(sp => sp.estado === 'PUBLICADO').map(sp => <li key={sp.id}>{sp.subproceso}</li>)}
            </ol>
            <button type="button" onClick={verVistaPrevia} disabled={publicados === 0 || generandoVista} aria-describedby={`${idTitulo}-vista`}
              className={`${botonBase} w-full mt-3 border border-gray-300 text-gray-900 hover:bg-gray-50 disabled:text-gray-600 disabled:cursor-not-allowed`}>
              {generandoVista ? 'Generando la vista previa…' : 'Vista previa del PDF completo'}</button>
            <p id={`${idTitulo}-vista`} className="text-xs text-gray-600 mt-1">
              {publicados === 0 ? 'Publica al menos un informe para ver la vista previa.'
                : 'Descarga el PDF con la nota y las alertas que llevas escritas, marcado «VISTA PREVIA». No emite nada ni avisa al cliente.'}</p>
            {errorVista && <p role="alert" className="text-xs text-red-700 mt-1">{errorVista}</p>}
          </section>

          <div className="rounded-lg bg-gray-100 px-4 py-3 text-xs text-gray-700 leading-relaxed">
            <strong>Al emitir:</strong> el estudio pasa a <strong>Publicado</strong> y la pasta queda <strong>emitida</strong>. El PDF se arma en segundo plano; cuando está listo, el cliente
            ve &quot;Tu informe está listo&quot; con el botón &quot;Descargar estudio completo&quot; y recibe el aviso. Si todavía hay pendientes, puedes emitir un preliminar: el PDF dice qué falta y el cliente no ve el nivel del estudio.
          </div>
          {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        </div>

        <footer className="px-6 py-4 border-t border-gray-200 flex flex-wrap items-center justify-end gap-3">
          <button type="button" onClick={onClose} className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Cancelar</button>
          <button type="button" onClick={() => emitir('PRELIMINAR')} disabled={!!faltaPreliminar || enviando}
            className={`${botonBase} border border-[#A8323F] text-[#A8323F] hover:bg-red-50 disabled:opacity-50 disabled:cursor-not-allowed`}>Emitir preliminar</button>
          <button type="button" onClick={() => emitir('FINAL')} disabled={!!faltaFinal || enviando}
            className={`${botonBase} ${faltaFinal || enviando ? 'bg-gray-200 text-gray-600 cursor-not-allowed' : 'bg-[#A8323F] text-white hover:bg-[#8f2a35]'}`}>
            {enviando ? 'Emitiendo…' : 'Emitir y publicar al cliente'}
          </button>
          {(faltaFinal || faltaPreliminar) && <p className="basis-full text-xs text-gray-600 text-right">{faltaFinal ?? ''}{faltaFinal && faltaPreliminar ? ' · ' : ''}{faltaPreliminar ? `Preliminar: ${faltaPreliminar}` : ''}</p>}
        </footer>
      </aside>
    </div>,
    document.body
  )
}

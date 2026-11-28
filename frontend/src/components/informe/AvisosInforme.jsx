import { useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useVentana } from '../../hooks/useVentana'
import asignacionService from '../../services/asignacionService'
import { fechaCorta, fechaHora as fechaYHora } from '../../utils/fechas'

// PC-04 · Lo que el ejecutor ve ARRIBA de su formato después de enviarlo: «Enviado · en revisión», «Devuelto por … Motivo: …» o «Publicado al cliente el … por …»
// (con el ajuste que hizo el gestor y el enlace «Solicitar corrección»). Un solo componente para los siete formatos: ninguno arma su propio aviso. Lo muestra cada
// pantalla justo debajo de su encabezado, con `meta` = la respuesta del formulario. El diseño es PB-H-28_ejemplo_solicitar_correccion.png.

// Las fechas salen de la utilidad única (utils/fechas.js); sin dato, '' para que la frase que las lleva se omita.
const fechaHora = (iso) => fechaYHora(iso, '')
const fecha = (iso) => fechaCorta(iso, '')

const TIPOS_CORRECCION = [
  { valor: 'DATO_ERRADO', texto: 'Dato errado' },
  { valor: 'SOPORTE_EQUIVOCADO', texto: 'Soporte equivocado' },
  { valor: 'HALLAZGO_MAL_CALIFICADO', texto: 'Hallazgo mal calificado' },
  { valor: 'OTRO', texto: 'Otro' },
]
const campoCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-[#A8323F]'
const botonBase = 'min-h-[44px] px-4 rounded-lg text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#A8323F]'

/** Ventana «Solicitar corrección · #N.º · <subproceso> · publicado el <fecha>». Le llega al gestor como novedad; el cliente no la ve. */
function ModalSolicitarCorreccion({ meta, idServicioSubproceso, onClose, onEnviada }) {
  const idTitulo = useId()
  const primero = useRef(null)
  const capa = useRef(null)
  const [tipo, setTipo] = useState('')
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  // PC-06: foco adentro al abrir, Esc cierra, el foco vuelve a quien la abrió y el fondo queda inerte (hooks/useVentana); la ventana va en un portal.
  useVentana({ capa, alEscape: onClose })

  const enviar = async () => {
    if (!tipo) { setError('Elige qué hay que corregir.'); return }
    if (!motivo.trim()) { setError('Explícale al gestor qué hay que corregir.'); return }
    setEnviando(true); setError(null)
    try {
      const r = await asignacionService.solicitarCorreccion(idServicioSubproceso, { tipo, motivo: motivo.trim() })
      onEnviada(r?.gestor)
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo enviar la solicitud. Reintentar')
    } finally { setEnviando(false) }
  }

  return createPortal(
    <div ref={capa} className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 overscroll-contain" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-labelledby={idTitulo} onClick={e => e.stopPropagation()}
        className="w-full max-w-[540px] max-h-full overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <header className="rounded-t-2xl bg-[#A8323F] px-6 py-4 text-white">
          <h2 id={idTitulo} className="text-xl font-bold">Solicitar corrección</h2>
          <p className="text-sm text-white/90">#{meta.idServicio} · {meta.nombreSubproceso}{meta.publicacion?.fecha ? ` · publicado el ${fecha(meta.publicacion.fecha)}` : ''}</p>
        </header>
        <div className="space-y-4 px-6 py-5">
          <div>
            <label htmlFor={`${idTitulo}-tipo`} className="block text-sm font-semibold text-gray-900 mb-1">Qué hay que corregir <span className="text-red-700">*</span></label>
            <select ref={primero} id={`${idTitulo}-tipo`} value={tipo} onChange={e => setTipo(e.target.value)} className={`${campoCls} min-h-[44px]`}>
              <option value="">Elige… (Dato errado · Soporte equivocado · Hallazgo mal calificado · Otro)</option>
              {TIPOS_CORRECCION.map(t => <option key={t.valor} value={t.valor}>{t.texto}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor={`${idTitulo}-motivo`} className="block text-sm font-semibold text-gray-900 mb-1">Explícale al gestor <span className="text-red-700">*</span></label>
            <textarea id={`${idTitulo}-motivo`} rows={4} maxLength={2000} value={motivo} onChange={e => setMotivo(e.target.value)}
              aria-describedby={`${idTitulo}-ayuda`} placeholder="Ej.: la fecha de expedición quedó 01/06/2010 y es 01/06/2011." className={campoCls} />
            <p id={`${idTitulo}-ayuda`} className="mt-1 text-xs text-gray-700">
              Le llega al gestor como novedad. Si está de acuerdo, él revierte el informe por Reversiones y te lo devuelve para editar. El cliente no ve esta solicitud.
            </p>
          </div>
          {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
          <div className="flex flex-wrap justify-end gap-3">
            <button type="button" onClick={onClose} disabled={enviando} className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Cancelar</button>
            <button type="button" onClick={enviar} disabled={enviando}
              className={`${botonBase} bg-[#A8323F] text-white hover:bg-[#8f2a35] disabled:opacity-60`}>{enviando ? 'Enviando…' : 'Enviar al gestor'}</button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  )
}

function AvisoPublicado({ meta, puedeSolicitar, onSolicitar }) {
  const p = meta.publicacion
  const cuando = p?.fecha ?? meta.fechaPublicado
  return (
    <section aria-label="Informe publicado" className="mb-4 print:hidden">
      <div className="flex gap-3 rounded-xl border border-[#8FD6CC] bg-[#E8F8F5] px-4 py-3">
        <span aria-hidden="true" className="mt-1.5 h-2.5 w-2.5 flex-shrink-0 rounded-full bg-[#0D9488]" />
        <div className="text-sm text-[#0B3B36]">
          <p><strong>Publicado al cliente{cuando ? ` el ${fechaHora(cuando)}` : ''}</strong>{p?.publicadoPor ? ` por ${p.publicadoPor}` : ''}. Ya no se puede editar.</p>
          {puedeSolicitar && (
            <p>¿Encontraste un error después de publicar?{' '}
              <button type="button" onClick={onSolicitar} className="font-semibold text-[#A8323F] underline underline-offset-2 hover:text-[#8f2a35] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#A8323F]">Solicitar corrección</button></p>
          )}
        </div>
      </div>
      {(p?.conclusionAjustada || p?.nivelAjustado) && (
        <div className="mt-3 space-y-3 rounded-xl border border-dashed border-gray-300 bg-gray-50 px-4 py-3 text-sm text-gray-900">
          {p.conclusionAjustada && (
            <div>
              <p className="font-semibold">El gestor ajustó la conclusión al publicar</p>
              <p className="text-gray-700"><del><span className="font-medium">Antes:</span> «{p.conclusionOriginal}»</del></p>
              <p><span className="font-medium">Ahora:</span> «{p.conclusionPublicada}»</p>
            </div>
          )}
          {p.nivelAjustado && (
            <div>
              <p className="font-semibold">El gestor ajustó el nivel al publicar</p>
              <p><del className="text-gray-700"><span className="font-medium">Antes:</span> {p.nivelSugerido}</del> · <span className="font-medium">Ahora:</span> {p.nivelPublicado}</p>
              {p.motivoNivel && <p className="text-gray-700">Motivo: {p.motivoNivel}</p>}
            </div>
          )}
        </div>
      )}
    </section>
  )
}

function AvisoDevuelto({ devolucion }) {
  return (
    <section aria-label="Informe devuelto" className="mb-4 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950 print:hidden">
      <p>
        <strong>Devuelto{devolucion.por ? ` por ${devolucion.por}` : ''}{devolucion.fecha ? ` el ${fecha(devolucion.fecha)}` : ''}</strong>
        {devolucion.motivo ? <> · Motivo: {devolucion.motivo}</> : null}
        {devolucion.detalle ? <> · {devolucion.detalle}</> : null}
      </p>
      <p className="mt-1 text-xs text-amber-900">Corrígelo y vuelve a enviarlo: el gestor recibirá el aviso.</p>
    </section>
  )
}

function AvisoEnviado() {
  return (
    <section aria-label="Informe enviado" className="mb-4 rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 print:hidden">
      <p className="text-sm font-semibold text-gray-900">Enviado · en revisión del gestor</p>
      <p className="text-sm text-gray-700">Quedó en solo lectura. Si el gestor lo devuelve, verás el motivo aquí y podrás corregirlo.</p>
    </section>
  )
}

/**
 * esEjecutor: quien ejecutó el informe (el aviso de enviado y el enlace «Solicitar corrección» son solo suyos; el gestor que supervisa solo ve lo publicado y lo devuelto).
 * onToast(mensaje): para el «Solicitud enviada a <gestor>».
 */
export default function AvisosInforme({ meta, esEjecutor, idServicioSubproceso, onToast }) {
  const [abierta, setAbierta] = useState(false)
  if (!meta) return null
  const publicado = meta.estado === 'PUBLICADO'
  return (
    <>
      {meta.devolucion && meta.estado === 'EN_EJECUCION' && <AvisoDevuelto devolucion={meta.devolucion} />}
      {esEjecutor && meta.estado === 'FINALIZADO' && <AvisoEnviado />}
      {publicado && <AvisoPublicado meta={meta} puedeSolicitar={esEjecutor} onSolicitar={() => setAbierta(true)} />}
      {abierta && (
        <ModalSolicitarCorreccion meta={meta} idServicioSubproceso={idServicioSubproceso} onClose={() => setAbierta(false)}
          onEnviada={(gestor) => { setAbierta(false); onToast?.(gestor ? `Solicitud enviada a ${gestor}` : 'Solicitud enviada al gestor') }} />
      )}
    </>
  )
}

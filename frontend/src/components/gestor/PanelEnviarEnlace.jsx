import { useCallback, useEffect, useId, useRef, useState } from 'react'
import tokenService from '../../services/tokenService'

// PB-04 · «Enviar enlace al candidato»: contacto, qué va a diligenciar (el sistema decide corto o completo), por dónde se envía, el mensaje y el seguimiento.
// WhatsApp abre el chat con el mensaje escrito (no hay proveedor que lo envíe solo); el correo sale desde el servidor. Todo queda registrado.
const campoCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'
const botonBase = 'min-h-[44px] px-4 rounded-lg text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#A8323F]'
const COLOR_PASO = { HECHO: 'bg-[#1f6b44]', ACTUAL: 'bg-[#D9952B]', PENDIENTE: 'bg-gray-300' }
const fechaHora = (iso) => iso ? new Date(iso).toLocaleString('es-CO', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : ''

export default function PanelEnviarEnlace({ solicitud, onClose, onHecho, onToast }) {
  const idTitulo = useId()
  const cierre = useRef(null)
  const celularRef = useRef(null)
  const [link, setLink] = useState(null)
  const [ctx, setCtx] = useState(null)
  const [pasos, setPasos] = useState([])
  const [celular, setCelular] = useState('')
  const [correo, setCorreo] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [canales, setCanales] = useState({ WHATSAPP: true, CORREO: true })
  const [cargando, setCargando] = useState(true)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    cierre.current?.focus()
    const salir = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', salir)
    return () => window.removeEventListener('keydown', salir)
  }, [onClose])

  const cargar = useCallback(async () => {
    setCargando(true); setError(null)
    try {
      // Si el servicio todavía no tiene enlace, se genera (uno solo por servicio: nunca reemplaza uno que ya exista).
      const l = (await tokenService.porServicio(solicitud.idServicio)) ?? (await tokenService.generar(solicitud.idServicio))
      setLink(l)
      const [c, s] = await Promise.all([tokenService.envioContexto(l.id), tokenService.seguimiento(l.id)])
      setCtx(c); setPasos(s)
      setCelular(c.celular ?? ''); setCorreo(c.correo ?? ''); setMensaje(c.mensaje)
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo preparar el enlace. Reintentar')
    } finally { setCargando(false) }
  }, [solicitud.idServicio])

  useEffect(() => { cargar() }, [cargar])

  const elegidos = Object.keys(canales).filter(k => canales[k])
  const faltaContacto = canales.WHATSAPP && celular.replace(/\D/g, '').length < 10 ? 'Escribe un celular válido para WhatsApp'
    : canales.CORREO && !/^\S+@\S+\.\S+$/.test(correo.trim()) ? 'Escribe un correo válido'
    : elegidos.length === 0 ? 'Elige por dónde enviarlo' : null
  const textoBoton = elegidos.length === 2 ? 'Enviar por WhatsApp y correo' : canales.WHATSAPP ? 'Enviar por WhatsApp' : canales.CORREO ? 'Enviar por correo' : 'Enviar'

  const enviar = async () => {
    setEnviando(true); setError(null)
    try {
      const r = await tokenService.enviar(link.id, { celular, correo, mensaje, canales: elegidos })
      if (r.whatsappUrl) window.open(r.whatsappUrl, '_blank', 'noopener')
      onToast?.(r.enviados.includes('CORREO') && r.whatsappUrl ? 'Enlace enviado por correo · WhatsApp abierto con el mensaje listo'
        : r.whatsappUrl ? 'WhatsApp abierto con el mensaje listo: envíalo desde ahí' : 'Enlace enviado por correo')
      onHecho?.()
      cargar()
    } catch (e) {
      setError((e.response?.data?.mensaje ?? 'No se pudo enviar.') + ' Reintentar · Copiar enlace')
    } finally { setEnviando(false) }
  }

  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(ctx.url)
      await tokenService.copiado(link.id)
      onToast?.('Enlace copiado · quedó registrado que lo enviaste por fuera')
      cargar()
    } catch { setError('No se pudo copiar el enlace. Cópialo a mano: ' + ctx.url) }
  }

  const ampliar = async () => {
    try { const r = await tokenService.ampliar(link.id, 2); onToast?.(`Fecha de referencia ampliada hasta el ${r.venceReferencia}`); cargar() }
    catch (e) { setError(e.response?.data?.mensaje ?? 'No se pudo ampliar la vigencia. Reintentar') }
  }

  const nombre = `${solicitud.nombresEvaluado ?? ''} ${solicitud.apellidosEvaluado ?? ''}`.trim()
  const yaEnviado = pasos.some(p => p.clave === 'ENVIADO' && p.estado === 'HECHO')
  const vence = ctx?.venceReferencia ? new Date(`${ctx.venceReferencia}T12:00:00`).toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'numeric', year: 'numeric' }) : ''

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={onClose}>
      <aside role="dialog" aria-modal="true" aria-labelledby={idTitulo} onClick={e => e.stopPropagation()}
        className="w-full max-w-[560px] h-full bg-white shadow-2xl flex flex-col rounded-l-2xl">
        <header className="px-6 pt-5 pb-4 border-b border-gray-200 flex items-start justify-between gap-3">
          <div>
            <p className="text-[11px] font-bold tracking-wider uppercase text-[#A8323F]">Paso 1 · Asignar · Pendiente</p>
            <h2 id={idTitulo} className="text-lg font-bold text-gray-900">Enviar enlace al candidato</h2>
            <p className="text-xs text-gray-600">#{solicitud.idServicio} · {nombre}{solicitud.cargo ? ` · ${solicitud.cargo}` : ''}{solicitud.proceso ? ` · ${solicitud.proceso}` : ''}</p>
          </div>
          <button ref={cierre} type="button" onClick={onClose} aria-label="Cerrar" className="min-h-[44px] min-w-[44px] rounded-lg text-gray-600 hover:bg-gray-100">✕</button>
        </header>

        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          {cargando && <p className="text-sm text-gray-700">Preparando el enlace…</p>}
          {error && <p role="alert" className="text-sm text-red-700">{error}</p>}

          {ctx && (
            <>
              {yaEnviado && (
                <section aria-label="Seguimiento del enlace" className="rounded-xl border border-gray-200 p-4">
                  <h3 className="text-sm font-bold text-gray-900">Seguimiento del enlace</h3>
                  <ol className="mt-3 space-y-3">
                    {pasos.map(p => (
                      <li key={p.clave} className="flex items-start gap-3">
                        <span aria-hidden="true" className={`mt-1.5 h-[10px] w-[10px] flex-shrink-0 rounded-full ${COLOR_PASO[p.estado]}`} />
                        <div className="flex-1">
                          <p className="text-sm font-semibold text-gray-900">{p.titulo}<span className="sr-only"> ({p.estado === 'HECHO' ? 'hecho' : p.estado === 'ACTUAL' ? 'en curso' : 'pendiente'})</span></p>
                          <p className="text-xs text-gray-700">{p.detalle}</p>
                        </div>
                        <span className="text-xs text-gray-700 tabular-nums">{fechaHora(p.fecha)}</span>
                      </li>
                    ))}
                  </ol>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button type="button" onClick={copiar} className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Copiar enlace</button>
                    <button type="button" onClick={() => celularRef.current?.focus()} className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Cambiar contacto</button>
                    <button type="button" onClick={ampliar} className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Ampliar vigencia</button>
                  </div>
                </section>
              )}

              <section>
                <h3 className="text-sm font-bold text-gray-900">1. Contacto del candidato <span className="font-normal text-gray-700">· viene de la solicitud del cliente</span></h3>
                <div className="mt-2 grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <label className="block text-xs font-medium text-gray-700">Celular (WhatsApp)
                    <input ref={celularRef} value={celular} onChange={e => setCelular(e.target.value)} inputMode="tel" className={`${campoCls} mt-1`} /></label>
                  <label className="block text-xs font-medium text-gray-700">Correo
                    <input value={correo} onChange={e => setCorreo(e.target.value)} type="email" className={`${campoCls} mt-1`} /></label>
                </div>
                <p className="text-xs text-gray-600 mt-1">Si corriges un dato aquí, queda registrado en el historial de la solicitud.</p>
              </section>

              <section>
                <h3 className="text-sm font-bold text-gray-900 flex flex-wrap items-center gap-2">2. Qué va a diligenciar
                  <span className="rounded-full bg-[#141B2B] px-2.5 py-0.5 text-[11px] font-semibold text-white">{ctx.etiqueta}</span>
                  <span className="font-normal text-gray-700 text-xs">· lo elige el sistema según el estudio · abre con la cédula</span></h3>
                <ul className="mt-2 grid grid-cols-1 sm:grid-cols-3 gap-2">
                  {ctx.secciones.map(s => (
                    <li key={s.titulo} className="rounded-lg bg-gray-100 p-3"><p className="text-sm font-semibold text-gray-900">{s.titulo}</p><p className="text-xs text-gray-700">{s.detalle}</p></li>
                  ))}
                </ul>
                {ctx.tipoEnlace === 'CORTO' && <p className="text-xs text-gray-700 mt-2">No pide educación, experiencia, referencias, mora o embargos, ni documentos. Si después le agregan otro servicio, recibe el enlace completo con la misma cédula y lo ya diligenciado aparece precargado.</p>}
              </section>

              <section>
                <h3 className="text-sm font-bold text-gray-900">3. Por dónde envías</h3>
                <div className="mt-2 flex flex-wrap gap-2">
                  {[['WHATSAPP', 'WhatsApp'], ['CORREO', 'Correo']].map(([k, t]) => (
                    <label key={k} className={`cursor-pointer inline-flex items-center gap-2 px-4 min-h-[44px] rounded-lg border-2 text-sm font-semibold ${canales[k] ? 'border-[#A8323F] bg-[#FBEFF0] text-[#8f2a35]' : 'border-gray-200 text-gray-800'}`}>
                      <input type="checkbox" checked={!!canales[k]} onChange={e => setCanales(c => ({ ...c, [k]: e.target.checked }))} className="accent-[#A8323F]" />{t}
                    </label>
                  ))}
                  <button type="button" onClick={copiar} className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Copiar enlace</button>
                </div>
                <p className="text-xs text-gray-600 mt-1">SMS no está disponible. «Copiar enlace» también deja registro: el sistema anota que lo enviaste por fuera y quién lo copió.</p>
              </section>

              <section>
                <label htmlFor={`${idTitulo}-mensaje`} className="block text-sm font-bold text-gray-900">4. Mensaje <span className="font-normal text-gray-700">· plantilla de Polygraph, puedes editarla (va por WhatsApp)</span></label>
                <textarea id={`${idTitulo}-mensaje`} rows={7} value={mensaje} onChange={e => setMensaje(e.target.value)} className={`${campoCls} mt-2`} />
                <p className="text-xs text-gray-700 mt-1">Fecha de referencia: <strong>{vence}</strong> (2 días hábiles) · Recordatorio automático por correo: <strong>a las 24 h si no lo abre o no lo termina</strong></p>
              </section>

              <section className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-xs text-gray-900 space-y-1">
                <p className="font-bold text-sm">Qué pasa al enviar</p>
                <p>· El estudio sigue en <strong>Pendiente</strong>: enviar el enlace no crea un estado.</p>
                <p>· Cuando el candidato envía su información, los subprocesos aparecen en el paso 1 para asignar.</p>
                <p>· El cliente ve en su portal: &quot;Enlace enviado al candidato&quot; con la fecha y hora.</p>
              </section>
            </>
          )}
        </div>

        <footer className="px-6 py-4 border-t border-gray-200 flex flex-wrap items-center justify-end gap-3">
          <button type="button" onClick={onClose} className={`${botonBase} border border-gray-300 text-gray-900 hover:bg-gray-50`}>Cancelar</button>
          <button type="button" onClick={enviar} disabled={!ctx || !!faltaContacto || enviando}
            className={`${botonBase} ${!ctx || faltaContacto || enviando ? 'bg-gray-200 text-gray-600 cursor-not-allowed' : 'bg-[#A8323F] text-white hover:bg-[#8f2a35]'}`}>
            {enviando ? 'Enviando…' : yaEnviado ? textoBoton.replace('Enviar', 'Reenviar') : textoBoton}
          </button>
          {faltaContacto && <p className="basis-full text-xs text-gray-600 text-right">{faltaContacto}</p>}
        </footer>
      </aside>
    </div>
  )
}

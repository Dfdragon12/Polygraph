import { useEffect, useRef, useState } from 'react'
import formulariosService from '../../services/formulariosService'
import trabajoService, { esperarTrabajo, mensajeDeError } from '../../services/trabajoService'

// PF-07 · «Revisión y envío»: lo que falta (con enlace a cada tema), el informe real en PDF tal como lo verá el
// cliente y el envío al gestor. El envío queda apagado mientras falte algo; el servidor vuelve a validar.

const plural = (n) => (n === 1 ? 'cosa' : 'cosas')

// Lo que dijo el poligrafista en las admisiones, de solo lectura: lo que se imprime y lo personal o de salud, que no.
export function ResumenAdmisiones({ secciones }) {
  const textos = (clave) => {
    const sec = secciones.find(s => s.clave === clave && !s.origenFijoClave)
    const id = sec?.columnas.find(c => c.clave === 'ad_texto')?.idColumna
    return sec && id ? sec.filas.map(f => f.valores[id]).filter(t => t && String(t).trim() !== '') : []
  }
  const impresas = textos('admisiones')
  const personales = textos('admisiones_sensibles')
  return (
    <div>
      <h3 className="text-sm font-bold text-gray-700 mb-2">Admisiones</h3>
      {impresas.length === 0 ? <p className="text-gray-700">Sin admisiones que se impriman.</p> : (
        <ul className="list-disc pl-5 space-y-1">{impresas.map((t, i) => <li key={i} className="break-words">{t}</li>)}</ul>
      )}
      {personales.length > 0 && (
        <div className="mt-3 rounded border border-gray-300 bg-gray-50 p-3">
          <p className="text-xs font-semibold text-gray-700 mb-1">Personales o de salud · no se imprimen</p>
          <ul className="list-disc pl-5 space-y-1 text-sm">{personales.map((t, i) => <li key={i} className="break-words">{t}</li>)}</ul>
        </div>
      )}
    </div>
  )
}

export default function RevisionEnvio({ idServicioSubproceso, faltantes, onIrA, asegurarGuardado, version, pedidoVista, soloLectura, enviando, onEnviar, onError }) {
  const [vista, setVista] = useState({ estado: 'ninguna', url: null, version: null })
  const [confirmando, setConfirmando] = useState(false)
  const abortoRef = useRef(null)
  const urlRef = useRef(null)
  const { grupos, total } = faltantes

  const soltarUrl = () => { if (urlRef.current) { window.URL.revokeObjectURL(urlRef.current); urlRef.current = null } }
  useEffect(() => () => { abortoRef.current?.abort(); soltarUrl() }, [])

  const generar = async () => {
    abortoRef.current?.abort()
    const aborto = new AbortController()
    abortoRef.current = aborto
    setVista(v => ({ ...v, estado: 'generando' }))
    try {
      if (!(await asegurarGuardado())) { setVista(v => ({ ...v, estado: v.url ? 'lista' : 'ninguna' })); return }
      const inicial = await formulariosService.solicitarDocumentoPdf(idServicioSubproceso)
      const listo = await esperarTrabajo(inicial, { signal: aborto.signal })
      const r = await trabajoService.descargarResultado(listo.id)
      soltarUrl()
      urlRef.current = window.URL.createObjectURL(new Blob([r.data], { type: 'application/pdf' }))
      setVista({ estado: 'lista', url: urlRef.current, version })
    } catch (err) {
      if (err.cancelado) return
      setVista(v => ({ ...v, estado: v.url ? 'lista' : 'ninguna' }))
      onError(mensajeDeError(err, 'No se pudo generar la vista del informe.'))
    }
  }

  // «Ver como lo verá el cliente» (arriba) pide la vista de una vez.
  useEffect(() => { if (pedidoVista > 0) generar() }, [pedidoVista]) // eslint-disable-line react-hooks/exhaustive-deps

  const desactualizada = vista.estado === 'lista' && vista.version !== version

  return (
    <div className="space-y-6">
      {/* Qué falta */}
      {total > 0 ? (
        <section aria-labelledby="faltan" className="rounded-lg border border-amber-300 bg-amber-50 p-4">
          <h3 id="faltan" className="font-bold text-amber-900">Faltan {total} {plural(total)} para poder enviar</h3>
          <ul className="mt-2 space-y-3">
            {grupos.map(g => (
              <li key={g.id}>
                <button type="button" onClick={() => onIrA(g.id)} className="text-sm font-semibold text-primary-700 underline min-h-11 text-left">
                  Fase {g.fase} · {g.titulo} →
                </button>
                <ul className="list-disc pl-5 text-sm text-gray-900">
                  {g.items.map((t, i) => <li key={i} className="break-words">{t}</li>)}
                </ul>
              </li>
            ))}
          </ul>
        </section>
      ) : (
        <section className="rounded-lg border border-green-300 bg-green-50 p-4">
          <p className="font-bold text-green-900">Todo está diligenciado.</p>
          <p className="text-sm text-green-900">Revisa el informe y envíalo al gestor.</p>
        </section>
      )}

      {/* El informe real */}
      <section aria-labelledby="vista-informe">
        <h3 id="vista-informe" className="text-sm font-bold text-gray-700 mb-2">Así lo verá el cliente</h3>
        <div className="flex flex-wrap items-center gap-3">
          <button type="button" onClick={generar} disabled={vista.estado === 'generando'}
            className="min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50 disabled:opacity-60">
            {vista.estado === 'generando' ? 'Generando el informe…' : vista.url ? 'Actualizar la vista' : 'Ver el informe'}
          </button>
          {vista.url && <a href={vista.url} target="_blank" rel="noreferrer" className="text-sm text-primary-700 underline">Abrir el PDF en otra pestaña</a>}
        </div>
        {desactualizada && <p className="mt-2 text-sm text-amber-800" role="status">Cambiaste algo después de generar esta vista. Actualízala para verlo.</p>}
        {vista.url && (
          <iframe title="Vista del informe en PDF" src={vista.url} className="mt-3 w-full h-[70vh] border border-gray-300 rounded bg-white" />
        )}
        {vista.estado === 'generando' && <p className="mt-2 text-sm text-gray-700" aria-live="polite">Esto puede tardar unos segundos.</p>}
      </section>

      {/* Envío */}
      <section aria-labelledby="envio" className="border-t border-gray-300 pt-4">
        <h3 id="envio" className="text-sm font-bold text-gray-700 mb-2">Envío</h3>
        {soloLectura ? (
          <p className="text-gray-800">Este informe ya fue enviado a revisión del gestor.</p>
        ) : confirmando ? (
          <div className="rounded-lg border border-gray-300 p-4 space-y-3" role="alertdialog" aria-label="Confirmar el envío">
            <p className="text-gray-900">Al enviarlo, el informe pasa al gestor y ya no podrás editarlo. ¿Lo envías?</p>
            <div className="flex flex-wrap gap-3">
              <button type="button" onClick={onEnviar} disabled={enviando}
                className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white">
                {enviando ? 'Enviando…' : 'Sí, enviar'}
              </button>
              <button type="button" onClick={() => setConfirmando(false)} disabled={enviando}
                className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800">Cancelar</button>
            </div>
          </div>
        ) : (
          <div>
            <button type="button" onClick={() => setConfirmando(true)} disabled={total > 0}
              className="min-h-11 px-5 text-sm font-semibold rounded-lg bg-primary-600 hover:bg-primary-700 disabled:bg-gray-300 disabled:text-gray-700 text-white">
              Enviar a revisión del gestor
            </button>
            {total > 0 && <p className="mt-2 text-sm text-gray-800">Faltan {total} {plural(total)}. Complétalas para poder enviar.</p>}
          </div>
        )}
      </section>
    </div>
  )
}

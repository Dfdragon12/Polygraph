import { useCallback, useEffect, useState } from 'react'
import { Modal } from '../ui/Modal'
import enlaceProveedorService from '../../services/enlaceProveedorService'

// PF-09 · Enlace para que un proveedor externo diligencie esta evaluación de poligrafía sin usuario ni contraseña.
// Un solo enlace vigente por evaluación; vence al enviar el informe o a las 72 h. El enlace completo se ve UNA vez, al generarlo.

const ESTADOS = {
  VIGENTE: { texto: 'Vigente', cls: 'bg-green-100 text-green-800' },
  ENVIADO: { texto: 'Vencido: se envió el informe', cls: 'bg-gray-100 text-gray-800' },
  VENCIDO: { texto: 'Vencido: pasaron 72 horas', cls: 'bg-gray-100 text-gray-800' },
  REVOCADO: { texto: 'Revocado', cls: 'bg-red-100 text-red-800' },
  REEMPLAZADO: { texto: 'Reemplazado por otro', cls: 'bg-gray-100 text-gray-800' },
}

const fecha = (iso) => (iso ? new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: 'numeric', minute: '2-digit' }) : '—')

export default function ModalEnlaceProveedor({ cita, onClose }) {
  const [historial, setHistorial] = useState(null)
  const [nuevo, setNuevo] = useState(null)       // { url, expiraEn } — solo existe en esta ventana
  const [copiado, setCopiado] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [confirmarRevocar, setConfirmarRevocar] = useState(false)
  const [error, setError] = useState(null)

  const cargar = useCallback(() => enlaceProveedorService.historial(cita.id).then(setHistorial)
    .catch(err => setError(err.response?.data?.mensaje ?? 'No se pudo cargar el historial de enlaces.')), [cita.id])
  useEffect(() => { cargar() }, [cargar])

  const vigente = (historial ?? []).find(e => e.estado === 'VIGENTE')

  const generar = async () => {
    setOcupado(true); setError(null); setCopiado(false)
    try {
      const g = await enlaceProveedorService.generar(cita.id)
      setNuevo({ url: enlaceProveedorService.urlDelEnlace(g.token), expiraEn: g.expiraEn })
      await cargar()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo generar el enlace.')
    } finally {
      setOcupado(false)
    }
  }

  const revocar = async () => {
    setOcupado(true); setError(null)
    try {
      await enlaceProveedorService.revocar(cita.id)
      setNuevo(null); setConfirmarRevocar(false)
      await cargar()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo revocar el enlace.')
    } finally {
      setOcupado(false)
    }
  }

  const copiar = async () => {
    try { await navigator.clipboard.writeText(nuevo.url); setCopiado(true) } catch { setError('No se pudo copiar. Selecciona el enlace y cópialo a mano.') }
  }

  const evaluado = `${cita.nombresEvaluado ?? ''} ${cita.apellidosEvaluado ?? ''}`.trim()
  const mensajeWhatsapp = `Hola. Este es tu enlace para diligenciar la poligrafía${evaluado ? ` de ${evaluado}` : ''}. Sirve para esa evaluación y vence al enviar el informe o en 72 horas: ${nuevo?.url ?? ''}`

  return (
    <Modal titulo="Enlace para proveedor externo" subtitulo={`${cita.subproceso}${evaluado ? ` — ${evaluado}` : ''}`} onClose={onClose} ancho="max-w-2xl">
      <div className="px-6 py-5 space-y-5">
        {error && <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

        <p className="text-sm text-gray-800">
          Con este enlace el proveedor diligencia <strong>solo esta evaluación</strong>, sin usuario ni contraseña. Vence cuando envía el informe
          o a las 72 horas, lo que ocurra primero. Solo hay un enlace vigente: generar otro cancela el anterior.
        </p>

        {nuevo && (
          <div className="rounded-lg border border-green-300 bg-green-50 p-4 space-y-3" role="status">
            <p className="text-sm font-semibold text-green-900">Enlace listo. Cópialo ahora: por seguridad no se vuelve a mostrar.</p>
            <input readOnly value={nuevo.url} onFocus={e => e.target.select()} aria-label="Enlace del proveedor"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white font-mono" />
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={copiar} className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 hover:bg-primary-700 text-white">{copiado ? 'Copiado ✓' : 'Copiar enlace'}</button>
              <a href={`https://wa.me/?text=${encodeURIComponent(mensajeWhatsapp)}`} target="_blank" rel="noreferrer"
                className="min-h-11 inline-flex items-center px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Enviar por WhatsApp</a>
            </div>
            <p className="text-xs text-green-900">Vence el {fecha(nuevo.expiraEn)} (o antes, si envía el informe).</p>
          </div>
        )}

        {confirmarRevocar ? (
          <div className="rounded-lg border border-gray-300 p-4 space-y-3" role="alertdialog" aria-label="Confirmar la revocación">
            <p className="text-sm text-gray-900">El enlace vigente dejará de funcionar de inmediato. Lo que el proveedor ya guardó se conserva. ¿Lo revocas?</p>
            <div className="flex gap-2">
              <button type="button" onClick={revocar} disabled={ocupado} className="min-h-11 px-4 text-sm font-medium rounded-lg bg-red-600 hover:bg-red-700 disabled:opacity-60 text-white">Sí, revocar</button>
              <button type="button" onClick={() => setConfirmarRevocar(false)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800">Cancelar</button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={generar} disabled={ocupado || historial === null}
              className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white">
              {ocupado ? 'Generando…' : vigente ? 'Generar otro enlace (cancela el actual)' : 'Generar enlace'}
            </button>
            {vigente && (
              <button type="button" onClick={() => setConfirmarRevocar(true)} disabled={ocupado}
                className="min-h-11 px-4 text-sm font-medium rounded-lg border border-red-300 text-red-700 hover:bg-red-50 disabled:opacity-60">Revocar enlace</button>
            )}
          </div>
        )}

        <section aria-labelledby="hist-enlaces">
          <h4 id="hist-enlaces" className="text-sm font-bold text-gray-800 mb-2">Historial</h4>
          {historial === null ? <p className="text-sm text-gray-700">Cargando…</p>
            : historial.length === 0 ? <p className="text-sm text-gray-700">Todavía no se ha generado ningún enlace para esta evaluación.</p> : (
              <ul className="space-y-2">
                {historial.map(e => {
                  const est = ESTADOS[e.estado] ?? ESTADOS.VENCIDO
                  return (
                    <li key={e.id} className="rounded-lg border border-gray-200 p-3 text-sm">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className={`px-2 py-0.5 rounded text-xs font-semibold ${est.cls}`}>{est.texto}</span>
                        <span className="text-xs text-gray-700">Generado por {e.generadoPor} · {fecha(e.generadoEn)}</span>
                      </div>
                      <p className="mt-1 text-xs text-gray-800">
                        {e.primerUsoEn ? `Primer uso: ${fecha(e.primerUsoEn)} · último: ${fecha(e.ultimoUsoEn)} · ${e.usos} ${e.usos === 1 ? 'ingreso' : 'ingresos'}` : 'Aún no se ha usado.'}
                      </p>
                      <p className="text-xs text-gray-700">
                        Vence: {fecha(e.expiraEn)}
                        {e.revocadoEn && ` · Revocado por ${e.revocadoPor ?? '—'} el ${fecha(e.revocadoEn)}`}
                        {e.cerradoEn && ` · Cerrado el ${fecha(e.cerradoEn)}`}
                      </p>
                    </li>
                  )
                })}
              </ul>
            )}
        </section>
      </div>
    </Modal>
  )
}

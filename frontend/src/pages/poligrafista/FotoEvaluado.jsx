import { useCallback, useEffect, useRef, useState } from 'react'
import formulariosService from '../../services/formulariosService'

// PF-06 · Foto del evaluado: se toma EN VIVO con la cámara del dispositivo (no hay forma de subir un archivo) y va con la verificación de la cédula
// original. Queda fija al pasar a la fase 2; desde ahí solo el gestor la reemplaza, con motivo.

const fechaHora = (iso) => new Date(iso).toLocaleString('es-CO', { day: 'numeric', month: 'long', year: 'numeric', hour: 'numeric', minute: '2-digit' })

// Cámara: muestra lo que ve, toma el cuadro y lo recorta al centro en proporción vertical 3:4.
function Camara({ onTomada, onCancelar }) {
  const video = useRef(null)
  const flujo = useRef(null)
  const [error, setError] = useState(null)
  const [lista, setLista] = useState(false)

  const iniciar = useCallback(async () => {
    setError(null)
    setLista(false)
    try {
      if (!navigator.mediaDevices?.getUserMedia) throw new Error('sin-api')
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 960 } }, audio: false })
      flujo.current = stream
      if (video.current) {
        video.current.srcObject = stream
        await video.current.play()
        setLista(true)
      }
    } catch {
      setError('No se pudo usar la cámara. Permite el acceso a la cámara en el navegador (hace falta una conexión segura, https) e inténtalo de nuevo.')
    }
  }, [])

  useEffect(() => {
    iniciar()
    return () => flujo.current?.getTracks().forEach(t => t.stop())
  }, [iniciar])

  const tomar = () => {
    const v = video.current
    if (!v || !v.videoWidth) return
    const ancho = Math.min(v.videoWidth, Math.floor((v.videoHeight * 3) / 4))
    const alto = Math.floor((ancho * 4) / 3)
    const lienzo = document.createElement('canvas')
    lienzo.width = ancho
    lienzo.height = alto
    lienzo.getContext('2d').drawImage(v, (v.videoWidth - ancho) / 2, (v.videoHeight - alto) / 2, ancho, alto, 0, 0, ancho, alto)
    lienzo.toBlob(blob => { if (blob) onTomada(blob) }, 'image/jpeg', 0.9)
  }

  return (
    <div className="space-y-3">
      {error ? (
        <div className="rounded border border-red-200 bg-red-50 text-red-800 text-sm px-3 py-2" role="alert">{error}</div>
      ) : (
        <div className="mx-auto w-full max-w-xs aspect-[3/4] bg-black rounded overflow-hidden">
          <video ref={video} playsInline muted aria-label="Cámara" className="w-full h-full object-cover" />
        </div>
      )}
      <div className="flex flex-wrap justify-center gap-2">
        {error
          ? <button type="button" onClick={iniciar} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300">Intentar de nuevo</button>
          : <button type="button" disabled={!lista} onClick={tomar} className="min-h-11 px-5 text-sm font-semibold rounded-lg bg-primary-600 text-white disabled:opacity-50">Tomar foto</button>}
        {onCancelar && <button type="button" onClick={onCancelar} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300">Cancelar</button>}
      </div>
    </div>
  )
}

function Imagen({ idServicioSubproceso, version }) {
  const [url, setUrl] = useState(null)
  useEffect(() => {
    let vivo = true
    let objeto = null
    formulariosService.fotoImagen(idServicioSubproceso).then(blob => {
      if (!vivo) return
      objeto = URL.createObjectURL(blob)
      setUrl(objeto)
    }).catch(() => setUrl(null))
    return () => { vivo = false; if (objeto) URL.revokeObjectURL(objeto) }
  }, [idServicioSubproceso, version])
  return url ? <img src={url} alt="Foto del evaluado" className="w-40 aspect-[3/4] object-cover rounded border border-gray-300" /> : <div className="w-40 aspect-[3/4] bg-gray-100 rounded" />
}

// Vista del poligrafista: tomar, ver y repetir mientras no haya pasado a la fase 2.
export function FotoEvaluado({ idServicioSubproceso, estado, onEstado, onError }) {
  const [tomando, setTomando] = useState(false)
  const [pendiente, setPendiente] = useState(null)   // { blob, url } antes de guardar
  const [cedula, setCedula] = useState(false)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => () => { if (pendiente?.url) URL.revokeObjectURL(pendiente.url) }, [pendiente])

  const guardar = async () => {
    setGuardando(true)
    try {
      const nuevo = await formulariosService.fotoTomar(idServicioSubproceso, pendiente.blob, cedula)
      setPendiente(null)
      setTomando(false)
      onEstado(nuevo)
    } catch (err) {
      onError(err.response?.data?.mensaje ?? 'No se pudo guardar la foto.')
    } finally {
      setGuardando(false)
    }
  }

  const vigente = estado?.vigente
  return (
    <div className="space-y-4">
      <p>La foto es la constancia de que la prueba se le hizo a la persona indicada. Se toma en vivo con la cámara; no se puede subir desde un archivo.</p>

      {!pendiente && vigente && !tomando && (
        <div className="flex flex-wrap items-start gap-4">
          <Imagen idServicioSubproceso={idServicioSubproceso} version={vigente.id} />
          <div className="text-sm space-y-1">
            <p><b>Tomada por:</b> {vigente.tomadaPor}</p>
            <p><b>Fecha y hora:</b> {fechaHora(vigente.fecha)}</p>
            <p className="text-green-700">✓ Cédula original verificada</p>
            {estado.bloqueada && <p className="text-gray-700">La foto quedó fija. Si hay que cambiarla, lo hace el gestor.</p>}
            {estado.puedeTomar && (
              <button type="button" onClick={() => setTomando(true)} className="mt-2 min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 hover:bg-gray-50">Repetir foto</button>
            )}
          </div>
        </div>
      )}

      {pendiente && (
        <div className="space-y-3">
          <img src={pendiente.url} alt="Foto por confirmar" className="mx-auto w-48 aspect-[3/4] object-cover rounded border border-gray-300" />
          <div className="flex items-center gap-2">
            <input id="cedula-verificada" type="checkbox" checked={cedula} onChange={e => setCedula(e.target.checked)} className="h-4 w-4 rounded border-gray-400 text-primary-600" />
            <label htmlFor="cedula-verificada" className="text-[15px] text-gray-900">Verifiqué la cédula original y coincide con la persona evaluada</label>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={!cedula || guardando} onClick={guardar} className="min-h-11 px-5 text-sm font-semibold rounded-lg bg-primary-600 text-white disabled:opacity-50">
              {guardando ? 'Guardando…' : 'Guardar foto'}
            </button>
            <button type="button" onClick={() => { setPendiente(null); setTomando(true) }} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300">Tomar otra</button>
          </div>
          {!cedula && <p className="text-sm text-amber-800">Marca la verificación de la cédula para guardar la foto.</p>}
        </div>
      )}

      {tomando && !pendiente && (
        <Camara onCancelar={vigente ? () => setTomando(false) : null}
          onTomada={blob => { setPendiente({ blob, url: URL.createObjectURL(blob) }); setCedula(false) }} />
      )}

      {!vigente && !tomando && !pendiente && estado?.puedeTomar && (
        <button type="button" onClick={() => setTomando(true)} className="min-h-11 px-5 text-sm font-semibold rounded-lg bg-primary-600 text-white">Abrir la cámara</button>
      )}
      {!vigente && !estado?.puedeTomar && <p className="text-gray-700">Todavía no hay foto del evaluado.</p>}
    </div>
  )
}

// Vista del gestor y del administrador: ven la foto, su registro y el historial; pueden reemplazarla con motivo.
export function FotoEvaluadoGestor({ idServicioSubproceso }) {
  const [estado, setEstado] = useState(null)
  const [reemplazando, setReemplazando] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [pendiente, setPendiente] = useState(null)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => { formulariosService.fotoEstado(idServicioSubproceso).then(setEstado).catch(() => setEstado(null)) }, [idServicioSubproceso])
  useEffect(() => () => { if (pendiente?.url) URL.revokeObjectURL(pendiente.url) }, [pendiente])

  if (!estado) return null
  const vigente = estado.vigente

  const guardar = async () => {
    setGuardando(true)
    setError(null)
    try {
      setEstado(await formulariosService.fotoReemplazar(idServicioSubproceso, pendiente.blob, motivo.trim()))
      setPendiente(null)
      setReemplazando(false)
      setMotivo('')
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo reemplazar la foto.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4 mb-4" aria-label="Foto del evaluado">
      <p className="text-xs font-semibold text-gray-600 uppercase tracking-wider mb-2">Foto del evaluado</p>
      {error && <p className="mb-2 text-sm text-red-700" role="alert">{error}</p>}
      {!vigente ? <p className="text-sm text-gray-700">El poligrafista todavía no ha tomado la foto.</p> : (
        <div className="flex flex-wrap items-start gap-4">
          <Imagen idServicioSubproceso={idServicioSubproceso} version={vigente.id} />
          <div className="text-sm space-y-1">
            <p><b>Tomada por:</b> {vigente.tomadaPor} · {fechaHora(vigente.fecha)}</p>
            <p>{vigente.cedulaVerificada ? '✓ Cédula original verificada' : 'Cédula sin verificar'}</p>
            {vigente.motivoReemplazo && <p className="text-gray-700"><b>Reemplazo:</b> {vigente.motivoReemplazo}</p>}
            {estado.historial.length > 0 && (
              <details className="mt-1">
                <summary className="cursor-pointer text-primary-700">Historial ({estado.historial.length})</summary>
                <ul className="mt-1 space-y-0.5 text-gray-700">
                  {estado.historial.map(h => <li key={h.id}>{fechaHora(h.fecha)} · {h.tomadaPor}{h.motivoReemplazo ? ` — ${h.motivoReemplazo}` : ''}</li>)}
                </ul>
              </details>
            )}
          </div>
        </div>
      )}
      {estado.puedeReemplazar && !reemplazando && (
        <button type="button" onClick={() => setReemplazando(true)} className="mt-3 min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 hover:bg-gray-50">
          {vigente ? 'Reemplazar foto' : 'Tomar foto'}
        </button>
      )}
      {reemplazando && (
        <div className="mt-3 space-y-3">
          <div>
            <label htmlFor="motivo-foto" className="block text-sm font-bold text-gray-900">Motivo del reemplazo</label>
            <input id="motivo-foto" value={motivo} maxLength={500} onChange={e => setMotivo(e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
          </div>
          {pendiente ? (
            <div className="space-y-2">
              <img src={pendiente.url} alt="Foto nueva" className="w-40 aspect-[3/4] object-cover rounded border border-gray-300" />
              <div className="flex flex-wrap gap-2">
                <button type="button" disabled={!motivo.trim() || guardando} onClick={guardar} className="min-h-11 px-5 text-sm font-semibold rounded-lg bg-primary-600 text-white disabled:opacity-50">
                  {guardando ? 'Guardando…' : 'Guardar reemplazo'}
                </button>
                <button type="button" onClick={() => setPendiente(null)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300">Tomar otra</button>
              </div>
            </div>
          ) : (
            <Camara onCancelar={() => setReemplazando(false)} onTomada={blob => setPendiente({ blob, url: URL.createObjectURL(blob) })} />
          )}
        </div>
      )}
    </section>
  )
}

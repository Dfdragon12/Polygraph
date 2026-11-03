import { useEffect, useRef, useState } from 'react'
import formulariosService from '../../services/formulariosService'

// PF-06 · Foto del evaluado: se toma con la cámara del dispositivo o se carga una imagen del dispositivo (ambas se reducen a 600 × 800 px) y va con la verificación de la cédula
// original. Queda fija al pasar a la fase 2; desde ahí solo el gestor la reemplaza, con motivo.

const fechaHora = (iso) => new Date(iso).toLocaleString('es-CO', { day: 'numeric', month: 'long', year: 'numeric', hour: 'numeric', minute: '2-digit' })

const ANCHO_FOTO = 600
const ALTO_FOTO = 800
const MAX_BYTES_FOTO = 300 * 1024

// Recorta al centro en proporción vertical 3:4, reduce a 600 × 800 px y comprime en JPG hasta pesar menos de 300 KB.
function reducirFoto(fuente, anchoFuente, altoFuente, alista) {
  const ancho = Math.min(anchoFuente, Math.floor((altoFuente * 3) / 4))
  const alto = Math.floor((ancho * 4) / 3)
  const lienzo = document.createElement('canvas')
  lienzo.width = Math.min(ancho, ANCHO_FOTO)
  lienzo.height = Math.min(alto, ALTO_FOTO)
  lienzo.getContext('2d').drawImage(fuente, (anchoFuente - ancho) / 2, (altoFuente - alto) / 2, ancho, alto, 0, 0, lienzo.width, lienzo.height)
  const codificar = (calidad) => lienzo.toBlob(blob => {
    if (!blob) return
    if (blob.size > MAX_BYTES_FOTO && calidad > 0.5) codificar(calidad - 0.1)
    else alista(blob)
  }, 'image/jpeg', calidad)
  codificar(0.85)
}

// Cámara: muestra lo que ve, toma el cuadro y lo recorta al centro en proporción vertical 3:4. Si no abre, avisa con onFallo.
function Camara({ onTomada, onFallo, onCancelar }) {
  const video = useRef(null)
  const flujo = useRef(null)
  const [lista, setLista] = useState(false)

  useEffect(() => {
    let vivo = true
    ;(async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error('sin-api')
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 960 } }, audio: false })
        if (!vivo) { stream.getTracks().forEach(t => t.stop()); return }
        flujo.current = stream
        if (video.current) {
          video.current.srcObject = stream
          await video.current.play()
          setLista(true)
        }
      } catch {
        if (vivo) onFallo()
      }
    })()
    return () => { vivo = false; flujo.current?.getTracks().forEach(t => t.stop()) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const tomar = () => {
    const v = video.current
    if (!v || !v.videoWidth) return
    reducirFoto(v, v.videoWidth, v.videoHeight, blob => onTomada(blob, 'CAMARA'))
  }

  return (
    <div className="space-y-3">
      <div className="mx-auto w-full max-w-xs aspect-[3/4] bg-black rounded overflow-hidden">
        <video ref={video} playsInline muted aria-label="Cámara" className="w-full h-full object-cover" />
      </div>
      <div className="flex flex-wrap justify-center gap-2">
        <button type="button" disabled={!lista} onClick={tomar} className="min-h-11 px-5 text-sm font-semibold rounded-lg bg-primary-600 text-white disabled:opacity-50">Tomar foto</button>
        <button type="button" onClick={onCancelar} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300">Cancelar</button>
      </div>
    </div>
  )
}

// FA-02 · Dos formas de cargar la foto, con botones del mismo tamaño: la cámara o un archivo del dispositivo (en celular y tableta el selector
// también ofrece la cámara). Las dos pasan por la misma reducción. Si la cámara no abre, se resalta la opción del archivo: nunca se queda sin salida.
function ElegirFoto({ onTomada, onCancelar }) {
  const [modo, setModo] = useState('elegir')   // 'elegir' | 'camara'
  const [camaraFallo, setCamaraFallo] = useState(false)
  const [aviso, setAviso] = useState(null)
  const archivoRef = useRef(null)

  const alElegirArchivo = async (e) => {
    const archivo = e.target.files?.[0]
    e.target.value = ''
    if (!archivo) return
    if (!['image/jpeg', 'image/png'].includes(archivo.type)) { setAviso('El archivo debe ser una foto JPG o PNG.'); return }
    try {
      const imagen = await createImageBitmap(archivo)
      setAviso(null)
      reducirFoto(imagen, imagen.width, imagen.height, blob => onTomada(blob, 'ARCHIVO'))
    } catch {
      setAviso('El archivo debe ser una foto JPG o PNG.')
    }
  }

  if (modo === 'camara') {
    return <Camara onTomada={onTomada} onCancelar={() => setModo('elegir')} onFallo={() => { setCamaraFallo(true); setModo('elegir') }} />
  }
  const base = 'flex-1 min-h-16 px-4 text-base font-semibold rounded-lg border-2'
  return (
    <div className="space-y-3">
      {camaraFallo && <p role="alert" className="rounded border border-amber-300 bg-amber-50 text-amber-900 text-sm px-3 py-2">No se pudo abrir la cámara. Puedes subir la foto desde un archivo.</p>}
      {aviso && <p role="alert" className="text-sm text-red-700">{aviso}</p>}
      <div className="flex flex-col sm:flex-row gap-3">
        <button type="button" onClick={() => { setCamaraFallo(false); setAviso(null); setModo('camara') }}
          className={`${base} ${camaraFallo ? 'border-gray-300 bg-white text-gray-900' : 'border-primary-600 bg-primary-600 text-white'}`}>Tomar foto con la cámara</button>
        <button type="button" onClick={() => archivoRef.current?.click()}
          className={`${base} ${camaraFallo ? 'border-primary-600 bg-primary-600 text-white ring-4 ring-primary-200' : 'border-primary-600 bg-white text-primary-700'}`}>Subir foto desde un archivo</button>
      </div>
      <input ref={archivoRef} type="file" accept="image/jpeg,image/png" onChange={alElegirArchivo} className="hidden" aria-label="Subir foto desde un archivo" />
      {onCancelar && <button type="button" onClick={onCancelar} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300">Cancelar</button>}
    </div>
  )
}

// Origen y fecha de la foto: solo para la pantalla y el registro interno; no se imprime en el informe.
const fechaCorta = (iso) => new Date(iso).toLocaleString('es-CO', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })
const origenTexto = (origen) => (origen === 'ARCHIVO' ? 'Foto subida desde un archivo' : 'Foto tomada con la cámara')

// Dice qué pasó y qué hacer; nunca el «Error interno del servidor» de un fallo que no es del usuario.
function mensajeDeFoto(err, accion) {
  const estado = err.response?.status
  const mensaje = err.response?.data?.mensaje
  if (estado === 413) return 'La foto es demasiado pesada. Elige una de menos de 5 MB.'
  if (estado === 400 && mensaje) return mensaje   // «El archivo debe ser una foto JPG o PNG.» y similares: el servidor ya dice qué pasó
  if (estado && estado < 500 && estado !== 400 && mensaje) return mensaje
  return `No se pudo ${accion} la foto. Revisa tu conexión e inténtalo de nuevo.`
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
  const [errorGuardado, setErrorGuardado] = useState(null)
  const [guardada, setGuardada] = useState(false)

  useEffect(() => () => { if (pendiente?.url) URL.revokeObjectURL(pendiente.url) }, [pendiente])

  const guardar = async () => {
    if (guardando) return
    setGuardando(true)
    setErrorGuardado(null)
    try {
      const nuevo = await formulariosService.fotoTomar(idServicioSubproceso, pendiente.blob, cedula, pendiente.origen)
      setPendiente(null)
      setTomando(false)
      onEstado(nuevo)
      setGuardada(true)
    } catch (err) {
      setErrorGuardado(mensajeDeFoto(err, 'guardar'))
    } finally {
      setGuardando(false)
    }
  }

  const vigente = estado?.vigente
  return (
    <div className="space-y-4">
      <p>La foto es la constancia de que la prueba se le hizo a la persona indicada. Se toma con la cámara o se carga desde el dispositivo.</p>

      {!pendiente && vigente && !tomando && (
        <div className="flex flex-wrap items-start gap-4">
          <Imagen idServicioSubproceso={idServicioSubproceso} version={vigente.id} />
          <div className="text-sm space-y-1">
            <p><b>Tomada por:</b> {vigente.tomadaPor}</p>
            <p className="text-xs text-gray-700">{origenTexto(vigente.origen)} · {fechaCorta(vigente.fecha)}</p>
            {guardada && <p role="status" className="text-green-700 font-semibold">✓ Foto guardada</p>}
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
            <button type="button" onClick={() => { setPendiente(null); setTomando(true) }} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300">Cambiar foto</button>
          </div>
          {!cedula && <p className="text-sm text-amber-800">Marca la verificación de la cédula para guardar la foto.</p>}
          {errorGuardado && (
            <div role="alert" className="rounded border border-red-200 bg-red-50 text-red-800 text-sm px-3 py-2 flex flex-wrap items-center gap-3">
              <span>{errorGuardado}</span>
              <button type="button" onClick={guardar} disabled={guardando || !cedula} className="min-h-11 px-4 text-sm font-semibold rounded-lg border border-red-300 bg-white disabled:opacity-50">Reintentar</button>
            </div>
          )}
        </div>
      )}

      {!pendiente && (tomando || (!vigente && estado?.puedeTomar)) && (
        <ElegirFoto onCancelar={vigente ? () => setTomando(false) : null}
          onTomada={(blob, origen) => { setPendiente({ blob, origen, url: URL.createObjectURL(blob) }); setCedula(false); setErrorGuardado(null); setGuardada(false) }} />
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
      setEstado(await formulariosService.fotoReemplazar(idServicioSubproceso, pendiente.blob, motivo.trim(), pendiente.origen))
      setPendiente(null)
      setReemplazando(false)
      setMotivo('')
    } catch (err) {
      setError(mensajeDeFoto(err, 'reemplazar'))
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
            <p><b>Origen:</b> {origenTexto(vigente.origen)}</p>
            <p>{vigente.cedulaVerificada ? '✓ Cédula original verificada' : 'Cédula sin verificar'}</p>
            {vigente.motivoReemplazo && <p className="text-gray-700"><b>Reemplazo:</b> {vigente.motivoReemplazo}</p>}
            {estado.historial.length > 0 && (
              <details className="mt-1">
                <summary className="cursor-pointer text-primary-700">Historial ({estado.historial.length})</summary>
                <ul className="mt-1 space-y-0.5 text-gray-700">
                  {estado.historial.map(h => <li key={h.id}>{fechaHora(h.fecha)} · {h.tomadaPor} ({h.origen === 'ARCHIVO' ? 'archivo' : 'cámara'}){h.motivoReemplazo ? ` — ${h.motivoReemplazo}` : ''}</li>)}
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
                <button type="button" onClick={() => setPendiente(null)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300">Cambiar foto</button>
              </div>
            </div>
          ) : (
            <ElegirFoto onCancelar={() => setReemplazando(false)} onTomada={(blob, origen) => setPendiente({ blob, origen, url: URL.createObjectURL(blob) })} />
          )}
        </div>
      )}
    </section>
  )
}

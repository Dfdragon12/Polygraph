import { useCallback, useEffect, useRef, useState } from 'react'
import formulariosService from '../../services/formulariosService'
import { reducirImagen, MENSAJES_FOTO } from '../../utils/reducirImagen'

// VD-07 · Fotos de la visita: un espacio con nombre por cada foto (en vez de «Nombre de la fila» y «Agregar fila»), cámara o archivo, reducción en el
// navegador y subida con progreso. Se guarda en las mismas columnas de siempre: etiqueta de la fila = nombre del espacio, ft_imagen = ruta (arreglo JSON),
// ft_descripcion = descripción. Un visitador no puede borrar filas ya guardadas, así que «Quitar» vacía la foto y su descripción.

const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const btnCls = 'min-h-11 px-4 text-sm font-medium rounded-lg border'
const kb = (b) => (b >= 1024 * 1024 ? `${(b / 1024 / 1024).toFixed(1).replace('.', ',')} MB` : `${Math.max(1, Math.round(b / 1024))} KB`)

export const rutaDeFoto = (valor) => {
  if (!valor) return null
  try { const a = JSON.parse(valor); return Array.isArray(a) ? (a[0] ?? null) : String(a) } catch { return String(valor) }
}

// Miniatura: baja la imagen con la sesión (no es una URL pública) y dice cuánto pesa lo guardado.
function Miniatura({ idServicioSubproceso, ruta, alTamano }) {
  const [url, setUrl] = useState(null)
  useEffect(() => {
    let vivo = true
    let objeto = null
    setUrl(null)
    formulariosService.descargarArchivo(idServicioSubproceso, ruta).then(blob => {
      if (!vivo) return
      objeto = URL.createObjectURL(blob)
      setUrl(objeto)
      alTamano?.(blob.size)
    }).catch(() => {})
    return () => { vivo = false; if (objeto) URL.revokeObjectURL(objeto) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idServicioSubproceso, ruta])
  return url
    ? <img src={url} alt="Miniatura de la foto guardada" className="w-24 h-24 object-cover rounded border border-gray-300 flex-shrink-0" />
    : <div className="w-24 h-24 rounded border border-gray-200 bg-gray-100 flex-shrink-0" aria-hidden="true" />
}

// Vista previa de la cámara en computador. En celular y tableta se usa el selector del sistema con la cámara trasera.
function CamaraPrevia({ onTomada, onFallo, onCancelar }) {
  const video = useRef(null)
  const flujo = useRef(null)
  const [lista, setLista] = useState(false)
  useEffect(() => {
    let vivo = true
    ;(async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error('sin-api')
        const s = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment', width: { ideal: 1920 } }, audio: false })
        if (!vivo) { s.getTracks().forEach(t => t.stop()); return }
        flujo.current = s
        video.current.srcObject = s
        await video.current.play()
        setLista(true)
      } catch { if (vivo) onFallo() }
    })()
    return () => { vivo = false; flujo.current?.getTracks().forEach(t => t.stop()) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const tomar = () => {
    const v = video.current
    if (!v?.videoWidth) return
    const c = document.createElement('canvas')
    c.width = v.videoWidth
    c.height = v.videoHeight
    c.getContext('2d').drawImage(v, 0, 0)
    c.toBlob(b => b && onTomada(b), 'image/jpeg', 0.92)
  }
  return (
    <div className="space-y-2">
      <video ref={video} playsInline muted aria-label="Cámara" className="w-full max-w-sm bg-black rounded" />
      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={!lista} onClick={tomar} className={`${btnCls} border-primary-600 bg-primary-600 text-white disabled:opacity-50`}>Capturar</button>
        <button type="button" onClick={onCancelar} className={`${btnCls} border-gray-300 text-gray-800`}>Cancelar</button>
      </div>
    </div>
  )
}

const esTactil = () => typeof window !== 'undefined' && window.matchMedia('(pointer: coarse)').matches

// Un espacio: vacío (dos botones) o con foto (miniatura, descripción, datos y enlaces).
function Espacio({ nombre, descripcionBase, fila, columnas, idServicioSubproceso, soloLectura, onGuardar, onVaciar, onDescripcion, onError, esExtra, onDescartarExtra }) {
  const [modo, setModo] = useState('inicio')   // 'inicio' | 'camara'
  const [camaraFallo, setCamaraFallo] = useState(false)
  const [aviso, setAviso] = useState(null)
  const [progreso, setProgreso] = useState(null)   // null = quieto; 0..1 = subiendo
  const [enEspera, setEnEspera] = useState(null)   // { blob, origen } cuando se cayó la conexión
  const [datos, setDatos] = useState(null)         // { origen } de esta sesión
  const [tam, setTam] = useState(null)
  const [cambiando, setCambiando] = useState(false)
  const archivoRef = useRef(null)
  const camaraRef = useRef(null)
  const ruta = fila ? rutaDeFoto(fila.valores[columnas.imagen]) : null
  const descripcion = fila ? (fila.valores[columnas.descripcion] ?? '') : ''
  const tieneFoto = !!ruta

  const subir = useCallback(async (blob, origen) => {
    setAviso(null)
    setEnEspera(null)
    setProgreso(0)
    try {
      const nueva = await formulariosService.subirArchivo(idServicioSubproceso, blob, 'foto.jpg', setProgreso)
      setTam(blob.size)
      setDatos({ origen })
      setCambiando(false)
      onGuardar(nueva)
    } catch {
      setEnEspera({ blob, origen })
      setAviso(MENSAJES_FOTO.subida)
    } finally {
      setProgreso(null)
    }
  }, [idServicioSubproceso, onGuardar])

  const procesar = useCallback(async (fuente, origen) => {
    setAviso(null)
    setProgreso(0)
    try {
      const { blob } = await reducirImagen(fuente)
      await subir(blob, origen)
    } catch (e) {
      setProgreso(null)
      setAviso(e.mensaje ?? MENSAJES_FOTO.tipo)
    }
  }, [subir])

  // Si se cayó la conexión, la foto queda en espera y se reintenta sola al volver la señal.
  useEffect(() => {
    if (!enEspera) return undefined
    const volvio = () => subir(enEspera.blob, enEspera.origen)
    window.addEventListener('online', volvio)
    return () => window.removeEventListener('online', volvio)
  }, [enEspera, subir])

  const alElegirArchivo = (e, origen) => {
    const f = e.target.files?.[0]
    e.target.value = ''
    if (f) procesar(f, origen)
  }

  const botones = (
    <div className="space-y-2">
      {camaraFallo && <p role="alert" className="rounded border border-amber-300 bg-amber-50 text-amber-900 text-sm px-3 py-2">No se pudo abrir la cámara. Puedes subir la foto desde un archivo.</p>}
      {modo === 'camara' ? (
        <CamaraPrevia onTomada={b => { setModo('inicio'); procesar(b, 'CAMARA') }} onFallo={() => { setCamaraFallo(true); setModo('inicio') }} onCancelar={() => setModo('inicio')} />
      ) : (
        <div className="flex flex-col sm:flex-row gap-2">
          <button type="button" disabled={soloLectura || progreso !== null}
            onClick={() => { setCamaraFallo(false); if (esTactil()) camaraRef.current?.click(); else setModo('camara') }}
            className={`${btnCls} flex-1 ${camaraFallo ? 'border-gray-300 bg-white text-gray-900' : 'border-primary-600 bg-primary-600 text-white'} disabled:opacity-50`}>Tomar foto</button>
          <button type="button" disabled={soloLectura || progreso !== null} onClick={() => archivoRef.current?.click()}
            className={`${btnCls} flex-1 ${camaraFallo ? 'border-primary-600 bg-primary-600 text-white ring-4 ring-primary-200' : 'border-primary-600 bg-white text-primary-700'} disabled:opacity-50`}>Subir desde un archivo</button>
        </div>
      )}
      <input ref={camaraRef} type="file" accept="image/*" capture="environment" className="hidden" aria-label={`${nombre}: tomar foto`} onChange={e => alElegirArchivo(e, 'CAMARA')} />
      <input ref={archivoRef} type="file" accept="image/jpeg,image/png,image/heic,image/heif,.heic,.heif" className="hidden" aria-label={`${nombre}: subir desde un archivo`} onChange={e => alElegirArchivo(e, 'ARCHIVO')} />
    </div>
  )

  return (
    <li className="px-3 py-3 space-y-2">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold text-gray-900">{nombre}</p>
        {esExtra && !tieneFoto && !soloLectura && (
          <button type="button" onClick={onDescartarExtra} className="text-xs text-gray-700 underline min-h-11">Quitar espacio</button>
        )}
      </div>
      {progreso !== null && (
        <div role="status" aria-live="polite" className="text-sm text-gray-800">
          Subiendo… {progreso > 0 ? `${Math.round(progreso * 100)} %` : ''}
          <div className="h-2 bg-gray-200 rounded mt-1"><div className="h-2 bg-primary-600 rounded" style={{ width: `${Math.round(progreso * 100)}%` }} /></div>
        </div>
      )}
      {aviso && (
        <div role="alert" className="text-sm text-red-700 flex flex-wrap items-center gap-2">
          <span>{aviso}</span>
          {enEspera && <button type="button" onClick={() => subir(enEspera.blob, enEspera.origen)} className={`${btnCls} border-red-300 text-red-800`}>Reintentar</button>}
        </div>
      )}
      {tieneFoto && !cambiando ? (
        <div className="flex gap-3">
          <Miniatura idServicioSubproceso={idServicioSubproceso} ruta={ruta} alTamano={setTam} />
          <div className="min-w-0 flex-1 space-y-1">
            <label htmlFor={`desc-${nombre}`} className="block text-xs font-semibold text-gray-700">Descripción *</label>
            <input id={`desc-${nombre}`} className={inputCls} value={descripcion} disabled={soloLectura} onChange={e => onDescripcion(e.target.value)} maxLength={300} />
            <p className="text-xs text-green-800">
              Guardada{tam ? ` · ${kb(tam)}` : ''}{datos ? ` · ${datos.origen === 'ARCHIVO' ? 'subida desde un archivo' : 'tomada con la cámara'}` : ''}
            </p>
            {!soloLectura && (
              <div className="flex gap-4">
                <button type="button" onClick={() => setCambiando(true)} className="text-sm text-primary-700 underline min-h-11">Cambiar</button>
                <button type="button" onClick={() => { onVaciar(); setDatos(null); setTam(null) }} className="text-sm text-red-700 underline min-h-11">Quitar</button>
              </div>
            )}
          </div>
        </div>
      ) : (
        !soloLectura && progreso === null && (
          <div className="space-y-2">
            {botones}
            {cambiando && <button type="button" onClick={() => setCambiando(false)} className="text-sm text-gray-700 underline min-h-11">Cancelar</button>}
          </div>
        )
      )}
      <span className="sr-only">{descripcionBase}</span>
    </li>
  )
}

export default function FotosVisita({ seccion, config, idServicioSubproceso, soloLectura, onCambiarFilas, onError }) {
  const colImg = seccion.columnas.find(c => c.clave === 'ft_imagen')?.idColumna
  const colDesc = seccion.columnas.find(c => c.clave === 'ft_descripcion')?.idColumna
  const columnas = { imagen: colImg, descripcion: colDesc }
  const nombresFijos = config.grupos.flatMap(g => g.espacios.map(e => e.nombre))
  const [extrasNuevos, setExtrasNuevos] = useState(0)

  const filaDe = (nombre) => seccion.filas.find(f => f.etiqueta === nombre)
  const extras = seccion.filas.filter(f => !nombresFijos.includes(f.etiqueta))
  const conFoto = seccion.filas.filter(f => rutaDeFoto(f.valores[colImg])).length

  // Pone/actualiza la fila de un espacio (se crea al guardar su primera foto) y deja las filas en el orden de la lista.
  const poner = (nombre, cambios) => onCambiarFilas(seccion.idSeccion, (filas) => {
    const existe = filas.some(f => f.etiqueta === nombre)
    const nuevas = existe
      ? filas.map(f => (f.etiqueta === nombre ? { ...f, valores: { ...f.valores, ...cambios } } : f))
      : [...filas, { localId: `ft-${Date.now()}-${filas.length}`, idFila: null, etiqueta: nombre, valores: { ...cambios }, justificaciones: {} }]
    const pos = (f) => { const i = nombresFijos.indexOf(f.etiqueta); return i < 0 ? 1000 + filas.indexOf(f) : i }
    return [...nuevas].sort((a, b) => pos(a) - pos(b))
  })

  const guardarFoto = (nombre, descripcionBase) => (ruta) => {
    const actual = filaDe(nombre)
    poner(nombre, { [colImg]: JSON.stringify([ruta]), [colDesc]: actual?.valores[colDesc] || descripcionBase })
  }
  const nombreExtra = (n) => `${config.nombreExtra} ${n}`

  return (
    <section aria-labelledby={`fotos-${seccion.clave}`} data-clave={`seccion-${seccion.clave}`} className="space-y-3 rounded">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h4 id={`fotos-${seccion.clave}`} className="text-sm font-bold text-gray-900">{config.titulo}</h4>
        <p className={`text-xs font-medium ${conFoto >= config.minimo ? 'text-green-700' : 'text-amber-800'}`} aria-live="polite">{conFoto} de {config.minimo} mínimas</p>
      </div>
      <p className="text-xs text-gray-800">No pueden aparecer menores de edad.</p>
      {config.grupos.map(g => (
        <div key={g.titulo ?? 'unico'}>
          {g.titulo && <h5 className="text-xs font-bold uppercase tracking-wide text-gray-700 mb-1">{g.titulo}</h5>}
          <ul className="divide-y divide-gray-200 border border-gray-200 rounded-lg">
            {g.espacios.map(e => (
              <Espacio key={e.nombre} nombre={e.nombre} descripcionBase={e.descripcion} fila={filaDe(e.nombre)} columnas={columnas}
                idServicioSubproceso={idServicioSubproceso} soloLectura={soloLectura} onError={onError}
                onGuardar={guardarFoto(e.nombre, e.descripcion)}
                onVaciar={() => poner(e.nombre, { [colImg]: '', [colDesc]: '' })}
                onDescripcion={(v) => poner(e.nombre, { [colDesc]: v })} />
            ))}
          </ul>
        </div>
      ))}
      {(extras.length > 0 || extrasNuevos > 0) && (
        <ul className="divide-y divide-gray-200 border border-gray-200 rounded-lg">
          {extras.map((f, i) => {
            const nombre = f.etiqueta || nombreExtra(i + 1)
            return (
              <Espacio key={f.localId} nombre={nombre} descripcionBase={config.descripcionExtra} fila={f} columnas={columnas}
                idServicioSubproceso={idServicioSubproceso} soloLectura={soloLectura} onError={onError} esExtra
                onGuardar={ruta => onCambiarFilas(seccion.idSeccion, filas => filas.map(x => (x.localId === f.localId
                  ? { ...x, etiqueta: x.etiqueta || nombreExtra(i + 1), valores: { ...x.valores, [colImg]: JSON.stringify([ruta]), [colDesc]: x.valores[colDesc] || config.descripcionExtra } } : x)))}
                onVaciar={() => onCambiarFilas(seccion.idSeccion, filas => filas.map(x => (x.localId === f.localId ? { ...x, valores: { ...x.valores, [colImg]: '', [colDesc]: '' } } : x)))}
                onDescripcion={v => onCambiarFilas(seccion.idSeccion, filas => filas.map(x => (x.localId === f.localId ? { ...x, valores: { ...x.valores, [colDesc]: v } } : x)))} />
            )
          })}
          {Array.from({ length: extrasNuevos }, (_, k) => {
            const n = extras.length + k + 1
            const nombre = nombreExtra(n)
            return (
              <Espacio key={`nuevo-${k}`} nombre={nombre} descripcionBase={config.descripcionExtra} fila={filaDe(nombre)} columnas={columnas}
                idServicioSubproceso={idServicioSubproceso} soloLectura={soloLectura} onError={onError} esExtra
                onGuardar={ruta => { poner(nombre, { [colImg]: JSON.stringify([ruta]), [colDesc]: config.descripcionExtra }); setExtrasNuevos(x => Math.max(0, x - 1)) }}
                onVaciar={() => {}} onDescripcion={() => {}} onDescartarExtra={() => setExtrasNuevos(x => Math.max(0, x - 1))} />
            )
          })}
        </ul>
      )}
      {!soloLectura && config.agregar && (
        <button type="button" onClick={() => setExtrasNuevos(x => x + 1)} className={`${btnCls} border-primary-300 text-primary-700 hover:bg-primary-50`}>{config.agregar}</button>
      )}
    </section>
  )
}

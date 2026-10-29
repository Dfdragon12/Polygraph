import api from './api'

// Trabajos en segundo plano (cola persistente del backend, módulo trabajos). Un POST pesado
// (carga masiva, PDF de informe) responde 202 con { id, estado, ... } y aquí se sigue ese id
// hasta que termina — ver esperarTrabajo.

const trabajoService = {
  consultar: (id, { signal } = {}) =>
    api.get(`/trabajos/${id}`, { signal }).then(r => r.data),

  // Archivo resultado (ej. el PDF). Llega en streaming desde disco en el backend.
  descargarResultado: (id) =>
    api.get(`/trabajos/${id}/resultado`, { responseType: 'blob' }),

  // ── Panel admin ──
  listar: ({ estado, tipo, pagina = 0, tamano = 10 } = {}) =>
    api.get('/admin/trabajos', { params: { estado: estado || undefined, tipo: tipo || undefined, pagina, tamano } }).then(r => r.data),

  metricas: () => api.get('/admin/trabajos/metricas').then(r => r.data),

  reintentar: (id) => api.post(`/admin/trabajos/${id}/reintentar`).then(r => r.data),

  // Borra los completados (menos los de los últimos minutos). → { borrados, minutosProtegidos }
  borrarCompletados: () => api.delete('/admin/trabajos/completados').then(r => r.data),
}

const TERMINADOS = new Set(['COMPLETADO', 'FALLIDO'])

function error(mensaje, extra = {}) {
  return Object.assign(new Error(mensaje), extra)
}

// Espera ms, pero se corta de inmediato si se aborta.
function dormir(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(error('Cancelado', { cancelado: true }))
    const t = setTimeout(() => { signal?.removeEventListener('abort', alAbortar); resolve() }, ms)
    function alAbortar() { clearTimeout(t); reject(error('Cancelado', { cancelado: true })) }
    signal?.addEventListener('abort', alAbortar, { once: true })
  })
}

// Con la pestaña oculta no se consulta nada: se espera a que el usuario vuelva.
function esperarPestanaVisible(signal) {
  if (typeof document === 'undefined' || !document.hidden) return Promise.resolve()
  return new Promise((resolve, reject) => {
    function alCambiar() {
      if (!document.hidden) { limpiar(); resolve() }
    }
    function alAbortar() { limpiar(); reject(error('Cancelado', { cancelado: true })) }
    function limpiar() {
      document.removeEventListener('visibilitychange', alCambiar)
      signal?.removeEventListener('abort', alAbortar)
    }
    document.addEventListener('visibilitychange', alCambiar)
    signal?.addEventListener('abort', alAbortar, { once: true })
  })
}

/**
 * Short polling adaptativo de un trabajo hasta que termina.
 *
 * - Intervalo creciente: empieza en `intervaloInicial` (1 s) y crece ×1.5 hasta `intervaloMaximo`
 *   (8 s). Un PDF que tarda 3 s se ve casi al instante; una carga de 5 min hace ~40 consultas,
 *   no 300. Mientras hay avance (progreso o posición en cola cambia) el intervalo se reinicia
 *   un poco para que la barra se vea viva.
 * - Pestaña oculta = pausa total (cero tráfico); al volver consulta enseguida.
 * - Cada consulta es una petición corta e independiente: no hay conexiones colgadas como con
 *   long polling, ni problemas de proxies/ngrok cortando streams como con SSE, y el JWT viaja
 *   en el header de siempre.
 * - Errores de red o 5xx se toleran (se reintenta con el mismo backoff) hasta `maxErroresSeguidos`.
 * - `signal` (AbortController) corta todo al desmontar el componente; `tiempoMaximo` evita
 *   esperar para siempre.
 *
 * Resuelve con el trabajo COMPLETADO; rechaza con error.trabajo si quedó FALLIDO.
 */
export async function esperarTrabajo(trabajoInicial, {
  signal,
  onActualizacion,
  intervaloInicial = 1000,
  intervaloMaximo = 8000,
  tiempoMaximo = 15 * 60 * 1000,
  maxErroresSeguidos = 5,
} = {}) {
  let trabajo = trabajoInicial
  let intervalo = intervaloInicial
  let errores = 0
  const limite = Date.now() + tiempoMaximo
  onActualizacion?.(trabajo)

  while (!TERMINADOS.has(trabajo.estado)) {
    if (Date.now() > limite) {
      throw error('El proceso está tardando más de lo normal. Puedes revisar más tarde.', { tiempoAgotado: true })
    }
    await dormir(intervalo, signal)
    await esperarPestanaVisible(signal)
    try {
      const anterior = trabajo
      trabajo = await trabajoService.consultar(trabajo.id, { signal })
      errores = 0
      onActualizacion?.(trabajo)
      const huboAvance = trabajo.progreso !== anterior.progreso || trabajo.posicionEnCola !== anterior.posicionEnCola
      intervalo = huboAvance
        ? Math.max(intervaloInicial, Math.round(intervalo / 1.5))
        : Math.min(intervaloMaximo, Math.round(intervalo * 1.5))
    } catch (e) {
      if (signal?.aborted || e.code === 'ERR_CANCELED') throw error('Cancelado', { cancelado: true })
      const status = e.response?.status
      // 4xx (ej. 404: no es tuyo o ya se limpió) no se arregla reintentando.
      if (status && status < 500) throw e
      if (++errores >= maxErroresSeguidos) throw e
      intervalo = Math.min(intervaloMaximo, Math.round(intervalo * 1.5))
    }
  }

  if (trabajo.estado === 'FALLIDO') {
    throw error(trabajo.mensaje || 'No se pudo completar el proceso.', { trabajo })
  }
  return trabajo
}

/**
 * Mensaje para el usuario a partir de cualquier error de un trabajo o de su descarga. Solo cae en
 * `porDefecto` si de verdad no se sabe nada — antes todo error terminaba en el genérico y no había
 * forma de saber qué pasó.
 */
export function mensajeDeError(e, porDefecto) {
  // El servidor explicó el motivo (403/404/409, archivo de plantilla faltante, etc.).
  if (e?.response?.data?.mensaje) return e.response.data.mensaje
  // El trabajo terminó FALLIDO: su mensaje ya viene pensado para el usuario.
  if (e?.trabajo?.mensaje) return e.trabajo.mensaje
  // esperarTrabajo agotó su tiempo: el trabajo sigue en el servidor.
  if (e?.tiempoAgotado) return e.message
  // Sin respuesta del servidor: red caída, servidor reiniciándose, túnel cortado.
  if (e?.code === 'ERR_NETWORK' || (e?.request && !e?.response)) {
    return 'Se perdió la conexión con el servidor. Revisa tu internet e intenta de nuevo en un momento.'
  }
  if (e?.response?.status === 502 || e?.response?.status === 503 || e?.response?.status === 504) {
    return 'El servidor no está disponible en este momento. Intenta de nuevo en un minuto.'
  }
  return porDefecto
}

// Baja un blob de axios como archivo, usando el nombre del Content-Disposition si viene.
export function guardarBlob(respuesta, nombrePorDefecto) {
  const disposicion = respuesta.headers?.['content-disposition'] ?? ''
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposicion)?.[1]
  const simple = /filename="?([^";]+)"?/i.exec(disposicion)?.[1]
  const nombre = utf8 ? decodeURIComponent(utf8) : (simple ?? nombrePorDefecto)
  const url = window.URL.createObjectURL(respuesta.data)
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(url)
}

export default trabajoService

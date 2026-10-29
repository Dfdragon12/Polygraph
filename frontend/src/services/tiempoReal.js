// Avisos en tiempo real (Server-Sent Events). Una sola conexión por pestaña y por canal, que
// comparten todas las pantallas abiertas: cuando el backend avisa "cambió el servicio X", cada
// pantalla que muestre algo de X recarga sus datos (ver hooks/useCambios).
//
// Se usa fetch (no EventSource) para poder mandar el token en el encabezado Authorization, igual
// que el resto de la API. Si la conexión se cae, reintenta con espera creciente; mientras está
// caída, useCambios consulta cada tanto como respaldo.

function crearCanal(obtenerUrl, obtenerEncabezados) {
  const oyentes = new Set()           // fn(idsServicio: number[], temas: string[]) — los temas solo le llegan al administrador
  const oyentesEstado = new Set()     // fn(conectado: boolean)
  let controlador = null
  let conectado = false
  let espera = 1000
  let temporizador = null

  const cambiarEstado = (valor) => {
    if (conectado === valor) return
    conectado = valor
    oyentesEstado.forEach(fn => { try { fn(valor) } catch { /* pantalla ya cerrada */ } })
  }

  const procesar = (bloque) => {
    let evento = 'message'
    const datos = []
    bloque.split('\n').forEach(linea => {
      if (linea.startsWith('event:')) evento = linea.slice(6).trim()
      else if (linea.startsWith('data:')) datos.push(linea.slice(5).trim())
    })
    if (evento === 'conectado') { espera = 1000; cambiarEstado(true); return }
    if (evento !== 'cambio' || !datos.length) return
    try {
      const { servicios = [], temas = [] } = JSON.parse(datos.join('\n'))
      oyentes.forEach(fn => { try { fn(servicios, temas) } catch { /* una pantalla no tumba a las demás */ } })
    } catch { /* aviso mal formado: se ignora */ }
  }

  const programarReintento = () => {
    cambiarEstado(false)
    if (!oyentes.size || temporizador) return
    temporizador = setTimeout(() => { temporizador = null; conectar() }, espera)
    espera = Math.min(espera * 2, 30000)
  }

  const conectar = async () => {
    if (controlador || !oyentes.size) return
    const url = obtenerUrl()
    if (!url) return
    controlador = new AbortController()
    try {
      const resp = await fetch(url, { headers: { Accept: 'text/event-stream', ...obtenerEncabezados() }, signal: controlador.signal, cache: 'no-store' })
      if (!resp.ok || !resp.body) throw new Error(`HTTP ${resp.status}`)
      const lector = resp.body.getReader()
      const decodificador = new TextDecoder()
      let resto = ''
      for (;;) {
        const { value, done } = await lector.read()
        if (done) break
        resto += decodificador.decode(value, { stream: true }).replace(/\r\n/g, '\n')
        let corte
        while ((corte = resto.indexOf('\n\n')) >= 0) {
          procesar(resto.slice(0, corte))
          resto = resto.slice(corte + 2)
        }
      }
    } catch {
      /* se cae, el servidor la cerró o la cancelamos: se reintenta abajo si alguien sigue escuchando */
    } finally {
      const cancelada = controlador?.signal.aborted
      controlador = null
      if (!cancelada) programarReintento()
      else cambiarEstado(false)
    }
  }

  const cerrar = () => {
    clearTimeout(temporizador); temporizador = null
    controlador?.abort()
    controlador = null
    cambiarEstado(false)
  }

  return {
    suscribir(fn) {
      oyentes.add(fn)
      conectar()
      return () => { oyentes.delete(fn); if (!oyentes.size) cerrar() }
    },
    alCambiarEstado(fn) { oyentesEstado.add(fn); return () => oyentesEstado.delete(fn) },
    estaConectado: () => conectado,
    // Tras volver a tener sesión (token nuevo) o recuperar la red, reconecta ya.
    reconectar() { cerrar(); espera = 1000; if (oyentes.size) conectar() },
  }
}

/** Canal de usuarios con sesión (internos y clientes). */
export const canalUsuario = crearCanal(
  () => (localStorage.getItem('token') ? '/api/v1/tiempo-real/eventos' : null),
  () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` }),
)

/** Canal del portal del candidato (uno por token de link). */
const canalesCandidato = new Map()
export function canalCandidato(token) {
  if (!canalesCandidato.has(token)) {
    canalesCandidato.set(token, crearCanal(() => `/api/v1/evaluees/link/${token}/eventos`, () => {
      // Misma llave que el resto del portal (evaluadoService.verificarDocumento)
      let acceso = null
      try { acceso = sessionStorage.getItem(`acceso-candidato-${token}`) } catch { /* sin almacenamiento */ }
      return acceso ? { 'X-Acceso-Candidato': acceso } : {}
    }))
  }
  return canalesCandidato.get(token)
}

if (typeof window !== 'undefined') {
  window.addEventListener('online', () => canalUsuario.reconectar())
  window.addEventListener('usuario-refrescado', () => canalUsuario.reconectar())
}

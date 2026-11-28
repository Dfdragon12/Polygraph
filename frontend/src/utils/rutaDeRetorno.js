// PC-06 (PB-H-41) · Volver a la pantalla donde estaba la persona cuando la sesión se cerró SOLA (por inactividad o porque venció el token).
// Se recuerda la ruta completa (con ?filtros y ?abierto=) en sessionStorage, junto con quién era; al iniciar sesión se vuelve ahí solo si entra la MISMA persona.
// Si la persona cierra sesión a mano, no se recuerda nada (logout la borra). La ruta se valida: solo rutas internas de la plataforma, nunca una dirección externa.

const CLAVE = 'polygraph.volverA'
const VIGENCIA_MS = 12 * 60 * 60 * 1000
const BARRA_INVERTIDA = String.fromCharCode(92)
const NO_VOLVER = /^\/(login|registro|recuperar-password|reset-password|cambiar-password|no-autorizado|legal|evaluado|proveedor)(\/|\?|#|$)/

/** Una ruta interna de la plataforma a la que tiene sentido volver. */
export function esRutaDeRetorno(ruta) {
  return typeof ruta === 'string' && ruta.length <= 2000 && ruta.startsWith('/') && !ruta.startsWith('//') && !ruta.includes(BARRA_INVERTIDA) && !/[\u0000-\u001f]/.test(ruta) && !NO_VOLVER.test(ruta)
}

/** Guarda dónde estaba `idUsuario` (la ruta actual del navegador por defecto). No hace nada si no es una ruta a la que se pueda volver. */
export function recordarRuta(idUsuario, ruta = typeof window !== 'undefined' ? window.location.pathname + window.location.search + window.location.hash : '', ahora = Date.now()) {
  if (idUsuario == null || !esRutaDeRetorno(ruta)) return false
  try { sessionStorage.setItem(CLAVE, JSON.stringify({ ruta, idUsuario, en: ahora })); return true } catch { return false }
}

/** La ruta guardada si entra la misma persona (y no es vieja); la consume. Si entra otra persona, se descarta. */
export function rutaParaVolver(idUsuario, ahora = Date.now()) {
  let guardada = null
  try { guardada = JSON.parse(sessionStorage.getItem(CLAVE)) } catch { /* sin nada guardado */ }
  olvidarRuta()
  if (!guardada || guardada.idUsuario == null || idUsuario == null || guardada.idUsuario !== idUsuario) return null
  if (typeof guardada.en !== 'number' || ahora - guardada.en > VIGENCIA_MS) return null
  return esRutaDeRetorno(guardada.ruta) ? guardada.ruta : null
}

export function olvidarRuta() {
  try { sessionStorage.removeItem(CLAVE) } catch { /* nada que olvidar */ }
}

// «Tu sesión se cerró por inactividad»: el aviso del login no puede depender de ?motivo= en la URL, porque la ruta protegida que se desmonta redirige a /login
// una vez más y borra el parámetro (se vio con la sesión de prueba). Se guarda también una marca de la pestaña, que el login lee y borra al entrar.
const CLAVE_CIERRE = 'polygraph.cierreAutomatico'
export function marcarCierreAutomatico() { try { sessionStorage.setItem(CLAVE_CIERRE, '1') } catch { /* sin almacenamiento: queda el parámetro de la URL */ } }
export function hayCierreAutomatico() { try { return sessionStorage.getItem(CLAVE_CIERRE) === '1' } catch { return false } }
export function olvidarCierreAutomatico() { try { sessionStorage.removeItem(CLAVE_CIERRE) } catch { /* nada que olvidar */ } }

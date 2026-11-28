// PC-06 · Lo de los borradores que no es un hook (lo usa también AuthContext al cerrar sesión a mano, sin importar hooks: evita un ciclo de importación).
// Los borradores van en sessionStorage, por usuario, y valen 12 h. Ver hooks/useBorrador.js.

const PREFIJO = 'polygraph.borrador:'
const VIGENCIA_MS = 12 * 60 * 60 * 1000

export const claveBorrador = (idUsuario, nombre, id) => `${PREFIJO}${idUsuario}:${nombre}:${id}`

/** Borra todos los borradores de la pestaña (al cerrar sesión a mano). */
export function borrarBorradores() {
  try {
    const claves = []
    for (let i = 0; i < sessionStorage.length; i++) { const k = sessionStorage.key(i); if (k && k.startsWith(PREFIJO)) claves.push(k) }
    claves.forEach(k => sessionStorage.removeItem(k))
  } catch { /* sin almacenamiento: no hay borradores que borrar */ }
}

export function leerBorrador(clave, ahora = Date.now()) {
  try {
    const x = JSON.parse(sessionStorage.getItem(clave))
    if (!x || typeof x.guardadoEn !== 'number' || ahora - x.guardadoEn > VIGENCIA_MS) return null
    return x
  } catch { return null }
}


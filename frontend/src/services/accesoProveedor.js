// PF-09 · Acceso del proveedor externo. El token del enlace viaja en la cabecera X-Acceso-Proveedor en CADA petición (lo agrega
// api.js) y reemplaza a la sesión normal: mientras hay token no se manda el Bearer ni se intenta renovar sesión ni se redirige
// al login. Vive en sessionStorage: se borra al cerrar la pestaña (el enlace de la URL sirve para volver a entrar).

const CLAVE = 'acceso_proveedor'

export const CABECERA_PROVEEDOR = 'X-Acceso-Proveedor'

export const tokenProveedor = () => {
  try { return sessionStorage.getItem(CLAVE) } catch { return null }
}
export const guardarTokenProveedor = (token) => {
  try { sessionStorage.setItem(CLAVE, token) } catch { /* sin almacenamiento: queda solo en la URL */ }
}
export const olvidarTokenProveedor = () => {
  try { sessionStorage.removeItem(CLAVE) } catch { /* nada */ }
}
export const enModoProveedor = () => !!tokenProveedor()

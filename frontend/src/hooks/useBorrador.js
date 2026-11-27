import { useCallback, useEffect, useMemo, useRef } from 'react'
import { useAuth } from './useAuth'
import { claveBorrador, leerBorrador } from '../utils/borradores'

// PC-06 (PB-H-41) · Borrador de lo escrito en una ventana (Publicar, Emitir, Devolver): si la sesión se cierra por inactividad —o se recarga la página— al volver a
// abrir la misma ventana el texto sigue ahí. Va en sessionStorage, no en localStorage: sobrevive a recargar y a volver a iniciar sesión en la MISMA pestaña, pero
// no queda en un computador compartido al cerrar la pestaña, y se borra al cerrar sesión a mano (borrarBorradores). Es por usuario: otro usuario no lo ve.
//   const borrador = useBorrador('publicar', idSubproceso)
//   borrador.leer()            → { valor, guardadoEn } | null (nada con más de 12 h)
//   borrador.guardar(valor)    → con una pausa de medio segundo; al cerrar la ventana lo pendiente se escribe
//   borrador.borrar()          → cuando la acción terminó bien (o el usuario descarta el borrador)

const PAUSA_MS = 500

export function useBorrador(nombre, id) {
  const { usuario } = useAuth()
  const clave = useMemo(() => (usuario?.id != null && id != null ? claveBorrador(usuario.id, nombre, id) : null), [usuario?.id, nombre, id])
  const temporizador = useRef(null)
  const pendiente = useRef(undefined)   // undefined = nada por escribir

  const escribir = useCallback((valor) => {
    if (!clave) return
    try { sessionStorage.setItem(clave, JSON.stringify({ valor, guardadoEn: Date.now() })) } catch { /* lleno o bloqueado: se sigue sin borrador */ }
  }, [clave])

  const leer = useCallback(() => (clave ? leerBorrador(clave) : null), [clave])

  const guardar = useCallback((valor) => {
    pendiente.current = valor
    clearTimeout(temporizador.current)
    temporizador.current = setTimeout(() => { escribir(valor); pendiente.current = undefined }, PAUSA_MS)
  }, [escribir])

  const borrar = useCallback(() => {
    clearTimeout(temporizador.current)
    pendiente.current = undefined
    if (!clave) return
    try { sessionStorage.removeItem(clave) } catch { /* nada que borrar */ }
  }, [clave])

  // Al cerrar la ventana (Esc, ✕, Cancelar) lo que estaba por escribirse se escribe.
  useEffect(() => () => {
    clearTimeout(temporizador.current)
    if (pendiente.current !== undefined) escribir(pendiente.current)
  }, [escribir])

  return { leer, guardar, borrar }
}

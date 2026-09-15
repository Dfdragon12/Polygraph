import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from './useAuth'

const claveStorage = (idUsuario) => idUsuario ? `favoritos-servicios-${idUsuario}` : null

/** Favoritos del catálogo de servicios, guardados en localStorage — escalado por usuario, nunca
 * heredado de otra sesión en el mismo navegador (igual que el carrito, ver CarritoContext). */
export function useFavoritos() {
  const { usuario } = useAuth()
  // El objeto de sesión (login) trae "id", no "idUsuario" (ese nombre solo lo usan las entidades
  // completas en otras pantallas) — con el nombre equivocado esto nunca guardaba nada.
  const idUsuario = usuario?.id ?? null
  const [favoritos, setFavoritos] = useState([])
  const saltarProximaPersistencia = useRef(false)

  useEffect(() => {
    saltarProximaPersistencia.current = true
    const clave = claveStorage(idUsuario)
    if (!clave) { setFavoritos([]); return }
    try {
      const guardado = localStorage.getItem(clave)
      setFavoritos(guardado ? JSON.parse(guardado) : [])
    } catch {
      setFavoritos([])
    }
  }, [idUsuario])

  useEffect(() => {
    if (saltarProximaPersistencia.current) {
      saltarProximaPersistencia.current = false
      return
    }
    const clave = claveStorage(idUsuario)
    if (!clave) return
    localStorage.setItem(clave, JSON.stringify(favoritos))
  }, [favoritos])

  const esFavorito = useCallback((idProceso) => favoritos.includes(idProceso), [favoritos])

  const toggleFavorito = useCallback((idProceso) => {
    setFavoritos((prev) => (
      prev.includes(idProceso) ? prev.filter((id) => id !== idProceso) : [...prev, idProceso]
    ))
  }, [])

  return { favoritos, esFavorito, toggleFavorito }
}

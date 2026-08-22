import { useCallback, useEffect, useState } from 'react'

const CLAVE_STORAGE = 'favoritos-servicios'

function leerStorage() {
  try {
    const guardado = localStorage.getItem(CLAVE_STORAGE)
    return guardado ? JSON.parse(guardado) : []
  } catch {
    return []
  }
}

/** Favoritos del catálogo de servicios, guardados en localStorage (por navegador, igual que el carrito). */
export function useFavoritos() {
  const [favoritos, setFavoritos] = useState(leerStorage)

  useEffect(() => {
    localStorage.setItem(CLAVE_STORAGE, JSON.stringify(favoritos))
  }, [favoritos])

  const esFavorito = useCallback((idProceso) => favoritos.includes(idProceso), [favoritos])

  const toggleFavorito = useCallback((idProceso) => {
    setFavoritos((prev) => (
      prev.includes(idProceso) ? prev.filter((id) => id !== idProceso) : [...prev, idProceso]
    ))
  }, [])

  return { favoritos, esFavorito, toggleFavorito }
}

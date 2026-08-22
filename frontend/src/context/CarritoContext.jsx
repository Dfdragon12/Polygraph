import { createContext, useState, useEffect, useRef } from 'react'
import { resolverPrecioUnitario } from '../utils/catalogoDisplay'
import { useAuth } from '../hooks/useAuth'

const CarritoContext = createContext(null)

const claveStorage = (idUsuario) => idUsuario ? `carrito-cliente-${idUsuario}` : null

export function CarritoProvider({ children }) {
  const { usuario } = useAuth()
  const idUsuario = usuario?.idUsuario ?? null
  const [items, setItems] = useState([])
  // Evita que, justo al cambiar de usuario, el carrito que se acaba de cargar se sobreescriba
  // con el del usuario anterior antes de que el nuevo estado termine de aplicarse.
  const saltarProximaPersistencia = useRef(false)

  // Carga el carrito propio de la sesión activa — nunca el de otro usuario del mismo navegador.
  useEffect(() => {
    saltarProximaPersistencia.current = true
    const clave = claveStorage(idUsuario)
    if (!clave) { setItems([]); return }
    try {
      const guardado = localStorage.getItem(clave)
      setItems(guardado ? JSON.parse(guardado) : [])
    } catch {
      setItems([])
    }
  }, [idUsuario])

  useEffect(() => {
    if (saltarProximaPersistencia.current) {
      saltarProximaPersistencia.current = false
      return
    }
    const clave = claveStorage(idUsuario)
    if (!clave) return
    localStorage.setItem(clave, JSON.stringify(items))
  }, [items])

  const agregarItem = (proceso, cantidad = 1) => {
    setItems((prev) => {
      const existente = prev.find((i) => i.idProceso === proceso.idProceso)
      if (existente) {
        return prev.map((i) =>
          i.idProceso === proceso.idProceso ? { ...i, cantidad: i.cantidad + cantidad } : i
        )
      }
      return [...prev, {
        idProceso: proceso.idProceso,
        nombreProceso: proceso.nombreProceso,
        valorBase: proceso.valor,
        tramosPrecio: proceso.tramosPrecio ?? [],
        cantidad,
      }]
    })
  }

  const quitarItem = (idProceso) => {
    setItems((prev) => prev.filter((i) => i.idProceso !== idProceso))
  }

  const vaciarCarrito = () => setItems([])

  /** Precio unitario vigente del ítem según su cantidad actual (aplica tramos de volumen si hay). Los descuentos dinámicos solo se ven reflejados al pagar — el backend siempre recalcula. */
  const precioUnitario = (item) => resolverPrecioUnitario(item.valorBase, item.tramosPrecio, item.cantidad)

  const totalItems = items.reduce((acc, i) => acc + i.cantidad, 0)
  const total = items.reduce((acc, i) => acc + precioUnitario(i) * i.cantidad, 0)

  return (
    <CarritoContext.Provider value={{
      items, agregarItem, quitarItem,
      vaciarCarrito, precioUnitario, totalItems, total,
    }}>
      {children}
    </CarritoContext.Provider>
  )
}

export default CarritoContext

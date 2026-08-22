import { createContext, useState, useEffect } from 'react'
import { resolverPrecioUnitario } from '../utils/catalogoDisplay'

const CarritoContext = createContext(null)
const CLAVE_STORAGE = 'carrito-cliente'

export function CarritoProvider({ children }) {
  const [items, setItems] = useState(() => {
    try {
      const guardado = localStorage.getItem(CLAVE_STORAGE)
      return guardado ? JSON.parse(guardado) : []
    } catch {
      return []
    }
  })

  useEffect(() => {
    localStorage.setItem(CLAVE_STORAGE, JSON.stringify(items))
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

  const cambiarCantidad = (idProceso, cantidad) => {
    if (cantidad < 1) {
      quitarItem(idProceso)
      return
    }
    setItems((prev) => prev.map((i) => (i.idProceso === idProceso ? { ...i, cantidad } : i)))
  }

  const vaciarCarrito = () => setItems([])

  /** Precio unitario vigente del ítem según su cantidad actual (aplica tramos de volumen si hay). */
  const precioUnitario = (item) => resolverPrecioUnitario(item.valorBase, item.tramosPrecio, item.cantidad)

  const totalItems = items.reduce((acc, i) => acc + i.cantidad, 0)
  const total = items.reduce((acc, i) => acc + precioUnitario(i) * i.cantidad, 0)

  return (
    <CarritoContext.Provider value={{ items, agregarItem, quitarItem, cambiarCantidad, vaciarCarrito, precioUnitario, totalItems, total }}>
      {children}
    </CarritoContext.Provider>
  )
}

export default CarritoContext

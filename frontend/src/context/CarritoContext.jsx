import { createContext, useState, useEffect, useRef } from 'react'
import { resolverPrecioUnitario } from '../utils/catalogoDisplay'
import { tituloDe } from '../utils/planesTienda'
import { useAuth } from '../hooks/useAuth'
import { useConfigTienda } from '../hooks/useConfigTienda'

const CarritoContext = createContext(null)

const claveStorage = (idUsuario) => idUsuario ? `carrito-cliente-${idUsuario}` : null

/** Identifica una línea del carrito: la tienda actual tiene una por servicio; la tienda por planes, una por servicio + plan + cobertura. */
export const claveDe = (item) => item.clave ?? String(item.idProceso)

export function CarritoProvider({ children }) {
  const { usuario } = useAuth()
  const config = useConfigTienda()
  // El objeto de sesión (login) trae "id", no "idUsuario" (ese nombre solo lo usan las entidades
  // completas en otras pantallas) — con el nombre equivocado esto nunca guardaba nada.
  const idUsuario = usuario?.id ?? null
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

  // Si el interruptor tiendaPorPlanes cambió desde que se armó el carrito, las líneas del otro modelo ya no son válidas
  // (el backend las rechazaría): se descartan en vez de dejar un carrito que no se puede pagar.
  useEffect(() => {
    if (!config.cargada) return
    setItems((prev) => {
      const validos = prev.filter((i) => Boolean(i.unidadesPlan) === config.tiendaPorPlanes)
      return validos.length === prev.length ? prev : validos
    })
  }, [config.cargada, config.tiendaPorPlanes])

  const agregarItem = (proceso, cantidad = 1) => {
    setItems((prev) => {
      const existente = prev.find((i) => !i.unidadesPlan && i.idProceso === proceso.idProceso)
      if (existente) {
        return prev.map((i) =>
          i === existente ? { ...i, cantidad: i.cantidad + cantidad } : i
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

  /**
   * Tienda por planes: agrega UN paquete del plan (o suma otro si ya está). `plan` viene del backend con su precio por unidad
   * vigente; el backend lo vuelve a calcular al pagar y gana si difiere.
   */
  const agregarPlan = (servicio, plan, cobertura = 'LOCAL') => {
    const clave = `${servicio.idProceso}:${plan.unidades}:${cobertura}`
    setItems((prev) => {
      const existente = prev.find((i) => i.clave === clave)
      if (existente) {
        return prev.map((i) => i === existente
          ? { ...i, cantidadPaquetes: i.cantidadPaquetes + 1, cantidad: i.cantidad + plan.unidades }
          : i)
      }
      return [...prev, {
        clave,
        idProceso: servicio.idProceso,
        nombreProceso: tituloDe(servicio),
        unidadSingular: servicio.unidadSingular,
        unidadPlural: servicio.unidadPlural,
        unidadesPlan: plan.unidades,
        nombrePlan: plan.nombre,
        cantidadPaquetes: 1,
        cantidad: plan.unidades,
        cobertura,
        precioUnitario: plan.precioUnitario,
        vigenciaMeses: plan.vigenciaMeses ?? null,
        valorBase: plan.precioUnitario,
        tramosPrecio: [],
      }]
    })
  }

  const quitarItem = (clave) => {
    setItems((prev) => prev.filter((i) => claveDe(i) !== String(clave)))
  }

  const vaciarCarrito = () => setItems([])

  /** Precio unitario vigente del ítem. Plan: el fijo del plan. Tienda actual: aplica tramos de volumen según la cantidad. Los descuentos dinámicos solo se ven reflejados al pagar — el backend siempre recalcula. */
  const precioUnitario = (item) => item.unidadesPlan
    ? item.precioUnitario
    : resolverPrecioUnitario(item.valorBase, item.tramosPrecio, item.cantidad)

  const totalItems = items.reduce((acc, i) => acc + i.cantidad, 0)
  const total = items.reduce((acc, i) => acc + precioUnitario(i) * i.cantidad, 0)

  return (
    <CarritoContext.Provider value={{
      items, agregarItem, agregarPlan, quitarItem,
      vaciarCarrito, precioUnitario, totalItems, total,
    }}>
      {children}
    </CarritoContext.Provider>
  )
}

export default CarritoContext

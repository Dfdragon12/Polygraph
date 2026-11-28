import { useEffect, useRef } from 'react'

/**
 * setInterval que se detiene mientras la pestaña está oculta. Con varias pestañas de la
 * plataforma abiertas (o el portátil con la tapa cerrada) antes seguían los polls de mensajes,
 * badges y notificaciones cada pocos segundos, sin que nadie los viera — tráfico y carga de BD
 * para nada. Al volver a la pestaña se ejecuta enseguida (el dato se ve fresco) y retoma el ritmo.
 *
 * Devuelve la función de limpieza, lista para retornarla desde un useEffect:
 *   useEffect(() => { cargar(); return iniciarIntervaloVisible(cargar, 30000) }, [cargar])
 */
export function iniciarIntervaloVisible(fn, ms) {
  let id = null
  const arrancar = () => {
    if (id == null) id = setInterval(fn, ms)
  }
  const parar = () => {
    if (id != null) { clearInterval(id); id = null }
  }
  const alCambiarVisibilidad = () => {
    if (document.hidden) {
      parar()
    } else {
      fn()
      arrancar()
    }
  }
  if (!document.hidden) arrancar()
  document.addEventListener('visibilitychange', alCambiarVisibilidad)
  return () => {
    parar()
    document.removeEventListener('visibilitychange', alCambiarVisibilidad)
  }
}

/** Versión hook. `fn` puede cambiar en cada render sin reiniciar el intervalo (se guarda en un
 *  ref); `activo = false` lo apaga. */
export function useIntervaloVisible(fn, ms, activo = true) {
  const fnRef = useRef(fn)
  useEffect(() => { fnRef.current = fn }, [fn])

  useEffect(() => {
    if (!activo || !ms) return undefined
    return iniciarIntervaloVisible(() => fnRef.current(), ms)
  }, [ms, activo])
}

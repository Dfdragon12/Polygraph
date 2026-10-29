import { useEffect, useRef } from 'react'
import { canalUsuario, canalCandidato } from '../services/tiempoReal'

/**
 * Recarga los datos de una pantalla cuando algo cambia en el backend, sin refrescar la página.
 *
 *   useCambios(cargar)                              → ante cualquier cambio que le incumba al usuario
 *   useCambios(cargar, { servicios: idServicio })   → solo si cambió ese servicio (número, lista o función)
 *   useCambios(cargar, { candidato: token })        → portal del candidato (canal por link)
 *   useCambios(cargar, { temas: true })             → además, los avisos que solo recibe el administrador
 *                                                     (documentos de clientes, pagos, reversiones, usuarios...)
 *
 * `recargar` debe traer los datos SIN mostrar el spinner de carga completa (es una actualización
 * silenciosa). Varios avisos seguidos se juntan en una sola recarga. Si la conexión de avisos está
 * caída, consulta cada `respaldoMs` (solo con la pestaña visible) y al reconectar recarga una vez
 * por si se perdió algo.
 */
export function useCambios(recargar, { servicios = null, candidato = null, temas = false, activo = true, respaldoMs = 30000, esperaMs = 400 } = {}) {
  const recargarRef = useRef(recargar)
  const serviciosRef = useRef(servicios)
  recargarRef.current = recargar
  serviciosRef.current = servicios

  useEffect(() => {
    if (!activo) return undefined
    const canal = candidato ? canalCandidato(candidato) : canalUsuario
    let temporizador = null

    const disparar = () => {
      clearTimeout(temporizador)
      temporizador = setTimeout(() => {
        try { recargarRef.current?.() } catch { /* la pantalla maneja sus errores */ }
      }, esperaMs)
    }

    const leIncumbe = (ids) => {
      const f = serviciosRef.current
      if (f == null) return true
      if (typeof f === 'function') return ids.some(id => f(id))
      const lista = Array.isArray(f) ? f : [f]
      return ids.some(id => lista.some(x => Number(x) === Number(id)))
    }

    const quitar = canal.suscribir((ids, avisados = []) => {
      // Un aviso sin servicios es de "temas" del administrador: solo lo atiende quien lo pidió.
      if (ids.length === 0) { if (temas && avisados.length) disparar(); return }
      if (leIncumbe(ids)) disparar()
    })
    // Al RE-conectar (no la primera vez) se recarga: mientras estuvo caída pudo perderse un aviso.
    let yaConecto = canal.estaConectado()
    const quitarEstado = canal.alCambiarEstado((conectado) => {
      if (!conectado) return
      if (yaConecto) disparar()
      yaConecto = true
    })

    // Respaldo: sin avisos, consulta cada tanto (solo con la pestaña visible)
    const respaldo = setInterval(() => {
      if (!canal.estaConectado() && document.visibilityState === 'visible') disparar()
    }, respaldoMs)

    return () => {
      clearTimeout(temporizador)
      clearInterval(respaldo)
      quitar()
      quitarEstado()
    }
  }, [candidato, activo, temas, respaldoMs, esperaMs])
}

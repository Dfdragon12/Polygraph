import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './useAuth'

const EVENTOS_ACTIVIDAD = ['mousedown', 'mousemove', 'keydown', 'wheel', 'scroll', 'touchstart']

const TIEMPO_INACTIVIDAD_MS = 15 * 60 * 1000 // 15 minutos totales sin actividad
const TIEMPO_ALERTA_MS = 60 * 1000 // el aviso aparece 1 minuto antes de cerrar la sesión

/**
 * Cierra la sesión de cualquier usuario autenticado tras 15 minutos sin actividad (mouse, teclado,
 * scroll, touch), avisando con 1 minuto de anticipación para que pueda seguir conectado.
 * No corre en el portal del candidato (/evaluado/...): el candidato entra por su link, no tiene sesión,
 * y aunque en ese navegador haya un usuario interno conectado, el aviso no debe salirle a él.
 */
export default function useInactivityLogout() {
  const { usuario, logout } = useAuth()
  const navigate = useNavigate()
  const enPortalCandidato = useLocation().pathname.startsWith('/evaluado/')
  const idleTimeoutRef = useRef(null)
  const countdownIntervalRef = useRef(null)
  const reiniciarTimerRef = useRef(() => {})
  const [mostrarAlerta, setMostrarAlerta] = useState(false)
  const [segundosRestantes, setSegundosRestantes] = useState(TIEMPO_ALERTA_MS / 1000)

  const cerrarSesion = useCallback(() => {
    logout()
    navigate('/login?motivo=inactividad', { replace: true })
  }, [logout, navigate])

  useEffect(() => {
    if (!usuario || enPortalCandidato) {
      setMostrarAlerta(false)
      return
    }

    const limpiarTimers = () => {
      clearTimeout(idleTimeoutRef.current)
      clearInterval(countdownIntervalRef.current)
    }

    const iniciarCuentaRegresiva = () => {
      setSegundosRestantes(TIEMPO_ALERTA_MS / 1000)
      setMostrarAlerta(true)
      countdownIntervalRef.current = setInterval(() => {
        setSegundosRestantes((s) => Math.max(s - 1, 0))
      }, 1000)
      idleTimeoutRef.current = setTimeout(cerrarSesion, TIEMPO_ALERTA_MS)
    }

    const reiniciarTimer = () => {
      limpiarTimers()
      setMostrarAlerta(false)
      idleTimeoutRef.current = setTimeout(iniciarCuentaRegresiva, TIEMPO_INACTIVIDAD_MS - TIEMPO_ALERTA_MS)
    }
    reiniciarTimerRef.current = reiniciarTimer

    reiniciarTimer()
    EVENTOS_ACTIVIDAD.forEach((evento) => window.addEventListener(evento, reiniciarTimer, { passive: true }))

    return () => {
      limpiarTimers()
      EVENTOS_ACTIVIDAD.forEach((evento) => window.removeEventListener(evento, reiniciarTimer))
    }
  }, [usuario, enPortalCandidato, cerrarSesion])

  // Botón "Seguir conectado" del aviso — reinicia el ciclo completo, igual que cualquier otra actividad.
  const seguirConectado = useCallback(() => reiniciarTimerRef.current(), [])

  return { mostrarAlerta, segundosRestantes, seguirConectado, cerrarSesion }
}

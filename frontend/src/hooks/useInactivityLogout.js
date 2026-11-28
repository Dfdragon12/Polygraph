import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './useAuth'
import { recordarRuta, marcarCierreAutomatico } from '../utils/rutaDeRetorno'

const EVENTOS_ACTIVIDAD = ['mousedown', 'mousemove', 'keydown', 'wheel', 'scroll', 'touchstart']

const TIEMPO_INACTIVIDAD_MS = 15 * 60 * 1000 // 15 minutos totales sin actividad
const TIEMPO_ALERTA_MS = 2 * 60 * 1000 // el aviso aparece 2 minutos antes de cerrar la sesión (PC-06)

/**
 * Cierra la sesión de cualquier usuario autenticado tras 15 minutos sin actividad (mouse, teclado,
 * scroll, touch), avisando con 2 minutos de anticipación para que pueda seguir conectado.
 * PC-06: al empezar el aviso se emite «sesion-por-expirar» (las pantallas guardan lo que tengan a medias), mientras el aviso está en pantalla solo «Seguir conectado»
 * lo cancela (un movimiento del cursor ya no lo hace desaparecer), y al cerrarse la sesión se recuerda la pantalla donde estaba para volver a ella al entrar.
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
  const alertaRef = useRef(false)
  const [mostrarAlerta, setMostrarAlerta] = useState(false)
  const [segundosRestantes, setSegundosRestantes] = useState(TIEMPO_ALERTA_MS / 1000)

  const cerrarSesion = useCallback(() => {
    recordarRuta(usuario?.id)                    // dónde estaba, para volver ahí al entrar de nuevo (solo la misma persona)
    marcarCierreAutomatico()                     // el login explica por qué se cerró
    logout({ automatico: true })                 // conserva los borradores
    navigate('/login?motivo=inactividad', { replace: true })
  }, [logout, navigate, usuario?.id])

  useEffect(() => {
    if (!usuario || enPortalCandidato) {
      alertaRef.current = false
      setMostrarAlerta(false)
      return
    }

    const limpiarTimers = () => {
      clearTimeout(idleTimeoutRef.current)
      clearInterval(countdownIntervalRef.current)
    }

    const iniciarCuentaRegresiva = () => {
      alertaRef.current = true
      window.dispatchEvent(new Event('sesion-por-expirar'))
      setSegundosRestantes(TIEMPO_ALERTA_MS / 1000)
      setMostrarAlerta(true)
      countdownIntervalRef.current = setInterval(() => {
        setSegundosRestantes((s) => Math.max(s - 1, 0))
      }, 1000)
      idleTimeoutRef.current = setTimeout(cerrarSesion, TIEMPO_ALERTA_MS)
    }

    const reiniciarTimer = () => {
      limpiarTimers()
      alertaRef.current = false
      setMostrarAlerta(false)
      idleTimeoutRef.current = setTimeout(iniciarCuentaRegresiva, TIEMPO_INACTIVIDAD_MS - TIEMPO_ALERTA_MS)
    }
    reiniciarTimerRef.current = reiniciarTimer

    reiniciarTimer()
    // Con el aviso en pantalla la actividad suelta (mover el cursor, desplazarse) no lo cancela: hay que pulsar «Seguir conectado».
    const alActividad = () => { if (!alertaRef.current) reiniciarTimer() }
    EVENTOS_ACTIVIDAD.forEach((evento) => window.addEventListener(evento, alActividad, { passive: true }))

    return () => {
      limpiarTimers()
      EVENTOS_ACTIVIDAD.forEach((evento) => window.removeEventListener(evento, alActividad))
    }
  }, [usuario, enPortalCandidato, cerrarSesion])

  // Botón "Seguir conectado" del aviso — reinicia el ciclo completo, igual que cualquier otra actividad.
  const seguirConectado = useCallback(() => reiniciarTimerRef.current(), [])

  return { mostrarAlerta, segundosRestantes, seguirConectado, cerrarSesion }
}

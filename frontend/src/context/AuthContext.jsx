import { createContext, useState, useEffect, useCallback } from 'react'
import authService from '../services/authService'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null)
  const [cargando, setCargando] = useState(true)

  const cargarDeStorage = useCallback(() => {
    const usuarioGuardado = localStorage.getItem('usuario')
    if (!usuarioGuardado) return
    try {
      setUsuario(JSON.parse(usuarioGuardado))
    } catch {
      localStorage.removeItem('usuario')
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
    }
  }, [])

  useEffect(() => {
    if (localStorage.getItem('token')) cargarDeStorage()
    setCargando(false)
    // api.js actualiza localStorage.usuario cada vez que renueva el token en segundo plano (el
    // backend recalcula puedeSolicitar/tipoCliente en cada refresh) — esto sincroniza el estado
    // de React con ese cambio sin necesitar recargar la página.
    window.addEventListener('usuario-refrescado', cargarDeStorage)
    return () => window.removeEventListener('usuario-refrescado', cargarDeStorage)
  }, [cargarDeStorage])

  // Guarda la sesión a partir de una respuesta de login ya obtenida (ej. login con Google, que
  // no pasa por authService.login) — mismo mecanismo que usa login() abajo.
  const establecerSesion = (datos) => {
    localStorage.setItem('token', datos.token)
    localStorage.setItem('refreshToken', datos.refreshToken)
    localStorage.setItem('usuario', JSON.stringify(datos.usuario))
    setUsuario(datos.usuario)
    return datos
  }

  const login = async (email, password) => {
    const datos = await authService.login(email, password)
    return establecerSesion(datos)
  }

  // Fuerza traer datos frescos del usuario (ej. justo tras completar una compra), para que cosas
  // como "Solicitudes" aparezcan sin tener que cerrar sesión y volver a entrar — reutiliza
  // /auth/refresh, que ya recalcula puedeSolicitar en el backend en cada llamada.
  const refrescarUsuario = useCallback(async () => {
    const refreshToken = localStorage.getItem('refreshToken')
    if (!refreshToken) return
    try {
      const datos = await authService.refresh(refreshToken)
      localStorage.setItem('token', datos.token)
      localStorage.setItem('refreshToken', datos.refreshToken)
      localStorage.setItem('usuario', JSON.stringify(datos.usuario))
      setUsuario(datos.usuario)
    } catch {
      // sesión inválida — el interceptor de api.js ya maneja la redirección en ese caso
    }
  }, [])

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('usuario')
    setUsuario(null)
  }

  const isAuthenticated = () => !!localStorage.getItem('token') && !!usuario

  return (
    <AuthContext.Provider value={{ usuario, cargando, login, logout, isAuthenticated, establecerSesion, refrescarUsuario }}>
      {children}
    </AuthContext.Provider>
  )
}

export default AuthContext

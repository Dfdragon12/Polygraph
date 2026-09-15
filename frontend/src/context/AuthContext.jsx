import { createContext, useState, useEffect } from 'react'
import authService from '../services/authService'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    const usuarioGuardado = localStorage.getItem('usuario')
    const token = localStorage.getItem('token')
    if (usuarioGuardado && token) {
      try {
        setUsuario(JSON.parse(usuarioGuardado))
      } catch {
        localStorage.removeItem('usuario')
        localStorage.removeItem('token')
        localStorage.removeItem('refreshToken')
      }
    }
    setCargando(false)
  }, [])

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

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('usuario')
    setUsuario(null)
  }

  const isAuthenticated = () => !!localStorage.getItem('token') && !!usuario

  return (
    <AuthContext.Provider value={{ usuario, cargando, login, logout, isAuthenticated, establecerSesion }}>
      {children}
    </AuthContext.Provider>
  )
}

export default AuthContext

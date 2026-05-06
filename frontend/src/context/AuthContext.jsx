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

  const login = async (email, password) => {
    const datos = await authService.login(email, password)
    localStorage.setItem('token', datos.token)
    localStorage.setItem('refreshToken', datos.refreshToken)
    localStorage.setItem('usuario', JSON.stringify(datos.usuario))
    setUsuario(datos.usuario)
    return datos
  }

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('usuario')
    setUsuario(null)
  }

  const isAuthenticated = () => !!localStorage.getItem('token') && !!usuario

  return (
    <AuthContext.Provider value={{ usuario, cargando, login, logout, isAuthenticated }}>
      {children}
    </AuthContext.Provider>
  )
}

export default AuthContext

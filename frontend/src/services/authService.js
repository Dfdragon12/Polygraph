import api from './api'

const authService = {
  login: async (email, password) => {
    const { data } = await api.post('/auth/login', { email, password })
    return data
  },

  registrarNatural: async (datos) => {
    const { data } = await api.post('/auth/register/natural', datos)
    return data
  },

  registrarJuridica: async (datos) => {
    const { data } = await api.post('/auth/register/juridica', datos)
    return data
  },

  activarCuenta: async (token) => {
    const { data } = await api.post(`/auth/activate?token=${encodeURIComponent(token)}`)
    return data
  },

  refresh: async (refreshToken) => {
    const { data } = await api.post('/auth/refresh', { refreshToken })
    return data
  },

  forgotPassword: async (email) => {
    const { data } = await api.post('/auth/forgot-password', { email })
    return data
  },

  resetPassword: async (token, nuevaPassword) => {
    const { data } = await api.post('/auth/reset-password', { token, nuevaPassword })
    return data
  },
}

export default authService

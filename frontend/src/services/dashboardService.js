import api from './api'

const dashboardService = {
  async obtenerDashboard() {
    const { data } = await api.get('/client/dashboard')
    return data
  },

  async listarSolicitudes(params = {}) {
    const { data } = await api.get('/client/requests', { params })
    return data
  },

  async obtenerBolsaServicios() {
    const { data } = await api.get('/client/service-packages')
    return data
  },

  async obtenerGestor() {
    const { data } = await api.get('/client/gestor')
    return data
  },
}

export default dashboardService

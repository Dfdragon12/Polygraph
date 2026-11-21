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

  /** TP-07: lotes con unidades que vencen pronto (vacío si la vigencia está apagada). */
  async obtenerLotesPorVencer() {
    const { data } = await api.get('/client/lotes-por-vencer')
    return data
  },

  // PB-05: informes listos, requiere tu atención (candidatos y citas) y tiempos de entrega. desde = última visita (ISO) para marcar lo nuevo.
  async obtenerInicio(desde) {
    const { data } = await api.get('/client/inicio', { params: { desde: desde || undefined } })
    return data
  },

  // PB-05: estadísticas de mi empresa por periodo (HOY | SEMANA | MES | ANIO | TOTAL)
  async obtenerEstadisticas(periodo) {
    const { data } = await api.get('/client/estadisticas', { params: { periodo } })
    return data
  },

  async obtenerGestor() {
    const { data } = await api.get('/client/gestor')
    return data
  },
}

export default dashboardService

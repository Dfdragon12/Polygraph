import api from './api'

const catalogoService = {
  async listarServicios() {
    const { data } = await api.get('/services')
    return data
  },

  async obtenerServicio(id) {
    const { data } = await api.get(`/services/${id}`)
    return data
  },

  async crearServicio(solicitud) {
    const { data } = await api.post('/services', solicitud)
    return data
  },

  async actualizarServicio(id, solicitud) {
    const { data } = await api.put(`/services/${id}`, solicitud)
    return data
  },

  async desactivarServicio(id) {
    await api.delete(`/services/${id}`)
  },
}

export default catalogoService

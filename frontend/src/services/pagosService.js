import api from './api'

const pagosService = {
  async crearOrden(items) {
    const { data } = await api.post('/client/ordenes', { items })
    return data
  },

  async iniciarPago(idOrden) {
    const { data } = await api.post(`/client/ordenes/${idOrden}/pagar`)
    return data
  },

  async obtenerOrden(idOrden) {
    const { data } = await api.get(`/client/ordenes/${idOrden}`)
    return data
  },

  async listarOrdenes(params = {}) {
    const { data } = await api.get('/client/ordenes', { params })
    return data
  },

  async simularPago(idOrden, resultado) {
    const { data } = await api.post(`/client/ordenes/${idOrden}/simular`, { resultado })
    return data
  },
}

export default pagosService

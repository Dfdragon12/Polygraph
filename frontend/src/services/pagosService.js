import api from './api'

const pagosService = {
  async crearOrden(items, codigoCupon = null) {
    const { data } = await api.post('/client/ordenes', { items, codigoCupon: codigoCupon || null })
    return data
  },

  async validarCupon(items, codigoCupon) {
    const { data } = await api.post('/client/ordenes/validar-cupon', { items, codigoCupon })
    return data
  },

  async obtenerRecargosPendientes() {
    const { data } = await api.get('/client/ordenes/recargos-pendientes')
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

  async listarComprobantes() {
    const { data } = await api.get('/client/comprobantes')
    return data
  },

  async obtenerComprobante(idComprobante) {
    const { data } = await api.get(`/client/comprobantes/${idComprobante}`)
    return data
  },

  // ---------- Admin (ADMIN_POLYGRAPH / GESTOR) ----------

  async adminListarOrdenes(params = {}) {
    const { data } = await api.get('/admin/ordenes', { params })
    return data
  },

  async adminGenerarComprobante(idOrden) {
    const { data } = await api.post(`/admin/ordenes/${idOrden}/comprobante`)
    return data
  },

  async adminObtenerComprobante(idComprobante) {
    const { data } = await api.get(`/admin/ordenes/comprobantes/${idComprobante}`)
    return data
  },

  async adminEliminarOrden(idOrden) {
    await api.delete(`/admin/ordenes/${idOrden}`)
  },
}

export default pagosService

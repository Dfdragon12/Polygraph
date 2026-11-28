import api from './api'

const pospagoService = {
  async obtenerEstadoCuenta() {
    const { data } = await api.get('/client/pospago/estado-cuenta')
    return data
  },

  async listarFacturas() {
    const { data } = await api.get('/client/pospago/facturas')
    return data
  },

  async obtenerFactura(idFactura) {
    const { data } = await api.get(`/client/pospago/facturas/${idFactura}`)
    return data
  },

  // Pipeline genérico de pago Wompi (widget) — compartido con cualquier otro concepto pagable.
  async iniciarPagoWompi({ clienteId, facturaOConceptoId, montoEnCentavos, descripcion, tipoConcepto }) {
    const { data } = await api.post('/pagos/iniciar', { clienteId, facturaOConceptoId, montoEnCentavos, descripcion, tipoConcepto })
    return data
  },

  async obtenerEstadoPago(referencia) {
    const { data } = await api.get(`/pagos/${referencia}`)
    return data
  },

  // ---------- Admin (ADMIN_POLYGRAPH / GESTOR) ----------

  async adminEstadoCuenta(idCliente) {
    const { data } = await api.get(`/admin/pospago/clientes/${idCliente}/estado-cuenta`)
    return data
  },

  async adminMovimientosPendientes(idCliente) {
    const { data } = await api.get(`/admin/pospago/clientes/${idCliente}/movimientos-pendientes`)
    return data
  },

  async adminPendientesGlobal() {
    const { data } = await api.get('/admin/pospago/pendientes')
    return data
  },

  async adminResumenFacturas() {
    const { data } = await api.get('/admin/pospago/facturas/resumen')
    return data
  },

  async adminFacturasDelCliente(idCliente) {
    const { data } = await api.get(`/admin/pospago/clientes/${idCliente}/facturas`)
    return data
  },

  async adminGenerarFactura(idCliente, fechaVencimiento, idsMovimientos) {
    const { data } = await api.post(`/admin/pospago/clientes/${idCliente}/facturas`, { fechaVencimiento, idsMovimientos })
    return data
  },

  async adminListarFacturas(params = {}) {
    const { data } = await api.get('/admin/pospago/facturas', { params })
    return data
  },

  async adminObtenerFactura(idFactura) {
    const { data } = await api.get(`/admin/pospago/facturas/${idFactura}`)
    return data
  },

  async adminEliminarFactura(idFactura) {
    await api.delete(`/admin/pospago/facturas/${idFactura}`)
  },
}

export default pospagoService

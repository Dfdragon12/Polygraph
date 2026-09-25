import api from './api'

// Notas crédito/débito internas sobre órdenes prepago y facturas pospago ya generadas — ver
// AdminNotaAjusteController / NotaAjusteService en el backend.
const notasAjusteService = {
  async adminListarNotasOrden(idOrdenCompra) {
    const { data } = await api.get(`/admin/facturacion/notas/ordenes/${idOrdenCompra}`)
    return data
  },

  async adminGenerarNotaOrden(idOrdenCompra, tipo, payload) {
    const ruta = tipo === 'CREDITO' ? 'credito' : 'debito'
    const { data } = await api.post(`/admin/facturacion/notas/ordenes/${idOrdenCompra}/${ruta}`, payload)
    return data
  },

  async adminListarNotasFactura(idFactura) {
    const { data } = await api.get(`/admin/facturacion/notas/facturas/${idFactura}`)
    return data
  },

  async adminGenerarNotaFactura(idFactura, tipo, payload) {
    const ruta = tipo === 'CREDITO' ? 'credito' : 'debito'
    const { data } = await api.post(`/admin/facturacion/notas/facturas/${idFactura}/${ruta}`, payload)
    return data
  },
}

export default notasAjusteService

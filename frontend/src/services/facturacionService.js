import api from './api'

// Tabla unificada de "documentos de facturación" (órdenes prepago + facturas pospago mezcladas)
// que se ve en la página admin Facturacion.jsx — ver AdminFacturacionController en el backend.
const facturacionService = {
  async adminListarDocumentos(params = {}) {
    const { data } = await api.get('/admin/facturacion/documentos', { params })
    return data
  },
}

export default facturacionService

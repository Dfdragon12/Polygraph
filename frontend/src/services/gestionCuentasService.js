import api from './api'

// Estado de cuenta de clientes (saldo prepago / crédito pospago) para la pantalla admin
// "Gestión de cuentas" — ver ClienteAdminController en el backend.
const gestionCuentasService = {
  async adminListarEstadoCuentas() {
    const { data } = await api.get('/clientes/estado-cuentas')
    return data
  },

  async adminObtenerSaldos(idCliente) {
    const { data } = await api.get(`/clientes/${idCliente}/saldos`)
    return data
  },

  async adminActualizarPospago(idCliente, payload) {
    const { data } = await api.patch(`/clientes/${idCliente}/pospago`, payload)
    return data
  },
}

export default gestionCuentasService
